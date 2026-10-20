package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.service.shared.CreativeContextService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisInferenceServiceTest {

    @Test
    void shouldRejectNonLlmInferenceSource() {
        CreativeContextService creativeContextService = mock(CreativeContextService.class);
        RedisSupportService redisSupportService = mock(RedisSupportService.class);
        when(creativeContextService.infer("洛斐", null, null)).thenReturn(
                new CreativeContextResult("洛斐", "机械键盘", List.of("Keychron"), List.of("二手"), null, "HEURISTIC")
        );
        DiagnosisInferenceService inferenceService = new DiagnosisInferenceService(creativeContextService, redisSupportService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                inferenceService.inferAndValidate("洛斐", null, null, "infer_context")
        );
        assertEquals(ErrorCode.LLM_CALL_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldMapTimeoutExceptionToLlmTimeout() {
        CreativeContextService creativeContextService = mock(CreativeContextService.class);
        RedisSupportService redisSupportService = mock(RedisSupportService.class);
        when(creativeContextService.infer("洛斐", null, null))
                .thenThrow(new RuntimeException("upstream timeout"));
        DiagnosisInferenceService inferenceService = new DiagnosisInferenceService(creativeContextService, redisSupportService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                inferenceService.inferAndValidate("洛斐", null, null, "infer_context")
        );
        assertEquals(ErrorCode.LLM_TIMEOUT, ex.getErrorCode());
    }
}
