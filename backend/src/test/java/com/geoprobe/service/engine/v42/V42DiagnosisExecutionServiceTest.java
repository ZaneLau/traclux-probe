package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.V42DiagnosisRequest;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.service.CreditService;
import com.geoprobe.service.DiagnosisBillingService;
import com.geoprobe.service.DiagnosisProbeEventService;
import com.geoprobe.service.DiagnosisTaskNativeRuntimeService;
import com.geoprobe.service.RedisSupportService;
import com.geoprobe.service.shared.BrandAliasService;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import com.geoprobe.service.shared.DiagnosisAliasConfigService;
import com.geoprobe.service.shared.DiagnosisCancellationRegistry;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V42DiagnosisExecutionServiceTest {

    @Mock
    private DiagnosisMapper diagnosisMapper;
    @Mock
    private BrandAliasService brandAliasService;
    @Mock
    private DiagnosisAliasConfigService diagnosisAliasConfigService;
    @Mock
    private DiagnosisCancellationRegistry diagnosisCancellationRegistry;
    @Mock
    private DiagnosisProbeEventService diagnosisProbeEventService;
    @Mock
    private V42TargetProbeCountPolicy v42TargetProbeCountPolicy;
    @Mock
    private V42DiagnosisOrchestrator v42DiagnosisOrchestrator;
    @Mock
    private V42ReportProjectionService v42ReportProjectionService;
    @Mock
    private RedisSupportService redisSupportService;
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Mock
    private CreditService creditService;
    @Mock
    private DiagnosisBillingService diagnosisBillingService;
    @Mock
    private VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    private V42DiagnosisExecutionService service;

    @BeforeEach
    void setUp() {
        lenient().when(v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(any(), any()))
                .thenAnswer(invocation -> resolveRequiredTargetFromMeta(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderSnapshot(any(), any()))
                .thenAnswer(invocation -> resolveRequiredTargetFromTaskOrderSnapshot(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        service = new V42DiagnosisExecutionService(
                diagnosisMapper,
                brandAliasService,
                diagnosisAliasConfigService,
                diagnosisCancellationRegistry,
                diagnosisProbeEventService,
                v42TargetProbeCountPolicy,
                v42DiagnosisOrchestrator,
                v42ReportProjectionService,
                new ObjectMapper(),
                redisSupportService,
                volcengineRewardRunAdmissionService,
                taskExecutor
        );
        ReflectionTestUtils.setField(service, "diagnosisTaskNativeRuntimeService", diagnosisTaskNativeRuntimeService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "diagnosisBillingService", diagnosisBillingService);
    }

    @Test
    void shouldPreserveLayeredContractsWhenExecutingPlan() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-1")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of(
                        "billingState", "charged",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 100)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById("diag-1")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of("农夫"), Map.of("怡宝", List.of("C'estbon"))));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any())).thenAnswer(invocation -> {
            V42DiagnosisOrchestrator.ProgressCallback progressCallback =
                    invocation.getArgument(1, V42DiagnosisOrchestrator.ProgressCallback.class);
            progressCallback.onProgress(new V42DiagnosisOrchestrator.ProgressUpdate(
                    "NODE1_BLUEPRINTS",
                    1,
                    12.0d,
                    "Node 0 完成，正在生成探针蓝图...",
                    null,
                    null
            ));
            progressCallback.onProgress(new V42DiagnosisOrchestrator.ProgressUpdate(
                    "NODE3_SAMPLING",
                    4,
                    54.0d,
                    "正在向 AI 发送探测请求（11/100）...",
                    11,
                    100
            ));
            return sampleResult();
        });
        when(v42ReportProjectionService.buildReportData(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "seoVsGeo", Map.of(),
                        "competitorRanking", List.of(),
                        "associatedKeywords", List.of()
                ));
        when(v42ReportProjectionService.buildSummaryKpiMirror(any())).thenReturn(Map.of("overallMentionRate", 1.0d));

        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                "https://www.nongfuspring.com",
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "deepseek",
                Map.of("frontDeskUserPrompt", "重点看家用囤水场景"),
                Map.of("source", "frontdesk_create")
        );
        EngineTaskContextPackage contextPackage = new EngineTaskContextPackage(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水"),
                List.of(
                        new CategoryAnchorSignal("包装饮用水", "frontdesk_or_legacy_plan", 1.0d),
                        new CategoryAnchorSignal("饮用水", "frontdesk_or_legacy_plan", 1.0d)
                ),
                true,
                "天然饮用水品牌",
                List.of("家庭常备"),
                List.of("家里常喝的水"),
                List.of("怡宝"),
                List.of("偏好一步到位"),
                List.of("允许一句话搜索式提问"),
                List.of(
                        new GroundedContextSignal(
                                MinusOneContextSlots.USAGE_SCENARIO,
                                "家庭常备",
                                "memo",
                                null,
                                0.8d,
                                List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
                        ),
                        new GroundedContextSignal(
                                MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                                "旧饮用概念",
                                "memo",
                                null,
                                0.4d,
                                List.of(MinusOneContextUses.NODE0_STATE_INFERENCE)
                        )
                ),
                List.of(),
                Map.of("assemblySource", "frontdesk_create")
        );

        service.enqueue(new V42ExecutionPlan(
                "diag-1",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水"),
                true,
                List.of(),
                Map.of(),
                taskOrder,
                contextPackage
        ));

        ArgumentCaptor<V42DiagnosisRequest> requestCaptor = ArgumentCaptor.forClass(V42DiagnosisRequest.class);
        verify(v42DiagnosisOrchestrator).execute(requestCaptor.capture(), any(), any());
        V42DiagnosisRequest request = requestCaptor.getValue();

        assertEquals("https://www.nongfuspring.com", request.taskOrder().brandWebsite());
        assertEquals("frontdesk_create", request.taskOrder().taskMetadata().get("source"));
        assertFalse(request.taskOrder().taskMetadata().containsKey("executionAssemblyStage"));
        assertFalse(request.taskOrder().businessHints().containsKey("categoryAnchorSource"));

        assertEquals("天然饮用水品牌", request.contextPackage().brandBrief());
        assertEquals(List.of("家庭常备"), request.contextPackage().categorySceneMap());
        assertEquals(List.of("家里常喝的水"), request.contextPackage().descriptiveEntrypoints());
        assertEquals(List.of("包装饮用水", "饮用水"), request.contextPackage().categoryAnchors());
        assertEquals("backend_fallback", request.contextPackage().categoryAnchorSignals().getFirst().source());
        assertTrue(request.contextPackage().groundedSignals().stream()
                .noneMatch(signal -> MinusOneContextSlots.CATEGORY_HYPOTHESIS.equals(signal.slot())
                        && "旧饮用概念".equals(signal.value())));
        assertTrue(request.contextPackage().groundedSignals().stream()
                .anyMatch(signal -> MinusOneContextSlots.CATEGORY_HYPOTHESIS.equals(signal.slot())
                        && "包装饮用水".equals(signal.value())));
        assertEquals("diagnosis_execution_service", request.contextPackage().extensibleSignals().get("contextAssemblyStage"));

        ArgumentCaptor<Map<String, Object>> progressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(redisSupportService, atLeastOnce()).setJson(eq("diagnosis:diag-1:progress"), progressCaptor.capture(), any(), eq("v42 diagnosis progress"));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "NODE1_BLUEPRINTS".equals(progress.get("currentStep"))
                        && Integer.valueOf(0).equals(progress.get("samplingCompleted"))
                        && Integer.valueOf(0).equals(progress.get("samplingTotal"))));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "NODE3_SAMPLING".equals(progress.get("currentStep"))
                        && "正在向 AI 发送探测请求（11/100）...".equals(progress.get("currentStepLabel"))
                        && Integer.valueOf(11).equals(progress.get("samplingCompleted"))
                        && Integer.valueOf(100).equals(progress.get("samplingTotal"))));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "REPORT_PROJECTION".equals(progress.get("currentStep"))
                        && "引擎报告已装配，正在生成下游报告视图...".equals(progress.get("currentStepLabel"))));
        verify(volcengineRewardRunAdmissionService)
                .openMainChainLease(eq("diag-1"), any(), eq("diagnosis_execution diagnosisId=diag-1"));
        verify(volcengineRewardRunAdmissionService).closeLease("diag-1");
    }

    @Test
    void shouldProjectSuccessfulSampleCountInsteadOfSampleSizeAfterNode3() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-partial-success")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of(
                        "billingState", "charged",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 100)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById("diag-partial-success")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenReturn(sampleResult(98, 2));
        when(v42ReportProjectionService.buildReportData(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "seoVsGeo", Map.of(),
                        "competitorRanking", List.of(),
                        "associatedKeywords", List.of()
                ));
        when(v42ReportProjectionService.buildSummaryKpiMirror(any())).thenReturn(Map.of("overallMentionRate", 0.98d));

        service.enqueue(new V42ExecutionPlan(
                "diag-partial-success",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        null,
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "doubao",
                        Map.of("sampling", Map.of("targetProbeCount", 100)),
                        Map.of()
                ),
                sampleEngineTaskContextPackage()
        ));

        ArgumentCaptor<Map<String, Object>> progressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(redisSupportService, atLeastOnce()).setJson(eq("diagnosis:diag-partial-success:progress"),
                progressCaptor.capture(), any(), eq("v42 diagnosis progress"));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "REPORT_PROJECTION".equals(progress.get("currentStep"))
                        && Integer.valueOf(98).equals(progress.get("samplingCompleted"))
                        && Integer.valueOf(100).equals(progress.get("samplingTotal"))));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "COMPLETED".equals(progress.get("currentStep"))
                        && Integer.valueOf(98).equals(progress.get("samplingCompleted"))
                        && Integer.valueOf(100).equals(progress.get("samplingTotal"))));
    }

    @Test
    void shouldPreserveKnownSamplingCountsWhenFailureHappensAfterNode3() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-node3-all-failed")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of(
                        "billingState", "charged",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 10)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById("diag-node3-all-failed")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(diagnosisTaskNativeRuntimeService.readProgressSnapshot("diag-node3-all-failed"))
                .thenReturn(DiagnosisProgressSnapshot.builder()
                        .diagnosisTaskId("diag-node3-all-failed")
                        .currentStepCode("NODE3_SAMPLING")
                        .stepsCompleted(4)
                        .stepsTotal(11)
                        .samplingCompleted(0)
                        .samplingExpectedTotal(10)
                        .samplingGeneratedTotal(10)
                        .samplingMissingTotal(0)
                        .samplingVariantShortfall(false)
                        .build());
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenAnswer(invocation -> {
                    V42DiagnosisOrchestrator.ProgressCallback progressCallback =
                            invocation.getArgument(1, V42DiagnosisOrchestrator.ProgressCallback.class);
                    progressCallback.onProgress(new V42DiagnosisOrchestrator.ProgressUpdate(
                            "NODE3_SAMPLING",
                            4,
                            68.0d,
                            "正在向 AI 发送探测请求（10/10）...",
                            0,
                            10
                    ));
                    throw new Node3SamplingViabilityException(
                            "V4.2 Node 3 未产出任何成功样本，provider=chatgpt",
                            sampleResult(0, 10, 10).task()
                    );
                });

        service.enqueue(new V42ExecutionPlan(
                "diag-node3-all-failed",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        null,
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "chatgpt",
                        Map.of("sampling", Map.of("targetProbeCount", 10)),
                        Map.of()
                ),
                sampleEngineTaskContextPackage()
        ));

        ArgumentCaptor<Map<String, Object>> progressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(redisSupportService, atLeastOnce()).setJson(eq("diagnosis:diag-node3-all-failed:progress"),
                progressCaptor.capture(), any(), eq("v42 diagnosis progress"));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "FAILED".equals(progress.get("currentStep"))
                        && Double.valueOf(68.0d).equals(progress.get("percent"))
                        && Integer.valueOf(0).equals(progress.get("samplingCompleted"))
                        && Integer.valueOf(10).equals(progress.get("samplingTotal"))
                        && Integer.valueOf(10).equals(progress.get("samplingGeneratedTotal"))));
        verify(diagnosisTaskNativeRuntimeService).onProgress(
                eq("diag-node3-all-failed"),
                eq("FAILED"),
                eq("诊断执行失败"),
                eq(4),
                eq(11),
                eq(0),
                eq(10),
                eq(10),
                eq(0),
                eq(false),
                eq(68.0d)
        );
        verify(diagnosisTaskNativeRuntimeService).onDiagnosisSamplingFailed(eq(diagnosis), argThat((V42DiagnosisResult.Task task) ->
                task.sampleSize() == 10
                        && task.successfulProbes() == 0
                        && task.failedProbes() == 10
                        && "FAILED".equals(task.status())
        ));
        verify(creditService, never()).refundCredits(any(), any(), anyInt());
    }

    @Test
    void shouldRefundCreditsWhenNode3BudgetGateFailsAfterCharge() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-node3-budget-failed")
                .userId("user-1")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of(
                        "billingState", "charged",
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 10)
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById("diag-node3-budget-failed")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenThrow(new Node3SamplingViabilityException(
                        "V4.2 Node 3 未产出任何成功样本，provider=doubao",
                        sampleResult(0, 10, 10, "TOKEN_BUDGET_EXCEEDED").task()
                ));
        when(diagnosisBillingService.readChargedCredits("diag-node3-budget-failed", diagnosis)).thenReturn(1);

        service.enqueue(new V42ExecutionPlan(
                "diag-node3-budget-failed",
                "4.3",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        null,
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "doubao",
                        Map.of("sampling", Map.of("targetProbeCount", 10)),
                        Map.of()
                ),
                sampleEngineTaskContextPackage()
        ));

        verify(creditService).refundCredits("user-1", "diag-node3-budget-failed", 1);
        verify(diagnosisBillingService).markDiagnosisBillingState("diag-node3-budget-failed", "refunded");
    }

    @Test
    void shouldNotRefundCreditsWhenBrandSpaceRunIsServiceAccessManaged() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-brand-space-budget-failed")
                .userId("user-1")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of(
                        "billingState", "service_access_managed",
                        "taskOrderSnapshot", Map.of(
                                "taskMetadata", Map.of(
                                        "source", "brand_space_schedule",
                                        "origin", "brand_space"
                                )
                        )
                )))
                .build();
        when(diagnosisMapper.selectById("diag-brand-space-budget-failed")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenThrow(new Node3SamplingViabilityException(
                        "V4.2 Node 3 未产出任何成功样本，provider=doubao",
                        sampleResult(0, 10, 10, "TOKEN_BUDGET_EXCEEDED").task()
                ));

        service.enqueue(new V42ExecutionPlan(
                "diag-brand-space-budget-failed",
                "4.3",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        null,
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "doubao",
                        Map.of("sampling", Map.of("targetProbeCount", 10)),
                        Map.of("source", "brand_space_schedule", "origin", "brand_space")
                ),
                sampleEngineTaskContextPackage()
        ));

        verify(creditService, never()).refundCredits(any(), any(), anyInt());
        verify(diagnosisBillingService, never()).markDiagnosisBillingState("diag-brand-space-budget-failed", "refunded");
    }

    @Test
    void shouldFailDiagnosisWhenActualSampleSizeDoesNotMatchTaskOrder() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-mismatch")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of(
                        "meta", Map.of(
                                "billingState", "charged",
                                "expectedSamples", 101,
                                "targetProbeCount", 101,
                                "taskOrderSnapshot", Map.of(
                                        "businessHints", Map.of(
                                                "sampling", Map.of("targetProbeCount", 101)
                                        )
                                )
                        )
                ))
                .build();
        when(diagnosisMapper.selectById("diag-mismatch")).thenReturn(diagnosis, diagnosis);
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenReturn(sampleResult());

        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of("sampling", Map.of("targetProbeCount", 101)),
                Map.of("source", "frontdesk_create")
        );
        EngineTaskContextPackage contextPackage = new EngineTaskContextPackage(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "天然饮用水"),
                List.of(
                        new CategoryAnchorSignal("包装饮用水", "frontdesk_or_legacy_plan", 1.0d),
                        new CategoryAnchorSignal("天然饮用水", "frontdesk_or_legacy_plan", 1.0d)
                ),
                true,
                "天然饮用水品牌",
                List.of("家庭常备"),
                List.of("家里常喝的水"),
                List.of("怡宝"),
                List.of("偏好一步到位"),
                List.of("允许一句话搜索式提问"),
                List.of(),
                List.of(),
                Map.of("assemblySource", "frontdesk_create")
        );

        service.enqueue(new V42ExecutionPlan(
                "diag-mismatch",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "天然饮用水"),
                true,
                List.of(),
                Map.of(),
                taskOrder,
                contextPackage
        ));

        ArgumentCaptor<Diagnosis> updateCaptor = ArgumentCaptor.forClass(Diagnosis.class);
        verify(diagnosisMapper, atLeastOnce()).updateById(updateCaptor.capture());
        assertTrue(updateCaptor.getAllValues().stream().anyMatch(updated ->
                updated.getStatus() != null
                        && updated.getStatus() == 3
                        && updated.getErrorMessage() != null
                        && updated.getErrorMessage().contains("未按前台任务单完成探针交付")));
    }

    @Test
    void shouldFailDiagnosisWhenTaskOrderSnapshotMissing() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-missing-task-order")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of("billingState", "charged")))
                .build();
        when(diagnosisMapper.selectById("diag-missing-task-order")).thenReturn(diagnosis, diagnosis);
        doThrow(new IllegalStateException("缺少合法的前台任务单探针数快照"))
                .when(v42TargetProbeCountPolicy)
                .resolveRequiredFromDiagnosisMeta(
                        argThat(meta -> meta == null || !meta.containsKey("taskOrderSnapshot")),
                        eq("v42_execution")
                );
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of(),
                Map.of()
        );

        service.enqueue(new V42ExecutionPlan(
                "diag-missing-task-order",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                taskOrder,
                sampleEngineTaskContextPackage()
        ));

        verify(diagnosisMapper).updateById(org.mockito.ArgumentMatchers.<Diagnosis>argThat(updated ->
                "diag-missing-task-order".equals(updated.getId())
                        && Integer.valueOf(3).equals(updated.getStatus())
                        && updated.getErrorMessage() != null
                        && updated.getErrorMessage().contains("缺少合法的前台任务单探针数快照")));
        verify(v42DiagnosisOrchestrator, never()).execute(any(V42DiagnosisRequest.class), any(), any());
        verify(redisSupportService, never()).setJson(eq("diagnosis:diag-missing-task-order:progress"), any(), any(), eq("v42 diagnosis progress"));
    }

    @Test
    void shouldUsePlanTaskOrderForProgressWhenLegacyMetaMissing() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-progress-fallback")
                .brandName("农夫山泉")
                .category("包装饮用水")
                .reportData(Map.of("meta", Map.of("billingState", "charged")))
                .build();
        when(diagnosisMapper.selectById("diag-progress-fallback")).thenReturn(diagnosis, diagnosis);
        doThrow(new IllegalStateException("缺少合法的前台任务单探针数快照"))
                .when(v42TargetProbeCountPolicy)
                .resolveRequiredFromDiagnosisMeta(
                        argThat(meta -> meta == null || !meta.containsKey("taskOrderSnapshot")),
                        eq("v42_execution")
                );
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenThrow(new IllegalStateException("模拟执行失败"));

        service.enqueue(new V42ExecutionPlan(
                "diag-progress-fallback",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        null,
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "doubao",
                        Map.of("sampling", Map.of("targetProbeCount", 88)),
                        Map.of()
                ),
                sampleEngineTaskContextPackage()
        ));

        ArgumentCaptor<Map<String, Object>> progressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(redisSupportService, atLeastOnce()).setJson(
                eq("diagnosis:diag-progress-fallback:progress"),
                progressCaptor.capture(),
                any(),
                eq("v42 diagnosis progress")
        );
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "NODE0_STATE_SPACE".equals(progress.get("currentStep"))
                        && Integer.valueOf(88).equals(progress.get("samplingExpectedTotal"))));
        assertTrue(progressCaptor.getAllValues().stream().anyMatch(progress ->
                "FAILED".equals(progress.get("currentStep"))
                        && Integer.valueOf(88).equals(progress.get("samplingExpectedTotal"))
                        && Integer.valueOf(88).equals(progress.get("samplingMissingTotal"))));
    }

    @Test
    void shouldCompleteTaskOnlyExecutionWithoutLegacyDiagnosisRow() {
        when(diagnosisMapper.selectById("diag-task-only")).thenReturn(null);
        when(diagnosisTaskNativeRuntimeService.readDiagnosisTask("diag-task-only")).thenReturn(DiagnosisTask.builder()
                .id("diag-task-only")
                .requestedByUserId("user-1")
                .statusCode(0)
                .sourceCode(2)
                .engineVersion("4.2")
                .brandNameSnapshot("农夫山泉")
                .categorySnapshot("包装饮用水")
                .officialWebsiteSnapshot("https://www.nongfuspring.com")
                .createdAt(LocalDateTime.parse("2026-04-29T10:00:00"))
                .build());
        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
        when(brandAliasService.generateAliasConfig(any(), any(), any(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of(), Map.of()));
        when(v42DiagnosisOrchestrator.execute(any(V42DiagnosisRequest.class), any(), any()))
                .thenReturn(sampleResult());
        when(v42ReportProjectionService.buildReportData(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "seoVsGeo", Map.of(),
                        "competitorRanking", List.of(),
                        "associatedKeywords", List.of()
                ));
        when(v42ReportProjectionService.buildSummaryKpiMirror(any())).thenReturn(Map.of("overallMentionRate", 1.0d));

        service.enqueue(new V42ExecutionPlan(
                "diag-task-only",
                "4.2",
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                false,
                List.of(),
                Map.of(),
                new FrontDeskTaskOrder(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝"),
                        "https://www.nongfuspring.com",
                        "geo_brand_visibility_diagnosis",
                        "zh-CN",
                        "zh",
                        "doubao",
                        Map.of("sampling", Map.of("targetProbeCount", 100)),
                        Map.of("source", "frontdesk_create")
                ),
                sampleEngineTaskContextPackage()
        ));

        verify(diagnosisMapper, never()).updateById(any(Diagnosis.class));
        verify(diagnosisTaskNativeRuntimeService).onTaskRunning(eq("diag-task-only"), any(LocalDateTime.class));
        verify(diagnosisTaskNativeRuntimeService).onDiagnosisCompleted(
                argThat((Diagnosis diagnosis) ->
                        diagnosis != null
                                && "diag-task-only".equals(diagnosis.getId())
                                && Integer.valueOf(2).equals(diagnosis.getStatus())
                                && "农夫山泉".equals(diagnosis.getBrandName())
                                && diagnosis.getCompletedAt() != null
                ),
                any(V42DiagnosisResult.class),
                any(Map.class)
        );
    }

    private EngineTaskContextPackage sampleEngineTaskContextPackage() {
        return new EngineTaskContextPackage(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用水", "天然饮用水"),
                List.of(
                        new CategoryAnchorSignal("包装饮用水", "frontdesk_or_legacy_plan", 1.0d),
                        new CategoryAnchorSignal("饮用水", "frontdesk_or_legacy_plan", 1.0d),
                        new CategoryAnchorSignal("天然饮用水", "frontdesk_or_legacy_plan", 1.0d)
                ),
                true,
                "天然饮用水品牌",
                List.of("家庭常备"),
                List.of("家里常喝的水"),
                List.of("怡宝"),
                List.of("偏好一步到位"),
                List.of("允许一句话搜索式提问"),
                List.of(),
                Map.of()
        );
    }

    private V42DiagnosisResult sampleResult() {
        return sampleResult(100, 0);
    }

    private V42DiagnosisResult sampleResult(int successCount, int failedCount) {
        return sampleResult(successCount, failedCount, 100);
    }

    private V42DiagnosisResult sampleResult(int successCount, int failedCount, int sampleSize) {
        return sampleResult(successCount, failedCount, sampleSize, "EMPTY_FINAL_RESPONSE");
    }

    private V42DiagnosisResult sampleResult(int successCount, int failedCount, int sampleSize, String failedReason) {
        List<V42DiagnosisResult.ProbeTrace> probeTraces = new java.util.ArrayList<>();
        for (int index = 1; index <= sampleSize; index++) {
            ProbeDispatchStatus status = index <= successCount ? ProbeDispatchStatus.SUCCESS : ProbeDispatchStatus.FAILED;
            String failureReason = status == ProbeDispatchStatus.SUCCESS ? null : failedReason;
            probeTraces.add(new V42DiagnosisResult.ProbeTrace(
                    "probe-" + index,
                    BucketType.ORTHOGONAL,
                    Map.of("D1", "state-1"),
                    Map.of("D1", "场景-1"),
                    ProbeExposurePolicy.BRAND_HIDDEN,
                    "rendered-probe-" + index,
                    status,
                    ProbeType.OPEN_DISCOVERY,
                    status == ProbeDispatchStatus.SUCCESS,
                    status == ProbeDispatchStatus.SUCCESS ? "response-probe-" + index : "",
                    status == ProbeDispatchStatus.SUCCESS ? 32 : 0,
                    "Short",
                    status == ProbeDispatchStatus.SUCCESS ? "L1_EXACT" : null,
                    failureReason
            ));
        }
        V42DiagnosisResult.Task task = new V42DiagnosisResult.Task(
                "diag-1",
                "rpt_diag1",
                "4.2",
                successCount == 0 ? "FAILED" : "COMPLETED",
                LocalDateTime.parse("2026-03-29T10:00:00"),
                LocalDateTime.parse("2026-03-29T10:01:00"),
                sampleSize,
                "doubao",
                successCount,
                failedCount,
                probeTraces
        );
        V42DiagnosisResult.Report report = new V42DiagnosisResult.Report(
                "diag-1",
                "rpt_diag1",
                "4.2",
                LocalDateTime.parse("2026-03-29T10:00:00"),
                LocalDateTime.parse("2026-03-29T10:01:00"),
                sampleSize,
                "doubao",
                "methodology",
                "disclaimer",
                new KpiSummary(
                        1.0d,
                        1.0d,
                        1.0d,
                        new LengthStratifiedKpi(
                                new LengthBucketKpi("Short", 1, 1, 0, 1.0d, 1.0d, 1.0d),
                                new LengthBucketKpi("Medium", 0, 0, 0, 0.0d, 0.0d, 0.0d),
                                new LengthBucketKpi("Long", 0, 0, 0, 0.0d, 0.0d, 0.0d)
                        ),
                        1.0d,
                        1.0d,
                        100.0d,
                        new BucketKpi(BucketType.ORTHOGONAL, 1, 1, 1, 0, 1.0d, 1.0d, 1.0d, 1.0d, 1.0d),
                        new BucketKpi(BucketType.PARETO, 0, 0, 0, 0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d),
                        new BucketKpi(BucketType.ADVERSARIAL, 0, 0, 0, 0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d),
                        Map.of("D1", 1.0d),
                        Map.of("D1", 1.0d),
                        Map.of(),
                        sampleSize
                ),
                new CommercialInsights(
                        "品牌已经稳定进入候选池。",
                        List.of(
                                new InsightItem("命中率稳定", "当前 1 个样本里提及率 100%。", "必须继续保持当前命中路径。"),
                                new InsightItem("推荐率到位", "当前 1 个样本里推荐率 100%。", "必须维持同类场景的推荐表达。"),
                                new InsightItem("样本量仍小", "当前仅有 1 个样本，需要继续扩大验证。", "必须继续补跑更多场景样本。")
                        ),
                        "LLM"
                ),
                new DefenseCompletenessSummary(
                        1.0d,
                        1,
                        List.of(new DefenseCompletenessResult("probe-1", 1.0d, 1.0d, 1.0d, 1.0d))
                ),
                new DashboardCharts(
                        new DashboardCharts.RadarChartData(
                                "radar",
                                List.of("D1"),
                                "农夫山泉",
                                List.of(1.0d),
                                Map.of()
                        ),
                        new DashboardCharts.ScatterChartData(
                                "scatter",
                                "brandDistance",
                                "recommendRate",
                                List.of(new DashboardCharts.ScatterPoint("农夫山泉", 0.0d, 1.0d, true, 1.0d, 1))
                        ),
                        new DashboardCharts.SankeyChartData(
                                "sankey",
                                List.of(
                                        new DashboardCharts.SankeyNode("intent", "意图", "intent"),
                                        new DashboardCharts.SankeyNode("candidate", "农夫山泉", "candidate"),
                                        new DashboardCharts.SankeyNode("recommend", "农夫山泉推荐", "recommend")
                                ),
                                List.of(
                                        new DashboardCharts.SankeyLink("intent", "candidate", 1),
                                        new DashboardCharts.SankeyLink("candidate", "recommend", 1)
                                )
                        ),
                        new DashboardCharts.StackedBarChartData(
                                "stacked_bar",
                                List.of("总体提及率"),
                                List.of(new DashboardCharts.MetricSeries("orthogonal", "正交探测", List.of(1.0d), 1))
                        )
                ),
                List.of(new V42DiagnosisResult.DisplayNameMapping("kpiSummary.overallMentionRate", "总体提及率"))
        );
        return new V42DiagnosisResult(task, report);
    }

    @SuppressWarnings("unchecked")
    private int resolveTargetFromMeta(Map<String, Object> meta, int fallback) {
        if (meta == null || meta.isEmpty()) {
            return fallback;
        }
        Object taskOrderSnapshot = meta.get("taskOrderSnapshot");
        if (taskOrderSnapshot instanceof Map<?, ?> snapshotMap) {
            Object businessHints = ((Map<String, Object>) snapshotMap).get("businessHints");
            if (businessHints instanceof Map<?, ?> hints) {
                Object sampling = ((Map<String, Object>) hints).get("sampling");
                if (sampling instanceof Map<?, ?> samplingMap) {
                    Object value = ((Map<String, Object>) samplingMap).get("targetProbeCount");
                    if (value instanceof Number number && number.intValue() > 0) {
                        return number.intValue();
                    }
                }
            }
        }
        Object targetProbeCount = meta.get("targetProbeCount");
        if (targetProbeCount instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        Object expectedSamples = meta.get("expectedSamples");
        if (expectedSamples instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return fallback;
    }

    private int resolveRequiredTargetFromMeta(Map<String, Object> meta, String scene) {
        int resolved = resolveTargetFromMeta(meta, 0);
        if (resolved > 0) {
            return resolved;
        }
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照" + sceneSuffix(scene));
    }

    @SuppressWarnings("unchecked")
    private int resolveRequiredTargetFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot, String scene) {
        if (taskOrderSnapshot != null && !taskOrderSnapshot.isEmpty()) {
            Object businessHints = taskOrderSnapshot.get("businessHints");
            if (businessHints instanceof Map<?, ?> hints) {
                int resolved = resolveTargetFromTaskOrderHints((Map<String, Object>) hints);
                if (resolved > 0) {
                    return resolved;
                }
            }
        }
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照" + sceneSuffix(scene));
    }

    @SuppressWarnings("unchecked")
    private int resolveTargetFromTaskOrderHints(Map<String, Object> taskOrderHints) {
        if (taskOrderHints == null || taskOrderHints.isEmpty()) {
            return 0;
        }
        Object sampling = taskOrderHints.get("sampling");
        if (sampling instanceof Map<?, ?> samplingMap) {
            Object value = ((Map<String, Object>) samplingMap).get("targetProbeCount");
            if (value instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
        }
        return 0;
    }

    private String sceneSuffix(String scene) {
        return scene == null || scene.isBlank() ? "" : "，scene=" + scene.trim();
    }
}
