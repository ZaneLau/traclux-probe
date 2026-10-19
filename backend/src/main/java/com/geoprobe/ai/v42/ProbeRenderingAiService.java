package com.geoprobe.ai.v42;

public interface ProbeRenderingAiService {

    String renderProbe(
            String brandName,
            String category,
            String competitors,
            String categoryAnchors,
            String brandInjectionPolicy,
            String simulationProfile,
            String mustSemantics,
            String softHints,
            String behaviorRules
    );

}
