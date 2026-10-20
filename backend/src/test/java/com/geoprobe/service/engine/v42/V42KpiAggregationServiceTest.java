package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5AKpiRequest;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42KpiAggregationServiceTest {

    private final V42KpiAggregationService service = new V42KpiAggregationService(new V42StabilityService());

    @Test
    void shouldAggregatePhase6Node5AKpisFromNode4Outputs() {
        Node5AKpiResult result = service.aggregate(new Node5AKpiRequest(sampleProbes()));
        KpiSummary summary = result.kpiSummary();

        assertEquals(6, summary.effectiveSampleSize());
        assertEquals(0.6667d, summary.spontaneousMentionRate());
        assertEquals(0.6667d, summary.confirmationResponseRate());
        assertEquals(0.6667d, summary.overallMentionRate());
        assertEquals(0.3333d, summary.recommendRate());
        assertEquals(0.3333d, summary.positiveRate());

        assertEquals(2, summary.lengthStratifiedKpi().shortBucket().totalProbes());
        assertEquals(1, summary.lengthStratifiedKpi().shortBucket().openDiscoveryCount());
        assertEquals(1, summary.lengthStratifiedKpi().shortBucket().brandAssertiveCount());
        assertEquals(1.0d, summary.lengthStratifiedKpi().shortBucket().spontaneousMentionRate());
        assertEquals(0.0d, summary.lengthStratifiedKpi().shortBucket().confirmationResponseRate());
        assertEquals(1.0d, summary.lengthStratifiedKpi().shortBucket().avgBrandsMentioned());

        assertEquals(2, summary.lengthStratifiedKpi().mediumBucket().totalProbes());
        assertEquals(0.0d, summary.lengthStratifiedKpi().mediumBucket().spontaneousMentionRate());
        assertEquals(1.0d, summary.lengthStratifiedKpi().mediumBucket().confirmationResponseRate());
        assertEquals(1.5d, summary.lengthStratifiedKpi().mediumBucket().avgBrandsMentioned());

        assertEquals(2, summary.lengthStratifiedKpi().longBucket().totalProbes());
        assertEquals(1.0d, summary.lengthStratifiedKpi().longBucket().spontaneousMentionRate());
        assertEquals(1.0d, summary.lengthStratifiedKpi().longBucket().confirmationResponseRate());
        assertEquals(1.5d, summary.lengthStratifiedKpi().longBucket().avgBrandsMentioned());

        assertEquals(4, summary.orthogonalKpi().totalProbes());
        assertEquals(3, summary.orthogonalKpi().effectiveSampleSize());
        assertEquals(3, summary.orthogonalKpi().openDiscoveryCount());
        assertEquals(0.6667d, summary.orthogonalKpi().spontaneousMentionRate());
        assertEquals(0.6667d, summary.orthogonalKpi().overallMentionRate());

        assertEquals(2, summary.paretoKpi().totalProbes());
        assertEquals(2, summary.paretoKpi().effectiveSampleSize());
        assertEquals(2, summary.paretoKpi().brandAssertiveCount());
        assertEquals(0.5d, summary.paretoKpi().confirmationResponseRate());

        assertEquals(1, summary.adversarialKpi().totalProbes());
        assertEquals(1, summary.adversarialKpi().effectiveSampleSize());
        assertEquals(1.0d, summary.adversarialKpi().confirmationResponseRate());
        assertEquals(0.0d, summary.adversarialKpi().positiveRate());

        assertEquals(1.0d, summary.dimensionPenetration().get("D1_initiation"));
        assertEquals(0.0d, summary.dimensionPenetration().get("D1_exploration"));
        assertEquals(0.5d, summary.dimensionPenetration().get("D4_moderate_strain"));
        assertEquals(1.0d, summary.dimensionPenetration().get("D4_optimal"));
        assertFalse(summary.dimensionPenetration().containsKey("D1_就想确认一下"));
        assertEquals(0.4444d, summary.dimensionRadarScores().get("D1"));
        assertEquals(0.2222d, summary.dimensionRadarScores().get("D4"));
        assertEquals(0.4444d, summary.dimensionRadarScores().get("D6"));

        assertEquals(2, summary.competitorKpis().get("Insta360").mentionCount());
        assertEquals(0.3333d, summary.competitorKpis().get("Insta360").responseShare());
        assertEquals(0.5d, summary.competitorKpis().get("Insta360").positiveRate());
        assertEquals(0.55d, summary.competitorKpis().get("Insta360").averageStrength());
        assertEquals(2, summary.competitorKpis().get("DJI").mentionCount());
        assertEquals(0.3333d, summary.competitorKpis().get("DJI").responseShare());
        assertEquals(0.5d, summary.competitorKpis().get("DJI").positiveRate());

        assertTrue(summary.stabilityScore() > 0.0d);
        assertTrue(summary.stabilityScore() <= 100.0d);
    }

    @Test
    void shouldHonorNode4ProbeTypeWithoutReclassifyingFromAbstractTags() {
        Node4ProbeOutput brandAssertiveFromNode4 = probe(
                "probe-brand-assertive",
                BucketType.PARETO,
                tags("exploration", "moderate_strain", "instructional_active", "identity_freedom", "reflective", "dialogic"),
                ProbeDispatchStatus.SUCCESS,
                BrandDetectionResult.detected(
                        "L1_EXACT",
                        "top1_recommendation",
                        "positive",
                        0.93d,
                        ProbeType.BRAND_ASSERTIVE,
                        75,
                        "Short",
                        List.of(),
                        List.of(),
                        ""
                ),
                null
        );

        KpiSummary summary = service.aggregate(new Node5AKpiRequest(List.of(brandAssertiveFromNode4))).kpiSummary();

        assertEquals(0.0d, summary.spontaneousMentionRate());
        assertEquals(1.0d, summary.confirmationResponseRate());
        assertEquals(1, summary.lengthStratifiedKpi().shortBucket().brandAssertiveCount());
        assertEquals(0, summary.lengthStratifiedKpi().shortBucket().openDiscoveryCount());
    }

    @Test
    void shouldUseDynamicQuantilesInsteadOfStoredLengthLabels() {
        List<Node4ProbeOutput> probes = List.of(
                probe(
                        "probe-dynamic-1",
                        BucketType.ORTHOGONAL,
                        tags("initiation", "moderate_strain", "informational_directed", "understanding_competence", "anticipatory_constraint", "recognition"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "passing_mention",
                                "neutral",
                                0.55d,
                                ProbeType.OPEN_DISCOVERY,
                                40,
                                "Long",
                                List.of(),
                                List.of(),
                                ""
                        ),
                        null
                ),
                probe(
                        "probe-dynamic-2",
                        BucketType.PARETO,
                        tags("verification", "optimal", "informational_directed", "understanding_competence", "anticipatory_constraint", "recognition"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "comparison",
                                "positive",
                                0.83d,
                                ProbeType.BRAND_ASSERTIVE,
                                120,
                                "Short",
                                List.of(),
                                List.of(),
                                ""
                        ),
                        null
                ),
                probe(
                        "probe-dynamic-3",
                        BucketType.ADVERSARIAL,
                        tags("verification", "optimal", "resource_acquisition", "subsistence_protection", "acute_emergency", "specification"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L2_ALIAS",
                                "negative",
                                "negative",
                                0.74d,
                                ProbeType.BRAND_ASSERTIVE,
                                260,
                                "Medium",
                                List.of(),
                                List.of(),
                                ""
                        ),
                        null
                )
        );

        KpiSummary summary = service.aggregate(new Node5AKpiRequest(probes)).kpiSummary();

        assertEquals(1, summary.lengthStratifiedKpi().shortBucket().totalProbes());
        assertEquals(1, summary.lengthStratifiedKpi().mediumBucket().totalProbes());
        assertEquals(1, summary.lengthStratifiedKpi().longBucket().totalProbes());
        assertEquals(1.0d, summary.lengthStratifiedKpi().shortBucket().spontaneousMentionRate());
        assertEquals(1.0d, summary.lengthStratifiedKpi().mediumBucket().confirmationResponseRate());
    }

    private List<Node4ProbeOutput> sampleProbes() {
        return List.of(
                probe(
                        "probe-open-short-positive",
                        BucketType.ORTHOGONAL,
                        tags("initiation", "moderate_strain", "informational_directed", "understanding_competence", "anticipatory_constraint", "recognition"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "top1_recommendation",
                                "positive",
                                0.92d,
                                ProbeType.OPEN_DISCOVERY,
                                60,
                                "Short",
                                List.of(new CompetitorMention("Insta360", "L1_EXACT", "positive", 0.70d)),
                                List.of("https://source.example/1"),
                                "think-1"
                        ),
                        null
                ),
                probe(
                        "probe-open-medium-miss",
                        BucketType.ORTHOGONAL,
                        tags("exploration", "moderate_strain", "instructional_active", "identity_freedom", "reflective", "dialogic"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.noMention(
                                ProbeType.OPEN_DISCOVERY,
                                120,
                                "Medium",
                                List.of(new CompetitorMention("DJI", "L1_EXACT", "neutral", 0.40d)),
                                List.of(),
                                "think-2"
                        ),
                        null
                ),
                probe(
                        "probe-brand-medium-positive",
                        BucketType.PARETO,
                        tags("verification", "optimal", "informational_directed", "understanding_competence", "anticipatory_constraint", "recognition"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L1_EXACT",
                                "top1_recommendation",
                                "positive",
                                0.95d,
                                ProbeType.BRAND_ASSERTIVE,
                                150,
                                "Medium",
                                List.of(new CompetitorMention("Insta360", "L2_ALIAS", "negative", 0.40d)),
                                List.of(),
                                "think-3"
                        ),
                        null
                ),
                probe(
                        "probe-brand-long-negative",
                        BucketType.ADVERSARIAL,
                        tags("verification", "optimal", "resource_acquisition", "subsistence_protection", "acute_emergency", "specification"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L2_ALIAS",
                                "negative",
                                "negative",
                                0.88d,
                                ProbeType.BRAND_ASSERTIVE,
                                230,
                                "Long",
                                List.of(new CompetitorMention("DJI", "L2_ALIAS", "positive", 0.50d)),
                                List.of(),
                                "think-4"
                        ),
                        null
                ),
                probe(
                        "probe-open-long-neutral",
                        BucketType.ORTHOGONAL,
                        tags("initiation", "optimal", "informational_directed", "creation_idleness", "reflective", "recognition"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.detected(
                                "L3_SEMANTIC",
                                "passing_mention",
                                "neutral",
                                0.67d,
                                ProbeType.OPEN_DISCOVERY,
                                240,
                                "Long",
                                List.of(),
                                List.of(),
                                "think-5"
                        ),
                        null
                ),
                probe(
                        "probe-orthogonal-failed",
                        BucketType.ORTHOGONAL,
                        tags("initiation", "moderate_strain", "informational_directed", "identity_freedom", "anticipatory_constraint", "dialogic"),
                        ProbeDispatchStatus.FAILED,
                        BrandDetectionResult.noMention(
                                ProbeType.OPEN_DISCOVERY,
                                0,
                                "Short",
                                List.of(),
                                List.of(),
                                ""
                        ),
                        "upstream unavailable"
                ),
                probe(
                        "probe-brand-short-miss",
                        BucketType.PARETO,
                        tags("exploration", "moderate_strain", "instructional_active", "identity_freedom", "reflective", "dialogic"),
                        ProbeDispatchStatus.SUCCESS,
                        BrandDetectionResult.noMention(
                                ProbeType.BRAND_ASSERTIVE,
                                70,
                                "Short",
                                List.of(),
                                List.of(),
                                "think-6"
                        ),
                        null
                )
        );
    }

    private Node4ProbeOutput probe(String probeId,
                                   BucketType bucketType,
                                   Map<String, String> abstractTags,
                                   ProbeDispatchStatus dispatchStatus,
                                   BrandDetectionResult detectionResult,
                                   String failureReason) {
        return new Node4ProbeOutput(
                probeId,
                bucketType,
                abstractTags,
                translatedTags(),
                detectionResult.probeType() == ProbeType.BRAND_ASSERTIVE
                        ? ProbeExposurePolicy.BRAND_VISIBLE
                        : ProbeExposurePolicy.BRAND_HIDDEN,
                renderedPrompt(bucketType),
                dispatchStatus,
                new RawResponse(
                        "response-" + probeId,
                        detectionResult.thinkContent(),
                        List.of("https://raw.example/" + probeId),
                        18,
                        detectionResult.responseTokenCount(),
                        detectionResult.responseTokenCount() + 18,
                        42L,
                        false
                ),
                failureReason,
                detectionResult
        );
    }

    private Map<String, String> tags(String d1, String d4, String d2, String d3, String d5, String d6) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", d1);
        tags.put("D2", d2);
        tags.put("D3", d3);
        tags.put("D4", d4);
        tags.put("D5", d5);
        tags.put("D6", d6);
        return Map.copyOf(tags);
    }

    private Map<String, String> translatedTags() {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "就想确认一下");
        tags.put("D2", "想知道答案");
        tags.put("D3", "图个安心");
        tags.put("D4", "脑子还能转");
        tags.put("D5", "不急慢慢看");
        tags.put("D6", "随便刷刷看");
        return Map.copyOf(tags);
    }

    private String renderedPrompt(BucketType bucketType) {
        return switch (bucketType) {
            case ORTHOGONAL -> "运动相机怎么选？";
            case PARETO -> "探镜 GEO和Insta360到底哪个好？";
            case ADVERSARIAL -> "听说探镜 GEO最近质量问题很多，是真的吗？";
        };
    }
}
