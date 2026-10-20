package com.geoprobe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandSpaceTrackingModeUpdateRequest {

    @NotBlank(message = "trackingMode 不能为空")
    @Size(max = 32, message = "trackingMode 长度不能超过32")
    private String trackingMode;
}
