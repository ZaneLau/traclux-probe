package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record KpiSummary(
        double spontaneousMentionRate,
        double confirmationResponseRate,
        double overallMentionRate,
        LengthStratifiedKpi lengthStratifiedKpi,
        double recommendRate,
        double positiveRate,
        double stabilityScore,
        BucketKpi orthogonalKpi,
        BucketKpi paretoKpi,
        BucketKpi adversarialKpi,
        Map<String, Double> dimensionPenetration,
        Map<String, Double> dimensionRadarScores,
        Map<String, CompetitorKpi> competitorKpis,
        int effectiveSampleSize
) {

    public KpiSummary {
        validateUnitRate(spontaneousMentionRate, "spontaneousMentionRate");
        validateUnitRate(confirmationResponseRate, "confirmationResponseRate");
        validateUnitRate(overallMentionRate, "overallMentionRate");
        lengthStratifiedKpi = Objects.requireNonNull(lengthStratifiedKpi, "lengthStratifiedKpi 不能为空");
        validateUnitRate(recommendRate, "recommendRate");
        validateUnitRate(positiveRate, "positiveRate");
        if (Double.isNaN(stabilityScore) || Double.isInfinite(stabilityScore) || stabilityScore < 0.0d || stabilityScore > 100.0d) {
            throw new IllegalArgumentException("stabilityScore 必须在 0 到 100 之间");
        }
        orthogonalKpi = Objects.requireNonNull(orthogonalKpi, "orthogonalKpi 不能为空");
        paretoKpi = Objects.requireNonNull(paretoKpi, "paretoKpi 不能为空");
        adversarialKpi = Objects.requireNonNull(adversarialKpi, "adversarialKpi 不能为空");
        dimensionPenetration = immutableDoubleMap(dimensionPenetration, "dimensionPenetration 不能为空");
        dimensionRadarScores = immutableDoubleMap(dimensionRadarScores, "dimensionRadarScores 不能为空");
        competitorKpis = immutableCompetitorMap(competitorKpis);
        if (effectiveSampleSize < 0) {
            throw new IllegalArgumentException("effectiveSampleSize 不能为负数");
        }
    }

    private static void validateUnitRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source, String message) {
        Objects.requireNonNull(source, message);
        LinkedHashMap<String, Double> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), message);
            Double value = Objects.requireNonNull(entry.getValue(), message);
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException("非法 rate 值: " + key);
            }
            sanitized.put(key, value);
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static Map<String, CompetitorKpi> immutableCompetitorMap(Map<String, CompetitorKpi> source) {
        Objects.requireNonNull(source, "competitorKpis 不能为空");
        LinkedHashMap<String, CompetitorKpi> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, CompetitorKpi> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "competitorKpis key 不能为空");
            CompetitorKpi value = Objects.requireNonNull(entry.getValue(), "competitorKpis value 不能为空");
            sanitized.put(key, value);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
