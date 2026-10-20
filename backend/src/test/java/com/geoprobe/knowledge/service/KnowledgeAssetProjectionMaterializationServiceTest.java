package com.geoprobe.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.knowledge.repository.KnowledgeAssetCompetitorRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProjectionRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAssetProjectionMaterializationServiceTest {

    @Mock
    private KnowledgeAssetRunRepository knowledgeAssetRunRepository;

    @Mock
    private KnowledgeAssetCompetitorRepository knowledgeAssetCompetitorRepository;

    @Mock
    private KnowledgeAssetSourceRepository knowledgeAssetSourceRepository;

    @Mock
    private KnowledgeAssetEvidenceRepository knowledgeAssetEvidenceRepository;

    @Mock
    private KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;

    @Mock
    private KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;

    @Mock
    private KnowledgeAssetProjectionRepository knowledgeAssetProjectionRepository;

    @Test
    @SuppressWarnings("unchecked")
    void shouldMergeMobileAndDesktopCitationUrlsDuringMaterialization() {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        KnowledgeAssetProjectionMaterializationService service = new KnowledgeAssetProjectionMaterializationService(
                properties,
                knowledgeAssetRunRepository,
                knowledgeAssetCompetitorRepository,
                knowledgeAssetSourceRepository,
                knowledgeAssetEvidenceRepository,
                knowledgeAssetProblemRepository,
                knowledgeAssetMetricRepository,
                knowledgeAssetProjectionRepository,
                new ObjectMapper()
        );

        List<LinkedHashMap<String, Object>> rows = ReflectionTestUtils.invokeMethod(
                service,
                "aggregateUrlRows",
                List.of(
                        new KnowledgeAssetSourceRepository.CitationView(
                                "run-1", "probe-1", "attempt-1", null,
                                "domain:smzdm", "什么值得买",
                                "https://post.m.smzdm.com/p/aqko0wdx/",
                                "移动端文章", null, "EDITORIAL", "EXTERNAL", "doubao",
                                OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                        ),
                        new KnowledgeAssetSourceRepository.CitationView(
                                "run-1", "probe-2", "attempt-2", null,
                                "domain:smzdm", "什么值得买",
                                "https://post.smzdm.com/p/aqko0wdx/",
                                "桌面端文章", null, "EDITORIAL", "EXTERNAL", "doubao",
                                OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                        )
                )
        );

        assertEquals(1, rows.size());
        assertEquals("https://post.smzdm.com/p/aqko0wdx/", rows.getFirst().get("url"));
        assertEquals(2, rows.getFirst().get("usedCount"));
    }

    @Test
    void shouldMaterializeThreeProjectionDomainsFromKnowledgeFacts() throws Exception {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        properties.setEnabled(true);
        properties.getProjection().setMaterializationEnabled(true);
        properties.getProjection().setBrandPerformanceEnabled(true);
        properties.getProjection().setCitationSourcesEnabled(true);
        properties.getProjection().setProblemAnalysisEnabled(true);
        properties.getProjection().setMetricSlicesEnabled(true);

        KnowledgeAssetProjectionMaterializationService service = new KnowledgeAssetProjectionMaterializationService(
                properties,
                knowledgeAssetRunRepository,
                knowledgeAssetCompetitorRepository,
                knowledgeAssetSourceRepository,
                knowledgeAssetEvidenceRepository,
                knowledgeAssetProblemRepository,
                knowledgeAssetMetricRepository,
                knowledgeAssetProjectionRepository,
                new ObjectMapper()
        );

        when(knowledgeAssetRunRepository.findLatestRunByScope("scope-1"))
                .thenReturn(new KnowledgeAssetRunRepository.LatestRunView(
                        "ka-run-task-1",
                        "task-1",
                        "report-1",
                        8,
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                ));
        when(knowledgeAssetCompetitorRepository.listActiveConfiguredCompetitors("scope-1")).thenReturn(List.of(
                new KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView("target", "美厨物语"),
                new KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView("competitor-a", "竞品A")
        ));
        when(knowledgeAssetCompetitorRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetCompetitorRepository.CompetitorWindowFactView(
                        "competitor-a",
                        "竞品A",
                        true,
                        3,
                        12,
                        8,
                        68.5d,
                        41.2d,
                        52.1d,
                        29.4d,
                        2.7d
                )
        ));
        when(knowledgeAssetCompetitorRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView(
                        "ka-run-task-1",
                        "competitor-a",
                        "竞品A",
                        1,
                        70d,
                        44d,
                        55d,
                        33d,
                        2.5d,
                        6
                )
        ));
        when(knowledgeAssetRunRepository.findRunDetailByRunId("ka-run-task-1"))
                .thenReturn(new KnowledgeAssetRunRepository.RunDetailView(
                        "ka-run-task-1",
                        "org-1",
                        "scope-1",
                        "task-1",
                        "report-1",
                        "doubao-pro",
                        8,
                        "{\"brandNameSnapshot\":\"美厨物语\"}",
                        "{}",
                        "{}",
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                ));
        when(knowledgeAssetSourceRepository.listActiveOwnedDomains("scope-1")).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.OwnedDomainView("brand.example.com", "OFFICIAL_DOMAIN")
        ));
        when(knowledgeAssetSourceRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.SourceWindowFactView("brand.example.com", "domain:brand-example-com", "OWNED", "OWNED", 7, 3, 58.3d),
                new KnowledgeAssetSourceRepository.SourceWindowFactView("media.example.com", "domain:media-example-com", "EDITORIAL", "EXTERNAL", 5, 3, 41.7d)
        ));
        when(knowledgeAssetSourceRepository.listRecentRunsByScope(
                "scope-1",
                OffsetDateTime.parse("2026-05-05T10:31:00+08:00"),
                2
        )).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.WindowRunHead(
                        "ka-run-task-1",
                        8,
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                ),
                new KnowledgeAssetSourceRepository.WindowRunHead(
                        "ka-run-task-0",
                        6,
                        OffsetDateTime.parse("2026-04-29T10:31:00+08:00")
                )
        ));
        when(knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-1", "domain:brand-example-com", "brand.example.com", "OWNED", "OWNED", 3, 60d)
        ));
        when(knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-0"))).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-0", "domain:media-example-com", "media.example.com", "EDITORIAL", "EXTERNAL", 2, 66.7d),
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-0", "domain:brand-example-com", "brand.example.com", "OWNED", "OWNED", 1, 33.3d)
        ));
        when(knowledgeAssetSourceRepository.listRunsByScopeWithinWindow(
                "scope-1",
                OffsetDateTime.parse("2026-04-21T10:31:00+08:00"),
                OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
        )).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.WindowRunHead(
                        "ka-run-task-0",
                        6,
                        OffsetDateTime.parse("2026-04-29T10:31:00+08:00")
                ),
                new KnowledgeAssetSourceRepository.WindowRunHead(
                        "ka-run-task-1",
                        8,
                        OffsetDateTime.parse("2026-05-05T10:31:00+08:00")
                )
        ));
        when(knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-0", "ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-0", "domain:media-example-com", "media.example.com", "EDITORIAL", "EXTERNAL", 2, 66.7d),
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-0", "domain:brand-example-com", "brand.example.com", "OWNED", "OWNED", 1, 33.3d),
                new KnowledgeAssetSourceRepository.SourceSnapshotFactView("ka-run-task-1", "domain:brand-example-com", "brand.example.com", "OWNED", "OWNED", 3, 60d)
        ));
        when(knowledgeAssetSourceRepository.listCitationsByRunIds(List.of("ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.CitationView("ka-run-task-1", "probe-1", "attempt-1", null, "domain:brand-example-com", "brand.example.com", "https://brand.example.com/article", "官网文章", null, "OWNED", "OWNED", "doubao", java.time.OffsetDateTime.parse("2026-05-05T10:31:00+08:00")),
                new KnowledgeAssetSourceRepository.CitationView("ka-run-task-1", "probe-1", "attempt-1", null, "domain:brand-example-com", "brand.example.com", "https://brand.example.com/article", "官网文章", null, "OWNED", "OWNED", "doubao", java.time.OffsetDateTime.parse("2026-05-05T10:31:00+08:00")),
                new KnowledgeAssetSourceRepository.CitationView("ka-run-task-1", "probe-1", "attempt-1", null, "domain:media-example-com", "media.example.com", "https://media.example.com/story", "媒体报道", null, "EDITORIAL", "EXTERNAL", "doubao", java.time.OffsetDateTime.parse("2026-05-05T10:31:00+08:00"))
        ));
        when(knowledgeAssetSourceRepository.listCitationsByRunIds(List.of("ka-run-task-0"))).thenReturn(List.of(
                new KnowledgeAssetSourceRepository.CitationView("ka-run-task-0", null, null, null, "domain:media-example-com", "media.example.com", "https://media.example.com/story", "媒体报道", null, "EDITORIAL", "EXTERNAL", "doubao", java.time.OffsetDateTime.parse("2026-04-29T10:31:00+08:00")),
                new KnowledgeAssetSourceRepository.CitationView("ka-run-task-0", null, null, null, "domain:brand-example-com", "brand.example.com", "https://brand.example.com/old", "历史官网文章", null, "OWNED", "OWNED", "doubao", java.time.OffsetDateTime.parse("2026-04-29T10:31:00+08:00"))
        ));
        when(knowledgeAssetProblemRepository.listActiveTopics("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.TopicCatalogView("topic-cat-1", "topic-1", "产品选购", "围绕选购", "orthogonal", true, null)
        ));
        when(knowledgeAssetProblemRepository.listActivePrompts("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.PromptCatalogView("prompt-cat-1", "topic-cat-1", "prompt-1", "怎么比较竞品", 1, true)
        ));
        when(knowledgeAssetProblemRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.ProblemWindowFactView("orthogonal", "topic-cat-1", "cluster-a", "REPORT_CLUSTER_BREAKDOWN", 3, 4, 9, 7, 6, 31.5d)
        ));
        when(knowledgeAssetProblemRepository.listSnapshotFactsByRunIds(List.of("ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.ProblemSnapshotFactView("ka-run-task-1", "orthogonal", "topic-cat-1", "cluster-a", "REPORT_CLUSTER_BREAKDOWN", 2, 4, 3, 2, 29.5d)
        ));
        when(knowledgeAssetEvidenceRepository.listProbesByRunId("ka-run-task-1")).thenReturn(List.of(
                new KnowledgeAssetEvidenceRepository.ProbeView(
                        "probe-1",
                        "ka-run-task-1",
                        "source-probe-1",
                        "probe-ref-1",
                        1,
                        "topic-cat-1",
                        "prompt-cat-1",
                        "orthogonal",
                        "怎么比较竞品"
                )
        ));
        when(knowledgeAssetEvidenceRepository.listAttemptsByRunId("ka-run-task-1")).thenReturn(List.of(
                new KnowledgeAssetEvidenceRepository.AttemptView(
                        "attempt-1",
                        "ka-run-task-1",
                        "probe-1",
                        "source-attempt-1",
                        "doubao",
                        "doubao-pro",
                        3,
                        true,
                        "这是完整回答",
                        "这是预览",
                        "{\"count\":3}",
                        "{}",
                        LocalDateTime.parse("2026-05-05T10:30:00")
                )
        ));
        when(knowledgeAssetEvidenceRepository.listBrandMentionsByRunId("ka-run-task-1")).thenReturn(List.of(
                new KnowledgeAssetEvidenceRepository.BrandMentionView(
                        "mention-1",
                        "ka-run-task-1",
                        "probe-1",
                        "attempt-1",
                        "source-mention-1",
                        "meichuwuyu",
                        "美厨物语",
                        "0",
                        true,
                        true,
                        1,
                        2,
                        java.math.BigDecimal.valueOf(0.88d),
                        "{}",
                        LocalDateTime.parse("2026-05-05T10:31:00")
                )
        ));
        when(knowledgeAssetMetricRepository.listActiveMetricRegistry()).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricRegistryView("visibility.score", "可见度得分", "visibility", "NUMBER", "score", "WEIGHTED_AVG", "HIGHER_BETTER", "竞品、品牌或主题在回答中的可见度"),
                new KnowledgeAssetMetricRepository.MetricRegistryView("model.attempt_count", "模型调用次数", "model", "NUMBER", "count", "SUM", "NEUTRAL", "按模型切面的尝试次数")
        ));
        when(knowledgeAssetMetricRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "competitor",
                        "competitor-a",
                        "visibility.score",
                        "slice:competitor|competitor-name:a",
                        3,
                        68.5d,
                        60.0d,
                        70.0d,
                        null,
                        "score",
                        "{\"slice\":\"competitor\",\"competitorName\":\"竞品A\"}",
                        "{\"runIds\":[\"ka-run-task-1\"],\"factCount\":1}",
                        "{}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "kpi.sample_size",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        12.0d,
                        12.0d,
                        12.0d,
                        null,
                        "count",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":12.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.score",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        36.5d,
                        31.0d,
                        42.0d,
                        null,
                        "score",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":12.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.mention_rate",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        48.0d,
                        41.0d,
                        55.0d,
                        null,
                        "percent",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":12.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.recommend_rate",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        31.0d,
                        25.0d,
                        37.0d,
                        null,
                        "percent",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":12.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.top3_rate",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        22.0d,
                        17.0d,
                        27.0d,
                        null,
                        "percent",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":12.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.average_position",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        1.8d,
                        1.5d,
                        2.1d,
                        null,
                        "rank",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{\"weightSum\":9.0}"
                ),
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "visibility.mention_count",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        5.0d,
                        2.0d,
                        3.0d,
                        null,
                        "count",
                        "{\"slice\":\"topic\",\"topicCatalogId\":\"topic-cat-1\"}",
                        "{\"runIds\":[\"ka-run-task-1\",\"ka-run-task-0\"],\"factCount\":2}",
                        "{}"
                )
        ));
        when(knowledgeAssetMetricRepository.listRunFacts("ka-run-task-1")).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricFactView(
                        "ka-run-task-1",
                        "model",
                        "doubao:doubao-1-5-thinking",
                        "model.attempt_count",
                        "slice:model|model-id:doubao-1-5-thinking",
                        1.0d,
                        null,
                        "count",
                        "{\"slice\":\"model\",\"modelId\":\"doubao-1.5-thinking\"}",
                        "{}"
                )
        ));

        KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult result =
                service.materializeScope("scope-1");

        assertFalse(result.skipped());
        assertEquals(List.of("brand-performance", "citation-sources", "problem-analysis", "metric-slices"), result.materializedDomains());

        ArgumentCaptor<KnowledgeAssetProjectionRepository.ProjectionUpsert> brandCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProjectionRepository.ProjectionUpsert.class);
        ArgumentCaptor<KnowledgeAssetProjectionRepository.ProjectionUpsert> sourceCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProjectionRepository.ProjectionUpsert.class);
        ArgumentCaptor<KnowledgeAssetProjectionRepository.ProjectionUpsert> problemCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProjectionRepository.ProjectionUpsert.class);
        ArgumentCaptor<KnowledgeAssetProjectionRepository.ProjectionUpsert> metricCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetProjectionRepository.ProjectionUpsert.class);
        verify(knowledgeAssetProjectionRepository).upsertBrandPerformanceProjection(brandCaptor.capture());
        verify(knowledgeAssetProjectionRepository).upsertCitationSourcesProjection(sourceCaptor.capture());
        verify(knowledgeAssetProjectionRepository).upsertProblemAnalysisProjection(problemCaptor.capture());
        verify(knowledgeAssetProjectionRepository).upsertMetricSlicesProjection(metricCaptor.capture());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode brandPayload = mapper.readTree(brandCaptor.getValue().payloadJson());
        assertEquals("default:brand-performance", brandCaptor.getValue().projectionKey());
        assertEquals(2, brandPayload.get("configuredCompetitorCount").asInt());
        assertEquals(1, brandPayload.get("windowCompetitorRows").size());
        assertEquals("竞品A", brandPayload.get("latestSnapshotRows").get(0).get("competitorName").asText());
        assertEquals(1, brandPayload.get("topicRows").size());
        assertEquals(12, brandPayload.get("topicRows").get(0).get("sampleSize").asInt());
        assertEquals(36.5d, brandPayload.get("topicRows").get(0).get("visibilityScore").asDouble());
        assertEquals(48.0d, brandPayload.get("topicRows").get(0).get("mentionRate").asDouble());
        assertTrue(brandPayload.get("truthSources").has("analysisInputTruth"));
        assertTrue(brandPayload.get("truthSources").has("topicMetricTruth"));

        JsonNode sourcePayload = mapper.readTree(sourceCaptor.getValue().payloadJson());
        assertEquals(12, sourcePayload.get("windowSummary").get("totalCitations").asInt());
        assertEquals("brand.example.com", sourcePayload.get("ownedDomains").get(0).get("domainName").asText());
        assertEquals("domain:brand-example-com", sourcePayload.get("windowDomainRows").get(0).get("sourceKey").asText());
        assertEquals(2, sourcePayload.get("previousSnapshotRows").size());
        assertEquals(2, sourcePayload.get("latestUrlRows").get(0).get("usedCount").asInt());
        assertEquals("https://media.example.com/story", sourcePayload.get("previousUrlRows").get(0).get("url").asText());
        assertEquals(2, sourcePayload.get("windowTrendPoints").size());
        assertEquals("2026-05-05", sourcePayload.get("windowTrendPoints").get(1).get("label").asText());
        assertEquals("自有资产", sourcePayload.get("windowTrendPoints").get(1).get("topDomains").get(0).get("ownershipLabel").asText());
        assertTrue(sourcePayload.get("truthSources").has("rootAssetTruth"));

        JsonNode problemPayload = mapper.readTree(problemCaptor.getValue().payloadJson());
        assertEquals(1, problemPayload.get("windowSummary").get("topicCount").asInt());
        assertEquals("产品选购", problemPayload.get("topicRows").get(0).get("topicName").asText());
        assertEquals("怎么比较竞品", problemPayload.get("topicRows").get(0).get("sampleQuestions").get(0).asText());
        assertEquals(1, problemPayload.get("promptRows").get(0).get("probeCount").asInt());
        assertEquals(3, problemPayload.get("promptRows").get(0).get("evidenceCount").asInt());
        assertEquals("completed", problemPayload.get("promptRows").get(0).get("latestProbeStatus").asText());
        assertEquals(88.0d, problemPayload.get("promptRows").get(0).get("visibilityScore").asDouble());
        assertEquals(100.0d, problemPayload.get("promptRows").get(0).get("citationShare").asDouble());
        assertEquals("REPORT_CLUSTER_BREAKDOWN", problemPayload.get("windowBucketRows").get(0).get("factSource").asText());
        assertTrue(problemPayload.get("windowBucketRows").get(0).get("avgSignalScore").asDouble() > 30d);

        JsonNode metricPayload = mapper.readTree(metricCaptor.getValue().payloadJson());
        assertEquals("default:metric-slices", metricCaptor.getValue().projectionKey());
        assertEquals(2, metricPayload.get("windowSummary").get("registryCount").asInt());
        assertEquals("visibility.score", metricPayload.get("windowMetricRows").get(0).get("metricKey").asText());
        assertEquals("model", metricPayload.get("latestRunMetricRows").get(0).get("metricGroup").asText());
        assertTrue(metricPayload.get("truthSources").has("metricRegistryTruth"));
    }

    @Test
    void shouldSkipWhenProjectionMaterializationDisabled() {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        properties.setEnabled(true);

        KnowledgeAssetProjectionMaterializationService service = new KnowledgeAssetProjectionMaterializationService(
                properties,
                knowledgeAssetRunRepository,
                knowledgeAssetCompetitorRepository,
                knowledgeAssetSourceRepository,
                knowledgeAssetEvidenceRepository,
                knowledgeAssetProblemRepository,
                knowledgeAssetMetricRepository,
                knowledgeAssetProjectionRepository,
                new ObjectMapper()
        );

        KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult result =
                service.materializeScope("scope-1");

        assertTrue(result.skipped());
        assertEquals("projection_materialization_disabled", result.reason());
    }
}
