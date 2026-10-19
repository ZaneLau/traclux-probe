package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record Node3ProbeInput(
        String probeId,
        BucketType bucketType,
        Map<String, String> abstractTags,
        Map<String, String> translatedTags,
        RealizationPlan realizationPlan,
        ProbeExposurePolicy exposurePolicy,
        String renderedPrompt,
        String renderFamily,
        String renderReason,
        Map<String, Object> triggeredRulesJson,
        Node3SearchGroundingContext searchGroundingContext
) {

    public Node3ProbeInput {
        probeId = normalizeRequired(probeId, "probeId 不能为空");
        bucketType = Objects.requireNonNull(bucketType, "bucketType 不能为空");
        abstractTags = immutableOrderedMap(abstractTags, "abstractTags 不能为空");
        translatedTags = immutableOrderedMap(translatedTags, "translatedTags 不能为空");
        exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy 不能为空");
        renderedPrompt = normalizeRequired(renderedPrompt, "renderedPrompt 不能为空");
        renderFamily = normalizeRequired(renderFamily, "renderFamily 不能为空");
        renderReason = normalizeRequired(renderReason, "renderReason 不能为空");
        triggeredRulesJson = immutableObjectMap(triggeredRulesJson, "triggeredRulesJson 不能为空");
        searchGroundingContext = searchGroundingContext == null
                ? Node3SearchGroundingContext.empty()
                : searchGroundingContext;
    }

    public Node3ProbeInput(String probeId,
                           BucketType bucketType,
                           Map<String, String> abstractTags,
                           Map<String, String> translatedTags,
                           ProbeExposurePolicy exposurePolicy,
                           String renderedPrompt) {
        this(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                null,
                exposurePolicy,
                renderedPrompt,
                "default_render_family",
                "default_render",
                Map.of(),
                Node3SearchGroundingContext.empty()
        );
    }

    public static Node3ProbeInput fromRenderedProbe(RenderedProbe renderedProbe) {
        return fromRenderedProbe(renderedProbe, Node3SearchGroundingContext.empty());
    }

    public static Node3ProbeInput fromRenderedProbe(RenderedProbe renderedProbe,
                                                    Node3SearchGroundingContext searchGroundingContext) {
        Objects.requireNonNull(renderedProbe, "RenderedProbe 不能为空");
        LinkedHashMap<String, Object> trace = new LinkedHashMap<>(renderedProbe.triggeredRulesJson());
        trace.put("renderSource", renderedProbe.renderSource());
        trace.put("renderReason", renderedProbe.renderReason());
        trace.put("fallbackUsed", renderedProbe.fallbackUsed());
        return new Node3ProbeInput(
                renderedProbe.probeId(),
                renderedProbe.bucketType(),
                renderedProbe.abstractTags(),
                renderedProbe.translatedTags(),
                renderedProbe.realizationPlan(),
                renderedProbe.exposurePolicy(),
                renderedProbe.renderedPrompt(),
                renderedProbe.renderFamily(),
                renderedProbe.renderReason(),
                Map.copyOf(trace),
                searchGroundingContext
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
                throw new IllegalArgumentException("Node3ProbeInput tags 不能为空");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source, String message) {
        if (source == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(message);
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
