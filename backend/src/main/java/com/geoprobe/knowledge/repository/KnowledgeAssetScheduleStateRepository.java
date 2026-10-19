package com.geoprobe.knowledge.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetScheduleStateRepository {

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertBrandState(BrandScheduleStateUpsert state) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_brand_space_schedule_state (
                    id,
                    brand_scope_id,
                    latest_run_id,
                    latest_run_at,
                    last_1d_probe_count,
                    last_7d_probe_count,
                    last_14d_probe_count,
                    active_topic_count,
                    covered_topic_count_7d,
                    coverage_gap_score,
                    freshness_score,
                    cold_start_flag,
                    just_edited_flag,
                    suggested_brand_weight,
                    state_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :latestRunId,
                    :latestRunAt,
                    :last1dProbeCount,
                    :last7dProbeCount,
                    :last14dProbeCount,
                    :activeTopicCount,
                    :coveredTopicCount7d,
                    :coverageGapScore,
                    :freshnessScore,
                    :coldStartFlag,
                    :justEditedFlag,
                    :suggestedBrandWeight,
                    CAST(:statePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id) DO UPDATE
                SET latest_run_id = EXCLUDED.latest_run_id,
                    latest_run_at = EXCLUDED.latest_run_at,
                    last_1d_probe_count = EXCLUDED.last_1d_probe_count,
                    last_7d_probe_count = EXCLUDED.last_7d_probe_count,
                    last_14d_probe_count = EXCLUDED.last_14d_probe_count,
                    active_topic_count = EXCLUDED.active_topic_count,
                    covered_topic_count_7d = EXCLUDED.covered_topic_count_7d,
                    coverage_gap_score = EXCLUDED.coverage_gap_score,
                    freshness_score = EXCLUDED.freshness_score,
                    cold_start_flag = EXCLUDED.cold_start_flag,
                    just_edited_flag = EXCLUDED.just_edited_flag,
                    suggested_brand_weight = EXCLUDED.suggested_brand_weight,
                    state_payload = EXCLUDED.state_payload,
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("id", state.id())
                .addValue("brandScopeId", state.brandScopeId())
                .addValue("latestRunId", state.latestRunId())
                .addValue("latestRunAt", state.latestRunAt())
                .addValue("last1dProbeCount", state.last1dProbeCount())
                .addValue("last7dProbeCount", state.last7dProbeCount())
                .addValue("last14dProbeCount", state.last14dProbeCount())
                .addValue("activeTopicCount", state.activeTopicCount())
                .addValue("coveredTopicCount7d", state.coveredTopicCount7d())
                .addValue("coverageGapScore", state.coverageGapScore())
                .addValue("freshnessScore", state.freshnessScore())
                .addValue("coldStartFlag", state.coldStartFlag())
                .addValue("justEditedFlag", state.justEditedFlag())
                .addValue("suggestedBrandWeight", state.suggestedBrandWeight())
                .addValue("statePayload", state.statePayloadJson()));
    }

    public void replaceTopicStates(String brandScopeId, List<TopicScheduleStateUpsert> topicStates) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_topic_schedule_state
                WHERE brand_scope_id = :brandScopeId
                """, new MapSqlParameterSource().addValue("brandScopeId", brandScopeId));
        if (topicStates == null || topicStates.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batchParameters = topicStates.stream()
                .map(state -> new MapSqlParameterSource()
                        .addValue("id", state.id())
                        .addValue("brandScopeId", state.brandScopeId())
                        .addValue("topicCatalogId", state.topicCatalogId())
                        .addValue("latestRunId", state.latestRunId())
                        .addValue("latestCoveredAt", state.latestCoveredAt())
                        .addValue("last1dProbeCount", state.last1dProbeCount())
                        .addValue("last7dProbeCount", state.last7dProbeCount())
                        .addValue("last14dProbeCount", state.last14dProbeCount())
                        .addValue("last14dSampleSize", state.last14dSampleSize())
                        .addValue("coverageFloor", state.coverageFloor())
                        .addValue("coverageDebtScore", state.coverageDebtScore())
                        .addValue("freshnessScore", state.freshnessScore())
                        .addValue("recentIntensityScore", state.recentIntensityScore())
                        .addValue("coldStartFlag", state.coldStartFlag())
                        .addValue("justEditedFlag", state.justEditedFlag())
                        .addValue("suggestedPriorityScore", state.suggestedPriorityScore())
                        .addValue("statePayload", state.statePayloadJson()))
                .toArray(MapSqlParameterSource[]::new);
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_topic_schedule_state (
                    id,
                    brand_scope_id,
                    topic_catalog_id,
                    latest_run_id,
                    latest_covered_at,
                    last_1d_probe_count,
                    last_7d_probe_count,
                    last_14d_probe_count,
                    last_14d_sample_size,
                    coverage_floor,
                    coverage_debt_score,
                    freshness_score,
                    recent_intensity_score,
                    cold_start_flag,
                    just_edited_flag,
                    suggested_priority_score,
                    state_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :topicCatalogId,
                    :latestRunId,
                    :latestCoveredAt,
                    :last1dProbeCount,
                    :last7dProbeCount,
                    :last14dProbeCount,
                    :last14dSampleSize,
                    :coverageFloor,
                    :coverageDebtScore,
                    :freshnessScore,
                    :recentIntensityScore,
                    :coldStartFlag,
                    :justEditedFlag,
                    :suggestedPriorityScore,
                    CAST(:statePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                """, batchParameters);
    }

    public BrandScheduleStateView findBrandState(String brandScopeId) {
        List<BrandScheduleStateView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT brand_scope_id,
                       latest_run_id,
                       latest_run_at,
                       last_1d_probe_count,
                       last_7d_probe_count,
                       last_14d_probe_count,
                       active_topic_count,
                       covered_topic_count_7d,
                       coverage_gap_score,
                       freshness_score,
                       cold_start_flag,
                       just_edited_flag,
                       suggested_brand_weight,
                       state_payload::text AS state_payload_json
                FROM ka_brand_space_schedule_state
                WHERE brand_scope_id = :brandScopeId
                LIMIT 1
                """, new MapSqlParameterSource().addValue("brandScopeId", brandScopeId), (resultSet, rowNum) ->
                new BrandScheduleStateView(
                        resultSet.getString("brand_scope_id"),
                        resultSet.getString("latest_run_id"),
                        resultSet.getObject("latest_run_at", OffsetDateTime.class),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_1d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_7d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_14d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("active_topic_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("covered_topic_count_7d")),
                        toDouble(resultSet.getObject("coverage_gap_score")),
                        toDouble(resultSet.getObject("freshness_score")),
                        resultSet.getBoolean("cold_start_flag"),
                        resultSet.getBoolean("just_edited_flag"),
                        toDouble(resultSet.getObject("suggested_brand_weight")),
                        resultSet.getString("state_payload_json")
                ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<TopicScheduleStateView> listTopicStates(String brandScopeId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT brand_scope_id,
                       topic_catalog_id,
                       latest_run_id,
                       latest_covered_at,
                       last_1d_probe_count,
                       last_7d_probe_count,
                       last_14d_probe_count,
                       last_14d_sample_size,
                       coverage_floor,
                       coverage_debt_score,
                       freshness_score,
                       recent_intensity_score,
                       cold_start_flag,
                       just_edited_flag,
                       suggested_priority_score,
                       state_payload::text AS state_payload_json
                FROM ka_topic_schedule_state
                WHERE brand_scope_id = :brandScopeId
                ORDER BY suggested_priority_score DESC NULLS LAST,
                         latest_covered_at ASC NULLS FIRST,
                         topic_catalog_id ASC
                """, new MapSqlParameterSource().addValue("brandScopeId", brandScopeId), (resultSet, rowNum) ->
                new TopicScheduleStateView(
                        resultSet.getString("brand_scope_id"),
                        resultSet.getString("topic_catalog_id"),
                        resultSet.getString("latest_run_id"),
                        resultSet.getObject("latest_covered_at", OffsetDateTime.class),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_1d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_7d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_14d_probe_count")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("last_14d_sample_size")),
                        KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("coverage_floor")),
                        toDouble(resultSet.getObject("coverage_debt_score")),
                        toDouble(resultSet.getObject("freshness_score")),
                        toDouble(resultSet.getObject("recent_intensity_score")),
                        resultSet.getBoolean("cold_start_flag"),
                        resultSet.getBoolean("just_edited_flag"),
                        toDouble(resultSet.getObject("suggested_priority_score")),
                        resultSet.getString("state_payload_json")
                ));
    }

    private Double toDouble(Object raw) {
        return raw instanceof Number number ? number.doubleValue() : null;
    }

    public record BrandScheduleStateUpsert(String id,
                                           String brandScopeId,
                                           String latestRunId,
                                           OffsetDateTime latestRunAt,
                                           int last1dProbeCount,
                                           int last7dProbeCount,
                                           int last14dProbeCount,
                                           int activeTopicCount,
                                           int coveredTopicCount7d,
                                           Double coverageGapScore,
                                           Double freshnessScore,
                                           boolean coldStartFlag,
                                           boolean justEditedFlag,
                                           Double suggestedBrandWeight,
                                           String statePayloadJson) {
    }

    public record TopicScheduleStateUpsert(String id,
                                           String brandScopeId,
                                           String topicCatalogId,
                                           String latestRunId,
                                           OffsetDateTime latestCoveredAt,
                                           int last1dProbeCount,
                                           int last7dProbeCount,
                                           int last14dProbeCount,
                                           int last14dSampleSize,
                                           int coverageFloor,
                                           Double coverageDebtScore,
                                           Double freshnessScore,
                                           Double recentIntensityScore,
                                           boolean coldStartFlag,
                                           boolean justEditedFlag,
                                           Double suggestedPriorityScore,
                                           String statePayloadJson) {
    }

    public record BrandScheduleStateView(String brandScopeId,
                                         String latestRunId,
                                         OffsetDateTime latestRunAt,
                                         Integer last1dProbeCount,
                                         Integer last7dProbeCount,
                                         Integer last14dProbeCount,
                                         Integer activeTopicCount,
                                         Integer coveredTopicCount7d,
                                         Double coverageGapScore,
                                         Double freshnessScore,
                                         boolean coldStartFlag,
                                         boolean justEditedFlag,
                                         Double suggestedBrandWeight,
                                         String statePayloadJson) {
    }

    public record TopicScheduleStateView(String brandScopeId,
                                         String topicCatalogId,
                                         String latestRunId,
                                         OffsetDateTime latestCoveredAt,
                                         Integer last1dProbeCount,
                                         Integer last7dProbeCount,
                                         Integer last14dProbeCount,
                                         Integer last14dSampleSize,
                                         Integer coverageFloor,
                                         Double coverageDebtScore,
                                         Double freshnessScore,
                                         Double recentIntensityScore,
                                         boolean coldStartFlag,
                                         boolean justEditedFlag,
                                         Double suggestedPriorityScore,
                                         String statePayloadJson) {
    }
}
