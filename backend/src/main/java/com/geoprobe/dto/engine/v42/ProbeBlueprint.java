package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ProbeBlueprint(
        String probeId,
        BucketType bucketType,
        Map<String, String> abstractTags,
        Map<String, String> translatedTags,
        String templateText,
        ProbeStatus status,
        RealizationPlan realizationPlan
) {

    public ProbeBlueprint {
        probeId = normalizeProbeId(probeId);
        bucketType = Objects.requireNonNull(bucketType, "bucketType 不能为空");
        abstractTags = immutableOrderedMap(abstractTags, "abstractTags 不能为空");
        translatedTags = immutableOrderedMap(translatedTags, "translatedTags 不能为空");
        templateText = templateText == null || templateText.isBlank() ? null : templateText.trim();
        status = status == null ? ProbeStatus.PENDING : status;
    }

    public ProbeBlueprint(String probeId,
                          BucketType bucketType,
                          Map<String, String> abstractTags,
                          Map<String, String> translatedTags,
                          String templateText,
                          ProbeStatus status) {
        this(probeId, bucketType, abstractTags, translatedTags, templateText, status, null);
    }

    private static String normalizeProbeId(String probeId) {
        String normalized = Objects.requireNonNull(probeId, "probeId 不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("probeId 不能为空");
        }
        return normalized;
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source, String message) {
        Objects.requireNonNull(source, message);
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("ProbeBlueprint tags 不能为空");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
