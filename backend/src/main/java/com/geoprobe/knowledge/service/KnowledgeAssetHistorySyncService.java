package com.geoprobe.knowledge.service;

import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetHistorySyncService {

    private final KnowledgeAssetProperties knowledgeAssetProperties;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final KnowledgeAssetRunRepository knowledgeAssetRunRepository;
    private final KnowledgeAssetIngestionService knowledgeAssetIngestionService;
    private final ConcurrentMap<String, ReentrantLock> scopeLocks = new ConcurrentHashMap<>();

    public HistorySyncResult ensureScopeHistoryUpToDate(String brandScopeId) {
        if (!knowledgeAssetProperties.getIngestion().isEnabled()) {
            return HistorySyncResult.skipped(normalizeScopeId(brandScopeId), "knowledge_asset_ingestion_disabled");
        }
        if (!knowledgeAssetProperties.getIngestion().getHistorySync().isReadRepairEnabled()) {
            return HistorySyncResult.skipped(normalizeScopeId(brandScopeId), "knowledge_asset_history_read_repair_disabled");
        }
        return syncScope(brandScopeId);
    }

    public StartupSyncSummary syncAllScopesOnStartup() {
        if (!knowledgeAssetProperties.getIngestion().isEnabled()) {
            return StartupSyncSummary.skipped("knowledge_asset_ingestion_disabled");
        }
        List<String> scopeIds = diagnosisReportRecordMapper.selectDistinctBrandScopeIdsWithReports();
        if (scopeIds == null || scopeIds.isEmpty()) {
            return StartupSyncSummary.completed(0, 0, 0, 0);
        }
        int maxScopes = safeInt(knowledgeAssetProperties.getIngestion().getHistorySync().getStartupMaxScopes());
        List<String> effectiveScopeIds = maxScopes > 0 && scopeIds.size() > maxScopes
                ? scopeIds.subList(0, maxScopes)
                : scopeIds;

        int repairedScopes = 0;
        int ingestedRuns = 0;
        int reanchoredScopes = 0;
        for (String scopeId : effectiveScopeIds) {
            HistorySyncResult result = syncScope(scopeId);
            if (result.ingestedTaskCount() > 0) {
                repairedScopes++;
                ingestedRuns += result.ingestedTaskCount();
            }
            if (result.reanchoredLatest()) {
                reanchoredScopes++;
            }
        }
        return StartupSyncSummary.completed(effectiveScopeIds.size(), repairedScopes, ingestedRuns, reanchoredScopes);
    }

    private HistorySyncResult syncScope(String brandScopeId) {
        String normalizedScopeId = normalizeScopeId(brandScopeId);
        if (!StringUtils.hasText(normalizedScopeId)) {
            return HistorySyncResult.skipped(null, "missing_brand_scope_id");
        }
        ReentrantLock lock = scopeLocks.computeIfAbsent(normalizedScopeId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            List<DiagnosisReportRecord> legacyHistory =
                    diagnosisReportRecordMapper.selectHistoryRefsByBrandScopeId(normalizedScopeId);
            if (legacyHistory == null || legacyHistory.isEmpty()) {
                return HistorySyncResult.skipped(normalizedScopeId, "legacy_history_not_found");
            }
            List<String> orderedTaskIds = orderedTaskIds(legacyHistory);
            if (orderedTaskIds.isEmpty()) {
                return HistorySyncResult.skipped(normalizedScopeId, "legacy_history_missing_task_ids");
            }
            Set<String> existingTaskIds = new LinkedHashSet<>(
                    knowledgeAssetRunRepository.listSourceTaskIdsByScope(normalizedScopeId)
            );
            List<String> missingTaskIds = orderedTaskIds.stream()
                    .filter(taskId -> !existingTaskIds.contains(taskId))
                    .toList();
            if (missingTaskIds.isEmpty()) {
                return HistorySyncResult.completed(normalizedScopeId, orderedTaskIds.size(), existingTaskIds.size(), 0, false, null);
            }

            int ingestedTaskCount = 0;
            for (String taskId : missingTaskIds) {
                KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult result =
                        knowledgeAssetIngestionService.ingestRun(taskId);
                if (result != null && result.synced()) {
                    ingestedTaskCount++;
                } else {
                    log.warn("Knowledge asset history sync skipped task. scopeId={}, taskId={}, reason={}",
                            normalizedScopeId,
                            taskId,
                            result == null ? "unknown" : result.reason());
                }
            }

            boolean reanchoredLatest = false;
            String latestTaskId = orderedTaskIds.getLast();
            if (!missingTaskIds.isEmpty() && !latestTaskId.equals(missingTaskIds.getLast())) {
                KnowledgeAssetIngestionService.KnowledgeAssetIngestionResult result =
                        knowledgeAssetIngestionService.ingestRun(latestTaskId);
                reanchoredLatest = result != null && result.synced();
            }

            log.info("Knowledge asset history sync completed. scopeId={}, legacyReports={}, existingRunsBefore={}, missingRuns={}, ingestedRuns={}, reanchoredLatest={}",
                    normalizedScopeId,
                    orderedTaskIds.size(),
                    existingTaskIds.size(),
                    missingTaskIds.size(),
                    ingestedTaskCount,
                    reanchoredLatest);
            return HistorySyncResult.completed(
                    normalizedScopeId,
                    orderedTaskIds.size(),
                    existingTaskIds.size(),
                    ingestedTaskCount,
                    reanchoredLatest,
                    null
            );
        } finally {
            lock.unlock();
        }
    }

    private List<String> orderedTaskIds(List<DiagnosisReportRecord> legacyHistory) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (DiagnosisReportRecord report : legacyHistory) {
            if (report == null || !StringUtils.hasText(report.getDiagnosisTaskId())) {
                continue;
            }
            ordered.add(report.getDiagnosisTaskId().trim());
        }
        return new ArrayList<>(ordered);
    }

    private String normalizeScopeId(String brandScopeId) {
        return StringUtils.hasText(brandScopeId) ? brandScopeId.trim() : null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    public record HistorySyncResult(String brandScopeId,
                                    boolean skipped,
                                    String reason,
                                    int legacyReportCount,
                                    int existingRunCountBefore,
                                    int ingestedTaskCount,
                                    boolean reanchoredLatest) {

        private static HistorySyncResult skipped(String brandScopeId, String reason) {
            return new HistorySyncResult(brandScopeId, true, reason, 0, 0, 0, false);
        }

        private static HistorySyncResult completed(String brandScopeId,
                                                   int legacyReportCount,
                                                   int existingRunCountBefore,
                                                   int ingestedTaskCount,
                                                   boolean reanchoredLatest,
                                                   String reason) {
            return new HistorySyncResult(
                    brandScopeId,
                    false,
                    reason,
                    legacyReportCount,
                    existingRunCountBefore,
                    ingestedTaskCount,
                    reanchoredLatest
            );
        }
    }

    public record StartupSyncSummary(boolean skipped,
                                     String reason,
                                     int inspectedScopeCount,
                                     int repairedScopeCount,
                                     int ingestedRunCount,
                                     int reanchoredScopeCount) {

        private static StartupSyncSummary skipped(String reason) {
            return new StartupSyncSummary(true, reason, 0, 0, 0, 0);
        }

        private static StartupSyncSummary completed(int inspectedScopeCount,
                                                    int repairedScopeCount,
                                                    int ingestedRunCount,
                                                    int reanchoredScopeCount) {
            return new StartupSyncSummary(
                    false,
                    null,
                    inspectedScopeCount,
                    repairedScopeCount,
                    ingestedRunCount,
                    reanchoredScopeCount
            );
        }
    }
}
