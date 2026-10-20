package com.geoprobe.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetCompetitorRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunEventRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetSourceRepository;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAssetIngestionServiceTest {

    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;

    @Mock
    private BrandSpaceTopicMapper brandSpaceTopicMapper;

    @Mock
    private BrandSpacePromptMapper brandSpacePromptMapper;

    @Mock
    private BrandScopeMapper brandScopeMapper;

    @Mock
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;

    @Mock
    private DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;

    @Mock
    private DiagnosisEventRecordMapper diagnosisEventRecordMapper;

    @Mock
    private ProbeDispatchAttemptMapper probeDispatchAttemptMapper;

    @Mock
    private ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;

    @Mock
    private ProbeEvidenceRecordMapper probeEvidenceRecordMapper;

    @Mock
    private KnowledgeAssetRunRepository knowledgeAssetRunRepository;

    @Mock
    private KnowledgeAssetRunEventRepository knowledgeAssetRunEventRepository;

    @Mock
    private KnowledgeAssetCompetitorRepository knowledgeAssetCompetitorRepository;

    @Mock
    private KnowledgeAssetEvidenceRepository knowledgeAssetEvidenceRepository;

    @Mock
    private KnowledgeAssetSourceRepository knowledgeAssetSourceRepository;

    @Mock
    private KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;

    @Mock
    private KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;

    @Mock
    private KnowledgeAssetScheduleStateMaterializationService knowledgeAssetScheduleStateMaterializationService;

    @Mock
    private KnowledgeAssetProjectionMaterializationService knowledgeAssetProjectionMaterializationService;

    @Test
    void shouldSkipWhenOnCompletionSyncDisabled() {
        KnowledgeAssetProperties properties = buildProperties(true, true, false);
        KnowledgeAssetIngestionService service = new KnowledgeAssetIngestionService(
                properties,
                brandSpaceTopicMapper,
                brandSpacePromptMapper,
                brandScopeMapper,
                diagnosisTaskMapper,
                diagnosisReportRecordMapper,
                diagnosisProbeRecordMapper,
                diagnosisEventRecordMapper,
                probeDispatchAttemptMapper,
                probeBrandMentionRecordMapper,
                probeEvidenceRecordMapper,
                knowledgeAssetRunRepository,
                knowledgeAssetRunEventRepository,
                knowledgeAssetCompetitorRepository,
                knowledgeAssetEvidenceRepository,
                knowledgeAssetSourceRepository,
                knowledgeAssetProblemRepository,
                knowledgeAssetMetricRepository,
                knowledgeAssetScheduleStateMaterializationService,
                knowledgeAssetProjectionMaterializationService,
                new ObjectMapper()
        );

        KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult result = service.ingestRunOnCompletion("task-1");

        assertTrue(result.skipped());
        assertEquals("knowledge_asset_on_completion_disabled", result.reason());
        verify(diagnosisTaskMapper, never()).selectById(any());
        verify(knowledgeAssetRunRepository, never()).upsertRun(any());
    }

    @Test
    void shouldUpsertRunAndCheckpointFromTaskAndReport() {
        KnowledgeAssetProperties properties = buildProperties(true, true, true);
        KnowledgeAssetIngestionService service = new KnowledgeAssetIngestionService(
                properties,
                brandSpaceTopicMapper,
                brandSpacePromptMapper,
                brandScopeMapper,
                diagnosisTaskMapper,
                diagnosisReportRecordMapper,
                diagnosisProbeRecordMapper,
                diagnosisEventRecordMapper,
                probeDispatchAttemptMapper,
                probeBrandMentionRecordMapper,
                probeEvidenceRecordMapper,
                knowledgeAssetRunRepository,
                knowledgeAssetRunEventRepository,
                knowledgeAssetCompetitorRepository,
                knowledgeAssetEvidenceRepository,
                knowledgeAssetSourceRepository,
                knowledgeAssetProblemRepository,
                knowledgeAssetMetricRepository,
                knowledgeAssetScheduleStateMaterializationService,
                knowledgeAssetProjectionMaterializationService,
                new ObjectMapper()
        );

        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 5, 10, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 5, 10, 30);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 5, 5, 10, 31);
        DiagnosisTask task = DiagnosisTask.builder()
                .id("task-1")
                .organizationId("org-1")
                .brandId("brand-1")
                .brandScopeId("scope-1")
                .engineVersion("light-cone-5.0")
                .statusCode(2)
                .targetProbeCount(12)
                .targetModelProduct("doubao-1.5-thinking")
                .brandNameSnapshot("美厨物语")
                .categorySnapshot("厨房调味")
                .officialWebsiteSnapshot("https://brand.example.com")
                .taskOrderJson(new LinkedHashMap<>(Map.of("competitors", 4)))
                .taskMetadataJson(new LinkedHashMap<>(Map.of("window", "recent-8")))
                .startedAt(startedAt)
                .completedAt(completedAt)
                .createdAt(startedAt.minusMinutes(5))
                .updatedAt(completedAt)
                .build();
        LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
        projection.put("competitorPool", 6);
        projection.put("narrative", null);
        projection.put("competitorRanking", java.util.List.of(
                new LinkedHashMap<>(Map.of(
                        "brand", "竞品A",
                        "visibilityScore", 0.71d,
                        "recommendRate", 0.44d,
                        "mentionRate", 0.38d,
                        "top3Rate", 0.25d,
                        "averagePosition", 2.4d,
                        "mentionCount", 5,
                        "rank", 1
                ))
        ));
        projection.put("clusterBreakdown", new LinkedHashMap<>(Map.of(
                "clusters", java.util.List.of(
                        new LinkedHashMap<>(Map.of(
                                "clusterId", "orthogonal",
                                "clusterName", "正交探测",
                                "sampleCount", 4,
                                "recommendRate", 0.28d,
                                "samplePrompts", java.util.List.of("怎么选购", "怎么比较")
                        ))
                )
        )));
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("visibilityScore", 0.61d);
        summary.put("sourceDiversity", null);
        BrandScope brandScope = BrandScope.builder()
                .id("scope-1")
                .primaryDomain("brand.example.com")
                .configGeneration(3)
                .defaultModelPolicyJson(new LinkedHashMap<>(Map.of(
                        "brandSpaceProfile", new LinkedHashMap<>(Map.of(
                                "competitors", java.util.List.of("竞品A", "竞品B")
                        ))
                )))
                .build();
        DiagnosisReportRecord report = DiagnosisReportRecord.builder()
                .id("report-1")
                .diagnosisTaskId("task-1")
                .targetModelProduct("doubao-1.5-thinking")
                .sampleSize(8)
                .reportProjectionJson(projection)
                .summaryJson(summary)
                .generatedAt(generatedAt)
                .createdAt(generatedAt)
                .build();

        when(diagnosisTaskMapper.selectById("task-1")).thenReturn(task);
        when(brandSpaceTopicMapper.selectList(any())).thenReturn(java.util.List.of(
                BrandSpaceTopic.builder()
                        .id("topic-1")
                        .brandScopeId("scope-1")
                        .name("产品选购")
                        .description("围绕选购决策的主题")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(any())).thenReturn(java.util.List.of(
                BrandSpacePrompt.builder()
                        .id("prompt-1")
                        .brandScopeId("scope-1")
                        .topicId("topic-1")
                        .promptText("美厨物语和竞品怎么选")
                        .seqNo(1)
                        .statusCode(1)
                        .build()
        ));
        when(brandScopeMapper.selectById("scope-1")).thenReturn(brandScope);
        when(diagnosisReportRecordMapper.selectOne(any())).thenReturn(report);
        when(diagnosisProbeRecordMapper.selectList(any())).thenReturn(java.util.List.of(
                DiagnosisProbeRecord.builder()
                        .id("probe-1")
                        .diagnosisTaskId("task-1")
                        .probeRef("p-001")
                        .seqNo(1)
                        .bucketTypeCode("orthogonal")
                        .surfaceIntent("seek_clear_answer")
                        .renderText("美厨物语和竞品怎么选")
                        .renderMetadataJson(new LinkedHashMap<>(Map.of("promptSequence", 1)))
                        .triggeredRulesJson(new LinkedHashMap<>(Map.of("promptSequence", 1)))
                        .statusCode(2)
                        .createdAt(generatedAt.minusMinutes(4))
                        .build()
        ));
        when(probeDispatchAttemptMapper.selectList(any())).thenReturn(java.util.List.of(
                ProbeDispatchAttempt.builder()
                        .id("attempt-1")
                        .diagnosisTaskId("task-1")
                        .probeId("probe-1")
                        .provider("doubao")
                        .modelId("doubao-1.5-thinking")
                        .rawResponseText("官网与媒体均提及美厨物语。")
                        .responseMetadataJson(new LinkedHashMap<>(Map.of("responsePreview", "官网与媒体均提及美厨物语。")))
                        .citationsJson(java.util.List.of("https://brand.example.com/article"))
                        .selectedFinal(true)
                        .statusCode(2)
                        .createdAt(generatedAt.minusMinutes(3))
                        .build()
        ));
        when(probeBrandMentionRecordMapper.selectList(any())).thenReturn(java.util.List.of(
                ProbeBrandMentionRecord.builder()
                        .id("mention-1")
                        .diagnosisTaskId("task-1")
                        .probeId("probe-1")
                        .dispatchAttemptId("attempt-1")
                        .brandName("美厨物语")
                        .brandRoleCode(0)
                        .mentionedFlag(true)
                        .recommendedFlag(true)
                        .sentimentCode(1)
                        .positionIndex(1)
                        .confidenceScore(BigDecimal.valueOf(0.8800d))
                        .mentionMetadataJson(new LinkedHashMap<>(Map.of("position", "top1_recommendation")))
                        .createdAt(generatedAt.minusMinutes(2))
                        .build()
        ));
        when(probeEvidenceRecordMapper.selectList(any())).thenReturn(java.util.List.of(
                ProbeEvidenceRecord.builder()
                        .id("evidence-1")
                        .diagnosisTaskId("task-1")
                        .probeId("probe-1")
                        .dispatchAttemptId("attempt-1")
                        .sourceTypeCode("official")
                        .domain("brand.example.com")
                        .url("https://brand.example.com/article")
                        .title("品牌官网文章")
                        .snippet("来自品牌官网")
                        .searchQuery("美厨物语 官网")
                        .retrievedAt(generatedAt.minusMinutes(2))
                        .createdAt(generatedAt.minusMinutes(2))
                        .build(),
                ProbeEvidenceRecord.builder()
                        .id("evidence-2")
                        .diagnosisTaskId("task-1")
                        .probeId("probe-2")
                        .dispatchAttemptId("attempt-2")
                        .sourceTypeCode("news")
                        .domain("news.example.com")
                        .url("https://news.example.com/story")
                        .title("行业新闻")
                        .snippet("来自媒体报道")
                        .searchQuery("美厨物语 新闻")
                        .retrievedAt(generatedAt.minusMinutes(1))
                        .createdAt(generatedAt.minusMinutes(1))
                        .build()
        ));
        when(knowledgeAssetCompetitorRepository.listActiveConfiguredCompetitors("scope-1")).thenReturn(java.util.List.of(
                new KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView("竞品a", "竞品A"),
                new KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView("竞品b", "竞品B")
        ));
        when(knowledgeAssetCompetitorRepository.listRecentRunsByScope(org.mockito.ArgumentMatchers.eq("scope-1"), any(), anyInt()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetCompetitorRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetCompetitorRepository.listRunsByScopeWithinWindow(org.mockito.ArgumentMatchers.eq("scope-1"), any(), any()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetCompetitorRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetCompetitorRepository.listSnapshotFactsByRunIds(any())).thenReturn(java.util.List.of(
                new KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView(
                        "ka-run-task-1",
                        "竞品a",
                        "竞品A",
                        1,
                        71.00d,
                        44.00d,
                        38.00d,
                        25.00d,
                        2.40d,
                        5
                )
        ));
        when(knowledgeAssetSourceRepository.listRecentRunsByScope(org.mockito.ArgumentMatchers.eq("scope-1"), any(), anyInt()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetSourceRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetSourceRepository.listRunsByScopeWithinWindow(org.mockito.ArgumentMatchers.eq("scope-1"), any(), any()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetSourceRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetSourceRepository.listCitationsByRunIds(any())).thenReturn(java.util.List.of(
                new KnowledgeAssetSourceRepository.CitationView(
                        "ka-run-task-1",
                        null,
                        null,
                        null,
                        "domain:brand-example-com",
                        "brand.example.com",
                        "https://brand.example.com/article",
                        "品牌官网文章",
                        null,
                        "OWNED",
                        "OWNED",
                        "doubao",
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                ),
                new KnowledgeAssetSourceRepository.CitationView(
                        "ka-run-task-1",
                        null,
                        null,
                        null,
                        "domain:news-example-com",
                        "news.example.com",
                        "https://news.example.com/story",
                        "行业新闻",
                        null,
                        "EDITORIAL",
                        "EXTERNAL",
                        "doubao",
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                )
        ));
        when(knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(any())).thenReturn(java.util.List.of(
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView(
                        "ka-run-task-1",
                        "domain:brand-example-com",
                        "brand.example.com",
                        "OWNED",
                        "OWNED",
                        1,
                        50.0d
                ),
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView(
                        "ka-run-task-1",
                        "domain:news-example-com",
                        "news.example.com",
                        "EDITORIAL",
                        "EXTERNAL",
                        1,
                        50.0d
                )
        ));
        when(knowledgeAssetProblemRepository.listRecentRunsByScope(org.mockito.ArgumentMatchers.eq("scope-1"), any(), anyInt()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetProblemRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetProblemRepository.listRunsByScopeWithinWindow(org.mockito.ArgumentMatchers.eq("scope-1"), any(), any()))
                .thenReturn(java.util.List.of(
                        new KnowledgeAssetProblemRepository.WindowRunHead("ka-run-task-1", 8, OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
                ));
        when(knowledgeAssetProblemRepository.listSnapshotFactsByRunIds(any())).thenReturn(java.util.List.of(
                new KnowledgeAssetProblemRepository.ProblemSnapshotFactView(
                        "ka-run-task-1",
                        "orthogonal",
                        null,
                        "orthogonal",
                        "REPORT_CLUSTER_BREAKDOWN",
                        2,
                        4,
                        0,
                        0,
                        28.00d
                )
        ));
        when(knowledgeAssetProblemRepository.listActiveTopics("scope-1")).thenReturn(java.util.List.of(
                new KnowledgeAssetProblemRepository.TopicCatalogView(
                        "ka-topic-scope-1-topic-1",
                        "topic-1",
                        "产品选购",
                        "围绕选购决策的主题",
                        "orthogonal",
                        true,
                        null
                )
        ));
        when(knowledgeAssetEvidenceRepository.listProbesByRunId("ka-run-task-1")).thenReturn(java.util.List.of(
                new KnowledgeAssetEvidenceRepository.ProbeView(
                        "ka-probe-probe-1",
                        "ka-run-task-1",
                        "probe-1",
                        "p-001",
                        1,
                        "ka-topic-scope-1-topic-1",
                        "ka-prompt-scope-1-prompt-1",
                        "orthogonal",
                        "美厨物语和竞品怎么选"
                )
        ));
        when(knowledgeAssetEvidenceRepository.listAttemptsByRunId("ka-run-task-1")).thenReturn(java.util.List.of(
                new KnowledgeAssetEvidenceRepository.AttemptView(
                        "ka-attempt-attempt-1",
                        "ka-run-task-1",
                        "ka-probe-probe-1",
                        "attempt-1",
                        "doubao",
                        "doubao-1.5-thinking",
                        2,
                        true,
                        "官网与媒体均提及美厨物语。",
                        "官网与媒体均提及美厨物语。",
                        "[]",
                        "{}",
                        generatedAt.minusMinutes(3)
                )
        ));
        when(knowledgeAssetEvidenceRepository.listBrandMentionsByRunId("ka-run-task-1")).thenReturn(java.util.List.of(
                new KnowledgeAssetEvidenceRepository.BrandMentionView(
                        "ka-mention-mention-1",
                        "ka-run-task-1",
                        "ka-probe-probe-1",
                        "ka-attempt-attempt-1",
                        "mention-1",
                        "美厨物语",
                        "美厨物语",
                        "target",
                        true,
                        true,
                        1,
                        1,
                        BigDecimal.valueOf(0.8800d),
                        "{}",
                        generatedAt.minusMinutes(2)
                )
        ));
        when(knowledgeAssetScheduleStateMaterializationService.materializeScope("scope-1"))
                .thenReturn(new KnowledgeAssetScheduleStateMaterializationService.ScheduleStateMaterializationResult(
                        "scope-1",
                        "ka-run-task-1",
                        false,
                        1,
                        null
                ));
        when(knowledgeAssetProjectionMaterializationService.materializeScope("scope-1"))
                .thenReturn(new KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult(
                        "scope-1",
                        "ka-run-task-1",
                        false,
                        java.util.List.of("brand-performance", "citation-sources", "problem-analysis"),
                        null
                ));

        KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult result = service.ingestRun(" task-1 ");

        assertTrue(result.synced());
        assertFalse(result.skipped());
        assertEquals("scope-1", result.brandScopeId());
        assertEquals("report-1", result.sourceReportId());

        ArgumentCaptor<KnowledgeAssetRunRepository.KnowledgeAssetRunUpsert> runCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetRunRepository.KnowledgeAssetRunUpsert.class);
        ArgumentCaptor<KnowledgeAssetRunRepository.KnowledgeAssetCheckpointUpsert> checkpointCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetRunRepository.KnowledgeAssetCheckpointUpsert.class);

        verify(knowledgeAssetRunRepository).upsertRun(runCaptor.capture());
        verify(knowledgeAssetRunRepository).upsertCheckpoint(checkpointCaptor.capture());
        ArgumentCaptor<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorUpsert> configuredCompetitorCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetCompetitorRepository.ConfiguredCompetitorUpsert.class);
        ArgumentCaptor<java.util.List<String>> configuredKeysCaptor = ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert>> snapshotFactsCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<String> windowKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert>> windowFactsCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetEvidenceRepository.ProbeUpsert>> probeEventCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetEvidenceRepository.AttemptUpsert>> attemptEventCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetEvidenceRepository.BrandMentionUpsert>> brandMentionEventCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<KnowledgeAssetSourceRepository.OwnedDomainUpsert> ownedDomainCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetSourceRepository.OwnedDomainUpsert.class);
        ArgumentCaptor<KnowledgeAssetSourceRepository.SourceAssetUpsert> sourceAssetCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetSourceRepository.SourceAssetUpsert.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetSourceRepository.CitationUpsert>> citationCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert>> sourceSnapshotCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<String> sourceWindowKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetSourceRepository.SourceWindowFactUpsert>> sourceWindowFactsCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<KnowledgeAssetProblemRepository.TopicCatalogUpsert> topicCatalogCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProblemRepository.TopicCatalogUpsert.class);
        ArgumentCaptor<KnowledgeAssetProblemRepository.PromptCatalogUpsert> promptCatalogCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProblemRepository.PromptCatalogUpsert.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetProblemRepository.ProblemSignalUpsert>> problemSignalCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert>> problemSnapshotCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<String> problemWindowKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetProblemRepository.ProblemWindowFactUpsert>> problemWindowFactsCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetMetricRepository.MetricRegistryUpsert>> metricRegistryCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<java.util.List<KnowledgeAssetMetricRepository.MetricFactUpsert>> metricFactCaptor =
                ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<String> metricWindowKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeAssetCompetitorRepository, org.mockito.Mockito.times(2))
                .upsertConfiguredCompetitor(configuredCompetitorCaptor.capture());
        verify(knowledgeAssetCompetitorRepository)
                .deactivateMissingConfiguredCompetitors(org.mockito.ArgumentMatchers.eq("scope-1"), configuredKeysCaptor.capture());
        verify(knowledgeAssetCompetitorRepository)
                .replaceSnapshotFacts(org.mockito.ArgumentMatchers.eq("ka-run-task-1"),
                        org.mockito.ArgumentMatchers.eq("scope-1"),
                        snapshotFactsCaptor.capture());
        verify(knowledgeAssetCompetitorRepository, org.mockito.Mockito.times(3))
                .replaceWindowFacts(org.mockito.ArgumentMatchers.eq("scope-1"),
                        windowKeyCaptor.capture(),
                        any(),
                        any(),
                        windowFactsCaptor.capture());
        verify(knowledgeAssetEvidenceRepository)
                .replaceProbes(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), probeEventCaptor.capture());
        verify(knowledgeAssetEvidenceRepository)
                .replaceAttempts(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), attemptEventCaptor.capture());
        verify(knowledgeAssetEvidenceRepository)
                .replaceBrandMentions(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), brandMentionEventCaptor.capture());
        verify(knowledgeAssetSourceRepository)
                .upsertOwnedDomain(ownedDomainCaptor.capture());
        verify(knowledgeAssetSourceRepository, org.mockito.Mockito.times(2))
                .upsertSourceAsset(sourceAssetCaptor.capture());
        verify(knowledgeAssetSourceRepository)
                .replaceCitations(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), citationCaptor.capture());
        verify(knowledgeAssetSourceRepository)
                .replaceSnapshotFacts(org.mockito.ArgumentMatchers.eq("ka-run-task-1"),
                        org.mockito.ArgumentMatchers.eq("scope-1"),
                        sourceSnapshotCaptor.capture());
        verify(knowledgeAssetSourceRepository, org.mockito.Mockito.times(3))
                .replaceWindowFacts(org.mockito.ArgumentMatchers.eq("scope-1"),
                        sourceWindowKeyCaptor.capture(),
                        any(),
                        any(),
                        sourceWindowFactsCaptor.capture());
        verify(knowledgeAssetProblemRepository)
                .upsertTopicCatalog(topicCatalogCaptor.capture());
        verify(knowledgeAssetProblemRepository)
                .upsertPromptCatalog(promptCatalogCaptor.capture());
        verify(knowledgeAssetProblemRepository)
                .replaceProblemSignals(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), problemSignalCaptor.capture());
        verify(knowledgeAssetProblemRepository)
                .replaceSnapshotFacts(org.mockito.ArgumentMatchers.eq("ka-run-task-1"),
                        org.mockito.ArgumentMatchers.eq("scope-1"),
                        problemSnapshotCaptor.capture());
        verify(knowledgeAssetProblemRepository, org.mockito.Mockito.times(3))
                .replaceWindowFacts(org.mockito.ArgumentMatchers.eq("scope-1"),
                        problemWindowKeyCaptor.capture(),
                        any(),
                        any(),
                        problemWindowFactsCaptor.capture());
        verify(knowledgeAssetMetricRepository).upsertMetricRegistry(metricRegistryCaptor.capture());
        verify(knowledgeAssetMetricRepository).replaceRunFacts(org.mockito.ArgumentMatchers.eq("ka-run-task-1"), metricFactCaptor.capture());
        verify(knowledgeAssetMetricRepository, org.mockito.Mockito.times(3))
                .refreshWindowFacts(org.mockito.ArgumentMatchers.eq("scope-1"),
                        metricWindowKeyCaptor.capture(),
                        any(),
                        any());
        verify(knowledgeAssetScheduleStateMaterializationService).materializeScope("scope-1");
        verify(knowledgeAssetProjectionMaterializationService).materializeScope("scope-1");

        KnowledgeAssetRunRepository.KnowledgeAssetRunUpsert runUpsert = runCaptor.getValue();
        assertEquals("ka-run-task-1", runUpsert.id());
        assertEquals("org-1", runUpsert.organizationId());
        assertEquals("scope-1", runUpsert.brandScopeId());
        assertEquals("report-1", runUpsert.sourceReportId());
        assertEquals(Integer.valueOf(8), runUpsert.sampleSize());
        assertEquals(OffsetDateTime.parse("2026-05-05T10:31:00+08:00"), runUpsert.reportGeneratedAt());
        assertTrue(runUpsert.reportProjectionJson().contains("\"competitorPool\":6"));
        assertTrue(runUpsert.reportProjectionJson().contains("\"narrative\":null"));
        assertTrue(runUpsert.summaryPayloadJson().contains("\"sourceDiversity\":null"));

        KnowledgeAssetRunRepository.KnowledgeAssetCheckpointUpsert checkpointUpsert = checkpointCaptor.getValue();
        assertEquals("ka-checkpoint-task-1", checkpointUpsert.id());
        assertEquals("scope-1", checkpointUpsert.brandScopeId());
        assertEquals("RUN_SYNCED", checkpointUpsert.ingestionStatus());
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"scheduleStateSkipped\":false"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"scheduleTopicStateCount\":1"));
        assertEquals(OffsetDateTime.parse("2026-05-05T10:31:00+08:00"), checkpointUpsert.sourceUpdatedAt());
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"reportPresent\":true"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"probeEventCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"attemptEventCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"brandMentionEventCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"configuredCompetitorCount\":2"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"snapshotCompetitorCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"ownedDomainCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"sourceAssetCount\":2"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"citationCount\":2"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"sourceSnapshotDomainCount\":2"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"topicCatalogCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"promptCatalogCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"problemSignalCount\":2"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"problemSnapshotFactCount\":1"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"metricRegistryCount\":21"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"metricFactCount\""));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"metricWindowFactCount\""));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"projectionMaterializationSkipped\":false"));
        assertTrue(checkpointUpsert.checkpointPayloadJson().contains("\"projectionMaterializedDomains\":[\"brand-performance\",\"citation-sources\",\"problem-analysis\"]"));

        assertEquals(java.util.List.of("竞品a", "竞品b"), configuredKeysCaptor.getValue());
        assertEquals(2, configuredCompetitorCaptor.getAllValues().size());
        assertEquals("竞品A", configuredCompetitorCaptor.getAllValues().getFirst().competitorName());
        assertEquals("brand.example.com", ownedDomainCaptor.getValue().domainName());
        assertEquals("产品选购", topicCatalogCaptor.getValue().topicName());
        assertEquals("美厨物语和竞品怎么选", promptCatalogCaptor.getValue().promptText());
        assertEquals(1, probeEventCaptor.getValue().size());
        assertEquals("ka-probe-probe-1", probeEventCaptor.getValue().getFirst().id());
        assertEquals("ka-topic-scope-1-topic-1", probeEventCaptor.getValue().getFirst().topicCatalogId());
        assertEquals("ka-prompt-scope-1-prompt-1", probeEventCaptor.getValue().getFirst().promptCatalogId());
        assertEquals(1, attemptEventCaptor.getValue().size());
        assertEquals("ka-attempt-attempt-1", attemptEventCaptor.getValue().getFirst().id());
        assertEquals(1, brandMentionEventCaptor.getValue().size());
        assertEquals("美厨物语", brandMentionEventCaptor.getValue().getFirst().brandName());

        assertEquals(1, snapshotFactsCaptor.getValue().size());
        KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert snapshotFact = snapshotFactsCaptor.getValue().getFirst();
        assertEquals("竞品a", snapshotFact.competitorKey());
        assertEquals(Integer.valueOf(1), snapshotFact.rankNo());
        assertEquals(Double.valueOf(71.00d), snapshotFact.visibilityScore());
        assertEquals(Double.valueOf(38.00d), snapshotFact.mentionRate());

        assertEquals(2, citationCaptor.getValue().size());
        assertEquals(2, sourceAssetCaptor.getAllValues().size());
        assertEquals("domain:brand-example-com", sourceAssetCaptor.getAllValues().getFirst().sourceKey());
        assertEquals("DOMAIN", sourceAssetCaptor.getAllValues().getFirst().sourceLevel());
        assertEquals("domain:brand-example-com", citationCaptor.getValue().getFirst().sourceKey());
        assertEquals("ka-probe-probe-1", citationCaptor.getValue().getFirst().probeId());
        assertEquals("ka-attempt-attempt-1", citationCaptor.getValue().getFirst().attemptId());
        assertEquals("OWNED", citationCaptor.getValue().getFirst().ownershipKey());
        assertEquals("EDITORIAL", citationCaptor.getValue().get(1).sourceType());
        assertEquals(2, sourceSnapshotCaptor.getValue().size());
        Map<String, KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert> sourceSnapshotsByDomain =
                sourceSnapshotCaptor.getValue().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert::domainName,
                                item -> item
                        ));
        assertEquals(Integer.valueOf(1), Integer.valueOf(sourceSnapshotsByDomain.get("brand.example.com").citationCount()));
        assertEquals("OWNED", sourceSnapshotsByDomain.get("brand.example.com").ownershipKey());
        assertEquals("EDITORIAL", sourceSnapshotsByDomain.get("news.example.com").sourceType());
        assertEquals(1, problemSnapshotCaptor.getValue().size());
        assertEquals(2, problemSignalCaptor.getValue().size());
        assertEquals("cluster_breakdown", problemSignalCaptor.getValue().getFirst().signalType());
        KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert problemSnapshotFact =
                problemSnapshotCaptor.getValue().getFirst();
        assertEquals("orthogonal", problemSnapshotFact.problemSpaceBucket());
        assertEquals("orthogonal", problemSnapshotFact.clusterKey());
        assertEquals("REPORT_CLUSTER_BREAKDOWN", problemSnapshotFact.factSource());
        assertEquals(2, problemSnapshotFact.promptCount());
        assertEquals(4, problemSnapshotFact.probeCount());
        assertEquals(Double.valueOf(28.00d), problemSnapshotFact.signalScore());

        assertEquals(java.util.List.of("last_3_runs", "last_7_days", "last_14_days"), windowKeyCaptor.getAllValues());
        assertEquals(2, windowFactsCaptor.getAllValues().getFirst().size());
        Map<String, KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert> firstWindowFactsByKey =
                windowFactsCaptor.getAllValues().getFirst().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert::competitorKey,
                                item -> item
                        ));
        KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert configuredWindowFact =
                firstWindowFactsByKey.get("竞品b");
        KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert observedWindowFact =
                firstWindowFactsByKey.get("竞品a");
        assertTrue(configuredWindowFact.configuredFlag());
        assertEquals("竞品B", configuredWindowFact.competitorName());
        assertEquals(0, configuredWindowFact.runCount());
        assertEquals("竞品a", observedWindowFact.competitorKey());
        assertTrue(observedWindowFact.configuredFlag());
        assertEquals(1, observedWindowFact.runCount());
        assertEquals(5, observedWindowFact.mentionProbeCount());
        assertEquals(4, observedWindowFact.recommendProbeCount());
        assertEquals(Double.valueOf(71.00d), observedWindowFact.avgVisibilityScore());

        assertEquals(java.util.List.of("last_3_runs", "last_7_days", "last_14_days"), sourceWindowKeyCaptor.getAllValues());
        assertEquals(2, sourceWindowFactsCaptor.getAllValues().getFirst().size());
        Map<String, KnowledgeAssetSourceRepository.SourceWindowFactUpsert> sourceWindowFactsByDomain =
                sourceWindowFactsCaptor.getAllValues().getFirst().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                KnowledgeAssetSourceRepository.SourceWindowFactUpsert::domainName,
                                item -> item
                        ));
        assertEquals("OWNED", sourceWindowFactsByDomain.get("brand.example.com").ownershipKey());
        assertEquals(1, sourceWindowFactsByDomain.get("brand.example.com").usedRunCount());
        assertEquals("EDITORIAL", sourceWindowFactsByDomain.get("news.example.com").sourceType());
        assertEquals(java.util.List.of("last_3_runs", "last_7_days", "last_14_days"), problemWindowKeyCaptor.getAllValues());
        assertEquals(1, problemWindowFactsCaptor.getAllValues().getFirst().size());
        KnowledgeAssetProblemRepository.ProblemWindowFactUpsert problemWindowFact =
                problemWindowFactsCaptor.getAllValues().getFirst().getFirst();
        assertEquals("orthogonal", problemWindowFact.problemSpaceBucket());
        assertEquals("REPORT_CLUSTER_BREAKDOWN", problemWindowFact.factSource());
        assertEquals(1, problemWindowFact.runCount());
        assertEquals(4, problemWindowFact.probeCount());

        assertEquals(21, metricRegistryCaptor.getValue().size());
        assertTrue(metricRegistryCaptor.getValue().stream()
                .anyMatch(item -> "visibility.score".equals(item.metricKey())
                        && "visibility".equals(item.metricGroup())
                        && "WEIGHTED_AVG".equals(item.aggregationMethod())));
        assertTrue(metricRegistryCaptor.getValue().stream()
                .anyMatch(item -> "model.attempt_count".equals(item.metricKey()) && "model".equals(item.metricGroup())));
        assertTrue(metricRegistryCaptor.getValue().stream()
                .anyMatch(item -> "sentiment.mention_count".equals(item.metricKey()) && "sentiment".equals(item.metricGroup())));
        assertTrue(metricFactCaptor.getValue().stream()
                .anyMatch(item -> "visibility.score".equals(item.metricKey()) && "competitor".equals(item.sourceObjectType())));
        assertTrue(metricFactCaptor.getValue().stream()
                .anyMatch(item -> "citation.count".equals(item.metricKey()) && "source".equals(item.sourceObjectType())));
        assertTrue(metricFactCaptor.getValue().stream()
                .anyMatch(item -> "model.attempt_count".equals(item.metricKey()) && "model".equals(item.sourceObjectType())));
        assertTrue(metricFactCaptor.getValue().stream()
                .anyMatch(item -> "sentiment.mention_count".equals(item.metricKey()) && "brand".equals(item.sourceObjectType())));
        Map<String, KnowledgeAssetMetricRepository.MetricFactUpsert> topicMetricFactsByKey =
                metricFactCaptor.getValue().stream()
                        .filter(item -> "topic".equals(item.sourceObjectType()))
                        .collect(java.util.stream.Collectors.toMap(
                                KnowledgeAssetMetricRepository.MetricFactUpsert::metricKey,
                                item -> item
                        ));
        assertEquals(7, topicMetricFactsByKey.size());
        assertEquals(Double.valueOf(1.0d), topicMetricFactsByKey.get("kpi.sample_size").metricValue());
        assertEquals(Double.valueOf(100.0d), topicMetricFactsByKey.get("visibility.mention_rate").metricValue());
        assertEquals(Double.valueOf(100.0d), topicMetricFactsByKey.get("visibility.recommend_rate").metricValue());
        assertEquals(Double.valueOf(100.0d), topicMetricFactsByKey.get("visibility.top3_rate").metricValue());
        assertEquals(Double.valueOf(100.0d), topicMetricFactsByKey.get("visibility.score").metricValue());
        assertEquals(Double.valueOf(1.0d), topicMetricFactsByKey.get("visibility.average_position").metricValue());
        assertEquals(Double.valueOf(1.0d), topicMetricFactsByKey.get("visibility.mention_count").metricValue());
        assertTrue(topicMetricFactsByKey.get("visibility.score").factPayloadJson().contains("\"weightValue\":1.0"));
        assertTrue(topicMetricFactsByKey.get("visibility.average_position").factPayloadJson().contains("\"weightValue\":1.0"));
        assertEquals(java.util.List.of("last_3_runs", "last_7_days", "last_14_days"), metricWindowKeyCaptor.getAllValues());
    }

    private KnowledgeAssetProperties buildProperties(boolean enabled,
                                                     boolean ingestionEnabled,
                                                     boolean onCompletionEnabled) {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        properties.setEnabled(enabled);
        properties.getIngestion().setEnabled(ingestionEnabled);
        properties.getIngestion().setOnRunCompletionEnabled(onCompletionEnabled);
        return properties;
    }
}
