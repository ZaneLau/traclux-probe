package com.geoprobe.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCapabilityRegistryTest {

    private final ModelCapabilityRegistry registry = new ModelCapabilityRegistry();

    @Test
    void shouldResolveVolcengineThinkingCapability() {
        ModelCapability capability = registry.resolve("volcengine", "doubao-seed-1.8");
        assertTrue(capability.supportsThinkingToggle());
        assertTrue(capability.supportsMaxCompletionTokens());
    }

    @Test
    void shouldDisableThinkingToggleForDeepseekOnVolcengineGateway() {
        ModelCapability capability = registry.resolve("volcengine", "deepseek-v3.2");
        assertFalse(capability.supportsThinkingToggle());
        assertTrue(capability.supportsMaxCompletionTokens());
    }
}
