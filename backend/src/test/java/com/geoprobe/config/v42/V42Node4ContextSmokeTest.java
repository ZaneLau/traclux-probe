package com.geoprobe.config.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.service.engine.v42.AliasMatcher;
import com.geoprobe.service.engine.v42.BrandDetectionChain;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node4AiConfig.class,
        BrandDetectionChain.class,
        AliasMatcher.class,
        V42Node4ContextSmokeTest.TestConfig.class
})
@TestPropertySource(properties = {
        "app.security.secret-assets.prompt-asset-dir=classpath:v42-test-prompt-assets",
        "app.security.secret-assets.prompt-asset-fail-fast=true"
})
class V42Node4ContextSmokeTest {

    @Autowired
    private BrandDetectionL3AiService brandDetectionL3AiService;

    @Autowired
    private BrandDetectionChain brandDetectionChain;

    @Test
    void shouldCreateNode4Beans() {
        assertNotNull(brandDetectionL3AiService);
        assertNotNull(brandDetectionChain);
    }

    @Configuration
    static class TestConfig {

        @Bean
        ChatLanguageModel creativeModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
