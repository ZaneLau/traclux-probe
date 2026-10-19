package com.geoprobe.dto.engine;

import com.fasterxml.jackson.annotation.JsonAlias;

public record VoiceType(
        @JsonAlias({"id"}) String voiceTypeId,
        String name,
        String description,
        @JsonAlias({"example_prompt", "examplePrompt"}) String examplePrompt,
        @JsonAlias({"suggested_ratio", "suggestedRatio"}) double suggestedRatio,
        boolean brandAnchored
) {

    public VoiceType(String voiceTypeId,
                     String name,
                     String description,
                     String examplePrompt,
                     double suggestedRatio) {
        this(voiceTypeId, name, description, examplePrompt, suggestedRatio, false);
    }
}
