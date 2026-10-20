package com.geoprobe.dto.engine.v42;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record Node3SamplingRequest(
        String diagnosisId,
        List<Node3ProbeInput> probes,
        RealtimeSearchPolicy realtimeSearchPolicy,
        Node3TargetEnginePolicy targetEnginePolicy
) {

    public Node3SamplingRequest {
        diagnosisId = normalizeOptional(diagnosisId);
        Objects.requireNonNull(probes, "probes 不能为空");
        probes = List.copyOf(probes);
        realtimeSearchPolicy = realtimeSearchPolicy == null
                ? RealtimeSearchPolicy.disabled()
                : realtimeSearchPolicy;
        targetEnginePolicy = targetEnginePolicy == null
                ? Node3TargetEnginePolicy.defaultPolicy()
                : targetEnginePolicy;
    }

    public Node3SamplingRequest(List<Node3ProbeInput> probes) {
        this(null, probes, RealtimeSearchPolicy.disabled(), Node3TargetEnginePolicy.defaultPolicy());
    }

    public Node3SamplingRequest(String diagnosisId, List<Node3ProbeInput> probes) {
        this(diagnosisId, probes, RealtimeSearchPolicy.disabled(), Node3TargetEnginePolicy.defaultPolicy());
    }

    public Node3SamplingRequest(String diagnosisId,
                                List<Node3ProbeInput> probes,
                                RealtimeSearchPolicy realtimeSearchPolicy) {
        this(diagnosisId, probes, realtimeSearchPolicy, Node3TargetEnginePolicy.defaultPolicy());
    }

    public static Node3SamplingRequest fromRenderedProbes(List<RenderedProbe> renderedProbes) {
        return fromRenderedProbes(null, renderedProbes, RealtimeSearchPolicy.disabled(), Node3TargetEnginePolicy.defaultPolicy());
    }

    public static Node3SamplingRequest fromRenderedProbes(String diagnosisId, List<RenderedProbe> renderedProbes) {
        return fromRenderedProbes(diagnosisId, renderedProbes, RealtimeSearchPolicy.disabled(), Node3TargetEnginePolicy.defaultPolicy());
    }

    public static Node3SamplingRequest fromRenderedProbes(String diagnosisId,
                                                          List<RenderedProbe> renderedProbes,
                                                          RealtimeSearchPolicy realtimeSearchPolicy) {
        return fromRenderedProbes(
                diagnosisId,
                renderedProbes,
                realtimeSearchPolicy,
                Node3TargetEnginePolicy.defaultPolicy()
        );
    }

    public static Node3SamplingRequest fromRenderedProbes(String diagnosisId,
                                                          List<RenderedProbe> renderedProbes,
                                                          RealtimeSearchPolicy realtimeSearchPolicy,
                                                          Node3TargetEnginePolicy targetEnginePolicy) {
        return fromRenderedProbes(
                diagnosisId,
                renderedProbes,
                realtimeSearchPolicy,
                targetEnginePolicy,
                renderedProbe -> Node3SearchGroundingContext.empty()
        );
    }

    public static Node3SamplingRequest fromRenderedProbes(String diagnosisId,
                                                          List<RenderedProbe> renderedProbes,
                                                          RealtimeSearchPolicy realtimeSearchPolicy,
                                                          Node3TargetEnginePolicy targetEnginePolicy,
                                                          Function<RenderedProbe, Node3SearchGroundingContext> searchGroundingResolver) {
        Objects.requireNonNull(renderedProbes, "renderedProbes 不能为空");
        Function<RenderedProbe, Node3SearchGroundingContext> safeResolver = searchGroundingResolver == null
                ? renderedProbe -> Node3SearchGroundingContext.empty()
                : searchGroundingResolver;
        return new Node3SamplingRequest(
                diagnosisId,
                renderedProbes.stream()
                        .map(renderedProbe -> Node3ProbeInput.fromRenderedProbe(renderedProbe, safeResolver.apply(renderedProbe)))
                        .toList(),
                realtimeSearchPolicy,
                targetEnginePolicy
        );
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
