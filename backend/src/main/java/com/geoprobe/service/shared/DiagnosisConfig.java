package com.geoprobe.service.shared;

/**
 * 诊断执行参数配置（按版本档位）。
 * 成本约束（DFCA）：
 * C8: 校验/重试开销上限 <= 基础成本的 10%，总成本上限 = baseCost * 1.1。
 * 当前校验流程均为纯规则校验（无新增 LLM 调用），实际额外成本约等于 0。
 */
public class DiagnosisConfig {

    // 核心 Prompt 总数（用于聚类视角规划，不直接决定最终探针总数）
    private final int totalCorePrompts;
    // 每簇最少核心 Prompt 数
    private final int minCorePromptsPerCluster;
    // 每核心问题的默认展开密度（仅用于前台预览与规划，不是最终交付条数）
    private final int variantsPerCorePrompt;

    public DiagnosisConfig(int totalCorePrompts, int minCorePromptsPerCluster, int variantsPerCorePrompt) {
        this.totalCorePrompts = totalCorePrompts;
        this.minCorePromptsPerCluster = minCorePromptsPerCluster;
        this.variantsPerCorePrompt = variantsPerCorePrompt;
    }

    public static DiagnosisConfig experienceEdition() {
        return new DiagnosisConfig(7, 2, 5);
    }

    public static DiagnosisConfig standardEdition() {
        return new DiagnosisConfig(11, 2, 10);
    }

    public static DiagnosisConfig deepEdition() {
        return new DiagnosisConfig(18, 2, 30);
    }

    /**
     * 当前配置允许的最大簇数。
     */
    public int getMaxClusterCount() {
        return totalCorePrompts / minCorePromptsPerCluster;
    }

    public int getTotalCorePrompts() {
        return totalCorePrompts;
    }

    public int getMinCorePromptsPerCluster() {
        return minCorePromptsPerCluster;
    }

    public int getVariantsPerCorePrompt() {
        return variantsPerCorePrompt;
    }
}
