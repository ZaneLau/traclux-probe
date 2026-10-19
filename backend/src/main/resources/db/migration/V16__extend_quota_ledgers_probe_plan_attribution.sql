ALTER TABLE quota_ledgers
    ADD COLUMN organization_id VARCHAR(36) NULL AFTER probe_id,
    ADD COLUMN brand_scope_id VARCHAR(36) NULL AFTER organization_id,
    ADD COLUMN plan_version_id VARCHAR(36) NULL AFTER brand_scope_id,
    ADD COLUMN plan_record_id VARCHAR(36) NULL AFTER plan_version_id,
    ADD COLUMN pricing_rule_snapshot_json JSON NULL AFTER plan_record_id,
    ADD COLUMN ledger_metadata_json JSON NULL AFTER pricing_rule_snapshot_json;

ALTER TABLE quota_ledgers
    ADD INDEX idx_quota_ledgers_brand_plan_created (brand_scope_id, plan_record_id, created_at),
    ADD INDEX idx_quota_ledgers_plan_version_created (plan_version_id, created_at);
