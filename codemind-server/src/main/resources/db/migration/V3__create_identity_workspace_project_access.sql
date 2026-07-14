CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS user_account (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    avatar_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_account_username UNIQUE (username),
    CONSTRAINT uk_user_account_email UNIQUE (email)
    );

CREATE TABLE IF NOT EXISTS workspace (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_code VARCHAR(128) NOT NULL,
    workspace_name VARCHAR(255) NOT NULL,
    workspace_type VARCHAR(32) NOT NULL,
    owner_user_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    settings JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workspace_code UNIQUE (workspace_code),
    CONSTRAINT fk_workspace_owner
    FOREIGN KEY (owner_user_id) REFERENCES user_account(id) ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS workspace_member (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workspace_member_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,

    CONSTRAINT fk_workspace_member_user
    FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,

    CONSTRAINT uk_workspace_member_user
    UNIQUE (workspace_id, user_id)
    );

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS workspace_id UUID;

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS owner_user_id UUID;

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_project_workspace'
    ) THEN
ALTER TABLE project
    ADD CONSTRAINT fk_project_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE;
END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_project_owner_user'
    ) THEN
ALTER TABLE project
    ADD CONSTRAINT fk_project_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES user_account(id) ON DELETE SET NULL;
END IF;
END $$;

CREATE TABLE IF NOT EXISTS project_member (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    project_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_member_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_member_user
    FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,

    CONSTRAINT uk_project_member_user
    UNIQUE (project_id, user_id)
    );

CREATE TABLE IF NOT EXISTS project_workspace_state (
                                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    selected_module_id UUID,
    selected_artifact_type VARCHAR(64),
    selected_artifact_id UUID,
    active_agent_tab VARCHAR(32) DEFAULT 'requirement',
    ui_state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_workspace_state_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_workspace_state_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_workspace_state_user
    FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,

    CONSTRAINT fk_project_workspace_state_module
    FOREIGN KEY (selected_module_id) REFERENCES project_module(id) ON DELETE SET NULL,

    CONSTRAINT fk_project_workspace_state_artifact
    FOREIGN KEY (selected_artifact_id) REFERENCES artifact_version(id) ON DELETE SET NULL,

    CONSTRAINT uk_project_workspace_state_user_project
    UNIQUE (user_id, project_id)
    );

CREATE TABLE IF NOT EXISTS auth_refresh_token (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    device_info TEXT,
    ip_address VARCHAR(64),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_auth_refresh_token_user
    FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_workspace_owner
    ON workspace(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_workspace_member_user
    ON workspace_member(user_id, status);

CREATE INDEX IF NOT EXISTS idx_project_workspace
    ON project(workspace_id, status);

CREATE INDEX IF NOT EXISTS idx_project_owner_user
    ON project(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_project_member_user
    ON project_member(user_id, status);

CREATE INDEX IF NOT EXISTS idx_project_workspace_state_user
    ON project_workspace_state(user_id, project_id);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user
    ON auth_refresh_token(user_id, expires_at);

INSERT INTO user_account (
    id,
    username,
    email,
    password_hash,
    display_name,
    status
)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'local_user',
           'local_user@codemind.local',
           '$2a$10$local.development.password.hash.placeholder',
           '本地开发用户',
           'ACTIVE'
       )
    ON CONFLICT (username)
DO UPDATE SET
    display_name = EXCLUDED.display_name,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;

INSERT INTO workspace (
    id,
    workspace_code,
    workspace_name,
    workspace_type,
    owner_user_id,
    status,
    description
)
VALUES (
           '00000000-0000-0000-0000-000000000101',
           'personal-local-user',
           '本地开发个人空间',
           'PERSONAL',
           '00000000-0000-0000-0000-000000000001',
           'ACTIVE',
           '本地开发阶段用于承载个人项目的默认空间'
       )
    ON CONFLICT (workspace_code)
DO UPDATE SET
    workspace_name = EXCLUDED.workspace_name,
           workspace_type = EXCLUDED.workspace_type,
           owner_user_id = EXCLUDED.owner_user_id,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;

INSERT INTO workspace_member (
    workspace_id,
    user_id,
    member_role,
    status
)
VALUES (
           '00000000-0000-0000-0000-000000000101',
           '00000000-0000-0000-0000-000000000001',
           'OWNER',
           'ACTIVE'
       )
    ON CONFLICT (workspace_id, user_id)
DO UPDATE SET
    member_role = EXCLUDED.member_role,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;

INSERT INTO workspace (
    id,
    workspace_code,
    workspace_name,
    workspace_type,
    owner_user_id,
    status,
    description
)
VALUES (
           '00000000-0000-0000-0000-000000000102',
           'team-demo',
           '演示团队空间',
           'TEAM',
           '00000000-0000-0000-0000-000000000001',
           'ACTIVE',
           '本地开发阶段用于验证团队项目协同的默认团队空间'
       )
    ON CONFLICT (workspace_code)
DO UPDATE SET
    workspace_name = EXCLUDED.workspace_name,
           workspace_type = EXCLUDED.workspace_type,
           owner_user_id = EXCLUDED.owner_user_id,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;

INSERT INTO workspace_member (
    workspace_id,
    user_id,
    member_role,
    status
)
VALUES (
           '00000000-0000-0000-0000-000000000102',
           '00000000-0000-0000-0000-000000000001',
           'OWNER',
           'ACTIVE'
       )
    ON CONFLICT (workspace_id, user_id)
DO UPDATE SET
    member_role = EXCLUDED.member_role,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;

UPDATE project
SET
    workspace_id = '00000000-0000-0000-0000-000000000101',
    owner_user_id = '00000000-0000-0000-0000-000000000001',
    tenant_id = 'personal-local-user',
    updated_at = CURRENT_TIMESTAMP
WHERE project_code = 'P001'
  AND workspace_id IS NULL;

INSERT INTO project_member (
    project_id,
    user_id,
    project_role,
    status
)
SELECT
    p.id,
    '00000000-0000-0000-0000-000000000001',
    'OWNER',
    'ACTIVE'
FROM project p
WHERE p.project_code = 'P001'
    ON CONFLICT (project_id, user_id)
DO UPDATE SET
    project_role = EXCLUDED.project_role,
           status = EXCLUDED.status,
           updated_at = CURRENT_TIMESTAMP;