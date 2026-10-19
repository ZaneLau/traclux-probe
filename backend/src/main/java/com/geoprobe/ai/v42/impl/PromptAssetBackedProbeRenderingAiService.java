package com.geoprobe.ai.v42.impl;

import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetAware;
import com.geoprobe.ai.v42.support.V42PromptAssetDescriptor;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

public class PromptAssetBackedProbeRenderingAiService implements ProbeRenderingAiService, V42PromptAssetAware {

    private final ChatLanguageModel model;
    private final V42PromptExecutionSupport promptExecutionSupport;

    public PromptAssetBackedProbeRenderingAiService(ChatLanguageModel model,
                                                    V42PromptExecutionSupport promptExecutionSupport) {
        this.model = model;
        this.promptExecutionSupport = promptExecutionSupport;
    }

    @Override
    public String renderProbe(String brandName,
                              String category,
                              String competitors,
                              String categoryAnchors,
                              String brandInjectionPolicy,
                              String simulationProfile,
                              String mustSemantics,
                              String softHints,
                              String behaviorRules) {
        return promptExecutionSupport.execute(
                model,
                V42PromptAssetKey.PROBE_RENDERING,
                Map.of(
                        "brandName", normalize(brandName),
                        "category", normalize(category),
                        "competitors", normalize(competitors),
                        "categoryAnchors", normalize(categoryAnchors),
                        "brandInjectionPolicy", normalize(brandInjectionPolicy),
                        "simulationProfile", normalize(simulationProfile),
                        "mustSemantics", normalize(mustSemantics),
                        "softHints", normalize(softHints),
                        "behaviorRules", normalize(behaviorRules)
                )
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    @Override
    public V42PromptAssetDescriptor describePromptAsset(V42PromptAssetKey assetKey) {
        return promptExecutionSupport.describe(assetKey);
    }
}
