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
public class KnowledgeAssetMetricRepository {

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertMetricRegistry(List<MetricRegistryUpsert> registryItems) {
        if (registryItems == null || registryItems.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batchParameters = registryItems.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("id", item.id())
                        .addValue("metricKey", item.metricKey())
                        .addValue("metricName", item.metricName())
                        .addValue("metricGroup", item.metricGroup())
                        .addValue("valueType", item.valueType())
                        .addValue("unit", item.unit())
                        .addValue("aggregationMethod", item.aggregationMethod())
                        .addValue("direction", item.direction())
                        .addValue("description", item.description())
                        .addValue("registryPayload", item.registryPayloadJson()))
                .toArray(MapSqlParameterSource[]::new);
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_metric_registry (
                    id,
                    metric_key,
                    metric_name,
                    metric_group,
                    value_type,
                    unit,
                    aggregation_method,
                    direction,
                    description,
                    active_flag,
                    registry_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :metricKey,
                    :metricName,
                    :metricGroup,
                    :valueType,
                    :unit,
                    :aggregationMethod,
                    :direction,
                    :description,
                    TRUE,
                    CAST(:registryPayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (metric_key) DO UPDATE
                SET metric_name = EXCLUDED.metric_name,
                    metric_group = EXCLUDED.metric_group,
                    value_type = EXCLUDED.value_type,
                    unit = EXCLUDED.unit,
                    aggregation_method = EXCLUDED.aggregation_method,
                    direction = EXCLUDED.direction,
                    description = EXCLUDED.description,
                    active_flag = TRUE,
                    registry_payload = EXCLUDED.registry_payload,
                    updated_at = NOW()
                """, batchParameters);
    }

    public void replaceRunFacts(String runId, List<MetricFactUpsert> metricFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_metric_facts
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (metricFacts == null || metricFacts.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batchParameters = metricFacts.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("id", item.id())
                        .addValue("brandScopeId", item.brandScopeId())
                        .addValue("runId", item.runId())
                        .addValue("sourceObjectType", item.sourceObjectType())
                        .addValue("sourceObjectKey", item.sourceObjectKey())
                        .addValue("metricKey", item.metricKey())
                        .addValue("dimensionKey", item.dimensionKey())
                        .addValue("metricValue", item.metricValue())
                        .addValue("metricText", item.metricText())
                        .addValue("metricUnit", item.metricUnit())
                        .addValue("dimensions", item.dimensionsJson())
                        .addValue("sourceRefs", item.sourceRefsJson())
                        .addValue("factPayload", item.factPayloadJson())
                        .addValue("observedAt", item.observedAt()))
                .toArray(MapSqlParameterSource[]::new);
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_metric_facts (
                    id,
                    brand_scope_id,
                    run_id,
                    source_object_type,
                    source_object_key,
                    metric_key,
                    dimension_key,
                    metric_value,
                    metric_text,
                    metric_unit,
                    dimensions,
                    source_refs,
                    fact_payload,
                    observed_at,
                    created_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :runId,
                    :sourceObjectType,
                    :sourceObjectKey,
                    :metricKey,
                    :dimensionKey,
                    :metricValue,
                    :metricText,
                    :metricUnit,
                    CAST(:dimensions AS jsonb),
                    CAST(:sourceRefs AS jsonb),
                    CAST(:factPayload AS jsonb),
                    :observedAt,
                    NOW()
                )
                """, batchParameters);
    }

    public int refreshWindowFacts(String brandScopeId,
                                  String windowKey,
                                  OffsetDateTime windowStartAt,
                                  OffsetDateTime windowEndAt) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_metric_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey));
        return knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_metric_window_facts (
                    id,
                    brand_scope_id,
                    window_key,
                    window_start_at,
                    window_end_at,
                    source_object_type,
                    source_object_key,
                    metric_key,
                    dimension_key,
                    sample_count,
                    numeric_value,
                    numeric_min,
                    numeric_max,
                    metric_text,
                    metric_unit,
                    dimensions,
                    source_refs,
                    fact_payload,
                    created_at,
                    updated_at
                )
                SELECT
                    'ka-metric-window-' || facts.brand_scope_id || '-' || :windowKey || '-' ||
                        facts.source_object_type || '-' || facts.source_object_key || '-' || facts.metric_key || '-' || facts.dimension_key AS id,
                    facts.brand_scope_id,
                    :windowKey AS window_key,
                    :windowStartAt AS window_start_at,
                    :windowEndAt AS window_end_at,
                    facts.source_object_type,
                    facts.source_object_key,
                    facts.metric_key,
                    facts.dimension_key,
                    COUNT(*)::integer AS sample_count,
                    CASE COALESCE(registry.aggregation_method, 'AVG')
                        WHEN 'SUM' THEN SUM(facts.metric_value)
                        WHEN 'LATEST' THEN (ARRAY_AGG(facts.metric_value ORDER BY facts.observed_at DESC NULLS LAST, facts.created_at DESC))[1]
                        WHEN 'WEIGHTED_AVG' THEN CASE
                            WHEN SUM(COALESCE((facts.fact_payload ->> 'weightValue')::double precision, 0.0)) > 0.0
                                THEN SUM(facts.metric_value * COALESCE((facts.fact_payload ->> 'weightValue')::double precision, 0.0))
                                    / SUM(COALESCE((facts.fact_payload ->> 'weightValue')::double precision, 0.0))
                            ELSE AVG(facts.metric_value)
                        END
                        ELSE AVG(facts.metric_value)
                    END AS numeric_value,
                    MIN(facts.metric_value) AS numeric_min,
                    MAX(facts.metric_value) AS numeric_max,
                    (ARRAY_AGG(facts.metric_text ORDER BY facts.observed_at DESC NULLS LAST, facts.created_at DESC))[1] AS metric_text,
                    (ARRAY_AGG(facts.metric_unit ORDER BY facts.observed_at DESC NULLS LAST, facts.created_at DESC))[1] AS metric_unit,
                    COALESCE((ARRAY_AGG(facts.dimensions ORDER BY facts.observed_at DESC NULLS LAST, facts.created_at DESC))[1], '{}'::jsonb) AS dimensions,
                    jsonb_build_object(
                        'runIds', jsonb_agg(DISTINCT facts.run_id),
                        'factCount', COUNT(*)
                    ) AS source_refs,
                    jsonb_build_object(
                        'aggregationMethod', COALESCE(registry.aggregation_method, 'AVG'),
                        'metricGroup', COALESCE(registry.metric_group, 'unknown'),
                        'weightSum', SUM(COALESCE((facts.fact_payload ->> 'weightValue')::double precision, 0.0))
                    ) AS fact_payload,
                    NOW(),
                    NOW()
                FROM ka_metric_facts facts
                LEFT JOIN ka_metric_registry registry
                    ON registry.metric_key = facts.metric_key
                WHERE facts.brand_scope_id = :brandScopeId
                  AND COALESCE(facts.observed_at, facts.created_at) >= :windowStartAt
                  AND COALESCE(facts.observed_at, facts.created_at) <= :windowEndAt
                GROUP BY facts.brand_scope_id,
                         facts.source_object_type,
                         facts.source_object_key,
                         facts.metric_key,
                         facts.dimension_key,
                         registry.aggregation_method,
                         registry.metric_group
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey)
                .addValue("windowStartAt", windowStartAt)
                .addValue("windowEndAt", windowEndAt));
    }

    public List<MetricRegistryView> listActiveMetricRegistry() {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT metric_key,
                       metric_name,
                       metric_group,
                       value_type,
                       unit,
                       aggregation_method,
                       direction,
                       description
                FROM ka_metric_registry
                WHERE active_flag = TRUE
                ORDER BY metric_group ASC, metric_key ASC
                """, (resultSet, rowNum) -> new MetricRegistryView(
                resultSet.getString("metric_key"),
                resultSet.getString("metric_name"),
                resultSet.getString("metric_group"),
                resultSet.getString("value_type"),
                resultSet.getString("unit"),
                resultSet.getString("aggregation_method"),
                resultSet.getString("direction"),
                resultSet.getString("description")
        ));
    }

    public List<MetricWindowFactView> listWindowFacts(String brandScopeId, String windowKey) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT source_object_type,
                       source_object_key,
                       metric_key,
                       dimension_key,
                       sample_count,
                       numeric_value,
                       numeric_min,
                       numeric_max,
                       metric_text,
                       metric_unit,
                       dimensions,
                       source_refs,
                       fact_payload
                FROM ka_metric_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                ORDER BY metric_key ASC, numeric_value DESC NULLS LAST, source_object_key ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey), (resultSet, rowNum) -> new MetricWindowFactView(
                resultSet.getString("source_object_type"),
                resultSet.getString("source_object_key"),
                resultSet.getString("metric_key"),
                resultSet.getString("dimension_key"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("sample_count")),
                asDouble(resultSet.getObject("numeric_value")),
                asDouble(resultSet.getObject("numeric_min")),
                asDouble(resultSet.getObject("numeric_max")),
                resultSet.getString("metric_text"),
                resultSet.getString("metric_unit"),
                resultSet.getString("dimensions"),
                resultSet.getString("source_refs"),
                resultSet.getString("fact_payload")
        ));
    }

    public List<MetricFactView> listRunFacts(String runId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       source_object_type,
                       source_object_key,
                       metric_key,
                       dimension_key,
                       metric_value,
                       metric_text,
                       metric_unit,
                       dimensions,
                       fact_payload::text AS fact_payload_json
                FROM ka_metric_facts
                WHERE run_id = :runId
                ORDER BY metric_key ASC, source_object_key ASC
                """, new MapSqlParameterSource()
                .addValue("runId", runId), (resultSet, rowNum) -> new MetricFactView(
                resultSet.getString("run_id"),
                resultSet.getString("source_object_type"),
                resultSet.getString("source_object_key"),
                resultSet.getString("metric_key"),
                resultSet.getString("dimension_key"),
                asDouble(resultSet.getObject("metric_value")),
                resultSet.getString("metric_text"),
                resultSet.getString("metric_unit"),
                resultSet.getString("dimensions"),
                resultSet.getString("fact_payload_json")
        ));
    }

    public List<MetricFactView> listRunFactsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       source_object_type,
                       source_object_key,
                       metric_key,
                       dimension_key,
                       metric_value,
                       metric_text,
                       metric_unit,
                       dimensions,
                       fact_payload::text AS fact_payload_json
                FROM ka_metric_facts
                WHERE run_id IN (:runIds)
                ORDER BY run_id ASC, metric_key ASC, source_object_key ASC
                """, new MapSqlParameterSource()
                .addValue("runIds", runIds), (resultSet, rowNum) -> new MetricFactView(
                resultSet.getString("run_id"),
                resultSet.getString("source_object_type"),
                resultSet.getString("source_object_key"),
                resultSet.getString("metric_key"),
                resultSet.getString("dimension_key"),
                asDouble(resultSet.getObject("metric_value")),
                resultSet.getString("metric_text"),
                resultSet.getString("metric_unit"),
                resultSet.getString("dimensions"),
                resultSet.getString("fact_payload_json")
        ));
    }

    private Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public record MetricRegistryUpsert(String id,
                                       String metricKey,
                                       String metricName,
                                       String metricGroup,
                                       String valueType,
                                       String unit,
                                       String aggregationMethod,
                                       String direction,
                                       String description,
                                       String registryPayloadJson) {
    }

    public record MetricFactUpsert(String id,
                                   String brandScopeId,
                                   String runId,
                                   String sourceObjectType,
                                   String sourceObjectKey,
                                   String metricKey,
                                   String dimensionKey,
                                   Double metricValue,
                                   String metricText,
                                   String metricUnit,
                                   String dimensionsJson,
                                   String sourceRefsJson,
                                   String factPayloadJson,
                                   OffsetDateTime observedAt) {
    }

    public record MetricRegistryView(String metricKey,
                                     String metricName,
                                     String metricGroup,
                                     String valueType,
                                     String unit,
                                     String aggregationMethod,
                                     String direction,
                                     String description) {
    }

    public record MetricWindowFactView(String sourceObjectType,
                                       String sourceObjectKey,
                                       String metricKey,
                                       String dimensionKey,
                                       Integer sampleCount,
                                       Double numericValue,
                                       Double numericMin,
                                       Double numericMax,
                                       String metricText,
                                       String metricUnit,
                                       String dimensionsJson,
                                       String sourceRefsJson,
                                       String factPayloadJson) {
    }

    public record MetricFactView(String runId,
                                 String sourceObjectType,
                                 String sourceObjectKey,
                                 String metricKey,
                                 String dimensionKey,
                                 Double metricValue,
                                 String metricText,
                                 String metricUnit,
                                 String dimensionsJson,
                                 String factPayloadJson) {
    }
}
