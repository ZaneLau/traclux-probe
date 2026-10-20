package com.geoprobe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BrandSpaceBasicProfileUpdateRequest {

    @NotBlank(message = "brandName 不能为空")
    @Size(max = 200, message = "brandName 长度不能超过200")
    private String brandName;

    @NotBlank(message = "category 不能为空")
    @Size(max = 200, message = "category 长度不能超过200")
    private String category;

    @Size(max = 500, message = "officialWebsite 长度不能超过500")
    private String officialWebsite;

    @Size(max = 10, message = "competitors 数量不能超过10")
    private List<String> competitors;

    @Size(max = 1000, message = "supplementalBrief 长度不能超过1000")
    private String supplementalBrief;
}
