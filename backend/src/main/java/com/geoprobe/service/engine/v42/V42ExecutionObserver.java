package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public interface V42ExecutionObserver {

    V42ExecutionObserver NO_OP = new V42ExecutionObserver() {
    };

    default void onStageCompleted(StageTrace trace) {
    }

    default void onExecutionFailed(FailureTrace trace) {
    }

    default void onRealtimeEvent(RealtimeEventTrace trace) {
    }

    record StageTrace(
            String stage,
            long durationMillis,
            Map<String, Object> summary
    ) {

        public StageTrace {
            stage = normalizeRequired(stage, "stage 不能为空");
            if (durationMillis < 0L) {
                throw new IllegalArgumentException("durationMillis 不能为负数");
            }
            summary = immutableObjectMap(summary);
        }
    }

    record FailureTrace(
            String stage,
            long elapsedMillis,
            String errorType,
            String message
    ) {

        public FailureTrace {
            stage = normalizeRequired(stage, "stage 不能为空");
            if (elapsedMillis < 0L) {
                throw new IllegalArgumentException("elapsedMillis 不能为负数");
            }
            errorType = normalizeRequired(errorType, "errorType 不能为空");
            message = normalizeRequired(message, "message 不能为空");
        }
    }

    record RealtimeEventTrace(
            String eventType,
            boolean provisional,
            Map<String, Object> payload
    ) {

        public RealtimeEventTrace {
            eventType = normalizeRequired(eventType, "eventType 不能为空");
            payload = immutableObjectMap(payload);
        }
    }

    private static String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "summary key 不能为空");
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            sanitized.put(key, value);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
