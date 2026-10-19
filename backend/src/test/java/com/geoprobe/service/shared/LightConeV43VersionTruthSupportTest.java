package com.geoprobe.service.shared;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightConeV43VersionTruthSupportTest {

    @Test
    void shouldInjectRound2ContractIntoTaskMetadata() {
        Map<String, Object> metadata = LightConeV43VersionTruthSupport.ensureTaskMetadataContract(
                Map.of("channel", "brand_space_preview"),
                "4.2"
        );

        assertEquals("light_cone_task_order_v4_3", metadata.get("taskOrderContractVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runTruth = (Map<String, Object>) metadata.get("lightConeRunVersionTruth");
        assertEquals("legacy_literal", runTruth.get("migrationMode"));
        assertEquals("4.2", runTruth.get("engineRuntimeVersion"));
        assertEquals("legacyExecutionLane", runTruth.get("executionLane"));
    }

    @Test
    void shouldInjectRunTruthIntoRuntimeEnvelope() {
        Map<String, Object> runtimeMetadata = LightConeV43VersionTruthSupport.ensureTaskRuntimeContract(
                Map.of("brandName", "探镜"),
                "4.2"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) runtimeMetadata.get("_runtime");
        @SuppressWarnings("unchecked")
        Map<String, Object> runTruth = (Map<String, Object>) runtime.get("lightConeRunVersionTruth");
        assertEquals("legacy_literal_projection_v1", runTruth.get("projectionVersion"));
    }

    @Test
    void shouldBuildCanonicalShadowRunTruth() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.canonicalShadowRunVersionTruth();

        assertEquals("shadow", runTruth.get("migrationMode"));
        assertEquals("light_cone_4_3", runTruth.get("engineRuntimeVersion"));
        assertEquals("canonicalShadowLane", runTruth.get("executionLane"));
        assertEquals("topic_projection_v4_3", runTruth.get("projectionVersion"));
    }

    @Test
    void shouldBuildCanonicalRunTruth() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.canonicalRunVersionTruth();

        assertEquals("canonical", runTruth.get("migrationMode"));
        assertEquals("light_cone_4_3", runTruth.get("engineRuntimeVersion"));
        assertEquals("canonicalExecutionLane", runTruth.get("executionLane"));
        assertEquals("triple_gate_v4_3", runTruth.get("gateProfileVersion"));
    }

    @Test
    void shouldNormalizeLegacyRunTruthKeysOnRead() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.readRunVersionTruth(
                Map.of("taskMetadata", Map.of(
                        "tracluxRunVersionTruth", Map.of(
                                "engineRuntimeVersion", "traclux_light_cone_4_3",
                                "projectionVersion", "topic_projection_v4_3",
                                "migrationMode", "canonical"
                        )
                )),
                Map.of()
        );

        assertEquals("light_cone_4_3", runTruth.get("engineRuntimeVersion"));
    }

    @Test
    void shouldPreferCanonicalTaskOrderTruthWhenRuntimeSidecarStillLegacyDefault() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.readRunVersionTruth(
                Map.of("taskMetadata", Map.of(
                        "lightConeRunVersionTruth", LightConeV43VersionTruthSupport.canonicalRunVersionTruth()
                )),
                Map.of("_runtime", Map.of(
                        "lightConeRunVersionTruth", LightConeV43VersionTruthSupport.legacyLiteralRunVersionTruth("4.2")
                ))
        );

        assertEquals("canonical", runTruth.get("migrationMode"));
        assertEquals("canonicalExecutionLane", runTruth.get("executionLane"));
    }

    @Test
    void shouldDefaultShadowExecutionLaneWhenMissing() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.readRunVersionTruth(
                Map.of("taskMetadata", Map.of(
                        "lightConeRunVersionTruth", Map.of(
                                "engineRuntimeVersion", "light_cone_4_3",
                                "projectionVersion", "topic_projection_v4_3",
                                "migrationMode", "shadow"
                        )
                )),
                Map.of()
        );

        assertEquals("shadow", runTruth.get("migrationMode"));
        assertEquals("canonicalShadowLane", runTruth.get("executionLane"));
    }

    @Test
    void shouldAcceptLegacyRunProbeCompatibility() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.legacyLiteralRunVersionTruth("4.2");
        Map<String, Object> probeTruth = LightConeV43VersionTruthSupport.legacyLiteralProbeVersionTruth(
                "legacy_prompt_bridge",
                "topic-seed",
                null
        );

        LightConeV43VersionTruthSupport.assertCompatible(runTruth, probeTruth);

        assertTrue(String.valueOf(probeTruth.get("topicBindingHash")).length() >= 32);
    }

    @Test
    void shouldAcceptLegacyLiteralLaneUnderPublicEngineVersion43() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.legacyLiteralRunVersionTruth("4.3");
        Map<String, Object> probeTruth = LightConeV43VersionTruthSupport.legacyLiteralProbeVersionTruth(
                "legacy_prompt_bridge",
                "topic-seed",
                null
        );

        LightConeV43VersionTruthSupport.assertCompatible(runTruth, probeTruth);

        assertEquals("4.3", runTruth.get("engineRuntimeVersion"));
    }

    @Test
    void shouldRejectCanonicalProbeInsideLegacyRun() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.legacyLiteralRunVersionTruth("4.2");
        Map<String, Object> probeTruth = Map.of(
                "blueprintSchemaVersion", "light_cone_4_3",
                "renderContractVersion", "canonical_render_v1",
                "projectionVersion", "topic_projection_v4_3",
                "executionLane", "canonicalExecutionLane"
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LightConeV43VersionTruthSupport.assertCompatible(runTruth, probeTruth)
        );

        assertTrue(error.getMessage().contains("probe"));
    }

    @Test
    void shouldBuildCanonicalShadowProbeTruthCompatibleWithShadowRun() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.canonicalShadowRunVersionTruth();
        Map<String, Object> probeTruth = LightConeV43VersionTruthSupport.canonicalShadowProbeVersionTruth(
                "topic_compiler",
                "topic-binding-seed",
                "canonical_render_v1_offline_shadow",
                null
        );

        LightConeV43VersionTruthSupport.assertCompatible(runTruth, probeTruth);

        assertEquals("light_cone_4_3", probeTruth.get("blueprintSchemaVersion"));
        assertEquals("canonicalShadowLane", probeTruth.get("executionLane"));
        assertTrue(String.valueOf(probeTruth.get("topicBindingHash")).length() >= 32);
    }

    @Test
    void shouldBuildCanonicalProbeTruthCompatibleWithCanonicalRun() {
        Map<String, Object> runTruth = LightConeV43VersionTruthSupport.canonicalRunVersionTruth();
        Map<String, Object> probeTruth = LightConeV43VersionTruthSupport.canonicalProbeVersionTruth(
                "topic_compiler_runtime",
                "topic-binding-seed",
                "canonical_render_v1_runtime",
                null
        );

        LightConeV43VersionTruthSupport.assertCompatible(runTruth, probeTruth);

        assertEquals("light_cone_4_3", probeTruth.get("blueprintSchemaVersion"));
        assertEquals("canonicalExecutionLane", probeTruth.get("executionLane"));
        assertTrue(String.valueOf(probeTruth.get("topicBindingHash")).length() >= 32);
    }
}
