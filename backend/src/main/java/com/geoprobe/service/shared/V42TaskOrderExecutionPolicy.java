package com.geoprobe.service.shared;

import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;

import java.util.Objects;

public record V42TaskOrderExecutionPolicy(int targetProbeCount,
                                          RealtimeSearchPolicy realtimeSearchPolicy) {

    public V42TaskOrderExecutionPolicy {
        if (targetProbeCount <= 0) {
            throw new IllegalArgumentException("targetProbeCount 必须是正整数");
        }
        realtimeSearchPolicy = Objects.requireNonNull(realtimeSearchPolicy, "realtimeSearchPolicy 不能为空");
    }
}
