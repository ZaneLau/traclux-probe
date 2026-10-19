package com.geoprobe.ai.v42.support;

import org.springframework.util.StringUtils;

public record V42SecretAssetSettings(
        String promptAssetDir,
        boolean promptAssetFailFast
) {
    public V42SecretAssetSettings {
        if (!StringUtils.hasText(promptAssetDir)) {
            throw new IllegalArgumentException("promptAssetDir 不能为空");
        }
        promptAssetDir = promptAssetDir.trim();
    }
}
