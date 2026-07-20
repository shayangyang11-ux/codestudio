CREATE TABLE IF NOT EXISTS project_theme_profile (
                                                     id UUID PRIMARY KEY,
                                                     project_id UUID NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,

    industry VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    theme_key VARCHAR(64) NOT NULL DEFAULT 'enterprise_default',
    visual_tone VARCHAR(64) NOT NULL DEFAULT 'CLEAN',

    primary_color VARCHAR(32) NOT NULL DEFAULT '#2563eb',
    secondary_color VARCHAR(32),
    accent_color VARCHAR(32),

    density VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    radius VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    login_layout VARCHAR(64) NOT NULL DEFAULT 'BRAND_SPLIT',
    navigation_style VARCHAR(64) NOT NULL DEFAULT 'SIDE_NAV',
    dark_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    source VARCHAR(64) NOT NULL DEFAULT 'USER',
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,

    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_project_theme_profile_project_id
    ON project_theme_profile(project_id);

COMMENT ON TABLE project_theme_profile IS
    'Project-level theme profile used by UI_CONTRACT Runtime and future prototype source generation.';

COMMENT ON COLUMN project_theme_profile.theme_key IS
    'Runtime theme key, such as enterprise_default, tech_modern, commerce_light, industrial_pro, finance_stable, gov_clean.';

COMMENT ON COLUMN project_theme_profile.login_layout IS
    'Preferred auth page layout, such as BRAND_SPLIT, CENTER_CARD, MINIMAL.';
