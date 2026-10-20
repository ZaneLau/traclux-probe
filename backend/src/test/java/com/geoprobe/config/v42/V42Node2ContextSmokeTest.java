package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.service.engine.v42.Node2TriggeredRulesTraceComposer;
import com.geoprobe.service.engine.v42.ProbeRenderingService;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node2AiConfig.class,
        ProbeRenderingService.class,
        ThreeLayerMappingEvaluator.class,
        Node2TriggeredRulesTraceComposer.class,
        V42Node2ContextSmokeTest.TestConfig.class
})
@TestPropertySource(properties = {
        "app.security.secret-assets.prompt-asset-dir=classpath:v42-test-prompt-assets",
        "app.security.secret-assets.prompt-asset-fail-fast=true"
})
class V42Node2ContextSmokeTest {

    @Autowired
    private ProbeRenderingAiService probeRenderingAiService;

    @Autowired
    private ProbeRenderingService probeRenderingService;

    @Test
    void shouldCreateNode2Beans() {
        assertNotNull(probeRenderingAiService);
        assertNotNull(probeRenderingService);
    }

    @Configuration
    static class TestConfig {

        @Bean
        @Qualifier("creativeModel")
        ChatLanguageModel creativeModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }

        @Bean
        @Qualifier("probeRenderingModel")
        ChatLanguageModel probeRenderingModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }
    }
}
