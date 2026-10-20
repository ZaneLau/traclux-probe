package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.ModelCapabilityRegistry;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceCancellationTest {

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
    private com.geoprobe.common.VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;
    @Mock
    private ModelCapabilityRegistry modelCapabilityRegistry;

    private DiagnosisService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        DiagnosisMapSupport diagnosisMapSupport = new DiagnosisMapSupport(objectMapper);
        DiagnosisSnapshotService diagnosisSnapshotService =
                new DiagnosisSnapshotService(diagnosisMapSupport, modelCapabilityRegistry);
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
        when(diagnosisRuntimeReadUseCaseService.getDiagnosisProgress(anyString(), anyString()))
                .thenReturn(new DiagnosisProgressResponse());
    }

    @Test
    void shouldNotRefundCancellationWhenServiceAccessManagesBilling() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-1")
                .userId("user-1")
                .status(0)
                .isTrial(0)
                .build();
        when(diagnosisAccessService.getRuntimeReadByIdAndUserId("diag-1", "user-1")).thenReturn(diagnosis);
        when(diagnosisBillingService.shouldRefundOnCancellation("diag-1", diagnosis)).thenReturn(false);

        service.cancelDiagnosis("user-1", "diag-1");

        verify(diagnosisBillingService).shouldRefundOnCancellation("diag-1", diagnosis);
        verify(creditService, never()).refundCredits("user-1", "diag-1", 1);
        verify(diagnosisBillingService, never()).markDiagnosisBillingState("diag-1", "refunded");
    }

    @Test
    void shouldRefundCancellationWhenBillingStateIsCharged() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-2")
                .userId("user-1")
                .status(1)
                .isTrial(0)
                .build();
        when(diagnosisAccessService.getRuntimeReadByIdAndUserId("diag-2", "user-1")).thenReturn(diagnosis);
        when(diagnosisBillingService.shouldRefundOnCancellation("diag-2", diagnosis)).thenReturn(true);
        when(diagnosisBillingService.readChargedCredits("diag-2", diagnosis)).thenReturn(1);

        service.cancelDiagnosis("user-1", "diag-2");

        verify(diagnosisBillingService).shouldRefundOnCancellation("diag-2", diagnosis);
        verify(creditService).refundCredits("user-1", "diag-2", 1);
        verify(diagnosisBillingService).markDiagnosisBillingState("diag-2", "refunded");
    }
}
