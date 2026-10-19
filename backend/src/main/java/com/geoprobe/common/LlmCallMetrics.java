package com.geoprobe.common;

public record LlmCallMetrics(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer latencyMs,
        String finishReason,
        Integer responseCharLen,
        Integer status,
        String errorMessage
) {
}
