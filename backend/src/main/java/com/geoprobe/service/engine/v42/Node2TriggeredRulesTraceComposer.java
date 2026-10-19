package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.CognitiveDistortion;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.IllocutionaryForce;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PragmaticIndirectness;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.PromptInstructionRule;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.RegisterVariation;
import com.geoprobe.service.engine.v42.LinguisticDistortionRules.SyntacticOffloading;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator.MappingResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class Node2TriggeredRulesTraceComposer {

    private final ThreeLayerMappingEvaluator threeLayerMappingEvaluator;

    public Node2TriggeredRulesTraceComposer(ThreeLayerMappingEvaluator threeLayerMappingEvaluator) {
        this.threeLayerMappingEvaluator = Objects.requireNonNull(threeLayerMappingEvaluator, "threeLayerMappingEvaluator 不能为空");
    }

    Map<String, Object> composeOrthogonal(Map<String, String> abstractTags,
                                          MappingResult mappingResult,
                                          boolean renderFallbackUsed,
                                          String renderReason) {
        Objects.requireNonNull(abstractTags, "abstractTags 不能为空");
        Objects.requireNonNull(mappingResult, "mappingResult 不能为空");
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        List<String> must = new ArrayList<>();
        List<String> should = new ArrayList<>();
        int roll = threeLayerMappingEvaluator.deterministicRoll(abstractTags);

        if (containsAll(mappingResult.mustRules(), SyntacticOffloading.HEURISTIC_KEYWORD_AGG, SyntacticOffloading.FRAGMENTED_IMPERATIVES)) {
            must.add(Node2RuleCatalog.RULE_M1);
            details.put(Node2RuleCatalog.RULE_M1, Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_M1,
                    "D4 = severely_depleted",
                    List.of("HEURISTIC_KEYWORD_AGG", "FRAGMENTED_IMPERATIVES"),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
        }
        if (containsAll(mappingResult.mustRules(), CognitiveDistortion.CATASTROPHIZING, IllocutionaryForce.DIRECTIVE_DOMINANT)) {
            must.add(Node2RuleCatalog.RULE_M2);
            details.put(Node2RuleCatalog.RULE_M2, Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_M2,
                    "D5 = acute_emergency ∧ D1 = initiation",
                    List.of("CATASTROPHIZING", "DIRECTIVE_DOMINANT"),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
        }
        if (mappingResult.mustRules().contains(IllocutionaryForce.REPRESENTATIVE_ASSERTIVE)) {
            must.add(Node2RuleCatalog.RULE_M3);
            LinkedHashMap<String, Object> detail = new LinkedHashMap<>(Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_M3,
                    "D1 = verification ∧ D4 = optimal",
                    List.of("REPRESENTATIVE_ASSERTIVE"),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
            detail.put("brandInjectionAllowed", mappingResult.brandInjectionAllowed());
            details.put(Node2RuleCatalog.RULE_M3, Map.copyOf(detail));
        }
        if (mappingResult.mustRules().contains(PragmaticIndirectness.STATE_CONDITIONED)) {
            must.add(Node2RuleCatalog.RULE_M4);
            details.put(Node2RuleCatalog.RULE_M4, Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_M4,
                    "D4 = moderate_strain ∧ D6 = dialogic",
                    List.of("STATE_CONDITIONED"),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
        }

        if ("exploration".equals(abstractTags.get("D1")) && "moderate_strain".equals(abstractTags.get("D4"))) {
            should.add(Node2RuleCatalog.RULE_S1);
            LinkedHashMap<String, Object> detail = new LinkedHashMap<>(Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_S1,
                    "D1 = exploration ∧ D4 = moderate_strain",
                    ruleNames(mappingResult.shouldRules().isEmpty()
                            ? List.of(RegisterVariation.ACADEMIC_HEDGING)
                            : mappingResult.shouldRules()),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
            detail.put("probability", 0.70d);
            detail.put("roll", roll);
            detail.put("fired", !mappingResult.fallback());
            details.put(Node2RuleCatalog.RULE_S1, Map.copyOf(detail));
        }
        if ("formulation".equals(abstractTags.get("D1")) && "optimal".equals(abstractTags.get("D4"))) {
            should.add(Node2RuleCatalog.RULE_S2);
            LinkedHashMap<String, Object> detail = new LinkedHashMap<>(Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_S2,
                    "D1 = formulation ∧ D4 = optimal",
                    ruleNames(mappingResult.shouldRules()),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
            detail.put("probability", 0.80d);
            detail.put("roll", roll);
            detail.put("fired", !mappingResult.fallback());
            details.put(Node2RuleCatalog.RULE_S2, Map.copyOf(detail));
        }
        if ("initiation".equals(abstractTags.get("D1")) && "reflective".equals(abstractTags.get("D5"))) {
            should.add(Node2RuleCatalog.RULE_S3);
            LinkedHashMap<String, Object> detail = new LinkedHashMap<>(Node2RuleCatalog.boundaryDetail(
                    Node2RuleCatalog.RULE_S3,
                    "D1 = initiation ∧ D5 = reflective",
                    ruleNames(mappingResult.shouldRules()),
                    Node2RuleCatalog.BOUNDARY_TYPE_STATE_TRANSLATION
            ));
            detail.put("probability", 0.60d);
            detail.put("roll", roll);
            detail.put("fired", !mappingResult.fallback());
            details.put(Node2RuleCatalog.RULE_S3, Map.copyOf(detail));
        }

        payload.put("ruleCatalogVersion", Node2RuleCatalog.VERSION);
        payload.put("must", List.copyOf(must));
        payload.put("should", List.copyOf(should));
        payload.put("fallback", mappingResult.fallback());
        payload.put("mappingApplied", true);
        payload.put("renderFallbackUsed", renderFallbackUsed);
        payload.put("renderReason", renderReason == null ? "" : renderReason);
        payload.put("promptInstructions", List.copyOf(mappingResult.promptInstructions()));
        payload.put("details", details);
        return Map.copyOf(payload);
    }

    Map<String, Object> composeTemplate(BucketType bucketType,
                                        boolean renderFallbackUsed,
                                        String renderReason) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("must", List.of());
        payload.put("should", List.of());
        payload.put("fallback", false);
        payload.put("mappingApplied", false);
        payload.put("renderFallbackUsed", renderFallbackUsed);
        payload.put("renderReason", renderReason == null ? "" : renderReason);
        payload.put("ruleCatalogVersion", Node2RuleCatalog.VERSION);
        payload.put("details", Map.of(
                "bucketType", bucketType == null ? "UNKNOWN" : bucketType.name(),
                "reason", "template_bucket",
                "ruleOrigin", Node2RuleCatalog.ORIGIN_BOUNDARY,
                "boundaryType", Node2RuleCatalog.BOUNDARY_TYPE_TEMPLATE_BUCKET
        ));
        return Map.copyOf(payload);
    }

    private boolean containsAll(List<PromptInstructionRule> rules, PromptInstructionRule first, PromptInstructionRule second) {
        return rules.contains(first) && rules.contains(second);
    }

    private List<String> ruleNames(List<PromptInstructionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(rules.size());
        for (PromptInstructionRule rule : rules) {
            if (rule instanceof Enum<?> enumRule) {
                names.add(enumRule.name());
            } else {
                names.add(rule.toPromptInstruction());
            }
        }
        return List.copyOf(names);
    }
}
