package com.geoprobe.dto.engine;

public record SamplingProgress(
        int completed,
        int total,
        int succeeded,
        int failed
) {
}
