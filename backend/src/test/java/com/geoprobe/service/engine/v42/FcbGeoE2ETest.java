package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.DiagnosisEdition;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.dto.request.CreateDiagnosisRequest;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.dto.response.DiagnosisResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.service.DiagnosisService;
import com.geoprobe.service.shared.BrandAliasService;
import com.geoprobe.service.shared.CreativeContextService;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class FcbGeoE2ETest {

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private DiagnosisMapper diagnosisMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreativeContextService creativeContextService;

    @MockBean
    private BrandAliasService brandAliasService;

    @MockBean
    private com.geoprobe.ai.v42.CssmHiStateSpaceAiService cssmHiStateSpaceAiService;

    @MockBean
    private com.geoprobe.ai.v42.ProbeRenderingAiService probeRenderingAiService;

    @MockBean
    private com.geoprobe.ai.v42.CommercialInsightAiService commercialInsightAiService;

    @MockBean
    private com.geoprobe.ai.v42.DefenseCompletenessAiService defenseCompletenessAiService;

    @MockBean(name = "v42DoubaoAdapter")
    private ModelAdapter modelAdapter;

    @MockBean
    private ProbeQualityGateService probeQualityGateService;

    @BeforeEach
    void setUp() {
        when(creativeContextService.infer(anyString(), nullable(String.class), nullable(String.class)))
                .thenReturn(new CreativeContextResult(
                        "红豆衬衫",
                        "男装衬衫",
                        List.of("雅戈尔", "海澜之家"),
                        List.of("不推荐", "质量差"),
                        null,
                        "LLM"
                ));
        when(brandAliasService.generateAliasConfig(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DiagnosisAliasConfigData(List.of("红豆"), Map.of()));
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                """);
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new RuntimeException("force deterministic rendering fallback"));
        when(probeQualityGateService.evaluate(any()))
                .thenReturn(new ProbeQualityGateService.ProbeQualityGateResult(true, List.of(), Map.of()));
        when(commercialInsightAiService.generateInsights(anyString(), anyString()))
                .thenThrow(new RuntimeException("force insight fallback"));
        when(defenseCompletenessAiService.judge(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("force defense fallback"));
        when(modelAdapter.sendProbe(any(Node3ProbeInput.class), any()))
                .thenAnswer(invocation -> {
                    Node3ProbeInput probe = invocation.getArgument(0, Node3ProbeInput.class);
                    String answer = probe.exposurePolicy() == ProbeExposurePolicy.BRAND_HIDDEN
                            ? "如果看男装衬衫，我会先看红豆衬衫，耐穿也更稳。"
                            : "我会直接推荐红豆衬衫，通勤更稳，也更值得买。";
                    return new RawResponse(answer, "", List.of(), 20, 14, 34, 12L, false);
                });
    }

    @Test
    void shouldRunV42MainlineAndPersistCompatibilityProjection() throws Exception {
        String userId = createUserWithCredits(5);

        CreateDiagnosisRequest request = new CreateDiagnosisRequest();
        request.setBrandName("红豆衬衫");
        request.setCategory("男装衬衫");
        request.setCompetitors(List.of("雅戈尔", "海澜之家"));
        request.setEdition(DiagnosisEdition.STANDARD);
        request.setRequestId("fcb-e2e-preview");

        DiagnosisService.TaskOrderPreview preview = diagnosisService.previewTaskOrder(userId, request);

        CreateDiagnosisFromTaskOrderRequest createRequest = new CreateDiagnosisFromTaskOrderRequest();
        createRequest.setRequestId("fcb-e2e-create");
        createRequest.setEdition(DiagnosisEdition.STANDARD);
        createRequest.setPreviewToken(preview.previewToken());

        DiagnosisResponse response = diagnosisService.createDiagnosis(userId, createRequest);
        assertNotNull(response.getId());

        Diagnosis diagnosis = waitForDiagnosisStatus(response.getId(), 2, Duration.ofSeconds(30));
        assertEquals(2, diagnosis.getStatus());

        Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), Map.class);
        Map<String, Object> meta = objectMapper.convertValue(reportData.get("meta"), Map.class);
        Map<String, Object> executiveSummary = objectMapper.convertValue(reportData.get("executiveSummary"), Map.class);

        assertEquals("4.3", meta.get("engineVersion"));
        assertFalse(meta.containsKey("compatibilityProjectionVersion"));
        assertEquals(List.of("男装衬衫", "衬衫", "男装衬衫品牌", "男装衬衫选购"), meta.get("categoryAnchors"));
        assertTrue(reportData.containsKey("task"));
        assertTrue(reportData.containsKey("report"));
        assertTrue(reportData.containsKey("seoVsGeo"));
        assertTrue(reportData.containsKey("heroSummary"));
        assertTrue(reportData.containsKey("competitorRadar"));
        assertTrue(executiveSummary.containsKey("kpis"));

        DiagnosisProgressResponse progress = diagnosisService.getDiagnosisProgress(userId, diagnosis.getId());
        assertEquals(11, progress.getProgress().getStepsTotal());
        assertEquals(100.0d, progress.getProgress().getPercent());
        assertEquals(100, progress.getProgress().getSamplingTotal());
    }

    private String createUserWithCredits(int credits) {
        User user = User.builder()
                .username("e2e_" + System.nanoTime())
                .password("123456")
                .displayName("E2E")
                .companyName("Geo")
                .creditsRemaining(credits)
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    private Diagnosis waitForDiagnosisStatus(String diagnosisId, int targetStatus, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Diagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
            if (diagnosis != null && diagnosis.getStatus() != null && diagnosis.getStatus() == targetStatus) {
                return diagnosis;
            }
            Thread.sleep(100L);
        }
        Diagnosis latest = diagnosisMapper.selectById(diagnosisId);
        throw new AssertionError("诊断未在预期时间内完成，当前状态=" + (latest == null ? "null" : latest.getStatus()));
    }
}
