package com.geoprobe.ai.v42.support;

import java.util.List;

public record V42PromptAssetDocument(
        String version,
        String systemMessage,
        String userMessage,
        List<V42PromptAssetExample> fewShotExamples,
        List<String> constraints,
        String outputFormat,
        List<V42PromptAssetChangeLogEntry> changelog
) {
    public V42PromptAssetDocument {
        fewShotExamples = fewShotExamples == null ? List.of() : List.copyOf(fewShotExamples);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        outputFormat = outputFormat == null ? null : outputFormat.trim();
        changelog = changelog == null ? List.of() : List.copyOf(changelog);
    }
}
