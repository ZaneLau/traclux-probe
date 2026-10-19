package com.geoprobe.service;

import com.geoprobe.dto.brandspace.BrandSpaceRunDetailView;
import com.geoprobe.dto.brandspace.BrandSpaceRunHistoryView;
import com.geoprobe.dto.brandspace.RunEventPageView;
import com.geoprobe.dto.brandspace.RunEventView;
import com.geoprobe.dto.brandspace.RunView;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanVersion;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunEventRepository;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanVersionMapper;
import com.geoprobe.mapper.v1.QuotaLedgerMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceRunReadServiceTest {

    @Mock
    private ServiceAccessService serviceAccessService;
    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private DiagnosisEventRecordMapper diagnosisEventRecordMapper;
    @Mock
    private DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    @Mock
    private ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    @Mock
    private ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    @Mock
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    @Mock
    private ProbePlanRecordMapper probePlanRecordMapper;
    @Mock
    private ProbePlanVersionMapper probePlanVersionMapper;
    @Mock
    private QuotaLedgerMapper quotaLedgerMapper;
    @Mock
    private ObjectProvider<KnowledgeAssetRunEventRepository> knowledgeAssetRunEventRepositoryProvider;
    @Mock
    private RunReadService runReadService;

    private BrandSpaceRunReadService brandSpaceRunReadService;

    @BeforeEach
    void setUp() {
        brandSpaceRunReadService = new BrandSpaceRunReadService(
                serviceAccessService,
                brandScopeMapper,
                diagnosisTaskMapper,
                diagnosisEventRecordMapper,
                diagnosisProbeRecordMapper,
                probeDispatchAttemptMapper,
                probeEvidenceRecordMapper,
                diagnosisReportRecordMapper,
                probePlanRecordMapper,
                probePlanVersionMapper,
                quotaLedgerMapper,
                knowledgeAssetRunEventRepositoryProvider,
                runReadService
        );
    }

    @Test
    void listRunsShouldBuildHistoryRowsFromRealRunTruth() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .scopeName("美厨物语")
                .scopeStatusCode(1)
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("run-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .statusCode(2)
                .targetProbeCount(14)
                .samplingCompletedCount(12)
                .samplingFailedCount(2)
                .brandNameSnapshot("美厨物语")
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "probePlanBinding", Map.of(
                                        "planVersionId", "plan-version-1",
                                        "planRecordId", "plan-record-1",
                                        "versionNo", 2,
                                        "recordNo", 5,
                                        "enabledProbeCount", 14
                                ),
                                "scheduleDecision", Map.of(
                                        "triggerMode", "scheduled_tracking",
                                        "targetProbeCount", 14,
                                        "coverageStrategy", "balanced_weekly_coverage",
                                        "humanReason", "今天优先补齐最近一周未覆盖主题。",
                                        "selectedTopics", List.of(
                                                Map.of(
                                                        "topicId", "topic-1",
                                                        "topicName", "品牌选择",
                                                        "allocatedProbeCount", 8,
                                                        "priorityScore", 0.95d,
                                                        "reasonTags", List.of("补覆盖")
                                                ),
                                                Map.of(
                                                        "topicId", "topic-2",
                                                        "topicName", "渠道评价",
                                                        "allocatedProbeCount", 6,
                                                        "priorityScore", 0.72d,
                                                        "reasonTags", List.of("均衡")
                                                )
                                        )
                                )
                        )
                ))
                .createdAt(LocalDateTime.of(2026, 5, 9, 9, 0))
                .startedAt(LocalDateTime.of(2026, 5, 9, 9, 5))
                .completedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .updatedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .build();
        DiagnosisReportRecord report = DiagnosisReportRecord.builder()
                .diagnosisTaskId("run-1")
                .brandScopeId("scope-1")
                .generatedAt(LocalDateTime.of(2026, 5, 9, 9, 41))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of(
                DiagnosisProbeRecord.builder().diagnosisTaskId("run-1").build(),
                DiagnosisProbeRecord.builder().diagnosisTaskId("run-1").build()
        ));
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of(
                ProbeEvidenceRecord.builder().diagnosisTaskId("run-1").build(),
                ProbeEvidenceRecord.builder().diagnosisTaskId("run-1").build(),
                ProbeEvidenceRecord.builder().diagnosisTaskId("run-1").build()
        ));
        when(diagnosisReportRecordMapper.selectList(any())).thenReturn(List.of(report));

        BrandSpaceRunHistoryView view = brandSpaceRunReadService.listRuns("user-1", "scope-1", "2026-05-01", "2026-05-09");

        assertNotNull(view);
        assertEquals("scope-1", view.getBrandSpaceId());
        assertEquals("美厨物语", view.getBrandName());
        assertEquals(1, view.getTotalRuns());
        assertEquals("日常跟踪", view.getItems().getFirst().getTriggerLabel());
        assertEquals("已完成", view.getItems().getFirst().getStatusLabel());
        assertEquals(Integer.valueOf(14), view.getItems().getFirst().getPlannedProbeCount());
        assertEquals(Integer.valueOf(12), view.getItems().getFirst().getCompletedProbeCount());
        assertEquals(Integer.valueOf(2), view.getItems().getFirst().getFailedProbeCount());
        assertEquals(Integer.valueOf(2), view.getItems().getFirst().getCoverageTopicCount());
        assertEquals(Integer.valueOf(3), view.getItems().getFirst().getEvidenceCount());
        assertNotNull(view.getItems().getFirst().getPlanTrace());
        assertEquals(Integer.valueOf(2), view.getItems().getFirst().getPlanTrace().getPlanVersionNo());
        assertEquals("RECURRING", view.getItems().getFirst().getPlanTrace().getExecuteModeCode());
        assertEquals(Integer.valueOf(14), view.getItems().getFirst().getPlanTrace().getEstimatedCredits());
        assertEquals("PENDING", view.getItems().getFirst().getPlanTrace().getCreditReconciliationStatus());
        assertEquals("PROBE_PLAN_BINDING", view.getItems().getFirst().getPlanTrace().getEstimatedCreditsSourceCode());
    }

    @Test
    void listRunsShouldEnrichPlanTraceFromPlanRecordAndCreditLedger() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .scopeName("美厨物语")
                .scopeStatusCode(1)
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("run-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .statusCode(2)
                .targetProbeCount(12)
                .samplingCompletedCount(12)
                .samplingFailedCount(0)
                .brandNameSnapshot("美厨物语")
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "scheduleDecision", Map.of(
                                        "triggerMode", "scheduled_tracking",
                                        "targetProbeCount", 12,
                                        "selectedTopics", List.of(
                                                Map.of("topicId", "topic-1", "topicName", "品牌选择", "allocatedProbeCount", 12)
                                        )
                                )
                        )
                ))
                .createdAt(LocalDateTime.of(2026, 5, 9, 9, 0))
                .startedAt(LocalDateTime.of(2026, 5, 9, 9, 5))
                .completedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .updatedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of());
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of());
        when(diagnosisReportRecordMapper.selectList(any())).thenReturn(List.of());
        when(probePlanRecordMapper.selectOne(any())).thenReturn(ProbePlanRecord.builder()
                .id("plan-record-1")
                .brandScopeId("scope-1")
                .planVersionId("plan-version-1")
                .recordNo(7)
                .triggeredRunId("run-1")
                .immediateEstimatedCredits(12)
                .build());
        when(probePlanVersionMapper.selectById("plan-version-1")).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(3)
                .build());
        when(quotaLedgerMapper.selectList(any())).thenReturn(List.of(QuotaLedger.builder()
                .id("ledger-1")
                .diagnosisTaskId("run-1")
                .ledgerTypeCode("CONSUME")
                .probeDelta(-12L)
                .planVersionId("plan-version-1")
                .planRecordId("plan-record-1")
                .build()));

        BrandSpaceRunHistoryView view = brandSpaceRunReadService.listRuns("user-1", "scope-1", "2026-05-01", "2026-05-09");

        var trace = view.getItems().getFirst().getPlanTrace();
        assertEquals("plan-version-1", trace.getPlanVersionId());
        assertEquals(Integer.valueOf(3), trace.getPlanVersionNo());
        assertEquals("plan-record-1", trace.getPlanRecordId());
        assertEquals(Integer.valueOf(7), trace.getPlanRecordNo());
        assertEquals(Integer.valueOf(12), trace.getEstimatedCredits());
        assertEquals(Integer.valueOf(12), trace.getChargedCredits());
        assertEquals(Integer.valueOf(0), trace.getCreditDelta());
        assertEquals("MATCHED", trace.getCreditReconciliationStatus());
        assertEquals("PROBE_PLAN_RECORD", trace.getEstimatedCreditsSourceCode());
        assertEquals("QUOTA_LEDGER_CONSUME", trace.getChargedCreditsSourceCode());
    }

    @Test
    void listRunsShouldExposeCreditReconciliationDifferenceWithoutOverwritingLedger() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .scopeName("美厨物语")
                .scopeStatusCode(1)
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("run-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .statusCode(2)
                .targetProbeCount(25)
                .samplingCompletedCount(25)
                .samplingFailedCount(0)
                .brandNameSnapshot("美厨物语")
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "probePlanBinding", Map.of("enabledProbeCount", 25),
                                "scheduleDecision", Map.of(
                                        "triggerMode", "scheduled_tracking",
                                        "targetProbeCount", 25,
                                        "selectedTopics", List.of(Map.of(
                                                "topicId", "topic-1",
                                                "topicName", "品牌选择",
                                                "allocatedProbeCount", 25
                                        ))
                                )
                        )
                ))
                .createdAt(LocalDateTime.of(2026, 5, 9, 9, 0))
                .startedAt(LocalDateTime.of(2026, 5, 9, 9, 5))
                .completedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .updatedAt(LocalDateTime.of(2026, 5, 9, 9, 40))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of());
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of());
        when(diagnosisReportRecordMapper.selectList(any())).thenReturn(List.of());
        when(quotaLedgerMapper.selectList(any())).thenReturn(List.of(QuotaLedger.builder()
                .id("ledger-1")
                .diagnosisTaskId("run-1")
                .ledgerTypeCode("CONSUME")
                .probeDelta(-1L)
                .build()));

        BrandSpaceRunHistoryView view = brandSpaceRunReadService.listRuns("user-1", "scope-1", "2026-05-01", "2026-05-09");

        var trace = view.getItems().getFirst().getPlanTrace();
        assertEquals(Integer.valueOf(25), trace.getEstimatedCredits());
        assertEquals(Integer.valueOf(1), trace.getChargedCredits());
        assertEquals(Integer.valueOf(-24), trace.getCreditDelta());
        assertEquals("DIFFERENT", trace.getCreditReconciliationStatus());
        assertEquals("PROBE_PLAN_BINDING", trace.getEstimatedCreditsSourceCode());
        assertEquals("QUOTA_LEDGER_CONSUME", trace.getChargedCreditsSourceCode());
    }

    @Test
    void getRunDetailShouldExposeProbeExecutionsAndRawEvents() {
        BrandScope scope = BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .scopeName("美厨物语")
                .scopeStatusCode(1)
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("run-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .statusCode(2)
                .targetProbeCount(2)
                .samplingCompletedCount(2)
                .samplingFailedCount(0)
                .brandNameSnapshot("美厨物语")
                .taskOrderJson(Map.of(
                        "runGoal", "geo_brand_visibility_diagnosis",
                        "businessHints", Map.of(
                                "scheduleDecision", Map.of(
                                        "triggerMode", "scheduled_tracking_manual_acceptance",
                                        "targetProbeCount", 2,
                                        "coverageStrategy", "balanced_weekly_coverage",
                                        "humanReason", "人工验收重跑",
                                        "selectedTopics", List.of(
                                                Map.of(
                                                        "topicId", "topic-1",
                                                        "topicName", "品牌选择",
                                                        "allocatedProbeCount", 2,
                                                        "priorityScore", 0.95d,
                                                        "reasonTags", List.of("验收")
                                                )
                                        ),
                                        "executionPrompts", List.of(
                                                Map.of(
                                                        "seqNo", 1,
                                                        "promptId", "prompt-1",
                                                        "topicId", "topic-1",
                                                        "topicName", "品牌选择",
                                                        "promptText", "美厨物语值得买吗？"
                                                ),
                                                Map.of(
                                                        "seqNo", 2,
                                                        "promptId", "prompt-2",
                                                        "topicId", "topic-1",
                                                        "topicName", "品牌选择",
                                                        "promptText", "美厨物语和竞品相比怎么样？"
                                                )
                                        )
                                )
                        )
                ))
                .taskMetadataJson(Map.of(
                        "planVersionId", "plan-version-1",
                        "planRecordId", "plan-record-1",
                        "probePlanVersionNo", 2,
                        "probePlanRecordNo", 5,
                        "runTypeCode", "MANUAL_ONCE"
                ))
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 15))
                .startedAt(LocalDateTime.of(2026, 5, 9, 23, 16))
                .completedAt(LocalDateTime.of(2026, 5, 9, 23, 20))
                .updatedAt(LocalDateTime.of(2026, 5, 9, 23, 20))
                .build();
        DiagnosisReportRecord report = DiagnosisReportRecord.builder()
                .diagnosisTaskId("run-1")
                .brandScopeId("scope-1")
                .summaryJson(Map.of("mentionRate", 0.5d))
                .generatedAt(LocalDateTime.of(2026, 5, 9, 23, 20))
                .build();
        DiagnosisProbeRecord probe1 = DiagnosisProbeRecord.builder()
                .id("probe-1")
                .diagnosisTaskId("run-1")
                .probeRef("P1")
                .seqNo(1)
                .renderText("美厨物语值得买吗？")
                .statusCode(2)
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 16, 30))
                .build();
        DiagnosisProbeRecord probe2 = DiagnosisProbeRecord.builder()
                .id("probe-2")
                .diagnosisTaskId("run-1")
                .probeRef("P2")
                .seqNo(2)
                .renderText("美厨物语和竞品相比怎么样？")
                .statusCode(2)
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 17, 0))
                .build();
        DiagnosisEventRecord resultEvent = DiagnosisEventRecord.builder()
                .diagnosisTaskId("run-1")
                .eventType("probe_result")
                .provisional(false)
                .payloadJson(Map.of(
                        "probeId", "probe-1",
                        "renderedPrompt", "美厨物语值得买吗？",
                        "modelId", "doubao-seed-1-6-lite-251015",
                        "status", "succeeded",
                        "latencyMs", 1200,
                        "searchEvidence", List.of(Map.of("domain", "example.com"))
                ))
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 16, 45))
                .build();
        ProbeDispatchAttempt attempt1 = ProbeDispatchAttempt.builder()
                .diagnosisTaskId("run-1")
                .probeId("probe-1")
                .attemptNo(1)
                .selectedFinal(true)
                .provider("doubao")
                .modelId("doubao-seed-1-6-lite-251015")
                .latencyMs(1200)
                .statusCode(1)
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 16, 45))
                .build();
        ProbeDispatchAttempt attempt2 = ProbeDispatchAttempt.builder()
                .diagnosisTaskId("run-1")
                .probeId("probe-2")
                .attemptNo(1)
                .selectedFinal(true)
                .provider("doubao")
                .modelId("doubao-seed-2-0-pro-260215")
                .latencyMs(980)
                .statusCode(1)
                .createdAt(LocalDateTime.of(2026, 5, 9, 23, 17, 5))
                .build();
        RunView runView = RunView.builder()
                .runId("run-1")
                .brandSpaceId("scope-1")
                .brandName("美厨物语")
                .status("completed")
                .startedAt("2026-05-09T23:16:00")
                .completedAt("2026-05-09T23:20:00")
                .build();
        RunEventView milestone = RunEventView.builder()
                .type("run_completed")
                .createdAt("2026-05-09T23:20:00")
                .payload(Map.of("runId", "run-1"))
                .build();

        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(scope);
        when(diagnosisTaskMapper.selectById("run-1")).thenReturn(task);
        when(runReadService.getRunForBrandSpace("org-1", "scope-1", "run-1")).thenReturn(runView);
        when(runReadService.getRunEventsForBrandSpace("org-1", "scope-1", "run-1", null, 300))
                .thenReturn(RunEventPageView.builder().events(List.of(milestone)).build());
        when(diagnosisReportRecordMapper.selectList(any())).thenReturn(List.of(report));
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of(probe1, probe2));
        when(probeDispatchAttemptMapper.selectList(any())).thenReturn(List.of(attempt2, attempt1));
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of(
                ProbeEvidenceRecord.builder().diagnosisTaskId("run-1").probeId("probe-1").build(),
                ProbeEvidenceRecord.builder().diagnosisTaskId("run-1").probeId("probe-2").build()
        ));
        when(diagnosisEventRecordMapper.selectList(any())).thenReturn(List.of(resultEvent));

        BrandSpaceRunDetailView view = brandSpaceRunReadService.getRunDetail("user-1", "scope-1", "run-1");

        assertNotNull(view);
        assertEquals("scope-1", view.getBrandSpaceId());
        assertEquals("run-1", view.getRunId());
        assertNotNull(view.getPlanTrace());
        assertEquals("plan-version-1", view.getPlanTrace().getPlanVersionId());
        assertEquals(Integer.valueOf(2), view.getPlanTrace().getPlanVersionNo());
        assertEquals("ONE_TIME", view.getPlanTrace().getExecuteModeCode());
        assertEquals(Integer.valueOf(2), view.getPlanTrace().getEstimatedCredits());
        assertEquals(Integer.valueOf(1), view.getExecutionRecord().getSelectedTopicCount());
        assertEquals(2, view.getExecutionRecord().getProbeExecutions().size());
        assertEquals("topic-1", view.getExecutionRecord().getProbeExecutions().getFirst().getTopicId());
        assertEquals("美厨物语值得买吗？", view.getExecutionRecord().getProbeExecutions().getFirst().getRenderedPrompt());
        assertEquals("succeeded", view.getExecutionRecord().getProbeExecutions().getFirst().getStatus());
        assertEquals("doubao-seed-1-6-lite-251015", view.getExecutionRecord().getProbeExecutions().getFirst().getModelId());
        assertEquals(Integer.valueOf(1), view.getExecutionRecord().getProbeExecutions().getFirst().getEvidenceCount());
        assertEquals("geo_brand_visibility_diagnosis", view.getRawRecord().getTaskOrder().get("runGoal"));
        assertFalse(view.getRawRecord().getTaskOrder().containsKey("diagnosisGoal"));
        assertFalse(view.getRawRecord().getEvents().isEmpty());
    }
}
