package com.geoprobe.common;

import com.geoprobe.entity.LlmCallLog;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

public final class LlmManualCallLogger {

    private static final int STATUS_SUCCESS = 1;

    private LlmManualCallLogger() {
    }

    public static void record(Consumer<LlmCallLog> sink,
                              String fallbackProvider,
                              String modelId,
                              Integer inputTokens,
                              Integer outputTokens,
                              Integer latencyMs,
                              int status,
                              String errorMessage) {
        if (sink == null) {
            return;
        }
        LlmCallContext context = LlmCallContext.get();
        if (context == null || !StringUtils.hasText(context.getDiagnosisId()) || context.getModule() == null) {
            return;
        }
        String resolvedProvider = StringUtils.hasText(context.getProvider())
                ? context.getProvider().trim()
                : normalize(fallbackProvider);
        String resolvedModelId = normalize(modelId);
        LlmCallLog logEntity = LlmCallLog.builder()
                .diagnosisId(context.getDiagnosisId())
                .module(context.getModule())
                .provider(resolvedProvider)
                .model(resolvedModelId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .costYuan(status == STATUS_SUCCESS || inputTokens != null || outputTokens != null
                        ? LlmCostCalculator.estimateCostYuan(resolvedProvider, resolvedModelId, inputTokens, outputTokens)
                        : null)
                .latencyMs(latencyMs)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        try {
            sink.accept(logEntity);
        } catch (RuntimeException ignored) {
            // Keep runtime path fail-open when logging persistence is unavailable.
        }
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
