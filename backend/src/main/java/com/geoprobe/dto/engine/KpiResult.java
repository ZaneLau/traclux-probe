package com.geoprobe.dto.engine;

import java.util.List;

public record KpiResult(
        double l1MentionRate,
        double l2RecommendRate,
        double l3Top3Rate,
        int totalSuccessRuns,
        int mentionedCount,
        int recommendedCount,
        int top3Count,
        List<ClusterKpi> clusterKpis
) {
}
