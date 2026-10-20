package com.geoprobe.dto.brandspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandSpaceScheduleExplanationView {

    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private String trackingStatus;
    private Boolean configReady;
    private String lastRunId;
    private String lastRunAt;
    private Boolean snapshotAvailable;
    private String snapshotMessage;
    private BrandSpaceScheduleView latestSchedule;
    private ConfigSnapshotView configSnapshot;
    private List<TopicDecisionView> selectedTopics;
    private List<TopicDecisionView> deferredTopics;
    private List<TopicIssueView> blockedTopics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigSnapshotView {
        private Integer topicCount;
        private Integer enabledTopicCount;
        private Integer promptCount;
        private Integer enabledPromptCount;
        private Integer executableTopicCount;
        private Integer blockedTopicCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicDecisionView {
        private String topicId;
        private String topicName;
        private Integer allocatedProbeCount;
        private Double priorityScore;
        private List<String> reasonTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicIssueView {
        private String topicId;
        private String topicName;
        private String issueCode;
        private String issueLabel;
        private String issueDetail;
    }
}
