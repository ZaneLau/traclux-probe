ALTER TABLE brand_scopes
    ADD COLUMN archived_at DATETIME NULL AFTER scope_status_code;

ALTER TABLE brand_scopes
    ADD COLUMN archived_by_membership_id VARCHAR(36) NULL AFTER archived_at;

ALTER TABLE brand_scopes
    ADD COLUMN retention_until DATETIME NULL AFTER archived_by_membership_id;

UPDATE brand_scopes
SET archived_at = COALESCE(archived_at, updated_at, NOW()),
    retention_until = COALESCE(retention_until, DATE_ADD(COALESCE(updated_at, NOW()), INTERVAL 30 DAY)),
    tracking_mode = 'PAUSED'
WHERE scope_status_code = 0;
