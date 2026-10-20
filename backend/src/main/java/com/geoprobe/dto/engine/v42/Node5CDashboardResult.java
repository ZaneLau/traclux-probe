package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record Node5CDashboardResult(
        String brandName,
        KpiSummary kpiSummary,
        CommercialInsights commercialInsights,
        DefenseCompletenessSummary defenseCompleteness,
        DashboardCharts dashboardCharts
) {

    public Node5CDashboardResult {
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        kpiSummary = Objects.requireNonNull(kpiSummary, "kpiSummary 不能为空");
        commercialInsights = Objects.requireNonNull(commercialInsights, "commercialInsights 不能为空");
        defenseCompleteness = Objects.requireNonNull(defenseCompleteness, "defenseCompleteness 不能为空");
        dashboardCharts = Objects.requireNonNull(dashboardCharts, "dashboardCharts 不能为空");
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
