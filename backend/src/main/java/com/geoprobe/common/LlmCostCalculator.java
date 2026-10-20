package com.geoprobe.common;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * LLM 调用成本估算器（单位：人民币元）。
 * 说明：用于落库 llm_call_logs.cost_yuan，便于运维看板和账务对账。
 */
public final class LlmCostCalculator {

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    // Doubao Seed 1.8 在线口径（元 / 千 tokens）
    private static final BigDecimal DOUBAO_INPUT_PER_1K = new BigDecimal("0.0008");
    private static final BigDecimal DOUBAO_OUTPUT_PER_1K = new BigDecimal("0.008");
    private static final BigDecimal DOUBAO_OUTPUT_SHORT_PER_1K = new BigDecimal("0.002");
    private static final int DOUBAO_OUTPUT_SHORT_THRESHOLD = 200;

    // DeepSeek-V3.2 在线口径（元 / 百万 tokens）
    private static final BigDecimal DEEPSEEK_INPUT_PER_1M = new BigDecimal("2.0");
    private static final BigDecimal DEEPSEEK_OUTPUT_PER_1M = new BigDecimal("3.0");

    // GLM-4.7（兜底）口径（元 / 百万 tokens）
    private static final BigDecimal ZHIPU_INPUT_PER_1M = new BigDecimal("3.0");
    private static final BigDecimal ZHIPU_OUTPUT_PER_1M = new BigDecimal("14.0");

    private LlmCostCalculator() {
    }

    public static BigDecimal estimateCostYuan(String provider,
                                              String model,
                                              Integer inputTokens,
                                              Integer outputTokens) {
        int safeInput = sanitizeTokenCount(inputTokens);
        int safeOutput = sanitizeTokenCount(outputTokens);
        if (safeInput <= 0 && safeOutput <= 0) {
            return null;
        }

        PricingProfile profile = resolveProfile(provider, model);
        return switch (profile) {
            case DOUBAO -> estimateDoubaoCost(safeInput, safeOutput);
            case DEEPSEEK -> estimatePerMillionCost(safeInput, safeOutput, DEEPSEEK_INPUT_PER_1M, DEEPSEEK_OUTPUT_PER_1M);
            case ZHIPU -> estimatePerMillionCost(safeInput, safeOutput, ZHIPU_INPUT_PER_1M, ZHIPU_OUTPUT_PER_1M);
            case UNKNOWN -> null;
        };
    }

    public static BigDecimal estimateCostYuanWithFallback(String provider,
                                                          String model,
                                                          Integer inputTokens,
                                                          Integer outputTokens,
                                                          BigDecimal fallbackInputPer1k,
                                                          BigDecimal fallbackOutputPer1k,
                                                          BigDecimal fallbackShortOutputPer1k,
                                                          int fallbackShortOutputThreshold) {
        BigDecimal catalogCost = estimateCostYuan(provider, model, inputTokens, outputTokens);
        if (catalogCost != null) {
            return catalogCost;
        }
        int safeInput = sanitizeTokenCount(inputTokens);
        int safeOutput = sanitizeTokenCount(outputTokens);
        BigDecimal inputRate = nonNegativeRate(fallbackInputPer1k);
        BigDecimal outputRate = nonNegativeRate(fallbackOutputPer1k);
        BigDecimal shortOutputRate = nonNegativeRate(fallbackShortOutputPer1k);
        BigDecimal inputCost = BigDecimal.valueOf(safeInput)
                .multiply(inputRate)
                .divide(ONE_THOUSAND, 12, RoundingMode.HALF_UP);
        BigDecimal appliedOutputRate = safeOutput > 0 && safeOutput <= Math.max(0, fallbackShortOutputThreshold)
                ? shortOutputRate
                : outputRate;
        BigDecimal outputCost = BigDecimal.valueOf(safeOutput)
                .multiply(appliedOutputRate)
                .divide(ONE_THOUSAND, 12, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeRate(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static int sanitizeTokenCount(Integer value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value;
    }

    private static BigDecimal estimateDoubaoCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(DOUBAO_INPUT_PER_1K)
                .divide(ONE_THOUSAND, 12, RoundingMode.HALF_UP);
        BigDecimal outputRate = outputTokens > 0 && outputTokens <= DOUBAO_OUTPUT_SHORT_THRESHOLD
                ? DOUBAO_OUTPUT_SHORT_PER_1K
                : DOUBAO_OUTPUT_PER_1K;
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(outputRate)
                .divide(ONE_THOUSAND, 12, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal estimatePerMillionCost(int inputTokens,
                                                     int outputTokens,
                                                     BigDecimal inputPerMillion,
                                                     BigDecimal outputPerMillion) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(inputPerMillion)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(outputPerMillion)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private static PricingProfile resolveProfile(String provider, String model) {
        String normalizedProvider = normalize(provider);
        String normalizedModel = normalize(model);

        // 优先以模型名识别，确保火山端点挂 deepseek 时仍走 deepseek 计价。
        if (normalizedModel.contains("deepseek") || normalizedProvider.contains("deepseek")) {
            return PricingProfile.DEEPSEEK;
        }
        if (normalizedModel.contains("glm") || normalizedProvider.contains("zhipu")) {
            return PricingProfile.ZHIPU;
        }
        if (normalizedModel.contains("doubao") || "volcengine".equals(normalizedProvider)) {
            return PricingProfile.DOUBAO;
        }
        return PricingProfile.UNKNOWN;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private enum PricingProfile {
        DOUBAO,
        DEEPSEEK,
        ZHIPU,
        UNKNOWN
    }
}
