ALTER TABLE agent_task
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archived_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS archive_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_agent_task_project_active_updated
    ON agent_task (project_id, archived_at, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_task_project_module_active_updated
    ON agent_task (project_id, module_id, archived_at, updated_at DESC);