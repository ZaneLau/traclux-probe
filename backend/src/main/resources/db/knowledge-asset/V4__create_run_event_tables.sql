CREATE TABLE IF NOT EXISTS ka_run_events (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    brand_scope_id TEXT NOT NULL,
    source_task_id TEXT NOT NULL,
    source_event_seq BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    provisional BOOLEAN NOT NULL DEFAULT FALSE,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    event_created_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_task_id, source_event_seq)
);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_task_seq
    ON ka_run_events (source_task_id, source_event_seq ASC);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_run_type
    ON ka_run_events (run_id, event_type, event_created_at ASC);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_scope_created
    ON ka_run_events (brand_scope_id, event_created_at DESC);
