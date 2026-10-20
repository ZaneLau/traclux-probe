package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeSimulationPlanningServiceTest {

    private final ProbeSimulationPlanningService service = new ProbeSimulationPlanningService();

    @Test
    void shouldPlanConfirmationStyleForVerificationAndResourceAcquisition() {
        RealizationPlan plan = service.plan(
                BucketType.ORTHOGONAL,
                tags(Map.of("D1", "verification", "D2", "resource_acquisition", "D4", "optimal")),
                translatedTags(),
                null
        );

        assertEquals("confirm_and_buy", plan.simulationIntentProfile().surfaceIntent());
        assertEquals("confirmation_seeking", plan.simulationIntentProfile().interactionPosture());
        assertEquals("explicit_brand_reference", plan.simulationIntentProfile().brandFraming());
        assertEquals(ProbeExposurePolicy.BRAND_VISIBLE, plan.realizationConstraints().brandExposurePolicy());
    }

    @Test
    void shouldPlanHighDegradationForUrgentAndSeverelyDepletedStates() {
        RealizationPlan plan = service.plan(
                BucketType.ORTHOGONAL,
                tags(Map.of("D1", "initiation", "D4", "severely_depleted", "D5", "acute_emergency")),
                translatedTags(),
                null
        );

        assertEquals("stressed_urgent", plan.simulationIntentProfile().interactionPosture());
        assertEquals("high", plan.simulationIntentProfile().languageDegradationLevel());
        assertEquals("orthogonal_seek_clear_answer_single_turn_search_like",
                plan.realizationConstraints().fallbackFamily());
    }

    @Test
    void shouldPlanDialogicConversationModeForD6Dialogic() {
        RealizationPlan plan = service.plan(
                BucketType.ORTHOGONAL,
                tags(Map.of("D6", "dialogic")),
                translatedTags(),
                null
        );

        assertEquals("back_and_forth_consultation", plan.simulationIntentProfile().conversationMode());
        assertEquals("natural_question_only", plan.realizationConstraints().questionFormPolicy());
    }

    @Test
    void shouldPlanParetoFamiliesWithDistinctInteractionProfiles() {
        RealizationPlan shortlist = service.plan(
                BucketType.PARETO,
                tags(Map.of()),
                translatedTags(),
                "现在买{category}，优先看哪个",
                "pareto_recommendation_shortlist"
        );
        RealizationPlan trustSignal = service.plan(
                BucketType.PARETO,
                tags(Map.of()),
                translatedTags(),
                "{category}靠不靠谱，通常先看哪几点",
                "pareto_trust_signal"
        );

        assertEquals("high_frequency_decision_query", shortlist.simulationIntentProfile().surfaceIntent());
        assertEquals("search_like_prompt", shortlist.simulationIntentProfile().conversationMode());
        assertEquals("seek_clear_answer", trustSignal.simulationIntentProfile().surfaceIntent());
        assertEquals("deliberate_structuring", trustSignal.simulationIntentProfile().interactionPosture());
        assertEquals("pareto_trust_signal", trustSignal.realizationConstraints().fallbackFamily());
    }

    @Test
    void shouldPlanAdversarialFamiliesWithDistinctInteractionProfiles() {
        RealizationPlan rumor = service.plan(
                BucketType.ADVERSARIAL,
                tags(Map.of()),
                translatedTags(),
                "网上说{brandName}问题很多，靠谱吗",
                "adversarial_rumor_challenge"
        );
        RealizationPlan exclusion = service.plan(
                BucketType.ADVERSARIAL,
                tags(Map.of()),
                translatedTags(),
                "推荐{category}的时候先别提{brandName}",
                "adversarial_brand_exclusion"
        );

        assertEquals("brand_stress_test", rumor.simulationIntentProfile().surfaceIntent());
        assertEquals("skeptical_challenging", rumor.simulationIntentProfile().interactionPosture());
        assertEquals("brand_exclusion_test", exclusion.simulationIntentProfile().surfaceIntent());
        assertEquals("adversarial_brand_exclusion", exclusion.realizationConstraints().fallbackFamily());
    }

    @Test
    void shouldProjectMinusOneSignalsIntoPlanningSoftHints() {
        RealizationPlan plan = service.plan(
                BucketType.ORTHOGONAL,
                tags(Map.of()),
                translatedTags(),
                null,
                null,
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "包装饮用水",
                        List.of("怡宝", "百岁山"),
                        List.of("包装饮用水", "天然水"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("怡宝"),
                        List.of(),
                        List.of(),
                        List.of(
                                new GroundedContextSignal(
                                        MinusOneContextSlots.USAGE_SCENARIO,
                                        "家庭常备",
                                        "benchmark_fixture",
                                        "家庭装/常备场景",
                                        0.91,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DESCRIPTIVE_CUE,
                                        "家里常备喝的矿泉水",
                                        "benchmark_fixture",
                                        "真实口语入口",
                                        0.88,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DECISION_FACTOR,
                                        "口感更稳",
                                        "benchmark_fixture",
                                        "常见判断点",
                                        0.84,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.COMPARISON_TARGET,
                                        "怡宝",
                                        "benchmark_fixture",
                                        "常见比较对象",
                                        0.83,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                )
                        ),
                        List.of(),
                        Map.of()
                )
        );

        assertEquals(
                List.of(
                        "D1:exploration=越看越乱",
                        "D4:moderate_strain=有点累但还能想",
                        "D5:anticipatory_constraint=还有几天缓冲",
                        "D6:recognition=随便刷刷看",
                        "SCENE:家庭常备",
                        "DECISION:口感更稳",
                        "DESCRIBE:家里常备喝的矿泉水",
                        "COMPARE:怡宝"
                ),
                plan.realizationConstraints().softSemanticHints()
        );
    }

    @Test
    void shouldProjectAssignedTopicSignalsIntoPlanningSoftHints() {
        RealizationPlan plan = service.plan(
                BucketType.ORTHOGONAL,
                tags(Map.of()),
                translatedTags(),
                null,
                null,
                new EngineTaskContextPackage(
                        "AOC",
                        "鼠标",
                        List.of("罗技"),
                        List.of("鼠标", "办公鼠标"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("罗技"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of()
                ),
                new LightConeV43TopicStateProjectionSupport.TopicStateProjection(
                        "topic-aoc-wireless",
                        LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION,
                        "无线 / 双模的连接稳定性",
                        "general_visibility",
                        Map.of("D1", 0.55d, "D2", 0.60d, "D3", 0.55d, "D4", 0.45d, "D5", 0.50d, "D6", 0.55d),
                        List.of(),
                        1,
                        1,
                        0.4d,
                        0.3d,
                        "system_generated",
                        List.of(
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DECISION_FACTOR,
                                        "无线 / 双模的连接稳定性",
                                        "topic_projection_compiler",
                                        "topic_contract",
                                        0.9d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DESCRIPTIVE_CUE,
                                        "无线 / 双模的连接稳定性",
                                        "topic_projection_compiler",
                                        "topic_contract",
                                        0.85d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.BRAND_EVIDENCE,
                                        "无线与双模连接、延迟、掉连和切换稳定性",
                                        "topic_projection_compiler",
                                        "topic_contract",
                                        0.88d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                )
                        ),
                        Map.of()
                )
        );

        assertEquals(
                List.of(
                        "D1:exploration=越看越乱",
                        "D4:moderate_strain=有点累但还能想",
                        "D5:anticipatory_constraint=还有几天缓冲",
                        "D6:recognition=随便刷刷看",
                        "COMPARE:罗技",
                        "TOPIC:无线 / 双模的连接稳定性",
                        "EVIDENCE:无线与双模连接、延迟、掉连和切换稳定性"
                ),
                plan.realizationConstraints().softSemanticHints()
        );
    }

    private Map<String, String> tags(Map<String, String> overrides) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "exploration");
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", "moderate_strain");
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        tags.putAll(overrides);
        return tags;
    }

    private Map<String, String> translatedTags() {
        return Map.of(
                "D1", "越看越乱",
                "D2", "想知道明确答案",
                "D3", "想搞懂原理",
                "D4", "有点累但还能想",
                "D5", "还有几天缓冲",
                "D6", "随便刷刷看"
        );
    }
}
