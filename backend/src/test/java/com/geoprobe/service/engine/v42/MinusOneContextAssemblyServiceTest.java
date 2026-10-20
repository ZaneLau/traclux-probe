package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.MinusOneContextExpansionAiService;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinusOneContextAssemblyServiceTest {

    private final MinusOneContextAssemblyService service = new MinusOneContextAssemblyService(
            null,
            new ObjectMapper()
    );

    @Test
    void shouldAssembleGroundedSignalsForKitchenRackCategory() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "帅仕",
                "厨房沥水架",
                List.of("佳帮手", "太力"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of(
                        "userPrompt", "想找个不容易积水、安装稳一点的厨房沥水架"
                ),
                Map.of("source", "test")
        );

        EngineTaskContextPackage contextPackage = service.assemble(
                taskOrder,
                List.of("厨房沥水架", "碗碟沥水架", "水槽置物架", "不锈钢沥水篮"),
                false,
                "explicit_input",
                Map.of("assemblySource", "test_case")
        );

        assertEquals(List.of("厨房台面收纳", "水槽旁沥水整理"), contextPackage.categorySceneMap());
        assertEquals(List.of("碗碟沥水架", "水槽置物架", "不锈钢沥水篮"), contextPackage.descriptiveEntrypoints());
        assertEquals(List.of("更在意稳妥不踩坑"), contextPackage.personaExpressionTendencies());
        assertTrue(contextPackage.targetModelContextSignals().contains("允许轻咨询式表达"));
        assertEquals(
                List.of("碗碟沥水架", "水槽置物架", "不锈钢沥水篮"),
                contextPackage.slotValuesForUse(
                        MinusOneContextSlots.PRODUCT_FORM_FACTOR,
                        MinusOneContextUses.NODE0_STATE_INFERENCE,
                        3
                )
        );
        assertTrue(contextPackage.slotValues(MinusOneContextSlots.DECISION_FACTOR).contains("排水不积水"));
        assertTrue(contextPackage.slotValues(MinusOneContextSlots.DECISION_FACTOR).contains("安装稳固"));
        assertTrue(contextPackage.slotValues(MinusOneContextSlots.COMPARISON_TARGET).contains("佳帮手"));
        assertTrue(contextPackage.missingContextItems().contains("brandBrief"));
        assertEquals("deterministic_derived", contextPackage.extensibleSignals().get("contextAssemblyMode"));
    }

    @Test
    void shouldAugmentThinSnapshotButPreserveRichSnapshotFields() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of(
                        "userPrompt", "冲奶粉和家庭常备的话先看什么更稳妥"
                ),
                Map.of("source", "test")
        );

        EngineTaskContextPackage snapshot = new EngineTaskContextPackage(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝"),
                List.of("包装饮用水", "饮用天然水"),
                List.of(),
                false,
                null,
                List.of("家庭常备"),
                List.of("家里常备喝的水"),
                List.of("怡宝"),
                List.of(),
                List.of("允许先做轻咨询"),
                List.of(),
                List.of("brandBrief", "personaExpressionTendencies", "groundedSignals"),
                Map.of("assemblySource", "snapshot")
        );

        EngineTaskContextPackage contextPackage = service.augment(
                taskOrder,
                snapshot,
                List.of("包装饮用水", "饮用天然水", "桶装水"),
                false,
                "resolver",
                Map.of("assemblySource", "augment_test")
        );

        assertEquals(List.of("家庭常备"), contextPackage.categorySceneMap());
        assertEquals(List.of("家里常备喝的水"), contextPackage.descriptiveEntrypoints());
        assertEquals(List.of("允许先做轻咨询"), contextPackage.targetModelContextSignals());
        assertTrue(contextPackage.groundedSignals().stream()
                .anyMatch(signal -> MinusOneContextSlots.DECISION_FACTOR.equals(signal.slot())));
        assertTrue(contextPackage.groundedSignals().stream()
                .anyMatch(signal -> MinusOneContextSlots.USAGE_SCENARIO.equals(signal.slot())));
        assertTrue(contextPackage.missingContextItems().contains("brandBrief"));
        assertTrue(contextPackage.personaExpressionTendencies().contains("更在意稳妥不踩坑"));
    }

    @Test
    void shouldNotPromoteTaxonomyAnchorsToDescriptiveCues() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "红豆衬衫",
                "男装衬衫",
                List.of("男装衬衫", "男装衬衫品牌", "男装衬衫类型"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of(),
                Map.of("source", "test")
        );

        EngineTaskContextPackage contextPackage = service.assemble(
                taskOrder,
                List.of("男装衬衫", "男装衬衫品牌", "男装衬衫类型"),
                false,
                "explicit_input",
                Map.of("assemblySource", "taxonomy_guard")
        );

        assertTrue(contextPackage.descriptiveEntrypoints().isEmpty());
        assertTrue(contextPackage.descriptiveEntrypoints().stream()
                .noneMatch(value -> value.contains("衬衫品牌") || value.contains("衬衫类型")));
        assertTrue(contextPackage.missingContextItems().contains("descriptiveEntrypoints"));
        assertTrue(contextPackage.groundedSignals().stream()
                .anyMatch(signal -> MinusOneContextSlots.CATEGORY_HYPOTHESIS.equals(signal.slot())));
    }

    @Test
    void shouldUseEvidenceExpansionWhenAiPayloadIsValid() {
        MinusOneContextExpansionAiService aiService = (brandName, category, competitors, categoryAnchors, userPrompt, brandWebsite, evidenceSnippets, benchmarkQueries) -> """
                {
                  "brandBrief": "报喜鸟聚焦商务男装与通勤场景",
                  "productForms": ["商务衬衫", "西服套装"],
                  "usageScenarios": ["商务通勤穿着", "面试场景搭配"],
                  "decisionFactors": ["版型是否利落", "面料透气性"],
                  "descriptiveCues": ["通勤男装", "上班穿搭"],
                  "memoryCues": ["那家做商务男装的品牌"],
                  "channelCues": ["京东男装频道"],
                  "geoOrOriginCues": ["江浙男装产业带"],
                  "scenarioGraph": ["通勤->会客->晚间社交"],
                  "intentLibrary": ["先确认版型再下单"],
                  "objectionLibrary": ["担心尺码不准"],
                  "competitorConfusions": ["报喜鸟和雅戈尔容易被并列比较"],
                  "priceSensitivitySignals": ["可接受中高客单价"],
                  "riskNarratives": ["担心线上下单后不合身"],
                  "targetModelSignals": ["优先给出可执行的尺码判断建议"],
                  "brandEvidence": ["官方强调商务场景与品质面料"]
                }
                """;
        MinusOneContextAssemblyService evidenceService = new MinusOneContextAssemblyService(aiService, new ObjectMapper());
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "报喜鸟",
                "男装",
                List.of("雅戈尔", "海澜之家"),
                "https://www.baoxiniao.com",
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of("userPrompt", "通勤买男装，想先看版型和面料"),
                Map.of("source", "test")
        );

        EngineTaskContextPackage contextPackage = evidenceService.assemble(
                taskOrder,
                List.of("男装", "商务衬衫", "西服套装"),
                false,
                "explicit_input",
                Map.of("externalEvidence", List.of("京东男装榜单里报喜鸟常和雅戈尔并列"))
        );

        assertEquals("external_evidence_augmented", contextPackage.extensibleSignals().get("contextAssemblyMode"));
        assertTrue(contextPackage.categorySceneMap().contains("商务通勤穿着"));
        assertTrue(contextPackage.slotValues(MinusOneContextSlots.MEMORY_CUE).contains("那家做商务男装的品牌"));
        assertTrue(contextPackage.slotValues(MinusOneContextSlots.BRAND_EVIDENCE).contains("官方强调商务场景与品质面料"));
        assertTrue(contextPackage.brandBrief().contains("报喜鸟聚焦商务男装与通勤场景"));
    }

    @Test
    void shouldNotTreatRealtimeSearchControlHintsAsExternalEvidence() {
        MinusOneContextExpansionAiService aiService = (brandName, category, competitors, categoryAnchors, userPrompt, brandWebsite, evidenceSnippets, benchmarkQueries) -> """
                {
                  "memoryCues": ["那家做厨房收纳的品牌"]
                }
                """;
        MinusOneContextAssemblyService evidenceService = new MinusOneContextAssemblyService(aiService, new ObjectMapper());
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "佳帮手",
                "厨房置物架",
                List.of("太力"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of("userPrompt", "想看厨房置物架里比较省心的选择"),
                Map.of(
                        "samplingRealtimeSearch.enabled", true,
                        "samplingRealtimeSearch.provider", "doubao"
                )
        );

        EngineTaskContextPackage contextPackage = evidenceService.assemble(
                taskOrder,
                List.of("厨房置物架", "厨房收纳架"),
                false,
                "explicit_input",
                Map.of(
                        "samplingRealtimeSearch.maxEvidenceRetries", 2,
                        "samplingRealtimeSearch.minEvidenceSuccessRate", 0.9d
                )
        );

        assertEquals("deterministic_derived", contextPackage.extensibleSignals().get("contextAssemblyMode"));
        assertEquals(0, contextPackage.extensibleSignals().get("evidenceSnippetCount"));
        assertTrue(contextPackage.groundedSignals().stream()
                .filter(signal -> MinusOneContextSlots.MEMORY_CUE.equals(signal.slot()))
                .allMatch(signal -> "local_context_expansion_llm".equals(signal.source())));
    }

    @Test
    void shouldPromoteTopicStateProjectionsIntoTopLevelExtensibleSignals() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "探镜",
                "AI 可见性平台",
                List.of("Profound"),
                null,
                "geo_brand_visibility_diagnosis",
                null,
                null,
                null,
                Map.of(
                        LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY,
                        List.of(
                                Map.ofEntries(
                                        Map.entry("topicId", "topic-choice"),
                                        Map.entry("projectionVersion", "light_cone_topic_projection_v4_3_r3a"),
                                        Map.entry("canonicalTopicLabel", "品牌选择"),
                                        Map.entry("problemSpaceBucket", "brand_choice"),
                                        Map.entry("dimensionWeights", Map.of(
                                                "D1", 0.60d,
                                                "D2", 0.90d,
                                                "D3", 0.75d,
                                                "D4", 0.45d,
                                                "D5", 0.70d,
                                                "D6", 0.55d
                                        )),
                                        Map.entry("stateBias", List.of(
                                                Map.of(
                                                        "matchTags", Map.of("D2", "resource_acquisition"),
                                                        "biasScore", 0.6d,
                                                        "reason", "品牌选择类 topic 优先资源获取态"
                                                )
                                        )),
                                        Map.entry("coverageFloor", 2),
                                        Map.entry("topicQuotaFloor", 1),
                                        Map.entry("topicFreshnessBoost", 0.75d),
                                        Map.entry("stateCoverageDebtBoost", 0.35d),
                                        Map.entry("sourceType", "user_added"),
                                        Map.entry("projectionTrace", Map.of("rawTopicId", "topic-choice"))
                                )
                        ),
                        LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY,
                        Map.of(
                                "projectionVersion", "light_cone_topic_projection_v4_3_r3a",
                                "selectionMode", LightConeV43TopicStateProjectionSupport.SELECTION_MODE,
                                "activeTopicCount", 1
                        )
                ),
                Map.of("source", "test")
        );

        EngineTaskContextPackage contextPackage = service.assemble(
                taskOrder,
                List.of("AI 可见性平台", "AI 搜索可见性"),
                false,
                "explicit_input",
                Map.of("assemblySource", "projection_case")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projections = (List<Map<String, Object>>) contextPackage.extensibleSignals()
                .get(LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY);
        assertEquals(1, projections.size());
        assertEquals("品牌选择", projections.getFirst().get("canonicalTopicLabel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) contextPackage.extensibleSignals()
                .get(LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY);
        assertEquals(LightConeV43TopicStateProjectionSupport.SELECTION_MODE, summary.get("selectionMode"));
    }
}
