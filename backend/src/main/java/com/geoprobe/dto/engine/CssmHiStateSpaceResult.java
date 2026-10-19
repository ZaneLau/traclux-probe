package com.geoprobe.dto.engine;

import java.util.Objects;

public record CssmHiStateSpaceResult(
        CategoryVariablePool pool,
        boolean fallbackUsed,
        String source
) {

    public CssmHiStateSpaceResult {
        pool = Objects.requireNonNull(pool, "pool 不能为空");
        source = source == null ? "LLM" : source;
    }
}
