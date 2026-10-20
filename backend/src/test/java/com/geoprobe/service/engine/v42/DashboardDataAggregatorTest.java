package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.Node5CDashboardRequest;
import com.geoprobe.dto.engine.v42.Node5CDashboardResult;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class DashboardDataAggregatorTest {

    private final DefenseCompletenessJudge defenseCompletenessJudge = Mockito.mock(DefenseCompletenessJudge.class);
    private DashboardDataAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DashboardDataAggregator(defenseCompletenessJudge);
    }

    @Test
    void shouldAggregateNode5CChartsAndPreserveFrozenContracts() throws JsonProcessingException {
        when(defenseCompletenessJudge.judge(isNull(), eq("农夫山泉"), anyList())).thenReturn(
                new DefenseCompletenessSummary(
                        0.4250d,
                        2,
                        List.of(
                                new DefenseCompletenessResult("adv-1", 1.0d, 0.5d, 1.0d, 0.8500d),
                                DefenseCompletenessResult.missing("adv-2")
                        )
                )
        );

        Node5CDashboardResult result = aggregator.aggregate(new Node5CDashboardRequest(
                "农夫山泉",
                sampleProbes(),
                new Node5AKpiResult(sampleSummary()),
                sampleInsights()
        ));

        assertEquals("TEMPLATE_FALLBACK", result.commercialInsights().insightSource());
        assertEquals(0.4250d, result.defenseCompleteness().averageCompositeScore());

        DashboardCharts.RadarChartData radar = result.dashboardCharts().radar();
        assertEquals("radar", radar.chartType());
        assertEquals(List.of(
                "D1 认知异常",
                "D2 目的论目标",
                "D3 价值论驱动",
                "D4 认知负荷",
                "D5 时间约束",
                "D6 交互模态"
        ), radar.dimensions());
        assertEquals(List.of(0.1800d, 0.2800d, 0.3100d, 0.0933d, 0.2200d, 0.2600d), radar.brand());
        assertTrue(radar.competitors().containsKey("Insta360"));
        assertEquals(List.of(0.5000d, 0.2500d, 0.0000d, 0.5000d, 0.2500d, 0.5000d), radar.competitors().get("Insta360"));
        assertEquals(List.of(0.5000d, 0.2500d, 0.0000d, 0.5000d, 0.2500d, 0.5000d), radar.competitors().get("DJI"));

        DashboardCharts.ScatterChartData scatter = result.dashboardCharts().scatter();
        assertEquals("scatter", scatter.chartType());
        assertEquals("brandDistance", scatter.xMetric());
        assertEquals("recommendRate", scatter.yMetric());
        DashboardCharts.ScatterPoint targetPoint = scatter.points().getFirst();
        assertEquals("农夫山泉", targetPoint.brandName());
        assertTrue(targetPoint.targetBrand());
        assertEquals(0.0000d, targetPoint.brandDistance());
        assertEquals(0.3333d, targetPoint.recommendRate());
        Map<String, DashboardCharts.ScatterPoint> scatterPointByBrand = scatter.points().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DashboardCharts.ScatterPoint::brandName,
                        point -> point,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        assertEquals(0.2228d, scatterPointByBrand.get("Insta360").brandDistance());
        assertEquals(0.3333d, scatterPointByBrand.get("Insta360").recommendRate());
        assertEquals(0.2228d, scatterPointByBrand.get("DJI").brandDistance());
        assertEquals(0.1667d, scatterPointByBrand.get("DJI").recommendRate());

        DashboardCharts.SankeyChartData sankey = result.dashboardCharts().sankey();
        assertEquals("sankey", sankey.chartType());
        assertTrue(sankey.nodes().stream().anyMatch(node -> "intent".equals(node.stage())));
        assertTrue(sankey.nodes().stream().anyMatch(node -> "candidate".equals(node.stage())));
        assertTrue(sankey.nodes().stream().anyMatch(node -> "recommend".equals(node.stage())));
        assertTrue(sankey.links().stream().anyMatch(link -> link.value() >= 1));

        DashboardCharts.StackedBarChartData stackedBar = result.dashboardCharts().stackedBar();
        assertEquals("stacked_bar", stackedBar.chartType());
        assertEquals(List.of(
                "spontaneousMentionRate",
                "confirmationResponseRate",
                "overallMentionRate",
                "recommendRate",
                "positiveRate"
        ), stackedBar.categories());
        assertEquals(3, stackedBar.series().size());
        assertEquals("ORTHOGONAL", stackedBar.series().getFirst().bucketKey());

        String json = new ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("\"insightSource\":\"TEMPLATE_FALLBACK\""));
        assertTrue(json.contains("\"overallMentionRate\""));
        assertMissingJsonKey(json, "defense" + "Durability");
        assertMissingJsonKey(json, "eeat" + "Dimension");
        assertMissingJsonKey(json, "cluster" + "Breakdown");
        assertMissingJsonKey(json, "involvement" + "Level");
        assertMissingJsonKey(json, "voice" + "Type");
        assertMissingJsonKey(json, "mention" + "Rate");
    }

    private List<Node4ProbeOutput> sampleProbes() {
        return List.of(
                probe(
                        "open-1",
                        BucketType.ORTHOGONAL,
                        ProbeType.OPEN_DISCOVERY,
                        ProbeDispatchStatus.SUCCESS,
                        tags("initiation", "informational_directed", "understanding_competence", "moderate_strain", "reflective", "recognition"),
                        Map.of("D2", "想先看一个确切答案"),
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "top1_recommendation",
                                "positive",
                                0.91d,
                                ProbeType.OPEN_DISCOVERY,
                                80,
                                "Short",
                                List.of(new CompetitorMention("Insta360", "L1_EXACT", "positive", 0.74d)),
                                List.of(),
                                ""
                        )
                ),
                probe(
                        "open-2",
                        BucketType.ORTHOGONAL,
                        ProbeType.OPEN_DISCOVERY,
                        ProbeDispatchStatus.SUCCESS,
                        tags("initiation", "instructional_active", "identity_freedom", "moderate_strain", "anticipatory_constraint", "recognition"),
                        Map.of("D2", "想找个具体方法"),
                        BrandDetectionResult.noMention(
                                ProbeType.OPEN_DISCOVERY,
                                120,
                                "Medium",
                                List.of(new CompetitorMention("Insta360", "L2_ALIAS", "neutral", 0.63d)),
                                List.of(),
                                ""
                        )
                ),
                probe(
                        "open-3",
                        BucketType.ORTHOGONAL,
                        ProbeType.OPEN_DISCOVERY,
                        ProbeDispatchStatus.SUCCESS,
                        tags("exploration", "instructional_active", "understanding_competence", "optimal", "anticipatory_constraint", "dialogic"),
                        Map.of("D2", "想先听一个可执行建议"),
                        BrandDetectionResult.noMention(
                                ProbeType.OPEN_DISCOVERY,
                                135,
                                "Medium",
                                List.of(new CompetitorMention("DJI", "L1_EXACT", "positive", 0.66d)),
                                List.of(),
                                ""
                        )
                ),
                probe(
                        "open-4",
                        BucketType.ORTHOGONAL,
                        ProbeType.OPEN_DISCOVERY,
                        ProbeDispatchStatus.SUCCESS,
                        tags("formulation", "resource_acquisition", "identity_freedom", "severely_depleted", "acute_emergency", "specification"),
                        Map.of("D2", "得马上定下来"),
                        BrandDetectionResult.detected(
                                "L2_ALIAS",
                                "passing_mention",
                                "neutral",
                                0.58d,
                                ProbeType.OPEN_DISCOVERY,
                                140,
                                "Medium",
                                List.of(new CompetitorMention("DJI", "L2_ALIAS", "neutral", 0.51d)),
                                List.of(),
                                ""
                        )
                ),
                probe(
                        "brand-1",
                        BucketType.PARETO,
                        ProbeType.BRAND_ASSERTIVE,
                        ProbeDispatchStatus.SUCCESS,
                        tags("verification", "resource_acquisition", "subsistence_protection", "optimal", "acute_emergency", "specification"),
                        Map.of("D2", "马上就要买"),
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "comparison",
                                "positive",
                                0.83d,
                                ProbeType.BRAND_ASSERTIVE,
                                150,
                                "Long",
                                List.of(new CompetitorMention("DJI", "L1_EXACT", "neutral", 0.41d)),
                                List.of(),
                                ""
                        )
                ),
                probe(
                        "adv-1",
                        BucketType.ADVERSARIAL,
                        ProbeType.BRAND_ASSERTIVE,
                        ProbeDispatchStatus.SUCCESS,
                        tags("verification", "resource_acquisition", "subsistence_protection", "optimal", "acute_emergency", "specification"),
                        Map.of("D2", "攻击式排除问法"),
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "negative",
                                "negative",
                                0.88d,
                                ProbeType.BRAND_ASSERTIVE,
                                190,
                                "Long",
                                List.of(new CompetitorMention("Insta360", "L1_EXACT", "positive", 0.82d)),
                                List.of(),
                                ""
                        )
                )
        );
    }

    private Node4ProbeOutput probe(String probeId,
                                   BucketType bucketType,
                                   ProbeType probeType,
                                   ProbeDispatchStatus dispatchStatus,
                                   Map<String, String> abstractTags,
                                   Map<String, String> translatedTags,
                                   BrandDetectionResult detectionResult) {
        return new Node4ProbeOutput(
                probeId,
                bucketType,
                abstractTags,
                translatedTags,
                probeType == ProbeType.BRAND_ASSERTIVE
                        ? ProbeExposurePolicy.BRAND_VISIBLE
                        : ProbeExposurePolicy.BRAND_HIDDEN,
                "rendered-" + probeType.name().toLowerCase(),
                dispatchStatus,
                new RawResponse("回答-" + probeId, "", List.of(), 60, 180, 240, 900L, false),
                null,
                detectionResult
        );
    }

    private Map<String, String> tags(String d1, String d2, String d3, String d4, String d5, String d6) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", d1);
        tags.put("D2", d2);
        tags.put("D3", d3);
        tags.put("D4", d4);
        tags.put("D5", d5);
        tags.put("D6", d6);
        return Map.copyOf(tags);
    }

    private CommercialInsights sampleInsights() {
        return new CommercialInsights(
                "headline",
                List.of(
                        new InsightItem("i1", "evidence 12.00%", "必须立即修复 i1"),
                        new InsightItem("i2", "evidence 18.00%", "必须立即修复 i2"),
                        new InsightItem("i3", "evidence 24.00%", "必须立即修复 i3")
                ),
                "TEMPLATE_FALLBACK"
        );
    }

    private void assertMissingJsonKey(String json, String key) {
        assertFalse(json.contains("\"" + key + "\":"));
    }

    private KpiSummary sampleSummary() {
        LinkedHashMap<String, Double> dimensionPenetration = new LinkedHashMap<>();
        dimensionPenetration.put("D1_initiation", 0.1800d);
        dimensionPenetration.put("D1_verification", 0.2200d);
        dimensionPenetration.put("D4_optimal", 0.0500d);
        dimensionPenetration.put("D4_moderate_strain", 0.1400d);
        dimensionPenetration.put("D6_dialogic", 0.2600d);

        LinkedHashMap<String, Double> dimensionRadarScores = new LinkedHashMap<>();
        dimensionRadarScores.put("D1", 0.1800d);
        dimensionRadarScores.put("D2", 0.2800d);
        dimensionRadarScores.put("D3", 0.3100d);
        dimensionRadarScores.put("D4", 0.0933d);
        dimensionRadarScores.put("D5", 0.2200d);
        dimensionRadarScores.put("D6", 0.2600d);

        LinkedHashMap<String, CompetitorKpi> competitorKpis = new LinkedHashMap<>();
        competitorKpis.put("DJI", new CompetitorKpi("DJI", 3, 0.5000d, 0.3333d, 0.5267d));
        competitorKpis.put("Insta360", new CompetitorKpi("Insta360", 3, 0.5000d, 0.6667d, 0.7300d));

        return new KpiSummary(
                0.5000d,
                1.0000d,
                0.6667d,
                new LengthStratifiedKpi(
                        new LengthBucketKpi("Short", 1, 1, 0, 1.0000d, 0.0000d, 2.0000d),
                        new LengthBucketKpi("Medium", 3, 3, 0, 0.3333d, 0.0000d, 1.3333d),
                        new LengthBucketKpi("Long", 2, 0, 2, 0.0000d, 1.0000d, 2.0000d)
                ),
                0.3333d,
                0.3333d,
                58.2000d,
                new BucketKpi(BucketType.ORTHOGONAL, 4, 4, 4, 0, 0.5000d, 0.0000d, 0.5000d, 0.2500d, 0.2500d),
                new BucketKpi(BucketType.PARETO, 1, 1, 0, 1, 0.0000d, 1.0000d, 1.0000d, 1.0000d, 1.0000d),
                new BucketKpi(BucketType.ADVERSARIAL, 1, 1, 0, 1, 0.0000d, 1.0000d, 1.0000d, 0.0000d, 0.0000d),
                Map.copyOf(dimensionPenetration),
                Map.copyOf(dimensionRadarScores),
                Map.copyOf(competitorKpis),
                6
        );
    }
}
