package com.geoprobe.dto.brandspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpacePromptPayload {

    @Size(max = 64, message = "tempId 长度不能超过64")
    private String tempId;

    @Size(max = 64, message = "topicTempId 长度不能超过64")
    private String topicTempId;

    @NotBlank(message = "prompt text 不能为空")
    @Size(max = 1000, message = "prompt text 长度不能超过1000")
    private String text;

    private Boolean enabled;

    @Size(max = 32, message = "source 长度不能超过32")
    private String source;
}
