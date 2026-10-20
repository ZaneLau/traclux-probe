package com.geoprobe.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StagingSafetyStartupValidatorTest {

    private StagingSafetyStartupValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StagingSafetyStartupValidator();
        ReflectionTestUtils.setField(validator, "productionSafeRequired", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:mysql://staging-db.example.net:3306/traclux_staging?useSSL=true");
        ReflectionTestUtils.setField(validator, "schedulingEnabled", false);
        ReflectionTestUtils.setField(validator, "engineExecutionEnabled", false);
        ReflectionTestUtils.setField(validator, "brandSpaceInitialRunEnabled", false);
        ReflectionTestUtils.setField(validator, "llmHealthEnabled", false);
        ReflectionTestUtils.setField(validator, "rewardQuotaCollectorEnabled", false);
        ReflectionTestUtils.setField(validator, "rewardQuotaRuntimeFallbackEnabled", false);
        ReflectionTestUtils.setField(validator, "rewardQuotaEnforcementEnabled", false);
        ReflectionTestUtils.setField(validator, "knowledgeAssetEnabled", false);
    }

    @Test
    void shouldPassWhenStagingSafetySwitchesAreClosed() {
        assertDoesNotThrow(() -> validator.validate());
    }

    @Test
    void shouldRejectProductionLookingDatabaseName() {
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:mysql://db.example.net:3306/geoprobe?useSSL=true");

        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    void shouldRejectRealExecutionEnabled() {
        ReflectionTestUtils.setField(validator, "engineExecutionEnabled", true);

        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    void shouldRejectSchedulingEnabled() {
        ReflectionTestUtils.setField(validator, "schedulingEnabled", true);

        assertThrows(IllegalStateException.class, () -> validator.validate());
    }

    @Test
    void shouldRejectLlmHealthEnabled() {
        ReflectionTestUtils.setField(validator, "llmHealthEnabled", true);

        assertThrows(IllegalStateException.class, () -> validator.validate());
    }
}
