package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.DiagnosisTaskNativeRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisAccessServiceTest {

    @Mock
    private DiagnosisMapper diagnosisMapper;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;

    private DiagnosisAccessService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosisAccessService(diagnosisMapper, diagnosisTaskMapper);
        ReflectionTestUtils.setField(service, "diagnosisTaskNativeRuntimeService", diagnosisTaskNativeRuntimeService);
    }

    @Test
    void shouldFallbackToTaskNativeViewWhenLegacyDiagnosisIsMissing() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisMapper.selectById("diag-task-only")).thenReturn(null);
        when(diagnosisTaskMapper.selectById("diag-task-only")).thenReturn(taskOnlyDiagnosis());

        Diagnosis diagnosis = service.getByIdAndUserId("diag-task-only", "user-1");

        assertEquals("diag-task-only", diagnosis.getId());
        assertEquals("品牌空间任务", diagnosis.getBrandName());
        assertEquals(List.of("竞品A"), diagnosis.getCompetitorsInput());
        assertEquals("来自任务快照的提示", diagnosis.getUserPrompt());
    }

    @Test
    void shouldReturnTaskOnlyDiagnosisWithoutLegacyReportProjection() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectById("diag-task-only")).thenReturn(taskOnlyDiagnosis());

        Diagnosis diagnosis = service.getReportReadByIdAndUserId("diag-task-only", "user-1");

        assertNotNull(diagnosis);
        assertNull(diagnosis.getReportData());
        assertEquals("品牌空间任务", diagnosis.getBrandName());
    }

    @Test
    void shouldFallbackToLegacyDiagnosisWhenTaskNativeFormalReadIsUnavailable() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(false);
        when(diagnosisMapper.selectById("diag-legacy")).thenReturn(Diagnosis.builder()
                .id("diag-legacy")
                .userId("user-1")
                .brandName("旧诊断")
                .build());

        Diagnosis diagnosis = service.getRuntimeReadByIdAndUserId("diag-legacy", "user-1");

        assertEquals("旧诊断", diagnosis.getBrandName());
    }

    @Test
    void shouldNotFallbackToLegacyDiagnosisForRuntimeReadWhenTaskNativeFormalReadIsAuthoritative() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectById("diag-legacy-only")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getRuntimeReadByIdAndUserId("diag-legacy-only", "user-1"));

        assertEquals(ErrorCode.DIAGNOSIS_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldNotFallbackToLegacyDiagnosisForReportReadWhenTaskNativeFormalReadIsAuthoritative() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectById("diag-legacy-only")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getReportReadByIdAndUserId("diag-legacy-only", "user-1"));

        assertEquals(ErrorCode.DIAGNOSIS_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldRejectTaskOnlyDiagnosisFromAnotherUser() {
        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisMapper.selectById("diag-task-only")).thenReturn(null);
        when(diagnosisTaskMapper.selectById("diag-task-only")).thenReturn(taskOnlyDiagnosis());

        assertThrows(BusinessException.class, () -> service.getByIdAndUserId("diag-task-only", "user-2"));
    }

    private DiagnosisTask taskOnlyDiagnosis() {
        return DiagnosisTask.builder()
                .id("diag-task-only")
                .requestedByUserId("user-1")
                .statusCode(0)
                .sourceCode(0)
                .brandNameSnapshot("品牌空间任务")
                .categorySnapshot("厨房收纳")
                .officialWebsiteSnapshot("https://brand.example.com")
                .taskOrderJson(Map.of(
                        "competitors", List.of("竞品A"),
                        "businessHints", Map.of("userPrompt", "来自任务快照的提示")
                ))
                .createdAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .build();
    }
}
