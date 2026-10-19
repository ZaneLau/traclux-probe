package com.geoprobe.common;

import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmTokenBudgetServiceTest {

    @Mock
    private LlmCallLogMapper llmCallLogMapper;

    @Mock
    private ModelInvocationRecordMapper modelInvocationRecordMapper;

    @Mock
    private VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    private LlmTokenBudgetService service;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("modelInvocationRecordMapper", modelInvocationRecordMapper);
        service = new LlmTokenBudgetService(
                llmCallLogMapper,
                beanFactory.getBeanProvider(ModelInvocationRecordMapper.class),
                volcengineRewardRunAdmissionService
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "defaultDailyTokenBudgetPerModel", 100L);
        ReflectionTestUtils.setField(service, "defaultReservationTokens", 20L);
        ReflectionTestUtils.setField(service, "zoneIdText", "Asia/Shanghai");
        ReflectionTestUtils.setField(service, "budgetOverridesText", "");
        ReflectionTestUtils.setField(service, "databaseV1Enabled", true);
        lenient().when(volcengineRewardRunAdmissionService.isAllowed(anyString(), anyString())).thenReturn(true);
        service.refreshRuntimeConfig();
    }

    @Test
    void shouldCombineNonNode3LogsWithMaxNode3Ledger() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(30L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(40L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(55L);

        long usage = service.readEffectiveUsage("deepseek", "deepseek-v3.2");

        assertEquals(85L, usage);
    }

    @Test
    void shouldReserveThenCommitActualTokens() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(0L);

        LlmTokenBudgetService.BudgetLease lease = service.reserveOrThrow("glm", "glm-4-7-251222");
        assertEquals(20L, service.readEffectiveUsage("glm", "glm-4-7-251222"));

        service.commit(lease, 12);

        assertEquals(12L, service.readEffectiveUsage("glm", "glm-4-7-251222"));
    }

    @Test
    void shouldHonorProviderModelOverride() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(0L);
        ReflectionTestUtils.setField(service, "budgetOverridesText", "deepseek:deepseek-v3.2=15");
        service.refreshRuntimeConfig();

        LlmTokenBudgetService.BudgetLease lease = service.reserveOrThrow("deepseek", "deepseek-v3.2");
        service.commit(lease, 15);

        LlmTokenBudgetExceededException ex = assertThrows(
                LlmTokenBudgetExceededException.class,
                () -> service.reserveOrThrow("deepseek", "deepseek-v3.2")
        );
        assertEquals("deepseek", ex.budgetProvider());
        assertEquals("deepseek-v3.2", ex.modelId());
        assertEquals(15L, service.resolveBudgetLimitForTest("deepseek", "deepseek-v3.2"));
    }

    @Test
    void shouldRecommendSafeParallelReservationsFromRemainingHeadroom() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(45L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(0L);

        int recommended = service.recommendMaxConcurrentReservations("deepseek", "deepseek-v3.2", 8);

        assertEquals(2, recommended);
        assertEquals(55L, service.readRemainingBudget("deepseek", "deepseek-v3.2"));
    }

    @Test
    void shouldKeepSingleParallelSlotWhenRemainingBudgetIsBelowReservation() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(85L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(0L);

        int recommended = service.recommendMaxConcurrentReservations("deepseek", "deepseek-v3.2", 8);

        assertEquals(1, recommended);
        assertEquals(15L, service.readRemainingBudget("deepseek", "deepseek-v3.2"));
    }

    @Test
    void shouldExposeStructuredConcurrencyRecommendation() {
        when(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(45L);
        when(llmCallLogMapper.sumTokensByModelAndModuleBetween(anyString(), anyInt(), any(), any()))
                .thenReturn(0L);
        when(modelInvocationRecordMapper.sumTotalTokensByModelAndNodeBetween(anyString(), anyString(), any(), any()))
                .thenReturn(0L);

        LlmTokenBudgetService.ConcurrencyRecommendation recommendation =
                service.recommendConcurrency("deepseek", "deepseek-v3.2", 8);

        assertEquals("deepseek", recommendation.provider());
        assertEquals("deepseek-v3.2", recommendation.modelId());
        assertEquals(8, recommendation.desiredConcurrency());
        assertEquals(2, recommendation.effectiveConcurrency());
        assertEquals(55L, recommendation.remainingTokens());
        assertEquals(true, recommendation.budgetManaged());
        assertEquals(true, recommendation.limited());
    }

    @Test
    void shouldTreatAdmissionBlockedModelAsUnavailable() {
        when(volcengineRewardRunAdmissionService.isAllowed("deepseek", "ep-deepseek")).thenReturn(false);

        assertEquals(false, service.isBudgetAvailable("deepseek", "ep-deepseek"));
        assertEquals(0L, service.readRemainingBudget("deepseek", "ep-deepseek"));
    }
}
