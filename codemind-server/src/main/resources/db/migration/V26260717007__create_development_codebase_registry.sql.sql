CREATE TABLE IF NOT EXISTS development_codebase (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    project_code VARCHAR(128) NOT NULL,
    target_stack VARCHAR(64) NOT NULL,
    storage_type VARCHAR(64) NOT NULL DEFAULT 'LOCAL_FILESYSTEM',
    storage_root_key VARCHAR(128) NOT NULL DEFAULT 'WORKSPACE_GENERATED_ROOT',
    relative_root_path VARCHAR(1024) NOT NULL,
    manifest_relative_path VARCHAR(1024) NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    last_generated_at TIMESTAMP,
    last_materialized_at TIMESTAMP,
    last_runtime_status VARCHAR(64),
    last_frontend_port INTEGER,
    last_backend_port INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_development_codebase_project_stack UNIQUE (workspace_id, project_id, target_stack)
);

CREATE INDEX IF NOT EXISTS idx_development_codebase_workspace_project
    ON development_codebase (workspace_id, project_id);

CREATE TABLE IF NOT EXISTS development_codebase_node_contribution (
    id UUID PRIMARY KEY,
    codebase_id UUID NOT NULL REFERENCES development_codebase(id) ON DELETE CASCADE,
    module_id UUID NOT NULL,
    node_type VARCHAR(64),
    node_name VARCHAR(255),
    preview_route VARCHAR(512),
    file_count INTEGER NOT NULL DEFAULT 0,
    source_plan_checksum VARCHAR(128),
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    last_generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_development_codebase_node UNIQUE (codebase_id, module_id)
);

CREATE INDEX IF NOT EXISTS idx_development_codebase_node_module
    ON development_codebase_node_contribution (module_id);
