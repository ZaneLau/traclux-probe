package com.geoprobe.dto.workbench;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchBrandIdentityView {

    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private String category;
    private String officialWebsite;
    private String primaryDomain;
}
