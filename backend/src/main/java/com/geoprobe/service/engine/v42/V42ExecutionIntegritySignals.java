package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceResult;
import com.geoprobe.dto.engine.v42.ExecutionSignal;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class V42ExecutionIntegritySignals {

    private static final String SEVERITY_WARN = "WARN";
    private static final String SEVERITY_ERROR = "ERROR";

    private V42ExecutionIntegritySignals() {
    }

    static List<ExecutionSignal> collect(CssmHiStateSpaceResult node0Result,
                                         List<RenderedProbe> renderedProbes,
                                         CommercialInsights commercialInsights) {
        java.util.ArrayList<ExecutionSignal> signals = new java.util.ArrayList<>();
        appendNode0Signals(signals, node0Result);
        appendNode2Signals(signals, renderedProbes);
        appendNode5BSignals(signals, commercialInsights);
        return List.copyOf(signals);
    }

    static List<ExecutionSignal> collectBeforeInsights(CssmHiStateSpaceResult node0Result,
                                                       List<RenderedProbe> renderedProbes) {
        return collect(node0Result, renderedProbes, null);
    }

    static List<ExecutionSignal> node0Signals(CssmHiStateSpaceResult node0Result) {
        java.util.ArrayList<ExecutionSignal> signals = new java.util.ArrayList<>();
        appendNode0Signals(signals, node0Result);
        return List.copyOf(signals);
    }

    static List<ExecutionSignal> node2Signals(List<RenderedProbe> renderedProbes) {
        java.util.ArrayList<ExecutionSignal> signals = new java.util.ArrayList<>();
        appendNode2Signals(signals, renderedProbes);
        return List.copyOf(signals);
    }

    static List<ExecutionSignal> node5BSignals(CommercialInsights commercialInsights) {
        java.util.ArrayList<ExecutionSignal> signals = new java.util.ArrayList<>();
        appendNode5BSignals(signals, commercialInsights);
        return List.copyOf(signals);
    }

    private static void appendNode0Signals(List<ExecutionSignal> signals, CssmHiStateSpaceResult node0Result) {
        if (signals == null || node0Result == null || !node0Result.fallbackUsed()) {
            return;
        }
        String reasonCode = defaultReasonCode(node0Result.fallbackReasonCode());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", node0Result.source());
        details.put("dimensionCount", node0Result.pool().allDimensions().size());
        details.put("groundedSignalCount", node0Result.pool().effectiveGroundedSignals().size());
        details.put("fallbackReasonCode", reasonCode);
        signals.add(new ExecutionSignal(
                "NODE0_STATE_SPACE_FALLBACK",
                "NODE0_STATE_SPACE",
                SEVERITY_ERROR,
                "Node0 状态空间推断已降级为 deterministic fallback，原因码=%s。".formatted(reasonCode),
                List.of(reasonCode),
                details
        ));
    }

    private static void appendNode2Signals(List<ExecutionSignal> signals, List<RenderedProbe> renderedProbes) {
        if (signals == null || renderedProbes == null || renderedProbes.isEmpty()) {
            return;
        }
        long fallbackCount = renderedProbes.stream().filter(RenderedProbe::fallbackUsed).count();
        long repairCount = renderedProbes.stream().filter(V42ExecutionIntegritySignals::repairUsed).count();
        if (fallbackCount > 0L) {
            int total = renderedProbes.size();
            double fallbackRate = total <= 0 ? 0.0d : fallbackCount * 1.0d / total;
            Map<String, Integer> reasonCodeCounts = countFallbackReasonCodes(renderedProbes);
            List<String> reasonCodes = List.copyOf(reasonCodeCounts.keySet());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("probeCount", total);
            details.put("fallbackCount", fallbackCount);
            details.put("fallbackRate", fallbackRate);
            details.put("fallbackFamilyCounts", countFallbackFamilies(renderedProbes));
            details.put("renderSourceCounts", countRenderSources(renderedProbes));
            details.put("reasonCodeCounts", reasonCodeCounts);
            signals.add(new ExecutionSignal(
                    "NODE2_RENDER_FALLBACK_PRESENT",
                    "NODE2_RENDERING",
                    fallbackRate >= 0.25d ? SEVERITY_ERROR : SEVERITY_WARN,
                    "Node2 探针生成出现 deterministic fallback，原因码=%s。".formatted(String.join(", ", reasonCodes)),
                    reasonCodes,
                    details
            ));
        }
        if (repairCount > 0L) {
            int total = renderedProbes.size();
            double repairRate = total <= 0 ? 0.0d : repairCount * 1.0d / total;
            Map<String, Integer> repairRuleCounts = countRepairRules(renderedProbes);
            List<String> repairRules = List.copyOf(repairRuleCounts.keySet());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("probeCount", total);
            details.put("repairCount", repairCount);
            details.put("repairRate", repairRate);
            details.put("repairRuleCounts", repairRuleCounts);
            details.put("renderSourceCounts", countRenderSources(renderedProbes));
            signals.add(new ExecutionSignal(
                    "NODE2_RENDER_REPAIR_PRESENT",
                    "NODE2_RENDERING",
                    SEVERITY_ERROR,
                    "Node2 探针生成出现 repair，repair=%s。".formatted(String.join(", ", repairRules)),
                    repairRules,
                    details
            ));
        }
    }

    private static void appendNode5BSignals(List<ExecutionSignal> signals, CommercialInsights commercialInsights) {
        if (signals == null || commercialInsights == null || !"TEMPLATE_FALLBACK".equals(commercialInsights.insightSource())) {
            return;
        }
        String reasonCode = defaultReasonCode(commercialInsights.fallbackReasonCode());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("insightSource", commercialInsights.insightSource());
        details.put("topInsightCount", commercialInsights.topThreeInsights().size());
        details.put("headlineInsight", commercialInsights.headlineInsight());
        details.put("fallbackReasonCode", reasonCode);
        signals.add(new ExecutionSignal(
                "NODE5B_INSIGHT_TEMPLATE_FALLBACK",
                "NODE5B_INSIGHTS",
                SEVERITY_WARN,
                "Node5B 商业洞察已降级为模板 fallback，原因码=%s。".formatted(reasonCode),
                List.of(reasonCode),
                details
        ));
    }

    private static String defaultReasonCode(String reasonCode) {
        return StringUtils.hasText(reasonCode)
                ? reasonCode.trim()
                : V42CreativeDegradationReasons.UNKNOWN_FAILURE;
    }

    private static Map<String, Integer> countFallbackReasonCodes(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            if (probe == null || !probe.fallbackUsed()) {
                continue;
            }
            String reasonCode = defaultReasonCode(V42CreativeDegradationReasons.resolveNode2(probe));
            counts.merge(reasonCode, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> countFallbackFamilies(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            if (probe == null || !probe.fallbackUsed() || !StringUtils.hasText(probe.renderFamily())) {
                continue;
            }
            String family = probe.renderFamily().trim();
            counts.merge(family, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> countRenderSources(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            if (probe == null || !StringUtils.hasText(probe.renderSource())) {
                continue;
            }
            String source = probe.renderSource().trim().toUpperCase(Locale.ROOT);
            counts.merge(source, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> countRepairRules(List<RenderedProbe> renderedProbes) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RenderedProbe probe : renderedProbes) {
            if (!repairUsed(probe)) {
                continue;
            }
            String repairRule = normalizeRepairRule(probe);
            counts.merge(repairRule, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static boolean repairUsed(RenderedProbe probe) {
        return StringUtils.hasText(normalizeRepairRule(probe));
    }

    private static String normalizeRepairRule(RenderedProbe probe) {
        if (probe == null || probe.triggeredRulesJson() == null) {
            return null;
        }
        Object repairRule = probe.triggeredRulesJson().get("validationRepairApplied");
        if (repairRule == null) {
            return null;
        }
        String normalized = repairRule.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
