package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BrandSpaceTaskOrderSupport {

    private static final String MAIN_CHAIN_RUN_GOAL = "geo_brand_visibility_diagnosis";
    private static final String MAIN_CHAIN_RUN_GOAL_KEY = "runGoal";
    private static final String HISTORICAL_RUN_GOAL_ALIAS_KEY = "diagnosisGoal";
    private static final String BRAND_SPACE_ADAPTER_VERSION = "brand_space_p0";
    private static final String BRAND_SPACE_CONTRACT_VERSION = "brand_space_runtime_v1";
    private static final String BRAND_SPACE_PROVIDER = "doubao";
    private static final String BILLING_MODE_USER_CREDITS = "user_credits";
    private static final java.util.Set<String> LEGACY_QUOTA_KEYS = java.util.Set.of(
            "accountRemainingPromptBudget",
            "accountDailyPromptPool",
            "dailyPromptPool",
            "remainingPromptBudget",
            "remainingQuota"
    );

    private BrandSpaceTaskOrderSupport() {
    }

    static Map<String, Object> buildPreviewTaskOrder(BrandSpaceDraftPayload draft,
                                                     List<BrandSpaceTopicPayload> topics,
                                                     List<BrandSpacePromptPayload> prompts,
                                                     ContractContext contractContext,
                                                     BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        int targetProbeCount = resolveTargetProbeCount(executionPlan);
        String modelProduct = BRAND_SPACE_PROVIDER;
        List<String> selectedTopicIds = resolveSelectedTopicIds(executionPlan);
        List<BrandSpaceTopicPayload> executionTopics = filterDraftTopics(topics, selectedTopicIds);
        List<BrandSpacePromptPayload> executionPrompts = filterDraftPrompts(prompts, selectedTopicIds);
        BrandSpaceSchedulingService.ExecutionPlan effectiveExecutionPlan =
                refreshExecutionPrompts(executionPlan, executionTopics, executionPrompts);
        LinkedHashMap<String, Object> taskOrder = new LinkedHashMap<>();
        taskOrder.put("brandName", draft.getBrandName());
        taskOrder.put("category", draft.getCategory());
        taskOrder.put("competitors", safeList(draft.getCompetitors()));
        putRuntimeGoal(taskOrder);
        taskOrder.put("locale", defaultIfBlank(draft.getLocale(), "zh-CN"));
        taskOrder.put("language", defaultIfBlank(draft.getLanguage(), "zh-CN"));
        taskOrder.put("targetModelProduct", modelProduct);
        putIfHasText(taskOrder, "brandWebsite", draft.getOfficialWebsite());
        taskOrder.put(
                "businessHints",
                buildPreviewBusinessHints(
                        draft,
                        modelProduct,
                        targetProbeCount,
                        contractContext,
                        effectiveExecutionPlan,
                        LightConeV43TopicStateProjectionSupport.buildDraftProjectionEnvelope(
                                executionTopics,
                                executionPrompts,
                                contractContext == null ? null : contractContext.lifecycleStage()
                        )
                )
        );
        taskOrder.put("taskMetadata", buildTaskMetadata(contractContext));
        return taskOrder;
    }

    static Map<String, Object> mergeCreateTaskOrder(Map<String, Object> previewTaskOrder,
                                                    BrandSpaceDraftPayload draft,
                                                    List<BrandSpaceTopicPayload> topics,
                                                    List<BrandSpacePromptPayload> prompts,
                                                    ContractContext contractContext,
                                                    BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                    String targetModelProduct,
                                                    ObjectMapper objectMapper) {
        int targetProbeCount = resolveTargetProbeCount(executionPlan);
        String modelProduct = defaultIfBlank(targetModelProduct, BRAND_SPACE_PROVIDER);
        List<String> selectedTopicIds = resolveSelectedTopicIds(executionPlan);
        List<BrandSpaceTopicPayload> executionTopics = filterDraftTopics(topics, selectedTopicIds);
        List<BrandSpacePromptPayload> executionPrompts = filterDraftPrompts(prompts, selectedTopicIds);
        BrandSpaceSchedulingService.ExecutionPlan effectiveExecutionPlan =
                refreshExecutionPrompts(executionPlan, executionTopics, executionPrompts);
        LinkedHashMap<String, Object> taskOrder = deepMutableCopy(previewTaskOrder, objectMapper);
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope =
                LightConeV43TopicStateProjectionSupport.buildDraftProjectionEnvelope(
                        executionTopics,
                        executionPrompts,
                        contractContext == null ? null : contractContext.lifecycleStage()
                );
        taskOrder.put("brandName", draft.getBrandName());
        taskOrder.put("category", draft.getCategory());
        taskOrder.put("competitors", safeList(draft.getCompetitors()));
        putRuntimeGoal(taskOrder);
        taskOrder.put("locale", defaultIfBlank(draft.getLocale(), "zh-CN"));
        taskOrder.put("language", defaultIfBlank(draft.getLanguage(), "zh-CN"));
        taskOrder.put("targetModelProduct", modelProduct);
        if (StringUtils.hasText(draft.getOfficialWebsite())) {
            taskOrder.put("brandWebsite", draft.getOfficialWebsite().trim());
        } else {
            taskOrder.remove("brandWebsite");
        }
        taskOrder.put(
                "businessHints",
                mergeCreateBusinessHints(
                        readMutableMap(taskOrder.get("businessHints"), objectMapper),
                        draft,
                        modelProduct,
                        projectionEnvelope,
                        contractContext,
                        targetProbeCount,
                        effectiveExecutionPlan
                )
        );
        taskOrder.put("taskMetadata", buildTaskMetadata(contractContext));
        return taskOrder;
    }

    static Map<String, Object> buildScheduledTaskOrder(BrandScope scope,
                                                       Brand brand,
                                                       List<String> competitors,
                                                       BrandSpaceConfigView configView,
                                                       String targetModelProduct,
                                                       ContractContext contractContext,
                                                       BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        int targetProbeCount = resolveTargetProbeCount(executionPlan);
        String modelProduct = defaultIfBlank(targetModelProduct, BRAND_SPACE_PROVIDER);
        List<String> selectedTopicIds = resolveSelectedTopicIds(executionPlan);
        BrandSpaceConfigView executionConfigView = filterConfigView(configView, selectedTopicIds);
        BrandSpaceSchedulingService.ExecutionPlan effectiveExecutionPlan =
                refreshExecutionPrompts(executionPlan, executionConfigView);
        LinkedHashMap<String, Object> taskOrder = new LinkedHashMap<>();
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        executionConfigView,
                        contractContext == null ? null : contractContext.lifecycleStage()
                );
        taskOrder.put("brandName", firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName()));
        taskOrder.put("category", scope.getCategorySnapshot());
        taskOrder.put("competitors", safeList(competitors));
        putRuntimeGoal(taskOrder);
        taskOrder.put("locale", defaultIfBlank(scope.getLocale(), "zh-CN"));
        taskOrder.put("language", defaultIfBlank(scope.getLanguage(), "zh-CN"));
        taskOrder.put("targetModelProduct", modelProduct);
        putIfHasText(taskOrder, "brandWebsite", brand == null ? null : brand.getOfficialWebsite());
        taskOrder.put(
                "businessHints",
                buildScheduledBusinessHints(
                        scope,
                        modelProduct,
                        projectionEnvelope,
                        contractContext,
                        targetProbeCount,
                        effectiveExecutionPlan
                )
        );
        taskOrder.put("taskMetadata", buildTaskMetadata(contractContext));
        return taskOrder;
    }

    static Map<String, Object> refreshRuntimeExecutionHints(Map<String, Object> taskOrder,
                                                            BrandSpaceConfigView configView,
                                                            ObjectMapper objectMapper) {
        LinkedHashMap<String, Object> mutableTaskOrder = deepMutableCopy(taskOrder, objectMapper);
        LinkedHashMap<String, Object> businessHints = readMutableMap(mutableTaskOrder.get("businessHints"), objectMapper);
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = readExecutionPlan(businessHints);
        int targetProbeCount = resolveTargetProbeCount(executionPlan);
        BrandSpaceConfigView executionConfigView = filterConfigView(configView, resolveSelectedTopicIds(executionPlan));
        BrandSpaceSchedulingService.ExecutionPlan effectiveExecutionPlan =
                refreshExecutionPrompts(executionPlan, executionConfigView);
        businessHints.put("topicPlan", buildTopicPlan(
                targetProbeCount,
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(executionConfigView, null),
                effectiveExecutionPlan
        ));
        if (effectiveExecutionPlan != null) {
            businessHints.put("scheduleDecision", buildScheduleDecisionMap(effectiveExecutionPlan));
        }
        mergeProjectionEnvelope(
                businessHints,
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(executionConfigView, null)
        );
        mutableTaskOrder.put("businessHints", businessHints);
        return mutableTaskOrder;
    }

    private static BrandSpaceSchedulingService.ExecutionPlan refreshExecutionPrompts(
            BrandSpaceSchedulingService.ExecutionPlan executionPlan,
            List<BrandSpaceTopicPayload> topics,
            List<BrandSpacePromptPayload> prompts) {
        if (executionPlan == null) {
            return null;
        }
        return new BrandSpaceSchedulingService.ExecutionPlan(
                executionPlan.triggerMode(),
                executionPlan.targetProbeCount(),
                executionPlan.selectedTopics(),
                executionPlan.deferredTopics(),
                executionPlan.coverageStrategy(),
                executionPlan.humanReason(),
                executionPlan.coldStart(),
                executionPlan.justEdited(),
                executionPlan.quotaLimited(),
                executionPlan.accountAvailableCredits(),
                executionPlan.accountBrandCount(),
                executionPlan.brandCoreProbeCount(),
                executionPlan.brandExpansionProbeCount(),
                executionPlan.weeklyTargetTopicCount(),
                executionPlan.weeklyCoveredTopicCount(),
                executionPlan.weeklyGapTopicCount(),
                executionPlan.weeklyCoverageRatio(),
                buildExecutionPromptPlans(topics, prompts)
        );
    }

    private static BrandSpaceSchedulingService.ExecutionPlan refreshExecutionPrompts(
            BrandSpaceSchedulingService.ExecutionPlan executionPlan,
            BrandSpaceConfigView configView) {
        if (executionPlan == null) {
            return null;
        }
        return new BrandSpaceSchedulingService.ExecutionPlan(
                executionPlan.triggerMode(),
                executionPlan.targetProbeCount(),
                executionPlan.selectedTopics(),
                executionPlan.deferredTopics(),
                executionPlan.coverageStrategy(),
                executionPlan.humanReason(),
                executionPlan.coldStart(),
                executionPlan.justEdited(),
                executionPlan.quotaLimited(),
                executionPlan.accountAvailableCredits(),
                executionPlan.accountBrandCount(),
                executionPlan.brandCoreProbeCount(),
                executionPlan.brandExpansionProbeCount(),
                executionPlan.weeklyTargetTopicCount(),
                executionPlan.weeklyCoveredTopicCount(),
                executionPlan.weeklyGapTopicCount(),
                executionPlan.weeklyCoverageRatio(),
                buildExecutionPromptPlans(configView)
        );
    }

    private static Map<String, Object> buildPreviewBusinessHints(BrandSpaceDraftPayload draft,
                                                                 String targetModelProduct,
                                                                 int targetProbeCount,
                                                                 ContractContext contractContext,
                                                                 BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                                 LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope) {
        LinkedHashMap<String, Object> businessHints = new LinkedHashMap<>();
        if (StringUtils.hasText(draft.getSupplementalBrief())) {
            businessHints.put("userPrompt", draft.getSupplementalBrief().trim());
        }
        businessHints.putAll(buildExecutionHints(
                targetProbeCount,
                targetModelProduct,
                contractContext,
                projectionEnvelope,
                executionPlan
        ));
        if (executionPlan != null) {
            businessHints.put("scheduleDecision", buildScheduleDecisionMap(executionPlan));
        }
        mergeProjectionEnvelope(businessHints, projectionEnvelope);
        businessHints.put("brandSpaceContext",
                buildBrandSpaceContext(
                        contractContext,
                        defaultIfBlank(draft.getRegionCode(), "CN"),
                        defaultIfBlank(draft.getLocale(), "zh-CN"),
                        defaultIfBlank(draft.getLanguage(), "zh-CN"),
                        targetProbeCount,
                        executionPlan
                ));
        return businessHints;
    }

    static Map<String, Object> normalizeTaskOrderForRead(Map<String, Object> taskOrder) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(sanitizeLegacyQuotaFields(taskOrder));
        String runGoal = firstText(
                firstText(asText(normalized.get(MAIN_CHAIN_RUN_GOAL_KEY)), asText(normalized.get(HISTORICAL_RUN_GOAL_ALIAS_KEY))),
                MAIN_CHAIN_RUN_GOAL
        );
        normalized.put(MAIN_CHAIN_RUN_GOAL_KEY, runGoal);
        normalized.remove(HISTORICAL_RUN_GOAL_ALIAS_KEY);
        return Map.copyOf(normalized);
    }

    static Map<String, Object> normalizeRuntimeMapForRead(Map<String, Object> runtimeMap) {
        return sanitizeLegacyQuotaFields(runtimeMap);
    }

    private static void putRuntimeGoal(Map<String, Object> taskOrder) {
        if (taskOrder == null) {
            return;
        }
        taskOrder.put(MAIN_CHAIN_RUN_GOAL_KEY, MAIN_CHAIN_RUN_GOAL);
    }

    private static Map<String, Object> mergeCreateBusinessHints(Map<String, Object> existingHints,
                                                                BrandSpaceDraftPayload draft,
                                                                String targetModelProduct,
                                                                LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                                ContractContext contractContext,
                                                                int targetProbeCount,
                                                                BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> businessHints = new LinkedHashMap<>();
        if (existingHints != null && !existingHints.isEmpty()) {
            businessHints.putAll(existingHints);
        }
        businessHints.putAll(buildExecutionHints(
                targetProbeCount,
                targetModelProduct,
                contractContext,
                projectionEnvelope,
                executionPlan
        ));
        String supplementalBrief = buildExecutionSupplementalBrief(
                draft.getSupplementalBrief(),
                projectionEnvelope,
                executionPlan
        );
        if (StringUtils.hasText(supplementalBrief)) {
            businessHints.put("userPrompt", supplementalBrief);
        } else {
            businessHints.remove("userPrompt");
        }
        if (executionPlan != null) {
            businessHints.put("scheduleDecision", buildScheduleDecisionMap(executionPlan));
        }
        mergeProjectionEnvelope(businessHints, projectionEnvelope);
        businessHints.put("brandSpaceContext",
                buildBrandSpaceContext(
                        contractContext,
                        defaultIfBlank(draft.getRegionCode(), "CN"),
                        defaultIfBlank(draft.getLocale(), "zh-CN"),
                        defaultIfBlank(draft.getLanguage(), "zh-CN"),
                        targetProbeCount,
                        executionPlan
                ));
        return businessHints;
    }

    private static Map<String, Object> buildScheduledBusinessHints(BrandScope scope,
                                                                   String targetModelProduct,
                                                                   LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                                   ContractContext contractContext,
                                                                   int targetProbeCount,
                                                                   BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> businessHints = new LinkedHashMap<>();
        String supplementalBrief = buildScheduledSupplementalBrief(projectionEnvelope, executionPlan);
        if (StringUtils.hasText(supplementalBrief)) {
            businessHints.put("userPrompt", supplementalBrief);
        }
        businessHints.putAll(buildExecutionHints(
                targetProbeCount,
                targetModelProduct,
                contractContext,
                projectionEnvelope,
                executionPlan
        ));
        if (executionPlan != null) {
            businessHints.put("scheduleDecision", buildScheduleDecisionMap(executionPlan));
        }
        mergeProjectionEnvelope(businessHints, projectionEnvelope);
        businessHints.put("brandSpaceContext",
                buildBrandSpaceContext(
                        contractContext,
                        defaultIfBlank(scope.getRegionCode(), "CN"),
                        defaultIfBlank(scope.getLocale(), "zh-CN"),
                        defaultIfBlank(scope.getLanguage(), "zh-CN"),
                        targetProbeCount,
                        executionPlan
                ));
        return businessHints;
    }

    private static Map<String, Object> buildBrandSpaceContext(ContractContext contractContext,
                                                              String regionCode,
                                                              String locale,
                                                              String language,
                                                              int targetProbeCount,
                                                              BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("origin", "brand_space");
        context.put("contractVersion", BRAND_SPACE_CONTRACT_VERSION);
        putIfHasText(context, "lifecycleStage", contractContext == null ? null : contractContext.lifecycleStage());
        putIfHasText(context, "trigger", contractContext == null ? null : contractContext.trigger());
        putIfHasText(context, "organizationId", contractContext == null ? null : contractContext.organizationId());
        putIfHasText(context, "membershipId", contractContext == null ? null : contractContext.membershipId());
        putIfHasText(context, "brandId", contractContext == null ? null : contractContext.brandId());
        putIfHasText(context, "brandSpaceId", contractContext == null ? null : contractContext.brandSpaceId());
        putIfNonNull(context, "configGeneration", contractContext == null ? null : contractContext.configGeneration());
        putIfHasText(context, "trackingMode", contractContext == null ? null : contractContext.trackingMode());
        putIfHasText(context, "regionCode", regionCode);
        putIfHasText(context, "locale", locale);
        putIfHasText(context, "language", language);
        context.put("requestedProbeCount", targetProbeCount);
        if (executionPlan != null) {
            context.put("coverageStrategy", executionPlan.coverageStrategy());
            context.put("selectedTopicCount", executionPlan.selectedTopics().size());
            if (executionPlan.accountAvailableCredits() != null) {
                context.put("accountAvailableCredits", executionPlan.accountAvailableCredits());
            }
            if (executionPlan.weeklyTargetTopicCount() != null) {
                context.put("weeklyTargetTopicCount", executionPlan.weeklyTargetTopicCount());
            }
            if (executionPlan.weeklyCoveredTopicCount() != null) {
                context.put("weeklyCoveredTopicCount", executionPlan.weeklyCoveredTopicCount());
            }
            if (executionPlan.weeklyGapTopicCount() != null) {
                context.put("weeklyGapTopicCount", executionPlan.weeklyGapTopicCount());
            }
        }
        Map<String, Object> serviceAccessSnapshot = buildServiceAccessSnapshot(contractContext == null ? null : contractContext.serviceAccess());
        if (!serviceAccessSnapshot.isEmpty()) {
            context.put("serviceAccess", serviceAccessSnapshot);
        }
        return Map.copyOf(context);
    }

    private static Map<String, Object> buildTaskMetadata(ContractContext contractContext) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", "brand_space");
        metadata.put("brandSpaceContractVersion", BRAND_SPACE_CONTRACT_VERSION);
        putIfHasText(metadata, "channel", contractContext == null ? null : contractContext.channel());
        putIfHasText(metadata, "source", contractContext == null ? null : contractContext.source());
        putIfHasText(metadata, "trigger", contractContext == null ? null : contractContext.trigger());
        putIfHasText(metadata, "lifecycleStage", contractContext == null ? null : contractContext.lifecycleStage());
        putIfHasText(metadata, "requestId", contractContext == null ? null : contractContext.requestId());
        putIfHasText(metadata, "organizationId", contractContext == null ? null : contractContext.organizationId());
        putIfHasText(metadata, "membershipId", contractContext == null ? null : contractContext.membershipId());
        putIfHasText(metadata, "brandId", contractContext == null ? null : contractContext.brandId());
        putIfHasText(metadata, "brandSpaceId", contractContext == null ? null : contractContext.brandSpaceId());
        putIfNonNull(metadata, "configGeneration", contractContext == null ? null : contractContext.configGeneration());
        putIfHasText(metadata, "trackingMode", contractContext == null ? null : contractContext.trackingMode());
        metadata.put("billingMode", BILLING_MODE_USER_CREDITS);
        if (contractContext != null && contractContext.scheduleDate() != null) {
            metadata.put("scheduleDate", contractContext.scheduleDate().toString());
        }
        if (contractContext != null && contractContext.serviceAccess() != null) {
            putIfHasText(metadata, "serviceAccessStatus", contractContext.serviceAccess().getStatus());
            putIfHasText(metadata, "planName", contractContext.serviceAccess().getPlanName());
        }
        metadata.put(
                LightConeV43VersionTruthSupport.RUN_VERSION_TRUTH_KEY,
                LightConeV43VersionTruthSupport.canonicalRunVersionTruth()
        );
        return LightConeV43VersionTruthSupport.ensureTaskMetadataContract(
                metadata,
                LightConeV43VersionTruthSupport.ENGINE_RUNTIME_VERSION_V42
        );
    }

    private static Map<String, Object> buildServiceAccessSnapshot(ServiceAccessView serviceAccess) {
        if (serviceAccess == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        putIfHasText(snapshot, "status", serviceAccess.getStatus());
        putIfHasText(snapshot, "planName", serviceAccess.getPlanName());
        if (serviceAccess.getLimits() != null) {
            putIfNonNull(snapshot, "brandSpaceLimit", serviceAccess.getLimits().getBrandSpaceLimit());
            putIfNonNull(snapshot, "monthlyRunLimit", serviceAccess.getLimits().getMonthlyRunLimit());
            putIfNonNull(snapshot, "promptLimitPerBrandSpace", serviceAccess.getLimits().getPromptLimitPerBrandSpace());
            putIfNonNull(snapshot, "enabledModelCount", serviceAccess.getLimits().getEnabledModelCount());
            putIfNonNull(snapshot, "creditsPerProbe", serviceAccess.getLimits().getCreditsPerProbe());
        }
        return snapshot.isEmpty() ? Map.of() : Map.copyOf(snapshot);
    }

    private static List<BrandSpaceSchedulingService.ExecutionPromptPlan> buildExecutionPromptPlans(
            List<BrandSpaceTopicPayload> topics,
            List<BrandSpacePromptPayload> prompts) {
        List<BrandSpaceTopicPayload> safeTopics = topics == null ? List.of() : topics;
        List<BrandSpacePromptPayload> safePrompts = prompts == null ? List.of() : prompts;
        Map<String, String> topicNameByTempId = new LinkedHashMap<>();
        for (BrandSpaceTopicPayload topic : safeTopics) {
            if (topic == null || !Boolean.TRUE.equals(topic.getEnabled())) {
                continue;
            }
            String topicTempId = normalizeText(topic.getTempId());
            String topicName = normalizeText(topic.getName());
            if (StringUtils.hasText(topicTempId) && StringUtils.hasText(topicName)) {
                topicNameByTempId.put(topicTempId, topicName);
            }
        }
        List<BrandSpaceSchedulingService.ExecutionPromptPlan> result = new ArrayList<>();
        int seqNo = 0;
        for (BrandSpacePromptPayload prompt : safePrompts) {
            if (prompt == null || !Boolean.TRUE.equals(prompt.getEnabled()) || !StringUtils.hasText(prompt.getText())) {
                continue;
            }
            seqNo++;
            String topicTempId = normalizeText(prompt.getTopicTempId());
            result.add(new BrandSpaceSchedulingService.ExecutionPromptPlan(
                    seqNo,
                    normalizeText(prompt.getTempId()),
                    topicTempId,
                    topicNameByTempId.get(topicTempId),
                    prompt.getText().trim()
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static List<BrandSpaceSchedulingService.ExecutionPromptPlan> buildExecutionPromptPlans(
            BrandSpaceConfigView configView) {
        if (configView == null || configView.getPrompts() == null || configView.getPrompts().isEmpty()) {
            return List.of();
        }
        List<BrandSpaceSchedulingService.ExecutionPromptPlan> result = new ArrayList<>();
        int seqNo = 0;
        for (BrandSpaceConfigView.PromptView prompt : configView.getPrompts()) {
            if (prompt == null || !Boolean.TRUE.equals(prompt.getEnabled()) || !StringUtils.hasText(prompt.getText())) {
                continue;
            }
            seqNo++;
            result.add(new BrandSpaceSchedulingService.ExecutionPromptPlan(
                    seqNo,
                    normalizeText(prompt.getPromptId()),
                    normalizeText(prompt.getTopicId()),
                    normalizeText(prompt.getTopicName()),
                    prompt.getText().trim()
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static Map<String, Object> buildExecutionHints(int targetProbeCount,
                                                           String targetModelProduct,
                                                           ContractContext contractContext,
                                                           LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                           BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        String normalizedModelProduct = defaultIfBlank(targetModelProduct, BRAND_SPACE_PROVIDER);
        LinkedHashMap<String, Object> hints = new LinkedHashMap<>();
        hints.put("sampling", Map.of("targetProbeCount", targetProbeCount));
        hints.put("samplingRealtimeSearch", Map.of(
                "enabled", true,
                "provider", BRAND_SPACE_PROVIDER
        ));
        hints.put("samplingEngine", Map.of(
                "provider", resolveSamplingEngineProvider(normalizedModelProduct)
        ));
        hints.put("topicPlan", buildTopicPlan(targetProbeCount, projectionEnvelope, executionPlan));
        hints.put("schedulePolicy", buildSchedulePolicy(contractContext, targetProbeCount, executionPlan));
        return Map.copyOf(hints);
    }

    private static String resolveSamplingEngineProvider(String targetModelProduct) {
        String inferredProvider = Node3TargetEnginePolicy.inferProviderFromModelId(targetModelProduct);
        return StringUtils.hasText(inferredProvider) ? inferredProvider : BRAND_SPACE_PROVIDER;
    }

    private static Map<String, Object> buildTopicPlan(int targetProbeCount,
                                                      LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                      BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> topicPlan = new LinkedHashMap<>();
        List<String> canonicalTopicLabels = readProjectionTopicLabels(projectionEnvelope);
        boolean canonicalProjectionActive = projectionEnvelope != null
                && projectionEnvelope.active()
                && !canonicalTopicLabels.isEmpty();
        List<String> effectiveTopicNames = canonicalProjectionActive ? canonicalTopicLabels : List.of();
        topicPlan.put("requestedProbeCount", targetProbeCount);
        topicPlan.put("enabledTopicNames", effectiveTopicNames);
        topicPlan.put("enabledTopicCount", effectiveTopicNames.size());
        if (executionPlan != null) {
            topicPlan.put("selectedTopicNames", executionPlan.selectedTopicNames());
            topicPlan.put("selectedTopicCount", executionPlan.selectedTopics().size());
            topicPlan.put("deferredTopicNames", executionPlan.deferredTopicNames());
            topicPlan.put("deferredTopicCount", executionPlan.deferredTopics().size());
            topicPlan.put("executionPromptCount", executionPlan.executionPrompts().size());
            topicPlan.put("coverageStrategy", executionPlan.coverageStrategy());
            topicPlan.put("selectionReason", executionPlan.humanReason());
            topicPlan.put("topicSelectionMode", "brand_space_schedule_plan");
            topicPlan.put("coverageSource", "brand_space_schedule_truth");
            topicPlan.put("selectedTopicAllocations", executionPlan.selectedTopics().stream()
                    .map(BrandSpaceTaskOrderSupport::buildTopicDecisionMap)
                    .toList());
        } else {
            topicPlan.put("topicSelectionMode",
                    canonicalProjectionActive ? "canonical_topic_projection" : "prompt_budget_only");
            topicPlan.put("coverageSource",
                    canonicalProjectionActive ? "brand_space_topic_projection" : "brand_space_probe_budget");
        }
        return Map.copyOf(topicPlan);
    }

    private static void mergeProjectionEnvelope(Map<String, Object> businessHints,
                                                LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope) {
        if (businessHints == null) {
            return;
        }
        if (projectionEnvelope == null || !projectionEnvelope.active()) {
            businessHints.remove(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);
            businessHints.remove(LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY);
            return;
        }
        businessHints.put(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY, projectionEnvelope.projections());
        businessHints.put(LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY, projectionEnvelope.summary());
    }

    private static Map<String, Object> buildSchedulePolicy(ContractContext contractContext,
                                                           int targetProbeCount,
                                                           BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> schedulePolicy = new LinkedHashMap<>();
        schedulePolicy.put("mode", resolveScheduleMode(contractContext));
        schedulePolicy.put("targetProbeCount", targetProbeCount);
        schedulePolicy.put("realtimeEvidenceRequired", true);
        putIfHasText(schedulePolicy, "trigger", contractContext == null ? null : contractContext.trigger());
        putIfHasText(schedulePolicy, "trackingMode", contractContext == null ? null : contractContext.trackingMode());
        putIfHasText(schedulePolicy, "lifecycleStage", contractContext == null ? null : contractContext.lifecycleStage());
        if (contractContext != null && contractContext.scheduleDate() != null) {
            schedulePolicy.put("scheduleDate", contractContext.scheduleDate().toString());
        }
        if (executionPlan != null) {
            schedulePolicy.put("coverageStrategy", executionPlan.coverageStrategy());
            schedulePolicy.put("schedulingReason", executionPlan.humanReason());
            schedulePolicy.put("selectedTopicCount", executionPlan.selectedTopics().size());
            schedulePolicy.put("deferredTopicCount", executionPlan.deferredTopics().size());
            schedulePolicy.put("coldStart", executionPlan.coldStart());
            schedulePolicy.put("justEdited", executionPlan.justEdited());
            schedulePolicy.put("quotaLimited", executionPlan.quotaLimited());
            if (executionPlan.accountAvailableCredits() != null) {
                schedulePolicy.put("accountAvailableCredits", executionPlan.accountAvailableCredits());
            }
            if (executionPlan.accountBrandCount() != null) {
                schedulePolicy.put("accountBrandCount", executionPlan.accountBrandCount());
            }
            if (executionPlan.brandCoreProbeCount() != null) {
                schedulePolicy.put("brandCoreProbeCount", executionPlan.brandCoreProbeCount());
            }
            if (executionPlan.brandExpansionProbeCount() != null) {
                schedulePolicy.put("brandExpansionProbeCount", executionPlan.brandExpansionProbeCount());
            }
            if (executionPlan.weeklyTargetTopicCount() != null) {
                schedulePolicy.put("weeklyTargetTopicCount", executionPlan.weeklyTargetTopicCount());
            }
            if (executionPlan.weeklyCoveredTopicCount() != null) {
                schedulePolicy.put("weeklyCoveredTopicCount", executionPlan.weeklyCoveredTopicCount());
            }
            if (executionPlan.weeklyGapTopicCount() != null) {
                schedulePolicy.put("weeklyGapTopicCount", executionPlan.weeklyGapTopicCount());
            }
            if (executionPlan.weeklyCoverageRatio() != null) {
                schedulePolicy.put("weeklyCoverageRatio", executionPlan.weeklyCoverageRatio());
            }
        }
        return Map.copyOf(schedulePolicy);
    }

    private static String resolveScheduleMode(ContractContext contractContext) {
        if (contractContext == null) {
            return "brand_space_unknown";
        }
        if ("brand_space_schedule".equals(defaultIfBlank(contractContext.source(), ""))) {
            return "scheduled_tracking";
        }
        if ("frontdesk_create".equals(defaultIfBlank(contractContext.source(), ""))) {
            return "frontdesk_create";
        }
        if ("brand_space_preview".equals(defaultIfBlank(contractContext.channel(), ""))) {
            return "preview_planning";
        }
        return "brand_space_runtime";
    }

    private static String buildExecutionSupplementalBrief(String supplementalBrief,
                                                          LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                          BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(supplementalBrief)) {
            lines.add(supplementalBrief.trim());
        }
        String topicSummary = buildTopicSummaryLine("首轮优先跟踪的主题：", projectionEnvelope, executionPlan);
        if (StringUtils.hasText(topicSummary)) {
            lines.add(topicSummary);
        }
        if (executionPlan != null && StringUtils.hasText(executionPlan.humanReason())) {
            lines.add(executionPlan.humanReason());
        }
        return truncateBrief(lines);
    }

    private static String buildScheduledSupplementalBrief(LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                          BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        List<String> lines = new ArrayList<>();
        String topicSummary = buildTopicSummaryLine("持续跟踪当前品牌空间的主题：", projectionEnvelope, executionPlan);
        if (StringUtils.hasText(topicSummary)) {
            lines.add(topicSummary);
        }
        if (executionPlan != null && StringUtils.hasText(executionPlan.humanReason())) {
            lines.add(executionPlan.humanReason());
        }
        return truncateBrief(lines);
    }

    private static String buildTopicSummaryLine(String prefix,
                                                LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope,
                                                BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        List<String> topicLabels = readProjectionTopicLabels(projectionEnvelope);
        if (topicLabels.isEmpty() && executionPlan != null && !executionPlan.selectedTopicNames().isEmpty()) {
            topicLabels = executionPlan.selectedTopicNames();
        }
        if (!topicLabels.isEmpty()) {
            return defaultIfBlank(prefix, "优先跟踪的主题：") + String.join("、", topicLabels);
        }
        if (executionPlan != null && !executionPlan.executionPrompts().isEmpty()) {
            return defaultIfBlank(prefix, "优先跟踪的主题：") + "当前配置共 " + executionPlan.executionPrompts().size() + " 条追踪条目";
        }
        return null;
    }

    private static List<String> readProjectionTopicLabels(
            LightConeV43TopicStateProjectionSupport.ProjectionEnvelope projectionEnvelope) {
        if (projectionEnvelope == null || !projectionEnvelope.active()) {
            return List.of();
        }
        Object rawLabels = projectionEnvelope.summary().get("canonicalTopicLabels");
        if (!(rawLabels instanceof List<?> labels) || labels.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(labels.size());
        for (Object item : labels) {
            if (item == null) {
                continue;
            }
            String text = item.toString().trim();
            if (!text.isEmpty()) {
                normalized.add(text);
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private static String truncateBrief(List<String> lines) {
        String merged = String.join("\n", lines);
        if (!StringUtils.hasText(merged)) {
            return null;
        }
        if (merged.length() <= 1000) {
            return merged;
        }
        return merged.substring(0, 997) + "...";
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    private static void putIfHasText(Map<String, Object> target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key.trim(), value.trim());
    }

    private static void putIfNonNull(Map<String, Object> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        target.put(key.trim(), value);
    }

    private static String firstText(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String asText(Object value) {
        return value instanceof String text ? normalizeText(text) : null;
    }

    private static int resolveTargetProbeCount(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.targetProbeCount() <= 0) {
            throw new IllegalArgumentException("品牌空间调度计划缺少合法 targetProbeCount");
        }
        return executionPlan.targetProbeCount();
    }

    private static List<String> resolveSelectedTopicIds(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.selectedTopicIds().isEmpty()) {
            return List.of();
        }
        return executionPlan.selectedTopicIds();
    }

    private static List<BrandSpaceTopicPayload> filterDraftTopics(List<BrandSpaceTopicPayload> topics,
                                                                  List<String> selectedTopicIds) {
        if (topics == null || topics.isEmpty() || selectedTopicIds == null || selectedTopicIds.isEmpty()) {
            return topics == null ? List.of() : topics;
        }
        List<BrandSpaceTopicPayload> filtered = topics.stream()
                .filter(topic -> topic != null && selectedTopicIds.contains(normalizeText(topic.getTempId())))
                .toList();
        return filtered.isEmpty() ? topics : filtered;
    }

    private static List<BrandSpacePromptPayload> filterDraftPrompts(List<BrandSpacePromptPayload> prompts,
                                                                    List<String> selectedTopicIds) {
        if (prompts == null || prompts.isEmpty() || selectedTopicIds == null || selectedTopicIds.isEmpty()) {
            return prompts == null ? List.of() : prompts;
        }
        List<BrandSpacePromptPayload> filtered = prompts.stream()
                .filter(prompt -> prompt != null && selectedTopicIds.contains(normalizeText(prompt.getTopicTempId())))
                .toList();
        return filtered.isEmpty() ? prompts : filtered;
    }

    private static BrandSpaceConfigView filterConfigView(BrandSpaceConfigView configView,
                                                         List<String> selectedTopicIds) {
        if (configView == null || selectedTopicIds == null || selectedTopicIds.isEmpty()) {
            return configView;
        }
        List<BrandSpaceConfigView.TopicView> topics = configView.getTopics() == null
                ? List.of()
                : configView.getTopics().stream()
                .filter(topic -> topic != null && selectedTopicIds.contains(normalizeText(topic.getTopicId())))
                .toList();
        List<BrandSpaceConfigView.PromptView> prompts = configView.getPrompts() == null
                ? List.of()
                : configView.getPrompts().stream()
                .filter(prompt -> prompt != null && selectedTopicIds.contains(normalizeText(prompt.getTopicId())))
                .toList();
        if (topics.isEmpty()) {
            return configView;
        }
        BrandSpaceConfigView.SummaryView summary = BrandSpaceConfigView.SummaryView.builder()
                .topicCount(topics.size())
                .enabledTopicCount((int) topics.stream().filter(topic -> Boolean.TRUE.equals(topic.getEnabled())).count())
                .promptCount(prompts.size())
                .enabledPromptCount((int) prompts.stream().filter(prompt -> Boolean.TRUE.equals(prompt.getEnabled())).count())
                .build();
        return BrandSpaceConfigView.builder()
                .brandSpaceId(configView.getBrandSpaceId())
                .brandId(configView.getBrandId())
                .brandName(configView.getBrandName())
                .configGeneration(configView.getConfigGeneration())
                .topics(topics)
                .prompts(prompts)
                .summary(summary)
                .capabilities(configView.getCapabilities())
                .build();
    }

    private static BrandSpaceSchedulingService.ExecutionPlan readExecutionPlan(Map<String, Object> businessHints) {
        if (businessHints == null || businessHints.isEmpty()) {
            return null;
        }
        Map<String, Object> decisionMap = readObjectMap(businessHints.get("scheduleDecision"));
        if (decisionMap.isEmpty()) {
            return null;
        }
        Integer targetProbeCount = toInteger(decisionMap.get("targetProbeCount"));
        if (targetProbeCount == null || targetProbeCount <= 0) {
            return null;
        }
        return new BrandSpaceSchedulingService.ExecutionPlan(
                normalizeText((String) decisionMap.get("triggerMode")),
                targetProbeCount,
                readTopicDecisions(decisionMap.get("selectedTopics")),
                readTopicDecisions(decisionMap.get("deferredTopics")),
                firstText((String) decisionMap.get("coverageStrategy"), "unknown"),
                firstText((String) decisionMap.get("humanReason"), "未提供调度说明"),
                Boolean.TRUE.equals(decisionMap.get("coldStart")),
                Boolean.TRUE.equals(decisionMap.get("justEdited")),
                Boolean.TRUE.equals(decisionMap.get("quotaLimited")),
                firstNonNullInteger(
                        toInteger(decisionMap.get("accountAvailableCredits")),
                        toInteger(decisionMap.get("accountRemainingPromptBudget"))
                ),
                toInteger(decisionMap.get("accountBrandCount")),
                toInteger(decisionMap.get("brandCoreProbeCount")),
                toInteger(decisionMap.get("brandExpansionProbeCount")),
                toInteger(decisionMap.get("weeklyTargetTopicCount")),
                toInteger(decisionMap.get("weeklyCoveredTopicCount")),
                toInteger(decisionMap.get("weeklyGapTopicCount")),
                readNumber(decisionMap.get("weeklyCoverageRatio")) == null ? null : readNumber(decisionMap.get("weeklyCoverageRatio")).doubleValue(),
                readExecutionPromptPlans(decisionMap.get("executionPrompts"))
        );
    }

    static BrandSpaceSchedulingService.ExecutionPlan readExecutionPlanFromTaskOrder(Map<String, Object> taskOrder) {
        if (taskOrder == null || taskOrder.isEmpty()) {
            return null;
        }
        return readExecutionPlan(readObjectMap(taskOrder.get("businessHints")));
    }

    private static List<BrandSpaceSchedulingService.TopicDecision> readTopicDecisions(Object raw) {
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }
        List<BrandSpaceSchedulingService.TopicDecision> result = new ArrayList<>();
        for (Object item : source) {
            Map<String, Object> map = readObjectMap(item);
            if (map.isEmpty()) {
                continue;
            }
            Number priorityScore = readNumber(map.get("priorityScore"));
            result.add(new BrandSpaceSchedulingService.TopicDecision(
                    normalizeText((String) map.get("topicId")),
                    normalizeText((String) map.get("topicName")),
                    toInteger(map.get("allocatedProbeCount")) == null ? 0 : toInteger(map.get("allocatedProbeCount")),
                    priorityScore == null ? 0.0d : priorityScore.doubleValue(),
                    readStringList(map.get("reasonTags"))
            ));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> buildScheduleDecisionMap(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        LinkedHashMap<String, Object> decision = new LinkedHashMap<>();
        decision.put("triggerMode", executionPlan.triggerMode());
        decision.put("targetProbeCount", executionPlan.targetProbeCount());
        decision.put("coverageStrategy", executionPlan.coverageStrategy());
        decision.put("humanReason", executionPlan.humanReason());
        decision.put("coldStart", executionPlan.coldStart());
        decision.put("justEdited", executionPlan.justEdited());
        decision.put("quotaLimited", executionPlan.quotaLimited());
        decision.put("selectedTopicIds", executionPlan.selectedTopicIds());
        decision.put("selectedTopicNames", executionPlan.selectedTopicNames());
        decision.put("deferredTopicIds", executionPlan.deferredTopicIds());
        decision.put("deferredTopicNames", executionPlan.deferredTopicNames());
        decision.put("selectedTopics", executionPlan.selectedTopics().stream()
                .map(BrandSpaceTaskOrderSupport::buildTopicDecisionMap)
                .toList());
        decision.put("deferredTopics", executionPlan.deferredTopics().stream()
                .map(BrandSpaceTaskOrderSupport::buildTopicDecisionMap)
                .toList());
        if (!executionPlan.executionPrompts().isEmpty()) {
            decision.put("executionPrompts", executionPlan.executionPrompts().stream()
                    .map(BrandSpaceTaskOrderSupport::buildExecutionPromptPlanMap)
                    .toList());
        }
        if (executionPlan.accountAvailableCredits() != null) {
            decision.put("accountAvailableCredits", executionPlan.accountAvailableCredits());
        }
        if (executionPlan.accountBrandCount() != null) {
            decision.put("accountBrandCount", executionPlan.accountBrandCount());
        }
        if (executionPlan.brandCoreProbeCount() != null) {
            decision.put("brandCoreProbeCount", executionPlan.brandCoreProbeCount());
        }
        if (executionPlan.brandExpansionProbeCount() != null) {
            decision.put("brandExpansionProbeCount", executionPlan.brandExpansionProbeCount());
        }
        if (executionPlan.weeklyTargetTopicCount() != null) {
            decision.put("weeklyTargetTopicCount", executionPlan.weeklyTargetTopicCount());
        }
        if (executionPlan.weeklyCoveredTopicCount() != null) {
            decision.put("weeklyCoveredTopicCount", executionPlan.weeklyCoveredTopicCount());
        }
        if (executionPlan.weeklyGapTopicCount() != null) {
            decision.put("weeklyGapTopicCount", executionPlan.weeklyGapTopicCount());
        }
        if (executionPlan.weeklyCoverageRatio() != null) {
            decision.put("weeklyCoverageRatio", executionPlan.weeklyCoverageRatio());
        }
        return Map.copyOf(decision);
    }

    private static Map<String, Object> buildTopicDecisionMap(BrandSpaceSchedulingService.TopicDecision topicDecision) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        putIfHasText(map, "topicId", topicDecision.topicId());
        putIfHasText(map, "topicName", topicDecision.topicName());
        map.put("allocatedProbeCount", topicDecision.allocatedProbeCount());
        map.put("priorityScore", topicDecision.priorityScore());
        map.put("reasonTags", topicDecision.reasonTags());
        return Map.copyOf(map);
    }

    private static Map<String, Object> buildExecutionPromptPlanMap(BrandSpaceSchedulingService.ExecutionPromptPlan executionPromptPlan) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        putIfNonNull(map, "seqNo", executionPromptPlan.seqNo());
        putIfHasText(map, "promptId", executionPromptPlan.promptId());
        putIfHasText(map, "topicId", executionPromptPlan.topicId());
        putIfHasText(map, "topicName", executionPromptPlan.topicName());
        putIfHasText(map, "promptText", executionPromptPlan.promptText());
        return Map.copyOf(map);
    }

    private static List<BrandSpaceSchedulingService.ExecutionPromptPlan> readExecutionPromptPlans(Object raw) {
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }
        List<BrandSpaceSchedulingService.ExecutionPromptPlan> result = new ArrayList<>();
        for (Object item : source) {
            Map<String, Object> map = readObjectMap(item);
            if (map.isEmpty()) {
                continue;
            }
            result.add(new BrandSpaceSchedulingService.ExecutionPromptPlan(
                    toInteger(map.get("seqNo")),
                    normalizeText((String) map.get("promptId")),
                    normalizeText((String) map.get("topicId")),
                    normalizeText((String) map.get("topicName")),
                    normalizeText((String) map.get("promptText"))
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
            if (StringUtils.hasText(key) && entry.getValue() != null) {
                map.put(key, entry.getValue());
            }
        }
        return map.isEmpty() ? Map.of() : Map.copyOf(map);
    }

    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : source) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static Number readNumber(Object raw) {
        return raw instanceof Number number ? number : null;
    }

    private static Integer toInteger(Object raw) {
        return raw instanceof Number number ? number.intValue() : null;
    }

    private static Integer firstNonNullInteger(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private static List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private static LinkedHashMap<String, Object> deepMutableCopy(Map<String, Object> source,
                                                                 ObjectMapper objectMapper) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> readMutableMap(Object raw,
                                                                ObjectMapper objectMapper) {
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            return objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeLegacyQuotaFields(Map<String, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key) || LEGACY_QUOTA_KEYS.contains(key)) {
                continue;
            }
            sanitized.put(key, sanitizeLegacyQuotaValue(entry.getValue()));
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeLegacyQuotaValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return sanitizeLegacyQuotaFields((Map<String, Object>) mapValue);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(BrandSpaceTaskOrderSupport::sanitizeLegacyQuotaValue)
                    .toList();
        }
        return value;
    }

    record ContractContext(String channel,
                           String source,
                           String trigger,
                           String lifecycleStage,
                           String requestId,
                           String organizationId,
                           String membershipId,
                           String brandId,
                           String brandSpaceId,
                           Integer configGeneration,
                           String trackingMode,
                           LocalDate scheduleDate,
                           ServiceAccessView serviceAccess) {
    }
}
