CREATE TABLE IF NOT EXISTS assistant_message (
                                                 id UUID PRIMARY KEY,
                                                 workspace_id UUID NOT NULL,
                                                 project_code VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    intent VARCHAR(64),
    selected_node_id UUID,
    selected_artifact_type VARCHAR(64),
    content JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_assistant_message_workspace_project
    ON assistant_message (workspace_id, project_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assistant_message_selected_node
    ON assistant_message (selected_node_id);

CREATE TABLE IF NOT EXISTS assistant_attachment (
                                                    id UUID PRIMARY KEY,
                                                    workspace_id UUID NOT NULL,
                                                    project_code VARCHAR(64) NOT NULL,
    message_id UUID,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(255),
    file_size BIGINT,
    storage_path TEXT,
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    parsed_text TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assistant_attachment_message
    FOREIGN KEY (message_id)
    REFERENCES assistant_message (id)
    ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_assistant_attachment_workspace_project
    ON assistant_attachment (workspace_id, project_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assistant_attachment_message
    ON assistant_attachment (message_id);