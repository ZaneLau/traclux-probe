package com.geoprobe.dto.request;

import com.geoprobe.common.DiagnosisEdition;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateDiagnosisRequest {

    @Size(max = 16, message = "engineVersion 长度不能超过16")
    private String engineVersion;

    @Size(max = 200, message = "品牌名长度不能超过200")
    private String brandName;

    @Size(max = 200, message = "品类长度不能超过200")
    private String category;

    @Size(max = 500, message = "官网链接长度不能超过500")
    private String brandWebsite;

    @Size(max = 1000, message = "补充提示词长度不能超过1000")
    private String userPrompt;

    @Size(max = 64, message = "contextId 长度不能超过64")
    private String contextId;

    @Size(max = 64, message = "requestId 长度不能超过64")
    private String requestId;

    private List<String> competitors;
    private List<String> categoryAnchors;
    private List<String> brandAliases;
    private Map<String, List<String>> competitorAliases;
    private Map<String, Object> frontDeskTaskOrder;
    private Map<String, Object> taskOrderHints;

    private DiagnosisEdition edition = DiagnosisEdition.STANDARD;
}
