package com.geoprobe.service.shared;

import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V42RealtimeSearchPolicyResolver {

    private boolean enabledDefault = false;
    private boolean allowed = true;
    private String providerDefault = "doubao";
    private double minEvidenceSuccessRateDefault = 0.90d;
    private int maxEvidenceRetriesDefault = 2;
    private int maxBackfillRoundsDefault = 2;

    @Value("${app.engine.sampling.realtime-search.enabled:false}")
    void setEnabledDefault(boolean enabledDefault) {
        this.enabledDefault = enabledDefault;
    }

    @Value("${app.engine.sampling.realtime-search.allowed:true}")
    void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Value("${app.engine.sampling.realtime-search.provider:doubao}")
    void setProviderDefault(String providerDefault) {
        this.providerDefault = StringUtils.hasText(providerDefault)
                ? providerDefault.trim().toLowerCase()
                : "doubao";
    }

    @Value("${app.engine.sampling.realtime-search.min-evidence-success-rate:0.9}")
    void setMinEvidenceSuccessRateDefault(double minEvidenceSuccessRateDefault) {
        this.minEvidenceSuccessRateDefault = minEvidenceSuccessRateDefault;
    }

    @Value("${app.engine.sampling.realtime-search.max-evidence-retries:2}")
    void setMaxEvidenceRetriesDefault(int maxEvidenceRetriesDefault) {
        this.maxEvidenceRetriesDefault = maxEvidenceRetriesDefault;
    }

    @Value("${app.engine.sampling.realtime-search.max-backfill-rounds:2}")
    void setMaxBackfillRoundsDefault(int maxBackfillRoundsDefault) {
        this.maxBackfillRoundsDefault = maxBackfillRoundsDefault;
    }

    public RealtimeSearchPolicy defaultPolicy() {
        return new RealtimeSearchPolicy(
                allowed && enabledDefault,
                providerDefault,
                minEvidenceSuccessRateDefault,
                maxEvidenceRetriesDefault,
                maxBackfillRoundsDefault
        );
    }

    public RealtimeSearchPolicy resolveFromTaskOrderHints(Map<String, Object> taskOrderHints) {
        Map<String, Object> hints = taskOrderHints == null ? Map.of() : taskOrderHints;
        Map<String, Object> settings = nestedMap(hints.get("samplingRealtimeSearch"));
        boolean hasNestedFormat = !settings.isEmpty();
        boolean hasDottedFormat = hints.containsKey("samplingRealtimeSearch.enabled")
                || hints.containsKey("samplingRealtimeSearch.provider")
                || hints.containsKey("samplingRealtimeSearch.minEvidenceSuccessRate")
                || hints.containsKey("samplingRealtimeSearch.maxEvidenceRetries")
                || hints.containsKey("samplingRealtimeSearch.maxBackfillRounds");
        if (hasNestedFormat && hasDottedFormat) {
            throw new IllegalArgumentException("samplingRealtimeSearch 不允许同时使用嵌套对象与点号键两种格式");
        }
        Object enabled = hasNestedFormat ? settings.get("enabled") : hints.get("samplingRealtimeSearch.enabled");
        Object provider = hasNestedFormat ? settings.get("provider") : hints.get("samplingRealtimeSearch.provider");
        Object minEvidenceSuccessRate = hasNestedFormat
                ? settings.get("minEvidenceSuccessRate")
                : hints.get("samplingRealtimeSearch.minEvidenceSuccessRate");
        Object maxEvidenceRetries = hasNestedFormat
                ? settings.get("maxEvidenceRetries")
                : hints.get("samplingRealtimeSearch.maxEvidenceRetries");
        Object maxBackfillRounds = hasNestedFormat
                ? settings.get("maxBackfillRounds")
                : hints.get("samplingRealtimeSearch.maxBackfillRounds");
        return new RealtimeSearchPolicy(
                allowed && readBoolean(enabled, enabledDefault),
                readString(provider, providerDefault),
                readDouble(minEvidenceSuccessRate, minEvidenceSuccessRateDefault),
                readInt(maxEvidenceRetries, maxEvidenceRetriesDefault),
                readInt(maxBackfillRounds, maxBackfillRoundsDefault)
        );
    }

    @SuppressWarnings("unchecked")
    public RealtimeSearchPolicy resolveFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return defaultPolicy();
        }
        Object rawBusinessHints = taskOrderSnapshot.get("businessHints");
        if (rawBusinessHints instanceof Map<?, ?> hints) {
            return resolveFromTaskOrderHints((Map<String, Object>) hints);
        }
        return defaultPolicy();
    }

    public Map<String, Object> normalizeTaskOrderHints(Map<String, Object> taskOrderHints, RealtimeSearchPolicy policy) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (taskOrderHints != null && !taskOrderHints.isEmpty()) {
            normalized.putAll(taskOrderHints);
        }
        normalized.remove("samplingRealtimeSearch.enabled");
        normalized.remove("samplingRealtimeSearch.provider");
        normalized.remove("samplingRealtimeSearch.minEvidenceSuccessRate");
        normalized.remove("samplingRealtimeSearch.maxEvidenceRetries");
        normalized.remove("samplingRealtimeSearch.maxBackfillRounds");
        normalized.put("samplingRealtimeSearch", Map.of(
                "enabled", policy.enabled(),
                "provider", policy.provider(),
                "minEvidenceSuccessRate", policy.minEvidenceSuccessRate(),
                "maxEvidenceRetries", policy.maxEvidenceRetries(),
                "maxBackfillRounds", policy.maxBackfillRounds()
        ));
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("taskOrder.businessHints.samplingRealtimeSearch 必须是对象");
        }
        return (Map<String, Object>) raw;
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        return defaultValue;
    }

    private String readString(Object value, String defaultValue) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim().toLowerCase();
        }
        return defaultValue;
    }

    private int readInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double readDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
