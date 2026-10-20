package com.geoprobe.dto.engine.v42;

import java.util.Objects;
import java.util.Set;

public record DefenseCompletenessResult(
        String probeId,
        double evidenceScore,
        double comparisonScore,
        double conclusionScore,
        double compositeScore
) {

    private static final Set<Double> ALLOWED_RUBRIC_SCORES = Set.of(0.0d, 0.5d, 1.0d);

    public DefenseCompletenessResult {
        probeId = normalizeRequired(probeId, "probeId 不能为空");
        validateRubric(evidenceScore, "evidenceScore");
        validateRubric(comparisonScore, "comparisonScore");
        validateRubric(conclusionScore, "conclusionScore");
        validateUnitRate(compositeScore, "compositeScore");
    }

    public static DefenseCompletenessResult missing(String probeId) {
        return new DefenseCompletenessResult(probeId, 0.0d, 0.0d, 0.0d, 0.0d);
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static void validateRubric(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || !ALLOWED_RUBRIC_SCORES.contains(value)) {
            throw new IllegalArgumentException(fieldName + " 必须为 0 / 0.5 / 1");
        }
    }

    private static void validateUnitRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }
}
