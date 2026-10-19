package com.geoprobe.config.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.DefenseCompletenessAiService;
import com.geoprobe.service.engine.v42.DashboardDataAggregator;
import com.geoprobe.service.engine.v42.DefenseCompletenessJudge;
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
        V42Node5CAiConfig.class,
        DefenseCompletenessJudge.class,
        DashboardDataAggregator.class,
        V42Node5CContextSmokeTest.TestConfig.class
})
class V42Node5CContextSmokeTest {

    @Autowired
    private DefenseCompletenessAiService defenseCompletenessAiService;

    @Autowired
    private DefenseCompletenessJudge defenseCompletenessJudge;

    @Autowired
    private DashboardDataAggregator dashboardDataAggregator;

    @Test
    void shouldCreateNode5CBeans() {
        assertNotNull(defenseCompletenessAiService);
        assertNotNull(defenseCompletenessJudge);
        assertNotNull(dashboardDataAggregator);
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
