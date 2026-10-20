package com.geoprobe.common;

import com.geoprobe.entity.LlmCallLog;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetGuardedChatLanguageModelTest {

    @AfterEach
    void tearDown() {
        LlmCallContext.clear();
        LlmCallMetricsHolder.clear();
    }

    @Test
    void shouldCommitActualTokensOnSuccess() {
        ChatLanguageModel delegate = mock(ChatLanguageModel.class);
        LlmTokenBudgetService tokenBudgetService = mock(LlmTokenBudgetService.class);
        LlmTokenBudgetService.BudgetLease lease = new LlmTokenBudgetService.BudgetLease(
                "deepseek",
                "deepseek-v3.2",
                20,
                100,
                false
        );
        when(tokenBudgetService.reserveOrThrow("deepseek", "deepseek-v3.2")).thenReturn(lease);
        when(delegate.generate(org.mockito.ArgumentMatchers.anyList())).thenReturn(Response.from(
                AiMessage.from("ok"),
                new TokenUsage(12, 18, 30),
                FinishReason.STOP
        ));

        BudgetGuardedChatLanguageModel model = new BudgetGuardedChatLanguageModel(
                delegate,
                "deepseek",
                "deepseek-v3.2",
                tokenBudgetService,
                llmCallLog -> {
                }
        );

        model.generate(List.of(UserMessage.from("hello")));

        verify(tokenBudgetService).commit(lease, 30);
        verify(tokenBudgetService, never()).release(lease);
    }

    @Test
    void shouldRecordBudgetRejectionIntoLlmCallLogs() {
        ChatLanguageModel delegate = mock(ChatLanguageModel.class);
        LlmTokenBudgetService tokenBudgetService = mock(LlmTokenBudgetService.class);
        when(tokenBudgetService.reserveOrThrow("deepseek", "deepseek-v3.2"))
                .thenThrow(new LlmTokenBudgetExceededException("deepseek", "deepseek-v3.2", 100, 100));
        List<LlmCallLog> logs = new ArrayList<>();
        LlmCallContext.set(new LlmCallContext("diag-001", 1, "deepseek"));

        BudgetGuardedChatLanguageModel model = new BudgetGuardedChatLanguageModel(
                delegate,
                "deepseek",
                "deepseek-v3.2",
                tokenBudgetService,
                logs::add
        );

        LlmTokenBudgetExceededException ex = assertThrows(
                LlmTokenBudgetExceededException.class,
                () -> model.generate(List.of(UserMessage.from("hello")))
        );

        assertEquals("deepseek", ex.budgetProvider());
        assertEquals(1, logs.size());
        assertEquals("diag-001", logs.getFirst().getDiagnosisId());
        assertEquals(Integer.valueOf(2), logs.getFirst().getStatus());
        assertEquals("deepseek-v3.2", logs.getFirst().getModel());
    }
}
