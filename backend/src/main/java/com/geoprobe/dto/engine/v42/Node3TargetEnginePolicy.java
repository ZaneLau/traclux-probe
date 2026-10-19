package com.geoprobe.dto.engine.v42;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record Node3TargetEnginePolicy(
        String provider,
        String modelId,
        boolean disableReasoning
) {

    private static final String DEFAULT_PROVIDER = "doubao";

    public Node3TargetEnginePolicy {
        modelId = normalizeOptional(modelId);
        provider = normalizeProvider(provider, modelId);
    }

    public Node3TargetEnginePolicy(String provider, String modelId) {
        this(provider, modelId, true);
    }

    public static Node3TargetEnginePolicy defaultPolicy() {
        return new Node3TargetEnginePolicy(DEFAULT_PROVIDER, null, true);
    }

    public static String inferProviderFromModelId(String modelId) {
        String normalized = normalizeOptional(modelId);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("gpt-") || lower.contains("chatgpt") || lower.contains("openai")) {
            return "chatgpt";
        }
        if (lower.contains("deepseek")) {
            return "deepseek";
        }
        if (lower.contains("kimi") || lower.contains("moonshot")) {
            return "kimi";
        }
        if (lower.contains("glm") || lower.contains("zhipu")) {
            return "glm";
        }
        if (lower.contains("minimax") || lower.contains("abab")) {
            return "minimax";
        }
        if (lower.contains("doubao")) {
            return DEFAULT_PROVIDER;
        }
        return null;
    }

    private static String normalizeProvider(String provider, String modelId) {
        String normalized = normalizeOptional(provider);
        if (!StringUtils.hasText(normalized)) {
            String inferred = inferProviderFromModelId(modelId);
            return StringUtils.hasText(inferred) ? inferred : DEFAULT_PROVIDER;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "openai" -> "chatgpt";
            case "deepseek-ai" -> "deepseek";
            case "moonshot" -> "kimi";
            case "zhipu" -> "glm";
            default -> lower;
        };
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
