package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandPerceptionData {
    private Integer totalMentions;
    private Double positivePercent;
    private Double negativePercent;
    private Double neutralPercent;
    private List<TagFrequency> topTags;
    private List<String> topPros;
    private List<String> topCons;
    private String oneLineSummary;
}
