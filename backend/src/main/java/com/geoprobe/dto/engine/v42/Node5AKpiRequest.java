package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Node5AKpiRequest(List<Node4ProbeOutput> probes) {

    public Node5AKpiRequest {
        Objects.requireNonNull(probes, "probes 不能为空");
        List<Node4ProbeOutput> sanitized = new ArrayList<>(probes.size());
        for (Node4ProbeOutput probe : probes) {
            sanitized.add(Objects.requireNonNull(probe, "probes 不能包含 null"));
        }
        probes = List.copyOf(sanitized);
    }
}
