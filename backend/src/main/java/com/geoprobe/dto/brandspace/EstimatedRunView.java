package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimatedRunView {

    private Integer targetProbeCount;
    private Integer estimatedDurationSeconds;
    private Boolean realtimeSearchEnabled;
    private BrandSpaceScheduleView schedule;
}
