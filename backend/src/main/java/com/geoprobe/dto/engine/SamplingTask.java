package com.geoprobe.dto.engine;

import org.springframework.util.StringUtils;

import java.util.List;

public record SamplingTask(
        String promptId,
        String clusterId,
        String promptText,
        double temperature,
        int repetition,
        List<ConversationTurn> conversation,
        String voiceTypeId,
        String voiceTypeName,
        boolean brandAnchored
) {

    public SamplingTask {
        conversation = normalizeConversation(conversation, promptText);
    }

    public SamplingTask(String promptId,
                        String clusterId,
                        String promptText,
                        double temperature,
                        int repetition) {
        this(promptId, clusterId, promptText, temperature, repetition, null, null, null, false);
    }

    public SamplingTask(String promptId,
                        String clusterId,
                        String promptText,
                        double temperature,
                        int repetition,
                        List<ConversationTurn> conversation) {
        this(promptId, clusterId, promptText, temperature, repetition, conversation, null, null, false);
    }

    private static List<ConversationTurn> normalizeConversation(List<ConversationTurn> conversation, String promptText) {
        if (conversation != null && !conversation.isEmpty()) {
            List<ConversationTurn> normalized = conversation.stream()
                    .filter(turn -> turn != null && StringUtils.hasText(turn.content()))
                    .toList();
            if (!normalized.isEmpty()) {
                return List.copyOf(normalized);
            }
        }
        return List.of(ConversationTurn.user(promptText));
    }
}
