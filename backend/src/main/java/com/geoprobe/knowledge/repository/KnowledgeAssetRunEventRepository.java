package com.geoprobe.knowledge.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetRunEventRepository {

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public void replaceRunEvents(String runId,
                                 String brandScopeId,
                                 String sourceTaskId,
                                 List<RunEventUpsert> runEvents) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_run_events
                WHERE source_task_id = :sourceTaskId
                """, new MapSqlParameterSource().addValue("sourceTaskId", sourceTaskId));
        if (runEvents == null || runEvents.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (RunEventUpsert runEvent : runEvents) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", runEvent.id())
                    .addValue("runId", runId)
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("sourceTaskId", sourceTaskId)
                    .addValue("sourceEventSeq", runEvent.sourceEventSeq())
                    .addValue("eventType", runEvent.eventType())
                    .addValue("provisional", runEvent.provisional())
                    .addValue("payloadJson", runEvent.payloadJson())
                    .addValue("eventCreatedAt", runEvent.eventCreatedAt()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_run_events (
                    id,
                    run_id,
                    brand_scope_id,
                    source_task_id,
                    source_event_seq,
                    event_type,
                    provisional,
                    payload_json,
                    event_created_at,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :runId,
                    :brandScopeId,
                    :sourceTaskId,
                    :sourceEventSeq,
                    :eventType,
                    :provisional,
                    CAST(:payloadJson AS jsonb),
                    :eventCreatedAt,
                    NOW(),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public List<RunEventView> listBySourceTaskId(String sourceTaskId) {
        if (sourceTaskId == null || sourceTaskId.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       run_id,
                       brand_scope_id,
                       source_task_id,
                       source_event_seq,
                       event_type,
                       provisional,
                       payload_json::text AS payload_json,
                       event_created_at
                FROM ka_run_events
                WHERE source_task_id = :sourceTaskId
                ORDER BY source_event_seq ASC, event_created_at ASC, created_at ASC
                """, new MapSqlParameterSource()
                .addValue("sourceTaskId", sourceTaskId.trim()), (resultSet, rowNum) -> new RunEventView(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getLong("source_event_seq"),
                resultSet.getString("event_type"),
                resultSet.getBoolean("provisional"),
                readJsonMap(resultSet.getString("payload_json")),
                KnowledgeAssetJdbcReaders.toLocalDateTime(resultSet.getObject("event_created_at"))
        ));
    }

    public List<RunEventView> listBySourceTaskIdAndEventType(String sourceTaskId, String eventType) {
        if (sourceTaskId == null || sourceTaskId.isBlank() || eventType == null || eventType.isBlank()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT id,
                       run_id,
                       brand_scope_id,
                       source_task_id,
                       source_event_seq,
                       event_type,
                       provisional,
                       payload_json::text AS payload_json,
                       event_created_at
                FROM ka_run_events
                WHERE source_task_id = :sourceTaskId
                  AND event_type = :eventType
                ORDER BY source_event_seq ASC, event_created_at ASC, created_at ASC
                """, new MapSqlParameterSource()
                .addValue("sourceTaskId", sourceTaskId.trim())
                .addValue("eventType", eventType.trim()), (resultSet, rowNum) -> new RunEventView(
                resultSet.getString("id"),
                resultSet.getString("run_id"),
                resultSet.getString("brand_scope_id"),
                resultSet.getString("source_task_id"),
                resultSet.getLong("source_event_seq"),
                resultSet.getString("event_type"),
                resultSet.getBoolean("provisional"),
                readJsonMap(resultSet.getString("payload_json")),
                KnowledgeAssetJdbcReaders.toLocalDateTime(resultSet.getObject("event_created_at"))
        ));
    }

    private Map<String, Object> readJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, Object> map = objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return map == null || map.isEmpty() ? Map.of() : Map.copyOf(map);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    public record RunEventUpsert(String id,
                                 Long sourceEventSeq,
                                 String eventType,
                                 boolean provisional,
                                 String payloadJson,
                                 LocalDateTime eventCreatedAt) {
    }

    public record RunEventView(String id,
                               String runId,
                               String brandScopeId,
                               String sourceTaskId,
                               Long sourceEventSeq,
                               String eventType,
                               Boolean provisional,
                               Map<String, Object> payloadJson,
                               LocalDateTime eventCreatedAt) {
    }
}
