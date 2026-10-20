package com.geoprobe.dto.engine.v42;

public record CategoryAnchorSignal(
        String anchor,
        String source,
        Double confidence
) {

    public CategoryAnchorSignal {
        anchor = normalizeRequired(anchor, "anchor 不能为空");
        source = normalizeOptional(source);
        if (confidence != null && (confidence < 0.0d || confidence > 1.0d)) {
            throw new IllegalArgumentException("confidence 必须位于 0-1 之间");
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
