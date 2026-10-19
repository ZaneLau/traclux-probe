ALTER TABLE diagnosis_reports
    ADD INDEX idx_diagnosis_reports_scope_generated (brand_scope_id, generated_at);
