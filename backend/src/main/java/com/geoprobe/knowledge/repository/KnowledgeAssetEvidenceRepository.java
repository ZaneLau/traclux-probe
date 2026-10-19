package com.geoprobe.knowledge.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetEvidenceRepository {

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void replaceProbes(String runId, List<ProbeUpsert> probes) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_probes
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (probes == null || probes.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (ProbeUpsert probe : probes) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", probe.id())
                    .addValue("runId", runId)
                    .addValue("sourceProbeId", probe.sourceProbeId())
                    .addValue("probeRef", probe.probeRef())
                    .addValue("seqNo", probe.seqNo())
                    .addValue("topicCatalogId", probe.topicCatalogId())
                    .addValue("promptCatalogId", probe.promptCatalogId())
                    .addValue("problemSpaceBucket", probe.problemSpaceBucket())
                    .addValue("renderText", probe.renderText())
                    .addValue("probePayload", probe.probePayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_probes (
                    id,
                    run_id,
                    source_probe_id,
                    probe_ref,
                    seq_no,
                    topic_catalog_id,
                    prompt_catalog_id,
                    problem_space_bucket,
                    render_text,
                    probe_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :sourceProbeId,
                    :probeRef,
                    :seqNo,
                    :topicCatalogId,
                    :promptCatalogId,
                    :problemSpaceBucket,
                    :renderText,
                    CAST(:probePayload AS jsonb),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public void replaceAttempts(String runId, List<AttemptUpsert> attempts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_attempts
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (attempts == null || attempts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (AttemptUpsert attempt : attempts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", attempt.id())
                    .addValue("runId", runId)
                    .addValue("probeId", attempt.probeId())
                    .addValue("sourceAttemptId", attempt.sourceAttemptId())
                    .addValue("provider", attempt.provider())
                    .addValue("modelId", attempt.modelId())
                    .addValue("statusCode", attempt.statusCode())
                    .addValue("selectedFinal", attempt.selectedFinal())
                    .addValue("rawResponseText", attempt.rawResponseText())
                    .addValue("responsePreview", attempt.responsePreview())
                    .addValue("citationsPayload", attempt.citationsPayloadJson())
                    .addValue("responseMetadata", attempt.responseMetadataJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_attempts (
                    id,
                    run_id,
                    probe_id,
                    source_attempt_id,
                    provider,
                    model_id,
                    status_code,
                    selected_final,
                    raw_response_text,
                    response_preview,
                    citations_payload,
                    response_metadata,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :probeId,
                    :sourceAttemptId,
                    :provider,
                    :modelId,
                    :statusCode,
                    :selectedFinal,
                    :rawResponseText,
                    :responsePreview,
                    CAST(:citationsPayload AS jsonb),
                    CAST(:responseMetadata AS jsonb),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public void replaceBrandMentions(String runId, List<BrandMentionUpsert> brandMentions) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_brand_mentions
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (brandMentions == null || brandMentions.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (BrandMentionUpsert brandMention : brandMentions) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", brandMention.id())
                    .addValue("runId", runId)
                    .addValue("probeId", brandMention.probeId())
                    .addValue("attemptId", brandMention.attemptId())
                    .addValue("sourceMentionId", brandMention.sourceMentionId())
                    .addValue("brandKey", brandMention.brandKey())
                    .addValue("brandName", brandMention.brandName())
                    .addValue("brandRole", brandMention.brandRole())
                    .addValue("mentionedFlag", brandMention.mentionedFlag())
                    .addValue("recommendedFlag", brandMention.recommendedFlag())
                    .addValue("sentimentCode", brandMention.sentimentCode())
                    .addValue("positionIndex", brandMention.positionIndex())
                    .addValue("confidenceScore", brandMention.confidenceScore())
                    .addValue("mentionPayload", brandMention.mentionPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_brand_mentions (
                    id,
                    run_id,
                    probe_id,
                    attempt_id,
                    source_mention_id,
                    brand_key,
                    brand_name,
                    brand_role,
                    mentioned_flag,
                    recommended_flag,
                    sentiment_code,
                    position_index,
                    confidence_score,
                    mention_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :probeId,
                    :attemptId,
                    :sourceMentionId,
                    :brandKey,
                    :brandName,
                    :brandRole,
                    :mentionedFlag,
                    :recommendedFlag,
                    :sentimentCode,
                    :positionIndex,
                    :confidenceScore,
                    CAST(:mentionPayload AS jsonb),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public List<ProbeView> listProbesByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       run_id,
                       source_probe_id,
                       probe_ref,
                       seq_no,
                       topic_catalog_id,
                       prompt_catalog_id,
                       problem_space_bucket,
                       render_text
                FROM ka_probes
                WHERE run_id = :runId
                ORDER BY seq_no ASC NULLS LAST, created_at ASC
                """, new MapSqlParameterSource()
                .addValue("runId", runId), (resultSet, rowNum) -> new ProbeView(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("source_probe_id"),
                resultSet.getString("probe_ref"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("seq_no")),
                resultSet.getString("topic_catalog_id"),
                resultSet.getString("prompt_catalog_id"),
                resultSet.getString("problem_space_bucket"),
                resultSet.getString("render_text")
        ));
    }

    public List<AttemptView> listAttemptsByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       run_id,
                       probe_id,
                       source_attempt_id,
                       provider,
                       model_id,
                       status_code,
                       selected_final,
                       raw_response_text,
                       response_preview,
                       citations_payload::text AS citations_payload_json,
                       response_metadata::text AS response_metadata_json,
                       created_at
                FROM ka_attempts
                WHERE run_id = :runId
                ORDER BY selected_final DESC, created_at DESC
                """, new MapSqlParameterSource()
                .addValue("runId", runId), (resultSet, rowNum) -> new AttemptView(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("probe_id"),
                resultSet.getString("source_attempt_id"),
                resultSet.getString("provider"),
                resultSet.getString("model_id"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("status_code")),
                resultSet.getObject("selected_final") == null ? null : resultSet.getBoolean("selected_final"),
                resultSet.getString("raw_response_text"),
                resultSet.getString("response_preview"),
                resultSet.getString("citations_payload_json"),
                resultSet.getString("response_metadata_json"),
                KnowledgeAssetJdbcReaders.toLocalDateTime(resultSet.getObject("created_at"))
        ));
    }

    public List<BrandMentionView> listBrandMentionsByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       run_id,
                       probe_id,
                       attempt_id,
                       source_mention_id,
                       brand_key,
                       brand_name,
                       brand_role,
                       mentioned_flag,
                       recommended_flag,
                       sentiment_code,
                       position_index,
                       confidence_score,
                       mention_payload::text AS mention_payload_json,
                       created_at
                FROM ka_brand_mentions
                WHERE run_id = :runId
                ORDER BY created_at ASC
                """, new MapSqlParameterSource()
                .addValue("runId", runId), (resultSet, rowNum) -> new BrandMentionView(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("probe_id"),
                resultSet.getString("attempt_id"),
                resultSet.getString("source_mention_id"),
                resultSet.getString("brand_key"),
                resultSet.getString("brand_name"),
                resultSet.getString("brand_role"),
                resultSet.getObject("mentioned_flag") == null ? null : resultSet.getBoolean("mentioned_flag"),
                resultSet.getObject("recommended_flag") == null ? null : resultSet.getBoolean("recommended_flag"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sentiment_code")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("position_index")),
                resultSet.getBigDecimal("confidence_score"),
                resultSet.getString("mention_payload_json"),
                KnowledgeAssetJdbcReaders.toLocalDateTime(resultSet.getObject("created_at"))
        ));
    }

    public MetricSampleRowsResult listMetricSampleRows(String brandScopeId,
                                                       String brandName,
                                                       OffsetDateTime windowStartAt,
                                                       OffsetDateTime windowEndAt,
                                                       int offset,
                                                       int limit) {
        if (brandScopeId == null || brandScopeId.isBlank() || windowStartAt == null || windowEndAt == null || limit <= 0) {
            return MetricSampleRowsResult.empty();
        }
        List<MetricSampleRowView> rows = knowledgeAssetNamedParameterJdbcTemplate.query("""
                WITH scoped_runs AS (
                    SELECT id,
                           source_task_id,
                           COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) AS observed_at
                    FROM ka_runs
                    WHERE brand_scope_id = :brandScopeId
                      AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) >= :windowStartAt
                      AND COALESCE(report_generated_at, run_completed_at, run_started_at, created_at) <= :windowEndAt
                ),
                target_mentions AS (
                    SELECT mentions.run_id,
                           mentions.probe_id,
                           BOOL_OR(COALESCE(mentions.mentioned_flag, FALSE)) AS mentioned,
                           BOOL_OR(COALESCE(mentions.recommended_flag, FALSE)) AS recommended,
                           MIN(mentions.position_index) FILTER (WHERE mentions.position_index IS NOT NULL) AS recommendation_rank
                    FROM ka_brand_mentions mentions
                    JOIN scoped_runs runs ON runs.id = mentions.run_id
                    WHERE (
                        mentions.brand_role = '0'
                        OR (:brandName IS NOT NULL AND LOWER(mentions.brand_name) = LOWER(:brandName))
                    )
                    GROUP BY mentions.run_id, mentions.probe_id
                ),
                sample_rows AS (
                    SELECT ROW_NUMBER() OVER (
                               ORDER BY (
                                            target_mentions.recommendation_rank IS NOT NULL
                                            AND target_mentions.recommendation_rank BETWEEN 1 AND 3
                                        ) DESC,
                                        COALESCE(target_mentions.recommended, FALSE) DESC,
                                        COALESCE(target_mentions.mentioned, FALSE) DESC,
                                        runs.observed_at DESC NULLS LAST,
                                        probes.seq_no ASC NULLS LAST,
                                        probes.id ASC
                           )::integer AS row_no,
                           runs.id AS run_id,
                           runs.source_task_id,
                           runs.observed_at,
                           probes.id AS probe_id,
                           probes.source_probe_id,
                           probes.probe_ref,
                           probes.seq_no,
                           probes.topic_catalog_id,
                           topics.topic_name,
                           probes.render_text AS prompt_text,
                           attempts.id AS attempt_id,
                           attempts.provider,
                           attempts.model_id,
                           attempts.raw_response_text,
                           attempts.response_preview,
                           (
                               attempts.id IS NOT NULL
                               AND COALESCE(NULLIF(attempts.raw_response_text, ''), NULLIF(attempts.response_preview, '')) IS NOT NULL
                           ) AS effective_answer,
                           CASE
                               WHEN attempts.id IS NULL THEN 'missing_attempt'
                               WHEN COALESCE(NULLIF(attempts.raw_response_text, ''), NULLIF(attempts.response_preview, '')) IS NULL THEN 'empty_answer'
                               ELSE NULL
                           END AS excluded_reason,
                           COALESCE(target_mentions.mentioned, FALSE) AS mentioned,
                           COALESCE(target_mentions.recommended, FALSE) AS recommended,
                           target_mentions.recommendation_rank,
                           (
                               target_mentions.recommendation_rank IS NOT NULL
                               AND target_mentions.recommendation_rank BETWEEN 1 AND 3
                           ) AS top3
                    FROM scoped_runs runs
                    JOIN ka_probes probes ON probes.run_id = runs.id
                    LEFT JOIN ka_topic_catalog topics ON topics.id = probes.topic_catalog_id
                    LEFT JOIN ka_attempts attempts ON attempts.run_id = runs.id
                        AND attempts.probe_id = probes.id
                        AND attempts.selected_final IS TRUE
                    LEFT JOIN target_mentions ON target_mentions.run_id = runs.id
                        AND target_mentions.probe_id = probes.id
                )
                SELECT row_no,
                       run_id,
                       source_task_id,
                       observed_at,
                       probe_id,
                       source_probe_id,
                       attempt_id,
                       probe_ref,
                       seq_no,
                       topic_catalog_id,
                       topic_name,
                       prompt_text,
                       provider,
                       model_id,
                       COALESCE(NULLIF(response_preview, ''), raw_response_text) AS answer_preview,
                       effective_answer,
                       excluded_reason,
                       mentioned,
                       recommended,
                       recommendation_rank,
                       top3,
                       COUNT(*) OVER()::integer AS total_count,
                       SUM(CASE WHEN effective_answer THEN 1 ELSE 0 END) OVER()::integer AS effective_answer_count,
                       SUM(CASE WHEN NOT effective_answer THEN 1 ELSE 0 END) OVER()::integer AS excluded_answer_count,
                       SUM(CASE WHEN effective_answer AND mentioned THEN 1 ELSE 0 END) OVER()::integer AS mention_hit_count,
                       SUM(CASE WHEN effective_answer AND recommended THEN 1 ELSE 0 END) OVER()::integer AS recommend_hit_count,
                       SUM(CASE WHEN effective_answer AND top3 THEN 1 ELSE 0 END) OVER()::integer AS top3_hit_count
                FROM sample_rows
                ORDER BY row_no ASC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId.trim())
                .addValue("brandName", brandName == null || brandName.isBlank() ? null : brandName.trim())
                .addValue("windowStartAt", windowStartAt)
                .addValue("windowEndAt", windowEndAt)
                .addValue("offset", Math.max(offset, 0))
                .addValue("limit", limit), (resultSet, rowNum) -> new MetricSampleRowView(
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("row_no")),
                resultSet.getString("run_id"),
                resultSet.getString("source_task_id"),
                resultSet.getObject("observed_at", OffsetDateTime.class),
                resultSet.getString("probe_id"),
                resultSet.getString("source_probe_id"),
                resultSet.getString("attempt_id"),
                resultSet.getString("probe_ref"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("seq_no")),
                resultSet.getString("topic_catalog_id"),
                resultSet.getString("topic_name"),
                resultSet.getString("prompt_text"),
                resultSet.getString("answer_preview"),
                resultSet.getString("provider"),
                resultSet.getString("model_id"),
                resultSet.getBoolean("effective_answer"),
                resultSet.getString("excluded_reason"),
                resultSet.getBoolean("mentioned"),
                resultSet.getBoolean("recommended"),
                resultSet.getBoolean("top3"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("recommendation_rank")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("total_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("effective_answer_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("excluded_answer_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("mention_hit_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("recommend_hit_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("top3_hit_count"))
        ));
        if (rows.isEmpty()) {
            return MetricSampleRowsResult.empty();
        }
        MetricSampleRowView first = rows.getFirst();
        return new MetricSampleRowsResult(
                first.totalCount(),
                first.effectiveAnswerCount(),
                first.excludedAnswerCount(),
                first.mentionHitCount(),
                first.recommendHitCount(),
                first.top3HitCount(),
                rows
        );
    }

    public record ProbeUpsert(String id,
                              String sourceProbeId,
                              String probeRef,
                              Integer seqNo,
                              String topicCatalogId,
                              String promptCatalogId,
                              String problemSpaceBucket,
                              String renderText,
                              String probePayloadJson) {
    }

    public record AttemptUpsert(String id,
                                String probeId,
                                String sourceAttemptId,
                                String provider,
                                String modelId,
                                String statusCode,
                                boolean selectedFinal,
                                String rawResponseText,
                                String responsePreview,
                                String citationsPayloadJson,
                                String responseMetadataJson) {
    }

    public record BrandMentionUpsert(String id,
                                     String probeId,
                                     String attemptId,
                                     String sourceMentionId,
                                     String brandKey,
                                     String brandName,
                                     String brandRole,
                                     boolean mentionedFlag,
                                     boolean recommendedFlag,
                                     String sentimentCode,
                                     Integer positionIndex,
                                     BigDecimal confidenceScore,
                                     String mentionPayloadJson) {
    }

    public record ProbeView(String id,
                            String runId,
                            String sourceProbeId,
                            String probeRef,
                            Integer seqNo,
                            String topicCatalogId,
                            String promptCatalogId,
                            String problemSpaceBucket,
                            String renderText) {
    }

    public record AttemptView(String id,
                              String runId,
                              String probeId,
                              String sourceAttemptId,
                              String provider,
                              String modelId,
                              Integer statusCode,
                              Boolean selectedFinal,
                              String rawResponseText,
                              String responsePreview,
                              String citationsPayloadJson,
                              String responseMetadataJson,
                              LocalDateTime createdAt) {
    }

    public record BrandMentionView(String id,
                                   String runId,
                                   String probeId,
                                   String attemptId,
                                   String sourceMentionId,
                                   String brandKey,
                                   String brandName,
                                   String brandRole,
                                   Boolean mentionedFlag,
                                   Boolean recommendedFlag,
                                   Integer sentimentCode,
                                   Integer positionIndex,
                                   BigDecimal confidenceScore,
                                   String mentionPayloadJson,
                                   LocalDateTime createdAt) {
    }

    public record MetricSampleRowsResult(Integer totalCount,
                                         Integer effectiveAnswerCount,
                                         Integer excludedAnswerCount,
                                         Integer mentionHitCount,
                                         Integer recommendHitCount,
                                         Integer top3HitCount,
                                         List<MetricSampleRowView> rows) {

        public static MetricSampleRowsResult empty() {
            return new MetricSampleRowsResult(0, 0, 0, 0, 0, 0, List.of());
        }
    }

    public record MetricSampleRowView(Integer rowNo,
                                      String runId,
                                      String sourceTaskId,
                                      OffsetDateTime observedAt,
                                      String probeId,
                                      String sourceProbeId,
                                      String attemptId,
                                      String probeRef,
                                      Integer seqNo,
                                      String topicCatalogId,
                                      String topicName,
                                      String promptText,
                                      String answerPreview,
                                      String provider,
                                      String modelId,
                                      Boolean effectiveAnswer,
                                      String excludedReason,
                                      Boolean mentioned,
                                      Boolean recommended,
                                      Boolean top3,
                                      Integer recommendationRank,
                                      Integer totalCount,
                                      Integer effectiveAnswerCount,
                                      Integer excludedAnswerCount,
                                      Integer mentionHitCount,
                                      Integer recommendHitCount,
                                      Integer top3HitCount) {
    }
}
