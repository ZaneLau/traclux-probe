package com.geoprobe.dto.brandspace;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandSpaceRunDetailView {

    private String brandSpaceId;
    private String brandName;
    private String runId;
    private OverviewView overview;
    private BrandSpaceRunPlanTraceView planTrace;
    private ScheduleDecisionView scheduleDecision;
    private ExecutionRecordView executionRecord;
    private RawRecordView rawRecord;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OverviewView {
        private String runDate;
        private String startedAt;
        private String completedAt;
        private String updatedAt;
        private Long durationSeconds;
        private String triggerMode;
        private String triggerLabel;
        private String status;
        private String statusLabel;
        private Integer plannedProbeCount;
        private Integer completedProbeCount;
        private Integer failedProbeCount;
        private Integer evidenceCount;
        private Integer coverageTopicCount;
        private String reportGeneratedAt;
        private String failureMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScheduleDecisionView {
        private Boolean snapshotAvailable;
        private String coverageStrategy;
        private String schedulingReason;
        private Boolean coldStart;
        private Boolean justEdited;
        private Boolean quotaLimited;
        private Integer accountBrandCount;
        private Integer brandCoreProbeCount;
        private Integer brandExpansionProbeCount;
        private Integer weeklyTargetTopicCount;
        private Integer weeklyCoveredTopicCount;
        private Integer weeklyGapTopicCount;
        private Double weeklyCoverageRatio;
        private List<TopicDecisionView> selectedTopics;
        private List<TopicDecisionView> deferredTopics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TopicDecisionView {
        private String topicId;
        private String topicName;
        private Integer plannedProbeCount;
        private Double priorityScore;
        private List<String> reasonTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExecutionRecordView {
        private Integer selectedTopicCount;
        private Integer deferredTopicCount;
        private Integer completedProbeCount;
        private Integer failedProbeCount;
        private List<TopicExecutionView> topicExecutions;
        private List<ProbeExecutionView> probeExecutions;
        private List<MilestoneView> milestones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TopicExecutionView {
        private String topicId;
        private String topicName;
        private Integer plannedProbeCount;
        private Integer promptCount;
        private List<String> promptSamples;
        private Double priorityScore;
        private List<String> reasonTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProbeExecutionView {
        private String probeId;
        private String probeRef;
        private Integer seqNo;
        private String topicId;
        private String topicName;
        private String promptId;
        private String renderedPrompt;
        private String modelId;
        private String status;
        private String statusLabel;
        private Integer evidenceCount;
        private Integer latencyMs;
        private String createdAt;
        private Boolean provisional;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MilestoneView {
        private String type;
        private String label;
        private String createdAt;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RawRecordView {
        private Map<String, Object> taskOrder;
        private Map<String, Object> scheduleDecision;
        private Map<String, Object> reportSummary;
        private List<RunEventView> events;
        private String failureMessage;
    }
}
