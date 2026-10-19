package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisProgressResponse {

    private String id;
    private String brandName;
    private Integer status;
    private Progress progress;
    private LocalDateTime startedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private String currentStep;
        private String currentStepLabel;
        private Integer stepsCompleted;
        private Integer stepsTotal;
        private Integer samplingCompleted;
        private Integer samplingTotal;
        private Integer samplingExpectedTotal;
        private Integer samplingGeneratedTotal;
        private Integer samplingMissingTotal;
        private Boolean samplingVariantShortfall;
        private Double percent;
    }
}
