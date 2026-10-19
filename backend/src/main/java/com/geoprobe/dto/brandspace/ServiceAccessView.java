package com.geoprobe.dto.brandspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceAccessView {

    private String status;
    private String planName;
    private String expiresAt;
    private Limits limits;
    private String reason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Limits {
        private Integer brandSpaceLimit;
        private Integer monthlyRunLimit;
        private Integer promptLimitPerBrandSpace;
        private Integer enabledModelCount;
        private Integer creditsPerProbe;
    }
}
