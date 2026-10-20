-- Align older local legacy schemas with the minimum column set expected by the
-- current legacy runtime. This keeps backfill and dual-write compatible with
-- historical MySQL instances that were initialized before the share/tracking
-- fields were introduced.

SET @ddl := IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'diagnoses'
          AND column_name = 'source'
    ),
    'SELECT 1',
    'ALTER TABLE diagnoses ADD COLUMN source TINYINT NOT NULL DEFAULT 0 COMMENT ''0=MANUAL,1=TRACKING'' AFTER is_trial'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'diagnoses'
          AND column_name = 'tracking_id'
    ),
    'SELECT 1',
    'ALTER TABLE diagnoses ADD COLUMN tracking_id VARCHAR(36) NULL AFTER source'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'diagnoses'
          AND column_name = 'share_token'
    ),
    'SELECT 1',
    'ALTER TABLE diagnoses ADD COLUMN share_token VARCHAR(64) NULL AFTER tracking_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'diagnoses'
          AND column_name = 'share_expires_at'
    ),
    'SELECT 1',
    'ALTER TABLE diagnoses ADD COLUMN share_expires_at DATETIME NULL AFTER share_token'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
