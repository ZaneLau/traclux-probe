package com.geoprobe.dto.engine.v42;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Node3ProbeInputTest {

    @Test
    void shouldMergeRenderTruthIntoTriggeredRules() {
        RenderedProbe renderedProbe = new RenderedProbe(
                "probe-1",
                BucketType.ORTHOGONAL,
                Map.of("D1", "exploration"),
                Map.of("D1", "越看越乱"),
                null,
                ProbeExposurePolicy.BRAND_HIDDEN,
                "这个现在到底该怎么选？",
                false,
                "LLM",
                "orthogonal_constrained_llm",
                "llm_validated_original",
                Map.of("promptAssetVersion", "node2-v2026-05-10")
        );

        Node3ProbeInput input = Node3ProbeInput.fromRenderedProbe(renderedProbe);

        assertEquals("LLM", input.triggeredRulesJson().get("renderSource"));
        assertEquals("llm_validated_original", input.triggeredRulesJson().get("renderReason"));
        assertEquals(false, input.triggeredRulesJson().get("fallbackUsed"));
        assertEquals("node2-v2026-05-10", input.triggeredRulesJson().get("promptAssetVersion"));
    }
}
