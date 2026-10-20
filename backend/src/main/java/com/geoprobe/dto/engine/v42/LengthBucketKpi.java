package com.geoprobe.dto.engine.v42;

import java.util.Objects;
import java.util.Set;

public record LengthBucketKpi(
        String lengthLabel,
        int totalProbes,
        int openDiscoveryCount,
        int brandAssertiveCount,
        double spontaneousMentionRate,
        double confirmationResponseRate,
        double avgBrandsMentioned
) {

    private static final Set<String> ALLOWED_LABELS = Set.of("Short", "Medium", "Long");

    public LengthBucketKpi {
        lengthLabel = normalizeLabel(lengthLabel);
        validateCount(totalProbes, "totalProbes");
        validateCount(openDiscoveryCount, "openDiscoveryCount");
        validateCount(brandAssertiveCount, "brandAssertiveCount");
        validateRate(spontaneousMentionRate, "spontaneousMentionRate");
        validateRate(confirmationResponseRate, "confirmationResponseRate");
        validateRate(avgBrandsMentioned, "avgBrandsMentioned");
    }

    private static String normalizeLabel(String value) {
        String normalized = Objects.requireNonNull(value, "lengthLabel 不能为空").trim();
        if (!ALLOWED_LABELS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的 lengthLabel: " + value);
        }
        return normalized;
    }

    private static void validateCount(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " 不能为负数");
        }
    }

    private static void validateRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " 非法");
        }
    }
}
