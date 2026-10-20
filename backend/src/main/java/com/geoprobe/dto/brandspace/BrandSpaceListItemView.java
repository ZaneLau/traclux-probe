package com.geoprobe.dto.brandspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandSpaceListItemView {

    private String brandSpaceId;
    private String brandId;
    private String brandScopeId;
    private String brandName;
    private String category;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String officialWebsite;
    private String primaryDomain;
    private String status;
    private String latestRunId;
    private String latestReportId;
    private String lastGeneratedAt;
    private Double visibilityScore;
    private Double recommendRate;
    private Double mentionRate;
    private Double top3Rate;
    private String serviceAccessStatus;
    private String trackingStatus;
    private String healthStatus;
    private Boolean configReady;
    private Integer activePromptCount;
    private String probePlanStatus;
    private Integer probePlanVersionNo;
    private Integer activeRecurringProbeCount;
    private Integer estimatedCreditsPerCycle;
    private String lastRunStatus;
    private String lastRunAt;
    private BrandSpaceScheduleView latestSchedule;
}
