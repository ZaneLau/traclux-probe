package com.geoprobe.ai.v42.impl;

import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

public class PromptAssetBackedCssmHiStateSpaceAiService implements CssmHiStateSpaceAiService {

    private final ChatLanguageModel model;
    private final V42PromptExecutionSupport promptExecutionSupport;

    public PromptAssetBackedCssmHiStateSpaceAiService(ChatLanguageModel model,
                                                      V42PromptExecutionSupport promptExecutionSupport) {
        this.model = model;
        this.promptExecutionSupport = promptExecutionSupport;
    }

    @Override
    public String translateStateSpace(String category,
                                      String d1AbstractCodes,
                                      String d2AbstractCodes,
                                      String d3AbstractCodes,
                                      String d4AbstractCodes,
                                      String d5AbstractCodes,
                                      String d6AbstractCodes) {
        return promptExecutionSupport.execute(
                model,
                V42PromptAssetKey.CSSM_HI_STATE_SPACE,
                Map.of(
                        "category", normalize(category),
                        "d1AbstractCodes", normalize(d1AbstractCodes),
                        "d2AbstractCodes", normalize(d2AbstractCodes),
                        "d3AbstractCodes", normalize(d3AbstractCodes),
                        "d4AbstractCodes", normalize(d4AbstractCodes),
                        "d5AbstractCodes", normalize(d5AbstractCodes),
                        "d6AbstractCodes", normalize(d6AbstractCodes)
                )
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
