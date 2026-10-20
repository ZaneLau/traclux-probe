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
public class BrandSpaceScheduleView {

    private String triggerMode;
    private Integer targetProbeCount;
    private String coverageStrategy;
    private String schedulingReason;
    private Integer selectedTopicCount;
    private List<String> selectedTopicNames;
    private Integer deferredTopicCount;
    private List<String> deferredTopicNames;
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
}
