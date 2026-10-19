package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceSystemRefreshView {

    private BrandSpaceBasicProfileView basicProfile;
    private BrandSpaceConfigView config;
    private BrandSpaceScheduleView latestSchedule;
}
