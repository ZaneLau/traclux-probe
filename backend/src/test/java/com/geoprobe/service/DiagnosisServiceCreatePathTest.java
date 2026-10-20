package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.ModelCapability;
import com.geoprobe.ai.ModelCapabilityRegistry;
import com.geoprobe.common.LlmRunAdmissionBlockedException;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.dto.response.DiagnosisResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.service.engine.v42.MinusOneContextAssemblyService;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import com.geoprobe.service.shared.DiagnosisCancellationRegistry;
import com.geoprobe.service.shared.DiagnosisIdempotencyService;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceCreatePathTest {

    @Mock
    private DiagnosisMapper diagnosisMapper;
    @Mock
    private CreditService creditService;
    @Mock
    private V42DiagnosisExecutionService v42DiagnosisExecutionService;
    @Mock
    private V42ReportProjectionService v42ReportProjectionService;
    @Mock
    private DiagnosisRuntimeQueryService diagnosisRuntimeQueryService;
    @Mock
    private DiagnosisRuntimeReadUseCaseService diagnosisRuntimeReadUseCaseService;
    @Mock
    private DiagnosisAccessService diagnosisAccessService;
    @Mock
    private MinusOneContextAssemblyService minusOneContextAssemblyService;
    @Mock
    private DiagnosisInferenceService diagnosisInferenceService;
    @Mock
    private DiagnosisEditionPreviewUseCaseService diagnosisEditionPreviewUseCaseService;
    @Mock
    private DiagnosisBillingService diagnosisBillingService;
    @Mock
    private DiagnosisCancellationRegistry diagnosisCancellationRegistry;
    @Mock
    private DiagnosisIdempotencyService diagnosisIdempotencyService;
    @Mock
    private FrontDeskPreviewTokenService frontDeskPreviewTokenService;
    @Mock
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Mock
    private LegacyDiagnosisMirrorService legacyDiagnosisMirrorService;
    @Mock
    private ModelCapabilityRegistry modelCapabilityRegistry;
    @Mock
    private VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    private DiagnosisService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        DiagnosisMapSupport diagnosisMapSupport = new DiagnosisMapSupport(objectMapper);
        DiagnosisSnapshotService diagnosisSnapshotService =
                new DiagnosisSnapshotService(diagnosisMapSupport, modelCapabilityRegistry);
        lenient().when(modelCapabilityRegistry.resolve(anyString(), anyString()))
                .thenReturn(new ModelCapability(true, true, true, true));
        lenient().when(minusOneContextAssemblyService.assemble(any(), any(), anyBoolean(), anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    var taskOrder = invocation.getArgument(0, com.geoprobe.dto.engine.v42.FrontDeskTaskOrder.class);
                    @SuppressWarnings("unchecked")
                    List<String> anchors = invocation.getArgument(1, List.class);
                    boolean fallbackAnchorMode = invocation.getArgument(2, Boolean.class);
                    String anchorSource = invocation.getArgument(3, String.class);
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
                            Map.of("assemblySource", "test")
                    );
                });
        when(diagnosisIdempotencyService.findIdempotentDiagnosis("user-1", "req-1")).thenReturn(null);
        when(diagnosisIdempotencyService.acquireLock("user-1", "req-1"))
                .thenReturn(new DiagnosisIdempotencyService.IdempotencyLock("lock", false, true));
        lenient().when(legacyDiagnosisMirrorService.shouldWriteLegacyDiagnosis()).thenReturn(false);
        lenient().when(legacyDiagnosisMirrorService.isLegacyDiagnosisWriteRequired()).thenReturn(false);
        lenient().when(diagnosisTaskNativeRuntimeService.ensureTaskShell(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString(), anyMap()
        )).thenAnswer(invocation -> DiagnosisTask.builder()
                .id(invocation.getArgument(0, String.class))
                .build());

        service = new DiagnosisService(
                diagnosisMapper,
                creditService,
                new V42TaskOrderExecutionPolicyResolver(
                        new V42TargetProbeCountPolicy(),
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
        ReflectionTestUtils.setField(service, "diagnosisTaskNativeRuntimeService", diagnosisTaskNativeRuntimeService);
        ReflectionTestUtils.setField(service, "legacyDiagnosisMirrorService", legacyDiagnosisMirrorService);
    }

    @Test
    void shouldDefaultCreatePathToTaskNativeWithoutLegacyDiagnosisInsert() {
        when(creditService.hasEnoughCredits("user-1", 1)).thenReturn(true);
        when(creditService.deductCredits(eq("user-1"), anyString(), eq(1))).thenReturn(true);

        DiagnosisResponse response = service.createDiagnosis("user-1", buildRequest());

        assertNotNull(response.getId());
        verify(diagnosisMapper, never()).insert(any(Diagnosis.class));
        verify(diagnosisBillingService).markDiagnosisBillingState(eq(response.getId()), eq("charged"), anyMap());
        verify(diagnosisTaskNativeRuntimeService).onTaskCreated(any(Diagnosis.class), any(), any(), eq("4.2"), anyMap());
        verify(v42DiagnosisExecutionService).enqueue(any(V42ExecutionPlan.class));
    }

    @Test
    void shouldContinueTaskNativeCreateWhenLegacyDiagnosisMirrorFails() {
        when(creditService.hasEnoughCredits("user-1", 1)).thenReturn(true);
        when(legacyDiagnosisMirrorService.shouldWriteLegacyDiagnosis()).thenReturn(true);
        when(legacyDiagnosisMirrorService.isLegacyDiagnosisWriteRequired()).thenReturn(false);
        when(diagnosisMapper.insert(any(Diagnosis.class))).thenThrow(new RuntimeException("legacy insert failed"));
        when(creditService.deductCredits(eq("user-1"), anyString(), eq(1))).thenReturn(true);

        DiagnosisResponse response = service.createDiagnosis("user-1", buildRequest());

        assertNotNull(response.getId());
        assertEquals(0, response.getStatus());
        verify(diagnosisBillingService).markDiagnosisBillingState(eq(response.getId()), eq("charged"), anyMap());
        verify(diagnosisTaskNativeRuntimeService).onTaskCreated(any(Diagnosis.class), any(), any(), eq("4.2"), anyMap());
        verify(diagnosisIdempotencyService).cacheResult("user-1", "req-1", response.getId());

        ArgumentCaptor<V42ExecutionPlan> planCaptor = ArgumentCaptor.forClass(V42ExecutionPlan.class);
        verify(v42DiagnosisExecutionService).enqueue(planCaptor.capture());
        assertEquals(response.getId(), planCaptor.getValue().diagnosisId());
        assertEquals("geo_brand_visibility_diagnosis", planCaptor.getValue().taskOrder().runGoal());
        assertEquals(10, ((Number) ((Map<?, ?>) planCaptor.getValue().taskOrder().businessHints().get("sampling"))
                .get("targetProbeCount")).intValue());
    }

    @Test
    void shouldStillSupportOptInLegacyDiagnosisInsert() {
        when(creditService.hasEnoughCredits("user-1", 1)).thenReturn(true);
        when(legacyDiagnosisMirrorService.shouldWriteLegacyDiagnosis()).thenReturn(true);
        when(diagnosisMapper.insert(any(Diagnosis.class))).thenReturn(1);
        when(creditService.deductCredits(eq("user-1"), anyString(), eq(1))).thenReturn(true);

        DiagnosisResponse response = service.createDiagnosis("user-1", buildRequest());

        assertNotNull(response.getId());
        verify(diagnosisMapper).insert(any(Diagnosis.class));
        verify(v42DiagnosisExecutionService).enqueue(any(V42ExecutionPlan.class));
    }

    @Test
    void shouldDefaultNewTaskToEngineVersion43() {
        when(creditService.hasEnoughCredits("user-1", 1)).thenReturn(true);
        when(creditService.deductCredits(eq("user-1"), anyString(), eq(1))).thenReturn(true);

        CreateDiagnosisFromTaskOrderRequest request = buildRequest();
        request.setEngineVersion(null);

        DiagnosisResponse response = service.createDiagnosis("user-1", request);

        assertNotNull(response.getId());
        ArgumentCaptor<V42ExecutionPlan> planCaptor = ArgumentCaptor.forClass(V42ExecutionPlan.class);
        verify(v42DiagnosisExecutionService).enqueue(planCaptor.capture());
        assertEquals("4.3", planCaptor.getValue().engineVersion());
        assertEquals("geo_brand_visibility_diagnosis", planCaptor.getValue().taskOrder().runGoal());
    }

    @Test
    void shouldStillRequireLegacyInsertWhenModeMarksItRequired() {
        when(creditService.hasEnoughCredits("user-1", 1)).thenReturn(true);
        when(legacyDiagnosisMirrorService.shouldWriteLegacyDiagnosis()).thenReturn(true);
        when(legacyDiagnosisMirrorService.isLegacyDiagnosisWriteRequired()).thenReturn(true);
        when(diagnosisMapper.insert(any(Diagnosis.class))).thenThrow(new RuntimeException("legacy insert failed"));

        assertThrows(RuntimeException.class, () -> service.createDiagnosis("user-1", buildRequest()));

        verify(diagnosisTaskNativeRuntimeService).removeTaskShell(anyString());
        verify(creditService, never()).deductCredits(anyString(), anyString(), eq(1));
        verify(v42DiagnosisExecutionService, never()).enqueue(any());
    }

    @Test
    void shouldChargeBrandSpaceTaskOrdersWhenBillingModeUsesUserCredits() {
        when(creditService.hasEnoughCredits("user-1", 10)).thenReturn(true);
        when(creditService.deductCredits(eq("user-1"), anyString(), eq(10))).thenReturn(true);

        DiagnosisResponse response = service.createDiagnosis("user-1", buildBrandSpaceRequest());

        assertNotNull(response.getId());
        verify(creditService).hasEnoughCredits("user-1", 10);
        verify(creditService).deductCredits(eq("user-1"), anyString(), eq(10));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(diagnosisBillingService).markDiagnosisBillingState(eq(response.getId()), eq("charged"), metadataCaptor.capture());
        assertEquals("user_credits", metadataCaptor.getValue().get("billingMode"));
        assertEquals(10, metadataCaptor.getValue().get("chargedCredits"));
        assertEquals(1, metadataCaptor.getValue().get("creditsPerProbe"));
        assertEquals(10, metadataCaptor.getValue().get("targetProbeCount"));
        verify(v42DiagnosisExecutionService).enqueue(any(V42ExecutionPlan.class));
    }

    @Test
    void shouldBlockCreateBeforeBillingWhenRewardGateRejectsTaskOrder() {
        org.mockito.Mockito.doThrow(new LlmRunAdmissionBlockedException("deepseek", "ep-deepseek", "snapshot stale"))
                .when(volcengineRewardRunAdmissionService)
                .requireTaskOrderReady(any(), anyString());

        assertThrows(LlmRunAdmissionBlockedException.class, () -> service.createDiagnosis("user-1", buildRequest()));

        verify(creditService, never()).hasEnoughCredits(anyString(), eq(1));
        verify(creditService, never()).deductCredits(anyString(), anyString(), eq(1));
        verify(v42DiagnosisExecutionService, never()).enqueue(any());
    }

    private CreateDiagnosisFromTaskOrderRequest buildRequest() {
        CreateDiagnosisFromTaskOrderRequest request = new CreateDiagnosisFromTaskOrderRequest();
        request.setEngineVersion("4.2");
        request.setRequestId("req-1");
        request.setFrontDeskTaskOrder(Map.of(
                "brandName", "任务制品牌",
                "category", "厨房收纳",
                "competitors", List.of("竞品甲"),
                "brandWebsite", "https://brand.example.com",
                "runGoal", "geo_brand_visibility_diagnosis",
                "locale", "zh-CN",
                "language", "zh-CN",
                "businessHints", Map.of(
                        "userPrompt", "关注真实创建链路",
                        "negativeTerms", List.of("无"),
                        "sampling", Map.of("targetProbeCount", 10)
                )
        ));
        return request;
    }

    private CreateDiagnosisFromTaskOrderRequest buildBrandSpaceRequest() {
        CreateDiagnosisFromTaskOrderRequest request = buildRequest();
        request.setFrontDeskTaskOrder(Map.of(
                "brandName", "任务制品牌",
                "category", "厨房收纳",
                "competitors", List.of("竞品甲"),
                "brandWebsite", "https://brand.example.com",
                "runGoal", "geo_brand_visibility_diagnosis",
                "locale", "zh-CN",
                "language", "zh-CN",
                "businessHints", Map.of(
                        "userPrompt", "关注真实创建链路",
                        "negativeTerms", List.of("无"),
                        "sampling", Map.of("targetProbeCount", 10),
                        "brandSpaceContext", Map.of(
                                "requestedProbeCount", 10,
                                "serviceAccess", Map.of(
                                        "creditsPerProbe", 1
                                )
                        )
                ),
                "taskMetadata", Map.of(
                        "source", "brand_space_schedule",
                        "origin", "brand_space",
                        "billingMode", "user_credits"
                )
        ));
        return request;
    }
}
