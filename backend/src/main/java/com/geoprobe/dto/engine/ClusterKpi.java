package com.geoprobe.dto.engine;

public record ClusterKpi(
        String clusterId,
        String clusterName,
        double mentionRate,
        double recommendRate,
        double top3Rate,
        int sampleCount,
        int mentionedCount,
        int recommendedCount,
        int top3Count,
        double clusterWeight,
        String eeatDimension,
        String involvementLevel
) {

    /**
     * 兼容旧构造，默认不带权重和维度元信息。
     */
    public ClusterKpi(String clusterId,
                      String clusterName,
                      double mentionRate,
                      double recommendRate,
                      double top3Rate,
                      int sampleCount,
                      int mentionedCount,
                      int recommendedCount,
                      int top3Count) {
        this(clusterId, clusterName, mentionRate, recommendRate, top3Rate, sampleCount, mentionedCount, recommendedCount, top3Count, 0.0d, null, null);
    }
}
