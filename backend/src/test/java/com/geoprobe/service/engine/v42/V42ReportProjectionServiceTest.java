package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node3UsageCostSummary;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import com.geoprobe.dto.engine.v42.ExecutionSignal;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V42ReportProjectionServiceTest {

    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy = mock(V42TargetProbeCountPolicy.class);
    private final V42ReportProjectionService service = new V42ReportProjectionService(new ObjectMapper(), v42TargetProbeCountPolicy);

    V42ReportProjectionServiceTest() {
        when(v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(anyMap(), anyString()))
                .thenReturn(100);
    }

    @Test
    void shouldProjectCanonicalV42ResultIntoPersistedReadModel() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-v42-1")
                .brandName("探镜")
                .category("运动相机")
                .brandWebsite("https://tanjing.example.com")
                .userPrompt("重点看推荐心智")
                .competitorsInput(List.of("Insta360"))
                .edition(1)
                .createdAt(LocalDateTime.parse("2026-03-26T10:00:00"))
                .build();

        V42DiagnosisResult.Task task = new V42DiagnosisResult.Task(
                "diag-v42-1",
                "rpt_diag_v42_1",
                "4.2",
                "COMPLETED",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                96,
                4,
                probeTrace(100)
        );
        V42DiagnosisResult.Report report = new V42DiagnosisResult.Report(
                "diag-v42-1",
                "rpt_diag_v42_1",
                "4.2",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                "methodology",
                "disclaimer",
                kpiSummary(),
                commercialInsights(),
                new DefenseCompletenessSummary(0.75d, 1, List.of(new DefenseCompletenessResult("probe-1", 1.0d, 0.5d, 1.0d, 0.8333d))),
                dashboardCharts(),
                List.of(new V42DiagnosisResult.DisplayNameMapping("kpiSummary.spontaneousMentionRate", "AI 首选率"))
        );

        Map<String, Object> reportData = service.buildReportData(
                diagnosis,
                new V42DiagnosisResult(task, report),
                new DiagnosisAliasConfigData(List.of("探镜相机"), Map.of("Insta360", List.of("影石"))),
                Map.of("billingState", "charged")
        );

        Map<String, Object> meta = castMap(reportData.get("meta"));
        Map<String, Object> executiveSummary = castMap(reportData.get("executiveSummary"));
        Map<String, Object> kpis = castMap(executiveSummary.get("kpis"));
        Map<String, Object> samplingStats = castMap(reportData.get("samplingStats"));
        Map<String, Object> executionIntegrity = castMap(reportData.get("executionIntegrity"));
        Map<String, Object> heroSummary = castMap(reportData.get("heroSummary"));

        assertEquals("4.2", meta.get("engineVersion"));
        assertEquals("charged", meta.get("billingState"));
        assertTrue(meta.containsKey("engineComponents"));
        assertEquals(false, castMap(meta.get("executionIntegrity")).get("degraded"));
        assertTrue(!meta.containsKey("engineVersions"));
        assertFalse(meta.containsKey("compatibilityKpiProjection"));
        assertEquals(0.41d, kpis.get("l1MentionRate"));
        assertEquals(0.27d, kpis.get("l2RecommendRate"));
        assertEquals(0.19d, kpis.get("l3Top3Rate"));
        assertEquals("AI 首选率", castList(heroSummary.get("kpis")).get(2).get("label"));
        assertTrue(reportData.containsKey("task"));
        assertTrue(reportData.containsKey("report"));
        assertEquals(false, reportData.get("executionDegraded"));
        assertEquals(false, reportData.get("cleanRun"));
        assertEquals(0, executionIntegrity.get("signalCount"));
        assertEquals(Node2RuleCatalog.VERSION, meta.get("node2RuleCatalogVersion"));
        assertTrue(reportData.containsKey("competitorRadar"));
        assertTrue(reportData.containsKey("semanticDistanceMap"));
        assertEquals(false, castMap(samplingStats.get("realtimeSearch")).get("enabled"));
        assertTrue(castList(reportData.get("evidenceChain")).isEmpty());
        assertFalse(castList(reportData.get("probeRuntimeTruth")).isEmpty());
        Map<String, Object> probeRuntimeTruth = castList(reportData.get("probeRuntimeTruth")).getFirst();
        assertEquals(List.of(Node2RuleCatalog.RULE_Q_ANCHOR), probeRuntimeTruth.get("validationFailedRuleIds"));
        assertEquals(List.of(Node2RuleCatalog.RULE_B_SURFACE_CONFIRM_BUY, Node2RuleCatalog.RULE_B_RETRY_ANCHOR), probeRuntimeTruth.get("behaviorRuleIds"));
        assertEquals("anchor", probeRuntimeTruth.get("validationRepairApplied"));
        assertEquals(true, probeRuntimeTruth.get("repairUsed"));
        assertEquals(Node2RuleCatalog.RULE_Q_ANCHOR, probeRuntimeTruth.get("fallbackTriggerRule"));
        assertEquals(Node2RuleCatalog.RULE_FAMILY_ORTHOGONAL_SHORT, probeRuntimeTruth.get("fallbackFamilyRuleId"));
        assertEquals(false, castMap(probeRuntimeTruth.get("gateDecisionInput")).get("anchorHit"));
        Map<String, Object> node2RuleCatalog = castMap(reportData.get("node2RuleCatalog"));
        assertEquals(Node2RuleCatalog.VERSION, node2RuleCatalog.get("version"));
        List<Map<String, Object>> catalogEntries = castList(node2RuleCatalog.get("entries"));
        assertTrue(catalogEntries.stream().anyMatch(entry -> Node2RuleCatalog.RULE_Q_ANCHOR.equals(entry.get("ruleId"))));
        assertTrue(catalogEntries.stream().anyMatch(entry -> Node2RuleCatalog.BOUNDARY_TYPE_QUALITY.equals(entry.get("boundaryType"))));
        assertTrue(catalogEntries.stream().anyMatch(entry -> Node2RuleCatalog.RULE_B_SURFACE_CONFIRM_BUY.equals(entry.get("ruleId"))));
        assertTrue(catalogEntries.stream().anyMatch(entry -> Node2RuleCatalog.RULE_FAMILY_ORTHOGONAL_SHORT.equals(entry.get("ruleId"))));
    }

    @Test
    void shouldNotFabricateSourceAttributionCountsWhenEvidenceMissing() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-v42-no-evidence")
                .brandName("探镜")
                .category("运动相机")
                .build();
        V42DiagnosisResult.Task task = new V42DiagnosisResult.Task(
                "diag-v42-no-evidence",
                "rpt_no_evidence",
                "4.2",
                "COMPLETED",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                100,
                0,
                probeTrace(100)
        );
        V42DiagnosisResult.Report report = new V42DiagnosisResult.Report(
                "diag-v42-no-evidence",
                "rpt_no_evidence",
                "4.2",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                "methodology",
                "disclaimer",
                kpiSummary(),
                commercialInsights(),
                new DefenseCompletenessSummary(0.75d, 0, List.of()),
                dashboardCharts(),
                List.of(new V42DiagnosisResult.DisplayNameMapping("kpiSummary.overallMentionRate", "总体提及率"))
        );

        Map<String, Object> reportData = service.buildReportData(
                diagnosis,
                new V42DiagnosisResult(task, report),
                new DiagnosisAliasConfigData(List.of(), Map.of()),
                Map.of(
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 100)
                                )
                        )
                )
        );

        Map<String, Object> sourceAttribution = castMap(reportData.get("sourceAttribution"));
        List<Map<String, Object>> categories = castList(sourceAttribution.get("categories"));
        assertEquals(1, categories.size());
        assertEquals(0, categories.getFirst().get("count"));
        assertEquals(0.0d, categories.getFirst().get("percent"));
        assertEquals(0, sourceAttribution.get("evidenceCount"));
        assertNull(sourceAttribution.get("dominantSource"));
    }

    @Test
    void shouldProjectExecutionReasonCodesIntoIntegrityView() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-v42-execution-reasons")
                .brandName("探镜")
                .category("运动相机")
                .build();
        List<ExecutionSignal> signals = List.of(
                new ExecutionSignal(
                        "NODE0_STATE_SPACE_FALLBACK",
                        "NODE0_STATE_SPACE",
                        "ERROR",
                        "Node0 fallback",
                        List.of(V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED),
                        Map.of("fallbackReasonCode", V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED)
                ),
                new ExecutionSignal(
                        "NODE2_RENDER_FALLBACK_PRESENT",
                        "NODE2_RENDERING",
                        "WARN",
                        "Node2 fallback",
                        List.of(V42CreativeDegradationReasons.OUTPUT_INVALID, V42CreativeDegradationReasons.CONCURRENCY_FAILURE),
                        Map.of("reasonCodeCounts", Map.of(
                                V42CreativeDegradationReasons.OUTPUT_INVALID, 2,
                                V42CreativeDegradationReasons.CONCURRENCY_FAILURE, 1
                        ))
                ),
                new ExecutionSignal(
                        "NODE2_RENDER_REPAIR_PRESENT",
                        "NODE2_RENDERING",
                        "ERROR",
                        "Node2 repair",
                        List.of("anchor"),
                        Map.of("repairRuleCounts", Map.of("anchor", 2))
                )
        );
        V42DiagnosisResult.Task task = new V42DiagnosisResult.Task(
                "diag-v42-execution-reasons",
                "rpt_execution_reasons",
                "4.2",
                "COMPLETED",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                97,
                3,
                RealtimeSearchSummary.disabled("doubao"),
                Node3UsageCostSummary.empty(),
                probeTrace(100),
                signals
        );
        V42DiagnosisResult.Report report = new V42DiagnosisResult.Report(
                "diag-v42-execution-reasons",
                "rpt_execution_reasons",
                "4.2",
                LocalDateTime.parse("2026-03-26T10:00:00"),
                LocalDateTime.parse("2026-03-26T10:04:00"),
                100,
                "doubao",
                "methodology",
                "disclaimer",
                kpiSummary(),
                commercialInsights(),
                new DefenseCompletenessSummary(0.75d, 0, List.of()),
                dashboardCharts(),
                List.of(new V42DiagnosisResult.DisplayNameMapping("kpiSummary.overallMentionRate", "总体提及率")),
                signals
        );

        Map<String, Object> reportData = service.buildReportData(
                diagnosis,
                new V42DiagnosisResult(task, report),
                new DiagnosisAliasConfigData(List.of(), Map.of()),
                Map.of(
                        "taskOrderSnapshot", Map.of(
                                "businessHints", Map.of(
                                        "sampling", Map.of("targetProbeCount", 100)
                                )
                        )
                )
        );

        Map<String, Object> executionIntegrity = castMap(reportData.get("executionIntegrity"));
        assertEquals(true, executionIntegrity.get("degraded"));
        assertEquals(List.of(
                V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED,
                V42CreativeDegradationReasons.OUTPUT_INVALID,
                V42CreativeDegradationReasons.CONCURRENCY_FAILURE,
                "anchor"
        ), executionIntegrity.get("reasonCodes"));
    }

    private KpiSummary kpiSummary() {
        return new KpiSummary(
                0.19d,
                0.34d,
                0.41d,
                new LengthStratifiedKpi(
                        new LengthBucketKpi("Short", 20, 10, 10, 0.15d, 0.30d, 1.1d),
                        new LengthBucketKpi("Medium", 50, 25, 25, 0.20d, 0.34d, 1.2d),
                        new LengthBucketKpi("Long", 30, 15, 15, 0.21d, 0.36d, 1.3d)
                ),
                0.27d,
                0.72d,
                68.0d,
                new BucketKpi(BucketType.ORTHOGONAL, 50, 48, 20, 30, 0.20d, 0.33d, 0.42d, 0.25d, 0.71d),
                new BucketKpi(BucketType.PARETO, 30, 29, 8, 21, 0.18d, 0.35d, 0.39d, 0.28d, 0.74d),
                new BucketKpi(BucketType.ADVERSARIAL, 20, 19, 4, 15, 0.19d, 0.34d, 0.40d, 0.29d, 0.70d),
                Map.of("D1", 0.34d, "D2", 0.52d, "D3", 0.48d, "D4", 0.41d, "D5", 0.37d, "D6", 0.45d),
                Map.of("D1", 0.29d, "D2", 0.61d, "D3", 0.55d, "D4", 0.33d, "D5", 0.38d, "D6", 0.47d),
                Map.of("Insta360", new CompetitorKpi("Insta360", 27, 0.32d, 0.68d, 0.57d)),
                96
        );
    }

    private CommercialInsights commercialInsights() {
        return new CommercialInsights(
                "品牌已经进入 AI 候选池，但还没有稳定坐稳首选位。",
                List.of(
                        new InsightItem("首选率仍偏低", "当前 19% 的 AI 首选率明显低于目标区间。", "必须立即补强可被主动提及的价值锚点。"),
                        new InsightItem("推荐层仍可提升", "当前 27% 的推荐率说明品牌已入围但未稳赢。", "必须立即加强对比场景中的优势表达。"),
                        new InsightItem("D1/D4 仍是弱项", "D1 0.29 与 D4 0.33 显示认知异常与负荷维度仍偏弱。", "必须立即收敛用户理解成本并前置决策线索。")
                ),
                "LLM"
        );
    }

    private DashboardCharts dashboardCharts() {
        return new DashboardCharts(
                new DashboardCharts.RadarChartData(
                        "radar",
                        List.of("D1", "D2", "D3", "D4", "D5", "D6"),
                        "探镜",
                        List.of(0.29d, 0.61d, 0.55d, 0.33d, 0.38d, 0.47d),
                        Map.of("Insta360", List.of(0.41d, 0.58d, 0.52d, 0.44d, 0.49d, 0.53d))
                ),
                new DashboardCharts.ScatterChartData(
                        "scatter",
                        "brandDistance",
                        "recommendRate",
                        List.of(
                                new DashboardCharts.ScatterPoint("探镜", 0.0d, 0.27d, true, 0.41d, 41),
                                new DashboardCharts.ScatterPoint("Insta360", 0.22d, 0.36d, false, 0.32d, 27)
                        )
                ),
                new DashboardCharts.SankeyChartData(
                        "sankey",
                        List.of(
                                new DashboardCharts.SankeyNode("intent", "意图", "intent"),
                                new DashboardCharts.SankeyNode("candidate", "探镜", "candidate"),
                                new DashboardCharts.SankeyNode("recommend", "探镜推荐", "recommend")
                        ),
                        List.of(
                                new DashboardCharts.SankeyLink("intent", "candidate", 41),
                                new DashboardCharts.SankeyLink("candidate", "recommend", 27)
                        )
                ),
                new DashboardCharts.StackedBarChartData(
                        "stacked_bar",
                        List.of("AI 首选率", "AI 确认率", "总体提及率"),
                        List.of(
                                new DashboardCharts.MetricSeries("orthogonal", "正交探测", List.of(0.20d, 0.33d, 0.42d), 48),
                                new DashboardCharts.MetricSeries("pareto", "帕累托场景", List.of(0.18d, 0.35d, 0.39d), 29),
                                new DashboardCharts.MetricSeries("adversarial", "对抗验证", List.of(0.19d, 0.34d, 0.40d), 19)
                        )
                )
        );
    }

    private List<V42DiagnosisResult.ProbeTrace> probeTrace(int sampleSize) {
        return java.util.stream.IntStream.range(0, sampleSize)
                .mapToObj(index -> new V42DiagnosisResult.ProbeTrace(
                        "probe-" + index,
                        BucketType.ORTHOGONAL,
                        Map.of("D1", "state-" + index),
                        Map.of("D1", "场景-" + index),
                        null,
                        null,
                        null,
                        ProbeExposurePolicy.BRAND_HIDDEN,
                        "rendered-probe-" + index,
                        null,
                        ProbeDispatchStatus.SUCCESS,
                        ProbeType.OPEN_DISCOVERY,
                        true,
                        "response-probe-" + index,
                        "response-probe-" + index,
                        null,
                        List.of(),
                        0,
                        120,
                        120,
                        0L,
                        "Medium",
                        "L1_EXACT",
                        null,
                        false,
                        false,
                        false,
                        List.of(),
                        0,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        0,
                        1,
                        Map.of(
                                "validationFailedChecks", List.of("anchor"),
                                "validationFailedRuleIds", List.of(Node2RuleCatalog.RULE_Q_ANCHOR),
                                "behaviorRuleIds", List.of(Node2RuleCatalog.RULE_B_SURFACE_CONFIRM_BUY, Node2RuleCatalog.RULE_B_RETRY_ANCHOR),
                                "validationRepairApplied", "anchor",
                                "gateDecisionInput", Map.of("anchorHit", false),
                                "fallbackTriggerRule", Node2RuleCatalog.RULE_Q_ANCHOR,
                                "fallbackFamilyRuleId", Node2RuleCatalog.RULE_FAMILY_ORTHOGONAL_SHORT
                        ),
                        null
                ))
                .toList();
    }

    @Test
    void shouldSupportProjectedResultWhenEngineVersionIs43() {
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-v43-1")
                .brandName("探镜")
                .category("运动相机")
                .reportData(Map.of(
                        "meta", Map.of("engineVersion", "4.3"),
                        "report", Map.of("engineVersion", "4.3")
                ))
                .build();

        assertTrue(service.supports(diagnosis));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
