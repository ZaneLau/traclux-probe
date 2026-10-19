package com.geoprobe.service.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosisConfigTest {

    @Test
    void shouldBuildExperienceEditionConfig() {
        DiagnosisConfig config = DiagnosisConfig.experienceEdition();

        assertEquals(7, config.getTotalCorePrompts());
        assertEquals(2, config.getMinCorePromptsPerCluster());
        assertEquals(5, config.getVariantsPerCorePrompt());
        assertEquals(3, config.getMaxClusterCount());
    }

    @Test
    void shouldBuildStandardEditionConfig() {
        DiagnosisConfig config = DiagnosisConfig.standardEdition();

        assertEquals(11, config.getTotalCorePrompts());
        assertEquals(2, config.getMinCorePromptsPerCluster());
        assertEquals(10, config.getVariantsPerCorePrompt());
        assertEquals(5, config.getMaxClusterCount());
    }

    @Test
    void shouldBuildDeepEditionConfig() {
        DiagnosisConfig config = DiagnosisConfig.deepEdition();

        assertEquals(18, config.getTotalCorePrompts());
        assertEquals(2, config.getMinCorePromptsPerCluster());
        assertEquals(30, config.getVariantsPerCorePrompt());
        assertEquals(9, config.getMaxClusterCount());
    }
}
