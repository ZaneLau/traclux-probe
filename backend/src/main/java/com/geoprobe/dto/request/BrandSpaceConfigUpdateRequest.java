package com.geoprobe.dto.request;

import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BrandSpaceConfigUpdateRequest {

    @Valid
    @NotNull(message = "topics 不能为空")
    private List<BrandSpaceTopicPayload> topics;

    @Valid
    @NotNull(message = "prompts 不能为空")
    private List<BrandSpacePromptPayload> prompts;

    @Min(value = 1, message = "targetProbeCount 必须大于 0")
    @Max(value = 100, message = "targetProbeCount 不能超过 100")
    private Integer targetProbeCount;

    @Size(max = 80, message = "targetModelProduct 长度不能超过80")
    private String targetModelProduct;

    private Boolean autoRunEnabled;

    private String regionCode;
    private String locale;
    private String language;
}
