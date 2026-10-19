package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EeatBreakdownData {
    private Double parameterScore;
    private Double experienceScore;
    private Double trustScore;
    private String weakestDimension;
    private String interpretation;
}
