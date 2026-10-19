package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.Node5AKpiRequest;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service("v42KpiAggregationService")
@RequiredArgsConstructor
public class V42KpiAggregationService {

    private static final List<String> DIMENSION_ORDER = List.of("D1", "D2", "D3", "D4", "D5", "D6");
    private static final String LENGTH_SHORT = "Short";
    private static final String LENGTH_MEDIUM = "Medium";
    private static final String LENGTH_LONG = "Long";
    private static final String SENTIMENT_POSITIVE = "positive";

    private final V42StabilityService stabilityService;

    public Node5AKpiResult aggregate(Node5AKpiRequest request) {
        Objects.requireNonNull(request, "Node5AKpiRequest 不能为空");
        List<Node4ProbeOutput> allProbes = request.probes();
        List<Node4ProbeOutput> effectiveProbes = allProbes.stream()
                .filter(this::isEffective)
                .toList();

        AggregateCounts overallCounts = aggregateCounts(effectiveProbes);
        LengthStratifiedKpi lengthStratifiedKpi = buildLengthStratifiedKpi(effectiveProbes);
        BucketKpi orthogonalKpi = buildBucketKpi(BucketType.ORTHOGONAL, allProbes, effectiveProbes);
        BucketKpi paretoKpi = buildBucketKpi(BucketType.PARETO, allProbes, effectiveProbes);
        BucketKpi adversarialKpi = buildBucketKpi(BucketType.ADVERSARIAL, allProbes, effectiveProbes);
        DimensionAggregation dimensionAggregation = buildDimensionAggregation(effectiveProbes);
        Map<String, CompetitorKpi> competitorKpis = buildCompetitorKpis(effectiveProbes);

        KpiSummary provisionalSummary = new KpiSummary(
                overallCounts.spontaneousMentionRate(),
                overallCounts.confirmationResponseRate(),
                overallCounts.overallMentionRate(),
                lengthStratifiedKpi,
                overallCounts.recommendRate(),
                overallCounts.positiveRate(),
                0.0d,
                orthogonalKpi,
                paretoKpi,
                adversarialKpi,
                dimensionAggregation.dimensionPenetration(),
                dimensionAggregation.dimensionRadarScores(),
                competitorKpis,
                effectiveProbes.size()
        );

        double stabilityScore = stabilityService.calculate(provisionalSummary);
        KpiSummary finalSummary = new KpiSummary(
                provisionalSummary.spontaneousMentionRate(),
                provisionalSummary.confirmationResponseRate(),
                provisionalSummary.overallMentionRate(),
                provisionalSummary.lengthStratifiedKpi(),
                provisionalSummary.recommendRate(),
                provisionalSummary.positiveRate(),
                stabilityScore,
                provisionalSummary.orthogonalKpi(),
                provisionalSummary.paretoKpi(),
                provisionalSummary.adversarialKpi(),
                provisionalSummary.dimensionPenetration(),
                provisionalSummary.dimensionRadarScores(),
                provisionalSummary.competitorKpis(),
                provisionalSummary.effectiveSampleSize()
        );
        return new Node5AKpiResult(finalSummary);
    }

    private boolean isEffective(Node4ProbeOutput probe) {
        return probe.dispatchStatus() == ProbeDispatchStatus.SUCCESS;
    }

    private BucketKpi buildBucketKpi(BucketType bucketType,
                                     List<Node4ProbeOutput> allProbes,
                                     List<Node4ProbeOutput> effectiveProbes) {
        List<Node4ProbeOutput> bucketAll = allProbes.stream()
                .filter(probe -> probe.bucketType() == bucketType)
                .toList();
        List<Node4ProbeOutput> bucketEffective = effectiveProbes.stream()
                .filter(probe -> probe.bucketType() == bucketType)
                .toList();
        AggregateCounts counts = aggregateCounts(bucketEffective);
        return new BucketKpi(
                bucketType,
                bucketAll.size(),
                bucketEffective.size(),
                counts.openDiscoveryCount(),
                counts.brandAssertiveCount(),
                counts.spontaneousMentionRate(),
                counts.confirmationResponseRate(),
                counts.overallMentionRate(),
                counts.recommendRate(),
                counts.positiveRate()
        );
    }

    private LengthStratifiedKpi buildLengthStratifiedKpi(List<Node4ProbeOutput> effectiveProbes) {
        V42LengthBucketResolver.LengthBucketThresholds thresholds = V42LengthBucketResolver.fromProbes(effectiveProbes);
        return new LengthStratifiedKpi(
                buildLengthBucket(LENGTH_SHORT, effectiveProbes, thresholds),
                buildLengthBucket(LENGTH_MEDIUM, effectiveProbes, thresholds),
                buildLengthBucket(LENGTH_LONG, effectiveProbes, thresholds)
        );
    }

    private LengthBucketKpi buildLengthBucket(String lengthLabel,
                                              List<Node4ProbeOutput> effectiveProbes,
                                              V42LengthBucketResolver.LengthBucketThresholds thresholds) {
        List<Node4ProbeOutput> bucketProbes = effectiveProbes.stream()
                .filter(probe -> lengthLabel.equals(thresholds.bucketFor(probe.brandDetectionResult().responseTokenCount())))
                .toList();
        AggregateCounts counts = aggregateCounts(bucketProbes);
        return new LengthBucketKpi(
                lengthLabel,
                bucketProbes.size(),
                counts.openDiscoveryCount(),
                counts.brandAssertiveCount(),
                counts.spontaneousMentionRate(),
                counts.confirmationResponseRate(),
                averageBrandsMentioned(bucketProbes)
        );
    }

    private AggregateCounts aggregateCounts(List<Node4ProbeOutput> probes) {
        int total = probes.size();
        int openDiscoveryCount = 0;
        int openDiscoveryMentioned = 0;
        int brandAssertiveCount = 0;
        int brandAssertiveMentioned = 0;
        int mentionedCount = 0;
        int recommendedCount = 0;
        int positiveCount = 0;

        for (Node4ProbeOutput probe : probes) {
            BrandDetectionResult result = probe.brandDetectionResult();
            if (result.probeType() == ProbeType.OPEN_DISCOVERY) {
                openDiscoveryCount++;
                if (result.mentioned()) {
                    openDiscoveryMentioned++;
                }
            } else if (result.probeType() == ProbeType.BRAND_ASSERTIVE) {
                brandAssertiveCount++;
                if (result.mentioned()) {
                    brandAssertiveMentioned++;
                }
            }
            if (result.mentioned()) {
                mentionedCount++;
            }
            if (isRecommended(result)) {
                recommendedCount++;
            }
            if (isPositive(result)) {
                positiveCount++;
            }
        }

        return new AggregateCounts(
                total,
                openDiscoveryCount,
                brandAssertiveCount,
                scale4(rate(openDiscoveryMentioned, openDiscoveryCount)),
                scale4(rate(brandAssertiveMentioned, brandAssertiveCount)),
                scale4(rate(mentionedCount, total)),
                scale4(rate(recommendedCount, total)),
                scale4(rate(positiveCount, total))
        );
    }

    private boolean isRecommended(BrandDetectionResult result) {
        if (!result.mentioned() || result.sentiment() == null) {
            return false;
        }
        if ("top1_recommendation".equals(result.position())) {
            return true;
        }
        return "comparison".equals(result.position()) && SENTIMENT_POSITIVE.equals(result.sentiment());
    }

    private boolean isPositive(BrandDetectionResult result) {
        return result.mentioned() && SENTIMENT_POSITIVE.equals(result.sentiment());
    }

    private double averageBrandsMentioned(List<Node4ProbeOutput> probes) {
        if (probes.isEmpty()) {
            return 0.0d;
        }
        int totalBrandsMentioned = 0;
        for (Node4ProbeOutput probe : probes) {
            BrandDetectionResult result = probe.brandDetectionResult();
            totalBrandsMentioned += (result.mentioned() ? 1 : 0) + result.competitorsMentioned().size();
        }
        return scale4((double) totalBrandsMentioned / probes.size());
    }

    private DimensionAggregation buildDimensionAggregation(List<Node4ProbeOutput> effectiveProbes) {
        LinkedHashMap<String, LinkedHashMap<String, SliceCounter>> counters = new LinkedHashMap<>();
        for (String dimensionId : DIMENSION_ORDER) {
            counters.put(dimensionId, new LinkedHashMap<>());
        }

        for (Node4ProbeOutput probe : effectiveProbes) {
            BrandDetectionResult result = probe.brandDetectionResult();
            if (result.probeType() != ProbeType.OPEN_DISCOVERY) {
                continue;
            }
            for (String dimensionId : DIMENSION_ORDER) {
                String abstractCode = probe.abstractTags().get(dimensionId);
                if (abstractCode == null || abstractCode.isBlank()) {
                    continue;
                }
                SliceCounter counter = counters.get(dimensionId).computeIfAbsent(abstractCode, ignored -> new SliceCounter());
                counter.total++;
                if (result.mentioned()) {
                    counter.mentioned++;
                }
            }
        }

        LinkedHashMap<String, Double> dimensionPenetration = new LinkedHashMap<>();
        LinkedHashMap<String, Double> dimensionRadarScores = new LinkedHashMap<>();
        double spontaneousBaseline = scale4(rate(
                effectiveProbes.stream()
                        .filter(probe -> probe.brandDetectionResult().probeType() == ProbeType.OPEN_DISCOVERY)
                        .mapToInt(probe -> probe.brandDetectionResult().mentioned() ? 1 : 0)
                        .sum(),
                (int) effectiveProbes.stream()
                        .filter(probe -> probe.brandDetectionResult().probeType() == ProbeType.OPEN_DISCOVERY)
                        .count()
        ));
        for (String dimensionId : DIMENSION_ORDER) {
            List<Map.Entry<String, SliceCounter>> slices = new ArrayList<>(counters.get(dimensionId).entrySet());
            slices.sort(Map.Entry.comparingByKey());

            double deviationWeightedSum = 0.0d;
            int effectiveSampleSum = 0;
            for (Map.Entry<String, SliceCounter> entry : slices) {
                SliceCounter counter = entry.getValue();
                double penetration = scale4(rate(counter.mentioned, counter.total));
                dimensionPenetration.put(dimensionId + "_" + entry.getKey(), penetration);
                deviationWeightedSum += Math.abs(penetration - spontaneousBaseline) * counter.total;
                effectiveSampleSum += counter.total;
            }
            dimensionRadarScores.put(dimensionId, scale4(rate(deviationWeightedSum, effectiveSampleSum)));
        }
        return new DimensionAggregation(dimensionPenetration, dimensionRadarScores);
    }

    private Map<String, CompetitorKpi> buildCompetitorKpis(List<Node4ProbeOutput> effectiveProbes) {
        LinkedHashMap<String, CompetitorAccumulator> accumulators = new LinkedHashMap<>();
        for (Node4ProbeOutput probe : effectiveProbes) {
            for (CompetitorMention mention : probe.brandDetectionResult().competitorsMentioned()) {
                CompetitorAccumulator accumulator = accumulators.computeIfAbsent(
                        mention.competitorName(),
                        ignored -> new CompetitorAccumulator()
                );
                accumulator.mentionCount++;
                if (SENTIMENT_POSITIVE.equals(mention.sentiment())) {
                    accumulator.positiveCount++;
                }
                accumulator.strengthSum += mention.strength();
            }
        }

        List<Map.Entry<String, CompetitorAccumulator>> sortedEntries = new ArrayList<>(accumulators.entrySet());
        sortedEntries.sort(Comparator
                .<Map.Entry<String, CompetitorAccumulator>>comparingInt(entry -> entry.getValue().mentionCount)
                .reversed()
                .thenComparing(Map.Entry::getKey));

        LinkedHashMap<String, CompetitorKpi> competitorKpis = new LinkedHashMap<>();
        for (Map.Entry<String, CompetitorAccumulator> entry : sortedEntries) {
            CompetitorAccumulator accumulator = entry.getValue();
            competitorKpis.put(entry.getKey(), new CompetitorKpi(
                    entry.getKey(),
                    accumulator.mentionCount,
                    scale4(rate(accumulator.mentionCount, effectiveProbes.size())),
                    scale4(rate(accumulator.positiveCount, accumulator.mentionCount)),
                    scale4(accumulator.mentionCount <= 0 ? 0.0d : accumulator.strengthSum / accumulator.mentionCount)
            ));
        }
        return competitorKpis;
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return 0.0d;
        }
        return (double) numerator / denominator;
    }

    private double rate(double numerator, int denominator) {
        if (denominator <= 0 || numerator <= 0.0d) {
            return 0.0d;
        }
        return numerator / denominator;
    }

    private double scale4(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record AggregateCounts(
            int totalProbes,
            int openDiscoveryCount,
            int brandAssertiveCount,
            double spontaneousMentionRate,
            double confirmationResponseRate,
            double overallMentionRate,
            double recommendRate,
            double positiveRate
    ) {
    }

    private record DimensionAggregation(
            Map<String, Double> dimensionPenetration,
            Map<String, Double> dimensionRadarScores
    ) {
    }

    private static final class SliceCounter {
        private int total;
        private int mentioned;
    }

    private static final class CompetitorAccumulator {
        private int mentionCount;
        private int positiveCount;
        private double strengthSum;
    }
}
