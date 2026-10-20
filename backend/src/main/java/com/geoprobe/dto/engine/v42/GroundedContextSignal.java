package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record GroundedContextSignal(
        String slot,
        String value,
        String source,
        String evidence,
        Double confidence,
        List<String> useFor,
        Map<String, Object> metadata
) {

    public GroundedContextSignal {
        slot = normalizeRequired(slot, "slot 不能为空");
        value = normalizeRequired(value, "value 不能为空");
        source = normalizeOptional(source);
        evidence = normalizeOptional(evidence);
        if (confidence != null && (confidence < 0.0d || confidence > 1.0d)) {
            throw new IllegalArgumentException("confidence 必须位于 0-1 之间");
        }
        useFor = immutableDistinctList(useFor);
        metadata = immutableObjectMap(metadata);
    }

    public GroundedContextSignal(String slot,
                                 String value,
                                 String source,
                                 String evidence,
                                 Double confidence,
                                 List<String> useFor) {
        this(slot, value, source, evidence, confidence, useFor, Map.of());
    }

    public boolean isUsedFor(String use) {
        String normalized = normalizeOptional(use);
        return normalized != null && useFor.contains(normalized);
    }

    public String dedupeKey() {
        return slot + "|" + value;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> immutableDistinctList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String value : source) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "metadata key 不能为空");
            Object value = entry.getValue();
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
