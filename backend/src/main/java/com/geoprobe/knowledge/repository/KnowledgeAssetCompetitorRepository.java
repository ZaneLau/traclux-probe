package com.geoprobe.knowledge.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetCompetitorRepository {

    private static final String CONFIG_PROFILE_SOURCE_TYPE = "CONFIG_PROFILE";

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertConfiguredCompetitor(ConfiguredCompetitorUpsert competitorUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", competitorUpsert.id())
                .addValue("organizationId", competitorUpsert.organizationId())
                .addValue("brandScopeId", competitorUpsert.brandScopeId())
                .addValue("competitorKey", competitorUpsert.competitorKey())
                .addValue("competitorName", competitorUpsert.competitorName())
                .addValue("firstSeenAt", competitorUpsert.firstSeenAt())
                .addValue("lastSeenAt", competitorUpsert.lastSeenAt())
                .addValue("sourcePayload", competitorUpsert.sourcePayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_competitor_catalog (
                    id,
                    organization_id,
                    brand_scope_id,
                    competitor_key,
                    competitor_name,
                    alias_names,
                    source_type,
                    active_flag,
                    first_seen_at,
                    last_seen_at,
                    source_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :competitorKey,
                    :competitorName,
                    '[]'::jsonb,
                    'CONFIG_PROFILE',
                    TRUE,
                    :firstSeenAt,
                    :lastSeenAt,
                    CAST(:sourcePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, competitor_key) DO UPDATE
                SET competitor_name = EXCLUDED.competitor_name,
                    alias_names = EXCLUDED.alias_names,
                    source_type = EXCLUDED.source_type,
                    active_flag = TRUE,
                    first_seen_at = COALESCE(ka_competitor_catalog.first_seen_at, EXCLUDED.first_seen_at),
                    last_seen_at = EXCLUDED.last_seen_at,
                    source_payload = EXCLUDED.source_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void deactivateMissingConfiguredCompetitors(String brandScopeId, List<String> activeCompetitorKeys) {
        if (activeCompetitorKeys == null || activeCompetitorKeys.isEmpty()) {
            knowledgeAssetNamedParameterJdbcTemplate.update("""
                    UPDATE ka_competitor_catalog
                    SET active_flag = FALSE,
                        updated_at = NOW()
                    WHERE brand_scope_id = :brandScopeId
                      AND source_type = :sourceType
                    """, new MapSqlParameterSource()
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("sourceType", CONFIG_PROFILE_SOURCE_TYPE));
            return;
        }
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                UPDATE ka_competitor_catalog
                SET active_flag = FALSE,
                    updated_at = NOW()
                WHERE brand_scope_id = :brandScopeId
                  AND source_type = :sourceType
                  AND competitor_key NOT IN (:activeCompetitorKeys)
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("sourceType", CONFIG_PROFILE_SOURCE_TYPE)
                .addValue("activeCompetitorKeys", activeCompetitorKeys));
    }

    public void replaceSnapshotFacts(String runId,
                                     String brandScopeId,
                                     List<CompetitorSnapshotFactUpsert> snapshotFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_competitor_snapshot_facts
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (snapshotFacts == null || snapshotFacts.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batchParameters = snapshotFacts.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("id", item.id())
                        .addValue("runId", runId)
                        .addValue("brandScopeId", brandScopeId)
                        .addValue("competitorKey", item.competitorKey())
                        .addValue("competitorName", item.competitorName())
                        .addValue("rankNo", item.rankNo())
                        .addValue("visibilityScore", item.visibilityScore())
                        .addValue("recommendRate", item.recommendRate())
                        .addValue("mentionRate", item.mentionRate())
                        .addValue("top3Rate", item.top3Rate())
                        .addValue("averagePosition", item.averagePosition())
                        .addValue("mentionCount", item.mentionCount())
                        .addValue("factPayload", item.factPayloadJson()))
                .toArray(MapSqlParameterSource[]::new);
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_competitor_snapshot_facts (
                    id,
                    run_id,
                    brand_scope_id,
                    competitor_key,
                    competitor_name,
                    rank_no,
                    visibility_score,
                    recommend_rate,
                    mention_rate,
                    top3_rate,
                    average_position,
                    mention_count,
                    fact_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :brandScopeId,
                    :competitorKey,
                    :competitorName,
                    :rankNo,
                    :visibilityScore,
                    :recommendRate,
                    :mentionRate,
                    :top3Rate,
                    :averagePosition,
                    :mentionCount,
                    CAST(:factPayload AS jsonb),
                    NOW()
                )
                """, batchParameters);
    }

    public List<WindowRunHead> listRecentRunsByScope(String brandScopeId,
                                                     OffsetDateTime observedAt,
                                                     int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       sample_size,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                  AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) <= :observedAt
                ORDER BY observed_at DESC, created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("observedAt", observedAt)
                .addValue("limit", limit), (resultSet, rowNum) -> new WindowRunHead(
                resultSet.getString("id"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
    }

    public List<WindowRunHead> listRunsByScopeWithinWindow(String brandScopeId,
                                                           OffsetDateTime windowStartAt,
                                                           OffsetDateTime windowEndAt) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       sample_size,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                  AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) >= :windowStartAt
                  AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) <= :windowEndAt
                ORDER BY observed_at DESC, created_at DESC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowStartAt", windowStartAt)
                .addValue("windowEndAt", windowEndAt), (resultSet, rowNum) -> new WindowRunHead(
                resultSet.getString("id"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
    }

    public List<ConfiguredCompetitorView> listActiveConfiguredCompetitors(String brandScopeId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT competitor_key,
                       competitor_name
                FROM ka_competitor_catalog
                WHERE brand_scope_id = :brandScopeId
                  AND active_flag = TRUE
                ORDER BY competitor_name ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new ConfiguredCompetitorView(
                resultSet.getString("competitor_key"),
                resultSet.getString("competitor_name")
        ));
    }

    public List<CompetitorSnapshotFactView> listSnapshotFactsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       competitor_key,
                       competitor_name,
                       rank_no,
                       visibility_score,
                       recommend_rate,
                       mention_rate,
                       top3_rate,
                       average_position,
                       mention_count
                FROM ka_competitor_snapshot_facts
                WHERE run_id IN (:runIds)
                ORDER BY run_id ASC, rank_no ASC NULLS LAST, competitor_name ASC
                """, new MapSqlParameterSource()
                .addValue("runIds", runIds), (resultSet, rowNum) -> new CompetitorSnapshotFactView(
                resultSet.getString("run_id"),
                resultSet.getString("competitor_key"),
                resultSet.getString("competitor_name"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("rank_no")),
                asDouble(resultSet.getObject("visibility_score")),
                asDouble(resultSet.getObject("recommend_rate")),
                asDouble(resultSet.getObject("mention_rate")),
                asDouble(resultSet.getObject("top3_rate")),
                asDouble(resultSet.getObject("average_position")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("mention_count"))
        ));
    }

    public void replaceWindowFacts(String brandScopeId,
                                   String windowKey,
                                   OffsetDateTime windowStartAt,
                                   OffsetDateTime windowEndAt,
                                   List<CompetitorWindowFactUpsert> windowFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_competitor_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey));
        if (windowFacts == null || windowFacts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (CompetitorWindowFactUpsert fact : windowFacts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", fact.id())
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("windowKey", windowKey)
                    .addValue("windowStartAt", windowStartAt)
                    .addValue("windowEndAt", windowEndAt)
                    .addValue("competitorKey", fact.competitorKey())
                    .addValue("competitorName", fact.competitorName())
                    .addValue("configuredFlag", fact.configuredFlag())
                    .addValue("runCount", fact.runCount())
                    .addValue("mentionProbeCount", fact.mentionProbeCount())
                    .addValue("recommendProbeCount", fact.recommendProbeCount())
                    .addValue("avgVisibilityScore", fact.avgVisibilityScore())
                    .addValue("avgRecommendRate", fact.avgRecommendRate())
                    .addValue("avgMentionRate", fact.avgMentionRate())
                    .addValue("avgTop3Rate", fact.avgTop3Rate())
                    .addValue("avgAveragePosition", fact.avgAveragePosition())
                    .addValue("factPayload", fact.factPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_competitor_window_facts (
                    id,
                    brand_scope_id,
                    window_key,
                    window_start_at,
                    window_end_at,
                    competitor_key,
                    competitor_name,
                    configured_flag,
                    run_count,
                    mention_probe_count,
                    recommend_probe_count,
                    avg_visibility_score,
                    avg_recommend_rate,
                    avg_mention_rate,
                    avg_top3_rate,
                    avg_average_position,
                    fact_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :windowKey,
                    :windowStartAt,
                    :windowEndAt,
                    :competitorKey,
                    :competitorName,
                    :configuredFlag,
                    :runCount,
                    :mentionProbeCount,
                    :recommendProbeCount,
                    :avgVisibilityScore,
                    :avgRecommendRate,
                    :avgMentionRate,
                    :avgTop3Rate,
                    :avgAveragePosition,
                    CAST(:factPayload AS jsonb),
                    NOW(),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public List<CompetitorWindowFactView> listWindowFacts(String brandScopeId, String windowKey) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT competitor_key,
                       competitor_name,
                       configured_flag,
                       run_count,
                       mention_probe_count,
                       recommend_probe_count,
                       avg_visibility_score,
                       avg_recommend_rate,
                       avg_mention_rate,
                       avg_top3_rate,
                       avg_average_position
                FROM ka_competitor_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                ORDER BY configured_flag DESC,
                         avg_visibility_score DESC NULLS LAST,
                         mention_probe_count DESC,
                         competitor_name ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey), (resultSet, rowNum) -> new CompetitorWindowFactView(
                resultSet.getString("competitor_key"),
                resultSet.getString("competitor_name"),
                resultSet.getBoolean("configured_flag"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("run_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("mention_probe_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("recommend_probe_count")),
                asDouble(resultSet.getObject("avg_visibility_score")),
                asDouble(resultSet.getObject("avg_recommend_rate")),
                asDouble(resultSet.getObject("avg_mention_rate")),
                asDouble(resultSet.getObject("avg_top3_rate")),
                asDouble(resultSet.getObject("avg_average_position"))
        ));
    }

    private Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public record ConfiguredCompetitorUpsert(String id,
                                             String organizationId,
                                             String brandScopeId,
                                             String competitorKey,
                                             String competitorName,
                                             OffsetDateTime firstSeenAt,
                                             OffsetDateTime lastSeenAt,
                                             String sourcePayloadJson) {
    }

    public record CompetitorSnapshotFactUpsert(String id,
                                               String competitorKey,
                                               String competitorName,
                                               Integer rankNo,
                                               Double visibilityScore,
                                               Double recommendRate,
                                               Double mentionRate,
                                               Double top3Rate,
                                               Double averagePosition,
                                               Integer mentionCount,
                                               String factPayloadJson) {
    }

    public record WindowRunHead(String runId,
                                Integer sampleSize,
                                OffsetDateTime observedAt) {
    }

    public record ConfiguredCompetitorView(String competitorKey,
                                           String competitorName) {
    }

    public record CompetitorSnapshotFactView(String runId,
                                             String competitorKey,
                                             String competitorName,
                                             Integer rankNo,
                                             Double visibilityScore,
                                             Double recommendRate,
                                             Double mentionRate,
                                             Double top3Rate,
                                             Double averagePosition,
                                             Integer mentionCount) {
    }

    public record CompetitorWindowFactView(String competitorKey,
                                           String competitorName,
                                           boolean configuredFlag,
                                           Integer runCount,
                                           Integer mentionProbeCount,
                                           Integer recommendProbeCount,
                                           Double avgVisibilityScore,
                                           Double avgRecommendRate,
                                           Double avgMentionRate,
                                           Double avgTop3Rate,
                                           Double avgAveragePosition) {
    }

    public record CompetitorWindowFactUpsert(String id,
                                             String competitorKey,
                                             String competitorName,
                                             boolean configuredFlag,
                                             int runCount,
                                             int mentionProbeCount,
                                             int recommendProbeCount,
                                             Double avgVisibilityScore,
                                             Double avgRecommendRate,
                                             Double avgMentionRate,
                                             Double avgTop3Rate,
                                             Double avgAveragePosition,
                                             String factPayloadJson) {
    }
}
