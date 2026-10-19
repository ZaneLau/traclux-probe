package com.geoprobe.service.shared;

import com.geoprobe.ai.CreativeContextAiService;
import com.geoprobe.dto.engine.CreativeContextAiResult;
import com.geoprobe.dto.engine.CreativeContextResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreativeContextServiceTest {

    @Mock
    private CreativeContextAiService creativeContextAiService;

    @InjectMocks
    private CreativeContextService creativeContextService;

    @Test
    void shouldNormalizeInferenceOutput() {
        when(creativeContextAiService.inferContext(anyString(), anyString(), anyString()))
                .thenReturn(new CreativeContextAiResult(
                        "男装商务西裤",
                        List.of("九牧王", "雅戈尔", "海澜之家", "雅戈尔", "七匹狼", "报喜鸟", "罗蒙", "杉杉"),
                        List.of("不推荐", "质量差"),
                        "https://www.jiumuwang.com"
                ));

        CreativeContextResult result = creativeContextService.infer("九牧王", null, null);

        assertEquals("九牧王", result.brandName());
        assertEquals("男装商务西裤", result.category());
        assertEquals(5, result.competitors().size());
        assertTrue(result.competitors().contains("雅戈尔"));
        assertTrue(result.competitors().contains("海澜之家"));
        assertTrue(result.negativeTerms().size() >= 10);
        assertEquals("https://www.jiumuwang.com", result.officialWebsite());
        assertEquals("LLM", result.source());
    }

    @Test
    void shouldThrowAfterRetriesExhausted() {
        when(creativeContextAiService.inferContext(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream timeout"));

        assertThrows(com.geoprobe.common.BusinessException.class,
                () -> creativeContextService.infer("九牧王", null, null));
        verify(creativeContextAiService, times(3)).inferContext(anyString(), anyString(), anyString());
    }
}
