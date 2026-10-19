package com.geoprobe.ai.v42.support;

import java.util.List;

public record V42PromptAssetSnapshot(
        V42PromptAssetDescriptor descriptor,
        String systemMessage,
        String userMessage,
        List<V42PromptAssetExample> fewShotExamples,
        List<String> constraints,
        String outputFormat,
        List<V42PromptAssetChangeLogEntry> changelog
) {
}
