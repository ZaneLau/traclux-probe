package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record CompetitorKpi(
        String competitorName,
        int mentionCount,
        double responseShare,
        double positiveRate,
        double averageStrength
) {

    public CompetitorKpi {
        competitorName = normalizeRequired(competitorName, "competitorName 不能为空");
        if (mentionCount < 0) {
            throw new IllegalArgumentException("mentionCount 不能为负数");
        }
        validateUnitRate(responseShare, "responseShare");
        validateUnitRate(positiveRate, "positiveRate");
        validateUnitRate(averageStrength, "averageStrength");
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static void validateUnitRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }
}
