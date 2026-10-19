package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record Node3ProbeOutput(
        String probeId,
        BucketType bucketType,
        java.util.Map<String, String> abstractTags,
        java.util.Map<String, String> translatedTags,
        RealizationPlan realizationPlan,
        ProbeExposurePolicy exposurePolicy,
        String renderedPrompt,
        String renderFamily,
        String renderReason,
        java.util.Map<String, Object> triggeredRulesJson,
        ProbeDispatchStatus dispatchStatus,
        RawResponse rawResponse,
        int roundNo,
        int attemptNo,
        String failureReason
) {

    public Node3ProbeOutput {
        Node3ProbeInput validatedInput = new Node3ProbeInput(
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
                Node3SearchGroundingContext.empty()
        );
        probeId = validatedInput.probeId();
        bucketType = validatedInput.bucketType();
        abstractTags = validatedInput.abstractTags();
        translatedTags = validatedInput.translatedTags();
        realizationPlan = validatedInput.realizationPlan();
        exposurePolicy = validatedInput.exposurePolicy();
        renderedPrompt = validatedInput.renderedPrompt();
        renderFamily = validatedInput.renderFamily();
        renderReason = validatedInput.renderReason();
        triggeredRulesJson = validatedInput.triggeredRulesJson();
        dispatchStatus = Objects.requireNonNull(dispatchStatus, "dispatchStatus 不能为空");
        rawResponse = Objects.requireNonNull(rawResponse, "rawResponse 不能为空");
        if (roundNo < 0) {
            throw new IllegalArgumentException("roundNo 不能为负数");
        }
        if (attemptNo < 0) {
            throw new IllegalArgumentException("attemptNo 不能为负数");
        }
        failureReason = normalizeOptional(failureReason);
        if (dispatchStatus == ProbeDispatchStatus.SUCCESS && failureReason != null) {
            throw new IllegalArgumentException("SUCCESS 状态不应包含 failureReason");
        }
        if (dispatchStatus == ProbeDispatchStatus.FAILED && failureReason == null) {
            throw new IllegalArgumentException("FAILED 状态必须包含 failureReason");
        }
    }

    public static Node3ProbeOutput success(Node3ProbeInput input, RawResponse rawResponse) {
        return success(input, rawResponse, 0, 1);
    }

    public static Node3ProbeOutput success(Node3ProbeInput input,
                                           RawResponse rawResponse,
                                           int roundNo,
                                           int attemptNo) {
        Objects.requireNonNull(input, "Node3ProbeInput 不能为空");
        return new Node3ProbeOutput(
                input.probeId(),
                input.bucketType(),
                input.abstractTags(),
                input.translatedTags(),
                input.realizationPlan(),
                input.exposurePolicy(),
                input.renderedPrompt(),
                input.renderFamily(),
                input.renderReason(),
                input.triggeredRulesJson(),
                ProbeDispatchStatus.SUCCESS,
                Objects.requireNonNull(rawResponse, "rawResponse 不能为空"),
                roundNo,
                attemptNo,
                null
        );
    }

    public static Node3ProbeOutput failed(Node3ProbeInput input, RawResponse rawResponse, String failureReason) {
        return failed(input, rawResponse, 0, 1, failureReason);
    }

    public static Node3ProbeOutput failed(Node3ProbeInput input,
                                          RawResponse rawResponse,
                                          int roundNo,
                                          int attemptNo,
                                          String failureReason) {
        Objects.requireNonNull(input, "Node3ProbeInput 不能为空");
        return new Node3ProbeOutput(
                input.probeId(),
                input.bucketType(),
                input.abstractTags(),
                input.translatedTags(),
                input.realizationPlan(),
                input.exposurePolicy(),
                input.renderedPrompt(),
                input.renderFamily(),
                input.renderReason(),
                input.triggeredRulesJson(),
                ProbeDispatchStatus.FAILED,
                Objects.requireNonNull(rawResponse, "rawResponse 不能为空"),
                roundNo,
                attemptNo,
                normalizeRequired(failureReason, "failureReason 不能为空")
        );
    }

    public Node3ProbeOutput(String probeId,
                            BucketType bucketType,
                            java.util.Map<String, String> abstractTags,
                            java.util.Map<String, String> translatedTags,
                            ProbeExposurePolicy exposurePolicy,
                            String renderedPrompt,
                            ProbeDispatchStatus dispatchStatus,
                            RawResponse rawResponse,
                            String failureReason) {
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
                java.util.Map.of(),
                dispatchStatus,
                rawResponse,
                0,
                1,
                failureReason
        );
    }

    public Node3ProbeOutput withDispatchTrace(int roundNo, int attemptNo) {
        return new Node3ProbeOutput(
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
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
