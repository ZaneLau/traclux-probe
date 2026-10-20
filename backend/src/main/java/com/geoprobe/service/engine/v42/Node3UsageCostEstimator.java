package com.geoprobe.service.engine.v42;

import com.geoprobe.common.LlmCostCalculator;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3UsageCostSummary;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class Node3UsageCostEstimator {

    private static final BigDecimal DEFAULT_INPUT_COST_PER_1K = new BigDecimal("0.0008");
    private static final BigDecimal DEFAULT_OUTPUT_COST_PER_1K = new BigDecimal("0.008");
    private static final BigDecimal DEFAULT_SHORT_OUTPUT_COST_PER_1K = new BigDecimal("0.002");
    private static final int DEFAULT_SHORT_OUTPUT_THRESHOLD = 200;
    private static final BigDecimal DEFAULT_WEB_SEARCH_COST_PER_CALL = new BigDecimal("0.004");

    private BigDecimal inputCostPer1k = DEFAULT_INPUT_COST_PER_1K;
    private BigDecimal outputCostPer1k = DEFAULT_OUTPUT_COST_PER_1K;
    private BigDecimal shortOutputCostPer1k = DEFAULT_SHORT_OUTPUT_COST_PER_1K;
    private int shortOutputThreshold = DEFAULT_SHORT_OUTPUT_THRESHOLD;
    private BigDecimal webSearchCostPerCall = DEFAULT_WEB_SEARCH_COST_PER_CALL;

    void setInputCostPer1k(String value) {
        this.inputCostPer1k = parseCost(value, DEFAULT_INPUT_COST_PER_1K);
    }

    void setOutputCostPer1k(String value) {
        this.outputCostPer1k = parseCost(value, DEFAULT_OUTPUT_COST_PER_1K);
    }

    void setShortOutputCostPer1k(String value) {
        this.shortOutputCostPer1k = parseCost(value, DEFAULT_SHORT_OUTPUT_COST_PER_1K);
    }

    void setShortOutputThreshold(int value) {
        this.shortOutputThreshold = Math.max(0, value);
    }

    void setWebSearchCostPerCall(String value) {
        this.webSearchCostPerCall = parseCost(value, DEFAULT_WEB_SEARCH_COST_PER_CALL);
    }

    Node3UsageCostSummary computeSummary(List<Node3ProbeOutput> outputs, String providerCode) {
        long promptTokenTotal = 0L;
        long completionTokenTotal = 0L;
        long totalTokenTotal = 0L;
        int searchCallTotal = 0;
        BigDecimal modelCost = BigDecimal.ZERO;
        String normalizedProvider = StringUtils.hasText(providerCode) ? providerCode.trim() : "doubao";

        for (Node3ProbeOutput output : outputs) {
            if (output == null || output.rawResponse() == null) {
                continue;
            }
            RawResponse raw = output.rawResponse();
            int promptTokens = Math.max(0, raw.promptTokenCount());
            int completionTokens = Math.max(0, raw.completionTokenCount());
            int totalTokens = Math.max(0, raw.totalTokenCount());

            promptTokenTotal += promptTokens;
            completionTokenTotal += completionTokens;
            totalTokenTotal += totalTokens > 0 ? totalTokens : promptTokens + completionTokens;
            searchCallTotal += Math.max(0, raw.searchCallCount());
            modelCost = modelCost.add(estimateModelCost(normalizedProvider, raw.modelId(), promptTokens, completionTokens));
        }

        BigDecimal searchCost = webSearchCostPerCall.multiply(BigDecimal.valueOf(searchCallTotal));
        BigDecimal totalCost = modelCost.add(searchCost);
        return new Node3UsageCostSummary(
                promptTokenTotal,
                completionTokenTotal,
                totalTokenTotal,
                searchCallTotal,
                toCostScale(modelCost),
                toCostScale(searchCost),
                toCostScale(totalCost)
        );
    }

    private BigDecimal estimateModelCost(String providerCode,
                                         String modelId,
                                         int promptTokens,
                                         int completionTokens) {
        return LlmCostCalculator.estimateCostYuanWithFallback(
                providerCode,
                modelId,
                promptTokens,
                completionTokens,
                inputCostPer1k,
                outputCostPer1k,
                shortOutputCostPer1k,
                shortOutputThreshold
        );
    }

    private BigDecimal parseCost(String value, BigDecimal fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal toCostScale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                : value.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
    }
}
