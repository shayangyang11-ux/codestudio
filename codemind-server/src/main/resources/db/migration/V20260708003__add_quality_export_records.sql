CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS artifact_quality_check_record (
                                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID,
    check_scope VARCHAR(32) NOT NULL,
    score INTEGER NOT NULL,
    asset_count INTEGER NOT NULL DEFAULT 0,
    blocker_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    pass_count INTEGER NOT NULL DEFAULT 0,
    export_decision VARCHAR(64) NOT NULL,
    forced_export BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(64) NOT NULL DEFAULT 'MANUAL_CHECK',
    result_json JSONB NOT NULL,
    checked_by VARCHAR(128),
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quality_check_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_quality_check_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS artifact_export_record (
                                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID,
    quality_check_id UUID,
    export_scope VARCHAR(32) NOT NULL,
    export_format VARCHAR(32) NOT NULL,
    export_status VARCHAR(32) NOT NULL,
    file_name VARCHAR(512),
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    forced_export BOOLEAN NOT NULL DEFAULT FALSE,
    quality_score INTEGER,
    blocker_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    export_decision VARCHAR(64),
    manifest_json JSONB,
    failure_reason TEXT,
    exported_by VARCHAR(128),
    exported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_export_record_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_export_record_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_export_quality_check
    FOREIGN KEY (quality_check_id) REFERENCES artifact_quality_check_record(id) ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_quality_check_project_module_time
    ON artifact_quality_check_record(project_id, module_id, checked_at DESC);

CREATE INDEX IF NOT EXISTS idx_quality_check_scope_time
    ON artifact_quality_check_record(check_scope, checked_at DESC);

CREATE INDEX IF NOT EXISTS idx_export_record_project_module_time
    ON artifact_export_record(project_id, module_id, exported_at DESC);

CREATE INDEX IF NOT EXISTS idx_export_record_scope_format_time
    ON artifact_export_record(export_scope, export_format, exported_at DESC);

CREATE INDEX IF NOT EXISTS idx_export_record_quality_check
    ON artifact_export_record(quality_check_id);