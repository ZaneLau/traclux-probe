ALTER TABLE brand_scopes
    ADD COLUMN tracking_mode VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE brand_scopes
    ADD COLUMN config_generation INT NOT NULL DEFAULT 0;

UPDATE plan_catalog
SET model_limit = CASE plan_code
    WHEN 'experience' THEN 1
    WHEN 'basic' THEN 1
    WHEN 'team' THEN 3
    ELSE model_limit
END
WHERE model_limit IS NULL
  AND plan_code IN ('experience', 'basic', 'team');
