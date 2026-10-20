package com.geoprobe.knowledge.service;

import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAssetHistorySyncServiceTest {

    @Mock
    private DiagnosisReportRecordMapper diagnosisReportRecordMapper;

    @Mock
    private KnowledgeAssetRunRepository knowledgeAssetRunRepository;

    @Mock
    private KnowledgeAssetIngestionService knowledgeAssetIngestionService;

    @Test
    void shouldBackfillMissingHistoryInOrderAndReanchorLatestRun() {
        KnowledgeAssetProperties properties = buildProperties(true, true);
        KnowledgeAssetHistorySyncService service = new KnowledgeAssetHistorySyncService(
                properties,
                diagnosisReportRecordMapper,
                knowledgeAssetRunRepository,
                knowledgeAssetIngestionService
        );
        when(diagnosisReportRecordMapper.selectHistoryRefsByBrandScopeId("scope-1")).thenReturn(List.of(
                historyRef("task-1", LocalDateTime.of(2026, 5, 1, 0, 0)),
                historyRef("task-2", LocalDateTime.of(2026, 5, 2, 0, 0)),
                historyRef("task-3", LocalDateTime.of(2026, 5, 3, 0, 0))
        ));
        when(knowledgeAssetRunRepository.listSourceTaskIdsByScope("scope-1")).thenReturn(List.of("task-3"));
        when(knowledgeAssetIngestionService.ingestRun("task-1"))
                .thenReturn(new KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult("task-1", "scope-1", "report-1", true, false, null));
        when(knowledgeAssetIngestionService.ingestRun("task-2"))
                .thenReturn(new KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult("task-2", "scope-1", "report-2", true, false, null));
        when(knowledgeAssetIngestionService.ingestRun("task-3"))
                .thenReturn(new KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult("task-3", "scope-1", "report-3", true, false, null));

        KnowledgeAssetHistorySyncService.HistorySyncResult result =
                service.ensureScopeHistoryUpToDate("scope-1");

        ArgumentCaptor<String> taskCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgeAssetIngestionService, org.mockito.Mockito.times(3)).ingestRun(taskCaptor.capture());
        assertEquals(List.of("task-1", "task-2", "task-3"), taskCaptor.getAllValues());
        assertFalse(result.skipped());
        assertEquals(3, result.legacyReportCount());
        assertEquals(1, result.existingRunCountBefore());
        assertEquals(2, result.ingestedTaskCount());
        assertTrue(result.reanchoredLatest());
    }

    @Test
    void shouldSkipBackfillWhenHistoryAlreadySynchronized() {
        KnowledgeAssetProperties properties = buildProperties(true, true);
        KnowledgeAssetHistorySyncService service = new KnowledgeAssetHistorySyncService(
                properties,
                diagnosisReportRecordMapper,
                knowledgeAssetRunRepository,
                knowledgeAssetIngestionService
        );
        when(diagnosisReportRecordMapper.selectHistoryRefsByBrandScopeId("scope-1")).thenReturn(List.of(
                historyRef("task-1", LocalDateTime.of(2026, 5, 1, 0, 0)),
                historyRef("task-2", LocalDateTime.of(2026, 5, 2, 0, 0))
        ));
        when(knowledgeAssetRunRepository.listSourceTaskIdsByScope("scope-1")).thenReturn(List.of("task-1", "task-2"));

        KnowledgeAssetHistorySyncService.HistorySyncResult result =
                service.ensureScopeHistoryUpToDate("scope-1");

        assertFalse(result.skipped());
        assertEquals(0, result.ingestedTaskCount());
        assertFalse(result.reanchoredLatest());
        verify(knowledgeAssetIngestionService, never()).ingestRun(org.mockito.ArgumentMatchers.anyString());
    }

    private KnowledgeAssetProperties buildProperties(boolean ingestionEnabled, boolean readRepairEnabled) {
        KnowledgeAssetProperties properties = new KnowledgeAssetProperties();
        properties.setEnabled(true);
        properties.getIngestion().setEnabled(ingestionEnabled);
        properties.getIngestion().getHistorySync().setReadRepairEnabled(readRepairEnabled);
        return properties;
    }

    private DiagnosisReportRecord historyRef(String taskId, LocalDateTime generatedAt) {
        return DiagnosisReportRecord.builder()
                .id("report-" + taskId)
                .diagnosisTaskId(taskId)
                .brandScopeId("scope-1")
                .generatedAt(generatedAt)
                .createdAt(generatedAt)
                .build();
    }
}
