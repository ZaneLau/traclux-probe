package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceRecognitionView {

    private String suggestedCategory;
    private String officialWebsite;
    private List<String> directCompetitors;
    private List<String> indirectCompetitors;
    private List<String> competitors;
    private String brandSummary;
    private String source;
    private String fallbackReason;
}
