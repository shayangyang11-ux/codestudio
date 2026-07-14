ALTER TABLE agent_task
    ADD COLUMN IF NOT EXISTS queue_name VARCHAR(64) NOT NULL DEFAULT 'requirement-agent',
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS last_error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_agent_task_queue_pick
    ON agent_task (
    queue_name,
    status,
    priority DESC,
    next_run_at,
    created_at
    );

CREATE INDEX IF NOT EXISTS idx_agent_task_lock
    ON agent_task (
    locked_by,
    locked_until
    );

CREATE INDEX IF NOT EXISTS idx_agent_task_active_dedup
    ON agent_task (
    project_id,
    module_id,
    task_type,
    status
    );