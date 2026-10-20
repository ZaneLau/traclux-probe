package com.geoprobe.dto.engine.v42;

import java.util.List;

public record DimensionVariables(
        String dimensionId,
        String dimensionName,
        List<String> abstractCodes,
        List<String> variables
) {

    public DimensionVariables {
        abstractCodes = abstractCodes == null ? List.of() : List.copyOf(abstractCodes);
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
