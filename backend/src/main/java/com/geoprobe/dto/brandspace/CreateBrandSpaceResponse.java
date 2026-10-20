package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBrandSpaceResponse {

    private String brandSpaceId;
    private String brandId;
    private String brandScopeId;
    private String initialRunId;
    private String initialRunStatus;
    private String initialRunBlockCode;
    private String initialRunBlockMessage;
    private String status;
    private String nextView;
}
