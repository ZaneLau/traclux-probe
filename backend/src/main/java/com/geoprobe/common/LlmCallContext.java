package com.geoprobe.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallContext {

    private static final ThreadLocal<LlmCallContext> HOLDER = new ThreadLocal<>();

    private String diagnosisId;
    private Integer module;
    private String provider;

    public static void set(LlmCallContext context) {
        HOLDER.set(context);
    }

    public static LlmCallContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
