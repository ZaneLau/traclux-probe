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
public class SemanticDistanceData {
    private List<BrandDistance> brands;
    private List<ClusterDistanceDetail> clusters;
    private Integer includedClusterCount;
    private Integer insufficientClusterCount;
}
