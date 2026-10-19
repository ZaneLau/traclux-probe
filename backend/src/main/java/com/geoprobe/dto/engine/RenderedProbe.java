package com.geoprobe.dto.engine;

import java.util.Map;

public record RenderedProbe(
        String probeId,
        ProbeBucketType bucketType,
        Map<String, String> tags,
        String templateKey,
        String templateText,
        String renderedPrompt,
        String angle,
        boolean fallbackUsed
) {

    public RenderedProbe {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
