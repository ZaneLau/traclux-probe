package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class VolcengineRewardPlanBudgetPlanner {

    private static final List<String> DEFAULT_COST_PRIORITY = List.of(
            "doubao-seed-1-6-lite",
            "doubao-seed-1-6-flash",
            "doubao-seed-1-6",
            "doubao-seed-2-0-mini",
            "doubao-seed-2-0-lite",
            "doubao-seed-1-8",
            "doubao-seed-2-0-pro"
    );
    private static final double DEFAULT_SAFETY_MARGIN = 0.20d;
    private static final Pattern DATE_SUFFIX_PATTERN = Pattern.compile("-\\d{6,8}$");

    private VolcengineRewardPlanBudgetPlanner() {
    }

    static RewardPlanBudgetPlan planFirstRun50(Map<String, Long> remainingTokensByModel) {
        return plan(
                "brand_space_first_run_50",
                50,
                ScenarioProfile.firstRun50(),
                remainingTokensByModel
        );
    }

    static RewardPlanBudgetPlan plan(String scenarioId,
                                     int targetProbeCount,
                                     ScenarioProfile profile,
                                     Map<String, Long> remainingTokensByModel) {
        if (!StringUtils.hasText(scenarioId)) {
            throw new IllegalArgumentException("scenarioId 不能为空");
        }
        if (targetProbeCount <= 0) {
            throw new IllegalArgumentException("targetProbeCount 必须大于 0");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile 不能为空");
        }
        if (remainingTokensByModel == null || remainingTokensByModel.isEmpty()) {
            throw new IllegalArgumentException("reward plan 余量不能为空");
        }

        LinkedHashMap<String, Long> normalizedBudgets = normalizeBudgets(remainingTokensByModel);
        long controlPlaneTokens = profile.recognitionTokens()
                + profile.contextExpansionTokens()
                + profile.node0Tokens();
        long renderTokens = Math.multiplyExact(profile.node2RenderTokensPerProbe(), targetProbeCount);

        List<ModelBudgetView> budgetViews = buildBudgetViews(profile.modelPriority(), normalizedBudgets, profile.safetyMargin());
        ControlPlaneAssignment controlPlane = assignControlPlane(controlPlaneTokens, budgetViews);
        List<RenderAllocation> renderAllocations = assignRender(renderTokens, targetProbeCount, profile, budgetViews, controlPlane.modelId());

        long plannedRenderTokens = renderAllocations.stream().mapToLong(RenderAllocation::plannedTokens).sum();
        int plannedProbeCount = renderAllocations.stream().mapToInt(RenderAllocation::probeCount).sum();
        if (plannedProbeCount < targetProbeCount || plannedRenderTokens < renderTokens) {
            throw new IllegalStateException(
                    "reward plan 余量不足，无法在安全边界内完成 %d 条探针；当前仅能安全分配 %d 条"
                            .formatted(targetProbeCount, plannedProbeCount)
            );
        }

        List<ModelPlan> modelPlans = buildModelPlans(budgetViews, controlPlane, renderAllocations);
        long plannedTotalTokens = controlPlaneTokens + plannedRenderTokens;
        long safeCapacityTokens = budgetViews.stream().mapToLong(ModelBudgetView::safeUsableTokens).sum();
        long totalRemainingTokens = budgetViews.stream().mapToLong(ModelBudgetView::remainingTokens).sum();

        return new RewardPlanBudgetPlan(
                scenarioId.trim(),
                targetProbeCount,
                profile,
                totalRemainingTokens,
                safeCapacityTokens,
                plannedTotalTokens,
                controlPlane,
                List.copyOf(renderAllocations),
                List.copyOf(modelPlans)
        );
    }

    static Map<String, Long> parseRemainingBudgetText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        LinkedHashMap<String, Long> budgets = new LinkedHashMap<>();
        for (String segment : raw.split("[,;\\n]+")) {
            String entry = segment == null ? "" : segment.trim();
            if (entry.isEmpty() || !entry.contains("=")) {
                continue;
            }
            int split = entry.indexOf('=');
            String modelId = entry.substring(0, split).trim();
            String tokenText = entry.substring(split + 1).trim();
            if (!StringUtils.hasText(modelId) || !StringUtils.hasText(tokenText)) {
                continue;
            }
            try {
                long remaining = Long.parseLong(tokenText);
                String normalizedModelId = normalizeModelId(modelId);
                if (normalizedModelId != null) {
                    budgets.put(normalizedModelId, Math.max(0L, remaining));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries so the planner can stay fail-closed on missing capacity.
            }
        }
        return Map.copyOf(budgets);
    }

    private static LinkedHashMap<String, Long> normalizeBudgets(Map<String, Long> raw) {
        LinkedHashMap<String, Long> normalized = new LinkedHashMap<>();
        for (String modelId : DEFAULT_COST_PRIORITY) {
            normalized.put(modelId, 0L);
        }
        raw.forEach((modelId, remaining) -> {
            String normalizedModelId = normalizeModelId(modelId);
            if (normalizedModelId != null) {
                normalized.put(
                        normalizedModelId,
                        Math.max(0L, remaining == null ? 0L : remaining)
                );
            }
        });
        return normalized;
    }

    private static String normalizeModelId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase().replace(".", "-");
        normalized = DATE_SUFFIX_PATTERN.matcher(normalized).replaceFirst("");
        return normalized;
    }

    private static List<ModelBudgetView> buildBudgetViews(List<String> priority,
                                                          Map<String, Long> normalizedBudgets,
                                                          double safetyMargin) {
        List<String> ordered = priority == null || priority.isEmpty() ? DEFAULT_COST_PRIORITY : priority;
        List<ModelBudgetView> views = new ArrayList<>();
        for (String modelId : ordered) {
            long remaining = Math.max(0L, normalizedBudgets.getOrDefault(modelId, 0L));
            long safeUsableTokens = Math.max(0L, (long) Math.floor(remaining * (1.0d - safetyMargin)));
            views.add(new ModelBudgetView(modelId, remaining, safeUsableTokens));
        }
        return List.copyOf(views);
    }

    private static ControlPlaneAssignment assignControlPlane(long controlPlaneTokens,
                                                             List<ModelBudgetView> budgetViews) {
        for (ModelBudgetView budgetView : budgetViews) {
            if (budgetView.safeUsableTokens() >= controlPlaneTokens) {
                return new ControlPlaneAssignment(
                        budgetView.modelId(),
                        controlPlaneTokens,
                        List.of("recognition", "minus_one_context_expansion", "node0_state_space")
                );
            }
        }
        throw new IllegalStateException("reward plan 余量不足，无法安全覆盖控制面 live 调用");
    }

    private static List<RenderAllocation> assignRender(long renderTokens,
                                                       int targetProbeCount,
                                                       ScenarioProfile profile,
                                                       List<ModelBudgetView> budgetViews,
                                                       String reservedControlPlaneModel) {
        List<RenderAllocation> allocations = new ArrayList<>();
        long remainingRenderTokens = renderTokens;
        int remainingProbes = targetProbeCount;
        for (ModelBudgetView budgetView : budgetViews) {
            long controlPlaneReservation = budgetView.modelId().equals(reservedControlPlaneModel)
                    ? profile.recognitionTokens() + profile.contextExpansionTokens() + profile.node0Tokens()
                    : 0L;
            long safeFreeTokens = Math.max(0L, budgetView.safeUsableTokens() - controlPlaneReservation);
            if (safeFreeTokens < profile.node2RenderTokensPerProbe()) {
                continue;
            }
            int maxProbeCount = (int) Math.min(
                    remainingProbes,
                    safeFreeTokens / profile.node2RenderTokensPerProbe()
            );
            if (maxProbeCount <= 0) {
                continue;
            }
            long plannedTokens = Math.min(remainingRenderTokens, Math.multiplyExact(profile.node2RenderTokensPerProbe(), maxProbeCount));
            allocations.add(new RenderAllocation(budgetView.modelId(), maxProbeCount, plannedTokens));
            remainingRenderTokens -= plannedTokens;
            remainingProbes -= maxProbeCount;
            if (remainingRenderTokens <= 0L || remainingProbes <= 0) {
                break;
            }
        }
        return List.copyOf(allocations);
    }

    private static List<ModelPlan> buildModelPlans(List<ModelBudgetView> budgetViews,
                                                   ControlPlaneAssignment controlPlane,
                                                   List<RenderAllocation> renderAllocations) {
        Map<String, RenderAllocation> renderByModel = new LinkedHashMap<>();
        for (RenderAllocation allocation : renderAllocations) {
            renderByModel.put(allocation.modelId(), allocation);
        }
        List<ModelPlan> plans = new ArrayList<>();
        for (ModelBudgetView budgetView : budgetViews) {
            long controlPlaneTokens = budgetView.modelId().equals(controlPlane.modelId())
                    ? controlPlane.plannedTokens()
                    : 0L;
            RenderAllocation renderAllocation = renderByModel.get(budgetView.modelId());
            long renderTokens = renderAllocation == null ? 0L : renderAllocation.plannedTokens();
            int renderProbeCount = renderAllocation == null ? 0 : renderAllocation.probeCount();
            long plannedTokens = controlPlaneTokens + renderTokens;
            plans.add(new ModelPlan(
                    budgetView.modelId(),
                    budgetView.remainingTokens(),
                    budgetView.safeUsableTokens(),
                    controlPlaneTokens,
                    renderProbeCount,
                    renderTokens,
                    plannedTokens,
                    Math.max(0L, budgetView.safeUsableTokens() - plannedTokens)
            ));
        }
        return List.copyOf(plans);
    }

    record ScenarioProfile(long recognitionTokens,
                           long contextExpansionTokens,
                           long node0Tokens,
                           long node2RenderTokensPerProbe,
                           double safetyMargin,
                           List<String> modelPriority) {

        static ScenarioProfile firstRun50() {
            return new ScenarioProfile(
                    12_000L,
                    8_000L,
                    10_000L,
                    2_200L,
                    DEFAULT_SAFETY_MARGIN,
                    DEFAULT_COST_PRIORITY
            );
        }
    }

    record RewardPlanBudgetPlan(String scenarioId,
                                int targetProbeCount,
                                ScenarioProfile profile,
                                long totalRemainingTokens,
                                long safeCapacityTokens,
                                long plannedTotalTokens,
                                ControlPlaneAssignment controlPlane,
                                List<RenderAllocation> renderAllocations,
                                List<ModelPlan> modelPlans) {

        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("scenarioId", scenarioId);
            result.put("targetProbeCount", targetProbeCount);
            result.put("totalRemainingTokens", totalRemainingTokens);
            result.put("safeCapacityTokens", safeCapacityTokens);
            result.put("plannedTotalTokens", plannedTotalTokens);
            result.put("safetyMargin", profile.safetyMargin());
            result.put("controlPlane", Map.of(
                    "modelId", controlPlane.modelId(),
                    "plannedTokens", controlPlane.plannedTokens(),
                    "stages", controlPlane.stages()
            ));
            result.put("renderAllocations", renderAllocations.stream()
                    .map(allocation -> Map.of(
                            "modelId", allocation.modelId(),
                            "probeCount", allocation.probeCount(),
                            "plannedTokens", allocation.plannedTokens()
                    ))
                    .toList());
            result.put("modelPlans", modelPlans.stream()
                    .map(plan -> Map.of(
                            "modelId", plan.modelId(),
                            "remainingTokens", plan.remainingTokens(),
                            "safeUsableTokens", plan.safeUsableTokens(),
                            "controlPlaneTokens", plan.controlPlaneTokens(),
                            "renderProbeCount", plan.renderProbeCount(),
                            "renderTokens", plan.renderTokens(),
                            "plannedTokens", plan.plannedTokens(),
                            "safeHeadroomAfterPlan", plan.safeHeadroomAfterPlan()
                    ))
                    .toList());
            return Map.copyOf(result);
        }
    }

    record ControlPlaneAssignment(String modelId,
                                  long plannedTokens,
                                  List<String> stages) {
    }

    record RenderAllocation(String modelId,
                            int probeCount,
                            long plannedTokens) {
    }

    record ModelPlan(String modelId,
                     long remainingTokens,
                     long safeUsableTokens,
                     long controlPlaneTokens,
                     int renderProbeCount,
                     long renderTokens,
                     long plannedTokens,
                     long safeHeadroomAfterPlan) {
    }

    private record ModelBudgetView(String modelId,
                                   long remainingTokens,
                                   long safeUsableTokens) {
    }
}
