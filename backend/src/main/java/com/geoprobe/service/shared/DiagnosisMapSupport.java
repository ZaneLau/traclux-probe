package com.geoprobe.service.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiagnosisMapSupport {

    private final ObjectMapper objectMapper;

    public DiagnosisMapSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sanitizeTaskOrderHints(Map<String, Object> taskOrderHints) {
        if (taskOrderHints == null || taskOrderHints.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        taskOrderHints.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            sanitized.put(key.trim(), sanitizeHintValue(value));
        });
        return sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeHintValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                if (!(nestedKey instanceof String key) || !StringUtils.hasText(key) || nestedValue == null) {
                    return;
                }
                nested.put(key.trim(), sanitizeHintValue(nestedValue));
            });
            return nested.isEmpty() ? Map.of() : Collections.unmodifiableMap(nested);
        }
        if (value instanceof List<?> list) {
            List<Object> nested = list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::sanitizeHintValue)
                    .toList();
            return List.copyOf(nested);
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return value;
    }

    public Map<String, Object> mergeMaps(Map<String, Object> primary, Map<String, Object> secondary) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return Map.of();
        }
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (primary != null) {
            merged.putAll(primary);
        }
        if (secondary != null) {
            secondary.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        return Map.copyOf(merged);
    }

    public Map<String, Object> mapOfNonNull(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues 必须成对出现");
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object rawKey = keyValues[index];
            Object rawValue = keyValues[index + 1];
            if (!(rawKey instanceof String key) || !StringUtils.hasText(key) || rawValue == null) {
                continue;
            }
            values.put(key.trim(), rawValue);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    public Map<String, Object> toSnapshotMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }
}
