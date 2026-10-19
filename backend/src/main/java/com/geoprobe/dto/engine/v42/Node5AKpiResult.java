package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record Node5AKpiResult(KpiSummary kpiSummary) {

    public Node5AKpiResult {
        kpiSummary = Objects.requireNonNull(kpiSummary, "kpiSummary 不能为空");
    }
}
