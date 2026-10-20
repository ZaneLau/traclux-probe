package com.geoprobe.service;

import com.geoprobe.dto.workbench.WorkbenchDataProvenanceCardView;
import com.geoprobe.dto.workbench.WorkbenchDataProvenanceQuery;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanRecordItem;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordItemMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchDataProvenanceServiceTest {

    @Mock
    private ServiceAccessService serviceAccessService;
    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private ProbePlanRecordMapper probePlanRecordMapper;
    @Mock
    private ProbePlanRecordItemMapper probePlanRecordItemMapper;
    @Mock
    private DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    @Mock
    private ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    @Mock
    private ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    @Mock
    private ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    @Mock
    private QuotaLedgerMapper quotaLedgerMapper;
    @Mock
    private ObjectProvider<KnowledgeAssetEvidenceRepository> knowledgeAssetEvidenceRepositoryProvider;

    private WorkbenchDataProvenanceService service;

    @BeforeEach
    void setUp() {
        service = new WorkbenchDataProvenanceService(
                serviceAccessService,
                brandScopeMapper,
                diagnosisTaskMapper,
                probePlanRecordMapper,
                probePlanRecordItemMapper,
                diagnosisProbeRecordMapper,
                probeDispatchAttemptMapper,
                probeEvidenceRecordMapper,
                probeBrandMentionRecordMapper,
                quotaLedgerMapper,
                knowledgeAssetEvidenceRepositoryProvider
        );
    }

    @Test
    void shouldBuildDataProvenanceCardForSelectedProbe() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 30);
        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .scopeStatusCode(1)
                .build());
        when(diagnosisTaskMapper.selectById("run-1")).thenReturn(DiagnosisTask.builder()
                .id("run-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .brandScopeId("scope-1")
                .targetModelProduct("doubao-seed-1.8")
                .engineVersion("5.0.4")
                .targetProbeCount(8)
                .samplingCompletedCount(8)
                .samplingFailedCount(0)
                .statusCode(2)
                .completedAt(now)
                .taskMetadataJson(Map.of(
                        "planVersionId", "plan-version-1",
                        "planRecordId", "plan-record-1",
                        "probePlanVersionNo", 2,
                        "probePlanRecordNo", 5,
                        "runTypeCode", "SCHEDULED_RUN"
                ))
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "probePlanBinding", Map.of(
                                        "planVersionId", "plan-version-1",
                                        "planRecordId", "plan-record-1",
                                        "enabledProbeCount", 8
                                )
                        )
                ))
                .build());
        when(probePlanRecordMapper.selectById("plan-record-1")).thenReturn(ProbePlanRecord.builder()
                .id("plan-record-1")
                .brandScopeId("scope-1")
                .planVersionId("plan-version-1")
                .recordNo(5)
                .changeTypeCode("SCHEDULED_RUN")
                .executeModeCode("RECURRING")
                .immediateEstimatedCredits(8)
                .recurringEstimatedCreditsPerCycle(8)
                .build());
        when(probePlanRecordItemMapper.selectList(any())).thenReturn(List.of(
                ProbePlanRecordItem.builder()
                        .recordId("plan-record-1")
                        .planVersionId("plan-version-1")
                        .probeId("probe-1")
                        .topicId("topic-1")
                        .seqNoSnapshot(1)
                        .probeTextSnapshot("探镜适合什么公司？")
                        .actionCode("KEEP")
                        .executeModeCode("RECURRING")
                        .build()
        ));
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of(
                DiagnosisProbeRecord.builder()
                        .id("probe-1")
                        .diagnosisTaskId("run-1")
                        .probeRef("P-001")
                        .seqNo(1)
                        .renderText("探镜适合什么公司？")
                        .statusCode(2)
                        .build()
        ));
        when(probeDispatchAttemptMapper.selectList(any())).thenReturn(List.of(
                ProbeDispatchAttempt.builder()
                        .id("attempt-1")
                        .diagnosisTaskId("run-1")
                        .probeId("probe-1")
                        .selectedFinal(true)
                        .createdAt(now)
                        .build()
        ));
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of(
                ProbeEvidenceRecord.builder()
                        .id("evidence-1")
                        .diagnosisTaskId("run-1")
                        .probeId("probe-1")
                        .domain("traclux.com")
                        .url("https://traclux.com")
                        .title("探镜官网")
                        .sourceTypeCode("official_site")
                        .createdAt(now)
                        .build()
        ));
        when(probeBrandMentionRecordMapper.selectList(any())).thenReturn(List.of(
                ProbeBrandMentionRecord.builder()
                        .diagnosisTaskId("run-1")
                        .probeId("probe-1")
                        .brandName("探镜")
                        .mentionedFlag(true)
                        .recommendedFlag(true)
                        .positionIndex(1)
                        .build()
        ));
        when(quotaLedgerMapper.selectList(any())).thenReturn(List.of(
                QuotaLedger.builder()
                        .id("ledger-1")
                        .diagnosisTaskId("run-1")
                        .probeId("probe-1")
                        .ledgerTypeCode("CONSUME")
                        .probeDelta(-1L)
                        .balanceAfter(99L)
                        .sourceTypeCode("RUN_BATCH")
                        .createdAt(now)
                        .build()
        ));

        WorkbenchDataProvenanceCardView card = service.getCard("user-1",
                new WorkbenchDataProvenanceQuery(
                        "scope-1",
                        "run-1",
                        "topic-1",
                        null,
                        "probe-1",
                        "overview_trend_point",
                        "visibility:2026-05-22",
                        "visibilityScore",
                        "AI 可见度",
                        "14.6",
                        "last_14d",
                        null,
                        null,
                        "overview_trend_point:visibility:visibilityScore",
                        null,
                        null,
                        null,
                        List.<String>of(),
                        null,
                        null,
                        null,
                        null,
                        12,
                        2,
                        16.7,
                        12.0,
                        8.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        assertEquals("workbench_data_provenance_card_b1", card.getContractVersion());
        assertEquals("数据溯源卡", card.getTitle());
        assertEquals("visibilityScore", card.getAnchor().getMetricKey());
        assertEquals("AI 可见度", card.getResultExplanation().getMetricLabel());
        assertEquals("近 14 天", card.getResultExplanation().getTimeWindowLabel());
        assertEquals("second_batch", card.getResultExplanation().getStatus());
        assertEquals("第二批：维度口径待接入", card.getResultExplanation().getStatusLabel());
        assertEquals(0, card.getResultExplanation().getComposition().size());
        assertEquals("run-1", card.getRun().getRunId());
        assertEquals("completed", card.getRun().getStatus());
        assertEquals("plan-record-1", card.getPlan().getPlanRecordId());
        assertEquals("SCHEDULED_RUN", card.getPlan().getChangeTypeCode());
        assertEquals(8, card.getPlan().getEstimatedCredits());
        assertEquals(1, card.getPlan().getSampleProbes().size());
        assertEquals("探镜适合什么公司？", card.getPlan().getSampleProbes().get(0).getPromptText());
        assertEquals(1, card.getEvidence().getSelectedProbeCount());
        assertEquals(1, card.getEvidence().getAttemptCount());
        assertEquals(1, card.getEvidence().getCitationCount());
        assertEquals("traclux.com", card.getEvidence().getCitations().get(0).getDomain());
        assertEquals(1L, card.getBilling().getConsumedCredits());
        assertEquals(99L, card.getBilling().getLatestBalanceAfter());
        assertNotNull(card.getBilling().getLedgers().get(0).getCreatedAt());
    }

    @Test
    void shouldBuildVisibilityFormulaWithoutRateContext() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 30);
        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(brandScopeMapper.selectById("scope-1")).thenReturn(BrandScope.builder()
                .id("scope-1")
                .organizationId("org-1")
                .scopeStatusCode(1)
                .build());
        when(diagnosisTaskMapper.selectById("run-1")).thenReturn(DiagnosisTask.builder()
                .id("run-1")
                .brandScopeId("scope-1")
                .targetProbeCount(25)
                .samplingCompletedCount(25)
                .statusCode(2)
                .completedAt(now)
                .build());
        when(probePlanRecordMapper.selectOne(any())).thenReturn(null);
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of());
        when(probeDispatchAttemptMapper.selectList(any())).thenReturn(List.of());
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(List.of());
        when(probeBrandMentionRecordMapper.selectList(any())).thenReturn(List.of());
        when(quotaLedgerMapper.selectList(any())).thenReturn(List.of());

        WorkbenchDataProvenanceCardView card = service.getCard("user-1",
                new WorkbenchDataProvenanceQuery(
                        "scope-1",
                        "run-1",
                        null,
                        null,
                        null,
                        "dashboard_metric",
                        "current_performance",
                        "visibilityScore",
                        "AI 可见度",
                        "14.6",
                        "last_14_days",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.<String>of(),
                        null,
                        null,
                        null,
                        null,
                        25,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        assertEquals("AI 可见度 = 提及率 × 35% + 推荐率 × 40% + Top3 出现率 × 25%。",
                card.getResultExplanation().getCalculation());
        assertTrue(card.getResultExplanation().getComposition().isEmpty());
    }
}
