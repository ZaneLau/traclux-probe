package com.geoprobe.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class VolcengineRewardModelIdentityResolver {

    @Value("${llm.deepseek.model:deepseek-v3.2}")
    private String deepseekModel;

    @Value("${llm.deepseek.endpoint-id:}")
    private String deepseekEndpointId;

    @Value("${llm.volcengine.model:doubao-seed-1.8}")
    private String doubaoModel;

    @Value("${llm.volcengine.endpoint-id:}")
    private String doubaoEndpointId;

    @Value("${llm.volcengine.realtime-endpoint-id:${llm.volcengine.endpoint-id:${llm.volcengine.model:doubao-seed-1.8}}}")
    private String doubaoRealtimeEndpointId;

    @Value("${llm.kimi.model:kimi-k2-thinking-251104}")
    private String kimiModel;

    @Value("${llm.glm.model:glm-4-7-251222}")
    private String glmModel;

    public ResolvedModelIdentity resolve(String providerCode, String runtimeModelId) {
        String provider = normalizeProvider(providerCode, runtimeModelId);
        String runtime = normalizeModelId(runtimeModelId);
        boolean rewardManaged = requiresRewardGate(provider, runtime);
        if (!rewardManaged) {
            return new ResolvedModelIdentity(provider, runtime, "", false);
        }
        String monitor = resolveMonitorModelId(provider, runtime);
        return new ResolvedModelIdentity(provider, runtime, monitor, true);
    }

    public String defaultDeepseekRuntimeModelId() {
        String runtime = firstNonBlank(deepseekEndpointId, deepseekModel);
        return normalizeModelId(runtime);
    }

    public String defaultDoubaoRealtimeRuntimeModelId() {
        String runtime = firstNonBlank(doubaoRealtimeEndpointId, doubaoEndpointId, doubaoModel);
        return normalizeModelId(runtime);
    }

    private boolean requiresRewardGate(String providerCode, String runtimeModelId) {
        if (!StringUtils.hasText(providerCode) && !StringUtils.hasText(runtimeModelId)) {
            return false;
        }
        return switch (providerCode) {
            case "doubao", "volcengine", "deepseek", "kimi", "glm" -> true;
            default -> looksLikeVolcengineModel(runtimeModelId);
        };
    }

    private String resolveMonitorModelId(String providerCode, String runtimeModelId) {
        return switch (providerCode) {
            case "doubao", "volcengine" -> resolveDoubaoMonitorModelId(runtimeModelId);
            case "deepseek" -> firstNonBlank(
                    normalizeModelId(deepseekModel),
                    runtimeModelId.startsWith("ep-") ? "" : runtimeModelId
            );
            case "kimi" -> firstNonBlank(normalizeModelId(kimiModel), runtimeModelId);
            case "glm" -> firstNonBlank(normalizeModelId(glmModel), runtimeModelId);
            default -> looksLikeVolcengineModel(runtimeModelId) ? runtimeModelId : "";
        };
    }

    private String resolveDoubaoMonitorModelId(String runtimeModelId) {
        if (StringUtils.hasText(runtimeModelId) && !runtimeModelId.startsWith("ep-")) {
            return runtimeModelId;
        }
        return firstNonBlank(
                normalizeModelId(doubaoModel),
                normalizeModelId(doubaoRealtimeEndpointId),
                normalizeModelId(doubaoEndpointId)
        );
    }

    private boolean looksLikeVolcengineModel(String modelId) {
        return StringUtils.hasText(modelId)
                && (modelId.startsWith("doubao-")
                || modelId.startsWith("deepseek-")
                || modelId.startsWith("kimi-")
                || modelId.startsWith("glm-"));
    }

    private String normalizeProvider(String providerCode, String runtimeModelId) {
        String provider = normalizeText(providerCode);
        if (StringUtils.hasText(provider)) {
            return switch (provider) {
                case "deepseek-ai" -> "deepseek";
                default -> provider;
            };
        }
        if (StringUtils.hasText(runtimeModelId)) {
            String model = normalizeModelId(runtimeModelId);
            if (model.startsWith("doubao-")) {
                return "doubao";
            }
            if (model.startsWith("deepseek-")) {
                return "deepseek";
            }
            if (model.startsWith("kimi-")) {
                return "kimi";
            }
            if (model.startsWith("glm-")) {
                return "glm";
            }
        }
        return "";
    }

    private String normalizeModelId(String value) {
        return VolcengineRewardSnapshotReader.normalizeModelId(value);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public record ResolvedModelIdentity(String providerCode,
                                        String runtimeModelId,
                                        String monitorModelId,
                                        boolean rewardManaged) {
    }
}
