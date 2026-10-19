package com.geoprobe.service;

import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.IntentCluster;
import com.geoprobe.dto.engine.IntentClusterResult;
import com.geoprobe.dto.request.PreviewDiagnosisEditionsRequest;
import com.geoprobe.dto.response.DiagnosisEditionPreviewResponse;
import com.geoprobe.service.shared.FcbClassification;
import com.geoprobe.service.shared.IntentClusterService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosisEditionPreviewUseCaseServiceTest {

    @Test
    void shouldSkipInferenceWhenCategoryAndCompetitorsAreProvided() {
        IntentClusterService intentClusterService = mock(IntentClusterService.class);
        DiagnosisInferenceService inferenceService = mock(DiagnosisInferenceService.class);
        DiagnosisEditionPreviewUseCaseService service =
                new DiagnosisEditionPreviewUseCaseService(intentClusterService, inferenceService);
        mockClusterPipeline(intentClusterService);

        PreviewDiagnosisEditionsRequest request = new PreviewDiagnosisEditionsRequest();
        request.setBrandName("佳帮手");
        request.setCategory("厨房置物架");
        request.setCompetitors(List.of("美之高"));

        DiagnosisEditionPreviewResponse response = service.previewEditions("user-1", request);

        assertEquals(3, response.getEditions().size());
        verify(inferenceService, never()).inferAndValidate(any(), any(), any(), any());
    }

    @Test
    void shouldUseInferenceWhenCategoryOrCompetitorsMissing() {
        IntentClusterService intentClusterService = mock(IntentClusterService.class);
        DiagnosisInferenceService inferenceService = mock(DiagnosisInferenceService.class);
        DiagnosisEditionPreviewUseCaseService service =
                new DiagnosisEditionPreviewUseCaseService(intentClusterService, inferenceService);
        mockClusterPipeline(intentClusterService);
        when(inferenceService.inferAndValidate(eq("佳帮手"), any(), any(), eq("preview_editions")))
                .thenReturn(new CreativeContextResult(
                        "佳帮手",
                        "厨房置物架",
                        List.of("美之高"),
                        List.of("二手"),
                        null,
                        "LLM"
                ));

        PreviewDiagnosisEditionsRequest request = new PreviewDiagnosisEditionsRequest();
        request.setBrandName("佳帮手");

        DiagnosisEditionPreviewResponse response = service.previewEditions("user-1", request);

        assertEquals(3, response.getEditions().size());
        verify(inferenceService, times(1)).inferAndValidate(eq("佳帮手"), any(), any(), eq("preview_editions"));
    }

    private static void mockClusterPipeline(IntentClusterService intentClusterService) {
        FcbClassification classification = FcbClassification.builder()
                .category("厨房置物架")
                .involvement("HIGH")
                .parameterValid(true)
                .experienceValid(true)
                .trustValid(true)
                .competitors(List.of("美之高"))
                .build();
        when(intentClusterService.classify(any(), any())).thenReturn(classification);
        when(intentClusterService.generateFromClassification(any(), any(), any()))
                .thenReturn(new IntentClusterResult(List.of(
                        new IntentCluster(
                                "HIGH_TRUST",
                                "信任型",
                                "trust",
                                List.of("怎么选"),
                                "TRUST",
                                "HIGH",
                                0.5d,
                                4
                        )
                )));
    }
}
