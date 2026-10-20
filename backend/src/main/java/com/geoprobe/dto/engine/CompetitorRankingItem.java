package com.geoprobe.dto.engine;

public record CompetitorRankingItem(
        String brand,
        double mentionRate,
        double recommendRate,
        double top3Rate,
        int mentionedCount,
        int recommendedCount,
        int top3Count,
        double averagePosition
) {
}
