DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ka_run_events'
          AND column_name = 'event_payload'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ka_run_events'
          AND column_name = 'payload_json'
    ) THEN
        ALTER TABLE ka_run_events RENAME COLUMN event_payload TO payload_json;
    END IF;
END $$;

ALTER TABLE ka_run_events
    ADD COLUMN IF NOT EXISTS brand_scope_id TEXT;

UPDATE ka_run_events events
SET brand_scope_id = runs.brand_scope_id
FROM ka_runs runs
WHERE events.brand_scope_id IS NULL
  AND (
      runs.id = events.run_id
      OR runs.source_task_id = events.source_task_id
  );

UPDATE ka_run_events
SET brand_scope_id = COALESCE(brand_scope_id, '')
WHERE brand_scope_id IS NULL;

ALTER TABLE ka_run_events
    ALTER COLUMN brand_scope_id SET NOT NULL;

ALTER TABLE ka_run_events
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE ka_run_events
    ALTER COLUMN payload_json SET DEFAULT '{}'::jsonb;

ALTER TABLE ka_run_events
    ALTER COLUMN payload_json SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ka_run_events_task_seq_unique
    ON ka_run_events (source_task_id, source_event_seq);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_task_seq
    ON ka_run_events (source_task_id, source_event_seq ASC);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_run_type
    ON ka_run_events (run_id, event_type, event_created_at ASC);

CREATE INDEX IF NOT EXISTS idx_ka_run_events_scope_created
    ON ka_run_events (brand_scope_id, event_created_at DESC);
