package com.geoprobe.service;

import com.geoprobe.common.LlmRunAdmissionBlockedException;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.dto.response.DiagnosisResponse;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceTrackingSchedulerServiceTest {

    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private OrganizationMembershipMapper organizationMembershipMapper;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    @Mock
    private ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    @Mock
    private BrandSpaceConfigService brandSpaceConfigService;
    @Mock
    private BrandSpaceSchedulingService brandSpaceSchedulingService;
    @Mock
    private BrandSpaceRunConfigService brandSpaceRunConfigService;
    @Mock
    private BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    @Mock
    private BrandSpaceExecutionGateService brandSpaceExecutionGateService;
    @Mock
    private UsageService usageService;
    @Mock
    private DiagnosisService diagnosisService;
    @Mock
    private DiagnosisTaskReconciliationService diagnosisTaskReconciliationService;
    @Mock
    private ProbePlanService probePlanService;
    @Mock
    private LegacyCreditMirrorService legacyCreditMirrorService;

    @InjectMocks
    private BrandSpaceTrackingSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        lenient().when(brandSpaceRunConfigService.resolveCurrentRunConfig(any(), any())).thenAnswer(invocation ->
                new BrandSpaceRunConfigService.CurrentRunConfig(
                        invocation.getArgument(1, Integer.class) == null ? 12 : invocation.getArgument(1, Integer.class),
                        "doubao",
                        true
                )
        );
        lenient().when(brandSpaceRunCreditPolicy.quote(any(), any(), any())).thenAnswer(invocation -> {
            Integer targetProbeCount = invocation.getArgument(1, Integer.class);
            int normalizedTargetProbeCount = targetProbeCount == null || targetProbeCount <= 0 ? 1 : targetProbeCount;
            return new BrandSpaceRunCreditPolicy.CreditQuote(
                    normalizedTargetProbeCount,
                    1,
                    normalizedTargetProbeCount,
                    200,
                    200 - normalizedTargetProbeCount,
                    true
            );
        });
    }

    @Test
    void shouldCreateScheduledRunForDueActiveBrandSpace() {
        LocalDateTime yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).atTime(12, 0);
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .createdByMembershipId("membership-1")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .trackingMode("ACTIVE")
                .configGeneration(1)
                .createdAt(yesterday)
                .updatedAt(yesterday)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("run-yesterday")
                .brandScopeId("scope-1")
                .statusCode(2)
                .createdAt(yesterday)
                .startedAt(yesterday.plusMinutes(1))
                .completedAt(yesterday.plusMinutes(2))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-1")
                .topics(List.of(
                        BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build(),
                        BrandSpaceConfigView.TopicView.builder().topicId("topic-2").name("风险异议").enabled(true).build()
                ))
                .prompts(List.of(
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-1")
                                .topicId("topic-1")
                                .topicName("品牌选择")
                                .text("美厨物语和 YWEEL 哪个更值得买？")
                                .enabled(true)
                                .build(),
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-2")
                                .topicId("topic-2")
                                .topicName("风险异议")
                                .text("美厨物语最常见的劝退点是什么？")
                                .enabled(true)
                                .build()
                ))
                .summary(BrandSpaceConfigView.SummaryView.builder()
                        .enabledTopicCount(2)
                        .enabledPromptCount(2)
                        .build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();

        when(brandScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-1"))).thenReturn(List.of(latestTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(eq("org-1"), eq(List.of("scope-1"))))
                .thenReturn(List.of());
        when(brandSpaceConfigService.summarizeConfigs(List.of("scope-1")))
                .thenReturn(Map.of("scope-1", new BrandSpaceConfigService.ConfigSummarySnapshot(2, 2, 2, 2)));
        when(organizationMembershipMapper.selectById("membership-1"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-1")
                        .organizationId("org-1")
                        .userId("user-1")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-1", "brand-1", "美厨物语", 1)).thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-1", scope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", access));
        when(brandSpaceRunConfigService.resolveCurrentRunConfig(eq(scope), any()))
                .thenReturn(new BrandSpaceRunConfigService.CurrentRunConfig(2, "deepseek", true));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-1"), eq(configView), eq(latestTask), eq(access), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        8,
                        List.of(
                                new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 5, 0.92d, List.of("补债")),
                                new BrandSpaceSchedulingService.TopicDecision("topic-2", "风险异议", 3, 0.61d, List.of("均衡覆盖"))
                        ),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        50,
                        1,
                        8,
                        12,
                        2,
                        0,
                        2,
                        0.0d
                ));
        when(diagnosisService.createDiagnosis(eq("user-1"), any(CreateDiagnosisFromTaskOrderRequest.class)))
                .thenReturn(DiagnosisResponse.builder().id("run-scheduled-1").build());
        when(probePlanService.recordScheduledRunPlan(any()))
                .thenReturn(new ProbePlanService.SnapshotRef("plan-version-1", "plan-record-2", 1, 2, 8));
        when(diagnosisTaskMapper.selectById("run-scheduled-1")).thenReturn(DiagnosisTask.builder()
                .id("run-scheduled-1")
                .taskOrderJson(Map.of("businessHints", Map.of(), "taskMetadata", Map.of()))
                .taskMetadataJson(Map.of())
                .build());
        schedulerService.scheduleDueBrandSpaces();

        verify(diagnosisTaskReconciliationService).reconcileOpenTasks("brand_space_schedule");

        ArgumentCaptor<CreateDiagnosisFromTaskOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateDiagnosisFromTaskOrderRequest.class);
        verify(diagnosisService)
                .createDiagnosis(eq("user-1"), requestCaptor.capture());

        CreateDiagnosisFromTaskOrderRequest request = requestCaptor.getValue();
        String expectedDate = java.time.format.DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        assertEquals("bs-schedule-scope1-" + expectedDate, request.getRequestId());
        assertNull(request.getEngineVersion());

        @SuppressWarnings("unchecked")
        Map<String, Object> taskOrder = (Map<String, Object>) request.getFrontDeskTaskOrder();
        assertEquals("美厨物语", taskOrder.get("brandName"));
        assertEquals("厨房收纳", taskOrder.get("category"));
        assertEquals("https://www.meichuwuyu.com", taskOrder.get("brandWebsite"));
        assertEquals("brand_space_schedule", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("source"));
        assertEquals("brand_space", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("origin"));
        assertEquals("scope-1", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("brandSpaceId"));
        assertEquals("brand-1", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("brandId"));
        assertEquals("active_scheduled_run", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("lifecycleStage"));
        assertEquals("scheduled_tracking", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("trigger"));
        assertEquals("light_cone_task_order_v4_3", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("taskOrderContractVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runVersionTruth = (Map<String, Object>) ((Map<?, ?>) taskOrder.get("taskMetadata")).get("lightConeRunVersionTruth");
        assertEquals("canonical", runVersionTruth.get("migrationMode"));
        assertEquals("canonicalExecutionLane", runVersionTruth.get("executionLane"));
        assertEquals("deepseek", taskOrder.get("targetModelProduct"));

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        @SuppressWarnings("unchecked")
        Map<String, Object> sampling = (Map<String, Object>) businessHints.get("sampling");
        assertEquals(8, sampling.get("targetProbeCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) businessHints.get("samplingRealtimeSearch");
        assertEquals(true, realtimeSearch.get("enabled"));
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) businessHints.get("samplingEngine");
        assertEquals("deepseek", samplingEngine.get("provider"));
        assertTrue(String.valueOf(businessHints.get("userPrompt")).contains("持续跟踪当前品牌空间的主题：品牌选择、风险异议"));
        assertFalse(String.valueOf(businessHints.get("userPrompt")).contains("1. 美厨物语和 YWEEL 哪个更值得买？"));
        assertFalse(businessHints.containsKey("brandSpacePromptSnapshot"));
        @SuppressWarnings("unchecked")
        Map<String, Object> topicPlan = (Map<String, Object>) businessHints.get("topicPlan");
        assertEquals(2, topicPlan.get("enabledTopicCount"));
        assertEquals("brand_space_schedule_plan", topicPlan.get("topicSelectionMode"));
        assertEquals("brand_space_schedule_truth", topicPlan.get("coverageSource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> scheduleDecision = (Map<String, Object>) businessHints.get("scheduleDecision");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionPrompts = (List<Map<String, Object>>) scheduleDecision.get("executionPrompts");
        assertEquals(2, executionPrompts.size());
        assertEquals("topic-1", executionPrompts.get(0).get("topicId"));
        assertEquals("品牌选择", executionPrompts.get(0).get("topicName"));
        assertEquals("prompt-1", executionPrompts.get(0).get("promptId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schedulePolicy = (Map<String, Object>) businessHints.get("schedulePolicy");
        assertEquals("scheduled_tracking", schedulePolicy.get("mode"));
        assertEquals(expectedDate, String.valueOf(schedulePolicy.get("scheduleDate")).replace("-", ""));
        assertEquals(8, schedulePolicy.get("targetProbeCount"));
        assertEquals(50, schedulePolicy.get("accountAvailableCredits"));
        assertEquals(8, schedulePolicy.get("brandCoreProbeCount"));
        assertEquals(12, schedulePolicy.get("brandExpansionProbeCount"));
        assertEquals(2, schedulePolicy.get("weeklyTargetTopicCount"));
        assertEquals(2, schedulePolicy.get("weeklyGapTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> brandSpaceContext = (Map<String, Object>) businessHints.get("brandSpaceContext");
        assertEquals("scope-1", brandSpaceContext.get("brandSpaceId"));
        assertEquals(8, brandSpaceContext.get("requestedProbeCount"));
        assertEquals(50, brandSpaceContext.get("accountAvailableCredits"));
        assertEquals(2, brandSpaceContext.get("weeklyTargetTopicCount"));
        assertEquals(2, brandSpaceContext.get("weeklyGapTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceAccess = (Map<String, Object>) brandSpaceContext.get("serviceAccess");
        assertEquals("trial", serviceAccess.get("status"));
        assertEquals(50, serviceAccess.get("promptLimitPerBrandSpace"));

        ArgumentCaptor<ProbePlanService.SnapshotCommand> snapshotCommandCaptor =
                ArgumentCaptor.forClass(ProbePlanService.SnapshotCommand.class);
        verify(probePlanService).recordScheduledRunPlan(snapshotCommandCaptor.capture());
        assertEquals("org-1", snapshotCommandCaptor.getValue().organizationId());
        assertEquals("brand-1", snapshotCommandCaptor.getValue().brandId());
        assertEquals("scope-1", snapshotCommandCaptor.getValue().brandScopeId());
        assertEquals("run-scheduled-1", snapshotCommandCaptor.getValue().triggeredRunId());
        assertEquals(8, snapshotCommandCaptor.getValue().plannedProbeCount());

        ArgumentCaptor<DiagnosisTask> updatedTaskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(updatedTaskCaptor.capture());
        DiagnosisTask updatedTask = updatedTaskCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedMetadata = updatedTask.getTaskMetadataJson();
        assertEquals("plan-version-1", updatedMetadata.get("planVersionId"));
        assertEquals("plan-record-2", updatedMetadata.get("planRecordId"));
        assertEquals("SCHEDULED", updatedMetadata.get("runTypeCode"));
        assertEquals("brand_space_schedule", updatedMetadata.get("triggerSource"));
        verify(legacyCreditMirrorService).attachDiagnosisTaskAttribution(updatedTask);
    }

    @Test
    void shouldSkipPausedOrAlreadyRunTodayAndInvokeCreditBridgeForDueScope() {
        LocalDateTime now = LocalDateTime.now();
        BrandScope pausedScope = BrandScope.builder()
                .id("scope-paused")
                .organizationId("org-1")
                .brandId("brand-1")
                .trackingMode("PAUSED")
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build();
        BrandScope todayScope = BrandScope.builder()
                .id("scope-today")
                .organizationId("org-1")
                .brandId("brand-2")
                .trackingMode("ACTIVE")
                .createdByMembershipId("membership-2")
                .categorySnapshot("智能门锁")
                .locale("zh-CN")
                .language("zh-CN")
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .configGeneration(2)
                .build();
        BrandScope creditScope = BrandScope.builder()
                .id("scope-credit")
                .organizationId("org-2")
                .brandId("brand-3")
                .trackingMode("ACTIVE")
                .createdByMembershipId("membership-3")
                .categorySnapshot("智能门锁")
                .locale("zh-CN")
                .language("zh-CN")
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(2))
                .configGeneration(3)
                .build();
        DiagnosisTask todayTask = DiagnosisTask.builder()
                .id("run-today")
                .brandScopeId("scope-today")
                .statusCode(2)
                .createdAt(now.minusHours(1))
                .completedAt(now.minusMinutes(30))
                .build();
        DiagnosisReportRecord todayReport = DiagnosisReportRecord.builder()
                .brandScopeId("scope-today")
                .generatedAt(now.minusMinutes(20))
                .build();
        Brand creditBrand = Brand.builder()
                .id("brand-3")
                .brandName("智能门锁品牌")
                .officialWebsite("https://lock.example.com")
                .build();
        BrandSpaceConfigView creditConfig = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-credit")
                .topics(List.of(BrandSpaceConfigView.TopicView.builder().name("安装与耐用度").enabled(true).build()))
                .prompts(List.of(BrandSpaceConfigView.PromptView.builder().text("这个门锁安装麻烦吗？").enabled(true).build()))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(1).enabledPromptCount(1).build())
                .build();

        when(brandScopeMapper.selectList(any())).thenReturn(List.of(pausedScope, todayScope, creditScope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(creditBrand));
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-paused", "scope-today", "scope-credit")))
                .thenReturn(List.of(todayTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(eq("org-1"), eq(List.of("scope-paused", "scope-today"))))
                .thenReturn(List.of(todayReport));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(eq("org-2"), eq(List.of("scope-credit"))))
                .thenReturn(List.of());
        when(brandSpaceConfigService.summarizeConfigs(List.of("scope-paused", "scope-today", "scope-credit")))
                .thenReturn(Map.of(
                        "scope-paused", new BrandSpaceConfigService.ConfigSummarySnapshot(1, 1, 1, 1),
                        "scope-today", new BrandSpaceConfigService.ConfigSummarySnapshot(1, 1, 1, 1),
                        "scope-credit", new BrandSpaceConfigService.ConfigSummarySnapshot(1, 1, 1, 1)
                ));
        when(organizationMembershipMapper.selectById("membership-3"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-3")
                        .organizationId("org-2")
                        .userId("user-3")
                        .statusCode(1)
                        .build());
        ServiceAccessView activeAccess = ServiceAccessView.builder()
                .status("active")
                .limits(ServiceAccessView.Limits.builder().promptLimitPerBrandSpace(50).build())
                .build();
        when(brandSpaceConfigService.loadConfigView("scope-credit", "brand-3", "智能门锁品牌", 3))
                .thenReturn(creditConfig);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-3", creditScope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-3", "org-2", "membership-3", activeAccess));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-credit"), eq(creditConfig), any(), eq(activeAccess), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        8,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "安装与耐用度", 8, 0.8d, List.of("补债"))),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        50,
                        1,
                        8,
                        12,
                        1,
                        0,
                        1,
                        0.0d
                ));
        when(diagnosisService.createDiagnosis(eq("user-3"), any(CreateDiagnosisFromTaskOrderRequest.class)))
                .thenReturn(DiagnosisResponse.builder().id("run-credit-1").build());
        schedulerService.scheduleDueBrandSpaces();

        verify(diagnosisService)
                .createDiagnosis(eq("user-3"), any(CreateDiagnosisFromTaskOrderRequest.class));
        verify(diagnosisService, never())
                .createDiagnosis(eq("user-2"), any(CreateDiagnosisFromTaskOrderRequest.class));
    }

    @Test
    void shouldSkipScheduledRunWhenCreditsAreInsufficient() {
        LocalDateTime yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).atTime(12, 0);
        BrandScope scope = BrandScope.builder()
                .id("scope-credit-blocked")
                .organizationId("org-1")
                .brandId("brand-1")
                .createdByMembershipId("membership-1")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(1)
                .createdAt(yesterday)
                .updatedAt(yesterday)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("run-yesterday")
                .brandScopeId("scope-credit-blocked")
                .statusCode(2)
                .createdAt(yesterday)
                .completedAt(yesterday.plusMinutes(2))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-credit-blocked")
                .topics(List.of(BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build()))
                .prompts(List.of(BrandSpaceConfigView.PromptView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("品牌选择")
                        .text("美厨物语值得买吗？")
                        .enabled(true)
                        .build()))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(1).enabledPromptCount(1).build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder().promptLimitPerBrandSpace(50).build())
                .build();

        when(brandScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-credit-blocked"))).thenReturn(List.of(latestTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(eq("org-1"), eq(List.of("scope-credit-blocked"))))
                .thenReturn(List.of());
        when(brandSpaceConfigService.summarizeConfigs(List.of("scope-credit-blocked")))
                .thenReturn(Map.of("scope-credit-blocked", new BrandSpaceConfigService.ConfigSummarySnapshot(1, 1, 1, 1)));
        when(organizationMembershipMapper.selectById("membership-1"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-1")
                        .organizationId("org-1")
                        .userId("user-1")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-credit-blocked", "brand-1", "美厨物语", 1)).thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-1", scope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", access));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-credit-blocked"), eq(configView), eq(latestTask), eq(access), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 12, 0.92d, List.of("补债"))),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        50,
                        1,
                        12,
                        12,
                        1,
                        0,
                        1,
                        0.0d
                ));
        when(brandSpaceRunCreditPolicy.quote("user-1", 12, access))
                .thenReturn(new BrandSpaceRunCreditPolicy.CreditQuote(12, 1, 12, 5, 0, false));

        schedulerService.scheduleDueBrandSpaces();

        verify(brandSpaceRunCreditPolicy).quote("user-1", 12, access);
        verify(diagnosisService, never()).createDiagnosis(eq("user-1"), any(CreateDiagnosisFromTaskOrderRequest.class));
    }

    @Test
    void shouldSkipScheduledRunWhenRewardSnapshotGateBlocksBeforeTaskCreation() {
        LocalDateTime yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).atTime(12, 0);
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .createdByMembershipId("membership-1")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(1)
                .createdAt(yesterday)
                .updatedAt(yesterday)
                .build();
        Brand brand = Brand.builder()
                .id("brand-1")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("run-token-gate-today")
                .brandScopeId("scope-1")
                .statusCode(3)
                .samplingCompletedCount(0)
                .samplingFailedCount(8)
                .failureMessage("V4.2 Node 3 未产出任何成功样本，failureReasons={TOKEN_BUDGET_EXCEEDED=8}")
                .createdAt(LocalDateTime.now().minusDays(1))
                .completedAt(LocalDateTime.now().minusDays(1).plusSeconds(2))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-1")
                .topics(List.of(BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build()))
                .prompts(List.of(BrandSpaceConfigView.PromptView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("品牌选择")
                        .text("美厨物语值得买吗？")
                        .enabled(true)
                        .build()))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(1).enabledPromptCount(1).build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder().promptLimitPerBrandSpace(50).build())
                .build();

        when(brandScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));
        when(diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of("scope-1"))).thenReturn(List.of(latestTask));
        when(diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(eq("org-1"), eq(List.of("scope-1"))))
                .thenReturn(List.of());
        when(brandSpaceConfigService.summarizeConfigs(List.of("scope-1")))
                .thenReturn(Map.of("scope-1", new BrandSpaceConfigService.ConfigSummarySnapshot(1, 1, 1, 1)));
        when(organizationMembershipMapper.selectById("membership-1"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-1")
                        .organizationId("org-1")
                        .userId("user-1")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-1", "brand-1", "美厨物语", 1)).thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-1", scope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-1", "org-1", "membership-1", access));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-1"), eq(configView), eq(latestTask), eq(access), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        8,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 8, 0.8d, List.of("补债"))),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        50,
                        1,
                        8,
                        12,
                        1,
                        0,
                        1,
                        0.0d
                ));
        org.mockito.Mockito.doThrow(new LlmRunAdmissionBlockedException("doubao", "", "snapshot stale"))
                .when(diagnosisService)
                .createDiagnosis(eq("user-1"), any(CreateDiagnosisFromTaskOrderRequest.class));

        schedulerService.scheduleDueBrandSpaces();

        verify(diagnosisService).createDiagnosis(eq("user-1"), any(CreateDiagnosisFromTaskOrderRequest.class));
    }

    @Test
    void replayBrandSpaceShouldRebuildTodayPlanWithUniqueReplayRequestId() {
        LocalDateTime now = LocalDateTime.now();
        BrandScope targetScope = BrandScope.builder()
                .id("scope-replay")
                .organizationId("org-9")
                .brandId("brand-9")
                .createdByMembershipId("membership-9")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(4)
                .createdAt(now.minusDays(3))
                .updatedAt(now.minusDays(3))
                .build();
        BrandScope peerScope = BrandScope.builder()
                .id("scope-peer")
                .organizationId("org-9")
                .brandId("brand-8")
                .createdByMembershipId("membership-9")
                .scopeName("同账户其他品牌")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(1)
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(2))
                .build();
        Brand targetBrand = Brand.builder()
                .id("brand-9")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTargetTask = DiagnosisTask.builder()
                .id("old-run")
                .brandScopeId("scope-replay")
                .statusCode(2)
                .createdAt(now.minusHours(2))
                .completedAt(now.minusHours(1))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-replay")
                .topics(List.of(BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build()))
                .prompts(List.of(BrandSpaceConfigView.PromptView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("品牌选择")
                        .text("美厨物语值得买吗？")
                        .enabled(true)
                        .build()))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(1).enabledPromptCount(1).build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
        when(brandScopeMapper.selectById("scope-replay")).thenReturn(targetScope);
        when(brandMapper.selectById("brand-9")).thenReturn(targetBrand);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(latestTargetTask));
        when(organizationMembershipMapper.selectById("membership-9"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-9")
                        .organizationId("org-9")
                        .userId("user-9")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-replay", "brand-9", "美厨物语", 4))
                .thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-9", targetScope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-9", "org-9", "membership-9", access));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-replay"), eq(configView), eq(latestTargetTask), eq(access), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 12, 0.95d, List.of("补债"))),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        32,
                        2,
                        12,
                        16,
                        1,
                        0,
                        1,
                        0.0d
                ));
        when(diagnosisService.createDiagnosis(eq("user-9"), any(CreateDiagnosisFromTaskOrderRequest.class)))
                .thenReturn(DiagnosisResponse.builder().id("rerun-1").build());
        when(probePlanService.recordControlledRerunPlan(any()))
                .thenReturn(new ProbePlanService.SnapshotRef("plan-version-replay", "plan-record-replay", 3, 4, 12));
        when(diagnosisTaskMapper.selectById("rerun-1")).thenReturn(DiagnosisTask.builder()
                .id("rerun-1")
                .taskOrderJson(Map.of("businessHints", Map.of(), "taskMetadata", Map.of()))
                .taskMetadataJson(Map.of())
                .build());

        BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult result =
                schedulerService.replayBrandSpace("scope-replay", LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));

        assertFalse(result.skipped());
        assertEquals("rerun-1", result.runId());
        assertEquals(12, result.targetProbeCount());
        assertNotNull(result.requestId());
        assertTrue(result.requestId().startsWith("bs-replay-scopereplay-"));

        ArgumentCaptor<CreateDiagnosisFromTaskOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateDiagnosisFromTaskOrderRequest.class);
        verify(diagnosisService)
                .createDiagnosis(eq("user-9"), requestCaptor.capture());
        assertEquals(result.requestId(), requestCaptor.getValue().getRequestId());

        ArgumentCaptor<ProbePlanService.SnapshotCommand> snapshotCommandCaptor =
                ArgumentCaptor.forClass(ProbePlanService.SnapshotCommand.class);
        verify(probePlanService).recordControlledRerunPlan(snapshotCommandCaptor.capture());
        assertEquals("org-9", snapshotCommandCaptor.getValue().organizationId());
        assertEquals("brand-9", snapshotCommandCaptor.getValue().brandId());
        assertEquals("scope-replay", snapshotCommandCaptor.getValue().brandScopeId());
        assertEquals("rerun-1", snapshotCommandCaptor.getValue().triggeredRunId());
        assertEquals(12, snapshotCommandCaptor.getValue().plannedProbeCount());

        ArgumentCaptor<DiagnosisTask> updatedTaskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(updatedTaskCaptor.capture());
        DiagnosisTask updatedTask = updatedTaskCaptor.getValue();
        Map<String, Object> updatedMetadata = updatedTask.getTaskMetadataJson();
        assertEquals("plan-version-replay", updatedMetadata.get("planVersionId"));
        assertEquals("plan-record-replay", updatedMetadata.get("planRecordId"));
        assertEquals("CONTROLLED_RERUN", updatedMetadata.get("runTypeCode"));
        assertEquals("brand_space_schedule_replay", updatedMetadata.get("triggerSource"));
        verify(legacyCreditMirrorService).attachDiagnosisTaskAttribution(updatedTask);
    }

    @Test
    void replayBrandSpaceShouldSkipWhenRewardSnapshotGateBlocks() {
        LocalDateTime now = LocalDateTime.now();
        BrandScope targetScope = BrandScope.builder()
                .id("scope-replay")
                .organizationId("org-9")
                .brandId("brand-9")
                .createdByMembershipId("membership-9")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(4)
                .createdAt(now.minusDays(3))
                .updatedAt(now.minusDays(3))
                .build();
        Brand targetBrand = Brand.builder()
                .id("brand-9")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTargetTask = DiagnosisTask.builder()
                .id("old-run")
                .brandScopeId("scope-replay")
                .statusCode(2)
                .createdAt(now.minusHours(2))
                .completedAt(now.minusHours(1))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-replay")
                .topics(List.of(BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build()))
                .prompts(List.of(BrandSpaceConfigView.PromptView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("品牌选择")
                        .text("美厨物语值得买吗？")
                        .enabled(true)
                        .build()))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(1).enabledPromptCount(1).build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder().promptLimitPerBrandSpace(50).build())
                .build();
        when(brandScopeMapper.selectById("scope-replay")).thenReturn(targetScope);
        when(brandMapper.selectById("brand-9")).thenReturn(targetBrand);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(latestTargetTask));
        when(organizationMembershipMapper.selectById("membership-9"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-9")
                        .organizationId("org-9")
                        .userId("user-9")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-replay", "brand-9", "美厨物语", 4))
                .thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-9", targetScope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-9", "org-9", "membership-9", access));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-replay"), eq(configView), eq(latestTargetTask), eq(access), any(), eq(false)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "scheduled_tracking",
                        12,
                        List.of(new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 12, 0.95d, List.of("补债"))),
                        List.of(),
                        "balanced_topic_coverage",
                        "今天优先补齐最近覆盖不足的主题",
                        false,
                        false,
                        false,
                        50,
                        1,
                        12,
                        16,
                        1,
                        0,
                        1,
                        0.0d
                ));
        org.mockito.Mockito.doThrow(new LlmRunAdmissionBlockedException("doubao", "", "snapshot stale"))
                .when(diagnosisService)
                .createDiagnosis(eq("user-9"), any(CreateDiagnosisFromTaskOrderRequest.class));

        BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult result =
                schedulerService.replayBrandSpace("scope-replay", LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));

        assertTrue(result.skipped());
        assertEquals("reward_quota_snapshot_unavailable", result.reason());
        verify(diagnosisService).createDiagnosis(eq("user-9"), any(CreateDiagnosisFromTaskOrderRequest.class));
    }

    @Test
    void executeBrandSpaceOnceShouldCreateManualRunFromCurrentConfig() {
        LocalDateTime now = LocalDateTime.now();
        BrandScope scope = BrandScope.builder()
                .id("scope-manual")
                .organizationId("org-9")
                .brandId("brand-9")
                .createdByMembershipId("membership-9")
                .scopeName("美厨物语 / 厨房收纳")
                .categorySnapshot("厨房收纳")
                .trackingMode("ACTIVE")
                .locale("zh-CN")
                .language("zh-CN")
                .configGeneration(4)
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(1))
                .build();
        Brand brand = Brand.builder()
                .id("brand-9")
                .brandName("美厨物语")
                .officialWebsite("https://www.meichuwuyu.com")
                .build();
        DiagnosisTask latestTask = DiagnosisTask.builder()
                .id("old-run")
                .brandScopeId("scope-manual")
                .statusCode(2)
                .createdAt(now.minusHours(2))
                .completedAt(now.minusHours(1))
                .build();
        BrandSpaceConfigView configView = BrandSpaceConfigView.builder()
                .brandSpaceId("scope-manual")
                .topics(List.of(
                        BrandSpaceConfigView.TopicView.builder().topicId("topic-1").name("品牌选择").enabled(true).build(),
                        BrandSpaceConfigView.TopicView.builder().topicId("topic-2").name("材质耐用").enabled(true).build()
                ))
                .prompts(List.of(
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-1")
                                .topicId("topic-1")
                                .topicName("品牌选择")
                                .seqNo(1)
                                .text("美厨物语值得买吗？")
                                .enabled(true)
                                .build(),
                        BrandSpaceConfigView.PromptView.builder()
                                .promptId("prompt-2")
                                .topicId("topic-2")
                                .topicName("材质耐用")
                                .seqNo(2)
                                .text("美厨物语材质耐用吗？")
                                .enabled(true)
                                .build()
                ))
                .summary(BrandSpaceConfigView.SummaryView.builder().enabledTopicCount(2).enabledPromptCount(2).build())
                .build();
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
        when(brandScopeMapper.selectById("scope-manual")).thenReturn(scope);
        when(brandMapper.selectById("brand-9")).thenReturn(brand);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(latestTask));
        when(organizationMembershipMapper.selectById("membership-9"))
                .thenReturn(OrganizationMembership.builder()
                        .id("membership-9")
                        .organizationId("org-9")
                        .userId("user-9")
                        .statusCode(1)
                        .build());
        when(brandSpaceConfigService.loadConfigView("scope-manual", "brand-9", "美厨物语", 4))
                .thenReturn(configView);
        when(brandSpaceExecutionGateService.requireScheduledAuthority("user-9", scope))
                .thenReturn(new BrandSpaceExecutionGateService.AuthoritySnapshot("user-9", "org-9", "membership-9", access));
        when(brandSpaceRunConfigService.resolveCurrentRunConfig(eq(scope), any()))
                .thenReturn(new BrandSpaceRunConfigService.CurrentRunConfig(2, "deepseek", true));
        when(brandSpaceSchedulingService.planCurrentConfigRun(eq("scope-manual"), eq(configView), eq(latestTask), eq(access), any(), eq(true)))
                .thenReturn(new BrandSpaceSchedulingService.ExecutionPlan(
                        "manual_execute_once",
                        2,
                        List.of(
                                new BrandSpaceSchedulingService.TopicDecision("topic-1", "品牌选择", 1, 0.95d, List.of("当前配置")),
                                new BrandSpaceSchedulingService.TopicDecision("topic-2", "材质耐用", 1, 0.88d, List.of("当前配置"))
                        ),
                        List.of(),
                        "manual_execute_once",
                        "按当前配置立即执行一次",
                        false,
                        false,
                        false,
                        42,
                        1,
                        2,
                        2,
                        2,
                        0,
                        2,
                        0.0d
                ));
        when(diagnosisService.createDiagnosis(eq("user-9"), any(CreateDiagnosisFromTaskOrderRequest.class)))
                .thenReturn(DiagnosisResponse.builder().id("manual-run-1").build());
        when(probePlanService.recordManualRunPlan(any()))
                .thenReturn(new ProbePlanService.SnapshotRef("plan-version-1", "plan-record-manual", 1, 2, 2));
        when(diagnosisTaskMapper.selectById("manual-run-1")).thenReturn(DiagnosisTask.builder()
                .id("manual-run-1")
                .taskOrderJson(Map.of("businessHints", Map.of(), "taskMetadata", Map.of()))
                .taskMetadataJson(Map.of())
                .build());

        BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult result =
                schedulerService.executeBrandSpaceOnce("scope-manual");

        assertFalse(result.skipped());
        assertEquals("manual-run-1", result.runId());
        assertEquals(2, result.targetProbeCount());
        assertTrue(result.requestId().startsWith("bs-manual-scopemanual-"));

        ArgumentCaptor<CreateDiagnosisFromTaskOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateDiagnosisFromTaskOrderRequest.class);
        verify(diagnosisService).createDiagnosis(eq("user-9"), requestCaptor.capture());
        Map<String, Object> taskOrder = requestCaptor.getValue().getFrontDeskTaskOrder();
        assertEquals("brand_space_manual_execute_once", ((Map<?, ?>) taskOrder.get("taskMetadata")).get("source"));
        assertEquals("deepseek", taskOrder.get("targetModelProduct"));
        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) taskOrder.get("businessHints");
        @SuppressWarnings("unchecked")
        Map<String, Object> realtimeSearch = (Map<String, Object>) businessHints.get("samplingRealtimeSearch");
        assertEquals("doubao", realtimeSearch.get("provider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> samplingEngine = (Map<String, Object>) businessHints.get("samplingEngine");
        assertEquals("deepseek", samplingEngine.get("provider"));

        ArgumentCaptor<ProbePlanService.SnapshotCommand> snapshotCommandCaptor =
                ArgumentCaptor.forClass(ProbePlanService.SnapshotCommand.class);
        verify(probePlanService).recordManualRunPlan(snapshotCommandCaptor.capture());
        assertEquals("manual-run-1", snapshotCommandCaptor.getValue().triggeredRunId());
        assertEquals(2, snapshotCommandCaptor.getValue().plannedProbeCount());

        ArgumentCaptor<DiagnosisTask> updatedTaskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(updatedTaskCaptor.capture());
        assertEquals("MANUAL_ONCE", updatedTaskCaptor.getValue().getTaskMetadataJson().get("runTypeCode"));
        assertEquals("brand_space_manual_execute_once", updatedTaskCaptor.getValue().getTaskMetadataJson().get("triggerSource"));
    }
}
