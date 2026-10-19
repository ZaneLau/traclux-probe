package com.geoprobe.dto.workbench;

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
public class WorkbenchDataAvailabilityView {

    private String status;
    private String message;
    private String reasonCode;
    private Boolean hasCompletedReport;
    private Boolean hasRunningRun;
    private String latestRunId;
    private String latestReportId;
    private List<String> missingFields;
    private Map<String, List<String>> missingFieldDetails;
}
