package com.geoprobe.service.shared;

import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class V42Node3TargetEnginePolicyResolver {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "doubao",
            "chatgpt",
            "deepseek",
            "kimi",
            "glm",
            "minimax"
    );

    private String defaultProvider = "doubao";
    private String defaultModelId = null;
    private boolean defaultDisableReasoning = true;
    private String doubaoConfiguredModelId = "doubao-seed-1.8";
    private String doubaoDefaultModelId = null;
    private String chatgptConfiguredModelId = "gpt-5-mini";
    private String chatgptDefaultModelId = "gpt-5-mini";
    private String deepseekConfiguredModelId = "deepseek-v3.2";
    private String deepseekDefaultModelId = "deepseek-v3.2";
    private String kimiConfiguredModelId = "kimi-k2-thinking-251104";
    private String kimiDefaultModelId = "kimi-k2-thinking-251104";
    private String glmConfiguredModelId = "glm-4-7-251222";
    private String glmDefaultModelId = "glm-4-7-251222";
    private String minimaxConfiguredModelId = "MiniMax-M1";
    private String minimaxDefaultModelId = "MiniMax-M1";

    @Value("${app.engine.sampling.target-provider:doubao}")
    void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = StringUtils.hasText(defaultProvider)
                ? defaultProvider.trim()
                : "doubao";
    }

    @Value("${app.engine.sampling.target-model-id:}")
    void setDefaultModelId(String defaultModelId) {
        this.defaultModelId = StringUtils.hasText(defaultModelId)
                ? defaultModelId.trim()
                : null;
    }

    @Value("${app.engine.sampling.disable-reasoning:true}")
    void setDefaultDisableReasoning(boolean defaultDisableReasoning) {
        this.defaultDisableReasoning = defaultDisableReasoning;
    }

    @Value("${llm.volcengine.model:doubao-seed-1.8}")
    void setDoubaoConfiguredModelId(String doubaoConfiguredModelId) {
        this.doubaoConfiguredModelId = normalizeOptionalModelId(doubaoConfiguredModelId);
    }

    @Value("${llm.volcengine.realtime-endpoint-id:${llm.volcengine.endpoint-id:${llm.volcengine.model:doubao-seed-1.8}}}")
    void setDoubaoDefaultModelId(String doubaoDefaultModelId) {
        this.doubaoDefaultModelId = normalizeOptionalModelId(doubaoDefaultModelId);
    }

    @Value("${llm.openai.model:gpt-5-mini}")
    void setChatgptConfiguredModelId(String chatgptConfiguredModelId) {
        this.chatgptConfiguredModelId = normalizeOptionalModelId(chatgptConfiguredModelId);
    }

    @Value("${llm.openai.model:gpt-5-mini}")
    void setChatgptDefaultModelId(String chatgptDefaultModelId) {
        this.chatgptDefaultModelId = normalizeOptionalModelId(chatgptDefaultModelId);
    }

    @Value("${llm.deepseek.model:deepseek-v3.2}")
    void setDeepseekConfiguredModelId(String deepseekConfiguredModelId) {
        this.deepseekConfiguredModelId = normalizeOptionalModelId(deepseekConfiguredModelId);
    }

    @Value("${llm.deepseek.endpoint-id:${llm.deepseek.model:deepseek-v3.2}}")
    void setDeepseekDefaultModelId(String deepseekDefaultModelId) {
        this.deepseekDefaultModelId = normalizeOptionalModelId(deepseekDefaultModelId);
    }

    @Value("${llm.kimi.model:kimi-k2-thinking-251104}")
    void setKimiConfiguredModelId(String kimiConfiguredModelId) {
        this.kimiConfiguredModelId = normalizeOptionalModelId(kimiConfiguredModelId);
    }

    @Value("${llm.kimi.model:kimi-k2-thinking-251104}")
    void setKimiDefaultModelId(String kimiDefaultModelId) {
        this.kimiDefaultModelId = normalizeOptionalModelId(kimiDefaultModelId);
    }

    @Value("${llm.glm.model:glm-4-7-251222}")
    void setGlmConfiguredModelId(String glmConfiguredModelId) {
        this.glmConfiguredModelId = normalizeOptionalModelId(glmConfiguredModelId);
    }

    @Value("${llm.glm.model:glm-4-7-251222}")
    void setGlmDefaultModelId(String glmDefaultModelId) {
        this.glmDefaultModelId = normalizeOptionalModelId(glmDefaultModelId);
    }

    @Value("${llm.minimax.model:MiniMax-M1}")
    void setMinimaxConfiguredModelId(String minimaxConfiguredModelId) {
        this.minimaxConfiguredModelId = normalizeOptionalModelId(minimaxConfiguredModelId);
    }

    @Value("${llm.minimax.model:MiniMax-M1}")
    void setMinimaxDefaultModelId(String minimaxDefaultModelId) {
        this.minimaxDefaultModelId = normalizeOptionalModelId(minimaxDefaultModelId);
    }

    public Node3TargetEnginePolicy defaultPolicy() {
        return new Node3TargetEnginePolicy(defaultProvider, defaultModelId, defaultDisableReasoning);
    }

    public Node3TargetEnginePolicy resolveForFrontDeskPlanning(Map<String, Object> taskOrderHints) {
        TargetEngineFields fields = readFields(taskOrderHints, null, false, "frontdesk_preview");
        String provider = resolveProvider(fields.provider(), fields.modelId(), false, "frontdesk_preview");
        String modelId = resolveModelId(provider, fields.modelId());
        boolean disableReasoning = fields.disableReasoning() == null
                ? defaultDisableReasoning
                : fields.disableReasoning();
        return new Node3TargetEnginePolicy(provider, modelId, disableReasoning);
    }

    public Node3TargetEnginePolicy resolveFromTaskOrder(FrontDeskTaskOrder taskOrder) {
        if (taskOrder == null) {
            return defaultPolicy();
        }
        try {
            return resolveRequiredFromTaskOrder(taskOrder, resolveScene(taskOrder));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        }
    }

    public Node3TargetEnginePolicy resolveRequiredFromTaskOrder(FrontDeskTaskOrder taskOrder, String scene) {
        if (taskOrder == null) {
            throw new IllegalStateException("V4.2 缺少合法的目标引擎快照" + sceneSuffix(scene));
        }
        TargetEngineFields fields = readFields(taskOrder.businessHints(), taskOrder.targetModelProduct(), true, scene);
        String provider = resolveProvider(fields.provider(), fields.modelId(), true, scene);
        if (!StringUtils.hasText(provider)) {
            throw new IllegalStateException("V4.2 缺少合法的目标引擎 provider" + sceneSuffix(scene));
        }
        String modelId = resolveModelId(provider, fields.modelId());
        boolean disableReasoning = fields.disableReasoning() == null
                ? defaultDisableReasoning
                : fields.disableReasoning();
        return new Node3TargetEnginePolicy(provider, modelId, disableReasoning);
    }

    @SuppressWarnings("unchecked")
    public Node3TargetEnginePolicy resolveRequiredFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot,
                                                                       String scene) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            throw new IllegalStateException("V4.2 缺少合法的目标引擎快照" + sceneSuffix(scene));
        }
        Object rawBusinessHints = taskOrderSnapshot.get("businessHints");
        Map<String, Object> hints = rawBusinessHints instanceof Map<?, ?> hintMap
                ? (Map<String, Object>) hintMap
                : Map.of();
        String targetModelProduct = taskOrderSnapshot.get("targetModelProduct") instanceof String text
                ? text
                : null;
        TargetEngineFields fields = readFields(hints, targetModelProduct, true, scene);
        String provider = resolveProvider(fields.provider(), fields.modelId(), true, scene);
        if (!StringUtils.hasText(provider)) {
            throw new IllegalStateException("V4.2 缺少合法的目标引擎 provider" + sceneSuffix(scene));
        }
        String modelId = resolveModelId(provider, fields.modelId());
        boolean disableReasoning = fields.disableReasoning() == null
                ? defaultDisableReasoning
                : fields.disableReasoning();
        return new Node3TargetEnginePolicy(provider, modelId, disableReasoning);
    }

    public Map<String, Object> normalizeTaskOrderHints(Map<String, Object> taskOrderHints,
                                                       Node3TargetEnginePolicy policy) {
        Node3TargetEnginePolicy safePolicy = policy == null ? defaultPolicy() : policy;
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (taskOrderHints != null && !taskOrderHints.isEmpty()) {
            normalized.putAll(taskOrderHints);
        }
        normalized.remove("samplingEngine.provider");
        normalized.remove("samplingEngine.modelId");
        normalized.remove("samplingEngine.model_id");
        normalized.remove("samplingEngine.disableReasoning");
        normalized.remove("modelId");
        normalized.remove("model_id");
        LinkedHashMap<String, Object> samplingEngine = new LinkedHashMap<>();
        samplingEngine.put("provider", safePolicy.provider());
        if (StringUtils.hasText(safePolicy.modelId())) {
            samplingEngine.put("modelId", safePolicy.modelId());
        }
        samplingEngine.put("disableReasoning", safePolicy.disableReasoning());
        normalized.put("samplingEngine", Map.copyOf(samplingEngine));
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("taskOrder.businessHints.samplingEngine 必须是对象");
        }
        return (Map<String, Object>) raw;
    }

    private String firstText(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private Boolean firstBoolean(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                String normalized = text.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized)) {
                    return false;
                }
            }
        }
        return null;
    }

    private TargetEngineFields readFields(Map<String, Object> taskOrderHints,
                                          String taskOrderTargetModelProduct,
                                          boolean strict,
                                          String scene) {
        Map<String, Object> hints = taskOrderHints == null ? Map.of() : taskOrderHints;
        Map<String, Object> nested = nestedMap(hints.get("samplingEngine"));
        boolean hasNestedFormat = !nested.isEmpty();
        boolean hasDottedFormat = hints.containsKey("samplingEngine.provider")
                || hints.containsKey("samplingEngine.modelId")
                || hints.containsKey("samplingEngine.model_id")
                || hints.containsKey("samplingEngine.disableReasoning")
                || hints.containsKey("modelId")
                || hints.containsKey("model_id");
        if (hasNestedFormat && hasDottedFormat) {
            throw new IllegalArgumentException("samplingEngine 不允许同时使用嵌套对象与点号键两种格式");
        }
        String provider = firstText(
                nested.get("provider"),
                hints.get("samplingEngine.provider")
        );
        String modelId = firstText(
                nested.get("modelId"),
                nested.get("model_id"),
                hints.get("samplingEngine.modelId"),
                hints.get("samplingEngine.model_id"),
                hints.get("modelId"),
                hints.get("model_id"),
                taskOrderTargetModelProduct
        );
        Boolean disableReasoning = firstBoolean(
                nested.get("disableReasoning"),
                hints.get("samplingEngine.disableReasoning")
        );
        if (strict && !StringUtils.hasText(provider) && !StringUtils.hasText(modelId)) {
            throw new IllegalStateException("V4.2 缺少合法的目标引擎快照" + sceneSuffix(scene));
        }
        return new TargetEngineFields(provider, modelId, disableReasoning);
    }

    private String resolveProvider(String explicitProvider,
                                   String modelId,
                                   boolean strict,
                                   String scene) {
        String normalized = normalizeProviderName(explicitProvider);
        if (!StringUtils.hasText(normalized) && StringUtils.hasText(modelId)) {
            normalized = Node3TargetEnginePolicy.inferProviderFromModelId(modelId);
        }
        if (!StringUtils.hasText(normalized)) {
            if (strict) {
                throw new IllegalStateException("V4.2 缺少合法的目标引擎 provider" + sceneSuffix(scene));
            }
            normalized = defaultProvider;
        }
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("samplingEngine.provider 不受支持: " + normalized);
        }
        return normalized;
    }

    private String resolveModelId(String provider, String explicitModelId) {
        if (StringUtils.hasText(explicitModelId)) {
            return canonicalizeConfiguredModelId(provider, explicitModelId.trim());
        }
        if (StringUtils.hasText(defaultModelId)) {
            return defaultModelId;
        }
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        return switch (provider) {
            case "doubao" -> doubaoDefaultModelId;
            case "chatgpt" -> chatgptDefaultModelId;
            case "deepseek" -> deepseekDefaultModelId;
            case "kimi" -> kimiDefaultModelId;
            case "glm" -> glmDefaultModelId;
            case "minimax" -> minimaxDefaultModelId;
            default -> null;
        };
    }

    private String canonicalizeConfiguredModelId(String provider, String explicitModelId) {
        if (!StringUtils.hasText(explicitModelId)) {
            return null;
        }
        String normalized = explicitModelId.trim();
        String configuredModelId = switch (provider) {
            case "doubao" -> doubaoConfiguredModelId;
            case "chatgpt" -> chatgptConfiguredModelId;
            case "deepseek" -> deepseekConfiguredModelId;
            case "kimi" -> kimiConfiguredModelId;
            case "glm" -> glmConfiguredModelId;
            case "minimax" -> minimaxConfiguredModelId;
            default -> null;
        };
        String executionModelId = switch (provider) {
            case "doubao" -> doubaoDefaultModelId;
            case "chatgpt" -> chatgptDefaultModelId;
            case "deepseek" -> deepseekDefaultModelId;
            case "kimi" -> kimiDefaultModelId;
            case "glm" -> glmDefaultModelId;
            case "minimax" -> minimaxDefaultModelId;
            default -> null;
        };
        if (!StringUtils.hasText(configuredModelId) || !StringUtils.hasText(executionModelId)) {
            return normalized;
        }
        if (looksLikeEndpointId(normalized)) {
            return normalized;
        }
        if (configuredModelId.equalsIgnoreCase(normalized) && !executionModelId.equalsIgnoreCase(normalized)) {
            return executionModelId;
        }
        return normalized;
    }

    private boolean looksLikeEndpointId(String modelId) {
        return StringUtils.hasText(modelId) && modelId.trim().toLowerCase(Locale.ROOT).startsWith("ep-");
    }

    private String normalizeProviderName(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String lower = provider.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "openai" -> "chatgpt";
            case "deepseek-ai" -> "deepseek";
            case "moonshot" -> "kimi";
            case "zhipu" -> "glm";
            default -> lower;
        };
    }

    private String normalizeOptionalModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        return modelId.trim();
    }

    private String resolveScene(FrontDeskTaskOrder taskOrder) {
        if (taskOrder == null || taskOrder.taskMetadata().isEmpty()) {
            return "node3_sampling";
        }
        Object source = taskOrder.taskMetadata().get("source");
        if (source instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return "node3_sampling";
    }

    private String sceneSuffix(String scene) {
        return StringUtils.hasText(scene) ? "，scene=" + scene.trim() : "";
    }

    private record TargetEngineFields(
            String provider,
            String modelId,
            Boolean disableReasoning
    ) {
    }
}
