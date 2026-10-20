package com.geoprobe.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmCostCalculatorTest {

    @Test
    void shouldEstimateDeepseekCostWithPerMillionPricing() {
        BigDecimal cost = LlmCostCalculator.estimateCostYuan("deepseek", "deepseek-v3.2", 1_000, 2_000);

        assertEquals(new BigDecimal("0.008000"), cost);
    }

    @Test
    void shouldEstimateDoubaoCostWithShortOutputTier() {
        BigDecimal cost = LlmCostCalculator.estimateCostYuan("volcengine", "doubao-seed-1.8", 1_000, 200);

        assertEquals(new BigDecimal("0.001200"), cost);
    }

    @Test
    void shouldPreferModelHintWhenProviderIsVolcengineButModelIsDeepseek() {
        BigDecimal cost = LlmCostCalculator.estimateCostYuan("volcengine", "deepseek-v3.2", 1_000, 1_000);

        assertEquals(new BigDecimal("0.005000"), cost);
    }

    @Test
    void shouldReturnNullForUnknownProviderAndModel() {
        BigDecimal cost = LlmCostCalculator.estimateCostYuan("unknown", "unknown", 100, 200);

        assertNull(cost);
    }
}
