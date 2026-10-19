package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Node5CDashboardRequest(
        String diagnosisId,
        String brandName,
        List<Node4ProbeOutput> probes,
        Node5AKpiResult node5AKpiResult,
        CommercialInsights commercialInsights
) {

    public Node5CDashboardRequest(String brandName,
                                  List<Node4ProbeOutput> probes,
                                  Node5AKpiResult node5AKpiResult,
                                  CommercialInsights commercialInsights) {
        this(null, brandName, probes, node5AKpiResult, commercialInsights);
    }

    public Node5CDashboardRequest {
        diagnosisId = normalizeOptional(diagnosisId);
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        Objects.requireNonNull(probes, "probes 不能为空");
        List<Node4ProbeOutput> sanitized = new ArrayList<>(probes.size());
        for (Node4ProbeOutput probe : probes) {
            sanitized.add(Objects.requireNonNull(probe, "probes 不能包含 null"));
        }
        probes = List.copyOf(sanitized);
        node5AKpiResult = Objects.requireNonNull(node5AKpiResult, "node5AKpiResult 不能为空");
        commercialInsights = Objects.requireNonNull(commercialInsights, "commercialInsights 不能为空");
    }

    public KpiSummary kpiSummary() {
        return node5AKpiResult.kpiSummary();
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
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
}
