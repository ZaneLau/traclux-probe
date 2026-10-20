package com.geoprobe.service.engine.v42;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V42LengthBucketResolverTest {

    @Test
    void shouldBuildDynamicQuantileThresholdsFromTaskDistribution() {
        V42LengthBucketResolver.LengthBucketThresholds thresholds =
                V42LengthBucketResolver.fromCompletionTokens(List.of(40, 60, 90, 150, 220, 260));

        assertEquals(60, thresholds.p33());
        assertEquals(150, thresholds.p66());
        assertEquals("Short", thresholds.bucketFor(60));
        assertEquals("Medium", thresholds.bucketFor(120));
        assertEquals("Long", thresholds.bucketFor(220));
    }
}
