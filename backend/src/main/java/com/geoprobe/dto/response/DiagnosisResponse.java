package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResponse {

    private String id;
    private Integer status;
    private String brandName;
    private String category;
    private List<String> competitors;
    private String brandWebsite;
    private String userPrompt;
    private Integer isTrial;
    private LocalDateTime createdAt;
}
