package com.geoprobe.dto.engine;

import java.util.List;

public record PromptMatrixResult(
        List<PromptItem> prompts,
        boolean fallbackUsed,
        List<String> fallbackClusterIds,
        String source,
        List<VoiceType> rawVoiceTypes,
        List<VoiceType> gatedVoiceTypes,
        List<String> voiceTypeGateActions
) {

    public PromptMatrixResult(List<PromptItem> prompts) {
        this(prompts, false, List.of(), "LLM", List.of(), List.of(), List.of());
    }

    public PromptMatrixResult(List<PromptItem> prompts,
                              boolean fallbackUsed,
                              List<String> fallbackClusterIds,
                              String source) {
        this(prompts, fallbackUsed, fallbackClusterIds, source, List.of(), List.of(), List.of());
    }

    public PromptMatrixResult {
        fallbackClusterIds = fallbackClusterIds == null ? List.of() : List.copyOf(fallbackClusterIds);
        rawVoiceTypes = rawVoiceTypes == null ? List.of() : List.copyOf(rawVoiceTypes);
        gatedVoiceTypes = gatedVoiceTypes == null ? List.of() : List.copyOf(gatedVoiceTypes);
        voiceTypeGateActions = voiceTypeGateActions == null ? List.of() : List.copyOf(voiceTypeGateActions);
    }
}
