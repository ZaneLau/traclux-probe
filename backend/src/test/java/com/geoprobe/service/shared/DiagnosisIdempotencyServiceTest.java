package com.geoprobe.service.shared;

import com.geoprobe.entity.Diagnosis;
import com.geoprobe.service.DiagnosisAccessService;
import com.geoprobe.service.RedisSupportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisIdempotencyServiceTest {

    @Test
    void shouldRecoverCachedDiagnosisThroughRuntimeAccessService() {
        DiagnosisAccessService diagnosisAccessService = mock(DiagnosisAccessService.class);
        RedisSupportService redisSupportService = mock(RedisSupportService.class);
        DiagnosisIdempotencyService service = new DiagnosisIdempotencyService(
                diagnosisAccessService,
                redisSupportService
        );

        when(redisSupportService.isEnabled()).thenReturn(false);
        when(diagnosisAccessService.findRuntimeReadByIdAndUserId("diag-runtime", "user-1")).thenReturn(
                Diagnosis.builder()
                        .id("diag-runtime")
                        .userId("user-1")
                        .brandName("探镜")
                        .build()
        );

        service.cacheResult("user-1", "req-1", "diag-runtime");
        Diagnosis diagnosis = service.findIdempotentDiagnosis("user-1", "req-1");

        assertEquals("diag-runtime", diagnosis.getId());
        assertEquals("user-1", diagnosis.getUserId());
        assertEquals("探镜", diagnosis.getBrandName());
    }

    @Test
    void shouldReturnNullWhenRuntimeAccessCannotResolveCachedDiagnosis() {
        DiagnosisAccessService diagnosisAccessService = mock(DiagnosisAccessService.class);
        RedisSupportService redisSupportService = mock(RedisSupportService.class);
        DiagnosisIdempotencyService service = new DiagnosisIdempotencyService(
                diagnosisAccessService,
                redisSupportService
        );

        when(redisSupportService.isEnabled()).thenReturn(false);
        when(diagnosisAccessService.findRuntimeReadByIdAndUserId("diag-missing", "user-1")).thenReturn(null);

        service.cacheResult("user-1", "req-2", "diag-missing");

        assertNull(service.findIdempotentDiagnosis("user-1", "req-2"));
    }
}
