CREATE TABLE IF NOT EXISTS billing_provider_model_mappings (
    id VARCHAR(36) PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128) NULL,
    model_id VARCHAR(128) NOT NULL,
    model_display_name VARCHAR(128) NULL,
    frontend_model_key VARCHAR(64) NOT NULL,
    frontend_model_label VARCHAR(64) NOT NULL,
    capability_code VARCHAR(64) NOT NULL DEFAULT 'STANDARD_PROBE',
    default_credits_per_probe INT NOT NULL DEFAULT 1,
    internal_cost_yuan_per_probe DECIMAL(18,6) NOT NULL DEFAULT 0,
    gross_margin_rate DECIMAL(8,4) NULL,
    status_code TINYINT NOT NULL DEFAULT 1,
    config_json JSON NULL,
    updated_by_user_id VARCHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_billing_provider_model_mapping (provider_code, model_id, frontend_model_key, capability_code),
    INDEX idx_billing_provider_model_frontend (frontend_model_key, status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS billing_credit_rule_configs (
    id VARCHAR(36) PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    pricing_mode_code VARCHAR(64) NOT NULL DEFAULT 'CREDIT_PER_PROBE',
    default_credits_per_probe INT NOT NULL DEFAULT 1,
    min_credits_per_probe INT NOT NULL DEFAULT 1,
    max_credits_per_probe INT NOT NULL DEFAULT 1,
    default_gross_margin_rate DECIMAL(8,4) NULL,
    status_code TINYINT NOT NULL DEFAULT 1,
    effective_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to DATETIME NULL,
    rule_json JSON NULL,
    updated_by_user_id VARCHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_billing_credit_rule_status (status_code, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
