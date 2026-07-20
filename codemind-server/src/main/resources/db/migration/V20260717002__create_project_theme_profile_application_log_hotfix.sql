CREATE TABLE IF NOT EXISTS project_theme_profile_application_log (
                                                                     id UUID PRIMARY KEY,
                                                                     workspace_id UUID NOT NULL,
                                                                     project_id UUID NOT NULL,
                                                                     project_code VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    style_text TEXT,
    target_page_type VARCHAR(128),
    agent_mode VARCHAR(64),
    confidence DOUBLE PRECISION,
    summary TEXT,
    before_profile JSONB,
    suggestion JSONB,
    update_payload JSONB,
    result_profile JSONB,
    changed_fields JSONB,
    reasons JSONB,
    warnings JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_project_theme_profile_application_log_project
    ON project_theme_profile_application_log(project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_theme_profile_application_log_workspace_project_code
    ON project_theme_profile_application_log(workspace_id, project_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_theme_profile_application_log_source
    ON project_theme_profile_application_log(source);
