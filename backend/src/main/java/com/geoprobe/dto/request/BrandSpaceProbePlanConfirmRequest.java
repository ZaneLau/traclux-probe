package com.geoprobe.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandSpaceProbePlanConfirmRequest {

    @Min(1)
    @Max(100)
    private Integer plannedProbeCount;

    @Size(max = 80)
    private String targetModelProduct;

    private String reason;

    private String executeModeCode;
}
