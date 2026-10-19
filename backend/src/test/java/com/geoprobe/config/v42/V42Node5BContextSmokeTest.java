package com.geoprobe.config.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.CommercialInsightAiService;
import com.geoprobe.service.engine.v42.InsightGenerationService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node5BAiConfig.class,
        InsightGenerationService.class,
        V42Node5BContextSmokeTest.TestConfig.class
})
class V42Node5BContextSmokeTest {

    @Autowired
    private CommercialInsightAiService commercialInsightAiService;

    @Autowired
    private InsightGenerationService insightGenerationService;

    @Test
    void shouldCreateNode5BBeans() {
        assertNotNull(commercialInsightAiService);
        assertNotNull(insightGenerationService);
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
