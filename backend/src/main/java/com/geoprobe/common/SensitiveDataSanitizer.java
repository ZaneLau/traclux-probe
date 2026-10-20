package com.geoprobe.common;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}\\b");
    private static final Pattern JWT_PATTERN = Pattern.compile("\\beyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._-]{16,}");
    private static final Pattern UUID_KEY_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private SensitiveDataSanitizer() {
    }

    public static String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String sanitized = API_KEY_PATTERN.matcher(raw).replaceAll("sk-***");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("jwt-***");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        sanitized = UUID_KEY_PATTERN.matcher(sanitized).replaceAll("uuid-***");
        return sanitized;
    }

    public static String preview(String raw) {
        return preview(raw, 96);
    }

    public static String preview(String raw, int maxLength) {
        if (!StringUtils.hasText(raw)) {
            return "<empty>";
        }
        int safeMaxLength = Math.max(16, maxLength);
        String normalized = sanitize(raw).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        if (normalized.length() <= safeMaxLength) {
            return normalized;
        }
        return normalized.substring(0, safeMaxLength) + "...";
    }
}
