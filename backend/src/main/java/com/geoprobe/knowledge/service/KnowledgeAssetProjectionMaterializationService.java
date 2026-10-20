package com.geoprobe.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.knowledge.repository.KnowledgeAssetCompetitorRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProjectionRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetProjectionMaterializationService {

    private static final String WINDOW_KEY_LAST_14_DAYS = "last_14_days";
    private static final String PROJECTION_KEY_DEFAULT = "default";
    private static final String PROJECTION_VERSION = "phase1.v1";
    private static final String OWNERSHIP_OWNED = "OWNED";
    private static final String METRIC_TOPIC = "topic";
    private static final String METRIC_SAMPLE_SIZE = "kpi.sample_size";
    private static final String METRIC_VISIBILITY_SCORE = "visibility.score";
    private static final String METRIC_VISIBILITY_MENTION_RATE = "visibility.mention_rate";
    private static final String METRIC_VISIBILITY_RECOMMEND_RATE = "visibility.recommend_rate";
    private static final String METRIC_VISIBILITY_TOP3_RATE = "visibility.top3_rate";
    private static final String METRIC_VISIBILITY_AVERAGE_POSITION = "visibility.average_position";
    private static final String METRIC_VISIBILITY_MENTION_COUNT = "visibility.mention_count";

    private final KnowledgeAssetProperties knowledgeAssetProperties;
    private final KnowledgeAssetRunRepository knowledgeAssetRunRepository;
    private final KnowledgeAssetCompetitorRepository knowledgeAssetCompetitorRepository;
    private final KnowledgeAssetSourceRepository knowledgeAssetSourceRepository;
    private final KnowledgeAssetEvidenceRepository knowledgeAssetEvidenceRepository;
    private final KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;
    private final KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;
    private final KnowledgeAssetProjectionRepository knowledgeAssetProjectionRepository;
    private final ObjectMapper objectMapper;

    public ProjectionMaterializationResult materializeScope(String brandScopeId) {
        if (!knowledgeAssetProperties.getProjection().isMaterializationEnabled()) {
            return ProjectionMaterializationResult.skipped(brandScopeId, "projection_materialization_disabled");
        }
        if (!StringUtils.hasText(brandScopeId)) {
            return ProjectionMaterializationResult.skipped(null, "missing_brand_scope_id");
        }

        KnowledgeAssetRunRepository.LatestRunView latestRun = knowledgeAssetRunRepository.findLatestRunByScope(brandScopeId);
        if (latestRun == null) {
            return ProjectionMaterializationResult.skipped(brandScopeId, "knowledge_asset_run_not_found");
        }

        List<String> materializedDomains = new ArrayList<>();
        if (knowledgeAssetProperties.getProjection().isBrandPerformanceEnabled()) {
            knowledgeAssetProjectionRepository.upsertBrandPerformanceProjection(
                    new KnowledgeAssetProjectionRepository.ProjectionUpsert(
                            projectionId("brand-performance", brandScopeId),
                            brandScopeId,
                            projectionKey("brand-performance"),
                            PROJECTION_VERSION,
                            WINDOW_KEY_LAST_14_DAYS,
                            latestRun.runId(),
                            toJson(buildBrandPerformancePayload(brandScopeId, latestRun))
                    )
            );
            materializedDomains.add("brand-performance");
        }
        if (knowledgeAssetProperties.getProjection().isCitationSourcesEnabled()) {
            knowledgeAssetProjectionRepository.upsertCitationSourcesProjection(
                    new KnowledgeAssetProjectionRepository.ProjectionUpsert(
                            projectionId("citation-sources", brandScopeId),
                            brandScopeId,
                            projectionKey("citation-sources"),
                            PROJECTION_VERSION,
                            WINDOW_KEY_LAST_14_DAYS,
                            latestRun.runId(),
                            toJson(buildCitationSourcesPayload(brandScopeId, latestRun))
                    )
            );
            materializedDomains.add("citation-sources");
        }
        if (knowledgeAssetProperties.getProjection().isProblemAnalysisEnabled()) {
            knowledgeAssetProjectionRepository.upsertProblemAnalysisProjection(
                    new KnowledgeAssetProjectionRepository.ProjectionUpsert(
                            projectionId("problem-analysis", brandScopeId),
                            brandScopeId,
                            projectionKey("problem-analysis"),
                            PROJECTION_VERSION,
                            WINDOW_KEY_LAST_14_DAYS,
                            latestRun.runId(),
                            toJson(buildProblemAnalysisPayload(brandScopeId, latestRun))
                    )
            );
            materializedDomains.add("problem-analysis");
        }
        if (knowledgeAssetProperties.getProjection().isMetricSlicesEnabled()) {
            knowledgeAssetProjectionRepository.upsertMetricSlicesProjection(
                    new KnowledgeAssetProjectionRepository.ProjectionUpsert(
                            projectionId("metric-slices", brandScopeId),
                            brandScopeId,
                            projectionKey("metric-slices"),
                            PROJECTION_VERSION,
                            WINDOW_KEY_LAST_14_DAYS,
                            latestRun.runId(),
                            toJson(buildMetricSlicesPayload(brandScopeId, latestRun))
                    )
            );
            materializedDomains.add("metric-slices");
        }

        return new ProjectionMaterializationResult(
                brandScopeId,
                latestRun.runId(),
                false,
                materializedDomains,
                null
        );
    }

    private Map<String, Object> buildBrandPerformancePayload(String brandScopeId,
                                                             KnowledgeAssetRunRepository.LatestRunView latestRun) {
        List<KnowledgeAssetCompetitorRepository.ConfiguredCompetitorView> configuredCompetitors =
                knowledgeAssetCompetitorRepository.listActiveConfiguredCompetitors(brandScopeId);
        List<KnowledgeAssetCompetitorRepository.CompetitorWindowFactView> windowFacts =
                knowledgeAssetCompetitorRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        List<KnowledgeAssetCompetitorRepository.CompetitorSnapshotFactView> latestSnapshotFacts =
                knowledgeAssetCompetitorRepository.listSnapshotFactsByRunIds(List.of(latestRun.runId()));
        List<KnowledgeAssetProblemRepository.TopicCatalogView> topics =
                knowledgeAssetProblemRepository.listActiveTopics(brandScopeId);
        List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts =
                knowledgeAssetProblemRepository.listActivePrompts(brandScopeId);
        List<KnowledgeAssetProblemRepository.ProblemWindowFactView> problemWindowFacts =
                knowledgeAssetProblemRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts =
                knowledgeAssetMetricRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);

        LinkedHashMap<String, Object> payload = basePayload("brand-performance", brandScopeId, latestRun);
        payload.put("configuredCompetitorCount", configuredCompetitors.size());
        payload.put("observedCompetitorCount", latestSnapshotFacts.size());
        payload.put("windowCompetitorCount", windowFacts.size());
        payload.put("configuredCompetitors", configuredCompetitors.stream()
                .map(item -> linkedMap(
                        "competitorKey", item.competitorKey(),
                        "competitorName", item.competitorName()
                ))
                .toList());
        payload.put("windowCompetitorRows", windowFacts.stream()
                .map(item -> linkedMap(
                        "competitorKey", item.competitorKey(),
                        "competitorName", item.competitorName(),
                        "configuredFlag", item.configuredFlag(),
                        "runCount", safeInt(item.runCount()),
                        "mentionProbeCount", safeInt(item.mentionProbeCount()),
                        "recommendProbeCount", safeInt(item.recommendProbeCount()),
                        "avgVisibilityScore", item.avgVisibilityScore(),
                        "avgRecommendRate", item.avgRecommendRate(),
                        "avgMentionRate", item.avgMentionRate(),
                        "avgTop3Rate", item.avgTop3Rate(),
                        "avgAveragePosition", item.avgAveragePosition()
                ))
                .toList());
        payload.put("latestSnapshotRows", latestSnapshotFacts.stream()
                .map(item -> linkedMap(
                        "competitorKey", item.competitorKey(),
                        "competitorName", item.competitorName(),
                        "rankNo", item.rankNo(),
                        "visibilityScore", item.visibilityScore(),
                        "recommendRate", item.recommendRate(),
                        "mentionRate", item.mentionRate(),
                        "top3Rate", item.top3Rate(),
                        "averagePosition", item.averagePosition(),
                        "mentionCount", item.mentionCount()
                ))
                .toList());
        payload.put("topicRows", buildBrandPerformanceTopicRows(topics, prompts, problemWindowFacts, metricWindowFacts));
        return payload;
    }

    private List<LinkedHashMap<String, Object>> buildBrandPerformanceTopicRows(
            List<KnowledgeAssetProblemRepository.TopicCatalogView> topics,
            List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts,
            List<KnowledgeAssetProblemRepository.ProblemWindowFactView> problemWindowFacts,
            List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        Map<String, TopicMetricRow> metricByTopicId = indexTopicMetricRows(metricWindowFacts);
        if (metricByTopicId.isEmpty()) {
            return List.of();
        }
        Map<String, List<KnowledgeAssetProblemRepository.PromptCatalogView>> promptsByTopicId = new LinkedHashMap<>();
        if (prompts != null) {
            for (KnowledgeAssetProblemRepository.PromptCatalogView prompt : prompts) {
                promptsByTopicId.computeIfAbsent(prompt.topicCatalogId(), ignored -> new ArrayList<>()).add(prompt);
            }
        }
        Map<String, Integer> evidenceCountByTopicId = new LinkedHashMap<>();
        if (problemWindowFacts != null) {
            for (KnowledgeAssetProblemRepository.ProblemWindowFactView fact : problemWindowFacts) {
                if (!StringUtils.hasText(fact.topicCatalogId())) {
                    continue;
                }
                evidenceCountByTopicId.merge(fact.topicCatalogId(), safeInt(fact.evidenceCount()), Integer::sum);
            }
        }
        int totalPromptCount = prompts == null ? 0 : prompts.size();
        int totalSignalCount = evidenceCountByTopicId.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return topics.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.topicCatalogId()))
                .map(item -> {
                    TopicMetricRow metricRow = metricByTopicId.getOrDefault(item.topicCatalogId(), TopicMetricRow.empty());
                    List<KnowledgeAssetProblemRepository.PromptCatalogView> topicPrompts =
                            promptsByTopicId.getOrDefault(item.topicCatalogId(), List.of());
                    int promptCount = topicPrompts.size();
                    int signalCount = safeInt(evidenceCountByTopicId.get(item.topicCatalogId()));
                    return linkedMap(
                            "topicCatalogId", item.topicCatalogId(),
                            "sourceTopicId", item.sourceTopicId(),
                            "topicName", item.topicName(),
                            "problemSpaceBucket", item.problemSpaceBucket(),
                            "seqNo", resolveTopicSeqNo(topicPrompts),
                            "enabled", item.activeFlag(),
                            "sampleSize", metricRow.sampleSize(),
                            "visibilityScore", metricRow.visibilityScore(),
                            "mentionRate", metricRow.mentionRate(),
                            "recommendRate", metricRow.recommendRate(),
                            "top3Rate", metricRow.top3Rate(),
                            "averagePosition", metricRow.averagePosition(),
                            "mentionCount", metricRow.mentionCount(),
                            "promptCount", promptCount,
                            "enabledPromptCount", promptCount,
                            "promptShare", totalPromptCount <= 0 ? null : promptCount * 100.0d / totalPromptCount,
                            "signalCount", signalCount,
                            "signalShare", totalSignalCount <= 0 ? null : signalCount * 100.0d / totalSignalCount,
                            "sampleQuestions", topicPrompts.stream()
                                    .map(KnowledgeAssetProblemRepository.PromptCatalogView::promptText)
                                    .filter(StringUtils::hasText)
                                    .limit(3)
                                    .toList()
                    );
                })
                .sorted((left, right) -> {
                    int signalCompare = Integer.compare(
                            safeInt((Integer) right.get("signalCount")),
                            safeInt((Integer) left.get("signalCount"))
                    );
                    if (signalCompare != 0) {
                        return signalCompare;
                    }
                    Integer rightSampleSize = (Integer) right.get("sampleSize");
                    Integer leftSampleSize = (Integer) left.get("sampleSize");
                    int sampleCompare = Integer.compare(
                            rightSampleSize == null ? 0 : rightSampleSize,
                            leftSampleSize == null ? 0 : leftSampleSize
                    );
                    if (sampleCompare != 0) {
                        return sampleCompare;
                    }
                    Integer leftSeqNo = (Integer) left.get("seqNo");
                    Integer rightSeqNo = (Integer) right.get("seqNo");
                    if (leftSeqNo == null && rightSeqNo == null) {
                        return defaultText(String.valueOf(left.get("topicName")))
                                .compareTo(defaultText(String.valueOf(right.get("topicName"))));
                    }
                    if (leftSeqNo == null) {
                        return 1;
                    }
                    if (rightSeqNo == null) {
                        return -1;
                    }
                    return Integer.compare(leftSeqNo, rightSeqNo);
                })
                .toList();
    }

    private Map<String, TopicMetricRow> indexTopicMetricRows(List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts) {
        if (metricWindowFacts == null || metricWindowFacts.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, MutableTopicMetricRow> metricByTopicId = new LinkedHashMap<>();
        for (KnowledgeAssetMetricRepository.MetricWindowFactView fact : metricWindowFacts) {
            if (fact == null
                    || !METRIC_TOPIC.equals(defaultText(fact.sourceObjectType()))
                    || !StringUtils.hasText(fact.sourceObjectKey())) {
                continue;
            }
            MutableTopicMetricRow row = metricByTopicId.computeIfAbsent(
                    fact.sourceObjectKey(),
                    ignored -> new MutableTopicMetricRow()
            );
            row.apply(fact.metricKey(), fact.numericValue());
        }
        LinkedHashMap<String, TopicMetricRow> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableTopicMetricRow> entry : metricByTopicId.entrySet()) {
            result.put(entry.getKey(), entry.getValue().freeze());
        }
        return result;
    }

    private Integer resolveTopicSeqNo(List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return null;
        }
        return prompts.stream()
                .map(KnowledgeAssetProblemRepository.PromptCatalogView::seqNo)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Map<String, Object> buildMetricSlicesPayload(String brandScopeId,
                                                         KnowledgeAssetRunRepository.LatestRunView latestRun) {
        List<KnowledgeAssetMetricRepository.MetricRegistryView> registryItems =
                knowledgeAssetMetricRepository.listActiveMetricRegistry();
        List<KnowledgeAssetMetricRepository.MetricWindowFactView> windowFacts =
                knowledgeAssetMetricRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        List<KnowledgeAssetMetricRepository.MetricFactView> latestRunFacts =
                knowledgeAssetMetricRepository.listRunFacts(latestRun.runId());

        Map<String, String> metricGroupByKey = registryItems.stream()
                .collect(Collectors.toMap(
                        KnowledgeAssetMetricRepository.MetricRegistryView::metricKey,
                        KnowledgeAssetMetricRepository.MetricRegistryView::metricGroup,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Long> windowMetricCountByGroup = windowFacts.stream()
                .collect(Collectors.groupingBy(
                        item -> metricGroupByKey.getOrDefault(item.metricKey(), "unknown"),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        LinkedHashMap<String, Object> payload = basePayload("metric-slices", brandScopeId, latestRun);
        payload.put("registryRows", registryItems.stream()
                .map(item -> linkedMap(
                        "metricKey", item.metricKey(),
                        "metricName", item.metricName(),
                        "metricGroup", item.metricGroup(),
                        "valueType", item.valueType(),
                        "unit", item.unit(),
                        "aggregationMethod", item.aggregationMethod(),
                        "direction", item.direction(),
                        "description", item.description()
                ))
                .toList());
        payload.put("windowSummary", linkedMap(
                "registryCount", registryItems.size(),
                "windowMetricFactCount", windowFacts.size(),
                "latestRunMetricFactCount", latestRunFacts.size(),
                "groups", windowMetricCountByGroup.entrySet().stream()
                        .map(entry -> linkedMap(
                                "metricGroup", entry.getKey(),
                                "metricCount", entry.getValue()
                        ))
                        .toList()
        ));
        payload.put("windowMetricRows", windowFacts.stream()
                .map(item -> linkedMap(
                        "sourceObjectType", item.sourceObjectType(),
                        "sourceObjectKey", item.sourceObjectKey(),
                        "metricKey", item.metricKey(),
                        "metricGroup", metricGroupByKey.getOrDefault(item.metricKey(), "unknown"),
                        "dimensionKey", item.dimensionKey(),
                        "sampleCount", safeInt(item.sampleCount()),
                        "numericValue", item.numericValue(),
                        "numericMin", item.numericMin(),
                        "numericMax", item.numericMax(),
                        "metricText", item.metricText(),
                        "metricUnit", item.metricUnit(),
                        "dimensions", readJson(item.dimensionsJson()),
                        "sourceRefs", readJson(item.sourceRefsJson())
                ))
                .toList());
        payload.put("latestRunMetricRows", latestRunFacts.stream()
                .map(item -> linkedMap(
                        "sourceObjectType", item.sourceObjectType(),
                        "sourceObjectKey", item.sourceObjectKey(),
                        "metricKey", item.metricKey(),
                        "metricGroup", metricGroupByKey.getOrDefault(item.metricKey(), "unknown"),
                        "dimensionKey", item.dimensionKey(),
                        "metricValue", item.metricValue(),
                        "metricText", item.metricText(),
                        "metricUnit", item.metricUnit(),
                        "dimensions", readJson(item.dimensionsJson())
                ))
                .toList());
        return payload;
    }

    private Map<String, Object> buildCitationSourcesPayload(String brandScopeId,
                                                            KnowledgeAssetRunRepository.LatestRunView latestRun) {
        List<KnowledgeAssetSourceRepository.OwnedDomainView> ownedDomains =
                knowledgeAssetSourceRepository.listActiveOwnedDomains(brandScopeId);
        List<KnowledgeAssetSourceRepository.WindowRunHead> recentRuns = latestRun.observedAt() == null
                ? List.of()
                : knowledgeAssetSourceRepository.listRecentRunsByScope(brandScopeId, latestRun.observedAt(), 2);
        String previousRunId = recentRuns.size() > 1 ? recentRuns.get(1).runId() : null;
        List<KnowledgeAssetSourceRepository.SourceWindowFactView> windowFacts =
                knowledgeAssetSourceRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        List<KnowledgeAssetSourceRepository.SourceSnapshotFactView> latestSnapshotFacts =
                knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of(latestRun.runId()));
        List<KnowledgeAssetSourceRepository.SourceSnapshotFactView> previousSnapshotFacts =
                StringUtils.hasText(previousRunId)
                        ? knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(List.of(previousRunId))
                        : List.of();
        List<KnowledgeAssetSourceRepository.CitationView> latestCitations =
                knowledgeAssetSourceRepository.listCitationsByRunIds(List.of(latestRun.runId()));
        List<KnowledgeAssetSourceRepository.CitationView> previousCitations =
                StringUtils.hasText(previousRunId)
                        ? knowledgeAssetSourceRepository.listCitationsByRunIds(List.of(previousRunId))
                        : List.of();

        int totalWindowCitations = windowFacts.stream()
                .map(KnowledgeAssetSourceRepository.SourceWindowFactView::citationCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long ownedCitationCount = windowFacts.stream()
                .filter(item -> OWNERSHIP_OWNED.equalsIgnoreCase(defaultText(item.ownershipKey())))
                .map(KnowledgeAssetSourceRepository.SourceWindowFactView::citationCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        Map<String, Long> sourceTypeCounts = windowFacts.stream()
                .collect(Collectors.groupingBy(
                        item -> defaultText(item.sourceType()),
                        LinkedHashMap::new,
                        Collectors.summingLong(item -> safeInt(item.citationCount()))
                ));
        List<LinkedHashMap<String, Object>> latestUrlRows = aggregateUrlRows(latestCitations);
        List<LinkedHashMap<String, Object>> previousUrlRows = aggregateUrlRows(previousCitations);

        LinkedHashMap<String, Object> payload = basePayload("citation-sources", brandScopeId, latestRun);
        payload.put("ownedDomains", ownedDomains.stream()
                .map(item -> linkedMap(
                        "domainName", item.domainName(),
                        "domainRole", item.domainRole()
                ))
                .toList());
        payload.put("windowSummary", linkedMap(
                "totalCitations", totalWindowCitations,
                "ownedShare", percentage(ownedCitationCount, totalWindowCitations),
                "externalShare", percentage(totalWindowCitations - ownedCitationCount, totalWindowCitations),
                "domainCount", windowFacts.size()
        ));
        payload.put("windowCategorySlices", sourceTypeCounts.entrySet().stream()
                .map(entry -> linkedMap(
                        "sourceType", normalizeUpper(entry.getKey()),
                        "citationCount", entry.getValue(),
                        "share", percentage(entry.getValue(), totalWindowCitations)
                ))
                .toList());
        payload.put("windowDomainRows", windowFacts.stream()
                .map(item -> linkedMap(
                        "domainName", item.domainName(),
                        "sourceKey", item.sourceKey(),
                        "sourceType", item.sourceType(),
                        "ownershipKey", item.ownershipKey(),
                        "citationCount", safeInt(item.citationCount()),
                        "usedRunCount", safeInt(item.usedRunCount()),
                        "shareRate", item.shareRate()
                ))
                .toList());
        payload.put("latestSnapshotRows", latestSnapshotFacts.stream()
                .map(item -> linkedMap(
                        "domainName", item.domainName(),
                        "sourceKey", item.sourceKey(),
                        "sourceType", item.sourceType(),
                        "ownershipKey", item.ownershipKey(),
                        "citationCount", safeInt(item.citationCount()),
                        "shareRate", item.shareRate()
                ))
                .toList());
        payload.put("previousSnapshotRows", previousSnapshotFacts.stream()
                .map(item -> linkedMap(
                        "domainName", item.domainName(),
                        "sourceKey", item.sourceKey(),
                        "sourceType", item.sourceType(),
                        "ownershipKey", item.ownershipKey(),
                        "citationCount", safeInt(item.citationCount()),
                        "shareRate", item.shareRate()
                ))
                .toList());
        payload.put("latestUrlRows", latestUrlRows);
        payload.put("previousUrlRows", previousUrlRows);
        payload.put("windowTrendPoints", buildCitationTrendPointsPayload(brandScopeId, latestRun));
        return payload;
    }

    private List<LinkedHashMap<String, Object>> aggregateUrlRows(
            List<KnowledgeAssetSourceRepository.CitationView> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        return citations.stream()
                .collect(Collectors.groupingBy(
                        item -> firstNonBlank(normalizeCitationUrlKey(item.url()), defaultText(item.url())),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(group -> {
                    KnowledgeAssetSourceRepository.CitationView first = group.getFirst();
                    String representativeUrl = group.stream()
                            .map(KnowledgeAssetSourceRepository.CitationView::url)
                            .filter(StringUtils::hasText)
                            .reduce(null, this::chooseRepresentativeCitationUrl);
                    return linkedMap(
                            "url", firstNonBlank(representativeUrl, first.url()),
                            "sourceKey", first.sourceKey(),
                            "domainName", first.domainName(),
                            "title", first.title(),
                            "sourceType", first.sourceType(),
                            "ownershipKey", first.ownershipKey(),
                            "providerKey", first.providerKey(),
                            "usedCount", group.size()
                    );
                })
                .sorted((left, right) -> Integer.compare(
                        safeInt((Integer) right.get("usedCount")),
                        safeInt((Integer) left.get("usedCount"))
                ))
                .toList();
    }

    private String normalizeCitationUrlKey(String rawUrl) {
        String normalized = defaultText(rawUrl);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String withoutFragment = normalized.replaceFirst("#.*$", "");
        String withoutProtocol = withoutFragment.replaceFirst("^https?://", "");
        int slashIndex = withoutProtocol.indexOf('/');
        String host = slashIndex >= 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
        String pathAndQuery = slashIndex >= 0 ? withoutProtocol.substring(slashIndex) : "/";
        String normalizedHost = normalizeCitationHost(host);
        if (!StringUtils.hasText(normalizedHost)) {
            return normalized;
        }
        return normalizedHost + normalizeCitationPathAndQuery(pathAndQuery);
    }

    private String normalizeCitationHost(String rawHost) {
        String normalized = defaultText(rawHost).replaceFirst(":\\d+$", "");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String[] labels = normalized.toLowerCase(Locale.ROOT).split("\\.");
        List<String> resolved = new ArrayList<>();
        for (String label : labels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            if (labels.length > 2 && isMobileCitationHostLabel(label)) {
                continue;
            }
            resolved.add(label);
        }
        return resolved.isEmpty() ? normalized : String.join(".", resolved);
    }

    private boolean isMobileCitationHostLabel(String label) {
        return "m".equals(label) || "mobile".equals(label) || "wap".equals(label);
    }

    private String normalizeCitationPathAndQuery(String rawPathAndQuery) {
        String normalized = defaultText(rawPathAndQuery);
        if (!StringUtils.hasText(normalized)) {
            return "/";
        }
        String[] parts = normalized.split("\\?", 2);
        String path = StringUtils.hasText(parts[0]) ? parts[0] : "/";
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
            return path;
        }
        return path + "?" + parts[1];
    }

    private String chooseRepresentativeCitationUrl(String currentUrl, String candidateUrl) {
        if (!StringUtils.hasText(currentUrl)) {
            return candidateUrl;
        }
        if (!StringUtils.hasText(candidateUrl)) {
            return currentUrl;
        }
        boolean currentMobile = isMobileCitationUrl(currentUrl);
        boolean candidateMobile = isMobileCitationUrl(candidateUrl);
        if (currentMobile != candidateMobile) {
            return candidateMobile ? currentUrl : candidateUrl;
        }
        return currentUrl;
    }

    private boolean isMobileCitationUrl(String rawUrl) {
        String normalized = defaultText(rawUrl).replaceFirst("^https?://", "");
        int slashIndex = normalized.indexOf('/');
        String host = slashIndex >= 0 ? normalized.substring(0, slashIndex) : normalized;
        if (!StringUtils.hasText(host)) {
            return false;
        }
        for (String label : host.toLowerCase(Locale.ROOT).split("\\.")) {
            if (isMobileCitationHostLabel(label)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<LinkedHashMap<String, Object>> buildCitationTrendPointsPayload(
            String brandScopeId,
            KnowledgeAssetRunRepository.LatestRunView latestRun) {
        if (!StringUtils.hasText(brandScopeId) || latestRun == null || latestRun.observedAt() == null) {
            return List.of();
        }
        OffsetDateTime windowEndAt = latestRun.observedAt();
        OffsetDateTime windowStartAt = windowEndAt.minusDays(14);
        List<KnowledgeAssetSourceRepository.WindowRunHead> visibleRuns = knowledgeAssetSourceRepository
                .listRunsByScopeWithinWindow(brandScopeId, windowStartAt, windowEndAt).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(KnowledgeAssetSourceRepository.WindowRunHead::observedAt,
                        Comparator.nullsLast(OffsetDateTime::compareTo)))
                .toList();
        if (visibleRuns.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, visibleRuns.size() - 6);
        List<KnowledgeAssetSourceRepository.WindowRunHead> recentVisibleRuns = visibleRuns.subList(fromIndex, visibleRuns.size());
        List<String> runIds = recentVisibleRuns.stream()
                .map(KnowledgeAssetSourceRepository.WindowRunHead::runId)
                .filter(StringUtils::hasText)
                .toList();
        Map<String, List<KnowledgeAssetSourceRepository.SourceSnapshotFactView>> factsByRunId =
                knowledgeAssetSourceRepository.listSnapshotFactsByRunIds(runIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(
                                KnowledgeAssetSourceRepository.SourceSnapshotFactView::runId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        List<LinkedHashMap<String, Object>> points = new ArrayList<>();
        for (int index = 0; index < recentVisibleRuns.size(); index++) {
            KnowledgeAssetSourceRepository.WindowRunHead run = recentVisibleRuns.get(index);
            List<KnowledgeAssetSourceRepository.SourceSnapshotFactView> runFacts =
                    factsByRunId.getOrDefault(run.runId(), List.of());
            int totalCitations = runFacts.stream()
                    .map(KnowledgeAssetSourceRepository.SourceSnapshotFactView::citationCount)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            long ownedCount = runFacts.stream()
                    .filter(item -> OWNERSHIP_OWNED.equalsIgnoreCase(defaultText(item.ownershipKey())))
                    .map(KnowledgeAssetSourceRepository.SourceSnapshotFactView::citationCount)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            points.add(linkedMap(
                    "sliceKey", run.runId(),
                    "label", run.observedAt() == null ? "未生成" : run.observedAt().toLocalDate().toString(),
                    "generatedAt", run.observedAt() == null ? null : run.observedAt().toString(),
                    "totalCitations", totalCitations,
                    "ownedShare", percentage(ownedCount, totalCitations),
                    "thirdPartyShare", percentage(totalCitations - ownedCount, totalCitations),
                    "competitorShare", null,
                    "isLatest", index == recentVisibleRuns.size() - 1,
                    "topDomains", runFacts.stream()
                            .sorted(Comparator.comparing(
                                    KnowledgeAssetSourceRepository.SourceSnapshotFactView::citationCount,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            ).thenComparing(
                                    KnowledgeAssetSourceRepository.SourceSnapshotFactView::domainName,
                                    Comparator.nullsLast(String::compareTo)
                            ))
                            .limit(3)
                            .map(item -> linkedMap(
                                    "domain", item.domainName(),
                                    "used", safeInt(item.citationCount()),
                                    "share", item.shareRate(),
                                    "type", item.sourceType(),
                                    "ownershipKey", item.ownershipKey(),
                                    "ownershipLabel", resolveOwnershipLabel(item.ownershipKey())
                            ))
                            .toList()
            ));
        }
        return points;
    }

    private Map<String, Object> buildProblemAnalysisPayload(String brandScopeId,
                                                            KnowledgeAssetRunRepository.LatestRunView latestRun) {
        KnowledgeAssetRunRepository.RunDetailView latestRunDetail =
                knowledgeAssetRunRepository.findRunDetailByRunId(latestRun.runId());
        List<KnowledgeAssetProblemRepository.TopicCatalogView> topics =
                knowledgeAssetProblemRepository.listActiveTopics(brandScopeId);
        List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts =
                knowledgeAssetProblemRepository.listActivePrompts(brandScopeId);
        List<KnowledgeAssetProblemRepository.ProblemWindowFactView> windowFacts =
                knowledgeAssetProblemRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        List<KnowledgeAssetProblemRepository.ProblemSnapshotFactView> latestSnapshotFacts =
                knowledgeAssetProblemRepository.listSnapshotFactsByRunIds(List.of(latestRun.runId()));
        List<KnowledgeAssetEvidenceRepository.ProbeView> probes =
                knowledgeAssetEvidenceRepository.listProbesByRunId(latestRun.runId());
        List<KnowledgeAssetEvidenceRepository.AttemptView> attempts =
                knowledgeAssetEvidenceRepository.listAttemptsByRunId(latestRun.runId());
        List<KnowledgeAssetEvidenceRepository.BrandMentionView> brandMentions =
                knowledgeAssetEvidenceRepository.listBrandMentionsByRunId(latestRun.runId());
        List<KnowledgeAssetSourceRepository.CitationView> citations =
                knowledgeAssetSourceRepository.listCitationsByRunIds(List.of(latestRun.runId()));

        Map<String, Long> promptCountByTopic = prompts.stream()
                .collect(Collectors.groupingBy(
                        item -> defaultText(item.topicCatalogId()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, Integer> evidenceCountByTopic = windowFacts.stream()
                .filter(item -> StringUtils.hasText(item.topicCatalogId()))
                .collect(Collectors.toMap(
                        KnowledgeAssetProblemRepository.ProblemWindowFactView::topicCatalogId,
                        item -> safeInt(item.evidenceCount()),
                        Integer::sum,
                        LinkedHashMap::new
                ));
        Map<String, List<String>> sampleQuestionsByTopic = prompts.stream()
                .filter(item -> StringUtils.hasText(item.topicCatalogId()) && StringUtils.hasText(item.promptText()))
                .collect(Collectors.groupingBy(
                        item -> defaultText(item.topicCatalogId()),
                        LinkedHashMap::new,
                        Collectors.mapping(KnowledgeAssetProblemRepository.PromptCatalogView::promptText, Collectors.toList())
                ));
        List<LinkedHashMap<String, Object>> promptProjectionRows = buildProblemPromptRows(
                latestRunDetail,
                prompts,
                probes,
                attempts,
                brandMentions,
                citations
        );

        LinkedHashMap<String, Object> payload = basePayload("problem-analysis", brandScopeId, latestRun);
        payload.put("windowSummary", linkedMap(
                "topicCount", topics.size(),
                "promptCount", prompts.size(),
                "windowFactCount", windowFacts.size(),
                "latestSnapshotFactCount", latestSnapshotFacts.size(),
                "windowEvidenceCount", windowFacts.stream()
                        .map(KnowledgeAssetProblemRepository.ProblemWindowFactView::evidenceCount)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum()
        ));
        payload.put("topicRows", topics.stream()
                .map(item -> linkedMap(
                        "topicCatalogId", item.topicCatalogId(),
                        "sourceTopicId", item.sourceTopicId(),
                        "topicName", item.topicName(),
                        "topicDescription", item.topicDescription(),
                        "problemSpaceBucket", item.problemSpaceBucket(),
                        "promptCount", safeLong(promptCountByTopic.get(item.topicCatalogId())),
                        "evidenceCount", safeInt(evidenceCountByTopic.get(item.topicCatalogId())),
                        "sampleQuestions", sampleQuestionsByTopic.getOrDefault(item.topicCatalogId(), List.of()).stream()
                                .limit(3)
                                .toList()
                ))
                .toList());
        payload.put("promptRows", promptProjectionRows);
        payload.put("windowBucketRows", windowFacts.stream()
                .map(item -> linkedMap(
                        "problemSpaceBucket", item.problemSpaceBucket(),
                        "topicCatalogId", item.topicCatalogId(),
                        "clusterKey", item.clusterKey(),
                        "factSource", item.factSource(),
                        "runCount", safeInt(item.runCount()),
                        "promptCount", safeInt(item.promptCount()),
                        "probeCount", safeInt(item.probeCount()),
                        "attemptCount", safeInt(item.attemptCount()),
                        "evidenceCount", safeInt(item.evidenceCount()),
                        "avgSignalScore", item.avgSignalScore()
                ))
                .toList());
        payload.put("latestSnapshotRows", latestSnapshotFacts.stream()
                .map(item -> linkedMap(
                        "problemSpaceBucket", item.problemSpaceBucket(),
                        "topicCatalogId", item.topicCatalogId(),
                        "clusterKey", item.clusterKey(),
                        "factSource", item.factSource(),
                        "promptCount", safeInt(item.promptCount()),
                        "probeCount", safeInt(item.probeCount()),
                        "attemptCount", safeInt(item.attemptCount()),
                        "evidenceCount", safeInt(item.evidenceCount()),
                        "signalScore", item.signalScore()
                ))
                .toList());
        return payload;
    }

    private List<LinkedHashMap<String, Object>> buildProblemPromptRows(
            KnowledgeAssetRunRepository.RunDetailView latestRunDetail,
            List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts,
            List<KnowledgeAssetEvidenceRepository.ProbeView> probes,
            List<KnowledgeAssetEvidenceRepository.AttemptView> attempts,
            List<KnowledgeAssetEvidenceRepository.BrandMentionView> brandMentions,
            List<KnowledgeAssetSourceRepository.CitationView> citations) {
        if (prompts == null || prompts.isEmpty()) {
            return List.of();
        }
        String targetBrandKey = resolveTargetBrandKey(latestRunDetail);
        Map<String, List<KnowledgeAssetEvidenceRepository.ProbeView>> probesByPromptId = probes == null
                ? Map.of()
                : probes.stream()
                .filter(item -> item != null && StringUtils.hasText(item.promptCatalogId()))
                .collect(Collectors.groupingBy(
                        item -> defaultText(item.promptCatalogId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<String, KnowledgeAssetEvidenceRepository.AttemptView> latestAttemptByProbeId = selectLatestAttemptByProbeId(attempts);
        Map<String, List<KnowledgeAssetEvidenceRepository.BrandMentionView>> mentionsByProbeId = brandMentions == null
                ? Map.of()
                : brandMentions.stream()
                .filter(item -> item != null && StringUtils.hasText(item.probeId()))
                .collect(Collectors.groupingBy(
                        item -> defaultText(item.probeId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<String, Integer> citationCountByProbeId = new LinkedHashMap<>();
        if (citations != null) {
            for (KnowledgeAssetSourceRepository.CitationView citation : citations) {
                if (citation == null || !StringUtils.hasText(citation.probeId())) {
                    continue;
                }
                citationCountByProbeId.merge(defaultText(citation.probeId()), 1, Integer::sum);
            }
        }
        int totalEvidenceCount = citations == null ? 0 : citations.size();
        List<MutablePromptProjectionRow> rows = new ArrayList<>();
        for (KnowledgeAssetProblemRepository.PromptCatalogView prompt : prompts) {
            List<KnowledgeAssetEvidenceRepository.ProbeView> linkedProbes =
                    probesByPromptId.getOrDefault(defaultText(prompt.promptCatalogId()), List.of());
            List<KnowledgeAssetEvidenceRepository.BrandMentionView> targetMentions = new ArrayList<>();
            int evidenceCount = 0;
            boolean hasRawAnswer = false;
            String latestProbeStatus = null;
            for (KnowledgeAssetEvidenceRepository.ProbeView probe : linkedProbes) {
                if (probe == null || !StringUtils.hasText(probe.id())) {
                    continue;
                }
                String probeId = defaultText(probe.id());
                evidenceCount += citationCountByProbeId.getOrDefault(probeId, 0);
                for (KnowledgeAssetEvidenceRepository.BrandMentionView mention : mentionsByProbeId.getOrDefault(probeId, List.of())) {
                    if (isTargetBrandMention(mention, targetBrandKey)) {
                        targetMentions.add(mention);
                    }
                }
                KnowledgeAssetEvidenceRepository.AttemptView latestAttempt = latestAttemptByProbeId.get(probeId);
                if (latestAttempt != null) {
                    hasRawAnswer = hasRawAnswer || StringUtils.hasText(latestAttempt.rawResponseText());
                    latestProbeStatus = resolvePromptProbeStatus(latestProbeStatus, latestAttempt);
                }
            }
            MutablePromptProjectionRow row = new MutablePromptProjectionRow(
                    prompt.promptCatalogId(),
                    prompt.topicCatalogId(),
                    prompt.sourcePromptId(),
                    prompt.promptText(),
                    prompt.seqNo(),
                    linkedProbes.size(),
                    evidenceCount,
                    hasRawAnswer,
                    latestProbeStatus,
                    resolvePromptVisibilityScore(targetMentions),
                    resolvePromptAveragePosition(targetMentions),
                    totalEvidenceCount <= 0 ? 0.0d : round2(evidenceCount * 100.0d / totalEvidenceCount)
            );
            rows.add(row);
        }
        applyPromptVisibilityRanks(rows);
        return rows.stream()
                .map(item -> linkedMap(
                        "promptCatalogId", item.promptCatalogId,
                        "topicCatalogId", item.topicCatalogId,
                        "sourcePromptId", item.sourcePromptId,
                        "promptText", item.promptText,
                        "seqNo", item.seqNo,
                        "probeCount", item.probeCount,
                        "evidenceCount", item.evidenceCount,
                        "hasRawAnswer", item.hasRawAnswer,
                        "latestProbeStatus", item.latestProbeStatus,
                        "visibilityRank", item.visibilityRank,
                        "visibilityScore", item.visibilityScore,
                        "visibilityDelta", 0.0d,
                        "averagePosition", item.averagePosition,
                        "averageDelta", 0.0d,
                        "citationShare", item.citationShare,
                        "citationDelta", 0.0d
                ))
                .toList();
    }

    private LinkedHashMap<String, Object> basePayload(String projectionType,
                                                      String brandScopeId,
                                                      KnowledgeAssetRunRepository.LatestRunView latestRun) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectionType", projectionType);
        payload.put("projectionVersion", PROJECTION_VERSION);
        payload.put("brandScopeId", brandScopeId);
        payload.put("sourceWindowKey", WINDOW_KEY_LAST_14_DAYS);
        payload.put("latestRun", linkedMap(
                "runId", latestRun.runId(),
                "sourceTaskId", latestRun.sourceTaskId(),
                "sourceReportId", latestRun.sourceReportId(),
                "sampleSize", latestRun.sampleSize(),
                "observedAt", latestRun.observedAt() == null ? null : latestRun.observedAt().toString()
        ));
        payload.put("truthSources", buildTruthSources(projectionType, latestRun));
        payload.put("materializedAt", OffsetDateTime.now().toString());
        return payload;
    }

    private LinkedHashMap<String, Object> buildTruthSources(String projectionType,
                                                            KnowledgeAssetRunRepository.LatestRunView latestRun) {
        return switch (projectionType) {
            case "brand-performance" -> linkedMap(
                    "configurationTruth", List.of("ka_competitor_catalog"),
                    "analysisInputTruth", List.of("ka_topic_catalog", "ka_prompt_catalog"),
                    "windowTruth", linkedMap("table", "ka_competitor_window_facts", "windowKey", WINDOW_KEY_LAST_14_DAYS),
                    "topicMetricTruth", linkedMap("table", "ka_metric_window_facts", "windowKey", WINDOW_KEY_LAST_14_DAYS, "sourceObjectType", METRIC_TOPIC),
                    "snapshotTruth", linkedMap("table", "ka_competitor_snapshot_facts", "runId", latestRun.runId()),
                    "evidenceTruth", linkedMap("tables", List.of("ka_brand_mentions"), "runId", latestRun.runId())
            );
            case "citation-sources" -> linkedMap(
                    "configurationTruth", List.of("ka_owned_domain_catalog"),
                    "rootAssetTruth", List.of("ka_source_assets"),
                    "windowTruth", linkedMap("table", "ka_source_window_facts", "windowKey", WINDOW_KEY_LAST_14_DAYS),
                    "snapshotTruth", linkedMap("table", "ka_source_snapshot_facts", "runId", latestRun.runId()),
                    "evidenceTruth", linkedMap("tables", List.of("ka_citations"), "runId", latestRun.runId())
            );
            case "problem-analysis" -> linkedMap(
                    "analysisInputTruth", List.of("ka_topic_catalog", "ka_prompt_catalog"),
                    "windowTruth", linkedMap("table", "ka_problem_window_facts", "windowKey", WINDOW_KEY_LAST_14_DAYS),
                    "snapshotTruth", linkedMap("table", "ka_problem_snapshot_facts", "runId", latestRun.runId()),
                    "evidenceTruth", linkedMap("tables", List.of("ka_probes", "ka_attempts", "ka_problem_signals", "ka_brand_mentions", "ka_citations"), "runId", latestRun.runId())
            );
            case "metric-slices" -> linkedMap(
                    "metricRegistryTruth", List.of("ka_metric_registry"),
                    "windowTruth", linkedMap("table", "ka_metric_window_facts", "windowKey", WINDOW_KEY_LAST_14_DAYS),
                    "snapshotTruth", linkedMap("table", "ka_metric_facts", "runId", latestRun.runId()),
                    "dimensionTruth", List.of("dimensions", "source_object_type", "source_object_key")
            );
            default -> linkedMap(
                    "windowTruth", linkedMap("windowKey", WINDOW_KEY_LAST_14_DAYS),
                    "snapshotTruth", linkedMap("runId", latestRun.runId())
            );
        };
    }

    private String projectionId(String projectionName, String brandScopeId) {
        return "ka-projection-%s-%s".formatted(projectionName, brandScopeId);
    }

    private String projectionKey(String projectionName) {
        return "%s:%s".formatted(PROJECTION_KEY_DEFAULT, projectionName);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize knowledge asset projection payload", exception);
        }
    }

    private String defaultText(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String resolveTargetBrandKey(KnowledgeAssetRunRepository.RunDetailView latestRunDetail) {
        if (latestRunDetail == null || !StringUtils.hasText(latestRunDetail.taskSnapshotJson())) {
            return "";
        }
        try {
            return normalizeStorageKey(readJson(latestRunDetail.taskSnapshotJson()), "brandNameSnapshot");
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String normalizeStorageKey(Object rawJson, String fieldName) {
        if (!(rawJson instanceof Map<?, ?> rawMap)) {
            return "";
        }
        Object value = ((Map<String, Object>) rawMap).get(fieldName);
        return normalizeText(String.valueOf(value));
    }

    private Object readJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, Object.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Map<String, KnowledgeAssetEvidenceRepository.AttemptView> selectLatestAttemptByProbeId(
            List<KnowledgeAssetEvidenceRepository.AttemptView> attempts) {
        Map<String, KnowledgeAssetEvidenceRepository.AttemptView> latestAttemptByProbeId = new LinkedHashMap<>();
        if (attempts == null || attempts.isEmpty()) {
            return latestAttemptByProbeId;
        }
        for (KnowledgeAssetEvidenceRepository.AttemptView attempt : attempts) {
            if (attempt == null || !StringUtils.hasText(attempt.probeId())) {
                continue;
            }
            latestAttemptByProbeId.putIfAbsent(defaultText(attempt.probeId()), attempt);
        }
        return latestAttemptByProbeId;
    }

    private boolean isTargetBrandMention(KnowledgeAssetEvidenceRepository.BrandMentionView mention, String targetBrandKey) {
        if (mention == null) {
            return false;
        }
        return Objects.equals("0", defaultText(mention.brandRole()))
                || (StringUtils.hasText(targetBrandKey)
                && Objects.equals(targetBrandKey, normalizeText(mention.brandName())));
    }

    private String normalizeText(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String resolvePromptProbeStatus(String currentStatus,
                                            KnowledgeAssetEvidenceRepository.AttemptView latestAttempt) {
        if ("completed".equals(currentStatus)) {
            return currentStatus;
        }
        if (latestAttempt == null) {
            return currentStatus;
        }
        if (StringUtils.hasText(latestAttempt.rawResponseText())
                || StringUtils.hasText(latestAttempt.responsePreview())
                || StringUtils.hasText(latestAttempt.citationsPayloadJson())) {
            return "completed";
        }
        return StringUtils.hasText(currentStatus) ? currentStatus : "running";
    }

    private Double resolvePromptVisibilityScore(List<KnowledgeAssetEvidenceRepository.BrandMentionView> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return 0.0d;
        }
        return round2(mentions.stream()
                .mapToDouble(item -> item.confidenceScore() == null
                        ? (Boolean.TRUE.equals(item.recommendedFlag()) ? 0.9d : 0.72d)
                        : item.confidenceScore().doubleValue())
                .average()
                .orElse(0.0d) * 100.0d);
    }

    private Double resolvePromptAveragePosition(List<KnowledgeAssetEvidenceRepository.BrandMentionView> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return null;
        }
        return mentions.stream()
                .map(KnowledgeAssetEvidenceRepository.BrandMentionView::positionIndex)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .map(this::round2)
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private void applyPromptVisibilityRanks(List<MutablePromptProjectionRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<MutablePromptProjectionRow> ranked = rows.stream()
                .sorted(Comparator
                        .comparing((MutablePromptProjectionRow item) -> item.visibilityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing((MutablePromptProjectionRow item) -> item.evidenceCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> defaultText(item.promptText)))
                .toList();
        int rank = 1;
        for (MutablePromptProjectionRow row : ranked) {
            row.visibilityRank = rank++;
        }
    }

    private Double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String normalizeUpper(String raw) {
        return defaultText(raw).toUpperCase(Locale.ROOT);
    }

    private Double percentage(long value, long total) {
        if (value <= 0 || total <= 0) {
            return 0d;
        }
        return value * 100d / total;
    }

    private String resolveOwnershipLabel(String ownershipKey) {
        return switch (defaultText(ownershipKey).toUpperCase(Locale.ROOT)) {
            case OWNERSHIP_OWNED -> "自有资产";
            case "COMPETITOR" -> "竞品引用";
            case "OTHER" -> "其他";
            default -> "第三方引用";
        };
    }

    private LinkedHashMap<String, Object> linkedMap(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static final class MutableTopicMetricRow {

        private Integer sampleSize;
        private Double visibilityScore;
        private Double mentionRate;
        private Double recommendRate;
        private Double top3Rate;
        private Double averagePosition;
        private Integer mentionCount;

        private void apply(String metricKey, Double value) {
            if (!StringUtils.hasText(metricKey) || value == null) {
                return;
            }
            switch (metricKey.trim()) {
                case METRIC_SAMPLE_SIZE -> sampleSize = (int) Math.round(value);
                case METRIC_VISIBILITY_SCORE -> visibilityScore = value;
                case METRIC_VISIBILITY_MENTION_RATE -> mentionRate = value;
                case METRIC_VISIBILITY_RECOMMEND_RATE -> recommendRate = value;
                case METRIC_VISIBILITY_TOP3_RATE -> top3Rate = value;
                case METRIC_VISIBILITY_AVERAGE_POSITION -> averagePosition = value;
                case METRIC_VISIBILITY_MENTION_COUNT -> mentionCount = (int) Math.round(value);
                default -> {
                }
            }
        }

        private TopicMetricRow freeze() {
            return new TopicMetricRow(
                    sampleSize == null ? 0 : sampleSize,
                    visibilityScore,
                    mentionRate,
                    recommendRate,
                    top3Rate,
                    averagePosition,
                    mentionCount == null ? 0 : mentionCount
            );
        }
    }

    private static final class MutablePromptProjectionRow {

        private final String promptCatalogId;
        private final String topicCatalogId;
        private final String sourcePromptId;
        private final String promptText;
        private final Integer seqNo;
        private final Integer probeCount;
        private final Integer evidenceCount;
        private final Boolean hasRawAnswer;
        private final String latestProbeStatus;
        private Integer visibilityRank;
        private final Double visibilityScore;
        private final Double averagePosition;
        private final Double citationShare;

        private MutablePromptProjectionRow(String promptCatalogId,
                                           String topicCatalogId,
                                           String sourcePromptId,
                                           String promptText,
                                           Integer seqNo,
                                           Integer probeCount,
                                           Integer evidenceCount,
                                           Boolean hasRawAnswer,
                                           String latestProbeStatus,
                                           Double visibilityScore,
                                           Double averagePosition,
                                           Double citationShare) {
            this.promptCatalogId = promptCatalogId;
            this.topicCatalogId = topicCatalogId;
            this.sourcePromptId = sourcePromptId;
            this.promptText = promptText;
            this.seqNo = seqNo;
            this.probeCount = probeCount;
            this.evidenceCount = evidenceCount;
            this.hasRawAnswer = hasRawAnswer;
            this.latestProbeStatus = latestProbeStatus;
            this.visibilityScore = visibilityScore;
            this.averagePosition = averagePosition;
            this.citationShare = citationShare;
        }
    }

    private record TopicMetricRow(Integer sampleSize,
                                  Double visibilityScore,
                                  Double mentionRate,
                                  Double recommendRate,
                                  Double top3Rate,
                                  Double averagePosition,
                                  Integer mentionCount) {

        private static TopicMetricRow empty() {
            return new TopicMetricRow(0, null, null, null, null, null, 0);
        }
    }

    public record ProjectionMaterializationResult(String brandScopeId,
                                                  String latestRunId,
                                                  boolean skipped,
                                                  List<String> materializedDomains,
                                                  String reason) {

        public static ProjectionMaterializationResult skipped(String brandScopeId, String reason) {
            return new ProjectionMaterializationResult(
                    brandScopeId,
                    null,
                    true,
                    List.of(),
                    reason
            );
        }
    }
}
