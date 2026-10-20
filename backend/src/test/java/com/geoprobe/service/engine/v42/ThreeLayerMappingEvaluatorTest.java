package com.geoprobe.service.engine.v42;

import com.geoprobe.service.engine.v42.LinguisticDistortionRules.IllocutionaryForce;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PragmaticIndirectness;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.RegisterVariation;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.SyntacticOffloading;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.CognitiveDistortion;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator.MappingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeLayerMappingEvaluatorTest {

    private ThreeLayerMappingEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ThreeLayerMappingEvaluator();
    }

    @Test
    void shouldMapMustRuleOneToTwoSyntacticRules() {
        MappingResult result = evaluator.evaluate(tags(Map.of("D4", "severely_depleted")));

        assertEquals(2, result.mustRules().size());
        assertEquals(SyntacticOffloading.HEURISTIC_KEYWORD_AGG, result.mustRules().get(0));
        assertEquals(SyntacticOffloading.FRAGMENTED_IMPERATIVES, result.mustRules().get(1));
        assertTrue(result.shouldRules().isEmpty());
        assertFalse(result.fallback());
    }

    @Test
    void shouldStackMultipleMustRulesAndShortCircuitShould() {
        MappingResult result = evaluator.evaluate(tags(Map.of(
                "D1", "initiation",
                "D4", "moderate_strain",
                "D5", "acute_emergency",
                "D6", "dialogic"
        )));

        assertEquals(3, result.mustRules().size());
        assertEquals(CognitiveDistortion.CATASTROPHIZING, result.mustRules().get(0));
        assertEquals(IllocutionaryForce.DIRECTIVE_DOMINANT, result.mustRules().get(1));
        assertEquals(PragmaticIndirectness.STATE_CONDITIONED, result.mustRules().get(2));
        assertTrue(result.shouldRules().isEmpty());
        assertFalse(result.fallback());
    }

    @Test
    void shouldTriggerM4OnVerificationAndOptimal() {
        MappingResult result = evaluator.evaluate(tags(Map.of(
                "D1", "verification",
                "D4", "optimal"
        )));

        assertEquals(1, result.mustRules().size());
        assertEquals(IllocutionaryForce.REPRESENTATIVE_ASSERTIVE, result.mustRules().getFirst());
        assertTrue(result.brandInjectionAllowed());
        assertFalse(result.fallback());
    }

    @Test
    void shouldEvaluateExplorationShouldBranchDeterministically() {
        Map<String, String> abstractTags = tags(Map.of(
                "D1", "exploration",
                "D4", "moderate_strain"
        ));

        MappingResult result = evaluator.evaluate(abstractTags);

        assertTrue(result.mustRules().isEmpty());
        assertFalse(result.fallback());
        if (evaluator.deterministicRoll(abstractTags) < 70) {
            assertEquals(IllocutionaryForce.EXPRESSIVE_DOMINANT, result.shouldRules().getFirst());
        } else {
            assertEquals(RegisterVariation.ACADEMIC_HEDGING, result.shouldRules().getFirst());
        }
    }

    @Test
    void shouldMapFormulationShouldRuleWhenThresholdHits() {
        Map<String, String> abstractTags = findTagsForRoll(
                roll -> roll < 80,
                Map.of("D1", "formulation", "D4", "optimal")
        );

        MappingResult result = evaluator.evaluate(abstractTags);

        assertEquals(RegisterVariation.INFORMATIONAL_ELABORATION, result.shouldRules().getFirst());
        assertFalse(result.fallback());
    }

    @Test
    void shouldFallbackWhenFormulationShouldThresholdMisses() {
        Map<String, String> abstractTags = findTagsForRoll(
                roll -> roll >= 80,
                Map.of("D1", "formulation", "D4", "optimal")
        );

        MappingResult result = evaluator.evaluate(abstractTags);

        assertTrue(result.shouldRules().isEmpty());
        assertTrue(result.fallback());
    }

    @Test
    void shouldMapReflectiveShouldRuleWhenThresholdHits() {
        Map<String, String> abstractTags = findTagsForRoll(
                roll -> roll < 60,
                Map.of("D1", "initiation", "D5", "reflective")
        );

        MappingResult result = evaluator.evaluate(abstractTags);

        assertEquals(PragmaticIndirectness.ABILITY_CONDITIONED, result.shouldRules().getFirst());
        assertFalse(result.fallback());
    }

    @Test
    void shouldFallbackWhenNoMustOrShouldConditionMatches() {
        MappingResult result = evaluator.evaluate(tags(Map.of()));

        assertTrue(result.mustRules().isEmpty());
        assertTrue(result.shouldRules().isEmpty());
        assertTrue(result.fallback());
    }

    private Map<String, String> findTagsForRoll(IntPredicate predicate, Map<String, String> overrides) {
        for (String d2 : CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL) {
            for (String d3 : CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE) {
                for (String d5 : CssmHiAbstractStates.D5_TIME_CONSTRAINT) {
                    for (String d6 : CssmHiAbstractStates.D6_INTERACTION_MODALITY) {
                        Map<String, String> tags = tags(Map.of(
                                "D2", d2,
                                "D3", d3,
                                "D5", d5,
                                "D6", d6
                        ));
                        tags.putAll(overrides);
                        if (predicate.test(evaluator.deterministicRoll(tags))) {
                            return tags;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("未找到满足条件的 deterministic roll 测试样本");
    }

    private Map<String, String> tags(Map<String, String> overrides) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "exploration");
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", "optimal");
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        tags.putAll(overrides);
        return tags;
    }
}
