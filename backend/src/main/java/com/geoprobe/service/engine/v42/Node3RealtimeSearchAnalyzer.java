package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Node3RealtimeSearchAnalyzer {

    List<Integer> unsatisfiedIndexes(List<Node3ProbeOutput> outputs) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < outputs.size(); index++) {
            Node3ProbeOutput output = outputs.get(index);
            if (output == null) {
                continue;
            }
            RawResponse rawResponse = output.rawResponse();
            if (rawResponse.searchRequested() && !rawResponse.searchSatisfied()) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    Node3SearchSnapshot computeSnapshot(List<Node3ProbeOutput> outputs, RealtimeSearchPolicy policy) {
        if (!policy.enabled()) {
            return new Node3SearchSnapshot(0, 0, 0.0d, 0, true, List.of());
        }
        int requested = 0;
        int satisfied = 0;
        int fallback = 0;
        Set<String> unsatisfied = new LinkedHashSet<>();
        for (Node3ProbeOutput output : outputs) {
            if (output == null) {
                continue;
            }
            RawResponse raw = output.rawResponse();
            if (!raw.searchRequested()) {
                continue;
            }
            requested++;
            if (raw.searchSatisfied()) {
                satisfied++;
            } else {
                unsatisfied.add(output.probeId());
            }
            if (raw.searchFallbackFlag()) {
                fallback++;
            }
        }
        double successRate = requested == 0 ? 1.0d : (double) satisfied / requested;
        boolean passed = successRate >= policy.minEvidenceSuccessRate();
        return new Node3SearchSnapshot(requested, satisfied, successRate, fallback, passed, List.copyOf(unsatisfied));
    }
}
