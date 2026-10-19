package com.geoprobe.dto.engine;

public record SeoCompareResult(
        String queryKeyword,
        Integer baiduRank,
        double aiRecommendRate,
        String source,
        String gapType,
        String insight,
        String errorMessage
) {
}
