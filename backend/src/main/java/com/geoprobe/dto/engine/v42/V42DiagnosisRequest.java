package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record V42DiagnosisRequest(
        String taskId,
        String reportId,
        String engineVersion,
        FrontDeskTaskOrder taskOrder,
        EngineTaskContextPackage contextPackage,
        List<String> brandAliases,
        Map<String, List<String>> competitorAliases
) {

    public V42DiagnosisRequest {
        taskId = normalizeRequired(taskId, "taskId 不能为空");
        reportId = normalizeOptional(reportId);
        engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
        if (taskOrder == null) {
            throw new IllegalArgumentException("taskOrder 不能为空");
        }
        if (contextPackage == null) {
            throw new IllegalArgumentException("contextPackage 不能为空");
        }
        brandAliases = immutableDistinctList(brandAliases);
        competitorAliases = immutableAliasMap(competitorAliases);
        validateLayerConsistency(taskOrder, contextPackage);
    }

    public String brandName() {
        return taskOrder.brandName();
    }

    public String category() {
        return taskOrder.category();
    }

    public List<String> competitors() {
        return taskOrder.competitors();
    }

    public List<String> categoryAnchors() {
        return contextPackage.categoryAnchors();
    }

    public boolean fallbackAnchorMode() {
        return contextPackage.fallbackAnchorMode();
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
            List<String> aliases = immutableDistinctList(entry.getValue());
            sanitized.put(key, aliases);
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static void validateLayerConsistency(FrontDeskTaskOrder taskOrder,
                                                 EngineTaskContextPackage contextPackage) {
        if (!taskOrder.brandName().equals(contextPackage.brandName())) {
            throw new IllegalArgumentException("taskOrder.brandName 与 contextPackage.brandName 必须一致");
        }
        if (!taskOrder.category().equals(contextPackage.category())) {
            throw new IllegalArgumentException("taskOrder.category 与 contextPackage.category 必须一致");
        }
        if (!taskOrder.competitors().equals(contextPackage.competitors())) {
            throw new IllegalArgumentException("taskOrder.competitors 与 contextPackage.competitors 必须一致");
        }
    }
}
