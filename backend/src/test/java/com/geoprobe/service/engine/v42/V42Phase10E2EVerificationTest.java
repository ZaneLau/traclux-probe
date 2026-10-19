package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.ai.v42.CommercialInsightAiService;
import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.ai.v42.DefenseCompletenessAiService;
import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.engine.v42.V42DiagnosisRequest;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42Phase10E2EVerificationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-26T12:00:00Z"), ZoneOffset.UTC);
    private static final String FIXED_REPORT_ID = "rpt_phase10_v42_e2e";
    private static final Path ARTIFACT_DIR = Path.of("target", "v42-phase10");
    private static final List<String> EXPECTED_STAGE_ORDER = List.of(
            "REQUEST_CONTEXT",
            "NODE0_STATE_SPACE",
            "NODE1_BLUEPRINTS",
            "NODE2_RENDERING",
            "QUALITY_GATE",
            "NODE3_SAMPLING",
            "NODE4_PARSING",
            "NODE5A_KPI",
            "NODE5B_INSIGHTS",
            "NODE5C_DASHBOARD",
            "REPORT_ASSEMBLY"
    );

    @Test
    void shouldKeepMainlineResultStableWhenObserverEnabled() throws Exception {
        V42DiagnosisRequest request = sampleRequest();

        Fixture plainFixture = newFixture();
        V42DiagnosisResult plainResult = plainFixture.orchestrator().execute(request);

        Fixture observedFixture = newFixture();
        RecordingExecutionObserver observer = new RecordingExecutionObserver();
        V42DiagnosisResult observedResult = observedFixture.orchestrator().execute(request, null, observer);

        assertEquals(plainResult, observedResult);
        assertRequiredFieldsPresent(observedResult);
        assertStageOrder(observer);
        assertObserverSummaries(observer, observedFixture.recordingModelAdapter().recordedInputs());

        writeArtifacts(request, observedResult, observer);
    }

    @Test
    void shouldIgnoreObserverFailures() {
        V42DiagnosisRequest request = sampleRequest();

        Fixture plainFixture = newFixture();
        V42DiagnosisResult plainResult = plainFixture.orchestrator().execute(request);

        Fixture observedFixture = newFixture();
        V42ExecutionObserver throwingObserver = new V42ExecutionObserver() {
            @Override
            public void onStageCompleted(StageTrace trace) {
                if ("NODE2_RENDERING".equals(trace.stage())) {
                    throw new IllegalStateException("observer should not break mainline");
                }
            }
        };

        V42DiagnosisResult observedResult = observedFixture.orchestrator().execute(request, null, throwingObserver);
        assertEquals(plainResult, observedResult);
    }

    private void assertRequiredFieldsPresent(V42DiagnosisResult result) {
        assertEquals("4.2", result.report().engineVersion());
        assertEquals(100, result.report().sampleSize());
        assertNotNull(result.report().kpiSummary().spontaneousMentionRate());
        assertNotNull(result.report().kpiSummary().confirmationResponseRate());
        assertNotNull(result.report().kpiSummary().overallMentionRate());
        assertEquals(100, result.task().probeTrace().size());
        assertEquals(100L, result.task().successfulProbes());
        assertEquals(0L, result.task().failedProbes());
        assertEquals("TEMPLATE_FALLBACK", result.report().commercialInsights().insightSource());
        assertNotNull(result.report().dashboardCharts().radar());
        assertNotNull(result.report().dashboardCharts().scatter());
        assertNotNull(result.report().dashboardCharts().sankey());
        assertNotNull(result.report().dashboardCharts().stackedBar());
        assertTrue(result.report().kpiSummary().spontaneousMentionRate() >= 0.30d);
        assertTrue(result.report().kpiSummary().confirmationResponseRate() >= 0.70d);
        assertTrue(result.report().kpiSummary().positiveRate() >= 0.50d);
        assertTrue(result.report().defenseCompleteness().averageCompositeScore() >= 0.40d);
    }

    private void assertStageOrder(RecordingExecutionObserver observer) {
        assertIterableEquals(EXPECTED_STAGE_ORDER, observer.stageNames());
        assertTrue(observer.failure() == null, "observer should not record failure on success path");
    }

    private void assertObserverSummaries(RecordingExecutionObserver observer,
                                         List<Node3ProbeInput> recordedInputs) {
        Map<String, Object> requestContext = observer.summary("REQUEST_CONTEXT");
        assertEquals("农夫山泉", requestContext.get("brandName"));
        assertEquals("饮用水", requestContext.get("category"));
        assertEquals(2, requestContext.get("competitorCount"));

        Map<String, Object> node1 = observer.summary("NODE1_BLUEPRINTS");
        assertEquals(100, node1.get("selectedBlueprintCount"));
        assertEquals(100, node1.get("requestedProbeCount"));
        assertNotNull(node1.get("bucketCounts"));

        Map<String, Object> node2 = observer.summary("NODE2_RENDERING");
        assertEquals(100, node2.get("probeCount"));
        assertTrue(node2.containsKey("duplicatePromptCount"));

        Map<String, Object> qualityGate = observer.summary("QUALITY_GATE");
        assertEquals(true, qualityGate.get("passed"));

        Map<String, Object> node3 = observer.summary("NODE3_SAMPLING");
        assertEquals("doubao", node3.get("provider"));
        assertEquals(false, node3.get("searchEnabled"));
        assertEquals(100, recordedInputs.size());

        Map<String, Object> report = observer.summary("REPORT_ASSEMBLY");
        assertEquals(100, report.get("sampleSize"));
        assertEquals("doubao", report.get("modelProvider"));
        assertTrue(((Number) report.get("totalDurationMillis")).longValue() >= 0L);
    }

    private void writeArtifacts(V42DiagnosisRequest request,
                                V42DiagnosisResult result,
                                RecordingExecutionObserver observer) throws IOException {
        Files.createDirectories(ARTIFACT_DIR);
        Files.writeString(
                ARTIFACT_DIR.resolve("input.json"),
                prettyJson(request),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                ARTIFACT_DIR.resolve("output.json"),
                prettyJson(result),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                ARTIFACT_DIR.resolve("execution-trace.json"),
                prettyJson(observer.stageTraces()),
                StandardCharsets.UTF_8
        );
    }

    private String prettyJson(Object value) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private Fixture newFixture() {
        CssmHiStateSpaceAiService stateSpaceAiService = (category, d1, d2, d3, d4, d5, d6) -> stateSpaceJson();
        ProbeRenderingAiService probeRenderingAiService = (brandName, category, competitors, categoryAnchors, brandInjectionPolicy, mustLines, shouldLines, contextLines, promptInstructions) ->
                renderPrompt(brandName, categoryAnchors, brandInjectionPolicy, mustLines, shouldLines, promptInstructions);
        BrandDetectionL3AiService brandDetectionL3AiService = (brandName, brandAliases, competitors, responseText) -> {
            if (responseText != null && responseText.contains("天然水老大")) {
                return "{\"mentioned\":true,\"position\":\"top1_recommendation\",\"sentiment\":\"positive\",\"strength\":0.76}";
            }
            return "{\"mentioned\":false,\"position\":null,\"sentiment\":null,\"strength\":0.0}";
        };
        CommercialInsightAiService commercialInsightAiService = new CommercialInsightAiService() {
            @Override
            public String generateInsights(String summaryJson, String focusPoints) {
                throw new IllegalStateException("phase10 deterministic insight fallback");
            }

            @Override
            public String repairInsights(String summaryJson, String focusPoints, String invalidOutput, String validationError) {
                throw new IllegalStateException("phase10 deterministic insight repair fallback");
            }
        };
        DefenseCompletenessAiService defenseCompletenessAiService = (brandName, probeText, responseText) ->
                "{\"evidence\":1,\"comparison\":0.5,\"conclusion\":1}";

        CssmHiStateSpaceService cssmHiStateSpaceService = new CssmHiStateSpaceService(stateSpaceAiService, OBJECT_MAPPER);
        OrthogonalMatrixService orthogonalMatrixService = new OrthogonalMatrixService();
        ThreeLayerMappingEvaluator mappingEvaluator = new ThreeLayerMappingEvaluator();
        ProbeRenderingService probeRenderingService = new ProbeRenderingService(probeRenderingAiService, mappingEvaluator);
        ProbeQualityGateService probeQualityGateService = Mockito.mock(ProbeQualityGateService.class);
        Mockito.when(probeQualityGateService.evaluate(Mockito.anyList()))
                .thenReturn(new ProbeQualityGateService.ProbeQualityGateResult(true, List.of(), Map.of()));
        RecordingModelAdapter recordingModelAdapter = new RecordingModelAdapter();
        V42Node3SamplingGatewayService node3SamplingGatewayService = new V42Node3SamplingGatewayService(recordingModelAdapter, millis -> {
        });
        AliasMatcher aliasMatcher = new AliasMatcher();
        BrandDetectionChain brandDetectionChain = new BrandDetectionChain(brandDetectionL3AiService, aliasMatcher, OBJECT_MAPPER);
        V42StabilityService stabilityService = new V42StabilityService();
        V42KpiAggregationService kpiAggregationService = new V42KpiAggregationService(stabilityService);
        InsightGenerationService insightGenerationService = new InsightGenerationService(commercialInsightAiService, OBJECT_MAPPER);
        DefenseCompletenessJudge defenseCompletenessJudge = new DefenseCompletenessJudge(defenseCompletenessAiService, OBJECT_MAPPER);
        DashboardDataAggregator dashboardDataAggregator = new DashboardDataAggregator(defenseCompletenessJudge);
        V42DiagnosisReportAssembler diagnosisReportAssembler = new V42DiagnosisReportAssembler();
        V42TargetProbeCountPolicy v42TargetProbeCountPolicy = Mockito.mock(V42TargetProbeCountPolicy.class);
        Mockito.when(v42TargetProbeCountPolicy.defaultTargetProbeCount()).thenReturn(100);
        Mockito.when(v42TargetProbeCountPolicy.normalizeRequestedValue(Mockito.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0, Integer.class));
        Mockito.when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(Mockito.anyMap(), Mockito.anyString())).thenReturn(100);
        V42TaskOrderExecutionPolicyResolver taskOrderExecutionPolicyResolver = new V42TaskOrderExecutionPolicyResolver(
                v42TargetProbeCountPolicy,
                new V42RealtimeSearchPolicyResolver()
        );
        V42Node3TargetEnginePolicyResolver targetEnginePolicyResolver = new V42Node3TargetEnginePolicyResolver();
        V42DiagnosisOrchestrator orchestrator = new V42DiagnosisOrchestrator(
                cssmHiStateSpaceService,
                orthogonalMatrixService,
                probeRenderingService,
                probeQualityGateService,
                node3SamplingGatewayService,
                brandDetectionChain,
                kpiAggregationService,
                insightGenerationService,
                dashboardDataAggregator,
                diagnosisReportAssembler,
                taskOrderExecutionPolicyResolver,
                targetEnginePolicyResolver,
                FIXED_CLOCK,
                () -> FIXED_REPORT_ID
        );

        return new Fixture(orchestrator, recordingModelAdapter);
    }

    private String stateSpaceJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "d1CognitiveAnomaly", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D1"),
                    "d2TeleologicalGoal", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D2"),
                    "d3AxiologicalDrive", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D3"),
                    "d4CognitiveLoad", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D4"),
                    "d5TimeConstraint", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D5"),
                    "d6InteractionModality", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D6")
            ));
        } catch (IOException ex) {
            throw new IllegalStateException("phase10 state space stub 序列化失败", ex);
        }
    }

    private String renderPrompt(String brandName,
                                String categoryAnchors,
                                String brandInjectionPolicy,
                                String mustLines,
                                String shouldLines,
                                String promptInstructions) {
        String anchor = categoryAnchors.split(" / ")[0].trim();
        String opener = resolvePromptOpener(shouldLines, promptInstructions);
        String d2Cue = resolveD2Cue(mustLines);
        String d3Cue = resolveD3Cue(mustLines);
        String styleCue = resolveStyleCue(mustLines, shouldLines, promptInstructions);
        if (brandInjectionPolicy.contains("允许")) {
            return "%s%s这款%s%s？%s，也想%s。"
                    .formatted(opener, brandName, anchor, d2Cue, styleCue, d3Cue);
        }
        return "%s%s%s？%s，也想%s。"
                .formatted(opener, anchor, d2Cue, styleCue, d3Cue);
    }

    private String resolvePromptOpener(String shouldLines, String promptInstructions) {
        String d1Value = extractSoftHintValue(shouldLines, "D1");
        String d5Value = extractSoftHintValue(shouldLines, "D5");
        String d6Value = extractSoftHintValue(shouldLines, "D6");
        if ("马上就得用".equals(d5Value)) {
            return "我现在就要定下来，";
        }
        if ("就想确认一下".equals(d1Value)) {
            return "我主要想再确认一遍，";
        }
        if ("完全不懂怎么选".equals(d1Value)) {
            return "我还没摸清门道，";
        }
        if ("越看越乱".equals(d1Value)) {
            return "我现在看得有点乱，";
        }
        if ("方向开始清楚了".equals(d1Value)) {
            return "我大概有点方向了，";
        }
        if ("想跟AI聊聊".equals(d6Value)) {
            return "我想直接和你聊清楚，";
        }
        if ("直接搜答案".equals(d6Value)) {
            return "我就想直接搜个答案，";
        }
        if ("随便刷刷看".equals(d6Value)) {
            return "我想先随手搜一下，";
        }
        if (promptInstructions == null || promptInstructions.isBlank()) {
            return "";
        }
        if (promptInstructions.contains("确认式")) {
            return "我想最后确认一下，";
        }
        if (promptInstructions.contains("急迫") || promptInstructions.contains("立即")) {
            return "我现在要赶紧定下来，";
        }
        if (promptInstructions.contains("聊天")) {
            return "我先想顺手问一句，";
        }
        return "我现在有点拿不准，";
    }

    private String resolveD2Cue(String mustLines) {
        if (mustLines.contains("明确结论")) {
            return "到底怎么选更稳";
        }
        if (mustLines.contains("可执行条件")) {
            return "先看哪几个条件";
        }
        if (mustLines.contains("收集信息")) {
            return "先从哪些信息看起";
        }
        return "先看哪些点";
    }

    private String resolveD3Cue(String mustLines) {
        if (mustLines.contains("不想踩坑")) {
            return "尽量少踩坑";
        }
        if (mustLines.contains("差别")) {
            return "先弄懂差别";
        }
        if (mustLines.contains("偏好")) {
            return "有点自己的偏好";
        }
        return "先确认方向";
    }

    private String resolveStyleCue(String mustLines, String shouldLines, String promptInstructions) {
        if (mustLines.contains("直接给结论")) {
            return "最好直接给结论";
        }
        if (shouldLines.contains("简短")) {
            return "说得简单点就行";
        }
        if (promptInstructions != null && promptInstructions.contains("对话")) {
            return "像平时聊天那样说";
        }
        return "说得顺一点就行";
    }

    private String extractSoftHintValue(String shouldLines, String dimensionId) {
        if (shouldLines == null || shouldLines.isBlank()) {
            return "";
        }
        for (String line : shouldLines.split("\n")) {
            if (!line.contains(dimensionId + ":")) {
                continue;
            }
            int index = line.indexOf(':');
            if (index >= 0 && index + 1 < line.length()) {
                return line.substring(index + 1).trim();
            }
        }
        return "";
    }

    private V42DiagnosisRequest sampleRequest() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of("channel", "phase10_orchestrator_observer"),
                Map.of("contractMode", "mainline_trace_fixture")
        );
        EngineTaskContextPackage contextPackage = new EngineTaskContextPackage(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "天然水", "饮用水"),
                List.of(),
                false,
                null,
                List.of(),
                List.of(),
                List.of("怡宝", "百岁山"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("contractMode", "mainline_trace_fixture")
        );
        return new V42DiagnosisRequest(
                "task_phase10_v42_e2e",
                FIXED_REPORT_ID,
                "4.2",
                taskOrder,
                contextPackage,
                List.of("农夫"),
                Map.of(
                        "怡宝", List.of("C'estbon"),
                        "百岁山", List.of("Ganten")
                )
        );
    }

    private record Fixture(
            V42DiagnosisOrchestrator orchestrator,
            RecordingModelAdapter recordingModelAdapter
    ) {
    }

    private static final class RecordingExecutionObserver implements V42ExecutionObserver {

        private final List<StageTrace> stageTraces = new ArrayList<>();
        private FailureTrace failure;

        @Override
        public void onStageCompleted(StageTrace trace) {
            stageTraces.add(trace);
        }

        @Override
        public void onExecutionFailed(FailureTrace trace) {
            failure = trace;
        }

        private List<String> stageNames() {
            return stageTraces.stream().map(StageTrace::stage).toList();
        }

        private Map<String, Object> summary(String stage) {
            return stageTraces.stream()
                    .filter(trace -> trace.stage().equals(stage))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("缺少 stage trace: " + stage))
                    .summary();
        }

        private FailureTrace failure() {
            return failure;
        }

        private List<StageTrace> stageTraces() {
            return List.copyOf(stageTraces);
        }
    }

    private static final class RecordingModelAdapter implements ModelAdapter {

        private final List<Node3ProbeInput> recordedInputs = new ArrayList<>();

        @Override
        public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
            recordedInputs.add(probeInput);
            String responseText = buildResponse(probeInput);
            return new RawResponse(
                    responseText,
                    "",
                    List.of("https://phase10.example/" + probeInput.probeId()),
                    24,
                    responseLengthTokens(probeInput.probeId()),
                    responseLengthTokens(probeInput.probeId()) + 24,
                    15L,
                    false
            );
        }

        private List<Node3ProbeInput> recordedInputs() {
            return List.copyOf(recordedInputs);
        }

        private String buildResponse(Node3ProbeInput probeInput) {
            int bucket = Math.floorMod(probeInput.probeId().hashCode(), 10);
            boolean assertive = isAssertive(probeInput);
            String competitor = Math.floorMod(probeInput.probeId().hashCode(), 2) == 0 ? "怡宝" : "百岁山";

            if (assertive) {
                return switch (bucket) {
                    case 0, 1, 2, 3, 4, 5 ->
                            "如果看饮用水稳定性，我更推荐农夫山泉，和" + competitor + "比更稳，配料与水源信息也更清楚。";
                    case 6, 7 ->
                            "我会先选农夫，日常补水更稳，" + competitor + "可以做备选。";
                    case 8 ->
                            "这家天然水老大更稳，补水场景可以优先选它，" + competitor + "更像备选。";
                    default ->
                            competitor + "也能满足基础补水，但如果只看稳妥度我不会先提别的品牌。";
                };
            }

            return switch (bucket) {
                case 0, 1, 2 ->
                        "日常买饮用水我更推荐农夫山泉，口感和稳定性都更稳。";
                case 3 ->
                        "先看农夫，补水场景更稳，" + competitor + "可以再比较。";
                case 4 ->
                        "这家天然水老大在日常补水里更稳，先把它放进首选。";
                case 5 ->
                        competitor + "更适合只看价格的人。";
                case 6 ->
                        "农夫山泉更适合日常补水，" + competitor + "可以当次选。";
                case 7 ->
                        "如果看稳定和日常接受度，我会先想到农夫山泉。";
                case 8 ->
                        "这家天然水老大更稳，适合先放进候选。";
                default ->
                        competitor + "价格更敏感一些。";
            };
        }

        private int responseLengthTokens(String probeId) {
            return 40 + Math.floorMod(probeId.hashCode(), 8);
        }

        private boolean isAssertive(Node3ProbeInput probeInput) {
            if (probeInput.realizationPlan() == null || probeInput.realizationPlan().simulationIntentProfile() == null) {
                return false;
            }
            String surfaceIntent = probeInput.realizationPlan().simulationIntentProfile().surfaceIntent();
            return "seek_clear_answer".equals(surfaceIntent) || "confirm_and_buy".equals(surfaceIntent);
        }
    }
}
