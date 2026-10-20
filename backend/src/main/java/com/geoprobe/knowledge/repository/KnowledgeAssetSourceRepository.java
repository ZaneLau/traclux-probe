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
public class KnowledgeAssetSourceRepository {

    private static final String OWNED_DOMAIN_SOURCE_TYPE = "OFFICIAL_DOMAIN";

    @Qualifier("knowledgeAssetNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate;

    public void upsertOwnedDomain(OwnedDomainUpsert ownedDomainUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", ownedDomainUpsert.id())
                .addValue("organizationId", ownedDomainUpsert.organizationId())
                .addValue("brandScopeId", ownedDomainUpsert.brandScopeId())
                .addValue("domainName", ownedDomainUpsert.domainName())
                .addValue("domainRole", ownedDomainUpsert.domainRole())
                .addValue("sourcePayload", ownedDomainUpsert.sourcePayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_owned_domain_catalog (
                    id,
                    organization_id,
                    brand_scope_id,
                    domain_name,
                    domain_role,
                    active_flag,
                    source_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :domainName,
                    :domainRole,
                    TRUE,
                    CAST(:sourcePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, domain_name) DO UPDATE
                SET domain_role = EXCLUDED.domain_role,
                    active_flag = TRUE,
                    source_payload = EXCLUDED.source_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void upsertSourceAsset(SourceAssetUpsert sourceAssetUpsert) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", sourceAssetUpsert.id())
                .addValue("organizationId", sourceAssetUpsert.organizationId())
                .addValue("brandScopeId", sourceAssetUpsert.brandScopeId())
                .addValue("sourceKey", sourceAssetUpsert.sourceKey())
                .addValue("sourceLevel", sourceAssetUpsert.sourceLevel())
                .addValue("domainName", sourceAssetUpsert.domainName())
                .addValue("canonicalUrl", sourceAssetUpsert.canonicalUrl())
                .addValue("documentTitle", sourceAssetUpsert.documentTitle())
                .addValue("sourceType", sourceAssetUpsert.sourceType())
                .addValue("ownershipKey", sourceAssetUpsert.ownershipKey())
                .addValue("providerKey", sourceAssetUpsert.providerKey())
                .addValue("observedAt", sourceAssetUpsert.observedAt())
                .addValue("sourcePayload", sourceAssetUpsert.sourcePayloadJson());
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                INSERT INTO ka_source_assets (
                    id,
                    organization_id,
                    brand_scope_id,
                    source_key,
                    source_level,
                    domain_name,
                    canonical_url,
                    document_title,
                    source_type,
                    ownership_key,
                    provider_key,
                    first_seen_at,
                    last_seen_at,
                    source_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :organizationId,
                    :brandScopeId,
                    :sourceKey,
                    :sourceLevel,
                    :domainName,
                    :canonicalUrl,
                    :documentTitle,
                    :sourceType,
                    :ownershipKey,
                    :providerKey,
                    :observedAt,
                    :observedAt,
                    CAST(:sourcePayload AS jsonb),
                    NOW(),
                    NOW()
                )
                ON CONFLICT (brand_scope_id, source_key) DO UPDATE
                SET domain_name = COALESCE(EXCLUDED.domain_name, ka_source_assets.domain_name),
                    canonical_url = COALESCE(EXCLUDED.canonical_url, ka_source_assets.canonical_url),
                    document_title = COALESCE(EXCLUDED.document_title, ka_source_assets.document_title),
                    source_type = COALESCE(EXCLUDED.source_type, ka_source_assets.source_type),
                    ownership_key = COALESCE(EXCLUDED.ownership_key, ka_source_assets.ownership_key),
                    provider_key = COALESCE(EXCLUDED.provider_key, ka_source_assets.provider_key),
                    last_seen_at = COALESCE(EXCLUDED.last_seen_at, ka_source_assets.last_seen_at),
                    source_payload = ka_source_assets.source_payload || EXCLUDED.source_payload,
                    updated_at = NOW()
                """, parameters);
    }

    public void deactivateMissingOwnedDomains(String brandScopeId, List<String> activeDomains) {
        if (activeDomains == null || activeDomains.isEmpty()) {
            knowledgeAssetNamedParameterJdbcTemplate.update("""
                    UPDATE ka_owned_domain_catalog
                    SET active_flag = FALSE,
                        updated_at = NOW()
                    WHERE brand_scope_id = :brandScopeId
                      AND domain_role = :domainRole
                    """, new MapSqlParameterSource()
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("domainRole", OWNED_DOMAIN_SOURCE_TYPE));
            return;
        }
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                UPDATE ka_owned_domain_catalog
                SET active_flag = FALSE,
                    updated_at = NOW()
                WHERE brand_scope_id = :brandScopeId
                  AND domain_role = :domainRole
                  AND domain_name NOT IN (:activeDomains)
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("domainRole", OWNED_DOMAIN_SOURCE_TYPE)
                .addValue("activeDomains", activeDomains));
    }

    public void replaceCitations(String runId, List<CitationUpsert> citations) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_citations
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (citations == null || citations.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (CitationUpsert citation : citations) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", citation.id())
                    .addValue("runId", runId)
                    .addValue("probeId", citation.probeId())
                    .addValue("attemptId", citation.attemptId())
                    .addValue("sourceEvidenceId", citation.sourceEvidenceId())
                    .addValue("sourceKey", citation.sourceKey())
                    .addValue("domainName", citation.domainName())
                    .addValue("url", citation.url())
                    .addValue("title", citation.title())
                    .addValue("snippet", citation.snippet())
                    .addValue("sourceType", citation.sourceType())
                    .addValue("ownershipKey", citation.ownershipKey())
                    .addValue("providerKey", citation.providerKey())
                    .addValue("sourcePayload", citation.sourcePayloadJson())
                    .addValue("retrievedAt", citation.retrievedAt()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_citations (
                    id,
                    run_id,
                    probe_id,
                    attempt_id,
                    source_evidence_id,
                    source_key,
                    domain_name,
                    url,
                    title,
                    snippet,
                    source_type,
                    ownership_key,
                    provider_key,
                    source_payload,
                    retrieved_at,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :probeId,
                    :attemptId,
                    :sourceEvidenceId,
                    :sourceKey,
                    :domainName,
                    :url,
                    :title,
                    :snippet,
                    :sourceType,
                    :ownershipKey,
                    :providerKey,
                    CAST(:sourcePayload AS jsonb),
                    :retrievedAt,
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    public void replaceSnapshotFacts(String runId,
                                     String brandScopeId,
                                     List<SourceSnapshotFactUpsert> snapshotFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_source_snapshot_facts
                WHERE run_id = :runId
                """, new MapSqlParameterSource().addValue("runId", runId));
        if (snapshotFacts == null || snapshotFacts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (SourceSnapshotFactUpsert fact : snapshotFacts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", fact.id())
                    .addValue("runId", runId)
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("sourceKey", fact.sourceKey())
                    .addValue("domainName", fact.domainName())
                    .addValue("sourceType", fact.sourceType())
                    .addValue("ownershipKey", fact.ownershipKey())
                    .addValue("citationCount", fact.citationCount())
                    .addValue("shareRate", fact.shareRate())
                    .addValue("factPayload", fact.factPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_source_snapshot_facts (
                    id,
                    run_id,
                    brand_scope_id,
                    source_key,
                    domain_name,
                    source_type,
                    ownership_key,
                    citation_count,
                    share_rate,
                    fact_payload,
                    created_at
                ) VALUES (
                    :id,
                    :runId,
                    :brandScopeId,
                    :sourceKey,
                    :domainName,
                    :sourceType,
                    :ownershipKey,
                    :citationCount,
                    :shareRate,
                    CAST(:factPayload AS jsonb),
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

    public List<CitationView> listCitationsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       probe_id,
                       attempt_id,
                       source_evidence_id,
                       source_key,
                       domain_name,
                       url,
                       title,
                       snippet,
                       source_type,
                       ownership_key,
                       provider_key,
                       retrieved_at
                FROM ka_citations
                WHERE run_id IN (:runIds)
                ORDER BY run_id ASC, created_at ASC
                """, new MapSqlParameterSource()
                .addValue("runIds", runIds), (resultSet, rowNum) -> new CitationView(
                resultSet.getString("run_id"),
                resultSet.getString("probe_id"),
                resultSet.getString("attempt_id"),
                resultSet.getString("source_evidence_id"),
                resultSet.getString("source_key"),
                resultSet.getString("domain_name"),
                resultSet.getString("url"),
                resultSet.getString("title"),
                resultSet.getString("snippet"),
                resultSet.getString("source_type"),
                resultSet.getString("ownership_key"),
                resultSet.getString("provider_key"),
                resultSet.getObject("retrieved_at", OffsetDateTime.class)
        ));
    }

    public List<OwnedDomainView> listActiveOwnedDomains(String brandScopeId) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT domain_name,
                       domain_role
                FROM ka_owned_domain_catalog
                WHERE brand_scope_id = :brandScopeId
                  AND active_flag = TRUE
                ORDER BY domain_name ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId), (resultSet, rowNum) -> new OwnedDomainView(
                resultSet.getString("domain_name"),
                resultSet.getString("domain_role")
        ));
    }

    public List<SourceSnapshotFactView> listSnapshotFactsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT run_id,
                       source_key,
                       domain_name,
                       source_type,
                       ownership_key,
                       citation_count,
                       share_rate
                FROM ka_source_snapshot_facts
                WHERE run_id IN (:runIds)
                ORDER BY run_id ASC, citation_count DESC, domain_name ASC
                """, new MapSqlParameterSource()
                .addValue("runIds", runIds), (resultSet, rowNum) -> new SourceSnapshotFactView(
                resultSet.getString("run_id"),
                resultSet.getString("source_key"),
                resultSet.getString("domain_name"),
                resultSet.getString("source_type"),
                resultSet.getString("ownership_key"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("citation_count")),
                asDouble(resultSet.getObject("share_rate"))
        ));
    }

    public List<SourceWindowFactView> listWindowFacts(String brandScopeId, String windowKey) {
        return knowledgeAssetNamedParameterJdbcTemplate.query("""
                SELECT domain_name,
                       source_key,
                       source_type,
                       ownership_key,
                       citation_count,
                       used_run_count,
                       share_rate
                FROM ka_source_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                ORDER BY citation_count DESC, domain_name ASC
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey), (resultSet, rowNum) -> new SourceWindowFactView(
                resultSet.getString("domain_name"),
                resultSet.getString("source_key"),
                resultSet.getString("source_type"),
                resultSet.getString("ownership_key"),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("citation_count")),
                KnowledgeAssetJdbcReaders.toInteger(resultSet.getObject("used_run_count")),
                asDouble(resultSet.getObject("share_rate"))
        ));
    }

    public void replaceWindowFacts(String brandScopeId,
                                   String windowKey,
                                   OffsetDateTime windowStartAt,
                                   OffsetDateTime windowEndAt,
                                   List<SourceWindowFactUpsert> windowFacts) {
        knowledgeAssetNamedParameterJdbcTemplate.update("""
                DELETE FROM ka_source_window_facts
                WHERE brand_scope_id = :brandScopeId
                  AND window_key = :windowKey
                """, new MapSqlParameterSource()
                .addValue("brandScopeId", brandScopeId)
                .addValue("windowKey", windowKey));
        if (windowFacts == null || windowFacts.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();
        for (SourceWindowFactUpsert fact : windowFacts) {
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("id", fact.id())
                    .addValue("brandScopeId", brandScopeId)
                    .addValue("windowKey", windowKey)
                    .addValue("windowStartAt", windowStartAt)
                    .addValue("windowEndAt", windowEndAt)
                    .addValue("sourceKey", fact.sourceKey())
                    .addValue("domainName", fact.domainName())
                    .addValue("sourceType", fact.sourceType())
                    .addValue("ownershipKey", fact.ownershipKey())
                    .addValue("citationCount", fact.citationCount())
                    .addValue("usedRunCount", fact.usedRunCount())
                    .addValue("shareRate", fact.shareRate())
                    .addValue("factPayload", fact.factPayloadJson()));
        }
        knowledgeAssetNamedParameterJdbcTemplate.batchUpdate("""
                INSERT INTO ka_source_window_facts (
                    id,
                    brand_scope_id,
                    window_key,
                    window_start_at,
                    window_end_at,
                    source_key,
                    domain_name,
                    source_type,
                    ownership_key,
                    citation_count,
                    used_run_count,
                    share_rate,
                    fact_payload,
                    created_at,
                    updated_at
                ) VALUES (
                    :id,
                    :brandScopeId,
                    :windowKey,
                    :windowStartAt,
                    :windowEndAt,
                    :sourceKey,
                    :domainName,
                    :sourceType,
                    :ownershipKey,
                    :citationCount,
                    :usedRunCount,
                    :shareRate,
                    CAST(:factPayload AS jsonb),
                    NOW(),
                    NOW()
                )
                """, batchParameters.toArray(MapSqlParameterSource[]::new));
    }

    private Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public record OwnedDomainUpsert(String id,
                                    String organizationId,
                                    String brandScopeId,
                                    String domainName,
                                    String domainRole,
                                    String sourcePayloadJson) {
    }

    public record SourceAssetUpsert(String id,
                                    String organizationId,
                                    String brandScopeId,
                                    String sourceKey,
                                    String sourceLevel,
                                    String domainName,
                                    String canonicalUrl,
                                    String documentTitle,
                                    String sourceType,
                                    String ownershipKey,
                                    String providerKey,
                                    OffsetDateTime observedAt,
                                    String sourcePayloadJson) {
    }

    public record CitationUpsert(String id,
                                 String probeId,
                                 String attemptId,
                                 String sourceEvidenceId,
                                 String sourceKey,
                                 String domainName,
                                 String url,
                                 String title,
                                 String snippet,
                                 String sourceType,
                                 String ownershipKey,
                                 String providerKey,
                                 String sourcePayloadJson,
                                 OffsetDateTime retrievedAt) {
    }

    public record SourceSnapshotFactUpsert(String id,
                                           String sourceKey,
                                           String domainName,
                                           String sourceType,
                                           String ownershipKey,
                                           int citationCount,
                                           Double shareRate,
                                           String factPayloadJson) {
    }

    public record WindowRunHead(String runId,
                                Integer sampleSize,
                                OffsetDateTime observedAt) {
    }

    public record CitationView(String runId,
                               String probeId,
                               String attemptId,
                               String sourceEvidenceId,
                               String sourceKey,
                               String domainName,
                               String url,
                               String title,
                               String snippet,
                               String sourceType,
                               String ownershipKey,
                               String providerKey,
                               OffsetDateTime retrievedAt) {
    }

    public record OwnedDomainView(String domainName,
                                  String domainRole) {
    }

    public record SourceSnapshotFactView(String runId,
                                         String sourceKey,
                                         String domainName,
                                         String sourceType,
                                         String ownershipKey,
                                         Integer citationCount,
                                         Double shareRate) {
    }

    public record SourceWindowFactView(String domainName,
                                       String sourceKey,
                                       String sourceType,
                                       String ownershipKey,
                                       Integer citationCount,
                                       Integer usedRunCount,
                                       Double shareRate) {
    }

    public record SourceWindowFactUpsert(String id,
                                         String sourceKey,
                                         String domainName,
                                         String sourceType,
                                         String ownershipKey,
                                         int citationCount,
                                         int usedRunCount,
                                         Double shareRate,
                                         String factPayloadJson) {
    }
}
