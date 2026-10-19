package com.geoprobe.dto.request;

import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BrandSpaceCreateRequest {

    @NotBlank(message = "previewToken 不能为空")
    @Size(max = 64, message = "previewToken 长度不能超过64")
    private String previewToken;

    @Valid
    @NotNull(message = "brandSpaceDraft 不能为空")
    private BrandSpaceDraftPayload brandSpaceDraft;

    @Valid
    private List<BrandSpaceTopicPayload> topics;

    @Valid
    private List<BrandSpacePromptPayload> prompts;

    @NotBlank(message = "idempotencyKey 不能为空")
    @Size(max = 64, message = "idempotencyKey 长度不能超过64")
    private String idempotencyKey;

    @Min(value = 1, message = "targetProbeCount 必须大于 0")
    @Max(value = 100, message = "targetProbeCount 不能超过 100")
    private Integer targetProbeCount;

    @Size(max = 80, message = "targetModelProduct 长度不能超过80")
    private String targetModelProduct;

    @Size(max = 32, message = "executeModeCode 长度不能超过32")
    private String executeModeCode;
}
