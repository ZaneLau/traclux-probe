package com.geoprobe.dto.engine.v42;

import java.util.Objects;

public record CssmHiStateSpaceResult(
        CategoryVariablePool pool,
        boolean fallbackUsed,
        String source,
        String fallbackReasonCode
) {

    public CssmHiStateSpaceResult {
        pool = Objects.requireNonNull(pool, "pool 不能为空");
        source = source == null ? "LLM" : source;
        fallbackReasonCode = normalizeOptional(fallbackReasonCode);
    }

    public CssmHiStateSpaceResult(CategoryVariablePool pool,
                                  boolean fallbackUsed,
                                  String source) {
        this(pool, fallbackUsed, source, null);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
