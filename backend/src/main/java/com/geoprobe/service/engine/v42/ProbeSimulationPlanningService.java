package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationConstraints;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.SimulationIntentProfile;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service("v42ProbeSimulationPlanningService")
public class ProbeSimulationPlanningService {

    public RealizationPlan plan(BucketType bucketType,
                                Map<String, String> abstractTags,
                                Map<String, String> translatedTags,
                                String templateText) {
        return plan(bucketType, abstractTags, translatedTags, templateText, null, null);
    }

    public RealizationPlan plan(BucketType bucketType,
                                Map<String, String> abstractTags,
                                Map<String, String> translatedTags,
                                String templateText,
                                String templateFamily) {
        return plan(bucketType, abstractTags, translatedTags, templateText, templateFamily, null);
    }

    public RealizationPlan plan(BucketType bucketType,
                                Map<String, String> abstractTags,
                                Map<String, String> translatedTags,
                                String templateText,
                                String templateFamily,
                                EngineTaskContextPackage contextPackage) {
        return plan(bucketType, abstractTags, translatedTags, templateText, templateFamily, contextPackage, null);
    }

    public RealizationPlan plan(BucketType bucketType,
                                Map<String, String> abstractTags,
                                Map<String, String> translatedTags,
                                String templateText,
                                String templateFamily,
                                EngineTaskContextPackage contextPackage,
                                LightConeV43TopicStateProjectionSupport.TopicStateProjection assignedTopicProjection) {
        Objects.requireNonNull(bucketType, "bucketType 不能为空");
        Objects.requireNonNull(abstractTags, "abstractTags 不能为空");
        Objects.requireNonNull(translatedTags, "translatedTags 不能为空");

        ProbeExposurePolicy exposurePolicy = resolveExposurePolicy(bucketType, abstractTags, templateText);
        SimulationIntentProfile intentProfile = buildIntentProfile(bucketType, abstractTags, exposurePolicy, templateText, templateFamily);
        RealizationConstraints constraints = buildConstraints(
                bucketType,
                abstractTags,
                translatedTags,
                exposurePolicy,
                templateFamily,
                intentProfile,
                contextPackage,
                assignedTopicProjection
        );

        String realizationSeed = "%s|%s|%s|%s|%s".formatted(
                bucketType.name(),
                normalizeFamily(templateFamily),
                intentProfile.surfaceIntent(),
                intentProfile.interactionPosture(),
                intentProfile.conversationMode()
        );
        String debugSignature = "%s|%s|%s|%s|%s|%s".formatted(
                bucketType.name(),
                normalizeFamily(templateFamily),
                abstractTags.getOrDefault("D1", "na"),
                abstractTags.getOrDefault("D2", "na"),
                abstractTags.getOrDefault("D3", "na"),
                exposurePolicy.contractValue()
        );

        return new RealizationPlan(intentProfile, constraints, realizationSeed, debugSignature);
    }

    private SimulationIntentProfile buildIntentProfile(BucketType bucketType,
                                                       Map<String, String> abstractTags,
                                                       ProbeExposurePolicy exposurePolicy,
                                                       String templateText,
                                                       String templateFamily) {
        String brandFraming = exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                ? "explicit_brand_reference"
                : "hidden_brand_discovery";
        return switch (bucketType) {
            case ORTHOGONAL -> new SimulationIntentProfile(
                    surfaceIntent(abstractTags.get("D2")),
                    interactionPosture(abstractTags),
                    conversationMode(abstractTags.get("D6")),
                    brandFraming,
                    "human_colloquial_question",
                    languageDegradationLevel(abstractTags.get("D4"), abstractTags.get("D5"))
            );
            case PARETO -> buildParetoIntentProfile(brandFraming, templateFamily);
            case ADVERSARIAL -> buildAdversarialIntentProfile(brandFraming, templateText, templateFamily);
        };
    }

    private RealizationConstraints buildConstraints(BucketType bucketType,
                                                    Map<String, String> abstractTags,
                                                    Map<String, String> translatedTags,
                                                    ProbeExposurePolicy exposurePolicy,
                                                    String templateFamily,
                                                    SimulationIntentProfile intentProfile,
                                                    EngineTaskContextPackage contextPackage,
                                                    LightConeV43TopicStateProjectionSupport.TopicStateProjection assignedTopicProjection) {
        List<String> mustExpressSemantics = new ArrayList<>();
        List<String> softSemanticHints = new ArrayList<>();

        if (abstractTags.containsKey("D2")) {
            mustExpressSemantics.add("D2:" + abstractTags.get("D2"));
        }
        if (abstractTags.containsKey("D3")) {
            mustExpressSemantics.add("D3:" + abstractTags.get("D3"));
        }
        appendHint(softSemanticHints, "D1", abstractTags, translatedTags);
        appendHint(softSemanticHints, "D4", abstractTags, translatedTags);
        appendHint(softSemanticHints, "D5", abstractTags, translatedTags);
        appendHint(softSemanticHints, "D6", abstractTags, translatedTags);
        softSemanticHints.addAll(contextSoftHints(contextPackage, exposurePolicy));
        softSemanticHints.addAll(topicProjectionHints(assignedTopicProjection, exposurePolicy, contextPackage));

        return new RealizationConstraints(
                exposurePolicy,
                mustExpressSemantics,
                softSemanticHints,
                bucketType == BucketType.ORTHOGONAL ? "anchor_required" : "anchor_preferred",
                bucketType == BucketType.ORTHOGONAL ? "short_single_turn" : "short_or_medium_single_turn",
                "natural_question_only",
                switch (bucketType) {
                    case ORTHOGONAL -> orthogonalFallbackFamily(intentProfile);
                    case PARETO -> normalizeFamily(templateFamily).startsWith("pareto_")
                            ? normalizeFamily(templateFamily)
                            : "pareto_template_humanized";
                    case ADVERSARIAL -> normalizeFamily(templateFamily).startsWith("adversarial_")
                            ? normalizeFamily(templateFamily)
                            : "adversarial_template_humanized";
                }
        );
    }

    private List<String> contextSoftHints(EngineTaskContextPackage contextPackage,
                                          ProbeExposurePolicy exposurePolicy) {
        if (contextPackage == null) {
            return List.of();
        }
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        addContextHints(hints,
                "SCENE",
                contextPackage.slotValuesForUse(
                        MinusOneContextSlots.USAGE_SCENARIO,
                        MinusOneContextUses.NODE2_REALIZATION,
                        2
                ));
        addContextHints(hints,
                "DECISION",
                contextPackage.slotValuesForUse(
                        MinusOneContextSlots.DECISION_FACTOR,
                        MinusOneContextUses.NODE2_REALIZATION,
                        2
                ));
        List<String> descriptiveCues = contextPackage.slotValuesForUse(
                MinusOneContextSlots.DESCRIPTIVE_CUE,
                MinusOneContextUses.NODE2_REALIZATION,
                2
        );
        if (exposurePolicy == ProbeExposurePolicy.BRAND_HIDDEN) {
            descriptiveCues = descriptiveCues.stream()
                    .filter(value -> !containsLiteralBrand(value, contextPackage.brandName()))
                    .toList();
        }
        addContextHints(hints, "DESCRIBE", descriptiveCues);
        addContextHints(hints,
                "COMPARE",
                contextPackage.slotValuesForUse(
                        MinusOneContextSlots.COMPARISON_TARGET,
                        MinusOneContextUses.NODE2_REALIZATION,
                        2
                ));
        addContextHints(hints,
                "CHANNEL",
                contextPackage.slotValuesForUse(
                        MinusOneContextSlots.CHANNEL_CUE,
                        MinusOneContextUses.NODE2_REALIZATION,
                        1
                ));
        List<String> memoryCues = contextPackage.slotValuesForUse(
                MinusOneContextSlots.MEMORY_CUE,
                MinusOneContextUses.NODE2_REALIZATION,
                1
        );
        if (exposurePolicy == ProbeExposurePolicy.BRAND_HIDDEN) {
            memoryCues = memoryCues.stream()
                    .filter(value -> !containsLiteralBrand(value, contextPackage.brandName()))
                    .toList();
        }
        addContextHints(hints, "MEMORY", memoryCues);
        return List.copyOf(hints);
    }

    private List<String> topicProjectionHints(LightConeV43TopicStateProjectionSupport.TopicStateProjection assignedTopicProjection,
                                              ProbeExposurePolicy exposurePolicy,
                                              EngineTaskContextPackage contextPackage) {
        if (assignedTopicProjection == null) {
            return List.of();
        }
        String canonicalTopicLabel = normalizeTopicHintValue(assignedTopicProjection.canonicalTopicLabel());
        LinkedHashSet<String> evidenceHints = new LinkedHashSet<>();
        LinkedHashSet<String> scenarioHints = new LinkedHashSet<>();
        LinkedHashSet<String> decisionHints = new LinkedHashSet<>();
        LinkedHashSet<String> describeHints = new LinkedHashSet<>();
        LinkedHashSet<String> compareHints = new LinkedHashSet<>();
        LinkedHashSet<String> channelHints = new LinkedHashSet<>();
        LinkedHashSet<String> memoryHints = new LinkedHashSet<>();
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (shouldIncludeTopicLabelHint(assignedTopicProjection, contextPackage)) {
            hints.add("TOPIC:" + assignedTopicProjection.canonicalTopicLabel().trim());
        }
        for (var signal : assignedTopicProjection.topicGroundedSignals()) {
            if (signal == null || !signal.isUsedFor(MinusOneContextUses.NODE2_REALIZATION)) {
                continue;
            }
            String value = signal.value();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (exposurePolicy == ProbeExposurePolicy.BRAND_HIDDEN
                    && StringUtils.hasText(contextPackage == null ? null : contextPackage.brandName())
                    && containsLiteralBrand(value, contextPackage.brandName())) {
                continue;
            }
            boolean compilerDerivedTopicHint = "topic_projection_compiler".equals(signal.source())
                    && "topic_contract".equals(signal.evidence());
            switch (signal.slot()) {
                case MinusOneContextSlots.USAGE_SCENARIO -> scenarioHints.add("SCENE:" + value.trim());
                case MinusOneContextSlots.DECISION_FACTOR -> {
                    if (shouldSkipCompilerDerivedTopicHint(
                            compilerDerivedTopicHint,
                            value,
                            canonicalTopicLabel,
                            decisionHints
                    )) {
                        continue;
                    }
                    decisionHints.add("DECISION:" + value.trim());
                }
                case MinusOneContextSlots.DESCRIPTIVE_CUE -> {
                    if (shouldSkipCompilerDerivedTopicHint(
                            compilerDerivedTopicHint,
                            value,
                            canonicalTopicLabel,
                            describeHints
                    )) {
                        continue;
                    }
                    describeHints.add("DESCRIBE:" + value.trim());
                }
                case MinusOneContextSlots.COMPARISON_TARGET -> compareHints.add("COMPARE:" + value.trim());
                case MinusOneContextSlots.CHANNEL_CUE -> channelHints.add("CHANNEL:" + value.trim());
                case MinusOneContextSlots.MEMORY_CUE -> memoryHints.add("MEMORY:" + value.trim());
                case MinusOneContextSlots.BRAND_EVIDENCE -> evidenceHints.add("EVIDENCE:" + value.trim());
                default -> {
                }
            }
        }
        hints.addAll(evidenceHints);
        hints.addAll(scenarioHints);
        hints.addAll(decisionHints);
        hints.addAll(describeHints);
        hints.addAll(compareHints);
        hints.addAll(channelHints);
        hints.addAll(memoryHints);
        return List.copyOf(hints);
    }

    private boolean shouldIncludeTopicLabelHint(
            LightConeV43TopicStateProjectionSupport.TopicStateProjection assignedTopicProjection,
            EngineTaskContextPackage contextPackage) {
        return assignedTopicProjection != null
                && StringUtils.hasText(assignedTopicProjection.canonicalTopicLabel());
    }

    private boolean shouldSkipCompilerDerivedTopicHint(boolean compilerDerivedTopicHint,
                                                       String rawValue,
                                                       String canonicalTopicLabel,
                                                       LinkedHashSet<String> existingHints) {
        if (!compilerDerivedTopicHint || !StringUtils.hasText(rawValue)) {
            return false;
        }
        String normalizedValue = normalizeTopicHintValue(rawValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return true;
        }
        if (normalizedValue.equals(canonicalTopicLabel)) {
            return true;
        }
        if (looksLikeExplanatoryTopicSentence(rawValue) || normalizedValue.length() > 16) {
            return true;
        }
        if (!existingHints.isEmpty()) {
            return true;
        }
        return false;
    }

    private String normalizeTopicHintValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\s、，。！？,.!?/\\\\_-]", "").trim();
    }

    private boolean looksLikeExplanatoryTopicSentence(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.contains("、")
                || trimmed.contains("，")
                || trimmed.contains("。")
                || trimmed.startsWith("聚焦")
                || trimmed.startsWith("处理")
                || trimmed.startsWith("围绕")
                || trimmed.startsWith("关注")
                || trimmed.startsWith("观察")
                || trimmed.startsWith("识别")
                || trimmed.startsWith("看模型");
    }

    private boolean containsTopicConcreteCue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.contains("/")
                || trimmed.contains("／")
                || trimmed.contains("场景")
                || trimmed.contains("适配")
                || trimmed.contains("安装")
                || trimmed.contains("售后")
                || trimmed.contains("材质")
                || trimmed.contains("耐用")
                || trimmed.contains("连接")
                || trimmed.contains("稳定")
                || trimmed.contains("防水")
                || trimmed.contains("防滑")
                || trimmed.contains("成分")
                || trimmed.contains("功效")
                || trimmed.contains("配置")
                || trimmed.contains("续航")
                || trimmed.contains("容量")
                || trimmed.contains("空间");
    }

    private void addContextHints(LinkedHashSet<String> hints,
                                 String prefix,
                                 List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                hints.add(prefix + ":" + value.trim());
            }
        }
    }

    private boolean containsLiteralBrand(String candidate, String brandName) {
        return StringUtils.hasText(candidate)
                && StringUtils.hasText(brandName)
                && candidate.contains(brandName);
    }

    private SimulationIntentProfile buildParetoIntentProfile(String brandFraming, String templateFamily) {
        return switch (normalizeFamily(templateFamily)) {
            case "pareto_recommendation_shortlist" -> new SimulationIntentProfile(
                    "high_frequency_decision_query",
                    "comparative_browsing",
                    "search_like_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "pareto_brand_comparison" -> new SimulationIntentProfile(
                    "seek_clear_answer",
                    "comparative_browsing",
                    "search_like_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "pareto_constraint_filtering" -> new SimulationIntentProfile(
                    "ask_for_actionable_steps",
                    "exploratory_browsing",
                    "constraint_driven_query",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "pareto_trust_signal" -> new SimulationIntentProfile(
                    "seek_clear_answer",
                    "deliberate_structuring",
                    "constraint_driven_query",
                    brandFraming,
                    "human_colloquial_question",
                    "low"
            );
            case "pareto_scenario_match" -> new SimulationIntentProfile(
                    "browse_for_options",
                    "exploratory_browsing",
                    "chat_message",
                    brandFraming,
                    "human_colloquial_question",
                    "low"
            );
            case "pareto_search_shortcut" -> new SimulationIntentProfile(
                    "seek_clear_answer",
                    "stressed_urgent",
                    "search_like_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            default -> new SimulationIntentProfile(
                    "high_frequency_decision_query",
                    "comparative_browsing",
                    "search_like_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
        };
    }

    private SimulationIntentProfile buildAdversarialIntentProfile(String brandFraming,
                                                                  String templateText,
                                                                  String templateFamily) {
        return switch (normalizeFamily(templateFamily)) {
            case "adversarial_rumor_challenge" -> new SimulationIntentProfile(
                    "brand_stress_test",
                    "skeptical_challenging",
                    "challenge_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "adversarial_anti_hype" -> new SimulationIntentProfile(
                    "brand_stress_test",
                    "skeptical_challenging",
                    "chat_message",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "adversarial_substitute_pressure" -> new SimulationIntentProfile(
                    "seek_clear_answer",
                    "skeptical_challenging",
                    "constraint_driven_query",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "adversarial_forced_comparison" -> new SimulationIntentProfile(
                    "brand_stress_test",
                    "comparative_browsing",
                    "challenge_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            case "adversarial_brand_exclusion" -> new SimulationIntentProfile(
                    "brand_exclusion_test",
                    "skeptical_challenging",
                    "challenge_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
            default -> new SimulationIntentProfile(
                    templateText != null && templateText.contains("别提") ? "brand_exclusion_test" : "brand_stress_test",
                    "skeptical_challenging",
                    "challenge_prompt",
                    brandFraming,
                    "human_colloquial_question",
                    "medium"
            );
        };
    }

    private void appendHint(List<String> hints,
                            String dimensionId,
                            Map<String, String> abstractTags,
                            Map<String, String> translatedTags) {
        if (abstractTags.containsKey(dimensionId) && translatedTags.containsKey(dimensionId)) {
            hints.add(dimensionId + ":" + abstractTags.get(dimensionId) + "=" + translatedTags.get(dimensionId));
        }
    }

    private ProbeExposurePolicy resolveExposurePolicy(BucketType bucketType,
                                                      Map<String, String> abstractTags,
                                                      String templateText) {
        if (bucketType == BucketType.ORTHOGONAL) {
            return ProbeExposurePolicy.classifyOrthogonal(abstractTags);
        }
        return templateText != null && templateText.contains("{brandName}")
                ? ProbeExposurePolicy.BRAND_VISIBLE
                : ProbeExposurePolicy.BRAND_HIDDEN;
    }

    private String surfaceIntent(String d2Code) {
        if (d2Code == null) {
            return "generic_decision_query";
        }
        return switch (d2Code) {
            case "informational_undirected" -> "browse_for_options";
            case "informational_directed" -> "seek_clear_answer";
            case "instructional_active" -> "ask_for_actionable_steps";
            case "resource_acquisition" -> "confirm_and_buy";
            default -> "generic_decision_query";
        };
    }

    private String interactionPosture(Map<String, String> abstractTags) {
        String d1 = abstractTags.get("D1");
        String d4 = abstractTags.get("D4");
        String d5 = abstractTags.get("D5");
        if ("verification".equals(d1)) {
            return "confirmation_seeking";
        }
        if ("acute_emergency".equals(d5) || "severely_depleted".equals(d4)) {
            return "stressed_urgent";
        }
        if ("initiation".equals(d1)) {
            return "confused_entry";
        }
        if ("formulation".equals(d1)) {
            return "deliberate_structuring";
        }
        return "exploratory_browsing";
    }

    private String conversationMode(String d6Code) {
        if (d6Code == null) {
            return "single_turn_search_like";
        }
        return switch (d6Code) {
            case "recognition" -> "single_turn_search_like";
            case "specification" -> "constraint_driven_query";
            case "dialogic" -> "back_and_forth_consultation";
            default -> "single_turn_search_like";
        };
    }

    private String languageDegradationLevel(String d4Code, String d5Code) {
        if ("severely_depleted".equals(d4Code) || "acute_emergency".equals(d5Code)) {
            return "high";
        }
        if ("moderate_strain".equals(d4Code) || "anticipatory_constraint".equals(d5Code)) {
            return "medium";
        }
        return "low";
    }

    private String orthogonalFallbackFamily(SimulationIntentProfile intentProfile) {
        if (intentProfile == null) {
            return "orthogonal_humanized";
        }
        return "orthogonal_%s_%s".formatted(
                intentProfile.surfaceIntent(),
                intentProfile.conversationMode()
        );
    }

    private String normalizeFamily(String templateFamily) {
        if (templateFamily == null || templateFamily.isBlank()) {
            return "template_default";
        }
        return templateFamily.trim();
    }
}
