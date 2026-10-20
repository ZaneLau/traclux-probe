package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceArchiveView {

    private String brandSpaceId;
    private String brandName;
    private String lifecycleStatus;
    private String archivedAt;
    private String retentionUntil;
    private String internalRestoreUntil;
    private String purgedAt;
    private Boolean canUserRestore;
    private Boolean canInternalRestore;
}
