package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSummary {
    private Double mentionRate;
    private Double recommendRate;
    private Double top3Rate;
    private Integer sampleCount;
}
