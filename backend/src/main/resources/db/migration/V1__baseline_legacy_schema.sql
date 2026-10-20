-- GeoProbe legacy baseline schema.
-- For existing MySQL environments Flyway baselines at version 1 and only applies later migrations.

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    company_name VARCHAR(200),
    credits_remaining INT NOT NULL DEFAULT 0,
    role TINYINT NOT NULL DEFAULT 0 COMMENT '0=USER,1=ADMIN',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS credit_transactions (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    type TINYINT NOT NULL COMMENT '1=PURCHASE,2=CONSUME,3=REFUND,4=GRANT',
    amount INT NOT NULL,
    order_id VARCHAR(100),
    diagnosis_id VARCHAR(36),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_credit_tx_user (user_id),
    UNIQUE KEY uk_credit_tx_diagnosis_type (diagnosis_id, type),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracking_configs (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    brand_name VARCHAR(200) NOT NULL,
    edition TINYINT NOT NULL DEFAULT 1 COMMENT '0=EXPERIENCE,1=STANDARD,2=DEEP',
    interval_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=WEEKLY,2=BIWEEKLY,3=MONTHLY',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=PAUSED,3=CANCELLED',
    next_run_at DATETIME NOT NULL,
    last_run_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tracking_user_status (user_id, status),
    INDEX idx_tracking_next_run (status, next_run_at),
    UNIQUE KEY uk_tracking_user_brand_status (user_id, brand_name, status),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagnoses (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    brand_name VARCHAR(200) NOT NULL,
    category VARCHAR(200) NOT NULL,
    brand_website VARCHAR(500),
    user_prompt TEXT,
    competitors_input JSON DEFAULT ('[]'),
    negative_terms JSON,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING,1=RUNNING,2=COMPLETED,3=FAILED,4=CANCELLED,5=QUEUED,6=EXPIRED',
    edition TINYINT NOT NULL DEFAULT 1 COMMENT '0=EXPERIENCE,1=STANDARD,2=DEEP',
    is_trial TINYINT(1) NOT NULL DEFAULT 0,
    variants_per_core_prompt INT NOT NULL DEFAULT 10 COMMENT '每核心Prompt泛化数',
    source TINYINT NOT NULL DEFAULT 0 COMMENT '0=MANUAL,1=TRACKING',
    tracking_id VARCHAR(36),
    share_token VARCHAR(64),
    share_expires_at DATETIME,
    intent_clusters JSON,
    prompt_matrix JSON,
    summary_kpis JSON,
    stability_score DECIMAL(5,2),
    seo_result JSON,
    competitor_ranking JSON,
    associated_keywords JSON,
    report_data JSON,
    total_llm_cost_yuan DECIMAL(10,4),
    total_api_calls INT,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT,
    INDEX idx_diagnoses_user (user_id),
    INDEX idx_diagnoses_user_created_at (user_id, created_at),
    INDEX idx_diagnoses_status (status),
    UNIQUE KEY uk_diagnoses_share_token (share_token),
    INDEX idx_diagnoses_tracking_id (tracking_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (tracking_id) REFERENCES tracking_configs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS runs (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    diagnosis_id VARCHAR(36) NOT NULL,
    prompt_id VARCHAR(100) NOT NULL,
    cluster_id VARCHAR(100) NOT NULL,
    prompt_text TEXT NOT NULL,
    temperature DECIMAL(3,2) NOT NULL,
    model VARCHAR(100) NOT NULL,
    raw_answer TEXT,
    parsed_result JSON,
    status TINYINT NOT NULL DEFAULT 0,
    latency_ms INT,
    token_count JSON,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_runs_diagnosis (diagnosis_id),
    INDEX idx_runs_cluster (diagnosis_id, cluster_id),
    FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS llm_call_logs (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    diagnosis_id VARCHAR(36),
    module TINYINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    cost_yuan DECIMAL(10,6),
    latency_ms INT,
    status TINYINT NOT NULL,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_llm_logs_diagnosis (diagnosis_id),
    INDEX idx_llm_logs_module (module),
    FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS diagnosis_probe_events (
    seq BIGINT PRIMARY KEY AUTO_INCREMENT,
    diagnosis_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    provisional TINYINT(1) NOT NULL DEFAULT 1,
    payload JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_probe_events_diagnosis_seq (diagnosis_id, seq),
    CONSTRAINT fk_probe_events_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
