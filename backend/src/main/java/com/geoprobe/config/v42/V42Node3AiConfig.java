package com.geoprobe.config.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.service.engine.v42.DoubaoAdapter;
import com.geoprobe.service.engine.v42.ModelAdapter;
import com.geoprobe.service.engine.v42.OpenAiCompatibleAdapter;
import com.geoprobe.service.engine.v42.ProviderRoutedModelAdapter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Configuration
public class V42Node3AiConfig {

    private static final List<String> DEFAULT_REALTIME_MODEL_FALLBACKS = List.of(
            "doubao-seed-1-6-251015",
            "doubao-seed-1-6-flash-250828",
            "doubao-seed-1-8-251228",
            "doubao-seed-2-0-lite-260215",
            "doubao-seed-2-0-mini-260215",
            "doubao-seed-2-0-pro-260215"
    );

    @Value("${llm.volcengine.api-key:}")
    private String volcengineApiKey;

    @Value("${llm.volcengine.base-url:}")
    private String volcengineBaseUrl;

    @Value("${llm.volcengine.model:}")
    private String volcengineModel;

    @Value("${llm.volcengine.endpoint-id:}")
    private String volcengineEndpointId;

    @Value("${llm.volcengine.realtime-endpoint-id:}")
    private String volcengineRealtimeEndpointId;

    @Value("${llm.volcengine.realtime-fallback-endpoint-ids:}")
    private String volcengineRealtimeFallbackEndpointIds;

    @Value("${llm.openai.api-key:dummy-api-key}")
    private String openaiApiKey;

    @Value("${llm.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${llm.openai.model:gpt-5-mini}")
    private String openaiModel;

    @Value("${llm.deepseek.api-key:${V42_ARK_API_KEY:${VOLCENGINE_API_KEY:dummy-api-key}}}")
    private String deepseekApiKey;

    @Value("${llm.deepseek.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String deepseekBaseUrl;

    @Value("${llm.deepseek.model:deepseek-v3.2}")
    private String deepseekModel;

    @Value("${llm.deepseek.endpoint-id:}")
    private String deepseekEndpointId;

    @Value("${llm.kimi.api-key:${V42_ARK_API_KEY:${VOLCENGINE_API_KEY:dummy-api-key}}}")
    private String kimiApiKey;

    @Value("${llm.kimi.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String kimiBaseUrl;

    @Value("${llm.kimi.model:kimi-k2-thinking-251104}")
    private String kimiModel;

    @Value("${llm.glm.api-key:${V42_ARK_API_KEY:${VOLCENGINE_API_KEY:dummy-api-key}}}")
    private String glmApiKey;

    @Value("${llm.glm.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String glmBaseUrl;

    @Value("${llm.glm.model:glm-4-7-251222}")
    private String glmModel;

    @Value("${llm.minimax.api-key:${MINIMAX_API_KEY:dummy-api-key}}")
    private String minimaxApiKey;

    @Value("${llm.minimax.base-url:${MINIMAX_BASE_URL:https://api.minimax.chat/v1}}")
    private String minimaxBaseUrl;

    @Value("${llm.minimax.model:${MINIMAX_MODEL:MiniMax-M1}}")
    private String minimaxModel;

    @Value("${app.engine.sampling.realtime-search.request-timeout-seconds:120}")
    private int realtimeRequestTimeoutSeconds;

    @Value("${app.engine.sampling.realtime-search.rate-limit-qps:5}")
    private int realtimeRateLimitQps;

    @Value("${app.engine.sampling.realtime-search.rate-limit-rpm:300}")
    private int realtimeRateLimitRpm;

    @Value("${app.engine.sampling.model-timeout-seconds:150}")
    private long modelTimeoutSeconds;

    @Value("${app.engine.sampling.model-max-completion-tokens:4096}")
    private int modelMaxCompletionTokens;

    @Value("${app.engine.sampling.disable-reasoning:true}")
    private boolean disableReasoning;

    @Value("${app.engine.sampling.realtime-search.model-routing.enabled:true}")
    private boolean realtimeModelRoutingEnabled;

    @Value("${app.llm-budget.default-daily-token-budget-per-model:${app.engine.sampling.realtime-search.model-routing.daily-token-budget-per-model:1900000}}")
    private long realtimeModelDailyTokenBudget;

    @Value("${app.engine.sampling.realtime-search.model-routing.hard-stop-when-all-exhausted:false}")
    private boolean realtimeModelHardStopWhenAllExhausted;

    @Value("${app.security.secret-assets.expose-routing-debug:false}")
    private boolean exposeRoutingDebug;

    @Bean("v42DoubaoCoreAdapter")
    public ModelAdapter v42DoubaoCoreAdapter(@Qualifier("samplingModel") ChatLanguageModel model,
                                             ObjectProvider<ObjectMapper> objectMapperProvider,
                                             ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                             LlmTokenBudgetService llmTokenBudgetService) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String modelName = StringUtils.hasText(volcengineRealtimeEndpointId)
                ? volcengineRealtimeEndpointId.trim()
                : StringUtils.hasText(volcengineEndpointId)
                ? volcengineEndpointId.trim()
                : normalizeOptional(volcengineModel);
        List<String> fallbackCandidates = resolveRealtimeFallbackCandidates();
        return new DoubaoAdapter(
                model,
                objectMapper,
                normalizeOptional(volcengineApiKey),
                normalizeOptional(volcengineBaseUrl),
                modelName,
                fallbackCandidates,
                realtimeModelRoutingEnabled,
                realtimeModelDailyTokenBudget,
                realtimeModelHardStopWhenAllExhausted,
                realtimeRequestTimeoutSeconds,
                realtimeRateLimitQps,
                realtimeRateLimitRpm,
                exposeRoutingDebug,
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42OpenAiAdapter")
    public ModelAdapter v42OpenAiAdapter(ObjectProvider<ObjectMapper> objectMapperProvider,
                                         ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                         LlmTokenBudgetService llmTokenBudgetService) {
        return new OpenAiCompatibleAdapter(
                "chatgpt",
                normalizeOptional(openaiApiKey),
                normalizeOptional(openaiBaseUrl),
                normalizeOptional(openaiModel),
                modelTimeout(),
                null,
                normalizedMaxCompletionTokens(),
                false,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42DeepSeekAdapter")
    public ModelAdapter v42DeepSeekAdapter(ObjectProvider<ObjectMapper> objectMapperProvider,
                                           ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                           LlmTokenBudgetService llmTokenBudgetService) {
        String targetModel = StringUtils.hasText(deepseekEndpointId)
                ? deepseekEndpointId.trim()
                : normalizeOptional(deepseekModel);
        return new OpenAiCompatibleAdapter(
                "deepseek",
                normalizeOptional(deepseekApiKey),
                normalizeOptional(deepseekBaseUrl),
                targetModel,
                modelTimeout(),
                null,
                normalizedMaxCompletionTokens(),
                false,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42KimiAdapter")
    public ModelAdapter v42KimiAdapter(ObjectProvider<ObjectMapper> objectMapperProvider,
                                       ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                       LlmTokenBudgetService llmTokenBudgetService) {
        return new OpenAiCompatibleAdapter(
                "kimi",
                normalizeOptional(kimiApiKey),
                normalizeOptional(kimiBaseUrl),
                normalizeOptional(kimiModel),
                modelTimeout(),
                null,
                normalizedMaxCompletionTokens(),
                true,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42GlmAdapter")
    public ModelAdapter v42GlmAdapter(ObjectProvider<ObjectMapper> objectMapperProvider,
                                      ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                      LlmTokenBudgetService llmTokenBudgetService) {
        return new OpenAiCompatibleAdapter(
                "glm",
                normalizeOptional(glmApiKey),
                normalizeOptional(glmBaseUrl),
                normalizeOptional(glmModel),
                modelTimeout(),
                null,
                normalizedMaxCompletionTokens(),
                true,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42MiniMaxAdapter")
    public ModelAdapter v42MiniMaxAdapter(ObjectProvider<ObjectMapper> objectMapperProvider,
                                          ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider,
                                          LlmTokenBudgetService llmTokenBudgetService) {
        return new OpenAiCompatibleAdapter(
                "minimax",
                normalizeOptional(minimaxApiKey),
                normalizeOptional(minimaxBaseUrl),
                normalizeOptional(minimaxModel),
                modelTimeout(),
                null,
                normalizedMaxCompletionTokens(),
                false,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                resolveLlmCallLogSink(llmCallLogMapperProvider),
                llmTokenBudgetService
        );
    }

    @Bean("v42DoubaoAdapter")
    public ModelAdapter v42ProviderRoutedAdapter(@Qualifier("v42DoubaoCoreAdapter") ModelAdapter doubaoAdapter,
                                                 @Qualifier("v42OpenAiAdapter") ModelAdapter openAiAdapter,
                                                 @Qualifier("v42DeepSeekAdapter") ModelAdapter deepSeekAdapter,
                                                 @Qualifier("v42KimiAdapter") ModelAdapter kimiAdapter,
                                                 @Qualifier("v42GlmAdapter") ModelAdapter glmAdapter,
                                                 @Qualifier("v42MiniMaxAdapter") ModelAdapter minimaxAdapter) {
        Map<String, ModelAdapter> delegates = new LinkedHashMap<>();
        delegates.put("doubao", doubaoAdapter);
        delegates.put("chatgpt", openAiAdapter);
        delegates.put("deepseek", deepSeekAdapter);
        delegates.put("kimi", kimiAdapter);
        delegates.put("glm", glmAdapter);
        delegates.put("minimax", minimaxAdapter);
        return new ProviderRoutedModelAdapter(delegates);
    }

    private List<String> resolveRealtimeFallbackCandidates() {
        List<String> configured = parseCsvList(volcengineRealtimeFallbackEndpointIds);
        if (!configured.isEmpty()) {
            return configured;
        }
        return DEFAULT_REALTIME_MODEL_FALLBACKS;
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private Duration modelTimeout() {
        return Duration.ofSeconds(Math.max(30L, modelTimeoutSeconds));
    }

    private Integer normalizedMaxCompletionTokens() {
        return Math.max(64, modelMaxCompletionTokens);
    }

    private List<String> parseCsvList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
        return List.copyOf(values);
    }

    private Consumer<LlmCallLog> resolveLlmCallLogSink(ObjectProvider<LlmCallLogMapper> llmCallLogMapperProvider) {
        LlmCallLogMapper mapper = llmCallLogMapperProvider.getIfAvailable();
        return mapper == null ? null : mapper::insert;
    }
}
