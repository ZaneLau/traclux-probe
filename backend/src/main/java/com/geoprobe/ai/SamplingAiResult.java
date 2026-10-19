package com.geoprobe.ai;

public record SamplingAiResult(
        boolean success,
        Integer status,
        String rawAnswer,
        Integer responseCharLen,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer reasoningTokens,
        Integer latencyMs,
        String finishReason,
        String model,
        String errorMessage
) {

    public static SamplingAiResult success(String rawAnswer,
                                           Integer responseCharLen,
                                           Integer promptTokens,
                                           Integer completionTokens,
                                           Integer totalTokens,
                                           Integer reasoningTokens,
                                           Integer latencyMs,
                                           String finishReason,
                                           String model) {
        return new SamplingAiResult(
                true,
                1,
                rawAnswer,
                responseCharLen,
                promptTokens,
                completionTokens,
                totalTokens,
                reasoningTokens,
                latencyMs,
                finishReason,
                model,
                null
        );
    }

    public static SamplingAiResult failed(Integer status,
                                          Integer latencyMs,
                                          String finishReason,
                                          String model,
                                          String errorMessage) {
        return new SamplingAiResult(
                false,
                status,
                null,
                0,
                null,
                null,
                null,
                null,
                latencyMs,
                finishReason,
                model,
                errorMessage
        );
    }
}
