package com.geoprobe.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset.ingestion.history-sync", name = "run-on-startup", havingValue = "true")
public class KnowledgeAssetHistorySyncStartupRunner implements ApplicationRunner {

    private final KnowledgeAssetHistorySyncService knowledgeAssetHistorySyncService;

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeAssetHistorySyncService.StartupSyncSummary summary =
                knowledgeAssetHistorySyncService.syncAllScopesOnStartup();
        if (summary.skipped()) {
            log.info("Knowledge asset startup history sync skipped. reason={}", summary.reason());
            return;
        }
        log.info("Knowledge asset startup history sync finished. inspectedScopes={}, repairedScopes={}, ingestedRuns={}, reanchoredScopes={}",
                summary.inspectedScopeCount(),
                summary.repairedScopeCount(),
                summary.ingestedRunCount(),
                summary.reanchoredScopeCount());
    }
}
