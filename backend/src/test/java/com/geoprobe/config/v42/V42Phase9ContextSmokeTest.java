package com.geoprobe.config.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.service.engine.v42.AliasMatcher;
import com.geoprobe.service.engine.v42.BrandDetectionChain;
import com.geoprobe.service.engine.v42.CssmHiStateSpaceService;
import com.geoprobe.service.engine.v42.DashboardDataAggregator;
import com.geoprobe.service.engine.v42.DefenseCompletenessJudge;
import com.geoprobe.service.engine.v42.InsightGenerationService;
import com.geoprobe.service.engine.v42.Node2TriggeredRulesTraceComposer;
import com.geoprobe.service.engine.v42.OrthogonalMatrixService;
import com.geoprobe.service.engine.v42.ProbeQualityGateService;
import com.geoprobe.service.engine.v42.ProbeSimulationPlanningService;
import com.geoprobe.service.engine.v42.ProbeRenderingService;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator;
import com.geoprobe.service.engine.v42.V42DiagnosisOrchestrator;
import com.geoprobe.service.engine.v42.V42DiagnosisReportAssembler;
import com.geoprobe.service.engine.v42.V42KpiAggregationService;
import com.geoprobe.service.engine.v42.V42Node3SamplingGatewayService;
import com.geoprobe.service.engine.v42.V42StabilityService;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node0AiConfig.class,
        V42Node2AiConfig.class,
        V42Node3AiConfig.class,
        V42Node4AiConfig.class,
        V42Node5BAiConfig.class,
        V42Node5CAiConfig.class,
        CssmHiStateSpaceService.class,
        ProbeSimulationPlanningService.class,
        OrthogonalMatrixService.class,
        ProbeRenderingService.class,
        ProbeQualityGateService.class,
        ThreeLayerMappingEvaluator.class,
        Node2TriggeredRulesTraceComposer.class,
        V42Node3SamplingGatewayService.class,
        AliasMatcher.class,
        BrandDetectionChain.class,
        V42KpiAggregationService.class,
        V42StabilityService.class,
        InsightGenerationService.class,
        DefenseCompletenessJudge.class,
        DashboardDataAggregator.class,
        V42DiagnosisReportAssembler.class,
        V42DiagnosisOrchestrator.class,
        V42TargetProbeCountPolicy.class,
        V42RealtimeSearchPolicyResolver.class,
        V42Node3TargetEnginePolicyResolver.class,
        V42TaskOrderExecutionPolicyResolver.class,
        V42Phase9ContextSmokeTest.TestConfig.class
})
@TestPropertySource(properties = {
        "app.security.secret-assets.prompt-asset-dir=classpath:v42-test-prompt-assets",
        "app.security.secret-assets.prompt-asset-fail-fast=true"
})
class V42Phase9ContextSmokeTest {

    @Autowired
    private V42DiagnosisOrchestrator v42DiagnosisOrchestrator;

    @Autowired
    private V42DiagnosisReportAssembler v42DiagnosisReportAssembler;

    @Test
    void shouldCreatePhase9Beans() {
        assertNotNull(v42DiagnosisOrchestrator);
        assertNotNull(v42DiagnosisReportAssembler);
        assertTrue(v42DiagnosisOrchestrator.supports("4.2"));
        assertTrue(v42DiagnosisOrchestrator.supports("4.3"));
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

        @Bean
        @Qualifier("samplingModel")
        ChatLanguageModel samplingModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
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
