package com.geoprobe.dto.engine;

import org.springframework.util.StringUtils;

public record ConversationTurn(
        String role,
        String content
) {

    public ConversationTurn {
        role = normalizeRole(role);
        content = normalizeContent(content);
    }

    public static ConversationTurn user(String content) {
        return new ConversationTurn("user", content);
    }

    private static String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        if ("assistant".equals(normalized) || "system".equals(normalized) || "user".equals(normalized)) {
            return normalized;
        }
        return "user";
    }

    private static String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.trim();
    }
}
