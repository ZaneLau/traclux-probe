package com.geoprobe.dto.workbench;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchBrandPerformanceView {

    private WorkbenchBrandIdentityView identity;
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
    private SummaryMetricView summary;
    private List<CompetitorRowView> competitorRows;
    private List<ModelPerformanceItemView> modelPerformance;
    private List<TrendPointView> trendPoints;
    private List<TopicSummaryView> topicSummaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryMetricView {
        private Double visibilityScore;
        private Double recommendRate;
        private Double mentionRate;
        private Double top3Rate;
        private String abvGrade;
        private Integer sampleSize;
        private String generatedAt;
        private Integer visibilityRank;
        private Integer visibilityRankDelta;
        private Double shareOfVoice;
        private Double shareOfVoiceDelta;
        private Double averagePosition;
        private Double averagePositionDelta;
        private String headline;
        private Integer issueCount;
        private Integer evidenceCount;
        private List<String> topTopicLabels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorRowView {
        private String id;
        private String brandName;
        private Integer rank;
        private Boolean isTarget;
        private Double visibilityScore;
        private Double recommendRate;
        private Double mentionRate;
        private Double positiveRate;
        private Double top3Rate;
        private Integer rankDelta;
        private Double shareOfVoice;
        private Double shareOfVoiceDelta;
        private Double averagePosition;
        private Double averagePositionDelta;
        private Integer mentionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformanceItemView {
        private String id;
        private String label;
        private Boolean primary;
        private Integer reportCount;
        private String latestRunId;
        private String latestReportId;
        private Double visibilityScore;
        private Double recommendRate;
        private Double mentionRate;
        private Double top3Rate;
        private Double deltaVisibilityScore;
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
    public static class TopicSummaryView {
        private String topicId;
        private String topicName;
        private Integer seqNo;
        private Boolean enabled;
        private Integer sampleSize;
        private Double visibilityScore;
        private Double mentionRate;
        private Double recommendRate;
        private Double top3Rate;
        private Double averagePosition;
        private Integer mentionCount;
        private Integer promptCount;
        private Integer enabledPromptCount;
        private Double promptShare;
        private Integer signalCount;
        private Double signalShare;
        private List<String> sampleQuestions;
    }
}
