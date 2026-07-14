CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS prompt_template (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(128) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_category VARCHAR(64) NOT NULL DEFAULT 'REQUIREMENT',
    task_type VARCHAR(64) NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_template_key UNIQUE (template_key),
    CONSTRAINT uk_prompt_template_task_type UNIQUE (task_type)
    );

CREATE TABLE IF NOT EXISTS prompt_template_version (
                                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL,
    version_no INTEGER NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    output_schema JSONB,
    checksum VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deprecated_at TIMESTAMP,
    change_summary TEXT,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prompt_template_version_template
    FOREIGN KEY (template_id) REFERENCES prompt_template(id) ON DELETE CASCADE,

    CONSTRAINT uk_prompt_template_version_label
    UNIQUE (template_id, version_label)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_prompt_template_current_version
    ON prompt_template_version(template_id)
    WHERE is_current = true;

ALTER TABLE agent_task
    ADD COLUMN IF NOT EXISTS prompt_template_id UUID,
    ADD COLUMN IF NOT EXISTS prompt_template_version_id UUID,
    ADD COLUMN IF NOT EXISTS prompt_template_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS prompt_template_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS prompt_template_checksum VARCHAR(128);

ALTER TABLE artifact_version
    ADD COLUMN IF NOT EXISTS prompt_template_id UUID,
    ADD COLUMN IF NOT EXISTS prompt_template_version_id UUID,
    ADD COLUMN IF NOT EXISTS prompt_template_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS prompt_template_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS prompt_template_checksum VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_agent_task_prompt_template
    ON agent_task(prompt_template_key, prompt_template_version);

CREATE INDEX IF NOT EXISTS idx_artifact_version_prompt_template
    ON artifact_version(prompt_template_key, prompt_template_version);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_task_prompt_template'
    ) THEN
ALTER TABLE agent_task
    ADD CONSTRAINT fk_agent_task_prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE SET NULL;
END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_task_prompt_template_version'
    ) THEN
ALTER TABLE agent_task
    ADD CONSTRAINT fk_agent_task_prompt_template_version
        FOREIGN KEY (prompt_template_version_id) REFERENCES prompt_template_version(id) ON DELETE SET NULL;
END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_artifact_prompt_template'
    ) THEN
ALTER TABLE artifact_version
    ADD CONSTRAINT fk_artifact_prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE SET NULL;
END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_artifact_prompt_template_version'
    ) THEN
ALTER TABLE artifact_version
    ADD CONSTRAINT fk_artifact_prompt_template_version
        FOREIGN KEY (prompt_template_version_id) REFERENCES prompt_template_version(id) ON DELETE SET NULL;
END IF;
END $$;

WITH template_seed AS (
    SELECT *
    FROM (
             VALUES
                 ('REQUIREMENT_MODULE_TREE', '模块树生成模板', 'GENERATE_MODULE_TREE', 'MODULE_TREE', '根据项目需求生成模块树、模块边界和模块层级。'),
                 ('REQUIREMENT_MODULE_PRD', '模块 PRD 生成模板', 'GENERATE_MODULE_PRD', 'MODULE_PRD', '生成结构化模块产品需求说明。'),
                 ('REQUIREMENT_BUSINESS_FLOW', '业务流程生成模板', 'GENERATE_BUSINESS_FLOW', 'BUSINESS_FLOW', '生成主流程、分支流程、异常流程和状态流转。'),
                 ('REQUIREMENT_PAGE_SPEC', '页面规范生成模板', 'GENERATE_PAGE_SPEC', 'PAGE_SPEC', '生成页面结构、字段、交互、校验和接口绑定。'),
                 ('REQUIREMENT_DATA_MODEL', '数据模型生成模板', 'GENERATE_DATA_MODEL', 'DATA_MODEL', '生成实体、字段、关系、索引、约束和迁移建议。'),
                 ('REQUIREMENT_API_CONTRACT', '接口契约生成模板', 'GENERATE_API_CONTRACT', 'API_CONTRACT', '生成接口清单、请求响应、错误码、安全和测试关注点。'),
                 ('REQUIREMENT_TEST_SPEC', '测试规格生成模板', 'GENERATE_TEST_SPEC', 'TEST_SPEC', '生成测试用例、验收标准、测试数据和风险场景。'),
                 ('REQUIREMENT_DEV_HANDOFF_PACKAGE', '开发交接包生成模板', 'GENERATE_DEV_HANDOFF_PACKAGE', 'DEV_HANDOFF_PACKAGE', '汇总需求、流程、页面、数据、接口和测试内容，形成开发交接包。')
         ) AS t(template_key, template_name, task_type, artifact_type, description)
)
INSERT INTO prompt_template (
    template_key,
    template_name,
    template_category,
    task_type,
    artifact_type,
    description,
    status,
    created_by,
    updated_by
)
SELECT
    template_key,
    template_name,
    'REQUIREMENT',
    task_type,
    artifact_type,
    description,
    'ACTIVE',
    'system',
    'system'
FROM template_seed
    ON CONFLICT (template_key)
DO UPDATE SET
    template_name = EXCLUDED.template_name,
           task_type = EXCLUDED.task_type,
           artifact_type = EXCLUDED.artifact_type,
           description = EXCLUDED.description,
           status = 'ACTIVE',
           updated_by = 'system',
           updated_at = CURRENT_TIMESTAMP;

WITH version_seed AS (
    SELECT
        pt.id AS template_id,
        pt.template_key,
        pt.template_name,
        pt.task_type,
        pt.artifact_type,
        1 AS version_no,
        'v1.0.0' AS version_label,
        (
            '你是 CodeMind Studio 的企业级需求分析 Agent。' || E'\n' ||
            '你必须严格基于输入上下文生成结构化、可交付、可追溯的需求资产。' || E'\n' ||
            '输出必须服务于后续开发、测试、评审、验收和交付，不允许编造与上下文冲突的内容。'
            ) AS system_prompt,
        (
            '请为当前任务生成企业级需求资产。' || E'\n\n' ||
            '【任务信息】' || E'\n' ||
            '- task_id: {{task_id}}' || E'\n' ||
            '- project_id: {{project_id}}' || E'\n' ||
            '- project_code: {{project_code}}' || E'\n' ||
            '- module_id: {{module_id}}' || E'\n' ||
            '- task_type: {{task_type}}' || E'\n' ||
            '- artifact_type: ' || pt.artifact_type || E'\n\n' ||
            '【当前模板】' || E'\n' ||
            '- template_key: ' || pt.template_key || E'\n' ||
            '- template_version: v1.0.0' || E'\n\n' ||
            '【用户补充需求】' || E'\n' ||
            '{{requirement_text}}' || E'\n\n' ||
            '【当前模块】' || E'\n' ||
            '{{current_module_json}}' || E'\n\n' ||
            '【上下文包】' || E'\n' ||
            '{{context_package_json}}' || E'\n\n' ||
            '【输出要求】' || E'\n' ||
            '1. 必须输出完整、可解析的 JSON 对象。' || E'\n' ||
            '2. 不要输出 Markdown 代码块。' || E'\n' ||
            '3. 不要输出解释性前后缀。' || E'\n' ||
            '4. 内容必须面向真实开发、测试、评审和验收。' || E'\n' ||
            '5. 当上下文不足时，应生成保守但可落地的内容，并在 analysis_summary 或 development_notes 中说明假设。'
            ) AS user_prompt_template,
        jsonb_build_object(
                'type', 'object',
                'description', 'Structured requirement artifact output for ' || pt.artifact_type
        ) AS output_schema
    FROM prompt_template pt
    WHERE pt.template_category = 'REQUIREMENT'
)
INSERT INTO prompt_template_version (
    template_id,
    version_no,
    version_label,
    system_prompt,
    user_prompt_template,
    output_schema,
    checksum,
    status,
    is_current,
    change_summary,
    created_by
)
SELECT
    template_id,
    version_no,
    version_label,
    system_prompt,
    user_prompt_template,
    output_schema,
    encode(
            digest(
                    system_prompt || E'\n---USER---\n' || user_prompt_template || E'\n---SCHEMA---\n' || output_schema::text,
                    'sha256'
            ),
            'hex'
    ),
    'ACTIVE',
    true,
    'Initial production template version.',
    'system'
FROM version_seed
    ON CONFLICT (template_id, version_label)
DO UPDATE SET
    system_prompt = EXCLUDED.system_prompt,
           user_prompt_template = EXCLUDED.user_prompt_template,
           output_schema = EXCLUDED.output_schema,
           checksum = EXCLUDED.checksum,
           status = 'ACTIVE',
           is_current = true,
           updated_at = CURRENT_TIMESTAMP;

UPDATE prompt_template_version v
SET is_current = false
WHERE EXISTS (
    SELECT 1
    FROM prompt_template_version newer
    WHERE newer.template_id = v.template_id
      AND newer.version_no > v.version_no
      AND newer.status = 'ACTIVE'
)
  AND v.version_label = 'v1.0.0';

UPDATE agent_task t
SET
    prompt_template_id = pt.id,
    prompt_template_version_id = pv.id,
    prompt_template_key = pt.template_key,
    prompt_template_version = pv.version_label,
    prompt_template_checksum = pv.checksum
    FROM prompt_template pt
JOIN prompt_template_version pv
ON pv.template_id = pt.id
    AND pv.is_current = true
    AND pv.status = 'ACTIVE'
WHERE t.task_type = pt.task_type
  AND t.prompt_template_id IS NULL;

UPDATE artifact_version a
SET
    prompt_template_id = pt.id,
    prompt_template_version_id = pv.id,
    prompt_template_key = pt.template_key,
    prompt_template_version = pv.version_label,
    prompt_template_checksum = pv.checksum
    FROM prompt_template pt
JOIN prompt_template_version pv
ON pv.template_id = pt.id
    AND pv.is_current = true
    AND pv.status = 'ACTIVE'
WHERE a.artifact_type = pt.artifact_type
  AND a.prompt_template_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_task_requirement_prompt_template_present'
    ) THEN
ALTER TABLE agent_task
    ADD CONSTRAINT ck_agent_task_requirement_prompt_template_present
        CHECK (
            task_type NOT IN (
                              'GENERATE_MODULE_TREE',
                              'GENERATE_MODULE_PRD',
                              'GENERATE_BUSINESS_FLOW',
                              'GENERATE_PAGE_SPEC',
                              'GENERATE_DATA_MODEL',
                              'GENERATE_API_CONTRACT',
                              'GENERATE_TEST_SPEC',
                              'GENERATE_DEV_HANDOFF_PACKAGE'
                )
                OR (
                prompt_template_id IS NOT NULL
                    AND prompt_template_version_id IS NOT NULL
                    AND prompt_template_key IS NOT NULL
                    AND prompt_template_version IS NOT NULL
                    AND prompt_template_checksum IS NOT NULL
                )
            );
END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_artifact_requirement_prompt_template_present'
    ) THEN
ALTER TABLE artifact_version
    ADD CONSTRAINT ck_artifact_requirement_prompt_template_present
        CHECK (
            artifact_type NOT IN (
                                  'MODULE_TREE',
                                  'MODULE_PRD',
                                  'BUSINESS_FLOW',
                                  'PAGE_SPEC',
                                  'DATA_MODEL',
                                  'API_CONTRACT',
                                  'TEST_SPEC',
                                  'DEV_HANDOFF_PACKAGE'
                )
                OR (
                prompt_template_id IS NOT NULL
                    AND prompt_template_version_id IS NOT NULL
                    AND prompt_template_key IS NOT NULL
                    AND prompt_template_version IS NOT NULL
                    AND prompt_template_checksum IS NOT NULL
                )
            );
END IF;
END $$;