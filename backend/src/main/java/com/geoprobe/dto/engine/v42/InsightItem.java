package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightItem(
        String title,
        String evidence,
        String recommendation
) {

    private static final List<String> WEAK_WORDS = List.of("建议", "可能", "或许", "也许", "可以考虑", "尽量");
    private static final Pattern ACTIONABLE_PREFIX = Pattern.compile(
            "^(必须|立即|立刻|马上|全面|优先|重新|重写|重做|启动|发起|重构|强化|补齐|围绕|审查|优化|拆解|生产|分发|调整|建立|改写|扩展|统一).+"
    );

    public InsightItem {
        title = normalizeRequired(title, "title 不能为空");
        evidence = normalizeRequired(evidence, "evidence 不能为空");
        recommendation = normalizeRequired(recommendation, "recommendation 不能为空");
        if (!containsNumericSignal(evidence)) {
            throw new IllegalArgumentException("evidence 必须引用可量化 KPI");
        }
        if (containsWeakWords(recommendation)) {
            throw new IllegalArgumentException("recommendation 不能使用弱化措辞");
        }
        if (!isActionableRecommendation(recommendation)) {
            throw new IllegalArgumentException("recommendation 必须体现明确行动");
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static boolean containsNumericSignal(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWeakWords(String text) {
        return WEAK_WORDS.stream().anyMatch(text::contains);
    }

    private static boolean isActionableRecommendation(String text) {
        if (text.contains("必须") || text.contains("立即")) {
            return true;
        }
        return ACTIONABLE_PREFIX.matcher(text).matches();
    }
}
