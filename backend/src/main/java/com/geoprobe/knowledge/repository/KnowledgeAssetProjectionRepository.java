package com.geoprobe.knowledge.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetProjectionRepository {

    private static final String PROJECTION_KEY_DEFAULT = "default";

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertBrandPerformanceProjection(ProjectionUpsert projectionUpsert) {
        upsertProjection("ka_projection_brand_performance", projectionUpsert);
    }

    public void upsertCitationSourcesProjection(ProjectionUpsert projectionUpsert) {
        upsertProjection("ka_projection_citation_sources", projectionUpsert);
    }

    public void upsertProblemAnalysisProjection(ProjectionUpsert projectionUpsert) {
        upsertProjection("ka_projection_problem_analysis", projectionUpsert);
    }

    public void upsertMetricSlicesProjection(ProjectionUpsert projectionUpsert) {
        upsertProjection("ka_projection_metric_slices", projectionUpsert);
    }

    public ProjectionPayloadView findBrandPerformanceProjection(String brandScopeId) {
        return findProjection("ka_projection_brand_performance", brandScopeId, projectionKey("brand-performance"));
    }

    public ProjectionPayloadView findCitationSourcesProjection(String brandScopeId) {
        return findProjection("ka_projection_citation_sources", brandScopeId, projectionKey("citation-sources"));
    }

    public ProjectionPayloadView findProblemAnalysisProjection(String brandScopeId) {
        return findProjection("ka_projection_problem_analysis", brandScopeId, projectionKey("problem-analysis"));
    }

    public ProjectionPayloadView findMetricSlicesProjection(String brandScopeId) {
        return findProjection("ka_projection_metric_slices", brandScopeId, projectionKey("metric-slices"));
    }

    private void upsertProjection(String tableName, ProjectionUpsert projectionUpsert) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO %s (
                    id,
                    brand_scope_id,
                    projection_key,
                    projection_version,
                    source_window_key,
                    source_run_id,
                    payload,
                    generated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :projectionKey,
                    :projectionVersion,
                    :sourceWindowKey,
                    :sourceRunId,
                    CAST(:payload AS jsonb),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, projection_key) DO UPDATE
                SET projection_version = EXCLUDED.projection_version,
                    source_window_key = EXCLUDED.source_window_key,
                    source_run_id = EXCLUDED.source_run_id,
                    payload = EXCLUDED.payload,
                    generated_at = NOW()
                """.formatted(tableName), new MapSqlParameterSource()
                .addValue("id", projectionUpsert.id())
                .addValue("brandScopeId", projectionUpsert.brandScopeId())
                .addValue("projectionKey", projectionUpsert.projectionKey())
                .addValue("projectionVersion", projectionUpsert.projectionVersion())
                .addValue("sourceWindowKey", projectionUpsert.sourceWindowKey())
                .addValue("sourceRunId", projectionUpsert.sourceRunId())
                .addValue("payload", projectionUpsert.payloadJson()));
    }

    private ProjectionPayloadView findProjection(String tableName, String brandScopeId, String projectionKey) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT projection_version,
                       source_window_key,
                       source_run_id,
                       payload
                FROM %s
                WHERE brand_scope_id = :brandScopeId
                  AND projection_key = :projectionKey
                LIMIT 1
                """.formatted(tableName), new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("projectionKey", projectionKey), resultSet -> {
            if (!resultSet.next()) {
                return null;
            }
            return new ProjectionPayloadView(
                    resultSet.getString("projection_version"),
                    resultSet.getString("source_window_key"),
                    resultSet.getString("source_run_id"),
                    resultSet.getString("payload")
            );
        });
    }

    private String projectionKey(String projectionName) {
        return "%s:%s".formatted(PROJECTION_KEY_DEFAULT, projectionName);
    }

    public record ProjectionUpsert(String id,
                                   String brandScopeId,
                                   String projectionKey,
                                   String projectionVersion,
                                   String sourceWindowKey,
                                   String sourceRunId,
                                   String payloadJson) {
    }

    public record ProjectionPayloadView(String projectionVersion,
                                        String sourceWindowKey,
                                        String sourceRunId,
                                        String payloadJson) {
    }
}
