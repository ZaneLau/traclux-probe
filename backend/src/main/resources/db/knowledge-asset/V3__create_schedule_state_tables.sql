CREATE TABLE IF NOT EXISTS ka_brand_space_schedule_state (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL UNIQUE,
    latest_run_id TEXT,
    latest_run_at TIMESTAMPTZ,
    last_1d_probe_count INTEGER NOT NULL DEFAULT 0,
    last_7d_probe_count INTEGER NOT NULL DEFAULT 0,
    last_14d_probe_count INTEGER NOT NULL DEFAULT 0,
    active_topic_count INTEGER NOT NULL DEFAULT 0,
    covered_topic_count_7d INTEGER NOT NULL DEFAULT 0,
    coverage_gap_score NUMERIC(10,6),
    freshness_score NUMERIC(10,6),
    cold_start_flag BOOLEAN NOT NULL DEFAULT FALSE,
    just_edited_flag BOOLEAN NOT NULL DEFAULT FALSE,
    suggested_brand_weight NUMERIC(10,6),
    state_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_brand_space_schedule_state_scope
    ON ka_brand_space_schedule_state (brand_scope_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ka_brand_space_schedule_state_payload_gin
    ON ka_brand_space_schedule_state USING GIN (state_payload);

CREATE TABLE IF NOT EXISTS ka_topic_schedule_state (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    topic_catalog_id TEXT NOT NULL,
    latest_run_id TEXT,
    latest_covered_at TIMESTAMPTZ,
    last_1d_probe_count INTEGER NOT NULL DEFAULT 0,
    last_7d_probe_count INTEGER NOT NULL DEFAULT 0,
    last_14d_probe_count INTEGER NOT NULL DEFAULT 0,
    last_14d_sample_size INTEGER NOT NULL DEFAULT 0,
    coverage_floor INTEGER NOT NULL DEFAULT 1,
    coverage_debt_score NUMERIC(10,6),
    freshness_score NUMERIC(10,6),
    recent_intensity_score NUMERIC(10,6),
    cold_start_flag BOOLEAN NOT NULL DEFAULT FALSE,
    just_edited_flag BOOLEAN NOT NULL DEFAULT FALSE,
    suggested_priority_score NUMERIC(10,6),
    state_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, topic_catalog_id)
);

CREATE INDEX IF NOT EXISTS idx_ka_topic_schedule_state_scope
    ON ka_topic_schedule_state (brand_scope_id, suggested_priority_score DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ka_topic_schedule_state_latest_covered
    ON ka_topic_schedule_state (brand_scope_id, latest_covered_at DESC);

CREATE INDEX IF NOT EXISTS idx_ka_topic_schedule_state_payload_gin
    ON ka_topic_schedule_state USING GIN (state_payload);
