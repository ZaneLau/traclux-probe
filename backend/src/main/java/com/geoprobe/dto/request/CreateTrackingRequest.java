package com.geoprobe.dto.request;

import com.geoprobe.common.DiagnosisEdition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTrackingRequest {

    @NotBlank(message = "品牌名不能为空")
    @Size(max = 200, message = "品牌名长度不能超过200")
    private String brandName;

    private DiagnosisEdition edition = DiagnosisEdition.STANDARD;

    @NotBlank(message = "interval 不能为空")
    private String interval;
}
