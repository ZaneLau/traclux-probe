package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42StabilityServiceTest {

    private final V42StabilityService stabilityService = new V42StabilityService();

    @Test
    void shouldReturnHigherScoreForLargerAndBetterCoveredSamples() {
        double strong = stabilityService.calculate(summary(100, true, true));
        double weak = stabilityService.calculate(summary(6, false, false));

        assertTrue(strong > weak);
        assertTrue(strong <= 100.0d);
        assertTrue(weak >= 0.0d);
    }

    @Test
    void shouldReturnZeroWhenNoEffectiveSampleExists() {
        double score = stabilityService.calculate(summary(0, false, false));

        assertEquals(0.0d, score);
    }

    private KpiSummary summary(int effectiveSampleSize, boolean fullBucketCoverage, boolean fullLengthCoverage) {
        int orthogonalSize = effectiveSampleSize == 0 ? 0 : (fullBucketCoverage ? Math.max(1, effectiveSampleSize / 2) : effectiveSampleSize);
        int paretoSize = effectiveSampleSize == 0 ? 0 : (fullBucketCoverage ? Math.max(1, effectiveSampleSize / 3) : 0);
        int adversarialSize = effectiveSampleSize == 0 ? 0 : (fullBucketCoverage ? Math.max(1, effectiveSampleSize - orthogonalSize - paretoSize) : 0);

        int shortSize = effectiveSampleSize == 0 ? 0 : (fullLengthCoverage ? Math.max(1, effectiveSampleSize / 3) : effectiveSampleSize);
        int mediumSize = effectiveSampleSize == 0 ? 0 : (fullLengthCoverage ? Math.max(1, effectiveSampleSize / 3) : 0);
        int longSize = effectiveSampleSize == 0 ? 0 : (fullLengthCoverage ? Math.max(1, effectiveSampleSize - shortSize - mediumSize) : 0);

        return new KpiSummary(
                effectiveSampleSize == 0 ? 0.0d : 0.62d,
                effectiveSampleSize == 0 ? 0.0d : 0.74d,
                effectiveSampleSize == 0 ? 0.0d : 0.68d,
                new LengthStratifiedKpi(
                        new LengthBucketKpi("Short", shortSize, shortSize, 0, effectiveSampleSize == 0 ? 0.0d : 0.62d, 0.0d, effectiveSampleSize == 0 ? 0.0d : 1.1d),
                        new LengthBucketKpi("Medium", mediumSize, 0, mediumSize, 0.0d, effectiveSampleSize == 0 ? 0.0d : 0.74d, effectiveSampleSize == 0 ? 0.0d : 1.2d),
                        new LengthBucketKpi("Long", longSize, Math.max(0, longSize / 2), Math.max(0, longSize - Math.max(0, longSize / 2)), effectiveSampleSize == 0 ? 0.0d : 0.58d, effectiveSampleSize == 0 ? 0.0d : 0.71d, effectiveSampleSize == 0 ? 0.0d : 1.3d)
                ),
                effectiveSampleSize == 0 ? 0.0d : 0.49d,
                effectiveSampleSize == 0 ? 0.0d : 0.56d,
                0.0d,
                new BucketKpi(BucketType.ORTHOGONAL, orthogonalSize, orthogonalSize, orthogonalSize, 0, effectiveSampleSize == 0 ? 0.0d : 0.62d, 0.0d, effectiveSampleSize == 0 ? 0.0d : 0.62d, effectiveSampleSize == 0 ? 0.0d : 0.40d, effectiveSampleSize == 0 ? 0.0d : 0.46d),
                new BucketKpi(BucketType.PARETO, paretoSize, paretoSize, 0, paretoSize, 0.0d, effectiveSampleSize == 0 ? 0.0d : 0.74d, effectiveSampleSize == 0 ? 0.0d : 0.74d, effectiveSampleSize == 0 ? 0.0d : 0.61d, effectiveSampleSize == 0 ? 0.0d : 0.61d),
                new BucketKpi(BucketType.ADVERSARIAL, adversarialSize, adversarialSize, 0, adversarialSize, 0.0d, effectiveSampleSize == 0 ? 0.0d : 0.55d, effectiveSampleSize == 0 ? 0.0d : 0.55d, effectiveSampleSize == 0 ? 0.0d : 0.22d, effectiveSampleSize == 0 ? 0.0d : 0.22d),
                radarMap(0.66d),
                radarMap(0.66d),
                Map.of(),
                effectiveSampleSize
        );
    }

    private Map<String, Double> radarMap(double value) {
        LinkedHashMap<String, Double> map = new LinkedHashMap<>();
        map.put("D1", value);
        map.put("D2", value);
        map.put("D3", value);
        map.put("D4", value);
        map.put("D5", value);
        map.put("D6", value);
        return map;
    }
}
