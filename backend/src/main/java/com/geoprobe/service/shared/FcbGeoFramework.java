package com.geoprobe.service.shared;

import java.util.Map;

/**
 * FCB-GEO 六格矩阵常量定义。
 */
public final class FcbGeoFramework {

    private FcbGeoFramework() {
    }

    /**
     * 信息偏好维度（映射到 EEAT 解释口径）。
     */
    public enum InformationPreference {
        PARAMETER,
        EXPERIENCE,
        TRUST
    }

    /**
     * 决策介入度。
     */
    public enum InvolvementLevel {
        HIGH, LOW
    }

    /**
     * FCB-GEO 六格簇骨架。
     */
    public static final Map<String, ClusterSkeleton> CLUSTER_SKELETONS = Map.of(
            "HIGH_PARAMETER", new ClusterSkeleton(
                    "HIGH_PARAMETER",
                    "参数对比者",
                    "用户通过可量化的指标、技术参数、专业评测数据来做决策",
                    "PARAMETER",
                    "HIGH"
            ),
            "HIGH_EXPERIENCE", new ClusterSkeleton(
                    "HIGH_EXPERIENCE",
                    "真实体验者",
                    "用户关注真实的使用/体验感受、长期效果、来自真实用户的评价反馈",
                    "EXPERIENCE",
                    "HIGH"
            ),
            "HIGH_TRUST", new ClusterSkeleton(
                    "HIGH_TRUST",
                    "品牌信赖者",
                    "用户关注品牌/机构的权威性、行业认证、专家或官方背书",
                    "TRUST",
                    "HIGH"
            ),
            "LOW_PARAMETER", new ClusterSkeleton(
                    "LOW_PARAMETER",
                    "功能刚需者",
                    "用户关注基本需求满足和核心决策条件，倾向快速决策",
                    "PARAMETER",
                    "LOW"
            ),
            "LOW_EXPERIENCE", new ClusterSkeleton(
                    "LOW_EXPERIENCE",
                    "大众跟随者",
                    "用户跟随大众选择，关注热度、口碑数量和热门推荐",
                    "EXPERIENCE",
                    "LOW"
            ),
            "LOW_TRUST", new ClusterSkeleton(
                    "LOW_TRUST",
                    "熟悉度选择者",
                    "用户基于品牌/机构熟悉度和过往经验做选择",
                    "TRUST",
                    "LOW"
            )
    );

    public record ClusterSkeleton(
            String clusterId,
            String clusterName,
            String description,
            String eeatDimension,
            String involvementLevel
    ) {
    }
}
