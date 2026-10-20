package com.geoprobe.dto.engine;

import com.geoprobe.service.shared.FcbClassification;

import java.util.List;

public record IntentClusterResult(
        List<IntentCluster> clusters,
        FcbClassification fcbClassification,
        String edition,
        int totalCorePrompts,
        boolean fallbackUsed,
        String source
) {

    public IntentClusterResult(List<IntentCluster> clusters) {
        this(clusters, null, "STANDARD", 11, false, "LLM");
    }

    public IntentClusterResult(List<IntentCluster> clusters, boolean fallbackUsed, String source) {
        this(clusters, null, "STANDARD", 11, fallbackUsed, source);
    }

    public IntentClusterResult {
        clusters = clusters == null ? List.of() : List.copyOf(clusters);
        edition = edition == null ? "STANDARD" : edition;
        totalCorePrompts = totalCorePrompts <= 0 ? 11 : totalCorePrompts;
        source = source == null ? "LLM" : source;
    }
}
