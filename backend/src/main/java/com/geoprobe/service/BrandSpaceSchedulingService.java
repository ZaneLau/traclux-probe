package com.geoprobe.service;

import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.knowledge.repository.KnowledgeAssetScheduleStateRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetScheduleStateMaterializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

@Service
@RequiredArgsConstructor
public class BrandSpaceSchedulingService {

    private static final int DEFAULT_PER_BRAND_LIMIT = 50;
    private static final int DEFAULT_PREVIEW_BUDGET = 50;
    private static final int DEFAULT_REGULAR_TARGET = 12;
    private static final int DEFAULT_REGULAR_EXPANSION_TARGET = 20;
    private static final int DEFAULT_EDITED_TARGET = 16;
    private static final int DEFAULT_EDITED_EXPANSION_TARGET = 24;
    private static final int DEFAULT_COLD_START_TARGET = 24;
    private static final int DEFAULT_COLD_START_CAP = 50;
    private static final double FALLBACK_BRAND_WEIGHT = 0.55d;
    private static final double DEFAULT_COLD_START_TOPIC_PRIORITY = 1.0d;
    private static final double HIGH_INTENSITY_WEIGHT_PENALTY = 0.08d;
    private static final double HIGH_INTENSITY_BUDGET_FACTOR = 0.85d;

    private final ObjectProvider<KnowledgeAssetScheduleStateRepository> knowledgeAssetScheduleStateRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetScheduleStateMaterializationService> knowledgeAssetScheduleStateMaterializationServiceProvider;

    public ExecutionPlan planPreview(List<BrandSpaceTopicPayload> topics,
                                     List<BrandSpacePromptPayload> prompts,
                                     ServiceAccessView serviceAccess) {
        int perBrandLimit = resolvePerBrandLimit(serviceAccess, DEFAULT_PREVIEW_BUDGET);
        List<TopicInput> draftTopicInputs = buildDraftTopicInputs(topics, prompts);
        BrandProfile brandProfile = buildBrandProfile(null, null, draftTopicInputs, perBrandLimit);
        ExecutionPlan plan = planSingleBrand(
                TriggerMode.INTERACTIVE_PREVIEW,
                draftTopicInputs,
                brandProfile,
                perBrandLimit,
                perBrandLimit
        );
        plan = plan.withExecutionPrompts(buildDraftExecutionPrompts(plan.selectedTopics(), topics, prompts));
        return plan.withAccountContext(
                buildSingleBrandAccountBudgetSnapshot(serviceAccess, perBrandLimit, perBrandLimit),
                brandProfile
        );
    }

    public ExecutionPlan planCreateInitialRun(List<BrandSpaceTopicPayload> topics,
                                              List<BrandSpacePromptPayload> prompts,
                                              ServiceAccessView serviceAccess,
                                              Integer requestedTargetProbeCount) {
        int perBrandLimit = resolvePerBrandLimit(serviceAccess, DEFAULT_PER_BRAND_LIMIT);
        int requestedBudget = normalizeRequestedTargetProbeCount(requestedTargetProbeCount, perBrandLimit);
        List<TopicInput> draftTopicInputs = buildDraftTopicInputs(topics, prompts);
        BrandProfile brandProfile = buildBrandProfile(null, null, draftTopicInputs, perBrandLimit);
        ExecutionPlan plan = planSingleBrand(
                TriggerMode.CREATE_INITIAL_RUN,
                draftTopicInputs,
                brandProfile,
                requestedBudget,
                perBrandLimit
        );
        plan = plan.withExecutionPrompts(buildDraftExecutionPrompts(plan.selectedTopics(), topics, prompts));
        return plan.withAccountContext(
                buildSingleBrandAccountBudgetSnapshot(serviceAccess, requestedBudget, perBrandLimit),
                brandProfile
        );
    }

    public ExecutionPlan planCurrentConfigRun(String brandScopeId,
                                              BrandSpaceConfigView configView,
                                              DiagnosisTask latestTask,
                                              ServiceAccessView serviceAccess,
                                              Integer requestedTargetProbeCount,
                                              boolean manualExecuteOnce) {
        if (configView == null || !StringUtils.hasText(brandScopeId)) {
            return ExecutionPlan.skipped(
                    manualExecuteOnce ? "manual_execute_once" : "scheduled_tracking",
                    "当前品牌空间没有可执行配置。"
            );
        }

        KnowledgeAssetScheduleStateRepository repository = knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable();
        ScheduleTruthSnapshot scheduleTruthSnapshot = loadScheduleTruthSnapshot(repository, brandScopeId.trim());
        List<TopicInput> topicInputs = buildConfigTopicInputs(configView, scheduleTruthSnapshot.topicStates());
        if (topicInputs.isEmpty()) {
            return ExecutionPlan.skipped(
                    manualExecuteOnce ? "manual_execute_once" : "scheduled_tracking",
                    "当前品牌空间没有可执行配置。"
            );
        }

        int perBrandLimit = resolvePerBrandLimit(serviceAccess, DEFAULT_PER_BRAND_LIMIT);
        int requestedBudget = normalizeRequestedTargetProbeCount(requestedTargetProbeCount, perBrandLimit);
        BrandProfile brandProfile = buildBrandProfile(
                scheduleTruthSnapshot.brandState(),
                latestTask,
                topicInputs,
                perBrandLimit
        );
        ExecutionPlan plan = planSingleBrand(
                manualExecuteOnce ? TriggerMode.MANUAL_EXECUTE_ONCE : TriggerMode.SCHEDULED_TRACKING,
                topicInputs,
                brandProfile,
                requestedBudget,
                perBrandLimit
        );
        plan = plan.withExecutionPrompts(buildConfigExecutionPrompts(plan.selectedTopics(), configView));
        return plan.withAccountContext(
                buildSingleBrandAccountBudgetSnapshot(serviceAccess, requestedBudget, perBrandLimit),
                brandProfile
        );
    }

    private int normalizeRequestedTargetProbeCount(Integer requestedTargetProbeCount, int perBrandLimit) {
        if (requestedTargetProbeCount == null) {
            return Math.max(1, perBrandLimit);
        }
        return Math.max(1, Math.min(requestedTargetProbeCount, Math.max(1, perBrandLimit)));
    }

    public Map<String, ExecutionPlan> planScheduledAccount(List<ScheduledScopeCandidate> candidates,
                                                           AccountCreditSnapshot accountCreditSnapshot) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        KnowledgeAssetScheduleStateRepository repository = knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable();
        List<ScheduledBrandInput> inputs = new ArrayList<>();
        for (ScheduledScopeCandidate candidate : candidates) {
            if (candidate == null || candidate.configView() == null || !StringUtils.hasText(candidate.brandScopeId())) {
                continue;
            }
            ScheduleTruthSnapshot scheduleTruthSnapshot = loadScheduleTruthSnapshot(repository, candidate.brandScopeId());
            KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState = scheduleTruthSnapshot.brandState();
            List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateView> topicStates = scheduleTruthSnapshot.topicStates();
            int perBrandLimit = resolvePerBrandLimit(candidate.serviceAccess(), DEFAULT_PER_BRAND_LIMIT);
            List<TopicInput> topicInputs = buildConfigTopicInputs(candidate.configView(), topicStates);
            if (topicInputs.isEmpty()) {
                continue;
            }
            inputs.add(new ScheduledBrandInput(
                    candidate.brandScopeId(),
                    candidate.configView(),
                    candidate.latestTask(),
                    brandState,
                    topicInputs,
                    perBrandLimit
            ));
        }
        if (inputs.isEmpty()) {
            return Map.of();
        }

        AccountCreditSnapshot normalizedAccountCredits = accountCreditSnapshot == null
                ? AccountCreditSnapshot.empty(inputs.size())
                : accountCreditSnapshot.normalize(inputs.size());
        int availableCredits = normalizedAccountCredits.availableCredits();
        LinkedHashMap<String, Integer> allocatedBudgets = allocateBudgets(inputs, availableCredits);
        LinkedHashMap<String, ExecutionPlan> plans = new LinkedHashMap<>();
        for (ScheduledBrandInput input : inputs) {
            int allocatedBudget = allocatedBudgets.getOrDefault(input.brandScopeId(), 0);
            BrandProfile brandProfile = buildBrandProfile(input.brandState(), input.latestTask(), input.topicInputs(), input.perBrandLimit());
            ExecutionPlan plan = planSingleBrand(
                    TriggerMode.SCHEDULED_TRACKING,
                    input.topicInputs(),
                    brandProfile,
                    allocatedBudget,
                    input.perBrandLimit()
            );
            plan = plan.withExecutionPrompts(buildConfigExecutionPrompts(plan.selectedTopics(), input.configView()));
            plans.put(input.brandScopeId(), plan.withAccountContext(normalizedAccountCredits, brandProfile));
        }
        return Map.copyOf(plans);
    }

    private LinkedHashMap<String, Integer> allocateBudgets(List<ScheduledBrandInput> inputs, int remainingBudget) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }
        if (remainingBudget <= 0) {
            for (ScheduledBrandInput input : inputs) {
                result.put(input.brandScopeId(), 0);
            }
            return result;
        }

        List<BudgetNeed> needs = new ArrayList<>();
        int coreBudgetTotal = 0;
        for (ScheduledBrandInput input : inputs) {
            BrandProfile profile = buildBrandProfile(input.brandState(), input.latestTask(), input.topicInputs(), input.perBrandLimit());
            coreBudgetTotal += profile.desiredBudget();
            needs.add(new BudgetNeed(
                    input.brandScopeId(),
                    profile.desiredBudget(),
                    profile.expansionBudget(),
                    profile.allocationWeight()
            ));
            result.put(input.brandScopeId(), 0);
        }
        if (coreBudgetTotal >= remainingBudget) {
            return allocateWeightedLimited(needs, remainingBudget, BudgetNeed::desiredBudget);
        }

        for (BudgetNeed need : needs) {
            result.put(need.brandScopeId(), need.desiredBudget());
        }
        int bonusBudget = remainingBudget - coreBudgetTotal;
        if (bonusBudget <= 0) {
            return result;
        }

        int allocatedBonus = allocateWeightedBonus(needs, result, bonusBudget);
        if (allocatedBonus <= 0) {
            return result;
        }
        return result;
    }

    private LinkedHashMap<String, Integer> allocateWeightedLimited(List<BudgetNeed> needs,
                                                                   int availableBudget,
                                                                   ToIntFunction<BudgetNeed> limitExtractor) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (needs == null || needs.isEmpty() || availableBudget <= 0) {
            return result;
        }

        double weightTotal = resolveWeightTotal(needs);
        List<BudgetShare> shares = new ArrayList<>();
        int allocated = 0;
        for (BudgetNeed need : needs) {
            int limit = Math.max(0, limitExtractor.applyAsInt(need));
            double weight = need.allocationWeight() <= 0.0d ? 1.0d : need.allocationWeight();
            double raw = availableBudget * weight / weightTotal;
            int floor = Math.min(limit, Math.max(0, (int) Math.floor(raw)));
            result.put(need.brandScopeId(), floor);
            allocated += floor;
            shares.add(new BudgetShare(need.brandScopeId(), raw - floor));
        }
        if (allocated >= availableBudget) {
            return result;
        }
        int leftovers = availableBudget - allocated;
        List<BudgetShare> orderedShares = orderBudgetShares(shares);
        while (leftovers > 0) {
            boolean assigned = false;
            for (BudgetShare share : orderedShares) {
                if (leftovers <= 0) {
                    break;
                }
                BudgetNeed need = findBudgetNeed(needs, share.brandScopeId());
                if (need == null) {
                    continue;
                }
                int limit = Math.max(0, limitExtractor.applyAsInt(need));
                int current = result.getOrDefault(share.brandScopeId(), 0);
                if (current >= limit) {
                    continue;
                }
                result.put(share.brandScopeId(), current + 1);
                leftovers--;
                assigned = true;
            }
            if (!assigned) {
                break;
            }
        }
        return result;
    }

    private int allocateWeightedBonus(List<BudgetNeed> needs,
                                      LinkedHashMap<String, Integer> result,
                                      int availableBonusBudget) {
        double weightTotal = resolveWeightTotal(needs);
        List<BudgetShare> shares = new ArrayList<>();
        int allocated = 0;
        for (BudgetNeed need : needs) {
            int current = result.getOrDefault(need.brandScopeId(), 0);
            int headroom = Math.max(0, need.expansionBudget() - current);
            if (headroom <= 0) {
                continue;
            }
            double weight = need.allocationWeight() <= 0.0d ? 1.0d : need.allocationWeight();
            double raw = availableBonusBudget * weight / weightTotal;
            int floor = Math.min(headroom, Math.max(0, (int) Math.floor(raw)));
            if (floor > 0) {
                result.put(need.brandScopeId(), current + floor);
                current += floor;
                allocated += floor;
            }
            shares.add(new BudgetShare(need.brandScopeId(), raw - floor));
        }
        int leftovers = availableBonusBudget - allocated;
        if (leftovers <= 0) {
            return allocated;
        }
        List<BudgetShare> orderedShares = orderBudgetShares(shares);
        while (leftovers > 0) {
            boolean assigned = false;
            for (BudgetShare share : orderedShares) {
                if (leftovers <= 0) {
                    break;
                }
                BudgetNeed need = findBudgetNeed(needs, share.brandScopeId());
                if (need == null) {
                    continue;
                }
                int current = result.getOrDefault(share.brandScopeId(), 0);
                if (current >= need.expansionBudget()) {
                    continue;
                }
                result.put(share.brandScopeId(), current + 1);
                leftovers--;
                allocated++;
                assigned = true;
            }
            if (!assigned) {
                break;
            }
        }
        return allocated;
    }

    private double resolveWeightTotal(List<BudgetNeed> needs) {
        double weightTotal = needs.stream()
                .mapToDouble(need -> need.allocationWeight() <= 0.0d ? 1.0d : need.allocationWeight())
                .sum();
        return weightTotal <= 0.0d ? needs.size() : weightTotal;
    }

    private List<BudgetShare> orderBudgetShares(List<BudgetShare> shares) {
        return shares.stream()
                .sorted(Comparator.comparing(BudgetShare::fractionalRemainder).reversed()
                        .thenComparing(BudgetShare::brandScopeId))
                .toList();
    }

    private BudgetNeed findBudgetNeed(List<BudgetNeed> needs, String brandScopeId) {
        if (needs == null || !StringUtils.hasText(brandScopeId)) {
            return null;
        }
        for (BudgetNeed need : needs) {
            if (brandScopeId.equals(need.brandScopeId())) {
                return need;
            }
        }
        return null;
    }

    private ExecutionPlan planSingleBrand(TriggerMode triggerMode,
                                          List<TopicInput> topicInputs,
                                          BrandProfile brandProfile,
                                          int availableBudget,
                                          int perBrandLimit) {
        List<TopicInput> activeTopics = topicInputs == null
                ? List.of()
                : topicInputs.stream().filter(TopicInput::enabled).toList();
        if (activeTopics.isEmpty()) {
            return ExecutionPlan.skipped(triggerMode.code(), "当前没有可执行主题");
        }

        int budget = clampBudget(availableBudget, perBrandLimit);
        if (budget <= 0) {
            return ExecutionPlan.skipped(
                    triggerMode.code(),
                    triggerMode == TriggerMode.SCHEDULED_TRACKING
                            ? "今天因为可用积分已用尽，全部主题顺延到后续轮次"
                            : "当前可用积分不足，暂时无法生成本次执行计划"
            );
        }
        int effectiveTarget = Math.min(brandProfile.expansionBudget(), budget);
        TopicAllocationBundle allocationBundle = allocateTopics(activeTopics, effectiveTarget);
        boolean quotaLimited = effectiveTarget < brandProfile.desiredBudget();
        boolean expandedBySpareBudget = effectiveTarget > brandProfile.desiredBudget();
        String coverageStrategy = resolveCoverageStrategy(triggerMode, brandProfile, quotaLimited, expandedBySpareBudget);
        String humanReason = buildHumanReason(triggerMode, brandProfile, quotaLimited, expandedBySpareBudget);
        return new ExecutionPlan(
                triggerMode.code(),
                effectiveTarget,
                allocationBundle.selectedTopics(),
                allocationBundle.deferredTopics(),
                coverageStrategy,
                humanReason,
                brandProfile.coldStart(),
                brandProfile.justEdited(),
                quotaLimited,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private BrandProfile buildBrandProfile(KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState,
                                           DiagnosisTask latestTask,
                                           List<TopicInput> topicInputs,
                                           int perBrandLimit) {
        List<TopicInput> activeTopics = topicInputs == null
                ? List.of()
                : topicInputs.stream().filter(TopicInput::enabled).toList();
        int activeTopicCount = activeTopics == null ? 0 : activeTopics.size();
        boolean coldStart = brandState != null
                ? brandState.coldStartFlag()
                : latestTask == null;
        boolean justEdited = brandState != null && brandState.justEditedFlag();
        int coveredTopicCount7d = brandState != null && brandState.coveredTopicCount7d() != null
                ? Math.max(0, brandState.coveredTopicCount7d())
                : countCoveredTopics7d(activeTopics);
        boolean highIntensity = brandState != null
                && brandState.last1dProbeCount() != null
                && brandState.last1dProbeCount() >= Math.max(DEFAULT_REGULAR_TARGET, activeTopicCount * 2);
        double allocationWeight = brandState != null && brandState.suggestedBrandWeight() != null
                ? clamp01(brandState.suggestedBrandWeight())
                : FALLBACK_BRAND_WEIGHT;
        if (coldStart) {
            allocationWeight = clamp01(allocationWeight + 0.30d);
        }
        if (justEdited) {
            allocationWeight = clamp01(allocationWeight + 0.10d);
        }
        if (highIntensity) {
            allocationWeight = clamp01(allocationWeight - HIGH_INTENSITY_WEIGHT_PENALTY);
        }
        int weeklyCoveredTopicCount = Math.min(coveredTopicCount7d, activeTopicCount);
        int weeklyGapTopicCount = Math.max(0, activeTopicCount - weeklyCoveredTopicCount);
        double weeklyCoverageRatio = activeTopicCount <= 0 ? 0.0d : weeklyCoveredTopicCount / (double) activeTopicCount;
        int desiredBudget = resolveDesiredBudget(activeTopicCount, weeklyGapTopicCount, perBrandLimit, coldStart, justEdited, highIntensity);
        int expansionBudget = resolveExpansionBudget(activeTopicCount, weeklyGapTopicCount, perBrandLimit, coldStart, justEdited, highIntensity, desiredBudget);
        return new BrandProfile(
                coldStart,
                justEdited,
                highIntensity,
                allocationWeight,
                desiredBudget,
                expansionBudget,
                activeTopicCount,
                weeklyCoveredTopicCount,
                weeklyGapTopicCount,
                weeklyCoverageRatio
        );
    }

    private int resolveDesiredBudget(int activeTopicCount,
                                     int weeklyGapTopicCount,
                                     int perBrandLimit,
                                     boolean coldStart,
                                     boolean justEdited,
                                     boolean highIntensity) {
        int safeTopicCount = Math.max(activeTopicCount, 1);
        int desiredBudget;
        if (coldStart) {
            desiredBudget = Math.min(perBrandLimit, Math.min(DEFAULT_COLD_START_CAP, Math.max(DEFAULT_COLD_START_TARGET, safeTopicCount * 4)));
        } else if (justEdited) {
            desiredBudget = Math.min(perBrandLimit, Math.max(DEFAULT_EDITED_TARGET, safeTopicCount * 2 + Math.max(weeklyGapTopicCount, 1) * 2));
        } else {
            desiredBudget = Math.min(perBrandLimit, Math.max(DEFAULT_REGULAR_TARGET, safeTopicCount + weeklyGapTopicCount * 2 + (weeklyGapTopicCount > 0 ? 2 : 0)));
        }
        if (highIntensity && !coldStart) {
            desiredBudget = Math.max(minimumHealthyBudget(safeTopicCount), (int) Math.round(desiredBudget * HIGH_INTENSITY_BUDGET_FACTOR));
        }
        return Math.max(1, desiredBudget);
    }

    private int resolveExpansionBudget(int activeTopicCount,
                                       int weeklyGapTopicCount,
                                       int perBrandLimit,
                                       boolean coldStart,
                                       boolean justEdited,
                                       boolean highIntensity,
                                       int desiredBudget) {
        int safeTopicCount = Math.max(activeTopicCount, 1);
        int expansionBudget;
        if (coldStart) {
            expansionBudget = Math.min(perBrandLimit, Math.min(DEFAULT_COLD_START_CAP, Math.max(DEFAULT_COLD_START_TARGET, safeTopicCount * 5)));
        } else if (justEdited) {
            expansionBudget = Math.min(perBrandLimit, Math.max(DEFAULT_EDITED_EXPANSION_TARGET, Math.max(safeTopicCount * 4, safeTopicCount * 2 + Math.max(weeklyGapTopicCount, 1) * 3)));
        } else {
            expansionBudget = Math.min(perBrandLimit, Math.max(DEFAULT_REGULAR_EXPANSION_TARGET, Math.max(safeTopicCount * 3, safeTopicCount * 2 + Math.max(weeklyGapTopicCount, 1) * 2)));
        }
        return Math.max(desiredBudget, expansionBudget);
    }

    private int minimumHealthyBudget(int activeTopicCount) {
        int safeTopicCount = Math.max(activeTopicCount, 1);
        return Math.max(DEFAULT_REGULAR_TARGET, safeTopicCount * 2);
    }

    private TopicAllocationBundle allocateTopics(List<TopicInput> activeTopics, int targetProbeCount) {
        if (activeTopics == null || activeTopics.isEmpty() || targetProbeCount <= 0) {
            return new TopicAllocationBundle(List.of(), List.of());
        }
        List<TopicInput> orderedTopics = activeTopics.stream()
                .sorted(Comparator.comparing(TopicInput::priorityScore).reversed()
                        .thenComparing(TopicInput::seqNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TopicInput::topicName))
                .toList();
        LinkedHashMap<String, Integer> allocationByTopicId = new LinkedHashMap<>();
        int remaining = targetProbeCount;
        for (TopicInput topic : orderedTopics) {
            if (remaining <= 0) {
                break;
            }
            allocationByTopicId.put(topic.topicId(), 1);
            remaining--;
        }
        if (remaining > 0) {
            double totalWeight = orderedTopics.stream().mapToDouble(topic -> Math.max(0.25d, topic.priorityScore())).sum();
            LinkedHashMap<String, Double> remainders = new LinkedHashMap<>();
            int grantedExtra = 0;
            for (TopicInput topic : orderedTopics) {
                double rawExtra = remaining * (Math.max(0.25d, topic.priorityScore()) / Math.max(totalWeight, 0.25d));
                int floor = (int) Math.floor(rawExtra);
                if (floor > 0) {
                    allocationByTopicId.merge(topic.topicId(), floor, Integer::sum);
                    grantedExtra += floor;
                }
                remainders.put(topic.topicId(), rawExtra - floor);
            }
            int extraLeft = remaining - grantedExtra;
            List<Map.Entry<String, Double>> remainderEntries = remainders.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .toList();
            while (extraLeft > 0) {
                for (Map.Entry<String, Double> entry : remainderEntries) {
                    if (extraLeft <= 0) {
                        break;
                    }
                    allocationByTopicId.merge(entry.getKey(), 1, Integer::sum);
                    extraLeft--;
                }
            }
        }

        List<TopicDecision> selectedTopics = new ArrayList<>();
        List<TopicDecision> deferredTopics = new ArrayList<>();
        for (TopicInput topic : orderedTopics) {
            int allocated = allocationByTopicId.getOrDefault(topic.topicId(), 0);
            TopicDecision decision = new TopicDecision(
                    topic.topicId(),
                    topic.topicName(),
                    allocated,
                    topic.priorityScore(),
                    buildTopicReasonTags(topic)
            );
            if (allocated > 0) {
                selectedTopics.add(decision);
            } else {
                deferredTopics.add(decision);
            }
        }
        return new TopicAllocationBundle(List.copyOf(selectedTopics), List.copyOf(deferredTopics));
    }

    private List<String> buildTopicReasonTags(TopicInput topic) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (topic.coldStart()) {
            tags.add("冷启动");
        }
        if (topic.justEdited()) {
            tags.add("刚编辑");
        }
        if (topic.priorityScore() >= 0.75d) {
            tags.add("高优先级");
        } else if (topic.priorityScore() >= 0.50d) {
            tags.add("补债");
        } else {
            tags.add("均衡覆盖");
        }
        return List.copyOf(tags);
    }

    private String resolveCoverageStrategy(TriggerMode triggerMode,
                                           BrandProfile brandProfile,
                                           boolean quotaLimited,
                                           boolean expandedBySpareBudget) {
        if (triggerMode == TriggerMode.INTERACTIVE_PREVIEW || triggerMode == TriggerMode.CREATE_INITIAL_RUN || brandProfile.coldStart()) {
            return expandedBySpareBudget ? "cold_start_wide_coverage_plus_depth" : "cold_start_wide_coverage";
        }
        if (brandProfile.justEdited()) {
            if (quotaLimited) {
                return "edited_topic_quota_limited";
            }
            return expandedBySpareBudget ? "edited_topic_recovery_plus_depth" : "edited_topic_recovery";
        }
        if (quotaLimited) {
            return "quota_limited_balanced_coverage";
        }
        return expandedBySpareBudget ? "balanced_topic_coverage_plus_depth" : "balanced_topic_coverage";
    }

    private String buildHumanReason(TriggerMode triggerMode,
                                    BrandProfile brandProfile,
                                    boolean quotaLimited,
                                    boolean expandedBySpareBudget) {
        List<String> reasons = new ArrayList<>();
        if (triggerMode == TriggerMode.INTERACTIVE_PREVIEW || triggerMode == TriggerMode.CREATE_INITIAL_RUN || brandProfile.coldStart()) {
            reasons.add("今天是新品牌首跑，优先做宽覆盖");
        } else if (brandProfile.justEdited()) {
            reasons.add("今天优先处理你刚新增或刚修改的主题");
        } else {
            reasons.add("今天优先补齐最近覆盖不足的主题");
        }
        if (quotaLimited) {
            reasons.add("今天因为可用积分有限，部分主题延后到后续轮次");
        } else if (expandedBySpareBudget) {
            reasons.add("今天账户还有可用积分，会在已选主题上补更多跟踪深度");
        } else if (brandProfile.highIntensity() && !brandProfile.coldStart()) {
            reasons.add("这个品牌昨天已经有较高覆盖，今天先保留均衡节奏");
        }
        if (!brandProfile.coldStart() && brandProfile.weeklyGapTopicCount() > 0) {
            reasons.add("本周仍有主题覆盖缺口，需要优先补齐");
        }
        if (reasons.isEmpty()) {
            return "今天按默认均衡覆盖策略执行";
        }
        return String.join("；", reasons.stream().limit(2).toList());
    }

    private int countCoveredTopics7d(List<TopicInput> activeTopics) {
        if (activeTopics == null || activeTopics.isEmpty()) {
            return 0;
        }
        int coveredCount = 0;
        for (TopicInput topic : activeTopics) {
            if (topic != null && topic.last7dProbeCount() > 0) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    private List<TopicInput> buildDraftTopicInputs(List<BrandSpaceTopicPayload> topics,
                                                   List<BrandSpacePromptPayload> prompts) {
        Map<String, Integer> enabledPromptCountByTopicId = new LinkedHashMap<>();
        if (prompts != null) {
            for (BrandSpacePromptPayload prompt : prompts) {
                if (prompt == null || !Boolean.TRUE.equals(prompt.getEnabled()) || !StringUtils.hasText(prompt.getTopicTempId())) {
                    continue;
                }
                enabledPromptCountByTopicId.merge(prompt.getTopicTempId().trim(), 1, Integer::sum);
            }
        }
        List<TopicInput> inputs = new ArrayList<>();
        if (topics == null) {
            return inputs;
        }
        for (int index = 0; index < topics.size(); index++) {
            BrandSpaceTopicPayload topic = topics.get(index);
            if (topic == null || !StringUtils.hasText(topic.getTempId()) || !StringUtils.hasText(topic.getName())) {
                continue;
            }
            inputs.add(new TopicInput(
                    topic.getTempId().trim(),
                    topic.getName().trim(),
                    Boolean.TRUE.equals(topic.getEnabled()),
                    index + 1,
                    0.60d,
                    true,
                    false,
                    enabledPromptCountByTopicId.getOrDefault(topic.getTempId().trim(), 0),
                    0
            ));
        }
        return List.copyOf(inputs);
    }

    private List<ExecutionPromptPlan> buildDraftExecutionPrompts(List<TopicDecision> selectedTopics,
                                                                 List<BrandSpaceTopicPayload> topics,
                                                                 List<BrandSpacePromptPayload> prompts) {
        Map<String, String> topicNameById = new LinkedHashMap<>();
        if (topics != null) {
            for (BrandSpaceTopicPayload topic : topics) {
                if (topic == null || !StringUtils.hasText(topic.getTempId())) {
                    continue;
                }
                topicNameById.put(topic.getTempId().trim(), normalizeText(topic.getName()));
            }
        }
        Map<String, List<PromptCandidate>> promptCandidatesByTopicId = new LinkedHashMap<>();
        if (prompts != null) {
            for (BrandSpacePromptPayload prompt : prompts) {
                if (prompt == null
                        || !Boolean.TRUE.equals(prompt.getEnabled())
                        || !StringUtils.hasText(prompt.getTopicTempId())
                        || !StringUtils.hasText(prompt.getText())) {
                    continue;
                }
                String topicId = prompt.getTopicTempId().trim();
                promptCandidatesByTopicId.computeIfAbsent(topicId, ignored -> new ArrayList<>())
                        .add(new PromptCandidate(
                                normalizeText(prompt.getTempId()),
                                topicId,
                                firstText(topicNameById.get(topicId), null),
                                prompt.getText().trim()
                        ));
            }
        }
        return buildExecutionPrompts(selectedTopics, promptCandidatesByTopicId);
    }

    private List<ExecutionPromptPlan> buildConfigExecutionPrompts(List<TopicDecision> selectedTopics,
                                                                  BrandSpaceConfigView configView) {
        Map<String, List<PromptCandidate>> promptCandidatesByTopicId = new LinkedHashMap<>();
        if (configView != null && configView.getPrompts() != null) {
            for (BrandSpaceConfigView.PromptView prompt : configView.getPrompts()) {
                if (prompt == null
                        || !Boolean.TRUE.equals(prompt.getEnabled())
                        || !StringUtils.hasText(prompt.getTopicId())
                        || !StringUtils.hasText(prompt.getText())) {
                    continue;
                }
                String topicId = prompt.getTopicId().trim();
                promptCandidatesByTopicId.computeIfAbsent(topicId, ignored -> new ArrayList<>())
                        .add(new PromptCandidate(
                                normalizeText(prompt.getPromptId()),
                                topicId,
                                firstText(prompt.getTopicName(), null),
                                prompt.getText().trim()
                        ));
            }
        }
        return buildExecutionPrompts(selectedTopics, promptCandidatesByTopicId);
    }

    private List<ExecutionPromptPlan> buildExecutionPrompts(List<TopicDecision> selectedTopics,
                                                            Map<String, List<PromptCandidate>> promptCandidatesByTopicId) {
        if (selectedTopics == null || selectedTopics.isEmpty() || promptCandidatesByTopicId == null || promptCandidatesByTopicId.isEmpty()) {
            return List.of();
        }
        List<ExecutionPromptPlan> executionPrompts = new ArrayList<>();
        int seqNo = 1;
        for (TopicDecision topicDecision : selectedTopics) {
            if (topicDecision == null || !StringUtils.hasText(topicDecision.topicId()) || topicDecision.allocatedProbeCount() <= 0) {
                continue;
            }
            List<PromptCandidate> candidates = promptCandidatesByTopicId.get(topicDecision.topicId());
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            for (int index = 0; index < topicDecision.allocatedProbeCount(); index++) {
                PromptCandidate candidate = candidates.get(index % candidates.size());
                executionPrompts.add(new ExecutionPromptPlan(
                        seqNo++,
                        candidate.promptId(),
                        firstText(candidate.topicId(), topicDecision.topicId()),
                        firstText(candidate.topicName(), topicDecision.topicName()),
                        candidate.promptText()
                ));
            }
        }
        return executionPrompts.isEmpty() ? List.of() : List.copyOf(executionPrompts);
    }

    private ScheduleTruthSnapshot loadScheduleTruthSnapshot(KnowledgeAssetScheduleStateRepository repository,
                                                            String brandScopeId) {
        if (repository == null || !StringUtils.hasText(brandScopeId)) {
            return new ScheduleTruthSnapshot(null, List.of());
        }
        KnowledgeAssetScheduleStateMaterializationService materializationService =
                knowledgeAssetScheduleStateMaterializationServiceProvider.getIfAvailable();
        if (materializationService != null) {
            materializationService.materializeScope(brandScopeId);
        }
        KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState = repository.findBrandState(brandScopeId);
        List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateView> topicStates = repository.listTopicStates(brandScopeId);
        return new ScheduleTruthSnapshot(
                brandState,
                topicStates == null ? List.of() : List.copyOf(topicStates)
        );
    }

    private List<TopicInput> buildConfigTopicInputs(BrandSpaceConfigView configView,
                                                    List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateView> topicStates) {
        Map<String, KnowledgeAssetScheduleStateRepository.TopicScheduleStateView> topicStateById = new LinkedHashMap<>();
        if (topicStates != null) {
            for (KnowledgeAssetScheduleStateRepository.TopicScheduleStateView state : topicStates) {
                if (state != null && StringUtils.hasText(state.topicCatalogId())) {
                    topicStateById.put(state.topicCatalogId(), state);
                }
            }
        }
        List<TopicInput> inputs = new ArrayList<>();
        List<BrandSpaceConfigView.TopicView> topics = configView == null ? List.of() : configView.getTopics();
        if (topics == null) {
            return inputs;
        }
        for (BrandSpaceConfigView.TopicView topic : topics) {
            if (topic == null || !StringUtils.hasText(topic.getTopicId()) || !StringUtils.hasText(topic.getName())) {
                continue;
            }
            KnowledgeAssetScheduleStateRepository.TopicScheduleStateView state = topicStateById.get(topic.getTopicId());
            double priorityScore = state != null && state.suggestedPriorityScore() != null
                    ? clamp01(state.suggestedPriorityScore())
                    : DEFAULT_COLD_START_TOPIC_PRIORITY;
            int enabledPromptCount = topic.getEnabledPromptCount() == null ? 0 : Math.max(0, topic.getEnabledPromptCount());
            inputs.add(new TopicInput(
                    topic.getTopicId().trim(),
                    topic.getName().trim(),
                    Boolean.TRUE.equals(topic.getEnabled()) && enabledPromptCount > 0,
                    topic.getSeqNo(),
                    priorityScore,
                    state == null || state.coldStartFlag(),
                    state != null && state.justEditedFlag(),
                    enabledPromptCount,
                    state == null || state.last7dProbeCount() == null ? 0 : Math.max(0, state.last7dProbeCount())
            ));
        }
        return List.copyOf(inputs);
    }

    private int resolvePerBrandLimit(ServiceAccessView serviceAccess, int fallback) {
        ServiceAccessView.Limits limits = serviceAccess == null ? null : serviceAccess.getLimits();
        Integer limit = limits == null ? null : limits.getPromptLimitPerBrandSpace();
        if (limit != null && limit > 0) {
            return limit;
        }
        return Math.max(1, fallback);
    }

    private AccountCreditSnapshot buildSingleBrandAccountBudgetSnapshot(ServiceAccessView serviceAccess,
                                                                        int targetProbeCount,
                                                                        int perBrandLimit) {
        return new AccountCreditSnapshot(
                Math.max(0, targetProbeCount),
                1,
                1
        ).normalize(1);
    }

    private int clampBudget(int availableBudget, int perBrandLimit) {
        if (availableBudget <= 0 || perBrandLimit <= 0) {
            return 0;
        }
        return Math.min(availableBudget, perBrandLimit);
    }

    private double clamp01(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return 0.0d;
        }
        return Math.min(value, 1.0d);
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return normalizeText(fallback);
    }

    public record ScheduledScopeCandidate(String brandScopeId,
                                          BrandSpaceConfigView configView,
                                          DiagnosisTask latestTask,
                                          ServiceAccessView serviceAccess) {
    }

    public record ExecutionPlan(String triggerMode,
                                int targetProbeCount,
                                List<TopicDecision> selectedTopics,
                                List<TopicDecision> deferredTopics,
                                String coverageStrategy,
                                String humanReason,
                                boolean coldStart,
                                boolean justEdited,
                                boolean quotaLimited,
                                Integer accountAvailableCredits,
                                Integer accountBrandCount,
                                Integer brandCoreProbeCount,
                                Integer brandExpansionProbeCount,
                                Integer weeklyTargetTopicCount,
                                Integer weeklyCoveredTopicCount,
                                Integer weeklyGapTopicCount,
                                Double weeklyCoverageRatio,
                                List<ExecutionPromptPlan> executionPrompts) {

        public ExecutionPlan {
            triggerMode = StringUtils.hasText(triggerMode) ? triggerMode.trim() : "unknown";
            targetProbeCount = Math.max(0, targetProbeCount);
            selectedTopics = selectedTopics == null ? List.of() : List.copyOf(selectedTopics);
            deferredTopics = deferredTopics == null ? List.of() : List.copyOf(deferredTopics);
            coverageStrategy = StringUtils.hasText(coverageStrategy) ? coverageStrategy.trim() : "unknown";
            humanReason = StringUtils.hasText(humanReason) ? humanReason.trim() : "未提供调度说明";
            executionPrompts = executionPrompts == null ? List.of() : List.copyOf(executionPrompts);
        }

        public ExecutionPlan(String triggerMode,
                             int targetProbeCount,
                             List<TopicDecision> selectedTopics,
                             List<TopicDecision> deferredTopics,
                             String coverageStrategy,
                             String humanReason,
                             boolean coldStart,
                             boolean justEdited,
                             boolean quotaLimited,
                             Integer accountAvailableCredits,
                             Integer accountBrandCount,
                             Integer brandCoreProbeCount,
                             Integer brandExpansionProbeCount,
                             Integer weeklyTargetTopicCount,
                             Integer weeklyCoveredTopicCount,
                             Integer weeklyGapTopicCount,
                             Double weeklyCoverageRatio) {
            this(triggerMode,
                    targetProbeCount,
                    selectedTopics,
                    deferredTopics,
                    coverageStrategy,
                    humanReason,
                    coldStart,
                    justEdited,
                    quotaLimited,
                    accountAvailableCredits,
                    accountBrandCount,
                    brandCoreProbeCount,
                    brandExpansionProbeCount,
                    weeklyTargetTopicCount,
                    weeklyCoveredTopicCount,
                    weeklyGapTopicCount,
                    weeklyCoverageRatio,
                    List.of());
        }

        public static ExecutionPlan skipped(String triggerMode, String humanReason) {
            return new ExecutionPlan(triggerMode, 0, List.of(), List.of(), "skipped", humanReason, false, false, true, null, null, null, null, null, null, null, null, List.of());
        }

        public boolean shouldExecute() {
            return targetProbeCount > 0 && !selectedTopics.isEmpty();
        }

        public List<String> selectedTopicIds() {
            return selectedTopics.stream()
                    .map(TopicDecision::topicId)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public List<String> selectedTopicNames() {
            return selectedTopics.stream()
                    .map(TopicDecision::topicName)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public List<String> deferredTopicIds() {
            return deferredTopics.stream()
                    .map(TopicDecision::topicId)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public List<String> deferredTopicNames() {
            return deferredTopics.stream()
                    .map(TopicDecision::topicName)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public ExecutionPlan withAccountContext(AccountCreditSnapshot accountCreditSnapshot, BrandProfile brandProfile) {
            return new ExecutionPlan(
                    triggerMode,
                    targetProbeCount,
                    selectedTopics,
                    deferredTopics,
                    coverageStrategy,
                    humanReason,
                    coldStart,
                    justEdited,
                    quotaLimited,
                    accountCreditSnapshot == null ? null : accountCreditSnapshot.availableCredits(),
                    accountCreditSnapshot == null ? null : accountCreditSnapshot.activeBrandCount(),
                    brandProfile == null ? null : brandProfile.desiredBudget(),
                    brandProfile == null ? null : brandProfile.expansionBudget(),
                    brandProfile == null ? null : brandProfile.activeTopicCount(),
                    brandProfile == null ? null : brandProfile.coveredTopicCount7d(),
                    brandProfile == null ? null : brandProfile.weeklyGapTopicCount(),
                    brandProfile == null ? null : brandProfile.weeklyCoverageRatio(),
                    executionPrompts
            );
        }

        public ExecutionPlan withExecutionPrompts(List<ExecutionPromptPlan> executionPrompts) {
            return new ExecutionPlan(
                    triggerMode,
                    targetProbeCount,
                    selectedTopics,
                    deferredTopics,
                    coverageStrategy,
                    humanReason,
                    coldStart,
                    justEdited,
                    quotaLimited,
                    accountAvailableCredits,
                    accountBrandCount,
                    brandCoreProbeCount,
                    brandExpansionProbeCount,
                    weeklyTargetTopicCount,
                    weeklyCoveredTopicCount,
                    weeklyGapTopicCount,
                    weeklyCoverageRatio,
                    executionPrompts
            );
        }
    }

    public record ExecutionPromptPlan(Integer seqNo,
                                      String promptId,
                                      String topicId,
                                      String topicName,
                                      String promptText) {

        public ExecutionPromptPlan {
            seqNo = seqNo == null ? null : Math.max(1, seqNo);
            promptId = normalizeText(promptId);
            topicId = normalizeText(topicId);
            topicName = firstText(topicName, "未命名主题");
            promptText = normalizeText(promptText);
        }
    }

    public record TopicDecision(String topicId,
                                String topicName,
                                int allocatedProbeCount,
                                double priorityScore,
                                List<String> reasonTags) {

        public TopicDecision {
            topicId = StringUtils.hasText(topicId) ? topicId.trim() : null;
            topicName = StringUtils.hasText(topicName) ? topicName.trim() : "未命名主题";
            allocatedProbeCount = Math.max(0, allocatedProbeCount);
            priorityScore = Double.isNaN(priorityScore) ? 0.0d : Math.max(0.0d, Math.min(priorityScore, 1.0d));
            reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
        }
    }

    private record ScheduledBrandInput(String brandScopeId,
                                       BrandSpaceConfigView configView,
                                       DiagnosisTask latestTask,
                                       KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState,
                                       List<TopicInput> topicInputs,
                                       int perBrandLimit) {
    }

    private record BrandProfile(boolean coldStart,
                                boolean justEdited,
                                boolean highIntensity,
                                double allocationWeight,
                                int desiredBudget,
                                int expansionBudget,
                                int activeTopicCount,
                                int coveredTopicCount7d,
                                int weeklyGapTopicCount,
                                double weeklyCoverageRatio) {
    }

    private record TopicAllocationBundle(List<TopicDecision> selectedTopics,
                                         List<TopicDecision> deferredTopics) {
    }

    private record BudgetNeed(String brandScopeId,
                              int desiredBudget,
                              int expansionBudget,
                              double allocationWeight) {
    }

    private record BudgetShare(String brandScopeId,
                               double fractionalRemainder) {
    }

    private record PromptCandidate(String promptId,
                                   String topicId,
                                   String topicName,
                                   String promptText) {
    }

    private record TopicInput(String topicId,
                              String topicName,
                              boolean enabled,
                              Integer seqNo,
                              double priorityScore,
                              boolean coldStart,
                              boolean justEdited,
                              int enabledPromptCount,
                              int last7dProbeCount) {
    }

    public record AccountCreditSnapshot(int availableCredits,
                                        Integer activeBrandCount,
                                        Integer dueBrandCount) {

        public static AccountCreditSnapshot empty(int dueBrandCount) {
            return new AccountCreditSnapshot(0, dueBrandCount, dueBrandCount);
        }

        public AccountCreditSnapshot normalize(int fallbackDueBrandCount) {
            int safeDueBrandCount = dueBrandCount != null && dueBrandCount > 0 ? dueBrandCount : fallbackDueBrandCount;
            int safeActiveBrandCount = activeBrandCount != null && activeBrandCount > 0
                    ? activeBrandCount
                    : Math.max(safeDueBrandCount, fallbackDueBrandCount);
            return new AccountCreditSnapshot(
                    Math.max(0, availableCredits),
                    safeActiveBrandCount,
                    safeDueBrandCount
            );
        }
    }

    private record ScheduleTruthSnapshot(
            KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState,
            List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateView> topicStates) {
    }

    private enum TriggerMode {
        INTERACTIVE_PREVIEW("interactive_preview"),
        CREATE_INITIAL_RUN("create_initial_run"),
        MANUAL_EXECUTE_ONCE("manual_execute_once"),
        SCHEDULED_TRACKING("scheduled_tracking");

        private final String code;

        TriggerMode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
