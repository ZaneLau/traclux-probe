package com.geoprobe.dto.engine.v42;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record RealtimeSearchSummary(
        boolean enabled,
        String provider,
        int requestedProbeCount,
        int satisfiedProbeCount,
        double evidenceSuccessRate,
        int fallbackProbeCount,
        int backfillRoundsUsed,
        int recoveredProbeCount,
        boolean qualityGatePassed,
        List<String> unsatisfiedProbeIds
) {

    private static final String DEFAULT_PROVIDER = "doubao";

    public RealtimeSearchSummary {
        provider = normalizeProvider(provider);
        requestedProbeCount = normalizeCount(requestedProbeCount, "requestedProbeCount");
        satisfiedProbeCount = normalizeCount(satisfiedProbeCount, "satisfiedProbeCount");
        if (satisfiedProbeCount > requestedProbeCount) {
            throw new IllegalArgumentException("satisfiedProbeCount 不能大于 requestedProbeCount");
        }
        evidenceSuccessRate = normalizeRate(evidenceSuccessRate);
        fallbackProbeCount = normalizeCount(fallbackProbeCount, "fallbackProbeCount");
        backfillRoundsUsed = normalizeCount(backfillRoundsUsed, "backfillRoundsUsed");
        recoveredProbeCount = normalizeCount(recoveredProbeCount, "recoveredProbeCount");
        if (recoveredProbeCount > requestedProbeCount) {
            throw new IllegalArgumentException("recoveredProbeCount 不能大于 requestedProbeCount");
        }
        Objects.requireNonNull(unsatisfiedProbeIds, "unsatisfiedProbeIds 不能为空");
        unsatisfiedProbeIds = List.copyOf(unsatisfiedProbeIds);
    }

    public static RealtimeSearchSummary disabled(String provider) {
        return new RealtimeSearchSummary(
                false,
                provider,
                0,
                0,
                0.0d,
                0,
                0,
                0,
                true,
                List.of()
        );
    }

    private static String normalizeProvider(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_PROVIDER;
        }
        String normalized = value.trim().toLowerCase();
        if (!DEFAULT_PROVIDER.equals(normalized)) {
            throw new IllegalArgumentException("realtimeSearch.provider 当前仅支持 doubao");
        }
        return normalized;
    }

    private static int normalizeCount(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
        return value;
    }

    private static double normalizeRate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
