package com.geoprobe.dto.engine;

public record StabilityResult(
        double overallScore,
        double crossClusterConsistency,
        double withinClusterConsistency,
        double recommendStability
) {
}
