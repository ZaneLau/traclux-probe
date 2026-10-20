package com.geoprobe.ai.v42.impl;

import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

public class PromptAssetBackedBrandDetectionL3AiService implements BrandDetectionL3AiService {

    private final ChatLanguageModel model;
    private final V42PromptExecutionSupport promptExecutionSupport;

    public PromptAssetBackedBrandDetectionL3AiService(ChatLanguageModel model,
                                                      V42PromptExecutionSupport promptExecutionSupport) {
        this.model = model;
        this.promptExecutionSupport = promptExecutionSupport;
    }

    @Override
    public String detect(String brandName, String brandAliases, String competitors, String responseText) {
        return promptExecutionSupport.execute(
                model,
                V42PromptAssetKey.BRAND_DETECTION_L3,
                Map.of(
                        "brandName", normalize(brandName),
                        "brandAliases", normalize(brandAliases),
                        "competitors", normalize(competitors),
                        "responseText", normalize(responseText)
                )
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
