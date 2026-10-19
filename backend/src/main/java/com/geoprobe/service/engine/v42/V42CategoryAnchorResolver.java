package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Resolves V4.2 category anchors without relying on LLM generation.
 * Explicit frontend anchors remain the source of truth. When they are absent,
 * the backend derives a deterministic fallback pair from the category itself
 * so the current product entry can still execute the V4.2 engine safely.
 */
public final class V42CategoryAnchorResolver {

    private static final int MAX_CATEGORY_ANCHORS = 10;
    private static final int MIN_EXPLICIT_ANCHORS = 3;
    private static final int MIN_FALLBACK_ANCHORS = 2;
    private static final int MAX_FALLBACK_ANCHORS = 4;
    private static final String SOURCE_EXPLICIT_INPUT = "explicit_input";
    private static final String SOURCE_BACKEND_FALLBACK = "backend_fallback";
    private static final List<String> COMPACTABLE_CATEGORY_PREFIXES = List.of(
            "智能",
            "蓝牙",
            "无线",
            "有线",
            "家用",
            "商用",
            "专业",
            "便携",
            "手持",
            "电动",
            "多功能",
            "儿童",
            "婴儿",
            "宠物",
            "户外",
            "男装",
            "女装"
    );

    private V42CategoryAnchorResolver() {
    }

    public static ResolvedCategoryAnchors fromRequest(String category, List<String> requestedAnchors) {
        String normalizedCategory = normalizeRequired(category, "category 不能为空");
        List<String> normalizedExplicitAnchors = normalizeAnchors(requestedAnchors);
        if (!normalizedExplicitAnchors.isEmpty()) {
            validateCount(normalizedExplicitAnchors, MIN_EXPLICIT_ANCHORS);
            return new ResolvedCategoryAnchors(normalizedExplicitAnchors, false, SOURCE_EXPLICIT_INPUT);
        }
        List<String> fallbackAnchors = deriveFallbackAnchors(normalizedCategory);
        validateCount(fallbackAnchors, MIN_FALLBACK_ANCHORS);
        return new ResolvedCategoryAnchors(fallbackAnchors, true, SOURCE_BACKEND_FALLBACK);
    }

    public static ResolvedCategoryAnchors reconcile(String category,
                                                    List<String> currentAnchors,
                                                    boolean fallbackAnchorMode) {
        String normalizedCategory = normalizeRequired(category, "category 不能为空");
        List<String> normalizedAnchors = normalizeAnchors(currentAnchors);
        if (fallbackAnchorMode) {
            if (normalizedAnchors.isEmpty()) {
                List<String> fallbackAnchors = deriveFallbackAnchors(normalizedCategory);
                validateCount(fallbackAnchors, MIN_FALLBACK_ANCHORS);
                return new ResolvedCategoryAnchors(fallbackAnchors, true, SOURCE_BACKEND_FALLBACK);
            }
            validateCount(normalizedAnchors, MIN_FALLBACK_ANCHORS);
            return new ResolvedCategoryAnchors(normalizedAnchors, true, SOURCE_BACKEND_FALLBACK);
        }
        validateCount(normalizedAnchors, MIN_EXPLICIT_ANCHORS);
        return new ResolvedCategoryAnchors(normalizedAnchors, false, SOURCE_EXPLICIT_INPUT);
    }

    private static List<String> deriveFallbackAnchors(String category) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        expandAnchorVariants(category).forEach(anchor -> addAnchor(anchors, anchor));
        addAnchor(anchors, category + "品牌");
        addAnchor(anchors, category + "选购");
        return anchors.stream()
                .limit(MAX_FALLBACK_ANCHORS)
                .toList();
    }

    static List<String> expandAnchorVariants(String value) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return List.of();
        }
        addAnchor(variants, normalized);
        addAnchor(variants, normalized.replace("品牌", ""));
        addAnchor(variants, normalized.replace("选购", ""));
        addAnchor(variants, normalized.replace("包装", ""));
        addAnchor(variants, normalized.replace("品牌", "").replace("选购", "").replace("包装", ""));
        addAnchor(variants, extractSegmentTail(normalized));
        addAnchor(variants, extractCompactTail(normalized));
        return List.copyOf(variants);
    }

    private static String extractSegmentTail(String category) {
        String[] segments = category.split("[/、,，·•|\\-\\s]+");
        if (segments.length <= 1) {
            return null;
        }
        return normalizeOptional(segments[segments.length - 1]);
    }

    private static String extractCompactTail(String category) {
        String normalized = normalizeOptional(category);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .replace("品牌", "")
                .replace("选购", "")
                .replace("包装", "");
        for (String prefix : COMPACTABLE_CATEGORY_PREFIXES) {
            if (!normalized.startsWith(prefix) || normalized.length() <= prefix.length()) {
                continue;
            }
            String candidate = normalizeOptional(normalized.substring(prefix.length()));
            if (candidate != null && candidate.length() >= 2) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> normalizeAnchors(List<String> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String anchor : anchors) {
            String candidate = normalizeOptional(anchor);
            if (candidate == null) {
                continue;
            }
            normalized.add(candidate);
            if (normalized.size() >= MAX_CATEGORY_ANCHORS) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private static void validateCount(List<String> anchors, int minCount) {
        if (anchors.size() < minCount || anchors.size() > MAX_CATEGORY_ANCHORS) {
            throw new IllegalArgumentException(
                    "categoryAnchors 数量不合法，当前=%d，允许范围=%d-%d"
                            .formatted(anchors.size(), minCount, MAX_CATEGORY_ANCHORS)
            );
        }
    }

    private static void addAnchor(LinkedHashSet<String> collector, String value) {
        String candidate = normalizeOptional(value);
        if (candidate == null || collector.size() >= MAX_CATEGORY_ANCHORS) {
            return;
        }
        collector.add(candidate);
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record ResolvedCategoryAnchors(
            List<String> anchors,
            boolean fallbackAnchorMode,
            String source
    ) {
    }
}
