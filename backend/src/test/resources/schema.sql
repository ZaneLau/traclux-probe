CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    company_name VARCHAR(200),
    credits_remaining INT NOT NULL DEFAULT 0,
    role INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    requested_ip VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS credit_transactions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type INT NOT NULL,
    amount INT NOT NULL,
    order_id VARCHAR(100),
    diagnosis_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_transactions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS tracking_configs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    brand_name VARCHAR(200) NOT NULL,
    edition INT NOT NULL DEFAULT 1,
    interval_type INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 1,
    next_run_at TIMESTAMP NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tracking_user_brand_status UNIQUE (user_id, brand_name, status),
    CONSTRAINT fk_tracking_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS diagnoses (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    brand_name VARCHAR(200) NOT NULL,
    category VARCHAR(200) NOT NULL,
    brand_website VARCHAR(500),
    user_prompt CLOB,
    competitors_input CLOB DEFAULT '[]',
    negative_terms CLOB,
    status INT NOT NULL DEFAULT 0,
    edition INT NOT NULL DEFAULT 1,
    is_trial INT NOT NULL DEFAULT 0,
    variants_per_core_prompt INT NOT NULL DEFAULT 10,
    source INT NOT NULL DEFAULT 0,
    tracking_id VARCHAR(36),
    share_token VARCHAR(64),
    share_expires_at TIMESTAMP,
    intent_clusters CLOB,
    prompt_matrix CLOB,
    summary_kpis CLOB,
    stability_score DECIMAL(5,2),
    seo_result CLOB,
    competitor_ranking CLOB,
    associated_keywords CLOB,
    report_data CLOB,
    total_llm_cost_yuan DECIMAL(10,4),
    total_api_calls INT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message CLOB,
    CONSTRAINT uk_diagnoses_share_token UNIQUE (share_token),
    CONSTRAINT fk_diagnoses_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_diagnoses_tracking FOREIGN KEY (tracking_id) REFERENCES tracking_configs(id)
);

CREATE TABLE IF NOT EXISTS runs (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_id VARCHAR(36) NOT NULL,
    prompt_id VARCHAR(100) NOT NULL,
    cluster_id VARCHAR(100) NOT NULL,
    prompt_text CLOB NOT NULL,
    temperature DECIMAL(3,2) NOT NULL,
    model VARCHAR(100) NOT NULL,
    raw_answer CLOB,
    parsed_result CLOB,
    status INT NOT NULL DEFAULT 0,
    latency_ms INT,
    token_count CLOB,
    error_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_runs_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS llm_call_logs (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_id VARCHAR(36),
    module INT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    cost_yuan DECIMAL(10,6),
    latency_ms INT,
    status INT NOT NULL,
    error_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_llm_call_logs_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id)
);

CREATE TABLE IF NOT EXISTS diagnosis_probe_events (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY,
    diagnosis_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    provisional INT NOT NULL DEFAULT 1,
    payload CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_probe_events_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id) ON DELETE CASCADE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS default_organization_id VARCHAR(36);

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id VARCHAR(36) NOT NULL,
    organization_name VARCHAR(200) NOT NULL,
    organization_type_code INT NOT NULL DEFAULT 0,
    billing_owner_type_code INT NOT NULL DEFAULT 0,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS organization_memberships (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role_code INT NOT NULL,
    seat_type_code INT NOT NULL DEFAULT 0,
    status_code INT NOT NULL DEFAULT 1,
    invited_by_user_id VARCHAR(36),
    membership_metadata_json CLOB,
    invited_at TIMESTAMP,
    joined_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36),
    actor_membership_id VARCHAR(36),
    audit_type_code VARCHAR(64) NOT NULL,
    target_type_code VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    payload_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quota_accounts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    subject_type_code VARCHAR(32) NOT NULL,
    subject_user_id VARCHAR(36),
    subject_membership_id VARCHAR(36),
    status_code INT NOT NULL DEFAULT 1,
    available_probe_quota BIGINT NOT NULL DEFAULT 0,
    reserved_probe_quota BIGINT NOT NULL DEFAULT 0,
    consumed_probe_quota BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quota_grants (
    id VARCHAR(36) PRIMARY KEY,
    quota_account_id VARCHAR(36) NOT NULL,
    grant_type_code VARCHAR(32) NOT NULL,
    source_type_code VARCHAR(32) NOT NULL,
    source_id VARCHAR(36),
    plan_id VARCHAR(36),
    total_probe_quota BIGINT NOT NULL,
    used_probe_quota BIGINT NOT NULL DEFAULT 0,
    reserved_probe_quota BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    rollover_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    grant_metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quota_ledgers (
    id VARCHAR(36) PRIMARY KEY,
    quota_account_id VARCHAR(36) NOT NULL,
    quota_grant_id VARCHAR(36),
    ledger_type_code VARCHAR(32) NOT NULL,
    probe_delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    source_type_code VARCHAR(32) NOT NULL,
    source_id VARCHAR(36),
    diagnosis_task_id VARCHAR(36),
    probe_id VARCHAR(36),
    organization_id VARCHAR(36),
    brand_scope_id VARCHAR(36),
    plan_version_id VARCHAR(36),
    plan_record_id VARCHAR(36),
    pricing_rule_snapshot_json CLOB,
    ledger_metadata_json CLOB,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    memo VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brands (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36),
    brand_name VARCHAR(200) NOT NULL,
    brand_name_en VARCHAR(200),
    category_name VARCHAR(200),
    canonical_domain VARCHAR(255),
    official_website VARCHAR(500),
    official_domains_json CLOB,
    brand_aliases_json CLOB,
    brand_description CLOB,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_scopes (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36),
    scope_name VARCHAR(200) NOT NULL,
    category_snapshot VARCHAR(200) NOT NULL,
    region_code VARCHAR(16) NOT NULL DEFAULT 'CN',
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    primary_domain VARCHAR(255),
    default_model_policy_json CLOB,
    tracking_mode VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config_generation INT NOT NULL DEFAULT 0,
    scope_status_code INT NOT NULL DEFAULT 1,
    archived_at TIMESTAMP,
    archived_by_membership_id VARCHAR(36),
    retention_until TIMESTAMP,
    internal_restore_until TIMESTAMP,
    purged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS web_surfaces (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36),
    surface_type_code INT NOT NULL,
    host VARCHAR(255) NOT NULL DEFAULT '',
    path_prefix VARCHAR(255) NOT NULL DEFAULT '',
    full_url VARCHAR(500),
    official_flag BOOLEAN NOT NULL DEFAULT TRUE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    region_code VARCHAR(16) NOT NULL DEFAULT 'CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_memberships (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    membership_id VARCHAR(36) NOT NULL,
    access_role_code INT NOT NULL,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_onboarding_seeds (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    source_domain VARCHAR(255),
    source_type_code INT NOT NULL,
    official_terms_json CLOB,
    brand_aliases_json CLOB,
    seed_snapshot_json CLOB,
    extraction_version VARCHAR(64),
    approved_by_membership_id VARCHAR(36),
    approved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plan_catalog (
    id VARCHAR(36) PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    billing_mode_code INT NOT NULL,
    price_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'CNY',
    seat_limit INT,
    model_limit INT,
    daily_probe_quota INT,
    quota_scope_code VARCHAR(32) NOT NULL,
    history_retention_days INT,
    validity_days INT,
    quota_policy_json CLOB,
    feature_flags_json CLOB,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    status_code INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    expires_at TIMESTAMP,
    renewal_mode_code INT NOT NULL DEFAULT 0,
    quota_policy_json CLOB,
    quota_scope_code VARCHAR(32),
    grant_generation_policy_json CLOB,
    seat_limit INT,
    model_limit INT,
    history_retention_days INT,
    feature_flags_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_tasks (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36),
    brand_scope_id VARCHAR(36),
    requested_by_user_id VARCHAR(36) NOT NULL,
    requested_by_membership_id VARCHAR(36),
    billing_quota_account_id VARCHAR(36),
    rerun_root_task_id VARCHAR(36),
    rerun_of_task_id VARCHAR(36),
    rerun_seq_no INT NOT NULL DEFAULT 0,
    plan_id VARCHAR(36),
    source_code INT NOT NULL DEFAULT 0,
    target_probe_count INT NOT NULL,
    realtime_search_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    target_model_product VARCHAR(128),
    engine_version VARCHAR(32) NOT NULL,
    billing_mode_code INT NOT NULL DEFAULT 0,
    billing_policy_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    status_code INT NOT NULL DEFAULT 0,
    progress_percent DECIMAL(5, 2) NOT NULL DEFAULT 0,
    sampling_generated_count INT NOT NULL DEFAULT 0,
    sampling_completed_count INT NOT NULL DEFAULT 0,
    sampling_failed_count INT NOT NULL DEFAULT 0,
    sampling_skipped_count INT NOT NULL DEFAULT 0,
    total_cost_yuan DECIMAL(18, 6) NOT NULL DEFAULT 0,
    search_requested_count INT NOT NULL DEFAULT 0,
    search_satisfied_count INT NOT NULL DEFAULT 0,
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    diagnosis_goal VARCHAR(255),
    brand_name_snapshot VARCHAR(200) NOT NULL,
    category_snapshot VARCHAR(200) NOT NULL,
    official_website_snapshot VARCHAR(500),
    task_order_json CLOB,
    task_metadata_json CLOB,
    charge_policy_snapshot_json CLOB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    failure_reason_code VARCHAR(64),
    failure_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_task_competitors (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    seq_no INT NOT NULL,
    competitor_brand_id VARCHAR(36),
    competitor_name_snapshot VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_events (
    seq BIGINT PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    provisional BOOLEAN NOT NULL DEFAULT TRUE,
    payload_json CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_progress_snapshots (
    diagnosis_task_id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36),
    current_step_code VARCHAR(32),
    current_step_label VARCHAR(200),
    steps_completed INT NOT NULL DEFAULT 0,
    steps_total INT NOT NULL DEFAULT 0,
    sampling_completed INT NOT NULL DEFAULT 0,
    sampling_expected_total INT NOT NULL DEFAULT 0,
    sampling_generated_total INT NOT NULL DEFAULT 0,
    sampling_missing_total INT NOT NULL DEFAULT 0,
    sampling_variant_shortfall BOOLEAN NOT NULL DEFAULT FALSE,
    percent DECIMAL(5, 2) NOT NULL DEFAULT 0,
    last_event_seq BIGINT,
    search_requested_count INT NOT NULL DEFAULT 0,
    search_satisfied_count INT NOT NULL DEFAULT 0,
    failed_probe_count INT NOT NULL DEFAULT 0,
    total_cost_yuan DECIMAL(18, 6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36),
    brand_id VARCHAR(36),
    category_snapshot VARCHAR(200) NOT NULL,
    target_model_product VARCHAR(128),
    report_status_code INT NOT NULL DEFAULT 1,
    sample_size INT,
    spontaneous_mention_rate DECIMAL(8, 6),
    confirmation_response_rate DECIMAL(8, 6),
    defense_completeness DECIMAL(8, 6),
    citation_rate DECIMAL(8, 6),
    abv_index DECIMAL(7, 2),
    abv_grade VARCHAR(8),
    dimension_penetration_json CLOB,
    brand_drift_score DECIMAL(7, 2),
    brand_drift_details_json CLOB,
    pathology_labels_json CLOB,
    top_insights_json CLOB,
    summary_json CLOB,
    charts_json CLOB,
    report_projection_json CLOB,
    generated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_reports_scope_generated
    ON diagnosis_reports (brand_scope_id, generated_at);

CREATE TABLE IF NOT EXISTS probe_evidences (
    id VARCHAR(36) PRIMARY KEY,
    dispatch_attempt_id VARCHAR(36),
    diagnosis_task_id VARCHAR(36) NOT NULL,
    probe_id VARCHAR(36),
    source_type_code VARCHAR(64),
    provider VARCHAR(64),
    domain VARCHAR(255),
    url VARCHAR(1000),
    title VARCHAR(500),
    snippet CLOB,
    search_query VARCHAR(500),
    retrieved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_probe_evidences_task_created
    ON probe_evidences (diagnosis_task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_probe_evidences_task_domain
    ON probe_evidences (diagnosis_task_id, domain);

CREATE TABLE IF NOT EXISTS probe_brand_mentions (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    probe_id VARCHAR(36),
    dispatch_attempt_id VARCHAR(36),
    brand_id VARCHAR(36),
    brand_name VARCHAR(200),
    brand_role_code INT,
    mention_level_code INT,
    mentioned_flag BOOLEAN,
    recommended_flag BOOLEAN,
    sentiment_code INT,
    position_index INT,
    confidence_score DECIMAL(8, 6),
    evidence_span CLOB,
    mention_metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_probe_brand_mentions_task
    ON probe_brand_mentions (diagnosis_task_id, brand_role_code, mention_level_code);

CREATE TABLE IF NOT EXISTS diagnosis_probes (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    probe_ref VARCHAR(128) NOT NULL,
    seq_no INT NOT NULL,
    bucket_type_code VARCHAR(32) NOT NULL,
    probe_type_code VARCHAR(32) NOT NULL,
    d1_identity VARCHAR(64) NOT NULL,
    d2_context VARCHAR(64) NOT NULL,
    d3_pain_point VARCHAR(64) NOT NULL,
    d4_jtbd VARCHAR(64) NOT NULL,
    d5_knowledge VARCHAR(64) NOT NULL,
    d6_constraints VARCHAR(64) NOT NULL,
    surface_intent VARCHAR(64),
    interaction_posture VARCHAR(64),
    conversation_mode VARCHAR(64),
    render_text CLOB NOT NULL,
    render_source_code INT NOT NULL DEFAULT 0,
    quality_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    render_metadata_json CLOB,
    triggered_rules_json CLOB,
    status_code INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_probes_task_seq
    ON diagnosis_probes (diagnosis_task_id, seq_no);

CREATE TABLE IF NOT EXISTS probe_dispatch_attempts (
    id VARCHAR(36) PRIMARY KEY,
    diagnosis_task_id VARCHAR(36) NOT NULL,
    probe_id VARCHAR(36) NOT NULL,
    round_no INT NOT NULL DEFAULT 0,
    attempt_no INT NOT NULL DEFAULT 1,
    provider VARCHAR(64) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    endpoint_ref VARCHAR(128),
    routing_intent_code VARCHAR(64),
    routing_candidates_json CLOB,
    search_requested BOOLEAN NOT NULL DEFAULT FALSE,
    search_satisfied BOOLEAN NOT NULL DEFAULT FALSE,
    search_fallback_flag BOOLEAN NOT NULL DEFAULT FALSE,
    search_call_count INT NOT NULL DEFAULT 0,
    search_queries_json CLOB,
    raw_response_text CLOB,
    think_content CLOB,
    citations_json CLOB,
    response_metadata_json CLOB,
    input_tokens INT,
    output_tokens INT,
    total_tokens INT,
    latency_ms INT,
    status_code INT NOT NULL,
    failure_reason_code VARCHAR(64),
    selected_final BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_probe_dispatch_attempts_task_created
    ON probe_dispatch_attempts (diagnosis_task_id, created_at);

CREATE TABLE IF NOT EXISTS integration_outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json CLOB NOT NULL,
    status_code INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_space_topics (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    seq_no INT NOT NULL DEFAULT 1,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    source_code INT NOT NULL DEFAULT 0,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_space_prompts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    topic_id VARCHAR(36),
    seq_no INT NOT NULL DEFAULT 1,
    prompt_text CLOB NOT NULL,
    region_code VARCHAR(16) NOT NULL DEFAULT 'CN',
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    source_code INT NOT NULL DEFAULT 0,
    status_code INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS probe_plan_versions (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    status_code INT NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    config_generation_snapshot INT NOT NULL DEFAULT 0,
    active_recurring_probe_count INT NOT NULL DEFAULT 0,
    estimated_immediate_credits INT NOT NULL DEFAULT 0,
    estimated_recurring_credits_per_cycle INT NOT NULL DEFAULT 0,
    snapshot_json CLOB,
    created_by_user_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS probe_plan_records (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    plan_version_id VARCHAR(36) NOT NULL,
    record_no INT NOT NULL,
    change_type_code VARCHAR(32) NOT NULL,
    execute_mode_code VARCHAR(32) NOT NULL,
    status_code INT NOT NULL DEFAULT 1,
    immediate_estimated_credits INT NOT NULL DEFAULT 0,
    recurring_estimated_credits_per_cycle INT NOT NULL DEFAULT 0,
    triggered_run_id VARCHAR(36),
    effective_from_cycle_at TIMESTAMP,
    summary_json CLOB,
    created_by_user_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS probe_plan_record_items (
    id VARCHAR(36) PRIMARY KEY,
    record_id VARCHAR(36) NOT NULL,
    plan_version_id VARCHAR(36) NOT NULL,
    probe_id VARCHAR(36),
    topic_id VARCHAR(36),
    action_code VARCHAR(32) NOT NULL,
    execute_mode_code VARCHAR(32) NOT NULL,
    seq_no_snapshot INT,
    probe_text_snapshot CLOB,
    source_code_snapshot INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_provider_model_mappings (
    id VARCHAR(36) PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128),
    model_id VARCHAR(128) NOT NULL,
    model_display_name VARCHAR(128),
    frontend_model_key VARCHAR(64) NOT NULL,
    frontend_model_label VARCHAR(64) NOT NULL,
    capability_code VARCHAR(64) NOT NULL DEFAULT 'STANDARD_PROBE',
    default_credits_per_probe INT NOT NULL DEFAULT 1,
    internal_cost_yuan_per_probe DECIMAL(18, 6) NOT NULL DEFAULT 0,
    gross_margin_rate DECIMAL(8, 4),
    status_code INT NOT NULL DEFAULT 1,
    config_json CLOB,
    updated_by_user_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_credit_rule_configs (
    id VARCHAR(36) PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    pricing_mode_code VARCHAR(64) NOT NULL DEFAULT 'CREDIT_PER_PROBE',
    default_credits_per_probe INT NOT NULL DEFAULT 1,
    min_credits_per_probe INT NOT NULL DEFAULT 1,
    max_credits_per_probe INT NOT NULL DEFAULT 1,
    default_gross_margin_rate DECIMAL(8, 4),
    status_code INT NOT NULL DEFAULT 1,
    effective_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP,
    rule_json CLOB,
    updated_by_user_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS provider_quota_snapshot_sync_records (
    id VARCHAR(36) PRIMARY KEY,
    trigger_type_code VARCHAR(64) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    server_node_label VARCHAR(128) NULL,
    snapshot_path VARCHAR(512) NULL,
    snapshot_version VARCHAR(128) NULL,
    fetched_at TIMESTAMP NULL,
    model_count INT NULL,
    total_remaining_tokens BIGINT NULL,
    total_consumed_tokens_since_sync BIGINT NULL,
    total_estimated_remaining_tokens BIGINT NULL,
    reason VARCHAR(255) NULL,
    message VARCHAR(1000) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
