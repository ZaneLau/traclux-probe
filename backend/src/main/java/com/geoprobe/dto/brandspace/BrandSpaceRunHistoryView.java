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
public class BrandSpaceRunHistoryView {

    private String brandSpaceId;
    private String brandName;
    private String dateFrom;
    private String dateTo;
    private Integer totalRuns;
    private List<RunListItemView> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RunListItemView {
        private String runId;
        private String runDate;
        private String startedAt;
        private String completedAt;
        private String updatedAt;
        private String triggerMode;
        private String triggerLabel;
        private String status;
        private String statusLabel;
        private Integer plannedProbeCount;
        private Integer completedProbeCount;
        private Integer failedProbeCount;
        private Integer coverageTopicCount;
        private Integer evidenceCount;
        private String schedulingReason;
        private BrandSpaceRunPlanTraceView planTrace;
    }
}
