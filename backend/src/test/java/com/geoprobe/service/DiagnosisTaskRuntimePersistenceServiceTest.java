package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.Node3UsageCostSummary;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.LlmCallLog;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ModelInvocationRecord;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.knowledge.service.KnowledgeAssetIngestionService;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisNodeRunMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProgressSnapshotMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskCompetitorMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.IntegrationOutboxRecordMapper;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.LightConeV43VersionTruthSupport;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosisTaskRuntimePersistenceServiceTest {

    private final LlmCallLogMapper llmCallLogMapper = mock(LlmCallLogMapper.class);
    private final DiagnosisTaskMapper diagnosisTaskMapper = mock(DiagnosisTaskMapper.class);
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper = mock(DiagnosisProbeRecordMapper.class);
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper = mock(ProbeDispatchAttemptMapper.class);
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper = mock(ProbeEvidenceRecordMapper.class);
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper = mock(ProbeBrandMentionRecordMapper.class);
    private final ModelInvocationRecordMapper modelInvocationRecordMapper = mock(ModelInvocationRecordMapper.class);
    private final DiagnosisProgressSnapshotMapper diagnosisProgressSnapshotMapper = mock(DiagnosisProgressSnapshotMapper.class);
    private final ObjectProvider<KnowledgeAssetIngestionService> knowledgeAssetIngestionServiceProvider = mock(ObjectProvider.class);

    @Test
    void shouldPromotePendingTaskToRunningWhenProgressArrives() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-progress")
                .organizationId("org-001")
                .brandScopeId("scope-001")
                .statusCode(0)
                .samplingCompletedCount(0)
                .samplingGeneratedCount(0)
                .samplingFailedCount(0)
                .build();

        when(diagnosisTaskMapper.selectById("diag-progress")).thenReturn(task);
        when(diagnosisProgressSnapshotMapper.selectById("diag-progress")).thenReturn(null);

        service.onProgress(
                "diag-progress",
                "NODE3_SAMPLING",
                "正在向 AI 发送探测请求（1/50）...",
                4,
                11,
                1,
                50,
                1,
                49,
                false,
                40.56d
        );

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updatedTask = taskCaptor.getValue();
        assertEquals(1, updatedTask.getStatusCode());
        assertEquals(1, updatedTask.getSamplingCompletedCount());
        assertEquals(1, updatedTask.getSamplingGeneratedCount());
        assertEquals(0, updatedTask.getSamplingFailedCount());
        assertEquals(new BigDecimal("40.56"), updatedTask.getProgressPercent());
        assertTrue(updatedTask.getStartedAt() != null);

        ArgumentCaptor<DiagnosisProgressSnapshot> snapshotCaptor = ArgumentCaptor.forClass(DiagnosisProgressSnapshot.class);
        verify(diagnosisProgressSnapshotMapper).insert(snapshotCaptor.capture());
        DiagnosisProgressSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals("NODE3_SAMPLING", snapshot.getCurrentStepCode());
        assertEquals(new BigDecimal("40.56"), snapshot.getPercent());
        assertEquals(1, snapshot.getSamplingCompleted());
        assertEquals(1, snapshot.getSamplingGeneratedTotal());
    }

    @Test
    void shouldMirrorMentionsAndPreserveFailureProgressForSamplingFailures() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-failure")
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-failure")
                .organizationId("org-001")
                .brandScopeId("scope-001")
                .brandId("brand-001")
                .brandNameSnapshot("小米")
                .build();
        DiagnosisProgressSnapshot existingSnapshot = DiagnosisProgressSnapshot.builder()
                .diagnosisTaskId("diag-failure")
                .currentStepCode("NODE3_SAMPLING")
                .stepsCompleted(4)
                .stepsTotal(11)
                .lastEventSeq(99L)
                .build();

        when(diagnosisTaskMapper.selectById("diag-failure")).thenReturn(task);
        when(diagnosisProgressSnapshotMapper.selectById("diag-failure")).thenReturn(existingSnapshot, null);
        when(llmCallLogMapper.selectList(any())).thenReturn(List.of(
                LlmCallLog.builder()
                        .diagnosisId("diag-failure")
                        .module(1)
                        .costYuan(new BigDecimal("0.123456"))
                        .build()
        ));

        service.onDiagnosisSamplingFailed(diagnosis, samplingFailureTask());

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updatedTask = taskCaptor.getValue();
        assertEquals(new BigDecimal("68.00"), updatedTask.getProgressPercent());
        assertEquals(2, updatedTask.getSamplingGeneratedCount());
        assertEquals(1, updatedTask.getSamplingCompletedCount());
        assertEquals(1, updatedTask.getSamplingFailedCount());

        ArgumentCaptor<DiagnosisProgressSnapshot> progressCaptor = ArgumentCaptor.forClass(DiagnosisProgressSnapshot.class);
        verify(diagnosisProgressSnapshotMapper).insert(progressCaptor.capture());
        DiagnosisProgressSnapshot mirroredSnapshot = progressCaptor.getValue();
        assertEquals("FAILED", mirroredSnapshot.getCurrentStepCode());
        assertEquals(4, mirroredSnapshot.getStepsCompleted());
        assertEquals(11, mirroredSnapshot.getStepsTotal());
        assertEquals(new BigDecimal("68.00"), mirroredSnapshot.getPercent());
        assertEquals(1, mirroredSnapshot.getSamplingCompleted());
        assertEquals(2, mirroredSnapshot.getSamplingExpectedTotal());
        assertEquals(2, mirroredSnapshot.getSamplingGeneratedTotal());
        assertEquals(0, mirroredSnapshot.getSamplingMissingTotal());
        assertEquals(99L, mirroredSnapshot.getLastEventSeq());

        ArgumentCaptor<ProbeBrandMentionRecord> mentionCaptor = ArgumentCaptor.forClass(ProbeBrandMentionRecord.class);
        verify(probeBrandMentionRecordMapper, times(2)).insert(mentionCaptor.capture());
        List<ProbeBrandMentionRecord> mentions = mentionCaptor.getAllValues();
        assertTrue(mentions.stream().anyMatch(item ->
                Integer.valueOf(0).equals(item.getBrandRoleCode())
                        && "小米".equals(item.getBrandName())
                        && Boolean.TRUE.equals(item.getMentionedFlag())
        ));
        assertTrue(mentions.stream().anyMatch(item ->
                Integer.valueOf(1).equals(item.getBrandRoleCode())
                        && "华为".equals(item.getBrandName())
                        && Boolean.TRUE.equals(item.getMentionedFlag())
        ));
    }

    @Test
    void shouldMirrorCompetitorMentionsWhenTargetBrandIsNotDetected() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-competitor-only")
                .build();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-competitor-only")
                .organizationId("org-001")
                .brandScopeId("scope-001")
                .brandId("brand-001")
                .brandNameSnapshot("长虹")
                .targetModelProduct("mystery-model")
                .build();

        when(diagnosisTaskMapper.selectById("diag-competitor-only")).thenReturn(task);
        when(diagnosisProgressSnapshotMapper.selectById("diag-competitor-only")).thenReturn(null);
        when(llmCallLogMapper.selectList(any())).thenReturn(List.of());

        service.onDiagnosisSamplingFailed(diagnosis, competitorOnlyNoTargetBrandTask());

        ArgumentCaptor<ProbeBrandMentionRecord> mentionCaptor = ArgumentCaptor.forClass(ProbeBrandMentionRecord.class);
        verify(probeBrandMentionRecordMapper).insert(mentionCaptor.capture());
        ProbeBrandMentionRecord mention = mentionCaptor.getValue();
        assertEquals(Integer.valueOf(1), mention.getBrandRoleCode());
        assertEquals("三星", mention.getBrandName());
        assertEquals(Boolean.TRUE, mention.getMentionedFlag());

        ArgumentCaptor<ModelInvocationRecord> invocationCaptor = ArgumentCaptor.forClass(ModelInvocationRecord.class);
        verify(modelInvocationRecordMapper, times(2)).insert(invocationCaptor.capture());
        assertTrue(invocationCaptor.getAllValues().stream().anyMatch(invocation ->
                "mystery-provider".equals(invocation.getProvider())
                        && "mystery-model".equals(invocation.getModelId())
        ));
        assertTrue(invocationCaptor.getAllValues().stream().anyMatch(invocation ->
                "NODE3_USAGE_SUMMARY".equals(invocation.getNodeCode())
                        && new BigDecimal("0.000132").equals(invocation.getTotalCostYuan())
        ));
    }

    @Test
    void shouldNotDoubleCountNode3UsageSummaryInOpsMetrics() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-ops")
                .targetProbeCount(10)
                .samplingGeneratedCount(10)
                .samplingCompletedCount(10)
                .samplingFailedCount(0)
                .build();

        when(diagnosisTaskMapper.selectById("diag-ops")).thenReturn(task);
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(List.of());
        when(modelInvocationRecordMapper.selectList(any())).thenReturn(List.of(
                invocation("NODE1_BLUEPRINTS", 100, 200, 300, "0.010000", 1_000),
                invocation("NODE3_SAMPLING", "probe-1", 20, 1_000, 1_020, "0.080000", 20_000),
                invocation("NODE3_SAMPLING", "probe-2", 30, 1_500, 1_530, "0.120000", 30_000),
                invocation("NODE3_SAMPLING", null, 50, 2_500, 2_550, "0.200000", 40_000),
                invocation("NODE3_USAGE_SUMMARY", 50, 2_500, 2_550, "0.200000", null)
        ));

        DiagnosisOpsMetricsResponse metrics = service.buildOpsMetrics("diag-ops", 2, false);

        assertEquals(new BigDecimal("0.210000"), metrics.getTotalLlmCostYuan());
        assertEquals(new BigDecimal("0.200000"), metrics.getNode3LlmCostYuan());
        assertEquals(new BigDecimal("0.010000"), metrics.getNonSamplingLlmCostYuan());
        assertEquals(150L, metrics.getFullChainPromptTokenTotal());
        assertEquals(2700L, metrics.getFullChainCompletionTokenTotal());
        assertEquals(2850L, metrics.getFullChainTokenTotal());
        assertEquals(50L, metrics.getNode3PromptTokenTotal());
        assertEquals(2500L, metrics.getNode3CompletionTokenTotal());
        assertEquals(2550L, metrics.getNode3TokenTotal());
        assertEquals(10, metrics.getTotalApiCalls());
        assertEquals(30_000, metrics.getP95LatencyMs());
    }

    @Test
    void shouldPreserveRuntimeBillingSidecarsWhenUpsertingTaskShell() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask existing = DiagnosisTask.builder()
                .id("diag-runtime")
                .taskMetadataJson(Map.of(
                        "brandName", "旧品牌",
                        "_runtime", Map.of(
                                "billing", Map.of(
                                        "billingState", "charged",
                                        "billingTimeline", List.of(Map.of("state", "charged", "at", "2026-04-29T09:00:00"))
                                )
                        )
                ))
                .build();
        DiagnosisTask incoming = DiagnosisTask.builder()
                .id("diag-runtime")
                .taskMetadataJson(Map.of("brandName", "新品牌"))
                .build();

        when(diagnosisTaskMapper.selectById("diag-runtime")).thenReturn(existing);

        ReflectionTestUtils.invokeMethod(service, "upsertDiagnosisTask", incoming);

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updated = taskCaptor.getValue();
        assertEquals("新品牌", updated.getTaskMetadataJson().get("brandName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) updated.getTaskMetadataJson().get("_runtime");
        @SuppressWarnings("unchecked")
        Map<String, Object> billing = (Map<String, Object>) runtime.get("billing");
        assertEquals("charged", billing.get("billingState"));
    }

    @Test
    void shouldMergeTaskBillingMetadataWithoutDroppingContextSnapshot() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask existing = DiagnosisTask.builder()
                .id("diag-billing")
                .taskMetadataJson(Map.of(
                        "brandName", "探镜",
                        "category", "运动相机"
                ))
                .build();

        when(diagnosisTaskMapper.selectById("diag-billing")).thenReturn(existing);

        service.upsertTaskBillingMetadata("diag-billing", Map.of(
                "billingState", "charged",
                "billingTimeline", List.of(Map.of("state", "charged", "at", "2026-04-29T10:00:00"))
        ));

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updated = taskCaptor.getValue();
        assertEquals("探镜", updated.getTaskMetadataJson().get("brandName"));
        assertEquals("运动相机", updated.getTaskMetadataJson().get("category"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) updated.getTaskMetadataJson().get("_runtime");
        @SuppressWarnings("unchecked")
        Map<String, Object> billing = (Map<String, Object>) runtime.get("billing");
        assertEquals("charged", billing.get("billingState"));
    }

    @Test
    void shouldMergeTaskAliasConfigMetadataWithoutDroppingContextSnapshot() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask existing = DiagnosisTask.builder()
                .id("diag-alias")
                .taskMetadataJson(Map.of(
                        "brandName", "探镜",
                        "category", "运动相机"
                ))
                .build();

        when(diagnosisTaskMapper.selectById("diag-alias")).thenReturn(existing);

        service.upsertTaskAliasConfigMetadata("diag-alias", Map.of(
                "brandAliases", List.of("探镜相机"),
                "competitorAliases", Map.of("Insta360", List.of("影石")),
                "aliasConfigVersion", "1"
        ));

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updated = taskCaptor.getValue();
        assertEquals("探镜", updated.getTaskMetadataJson().get("brandName"));
        assertEquals("运动相机", updated.getTaskMetadataJson().get("category"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) updated.getTaskMetadataJson().get("_runtime");
        @SuppressWarnings("unchecked")
        Map<String, Object> aliasConfig = (Map<String, Object>) runtime.get("aliasConfig");
        assertEquals(List.of("探镜相机"), aliasConfig.get("brandAliases"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> competitorAliases = (Map<String, List<String>>) aliasConfig.get("competitorAliases");
        assertEquals(List.of("影石"), competitorAliases.get("Insta360"));
    }

    @Test
    void shouldHideRuntimeSidecarsWhenReadingTaskContextSnapshot() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-context")
                .taskMetadataJson(Map.of(
                        "brandName", "探镜",
                        "category", "运动相机",
                        "_runtime", Map.of(
                                "billing", Map.of("billingState", "charged"),
                                "aliasConfig", Map.of("brandAliases", List.of("探镜相机"))
                        )
                ))
                .build();

        when(diagnosisTaskMapper.selectById("diag-context")).thenReturn(task);

        Map<String, Object> contextSnapshot = service.readTaskContextSnapshot("diag-context");

        assertEquals("探镜", contextSnapshot.get("brandName"));
        assertEquals("运动相机", contextSnapshot.get("category"));
        org.junit.jupiter.api.Assertions.assertFalse(contextSnapshot.containsKey("_runtime"));
    }

    @Test
    void shouldMergeCreatePayloadSidecarWithoutDroppingContextSnapshot() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask existing = DiagnosisTask.builder()
                .id("diag-create-payload")
                .taskMetadataJson(Map.of(
                        "brandName", "探镜",
                        "category", "运动相机"
                ))
                .build();

        when(diagnosisTaskMapper.selectById("diag-create-payload")).thenReturn(existing);

        service.upsertTaskCreateDiagnosisPayloadSnapshot("diag-create-payload", Map.of(
                "requestId", "frontdesk-123",
                "engineVersion", "4.2"
        ));

        ArgumentCaptor<DiagnosisTask> taskCaptor = ArgumentCaptor.forClass(DiagnosisTask.class);
        verify(diagnosisTaskMapper).updateById(taskCaptor.capture());
        DiagnosisTask updated = taskCaptor.getValue();
        assertEquals("探镜", updated.getTaskMetadataJson().get("brandName"));
        assertEquals("运动相机", updated.getTaskMetadataJson().get("category"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) updated.getTaskMetadataJson().get("_runtime");
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadSnapshot = (Map<String, Object>) runtime.get("createDiagnosisPayloadSnapshot");
        assertEquals("frontdesk-123", payloadSnapshot.get("requestId"));
        assertEquals("4.2", payloadSnapshot.get("engineVersion"));
    }

    @Test
    void shouldMirrorCanonicalProbeTruthWhenRunVersionTruthIsCanonical() {
        DiagnosisTaskRuntimePersistenceService service = newService();
        DiagnosisTask task = DiagnosisTask.builder()
                .id("diag-canonical")
                .taskOrderJson(Map.of(
                        "taskMetadata", Map.of(
                                "lightConeRunVersionTruth", LightConeV43VersionTruthSupport.canonicalRunVersionTruth()
                        )
                ))
                .taskMetadataJson(Map.of(
                        "_runtime", Map.of(
                                "lightConeRunVersionTruth", LightConeV43VersionTruthSupport.legacyLiteralRunVersionTruth("4.2")
                        )
                ))
                .build();

        ReflectionTestUtils.invokeMethod(
                service,
                "mirrorProbeRows",
                task,
                samplingFailureTask(),
                samplingFailureTask().probeTrace()
        );

        ArgumentCaptor<DiagnosisProbeRecord> probeCaptor = ArgumentCaptor.forClass(DiagnosisProbeRecord.class);
        verify(diagnosisProbeRecordMapper, times(2)).insert(probeCaptor.capture());
        for (DiagnosisProbeRecord probeRecord : probeCaptor.getAllValues()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> probeTruth = (Map<String, Object>) probeRecord.getRenderMetadataJson()
                    .get("lightConeProbeVersionTruth");
            assertEquals("light_cone_4_3", probeTruth.get("blueprintSchemaVersion"));
            assertEquals("canonicalExecutionLane", probeTruth.get("executionLane"));
            assertEquals("topic_projection_v4_3", probeTruth.get("projectionVersion"));
        }
    }

    private DiagnosisTaskRuntimePersistenceService newService() {
        DiagnosisTaskRuntimePersistenceService service = new DiagnosisTaskRuntimePersistenceService(
                mock(DiagnosisTenantContextService.class),
                llmCallLogMapper,
                mock(IntegrationOutboxRecordMapper.class),
                diagnosisTaskMapper,
                mock(DiagnosisTaskCompetitorMapper.class),
                mock(DiagnosisNodeRunMapper.class),
                diagnosisProbeRecordMapper,
                probeDispatchAttemptMapper,
                probeEvidenceRecordMapper,
                probeBrandMentionRecordMapper,
                modelInvocationRecordMapper,
                mock(DiagnosisEventRecordMapper.class),
                diagnosisProgressSnapshotMapper,
                mock(DiagnosisReportRecordMapper.class),
                mock(DiagnosisMapSupport.class),
                mock(V42TaskOrderExecutionPolicyResolver.class),
                knowledgeAssetIngestionServiceProvider,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "shortOutputThreshold", 200);
        return service;
    }

    private ModelInvocationRecord invocation(String nodeCode,
                                             Integer inputTokens,
                                             Integer outputTokens,
                                             Integer totalTokens,
                                             String totalCostYuan,
                                             Integer latencyMs) {
        return invocation(nodeCode, null, inputTokens, outputTokens, totalTokens, totalCostYuan, latencyMs);
    }

    private ModelInvocationRecord invocation(String nodeCode,
                                             String probeId,
                                             Integer inputTokens,
                                             Integer outputTokens,
                                             Integer totalTokens,
                                             String totalCostYuan,
                                             Integer latencyMs) {
        return ModelInvocationRecord.builder()
                .nodeCode(nodeCode)
                .probeId(probeId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .totalCostYuan(new BigDecimal(totalCostYuan))
                .latencyMs(latencyMs)
                .build();
    }

    private V42DiagnosisResult.Task samplingFailureTask() {
        V42DiagnosisResult.ProbeTrace successTrace = new V42DiagnosisResult.ProbeTrace(
                "probe-1",
                BucketType.ORTHOGONAL,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                null,
                null,
                null,
                ProbeExposurePolicy.BRAND_HIDDEN,
                "手机怎么选更稳？",
                "orthogonal_constrained_llm",
                ProbeDispatchStatus.SUCCESS,
                ProbeType.OPEN_DISCOVERY,
                true,
                "更推荐小米，体验更均衡。",
                "更推荐小米，体验更均衡。",
                "",
                List.of(),
                18,
                42,
                60,
                1200L,
                "Short",
                "L1_EXACT",
                null,
                false,
                false,
                false,
                List.of(),
                0,
                List.of(),
                "glm-4-7-251222",
                null,
                List.of(),
                1,
                1,
                Map.of(),
                BrandDetectionResult.detected(
                        "L1_EXACT",
                        "top1_recommendation",
                        "positive",
                        0.93d,
                        ProbeType.OPEN_DISCOVERY,
                        42,
                        "Short",
                        List.of(new CompetitorMention("华为", "L1_EXACT", "neutral", 0.72d)),
                        List.of(),
                        ""
                )
        );
        V42DiagnosisResult.ProbeTrace failedTrace = new V42DiagnosisResult.ProbeTrace(
                "probe-2",
                BucketType.ORTHOGONAL,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                null,
                null,
                null,
                ProbeExposurePolicy.BRAND_HIDDEN,
                "再确认一次",
                "orthogonal_constrained_llm",
                ProbeDispatchStatus.FAILED,
                ProbeType.OPEN_DISCOVERY,
                false,
                "",
                "",
                "",
                List.of(),
                12,
                0,
                12,
                900L,
                "Short",
                null,
                "timeout",
                false,
                false,
                false,
                List.of(),
                0,
                List.of(),
                "glm-4-7-251222",
                null,
                List.of(),
                1,
                1,
                Map.of(),
                null
        );
        return new V42DiagnosisResult.Task(
                "diag-failure",
                "report-failure",
                "light_cone_4_3",
                "FAILED",
                LocalDateTime.parse("2026-05-10T09:30:00"),
                LocalDateTime.parse("2026-05-10T09:31:00"),
                2,
                "mystery-provider",
                1,
                1,
                new RealtimeSearchSummary(false, "doubao", 0, 0, 0.0d, 0, 0, 0, true, List.of()),
                new Node3UsageCostSummary(
                        30L,
                        42L,
                        72L,
                        0,
                        new BigDecimal("0.010000"),
                        BigDecimal.ZERO.setScale(6),
                        new BigDecimal("0.010000")
                ),
                List.of(successTrace, failedTrace)
        );
    }

    private V42DiagnosisResult.Task competitorOnlyNoTargetBrandTask() {
        V42DiagnosisResult.ProbeTrace competitorTrace = new V42DiagnosisResult.ProbeTrace(
                "probe-1",
                BucketType.ORTHOGONAL,
                Map.of("D1", "comparison"),
                Map.of("D1", "对比"),
                null,
                null,
                null,
                ProbeExposurePolicy.BRAND_HIDDEN,
                "电视怎么选？",
                "orthogonal_constrained_llm",
                ProbeDispatchStatus.SUCCESS,
                ProbeType.OPEN_DISCOVERY,
                false,
                "三星更适合。",
                "三星更适合。",
                "",
                List.of(),
                10,
                34,
                44,
                800L,
                "Short",
                null,
                null,
                false,
                false,
                false,
                List.of(),
                0,
                List.of(),
                "mystery-model",
                null,
                List.of(),
                1,
                1,
                Map.of(),
                BrandDetectionResult.noMention(
                        ProbeType.OPEN_DISCOVERY,
                        34,
                        "Short",
                        List.of(new CompetitorMention("三星", "L1_EXACT", "positive", 0.91d)),
                        List.of(),
                        ""
                )
        );
        return new V42DiagnosisResult.Task(
                "diag-competitor-only",
                "report-competitor-only",
                "light_cone_4_3",
                "COMPLETED",
                LocalDateTime.parse("2026-05-10T10:00:00"),
                LocalDateTime.parse("2026-05-10T10:01:00"),
                1,
                "mystery-provider",
                0,
                1,
                new RealtimeSearchSummary(false, "doubao", 0, 0, 0.0d, 0, 0, 0, true, List.of()),
                new Node3UsageCostSummary(
                        10L,
                        34L,
                        44L,
                        0,
                        new BigDecimal("0.000132"),
                        BigDecimal.ZERO.setScale(6),
                        new BigDecimal("0.000132")
                ),
                List.of(competitorTrace)
        );
    }
}
