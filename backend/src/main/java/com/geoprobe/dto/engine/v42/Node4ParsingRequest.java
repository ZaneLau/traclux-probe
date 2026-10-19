package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Node4ParsingRequest(
        String diagnosisId,
        String brandName,
        List<String> brandAliases,
        List<String> competitors,
        Map<String, List<String>> competitorAliases,
        List<Node3ProbeOutput> probes
) {

    public Node4ParsingRequest {
        diagnosisId = normalizeOptional(diagnosisId);
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        brandAliases = immutableDistinctList(brandAliases);
        competitors = immutableDistinctList(competitors);
        competitorAliases = immutableAliasMap(competitorAliases);
        probes = immutableProbeList(probes);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static List<String> immutableDistinctList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Node4 list 不能包含 null");
            }
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
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
        return Map.copyOf(sanitized);
    }

    private static List<Node3ProbeOutput> immutableProbeList(List<Node3ProbeOutput> probes) {
        Objects.requireNonNull(probes, "probes 不能为空");
        List<Node3ProbeOutput> sanitized = new ArrayList<>(probes.size());
        for (Node3ProbeOutput probe : probes) {
            sanitized.add(Objects.requireNonNull(probe, "probes 不能包含 null"));
        }
        return List.copyOf(sanitized);
    }
}
