package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.ExecutionSignal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class V42ExecutionIntegrityView {

    private V42ExecutionIntegrityView() {
    }

    static boolean degraded(List<ExecutionSignal> signals) {
        return !normalize(signals).isEmpty();
    }

    static int signalCount(List<ExecutionSignal> signals) {
        return normalize(signals).size();
    }

    static Map<String, Object> toMap(List<ExecutionSignal> signals,
                                     Function<List<ExecutionSignal>, List<Map<String, Object>>> signalMapper) {
        List<ExecutionSignal> normalizedSignals = normalize(signals);
        LinkedHashMap<String, Object> integrity = new LinkedHashMap<>();
        integrity.put("degraded", !normalizedSignals.isEmpty());
        integrity.put("signalCount", normalizedSignals.size());
        integrity.put("codes", normalizedSignals.stream().map(ExecutionSignal::code).distinct().toList());
        integrity.put("stages", normalizedSignals.stream().map(ExecutionSignal::stage).distinct().toList());
        integrity.put("reasonCodes", normalizedSignals.stream()
                .flatMap(signal -> signal.reasonCodes().stream())
                .distinct()
                .toList());
        integrity.put("signals", signalMapper.apply(normalizedSignals));
        return integrity;
    }

    private static List<ExecutionSignal> normalize(List<ExecutionSignal> signals) {
        return signals == null ? List.of() : List.copyOf(signals);
    }
}
