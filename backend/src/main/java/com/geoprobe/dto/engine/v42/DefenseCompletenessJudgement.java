package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DefenseCompletenessJudgement(
        Double evidence,
        Double comparison,
        Double conclusion
) {

    private static final Set<Double> ALLOWED_SCORES = Set.of(0.0d, 0.5d, 1.0d);

    public DefenseCompletenessJudgement {
        evidence = validateScore(evidence, "evidence");
        comparison = validateScore(comparison, "comparison");
        conclusion = validateScore(conclusion, "conclusion");
    }

    private static Double validateScore(Double value, String fieldName) {
        Double normalized = Objects.requireNonNull(value, fieldName + " 不能为空");
        if (Double.isNaN(normalized) || Double.isInfinite(normalized) || !ALLOWED_SCORES.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + " 必须为 0 / 0.5 / 1");
        }
        return normalized;
    }
}
