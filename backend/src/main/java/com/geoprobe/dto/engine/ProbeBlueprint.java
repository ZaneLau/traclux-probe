package com.geoprobe.dto.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProbeBlueprint(
        String probeId,
        ProbeBucketType bucketType,
        Map<String, String> tags,
        String templateKey,
        String templateText,
        ProbeStatus status
) {

    public ProbeBlueprint {
        tags = tags == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tags));
        status = status == null ? ProbeStatus.PENDING : status;
    }
}
