package com.geoprobe.dto.engine.v42;

import java.util.Map;
import java.util.Objects;

public record Node4ProbeOutput(
        String probeId,
        BucketType bucketType,
        Map<String, String> abstractTags,
        Map<String, String> translatedTags,
        RealizationPlan realizationPlan,
        ProbeExposurePolicy exposurePolicy,
        String renderedPrompt,
        String renderFamily,
        String renderReason,
        Map<String, Object> triggeredRulesJson,
        ProbeDispatchStatus dispatchStatus,
        RawResponse rawResponse,
        int roundNo,
        int attemptNo,
        String failureReason,
        BrandDetectionResult brandDetectionResult
) {

    public Node4ProbeOutput {
        Node3ProbeOutput validatedNode3 = new Node3ProbeOutput(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                realizationPlan,
                exposurePolicy,
                renderedPrompt,
                renderFamily,
                renderReason,
                triggeredRulesJson,
                dispatchStatus,
                rawResponse,
                roundNo,
                attemptNo,
                failureReason
        );
        probeId = validatedNode3.probeId();
        bucketType = validatedNode3.bucketType();
        abstractTags = validatedNode3.abstractTags();
        translatedTags = validatedNode3.translatedTags();
        realizationPlan = validatedNode3.realizationPlan();
        exposurePolicy = validatedNode3.exposurePolicy();
        renderedPrompt = validatedNode3.renderedPrompt();
        renderFamily = validatedNode3.renderFamily();
        renderReason = validatedNode3.renderReason();
        triggeredRulesJson = validatedNode3.triggeredRulesJson();
        dispatchStatus = validatedNode3.dispatchStatus();
        rawResponse = validatedNode3.rawResponse();
        roundNo = validatedNode3.roundNo();
        attemptNo = validatedNode3.attemptNo();
        failureReason = validatedNode3.failureReason();
        brandDetectionResult = Objects.requireNonNull(brandDetectionResult, "brandDetectionResult 不能为空");
    }

    public static Node4ProbeOutput fromNode3(Node3ProbeOutput probe, BrandDetectionResult brandDetectionResult) {
        Objects.requireNonNull(probe, "probe 不能为空");
        return new Node4ProbeOutput(
                probe.probeId(),
                probe.bucketType(),
                probe.abstractTags(),
                probe.translatedTags(),
                probe.realizationPlan(),
                probe.exposurePolicy(),
                probe.renderedPrompt(),
                probe.renderFamily(),
                probe.renderReason(),
                probe.triggeredRulesJson(),
                probe.dispatchStatus(),
                probe.rawResponse(),
                probe.roundNo(),
                probe.attemptNo(),
                probe.failureReason(),
                brandDetectionResult
        );
    }

    public Node4ProbeOutput(String probeId,
                            BucketType bucketType,
                            Map<String, String> abstractTags,
                            Map<String, String> translatedTags,
                            ProbeExposurePolicy exposurePolicy,
                            String renderedPrompt,
                            ProbeDispatchStatus dispatchStatus,
                            RawResponse rawResponse,
                            String failureReason,
                            BrandDetectionResult brandDetectionResult) {
        this(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                null,
                exposurePolicy,
                renderedPrompt,
                "default_render_family",
                failureReason == null ? "default_render" : "default_failed_render",
                Map.of(),
                dispatchStatus,
                rawResponse,
                0,
                1,
                failureReason,
                brandDetectionResult
        );
    }
}
