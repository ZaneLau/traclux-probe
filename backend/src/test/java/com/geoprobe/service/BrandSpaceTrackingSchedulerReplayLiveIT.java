package com.geoprobe.service;

import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.knowledge.repository.KnowledgeAssetScheduleStateRepository;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.security.enforce-jwt-secret=false",
        "app.security.secret-assets.prompt-asset-fail-fast=false",
        "app.knowledge-asset.enabled=true",
        "app.knowledge-asset.flyway.enabled=false",
        "app.knowledge-asset.ingestion.history-sync.run-on-startup=false",
        "spring.task.scheduling.enabled=false"
})
@EnabledIfSystemProperty(named = "brandspace.replay.live", matches = "true")
class BrandSpaceTrackingSchedulerReplayLiveIT {

    private static final String BRAND_SPACE_ID = "d7791807-558b-4eaf-97b6-2a3dce06b514";

    @Autowired
    private BrandSpaceTrackingSchedulerService schedulerService;

    @Autowired
    private KnowledgeAssetScheduleStateRepository knowledgeAssetScheduleStateRepository;

    @Autowired
    private DiagnosisTaskMapper diagnosisTaskMapper;

    @MockBean
    private V42DiagnosisExecutionService v42DiagnosisExecutionService;

    @MockBean
    private CreditService creditService;

    @Test
    void shouldReplayRealBrandSpaceIntoTaskShellWithScheduleTruth() {
        when(creditService.hasEnoughCredits(anyString(), anyInt())).thenReturn(true);
        when(creditService.deductCredits(anyString(), anyString(), anyInt())).thenReturn(true);

        BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult result =
                schedulerService.replayBrandSpace(BRAND_SPACE_ID);
        DiagnosisTask latestTask = result.skipped()
                ? diagnosisTaskMapper.selectLatestByBrandScopeIds(List.of(BRAND_SPACE_ID)).stream().findFirst().orElse(null)
                : diagnosisTaskMapper.selectById(result.runId());
        assertNotNull(latestTask);
        if (!result.skipped()) {
            assertNotNull(result.runId());
            assertNotNull(result.requestId());
            assertTrue(result.requestId().startsWith("bs-replay-"));
            assertEquals(result.runId(), latestTask.getId());
            assertEquals(result.targetProbeCount(), latestTask.getTargetProbeCount());
        } else {
            assertFalse("brand_space_not_found".equals(result.reason()));
            assertFalse("brand_space_group_empty".equals(result.reason()));
        }

        KnowledgeAssetScheduleStateRepository.BrandScheduleStateView brandState =
                knowledgeAssetScheduleStateRepository.findBrandState(BRAND_SPACE_ID);
        assertNotNull(brandState);
        assertFalse(knowledgeAssetScheduleStateRepository.listTopicStates(BRAND_SPACE_ID).isEmpty());

        Map<String, Object> taskMetadata = readObjectMap(latestTask.getTaskOrderJson().get("taskMetadata"));
        String requestId = String.valueOf(taskMetadata.get("requestId"));
        assertTrue(requestId.startsWith("bs-replay-") || requestId.startsWith("bs-schedule-replay-"));
        assertEquals("brand_space_schedule", taskMetadata.get("source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> businessHints = (Map<String, Object>) latestTask.getTaskOrderJson().get("businessHints");
        assertNotNull(businessHints.get("topicPlan"));
        assertNotNull(businessHints.get("schedulePolicy"));
        assertNotNull(businessHints.get("scheduleDecision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schedulePolicy = (Map<String, Object>) businessHints.get("schedulePolicy");
        assertNotNull(schedulePolicy.get("accountAvailableCredits"));
        assertNotNull(schedulePolicy.get("weeklyTargetTopicCount"));
        assertNotNull(schedulePolicy.get("weeklyGapTopicCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> scheduleDecision = (Map<String, Object>) businessHints.get("scheduleDecision");
        Integer accountAvailableCredits = scheduleDecision.get("accountAvailableCredits") instanceof Number number
                ? number.intValue()
                : null;
        Integer targetProbeCount = scheduleDecision.get("targetProbeCount") instanceof Number number
                ? number.intValue()
                : null;
        if (accountAvailableCredits != null && accountAvailableCredits >= 20 && targetProbeCount != null) {
            assertTrue(targetProbeCount >= 20);
        }

        if (!result.skipped()) {
            latestTask.setStatusCode(3);
            latestTask.setFailureReasonCode("harness_cleanup");
            latestTask.setFailureMessage("live replay test cleanup");
            latestTask.setCompletedAt(LocalDateTime.now());
            diagnosisTaskMapper.updateById(latestTask);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
