package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.geoprobe.service.shared.FcbClassification;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private String diagnosisId;
    private String brandName;
    private String category;

    private Map<String, Object> executiveSummary;
    private Map<String, Object> seoVsGeo;
    private List<Map<String, Object>> clusterAnalysis;
    private Map<String, Object> samplingStats;
    private Boolean samplingDegraded;
    private Boolean cleanRun;
    private List<Map<String, Object>> evidenceChain;
    private List<Map<String, Object>> probeRuntimeTruth;
    private List<Map<String, Object>> competitorRanking;
    private List<Map<String, Object>> negativeSignals;
    private List<Map<String, Object>> associatedKeywords;
    private Map<String, Object> actionItems;
    private Map<String, Object> meta;

    private BrandPerceptionData brandPerception;
    private SemanticDistanceData semanticDistanceMap;
    private ClusterBreakdownData clusterBreakdown;
    private CompetitorRadarData competitorRadar;
    private SourceAttributionData sourceAttribution;
    private EeatBreakdownData eeatBreakdown;
    private FcbClassification fcbClassification;
    private Map<String, String> kpiInterpretation;

    private RequestSummary requestSummary;
    private HeroSummary heroSummary;
    private List<InsightSection> evidenceSections;
    private List<InsightSection> transparencySections;
    private ActionBridge actionBridge;
    private List<ClusterProfile> clusterProfiles;
    private List<EvidenceChainCard> evidenceChains;
    private List<SourceDrilldown> sourceDrilldowns;
    private List<ActionPlanItem> actionPlan;
    private EeatAdvice eeatAdvice;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestSummary {
        private String brandName;
        private String category;
        private String website;
        private String websiteDisplay;
        private List<String> competitors;
        private String supplementalBrief;
        private String supplementalBriefDisplay;
        private String edition;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeroSummary {
        private String headline;
        private String verdict;
        private String topOpportunity;
        private String topRisk;
        private List<HeroKpi> kpis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeroKpi {
        private String key;
        private String label;
        private String value;
        private String context;
        private String availability;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightSection {
        private String key;
        private String eyebrow;
        private String title;
        private String summary;
        private String highlight;
        private String availability;
        private List<InsightMetric> metrics;
        private List<String> bullets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightMetric {
        private String label;
        private String value;
        private String context;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionBridge {
        private String headline;
        private String summary;
        private List<String> nowActions;
        private List<String> nextActions;
        private List<String> laterActions;
        private List<String> proofPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterProfile {
        private String key;
        private String title;
        private String archetype;
        private String problemSpaceBucket;
        private String description;
        private String focusLabel;
        private Integer sampleCount;
        private Boolean blindSpot;
        private List<String> prompts;
        private List<ClusterProfileMetric> metrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterProfileMetric {
        private String label;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceChainCard {
        private String key;
        private String clusterName;
        private String problemSpaceBucket;
        private String askerLabel;
        private String prompt;
        private String answer;
        private String judgement;
        private String sentiment;
        private List<String> keywords;
        private List<String> brands;
        private List<String> sourceLabels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDrilldown {
        private String key;
        private String label;
        private Integer count;
        private String percentText;
        private String availability;
        private List<String> evidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionPlanItem {
        private String key;
        private String priority;
        private String title;
        private String reason;
        private List<String> evidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EeatAdvice {
        private String headline;
        private String weakestDimension;
        private String nextMove;
        private String availability;
        private List<String> bullets;
    }
}
