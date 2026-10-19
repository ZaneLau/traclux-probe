package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record V42ExecutionPlan(
        String diagnosisId,
        String engineVersion,
        String brandName,
        String category,
        List<String> competitors,
        List<String> categoryAnchors,
        boolean fallbackAnchorMode,
        List<String> brandAliases,
        Map<String, List<String>> competitorAliases,
        FrontDeskTaskOrder taskOrder,
        EngineTaskContextPackage contextPackage
) {

    public V42ExecutionPlan {
        diagnosisId = normalizeRequired(diagnosisId, "diagnosisId 不能为空");
        engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        category = normalizeRequired(category, "category 不能为空");
        competitors = immutableDistinctList(competitors);
        categoryAnchors = immutableDistinctList(categoryAnchors);
        brandAliases = immutableDistinctList(brandAliases);
        competitorAliases = immutableAliasMap(competitorAliases);
        if (taskOrder == null) {
            throw new IllegalArgumentException("taskOrder 不能为空");
        }
        if (contextPackage == null) {
            throw new IllegalArgumentException("contextPackage 不能为空");
        }
        validateLayerConsistency(brandName, category, taskOrder, contextPackage);
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

    private static Map<String, List<String>> immutableAliasMap(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "competitorAliases key 不能为空");
            sanitized.put(key, immutableDistinctList(entry.getValue()));
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static void validateLayerConsistency(String brandName,
                                                 String category,
                                                 FrontDeskTaskOrder taskOrder,
                                                 EngineTaskContextPackage contextPackage) {
        if (!brandName.equals(taskOrder.brandName())) {
            throw new IllegalArgumentException("executionPlan.brandName 与 taskOrder.brandName 必须一致");
        }
        if (!category.equals(taskOrder.category())) {
            throw new IllegalArgumentException("executionPlan.category 与 taskOrder.category 必须一致");
        }
        if (!brandName.equals(contextPackage.brandName())) {
            throw new IllegalArgumentException("executionPlan.brandName 与 contextPackage.brandName 必须一致");
        }
        if (!category.equals(contextPackage.category())) {
            throw new IllegalArgumentException("executionPlan.category 与 contextPackage.category 必须一致");
        }
    }
}
