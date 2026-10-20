package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeStatus;
import com.geoprobe.dto.engine.v42.RealizationConstraints;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service("v42OrthogonalMatrixService")
public class OrthogonalMatrixService {

    private static final int MIN_PARETO_TEMPLATE_CATALOG_SIZE = 30;
    private static final int MIN_ADVERSARIAL_TEMPLATE_CATALOG_SIZE = 20;
    private static final int[] PRIMARY_BUCKET_ALLOCATION_WEIGHTS = new int[]{5, 3, 2};
    private static final int[] SECONDARY_BUCKET_ALLOCATION_WEIGHTS = new int[]{3, 2};
    private static final double ROUND3B_TOPIC_LANE_QUOTA_DEBT_WEIGHT = 0.55d;
    private static final double ROUND3B_TOPIC_LANE_FRESHNESS_WEIGHT = 0.20d;
    private static final double ROUND3B_TOPIC_LANE_COVERAGE_DEBT_WEIGHT = 0.25d;
    private static final double ROUND3B_STATE_GLOBAL_COVERAGE_WEIGHT = 0.50d;
    private static final double ROUND3B_STATE_TOPIC_AFFINITY_WEIGHT = 0.25d;
    private static final double ROUND3B_STATE_COVERAGE_DEBT_WEIGHT = 0.15d;
    private static final double ROUND3B_STATE_FRESHNESS_WEIGHT = 0.10d;
    private static final double HIGH_COVERAGE_DEBT_THRESHOLD = 0.70d;
    private static final int MIN_TOPIC_BINDING_SCORE_FOR_PRIORITY = 0;
    private static final List<String> TEMPLATE_DISALLOWED_FRAGMENTS = List.of(
            "类别", "维度", "框架", "信号", "门槛", "误判", "复核", "交叉验证"
    );
    private static final List<String> PARETO_VARIATION_PREFIXES = List.of(
            "如果是第一次买，",
            "如果想快点定下来，",
            "如果更在意长期用着省心，",
            "如果预算卡得更紧一点，",
            "如果是给家里长期备着，"
    );
    private static final List<String> ADVERSARIAL_VARIATION_PREFIXES = List.of(
            "站在更谨慎一点的角度，",
            "如果先不信营销说法，",
            "如果把翻车风险看得更重，",
            "如果先按最坏情况问清楚，",
            "如果先把质疑点摆在前面，"
    );
    private static final List<String> DIMENSION_ORDER = CssmHiAbstractStates.DIMENSION_IDS;

    private static final List<MutualExclusionRule> MUTUAL_EXCLUSION_RULES = List.of(
            new MutualExclusionRule("D1", "verification", "D4", "severely_depleted"),
            new MutualExclusionRule("D5", "acute_emergency", "D6", "dialogic")
    );

    private static final List<TemplatePrototype> PARETO_TEMPLATES = List.of(
            new TemplatePrototype("pareto_recommendation_shortlist", "现在买{category}，优先看哪个"),
            new TemplatePrototype("pareto_recommendation_shortlist", "{category}直接推荐几款靠谱的"),
            new TemplatePrototype("pareto_recommendation_shortlist", "最近{category}里谁更值得先看"),
            new TemplatePrototype("pareto_recommendation_shortlist", "{category}里有哪些是公认不容易出错的"),
            new TemplatePrototype("pareto_recommendation_shortlist", "如果现在就买{category}，一般先看哪几个"),
            new TemplatePrototype("pareto_brand_comparison", "{brandName}和{competitor}到底怎么选"),
            new TemplatePrototype("pareto_brand_comparison", "{brandName}跟{competitor}谁更值得入手"),
            new TemplatePrototype("pareto_brand_comparison", "同样是{category}，{brandName}和{competitor}差在哪"),
            new TemplatePrototype("pareto_brand_comparison", "{brandName}对比{competitor}，哪个更稳"),
            new TemplatePrototype("pareto_brand_comparison", "{brandName}和{competitor}放一起看，通常会先选谁"),
            new TemplatePrototype("pareto_constraint_filtering", "预算有限买{category}，先筛什么"),
            new TemplatePrototype("pareto_constraint_filtering", "第一次买{category}，怎么缩小范围"),
            new TemplatePrototype("pareto_constraint_filtering", "送人用的{category}，优先看什么"),
            new TemplatePrototype("pareto_constraint_filtering", "想省时间选{category}，最先排哪几个条件"),
            new TemplatePrototype("pareto_constraint_filtering", "选{category}如果怕买错，第一步该看什么"),
            new TemplatePrototype("pareto_trust_signal", "{category}靠不靠谱，通常先看哪几点"),
            new TemplatePrototype("pareto_trust_signal", "买{category}前，口碑到底该怎么看"),
            new TemplatePrototype("pareto_trust_signal", "{category}质量差异一般从哪几个点看"),
            new TemplatePrototype("pareto_trust_signal", "{category}最后下单前还要确认什么"),
            new TemplatePrototype("pareto_trust_signal", "挑{category}时，最能拉开差距的点是什么"),
            new TemplatePrototype("pareto_scenario_match", "家里常备的{category}，一般怎么挑更合适"),
            new TemplatePrototype("pareto_scenario_match", "自己长期用的{category}，更该看重什么"),
            new TemplatePrototype("pareto_scenario_match", "日常喝的{category}，怎么选更省心"),
            new TemplatePrototype("pareto_scenario_match", "如果主要图稳定，{category}该怎么选"),
            new TemplatePrototype("pareto_scenario_match", "想选个不容易后悔的{category}，通常怎么挑"),
            new TemplatePrototype("pareto_search_shortcut", "求一个{category}的快速选择思路"),
            new TemplatePrototype("pareto_search_shortcut", "别讲太复杂，{category}直接怎么选"),
            new TemplatePrototype("pareto_search_shortcut", "现在选{category}有没有简单点的判断方法"),
            new TemplatePrototype("pareto_search_shortcut", "{category}想快速挑一个靠谱的，怎么看"),
            new TemplatePrototype("pareto_search_shortcut", "如果只想听结论，{category}先看谁")
    );

    private static final List<TemplatePrototype> ADVERSARIAL_TEMPLATES = List.of(
            new TemplatePrototype("adversarial_rumor_challenge", "最近老看到{brandName}翻车，这事到底真不真"),
            new TemplatePrototype("adversarial_rumor_challenge", "网上说{brandName}问题很多，靠谱吗"),
            new TemplatePrototype("adversarial_rumor_challenge", "{brandName}最近是不是又出质量争议了"),
            new TemplatePrototype("adversarial_rumor_challenge", "听说{brandName}这阵子口碑不太行，真的假的"),
            new TemplatePrototype("adversarial_anti_hype", "{brandName}是不是被营销吹得太过了"),
            new TemplatePrototype("adversarial_anti_hype", "{brandName}真有网上说得那么好吗"),
            new TemplatePrototype("adversarial_anti_hype", "{brandName}是不是名气大于实际"),
            new TemplatePrototype("adversarial_anti_hype", "{brandName}到底是真的强还是被带节奏了"),
            new TemplatePrototype("adversarial_substitute_pressure", "不想买{brandName}，有没有更值的替代"),
            new TemplatePrototype("adversarial_substitute_pressure", "{brandName}太贵了，换成谁更划算"),
            new TemplatePrototype("adversarial_substitute_pressure", "如果不考虑{brandName}，{category}里谁更值"),
            new TemplatePrototype("adversarial_substitute_pressure", "买不起{brandName}的时候，大家一般换谁"),
            new TemplatePrototype("adversarial_forced_comparison", "{competitor}是不是已经比{brandName}更值得买了"),
            new TemplatePrototype("adversarial_forced_comparison", "{brandName}和{competitor}比，到底谁更容易踩坑"),
            new TemplatePrototype("adversarial_forced_comparison", "现在看{brandName}是不是已经不如{competitor}了"),
            new TemplatePrototype("adversarial_forced_comparison", "{competitor}和{brandName}放一起，谁更稳"),
            new TemplatePrototype("adversarial_brand_exclusion", "推荐{category}的时候先别提{brandName}"),
            new TemplatePrototype("adversarial_brand_exclusion", "如果不看{brandName}，{category}还有谁能打"),
            new TemplatePrototype("adversarial_brand_exclusion", "别默认推{brandName}，重新说一遍谁更值得看"),
            new TemplatePrototype("adversarial_brand_exclusion", "把{brandName}排除掉以后，{category}该怎么选")
    );

    private static final Map<String, Map<String, Integer>> VARIABLE_WEIGHTS = Map.of(
            "D1", Map.of(
                    "initiation", 3,
                    "exploration", 4,
                    "formulation", 5,
                    "verification", 6
            ),
            "D2", Map.of(
                    "informational_undirected", 3,
                    "informational_directed", 4,
                    "instructional_active", 5,
                    "resource_acquisition", 6
            ),
            "D3", Map.of(
                    "creation_idleness", 2,
                    "affection_belonging_participation", 3,
                    "identity_freedom", 4,
                    "understanding_competence", 5,
                    "subsistence_protection", 6
            ),
            "D4", Map.of(
                    "severely_depleted", 3,
                    "moderate_strain", 4,
                    "optimal", 6
            ),
            "D5", Map.of(
                    "reflective", 3,
                    "anticipatory_constraint", 4,
                    "acute_emergency", 5
            ),
            "D6", Map.of(
                    "recognition", 3,
                    "specification", 4,
                    "dialogic", 5
            )
    );

    private final ProbeSimulationPlanningService probeSimulationPlanningService;

    public OrthogonalMatrixService() {
        this(new ProbeSimulationPlanningService());
    }

    @Autowired
    public OrthogonalMatrixService(ProbeSimulationPlanningService probeSimulationPlanningService) {
        this.probeSimulationPlanningService = Objects.requireNonNull(probeSimulationPlanningService, "probeSimulationPlanningService 不能为空");
    }

    public List<ProbeBlueprint> generate(CategoryVariablePool pool, int targetProbeCount) {
        InputContext input = validateAndPrepare(pool);
        int normalizedTarget = normalizeTargetProbeCount(targetProbeCount);
        List<RowCandidate> candidates = buildCandidates(input);
        Set<String> allValidPairs = buildAllValidPairs(input);
        TopicLanePlanner topicLanePlanner = buildTopicLanePlanner(input.topicProjectionContext());
        List<RowCandidate> coverageSeed = buildCoverageSeed(candidates, allValidPairs, topicLanePlanner);
        BucketAllocation bucketAllocation = planBucketAllocation(normalizedTarget, coverageSeed.size());
        Set<String> globalUsedSignatures = new LinkedHashSet<>();
        List<RowCandidate> globalSelectedRows = new ArrayList<>();
        Map<String, Integer> globalSelectedTopicCounts = new LinkedHashMap<>();

        List<LaneSelectionDecision> orthogonalRows = buildOrthogonalBucket(
                candidates,
                coverageSeed,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.orthogonalTarget(),
                topicLanePlanner
        );
        List<ProbeBlueprint> blueprints = new ArrayList<>(normalizedTarget);
        blueprints.addAll(toBlueprints(input, orthogonalRows, BucketType.ORTHOGONAL, null));
        blueprints.addAll(buildTemplateBucketBlueprints(
                input,
                candidates,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.paretoTarget(),
                BucketType.PARETO,
                PARETO_TEMPLATES,
                topicLanePlanner
        ));
        blueprints.addAll(buildTemplateBucketBlueprints(
                input,
                candidates,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.adversarialTarget(),
                BucketType.ADVERSARIAL,
                ADVERSARIAL_TEMPLATES,
                topicLanePlanner
        ));

        validateOutput(blueprints, allValidPairs, bucketAllocation);
        return List.copyOf(blueprints);
    }

    Map<String, Integer> assignedTopicCounts(CategoryVariablePool pool, int targetProbeCount) {
        InputContext input = validateAndPrepare(pool);
        int normalizedTarget = normalizeTargetProbeCount(targetProbeCount);
        List<RowCandidate> candidates = buildCandidates(input);
        Set<String> allValidPairs = buildAllValidPairs(input);
        TopicLanePlanner topicLanePlanner = buildTopicLanePlanner(input.topicProjectionContext());
        List<RowCandidate> coverageSeed = buildCoverageSeed(candidates, allValidPairs, topicLanePlanner);
        BucketAllocation bucketAllocation = planBucketAllocation(normalizedTarget, coverageSeed.size());
        Set<String> globalUsedSignatures = new LinkedHashSet<>();
        List<RowCandidate> globalSelectedRows = new ArrayList<>();
        Map<String, Integer> globalSelectedTopicCounts = new LinkedHashMap<>();

        buildOrthogonalBucket(
                candidates,
                coverageSeed,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.orthogonalTarget(),
                topicLanePlanner
        );
        buildWeightedBucket(
                candidates,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.paretoTarget(),
                topicLanePlanner
        );
        buildWeightedBucket(
                candidates,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                bucketAllocation.adversarialTarget(),
                topicLanePlanner
        );
        return Map.copyOf(globalSelectedTopicCounts);
    }

    int topicAffinityScore(Map<String, String> abstractTags, EngineTaskContextPackage contextPackage) {
        if (abstractTags == null || abstractTags.isEmpty()) {
            return 0;
        }
        return resolveTopicAffinity(
                abstractTags,
                LightConeV43TopicStateProjectionSupport.resolveRuntimeContext(
                        contextPackage == null ? Map.of() : contextPackage.extensibleSignals()
                )
        ).score();
    }

    String primaryTopicLabel(Map<String, String> abstractTags, EngineTaskContextPackage contextPackage) {
        if (abstractTags == null || abstractTags.isEmpty()) {
            return null;
        }
        return resolveTopicAffinity(
                abstractTags,
                LightConeV43TopicStateProjectionSupport.resolveRuntimeContext(
                        contextPackage == null ? Map.of() : contextPackage.extensibleSignals()
                )
        ).primaryTopicLabel();
    }

    private InputContext validateAndPrepare(CategoryVariablePool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("CategoryVariablePool 不能为空");
        }

        LinkedHashMap<String, DimensionContext> dimensions = new LinkedHashMap<>();
        for (DimensionVariables dimension : pool.allDimensions()) {
            if (dimension == null) {
                throw new IllegalArgumentException("Node 1 需要完整的六维变量池");
            }
            String dimensionId = normalizeRequired(dimension.dimensionId(), "dimensionId 不能为空");
            List<String> expectedCodes = CssmHiAbstractStates.ABSTRACT_CODE_MAP.get(dimensionId);
            if (expectedCodes == null) {
                throw new IllegalArgumentException("未知维度: " + dimensionId);
            }
            if (!Objects.equals(CssmHiAbstractStates.DIMENSION_NAMES.get(dimensionId), dimension.dimensionName())) {
                throw new IllegalArgumentException("维度名称与主设计文档不一致: " + dimensionId);
            }
            if (!expectedCodes.equals(dimension.abstractCodes())) {
                throw new IllegalArgumentException("维度 abstractCodes 与主设计文档不一致: " + dimensionId);
            }
            if (dimension.abstractCodes().size() != dimension.variables().size()) {
                throw new IllegalArgumentException("维度 abstractCodes 与 variables 数量不一致: " + dimensionId);
            }

            LinkedHashMap<String, String> translatedByCode = new LinkedHashMap<>();
            for (int index = 0; index < expectedCodes.size(); index++) {
                translatedByCode.put(
                        expectedCodes.get(index),
                        normalizeRequired(dimension.variables().get(index), "translated variable 不能为空")
                );
            }
            dimensions.put(dimensionId, new DimensionContext(
                    dimensionId,
                    List.copyOf(expectedCodes),
                    Collections.unmodifiableMap(translatedByCode)
            ));
        }

        if (!dimensions.keySet().equals(new LinkedHashSet<>(DIMENSION_ORDER))) {
            throw new IllegalArgumentException("Node 1 仅接受 Phase 1 的完整六维输入");
        }

        return new InputContext(
                normalizeRequired(pool.brandName(), "brandName 不能为空"),
                normalizeRequired(pool.category(), "category 不能为空"),
                Collections.unmodifiableMap(dimensions),
                pool.contextPackage(),
                LightConeV43TopicStateProjectionSupport.resolveRuntimeContext(
                        pool.contextPackage() == null ? Map.of() : pool.contextPackage().extensibleSignals()
                )
        );
    }

    private List<RowCandidate> buildCandidates(InputContext input) {
        List<RowCandidate> candidates = new ArrayList<>();
        buildCandidatesRecursive(input, 0, new LinkedHashMap<>(), candidates);
        candidates.sort((left, right) -> left.signature().compareTo(right.signature()));
        return List.copyOf(candidates);
    }

    private void buildCandidatesRecursive(InputContext input,
                                          int dimensionIndex,
                                          LinkedHashMap<String, String> currentTags,
                                          List<RowCandidate> candidates) {
        if (dimensionIndex >= DIMENSION_ORDER.size()) {
            candidates.add(toCandidate(input, currentTags));
            return;
        }

        String dimensionId = DIMENSION_ORDER.get(dimensionIndex);
        for (String abstractCode : input.dimension(dimensionId).abstractCodes()) {
            currentTags.put(dimensionId, abstractCode);
            if (!violatesAnyRule(currentTags)) {
                buildCandidatesRecursive(input, dimensionIndex + 1, currentTags, candidates);
            }
            currentTags.remove(dimensionId);
        }
    }

    private RowCandidate toCandidate(InputContext input, Map<String, String> abstractTags) {
        LinkedHashMap<String, String> abstractCopy = new LinkedHashMap<>();
        LinkedHashMap<String, String> translatedCopy = new LinkedHashMap<>();
        int weightScore = 0;

        for (String dimensionId : DIMENSION_ORDER) {
            String abstractCode = abstractTags.get(dimensionId);
            abstractCopy.put(dimensionId, abstractCode);
            translatedCopy.put(dimensionId, input.dimension(dimensionId).translatedByCode().get(abstractCode));
            weightScore += VARIABLE_WEIGHTS.get(dimensionId).getOrDefault(abstractCode, 1);
        }
        CandidateTopicAffinity topicAffinity = resolveTopicAffinity(abstractCopy, input.topicProjectionContext());

        return new RowCandidate(
                signature(abstractCopy),
                Collections.unmodifiableMap(abstractCopy),
                Collections.unmodifiableMap(translatedCopy),
                coveredPairs(abstractCopy),
                weightScore,
                topicAffinity.score(),
                topicAffinity.primaryTopicLabel(),
                topicAffinity.affinityScoresByTopic()
        );
    }

    private Set<String> buildAllValidPairs(InputContext input) {
        LinkedHashSet<String> pairs = new LinkedHashSet<>();
        for (int leftIndex = 0; leftIndex < DIMENSION_ORDER.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < DIMENSION_ORDER.size(); rightIndex++) {
                String leftDimension = DIMENSION_ORDER.get(leftIndex);
                String rightDimension = DIMENSION_ORDER.get(rightIndex);
                for (String leftCode : input.dimension(leftDimension).abstractCodes()) {
                    for (String rightCode : input.dimension(rightDimension).abstractCodes()) {
                        if (violatesPair(leftDimension, leftCode, rightDimension, rightCode)) {
                            continue;
                        }
                        pairs.add(pairKey(leftDimension, leftCode, rightDimension, rightCode));
                    }
                }
            }
        }
        return Collections.unmodifiableSet(pairs);
    }

    private int normalizeTargetProbeCount(int targetProbeCount) {
        if (targetProbeCount <= 0) {
            throw new IllegalArgumentException("targetProbeCount 必须是正整数");
        }
        return targetProbeCount;
    }

    private List<RowCandidate> buildCoverageSeed(List<RowCandidate> candidates,
                                                 Set<String> allValidPairs,
                                                 TopicLanePlanner topicLanePlanner) {
        LinkedHashSet<String> remainingPairs = new LinkedHashSet<>(allValidPairs);
        List<RowCandidate> selected = new ArrayList<>();
        Set<String> usedSignatures = new LinkedHashSet<>();

        while (!remainingPairs.isEmpty()) {
            RowCandidate next = chooseBestCoverageCandidate(
                    candidates,
                    selected,
                    usedSignatures,
                    remainingPairs,
                    topicLanePlanner
            );
            if (next == null) {
                throw new IllegalStateException("正交桶无法完成两两覆盖");
            }
            selected.add(next);
            usedSignatures.add(next.signature());
            remainingPairs.removeAll(next.coveredPairs());
        }
        return List.copyOf(selected);
    }

    private BucketAllocation planBucketAllocation(int targetProbeCount, int coverageFloor) {
        if (targetProbeCount <= coverageFloor) {
            return new BucketAllocation(targetProbeCount, 0, 0, coverageFloor);
        }
        int[] weighted = allocateByWeights(targetProbeCount, PRIMARY_BUCKET_ALLOCATION_WEIGHTS);
        int orthogonalTarget = Math.max(coverageFloor, weighted[0]);
        int remaining = targetProbeCount - orthogonalTarget;
        int[] secondary = allocateByWeights(remaining, SECONDARY_BUCKET_ALLOCATION_WEIGHTS);
        return new BucketAllocation(orthogonalTarget, secondary[0], secondary[1], coverageFloor);
    }

    private int[] allocateByWeights(int total, int[] weights) {
        if (total <= 0) {
            return new int[weights.length];
        }
        int weightSum = 0;
        for (int weight : weights) {
            weightSum += Math.max(0, weight);
        }
        if (weightSum <= 0) {
            throw new IllegalArgumentException("weights 必须至少包含一个正值");
        }
        int[] allocated = new int[weights.length];
        double[] fractions = new double[weights.length];
        int allocatedTotal = 0;
        for (int index = 0; index < weights.length; index++) {
            double raw = total * (Math.max(0, weights[index]) / (double) weightSum);
            allocated[index] = (int) Math.floor(raw);
            fractions[index] = raw - allocated[index];
            allocatedTotal += allocated[index];
        }
        while (allocatedTotal < total) {
            int bestIndex = 0;
            double bestFraction = fractions[0];
            for (int index = 1; index < fractions.length; index++) {
                if (fractions[index] > bestFraction) {
                    bestFraction = fractions[index];
                    bestIndex = index;
                }
            }
            allocated[bestIndex]++;
            allocatedTotal++;
            fractions[bestIndex] = 0.0d;
        }
        return allocated;
    }

    private List<LaneSelectionDecision> buildOrthogonalBucket(List<RowCandidate> candidates,
                                                              List<RowCandidate> coverageSeed,
                                                              Set<String> globalUsedSignatures,
                                                              List<RowCandidate> globalSelectedRows,
                                                              Map<String, Integer> globalSelectedTopicCounts,
                                                              int target,
                                                              TopicLanePlanner topicLanePlanner) {
        List<RowCandidate> selectedRows = new ArrayList<>(Math.max(0, target));
        List<LaneSelectionDecision> selectedDecisions = new ArrayList<>(Math.max(0, target));
        int coverageCount = Math.min(target, coverageSeed.size());
        for (int index = 0; index < coverageCount; index++) {
            RowCandidate row = coverageSeed.get(index);
            String assignedTopicLabel = assignCoverageTopic(row, topicLanePlanner, globalSelectedTopicCounts);
            selectedRows.add(row);
            selectedDecisions.add(new LaneSelectionDecision(row, assignedTopicLabel));
            globalUsedSignatures.add(row.signature());
            globalSelectedRows.add(row);
            incrementTopicCount(globalSelectedTopicCounts, assignedTopicLabel);
        }
        fillRemainingByWeight(
                candidates,
                selectedRows,
                selectedDecisions,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                target,
                topicLanePlanner
        );
        return List.copyOf(selectedDecisions);
    }

    private String assignCoverageTopic(RowCandidate candidate,
                                       TopicLanePlanner topicLanePlanner,
                                       Map<String, Integer> topicCounts) {
        if (candidate == null || !topicLanePlanner.active()) {
            return candidate == null ? null : candidate.primaryTopicLabel();
        }
        String bestTopic = null;
        double bestLaneScore = Double.NEGATIVE_INFINITY;
        int bestAffinity = Integer.MIN_VALUE;
        for (TopicLane lane : topicLanePlanner.activeLanes()) {
            int affinity = candidate.topicAffinityScores().getOrDefault(lane.canonicalTopicLabel(), 0);
            if (affinity <= 0) {
                continue;
            }
            int selectedCount = topicCounts.getOrDefault(lane.canonicalTopicLabel(), 0);
            double laneScore = topicLanePlanner.laneScore(lane.canonicalTopicLabel(), selectedCount);
            if (laneScore > bestLaneScore
                    || (laneScore == bestLaneScore && affinity > bestAffinity)
                    || (laneScore == bestLaneScore && affinity == bestAffinity
                    && bestTopic != null && lane.canonicalTopicLabel().compareTo(bestTopic) < 0)) {
                bestTopic = lane.canonicalTopicLabel();
                bestLaneScore = laneScore;
                bestAffinity = affinity;
            }
        }
        return StringUtils.hasText(bestTopic) ? bestTopic : candidate.primaryTopicLabel();
    }

    private List<LaneSelectionDecision> buildWeightedBucket(List<RowCandidate> candidates,
                                                            Set<String> globalUsedSignatures,
                                                            List<RowCandidate> globalSelectedRows,
                                                            Map<String, Integer> globalSelectedTopicCounts,
                                                            int target,
                                                            TopicLanePlanner topicLanePlanner) {
        List<RowCandidate> selectedRows = new ArrayList<>(target);
        List<LaneSelectionDecision> selectedDecisions = new ArrayList<>(target);
        fillRemainingByWeight(
                candidates,
                selectedRows,
                selectedDecisions,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                target,
                topicLanePlanner
        );
        return List.copyOf(selectedDecisions);
    }

    private List<ProbeBlueprint> buildTemplateBucketBlueprints(InputContext input,
                                                               List<RowCandidate> candidates,
                                                               Set<String> globalUsedSignatures,
                                                               List<RowCandidate> globalSelectedRows,
                                                               Map<String, Integer> globalSelectedTopicCounts,
                                                               int target,
                                                               BucketType bucketType,
                                                               List<TemplatePrototype> templates,
                                                               TopicLanePlanner topicLanePlanner) {
        if (target <= 0) {
            return List.of();
        }
        int seedCount = Math.min(target, templates.size());
        List<LaneSelectionDecision> seedRows = buildWeightedBucket(
                candidates,
                globalUsedSignatures,
                globalSelectedRows,
                globalSelectedTopicCounts,
                seedCount,
                topicLanePlanner
        );
        List<ProbeBlueprint> seedBlueprints = toBlueprints(
                input,
                seedRows,
                bucketType,
                selectSeedTemplates(templates, seedCount)
        );
        return expandTemplateBlueprints(seedBlueprints, target);
    }

    private List<TemplatePrototype> selectSeedTemplates(List<TemplatePrototype> templates, int seedCount) {
        if (seedCount <= 0) {
            return List.of();
        }
        LinkedHashMap<String, List<TemplatePrototype>> byFamily = new LinkedHashMap<>();
        for (TemplatePrototype template : templates) {
            byFamily.computeIfAbsent(template.family(), ignored -> new ArrayList<>()).add(template);
        }
        List<String> familyOrder = new ArrayList<>(byFamily.keySet());
        List<TemplatePrototype> selected = new ArrayList<>(seedCount);
        for (int round = 0; selected.size() < seedCount; round++) {
            boolean progressed = false;
            for (String family : familyOrder) {
                List<TemplatePrototype> familyTemplates = byFamily.get(family);
                if (round >= familyTemplates.size()) {
                    continue;
                }
                selected.add(familyTemplates.get(round));
                progressed = true;
                if (selected.size() >= seedCount) {
                    break;
                }
            }
            if (!progressed) {
                throw new IllegalStateException("模板桶无法按 family 分散选出足够 seed template");
            }
        }
        return List.copyOf(selected);
    }

    private List<ProbeBlueprint> expandTemplateBlueprints(List<ProbeBlueprint> seedBlueprints, int target) {
        if (target <= 0) {
            return List.of();
        }
        if (seedBlueprints == null || seedBlueprints.isEmpty()) {
            throw new IllegalStateException("模板桶没有可用蓝图，target=" + target);
        }
        if (target <= seedBlueprints.size()) {
            return List.copyOf(seedBlueprints);
        }
        List<ProbeBlueprint> expanded = new ArrayList<>(target);
        for (int index = 0; index < target; index++) {
            ProbeBlueprint base = seedBlueprints.get(index % seedBlueprints.size());
            int cycle = index / seedBlueprints.size();
            expanded.add(cycle == 0 ? base : cloneTemplateVariant(base, cycle, index));
        }
        return List.copyOf(expanded);
    }

    private ProbeBlueprint cloneTemplateVariant(ProbeBlueprint base, int cycle, int absoluteIndex) {
        RealizationPlan realizationPlan = augmentRealizationPlan(base.realizationPlan(), base.bucketType(), cycle, absoluteIndex);
        return new ProbeBlueprint(
                base.probeId() + "__v" + (cycle + 1),
                base.bucketType(),
                base.abstractTags(),
                base.translatedTags(),
                augmentTemplateText(base.templateText(), base.bucketType(), cycle, absoluteIndex),
                base.status(),
                realizationPlan
        );
    }

    private RealizationPlan augmentRealizationPlan(RealizationPlan basePlan,
                                                   BucketType bucketType,
                                                   int cycle,
                                                   int absoluteIndex) {
        if (basePlan == null) {
            return null;
        }
        String variationHint = switch (bucketType) {
            case PARETO -> "换一种更贴近日常决策的表达顺序，别和上一轮问法重复";
            case ADVERSARIAL -> "换一个更自然的质疑切口，别重复上一轮句式";
            case ORTHOGONAL -> "保持原来六维语义，但换一种更自然的开口方式";
        };
        RealizationConstraints baseConstraints = basePlan.realizationConstraints();
        List<String> softHints = new ArrayList<>(baseConstraints.softSemanticHints());
        softHints.add("VARIATION:" + variationHint);
        RealizationConstraints augmentedConstraints = new RealizationConstraints(
                baseConstraints.brandExposurePolicy(),
                baseConstraints.mustExpressSemantics(),
                softHints,
                baseConstraints.anchorPolicy(),
                baseConstraints.lengthPolicy(),
                baseConstraints.questionFormPolicy(),
                baseConstraints.fallbackFamily()
        );
        String variantTag = "variant=" + (cycle + 1) + "-" + absoluteIndex;
        return new RealizationPlan(
                basePlan.simulationIntentProfile(),
                augmentedConstraints,
                basePlan.realizationSeed() + "|" + variantTag,
                basePlan.debugSignature() + "|" + variantTag
        );
    }

    private String augmentTemplateText(String templateText,
                                       BucketType bucketType,
                                       int cycle,
                                       int absoluteIndex) {
        if (!StringUtils.hasText(templateText)) {
            return templateText;
        }
        List<String> prefixes = switch (bucketType) {
            case PARETO -> PARETO_VARIATION_PREFIXES;
            case ADVERSARIAL -> ADVERSARIAL_VARIATION_PREFIXES;
            case ORTHOGONAL -> List.of();
        };
        if (prefixes.isEmpty()) {
            return templateText;
        }
        String prefix = prefixes.get(Math.floorMod(cycle + absoluteIndex, prefixes.size()));
        String normalized = templateText.trim();
        return normalized.startsWith(prefix) ? normalized : prefix + normalized;
    }

    private RowCandidate chooseBestCoverageCandidate(List<RowCandidate> candidates,
                                                     List<RowCandidate> selected,
                                                     Set<String> globalUsedSignatures,
                                                     Set<String> remainingPairs,
                                                     TopicLanePlanner topicLanePlanner) {
        RowCandidate priorityBound = chooseBestCoverageCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                remainingPairs,
                topicLanePlanner,
                true
        );
        return priorityBound != null
                ? priorityBound
                : chooseBestCoverageCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                remainingPairs,
                topicLanePlanner,
                false
        );
    }

    private RowCandidate chooseBestCoverageCandidate(List<RowCandidate> candidates,
                                                     List<RowCandidate> selected,
                                                     Set<String> globalUsedSignatures,
                                                     Set<String> remainingPairs,
                                                     TopicLanePlanner topicLanePlanner,
                                                     boolean requirePriorityBinding) {
        RowCandidate best = null;
        int bestCoverage = -1;
        double bestLaneScore = Double.NEGATIVE_INFINITY;
        double bestStateScore = Double.NEGATIVE_INFINITY;
        int bestDistance = -1;
        int bestWeight = -1;
        Map<String, Integer> topicCounts = countSelectedTopicOccurrences(selected);

        for (RowCandidate candidate : candidates) {
            if (globalUsedSignatures.contains(candidate.signature())) {
                continue;
            }
            int coverage = coverageCount(candidate, remainingPairs);
            if (coverage <= 0) {
                continue;
            }
            if (requirePriorityBinding
                    && StringUtils.hasText(candidate.primaryTopicLabel())
                    && candidate.topicAffinityScores().getOrDefault(candidate.primaryTopicLabel(), 0)
                    < MIN_TOPIC_BINDING_SCORE_FOR_PRIORITY) {
                continue;
            }
            int distance = minimumHammingDistance(candidate, selected);
            double laneScore = topicLanePlanner.laneScore(
                    candidate.primaryTopicLabel(),
                    topicCounts.getOrDefault(candidate.primaryTopicLabel(), 0)
            );
            double stateScore = resolveRound3BStateSelectionScore(
                    candidate,
                    selected,
                    topicLanePlanner,
                    topicCounts,
                    candidate.primaryTopicLabel(),
                    candidate.topicAffinityScore()
            );
            if (coverage > bestCoverage
                    || (coverage == bestCoverage && laneScore > bestLaneScore)
                    || (coverage == bestCoverage && laneScore == bestLaneScore && stateScore > bestStateScore)
                    || (coverage == bestCoverage && laneScore == bestLaneScore
                    && stateScore == bestStateScore && distance > bestDistance)
                    || (coverage == bestCoverage && laneScore == bestLaneScore
                    && stateScore == bestStateScore && distance == bestDistance
                    && candidate.weightScore() > bestWeight)) {
                best = candidate;
                bestCoverage = coverage;
                bestLaneScore = laneScore;
                bestStateScore = stateScore;
                bestDistance = distance;
                bestWeight = candidate.weightScore();
            }
        }
        return best;
    }

    private void fillRemainingByWeight(List<RowCandidate> candidates,
                                       List<RowCandidate> selected,
                                       List<LaneSelectionDecision> selectedDecisions,
                                       Set<String> globalUsedSignatures,
                                       List<RowCandidate> globalSelectedRows,
                                       Map<String, Integer> globalSelectedTopicCounts,
                                       int target,
                                       TopicLanePlanner topicLanePlanner) {
        while (selected.size() < target) {
            LaneSelectionDecision next = chooseWeightedCandidate(
                    candidates,
                    selected,
                    globalUsedSignatures,
                    globalSelectedRows,
                    globalSelectedTopicCounts,
                    topicLanePlanner
            );
            if (next == null) {
                throw new IllegalStateException("无法补齐 Node 1 桶配额");
            }
            selected.add(next.candidate());
            selectedDecisions.add(next);
            globalUsedSignatures.add(next.candidate().signature());
            globalSelectedRows.add(next.candidate());
            incrementTopicCount(globalSelectedTopicCounts, next.assignedTopicLabel());
        }
    }

    private LaneSelectionDecision chooseWeightedCandidate(List<RowCandidate> candidates,
                                                          List<RowCandidate> selected,
                                                          Set<String> globalUsedSignatures,
                                                          List<RowCandidate> globalSelectedRows,
                                                          Map<String, Integer> globalSelectedTopicCounts,
                                                          TopicLanePlanner topicLanePlanner) {
        if (!topicLanePlanner.active()) {
            return chooseLegacyWeightedCandidate(candidates, selected, globalUsedSignatures);
        }
        String preferredTopic = choosePreferredTopicLane(
                candidates,
                globalUsedSignatures,
                topicLanePlanner,
                globalSelectedTopicCounts
        );
        LaneSelectionDecision preferredCandidate = chooseBestLaneCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                globalSelectedRows,
                topicLanePlanner,
                globalSelectedTopicCounts,
                preferredTopic
        );
        if (preferredCandidate != null) {
            return preferredCandidate;
        }
        return chooseBestLaneCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                globalSelectedRows,
                topicLanePlanner,
                globalSelectedTopicCounts,
                null
        );
    }

    private LaneSelectionDecision chooseLegacyWeightedCandidate(List<RowCandidate> candidates,
                                                                List<RowCandidate> selected,
                                                                Set<String> globalUsedSignatures) {
        RowCandidate best = null;
        int bestCombinedScore = Integer.MIN_VALUE;
        int bestDistance = -1;

        for (RowCandidate candidate : candidates) {
            if (globalUsedSignatures.contains(candidate.signature())) {
                continue;
            }
            int distance = minimumHammingDistance(candidate, selected);
            int combinedScore = candidate.topicAffinityScore() * 10 + candidate.weightScore() * 5;
            if (combinedScore > bestCombinedScore
                    || (combinedScore == bestCombinedScore && distance > bestDistance)) {
                best = candidate;
                bestCombinedScore = combinedScore;
                bestDistance = distance;
            }
        }
        return best == null ? null : new LaneSelectionDecision(best, best.primaryTopicLabel());
    }

    private String choosePreferredTopicLane(List<RowCandidate> candidates,
                                            Set<String> globalUsedSignatures,
                                            TopicLanePlanner topicLanePlanner,
                                            Map<String, Integer> topicCounts) {
        if (!topicLanePlanner.active()) {
            return null;
        }
        String preferredTopic = null;
        double bestLaneScore = Double.NEGATIVE_INFINITY;
        int bestQuotaGap = Integer.MIN_VALUE;
        int lowestSelectedCount = Integer.MAX_VALUE;
        for (TopicLane lane : topicLanePlanner.activeLanes()) {
            if (!hasAvailableCandidateForTopic(candidates, globalUsedSignatures, lane.canonicalTopicLabel())) {
                continue;
            }
            int selectedCount = topicCounts.getOrDefault(lane.canonicalTopicLabel(), 0);
            double laneScore = topicLanePlanner.laneScore(lane.canonicalTopicLabel(), selectedCount);
            int quotaGap = Math.max(0, lane.topicQuotaFloor() - selectedCount);
            if (laneScore > bestLaneScore
                    || (laneScore == bestLaneScore && quotaGap > bestQuotaGap)
                    || (laneScore == bestLaneScore && quotaGap == bestQuotaGap
                    && selectedCount < lowestSelectedCount)
                    || (laneScore == bestLaneScore && quotaGap == bestQuotaGap
                    && selectedCount == lowestSelectedCount
                    && preferredTopic != null
                    && lane.canonicalTopicLabel().compareTo(preferredTopic) < 0)) {
                preferredTopic = lane.canonicalTopicLabel();
                bestLaneScore = laneScore;
                bestQuotaGap = quotaGap;
                lowestSelectedCount = selectedCount;
            }
        }
        return preferredTopic;
    }

    private boolean hasAvailableCandidateForTopic(List<RowCandidate> candidates,
                                                  Set<String> globalUsedSignatures,
                                                  String topicLabel) {
        if (!StringUtils.hasText(topicLabel)) {
            return false;
        }
        for (RowCandidate candidate : candidates) {
            if (!globalUsedSignatures.contains(candidate.signature())
                    && candidate.topicAffinityScores().getOrDefault(topicLabel, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private LaneSelectionDecision chooseBestLaneCandidate(List<RowCandidate> candidates,
                                                          List<RowCandidate> selected,
                                                          Set<String> globalUsedSignatures,
                                                          List<RowCandidate> globalSelectedRows,
                                                          TopicLanePlanner topicLanePlanner,
                                                          Map<String, Integer> topicCounts,
                                                          String preferredTopic) {
        LaneSelectionDecision priorityBound = chooseBestLaneCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                globalSelectedRows,
                topicLanePlanner,
                topicCounts,
                preferredTopic,
                true
        );
        return priorityBound != null
                ? priorityBound
                : chooseBestLaneCandidate(
                candidates,
                selected,
                globalUsedSignatures,
                globalSelectedRows,
                topicLanePlanner,
                topicCounts,
                preferredTopic,
                false
        );
    }

    private LaneSelectionDecision chooseBestLaneCandidate(List<RowCandidate> candidates,
                                                          List<RowCandidate> selected,
                                                          Set<String> globalUsedSignatures,
                                                          List<RowCandidate> globalSelectedRows,
                                                          TopicLanePlanner topicLanePlanner,
                                                          Map<String, Integer> topicCounts,
                                                          String preferredTopic,
                                                          boolean requirePriorityBinding) {
        RowCandidate best = null;
        String bestAssignedTopic = null;
        double bestLaneScore = Double.NEGATIVE_INFINITY;
        double bestStateScore = Double.NEGATIVE_INFINITY;
        int bestDistance = -1;
        int bestWeight = -1;

        for (RowCandidate candidate : candidates) {
            if (globalUsedSignatures.contains(candidate.signature())) {
                continue;
            }
            String assignedTopicLabel = StringUtils.hasText(preferredTopic)
                    ? preferredTopic
                    : candidate.primaryTopicLabel();
            if (!StringUtils.hasText(assignedTopicLabel)) {
                continue;
            }
            int laneAffinityScore = candidate.topicAffinityScores().getOrDefault(assignedTopicLabel, 0);
            if (laneAffinityScore <= 0) {
                continue;
            }
            if (requirePriorityBinding && laneAffinityScore < MIN_TOPIC_BINDING_SCORE_FOR_PRIORITY) {
                continue;
            }
            int selectedCount = topicCounts.getOrDefault(assignedTopicLabel, 0);
            double laneScore = topicLanePlanner.laneScore(assignedTopicLabel, selectedCount);
            double stateScore = resolveRound3BStateSelectionScore(
                    candidate,
                    globalSelectedRows,
                    topicLanePlanner,
                    topicCounts,
                    assignedTopicLabel,
                    laneAffinityScore
            );
            int distance = minimumHammingDistance(candidate, globalSelectedRows);
            if (laneScore > bestLaneScore
                    || (laneScore == bestLaneScore && stateScore > bestStateScore)
                    || (laneScore == bestLaneScore && stateScore == bestStateScore
                    && distance > bestDistance)
                    || (laneScore == bestLaneScore && stateScore == bestStateScore
                    && distance == bestDistance && candidate.weightScore() > bestWeight)) {
                best = candidate;
                bestAssignedTopic = assignedTopicLabel;
                bestLaneScore = laneScore;
                bestStateScore = stateScore;
                bestDistance = distance;
                bestWeight = candidate.weightScore();
            }
        }
        return best == null ? null : new LaneSelectionDecision(best, bestAssignedTopic);
    }

    private double resolveRound3BStateSelectionScore(RowCandidate candidate,
                                                     List<RowCandidate> selectedRows,
                                                     TopicLanePlanner topicLanePlanner,
                                                     Map<String, Integer> topicCounts,
                                                     String assignedTopicLabel,
                                                     int laneAffinityScore) {
        if (candidate == null) {
            return 0.0d;
        }
        int selectedCount = topicCounts.getOrDefault(assignedTopicLabel, 0);
        double globalCoverageGain = resolveGlobalCoverageGain(candidate, selectedRows);
        double topicAffinity = clamp(laneAffinityScore / 100.0d, 0.0d, 1.0d);
        double stateCoverageDebtBoostApplied = topicLanePlanner.coverageDebtPriority(assignedTopicLabel, selectedCount);
        double topicFreshnessBoostApplied = topicLanePlanner.freshnessApplied(
                assignedTopicLabel,
                selectedCount
        );
        return clamp(
                ROUND3B_STATE_GLOBAL_COVERAGE_WEIGHT * globalCoverageGain
                        + ROUND3B_STATE_TOPIC_AFFINITY_WEIGHT * topicAffinity
                        + ROUND3B_STATE_COVERAGE_DEBT_WEIGHT * stateCoverageDebtBoostApplied
                        + ROUND3B_STATE_FRESHNESS_WEIGHT * topicFreshnessBoostApplied,
                0.0d,
                1.0d
        );
    }

    private double resolveGlobalCoverageGain(RowCandidate candidate, List<RowCandidate> selectedRows) {
        double orthogonalityGain = minimumHammingDistance(candidate, selectedRows) / (double) DIMENSION_ORDER.size();
        double stateNoveltyGain = resolveStateNoveltyGain(candidate, selectedRows);
        return clamp(0.60d * orthogonalityGain + 0.40d * stateNoveltyGain, 0.0d, 1.0d);
    }

    private double resolveStateNoveltyGain(RowCandidate candidate, List<RowCandidate> selectedRows) {
        if (candidate == null) {
            return 0.0d;
        }
        if (selectedRows == null || selectedRows.isEmpty()) {
            return 1.0d;
        }
        Map<String, Integer> seenCodeCounts = countSeenAbstractCodeOccurrences(selectedRows);
        double totalGain = 0.0d;
        for (String dimensionId : DIMENSION_ORDER) {
            String code = candidate.abstractTags().get(dimensionId);
            int seen = seenCodeCounts.getOrDefault(dimensionId + "=" + code, 0);
            totalGain += 1.0d / (1.0d + seen);
        }
        return totalGain / DIMENSION_ORDER.size();
    }

    private Map<String, Integer> countSeenAbstractCodeOccurrences(List<RowCandidate> rows) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (RowCandidate row : rows) {
            if (row == null) {
                continue;
            }
            for (String dimensionId : DIMENSION_ORDER) {
                String code = row.abstractTags().get(dimensionId);
                counts.merge(dimensionId + "=" + code, 1, Integer::sum);
            }
        }
        return counts;
    }

    private void incrementTopicCount(Map<String, Integer> topicCounts, String topicLabel) {
        if (topicCounts == null || !StringUtils.hasText(topicLabel)) {
            return;
        }
        topicCounts.merge(topicLabel, 1, Integer::sum);
    }

    private Map<String, Integer> countSelectedTopicOccurrences(List<RowCandidate> rows) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (RowCandidate row : rows) {
            if (row == null || !StringUtils.hasText(row.primaryTopicLabel())) {
                continue;
            }
            counts.merge(row.primaryTopicLabel(), 1, Integer::sum);
        }
        return counts;
    }

    private TopicLanePlanner buildTopicLanePlanner(
            LightConeV43TopicStateProjectionSupport.ProjectionRuntimeContext projectionContext) {
        if (projectionContext == null || !projectionContext.active()) {
            return TopicLanePlanner.inactive();
        }
        List<TopicLane> lanes = projectionContext.projections().stream()
                .map(projection -> new TopicLane(
                        projection.topicId(),
                        projection.canonicalTopicLabel(),
                        projection.coverageFloor(),
                        projection.topicQuotaFloor(),
                        projection.topicFreshnessBoost(),
                        projection.stateCoverageDebtBoost()
                ))
                .toList();
        return new TopicLanePlanner(lanes);
    }

    private CandidateTopicAffinity resolveTopicAffinity(
            Map<String, String> abstractTags,
            LightConeV43TopicStateProjectionSupport.ProjectionRuntimeContext projectionContext) {
        if (projectionContext == null || !projectionContext.active()) {
            return CandidateTopicAffinity.inactive();
        }
        List<TopicAffinityScore> scores = new ArrayList<>();
        for (LightConeV43TopicStateProjectionSupport.TopicStateProjection projection : projectionContext.projections()) {
            double dimensionAffinity = resolveDimensionAffinity(abstractTags, projection);
            double biasAffinity = resolveBiasAffinity(abstractTags, projection.stateBias());
            double laneAdjustment = 1.0d
                    + 0.05d * projection.stateCoverageDebtBoost()
                    + 0.05d * projection.topicFreshnessBoost();
            double topicScore = clamp(
                    (
                            0.75d * dimensionAffinity
                                    + 0.25d * Math.max(0.0d, biasAffinity)
                                    - 0.15d * Math.max(0.0d, -biasAffinity)
                    ) * laneAdjustment,
                    0.0d,
                    1.0d
            );
            scores.add(new TopicAffinityScore(projection.canonicalTopicLabel(), topicScore));
        }
        if (scores.isEmpty()) {
            return CandidateTopicAffinity.inactive();
        }
        scores.sort((left, right) -> Double.compare(right.score(), left.score()));
        LinkedHashMap<String, Integer> affinityScoresByTopic = new LinkedHashMap<>();
        for (TopicAffinityScore score : scores) {
            affinityScoresByTopic.put(score.topicLabel(), (int) Math.round(score.score() * 100.0d));
        }
        double totalAffinity = scores.getFirst().score();
        double secondaryContribution = 0.0d;
        for (int index = 1; index < scores.size(); index++) {
            secondaryContribution += scores.get(index).score() * 0.12d;
        }
        totalAffinity = Math.min(1.0d, totalAffinity + Math.min(0.25d, secondaryContribution));
        return new CandidateTopicAffinity(
                (int) Math.round(totalAffinity * 100.0d),
                scores.getFirst().topicLabel(),
                Map.copyOf(affinityScoresByTopic)
        );
    }

    private double resolveDimensionAffinity(
            Map<String, String> abstractTags,
            LightConeV43TopicStateProjectionSupport.TopicStateProjection projection) {
        double weighted = 0.0d;
        for (String dimensionId : DIMENSION_ORDER) {
            String code = abstractTags.get(dimensionId);
            weighted += normalizedVariableWeight(dimensionId, code)
                    * projection.dimensionWeights().getOrDefault(dimensionId, 0.0d);
        }
        return clamp(weighted / DIMENSION_ORDER.size(), 0.0d, 1.0d);
    }

    private double resolveBiasAffinity(
            Map<String, String> abstractTags,
            List<LightConeV43TopicStateProjectionSupport.StateBiasRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return 0.0d;
        }
        double totalBias = 0.0d;
        for (LightConeV43TopicStateProjectionSupport.StateBiasRule rule : rules) {
            if (rule.matches(abstractTags)) {
                totalBias += rule.biasScore();
            }
        }
        return clamp(totalBias, -1.0d, 1.0d);
    }

    private double normalizedVariableWeight(String dimensionId, String code) {
        Map<String, Integer> weights = VARIABLE_WEIGHTS.get(dimensionId);
        if (weights == null || weights.isEmpty() || !StringUtils.hasText(code)) {
            return 0.0d;
        }
        Integer rawWeight = weights.get(code);
        if (rawWeight == null) {
            return 0.0d;
        }
        int min = weights.values().stream().min(Integer::compareTo).orElse(rawWeight);
        int max = weights.values().stream().max(Integer::compareTo).orElse(rawWeight);
        if (max <= min) {
            return 1.0d;
        }
        return (rawWeight - min) / (double) (max - min);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int coverageCount(RowCandidate candidate, Set<String> remainingPairs) {
        int covered = 0;
        for (String pair : candidate.coveredPairs()) {
            if (remainingPairs.contains(pair)) {
                covered++;
            }
        }
        return covered;
    }

    private int minimumHammingDistance(RowCandidate candidate, List<RowCandidate> selected) {
        if (selected.isEmpty()) {
            return DIMENSION_ORDER.size();
        }
        int minimum = Integer.MAX_VALUE;
        for (RowCandidate existing : selected) {
            minimum = Math.min(minimum, hammingDistance(candidate.abstractTags(), existing.abstractTags()));
        }
        return minimum;
    }

    private int hammingDistance(Map<String, String> left, Map<String, String> right) {
        int distance = 0;
        for (String dimensionId : DIMENSION_ORDER) {
            if (!Objects.equals(left.get(dimensionId), right.get(dimensionId))) {
                distance++;
            }
        }
        return distance;
    }

    private List<ProbeBlueprint> toBlueprints(InputContext input,
                                              List<LaneSelectionDecision> rows,
                                              BucketType bucketType,
                                              List<TemplatePrototype> templatePrototypes) {
        List<ProbeBlueprint> blueprints = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            LaneSelectionDecision rowDecision = rows.get(index);
            RowCandidate row = rowDecision.candidate();
            TemplatePrototype templatePrototype = templatePrototypes == null ? null : templatePrototypes.get(index);
            String templateText = templatePrototype == null ? null : templatePrototype.templateText();
            RealizationPlan realizationPlan = probeSimulationPlanningService.plan(
                    bucketType,
                    row.abstractTags(),
                    row.translatedTags(),
                    templateText,
                    templatePrototype == null ? null : templatePrototype.family(),
                    input.contextPackage(),
                    input.topicProjectionContext().projectionByLabel(rowDecision.assignedTopicLabel())
            );
            blueprints.add(new ProbeBlueprint(
                    stableProbeId(input, bucketType, templateText, row.abstractTags()),
                    bucketType,
                    row.abstractTags(),
                    row.translatedTags(),
                    templateText,
                    ProbeStatus.PENDING,
                    realizationPlan
            ));
        }
        return List.copyOf(blueprints);
    }

    private void validateOutput(List<ProbeBlueprint> blueprints,
                                Set<String> allValidPairs,
                                BucketAllocation bucketAllocation) {
        if (blueprints.size() != bucketAllocation.totalTarget()) {
            throw new IllegalStateException("Node 1 蓝图数量与任务单要求不一致");
        }
        long orthogonalCount = blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL).count();
        long paretoCount = blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.PARETO).count();
        long adversarialCount = blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ADVERSARIAL).count();
        if (orthogonalCount != bucketAllocation.orthogonalTarget()
                || paretoCount != bucketAllocation.paretoTarget()
                || adversarialCount != bucketAllocation.adversarialTarget()) {
            throw new IllegalStateException("Node 1 三桶配额与任务单规划不一致");
        }
        validateTemplatePrototypeShape();

        Set<String> probeIds = new LinkedHashSet<>();
        Set<String> orthogonalPairs = new LinkedHashSet<>();
        Set<String> paretoTemplates = new LinkedHashSet<>();
        Set<String> adversarialTemplates = new LinkedHashSet<>();
        Set<String> paretoFamilies = new LinkedHashSet<>();
        Set<String> adversarialFamilies = new LinkedHashSet<>();
        for (ProbeBlueprint blueprint : blueprints) {
            if (!probeIds.add(blueprint.probeId())) {
                throw new IllegalStateException("probeId 必须全局唯一");
            }
            if (!new LinkedHashSet<>(blueprint.abstractTags().keySet()).equals(new LinkedHashSet<>(DIMENSION_ORDER))) {
                throw new IllegalStateException("abstractTags 必须覆盖 D1-D6");
            }
            if (!new LinkedHashSet<>(blueprint.translatedTags().keySet()).equals(new LinkedHashSet<>(DIMENSION_ORDER))) {
                throw new IllegalStateException("translatedTags 必须覆盖 D1-D6");
            }
            if (violatesAnyRule(blueprint.abstractTags())) {
                throw new IllegalStateException("输出含互斥组合: " + blueprint.abstractTags());
            }
            if (blueprint.bucketType() == BucketType.ORTHOGONAL) {
                orthogonalPairs.addAll(coveredPairs(blueprint.abstractTags()));
            } else if (blueprint.bucketType() == BucketType.PARETO) {
                validateTemplateBucketPrompt(blueprint);
                if (!paretoTemplates.add(blueprint.templateText())) {
                    throw new IllegalStateException("Pareto 桶不允许重复模板: " + blueprint.templateText());
                }
                paretoFamilies.add(blueprint.realizationPlan().realizationConstraints().fallbackFamily());
            } else if (blueprint.bucketType() == BucketType.ADVERSARIAL) {
                validateTemplateBucketPrompt(blueprint);
                if (!adversarialTemplates.add(blueprint.templateText())) {
                    throw new IllegalStateException("Adversarial 桶不允许重复模板: " + blueprint.templateText());
                }
                adversarialFamilies.add(blueprint.realizationPlan().realizationConstraints().fallbackFamily());
            }
        }

        if (bucketAllocation.requiresFullPairCoverage() && !orthogonalPairs.equals(allValidPairs)) {
            throw new IllegalStateException("正交桶未完成全部两两覆盖");
        }
        if (paretoCount > 0 && paretoFamilies.size() < Math.min(6L, paretoCount)) {
            throw new IllegalStateException("Pareto 桶 family 展开不足");
        }
        if (adversarialCount > 0 && adversarialFamilies.size() < Math.min(5L, adversarialCount)) {
            throw new IllegalStateException("Adversarial 桶 family 展开不足");
        }
    }

    private void validateTemplatePrototypeShape() {
        if (PARETO_TEMPLATES.size() < MIN_PARETO_TEMPLATE_CATALOG_SIZE) {
            throw new IllegalStateException("Pareto prototype 目录至少需要 " + MIN_PARETO_TEMPLATE_CATALOG_SIZE + " 条");
        }
        if (ADVERSARIAL_TEMPLATES.size() < MIN_ADVERSARIAL_TEMPLATE_CATALOG_SIZE) {
            throw new IllegalStateException("Adversarial prototype 目录至少需要 " + MIN_ADVERSARIAL_TEMPLATE_CATALOG_SIZE + " 条");
        }
    }

    private void validateTemplateBucketPrompt(ProbeBlueprint blueprint) {
        if (blueprint.templateText() == null || blueprint.templateText().isBlank()) {
            throw new IllegalStateException("模板桶必须显式提供 templateText");
        }
        if (blueprint.realizationPlan() == null) {
            throw new IllegalStateException("模板桶必须携带 realizationPlan");
        }
        if (!blueprint.templateText().contains("{category}")
                && !blueprint.templateText().contains("{brandName}")
                && !blueprint.templateText().contains("{competitor}")) {
            throw new IllegalStateException("模板桶必须保留至少一个占位语义锚点");
        }
        if (TEMPLATE_DISALLOWED_FRAGMENTS.stream().anyMatch(blueprint.templateText()::contains)) {
            throw new IllegalStateException("模板桶不能包含内部术语: " + blueprint.templateText());
        }
    }

    private Set<String> coveredPairs(Map<String, String> abstractTags) {
        LinkedHashSet<String> pairs = new LinkedHashSet<>();
        for (int leftIndex = 0; leftIndex < DIMENSION_ORDER.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < DIMENSION_ORDER.size(); rightIndex++) {
                String leftDimension = DIMENSION_ORDER.get(leftIndex);
                String rightDimension = DIMENSION_ORDER.get(rightIndex);
                pairs.add(pairKey(
                        leftDimension,
                        abstractTags.get(leftDimension),
                        rightDimension,
                        abstractTags.get(rightDimension)
                ));
            }
        }
        return Collections.unmodifiableSet(pairs);
    }

    private boolean violatesAnyRule(Map<String, String> tags) {
        for (MutualExclusionRule rule : MUTUAL_EXCLUSION_RULES) {
            if (rule.violates(tags)) {
                return true;
            }
        }
        return false;
    }

    private boolean violatesPair(String leftDimension, String leftCode, String rightDimension, String rightCode) {
        for (MutualExclusionRule rule : MUTUAL_EXCLUSION_RULES) {
            if (rule.violates(leftDimension, leftCode, rightDimension, rightCode)) {
                return true;
            }
        }
        return false;
    }

    private String stableProbeId(InputContext input,
                                 BucketType bucketType,
                                 String templateText,
                                 Map<String, String> abstractTags) {
        String raw = input.brandName()
                + "|"
                + input.category()
                + "|"
                + bucketType.name()
                + "|"
                + (templateText == null ? "" : templateText)
                + "|"
                + signature(abstractTags);
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String signature(Map<String, String> abstractTags) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < DIMENSION_ORDER.size(); index++) {
            if (index > 0) {
                builder.append('|');
            }
            String dimensionId = DIMENSION_ORDER.get(index);
            builder.append(dimensionId).append('=').append(abstractTags.get(dimensionId));
        }
        return builder.toString();
    }

    private String pairKey(String leftDimension, String leftCode, String rightDimension, String rightCode) {
        return leftDimension + "=" + leftCode + "|" + rightDimension + "=" + rightCode;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private record InputContext(
            String brandName,
            String category,
            Map<String, DimensionContext> dimensions,
            EngineTaskContextPackage contextPackage,
            LightConeV43TopicStateProjectionSupport.ProjectionRuntimeContext topicProjectionContext
    ) {
        private DimensionContext dimension(String dimensionId) {
            return dimensions.get(dimensionId);
        }
    }

    private record DimensionContext(
            String dimensionId,
            List<String> abstractCodes,
            Map<String, String> translatedByCode
    ) {
    }

    private record RowCandidate(
            String signature,
            Map<String, String> abstractTags,
            Map<String, String> translatedTags,
            Set<String> coveredPairs,
            int weightScore,
            int topicAffinityScore,
            String primaryTopicLabel,
            Map<String, Integer> topicAffinityScores
    ) {
        private RowCandidate {
            topicAffinityScores = topicAffinityScores == null ? Map.of() : Map.copyOf(topicAffinityScores);
        }
    }

    private record LaneSelectionDecision(
            RowCandidate candidate,
            String assignedTopicLabel
    ) {
    }

    private record TopicLane(
            String topicId,
            String canonicalTopicLabel,
            int coverageFloor,
            int topicQuotaFloor,
            double topicFreshnessBoost,
            double stateCoverageDebtBoost
    ) {
        private TopicLane {
            topicId = topicId == null ? "" : topicId.trim();
            canonicalTopicLabel = canonicalTopicLabel == null ? "" : canonicalTopicLabel.trim();
            coverageFloor = Math.max(0, coverageFloor);
            topicQuotaFloor = Math.max(0, topicQuotaFloor);
            topicFreshnessBoost = Math.max(0.0d, Math.min(1.0d, topicFreshnessBoost));
            stateCoverageDebtBoost = Math.max(0.0d, Math.min(1.0d, stateCoverageDebtBoost));
        }
    }

    private record TopicLanePlanner(
            List<TopicLane> activeLanes
    ) {
        private TopicLanePlanner {
            activeLanes = activeLanes == null ? List.of() : List.copyOf(activeLanes);
        }

        private static TopicLanePlanner inactive() {
            return new TopicLanePlanner(List.of());
        }

        private boolean active() {
            return !activeLanes.isEmpty();
        }

        private double laneScore(String topicLabel, int selectedCount) {
            TopicLane lane = lane(topicLabel);
            if (lane == null) {
                return 0.0d;
            }
            double quotaDebtScore = lane.topicQuotaFloor() <= 0
                    ? 0.0d
                    : Math.max(0.0d, lane.topicQuotaFloor() - selectedCount) / (double) lane.topicQuotaFloor();
            double freshnessPriority = freshnessApplied(topicLabel, selectedCount);
            double coverageDebtPriority = coverageDebtPriority(topicLabel, selectedCount);
            return Math.max(
                    quotaDebtScore,
                    clamp(
                            ROUND3B_TOPIC_LANE_QUOTA_DEBT_WEIGHT * quotaDebtScore
                                    + ROUND3B_TOPIC_LANE_FRESHNESS_WEIGHT * freshnessPriority
                                    + ROUND3B_TOPIC_LANE_COVERAGE_DEBT_WEIGHT * coverageDebtPriority,
                            0.0d,
                            1.0d
                    )
            );
        }

        private double coverageDebtPriority(String topicLabel, int selectedCount) {
            TopicLane lane = lane(topicLabel);
            if (lane == null) {
                return 0.0d;
            }
            return selectedCount >= lane.coverageFloor() ? 0.0d : lane.stateCoverageDebtBoost();
        }

        private double freshnessApplied(String topicLabel, int selectedCount) {
            TopicLane lane = lane(topicLabel);
            if (lane == null) {
                return 0.0d;
            }
            double activeCoverageDebt = coverageDebtPriority(topicLabel, selectedCount);
            if (selectedCount >= lane.topicQuotaFloor() && activeCoverageDebt <= 0.0d) {
                return 0.0d;
            }
            double freshness = lane.topicFreshnessBoost();
            if (activeCoverageDebt >= HIGH_COVERAGE_DEBT_THRESHOLD) {
                freshness *= 0.5d;
            }
            return clamp(freshness, 0.0d, 1.0d);
        }

        private TopicLane lane(String topicLabel) {
            if (!StringUtils.hasText(topicLabel)) {
                return null;
            }
            for (TopicLane lane : activeLanes) {
                if (topicLabel.equals(lane.canonicalTopicLabel())) {
                    return lane;
                }
            }
            return null;
        }
    }

    private record TopicAffinityScore(
            String topicLabel,
            double score
    ) {
    }

    private record CandidateTopicAffinity(
            int score,
            String primaryTopicLabel,
            Map<String, Integer> affinityScoresByTopic
    ) {
        private static CandidateTopicAffinity inactive() {
            return new CandidateTopicAffinity(0, null, Map.of());
        }
    }

    private record MutualExclusionRule(
            String leftDimension,
            String leftCode,
            String rightDimension,
            String rightCode
    ) {
        private boolean violates(Map<String, String> tags) {
            return leftCode.equals(tags.get(leftDimension)) && rightCode.equals(tags.get(rightDimension));
        }

        private boolean violates(String firstDimension, String firstCode, String secondDimension, String secondCode) {
            return (leftDimension.equals(firstDimension)
                    && leftCode.equals(firstCode)
                    && rightDimension.equals(secondDimension)
                    && rightCode.equals(secondCode))
                    || (leftDimension.equals(secondDimension)
                    && leftCode.equals(secondCode)
                    && rightDimension.equals(firstDimension)
                    && rightCode.equals(firstCode));
        }
    }

    private record TemplatePrototype(
            String family,
            String templateText
    ) {
        private TemplatePrototype {
            family = family == null ? "" : family.trim();
            templateText = templateText == null ? "" : templateText.trim();
            if (family.isEmpty() || templateText.isEmpty()) {
                throw new IllegalArgumentException("TemplatePrototype 不能为空");
            }
        }
    }

    private record BucketAllocation(
            int orthogonalTarget,
            int paretoTarget,
            int adversarialTarget,
            int fullCoverageFloor
    ) {
        private int totalTarget() {
            return orthogonalTarget + paretoTarget + adversarialTarget;
        }

        private boolean requiresFullPairCoverage() {
            return orthogonalTarget >= fullCoverageFloor;
        }
    }
}
