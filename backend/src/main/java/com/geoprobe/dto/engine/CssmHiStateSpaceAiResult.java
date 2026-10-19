package com.geoprobe.dto.engine;

import java.util.List;

public record CssmHiStateSpaceAiResult(
        List<String> d1Persona,
        List<String> d2Context,
        List<String> d3PainPoint,
        List<String> d4Jtbd,
        List<String> d5Knowledge,
        List<String> d6Constraints
) {

    public CssmHiStateSpaceAiResult {
        d1Persona = d1Persona == null ? List.of() : List.copyOf(d1Persona);
        d2Context = d2Context == null ? List.of() : List.copyOf(d2Context);
        d3PainPoint = d3PainPoint == null ? List.of() : List.copyOf(d3PainPoint);
        d4Jtbd = d4Jtbd == null ? List.of() : List.copyOf(d4Jtbd);
        d5Knowledge = d5Knowledge == null ? List.of() : List.copyOf(d5Knowledge);
        d6Constraints = d6Constraints == null ? List.of() : List.copyOf(d6Constraints);
    }
}
