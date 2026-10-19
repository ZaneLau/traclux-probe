package com.geoprobe.dto.request;

import com.geoprobe.common.DiagnosisEdition;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CreateDiagnosisFromTaskOrderRequest {

    @Size(max = 16, message = "engineVersion 长度不能超过16")
    private String engineVersion;

    @Size(max = 64, message = "requestId 长度不能超过64")
    private String requestId;

    @Size(max = 64, message = "previewToken 长度不能超过64")
    private String previewToken;

    private Map<String, Object> frontDeskTaskOrder;

    private DiagnosisEdition edition = DiagnosisEdition.STANDARD;

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    @JsonAnySetter
    void captureUnsupportedField(String key, Object value) {
        if (key != null && value != null) {
            unsupportedFields.put(key, value);
        }
    }
}
