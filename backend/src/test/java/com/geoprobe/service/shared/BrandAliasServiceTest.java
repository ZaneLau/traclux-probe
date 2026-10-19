package com.geoprobe.service.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.BrandAliasAiService;
import com.geoprobe.common.LlmCallContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandAliasServiceTest {

    @Test
    void shouldTagAliasGenerationWithDedicatedLlmModule() {
        BrandAliasAiService aiService = mock(BrandAliasAiService.class);
        when(aiService.generateAliases(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    LlmCallContext context = LlmCallContext.get();
                    assertEquals("diag-alias", context.getDiagnosisId());
                    assertEquals(4, context.getModule());
                    assertEquals("deepseek", context.getProvider());
                    return "[\"Alias\"]";
                });

        BrandAliasService service = new BrandAliasService(aiService, new ObjectMapper());

        DiagnosisAliasConfigData config = service.generateAliasConfig(
                "diag-alias",
                "鸿星尔克",
                "跑鞋",
                List.of()
        );

        assertEquals(List.of("Alias"), config.brandAliases());
        assertNull(LlmCallContext.get());
    }

    @Test
    void shouldParseJsonArrayWrappedByMarkdownFence() {
        BrandAliasAiService aiService = mock(BrandAliasAiService.class);
        when(aiService.generateAliases(anyString(), anyString(), anyString()))
                .thenReturn("```json\n[\"小米之家\", \"Mi Home\"]\n```");

        BrandAliasService service = new BrandAliasService(aiService, new ObjectMapper());

        DiagnosisAliasConfigData config = service.generateAliasConfig(
                "diag-alias-fence",
                "小米",
                "智能手机",
                List.of()
        );

        assertEquals(List.of("小米之家", "Mi Home"), config.brandAliases());
    }

    @Test
    void shouldStopWaitingWhenAliasCallExceedsBudget() {
        BrandAliasAiService aiService = mock(BrandAliasAiService.class);
        when(aiService.generateAliases(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                    return "[\"iPhone\"]";
                });

        BrandAliasService service = new BrandAliasService(aiService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "aliasLlmTimeoutSeconds", 1L);

        long startedAt = System.nanoTime();
        DiagnosisAliasConfigData config = service.generateAliasConfig(
                "diag-alias-timeout",
                "苹果",
                "智能手机",
                List.of()
        );
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(List.of(), config.brandAliases());
        assertTrue(elapsedMs < 1400L, "alias timeout should stop blocking the diagnosis flow");
    }
}
