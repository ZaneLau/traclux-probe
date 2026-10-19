package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandSpaceTaskOrderSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void previewTaskOrderShouldExposeTopicStateProjections() {
        Map<String, Object> taskOrder = BrandSpaceTaskOrderSupport.buildPreviewTaskOrder(
                draft(),
                List.of(
                        BrandSpaceTopicPayload.builder()
                                .tempId("topic_manual_1")
                                .name("渠道评价")
                                .description("观察 AI 是否提到渠道合作与服务体验。")
                                .enabled(true)
                                .source("manual")
                                .build()
                ),
                List.of(
                        BrandSpacePromptPayload.builder()
                                .tempId("prompt_tmp_1")
                                .topicTempId("topic_manual_1")
                                .text("在渠道合作与服务评价里，探镜会被如何提及？")
                                .enabled(true)
                                .source("system_generated")
                                .build()
                ),
                previewContext(),
                executionPlan(
                        "interactive_preview",
                        6,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic_manual_1", "渠道评价", 6, 0.9d, List.of("冷启动"))),
                        List.of(),
                        "cold_start_wide_coverage",
                        "今天是新品牌首跑，优先做宽覆盖"
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections =
                (List<Map<String, Object>>) businessHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);

        assertEquals(1, projections.size());
        assertEquals("渠道评价", projections.getFirst().get("canonicalTopicLabel"));
        assertEquals("user_added", projections.getFirst().get("sourceType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary =
                (Map<String, Object>) businessHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY);
        assertEquals(1, summary.get("activeTopicCount"));
        assertEquals(LightConeV43TopicStateProjectionSupport.SELECTION_MODE, summary.get("selectionMode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> topicPlan = (Map<String, Object>) businessHints.get("topicPlan");
        assertEquals("brand_space_schedule_plan", topicPlan.get("topicSelectionMode"));
        assertEquals("brand_space_schedule_truth", topicPlan.get("coverageSource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> taskMetadata = (Map<String, Object>) taskOrder.get("taskMetadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> runVersionTruth = (Map<String, Object>) taskMetadata.get(LightConeV43VersionTruthSupport.RUN_VERSION_TRUTH_KEY);
        assertEquals("canonical", runVersionTruth.get("migrationMode"));
        assertEquals("canonicalExecutionLane", runVersionTruth.get("executionLane"));
        assertEquals("geo_brand_visibility_diagnosis", taskOrder.get("runGoal"));
        assertFalse(taskOrder.containsKey("diagnosisGoal"));
        assertFalse(businessHints.containsKey("brandSpacePromptBridge"));
        assertFalse(businessHints.containsKey("brandSpacePromptSnapshot"));
    }

    @Test
    void createTaskOrderShouldCarryExecutionPromptsAndProjectionTraceTogether() {
        Map<String, Object> previewTaskOrder = BrandSpaceTaskOrderSupport.buildPreviewTaskOrder(
                draft(),
                List.of(),
                List.of(),
                previewContext(),
                executionPlan(
                        "interactive_preview",
                        12,
                        List.of(),
                        List.of(),
                        "cold_start_wide_coverage",
                        "今天是新品牌首跑，优先做宽覆盖"
                )
        );
        List<BrandSpaceTopicPayload> topics = List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic_choice")
                        .name("品牌选择")
                        .description("比较探镜和 Profound 哪个更值得先看。")
                        .enabled(true)
                        .source("manual")
                        .build()
        );
        List<BrandSpacePromptPayload> prompts = List.of(
                BrandSpacePromptPayload.builder()
                        .tempId("prompt_choice_1")
                        .topicTempId("topic_choice")
                        .text("探镜和 Profound 哪个更值得先看？")
                        .enabled(true)
                        .source("system_generated")
                        .build(),
                BrandSpacePromptPayload.builder()
                        .tempId("prompt_choice_2")
                        .topicTempId("topic_choice")
                        .text("如果现在只先试一个 AI 可见性平台，通常先看探镜还是 Profound？")
                        .enabled(true)
                        .source("system_generated")
                        .build()
        );

        Map<String, Object> taskOrder = BrandSpaceTaskOrderSupport.mergeCreateTaskOrder(
                previewTaskOrder,
                draft(),
                topics,
                prompts,
                createContext(),
                executionPlan(
                        "create_initial_run",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic_choice", "品牌选择", 12, 0.88d, List.of("冷启动"))),
                        List.of(),
                        "cold_start_wide_coverage",
                        "今天是新品牌首跑，优先做宽覆盖"
                ),
                "deepseek",
                objectMapper
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        assertEquals("geo_brand_visibility_diagnosis", taskOrder.get("runGoal"));
        assertEquals("deepseek", taskOrder.get("targetModelProduct"));
        assertFalse(taskOrder.containsKey("diagnosisGoal"));
        assertFalse(businessHints.containsKey("brandSpacePromptSnapshot"));
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) businessHints.get("samplingEngine");
        assertEquals("deepseek", samplingEngine.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) businessHints.get("samplingRealtimeSearch");
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections =
                (List<Map<String, Object>>) businessHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);
        assertEquals(1, projections.size());
        assertEquals("brand_choice", projections.getFirst().get("problemSpaceBucket"));
        assertTrue(String.valueOf(businessHints.get("userPrompt")).contains("首轮优先跟踪的主题：品牌选择"));
        assertFalse(String.valueOf(businessHints.get("userPrompt")).contains("1. 探镜和 Profound 哪个更值得先看？"));
        @SuppressWarnings("unchecked")
        Map<String, Object> projectionTrace = (Map<String, Object>) projections.getFirst().get("projectionTrace");
        assertEquals(2, projectionTrace.get("enabledPromptCount"));
        @SuppressWarnings("unchecked")
        List<String> linkedPromptRefs = (List<String>) projectionTrace.get("linkedPromptRefs");
        assertEquals(List.of("prompt_choice_1", "prompt_choice_2"), linkedPromptRefs);
        @SuppressWarnings("unchecked")
        Map<String, Object> scheduleDecision = (Map<String, Object>) businessHints.get("scheduleDecision");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionPrompts = (List<Map<String, Object>>) scheduleDecision.get("executionPrompts");
        assertEquals(2, executionPrompts.size());
        assertEquals("prompt_choice_1", executionPrompts.get(0).get("promptId"));
    }

    @Test
    void scheduledTaskOrderAndRefreshShouldUseConfigTopicIdsAsProjectionTruth() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .categorySnapshot("AI 可见性平台")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .trackingMode("ACTIVE")
                .configGeneration(3)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("探镜")
                .officialWebsite("https://tanjing.example.com")
                .build();
        BrandSpaceConfigView configView = configView();

        Map<String, Object> taskOrder = BrandSpaceTaskOrderSupport.buildScheduledTaskOrder(
                scope,
                brand,
                List.of("Profound", "Peec AI"),
                configView,
                "deepseek",
                new BrandSpaceTaskOrderSupport.ContractContext(
                        "brand_space_schedule",
                        "brand_space_schedule",
                        "scheduled_tracking",
                        "active_scheduled_run",
                        "req-1",
                        "org-1",
                        "membership-1",
                        "brand-1",
                        "scope-1",
                        3,
                        "ACTIVE",
                        LocalDate.of(2026, 4, 30),
                        ServiceAccessView.builder().status("trial").build()
                ),
                executionPlan(
                        "scheduled_tracking",
                        8,
                        List.of(
                                new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 5, 0.92d, List.of("补债")),
                                new BrandSpaceSchedulingService.TopicDecision("topic-2", "渠道评价", 3, 0.61d, List.of("均衡覆盖"))
                        ),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题"
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        assertEquals("geo_brand_visibility_diagnosis", taskOrder.get("runGoal"));
        assertEquals("deepseek", taskOrder.get("targetModelProduct"));
        assertFalse(taskOrder.containsKey("diagnosisGoal"));
        @SuppressWarnings("unchecked")
        List<String> competitors = (List<String>) taskOrder.get("competitors");
        assertEquals(List.of("Profound", "Peec AI"), competitors);
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) businessHints.get("samplingEngine");
        assertEquals("deepseek", samplingEngine.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) businessHints.get("samplingRealtimeSearch");
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections =
                (List<Map<String, Object>>) businessHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);
        assertEquals(2, projections.size());
        assertEquals("品牌选择", projections.getFirst().get("canonicalTopicLabel"));

        BrandSpaceConfigView refreshedConfig = BrandSpaceConfigView.builder()
                .topics(List.of(
                        BrandSpaceConfigView.TopicView.builder()
                                .topicId("topic-9")
                                .name("风险异议")
                                .description("最新投诉与劝退点")
                                .enabled(true)
                                .source("manual")
                                .build()
                ))
                .prompts(List.of(
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-9")
                                .topicId("topic-9")
                                .topicName("风险异议")
                                .text("探镜最常见的劝退点是什么？")
                                .enabled(true)
                                .build()
                ))
                .build();
        Map<String, Object> refreshedTaskOrder = BrandSpaceTaskOrderSupport.refreshRuntimeExecutionHints(
                taskOrder,
                refreshedConfig,
                objectMapper
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> refreshedHints = (Map<String, Object>) refreshedTaskOrder.get("businessHints");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refreshedProjections =
                (List<Map<String, Object>>) refreshedHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);
        assertEquals(1, refreshedProjections.size());
        assertEquals("风险异议", refreshedProjections.getFirst().get("canonicalTopicLabel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> refreshedSummary =
                (Map<String, Object>) refreshedHints.get(LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY);
        assertEquals(1, refreshedSummary.get("activeTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> refreshedTopicPlan = (Map<String, Object>) refreshedHints.get("topicPlan");
        assertEquals("brand_space_schedule_plan", refreshedTopicPlan.get("topicSelectionMode"));
        assertEquals("brand_space_schedule_truth", refreshedTopicPlan.get("coverageSource"));
        assertFalse(refreshedHints.containsKey("brandSpacePromptSnapshot"));
    }

    private BrandSpaceDraftPayload draft() {
        return BrandSpaceDraftPayload.builder()
                .brandName("探镜")
                .category("AI 可见性平台")
                .officialWebsite("https://tanjing.example.com")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .competitors(List.of("Profound"))
                .supplementalBrief("关注 AI 是否提到品牌比较与渠道体验。")
                .build();
    }

    private BrandSpaceTaskOrderSupport.ContractContext previewContext() {
        return new BrandSpaceTaskOrderSupport.ContractContext(
                "brand_space_preview",
                "brand_space_preview",
                "interactive_preview",
                "draft_preview",
                "req-preview",
                "org-1",
                "membership-1",
                null,
                null,
                null,
                null,
                null,
                ServiceAccessView.builder().status("active").build()
        );
    }

    private BrandSpaceTaskOrderSupport.ContractContext createContext() {
        return new BrandSpaceTaskOrderSupport.ContractContext(
                "brand_space_create_initial_run",
                "brand_space_create_initial_run",
                "interactive_create",
                "create_initial_run",
                "req-create",
                "org-1",
                "membership-1",
                null,
                null,
                null,
                "ACTIVE",
                null,
                ServiceAccessView.builder().status("active").build()
        );
    }

    private BrandSpaceConfigView configView() {
        return BrandSpaceConfigView.builder()
                .brandSpaceId("scope-1")
                .topics(List.of(
                        BrandSpaceConfigView.TopicView.builder()
                                .topicId("topic-1")
                                .name("品牌选择")
                                .description("比较探镜和 Profound 哪个更值得买。")
                                .enabled(true)
                                .source("manual")
                                .build(),
                        BrandSpaceConfigView.TopicView.builder()
                                .topicId("topic-2")
                                .name("渠道评价")
                                .description("观察合作伙伴与服务体验。")
                                .enabled(true)
                                .source("manual")
                                .build()
                ))
                .prompts(List.of(
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-1")
                                .topicId("topic-1")
                                .topicName("品牌选择")
                                .text("探镜和 Profound 哪个更值得先看？")
                                .enabled(true)
                                .build(),
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-2")
                                .topicId("topic-2")
                                .topicName("渠道评价")
                                .text("在渠道合作评价里，探镜会被怎么提及？")
                                .enabled(true)
                                .build()
                ))
                .build();
    }

    private BrandSpaceSchedulingService.ExecutionPlan executionPlan(String triggerMode,
                                                                    int targetProbeCount,
                                                                    List<BrandSpaceSchedulingService.TopicDecision> selectedTopics,
                                                                    List<BrandSpaceSchedulingService.TopicDecision> deferredTopics,
                                                                    String coverageStrategy,
                                                                    String humanReason) {
        return new BrandSpaceSchedulingService.ExecutionPlan(
                triggerMode,
                targetProbeCount,
                selectedTopics,
                deferredTopics,
                coverageStrategy,
                humanReason,
                true,
                false,
                false,
                50,
                1,
                12,
                20,
                2,
                0,
                2,
                0.0d
        );
    }
}
