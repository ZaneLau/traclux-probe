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
public class KnowledgeAssetRunRepository {

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertRun(KnowledgeAssetRunUpsert runUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", runUpsert.id())
                .addValue("organizationId", runUpsert.organizationId())
                .addValue("brandScopeId", runUpsert.brandScopeId())
                .addValue("sourceTaskId", runUpsert.sourceTaskId())
                .addValue("sourceReportId", runUpsert.sourceReportId())
                .addValue("engineVersion", runUpsert.engineVersion())
                .addValue("targetModelProduct", runUpsert.targetModelProduct())
                .addValue("sampleSize", runUpsert.sampleSize())
                .addValue("reportGeneratedAt", runUpsert.reportGeneratedAt())
                .addValue("runStartedAt", runUpsert.runStartedAt())
                .addValue("runCompletedAt", runUpsert.runCompletedAt())
                .addValue("taskSnapshot", runUpsert.taskSnapshotJson())
                .addValue("reportProjection", runUpsert.reportProjectionJson())
                .addValue("summaryPayload", runUpsert.summaryPayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_runs (
                    id,
                    organization_id,
                    brand_scope_id,
                    source_task_id,
                    source_report_id,
                    engine_version,
                    target_model_product,
                    sample_size,
                    report_generated_at,
                    run_started_at,
                    run_completed_at,
                    task_snapshot,
                    report_projection,
                    summary_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :sourceTaskId,
                    :sourceReportId,
                    :engineVersion,
                    :targetModelProduct,
                    :sampleSize,
                    :reportGeneratedAt,
                    :runStartedAt,
                    :runCompletedAt,
                    CAST(:taskSnapshot AS jsonb),
                    CAST(:reportProjection AS jsonb),
                    CAST(:summaryPayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (source_task_id) DO UPDATE
                SET source_report_id = EXCLUDED.source_report_id,
                    engine_version = EXCLUDED.engine_version,
                    target_model_product = EXCLUDED.target_model_product,
                    sample_size = EXCLUDED.sample_size,
                    report_generated_at = EXCLUDED.report_generated_at,
                    run_started_at = EXCLUDED.run_started_at,
                    run_completed_at = EXCLUDED.run_completed_at,
                    task_snapshot = EXCLUDED.task_snapshot,
                    report_projection = EXCLUDED.report_projection,
                    summary_payload = EXCLUDED.summary_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void upsertCheckpoint(KnowledgeAssetCheckpointUpsert checkpointUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", checkpointUpsert.id())
                .addValue("brandScopeId", checkpointUpsert.brandScopeId())
                .addValue("sourceTaskId", checkpointUpsert.sourceTaskId())
                .addValue("sourceReportId", checkpointUpsert.sourceReportId())
                .addValue("sourceUpdatedAt", checkpointUpsert.sourceUpdatedAt())
                .addValue("ingestionStatus", checkpointUpsert.ingestionStatus())
                .addValue("checkpointPayload", checkpointUpsert.checkpointPayloadJson())
                .addValue("lastErrorCode", checkpointUpsert.lastErrorCode())
                .addValue("lastErrorMessage", checkpointUpsert.lastErrorMessage());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_ingestion_checkpoints (
                    id,
                    brand_scope_id,
                    source_task_id,
                    source_report_id,
                    source_updated_at,
                    ingestion_status,
                    checkpoint_payload,
                    last_error_code,
                    last_error_message,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :sourceTaskId,
                    :sourceReportId,
                    :sourceUpdatedAt,
                    :ingestionStatus,
                    CAST(:checkpointPayload AS jsonb),
                    :lastErrorCode,
                    :lastErrorMessage,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, source_task_id) DO UPDATE
                SET source_report_id = EXCLUDED.source_report_id,
                    source_updated_at = EXCLUDED.source_updated_at,
                    ingestion_status = EXCLUDED.ingestion_status,
                    checkpoint_payload = EXCLUDED.checkpoint_payload,
                    last_error_code = EXCLUDED.last_error_code,
                    last_error_message = EXCLUDED.last_error_message,
                    updated_at = NOW()
                """, parameters);
    }

    public LatestRunView findLatestRunByScope(String brandScopeId) {
        List<LatestRunView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       source_task_id,
                       source_report_id,
                       sample_size,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                ORDER BY observed_at DESC, created_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new LatestRunView(
                resultSet.getString("id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public RunDetailView findLatestRunDetailByScope(String brandScopeId) {
        List<RunDetailView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot::text AS task_snapshot_json,
                       report_projection::text AS report_projection_json,
                       summary_payload::text AS summary_payload_json,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                ORDER BY observed_at DESC, created_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public RunDetailView findRunDetailByRunId(String runId) {
        List<RunDetailView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot::text AS task_snapshot_json,
                       report_projection::text AS report_projection_json,
                       summary_payload::text AS summary_payload_json,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE id = :runId
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("runId", runId), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public RunDetailView findRunDetailBySourceTaskId(String sourceTaskId) {
        List<RunDetailView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot::text AS task_snapshot_json,
                       report_projection::text AS report_projection_json,
                       summary_payload::text AS summary_payload_json,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE source_task_id = :sourceTaskId
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("sourceTaskId", sourceTaskId), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<RunDetailView> listRecentRunDetailsByScope(String brandScopeId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot::text AS task_snapshot_json,
                       report_projection::text AS report_projection_json,
                       summary_payload::text AS summary_payload_json,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                ORDER BY observed_at DESC, created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("limit", limit), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
    }

    public List<String> listSourceTaskIdsByScope(String brandScopeId) {
        if (brandScopeId == null || brandScopeId.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT source_task_id
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                  AND source_task_id IS NOT NULL
                ORDER BY COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) ASC, created_at ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) ->
                resultSet.getString("source_task_id"));
    }

    public List<RunDetailView> findLatestRunDetailsByScopeIds(List<String> brandScopeIds) {
        if (brandScopeIds == null || brandScopeIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot_json,
                       report_projection_json,
                       summary_payload_json,
                       observed_at
                FROM (
                    SELECT id,
                           organization_id,
                           brand_scope_id,
                           source_task_id,
                           source_report_id,
                           target_model_product,
                           sample_size,
                           task_snapshot::text AS task_snapshot_json,
                           report_projection::text AS report_projection_json,
                           summary_payload::text AS summary_payload_json,
                           COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY brand_scope_id
                               ORDER BY COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) DESC,
                                        created_at DESC
                           ) AS row_no
                    FROM ka_runs
                    WHERE brand_scope_id IN (:brandScopeIds)
                ) ranked
                WHERE row_no = 1
                """, new MapSqlParameterSource()
                .addValue("brandScopeIds", brandScopeIds), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
    }

    public List<RunDetailView> listRunDetailsByScopeWithinWindow(String brandScopeId,
                                                                 OffsetDateTime windowStartAt,
                                                                 OffsetDateTime windowEndAt) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       organization_id,
                       brand_scope_id,
                       source_task_id,
                       source_report_id,
                       target_model_product,
                       sample_size,
                       task_snapshot::text AS task_snapshot_json,
                       report_projection::text AS report_projection_json,
                       summary_payload::text AS summary_payload_json,
                       COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                FROM ka_runs
                WHERE brand_scope_id = :brandScopeId
                  AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) >= :windowStartAt
                  AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) <= :windowEndAt
                ORDER BY observed_at DESC, created_at DESC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowStartAt", windowStartAt)
                .addValue("windowEndAt", windowEndAt), (resultSet, rowNum) -> new RunDetailView(
                resultSet.getString("id"),
                resultSet.getString("organization_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getString("source_report_id"),
                resultSet.getString("target_model_product"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_size")),
                resultSet.getString("task_snapshot_json"),
                resultSet.getString("report_projection_json"),
                resultSet.getString("summary_payload_json"),
                resultSet.getObject("observed_at", OffsetDateTime.class)
        ));
    }

    public record KnowledgeAssetRunUpsert(String id,
                                          String organizationId,
                                          String brandScopeId,
                                          String sourceTaskId,
                                          String sourceReportId,
                                          String engineVersion,
                                          String targetModelProduct,
                                          Integer sampleSize,
                                          OffsetDateTime reportGeneratedAt,
                                          OffsetDateTime runStartedAt,
                                          OffsetDateTime runCompletedAt,
                                          String taskSnapshotJson,
                                          String reportProjectionJson,
                                          String summaryPayloadJson) {
    }

    public record KnowledgeAssetCheckpointUpsert(String id,
                                                 String brandScopeId,
                                                 String sourceTaskId,
                                                 String sourceReportId,
                                                 OffsetDateTime sourceUpdatedAt,
                                                 String ingestionStatus,
                                                 String checkpointPayloadJson,
                                                 String lastErrorCode,
                                                 String lastErrorMessage) {
    }

    public record LatestRunView(String runId,
                                String sourceTaskId,
                                String sourceReportId,
                                Integer sampleSize,
                                OffsetDateTime observedAt) {
    }

    public record RunDetailView(String runId,
                                String organizationId,
                                String brandScopeId,
                                String sourceTaskId,
                                String sourceReportId,
                                String targetModelProduct,
                                Integer sampleSize,
                                String taskSnapshotJson,
                                String reportProjectionJson,
                                String summaryPayloadJson,
                                OffsetDateTime observedAt) {
    }
}
