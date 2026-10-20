package com.geoprobe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.User;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.entity.v1.Organization;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.OrganizationMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class BrandSpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiagnosisTaskMapper diagnosisTaskMapper;

    @Autowired
    private DiagnosisEventRecordMapper diagnosisEventRecordMapper;

    @Autowired
    private BrandSpaceTopicMapper brandSpaceTopicMapper;

    @Autowired
    private BrandSpacePromptMapper brandSpacePromptMapper;

    @Autowired
    private BrandScopeMapper brandScopeMapper;

    @Autowired
    private CreditTransactionMapper creditTransactionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private OrganizationMembershipMapper organizationMembershipMapper;

    @MockBean
    private V42DiagnosisExecutionService v42DiagnosisExecutionService;
    @MockBean
    private com.geoprobe.service.BrandSpaceRecognitionService brandSpaceRecognitionService;

    @Test
    void shouldPreviewCreateListAndReadRunObserver() throws Exception {
        AuthContext auth = register();
        mockRecognition("家居五金", "https://www.cobbe.com", List.of("九牧", "四季沐歌"), "卡贝是家居五金品牌，常见对比集中在花洒与卫浴场景。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "卡贝");
        previewPayload.put("category", "家居五金");
        previewPayload.put("officialWebsite", "https://www.cobbe.com");
        previewPayload.put("competitors", List.of("九牧", "四季沐歌"));
        previewPayload.put("supplementalBrief", "重点关注出租屋、材质安全和价格对比");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "bs-preview-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.previewToken").isNotEmpty())
                .andExpect(jsonPath("$.data.brandSpaceDraft.brandName").value("卡贝"))
                .andExpect(jsonPath("$.data.recognition.suggestedCategory").value("家居五金"))
                .andExpect(jsonPath("$.data.recognition.brandSummary").value("卡贝是家居五金品牌，常见对比集中在花洒与卫浴场景。"))
                .andExpect(jsonPath("$.data.serviceAccess.status").value("trial"))
                .andExpect(jsonPath("$.data.estimatedRun.targetProbeCount").value(50))
                .andExpect(jsonPath("$.data.estimatedRun.realtimeSearchEnabled").value(true))
                .andExpect(jsonPath("$.data.estimatedRun.schedule.targetProbeCount").value(50))
                .andExpect(jsonPath("$.data.estimatedRun.schedule.selectedTopicCount").value(10))
                .andExpect(jsonPath("$.data.estimatedRun.schedule.schedulingReason").isNotEmpty())
                .andExpect(jsonPath("$.data.topics[0].tempId").value("topic_tmp_1"))
                .andExpect(jsonPath("$.data.prompts").isArray())
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("idempotencyKey", "bs-create-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").isNotEmpty())
                .andExpect(jsonPath("$.data.initialRunId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("generating"))
                .andExpect(jsonPath("$.data.nextView").value("/brands"))
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String brandSpaceId = createData.path("brandSpaceId").asText();
        String runId = createData.path("initialRunId").asText();

        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        assertNotNull(task);
        assertEquals(brandSpaceId, task.getBrandScopeId());
        assertEquals(50, task.getTargetProbeCount());
        assertNotNull(task.getTaskOrderJson());
        assertTrue(task.getTaskOrderJson().containsKey("businessHints"));
        @SuppressWarnings("unchecked")
        Map<String, Object> previewBusinessHints = (Map<String, Object>) task.getTaskOrderJson().get("businessHints");
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) previewBusinessHints.get("samplingRealtimeSearch");
        assertEquals(true, realtimeSearch.get("enabled"));
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) previewBusinessHints.get("samplingEngine");
        assertEquals("doubao", samplingEngine.get("provider"));

        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandSpaceTopic>()
                        .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
        );
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandSpacePrompt>()
                        .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
        );
        assertFalse(topics.isEmpty());
        assertFalse(prompts.isEmpty());

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.items[0].status").value("generating"))
                .andExpect(jsonPath("$.data.items[0].serviceAccessStatus").value("trial"))
                .andExpect(jsonPath("$.data.items[0].trackingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].configReady").value(true))
                .andExpect(jsonPath("$.data.items[0].activePromptCount").value(10))
                .andExpect(jsonPath("$.data.items[0].lastRunStatus").value("created"))
                .andExpect(jsonPath("$.data.items[0].lastRunAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].latestSchedule.targetProbeCount").value(50))
                .andExpect(jsonPath("$.data.items[0].latestSchedule.selectedTopicCount").value(10))
                .andExpect(jsonPath("$.data.items[0].latestSchedule.schedulingReason").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].latestSchedule.weeklyTargetTopicCount").isNumber())
                .andExpect(jsonPath("$.data.items[0].latestSchedule.weeklyGapTopicCount").isNumber());

        mockMvc.perform(get("/api/v1/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.currentStep").value("created"));

        mockMvc.perform(get("/api/v1/runs/{runId}/events", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.events[0].type").value("run_started"))
                .andExpect(jsonPath("$.data.events[1].type").value("step_changed"));

        mockMvc.perform(get("/api/v1/service-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("trial"))
                .andExpect(jsonPath("$.data.planName").value("公测版"))
                .andExpect(jsonPath("$.data.limits.promptLimitPerBrandSpace").value(100))
                .andExpect(jsonPath("$.data.limits.enabledModelCount").value(1));

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/config", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandName").value("卡贝"))
                .andExpect(jsonPath("$.data.configGeneration").value(1))
                .andExpect(jsonPath("$.data.summary.topicCount").value(10))
                .andExpect(jsonPath("$.data.summary.enabledPromptCount").value(10))
                .andExpect(jsonPath("$.data.topics[0].name").value("家居五金选购场景"))
                .andExpect(jsonPath("$.data.topics[0].enabled").value(true))
                .andExpect(jsonPath("$.data.prompts[0].text").value("家居五金怎么选，哪些品牌更值得先加入候选？"))
                .andExpect(jsonPath("$.data.prompts[0].enabled").value(true))
                .andExpect(jsonPath("$.data.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.data.capabilities.canUpdateTopic").value(false))
                .andExpect(jsonPath("$.data.capabilities.canUpdatePrompt").value(false))
                .andExpect(jsonPath("$.data.capabilities.canRegenerateSystemConfig").value(true))
                .andExpect(jsonPath("$.data.capabilities.regenerationRecommended").value(false))
                .andExpect(jsonPath("$.data.capabilities.tagTruthObjectSupported").value(false));

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/basic-profile", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandName").value("卡贝"))
                .andExpect(jsonPath("$.data.category").value("家居五金"))
                .andExpect(jsonPath("$.data.officialWebsite").value("https://www.cobbe.com"))
                .andExpect(jsonPath("$.data.competitors[0]").value("九牧"))
                .andExpect(jsonPath("$.data.competitors[1]").value("四季沐歌"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/brand-spaces/{brandSpaceId}/basic-profile", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "brandName", "卡贝卫浴",
                                "category", "卫浴花洒",
                                "officialWebsite", "https://www.kabei-home.com",
                                "competitors", List.of("九牧", "箭牌")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandName").value("卡贝卫浴"))
                .andExpect(jsonPath("$.data.category").value("卫浴花洒"))
                .andExpect(jsonPath("$.data.officialWebsite").value("https://www.kabei-home.com"))
                .andExpect(jsonPath("$.data.competitors[0]").value("九牧"))
                .andExpect(jsonPath("$.data.competitors[1]").value("箭牌"));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.items[0].brandName").value("卡贝卫浴"))
                .andExpect(jsonPath("$.data.items[0].category").value("卫浴花洒"))
                .andExpect(jsonPath("$.data.items[0].officialWebsite").value("https://www.kabei-home.com"));

        Map<String, Object> clearBasicProfilePayload = new HashMap<>();
        clearBasicProfilePayload.put("brandName", "卡贝卫浴");
        clearBasicProfilePayload.put("category", "卫浴花洒");
        clearBasicProfilePayload.put("officialWebsite", null);
        clearBasicProfilePayload.put("competitors", List.of("九牧", "箭牌"));
        clearBasicProfilePayload.put("supplementalBrief", null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/brand-spaces/{brandSpaceId}/basic-profile", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clearBasicProfilePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.brandName").value("卡贝卫浴"))
                .andExpect(jsonPath("$.data.category").value("卫浴花洒"))
                .andExpect(jsonPath("$.data.officialWebsite").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.supplementalBrief").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.competitors[0]").value("九牧"))
                .andExpect(jsonPath("$.data.competitors[1]").value("箭牌"));

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/basic-profile", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.officialWebsite").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.supplementalBrief").value(Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].officialWebsite").value(Matchers.nullValue()));

        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("regionCode", "CN");
        updatePayload.put("locale", "zh-CN");
        updatePayload.put("language", "zh-CN");
        updatePayload.put("topics", List.of(
                Map.of(
                        "tempId", "topic_a",
                        "name", "出租屋花洒选购",
                        "description", "关注低水压和安装门槛",
                        "enabled", true,
                        "source", "user_edited"
                ),
                Map.of(
                        "tempId", "topic_b",
                        "name", "品牌信任线索",
                        "description", "关注官网与第三方口碑",
                        "enabled", true,
                        "source", "system_generated"
                )
        ));
        updatePayload.put("prompts", List.of(
                Map.of(
                        "tempId", "prompt_a",
                        "topicTempId", "topic_a",
                        "text", "出租屋低水压花洒怎么选，卡贝适合吗？",
                        "enabled", true,
                        "source", "user_edited"
                ),
                Map.of(
                        "tempId", "prompt_b",
                        "topicTempId", "topic_b",
                        "text", "卡贝官网与第三方口碑分别提供了哪些决策线索？",
                        "enabled", false,
                        "source", "system_generated"
                )
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/brand-spaces/{brandSpaceId}/config", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configGeneration").value(4))
                .andExpect(jsonPath("$.data.summary.topicCount").value(2))
                .andExpect(jsonPath("$.data.summary.enabledPromptCount").value(1))
                .andExpect(jsonPath("$.data.topics[0].name").value("出租屋花洒选购"))
                .andExpect(jsonPath("$.data.prompts[0].text").value("出租屋低水压花洒怎么选，卡贝适合吗？"))
                .andExpect(jsonPath("$.data.capabilities.canRegenerateSystemConfig").value(false))
                .andExpect(jsonPath("$.data.capabilities.regenerationRecommended").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/brand-spaces/{brandSpaceId}/tracking-mode", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("trackingMode", "PAUSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.trackingStatus").value("PAUSED"))
                .andExpect(jsonPath("$.data.configReady").value(true))
                .andExpect(jsonPath("$.data.activePromptCount").value(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/brand-spaces/{brandSpaceId}/archive", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("USER_SELF_RESTORE"))
                .andExpect(jsonPath("$.data.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.retentionUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.internalRestoreUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.canUserRestore").value(true))
                .andExpect(jsonPath("$.data.canInternalRestore").value(true));

        BrandScope archivedScope = brandScopeMapper.selectById(brandSpaceId);
        assertNotNull(archivedScope);
        assertEquals(0, archivedScope.getScopeStatusCode());
        assertEquals("PAUSED", archivedScope.getTrackingMode());
        assertNotNull(archivedScope.getArchivedAt());
        assertNotNull(archivedScope.getRetentionUntil());
        assertNotNull(archivedScope.getInternalRestoreUntil());

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/brand-spaces/archived")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.items[0].canUserRestore").value(true));

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/config", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("品牌空间已移出当前账号"));

        mockMvc.perform(get("/api/v1/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("品牌空间已移出当前账号"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/brand-spaces/{brandSpaceId}/restore", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.trackingStatus").value("PAUSED"));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldGenerateConcreteKitchenStorageTopicsForPreview() throws Exception {
        AuthContext auth = register();
        mockRecognition("厨房收纳", "https://www.meichu.test", List.of("太力", "茶花"), "美厨物语聚焦厨房收纳与台面整理，用户很在意容量、材质和小户型适配。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "美厨物语");
        previewPayload.put("category", "厨房收纳");
        previewPayload.put("officialWebsite", "https://www.meichu.test");
        previewPayload.put("competitors", List.of("太力", "茶花"));
        previewPayload.put("supplementalBrief", "重点关注小户型、出租屋厨房和台面整洁");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "bs-preview-kitchen-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.topics[6].name").value("容量与空间适配"))
                .andExpect(jsonPath("$.data.topics[7].name").value("材质与耐用度"))
                .andExpect(jsonPath("$.data.prompts").isArray())
                .andExpect(jsonPath("$.data.prompts.length()").value(0));
    }

    @Test
    void shouldCreateBrandSpaceAndReturnBlockedStateWhenCreditsAreInsufficient() throws Exception {
        AuthContext auth = register();
        User user = userMapper.selectById(auth.userId());
        assertNotNull(user);
        user.setCreditsRemaining(0);
        userMapper.updateById(user);

        mockRecognition("新能源混动汽车", "https://www.byd.com", List.of("理想", "问界"), "比亚迪聚焦新能源混动汽车，用户很在意续航、油耗和品牌可靠性。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "比亚迪");
        previewPayload.put("category", "新能源混动汽车");
        previewPayload.put("officialWebsite", "https://www.byd.com");
        previewPayload.put("competitors", List.of("理想", "问界"));
        previewPayload.put("supplementalBrief", "重点关注混动 SUV、家用场景和真实油耗讨论");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "bs-preview-credit-block-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.previewToken").isNotEmpty())
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("prompts", objectMapper.convertValue(previewData.path("prompts"), List.class));
        createPayload.put("idempotencyKey", "bs-create-credit-block-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").isNotEmpty())
                .andExpect(jsonPath("$.data.initialRunId").isEmpty())
                .andExpect(jsonPath("$.data.initialRunStatus").value("blocked"))
                .andExpect(jsonPath("$.data.initialRunBlockCode").value("CREDITS_INSUFFICIENT"))
                .andExpect(jsonPath("$.data.initialRunBlockMessage").value(Matchers.containsString("积分不足")))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.nextView").value("/brands"))
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String brandSpaceId = createData.path("brandSpaceId").asText();

        BrandScope scope = brandScopeMapper.selectById(brandSpaceId);
        assertNotNull(scope);
        assertEquals("比亚迪 / 新能源混动汽车", scope.getScopeName());

        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(
                new LambdaQueryWrapper<BrandSpaceTopic>()
                        .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
        );
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(
                new LambdaQueryWrapper<BrandSpacePrompt>()
                        .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
        );
        assertFalse(topics.isEmpty());
        assertFalse(prompts.isEmpty());

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.items[0].configReady").value(true))
                .andExpect(jsonPath("$.data.items[0].probePlanStatus").value("draft_pending"))
                .andExpect(jsonPath("$.data.items[0].latestRunId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].lastRunAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].lastGeneratedAt").doesNotExist());
    }

    @Test
    void shouldReadRunObserverWhenEventPayloadContainsNullValues() throws Exception {
        AuthContext auth = register();

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "空字段联调品牌");
        previewPayload.put("category", "测试品类");
        previewPayload.put("officialWebsite", "https://null-payload.example.com");
        previewPayload.put("competitors", List.of("竞品A"));
        previewPayload.put("supplementalBrief", "验证运行时间线在事件负载存在空字段时仍可读取");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "run-null-preview-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("prompts", objectMapper.convertValue(previewData.path("prompts"), List.class));
        createPayload.put("idempotencyKey", "run-null-create-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String runId = createData.path("initialRunId").asText();

        Map<String, Object> nullablePayload = new HashMap<>();
        nullablePayload.put("probeId", "probe-null-1");
        nullablePayload.put("renderedPrompt", null);
        nullablePayload.put("renderFamily", null);
        nullablePayload.put("status", null);
        nullablePayload.put("latencyMs", null);
        nullablePayload.put("totalTokenCount", null);
        nullablePayload.put("searchRequested", false);
        nullablePayload.put("searchSatisfied", false);
        nullablePayload.put("searchEvidence", List.of());
        nullablePayload.put("searchQueries", List.of());

        diagnosisEventRecordMapper.insert(DiagnosisEventRecord.builder()
                .seq(Math.abs(System.nanoTime()))
                .diagnosisTaskId(runId)
                .eventType("probe_result")
                .provisional(false)
                .payloadJson(nullablePayload)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.latestEventSeq").isNumber());

        mockMvc.perform(get("/api/v1/runs/{runId}/events", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.events[2].type").value("probe_generated"))
                .andExpect(jsonPath("$.data.events[3].type").value("model_called"));
    }

    @Test
    void shouldPersistEditedTopicsAndPromptsAndBridgeOnlyEnabledPrompts() throws Exception {
        AuthContext auth = register();
        mockRecognition("淋浴花洒", "https://www.wenmu-home.test", List.of("九牧", "箭牌"), "稳沐聚焦花洒场景，用户最关心低水压与安装友好度。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "稳沐");
        previewPayload.put("category", "淋浴花洒");
        previewPayload.put("officialWebsite", "https://www.wenmu-home.test");
        previewPayload.put("competitors", List.of("九牧", "箭牌"));
        previewPayload.put("supplementalBrief", "重点关注出租屋、低水压和安装友好度");
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "bs-preview-edited-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String previewToken = previewData.path("previewToken").asText();
        assertEquals("topic_tmp_1", previewData.path("topics").path(0).path("tempId").asText());

        List<BrandSpaceTopicPayload> topics = List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId(previewData.path("topics").path(0).path("tempId").asText())
                        .name("出租屋花洒选购")
                        .description("关注低水压增压、打孔安装与预算控制")
                        .enabled(true)
                        .source("user_edited")
                        .build(),
                BrandSpaceTopicPayload.builder()
                        .tempId(previewData.path("topics").path(1).path("tempId").asText())
                        .name("九牧与箭牌对比")
                        .description("优先比较耐用度、出水模式和安装复杂度")
                        .enabled(true)
                        .source("manual")
                        .build(),
                BrandSpaceTopicPayload.builder()
                        .tempId(previewData.path("topics").path(2).path("tempId").asText())
                        .name("补充关注点")
                        .description("重点关注出租屋、低水压和安装友好度")
                        .enabled(true)
                        .source("system_generated")
                        .build()
        );
        List<BrandSpacePromptPayload> prompts = List.of(
                BrandSpacePromptPayload.builder()
                        .tempId("prompt_tmp_1")
                        .topicTempId(previewData.path("topics").path(0).path("tempId").asText())
                        .text("出租屋低水压花洒怎么选，稳沐适合吗？")
                        .enabled(true)
                        .source("user_edited")
                        .build(),
                BrandSpacePromptPayload.builder()
                        .tempId("prompt_tmp_2")
                        .topicTempId(previewData.path("topics").path(1).path("tempId").asText())
                        .text("稳沐和九牧花洒谁更适合出租屋？")
                        .enabled(false)
                        .source("manual")
                        .build(),
                BrandSpacePromptPayload.builder()
                        .tempId("prompt_tmp_3")
                        .topicTempId(previewData.path("topics").path(2).path("tempId").asText())
                        .text("安装门槛低、预算友好的花洒里，稳沐值得买吗？")
                        .enabled(true)
                        .source("user_edited")
                        .build()
        );

        Map<String, Object> draft = new HashMap<>(previewPayload);
        draft.put("supplementalBrief", "重点关注租房升级、低水压和安装友好度");

        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewToken);
        createPayload.put("brandSpaceDraft", draft);
        createPayload.put("topics", topics);
        createPayload.put("prompts", prompts);
        createPayload.put("idempotencyKey", "bs-create-edited-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("generating"))
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String brandSpaceId = createData.path("brandSpaceId").asText();
        String runId = createData.path("initialRunId").asText();

        List<BrandSpaceTopic> persistedTopics = brandSpaceTopicMapper.selectList(
                new LambdaQueryWrapper<BrandSpaceTopic>()
                        .eq(BrandSpaceTopic::getBrandScopeId, brandSpaceId)
                        .orderByAsc(BrandSpaceTopic::getSeqNo)
        );
        List<BrandSpacePrompt> persistedPrompts = brandSpacePromptMapper.selectList(
                new LambdaQueryWrapper<BrandSpacePrompt>()
                        .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
                        .orderByAsc(BrandSpacePrompt::getSeqNo)
        );

        assertEquals(3, persistedTopics.size());
        assertEquals(3, persistedPrompts.size());
        assertEquals("出租屋花洒选购", persistedTopics.get(0).getName());
        assertEquals("关注低水压增压、打孔安装与预算控制", persistedTopics.get(0).getDescription());
        assertEquals(1, persistedTopics.get(0).getSourceCode());
        assertEquals("九牧与箭牌对比", persistedTopics.get(1).getName());
        assertEquals("优先比较耐用度、出水模式和安装复杂度", persistedTopics.get(1).getDescription());
        assertEquals(1, persistedTopics.get(1).getSourceCode());

        Map<Integer, BrandSpaceTopic> topicsBySeqNo = persistedTopics.stream()
                .collect(Collectors.toMap(BrandSpaceTopic::getSeqNo, item -> item));

        assertEquals("出租屋花洒选购里，稳沐表现怎么样？", persistedPrompts.get(0).getPromptText());
        assertEquals(0, persistedPrompts.get(0).getSourceCode());
        assertEquals(1, persistedPrompts.get(0).getStatusCode());
        assertEquals(topicsBySeqNo.get(1).getId(), persistedPrompts.get(0).getTopicId());

        assertEquals("九牧与箭牌对比里，稳沐表现怎么样？", persistedPrompts.get(1).getPromptText());
        assertEquals(0, persistedPrompts.get(1).getSourceCode());
        assertEquals(1, persistedPrompts.get(1).getStatusCode());
        assertEquals(topicsBySeqNo.get(2).getId(), persistedPrompts.get(1).getTopicId());

        assertEquals("补充关注点里，稳沐表现怎么样？", persistedPrompts.get(2).getPromptText());
        assertEquals(0, persistedPrompts.get(2).getSourceCode());
        assertEquals(1, persistedPrompts.get(2).getStatusCode());
        assertEquals(persistedTopics.get(2).getId(), persistedPrompts.get(2).getTopicId());

        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        assertNotNull(task);
        assertEquals(brandSpaceId, task.getBrandScopeId());
        assertEquals(24, task.getTargetProbeCount());
        Map<String, Object> taskOrderJson = task.getTaskOrderJson();
        assertNotNull(taskOrderJson);

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrderJson.get("businessHints");
        assertNotNull(businessHints);

        String userPrompt = (String) businessHints.get("userPrompt");
        assertTrue(userPrompt.contains("重点关注租房升级、低水压和安装友好度"));
        assertTrue(userPrompt.contains("首轮优先跟踪的主题：出租屋花洒选购、九牧与箭牌对比、补充关注点"));
        assertFalse(userPrompt.contains("出租屋低水压花洒怎么选，稳沐适合吗？"));
        assertFalse(userPrompt.contains("安装门槛低、预算友好的花洒里，稳沐值得买吗？"));
        assertFalse(userPrompt.contains("稳沐和九牧花洒谁更适合出租屋？"));

        assertFalse(businessHints.containsKey("brandSpacePromptSnapshot"));

        @SuppressWarnings("unchecked")
        Map<String, Object> topicPlan = (Map<String, Object>) businessHints.get("topicPlan");
        assertNotNull(topicPlan);
        assertEquals("brand_space_schedule_plan", topicPlan.get("topicSelectionMode"));
        assertEquals("brand_space_schedule_truth", topicPlan.get("coverageSource"));
        assertEquals(
                List.of("出租屋花洒选购", "九牧与箭牌对比", "补充关注点"),
                topicPlan.get("enabledTopicNames")
        );
        assertEquals(3, topicPlan.get("enabledTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> scheduleDecision = (Map<String, Object>) businessHints.get("scheduleDecision");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionPrompts = (List<Map<String, Object>>) scheduleDecision.get("executionPrompts");
        assertNotNull(executionPrompts);
        assertEquals(3, executionPrompts.size());
        assertEquals(persistedPrompts.get(0).getId(), executionPrompts.get(0).get("promptId"));
        assertEquals(topicsBySeqNo.get(1).getId(), executionPrompts.get(0).get("topicId"));
        assertEquals("出租屋花洒选购", executionPrompts.get(0).get("topicName"));
        assertEquals("出租屋花洒选购里，稳沐表现怎么样？", executionPrompts.get(0).get("promptText"));
        assertEquals(persistedPrompts.get(1).getId(), executionPrompts.get(1).get("promptId"));
        assertEquals(topicsBySeqNo.get(2).getId(), executionPrompts.get(1).get("topicId"));
        assertEquals("九牧与箭牌对比里，稳沐表现怎么样？", executionPrompts.get(1).get("promptText"));
        assertEquals(persistedPrompts.get(2).getId(), executionPrompts.get(2).get("promptId"));
        assertEquals(topicsBySeqNo.get(3).getId(), executionPrompts.get(2).get("topicId"));
        assertEquals("补充关注点里，稳沐表现怎么样？", executionPrompts.get(2).get("promptText"));

        @SuppressWarnings("unchecked")
        Map<String, Object> taskMetadata = (Map<String, Object>) taskOrderJson.get("taskMetadata");
        assertNotNull(taskMetadata);
        @SuppressWarnings("unchecked")
        Map<String, Object> runVersionTruth =
                (Map<String, Object>) taskMetadata.get(LightConeV43VersionTruthSupport.RUN_VERSION_TRUTH_KEY);
        assertNotNull(runVersionTruth);
        assertEquals("canonical", runVersionTruth.get("migrationMode"));
        assertEquals("canonicalExecutionLane", runVersionTruth.get("executionLane"));
        assertEquals("light_cone_4_3", runVersionTruth.get("engineRuntimeVersion"));

        assertNull(createData.path("diagnosisId").textValue());

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/config", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandSpaceId").value(brandSpaceId))
                .andExpect(jsonPath("$.data.configGeneration").value(1))
                .andExpect(jsonPath("$.data.summary.topicCount").value(3))
                .andExpect(jsonPath("$.data.summary.enabledTopicCount").value(3))
                .andExpect(jsonPath("$.data.summary.promptCount").value(3))
                .andExpect(jsonPath("$.data.summary.enabledPromptCount").value(3))
                .andExpect(jsonPath("$.data.topics[0].name").value("出租屋花洒选购"))
                .andExpect(jsonPath("$.data.topics[0].source").value("user_edited"))
                .andExpect(jsonPath("$.data.topics[0].promptCount").value(1))
                .andExpect(jsonPath("$.data.prompts[1].text").value("九牧与箭牌对比里，稳沐表现怎么样？"))
                .andExpect(jsonPath("$.data.prompts[1].enabled").value(true))
                .andExpect(jsonPath("$.data.prompts[1].source").value("system_generated"))
                .andExpect(jsonPath("$.data.prompts[1].topicName").value("九牧与箭牌对比"))
                .andExpect(jsonPath("$.data.capabilities.canCreateTopic").value(false))
                .andExpect(jsonPath("$.data.capabilities.canCreatePrompt").value(false))
                .andExpect(jsonPath("$.data.capabilities.canReorderTopic").value(false))
                .andExpect(jsonPath("$.data.capabilities.canReorderPrompt").value(false))
                .andExpect(jsonPath("$.data.capabilities.canRegenerateSystemConfig").value(false))
                .andExpect(jsonPath("$.data.capabilities.regenerationRecommended").value(false));
    }

    @Test
    void shouldRegenerateLegacySystemGeneratedConfig() throws Exception {
        AuthContext auth = register();
        mockRecognition("厨房收纳", "https://example.com", List.of("YWEEL"), "用于验证旧版 prompt 回填。");

        Map<String, Object> previewPayload = new HashMap<>();
        previewPayload.put("brandName", "美厨物语");
        previewPayload.put("category", "厨房收纳");
        previewPayload.put("officialWebsite", "https://example.com");
        previewPayload.put("competitors", List.of("YWEEL"));
        previewPayload.put("regionCode", "CN");
        previewPayload.put("locale", "zh-CN");
        previewPayload.put("language", "zh-CN");
        previewPayload.put("idempotencyKey", "bs-preview-regenerate-" + System.nanoTime());

        MvcResult previewResult = mockMvc.perform(post("/api/v1/brand-spaces/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewPayload)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");

        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("previewToken", previewData.path("previewToken").asText());
        createPayload.put("brandSpaceDraft", objectMapper.convertValue(previewData.path("brandSpaceDraft"), Map.class));
        createPayload.put("topics", objectMapper.convertValue(previewData.path("topics"), List.class));
        createPayload.put("prompts", objectMapper.convertValue(previewData.path("prompts"), List.class));
        createPayload.put("idempotencyKey", "bs-create-regenerate-" + System.nanoTime());

        MvcResult createResult = mockMvc.perform(post("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andReturn();
        String brandSpaceId = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("brandSpaceId").asText();

        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(
                new LambdaQueryWrapper<BrandSpacePrompt>()
                        .eq(BrandSpacePrompt::getBrandScopeId, brandSpaceId)
                        .orderByAsc(BrandSpacePrompt::getSeqNo)
        );
        assertEquals(10, prompts.size());
        List<String> legacyTexts = List.of(
                "厨房收纳怎么选，美厨物语值不值得买？",
                "美厨物语和YWEEL哪个好？",
                "美厨物语的官网和第三方口碑靠谱吗？",
                "美厨物语的真实口碑怎么样，最常见的优缺点是什么？",
                "美厨物语贵不贵，厨房收纳里它的性价比怎么样？",
                "什么人、什么使用场景更适合选美厨物语？",
                "美厨物语最值得强调的卖点是什么，它和YWEEL差在哪？",
                "用户在买厨房收纳时最怕踩哪些坑，美厨物语能避开吗？",
                "如果不指定品牌，AI 会推荐美厨物语还是其他厨房收纳品牌？",
                "用户在考虑美厨物语时，最常见的顾虑和劝退点是什么？"
        );
        for (int index = 0; index < prompts.size(); index++) {
            BrandSpacePrompt prompt = prompts.get(index);
            prompt.setPromptText(legacyTexts.get(index));
            brandSpacePromptMapper.updateById(prompt);
        }

        mockMvc.perform(get("/api/v1/brand-spaces/{brandSpaceId}/config", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities.canRegenerateSystemConfig").value(true))
                .andExpect(jsonPath("$.data.capabilities.regenerationRecommended").value(true))
                .andExpect(jsonPath("$.data.prompts[0].text").value("厨房收纳怎么选，美厨物语值不值得买？"));

        mockMvc.perform(post("/api/v1/brand-spaces/{brandSpaceId}/config/regenerate-system-generated", brandSpaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configGeneration").value(2))
                .andExpect(jsonPath("$.data.capabilities.canRegenerateSystemConfig").value(true))
                .andExpect(jsonPath("$.data.capabilities.regenerationRecommended").value(false))
                .andExpect(jsonPath("$.data.prompts[0].text").value("厨房收纳怎么选，哪些品牌更值得先加入候选？"))
                .andExpect(jsonPath("$.data.prompts[1].text").value("美厨物语和YWEEL相比，谁更适合大多数厨房收纳用户？"));
    }

    @Test
    void shouldExposeDailyUsageFromCreditCompatibilityBridge() throws Exception {
        AuthContext auth = register();

        creditTransactionMapper.insert(CreditTransaction.builder()
                .userId(auth.userId())
                .type(2)
                .amount(-2)
                .diagnosisId("diag-" + System.nanoTime())
                .createdAt(LocalDateTime.now())
                .build());
        creditTransactionMapper.insert(CreditTransaction.builder()
                .userId(auth.userId())
                .type(2)
                .amount(-1)
                .diagnosisId("diag-old-" + System.nanoTime())
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());

        mockMvc.perform(get("/api/v1/usage/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.date").isNotEmpty())
                .andExpect(jsonPath("$.data.consumedCredits").value(2))
                .andExpect(jsonPath("$.data.creditsRemaining").isNumber());
    }

    @Test
    void shouldAggregateDailyUsageAcrossSharedOrganizationMembers() throws Exception {
        AuthContext owner = register();
        AuthContext member = register();
        Organization sharedOrganization = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOwnerUserId, owner.userId())
                .last("limit 1"));

        userMapper.updateDefaultOrganizationId(member.userId(), sharedOrganization.getId());
        organizationMembershipMapper.insert(OrganizationMembership.builder()
                .id(UUID.randomUUID().toString())
                .organizationId(sharedOrganization.getId())
                .userId(member.userId())
                .roleCode(0)
                .seatTypeCode(0)
                .statusCode(1)
                .invitedByUserId(owner.userId())
                .invitedAt(LocalDateTime.now())
                .joinedAt(LocalDateTime.now())
                .build());

        creditTransactionMapper.insert(CreditTransaction.builder()
                .userId(owner.userId())
                .type(2)
                .amount(-2)
                .diagnosisId("diag-owner-" + System.nanoTime())
                .createdAt(LocalDateTime.now())
                .build());
        creditTransactionMapper.insert(CreditTransaction.builder()
                .userId(member.userId())
                .type(2)
                .amount(-3)
                .diagnosisId("diag-member-" + System.nanoTime())
                .createdAt(LocalDateTime.now())
                .build());
        creditTransactionMapper.insert(CreditTransaction.builder()
                .userId(member.userId())
                .type(2)
                .amount(-1)
                .diagnosisId("diag-member-old-" + System.nanoTime())
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());

        mockMvc.perform(get("/api/v1/usage/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.consumedCredits").value(5))
                .andExpect(jsonPath("$.data.creditsRemaining").isNumber());

        mockMvc.perform(get("/api/v1/usage/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.consumedCredits").value(5))
                .andExpect(jsonPath("$.data.creditsRemaining").isNumber());
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
}
