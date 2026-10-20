package com.geoprobe.common;

import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.mapper.LlmCallLogMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmCallLoggingListenerTest {

    @Mock
    private LlmCallLogMapper llmCallLogMapper;

    @AfterEach
    void tearDown() {
        LlmCallContext.clear();
        LlmCallMetricsHolder.clear();
    }

    @Test
    void shouldPersistSuccessLogWithTokenUsageAndLatency() {
        LlmCallLoggingListener listener = new LlmCallLoggingListener(llmCallLogMapper);
        LlmCallContext.set(new LlmCallContext("diag-1", 1, "deepseek"));

        ChatModelRequest request = ChatModelRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(UserMessage.from("hello")))
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, attributes));

        ChatModelResponse response = ChatModelResponse.builder()
                .id("resp-1")
                .model("deepseek-chat")
                .tokenUsage(new TokenUsage(12, 34, 46))
                .finishReason(FinishReason.LENGTH)
                .aiMessage(AiMessage.from("ok"))
                .build();
        listener.onResponse(new ChatModelResponseContext(response, request, attributes));

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogMapper).insert(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertEquals("diag-1", saved.getDiagnosisId());
        assertEquals(Integer.valueOf(1), saved.getModule());
        assertEquals("deepseek", saved.getProvider());
        assertEquals("deepseek-chat", saved.getModel());
        assertEquals(Integer.valueOf(12), saved.getInputTokens());
        assertEquals(Integer.valueOf(34), saved.getOutputTokens());
        assertEquals(new BigDecimal("0.000126"), saved.getCostYuan());
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertNotNull(saved.getLatencyMs());
        assertTrue(saved.getLatencyMs() >= 0);

        LlmCallMetrics metrics = LlmCallMetricsHolder.get();
        assertNotNull(metrics);
        assertEquals(Integer.valueOf(12), metrics.inputTokens());
        assertEquals(Integer.valueOf(34), metrics.outputTokens());
        assertEquals("LENGTH", metrics.finishReason());
        assertEquals(Integer.valueOf(2), metrics.responseCharLen());
    }

    @Test
    void shouldPersistTimeoutAsStatusTimeout() {
        LlmCallLoggingListener listener = new LlmCallLoggingListener(llmCallLogMapper);
        LlmCallContext.set(new LlmCallContext("diag-2", 1, "deepseek"));

        ChatModelRequest request = ChatModelRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(UserMessage.from("hello")))
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, attributes));

        listener.onError(new ChatModelErrorContext(new TimeoutException("Timeout after 10s"), request, null, attributes));

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogMapper).insert(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertEquals(Integer.valueOf(3), saved.getStatus());
        assertNull(saved.getCostYuan());
        assertNotNull(saved.getErrorMessage());
        assertTrue(saved.getErrorMessage().contains("Timeout"));

        LlmCallMetrics metrics = LlmCallMetricsHolder.get();
        assertNotNull(metrics);
        assertEquals(Integer.valueOf(3), metrics.status());
        assertEquals("TIMEOUT", metrics.finishReason());
    }
}
