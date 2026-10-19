package com.geoprobe.service;

import com.geoprobe.dto.brandspace.BrandSpaceProbePlanView;
import com.geoprobe.dto.brandspace.BrandSpaceProbePlanExecuteOnceResponse;
import com.geoprobe.dto.request.BrandSpaceProbePlanConfirmRequest;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanRecordItem;
import com.geoprobe.entity.v1.ProbePlanVersion;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordItemMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceProbePlanReadServiceTest {

    @Mock
    private ServiceAccessService serviceAccessService;
    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private BrandSpaceTopicMapper brandSpaceTopicMapper;
    @Mock
    private BrandSpacePromptMapper brandSpacePromptMapper;
    @Mock
    private ProbePlanVersionMapper probePlanVersionMapper;
    @Mock
    private ProbePlanRecordMapper probePlanRecordMapper;
    @Mock
    private ProbePlanRecordItemMapper probePlanRecordItemMapper;
    @Mock
    private ProbePlanService probePlanService;
    @Mock
    private BrandSpaceRunConfigService brandSpaceRunConfigService;
    @Mock
    private BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    @Mock
    private BrandSpaceTrackingSchedulerService brandSpaceTrackingSchedulerService;

    private BrandSpaceProbePlanReadService service;

    @BeforeEach
    void setUp() {
        service = new BrandSpaceProbePlanReadService(
                serviceAccessService,
                brandScopeMapper,
                brandSpaceTopicMapper,
                brandSpacePromptMapper,
                probePlanVersionMapper,
                probePlanRecordMapper,
                probePlanRecordItemMapper,
                probePlanService,
                brandSpaceRunConfigService,
                brandSpaceRunCreditPolicy,
                brandSpaceTrackingSchedulerService
        );
    }

    @Test
    void shouldReadCurrentProbePlanAndRecentRecords() {
        seedScope();
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(2)
                .brandScopeId("scope-1")
                .activeRecurringProbeCount(8)
                .estimatedRecurringCreditsPerCycle(8)
                .configGenerationSnapshot(3)
                .snapshotJson(Map.of("contractVersion", "probe_plan_b1"))
                .build());
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of(
                ProbePlanRecord.builder()
                        .id("record-2")
                        .planVersionId("plan-version-1")
                        .recordNo(2)
                        .changeTypeCode("SCHEDULED_RUN")
                        .executeModeCode("RECURRING")
                        .immediateEstimatedCredits(8)
                        .recurringEstimatedCreditsPerCycle(8)
                        .summaryJson(Map.of("enabledProbeCount", 8))
                        .createdAt(LocalDateTime.of(2026, 5, 22, 10, 0))
                        .build()
        ));
        when(probePlanRecordItemMapper.selectList(any())).thenReturn(List.of(
                ProbePlanRecordItem.builder()
                        .recordId("record-2")
                        .planVersionId("plan-version-1")
                        .probeId("probe-1")
                        .topicId("topic-1")
                        .seqNoSnapshot(1)
                        .probeTextSnapshot("探镜适合什么公司？")
                        .actionCode("KEEP")
                        .executeModeCode("RECURRING")
                        .build()
        ));

        BrandSpaceProbePlanView view = service.getProbePlan("user-1", "scope-1");

        assertEquals("brand_space_probe_plan_b1", view.getContractVersion());
        assertEquals("scope-1", view.getBrandSpaceId());
        assertEquals(8, view.getCurrentRunConfig().getTargetProbeCount());
        assertEquals("doubao", view.getCurrentRunConfig().getTargetModelProduct());
        assertEquals("plan-version-1", view.getCurrentPlan().getPlanVersionId());
        assertEquals(8, view.getCurrentPlan().getEstimatedCreditsPerCycle());
        assertEquals("探镜适合什么公司？", view.getCurrentPlan().getItems().get(0).getPromptText());
        assertEquals("record-2", view.getRecentRecords().get(0).getPlanRecordId());
    }

    @Test
    void shouldBuildDraftPlanFromCurrentConfigWhenNoConfirmedVersionExists() {
        seedScope();
        when(probePlanVersionMapper.selectOne(any())).thenReturn(null);
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of());
        when(brandSpaceTopicMapper.selectList(any())).thenReturn(List.of(
                BrandSpaceTopic.builder()
                        .id("topic-1")
                        .name("厨房收纳选购场景")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(any())).thenReturn(List.of(
                BrandSpacePrompt.builder()
                        .id("probe-1")
                        .topicId("topic-1")
                        .seqNo(1)
                        .promptText("厨房收纳应该怎么选？")
                        .statusCode(1)
                        .build(),
                BrandSpacePrompt.builder()
                        .id("probe-2")
                        .topicId("topic-1")
                        .seqNo(2)
                        .promptText("美厨物语适合小户型厨房吗？")
                        .statusCode(1)
                        .build()
        ));

        BrandSpaceProbePlanView view = service.getProbePlan("user-1", "scope-1");

        assertEquals("scope-1", view.getBrandSpaceId());
        assertEquals(2, view.getCurrentPlan().getActiveRecurringProbeCount());
        assertEquals(2, view.getCurrentPlan().getEstimatedCreditsPerCycle());
        assertEquals("probe-1", view.getCurrentPlan().getItems().get(0).getProbeId());
        assertEquals("RECURRING", view.getCurrentPlan().getItems().get(0).getExecuteModeCode());
        assertEquals("DRAFT_FROM_CURRENT_CONFIG", view.getCurrentPlan().getSnapshot().get("mode"));
    }

    @Test
    void shouldBuildDraftPlanFromCurrentConfigWhenConfirmedVersionIsStale() {
        seedScope();
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-old")
                .versionNo(1)
                .brandScopeId("scope-1")
                .activeRecurringProbeCount(1)
                .estimatedRecurringCreditsPerCycle(1)
                .configGenerationSnapshot(2)
                .snapshotJson(Map.of("contractVersion", "probe_plan_b1"))
                .build());
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of(
                ProbePlanRecord.builder()
                        .id("record-old")
                        .planVersionId("plan-version-old")
                        .recordNo(1)
                        .changeTypeCode("CONFIG_CONFIRM")
                        .executeModeCode("RECURRING")
                        .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                        .build()
        ));
        when(brandSpaceTopicMapper.selectList(any())).thenReturn(List.of(
                BrandSpaceTopic.builder()
                        .id("topic-new")
                        .name("新增选购主题")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(any())).thenReturn(List.of(
                BrandSpacePrompt.builder()
                        .id("probe-new")
                        .topicId("topic-new")
                        .seqNo(1)
                        .promptText("新主题应该如何评估？")
                        .statusCode(1)
                        .build()
        ));

        BrandSpaceProbePlanView view = service.getProbePlan("user-1", "scope-1");

        assertNull(view.getCurrentPlan().getPlanVersionId());
        assertNull(view.getCurrentPlan().getVersionNo());
        assertEquals(1, view.getCurrentPlan().getEstimatedCreditsPerCycle());
        assertEquals(3, view.getCurrentPlan().getConfigGeneration());
        assertEquals("DRAFT_FROM_CURRENT_CONFIG", view.getCurrentPlan().getSnapshot().get("mode"));
        assertEquals("probe-new", view.getCurrentPlan().getItems().get(0).getProbeId());
        assertEquals("record-old", view.getRecentRecords().get(0).getPlanRecordId());
        verify(probePlanRecordItemMapper, never()).selectList(any());
    }

    @Test
    void shouldConfirmCurrentPlanAndReturnLatestView() {
        seedScope();
        when(probePlanService.confirmCurrentConfigPlan(any())).thenReturn(
                new ProbePlanService.SnapshotRef("plan-version-2", "record-3", 3, 3, 10)
        );
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-2")
                .versionNo(3)
                .brandScopeId("scope-1")
                .configGenerationSnapshot(3)
                .activeRecurringProbeCount(10)
                .estimatedRecurringCreditsPerCycle(10)
                .build());
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of());

        BrandSpaceProbePlanConfirmRequest request = new BrandSpaceProbePlanConfirmRequest();
        request.setPlannedProbeCount(10);
        request.setTargetModelProduct("deepseek");
        BrandSpaceProbePlanView view = service.confirmCurrentPlan("user-1", "scope-1", request);

        verify(probePlanService).confirmCurrentConfigPlan(any());
        assertEquals("plan-version-2", view.getCurrentPlan().getPlanVersionId());
        assertEquals(10, view.getCurrentPlan().getEstimatedCreditsPerCycle());
    }

    @Test
    void shouldConfirmCurrentPlanAsOneTimeWhenRequested() {
        seedScope();
        when(probePlanService.confirmCurrentConfigPlanOnce(any())).thenReturn(
                new ProbePlanService.SnapshotRef("plan-version-once", "record-once", 4, 4, 6)
        );
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-once")
                .versionNo(4)
                .brandScopeId("scope-1")
                .configGenerationSnapshot(3)
                .activeRecurringProbeCount(0)
                .estimatedRecurringCreditsPerCycle(0)
                .build());
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of());

        BrandSpaceProbePlanConfirmRequest request = new BrandSpaceProbePlanConfirmRequest();
        request.setPlannedProbeCount(6);
        request.setExecuteModeCode("ONE_TIME");
        BrandSpaceProbePlanView view = service.confirmCurrentPlan("user-1", "scope-1", request);

        verify(probePlanService).confirmCurrentConfigPlanOnce(any());
        verify(probePlanService, never()).confirmCurrentConfigPlan(any());
        assertEquals("plan-version-once", view.getCurrentPlan().getPlanVersionId());
        assertEquals(0, view.getCurrentPlan().getActiveRecurringProbeCount());
        assertEquals(0, view.getCurrentPlan().getEstimatedCreditsPerCycle());
    }

    @Test
    void shouldExecuteCurrentPlanOnceAndReturnRunBinding() {
        seedScope();
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(1)
                .brandScopeId("scope-1")
                .configGenerationSnapshot(3)
                .activeRecurringProbeCount(10)
                .estimatedRecurringCreditsPerCycle(10)
                .build());
        when(brandSpaceTrackingSchedulerService.executeBrandSpaceOnce("scope-1"))
                .thenReturn(BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult.created(
                        "scope-1",
                        "run-manual-1",
                        "bs-manual-scope1-20260523-120000",
                        10
                ));
        when(probePlanRecordMapper.selectList(any())).thenReturn(List.of(
                ProbePlanRecord.builder()
                        .id("record-manual-1")
                        .planVersionId("plan-version-1")
                        .recordNo(2)
                        .changeTypeCode("MANUAL_RUN")
                        .executeModeCode("ONE_TIME")
                        .triggeredRunId("run-manual-1")
                        .immediateEstimatedCredits(10)
                        .recurringEstimatedCreditsPerCycle(0)
                        .createdAt(LocalDateTime.of(2026, 5, 23, 12, 0))
                        .build()
        ));
        when(probePlanRecordItemMapper.selectList(any())).thenReturn(List.of());

        BrandSpaceProbePlanExecuteOnceResponse response = service.executeCurrentPlanOnce("user-1", "scope-1");

        assertEquals("scope-1", response.getBrandSpaceId());
        assertEquals("run-manual-1", response.getRunId());
        assertEquals(10, response.getEstimatedCredits());
        assertEquals(10, response.getDeductedCredits());
        assertEquals(190, response.getCreditsRemainingAfterRun());
        assertEquals(Boolean.FALSE, response.getSkipped());
        assertEquals("record-manual-1", response.getProbePlan().getRecentRecords().get(0).getPlanRecordId());
    }

    @Test
    void shouldRejectExecuteOnceWhenCurrentPlanIsMissing() {
        seedScope();
        when(probePlanVersionMapper.selectOne(any())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.executeCurrentPlanOnce("user-1", "scope-1"));
        verify(brandSpaceTrackingSchedulerService, never()).executeBrandSpaceOnce(any());
    }

    private void seedScope() {
        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeName("探镜")
                .configGeneration(3)
                .scopeStatusCode(1)
                .build();
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        lenient().when(serviceAccessService.getServiceAccess("user-1")).thenReturn(
                com.geoprobe.dto.brandspace.ServiceAccessView.builder()
                        .limits(com.geoprobe.dto.brandspace.ServiceAccessView.Limits.builder()
                                .creditsPerProbe(1)
                                .build())
                        .build()
        );
        lenient().when(brandSpaceRunConfigService.resolveCurrentRunConfig(any(), any())).thenAnswer(invocation ->
                new BrandSpaceRunConfigService.CurrentRunConfig(
                        invocation.getArgument(1, Integer.class) == null ? 1 : invocation.getArgument(1, Integer.class),
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
}
