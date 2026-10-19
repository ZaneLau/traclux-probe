package com.geoprobe.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetScheduleStateRepository;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetScheduleStateMaterializationService {

    private static final String WINDOW_KEY_LAST_7_DAYS = "last_7_days";
    private static final String WINDOW_KEY_LAST_14_DAYS = "last_14_days";
    private static final String METRIC_TOPIC = "topic";
    private static final String METRIC_SAMPLE_SIZE = "kpi.sample_size";
    private static final int DEFAULT_COVERAGE_FLOOR = 1;
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final KnowledgeAssetRunRepository knowledgeAssetRunRepository;
    private final KnowledgeAssetProblemRepository knowledgeAssetProblemRepository;
    private final KnowledgeAssetMetricRepository knowledgeAssetMetricRepository;
    private final KnowledgeAssetScheduleStateRepository knowledgeAssetScheduleStateRepository;
    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;
    private final ObjectMapper objectMapper;

    public ScheduleStateMaterializationResult materializeScope(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return ScheduleStateMaterializationResult.skipped(null, "missing_brand_scope_id");
        }
        KnowledgeAssetRunRepository.LatestRunView latestRun = knowledgeAssetRunRepository.findLatestRunByScope(brandScopeId);
        if (latestRun == null || latestRun.observedAt() == null) {
            return ScheduleStateMaterializationResult.skipped(brandScopeId, "schedule_state_source_run_missing");
        }

        OffsetDateTime observedAt = latestRun.observedAt();
        List<KnowledgeAssetProblemRepository.TopicCatalogView> activeTopics =
                knowledgeAssetProblemRepository.listActiveTopics(brandScopeId);
        List<KnowledgeAssetRunRepository.RunDetailView> recentRuns14 = knowledgeAssetRunRepository
                .listRunDetailsByScopeWithinWindow(brandScopeId, observedAt.minusDays(14), observedAt);
        List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts7 =
                knowledgeAssetMetricRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_7_DAYS);
        List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts14 =
                knowledgeAssetMetricRepository.listWindowFacts(brandScopeId, WINDOW_KEY_LAST_14_DAYS);
        Map<String, OffsetDateTime> configUpdatedAtBySourceTopicId =
                loadConfigUpdatedAtBySourceTopicId(brandScopeId, activeTopics);

        Map<String, Integer> topicProbeCount7d = aggregateTopicSampleSizes(metricWindowFacts7);
        Map<String, Integer> topicProbeCount14d = aggregateTopicSampleSizes(metricWindowFacts14);
        Map<String, Integer> topicSampleSize14d = aggregateTopicSampleSizes(metricWindowFacts14);
        TopicLatestCoverageSnapshot topicLatestCoverageSnapshot = buildTopicLatestCoverageSnapshot(recentRuns14, observedAt);

        boolean brandColdStart = recentRuns14.size() <= 1;
        boolean brandJustEdited = hasTopicEditedAfter(activeTopics, configUpdatedAtBySourceTopicId, observedAt);
        int last1dProbeCount = sumProbeCount(recentRuns14, observedAt.minusDays(1));
        int last7dProbeCount = sumProbeCount(recentRuns14, observedAt.minusDays(7));
        int last14dProbeCount = sumProbeCount(recentRuns14, observedAt.minusDays(14));
        int activeTopicCount = activeTopics.size();
        int coveredTopicCount7d = countCoveredTopics(activeTopics, topicProbeCount7d);
        double coverageGapScore = activeTopicCount <= 0
                ? 0.0d
                : clamp((activeTopicCount - coveredTopicCount7d) / (double) activeTopicCount);
        double brandFreshnessScore = freshnessSince(latestRun.observedAt(), observedAt, 14);
        double suggestedBrandWeight = clamp(
                coverageGapScore * 0.45d
                        + brandFreshnessScore * 0.35d
                        + (brandColdStart ? 0.15d : 0.0d)
                        + (brandJustEdited ? 0.05d : 0.0d)
        );

        knowledgeAssetScheduleStateRepository.upsertBrandState(
                new KnowledgeAssetScheduleStateRepository.BrandScheduleStateUpsert(
                        "ka-schedule-brand-" + brandScopeId,
                        brandScopeId,
                        latestRun.runId(),
                        latestRun.observedAt(),
                        last1dProbeCount,
                        last7dProbeCount,
                        last14dProbeCount,
                        activeTopicCount,
                        coveredTopicCount7d,
                        coverageGapScore,
                        brandFreshnessScore,
                        brandColdStart,
                        brandJustEdited,
                        suggestedBrandWeight,
                        toJson(Map.of(
                                "coverageWindowKey", WINDOW_KEY_LAST_7_DAYS,
                                "sampleWindowKey", WINDOW_KEY_LAST_14_DAYS,
                                "topicIds", activeTopics.stream()
                                        .map(KnowledgeAssetProblemRepository.TopicCatalogView::topicCatalogId)
                                        .filter(StringUtils::hasText)
                                        .toList()
                        ))
                )
        );

        List<KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert> topicStates = new ArrayList<>();
        for (KnowledgeAssetProblemRepository.TopicCatalogView topic : activeTopics) {
            if (topic == null || !StringUtils.hasText(topic.topicCatalogId())) {
                continue;
            }
            int topicLast1dProbeCount = topicLatestCoverageSnapshot.last1dProbeCountByTopic()
                    .getOrDefault(topic.topicCatalogId(), 0);
            int topicLast7dProbeCount = topicProbeCount7d.getOrDefault(topic.topicCatalogId(), 0);
            int topicLast14dProbeCount = topicProbeCount14d.getOrDefault(topic.topicCatalogId(), 0);
            int topicLast14dSampleSize = topicSampleSize14d.getOrDefault(topic.topicCatalogId(), 0);
            OffsetDateTime latestCoveredAt = topicLatestCoverageSnapshot.latestCoveredAtByTopic().get(topic.topicCatalogId());
            OffsetDateTime configUpdatedAt = configUpdatedAtBySourceTopicId.get(topic.sourceTopicId());
            boolean topicJustEdited = configUpdatedAt != null
                    && latestRun.observedAt() != null
                    && configUpdatedAt.isAfter(latestRun.observedAt());
            boolean topicColdStart = brandColdStart || topicLast14dSampleSize <= 0;
            double coverageDebtScore = clamp((Math.max(DEFAULT_COVERAGE_FLOOR * 2, 2) - Math.min(
                    topicLast14dProbeCount,
                    Math.max(DEFAULT_COVERAGE_FLOOR * 2, 2)
            )) / (double) Math.max(DEFAULT_COVERAGE_FLOOR * 2, 2));
            if (topicLast7dProbeCount <= 0) {
                coverageDebtScore = 1.0d;
            }
            double freshnessScore = latestCoveredAt == null
                    ? 1.0d
                    : freshnessSince(latestCoveredAt, observedAt, 14);
            double recentIntensityScore = topicLast14dSampleSize <= 0
                    ? 0.0d
                    : clamp(topicLast1dProbeCount / (double) Math.max(1, topicLast14dSampleSize));
            double suggestedPriorityScore = clamp(
                    coverageDebtScore * 0.50d
                            + freshnessScore * 0.25d
                            + (topicColdStart ? 0.15d : 0.0d)
                            + (topicJustEdited ? 0.10d : 0.0d)
                            - recentIntensityScore * 0.20d
            );
            topicStates.add(new KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert(
                    "ka-schedule-topic-" + brandScopeId + "-" + topic.topicCatalogId(),
                    brandScopeId,
                    topic.topicCatalogId(),
                    latestRun.runId(),
                    latestCoveredAt,
                    topicLast1dProbeCount,
                    topicLast7dProbeCount,
                    topicLast14dProbeCount,
                    topicLast14dSampleSize,
                    DEFAULT_COVERAGE_FLOOR,
                    coverageDebtScore,
                    freshnessScore,
                    recentIntensityScore,
                    topicColdStart,
                    topicJustEdited,
                    suggestedPriorityScore,
                    toJson(Map.of(
                            "topicName", defaultIfBlank(topic.topicName(), "未命名主题"),
                            "problemSpaceBucket", defaultIfBlank(topic.problemSpaceBucket(), ""),
                            "latestCoveredAt", latestCoveredAt == null ? "" : latestCoveredAt.toString()
                    ))
            ));
        }
        topicStates.sort(Comparator.comparing(
                KnowledgeAssetScheduleStateRepository.TopicScheduleStateUpsert::suggestedPriorityScore,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        knowledgeAssetScheduleStateRepository.replaceTopicStates(brandScopeId, topicStates);

        return new ScheduleStateMaterializationResult(
                brandScopeId,
                latestRun.runId(),
                false,
                topicStates.size(),
                null
        );
    }

    private TopicLatestCoverageSnapshot buildTopicLatestCoverageSnapshot(
            List<KnowledgeAssetRunRepository.RunDetailView> recentRuns14,
            OffsetDateTime observedAt) {
        if (recentRuns14 == null || recentRuns14.isEmpty()) {
            return TopicLatestCoverageSnapshot.empty();
        }
        List<KnowledgeAssetRunRepository.RunDetailView> sortedRuns = recentRuns14.stream()
                .filter(Objects::nonNull)
                .filter(run -> StringUtils.hasText(run.runId()))
                .sorted(Comparator.comparing(KnowledgeAssetRunRepository.RunDetailView::observedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<String, OffsetDateTime> latestCoveredAtByTopic = new LinkedHashMap<>();
        Map<String, Integer> last1dProbeCountByTopic = new LinkedHashMap<>();
        List<String> runIds = sortedRuns.stream()
                .map(KnowledgeAssetRunRepository.RunDetailView::runId)
                .toList();
        Map<String, OffsetDateTime> observedAtByRunId = sortedRuns.stream()
                .collect(LinkedHashMap::new,
                        (map, item) -> map.put(item.runId(), item.observedAt()),
                        Map::putAll);
        for (KnowledgeAssetMetricRepository.MetricFactView fact :
                knowledgeAssetMetricRepository.listRunFactsByRunIds(runIds)) {
            if (fact == null
                    || !METRIC_TOPIC.equals(fact.sourceObjectType())
                    || !METRIC_SAMPLE_SIZE.equals(fact.metricKey())
                    || !StringUtils.hasText(fact.sourceObjectKey())
                    || fact.metricValue() == null
                    || fact.metricValue() <= 0.0d) {
                continue;
            }
            OffsetDateTime runObservedAt = observedAtByRunId.get(fact.runId());
            if (runObservedAt == null) {
                continue;
            }
            latestCoveredAtByTopic.putIfAbsent(fact.sourceObjectKey(), runObservedAt);
            if (!runObservedAt.isBefore(observedAt.minusDays(1))) {
                last1dProbeCountByTopic.merge(
                        fact.sourceObjectKey(),
                        fact.metricValue().intValue(),
                        Integer::sum
                );
            }
        }
        return new TopicLatestCoverageSnapshot(latestCoveredAtByTopic, last1dProbeCountByTopic);
    }

    private Map<String, Integer> aggregateTopicSampleSizes(List<KnowledgeAssetMetricRepository.MetricWindowFactView> facts) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (facts == null) {
            return result;
        }
        for (KnowledgeAssetMetricRepository.MetricWindowFactView fact : facts) {
            if (fact == null
                    || !METRIC_TOPIC.equals(fact.sourceObjectType())
                    || !METRIC_SAMPLE_SIZE.equals(fact.metricKey())
                    || !StringUtils.hasText(fact.sourceObjectKey())
                    || fact.numericValue() == null) {
                continue;
            }
            result.put(fact.sourceObjectKey(), Math.max(0, fact.numericValue().intValue()));
        }
        return result;
    }

    private int countCoveredTopics(List<KnowledgeAssetProblemRepository.TopicCatalogView> topics,
                                   Map<String, Integer> topicProbeCount7d) {
        if (topics == null || topics.isEmpty()) {
            return 0;
        }
        int coveredCount = 0;
        for (KnowledgeAssetProblemRepository.TopicCatalogView topic : topics) {
            if (topic != null && topicProbeCount7d.getOrDefault(topic.topicCatalogId(), 0) > 0) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    private int sumProbeCount(List<KnowledgeAssetRunRepository.RunDetailView> runs, OffsetDateTime windowStartAt) {
        if (runs == null || runs.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (KnowledgeAssetRunRepository.RunDetailView run : runs) {
            if (run == null || run.observedAt() == null || run.observedAt().isBefore(windowStartAt)) {
                continue;
            }
            total += safeInt(run.sampleSize());
        }
        return total;
    }

    private boolean hasTopicEditedAfter(List<KnowledgeAssetProblemRepository.TopicCatalogView> topics,
                                        Map<String, OffsetDateTime> configUpdatedAtBySourceTopicId,
                                        OffsetDateTime observedAt) {
        if (topics == null || observedAt == null) {
            return false;
        }
        for (KnowledgeAssetProblemRepository.TopicCatalogView topic : topics) {
            OffsetDateTime configUpdatedAt = topic == null
                    ? null
                    : configUpdatedAtBySourceTopicId.get(topic.sourceTopicId());
            if (configUpdatedAt != null && configUpdatedAt.isAfter(observedAt)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, OffsetDateTime> loadConfigUpdatedAtBySourceTopicId(
            String brandScopeId,
            List<KnowledgeAssetProblemRepository.TopicCatalogView> activeTopics) {
        Map<String, OffsetDateTime> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(brandScopeId) || activeTopics == null || activeTopics.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> activeSourceTopicIds = activeTopics.stream()
                .map(KnowledgeAssetProblemRepository.TopicCatalogView::sourceTopicId)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (activeSourceTopicIds.isEmpty()) {
            return result;
        }
        for (BrandSpaceTopic topic : brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandScopeId)
                .in(BrandSpaceTopic::getId, activeSourceTopicIds))) {
            if (topic == null || !StringUtils.hasText(topic.getId())) {
                continue;
            }
            mergeUpdatedAt(result, topic.getId(), toOffsetDateTime(topic.getUpdatedAt()));
            mergeUpdatedAt(result, topic.getId(), toOffsetDateTime(topic.getCreatedAt()));
        }
        for (BrandSpacePrompt prompt : brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandScopeId)
                .in(BrandSpacePrompt::getTopicId, activeSourceTopicIds))) {
            if (prompt == null || !StringUtils.hasText(prompt.getTopicId())) {
                continue;
            }
            mergeUpdatedAt(result, prompt.getTopicId(), toOffsetDateTime(prompt.getUpdatedAt()));
            mergeUpdatedAt(result, prompt.getTopicId(), toOffsetDateTime(prompt.getCreatedAt()));
        }
        return result;
    }

    private void mergeUpdatedAt(Map<String, OffsetDateTime> target,
                                String sourceTopicId,
                                OffsetDateTime candidateUpdatedAt) {
        if (target == null || !StringUtils.hasText(sourceTopicId) || candidateUpdatedAt == null) {
            return;
        }
        target.merge(sourceTopicId.trim(), candidateUpdatedAt, (current, candidate) ->
                current == null || candidate.isAfter(current) ? candidate : current);
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(DEFAULT_ZONE_ID).toOffsetDateTime();
    }

    private double freshnessSince(OffsetDateTime baseAt, OffsetDateTime observedAt, int maxDays) {
        if (baseAt == null || observedAt == null || maxDays <= 0) {
            return 1.0d;
        }
        if (baseAt.isAfter(observedAt)) {
            return 0.0d;
        }
        long hours = Duration.between(baseAt, observedAt).toHours();
        double days = hours / 24.0d;
        return clamp(days / maxDays);
    }

    private int safeInt(Integer raw) {
        return raw == null ? 0 : Math.max(0, raw);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return 0.0d;
        }
        return Math.min(value, 1.0d);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String toJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize schedule state payload", exception);
        }
    }

    private record TopicLatestCoverageSnapshot(Map<String, OffsetDateTime> latestCoveredAtByTopic,
                                               Map<String, Integer> last1dProbeCountByTopic) {
        private static TopicLatestCoverageSnapshot empty() {
            return new TopicLatestCoverageSnapshot(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    public record ScheduleStateMaterializationResult(String brandScopeId,
                                                     String latestRunId,
                                                     boolean skipped,
                                                     int topicStateCount,
                                                     String reason) {
        public static ScheduleStateMaterializationResult skipped(String brandScopeId, String reason) {
            return new ScheduleStateMaterializationResult(brandScopeId, null, true, 0, reason);
        }
    }
}
