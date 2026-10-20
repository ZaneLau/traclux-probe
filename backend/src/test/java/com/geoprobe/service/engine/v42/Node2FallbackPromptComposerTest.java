package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationConstraints;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.SimulationIntentProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Node2FallbackPromptComposerTest {

    private final Node2FallbackPromptComposer composer = new Node2FallbackPromptComposer();

    @Test
    void shouldUseEvidenceHintWhenBuildingSeekClearAnswerFallback() {
        String prompt = composer.fallbackCoreQuestion(
                "AOC这个办公鼠标",
                ProbeExposurePolicy.BRAND_VISIBLE,
                realizationPlan(
                        "seek_clear_answer",
                        List.of(
                                "D2:informational_directed",
                                "D3:identity_freedom"
                        ),
                        List.of(
                                "TOPIC:防水静音这些附加卖点靠不靠谱",
                                "EVIDENCE:防水、静音等附加卖点是不是噱头、是否靠谱"
                        )
                ),
                ""
        );

        assertTrue(prompt.contains("防水静音这些附加卖点"));
        assertTrue(prompt.contains("靠不靠谱"));
        assertTrue(prompt.contains("大众"));
    }

    @Test
    void shouldUseEvidenceHintWhenBuildingShortSeekFallback() {
        String prompt = composer.fallbackShortQuestion(
                "AOC这个办公鼠标",
                ProbeExposurePolicy.BRAND_VISIBLE,
                realizationPlan(
                        "seek_clear_answer",
                        List.of(
                                "D2:informational_directed",
                                "D3:understanding_competence"
                        ),
                        List.of(
                                "TOPIC:多DPI调节是不是实用功能",
                                "EVIDENCE:多DPI调节在办公与轻度游戏里的真实实用性"
                        )
                ),
                ""
        );

        assertEquals("想先看明白的话，AOC这个办公鼠标的DPI调节到底实不实用，值得先看吗", prompt);
    }

    @Test
    void shouldUseTopicHintWhenEvidenceHintIsAbsent() {
        String prompt = composer.fallbackBrowseQuestion(
                "AOC这个鼠标",
                ProbeExposurePolicy.BRAND_VISIBLE,
                realizationPlan(
                        "browse_for_options",
                        List.of(
                                "D2:informational_directed",
                                "D3:subsistence_protection"
                        ),
                        List.of(
                                "TOPIC:学生党与宿舍场景适配"
                        )
                ),
                ""
        );

        assertEquals("学生党宿舍里想用AOC这个鼠标，先看什么更合适", prompt);
    }

    @Test
    void shouldUseTrustTopicHintForGenericBrandSpaceTopic() {
        String prompt = composer.fallbackCoreQuestion(
                "霜山这个厨房收纳",
                ProbeExposurePolicy.BRAND_VISIBLE,
                realizationPlan(
                        "seek_clear_answer",
                        List.of(
                                "D1:verification",
                                "D3:understanding_competence"
                        ),
                        List.of(
                                "TOPIC:官网与信任线索"
                        )
                ),
                ""
        );

        assertEquals("判断霜山这个厨房收纳靠不靠谱，官网、可信来源和口碑该先看哪边", prompt);
    }

    @Test
    void shouldUseDecisionFrictionTopicHintForGenericBrandSpaceTopic() {
        String prompt = composer.fallbackShortQuestion(
                "霜山这个厨房收纳",
                ProbeExposurePolicy.BRAND_VISIBLE,
                realizationPlan(
                        "confirm_and_buy",
                        List.of(
                                "D1:verification",
                                "D3:subsistence_protection"
                        ),
                        List.of(
                                "TOPIC:购买决策阻力"
                        )
                ),
                ""
        );

        assertEquals("霜山这个厨房收纳最常见顾虑是什么", prompt);
    }

    private RealizationPlan realizationPlan(String surfaceIntent,
                                            List<String> mustSemantics,
                                            List<String> softHints) {
        return new RealizationPlan(
                new SimulationIntentProfile(
                        surfaceIntent,
                        "confirmation_seeking",
                        "single_turn_search_like",
                        "explicit_brand_reference",
                        "human_colloquial_question",
                        "low"
                ),
                new RealizationConstraints(
                        ProbeExposurePolicy.BRAND_VISIBLE,
                        mustSemantics,
                        softHints,
                        "anchor_required",
                        "short_single_turn",
                        "natural_question_only",
                        "orthogonal_seek_clear_answer_single_turn_search_like"
                ),
                "fallback-test-seed",
                "fallback-test-signature"
        );
    }
}
