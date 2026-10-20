package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.LlmRunAdmissionBlockedException;
import com.geoprobe.dto.brandspace.BrandSpaceArchiveView;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpacePreviewResponse;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.dto.brandspace.BrandSpaceScheduleExplanationView;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import com.geoprobe.dto.request.BrandSpaceCreateRequest;
import com.geoprobe.dto.request.BrandSpaceConfigUpdateRequest;
import com.geoprobe.dto.request.BrandSpacePreviewRequest;
import com.geoprobe.dto.request.CreateDiagnosisRequest;
import com.geoprobe.dto.response.DiagnosisResponse;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.Organization;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetHistorySyncService;
import com.geoprobe.knowledge.service.KnowledgeAssetScheduleStateMaterializationService;
import com.geoprobe.service.shared.DiagnosisIdempotencyService;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceServiceTest {

    @Mock
    private DiagnosisService diagnosisService;
    @Mock
    private FrontDeskPreviewTokenService frontDeskPreviewTokenService;
    @Mock
    private V42TaskOrderExecutionPolicyResolver executionPolicyResolver;
    @Mock
    private ServiceAccessService serviceAccessService;
    @Mock
    private BrandSpaceExecutionGateService brandSpaceExecutionGateService;
    @Mock
    private BrandSpaceRecognitionService brandSpaceRecognitionService;
    @Mock
    private BrandSpaceConfigService brandSpaceConfigService;
    @Mock
    private ProbePlanService probePlanService;
    @Mock
    private LegacyCreditMirrorService legacyCreditMirrorService;
    @Mock
    private BrandSpaceSchedulingService brandSpaceSchedulingService;
    @Mock
    private BrandSpaceRunConfigService brandSpaceRunConfigService;
    @Mock
    private BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    @Mock
    private DiagnosisTenantContextService diagnosisTenantContextService;
    @Mock
    private DiagnosisIdempotencyService diagnosisIdempotencyService;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    @Mock
    private DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    @Mock
    private ObjectProvider<KnowledgeAssetHistorySyncService> knowledgeAssetHistorySyncServiceProvider;
    @Mock
    private ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    @Mock
    private ObjectProvider<KnowledgeAssetEvidenceRepository> knowledgeAssetEvidenceRepositoryProvider;
    @Mock
    private ObjectProvider<KnowledgeAssetScheduleStateMaterializationService> knowledgeAssetScheduleStateMaterializationServiceProvider;
    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private BrandSpaceService brandSpaceService;

    @BeforeEach
    void setUp() {
        brandSpaceService = new BrandSpaceService(
                diagnosisService,
                frontDeskPreviewTokenService,
                executionPolicyResolver,
                serviceAccessService,
                brandSpaceExecutionGateService,
                brandSpaceRecognitionService,
                brandSpaceConfigService,
                probePlanService,
                legacyCreditMirrorService,
                brandSpaceSchedulingService,
                brandSpaceRunConfigService,
                brandSpaceRunCreditPolicy,
                diagnosisTenantContextService,
                diagnosisIdempotencyService,
                diagnosisTaskMapper,
                diagnosisReportRecordMapper,
                diagnosisProbeRecordMapper,
                brandScopeMapper,
                brandMapper,
                probeBrandMentionRecordMapper,
                objectMapper,
                knowledgeAssetHistorySyncServiceProvider,
                knowledgeAssetRunRepositoryProvider,
                knowledgeAssetEvidenceRepositoryProvider,
                knowledgeAssetScheduleStateMaterializationServiceProvider
        );
    }

    @Test
    void createShouldPersistBrandSpaceWhenInitialRunAdmissionBlocked() {
        BrandSpaceCreateRequest request = buildCreateRequest();
        FrontDeskPreviewTokenService.StoredPreviewSnapshot previewSnapshot =
                new FrontDeskPreviewTokenService.StoredPreviewSnapshot(
                        "user-1",
                        12,
                        Map.of("brandName", "比亚迪", "category", "新能源混动汽车", "businessHints", Map.of()),
                        Map.of(),
                        Map.of(),
                        LocalDateTime.now().toString()
                );
        ServiceAccessView serviceAccess = ServiceAccessView.builder()
                .status("active")
                .limits(ServiceAccessView.Limits.builder()
                        .creditsPerProbe(1)
                        .build())
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .organizationId("org-1")
                .brandName("比亚迪")
                .categoryName("新能源混动汽车")
                .build();
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("比亚迪 / 新能源混动汽车")
                .categorySnapshot("新能源混动汽车")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .build();

        when(diagnosisIdempotencyService.findIdempotentDiagnosis("user-1", "brand-create-1"))
                .thenReturn(null);
        when(frontDeskPreviewTokenService.load("user-1", "preview-token"))
                .thenReturn(previewSnapshot);
        when(brandSpaceExecutionGateService.requireCreateAuthority("user-1"))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", serviceAccess));
        when(brandSpaceSchedulingService.planCreateInitialRun(any(), any(), eq(serviceAccess), eq(12)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "create_initial_run",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "口碑认知", 12, 0.9d, List.of("cold_start"))),
                        List.of(),
                        "cold_start_wide_coverage",
                        "首次创建，优先完成宽覆盖首跑",
                        true,
                        false,
                        false,
                        100,
                        1,
                        12,
                        12,
                        1,
                        0,
                        1,
                        0.0d
                ));
        when(brandSpaceRunCreditPolicy.quote("user-1", 12, serviceAccess))
                .thenReturn(new BrandSpaceRunCreditPolicy.CreditQuote(12, 1, 12, 1000, 988, true));
        when(diagnosisTenantContextService.ensureBrandTenancy("user-1", "比亚迪", "新能源混动汽车", "https://byd.example.com"))
                .thenReturn(new DiagnosisTenantContextService.TenantContext(
                        null,
                        Organization.builder().id("org-1").build(),
                        null,
                        brand,
                        scope,
                        null
                ));
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(diagnosisService.createDiagnosis(eq("user-1"), any()))
                .thenThrow(new LlmRunAdmissionBlockedException("doubao", "doubao-seed", "snapshot stale"));

        var response = brandSpaceService.create("user-1", request);

        assertEquals("scope-1", response.getBrandSpaceId());
        assertEquals("brand-1", response.getBrandId());
        assertEquals("blocked", response.getInitialRunStatus());
        assertEquals("RUN_ADMISSION_BLOCKED", response.getInitialRunBlockCode());
        assertNull(response.getInitialRunId());
        assertTrue(response.getInitialRunBlockMessage().contains("品牌空间已创建"));
        verify(brandSpaceConfigService).replaceConfig(eq("org-1"), eq("brand-1"), eq("scope-1"), any(), any(), any());
        verify(brandSpaceRunConfigService).saveCurrentRunConfig("scope-1", 12, "doubao", true, 12);
        verify(probePlanService, never()).snapshotInitialConfirmedPlan(any());
        verify(probePlanService, never()).snapshotInitialConfirmedPlanOnce(any());
        verify(legacyCreditMirrorService, never()).attachDiagnosisTaskAttribution(any());
    }

    @Test
    void createShouldPersistBrandSpaceWhenCreditsAreInsufficient() {
        BrandSpaceCreateRequest request = buildCreateRequest();
        FrontDeskPreviewTokenService.StoredPreviewSnapshot previewSnapshot =
                new FrontDeskPreviewTokenService.StoredPreviewSnapshot(
                        "user-1",
                        12,
                        Map.of("brandName", "比亚迪", "category", "新能源混动汽车", "businessHints", Map.of()),
                        Map.of(),
                        Map.of(),
                        LocalDateTime.now().toString()
                );
        ServiceAccessView serviceAccess = ServiceAccessView.builder()
                .status("active")
                .limits(ServiceAccessView.Limits.builder()
                        .creditsPerProbe(1)
                        .build())
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .organizationId("org-1")
                .brandName("比亚迪")
                .categoryName("新能源混动汽车")
                .build();
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("比亚迪 / 新能源混动汽车")
                .categorySnapshot("新能源混动汽车")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .build();

        when(diagnosisIdempotencyService.findIdempotentDiagnosis("user-1", "brand-create-1"))
                .thenReturn(null);
        when(frontDeskPreviewTokenService.load("user-1", "preview-token"))
                .thenReturn(previewSnapshot);
        when(brandSpaceExecutionGateService.requireCreateAuthority("user-1"))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", serviceAccess));
        when(brandSpaceSchedulingService.planCreateInitialRun(any(), any(), eq(serviceAccess), eq(12)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "create_initial_run",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "口碑认知", 12, 0.9d, List.of("cold_start"))),
                        List.of(),
                        "cold_start_wide_coverage",
                        "首次创建，优先完成宽覆盖首跑",
                        true,
                        false,
                        false,
                        100,
                        1,
                        12,
                        12,
                        1,
                        0,
                        1,
                        0.0d
                ));
        when(brandSpaceRunCreditPolicy.quote("user-1", 12, serviceAccess))
                .thenReturn(new BrandSpaceRunCreditPolicy.CreditQuote(12, 1, 12, 0, 0, false));
        when(diagnosisTenantContextService.ensureBrandTenancy("user-1", "比亚迪", "新能源混动汽车", "https://byd.example.com"))
                .thenReturn(new DiagnosisTenantContextService.TenantContext(
                        null,
                        Organization.builder().id("org-1").build(),
                        null,
                        brand,
                        scope,
                        null
                ));
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);

        var response = brandSpaceService.create("user-1", request);

        assertEquals("scope-1", response.getBrandSpaceId());
        assertEquals("blocked", response.getInitialRunStatus());
        assertEquals("CREDITS_INSUFFICIENT", response.getInitialRunBlockCode());
        assertTrue(response.getInitialRunBlockMessage().contains("积分不足"));
        verify(diagnosisService, never()).createDiagnosis(eq("user-1"), any());
        verify(brandSpaceConfigService).replaceConfig(eq("org-1"), eq("brand-1"), eq("scope-1"), any(), any(), any());
    }

    private BrandSpaceCreateRequest buildCreateRequest() {
        BrandSpaceCreateRequest request = new BrandSpaceCreateRequest();
        request.setPreviewToken("preview-token");
        request.setIdempotencyKey("brand-create-1");
        request.setExecuteModeCode("RECURRING");
        request.setTargetModelProduct("doubao");
        request.setTargetProbeCount(12);
        request.setBrandSpaceDraft(com.geoprobe.dto.brandspace.BrandSpaceDraftPayload.builder()
                .brandName("比亚迪")
                .category("新能源混动汽车")
                .officialWebsite("https://byd.example.com")
                .competitors(List.of("理想", "问界"))
                .supplementalBrief("关注混动 SUV 的口碑和价格讨论")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .build());
        request.setTopics(List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic-1")
                        .name("口碑认知")
                        .description("关注真实用户对品牌和车型的评价。")
                        .enabled(true)
                        .source("system")
                        .build()
        ));
        request.setPrompts(List.of(
                BrandSpacePromptPayload.builder()
                        .tempId("prompt-1")
                        .topicTempId("topic-1")
                        .text("比亚迪在新能源混动汽车里口碑如何？")
                        .enabled(true)
                        .source("system")
                        .build()
        ));
        return request;
    }

    @Test
    void previewShouldAllowMissingWebsiteAndIdempotencyKey() {
        BrandSpacePreviewRequest request = new BrandSpacePreviewRequest();
        request.setBrandName("卫龙");
        request.setCategory("辣条");
        request.setCompetitors(List.of("麻辣王子"));

        when(brandSpaceRecognitionService.recognize(any()))
                .thenReturn(BrandSpaceRecognitionView.builder()
                        .suggestedCategory("休闲食品")
                        .officialWebsite("https://www.weilongshipin.com")
                        .competitors(List.of("麻辣王子"))
                        .brandSummary("卫龙是中国辣味休闲食品品牌。")
                        .source("doubao_web_search")
                        .build());

        when(diagnosisService.previewTaskOrder(eq("user-1"), any(CreateDiagnosisRequest.class)))
                .thenReturn(new DiagnosisService.TaskOrderPreview(
                        20,
                        Map.of("taskMetadata", Map.of("channel", "brand_space_preview")),
                        Map.of(),
                        Map.of(),
                        "preview-token"
                ));
        when(brandSpaceConfigService.resolvePreviewTopics(any(), any()))
                .thenReturn(List.of());
        when(executionPolicyResolver.resolveRealtimeSearchPolicyFromTaskOrderSnapshot(any()))
                .thenReturn(RealtimeSearchPolicy.disabled());
        ServiceAccessView previewAccess = ServiceAccessView.builder()
                .status("active")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
        when(brandSpaceExecutionGateService.requirePreviewAuthority("user-1"))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", previewAccess));
        when(brandSpaceSchedulingService.planPreview(any(), any(), eq(previewAccess)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "interactive_preview",
                        12,
                        List.of(
                                new BrandSpaceSchedulingService.TopicDecision("topic-1", "默认主题一", 6, 0.7d, List.of("冷启动")),
                                new BrandSpaceSchedulingService.TopicDecision("topic-2", "默认主题二", 6, 0.6d, List.of("补债"))
                        ),
                        List.of(),
                        "cold_start_wide_coverage",
                        "今天是新品牌首跑，优先做宽覆盖",
                        true,
                        false,
                        false,
                        50,
                        1,
                        12,
                        24,
                        2,
                        0,
                        2,
                        0.0d
                ));

        BrandSpacePreviewResponse response = brandSpaceService.preview("user-1", request);

        assertNotNull(response);
        assertEquals("preview-token", response.getPreviewToken());
        assertEquals("休闲食品", response.getRecognition().getSuggestedCategory());

        ArgumentCaptor<CreateDiagnosisRequest> requestCaptor = ArgumentCaptor.forClass(CreateDiagnosisRequest.class);
        verify(diagnosisService).previewTaskOrder(eq("user-1"), requestCaptor.capture());

        CreateDiagnosisRequest diagnosisRequest = requestCaptor.getValue();
        assertNull(diagnosisRequest.getRequestId());

        @SuppressWarnings("unchecked")
        Map<String, Object> taskOrder = (Map<String, Object>) diagnosisRequest.getFrontDeskTaskOrder();
        assertEquals("卫龙", taskOrder.get("brandName"));
        assertEquals("休闲食品", taskOrder.get("category"));
        assertEquals("https://www.weilongshipin.com", taskOrder.get("brandWebsite"));

        @SuppressWarnings("unchecked")
        Map<String, Object> taskMetadata = (Map<String, Object>) taskOrder.get("taskMetadata");
        assertEquals("brand_space_preview", taskMetadata.get("channel"));
        assertEquals("brand_space", taskMetadata.get("origin"));
        assertEquals("draft_preview", taskMetadata.get("lifecycleStage"));
        assertEquals("light_cone_task_order_v4_3", taskMetadata.get("taskOrderContractVersion"));
        assertFalse(taskMetadata.containsKey("requestId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runVersionTruth = (Map<String, Object>) taskMetadata.get("lightConeRunVersionTruth");
        assertEquals("canonical", runVersionTruth.get("migrationMode"));
        assertEquals("light_cone_4_3", runVersionTruth.get("engineRuntimeVersion"));
        assertEquals("canonicalExecutionLane", runVersionTruth.get("executionLane"));
        assertEquals("doubao", taskOrder.get("targetModelProduct"));
        assertTrue(taskOrder.containsKey("businessHints"));
        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        @SuppressWarnings("unchecked")
        Map<String, Object> sampling = (Map<String, Object>) businessHints.get("sampling");
        assertEquals(12, sampling.get("targetProbeCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) businessHints.get("samplingRealtimeSearch");
        assertEquals(true, realtimeSearch.get("enabled"));
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) businessHints.get("samplingEngine");
        assertEquals("doubao", samplingEngine.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> topicPlan = (Map<String, Object>) businessHints.get("topicPlan");
        assertEquals(12, topicPlan.get("requestedProbeCount"));
        assertEquals("brand_space_schedule_plan", topicPlan.get("topicSelectionMode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schedulePolicy = (Map<String, Object>) businessHints.get("schedulePolicy");
        assertEquals("preview_planning", schedulePolicy.get("mode"));
        assertEquals(true, schedulePolicy.get("realtimeEvidenceRequired"));
        assertEquals(12, schedulePolicy.get("targetProbeCount"));
        assertEquals(50, schedulePolicy.get("accountAvailableCredits"));
        assertEquals(12, schedulePolicy.get("brandCoreProbeCount"));
        assertEquals(24, schedulePolicy.get("brandExpansionProbeCount"));
        assertEquals(2, schedulePolicy.get("weeklyTargetTopicCount"));
        assertEquals(2, schedulePolicy.get("weeklyGapTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> brandSpaceContext = (Map<String, Object>) businessHints.get("brandSpaceContext");
        assertEquals("brand_space", brandSpaceContext.get("origin"));
        assertEquals("draft_preview", brandSpaceContext.get("lifecycleStage"));
        assertEquals(12, brandSpaceContext.get("requestedProbeCount"));
        assertEquals(50, brandSpaceContext.get("accountAvailableCredits"));
        assertEquals(2, brandSpaceContext.get("weeklyTargetTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceAccess = (Map<String, Object>) brandSpaceContext.get("serviceAccess");
        assertEquals("active", serviceAccess.get("status"));
        assertEquals(50, serviceAccess.get("promptLimitPerBrandSpace"));
    }

    @Test
    void previewShouldReuseConfirmedRecognitionAndRequestedTopics() {
        BrandSpacePreviewRequest request = new BrandSpacePreviewRequest();
        request.setBrandName("探镜");
        request.setCategory("AI 可见性");
        request.setOfficialWebsite("https://tanjing.example.com");
        request.setRecognition(BrandSpaceRecognitionView.builder()
                .suggestedCategory("AI 可见性平台")
                .officialWebsite("https://tanjing.example.com")
                .competitors(List.of("Profound", "Peec AI"))
                .brandSummary("探镜聚焦 AI 可见性与品牌问题空间跟踪。")
                .source("doubao_web_search")
                .build());
        request.setTopics(List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic_manual_1")
                        .name("渠道评价")
                        .description("观察 AI 是否提到渠道口碑与合作体验。")
                        .enabled(true)
                        .source("manual")
                        .build()
        ));

        List<BrandSpaceTopicPayload> resolvedTopics = List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic_manual_1")
                        .name("渠道评价")
                        .description("观察 AI 是否提到渠道口碑与合作体验。")
                        .enabled(true)
                        .source("manual")
                        .build()
        );
        when(diagnosisService.previewTaskOrder(eq("user-2"), any(CreateDiagnosisRequest.class)))
                .thenReturn(new DiagnosisService.TaskOrderPreview(
                        12,
                        Map.of("taskMetadata", Map.of("channel", "brand_space_preview")),
                        Map.of(),
                        Map.of(),
                        "preview-token-2"
                ));
        when(brandSpaceConfigService.resolvePreviewTopics(any(), any()))
                .thenReturn(resolvedTopics);
        when(executionPolicyResolver.resolveRealtimeSearchPolicyFromTaskOrderSnapshot(any()))
                .thenReturn(RealtimeSearchPolicy.disabled());
        ServiceAccessView previewAccess = ServiceAccessView.builder().status("active").build();
        when(brandSpaceExecutionGateService.requirePreviewAuthority("user-2"))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-2", "org-2", "membership-2", previewAccess));
        when(brandSpaceSchedulingService.planPreview(any(), any(), eq(previewAccess)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "interactive_preview",
                        10,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic_manual_1", "渠道评价", 10, 0.95d, List.of("冷启动"))),
                        List.of(),
                        "cold_start_wide_coverage",
                        "今天是新品牌首跑，优先做宽覆盖",
                        true,
                        false,
                        false,
                        50,
                        1,
                        10,
                        20,
                        1,
                        0,
                        1,
                        0.0d
                ));

        BrandSpacePreviewResponse response = brandSpaceService.preview("user-2", request);

        assertNotNull(response);
        assertEquals("preview-token-2", response.getPreviewToken());
        assertEquals("AI 可见性平台", response.getRecognition().getSuggestedCategory());
        assertEquals(1, response.getTopics().size());
        assertEquals("渠道评价", response.getTopics().getFirst().getName());
        assertTrue(response.getPrompts() == null || response.getPrompts().isEmpty());

        verify(brandSpaceRecognitionService, never()).recognize(any());
    }

    @Test
    void updateConfigShouldRefreshScheduleStateImmediately() {
        KnowledgeAssetScheduleStateMaterializationService materializationService =
                org.mockito.Mockito.mock(KnowledgeAssetScheduleStateMaterializationService.class);
        BrandSpaceConfigUpdateRequest request = new BrandSpaceConfigUpdateRequest();
        request.setRegionCode("CN");
        request.setLocale("zh-CN");
        request.setLanguage("zh-CN");
        request.setTargetProbeCount(17);
        request.setTargetModelProduct("deepseek");
        request.setAutoRunEnabled(false);
        request.setTopics(List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic-1")
                        .name("品牌选择")
                        .enabled(true)
                        .build()
        ));
        request.setPrompts(List.of(
                BrandSpacePromptPayload.builder()
                        .tempId("prompt-1")
                        .topicTempId("topic-1")
                        .text("品牌选择 prompt")
                        .enabled(true)
                        .build()
        ));

        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("美厨物语")
                .categorySnapshot("厨房收纳")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(3)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichu.com")
                .build();
        BrandScope refreshedScope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("美厨物语")
                .categorySnapshot("厨房收纳")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(5)
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope, scope, refreshedScope);
        when(brandMapper.selectById("brand-1")).thenReturn(brand);
        when(knowledgeAssetScheduleStateMaterializationServiceProvider.getIfAvailable()).thenReturn(materializationService);
        when(brandSpaceConfigService.loadConfigView(eq("scope-1"), eq("brand-1"), eq("美厨物语"), anyInt()))
                .thenReturn(BrandSpaceConfigView.builder()
                        .brandSpaceId("scope-1")
                        .brandId("brand-1")
                        .brandName("美厨物语")
                        .configGeneration(5)
                        .topics(List.of())
                        .prompts(List.of())
                        .runConfig(BrandSpaceConfigView.RunConfigView.builder()
                                .targetProbeCount(17)
                                .targetModelProduct("deepseek")
                                .autoRunEnabled(false)
                                .build())
                        .summary(BrandSpaceConfigView.SummaryView.builder()
                                .enabledTopicCount(1)
                                .enabledPromptCount(1)
                                .build())
                        .build());

        BrandSpaceConfigView response = brandSpaceService.updateConfig("user-1", "scope-1", request);

        assertNotNull(response);
        assertNotNull(response.getRunConfig());
        assertEquals(17, response.getRunConfig().getTargetProbeCount());
        assertEquals("deepseek", response.getRunConfig().getTargetModelProduct());
        assertFalse(response.getRunConfig().getAutoRunEnabled());
        verify(brandSpaceRunConfigService).saveCurrentRunConfig("scope-1", 17, "deepseek", false, 1);
        verify(materializationService).materializeScope("scope-1");
    }

    @Test
    void getScheduleExplanationShouldExposeLatestPlanAndBlockedTopics() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("美厨物语")
                .trackingMode("ACTIVE")
                .configGeneration(3)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("run-1")
                .brandScopeId("scope-1")
                .createdAt(LocalDateTime.of(2026, 5, 8, 18, 40))
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "scheduleDecision", Map.ofEntries(
                                        Map.entry("triggerMode", "scheduled_tracking"),
                                        Map.entry("targetProbeCount", 14),
                                        Map.entry("coverageStrategy", "balanced_weekly_coverage"),
                                        Map.entry("humanReason", "今天优先补齐最近一周未覆盖主题。"),
                                        Map.entry("selectedTopics", List.of(
                                                Map.of(
                                                        "topicId", "topic-1",
                                                        "topicName", "品牌选择",
                                                        "allocatedProbeCount", 8,
                                                        "priorityScore", 0.95d,
                                                        "reasonTags", List.of("补覆盖", "高优先级")
                                                )
                                        )),
                                        Map.entry("deferredTopics", List.of(
                                                Map.of(
                                                        "topicId", "topic-2",
                                                        "topicName", "渠道评价",
                                                        "allocatedProbeCount", 0,
                                                        "priorityScore", 0.42d,
                                                        "reasonTags", List.of("今日额度不足")
                                                )
                                        )),
                                        Map.entry("accountAvailableCredits", 26),
                                        Map.entry("accountBrandCount", 2),
                                        Map.entry("brandCoreProbeCount", 14),
                                        Map.entry("brandExpansionProbeCount", 28),
                                        Map.entry("weeklyTargetTopicCount", 4),
                                        Map.entry("weeklyCoveredTopicCount", 2),
                                        Map.entry("weeklyGapTopicCount", 2),
                                        Map.entry("weeklyCoverageRatio", 0.5d)
                                )
                        )
                ))
                .build();
        DiagnosisReportRecord latestReport = DiagnosisReportRecord.builder()
                .brandScopeId("scope-1")
                .generatedAt(LocalDateTime.of(2026, 5, 8, 18, 40))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(brandMapper.selectById("brand-1")).thenReturn(brand);
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-1"))).thenReturn(List.of(latestTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds("org-1", List.of("scope-1"))).thenReturn(List.of(latestReport));
        when(brandSpaceConfigService.loadConfigView("scope-1", "brand-1", "美厨物语", 3))
                .thenReturn(BrandSpaceConfigView.builder()
                        .brandSpaceId("scope-1")
                        .brandId("brand-1")
                        .brandName("美厨物语")
                        .topics(List.of(
                                BrandSpaceConfigView.TopicView.builder()
                                        .topicId("topic-1")
                                        .name("品牌选择")
                                        .enabled(true)
                                        .enabledPromptCount(2)
                                        .build(),
                                BrandSpaceConfigView.TopicView.builder()
                                        .topicId("topic-3")
                                        .name("售后体验")
                                        .enabled(true)
                                        .enabledPromptCount(0)
                                        .build()
                        ))
                        .prompts(List.of(
                                BrandSpaceConfigView.PromptView.builder()
                                        .promptId("prompt-1")
                                        .topicId("topic-1")
                                        .enabled(true)
                                        .text("品牌选择 prompt")
                                        .build(),
                                BrandSpaceConfigView.PromptView.builder()
                                        .promptId("prompt-2")
                                        .topicId("topic-1")
                                        .enabled(true)
                                        .text("品牌选择 prompt 2")
                                        .build()
                        ))
                        .summary(BrandSpaceConfigView.SummaryView.builder()
                                .topicCount(2)
                                .enabledTopicCount(2)
                                .promptCount(2)
                                .enabledPromptCount(2)
                                .build())
                        .build());

        BrandSpaceScheduleExplanationView view = brandSpaceService.getScheduleExplanation("user-1", "scope-1");

        assertNotNull(view);
        assertEquals("scope-1", view.getBrandSpaceId());
        assertEquals("美厨物语", view.getBrandName());
        assertTrue(Boolean.TRUE.equals(view.getSnapshotAvailable()));
        assertEquals(1, view.getSelectedTopics().size());
        assertEquals("品牌选择", view.getSelectedTopics().getFirst().getTopicName());
        assertEquals(1, view.getDeferredTopics().size());
        assertEquals("渠道评价", view.getDeferredTopics().getFirst().getTopicName());
        assertEquals(1, view.getBlockedTopics().size());
        assertEquals("售后体验", view.getBlockedTopics().getFirst().getTopicName());
        assertEquals(1, view.getConfigSnapshot().getExecutableTopicCount());
        assertEquals(1, view.getConfigSnapshot().getBlockedTopicCount());
        assertEquals(14, view.getLatestSchedule().getTargetProbeCount());
    }

    @Test
    void listShouldUseRealTop3AndVisibilityMetrics() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("美厨物语")
                .categorySnapshot("厨房收纳")
                .primaryDomain("meichu.com")
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichu.com")
                .canonicalDomain("meichu.com")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("run-1")
                .brandScopeId("scope-1")
                .brandNameSnapshot("美厨物语")
                .statusCode(2)
                .createdAt(LocalDateTime.now().minusHours(1))
                .completedAt(LocalDateTime.now())
                .build();
        DiagnosisReportRecord latestReport = DiagnosisReportRecord.builder()
                .id("report-1")
                .diagnosisTaskId("run-1")
                .brandScopeId("scope-1")
                .generatedAt(LocalDateTime.now())
                .sampleSize(10)
                .citationRate(BigDecimal.valueOf(0.40d))
                .confirmationResponseRate(BigDecimal.valueOf(0.9091d))
                .summaryJson(Map.of("kpis", Map.of(
                        "overallMentionRate", 0.40d,
                        "recommendRate", 0.30d
                )))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(serviceAccessService.getServiceAccess("user-1"))
                .thenReturn(ServiceAccessView.builder().status("trial").build());
        when(brandScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-1"))).thenReturn(List.of(latestTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds("org-1", List.of("scope-1")))
                .thenReturn(List.of(latestReport));
        when(brandSpaceConfigService.summarizeConfigs(List.of("scope-1")))
                .thenReturn(Map.of("scope-1", new BrandSpaceConfigService.ConfigSummarySnapshot(2, 10, 2, 10)));
        when(probeBrandMentionRecordMapper.selectList(any())).thenReturn(List.of(
                ProbeBrandMentionRecord.builder()
                        .id("mention-1")
                        .diagnosisTaskId("run-1")
                        .probeId("probe-1")
                        .brandId("brand-1")
                        .brandName("美厨物语")
                        .mentionedFlag(true)
                        .positionIndex(2)
                        .build(),
                ProbeBrandMentionRecord.builder()
                        .id("mention-2")
                        .diagnosisTaskId("run-1")
                        .probeId("probe-2")
                        .brandId("brand-1")
                        .brandName("美厨物语")
                        .mentionedFlag(true)
                        .positionIndex(1)
                        .build()
        ));

        var response = brandSpaceService.list("user-1");

        assertEquals(1L, response.getTotal());
        assertEquals(31.0d, response.getItems().getFirst().getVisibilityScore());
        assertEquals(20.0d, response.getItems().getFirst().getTop3Rate());
        assertEquals(40.0d, response.getItems().getFirst().getMentionRate());
        assertEquals(30.0d, response.getItems().getFirst().getRecommendRate());
    }

    @Test
    void getBasicProfileShouldRecoverHistoricalCompetitorsWhenStoredProfileIsMissing() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("百汉 / 卫浴")
                .categorySnapshot("卫浴")
                .scopeStatusCode(1)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("百汉")
                .officialWebsite("http://www.baihanchuwei.com/")
                .build();
        DiagnosisTask latestEmptyTask = DiagnosisTask.builder()
                .id("run-empty")
                .brandScopeId("scope-1")
                .taskOrderJson(Map.of("competitors", List.of()))
                .updatedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();
        DiagnosisTask olderNonEmptyTask = DiagnosisTask.builder()
                .id("run-history")
                .brandScopeId("scope-1")
                .taskOrderJson(Map.of("competitors", List.of("金正", "恒洁卫浴", "奥格玛")))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(brandMapper.selectById("brand-1")).thenReturn(brand);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(latestEmptyTask, olderNonEmptyTask));

        var response = brandSpaceService.getBasicProfile("user-1", "scope-1");

        assertEquals(List.of("金正", "恒洁卫浴", "奥格玛"), response.getCompetitors());

        ArgumentCaptor<BrandScope> scopeCaptor = ArgumentCaptor.forClass(BrandScope.class);
        verify(brandScopeMapper).updateById(scopeCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) scopeCaptor.getValue().getDefaultModelPolicyJson().get("brandSpaceProfile");
        assertEquals(List.of("金正", "恒洁卫浴", "奥格玛"), profile.get("competitors"));
    }

    @Test
    void restoreShouldRejectWhenSameNameActiveBrandSpaceAlreadyExists() {
        BrandScope archivedScope = BrandScope.builder()
                .id("scope-archived")
                .organizationId("org-1")
                .brandId("brand-archived")
                .scopeName("白汉")
                .scopeStatusCode(0)
                .archivedAt(LocalDateTime.now().minusDays(3))
                .retentionUntil(LocalDateTime.now().plusDays(27))
                .internalRestoreUntil(LocalDateTime.now().plusDays(177))
                .build();
        BrandScope activeScope = BrandScope.builder()
                .id("scope-active")
                .organizationId("org-1")
                .brandId("brand-active")
                .scopeName("白汉")
                .scopeStatusCode(1)
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-archived")).thenReturn(archivedScope);
        when(brandMapper.selectById("brand-archived")).thenReturn(Brand.builder()
                .id("brand-archived")
                .brandName("白汉")
                .build());
        when(brandScopeMapper.selectList(any())).thenReturn(List.of(activeScope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(Brand.builder()
                .id("brand-active")
                .brandName("白汉")
                .build()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> brandSpaceService.restore("user-1", "scope-archived"));

        assertEquals("当前账号中已存在同名活跃品牌空间，请先处理现有品牌空间后再恢复", error.getMessage());
    }

    @Test
    void deleteArchivedShouldCloseSelfRestoreWindowButKeepInternalRetention() {
        LocalDateTime now = LocalDateTime.now();
        BrandScope archivedScope = BrandScope.builder()
                .id("scope-archived")
                .organizationId("org-1")
                .brandId("brand-archived")
                .scopeName("白汉")
                .scopeStatusCode(0)
                .trackingMode("ACTIVE")
                .archivedAt(now.minusDays(5))
                .retentionUntil(now.plusDays(25))
                .internalRestoreUntil(now.plusDays(175))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-archived")).thenReturn(archivedScope);
        when(brandMapper.selectById("brand-archived")).thenReturn(Brand.builder()
                .id("brand-archived")
                .brandName("白汉")
                .build());

        BrandSpaceArchiveView response = brandSpaceService.deleteArchived("user-1", "scope-archived");

        assertNotNull(response);
        assertEquals("INTERNAL_COLD_ARCHIVE", response.getLifecycleStatus());
        assertFalse(Boolean.TRUE.equals(response.getCanUserRestore()));
        assertTrue(Boolean.TRUE.equals(response.getCanInternalRestore()));

        ArgumentCaptor<BrandScope> scopeCaptor = ArgumentCaptor.forClass(BrandScope.class);
        verify(brandScopeMapper).updateById(scopeCaptor.capture());
        assertEquals("PAUSED", scopeCaptor.getValue().getTrackingMode());
        assertTrue(scopeCaptor.getValue().getRetentionUntil().isBefore(now));
        assertEquals(archivedScope.getInternalRestoreUntil(), scopeCaptor.getValue().getInternalRestoreUntil());
    }
}
