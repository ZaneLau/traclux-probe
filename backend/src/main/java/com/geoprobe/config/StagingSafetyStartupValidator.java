package com.geoprobe.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

@Slf4j
@Component
@Profile("staging")
@RequiredArgsConstructor
public class StagingSafetyStartupValidator {

    @Value("${app.staging.production-safe-required:true}")
    private boolean productionSafeRequired;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.engine.execution.enabled:true}")
    private boolean engineExecutionEnabled;

    @Value("${app.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    @Value("${app.brand-space.create.initial-run-enabled:true}")
    private boolean brandSpaceInitialRunEnabled;

    @Value("${app.health.llm-enabled:true}")
    private boolean llmHealthEnabled;

    @Value("${app.volcengine.reward-quota-collector.enabled:true}")
    private boolean rewardQuotaCollectorEnabled;

    @Value("${app.volcengine.reward-quota-collector.runtime-fallback-enabled:true}")
    private boolean rewardQuotaRuntimeFallbackEnabled;

    @Value("${app.volcengine.reward-quota-enforcement.enabled:true}")
    private boolean rewardQuotaEnforcementEnabled;

    @Value("${app.knowledge-asset.enabled:true}")
    private boolean knowledgeAssetEnabled;

    @PostConstruct
    public void validate() {
        if (!productionSafeRequired) {
            throw new IllegalStateException("Staging safety validation must stay enabled.");
        }
        validateDatasource();
        requireDisabled(!schedulingEnabled, "APP_SCHEDULING_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!engineExecutionEnabled, "APP_ENGINE_EXECUTION_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!brandSpaceInitialRunEnabled, "APP_BRAND_SPACE_CREATE_INITIAL_RUN_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!llmHealthEnabled, "APP_HEALTH_LLM_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!rewardQuotaCollectorEnabled, "APP_VOLCENGINE_REWARD_QUOTA_COLLECTOR_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!rewardQuotaRuntimeFallbackEnabled, "APP_VOLCENGINE_REWARD_QUOTA_COLLECTOR_RUNTIME_FALLBACK_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!rewardQuotaEnforcementEnabled, "APP_VOLCENGINE_REWARD_QUOTA_ENFORCEMENT_ENABLED must stay false for Phase 2B-0 staging.");
        requireDisabled(!knowledgeAssetEnabled, "APP_KNOWLEDGE_ASSET_ENABLED must stay false for Phase 2B-0 staging.");
        log.info("Staging safety validation passed. datasource={}", safeDatasourceSummary(datasourceUrl));
    }

    private void validateDatasource() {
        if (!StringUtils.hasText(datasourceUrl)) {
            throw new IllegalStateException("STAGING_DB_URL is required for staging.");
        }
        String databaseName = extractDatabaseName(datasourceUrl);
        if (!StringUtils.hasText(databaseName)) {
            throw new IllegalStateException("Staging datasource must include an explicit database name.");
        }
        String normalized = databaseName.trim().toLowerCase(Locale.ROOT);
        if ("geoprobe".equals(normalized) || normalized.contains("prod") || normalized.contains("production")) {
            throw new IllegalStateException("Staging datasource points to a production-looking database: " + databaseName);
        }
        if (!normalized.contains("staging") && !normalized.contains("stage") && !normalized.contains("test")) {
            throw new IllegalStateException("Staging datasource database name must contain staging, stage, or test: " + databaseName);
        }
    }

    private String extractDatabaseName(String jdbcUrl) {
        try {
            String uriText = jdbcUrl.trim();
            if (uriText.startsWith("jdbc:")) {
                uriText = uriText.substring(5);
            }
            URI uri = URI.create(uriText);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return "";
            }
            String cleaned = path.startsWith("/") ? path.substring(1) : path;
            int slashIndex = cleaned.indexOf('/');
            if (slashIndex >= 0) {
                cleaned = cleaned.substring(0, slashIndex);
            }
            return cleaned;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Staging datasource URL is not parseable.", ex);
        }
    }

    private String safeDatasourceSummary(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return "missing";
        }
        try {
            String uriText = jdbcUrl.trim();
            if (uriText.startsWith("jdbc:")) {
                uriText = uriText.substring(5);
            }
            URI uri = URI.create(uriText);
            String host = StringUtils.hasText(uri.getHost()) ? uri.getHost() : "unknown";
            int port = uri.getPort();
            return host + (port > 0 ? ":" + port : "") + "/" + extractDatabaseName(jdbcUrl);
        } catch (RuntimeException ex) {
            return "unparseable";
        }
    }

    private void requireDisabled(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
