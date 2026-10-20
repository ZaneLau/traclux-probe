ALTER TABLE diagnosis_tasks
    ADD INDEX idx_diagnosis_tasks_requested_user_created (requested_by_user_id, created_at),
    ADD INDEX idx_diagnosis_tasks_requested_membership_created (requested_by_membership_id, created_at);

ALTER TABLE quota_ledgers
    ADD INDEX idx_quota_ledgers_task_created (diagnosis_task_id, created_at),
    ADD INDEX idx_quota_ledgers_probe_created (probe_id, created_at),
    ADD INDEX idx_quota_ledgers_source_created (source_type_code, source_id, created_at);

ALTER TABLE probe_evidences
    ADD INDEX idx_probe_evidences_task_created (diagnosis_task_id, created_at),
    ADD INDEX idx_probe_evidences_task_domain (diagnosis_task_id, domain);

ALTER TABLE model_invocations
    ADD INDEX idx_model_invocations_probe_created (probe_id, created_at);
