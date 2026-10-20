package com.geoprobe.ai.v42.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42Node2PromptGoldenSetTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_PROMPT_ASSET_DIR = "classpath:v42-test-prompt-assets";
    private static final String PROMPT_ASSET_DIR_PROPERTY = "v42.prompt.asset.test-dir";

    @Test
    void shouldPrepareAllGoldenSetCases() throws Exception {
        V42PromptAssetLoader assetLoader = new V42PromptAssetLoader(
                new V42SecretAssetSettings(resolvePromptAssetDir(), true)
        );
        V42PromptExecutionSupport support = new V42PromptExecutionSupport(assetLoader);
        String expectedVersion = assetLoader.describe(V42PromptAssetKey.PROBE_RENDERING).version();

        try (InputStream inputStream = new ClassPathResource("v42-prompt-golden-set/node2_probe_rendering_cases.json").getInputStream()) {
            List<Map<String, Object>> cases = MAPPER.readValue(inputStream, new TypeReference<>() {
            });
            assertEquals(5, cases.size());
            for (Map<String, Object> item : cases) {
                @SuppressWarnings("unchecked")
                Map<String, String> variables = (Map<String, String>) item.get("variables");
                @SuppressWarnings("unchecked")
                List<String> expectedSystemContains = (List<String>) item.get("expectedSystemContains");
                @SuppressWarnings("unchecked")
                List<String> expectedUserContains = (List<String>) item.get("expectedUserContains");

                V42PromptRenderPlan plan = support.prepare(V42PromptAssetKey.PROBE_RENDERING, variables);

                assertEquals(expectedVersion, plan.version());
                assertTrue(plan.fewShotExamples().size() >= 1);
                for (String expected : expectedSystemContains) {
                    assertTrue(plan.systemMessage().contains(expected), String.valueOf(item.get("caseId")));
                }
                for (String expected : expectedUserContains) {
                    assertTrue(plan.userMessage().contains(expected), String.valueOf(item.get("caseId")));
                }
            }
        }
    }

    private static String resolvePromptAssetDir() {
        String configured = System.getProperty(PROMPT_ASSET_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PROMPT_ASSET_DIR;
        }
        return configured.trim();
    }
}
