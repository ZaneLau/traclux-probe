package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.ModelCapability;
import com.geoprobe.ai.ModelCapabilityRegistry;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.RunMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.shared.DiagnosisCancellationRegistry;
import com.geoprobe.service.shared.DiagnosisIdempotencyService;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import com.geoprobe.service.engine.v42.MinusOneContextAssemblyService;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class DiagnosisServiceProgressTest {

    private static final String USER_ID = "user-1";
    private static final String DIAGNOSIS_ID = "diag-1";

    private DiagnosisMapper diagnosisMapper;
    private RunMapper runMapper;
    private DiagnosisService diagnosisService;
    private V42ReportProjectionService v42ReportProjectionService;
    private RedisSupportService redisSupportService;
    private CreditService creditService;
    private DiagnosisInferenceService diagnosisInferenceService;
    private DiagnosisEditionPreviewUseCaseService diagnosisEditionPreviewUseCaseService;
    private DiagnosisBillingService diagnosisBillingService;
    private V42DiagnosisExecutionService v42DiagnosisExecutionService;
    private V42TargetProbeCountPolicy v42TargetProbeCountPolicy;
    private ModelCapabilityRegistry modelCapabilityRegistry;
    private VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    @BeforeEach
    void setUp() {
        diagnosisMapper = mock(DiagnosisMapper.class);
        DiagnosisTaskMapper diagnosisTaskMapper = mock(DiagnosisTaskMapper.class);
        runMapper = mock(RunMapper.class);
        LlmCallLogMapper llmCallLogMapper = mock(LlmCallLogMapper.class);
        CreditTransactionMapper creditTransactionMapper = mock(CreditTransactionMapper.class);
        creditService = mock(CreditService.class);
        diagnosisInferenceService = mock(DiagnosisInferenceService.class);
        diagnosisEditionPreviewUseCaseService = mock(DiagnosisEditionPreviewUseCaseService.class);
        diagnosisBillingService = mock(DiagnosisBillingService.class);
        DiagnosisProbeEventService diagnosisProbeEventService = mock(DiagnosisProbeEventService.class);
        v42DiagnosisExecutionService = mock(V42DiagnosisExecutionService.class);
        v42ReportProjectionService = mock(V42ReportProjectionService.class);
        MinusOneContextAssemblyService minusOneContextAssemblyService = mock(MinusOneContextAssemblyService.class);
        DiagnosisCancellationRegistry diagnosisCancellationRegistry = mock(DiagnosisCancellationRegistry.class);
        redisSupportService = mock(RedisSupportService.class);
        modelCapabilityRegistry = mock(ModelCapabilityRegistry.class);
        v42TargetProbeCountPolicy = mock(V42TargetProbeCountPolicy.class);
        volcengineRewardRunAdmissionService = mock(VolcengineRewardRunAdmissionService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(redisSupportService.getJson(Mockito.anyString(), Mockito.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any(), Mockito.anyString()))
                .thenReturn(null);
        when(modelCapabilityRegistry.resolve(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new ModelCapability(true, true, true, true));
        when(v42TargetProbeCountPolicy.defaultTargetProbeCount()).thenReturn(100);
        when(v42TargetProbeCountPolicy.resolveOrDefaultForFrontDeskPlanning(Mockito.anyMap()))
                .thenAnswer(invocation -> {
                    int explicit = resolveTargetFromHints(invocation.getArgument(0));
                    return explicit > 0 ? explicit : 100;
                });
        when(v42TargetProbeCountPolicy.normalizeTaskOrderHints(Mockito.anyMap(), Mockito.anyInt()))
                .thenAnswer(invocation -> normalizeTaskOrderHints(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderHints(Mockito.anyMap(), Mockito.anyString()))
                .thenAnswer(invocation -> resolveRequiredTargetFromHints(invocation.getArgument(0), invocation.getArgument(1)));
        when(v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(Mockito.anyMap(), Mockito.anyString()))
                .thenAnswer(invocation -> resolveRequiredTargetFromMeta(invocation.getArgument(0), invocation.getArgument(1)));
        when(minusOneContextAssemblyService.assemble(any(), Mockito.anyList(), Mockito.anyBoolean(), Mockito.anyString(), Mockito.anyMap()))
                .thenAnswer(invocation -> {
                    FrontDeskTaskOrder taskOrder = invocation.getArgument(0, FrontDeskTaskOrder.class);
                    @SuppressWarnings("unchecked")
                    List<String> anchors = invocation.getArgument(1, List.class);
                    boolean fallbackAnchorMode = invocation.getArgument(2, Boolean.class);
                    String anchorSource = invocation.getArgument(3, String.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extensibleSignals = invocation.getArgument(4, Map.class);
                    return new EngineTaskContextPackage(
                            taskOrder.brandName(),
                            taskOrder.category(),
                            taskOrder.competitors(),
                            anchors,
                            anchors.stream().map(anchor -> new CategoryAnchorSignal(anchor, anchorSource, 1.0d)).toList(),
                            fallbackAnchorMode,
                            null,
                            List.of(),
                            List.of(),
                            taskOrder.competitors(),
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
                            extensibleSignals == null ? Map.of() : Map.copyOf(extensibleSignals)
                    );
                });

        DiagnosisRuntimeQueryService diagnosisRuntimeQueryService = new DiagnosisRuntimeQueryService(
                runMapper,
                llmCallLogMapper,
                creditTransactionMapper,
                diagnosisProbeEventService,
                v42TargetProbeCountPolicy,
                v42ReportProjectionService,
                objectMapper,
                redisSupportService
        );
        DiagnosisAccessService diagnosisAccessService = new DiagnosisAccessService(diagnosisMapper, diagnosisTaskMapper);
        DiagnosisRuntimeReadUseCaseService diagnosisRuntimeReadUseCaseService =
                new DiagnosisRuntimeReadUseCaseService(diagnosisAccessService, diagnosisRuntimeQueryService);
        DiagnosisIdempotencyService diagnosisIdempotencyService = new DiagnosisIdempotencyService(diagnosisAccessService, redisSupportService);
        DiagnosisMapSupport diagnosisMapSupport = new DiagnosisMapSupport(objectMapper);
        FrontDeskPreviewTokenService frontDeskPreviewTokenService = new FrontDeskPreviewTokenService(redisSupportService);

        DiagnosisSnapshotService diagnosisSnapshotService =
                new DiagnosisSnapshotService(diagnosisMapSupport, modelCapabilityRegistry);

        diagnosisService = new DiagnosisService(
                diagnosisMapper,
                creditService,
                new V42TaskOrderExecutionPolicyResolver(
                        v42TargetProbeCountPolicy,
                        new V42RealtimeSearchPolicyResolver()
                ),
                new V42Node3TargetEnginePolicyResolver(),
                v42DiagnosisExecutionService,
                v42ReportProjectionService,
                diagnosisRuntimeQueryService,
                diagnosisRuntimeReadUseCaseService,
                diagnosisAccessService,
                diagnosisIdempotencyService,
                diagnosisMapSupport,
                minusOneContextAssemblyService,
                diagnosisCancellationRegistry,
                diagnosisInferenceService,
                diagnosisEditionPreviewUseCaseService,
                diagnosisBillingService,
                diagnosisSnapshotService,
                frontDeskPreviewTokenService,
                volcengineRewardRunAdmissionService,
                objectMapper
        );
    }

    @Test
    void shouldSwitchToParsingWhenDegradedSamplingAlreadyProducedParsedRuns() {
        Diagnosis diagnosis = runningDiagnosisWithTaskOrderTarget(110, 76);
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(runningTask(76, 24));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals("SAMPLING", response.getProgress().getCurrentStep());
        assertEquals(24, response.getProgress().getSamplingCompleted());
        assertEquals(110, response.getProgress().getSamplingTotal());
        assertEquals(110, response.getProgress().getSamplingExpectedTotal());
        assertEquals(76, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(34, response.getProgress().getSamplingMissingTotal());
    }

    @Test
    void shouldKeepActualSamplingTotalAfterDegradedSamplingFullyParsed() {
        Diagnosis diagnosis = runningDiagnosisWithTaskOrderTarget(110, 76);
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(runningTask(76, 76));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals("SAMPLING", response.getProgress().getCurrentStep());
        assertEquals(76, response.getProgress().getSamplingCompleted());
        assertEquals(110, response.getProgress().getSamplingTotal());
        assertEquals(110, response.getProgress().getSamplingExpectedTotal());
        assertEquals(76, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(34, response.getProgress().getSamplingMissingTotal());
    }

    @Test
    void shouldUseGeneratedSamplingTotalDuringSamplingFallbackWhenShortfallMetaExists() {
        Diagnosis diagnosis = runningDiagnosisWithTaskOrderTarget(110, 76);
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(runningTask(38, 30));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals("SAMPLING", response.getProgress().getCurrentStep());
        assertEquals(30, response.getProgress().getSamplingCompleted());
        assertEquals(110, response.getProgress().getSamplingTotal());
        assertEquals(110, response.getProgress().getSamplingExpectedTotal());
        assertEquals(76, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(34, response.getProgress().getSamplingMissingTotal());
    }

    @Test
    void shouldRecoverCompletedProgressFromV42CanonicalTaskWhenRedisExpired() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id(DIAGNOSIS_ID)
                .userId(USER_ID)
                .status(2)
                .brandName("探镜")
                .category("运动相机")
                .reportData(Map.of("meta", Map.of(
                        "engineVersion", "4.2",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 100)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(new V42DiagnosisResult.Task(
                DIAGNOSIS_ID,
                "rpt_" + DIAGNOSIS_ID,
                "4.2",
                "COMPLETED",
                java.time.LocalDateTime.now().minusMinutes(2),
                java.time.LocalDateTime.now().minusMinutes(1),
                100,
                "doubao",
                96,
                4,
                probeTrace(100)
        ));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals("COMPLETED", response.getProgress().getCurrentStep());
        assertEquals(100, response.getProgress().getSamplingCompleted());
        assertEquals(100, response.getProgress().getSamplingTotal());
        assertEquals(100, response.getProgress().getSamplingExpectedTotal());
    }

    @Test
    void shouldUseTriggeredRulesFallbackFlagForV42OpsMetrics() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id(DIAGNOSIS_ID)
                .userId(USER_ID)
                .status(2)
                .brandName("探镜")
                .category("运动相机")
                .totalApiCalls(6)
                .reportData(Map.of("meta", Map.of(
                        "engineVersion", "4.2",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 2)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(new V42DiagnosisResult.Task(
                DIAGNOSIS_ID,
                "rpt_" + DIAGNOSIS_ID,
                "4.2",
                "COMPLETED",
                java.time.LocalDateTime.now().minusMinutes(2),
                java.time.LocalDateTime.now().minusMinutes(1),
                2,
                "doubao",
                2,
                0,
                probeTraceWithRenderFallbackSignals()
        ));

        var response = diagnosisService.getOpsMetrics(USER_ID, DIAGNOSIS_ID);

        assertEquals(true, response.getFallbackUsed());
        assertEquals(0.5d, response.getFallbackRate());
    }

    @Test
    void shouldPreferTaskOrderProbeCountOverStaleRedisExpectedTotal() {
        Diagnosis diagnosis = runningDiagnosisWithTaskOrderTarget(50, 38);
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(redisSupportService.getJson(Mockito.anyString(), Mockito.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any(), Mockito.anyString()))
                .thenReturn(Map.of(
                        "currentStep", "NODE3_SAMPLING",
                        "stepsCompleted", 5,
                        "stepsTotal", 10,
                        "samplingCompleted", 38,
                        "samplingTotal", 38,
                        "samplingExpectedTotal", 110,
                        "samplingGeneratedTotal", 38,
                        "samplingMissingTotal", 72,
                        "percent", 68.0d
                ));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals(50, response.getProgress().getSamplingExpectedTotal());
        assertEquals(38, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(12, response.getProgress().getSamplingMissingTotal());
    }

    @Test
    void shouldPreferTaskOrderProbeCountOverStaleMirrorFieldsInMeta() {
        Diagnosis diagnosis = runningDiagnosisWithTaskOrderTarget(50, 38);
        @SuppressWarnings("unchecked")
        Map<String, Object> reportData = (Map<String, Object>) diagnosis.getReportData();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) reportData.get("meta");
        meta.put("expectedSamples", 110);
        meta.put("targetProbeCount", 110);
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(runningTask(38, 30));

        DiagnosisProgressResponse response = diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID);

        assertEquals(50, response.getProgress().getSamplingExpectedTotal());
        assertEquals(38, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(12, response.getProgress().getSamplingMissingTotal());
    }

    @Test
    void shouldFailProgressWhenV42TaskOrderSnapshotIsMissing() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id(DIAGNOSIS_ID)
                .userId(USER_ID)
                .status(1)
                .edition(1)
                .reportData(Map.of("meta", Map.of(
                        "engineVersion", "4.2",
                        "generatedSamples", 38,
                        "expectedSamples", 110
                )))
                .build();
        when(diagnosisMapper.selectById(DIAGNOSIS_ID)).thenReturn(diagnosis);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> diagnosisService.getDiagnosisProgress(USER_ID, DIAGNOSIS_ID)
        );
    }

    private Diagnosis runningDiagnosisWithTaskOrderTarget(int targetProbeCount, int generatedSamples) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("engineVersion", "4.2");
        meta.put("generatedSamples", generatedSamples);
        meta.put("missingSamples", Math.max(0, targetProbeCount - generatedSamples));
        meta.put("samplingVariantShortfall", generatedSamples < targetProbeCount);
        meta.put("taskOrderSnapshot", Map.of(
                "businessHints", Map.of(
                        "sampling", Map.of("targetProbeCount", targetProbeCount)
                )
        ));
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("meta", meta);
        return Diagnosis.builder()
                .id(DIAGNOSIS_ID)
                .userId(USER_ID)
                .status(1)
                .edition(1)
                .reportData(reportData)
                .build();
    }

    private V42DiagnosisResult.Task runningTask(int sampleSize, int successfulProbes) {
        return new V42DiagnosisResult.Task(
                DIAGNOSIS_ID,
                "rpt_" + DIAGNOSIS_ID,
                "4.2",
                "RUNNING",
                java.time.LocalDateTime.now().minusMinutes(2),
                java.time.LocalDateTime.now().minusMinutes(1),
                sampleSize,
                "doubao",
                successfulProbes,
                Math.max(0, sampleSize - successfulProbes),
                probeTrace(sampleSize)
        );
    }

    @SuppressWarnings("unchecked")
    private int resolveTargetFromHints(Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return 0;
        }
        Object sampling = hints.get("sampling");
        if (sampling instanceof Map<?, ?> samplingMap) {
            int nested = parsePositiveInt(((Map<String, Object>) samplingMap).get("targetProbeCount"));
            if (nested > 0) {
                return nested;
            }
        }
        int flat = parsePositiveInt(hints.get("sampling.targetProbeCount"));
        return Math.max(0, flat);
    }

    private int resolveRequiredTargetFromHints(Map<String, Object> hints, String scene) {
        int target = resolveTargetFromHints(hints);
        if (target > 0) {
            return target;
        }
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数配置，scene=" + scene);
    }

    @SuppressWarnings("unchecked")
    private int resolveRequiredTargetFromMeta(Map<String, Object> meta, String scene) {
        if (meta == null || meta.isEmpty()) {
            throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照，scene=" + scene);
        }
        Object taskOrderSnapshot = meta.get("taskOrderSnapshot");
        if (taskOrderSnapshot instanceof Map<?, ?> snapshotMap) {
            Object businessHints = ((Map<String, Object>) snapshotMap).get("businessHints");
            if (businessHints instanceof Map<?, ?> hints) {
                int nested = resolveTargetFromHints((Map<String, Object>) hints);
                if (nested > 0) {
                    return nested;
                }
            }
        }
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照，scene=" + scene);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeTaskOrderHints(Map<String, Object> hints, int targetProbeCount) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (hints != null && !hints.isEmpty()) {
            normalized.putAll(hints);
        }
        LinkedHashMap<String, Object> sampling = new LinkedHashMap<>();
        Object existingSampling = normalized.get("sampling");
        if (existingSampling instanceof Map<?, ?> samplingMap) {
            sampling.putAll((Map<String, Object>) samplingMap);
        }
        sampling.put("targetProbeCount", targetProbeCount);
        normalized.put("sampling", Map.copyOf(sampling));
        normalized.remove("sampling.targetProbeCount");
        return Map.copyOf(normalized);
    }

    private int parsePositiveInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<V42DiagnosisResult.ProbeTrace> probeTrace(int sampleSize) {
        return java.util.stream.IntStream.range(0, sampleSize)
                .mapToObj(index -> new V42DiagnosisResult.ProbeTrace(
                        "probe-" + index,
                        BucketType.ORTHOGONAL,
                        Map.of("D1", "state-" + index),
                        Map.of("D1", "场景-" + index),
                        ProbeExposurePolicy.BRAND_HIDDEN,
                        "rendered-probe-" + index,
                        ProbeDispatchStatus.SUCCESS,
                        ProbeType.OPEN_DISCOVERY,
                        true,
                        "response-probe-" + index,
                        120,
                        "Medium",
                        "L1_EXACT",
                        null
                ))
                .toList();
    }

    private List<V42DiagnosisResult.ProbeTrace> probeTraceWithRenderFallbackSignals() {
        return List.of(
                new V42DiagnosisResult.ProbeTrace(
                        "probe-fallback",
                        BucketType.ORTHOGONAL,
                        Map.of("D1", "verification"),
                        Map.of("D1", "确认"),
                        "seek_clear_answer",
                        "confirmation_seeking",
                        "single_turn_search_like",
                        ProbeExposurePolicy.BRAND_HIDDEN,
                        "运动相机怎么挑更稳？",
                        "orthogonal_constrained_llm",
                        ProbeDispatchStatus.SUCCESS,
                        ProbeType.OPEN_DISCOVERY,
                        true,
                        "response-fallback",
                        "response-fallback",
                        null,
                        List.of(),
                        10,
                        20,
                        30,
                        1000L,
                        "Medium",
                        "L1_EXACT",
                        null,
                        false,
                        false,
                        false,
                        List.of(),
                        0,
                        List.of(),
                        "doubao",
                        null,
                        List.of(),
                        1,
                        1,
                        Map.of("renderFallbackUsed", true),
                        null
                ),
                new V42DiagnosisResult.ProbeTrace(
                        "probe-normal",
                        BucketType.ORTHOGONAL,
                        Map.of("D1", "verification"),
                        Map.of("D1", "确认"),
                        "seek_clear_answer",
                        "confirmation_seeking",
                        "single_turn_search_like",
                        ProbeExposurePolicy.BRAND_HIDDEN,
                        "运动相机哪款更值得看？",
                        "orthogonal_constrained_llm",
                        ProbeDispatchStatus.SUCCESS,
                        ProbeType.OPEN_DISCOVERY,
                        true,
                        "response-normal",
                        "response-normal",
                        null,
                        List.of(),
                        10,
                        20,
                        30,
                        1000L,
                        "Medium",
                        "L1_EXACT",
                        null,
                        false,
                        false,
                        false,
                        List.of(),
                        0,
                        List.of(),
                        "doubao",
                        null,
                        List.of(),
                        1,
                        1,
                        Map.of("renderFallbackUsed", false),
                        null
                )
        );
    }
}
