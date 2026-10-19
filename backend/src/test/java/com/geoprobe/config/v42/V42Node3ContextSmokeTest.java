package com.geoprobe.config.v42;

import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.service.engine.v42.ModelAdapter;
import com.geoprobe.service.engine.v42.V42Node3SamplingGatewayService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node3AiConfig.class,
        V42Node3SamplingGatewayService.class,
        V42Node3ContextSmokeTest.TestConfig.class
})
class V42Node3ContextSmokeTest {

    @Autowired
    @Qualifier("v42DoubaoAdapter")
    private ModelAdapter modelAdapter;

    @Autowired
    private V42Node3SamplingGatewayService samplingGatewayService;

    @Test
    void shouldCreateNode3Beans() {
        assertNotNull(modelAdapter);
        assertNotNull(samplingGatewayService);
    }

    @Configuration
    static class TestConfig {

        @Bean
        @Qualifier("samplingModel")
        ChatLanguageModel samplingModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }

        @Bean
        LlmTokenBudgetService llmTokenBudgetService() {
            return Mockito.mock(LlmTokenBudgetService.class);
        }

        @Bean(destroyMethod = "shutdownNow")
        @Qualifier("node3SamplingExecutor")
        ExecutorService node3SamplingExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean(destroyMethod = "shutdownNow")
        @Qualifier("node3SamplingCallExecutor")
        ExecutorService node3SamplingCallExecutor() {
            return Executors.newSingleThreadExecutor();
        }
    }
}
