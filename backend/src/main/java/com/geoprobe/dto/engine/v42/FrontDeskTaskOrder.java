package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record FrontDeskTaskOrder(
        String brandName,
        String category,
        List<String> competitors,
        String brandWebsite,
        @JsonAlias("diagnosisGoal") String runGoal,
        String locale,
        String language,
        String targetModelProduct,
        Map<String, Object> businessHints,
        Map<String, Object> taskMetadata
) {

    public FrontDeskTaskOrder {
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        category = normalizeRequired(category, "category 不能为空");
        competitors = immutableDistinctList(competitors);
        brandWebsite = normalizeOptional(brandWebsite);
        runGoal = normalizeOptional(runGoal);
        locale = normalizeOptional(locale);
        language = normalizeOptional(language);
        targetModelProduct = normalizeOptional(targetModelProduct);
        businessHints = immutableObjectMap(businessHints);
        taskMetadata = immutableObjectMap(taskMetadata);
    }

    @JsonIgnore
    public String diagnosisGoal() {
        return runGoal;
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

    private static List<String> immutableDistinctList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
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
            String key = normalizeRequired(entry.getKey(), "map key 不能为空");
            Object value = entry.getValue();
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
