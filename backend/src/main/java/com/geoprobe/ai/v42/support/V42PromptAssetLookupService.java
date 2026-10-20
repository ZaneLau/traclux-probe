package com.geoprobe.ai.v42.support;

import org.springframework.stereotype.Service;

@Service
public class V42PromptAssetLookupService {

    private final V42PromptAssetLoader promptAssetLoader;

    public V42PromptAssetLookupService(V42PromptAssetLoader promptAssetLoader) {
        this.promptAssetLoader = promptAssetLoader;
    }

    public V42PromptAssetSnapshot current(V42PromptAssetKey assetKey) {
        V42PromptAssetDocument document = promptAssetLoader.load(assetKey);
        return toSnapshot(promptAssetLoader.describe(assetKey), document);
    }

    public V42PromptAssetSnapshot lookup(V42PromptAssetKey assetKey, String version) {
        V42PromptAssetDocument document = promptAssetLoader.loadVersion(assetKey, version);
        V42PromptAssetDescriptor descriptor = new V42PromptAssetDescriptor(
                assetKey,
                document.version(),
                "version:" + version,
                null
        );
        return toSnapshot(descriptor, document);
    }

    private V42PromptAssetSnapshot toSnapshot(V42PromptAssetDescriptor descriptor,
                                              V42PromptAssetDocument document) {
        return new V42PromptAssetSnapshot(
                descriptor,
                document.systemMessage(),
                document.userMessage(),
                document.fewShotExamples(),
                document.constraints(),
                document.outputFormat(),
                document.changelog()
        );
    }
}
