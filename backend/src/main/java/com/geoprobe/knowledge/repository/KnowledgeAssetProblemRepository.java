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
public class KnowledgeAssetProblemRepository {

    private static final String TOPIC_SOURCE_TYPE = "BRAND_SPACE_TOPIC";
    private static final String PROMPT_SOURCE_TYPE = "BRAND_SPACE_PROMPT";

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertTopicCatalog(TopicCatalogUpsert topicUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", topicUpsert.id())
                .addValue("organizationId", topicUpsert.organizationId())
                .addValue("brandScopeId", topicUpsert.brandScopeId())
                .addValue("sourceTopicId", topicUpsert.sourceTopicId())
                .addValue("topicName", topicUpsert.topicName())
                .addValue("topicDescription", topicUpsert.topicDescription())
                .addValue("problemSpaceBucket", topicUpsert.problemSpaceBucket())
                .addValue("sourcePayload", topicUpsert.sourcePayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_topic_catalog (
                    id,
                    organization_id,
                    brand_scope_id,
                    source_topic_id,
                    topic_name,
                    topic_description,
                    problem_space_bucket,
                    source_type,
                    active_flag,
                    source_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :sourceTopicId,
                    :topicName,
                    :topicDescription,
                    :problemSpaceBucket,
                    'BRAND_SPACE_TOPIC',
                    TRUE,
                    CAST(:sourcePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, source_topic_id) DO UPDATE
                SET topic_name = EXCLUDED.topic_name,
                    topic_description = EXCLUDED.topic_description,
                    problem_space_bucket = EXCLUDED.problem_space_bucket,
                    active_flag = TRUE,
                    source_payload = EXCLUDED.source_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void deactivateMissingTopics(String brandScopeId, List<String> activeTopicIds) {
        if (activeTopicIds == null || activeTopicIds.isEmpty()) {
            knowledgeAssetNamedParameterJdbcTemplate.update("""
                    UPDATE ka_topic_catalog
                    SET active_flag = FALSE,
                        updated_at = NOW()
                    WHERE brand_scope_id = :brandScopeId
                      AND source_type = :sourceType
                    """, new MapSqlParameterSource()
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("sourceType", TOPIC_SOURCE_TYPE));
            return;
        }
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                UPDATE ka_topic_catalog
                SET active_flag = FALSE,
                    updated_at = NOW()
                WHERE brand_scope_id = :brandScopeId
                  AND source_type = :sourceType
                  AND source_topic_id NOT IN (:activeTopicIds)
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("sourceType", TOPIC_SOURCE_TYPE)
                .addValue("activeTopicIds", activeTopicIds));
    }

    public void upsertPromptCatalog(PromptCatalogUpsert promptUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", promptUpsert.id())
                .addValue("organizationId", promptUpsert.organizationId())
                .addValue("brandScopeId", promptUpsert.brandScopeId())
                .addValue("topicCatalogId", promptUpsert.topicCatalogId())
                .addValue("sourcePromptId", promptUpsert.sourcePromptId())
                .addValue("promptText", promptUpsert.promptText())
                .addValue("seqNo", promptUpsert.seqNo())
                .addValue("sourcePayload", promptUpsert.sourcePayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_prompt_catalog (
                    id,
                    organization_id,
                    brand_scope_id,
                    topic_catalog_id,
                    source_prompt_id,
                    prompt_text,
                    source_type,
                    active_flag,
                    seq_no,
                    source_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :topicCatalogId,
                    :sourcePromptId,
                    :promptText,
                    'BRAND_SPACE_PROMPT',
                    TRUE,
                    :seqNo,
                    CAST(:sourcePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, source_prompt_id) DO UPDATE
                SET topic_catalog_id = EXCLUDED.topic_catalog_id,
                    prompt_text = EXCLUDED.prompt_text,
                    active_flag = TRUE,
                    seq_no = EXCLUDED.seq_no,
                    source_payload = EXCLUDED.source_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void deactivateMissingPrompts(String brandScopeId, List<String> activePromptIds) {
        if (activePromptIds == null || activePromptIds.isEmpty()) {
            knowledgeAssetNamedParameterJdbcTemplate.update("""
                    UPDATE ka_prompt_catalog
                    SET active_flag = FALSE,
                        updated_at = NOW()
                    WHERE brand_scope_id = :brandScopeId
                      AND source_type = :sourceType
                    """, new MapSqlParameterSource()
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("sourceType", PROMPT_SOURCE_TYPE));
            return;
        }
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                UPDATE ka_prompt_catalog
                SET active_flag = FALSE,
                    updated_at = NOW()
                WHERE brand_scope_id = :brandScopeId
                  AND source_type = :sourceType
                  AND source_prompt_id NOT IN (:activePromptIds)
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("sourceType", PROMPT_SOURCE_TYPE)
                .addValue("activePromptIds", activePromptIds));
    }

    public void replaceSnapshotFacts(String runId,
                                     String brandScopeId,
                                     List<ProblemSnapshotFactUpsert> snapshotFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_problem_snapshot_facts
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (snapshotFacts == null || snapshotFacts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (ProblemSnapshotFactUpsert fact : snapshotFacts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", fact.id())
                    .addValue("runId", runId)
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("problemSpaceBucket", fact.problemSpaceBucket())
                    .addValue("topicCatalogId", fact.topicCatalogId())
                    .addValue("clusterKey", fact.clusterKey())
                    .addValue("factSource", fact.factSource())
                    .addValue("promptCount", fact.promptCount())
                    .addValue("probeCount", fact.probeCount())
                    .addValue("attemptCount", fact.attemptCount())
                    .addValue("evidenceCount", fact.evidenceCount())
                    .addValue("signalScore", fact.signalScore())
                    .addValue("factPayload", fact.factPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_problem_snapshot_facts (
                    id,
                    run_id,
                    brand_scope_id,
                    problem_space_bucket,
                    topic_catalog_id,
                    cluster_key,
                    fact_source,
                    prompt_count,
                    probe_count,
                    attempt_count,
                    evidence_count,
                    signal_score,
                    fact_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :brandScopeId,
                    :problemSpaceBucket,
                    :topicCatalogId,
                    :clusterKey,
                    :factSource,
                    :promptCount,
                    :probeCount,
                    :attemptCount,
                    :evidenceCount,
                    :signalScore,
                    CAST(:factPayload AS jsonb),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public void replaceProblemSignals(String runId, List<ProblemSignalUpsert> problemSignals) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_problem_signals
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (problemSignals == null || problemSignals.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (ProblemSignalUpsert signal : problemSignals) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", signal.id())
                    .addValue("runId", runId)
                    .addValue("probeId", signal.probeId())
                    .addValue("attemptId", signal.attemptId())
                    .addValue("topicCatalogId", signal.topicCatalogId())
                    .addValue("promptCatalogId", signal.promptCatalogId())
                    .addValue("problemSpaceBucket", signal.problemSpaceBucket())
                    .addValue("clusterKey", signal.clusterKey())
                    .addValue("signalType", signal.signalType())
                    .addValue("signalScore", signal.signalScore())
                    .addValue("signalSummary", signal.signalSummary())
                    .addValue("signalPayload", signal.signalPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_problem_signals (
                    id,
                    run_id,
                    probe_id,
                    attempt_id,
                    topic_catalog_id,
                    prompt_catalog_id,
                    problem_space_bucket,
                    cluster_key,
                    signal_type,
                    signal_score,
                    signal_summary,
                    signal_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :probeId,
                    :attemptId,
                    :topicCatalogId,
                    :promptCatalogId,
                    :problemSpaceBucket,
                    :clusterKey,
                    :signalType,
                    :signalScore,
                    :signalSummary,
                    CAST(:signalPayload AS jsonb),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
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

    public List<ProblemSnapshotFactView> listSnapshotFactsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       problem_space_bucket,
                       topic_catalog_id,
                       cluster_key,
                       fact_source,
                       prompt_count,
                       probe_count,
                       attempt_count,
                       evidence_count,
                       signal_score
                FROM ka_problem_snapshot_facts
                WHERE run_id IN (:runIds)
                ORDER BY run_id ASC, problem_space_bucket ASC, cluster_key ASC
                """, new MapSqlParameterSource()
                .addValue("runIds", runIds), (resultSet, rowNum) -> new ProblemSnapshotFactView(
                resultSet.getString("run_id"),
                resultSet.getString("problem_space_bucket"),
                resultSet.getString("topic_catalog_id"),
                resultSet.getString("cluster_key"),
                resultSet.getString("fact_source"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("prompt_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("probe_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("attempt_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("evidence_count")),
                resultSet.getObject("signal_score") == null ? null : resultSet.getDouble("signal_score")
        ));
    }

    public void replaceWindowFacts(String brandScopeId,
                                   String windowKey,
                                   OffsetDateTime windowStartAt,
                                   OffsetDateTime windowEndAt,
                                   List<ProblemWindowFactUpsert> windowFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_problem_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey));
        if (windowFacts == null || windowFacts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (ProblemWindowFactUpsert fact : windowFacts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", fact.id())
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("windowKey", windowKey)
                    .addValue("windowStartAt", windowStartAt)
                    .addValue("windowEndAt", windowEndAt)
                    .addValue("problemSpaceBucket", fact.problemSpaceBucket())
                    .addValue("topicCatalogId", fact.topicCatalogId())
                    .addValue("clusterKey", fact.clusterKey())
                    .addValue("factSource", fact.factSource())
                    .addValue("runCount", fact.runCount())
                    .addValue("promptCount", fact.promptCount())
                    .addValue("probeCount", fact.probeCount())
                    .addValue("attemptCount", fact.attemptCount())
                    .addValue("evidenceCount", fact.evidenceCount())
                    .addValue("avgSignalScore", fact.avgSignalScore())
                    .addValue("factPayload", fact.factPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_problem_window_facts (
                    id,
                    brand_scope_id,
                    window_key,
                    window_start_at,
                    window_end_at,
                    problem_space_bucket,
                    topic_catalog_id,
                    cluster_key,
                    fact_source,
                    run_count,
                    prompt_count,
                    probe_count,
                    attempt_count,
                    evidence_count,
                    avg_signal_score,
                    fact_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :windowKey,
                    :windowStartAt,
                    :windowEndAt,
                    :problemSpaceBucket,
                    :topicCatalogId,
                    :clusterKey,
                    :factSource,
                    :runCount,
                    :promptCount,
                    :probeCount,
                    :attemptCount,
                    :evidenceCount,
                    :avgSignalScore,
                    CAST(:factPayload AS jsonb),
                    NOW(),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public List<TopicCatalogView> listActiveTopics(String brandScopeId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       source_topic_id,
                       topic_name,
                       topic_description,
                       problem_space_bucket,
                       active_flag,
                       updated_at
                FROM ka_topic_catalog
                WHERE brand_scope_id = :brandScopeId
                  AND active_flag = TRUE
                ORDER BY topic_name ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new TopicCatalogView(
                resultSet.getString("id"),
                resultSet.getString("source_topic_id"),
                resultSet.getString("topic_name"),
                resultSet.getString("topic_description"),
                resultSet.getString("problem_space_bucket"),
                resultSet.getBoolean("active_flag"),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        ));
    }

    public List<PromptCatalogView> listActivePrompts(String brandScopeId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       topic_catalog_id,
                       source_prompt_id,
                       prompt_text,
                       seq_no,
                       active_flag
                FROM ka_prompt_catalog
                WHERE brand_scope_id = :brandScopeId
                  AND active_flag = TRUE
                ORDER BY seq_no ASC NULLS LAST, prompt_text ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new PromptCatalogView(
                resultSet.getString("id"),
                resultSet.getString("topic_catalog_id"),
                resultSet.getString("source_prompt_id"),
                resultSet.getString("prompt_text"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("seq_no")),
                resultSet.getBoolean("active_flag")
        ));
    }

    public List<ProblemWindowFactView> listWindowFacts(String brandScopeId, String windowKey) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT problem_space_bucket,
                       topic_catalog_id,
                       cluster_key,
                       fact_source,
                       run_count,
                       prompt_count,
                       probe_count,
                       attempt_count,
                       evidence_count,
                       avg_signal_score
                FROM ka_problem_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                ORDER BY avg_signal_score DESC NULLS LAST,
                         evidence_count DESC,
                         problem_space_bucket ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey), (resultSet, rowNum) -> new ProblemWindowFactView(
                resultSet.getString("problem_space_bucket"),
                resultSet.getString("topic_catalog_id"),
                resultSet.getString("cluster_key"),
                resultSet.getString("fact_source"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("run_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("prompt_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("probe_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("attempt_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("evidence_count")),
                resultSet.getObject("avg_signal_score") == null ? null : resultSet.getDouble("avg_signal_score")
        ));
    }

    public record TopicCatalogUpsert(String id,
                                     String organizationId,
                                     String brandScopeId,
                                     String sourceTopicId,
                                     String topicName,
                                     String topicDescription,
                                     String problemSpaceBucket,
                                     String sourcePayloadJson) {
    }

    public record PromptCatalogUpsert(String id,
                                      String organizationId,
                                      String brandScopeId,
                                      String topicCatalogId,
                                      String sourcePromptId,
                                      String promptText,
                                      Integer seqNo,
                                      String sourcePayloadJson) {
    }

    public record ProblemSnapshotFactUpsert(String id,
                                            String problemSpaceBucket,
                                            String topicCatalogId,
                                            String clusterKey,
                                            String factSource,
                                            int promptCount,
                                            int probeCount,
                                            int attemptCount,
                                            int evidenceCount,
                                            Double signalScore,
                                            String factPayloadJson) {
    }

    public record WindowRunHead(String runId,
                                Integer sampleSize,
                                OffsetDateTime observedAt) {
    }

    public record ProblemSnapshotFactView(String runId,
                                          String problemSpaceBucket,
                                          String topicCatalogId,
                                          String clusterKey,
                                          String factSource,
                                          Integer promptCount,
                                          Integer probeCount,
                                          Integer attemptCount,
                                          Integer evidenceCount,
                                          Double signalScore) {
    }

    public record TopicCatalogView(String topicCatalogId,
                                   String sourceTopicId,
                                   String topicName,
                                   String topicDescription,
                                   String problemSpaceBucket,
                                   boolean activeFlag,
                                   OffsetDateTime updatedAt) {
    }

    public record PromptCatalogView(String promptCatalogId,
                                    String topicCatalogId,
                                    String sourcePromptId,
                                    String promptText,
                                    Integer seqNo,
                                    boolean activeFlag) {
    }

    public record ProblemWindowFactView(String problemSpaceBucket,
                                        String topicCatalogId,
                                        String clusterKey,
                                        String factSource,
                                        Integer runCount,
                                        Integer promptCount,
                                        Integer probeCount,
                                        Integer attemptCount,
                                        Integer evidenceCount,
                                        Double avgSignalScore) {
    }

    public record ProblemWindowFactUpsert(String id,
                                          String problemSpaceBucket,
                                          String topicCatalogId,
                                          String clusterKey,
                                          String factSource,
                                          int runCount,
                                          int promptCount,
                                          int probeCount,
                                          int attemptCount,
                                          int evidenceCount,
                                          Double avgSignalScore,
                                          String factPayloadJson) {
    }

    public record ProblemSignalUpsert(String id,
                                      String probeId,
                                      String attemptId,
                                      String topicCatalogId,
                                      String promptCatalogId,
                                      String problemSpaceBucket,
                                      String clusterKey,
                                      String signalType,
                                      Double signalScore,
                                      String signalSummary,
                                      String signalPayloadJson) {
    }
}
