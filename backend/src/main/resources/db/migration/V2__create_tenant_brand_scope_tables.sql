ALTER TABLE users
    ADD COLUMN default_organization_id VARCHAR(36) NULL AFTER company_name;

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id VARCHAR(36) NOT NULL,
    organization_name VARCHAR(200) NOT NULL,
    organization_type_code TINYINT NOT NULL DEFAULT 0 COMMENT '0=PERSONAL,1=TEAM,2=AGENCY,3=ENTERPRISE',
    billing_owner_type_code TINYINT NOT NULL DEFAULT 0 COMMENT '0=USER,1=ORGANIZATION',
    billing_profile_json JSON NULL,
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=SUSPENDED,3=ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_organizations_owner (owner_user_id),
    INDEX idx_organizations_status (status_code),
    CONSTRAINT fk_organizations_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE users
    ADD CONSTRAINT fk_users_default_organization
    FOREIGN KEY (default_organization_id) REFERENCES organizations(id);

CREATE TABLE IF NOT EXISTS organization_memberships (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role_code TINYINT NOT NULL COMMENT '0=OWNER,1=ADMIN,2=MEMBER,3=VIEWER',
    seat_type_code TINYINT NOT NULL DEFAULT 0 COMMENT '0=FULL,1=LIMITED,2=BILLING_ONLY',
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '0=INVITED,1=ACTIVE,2=SUSPENDED,3=REMOVED',
    invited_by_user_id VARCHAR(36) NULL,
    membership_metadata_json JSON NULL,
    invited_at DATETIME NULL,
    joined_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_organization_membership (organization_id, user_id),
    INDEX idx_organization_memberships_user (user_id, status_code),
    CONSTRAINT fk_organization_memberships_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_memberships_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_organization_memberships_inviter FOREIGN KEY (invited_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS brands (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36) NULL,
    brand_name VARCHAR(200) NOT NULL,
    brand_name_en VARCHAR(200) NULL,
    category_name VARCHAR(200) NULL,
    canonical_domain VARCHAR(255) NULL,
    official_website VARCHAR(500) NULL,
    official_domains_json JSON NULL,
    brand_aliases_json JSON NULL,
    brand_description TEXT NULL,
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=PAUSED,3=ARCHIVED',
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brands_organization_name (organization_id, brand_name),
    INDEX idx_brands_canonical_domain (canonical_domain),
    CONSTRAINT fk_brands_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_brands_created_by_membership FOREIGN KEY (created_by_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS brand_scopes (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36) NULL,
    scope_name VARCHAR(200) NOT NULL,
    category_snapshot VARCHAR(200) NOT NULL,
    region_code VARCHAR(16) NOT NULL DEFAULT 'CN',
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    primary_domain VARCHAR(255) NULL,
    default_model_policy_json JSON NULL,
    scope_status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=PAUSED,3=ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_scopes_identity (brand_id, category_snapshot, region_code, language),
    INDEX idx_brand_scopes_brand (brand_id, scope_status_code),
    CONSTRAINT fk_brand_scopes_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_scopes_brand FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_scopes_created_by_membership FOREIGN KEY (created_by_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS web_surfaces (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    brand_scope_id VARCHAR(36) NOT NULL,
    created_by_membership_id VARCHAR(36) NULL,
    surface_type_code TINYINT NOT NULL COMMENT '0=DOMAIN,1=SUBDOMAIN,2=PATH_PREFIX,3=LANDING_PAGE,4=MARKETPLACE_STORE',
    host VARCHAR(255) NOT NULL DEFAULT '',
    path_prefix VARCHAR(255) NOT NULL DEFAULT '',
    full_url VARCHAR(500) NULL,
    official_flag TINYINT(1) NOT NULL DEFAULT 1,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    region_code VARCHAR(16) NOT NULL DEFAULT 'CN',
    language VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=PAUSED,3=ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_web_surfaces_identity (brand_scope_id, surface_type_code, host, path_prefix),
    INDEX idx_web_surfaces_host_path (host, path_prefix),
    CONSTRAINT fk_web_surfaces_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_web_surfaces_brand FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE CASCADE,
    CONSTRAINT fk_web_surfaces_scope FOREIGN KEY (brand_scope_id) REFERENCES brand_scopes(id) ON DELETE CASCADE,
    CONSTRAINT fk_web_surfaces_created_by_membership FOREIGN KEY (created_by_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS brand_memberships (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    membership_id VARCHAR(36) NOT NULL,
    access_role_code TINYINT NOT NULL COMMENT '0=OWNER,1=EDITOR,2=VIEWER',
    status_code TINYINT NOT NULL DEFAULT 1 COMMENT '1=ACTIVE,2=REVOKED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_memberships_identity (brand_id, membership_id),
    CONSTRAINT fk_brand_memberships_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_memberships_brand FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_memberships_membership FOREIGN KEY (membership_id) REFERENCES organization_memberships(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS brand_onboarding_seeds (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    source_domain VARCHAR(255) NULL,
    source_type_code TINYINT NOT NULL COMMENT '0=OFFICIAL_WEBSITE,1=MANUAL_IMPORT,2=OTHER',
    official_terms_json JSON NULL,
    brand_aliases_json JSON NULL,
    seed_snapshot_json JSON NULL,
    extraction_version VARCHAR(64) NULL,
    approved_by_membership_id VARCHAR(36) NULL,
    approved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_brand_onboarding_seeds_brand (organization_id, brand_id, approved_at),
    CONSTRAINT fk_brand_onboarding_seeds_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_onboarding_seeds_brand FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_onboarding_seeds_approved_by FOREIGN KEY (approved_by_membership_id) REFERENCES organization_memberships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
