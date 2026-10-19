package com.geoprobe.common;

public class LlmTokenBudgetExceededException extends RuntimeException {

    private final String budgetProvider;
    private final String modelId;
    private final long dailyBudgetTokens;
    private final long consumedTokens;

    public LlmTokenBudgetExceededException(String budgetProvider,
                                           String modelId,
                                           long dailyBudgetTokens,
                                           long consumedTokens) {
        super(buildMessage(budgetProvider, modelId, dailyBudgetTokens, consumedTokens));
        this.budgetProvider = budgetProvider;
        this.modelId = modelId;
        this.dailyBudgetTokens = dailyBudgetTokens;
        this.consumedTokens = consumedTokens;
    }

    public String budgetProvider() {
        return budgetProvider;
    }

    public String modelId() {
        return modelId;
    }

    public long dailyBudgetTokens() {
        return dailyBudgetTokens;
    }

    public long consumedTokens() {
        return consumedTokens;
    }

    private static String buildMessage(String budgetProvider,
                                       String modelId,
                                       long dailyBudgetTokens,
                                       long consumedTokens) {
        return "模型日累计 token 预算已耗尽: provider=%s, model=%s, consumed=%d, budget=%d"
                .formatted(budgetProvider, modelId, consumedTokens, dailyBudgetTokens);
    }
}
