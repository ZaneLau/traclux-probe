package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record BucketKpi(
        BucketType bucketType,
        int totalProbes,
        int effectiveSampleSize,
        int openDiscoveryCount,
        int brandAssertiveCount,
        double spontaneousMentionRate,
        double confirmationResponseRate,
        double overallMentionRate,
        double recommendRate,
        double positiveRate
) {

    public BucketKpi {
        bucketType = Objects.requireNonNull(bucketType, "bucketType 不能为空");
        validateCount(totalProbes, "totalProbes");
        validateCount(effectiveSampleSize, "effectiveSampleSize");
        validateCount(openDiscoveryCount, "openDiscoveryCount");
        validateCount(brandAssertiveCount, "brandAssertiveCount");
        validateUnitRate(spontaneousMentionRate, "spontaneousMentionRate");
        validateUnitRate(confirmationResponseRate, "confirmationResponseRate");
        validateUnitRate(overallMentionRate, "overallMentionRate");
        validateUnitRate(recommendRate, "recommendRate");
        validateUnitRate(positiveRate, "positiveRate");
    }

    private static void validateCount(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " 不能为负数");
        }
    }

    private static void validateUnitRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }
}
