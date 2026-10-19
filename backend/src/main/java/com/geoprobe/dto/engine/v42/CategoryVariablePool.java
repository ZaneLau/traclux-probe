package com.geoprobe.dto.engine.v42;

import java.util.List;
import java.util.Objects;

public record CategoryVariablePool(
        String brandName,
        String category,
        List<String> competitors,
        List<String> categoryAnchors,
        DimensionVariables d1CognitiveAnomaly,
        DimensionVariables d2TeleologicalGoal,
        DimensionVariables d3AxiologicalDrive,
        DimensionVariables d4CognitiveLoad,
        DimensionVariables d5TimeConstraint,
        DimensionVariables d6InteractionModality,
        EngineTaskContextPackage contextPackage
) {

    public CategoryVariablePool {
        competitors = competitors == null ? List.of() : List.copyOf(competitors);
        categoryAnchors = categoryAnchors == null ? List.of() : List.copyOf(categoryAnchors);
        d1CognitiveAnomaly = Objects.requireNonNull(d1CognitiveAnomaly, "d1CognitiveAnomaly 不能为空");
        d2TeleologicalGoal = Objects.requireNonNull(d2TeleologicalGoal, "d2TeleologicalGoal 不能为空");
        d3AxiologicalDrive = Objects.requireNonNull(d3AxiologicalDrive, "d3AxiologicalDrive 不能为空");
        d4CognitiveLoad = Objects.requireNonNull(d4CognitiveLoad, "d4CognitiveLoad 不能为空");
        d5TimeConstraint = Objects.requireNonNull(d5TimeConstraint, "d5TimeConstraint 不能为空");
        d6InteractionModality = Objects.requireNonNull(d6InteractionModality, "d6InteractionModality 不能为空");
    }

    public CategoryVariablePool(String brandName,
                                String category,
                                List<String> competitors,
                                List<String> categoryAnchors,
                                DimensionVariables d1CognitiveAnomaly,
                                DimensionVariables d2TeleologicalGoal,
                                DimensionVariables d3AxiologicalDrive,
                                DimensionVariables d4CognitiveLoad,
                                DimensionVariables d5TimeConstraint,
                                DimensionVariables d6InteractionModality) {
        this(
                brandName,
                category,
                competitors,
                categoryAnchors,
                d1CognitiveAnomaly,
                d2TeleologicalGoal,
                d3AxiologicalDrive,
                d4CognitiveLoad,
                d5TimeConstraint,
                d6InteractionModality,
                null
        );
    }

    public List<DimensionVariables> allDimensions() {
        return List.of(
                d1CognitiveAnomaly,
                d2TeleologicalGoal,
                d3AxiologicalDrive,
                d4CognitiveLoad,
                d5TimeConstraint,
                d6InteractionModality
        );
    }

    public List<GroundedContextSignal> effectiveGroundedSignals() {
        return contextPackage == null ? List.of() : contextPackage.effectiveGroundedSignals();
    }

    public List<String> node0StateInferenceValues() {
        return contextPackage == null ? List.of() : contextPackage.node0StateInferenceValues();
    }

    public List<String> node2RealizationValues() {
        return contextPackage == null ? List.of() : contextPackage.node2RealizationValues();
    }
}
