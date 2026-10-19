package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.QuotaAccount;
import com.geoprobe.entity.v1.QuotaGrant;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.QuotaAccountMapper;
import com.geoprobe.mapper.v1.QuotaGrantMapper;
import com.geoprobe.mapper.v1.QuotaLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LegacyCreditMirrorService {

    private static final String QUOTA_GRANT_MANUAL = "MANUAL";
    private static final String QUOTA_SOURCE_TRANSACTION = "transaction";
    private static final String LEDGER_GRANT = "GRANT";
    private static final String LEDGER_CONSUME = "CONSUME";
    private static final String LEDGER_REVERSAL = "REVERSAL";
    private static final String ATTRIBUTION_VERSION = "probe_plan_b2";

    private final DiagnosisTenantContextService diagnosisTenantContextService;
    private final QuotaAccountMapper quotaAccountMapper;
    private final QuotaGrantMapper quotaGrantMapper;
    private final QuotaLedgerMapper quotaLedgerMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;

    @Value("${app.database.v1.enabled:false}")
    private boolean enabled;

    @Value("${app.database.v1.legacy-credit-write-enabled:false}")
    private boolean legacyCreditWriteEnabled;

    public boolean shouldWriteLegacyCredit() {
        return enabled && legacyCreditWriteEnabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public void mirrorCreditTransaction(String userId, CreditTransaction transaction) {
        if (!shouldWriteLegacyCredit() || !StringUtils.hasText(userId) || transaction == null) {
            return;
        }
        DiagnosisTenantContextService.TenantContext tenant = diagnosisTenantContextService.ensureUserTenancy(userId);
        if (tenant == null || tenant.user() == null) {
            return;
        }
        QuotaAccount account = tenant.quotaAccount();
        if (account == null || transaction.getType() == null) {
            return;
        }
        String idempotencyKey = "credit-tx:" + transaction.getId();
        if (quotaLedgerMapper.selectCount(new LambdaQueryWrapper<QuotaLedger>()
                .eq(QuotaLedger::getIdempotencyKey, idempotencyKey)) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (transaction.getType() == 1 || transaction.getType() == 4) {
            QuotaGrant existingGrant = quotaGrantMapper.selectOne(new LambdaQueryWrapper<QuotaGrant>()
                    .eq(QuotaGrant::getSourceId, transaction.getId())
                    .eq(QuotaGrant::getSourceTypeCode, QUOTA_SOURCE_TRANSACTION)
                    .last("limit 1"));
            if (existingGrant == null) {
                quotaGrantMapper.insert(QuotaGrant.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .quotaAccountId(account.getId())
                        .grantTypeCode(QUOTA_GRANT_MANUAL)
                        .sourceTypeCode(QUOTA_SOURCE_TRANSACTION)
                        .sourceId(transaction.getId())
                        .totalProbeQuota(Long.valueOf(Math.max(0, transaction.getAmount() == null ? 0 : transaction.getAmount())))
                        .usedProbeQuota(0L)
                        .reservedProbeQuota(0L)
                        .validFrom(now)
                        .validTo(now.plusYears(10))
                        .expiresAt(now.plusYears(10))
                        .rolloverAllowed(true)
                        .grantMetadataJson(mapOfNonNull("transactionType", transaction.getType()))
                        .build());
            }
            account.setAvailableProbeQuota(longOrZero(tenant.user().getCreditsRemaining()));
            quotaAccountMapper.updateById(account);
            quotaLedgerMapper.insert(QuotaLedger.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .quotaAccountId(account.getId())
                    .ledgerTypeCode(LEDGER_GRANT)
                    .probeDelta(Long.valueOf(transaction.getAmount() == null ? 0 : transaction.getAmount()))
                    .balanceAfter(longOrZero(tenant.user().getCreditsRemaining()))
                    .sourceTypeCode(QUOTA_SOURCE_TRANSACTION)
                    .sourceId(transaction.getId())
                    .idempotencyKey(idempotencyKey)
                    .memo(transaction.getOrderId())
                    .build());
            return;
        }
        if (transaction.getType() == 2 || transaction.getType() == 3) {
            long delta = Long.valueOf(transaction.getAmount() == null ? 0 : transaction.getAmount());
            LedgerAttribution attribution = resolveLedgerAttribution(transaction.getDiagnosisId());
            account.setAvailableProbeQuota(longOrZero(tenant.user().getCreditsRemaining()));
            if (transaction.getType() == 2) {
                account.setConsumedProbeQuota(longOrZero(account.getConsumedProbeQuota()) + Math.abs(delta));
            } else {
                account.setConsumedProbeQuota(Math.max(0L, longOrZero(account.getConsumedProbeQuota()) - Math.abs(delta)));
            }
            quotaAccountMapper.updateById(account);
            quotaLedgerMapper.insert(QuotaLedger.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .quotaAccountId(account.getId())
                    .ledgerTypeCode(transaction.getType() == 2 ? LEDGER_CONSUME : LEDGER_REVERSAL)
                    .probeDelta(delta)
                    .balanceAfter(longOrZero(tenant.user().getCreditsRemaining()))
                    .sourceTypeCode(QUOTA_SOURCE_TRANSACTION)
                    .sourceId(transaction.getId())
                    .diagnosisTaskId(transaction.getDiagnosisId())
                    .organizationId(attribution.organizationId())
                    .brandScopeId(attribution.brandScopeId())
                    .planVersionId(attribution.planVersionId())
                    .planRecordId(attribution.planRecordId())
                    .pricingRuleSnapshotJson(attribution.pricingRuleSnapshotJson())
                    .ledgerMetadataJson(attribution.ledgerMetadataJson())
                    .idempotencyKey(idempotencyKey)
                    .memo(transaction.getOrderId())
                    .build());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void attachDiagnosisTaskAttribution(DiagnosisTask task) {
        if (!shouldWriteLegacyCredit() || task == null || !StringUtils.hasText(task.getId())) {
            return;
        }
        LedgerAttribution attribution = buildLedgerAttribution(task);
        if (!attribution.hasBusinessAttribution()) {
            return;
        }
        var ledgers = quotaLedgerMapper.selectList(new LambdaQueryWrapper<QuotaLedger>()
                .eq(QuotaLedger::getDiagnosisTaskId, task.getId()));
        if (ledgers == null || ledgers.isEmpty()) {
            return;
        }
        for (QuotaLedger ledger : ledgers) {
            ledger.setOrganizationId(attribution.organizationId());
            ledger.setBrandScopeId(attribution.brandScopeId());
            ledger.setPlanVersionId(attribution.planVersionId());
            ledger.setPlanRecordId(attribution.planRecordId());
            ledger.setPricingRuleSnapshotJson(attribution.pricingRuleSnapshotJson());
            ledger.setLedgerMetadataJson(attribution.ledgerMetadataJson());
            quotaLedgerMapper.updateById(ledger);
        }
    }

    private Map<String, Object> mapOfNonNull(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues 必须成对出现");
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object rawKey = keyValues[index];
            Object rawValue = keyValues[index + 1];
            if (!(rawKey instanceof String key) || !StringUtils.hasText(key) || rawValue == null) {
                continue;
            }
            values.put(key.trim(), rawValue);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    private LedgerAttribution resolveLedgerAttribution(String diagnosisTaskId) {
        if (!StringUtils.hasText(diagnosisTaskId)) {
            return LedgerAttribution.empty();
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisTaskId.trim());
        return buildLedgerAttribution(task);
    }

    private LedgerAttribution buildLedgerAttribution(DiagnosisTask task) {
        if (task == null) {
            return LedgerAttribution.empty();
        }
        Map<String, Object> metadata = task.getTaskMetadataJson();
        Map<String, Object> binding = readProbePlanBinding(task.getTaskOrderJson());
        String planVersionId = firstText(readText(metadata, "planVersionId"), readText(binding, "planVersionId"));
        String planRecordId = firstText(readText(metadata, "planRecordId"), readText(binding, "planRecordId"));
        Map<String, Object> pricingSnapshot = mapOfNonNull(
                "contractVersion", ATTRIBUTION_VERSION,
                "unit", "CREDIT_PER_PROBE",
                "creditsPerProbe", 1,
                "attributionLevel", "RUN_BATCH",
                "source", "diagnosis_task"
        );
        Map<String, Object> ledgerMetadata = mapOfNonNull(
                "attributionVersion", ATTRIBUTION_VERSION,
                "runTypeCode", readText(metadata, "runTypeCode"),
                "triggerSource", readText(metadata, "triggerSource"),
                "probePlanVersionNo", readAny(metadata, "probePlanVersionNo"),
                "probePlanRecordNo", readAny(metadata, "probePlanRecordNo"),
                "enabledProbeCount", readAny(binding, "enabledProbeCount")
        );
        return new LedgerAttribution(
                normalizeText(task.getOrganizationId()),
                normalizeText(task.getBrandScopeId()),
                normalizeText(planVersionId),
                normalizeText(planRecordId),
                pricingSnapshot,
                ledgerMetadata
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readProbePlanBinding(Map<String, Object> taskOrderJson) {
        Object businessHints = readAny(taskOrderJson, "businessHints");
        if (!(businessHints instanceof Map<?, ?> rawBusinessHints)) {
            return Map.of();
        }
        Object binding = rawBusinessHints.get("probePlanBinding");
        if (!(binding instanceof Map<?, ?> rawBinding)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        rawBinding.forEach((key, value) -> {
            if (key instanceof String textKey && StringUtils.hasText(textKey) && value != null) {
                normalized.put(textKey, value);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private Object readAny(Map<String, Object> values, String key) {
        if (values == null || !StringUtils.hasText(key)) {
            return null;
        }
        return values.get(key);
    }

    private String readText(Map<String, Object> values, String key) {
        Object value = readAny(values, key);
        return value instanceof String text && StringUtils.hasText(text) ? text.trim() : null;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : normalizeText(fallback);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long longOrZero(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private long longOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private record LedgerAttribution(String organizationId,
                                     String brandScopeId,
                                     String planVersionId,
                                     String planRecordId,
                                     Map<String, Object> pricingRuleSnapshotJson,
                                     Map<String, Object> ledgerMetadataJson) {
        private static LedgerAttribution empty() {
            return new LedgerAttribution(null, null, null, null, Map.of(), Map.of());
        }

        private boolean hasBusinessAttribution() {
            return StringUtils.hasText(organizationId)
                    || StringUtils.hasText(brandScopeId)
                    || StringUtils.hasText(planVersionId)
                    || StringUtils.hasText(planRecordId);
        }
    }
}
