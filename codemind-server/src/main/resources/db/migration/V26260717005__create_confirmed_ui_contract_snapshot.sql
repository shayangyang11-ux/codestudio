CREATE TABLE IF NOT EXISTS confirmed_ui_contract_snapshot (
                                                              id UUID PRIMARY KEY,
                                                              project_id UUID NOT NULL,
                                                              module_id UUID NOT NULL,
                                                              artifact_id UUID NOT NULL,
                                                              artifact_type VARCHAR(64) NOT NULL DEFAULT 'UI_CONTRACT',
    artifact_version_no INTEGER NOT NULL,
    page_id VARCHAR(128),
    page_key VARCHAR(255),
    page_name VARCHAR(512),
    ui_contract JSONB NOT NULL,
    theme_profile_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    confirmation_note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_confirmed_ui_contract_snapshot_project_module
    ON confirmed_ui_contract_snapshot(project_id, module_id, confirmed_at DESC);

CREATE INDEX IF NOT EXISTS idx_confirmed_ui_contract_snapshot_artifact
    ON confirmed_ui_contract_snapshot(artifact_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_confirmed_ui_contract_snapshot_current
    ON confirmed_ui_contract_snapshot(project_id, module_id)
    WHERE is_current = TRUE;

COMMENT ON TABLE confirmed_ui_contract_snapshot IS
'用户确认后的 UI_CONTRACT 页面蓝图快照，供后续 Development Agent 使用。';

COMMENT ON COLUMN confirmed_ui_contract_snapshot.ui_contract IS
'确认时的完整 UI_CONTRACT JSON。';

COMMENT ON COLUMN confirmed_ui_contract_snapshot.theme_profile_snapshot IS
'确认时的项目风格配置快照，保证后续真实页面视觉一致。';
