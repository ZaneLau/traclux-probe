package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.service.engine.v42.ModelAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceRecognitionServiceTest {

    @Mock
    private ModelAdapter doubaoAdapter;

    @Mock
    private DiagnosisInferenceService diagnosisInferenceService;

    @Test
    void shouldUseDoubaoRealtimeRecognitionWhenJsonIsValid() {
        BrandSpaceRecognitionService service = new BrandSpaceRecognitionService(
                doubaoAdapter,
                diagnosisInferenceService,
                new ObjectMapper()
        );
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("比亚迪")
                .category("新能源汽车")
                .build();

        when(doubaoAdapter.sendProbe(any(), any()))
                .thenReturn(new RawResponse(
                        """
                        {"suggestedCategory":"新能源汽车","officialWebsite":"https://www.byd.com","competitors":["特斯拉","蔚来","理想"],"brandSummary":"比亚迪是中国头部新能源汽车品牌，核心认知集中在电动车、混动和电池能力。"}
                        """,
                        "",
                        List.of("https://www.byd.com"),
                        100,
                        50,
                        150,
                        800,
                        false,
                        true,
                        true,
                        false,
                        List.of(),
                        1,
                        List.of("比亚迪 官网")
                ));

        BrandSpaceRecognitionView recognition = service.recognize(draft);

        assertEquals("新能源汽车", recognition.getSuggestedCategory());
        assertEquals("https://www.byd.com", recognition.getOfficialWebsite());
        assertEquals(List.of("特斯拉", "蔚来", "理想"), recognition.getCompetitors());
        assertTrue(recognition.getBrandSummary().contains("比亚迪"));
        assertEquals("doubao_web_search", recognition.getSource());
    }

    @Test
    void shouldFallbackToCreativeContextWhenDoubaoRecognitionFails() {
        BrandSpaceRecognitionService service = new BrandSpaceRecognitionService(
                doubaoAdapter,
                diagnosisInferenceService,
                new ObjectMapper()
        );
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("卡贝")
                .category("家居五金")
                .build();

        when(doubaoAdapter.sendProbe(any(), any()))
                .thenThrow(new IllegalStateException("doubao timeout"));
        when(diagnosisInferenceService.inferForDrafting(eq("卡贝"), eq(null), any()))
                .thenReturn(new CreativeContextResult(
                        "卡贝",
                        "家居五金",
                        List.of("九牧", "箭牌"),
                        List.of("安装门槛高"),
                        "https://www.cobbe.com",
                        "LLM"
                ));

        BrandSpaceRecognitionView recognition = service.recognize(draft);

        assertEquals("家居五金", recognition.getSuggestedCategory());
        assertEquals("https://www.cobbe.com", recognition.getOfficialWebsite());
        assertEquals(List.of("九牧", "箭牌"), recognition.getCompetitors());
        assertTrue(recognition.getBrandSummary().contains("卡贝"));
        assertEquals("creative_context_llm", recognition.getSource());
    }

    @Test
    void shouldFallbackWhenDoubaoRecognitionExceedsTimeoutBudget() {
        BrandSpaceRecognitionService service = new BrandSpaceRecognitionService(
                doubaoAdapter,
                diagnosisInferenceService,
                new ObjectMapper()
        );
        service.setRealtimeTimeoutSeconds(1L);
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("阿维塔")
                .category("新能源汽车")
                .build();
        when(doubaoAdapter.sendProbe(any(), any()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(1_500L);
                    } catch (InterruptedException ex) {
                        throw ex;
                    }
                    return new RawResponse(
                            """
                            {"suggestedCategory":"新能源汽车","officialWebsite":"https://www.avatarmobility.com","competitors":["特斯拉","蔚来"],"brandSummary":"阿维塔聚焦高端智能电动汽车。"}
                            """,
                            "",
                            List.of("https://www.avatarmobility.com"),
                            100,
                            50,
                            150,
                            800,
                            false,
                            true,
                            true,
                            false,
                            List.of(),
                            1,
                            List.of("阿维塔 官网")
                    );
                });
        when(diagnosisInferenceService.inferForDrafting(eq("阿维塔"), eq(null), any()))
                .thenReturn(new CreativeContextResult(
                        "阿维塔",
                        "新能源汽车",
                        List.of("特斯拉", "蔚来"),
                        List.of(),
                        "https://www.avatarmobility.com",
                        "LLM"
                ));

        BrandSpaceRecognitionView recognition = service.recognize(draft);

        assertEquals("新能源汽车", recognition.getSuggestedCategory());
        assertEquals("https://www.avatarmobility.com", recognition.getOfficialWebsite());
        assertEquals(List.of("特斯拉", "蔚来"), recognition.getCompetitors());
        assertEquals("creative_context_llm", recognition.getSource());
        verify(diagnosisInferenceService).inferForDrafting(eq("阿维塔"), eq(null), any());
    }

    @Test
    void shouldFallbackToDeterministicWhenRecognitionDriftsToShorterNearbyBrand() {
        BrandSpaceRecognitionService service = new BrandSpaceRecognitionService(
                doubaoAdapter,
                diagnosisInferenceService,
                new ObjectMapper()
        );
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("美厨物语")
                .category("厨房收纳")
                .build();

        when(doubaoAdapter.sendProbe(any(), any()))
                .thenReturn(new RawResponse(
                        """
                        {"suggestedCategory":"厨房收纳","officialWebsite":"https://www.meichu.com","competitors":["YWEEL","茶花"],"brandSummary":"美厨是厨房收纳品牌，主打家庭厨房整理场景。"}
                        """,
                        "",
                        List.of("https://www.meichu.com"),
                        100,
                        50,
                        150,
                        800,
                        false,
                        true,
                        true,
                        false,
                        List.of(),
                        1,
                        List.of("美厨 官网")
                ));
        when(diagnosisInferenceService.inferForDrafting(eq("美厨物语"), eq(null), any()))
                .thenReturn(new CreativeContextResult(
                        "美厨",
                        "厨房收纳",
                        List.of("YWEEL", "茶花"),
                        List.of(),
                        "https://www.meichu.com",
                        "LLM"
                ));

        BrandSpaceRecognitionView recognition = service.recognize(draft);

        assertEquals("厨房收纳", recognition.getSuggestedCategory());
        assertNull(recognition.getOfficialWebsite());
        assertEquals(List.of(), recognition.getCompetitors());
        assertTrue(recognition.getBrandSummary().contains("美厨物语"));
        assertEquals("deterministic_fallback", recognition.getSource());
    }
}
