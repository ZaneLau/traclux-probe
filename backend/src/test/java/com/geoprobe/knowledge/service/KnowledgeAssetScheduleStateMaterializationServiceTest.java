package com.geoprobe.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetScheduleStateRepository;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAssetScheduleStateMaterializationServiceTest {

    @Mock
    private KnowledgeAssetRunRepository knowledgeAssetRunRepository;

    @Mock
    private KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;

    @Mock
    private KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;

    @Mock
    private KnowledgeAssetScheduleStateRepository knowledgeAssetScheduleStateRepository;

    @Mock
    private BrandSpaceTopicMapper brandSpaceTopicMapper;

    @Mock
    private BrandSpacePromptMapper brandSpacePromptMapper;

    @Test
    void shouldMaterializeBrandAndTopicScheduleStatesFromKnowledgeAssets() {
        KnowledgeAssetScheduleStateMaterializationService service =
                new KnowledgeAssetScheduleStateMaterializationService(
                        knowledgeAssetRunRepository,
                        knowledgeAssetProblemRepository,
                        knowledgeAssetMetricRepository,
                        knowledgeAssetScheduleStateRepository,
                        brandSpaceTopicMapper,
                        brandSpacePromptMapper,
                        new ObjectMapper()
                );

        OffsetDateTime observedAt = OffsetDateTime.parse("2026-05-08T10:00:00+08:00");
        when(knowledgeAssetRunRepository.findLatestRunByScope("scope-1"))
                .thenReturn(new KnowledgeAssetRunRepository.LatestRunView(
                        "ka-run-task-2",
                        "task-2",
                        "report-2",
                        8,
                        observedAt
                ));
        when(knowledgeAssetRunRepository.listRunDetailsByScopeWithinWindow(
                "scope-1",
                observedAt.minusDays(14),
                observedAt
        )).thenReturn(List.of(
                new KnowledgeAssetRunRepository.RunDetailView(
                        "ka-run-task-2",
                        "org-1",
                        "scope-1",
                        "task-2",
                        "report-2",
                        "doubao",
                        8,
                        "{}",
                        "{}",
                        "{}",
                        observedAt
                ),
                new KnowledgeAssetRunRepository.RunDetailView(
                        "ka-run-task-1",
                        "org-1",
                        "scope-1",
                        "task-1",
                        "report-1",
                        "doubao",
                        6,
                        "{}",
                        "{}",
                        "{}",
                        observedAt.minusDays(3)
                )
        ));
        when(knowledgeAssetProblemRepository.listActiveTopics("scope-1")).thenReturn(List.of(
                new KnowledgeAssetProblemRepository.TopicCatalogView(
                        "topic-cat-1",
                        "topic-1",
                        "产品选购",
                        "围绕选购",
                        "orthogonal",
                        true,
                        observedAt.minusDays(5)
                ),
                new KnowledgeAssetProblemRepository.TopicCatalogView(
                        "topic-cat-2",
                        "topic-2",
                        "品牌推荐",
                        "围绕推荐",
                        "comparison",
                        true,
                        observedAt.plusHours(1)
                )
        ));
        when(brandSpaceTopicMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                BrandSpaceTopic.builder()
                        .id("topic-1")
                        .brandScopeId("scope-1")
                        .updatedAt(java.time.LocalDateTime.parse("2026-05-03T10:00:00"))
                        .build(),
                BrandSpaceTopic.builder()
                        .id("topic-2")
                        .brandScopeId("scope-1")
                        .updatedAt(java.time.LocalDateTime.parse("2026-05-08T11:00:00"))
                        .build()
        ));
        when(brandSpacePromptMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                BrandSpacePrompt.builder()
                        .id("prompt-2")
                        .brandScopeId("scope-1")
                        .topicId("topic-2")
                        .updatedAt(java.time.LocalDateTime.parse("2026-05-08T11:30:00"))
                        .build()
        ));
        when(knowledgeAssetMetricRepository.listWindowFacts("scope-1", "last_7_days")).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "kpi.sample_size",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        5.0d,
                        2.0d,
                        3.0d,
                        null,
                        "count",
                        "{}",
                        "{}",
                        "{}"
                )
        ));
        when(knowledgeAssetMetricRepository.listWindowFacts("scope-1", "last_14_days")).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricWindowFactView(
                        "topic",
                        "topic-cat-1",
                        "kpi.sample_size",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        2,
                        9.0d,
                        4.0d,
                        5.0d,
                        null,
                        "count",
                        "{}",
                        "{}",
                        "{}"
                )
        ));
        when(knowledgeAssetMetricRepository.listRunFactsByRunIds(List.of("ka-run-task-2", "ka-run-task-1"))).thenReturn(List.of(
                new KnowledgeAssetMetricRepository.MetricFactView(
                        "ka-run-task-2",
                        "topic",
                        "topic-cat-1",
                        "kpi.sample_size",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        5.0d,
                        null,
                        "count",
                        "{}",
                        "{}"
                ),
                new KnowledgeAssetMetricRepository.MetricFactView(
                        "ka-run-task-1",
                        "topic",
                        "topic-cat-1",
                        "kpi.sample_size",
                        "slice:topic|topic-catalog-id:topic-cat-1",
                        4.0d,
                        null,
                        "count",
                        "{}",
                        "{}"
                )
        ));

        KnowledgeAssetScheduleStateMaterializationService.ScheduleStateMaterializationResult result =
                service.materializeScope("scope-1");

        assertFalse(result.skipped());
        assertEquals("scope-1", result.brandScopeId());
        assertEquals("ka-run-task-2", result.latestRunId());
        assertEquals(2, result.topicStateCount());

        ArgumentCaptor<KnowledgeAssetScheduleStateRepository.BrandScheduleStateUpsert> brandCaptor =
                ArgumentCaptor.forClass(KnowledgeAssetScheduleStateRepository.BrandScheduleStateUpsert.class);
        ArgumentCaptor<List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert>> topicCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(knowledgeAssetScheduleStateRepository).upsertBrandState(brandCaptor.capture());
        verify(knowledgeAssetScheduleStateRepository).replaceTopicStates(org.mockito.ArgumentMatchers.eq("scope-1"), topicCaptor.capture());

        KnowledgeAssetScheduleStateRepository.BrandScheduleStateUpsert brandState = brandCaptor.getValue();
        assertEquals("scope-1", brandState.brandScopeId());
        assertEquals(2, brandState.activeTopicCount());
        assertEquals(1, brandState.coveredTopicCount7d());
        assertTrue(brandState.justEditedFlag());
        assertTrue(brandState.suggestedBrandWeight() > 0.0d);

        List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert> topicStates = topicCaptor.getValue();
        assertEquals(2, topicStates.size());
        KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert first = topicStates.getFirst();
        assertEquals("topic-cat-2", first.topicCatalogId());
        assertTrue(first.justEditedFlag());
        assertTrue(first.coldStartFlag());
        assertEquals(0, first.last7dProbeCount());
        assertTrue(first.suggestedPriorityScore() > 0.5d);

        KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert second = topicStates.get(1);
        assertEquals("topic-cat-1", second.topicCatalogId());
        assertEquals(5, second.last1dProbeCount());
        assertEquals(9, second.last14dProbeCount());
        assertEquals(9, second.last14dSampleSize());
        assertTrue(second.latestCoveredAt() != null);
    }
}
