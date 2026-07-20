CREATE TABLE IF NOT EXISTS confirmed_development_plan_snapshot (
                                                                   id UUID PRIMARY KEY,
                                                                   project_id UUID NOT NULL,
                                                                   module_id UUID NOT NULL,
                                                                   plan_checksum VARCHAR(128) NOT NULL,
    plan_status VARCHAR(64) NOT NULL DEFAULT 'CONFIRMED',
    plan_json JSONB NOT NULL,
    readiness_snapshot JSONB,
    handoff_context_snapshot JSONB,
    source_artifact_versions JSONB,
    confirmed_ui_contract_snapshot_id UUID,
    confirmation_note TEXT,
    status VARCHAR(64) NOT NULL DEFAULT 'CONFIRMED',
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    confirmed_by VARCHAR(128),
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_confirmed_development_plan_snapshot_project_module
    ON confirmed_development_plan_snapshot(project_id, module_id, confirmed_at DESC);

CREATE INDEX IF NOT EXISTS idx_confirmed_development_plan_snapshot_checksum
    ON confirmed_development_plan_snapshot(project_id, module_id, plan_checksum);

CREATE UNIQUE INDEX IF NOT EXISTS ux_confirmed_development_plan_snapshot_current
    ON confirmed_development_plan_snapshot(project_id, module_id)
    WHERE is_current = TRUE;

COMMENT ON TABLE confirmed_development_plan_snapshot IS
    'Confirmed Development Plan snapshots used as the locked input for Development Agent code generation.';

COMMENT ON COLUMN confirmed_development_plan_snapshot.plan_json IS
    'The exact Development Plan confirmed by the user.';

COMMENT ON COLUMN confirmed_development_plan_snapshot.handoff_context_snapshot IS
    'Development handoff context captured when the plan was confirmed.';

COMMENT ON COLUMN confirmed_development_plan_snapshot.source_artifact_versions IS
    'Source artifact ids/version numbers used by the confirmed plan.';
