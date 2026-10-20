package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceRequest;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceResult;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3SamplingRequest;
import com.geoprobe.dto.engine.v42.Node3SamplingResult;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.Node4ParsingRequest;
import com.geoprobe.dto.engine.v42.Node4ParsingResult;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5AKpiRequest;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.Node5CDashboardRequest;
import com.geoprobe.dto.engine.v42.Node5CDashboardResult;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.ProbeStatus;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import com.geoprobe.dto.engine.v42.V42DiagnosisRequest;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V42DiagnosisOrchestratorTest {

    private final CssmHiStateSpaceService cssmHiStateSpaceService = mock(CssmHiStateSpaceService.class);
    private final OrthogonalMatrixService orthogonalMatrixService = mock(OrthogonalMatrixService.class);
    private final ProbeRenderingService probeRenderingService = mock(ProbeRenderingService.class);
    private final ProbeQualityGateService probeQualityGateService = mock(ProbeQualityGateService.class);
    private final V42Node3SamplingGatewayService node3SamplingGatewayService = mock(V42Node3SamplingGatewayService.class);
    private final BrandDetectionChain brandDetectionChain = mock(BrandDetectionChain.class);
    private final V42KpiAggregationService v42KpiAggregationService = mock(V42KpiAggregationService.class);
    private final InsightGenerationService insightGenerationService = mock(InsightGenerationService.class);
    private final DashboardDataAggregator dashboardDataAggregator = mock(DashboardDataAggregator.class);
    private final V42DiagnosisReportAssembler diagnosisReportAssembler = new V42DiagnosisReportAssembler();
    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy = mock(V42TargetProbeCountPolicy.class);
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-26T10:15:30Z"), ZoneOffset.UTC);
    private final ProbeSimulationPlanningService planningService = new ProbeSimulationPlanningService();

    @Test
    void shouldSupportEngineVersion42And43() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();

        assertTrue(orchestrator.supports("4.2"));
        assertTrue(orchestrator.supports("4.3"));
        assertTrue(orchestrator.supports(" 4.2 "));
        assertTrue(orchestrator.supports(" 4.3 "));
        assertFalse(orchestrator.supports("4.1"));
        assertFalse(orchestrator.supports(null));

        V42DiagnosisRequest invalidRequest = new V42DiagnosisRequest(
                "task-v3",
                null,
                "3.0",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of(),
                Map.of()
        );
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.execute(invalidRequest)
        );
        assertTrue(exception.getMessage().contains("4.2 / 4.3"));
    }

    @Test
    void shouldOrchestrateNode0To5CAndAssembleIndependentReportEnvelope() throws Exception {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-phase9-001",
                null,
                "4.2",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of("农夫"),
                Map.of("怡宝", List.of("C'estbon"))
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        Node3SamplingResult node3Result = sampleNode3Result(renderedProbes);
        Node4ParsingResult node4Result = sampleNode4Result(node3Result.probes());
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights commercialInsights = sampleInsights();
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                commercialInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any())).thenReturn(node3Result);
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class))).thenReturn(node4Result);
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(commercialInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        V42DiagnosisResult result = orchestrator.execute(request);

        verify(probeQualityGateService).evaluate(renderedProbes);
        ArgumentCaptor<Node3SamplingRequest> node3RequestCaptor = ArgumentCaptor.forClass(Node3SamplingRequest.class);
        verify(node3SamplingGatewayService).dispatch(node3RequestCaptor.capture(), any(), any());
        assertFalse(node3RequestCaptor.getValue().realtimeSearchPolicy().enabled());
        assertEquals("doubao", node3RequestCaptor.getValue().realtimeSearchPolicy().provider());
        assertEquals("doubao", node3RequestCaptor.getValue().targetEnginePolicy().provider());
        assertEquals(request.category(), node3RequestCaptor.getValue().probes().getFirst().searchGroundingContext().category());
        assertEquals(request.categoryAnchors(), node3RequestCaptor.getValue().probes().getFirst().searchGroundingContext().categoryAnchors());

        ArgumentCaptor<CssmHiStateSpaceRequest> node0RequestCaptor = ArgumentCaptor.forClass(CssmHiStateSpaceRequest.class);
        verify(cssmHiStateSpaceService).generate(eq(request.taskId()), node0RequestCaptor.capture());
        CssmHiStateSpaceRequest node0Request = node0RequestCaptor.getValue();
        assertEquals(request.brandName(), node0Request.brandName());
        assertEquals(request.category(), node0Request.category());
        assertEquals(request.categoryAnchors(), node0Request.categoryAnchors());
        assertSame(request.taskOrder(), node0Request.taskOrder());
        assertSame(request.contextPackage(), node0Request.contextPackage());

        ArgumentCaptor<Node4ParsingRequest> node4RequestCaptor = ArgumentCaptor.forClass(Node4ParsingRequest.class);
        verify(brandDetectionChain).parse(node4RequestCaptor.capture());
        Node4ParsingRequest node4Request = node4RequestCaptor.getValue();
        assertEquals(request.brandAliases(), node4Request.brandAliases());
        assertEquals(List.of("C'estbon"), node4Request.competitorAliases().get("怡宝"));
        assertTrue(node4Request.competitorAliases().containsKey("百岁山"));

        assertEquals("task-phase9-001", result.task().taskId());
        assertEquals("rpt-phase9-001", result.task().reportId());
        assertEquals("4.2", result.task().engineVersion());
        assertEquals("COMPLETED", result.task().status());
        assertEquals(100, result.task().sampleSize());
        assertEquals("doubao", result.task().modelProvider());
        assertEquals(98L, result.task().successfulProbes());
        assertEquals(2L, result.task().failedProbes());
        assertEquals(2, result.task().executionSignals().size());
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE2_RENDER_REPAIR_PRESENT")
                        && signal.reasonCodes().equals(List.of("anchor"))));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE5B_INSIGHT_TEMPLATE_FALLBACK")
                        && signal.reasonCodes().equals(List.of(V42CreativeDegradationReasons.UNKNOWN_FAILURE))));
        assertEquals(100, result.task().probeTrace().size());
        assertEquals("probe-001", result.task().probeTrace().getFirst().probeId());
        assertEquals(BucketType.ORTHOGONAL, result.task().probeTrace().getFirst().bucketType());
        assertEquals("exploration", result.task().probeTrace().getFirst().abstractTags().get("D1"));
        assertEquals("状态-1-1", result.task().probeTrace().getFirst().translatedTags().get("D1"));
        assertEquals("seek_clear_answer", result.task().probeTrace().getFirst().surfaceIntent());
        assertEquals("exploratory_browsing", result.task().probeTrace().getFirst().interactionPosture());
        assertEquals("single_turn_search_like", result.task().probeTrace().getFirst().conversationMode());
        assertEquals(ProbeExposurePolicy.BRAND_HIDDEN, result.task().probeTrace().getFirst().exposurePolicy());
        assertEquals("rendered-probe-001", result.task().probeTrace().getFirst().renderedPrompt());
        assertEquals("orthogonal_constrained_llm", result.task().probeTrace().getFirst().renderFamily());
        assertEquals("response-probe-001", result.task().probeTrace().getFirst().responseText());
        assertEquals(180, result.task().probeTrace().getFirst().responseTokenCount());
        assertEquals("Short", result.task().probeTrace().getFirst().responseLengthBucket());
        assertEquals("L1_EXACT", result.task().probeTrace().getFirst().detectionLevel());

        assertEquals("task-phase9-001", result.report().taskId());
        assertEquals("rpt-phase9-001", result.report().reportId());
        assertEquals("4.2", result.report().engineVersion());
        assertEquals(100, result.report().sampleSize());
        assertEquals("doubao", result.report().modelProvider());
        assertEquals(2, result.report().executionSignals().size());
        assertTrue(result.report().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE2_RENDER_REPAIR_PRESENT")
                        && signal.reasonCodes().equals(List.of("anchor"))));
        assertTrue(result.report().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE5B_INSIGHT_TEMPLATE_FALLBACK")
                        && signal.reasonCodes().equals(List.of(V42CreativeDegradationReasons.UNKNOWN_FAILURE))));
        assertTrue(result.report().methodology().contains("由任务单策略驱动"));
        assertTrue(result.report().methodology().contains("profile divergence"));
        assertTrue(result.report().disclaimer().contains("API"));
        assertTrue(result.report().disclaimer().contains("early stop"));
        assertSame(node5AResult.kpiSummary(), result.report().kpiSummary());
        assertSame(commercialInsights, result.report().commercialInsights());
        assertSame(node5CResult.defenseCompleteness(), result.report().defenseCompleteness());
        assertSame(node5CResult.dashboardCharts(), result.report().dashboardCharts());
        assertTrue(result.report().displayNameMappings().stream()
                .anyMatch(item -> item.internalKey().equals("kpiSummary.spontaneousMentionRate")
                        && item.displayName().equals("AI 首选率")));
        assertTrue(result.report().displayNameMappings().stream()
                .anyMatch(item -> item.internalKey().equals("kpiSummary.confirmationResponseRate")
                        && item.displayName().equals("AI 确认率")));
        assertTrue(result.report().displayNameMappings().stream()
                .anyMatch(item -> item.internalKey().equals("kpiSummary.dimensionRadarScores")
                        && item.displayName().equals("六维心智穿透度")));
        assertTrue(result.report().displayNameMappings().stream()
                .anyMatch(item -> item.internalKey().equals("defenseCompleteness.averageCompositeScore")
                        && item.displayName().equals("AI 防御完整度")));
        assertTrue(result.report().displayNameMappings().stream()
                .anyMatch(item -> item.internalKey().equals("kpiSummary.dimensionRadarScores.D1")
                        && item.displayName().equals("D1 认知异常")));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String json = objectMapper.writeValueAsString(result);
        assertTrue(json.contains("\"engineVersion\":\"4.2\""));
        assertTrue(json.contains("\"sampleSize\":100"));
        assertTrue(json.contains("\"methodology\""));
        assertTrue(json.contains("\"disclaimer\""));
        assertTrue(json.contains("\"dimensionRadarScores\""));
        assertTrue(json.contains("\"insightSource\":\"TEMPLATE_FALLBACK\""));
        assertTrue(json.contains("\"stacked_bar\""));
        assertTrue(json.contains("\"renderedPrompt\":\"rendered-probe-001\""));
        assertTrue(json.contains("\"surfaceIntent\":\"seek_clear_answer\""));
        assertTrue(json.contains("\"renderFamily\":\"orthogonal_constrained_llm\""));
        assertTrue(json.contains("\"responseText\":\"response-probe-001\""));
        assertFalse(json.contains("\"defenseDurability\""));
        assertFalse(json.contains("\"clusterBreakdown\""));
        assertFalse(json.contains("\"eeatDimension\""));
        assertFalse(json.contains("\"involvementLevel\""));
        assertFalse(json.contains("\"voiceType\""));
        assertFalse(json.contains("\"mentionRate\":"));
    }

    @Test
    void shouldExposeExecutionSignalsWhenCreativeStagesDegrade() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-creative-degraded",
                null,
                "4.2",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of("农夫"),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = new ArrayList<>(sampleRenderedProbes(blueprints));
        renderedProbes.set(0, new RenderedProbe(
                renderedProbes.getFirst().probeId(),
                renderedProbes.getFirst().bucketType(),
                renderedProbes.getFirst().abstractTags(),
                renderedProbes.getFirst().translatedTags(),
                renderedProbes.getFirst().realizationPlan(),
                renderedProbes.getFirst().exposurePolicy(),
                renderedProbes.getFirst().renderedPrompt(),
                true,
                ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK,
                "orthogonal_fallback_short",
                "budget_guard_fallback",
                renderedProbes.getFirst().triggeredRulesJson()
        ));
        Node3SamplingResult node3Result = sampleNode3Result(renderedProbes);
        Node4ParsingResult node4Result = sampleNode4Result(node3Result.probes());
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights degradedInsights = new CommercialInsights(
                "本次洞察使用了模板降级。",
                sampleInsights().topThreeInsights(),
                "TEMPLATE_FALLBACK",
                V42CreativeDegradationReasons.UPSTREAM_TIMEOUT
        );
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                degradedInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(
                        categoryVariablePool,
                        true,
                        "DETERMINISTIC_FALLBACK",
                        V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED
                ));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any())).thenReturn(node3Result);
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class))).thenReturn(node4Result);
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(degradedInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        V42DiagnosisResult result = orchestrator.execute(request);

        assertEquals(4, result.task().executionSignals().size());
        assertTrue(result.task().executionSignals().stream().anyMatch(signal -> signal.code().equals("NODE0_STATE_SPACE_FALLBACK")));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal -> signal.code().equals("NODE2_RENDER_FALLBACK_PRESENT")));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal -> signal.code().equals("NODE2_RENDER_REPAIR_PRESENT")));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal -> signal.code().equals("NODE5B_INSIGHT_TEMPLATE_FALLBACK")));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE0_STATE_SPACE_FALLBACK")
                        && signal.reasonCodes().equals(List.of(V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED))));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE2_RENDER_FALLBACK_PRESENT")
                        && signal.reasonCodes().contains(V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED)));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE2_RENDER_REPAIR_PRESENT")
                        && signal.reasonCodes().equals(List.of("anchor"))));
        assertTrue(result.task().executionSignals().stream().anyMatch(signal ->
                signal.code().equals("NODE5B_INSIGHT_TEMPLATE_FALLBACK")
                        && signal.reasonCodes().equals(List.of(V42CreativeDegradationReasons.UPSTREAM_TIMEOUT))));
        assertEquals(result.task().executionSignals(), result.report().executionSignals());
    }

    @Test
    void shouldResolveNode3TargetEnginePolicyFromTaskOrder() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "gpt-5-mini",
                Map.of(
                        "samplingEngine", Map.of(
                                "provider", "chatgpt",
                                "modelId", "gpt-5-mini",
                                "disableReasoning", false
                        )
                ),
                Map.of()
        );
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-phase9-chatgpt",
                null,
                "4.2",
                taskOrder,
                sampleContextPackage(),
                List.of(),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        Node3SamplingResult node3Result = sampleNode3Result(renderedProbes);
        Node4ParsingResult node4Result = sampleNode4Result(node3Result.probes());
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights commercialInsights = sampleInsights();
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                commercialInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any())).thenReturn(node3Result);
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class))).thenReturn(node4Result);
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(commercialInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        orchestrator.execute(request);

        ArgumentCaptor<Node3SamplingRequest> node3RequestCaptor = ArgumentCaptor.forClass(Node3SamplingRequest.class);
        verify(node3SamplingGatewayService).dispatch(node3RequestCaptor.capture(), any(), any());
        Node3TargetEnginePolicy targetEnginePolicy = node3RequestCaptor.getValue().targetEnginePolicy();
        assertEquals("chatgpt", targetEnginePolicy.provider());
        assertEquals("gpt-5-mini", targetEnginePolicy.modelId());
        assertFalse(targetEnginePolicy.disableReasoning());
    }

    @Test
    void shouldIgnoreConfiguredBrandSpacePromptBridgeAndUseCanonicalNode2Render() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        List<String> configuredPrompts = List.of(
                "美厨物语和 YWEEL 哪个更值得买？",
                "小户型厨房沥水架怎么选更省心？",
                "想看不锈钢沥水架到底值不值得买？"
        );
        FrontDeskTaskOrder taskOrder = sampleBrandSpaceTaskOrder(configuredPrompts);
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-brand-space-literal",
                null,
                "4.2",
                taskOrder,
                sampleBrandSpaceContextPackage(),
                List.of("美厨"),
                Map.of("YWEEL", List.of("yweel"))
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints().subList(0, configuredPrompts.size());
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights commercialInsights = sampleInsights();
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                commercialInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(any(Map.class), any(String.class)))
                .thenReturn(configuredPrompts.size());
        when(orthogonalMatrixService.generate(categoryVariablePool, configuredPrompts.size())).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any()))
                .thenAnswer(invocation -> sampleNode3Result(invocation.getArgument(0, Node3SamplingRequest.class)));
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class)))
                .thenAnswer(invocation -> sampleNode4Result(invocation.getArgument(0, Node4ParsingRequest.class).probes()));
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(commercialInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        V42DiagnosisResult result = orchestrator.execute(request);

        ArgumentCaptor<Node3SamplingRequest> node3RequestCaptor = ArgumentCaptor.forClass(Node3SamplingRequest.class);
        verify(node3SamplingGatewayService).dispatch(node3RequestCaptor.capture(), any(), any());
        verify(probeRenderingService).renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints));
        verify(probeQualityGateService).evaluate(renderedProbes);
        assertEquals(
                renderedProbes.stream().map(RenderedProbe::renderedPrompt).toList(),
                node3RequestCaptor.getValue().probes().stream().map(Node3ProbeInput::renderedPrompt).toList()
        );
        assertEquals(renderedProbes.getFirst().renderedPrompt(), result.task().probeTrace().getFirst().renderedPrompt());
        assertFalse("brand_space_literal_prompt".equals(result.task().probeTrace().getFirst().renderFamily()));
    }

    @Test
    void shouldUseCanonicalNode2RenderWhenBrandSpaceTaskOrderDeclaresCanonicalRuntime() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        List<String> configuredPrompts = List.of(
                "美厨物语和 YWEEL 哪个更值得买？",
                "小户型厨房沥水架怎么选更省心？",
                "想看不锈钢沥水架到底值不值得买？"
        );
        FrontDeskTaskOrder taskOrder = sampleCanonicalBrandSpaceTaskOrder(configuredPrompts);
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-brand-space-canonical",
                null,
                "4.2",
                taskOrder,
                sampleBrandSpaceContextPackage(),
                List.of("美厨"),
                Map.of("YWEEL", List.of("yweel"))
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints().subList(0, configuredPrompts.size());
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights commercialInsights = sampleInsights();
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                commercialInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(any(Map.class), any(String.class)))
                .thenReturn(configuredPrompts.size());
        when(orthogonalMatrixService.generate(categoryVariablePool, configuredPrompts.size())).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any()))
                .thenAnswer(invocation -> sampleNode3Result(invocation.getArgument(0, Node3SamplingRequest.class)));
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class)))
                .thenAnswer(invocation -> sampleNode4Result(invocation.getArgument(0, Node4ParsingRequest.class).probes()));
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(commercialInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        V42DiagnosisResult result = orchestrator.execute(request);

        ArgumentCaptor<Node3SamplingRequest> node3RequestCaptor = ArgumentCaptor.forClass(Node3SamplingRequest.class);
        verify(node3SamplingGatewayService).dispatch(node3RequestCaptor.capture(), any(), any());
        verify(probeRenderingService).renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints));
        verify(probeQualityGateService).evaluate(renderedProbes);
        assertEquals(
                renderedProbes.stream().map(RenderedProbe::renderedPrompt).toList(),
                node3RequestCaptor.getValue().probes().stream().map(Node3ProbeInput::renderedPrompt).toList()
        );
        assertEquals(renderedProbes.getFirst().renderedPrompt(), result.task().probeTrace().getFirst().renderedPrompt());
        assertEquals(renderedProbes.getFirst().renderFamily(), result.task().probeTrace().getFirst().renderFamily());
        assertFalse("brand_space_literal_prompt".equals(result.task().probeTrace().getFirst().renderFamily()));
    }

    @Test
    void shouldNotFailWhenBrandSpacePromptBridgeCountDriftsBecauseBridgeIsNoLongerExecutableSource() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        FrontDeskTaskOrder taskOrder = sampleBrandSpaceTaskOrder(List.of(
                "美厨物语和 YWEEL 哪个更值得买？",
                "小户型厨房沥水架怎么选更省心？"
        ));
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-brand-space-mismatch",
                null,
                "4.2",
                taskOrder,
                sampleBrandSpaceContextPackage(),
                List.of("美厨"),
                Map.of("YWEEL", List.of("yweel"))
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints().subList(0, 3);
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        Node5AKpiResult node5AResult = new Node5AKpiResult(sampleSummary());
        CommercialInsights commercialInsights = sampleInsights();
        Node5CDashboardResult node5CResult = new Node5CDashboardResult(
                request.brandName(),
                node5AResult.kpiSummary(),
                commercialInsights,
                sampleDefenseCompleteness(),
                sampleCharts()
        );

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(any(Map.class), any(String.class)))
                .thenReturn(2);
        when(orthogonalMatrixService.generate(categoryVariablePool, 2)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any()))
                .thenAnswer(invocation -> sampleNode3Result(invocation.getArgument(0, Node3SamplingRequest.class)));
        when(brandDetectionChain.parse(any(Node4ParsingRequest.class)))
                .thenAnswer(invocation -> sampleNode4Result(invocation.getArgument(0, Node4ParsingRequest.class).probes()));
        when(v42KpiAggregationService.aggregate(any(Node5AKpiRequest.class))).thenReturn(node5AResult);
        when(insightGenerationService.generate(request.taskId(), node5AResult)).thenReturn(commercialInsights);
        when(dashboardDataAggregator.aggregate(any(Node5CDashboardRequest.class))).thenReturn(node5CResult);

        V42DiagnosisResult result = orchestrator.execute(request);

        assertEquals(renderedProbes.getFirst().renderedPrompt(), result.task().probeTrace().getFirst().renderedPrompt());
        verify(probeRenderingService).renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints));
        verify(node3SamplingGatewayService).dispatch(any(Node3SamplingRequest.class), any(), any());
    }

    @Test
    void shouldAbortBeforeNode3WhenProbeQualityGateFails() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-quality-gate-001",
                null,
                "4.2",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of("农夫"),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes))
                .thenReturn(failedGateResult("duplicated prompts"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> orchestrator.execute(request));
        assertTrue(exception.getMessage().contains("probe quality gate failed"));
        verify(probeQualityGateService).evaluate(renderedProbes);
        org.mockito.Mockito.verifyNoInteractions(node3SamplingGatewayService, brandDetectionChain,
                v42KpiAggregationService, insightGenerationService, dashboardDataAggregator);
    }

    @Test
    void shouldAbortWhenNode3ProducesZeroSuccessfulSamples() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-node3-zero-success",
                null,
                "4.2",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of("农夫"),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any()))
                .thenReturn(sampleFullyFailedNode3Result(renderedProbes));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> orchestrator.execute(request));
        assertTrue(exception.getMessage().contains("未产出任何成功样本"));
        verify(brandDetectionChain, never()).parse(any(Node4ParsingRequest.class));
        verify(v42KpiAggregationService, never()).aggregate(any(Node5AKpiRequest.class));
    }

    @Test
    void shouldAbortWhenNode3SuccessRateIsBelowQualityThreshold() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-node3-low-success-rate",
                null,
                "4.2",
                sampleTaskOrder(),
                sampleContextPackage(),
                List.of("农夫"),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);

        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());
        when(node3SamplingGatewayService.dispatch(any(Node3SamplingRequest.class), any(), any()))
                .thenReturn(sampleLowSuccessNode3Result(renderedProbes));

        Node3SamplingViabilityException exception = assertThrows(
                Node3SamplingViabilityException.class,
                () -> orchestrator.execute(request)
        );
        assertTrue(exception.getMessage().contains("成功样本率低于质量阈值"));
        assertTrue(exception.getMessage().contains("success=1/100"));
        assertTrue(exception.getMessage().contains("minSuccessRate=0.9000"));
        assertEquals(1L, exception.taskSnapshot().successfulProbes());
        assertEquals(99L, exception.taskSnapshot().failedProbes());
        verify(brandDetectionChain, never()).parse(any(Node4ParsingRequest.class));
        verify(v42KpiAggregationService, never()).aggregate(any(Node5AKpiRequest.class));
    }

    @Test
    void shouldRejectMixedRealtimeSearchHintFormats() {
        V42DiagnosisOrchestrator orchestrator = newOrchestrator();
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "饮用水",
                List.of("怡宝"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of(
                        "samplingEngine", Map.of("provider", "doubao"),
                        "samplingRealtimeSearch", Map.of("enabled", true, "provider", "doubao"),
                        "samplingRealtimeSearch.enabled", false
                ),
                Map.of()
        );
        EngineTaskContextPackage contextPackage = sampleContextPackage(
                List.of("怡宝"),
                List.of("矿泉水", "天然水"),
                false
        );
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-hints-001",
                null,
                "4.2",
                taskOrder,
                contextPackage,
                List.of("农夫"),
                Map.of()
        );

        CategoryVariablePool categoryVariablePool = samplePool();
        List<ProbeBlueprint> blueprints = sampleBlueprints();
        List<RenderedProbe> renderedProbes = sampleRenderedProbes(blueprints);
        when(cssmHiStateSpaceService.generate(eq(request.taskId()), any(CssmHiStateSpaceRequest.class)))
                .thenReturn(new CssmHiStateSpaceResult(categoryVariablePool, false, "LLM"));
        when(orthogonalMatrixService.generate(categoryVariablePool, 100)).thenReturn(blueprints);
        when(probeRenderingService.renderAll(eq(request.taskId()), eq(categoryVariablePool), anyList(), eq(blueprints)))
                .thenReturn(renderedProbes);
        when(probeQualityGateService.evaluate(renderedProbes)).thenReturn(passGateResult());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.execute(request)
        );
        assertTrue(exception.getMessage().contains("samplingRealtimeSearch"));
        org.mockito.Mockito.verifyNoInteractions(node3SamplingGatewayService);
    }

    private V42DiagnosisOrchestrator newOrchestrator() {
        when(v42TargetProbeCountPolicy.defaultTargetProbeCount()).thenReturn(100);
        when(v42TargetProbeCountPolicy.normalizeRequestedValue(any(Integer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Integer.class));
        when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(any(Map.class), any(String.class))).thenReturn(100);
        V42TaskOrderExecutionPolicyResolver taskOrderExecutionPolicyResolver = new V42TaskOrderExecutionPolicyResolver(
                v42TargetProbeCountPolicy,
                new V42RealtimeSearchPolicyResolver()
        );
        V42Node3TargetEnginePolicyResolver targetEnginePolicyResolver = new V42Node3TargetEnginePolicyResolver();
        return new V42DiagnosisOrchestrator(
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
                taskOrderExecutionPolicyResolver,
                targetEnginePolicyResolver,
                fixedClock,
                () -> "rpt-phase9-001"
        );
    }

    private CategoryVariablePool samplePool() {
        return new CategoryVariablePool(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "天然水", "饮用水"),
                dimension("D1", "D1 认知异常", List.of("initiation", "exploration", "formulation", "verification")),
                dimension("D2", "D2 目的论目标", List.of("informational_directed", "informational_undirected", "instructional_active", "resource_acquisition")),
                dimension("D3", "D3 价值论驱动", List.of("subsistence_protection", "affection_belonging_participation", "understanding_competence", "identity_freedom", "creation_idleness")),
                dimension("D4", "D4 认知负荷", List.of("severely_depleted", "moderate_strain", "optimal")),
                dimension("D5", "D5 时间约束", List.of("acute_emergency", "anticipatory_constraint", "reflective")),
                dimension("D6", "D6 交互模态", List.of("specification", "recognition", "dialogic"))
        );
    }

    private DimensionVariables dimension(String dimensionId, String dimensionName, List<String> abstractCodes) {
        List<String> variables = abstractCodes.stream().map(code -> dimensionId + "-" + code).toList();
        return new DimensionVariables(dimensionId, dimensionName, abstractCodes, variables);
    }

    private FrontDeskTaskOrder sampleTaskOrder() {
        return new FrontDeskTaskOrder(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of(
                        "sampling", Map.of("targetProbeCount", 100),
                        "samplingEngine", Map.of("provider", "doubao")
                ),
                Map.of("source", "orchestrator_test")
        );
    }

    private FrontDeskTaskOrder sampleBrandSpaceTaskOrder(List<String> enabledPromptTexts) {
        return sampleBrandSpaceTaskOrder(enabledPromptTexts, Map.of("source", "brand_space_create"));
    }

    private FrontDeskTaskOrder sampleCanonicalBrandSpaceTaskOrder(List<String> enabledPromptTexts) {
        return sampleBrandSpaceTaskOrder(
                enabledPromptTexts,
                Map.of(
                        "source", "brand_space_create",
                        "lightConeRunVersionTruth", LightConeV43VersionTruthSupport.canonicalRunVersionTruth()
                )
        );
    }

    private FrontDeskTaskOrder sampleBrandSpaceTaskOrder(List<String> enabledPromptTexts,
                                                         Map<String, Object> taskMetadata) {
        return new FrontDeskTaskOrder(
                "美厨物语",
                "厨房置物架",
                List.of("YWEEL"),
                "https://www.meichuwuyu.example.com",
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of(
                        "sampling", Map.of("targetProbeCount", enabledPromptTexts.size()),
                        "samplingEngine", Map.of("provider", "doubao"),
                        "brandSpacePromptSnapshot", Map.of(
                                "adapterVersion", "brand_space_p0",
                                "enabledTopicNames", List.of("品牌选择", "场景适配", "材质判断"),
                                "enabledPromptTexts", enabledPromptTexts
                        )
                ),
                taskMetadata
        );
    }

    private EngineTaskContextPackage sampleBrandSpaceContextPackage() {
        return new EngineTaskContextPackage(
                "美厨物语",
                "厨房置物架",
                List.of("YWEEL"),
                List.of("厨房沥水架", "厨房置物架"),
                List.of(
                        new CategoryAnchorSignal("厨房沥水架", "front_desk_task_order", 1.0d),
                        new CategoryAnchorSignal("厨房置物架", "front_desk_task_order", 1.0d)
                ),
                false,
                "厨房收纳品牌",
                List.of("小户型厨房"),
                List.of("租房厨房收纳"),
                List.of("YWEEL"),
                List.of("偏好先比较再决定"),
                List.of("允许一句话搜索式提问"),
                List.of(),
                List.of("brandBrief", "categorySceneMap"),
                Map.of("categoryAnchorSource", "front_desk_task_order")
        );
    }

    private EngineTaskContextPackage sampleContextPackage() {
        return sampleContextPackage(List.of("怡宝", "百岁山"), List.of("矿泉水", "天然水", "饮用水"), false);
    }

    private EngineTaskContextPackage sampleContextPackage(List<String> competitors,
                                                          List<String> anchors,
                                                          boolean fallbackAnchorMode) {
        String anchorSource = fallbackAnchorMode ? "fallback_category_resolver" : "front_desk_task_order";
        return new EngineTaskContextPackage(
                "农夫山泉",
                "饮用水",
                competitors,
                anchors,
                anchors.stream().map(anchor -> new CategoryAnchorSignal(anchor, anchorSource, 1.0d)).toList(),
                fallbackAnchorMode,
                null,
                List.of(),
                List.of(),
                competitors,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "brandBrief",
                        "categorySceneMap",
                        "descriptiveEntrypoints",
                        "personaExpressionTendencies",
                        "targetModelContextSignals"
                ),
                Map.of("categoryAnchorSource", anchorSource)
        );
    }

    private List<ProbeBlueprint> sampleBlueprints() {
        List<ProbeBlueprint> blueprints = new ArrayList<>(100);
        for (int index = 1; index <= 100; index++) {
            BucketType bucketType = index <= 50
                    ? BucketType.ORTHOGONAL
                    : index <= 80 ? BucketType.PARETO : BucketType.ADVERSARIAL;
            blueprints.add(new ProbeBlueprint(
                    "probe-%03d".formatted(index),
                    bucketType,
                    abstractTags(index),
                    translatedTags(index),
                    bucketType == BucketType.ORTHOGONAL ? null : "template-" + index,
                    ProbeStatus.PENDING,
                    realizationPlan(bucketType, abstractTags(index), translatedTags(index), bucketType == BucketType.ORTHOGONAL ? null : "template-" + index)
            ));
        }
        return List.copyOf(blueprints);
    }

    private List<RenderedProbe> sampleRenderedProbes(List<ProbeBlueprint> blueprints) {
        return blueprints.stream()
                .map(blueprint -> new RenderedProbe(
                        blueprint.probeId(),
                        blueprint.bucketType(),
                        blueprint.abstractTags(),
                        blueprint.translatedTags(),
                        blueprint.realizationPlan(),
                        blueprint.bucketType() == BucketType.ORTHOGONAL
                                ? ProbeExposurePolicy.BRAND_HIDDEN
                                : ProbeExposurePolicy.BRAND_VISIBLE,
                        "rendered-" + blueprint.probeId(),
                        false,
                        blueprint.bucketType() == BucketType.ORTHOGONAL ? "LLM" : "TEMPLATE",
                        blueprint.bucketType() == BucketType.ORTHOGONAL ? "orthogonal_constrained_llm" : bucketTemplateFamily(blueprint.bucketType()),
                        blueprint.bucketType() == BucketType.ORTHOGONAL ? "llm_validated" : "template_rendered",
                        blueprint.bucketType() == BucketType.ORTHOGONAL
                                ? Map.of("validationRepairApplied", "anchor")
                                : Map.of()
                ))
                .toList();
    }

    private ProbeQualityGateService.ProbeQualityGateResult passGateResult() {
        return new ProbeQualityGateService.ProbeQualityGateResult(true, List.of(), Map.of());
    }

    private ProbeQualityGateService.ProbeQualityGateResult failedGateResult(String violation) {
        return new ProbeQualityGateService.ProbeQualityGateResult(false, List.of(violation), Map.of());
    }

    private String bucketTemplateFamily(BucketType bucketType) {
        return bucketType == BucketType.PARETO ? "pareto_template" : "adversarial_template";
    }

    private RealizationPlan realizationPlan(BucketType bucketType,
                                            Map<String, String> abstractTags,
                                            Map<String, String> translatedTags,
                                            String templateText) {
        return planningService.plan(bucketType, abstractTags, translatedTags, templateText);
    }

    private Node3SamplingResult sampleNode3Result(List<RenderedProbe> renderedProbes) {
        List<Node3ProbeOutput> outputs = new ArrayList<>(renderedProbes.size());
        for (int index = 0; index < renderedProbes.size(); index++) {
            RenderedProbe renderedProbe = renderedProbes.get(index);
            if (index >= 98) {
                outputs.add(Node3ProbeOutput.failed(
                        com.geoprobe.dto.engine.v42.Node3ProbeInput.fromRenderedProbe(renderedProbe),
                        RawResponse.empty(1200L),
                        "model timeout"
                ));
                continue;
            }
            outputs.add(Node3ProbeOutput.success(
                    com.geoprobe.dto.engine.v42.Node3ProbeInput.fromRenderedProbe(renderedProbe),
                    new RawResponse("response-" + renderedProbe.probeId(), "", List.of(), 60, 180, 240, 900L, false)
            ));
        }
        return new Node3SamplingResult(outputs);
    }

    private Node3SamplingResult sampleNode3Result(Node3SamplingRequest request) {
        List<Node3ProbeOutput> outputs = new ArrayList<>(request.probes().size());
        for (Node3ProbeInput probe : request.probes()) {
            outputs.add(Node3ProbeOutput.success(
                    probe,
                    new RawResponse("response-" + probe.probeId(), "", List.of(), 60, 180, 240, 900L, false)
            ));
        }
        return new Node3SamplingResult(outputs);
    }

    private Node3SamplingResult sampleFullyFailedNode3Result(List<RenderedProbe> renderedProbes) {
        List<Node3ProbeOutput> outputs = new ArrayList<>(renderedProbes.size());
        for (RenderedProbe renderedProbe : renderedProbes) {
            outputs.add(Node3ProbeOutput.failed(
                    com.geoprobe.dto.engine.v42.Node3ProbeInput.fromRenderedProbe(renderedProbe),
                    RawResponse.empty(1200L),
                    "TARGET_PROVIDER_UNCONFIGURED"
            ));
        }
        return new Node3SamplingResult(outputs);
    }

    private Node3SamplingResult sampleLowSuccessNode3Result(List<RenderedProbe> renderedProbes) {
        List<Node3ProbeOutput> outputs = new ArrayList<>(renderedProbes.size());
        for (int index = 0; index < renderedProbes.size(); index++) {
            RenderedProbe renderedProbe = renderedProbes.get(index);
            if (index == 0) {
                outputs.add(Node3ProbeOutput.success(
                        com.geoprobe.dto.engine.v42.Node3ProbeInput.fromRenderedProbe(renderedProbe),
                        new RawResponse("response-" + renderedProbe.probeId(), "", List.of(), 10, 20, 30, 900L, false)
                ));
                continue;
            }
            outputs.add(Node3ProbeOutput.failed(
                    com.geoprobe.dto.engine.v42.Node3ProbeInput.fromRenderedProbe(renderedProbe),
                    RawResponse.empty(1200L),
                    "EMPTY_FINAL_RESPONSE"
            ));
        }
        return new Node3SamplingResult(outputs);
    }

    private Node4ParsingResult sampleNode4Result(List<Node3ProbeOutput> node3Outputs) {
        List<Node4ProbeOutput> outputs = new ArrayList<>(node3Outputs.size());
        for (int index = 0; index < node3Outputs.size(); index++) {
            Node3ProbeOutput node3Output = node3Outputs.get(index);
            ProbeType probeType = node3Output.bucketType() == BucketType.ORTHOGONAL
                    ? ProbeType.OPEN_DISCOVERY
                    : ProbeType.BRAND_ASSERTIVE;
            BrandDetectionResult detectionResult = node3Output.dispatchStatus() == ProbeDispatchStatus.SUCCESS
                    ? BrandDetectionResult.detected(
                    "L1_EXACT",
                    index % 3 == 0 ? "top1_recommendation" : "passing_mention",
                    index % 4 == 0 ? "positive" : "neutral",
                    0.82d,
                    probeType,
                    180,
                    index % 3 == 0 ? "Long" : index % 3 == 1 ? "Short" : "Medium",
                    index % 5 == 0 ? List.of(new CompetitorMention("怡宝", "L1_EXACT", "positive", 0.61d)) : List.of(),
                    List.of(),
                    ""
            )
                    : BrandDetectionResult.noMention(
                    probeType,
                    0,
                    "Short",
                    List.of(),
                    List.of(),
                    ""
            );
            outputs.add(Node4ProbeOutput.fromNode3(node3Output, detectionResult));
        }
        return new Node4ParsingResult(outputs);
    }

    private KpiSummary sampleSummary() {
        LinkedHashMap<String, Double> dimensionPenetration = new LinkedHashMap<>();
        dimensionPenetration.put("D1_verification", 0.4600d);
        dimensionPenetration.put("D2_resource_acquisition", 0.5200d);
        dimensionPenetration.put("D3_subsistence_protection", 0.4100d);
        dimensionPenetration.put("D4_optimal", 0.5500d);
        dimensionPenetration.put("D5_acute_emergency", 0.3900d);
        dimensionPenetration.put("D6_specification", 0.4400d);

        LinkedHashMap<String, Double> dimensionRadarScores = new LinkedHashMap<>();
        dimensionRadarScores.put("D1", 0.4600d);
        dimensionRadarScores.put("D2", 0.5200d);
        dimensionRadarScores.put("D3", 0.4100d);
        dimensionRadarScores.put("D4", 0.5500d);
        dimensionRadarScores.put("D5", 0.3900d);
        dimensionRadarScores.put("D6", 0.4400d);

        LinkedHashMap<String, CompetitorKpi> competitorKpis = new LinkedHashMap<>();
        competitorKpis.put("怡宝", new CompetitorKpi("怡宝", 28, 0.2857d, 0.6429d, 0.6100d));

        return new KpiSummary(
                0.4600d,
                0.7800d,
                0.5900d,
                new LengthStratifiedKpi(
                        new LengthBucketKpi("Short", 32, 18, 14, 0.5000d, 0.7857d, 1.1250d),
                        new LengthBucketKpi("Medium", 33, 16, 17, 0.4375d, 0.7647d, 1.0909d),
                        new LengthBucketKpi("Long", 33, 16, 17, 0.4375d, 0.8235d, 1.1515d)
                ),
                0.3400d,
                0.5100d,
                73.5000d,
                new BucketKpi(BucketType.ORTHOGONAL, 50, 49, 49, 0, 0.4600d, 0.0000d, 0.4600d, 0.2800d, 0.4800d),
                new BucketKpi(BucketType.PARETO, 30, 29, 0, 29, 0.0000d, 0.7931d, 0.7931d, 0.4138d, 0.5517d),
                new BucketKpi(BucketType.ADVERSARIAL, 20, 20, 0, 20, 0.0000d, 0.8000d, 0.8000d, 0.3500d, 0.5000d),
                Map.copyOf(dimensionPenetration),
                Map.copyOf(dimensionRadarScores),
                Map.copyOf(competitorKpis),
                98
        );
    }

    private CommercialInsights sampleInsights() {
        return new CommercialInsights(
                "openDiscovery 场景仍有稳定露出断层",
                List.of(
                        new InsightItem("D4 认知负荷成为首要失血点", "evidence 46.00%", "必须立即补强 optimal 负荷场景的品牌首句"),
                        new InsightItem("Pareto 桶里确认式响应率高于自然提及率", "evidence 79.31%", "必须把品牌证据前置到模板前两句"),
                        new InsightItem("竞品怡宝正在挤占比较池", "evidence 28.57%", "必须针对比较问法重写推荐理由")
                ),
                "TEMPLATE_FALLBACK"
        );
    }

    private DefenseCompletenessSummary sampleDefenseCompleteness() {
        return new DefenseCompletenessSummary(
                0.4200d,
                2,
                List.of(
                        new DefenseCompletenessResult("probe-081", 1.0d, 0.5d, 0.5d, 0.7000d),
                        DefenseCompletenessResult.missing("probe-082")
                )
        );
    }

    private DashboardCharts sampleCharts() {
        return new DashboardCharts(
                new DashboardCharts.RadarChartData(
                        "radar",
                        List.of("D1 认知异常", "D2 目的论目标", "D3 价值论驱动", "D4 认知负荷", "D5 时间约束", "D6 交互模态"),
                        "农夫山泉",
                        List.of(0.4600d, 0.5200d, 0.4100d, 0.5500d, 0.3900d, 0.4400d),
                        Map.of("怡宝", List.of(0.3000d, 0.3300d, 0.2900d, 0.3100d, 0.2800d, 0.2600d))
                ),
                new DashboardCharts.ScatterChartData(
                        "scatter",
                        "brandDistance",
                        "recommendRate",
                        List.of(
                                new DashboardCharts.ScatterPoint("农夫山泉", 0.4100d, 0.3400d, true, 0.5900d, 58),
                                new DashboardCharts.ScatterPoint("怡宝", 0.7143d, 0.2857d, false, 0.2857d, 28)
                        )
                ),
                new DashboardCharts.SankeyChartData(
                        "sankey",
                        List.of(
                                new DashboardCharts.SankeyNode("intent:想买水", "想买水", "intent"),
                                new DashboardCharts.SankeyNode("candidate:农夫山泉", "农夫山泉", "candidate"),
                                new DashboardCharts.SankeyNode("recommend:农夫山泉", "农夫山泉", "recommend")
                        ),
                        List.of(
                                new DashboardCharts.SankeyLink("intent:想买水", "candidate:农夫山泉", 58),
                                new DashboardCharts.SankeyLink("candidate:农夫山泉", "recommend:农夫山泉", 34)
                        )
                ),
                new DashboardCharts.StackedBarChartData(
                        "stacked_bar",
                        List.of("spontaneousMentionRate", "confirmationResponseRate", "overallMentionRate", "recommendRate", "positiveRate"),
                        List.of(
                                new DashboardCharts.MetricSeries("ORTHOGONAL", "正交桶", List.of(0.4600d, 0.0000d, 0.4600d, 0.2800d, 0.4800d), 49),
                                new DashboardCharts.MetricSeries("PARETO", "帕累托桶", List.of(0.0000d, 0.7931d, 0.7931d, 0.4138d, 0.5517d), 29),
                                new DashboardCharts.MetricSeries("ADVERSARIAL", "对抗桶", List.of(0.0000d, 0.8000d, 0.8000d, 0.3500d, 0.5000d), 20)
                        )
                )
        );
    }

    private Map<String, String> abstractTags(int index) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", index % 2 == 0 ? "verification" : "exploration");
        tags.put("D2", index % 3 == 0 ? "resource_acquisition" : "informational_directed");
        tags.put("D3", index % 4 == 0 ? "subsistence_protection" : "understanding_competence");
        tags.put("D4", index % 5 == 0 ? "optimal" : "moderate_strain");
        tags.put("D5", index % 2 == 0 ? "acute_emergency" : "reflective");
        tags.put("D6", index % 3 == 0 ? "specification" : "recognition");
        return Map.copyOf(tags);
    }

    private Map<String, String> translatedTags(int index) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "状态-" + index + "-1");
        tags.put("D2", "状态-" + index + "-2");
        tags.put("D3", "状态-" + index + "-3");
        tags.put("D4", "状态-" + index + "-4");
        tags.put("D5", "状态-" + index + "-5");
        tags.put("D6", "状态-" + index + "-6");
        return Map.copyOf(tags);
    }
}
