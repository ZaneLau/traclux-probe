package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.LlmCostCalculator;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.SearchEvidence;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisNodeRun;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.DiagnosisTaskCompetitor;
import com.geoprobe.entity.v1.IntegrationOutboxRecord;
import com.geoprobe.entity.v1.ModelInvocationRecord;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.knowledge.service.KnowledgeAssetIngestionService;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisNodeRunMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProgressSnapshotMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskCompetitorMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.IntegrationOutboxRecordMapper;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.service.engine.v42.V42ExecutionObserver;
import com.geoprobe.service.engine.v42.V42ProgressStateSupport;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisTaskRuntimePersistenceService {

    private static final int MAX_FAILURE_REASON_CODE_LENGTH = 64;
    private static final String TASK_METADATA_RUNTIME_KEY = "_runtime";
    private static final String TASK_METADATA_BILLING_KEY = "billing";
    private static final String TASK_METADATA_ALIAS_CONFIG_KEY = "aliasConfig";
    private static final String TASK_METADATA_CREATE_PAYLOAD_KEY = "createDiagnosisPayloadSnapshot";
    private static final int NODE_STATUS_SUCCEEDED = 2;
    private static final int NODE_STATUS_FAILED = 3;
    private static final int REPORT_STATUS_READY = 1;
    private static final int MODULE_SAMPLING = 3;
    private static final BigDecimal DEFAULT_INPUT_COST_PER_1K = new BigDecimal("0.0008");
    private static final BigDecimal DEFAULT_OUTPUT_COST_PER_1K = new BigDecimal("0.008");
    private static final BigDecimal DEFAULT_SHORT_OUTPUT_COST_PER_1K = new BigDecimal("0.002");
    private static final BigDecimal DEFAULT_WEB_SEARCH_COST_PER_CALL = new BigDecimal("0.004");

    private final DiagnosisTenantContextService diagnosisTenantContextService;
    private final LlmCallLogMapper llmCallLogMapper;
    private final IntegrationOutboxRecordMapper integrationOutboxRecordMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisTaskCompetitorMapper diagnosisTaskCompetitorMapper;
    private final DiagnosisNodeRunMapper diagnosisNodeRunMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    private final ModelInvocationRecordMapper modelInvocationRecordMapper;
    private final DiagnosisEventRecordMapper diagnosisEventRecordMapper;
    private final DiagnosisProgressSnapshotMapper diagnosisProgressSnapshotMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final DiagnosisMapSupport diagnosisMapSupport;
    private final V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver;
    private final ObjectProvider<KnowledgeAssetIngestionService> knowledgeAssetIngestionServiceProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.database.v1.enabled:false}")
    private boolean enabled;

    @Value("${app.engine.sampling.realtime-search.cost.input-yuan-per-1k-token:0.0008}")
    private String inputCostPer1k;

    @Value("${app.engine.sampling.realtime-search.cost.output-yuan-per-1k-token:0.008}")
    private String outputCostPer1k;

    @Value("${app.engine.sampling.realtime-search.cost.output-short-yuan-per-1k-token:0.002}")
    private String shortOutputCostPer1k;

    @Value("${app.engine.sampling.realtime-search.cost.output-short-threshold:200}")
    private int shortOutputThreshold;

    @Value("${app.engine.sampling.realtime-search.cost.web-search-yuan-per-call:0.004}")
    private String webSearchCostPerCall;

    public boolean isEnabled() {
        return enabled;
    }

    public DiagnosisProgressSnapshot readProgressSnapshot(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return null;
        }
        return diagnosisProgressSnapshotMapper.selectById(diagnosisId.trim());
    }

    public DiagnosisTask readDiagnosisTask(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return null;
        }
        return diagnosisTaskMapper.selectById(diagnosisId.trim());
    }

    public Map<String, Object> readTaskOrderSnapshot(String diagnosisId) {
        DiagnosisTask task = readDiagnosisTask(diagnosisId);
        if (task == null || task.getTaskOrderJson() == null) {
            return Map.of();
        }
        return task.getTaskOrderJson();
    }

    public Map<String, Object> readTaskMetadataSnapshot(String diagnosisId) {
        DiagnosisTask task = readDiagnosisTask(diagnosisId);
        if (task == null || task.getTaskMetadataJson() == null) {
            return Map.of();
        }
        return task.getTaskMetadataJson();
    }

    public Map<String, Object> readTaskContextSnapshot(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return Map.of();
        }
        Map<String, Object> taskMetadata = mutableObjectMap(readTaskMetadataSnapshot(diagnosisId));
        taskMetadata.remove(TASK_METADATA_RUNTIME_KEY);
        return taskMetadata.isEmpty() ? Map.of() : taskMetadata;
    }

    public Map<String, Object> readTaskCreateDiagnosisPayloadSnapshot(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return Map.of();
        }
        Map<String, Object> taskMetadata = readTaskMetadataSnapshot(diagnosisId);
        Map<String, Object> runtimeMeta = readObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        return readObjectMap(runtimeMeta.get(TASK_METADATA_CREATE_PAYLOAD_KEY));
    }

    public Map<String, Object> readTaskBillingMetadata(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return Map.of();
        }
        Map<String, Object> taskMetadata = readTaskMetadataSnapshot(diagnosisId);
        Map<String, Object> runtimeMeta = readObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        return readObjectMap(runtimeMeta.get(TASK_METADATA_BILLING_KEY));
    }

    public Map<String, Object> readTaskAliasConfigMetadata(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return Map.of();
        }
        Map<String, Object> taskMetadata = readTaskMetadataSnapshot(diagnosisId);
        Map<String, Object> runtimeMeta = readObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        return readObjectMap(runtimeMeta.get(TASK_METADATA_ALIAS_CONFIG_KEY));
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertTaskBillingMetadata(String diagnosisId, Map<String, Object> billingMetadata) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || billingMetadata == null || billingMetadata.isEmpty()) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        Map<String, Object> taskMetadata = mutableObjectMap(task.getTaskMetadataJson());
        Map<String, Object> runtimeMeta = mutableObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        runtimeMeta.put(TASK_METADATA_BILLING_KEY, mutableObjectMap(billingMetadata));
        taskMetadata.put(TASK_METADATA_RUNTIME_KEY, runtimeMeta);
        task.setTaskMetadataJson(taskMetadata);
        diagnosisTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertTaskAliasConfigMetadata(String diagnosisId, Map<String, Object> aliasConfigMetadata) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || aliasConfigMetadata == null || aliasConfigMetadata.isEmpty()) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        Map<String, Object> taskMetadata = mutableObjectMap(task.getTaskMetadataJson());
        Map<String, Object> runtimeMeta = mutableObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        runtimeMeta.put(TASK_METADATA_ALIAS_CONFIG_KEY, mutableObjectMap(aliasConfigMetadata));
        taskMetadata.put(TASK_METADATA_RUNTIME_KEY, runtimeMeta);
        task.setTaskMetadataJson(taskMetadata);
        diagnosisTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertTaskCreateDiagnosisPayloadSnapshot(String diagnosisId, Map<String, Object> createDiagnosisPayloadSnapshot) {
        if (!enabled || !StringUtils.hasText(diagnosisId)
                || createDiagnosisPayloadSnapshot == null || createDiagnosisPayloadSnapshot.isEmpty()) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        Map<String, Object> taskMetadata = mutableObjectMap(task.getTaskMetadataJson());
        Map<String, Object> runtimeMeta = mutableObjectMap(taskMetadata.get(TASK_METADATA_RUNTIME_KEY));
        runtimeMeta.put(TASK_METADATA_CREATE_PAYLOAD_KEY, mutableObjectMap(createDiagnosisPayloadSnapshot));
        taskMetadata.put(TASK_METADATA_RUNTIME_KEY, runtimeMeta);
        task.setTaskMetadataJson(taskMetadata);
        diagnosisTaskMapper.updateById(task);
    }

    public DiagnosisService.ProbeEventPage readProbeEvents(String diagnosisId, Long afterSeq, Integer limit) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return new DiagnosisService.ProbeEventPage(List.of(), Math.max(0L, afterSeq == null ? 0L : afterSeq));
        }
        long cursor = afterSeq == null ? 0L : Math.max(0L, afterSeq);
        int effectiveLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        List<DiagnosisEventRecord> records = diagnosisEventRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisEventRecord>()
                .eq(DiagnosisEventRecord::getDiagnosisTaskId, diagnosisId.trim())
                .gt(DiagnosisEventRecord::getSeq, cursor)
                .orderByAsc(DiagnosisEventRecord::getSeq)
                .last("limit " + effectiveLimit));
        if (records == null || records.isEmpty()) {
            return new DiagnosisService.ProbeEventPage(List.of(), cursor);
        }
        List<DiagnosisService.ProbeEventItem> events = records.stream()
                .sorted(Comparator.comparing(DiagnosisEventRecord::getSeq))
                .map(record -> new DiagnosisService.ProbeEventItem(
                        record.getSeq() == null ? 0L : record.getSeq(),
                        defaultIfBlank(record.getEventType(), ""),
                        Boolean.TRUE.equals(record.getProvisional()),
                        record.getCreatedAt() == null ? LocalDateTime.now().toString() : record.getCreatedAt().toString(),
                        record.getPayloadJson() == null ? Map.of() : record.getPayloadJson()
                ))
                .toList();
        long nextSeq = events.get(events.size() - 1).seq();
        return new DiagnosisService.ProbeEventPage(events, nextSeq);
    }

    public DiagnosisService.ProgressStreamSnapshot buildProgressStreamSnapshot(Diagnosis diagnosis,
                                                                              DiagnosisProgressResponse.Progress progress,
                                                                              String stage,
                                                                              int stageIndex,
                                                                              String stageLabel,
                                                                              Integer etaSeconds) {
        DiagnosisTask task = readDiagnosisTask(diagnosis == null ? null : diagnosis.getId());
        return new DiagnosisService.ProgressStreamSnapshot(
                diagnosis == null ? null : diagnosis.getId(),
                task == null ? (diagnosis == null ? null : diagnosis.getStatus()) : task.getStatusCode(),
                task == null ? (diagnosis == null ? null : diagnosis.getErrorMessage()) : task.getFailureMessage(),
                progress.getCurrentStep(),
                progress.getCurrentStepLabel(),
                stage,
                stageIndex,
                5,
                stageLabel,
                progress.getPercent(),
                progress.getSamplingCompleted(),
                progress.getSamplingTotal(),
                progress.getSamplingExpectedTotal(),
                progress.getSamplingGeneratedTotal(),
                progress.getSamplingMissingTotal(),
                progress.getSamplingVariantShortfall(),
                etaSeconds
        );
    }

    public DiagnosisOpsMetricsResponse buildOpsMetrics(String diagnosisId, Integer legacyStatus, boolean refunded) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return null;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return null;
        }
        List<ModelInvocationRecord> invocations = modelInvocationRecordMapper.selectList(new LambdaQueryWrapper<ModelInvocationRecord>()
                .eq(ModelInvocationRecord::getDiagnosisTaskId, diagnosisId.trim()));
        List<ModelInvocationRecord> billableInvocations = invocations.stream()
                .filter(record -> !isNode3UsageSummaryInvocation(record.getNodeCode()))
                .toList();
        List<ModelInvocationRecord> node3DetailedInvocations = billableInvocations.stream()
                .filter(record -> isNode3Invocation(record.getNodeCode()))
                .filter(record -> StringUtils.hasText(record.getProbeId()))
                .toList();
        List<ModelInvocationRecord> node3AggregateInvocations = billableInvocations.stream()
                .filter(record -> isNode3Invocation(record.getNodeCode()))
                .filter(record -> !StringUtils.hasText(record.getProbeId()))
                .toList();
        List<ModelInvocationRecord> nonSamplingInvocations = billableInvocations.stream()
                .filter(record -> !isNode3Invocation(record.getNodeCode()))
                .toList();
        ModelInvocationRecord node3UsageSummary = invocations.stream()
                .filter(record -> isNode3UsageSummaryInvocation(record.getNodeCode()))
                .findFirst()
                .orElse(null);
        List<ModelInvocationRecord> node3MetricInvocations = node3DetailedInvocations.isEmpty()
                ? node3AggregateInvocations
                : node3DetailedInvocations;
        List<ModelInvocationRecord> metricInvocations = new ArrayList<>(nonSamplingInvocations);
        metricInvocations.addAll(node3MetricInvocations);
        boolean useNode3SummaryFallback = node3MetricInvocations.isEmpty() && node3UsageSummary != null;
        BigDecimal node3Cost = useNode3SummaryFallback
                ? zeroIfNull(node3UsageSummary.getTotalCostYuan())
                : node3MetricInvocations.stream()
                .map(ModelInvocationRecord::getTotalCostYuan)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal nonSamplingCost = nonSamplingInvocations.stream()
                .map(ModelInvocationRecord::getTotalCostYuan)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal totalCost = nonSamplingCost.add(node3Cost).setScale(6, RoundingMode.HALF_UP);
        long node3PromptTokens = useNode3SummaryFallback
                ? longOrZero(node3UsageSummary.getInputTokens())
                : node3MetricInvocations.stream()
                .map(ModelInvocationRecord::getInputTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long node3CompletionTokens = useNode3SummaryFallback
                ? longOrZero(node3UsageSummary.getOutputTokens())
                : node3MetricInvocations.stream()
                .map(ModelInvocationRecord::getOutputTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long node3TotalTokens = useNode3SummaryFallback
                ? longOrZero(node3UsageSummary.getTotalTokens())
                : node3MetricInvocations.stream()
                .map(ModelInvocationRecord::getTotalTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long promptTokens = nonSamplingInvocations.stream()
                .map(ModelInvocationRecord::getInputTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum() + node3PromptTokens;
        long completionTokens = nonSamplingInvocations.stream()
                .map(ModelInvocationRecord::getOutputTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum() + node3CompletionTokens;
        long totalTokens = nonSamplingInvocations.stream()
                .map(ModelInvocationRecord::getTotalTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum() + node3TotalTokens;
        Integer p95LatencyMs = computeP95Latency(metricInvocations);
        int totalSamples = task.getSamplingGeneratedCount() != null && task.getSamplingGeneratedCount() > 0
                ? task.getSamplingGeneratedCount()
                : defaultInt(task.getTargetProbeCount());
        int successSamples = defaultInt(task.getSamplingCompletedCount());
        int failedSamples = Math.max(defaultInt(task.getSamplingFailedCount()), totalSamples - successSamples);
        FallbackStats fallbackStats = loadFallbackStats(task.getId());
        return DiagnosisOpsMetricsResponse.builder()
                .diagnosisId(diagnosisId.trim())
                .completedRate(legacyStatus != null && legacyStatus == 2 ? 1.0d : 0.0d)
                .timeoutRate(0.0d)
                .fallbackRate(fallbackStats.rate())
                .refundRate(refunded ? 1.0d : 0.0d)
                .p95LatencyMs(p95LatencyMs)
                .totalLlmCostYuan(totalCost)
                .node3LlmCostYuan(node3Cost)
                .nonSamplingLlmCostYuan(nonSamplingCost)
                .fullChainPromptTokenTotal(promptTokens)
                .fullChainCompletionTokenTotal(completionTokens)
                .fullChainTokenTotal(totalTokens)
                .node3PromptTokenTotal(node3PromptTokens)
                .node3CompletionTokenTotal(node3CompletionTokens)
                .node3TokenTotal(node3TotalTokens)
                .totalApiCalls(Math.max(metricInvocations.size(), totalSamples))
                .totalSamples(totalSamples)
                .successSamples(successSamples)
                .failedSamples(failedSamples)
                .timeoutSamples(0)
                .fallbackUsed(fallbackStats.used())
                .refunded(refunded)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public DiagnosisTask ensureTaskShell(String diagnosisId,
                                         String userId,
                                         String brandName,
                                         String category,
                                         String brandWebsite,
                                         Integer sourceCode,
                                         Integer statusCode,
                                         FrontDeskTaskOrder taskOrder,
                                         Object contextPackage,
                                         String engineVersion) {
        return ensureTaskShell(
                diagnosisId,
                userId,
                brandName,
                category,
                brandWebsite,
                sourceCode,
                statusCode,
                taskOrder,
                contextPackage,
                engineVersion,
                Map.of()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public DiagnosisTask ensureTaskShell(String diagnosisId,
                                         String userId,
                                         String brandName,
                                         String category,
                                         String brandWebsite,
                                         Integer sourceCode,
                                         Integer statusCode,
                                         FrontDeskTaskOrder taskOrder,
                                         Object contextPackage,
                                         String engineVersion,
                                         Map<String, Object> createDiagnosisPayloadSnapshot) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || !StringUtils.hasText(userId) || taskOrder == null) {
            return null;
        }
        DiagnosisTenantContextService.TenantContext tenant = diagnosisTenantContextService.ensureBrandTenancy(
                userId.trim(),
                brandName,
                category,
                brandWebsite
        );
        if (tenant == null) {
            return null;
        }
        DiagnosisTask entity = buildDiagnosisTaskEntity(
                diagnosisId.trim(),
                tenant,
                brandName,
                category,
                brandWebsite,
                sourceCode,
                statusCode,
                null,
                null,
                null,
                taskOrder,
                contextPackage,
                engineVersion
        );
        upsertDiagnosisTask(entity);
        ensureReservedBillingSeed(diagnosisId.trim());
        ensureAliasConfigSeed(diagnosisId.trim(), taskOrder);
        ensureCreateDiagnosisPayloadSeed(diagnosisId.trim(), createDiagnosisPayloadSnapshot);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeTaskShell(String diagnosisId) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        String normalizedId = diagnosisId.trim();
        diagnosisTaskCompetitorMapper.delete(new LambdaQueryWrapper<DiagnosisTaskCompetitor>()
                .eq(DiagnosisTaskCompetitor::getDiagnosisTaskId, normalizedId));
        diagnosisProgressSnapshotMapper.deleteById(normalizedId);
        diagnosisTaskMapper.deleteById(normalizedId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void mirrorTaskCreated(Diagnosis diagnosis,
                                  FrontDeskTaskOrder taskOrder,
                                  Object contextPackage,
                                  String engineVersion) {
        mirrorTaskCreated(diagnosis, taskOrder, contextPackage, engineVersion, Map.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void mirrorTaskCreated(Diagnosis diagnosis,
                                  FrontDeskTaskOrder taskOrder,
                                  Object contextPackage,
                                  String engineVersion,
                                  Map<String, Object> createDiagnosisPayloadSnapshot) {
        if (!enabled || diagnosis == null || taskOrder == null) {
            return;
        }
        DiagnosisTenantContextService.TenantContext tenant = diagnosisTenantContextService.ensureBrandTenancy(
                diagnosis.getUserId(),
                diagnosis.getBrandName(),
                diagnosis.getCategory(),
                diagnosis.getBrandWebsite()
        );
        if (tenant == null) {
            return;
        }
        DiagnosisTask entity = buildDiagnosisTaskEntity(
                diagnosis.getId(),
                tenant,
                diagnosis.getBrandName(),
                diagnosis.getCategory(),
                diagnosis.getBrandWebsite(),
                diagnosis.getSource(),
                diagnosis.getStatus(),
                diagnosis.getStartedAt(),
                diagnosis.getCompletedAt(),
                diagnosis.getErrorMessage(),
                taskOrder,
                contextPackage,
                engineVersion
        );
        upsertDiagnosisTask(entity);
        ensureReservedBillingSeed(diagnosis.getId());
        ensureAliasConfigSeed(diagnosis.getId(), taskOrder);
        ensureCreateDiagnosisPayloadSeed(diagnosis.getId(), createDiagnosisPayloadSnapshot);
        int targetProbeCount = entity.getTargetProbeCount() == null ? 0 : entity.getTargetProbeCount();
        boolean realtimeSearchEnabled = Boolean.TRUE.equals(entity.getRealtimeSearchEnabled());
        enqueueOutbox("diagnosis_task", diagnosis.getId(), "diagnosis.task.created", mapOfNonNull(
                "diagnosisId", diagnosis.getId(),
                "organizationId", tenant.organization().getId(),
                "brandScopeId", tenant.brandScope() == null ? null : tenant.brandScope().getId(),
                "targetProbeCount", targetProbeCount,
                "realtimeSearchEnabled", realtimeSearchEnabled
        ));
        diagnosisTaskCompetitorMapper.delete(new LambdaQueryWrapper<DiagnosisTaskCompetitor>()
                .eq(DiagnosisTaskCompetitor::getDiagnosisTaskId, diagnosis.getId()));
        List<String> competitors = diagnosis.getCompetitorsInput() == null ? List.of() : diagnosis.getCompetitorsInput();
        for (int index = 0; index < competitors.size(); index++) {
            String competitor = competitors.get(index);
            if (!StringUtils.hasText(competitor)) {
                continue;
            }
            diagnosisTaskCompetitorMapper.insert(DiagnosisTaskCompetitor.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .diagnosisTaskId(diagnosis.getId())
                    .seqNo(index + 1)
                    .competitorNameSnapshot(competitor.trim())
                    .build());
        }
        upsertProgressSnapshot(
                diagnosis.getId(),
                tenant.organization().getId(),
                tenant.brandScope() == null ? null : tenant.brandScope().getId(),
                "CREATED",
                "任务已创建，等待引擎执行",
                0,
                0,
                0,
                targetProbeCount,
                0,
                targetProbeCount,
                false,
                0.0d,
                null,
                0,
                0,
                0,
                BigDecimal.ZERO
        );
    }

    private DiagnosisTask buildDiagnosisTaskEntity(String diagnosisId,
                                                   DiagnosisTenantContextService.TenantContext tenant,
                                                   String brandName,
                                                   String category,
                                                   String brandWebsite,
                                                   Integer sourceCode,
                                                   Integer statusCode,
                                                   LocalDateTime startedAt,
                                                   LocalDateTime completedAt,
                                                   String failureMessage,
                                                   FrontDeskTaskOrder taskOrder,
                                                   Object contextPackage,
                                                   String engineVersion) {
        String normalizedEngineVersion = StringUtils.hasText(engineVersion)
                ? engineVersion.trim()
                : LightConeV43VersionTruthSupport.ENGINE_RUNTIME_VERSION_V42;
        Map<String, Object> taskOrderSnapshot = normalizeTaskOrderContract(
                diagnosisMapSupport.toSnapshotMap(taskOrder),
                normalizedEngineVersion
        );
        int targetProbeCount = v42TaskOrderExecutionPolicyResolver
                .resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, "db_v1_task_create")
                .targetProbeCount();
        boolean realtimeSearchEnabled = v42TaskOrderExecutionPolicyResolver
                .resolveRealtimeSearchPolicyFromTaskOrderSnapshot(taskOrderSnapshot)
                .enabled();
        Map<String, Object> taskMetadataSnapshot = contextPackage == null
                ? Map.of()
                : diagnosisMapSupport.toSnapshotMap(contextPackage);
        Map<String, Object> taskOrderRunVersionTruth = LightConeV43VersionTruthSupport.readRunVersionTruth(
                taskOrderSnapshot,
                Map.of()
        );
        taskMetadataSnapshot = LightConeV43VersionTruthSupport.ensureTaskRuntimeContract(
                taskMetadataSnapshot,
                taskOrderRunVersionTruth,
                normalizedEngineVersion
        );
        return DiagnosisTask.builder()
                .id(diagnosisId)
                .organizationId(tenant.organization().getId())
                .brandId(tenant.brand() == null ? null : tenant.brand().getId())
                .brandScopeId(tenant.brandScope() == null ? null : tenant.brandScope().getId())
                .requestedByUserId(tenant.user().getId())
                .requestedByMembershipId(tenant.membership() == null ? null : tenant.membership().getId())
                .billingQuotaAccountId(tenant.quotaAccount() == null ? null : tenant.quotaAccount().getId())
                .sourceCode(sourceCode)
                .targetProbeCount(targetProbeCount)
                .realtimeSearchEnabled(realtimeSearchEnabled)
                .targetModelProduct(taskOrder.targetModelProduct())
                .engineVersion(normalizedEngineVersion)
                .billingModeCode(0)
                .billingPolicyVersion("v1")
                .statusCode(statusCode)
                .progressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .samplingGeneratedCount(0)
                .samplingCompletedCount(0)
                .samplingFailedCount(0)
                .samplingSkippedCount(0)
                .totalCostYuan(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                .searchRequestedCount(0)
                .searchSatisfiedCount(0)
                .locale(defaultIfBlank(taskOrder.locale(), "zh-CN"))
                .language(defaultIfBlank(taskOrder.language(), "zh-CN"))
                .diagnosisGoal(taskOrder.runGoal())
                .brandNameSnapshot(brandName)
                .categorySnapshot(category)
                .officialWebsiteSnapshot(brandWebsite)
                .taskOrderJson(taskOrderSnapshot)
                .taskMetadataJson(taskMetadataSnapshot)
                .chargePolicySnapshotJson(Map.of("legacyCreditCostPerDiagnosis", 1))
                .startedAt(startedAt)
                .completedAt(completedAt)
                .failureMessage(failureMessage)
                .build();
    }

    private void upsertDiagnosisTask(DiagnosisTask entity) {
        if (entity == null || !StringUtils.hasText(entity.getId())) {
            return;
        }
        DiagnosisTask existing = diagnosisTaskMapper.selectById(entity.getId());
        if (existing == null) {
            diagnosisTaskMapper.insert(entity);
        } else {
            entity.setTaskMetadataJson(mergeTaskMetadataPreservingRuntime(existing.getTaskMetadataJson(), entity.getTaskMetadataJson()));
            diagnosisTaskMapper.updateById(entity);
        }
    }

    private void ensureReservedBillingSeed(String diagnosisId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId);
        if (task == null) {
            return;
        }
        Map<String, Object> existingBilling = readTaskBillingMetadata(diagnosisId);
        if (!existingBilling.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        upsertTaskBillingMetadata(diagnosisId, mapOfNonNull(
                "billingState", "reserved",
                "billingTimeline", List.of(Map.of("state", "reserved", "at", now.toString()))
        ));
    }

    private void ensureAliasConfigSeed(String diagnosisId, FrontDeskTaskOrder taskOrder) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId);
        if (task == null) {
            return;
        }
        Map<String, Object> existingAliasConfig = readTaskAliasConfigMetadata(diagnosisId);
        if (!existingAliasConfig.isEmpty()) {
            return;
        }
        Map<String, Object> aliasSeed = buildAliasConfigMetadata(taskOrder);
        if (aliasSeed.isEmpty()) {
            return;
        }
        upsertTaskAliasConfigMetadata(diagnosisId, aliasSeed);
    }

    private void ensureCreateDiagnosisPayloadSeed(String diagnosisId, Map<String, Object> createDiagnosisPayloadSnapshot) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId);
        if (task == null) {
            return;
        }
        Map<String, Object> existingPayload = readTaskCreateDiagnosisPayloadSnapshot(diagnosisId);
        if (!existingPayload.isEmpty()) {
            return;
        }
        if (createDiagnosisPayloadSnapshot == null || createDiagnosisPayloadSnapshot.isEmpty()) {
            return;
        }
        upsertTaskCreateDiagnosisPayloadSnapshot(diagnosisId, createDiagnosisPayloadSnapshot);
    }

    private Map<String, Object> buildAliasConfigMetadata(FrontDeskTaskOrder taskOrder) {
        if (taskOrder == null || taskOrder.businessHints().isEmpty()) {
            return Map.of();
        }
        List<String> brandAliases = readStringList(taskOrder.businessHints().get("brandAliases"));
        Map<String, List<String>> competitorAliases = readStringListMap(taskOrder.businessHints().get("competitorAliases"));
        if (brandAliases.isEmpty() && competitorAliases.isEmpty()) {
            return Map.of();
        }
        return mapOfNonNull(
                "brandAliases", brandAliases,
                "competitorAliases", competitorAliases,
                "aliasConfigVersion", "1",
                "aliasUpdatedAt", LocalDateTime.now().toString()
        );
    }

    private Map<String, Object> mergeTaskMetadataPreservingRuntime(Map<String, Object> existingMetadata,
                                                                   Map<String, Object> incomingMetadata) {
        Map<String, Object> merged = mutableObjectMap(existingMetadata);
        merged.putAll(mutableObjectMap(incomingMetadata));
        Map<String, Object> incomingRuntime = mutableObjectMap(mutableObjectMap(incomingMetadata).get(TASK_METADATA_RUNTIME_KEY));
        Map<String, Object> existingRuntime = mutableObjectMap(mutableObjectMap(existingMetadata).get(TASK_METADATA_RUNTIME_KEY));
        if (!incomingRuntime.isEmpty() || !existingRuntime.isEmpty()) {
            LinkedHashMap<String, Object> runtimeMerged = new LinkedHashMap<>(incomingRuntime);
            runtimeMerged.putAll(existingRuntime);
            merged.put(TASK_METADATA_RUNTIME_KEY, runtimeMerged);
        }
        return merged.isEmpty() ? Map.of() : merged;
    }

    private Map<String, Object> normalizeTaskOrderContract(Map<String, Object> taskOrderSnapshot,
                                                           String engineVersion) {
        Map<String, Object> normalized = mutableObjectMap(taskOrderSnapshot);
        String runGoal = defaultIfBlank(
                objectText(normalized.get("runGoal")),
                objectText(normalized.get("diagnosisGoal"))
        );
        if (StringUtils.hasText(runGoal)) {
            normalized.put("runGoal", runGoal);
        }
        normalized.remove("diagnosisGoal");
        normalized.put(
                "taskMetadata",
                LightConeV43VersionTruthSupport.ensureTaskMetadataContract(
                        mutableObjectMap(normalized.get("taskMetadata")),
                        engineVersion
                )
        );
        return normalized.isEmpty() ? Map.of() : normalized;
    }

    private Map<String, Object> readRunVersionTruth(DiagnosisTask task) {
        if (task == null) {
            return Map.of();
        }
        return LightConeV43VersionTruthSupport.readRunVersionTruth(task.getTaskOrderJson(), task.getTaskMetadataJson());
    }

    private String buildTopicBindingSeed(V42DiagnosisResult.ProbeTrace trace) {
        if (trace == null) {
            return "v42_probe_trace";
        }
        return String.join("|",
                defaultIfBlank(trace.probeId(), "probe"),
                String.valueOf(trace.abstractTags()),
                String.valueOf(trace.translatedTags()),
                defaultIfBlank(trace.surfaceIntent(), ""),
                defaultIfBlank(trace.conversationMode(), ""),
                trace.exposurePolicy() == null ? "" : trace.exposurePolicy().contractValue()
        );
    }

    private Map<String, Object> resolveProbeVersionTruth(Map<String, Object> runVersionTruth,
                                                         V42DiagnosisResult.ProbeTrace trace) {
        String topicBindingSeed = buildTopicBindingSeed(trace);
        String overrideReason = resolveProbeOverrideReason(trace);
        if (shouldUseLegacyLiteralProbeTruth(runVersionTruth, trace)) {
            return LightConeV43VersionTruthSupport.legacyLiteralProbeVersionTruth(
                    defaultIfBlank(trace == null ? null : trace.renderFamily(), "legacy_prompt_bridge"),
                    topicBindingSeed,
                    overrideReason
            );
        }
        String migrationMode = objectText(runVersionTruth == null ? null : runVersionTruth.get("migrationMode"));
        if (LightConeV43VersionTruthSupport.MIGRATION_MODE_SHADOW.equals(migrationMode)) {
            return LightConeV43VersionTruthSupport.canonicalShadowProbeVersionTruth(
                    "topic_compiler_runtime",
                    topicBindingSeed,
                    "canonical_render_v1_shadow_runtime",
                    overrideReason
            );
        }
        return LightConeV43VersionTruthSupport.canonicalProbeVersionTruth(
                "topic_compiler_runtime",
                topicBindingSeed,
                "canonical_render_v1_runtime",
                overrideReason
        );
    }

    private boolean shouldUseLegacyLiteralProbeTruth(Map<String, Object> runVersionTruth,
                                                     V42DiagnosisResult.ProbeTrace trace) {
        String migrationMode = objectText(runVersionTruth == null ? null : runVersionTruth.get("migrationMode"));
        String executionLane = objectText(runVersionTruth == null ? null : runVersionTruth.get("executionLane"));
        if (!StringUtils.hasText(migrationMode) && !StringUtils.hasText(executionLane)) {
            return true;
        }
        if (LightConeV43VersionTruthSupport.MIGRATION_MODE_LEGACY_LITERAL.equals(migrationMode)
                || LightConeV43VersionTruthSupport.EXECUTION_LANE_LEGACY.equals(executionLane)) {
            return true;
        }
        if (trace == null) {
            return false;
        }
        if ("brand_space_literal_prompt".equals(defaultIfBlank(trace.renderFamily(), ""))) {
            return true;
        }
        return trace.triggeredRulesJson() != null
                && StringUtils.hasText(objectText(trace.triggeredRulesJson().get("brandSpacePromptBridgeVersion")));
    }

    private String resolveProbeOverrideReason(V42DiagnosisResult.ProbeTrace trace) {
        if (trace == null || trace.triggeredRulesJson() == null || trace.triggeredRulesJson().isEmpty()) {
            return null;
        }
        String explicitReason = objectText(trace.triggeredRulesJson().get("overrideReason"));
        if (StringUtils.hasText(explicitReason)) {
            return explicitReason;
        }
        if (StringUtils.hasText(objectText(trace.triggeredRulesJson().get("brandSpacePromptBridgeVersion")))) {
            return "brand_space_prompt_bridge_override";
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void onTaskRunning(String diagnosisId, LocalDateTime startedAt) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        task.setStatusCode(1);
        task.setStartedAt(startedAt == null ? LocalDateTime.now() : startedAt);
        diagnosisTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onTaskTerminal(String diagnosisId, Integer statusCode, String failureMessage, LocalDateTime completedAt) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        task.setStatusCode(statusCode);
        task.setCompletedAt(completedAt == null ? LocalDateTime.now() : completedAt);
        task.setFailureMessage(failureMessage);
        if (statusCode != null && statusCode == 4) {
            task.setCancelledAt(task.getCompletedAt());
        }
        diagnosisTaskMapper.updateById(task);
        upsertProgressSnapshot(
                task.getId(),
                task.getOrganizationId(),
                task.getBrandScopeId(),
                resolveTerminalStepCode(statusCode),
                resolveTerminalStepLabel(statusCode),
                0,
                11,
                task.getSamplingCompletedCount() == null ? 0 : task.getSamplingCompletedCount(),
                task.getTargetProbeCount() == null ? 0 : task.getTargetProbeCount(),
                task.getSamplingGeneratedCount() == null ? 0 : task.getSamplingGeneratedCount(),
                Math.max(0, (task.getTargetProbeCount() == null ? 0 : task.getTargetProbeCount())
                        - (task.getSamplingGeneratedCount() == null ? 0 : task.getSamplingGeneratedCount())),
                false,
                task.getProgressPercent() == null ? 0.0d : task.getProgressPercent().doubleValue(),
                null,
                task.getSearchRequestedCount() == null ? 0 : task.getSearchRequestedCount(),
                task.getSearchSatisfiedCount() == null ? 0 : task.getSearchSatisfiedCount(),
                task.getSamplingFailedCount() == null ? 0 : task.getSamplingFailedCount(),
                task.getTotalCostYuan()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void onProgress(String diagnosisId,
                           String currentStep,
                           String currentStepLabel,
                           int stepsCompleted,
                           int stepsTotal,
                           int samplingCompleted,
                           int samplingExpectedTotal,
                           int samplingGeneratedTotal,
                           int samplingMissingTotal,
                           boolean samplingVariantShortfall,
                           double percent) {
        if (!enabled || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId.trim());
        if (task == null) {
            return;
        }
        if (shouldPromoteTaskToRunning(task, currentStep, percent, samplingCompleted, samplingGeneratedTotal)) {
            task.setStatusCode(1);
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
        }
        task.setProgressPercent(scale2(percent));
        task.setSamplingCompletedCount(samplingCompleted);
        task.setSamplingGeneratedCount(samplingGeneratedTotal);
        task.setSamplingFailedCount(Math.max(0, samplingGeneratedTotal - samplingCompleted));
        diagnosisTaskMapper.updateById(task);
        upsertProgressSnapshot(
                task.getId(),
                task.getOrganizationId(),
                task.getBrandScopeId(),
                currentStep,
                currentStepLabel,
                stepsCompleted,
                stepsTotal,
                samplingCompleted,
                samplingExpectedTotal,
                samplingGeneratedTotal,
                samplingMissingTotal,
                samplingVariantShortfall,
                percent,
                null,
                task.getSearchRequestedCount() == null ? 0 : task.getSearchRequestedCount(),
                task.getSearchSatisfiedCount() == null ? 0 : task.getSearchSatisfiedCount(),
                task.getSamplingFailedCount() == null ? 0 : task.getSamplingFailedCount(),
                task.getTotalCostYuan()
        );
    }

    private boolean shouldPromoteTaskToRunning(DiagnosisTask task,
                                               String currentStep,
                                               double percent,
                                               int samplingCompleted,
                                               int samplingGeneratedTotal) {
        if (task == null) {
            return false;
        }
        Integer statusCode = task.getStatusCode();
        if (!(statusCode == null || statusCode == 0 || statusCode == 5)) {
            return false;
        }
        if (samplingCompleted > 0 || samplingGeneratedTotal > 0 || percent > 0.0d) {
            return true;
        }
        String normalizedStep = defaultIfBlank(currentStep, "").trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedStep)) {
            return false;
        }
        return !"CREATED".equals(normalizedStep)
                && !"QUEUED".equals(normalizedStep)
                && !"PENDING".equals(normalizedStep);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onEvent(String diagnosisId, DiagnosisService.ProbeEventItem item) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || item == null) {
            return;
        }
        if (diagnosisEventRecordMapper.selectById(item.seq()) != null) {
            return;
        }
        diagnosisEventRecordMapper.insert(DiagnosisEventRecord.builder()
                .seq(item.seq())
                .diagnosisTaskId(diagnosisId.trim())
                .eventType(item.eventType())
                .provisional(item.provisional())
                .payloadJson(item.payload())
                .createdAt(parseDateTime(item.createdAt()))
                .build());
        DiagnosisProgressSnapshot snapshot = diagnosisProgressSnapshotMapper.selectById(diagnosisId.trim());
        if (snapshot != null) {
            snapshot.setLastEventSeq(item.seq());
            diagnosisProgressSnapshotMapper.updateById(snapshot);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void onStageCompleted(String diagnosisId, V42ExecutionObserver.StageTrace trace) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || trace == null) {
            return;
        }
        upsertNodeRun(diagnosisId.trim(), trace.stage(), NODE_STATUS_SUCCEEDED, trace.durationMillis(), trace.summary(), null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onExecutionFailed(String diagnosisId, V42ExecutionObserver.FailureTrace trace) {
        if (!enabled || !StringUtils.hasText(diagnosisId) || trace == null) {
            return;
        }
        upsertNodeRun(diagnosisId.trim(), trace.stage(), NODE_STATUS_FAILED, trace.elapsedMillis(), Map.of(), trace.errorType(), trace.message());
    }

    @Transactional(rollbackFor = Exception.class)
    public void onDiagnosisCompleted(Diagnosis diagnosis,
                                     V42DiagnosisResult result,
                                     Map<String, Object> reportData) {
        if (!enabled || diagnosis == null || result == null) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosis.getId());
        if (task == null) {
            return;
        }
        task.setStatusCode(2);
        task.setCompletedAt(diagnosis.getCompletedAt() == null ? LocalDateTime.now() : diagnosis.getCompletedAt());
        task.setProgressPercent(new BigDecimal("100.00"));
        task.setSamplingGeneratedCount(result.task().sampleSize());
        task.setSamplingCompletedCount((int) result.task().successfulProbes());
        task.setSamplingFailedCount((int) result.task().failedProbes());
        task.setSearchRequestedCount(result.task().realtimeSearchSummary().requestedProbeCount());
        task.setSearchSatisfiedCount(result.task().realtimeSearchSummary().satisfiedProbeCount());
        BigDecimal fullChainCost = resolveFullChainCost(diagnosis.getId(), result.task().usageCostSummary());
        task.setTotalCostYuan(fullChainCost);
        diagnosisTaskMapper.updateById(task);
        upsertProgressSnapshot(
                task.getId(),
                task.getOrganizationId(),
                task.getBrandScopeId(),
                "COMPLETED",
                "诊断完成，报告已可查看",
                11,
                11,
                (int) result.task().successfulProbes(),
                result.task().sampleSize(),
                result.task().sampleSize(),
                0,
                false,
                100.0d,
                null,
                result.task().realtimeSearchSummary().requestedProbeCount(),
                result.task().realtimeSearchSummary().satisfiedProbeCount(),
                (int) result.task().failedProbes(),
                fullChainCost
        );
        mirrorProbeData(task, result);
        mirrorModelInvocations(task.getId(), result.task().usageCostSummary(), diagnosis.getId());
        upsertReport(task, reportData, result);
        enqueueOutbox("diagnosis_task", task.getId(), "diagnosis.report.ready", mapOfNonNull(
                "diagnosisId", task.getId(),
                "organizationId", task.getOrganizationId(),
                "brandScopeId", task.getBrandScopeId(),
                "sampleSize", result.report().sampleSize(),
                "totalCostYuan", fullChainCost,
                "searchSatisfiedCount", result.task().realtimeSearchSummary().satisfiedProbeCount()
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public void onDiagnosisSamplingFailed(Diagnosis diagnosis, V42DiagnosisResult.Task taskResult) {
        if (!enabled || diagnosis == null || taskResult == null) {
            return;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosis.getId());
        if (task == null) {
            return;
        }
        DiagnosisProgressSnapshot existingSnapshot = diagnosisProgressSnapshotMapper.selectById(task.getId());
        V42ProgressStateSupport.FailureProgressState failureProgress = V42ProgressStateSupport.recoverFailureProgress(
                existingSnapshot,
                Math.toIntExact(taskResult.successfulProbes()),
                taskResult.probeTrace().size(),
                taskResult.sampleSize()
        );
        task.setStatusCode(3);
        task.setCompletedAt(taskResult.completedAt() == null ? LocalDateTime.now() : taskResult.completedAt());
        task.setProgressPercent(failureProgress.percentAsBigDecimal());
        task.setSamplingGeneratedCount(taskResult.sampleSize());
        task.setSamplingCompletedCount((int) taskResult.successfulProbes());
        task.setSamplingFailedCount((int) taskResult.failedProbes());
        task.setSearchRequestedCount(taskResult.realtimeSearchSummary().requestedProbeCount());
        task.setSearchSatisfiedCount(taskResult.realtimeSearchSummary().satisfiedProbeCount());
        BigDecimal fullChainCost = resolveFullChainCost(diagnosis.getId(), taskResult.usageCostSummary());
        task.setTotalCostYuan(fullChainCost);
        diagnosisTaskMapper.updateById(task);
        upsertProgressSnapshot(
                task.getId(),
                task.getOrganizationId(),
                task.getBrandScopeId(),
                "FAILED",
                "诊断执行失败",
                failureProgress.stepsCompleted(),
                failureProgress.stepsTotal(),
                failureProgress.samplingCompleted(),
                failureProgress.samplingExpectedTotal(),
                failureProgress.samplingGeneratedTotal(),
                failureProgress.samplingMissingTotal(),
                failureProgress.samplingVariantShortfall(),
                failureProgress.percent(),
                failureProgress.lastEventSeq(),
                taskResult.realtimeSearchSummary().requestedProbeCount(),
                taskResult.realtimeSearchSummary().satisfiedProbeCount(),
                (int) taskResult.failedProbes(),
                fullChainCost
        );
        mirrorProbeArtifacts(task, taskResult, taskResult.probeTrace());
        mirrorModelInvocations(task.getId(), taskResult.usageCostSummary(), diagnosis.getId());
    }

    private BigDecimal resolveFullChainCost(String legacyDiagnosisId,
                                            com.geoprobe.dto.engine.v42.Node3UsageCostSummary usageCostSummary) {
        BigDecimal nonSamplingCost = llmCallLogMapper.selectList(new LambdaQueryWrapper<LlmCallLog>()
                        .eq(LlmCallLog::getDiagnosisId, legacyDiagnosisId)
                        .and(wrapper -> wrapper
                                .ne(LlmCallLog::getModule, MODULE_SAMPLING)
                                .or()
                                .isNull(LlmCallLog::getModule)))
                .stream()
                .map(LlmCallLog::getCostYuan)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal node3Cost = usageCostSummary == null
                ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                : zeroIfNull(usageCostSummary.totalCostYuan());
        return nonSamplingCost.add(node3Cost).setScale(6, RoundingMode.HALF_UP);
    }

    private void mirrorProbeData(DiagnosisTask task, V42DiagnosisResult result) {
        mirrorProbeArtifacts(task, result.task(), result.task().probeTrace());
    }

    private void mirrorProbeArtifacts(DiagnosisTask task,
                                      V42DiagnosisResult.Task taskResult,
                                      List<V42DiagnosisResult.ProbeTrace> probeTraces) {
        clearMirroredProbeArtifacts(task.getId());
        MirroredProbeArtifacts mirrored = mirrorProbeRows(task, taskResult, probeTraces);
        mirrorBrandMentions(task, probeTraces, mirrored);
    }

    private void clearMirroredProbeArtifacts(String diagnosisTaskId) {
        modelInvocationRecordMapper.delete(new LambdaQueryWrapper<ModelInvocationRecord>()
                .eq(ModelInvocationRecord::getDiagnosisTaskId, diagnosisTaskId)
                .isNotNull(ModelInvocationRecord::getProbeId));
        probeEvidenceRecordMapper.delete(new LambdaQueryWrapper<ProbeEvidenceRecord>()
                .eq(ProbeEvidenceRecord::getDiagnosisTaskId, diagnosisTaskId));
        probeBrandMentionRecordMapper.delete(new LambdaQueryWrapper<ProbeBrandMentionRecord>()
                .eq(ProbeBrandMentionRecord::getDiagnosisTaskId, diagnosisTaskId));
        probeDispatchAttemptMapper.delete(new LambdaQueryWrapper<ProbeDispatchAttempt>()
                .eq(ProbeDispatchAttempt::getDiagnosisTaskId, diagnosisTaskId));
        diagnosisProbeRecordMapper.delete(new LambdaQueryWrapper<DiagnosisProbeRecord>()
                .eq(DiagnosisProbeRecord::getDiagnosisTaskId, diagnosisTaskId));
    }

    private MirroredProbeArtifacts mirrorProbeRows(DiagnosisTask task,
                                                   V42DiagnosisResult.Task taskResult,
                                                   List<V42DiagnosisResult.ProbeTrace> probeTraces) {
        Map<String, String> probeIdToRecordId = new LinkedHashMap<>();
        Map<String, String> recordIdToAttemptId = new LinkedHashMap<>();
        Map<String, Object> runVersionTruth = readRunVersionTruth(task);
        int sequence = 0;
        for (V42DiagnosisResult.ProbeTrace trace : probeTraces) {
            sequence++;
            String recordId = java.util.UUID.randomUUID().toString();
            probeIdToRecordId.put(trace.probeId(), recordId);
            Map<String, Object> probeVersionTruth = resolveProbeVersionTruth(runVersionTruth, trace);
            LightConeV43VersionTruthSupport.assertCompatible(runVersionTruth, probeVersionTruth);
            diagnosisProbeRecordMapper.insert(DiagnosisProbeRecord.builder()
                    .id(recordId)
                    .diagnosisTaskId(task.getId())
                    .probeRef(trace.probeId())
                    .seqNo(sequence)
                    .bucketTypeCode(trace.bucketType().name())
                    .probeTypeCode(trace.probeType().name())
                    .d1Identity(trace.abstractTags().getOrDefault("D1", ""))
                    .d2Context(trace.abstractTags().getOrDefault("D2", ""))
                    .d3PainPoint(trace.abstractTags().getOrDefault("D3", ""))
                    .d4Jtbd(trace.abstractTags().getOrDefault("D4", ""))
                    .d5Knowledge(trace.abstractTags().getOrDefault("D5", ""))
                    .d6Constraints(trace.abstractTags().getOrDefault("D6", ""))
                    .surfaceIntent(trace.surfaceIntent())
                    .interactionPosture(trace.interactionPosture())
                    .conversationMode(trace.conversationMode())
                    .renderText(trace.renderedPrompt())
                    .renderSourceCode(mapRenderSourceCode(objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("renderSource"))))
                    .qualityGatePassed(true)
                    .fallbackUsed(inferFallbackUsed(trace))
                    .renderMetadataJson(LightConeV43VersionTruthSupport.ensureProbeMetadataContract(
                            mapOfNonNull(
                                    "abstractTags", trace.abstractTags(),
                                    "translatedTags", trace.translatedTags(),
                                    "exposurePolicy", trace.exposurePolicy().contractValue(),
                                    "renderSource", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("renderSource")),
                                    "renderReason", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("renderReason")),
                                    "renderFamily", defaultIfBlank(trace.renderFamily(), "default"),
                                    "failureReason", trace.failureReason(),
                                    "fallbackUsed", trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("fallbackUsed"),
                                    "validationFailedChecks", trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("validationFailedChecks"),
                                    "validationRejectedCandidate", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("validationRejectedCandidate")),
                                    "validationRepairApplied", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("validationRepairApplied")),
                                    "promptAssetKey", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("promptAssetKey")),
                                    "promptAssetVersion", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("promptAssetVersion")),
                                    "promptAssetSource", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("promptAssetSource")),
                                    "promptAssetSha256", objectText(trace.triggeredRulesJson() == null ? null : trace.triggeredRulesJson().get("promptAssetSha256"))
                            ),
                            probeVersionTruth
                    ))
                    .triggeredRulesJson(trace.triggeredRulesJson())
                    .statusCode(mapDispatchStatus(trace.dispatchStatus()))
                    .build());
            String attemptId = java.util.UUID.randomUUID().toString();
            recordIdToAttemptId.put(recordId, attemptId);
            String provider = resolveProviderForAttempt(task, taskResult, trace);
            String modelId = resolveModelIdForAttempt(task, taskResult, trace);
            probeDispatchAttemptMapper.insert(ProbeDispatchAttempt.builder()
                    .id(attemptId)
                    .diagnosisTaskId(task.getId())
                    .probeId(recordId)
                    .roundNo(trace.roundNo())
                    .attemptNo(trace.attemptNo())
                    .provider(provider)
                    .modelId(modelId)
                    .routingIntentCode(trace.routingIntent())
                    .routingCandidatesJson(trace.routingCandidates())
                    .searchRequested(trace.searchRequested())
                    .searchSatisfied(trace.searchSatisfied())
                    .searchFallbackFlag(trace.searchFallbackFlag())
                    .searchCallCount(trace.searchCallCount())
                    .searchQueriesJson(trace.searchQueries())
                    .rawResponseText(trace.fullResponseText())
                    .thinkContent(trace.thinkContent())
                    .citationsJson(trace.citations())
                    .responseMetadataJson(mapOfNonNull(
                            "responseLengthBucket", trace.responseLengthBucket(),
                            "detectionLevel", trace.detectionLevel(),
                            "responsePreview", trace.responseText(),
                            "failureReason", trace.failureReason()
                    ))
                    .inputTokens(trace.promptTokenCount())
                    .outputTokens(trace.responseTokenCount())
                    .totalTokens(trace.totalTokenCount())
                    .latencyMs(toInt(trace.latencyMs()))
                    .statusCode(mapDispatchStatus(trace.dispatchStatus()))
                    .failureReasonCode(normalizeFailureReasonCode(trace.failureReason()))
                    .selectedFinal(true)
                    .build());
            insertProbeModelInvocation(task, recordId, attemptId, trace, provider, modelId);
            for (SearchEvidence evidence : trace.searchEvidence()) {
                probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .dispatchAttemptId(attemptId)
                        .diagnosisTaskId(task.getId())
                        .probeId(recordId)
                        .sourceTypeCode(defaultIfBlank(evidence.sourceType(), "web"))
                        .provider(evidence.provider())
                        .domain(extractHost(evidence.url()))
                        .url(evidence.url())
                        .title(evidence.title())
                        .snippet(evidence.snippet())
                        .searchQuery(evidence.query())
                        .retrievedAt(parseDateTime(evidence.retrievedAt()))
                        .build());
            }
        }
        return new MirroredProbeArtifacts(Map.copyOf(probeIdToRecordId), Map.copyOf(recordIdToAttemptId));
    }

    private void insertProbeModelInvocation(DiagnosisTask task,
                                            String probeRecordId,
                                            String attemptId,
                                            V42DiagnosisResult.ProbeTrace trace,
                                            String provider,
                                            String modelId) {
        BigDecimal modelCost = estimateModelCostYuan(provider, modelId, trace.promptTokenCount(), trace.responseTokenCount());
        BigDecimal pluginCost = estimateSearchPluginCostYuan(trace.searchCallCount());
        modelInvocationRecordMapper.insert(ModelInvocationRecord.builder()
                .id(java.util.UUID.randomUUID().toString())
                .diagnosisTaskId(task.getId())
                .probeId(probeRecordId)
                .dispatchAttemptId(attemptId)
                .nodeCode("NODE3_SAMPLING")
                .provider(provider)
                .modelId(modelId)
                .inputTokens(trace.promptTokenCount())
                .outputTokens(trace.responseTokenCount())
                .totalTokens(trace.totalTokenCount())
                .latencyMs(toInt(trace.latencyMs()))
                .modelCostYuan(modelCost)
                .pluginCostYuan(pluginCost)
                .totalCostYuan(modelCost.add(pluginCost).setScale(6, RoundingMode.HALF_UP))
                .statusCode(mapDispatchStatus(trace.dispatchStatus()))
                .errorCode(normalizeFailureReasonCode(trace.failureReason()))
                .build());
    }

    private void mirrorBrandMentions(DiagnosisTask task,
                                     List<V42DiagnosisResult.ProbeTrace> probeTraces,
                                     MirroredProbeArtifacts mirrored) {
        for (V42DiagnosisResult.ProbeTrace trace : probeTraces) {
            String probeRecordId = mirrored.probeIdToRecordId().get(trace.probeId());
            if (!StringUtils.hasText(probeRecordId)) {
                continue;
            }
            BrandDetectionResult detectionResult = resolveDetectionResult(trace);
            String dispatchAttemptId = mirrored.recordIdToAttemptId().get(probeRecordId);
            if (detectionResult.mentioned()) {
                probeBrandMentionRecordMapper.insert(ProbeBrandMentionRecord.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .diagnosisTaskId(task.getId())
                        .probeId(probeRecordId)
                        .dispatchAttemptId(dispatchAttemptId)
                        .brandId(task.getBrandId())
                        .brandName(task.getBrandNameSnapshot())
                        .brandRoleCode(0)
                        .mentionLevelCode(mapDetectionLevel(detectionResult.detectionLevel()))
                        .mentionedFlag(true)
                        .recommendedFlag("top1_recommendation".equals(detectionResult.position()) || "comparison".equals(detectionResult.position()))
                        .sentimentCode(mapSentiment(detectionResult.sentiment()))
                        .positionIndex("top1_recommendation".equals(detectionResult.position()) ? 1 : null)
                        .confidenceScore(scale4(detectionResult.strength()))
                        .evidenceSpan(detectionResult.detectionLevel())
                        .mentionMetadataJson(mapOfNonNull(
                                "position", detectionResult.position(),
                                "ragSources", detectionResult.ragSources(),
                                "probeType", detectionResult.probeType() == null ? null : detectionResult.probeType().name()
                        ))
                        .build());
            }
            for (CompetitorMention competitor : detectionResult.competitorsMentioned()) {
                probeBrandMentionRecordMapper.insert(ProbeBrandMentionRecord.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .diagnosisTaskId(task.getId())
                        .probeId(probeRecordId)
                        .dispatchAttemptId(dispatchAttemptId)
                        .brandName(competitor.competitorName())
                        .brandRoleCode(1)
                        .mentionLevelCode(mapDetectionLevel(competitor.detectionLevel()))
                        .mentionedFlag(true)
                        .recommendedFlag(false)
                        .sentimentCode(mapSentiment(competitor.sentiment()))
                        .confidenceScore(scale4(competitor.strength()))
                        .evidenceSpan(competitor.detectionLevel())
                        .mentionMetadataJson(mapOfNonNull())
                        .build());
            }
        }
    }

    private void mirrorModelInvocations(String diagnosisId,
                                        com.geoprobe.dto.engine.v42.Node3UsageCostSummary usageCostSummary,
                                        String legacyDiagnosisId) {
        modelInvocationRecordMapper.delete(new LambdaQueryWrapper<ModelInvocationRecord>()
                .eq(ModelInvocationRecord::getDiagnosisTaskId, diagnosisId)
                .isNull(ModelInvocationRecord::getProbeId));
        List<LlmCallLog> legacyLogs = llmCallLogMapper.selectList(new LambdaQueryWrapper<LlmCallLog>()
                .eq(LlmCallLog::getDiagnosisId, legacyDiagnosisId));
        for (LlmCallLog legacyLog : legacyLogs) {
            if (Integer.valueOf(MODULE_SAMPLING).equals(legacyLog.getModule())) {
                continue;
            }
            modelInvocationRecordMapper.insert(ModelInvocationRecord.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .diagnosisTaskId(diagnosisId)
                    .nodeCode(mapLegacyModule(legacyLog.getModule()))
                    .provider(legacyLog.getProvider())
                    .modelId(legacyLog.getModel())
                    .inputTokens(legacyLog.getInputTokens())
                    .outputTokens(legacyLog.getOutputTokens())
                    .totalTokens(totalTokens(legacyLog.getInputTokens(), legacyLog.getOutputTokens()))
                    .latencyMs(legacyLog.getLatencyMs())
                    .modelCostYuan(zeroIfNull(legacyLog.getCostYuan()))
                    .pluginCostYuan(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .totalCostYuan(zeroIfNull(legacyLog.getCostYuan()))
                    .statusCode(legacyLog.getStatus())
                    .errorMessage(legacyLog.getErrorMessage())
                    .createdAt(legacyLog.getCreatedAt())
                    .build());
        }
        if (usageCostSummary != null && usageCostSummary.totalTokenTotal() > 0) {
            modelInvocationRecordMapper.insert(ModelInvocationRecord.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .diagnosisTaskId(diagnosisId)
                    .nodeCode("NODE3_USAGE_SUMMARY")
                    .provider("aggregate")
                    .modelId("node3-summary")
                    .inputTokens(toInt(usageCostSummary.promptTokenTotal()))
                    .outputTokens(toInt(usageCostSummary.completionTokenTotal()))
                    .totalTokens(toInt(usageCostSummary.totalTokenTotal()))
                    .latencyMs(null)
                    .modelCostYuan(usageCostSummary.modelCostYuan())
                    .pluginCostYuan(usageCostSummary.searchPluginCostYuan())
                    .totalCostYuan(usageCostSummary.totalCostYuan())
                    .statusCode(1)
                    .build());
        }
    }

    private void upsertReport(DiagnosisTask task,
                              Map<String, Object> reportData,
                              V42DiagnosisResult result) {
        DiagnosisReportRecord existing = diagnosisReportRecordMapper.selectOne(new LambdaQueryWrapper<DiagnosisReportRecord>()
                .eq(DiagnosisReportRecord::getDiagnosisTaskId, task.getId())
                .last("limit 1"));
        V42DiagnosisResult.Report report = result.report();
        V42DiagnosisResult.Task taskResult = result.task();
        DiagnosisReportRecord entity = DiagnosisReportRecord.builder()
                .id(existing == null ? java.util.UUID.randomUUID().toString() : existing.getId())
                .diagnosisTaskId(task.getId())
                .organizationId(task.getOrganizationId())
                .brandScopeId(task.getBrandScopeId())
                .brandId(task.getBrandId())
                .categorySnapshot(task.getCategorySnapshot())
                .targetModelProduct(task.getTargetModelProduct())
                .reportStatusCode(REPORT_STATUS_READY)
                .sampleSize(report.sampleSize())
                .spontaneousMentionRate(scale6(report.kpiSummary().spontaneousMentionRate()))
                .confirmationResponseRate(scale6(report.kpiSummary().confirmationResponseRate()))
                .defenseCompleteness(scale6(report.defenseCompleteness().averageCompositeScore()))
                .citationRate(scale6(report.kpiSummary().overallMentionRate()))
                .abvIndex(scale2(report.kpiSummary().stabilityScore()))
                .abvGrade(resolveAbvGrade(report.kpiSummary().stabilityScore()))
                .dimensionPenetrationJson(new LinkedHashMap<>(report.kpiSummary().dimensionPenetration()))
                .brandDriftScore(scale2(1.0d - report.kpiSummary().overallMentionRate()))
                .brandDriftDetailsJson(mapOfNonNull(
                        "overallMentionRate", report.kpiSummary().overallMentionRate(),
                        "positiveRate", report.kpiSummary().positiveRate()
                ))
                .pathologyLabelsJson(List.of())
                .topInsightsJson(objectMapper.convertValue(report.commercialInsights().topThreeInsights(), new TypeReference<List<Map<String, Object>>>() {
                }))
                .summaryJson(objectMapper.convertValue(report.kpiSummary(), new TypeReference<Map<String, Object>>() {
                }))
                .chartsJson(objectMapper.convertValue(report.dashboardCharts(), new TypeReference<Map<String, Object>>() {
                }))
                .reportProjectionJson(reportData == null ? Map.of("task", objectMapper.convertValue(taskResult, new TypeReference<Map<String, Object>>() {
                })) : reportData)
                .generatedAt(report.completedAt() == null ? LocalDateTime.now() : report.completedAt())
                .build();
        if (existing == null) {
            diagnosisReportRecordMapper.insert(entity);
        } else {
            diagnosisReportRecordMapper.updateById(entity);
        }
        syncKnowledgeAssetRunOnCompletion(task.getId());
    }

    private void syncKnowledgeAssetRunOnCompletion(String diagnosisTaskId) {
        if (!StringUtils.hasText(diagnosisTaskId)) {
            return;
        }
        try {
            KnowledgeAssetIngestionService knowledgeAssetIngestionService = knowledgeAssetIngestionServiceProvider.getIfAvailable();
            if (knowledgeAssetIngestionService == null) {
                return;
            }
            KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult result =
                    knowledgeAssetIngestionService.ingestRunOnCompletion(diagnosisTaskId);
            if (result.synced()) {
                log.info("Knowledge asset on-completion sync succeeded. taskId={}, reportId={}, scopeId={}",
                        result.sourceTaskId(),
                        result.sourceReportId(),
                        result.brandScopeId());
                return;
            }
            if (result.skipped()) {
                log.debug("Knowledge asset on-completion sync skipped. taskId={}, reason={}",
                        diagnosisTaskId,
                        result.reason());
            }
        } catch (Exception exception) {
            log.warn("Knowledge asset on-completion sync failed but main run remains committed. taskId={}",
                    diagnosisTaskId,
                    exception);
        }
    }

    private void enqueueOutbox(String aggregateType,
                               String aggregateId,
                               String eventType,
                               Map<String, Object> payload) {
        if (!StringUtils.hasText(aggregateType) || !StringUtils.hasText(aggregateId) || !StringUtils.hasText(eventType)) {
            return;
        }
        String uniqueAggregateId = aggregateType.trim() + ":" + aggregateId.trim() + ":" + eventType.trim();
        IntegrationOutboxRecord existing = integrationOutboxRecordMapper.selectOne(new LambdaQueryWrapper<IntegrationOutboxRecord>()
                .eq(IntegrationOutboxRecord::getAggregateType, aggregateType.trim())
                .eq(IntegrationOutboxRecord::getAggregateId, aggregateId.trim())
                .eq(IntegrationOutboxRecord::getEventType, eventType.trim())
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        integrationOutboxRecordMapper.insert(IntegrationOutboxRecord.builder()
                .id(java.util.UUID.randomUUID().toString())
                .aggregateType(aggregateType.trim())
                .aggregateId(aggregateId.trim())
                .eventType(eventType.trim())
                .payloadJson(payload == null ? mapOfNonNull("aggregateRef", uniqueAggregateId) : payload)
                .statusCode(0)
                .attemptCount(0)
                .build());
    }

    private void upsertNodeRun(String diagnosisId,
                               String nodeCode,
                               int statusCode,
                               long durationMillis,
                               Map<String, Object> summary,
                               String errorCode,
                               String errorMessage) {
        DiagnosisNodeRun existing = diagnosisNodeRunMapper.selectOne(new LambdaQueryWrapper<DiagnosisNodeRun>()
                .eq(DiagnosisNodeRun::getDiagnosisTaskId, diagnosisId)
                .eq(DiagnosisNodeRun::getNodeCode, nodeCode)
                .eq(DiagnosisNodeRun::getAttemptNo, 1)
                .last("limit 1"));
        LocalDateTime completedAt = LocalDateTime.now();
        DiagnosisNodeRun entity = DiagnosisNodeRun.builder()
                .id(existing == null ? java.util.UUID.randomUUID().toString() : existing.getId())
                .diagnosisTaskId(diagnosisId)
                .nodeCode(nodeCode)
                .graphVersion("4.2-serial")
                .attemptNo(1)
                .statusCode(statusCode)
                .outputSummaryJson(summary == null ? Map.of() : summary)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .startedAt(completedAt.minusNanos(Math.max(0L, durationMillis) * 1_000_000L))
                .completedAt(completedAt)
                .durationMs((int) Math.max(0L, durationMillis))
                .build();
        if (existing == null) {
            diagnosisNodeRunMapper.insert(entity);
        } else {
            diagnosisNodeRunMapper.updateById(entity);
        }
    }

    private void upsertProgressSnapshot(String diagnosisId,
                                        String organizationId,
                                        String brandScopeId,
                                        String currentStepCode,
                                        String currentStepLabel,
                                        int stepsCompleted,
                                        int stepsTotal,
                                        int samplingCompleted,
                                        int samplingExpectedTotal,
                                        int samplingGeneratedTotal,
                                        int samplingMissingTotal,
                                        boolean samplingVariantShortfall,
                                        double percent,
                                        Long lastEventSeq,
                                        int searchRequestedCount,
                                        int searchSatisfiedCount,
                                        int failedProbeCount,
                                        BigDecimal totalCostYuan) {
        DiagnosisProgressSnapshot entity = DiagnosisProgressSnapshot.builder()
                .diagnosisTaskId(diagnosisId)
                .organizationId(organizationId)
                .brandScopeId(brandScopeId)
                .currentStepCode(currentStepCode)
                .currentStepLabel(currentStepLabel)
                .stepsCompleted(stepsCompleted)
                .stepsTotal(stepsTotal)
                .samplingCompleted(samplingCompleted)
                .samplingExpectedTotal(samplingExpectedTotal)
                .samplingGeneratedTotal(samplingGeneratedTotal)
                .samplingMissingTotal(samplingMissingTotal)
                .samplingVariantShortfall(samplingVariantShortfall)
                .percent(scale2(percent))
                .lastEventSeq(lastEventSeq)
                .searchRequestedCount(searchRequestedCount)
                .searchSatisfiedCount(searchSatisfiedCount)
                .failedProbeCount(failedProbeCount)
                .totalCostYuan(zeroIfNull(totalCostYuan))
                .build();
        DiagnosisProgressSnapshot existing = diagnosisProgressSnapshotMapper.selectById(diagnosisId);
        if (existing == null) {
            diagnosisProgressSnapshotMapper.insert(entity);
        } else {
            diagnosisProgressSnapshotMapper.updateById(entity);
        }
    }

    private String resolveTerminalStepCode(Integer statusCode) {
        if (statusCode == null) {
            return "TERMINAL";
        }
        return switch (statusCode) {
            case 3 -> "FAILED";
            case 4 -> "CANCELLED";
            case 6 -> "EXPIRED";
            default -> "TERMINAL";
        };
    }

    private String resolveTerminalStepLabel(Integer statusCode) {
        if (statusCode == null) {
            return "运行单已结束";
        }
        return switch (statusCode) {
            case 3 -> "诊断执行失败";
            case 4 -> "诊断已取消";
            case 6 -> "诊断已过期";
            default -> "运行单已结束";
        };
    }

    private String extractHost(String website) {
        java.net.URI uri = toUri(website);
        return uri == null ? null : uri.getHost();
    }

    private java.net.URI toUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim();
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://" + normalized;
            }
            return java.net.URI.create(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private BrandDetectionResult resolveDetectionResult(V42DiagnosisResult.ProbeTrace trace) {
        if (trace.brandDetectionResult() != null) {
            return trace.brandDetectionResult();
        }
        return defaultDetectionResult(trace);
    }

    private BrandDetectionResult defaultDetectionResult(V42DiagnosisResult.ProbeTrace trace) {
        String responseLengthBucket = defaultIfBlank(trace.responseLengthBucket(), "Short");
        int responseTokenCount = Math.max(trace.responseTokenCount(), 0);
        if (trace.mentioned()) {
            return BrandDetectionResult.detected(
                    defaultIfBlank(trace.detectionLevel(), "L1_EXACT"),
                    "comparison",
                    "neutral",
                    1.0d,
                    trace.probeType(),
                    responseTokenCount,
                    responseLengthBucket,
                    List.of(),
                    List.of(),
                    ""
            );
        }
        return BrandDetectionResult.noMention(
                trace.probeType(),
                responseTokenCount,
                responseLengthBucket,
                List.of(),
                List.of(),
                ""
        );
    }

    private String resolveModelIdFromTrace(V42DiagnosisResult.ProbeTrace trace) {
        if (StringUtils.hasText(trace.modelId())) {
            return trace.modelId();
        }
        if (trace.searchRequested() && trace.searchEvidence() != null && !trace.searchEvidence().isEmpty()) {
            SearchEvidence first = trace.searchEvidence().get(0);
            if (StringUtils.hasText(first.provider())) {
                return first.provider();
            }
        }
        return null;
    }

    private String resolveModelIdForAttempt(DiagnosisTask task,
                                            V42DiagnosisResult.Task taskResult,
                                            V42DiagnosisResult.ProbeTrace trace) {
        String explicit = nullIfBlank(resolveModelIdFromTrace(trace));
        if (explicit != null) {
            return explicit;
        }
        String taskProduct = task == null ? null : nullIfBlank(task.getTargetModelProduct());
        if (taskProduct != null) {
            return taskProduct;
        }
        String provider = taskResult == null ? null : nullIfBlank(taskResult.modelProvider());
        if (provider != null) {
            return provider;
        }
        return "unknown";
    }

    private String resolveProviderForAttempt(DiagnosisTask task,
                                             V42DiagnosisResult.Task taskResult,
                                             V42DiagnosisResult.ProbeTrace trace) {
        String inferred = nullIfBlank(Node3TargetEnginePolicy.inferProviderFromModelId(trace.modelId()));
        if (inferred != null) {
            return inferred;
        }
        String taskProvider = normalizeProviderCode(taskResult == null ? null : taskResult.modelProvider());
        if (taskProvider != null) {
            return taskProvider;
        }
        String taskProductProvider = Node3TargetEnginePolicy.inferProviderFromModelId(
                task == null ? null : task.getTargetModelProduct()
        );
        if (StringUtils.hasText(taskProductProvider)) {
            return taskProductProvider;
        }
        if (trace.searchRequested() && trace.searchEvidence() != null && !trace.searchEvidence().isEmpty()) {
            SearchEvidence first = trace.searchEvidence().getFirst();
            if (StringUtils.hasText(first.provider())) {
                return first.provider().trim();
            }
        }
        return "unknown";
    }

    private String normalizeProviderCode(String value) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            return null;
        }
        int searchSuffixIndex = normalized.indexOf("+web_search");
        if (searchSuffixIndex > 0) {
            return normalized.substring(0, searchSuffixIndex);
        }
        return normalized;
    }

    private int mapDispatchStatus(ProbeDispatchStatus status) {
        if (status == ProbeDispatchStatus.SUCCESS) {
            return 1;
        }
        if (status == ProbeDispatchStatus.FAILED) {
            return 2;
        }
        return 4;
    }

    private int mapRenderSourceCode(String renderSource) {
        if (!StringUtils.hasText(renderSource)) {
            return 0;
        }
        return switch (renderSource.trim().toUpperCase(Locale.ROOT)) {
            case "LLM" -> 1;
            case "TEMPLATE" -> 2;
            case "DETERMINISTIC_FALLBACK" -> 3;
            default -> 0;
        };
    }

    private String normalizeFailureReasonCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_FAILURE_REASON_CODE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FAILURE_REASON_CODE_LENGTH);
    }

    private int mapDetectionLevel(String detectionLevel) {
        if (!StringUtils.hasText(detectionLevel)) {
            return 0;
        }
        if (detectionLevel.startsWith("L1")) {
            return 1;
        }
        if (detectionLevel.startsWith("L2")) {
            return 2;
        }
        if (detectionLevel.startsWith("L3")) {
            return 3;
        }
        return 0;
    }

    private Integer mapSentiment(String sentiment) {
        if (!StringUtils.hasText(sentiment)) {
            return null;
        }
        return switch (sentiment.trim().toLowerCase()) {
            case "positive" -> 1;
            case "negative" -> 2;
            default -> 0;
        };
    }

    private String mapLegacyModule(Integer module) {
        if (module == null) {
            return "UNKNOWN";
        }
        return switch (module) {
            case 1 -> "NODE2_RENDERING";
            case 2 -> "NODE4_PARSING";
            case 3 -> "NODE3_SAMPLING";
            case 4 -> "ALIAS_EXPANSION";
            default -> "UNKNOWN";
        };
    }

    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(text.trim()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    private FallbackStats loadFallbackStats(String diagnosisTaskId) {
        if (!StringUtils.hasText(diagnosisTaskId)) {
            return FallbackStats.EMPTY;
        }
        List<DiagnosisProbeRecord> probes = diagnosisProbeRecordMapper.selectList(
                new QueryWrapper<DiagnosisProbeRecord>()
                        .eq("diagnosis_task_id", diagnosisTaskId.trim())
                        .select("fallback_used", "render_metadata_json")
        );
        if (probes == null || probes.isEmpty()) {
            return FallbackStats.EMPTY;
        }
        long fallbackCount = probes.stream()
                .filter(this::inferFallbackUsed)
                .count();
        return new FallbackStats(
                fallbackCount > 0L,
                probes.isEmpty() ? 0.0d : (double) fallbackCount / probes.size()
        );
    }

    private boolean inferFallbackUsed(V42DiagnosisResult.ProbeTrace trace) {
        return trace != null && inferFallbackUsed(trace.renderFamily());
    }

    private boolean inferFallbackUsed(DiagnosisProbeRecord probe) {
        if (probe == null) {
            return false;
        }
        if (Boolean.TRUE.equals(probe.getFallbackUsed())) {
            return true;
        }
        Map<String, Object> renderMetadata = probe.getRenderMetadataJson();
        Object renderFamily = renderMetadata == null ? null : renderMetadata.get("renderFamily");
        return renderFamily instanceof String family && inferFallbackUsed(family);
    }

    private boolean inferFallbackUsed(String renderFamily) {
        return StringUtils.hasText(renderFamily)
                && renderFamily.trim().toLowerCase(Locale.ROOT).contains("fallback");
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String objectText(Object value) {
        return value == null ? null : nullIfBlank(String.valueOf(value));
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String text && StringUtils.hasText(text)) {
                normalized.add(text.trim());
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private Map<String, List<String>> readStringListMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
        rawMap.forEach((rawKey, rawValue) -> {
            if (!(rawKey instanceof String key) || !StringUtils.hasText(key)) {
                return;
            }
            List<String> values = readStringList(rawValue);
            if (!values.isEmpty()) {
                normalized.put(key.trim(), values);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private Map<String, Object> readObjectMap(Object value) {
        if (!(value instanceof Map<?, ?>) || ((Map<?, ?>) value).isEmpty()) {
            return Map.of();
        }
        Map<String, Object> converted = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
        return converted == null || converted.isEmpty() ? Map.of() : converted;
    }

    private Map<String, Object> mutableObjectMap(Object value) {
        if (!(value instanceof Map<?, ?>) || ((Map<?, ?>) value).isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
        return converted == null ? new LinkedHashMap<>() : new LinkedHashMap<>(converted);
    }

    private long longOrZero(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private long longOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int toInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private Integer totalTokens(Integer inputTokens, Integer outputTokens) {
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateModelCostYuan(String provider, String modelId, Integer inputTokens, Integer outputTokens) {
        return LlmCostCalculator.estimateCostYuanWithFallback(
                provider,
                modelId,
                inputTokens,
                outputTokens,
                parseCost(inputCostPer1k, DEFAULT_INPUT_COST_PER_1K),
                parseCost(outputCostPer1k, DEFAULT_OUTPUT_COST_PER_1K),
                parseCost(shortOutputCostPer1k, DEFAULT_SHORT_OUTPUT_COST_PER_1K),
                shortOutputThreshold
        );
    }

    private BigDecimal estimateSearchPluginCostYuan(Integer searchCallCount) {
        int safeSearchCalls = defaultInt(searchCallCount);
        if (safeSearchCalls <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal rate = parseCost(webSearchCostPerCall, DEFAULT_WEB_SEARCH_COST_PER_CALL);
        return rate.multiply(BigDecimal.valueOf(safeSearchCalls)).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal parseCost(String value, BigDecimal fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.compareTo(BigDecimal.ZERO) < 0 ? fallback : parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal scale2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private boolean isNode3Invocation(String nodeCode) {
        return StringUtils.hasText(nodeCode)
                && nodeCode.trim().startsWith("NODE3")
                && !isNode3UsageSummaryInvocation(nodeCode);
    }

    private boolean isNode3UsageSummaryInvocation(String nodeCode) {
        return StringUtils.hasText(nodeCode) && "NODE3_USAGE_SUMMARY".equals(nodeCode.trim());
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer computeP95Latency(List<ModelInvocationRecord> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return null;
        }
        List<Integer> latencies = invocations.stream()
                .map(ModelInvocationRecord::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (latencies.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(latencies.size() * 0.95d) - 1;
        return latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
    }

    private String resolveAbvGrade(double score) {
        if (score >= 85.0d) {
            return "A";
        }
        if (score >= 70.0d) {
            return "B";
        }
        if (score >= 55.0d) {
            return "C";
        }
        return "D";
    }

    private record MirroredProbeArtifacts(Map<String, String> probeIdToRecordId,
                                          Map<String, String> recordIdToAttemptId) {
    }

    private record FallbackStats(boolean used, double rate) {
        private static final FallbackStats EMPTY = new FallbackStats(false, 0.0d);
    }
}
