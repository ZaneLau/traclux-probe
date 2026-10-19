package com.geoprobe.ai.v42.support;

public enum V42PromptAssetKey {
    CSSM_HI_STATE_SPACE("node0/cssm_hi_state_space", "cssm_hi_state_space.json"),
    PROBE_RENDERING("node2/probe_rendering", "probe_rendering.json"),
    MINUS_ONE_CONTEXT_EXPANSION("minus_one/context_expansion", "minus_one_context_expansion.json"),
    BRAND_DETECTION_L3("brand_detection/l3", "brand_detection_l3.json");

    private final String assetPath;
    private final String fileName;

    V42PromptAssetKey(String assetPath, String fileName) {
        this.assetPath = assetPath;
        this.fileName = fileName;
    }

    public String assetPath() {
        return assetPath;
    }

    public String fileName() {
        return fileName;
    }
}
