package com.geoprobe.ai.v42.support;

public record V42PromptAssetDescriptor(
        V42PromptAssetKey key,
        String version,
        String sourceLocation,
        String sourceSha256
) {
}
