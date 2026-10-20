package com.geoprobe.service.shared;

import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V42Node3TargetEnginePolicyResolverTest {

    @Test
    void shouldCanonicalizeConfiguredDeepseekAliasToEndpointId() {
        V42Node3TargetEnginePolicyResolver resolver = new V42Node3TargetEnginePolicyResolver();
        ReflectionTestUtils.setField(resolver, "deepseekConfiguredModelId", "deepseek-v3.2");
        ReflectionTestUtils.setField(resolver, "deepseekDefaultModelId", "ep-deepseek-32");

        Node3TargetEnginePolicy policy = resolver.resolveForFrontDeskPlanning(Map.of(
                "samplingEngine", Map.of(
                        "provider", "deepseek",
                        "modelId", "deepseek-v3.2",
                        "disableReasoning", true
                )
        ));

        assertEquals("deepseek", policy.provider());
        assertEquals("ep-deepseek-32", policy.modelId());
    }

    @Test
    void shouldPreserveExplicitEndpointIdWhenAlreadyCanonical() {
        V42Node3TargetEnginePolicyResolver resolver = new V42Node3TargetEnginePolicyResolver();
        ReflectionTestUtils.setField(resolver, "deepseekConfiguredModelId", "deepseek-v3.2");
        ReflectionTestUtils.setField(resolver, "deepseekDefaultModelId", "ep-deepseek-32");

        Node3TargetEnginePolicy policy = resolver.resolveForFrontDeskPlanning(Map.of(
                "samplingEngine", Map.of(
                        "provider", "deepseek",
                        "modelId", "ep-deepseek-32",
                        "disableReasoning", true
                )
        ));

        assertEquals("ep-deepseek-32", policy.modelId());
    }
}
