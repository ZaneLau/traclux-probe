package com.geoprobe.dto.engine;

import java.util.List;

public record VoiceTypeGenerationResult(
        List<VoiceType> rawVoiceTypes,
        List<VoiceType> voiceTypes,
        List<String> gateActions
) {

    public VoiceTypeGenerationResult {
        rawVoiceTypes = rawVoiceTypes == null ? List.of() : List.copyOf(rawVoiceTypes);
        voiceTypes = voiceTypes == null ? List.of() : List.copyOf(voiceTypes);
        gateActions = gateActions == null ? List.of() : List.copyOf(gateActions);
    }
}
