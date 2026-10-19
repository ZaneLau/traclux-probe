package com.geoprobe.dto.engine;

import java.util.List;
import java.util.Objects;

public record CategoryVariablePool(
        String brandName,
        String category,
        List<String> competitors,
        List<String> categoryAnchors,
        DimensionVariables d1Persona,
        DimensionVariables d2Context,
        DimensionVariables d3PainPoint,
        DimensionVariables d4Jtbd,
        DimensionVariables d5Knowledge,
        DimensionVariables d6Constraints
) {

    public CategoryVariablePool {
        competitors = competitors == null ? List.of() : List.copyOf(competitors);
        categoryAnchors = categoryAnchors == null ? List.of() : List.copyOf(categoryAnchors);
        d1Persona = Objects.requireNonNull(d1Persona, "d1Persona 不能为空");
        d2Context = Objects.requireNonNull(d2Context, "d2Context 不能为空");
        d3PainPoint = Objects.requireNonNull(d3PainPoint, "d3PainPoint 不能为空");
        d4Jtbd = Objects.requireNonNull(d4Jtbd, "d4Jtbd 不能为空");
        d5Knowledge = Objects.requireNonNull(d5Knowledge, "d5Knowledge 不能为空");
        d6Constraints = Objects.requireNonNull(d6Constraints, "d6Constraints 不能为空");
    }

    public List<DimensionVariables> allDimensions() {
        return List.of(d1Persona, d2Context, d3PainPoint, d4Jtbd, d5Knowledge, d6Constraints);
    }
}
