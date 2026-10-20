package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RenderedProbe(
        String probeId,
        BucketType bucketType,
        Map<String, String> abstractTags,
        Map<String, String> translatedTags,
        RealizationPlan realizationPlan,
        ProbeExposurePolicy exposurePolicy,
        String renderedPrompt,
        boolean fallbackUsed,
        String renderSource,
        String renderFamily,
        String renderReason,
        Map<String, Object> triggeredRulesJson
) {

    public RenderedProbe {
        probeId = normalizeRequired(probeId, "probeId 不能为空");
        bucketType = Objects.requireNonNull(bucketType, "bucketType 不能为空");
        abstractTags = immutableOrderedMap(abstractTags, "abstractTags 不能为空");
        translatedTags = immutableOrderedMap(translatedTags, "translatedTags 不能为空");
        exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy 不能为空");
        renderedPrompt = normalizeRequired(renderedPrompt, "renderedPrompt 不能为空");
        renderSource = normalizeRequired(renderSource, "renderSource 不能为空");
        renderFamily = normalizeRequired(renderFamily, "renderFamily 不能为空");
        renderReason = normalizeRequired(renderReason, "renderReason 不能为空");
        triggeredRulesJson = immutableObjectMap(triggeredRulesJson);
    }

    public RenderedProbe(String probeId,
                         BucketType bucketType,
                         Map<String, String> abstractTags,
                         Map<String, String> translatedTags,
                         RealizationPlan realizationPlan,
                         ProbeExposurePolicy exposurePolicy,
                         String renderedPrompt,
                         boolean fallbackUsed,
                         String renderSource,
                         String renderFamily,
                         String renderReason) {
        this(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                realizationPlan,
                exposurePolicy,
                renderedPrompt,
                fallbackUsed,
                renderSource,
                renderFamily,
                renderReason,
                Map.of()
        );
    }

    public RenderedProbe(String probeId,
                         BucketType bucketType,
                         Map<String, String> abstractTags,
                         Map<String, String> translatedTags,
                         ProbeExposurePolicy exposurePolicy,
                         String renderedPrompt,
                         boolean fallbackUsed,
                         String renderSource) {
        this(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                null,
                exposurePolicy,
                renderedPrompt,
                fallbackUsed,
                renderSource,
                "default_render_family",
                fallbackUsed ? "default_fallback" : "default_render",
                Map.of()
        );
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source, String message) {
        Objects.requireNonNull(source, message);
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("RenderedProbe tags 不能为空");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("RenderedProbe triggeredRulesJson 不能为空");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
