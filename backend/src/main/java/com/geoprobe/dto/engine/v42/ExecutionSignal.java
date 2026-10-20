package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExecutionSignal(
        String code,
        String stage,
        String severity,
        String message,
        List<String> reasonCodes,
        Map<String, Object> details
) {

    public ExecutionSignal {
        code = normalizeRequired(code, "code 不能为空");
        stage = normalizeRequired(stage, "stage 不能为空");
        severity = normalizeRequired(severity, "severity 不能为空");
        message = normalizeRequired(message, "message 不能为空");
        reasonCodes = immutableStringList(reasonCodes);
        details = immutableObjectMap(details);
    }

    public ExecutionSignal(String code,
                           String stage,
                           String severity,
                           String message,
                           Map<String, Object> details) {
        this(code, stage, severity, message, List.of(), details);
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static List<String> immutableStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            sanitized.add(normalizeRequired(value, "reasonCodes 不能为空"));
        }
        return List.copyOf(sanitized);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "details key 不能为空");
            sanitized.put(key, Objects.requireNonNull(entry.getValue(), "details value 不能为空"));
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
