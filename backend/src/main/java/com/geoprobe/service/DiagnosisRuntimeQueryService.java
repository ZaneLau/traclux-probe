package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.IntentClusterResult;
import com.geoprobe.dto.engine.PromptMatrixResult;
import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.entity.Run;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.RunMapper;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DiagnosisRuntimeQueryService {

    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_QUEUED = 5;
    private static final int STATUS_EXPIRED = 6;
    private static final int RUN_STATUS_SUCCESS = 1;
    private static final int RUN_STATUS_FAILED = 2;
    private static final int RUN_STATUS_TIMEOUT = 3;
    private static final int TOTAL_STEPS = 11;
    private static final int CREDIT_TYPE_REFUND = 3;
    private static final int MODULE_SAMPLING = 3;
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);

    private final RunMapper runMapper;
    private final LlmCallLogMapper llmCallLogMapper;
    private final CreditTransactionMapper creditTransactionMapper;
    private final DiagnosisProbeEventService diagnosisProbeEventService;
    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy;
    private final V42ReportProjectionService v42ReportProjectionService;
    private final ObjectMapper objectMapper;
    private final RedisSupportService redisSupportService;

    public DiagnosisProgressResponse buildProgressResponse(Diagnosis diagnosis) {
        DiagnosisProgressResponse.Progress progress = buildProgress(diagnosis);
        return DiagnosisProgressResponse.builder()
                .id(diagnosis.getId())
                .brandName(diagnosis.getBrandName())
                .status(diagnosis.getStatus())
                .startedAt(diagnosis.getStartedAt())
                .progress(progress)
                .build();
    }

    public DiagnosisService.ProgressStreamSnapshot buildProgressStreamSnapshot(Diagnosis diagnosis) {
        DiagnosisProgressResponse.Progress progress = buildProgress(diagnosis);
        StageDefinition stage = mapNarrativeStage(progress.getCurrentStep(), diagnosis.getStatus());
        Integer etaSeconds = estimateEtaSeconds(diagnosis.getStartedAt(), progress.getPercent());
        return new DiagnosisService.ProgressStreamSnapshot(
                diagnosis.getId(),
                diagnosis.getStatus(),
                diagnosis.getErrorMessage(),
                progress.getCurrentStep(),
                progress.getCurrentStepLabel(),
                stage.stage(),
                stage.index(),
                5,
                stage.label(),
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

    public DiagnosisService.ProgressStreamFrame buildProgressStreamFrame(Diagnosis diagnosis,
                                                                        Long afterSeq,
                                                                        Integer limit) {
        return new DiagnosisService.ProgressStreamFrame(
                buildProgressStreamSnapshot(diagnosis),
                diagnosisProbeEventService.readPage(diagnosis.getId(), afterSeq, limit)
        );
    }

    public DiagnosisService.ProbeEventPage readProbeEvents(String diagnosisId, Long afterSeq, Integer limit) {
        return diagnosisProbeEventService.readPage(diagnosisId, afterSeq, limit);
    }

    public DiagnosisOpsMetricsResponse buildOpsMetrics(String userId, String diagnosisId, Diagnosis diagnosis) {
        if (v42ReportProjectionService.supports(diagnosis)) {
            return buildV42OpsMetrics(userId, diagnosisId, diagnosis);
        }

        List<Run> runs = runMapper.selectList(
                new LambdaQueryWrapper<Run>()
                        .eq(Run::getDiagnosisId, diagnosisId)
        );
        int totalSamples = runs.size();
        int successSamples = (int) runs.stream().filter(run -> intEquals(run.getStatus(), RUN_STATUS_SUCCESS)).count();
        int failedSamples = (int) runs.stream().filter(run -> intEquals(run.getStatus(), RUN_STATUS_FAILED)).count();
        int timeoutSamples = (int) runs.stream().filter(run -> intEquals(run.getStatus(), RUN_STATUS_TIMEOUT)).count();

        List<LlmCallLog> samplingLogs = llmCallLogMapper.selectList(
                new LambdaQueryWrapper<LlmCallLog>()
                        .eq(LlmCallLog::getDiagnosisId, diagnosisId)
                        .eq(LlmCallLog::getModule, MODULE_SAMPLING)
                        .isNotNull(LlmCallLog::getLatencyMs)
        );

        int totalApiCalls = samplingLogs.size();
        Integer p95LatencyMs = computeP95Latency(samplingLogs);
        BigDecimal totalCost = samplingLogs.stream()
                .map(LlmCallLog::getCostYuan)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FallbackState fallbackState = resolveFallbackState(diagnosis);
        boolean refunded = isDiagnosisRefunded(userId, diagnosisId);

        return DiagnosisOpsMetricsResponse.builder()
                .diagnosisId(diagnosisId)
                .completedRate(intEquals(diagnosis.getStatus(), STATUS_COMPLETED) ? 1.0d : 0.0d)
                .timeoutRate(rate(timeoutSamples, totalSamples))
                .fallbackRate(fallbackState.fallbackRate())
                .refundRate(refunded ? 1.0d : 0.0d)
                .p95LatencyMs(p95LatencyMs)
                .totalLlmCostYuan(totalCost)
                .totalApiCalls(totalApiCalls)
                .totalSamples(totalSamples)
                .successSamples(successSamples)
                .failedSamples(failedSamples)
                .timeoutSamples(timeoutSamples)
                .fallbackUsed(fallbackState.fallbackUsed())
                .refunded(refunded)
                .build();
    }

    public void writeTerminalProgress(String diagnosisId, String currentStep) {
        if (!StringUtils.hasText(diagnosisId) || !StringUtils.hasText(currentStep)) {
            return;
        }
        Map<String, Object> existingProgress = readProgressFromRedis(diagnosisId);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("currentStep", currentStep.trim());
        progress.put("currentStepLabel", asString(existingProgress == null ? null : existingProgress.get("currentStepLabel"), null));
        progress.put("stepsCompleted", asInt(existingProgress == null ? null : existingProgress.get("stepsCompleted"), 0));
        progress.put("stepsTotal", TOTAL_STEPS);
        progress.put("samplingCompleted", asInt(existingProgress == null ? null : existingProgress.get("samplingCompleted"), 0));
        progress.put("samplingTotal", asInt(existingProgress == null ? null : existingProgress.get("samplingTotal"), 0));
        progress.put("samplingExpectedTotal", asInt(existingProgress == null ? null : existingProgress.get("samplingExpectedTotal"), 0));
        progress.put("samplingGeneratedTotal", asInt(existingProgress == null ? null : existingProgress.get("samplingGeneratedTotal"), 0));
        progress.put("samplingMissingTotal", asInt(existingProgress == null ? null : existingProgress.get("samplingMissingTotal"), 0));
        progress.put("samplingVariantShortfall", asBoolean(existingProgress == null ? null : existingProgress.get("samplingVariantShortfall"), false));
        progress.put("percent", asDouble(existingProgress == null ? null : existingProgress.get("percent"), 0.0d));

        redisSupportService.setJson(progressKey(diagnosisId), progress, PROGRESS_TTL, "diagnosis terminal progress");
    }

    private DiagnosisProgressResponse.Progress buildProgress(Diagnosis diagnosis) {
        SamplingPlanSnapshot samplingPlan = resolveSamplingPlan(diagnosis);
        Map<String, Object> redisProgress = readProgressFromRedis(diagnosis.getId());
        if (redisProgress == null || redisProgress.isEmpty()) {
            if (v42ReportProjectionService.supports(diagnosis)) {
                return buildTruthfulV42Progress(diagnosis, samplingPlan);
            }
            return defaultProgress(diagnosis == null ? null : diagnosis.getStatus());
        }

        int redisSamplingCompleted = asInt(redisProgress.get("samplingCompleted"), 0);
        int redisSamplingTotal = asInt(redisProgress.get("samplingTotal"), 0);
        int expectedTotal = samplingPlan.expectedSamples();
        int generatedTotal = asInt(
                redisProgress.get("samplingGeneratedTotal"),
                samplingPlan.generatedSamples()
        );
        if (generatedTotal <= 0 && redisSamplingTotal > 0) {
            generatedTotal = redisSamplingTotal;
        }
        int missingTotal = Math.max(0, expectedTotal - generatedTotal);
        boolean samplingVariantShortfall = missingTotal > 0
                || samplingPlan.samplingVariantShortfall()
                || asBoolean(redisProgress.get("samplingVariantShortfall"), false);
        int displayTotal = expectedTotal > 0 ? expectedTotal : samplingPlan.displayTotal();

        return DiagnosisProgressResponse.Progress.builder()
                .currentStep(asString(redisProgress.get("currentStep"), defaultStepName(diagnosis.getStatus())))
                .currentStepLabel(asString(
                        redisProgress.get("currentStepLabel"),
                        mapNarrativeStage(
                                asString(redisProgress.get("currentStep"), defaultStepName(diagnosis.getStatus())),
                                diagnosis.getStatus()
                        ).label()
                ))
                .stepsCompleted(asInt(redisProgress.get("stepsCompleted"), 0))
                .stepsTotal(asInt(redisProgress.get("stepsTotal"), TOTAL_STEPS))
                .samplingCompleted(redisSamplingCompleted)
                .samplingTotal(displayTotal)
                .samplingExpectedTotal(expectedTotal)
                .samplingGeneratedTotal(generatedTotal)
                .samplingMissingTotal(missingTotal)
                .samplingVariantShortfall(samplingVariantShortfall)
                .percent(asDouble(redisProgress.get("percent"), 0.0d))
                .build();
    }

    private DiagnosisProgressResponse.Progress buildTruthfulV42Progress(Diagnosis diagnosis, SamplingPlanSnapshot samplingPlan) {
        var task = v42ReportProjectionService.readTask(diagnosis);
        int sampleSize = task == null ? samplingPlan.generatedSamples() : task.sampleSize();
        int successful = task == null ? 0 : (int) task.successfulProbes();
        int expectedSamples = samplingPlan.expectedSamples() > 0 ? samplingPlan.expectedSamples() : sampleSize;
        int generatedSamples = samplingPlan.generatedSamples() > 0 ? samplingPlan.generatedSamples() : sampleSize;
        int missingSamples = Math.max(0, expectedSamples - generatedSamples);
        if (diagnosis != null && diagnosis.getStatus() != null && diagnosis.getStatus() == STATUS_COMPLETED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("COMPLETED")
                    .currentStepLabel("诊断完成，报告已可查看")
                    .stepsCompleted(TOTAL_STEPS)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(generatedSamples)
                    .samplingTotal(expectedSamples)
                    .samplingExpectedTotal(expectedSamples)
                    .samplingGeneratedTotal(generatedSamples)
                    .samplingMissingTotal(missingSamples)
                    .samplingVariantShortfall(samplingPlan.samplingVariantShortfall() || missingSamples > 0)
                    .percent(100.0d)
                    .build();
        }
        if (diagnosis != null && diagnosis.getStatus() != null && diagnosis.getStatus() == STATUS_RUNNING) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("SAMPLING")
                    .currentStepLabel("运行中，等待实时进度信号；当前仅展示可证实采样结果")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(successful)
                    .samplingTotal(expectedSamples)
                    .samplingExpectedTotal(expectedSamples)
                    .samplingGeneratedTotal(generatedSamples > 0 ? generatedSamples : successful)
                    .samplingMissingTotal(missingSamples)
                    .samplingVariantShortfall(samplingPlan.samplingVariantShortfall() || missingSamples > 0)
                    .percent(computeTruthfulSamplingPercent(generatedSamples > 0 ? generatedSamples : successful, expectedSamples, false))
                    .build();
        }
        return defaultProgress(diagnosis == null ? null : diagnosis.getStatus());
    }

    private double computeTruthfulSamplingPercent(int completedSamples, int expectedSamples, boolean completed) {
        if (completed) {
            return 100.0d;
        }
        if (expectedSamples <= 0) {
            return 0.0d;
        }
        double ratio = Math.max(0.0d, Math.min(1.0d, (double) Math.max(0, completedSamples) / expectedSamples));
        double percent = ratio * 100.0d;
        if (percent >= 100.0d) {
            return 99.0d;
        }
        return round4(percent);
    }

    private DiagnosisOpsMetricsResponse buildV42OpsMetrics(String userId, String diagnosisId, Diagnosis diagnosis) {
        var task = v42ReportProjectionService.readTask(diagnosis);
        if (task == null) {
            boolean refunded = isDiagnosisRefunded(userId, diagnosisId);
            return DiagnosisOpsMetricsResponse.builder()
                    .diagnosisId(diagnosisId)
                    .completedRate(intEquals(diagnosis.getStatus(), STATUS_COMPLETED) ? 1.0d : 0.0d)
                    .timeoutRate(0.0d)
                    .fallbackRate(0.0d)
                    .refundRate(refunded ? 1.0d : 0.0d)
                    .totalApiCalls(diagnosis.getTotalApiCalls())
                    .fallbackUsed(false)
                    .refunded(refunded)
                    .build();
        }
        FallbackState fallbackState = resolveV42FallbackState(task);
        List<LlmCallLog> nonSamplingLogs = llmCallLogMapper.selectList(
                new LambdaQueryWrapper<LlmCallLog>()
                        .eq(LlmCallLog::getDiagnosisId, diagnosisId)
                        .and(wrapper -> wrapper
                                .ne(LlmCallLog::getModule, MODULE_SAMPLING)
                                .or()
                                .isNull(LlmCallLog::getModule))
        );
        BigDecimal nonSamplingCost = sumCost(nonSamplingLogs);
        long nonSamplingPromptTokenTotal = sumToken(nonSamplingLogs, LlmCallLog::getInputTokens);
        long nonSamplingCompletionTokenTotal = sumToken(nonSamplingLogs, LlmCallLog::getOutputTokens);
        long node3PromptTokenTotal = task.usageCostSummary().promptTokenTotal();
        long node3CompletionTokenTotal = task.usageCostSummary().completionTokenTotal();
        long node3TokenTotal = task.usageCostSummary().totalTokenTotal();
        BigDecimal node3Cost = task.usageCostSummary().totalCostYuan();
        long fullChainPromptTokenTotal = nonSamplingPromptTokenTotal + node3PromptTokenTotal;
        long fullChainCompletionTokenTotal = nonSamplingCompletionTokenTotal + node3CompletionTokenTotal;
        long fullChainTokenTotal = fullChainPromptTokenTotal + fullChainCompletionTokenTotal;
        BigDecimal fullChainCost = nonSamplingCost.add(node3Cost);
        boolean refunded = isDiagnosisRefunded(userId, diagnosisId);
        return DiagnosisOpsMetricsResponse.builder()
                .diagnosisId(diagnosisId)
                .completedRate(intEquals(diagnosis.getStatus(), STATUS_COMPLETED) ? 1.0d : 0.0d)
                .timeoutRate(0.0d)
                .fallbackRate(fallbackState.fallbackRate())
                .refundRate(refunded ? 1.0d : 0.0d)
                .p95LatencyMs(null)
                .totalLlmCostYuan(fullChainCost)
                .node3LlmCostYuan(node3Cost)
                .nonSamplingLlmCostYuan(nonSamplingCost)
                .fullChainPromptTokenTotal(fullChainPromptTokenTotal)
                .fullChainCompletionTokenTotal(fullChainCompletionTokenTotal)
                .fullChainTokenTotal(fullChainTokenTotal)
                .node3PromptTokenTotal(node3PromptTokenTotal)
                .node3CompletionTokenTotal(node3CompletionTokenTotal)
                .node3TokenTotal(node3TokenTotal)
                .totalApiCalls(diagnosis.getTotalApiCalls())
                .totalSamples(task.sampleSize())
                .successSamples((int) task.successfulProbes())
                .failedSamples((int) task.failedProbes())
                .timeoutSamples(0)
                .fallbackUsed(fallbackState.fallbackUsed())
                .refunded(refunded)
                .build();
    }

    private SamplingPlanSnapshot resolveSamplingPlan(Diagnosis diagnosis) {
        if (diagnosis == null) {
            return new SamplingPlanSnapshot(0, 0, 0, 0, false);
        }
        Map<String, Object> meta = readReportMeta(diagnosis);
        int expectedSamples = v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(meta, "diagnosis_progress");
        int generatedSamples = asInt(meta.get("generatedSamples"), 0);
        if (generatedSamples <= 0 && v42ReportProjectionService.supports(diagnosis)) {
            var task = v42ReportProjectionService.readTask(diagnosis);
            if (task != null) {
                generatedSamples = task.sampleSize();
            }
        }
        int missingSamples = asInt(meta.get("missingSamples"), Math.max(0, expectedSamples - generatedSamples));
        boolean samplingVariantShortfall = asBoolean(meta.get("samplingVariantShortfall"), missingSamples > 0);
        int displayTotal = expectedSamples > 0 ? expectedSamples : generatedSamples;
        return new SamplingPlanSnapshot(expectedSamples, generatedSamples, missingSamples, displayTotal, samplingVariantShortfall);
    }

    private Map<String, Object> readReportMeta(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return Map.of();
        }
        try {
            Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
            });
            if (reportData == null) {
                return Map.of();
            }
            return reportData.get("meta") == null
                    ? Map.of()
                    : objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private DiagnosisProgressResponse.Progress defaultProgress(Integer status) {
        if (status != null && status == STATUS_COMPLETED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("COMPLETED")
                    .stepsCompleted(TOTAL_STEPS)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(100.0d)
                    .build();
        }
        if (status != null && status == STATUS_FAILED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("FAILED")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(0.0d)
                    .build();
        }
        if (status != null && status == STATUS_CANCELLED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("CANCELLED")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(0.0d)
                    .build();
        }
        if (status != null && status == STATUS_QUEUED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("QUEUED")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(0.0d)
                    .build();
        }
        if (status != null && status == STATUS_EXPIRED) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("EXPIRED")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(0.0d)
                    .build();
        }
        if (status != null && status == STATUS_RUNNING) {
            return DiagnosisProgressResponse.Progress.builder()
                    .currentStep("RUNNING")
                    .stepsCompleted(0)
                    .stepsTotal(TOTAL_STEPS)
                    .samplingCompleted(0)
                    .samplingTotal(0)
                    .percent(0.0d)
                    .build();
        }
        return DiagnosisProgressResponse.Progress.builder()
                .currentStep("PENDING")
                .stepsCompleted(0)
                .stepsTotal(TOTAL_STEPS)
                .samplingCompleted(0)
                .samplingTotal(0)
                .percent(0.0d)
                .build();
    }

    private Map<String, Object> readProgressFromRedis(String diagnosisId) {
        return redisSupportService.getJson(
                progressKey(diagnosisId),
                new TypeReference<Map<String, Object>>() {
                },
                "diagnosis progress snapshot"
        );
    }

    private String defaultStepName(Integer status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status) {
            case STATUS_RUNNING -> "RUNNING";
            case STATUS_COMPLETED -> "COMPLETED";
            case STATUS_FAILED -> "FAILED";
            case STATUS_CANCELLED -> "CANCELLED";
            case STATUS_QUEUED -> "QUEUED";
            case STATUS_EXPIRED -> "EXPIRED";
            default -> "PENDING";
        };
    }

    private StageDefinition mapNarrativeStage(String currentStep, Integer status) {
        String step = StringUtils.hasText(currentStep) ? currentStep.trim().toUpperCase() : defaultStepName(status);
        return switch (step) {
            case "NODE0_STATE_SPACE" -> new StageDefinition("UNDERSTANDING", 1, "正在生成状态空间...");
            case "NODE1_BLUEPRINTS", "NODE2_RENDERING", "QUALITY_GATE" ->
                    new StageDefinition("BUILDING", 2, "正在构建 AI 探测探针...");
            case "NODE3_SAMPLING" -> new StageDefinition("SAMPLING", 3, "正在向 AI 发送探测请求...");
            case "NODE4_PARSING", "NODE5A_KPI", "NODE5B_INSIGHTS", "NODE5C_DASHBOARD" ->
                    new StageDefinition("ANALYZING", 4, "正在深度分析采样结果...");
            case "REPORT_ASSEMBLY", "REPORT_PROJECTION", "COMPLETED" ->
                    new StageDefinition("REPORTING", 5, "正在生成您的诊断报告...");
            case "SAMPLING" -> new StageDefinition("SAMPLING", 3, "正在向 AI 发送探测请求...");
            case "QUEUED" -> new StageDefinition("UNDERSTANDING", 1, "任务排队中，正在等待资源...");
            default -> new StageDefinition("UNDERSTANDING", 1, "正在理解您的品牌定位...");
        };
    }

    private Integer estimateEtaSeconds(LocalDateTime startedAt, Double percent) {
        if (startedAt == null || percent == null || percent <= 1.0d || percent >= 100.0d) {
            return null;
        }
        long elapsedSeconds = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds <= 0L) {
            return null;
        }
        double remainingRatio = (100.0d - percent) / percent;
        long estimated = Math.round(elapsedSeconds * remainingRatio);
        if (estimated <= 0L) {
            return 1;
        }
        return (int) Math.min(estimated, 3600L);
    }

    private String progressKey(String diagnosisId) {
        return "diagnosis:" + diagnosisId + ":progress";
    }

    private FallbackState resolveFallbackState(Diagnosis diagnosis) {
        IntentClusterResult intentClusterResult = convertValue(diagnosis.getIntentClusters(), IntentClusterResult.class);
        PromptMatrixResult promptMatrixResult = convertValue(diagnosis.getPromptMatrix(), PromptMatrixResult.class);

        boolean fallbackUsed = (intentClusterResult != null && intentClusterResult.fallbackUsed())
                || (promptMatrixResult != null && promptMatrixResult.fallbackUsed());
        int totalClusters = intentClusterResult != null && intentClusterResult.clusters() != null
                ? Math.max(1, intentClusterResult.clusters().size())
                : 5;
        int fallbackClusterCount = promptMatrixResult != null && promptMatrixResult.fallbackClusterIds() != null
                ? promptMatrixResult.fallbackClusterIds().size()
                : 0;
        if (fallbackUsed && fallbackClusterCount == 0) {
            return new FallbackState(true, 1.0d);
        }
        return new FallbackState(fallbackUsed, rate(fallbackClusterCount, totalClusters));
    }

    private FallbackState resolveV42FallbackState(com.geoprobe.dto.engine.v42.V42DiagnosisResult.Task task) {
        if (task == null || task.probeTrace() == null || task.probeTrace().isEmpty()) {
            return new FallbackState(false, 0.0d);
        }
        int total = task.probeTrace().size();
        int fallbackCount = Math.toIntExact(task.probeTrace().stream()
                .filter(Objects::nonNull)
                .filter(this::isV42ProbeTraceFallbackUsed)
                .count());
        return new FallbackState(fallbackCount > 0, rate(fallbackCount, total));
    }

    private boolean isV42ProbeTraceFallbackUsed(com.geoprobe.dto.engine.v42.V42DiagnosisResult.ProbeTrace trace) {
        if (trace == null) {
            return false;
        }
        Map<String, Object> triggeredRules = trace.triggeredRulesJson();
        if (triggeredRules != null && triggeredRules.containsKey("renderFallbackUsed")) {
            return asBoolean(triggeredRules.get("renderFallbackUsed"), false);
        }
        return StringUtils.hasText(trace.renderFamily())
                && trace.renderFamily().trim().toLowerCase(Locale.ROOT).contains("fallback");
    }

    private boolean isDiagnosisRefunded(String userId, String diagnosisId) {
        Long count = creditTransactionMapper.selectCount(
                new LambdaQueryWrapper<CreditTransaction>()
                        .eq(CreditTransaction::getUserId, userId)
                        .eq(CreditTransaction::getDiagnosisId, diagnosisId)
                        .eq(CreditTransaction::getType, CREDIT_TYPE_REFUND)
                        .last("limit 1")
        );
        return count != null && count > 0L;
    }

    private Integer computeP95Latency(List<LlmCallLog> samplingLogs) {
        if (samplingLogs == null || samplingLogs.isEmpty()) {
            return null;
        }
        List<Integer> latencies = new ArrayList<>(samplingLogs.stream()
                .map(LlmCallLog::getLatencyMs)
                .filter(Objects::nonNull)
                .toList());
        if (latencies.isEmpty()) {
            return null;
        }
        latencies.sort(Integer::compareTo);
        int index = (int) Math.ceil(latencies.size() * 0.95d) - 1;
        index = Math.max(0, Math.min(index, latencies.size() - 1));
        return latencies.get(index);
    }

    private BigDecimal sumCost(List<LlmCallLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return logs.stream()
                .map(LlmCallLog::getCostYuan)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long sumToken(List<LlmCallLog> logs, Function<LlmCallLog, Integer> tokenGetter) {
        if (logs == null || logs.isEmpty()) {
            return 0L;
        }
        return logs.stream()
                .map(tokenGetter)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private <T> T convertValue(Object raw, Class<T> type) {
        if (raw == null || type == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(raw, type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String asString(Object value, String defaultValue) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return defaultValue;
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return round4((double) numerator / denominator);
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private double round4(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.round(value * 10_000.0d) / 10_000.0d;
    }

    private record StageDefinition(String stage, int index, String label) {
    }

    private record FallbackState(boolean fallbackUsed, double fallbackRate) {
    }

    private record SamplingPlanSnapshot(int expectedSamples,
                                        int generatedSamples,
                                        int missingSamples,
                                        int displayTotal,
                                        boolean samplingVariantShortfall) {
    }
}
