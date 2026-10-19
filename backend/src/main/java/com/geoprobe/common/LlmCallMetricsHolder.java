package com.geoprobe.common;

public final class LlmCallMetricsHolder {

    private static final ThreadLocal<LlmCallMetrics> HOLDER = new ThreadLocal<>();

    private LlmCallMetricsHolder() {
    }

    public static void set(LlmCallMetrics metrics) {
        HOLDER.set(metrics);
    }

    public static LlmCallMetrics get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
