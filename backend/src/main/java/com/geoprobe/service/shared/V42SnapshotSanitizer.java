package com.geoprobe.service.shared;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class V42SnapshotSanitizer {

    private V42SnapshotSanitizer() {
    }

    public static Map<String, Object> sanitizeObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((rawKey, rawValue) -> {
            if (!StringUtils.hasText(rawKey)) {
                return;
            }
            Object normalized = sanitizeValue(rawValue);
            if (normalized != null) {
                sanitized.put(rawKey.trim(), normalized);
            }
        });
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    @SuppressWarnings("unchecked")
    public static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return sanitizeObjectMap((Map<String, Object>) nestedMap);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                Object normalized = sanitizeValue(item);
                if (normalized != null) {
                    sanitized.add(normalized);
                }
            }
            return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
        }
        return value;
    }
}
