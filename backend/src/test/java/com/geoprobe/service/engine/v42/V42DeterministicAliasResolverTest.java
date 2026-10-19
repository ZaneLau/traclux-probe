package com.geoprobe.service.engine.v42;

import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42DeterministicAliasResolverTest {

    @Test
    void shouldFillBrandAndCompetitorAliasesWhenGeneratedConfigIsEmpty() {
        DiagnosisAliasConfigData resolved = V42DeterministicAliasResolver.resolve(
                "鸿蒙智行",
                List.of("复旦大学附属肿瘤医院", "DJI"),
                new DiagnosisAliasConfigData(List.of(), Map.of())
        );

        assertTrue(resolved.brandAliases().contains("AITO"));
        assertTrue(resolved.brandAliases().contains("问界"));
        assertTrue(resolved.competitorAliases().get("复旦大学附属肿瘤医院").contains("复旦肿瘤医院"));
        assertTrue(resolved.competitorAliases().get("复旦大学附属肿瘤医院").contains("复旦大学附属肿瘤"));
        assertTrue(resolved.competitorAliases().get("DJI").contains("大疆"));
    }

    @Test
    void shouldPreserveConfiguredAliasesAheadOfFallbacks() {
        List<String> resolved = V42DeterministicAliasResolver.resolveAliasesForName(
                "鸿蒙智行",
                List.of("鸿蒙车")
        );

        assertEquals("鸿蒙车", resolved.getFirst());
        assertTrue(resolved.contains("AITO"));
    }
}
