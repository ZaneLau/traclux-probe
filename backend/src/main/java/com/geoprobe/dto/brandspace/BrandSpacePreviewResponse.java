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
public class BrandSpacePreviewResponse {

    private String previewToken;
    private BrandSpaceDraftPayload brandSpaceDraft;
    private BrandSpaceRecognitionView recognition;
    private List<BrandSpaceTopicPayload> topics;
    private List<BrandSpacePromptPayload> prompts;
    private EstimatedRunView estimatedRun;
    private ServiceAccessView serviceAccess;
}
