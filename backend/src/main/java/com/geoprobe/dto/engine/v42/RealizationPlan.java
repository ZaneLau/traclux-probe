package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record RealizationPlan(
        SimulationIntentProfile simulationIntentProfile,
        RealizationConstraints realizationConstraints,
        String realizationSeed,
        String debugSignature
) {

    public RealizationPlan {
        simulationIntentProfile = Objects.requireNonNull(simulationIntentProfile, "simulationIntentProfile 不能为空");
        realizationConstraints = Objects.requireNonNull(realizationConstraints, "realizationConstraints 不能为空");
        realizationSeed = normalizeRequired(realizationSeed, "realizationSeed 不能为空");
        debugSignature = normalizeRequired(debugSignature, "debugSignature 不能为空");
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
