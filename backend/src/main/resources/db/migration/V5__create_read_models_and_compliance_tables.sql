CREATE TABLE IF NOT EXISTS diagnosis_events (
    seq BIGINT PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    provisional TINYINT(1) NOT NULL DEFAULT 1,
    payload_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_diagnosis_events_seq (diagnosis_task_id, seq),
    INDEX idx_diagnosis_events_task_created (diagnosis_task_id, created_at),
    CONSTRAINT fk_diagnosis_events_task FOREIGN KEY (diagnosis_task_id) REFERENCES diagnosis_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagnosis_progress_snapshots (
    diagnosis_task_id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NULL,
    current_step_code VARCHAR(32) NULL,
    current_step_label VARCHAR(200) NULL,
    steps_completed INT NOT NULL DEFAULT 0,
    steps_total INT NOT NULL DEFAULT 0,
    sampling_completed INT NOT NULL DEFAULT 0,
    sampling_expected_total INT NOT NULL DEFAULT 0,
    sampling_generated_total INT NOT NULL DEFAULT 0,
    sampling_missing_total INT NOT NULL DEFAULT 0,
    sampling_variant_shortfall TINYINT(1) NOT NULL DEFAULT 0,
    percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    last_event_seq BIGINT NULL,
    search_requested_count INT NOT NULL DEFAULT 0,
    search_satisfied_count INT NOT NULL DEFAULT 0,
    failed_probe_count INT NOT NULL DEFAULT 0,
    total_cost_yuan DECIMAL(18,6) NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_diagnosis_progress_snapshots_org_updated (organization_id, updated_at),
    CONSTRAINT fk_diagnosis_progress_snapshots_task FOREIGN KEY (diagnosis_task_id) REFERENCES diagnosis_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_diagnosis_progress_snapshots_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_diagnosis_progress_snapshots_scope FOREIGN KEY (brand_scope_id) REFERENCES brand_scopes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL UNIQUE,
    organization_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NULL,
    brand_id VARCHAR(36) NULL,
    category_snapshot VARCHAR(200) NOT NULL,
    target_model_product VARCHAR(128) NULL,
    report_status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=READY,2=ARCHIVED',
    sample_size INT NULL,
    spontaneous_mention_rate DECIMAL(8,6) NULL,
    confirmation_response_rate DECIMAL(8,6) NULL,
    defense_completeness DECIMAL(8,6) NULL,
    citation_rate DECIMAL(8,6) NULL,
    abv_index DECIMAL(7,2) NULL,
    abv_grade VARCHAR(8) NULL,
    dimension_penetration_json JSON NULL,
    brand_drift_score DECIMAL(7,2) NULL,
    brand_drift_details_json JSON NULL,
    pathology_labels_json JSON NULL,
    top_insights_json JSON NULL,
    summary_json JSON NULL,
    charts_json JSON NULL,
    report_projection_json JSON NULL,
    generated_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_diagnosis_reports_org_scope_generated (organization_id, brand_scope_id, generated_at),
    CONSTRAINT fk_diagnosis_reports_task FOREIGN KEY (diagnosis_task_id) REFERENCES diagnosis_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_diagnosis_reports_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_diagnosis_reports_scope FOREIGN KEY (brand_scope_id) REFERENCES brand_scopes(id) ON DELETE SET NULL,
    CONSTRAINT fk_diagnosis_reports_brand FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    status_code TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING,1=SENT,2=FAILED,3=DEAD',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    INDEX idx_integration_outbox_status_retry (status_code, next_retry_at),
    INDEX idx_integration_outbox_aggregate (aggregate_type, aggregate_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_export_jobs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    requested_by_membership_id VARCHAR(36) NOT NULL,
    export_scope_code VARCHAR(64) NOT NULL,
    target_type_code VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    format_code VARCHAR(32) NOT NULL,
    status_code TINYINT NOT NULL COMMENT '0=PENDING,1=RUNNING,2=COMPLETED,3=FAILED,4=EXPIRED',
    object_uri VARCHAR(500) NULL,
    checksum VARCHAR(128) NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    INDEX idx_data_export_jobs_org_created (organization_id, created_at),
    CONSTRAINT fk_data_export_jobs_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_data_export_jobs_membership FOREIGN KEY (requested_by_membership_id) REFERENCES organization_memberships(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_deletion_requests (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    requested_by_membership_id VARCHAR(36) NOT NULL,
    approved_by_membership_id VARCHAR(36) NULL,
    target_type_code VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    request_reason VARCHAR(500) NULL,
    status_code TINYINT NOT NULL COMMENT '0=PENDING,1=APPROVED,2=REJECTED,3=RUNNING,4=COMPLETED,5=FAILED',
    retention_exception_code VARCHAR(64) NULL,
    result_summary_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME NULL,
    executed_at DATETIME NULL,
    INDEX idx_data_deletion_requests_org_created (organization_id, created_at),
    CONSTRAINT fk_data_deletion_requests_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_data_deletion_requests_requested_by FOREIGN KEY (requested_by_membership_id) REFERENCES organization_memberships(id),
    CONSTRAINT fk_data_deletion_requests_approved_by FOREIGN KEY (approved_by_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS compliance_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    actor_membership_id VARCHAR(36) NULL,
    audit_type_code VARCHAR(64) NOT NULL,
    target_type_code VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NULL,
    payload_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_compliance_audit_logs_org_created (organization_id, created_at),
    INDEX idx_compliance_audit_logs_actor (actor_membership_id, created_at),
    CONSTRAINT fk_compliance_audit_logs_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_compliance_audit_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_compliance_audit_logs_actor_membership FOREIGN KEY (actor_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS migration_legacy_keymap (
    id VARCHAR(36) PRIMARY KEY,
    legacy_table_name VARCHAR(64) NOT NULL,
    legacy_id VARCHAR(64) NOT NULL,
    new_table_name VARCHAR(64) NOT NULL,
    new_id VARCHAR(64) NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_migration_legacy_keymap_identity (legacy_table_name, legacy_id, new_table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS migration_backfill_issues (
    id VARCHAR(36) PRIMARY KEY,
    issue_type_code VARCHAR(64) NOT NULL,
    severity_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=INFO,2=WARN,3=ERROR',
    legacy_table_name VARCHAR(64) NULL,
    legacy_id VARCHAR(64) NULL,
    diagnosis_id VARCHAR(36) NULL,
    organization_id VARCHAR(36) NULL,
    payload_json JSON NULL,
    resolved_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    INDEX idx_migration_backfill_issues_resolution (resolved_flag, severity_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
