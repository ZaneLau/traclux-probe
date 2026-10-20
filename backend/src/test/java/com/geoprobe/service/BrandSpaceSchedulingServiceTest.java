package com.geoprobe.service;

import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.knowledge.repository.KnowledgeAssetScheduleStateRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetScheduleStateMaterializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceSchedulingServiceTest {

    @Mock
    private ObjectProvider<KnowledgeAssetScheduleStateRepository> knowledgeAssetScheduleStateRepositoryProvider;

    @Mock
    private KnowledgeAssetScheduleStateRepository knowledgeAssetScheduleStateRepository;

    @Mock
    private ObjectProvider<KnowledgeAssetScheduleStateMaterializationService> knowledgeAssetScheduleStateMaterializationServiceProvider;

    @Test
    void createInitialRunShouldNotCollapseToEnabledPromptCount() {
        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        BrandSpaceSchedulingService.ExecutionPlan plan = service.planCreateInitialRun(
                List.of(
                        BrandSpaceTopicPayload.builder().tempId("topic-a").name("品牌选择").enabled(true).build(),
                        BrandSpaceTopicPayload.builder().tempId("topic-b").name("风险异议").enabled(true).build(),
                        BrandSpaceTopicPayload.builder().tempId("topic-c").name("渠道评价").enabled(true).build()
                ),
                List.of(
                        BrandSpacePromptPayload.builder().tempId("prompt-a").topicTempId("topic-a").text("A").enabled(true).build(),
                        BrandSpacePromptPayload.builder().tempId("prompt-b").topicTempId("topic-b").text("B").enabled(true).build(),
                        BrandSpacePromptPayload.builder().tempId("prompt-c").topicTempId("topic-c").text("C").enabled(true).build()
                ),
                ServiceAccessView.builder()
                        .limits(ServiceAccessView.Limits.builder()
                                .promptLimitPerBrandSpace(50)
                                .build())
                        .build(),
                50
        );

        assertTrue(plan.shouldExecute());
        assertEquals(24, plan.targetProbeCount());
        assertEquals(3, plan.selectedTopics().size());
        assertTrue(plan.selectedTopics().stream().mapToInt(BrandSpaceSchedulingService.TopicDecision::allocatedProbeCount).sum() > 3);
    }

    @Test
    void previewAndCreateInitialRunShouldCarryAccountContext() {
        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        List<BrandSpaceTopicPayload> topics = List.of(
                BrandSpaceTopicPayload.builder().tempId("topic-a").name("品牌选择").enabled(true).build(),
                BrandSpaceTopicPayload.builder().tempId("topic-b").name("风险异议").enabled(true).build(),
                BrandSpaceTopicPayload.builder().tempId("topic-c").name("渠道评价").enabled(true).build()
        );
        List<BrandSpacePromptPayload> prompts = List.of(
                BrandSpacePromptPayload.builder().tempId("prompt-a").topicTempId("topic-a").text("A").enabled(true).build(),
                BrandSpacePromptPayload.builder().tempId("prompt-b").topicTempId("topic-b").text("B").enabled(true).build(),
                BrandSpacePromptPayload.builder().tempId("prompt-c").topicTempId("topic-c").text("C").enabled(true).build()
        );
        ServiceAccessView serviceAccess = serviceAccess();

        BrandSpaceSchedulingService.ExecutionPlan previewPlan = service.planPreview(topics, prompts, serviceAccess);
        assertEquals(50, previewPlan.accountAvailableCredits());
        assertEquals(1, previewPlan.accountBrandCount());
        assertEquals(3, previewPlan.weeklyTargetTopicCount());
        assertEquals(3, previewPlan.weeklyGapTopicCount());

        BrandSpaceSchedulingService.ExecutionPlan createPlan =
                service.planCreateInitialRun(topics, prompts, serviceAccess, 18);
        assertEquals(18, createPlan.accountAvailableCredits());
        assertEquals(1, createPlan.accountBrandCount());
        assertEquals(3, createPlan.weeklyTargetTopicCount());
        assertEquals(3, createPlan.weeklyGapTopicCount());
    }

    @Test
    void scheduledAccountShouldPreferHigherWeightBrandUnderSharedBudget() {
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        0,
                        2,
                        4,
                        3,
                        1,
                        0.7d,
                        0.3d,
                        true,
                        false,
                        0.9d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-b"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-b",
                        "run-b",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        6,
                        12,
                        18,
                        3,
                        3,
                        0.1d,
                        0.1d,
                        false,
                        false,
                        0.2d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of(
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView("scope-a", "topic-a1", "run-a", null, 0, 0, 0, 0, 1, 1.0d, 1.0d, 0.0d, true, false, 0.95d, "{}"),
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView("scope-a", "topic-a2", "run-a", null, 0, 0, 0, 0, 1, 0.8d, 0.9d, 0.0d, true, false, 0.85d, "{}")
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-b"))
                .thenReturn(List.of(
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView("scope-b", "topic-b1", "run-b", null, 4, 10, 14, 18, 1, 0.2d, 0.2d, 0.6d, false, false, 0.35d, "{}"),
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView("scope-b", "topic-b2", "run-b", null, 2, 6, 8, 12, 1, 0.2d, 0.2d, 0.5d, false, false, 0.30d, "{}")
                ));
        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(
                        new BrandSpaceSchedulingService.ScheduledScopeCandidate("scope-a", configView("scope-a", "topic-a1", "topic-a2"), null, serviceAccess()),
                        new BrandSpaceSchedulingService.ScheduledScopeCandidate("scope-b", configView("scope-b", "topic-b1", "topic-b2"), DiagnosisTask.builder().id("run-b").build(), serviceAccess())
                ),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(20, 2, 2)
        );

        assertTrue(plans.get("scope-a").targetProbeCount() > plans.get("scope-b").targetProbeCount());
        assertEquals(20, plans.get("scope-a").targetProbeCount() + plans.get("scope-b").targetProbeCount());
        assertTrue(plans.get("scope-a").humanReason().contains("首跑"));
    }

    @Test
    void scheduledAccountShouldMaterializeMissingScheduleTruthBeforePlanning() {
        KnowledgeAssetScheduleStateMaterializationService materializationService =
                org.mockito.Mockito.mock(KnowledgeAssetScheduleStateMaterializationService.class);
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateMaterializationServiceProvider.getIfAvailable()).thenReturn(materializationService);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(null)
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        1,
                        4,
                        8,
                        2,
                        2,
                        0.0d,
                        0.1d,
                        false,
                        false,
                        0.4d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a",
                                "topic-a1",
                                "run-a",
                                OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                1,
                                4,
                                8,
                                8,
                                1,
                                0.0d,
                                0.1d,
                                0.0d,
                                false,
                                false,
                                0.6d,
                                "{}"
                        ),
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a",
                                "topic-a2",
                                "run-a",
                                OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                1,
                                4,
                                8,
                                8,
                                1,
                                0.0d,
                                0.1d,
                                0.0d,
                                false,
                                false,
                                0.5d,
                                "{}"
                        )
                ));
        when(materializationService.materializeScope("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateMaterializationService.ScheduleStateMaterializationResult(
                        "scope-a",
                        "run-a",
                        false,
                        2,
                        null
                ));

        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(new BrandSpaceSchedulingService.ScheduledScopeCandidate(
                        "scope-a",
                        configView("scope-a", "topic-a1", "topic-a2"),
                        DiagnosisTask.builder().id("run-a").build(),
                        serviceAccess()
                )),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(20, 1, 1)
        );

        verify(materializationService).materializeScope(eq("scope-a"));
        assertEquals(1, plans.size());
        assertTrue(plans.get("scope-a").shouldExecute());
    }

    @Test
    void scheduledAccountShouldRematerializeExistingScheduleTruthBeforePlanning() {
        KnowledgeAssetScheduleStateMaterializationService materializationService =
                org.mockito.Mockito.mock(KnowledgeAssetScheduleStateMaterializationService.class);
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateMaterializationServiceProvider.getIfAvailable()).thenReturn(materializationService);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        1,
                        4,
                        8,
                        2,
                        1,
                        0.5d,
                        0.1d,
                        false,
                        true,
                        0.8d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of(
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a",
                                "topic-a1",
                                "run-a",
                                OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                1,
                                4,
                                8,
                                8,
                                1,
                                0.4d,
                                0.2d,
                                0.0d,
                                false,
                                true,
                                0.9d,
                                "{}"
                        ),
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a",
                                "topic-a2",
                                "run-a",
                                OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                1,
                                4,
                                8,
                                8,
                                1,
                                0.3d,
                                0.2d,
                                0.0d,
                                false,
                                false,
                                0.6d,
                                "{}"
                        )
                ));
        when(materializationService.materializeScope("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateMaterializationService.ScheduleStateMaterializationResult(
                        "scope-a",
                        "run-a",
                        false,
                        2,
                        null
                ));

        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(new BrandSpaceSchedulingService.ScheduledScopeCandidate(
                        "scope-a",
                        configView("scope-a", "topic-a1", "topic-a2"),
                        DiagnosisTask.builder().id("run-a").build(),
                        serviceAccess()
                )),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(20, 1, 1)
        );

        verify(materializationService).materializeScope(eq("scope-a"));
        assertTrue(plans.get("scope-a").humanReason().contains("刚新增或刚修改的主题"));
    }

    @Test
    void scheduledAccountShouldUseSpareBudgetToDeepenSingleBrandCoverage() {
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        20,
                        80,
                        210,
                        10,
                        10,
                        0.0d,
                        0.0d,
                        false,
                        false,
                        0.0d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of(
                        topicState("scope-a", "topic-a1", 0.10d),
                        topicState("scope-a", "topic-a2", 0.10d),
                        topicState("scope-a", "topic-a3", 0.10d),
                        topicState("scope-a", "topic-a4", 0.10d),
                        topicState("scope-a", "topic-a5", 0.10d),
                        topicState("scope-a", "topic-a6", 0.10d),
                        topicState("scope-a", "topic-a7", 0.10d),
                        topicState("scope-a", "topic-a8", 0.10d),
                        topicState("scope-a", "topic-a9", 0.10d),
                        topicState("scope-a", "topic-a10", 0.10d)
                ));
        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(new BrandSpaceSchedulingService.ScheduledScopeCandidate(
                        "scope-a",
                        configView("scope-a",
                                "topic-a1", "topic-a2", "topic-a3", "topic-a4", "topic-a5",
                                "topic-a6", "topic-a7", "topic-a8", "topic-a9", "topic-a10"),
                        DiagnosisTask.builder().id("run-a").build(),
                        serviceAccess()
                )),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(48, 1, 1)
        );

        BrandSpaceSchedulingService.ExecutionPlan plan = plans.get("scope-a");
        assertTrue(plan.shouldExecute());
        assertEquals(30, plan.targetProbeCount());
        assertTrue(plan.humanReason().contains("账户还有可用积分"));
        assertEquals(48, plan.accountAvailableCredits());
        assertEquals(10, plan.weeklyTargetTopicCount());
        assertEquals(0, plan.weeklyGapTopicCount());
    }

    @Test
    void scheduledAccountShouldNotHalveHighIntensityBrandBelowHealthyFloor() {
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        20,
                        80,
                        210,
                        10,
                        10,
                        0.0d,
                        0.0d,
                        false,
                        false,
                        0.0d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of(
                        topicState("scope-a", "topic-a1", 0.10d),
                        topicState("scope-a", "topic-a2", 0.10d),
                        topicState("scope-a", "topic-a3", 0.10d),
                        topicState("scope-a", "topic-a4", 0.10d),
                        topicState("scope-a", "topic-a5", 0.10d),
                        topicState("scope-a", "topic-a6", 0.10d),
                        topicState("scope-a", "topic-a7", 0.10d),
                        topicState("scope-a", "topic-a8", 0.10d),
                        topicState("scope-a", "topic-a9", 0.10d),
                        topicState("scope-a", "topic-a10", 0.10d)
                ));
        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(new BrandSpaceSchedulingService.ScheduledScopeCandidate(
                        "scope-a",
                        configView("scope-a",
                                "topic-a1", "topic-a2", "topic-a3", "topic-a4", "topic-a5",
                                "topic-a6", "topic-a7", "topic-a8", "topic-a9", "topic-a10"),
                        DiagnosisTask.builder().id("run-a").build(),
                        serviceAccess()
                )),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(20, 1, 1)
        );

        BrandSpaceSchedulingService.ExecutionPlan plan = plans.get("scope-a");
        assertTrue(plan.shouldExecute());
        assertEquals(20, plan.targetProbeCount());
        assertTrue(plan.humanReason().contains("均衡节奏"));
    }

    @Test
    void scheduledAccountShouldIgnoreTopicsWithoutEnabledPrompts() {
        when(knowledgeAssetScheduleStateRepositoryProvider.getIfAvailable()).thenReturn(knowledgeAssetScheduleStateRepository);
        when(knowledgeAssetScheduleStateRepository.findBrandState("scope-a"))
                .thenReturn(new KnowledgeAssetScheduleStateRepository.BrandScheduleStateView(
                        "scope-a",
                        "run-a",
                        OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                        2,
                        8,
                        12,
                        2,
                        1,
                        0.5d,
                        0.2d,
                        false,
                        false,
                        0.6d,
                        "{}"
                ));
        when(knowledgeAssetScheduleStateRepository.listTopicStates("scope-a"))
                .thenReturn(List.of(
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a", "topic-a", "run-a", OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                2, 8, 12, 12, 1, 0.0d, 0.0d, 0.1d, false, false, 0.95d, "{}"
                        ),
                        new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                                "scope-a", "topic-b", "run-a", OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                                2, 8, 12, 12, 1, 0.0d, 0.0d, 0.1d, false, false, 0.55d, "{}"
                        )
                ));

        BrandSpaceSchedulingService service =
                new BrandSpaceSchedulingService(
                        knowledgeAssetScheduleStateRepositoryProvider,
                        knowledgeAssetScheduleStateMaterializationServiceProvider
                );

        Map<String, BrandSpaceSchedulingService.ExecutionPlan> plans = service.planScheduledAccount(
                List.of(new BrandSpaceSchedulingService.ScheduledScopeCandidate(
                        "scope-a",
                        configViewWithEnabledPromptCounts("scope-a", Map.of("topic-a", 0, "topic-b", 1)),
                        DiagnosisTask.builder().id("run-a").build(),
                        serviceAccess()
                )),
                new BrandSpaceSchedulingService.AccountCreditSnapshot(20, 1, 1)
        );

        BrandSpaceSchedulingService.ExecutionPlan plan = plans.get("scope-a");
        assertTrue(plan.shouldExecute());
        assertEquals(List.of("topic-b"), plan.selectedTopicIds());
        assertFalse(plan.selectedTopicIds().contains("topic-a"));
        assertEquals(1, plan.weeklyTargetTopicCount());
    }

    private ServiceAccessView serviceAccess() {
        return ServiceAccessView.builder()
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
    }

    private KnowledgeAssetScheduleStateRepository.TopicScheduleStateView topicState(String scopeId,
                                                                                    String topicId,
                                                                                    double priorityScore) {
        return new KnowledgeAssetScheduleStateRepository.TopicScheduleStateView(
                scopeId,
                topicId,
                "run-a",
                OffsetDateTime.parse("2026-05-08T09:00:00+08:00"),
                2,
                8,
                12,
                12,
                1,
                0.0d,
                0.0d,
                0.166667d,
                false,
                false,
                priorityScore,
                "{}"
        );
    }

    private BrandSpaceConfigView configView(String scopeId, String... topicIds) {
        List<BrandSpaceConfigView.TopicView> topics = java.util.Arrays.stream(topicIds)
                .map(topicId -> BrandSpaceConfigView.TopicView.builder()
                        .topicId(topicId)
                        .name(topicId)
                        .enabled(true)
                        .seqNo(1)
                        .enabledPromptCount(1)
                        .build())
                .toList();
        List<BrandSpaceConfigView.PromptView> prompts = java.util.Arrays.stream(topicIds)
                .map(topicId -> BrandSpaceConfigView.PromptView.builder()
                        .promptId(topicId + "-prompt")
                        .topicId(topicId)
                        .topicName(topicId)
                        .text(topicId + " prompt")
                        .enabled(true)
                        .build())
                .toList();
        return BrandSpaceConfigView.builder()
                .brandSpaceId(scopeId)
                .topics(topics)
                .prompts(prompts)
                .summary(BrandSpaceConfigView.SummaryView.builder()
                        .enabledTopicCount(topics.size())
                        .enabledPromptCount(prompts.size())
                        .build())
                .build();
    }

    private BrandSpaceConfigView configViewWithEnabledPromptCounts(String scopeId, Map<String, Integer> enabledPromptCounts) {
        List<BrandSpaceConfigView.TopicView> topics = enabledPromptCounts.entrySet().stream()
                .map(entry -> BrandSpaceConfigView.TopicView.builder()
                        .topicId(entry.getKey())
                        .name(entry.getKey())
                        .enabled(true)
                        .seqNo(1)
                        .enabledPromptCount(entry.getValue())
                        .build())
                .toList();
        List<BrandSpaceConfigView.PromptView> prompts = enabledPromptCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> BrandSpaceConfigView.PromptView.builder()
                        .promptId(entry.getKey() + "-prompt")
                        .topicId(entry.getKey())
                        .topicName(entry.getKey())
                        .text(entry.getKey() + " prompt")
                        .enabled(true)
                        .build())
                .toList();
        return BrandSpaceConfigView.builder()
                .brandSpaceId(scopeId)
                .topics(topics)
                .prompts(prompts)
                .summary(BrandSpaceConfigView.SummaryView.builder()
                        .enabledTopicCount(topics.size())
                        .enabledPromptCount(prompts.size())
                        .build())
                .build();
    }
}
