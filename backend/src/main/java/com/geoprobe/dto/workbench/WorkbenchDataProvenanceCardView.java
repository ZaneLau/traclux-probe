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
public class WorkbenchDataProvenanceCardView {

    private String contractVersion;
    private String title;
    private String brandSpaceId;
    private AnchorView anchor;
    private ResultExplanationView resultExplanation;
    private RunView run;
    private PlanView plan;
    private EvidenceView evidence;
    private SamplePageView samplePage;
    private BillingView billing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnchorView {
        private String anchorType;
        private String anchorKey;
        private String metricKey;
        private String metricLabel;
        private String metricValue;
        private String timeWindowLabel;
        private String topicId;
        private String topicName;
        private String probeId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultExplanationView {
        private String status;
        private String statusLabel;
        private String statusNote;
        private String metricLabel;
        private String metricValue;
        private String timeWindowLabel;
        private String anchorLabel;
        private String meaning;
        private String calculation;
        private String limitationNote;
        private List<CompositionItemView> composition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompositionItemView {
        private String label;
        private String value;
        private String description;
        private String formulaKey;
        private Double rateValue;
        private Integer weightPercent;
        private Double contributionValue;
        private String numerator;
        private String denominator;
        private String sourceMetricKey;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunView {
        private String runId;
        private String status;
        private String generatedAt;
        private String model;
        private String engineVersion;
        private Integer targetProbeCount;
        private Integer completedProbeCount;
        private Integer failedProbeCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanView {
        private String planVersionId;
        private String planRecordId;
        private Integer planVersionNo;
        private Integer planRecordNo;
        private String changeTypeCode;
        private String executeModeCode;
        private Integer estimatedCredits;
        private Integer recurringEstimatedCreditsPerCycle;
        private Integer topicCount;
        private Integer probeCount;
        private List<PlanItemView> sampleProbes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanItemView {
        private String probeId;
        private String topicId;
        private Integer seqNo;
        private String promptText;
        private String actionCode;
        private String executeModeCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceView {
        private Integer probeCount;
        private Integer selectedProbeCount;
        private Integer attemptCount;
        private Integer citationCount;
        private Integer brandMentionCount;
        private List<ProbeView> probes;
        private List<CitationView> citations;
        private List<BrandMentionView> brandMentions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SamplePageView {
        private String sourceStatus;
        private String sourceStatusLabel;
        private String sourceNote;
        private Integer page;
        private Integer pageSize;
        private Integer pageCount;
        private Integer totalCount;
        private Integer effectiveAnswerCount;
        private Integer excludedAnswerCount;
        private Integer mentionHitCount;
        private Integer recommendHitCount;
        private Integer top3HitCount;
        private List<AnswerSampleView> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSampleView {
        private Integer rowNo;
        private String runId;
        private String sourceTaskId;
        private String generatedAt;
        private String probeId;
        private String sourceProbeId;
        private String attemptId;
        private String sourceAttemptId;
        private String probeRef;
        private Integer seqNo;
        private String topicId;
        private String topicName;
        private String promptText;
        private String answerSnippet;
        private String provider;
        private String modelId;
        private Boolean effectiveAnswer;
        private String excludedReason;
        private Boolean mentioned;
        private Boolean recommended;
        private Boolean top3;
        private Integer recommendationRank;
        private String detailRouteKey;
        private String detailProbeId;
        private String detailAttemptId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProbeView {
        private String probeId;
        private String probeRef;
        private Integer seqNo;
        private String promptText;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationView {
        private String evidenceId;
        private String probeId;
        private String title;
        private String domain;
        private String url;
        private String sourceType;
        private String capturedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandMentionView {
        private String probeId;
        private String brandName;
        private Boolean mentioned;
        private Boolean recommended;
        private Integer positionIndex;
        private Integer sentimentCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingView {
        private Integer ledgerCount;
        private Long consumedCredits;
        private Long latestBalanceAfter;
        private List<LedgerView> ledgers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerView {
        private String ledgerId;
        private String ledgerTypeCode;
        private Long probeDelta;
        private Long balanceAfter;
        private String sourceTypeCode;
        private String createdAt;
        private String memo;
    }
}
