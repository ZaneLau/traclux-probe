package com.geoprobe.dto.engine.v42;

import org.springframework.util.StringUtils;

public record RealtimeSearchPolicy(
        boolean enabled,
        String provider,
        double minEvidenceSuccessRate,
        int maxEvidenceRetries,
        int maxBackfillRounds
) {

    private static final String DEFAULT_PROVIDER = "doubao";
    private static final java.util.Set<String> SUPPORTED_PROVIDERS = java.util.Set.of(DEFAULT_PROVIDER);

    public RealtimeSearchPolicy {
        provider = normalizeProvider(provider);
        minEvidenceSuccessRate = normalizeRate(minEvidenceSuccessRate);
        maxEvidenceRetries = normalizeRetries(maxEvidenceRetries);
        maxBackfillRounds = normalizeBackfillRounds(maxBackfillRounds);
    }

    public static RealtimeSearchPolicy disabled() {
        return new RealtimeSearchPolicy(false, DEFAULT_PROVIDER, 0.90d, 2, 2);
    }

    private static String normalizeProvider(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_PROVIDER;
        }
        String normalized = value.trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("realtimeSearch.provider 当前仅支持 doubao");
        }
        return normalized;
    }

    private static double normalizeRate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.90d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int normalizeRetries(int value) {
        return Math.max(0, Math.min(5, value));
    }

    private static int normalizeBackfillRounds(int value) {
        return Math.max(0, Math.min(5, value));
    }
}
