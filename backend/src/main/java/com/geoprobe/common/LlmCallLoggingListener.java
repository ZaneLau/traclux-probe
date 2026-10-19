package com.geoprobe.common;

import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.mapper.LlmCallLogMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class LlmCallLoggingListener implements ChatModelListener {

    private static final String START_NANOS_KEY = "geo.llm.startNanos";

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_TIMEOUT = 3;

    private final LlmCallLogMapper llmCallLogMapper;

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START_NANOS_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        LlmCallContext llmCallContext = LlmCallContext.get();
        if (llmCallContext == null) {
            return;
        }

        TokenUsage usage = context.response() != null ? context.response().tokenUsage() : null;
        String finishReason = extractFinishReason(context.response());
        Integer responseCharLen = extractResponseCharLen(context.response());
        Integer latencyMs = extractLatencyMs(context.attributes());
        LlmCallLog logEntity = LlmCallLog.builder()
                .diagnosisId(llmCallContext.getDiagnosisId())
                .module(llmCallContext.getModule())
                .provider(llmCallContext.getProvider())
                .model(context.request() != null ? context.request().model() : null)
                .inputTokens(usage != null ? usage.inputTokenCount() : null)
                .outputTokens(usage != null ? usage.outputTokenCount() : null)
                .costYuan(LlmCostCalculator.estimateCostYuan(
                        llmCallContext.getProvider(),
                        context.request() != null ? context.request().model() : null,
                        usage != null ? usage.inputTokenCount() : null,
                        usage != null ? usage.outputTokenCount() : null
                ))
                .latencyMs(latencyMs)
                .status(STATUS_SUCCESS)
                .build();

        LlmCallMetricsHolder.set(new LlmCallMetrics(
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null,
                usage != null ? usage.totalTokenCount() : null,
                latencyMs,
                finishReason,
                responseCharLen,
                STATUS_SUCCESS,
                null
        ));

        insertSafely(logEntity);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        LlmCallContext llmCallContext = LlmCallContext.get();
        if (llmCallContext == null) {
            return;
        }

        Throwable error = context.error();
        TokenUsage usage = context.partialResponse() != null ? context.partialResponse().tokenUsage() : null;
        String finishReason = extractFinishReason(context.partialResponse());
        Integer responseCharLen = extractResponseCharLen(context.partialResponse());
        Integer latencyMs = extractLatencyMs(context.attributes());
        int status = isTimeout(error) ? STATUS_TIMEOUT : STATUS_FAILED;
        String errorMessage = extractErrorMessage(error);
        LlmCallLog logEntity = LlmCallLog.builder()
                .diagnosisId(llmCallContext.getDiagnosisId())
                .module(llmCallContext.getModule())
                .provider(llmCallContext.getProvider())
                .model(context.request() != null ? context.request().model() : null)
                .inputTokens(usage != null ? usage.inputTokenCount() : null)
                .outputTokens(usage != null ? usage.outputTokenCount() : null)
                .costYuan(LlmCostCalculator.estimateCostYuan(
                        llmCallContext.getProvider(),
                        context.request() != null ? context.request().model() : null,
                        usage != null ? usage.inputTokenCount() : null,
                        usage != null ? usage.outputTokenCount() : null
                ))
                .latencyMs(latencyMs)
                .status(status)
                .errorMessage(errorMessage)
                .build();

        LlmCallMetricsHolder.set(new LlmCallMetrics(
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null,
                usage != null ? usage.totalTokenCount() : null,
                latencyMs,
                finishReason != null ? finishReason : (status == STATUS_TIMEOUT ? "TIMEOUT" : "ERROR"),
                responseCharLen,
                status,
                errorMessage
        ));

        insertSafely(logEntity);
    }

    private void insertSafely(LlmCallLog logEntity) {
        try {
            llmCallLogMapper.insert(logEntity);
        } catch (Exception ex) {
            log.warn("Failed to persist llm_call_logs entry: {}", ex.getMessage());
        }
    }

    private Integer extractLatencyMs(Map<Object, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        Object startValue = attributes.get(START_NANOS_KEY);
        if (!(startValue instanceof Long startNanos)) {
            return null;
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        return (int) (elapsedNanos / 1_000_000L);
    }

    private boolean isTimeout(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof TimeoutException) {
                return true;
            }
            String message = cursor.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase().contains("timeout")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String extractErrorMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : cursor.getClass().getSimpleName();
        return SensitiveDataSanitizer.sanitize(message);
    }

    private String extractFinishReason(ChatModelResponse response) {
        if (response == null) {
            return null;
        }
        FinishReason finishReason = response.finishReason();
        return finishReason != null ? finishReason.name() : null;
    }

    private Integer extractResponseCharLen(ChatModelResponse response) {
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        String text = response.aiMessage().text();
        if (text == null) {
            return null;
        }
        return text.length();
    }
}
