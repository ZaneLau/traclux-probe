package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.ExecutionSignal;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3SamplingResult;
import com.geoprobe.dto.engine.v42.Node4ParsingResult;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.Node5CDashboardResult;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service("v42DiagnosisReportAssembler")
public class V42DiagnosisReportAssembler {

    static final String TASK_STATUS_COMPLETED = "COMPLETED";
    static final String TASK_STATUS_FAILED = "FAILED";

    private static final List<V42DiagnosisResult.DisplayNameMapping> DISPLAY_NAME_MAPPINGS = List.of(
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.spontaneousMentionRate", "AI 首选率"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.confirmationResponseRate", "AI 确认率"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.overallMentionRate", "总体提及率"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.recommendRate", "推荐率"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.positiveRate", "正向率"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores", "六维心智穿透度"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D1", "D1 认知异常"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D2", "D2 目的论目标"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D3", "D3 价值论驱动"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D4", "D4 认知负荷"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D5", "D5 时间约束"),
            new V42DiagnosisResult.DisplayNameMapping("kpiSummary.dimensionRadarScores.D6", "D6 交互模态"),
            new V42DiagnosisResult.DisplayNameMapping("commercialInsights.insightSource", "洞察来源"),
            new V42DiagnosisResult.DisplayNameMapping("defenseCompleteness.averageCompositeScore", "AI 防御完整度"),
            new V42DiagnosisResult.DisplayNameMapping("dashboardCharts.radar", "六维能见度穿透雷达"),
            new V42DiagnosisResult.DisplayNameMapping("dashboardCharts.scatter", "语义引力轨道图"),
            new V42DiagnosisResult.DisplayNameMapping("dashboardCharts.sankey", "竞品拦截漏斗"),
            new V42DiagnosisResult.DisplayNameMapping("dashboardCharts.stacked_bar", "三桶诊断分布")
    );

    public V42DiagnosisResult assemble(String taskId,
                                       String reportId,
                                       String engineVersion,
                                       LocalDateTime createdAt,
                                       LocalDateTime completedAt,
                                       List<ProbeBlueprint> blueprints,
                                       List<RenderedProbe> renderedProbes,
                                       Node3SamplingResult node3SamplingResult,
                                       Node4ParsingResult node4ParsingResult,
                                       Node5AKpiResult node5AKpiResult,
                                       CommercialInsights commercialInsights,
                                       Node5CDashboardResult node5CDashboardResult,
                                       List<ExecutionSignal> executionSignals) {
        taskId = normalizeRequired(taskId, "taskId 不能为空");
        reportId = normalizeRequired(reportId, "reportId 不能为空");
        engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        completedAt = Objects.requireNonNull(completedAt, "completedAt 不能为空");
        Objects.requireNonNull(blueprints, "blueprints 不能为空");
        Objects.requireNonNull(renderedProbes, "renderedProbes 不能为空");
        Objects.requireNonNull(node3SamplingResult, "node3SamplingResult 不能为空");
        Objects.requireNonNull(node4ParsingResult, "node4ParsingResult 不能为空");
        Objects.requireNonNull(node5AKpiResult, "node5AKpiResult 不能为空");
        Objects.requireNonNull(commercialInsights, "commercialInsights 不能为空");
        Objects.requireNonNull(node5CDashboardResult, "node5CDashboardResult 不能为空");
        executionSignals = executionSignals == null ? List.of() : List.copyOf(executionSignals);

        int sampleSize = validateSampleSize(blueprints, renderedProbes, node3SamplingResult, node4ParsingResult);
        validateProbeAlignment(blueprints, renderedProbes, node3SamplingResult.probes(), node4ParsingResult.probes());
        validatePassThroughContracts(node5AKpiResult, commercialInsights, node5CDashboardResult);
        validateFrozenFields(node5CDashboardResult.kpiSummary(), node5CDashboardResult.commercialInsights(), node5CDashboardResult.dashboardCharts());
        String modelProvider = resolveModelProvider(node3SamplingResult);

        V42DiagnosisResult.Task task = new V42DiagnosisResult.Task(
                taskId,
                reportId,
                engineVersion,
                TASK_STATUS_COMPLETED,
                createdAt,
                completedAt,
                sampleSize,
                modelProvider,
                node3SamplingResult.successCount(),
                node3SamplingResult.failedCount(),
                node3SamplingResult.realtimeSearchSummary(),
                node3SamplingResult.usageCostSummary(),
                buildProbeTrace(node4ParsingResult.probes()),
                executionSignals
        );

        V42DiagnosisResult.Report report = new V42DiagnosisResult.Report(
                taskId,
                reportId,
                engineVersion,
                createdAt,
                completedAt,
                sampleSize,
                modelProvider,
                buildMethodology(sampleSize),
                buildDisclaimer(sampleSize),
                node5CDashboardResult.kpiSummary(),
                node5CDashboardResult.commercialInsights(),
                node5CDashboardResult.defenseCompleteness(),
                node5CDashboardResult.dashboardCharts(),
                DISPLAY_NAME_MAPPINGS,
                executionSignals
        );

        return new V42DiagnosisResult(task, report);
    }

    public V42DiagnosisResult.Task assembleNode3FailureTask(String taskId,
                                                            String reportId,
                                                            String engineVersion,
                                                            LocalDateTime createdAt,
                                                            LocalDateTime completedAt,
                                                            Node3SamplingResult node3SamplingResult,
                                                            List<ExecutionSignal> executionSignals) {
        taskId = normalizeRequired(taskId, "taskId 不能为空");
        reportId = normalizeRequired(reportId, "reportId 不能为空");
        engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        completedAt = Objects.requireNonNull(completedAt, "completedAt 不能为空");
        Objects.requireNonNull(node3SamplingResult, "node3SamplingResult 不能为空");
        executionSignals = executionSignals == null ? List.of() : List.copyOf(executionSignals);
        int sampleSize = node3SamplingResult.probes().size();
        if (sampleSize <= 0) {
            throw new IllegalStateException("Node 3 failure task sampleSize 必须大于 0");
        }
        return new V42DiagnosisResult.Task(
                taskId,
                reportId,
                engineVersion,
                TASK_STATUS_FAILED,
                createdAt,
                completedAt,
                sampleSize,
                resolveModelProvider(node3SamplingResult),
                node3SamplingResult.successCount(),
                node3SamplingResult.failedCount(),
                node3SamplingResult.realtimeSearchSummary(),
                node3SamplingResult.usageCostSummary(),
                buildProbeTraceFromNode3(node3SamplingResult.probes()),
                executionSignals
        );
    }

    private String resolveModelProvider(Node3SamplingResult node3SamplingResult) {
        if (node3SamplingResult == null) {
            return "doubao";
        }
        String provider = normalizeOptional(node3SamplingResult.providerCode());
        if (provider == null && node3SamplingResult.realtimeSearchSummary() != null) {
            provider = normalizeOptional(node3SamplingResult.realtimeSearchSummary().provider());
        }
        if (provider == null) {
            provider = "doubao";
        }
        if (node3SamplingResult.realtimeSearchSummary() != null
                && node3SamplingResult.realtimeSearchSummary().enabled()) {
            return provider + "+web_search";
        }
        return provider;
    }

    private int validateSampleSize(List<ProbeBlueprint> blueprints,
                                   List<RenderedProbe> renderedProbes,
                                   Node3SamplingResult node3SamplingResult,
                                   Node4ParsingResult node4ParsingResult) {
        int sampleSize = blueprints.size();
        if (sampleSize <= 0) {
            throw new IllegalStateException("V4.2 sampleSize 必须大于 0");
        }
        if (renderedProbes.size() != sampleSize
                || node3SamplingResult.probes().size() != sampleSize
                || node4ParsingResult.probes().size() != sampleSize) {
            throw new IllegalStateException("Node 0-4 输出 sampleSize 不一致");
        }
        if (node3SamplingResult.successCount() + node3SamplingResult.failedCount() != sampleSize) {
            throw new IllegalStateException("Node 3 success/failed 统计与 sampleSize 不一致");
        }
        return sampleSize;
    }

    private String buildMethodology(int sampleSize) {
        return """
                固定主链：Node 0 生成 CSSM-HI 六维状态空间，Node 1 直接按前台任务单规划当前批次探针规模与三桶分配，Node 2 受限渲染，
                Node 3 实际采样，Node 4 以 L1/L2/L3 责任链解析品牌提及，Node 5A/5B/5C 直接装配 KPI、商业洞察、防御完整度与图表数据。
                本次任务样本量为 %d probes（由任务单策略驱动）；雷达图品牌主序列透传 Node 5A 的 dimensionRadarScores，语义定义为相对 spontaneous baseline 的结构对比度；
                竞品序列按相同 baseline-deviation 原则在 openDiscovery 样本上聚合。长度分层采用当前任务 completionTokenCount 分布的动态分位数（p33 / p66）进行
                Short / Medium / Long 归一化，避免跨品类固定阈值偏差；scatter.brandDistance 使用六维 profile divergence 的 0-1 归一化结果表示“品牌核心价值偏移度”。
                """.formatted(sampleSize);
    }

    private String buildDisclaimer(int sampleSize) {
        return """
                本报告以 API 层引擎结果为准，UI 仅允许做展示文案压缩与排版，不得改写字段口径。
                sampleSize 表示本次任务真实执行的 probes 数量（当前任务=%d）；Node 3 当前未启用 early stop，采样结果保留完整响应。
                scatter.recommendRate 与 sankey 的 candidate/recommend 节点均直接基于 Node 4 的响应级判定装配，不做 UI 二次推断。
                """.formatted(sampleSize);
    }

    private void validateProbeAlignment(List<ProbeBlueprint> blueprints,
                                        List<RenderedProbe> renderedProbes,
                                        List<Node3ProbeOutput> node3Probes,
                                        List<Node4ProbeOutput> node4Probes) {
        for (int index = 0; index < blueprints.size(); index++) {
            ProbeBlueprint blueprint = blueprints.get(index);
            RenderedProbe renderedProbe = renderedProbes.get(index);
            Node3ProbeOutput node3Probe = node3Probes.get(index);
            Node4ProbeOutput node4Probe = node4Probes.get(index);

            validateProbeId("Node1->Node2", blueprint.probeId(), renderedProbe.probeId());
            validateProbeId("Node2->Node3", renderedProbe.probeId(), node3Probe.probeId());
            validateProbeId("Node3->Node4", node3Probe.probeId(), node4Probe.probeId());

            if (blueprint.bucketType() != renderedProbe.bucketType()
                    || renderedProbe.bucketType() != node3Probe.bucketType()
                    || node3Probe.bucketType() != node4Probe.bucketType()) {
                throw new IllegalStateException("Probe bucketType 在编排链路中发生漂移");
            }
        }
    }

    private void validateProbeId(String stage, String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(stage + " probeId 未保持全链路透传");
        }
    }

    private void validatePassThroughContracts(Node5AKpiResult node5AKpiResult,
                                              CommercialInsights commercialInsights,
                                              Node5CDashboardResult node5CDashboardResult) {
        if (!node5AKpiResult.kpiSummary().equals(node5CDashboardResult.kpiSummary())) {
            throw new IllegalStateException("Phase 9 不得重算或改写 Node 5A 的 kpiSummary");
        }
        if (!commercialInsights.equals(node5CDashboardResult.commercialInsights())) {
            throw new IllegalStateException("Phase 9 不得改写 Node 5B 的 commercialInsights");
        }
    }

    private void validateFrozenFields(KpiSummary kpiSummary,
                                      CommercialInsights commercialInsights,
                                      DashboardCharts dashboardCharts) {
        if (kpiSummary.dimensionRadarScores().isEmpty()) {
            throw new IllegalStateException("dimensionRadarScores 不能为空");
        }
        if (!kpiSummary.dimensionRadarScores().keySet().containsAll(List.of("D1", "D2", "D3", "D4", "D5", "D6"))) {
            throw new IllegalStateException("dimensionRadarScores 必须保留 D1-D6 键");
        }
        if (!StringUtils.hasText(commercialInsights.insightSource())) {
            throw new IllegalStateException("commercialInsights.insightSource 不能为空");
        }
        Objects.requireNonNull(dashboardCharts.radar(), "dashboardCharts.radar 不能为空");
        Objects.requireNonNull(dashboardCharts.scatter(), "dashboardCharts.scatter 不能为空");
        Objects.requireNonNull(dashboardCharts.sankey(), "dashboardCharts.sankey 不能为空");
        Objects.requireNonNull(dashboardCharts.stackedBar(), "dashboardCharts.stackedBar 不能为空");
    }

    private List<V42DiagnosisResult.ProbeTrace> buildProbeTrace(List<Node4ProbeOutput> probes) {
        V42LengthBucketResolver.LengthBucketThresholds thresholds = V42LengthBucketResolver.fromProbes(probes);
        return probes.stream()
                .map(probe -> new V42DiagnosisResult.ProbeTrace(
                        probe.probeId(),
                        probe.bucketType(),
                        probe.abstractTags(),
                        probe.translatedTags(),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().surfaceIntent()),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().interactionPosture()),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().conversationMode()),
                        probe.exposurePolicy(),
                        probe.renderedPrompt(),
                        probe.renderFamily(),
                        probe.dispatchStatus(),
                        probe.brandDetectionResult().probeType(),
                        probe.brandDetectionResult().mentioned(),
                        summarizeResponseText(probe.rawResponse()),
                        normalizeOptional(probe.rawResponse().fullText()),
                        normalizeOptional(probe.rawResponse().thinkContent()),
                        probe.rawResponse().citations(),
                        probe.rawResponse().promptTokenCount(),
                        probe.brandDetectionResult().responseTokenCount(),
                        probe.rawResponse().totalTokenCount(),
                        probe.rawResponse().latencyMs(),
                        thresholds.bucketFor(probe.brandDetectionResult().responseTokenCount()),
                        probe.brandDetectionResult().detectionLevel(),
                        probe.failureReason(),
                        probe.rawResponse().searchRequested(),
                        probe.rawResponse().searchSatisfied(),
                        probe.rawResponse().searchFallbackFlag(),
                        probe.rawResponse().searchEvidence(),
                        probe.rawResponse().searchCallCount(),
                        probe.rawResponse().searchQueries(),
                        normalizeOptional(probe.rawResponse().modelId()),
                        normalizeOptional(probe.rawResponse().routingIntent()),
                        probe.rawResponse().routingCandidates(),
                        probe.roundNo(),
                        probe.attemptNo(),
                        probe.triggeredRulesJson(),
                        probe.brandDetectionResult()
                ))
                .toList();
    }

    private List<V42DiagnosisResult.ProbeTrace> buildProbeTraceFromNode3(List<Node3ProbeOutput> probes) {
        V42LengthBucketResolver.LengthBucketThresholds thresholds = V42LengthBucketResolver.fromCompletionTokens(
                probes.stream()
                        .filter(probe -> probe != null && probe.dispatchStatus() == com.geoprobe.dto.engine.v42.ProbeDispatchStatus.SUCCESS)
                        .map(probe -> probe.rawResponse().responseTokenCount())
                        .toList()
        );
        return probes.stream()
                .map(probe -> new V42DiagnosisResult.ProbeTrace(
                        probe.probeId(),
                        probe.bucketType(),
                        probe.abstractTags(),
                        probe.translatedTags(),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().surfaceIntent()),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().interactionPosture()),
                        profileValue(probe.realizationPlan(), plan -> plan.simulationIntentProfile().conversationMode()),
                        probe.exposurePolicy(),
                        probe.renderedPrompt(),
                        probe.renderFamily(),
                        probe.dispatchStatus(),
                        com.geoprobe.dto.engine.v42.ProbeType.OPEN_DISCOVERY,
                        false,
                        summarizeResponseText(probe.rawResponse()),
                        normalizeOptional(probe.rawResponse().fullText()),
                        normalizeOptional(probe.rawResponse().thinkContent()),
                        probe.rawResponse().citations(),
                        probe.rawResponse().promptTokenCount(),
                        probe.rawResponse().responseTokenCount(),
                        probe.rawResponse().totalTokenCount(),
                        probe.rawResponse().latencyMs(),
                        thresholds.bucketFor(probe.rawResponse().responseTokenCount()),
                        null,
                        probe.failureReason(),
                        probe.rawResponse().searchRequested(),
                        probe.rawResponse().searchSatisfied(),
                        probe.rawResponse().searchFallbackFlag(),
                        probe.rawResponse().searchEvidence(),
                        probe.rawResponse().searchCallCount(),
                        probe.rawResponse().searchQueries(),
                        normalizeOptional(probe.rawResponse().modelId()),
                        normalizeOptional(probe.rawResponse().routingIntent()),
                        probe.rawResponse().routingCandidates(),
                        probe.roundNo(),
                        probe.attemptNo(),
                        probe.triggeredRulesJson(),
                        null
                ))
                .toList();
    }

    private String profileValue(RealizationPlan plan, java.util.function.Function<RealizationPlan, String> extractor) {
        if (plan == null) {
            return null;
        }
        return normalizeOptional(extractor.apply(plan));
    }

    private String summarizeResponseText(com.geoprobe.dto.engine.v42.RawResponse rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        String normalized = normalizeOptional(rawResponse.fullText());
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400) + "...";
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
