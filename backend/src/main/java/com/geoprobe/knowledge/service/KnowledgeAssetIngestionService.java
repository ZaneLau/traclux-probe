package com.geoprobe.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.knowledge.repository.KnowledgeAssetCompetitorRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunEventRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetSourceRepository;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetIngestionService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int WINDOW_LAST_3_RUNS = 3;
    private static final String WINDOW_KEY_LAST_3_RUNS = "last_3_runs";
    private static final String WINDOW_KEY_LAST_7_DAYS = "last_7_days";
    private static final String WINDOW_KEY_LAST_14_DAYS = "last_14_days";
    private static final String OWNERSHIP_KEY_OWNED = "OWNED";
    private static final String OWNERSHIP_KEY_EXTERNAL = "EXTERNAL";
    private static final String SOURCE_TYPE_OWNED = "OWNED";
    private static final String SOURCE_TYPE_CORPORATE = "CORPORATE";
    private static final String SOURCE_TYPE_EDITORIAL = "EDITORIAL";
    private static final String SOURCE_TYPE_UGC = "UGC";
    private static final String SOURCE_TYPE_REFERENCE = "REFERENCE";
    private static final String SOURCE_LEVEL_DOMAIN = "DOMAIN";
    private static final String FACT_SOURCE_CLUSTER_PROFILE = "REPORT_CLUSTER_PROFILE";
    private static final String FACT_SOURCE_CLUSTER_BREAKDOWN = "REPORT_CLUSTER_BREAKDOWN";
    private static final String FACT_SOURCE_NEGATIVE_SIGNAL = "REPORT_NEGATIVE_SIGNAL";
    private static final String FACT_SOURCE_PROBE_BUCKET = "PROBE_BUCKET_FALLBACK";
    private static final String METRIC_AGGREGATION_WEIGHTED_AVG = "WEIGHTED_AVG";
    private static final String METRIC_TOPIC = "topic";
    private static final String METRIC_SAMPLE_SIZE = "kpi.sample_size";
    private static final String METRIC_VISIBILITY_SCORE = "visibility.score";
    private static final String METRIC_VISIBILITY_MENTION_RATE = "visibility.mention_rate";
    private static final String METRIC_VISIBILITY_RECOMMEND_RATE = "visibility.recommend_rate";
    private static final String METRIC_VISIBILITY_TOP3_RATE = "visibility.top3_rate";
    private static final String METRIC_VISIBILITY_AVERAGE_POSITION = "visibility.average_position";
    private static final String METRIC_VISIBILITY_MENTION_COUNT = "visibility.mention_count";
    private static final String SIGNAL_TYPE_CLUSTER_PROFILE = "cluster_profile";
    private static final String SIGNAL_TYPE_CLUSTER_BREAKDOWN = "cluster_breakdown";
    private static final String SIGNAL_TYPE_NEGATIVE_SIGNAL = "negative_signal";
    private static final String SIGNAL_TYPE_PROBE_BUCKET = "probe_bucket";
    private static final int MAX_EVENT_ROWS = 5000;

    private final KnowledgeAssetProperties knowledgeAssetProperties;
    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;
    private final BrandScopeMapper brandScopeMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final DiagnosisEventRecordMapper diagnosisEventRecordMapper;
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    private final KnowledgeAssetRunRepository knowledgeAssetRunRepository;
    private final KnowledgeAssetRunEventRepository knowledgeAssetRunEventRepository;
    private final KnowledgeAssetCompetitorRepository knowledgeAssetCompetitorRepository;
    private final KnowledgeAssetEvidenceRepository knowledgeAssetEvidenceRepository;
    private final KnowledgeAssetSourceRepository knowledgeAssetSourceRepository;
    private final KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;
    private final KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;
    private final KnowledgeAssetScheduleStateMaterializationService knowledgeAssetScheduleStateMaterializationService;
    private final KnowledgeAssetProjectionMaterializationService knowledgeAssetProjectionMaterializationService;
    private final ObjectMapper objectMapper;

    public KnowledgeAssetIngestionResult ingestRunOnCompletion(String sourceTaskId) {
        if (!knowledgeAssetProperties.getIngestion().isOnRunCompletionEnabled()) {
            return KnowledgeAssetIngestionResult.skipped(sourceTaskId, "knowledge_asset_on_completion_disabled");
        }
        return ingestRun(sourceTaskId);
    }

    public KnowledgeAssetIngestionResult ingestRun(String sourceTaskId) {
        if (!knowledgeAssetProperties.getIngestion().isEnabled()) {
            return KnowledgeAssetIngestionResult.skipped(sourceTaskId, "knowledge_asset_ingestion_disabled");
        }
        if (!StringUtils.hasText(sourceTaskId)) {
            return KnowledgeAssetIngestionResult.skipped(null, "missing_source_task_id");
        }

        DiagnosisTask diagnosisTask = diagnosisTaskMapper.selectById(sourceTaskId.trim());
        if (diagnosisTask == null) {
            return KnowledgeAssetIngestionResult.skipped(sourceTaskId, "source_task_not_found");
        }

        DiagnosisReportRecord diagnosisReport = diagnosisReportRecordMapper.selectOne(
                new LambdaQueryWrapper<DiagnosisReportRecord>()
                        .eq(DiagnosisReportRecord::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByDesc(DiagnosisReportRecord::getGeneratedAt)
                        .orderByDesc(DiagnosisReportRecord::getCreatedAt)
                        .last("limit 1")
        );
        BrandScope brandScope = StringUtils.hasText(diagnosisTask.getBrandScopeId())
                ? brandScopeMapper.selectById(diagnosisTask.getBrandScopeId())
                : null;
        OffsetDateTime observedAt = firstNonNull(
                toOffsetDateTime(diagnosisReport == null ? null : diagnosisReport.getGeneratedAt()),
                toOffsetDateTime(diagnosisTask.getCompletedAt()),
                firstNonNull(
                        toOffsetDateTime(diagnosisTask.getUpdatedAt()),
                        toOffsetDateTime(diagnosisTask.getCreatedAt()),
                        null
                )
        );

        knowledgeAssetRunRepository.upsertRun(new KnowledgeAssetRunRepository.KnowledgeAssetRunUpsert(
                buildRunId(diagnosisTask.getId()),
                diagnosisTask.getOrganizationId(),
                diagnosisTask.getBrandScopeId(),
                diagnosisTask.getId(),
                diagnosisReport == null ? null : diagnosisReport.getId(),
                diagnosisTask.getEngineVersion(),
                firstText(
                        diagnosisReport == null ? null : diagnosisReport.getTargetModelProduct(),
                        diagnosisTask.getTargetModelProduct()
                ),
                diagnosisReport == null ? null : diagnosisReport.getSampleSize(),
                toOffsetDateTime(diagnosisReport == null ? null : diagnosisReport.getGeneratedAt()),
                toOffsetDateTime(diagnosisTask.getStartedAt()),
                toOffsetDateTime(diagnosisTask.getCompletedAt()),
                toJson(sanitizeTaskSnapshot(diagnosisTask)),
                toJson(diagnosisReport == null ? Map.of() : safeMap(diagnosisReport.getReportProjectionJson())),
                toJson(diagnosisReport == null ? Map.of() : safeMap(diagnosisReport.getSummaryJson()))
        ));
        int runEventCount = syncRunEvents(diagnosisTask);
        EvidenceEventSyncResult evidenceEventSyncResult = syncEvidenceEvents(diagnosisTask);
        int configuredCompetitorCount = syncConfiguredCompetitors(diagnosisTask, brandScope, observedAt);
        int snapshotCompetitorCount = syncCompetitorSnapshotFacts(diagnosisTask, diagnosisReport);
        syncCompetitorWindowFacts(diagnosisTask.getBrandScopeId(), observedAt);
        int ownedDomainCount = syncOwnedDomains(diagnosisTask, brandScope);
        SourceAssetSyncResult sourceAssetSyncResult =
                syncSourceAssets(diagnosisTask, diagnosisReport, brandScope, observedAt);
        ProblemAssetSyncResult problemAssetSyncResult =
                syncProblemAssets(diagnosisTask, diagnosisReport, observedAt);
        MetricAssetSyncResult metricAssetSyncResult =
                syncMetricAssets(diagnosisTask, diagnosisReport, observedAt);
        KnowledgeAssetScheduleStateMaterializationService.ScheduleStateMaterializationResult scheduleStateMaterializationResult =
                knowledgeAssetScheduleStateMaterializationService.materializeScope(diagnosisTask.getBrandScopeId());
        KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult projectionMaterializationResult =
                materializeProjectionBestEffort(diagnosisTask.getBrandScopeId(), diagnosisTask.getId());

        LinkedHashMap<String, Object> checkpointPayload = new LinkedHashMap<>();
        checkpointPayload.put("stage", "run_sync");
        checkpointPayload.put("reportPresent", diagnosisReport != null);
        checkpointPayload.put("engineVersion", defaultIfBlank(diagnosisTask.getEngineVersion(), "unknown"));
        checkpointPayload.put("runEventCount", runEventCount);
        checkpointPayload.put("probeEventCount", evidenceEventSyncResult.probeCount());
        checkpointPayload.put("attemptEventCount", evidenceEventSyncResult.attemptCount());
        checkpointPayload.put("brandMentionEventCount", evidenceEventSyncResult.brandMentionCount());
        checkpointPayload.put("configuredCompetitorCount", configuredCompetitorCount);
        checkpointPayload.put("snapshotCompetitorCount", snapshotCompetitorCount);
        checkpointPayload.put("ownedDomainCount", ownedDomainCount);
        checkpointPayload.put("sourceAssetCount", sourceAssetSyncResult.sourceAssetCount());
        checkpointPayload.put("citationCount", sourceAssetSyncResult.citationCount());
        checkpointPayload.put("sourceSnapshotDomainCount", sourceAssetSyncResult.snapshotDomainCount());
        checkpointPayload.put("topicCatalogCount", problemAssetSyncResult.topicCatalogCount());
        checkpointPayload.put("promptCatalogCount", problemAssetSyncResult.promptCatalogCount());
        checkpointPayload.put("problemSignalCount", problemAssetSyncResult.signalCount());
        checkpointPayload.put("problemSnapshotFactCount", problemAssetSyncResult.snapshotFactCount());
        checkpointPayload.put("metricRegistryCount", metricAssetSyncResult.registryCount());
        checkpointPayload.put("metricFactCount", metricAssetSyncResult.metricFactCount());
        checkpointPayload.put("metricWindowFactCount", metricAssetSyncResult.windowFactCount());
        checkpointPayload.put("scheduleStateSkipped", scheduleStateMaterializationResult.skipped());
        checkpointPayload.put("scheduleStateReason", scheduleStateMaterializationResult.reason());
        checkpointPayload.put("scheduleTopicStateCount", scheduleStateMaterializationResult.topicStateCount());
        checkpointPayload.put("projectionMaterializationSkipped", projectionMaterializationResult.skipped());
        checkpointPayload.put("projectionMaterializationReason", projectionMaterializationResult.reason());
        checkpointPayload.put("projectionMaterializedDomains", projectionMaterializationResult.materializedDomains());

        knowledgeAssetRunRepository.upsertCheckpoint(new KnowledgeAssetRunRepository.KnowledgeAssetCheckpointUpsert(
                buildCheckpointId(diagnosisTask.getId()),
                diagnosisTask.getBrandScopeId(),
                diagnosisTask.getId(),
                diagnosisReport == null ? null : diagnosisReport.getId(),
                firstNonNull(
                        toOffsetDateTime(diagnosisReport == null ? null : diagnosisReport.getGeneratedAt()),
                        toOffsetDateTime(diagnosisTask.getUpdatedAt()),
                        toOffsetDateTime(diagnosisTask.getCreatedAt())
                ),
                "RUN_SYNCED",
                toJson(checkpointPayload),
                null,
                null
        ));

        log.info("Knowledge asset run synced. taskId={}, reportId={}, scopeId={}",
                diagnosisTask.getId(),
                diagnosisReport == null ? null : diagnosisReport.getId(),
                diagnosisTask.getBrandScopeId());

        return new KnowledgeAssetIngestionResult(
                diagnosisTask.getId(),
                diagnosisTask.getBrandScopeId(),
                diagnosisReport == null ? null : diagnosisReport.getId(),
                true,
                false,
                null
        );
    }

    private KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult materializeProjectionBestEffort(
            String brandScopeId,
            String sourceTaskId) {
        try {
            return knowledgeAssetProjectionMaterializationService.materializeScope(brandScopeId);
        } catch (Exception exception) {
            log.warn("Knowledge asset projection materialization failed. scopeId={}, taskId={}, reason={}",
                    brandScopeId,
                    sourceTaskId,
                    exception.getMessage(),
                    exception);
            return KnowledgeAssetProjectionMaterializationService.ProjectionMaterializationResult.skipped(
                    brandScopeId,
                    "projection_materialization_failed"
            );
        }
    }

    private EvidenceEventSyncResult syncEvidenceEvents(DiagnosisTask diagnosisTask) {
        String runId = buildRunId(diagnosisTask.getId());
        List<DiagnosisProbeRecord> probes = loadDiagnosisProbes(diagnosisTask);
        List<BrandSpacePrompt> prompts = loadBrandSpacePrompts(diagnosisTask.getBrandScopeId());
        Map<Integer, BrandSpacePrompt> promptBySeqNo = indexPromptsBySeqNo(prompts);
        Map<String, BrandSpacePrompt> promptById = indexPromptsById(prompts);
        List<KnowledgeAssetEvidenceRepository.ProbeUpsert> probeUpserts = new ArrayList<>();
        for (DiagnosisProbeRecord probe : probes) {
            ProbeCatalogBinding binding = resolveProbeCatalogBinding(diagnosisTask.getBrandScopeId(), probe, promptBySeqNo, promptById);
            probeUpserts.add(new KnowledgeAssetEvidenceRepository.ProbeUpsert(
                    buildProbeEventId(probe.getId()),
                    normalizeText(probe.getId()),
                    normalizeText(probe.getProbeRef()),
                    probe.getSeqNo(),
                    binding.topicCatalogId(),
                    binding.promptCatalogId(),
                    binding.problemSpaceBucket(),
                    normalizeText(probe.getRenderText()),
                    toJson(buildProbePayload(probe, binding))
            ));
        }
        knowledgeAssetEvidenceRepository.replaceProbes(runId, probeUpserts);

        List<ProbeDispatchAttempt> attempts = loadDispatchAttempts(diagnosisTask);
        List<KnowledgeAssetEvidenceRepository.AttemptUpsert> attemptUpserts = new ArrayList<>();
        for (ProbeDispatchAttempt attempt : attempts) {
            attemptUpserts.add(new KnowledgeAssetEvidenceRepository.AttemptUpsert(
                    buildAttemptEventId(attempt.getId()),
                    buildProbeEventId(attempt.getProbeId()),
                    normalizeText(attempt.getId()),
                    normalizeText(attempt.getProvider()),
                    normalizeText(attempt.getModelId()),
                    attempt.getStatusCode() == null ? null : String.valueOf(attempt.getStatusCode()),
                    Boolean.TRUE.equals(attempt.getSelectedFinal()),
                    normalizeText(attempt.getRawResponseText()),
                    responsePreview(attempt.getRawResponseText(), attempt.getResponseMetadataJson()),
                    toJson(attempt.getCitationsJson() == null ? List.of() : attempt.getCitationsJson()),
                    toJson(attempt.getResponseMetadataJson() == null ? Map.of() : attempt.getResponseMetadataJson())
            ));
        }
        knowledgeAssetEvidenceRepository.replaceAttempts(runId, attemptUpserts);

        List<ProbeBrandMentionRecord> brandMentions = loadBrandMentions(diagnosisTask);
        List<KnowledgeAssetEvidenceRepository.BrandMentionUpsert> mentionUpserts = new ArrayList<>();
        for (ProbeBrandMentionRecord brandMention : brandMentions) {
            String brandName = normalizeText(brandMention.getBrandName());
            String brandKey = firstText(normalizeBrandKey(brandName), "unknown");
            mentionUpserts.add(new KnowledgeAssetEvidenceRepository.BrandMentionUpsert(
                    buildBrandMentionEventId(brandMention.getId()),
                    buildProbeEventId(brandMention.getProbeId()),
                    buildAttemptEventId(brandMention.getDispatchAttemptId()),
                    normalizeText(brandMention.getId()),
                    brandKey,
                    defaultIfBlank(brandName, "unknown"),
                    brandMention.getBrandRoleCode() == null ? "UNKNOWN" : String.valueOf(brandMention.getBrandRoleCode()),
                    Boolean.TRUE.equals(brandMention.getMentionedFlag()),
                    Boolean.TRUE.equals(brandMention.getRecommendedFlag()),
                    brandMention.getSentimentCode() == null ? null : String.valueOf(brandMention.getSentimentCode()),
                    brandMention.getPositionIndex(),
                    brandMention.getConfidenceScore(),
                    toJson(buildBrandMentionPayload(brandMention))
            ));
        }
        knowledgeAssetEvidenceRepository.replaceBrandMentions(runId, mentionUpserts);
        return new EvidenceEventSyncResult(probeUpserts.size(), attemptUpserts.size(), mentionUpserts.size());
    }

    private int syncRunEvents(DiagnosisTask diagnosisTask) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return 0;
        }
        List<DiagnosisEventRecord> events = loadDiagnosisEvents(diagnosisTask);
        if (events.isEmpty()) {
            knowledgeAssetRunEventRepository.replaceRunEvents(
                    buildRunId(diagnosisTask.getId()),
                    diagnosisTask.getBrandScopeId(),
                    diagnosisTask.getId(),
                    List.of()
            );
            return 0;
        }
        List<KnowledgeAssetRunEventRepository.RunEventUpsert> runEvents = new ArrayList<>();
        for (DiagnosisEventRecord event : events) {
            if (event == null || event.getSeq() == null || !StringUtils.hasText(event.getEventType())) {
                continue;
            }
            runEvents.add(new KnowledgeAssetRunEventRepository.RunEventUpsert(
                    buildRunEventId(diagnosisTask.getId(), event.getSeq()),
                    event.getSeq(),
                    event.getEventType().trim(),
                    Boolean.TRUE.equals(event.getProvisional()),
                    toJson(safeMap(event.getPayloadJson())),
                    firstNonNull(event.getCreatedAt(), diagnosisTask.getUpdatedAt(), diagnosisTask.getCreatedAt())
            ));
        }
        knowledgeAssetRunEventRepository.replaceRunEvents(
                buildRunId(diagnosisTask.getId()),
                diagnosisTask.getBrandScopeId(),
                diagnosisTask.getId(),
                runEvents
        );
        return runEvents.size();
    }

    private int syncConfiguredCompetitors(DiagnosisTask diagnosisTask,
                                          BrandScope brandScope,
                                          OffsetDateTime observedAt) {
        if (brandScope == null || !StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return 0;
        }
        List<String> configuredCompetitors = readConfiguredCompetitors(brandScope);
        List<String> activeCompetitorKeys = new ArrayList<>();
        for (String competitorName : configuredCompetitors) {
            String competitorKey = normalizeBrandKey(competitorName);
            if (!StringUtils.hasText(competitorKey)) {
                continue;
            }
            activeCompetitorKeys.add(competitorKey);
            knowledgeAssetCompetitorRepository.upsertConfiguredCompetitor(
                    new KnowledgeAssetCompetitorRepository.ConfiguredCompetitorUpsert(
                            buildConfiguredCompetitorId(diagnosisTask.getBrandScopeId(), competitorKey),
                            diagnosisTask.getOrganizationId(),
                            diagnosisTask.getBrandScopeId(),
                            competitorKey,
                            competitorName,
                            observedAt,
                            observedAt,
                            toJson(Map.of(
                                    "sourceTaskId", diagnosisTask.getId(),
                                    "configGeneration", brandScope.getConfigGeneration(),
                                    "source", "brand_scope_profile"
                            ))
                    )
            );
        }
        knowledgeAssetCompetitorRepository.deactivateMissingConfiguredCompetitors(
                diagnosisTask.getBrandScopeId(),
                activeCompetitorKeys
        );
        return activeCompetitorKeys.size();
    }

    private int syncCompetitorSnapshotFacts(DiagnosisTask diagnosisTask,
                                            DiagnosisReportRecord diagnosisReport) {
        String runId = buildRunId(diagnosisTask.getId());
        List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert> snapshotFacts =
                buildCompetitorSnapshotFacts(diagnosisTask, runId, diagnosisReport);
        knowledgeAssetCompetitorRepository.replaceSnapshotFacts(
                runId,
                diagnosisTask.getBrandScopeId(),
                snapshotFacts
        );
        return snapshotFacts.size();
    }

    private int syncOwnedDomains(DiagnosisTask diagnosisTask, BrandScope brandScope) {
        if (!StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return 0;
        }
        List<String> ownedDomains = readOwnedDomains(diagnosisTask, brandScope);
        List<String> activeOwnedDomains = new ArrayList<>();
        for (String ownedDomain : ownedDomains) {
            activeOwnedDomains.add(ownedDomain);
            knowledgeAssetSourceRepository.upsertOwnedDomain(new KnowledgeAssetSourceRepository.OwnedDomainUpsert(
                    buildOwnedDomainId(diagnosisTask.getBrandScopeId(), ownedDomain),
                    diagnosisTask.getOrganizationId(),
                    diagnosisTask.getBrandScopeId(),
                    ownedDomain,
                    "OFFICIAL_DOMAIN",
                    toJson(Map.of(
                            "sourceTaskId", diagnosisTask.getId(),
                            "primaryDomain", defaultIfBlank(brandScope == null ? null : brandScope.getPrimaryDomain(), ownedDomain),
                            "officialWebsiteSnapshot", defaultIfBlank(diagnosisTask.getOfficialWebsiteSnapshot(), ownedDomain)
                    ))
            ));
        }
        knowledgeAssetSourceRepository.deactivateMissingOwnedDomains(
                diagnosisTask.getBrandScopeId(),
                activeOwnedDomains
        );
        return activeOwnedDomains.size();
    }

    private SourceAssetSyncResult syncSourceAssets(DiagnosisTask diagnosisTask,
                                                   DiagnosisReportRecord diagnosisReport,
                                                   BrandScope brandScope,
                                                   OffsetDateTime observedAt) {
        String runId = buildRunId(diagnosisTask.getId());
        Set<String> ownedDomains = new LinkedHashSet<>(readOwnedDomains(diagnosisTask, brandScope));
        List<KnowledgeAssetSourceRepository.CitationUpsert> citations = buildCitationUpserts(
                runId,
                diagnosisTask,
                diagnosisReport,
                ownedDomains
        );
        int sourceAssetCount = upsertSourceAssets(diagnosisTask, citations, observedAt);
        knowledgeAssetSourceRepository.replaceCitations(runId, citations);
        List<KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert> snapshotFacts =
                buildSourceSnapshotFacts(runId, diagnosisTask.getBrandScopeId(), citations);
        knowledgeAssetSourceRepository.replaceSnapshotFacts(runId, diagnosisTask.getBrandScopeId(), snapshotFacts);
        syncSourceWindowFacts(diagnosisTask.getBrandScopeId(), observedAt);
        return new SourceAssetSyncResult(sourceAssetCount, citations.size(), snapshotFacts.size());
    }

    private ProblemAssetSyncResult syncProblemAssets(DiagnosisTask diagnosisTask,
                                                     DiagnosisReportRecord diagnosisReport,
                                                     OffsetDateTime observedAt) {
        if (!StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return new ProblemAssetSyncResult(0, 0, 0, 0);
        }
        List<BrandSpaceTopic> topics = loadBrandSpaceTopics(diagnosisTask.getBrandScopeId());
        int topicCatalogCount = syncTopicCatalog(diagnosisTask, topics);
        List<BrandSpacePrompt> prompts = loadBrandSpacePrompts(diagnosisTask.getBrandScopeId());
        int promptCatalogCount = syncPromptCatalog(diagnosisTask, prompts);
        List<DiagnosisProbeRecord> probes = loadDiagnosisProbes(diagnosisTask);
        List<KnowledgeAssetProblemRepository.ProblemSignalUpsert> problemSignals =
                buildProblemSignals(buildRunId(diagnosisTask.getId()), diagnosisTask, diagnosisReport, prompts, probes);
        knowledgeAssetProblemRepository.replaceProblemSignals(buildRunId(diagnosisTask.getId()), problemSignals);
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> snapshotFacts =
                buildProblemSnapshotFacts(buildRunId(diagnosisTask.getId()), diagnosisTask, diagnosisReport, prompts, probes);
        knowledgeAssetProblemRepository.replaceSnapshotFacts(
                buildRunId(diagnosisTask.getId()),
                diagnosisTask.getBrandScopeId(),
                snapshotFacts
        );
        syncProblemWindowFacts(diagnosisTask.getBrandScopeId(), observedAt);
        return new ProblemAssetSyncResult(topicCatalogCount, promptCatalogCount, problemSignals.size(), snapshotFacts.size());
    }

    private MetricAssetSyncResult syncMetricAssets(DiagnosisTask diagnosisTask,
                                                   DiagnosisReportRecord diagnosisReport,
                                                   OffsetDateTime observedAt) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return new MetricAssetSyncResult(0, 0, 0);
        }
        List<KnowledgeAssetMetricRepository.MetricRegistryUpsert> registryItems = buildCoreMetricRegistry();
        knowledgeAssetMetricRepository.upsertMetricRegistry(registryItems);

        String runId = buildRunId(diagnosisTask.getId());
        List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts =
                buildMetricFacts(diagnosisTask, diagnosisReport, runId, observedAt);
        knowledgeAssetMetricRepository.replaceRunFacts(runId, metricFacts);
        int windowFactCount = syncMetricWindowFacts(diagnosisTask.getBrandScopeId(), observedAt);
        return new MetricAssetSyncResult(registryItems.size(), metricFacts.size(), windowFactCount);
    }

    private List<KnowledgeAssetMetricRepository.MetricRegistryUpsert> buildCoreMetricRegistry() {
        List<KnowledgeAssetMetricRepository.MetricRegistryUpsert> items = new ArrayList<>();
        items.add(metricRegistry("kpi.sample_size", "样本量", "kpi", "NUMBER", "count", "SUM", "NEUTRAL", "运行样本数量"));
        items.add(metricRegistry("kpi.observed_competitor_count", "本轮观察竞品数", "kpi", "NUMBER", "count", "LATEST", "NEUTRAL", "本轮快照中实际出现的竞品数量"));
        items.add(metricRegistry("kpi.citation_domain_count", "本轮引用域名数", "kpi", "NUMBER", "count", "LATEST", "NEUTRAL", "本轮快照中实际出现的引用域名数量"));
        items.add(metricRegistry("kpi.problem_signal_count", "本轮问题信号数", "kpi", "NUMBER", "count", "LATEST", "NEUTRAL", "本轮沉淀的问题信号数量"));
        items.add(metricRegistry(METRIC_VISIBILITY_SCORE, "可见度得分", "visibility", "NUMBER", "score", METRIC_AGGREGATION_WEIGHTED_AVG, "HIGHER_BETTER", "竞品、品牌或主题在回答中的可见度"));
        items.add(metricRegistry(METRIC_VISIBILITY_MENTION_RATE, "提及率", "visibility", "NUMBER", "percent", METRIC_AGGREGATION_WEIGHTED_AVG, "HIGHER_BETTER", "被提及的样本占比"));
        items.add(metricRegistry(METRIC_VISIBILITY_RECOMMEND_RATE, "推荐率", "visibility", "NUMBER", "percent", METRIC_AGGREGATION_WEIGHTED_AVG, "HIGHER_BETTER", "被推荐的样本占比"));
        items.add(metricRegistry(METRIC_VISIBILITY_TOP3_RATE, "Top3 率", "visibility", "NUMBER", "percent", METRIC_AGGREGATION_WEIGHTED_AVG, "HIGHER_BETTER", "进入前三推荐或提及位置的样本占比"));
        items.add(metricRegistry(METRIC_VISIBILITY_AVERAGE_POSITION, "平均位置", "visibility", "NUMBER", "rank", METRIC_AGGREGATION_WEIGHTED_AVG, "LOWER_BETTER", "平均出现或推荐位置"));
        items.add(metricRegistry(METRIC_VISIBILITY_MENTION_COUNT, "提及次数", "visibility", "NUMBER", "count", "SUM", "HIGHER_BETTER", "提及次数累计"));
        items.add(metricRegistry("citation.count", "引用次数", "citation", "NUMBER", "count", "SUM", "HIGHER_BETTER", "来源被引用次数"));
        items.add(metricRegistry("citation.share_rate", "引用占比", "citation", "NUMBER", "percent", "AVG", "NEUTRAL", "来源引用占比"));
        items.add(metricRegistry("platform.citation_count", "平台引用次数", "platform", "NUMBER", "count", "SUM", "NEUTRAL", "按来源类型/平台切面的引用次数"));
        items.add(metricRegistry("platform.citation_share_rate", "平台引用占比", "platform", "NUMBER", "percent", "AVG", "NEUTRAL", "按来源类型/平台切面的引用占比"));
        items.add(metricRegistry("model.attempt_count", "模型调用次数", "model", "NUMBER", "count", "SUM", "NEUTRAL", "按模型切面的尝试次数"));
        items.add(metricRegistry("model.final_attempt_count", "模型最终回答次数", "model", "NUMBER", "count", "SUM", "NEUTRAL", "按模型切面的最终回答次数"));
        items.add(metricRegistry("sentiment.mention_count", "情感提及次数", "sentiment", "NUMBER", "count", "SUM", "NEUTRAL", "按品牌、角色和情感切面的提及次数"));
        items.add(metricRegistry("problem.signal_score", "问题信号分", "problem", "NUMBER", "score", "AVG", "HIGHER_BETTER", "问题信号强度"));
        items.add(metricRegistry("problem.evidence_count", "问题证据数", "problem", "NUMBER", "count", "SUM", "HIGHER_BETTER", "问题相关证据数量"));
        items.add(metricRegistry("problem.probe_count", "问题探测数", "problem", "NUMBER", "count", "SUM", "HIGHER_BETTER", "问题相关探测数量"));
        items.add(metricRegistry("problem.attempt_count", "问题回答数", "problem", "NUMBER", "count", "SUM", "HIGHER_BETTER", "问题相关回答数量"));
        return items;
    }

    private KnowledgeAssetMetricRepository.MetricRegistryUpsert metricRegistry(String metricKey,
                                                                               String metricName,
                                                                               String metricGroup,
                                                                               String valueType,
                                                                               String unit,
                                                                               String aggregationMethod,
                                                                               String direction,
                                                                               String description) {
        return new KnowledgeAssetMetricRepository.MetricRegistryUpsert(
                "ka-metric-registry-" + normalizeStorageKey(metricKey),
                metricKey,
                metricName,
                metricGroup,
                valueType,
                unit,
                aggregationMethod,
                direction,
                description,
                toJson(metricMap(
                        "phase", "phase1.2",
                        "layer", "metric_and_analysis_slice",
                        "rootObject", false
                ))
        );
    }

    private List<KnowledgeAssetMetricRepository.MetricFactUpsert> buildMetricFacts(DiagnosisTask diagnosisTask,
                                                                                   DiagnosisReportRecord diagnosisReport,
                                                                                   String runId,
                                                                                   OffsetDateTime observedAt) {
        String brandScopeId = diagnosisTask.getBrandScopeId();
        List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts = new ArrayList<>();
        Map<String, Object> runDimensions = metricMap("slice", "run");
        Map<String, Object> runRefs = metricMap(
                "runId", runId,
                "sourceTaskId", diagnosisTask.getId(),
                "sourceReportId", diagnosisReport == null ? null : diagnosisReport.getId()
        );
        addMetricFact(metricFacts, brandScopeId, runId, "run", runId, METRIC_SAMPLE_SIZE,
                diagnosisReport == null || diagnosisReport.getSampleSize() == null
                        ? null
                        : diagnosisReport.getSampleSize().doubleValue(),
                null, "count", runDimensions, runRefs, Map.of(), observedAt);

        List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView> competitorFacts =
                defaultList(knowledgeAssetCompetitorRepository.listSnapshotFactsByRunIds(List.of(runId)));
        addMetricFact(metricFacts, brandScopeId, runId, "run", runId, "kpi.observed_competitor_count",
                (double) competitorFacts.size(), null, "count", runDimensions, runRefs, Map.of(), observedAt);
        for (KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView competitorFact : competitorFacts) {
            Map<String, Object> dimensions = metricMap("slice", "competitor", "competitorName", competitorFact.competitorName());
            Map<String, Object> sourceRefs = metricMap("runId", runId, "competitorKey", competitorFact.competitorKey());
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_SCORE, competitorFact.visibilityScore(), null, "score", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_MENTION_RATE, competitorFact.mentionRate(), null, "percent", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_RECOMMEND_RATE, competitorFact.recommendRate(), null, "percent", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_TOP3_RATE, competitorFact.top3Rate(), null, "percent", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_AVERAGE_POSITION, competitorFact.averagePosition(), null, "rank", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "competitor", competitorFact.competitorKey(),
                    METRIC_VISIBILITY_MENTION_COUNT, competitorFact.mentionCount() == null ? null : competitorFact.mentionCount().doubleValue(),
                    null, "count", dimensions, sourceRefs, Map.of(), observedAt);
        }

        List<KnowledgeAssetSourceRepository.SourceSnapshotFactView> sourceFacts =
                defaultList(knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of(runId)));
        addMetricFact(metricFacts, brandScopeId, runId, "run", runId, "kpi.citation_domain_count",
                (double) sourceFacts.size(), null, "count", runDimensions, runRefs, Map.of(), observedAt);
        Map<String, PlatformMetricAccumulator> platformMetrics = new LinkedHashMap<>();
        for (KnowledgeAssetSourceRepository.SourceSnapshotFactView sourceFact : sourceFacts) {
            String sourceKey = firstText(sourceFact.sourceKey(), buildSourceKey(sourceFact.domainName()));
            Map<String, Object> dimensions = metricMap(
                    "slice", "source",
                    "domainName", sourceFact.domainName(),
                    "sourceType", sourceFact.sourceType(),
                    "ownershipKey", sourceFact.ownershipKey()
            );
            Map<String, Object> sourceRefs = metricMap("runId", runId, "sourceKey", sourceKey, "domainName", sourceFact.domainName());
            addMetricFact(metricFacts, brandScopeId, runId, "source", sourceKey,
                    "citation.count", sourceFact.citationCount() == null ? null : sourceFact.citationCount().doubleValue(),
                    null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "source", sourceKey,
                    "citation.share_rate", sourceFact.shareRate(), null, "percent", dimensions, sourceRefs, Map.of(), observedAt);
            String platformKey = normalizeStorageKey(firstText(sourceFact.sourceType(), "unknown"));
            platformMetrics.computeIfAbsent(platformKey, ignored -> new PlatformMetricAccumulator(sourceFact.sourceType()))
                    .add(sourceFact.citationCount(), sourceFact.shareRate());
        }
        for (Map.Entry<String, PlatformMetricAccumulator> entry : platformMetrics.entrySet()) {
            PlatformMetricAccumulator accumulator = entry.getValue();
            Map<String, Object> dimensions = metricMap("slice", "platform", "sourceType", accumulator.sourceType);
            Map<String, Object> sourceRefs = metricMap("runId", runId, "source", "ka_source_snapshot_facts");
            addMetricFact(metricFacts, brandScopeId, runId, "platform", entry.getKey(),
                    "platform.citation_count", (double) accumulator.citationCount, null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "platform", entry.getKey(),
                    "platform.citation_share_rate", accumulator.averageShareRate(), null, "percent", dimensions, sourceRefs, Map.of(), observedAt);
        }

        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactView> problemFacts =
                defaultList(knowledgeAssetProblemRepository.listSnapshotFactsByRunIds(List.of(runId)));
        addMetricFact(metricFacts, brandScopeId, runId, "run", runId, "kpi.problem_signal_count",
                (double) problemFacts.size(), null, "count", runDimensions, runRefs, Map.of(), observedAt);
        for (KnowledgeAssetProblemRepository.ProblemSnapshotFactView problemFact : problemFacts) {
            String sourceObjectKey = buildProblemAggregateKey(
                    problemFact.problemSpaceBucket(),
                    problemFact.topicCatalogId(),
                    problemFact.clusterKey(),
                    problemFact.factSource()
            );
            Map<String, Object> dimensions = metricMap(
                    "slice", "problem",
                    "problemSpaceBucket", problemFact.problemSpaceBucket(),
                    "topicCatalogId", problemFact.topicCatalogId(),
                    "clusterKey", problemFact.clusterKey(),
                    "factSource", problemFact.factSource()
            );
            Map<String, Object> sourceRefs = metricMap("runId", runId, "source", "ka_problem_snapshot_facts");
            addMetricFact(metricFacts, brandScopeId, runId, "problem", sourceObjectKey,
                    "problem.signal_score", problemFact.signalScore(), null, "score", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "problem", sourceObjectKey,
                    "problem.evidence_count", problemFact.evidenceCount() == null ? null : problemFact.evidenceCount().doubleValue(),
                    null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "problem", sourceObjectKey,
                    "problem.probe_count", problemFact.probeCount() == null ? null : problemFact.probeCount().doubleValue(),
                    null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "problem", sourceObjectKey,
                    "problem.attempt_count", problemFact.attemptCount() == null ? null : problemFact.attemptCount().doubleValue(),
                    null, "count", dimensions, sourceRefs, Map.of(), observedAt);
        }

        addTopicMetricFacts(metricFacts, diagnosisTask, runId, brandScopeId, observedAt);
        addModelMetricFacts(metricFacts, diagnosisTask, runId, brandScopeId, observedAt);
        addSentimentMetricFacts(metricFacts, diagnosisTask, runId, brandScopeId, observedAt);
        return metricFacts;
    }

    private void addTopicMetricFacts(List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts,
                                     DiagnosisTask diagnosisTask,
                                     String runId,
                                     String brandScopeId,
                                     OffsetDateTime observedAt) {
        if (diagnosisTask == null || !StringUtils.hasText(runId) || !StringUtils.hasText(brandScopeId)) {
            return;
        }
        List<KnowledgeAssetEvidenceRepository.ProbeView> probes =
                defaultList(knowledgeAssetEvidenceRepository.listProbesByRunId(runId));
        if (probes.isEmpty()) {
            return;
        }
        List<KnowledgeAssetEvidenceRepository.AttemptView> attempts =
                defaultList(knowledgeAssetEvidenceRepository.listAttemptsByRunId(runId));
        List<KnowledgeAssetEvidenceRepository.BrandMentionView> brandMentions =
                defaultList(knowledgeAssetEvidenceRepository.listBrandMentionsByRunId(runId));
        Map<String, KnowledgeAssetProblemRepository.TopicCatalogView> topicByCatalogId = knowledgeAssetProblemRepository
                .listActiveTopics(brandScopeId).stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.topicCatalogId()))
                .collect(java.util.stream.Collectors.toMap(
                        KnowledgeAssetProblemRepository.TopicCatalogView::topicCatalogId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> finalAttemptIds = attempts.stream()
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.selectedFinal()))
                .map(KnowledgeAssetEvidenceRepository.AttemptView::id)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, Set<String>> finalProbeIdsByTopic = new LinkedHashMap<>();
        Map<String, Set<String>> probeIdsByTopic = new LinkedHashMap<>();
        for (KnowledgeAssetEvidenceRepository.ProbeView probe : probes) {
            if (probe == null || !StringUtils.hasText(probe.topicCatalogId()) || !StringUtils.hasText(probe.id())) {
                continue;
            }
            probeIdsByTopic.computeIfAbsent(probe.topicCatalogId(), ignored -> new LinkedHashSet<>()).add(probe.id());
        }
        if (!finalAttemptIds.isEmpty()) {
            for (KnowledgeAssetEvidenceRepository.AttemptView attempt : attempts) {
                if (attempt == null
                        || !Boolean.TRUE.equals(attempt.selectedFinal())
                        || !StringUtils.hasText(attempt.probeId())) {
                    continue;
                }
                KnowledgeAssetEvidenceRepository.ProbeView probe = probes.stream()
                        .filter(item -> item != null && Objects.equals(item.id(), attempt.probeId()))
                        .findFirst()
                        .orElse(null);
                if (probe == null || !StringUtils.hasText(probe.topicCatalogId())) {
                    continue;
                }
                finalProbeIdsByTopic.computeIfAbsent(probe.topicCatalogId(), ignored -> new LinkedHashSet<>()).add(probe.id());
            }
        }

        String targetBrandKey = firstText(normalizeBrandKey(diagnosisTask.getBrandNameSnapshot()), "unknown");
        Map<String, TopicMetricAccumulator> accumulatorByTopic = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : probeIdsByTopic.entrySet()) {
            String topicCatalogId = entry.getKey();
            Set<String> scopedProbeIds = finalProbeIdsByTopic.getOrDefault(topicCatalogId, entry.getValue());
            TopicMetricAccumulator accumulator = accumulatorByTopic.computeIfAbsent(
                    topicCatalogId,
                    ignored -> {
                        KnowledgeAssetProblemRepository.TopicCatalogView topic = topicByCatalogId.get(topicCatalogId);
                        return new TopicMetricAccumulator(
                                topicCatalogId,
                                topic == null ? null : topic.topicName(),
                                topic == null ? null : topic.problemSpaceBucket(),
                                topic == null ? null : topic.sourceTopicId()
                        );
                    }
            );
            accumulator.addSampleProbes(scopedProbeIds);
        }
        for (KnowledgeAssetEvidenceRepository.BrandMentionView brandMention : brandMentions) {
            if (brandMention == null
                    || !StringUtils.hasText(brandMention.probeId())
                    || !Objects.equals(targetBrandKey, normalizeBrandKey(brandMention.brandName()))) {
                continue;
            }
            if (!finalAttemptIds.isEmpty()
                    && StringUtils.hasText(brandMention.attemptId())
                    && !finalAttemptIds.contains(brandMention.attemptId())) {
                continue;
            }
            KnowledgeAssetEvidenceRepository.ProbeView probe = probes.stream()
                    .filter(item -> item != null && Objects.equals(item.id(), brandMention.probeId()))
                    .findFirst()
                    .orElse(null);
            if (probe == null || !StringUtils.hasText(probe.topicCatalogId())) {
                continue;
            }
            TopicMetricAccumulator accumulator = accumulatorByTopic.get(probe.topicCatalogId());
            if (accumulator == null) {
                continue;
            }
            accumulator.addMention(brandMention);
        }
        for (TopicMetricAccumulator accumulator : accumulatorByTopic.values()) {
            TopicMetricSnapshot snapshot = accumulator.snapshot();
            if (snapshot.sampleSize() <= 0) {
                continue;
            }
            Map<String, Object> dimensions = metricMap(
                    "slice", METRIC_TOPIC,
                    "topicCatalogId", accumulator.topicCatalogId,
                    "topicName", accumulator.topicName,
                    "problemSpaceBucket", accumulator.problemSpaceBucket,
                    "sourceTopicId", accumulator.sourceTopicId
            );
            Map<String, Object> sourceRefs = metricMap(
                    "runId", runId,
                    "source", "ka_probes+ka_brand_mentions",
                    "topicCatalogId", accumulator.topicCatalogId
            );
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_SAMPLE_SIZE, (double) snapshot.sampleSize(), null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_SCORE, snapshot.visibilityScore(), null, "score", dimensions, sourceRefs,
                    metricWeightPayload(snapshot.sampleSize()), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_MENTION_RATE, snapshot.mentionRate(), null, "percent", dimensions, sourceRefs,
                    metricWeightPayload(snapshot.sampleSize()), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_RECOMMEND_RATE, snapshot.recommendRate(), null, "percent", dimensions, sourceRefs,
                    metricWeightPayload(snapshot.sampleSize()), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_TOP3_RATE, snapshot.top3Rate(), null, "percent", dimensions, sourceRefs,
                    metricWeightPayload(snapshot.sampleSize()), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_AVERAGE_POSITION, snapshot.averagePosition(), null, "rank", dimensions, sourceRefs,
                    metricWeightPayload(snapshot.positionedCount()), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, METRIC_TOPIC, accumulator.topicCatalogId,
                    METRIC_VISIBILITY_MENTION_COUNT, (double) snapshot.mentionCount(), null, "count", dimensions, sourceRefs, Map.of(), observedAt);
        }
    }

    private Map<String, Object> metricWeightPayload(Number weightValue) {
        if (weightValue == null) {
            return Map.of();
        }
        double numericWeight = weightValue.doubleValue();
        if (numericWeight <= 0.0d) {
            return Map.of();
        }
        return metricMap("weightValue", numericWeight);
    }

    private void addModelMetricFacts(List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts,
                                     DiagnosisTask diagnosisTask,
                                     String runId,
                                     String brandScopeId,
                                     OffsetDateTime observedAt) {
        Map<String, ModelMetricAccumulator> accumulatorByModel = new LinkedHashMap<>();
        for (ProbeDispatchAttempt attempt : loadDispatchAttempts(diagnosisTask)) {
            String modelKey = normalizeStorageKey(firstText(attempt.getModelId(), "unknown"));
            String providerKey = normalizeStorageKey(firstText(attempt.getProvider(), "unknown"));
            String aggregateKey = providerKey + ":" + modelKey;
            accumulatorByModel.computeIfAbsent(
                    aggregateKey,
                    ignored -> new ModelMetricAccumulator(attempt.getProvider(), attempt.getModelId())
            ).add(attempt);
        }
        for (Map.Entry<String, ModelMetricAccumulator> entry : accumulatorByModel.entrySet()) {
            ModelMetricAccumulator accumulator = entry.getValue();
            Map<String, Object> dimensions = metricMap(
                    "slice", "model",
                    "provider", accumulator.provider,
                    "modelId", accumulator.modelId
            );
            Map<String, Object> sourceRefs = metricMap("runId", runId, "source", "probe_dispatch_attempts");
            addMetricFact(metricFacts, brandScopeId, runId, "model", entry.getKey(),
                    "model.attempt_count", (double) accumulator.attemptCount, null, "count", dimensions, sourceRefs, Map.of(), observedAt);
            addMetricFact(metricFacts, brandScopeId, runId, "model", entry.getKey(),
                    "model.final_attempt_count", (double) accumulator.finalAttemptCount, null, "count", dimensions, sourceRefs, Map.of(), observedAt);
        }
    }

    private void addSentimentMetricFacts(List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts,
                                         DiagnosisTask diagnosisTask,
                                         String runId,
                                         String brandScopeId,
                                         OffsetDateTime observedAt) {
        Map<String, SentimentMetricAccumulator> accumulatorBySentiment = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : loadBrandMentions(diagnosisTask)) {
            String brandName = normalizeText(mention.getBrandName());
            String brandKey = firstText(normalizeBrandKey(brandName), "unknown");
            String sentimentKey = normalizeStorageKey(String.valueOf(mention.getSentimentCode()));
            String roleKey = normalizeStorageKey(String.valueOf(mention.getBrandRoleCode()));
            String aggregateKey = brandKey + ":" + roleKey + ":" + sentimentKey;
            accumulatorBySentiment.computeIfAbsent(
                    aggregateKey,
                    ignored -> new SentimentMetricAccumulator(brandKey, brandName, mention.getBrandRoleCode(), mention.getSentimentCode())
            ).add(mention);
        }
        for (Map.Entry<String, SentimentMetricAccumulator> entry : accumulatorBySentiment.entrySet()) {
            SentimentMetricAccumulator accumulator = entry.getValue();
            Map<String, Object> dimensions = metricMap(
                    "slice", "sentiment",
                    "brandName", accumulator.brandName,
                    "brandRoleCode", accumulator.brandRoleCode,
                    "sentimentCode", accumulator.sentimentCode
            );
            Map<String, Object> sourceRefs = metricMap("runId", runId, "source", "probe_brand_mentions");
            addMetricFact(metricFacts, brandScopeId, runId, "brand", accumulator.brandKey,
                    "sentiment.mention_count", (double) accumulator.mentionCount, null, "count", dimensions, sourceRefs, Map.of(), observedAt);
        }
    }

    private void addMetricFact(List<KnowledgeAssetMetricRepository.MetricFactUpsert> metricFacts,
                               String brandScopeId,
                               String runId,
                               String sourceObjectType,
                               String sourceObjectKey,
                               String metricKey,
                               Double metricValue,
                               String metricText,
                               String metricUnit,
                               Map<String, Object> dimensions,
                               Map<String, Object> sourceRefs,
                               Map<String, Object> factPayload,
                               OffsetDateTime observedAt) {
        if (!StringUtils.hasText(brandScopeId)
                || !StringUtils.hasText(runId)
                || !StringUtils.hasText(sourceObjectType)
                || !StringUtils.hasText(sourceObjectKey)
                || !StringUtils.hasText(metricKey)
                || (metricValue == null && !StringUtils.hasText(metricText))) {
            return;
        }
        String dimensionKey = buildDimensionKey(dimensions);
        metricFacts.add(new KnowledgeAssetMetricRepository.MetricFactUpsert(
                buildMetricFactId(runId, sourceObjectType, sourceObjectKey, metricKey, dimensionKey),
                brandScopeId,
                runId,
                sourceObjectType,
                sourceObjectKey,
                metricKey,
                dimensionKey,
                metricValue,
                metricText,
                metricUnit,
                toJson(dimensions),
                toJson(sourceRefs),
                toJson(factPayload),
                observedAt
        ));
    }

    private int syncMetricWindowFacts(String brandScopeId, OffsetDateTime observedAt) {
        if (!StringUtils.hasText(brandScopeId) || observedAt == null) {
            return 0;
        }
        int updated = 0;
        List<KnowledgeAssetCompetitorRepository.WindowRunHead> recentRuns = knowledgeAssetCompetitorRepository
                .listRecentRunsByScope(brandScopeId, observedAt, WINDOW_LAST_3_RUNS);
        OffsetDateTime runWindowStartAt = recentRuns.isEmpty()
                ? observedAt
                : recentRuns.get(recentRuns.size() - 1).observedAt();
        updated += knowledgeAssetMetricRepository.refreshWindowFacts(
                brandScopeId,
                WINDOW_KEY_LAST_3_RUNS,
                runWindowStartAt,
                observedAt
        );
        updated += knowledgeAssetMetricRepository.refreshWindowFacts(
                brandScopeId,
                WINDOW_KEY_LAST_7_DAYS,
                observedAt.minusDays(7),
                observedAt
        );
        updated += knowledgeAssetMetricRepository.refreshWindowFacts(
                brandScopeId,
                WINDOW_KEY_LAST_14_DAYS,
                observedAt.minusDays(14),
                observedAt
        );
        return updated;
    }

    private void syncCompetitorWindowFacts(String brandScopeId, OffsetDateTime observedAt) {
        if (!StringUtils.hasText(brandScopeId) || observedAt == null) {
            return;
        }
        List<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView> configuredCompetitors =
                defaultList(knowledgeAssetCompetitorRepository.listActiveConfiguredCompetitors(brandScopeId));
        recomputeRunBasedWindow(brandScopeId, observedAt, configuredCompetitors);
        recomputeTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(7), WINDOW_KEY_LAST_7_DAYS, configuredCompetitors);
        recomputeTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(14), WINDOW_KEY_LAST_14_DAYS, configuredCompetitors);
    }

    private void recomputeRunBasedWindow(String brandScopeId,
                                         OffsetDateTime observedAt,
                                         List<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView> configuredCompetitors) {
        List<KnowledgeAssetCompetitorRepository.WindowRunHead> runs = knowledgeAssetCompetitorRepository
                .listRecentRunsByScope(brandScopeId, observedAt, WINDOW_LAST_3_RUNS);
        OffsetDateTime windowStartAt = runs.isEmpty()
                ? observedAt
                : runs.get(runs.size() - 1).observedAt();
        replaceCompetitorWindowFacts(
                brandScopeId,
                WINDOW_KEY_LAST_3_RUNS,
                windowStartAt,
                observedAt,
                runs,
                configuredCompetitors
        );
    }

    private void recomputeTimeBasedWindow(String brandScopeId,
                                          OffsetDateTime observedAt,
                                          OffsetDateTime windowStartAt,
                                          String windowKey,
                                          List<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView> configuredCompetitors) {
        List<KnowledgeAssetCompetitorRepository.WindowRunHead> runs = knowledgeAssetCompetitorRepository
                .listRunsByScopeWithinWindow(brandScopeId, windowStartAt, observedAt);
        replaceCompetitorWindowFacts(
                brandScopeId,
                windowKey,
                windowStartAt,
                observedAt,
                runs,
                configuredCompetitors
        );
    }

    private void replaceCompetitorWindowFacts(String brandScopeId,
                                              String windowKey,
                                              OffsetDateTime windowStartAt,
                                              OffsetDateTime windowEndAt,
                                              List<KnowledgeAssetCompetitorRepository.WindowRunHead> runs,
                                              List<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView> configuredCompetitors) {
        Map<String, CompetitorWindowAccumulator> accumulatorByCompetitor = new LinkedHashMap<>();
        for (KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView configuredCompetitor : configuredCompetitors) {
            accumulatorByCompetitor.computeIfAbsent(
                    configuredCompetitor.competitorKey(),
                    ignored -> new CompetitorWindowAccumulator(
                            configuredCompetitor.competitorKey(),
                            configuredCompetitor.competitorName(),
                            true
                    )
            );
        }
        List<String> runIds = runs.stream()
                .map(KnowledgeAssetCompetitorRepository.WindowRunHead::runId)
                .toList();
        Map<String, Integer> sampleSizeByRunId = new LinkedHashMap<>();
        for (KnowledgeAssetCompetitorRepository.WindowRunHead run : runs) {
            sampleSizeByRunId.put(run.runId(), run.sampleSize());
        }
        for (KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView snapshotFact :
                defaultList(knowledgeAssetCompetitorRepository.listSnapshotFactsByRunIds(runIds))) {
            CompetitorWindowAccumulator accumulator = accumulatorByCompetitor.computeIfAbsent(
                    snapshotFact.competitorKey(),
                    ignored -> new CompetitorWindowAccumulator(
                            snapshotFact.competitorKey(),
                            snapshotFact.competitorName(),
                            false
                    )
            );
            accumulator.updateName(snapshotFact.competitorName());
            accumulator.addSnapshot(snapshotFact, sampleSizeByRunId.get(snapshotFact.runId()));
        }
        List<KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert> windowFacts = accumulatorByCompetitor.values().stream()
                .map(accumulator -> accumulator.toUpsert(brandScopeId, windowKey, runIds.size()))
                .toList();
        knowledgeAssetCompetitorRepository.replaceWindowFacts(
                brandScopeId,
                windowKey,
                windowStartAt,
                windowEndAt,
                windowFacts
        );
    }

    private void syncSourceWindowFacts(String brandScopeId, OffsetDateTime observedAt) {
        if (!StringUtils.hasText(brandScopeId) || observedAt == null) {
            return;
        }
        recomputeSourceRunBasedWindow(brandScopeId, observedAt);
        recomputeSourceTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(7), WINDOW_KEY_LAST_7_DAYS);
        recomputeSourceTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(14), WINDOW_KEY_LAST_14_DAYS);
    }

    private void recomputeSourceRunBasedWindow(String brandScopeId, OffsetDateTime observedAt) {
        List<KnowledgeAssetSourceRepository.WindowRunHead> runs = knowledgeAssetSourceRepository
                .listRecentRunsByScope(brandScopeId, observedAt, WINDOW_LAST_3_RUNS);
        OffsetDateTime windowStartAt = runs.isEmpty()
                ? observedAt
                : runs.get(runs.size() - 1).observedAt();
        replaceSourceWindowFacts(brandScopeId, WINDOW_KEY_LAST_3_RUNS, windowStartAt, observedAt, runs);
    }

    private void recomputeSourceTimeBasedWindow(String brandScopeId,
                                                OffsetDateTime observedAt,
                                                OffsetDateTime windowStartAt,
                                                String windowKey) {
        List<KnowledgeAssetSourceRepository.WindowRunHead> runs = knowledgeAssetSourceRepository
                .listRunsByScopeWithinWindow(brandScopeId, windowStartAt, observedAt);
        replaceSourceWindowFacts(brandScopeId, windowKey, windowStartAt, observedAt, runs);
    }

    private void replaceSourceWindowFacts(String brandScopeId,
                                          String windowKey,
                                          OffsetDateTime windowStartAt,
                                          OffsetDateTime windowEndAt,
                                          List<KnowledgeAssetSourceRepository.WindowRunHead> runs) {
        List<String> runIds = runs.stream()
                .map(KnowledgeAssetSourceRepository.WindowRunHead::runId)
                .toList();
        Map<String, SourceAggregationAccumulator> accumulatorByDomain = new LinkedHashMap<>();
        for (KnowledgeAssetSourceRepository.CitationView citation :
                defaultList(knowledgeAssetSourceRepository.listCitationsByRunIds(runIds))) {
            String domainName = normalizeDomain(citation.domainName());
            if (!StringUtils.hasText(domainName)) {
                continue;
            }
            SourceAggregationAccumulator accumulator = accumulatorByDomain.computeIfAbsent(
                    domainName,
                    ignored -> new SourceAggregationAccumulator(citation.sourceKey(), domainName)
            );
            accumulator.addCitation(
                    citation.runId(),
                    citation.url(),
                    citation.title(),
                    citation.sourceType(),
                    citation.ownershipKey()
            );
        }
        int totalCitationCount = accumulatorByDomain.values().stream()
                .mapToInt(SourceAggregationAccumulator::citationCount)
                .sum();
        List<KnowledgeAssetSourceRepository.SourceWindowFactUpsert> windowFacts = accumulatorByDomain.values().stream()
                .map(accumulator -> accumulator.toSourceWindowFactUpsert(
                        brandScopeId,
                        windowKey,
                        runs.size(),
                        totalCitationCount
                ))
                .toList();
        knowledgeAssetSourceRepository.replaceWindowFacts(
                brandScopeId,
                windowKey,
                windowStartAt,
                windowEndAt,
                windowFacts
        );
    }

    private List<BrandSpaceTopic> loadBrandSpaceTopics(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return List.of();
        }
        return defaultList(brandSpaceTopicMapper.selectList(
                new LambdaQueryWrapper<BrandSpaceTopic>()
                        .eq(BrandSpaceTopic::getBrandScopeId, brandScopeId)
                        .orderByAsc(BrandSpaceTopic::getSeqNo)
                        .orderByAsc(BrandSpaceTopic::getCreatedAt)
        ));
    }

    private List<BrandSpacePrompt> loadBrandSpacePrompts(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return List.of();
        }
        return defaultList(brandSpacePromptMapper.selectList(
                new LambdaQueryWrapper<BrandSpacePrompt>()
                        .eq(BrandSpacePrompt::getBrandScopeId, brandScopeId)
                        .orderByAsc(BrandSpacePrompt::getSeqNo)
                        .orderByAsc(BrandSpacePrompt::getCreatedAt)
        ));
    }

    private int syncTopicCatalog(DiagnosisTask diagnosisTask, List<BrandSpaceTopic> topics) {
        if (!StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return 0;
        }
        List<String> activeTopicIds = new ArrayList<>();
        for (BrandSpaceTopic topic : topics) {
            if (!StringUtils.hasText(topic.getId())) {
                continue;
            }
            activeTopicIds.add(topic.getId());
            knowledgeAssetProblemRepository.upsertTopicCatalog(new KnowledgeAssetProblemRepository.TopicCatalogUpsert(
                    buildTopicCatalogId(diagnosisTask.getBrandScopeId(), topic.getId()),
                    diagnosisTask.getOrganizationId(),
                    diagnosisTask.getBrandScopeId(),
                    topic.getId(),
                    firstText(topic.getName(), "未命名主题"),
                    normalizeText(topic.getDescription()),
                    null,
                    toJson(Map.of(
                            "sourceTaskId", diagnosisTask.getId(),
                            "seqNo", topic.getSeqNo() == null ? 0 : topic.getSeqNo(),
                            "statusCode", topic.getStatusCode() == null ? 0 : topic.getStatusCode()
                    ))
            ));
        }
        knowledgeAssetProblemRepository.deactivateMissingTopics(diagnosisTask.getBrandScopeId(), activeTopicIds);
        return activeTopicIds.size();
    }

    private int syncPromptCatalog(DiagnosisTask diagnosisTask, List<BrandSpacePrompt> prompts) {
        if (!StringUtils.hasText(diagnosisTask.getBrandScopeId())) {
            return 0;
        }
        List<String> activePromptIds = new ArrayList<>();
        for (BrandSpacePrompt prompt : prompts) {
            if (!StringUtils.hasText(prompt.getId())) {
                continue;
            }
            activePromptIds.add(prompt.getId());
            knowledgeAssetProblemRepository.upsertPromptCatalog(new KnowledgeAssetProblemRepository.PromptCatalogUpsert(
                    buildPromptCatalogId(diagnosisTask.getBrandScopeId(), prompt.getId()),
                    diagnosisTask.getOrganizationId(),
                    diagnosisTask.getBrandScopeId(),
                    StringUtils.hasText(prompt.getTopicId())
                            ? buildTopicCatalogId(diagnosisTask.getBrandScopeId(), prompt.getTopicId())
                            : null,
                    prompt.getId(),
                    firstText(prompt.getPromptText(), "未命名提示词"),
                    prompt.getSeqNo(),
                    toJson(Map.of(
                            "sourceTaskId", diagnosisTask.getId(),
                            "topicId", defaultIfBlank(prompt.getTopicId(), ""),
                            "statusCode", prompt.getStatusCode() == null ? 0 : prompt.getStatusCode()
                    ))
            ));
        }
        knowledgeAssetProblemRepository.deactivateMissingPrompts(diagnosisTask.getBrandScopeId(), activePromptIds);
        return activePromptIds.size();
    }

    private void syncProblemWindowFacts(String brandScopeId, OffsetDateTime observedAt) {
        if (!StringUtils.hasText(brandScopeId) || observedAt == null) {
            return;
        }
        recomputeProblemRunBasedWindow(brandScopeId, observedAt);
        recomputeProblemTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(7), WINDOW_KEY_LAST_7_DAYS);
        recomputeProblemTimeBasedWindow(brandScopeId, observedAt, observedAt.minusDays(14), WINDOW_KEY_LAST_14_DAYS);
    }

    private void recomputeProblemRunBasedWindow(String brandScopeId, OffsetDateTime observedAt) {
        List<KnowledgeAssetProblemRepository.WindowRunHead> runs = knowledgeAssetProblemRepository
                .listRecentRunsByScope(brandScopeId, observedAt, WINDOW_LAST_3_RUNS);
        OffsetDateTime windowStartAt = runs.isEmpty()
                ? observedAt
                : runs.get(runs.size() - 1).observedAt();
        replaceProblemWindowFacts(brandScopeId, WINDOW_KEY_LAST_3_RUNS, windowStartAt, observedAt, runs);
    }

    private void recomputeProblemTimeBasedWindow(String brandScopeId,
                                                 OffsetDateTime observedAt,
                                                 OffsetDateTime windowStartAt,
                                                 String windowKey) {
        List<KnowledgeAssetProblemRepository.WindowRunHead> runs = knowledgeAssetProblemRepository
                .listRunsByScopeWithinWindow(brandScopeId, windowStartAt, observedAt);
        replaceProblemWindowFacts(brandScopeId, windowKey, windowStartAt, observedAt, runs);
    }

    private void replaceProblemWindowFacts(String brandScopeId,
                                           String windowKey,
                                           OffsetDateTime windowStartAt,
                                           OffsetDateTime windowEndAt,
                                           List<KnowledgeAssetProblemRepository.WindowRunHead> runs) {
        List<String> runIds = runs.stream()
                .map(KnowledgeAssetProblemRepository.WindowRunHead::runId)
                .toList();
        Map<String, ProblemWindowAccumulator> accumulatorByKey = new LinkedHashMap<>();
        for (KnowledgeAssetProblemRepository.ProblemSnapshotFactView snapshotFact :
                defaultList(knowledgeAssetProblemRepository.listSnapshotFactsByRunIds(runIds))) {
            String aggregateKey = buildProblemAggregateKey(
                    snapshotFact.problemSpaceBucket(),
                    snapshotFact.topicCatalogId(),
                    snapshotFact.clusterKey(),
                    snapshotFact.factSource()
            );
            if (!StringUtils.hasText(aggregateKey)) {
                continue;
            }
            ProblemWindowAccumulator accumulator = accumulatorByKey.computeIfAbsent(
                    aggregateKey,
                    ignored -> new ProblemWindowAccumulator(
                            defaultIfBlank(snapshotFact.problemSpaceBucket(), "uncategorized"),
                            snapshotFact.topicCatalogId(),
                            snapshotFact.clusterKey(),
                            snapshotFact.factSource()
                    )
            );
            accumulator.addSnapshot(snapshotFact);
        }
        List<KnowledgeAssetProblemRepository.ProblemWindowFactUpsert> windowFacts = accumulatorByKey.values().stream()
                .map(accumulator -> accumulator.toUpsert(brandScopeId, windowKey, runs.size()))
                .toList();
        knowledgeAssetProblemRepository.replaceWindowFacts(
                brandScopeId,
                windowKey,
                windowStartAt,
                windowEndAt,
                windowFacts
        );
    }

    private List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert> buildCompetitorSnapshotFacts(
            DiagnosisTask diagnosisTask,
            String runId,
            DiagnosisReportRecord diagnosisReport) {
        Map<String, DerivedCompetitorMetricSnapshot> derivedMetrics =
                buildDerivedCompetitorMetrics(diagnosisTask, diagnosisReport);
        Object rawRanking = diagnosisReport == null || diagnosisReport.getReportProjectionJson() == null
                ? null
                : diagnosisReport.getReportProjectionJson().get("competitorRanking");
        List<?> rankingList = rawRanking instanceof List<?> list ? list : List.of();
        List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert> snapshotFacts = new ArrayList<>();
        Set<String> includedCompetitorKeys = new LinkedHashSet<>();
        int fallbackRankNo = 1;
        for (Object item : rankingList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> competitorItem = toStringKeyMap(rawMap);
            String competitorName = normalizeText(asText(competitorItem.get("brand")));
            String competitorKey = normalizeBrandKey(competitorName);
            if (!StringUtils.hasText(competitorKey)) {
                continue;
            }
            DerivedCompetitorMetricSnapshot derived = derivedMetrics.get(competitorKey);
            Integer rankNo = readInteger(competitorItem.get("rank"));
            Double mentionRate = firstNonNull(
                    readPercent(competitorItem.get("mentionRate"), true),
                    derived == null ? null : derived.mentionRate()
            );
            Double recommendRate = firstNonNull(
                    readPercent(competitorItem.get("recommendRate"), true),
                    derived == null ? null : derived.recommendRate()
            );
            Double top3Rate = firstNonNull(
                    readPercent(competitorItem.get("top3Rate"), true),
                    derived == null ? null : derived.top3Rate()
            );
            Double averagePosition = firstNonNull(
                    readNumber(competitorItem.get("averagePosition")),
                    derived == null ? null : derived.averagePosition()
            );
            Integer mentionCount = firstNonNull(
                    readInteger(competitorItem.get("mentionCount")),
                    derived == null ? null : derived.mentionCount()
            );
            snapshotFacts.add(new KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert(
                    buildCompetitorSnapshotFactId(runId, competitorKey),
                    competitorKey,
                    competitorName,
                    rankNo == null ? fallbackRankNo : rankNo,
                    firstNonNull(
                            readPercent(competitorItem.get("visibilityScore"), true),
                            resolveVisibilityScore(mentionRate, recommendRate, top3Rate)
                    ),
                    recommendRate,
                    mentionRate,
                    top3Rate,
                    averagePosition,
                    mentionCount,
                    toJson(competitorItem)
            ));
            includedCompetitorKeys.add(competitorKey);
            fallbackRankNo++;
        }
        String targetBrandKey = normalizeBrandKey(diagnosisTask == null ? null : diagnosisTask.getBrandNameSnapshot());
        for (Map.Entry<String, DerivedCompetitorMetricSnapshot> entry : derivedMetrics.entrySet()) {
            String competitorKey = entry.getKey();
            DerivedCompetitorMetricSnapshot derived = entry.getValue();
            if (!StringUtils.hasText(competitorKey)
                    || Objects.equals(competitorKey, targetBrandKey)
                    || includedCompetitorKeys.contains(competitorKey)) {
                continue;
            }
            snapshotFacts.add(new KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactUpsert(
                    buildCompetitorSnapshotFactId(runId, competitorKey),
                    competitorKey,
                    derived.brandName(),
                    fallbackRankNo,
                    resolveVisibilityScore(derived.mentionRate(), derived.recommendRate(), derived.top3Rate()),
                    derived.recommendRate(),
                    derived.mentionRate(),
                    derived.top3Rate(),
                    derived.averagePosition(),
                    derived.mentionCount(),
                    toJson(Map.of(
                            "brand", derived.brandName(),
                            "source", "probe_brand_mentions_fallback"
                    ))
            ));
            fallbackRankNo++;
        }
        return snapshotFacts;
    }

    private Map<String, DerivedCompetitorMetricSnapshot> buildDerivedCompetitorMetrics(DiagnosisTask diagnosisTask,
                                                                                       DiagnosisReportRecord diagnosisReport) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return Map.of();
        }
        int denominator = resolveCompetitorMetricDenominator(diagnosisTask, diagnosisReport);
        if (denominator <= 0) {
            return Map.of();
        }
        LinkedHashMap<String, DerivedCompetitorMetricAccumulator> accumulatorByBrand = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : loadBrandMentions(diagnosisTask)) {
            if (!Boolean.TRUE.equals(mention.getMentionedFlag())) {
                continue;
            }
            String brandName = normalizeText(mention.getBrandName());
            String brandKey = normalizeBrandKey(brandName);
            if (!StringUtils.hasText(brandKey)) {
                continue;
            }
            String probeKey = firstText(mention.getProbeId(), mention.getDispatchAttemptId(), mention.getId());
            if (!StringUtils.hasText(probeKey)) {
                continue;
            }
            accumulatorByBrand.computeIfAbsent(
                    brandKey,
                    ignored -> new DerivedCompetitorMetricAccumulator(brandName)
            ).add(probeKey, Boolean.TRUE.equals(mention.getRecommendedFlag()), mention.getPositionIndex());
        }
        if (accumulatorByBrand.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, DerivedCompetitorMetricSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, DerivedCompetitorMetricAccumulator> entry : accumulatorByBrand.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot(denominator));
        }
        return result;
    }

    private int resolveCompetitorMetricDenominator(DiagnosisTask diagnosisTask,
                                                   DiagnosisReportRecord diagnosisReport) {
        if (diagnosisReport != null && diagnosisReport.getSampleSize() != null && diagnosisReport.getSampleSize() > 0) {
            return diagnosisReport.getSampleSize();
        }
        return loadDiagnosisProbes(diagnosisTask).size();
    }

    private Double resolveVisibilityScore(Double mentionRate, Double recommendRate, Double top3Rate) {
        if (mentionRate == null || recommendRate == null || top3Rate == null) {
            return null;
        }
        return round2(mentionRate * 0.35d + recommendRate * 0.40d + top3Rate * 0.25d);
    }

    private List<KnowledgeAssetSourceRepository.CitationUpsert> buildCitationUpserts(String runId,
                                                                                     DiagnosisTask diagnosisTask,
                                                                                     DiagnosisReportRecord diagnosisReport,
                                                                                     Set<String> ownedDomains) {
        List<ProbeEvidenceRecord> evidenceRecords = loadCitationEvidence(diagnosisTask, diagnosisReport);
        List<KnowledgeAssetSourceRepository.CitationUpsert> citations = new ArrayList<>();
        int sequence = 0;
        for (ProbeEvidenceRecord evidenceRecord : evidenceRecords) {
            sequence++;
            String url = normalizeText(evidenceRecord.getUrl());
            String domainName = firstText(normalizeDomain(evidenceRecord.getDomain()), domainOf(url));
            if (!StringUtils.hasText(domainName)) {
                continue;
            }
            String sourceType = resolveSourceType(evidenceRecord.getSourceTypeCode(), domainName, ownedDomains);
            String ownershipKey = resolveOwnershipKey(domainName, ownedDomains);
            String sourceKey = buildSourceKey(domainName);
            String providerKey = normalizeStorageKey(evidenceRecord.getProvider());
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceTaskId", diagnosisTask.getId());
            payload.put("provider", normalizeText(evidenceRecord.getProvider()));
            payload.put("providerKey", providerKey);
            payload.put("sourceKey", sourceKey);
            payload.put("searchQuery", normalizeText(evidenceRecord.getSearchQuery()));
            payload.put("rawSourceTypeCode", normalizeText(evidenceRecord.getSourceTypeCode()));
            payload.put("source", StringUtils.hasText(evidenceRecord.getId()) ? "probe_evidence" : "report_projection_fallback");
            citations.add(new KnowledgeAssetSourceRepository.CitationUpsert(
                    buildCitationId(runId, evidenceRecord.getId(), domainName, url, sequence),
                    buildProbeEventId(evidenceRecord.getProbeId()),
                    buildAttemptEventId(evidenceRecord.getDispatchAttemptId()),
                    normalizeText(evidenceRecord.getId()),
                    sourceKey,
                    domainName,
                    url,
                    normalizeText(evidenceRecord.getTitle()),
                    normalizeText(evidenceRecord.getSnippet()),
                    sourceType,
                    ownershipKey,
                    providerKey,
                    toJson(payload),
                    toOffsetDateTime(firstNonNull(evidenceRecord.getRetrievedAt(), evidenceRecord.getCreatedAt(), null))
            ));
        }
        return citations;
    }

    private int upsertSourceAssets(DiagnosisTask diagnosisTask,
                                   List<KnowledgeAssetSourceRepository.CitationUpsert> citations,
                                   OffsetDateTime observedAt) {
        if (!StringUtils.hasText(diagnosisTask.getBrandScopeId()) || citations == null || citations.isEmpty()) {
            return 0;
        }
        Map<String, KnowledgeAssetSourceRepository.CitationUpsert> citationBySourceKey = new LinkedHashMap<>();
        for (KnowledgeAssetSourceRepository.CitationUpsert citation : citations) {
            if (StringUtils.hasText(citation.sourceKey())) {
                citationBySourceKey.putIfAbsent(citation.sourceKey(), citation);
            }
        }
        for (KnowledgeAssetSourceRepository.CitationUpsert citation : citationBySourceKey.values()) {
            knowledgeAssetSourceRepository.upsertSourceAsset(new KnowledgeAssetSourceRepository.SourceAssetUpsert(
                    buildSourceAssetId(diagnosisTask.getBrandScopeId(), citation.sourceKey()),
                    diagnosisTask.getOrganizationId(),
                    diagnosisTask.getBrandScopeId(),
                    citation.sourceKey(),
                    SOURCE_LEVEL_DOMAIN,
                    citation.domainName(),
                    null,
                    citation.title(),
                    citation.sourceType(),
                    citation.ownershipKey(),
                    citation.providerKey(),
                    firstNonNull(citation.retrievedAt(), observedAt, OffsetDateTime.now()),
                    toJson(Map.of(
                            "sourceTaskId", diagnosisTask.getId(),
                            "sampleUrl", defaultIfBlank(citation.url(), ""),
                            "firstCitationId", citation.id()
                    ))
            ));
        }
        return citationBySourceKey.size();
    }

    private List<KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert> buildSourceSnapshotFacts(
            String runId,
            String brandScopeId,
            List<KnowledgeAssetSourceRepository.CitationUpsert> citations) {
        if (!StringUtils.hasText(brandScopeId) || citations == null || citations.isEmpty()) {
            return List.of();
        }
        Map<String, SourceAggregationAccumulator> accumulatorByDomain = new LinkedHashMap<>();
        for (KnowledgeAssetSourceRepository.CitationUpsert citation : citations) {
            String domainName = normalizeDomain(citation.domainName());
            if (!StringUtils.hasText(domainName)) {
                continue;
            }
            SourceAggregationAccumulator accumulator = accumulatorByDomain.computeIfAbsent(
                    domainName,
                    ignored -> new SourceAggregationAccumulator(citation.sourceKey(), domainName)
            );
            accumulator.addCitation(
                    runId,
                    citation.url(),
                    citation.title(),
                    citation.sourceType(),
                    citation.ownershipKey()
            );
        }
        int totalCitationCount = accumulatorByDomain.values().stream()
                .mapToInt(SourceAggregationAccumulator::citationCount)
                .sum();
        return accumulatorByDomain.values().stream()
                .map(accumulator -> accumulator.toSourceSnapshotFactUpsert(runId, brandScopeId, totalCitationCount))
                .toList();
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildProblemSnapshotFacts(
            String runId,
            DiagnosisTask diagnosisTask,
            DiagnosisReportRecord diagnosisReport,
            List<BrandSpacePrompt> prompts,
            List<DiagnosisProbeRecord> probes) {
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> reportFacts =
                buildReportProblemSnapshotFacts(runId, diagnosisReport);
        if (!reportFacts.isEmpty()) {
            return reportFacts;
        }
        return buildProbeBucketProblemFacts(runId, diagnosisTask, prompts, probes);
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildReportProblemSnapshotFacts(
            String runId,
            DiagnosisReportRecord diagnosisReport) {
        if (diagnosisReport == null || diagnosisReport.getReportProjectionJson() == null) {
            return List.of();
        }
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> clusterProfileFacts =
                buildClusterProfileProblemFacts(runId, diagnosisReport.getReportProjectionJson().get("clusterProfiles"));
        if (!clusterProfileFacts.isEmpty()) {
            return clusterProfileFacts;
        }
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> clusterBreakdownFacts =
                buildClusterBreakdownProblemFacts(runId, diagnosisReport.getReportProjectionJson().get("clusterBreakdown"));
        if (!clusterBreakdownFacts.isEmpty()) {
            return clusterBreakdownFacts;
        }
        return buildNegativeSignalProblemFacts(runId, diagnosisReport.getReportProjectionJson().get("negativeSignals"));
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildClusterProfileProblemFacts(
            String runId,
            Object rawClusterProfiles) {
        if (!(rawClusterProfiles instanceof List<?> clusterProfiles) || clusterProfiles.isEmpty()) {
            return List.of();
        }
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> facts = new ArrayList<>();
        int index = 0;
        for (Object item : clusterProfiles) {
            index++;
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> clusterProfile = toStringKeyMap(rawMap);
            String clusterKey = firstText(normalizeText(asText(clusterProfile.get("key"))), "cluster_profile_" + index);
            String problemSpaceBucket = defaultIfBlank(normalizeText(asText(clusterProfile.get("problemSpaceBucket"))), "uncategorized");
            int promptCount = readStringList(clusterProfile.get("prompts")).size();
            Integer sampleCount = readInteger(clusterProfile.get("sampleCount"));
            facts.add(new KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert(
                    buildProblemSnapshotFactId(runId, problemSpaceBucket, null, clusterKey, FACT_SOURCE_CLUSTER_PROFILE),
                    problemSpaceBucket,
                    null,
                    clusterKey,
                    FACT_SOURCE_CLUSTER_PROFILE,
                    promptCount,
                    sampleCount == null ? 0 : sampleCount,
                    0,
                    0,
                    readBlindSpotScore(clusterProfile.get("blindSpot")),
                    toJson(clusterProfile)
            ));
        }
        return facts;
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildClusterBreakdownProblemFacts(
            String runId,
            Object rawClusterBreakdown) {
        if (!(rawClusterBreakdown instanceof Map<?, ?> rawBreakdownMap)) {
            return List.of();
        }
        Map<String, Object> clusterBreakdown = toStringKeyMap(rawBreakdownMap);
        Object rawClusters = clusterBreakdown.get("clusters");
        if (!(rawClusters instanceof List<?> clusters) || clusters.isEmpty()) {
            return List.of();
        }
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> facts = new ArrayList<>();
        int index = 0;
        for (Object item : clusters) {
            index++;
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> cluster = toStringKeyMap(rawMap);
            String clusterKey = firstText(normalizeText(asText(cluster.get("clusterId"))), "cluster_breakdown_" + index);
            String problemSpaceBucket = defaultIfBlank(resolveProblemSpaceBucket(clusterKey), "uncategorized");
            List<String> samplePrompts = readStringList(cluster.get("samplePrompts"));
            facts.add(new KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert(
                    buildProblemSnapshotFactId(runId, problemSpaceBucket, null, clusterKey, FACT_SOURCE_CLUSTER_BREAKDOWN),
                    problemSpaceBucket,
                    null,
                    clusterKey,
                    FACT_SOURCE_CLUSTER_BREAKDOWN,
                    samplePrompts.size(),
                    readInteger(cluster.get("sampleCount")) == null ? 0 : readInteger(cluster.get("sampleCount")),
                    0,
                    0,
                    readPercent(cluster.get("recommendRate"), true),
                    toJson(cluster)
            ));
        }
        return facts;
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildNegativeSignalProblemFacts(
            String runId,
            Object rawNegativeSignals) {
        if (!(rawNegativeSignals instanceof List<?> negativeSignals) || negativeSignals.isEmpty()) {
            return List.of();
        }
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> facts = new ArrayList<>();
        int index = 0;
        for (Object item : negativeSignals) {
            index++;
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> signal = toStringKeyMap(rawMap);
            String clusterKey = firstText(normalizeStorageKey(asText(signal.get("signal"))), "negative_signal_" + index);
            Integer count = readInteger(signal.get("count"));
            facts.add(new KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert(
                    buildProblemSnapshotFactId(runId, "negative_signal", null, clusterKey, FACT_SOURCE_NEGATIVE_SIGNAL),
                    "negative_signal",
                    null,
                    clusterKey,
                    FACT_SOURCE_NEGATIVE_SIGNAL,
                    0,
                    count == null ? 0 : count,
                    0,
                    0,
                    count == null ? null : round2(count.doubleValue()),
                    toJson(signal)
            ));
        }
        return facts;
    }

    private List<KnowledgeAssetProblemRepository.ProblemSignalUpsert> buildProblemSignals(String runId,
                                                                                          DiagnosisTask diagnosisTask,
                                                                                          DiagnosisReportRecord diagnosisReport,
                                                                                          List<BrandSpacePrompt> prompts,
                                                                                          List<DiagnosisProbeRecord> probes) {
        List<KnowledgeAssetProblemRepository.ProblemSignalUpsert> signals = new ArrayList<>();
        for (KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert fact :
                buildReportProblemSnapshotFacts(runId, diagnosisReport)) {
            String signalType = signalTypeForFactSource(fact.factSource());
            signals.add(new KnowledgeAssetProblemRepository.ProblemSignalUpsert(
                    buildProblemSignalId(runId, fact.problemSpaceBucket(), fact.topicCatalogId(), fact.clusterKey(), signalType),
                    null,
                    null,
                    fact.topicCatalogId(),
                    null,
                    fact.problemSpaceBucket(),
                    fact.clusterKey(),
                    signalType,
                    fact.signalScore(),
                    defaultIfBlank(fact.clusterKey(), fact.problemSpaceBucket()),
                    fact.factPayloadJson()
            ));
        }
        if (probes == null || probes.isEmpty()) {
            return signals;
        }
        Map<Integer, BrandSpacePrompt> promptBySeqNo = indexPromptsBySeqNo(prompts);
        Map<String, BrandSpacePrompt> promptById = indexPromptsById(prompts);
        int sequence = 0;
        for (DiagnosisProbeRecord probe : probes) {
            sequence++;
            ProbeCatalogBinding binding = resolveProbeCatalogBinding(
                    diagnosisTask.getBrandScopeId(),
                    probe,
                    promptBySeqNo,
                    promptById
            );
            String clusterKey = firstText(
                    binding.promptCatalogId(),
                    firstText(binding.topicCatalogId(), "probe-" + sequence)
            );
            signals.add(new KnowledgeAssetProblemRepository.ProblemSignalUpsert(
                    buildProblemSignalId(runId, binding.problemSpaceBucket(), binding.topicCatalogId(), clusterKey,
                            SIGNAL_TYPE_PROBE_BUCKET + "-" + defaultIfBlank(probe.getId(), String.valueOf(sequence))),
                    buildProbeEventId(probe.getId()),
                    null,
                    binding.topicCatalogId(),
                    binding.promptCatalogId(),
                    binding.problemSpaceBucket(),
                    clusterKey,
                    SIGNAL_TYPE_PROBE_BUCKET,
                    null,
                    responsePreview(probe.getRenderText(), probe.getRenderMetadataJson()),
                    toJson(buildProbePayload(probe, binding))
            ));
        }
        return signals;
    }

    private List<KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert> buildProbeBucketProblemFacts(
            String runId,
            DiagnosisTask diagnosisTask,
            List<BrandSpacePrompt> prompts,
            List<DiagnosisProbeRecord> probes) {
        if (probes == null || probes.isEmpty()) {
            return List.of();
        }
        Map<Integer, BrandSpacePrompt> promptBySeqNo = indexPromptsBySeqNo(prompts);
        Map<String, BrandSpacePrompt> promptById = indexPromptsById(prompts);
        Map<String, ProbeBucketAccumulator> accumulatorByKey = new LinkedHashMap<>();
        for (DiagnosisProbeRecord probe : probes) {
            ProbeCatalogBinding binding = resolveProbeCatalogBinding(
                    diagnosisTask.getBrandScopeId(),
                    probe,
                    promptBySeqNo,
                    promptById
            );
            String clusterKey = firstText(
                    binding.promptCatalogId(),
                    firstText(binding.topicCatalogId(), binding.problemSpaceBucket())
            );
            String aggregateKey = buildProblemAggregateKey(
                    binding.problemSpaceBucket(),
                    binding.topicCatalogId(),
                    clusterKey,
                    SIGNAL_TYPE_PROBE_BUCKET
            );
            ProbeBucketAccumulator accumulator = accumulatorByKey.computeIfAbsent(
                    aggregateKey,
                    ignored -> new ProbeBucketAccumulator(
                            binding.problemSpaceBucket(),
                            binding.topicCatalogId(),
                            clusterKey
                    )
            );
            accumulator.addProbe(binding.promptCatalogId(), probe.getStatusCode());
        }
        return accumulatorByKey.values().stream()
                .map(accumulator -> accumulator.toSnapshotFact(runId))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> readConfiguredCompetitors(BrandScope brandScope) {
        if (brandScope == null || brandScope.getDefaultModelPolicyJson() == null) {
            return List.of();
        }
        Object rawProfile = brandScope.getDefaultModelPolicyJson().get("brandSpaceProfile");
        if (!(rawProfile instanceof Map<?, ?> profileMap)) {
            return List.of();
        }
        Object rawCompetitors = ((Map<String, Object>) toStringKeyMap(profileMap)).get("competitors");
        if (!(rawCompetitors instanceof List<?> competitorList)) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (Object item : competitorList) {
            String competitorName = normalizeText(asText(item));
            if (StringUtils.hasText(competitorName)) {
                deduplicated.add(competitorName);
            }
        }
        return List.copyOf(deduplicated);
    }

    private List<String> readOwnedDomains(DiagnosisTask diagnosisTask, BrandScope brandScope) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        addOwnedDomain(domains, brandScope == null ? null : brandScope.getPrimaryDomain());
        addOwnedDomain(domains, diagnosisTask == null ? null : diagnosisTask.getOfficialWebsiteSnapshot());
        return List.copyOf(domains);
    }

    private void addOwnedDomain(Set<String> domains, String raw) {
        String domain = firstText(domainOf(raw), normalizeDomain(raw));
        if (StringUtils.hasText(domain)) {
            domains.add(domain);
            if (domain.startsWith("www.") && domain.length() > 4) {
                domains.add(domain.substring(4));
            }
        }
    }

    private List<DiagnosisProbeRecord> loadDiagnosisProbes(DiagnosisTask diagnosisTask) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return List.of();
        }
        return defaultList(diagnosisProbeRecordMapper.selectList(
                new LambdaQueryWrapper<DiagnosisProbeRecord>()
                        .eq(DiagnosisProbeRecord::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByAsc(DiagnosisProbeRecord::getSeqNo)
                        .orderByAsc(DiagnosisProbeRecord::getCreatedAt)
                        .last("limit " + MAX_EVENT_ROWS)
        ));
    }

    private List<DiagnosisEventRecord> loadDiagnosisEvents(DiagnosisTask diagnosisTask) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return List.of();
        }
        return defaultList(diagnosisEventRecordMapper.selectList(
                new LambdaQueryWrapper<DiagnosisEventRecord>()
                        .eq(DiagnosisEventRecord::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByAsc(DiagnosisEventRecord::getSeq)
                        .last("limit " + MAX_EVENT_ROWS)
        ));
    }

    private List<ProbeDispatchAttempt> loadDispatchAttempts(DiagnosisTask diagnosisTask) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return List.of();
        }
        return defaultList(probeDispatchAttemptMapper.selectList(
                new LambdaQueryWrapper<ProbeDispatchAttempt>()
                        .eq(ProbeDispatchAttempt::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByAsc(ProbeDispatchAttempt::getCreatedAt)
                        .last("limit " + MAX_EVENT_ROWS)
        ));
    }

    private List<ProbeBrandMentionRecord> loadBrandMentions(DiagnosisTask diagnosisTask) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return List.of();
        }
        return defaultList(probeBrandMentionRecordMapper.selectList(
                new LambdaQueryWrapper<ProbeBrandMentionRecord>()
                        .eq(ProbeBrandMentionRecord::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByAsc(ProbeBrandMentionRecord::getCreatedAt)
                        .last("limit " + MAX_EVENT_ROWS)
        ));
    }

    private List<ProbeEvidenceRecord> loadCitationEvidence(DiagnosisTask diagnosisTask,
                                                           DiagnosisReportRecord diagnosisReport) {
        if (diagnosisTask == null || !StringUtils.hasText(diagnosisTask.getId())) {
            return List.of();
        }
        List<ProbeEvidenceRecord> persisted = probeEvidenceRecordMapper.selectList(
                new LambdaQueryWrapper<ProbeEvidenceRecord>()
                        .eq(ProbeEvidenceRecord::getDiagnosisTaskId, diagnosisTask.getId())
                        .orderByDesc(ProbeEvidenceRecord::getCreatedAt)
                        .last("limit 1000")
        );
        if (persisted != null && !persisted.isEmpty()) {
            return persisted;
        }
        return loadProjectionEvidence(diagnosisTask, diagnosisReport);
    }

    private List<ProbeEvidenceRecord> loadProjectionEvidence(DiagnosisTask diagnosisTask,
                                                             DiagnosisReportRecord diagnosisReport) {
        if (diagnosisTask == null || diagnosisReport == null || diagnosisReport.getReportProjectionJson() == null) {
            return List.of();
        }
        Object rawEvidenceChain = diagnosisReport.getReportProjectionJson().get("evidenceChain");
        if (!(rawEvidenceChain instanceof List<?> evidenceChains) || evidenceChains.isEmpty()) {
            return List.of();
        }
        List<ProbeEvidenceRecord> projectionEvidence = new ArrayList<>();
        int chainIndex = 0;
        for (Object chainItem : evidenceChains) {
            chainIndex++;
            if (!(chainItem instanceof Map<?, ?> chainMap)) {
                continue;
            }
            Map<String, Object> chain = toStringKeyMap(chainMap);
            String probeId = firstText(asText(chain.get("probeId")), "projection_probe_" + chainIndex);
            Object rawEvidence = chain.get("evidence");
            if (!(rawEvidence instanceof List<?> evidenceItems) || evidenceItems.isEmpty()) {
                continue;
            }
            for (Object evidenceItem : evidenceItems) {
                if (!(evidenceItem instanceof Map<?, ?> evidenceMap)) {
                    continue;
                }
                Map<String, Object> evidence = toStringKeyMap(evidenceMap);
                String url = normalizeText(asText(evidence.get("url")));
                String domain = firstText(normalizeDomain(asText(evidence.get("domain"))), domainOf(url));
                if (!StringUtils.hasText(url) && !StringUtils.hasText(domain)) {
                    continue;
                }
                LocalDateTime retrievedAt = parseProjectionDateTime(asText(evidence.get("retrievedAt")));
                projectionEvidence.add(ProbeEvidenceRecord.builder()
                        .diagnosisTaskId(diagnosisTask.getId())
                        .probeId(probeId)
                        .sourceTypeCode(normalizeText(asText(evidence.get("sourceType"))))
                        .provider(normalizeText(asText(evidence.get("provider"))))
                        .domain(domain)
                        .url(url)
                        .title(normalizeText(asText(evidence.get("title"))))
                        .snippet(normalizeText(asText(evidence.get("snippet"))))
                        .searchQuery(normalizeText(asText(evidence.get("query"))))
                        .retrievedAt(retrievedAt)
                        .createdAt(firstNonNull(retrievedAt, diagnosisReport.getGeneratedAt(), diagnosisReport.getCreatedAt()))
                        .build());
            }
        }
        return projectionEvidence;
    }

    private LocalDateTime parseProjectionDateTime(String raw) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
            return KnowledgeAssetTimeSupport.toProductLocalDateTime(normalized);
        }
    }

    private Map<Integer, BrandSpacePrompt> indexPromptsBySeqNo(List<BrandSpacePrompt> prompts) {
        Map<Integer, BrandSpacePrompt> promptBySeqNo = new LinkedHashMap<>();
        for (BrandSpacePrompt prompt : defaultList(prompts)) {
            if (prompt.getSeqNo() != null) {
                promptBySeqNo.putIfAbsent(prompt.getSeqNo(), prompt);
            }
        }
        return promptBySeqNo;
    }

    private Map<String, BrandSpacePrompt> indexPromptsById(List<BrandSpacePrompt> prompts) {
        Map<String, BrandSpacePrompt> promptById = new LinkedHashMap<>();
        for (BrandSpacePrompt prompt : defaultList(prompts)) {
            if (StringUtils.hasText(prompt.getId())) {
                promptById.put(prompt.getId(), prompt);
            }
        }
        return promptById;
    }

    private ProbeCatalogBinding resolveProbeCatalogBinding(String brandScopeId,
                                                           DiagnosisProbeRecord probe,
                                                           Map<Integer, BrandSpacePrompt> promptBySeqNo,
                                                           Map<String, BrandSpacePrompt> promptById) {
        Map<String, Object> renderMetadata = safeMap(probe == null ? null : probe.getRenderMetadataJson());
        Map<String, Object> triggeredRules = safeMap(probe == null ? null : probe.getTriggeredRulesJson());
        String sourcePromptId = firstText(
                firstText(asText(renderMetadata.get("promptId")), asText(renderMetadata.get("sourcePromptId"))),
                firstText(asText(triggeredRules.get("promptId")), asText(triggeredRules.get("sourcePromptId")))
        );
        BrandSpacePrompt prompt = StringUtils.hasText(sourcePromptId) ? promptById.get(sourcePromptId) : null;
        if (prompt == null) {
            Integer promptSequence = firstNonNull(
                    readInteger(renderMetadata.get("promptSequence")),
                    readInteger(triggeredRules.get("promptSequence")),
                    probe == null ? null : probe.getSeqNo()
            );
            prompt = promptSequence == null ? null : promptBySeqNo.get(promptSequence);
            sourcePromptId = prompt == null ? sourcePromptId : prompt.getId();
        }
        String sourceTopicId = firstText(
                firstText(asText(renderMetadata.get("topicId")), asText(renderMetadata.get("sourceTopicId"))),
                firstText(asText(triggeredRules.get("topicId")), asText(triggeredRules.get("sourceTopicId")))
        );
        if (!StringUtils.hasText(sourceTopicId) && prompt != null) {
            sourceTopicId = prompt.getTopicId();
        }
        String problemSpaceBucket = defaultIfBlank(
                resolveProblemSpaceBucket(firstText(
                        firstText(asText(renderMetadata.get("problemSpaceBucket")), asText(triggeredRules.get("problemSpaceBucket"))),
                        probe == null ? null : probe.getBucketTypeCode()
                )),
                "uncategorized"
        );
        return new ProbeCatalogBinding(
                StringUtils.hasText(sourceTopicId) ? buildTopicCatalogId(brandScopeId, sourceTopicId) : null,
                StringUtils.hasText(sourcePromptId) ? buildPromptCatalogId(brandScopeId, sourcePromptId) : null,
                problemSpaceBucket,
                sourceTopicId,
                sourcePromptId
        );
    }

    private Map<String, Object> buildProbePayload(DiagnosisProbeRecord probe, ProbeCatalogBinding binding) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceProbeId", probe == null ? null : probe.getId());
        payload.put("probeRef", probe == null ? null : probe.getProbeRef());
        payload.put("bucketTypeCode", probe == null ? null : probe.getBucketTypeCode());
        payload.put("probeTypeCode", probe == null ? null : probe.getProbeTypeCode());
        payload.put("surfaceIntent", probe == null ? null : probe.getSurfaceIntent());
        payload.put("interactionPosture", probe == null ? null : probe.getInteractionPosture());
        payload.put("conversationMode", probe == null ? null : probe.getConversationMode());
        payload.put("statusCode", probe == null ? null : probe.getStatusCode());
        payload.put("sourceTopicId", binding == null ? null : binding.sourceTopicId());
        payload.put("sourcePromptId", binding == null ? null : binding.sourcePromptId());
        payload.put("renderMetadata", safeMap(probe == null ? null : probe.getRenderMetadataJson()));
        payload.put("triggeredRules", safeMap(probe == null ? null : probe.getTriggeredRulesJson()));
        return payload;
    }

    private Map<String, Object> buildBrandMentionPayload(ProbeBrandMentionRecord brandMention) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceMentionId", brandMention.getId());
        payload.put("sourceProbeId", brandMention.getProbeId());
        payload.put("sourceAttemptId", brandMention.getDispatchAttemptId());
        payload.put("brandRoleCode", brandMention.getBrandRoleCode());
        payload.put("mentionLevelCode", brandMention.getMentionLevelCode());
        payload.put("evidenceSpan", brandMention.getEvidenceSpan());
        payload.put("mentionMetadata", safeMap(brandMention.getMentionMetadataJson()));
        return payload;
    }

    private Map<String, Object> sanitizeTaskSnapshot(DiagnosisTask diagnosisTask) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", diagnosisTask.getId());
        snapshot.put("organizationId", diagnosisTask.getOrganizationId());
        snapshot.put("brandId", diagnosisTask.getBrandId());
        snapshot.put("brandScopeId", diagnosisTask.getBrandScopeId());
        snapshot.put("engineVersion", diagnosisTask.getEngineVersion());
        snapshot.put("statusCode", diagnosisTask.getStatusCode());
        snapshot.put("targetProbeCount", diagnosisTask.getTargetProbeCount());
        snapshot.put("targetModelProduct", diagnosisTask.getTargetModelProduct());
        snapshot.put("brandNameSnapshot", diagnosisTask.getBrandNameSnapshot());
        snapshot.put("categorySnapshot", diagnosisTask.getCategorySnapshot());
        snapshot.put("officialWebsiteSnapshot", diagnosisTask.getOfficialWebsiteSnapshot());
        snapshot.put("taskOrder", safeMap(diagnosisTask.getTaskOrderJson()));
        snapshot.put("taskMetadata", safeMap(diagnosisTask.getTaskMetadataJson()));
        return snapshot;
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize knowledge asset payload", exception);
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(DEFAULT_ZONE_ID).toOffsetDateTime();
    }

    private LocalDateTime firstNonNull(LocalDateTime first,
                                       LocalDateTime second,
                                       LocalDateTime third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private OffsetDateTime firstNonNull(OffsetDateTime first,
                                        OffsetDateTime second,
                                        OffsetDateTime third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private Integer firstNonNull(Integer first,
                                 Integer second,
                                 Integer third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private Integer firstNonNull(Integer first,
                                 Integer second) {
        return firstNonNull(first, second, null);
    }

    private Double firstNonNull(Double first,
                                Double second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    private String buildConfiguredCompetitorId(String brandScopeId, String competitorKey) {
        return "ka-competitor-config-" + brandScopeId + "-" + competitorKey;
    }

    private String buildOwnedDomainId(String brandScopeId, String domainName) {
        return "ka-owned-domain-" + brandScopeId + "-" + normalizeStorageKey(domainName);
    }

    private String buildTopicCatalogId(String brandScopeId, String sourceTopicId) {
        return "ka-topic-" + brandScopeId + "-" + normalizeStorageKey(sourceTopicId);
    }

    private String buildPromptCatalogId(String brandScopeId, String sourcePromptId) {
        return "ka-prompt-" + brandScopeId + "-" + normalizeStorageKey(sourcePromptId);
    }

    private String buildCompetitorSnapshotFactId(String runId, String competitorKey) {
        return "ka-competitor-snapshot-" + runId + "-" + competitorKey;
    }

    private String buildProblemSnapshotFactId(String runId,
                                              String problemSpaceBucket,
                                              String topicCatalogId,
                                              String clusterKey,
                                              String factSource) {
        return "ka-problem-snapshot-" + runId
                + "-" + normalizeStorageKey(problemSpaceBucket)
                + "-" + normalizeStorageKey(topicCatalogId)
                + "-" + normalizeStorageKey(clusterKey)
                + "-" + normalizeStorageKey(factSource);
    }

    private String buildCitationId(String runId,
                                   String sourceEvidenceId,
                                   String domainName,
                                   String url,
                                   int sequence) {
        if (StringUtils.hasText(sourceEvidenceId)) {
            return "ka-citation-evidence-" + sourceEvidenceId.trim();
        }
        String sourceKey = StringUtils.hasText(url)
                ? normalizeStorageKey(url)
                : normalizeStorageKey(domainName);
        return "ka-citation-" + runId + "-" + sourceKey + "-" + sequence;
    }

    private String buildSourceKey(String domainName) {
        return "domain:" + normalizeStorageKey(domainName);
    }

    private String buildSourceAssetId(String brandScopeId, String sourceKey) {
        return "ka-source-asset-" + brandScopeId + "-" + normalizeStorageKey(sourceKey);
    }

    private String buildProbeEventId(String sourceProbeId) {
        return StringUtils.hasText(sourceProbeId) ? "ka-probe-" + sourceProbeId.trim() : null;
    }

    private String buildAttemptEventId(String sourceAttemptId) {
        return StringUtils.hasText(sourceAttemptId) ? "ka-attempt-" + sourceAttemptId.trim() : null;
    }

    private String buildBrandMentionEventId(String sourceMentionId) {
        return StringUtils.hasText(sourceMentionId) ? "ka-brand-mention-" + sourceMentionId.trim() : null;
    }

    private String buildProblemSignalId(String runId,
                                        String problemSpaceBucket,
                                        String topicCatalogId,
                                        String clusterKey,
                                        String signalType) {
        return "ka-problem-signal-" + runId
                + "-" + normalizeStorageKey(problemSpaceBucket)
                + "-" + normalizeStorageKey(topicCatalogId)
                + "-" + normalizeStorageKey(clusterKey)
                + "-" + normalizeStorageKey(signalType);
    }

    private String buildMetricFactId(String runId,
                                     String sourceObjectType,
                                     String sourceObjectKey,
                                     String metricKey,
                                     String dimensionKey) {
        return "ka-metric-fact-" + normalizeStorageKey(runId)
                + "-" + normalizeStorageKey(sourceObjectType)
                + "-" + normalizeStorageKey(sourceObjectKey)
                + "-" + normalizeStorageKey(metricKey)
                + "-" + normalizeStorageKey(dimensionKey);
    }

    private String buildDimensionKey(Map<String, Object> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "all";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : dimensions.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String normalizedValue = normalizeStorageKey(String.valueOf(value));
            if (StringUtils.hasText(normalizedValue) && !"unknown".equals(normalizedValue)) {
                parts.add(normalizeStorageKey(entry.getKey()) + ":" + normalizedValue);
            }
        }
        return parts.isEmpty() ? "all" : String.join("|", parts);
    }

    private LinkedHashMap<String, Object> metricMap(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object value = values[index + 1];
            if (value != null) {
                result.put(String.valueOf(values[index]), value);
            }
        }
        return result;
    }

    private String buildProblemAggregateKey(String problemSpaceBucket,
                                            String topicCatalogId,
                                            String clusterKey,
                                            String signalType) {
        return normalizeStorageKey(problemSpaceBucket)
                + "|" + normalizeStorageKey(topicCatalogId)
                + "|" + normalizeStorageKey(clusterKey)
                + "|" + normalizeStorageKey(signalType);
    }

    private String buildRunId(String sourceTaskId) {
        return "ka-run-" + sourceTaskId;
    }

    private String buildRunEventId(String sourceTaskId, Long sourceEventSeq) {
        return "ka-run-event-" + normalizeStorageKey(sourceTaskId) + "-" + (sourceEventSeq == null ? "unknown" : sourceEventSeq);
    }

    private String buildCheckpointId(String sourceTaskId) {
        return "ka-checkpoint-" + sourceTaskId;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private String firstText(String first, String second, String third) {
        String resolved = firstText(first, second);
        return StringUtils.hasText(resolved) ? resolved : firstText(third, null);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String responsePreview(String rawResponseText, Map<String, Object> metadata) {
        String metadataPreview = metadata == null ? null : normalizeText(asText(metadata.get("responsePreview")));
        if (StringUtils.hasText(metadataPreview)) {
            return abbreviate(metadataPreview, 500);
        }
        return abbreviate(rawResponseText, 500);
    }

    private String abbreviate(String raw, int maxLength) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized) || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String signalTypeForFactSource(String factSource) {
        String normalized = defaultIfBlank(factSource, "");
        return switch (normalized) {
            case FACT_SOURCE_CLUSTER_PROFILE -> SIGNAL_TYPE_CLUSTER_PROFILE;
            case FACT_SOURCE_CLUSTER_BREAKDOWN -> SIGNAL_TYPE_CLUSTER_BREAKDOWN;
            case FACT_SOURCE_NEGATIVE_SIGNAL -> SIGNAL_TYPE_NEGATIVE_SIGNAL;
            default -> SIGNAL_TYPE_PROBE_BUCKET;
        };
    }

    private String resolveOwnershipKey(String domainName, Set<String> ownedDomains) {
        return isOwnedDomain(domainName, ownedDomains) ? OWNERSHIP_KEY_OWNED : OWNERSHIP_KEY_EXTERNAL;
    }

    private String resolveSourceType(String sourceTypeCode, String domainName, Set<String> ownedDomains) {
        if (isOwnedDomain(domainName, ownedDomains)) {
            return SOURCE_TYPE_OWNED;
        }
        String normalizedDomain = normalizeDomain(domainName);
        if (StringUtils.hasText(normalizedDomain)) {
            if (containsAny(normalizedDomain, "tieba", "bbs", "forum", "club.", "zhihu", "xiaohongshu")) {
                return SOURCE_TYPE_UGC;
            }
            if (containsAny(normalizedDomain, "wiki", "baike")) {
                return SOURCE_TYPE_REFERENCE;
            }
            if (containsAny(normalizedDomain, "news", "media", "toutiao", "ifeng", "163.com", "sohu.com", "qq.com")) {
                return SOURCE_TYPE_EDITORIAL;
            }
        }
        String normalized = normalizeText(sourceTypeCode);
        if (!StringUtils.hasText(normalized)) {
            return SOURCE_TYPE_REFERENCE;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "official", "brand", "corporate", "corp", "官网", "品牌", "企业")) {
            return SOURCE_TYPE_CORPORATE;
        }
        if (containsAny(lower, "media", "news", "editorial", "article", "媒体", "资讯", "新闻")) {
            return SOURCE_TYPE_EDITORIAL;
        }
        if (containsAny(lower, "social", "forum", "ugc", "community", "评价", "口碑", "问答", "帖子", "讨论")) {
            return SOURCE_TYPE_UGC;
        }
        return SOURCE_TYPE_REFERENCE;
    }

    private boolean isOwnedDomain(String domainName, Set<String> ownedDomains) {
        String normalizedDomain = normalizeDomain(domainName);
        if (!StringUtils.hasText(normalizedDomain) || ownedDomains == null || ownedDomains.isEmpty()) {
            return false;
        }
        for (String ownedDomain : ownedDomains) {
            String normalizedOwnedDomain = normalizeDomain(ownedDomain);
            if (!StringUtils.hasText(normalizedOwnedDomain)) {
                continue;
            }
            if (normalizedDomain.equals(normalizedOwnedDomain)
                    || normalizedDomain.endsWith("." + normalizedOwnedDomain)) {
                return true;
            }
            String withoutWww = normalizedDomain.startsWith("www.")
                    ? normalizedDomain.substring(4)
                    : normalizedDomain;
            if (withoutWww.equals(normalizedOwnedDomain)
                    || withoutWww.endsWith("." + normalizedOwnedDomain)) {
                return true;
            }
        }
        return false;
    }

    private String asText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return text;
        }
        return String.valueOf(raw);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeDomain(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeStorageKey(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return "unknown";
        }
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("^https?://", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    private String normalizeBrandKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", "");
        normalized = normalized.replace("（", "(").replace("）", ")");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]", "");
        return normalized;
    }

    private String resolveProblemSpaceBucket(String structuralBucket) {
        String normalized = normalizeText(structuralBucket);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "orthogonal" -> "orthogonal";
            case "pareto" -> "pareto";
            case "adversarial" -> "adversarial";
            default -> normalized;
        };
    }

    private Double readPercent(Object raw, boolean ratioToPercent) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = BigDecimal.valueOf(number.doubleValue());
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                value = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value == null) {
            return null;
        }
        if (ratioToPercent && value.compareTo(BigDecimal.ONE) <= 0) {
            value = value.multiply(BigDecimal.valueOf(100));
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double readNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = BigDecimal.valueOf(number.doubleValue());
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                value = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String domainOf(String raw) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String withoutProtocol = normalized.replaceFirst("^https?://", "");
        int slashIndex = withoutProtocol.indexOf('/');
        String host = slashIndex >= 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
        return normalizeDomain(host);
    }

    private Integer readInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (Object item : rawList) {
            String text = normalizeText(asText(item));
            if (StringUtils.hasText(text)) {
                deduplicated.add(text);
            }
        }
        return List.copyOf(deduplicated);
    }

    private Double readBlindSpotScore(Object raw) {
        if (raw instanceof Boolean blindSpot) {
            return blindSpot ? 25.0d : 75.0d;
        }
        return null;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                mapped.put(String.valueOf(key), value);
            }
        });
        return mapped;
    }

    private int estimateRateCount(Integer sampleSize, Double percentValue) {
        if (sampleSize == null || sampleSize <= 0 || percentValue == null) {
            return 0;
        }
        return (int) Math.round(sampleSize * percentValue / 100.0d);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private final class CompetitorWindowAccumulator {

        private final String competitorKey;
        private String competitorName;
        private final boolean configuredFlag;
        private int runCount;
        private int mentionProbeCount;
        private int recommendProbeCount;
        private final List<Double> visibilityScores = new ArrayList<>();
        private final List<Double> recommendRates = new ArrayList<>();
        private final List<Double> mentionRates = new ArrayList<>();
        private final List<Double> top3Rates = new ArrayList<>();
        private final List<Double> averagePositions = new ArrayList<>();

        private CompetitorWindowAccumulator(String competitorKey,
                                            String competitorName,
                                            boolean configuredFlag) {
            this.competitorKey = competitorKey;
            this.competitorName = competitorName;
            this.configuredFlag = configuredFlag;
        }

        private void updateName(String candidateName) {
            if (!StringUtils.hasText(this.competitorName) && StringUtils.hasText(candidateName)) {
                this.competitorName = candidateName.trim();
            }
        }

        private void addSnapshot(KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView snapshotFact,
                                 Integer sampleSize) {
            runCount += 1;
            mentionProbeCount += snapshotFact.mentionCount() == null ? 0 : snapshotFact.mentionCount();
            recommendProbeCount += estimateRateCount(sampleSize, snapshotFact.recommendRate());
            addIfPresent(visibilityScores, snapshotFact.visibilityScore());
            addIfPresent(recommendRates, snapshotFact.recommendRate());
            addIfPresent(mentionRates, snapshotFact.mentionRate());
            addIfPresent(top3Rates, snapshotFact.top3Rate());
            addIfPresent(averagePositions, snapshotFact.averagePosition());
        }

        private KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert toUpsert(String brandScopeId,
                                                                                        String windowKey,
                                                                                        int windowRunCount) {
            return new KnowledgeAssetCompetitorRepository.CompetitorWindowFactUpsert(
                    "ka-competitor-window-" + brandScopeId + "-" + windowKey + "-" + competitorKey,
                    competitorKey,
                    competitorName,
                    configuredFlag,
                    runCount,
                    mentionProbeCount,
                    recommendProbeCount,
                    averageOf(visibilityScores),
                    averageOf(recommendRates),
                    averageOf(mentionRates),
                    averageOf(top3Rates),
                    averageOf(averagePositions),
                    toJson(Map.of(
                            "windowKey", windowKey,
                            "windowRunCount", windowRunCount,
                            "appearanceRunCount", runCount,
                            "configuredFlag", configuredFlag
                    ))
            );
        }

        private void addIfPresent(List<Double> bucket, Double value) {
            if (value != null) {
                bucket.add(value);
            }
        }

        private Double averageOf(List<Double> values) {
            if (values.isEmpty()) {
                return null;
            }
            double sum = 0.0d;
            for (Double value : values) {
                sum += value;
            }
            return round2(sum / values.size());
        }
    }

    private final class SourceAggregationAccumulator {

        private final String sourceKey;
        private final String domainName;
        private String sourceType;
        private String ownershipKey;
        private int citationCount;
        private final LinkedHashSet<String> runIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> urls = new LinkedHashSet<>();
        private final LinkedHashSet<String> titles = new LinkedHashSet<>();

        private SourceAggregationAccumulator(String sourceKey, String domainName) {
            this.sourceKey = sourceKey;
            this.domainName = domainName;
        }

        private void addCitation(String runId,
                                 String url,
                                 String title,
                                 String sourceType,
                                 String ownershipKey) {
            citationCount += 1;
            if (StringUtils.hasText(runId)) {
                runIds.add(runId.trim());
            }
            if (StringUtils.hasText(url)) {
                urls.add(url.trim());
            }
            if (StringUtils.hasText(title)) {
                titles.add(title.trim());
            }
            if (!StringUtils.hasText(this.sourceType) && StringUtils.hasText(sourceType)) {
                this.sourceType = sourceType.trim();
            }
            if (!StringUtils.hasText(this.ownershipKey) && StringUtils.hasText(ownershipKey)) {
                this.ownershipKey = ownershipKey.trim();
            }
        }

        private int citationCount() {
            return citationCount;
        }

        private KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert toSourceSnapshotFactUpsert(String runId,
                                                                                                   String brandScopeId,
                                                                                                   int totalCitationCount) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.put("sourceKey", sourceKey);
            payload.put("domainName", domainName);
            payload.put("topUrls", urls.stream().limit(10).toList());
            payload.put("titleSamples", titles.stream().limit(5).toList());
            payload.put("ownershipKey", ownershipKey);
            return new KnowledgeAssetSourceRepository.SourceSnapshotFactUpsert(
                    "ka-source-snapshot-" + runId + "-" + normalizeStorageKey(domainName),
                    sourceKey,
                    domainName,
                    defaultIfBlank(sourceType, SOURCE_TYPE_REFERENCE),
                    defaultIfBlank(ownershipKey, OWNERSHIP_KEY_EXTERNAL),
                    citationCount,
                    totalCitationCount <= 0 ? 0.0d : round2(citationCount * 100.0d / totalCitationCount),
                    toJson(payload)
            );
        }

        private KnowledgeAssetSourceRepository.SourceWindowFactUpsert toSourceWindowFactUpsert(String brandScopeId,
                                                                                               String windowKey,
                                                                                               int windowRunCount,
                                                                                               int totalCitationCount) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("windowKey", windowKey);
            payload.put("windowRunCount", windowRunCount);
            payload.put("sourceKey", sourceKey);
            payload.put("usedRunIds", List.copyOf(runIds));
            payload.put("topUrls", urls.stream().limit(10).toList());
            payload.put("titleSamples", titles.stream().limit(5).toList());
            return new KnowledgeAssetSourceRepository.SourceWindowFactUpsert(
                    "ka-source-window-" + brandScopeId + "-" + windowKey + "-" + normalizeStorageKey(domainName),
                    sourceKey,
                    domainName,
                    defaultIfBlank(sourceType, SOURCE_TYPE_REFERENCE),
                    defaultIfBlank(ownershipKey, OWNERSHIP_KEY_EXTERNAL),
                    citationCount,
                    runIds.size(),
                    totalCitationCount <= 0 ? 0.0d : round2(citationCount * 100.0d / totalCitationCount),
                    toJson(payload)
            );
        }
    }

    private final class ProblemWindowAccumulator {

        private final String problemSpaceBucket;
        private final String topicCatalogId;
        private final String clusterKey;
        private final String factSource;
        private int runCount;
        private int promptCount;
        private int probeCount;
        private int attemptCount;
        private int evidenceCount;
        private final List<Double> signalScores = new ArrayList<>();

        private ProblemWindowAccumulator(String problemSpaceBucket,
                                         String topicCatalogId,
                                         String clusterKey,
                                         String factSource) {
            this.problemSpaceBucket = problemSpaceBucket;
            this.topicCatalogId = topicCatalogId;
            this.clusterKey = clusterKey;
            this.factSource = factSource;
        }

        private void addSnapshot(KnowledgeAssetProblemRepository.ProblemSnapshotFactView snapshotFact) {
            runCount += 1;
            promptCount += snapshotFact.promptCount() == null ? 0 : snapshotFact.promptCount();
            probeCount += snapshotFact.probeCount() == null ? 0 : snapshotFact.probeCount();
            attemptCount += snapshotFact.attemptCount() == null ? 0 : snapshotFact.attemptCount();
            evidenceCount += snapshotFact.evidenceCount() == null ? 0 : snapshotFact.evidenceCount();
            if (snapshotFact.signalScore() != null) {
                signalScores.add(snapshotFact.signalScore());
            }
        }

        private KnowledgeAssetProblemRepository.ProblemWindowFactUpsert toUpsert(String brandScopeId,
                                                                                 String windowKey,
                                                                                 int windowRunCount) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("windowKey", windowKey);
            payload.put("windowRunCount", windowRunCount);
            payload.put("clusterKey", clusterKey);
            payload.put("topicCatalogId", topicCatalogId);
            payload.put("factSource", factSource);
            return new KnowledgeAssetProblemRepository.ProblemWindowFactUpsert(
                    "ka-problem-window-" + brandScopeId + "-" + windowKey
                            + "-" + normalizeStorageKey(problemSpaceBucket)
                            + "-" + normalizeStorageKey(topicCatalogId) + "-" + normalizeStorageKey(clusterKey)
                            + "-" + normalizeStorageKey(factSource),
                    problemSpaceBucket,
                    topicCatalogId,
                    clusterKey,
                    factSource,
                    runCount,
                    promptCount,
                    probeCount,
                    attemptCount,
                    evidenceCount,
                    averageOf(signalScores),
                    toJson(payload)
            );
        }

        private Double averageOf(List<Double> values) {
            if (values.isEmpty()) {
                return null;
            }
            double sum = 0.0d;
            for (Double value : values) {
                sum += value;
            }
            return round2(sum / values.size());
        }
    }

    private final class ProbeBucketAccumulator {

        private final String problemSpaceBucket;
        private final String topicCatalogId;
        private final String clusterKey;
        private int probeCount;
        private int successfulProbeCount;
        private final LinkedHashSet<String> promptCatalogIds = new LinkedHashSet<>();

        private ProbeBucketAccumulator(String problemSpaceBucket,
                                       String topicCatalogId,
                                       String clusterKey) {
            this.problemSpaceBucket = problemSpaceBucket;
            this.topicCatalogId = topicCatalogId;
            this.clusterKey = clusterKey;
        }

        private void addProbe(String promptCatalogId, Integer statusCode) {
            probeCount += 1;
            if (StringUtils.hasText(promptCatalogId)) {
                promptCatalogIds.add(promptCatalogId.trim());
            }
            if (Integer.valueOf(2).equals(statusCode) || Integer.valueOf(1).equals(statusCode)) {
                successfulProbeCount += 1;
            }
        }

        private KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert toSnapshotFact(String runId) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("factSource", FACT_SOURCE_PROBE_BUCKET);
            payload.put("promptCatalogIds", List.copyOf(promptCatalogIds));
            payload.put("successfulProbeCount", successfulProbeCount);
            payload.put("clusterKey", clusterKey);
            return new KnowledgeAssetProblemRepository.ProblemSnapshotFactUpsert(
                    buildProblemSnapshotFactId(runId, problemSpaceBucket, topicCatalogId, clusterKey, FACT_SOURCE_PROBE_BUCKET),
                    problemSpaceBucket,
                    topicCatalogId,
                    clusterKey,
                    FACT_SOURCE_PROBE_BUCKET,
                    promptCatalogIds.size(),
                    probeCount,
                    0,
                    0,
                    probeCount <= 0 ? null : round2(successfulProbeCount * 100.0d / probeCount),
                    toJson(payload)
            );
        }
    }

    private static final class PlatformMetricAccumulator {

        private final String sourceType;
        private int citationCount;
        private int shareRateCount;
        private double shareRateSum;

        private PlatformMetricAccumulator(String sourceType) {
            this.sourceType = sourceType;
        }

        private void add(Integer citationCount, Double shareRate) {
            this.citationCount += citationCount == null ? 0 : citationCount;
            if (shareRate != null) {
                shareRateSum += shareRate;
                shareRateCount += 1;
            }
        }

        private Double averageShareRate() {
            if (shareRateCount <= 0) {
                return null;
            }
            return BigDecimal.valueOf(shareRateSum / shareRateCount)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    private final class DerivedCompetitorMetricAccumulator {

        private final String brandName;
        private final Set<String> mentionedProbeKeys = new LinkedHashSet<>();
        private final Set<String> recommendedProbeKeys = new LinkedHashSet<>();
        private final Set<String> top3ProbeKeys = new LinkedHashSet<>();
        private int mentionCount;
        private double positionedCount;
        private double positionSum;

        private DerivedCompetitorMetricAccumulator(String brandName) {
            this.brandName = brandName;
        }

        private void add(String probeKey, boolean recommended, Integer positionIndex) {
            mentionCount += 1;
            mentionedProbeKeys.add(probeKey);
            if (recommended) {
                recommendedProbeKeys.add(probeKey);
            }
            if (positionIndex != null) {
                if (positionIndex <= 3) {
                    top3ProbeKeys.add(probeKey);
                }
                positionedCount += 1.0d;
                positionSum += positionIndex;
            }
        }

        private DerivedCompetitorMetricSnapshot snapshot(int denominator) {
            Double mentionRate = denominator <= 0 ? null : round2(mentionedProbeKeys.size() * 100.0d / denominator);
            Double recommendRate = denominator <= 0 ? null : round2(recommendedProbeKeys.size() * 100.0d / denominator);
            Double top3Rate = denominator <= 0 ? null : round2(top3ProbeKeys.size() * 100.0d / denominator);
            Double averagePosition = positionedCount <= 0.0d ? null : round2(positionSum / positionedCount);
            return new DerivedCompetitorMetricSnapshot(
                    brandName,
                    mentionRate,
                    recommendRate,
                    top3Rate,
                    averagePosition,
                    mentionCount
            );
        }
    }

    private record DerivedCompetitorMetricSnapshot(String brandName,
                                                   Double mentionRate,
                                                   Double recommendRate,
                                                   Double top3Rate,
                                                   Double averagePosition,
                                                   Integer mentionCount) {
    }

    private static final class ModelMetricAccumulator {

        private final String provider;
        private final String modelId;
        private int attemptCount;
        private int finalAttemptCount;

        private ModelMetricAccumulator(String provider, String modelId) {
            this.provider = provider;
            this.modelId = modelId;
        }

        private void add(ProbeDispatchAttempt attempt) {
            attemptCount += 1;
            if (attempt != null && Boolean.TRUE.equals(attempt.getSelectedFinal())) {
                finalAttemptCount += 1;
            }
        }
    }

    private static final class SentimentMetricAccumulator {

        private final String brandKey;
        private final String brandName;
        private final Integer brandRoleCode;
        private final Integer sentimentCode;
        private int mentionCount;

        private SentimentMetricAccumulator(String brandKey,
                                           String brandName,
                                           Integer brandRoleCode,
                                           Integer sentimentCode) {
            this.brandKey = brandKey;
            this.brandName = brandName;
            this.brandRoleCode = brandRoleCode;
            this.sentimentCode = sentimentCode;
        }

        private void add(ProbeBrandMentionRecord mention) {
            if (mention == null || Boolean.TRUE.equals(mention.getMentionedFlag())) {
                mentionCount += 1;
            }
        }
    }

    private final class TopicMetricAccumulator {

        private final String topicCatalogId;
        private final String topicName;
        private final String problemSpaceBucket;
        private final String sourceTopicId;
        private final Set<String> sampleProbeIds = new LinkedHashSet<>();
        private final Set<String> mentionedProbeIds = new LinkedHashSet<>();
        private final Set<String> recommendedProbeIds = new LinkedHashSet<>();
        private final Set<String> top3ProbeIds = new LinkedHashSet<>();
        private int mentionCount;
        private int positionedCount;
        private double positionSum;

        private TopicMetricAccumulator(String topicCatalogId,
                                       String topicName,
                                       String problemSpaceBucket,
                                       String sourceTopicId) {
            this.topicCatalogId = topicCatalogId;
            this.topicName = topicName;
            this.problemSpaceBucket = problemSpaceBucket;
            this.sourceTopicId = sourceTopicId;
        }

        private void addSampleProbes(Set<String> probeIds) {
            if (probeIds == null || probeIds.isEmpty()) {
                return;
            }
            sampleProbeIds.addAll(probeIds);
        }

        private void addMention(KnowledgeAssetEvidenceRepository.BrandMentionView brandMention) {
            if (brandMention == null
                    || !Boolean.TRUE.equals(brandMention.mentionedFlag())
                    || !StringUtils.hasText(brandMention.probeId())) {
                return;
            }
            mentionCount += 1;
            mentionedProbeIds.add(brandMention.probeId());
            if (Boolean.TRUE.equals(brandMention.recommendedFlag())) {
                recommendedProbeIds.add(brandMention.probeId());
            }
            if (brandMention.positionIndex() != null) {
                if (brandMention.positionIndex() <= 3) {
                    top3ProbeIds.add(brandMention.probeId());
                }
                positionedCount += 1;
                positionSum += brandMention.positionIndex();
            }
        }

        private TopicMetricSnapshot snapshot() {
            int sampleSize = sampleProbeIds.size();
            Double mentionRate = sampleSize <= 0 ? null : round2(mentionedProbeIds.size() * 100.0d / sampleSize);
            Double recommendRate = sampleSize <= 0 ? null : round2(recommendedProbeIds.size() * 100.0d / sampleSize);
            Double top3Rate = sampleSize <= 0 ? null : round2(top3ProbeIds.size() * 100.0d / sampleSize);
            Double averagePosition = positionedCount <= 0 ? null : round2(positionSum / positionedCount);
            return new TopicMetricSnapshot(
                    sampleSize,
                    resolveVisibilityScore(mentionRate, recommendRate, top3Rate),
                    mentionRate,
                    recommendRate,
                    top3Rate,
                    averagePosition,
                    mentionCount,
                    positionedCount
            );
        }
    }

    private record TopicMetricSnapshot(int sampleSize,
                                       Double visibilityScore,
                                       Double mentionRate,
                                       Double recommendRate,
                                       Double top3Rate,
                                       Double averagePosition,
                                       int mentionCount,
                                       int positionedCount) {
    }

    private record ProbeCatalogBinding(String topicCatalogId,
                                       String promptCatalogId,
                                       String problemSpaceBucket,
                                       String sourceTopicId,
                                       String sourcePromptId) {
    }

    private record EvidenceEventSyncResult(int probeCount,
                                           int attemptCount,
                                           int brandMentionCount) {
    }

    private record SourceAssetSyncResult(int sourceAssetCount,
                                         int citationCount,
                                         int snapshotDomainCount) {
    }

    private record ProblemAssetSyncResult(int topicCatalogCount,
                                          int promptCatalogCount,
                                          int signalCount,
                                          int snapshotFactCount) {
    }

    private record MetricAssetSyncResult(int registryCount,
                                         int metricFactCount,
                                         int windowFactCount) {
    }

    public record KnowledgeAssetIngestionResult(String sourceTaskId,
                                                String brandScopeId,
                                                String sourceReportId,
                                                boolean synced,
                                                boolean skipped,
                                                String reason) {

        public static KnowledgeAssetIngestionResult skipped(String sourceTaskId, String reason) {
            return new KnowledgeAssetIngestionResult(sourceTaskId, null, null, false, true, reason);
        }
    }
}
