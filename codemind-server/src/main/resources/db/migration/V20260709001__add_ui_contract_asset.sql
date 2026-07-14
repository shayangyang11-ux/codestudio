CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
VALUES (
           'REQUIREMENT_UI_CONTRACT',
           '交互式 UI 契约生成模板',
           'REQUIREMENT',
           'GENERATE_UI_CONTRACT',
           'UI_CONTRACT',
           '基于页面规范、业务流程、数据模型和接口契约生成可 Mock 预览、可被开发 Agent 复用的 UI 契约。',
           'ACTIVE',
           'system',
           'system'
       )
    ON CONFLICT (template_key)
DO UPDATE SET
    template_name = EXCLUDED.template_name,
           task_type = EXCLUDED.task_type,
           artifact_type = EXCLUDED.artifact_type,
           description = EXCLUDED.description,
           status = 'ACTIVE',
           updated_by = 'system',
           updated_at = CURRENT_TIMESTAMP;

WITH tpl AS (
    SELECT id
    FROM prompt_template
    WHERE template_key = 'REQUIREMENT_UI_CONTRACT'
),
     seed AS (
         SELECT
             tpl.id AS template_id,
             1 AS version_no,
             'v1.0.0' AS version_label,
             (
                 '你是 CodeMind Studio 的企业级 UI Contract Agent。' || E'\n' ||
            '你的任务不是生成一次性原型，而是生成可被需求阶段 Mock Preview 运行、并可被后续开发 Agent 生成真实前端页面的 UI 契约。' || E'\n' ||
            '必须保证页面结构、字段、布局、交互流程、校验规则、按钮动作和 API 绑定点稳定、可追溯、可开发实现。'
                 ) AS system_prompt,
             (
                 '请为当前模块生成 UI_CONTRACT 资产。' || E'\n\n' ||
            '【核心原则】' || E'\n' ||
            '1. 生成的是 UI 契约，不是一次性 Mock 页面。' || E'\n' ||
            '2. 需求阶段使用 Mock Runtime 运行，开发阶段开发 Agent 必须基于同一份 UI_CONTRACT 生成真实页面。' || E'\n' ||
            '3. 页面设计、组件结构、字段、按钮、校验和交互流程不得在后续开发阶段随意改变。' || E'\n' ||
            '4. 所有数据源必须同时给出 mockBinding 和 realApiBinding。' || E'\n' ||
            '5. 所有提交、查询、编辑、删除动作必须同时给出 mockAction 和 apiBinding。' || E'\n\n' ||
            '【任务信息】' || E'\n' ||
            '- task_id: {{task_id}}' || E'\n' ||
            '- project_id: {{project_id}}' || E'\n' ||
            '- project_code: {{project_code}}' || E'\n' ||
            '- module_id: {{module_id}}' || E'\n' ||
            '- task_type: {{task_type}}' || E'\n' ||
            '- artifact_type: UI_CONTRACT' || E'\n\n' ||
            '【当前模块】' || E'\n' ||
            '{{current_module_json}}' || E'\n\n' ||
            '【上下文包】' || E'\n' ||
            '{{context_package_json}}' || E'\n\n' ||
            '【用户补充需求】' || E'\n' ||
            '{{requirement_text}}' || E'\n\n' ||
            '【输出 JSON 要求】' || E'\n' ||
            '必须输出完整 JSON 对象，不要 Markdown 代码块，不要解释性前后缀。' || E'\n' ||
            '顶层字段必须包含：schemaVersion、artifactType、module、routes、pages、mockData、apiBindings、developmentRules、analysisSummary。'
                 ) AS user_prompt_template,
             jsonb_build_object(
                     'type', 'object',
                     'required', jsonb_build_array(
                             'schemaVersion',
                             'artifactType',
                             'module',
                             'routes',
                             'pages',
                             'mockData',
                             'apiBindings',
                             'developmentRules'
                                 ),
                     'properties', jsonb_build_object(
                             'schemaVersion', jsonb_build_object('type', 'string'),
                             'artifactType', jsonb_build_object('const', 'UI_CONTRACT'),
                             'module', jsonb_build_object('type', 'object'),
                             'routes', jsonb_build_object('type', 'array'),
                             'pages', jsonb_build_object('type', 'array'),
                             'mockData', jsonb_build_object('type', 'object'),
                             'apiBindings', jsonb_build_object('type', 'array'),
                             'developmentRules', jsonb_build_object('type', 'object'),
                             'analysisSummary', jsonb_build_object('type', 'string')
                                   )
             ) AS output_schema
         FROM tpl
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
    'Initial production UI Contract template version.',
    'system'
FROM seed
    ON CONFLICT (template_id, version_label)
DO UPDATE SET
    system_prompt = EXCLUDED.system_prompt,
           user_prompt_template = EXCLUDED.user_prompt_template,
           output_schema = EXCLUDED.output_schema,
           checksum = EXCLUDED.checksum,
           status = 'ACTIVE',
           is_current = true,
           updated_at = CURRENT_TIMESTAMP;

ALTER TABLE agent_task
DROP CONSTRAINT IF EXISTS ck_agent_task_requirement_prompt_template_present;

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
                              'GENERATE_UI_CONTRACT',
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

ALTER TABLE artifact_version
DROP CONSTRAINT IF EXISTS ck_artifact_requirement_prompt_template_present;

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
                                  'UI_CONTRACT',
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