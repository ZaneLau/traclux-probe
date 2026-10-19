package com.geoprobe.service.engine.v42;

import com.geoprobe.service.engine.v42.LinguisticDistortionRules.CognitiveDistortion;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.IllocutionaryForce;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PragmaticIndirectness;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PromptInstructionRule;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.RegisterVariation;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.SyntacticOffloading;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component("v42ThreeLayerMappingEvaluator")
public class ThreeLayerMappingEvaluator {

    public MappingResult evaluate(Map<String, String> abstractTags) {
        Map<String, String> normalizedTags = normalizeTags(abstractTags);
        LinkedHashSet<PromptInstructionRule> mustRules = evaluateMustRules(normalizedTags);
        boolean m4Triggered = isM4Triggered(normalizedTags);
        if (!mustRules.isEmpty()) {
            return new MappingResult(
                    List.copyOf(mustRules),
                    List.of(),
                    toPromptInstructions(mustRules),
                    false,
                    m4Triggered
            );
        }

        List<PromptInstructionRule> shouldRules = evaluateShouldRules(normalizedTags);
        boolean fallback = shouldRules.isEmpty();
        return new MappingResult(
                List.of(),
                List.copyOf(shouldRules),
                fallback ? List.of() : toPromptInstructions(shouldRules),
                fallback,
                false
        );
    }

    int deterministicRoll(Map<String, String> abstractTags) {
        Map<String, String> normalizedTags = normalizeTags(abstractTags);
        StringBuilder signature = new StringBuilder();
        for (String dimensionId : CssmHiAbstractStates.DIMENSION_IDS) {
            if (!signature.isEmpty()) {
                signature.append('|');
            }
            signature.append(dimensionId).append('=').append(normalizedTags.get(dimensionId));
        }
        return Math.floorMod(signature.toString().hashCode(), 100);
    }

    private LinkedHashSet<PromptInstructionRule> evaluateMustRules(Map<String, String> abstractTags) {
        LinkedHashSet<PromptInstructionRule> rules = new LinkedHashSet<>();

        if ("severely_depleted".equals(abstractTags.get("D4"))) {
            rules.add(SyntacticOffloading.HEURISTIC_KEYWORD_AGG);
            rules.add(SyntacticOffloading.FRAGMENTED_IMPERATIVES);
        }
        if ("acute_emergency".equals(abstractTags.get("D5")) && "initiation".equals(abstractTags.get("D1"))) {
            rules.add(CognitiveDistortion.CATASTROPHIZING);
            rules.add(IllocutionaryForce.DIRECTIVE_DOMINANT);
        }
        if (isM4Triggered(abstractTags)) {
            rules.add(IllocutionaryForce.REPRESENTATIVE_ASSERTIVE);
        }
        if ("moderate_strain".equals(abstractTags.get("D4")) && "dialogic".equals(abstractTags.get("D6"))) {
            rules.add(PragmaticIndirectness.STATE_CONDITIONED);
        }
        return rules;
    }

    private List<PromptInstructionRule> evaluateShouldRules(Map<String, String> abstractTags) {
        int roll = deterministicRoll(abstractTags);
        if ("exploration".equals(abstractTags.get("D1")) && "moderate_strain".equals(abstractTags.get("D4"))) {
            return roll < 70
                    ? List.of(IllocutionaryForce.EXPRESSIVE_DOMINANT)
                    : List.of(RegisterVariation.ACADEMIC_HEDGING);
        }
        if ("formulation".equals(abstractTags.get("D1")) && "optimal".equals(abstractTags.get("D4"))) {
            return roll < 80
                    ? List.of(RegisterVariation.INFORMATIONAL_ELABORATION)
                    : List.of();
        }
        if ("initiation".equals(abstractTags.get("D1")) && "reflective".equals(abstractTags.get("D5"))) {
            return roll < 60
                    ? List.of(PragmaticIndirectness.ABILITY_CONDITIONED)
                    : List.of();
        }
        return List.of();
    }

    private List<String> toPromptInstructions(Iterable<PromptInstructionRule> rules) {
        List<String> instructions = new ArrayList<>();
        for (PromptInstructionRule rule : rules) {
            instructions.add(rule.toPromptInstruction());
        }
        return List.copyOf(instructions);
    }

    private boolean isM4Triggered(Map<String, String> abstractTags) {
        return "verification".equals(abstractTags.get("D1")) && "optimal".equals(abstractTags.get("D4"));
    }

    private Map<String, String> normalizeTags(Map<String, String> abstractTags) {
        if (abstractTags == null) {
            throw new IllegalArgumentException("abstractTags 不能为空");
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String dimensionId : CssmHiAbstractStates.DIMENSION_IDS) {
            String value = abstractTags.get(dimensionId);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("abstractTags 缺少维度: " + dimensionId);
            }
            normalized.put(dimensionId, value.trim());
        }
        return normalized;
    }

    public record MappingResult(
            List<PromptInstructionRule> mustRules,
            List<PromptInstructionRule> shouldRules,
            List<String> promptInstructions,
            boolean fallback,
            boolean brandInjectionAllowed
    ) {

        public MappingResult {
            mustRules = mustRules == null ? List.of() : List.copyOf(mustRules);
            shouldRules = shouldRules == null ? List.of() : List.copyOf(shouldRules);
            promptInstructions = promptInstructions == null ? List.of() : List.copyOf(promptInstructions);
        }
    }
}
