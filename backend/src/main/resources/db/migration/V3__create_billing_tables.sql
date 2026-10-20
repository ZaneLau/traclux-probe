CREATE TABLE IF NOT EXISTS model_price_catalog (
    id VARCHAR(36) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    plugin_code VARCHAR(64) NULL,
    pricing_policy_version VARCHAR(32) NOT NULL,
    input_price_per_1k DECIMAL(18,8) NOT NULL DEFAULT 0,
    output_price_per_1k DECIMAL(18,8) NOT NULL DEFAULT 0,
    plugin_price_per_call DECIMAL(18,8) NOT NULL DEFAULT 0,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'CNY',
    effective_from DATETIME NOT NULL,
    effective_to DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_price_catalog_window (provider, model_id, plugin_code, pricing_policy_version, effective_from),
    INDEX idx_model_price_catalog_lookup (provider, model_id, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS plan_catalog (
    id VARCHAR(36) PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    billing_mode_code TINYINT NOT NULL COMMENT '0=ONE_TIME,1=MONTHLY,2=YEARLY,3=CONTRACT',
    price_amount DECIMAL(12,2) NOT NULL,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'CNY',
    seat_limit INT NULL,
    model_limit INT NULL,
    daily_probe_quota INT NULL,
    quota_scope_code VARCHAR(32) NOT NULL,
    history_retention_days INT NULL,
    validity_days INT NULL,
    quota_policy_json JSON NULL,
    feature_flags_json JSON NULL,
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=INACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    organization_id VARCHAR(36) NOT NULL,
    purchaser_user_id VARCHAR(36) NOT NULL,
    purchaser_membership_id VARCHAR(36) NULL,
    plan_id VARCHAR(36) NOT NULL,
    order_type_code TINYINT NOT NULL COMMENT '0=EXPERIENCE,1=SUBSCRIPTION,2=ADDON,3=CONTRACT',
    amount DECIMAL(12,2) NOT NULL,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status_code TINYINT NOT NULL COMMENT '0=CREATED,1=PAID,2=CLOSED,3=REFUNDED',
    pricing_policy_version VARCHAR(32) NOT NULL,
    granted_probe_quota INT NULL,
    grant_expires_at DATETIME NULL,
    ext_trade_no VARCHAR(128) NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_orders_organization_created (organization_id, created_at),
    CONSTRAINT fk_orders_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_orders_purchaser_user FOREIGN KEY (purchaser_user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_purchaser_membership FOREIGN KEY (purchaser_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_plan FOREIGN KEY (plan_id) REFERENCES plan_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    provider_code TINYINT NOT NULL COMMENT '0=WECHAT,1=ALIPAY,2=MANUAL,3=OTHER',
    provider_trade_no VARCHAR(128) NULL,
    status_code TINYINT NOT NULL COMMENT '0=PENDING,1=SUCCESS,2=FAILED,3=REFUNDED',
    callback_payload_json JSON NULL,
    paid_amount DECIMAL(12,2) NOT NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payments_order (order_id, created_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    status_code TINYINT NOT NULL COMMENT '0=CREATED,1=SUCCESS,2=FAILED',
    refund_amount DECIMAL(12,2) NOT NULL,
    refund_reason_code TINYINT NOT NULL,
    provider_refund_no VARCHAR(128) NULL,
    refunded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refunds_order (order_id, created_at),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    status_code TINYINT NOT NULL COMMENT '0=ACTIVE,1=PAUSED,2=EXPIRED,3=CANCELLED',
    started_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    renewal_mode_code TINYINT NOT NULL COMMENT '0=MANUAL,1=AUTO',
    quota_policy_json JSON NULL,
    quota_scope_code VARCHAR(32) NOT NULL,
    grant_generation_policy_json JSON NULL,
    seat_limit INT NULL,
    model_limit INT NULL,
    history_retention_days INT NULL,
    feature_flags_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_subscriptions_organization_status (organization_id, status_code, expires_at),
    CONSTRAINT fk_subscriptions_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plan_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quota_accounts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    subject_type_code VARCHAR(32) NOT NULL COMMENT 'PERSONAL/ORGANIZATION_POOL/MEMBER_ADDON/ENTERPRISE_CONTRACT',
    subject_user_id VARCHAR(36) NULL,
    subject_membership_id VARCHAR(36) NULL,
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=SUSPENDED,3=CLOSED',
    available_probe_quota BIGINT NOT NULL DEFAULT 0,
    reserved_probe_quota BIGINT NOT NULL DEFAULT 0,
    consumed_probe_quota BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quota_accounts_subject (organization_id, subject_type_code, subject_user_id, subject_membership_id),
    CONSTRAINT fk_quota_accounts_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_quota_accounts_user FOREIGN KEY (subject_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_quota_accounts_membership FOREIGN KEY (subject_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quota_grants (
    id VARCHAR(36) PRIMARY KEY,
    quota_account_id VARCHAR(36) NOT NULL,
    grant_type_code VARCHAR(32) NOT NULL COMMENT 'ONE_TIME_ORDER/DAILY_SUBSCRIPTION/CONTRACT/MANUAL/ADDON_PACKAGE',
    source_type_code VARCHAR(32) NOT NULL COMMENT 'order/subscription/system/admin',
    source_id VARCHAR(36) NULL,
    plan_id VARCHAR(36) NULL,
    total_probe_quota BIGINT NOT NULL,
    used_probe_quota BIGINT NOT NULL DEFAULT 0,
    reserved_probe_quota BIGINT NOT NULL DEFAULT 0,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NOT NULL,
    expires_at DATETIME NULL,
    rollover_allowed TINYINT(1) NOT NULL DEFAULT 0,
    grant_metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_quota_grants_account_window (quota_account_id, valid_from, valid_to),
    CONSTRAINT fk_quota_grants_account FOREIGN KEY (quota_account_id) REFERENCES quota_accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_quota_grants_plan FOREIGN KEY (plan_id) REFERENCES plan_catalog(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quota_ledgers (
    id VARCHAR(36) PRIMARY KEY,
    quota_account_id VARCHAR(36) NOT NULL,
    quota_grant_id VARCHAR(36) NULL,
    ledger_type_code VARCHAR(32) NOT NULL COMMENT 'GRANT/RESERVE/CONSUME/RELEASE/EXPIRE/REVERSAL',
    probe_delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    source_type_code VARCHAR(32) NOT NULL COMMENT 'order/subscription/task/probe/system',
    source_id VARCHAR(36) NULL,
    diagnosis_task_id VARCHAR(36) NULL,
    probe_id VARCHAR(36) NULL,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    memo VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_quota_ledgers_account_created (quota_account_id, created_at),
    CONSTRAINT fk_quota_ledgers_account FOREIGN KEY (quota_account_id) REFERENCES quota_accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_quota_ledgers_grant FOREIGN KEY (quota_grant_id) REFERENCES quota_grants(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
