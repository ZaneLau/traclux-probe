package com.geoprobe.dto.engine.v42;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeTypeTest {

    @Test
    void shouldClassifyOrthogonalM4AsBrandAssertive() {
        ProbeType probeType = ProbeType.classify(tags("verification", "optimal"));

        assertEquals(ProbeType.BRAND_ASSERTIVE, probeType);
    }

    @Test
    void shouldKeepNonM4TagsAsOpenDiscovery() {
        ProbeType probeType = ProbeType.classify(tags("exploration", "moderate_strain"));

        assertEquals(ProbeType.OPEN_DISCOVERY, probeType);
    }

    private Map<String, String> tags(String d1, String d4) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", d1);
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", d4);
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        return Map.copyOf(tags);
    }
}
