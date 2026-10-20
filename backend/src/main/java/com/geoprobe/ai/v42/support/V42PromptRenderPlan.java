package com.geoprobe.ai.v42.support;

import java.util.List;

public record V42PromptRenderPlan(
        V42PromptAssetDescriptor descriptor,
        String systemMessage,
        String userMessage,
        List<V42PromptAssetExample> fewShotExamples,
        List<String> constraints,
        String outputFormat
) {
    public V42PromptRenderPlan {
        fewShotExamples = fewShotExamples == null ? List.of() : List.copyOf(fewShotExamples);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    public String version() {
        return descriptor == null ? null : descriptor.version();
    }
}
