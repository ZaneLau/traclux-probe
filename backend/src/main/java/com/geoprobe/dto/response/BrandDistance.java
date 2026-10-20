package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandDistance {
    private String brandName;
    private Double distance;
    private Double recommendRate;
    private Boolean isTargetBrand;
}
