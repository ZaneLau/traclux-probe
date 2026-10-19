package com.geoprobe.dto.engine.v42;

import java.util.List;
import java.util.Objects;

public record Node4ParsingResult(List<Node4ProbeOutput> probes) {

    public Node4ParsingResult {
        Objects.requireNonNull(probes, "probes 不能为空");
        probes = List.copyOf(probes);
    }
}
