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
public class BrandSpaceTopicPayload {

    @Size(max = 64, message = "tempId 长度不能超过64")
    private String tempId;

    @NotBlank(message = "topic name 不能为空")
    @Size(max = 120, message = "topic name 长度不能超过120")
    private String name;

    @Size(max = 500, message = "topic description 长度不能超过500")
    private String description;

    private Boolean enabled;

    @Size(max = 32, message = "source 长度不能超过32")
    private String source;
}
