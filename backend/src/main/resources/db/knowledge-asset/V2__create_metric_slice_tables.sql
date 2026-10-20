CREATE TABLE IF NOT EXISTS ka_metric_registry (
    id TEXT PRIMARY KEY,
    metric_key TEXT NOT NULL UNIQUE,
    metric_name TEXT NOT NULL,
    metric_group TEXT NOT NULL,
    value_type TEXT NOT NULL,
    unit TEXT,
    aggregation_method TEXT NOT NULL,
    direction TEXT NOT NULL DEFAULT 'NEUTRAL',
    description TEXT,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    registry_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ka_metric_registry_group_active
    ON ka_metric_registry (metric_group, active_flag);
CREATE INDEX IF NOT EXISTS idx_ka_metric_registry_payload_gin
    ON ka_metric_registry USING GIN (registry_payload);

CREATE TABLE IF NOT EXISTS ka_metric_facts (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    run_id TEXT NOT NULL,
    source_object_type TEXT NOT NULL,
    source_object_key TEXT NOT NULL,
    metric_key TEXT NOT NULL,
    dimension_key TEXT NOT NULL,
    metric_value NUMERIC(18,6),
    metric_text TEXT,
    metric_unit TEXT,
    dimensions JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_refs JSONB NOT NULL DEFAULT '{}'::jsonb,
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    observed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, source_object_type, source_object_key, metric_key, dimension_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_metric_facts_scope_metric
    ON ka_metric_facts (brand_scope_id, metric_key, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_ka_metric_facts_object
    ON ka_metric_facts (brand_scope_id, source_object_type, source_object_key, metric_key);
CREATE INDEX IF NOT EXISTS idx_ka_metric_facts_dimensions_gin
    ON ka_metric_facts USING GIN (dimensions);
CREATE INDEX IF NOT EXISTS idx_ka_metric_facts_payload_gin
    ON ka_metric_facts USING GIN (fact_payload);

CREATE TABLE IF NOT EXISTS ka_metric_window_facts (
    id TEXT PRIMARY KEY,
    brand_scope_id TEXT NOT NULL,
    window_key TEXT NOT NULL,
    window_start_at TIMESTAMPTZ NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    source_object_type TEXT NOT NULL,
    source_object_key TEXT NOT NULL,
    metric_key TEXT NOT NULL,
    dimension_key TEXT NOT NULL,
    sample_count INTEGER NOT NULL DEFAULT 0,
    numeric_value NUMERIC(18,6),
    numeric_min NUMERIC(18,6),
    numeric_max NUMERIC(18,6),
    metric_text TEXT,
    metric_unit TEXT,
    dimensions JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_refs JSONB NOT NULL DEFAULT '{}'::jsonb,
    fact_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (brand_scope_id, window_key, source_object_type, source_object_key, metric_key, dimension_key)
);

CREATE INDEX IF NOT EXISTS idx_ka_metric_window_facts_scope_metric
    ON ka_metric_window_facts (brand_scope_id, window_key, metric_key);
CREATE INDEX IF NOT EXISTS idx_ka_metric_window_facts_object
    ON ka_metric_window_facts (brand_scope_id, window_key, source_object_type, source_object_key);
CREATE INDEX IF NOT EXISTS idx_ka_metric_window_facts_dimensions_gin
    ON ka_metric_window_facts USING GIN (dimensions);
CREATE INDEX IF NOT EXISTS idx_ka_metric_window_facts_payload_gin
    ON ka_metric_window_facts USING GIN (fact_payload);

CREATE TABLE IF NOT EXISTS ka_projection_metric_slices (
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

CREATE INDEX IF NOT EXISTS idx_ka_projection_metric_slices_scope
    ON ka_projection_metric_slices (brand_scope_id, generated_at DESC);
