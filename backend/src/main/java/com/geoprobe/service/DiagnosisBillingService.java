package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.mapper.DiagnosisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class DiagnosisBillingService {

    private static final String BILLING_STATE_CHARGED = "charged";
    private static final String BILLING_STATE_REFUNDED = "refunded";
    private static final String BILLING_STATE_SERVICE_ACCESS_MANAGED = "service_access_managed";
    private final Map<String, ReentrantLock> diagnosisLocks = new ConcurrentHashMap<>();

    private final DiagnosisMapper diagnosisMapper;
    private final DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    private final ObjectMapper objectMapper;

    public void markDiagnosisBillingState(String diagnosisId, String billingState) {
        markDiagnosisBillingState(diagnosisId, billingState, Map.of());
    }

    public void markDiagnosisBillingState(String diagnosisId,
                                          String billingState,
                                          Map<String, Object> extraMetadata) {
        if (!StringUtils.hasText(diagnosisId) || !StringUtils.hasText(billingState)) {
            return;
        }
        ReentrantLock lock = diagnosisLocks.computeIfAbsent(diagnosisId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            String normalizedState = billingState.trim();
            Diagnosis current = diagnosisMapper.selectById(diagnosisId);
            Map<String, Object> nativeBillingMeta = diagnosisTaskNativeRuntimeService.readTaskBillingMetadata(diagnosisId);
            Map<String, Object> legacyBillingMeta = readLegacyBillingMeta(current);
            Map<String, Object> updatedBillingMeta = buildUpdatedBillingMeta(
                    nativeBillingMeta,
                    legacyBillingMeta,
                    normalizedState,
                    now,
                    extraMetadata
            );

            diagnosisTaskNativeRuntimeService.upsertTaskBillingMetadata(diagnosisId, updatedBillingMeta);
            mirrorLegacyBillingMeta(diagnosisId, current, updatedBillingMeta);
        } catch (Exception ignored) {
            // Billing state meta is best-effort and must not block diagnosis workflow.
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                diagnosisLocks.remove(diagnosisId, lock);
            }
        }
    }

    public String readDiagnosisBillingState(String diagnosisId, Diagnosis diagnosis) {
        if (!StringUtils.hasText(diagnosisId)) {
            return readBillingStateFromLegacyDiagnosis(diagnosis);
        }
        try {
            Map<String, Object> nativeBillingMeta = diagnosisTaskNativeRuntimeService.readTaskBillingMetadata(diagnosisId);
            String nativeState = normalizeBillingState(nativeBillingMeta.get("billingState"));
            if (nativeState != null) {
                return nativeState;
            }
        } catch (Exception ignored) {
            // Billing state read is best-effort.
        }
        return readBillingStateFromLegacyDiagnosis(diagnosis);
    }

    public int readChargedCredits(String diagnosisId, Diagnosis diagnosis) {
        Map<String, Object> billingMeta = readBillingMeta(diagnosisId, diagnosis);
        Integer chargedCredits = readInteger(billingMeta.get("chargedCredits"));
        return chargedCredits != null && chargedCredits > 0 ? chargedCredits : 1;
    }

    public boolean shouldRefundOnCancellation(String diagnosisId, Diagnosis diagnosis) {
        String billingState = readDiagnosisBillingState(diagnosisId, diagnosis);
        if (BILLING_STATE_CHARGED.equals(billingState)) {
            return true;
        }
        if (BILLING_STATE_REFUNDED.equals(billingState) || BILLING_STATE_SERVICE_ACCESS_MANAGED.equals(billingState)) {
            return false;
        }
        return diagnosis != null && (diagnosis.getIsTrial() == null || diagnosis.getIsTrial() != 1);
    }

    private Map<String, Object> readLegacyBillingMeta(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return Map.of();
        }
        Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
        });
        if (reportData == null || reportData.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> meta = objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
        });
        return meta == null || meta.isEmpty() ? Map.of() : meta;
    }

    private String readBillingStateFromLegacyDiagnosis(Diagnosis diagnosis) {
        return normalizeBillingState(readLegacyBillingMeta(diagnosis).get("billingState"));
    }

    private Map<String, Object> readBillingMeta(String diagnosisId, Diagnosis diagnosis) {
        if (StringUtils.hasText(diagnosisId)) {
            try {
                Map<String, Object> nativeBillingMeta = diagnosisTaskNativeRuntimeService.readTaskBillingMetadata(diagnosisId);
                if (nativeBillingMeta != null && !nativeBillingMeta.isEmpty()) {
                    return nativeBillingMeta;
                }
            } catch (Exception ignored) {
                // Billing state read is best-effort.
            }
        }
        return readLegacyBillingMeta(diagnosis);
    }

    private String normalizeBillingState(Object billingState) {
        if (!(billingState instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private Map<String, Object> buildUpdatedBillingMeta(Map<String, Object> nativeBillingMeta,
                                                        Map<String, Object> legacyBillingMeta,
                                                        String normalizedState,
                                                        LocalDateTime now,
                                                        Map<String, Object> extraMetadata) {
        Map<String, Object> billingMeta = nativeBillingMeta == null || nativeBillingMeta.isEmpty()
                ? new LinkedHashMap<>(legacyBillingMeta == null ? Map.of() : legacyBillingMeta)
                : new LinkedHashMap<>(nativeBillingMeta);
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            billingMeta.putAll(extraMetadata);
        }
        billingMeta.put("billingState", normalizedState);
        if (BILLING_STATE_CHARGED.equals(normalizedState)) {
            billingMeta.put("chargedAt", now.toString());
        }
        if (BILLING_STATE_REFUNDED.equals(normalizedState)) {
            billingMeta.put("refundedAt", now.toString());
        }
        List<Map<String, Object>> timeline = new ArrayList<>();
        Object existingTimeline = billingMeta.get("billingTimeline");
        if (existingTimeline instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> mapped = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                });
                if (mapped != null && !mapped.isEmpty()) {
                    timeline.add(mapped);
                }
            }
        }
        timeline.add(Map.of("state", normalizedState, "at", now.toString()));
        billingMeta.put("billingTimeline", timeline);
        return billingMeta;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void mirrorLegacyBillingMeta(String diagnosisId,
                                         Diagnosis diagnosis,
                                         Map<String, Object> updatedBillingMeta) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return;
        }
        Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
        });
        if (reportData == null) {
            reportData = new LinkedHashMap<>();
        }
        Map<String, Object> meta = objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
        });
        if (meta == null) {
            meta = new LinkedHashMap<>();
        }
        meta.putAll(updatedBillingMeta);
        reportData.put("meta", meta);
        diagnosisMapper.updateById(Diagnosis.builder()
                .id(diagnosisId)
                .reportData(reportData)
                .build());
    }
}
