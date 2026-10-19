package com.geoprobe.controller;

import com.geoprobe.common.Result;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private static final String LLM_HEALTH_PROMPT = "你好，请回复 OK";

    @Qualifier("samplingModel")
    private final ChatLanguageModel samplingModel;

    @Qualifier("creativeModel")
    private final ChatLanguageModel creativeModel;

    @Qualifier("extractionModel")
    private final ChatLanguageModel extractionModel;

    private final Environment environment;

    @Value("${llm.volcengine.model}")
    private String volcengineModel;

    @Value("${llm.volcengine.endpoint-id:}")
    private String volcengineEndpointId;

    @Value("${llm.deepseek.model}")
    private String deepseekModel;

    @Value("${llm.deepseek.endpoint-id:}")
    private String deepseekEndpointId;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.staging.environment-label:}")
    private String stagingEnvironmentLabel;

    @Value("${app.staging.proxy-ready:false}")
    private boolean stagingProxyReady;

    @Value("${app.engine.execution.enabled:true}")
    private boolean engineExecutionEnabled;

    @Value("${app.runtime.mode:default}")
    private String runtimeMode;

    @Value("${app.scheduling.enabled:false}")
    private boolean schedulingEnabled;

    @Value("${app.brand-space.create.initial-run-enabled:true}")
    private boolean brandSpaceInitialRunEnabled;

    @Value("${app.health.llm-enabled:true}")
    private boolean llmHealthEnabled;

    @Value("${app.volcengine.reward-quota-collector.enabled:true}")
    private boolean rewardQuotaCollectorEnabled;

    @Value("${app.volcengine.reward-quota-enforcement.enabled:true}")
    private boolean rewardQuotaEnforcementEnabled;

    @Value("${app.knowledge-asset.enabled:true}")
    private boolean knowledgeAssetEnabled;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("version", "0.1.0");
        data.put("profiles", Arrays.asList(environment.getActiveProfiles()));
        data.put("environment", resolveEnvironmentLabel());
        data.put("runtimeMode", runtimeMode);
        data.put("datasource", safeDatasourceSummary());
        data.put("productionSafe", isProductionSafe());
        data.put("stagingProxyReady", stagingProxyReady);
        data.put("safety", Map.of(
                "schedulingEnabled", schedulingEnabled,
                "engineExecutionEnabled", engineExecutionEnabled,
                "brandSpaceInitialRunEnabled", brandSpaceInitialRunEnabled,
                "llmHealthEnabled", llmHealthEnabled,
                "rewardQuotaCollectorEnabled", rewardQuotaCollectorEnabled,
                "rewardQuotaEnforcementEnabled", rewardQuotaEnforcementEnabled,
                "knowledgeAssetEnabled", knowledgeAssetEnabled
        ));
        return Result.ok(data);
    }

    @GetMapping("/llm")
    public Result<Map<String, Object>> llmHealth() {
        if (!llmHealthEnabled) {
            return Result.ok(Map.of(
                    "status", "DISABLED",
                    "reason", "LLM health probes are disabled for this environment"
            ));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("samplingModel", probeModel(samplingModel, resolveSamplingModelName()));
        data.put("creativeModel", probeModel(creativeModel, resolveCreativeModelName()));
        data.put("extractionModel", probeModel(extractionModel, resolveExtractionModelName()));
        return Result.ok(data);
    }

    private String resolveSamplingModelName() {
        return StringUtils.hasText(volcengineEndpointId) ? volcengineEndpointId : volcengineModel;
    }

    private String resolveCreativeModelName() {
        return StringUtils.hasText(deepseekEndpointId) ? deepseekEndpointId : deepseekModel;
    }

    private String resolveExtractionModelName() {
        return StringUtils.hasText(deepseekEndpointId) ? deepseekEndpointId : deepseekModel;
    }

    private Map<String, Object> probeModel(ChatLanguageModel model, String modelName) {
        long start = System.currentTimeMillis();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("model", modelName);

        try {
            model.generate(LLM_HEALTH_PROMPT);
            status.put("status", "OK");
            status.put("latencyMs", System.currentTimeMillis() - start);
            return status;
        } catch (Exception ex) {
            status.put("status", "FAIL");
            status.put("latencyMs", System.currentTimeMillis() - start);
            status.put("error", extractErrorMessage(ex));
            return status;
        }
    }

    private String extractErrorMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return StringUtils.hasText(message) ? message : root.getClass().getSimpleName();
    }

    private String resolveEnvironmentLabel() {
        if (StringUtils.hasText(stagingEnvironmentLabel)) {
            return stagingEnvironmentLabel.trim();
        }
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }

    private boolean isProductionSafe() {
        boolean staging = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "staging".equalsIgnoreCase(profile));
        if (!staging) {
            return false;
        }
        return !engineExecutionEnabled
                && !schedulingEnabled
                && !brandSpaceInitialRunEnabled
                && !llmHealthEnabled
                && !rewardQuotaCollectorEnabled
                && !rewardQuotaEnforcementEnabled
                && !knowledgeAssetEnabled;
    }

    private Map<String, Object> safeDatasourceSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!StringUtils.hasText(datasourceUrl)) {
            summary.put("configured", false);
            return summary;
        }
        try {
            String uriText = datasourceUrl.trim();
            if (uriText.startsWith("jdbc:")) {
                uriText = uriText.substring(5);
            }
            URI uri = URI.create(uriText);
            summary.put("configured", true);
            summary.put("host", StringUtils.hasText(uri.getHost()) ? uri.getHost() : "unknown");
            if (uri.getPort() > 0) {
                summary.put("port", uri.getPort());
            }
            summary.put("database", extractDatabaseName(uri.getPath()));
        } catch (RuntimeException ex) {
            summary.put("configured", true);
            summary.put("parseable", false);
        }
        return summary;
    }

    private String extractDatabaseName(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        int slashIndex = cleaned.indexOf('/');
        return slashIndex >= 0 ? cleaned.substring(0, slashIndex) : cleaned;
    }
}
