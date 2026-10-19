ALTER TABLE diagnosis_probes
    ADD COLUMN triggered_rules_json JSON NULL AFTER render_metadata_json;

