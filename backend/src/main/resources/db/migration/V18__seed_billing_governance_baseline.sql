INSERT INTO billing_credit_rule_configs (
    id,
    rule_code,
    display_name,
    pricing_mode_code,
    default_credits_per_probe,
    min_credits_per_probe,
    max_credits_per_probe,
    default_gross_margin_rate,
    status_code,
    effective_from,
    rule_json
) VALUES (
    'brc-standard-probe-baseline-000001',
    'standard_probe',
    '标准探针',
    'CREDIT_PER_PROBE',
    1,
    1,
    1,
    0.7000,
    1,
    '2026-05-01 00:00:00',
    JSON_OBJECT(
        'unit', 'probe',
        'userFacingRule', '1 积分 = 1 标准探针额度',
        'source', '5.0.4 baseline'
    )
) ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    pricing_mode_code = VALUES(pricing_mode_code),
    default_credits_per_probe = VALUES(default_credits_per_probe),
    min_credits_per_probe = VALUES(min_credits_per_probe),
    max_credits_per_probe = VALUES(max_credits_per_probe),
    default_gross_margin_rate = VALUES(default_gross_margin_rate),
    status_code = VALUES(status_code),
    rule_json = VALUES(rule_json);

INSERT INTO billing_credit_rule_configs (
    id,
    rule_code,
    display_name,
    pricing_mode_code,
    default_credits_per_probe,
    min_credits_per_probe,
    max_credits_per_probe,
    default_gross_margin_rate,
    status_code,
    effective_from,
    rule_json
) VALUES (
    'brc-high-cost-probe-baseline-0001',
    'high_cost_probe',
    '高成本探针',
    'CREDIT_PER_PROBE',
    2,
    2,
    4,
    0.7000,
    1,
    '2026-05-01 00:00:00',
    JSON_OBJECT(
        'unit', 'probe',
        'userFacingRule', '高成本模型可按倍率消耗更多积分',
        'source', '5.0.4 baseline'
    )
) ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    pricing_mode_code = VALUES(pricing_mode_code),
    default_credits_per_probe = VALUES(default_credits_per_probe),
    min_credits_per_probe = VALUES(min_credits_per_probe),
    max_credits_per_probe = VALUES(max_credits_per_probe),
    default_gross_margin_rate = VALUES(default_gross_margin_rate),
    status_code = VALUES(status_code),
    rule_json = VALUES(rule_json);

INSERT INTO billing_provider_model_mappings (
    id,
    provider_code,
    provider_name,
    model_id,
    model_display_name,
    frontend_model_key,
    frontend_model_label,
    capability_code,
    default_credits_per_probe,
    internal_cost_yuan_per_probe,
    gross_margin_rate,
    status_code,
    config_json
) VALUES (
    'bpm-volcengine-doubao-standard-001',
    'volcengine',
    '火山引擎',
    'doubao-seed-1-6-251015',
    'Doubao Seed 1.6',
    'doubao',
    '豆包',
    'STANDARD_PROBE',
    1,
    0.050000,
    0.7000,
    1,
    JSON_OBJECT(
        'rewardSnapshotRequired', true,
        'source', '5.0.4 baseline'
    )
) ON DUPLICATE KEY UPDATE
    provider_name = VALUES(provider_name),
    model_display_name = VALUES(model_display_name),
    frontend_model_label = VALUES(frontend_model_label),
    capability_code = VALUES(capability_code),
    default_credits_per_probe = VALUES(default_credits_per_probe),
    internal_cost_yuan_per_probe = VALUES(internal_cost_yuan_per_probe),
    gross_margin_rate = VALUES(gross_margin_rate),
    status_code = VALUES(status_code),
    config_json = VALUES(config_json);

INSERT INTO billing_provider_model_mappings (
    id,
    provider_code,
    provider_name,
    model_id,
    model_display_name,
    frontend_model_key,
    frontend_model_label,
    capability_code,
    default_credits_per_probe,
    internal_cost_yuan_per_probe,
    gross_margin_rate,
    status_code,
    config_json
) VALUES (
    'bpm-volcengine-doubao-highcost-001',
    'volcengine',
    '火山引擎',
    'doubao-seed-2-0-pro-260215',
    'Doubao Seed 2.0 Pro',
    'doubao',
    '豆包',
    'HIGH_COST_PROBE',
    2,
    0.120000,
    0.7000,
    1,
    JSON_OBJECT(
        'rewardSnapshotRequired', true,
        'source', '5.0.4 baseline'
    )
) ON DUPLICATE KEY UPDATE
    provider_name = VALUES(provider_name),
    model_display_name = VALUES(model_display_name),
    frontend_model_label = VALUES(frontend_model_label),
    capability_code = VALUES(capability_code),
    default_credits_per_probe = VALUES(default_credits_per_probe),
    internal_cost_yuan_per_probe = VALUES(internal_cost_yuan_per_probe),
    gross_margin_rate = VALUES(gross_margin_rate),
    status_code = VALUES(status_code),
    config_json = VALUES(config_json);
