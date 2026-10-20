package com.geoprobe.dto.engine.v42;

import java.util.List;
import java.util.Objects;

public record Node3SamplingResult(
        List<Node3ProbeOutput> probes,
        RealtimeSearchSummary realtimeSearchSummary,
        Node3UsageCostSummary usageCostSummary,
        String providerCode
) {

    public Node3SamplingResult {
        Objects.requireNonNull(probes, "probes 不能为空");
        probes = List.copyOf(probes);
        realtimeSearchSummary = realtimeSearchSummary == null
                ? RealtimeSearchSummary.disabled("doubao")
                : realtimeSearchSummary;
        usageCostSummary = usageCostSummary == null
                ? Node3UsageCostSummary.empty()
                : usageCostSummary;
        providerCode = normalizeProvider(providerCode, realtimeSearchSummary.provider());
    }

    public Node3SamplingResult(List<Node3ProbeOutput> probes) {
        this(probes, RealtimeSearchSummary.disabled("doubao"), Node3UsageCostSummary.empty(), "doubao");
    }

    public Node3SamplingResult(List<Node3ProbeOutput> probes,
                               RealtimeSearchSummary realtimeSearchSummary) {
        this(probes, realtimeSearchSummary, Node3UsageCostSummary.empty(), realtimeSearchSummary == null ? "doubao" : realtimeSearchSummary.provider());
    }

    public Node3SamplingResult(List<Node3ProbeOutput> probes,
                               RealtimeSearchSummary realtimeSearchSummary,
                               Node3UsageCostSummary usageCostSummary) {
        this(
                probes,
                realtimeSearchSummary,
                usageCostSummary,
                realtimeSearchSummary == null ? "doubao" : realtimeSearchSummary.provider()
        );
    }

    public long successCount() {
        return probes.stream().filter(probe -> probe.dispatchStatus() == ProbeDispatchStatus.SUCCESS).count();
    }

    public long failedCount() {
        return probes.stream().filter(probe -> probe.dispatchStatus() == ProbeDispatchStatus.FAILED).count();
    }

    private static String normalizeProvider(String providerCode, String fallback) {
        String normalized = normalizeOptional(providerCode);
        if (normalized != null) {
            return normalized;
        }
        String fallbackNormalized = normalizeOptional(fallback);
        return fallbackNormalized == null ? "doubao" : fallbackNormalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
