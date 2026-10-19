package com.geoprobe.dto.engine;

import java.util.List;

public record SamplingResult(
        List<String> runIds,
        int totalCompleted,
        int totalSucceeded,
        int totalFailed,
        SamplingQualityAssessment qualityAssessment
) {

    public SamplingResult(List<String> runIds,
                          int totalCompleted,
                          int totalSucceeded,
                          int totalFailed) {
        this(runIds, totalCompleted, totalSucceeded, totalFailed,
                new SamplingQualityAssessment(List.of(), java.util.Map.of(), java.util.Map.of()));
    }
}
