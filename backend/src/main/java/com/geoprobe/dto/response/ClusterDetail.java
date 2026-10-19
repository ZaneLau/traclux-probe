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
public class ClusterDetail {
    private String clusterId;
    private String clusterName;
    private String description;
    private String eeatDimension;
    private String involvementLevel;
    private Double weight;
    private Double mentionRate;
    private Double recommendRate;
    private Double top3Rate;
    private Integer sampleCount;
    private Boolean blindSpot;
    private List<String> samplePrompts;
}
