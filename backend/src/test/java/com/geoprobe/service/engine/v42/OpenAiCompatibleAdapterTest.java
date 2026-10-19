package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.entity.LlmCallLog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDisableThinkingForProvidersThatSupportReasoningToggle() {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "glm",
                "test-key",
                "https://example.com/v1",
                "glm-4-7-251222",
                Duration.ofSeconds(30),
                null,
                1024,
                true,
                new ObjectMapper()
        );

        Map<String, Object> payload = adapter.buildRequestPayload("glm-4-7-251222", "test prompt", true);

        assertEquals(Map.of("type", "disabled"), payload.get("thinking"));
    }

    @Test
    void shouldNotInjectThinkingToggleWhenProviderDoesNotSupportIt() {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "deepseek",
                "test-key",
                "https://example.com/v1",
                "deepseek-v3.2",
                Duration.ofSeconds(30),
                null,
                1024,
                false,
                new ObjectMapper()
        );

        Map<String, Object> payload = adapter.buildRequestPayload("deepseek-v3.2", "test prompt", true);

        assertTrue(!payload.containsKey("thinking"));
    }

    @Test
    void shouldRespectReasoningEnabledOverride() {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "glm",
                "test-key",
                "https://example.com/v1",
                "glm-4-7-251222",
                Duration.ofSeconds(30),
                null,
                1024,
                true,
                new ObjectMapper()
        );

        Map<String, Object> payload = adapter.buildRequestPayload("glm-4-7-251222", "test prompt", false);

        assertTrue(!payload.containsKey("thinking"));
    }

    @Test
    void shouldPreserveUsageAndReasoningWhenFinalContentIsEmpty() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "glm",
                "test-key",
                "https://example.com/v1",
                "glm-4-7-251222",
                Duration.ofSeconds(30),
                null,
                1024,
                true,
                objectMapper
        );

        RawResponse response = adapter.parseChatCompletionResponse(objectMapper.readTree("""
                {
                  "model": "glm-4-7-251222",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "",
                        "reasoning_content": "推理已经消耗，但没有输出最终答案"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 123,
                    "completion_tokens": 456,
                    "total_tokens": 579
                  }
                }
                """), "glm-4-7-251222", 3456L);

        assertEquals("", response.fullText());
        assertEquals("推理已经消耗，但没有输出最终答案", response.thinkContent());
        assertEquals(123, response.promptTokenCount());
        assertEquals(456, response.completionTokenCount());
        assertEquals(579, response.totalTokenCount());
        assertEquals(3456L, response.latencyMs());
    }

    @Test
    void shouldMarkLengthFinishReasonAsEarlyStopped() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "chatgpt",
                "test-key",
                "https://example.com/v1",
                "gpt-5-mini",
                Duration.ofSeconds(30),
                null,
                4096,
                false,
                objectMapper
        );

        RawResponse response = adapter.parseChatCompletionResponse(objectMapper.readTree("""
                {
                  "model": "gpt-5-mini",
                  "choices": [
                    {
                      "finish_reason": "length",
                      "message": {
                        "role": "assistant",
                        "content": "回答到这里被长度截断"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 4096,
                    "total_tokens": 4196
                  }
                }
                """), "gpt-5-mini", 4567L);

        assertTrue(response.earlyStoppedFlag());
        assertEquals("回答到这里被长度截断", response.fullText());
        assertEquals(4096, response.completionTokenCount());
    }

    @Test
    void shouldPreferTypedOutputContentOverReasoningParts() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "chatgpt",
                "test-key",
                "https://example.com/v1",
                "gpt-5-mini",
                Duration.ofSeconds(30),
                null,
                4096,
                false,
                objectMapper
        );

        RawResponse response = adapter.parseChatCompletionResponse(objectMapper.readTree("""
                {
                  "model": "gpt-5-mini",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": [
                          {
                            "type": "reasoning",
                            "text": {
                              "value": "先比较一下参数和口碑"
                            }
                          },
                          {
                            "type": "output_text",
                            "text": {
                              "value": "更推荐小米 15，整体更均衡。"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 90,
                    "completion_tokens": 180,
                    "total_tokens": 270
                  }
                }
                """), "gpt-5-mini", 2345L);

        assertEquals("更推荐小米 15，整体更均衡。", response.fullText());
        assertEquals("先比较一下参数和口碑", response.thinkContent());
        assertEquals(270, response.totalTokenCount());
    }

    @Test
    void shouldRecordFailedNode3CallsForOpenAiCompatibleProviders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"invalid model\"}}".getBytes();
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        List<LlmCallLog> logs = new ArrayList<>();
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "deepseek",
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "deepseek-v3.2",
                Duration.ofSeconds(5),
                null,
                1024,
                false,
                objectMapper,
                logs::add
        );
        Node3ProbeInput probe = new Node3ProbeInput(
                "probe-1",
                BucketType.PARETO,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "测试提示词"
        );
        LlmCallContext.set(new LlmCallContext("diag-001", 3, "deepseek"));
        try {
            ProviderDispatchException exception = assertThrows(
                    ProviderDispatchException.class,
                    () -> adapter.sendProbe(
                            probe,
                            ModelAdapter.DispatchControl.withTargetEngine(false, "doubao", "deepseek", "deepseek-v3.2", true)
                    )
            );
            assertEquals("UPSTREAM_PROVIDER_ERROR", exception.errorCode());
            assertEquals(1, logs.size());
            assertEquals("diag-001", logs.getFirst().getDiagnosisId());
            assertEquals(3, logs.getFirst().getModule());
            assertEquals("deepseek", logs.getFirst().getProvider());
            assertEquals("deepseek-v3.2", logs.getFirst().getModel());
            assertEquals(2, logs.getFirst().getStatus());
        } finally {
            LlmCallContext.clear();
            server.stop(0);
        }
    }

    @Test
    void shouldClassifyAccountOverdueAsSystemicProviderFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = """
                    {"error":{"code":"AccountOverdueError","message":"The request failed because your account has an overdue balance."}}
                    """.getBytes();
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "doubao",
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "doubao-seed-2-0-pro-260215",
                Duration.ofSeconds(5),
                null,
                1024,
                false,
                objectMapper
        );
        Node3ProbeInput probe = new Node3ProbeInput(
                "probe-overdue",
                BucketType.PARETO,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "测试提示词"
        );

        try {
            ProviderDispatchException exception = assertThrows(
                    ProviderDispatchException.class,
                    () -> adapter.sendProbe(
                            probe,
                            ModelAdapter.DispatchControl.withTargetEngine(false, "doubao", "doubao", "doubao-seed-2-0-pro-260215", true)
                    )
            );
            assertEquals("UPSTREAM_PROVIDER_ACCOUNT_OVERDUE", exception.errorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStopBeforeDispatchWhenBudgetExceeded() throws Exception {
        AtomicBoolean requested = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requested.set(true);
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        List<LlmCallLog> logs = new ArrayList<>();
        LlmTokenBudgetService tokenBudgetService = mock(LlmTokenBudgetService.class);
        when(tokenBudgetService.reserveOrThrow("deepseek", "deepseek-v3.2"))
                .thenThrow(new LlmTokenBudgetExceededException("deepseek", "deepseek-v3.2", 100, 100));
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                "deepseek",
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "deepseek-v3.2",
                Duration.ofSeconds(5),
                null,
                1024,
                false,
                objectMapper,
                logs::add,
                tokenBudgetService
        );
        Node3ProbeInput probe = new Node3ProbeInput(
                "probe-2",
                BucketType.PARETO,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "测试提示词"
        );
        LlmCallContext.set(new LlmCallContext("diag-002", 3, "deepseek"));
        try {
            LlmTokenBudgetExceededException exception = assertThrows(
                    LlmTokenBudgetExceededException.class,
                    () -> adapter.sendProbe(
                            probe,
                            ModelAdapter.DispatchControl.withTargetEngine(false, "doubao", "deepseek", "deepseek-v3.2", true)
                    )
            );
            assertEquals("deepseek", exception.budgetProvider());
            assertFalse(requested.get());
            assertEquals(1, logs.size());
            assertEquals(2, logs.getFirst().getStatus());
            assertTrue(logs.getFirst().getErrorMessage().contains("预算已耗尽"));
        } finally {
            LlmCallContext.clear();
            server.stop(0);
        }
    }
}
