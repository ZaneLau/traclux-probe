package com.geoprobe.dto.engine.v42;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record EngineTaskContextPackage(
        String brandName,
        String category,
        List<String> competitors,
        List<String> categoryAnchors,
        List<CategoryAnchorSignal> categoryAnchorSignals,
        boolean fallbackAnchorMode,
        String brandBrief,
        List<String> categorySceneMap,
        List<String> descriptiveEntrypoints,
        List<String> competitorPlaceholders,
        List<String> personaExpressionTendencies,
        List<String> targetModelContextSignals,
        List<GroundedContextSignal> groundedSignals,
        List<String> missingContextItems,
        Map<String, Object> extensibleSignals
) {

    public EngineTaskContextPackage {
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        category = normalizeRequired(category, "category 不能为空");
        competitors = immutableDistinctList(competitors);
        categoryAnchors = immutableDistinctList(categoryAnchors);
        categoryAnchorSignals = immutableAnchorSignals(categoryAnchorSignals);
        brandBrief = normalizeOptional(brandBrief);
        categorySceneMap = immutableDistinctList(categorySceneMap);
        descriptiveEntrypoints = immutableDistinctList(descriptiveEntrypoints);
        competitorPlaceholders = immutableDistinctList(competitorPlaceholders);
        personaExpressionTendencies = immutableDistinctList(personaExpressionTendencies);
        targetModelContextSignals = immutableDistinctList(targetModelContextSignals);
        groundedSignals = immutableGroundedSignals(groundedSignals);
        missingContextItems = immutableDistinctList(missingContextItems);
        extensibleSignals = immutableObjectMap(extensibleSignals);
        validateAnchorCoverage(categoryAnchors, categoryAnchorSignals);
    }

    public EngineTaskContextPackage(String brandName,
                                    String category,
                                    List<String> competitors,
                                    List<String> categoryAnchors,
                                    List<CategoryAnchorSignal> categoryAnchorSignals,
                                    boolean fallbackAnchorMode,
                                    String brandBrief,
                                    List<String> categorySceneMap,
                                    List<String> descriptiveEntrypoints,
                                    List<String> competitorPlaceholders,
                                    List<String> personaExpressionTendencies,
                                    List<String> targetModelContextSignals,
                                    List<String> missingContextItems,
                                    Map<String, Object> extensibleSignals) {
        this(
                brandName,
                category,
                competitors,
                categoryAnchors,
                categoryAnchorSignals,
                fallbackAnchorMode,
                brandBrief,
                categorySceneMap,
                descriptiveEntrypoints,
                competitorPlaceholders,
                personaExpressionTendencies,
                targetModelContextSignals,
                List.of(),
                missingContextItems,
                extensibleSignals
        );
    }

    public List<GroundedContextSignal> effectiveGroundedSignals() {
        if (!groundedSignals.isEmpty()) {
            return groundedSignals;
        }
        LinkedHashMap<String, GroundedContextSignal> derived = new LinkedHashMap<>();
        for (CategoryAnchorSignal anchorSignal : categoryAnchorSignals) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                    anchorSignal.anchor(),
                    anchorSignal.source(),
                    null,
                    anchorSignal.confidence(),
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String categoryAnchor : categoryAnchors) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                    categoryAnchor,
                    "derived_category_anchor",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        if (brandBrief != null) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.BRAND_EVIDENCE,
                    brandBrief,
                    "derived_brand_brief",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String scenario : categorySceneMap) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.USAGE_SCENARIO,
                    scenario,
                    "derived_category_scene",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String entrypoint : descriptiveEntrypoints) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.DESCRIPTIVE_CUE,
                    entrypoint,
                    "derived_descriptive_entrypoint",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String competitor : competitorPlaceholders) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.COMPARISON_TARGET,
                    competitor,
                    "derived_competitor_placeholder",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String tendency : personaExpressionTendencies) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.PERSONA_EXPRESSION_TENDENCY,
                    tendency,
                    "derived_persona_expression_tendency",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String signal : targetModelContextSignals) {
            addDerivedSignal(derived, new GroundedContextSignal(
                    MinusOneContextSlots.TARGET_MODEL_CONTEXT_SIGNAL,
                    signal,
                    "derived_target_model_context_signal",
                    null,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        return List.copyOf(derived.values());
    }

    public List<String> slotValues(String slot) {
        String normalizedSlot = normalizeOptional(slot);
        if (normalizedSlot == null) {
            return List.of();
        }
        return effectiveGroundedSignals().stream()
                .filter(signal -> normalizedSlot.equals(signal.slot()))
                .map(GroundedContextSignal::value)
                .distinct()
                .toList();
    }

    public List<String> slotValuesForUse(String slot, String use) {
        return slotValuesForUse(slot, use, Integer.MAX_VALUE);
    }

    public List<String> slotValuesForUse(String slot, String use, int limit) {
        String normalizedSlot = normalizeOptional(slot);
        String normalizedUse = normalizeOptional(use);
        if (normalizedSlot == null || normalizedUse == null || limit <= 0) {
            return List.of();
        }
        return effectiveGroundedSignals().stream()
                .filter(signal -> normalizedSlot.equals(signal.slot()))
                .filter(signal -> signal.isUsedFor(normalizedUse))
                .map(GroundedContextSignal::value)
                .distinct()
                .limit(limit)
                .toList();
    }

    public List<String> node0StateInferenceValues() {
        return effectiveGroundedSignals().stream()
                .filter(signal -> signal.isUsedFor(MinusOneContextUses.NODE0_STATE_INFERENCE))
                .map(GroundedContextSignal::value)
                .distinct()
                .limit(12)
                .toList();
    }

    public List<String> node2RealizationValues() {
        return effectiveGroundedSignals().stream()
                .filter(signal -> signal.isUsedFor(MinusOneContextUses.NODE2_REALIZATION))
                .map(GroundedContextSignal::value)
                .distinct()
                .limit(12)
                .toList();
    }

    private static void validateAnchorCoverage(List<String> categoryAnchors,
                                               List<CategoryAnchorSignal> categoryAnchorSignals) {
        if (categoryAnchors.isEmpty() || categoryAnchorSignals.isEmpty()) {
            return;
        }
        LinkedHashSet<String> coveredAnchors = new LinkedHashSet<>();
        for (CategoryAnchorSignal signal : categoryAnchorSignals) {
            coveredAnchors.add(signal.anchor());
        }
        if (!coveredAnchors.containsAll(categoryAnchors)) {
            throw new IllegalArgumentException("categoryAnchorSignals 必须覆盖所有 categoryAnchors");
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> immutableDistinctList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }

    private static List<CategoryAnchorSignal> immutableAnchorSignals(List<CategoryAnchorSignal> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, CategoryAnchorSignal> sanitized = new LinkedHashMap<>();
        for (CategoryAnchorSignal value : values) {
            if (value != null) {
                sanitized.put(value.anchor(), value);
            }
        }
        return List.copyOf(sanitized.values());
    }

    private static List<GroundedContextSignal> immutableGroundedSignals(List<GroundedContextSignal> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, GroundedContextSignal> sanitized = new LinkedHashMap<>();
        for (GroundedContextSignal value : values) {
            if (value != null) {
                sanitized.put(value.dedupeKey(), value);
            }
        }
        return List.copyOf(sanitized.values());
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "map key 不能为空");
            Object value = entry.getValue();
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static void addDerivedSignal(Map<String, GroundedContextSignal> collector, GroundedContextSignal signal) {
        if (signal == null) {
            return;
        }
        collector.putIfAbsent(signal.dedupeKey(), signal);
    }
}
