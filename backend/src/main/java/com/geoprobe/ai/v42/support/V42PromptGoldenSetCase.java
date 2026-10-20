package com.geoprobe.ai.v42.support;

import java.util.List;
import java.util.Map;

public record V42PromptGoldenSetCase(
        String caseId,
        V42PromptAssetKey assetKey,
        Map<String, String> variables,
        List<String> expectedSystemContains,
        List<String> expectedUserContains,
        Integer minFewShotExamples
) {
    public V42PromptGoldenSetCase {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        expectedSystemContains = expectedSystemContains == null ? List.of() : List.copyOf(expectedSystemContains);
        expectedUserContains = expectedUserContains == null ? List.of() : List.copyOf(expectedUserContains);
    }
}
