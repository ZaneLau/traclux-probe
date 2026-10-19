package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunView {

    private String runId;
    private String brandSpaceId;
    private String brandName;
    private String status;
    private Double progressPercent;
    private String currentStep;
    private String currentStepLabel;
    private String startedAt;
    private String completedAt;
    private Long latestEventSeq;
}
