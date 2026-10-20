package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record SimulationIntentProfile(
        String surfaceIntent,
        String interactionPosture,
        String conversationMode,
        String brandFraming,
        String naturalnessTarget,
        String languageDegradationLevel
) {

    public SimulationIntentProfile {
        surfaceIntent = normalizeRequired(surfaceIntent, "surfaceIntent 不能为空");
        interactionPosture = normalizeRequired(interactionPosture, "interactionPosture 不能为空");
        conversationMode = normalizeRequired(conversationMode, "conversationMode 不能为空");
        brandFraming = normalizeRequired(brandFraming, "brandFraming 不能为空");
        naturalnessTarget = normalizeRequired(naturalnessTarget, "naturalnessTarget 不能为空");
        languageDegradationLevel = normalizeRequired(languageDegradationLevel, "languageDegradationLevel 不能为空");
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
