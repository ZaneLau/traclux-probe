package com.geoprobe.service.shared;

import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightConeV43TopicStateProjectionSupportTest {

    @Test
    void shouldPreserveQualityEvidenceBucketWhenEvidenceTopicContainsRiskWords() {
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope envelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        BrandSpaceConfigView.builder()
                                .topics(List.of(
                                        topic("topic-quality", "质量证据", "处理材质、官网、第三方证据和承重判断。", "user_added")
                                ))
                                .prompts(List.of(
                                        prompt("prompt-quality-1", "topic-quality", "质量证据",
                                                "美厨物语的官网和第三方口碑靠谱吗，材质和承重到底该怎么看")
                                ))
                                .build(),
                        "active_scheduled_run"
                );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections = (List<Map<String, Object>>) envelope.projections();
        assertEquals(1, projections.size());
        assertEquals("quality_evidence", projections.getFirst().get("problemSpaceBucket"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectionTrace = (Map<String, Object>) projections.getFirst().get("projectionTrace");
        @SuppressWarnings("unchecked")
        List<String> matchedKeywords = (List<String>) projectionTrace.get("matchedKeywords");
        assertTrue(matchedKeywords.contains("官网"));
        assertTrue(matchedKeywords.contains("第三方"));
        assertTrue(matchedKeywords.contains("材质"));
    }

    @Test
    void shouldKeepRiskObjectionBucketForRiskFocusedTopic() {
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope envelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        BrandSpaceConfigView.builder()
                                .topics(List.of(
                                        topic("topic-risk", "风险异议", "处理顾虑、劝退点、投诉和翻车风险。", "manual")
                                ))
                                .prompts(List.of(
                                        prompt("prompt-risk-1", "topic-risk", "风险异议",
                                                "美厨物语最常见的顾虑和劝退点是什么，真的靠谱吗")
                                ))
                                .build(),
                        "active_scheduled_run"
                );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections = (List<Map<String, Object>>) envelope.projections();
        assertEquals(1, projections.size());
        assertEquals("risk_objection", projections.getFirst().get("problemSpaceBucket"));
    }

    @Test
    void shouldExposeFourProblemSpaceBucketsForMeichuStyleConfig() {
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope envelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        BrandSpaceConfigView.builder()
                                .topics(List.of(
                                        topic("topic-1", "品牌选择", "处理品牌比较、候选筛选和值不值得买。", "system_generated"),
                                        topic("topic-2", "风险异议", "处理顾虑、劝退点、踩坑和口碑疑虑。", "system_generated"),
                                        topic("topic-3", "场景适配", "处理小户型、出租屋、空间限制与适用人群。", "user_added"),
                                        topic("topic-4", "质量证据", "处理材质、承重、官网与第三方证据。", "user_added")
                                ))
                                .prompts(List.of(
                                        prompt("prompt-1", "topic-1", "品牌选择", "厨房收纳怎么选，美厨物语值不值得买？"),
                                        prompt("prompt-2", "topic-1", "品牌选择", "美厨物语和YWEEL哪个好？"),
                                        prompt("prompt-3", "topic-4", "质量证据", "美厨物语的官网和第三方口碑靠谱吗？"),
                                        prompt("prompt-4", "topic-2", "风险异议", "美厨物语的真实口碑怎么样，最常见的优缺点是什么？"),
                                        prompt("prompt-5", "topic-1", "品牌选择", "美厨物语贵不贵，厨房收纳里它的性价比怎么样？"),
                                        prompt("prompt-6", "topic-3", "场景适配", "什么人、什么使用场景更适合选美厨物语？"),
                                        prompt("prompt-7", "topic-1", "品牌选择", "美厨物语最值得强调的卖点是什么，它和YWEEL差在哪？"),
                                        prompt("prompt-8", "topic-2", "风险异议", "用户在买厨房收纳时最怕踩哪些坑，美厨物语能避开吗？"),
                                        prompt("prompt-9", "topic-1", "品牌选择", "如果不指定品牌，AI 会推荐美厨物语还是其他厨房收纳品牌？"),
                                        prompt("prompt-10", "topic-2", "风险异议", "用户在考虑美厨物语时，最常见的顾虑和劝退点是什么？")
                                ))
                                .build(),
                        "active_scheduled_run"
                );

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) envelope.summary();
        assertEquals(
                List.of("brand_choice", "risk_objection", "usage_scenario", "quality_evidence"),
                summary.get("problemSpaceBuckets")
        );
    }

    @Test
    void shouldEmitReusableGroundedSignalsForSpecificTopics() {
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope envelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        BrandSpaceConfigView.builder()
                                .topics(List.of(
                                        topic("topic-scene", "学生党与宿舍场景适配", "处理学生党、宿舍、小桌面等场景适配。", "user_added"),
                                        topic("topic-quality", "做工耐用与长期使用风险", "处理做工、耐用度、寿命和长期使用风险。", "system_generated")
                                ))
                                .build(),
                        "active_scheduled_run"
                );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections = (List<Map<String, Object>>) envelope.projections();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstSignals = (List<Map<String, Object>>) projections.get(0).get("topicGroundedSignals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondSignals = (List<Map<String, Object>>) projections.get(1).get("topicGroundedSignals");

        assertTrue(firstSignals.stream().anyMatch(signal -> "usage_scenario".equals(signal.get("slot"))));
        assertTrue(firstSignals.stream().anyMatch(signal -> "decision_factor".equals(signal.get("slot"))));
        assertTrue(secondSignals.stream().anyMatch(signal -> "brand_evidence".equals(signal.get("slot"))));
        assertTrue(secondSignals.stream().anyMatch(signal -> "decision_factor".equals(signal.get("slot"))));
    }

    @Test
    void shouldClassifyFeatureSpecificTopicsAwayFromGeneralVisibilityAndEmitDetailedEvidence() {
        LightConeV43TopicStateProjectionSupport.ProjectionEnvelope envelope =
                LightConeV43TopicStateProjectionSupport.buildConfigProjectionEnvelope(
                        BrandSpaceConfigView.builder()
                                .topics(List.of(
                                        topic("topic-wireless", "无线 / 双模的连接稳定性", "处理无线与双模连接、延迟、掉连和切换稳定性。", "system_generated"),
                                        topic("topic-dpi", "多DPI调节是不是实用功能", "处理多DPI调节在办公与轻度游戏里的真实实用性。", "system_generated"),
                                        topic("topic-claims", "防水静音这些附加卖点靠不靠谱", "处理防水、静音等附加卖点是不是噱头、是否靠谱。", "system_generated")
                                ))
                                .build(),
                        "active_scheduled_run"
                );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections = (List<Map<String, Object>>) envelope.projections();

        assertEquals("quality_evidence", projections.get(0).get("problemSpaceBucket"));
        assertEquals("quality_evidence", projections.get(1).get("problemSpaceBucket"));
        assertTrue(List.of("quality_evidence", "risk_objection").contains(projections.get(2).get("problemSpaceBucket")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wirelessSignals = (List<Map<String, Object>>) projections.get(0).get("topicGroundedSignals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dpiSignals = (List<Map<String, Object>>) projections.get(1).get("topicGroundedSignals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> claimsSignals = (List<Map<String, Object>>) projections.get(2).get("topicGroundedSignals");

        assertTrue(wirelessSignals.stream().anyMatch(signal ->
                "brand_evidence".equals(signal.get("slot"))
                        && "无线与双模连接、延迟、掉连和切换稳定性".equals(signal.get("value"))));
        assertTrue(wirelessSignals.stream().anyMatch(signal ->
                "decision_factor".equals(signal.get("slot"))
                        && "无线与双模连接、延迟、掉连和切换稳定性".equals(signal.get("value"))));
        assertTrue(dpiSignals.stream().anyMatch(signal ->
                "brand_evidence".equals(signal.get("slot"))
                        && "多DPI调节在办公与轻度游戏里的真实实用性".equals(signal.get("value"))));
        assertTrue(claimsSignals.stream().anyMatch(signal ->
                "brand_evidence".equals(signal.get("slot"))
                        && "防水、静音等附加卖点是不是噱头、是否靠谱".equals(signal.get("value"))));
    }

    private BrandSpaceConfigView.TopicView topic(String topicId, String name, String description, String source) {
        return BrandSpaceConfigView.TopicView.builder()
                .topicId(topicId)
                .name(name)
                .description(description)
                .enabled(true)
                .source(source)
                .build();
    }

    private BrandSpaceConfigView.PromptView prompt(String promptId, String topicId, String topicName, String text) {
        return BrandSpaceConfigView.PromptView.builder()
                .promptId(promptId)
                .topicId(topicId)
                .topicName(topicName)
                .text(text)
                .enabled(true)
                .build();
    }
}
