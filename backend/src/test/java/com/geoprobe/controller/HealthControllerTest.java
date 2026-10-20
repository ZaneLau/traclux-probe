package com.geoprobe.controller;

import com.geoprobe.common.Result;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private ChatLanguageModel samplingModel;
    private ChatLanguageModel creativeModel;
    private ChatLanguageModel extractionModel;
    private Environment environment;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        samplingModel = mock(ChatLanguageModel.class);
        creativeModel = mock(ChatLanguageModel.class);
        extractionModel = mock(ChatLanguageModel.class);
        environment = mock(Environment.class);
        controller = new HealthController(samplingModel, creativeModel, extractionModel, environment);

        ReflectionTestUtils.setField(controller, "volcengineModel", "doubao-seed-1.8");
        ReflectionTestUtils.setField(controller, "deepseekModel", "deepseek-v3.2");
        ReflectionTestUtils.setField(controller, "datasourceUrl", "jdbc:mysql://staging-db.example.net:3306/traclux_staging?useSSL=true");
        ReflectionTestUtils.setField(controller, "stagingEnvironmentLabel", "staging");
        ReflectionTestUtils.setField(controller, "stagingProxyReady", false);
        ReflectionTestUtils.setField(controller, "runtimeMode", "staging");
        ReflectionTestUtils.setField(controller, "schedulingEnabled", false);
        ReflectionTestUtils.setField(controller, "engineExecutionEnabled", false);
        ReflectionTestUtils.setField(controller, "brandSpaceInitialRunEnabled", false);
        ReflectionTestUtils.setField(controller, "llmHealthEnabled", false);
        ReflectionTestUtils.setField(controller, "rewardQuotaCollectorEnabled", false);
        ReflectionTestUtils.setField(controller, "rewardQuotaEnforcementEnabled", false);
        ReflectionTestUtils.setField(controller, "knowledgeAssetEnabled", false);
    }

    @Test
    void shouldReportProductionSafeWhenStagingSwitchesAreClosed() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});

        Result<Map<String, Object>> response = controller.health();

        assertEquals(0, response.getCode());
        assertTrue(Boolean.TRUE.equals(response.getData().get("productionSafe")));
        assertEquals("staging", response.getData().get("environment"));
        assertEquals("staging", response.getData().get("runtimeMode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> datasource = (Map<String, Object>) response.getData().get("datasource");
        assertEquals("traclux_staging", datasource.get("database"));
    }

    @Test
    void shouldReportNotProductionSafeOutsideStagingProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        Result<Map<String, Object>> response = controller.health();

        assertFalse(Boolean.TRUE.equals(response.getData().get("productionSafe")));
    }

    @Test
    void shouldNotCallModelsWhenLlmHealthIsDisabled() {
        Result<Map<String, Object>> response = controller.llmHealth();

        assertEquals("DISABLED", response.getData().get("status"));
        verifyNoInteractions(samplingModel, creativeModel, extractionModel);
    }
}
