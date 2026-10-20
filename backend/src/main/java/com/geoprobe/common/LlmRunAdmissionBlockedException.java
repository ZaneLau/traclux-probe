package com.geoprobe.common;

public class LlmRunAdmissionBlockedException extends LlmTokenBudgetExceededException {

    public LlmRunAdmissionBlockedException(String budgetProvider,
                                           String modelId,
                                           String reason) {
        super(budgetProvider, modelId, 0L, 0L);
        this.reason = reason == null ? "运行准入门拒绝放行" : reason.trim();
    }

    private final String reason;

    public String reason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return reason;
    }
}
