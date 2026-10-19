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
public class WorkbenchProblemAnalysisView {

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
    private SummaryView summary;
    private CatalogView catalog;
    private List<TopicRowView> topicRows;
    private List<PromptRowView> promptRows;
    private List<ProblemClusterView> problemClusters;
    private List<ProbeRowView> probeRows;
    private List<EvidenceRowView> evidenceRows;
    private RichView rich;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryView {
        private Integer topicCount;
        private Integer promptCount;
        private Integer enabledPromptCount;
        private Integer problemClusterCount;
        private Integer probeCount;
        private Integer attemptCount;
        private Integer linkedProbeCount;
        private Integer evidenceCount;
        private Integer linkedEvidenceCount;
        private Integer rawAnswerCount;
        private Integer linkedPromptCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicRowView {
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private Integer seqNo;
        private Boolean enabled;
        private Integer promptCount;
        private Integer enabledPromptCount;
        private Integer signalCount;
        private List<String> sampleQuestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptRowView {
        private String promptId;
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private Integer seqNo;
        private String promptText;
        private Boolean enabled;
        private Integer probeCount;
        private Integer evidenceCount;
        private Boolean hasRawAnswer;
        private String latestProbeStatus;
        private Integer visibilityRank;
        private Double visibilityScore;
        private Double visibilityDelta;
        private Double averagePosition;
        private Double averageDelta;
        private Double citationShare;
        private Double citationDelta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemClusterView {
        private String id;
        private String title;
        private String summary;
        private String severity;
        private String problemSpaceBucket;
        private Integer signalCount;
        private List<String> representativeQuestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProbeRowView {
        private String probeId;
        private String probeRef;
        private Integer seqNo;
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private String promptId;
        private String promptText;
        private String renderText;
        private String status;
        private Integer evidenceCount;
        private Boolean hasRawAnswer;
        private String latestAttemptId;
        private String model;
        private String answerPreview;
        private String rawAnswer;
        private Double visibilityScore;
        private Double averagePosition;
        private Integer sentimentScore;
        private Integer mentionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRowView {
        private String evidenceId;
        private String attemptId;
        private String probeId;
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private String promptId;
        private String promptText;
        private String sourceType;
        private String domain;
        private String url;
        private String title;
        private String snippet;
        private String capturedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptRowView {
        private String attemptId;
        private String probeId;
        private String probeRef;
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private String promptId;
        private String promptText;
        private String provider;
        private String model;
        private String status;
        private String createdAt;
        private String query;
        private String answerPreview;
        private String rawAnswer;
        private List<String> summaryBullets;
        private List<BrandHitView> brandHits;
        private List<CitationView> citations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandHitView {
        private String brandName;
        private Double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationView {
        private String evidenceId;
        private String title;
        private String url;
        private String domain;
    }

    private List<AttemptRowView> attemptRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RichView {
        private VolumeView volume;
        private IntentView intent;
        private AudienceView audience;
        private HierarchyView hierarchy;
        private FanoutView fanout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogView {
        private List<CatalogBucketView> problemSpaceBuckets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogBucketView {
        private String key;
        private String label;
        private String description;
        private Integer topicCount;
        private Integer promptCount;
        private Integer volume;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeView {
        private List<VolumeTrendPointView> trendPoints;
        private VolumeSummaryView summary;
        private List<PlatformDemandRowView> platformRows;
        private List<PromptOpportunityRowView> promptOpportunityRows;
        private PromptProjectionSummaryView promptProjectionSummary;
        private List<PromptProjectionRowView> promptProjectionRows;
        private PromptLoopSummaryView promptLoopSummary;
        private List<PromptLoopRowView> promptLoopRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeTrendPointView {
        private String sliceKey;
        private String label;
        private String generatedAt;
        private Integer currentValue;
        private Integer previousValue;
        private Boolean isLatest;
        private VolumeTimeSliceSnapshotView snapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeTimeSliceSnapshotView {
        private VolumeSummaryView summary;
        private List<VolumeTopicSliceRowView> topTopics;
        private List<PlatformDemandRowView> platformRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeTopicSliceRowView {
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private Integer demandCount;
        private Double share;
        private String leadPromptText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeSummaryView {
        private Integer demandTotal;
        private Double demandDelta;
        private Integer activeTopicCount;
        private Integer activePlatformCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformDemandRowView {
        private String platform;
        private Integer value;
        private Double share;
        private Double delta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptOpportunityRowView {
        private String prompt;
        private String topic;
        private String problemSpaceBucket;
        private Integer demandIndex;
        private Integer projection;
        private Integer competitionScore;
        private Integer relevanceScore;
        private Integer opportunityScore;
        private String intentLabel;
        private Double trendDelta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptProjectionSummaryView {
        private Integer promptCount;
        private Integer totalProjection;
        private Integer highValuePromptCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptProjectionRowView {
        private String prompt;
        private Integer projection;
        private Double share;
        private String intentLabel;
        private String action;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptLoopSummaryView {
        private Double coverage;
        private Integer gapCount;
        private Integer highOpportunityCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptLoopRowView {
        private String id;
        private String topic;
        private String problemSpaceBucket;
        private String prompt;
        private String status;
        private String citedTarget;
        private Integer opportunity;
        private String action;
        private Boolean targetBrandCited;
        private Double citationCoverage;
        private String competitorLeadBrand;
        private List<String> citedBrandNames;
        private String gapType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentView {
        private List<IntentStatView> intents;
        private List<IntentSubIntentView> subIntents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentStatView {
        private String key;
        private String label;
        private Integer count;
        private Double share;
        private List<String> examples;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentSubIntentView {
        private String key;
        private String parent;
        private String parentLabel;
        private String label;
        private Integer count;
        private Double share;
        private Double change;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceView {
        private List<AudienceDistributionRowView> roleRows;
        private List<AudienceDistributionRowView> budgetRows;
        private List<AudiencePlatformRowView> platformRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceDistributionRowView {
        private String label;
        private Double share;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudiencePlatformRowView {
        private String platform;
        private Double score;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HierarchyView {
        private List<HierarchyTopicView> topics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HierarchyTopicView {
        private String topic;
        private String problemSpaceBucket;
        private Integer demandIndex;
        private List<HierarchyClusterView> clusters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HierarchyClusterView {
        private String label;
        private Integer count;
        private List<String> samples;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FanoutView {
        private List<FanoutPromptRowView> promptRows;
        private List<FanoutPlatformRowView> platformRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FanoutPromptRowView {
        private String id;
        private String topic;
        private String problemSpaceBucket;
        private String prompt;
        private Integer queryCount;
        private Double avgQueriesPerExecution;
        private List<FanoutVariationRowView> variations;
        private List<FanoutPlatformInsightRowView> platformRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FanoutVariationRowView {
        private String id;
        private String prompt;
        private Integer queryCount;
        private Double delta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FanoutPlatformInsightRowView {
        private String platform;
        private Integer queryCount;
        private Double share;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FanoutPlatformRowView {
        private Integer rank;
        private String platform;
        private Double avgQueriesPerExecution;
        private Double delta;
    }
}
