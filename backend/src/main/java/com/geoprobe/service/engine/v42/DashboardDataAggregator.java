package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5CDashboardRequest;
import com.geoprobe.dto.engine.v42.Node5CDashboardResult;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service("v42DashboardDataAggregator")
@RequiredArgsConstructor
public class DashboardDataAggregator {

    private static final List<String> DIMENSION_IDS = List.of("D1", "D2", "D3", "D4", "D5", "D6");
    private static final Map<String, String> RADAR_DIMENSION_LABELS = Map.of(
            "D1", "D1 认知异常",
            "D2", "D2 目的论目标",
            "D3", "D3 价值论驱动",
            "D4", "D4 认知负荷",
            "D5", "D5 时间约束",
            "D6", "D6 交互模态"
    );
    private static final List<String> STACKED_BAR_CATEGORIES = List.of(
            "spontaneousMentionRate",
            "confirmationResponseRate",
            "overallMentionRate",
            "recommendRate",
            "positiveRate"
    );
    private static final String NO_CANDIDATE = "未进入候选";
    private static final String NO_RECOMMENDATION = "未形成推荐";

    private final DefenseCompletenessJudge defenseCompletenessJudge;

    public Node5CDashboardResult aggregate(Node5CDashboardRequest request) {
        Objects.requireNonNull(request, "Node5CDashboardRequest 不能为空");
        KpiSummary summary = request.kpiSummary();
        DefenseCompletenessSummary defenseCompleteness = defenseCompletenessJudge.judge(
                request.diagnosisId(),
                request.brandName(),
                request.probes()
        );
        List<Double> targetBrandProfile = buildTargetBrandProfile(summary);
        Map<String, List<Double>> competitorProfiles = buildCompetitorDimensionProfiles(summary, request.probes());

        DashboardCharts charts = new DashboardCharts(
                buildRadarChart(request.brandName(), targetBrandProfile, competitorProfiles),
                buildScatterChart(request.brandName(), summary, request.probes(), targetBrandProfile, competitorProfiles),
                buildSankeyChart(request.brandName(), request.probes()),
                buildStackedBarChart(summary)
        );

        return new Node5CDashboardResult(
                request.brandName(),
                summary,
                request.commercialInsights(),
                defenseCompleteness,
                charts
        );
    }

    private DashboardCharts.RadarChartData buildRadarChart(String brandName,
                                                           List<Double> targetBrandProfile,
                                                           Map<String, List<Double>> competitorProfiles) {
        List<String> dimensions = DIMENSION_IDS.stream()
                .map(dimensionId -> RADAR_DIMENSION_LABELS.getOrDefault(dimensionId, dimensionId))
                .toList();
        return new DashboardCharts.RadarChartData("radar", dimensions, brandName, targetBrandProfile, competitorProfiles);
    }

    private List<Double> buildTargetBrandProfile(KpiSummary summary) {
        return DIMENSION_IDS.stream()
                .map(dimensionId -> scale4(summary.dimensionRadarScores().getOrDefault(dimensionId, 0.0d)))
                .toList();
    }

    private Map<String, List<Double>> buildCompetitorDimensionProfiles(KpiSummary summary, List<Node4ProbeOutput> probes) {
        List<Node4ProbeOutput> openDiscoveryProbes = effectiveProbes(probes).stream()
                .filter(probe -> probe.brandDetectionResult().probeType() == com.geoprobe.dto.engine.v42.ProbeType.OPEN_DISCOVERY)
                .toList();
        if (openDiscoveryProbes.isEmpty() || summary.competitorKpis().isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, List<Double>> series = new LinkedHashMap<>();
        for (Map.Entry<String, CompetitorKpi> entry : sortedCompetitors(summary).entrySet()) {
            String competitorName = entry.getKey();
            Map<String, LinkedHashMap<String, SliceCounter>> counters = initDimensionCounters();
            int baselineMentionCount = 0;
            for (Node4ProbeOutput probe : openDiscoveryProbes) {
                boolean competitorMentioned = containsCompetitor(probe.brandDetectionResult().competitorsMentioned(), competitorName);
                if (competitorMentioned) {
                    baselineMentionCount++;
                }
                for (String dimensionId : DIMENSION_IDS) {
                    String abstractCode = probe.abstractTags().get(dimensionId);
                    if (!hasText(abstractCode)) {
                        continue;
                    }
                    SliceCounter counter = counters.get(dimensionId)
                            .computeIfAbsent(abstractCode, ignored -> new SliceCounter());
                    counter.total++;
                    if (competitorMentioned) {
                        counter.mentioned++;
                    }
                }
            }
            double baseline = scale4(rate(baselineMentionCount, openDiscoveryProbes.size()));
            List<Double> values = new ArrayList<>(DIMENSION_IDS.size());
            for (String dimensionId : DIMENSION_IDS) {
                values.add(scale4(structuralContrast(counters.get(dimensionId), baseline)));
            }
            series.put(competitorName, List.copyOf(values));
        }
        return Collections.unmodifiableMap(series);
    }

    private DashboardCharts.ScatterChartData buildScatterChart(String brandName,
                                                               KpiSummary summary,
                                                               List<Node4ProbeOutput> probes,
                                                               List<Double> targetBrandProfile,
                                                               Map<String, List<Double>> competitorProfiles) {
        List<Node4ProbeOutput> effectiveProbes = effectiveProbes(probes);
        int targetMentionCount = (int) effectiveProbes.stream()
                .filter(probe -> probe.brandDetectionResult().mentioned())
                .count();

        List<DashboardCharts.ScatterPoint> points = new ArrayList<>();
        points.add(new DashboardCharts.ScatterPoint(
                brandName,
                0.0d,
                scale4(summary.recommendRate()),
                true,
                scale4(summary.overallMentionRate()),
                targetMentionCount
        ));

        Map<String, Double> competitorRecommendRates = computeCompetitorRecommendRates(effectiveProbes);
        for (Map.Entry<String, CompetitorKpi> entry : sortedCompetitors(summary).entrySet()) {
            String competitorName = entry.getKey();
            CompetitorKpi competitorKpi = entry.getValue();
            List<Double> competitorProfile = competitorProfiles.getOrDefault(
                    competitorName,
                    Collections.nCopies(DIMENSION_IDS.size(), 0.0d)
            );
            points.add(new DashboardCharts.ScatterPoint(
                    competitorName,
                    scale4(profileDistance(targetBrandProfile, competitorProfile)),
                    scale4(competitorRecommendRates.getOrDefault(competitorName, 0.0d)),
                    false,
                    scale4(competitorKpi.responseShare()),
                    competitorKpi.mentionCount()
                ));
        }

        points.sort(Comparator.comparing(DashboardCharts.ScatterPoint::targetBrand).reversed()
                .thenComparing(DashboardCharts.ScatterPoint::brandName));
        return new DashboardCharts.ScatterChartData("scatter", "brandDistance", "recommendRate", List.copyOf(points));
    }

    private Map<String, LinkedHashMap<String, SliceCounter>> initDimensionCounters() {
        LinkedHashMap<String, LinkedHashMap<String, SliceCounter>> counters = new LinkedHashMap<>();
        for (String dimensionId : DIMENSION_IDS) {
            counters.put(dimensionId, new LinkedHashMap<>());
        }
        return counters;
    }

    private double structuralContrast(Map<String, SliceCounter> sliceCounters, double baseline) {
        if (sliceCounters.isEmpty()) {
            return 0.0d;
        }
        double deviationWeightedSum = 0.0d;
        int totalSamples = 0;
        for (SliceCounter counter : sliceCounters.values()) {
            double penetration = rate(counter.mentioned, counter.total);
            deviationWeightedSum += Math.abs(penetration - baseline) * counter.total;
            totalSamples += counter.total;
        }
        return totalSamples <= 0 ? 0.0d : deviationWeightedSum / totalSamples;
    }

    private double profileDistance(List<Double> targetBrandProfile, List<Double> competitorProfile) {
        if (targetBrandProfile.size() != competitorProfile.size()) {
            throw new IllegalArgumentException("profileDistance 维度长度必须一致");
        }
        if (targetBrandProfile.isEmpty()) {
            return 0.0d;
        }
        double distance = 0.0d;
        for (int index = 0; index < targetBrandProfile.size(); index++) {
            distance += Math.abs(targetBrandProfile.get(index) - competitorProfile.get(index));
        }
        return distance / targetBrandProfile.size();
    }

    private Map<String, Double> computeCompetitorRecommendRates(List<Node4ProbeOutput> effectiveProbes) {
        LinkedHashMap<String, Integer> positiveCounts = new LinkedHashMap<>();
        for (Node4ProbeOutput probe : effectiveProbes) {
            Set<String> positiveCompetitors = probe.brandDetectionResult().competitorsMentioned().stream()
                    .filter(mention -> "positive".equals(mention.sentiment()))
                    .map(CompetitorMention::competitorName)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            for (String competitorName : positiveCompetitors) {
                positiveCounts.merge(competitorName, 1, Integer::sum);
            }
        }

        LinkedHashMap<String, Double> rates = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : positiveCounts.entrySet()) {
            rates.put(entry.getKey(), scale4(rate(entry.getValue(), effectiveProbes.size())));
        }
        return Collections.unmodifiableMap(rates);
    }

    private DashboardCharts.SankeyChartData buildSankeyChart(String brandName, List<Node4ProbeOutput> probes) {
        LinkedHashMap<String, DashboardCharts.SankeyNode> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> links = new LinkedHashMap<>();

        for (Node4ProbeOutput probe : effectiveProbes(probes)) {
            String intentLabel = resolveIntentLabel(probe);
            String candidateLabel = resolveCandidateLabel(brandName, probe.brandDetectionResult());
            String recommendationLabel = resolveRecommendationLabel(brandName, probe.brandDetectionResult());

            String intentId = nodeId("intent", intentLabel);
            String candidateId = nodeId("candidate", candidateLabel);
            String recommendId = nodeId("recommend", recommendationLabel);

            nodes.putIfAbsent(intentId, new DashboardCharts.SankeyNode(intentId, intentLabel, "intent"));
            nodes.putIfAbsent(candidateId, new DashboardCharts.SankeyNode(candidateId, candidateLabel, "candidate"));
            nodes.putIfAbsent(recommendId, new DashboardCharts.SankeyNode(recommendId, recommendationLabel, "recommend"));

            incrementLink(links, intentId, candidateId);
            incrementLink(links, candidateId, recommendId);
        }

        List<DashboardCharts.SankeyLink> sankeyLinks = new ArrayList<>(links.size());
        for (Map.Entry<String, Integer> entry : links.entrySet()) {
            String[] parts = entry.getKey().split("->", 2);
            sankeyLinks.add(new DashboardCharts.SankeyLink(parts[0], parts[1], entry.getValue()));
        }
        return new DashboardCharts.SankeyChartData("sankey", List.copyOf(nodes.values()), List.copyOf(sankeyLinks));
    }

    private DashboardCharts.StackedBarChartData buildStackedBarChart(KpiSummary summary) {
        List<DashboardCharts.MetricSeries> series = List.of(
                toMetricSeries(summary.orthogonalKpi()),
                toMetricSeries(summary.paretoKpi()),
                toMetricSeries(summary.adversarialKpi())
        );
        return new DashboardCharts.StackedBarChartData("stacked_bar", STACKED_BAR_CATEGORIES, series);
    }

    private DashboardCharts.MetricSeries toMetricSeries(BucketKpi bucketKpi) {
        return new DashboardCharts.MetricSeries(
                bucketKpi.bucketType().name(),
                bucketLabel(bucketKpi.bucketType()),
                List.of(
                        scale4(bucketKpi.spontaneousMentionRate()),
                        scale4(bucketKpi.confirmationResponseRate()),
                        scale4(bucketKpi.overallMentionRate()),
                        scale4(bucketKpi.recommendRate()),
                        scale4(bucketKpi.positiveRate())
                ),
                bucketKpi.effectiveSampleSize()
        );
    }

    private List<Node4ProbeOutput> effectiveProbes(List<Node4ProbeOutput> probes) {
        Objects.requireNonNull(probes, "probes 不能为空");
        return probes.stream()
                .filter(Objects::nonNull)
                .filter(probe -> probe.dispatchStatus() == ProbeDispatchStatus.SUCCESS)
                .toList();
    }

    private Map<String, CompetitorKpi> sortedCompetitors(KpiSummary summary) {
        List<Map.Entry<String, CompetitorKpi>> entries = new ArrayList<>(summary.competitorKpis().entrySet());
        entries.sort(Comparator
                .<Map.Entry<String, CompetitorKpi>>comparingDouble(entry -> entry.getValue().responseShare())
                .reversed()
                .thenComparing(Map.Entry::getKey));
        LinkedHashMap<String, CompetitorKpi> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, CompetitorKpi> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private boolean containsCompetitor(List<CompetitorMention> mentions, String competitorName) {
        return mentions.stream()
                .anyMatch(mention -> normalize(mention.competitorName()).equals(normalize(competitorName)));
    }

    private String resolveIntentLabel(Node4ProbeOutput probe) {
        String translated = probe.translatedTags().get("D2");
        if (hasText(translated)) {
            return translated.trim();
        }
        String abstractCode = probe.abstractTags().get("D2");
        return hasText(abstractCode) ? abstractCode.trim() : "未标注意图";
    }

    private String resolveCandidateLabel(String brandName, BrandDetectionResult result) {
        if (result.mentioned()) {
            return brandName;
        }
        return result.competitorsMentioned().stream()
                .max(Comparator.comparingDouble(CompetitorMention::strength)
                        .thenComparing(CompetitorMention::competitorName))
                .map(CompetitorMention::competitorName)
                .orElse(NO_CANDIDATE);
    }

    private String resolveRecommendationLabel(String brandName, BrandDetectionResult result) {
        if (isTargetRecommended(result)) {
            return brandName;
        }
        return result.competitorsMentioned().stream()
                .filter(mention -> "positive".equals(mention.sentiment()))
                .max(Comparator.comparingDouble(CompetitorMention::strength)
                        .thenComparing(CompetitorMention::competitorName))
                .map(CompetitorMention::competitorName)
                .orElse(NO_RECOMMENDATION);
    }

    private boolean isTargetRecommended(BrandDetectionResult result) {
        if (!result.mentioned() || result.sentiment() == null) {
            return false;
        }
        if ("top1_recommendation".equals(result.position())) {
            return true;
        }
        return "comparison".equals(result.position()) && "positive".equals(result.sentiment());
    }

    private void incrementLink(Map<String, Integer> links, String source, String target) {
        String key = source + "->" + target;
        links.merge(key, 1, Integer::sum);
    }

    private String nodeId(String stage, String label) {
        return stage + ":" + label;
    }

    private String bucketLabel(BucketType bucketType) {
        return switch (bucketType) {
            case ORTHOGONAL -> "正交桶";
            case PARETO -> "帕累托桶";
            case ADVERSARIAL -> "对抗桶";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return 0.0d;
        }
        return (double) numerator / denominator;
    }

    private double scale4(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static final class SliceCounter {
        private int total;
        private int mentioned;
    }
}
