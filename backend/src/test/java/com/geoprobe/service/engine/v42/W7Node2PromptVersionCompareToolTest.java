package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class W7Node2PromptVersionCompareToolTest {

    @Test
    void shouldSummarizePromptVersionComparison() {
        W7Node2PromptVersionCompareTool.ComparisonRow row1 = new W7Node2PromptVersionCompareTool.ComparisonRow(
                "probe-1",
                "美厨物语",
                "儿童餐具",
                "ORTHOGONAL",
                List.of("竞品甲", "竞品乙"),
                "想看竞品甲和竞品乙到底差在哪，官网该先看哪边？",
                new W7Node2PromptVersionCompareTool.RenderSnapshot(
                        "旧问题1",
                        "llm_validated_anchor_repaired",
                        "LLM",
                        "orthogonal_constrained_llm",
                        false,
                        true,
                        "anchor",
                        null,
                        null,
                        null,
                        "node2-v2026-05-01"
                ),
                new W7Node2PromptVersionCompareTool.RenderSnapshot(
                        "竞品甲和竞品乙该先看哪边官网？",
                        "llm_validated_original",
                        "LLM",
                        "orthogonal_constrained_llm",
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        "node2-v2026-05-10"
                )
        );
        W7Node2PromptVersionCompareTool.ComparisonRow row2 = new W7Node2PromptVersionCompareTool.ComparisonRow(
                "probe-2",
                "美厨物语",
                "儿童餐具",
                "ORTHOGONAL",
                List.of("竞品甲", "竞品乙"),
                "想知道竞品甲和竞品乙怎么选。",
                new W7Node2PromptVersionCompareTool.RenderSnapshot(
                        "这几家里怎么选？",
                        "llm_validated_original",
                        "LLM",
                        "orthogonal_constrained_llm",
                        false,
                        false,
                        null,
                        null,
                        "EVERYONE_CHOOSES_PATTERN",
                        null,
                        "node2-v2026-05-01"
                ),
                new W7Node2PromptVersionCompareTool.RenderSnapshot(
                        "旧问题2",
                        "validation_failed_fallback[mechanical]",
                        "DETERMINISTIC_FALLBACK",
                        "orthogonal_fallback_core",
                        true,
                        false,
                        null,
                        "Q_MECHANICAL",
                        null,
                        null,
                        "node2-v2026-05-10"
                )
        );

        W7Node2PromptVersionCompareTool.CompareSummary summary =
                W7Node2PromptVersionCompareTool.summarize(List.of(row1, row2));

        assertEquals(2, summary.sampleCount());
        assertEquals(2, summary.promptChangedCount());
        assertEquals(0, summary.baselineFallbackCount());
        assertEquals(1, summary.candidateFallbackCount());
        assertEquals(1, summary.baselineRepairCount());
        assertEquals(0, summary.candidateRepairCount());
        assertEquals(0, summary.fallbackImprovedCount());
        assertEquals(1, summary.fallbackRegressedCount());
        assertEquals(1, summary.repairImprovedCount());
        assertEquals(0, summary.repairRegressedCount());
        assertEquals(2, summary.sourceExplicitCompetitorCount());
        assertEquals(0, summary.baselineExplicitCompetitorRetainedCount());
        assertEquals(1, summary.candidateExplicitCompetitorRetainedCount());
        assertEquals(1, summary.baselineGenericCompetitorPhraseCount());
        assertEquals(0, summary.candidateGenericCompetitorPhraseCount());
        assertEquals(1, summary.sourceOfficialIntentCount());
        assertEquals(0, summary.baselineOfficialIntentRetainedCount());
        assertEquals(1, summary.candidateOfficialIntentRetainedCount());
        assertEquals(0, summary.baselineDuplicatePromptCount());
        assertEquals(0, summary.candidateDuplicatePromptCount());
        assertEquals(1, summary.baselineMaxPromptRepeatCount());
        assertEquals(1, summary.candidateMaxPromptRepeatCount());
        assertEquals(Map.of("anchor", 1, "EVERYONE_CHOOSES_PATTERN", 1), summary.baselineRuleCounts());
        assertEquals(Map.of("Q_MECHANICAL", 1), summary.candidateRuleCounts());
    }

    @Test
    void shouldCarrySourceProbeTextIntoTemplateBlueprint() throws Exception {
        Method buildRenderContext = W7Node2PromptVersionCompareTool.class.getDeclaredMethod(
                "buildRenderContext",
                Map.class,
                ProbeSimulationPlanningService.class
        );
        buildRenderContext.setAccessible(true);
        Object renderContext = buildRenderContext.invoke(
                null,
                Map.of(
                        "probe_id", "probe-template-1",
                        "brand", "美厨物语",
                        "category", "厨房收纳",
                        "bucket_type", "ADVERSARIAL",
                        "probe_text", "厨房收纳里不想买美厨物语，有没有更值的替代？",
                        "competitors", List.of("懒角落", "纳川"),
                        "state_vector", Map.of(
                                "D1", "initiation",
                                "D2", "resource_acquisition",
                                "D3", "understanding_competence",
                                "D4", "severely_depleted",
                                "D5", "acute_emergency",
                                "D6", "recognition"
                        )
                ),
                new ProbeSimulationPlanningService()
        );

        Method blueprintAccessor = renderContext.getClass().getDeclaredMethod("blueprint");
        blueprintAccessor.setAccessible(true);
        ProbeBlueprint blueprint = (ProbeBlueprint) blueprintAccessor.invoke(renderContext);

        assertNotNull(blueprint);
        assertEquals("厨房收纳里不想买美厨物语，有没有更值的替代？", blueprint.templateText());
    }
}
