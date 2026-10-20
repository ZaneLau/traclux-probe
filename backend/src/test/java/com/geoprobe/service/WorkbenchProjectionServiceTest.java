package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.dto.workbench.WorkbenchAnalysisContextView;
import com.geoprobe.dto.workbench.WorkbenchBrandPerformanceView;
import com.geoprobe.dto.workbench.WorkbenchCitationSourcesView;
import com.geoprobe.dto.workbench.WorkbenchDataAvailabilityView;
import com.geoprobe.dto.workbench.WorkbenchOverviewView;
import com.geoprobe.dto.workbench.WorkbenchProblemAnalysisView;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskCompetitorMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkbenchProjectionServiceTest {

    @Test
    void shouldNotPreferProjectionWhenStrictReadHasExplicitPlatformFilter() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchAnalysisContextView analysisContext = WorkbenchAnalysisContextView.builder()
                .windowKey("last_14_days")
                .platforms(List.of("豆包"))
                .build();

        boolean preferProjection = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreferredProjection",
                analysisContext
        );

        assertFalse(preferProjection);
    }

    @Test
    void shouldNotPreferProjectionWhenStrictReadHasCustomTimeWindow() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchAnalysisContextView analysisContext = WorkbenchAnalysisContextView.builder()
                .windowKey("last_7_days")
                .platforms(List.of())
                .build();

        boolean preferProjection = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreferredProjection",
                analysisContext
        );

        assertFalse(preferProjection);
    }

    @Test
    void shouldNotPreferProjectionWhenKnowledgeWindowIsEmpty() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchAnalysisContextView analysisContext = WorkbenchAnalysisContextView.builder()
                .windowKey("last_14_days")
                .platforms(List.of())
                .build();

        boolean preferProjection = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreferredProjection",
                analysisContext,
                true
        );

        assertFalse(preferProjection);
    }

    @Test
    void shouldReturnFilteredEmptyReasonForStrictFilteredWindow() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchAnalysisContextView analysisContext = WorkbenchAnalysisContextView.builder()
                .windowKey("last_14_days")
                .platforms(List.of("豆包"))
                .build();

        WorkbenchDataAvailabilityView availability = ReflectionTestUtils.invokeMethod(
                service,
                "buildKnowledgeWindowEmptyAvailability",
                null,
                analysisContext
        );

        assertEquals("empty", availability.getStatus());
        assertEquals("filtered_window_empty", availability.getReasonCode());
    }

    @Test
    void shouldNotBuildKnowledgePrioritiesFromZeroOnlyFacts() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchBrandPerformanceView brandPerformanceView = WorkbenchBrandPerformanceView.builder()
                .summary(WorkbenchBrandPerformanceView.SummaryMetricView.builder().build())
                .topicSummaries(List.of(WorkbenchBrandPerformanceView.TopicSummaryView.builder()
                        .topicId("topic-1")
                        .topicName("厨房收纳选购场景")
                        .signalCount(0)
                        .signalShare(0.0d)
                        .build()))
                .build();
        WorkbenchCitationSourcesView citationSourcesView = WorkbenchCitationSourcesView.builder()
                .summary(WorkbenchCitationSourcesView.SourceSummaryView.builder()
                        .totalCitations(0)
                        .ownedShare(0.0d)
                        .build())
                .build();

        @SuppressWarnings("unchecked")
        List<WorkbenchOverviewView.PriorityItemView> priorities = ReflectionTestUtils.invokeMethod(
                service,
                "buildKnowledgePriorityItems",
                brandPerformanceView,
                citationSourcesView
        );

        assertTrue(priorities.isEmpty());
    }

    @Test
    void shouldBuildKnowledgePrioritiesOnlyWhenFactsExist() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchBrandPerformanceView brandPerformanceView = WorkbenchBrandPerformanceView.builder()
                .topicSummaries(List.of(WorkbenchBrandPerformanceView.TopicSummaryView.builder()
                        .topicId("topic-1")
                        .topicName("厨房收纳选购场景")
                        .signalCount(4)
                        .signalShare(24.0d)
                        .build()))
                .build();
        WorkbenchCitationSourcesView citationSourcesView = WorkbenchCitationSourcesView.builder()
                .summary(WorkbenchCitationSourcesView.SourceSummaryView.builder()
                        .totalCitations(12)
                        .ownedShare(20.0d)
                        .build())
                .build();

        @SuppressWarnings("unchecked")
        List<WorkbenchOverviewView.PriorityItemView> priorities = ReflectionTestUtils.invokeMethod(
                service,
                "buildKnowledgePriorityItems",
                brandPerformanceView,
                citationSourcesView
        );

        assertEquals(2, priorities.size());
        assertEquals("knowledge_topic_topic-1", priorities.get(0).getId());
        assertEquals("knowledge_owned_share", priorities.get(1).getId());
    }

    @Test
    void shouldDowngradeProblemAnalysisAvailabilityToRawOnlyWhenPreferredReadDropsStructure() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        WorkbenchDataAvailabilityView availability = WorkbenchDataAvailabilityView.builder()
                .status("ready")
                .reasonCode("ready")
                .missingFields(List.of())
                .missingFieldDetails(Map.of())
                .build();
        WorkbenchProblemAnalysisView.SummaryView summary = WorkbenchProblemAnalysisView.SummaryView.builder()
                .rawAnswerCount(12)
                .linkedPromptCount(0)
                .linkedProbeCount(0)
                .linkedEvidenceCount(0)
                .build();

        WorkbenchDataAvailabilityView reconciled = ReflectionTestUtils.invokeMethod(
                service,
                "reconcileProblemAnalysisAvailabilityAfterPreferredRead",
                availability,
                summary,
                List.of(),
                List.of(),
                List.of(),
                List.of(WorkbenchProblemAnalysisView.ProbeRowView.builder()
                        .probeId("probe-1")
                        .rawAnswer("真实回答")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.AttemptRowView.builder()
                        .attemptId("attempt-1")
                        .build()),
                List.of()
        );

        assertEquals("raw_only", reconciled.getStatus());
        assertEquals("problem_analysis_raw_only", reconciled.getReasonCode());
        assertTrue(reconciled.getMissingFields().contains("prompts"));
        assertTrue(reconciled.getMissingFields().contains("problemClusters"));
    }

    @Test
    void shouldMatchPlatformAliasBetweenModelIdAndUnifiedPlatformLabel() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());

        boolean matchedByChinesePlatform = ReflectionTestUtils.invokeMethod(
                service,
                "matchesPlatformSelection",
                "doubao-seed-1-6-lite-251015",
                null,
                Set.of("豆包")
        );
        boolean matchedByRawModelId = ReflectionTestUtils.invokeMethod(
                service,
                "matchesPlatformSelection",
                "doubao-seed-1-6-lite-251015",
                null,
                Set.of("doubao-seed-1-6-lite-251015")
        );
        boolean mismatchedPlatform = ReflectionTestUtils.invokeMethod(
                service,
                "matchesPlatformSelection",
                "gpt-4o",
                null,
                Set.of("豆包")
        );

        assertTrue(matchedByChinesePlatform);
        assertTrue(matchedByRawModelId);
        assertFalse(mismatchedPlatform);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveActualCompetitorRankOrderInOverviewRows() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.CompetitorRowView> source = List.of(
                WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id("brand-self")
                        .brandName("目标品牌")
                        .rank(2)
                        .isTarget(true)
                        .visibilityScore(12.1d)
                        .build(),
                WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id("brand-peer")
                        .brandName("头部竞品")
                        .rank(1)
                        .isTarget(false)
                        .visibilityScore(14.0d)
                        .recommendRate(14.0d)
                        .build()
        );

        List<WorkbenchOverviewView.CompetitorSummaryView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "toOverviewCompetitorRows",
                source
        );

        assertEquals(2, rows.size());
        assertEquals("头部竞品", rows.get(0).getBrandName());
        assertEquals(1, rows.get(0).getRank());
        assertEquals("目标品牌", rows.get(1).getBrandName());
        assertEquals(2, rows.get(1).getRank());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseSharedComparableMetricForOverviewCompetitorRows() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.CompetitorRowView> source = List.of(
                WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id("brand-self")
                        .brandName("目标品牌")
                        .isTarget(true)
                        .visibilityScore(12.1d)
                        .recommendRate(8.6d)
                        .build(),
                WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id("brand-peer-a")
                        .brandName("竞品甲")
                        .isTarget(false)
                        .recommendRate(14.0d)
                        .build(),
                WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id("brand-peer-b")
                        .brandName("竞品乙")
                        .isTarget(false)
                        .recommendRate(10.0d)
                        .build()
        );

        List<WorkbenchOverviewView.CompetitorSummaryView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "toOverviewCompetitorRows",
                source
        );

        assertEquals(3, rows.size());
        assertEquals("竞品甲", rows.get(0).getBrandName());
        assertEquals(1, rows.get(0).getRank());
        assertEquals(14.0d, rows.get(0).getDisplayScore());
        assertEquals("recommendRate", rows.get(0).getRankingMetric());
        assertEquals("目标品牌", rows.get(2).getBrandName());
        assertEquals(3, rows.get(2).getRank());
        assertEquals(8.6d, rows.get(2).getDisplayScore());
        assertEquals("recommendRate", rows.get(2).getRankingMetric());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMergeMobileAndDesktopCitationUrlsIntoSingleDomainDetailRow() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<ProbeEvidenceRecord> evidence = List.of(
                ProbeEvidenceRecord.builder()
                        .probeId("probe-1")
                        .domain("post.m.smzdm.com")
                        .url("https://post.m.smzdm.com/p/aqko0wdx/")
                        .sourceTypeCode("url_citation")
                        .title("移动端文章")
                        .build(),
                ProbeEvidenceRecord.builder()
                        .probeId("probe-2")
                        .domain("post.smzdm.com")
                        .url("https://post.smzdm.com/p/aqko0wdx/")
                        .sourceTypeCode("url_citation")
                        .title("桌面端文章")
                        .build()
        );

        List<WorkbenchCitationSourcesView.DomainDetailRowView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "buildCitationDomainDetails",
                evidence,
                Set.of(),
                2,
                Map.of(),
                0,
                Map.of(),
                0
        );

        assertEquals(1, rows.size());
        assertEquals(1, rows.getFirst().getUrlCount());
        assertEquals(2, rows.getFirst().getUrls().getFirst().getUsed());
        assertEquals("https://post.smzdm.com/p/aqko0wdx/", rows.getFirst().getUrls().getFirst().getUrl());
    }

    @Test
    void shouldNotBackfillProblemAnalysisStructureAfterPlatformScope() throws Exception {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        Object frame = buildProblemAnalysisFrameRecord(
                List.of(WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId("topic-1")
                        .topicName("选购关注")
                        .problemSpaceBucket("capability-validation")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.PromptRowView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("选购关注")
                        .promptText("怎么选")
                        .problemSpaceBucket("capability-validation")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id("cluster-1")
                        .problemSpaceBucket("capability-validation")
                        .representativeQuestions(List.of("怎么选"))
                        .build()),
                List.of(WorkbenchProblemAnalysisView.ProbeRowView.builder()
                        .probeId("probe-1")
                        .rawAnswer("真实回答")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.EvidenceRowView.builder()
                        .attemptId("attempt-1")
                        .probeId("probe-1")
                        .title("证据")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.AttemptRowView.builder()
                        .attemptId("attempt-1")
                        .probeId("probe-1")
                        .model("doubao-seed-1-6-lite-251015")
                        .build())
        );

        Object scopedFrame = ReflectionTestUtils.invokeMethod(
                service,
                "applyProblemAnalysisPlatformScope",
                frame,
                Set.of("豆包")
        );

        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "topicRows").size());
        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "promptRows").size());
        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "problemClusters").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "probeRows").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "attemptRows").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "evidenceRows").size());
    }

    @Test
    void shouldNotBackfillProblemAnalysisStructureAfterTimeScope() throws Exception {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        Object frame = buildProblemAnalysisFrameRecord(
                List.of(WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId("topic-1")
                        .topicName("选购关注")
                        .problemSpaceBucket("capability-validation")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.PromptRowView.builder()
                        .promptId("prompt-1")
                        .topicId("topic-1")
                        .topicName("选购关注")
                        .promptText("怎么选")
                        .problemSpaceBucket("capability-validation")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id("cluster-1")
                        .problemSpaceBucket("capability-validation")
                        .representativeQuestions(List.of("怎么选"))
                        .build()),
                List.of(WorkbenchProblemAnalysisView.ProbeRowView.builder()
                        .probeId("probe-1")
                        .rawAnswer("真实回答")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.EvidenceRowView.builder()
                        .attemptId("attempt-1")
                        .probeId("probe-1")
                        .capturedAt("2026-05-15T10:00:00")
                        .title("证据")
                        .build()),
                List.of(WorkbenchProblemAnalysisView.AttemptRowView.builder()
                        .attemptId("attempt-1")
                        .probeId("probe-1")
                        .createdAt("2026-05-15T10:00:00")
                        .build())
        );

        Object scopedFrame = ReflectionTestUtils.invokeMethod(
                service,
                "applyProblemAnalysisTimeScope",
                frame,
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 15)
        );

        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "topicRows").size());
        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "promptRows").size());
        assertEquals(0, problemAnalysisFrameRows(scopedFrame, "problemClusters").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "probeRows").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "attemptRows").size());
        assertEquals(1, problemAnalysisFrameRows(scopedFrame, "evidenceRows").size());
    }

    @Test
    void shouldBuildKnowledgeWindowTopicSummariesFromMetricWindowFacts() {
        KnowledgeAssetProblemRepository problemRepository = mock(KnowledgeAssetProblemRepository.class);
        KnowledgeAssetMetricRepository metricRepository = mock(KnowledgeAssetMetricRepository.class);
        when(problemRepository.listActiveTopics("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.TopicCatalogView("topic-cat-1", "topic-1", "产品选购", "围绕选购", "orthogonal", true, null)
        ));
        when(problemRepository.listActivePrompts("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.PromptCatalogView("prompt-cat-1", "topic-cat-1", "prompt-1", "怎么买", 2, true)
        ));
        when(problemRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.ProblemWindowFactView("orthogonal", "topic-cat-1", "cluster-a", "REPORT_CLUSTER_BREAKDOWN", 1, 1, 3, 3, 6, 21.5d)
        ));
        when(metricRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                windowMetric("topic-cat-1", "kpi.sample_size", 12d),
                windowMetric("topic-cat-1", "visibility.score", 36.5d),
                windowMetric("topic-cat-1", "visibility.mention_rate", 48d),
                windowMetric("topic-cat-1", "visibility.recommend_rate", 31d),
                windowMetric("topic-cat-1", "visibility.top3_rate", 22d),
                windowMetric("topic-cat-1", "visibility.average_position", 1.8d),
                windowMetric("topic-cat-1", "visibility.mention_count", 5d)
        ));
        WorkbenchProjectionService service = buildService(
                enabledKnowledgeProperties(),
                providerOf(null),
                providerOf(metricRepository),
                providerOf(problemRepository)
        );

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.TopicSummaryView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "buildKnowledgeWindowTopicSummaries",
                "scope-1"
        );

        assertEquals(1, rows.size());
        assertEquals("topic-cat-1", rows.getFirst().getTopicId());
        assertEquals(Integer.valueOf(12), rows.getFirst().getSampleSize());
        assertEquals(Double.valueOf(36.5d), rows.getFirst().getVisibilityScore());
        assertEquals(Double.valueOf(48d), rows.getFirst().getMentionRate());
        assertEquals(Integer.valueOf(6), rows.getFirst().getSignalCount());
    }

    @Test
    void shouldAggregateKnowledgeScopedTopicSummariesBySampleWeight() {
        KnowledgeAssetProblemRepository problemRepository = mock(KnowledgeAssetProblemRepository.class);
        KnowledgeAssetMetricRepository metricRepository = mock(KnowledgeAssetMetricRepository.class);
        when(problemRepository.listActiveTopics("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.TopicCatalogView("topic-cat-1", "topic-1", "产品选购", "围绕选购", "orthogonal", true, null)
        ));
        when(problemRepository.listActivePrompts("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.PromptCatalogView("prompt-cat-1", "topic-cat-1", "prompt-1", "怎么买", 1, true)
        ));
        when(problemRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-1", "ka-run-task-2"))).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.ProblemSnapshotFactView("ka-run-task-1", "orthogonal", "topic-cat-1", "cluster-a", "REPORT_CLUSTER_BREAKDOWN", 1, 2, 2, 3, 12.0d),
                new KnowledgeAssetProblemRepository.ProblemSnapshotFactView("ka-run-task-2", "orthogonal", "topic-cat-1", "cluster-a", "REPORT_CLUSTER_BREAKDOWN", 1, 3, 3, 4, 18.0d)
        ));
        when(metricRepository.listRunFactsByRunIds(List.of("ka-run-task-1", "ka-run-task-2"))).thenReturn(List.of(
                runMetric("ka-run-task-1", "topic-cat-1", "kpi.sample_size", 10d, null),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.score", 18.5d, 10d),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.mention_rate", 20d, 10d),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.recommend_rate", 10d, 10d),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.top3_rate", 30d, 10d),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.average_position", 2.0d, 2d),
                runMetric("ka-run-task-1", "topic-cat-1", "visibility.mention_count", 2d, null),
                runMetric("ka-run-task-2", "topic-cat-1", "kpi.sample_size", 30d, null),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.score", 38.5d, 30d),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.mention_rate", 50d, 30d),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.recommend_rate", 40d, 30d),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.top3_rate", 20d, 30d),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.average_position", 1.5d, 9d),
                runMetric("ka-run-task-2", "topic-cat-1", "visibility.mention_count", 9d, null)
        ));
        WorkbenchProjectionService service = buildService(
                enabledKnowledgeProperties(),
                providerOf(null),
                providerOf(metricRepository),
                providerOf(problemRepository)
        );
        List<DiagnosisReportRecord> recentReports = List.of(
                DiagnosisReportRecord.builder().diagnosisTaskId("ka-run-task-1").generatedAt(LocalDateTime.now().minusDays(1)).build(),
                DiagnosisReportRecord.builder().diagnosisTaskId("ka-run-task-2").generatedAt(LocalDateTime.now()).build()
        );

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.TopicSummaryView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "buildKnowledgeScopedTopicSummaries",
                "scope-1",
                recentReports
        );

        assertEquals(1, rows.size());
        assertEquals(Integer.valueOf(40), rows.getFirst().getSampleSize());
        assertEquals(Double.valueOf(42.5d), rows.getFirst().getMentionRate());
        assertEquals(Double.valueOf(32.5d), rows.getFirst().getRecommendRate());
        assertEquals(Double.valueOf(22.5d), rows.getFirst().getTop3Rate());
        assertEquals(Double.valueOf(33.5d), rows.getFirst().getVisibilityScore());
        assertEquals(Double.valueOf(1.59d), rows.getFirst().getAveragePosition());
        assertEquals(Integer.valueOf(11), rows.getFirst().getMentionCount());
        assertEquals(Integer.valueOf(7), rows.getFirst().getSignalCount());
    }

    @Test
    void shouldGroupModelPerformanceRowsByPlatformLabel() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<DiagnosisReportRecord> reports = List.of(
                modelReport("r-3", "run-3", "doubao:doubao-seed-2-0-pro-260215", LocalDateTime.of(2026, 5, 11, 12, 0), null, null, null),
                modelReport("r-2", "run-2", "doubao:doubao-seed-1-6-lite-251015", LocalDateTime.of(2026, 5, 10, 12, 0), null, null, null),
                modelReport("r-1", "run-1", "doubao-seed-1-6-lite-251015", LocalDateTime.of(2026, 5, 9, 12, 0), 0.10d, 0.20d, 0.30d)
        );

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "buildModelPerformanceRows",
                null,
                reports
        );

        assertEquals(1, rows.size());
        assertEquals("豆包", rows.getFirst().getId());
        assertEquals("豆包", rows.getFirst().getLabel());
        assertEquals(Integer.valueOf(3), rows.getFirst().getReportCount());
        assertEquals("run-1", rows.getFirst().getLatestRunId());
        assertEquals(Double.valueOf(19.0d), rows.getFirst().getVisibilityScore());
        assertEquals(Double.valueOf(20.0d), rows.getFirst().getRecommendRate());
        assertEquals(Double.valueOf(10.0d), rows.getFirst().getMentionRate());
        assertEquals(Double.valueOf(30.0d), rows.getFirst().getTop3Rate());
    }

    @Test
    void shouldAggregateWindowModelPerformanceRowsByPlatformLabel() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<DiagnosisReportRecord> reports = List.of(
                modelReport("r-3", "run-3", "doubao:doubao-seed-2-0-pro-260215", LocalDateTime.of(2026, 5, 11, 12, 0), null, null, null),
                modelReport("r-2", "run-2", "doubao:doubao-seed-1-6-lite-251015", LocalDateTime.of(2026, 5, 10, 12, 0), 0.20d, 0.40d, 0.60d),
                modelReport("r-1", "run-1", "doubao-seed-1-6-lite-251015", LocalDateTime.of(2026, 5, 9, 12, 0), 0.10d, 0.20d, 0.30d)
        );

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> rows = ReflectionTestUtils.invokeMethod(
                service,
                "buildWindowModelPerformanceRows",
                null,
                reports
        );

        assertEquals(1, rows.size());
        assertEquals("豆包", rows.getFirst().getId());
        assertEquals(Integer.valueOf(3), rows.getFirst().getReportCount());
        assertEquals("run-2", rows.getFirst().getLatestRunId());
        assertEquals(Double.valueOf(28.5d), rows.getFirst().getVisibilityScore());
        assertEquals(Double.valueOf(30.0d), rows.getFirst().getRecommendRate());
        assertEquals(Double.valueOf(15.0d), rows.getFirst().getMentionRate());
        assertEquals(Double.valueOf(45.0d), rows.getFirst().getTop3Rate());
    }

    @Test
    void shouldBackfillSingleModelRowFromOverviewCurrentPerformance() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = List.of(
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("豆包")
                        .label("豆包")
                        .reportCount(225)
                        .latestRunId("run-225")
                        .latestReportId("report-225")
                        .build()
        );
        WorkbenchOverviewView.CurrentPerformanceView currentPerformance = WorkbenchOverviewView.CurrentPerformanceView.builder()
                .visibilityScore(42.5d)
                .recommendRate(51.0d)
                .mentionRate(63.0d)
                .top3Rate(28.0d)
                .build();

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalized = ReflectionTestUtils.invokeMethod(
                service,
                "normalizeModelPerformanceRows",
                modelRows,
                currentPerformance
        );

        assertEquals(1, normalized.size());
        assertEquals(Double.valueOf(42.5d), normalized.getFirst().getVisibilityScore());
        assertEquals(Double.valueOf(51.0d), normalized.getFirst().getRecommendRate());
        assertEquals(Double.valueOf(63.0d), normalized.getFirst().getMentionRate());
        assertEquals(Double.valueOf(28.0d), normalized.getFirst().getTop3Rate());
        assertEquals(Integer.valueOf(225), normalized.getFirst().getReportCount());
    }

    @Test
    void shouldDropEmptyLegacyModelRowsWhenMultipleRowsPresent() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = List.of(
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("DeepSeek")
                        .label("DeepSeek")
                        .reportCount(200)
                        .build(),
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("豆包")
                        .label("豆包")
                        .reportCount(300)
                        .build(),
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("glm")
                        .label("glm")
                        .reportCount(200)
                        .build()
        );
        WorkbenchBrandPerformanceView.SummaryMetricView summary = WorkbenchBrandPerformanceView.SummaryMetricView.builder()
                .visibilityScore(32.5d)
                .recommendRate(41.0d)
                .mentionRate(28.0d)
                .top3Rate(19.0d)
                .build();

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalized = ReflectionTestUtils.invokeMethod(
                service,
                "normalizeModelPerformanceRows",
                modelRows,
                summary
        );

        assertTrue(normalized.isEmpty());
    }

    @Test
    void shouldKeepOnlyUsableModelRowsWhenLegacyEmptyRowsExist() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = List.of(
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("DeepSeek")
                        .label("DeepSeek")
                        .reportCount(200)
                        .build(),
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("豆包")
                        .label("豆包")
                        .reportCount(300)
                        .visibilityScore(35.0d)
                        .mentionRate(30.0d)
                        .recommendRate(40.0d)
                        .top3Rate(20.0d)
                        .build()
        );

        @SuppressWarnings("unchecked")
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalized = ReflectionTestUtils.invokeMethod(
                service,
                "normalizeModelPerformanceRows",
                modelRows,
                WorkbenchBrandPerformanceView.SummaryMetricView.builder()
                        .visibilityScore(35.0d)
                        .recommendRate(40.0d)
                        .mentionRate(30.0d)
                        .top3Rate(20.0d)
                        .build()
        );

        assertEquals(1, normalized.size());
        assertEquals("豆包", normalized.getFirst().getId());
        assertEquals(Double.valueOf(35.0d), normalized.getFirst().getVisibilityScore());
    }

    @Test
    void shouldTreatAllNullModelRowsAsMissingModelPerformance() {
        WorkbenchProjectionService service = buildService(enabledKnowledgeProperties());
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = List.of(
                WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                        .id("豆包")
                        .label("豆包")
                        .reportCount(225)
                        .build()
        );

        @SuppressWarnings("unchecked")
        List<String> missing = ReflectionTestUtils.invokeMethod(
                service,
                "collectBrandPerformanceMissingFields",
                WorkbenchBrandPerformanceView.SummaryMetricView.builder()
                        .shareOfVoice(30.0d)
                        .averagePosition(1.8d)
                        .build(),
                List.of(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .brandName("美厨物语")
                        .isTarget(true)
                        .averagePosition(1.8d)
                        .build()),
                modelRows,
                List.of(
                        WorkbenchBrandPerformanceView.TrendPointView.builder().visibilityScore(30.0d).build(),
                        WorkbenchBrandPerformanceView.TrendPointView.builder().visibilityScore(31.0d).build()
                ),
                List.of(WorkbenchBrandPerformanceView.TopicSummaryView.builder().topicId("topic-1").build())
        );

        assertTrue(missing.contains("modelPerformance"));
    }

    private WorkbenchProjectionService buildService(KnowledgeAssetProperties properties) {
        return buildService(properties, providerOf(null), providerOf(null), providerOf(null));
    }

    private WorkbenchProjectionService buildService(KnowledgeAssetProperties properties,
                                                    ObjectProvider<?> runProvider,
                                                    ObjectProvider<?> metricProvider,
                                                    ObjectProvider<?> problemProvider) {
        ObjectProvider<?> emptyProvider = providerOf(null);
        return new WorkbenchProjectionService(
                mock(ServiceAccessService.class),
                mock(BrandScopeMapper.class),
                mock(BrandMapper.class),
                mock(BrandSpaceTopicMapper.class),
                mock(BrandSpacePromptMapper.class),
                mock(DiagnosisTaskMapper.class),
                mock(DiagnosisTaskCompetitorMapper.class),
                mock(DiagnosisReportRecordMapper.class),
                mock(DiagnosisProbeRecordMapper.class),
                mock(ProbeBrandMentionRecordMapper.class),
                mock(ProbeDispatchAttemptMapper.class),
                mock(ProbeEvidenceRecordMapper.class),
                mock(DataSource.class),
                new ObjectMapper(),
                properties,
                castProvider(emptyProvider),
                castProvider(runProvider),
                castProvider(emptyProvider),
                castProvider(metricProvider),
                castProvider(problemProvider),
                castProvider(emptyProvider)
        );
    }

    private KnowledgeAssetMetricRepository.MetricWindowFactView windowMetric(String sourceObjectKey,
                                                                             String metricKey,
                                                                             Double numericValue) {
        return new KnowledgeAssetMetricRepository.MetricWindowFactView(
                "topic",
                sourceObjectKey,
                metricKey,
                sourceObjectKey + ":" + metricKey,
                1,
                numericValue,
                numericValue,
                numericValue,
                null,
                null,
                "{}",
                "{}",
                "{}"
        );
    }

    private KnowledgeAssetMetricRepository.MetricFactView runMetric(String runId,
                                                                    String sourceObjectKey,
                                                                    String metricKey,
                                                                    Double metricValue,
                                                                    Double weightValue) {
        return new KnowledgeAssetMetricRepository.MetricFactView(
                runId,
                "topic",
                sourceObjectKey,
                metricKey,
                sourceObjectKey + ":" + metricKey,
                metricValue,
                null,
                null,
                "{}",
                weightValue == null ? "{}" : "{\"weightValue\":" + weightValue + "}"
        );
    }

    private Object buildProblemAnalysisFrameRecord(List<WorkbenchProblemAnalysisView.TopicRowView> topicRows,
                                                   List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                                   List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters,
                                                   List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
                                                   List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows,
                                                   List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows) throws Exception {
        Class<?> frameClass = findProblemAnalysisFrameClass();
        Constructor<?> constructor = frameClass.getDeclaredConstructor(
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                topicRows,
                promptRows,
                problemClusters,
                probeRows,
                evidenceRows,
                attemptRows,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private List<?> problemAnalysisFrameRows(Object frame, String accessorName) throws Exception {
        Method method = frame.getClass().getDeclaredMethod(accessorName);
        method.setAccessible(true);
        return (List<?>) method.invoke(frame);
    }

    private Class<?> findProblemAnalysisFrameClass() {
        for (Class<?> nested : WorkbenchProjectionService.class.getDeclaredClasses()) {
            if ("ProblemAnalysisFrame".equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new IllegalStateException("ProblemAnalysisFrame class not found");
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> castProvider(ObjectProvider<?> provider) {
        return (ObjectProvider<T>) provider;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private KnowledgeAssetProperties enabledKnowledgeProperties() {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        properties.setEnabled(true);
        properties.getProjection().setStrictWorkbenchReadEnabled(true);
        return properties;
    }

    private DiagnosisReportRecord modelReport(String reportId,
                                              String runId,
                                              String model,
                                              LocalDateTime generatedAt,
                                              Double mentionRateRatio,
                                              Double recommendRateRatio,
                                              Double top3RateRatio) {
        Map<String, Object> summary = null;
        if (recommendRateRatio != null || top3RateRatio != null) {
            summary = new java.util.LinkedHashMap<>();
            if (recommendRateRatio != null) {
                summary.put("recommendRate", recommendRateRatio);
            }
            if (top3RateRatio != null) {
                summary.put("top3Rate", top3RateRatio);
            }
        }
        return DiagnosisReportRecord.builder()
                .id(reportId)
                .diagnosisTaskId(runId)
                .targetModelProduct(model)
                .generatedAt(generatedAt)
                .citationRate(mentionRateRatio == null ? null : BigDecimal.valueOf(mentionRateRatio))
                .summaryJson(summary)
                .build();
    }
}
