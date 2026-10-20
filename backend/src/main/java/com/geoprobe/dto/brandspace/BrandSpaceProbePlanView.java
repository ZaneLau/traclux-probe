package com.geoprobe.dto.brandspace;

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
public class BrandSpaceProbePlanView {

    private String contractVersion;
    private String brandSpaceId;
    private BrandSpaceConfigView.RunConfigView currentRunConfig;
    private CurrentPlanView currentPlan;
    private List<RecordView> recentRecords;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentPlanView {
        private String planVersionId;
        private Integer versionNo;
        private Integer activeRecurringProbeCount;
        private Integer estimatedCreditsPerCycle;
        private Integer configGeneration;
        private Map<String, Object> snapshot;
        private List<ItemView> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordView {
        private String planRecordId;
        private String planVersionId;
        private Integer recordNo;
        private String changeTypeCode;
        private String executeModeCode;
        private Integer immediateEstimatedCredits;
        private Integer recurringEstimatedCreditsPerCycle;
        private String triggeredRunId;
        private Map<String, Object> summary;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemView {
        private String probeId;
        private String topicId;
        private Integer seqNo;
        private String promptText;
        private String actionCode;
        private String executeModeCode;
    }
}
