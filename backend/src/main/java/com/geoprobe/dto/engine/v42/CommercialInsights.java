package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommercialInsights(
        String headlineInsight,
        List<InsightItem> topThreeInsights,
        String insightSource,
        String fallbackReasonCode
) {

    private static final Set<String> ALLOWED_SOURCES = Set.of("LLM", "TEMPLATE_FALLBACK");

    public CommercialInsights {
        headlineInsight = normalizeRequired(headlineInsight, "headlineInsight 不能为空");
        insightSource = normalizeRequired(insightSource, "insightSource 不能为空");
        fallbackReasonCode = normalizeOptional(fallbackReasonCode);
        if (!ALLOWED_SOURCES.contains(insightSource)) {
            throw new IllegalArgumentException("不支持的 insightSource: " + insightSource);
        }
        Objects.requireNonNull(topThreeInsights, "topThreeInsights 不能为空");
        List<InsightItem> sanitized = new ArrayList<>(topThreeInsights.size());
        for (InsightItem item : topThreeInsights) {
            sanitized.add(Objects.requireNonNull(item, "topThreeInsights 不能包含 null"));
        }
        if (sanitized.size() != 3) {
            throw new IllegalArgumentException("topThreeInsights 必须固定为 3 条");
        }
        topThreeInsights = List.copyOf(sanitized);
    }

    public CommercialInsights(String headlineInsight,
                              List<InsightItem> topThreeInsights,
                              String insightSource) {
        this(headlineInsight, topThreeInsights, insightSource, null);
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
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
