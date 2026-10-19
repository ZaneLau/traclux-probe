package com.geoprobe.config;

import com.geoprobe.common.BudgetGuardedChatLanguageModel;
import com.geoprobe.common.LlmCallLoggingListener;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.mapper.LlmCallLogMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Configuration
public class LlmConfig {

    private static final Double DEFAULT_SAMPLING_TEMPERATURE = 0.85d;
    private static final int DEFAULT_SAMPLING_MAX_COMPLETION_TOKENS = 4096;

    @Value("${llm.volcengine.api-key:dummy-api-key}")
    private String volcengineApiKey;

    @Value("${llm.volcengine.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String volcengineBaseUrl;

    @Value("${llm.volcengine.model:doubao-seed-1.8}")
    private String volcengineModel;

    @Value("${llm.volcengine.endpoint-id:}")
    private String volcengineEndpointId;

    @Value("${llm.deepseek.api-key:dummy-api-key}")
    private String deepseekApiKey;

    @Value("${llm.deepseek.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String deepseekBaseUrl;

    @Value("${llm.deepseek.model:deepseek-v3.2}")
    private String deepseekModel;

    @Value("${llm.deepseek.endpoint-id:}")
    private String deepseekEndpointId;

    @Value("${llm.zhipu.api-key:dummy-api-key}")
    private String zhipuApiKey;

    @Value("${llm.zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String zhipuBaseUrl;

    @Value("${llm.zhipu.model:glm-4-flashx}")
    private String zhipuModel;

    @Value("${app.engine.creative.model-timeout-seconds:90}")
    private long creativeModelTimeoutSeconds = 90L;

    @Value("${app.engine.node2.probe-rendering.model-temperature:0.2}")
    private double node2ProbeRenderingTemperature = 0.2d;

    @Value("${app.engine.extraction.model-timeout-seconds:90}")
    private long extractionModelTimeoutSeconds = 90L;

    @Value("${app.engine.sampling.model-timeout-seconds:150}")
    private long samplingModelTimeoutSeconds = 150L;

    @Value("${app.engine.sampling.model-max-completion-tokens:4096}")
    private int samplingModelMaxCompletionTokens = DEFAULT_SAMPLING_MAX_COMPLETION_TOKENS;

    @Bean
    public LlmCallLoggingListener llmCallLoggingListener(LlmCallLogMapper llmCallLogMapper) {
        return new LlmCallLoggingListener(llmCallLogMapper);
    }

    @Bean(name = "samplingModel")
    public ChatLanguageModel samplingModel(LlmCallLoggingListener llmCallLoggingListener,
                                           LlmCallLogMapper llmCallLogMapper,
                                           LlmTokenBudgetService llmTokenBudgetService) {
        String modelName = StringUtils.hasText(volcengineEndpointId) ? volcengineEndpointId : volcengineModel;
        return wrapWithBudgetGuard(
                buildModel(
                        volcengineApiKey,
                        volcengineBaseUrl,
                        modelName,
                        samplingModelTimeout(),
                        DEFAULT_SAMPLING_TEMPERATURE,
                        samplingModelMaxCompletionTokens(),
                        llmCallLoggingListener
                ),
                "doubao",
                modelName,
                llmTokenBudgetService,
                llmCallLogMapper
        );
    }

    @Bean(name = "creativeModel")
    public ChatLanguageModel creativeModel(LlmCallLoggingListener llmCallLoggingListener,
                                           LlmCallLogMapper llmCallLogMapper,
                                           LlmTokenBudgetService llmTokenBudgetService) {
        String modelName = StringUtils.hasText(deepseekEndpointId) ? deepseekEndpointId : deepseekModel;
        return wrapWithBudgetGuard(
                buildModel(
                        deepseekApiKey,
                        deepseekBaseUrl,
                        modelName,
                        creativeModelTimeout(),
                        null,
                        null,
                        llmCallLoggingListener
                ),
                "deepseek",
                modelName,
                llmTokenBudgetService,
                llmCallLogMapper
        );
    }

    @Bean(name = "probeRenderingModel")
    public ChatLanguageModel probeRenderingModel(LlmCallLoggingListener llmCallLoggingListener,
                                                 LlmCallLogMapper llmCallLogMapper,
                                                 LlmTokenBudgetService llmTokenBudgetService) {
        String modelName = StringUtils.hasText(deepseekEndpointId) ? deepseekEndpointId : deepseekModel;
        return wrapWithBudgetGuard(
                buildModel(
                        deepseekApiKey,
                        deepseekBaseUrl,
                        modelName,
                        creativeModelTimeout(),
                        node2ProbeRenderingTemperature(),
                        null,
                        llmCallLoggingListener
                ),
                "deepseek",
                modelName,
                llmTokenBudgetService,
                llmCallLogMapper
        );
    }

    @Bean(name = "extractionModel")
    public ChatLanguageModel extractionModel(LlmCallLoggingListener llmCallLoggingListener,
                                             LlmCallLogMapper llmCallLogMapper,
                                             LlmTokenBudgetService llmTokenBudgetService) {
        String modelName = StringUtils.hasText(deepseekEndpointId) ? deepseekEndpointId : deepseekModel;
        return wrapWithBudgetGuard(
                buildModel(
                        deepseekApiKey,
                        deepseekBaseUrl,
                        modelName,
                        extractionModelTimeout(),
                        null,
                        null,
                        llmCallLoggingListener
                ),
                "deepseek",
                modelName,
                llmTokenBudgetService,
                llmCallLogMapper
        );
    }

    private ChatLanguageModel buildModel(String apiKey,
                                         String baseUrl,
                                         String modelName,
                                         Duration timeout,
                                         Double temperature,
                                         Integer maxCompletionTokens,
                                         LlmCallLoggingListener llmCallLoggingListener) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(0)
                .listeners(List.of(llmCallLoggingListener));

        if (temperature != null) {
            // TODO: per-request temperature when LangChain4j supports ChatRequestParameters in our stack.
            builder.temperature(temperature);
        }

        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            builder.maxCompletionTokens(maxCompletionTokens);
        }

        return builder.build();
    }

    private ChatLanguageModel wrapWithBudgetGuard(ChatLanguageModel model,
                                                  String budgetProvider,
                                                  String modelName,
                                                  LlmTokenBudgetService llmTokenBudgetService,
                                                  LlmCallLogMapper llmCallLogMapper) {
        return new BudgetGuardedChatLanguageModel(
                model,
                budgetProvider,
                modelName,
                llmTokenBudgetService,
                llmCallLogMapper::insert
        );
    }

    private Duration creativeModelTimeout() {
        return Duration.ofSeconds(Math.max(30L, creativeModelTimeoutSeconds));
    }

    private double node2ProbeRenderingTemperature() {
        double value = node2ProbeRenderingTemperature;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.2d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private Duration extractionModelTimeout() {
        return Duration.ofSeconds(Math.max(30L, extractionModelTimeoutSeconds));
    }

    private Duration samplingModelTimeout() {
        return Duration.ofSeconds(Math.max(30L, samplingModelTimeoutSeconds));
    }

    private int samplingModelMaxCompletionTokens() {
        return Math.max(64, samplingModelMaxCompletionTokens);
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
