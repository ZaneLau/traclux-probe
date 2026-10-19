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
public class ClusterDistanceDetail {
    private String clusterId;
    private String clusterName;
    private Double weight;
    private Integer sampleCount;
    private Boolean insufficientData;
    private List<BrandDistance> brands;
}
