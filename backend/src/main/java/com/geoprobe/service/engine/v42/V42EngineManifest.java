package com.geoprobe.service.engine.v42;

import java.util.LinkedHashMap;
import java.util.Map;

public final class V42EngineManifest {

    private V42EngineManifest() {
    }

    public static Map<String, String> components() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("stateSpace", "cssm_hi_state_space_v4_2");
        components.put("matrix", "orthogonal_matrix_v4_2");
        components.put("rendering", "probe_rendering_v4_2");
        components.put("sampling", "node3_sampling_gateway_v4_2");
        components.put("parsing", "brand_detection_chain_v4_2");
        components.put("aggregation", "node5_aggregation_v4_2");
        components.put("reportProjection", "report_projection_v4_2");
        return Map.copyOf(components);
    }
}
