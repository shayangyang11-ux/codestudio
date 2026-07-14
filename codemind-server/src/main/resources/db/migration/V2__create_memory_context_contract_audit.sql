CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS project_memory (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    memory_type VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    summary TEXT,
    content JSONB,
    source_artifact_id UUID,
    source_task_id UUID,
    confidence NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    is_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_memory_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_memory_artifact
    FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL,

    CONSTRAINT fk_project_memory_task
    FOREIGN KEY (source_task_id) REFERENCES agent_task(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS module_memory (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    responsibility TEXT,
    summary TEXT,
    key_rules JSONB,
    provided_apis JSONB,
    consumed_apis JSONB,
    owned_entities JSONB,
    risk_points JSONB,
    source_artifact_id UUID,
    source_task_id UUID,
    confidence NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    is_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_module_memory_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_module_memory_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE CASCADE,

    CONSTRAINT fk_module_memory_artifact
    FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL,

    CONSTRAINT fk_module_memory_task
    FOREIGN KEY (source_task_id) REFERENCES agent_task(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS memory_chunk (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    module_id UUID,
    source_type VARCHAR(64) NOT NULL,
    source_id UUID,
    source_artifact_id UUID,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    chunk_text TEXT NOT NULL,
    chunk_summary TEXT,
    embedding vector(1536),
    embedding_model VARCHAR(128),
    embedding_dim INTEGER,
    tags JSONB,
    metadata JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_memory_chunk_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_memory_chunk_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_memory_chunk_artifact
    FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS contract_registry (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    contract_code VARCHAR(128) NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    contract_type VARCHAR(64) NOT NULL,
    provider_module_id UUID,
    consumer_module_id UUID,
    version_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    content JSONB NOT NULL,
    source_artifact_id UUID,
    created_by_task_id UUID,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_contract_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_contract_provider_module
    FOREIGN KEY (provider_module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_contract_consumer_module
    FOREIGN KEY (consumer_module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_contract_source_artifact
    FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL,

    CONSTRAINT fk_contract_created_task
    FOREIGN KEY (created_by_task_id) REFERENCES agent_task(id) ON DELETE SET NULL,

    CONSTRAINT uk_contract_version
    UNIQUE (project_id, contract_code, version_no)
    );

CREATE TABLE IF NOT EXISTS dependency_edge (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    source_module_id UUID,
    target_module_id UUID,
    edge_type VARCHAR(64) NOT NULL,
    relation_name VARCHAR(255),
    trigger_condition TEXT,
    contract_id UUID,
    weight INTEGER NOT NULL DEFAULT 50,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source_artifact_id UUID,
    created_by_task_id UUID,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dependency_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_dependency_source_module
    FOREIGN KEY (source_module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_dependency_target_module
    FOREIGN KEY (target_module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_dependency_contract
    FOREIGN KEY (contract_id) REFERENCES contract_registry(id) ON DELETE SET NULL,

    CONSTRAINT fk_dependency_source_artifact
    FOREIGN KEY (source_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL,

    CONSTRAINT fk_dependency_created_task
    FOREIGN KEY (created_by_task_id) REFERENCES agent_task(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS context_package_log (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL,
    project_id UUID NOT NULL,
    module_id UUID,
    task_type VARCHAR(64) NOT NULL,
    context_package JSONB NOT NULL,
    included_artifact_ids JSONB,
    included_memory_ids JSONB,
    included_chunk_ids JSONB,
    included_contract_ids JSONB,
    included_dependency_ids JSONB,
    retrieval_query TEXT,
    token_budget INTEGER,
    estimated_input_tokens INTEGER,
    actual_input_tokens INTEGER,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_context_log_task
    FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE CASCADE,

    CONSTRAINT fk_context_log_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_context_log_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS agent_execution_log (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL,
    project_id UUID NOT NULL,
    module_id UUID,
    graph_name VARCHAR(128),
    node_name VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    model_name VARCHAR(128),
    input_summary TEXT,
    output_summary TEXT,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    cost_estimate NUMERIC(12, 6),
    error_code VARCHAR(128),
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agent_exec_task
    FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE CASCADE,

    CONSTRAINT fk_agent_exec_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_agent_exec_module
    FOREIGN KEY (module_id) REFERENCES project_module(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS audit_log (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    actor_id VARCHAR(128),
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    before_json JSONB,
    after_json JSONB,
    ip_address VARCHAR(64),
    user_agent TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_project_memory_project_type_status
    ON project_memory(project_id, memory_type, status);

CREATE INDEX IF NOT EXISTS idx_module_memory_project_module_status
    ON module_memory(project_id, module_id, status);

CREATE INDEX IF NOT EXISTS idx_memory_chunk_project_module_status
    ON memory_chunk(project_id, module_id, status);

CREATE INDEX IF NOT EXISTS idx_memory_chunk_source
    ON memory_chunk(source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_contract_project_type_status
    ON contract_registry(project_id, contract_type, status);

CREATE INDEX IF NOT EXISTS idx_contract_provider_consumer
    ON contract_registry(provider_module_id, consumer_module_id);

CREATE INDEX IF NOT EXISTS idx_dependency_source
    ON dependency_edge(project_id, source_module_id, status);

CREATE INDEX IF NOT EXISTS idx_dependency_target
    ON dependency_edge(project_id, target_module_id, status);

CREATE INDEX IF NOT EXISTS idx_context_log_task
    ON context_package_log(task_id);

CREATE INDEX IF NOT EXISTS idx_context_log_project_module
    ON context_package_log(project_id, module_id, task_type);

CREATE INDEX IF NOT EXISTS idx_agent_execution_task
    ON agent_execution_log(task_id);

CREATE INDEX IF NOT EXISTS idx_agent_execution_project
    ON agent_execution_log(project_id, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_project_action
    ON audit_log(project_id, action, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_resource
    ON audit_log(resource_type, resource_id);

CREATE INDEX IF NOT EXISTS idx_memory_chunk_embedding_hnsw
    ON memory_chunk
    USING hnsw (embedding vector_cosine_ops);