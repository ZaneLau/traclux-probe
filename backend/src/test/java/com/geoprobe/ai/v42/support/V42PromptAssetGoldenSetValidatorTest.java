package com.geoprobe.ai.v42.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class V42PromptAssetGoldenSetValidatorTest {

    private static final String DEFAULT_PROMPT_ASSET_DIR = "classpath:v42-test-prompt-assets";
    private static final String PROMPT_ASSET_DIR_PROPERTY = "v42.prompt.asset.test-dir";

    @Test
    void shouldValidatePromptAssetGoldenSetForAllAssets() {
        V42PromptAssetLoader assetLoader = new V42PromptAssetLoader(
                new V42SecretAssetSettings(resolvePromptAssetDir(), true)
        );
        V42PromptExecutionSupport executionSupport = new V42PromptExecutionSupport(assetLoader);
        V42PromptAssetGoldenSetLoader goldenSetLoader = new V42PromptAssetGoldenSetLoader();
        V42PromptAssetGoldenSetValidator validator = new V42PromptAssetGoldenSetValidator(assetLoader, executionSupport);

        List<V42PromptGoldenSetCase> cases = goldenSetLoader.load("classpath:v42-prompt-golden-set/prompt_asset_cases.json");

        assertDoesNotThrow(() -> validator.validate(cases));
    }

    private static String resolvePromptAssetDir() {
        String configured = System.getProperty(PROMPT_ASSET_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PROMPT_ASSET_DIR;
        }
        return configured.trim();
    }
}
