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
public class BrandSpaceRunPlanTraceView {

    private String planVersionId;
    private Integer planVersionNo;
    private String planRecordId;
    private Integer planRecordNo;
    private String executeModeCode;
    private Integer estimatedCredits;
    private Integer chargedCredits;
    private Integer creditDelta;
    private String creditReconciliationStatus;
    private String estimatedCreditsSourceCode;
    private String chargedCreditsSourceCode;
    private Integer coveredTopicCount;
    private Integer plannedProbeCount;
}
