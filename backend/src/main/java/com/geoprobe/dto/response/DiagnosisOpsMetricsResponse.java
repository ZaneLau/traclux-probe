package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisOpsMetricsResponse {
    private String diagnosisId;
    private Double completedRate;
    private Double timeoutRate;
    private Double fallbackRate;
    private Double refundRate;
    private Integer p95LatencyMs;
    private BigDecimal totalLlmCostYuan;
    private Integer totalApiCalls;
    private Integer totalSamples;
    private Integer successSamples;
    private Integer failedSamples;
    private Integer timeoutSamples;
    private Boolean fallbackUsed;
    private Boolean refunded;

    /**
     * V4.2 Node3 采样阶段（含联网插件）的费用估算（元）。
     */
    private BigDecimal node3LlmCostYuan;

    /**
     * V4.2 非 Node3 阶段（Node0/2/4/5 等）LLM 费用汇总（元）。
     */
    private BigDecimal nonSamplingLlmCostYuan;

    /**
     * 全链路 Token 汇总（Node0~5C）。
     */
    private Long fullChainPromptTokenTotal;
    private Long fullChainCompletionTokenTotal;
    private Long fullChainTokenTotal;

    /**
     * Node3 Token 汇总。
     */
    private Long node3PromptTokenTotal;
    private Long node3CompletionTokenTotal;
    private Long node3TokenTotal;
}
