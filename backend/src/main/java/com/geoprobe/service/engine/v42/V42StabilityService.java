package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.KpiSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service("v42StabilityService")
public class V42StabilityService {

    private static final double WILSON_Z = 1.96d;

    /**
     * Node 5A 仅有单次响应样本，因此稳定性按置信区间宽度和覆盖完整度做确定性评估。
     */
    public double calculate(KpiSummary summary) {
        Objects.requireNonNull(summary, "KpiSummary 不能为空");
        if (summary.effectiveSampleSize() <= 0) {
            return 0.0d;
        }

        List<RateObservation> observations = new ArrayList<>();
        int openDiscoveryCount = totalOpenDiscovery(summary);
        int brandAssertiveCount = totalBrandAssertive(summary);

        observations.add(new RateObservation(summary.spontaneousMentionRate(), openDiscoveryCount));
        observations.add(new RateObservation(summary.confirmationResponseRate(), brandAssertiveCount));
        observations.add(new RateObservation(summary.recommendRate(), summary.effectiveSampleSize()));
        observations.add(new RateObservation(summary.positiveRate(), summary.effectiveSampleSize()));

        double weightedConfidenceSum = 0.0d;
        double weightSum = 0.0d;
        for (RateObservation observation : observations) {
            if (observation.denominator() <= 0) {
                continue;
            }
            double confidenceScore = confidenceScore(observation.rate(), observation.denominator());
            weightedConfidenceSum += confidenceScore * observation.denominator();
            weightSum += observation.denominator();
        }

        double rateConfidence = weightSum <= 0.0d ? 0.0d : weightedConfidenceSum / weightSum;
        double bucketCoverage = coverageScore(nonEmptyBucketCount(summary), 3);
        double lengthCoverage = coverageScore(nonEmptyLengthCount(summary), 3);

        double composite = (rateConfidence * 0.75d) + (bucketCoverage * 0.15d) + (lengthCoverage * 0.10d);
        return round2(clampScore(composite));
    }

    private int totalOpenDiscovery(KpiSummary summary) {
        return summary.lengthStratifiedKpi().shortBucket().openDiscoveryCount()
                + summary.lengthStratifiedKpi().mediumBucket().openDiscoveryCount()
                + summary.lengthStratifiedKpi().longBucket().openDiscoveryCount();
    }

    private int totalBrandAssertive(KpiSummary summary) {
        return summary.lengthStratifiedKpi().shortBucket().brandAssertiveCount()
                + summary.lengthStratifiedKpi().mediumBucket().brandAssertiveCount()
                + summary.lengthStratifiedKpi().longBucket().brandAssertiveCount();
    }

    private int nonEmptyBucketCount(KpiSummary summary) {
        int count = 0;
        if (summary.orthogonalKpi().effectiveSampleSize() > 0) {
            count++;
        }
        if (summary.paretoKpi().effectiveSampleSize() > 0) {
            count++;
        }
        if (summary.adversarialKpi().effectiveSampleSize() > 0) {
            count++;
        }
        return count;
    }

    private int nonEmptyLengthCount(KpiSummary summary) {
        int count = 0;
        if (summary.lengthStratifiedKpi().shortBucket().totalProbes() > 0) {
            count++;
        }
        if (summary.lengthStratifiedKpi().mediumBucket().totalProbes() > 0) {
            count++;
        }
        if (summary.lengthStratifiedKpi().longBucket().totalProbes() > 0) {
            count++;
        }
        return count;
    }

    private double confidenceScore(double rate, int denominator) {
        double p = Math.max(0.0d, Math.min(1.0d, rate));
        double n = denominator;
        double denominatorTerm = 1.0d + (WILSON_Z * WILSON_Z / n);
        double center = p + (WILSON_Z * WILSON_Z / (2.0d * n));
        double margin = WILSON_Z * Math.sqrt((p * (1.0d - p) / n) + (WILSON_Z * WILSON_Z / (4.0d * n * n)));
        double lower = Math.max(0.0d, (center - margin) / denominatorTerm);
        double upper = Math.min(1.0d, (center + margin) / denominatorTerm);
        return clampScore((1.0d - (upper - lower)) * 100.0d);
    }

    private double coverageScore(int supportedBuckets, int totalBuckets) {
        if (totalBuckets <= 0) {
            return 0.0d;
        }
        return (supportedBuckets * 100.0d) / totalBuckets;
    }

    private double clampScore(double value) {
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record RateObservation(double rate, int denominator) {
    }
}
