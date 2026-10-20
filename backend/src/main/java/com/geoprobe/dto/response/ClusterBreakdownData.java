package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterBreakdownData {
    private Integer totalClusters;
    private Integer coveredClusters;
    private Double coverageRate;
    private List<ClusterDetail> clusters;
}
