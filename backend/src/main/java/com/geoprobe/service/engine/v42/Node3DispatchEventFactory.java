package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class Node3DispatchEventFactory {

    private Node3DispatchEventFactory() {
    }

    static V42Node3SamplingGatewayService.DispatchEvent probeResultEvent(String diagnosisId,
                                                                         int round,
                                                                         int probeIndex,
                                                                         int completed,
                                                                         int total,
                                                                         int activeWindow,
                                                                         Node3ProbeOutput output,
                                                                         boolean exposeRoutingDebug) {
        LinkedHashSet<String> uniqueQueries = new LinkedHashSet<>(output.rawResponse().searchQueries());
        output.rawResponse().searchEvidence().forEach(item -> {
            if (item != null && StringUtils.hasText(item.query())) {
                uniqueQueries.add(item.query().trim());
            }
        });
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("diagnosisId", diagnosisId == null ? "" : diagnosisId);
        payload.put("stage", "NODE3_SAMPLING");
        payload.put("probeId", output.probeId());
        payload.put("probeIndex", probeIndex);
        payload.put("round", round);
        payload.put("status", output.dispatchStatus().name());
        payload.put("completed", completed);
        payload.put("total", total);
        payload.put("activeWindow", activeWindow);
        payload.put("renderedPrompt", output.renderedPrompt());
        payload.put("renderFamily", output.renderFamily());
        payload.put("renderReason", output.renderReason());
        payload.put("abstractTags", output.abstractTags());
        payload.put("translatedTags", output.translatedTags());
        payload.put("responseText", output.rawResponse().fullText());
        payload.put("failureReason", output.failureReason() == null ? "" : output.failureReason());
        payload.put("latencyMs", output.rawResponse().latencyMs());
        payload.put("promptTokenCount", output.rawResponse().promptTokenCount());
        payload.put("completionTokenCount", output.rawResponse().completionTokenCount());
        payload.put("totalTokenCount", output.rawResponse().totalTokenCount());
        payload.put("searchRequested", output.rawResponse().searchRequested());
        payload.put("searchSatisfied", output.rawResponse().searchSatisfied());
        payload.put("searchFallbackFlag", output.rawResponse().searchFallbackFlag());
        payload.put("searchCallCount", output.rawResponse().searchCallCount());
        payload.put("searchQueries", List.copyOf(uniqueQueries));
        payload.put("searchEvidence", output.rawResponse().searchEvidence());
        payload.put("modelId", output.rawResponse().modelId());
        payload.put("routingIntent", output.rawResponse().routingIntent());
        if (exposeRoutingDebug) {
            payload.put("routingCandidates", output.rawResponse().routingCandidates());
            payload.put("routingLog", buildRoutingLogMessage(output));
        }
        return new V42Node3SamplingGatewayService.DispatchEvent("probe_result", true, payload);
    }

    static V42Node3SamplingGatewayService.DispatchEvent roundSummaryEvent(String diagnosisId,
                                                                          int round,
                                                                          boolean backfill,
                                                                          int roundRequested,
                                                                          int roundSucceeded,
                                                                          Node3SearchSnapshot snapshot,
                                                                          List<Node3ProbeOutput> outputs,
                                                                          int activeWindow,
                                                                          RealtimeSearchPolicy policy) {
        if (policy == null || !policy.enabled()) {
            return null;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("diagnosisId", diagnosisId == null ? "" : diagnosisId);
        payload.put("stage", "NODE3_SAMPLING");
        payload.put("round", round);
        payload.put("backfill", backfill);
        payload.put("roundRequested", Math.max(0, roundRequested));
        payload.put("roundSucceeded", Math.max(0, roundSucceeded));
        payload.put("requestedProbeCount", snapshot.requestedProbeCount());
        payload.put("satisfiedProbeCount", snapshot.satisfiedProbeCount());
        payload.put("evidenceSuccessRate", snapshot.evidenceSuccessRate());
        payload.put("fallbackProbeCount", snapshot.fallbackProbeCount());
        payload.put("unsatisfiedCount", snapshot.unsatisfiedProbeIds().size());
        payload.put("qualityGatePassed", snapshot.qualityGatePassed());
        payload.put("activeWindow", activeWindow);
        payload.put("modelDistribution", resolveModelDistribution(outputs));
        return new V42Node3SamplingGatewayService.DispatchEvent("round_summary", true, payload);
    }

    private static String buildRoutingLogMessage(Node3ProbeOutput output) {
        String modelId = normalizeOrUnknown(output.rawResponse().modelId());
        String routingIntent = normalizeOrUnknown(output.rawResponse().routingIntent());
        int candidateCount = output.rawResponse().routingCandidates().size();
        return "Node3 路由: intent=" + routingIntent
                + ", modelId=" + modelId
                + ", candidates=" + candidateCount;
    }

    private static String normalizeOrUnknown(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }

    private static Map<String, Integer> resolveModelDistribution(List<Node3ProbeOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> distribution = new HashMap<>();
        for (Node3ProbeOutput output : outputs) {
            if (output == null || output.rawResponse() == null) {
                continue;
            }
            String modelId = normalizeOrUnknown(output.rawResponse().modelId());
            distribution.merge(modelId, 1, Integer::sum);
        }
        return distribution;
    }
}
