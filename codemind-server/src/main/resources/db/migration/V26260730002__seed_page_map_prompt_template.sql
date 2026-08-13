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
    'REQUIREMENT_PAGE_MAP',
    '页面地图 PAGE_MAP 生成模板',
    'REQUIREMENT',
    'GENERATE_PAGE_MAP',
    'PAGE_MAP',
    '生成 MODULE 级页面地图：页面清单、pageKey、route、页面职责、入口出口、页面跳转和跨模块导航引用。',
    'ACTIVE',
    'system',
    'system'
)
ON CONFLICT (template_key)
DO UPDATE SET
    template_name = EXCLUDED.template_name,
    template_category = EXCLUDED.template_category,
    task_type = EXCLUDED.task_type,
    artifact_type = EXCLUDED.artifact_type,
    description = EXCLUDED.description,
    status = 'ACTIVE',
    updated_by = 'system',
    updated_at = CURRENT_TIMESTAMP;

DO $$
DECLARE
    v_template_id UUID;
    v_system_prompt TEXT;
    v_user_prompt TEXT;
    v_checksum TEXT;
BEGIN
    SELECT id
      INTO v_template_id
      FROM prompt_template
     WHERE template_key = 'REQUIREMENT_PAGE_MAP';

    v_system_prompt := '你是 CodeMind Studio 的企业级需求分析 Agent，负责生成 MODULE 级 PAGE_MAP 页面地图资产。PAGE_MAP 是模块级页面关系、路由、入口出口和跨模块导航索引，不是单个页面的 PAGE_SPEC，也不是 UI_CONTRACT。';

    v_user_prompt := '请基于当前模块、模块树上下文、业务流程和需求材料，生成 PAGE_MAP 页面地图。\n\n硬性要求：\n1. 只输出 MODULE 级页面地图，不展开单个页面字段、表单校验或 UI 组件细节。\n2. 必须包含页面清单、pageKey、routePath、页面职责、入口、出口、主路径、异常路径。\n3. 必须描述页面之间的跳转关系，包括 sourcePageKey、actionId、targetPageKey、targetRoute、navigationType。\n4. 跨 MODULE 跳转允许保留为轻量引用，但必须标记 targetModuleKey / targetModuleName / navigationStatus。\n5. 若目标页面存在但未生成 UI_CONTRACT，navigationStatus 使用 RESOLVED_PAGE_ONLY，并提供 fallbackBehavior。\n6. 若目标页面不存在，navigationStatus 使用 UNRESOLVED_PAGE，并给出补充页面节点建议。\n7. PAGE_MAP 将作为后续 PAGE 级 UI_CONTRACT 的导航依据，所以 pageKey 和 route 必须稳定、可复用。\n\n用户需求：{{requirement_text}}\n\n当前节点：{{current_module_json}}\n\n上下文：{{context_package_json}}';

    v_checksum := md5(v_system_prompt || E'\n---\n' || v_user_prompt);

    UPDATE prompt_template_version
       SET is_current = FALSE,
           updated_at = CURRENT_TIMESTAMP
     WHERE template_id = v_template_id
       AND is_current = TRUE
       AND version_label <> 'v1';

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
    VALUES (
        v_template_id,
        1,
        'v1',
        v_system_prompt,
        v_user_prompt,
        '{}'::jsonb,
        v_checksum,
        'ACTIVE',
        TRUE,
        'A8.4 PAGE_MAP prompt governance seed.',
        'system'
    )
    ON CONFLICT (template_id, version_label)
    DO UPDATE SET
        system_prompt = EXCLUDED.system_prompt,
        user_prompt_template = EXCLUDED.user_prompt_template,
        output_schema = EXCLUDED.output_schema,
        checksum = EXCLUDED.checksum,
        status = 'ACTIVE',
        is_current = TRUE,
        change_summary = EXCLUDED.change_summary,
        updated_at = CURRENT_TIMESTAMP;
END $$;
