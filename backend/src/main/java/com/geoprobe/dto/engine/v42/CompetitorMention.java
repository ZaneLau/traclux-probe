package com.geoprobe.dto.engine.v42;

import java.util.Objects;
import java.util.Set;

public record CompetitorMention(
        String competitorName,
        String detectionLevel,
        String sentiment,
        double strength
) {

    private static final Set<String> ALLOWED_DETECTION_LEVELS = Set.of("L1_EXACT", "L2_ALIAS");
    private static final Set<String> ALLOWED_SENTIMENTS = Set.of("positive", "neutral", "negative");

    public CompetitorMention {
        competitorName = normalizeRequired(competitorName, "competitorName 不能为空");
        detectionLevel = normalizeRequired(detectionLevel, "detectionLevel 不能为空");
        sentiment = normalizeRequired(sentiment, "sentiment 不能为空");
        if (!ALLOWED_DETECTION_LEVELS.contains(detectionLevel)) {
            throw new IllegalArgumentException("不支持的 competitor detectionLevel: " + detectionLevel);
        }
        if (!ALLOWED_SENTIMENTS.contains(sentiment)) {
            throw new IllegalArgumentException("不支持的 sentiment: " + sentiment);
        }
        if (strength < 0.0d || strength > 1.0d) {
            throw new IllegalArgumentException("strength 必须在 0.0 到 1.0 之间");
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
