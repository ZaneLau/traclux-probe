package com.geoprobe.dto.engine.v42;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Node3UsageCostSummary(
        long promptTokenTotal,
        long completionTokenTotal,
        long totalTokenTotal,
        int searchCallTotal,
        BigDecimal modelCostYuan,
        BigDecimal searchPluginCostYuan,
        BigDecimal totalCostYuan
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    public Node3UsageCostSummary {
        if (promptTokenTotal < 0L) {
            throw new IllegalArgumentException("promptTokenTotal 不能为负数");
        }
        if (completionTokenTotal < 0L) {
            throw new IllegalArgumentException("completionTokenTotal 不能为负数");
        }
        if (totalTokenTotal < 0L) {
            throw new IllegalArgumentException("totalTokenTotal 不能为负数");
        }
        if (searchCallTotal < 0) {
            throw new IllegalArgumentException("searchCallTotal 不能为负数");
        }
        modelCostYuan = normalizeCost(modelCostYuan, "modelCostYuan");
        searchPluginCostYuan = normalizeCost(searchPluginCostYuan, "searchPluginCostYuan");
        totalCostYuan = normalizeCost(totalCostYuan, "totalCostYuan");
    }

    public static Node3UsageCostSummary empty() {
        return new Node3UsageCostSummary(0L, 0L, 0L, 0, ZERO, ZERO, ZERO);
    }

    private static BigDecimal normalizeCost(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
