package com.geoprobe.service.shared;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42TaskOrderExecutionPolicyResolverTest {

    private final V42TaskOrderExecutionPolicyResolver resolver =
            new V42TaskOrderExecutionPolicyResolver(
                    new V42TargetProbeCountPolicy(),
                    new V42RealtimeSearchPolicyResolver()
            );

    @Test
    void frontDeskPlanningShouldKeepRealtimeSearchDisabledWhenTaskOrderOmitsIt() {
        V42TaskOrderExecutionPolicy policy = resolver.resolveForFrontDeskPlanning(Map.of());

        assertEquals(100, policy.targetProbeCount());
        assertFalse(policy.realtimeSearchPolicy().enabled());
        assertEquals("doubao", policy.realtimeSearchPolicy().provider());
    }

    @Test
    void frontDeskPlanningShouldRespectExplicitRealtimeSearchFlag() {
        V42TaskOrderExecutionPolicy policy = resolver.resolveForFrontDeskPlanning(Map.of(
                "samplingRealtimeSearch", Map.of(
                        "enabled", false,
                        "provider", "doubao"
                )
        ));

        assertFalse(policy.realtimeSearchPolicy().enabled());
        assertEquals("doubao", policy.realtimeSearchPolicy().provider());
    }

    @Test
    void defaultPolicyShouldRemainDisabledForNonFrontDeskFlows() {
        assertFalse(resolver.defaultPolicy().realtimeSearchPolicy().enabled());
    }
}
