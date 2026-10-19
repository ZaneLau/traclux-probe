package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceProbePlanExecuteOnceResponse {

    private String brandSpaceId;
    private String runId;
    private String requestId;
    private Integer targetProbeCount;
    private Integer estimatedCredits;
    private Integer deductedCredits;
    private Integer creditsRemainingAfterRun;
    private Boolean skipped;
    private String skippedReason;
    private BrandSpaceProbePlanView probePlan;
}
