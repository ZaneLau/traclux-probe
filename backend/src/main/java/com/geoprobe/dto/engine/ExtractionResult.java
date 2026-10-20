package com.geoprobe.dto.engine;

import java.util.List;

public record ExtractionResult(
        List<CompetitorRankingItem> competitorRanking,
        List<AssociatedKeywordStat> associatedKeywords,
        List<NegativeSignalItem> negativeSignals,
        int totalParsedRuns
) {
}
