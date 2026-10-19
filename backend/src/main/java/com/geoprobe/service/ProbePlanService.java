package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanRecordItem;
import com.geoprobe.entity.v1.ProbePlanVersion;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordItemMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProbePlanService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int RECORD_STATUS_CONFIRMED = 1;
    private static final String CHANGE_TYPE_INITIAL_CONFIRM = "INITIAL_CONFIRM";
    private static final String CHANGE_TYPE_CHANGE_CONFIRM = "CHANGE_CONFIRM";
    private static final String CHANGE_TYPE_SCHEDULED_RUN = "SCHEDULED_RUN";
    private static final String CHANGE_TYPE_CONTROLLED_RERUN = "CONTROLLED_RERUN";
    private static final String CHANGE_TYPE_MANUAL_RUN = "MANUAL_RUN";
    private static final String EXECUTE_MODE_RECURRING = "RECURRING";
    private static final String EXECUTE_MODE_ONE_TIME = "ONE_TIME";
    private static final String ACTION_ADD = "ADD";
    private static final String ACTION_KEEP = "KEEP";
    private static final String ACTION_DISABLE = "DISABLE";
    private static final String CONTRACT_VERSION = "probe_plan_b1";

    private final ProbePlanVersionMapper probePlanVersionMapper;
    private final ProbePlanRecordMapper probePlanRecordMapper;
    private final ProbePlanRecordItemMapper probePlanRecordItemMapper;
    private final BrandScopeMapper brandScopeMapper;
    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;

    public SnapshotRef snapshotInitialConfirmedPlan(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return snapshotCurrentConfig(normalized, CHANGE_TYPE_INITIAL_CONFIRM, EXECUTE_MODE_RECURRING, ACTION_ADD);
    }

    public SnapshotRef snapshotInitialConfirmedPlanOnce(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return snapshotCurrentConfig(normalized, CHANGE_TYPE_INITIAL_CONFIRM, EXECUTE_MODE_ONE_TIME, ACTION_ADD);
    }

    public SnapshotRef confirmCurrentConfigPlan(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return snapshotCurrentConfig(normalized, CHANGE_TYPE_CHANGE_CONFIRM, EXECUTE_MODE_RECURRING, ACTION_KEEP);
    }

    public SnapshotRef confirmCurrentConfigPlanOnce(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return snapshotCurrentConfig(normalized, CHANGE_TYPE_CHANGE_CONFIRM, EXECUTE_MODE_ONE_TIME, ACTION_KEEP);
    }

    public SnapshotRef recordScheduledRunPlan(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return recordRunAgainstCurrentPlan(normalized, CHANGE_TYPE_SCHEDULED_RUN, EXECUTE_MODE_RECURRING);
    }

    public SnapshotRef recordControlledRerunPlan(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return recordRunAgainstCurrentPlan(normalized, CHANGE_TYPE_CONTROLLED_RERUN, EXECUTE_MODE_ONE_TIME);
    }

    public SnapshotRef recordManualRunPlan(SnapshotCommand command) {
        SnapshotCommand normalized = normalize(command);
        return recordRunAgainstCurrentPlan(normalized, CHANGE_TYPE_MANUAL_RUN, EXECUTE_MODE_ONE_TIME);
    }

    public PlanStatusSnapshot currentPlanStatus(String brandScopeId, boolean configReady, Integer currentConfigGeneration) {
        if (!StringUtils.hasText(brandScopeId)) {
            return new PlanStatusSnapshot("unavailable", null, null, null, null);
        }
        if (!configReady) {
            return new PlanStatusSnapshot("unavailable", null, null, null, null);
        }
        ProbePlanVersion currentVersion = probePlanVersionMapper.selectOne(new QueryWrapper<ProbePlanVersion>()
                .eq("brand_scope_id", brandScopeId.trim())
                .eq("is_current", true)
                .eq("status_code", STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (currentVersion == null || !StringUtils.hasText(currentVersion.getId())) {
            return new PlanStatusSnapshot("draft_pending", null, null, null, null);
        }
        String status = Objects.equals(defaultInt(currentVersion.getConfigGenerationSnapshot()), defaultInt(currentConfigGeneration))
                ? "confirmed"
                : "needs_review";
        return new PlanStatusSnapshot(
                status,
                currentVersion.getVersionNo(),
                currentVersion.getActiveRecurringProbeCount(),
                currentVersion.getEstimatedRecurringCreditsPerCycle(),
                currentVersion.getId()
        );
    }

    private SnapshotRef recordRunAgainstCurrentPlan(SnapshotCommand command,
                                                   String changeTypeCode,
                                                   String executeModeCode) {
        ProbePlanVersion currentVersion = probePlanVersionMapper.selectOne(new QueryWrapper<ProbePlanVersion>()
                .eq("brand_scope_id", command.brandScopeId())
                .eq("is_current", true)
                .eq("status_code", STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (currentVersion == null || !StringUtils.hasText(currentVersion.getId())) {
            return snapshotCurrentConfig(command, changeTypeCode, executeModeCode, ACTION_KEEP);
        }
        BrandScope scope = brandScopeMapper.selectById(command.brandScopeId());
        if (scope == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探针方案缺少品牌空间底座");
        }
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new QueryWrapper<BrandSpaceTopic>()
                .eq("brand_scope_id", command.brandScopeId())
                .orderByAsc("seq_no")
                .orderByAsc("created_at"));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new QueryWrapper<BrandSpacePrompt>()
                .eq("brand_scope_id", command.brandScopeId())
                .orderByAsc("seq_no")
                .orderByAsc("created_at"));
        if (prompts == null || prompts.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探针方案缺少可快照的探针定义");
        }

        int recordNo = nextRecordNo(command.brandScopeId());
        int enabledProbeCount = resolveEffectiveProbeCount(command, prompts);
        String recordId = UUID.randomUUID().toString();
        probePlanRecordMapper.insert(ProbePlanRecord.builder()
                .id(recordId)
                .organizationId(command.organizationId())
                .brandId(command.brandId())
                .brandScopeId(command.brandScopeId())
                .planVersionId(currentVersion.getId())
                .recordNo(recordNo)
                .changeTypeCode(changeTypeCode)
                .executeModeCode(executeModeCode)
                .statusCode(RECORD_STATUS_CONFIRMED)
                .immediateEstimatedCredits(enabledProbeCount)
                .recurringEstimatedCreditsPerCycle(EXECUTE_MODE_RECURRING.equals(executeModeCode) ? enabledProbeCount : 0)
                .triggeredRunId(command.triggeredRunId())
                .summaryJson(buildRecordSummary(command, enabledProbeCount, topics.size(), prompts.size()))
                .createdByUserId(command.createdByUserId())
                .build());

        for (BrandSpacePrompt prompt : prompts) {
            probePlanRecordItemMapper.insert(ProbePlanRecordItem.builder()
                    .id(UUID.randomUUID().toString())
                    .recordId(recordId)
                    .planVersionId(currentVersion.getId())
                    .probeId(prompt.getId())
                    .topicId(prompt.getTopicId())
                    .actionCode(resolveActionCode(prompt, ACTION_KEEP))
                    .executeModeCode(executeModeCode)
                    .seqNoSnapshot(prompt.getSeqNo())
                    .probeTextSnapshot(prompt.getPromptText())
                    .sourceCodeSnapshot(prompt.getSourceCode())
                    .build());
        }

        return new SnapshotRef(
                currentVersion.getId(),
                recordId,
                currentVersion.getVersionNo(),
                recordNo,
                enabledProbeCount
        );
    }

    private SnapshotRef snapshotCurrentConfig(SnapshotCommand command,
                                              String changeTypeCode,
                                              String executeModeCode,
                                              String defaultActionCode) {
        BrandScope scope = brandScopeMapper.selectById(command.brandScopeId());
        if (scope == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探针方案缺少品牌空间底座");
        }
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new QueryWrapper<BrandSpaceTopic>()
                .eq("brand_scope_id", command.brandScopeId())
                .orderByAsc("seq_no")
                .orderByAsc("created_at"));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new QueryWrapper<BrandSpacePrompt>()
                .eq("brand_scope_id", command.brandScopeId())
                .orderByAsc("seq_no")
                .orderByAsc("created_at"));
        if (prompts == null || prompts.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探针方案缺少可快照的探针定义");
        }

        int versionNo = nextVersionNo(command.brandScopeId());
        int recordNo = nextRecordNo(command.brandScopeId());
        int enabledProbeCount = resolveEffectiveProbeCount(command, prompts);
        int configGeneration = defaultInt(scope.getConfigGeneration());
        String planVersionId = UUID.randomUUID().toString();
        String recordId = UUID.randomUUID().toString();

        probePlanVersionMapper.update(null, new UpdateWrapper<ProbePlanVersion>()
                .eq("brand_scope_id", command.brandScopeId())
                .eq("is_current", true)
                .set("is_current", false));

        probePlanVersionMapper.insert(ProbePlanVersion.builder()
                .id(planVersionId)
                .organizationId(command.organizationId())
                .brandId(command.brandId())
                .brandScopeId(command.brandScopeId())
                .versionNo(versionNo)
                .statusCode(STATUS_ACTIVE)
                .isCurrent(true)
                .configGenerationSnapshot(configGeneration)
                .activeRecurringProbeCount(EXECUTE_MODE_RECURRING.equals(executeModeCode) ? enabledProbeCount : 0)
                .estimatedImmediateCredits(enabledProbeCount)
                .estimatedRecurringCreditsPerCycle(EXECUTE_MODE_RECURRING.equals(executeModeCode) ? enabledProbeCount : 0)
                .snapshotJson(buildVersionSnapshot(command, scope, topics, prompts, configGeneration))
                .createdByUserId(command.createdByUserId())
                .build());

        probePlanRecordMapper.insert(ProbePlanRecord.builder()
                .id(recordId)
                .organizationId(command.organizationId())
                .brandId(command.brandId())
                .brandScopeId(command.brandScopeId())
                .planVersionId(planVersionId)
                .recordNo(recordNo)
                .changeTypeCode(changeTypeCode)
                .executeModeCode(executeModeCode)
                .statusCode(RECORD_STATUS_CONFIRMED)
                .immediateEstimatedCredits(enabledProbeCount)
                .recurringEstimatedCreditsPerCycle(EXECUTE_MODE_RECURRING.equals(executeModeCode) ? enabledProbeCount : 0)
                .triggeredRunId(command.triggeredRunId())
                .summaryJson(buildRecordSummary(command, enabledProbeCount, topics.size(), prompts.size()))
                .createdByUserId(command.createdByUserId())
                .build());

        for (BrandSpacePrompt prompt : prompts) {
            probePlanRecordItemMapper.insert(ProbePlanRecordItem.builder()
                    .id(UUID.randomUUID().toString())
                    .recordId(recordId)
                    .planVersionId(planVersionId)
                    .probeId(prompt.getId())
                    .topicId(prompt.getTopicId())
                    .actionCode(resolveActionCode(prompt, defaultActionCode))
                    .executeModeCode(executeModeCode)
                    .seqNoSnapshot(prompt.getSeqNo())
                    .probeTextSnapshot(prompt.getPromptText())
                    .sourceCodeSnapshot(prompt.getSourceCode())
                    .build());
        }

        return new SnapshotRef(planVersionId, recordId, versionNo, recordNo, enabledProbeCount);
    }

    private Map<String, Object> buildVersionSnapshot(SnapshotCommand command,
                                                     BrandScope scope,
                                                     List<BrandSpaceTopic> topics,
                                                     List<BrandSpacePrompt> prompts,
                                                     int configGeneration) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractVersion", CONTRACT_VERSION);
        snapshot.put("brandSpaceId", command.brandScopeId());
        snapshot.put("brandId", command.brandId());
        snapshot.put("brandName", firstText(command.brandName(), scope.getScopeName()));
        snapshot.put("configGeneration", configGeneration);
        snapshot.put("createdAt", LocalDateTime.now().toString());
        snapshot.put("targetModelProduct", command.targetModelProduct());
        snapshot.put("topics", topics.stream()
                .map(topic -> {
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    item.put("topicId", topic.getId());
                    item.put("name", topic.getName());
                    item.put("seqNo", topic.getSeqNo());
                    item.put("enabled", intEquals(topic.getStatusCode(), STATUS_ACTIVE));
                    return item;
                })
                .toList());
        snapshot.put("probes", prompts.stream()
                .map(prompt -> {
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    item.put("probeId", prompt.getId());
                    item.put("topicId", prompt.getTopicId());
                    item.put("seqNo", prompt.getSeqNo());
                    item.put("prompt", prompt.getPromptText());
                    item.put("enabled", intEquals(prompt.getStatusCode(), STATUS_ACTIVE));
                    item.put("sourceCode", prompt.getSourceCode());
                    item.put("userAuthoredSeed", intEquals(prompt.getSourceCode(), 1));
                    return item;
                })
                .toList());
        snapshot.put("pricing", Map.of(
                "unit", "CREDIT_PER_PROBE",
                "defaultCreditsPerProbe", defaultCreditsPerProbe(command)
        ));
        return snapshot;
    }

    private Map<String, Object> buildRecordSummary(SnapshotCommand command,
                                                   int enabledProbeCount,
                                                   int topicCount,
                                                   int probeCount) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("contractVersion", CONTRACT_VERSION);
        summary.put("brandSpaceId", command.brandScopeId());
        summary.put("brandName", command.brandName());
        summary.put("topicCount", topicCount);
        summary.put("probeCount", probeCount);
        summary.put("enabledProbeCount", enabledProbeCount);
        summary.put("executionTargetProbeCount", command.plannedProbeCount());
        summary.put("targetModelProduct", command.targetModelProduct());
        summary.put("creditsPerProbe", defaultCreditsPerProbe(command));
        summary.put("triggeredRunId", command.triggeredRunId());
        return summary;
    }

    private int defaultCreditsPerProbe(SnapshotCommand command) {
        return command != null && command.creditsPerProbe() != null && command.creditsPerProbe() > 0
                ? command.creditsPerProbe()
                : 1;
    }

    private int nextVersionNo(String brandScopeId) {
        ProbePlanVersion latest = probePlanVersionMapper.selectOne(new QueryWrapper<ProbePlanVersion>()
                .eq("brand_scope_id", brandScopeId)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private int nextRecordNo(String brandScopeId) {
        ProbePlanRecord latest = probePlanRecordMapper.selectOne(new QueryWrapper<ProbePlanRecord>()
                .eq("brand_scope_id", brandScopeId)
                .orderByDesc("record_no")
                .last("LIMIT 1"));
        return latest == null || latest.getRecordNo() == null ? 1 : latest.getRecordNo() + 1;
    }

    private SnapshotCommand normalize(SnapshotCommand command) {
        if (command == null
                || !StringUtils.hasText(command.organizationId())
                || !StringUtils.hasText(command.brandId())
                || !StringUtils.hasText(command.brandScopeId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探针方案快照缺少必要主键");
        }
        return new SnapshotCommand(
                command.organizationId().trim(),
                command.brandId().trim(),
                command.brandScopeId().trim(),
                normalizeText(command.brandName()),
                normalizeText(command.createdByUserId()),
                normalizeText(command.triggeredRunId()),
                command.plannedProbeCount(),
                normalizeText(command.targetModelProduct()),
                command.creditsPerProbe()
        );
    }

    private int resolveEffectiveProbeCount(SnapshotCommand command, List<BrandSpacePrompt> prompts) {
        if (command != null && command.plannedProbeCount() != null && command.plannedProbeCount() > 0) {
            return command.plannedProbeCount();
        }
        return (int) prompts.stream()
                .filter(prompt -> intEquals(prompt.getStatusCode(), STATUS_ACTIVE))
                .count();
    }

    private String resolveActionCode(BrandSpacePrompt prompt, String defaultActionCode) {
        if (prompt != null && intEquals(prompt.getStatusCode(), STATUS_DISABLED)) {
            return ACTION_DISABLE;
        }
        return defaultActionCode;
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : normalizeText(fallback);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record SnapshotCommand(String organizationId,
                                  String brandId,
                                  String brandScopeId,
                                  String brandName,
                                  String createdByUserId,
                                  String triggeredRunId,
                                  Integer plannedProbeCount,
                                  String targetModelProduct,
                                  Integer creditsPerProbe) {
        public SnapshotCommand(String organizationId,
                               String brandId,
                               String brandScopeId,
                               String brandName,
                               String createdByUserId,
                               String triggeredRunId) {
            this(organizationId, brandId, brandScopeId, brandName, createdByUserId, triggeredRunId, null, null, null);
        }

        public SnapshotCommand(String organizationId,
                               String brandId,
                               String brandScopeId,
                               String brandName,
                               String createdByUserId,
                               String triggeredRunId,
                               Integer plannedProbeCount) {
            this(organizationId, brandId, brandScopeId, brandName, createdByUserId, triggeredRunId, plannedProbeCount, null, null);
        }
    }

    public record SnapshotRef(String planVersionId,
                              String planRecordId,
                              Integer versionNo,
                              Integer recordNo,
                              Integer enabledProbeCount) {
    }

    public record PlanStatusSnapshot(String status,
                                     Integer versionNo,
                                     Integer activeRecurringProbeCount,
                                     Integer estimatedCreditsPerCycle,
                                     String planVersionId) {
    }
}
