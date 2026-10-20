package com.geoprobe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.database.v1.enabled=true",
        "app.database.v1.read-preferred=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiagnosisTaskMapper diagnosisTaskMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private BrandScopeMapper brandScopeMapper;

    @Autowired
    private BrandSpaceTopicMapper brandSpaceTopicMapper;

    @Autowired
    private BrandSpacePromptMapper brandSpacePromptMapper;

    @Autowired
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;

    @Autowired
    private DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;

    @Autowired
    private ProbeEvidenceRecordMapper probeEvidenceRecordMapper;

    @Autowired
    private ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;

    @Autowired
    private ProbeDispatchAttemptMapper probeDispatchAttemptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private V42DiagnosisExecutionService v42DiagnosisExecutionService;
    @MockBean
    private com.geoprobe.service.BrandSpaceRecognitionService brandSpaceRecognitionService;

    @Test
    void shouldReturnGeneratingAndReadyWorkbenchProjections() throws Exception {
        AuthContext auth = register();
        mockRecognition("家居五金", "https://brand.example.com", List.of("九牧", "箭牌"), "这是一个家居五金品牌，核心关注安装友好度与口碑信任线索。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "工作台联调品牌");
        previewPayload.put("category", "家居五金");
        previewPayload.put("officialWebsite", "https://brand.example.com");
        previewPayload.put("competitors", List.of("九牧", "箭牌"));
        previewPayload.put("supplementalBrief", "重点关注出租屋、安装友好度");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "wb-preview-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("prompts", objectMapper.convertValue(previewData.path("prompts"), List.class));
        createPayload.put("idempotencyKey", "wb-create-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String brandSpaceId = createData.path("brandSpaceId").asText();
        String runId = createData.path("initialRunId").asText();

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandSpaceStatus").value("generating"))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("generating"));

        seedCompletedReport(runId, brandSpaceId);

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("ready"))
                .andExpect(jsonPath("$.data.cards.length()").value(6))
                .andExpect(jsonPath("$.data.cards[1].primary").value("+3.42"))
                .andExpect(jsonPath("$.data.cards[1].secondary").value("推荐率 +4.00% | 提及率 +6.00%"))
                .andExpect(jsonPath("$.data.cards[3].primary").value("#2"))
                .andExpect(jsonPath("$.data.cards[3].secondary").value("推荐率落后 九牧 7.50"))
                .andExpect(jsonPath("$.data.competitorRows[0].brandName").value("九牧"))
                .andExpect(jsonPath("$.data.competitorRows[0].rank").value(1))
                .andExpect(jsonPath("$.data.competitorRows[0].displayScore").value(42.5))
                .andExpect(jsonPath("$.data.competitorRows[0].rankingMetric").value("recommendRate"))
                .andExpect(jsonPath("$.data.competitorRows[1].brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.competitorRows[1].rank").value(2))
                .andExpect(jsonPath("$.data.competitorRows[1].displayScore").value(35.0))
                .andExpect(jsonPath("$.data.competitorRows[1].rankingMetric").value("recommendRate"))
                .andExpect(jsonPath("$.data.currentPerformance.visibilityScore").value(28.29))
                .andExpect(jsonPath("$.data.currentPerformance.competitorVisibilityAverage").value(0.0))
                .andExpect(jsonPath("$.data.currentPerformance.recommendRate").value(35.0))
                .andExpect(jsonPath("$.data.currentPerformance.top3Rate").value(2.57))
                .andExpect(jsonPath("$.data.latestInsight.headline").value("品牌在竞争问题里已经有可见度，但关键场景和自发心智仍然偏弱"))
                .andExpect(jsonPath("$.data.latestInsight.engineVersion").value("5.0"))
                .andExpect(jsonPath("$.data.latestInsight.items.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.latestInsight.items[0].evidenceSummary").value("对抗场景样本还不够，暂时看不清真实防守能力。"))
                .andExpect(jsonPath("$.data.latestInsight.items[0].nextStepLabel").value("去问题分析"))
                .andExpect(jsonPath("$.data.latestInsight.items[0].nextStepText").value("看竞品决策和风险异议"))
                .andExpect(jsonPath("$.data.latestInsight.items[0].nextStepRouteKey").value("probe_analysis"))
                .andExpect(jsonPath("$.data.priorities[0].detailRouteKey").value("probe_analysis"));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.items[0].visibilityScore").value(30.0))
                .andExpect(jsonPath("$.data.items[0].mentionRate").value(42.0))
                .andExpect(jsonPath("$.data.items[0].recommendRate").value(37.0))
                .andExpect(jsonPath("$.data.items[0].top3Rate").value(2.0));

        mockMvc.perform(get("/api/v1/workbench/brand-performance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandId").value(brandScopeMapper.selectById(brandSpaceId).getBrandId()))
                .andExpect(jsonPath("$.data.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.category").value("家居五金"))
                .andExpect(jsonPath("$.data.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.identity.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.identity.category").value("家居五金"))
                .andExpect(jsonPath("$.data.identity.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.identity.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("ready"))
                .andExpect(jsonPath("$.data.summary.visibilityScore").value(28.29))
                .andExpect(jsonPath("$.data.summary.recommendRate").value(35.0))
                .andExpect(jsonPath("$.data.summary.shareOfVoice").value(39.0))
                .andExpect(jsonPath("$.data.summary.shareOfVoiceDelta").value(3.0))
                .andExpect(jsonPath("$.data.summary.averagePosition").value(1.75))
                .andExpect(jsonPath("$.data.summary.averagePositionDelta").value(-0.25))
                .andExpect(jsonPath("$.data.summary.top3Rate").value(2.57))
                .andExpect(jsonPath("$.data.summary.headline").value("品牌已进入候选池前列，下一步要围绕家居五金选购场景巩固优势。"))
                .andExpect(jsonPath("$.data.summary.topTopicLabels[0]").value("安装友好度"))
                .andExpect(jsonPath("$.data.competitorRows[0].rank").value(1))
                .andExpect(jsonPath("$.data.competitorRows[0].averagePosition").value(1.75))
                .andExpect(jsonPath("$.data.competitorRows[1].shareOfVoice").value(47.5))
                .andExpect(jsonPath("$.data.competitorRows[1].shareOfVoiceDelta").value(1.5))
                .andExpect(jsonPath("$.data.topicSummaries.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.modelPerformance[0].label").value("豆包"));

        mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandId").value(brandScopeMapper.selectById(brandSpaceId).getBrandId()))
                .andExpect(jsonPath("$.data.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.category").value("家居五金"))
                .andExpect(jsonPath("$.data.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.identity.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.identity.category").value("家居五金"))
                .andExpect(jsonPath("$.data.identity.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.identity.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.summary.totalCitations").value(5))
                .andExpect(jsonPath("$.data.summary.ownedShare").value(60.0))
                .andExpect(jsonPath("$.data.summary.thirdPartyShare").value(40.0))
                .andExpect(jsonPath("$.data.domainRows[0].domain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.urlRows[0].url").value("https://brand.example.com/products/shower"))
                .andExpect(jsonPath("$.data.trendPoints.length()").value(2))
                .andExpect(jsonPath("$.data.trendPoints[0].totalCitations").value(2))
                .andExpect(jsonPath("$.data.trendPoints[1].totalCitations").value(3))
                .andExpect(jsonPath("$.data.trendPoints[1].isLatest").value(true))
                .andExpect(jsonPath("$.data.trendPoints[1].topDomains[0].domain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.ownership.slices[0].key").value("owned"))
                .andExpect(jsonPath("$.data.ownership.slices[0].used").value(3))
                .andExpect(jsonPath("$.data.domainDetails[0].domain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.domainDetails[0].ownershipKey").value("owned"))
                .andExpect(jsonPath("$.data.domainDetails[0].urlCount").value(3))
                .andExpect(jsonPath("$.data.domainDetails[0].urls[0].url").value("https://brand.example.com/products/shower"));

        MvcResult problemAnalysisResult = mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.identity.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.identity.category").value("家居五金"))
                .andExpect(jsonPath("$.data.identity.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.identity.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandId").value(brandScopeMapper.selectById(brandSpaceId).getBrandId()))
                .andExpect(jsonPath("$.data.brandName").value("工作台联调品牌"))
                .andExpect(jsonPath("$.data.category").value("家居五金"))
                .andExpect(jsonPath("$.data.officialWebsite").value("https://brand.example.com"))
                .andExpect(jsonPath("$.data.primaryDomain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("raw_only"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("problem_analysis_raw_only"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("topics")))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("prompts")))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("attemptRows")))
                .andExpect(jsonPath("$.data.summary.topicCount").value(0))
                .andExpect(jsonPath("$.data.summary.promptCount").value(0))
                .andExpect(jsonPath("$.data.summary.problemClusterCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.summary.probeCount").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.summary.attemptCount").value(0))
                .andExpect(jsonPath("$.data.summary.linkedProbeCount").value(0))
                .andExpect(jsonPath("$.data.summary.evidenceCount").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.summary.linkedEvidenceCount").value(0))
                .andExpect(jsonPath("$.data.summary.rawAnswerCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.summary.linkedPromptCount").value(0))
                .andExpect(jsonPath("$.data.topicRows.length()").value(0))
                .andExpect(jsonPath("$.data.promptRows.length()").value(0))
                .andExpect(jsonPath("$.data.problemClusters.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.problemClusters[0].problemSpaceBucket").value(not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.data.probeRows[0].hasRawAnswer").value(true))
                .andExpect(jsonPath("$.data.evidenceRows[0].sourceType").value("自有资产"))
                .andReturn();

        JsonNode problemAnalysisData = objectMapper.readTree(problemAnalysisResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode trendPoints = problemAnalysisData.path("rich").path("volume").path("trendPoints");
        assertTrue(trendPoints.size() >= 1);
        JsonNode latestTrendPoint = trendPoints.path(trendPoints.size() - 1);
        assertTrue(latestTrendPoint.path("sliceKey").asText().length() > 0);
        assertTrue(latestTrendPoint.path("isLatest").asBoolean());
        assertTrue(latestTrendPoint.path("snapshot").path("summary").path("demandTotal").asInt() >= 0);
        assertTrue(latestTrendPoint.path("snapshot").path("topTopics").size() >= 1);
        assertTrue(latestTrendPoint.path("snapshot").path("platformRows").isArray());
        assertTrue(problemAnalysisData.path("catalog").path("problemSpaceBuckets").size() >= 1);
        assertTrue(problemAnalysisData.path("probeRows").path(0).path("problemSpaceBucket").asText().length() > 0);
        assertTrue(problemAnalysisData.path("rich").path("volume").path("promptOpportunityRows").path(0).path("problemSpaceBucket").asText().length() > 0);

        mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                .queryParam("brandSpaceId", brandSpaceId)
                .queryParam("problemSpaceBuckets", "non-existent-bucket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary.promptCount").value(0))
                .andExpect(jsonPath("$.data.rich.volume.promptOpportunityRows.length()").value(0));

        BrandScope archivedScope = brandScopeMapper.selectById(brandSpaceId);
        assertNotNull(archivedScope);
        archivedScope.setScopeStatusCode(0);
        archivedScope.setTrackingMode("PAUSED");
        archivedScope.setArchivedAt(LocalDateTime.now());
        archivedScope.setRetentionUntil(LocalDateTime.now().plusDays(30));
        brandScopeMapper.updateById(archivedScope);

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", brandSpaceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("品牌空间已移出当前账号"));
    }

    @Test
    void shouldReturnExplicitOverviewEmptyCopyForSingleTrendAndNoSources() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台空态品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("partial"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("citation_search_not_requested"))
                .andExpect(jsonPath("$.data.dataAvailability.message").value("最近一轮未实际触发联网搜索，当前无可展示引用证据"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields[0]").value("sourceCitations"))
                .andExpect(jsonPath("$.data.cards[1].primary").value("趋势样本不足"))
                .andExpect(jsonPath("$.data.cards[1].secondary").value("等待下一轮对比"))
                .andExpect(jsonPath("$.data.cards[4].primary").value("暂无引用数据"))
                .andExpect(jsonPath("$.data.cards[4].secondary").value("去引用来源查看"))
                .andExpect(jsonPath("$.data.competitorRows[0].displayScore").value(41.0))
                .andExpect(jsonPath("$.data.currentPerformance.top3Rate").value(1.67))
                .andExpect(jsonPath("$.data.currentPerformance.competitorVisibilityAverage").value(0.0))
                .andExpect(jsonPath("$.data.competitorRows[0].rankingMetric").value("recommendRate"));

        mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("partial"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("citation_search_not_requested"))
                .andExpect(jsonPath("$.data.dataAvailability.message").value("最近一轮未实际触发联网搜索，当前无可展示引用证据"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields[0]").value("domains"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields[1]").value("urls"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields[2]").value("categories"));
    }

    @Test
    void shouldExposeCanonicalBrandPerformanceMissingFieldNames() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台品牌表现缺口品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/brand-performance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("trendPoints")))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(not(hasItem("trend"))));
    }

    @Test
    void shouldKeepConfiguredCompetitorsVisibleWhenProjectionRankingIsEmpty() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台竞品占位品牌", "智能门锁");
        seedSingleCompletedReportWithoutCompetitorRanking(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/brand-performance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.competitorRows.length()").value(3))
                .andExpect(jsonPath("$.data.competitorRows[0].isTarget").value(true))
                .andExpect(jsonPath("$.data.competitorRows[1].brandName").value("竞品一"))
                .andExpect(jsonPath("$.data.competitorRows[2].brandName").value("竞品二"));
    }

    @Test
    void shouldExposeProblemAnalysisTruthVacuumReasonWhenEvidenceIsEmpty() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台真空证据品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        seedProblemAnalysisRuntimeWithoutEvidence(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("raw_only"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("problem_analysis_raw_only"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("evidenceRows")))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(hasItem("problemClusters")))
                .andExpect(jsonPath("$.data.summary.rawAnswerCount").value(3))
                .andExpect(jsonPath("$.data.summary.evidenceCount").value(0));
    }

    @Test
    void shouldKeepFilteredWindowEmptyReasonForProblemAnalysis() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台空窗口问题品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        seedProblemAnalysisRuntimeWithoutEvidence(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId())
                        .queryParam("timePreset", "custom")
                        .queryParam("dateFrom", "2024-01-01")
                        .queryParam("dateTo", "2024-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("empty"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("filtered_window_empty"))
                .andExpect(jsonPath("$.data.dataAvailability.message").value("当前筛选窗口暂无可展示数据"))
                .andExpect(jsonPath("$.data.topicRows.length()").value(0))
                .andExpect(jsonPath("$.data.promptRows.length()").value(0));
    }

    @Test
    void shouldFailExplicitlyWhenProblemAnalysisRuntimeTablesAreMissing() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台缺表品牌", "智能门锁");
        seedCompletedReport(created.runId(), created.brandSpaceId());

        jdbcTemplate.execute("ALTER TABLE probe_dispatch_attempts RENAME TO probe_dispatch_attempts_tmp_missing");
        jdbcTemplate.execute("ALTER TABLE diagnosis_probes RENAME TO diagnosis_probes_tmp_missing");
        try {
            mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                            .queryParam("brandSpaceId", created.brandSpaceId()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(9999))
                    .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("问题分析探针记录")));
        } finally {
            jdbcTemplate.execute("ALTER TABLE diagnosis_probes_tmp_missing RENAME TO diagnosis_probes");
            jdbcTemplate.execute("ALTER TABLE probe_dispatch_attempts_tmp_missing RENAME TO probe_dispatch_attempts");
        }
    }

    @Test
    void shouldResolveProblemAnalysisPromptBridgeForActualBrandSpaceRuns() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台真实映射品牌", "智能门锁");
        seedCompletedReport(created.runId(), created.brandSpaceId());
        seedActualProblemAnalysisRuntime(created.runId(), created.brandSpaceId());
        rewriteCurrentProblemAnalysisConfig(created.brandSpaceId());

        MvcResult result = mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("ready"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(not(hasItem("promptCoverage"))))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(not(hasItem("probeTopicMapping"))))
                .andExpect(jsonPath("$.data.catalog.problemSpaceBuckets.length()").value(greaterThanOrEqualTo(1)))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertTrue(data.path("probeRows").size() >= 3);
        assertEquals("智能门锁选购场景", data.path("topicRows").path(0).path("topicName").asText());
        assertEquals("智能门锁怎么选，哪些品牌更值得先加入候选？", data.path("promptRows").path(0).path("promptText").asText());
        for (JsonNode row : data.path("probeRows")) {
            assertFalse(row.path("promptId").asText().isBlank());
            assertFalse(row.path("topicId").asText().isBlank());
            assertFalse(row.path("topicName").asText().isBlank());
            assertFalse(row.path("problemSpaceBucket").asText().isBlank());
        }
        for (JsonNode row : data.path("evidenceRows")) {
            assertFalse(row.path("promptId").asText().isBlank());
            assertFalse(row.path("topicName").asText().isBlank());
            assertFalse(row.path("problemSpaceBucket").asText().isBlank());
        }
    }

    @Test
    void shouldReturnBackendScopedProblemAnalysisRowsForPlatformFilter() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台平台筛选品牌", "智能门锁");
        seedCompletedReport(created.runId(), created.brandSpaceId());
        seedActualProblemAnalysisRuntime(created.runId(), created.brandSpaceId());
        rewriteCurrentProblemAnalysisConfig(created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId())
                        .queryParam("platforms", "doubao-seed-1-6-lite-251015"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.analysisContext.availablePlatforms", hasItem("豆包")))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("ready"))
                .andExpect(jsonPath("$.data.attemptRows.length()").value(3))
                .andExpect(jsonPath("$.data.probeRows.length()").value(3))
                .andExpect(jsonPath("$.data.evidenceRows.length()").value(3));

        mockMvc.perform(get("/api/v1/workbench/problem-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId())
                        .queryParam("platforms", "gpt-4o"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataAvailability.status").value("empty"))
                .andExpect(jsonPath("$.data.dataAvailability.reasonCode").value("filtered_window_empty"))
                .andExpect(jsonPath("$.data.promptRows.length()").value(0))
                .andExpect(jsonPath("$.data.probeRows.length()").value(0))
                .andExpect(jsonPath("$.data.evidenceRows.length()").value(0));
    }

    @Test
    void shouldDisplayChineseSourceNamesForKnownDomainsInOverview() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台信源中文名品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        appendKnownSourceEvidence(created.runId());

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cards[4].primary").value("汽车之家"))
                .andExpect(jsonPath("$.data.sourceRows[0].label").value("汽车之家"));

        mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary.dominantLabel").value("汽车之家"))
                .andExpect(jsonPath("$.data.domainRows[0].domain").value("汽车之家"))
                .andExpect(jsonPath("$.data.domainRows[0].url").value("https://www.autohome.com.cn"));
    }

    @Test
    void shouldFallbackCitationProjectionToLatestReportWithEvidence() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台信源回退品牌", "智能门锁");
        seedCompletedReport(created.runId(), created.brandSpaceId());
        seedNewerCompletedReportWithoutCitationEvidence(created.runId(), created.brandSpaceId());

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceRows.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.cards[4].primary").value("brand.example.com"))
                .andExpect(jsonPath("$.data.dataAvailability.missingFields").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("sourceCitations"))));

        mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary.totalCitations").value(5))
                .andExpect(jsonPath("$.data.categorySlices.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.domainRows.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.domainRows[0].domain").value("brand.example.com"))
                .andExpect(jsonPath("$.data.urlRows.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.urlRows[0].url").value("https://brand.example.com/products/shower"));
    }

    @Test
    void shouldGroupTranslatedPublisherDomainsIntoSingleCitationRow() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台信源聚合品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        appendGroupedPublisherEvidence(created.runId());

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cards[4].primary").value("新浪新闻"))
                .andExpect(jsonPath("$.data.sourceRows[0].label").value("新浪新闻"));

        mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary.dominantLabel").value("新浪新闻"))
                .andExpect(jsonPath("$.data.domainRows[0].domain").value("新浪新闻"))
                .andExpect(jsonPath("$.data.domainRows[0].used").value(3))
                .andExpect(jsonPath("$.data.domainRows[1].domain").value("汽车之家"));
    }

    @Test
    void shouldClassifyCitationSourcesByDomainFamilyWhenEvidenceCodeIsGenericUrlCitation() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台信源分型品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        appendGenericUrlCitationDomainEvidence(created.runId());

        MvcResult result = mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("you", findDomainType(data.path("domainRows"), "empty.example.com"));
        assertEquals("editorial", findDomainType(data.path("domainRows"), "汽车之家"));
        assertEquals("ugc", findDomainType(data.path("domainRows"), "xueqiu.com"));
        assertEquals("reference", findDomainType(data.path("domainRows"), "百度百科"));
        assertTrue(hasCategorySlice(data.path("categorySlices"), "you"));
        assertTrue(hasCategorySlice(data.path("categorySlices"), "editorial"));
        assertTrue(hasCategorySlice(data.path("categorySlices"), "ugc"));
        assertTrue(hasCategorySlice(data.path("categorySlices"), "reference"));
    }

    @Test
    void shouldTreatTaskOfficialWebsiteSnapshotAsOwnedDomainTruthWhenBrandMetadataIsBlank() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "工作台官网快照品牌", "智能门锁");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        clearPersistedOfficialDomainMetadata(created.runId(), created.brandSpaceId());
        appendSingleGenericOfficialEvidence(created.runId(), "snapshot-owned-probe-1", "empty.example.com",
                "https://empty.example.com/products/hero", "官网产品页");

        MvcResult result = mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity.officialWebsite").value("https://empty.example.com"))
                .andExpect(jsonPath("$.data.identity.primaryDomain").value("empty.example.com"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("you", findDomainType(data.path("domainRows"), "empty.example.com"));
        assertEquals("owned", data.path("ownership").path("slices").path(0).path("key").asText());
        assertEquals(100.0d, data.path("summary").path("ownedShare").asDouble());
        assertEquals(0.0d, data.path("summary").path("thirdPartyShare").asDouble());
    }

    @Test
    void shouldTreatOfficialDomainFamilyAsOwnedWhenItMatchesKnownBrandDomainStem() throws Exception {
        AuthContext auth = register();
        CreatedBrandSpace created = createBrandSpace(auth, "比亚迪域名家族品牌", "新能源汽车", "https://www.byd.com");
        seedSingleCompletedReport(created.runId(), created.brandSpaceId());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(created.runId())
                .probeId("owned-family-probe-1")
                .domain("www.bydglobal.com")
                .url("https://www.bydglobal.com/cn/news/launch")
                .sourceTypeCode("url_citation")
                .title("比亚迪全球官网")
                .snippet("官方站点的全球新闻页")
                .createdAt(LocalDateTime.now())
                .build());

        MvcResult result = mockMvc.perform(get("/api/v1/workbench/citation-sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .queryParam("brandSpaceId", created.brandSpaceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity.officialWebsite").value("https://www.byd.com"))
                .andExpect(jsonPath("$.data.identity.primaryDomain").value("www.byd.com"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("you", findDomainType(data.path("domainRows"), "www.bydglobal.com"));
        assertEquals("owned", data.path("domainDetails").path(0).path("ownershipKey").asText());
        assertEquals(100.0d, data.path("summary").path("ownedShare").asDouble());
        assertTrue(hasCategorySlice(data.path("categorySlices"), "you"));
    }

    private void seedCompletedReport(String runId, String brandSpaceId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousAt = now.minusDays(7);
        task.setStatusCode(2);
        task.setProgressPercent(new BigDecimal("100.00"));
        task.setCompletedAt(now);
        task.setTargetModelProduct("doubao-seed-1.8");
        diagnosisTaskMapper.updateById(task);

        String previousRunId = UUID.randomUUID().toString();
        diagnosisTaskMapper.insert(DiagnosisTask.builder()
                .id(previousRunId)
                .organizationId(task.getOrganizationId())
                .brandId(task.getBrandId())
                .brandScopeId(brandSpaceId)
                .requestedByUserId(task.getRequestedByUserId())
                .requestedByMembershipId(task.getRequestedByMembershipId())
                .targetProbeCount(task.getTargetProbeCount())
                .targetModelProduct("doubao-seed-1.8")
                .engineVersion(task.getEngineVersion())
                .statusCode(2)
                .progressPercent(new BigDecimal("100.00"))
                .locale(task.getLocale())
                .language(task.getLanguage())
                .diagnosisGoal(task.getDiagnosisGoal())
                .brandNameSnapshot(task.getBrandNameSnapshot())
                .categorySnapshot(task.getCategorySnapshot())
                .officialWebsiteSnapshot(task.getOfficialWebsiteSnapshot())
                .startedAt(previousAt.minusMinutes(3))
                .completedAt(previousAt)
                .createdAt(previousAt.minusMinutes(4))
                .updatedAt(previousAt)
                .build());

        diagnosisReportRecordMapper.insert(DiagnosisReportRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(previousRunId)
                .organizationId(task.getOrganizationId())
                .brandScopeId(brandSpaceId)
                .brandId(task.getBrandId())
                .categorySnapshot(task.getCategorySnapshot())
                .targetModelProduct("doubao-seed-1.8")
                .reportStatusCode(1)
                .sampleSize(96)
                .spontaneousMentionRate(new BigDecimal("0.140000"))
                .confirmationResponseRate(new BigDecimal("0.330000"))
                .citationRate(new BigDecimal("0.360000"))
                .abvIndex(new BigDecimal("64.20"))
                .abvGrade("C+")
                .summaryJson(Map.of(
                        "overallMentionRate", 0.36d,
                        "recommendRate", 0.33d,
                        "spontaneousMentionRate", 0.14d,
                        "positiveRate", 0.57d,
                        "mentionedCount", 35
                ))
                .reportProjectionJson(Map.of(
                        "competitorRanking", List.of(
                                Map.of("brand", "九牧", "recommendRate", 0.41d, "mentionRate", 0.46d, "positiveRate", 0.56d),
                                Map.of("brand", "箭牌", "recommendRate", 0.29d, "mentionRate", 0.33d, "positiveRate", 0.49d)
                        ),
                        "negativeSignals", List.of(
                                Map.of("signal", "安装步骤解释不清", "count", 16)
                        ),
                        "brandPerception", Map.of(
                                "oneLineSummary", "前一轮仍停留在候选池边缘",
                                "topTags", List.of(
                                        Map.of("tag", "安装友好度", "count", 11),
                                        Map.of("tag", "低水压适配", "count", 9)
                                )
                        ),
                        "heroSummary", Map.of(
                                "headline", "稳沐在上一轮里可见度初步建立，但没有坐稳前排"
                        )
                ))
                .generatedAt(previousAt)
                .createdAt(previousAt)
                .build());

        diagnosisReportRecordMapper.insert(DiagnosisReportRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .organizationId(task.getOrganizationId())
                .brandScopeId(brandSpaceId)
                .brandId(task.getBrandId())
                .categorySnapshot(task.getCategorySnapshot())
                .targetModelProduct("doubao-seed-1.8")
                .reportStatusCode(1)
                .sampleSize(100)
                .spontaneousMentionRate(new BigDecimal("0.180000"))
                .confirmationResponseRate(new BigDecimal("0.510000"))
                .citationRate(new BigDecimal("0.420000"))
                .abvIndex(new BigDecimal("72.50"))
                .abvGrade("B")
                .summaryJson(Map.of(
                        "overallMentionRate", 0.42d,
                        "recommendRate", 0.37d,
                        "spontaneousMentionRate", 0.18d,
                        "positiveRate", 0.61d,
                        "mentionedCount", 42
                ))
                .reportProjectionJson(Map.of(
                        "competitorRanking", List.of(
                                Map.of("brand", "九牧", "recommendRate", 0.44d, "mentionRate", 0.49d, "positiveRate", 0.58d),
                                Map.of("brand", "箭牌", "recommendRate", 0.31d, "mentionRate", 0.35d, "positiveRate", 0.52d)
                        ),
                        "negativeSignals", List.of(
                                Map.of("signal", "低水压场景偏弱", "count", 21)
                        ),
                        "actionItems", Map.of(
                                "p0", List.of("先修低水压场景表达"),
                                "p1", List.of("补强安装友好度内容")
                        ),
                        "brandPerception", Map.of(
                                "oneLineSummary", "品牌已进入候选池，但推荐率仍需继续抬升",
                                "topTags", List.of(
                                        Map.of("tag", "安装友好度", "count", 18),
                                        Map.of("tag", "低水压适配", "count", 14),
                                        Map.of("tag", "售后响应", "count", 9)
                                )
                        ),
                        "heroSummary", Map.of(
                                "headline", "稳沐在家居五金候选池里已有可见度，但还没形成稳定首选"
                        ),
                        "clusterProfiles", List.of(
                                Map.of(
                                        "key", "cluster_install",
                                        "title", "安装与适配问题",
                                        "problemSpaceBucket", "scenario-fit",
                                        "description", "用户对低水压和安装门槛的担心集中在同一问题簇",
                                        "sampleCount", 21,
                                        "blindSpot", true,
                                        "prompts", List.of("低水压能不能稳定出水", "老房子管径小会不会漏水")
                                ),
                                Map.of(
                                        "key", "cluster_service",
                                        "title", "售后与可靠性问题",
                                        "problemSpaceBucket", "risk-objection",
                                        "description", "售后流程和质保承诺描述不够具体",
                                        "sampleCount", 11,
                                        "blindSpot", false,
                                        "prompts", List.of("坏了多久能换新", "质保范围覆盖哪些配件")
                                )
                        ),
                        "evidenceChains", List.of(
                                Map.of(
                                        "key", "probe-1",
                                        "clusterName", "安装与适配问题",
                                        "problemSpaceBucket", "scenario-fit",
                                        "prompt", "低水压能不能稳定出水",
                                        "answer", "低水压场景下的稳定性描述偏少，建议补充实际工况数据",
                                        "judgement", "回答提到增压能力但缺少量化证据",
                                        "sourceLabels", List.of("brand.example.com")
                                ),
                                Map.of(
                                        "key", "probe-2",
                                        "clusterName", "售后与可靠性问题",
                                        "problemSpaceBucket", "risk-objection",
                                        "prompt", "坏了多久能换新",
                                        "answer", "售后承诺存在，但具体时效和流程不够明确",
                                        "judgement", "需要补充标准时效和服务路径",
                                        "sourceLabels", List.of("review.example.com")
                                )
                        ),
                        "commercialInsights", Map.of(
                                "headlineInsight", "品牌在竞争问题里已经有可见度，但关键场景和自发心智仍然偏弱",
                                "insightSource", "LLM"
                        ),
                        "sourceAttribution", Map.of(
                                "dominantSource", "editorial",
                                "dominantPercent", 0.58d,
                                "categories", List.of(
                                        Map.of("name", "editorial", "count", 7, "percent", 0.58d),
                                        Map.of("name", "corporate", "count", 5, "percent", 0.42d)
                                )
                        )
                ))
                .topInsightsJson(List.of(
                        Map.of(
                                "title", "品牌在对抗性场景下存在致命盲区，样本量为零，无法评估真实防御能力",
                                "evidence", "对抗桶 confirmationResponseRate=0.00%，且 effectiveSampleSize=0",
                                "recommendation", "立即启动对抗性压力测试专项研究，补齐在竞争性、误导性语境下的品牌认知数据"
                        ),
                        Map.of(
                                "title", "品牌完全缺乏自发认知，所有提及均为提示后被动确认，心智份额脆弱",
                                "evidence", "总体 spontaneousMentionRate=0.00%，而 confirmationResponseRate=100.00%",
                                "recommendation", "重构品牌传播策略，将资源从被动确认转向主动植入，以提升 spontaneousMentionRate"
                        ),
                        Map.of(
                                "title", "竞品‘金正’在用户心智中占据显著份额且强度极高，构成直接压制",
                                "evidence", "竞品‘金正’ responseShare=20.00%，且 averageStrength=92.00%",
                                "recommendation", "立即分析竞品‘金正’的高强度认知来源，并针对性设计差异化沟通点以削弱其压制"
                        )
                ))
                .generatedAt(now)
                .createdAt(now)
                .build());

        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(previousRunId)
                .probeId("prev-probe-1")
                .domain("brand.example.com")
                .url("https://brand.example.com/products/classic")
                .sourceTypeCode("official_site")
                .title("上一轮品牌产品页")
                .snippet("上一轮官方产品页")
                .createdAt(previousAt.minusMinutes(1))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(previousRunId)
                .probeId("prev-probe-2")
                .domain("review.example.com")
                .url("https://review.example.com/legacy-review")
                .sourceTypeCode("editorial_review")
                .title("上一轮测评媒体")
                .snippet("上一轮第三方测评")
                .createdAt(previousAt.minusSeconds(30))
                .build());

        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("probe-1")
                .domain("brand.example.com")
                .url("https://brand.example.com/products/shower")
                .sourceTypeCode("official_site")
                .title("品牌官方产品页")
                .snippet("稳沐低水压增压花洒")
                .createdAt(now)
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("probe-2")
                .domain("review.example.com")
                .url("https://review.example.com/shower-review")
                .sourceTypeCode("editorial_review")
                .title("测评媒体")
                .snippet("第三方安装体验评测")
                .createdAt(now)
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("probe-3")
                .domain("brand.example.com")
                .url("https://brand.example.com/guide/install")
                .sourceTypeCode("official_site")
                .title("安装指南")
                .snippet("官方安装说明")
                .createdAt(now)
                .build());

        insertBrandMention(previousRunId, "prev-probe-1", task.getBrandNameSnapshot(), 1, true);
        insertBrandMention(previousRunId, "prev-probe-2", task.getBrandNameSnapshot(), 2, true);
        insertBrandMention(previousRunId, "prev-probe-3", task.getBrandNameSnapshot(), 3, true);
        insertBrandMention(previousRunId, "prev-probe-1", "九牧", 1, true);
        insertBrandMention(previousRunId, "prev-probe-2", "九牧", 1, true);
        insertBrandMention(previousRunId, "prev-probe-3", "九牧", 2, true);
        insertBrandMention(previousRunId, "prev-probe-1", "箭牌", 3, true);
        insertBrandMention(previousRunId, "prev-probe-2", "箭牌", 3, true);

        insertBrandMention(runId, "probe-1", task.getBrandNameSnapshot(), 1, true);
        insertBrandMention(runId, "probe-2", task.getBrandNameSnapshot(), 2, true);
        insertBrandMention(runId, "probe-1", "九牧", 2, true);
        insertBrandMention(runId, "probe-2", "九牧", 2, true);
        insertBrandMention(runId, "probe-3", "九牧", 3, true);
        insertBrandMention(runId, "probe-1", "箭牌", 3, true);
        insertBrandMention(runId, "probe-2", "箭牌", 4, true);
    }

    private CreatedBrandSpace createBrandSpace(AuthContext auth, String brandName, String category) throws Exception {
        return createBrandSpace(auth, brandName, category, "https://empty.example.com");
    }

    private CreatedBrandSpace createBrandSpace(AuthContext auth,
                                               String brandName,
                                               String category,
                                               String officialWebsite) throws Exception {
        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", brandName);
        previewPayload.put("category", category);
        previewPayload.put("officialWebsite", officialWebsite);
        previewPayload.put("competitors", List.of("竞品一", "竞品二"));
        previewPayload.put("supplementalBrief", "用于工作台空态回归");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "wb-empty-preview-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("prompts", objectMapper.convertValue(previewData.path("prompts"), List.class));
        createPayload.put("idempotencyKey", "wb-empty-create-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return new CreatedBrandSpace(
                createData.path("brandSpaceId").asText(),
                createData.path("initialRunId").asText()
        );
    }

    private void seedSingleCompletedReport(String runId, String brandSpaceId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatusCode(2);
        task.setProgressPercent(new BigDecimal("100.00"));
        task.setCompletedAt(now);
        task.setTargetModelProduct("doubao-seed-1.8");
        diagnosisTaskMapper.updateById(task);

        diagnosisReportRecordMapper.insert(DiagnosisReportRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .organizationId(task.getOrganizationId())
                .brandScopeId(brandSpaceId)
                .brandId(task.getBrandId())
                .categorySnapshot(task.getCategorySnapshot())
                .targetModelProduct("doubao-seed-1.8")
                .reportStatusCode(1)
                .sampleSize(60)
                .spontaneousMentionRate(new BigDecimal("0.120000"))
                .confirmationResponseRate(new BigDecimal("0.410000"))
                .citationRate(new BigDecimal("0.280000"))
                .abvIndex(new BigDecimal("67.30"))
                .abvGrade("B-")
                .summaryJson(Map.of(
                        "overallMentionRate", 0.28d,
                        "recommendRate", 0.41d,
                        "spontaneousMentionRate", 0.12d,
                        "positiveRate", 0.55d,
                        "mentionedCount", 17
                ))
                .reportProjectionJson(Map.of(
                        "competitorRanking", List.of(
                                Map.of("brand", "竞品一", "recommendRate", 0.36d, "mentionRate", 0.40d, "positiveRate", 0.52d),
                                Map.of("brand", "竞品二", "recommendRate", 0.31d, "mentionRate", 0.34d, "positiveRate", 0.48d)
                        ),
                        "negativeSignals", List.of(
                                Map.of("signal", "安装成本解释不足", "count", 6)
                        ),
                        "brandPerception", Map.of(
                                "oneLineSummary", "单轮样本已生成，仍需等待下一轮趋势对比",
                                "topTags", List.of(
                                        Map.of("tag", "安装成本", "count", 6)
                                )
                        ),
                        "heroSummary", Map.of(
                                "headline", "单轮样本已生成，趋势仍需下一轮确认"
                        )
                ))
                .generatedAt(now)
                .createdAt(now)
                .build());

        insertBrandMention(runId, "single-probe-1", task.getBrandNameSnapshot(), 1, true);
    }

    private void seedSingleCompletedReportWithoutCompetitorRanking(String runId, String brandSpaceId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatusCode(2);
        task.setProgressPercent(new BigDecimal("100.00"));
        task.setCompletedAt(now);
        task.setTargetModelProduct("doubao-seed-1.8");
        diagnosisTaskMapper.updateById(task);

        diagnosisReportRecordMapper.insert(DiagnosisReportRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .organizationId(task.getOrganizationId())
                .brandScopeId(brandSpaceId)
                .brandId(task.getBrandId())
                .categorySnapshot(task.getCategorySnapshot())
                .targetModelProduct("doubao-seed-1.8")
                .reportStatusCode(1)
                .sampleSize(60)
                .spontaneousMentionRate(new BigDecimal("0.120000"))
                .confirmationResponseRate(new BigDecimal("0.410000"))
                .citationRate(new BigDecimal("0.280000"))
                .abvIndex(new BigDecimal("67.30"))
                .abvGrade("B-")
                .summaryJson(Map.of(
                        "overallMentionRate", 0.28d,
                        "recommendRate", 0.41d,
                        "spontaneousMentionRate", 0.12d,
                        "positiveRate", 0.55d,
                        "mentionedCount", 17
                ))
                .reportProjectionJson(Map.of(
                        "competitorRanking", List.of(),
                        "negativeSignals", List.of(
                                Map.of("signal", "竞品本轮未命中", "count", 6)
                        ),
                        "brandPerception", Map.of(
                                "oneLineSummary", "单轮样本已生成，但竞品结果为空，工作台应回退到任务快照竞品"
                        ),
                        "heroSummary", Map.of(
                                "headline", "竞品结果为空时，工作台仍需保留任务快照竞品占位"
                        )
                ))
                .generatedAt(now)
                .createdAt(now)
                .build());

        insertBrandMention(runId, "single-probe-1", task.getBrandNameSnapshot(), 1, true);
    }

    private void seedNewerCompletedReportWithoutCitationEvidence(String runId, String brandSpaceId) {
        DiagnosisTask baseTask = diagnosisTaskMapper.selectById(runId);
        LocalDateTime now = LocalDateTime.now().plusMinutes(5);
        String fallbackRunId = UUID.randomUUID().toString();
        Map<String, Object> sourceAttribution = new HashMap<>();
        sourceAttribution.put("dominantSource", null);
        sourceAttribution.put("dominantPercent", 0.0d);
        sourceAttribution.put("categories", List.of(
                Map.of("name", "LLM 洞察", "count", 0, "percent", 0.0d)
        ));
        sourceAttribution.put("topDomains", List.of());
        sourceAttribution.put("evidenceCount", 0);
        Map<String, Object> reportProjection = new HashMap<>();
        reportProjection.put("competitorRanking", List.of(
                Map.of("brand", "九牧", "recommendRate", 0.43d, "mentionRate", 0.48d, "positiveRate", 0.57d)
        ));
        reportProjection.put("negativeSignals", List.of(
                Map.of("signal", "新一轮报告未落盘 citation 明细", "count", 8)
        ));
        reportProjection.put("brandPerception", Map.of(
                "oneLineSummary", "最新报告保留了 KPI，但 citation 明细为空"
        ));
        reportProjection.put("heroSummary", Map.of(
                "headline", "最新报告只剩 KPI 快照，信源明细需要回退到最近可用快照"
        ));
        reportProjection.put("evidenceChain", List.of());
        reportProjection.put("sourceAttribution", sourceAttribution);

        diagnosisTaskMapper.insert(DiagnosisTask.builder()
                .id(fallbackRunId)
                .organizationId(baseTask.getOrganizationId())
                .brandId(baseTask.getBrandId())
                .brandScopeId(brandSpaceId)
                .requestedByUserId(baseTask.getRequestedByUserId())
                .requestedByMembershipId(baseTask.getRequestedByMembershipId())
                .targetProbeCount(baseTask.getTargetProbeCount())
                .targetModelProduct("doubao-seed-1.8")
                .engineVersion(baseTask.getEngineVersion())
                .statusCode(2)
                .progressPercent(new BigDecimal("100.00"))
                .locale(baseTask.getLocale())
                .language(baseTask.getLanguage())
                .diagnosisGoal(baseTask.getDiagnosisGoal())
                .brandNameSnapshot(baseTask.getBrandNameSnapshot())
                .categorySnapshot(baseTask.getCategorySnapshot())
                .officialWebsiteSnapshot(baseTask.getOfficialWebsiteSnapshot())
                .startedAt(now.minusMinutes(3))
                .completedAt(now)
                .createdAt(now.minusMinutes(4))
                .updatedAt(now)
                .build());

        diagnosisReportRecordMapper.insert(DiagnosisReportRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(fallbackRunId)
                .organizationId(baseTask.getOrganizationId())
                .brandScopeId(brandSpaceId)
                .brandId(baseTask.getBrandId())
                .categorySnapshot(baseTask.getCategorySnapshot())
                .targetModelProduct("doubao-seed-1.8")
                .reportStatusCode(1)
                .sampleSize(100)
                .spontaneousMentionRate(new BigDecimal("0.180000"))
                .confirmationResponseRate(new BigDecimal("0.510000"))
                .citationRate(new BigDecimal("0.420000"))
                .abvIndex(new BigDecimal("73.10"))
                .abvGrade("B")
                .summaryJson(Map.of(
                        "overallMentionRate", 0.42d,
                        "recommendRate", 0.38d,
                        "spontaneousMentionRate", 0.19d,
                        "positiveRate", 0.60d,
                        "mentionedCount", 43
                ))
                .reportProjectionJson(reportProjection)
                .generatedAt(now)
                .createdAt(now)
                .build());
    }

    private void seedActualProblemAnalysisRuntime(String runId, String brandSpaceId) {
        clearProblemAnalysisRuntime(runId);
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt))
                .stream()
                .limit(3)
                .toList();
        assertEquals(3, prompts.size());

        Map<String, BrandSpaceTopic> topicById = new HashMap<>();
        for (BrandSpaceTopic topic : topics) {
            topicById.put(topic.getId(), topic);
        }

        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        Map<String, Object> taskOrder = objectMapper.convertValue(task.getTaskOrderJson(), Map.class);
        Map<String, Object> businessHints = taskOrder.get("businessHints") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(taskOrder.get("businessHints"), Map.class))
                : new HashMap<>();
        Map<String, Object> sampling = businessHints.get("sampling") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(businessHints.get("sampling"), Map.class))
                : new HashMap<>();
        Map<String, Object> scheduleDecision = businessHints.get("scheduleDecision") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(businessHints.get("scheduleDecision"), Map.class))
                : new HashMap<>();
        sampling.put("targetProbeCount", prompts.size());
        businessHints.put("sampling", sampling);
        scheduleDecision.put("executionPrompts", prompts.stream()
                .map(prompt -> {
                    BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
                    return Map.<String, Object>of(
                            "promptId", prompt.getId(),
                            "topicId", prompt.getTopicId(),
                            "topicName", topic == null ? null : topic.getName(),
                            "promptText", prompt.getPromptText(),
                            "seqNo", prompt.getSeqNo()
                    );
                })
                .toList());
        businessHints.put("scheduleDecision", scheduleDecision);
        taskOrder.put("businessHints", businessHints);
        task.setTaskOrderJson(taskOrder);
        task.setTargetProbeCount(prompts.size());
        diagnosisTaskMapper.updateById(task);

        List<String> d3Values = List.of(
                "identity_freedom",
                "understanding_competence",
                "subsistence_protection"
        );
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < prompts.size(); index++) {
            BrandSpacePrompt prompt = prompts.get(index);
            BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
            String probeId = UUID.randomUUID().toString();
            String attemptId = UUID.randomUUID().toString();
            diagnosisProbeRecordMapper.insert(DiagnosisProbeRecord.builder()
                    .id(probeId)
                    .diagnosisTaskId(runId)
                    .probeRef("bridge-probe-" + (index + 1))
                    .seqNo(index + 1)
                    .bucketTypeCode("ORTHOGONAL")
                    .probeTypeCode("OPEN_DISCOVERY")
                    .d1Identity("exploration")
                    .d2Context("informational_directed")
                    .d3PainPoint(d3Values.get(index))
                    .d4Jtbd("plan_purchase")
                    .d5Knowledge("anticipatory_constraint")
                    .d6Constraints("specification")
                    .surfaceIntent("decision")
                    .interactionPosture("curious")
                    .conversationMode("assistant")
                    .renderText("这是改写后的真实探针 " + (index + 1))
                    .renderSourceCode(0)
                    .qualityGatePassed(true)
                    .fallbackUsed(false)
                    .renderMetadataJson(Map.of("bridge", true))
                    .triggeredRulesJson(Map.of("source", "workbench-test"))
                    .statusCode(1)
                    .createdAt(now.plusSeconds(index))
                    .updatedAt(now.plusSeconds(index))
                    .build());
            probeDispatchAttemptMapper.insert(ProbeDispatchAttempt.builder()
                    .id(attemptId)
                    .diagnosisTaskId(runId)
                    .probeId(probeId)
                    .roundNo(1)
                    .attemptNo(1)
                    .provider("doubao")
                    .modelId("doubao-seed-1-6-lite-251015")
                    .searchRequested(false)
                    .searchSatisfied(false)
                    .searchFallbackFlag(false)
                    .searchCallCount(0)
                    .searchQueriesJson(List.of(prompt.getPromptText()))
                    .rawResponseText("这是 " + (topic == null ? "默认主题" : topic.getName()) + " 的真实回答 " + (index + 1))
                    .citationsJson(List.of())
                    .responseMetadataJson(Map.of("responsePreview", "真实回答预览 " + (index + 1)))
                    .statusCode(1)
                    .selectedFinal(true)
                    .createdAt(now.plusSeconds(index))
                    .build());
            probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .dispatchAttemptId(attemptId)
                    .diagnosisTaskId(runId)
                    .probeId(probeId)
                    .sourceTypeCode(index == 0 ? "official_site" : "editorial_review")
                    .domain(index == 0 ? "brand.example.com" : "review.example.com")
                    .url(index == 0
                            ? "https://brand.example.com/problem-analysis/" + (index + 1)
                            : "https://review.example.com/problem-analysis/" + (index + 1))
                    .title("问题分析证据 " + (index + 1))
                    .snippet("用于校验 prompt/topic 桥接")
                    .createdAt(now.plusSeconds(index))
                    .build());
        }
    }

    private void seedProblemAnalysisRuntimeWithoutEvidence(String runId, String brandSpaceId) {
        clearProblemAnalysisRuntime(runId);
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt))
                .stream()
                .limit(3)
                .toList();
        assertEquals(3, prompts.size());

        Map<String, BrandSpaceTopic> topicById = new HashMap<>();
        for (BrandSpaceTopic topic : topics) {
            topicById.put(topic.getId(), topic);
        }

        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        Map<String, Object> taskOrder = objectMapper.convertValue(task.getTaskOrderJson(), Map.class);
        Map<String, Object> businessHints = taskOrder.get("businessHints") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(taskOrder.get("businessHints"), Map.class))
                : new HashMap<>();
        Map<String, Object> sampling = businessHints.get("sampling") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(businessHints.get("sampling"), Map.class))
                : new HashMap<>();
        Map<String, Object> scheduleDecision = businessHints.get("scheduleDecision") instanceof Map<?, ?>
                ? new HashMap<>(objectMapper.convertValue(businessHints.get("scheduleDecision"), Map.class))
                : new HashMap<>();
        sampling.put("targetProbeCount", prompts.size());
        businessHints.put("sampling", sampling);
        scheduleDecision.put("executionPrompts", prompts.stream()
                .map(prompt -> {
                    BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
                    return Map.<String, Object>of(
                            "promptId", prompt.getId(),
                            "topicId", prompt.getTopicId(),
                            "topicName", topic == null ? null : topic.getName(),
                            "promptText", prompt.getPromptText(),
                            "seqNo", prompt.getSeqNo()
                    );
                })
                .toList());
        businessHints.put("scheduleDecision", scheduleDecision);
        taskOrder.put("businessHints", businessHints);
        task.setTaskOrderJson(taskOrder);
        task.setTargetProbeCount(prompts.size());
        diagnosisTaskMapper.updateById(task);

        List<String> d3Values = List.of(
                "identity_freedom",
                "understanding_competence",
                "subsistence_protection"
        );
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < prompts.size(); index++) {
            BrandSpacePrompt prompt = prompts.get(index);
            BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
            String probeId = UUID.randomUUID().toString();
            String attemptId = UUID.randomUUID().toString();
            diagnosisProbeRecordMapper.insert(DiagnosisProbeRecord.builder()
                    .id(probeId)
                    .diagnosisTaskId(runId)
                    .probeRef("no-evidence-probe-" + (index + 1))
                    .seqNo(index + 1)
                    .bucketTypeCode("ORTHOGONAL")
                    .probeTypeCode("OPEN_DISCOVERY")
                    .d1Identity("exploration")
                    .d2Context("informational_directed")
                    .d3PainPoint(d3Values.get(index))
                    .d4Jtbd("plan_purchase")
                    .d5Knowledge("anticipatory_constraint")
                    .d6Constraints("specification")
                    .surfaceIntent("decision")
                    .interactionPosture("curious")
                    .conversationMode("assistant")
                    .renderText("无证据探针 " + (index + 1))
                    .renderSourceCode(0)
                    .qualityGatePassed(true)
                    .fallbackUsed(false)
                    .renderMetadataJson(Map.of("bridge", true))
                    .triggeredRulesJson(Map.of("source", "workbench-no-evidence"))
                    .statusCode(1)
                    .createdAt(now.plusSeconds(index))
                    .updatedAt(now.plusSeconds(index))
                    .build());
            probeDispatchAttemptMapper.insert(ProbeDispatchAttempt.builder()
                    .id(attemptId)
                    .diagnosisTaskId(runId)
                    .probeId(probeId)
                    .roundNo(1)
                    .attemptNo(1)
                    .provider("doubao")
                    .modelId("doubao-seed-1-6-lite-251015")
                    .searchRequested(false)
                    .searchSatisfied(false)
                    .searchFallbackFlag(false)
                    .searchCallCount(0)
                    .searchQueriesJson(List.of(prompt.getPromptText()))
                    .rawResponseText("这是 " + (topic == null ? "默认主题" : topic.getName()) + " 的无证据回答 " + (index + 1))
                    .citationsJson(List.of())
                    .responseMetadataJson(Map.of("responsePreview", "无证据回答预览 " + (index + 1)))
                    .statusCode(1)
                    .selectedFinal(true)
                    .createdAt(now.plusSeconds(index))
                    .build());
        }
    }

    private void clearProblemAnalysisRuntime(String runId) {
        jdbcTemplate.update("DELETE FROM probe_brand_mentions WHERE diagnosis_task_id = ?", runId);
        jdbcTemplate.update("DELETE FROM probe_evidences WHERE diagnosis_task_id = ?", runId);
        jdbcTemplate.update("DELETE FROM probe_dispatch_attempts WHERE diagnosis_task_id = ?", runId);
        jdbcTemplate.update("DELETE FROM diagnosis_probes WHERE diagnosis_task_id = ?", runId);
        jdbcTemplate.update("DELETE FROM diagnosis_events WHERE diagnosis_task_id = ?", runId);
    }

    private void rewriteCurrentProblemAnalysisConfig(String brandSpaceId) {
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt));
        assertTrue(topics.size() >= 3);
        assertTrue(prompts.size() >= 3);
        topics.get(0).setName("后改主题一");
        topics.get(1).setName("后改主题二");
        topics.get(2).setName("后改主题三");
        brandSpaceTopicMapper.updateById(topics.get(0));
        brandSpaceTopicMapper.updateById(topics.get(1));
        brandSpaceTopicMapper.updateById(topics.get(2));
        prompts.get(0).setPromptText("后改 prompt 1");
        prompts.get(1).setPromptText("后改 prompt 2");
        prompts.get(2).setPromptText("后改 prompt 3");
        brandSpacePromptMapper.updateById(prompts.get(0));
        brandSpacePromptMapper.updateById(prompts.get(1));
        brandSpacePromptMapper.updateById(prompts.get(2));
    }

    private void appendKnownSourceEvidence(String runId) {
        LocalDateTime now = LocalDateTime.now();
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("known-source-probe-1")
                .domain("www.autohome.com.cn")
                .url("https://www.autohome.com.cn/drive/202504/1290012.html")
                .sourceTypeCode("editorial_review")
                .title("汽车之家测评")
                .snippet("第三方汽车媒体测评")
                .createdAt(now)
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("known-source-probe-2")
                .domain("www.autohome.com.cn")
                .url("https://www.autohome.com.cn/news/202504/1287788.html")
                .sourceTypeCode("editorial_review")
                .title("汽车之家资讯")
                .snippet("品牌资讯引用")
                .createdAt(now.plusSeconds(1))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("known-source-probe-3")
                .domain("m.pcauto.com.cn")
                .url("https://m.pcauto.com.cn/x/2345/23456789.html")
                .sourceTypeCode("editorial_review")
                .title("太平洋汽车导购")
                .snippet("汽车导购内容")
                .createdAt(now.plusSeconds(2))
                .build());
    }

    private void appendGroupedPublisherEvidence(String runId) {
        LocalDateTime now = LocalDateTime.now();
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("publisher-group-probe-1")
                .domain("cj.sina.cn")
                .url("https://cj.sina.cn/articles/view/2025-04-01/detail-1.html")
                .sourceTypeCode("editorial_review")
                .title("新浪财经报道")
                .snippet("新浪财经引用")
                .createdAt(now)
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("publisher-group-probe-2")
                .domain("k.sina.cn")
                .url("https://k.sina.cn/article_123456_1.html")
                .sourceTypeCode("editorial_review")
                .title("新浪新闻报道")
                .snippet("新浪新闻引用")
                .createdAt(now.plusSeconds(1))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("publisher-group-probe-3")
                .domain("k.sina.com.cn")
                .url("https://k.sina.com.cn/article_654321_1.html")
                .sourceTypeCode("editorial_review")
                .title("新浪新闻二次报道")
                .snippet("新浪新闻二次引用")
                .createdAt(now.plusSeconds(2))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("publisher-group-probe-4")
                .domain("www.autohome.com.cn")
                .url("https://www.autohome.com.cn/news/202504/1287788.html")
                .sourceTypeCode("editorial_review")
                .title("汽车之家资讯")
                .snippet("汽车之家引用")
                .createdAt(now.plusSeconds(3))
                .build());
    }

    private void appendGenericUrlCitationDomainEvidence(String runId) {
        LocalDateTime now = LocalDateTime.now();
        appendSingleGenericOfficialEvidence(runId, "family-probe-owned", "empty.example.com",
                "https://empty.example.com/products/entry", "官网产品页");
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("family-probe-editorial")
                .domain("www.autohome.com.cn")
                .url("https://www.autohome.com.cn/drive/202504/1290012.html")
                .sourceTypeCode("url_citation")
                .title("汽车之家测评")
                .snippet("泛化证据类型下的媒体来源")
                .createdAt(now.plusSeconds(1))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("family-probe-ugc")
                .domain("xueqiu.com")
                .url("https://xueqiu.com/1234567890/abcdef")
                .sourceTypeCode("url_citation")
                .title("雪球讨论")
                .snippet("泛化证据类型下的社区来源")
                .createdAt(now.plusSeconds(2))
                .build());
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId("family-probe-reference")
                .domain("baike.baidu.com")
                .url("https://baike.baidu.com/item/%E6%99%BA%E8%83%BD%E9%97%A8%E9%94%81/12345")
                .sourceTypeCode("url_citation")
                .title("百度百科词条")
                .snippet("泛化证据类型下的资料来源")
                .createdAt(now.plusSeconds(3))
                .build());
    }

    private void appendSingleGenericOfficialEvidence(String runId, String probeId, String domain, String url, String title) {
        probeEvidenceRecordMapper.insert(ProbeEvidenceRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId(probeId)
                .domain(domain)
                .url(url)
                .sourceTypeCode("url_citation")
                .title(title)
                .snippet("泛化证据类型下的官方来源")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void clearPersistedOfficialDomainMetadata(String runId, String brandSpaceId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        Brand brand = brandMapper.selectById(task.getBrandId());
        brand.setCanonicalDomain(null);
        brand.setOfficialWebsite(null);
        brand.setOfficialDomainsJson(List.of());
        brandMapper.updateById(brand);

        BrandScope scope = brandScopeMapper.selectById(brandSpaceId);
        scope.setPrimaryDomain(null);
        brandScopeMapper.updateById(scope);
    }

    private String findDomainType(JsonNode domainRows, String domainLabel) {
        for (JsonNode row : domainRows) {
            if (domainLabel.equals(row.path("domain").asText())) {
                return row.path("type").asText();
            }
        }
        return null;
    }

    private boolean hasCategorySlice(JsonNode categorySlices, String key) {
        for (JsonNode row : categorySlices) {
            if (key.equals(row.path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    private void insertBrandMention(String runId, String probeId, String brandName, Integer positionIndex, boolean recommendedFlag) {
        probeBrandMentionRecordMapper.insert(ProbeBrandMentionRecord.builder()
                .id(UUID.randomUUID().toString())
                .diagnosisTaskId(runId)
                .probeId(probeId)
                .brandName(brandName)
                .mentionedFlag(true)
                .recommendedFlag(recommendedFlag)
                .positionIndex(positionIndex)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private AuthContext register() throws Exception {
        String username = "u" + System.nanoTime() + "@example.com";
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", "StrongPass123");
        payload.put("displayName", "Tester");
        payload.put("companyName", "Geo");

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode response = objectMapper.readTree(registerResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String userId = response.path("data").path("userId").asText();
        userMapper.addCredits(userId, 200);
        return new AuthContext(
                userId,
                response.path("data").path("token").asText()
        );
    }

    private void mockRecognition(String category, String website, List<String> competitors, String summary) {
        when(brandSpaceRecognitionService.recognize(any()))
                .thenReturn(BrandSpaceRecognitionView.builder()
                        .suggestedCategory(category)
                        .officialWebsite(website)
                        .competitors(competitors)
                        .brandSummary(summary)
                        .source("doubao_web_search")
                        .build());
    }

    private record AuthContext(String userId, String token) {
    }

    private record CreatedBrandSpace(String brandSpaceId, String runId) {
    }
}
