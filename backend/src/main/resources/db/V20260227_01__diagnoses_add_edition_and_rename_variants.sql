ALTER TABLE diagnoses
    ADD COLUMN edition TINYINT NOT NULL DEFAULT 1 COMMENT '0=EXPERIENCE, 1=STANDARD, 2=DEEP';

ALTER TABLE diagnoses
    CHANGE samples_per_prompt variants_per_core_prompt INT NOT NULL DEFAULT 10 COMMENT '每核心Prompt泛化数';
