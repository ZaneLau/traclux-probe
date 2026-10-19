package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.SamplingProgress;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceRequest;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceResult;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.ExecutionSignal;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3SamplingRequest;
import com.geoprobe.dto.engine.v42.Node3SamplingResult;
import com.geoprobe.dto.engine.v42.Node3SearchGroundingContext;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.Node4ParsingRequest;
import com.geoprobe.dto.engine.v42.Node4ParsingResult;
import com.geoprobe.dto.engine.v42.Node5AKpiRequest;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.Node5CDashboardRequest;
import com.geoprobe.dto.engine.v42.Node5CDashboardResult;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import com.geoprobe.dto.engine.v42.V42DiagnosisRequest;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service("v42DiagnosisOrchestrator")
public class V42DiagnosisOrchestrator {

    public static final String SUPPORTED_ENGINE_VERSION = V42EngineVersionSupport.DEFAULT_ENGINE_VERSION;
    private static final double DEFAULT_MIN_NODE3_SUCCESS_RATE = 0.90d;
    private final CssmHiStateSpaceService cssmHiStateSpaceService;
    private final OrthogonalMatrixService orthogonalMatrixService;
    private final ProbeRenderingService probeRenderingService;
    private final ProbeQualityGateService probeQualityGateService;
    private final V42Node3SamplingGatewayService node3SamplingGatewayService;
    private final BrandDetectionChain brandDetectionChain;
    private final V42KpiAggregationService v42KpiAggregationService;
    private final InsightGenerationService insightGenerationService;
    private final DashboardDataAggregator dashboardDataAggregator;
    private final V42DiagnosisReportAssembler diagnosisReportAssembler;
    private final V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver;
    private final V42Node3TargetEnginePolicyResolver v42Node3TargetEnginePolicyResolver;
    private final Clock clock;
    private final Supplier<String> reportIdGenerator;
    private final double minNode3SuccessRate;

    @Autowired
    public V42DiagnosisOrchestrator(CssmHiStateSpaceService cssmHiStateSpaceService,
                                    OrthogonalMatrixService orthogonalMatrixService,
                                    ProbeRenderingService probeRenderingService,
                                    ProbeQualityGateService probeQualityGateService,
                                    V42Node3SamplingGatewayService node3SamplingGatewayService,
                                    BrandDetectionChain brandDetectionChain,
                                    V42KpiAggregationService v42KpiAggregationService,
                                    InsightGenerationService insightGenerationService,
                                    DashboardDataAggregator dashboardDataAggregator,
                                    V42DiagnosisReportAssembler diagnosisReportAssembler,
                                    V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver,
                                    V42Node3TargetEnginePolicyResolver v42Node3TargetEnginePolicyResolver,
                                    @Value("${app.v42.node3.min-success-rate:0.9}") double minNode3SuccessRate) {
        this(
                cssmHiStateSpaceService,
                orthogonalMatrixService,
                probeRenderingService,
                probeQualityGateService,
                node3SamplingGatewayService,
                brandDetectionChain,
                v42KpiAggregationService,
                insightGenerationService,
                dashboardDataAggregator,
                diagnosisReportAssembler,
                v42TaskOrderExecutionPolicyResolver,
                v42Node3TargetEnginePolicyResolver,
                Clock.systemDefaultZone(),
                () -> "rpt_" + UUID.randomUUID().toString().replace("-", ""),
                minNode3SuccessRate
        );
    }

    V42DiagnosisOrchestrator(CssmHiStateSpaceService cssmHiStateSpaceService,
                             OrthogonalMatrixService orthogonalMatrixService,
                             ProbeRenderingService probeRenderingService,
                             ProbeQualityGateService probeQualityGateService,
                             V42Node3SamplingGatewayService node3SamplingGatewayService,
                             BrandDetectionChain brandDetectionChain,
                             V42KpiAggregationService v42KpiAggregationService,
                             InsightGenerationService insightGenerationService,
                             DashboardDataAggregator dashboardDataAggregator,
                             V42DiagnosisReportAssembler diagnosisReportAssembler,
                             V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver,
                             V42Node3TargetEnginePolicyResolver v42Node3TargetEnginePolicyResolver,
                             Clock clock,
                             Supplier<String> reportIdGenerator) {
        this(
                cssmHiStateSpaceService,
                orthogonalMatrixService,
                probeRenderingService,
                probeQualityGateService,
                node3SamplingGatewayService,
                brandDetectionChain,
                v42KpiAggregationService,
                insightGenerationService,
                dashboardDataAggregator,
                diagnosisReportAssembler,
                v42TaskOrderExecutionPolicyResolver,
                v42Node3TargetEnginePolicyResolver,
                clock,
                reportIdGenerator,
                DEFAULT_MIN_NODE3_SUCCESS_RATE
        );
    }

    V42DiagnosisOrchestrator(CssmHiStateSpaceService cssmHiStateSpaceService,
                             OrthogonalMatrixService orthogonalMatrixService,
                             ProbeRenderingService probeRenderingService,
                             ProbeQualityGateService probeQualityGateService,
                             V42Node3SamplingGatewayService node3SamplingGatewayService,
                             BrandDetectionChain brandDetectionChain,
                             V42KpiAggregationService v42KpiAggregationService,
                             InsightGenerationService insightGenerationService,
                             DashboardDataAggregator dashboardDataAggregator,
                             V42DiagnosisReportAssembler diagnosisReportAssembler,
                             V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver,
                             V42Node3TargetEnginePolicyResolver v42Node3TargetEnginePolicyResolver,
                             Clock clock,
                             Supplier<String> reportIdGenerator,
                             double minNode3SuccessRate) {
        this.cssmHiStateSpaceService = Objects.requireNonNull(cssmHiStateSpaceService, "cssmHiStateSpaceService 不能为空");
        this.orthogonalMatrixService = Objects.requireNonNull(orthogonalMatrixService, "orthogonalMatrixService 不能为空");
        this.probeRenderingService = Objects.requireNonNull(probeRenderingService, "probeRenderingService 不能为空");
        this.probeQualityGateService = Objects.requireNonNull(probeQualityGateService, "probeQualityGateService 不能为空");
        this.node3SamplingGatewayService = Objects.requireNonNull(node3SamplingGatewayService, "node3SamplingGatewayService 不能为空");
        this.brandDetectionChain = Objects.requireNonNull(brandDetectionChain, "brandDetectionChain 不能为空");
        this.v42KpiAggregationService = Objects.requireNonNull(v42KpiAggregationService, "v42KpiAggregationService 不能为空");
        this.insightGenerationService = Objects.requireNonNull(insightGenerationService, "insightGenerationService 不能为空");
        this.dashboardDataAggregator = Objects.requireNonNull(dashboardDataAggregator, "dashboardDataAggregator 不能为空");
        this.diagnosisReportAssembler = Objects.requireNonNull(diagnosisReportAssembler, "diagnosisReportAssembler 不能为空");
        this.v42TaskOrderExecutionPolicyResolver = Objects.requireNonNull(
                v42TaskOrderExecutionPolicyResolver,
                "v42TaskOrderExecutionPolicyResolver 不能为空"
        );
        this.v42Node3TargetEnginePolicyResolver = Objects.requireNonNull(
                v42Node3TargetEnginePolicyResolver,
                "v42Node3TargetEnginePolicyResolver 不能为空"
        );
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.reportIdGenerator = Objects.requireNonNull(reportIdGenerator, "reportIdGenerator 不能为空");
        this.minNode3SuccessRate = normalizeMinNode3SuccessRate(minNode3SuccessRate);
    }

    public boolean supports(String engineVersion) {
        return V42EngineVersionSupport.isSupported(engineVersion);
    }

    public V42DiagnosisResult execute(V42DiagnosisRequest request) {
        return execute(request, null, V42ExecutionObserver.NO_OP);
    }

    public V42DiagnosisResult execute(V42DiagnosisRequest request, ProgressCallback progressCallback) {
        return execute(request, progressCallback, V42ExecutionObserver.NO_OP);
    }

    public V42DiagnosisResult execute(V42DiagnosisRequest request,
                                      ProgressCallback progressCallback,
                                      V42ExecutionObserver executionObserver) {
        Objects.requireNonNull(request, "V42DiagnosisRequest 不能为空");
        if (!supports(request.engineVersion())) {
            throw new IllegalArgumentException(
                    "V42DiagnosisOrchestrator 仅支持 engineVersion="
                            + V42EngineVersionSupport.supportedVersionsLabel()
            );
        }
        V42ExecutionObserver observer = executionObserver == null ? V42ExecutionObserver.NO_OP : executionObserver;
        long executionStartedAt = System.nanoTime();
        String currentStage = "REQUEST_CONTEXT";

        String taskId = request.taskId();
        String reportId = resolveReportId(request.reportId());
        LocalDateTime createdAt = LocalDateTime.now(clock);
        List<String> resolvedBrandAliases = V42DeterministicAliasResolver.resolveAliasesForName(
                request.brandName(),
                request.brandAliases()
        );
        Map<String, List<String>> resolvedCompetitorAliases = V42DeterministicAliasResolver.resolve(
                request.brandName(),
                request.competitors(),
                new DiagnosisAliasConfigData(resolvedBrandAliases, request.competitorAliases())
        ).competitorAliases();
        safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                currentStage,
                0L,
                buildRequestContextSummary(request)
        ));

        try {
            currentStage = "NODE0_STATE_SPACE";
            emitProgress(progressCallback, currentStage, 0, 5.0d, "正在推断品类状态空间...", 0, 0);
            long stageStartedAt = System.nanoTime();
            CssmHiStateSpaceResult node0Result = cssmHiStateSpaceService.generate(
                    taskId,
                    new CssmHiStateSpaceRequest(
                            request.brandName(),
                            request.category(),
                            request.competitors(),
                            request.categoryAnchors(),
                            request.fallbackAnchorMode(),
                            request.taskOrder(),
                            request.contextPackage()
                    )
            );
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode0Summary(node0Result)
            ));

            V42TaskOrderExecutionPolicy executionPolicy = resolveTaskOrderExecutionPolicy(request);
            currentStage = "NODE1_BLUEPRINTS";
            emitProgress(progressCallback, currentStage, 1, 12.0d, "Node 0 完成，正在生成探针蓝图...", 0, 0);
            stageStartedAt = System.nanoTime();
            int targetProbeCount = executionPolicy.targetProbeCount();
            List<ProbeBlueprint> selectedBlueprints = orthogonalMatrixService.generate(node0Result.pool(), targetProbeCount);
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode1Summary(selectedBlueprints, targetProbeCount, node0Result.pool().contextPackage())
            ));

            currentStage = "NODE2_RENDERING";
            emitProgress(progressCallback, currentStage, 2, 22.0d, "Node 1 完成，正在生成探针文案...", 0, 0);
            stageStartedAt = System.nanoTime();
            List<RenderedProbe> renderedProbes = probeRenderingService.renderAll(
                    taskId,
                    node0Result.pool(),
                    resolvedBrandAliases,
                    selectedBlueprints
            );
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode2RenderSummary(renderedProbes)
            ));
            List<ExecutionSignal> executionSignals = V42ExecutionIntegritySignals.collectBeforeInsights(node0Result, renderedProbes);

            currentStage = "QUALITY_GATE";
            emitProgress(progressCallback, currentStage, 3, 32.0d, "Node 2 完成，正在执行质量门校验...", 0, 0);
            stageStartedAt = System.nanoTime();
            ProbeQualityGateService.ProbeQualityGateResult gateResult = probeQualityGateService.evaluate(renderedProbes);
            if (gateResult == null) {
                throw new IllegalStateException("ProbeQualityGateService.evaluate 返回空结果");
            }
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildQualityGateSummary(gateResult)
            ));
            if (!gateResult.passed()) {
                throw new IllegalStateException("V4.2 probe quality gate failed: " + String.join(" | ", gateResult.violations()));
            }

            currentStage = "NODE3_SAMPLING";
            var realtimeSearchPolicy = executionPolicy.realtimeSearchPolicy();
            Node3TargetEnginePolicy targetEnginePolicy = v42Node3TargetEnginePolicyResolver.resolveRequiredFromTaskOrder(
                    request.taskOrder(),
                    currentStage
            );
            validateNode3PolicyCompatibility(realtimeSearchPolicy, targetEnginePolicy, currentStage);
            emitProgress(progressCallback, currentStage, 4, 40.0d, "质量门通过，正在向 AI 发送探测请求...", 0, 0);
            stageStartedAt = System.nanoTime();
            Node3SamplingResult node3Result = progressCallback == null
                    ? node3SamplingGatewayService.dispatch(
                    Node3SamplingRequest.fromRenderedProbes(
                            request.taskId(),
                            renderedProbes,
                            realtimeSearchPolicy,
                            targetEnginePolicy,
                            renderedProbe -> buildNode3SearchGroundingContext(request, renderedProbe)
                    ),
                    null,
                    event -> safeRealtimeEvent(observer, toRealtimeEventTrace(event))
            )
                    : node3SamplingGatewayService.dispatch(
                    Node3SamplingRequest.fromRenderedProbes(
                            request.taskId(),
                            renderedProbes,
                            realtimeSearchPolicy,
                            targetEnginePolicy,
                            renderedProbe -> buildNode3SearchGroundingContext(request, renderedProbe)
                    ),
                    toSamplingProgressCallback(progressCallback),
                    event -> safeRealtimeEvent(observer, toRealtimeEventTrace(event))
            );
            safeRealtimeEvent(observer, new V42ExecutionObserver.RealtimeEventTrace(
                    "final_gate",
                    false,
                    buildFinalGatePayload(node3Result)
            ));
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode3Summary(node3Result)
            ));
            long node3SuccessCount = node3Result.successCount();
            int node3GeneratedCount = node3Result.probes().size();
            assertNode3SamplingViable(
                    node3Result,
                    targetEnginePolicy.provider(),
                    taskId,
                    reportId,
                    request.engineVersion(),
                    createdAt,
                    LocalDateTime.now(clock),
                    executionSignals
            );

            currentStage = "NODE4_PARSING";
            emitProgress(progressCallback, currentStage, 5, 68.0d, "采样完成，正在解析品牌提及...",
                    Math.toIntExact(node3SuccessCount), node3GeneratedCount);
            stageStartedAt = System.nanoTime();
            Node4ParsingResult node4Result = brandDetectionChain.parse(new Node4ParsingRequest(
                    request.taskId(),
                    request.brandName(),
                    resolvedBrandAliases,
                    request.competitors(),
                    resolvedCompetitorAliases,
                    node3Result.probes()
            ));
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode4Summary(node4Result)
            ));

            currentStage = "NODE5A_KPI";
            emitProgress(progressCallback, currentStage, 6, 76.0d, "Node 4 完成，正在聚合 KPI...",
                    Math.toIntExact(node3SuccessCount), node3GeneratedCount);
            stageStartedAt = System.nanoTime();
            Node5AKpiResult node5AResult = v42KpiAggregationService.aggregate(new Node5AKpiRequest(node4Result.probes()));
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode5AKpiSummary(node5AResult)
            ));

            currentStage = "NODE5B_INSIGHTS";
            emitProgress(progressCallback, currentStage, 7, 84.0d, "KPI 已完成，正在生成商业洞察...",
                    Math.toIntExact(node3SuccessCount), node3GeneratedCount);
            stageStartedAt = System.nanoTime();
            CommercialInsights commercialInsights = insightGenerationService.generate(taskId, node5AResult);
            executionSignals = V42ExecutionIntegritySignals.collect(node0Result, renderedProbes, commercialInsights);
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode5BInsightsSummary(commercialInsights)
            ));

            currentStage = "NODE5C_DASHBOARD";
            emitProgress(progressCallback, currentStage, 8, 90.0d, "商业洞察已完成，正在汇总图表数据...",
                    Math.toIntExact(node3SuccessCount), node3GeneratedCount);
            stageStartedAt = System.nanoTime();
            Node5CDashboardResult node5CResult = dashboardDataAggregator.aggregate(new Node5CDashboardRequest(
                    taskId,
                    request.brandName(),
                    node4Result.probes(),
                    node5AResult,
                    commercialInsights
            ));
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildNode5CSummary(node5CResult)
            ));

            currentStage = "REPORT_ASSEMBLY";
            emitProgress(progressCallback, currentStage, 9, 96.0d, "节点分析完成，正在装配引擎报告...",
                    Math.toIntExact(node3SuccessCount), node3GeneratedCount);
            stageStartedAt = System.nanoTime();
            LocalDateTime completedAt = LocalDateTime.now(clock);
            V42DiagnosisResult diagnosisResult = diagnosisReportAssembler.assemble(
                    taskId,
                    reportId,
                    request.engineVersion(),
                    createdAt,
                    completedAt,
                    selectedBlueprints,
                    renderedProbes,
                    node3Result,
                    node4Result,
                    node5AResult,
                    commercialInsights,
                    node5CResult,
                    executionSignals
            );
            safeStageCompleted(observer, new V42ExecutionObserver.StageTrace(
                    currentStage,
                    elapsedMillis(stageStartedAt),
                    buildReportSummary(diagnosisResult, executionStartedAt)
            ));
            return diagnosisResult;
        } catch (Exception ex) {
            safeExecutionFailed(observer, new V42ExecutionObserver.FailureTrace(
                    currentStage,
                    elapsedMillis(executionStartedAt),
                    ex.getClass().getSimpleName(),
                    sanitizeFailureMessage(ex)
            ));
            throw ex;
        }
    }

    private String resolveReportId(String reportIdFromRequest) {
        String resolved = normalizeOptional(reportIdFromRequest);
        if (resolved != null) {
            return resolved;
        }
        String generated = normalizeOptional(reportIdGenerator.get());
        if (generated == null) {
            throw new IllegalStateException("reportIdGenerator 生成了空 reportId");
        }
        return generated;
    }

    private Consumer<SamplingProgress> toSamplingProgressCallback(ProgressCallback progressCallback) {
        return progress -> emitProgress(
                progressCallback,
                "NODE3_SAMPLING",
                4,
                samplingPercent(progress),
                "正在向 AI 发送探测请求（%d/%d）...".formatted(progress.completed(), progress.total()),
                progress.succeeded(),
                progress.completed()
        );
    }

    private void assertNode3SamplingViable(Node3SamplingResult node3Result,
                                           String providerCode,
                                           String taskId,
                                           String reportId,
                                           String engineVersion,
                                           LocalDateTime createdAt,
                                           LocalDateTime completedAt,
                                           List<ExecutionSignal> executionSignals) {
        if (node3Result == null) {
            throw new IllegalStateException("V4.2 Node 3 返回空结果");
        }
        int generatedCount = node3Result.probes().size();
        long successCount = node3Result.successCount();
        double successRate = generatedCount <= 0 ? 0.0d : successCount * 1.0d / generatedCount;
        if (successCount > 0 && successRate >= minNode3SuccessRate) {
            return;
        }
        String provider = normalizeOptional(providerCode);
        if (provider == null) {
            provider = resolveNode3ProviderLabel(node3Result);
        }
        Map<String, Integer> failureCounts = countNode3FailureReasons(node3Result);
        V42DiagnosisResult.Task taskSnapshot = diagnosisReportAssembler.assembleNode3FailureTask(
                taskId,
                reportId,
                engineVersion,
                createdAt,
                completedAt,
                node3Result,
                executionSignals
        );
        String reason = successCount <= 0
                ? "未产出任何成功样本"
                : "成功样本率低于质量阈值";
        throw new Node3SamplingViabilityException(
                "V4.2 Node 3 " + reason
                        + "，provider=" + defaultIfBlank(provider, "unknown")
                        + ", success=" + successCount + "/" + generatedCount
                        + ", successRate=" + formatRate(successRate)
                        + ", minSuccessRate=" + formatRate(minNode3SuccessRate)
                        + ", failureReasons=" + failureCounts,
                taskSnapshot
        );
    }

    private double normalizeMinNode3SuccessRate(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_MIN_NODE3_SUCCESS_RATE;
        }
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private String formatRate(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private void safeStageCompleted(V42ExecutionObserver observer, V42ExecutionObserver.StageTrace trace) {
        try {
            observer.onStageCompleted(trace);
        } catch (RuntimeException ex) {
            log.warn("V4.2 execution observer stage notification ignored, stage={}, reason={}", trace.stage(), ex.getMessage());
        }
    }

    private void safeExecutionFailed(V42ExecutionObserver observer, V42ExecutionObserver.FailureTrace trace) {
        try {
            observer.onExecutionFailed(trace);
        } catch (RuntimeException ex) {
            log.warn("V4.2 execution observer failure notification ignored, stage={}, reason={}", trace.stage(), ex.getMessage());
        }
    }

    private void safeRealtimeEvent(V42ExecutionObserver observer, V42ExecutionObserver.RealtimeEventTrace trace) {
        try {
            observer.onRealtimeEvent(trace);
        } catch (RuntimeException ex) {
            log.warn("V4.2 execution observer realtime event ignored, eventType={}, reason={}", trace.eventType(), ex.getMessage());
        }
    }

    private V42ExecutionObserver.RealtimeEventTrace toRealtimeEventTrace(V42Node3SamplingGatewayService.DispatchEvent event) {
        return new V42ExecutionObserver.RealtimeEventTrace(
                event.eventType(),
                event.provisional(),
                event.payload()
        );
    }

    private Map<String, Object> buildFinalGatePayload(Node3SamplingResult node3Result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", "NODE3_SAMPLING");
        payload.put("provider", resolveNode3ProviderLabel(node3Result));
        payload.put("searchEnabled", node3Result.realtimeSearchSummary().enabled());
        payload.put("requestedProbeCount", node3Result.realtimeSearchSummary().requestedProbeCount());
        payload.put("satisfiedProbeCount", node3Result.realtimeSearchSummary().satisfiedProbeCount());
        payload.put("evidenceSuccessRate", node3Result.realtimeSearchSummary().evidenceSuccessRate());
        payload.put("fallbackProbeCount", node3Result.realtimeSearchSummary().fallbackProbeCount());
        payload.put("recoveredProbeCount", node3Result.realtimeSearchSummary().recoveredProbeCount());
        payload.put("backfillRoundsUsed", node3Result.realtimeSearchSummary().backfillRoundsUsed());
        payload.put("qualityGatePassed", node3Result.realtimeSearchSummary().qualityGatePassed());
        payload.put("unsatisfiedProbeIds", node3Result.realtimeSearchSummary().unsatisfiedProbeIds());
        payload.put("usageCost", buildUsageCostPayload(node3Result));
        return payload;
    }

    private Map<String, Object> buildRequestContextSummary(V42DiagnosisRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("brandName", request.brandName());
        payload.put("category", request.category());
        payload.put("competitorCount", request.competitors().size());
        payload.put("categoryAnchorCount", request.categoryAnchors().size());
        payload.put("taskHintKeys", sortedKeys(request.taskOrder() == null ? Map.of() : request.taskOrder().businessHints()));
        payload.put("taskMetadataKeys", sortedKeys(request.taskOrder() == null ? Map.of() : request.taskOrder().taskMetadata()));
        payload.put("targetProbeCount", resolveTaskOrderExecutionPolicy(request).targetProbeCount());
        payload.put("contextAssemblyMode", resolveContextAssemblyMode(request.contextPackage()));
        payload.put("groundedSignalCount", request.contextPackage().effectiveGroundedSignals().size());
        payload.put("externalEvidenceSignalCount", countExternalEvidenceSignals(request.contextPackage()));
        payload.put("node0StateInferenceCount", request.contextPackage().node0StateInferenceValues().size());
        payload.put("node2RealizationCount", request.contextPackage().node2RealizationValues().size());
        appendTopicProjectionSummary(payload, request.contextPackage());
        return payload;
    }

    private Map<String, Object> buildNode0Summary(CssmHiStateSpaceResult node0Result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", node0Result.source());
        payload.put("fallbackUsed", node0Result.fallbackUsed());
        payload.put("fallbackReasonCode", node0Result.fallbackReasonCode());
        payload.put("dimensionCount", node0Result.pool().allDimensions().size());
        payload.put("groundedSignalCount", node0Result.pool().effectiveGroundedSignals().size());
        payload.put("executionSignals", V42ExecutionIntegritySignals.node0Signals(node0Result));
        return payload;
    }

    private Map<String, Object> buildNode1Summary(List<ProbeBlueprint> selectedBlueprints,
                                                  int targetProbeCount,
                                                  EngineTaskContextPackage contextPackage) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedBlueprintCount", selectedBlueprints.size());
        payload.put("bucketCounts", countByBlueprintBucket(selectedBlueprints));
        payload.put("requestedProbeCount", targetProbeCount);
        appendTopicProjectionSummary(payload, contextPackage);
        return payload;
    }

    private Map<String, Object> buildNode2RenderSummary(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        long repairCount = renderedProbes.stream()
                .filter(this::hasValidationRepair)
                .count();
        payload.put("probeCount", renderedProbes.size());
        payload.put("fallbackCount", (int) renderedProbes.stream().filter(RenderedProbe::fallbackUsed).count());
        payload.put("repairCount", (int) repairCount);
        payload.put("repairRate", renderedProbes.isEmpty() ? 0.0d : repairCount * 1.0d / renderedProbes.size());
        payload.put("renderSourceCounts", countByRenderedProbe(renderedProbes, RenderedProbe::renderSource));
        payload.put("renderFamilyCounts", countByRenderedProbe(renderedProbes, RenderedProbe::renderFamily));
        payload.put("duplicatePromptCount", duplicatePromptCount(renderedProbes));
        payload.put("executionSignals", V42ExecutionIntegritySignals.node2Signals(renderedProbes));
        return payload;
    }

    private boolean hasValidationRepair(RenderedProbe probe) {
        if (probe == null || probe.triggeredRulesJson() == null) {
            return false;
        }
        Object repairRule = probe.triggeredRulesJson().get("validationRepairApplied");
        return repairRule != null && org.springframework.util.StringUtils.hasText(repairRule.toString());
    }

    private Map<String, Object> buildQualityGateSummary(ProbeQualityGateService.ProbeQualityGateResult gateResult) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("passed", gateResult.passed());
        payload.put("violationCount", gateResult.violations().size());
        payload.put("violations", gateResult.violations());
        payload.put("bucketStats", gateResult.bucketStats());
        return payload;
    }

    private Map<String, Object> buildNode3Summary(Node3SamplingResult node3Result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("dispatchCounts", countByNode3Status(node3Result));
        payload.put("provider", resolveNode3ProviderLabel(node3Result));
        payload.put("searchEnabled", node3Result.realtimeSearchSummary().enabled());
        payload.put("requestedProbeCount", node3Result.realtimeSearchSummary().requestedProbeCount());
        payload.put("satisfiedProbeCount", node3Result.realtimeSearchSummary().satisfiedProbeCount());
        payload.put("evidenceSuccessRate", node3Result.realtimeSearchSummary().evidenceSuccessRate());
        payload.put("fallbackProbeCount", node3Result.realtimeSearchSummary().fallbackProbeCount());
        payload.put("recoveredProbeCount", node3Result.realtimeSearchSummary().recoveredProbeCount());
        payload.put("qualityGatePassed", node3Result.realtimeSearchSummary().qualityGatePassed());
        payload.put("failureReasonCounts", countNode3FailureReasons(node3Result));
        payload.put("usageCost", buildUsageCostPayload(node3Result));
        return payload;
    }

    private Map<String, Object> buildUsageCostPayload(Node3SamplingResult node3Result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("promptTokenTotal", node3Result.usageCostSummary().promptTokenTotal());
        payload.put("completionTokenTotal", node3Result.usageCostSummary().completionTokenTotal());
        payload.put("totalTokenTotal", node3Result.usageCostSummary().totalTokenTotal());
        payload.put("searchCallTotal", node3Result.usageCostSummary().searchCallTotal());
        payload.put("modelCostYuan", node3Result.usageCostSummary().modelCostYuan());
        payload.put("searchPluginCostYuan", node3Result.usageCostSummary().searchPluginCostYuan());
        payload.put("totalCostYuan", node3Result.usageCostSummary().totalCostYuan());
        payload.put("currency", "CNY");
        return payload;
    }

    private String resolveNode3ProviderLabel(Node3SamplingResult node3Result) {
        if (node3Result == null) {
            return "doubao";
        }
        String provider = normalizeOptional(node3Result.providerCode());
        if (provider == null) {
            provider = normalizeOptional(node3Result.realtimeSearchSummary().provider());
        }
        if (provider == null) {
            provider = "doubao";
        }
        if (node3Result.realtimeSearchSummary().enabled()) {
            return provider + "+web_search";
        }
        return provider;
    }

    private Map<String, Object> buildNode4Summary(Node4ParsingResult node4Result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("probeCount", node4Result.probes().size());
        payload.put("mentionedCount", (int) node4Result.probes().stream()
                .filter(probe -> probe.brandDetectionResult().mentioned())
                .count());
        payload.put("detectionLevelCounts", countByNode4DetectionLevel(node4Result));
        return payload;
    }

    private Map<String, Object> buildNode5AKpiSummary(Node5AKpiResult node5AResult) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("overallMentionRate", node5AResult.kpiSummary().overallMentionRate());
        payload.put("recommendRate", node5AResult.kpiSummary().recommendRate());
        payload.put("spontaneousMentionRate", node5AResult.kpiSummary().spontaneousMentionRate());
        payload.put("positiveRate", node5AResult.kpiSummary().positiveRate());
        payload.put("stabilityScore", node5AResult.kpiSummary().stabilityScore());
        return payload;
    }

    private Map<String, Object> buildNode5BInsightsSummary(CommercialInsights insights) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("insightSource", insights.insightSource());
        payload.put("fallbackReasonCode", insights.fallbackReasonCode());
        payload.put("headlineInsight", insights.headlineInsight());
        payload.put("topInsightCount", insights.topThreeInsights().size());
        payload.put("executionSignals", V42ExecutionIntegritySignals.node5BSignals(insights));
        return payload;
    }

    private Map<String, Object> buildNode5CSummary(Node5CDashboardResult node5CResult) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("brandName", node5CResult.brandName());
        payload.put("defenseScore", node5CResult.defenseCompleteness().averageCompositeScore());
        payload.put("chartTypes", List.of(
                node5CResult.dashboardCharts().radar().chartType(),
                node5CResult.dashboardCharts().scatter().chartType(),
                node5CResult.dashboardCharts().sankey().chartType(),
                node5CResult.dashboardCharts().stackedBar().chartType()
        ));
        return payload;
    }

    private Map<String, Object> buildReportSummary(V42DiagnosisResult diagnosisResult, long executionStartedAt) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sampleSize", diagnosisResult.report().sampleSize());
        payload.put("modelProvider", diagnosisResult.report().modelProvider());
        payload.put("overallMentionRate", diagnosisResult.report().kpiSummary().overallMentionRate());
        payload.put("successfulProbes", diagnosisResult.task().successfulProbes());
        payload.put("failedProbes", diagnosisResult.task().failedProbes());
        payload.put("executionSignalCount", V42ExecutionIntegrityView.signalCount(diagnosisResult.task().executionSignals()));
        payload.put("executionDegraded", V42ExecutionIntegrityView.degraded(diagnosisResult.task().executionSignals()));
        payload.put("totalDurationMillis", elapsedMillis(executionStartedAt));
        return payload;
    }

    private List<String> sortedKeys(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.keySet().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .sorted()
                .toList();
    }

    private String resolveContextAssemblyMode(EngineTaskContextPackage contextPackage) {
        if (contextPackage == null || contextPackage.extensibleSignals().isEmpty()) {
            return "unknown";
        }
        Object value = contextPackage.extensibleSignals().get("contextAssemblyMode");
        return value instanceof String text && StringUtils.hasText(text) ? text.trim() : "unknown";
    }

    private void appendTopicProjectionSummary(Map<String, Object> payload, EngineTaskContextPackage contextPackage) {
        if (payload == null || contextPackage == null) {
            return;
        }
        LightConeV43TopicStateProjectionSupport.ProjectionRuntimeContext projectionContext =
                LightConeV43TopicStateProjectionSupport.resolveRuntimeContext(contextPackage.extensibleSignals());
        if (!projectionContext.active()) {
            return;
        }
        payload.put("topicProjectionCount", projectionContext.projections().size());
        payload.put("topicProjectionLabels", projectionContext.canonicalTopicLabels());
        payload.put("topicProjectionSelectionMode", projectionContext.selectionMode());
    }

    private long countExternalEvidenceSignals(EngineTaskContextPackage contextPackage) {
        if (contextPackage == null) {
            return 0L;
        }
        return contextPackage.effectiveGroundedSignals().stream()
                .filter(signal -> StringUtils.hasText(signal.evidence())
                        || (StringUtils.hasText(signal.source()) && signal.source().startsWith("external_")))
                .count();
    }

    private V42TaskOrderExecutionPolicy resolveTaskOrderExecutionPolicy(V42DiagnosisRequest request) {
        if (request == null || request.taskOrder() == null) {
            throw new IllegalStateException("V4.2 缺少前台任务单，无法规划探针数量");
        }
        Map<String, Object> hints = request.taskOrder().businessHints();
        return v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderHints(hints, "v42_orchestrator");
    }

    private Map<String, Object> readObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = coerceString(entry.getKey());
            if (StringUtils.hasText(key) && entry.getValue() != null) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(source.size());
        for (Object item : source) {
            String normalized = coerceString(item);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private String coerceString(Object raw) {
        if (raw == null) {
            return null;
        }
        String normalized = String.valueOf(raw).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean coerceBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String normalized = coerceString(raw);
        return normalized != null && Boolean.parseBoolean(normalized);
    }

    private Map<String, Integer> countByBlueprintBucket(List<ProbeBlueprint> blueprints) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (ProbeBlueprint blueprint : blueprints) {
            counts.merge(blueprint.bucketType().name(), 1, Integer::sum);
        }
        return sortCounts(counts);
    }

    private Map<String, Integer> countByRenderedProbe(List<RenderedProbe> renderedProbes,
                                                      java.util.function.Function<RenderedProbe, String> keyFn) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            String key = keyFn.apply(probe);
            counts.merge(StringUtils.hasText(key) ? key.trim() : "UNKNOWN", 1, Integer::sum);
        }
        return sortCounts(counts);
    }

    private Map<String, Integer> countByNode3Status(Node3SamplingResult node3Result) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        node3Result.probes().forEach(probe -> counts.merge(probe.dispatchStatus().name(), 1, Integer::sum));
        return sortCounts(counts);
    }

    private Map<String, Integer> countNode3FailureReasons(Node3SamplingResult node3Result) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        node3Result.probes().stream()
                .filter(probe -> probe.dispatchStatus().name().equals("FAILED"))
                .forEach(probe -> counts.merge(
                        StringUtils.hasText(probe.failureReason()) ? probe.failureReason().trim() : "UNKNOWN_FAILURE",
                        1,
                        Integer::sum
                ));
        return sortCounts(counts);
    }

    private Map<String, Integer> countByNode4DetectionLevel(Node4ParsingResult node4Result) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        node4Result.probes().forEach(probe -> counts.merge(
                probe.brandDetectionResult().mentioned()
                        ? probe.brandDetectionResult().detectionLevel()
                        : "NONE",
                1,
                Integer::sum
        ));
        return sortCounts(counts);
    }

    private Map<String, Integer> sortCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private int duplicatePromptCount(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Integer> byCanonicalPrompt = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            byCanonicalPrompt.merge(canonicalPrompt(probe.renderedPrompt()), 1, Integer::sum);
        }
        return byCanonicalPrompt.values().stream()
                .filter(count -> count > 1)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String canonicalPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        return prompt.trim()
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+", "")
                .toLowerCase();
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String sanitizeFailureMessage(Exception ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return ex == null ? "UNKNOWN_ERROR" : ex.getClass().getSimpleName();
        }
        String message = ex.getMessage().trim();
        return message.length() <= 400 ? message : message.substring(0, 400);
    }

    private double samplingPercent(SamplingProgress progress) {
        if (progress == null || progress.total() <= 0) {
            return 40.0d;
        }
        double ratio = Math.min(1.0d, Math.max(0.0d, (double) progress.completed() / progress.total()));
        return 40.0d + ratio * 28.0d;
    }

    private void emitProgress(ProgressCallback progressCallback,
                              String currentStep,
                              int stepsCompleted,
                              double percent,
                              String currentStepLabel) {
        emitProgress(progressCallback, currentStep, stepsCompleted, percent, currentStepLabel, null, null);
    }

    private void emitProgress(ProgressCallback progressCallback,
                              String currentStep,
                              int stepsCompleted,
                              double percent,
                              String currentStepLabel,
                              Integer samplingCompleted,
                              Integer samplingTotal) {
        if (progressCallback == null) {
            return;
        }
        progressCallback.onProgress(new ProgressUpdate(
                currentStep,
                stepsCompleted,
                percent,
                currentStepLabel,
                samplingCompleted,
                samplingTotal
        ));
    }

    private Node3SearchGroundingContext buildNode3SearchGroundingContext(V42DiagnosisRequest request,
                                                                         RenderedProbe renderedProbe) {
        if (request == null || renderedProbe == null) {
            return Node3SearchGroundingContext.empty();
        }
        String visibleBrandName = renderedProbe.exposurePolicy() == com.geoprobe.dto.engine.v42.ProbeExposurePolicy.BRAND_VISIBLE
                ? request.brandName()
                : null;
        return new Node3SearchGroundingContext(
                request.category(),
                request.categoryAnchors(),
                visibleBrandName
        );
    }

    private void validateNode3PolicyCompatibility(com.geoprobe.dto.engine.v42.RealtimeSearchPolicy realtimeSearchPolicy,
                                                  Node3TargetEnginePolicy targetEnginePolicy,
                                                  String scene) {
        if (realtimeSearchPolicy != null
                && realtimeSearchPolicy.enabled()
                && targetEnginePolicy != null
                && !"doubao".equals(targetEnginePolicy.provider())) {
            throw new IllegalArgumentException(
                    "samplingRealtimeSearch 当前仅支持 doubao，scene=" + scene + ", provider=" + targetEnginePolicy.provider()
            );
        }
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized == null ? fallback : normalized;
    }

    public record ProgressUpdate(String currentStep,
                                 int stepsCompleted,
                                 double percent,
                                 String currentStepLabel,
                                 Integer samplingCompleted,
                                 Integer samplingTotal) {
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(ProgressUpdate update);
    }

}
