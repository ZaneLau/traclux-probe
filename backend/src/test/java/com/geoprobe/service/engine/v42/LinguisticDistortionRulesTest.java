package com.geoprobe.service.engine.v42;

import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PromptInstructionRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinguisticDistortionRulesTest {

    @Test
    void shouldExposeAllSeventeenPromptInstructions() {
        List<PromptInstructionRule> rules = LinguisticDistortionRules.allRules();

        assertEquals(17, rules.size());
        assertEquals(17, rules.stream().map(PromptInstructionRule::toPromptInstruction).distinct().count());
        assertTrue(rules.stream()
                .map(PromptInstructionRule::toPromptInstruction)
                .allMatch(text -> text.startsWith("【") && !text.isBlank()));
        assertFalse(LinguisticDistortionRules.IllocutionaryForce.DIRECTIVE_DOMINANT
                .toPromptInstruction().contains("维度"));
    }

    @Test
    void shouldStrengthenWeakEffectsIntoObservableConstraints() {
        assertTrue(LinguisticDistortionRules.CognitiveDistortion.CATASTROPHIZING
                .toPromptInstruction().contains("必须"));
        assertTrue(LinguisticDistortionRules.CognitiveDistortion.CATASTROPHIZING
                .toPromptInstruction().contains("个人后果"));
        assertTrue(LinguisticDistortionRules.IllocutionaryForce.DIRECTIVE_DOMINANT
                .toPromptInstruction().contains("必须"));
        assertTrue(LinguisticDistortionRules.IllocutionaryForce.EXPRESSIVE_DOMINANT
                .toPromptInstruction().contains("句首必须"));
        assertTrue(LinguisticDistortionRules.IllocutionaryForce.EXPRESSIVE_DOMINANT
                .toPromptInstruction().contains("第一人称"));
        assertTrue(LinguisticDistortionRules.PragmaticIndirectness.STATE_CONDITIONED
                .toPromptInstruction().contains("开头先交代"));
    }
}
