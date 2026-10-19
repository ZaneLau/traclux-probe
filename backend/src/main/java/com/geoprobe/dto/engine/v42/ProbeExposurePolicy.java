package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.Objects;

public enum ProbeExposurePolicy {
    BRAND_HIDDEN("brandHidden"),
    BRAND_VISIBLE("brandVisible");

    private final String contractValue;

    ProbeExposurePolicy(String contractValue) {
        this.contractValue = contractValue;
    }

    @JsonValue
    public String contractValue() {
        return contractValue;
    }

    public static ProbeExposurePolicy classifyOrthogonal(Map<String, String> abstractTags) {
        Objects.requireNonNull(abstractTags, "abstractTags 不能为空");
        return ProbeType.isM4Triggered(abstractTags) ? BRAND_VISIBLE : BRAND_HIDDEN;
    }
}
