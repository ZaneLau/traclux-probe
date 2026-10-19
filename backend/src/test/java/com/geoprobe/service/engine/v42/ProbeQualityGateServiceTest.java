package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeQualityGateServiceTest {

    private final ProbeQualityGateService service = new ProbeQualityGateService();
    private final ProbeSimulationPlanningService planningService = new ProbeSimulationPlanningService();

    @Test
    void shouldPassWhenRenderedProbesAreDiverseAndHumanized() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "矿泉水第一次买，先看哪几个判断点最靠谱？"),
                orthogonal("probe-o2", "饮用水挑的时候，我先看水源还是口感更有用？"),
                pareto("probe-p1", "日常囤水更推荐哪一款？", "pareto_recommendation_shortlist"),
                pareto("probe-p2", "农夫山泉和怡宝放一起看，先比什么最有效？", "pareto_brand_comparison"),
                pareto("probe-p3", "我想先按预算筛一轮，饮用水怎么挑更省事？", "pareto_constraint_filtering"),
                pareto("probe-p4", "瓶装饮用水看哪些信号更不容易踩坑？", "pareto_trust_signal"),
                pareto("probe-p5", "办公室日常喝水的场景，更适合先看哪种产品？", "pareto_scenario_match"),
                pareto("probe-p6", "想快点缩小范围，饮用水可以先排除哪些？", "pareto_search_shortcut"),
                adversarial("probe-a1", "网上有人说农夫山泉最近翻车，这个说法靠谱吗？", "adversarial_rumor_challenge"),
                adversarial("probe-a2", "如果不看品牌宣传，饮用水到底还剩什么差别？", "adversarial_anti_hype"),
                adversarial("probe-a3", "要是直接换成怡宝，实际会差很多吗？", "adversarial_substitute_pressure"),
                adversarial("probe-a4", "只在农夫山泉和百岁山里选，哪种情况更适合各自？", "adversarial_forced_comparison"),
                adversarial("probe-a5", "如果先不考虑农夫山泉，还有哪些饮用水值得看？", "adversarial_brand_exclusion")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertTrue(result.passed());
        assertTrue(result.violations().isEmpty());
        assertEquals(6, result.bucketStats().get(BucketType.PARETO).familyCount());
        assertEquals(5, result.bucketStats().get(BucketType.ADVERSARIAL).familyCount());
    }

    @Test
    void shouldPassSmallSampleFamilyCoverageWhenEachTemplateProbeUsesDistinctFamily() {
        List<RenderedProbe> renderedProbes = List.of(
                pareto("probe-p1", "厨房收纳先推荐哪几个方向？", "pareto_recommendation_shortlist"),
                pareto("probe-p2", "厨房收纳和竞品放一起时，先比哪一点？", "pareto_brand_comparison"),
                pareto("probe-p3", "预算有限时，厨房收纳怎么先筛一轮？", "pareto_constraint_filtering"),
                adversarial("probe-a1", "有人说厨房收纳这类产品都差不多，是真的吗？", "adversarial_anti_hype"),
                adversarial("probe-a2", "如果先不看这个品牌，还有哪些替代方向？", "adversarial_brand_exclusion")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertTrue(result.passed());
        assertTrue(result.violations().isEmpty());
        assertEquals(3, result.bucketStats().get(BucketType.PARETO).familyCount());
        assertEquals(2, result.bucketStats().get(BucketType.ADVERSARIAL).familyCount());
    }

    @Test
    void shouldFailWhenBucketContainsDuplicatePrompts() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "饮用水现在怎么挑更稳？"),
                orthogonal("probe-o2", "饮用水现在怎么挑更稳？")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("重复 probe")));
    }

    @Test
    void shouldIdentifyConcreteRepairTargetsForDuplicateAndBlacklistedPrompts() {
        List<RenderedProbe> renderedProbes = List.of(
                pareto("probe-p1", "厨房收纳怎么选才不踩坑？", "pareto_recommendation_shortlist"),
                pareto("probe-p2", "厨房收纳怎么选才不踩坑？", "pareto_brand_comparison"),
                adversarial("probe-a1", "厨房收纳现在直接上值不值，会不会太普通？", "adversarial_anti_hype")
        );

        List<ProbeQualityGateService.SurfaceRepairTarget> targets = service.identifyRepairTargets(renderedProbes);

        assertEquals(2, targets.size());
        assertEquals("probe-p2", targets.get(0).probeId());
        assertTrue(targets.get(0).reasons().contains(ProbeQualityGateService.SurfaceRepairReason.DUPLICATE));
        assertEquals("probe-a1", targets.get(1).probeId());
        assertTrue(targets.get(1).reasons().contains(ProbeQualityGateService.SurfaceRepairReason.BLACKLISTED));
    }

    @Test
    void shouldFailWhenPromptLooksPseudoHuman() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "想买包装饮用水，但怕喝坏肚子，问问别人再决定，按自己要求找？"),
                orthogonal("probe-o2", "如果现在挑农夫山泉这个包装饮用水，你会先让我看哪几点，少踩坑点？")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("伪人话黑名单")));
    }

    @Test
    void shouldFailWhenPromptLiteralizesUrgencyState() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "急着用，矿泉水现在选哪个更稳，不想踩坑？")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("机械化问句")
                || message.contains("伪人话黑名单")));
    }

    @Test
    void shouldFailWhenPromptContainsQuoteArtifactOrPerfectiveDecisionTail() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "“这几个包装饮用水品牌，主要区别在哪？我比较完就能直接下单了。”？"),
                orthogonal("probe-o2", "帮我确认下哪个包装饮用水品牌靠谱，别踩坑，我直接下单了？"),
                orthogonal("probe-o3", "我想确认一下，这几个包装饮用水品牌到底差别在哪，好直接下单？"),
                orthogonal("probe-o4", "农夫山泉的包装饮用水怎么样，确认没问题我就直接买了，怕踩坑？"),
                orthogonal("probe-o5", "哪个包装饮用水牌子好，我直接买了，顺便也想知道它们到底有啥区别？"),
                orthogonal("probe-o6", "这几种包装饮用水到底有啥区别？我选一个直接买了？"),
                orthogonal("probe-o7", "农夫山泉和怡宝这些包装饮用水，到底差别在哪？我确认一下这个方向就准备买了？"),
                orthogonal("probe-o8", "包装饮用水如果现在就定差别先看哪点？"),
                orthogonal("probe-o9", "包装饮用水现在直接上值不值，会不会太普通？")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("伪人话黑名单")));
    }

    @Test
    void shouldFailWhenOrthogonalBucketCollapsesIntoNearIdenticalFallbackCluster() {
        List<RenderedProbe> renderedProbes = List.of(
                orthogonal("probe-o1", "矿泉水现在买哪个更稳，不想踩坑？"),
                orthogonal("probe-o2", "矿泉水现在买哪个更稳，也想看懂差别？"),
                orthogonal("probe-o3", "矿泉水现在买哪个更稳，大家一般怎么选？")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("重复 probe")
                || message.contains("机械化问句")));
    }

    @Test
    void shouldFailWhenTemplateFamilyCoverageCollapses() {
        List<RenderedProbe> renderedProbes = List.of(
                pareto("probe-p1", "日常囤水更推荐哪一款？", "pareto_recommendation_shortlist"),
                pareto("probe-p2", "想快点选水，先看哪一项最有效？", "pareto_recommendation_shortlist"),
                pareto("probe-p3", "囤水这件事，优先看哪种选择更省心？", "pareto_recommendation_shortlist")
        );

        ProbeQualityGateService.ProbeQualityGateResult result = service.evaluate(renderedProbes);

        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(message -> message.contains("family 展开不足")));
        assertThrows(IllegalStateException.class, () -> service.assertPass(renderedProbes));
    }

    private RenderedProbe orthogonal(String probeId, String prompt) {
        Map<String, String> abstractTags = tags(Map.of(
                "D1", "exploration",
                "D2", "informational_directed",
                "D3", "understanding_competence",
                "D4", "moderate_strain",
                "D5", "anticipatory_constraint",
                "D6", "recognition"
        ));
        Map<String, String> translatedTags = translated(Map.of(
                "D1", "越看越乱",
                "D2", "想听个明确结论",
                "D3", "我想先弄清楚差别"
        ));
        RealizationPlan realizationPlan = planningService.plan(BucketType.ORTHOGONAL, abstractTags, translatedTags, null);
        return new RenderedProbe(
                probeId,
                BucketType.ORTHOGONAL,
                abstractTags,
                translatedTags,
                realizationPlan,
                ProbeExposurePolicy.BRAND_HIDDEN,
                prompt,
                false,
                "LLM",
                "orthogonal_constrained_llm",
                "llm_validated"
        );
    }

    private RenderedProbe pareto(String probeId, String prompt, String family) {
        Map<String, String> abstractTags = tags(Map.of());
        Map<String, String> translatedTags = translated(Map.of());
        RealizationPlan realizationPlan = planningService.plan(
                BucketType.PARETO,
                abstractTags,
                translatedTags,
                "{category}怎么选",
                family
        );
        return new RenderedProbe(
                probeId,
                BucketType.PARETO,
                abstractTags,
                translatedTags,
                realizationPlan,
                ProbeExposurePolicy.BRAND_HIDDEN,
                prompt,
                false,
                "TEMPLATE",
                family,
                "template_rendered"
        );
    }

    private RenderedProbe adversarial(String probeId, String prompt, String family) {
        Map<String, String> abstractTags = tags(Map.of());
        Map<String, String> translatedTags = translated(Map.of());
        RealizationPlan realizationPlan = planningService.plan(
                BucketType.ADVERSARIAL,
                abstractTags,
                translatedTags,
                "听说{brandName}最近有问题",
                family
        );
        return new RenderedProbe(
                probeId,
                BucketType.ADVERSARIAL,
                abstractTags,
                translatedTags,
                realizationPlan,
                ProbeExposurePolicy.BRAND_VISIBLE,
                prompt,
                false,
                "TEMPLATE",
                family,
                "template_rendered"
        );
    }

    private Map<String, String> tags(Map<String, String> overrides) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "exploration");
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", "moderate_strain");
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        tags.putAll(overrides);
        return Map.copyOf(tags);
    }

    private Map<String, String> translated(Map<String, String> overrides) {
        LinkedHashMap<String, String> translated = new LinkedHashMap<>();
        translated.put("D1", "越看越乱");
        translated.put("D2", "想听个明确结论");
        translated.put("D3", "我想先弄清楚差别");
        translated.put("D4", "有点累但还能想");
        translated.put("D5", "还有一点时间");
        translated.put("D6", "像平时搜一搜");
        translated.putAll(overrides);
        return Map.copyOf(translated);
    }
}
