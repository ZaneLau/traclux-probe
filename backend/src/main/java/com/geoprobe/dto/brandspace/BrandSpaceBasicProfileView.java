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
public class BrandSpaceBasicProfileView {

    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private String category;
    private String officialWebsite;
    private String primaryDomain;
    private List<String> competitors;
    private String supplementalBrief;
}
