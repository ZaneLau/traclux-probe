package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeStatus;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrthogonalMatrixServiceTest {

    private static final List<String> TEMPLATE_DISALLOWED_FRAGMENTS = List.of(
            "类别", "维度", "框架", "信号", "门槛", "误判", "复核", "交叉验证"
    );

    private final OrthogonalMatrixService orthogonalMatrixService = new OrthogonalMatrixService();

    @Test
    void shouldGenerate100BlueprintsWithRequiredBucketSplitAndPendingStatus() {
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(samplePool(), 100);

        assertEquals(100, blueprints.size());
        assertEquals(50, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL).count());
        assertEquals(30, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.PARETO).count());
        assertEquals(20, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ADVERSARIAL).count());
        assertTrue(blueprints.stream().allMatch(blueprint -> blueprint.status() == ProbeStatus.PENDING));
    }

    @Test
    void shouldRespectCompressedTaskOrderTargetWithoutFallingBackToFixedHundred() {
        CategoryVariablePool pool = samplePool();
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(pool, 30);
        List<ProbeBlueprint> orthogonal = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .toList();
        Set<String> expectedPairs = allExpectedPairs(pool);
        Set<String> actualPairs = orthogonal.stream()
                .flatMap(blueprint -> coveredPairs(blueprint.abstractTags()).stream())
                .collect(Collectors.toSet());

        assertEquals(30, blueprints.size());
        assertEquals(expectedPairs, actualPairs);
        assertEquals(30, blueprints.stream().map(ProbeBlueprint::probeId).collect(Collectors.toCollection(LinkedHashSet::new)).size());
        assertTrue(blueprints.stream().allMatch(blueprint -> blueprint.status() == ProbeStatus.PENDING));
    }

    @Test
    void shouldKeepPairCoverageWhenTaskOrderRequestsFiftyBlueprints() {
        CategoryVariablePool pool = samplePool();
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(pool, 50);
        List<ProbeBlueprint> orthogonal = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .toList();

        Set<String> expectedPairs = allExpectedPairs(pool);
        Set<String> actualPairs = orthogonal.stream()
                .flatMap(blueprint -> coveredPairs(blueprint.abstractTags()).stream())
                .collect(Collectors.toSet());

        assertEquals(50, blueprints.size());
        assertEquals(expectedPairs, actualPairs);
        assertEquals(50L, blueprints.stream().map(ProbeBlueprint::probeId).distinct().count());
    }

    @Test
    void shouldScaleBucketsForExpandedTaskOrderTargets() {
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(samplePool(), 300);

        assertEquals(300, blueprints.size());
        assertEquals(150, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL).count());
        assertEquals(90, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.PARETO).count());
        assertEquals(60, blueprints.stream().filter(blueprint -> blueprint.bucketType() == BucketType.ADVERSARIAL).count());
    }

    @Test
    void shouldKeepAbstractAndTranslatedTagsSeparatedAndDeterministic() {
        List<ProbeBlueprint> first = orthogonalMatrixService.generate(samplePool(), 100);
        List<ProbeBlueprint> second = orthogonalMatrixService.generate(samplePool(), 100);

        ProbeBlueprint orthogonal = first.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .findFirst()
                .orElseThrow();
        ProbeBlueprint pareto = first.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.PARETO)
                .findFirst()
                .orElseThrow();
        ProbeBlueprint adversarial = first.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ADVERSARIAL)
                .findFirst()
                .orElseThrow();

        assertEquals(Set.of("D1", "D2", "D3", "D4", "D5", "D6"), orthogonal.abstractTags().keySet());
        assertEquals(Set.of("D1", "D2", "D3", "D4", "D5", "D6"), orthogonal.translatedTags().keySet());
        assertTrue(CssmHiAbstractStates.D1_COGNITIVE_ANOMALY.contains(orthogonal.abstractTags().get("D1")));
        assertTrue(samplePool().d1CognitiveAnomaly().variables().contains(orthogonal.translatedTags().get("D1")));
        assertNotEquals(orthogonal.abstractTags(), orthogonal.translatedTags());
        assertTrue(first.stream().allMatch(blueprint -> blueprint.realizationPlan() != null));
        assertEquals("human_colloquial_question", orthogonal.realizationPlan().simulationIntentProfile().naturalnessTarget());
        assertEquals("natural_question_only", orthogonal.realizationPlan().realizationConstraints().questionFormPolicy());
        assertNull(orthogonal.templateText());
        assertTrue(pareto.templateText().contains("{"));
        assertTrue(adversarial.templateText().contains("{"));
        assertTrue(pareto.realizationPlan().realizationConstraints().fallbackFamily().startsWith("pareto_"));
        assertTrue(adversarial.realizationPlan().realizationConstraints().fallbackFamily().startsWith("adversarial_"));

        assertEquals(
                first.stream().map(ProbeBlueprint::probeId).toList(),
                second.stream().map(ProbeBlueprint::probeId).toList()
        );
        assertEquals(
                first.size(),
                first.stream().map(ProbeBlueprint::probeId).collect(Collectors.toCollection(LinkedHashSet::new)).size()
        );
    }

    @Test
    void shouldCoverAllValidPairsAndPruneMutualExclusionsInOrthogonalBucket() {
        CategoryVariablePool pool = samplePool();
        List<ProbeBlueprint> orthogonal = orthogonalMatrixService.generate(pool, 100).stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .toList();

        Set<String> expectedPairs = allExpectedPairs(pool);
        Set<String> actualPairs = orthogonal.stream()
                .flatMap(blueprint -> coveredPairs(blueprint.abstractTags()).stream())
                .collect(Collectors.toSet());

        assertEquals(expectedPairs, actualPairs);
        assertFalse(orthogonal.stream().anyMatch(blueprint -> violatesMutualExclusion(blueprint.abstractTags())));
    }

    @Test
    void shouldKeepParetoAndAdversarialTemplatesUniqueAndFamilyDistributed() {
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(samplePool(), 100);

        List<ProbeBlueprint> pareto = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.PARETO)
                .toList();
        List<ProbeBlueprint> adversarial = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ADVERSARIAL)
                .toList();

        assertEquals(30, pareto.size());
        assertEquals(20, adversarial.size());
        assertEquals(30, pareto.stream().map(ProbeBlueprint::templateText).collect(Collectors.toSet()).size());
        assertEquals(20, adversarial.stream().map(ProbeBlueprint::templateText).collect(Collectors.toSet()).size());
        assertTrue(pareto.stream()
                .map(blueprint -> blueprint.realizationPlan().realizationConstraints().fallbackFamily())
                .collect(Collectors.toSet()).size() >= 6);
        assertTrue(adversarial.stream()
                .map(blueprint -> blueprint.realizationPlan().realizationConstraints().fallbackFamily())
                .collect(Collectors.toSet()).size() >= 5);
    }

    @Test
    void shouldRejectInternalTermsInTemplateCatalog() {
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(samplePool(), 100);

        List<ProbeBlueprint> templateBuckets = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() != BucketType.ORTHOGONAL)
                .toList();

        assertTrue(templateBuckets.stream()
                .map(ProbeBlueprint::templateText)
                .allMatch(template -> TEMPLATE_DISALLOWED_FRAGMENTS.stream().noneMatch(template::contains)));
    }

    @Test
    void shouldCarryMinusOnePlanningHintsIntoGeneratedBlueprints() {
        List<ProbeBlueprint> blueprints = orthogonalMatrixService.generate(samplePoolWithContext(), 100);

        ProbeBlueprint orthogonal = blueprints.stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .findFirst()
                .orElseThrow();

        assertTrue(orthogonal.realizationPlan().realizationConstraints().softSemanticHints()
                .contains("SCENE:家庭常备"));
        assertTrue(orthogonal.realizationPlan().realizationConstraints().softSemanticHints()
                .contains("DESCRIBE:家里常备喝的矿泉水"));
        assertTrue(orthogonal.realizationPlan().realizationConstraints().softSemanticHints()
                .contains("DECISION:口感更稳"));
    }

    @Test
    void shouldResolveHigherTopicAffinityForProjectionMatchedState() {
        EngineTaskContextPackage contextPackage = samplePoolWithTopicProjectionBias().contextPackage();

        int matchedScore = orthogonalMatrixService.topicAffinityScore(
                Map.of(
                        "D1", "initiation",
                        "D2", "informational_undirected",
                        "D3", "creation_idleness",
                        "D4", "severely_depleted",
                        "D5", "reflective",
                        "D6", "recognition"
                ),
                contextPackage
        );
        int unmatchedScore = orthogonalMatrixService.topicAffinityScore(
                Map.of(
                        "D1", "verification",
                        "D2", "resource_acquisition",
                        "D3", "understanding_competence",
                        "D4", "optimal",
                        "D5", "acute_emergency",
                        "D6", "specification"
                ),
                contextPackage
        );

        assertTrue(matchedScore > unmatchedScore);
    }

    @Test
    void shouldAssignAtLeastOneRunSlotPerRealProblemSpaceWhenRound3BSelectorIsActive() {
        CategoryVariablePool pool = samplePoolWithRound3BQuotaSelector();

        Map<String, Integer> topicCounts = orthogonalMatrixService.assignedTopicCounts(pool, 12);

        assertTrue(topicCounts.getOrDefault("品牌选择", 0) >= 1, topicCounts::toString);
        assertTrue(topicCounts.getOrDefault("风险异议", 0) >= 1, topicCounts::toString);
        assertTrue(topicCounts.getOrDefault("场景适配", 0) >= 1, topicCounts::toString);
        assertTrue(topicCounts.getOrDefault("质量证据", 0) >= 1, topicCounts::toString);
    }

    @Test
    void shouldKeepPairCoverageWhenRound3BSelectorIsActive() {
        CategoryVariablePool pool = samplePoolWithRound3BQuotaSelector();

        List<ProbeBlueprint> orthogonal = orthogonalMatrixService.generate(pool, 100).stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .toList();
        Set<String> actualPairs = orthogonal.stream()
                .flatMap(blueprint -> coveredPairs(blueprint.abstractTags()).stream())
                .collect(Collectors.toSet());

        assertEquals(allExpectedPairs(pool), actualPairs);
    }

    @Test
    void shouldRejectPoolWhenAbstractAndTranslatedContractsDrift() {
        CategoryVariablePool invalidPool = new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("完全不懂怎么选", "越看越乱", "方向清楚了")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("想知道答案", "先随便看看", "想找方法", "想直接买")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("为了安全", "想融入圈子", "想搞懂原理", "就想特别点", "图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("脑子转不动", "还有点余力", "还能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("马上就要用", "还有几天", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("直接搜答案", "随便刷刷看", "跟AI聊聊"))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orthogonalMatrixService.generate(invalidPool, 100));

        assertTrue(ex.getMessage().contains("数量不一致"));
    }

    private Set<String> allExpectedPairs(CategoryVariablePool pool) {
        Map<String, List<String>> values = Map.of(
                "D1", pool.d1CognitiveAnomaly().abstractCodes(),
                "D2", pool.d2TeleologicalGoal().abstractCodes(),
                "D3", pool.d3AxiologicalDrive().abstractCodes(),
                "D4", pool.d4CognitiveLoad().abstractCodes(),
                "D5", pool.d5TimeConstraint().abstractCodes(),
                "D6", pool.d6InteractionModality().abstractCodes()
        );

        LinkedHashSet<String> pairs = new LinkedHashSet<>();
        List<String> dimensions = CssmHiAbstractStates.DIMENSION_IDS;
        for (int leftIndex = 0; leftIndex < dimensions.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < dimensions.size(); rightIndex++) {
                String leftDimension = dimensions.get(leftIndex);
                String rightDimension = dimensions.get(rightIndex);
                for (String leftCode : values.get(leftDimension)) {
                    for (String rightCode : values.get(rightDimension)) {
                        if (violatesMutualExclusion(leftDimension, leftCode, rightDimension, rightCode)) {
                            continue;
                        }
                        pairs.add(pairKey(leftDimension, leftCode, rightDimension, rightCode));
                    }
                }
            }
        }
        return pairs;
    }

    private Set<String> coveredPairs(Map<String, String> abstractTags) {
        LinkedHashSet<String> pairs = new LinkedHashSet<>();
        List<String> dimensions = CssmHiAbstractStates.DIMENSION_IDS;
        for (int leftIndex = 0; leftIndex < dimensions.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < dimensions.size(); rightIndex++) {
                String leftDimension = dimensions.get(leftIndex);
                String rightDimension = dimensions.get(rightIndex);
                pairs.add(pairKey(
                        leftDimension,
                        abstractTags.get(leftDimension),
                        rightDimension,
                        abstractTags.get(rightDimension)
                ));
            }
        }
        return pairs;
    }

    private boolean violatesMutualExclusion(Map<String, String> abstractTags) {
        return violatesMutualExclusion("D1", abstractTags.get("D1"), "D4", abstractTags.get("D4"))
                || violatesMutualExclusion("D5", abstractTags.get("D5"), "D6", abstractTags.get("D6"));
    }

    private boolean violatesMutualExclusion(String leftDimension, String leftCode, String rightDimension, String rightCode) {
        return ("D1".equals(leftDimension) && "verification".equals(leftCode) && "D4".equals(rightDimension) && "severely_depleted".equals(rightCode))
                || ("D4".equals(leftDimension) && "severely_depleted".equals(leftCode) && "D1".equals(rightDimension) && "verification".equals(rightCode))
                || ("D5".equals(leftDimension) && "acute_emergency".equals(leftCode) && "D6".equals(rightDimension) && "dialogic".equals(rightCode))
                || ("D6".equals(leftDimension) && "dialogic".equals(leftCode) && "D5".equals(rightDimension) && "acute_emergency".equals(rightCode));
    }

    private String pairKey(String leftDimension, String leftCode, String rightDimension, String rightCode) {
        return leftDimension + "=" + leftCode + "|" + rightDimension + "=" + rightCode;
    }

    private CategoryVariablePool samplePool() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("脑子已经很累了", "还有点余力分析", "现在能冷静判断")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("马上就得用上", "还有几天缓冲", "暂时不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"))
        );
    }

    private CategoryVariablePool samplePoolWithContext() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("脑子已经很累了", "还有点余力分析", "现在能冷静判断")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("马上就得用上", "还有几天缓冲", "暂时不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("直接搜个答案", "先随便刷一刷", "打算跟AI聊聊")),
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "瓶装饮用水",
                        List.of("怡宝", "百岁山"),
                        List.of("矿泉水", "饮用水", "天然水"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("怡宝"),
                        List.of(),
                        List.of(),
                        List.of(
                                new GroundedContextSignal(
                                        MinusOneContextSlots.USAGE_SCENARIO,
                                        "家庭常备",
                                        "benchmark_fixture",
                                        "家庭装场景",
                                        0.92,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DESCRIPTIVE_CUE,
                                        "家里常备喝的矿泉水",
                                        "benchmark_fixture",
                                        "真实口语入口",
                                        0.87,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DECISION_FACTOR,
                                        "口感更稳",
                                        "benchmark_fixture",
                                        "判断点",
                                        0.84,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.COMPARISON_TARGET,
                                        "怡宝",
                                        "benchmark_fixture",
                                        "比较对象",
                                        0.82,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                )
                        ),
                        List.of(),
                        Map.of()
                )
        );
    }

    private CategoryVariablePool samplePoolWithTopicProjectionBias() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("脑子已经很累了", "还有点余力分析", "现在能冷静判断")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("马上就得用上", "还有几天缓冲", "暂时不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("直接搜个答案", "先随便刷一刷", "打算跟AI聊聊")),
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "瓶装饮用水",
                        List.of("怡宝", "百岁山"),
                        List.of("矿泉水", "饮用水", "天然水"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("怡宝"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(
                                LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY,
                                List.of(
                                        Map.ofEntries(
                                                Map.entry("topicId", "topic-lane-bias"),
                                                Map.entry("projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION),
                                                Map.entry("canonicalTopicLabel", "低负荷探索"),
                                                Map.entry("problemSpaceBucket", "general_visibility"),
                                                Map.entry("dimensionWeights", Map.of(
                                                        "D1", 0.05d,
                                                        "D2", 0.05d,
                                                        "D3", 0.05d,
                                                        "D4", 0.05d,
                                                        "D5", 0.05d,
                                                        "D6", 0.05d
                                                )),
                                                Map.entry("stateBias", List.of(
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D1", "initiation",
                                                                        "D2", "informational_undirected",
                                                                        "D3", "creation_idleness",
                                                                        "D4", "severely_depleted",
                                                                        "D5", "reflective",
                                                                        "D6", "recognition"
                                                                ),
                                                                "biasScore", 1.0d,
                                                                "reason", "Round 3-A 测试用强 lane bias"
                                                        )
                                                )),
                                                Map.entry("coverageFloor", 1),
                                                Map.entry("topicQuotaFloor", 1),
                                                Map.entry("topicFreshnessBoost", 1.0d),
                                                Map.entry("stateCoverageDebtBoost", 1.0d),
                                                Map.entry("sourceType", "user_added"),
                                                Map.entry("projectionTrace", Map.of("rawTopicId", "topic-lane-bias"))
                                        )
                                ),
                                LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY,
                                Map.of(
                                        "projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION,
                                        "selectionMode", LightConeV43TopicStateProjectionSupport.SELECTION_MODE,
                                        "activeTopicCount", 1
                                )
                        )
                )
        );
    }

    private CategoryVariablePool samplePoolWithRound3BQuotaSelector() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("脑子已经很累了", "还有点余力分析", "现在能冷静判断")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("马上就得用上", "还有几天缓冲", "暂时不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("直接搜个答案", "先随便刷一刷", "打算跟AI聊聊")),
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "瓶装饮用水",
                        List.of("怡宝", "百岁山"),
                        List.of("矿泉水", "饮用水", "天然水"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("怡宝"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(
                                LightConeV43TopicStateProjectionSupport.PROJECTION_HINT_KEY,
                                List.of(
                                        Map.ofEntries(
                                                Map.entry("topicId", "topic-brand-choice"),
                                                Map.entry("projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION),
                                                Map.entry("canonicalTopicLabel", "品牌选择"),
                                                Map.entry("problemSpaceBucket", "brand_choice"),
                                                Map.entry("dimensionWeights", Map.of(
                                                        "D1", 0.60d,
                                                        "D2", 0.90d,
                                                        "D3", 0.75d,
                                                        "D4", 0.45d,
                                                        "D5", 0.70d,
                                                        "D6", 0.55d
                                                )),
                                                Map.entry("stateBias", List.of(
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D2", "resource_acquisition"
                                                                ),
                                                                "biasScore", 0.60d,
                                                                "reason", "Round 3-B 品牌选择 lane"
                                                        ),
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D6", "recognition"
                                                                ),
                                                                "biasScore", 0.20d,
                                                                "reason", "Round 3-B 品牌选择搜索式入口"
                                                        )
                                                )),
                                                Map.entry("coverageFloor", 2),
                                                Map.entry("topicQuotaFloor", 1),
                                                Map.entry("topicFreshnessBoost", 0.20d),
                                                Map.entry("stateCoverageDebtBoost", 0.40d),
                                                Map.entry("sourceType", "system_generated"),
                                                Map.entry("projectionTrace", Map.of("rawTopicId", "topic-brand-choice"))
                                        ),
                                        Map.ofEntries(
                                                Map.entry("topicId", "topic-risk"),
                                                Map.entry("projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION),
                                                Map.entry("canonicalTopicLabel", "风险异议"),
                                                Map.entry("problemSpaceBucket", "risk_objection"),
                                                Map.entry("dimensionWeights", Map.of(
                                                        "D1", 0.85d,
                                                        "D2", 0.60d,
                                                        "D3", 0.65d,
                                                        "D4", 0.20d,
                                                        "D5", 0.70d,
                                                        "D6", 0.70d
                                                )),
                                                Map.entry("stateBias", List.of(
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D1", "verification"
                                                                ),
                                                                "biasScore", 0.70d,
                                                                "reason", "Round 3-B 风险异议核验态"
                                                        ),
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D5", "reflective"
                                                                ),
                                                                "biasScore", 0.35d,
                                                                "reason", "Round 3-B 风险异议复核态"
                                                        ),
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D6", "specification"
                                                                ),
                                                                "biasScore", 0.30d,
                                                                "reason", "Round 3-B 风险异议结构化提问"
                                                        )
                                                )),
                                                Map.entry("coverageFloor", 2),
                                                Map.entry("topicQuotaFloor", 1),
                                                Map.entry("topicFreshnessBoost", 0.20d),
                                                Map.entry("stateCoverageDebtBoost", 0.40d),
                                                Map.entry("sourceType", "user_added"),
                                                Map.entry("projectionTrace", Map.of("rawTopicId", "topic-risk"))
                                        ),
                                        Map.ofEntries(
                                                Map.entry("topicId", "topic-scenario-fit"),
                                                Map.entry("projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION),
                                                Map.entry("canonicalTopicLabel", "场景适配"),
                                                Map.entry("problemSpaceBucket", "usage_scenario"),
                                                Map.entry("dimensionWeights", Map.of(
                                                        "D1", 0.45d,
                                                        "D2", 0.75d,
                                                        "D3", 0.70d,
                                                        "D4", 0.40d,
                                                        "D5", 0.55d,
                                                        "D6", 0.55d
                                                )),
                                                Map.entry("stateBias", List.of(
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D2", "instructional_active"
                                                                ),
                                                                "biasScore", 0.50d,
                                                                "reason", "Round 3-B 场景适配行动问法"
                                                        ),
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D3", "subsistence_protection"
                                                                ),
                                                                "biasScore", 0.30d,
                                                                "reason", "Round 3-B 场景适配稳妥可用"
                                                        )
                                                )),
                                                Map.entry("coverageFloor", 1),
                                                Map.entry("topicQuotaFloor", 1),
                                                Map.entry("topicFreshnessBoost", 0.75d),
                                                Map.entry("stateCoverageDebtBoost", 0.30d),
                                                Map.entry("sourceType", "user_added"),
                                                Map.entry("projectionTrace", Map.of("rawTopicId", "topic-scenario-fit"))
                                        ),
                                        Map.ofEntries(
                                                Map.entry("topicId", "topic-quality-evidence"),
                                                Map.entry("projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION),
                                                Map.entry("canonicalTopicLabel", "质量证据"),
                                                Map.entry("problemSpaceBucket", "quality_evidence"),
                                                Map.entry("dimensionWeights", Map.of(
                                                        "D1", 0.80d,
                                                        "D2", 0.55d,
                                                        "D3", 0.60d,
                                                        "D4", 0.45d,
                                                        "D5", 0.45d,
                                                        "D6", 0.80d
                                                )),
                                                Map.entry("stateBias", List.of(
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D1", "verification"
                                                                ),
                                                                "biasScore", 0.60d,
                                                                "reason", "Round 3-B 质量证据核验态"
                                                        ),
                                                        Map.of(
                                                                "matchTags", Map.of(
                                                                        "D6", "specification"
                                                                ),
                                                                "biasScore", 0.45d,
                                                                "reason", "Round 3-B 质量证据结构化提问"
                                                        )
                                                )),
                                                Map.entry("coverageFloor", 1),
                                                Map.entry("topicQuotaFloor", 1),
                                                Map.entry("topicFreshnessBoost", 0.75d),
                                                Map.entry("stateCoverageDebtBoost", 0.30d),
                                                Map.entry("sourceType", "user_added"),
                                                Map.entry("projectionTrace", Map.of("rawTopicId", "topic-quality-evidence"))
                                        )
                                ),
                                LightConeV43TopicStateProjectionSupport.PROJECTION_SUMMARY_HINT_KEY,
                                Map.of(
                                        "projectionVersion", LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION,
                                        "selectionMode", LightConeV43TopicStateProjectionSupport.SELECTION_MODE,
                                        "activeTopicCount", 3
                                )
                        )
                )
        );
    }

    private DimensionVariables dimension(String dimensionId, List<String> abstractCodes, List<String> variables) {
        return new DimensionVariables(
                dimensionId,
                CssmHiAbstractStates.DIMENSION_NAMES.get(dimensionId),
                abstractCodes,
                variables
        );
    }
}
