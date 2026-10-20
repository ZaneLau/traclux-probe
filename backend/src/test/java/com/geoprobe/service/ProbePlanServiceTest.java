package com.geoprobe.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbePlanServiceTest {

    @Mock
    private ProbePlanVersionMapper probePlanVersionMapper;
    @Mock
    private ProbePlanRecordMapper probePlanRecordMapper;
    @Mock
    private ProbePlanRecordItemMapper probePlanRecordItemMapper;
    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private BrandSpaceTopicMapper brandSpaceTopicMapper;
    @Mock
    private BrandSpacePromptMapper brandSpacePromptMapper;

    private ProbePlanService service;

    @BeforeEach
    void setUp() {
        service = new ProbePlanService(
                probePlanVersionMapper,
                probePlanRecordMapper,
                probePlanRecordItemMapper,
                brandScopeMapper,
                brandSpaceTopicMapper,
                brandSpacePromptMapper
        );
    }

    @Test
    void currentPlanStatusShouldExposeDraftPendingWhenConfigReadyButNoCurrentVersion() {
        when(probePlanVersionMapper.selectOne(any())).thenReturn(null);

        ProbePlanService.PlanStatusSnapshot snapshot = service.currentPlanStatus("scope-1", true, 2);

        assertEquals("draft_pending", snapshot.status());
        assertEquals(null, snapshot.versionNo());
        assertEquals(null, snapshot.estimatedCreditsPerCycle());
    }

    @Test
    void currentPlanStatusShouldExposeNeedsReviewWhenConfigGenerationDrifts() {
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(3)
                .activeRecurringProbeCount(10)
                .estimatedRecurringCreditsPerCycle(10)
                .configGenerationSnapshot(2)
                .build());

        ProbePlanService.PlanStatusSnapshot snapshot = service.currentPlanStatus("scope-1", true, 4);

        assertEquals("needs_review", snapshot.status());
        assertEquals(3, snapshot.versionNo());
        assertEquals(10, snapshot.activeRecurringProbeCount());
        assertEquals(10, snapshot.estimatedCreditsPerCycle());
        assertEquals("plan-version-1", snapshot.planVersionId());
    }

    @Test
    void currentPlanStatusShouldExposeConfirmedWhenConfigGenerationMatches() {
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(3)
                .activeRecurringProbeCount(10)
                .estimatedRecurringCreditsPerCycle(10)
                .configGenerationSnapshot(4)
                .build());

        ProbePlanService.PlanStatusSnapshot snapshot = service.currentPlanStatus("scope-1", true, 4);

        assertEquals("confirmed", snapshot.status());
        assertEquals(3, snapshot.versionNo());
        assertEquals(10, snapshot.activeRecurringProbeCount());
        assertEquals(10, snapshot.estimatedCreditsPerCycle());
        assertEquals("plan-version-1", snapshot.planVersionId());
    }

    @Test
    void currentPlanStatusShouldExposeUnavailableWhenConfigIsNotReady() {
        ProbePlanService.PlanStatusSnapshot snapshot = service.currentPlanStatus("scope-1", false, 4);

        assertEquals("unavailable", snapshot.status());
        assertEquals(null, snapshot.versionNo());
        assertEquals(null, snapshot.planVersionId());
    }

    @Test
    void snapshotInitialConfirmedPlanShouldPersistVersionRecordAndItems() {
        when(brandScopeMapper.selectById("scope-1")).thenReturn(BrandScope.builder()
                .id("scope-1")
                .scopeName("探镜")
                .configGeneration(2)
                .build());
        when(probePlanVersionMapper.selectOne(any())).thenReturn(null);
        when(probePlanRecordMapper.selectOne(any())).thenReturn(null);
        when(brandSpaceTopicMapper.selectList(any())).thenReturn(List.of(
                BrandSpaceTopic.builder()
                        .id("topic-1")
                        .name("购买决策")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(any())).thenReturn(List.of(
                BrandSpacePrompt.builder()
                        .id("probe-1")
                        .topicId("topic-1")
                        .seqNo(1)
                        .promptText("探镜适合什么公司？")
                        .sourceCode(1)
                        .statusCode(1)
                        .build(),
                BrandSpacePrompt.builder()
                        .id("probe-2")
                        .topicId("topic-1")
                        .seqNo(2)
                        .promptText("AI 可见性工具怎么选？")
                        .sourceCode(0)
                        .statusCode(0)
                        .build()
        ));

        ProbePlanService.SnapshotRef ref = service.snapshotInitialConfirmedPlan(
                new ProbePlanService.SnapshotCommand(
                        "org-1",
                        "brand-1",
                        "scope-1",
                        "探镜",
                        "user-1",
                        "run-1"
                )
        );

        assertNotNull(ref.planVersionId());
        assertNotNull(ref.planRecordId());
        assertEquals(1, ref.versionNo());
        assertEquals(1, ref.recordNo());
        assertEquals(1, ref.enabledProbeCount());

        ArgumentCaptor<ProbePlanVersion> versionCaptor = ArgumentCaptor.forClass(ProbePlanVersion.class);
        verify(probePlanVersionMapper).insert(versionCaptor.capture());
        ProbePlanVersion version = versionCaptor.getValue();
        assertEquals("scope-1", version.getBrandScopeId());
        assertEquals(2, version.getConfigGenerationSnapshot());
        assertEquals(1, version.getActiveRecurringProbeCount());
        assertTrue(Boolean.TRUE.equals(version.getIsCurrent()));
        assertEquals("probe_plan_b1", version.getSnapshotJson().get("contractVersion"));

        ArgumentCaptor<ProbePlanRecord> recordCaptor = ArgumentCaptor.forClass(ProbePlanRecord.class);
        verify(probePlanRecordMapper).insert(recordCaptor.capture());
        ProbePlanRecord record = recordCaptor.getValue();
        assertEquals(version.getId(), record.getPlanVersionId());
        assertEquals("INITIAL_CONFIRM", record.getChangeTypeCode());
        assertEquals("RECURRING", record.getExecuteModeCode());
        assertEquals("run-1", record.getTriggeredRunId());

        ArgumentCaptor<ProbePlanRecordItem> itemCaptor = ArgumentCaptor.forClass(ProbePlanRecordItem.class);
        verify(probePlanRecordItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        List<ProbePlanRecordItem> items = itemCaptor.getAllValues();
        assertEquals("ADD", items.get(0).getActionCode());
        assertEquals("DISABLE", items.get(1).getActionCode());
        assertEquals("探镜适合什么公司？", items.get(0).getProbeTextSnapshot());
        assertEquals(1, items.get(0).getSourceCodeSnapshot());
    }

    @Test
    void recordScheduledRunPlanShouldAppendRecordAgainstCurrentVersion() {
        when(probePlanVersionMapper.selectOne(any())).thenReturn(ProbePlanVersion.builder()
                .id("plan-version-1")
                .versionNo(7)
                .build());
        when(brandScopeMapper.selectById("scope-1")).thenReturn(BrandScope.builder()
                .id("scope-1")
                .scopeName("探镜")
                .configGeneration(4)
                .build());
        when(probePlanRecordMapper.selectOne(any())).thenReturn(ProbePlanRecord.builder()
                .recordNo(2)
                .build());
        when(brandSpaceTopicMapper.selectList(any())).thenReturn(List.of(
                BrandSpaceTopic.builder()
                        .id("topic-1")
                        .name("购买决策")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(any())).thenReturn(List.of(
                BrandSpacePrompt.builder()
                        .id("probe-1")
                        .topicId("topic-1")
                        .seqNo(1)
                        .promptText("探镜适合什么公司？")
                        .sourceCode(1)
                        .statusCode(1)
                        .build()
        ));

        ProbePlanService.SnapshotRef ref = service.recordScheduledRunPlan(
                new ProbePlanService.SnapshotCommand(
                        "org-1",
                        "brand-1",
                        "scope-1",
                        "探镜",
                        "user-1",
                        "run-2",
                        8
                )
        );

        assertEquals("plan-version-1", ref.planVersionId());
        assertEquals(7, ref.versionNo());
        assertEquals(3, ref.recordNo());
        assertEquals(8, ref.enabledProbeCount());
        verify(probePlanVersionMapper, org.mockito.Mockito.never()).insert(any(ProbePlanVersion.class));

        ArgumentCaptor<ProbePlanRecord> recordCaptor = ArgumentCaptor.forClass(ProbePlanRecord.class);
        verify(probePlanRecordMapper).insert(recordCaptor.capture());
        ProbePlanRecord record = recordCaptor.getValue();
        assertEquals("plan-version-1", record.getPlanVersionId());
        assertEquals("SCHEDULED_RUN", record.getChangeTypeCode());
        assertEquals("RECURRING", record.getExecuteModeCode());
        assertEquals(8, record.getImmediateEstimatedCredits());
        assertEquals(8, record.getRecurringEstimatedCreditsPerCycle());
        assertEquals("run-2", record.getTriggeredRunId());
        assertEquals(8, record.getSummaryJson().get("executionTargetProbeCount"));

        ArgumentCaptor<ProbePlanRecordItem> itemCaptor = ArgumentCaptor.forClass(ProbePlanRecordItem.class);
        verify(probePlanRecordItemMapper).insert(itemCaptor.capture());
        assertEquals("KEEP", itemCaptor.getValue().getActionCode());
        assertEquals("RECURRING", itemCaptor.getValue().getExecuteModeCode());
    }
}
