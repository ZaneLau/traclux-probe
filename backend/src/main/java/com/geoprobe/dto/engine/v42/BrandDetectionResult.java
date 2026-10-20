package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BrandDetectionResult(
        boolean mentioned,
        String detectionLevel,
        String position,
        String sentiment,
        double strength,
        ProbeType probeType,
        int responseTokenCount,
        String responseLengthBucket,
        List<CompetitorMention> competitorsMentioned,
        List<String> ragSources,
        String thinkContent
) {

    private static final Set<String> ALLOWED_DETECTION_LEVELS = Set.of("L1_EXACT", "L2_ALIAS", "L3_SEMANTIC");
    private static final Set<String> ALLOWED_POSITIONS = Set.of("top1_recommendation", "comparison", "passing_mention", "negative");
    private static final Set<String> ALLOWED_SENTIMENTS = Set.of("positive", "neutral", "negative");
    private static final Set<String> ALLOWED_LENGTH_BUCKETS = Set.of("Short", "Medium", "Long");

    public BrandDetectionResult {
        detectionLevel = normalizeOptional(detectionLevel);
        position = normalizeOptional(position);
        sentiment = normalizeOptional(sentiment);
        probeType = Objects.requireNonNull(probeType, "probeType 不能为空");
        if (responseTokenCount < 0) {
            throw new IllegalArgumentException("responseTokenCount 不能为负数");
        }
        responseLengthBucket = normalizeRequired(responseLengthBucket, "responseLengthBucket 不能为空");
        if (!ALLOWED_LENGTH_BUCKETS.contains(responseLengthBucket)) {
            throw new IllegalArgumentException("不支持的 responseLengthBucket: " + responseLengthBucket);
        }
        if (strength < 0.0d || strength > 1.0d) {
            throw new IllegalArgumentException("strength 必须在 0.0 到 1.0 之间");
        }
        competitorsMentioned = immutableList(competitorsMentioned, "competitorsMentioned 不能为空");
        ragSources = immutableStringList(ragSources, "ragSources 不能为空");
        thinkContent = thinkContent == null ? "" : thinkContent.trim();

        if (mentioned) {
            if (!ALLOWED_DETECTION_LEVELS.contains(detectionLevel)) {
                throw new IllegalArgumentException("mentioned=true 时 detectionLevel 非法");
            }
            if (!ALLOWED_POSITIONS.contains(position)) {
                throw new IllegalArgumentException("mentioned=true 时 position 非法");
            }
            if (!ALLOWED_SENTIMENTS.contains(sentiment)) {
                throw new IllegalArgumentException("mentioned=true 时 sentiment 非法");
            }
        } else {
            if (detectionLevel != null || position != null || sentiment != null) {
                throw new IllegalArgumentException("mentioned=false 时不应填充 detectionLevel/position/sentiment");
            }
        }
    }

    public static BrandDetectionResult noMention(ProbeType probeType,
                                                 int responseTokenCount,
                                                 String responseLengthBucket,
                                                 List<CompetitorMention> competitorsMentioned,
                                                 List<String> ragSources,
                                                 String thinkContent) {
        return new BrandDetectionResult(
                false,
                null,
                null,
                null,
                0.0d,
                probeType,
                responseTokenCount,
                responseLengthBucket,
                competitorsMentioned,
                ragSources,
                thinkContent
        );
    }

    public static BrandDetectionResult detected(String detectionLevel,
                                                String position,
                                                String sentiment,
                                                double strength,
                                                ProbeType probeType,
                                                int responseTokenCount,
                                                String responseLengthBucket,
                                                List<CompetitorMention> competitorsMentioned,
                                                List<String> ragSources,
                                                String thinkContent) {
        return new BrandDetectionResult(
                true,
                detectionLevel,
                position,
                sentiment,
                strength,
                probeType,
                responseTokenCount,
                responseLengthBucket,
                competitorsMentioned,
                ragSources,
                thinkContent
        );
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

    private static <T> List<T> immutableList(List<T> values, String message) {
        Objects.requireNonNull(values, message);
        List<T> sanitized = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(message);
            }
            sanitized.add(value);
        }
        return List.copyOf(sanitized);
    }

    private static List<String> immutableStringList(List<String> values, String message) {
        Objects.requireNonNull(values, message);
        List<String> sanitized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(message);
            }
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }
}
