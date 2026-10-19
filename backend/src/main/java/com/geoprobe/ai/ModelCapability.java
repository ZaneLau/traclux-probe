package com.geoprobe.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public record ModelCapability(
        boolean supportsThinkingToggle,
        boolean supportsJsonMode,
        boolean supportsFunctionCalling,
        boolean supportsMaxCompletionTokens
) {

    public static final ModelCapability DEFAULT = new ModelCapability(false, false, false, true);

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("supportsThinkingToggle", supportsThinkingToggle);
        map.put("supportsJsonMode", supportsJsonMode);
        map.put("supportsFunctionCalling", supportsFunctionCalling);
        map.put("supportsMaxCompletionTokens", supportsMaxCompletionTokens);
        return map;
    }
}
