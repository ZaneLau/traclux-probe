package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import org.springframework.util.StringUtils;

import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

final class V42CreativeDegradationReasons {

    static final String TOKEN_BUDGET_EXCEEDED = "CREATIVE_TOKEN_BUDGET_EXCEEDED";
    static final String UPSTREAM_TIMEOUT = "CREATIVE_UPSTREAM_TIMEOUT";
    static final String OUTPUT_INVALID = "CREATIVE_OUTPUT_INVALID";
    static final String CONCURRENCY_FAILURE = "CREATIVE_CONCURRENCY_FAILURE";
    static final String UNKNOWN_FAILURE = "CREATIVE_UNKNOWN_FAILURE";

    private V42CreativeDegradationReasons() {
    }

    static String resolve(Throwable throwable) {
        if (containsCause(throwable, LlmTokenBudgetExceededException.class)) {
            return TOKEN_BUDGET_EXCEEDED;
        }
        if (containsTimeout(throwable)) {
            return UPSTREAM_TIMEOUT;
        }
        if (containsCause(throwable, JsonProcessingException.class)) {
            return OUTPUT_INVALID;
        }
        Throwable root = rootCause(throwable);
        if (root instanceof IllegalStateException || root instanceof IllegalArgumentException) {
            return OUTPUT_INVALID;
        }
        String message = normalize(root == null ? null : root.getMessage());
        if (message == null) {
            return UNKNOWN_FAILURE;
        }
        if (message.contains("token_budget_exceeded") || message.contains("预算已耗尽")) {
            return TOKEN_BUDGET_EXCEEDED;
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return UPSTREAM_TIMEOUT;
        }
        if (message.contains("json")
                || message.contains("输出")
                || message.contains("evidence")
                || message.contains("insightsource")
                || message.contains("validation")
                || message.contains("校验")) {
            return OUTPUT_INVALID;
        }
        return UNKNOWN_FAILURE;
    }

    static String resolveNode2(RenderedProbe probe) {
        if (probe == null || !probe.fallbackUsed()) {
            return null;
        }
        String reason = normalize(probe.renderReason());
        if (reason == null) {
            return UNKNOWN_FAILURE;
        }
        if (reason.contains("token_budget_exceeded") || reason.contains("budget_guard")) {
            return TOKEN_BUDGET_EXCEEDED;
        }
        if (reason.contains("timeout")) {
            return UPSTREAM_TIMEOUT;
        }
        if (reason.startsWith("concurrent_failure")) {
            return CONCURRENCY_FAILURE;
        }
        if (reason.contains("validation_failed") || reason.contains("template_validation_failed")) {
            return OUTPUT_INVALID;
        }
        return UNKNOWN_FAILURE;
    }

    static String describe(String reasonCode) {
        String normalized = normalize(reasonCode);
        if (TOKEN_BUDGET_EXCEEDED.toLowerCase(Locale.ROOT).equals(normalized)) {
            return "token budget 超限";
        }
        if (UPSTREAM_TIMEOUT.toLowerCase(Locale.ROOT).equals(normalized)) {
            return "上游调用超时";
        }
        if (OUTPUT_INVALID.toLowerCase(Locale.ROOT).equals(normalized)) {
            return "输出不合法或后验校验失败";
        }
        if (CONCURRENCY_FAILURE.toLowerCase(Locale.ROOT).equals(normalized)) {
            return "并发执行失败";
        }
        return "未知故障";
    }

    private static boolean containsTimeout(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof TimeoutException || cursor instanceof HttpTimeoutException) {
                return true;
            }
            String message = normalize(cursor.getMessage());
            if (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("超时"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (targetType.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
