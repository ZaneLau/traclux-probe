package com.geoprobe.dto.engine;

import java.util.List;

public record VoiceTypeGateResult(
        List<VoiceType> voiceTypes,
        List<String> gateActions
) {

    public VoiceTypeGateResult {
        voiceTypes = voiceTypes == null ? List.of() : List.copyOf(voiceTypes);
        gateActions = gateActions == null ? List.of() : List.copyOf(gateActions);
    }
}
