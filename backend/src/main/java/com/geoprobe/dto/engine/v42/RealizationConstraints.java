package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RealizationConstraints(
        ProbeExposurePolicy brandExposurePolicy,
        List<String> mustExpressSemantics,
        List<String> softSemanticHints,
        String anchorPolicy,
        String lengthPolicy,
        String questionFormPolicy,
        String fallbackFamily
) {

    public RealizationConstraints {
        brandExposurePolicy = Objects.requireNonNull(brandExposurePolicy, "brandExposurePolicy 不能为空");
        mustExpressSemantics = immutableStringList(mustExpressSemantics, "mustExpressSemantics 不能为空");
        softSemanticHints = immutableStringList(softSemanticHints, "softSemanticHints 不能为空");
        anchorPolicy = normalizeRequired(anchorPolicy, "anchorPolicy 不能为空");
        lengthPolicy = normalizeRequired(lengthPolicy, "lengthPolicy 不能为空");
        questionFormPolicy = normalizeRequired(questionFormPolicy, "questionFormPolicy 不能为空");
        fallbackFamily = normalizeRequired(fallbackFamily, "fallbackFamily 不能为空");
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static List<String> immutableStringList(List<String> source, String message) {
        Objects.requireNonNull(source, message);
        List<String> sanitized = new ArrayList<>(source.size());
        for (String item : source) {
            sanitized.add(normalizeRequired(item, message));
        }
        return List.copyOf(sanitized);
    }
}
