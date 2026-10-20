package com.geoprobe.ai.v42.impl;

import com.geoprobe.ai.v42.MinusOneContextExpansionAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

public class PromptAssetBackedMinusOneContextExpansionAiService implements MinusOneContextExpansionAiService {

    private final ChatLanguageModel model;
    private final V42PromptExecutionSupport promptExecutionSupport;

    public PromptAssetBackedMinusOneContextExpansionAiService(ChatLanguageModel model,
                                                              V42PromptExecutionSupport promptExecutionSupport) {
        this.model = model;
        this.promptExecutionSupport = promptExecutionSupport;
    }

    @Override
    public String expand(String brandName,
                         String category,
                         String competitors,
                         String categoryAnchors,
                         String userPrompt,
                         String brandWebsite,
                         String evidenceSnippets,
                         String benchmarkQueries) {
        return promptExecutionSupport.execute(
                model,
                V42PromptAssetKey.MINUS_ONE_CONTEXT_EXPANSION,
                Map.of(
                        "brandName", normalize(brandName),
                        "category", normalize(category),
                        "competitors", normalize(competitors),
                        "categoryAnchors", normalize(categoryAnchors),
                        "userPrompt", normalize(userPrompt),
                        "brandWebsite", normalize(brandWebsite),
                        "evidenceSnippets", normalize(evidenceSnippets),
                        "benchmarkQueries", normalize(benchmarkQueries)
                )
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
