CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS project (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    project_code VARCHAR(128) NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    tech_stack JSONB,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_tenant_code UNIQUE (tenant_id, project_code)
    );

CREATE TABLE IF NOT EXISTS project_module (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    parent_id UUID,
    module_key VARCHAR(128) NOT NULL,
    module_name VARCHAR(255) NOT NULL,
    module_type VARCHAR(64) NOT NULL DEFAULT 'BUSINESS_MODULE',
    module_path VARCHAR(1024),
    level_no INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source_artifact_id UUID,
    extra JSONB,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_module_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_module_parent
    FOREIGN KEY (parent_id) REFERENCES project_module(id) ON DELETE CASCADE,

    CONSTRAINT uk_project_module_key
    UNIQUE (project_id, module_key)
    );

CREATE TABLE IF NOT EXISTS agent_task (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    request_payload JSONB,
    response_payload JSONB,
    result_artifact_id UUID,
    error_code VARCHAR(128),
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agent_task_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_agent_task_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS artifact_version (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID,
    task_id UUID,
    artifact_type VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    title VARCHAR(255),
    content JSONB NOT NULL,
    content_markdown TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    checksum VARCHAR(128),
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_artifact_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_artifact_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_artifact_task
    FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE SET NULL,

    CONSTRAINT uk_artifact_version
    UNIQUE (project_id, module_id, artifact_type, version_no)
    );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_project_module_source_artifact'
    ) THEN
ALTER TABLE project_module
    ADD CONSTRAINT fk_project_module_source_artifact
        FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL;
END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_task_result_artifact'
    ) THEN
ALTER TABLE agent_task
    ADD CONSTRAINT fk_agent_task_result_artifact
        FOREIGN KEY (result_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL;
END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_project_status
    ON project(status);

CREATE INDEX IF NOT EXISTS idx_project_module_project_parent
    ON project_module(project_id, parent_id);

CREATE INDEX IF NOT EXISTS idx_project_module_project_status
    ON project_module(project_id, status);

CREATE INDEX IF NOT EXISTS idx_agent_task_project_type_status
    ON agent_task(project_id, task_type, status);

CREATE INDEX IF NOT EXISTS idx_agent_task_module
    ON agent_task(module_id);

CREATE INDEX IF NOT EXISTS idx_artifact_project_module_type
    ON artifact_version(project_id, module_id, artifact_type);

CREATE INDEX IF NOT EXISTS idx_artifact_current
    ON artifact_version(project_id, artifact_type, is_current);