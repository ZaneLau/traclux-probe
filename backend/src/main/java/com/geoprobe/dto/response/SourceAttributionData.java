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
public class SourceAttributionData {
    private List<SourceCategory> categories;
    private String dominantSource;
    private Double dominantPercent;
}
