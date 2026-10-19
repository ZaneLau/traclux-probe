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
public class DiagnosisEditionPreviewResponse {

    private List<EditionPreview> editions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditionPreview {
        private String edition;
        private int tokenCost;
        private int sampleTarget;
        private int corePromptCount;
        private int variationPerPrompt;
        private List<ClusterPreview> clusterPreview;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterPreview {
        private String clusterId;
        private String clusterName;
        private int corePromptCount;
        private String involvementLevel;
        private String eeatDimension;
    }
}
