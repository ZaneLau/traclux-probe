package com.geoprobe.dto.workbench;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchOverviewView {

    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private String category;
    private String officialWebsite;
    private String primaryDomain;
    private String brandSpaceStatus;
    private String latestRunId;
    private String latestReportId;
    private String lastGeneratedAt;
    private WorkbenchAnalysisContextView analysisContext;
    private WorkbenchDataAvailabilityView dataAvailability;
    private List<CardView> cards;
    private CurrentPerformanceView currentPerformance;
    private LatestInsightView latestInsight;
    private TrendView trend;
    private List<PriorityItemView> priorities;
    private List<CompetitorSummaryView> competitorRows;
    private List<SourceSummaryView> sourceRows;
    private List<ModelSummaryView> modelPerformance;
    private Map<String, String> detailRoutes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardView {
        private String cardKey;
        private String title;
        private String primary;
        private String secondary;
        private String detailRouteKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentPerformanceView {
        private Double visibilityScore;
        private Double competitorVisibilityAverage;
        private Double recommendRate;
        private Double mentionRate;
        private Double top3Rate;
        private String abvGrade;
        private String headline;
        private String generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendView {
        private String direction;
        private Double deltaVisibilityScore;
        private Double deltaRecommendRate;
        private Double deltaMentionRate;
        private List<TrendPointView> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatestInsightView {
        private String generatedAt;
        private String engineVersion;
        private String runId;
        private String reportId;
        private String insightSource;
        private Integer sampleSize;
        private String headline;
        private List<InsightItemView> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightItemView {
        private String title;
        private String evidence;
        private String evidenceSummary;
        private String recommendation;
        private String nextStepLabel;
        private String nextStepText;
        private String nextStepRouteKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPointView {
        private String reportId;
        private String runId;
        private String label;
        private String generatedAt;
        private Double visibilityScore;
        private Double recommendRate;
        private Double mentionRate;
        private Double top3Rate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityItemView {
        private String id;
        private String title;
        private String summary;
        private String reason;
        private String detailRouteKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorSummaryView {
        private String id;
        private String brandName;
        private Integer rank;
        private Boolean isTarget;
        private Double visibilityScore;
        private Double displayScore;
        private String rankingMetric;
        private Double recommendRate;
        private Double mentionRate;
        private Double positiveRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSummaryView {
        private String id;
        private String label;
        private String type;
        private Double share;
        private String shareLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelSummaryView {
        private String id;
        private String label;
        private Double visibilityScore;
        private Double recommendRate;
        private Double mentionRate;
        private Double deltaVisibilityScore;
        private Integer reportCount;
        private Boolean primary;
    }
}
