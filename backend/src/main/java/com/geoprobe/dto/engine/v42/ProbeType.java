package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.Objects;

public enum ProbeType {
    OPEN_DISCOVERY("openDiscovery"),
    BRAND_ASSERTIVE("brandAssertive");

    private final String contractValue;

    ProbeType(String contractValue) {
        this.contractValue = contractValue;
    }

    @JsonValue
    public String contractValue() {
        return contractValue;
    }

    public static ProbeType classify(ProbeBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint 不能为空");
        return classify(blueprint.abstractTags());
    }

    public static ProbeType classify(Node3ProbeOutput probe) {
        Objects.requireNonNull(probe, "probe 不能为空");
        return fromExposurePolicy(probe.exposurePolicy());
    }

    public static ProbeType classify(Map<String, String> abstractTags) {
        Objects.requireNonNull(abstractTags, "abstractTags 不能为空");
        return fromExposurePolicy(ProbeExposurePolicy.classifyOrthogonal(abstractTags));
    }

    public static ProbeType fromExposurePolicy(ProbeExposurePolicy exposurePolicy) {
        Objects.requireNonNull(exposurePolicy, "exposurePolicy 不能为空");
        return exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE ? BRAND_ASSERTIVE : OPEN_DISCOVERY;
    }

    public static boolean isM4Triggered(Map<String, String> abstractTags) {
        Objects.requireNonNull(abstractTags, "abstractTags 不能为空");
        return "verification".equals(abstractTags.get("D1")) && "optimal".equals(abstractTags.get("D4"));
    }

}
