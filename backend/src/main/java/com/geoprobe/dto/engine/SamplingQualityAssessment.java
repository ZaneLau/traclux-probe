package com.geoprobe.dto.engine;

import java.util.List;
import java.util.Map;

public record SamplingQualityAssessment(
        List<String> warnings,
        Map<String, Integer> voiceTypeDistribution,
        Map<String, Object> brandAnchoringStats,
        boolean samplingVariantShortfall,
        int expectedSamples,
        int generatedSamples,
        int missingSamples,
        Map<String, Integer> shortfallByVoiceType,
        Map<String, Integer> shortfallByCorePrompt
) {

    public SamplingQualityAssessment(List<String> warnings,
                                     Map<String, Integer> voiceTypeDistribution,
                                     Map<String, Object> brandAnchoringStats) {
        this(warnings, voiceTypeDistribution, brandAnchoringStats, false, 0, 0, 0, Map.of(), Map.of());
    }

    public SamplingQualityAssessment {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        voiceTypeDistribution = voiceTypeDistribution == null ? Map.of() : Map.copyOf(voiceTypeDistribution);
        brandAnchoringStats = brandAnchoringStats == null ? Map.of() : Map.copyOf(brandAnchoringStats);
        expectedSamples = Math.max(0, expectedSamples);
        generatedSamples = Math.max(0, generatedSamples);
        missingSamples = Math.max(0, missingSamples);
        shortfallByVoiceType = shortfallByVoiceType == null ? Map.of() : Map.copyOf(shortfallByVoiceType);
        shortfallByCorePrompt = shortfallByCorePrompt == null ? Map.of() : Map.copyOf(shortfallByCorePrompt);
    }
}
