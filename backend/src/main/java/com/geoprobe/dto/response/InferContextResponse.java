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
public class InferContextResponse {

    private String contextId;
    private String brandName;
    private String inferredCategory;
    private List<String> inferredCompetitors;
    private List<String> inferredNegativeTerms;
    private String source;
    private LocalDateTime expiresAt;
}
