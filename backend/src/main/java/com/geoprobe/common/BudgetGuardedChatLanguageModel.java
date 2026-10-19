package com.geoprobe.common;

import com.geoprobe.entity.LlmCallLog;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BudgetGuardedChatLanguageModel implements ChatLanguageModel {

    private static final int STATUS_FAILED = 2;

    private final ChatLanguageModel delegate;
    private final String budgetProvider;
    private final String modelId;
    private final LlmTokenBudgetService tokenBudgetService;
    private final Consumer<LlmCallLog> llmCallLogSink;

    public BudgetGuardedChatLanguageModel(ChatLanguageModel delegate,
                                          String budgetProvider,
                                          String modelId,
                                          LlmTokenBudgetService tokenBudgetService,
                                          Consumer<LlmCallLog> llmCallLogSink) {
        this.delegate = delegate;
        this.budgetProvider = normalize(budgetProvider);
        this.modelId = normalize(modelId);
        this.tokenBudgetService = tokenBudgetService;
        this.llmCallLogSink = llmCallLogSink;
    }

    public ChatLanguageModel delegate() {
        return delegate;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        LlmCallMetricsHolder.clear();
        LlmTokenBudgetService.BudgetLease lease = null;
        try {
            lease = tokenBudgetService == null
                    ? LlmTokenBudgetService.BudgetLease.noop()
                    : tokenBudgetService.reserveOrThrow(budgetProvider, modelId);
            Response<AiMessage> response = delegate.generate(messages);
            commitFromResponse(lease, response);
            return response;
        } catch (LlmTokenBudgetExceededException ex) {
            LlmManualCallLogger.record(
                    llmCallLogSink,
                    budgetProvider,
                    modelId,
                    null,
                    null,
                    0,
                    STATUS_FAILED,
                    ex.getMessage()
            );
            throw ex;
        } catch (RuntimeException ex) {
            commitFailureUsageOrRelease(lease);
            throw ex;
        } finally {
            LlmCallMetricsHolder.clear();
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private void commitFromResponse(LlmTokenBudgetService.BudgetLease lease, Response<AiMessage> response) {
        if (lease == null || lease.noOp() || tokenBudgetService == null) {
            return;
        }
        TokenUsage tokenUsage = response == null ? null : response.tokenUsage();
        Integer totalTokens = tokenUsage == null ? null : tokenUsage.totalTokenCount();
        if (totalTokens == null) {
            LlmCallMetrics metrics = LlmCallMetricsHolder.get();
            totalTokens = metrics == null ? null : metrics.totalTokens();
        }
        tokenBudgetService.commit(lease, totalTokens);
    }

    private void commitFailureUsageOrRelease(LlmTokenBudgetService.BudgetLease lease) {
        if (lease == null || lease.noOp() || tokenBudgetService == null) {
            return;
        }
        LlmCallMetrics metrics = LlmCallMetricsHolder.get();
        Integer totalTokens = metrics == null ? null : metrics.totalTokens();
        if (totalTokens != null && totalTokens > 0) {
            tokenBudgetService.commit(lease, totalTokens);
            return;
        }
        tokenBudgetService.release(lease);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
