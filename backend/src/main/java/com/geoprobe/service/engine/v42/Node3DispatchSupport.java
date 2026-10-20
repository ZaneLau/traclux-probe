package com.geoprobe.service.engine.v42;

import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.common.SensitiveDataSanitizer;
import org.springframework.util.StringUtils;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

final class Node3DispatchSupport {

    private static final int MAX_FAILURE_REASON_CODE_LENGTH = 64;

    private Node3DispatchSupport() {
    }

    static int resolveMaxConcurrency(int maxConcurrency) {
        return Math.max(1, maxConcurrency);
    }

    static int resolveMaxAttempts(int configuredValue) {
        return Math.max(1, configuredValue);
    }

    static double normalizeCongestionRatio(double configuredValue, double defaultValue) {
        if (Double.isNaN(configuredValue) || Double.isInfinite(configuredValue)) {
            return defaultValue;
        }
        return Math.max(0.05d, Math.min(0.95d, configuredValue));
    }

    static long resolveConfiguredTaskTimeoutSeconds(long configuredValue) {
        if (configuredValue <= 0L) {
            return 0L;
        }
        return Math.max(10L, configuredValue);
    }

    static long[] resolveRetryBackoffMillis(String configuredValue, String defaultSpec) {
        if (!StringUtils.hasText(configuredValue)) {
            return parseRetryBackoff(defaultSpec);
        }
        long[] values = parseRetryBackoff(configuredValue);
        return values.length == 0 ? parseRetryBackoff(defaultSpec) : values;
    }

    static long[] parseRetryBackoff(String spec) {
        List<Long> values = new ArrayList<>();
        for (String part : spec.split(",")) {
            String token = part == null ? "" : part.trim();
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                long value = Long.parseLong(token);
                if (value >= 0L) {
                    values.add(value);
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid values and fallback to defaults if none remain
            }
        }
        long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    static String resolveFailureReasonCode(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN_ERROR";
        }
        if (containsType(throwable, LlmTokenBudgetExceededException.class)) {
            return "TOKEN_BUDGET_EXCEEDED";
        }
        if (throwable instanceof ProviderDispatchException providerDispatchException
                && StringUtils.hasText(providerDispatchException.errorCode())) {
            return fitFailureReasonCode(providerDispatchException.errorCode());
        }
        if (containsMessage(throwable, "采样任务超时")) {
            return "PROBE_TASK_TIMEOUT";
        }
        if (containsMessage(throwable, "模型调用执行器已过载")) {
            return "PROBE_EXECUTOR_OVERLOADED";
        }
        if (containsMessage(throwable, "采样线程被中断", "调用被中断")) {
            return "PROBE_TASK_INTERRUPTED";
        }
        if (containsType(throwable, CancellationException.class) || containsMessage(throwable, "采样任务被取消")) {
            return "PROBE_TASK_CANCELLED";
        }
        if (containsType(throwable, TimeoutException.class)
                || containsType(throwable, InterruptedIOException.class)
                || containsMessage(throwable, "timeout", "timed out", "超时", "stream was reset: cancel")) {
            return "UPSTREAM_PROVIDER_TIMEOUT";
        }
        return "UPSTREAM_PROVIDER_ERROR";
    }

    static boolean isTokenBudgetFailure(Throwable throwable) {
        return containsType(throwable, LlmTokenBudgetExceededException.class)
                || "TOKEN_BUDGET_EXCEEDED".equals(resolveFailureReasonCode(throwable));
    }

    static boolean isSystemicProviderFailure(Throwable throwable) {
        return "UPSTREAM_PROVIDER_ACCOUNT_OVERDUE".equals(resolveFailureReasonCode(throwable))
                || containsMessage(throwable, "accountoverdueerror", "overdue balance");
    }

    static String describeFailure(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN_ERROR";
        }
        String message = throwable.getMessage();
        if (StringUtils.hasText(message)) {
            return SensitiveDataSanitizer.sanitize(message.trim());
        }
        return throwable.getClass().getSimpleName();
    }

    static String extractThrowableMessage(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN_ERROR";
        }
        String message = throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            return throwable.getClass().getSimpleName();
        }
        return SensitiveDataSanitizer.sanitize(message.trim());
    }

    static String resolveProviderLabel(ModelAdapter.DispatchControl dispatchControl, String defaultProvider) {
        if (dispatchControl == null) {
            return defaultProvider;
        }
        String provider = StringUtils.hasText(dispatchControl.targetProvider())
                ? dispatchControl.targetProvider()
                : defaultProvider;
        if (dispatchControl.realtimeSearchEnabled()) {
            return provider + "_web_search";
        }
        return provider;
    }

    static long resolveEffectiveTaskTimeoutSeconds(ModelAdapter.DispatchControl dispatchControl,
                                                   long taskTimeoutSeconds,
                                                   long realtimeTaskTimeoutSeconds) {
        if (dispatchControl != null && dispatchControl.realtimeSearchEnabled()) {
            if (taskTimeoutSeconds <= 0L) {
                return realtimeTaskTimeoutSeconds;
            }
            if (realtimeTaskTimeoutSeconds <= 0L) {
                return taskTimeoutSeconds;
            }
            return Math.max(taskTimeoutSeconds, realtimeTaskTimeoutSeconds);
        }
        return taskTimeoutSeconds;
    }

    private static boolean containsType(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (targetType.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean containsMessage(Throwable throwable, String... needles) {
        if (throwable == null || needles == null || needles.length == 0) {
            return false;
        }
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (StringUtils.hasText(message)) {
                String normalized = message.trim().toLowerCase(Locale.ROOT);
                for (String needle : needles) {
                    if (StringUtils.hasText(needle) && normalized.contains(needle.trim().toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String fitFailureReasonCode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "UNKNOWN_ERROR";
        if (normalized.length() <= MAX_FAILURE_REASON_CODE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FAILURE_REASON_CODE_LENGTH);
    }
}
