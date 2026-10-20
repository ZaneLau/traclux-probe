package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DefenseCompletenessSummary(
        double averageCompositeScore,
        int evaluatedProbeCount,
        List<DefenseCompletenessResult> probeResults
) {

    public DefenseCompletenessSummary {
        validateUnitRate(averageCompositeScore, "averageCompositeScore");
        if (evaluatedProbeCount < 0) {
            throw new IllegalArgumentException("evaluatedProbeCount 不能为负数");
        }
        Objects.requireNonNull(probeResults, "probeResults 不能为空");
        List<DefenseCompletenessResult> sanitized = new ArrayList<>(probeResults.size());
        for (DefenseCompletenessResult result : probeResults) {
            sanitized.add(Objects.requireNonNull(result, "probeResults 不能包含 null"));
        }
        if (sanitized.size() != evaluatedProbeCount) {
            throw new IllegalArgumentException("evaluatedProbeCount 必须等于 probeResults.size()");
        }
        probeResults = List.copyOf(sanitized);
    }

    private static void validateUnitRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }
}
