package com.geoprobe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PreviewDiagnosisEditionsRequest {

    @NotBlank(message = "品牌名不能为空")
    @Size(max = 200, message = "品牌名长度不能超过200")
    private String brandName;

    @Size(max = 500, message = "官网链接长度不能超过500")
    private String brandWebsite;

    @Size(max = 1000, message = "补充提示词长度不能超过1000")
    private String userPrompt;

    @Size(max = 200, message = "品类长度不能超过200")
    private String category;

    @Size(max = 10, message = "竞品数量不能超过10")
    private List<String> competitors;
}
