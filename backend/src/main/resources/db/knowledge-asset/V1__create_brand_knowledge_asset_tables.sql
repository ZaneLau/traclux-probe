CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS ka_brand_space_profiles (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL UNIQUE,
    brand_name TEXT NOT NULL,
    category_name TEXT,
    official_website TEXT,
    primary_domain TEXT,
    locale TEXT NOT NULL,
    language TEXT NOT NULL,
    supplemental_brief TEXT,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_brand_space_profiles_org_scope
    ON ka_brand_space_profiles (organization_id, brand_scope_id);
CREATE INDEX IF NOT EXISTS idx_ka_brand_space_profiles_name_trgm
    ON ka_brand_space_profiles USING GIN (brand_name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_competitor_catalog (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    competitor_key TEXT NOT NULL,
    competitor_name TEXT NOT NULL,
    alias_names JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_type TEXT NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, competitor_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_competitor_catalog_scope_active
    ON ka_competitor_catalog (brand_scope_id, active_flag);
CREATE INDEX IF NOT EXISTS idx_ka_competitor_catalog_name_trgm
    ON ka_competitor_catalog USING GIN (competitor_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ka_competitor_catalog_aliases_gin
    ON ka_competitor_catalog USING GIN (alias_names);

CREATE TABLE IF NOT EXISTS ka_topic_catalog (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    source_topic_id TEXT,
    topic_name TEXT NOT NULL,
    topic_description TEXT,
    problem_space_bucket TEXT,
    source_type TEXT NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, source_topic_id)
);

CREATE INDEX IF NOT EXISTS idx_ka_topic_catalog_scope_bucket
    ON ka_topic_catalog (brand_scope_id, problem_space_bucket, active_flag);
CREATE INDEX IF NOT EXISTS idx_ka_topic_catalog_name_trgm
    ON ka_topic_catalog USING GIN (topic_name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_prompt_catalog (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    topic_catalog_id TEXT,
    source_prompt_id TEXT,
    prompt_text TEXT NOT NULL,
    source_type TEXT NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    seq_no INTEGER,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, source_prompt_id)
);

CREATE INDEX IF NOT EXISTS idx_ka_prompt_catalog_scope_topic
    ON ka_prompt_catalog (brand_scope_id, topic_catalog_id, active_flag);
CREATE INDEX IF NOT EXISTS idx_ka_prompt_catalog_text_trgm
    ON ka_prompt_catalog USING GIN (prompt_text gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_owned_domain_catalog (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    domain_name TEXT NOT NULL,
    domain_role TEXT NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, domain_name)
);

CREATE INDEX IF NOT EXISTS idx_ka_owned_domain_catalog_scope
    ON ka_owned_domain_catalog (brand_scope_id, active_flag);
CREATE INDEX IF NOT EXISTS idx_ka_owned_domain_catalog_domain_trgm
    ON ka_owned_domain_catalog USING GIN (domain_name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_source_assets (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    source_level TEXT NOT NULL,
    domain_name TEXT,
    canonical_url TEXT,
    document_title TEXT,
    source_type TEXT,
    ownership_key TEXT,
    provider_key TEXT,
    first_seen_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, source_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_source_assets_scope_level
    ON ka_source_assets (brand_scope_id, source_level, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_source_assets_domain_trgm
    ON ka_source_assets USING GIN (domain_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ka_source_assets_url_trgm
    ON ka_source_assets USING GIN (canonical_url gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ka_source_assets_payload_gin
    ON ka_source_assets USING GIN (source_payload);

CREATE TABLE IF NOT EXISTS ka_runs (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    source_task_id TEXT NOT NULL UNIQUE,
    source_report_id TEXT,
    engine_version TEXT NOT NULL,
    target_model_product TEXT,
    sample_size INTEGER,
    report_generated_at TIMESTAMPTZ,
    run_started_at TIMESTAMPTZ,
    run_completed_at TIMESTAMPTZ,
    task_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    report_projection JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_runs_scope_generated
    ON ka_runs (brand_scope_id, report_generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_runs_projection_gin
    ON ka_runs USING GIN (report_projection);

CREATE TABLE IF NOT EXISTS ka_probes (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    source_probe_id TEXT NOT NULL UNIQUE,
    probe_ref TEXT,
    seq_no INTEGER,
    topic_catalog_id TEXT,
    prompt_catalog_id TEXT,
    problem_space_bucket TEXT,
    render_text TEXT,
    probe_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_probes_run_seq
    ON ka_probes (run_id, seq_no);
CREATE INDEX IF NOT EXISTS idx_ka_probes_topic
    ON ka_probes (topic_catalog_id, problem_space_bucket);

CREATE TABLE IF NOT EXISTS ka_attempts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    probe_id TEXT NOT NULL,
    source_attempt_id TEXT NOT NULL UNIQUE,
    provider TEXT,
    model_id TEXT,
    status_code TEXT,
    selected_final BOOLEAN NOT NULL DEFAULT FALSE,
    raw_response_text TEXT,
    response_preview TEXT,
    citations_payload JSONB NOT NULL DEFAULT '[]'::jsonb,
    response_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_attempts_run_probe
    ON ka_attempts (run_id, probe_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_attempts_response_trgm
    ON ka_attempts USING GIN (response_preview gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_citations (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    probe_id TEXT,
    attempt_id TEXT,
    source_evidence_id TEXT,
    source_key TEXT,
    domain_name TEXT,
    url TEXT,
    title TEXT,
    snippet TEXT,
    source_type TEXT,
    ownership_key TEXT,
    provider_key TEXT,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_citations_run_domain
    ON ka_citations (run_id, domain_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_citations_run_source
    ON ka_citations (run_id, source_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_citations_scope_url_trgm
    ON ka_citations USING GIN (url gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ka_citations_title_trgm
    ON ka_citations USING GIN (title gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_brand_mentions (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    probe_id TEXT,
    attempt_id TEXT,
    source_mention_id TEXT,
    brand_key TEXT NOT NULL,
    brand_name TEXT NOT NULL,
    brand_role TEXT NOT NULL,
    mentioned_flag BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_flag BOOLEAN NOT NULL DEFAULT FALSE,
    sentiment_code TEXT,
    position_index INTEGER,
    confidence_score NUMERIC(7,4),
    mention_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_brand_mentions_run_role
    ON ka_brand_mentions (run_id, brand_role, brand_key);
CREATE INDEX IF NOT EXISTS idx_ka_brand_mentions_name_trgm
    ON ka_brand_mentions USING GIN (brand_name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_problem_signals (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    probe_id TEXT,
    attempt_id TEXT,
    topic_catalog_id TEXT,
    prompt_catalog_id TEXT,
    problem_space_bucket TEXT NOT NULL,
    cluster_key TEXT,
    signal_type TEXT NOT NULL,
    signal_score NUMERIC(8,4),
    signal_summary TEXT,
    signal_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_problem_signals_run_bucket
    ON ka_problem_signals (run_id, problem_space_bucket, signal_type);
CREATE INDEX IF NOT EXISTS idx_ka_problem_signals_stable_key
    ON ka_problem_signals (run_id, problem_space_bucket, topic_catalog_id, cluster_key, signal_type);
CREATE INDEX IF NOT EXISTS idx_ka_problem_signals_summary_trgm
    ON ka_problem_signals USING GIN (signal_summary gin_trgm_ops);

CREATE TABLE IF NOT EXISTS ka_competitor_snapshot_facts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    competitor_key TEXT NOT NULL,
    competitor_name TEXT NOT NULL,
    rank_no INTEGER,
    visibility_score NUMERIC(8,4),
    recommend_rate NUMERIC(8,4),
    mention_rate NUMERIC(8,4),
    top3_rate NUMERIC(8,4),
    average_position NUMERIC(8,4),
    mention_count INTEGER,
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, competitor_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_competitor_snapshot_facts_scope
    ON ka_competitor_snapshot_facts (brand_scope_id, run_id);

CREATE TABLE IF NOT EXISTS ka_competitor_window_facts (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    window_key TEXT NOT NULL,
    window_start_at TIMESTAMPTZ NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    competitor_key TEXT NOT NULL,
    competitor_name TEXT NOT NULL,
    configured_flag BOOLEAN NOT NULL DEFAULT FALSE,
    run_count INTEGER NOT NULL DEFAULT 0,
    mention_probe_count INTEGER NOT NULL DEFAULT 0,
    recommend_probe_count INTEGER NOT NULL DEFAULT 0,
    avg_visibility_score NUMERIC(8,4),
    avg_recommend_rate NUMERIC(8,4),
    avg_mention_rate NUMERIC(8,4),
    avg_top3_rate NUMERIC(8,4),
    avg_average_position NUMERIC(8,4),
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, window_key, competitor_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_competitor_window_facts_scope_window
    ON ka_competitor_window_facts (brand_scope_id, window_end_at DESC);

CREATE TABLE IF NOT EXISTS ka_source_snapshot_facts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    source_key TEXT,
    domain_name TEXT NOT NULL,
    source_type TEXT,
    ownership_key TEXT,
    citation_count INTEGER NOT NULL DEFAULT 0,
    share_rate NUMERIC(8,4),
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, domain_name)
);

CREATE INDEX IF NOT EXISTS idx_ka_source_snapshot_facts_scope
    ON ka_source_snapshot_facts (brand_scope_id, run_id);
CREATE INDEX IF NOT EXISTS idx_ka_source_snapshot_facts_source
    ON ka_source_snapshot_facts (brand_scope_id, source_key);

CREATE TABLE IF NOT EXISTS ka_source_window_facts (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    window_key TEXT NOT NULL,
    window_start_at TIMESTAMPTZ NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    source_key TEXT,
    domain_name TEXT NOT NULL,
    source_type TEXT,
    ownership_key TEXT,
    citation_count INTEGER NOT NULL DEFAULT 0,
    used_run_count INTEGER NOT NULL DEFAULT 0,
    share_rate NUMERIC(8,4),
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, window_key, domain_name)
);

CREATE INDEX IF NOT EXISTS idx_ka_source_window_facts_scope_window
    ON ka_source_window_facts (brand_scope_id, window_end_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_source_window_facts_source
    ON ka_source_window_facts (brand_scope_id, window_key, source_key);

CREATE TABLE IF NOT EXISTS ka_problem_snapshot_facts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    problem_space_bucket TEXT NOT NULL,
    topic_catalog_id TEXT,
    cluster_key TEXT,
    fact_source TEXT NOT NULL DEFAULT 'UNKNOWN',
    prompt_count INTEGER NOT NULL DEFAULT 0,
    probe_count INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    signal_score NUMERIC(8,4),
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_problem_snapshot_facts_scope
    ON ka_problem_snapshot_facts (brand_scope_id, run_id, problem_space_bucket);
CREATE INDEX IF NOT EXISTS idx_ka_problem_snapshot_facts_stable_key
    ON ka_problem_snapshot_facts (run_id, problem_space_bucket, topic_catalog_id, cluster_key, fact_source);

CREATE TABLE IF NOT EXISTS ka_problem_window_facts (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    window_key TEXT NOT NULL,
    window_start_at TIMESTAMPTZ NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    problem_space_bucket TEXT NOT NULL,
    topic_catalog_id TEXT,
    cluster_key TEXT,
    fact_source TEXT NOT NULL DEFAULT 'UNKNOWN',
    run_count INTEGER NOT NULL DEFAULT 0,
    prompt_count INTEGER NOT NULL DEFAULT 0,
    probe_count INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    avg_signal_score NUMERIC(8,4),
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, window_key, problem_space_bucket, topic_catalog_id, cluster_key, fact_source)
);

CREATE INDEX IF NOT EXISTS idx_ka_problem_window_facts_scope_window
    ON ka_problem_window_facts (brand_scope_id, window_end_at DESC, problem_space_bucket);

CREATE TABLE IF NOT EXISTS ka_projection_brand_performance (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    projection_key TEXT NOT NULL,
    projection_version TEXT NOT NULL,
    source_window_key TEXT,
    source_run_id TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, projection_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_projection_brand_performance_scope
    ON ka_projection_brand_performance (brand_scope_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS ka_projection_citation_sources (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    projection_key TEXT NOT NULL,
    projection_version TEXT NOT NULL,
    source_window_key TEXT,
    source_run_id TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, projection_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_projection_citation_sources_scope
    ON ka_projection_citation_sources (brand_scope_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS ka_projection_problem_analysis (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    projection_key TEXT NOT NULL,
    projection_version TEXT NOT NULL,
    source_window_key TEXT,
    source_run_id TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, projection_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_projection_problem_analysis_scope
    ON ka_projection_problem_analysis (brand_scope_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS ka_ingestion_checkpoints (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    source_task_id TEXT NOT NULL,
    source_report_id TEXT,
    source_updated_at TIMESTAMPTZ,
    ingestion_status TEXT NOT NULL,
    checkpoint_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_error_code TEXT,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, source_task_id)
);

CREATE INDEX IF NOT EXISTS idx_ka_ingestion_checkpoints_scope_status
    ON ka_ingestion_checkpoints (brand_scope_id, ingestion_status, updated_at DESC);
