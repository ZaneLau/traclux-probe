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
public class WorkbenchAnalysisContextView {

    private String timePreset;
    private String dateFrom;
    private String dateTo;
    private String windowKey;
    private String comparison;
    private String frequency;
    private List<String> platforms;
    private List<String> availablePlatforms;
    private Map<String, List<String>> channelFilters;
    private List<String> supportedDimensions;
    private String samplePolicy;
    private String truthSource;
    private String sourceWindowKey;
    private List<String> notes;
}
