ALTER TABLE brand_scopes
    ADD COLUMN internal_restore_until DATETIME NULL AFTER retention_until;

ALTER TABLE brand_scopes
    ADD COLUMN purged_at DATETIME NULL AFTER internal_restore_until;

CREATE INDEX idx_brand_scopes_archive_windows
    ON brand_scopes (scope_status_code, retention_until, internal_restore_until, purged_at);

UPDATE brand_scopes
SET retention_until = COALESCE(retention_until, DATE_ADD(COALESCE(archived_at, updated_at, NOW()), INTERVAL 30 DAY)),
    internal_restore_until = COALESCE(internal_restore_until, DATE_ADD(COALESCE(archived_at, updated_at, NOW()), INTERVAL 180 DAY))
WHERE scope_status_code = 0;
