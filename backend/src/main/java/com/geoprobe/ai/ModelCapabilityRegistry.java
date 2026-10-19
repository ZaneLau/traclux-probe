package com.geoprobe.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ModelCapabilityRegistry {

    private static final Map<String, ModelCapability> PROVIDER_DEFAULTS = new HashMap<>();

    static {
        PROVIDER_DEFAULTS.put("volcengine", new ModelCapability(true, true, true, true));
        PROVIDER_DEFAULTS.put("deepseek", new ModelCapability(false, true, true, true));
        PROVIDER_DEFAULTS.put("zhipu", new ModelCapability(false, true, true, true));
    }

    public ModelCapability resolve(String provider, String model) {
        String normalizedProvider = normalize(provider);
        String normalizedModel = normalize(model);

        if ("volcengine".equals(normalizedProvider) && normalizedModel.contains("deepseek")) {
            return new ModelCapability(false, true, true, true);
        }

        ModelCapability capability = PROVIDER_DEFAULTS.get(normalizedProvider);
        return capability != null ? capability : ModelCapability.DEFAULT;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
