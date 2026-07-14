from __future__ import annotations

import json
from typing import Any

from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.ui_contract import UI_CONTRACT_LLM_JSON_SCHEMA, UiContractArtifact
from app.services.llm_service import LlmService


class UiContractAgent:
    """
    Generate a PAGE-level UI_CONTRACT.

    The contract is intentionally structured, not a one-off markdown prototype:
    - Mock Preview Runtime renders it during requirement review.
    - Downstream development agents must use it as the page blueprint.
    """

    SUPPORTED_COMPONENT_TYPES = {
        "section",
        "toolbar",
        "card",
        "description",
        "steps",
        "tabs",
        "form",
        "table",
        "modal",
        "button",
    }

    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> UiContractArtifact:
        try:
            system_prompt = self.prompt_template_renderer.resolve_system_prompt(
                request=request,
                fallback_system_prompt=self._fallback_system_prompt(),
            )

            user_prompt = self.prompt_template_renderer.render_user_prompt(
                request=request,
                fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
            )

            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="ui_contract_output",
                output_json_schema=UI_CONTRACT_LLM_JSON_SCHEMA,
            )

            parsed = json.loads(self._strip_code_fence(llm_result.text))
            artifact = self._normalize_artifact(parsed, request)

        except Exception as exc:
            artifact = self._fallback_artifact(request, exc)

        artifact.markdown = self._build_markdown(artifact)
        return artifact

    def _fallback_system_prompt(self) -> str:
        return "\n".join(
            [
                "你是 CodeMind Studio 的企业级 UI Contract Agent。",
                "你只为 PAGE 页面节点生成 UI_CONTRACT。",
                "UI_CONTRACT 不是一次性 Mock 页面，而是需求阶段 Mock Preview 和后续开发 Agent 真实前端页面生成共同使用的页面蓝图。",
                "你必须输出可渲染 JSON，不要输出 Markdown，不要输出代码块。",
                "",
                "强制要求：",
                "1. 必须包含 schemaVersion、artifactType、module、routes、pages、mockData、apiBindings、developmentRules、analysisSummary。",
                "2. pages[0].components 必须是当前 Mock Preview Runtime 可识别的组件树。",
                "3. 可用组件 type：section、toolbar、card、description、steps、tabs、form、table、modal、button。",
                "4. form 组件必须包含 fields 和 actions。",
                "5. table 组件必须包含 columns、dataSource.mockBinding、actions 或 rowActions。",
                "6. 所有按钮 action 必须尽量包含 mockAction 和 apiBinding。",
                "7. mockData 不能是空对象，必须能让页面一打开就有可展示内容。",
                "8. developmentRules 必须约束后续开发 Agent 不得擅自改变布局、字段、校验、交互流程和接口绑定。",
                "9. 不要编造过多无关模块；只围绕当前 PAGE 节点生成完整页面。",
            ]
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        current_module = self._extract_current_module(request)
        page_kind = self._infer_page_kind(current_module)
        context_package = self._compact_context_package(request.context_package or {})

        return "\n".join(
            [
                "请为当前 PAGE 节点生成企业级 UI_CONTRACT。",
                "",
                "【页面节点】",
                json.dumps(current_module, ensure_ascii=False, default=str),
                "",
                "【建议页面类型】",
                page_kind,
                "",
                "【用户补充需求】",
                self._get_requirement_text(request) or "无额外补充需求。",
                "",
                "【上游上下文摘要】",
                json.dumps(context_package, ensure_ascii=False, default=str),
                "",
                "【不同页面类型的最低结构】",
                "- LIST_PAGE：查询表单 + 工具栏 + 数据表格 + 分页/行操作 + 空状态/错误状态。",
                "- FORM_PAGE：基础信息表单 + 明细表格或卡片 + 提交确认弹窗 + 底部按钮。",
                "- DETAIL_PAGE：详情摘要 + 描述列表 + tabs + 操作日志/关联明细。",
                "- APPROVAL_PAGE：审批对象详情 + 审批意见表单 + 通过/驳回按钮 + 审批状态步骤条。",
                "- DASHBOARD_PAGE：指标卡片 + 图表占位 + 明细列表。",
                "- SETTINGS_PAGE：配置表单 + 保存/重置 + 权限提示。",
                "",
                "【组件 JSON 要求】",
                "每个组件至少包含 componentId、type、title。",
                "form.fields 每项包含 field、label、type、required、placeholder、validation。",
                "table.columns 每项包含 field、label、type。",
                "modal 需要 modalId、title、body、actions。",
                "actions 每项包含 actionId、type、label、mockAction、apiBinding、successMessage。",
                "",
                "【输出】",
                "只输出 JSON 对象，不要输出 Markdown，不要输出 ```json，不要输出解释文字。",
            ]
        )

    def _compact_context_package(self, context_package: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {}

        for key in ["current_module", "module", "generation_scope"]:
            if isinstance(context_package.get(key), dict):
                result[key] = context_package[key]

        artifacts = context_package.get("artifacts")
        compact_artifacts: dict[str, Any] = {}

        if isinstance(artifacts, dict):
            source_items = artifacts.items()
        elif isinstance(artifacts, list):
            source_items = [
                (str(item.get("artifact_type") or item.get("artifactType") or ""), item)
                for item in artifacts
                if isinstance(item, dict)
            ]
        else:
            source_items = []

        for artifact_type, artifact in source_items:
            if artifact_type not in {
                "MODULE_PRD",
                "BUSINESS_FLOW",
                "PAGE_SPEC",
                "DATA_MODEL",
                "API_CONTRACT",
                "TEST_SPEC",
            }:
                continue

            compact_artifacts[artifact_type] = self._compact_value(artifact, 4500)

        if compact_artifacts:
            result["artifacts"] = compact_artifacts

        request_payload = context_package.get("request_payload")
        if isinstance(request_payload, dict):
            result["request_payload"] = self._compact_value(request_payload, 1200)

        return result

    def _compact_value(self, value: Any, max_chars: int) -> Any:
        text = json.dumps(value or {}, ensure_ascii=False, default=str)

        if len(text) <= max_chars:
            return value

        return {
            "_truncated": True,
            "excerpt": text[:max_chars],
        }

    def _normalize_artifact(
        self,
        value: dict[str, Any],
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        source = dict(value or {})
        fallback = self._fallback_artifact(request, None)

        module = source.get("module")
        if not isinstance(module, dict) or not module:
            module = fallback.module

        routes = self._normalize_routes(source.get("routes"), fallback.routes)
        pages = self._normalize_pages(source.get("pages"), fallback.pages)
        mock_data = self._normalize_mock_data(source.get("mockData"), fallback.mockData)
        api_bindings = self._normalize_api_bindings(source.get("apiBindings"), fallback.apiBindings)
        development_rules = self._normalize_development_rules(
            source.get("developmentRules"),
            fallback.developmentRules,
        )

        return UiContractArtifact(
            artifact_type="UI_CONTRACT",
            schemaVersion=str(source.get("schemaVersion") or "1.0"),
            module=module,
            routes=routes,
            pages=pages,
            mockData=mock_data,
            apiBindings=api_bindings,
            developmentRules=development_rules,
            analysisSummary=str(
                source.get("analysisSummary")
                or "已生成可 Mock 预览并可供开发 Agent 复用的页面级 UI 契约。"
            ),
            markdown="",
        )

    def _normalize_routes(
        self,
        raw_routes: Any,
        fallback_routes: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_routes, list) or not raw_routes:
            return fallback_routes

        routes: list[dict[str, Any]] = []

        for index, item in enumerate(raw_routes):
            if not isinstance(item, dict):
                continue

            page_id = str(item.get("pageId") or item.get("page_id") or f"page_{index + 1}")
            routes.append(
                {
                    "path": str(item.get("path") or f"/mock/{page_id}"),
                    "pageId": page_id,
                    "title": str(item.get("title") or item.get("name") or page_id),
                }
            )

        return routes or fallback_routes

    def _normalize_pages(
        self,
        raw_pages: Any,
        fallback_pages: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_pages, list) or not raw_pages:
            return fallback_pages

        pages: list[dict[str, Any]] = []

        for index, page in enumerate(raw_pages):
            if not isinstance(page, dict):
                continue

            page_id = str(page.get("pageId") or page.get("page_id") or f"page_{index + 1}")
            components = self._normalize_components(page.get("components"))

            if not components:
                components = fallback_pages[0].get("components", [])

            pages.append(
                {
                    **page,
                    "pageId": page_id,
                    "pageName": str(page.get("pageName") or page.get("title") or page_id),
                    "layout": str(page.get("layout") or "form-table"),
                    "description": str(page.get("description") or "由 UI_CONTRACT 生成的 Mock 预览页面。"),
                    "components": components,
                    "states": page.get("states")
                    if isinstance(page.get("states"), dict)
                    else {
                        "loading": True,
                        "empty": True,
                        "error": True,
                        "success": True,
                    },
                }
            )

        return pages or fallback_pages

    def _normalize_components(
        self,
        raw_components: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_components, list):
            return []

        components: list[dict[str, Any]] = []

        for index, raw in enumerate(raw_components):
            if not isinstance(raw, dict):
                continue

            component = dict(raw)
            component_id = str(
                component.get("componentId")
                or component.get("component_id")
                or f"component_{index + 1}"
            )
            component_type = str(component.get("type") or "card").strip().lower()

            if component_type not in self.SUPPORTED_COMPONENT_TYPES:
                component_type = "card"

            component["componentId"] = component_id
            component["type"] = component_type
            component.setdefault("title", component.get("label") or component_id)

            if component_type == "form":
                component["fields"] = self._normalize_fields(component.get("fields"))
                component["actions"] = self._normalize_actions(component.get("actions"))

            if component_type == "table":
                component["columns"] = self._normalize_columns(component.get("columns"))
                data_source = component.get("dataSource")
                if not isinstance(data_source, dict):
                    data_source = {}
                data_source.setdefault("mockBinding", "records")
                component["dataSource"] = data_source
                component["actions"] = self._normalize_actions(component.get("actions"))
                component["rowActions"] = self._normalize_actions(component.get("rowActions"))

            if component_type in {"section", "tabs", "card"}:
                nested = self._normalize_components(component.get("components"))
                if nested:
                    component["components"] = nested

            if component_type == "modal":
                component.setdefault("modalId", component_id)
                component["actions"] = self._normalize_actions(component.get("actions"))

            if component_type in {"toolbar", "button"}:
                component["actions"] = self._normalize_actions(
                    component.get("actions")
                    or [
                        {
                            "actionId": component.get("actionId") or component_id,
                            "type": component.get("actionType") or "custom",
                            "label": component.get("label") or component.get("title") or "操作",
                            "mockAction": component.get("mockAction") or "mockToolbarAction",
                            "apiBinding": component.get("apiBinding") or "",
                        }
                    ]
                )

            components.append(component)

        return components

    def _normalize_fields(
        self,
        raw_fields: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_fields, list):
            return []

        fields: list[dict[str, Any]] = []
        for index, raw in enumerate(raw_fields):
            if not isinstance(raw, dict):
                continue

            field_name = str(raw.get("field") or raw.get("name") or f"field_{index + 1}")
            field_type = str(raw.get("type") or raw.get("fieldType") or "input").lower()

            fields.append(
                {
                    **raw,
                    "field": field_name,
                    "label": str(raw.get("label") or raw.get("field_name") or field_name),
                    "type": field_type,
                    "required": bool(raw.get("required", False)),
                    "placeholder": str(raw.get("placeholder") or ""),
                    "validation": raw.get("validation")
                    if isinstance(raw.get("validation"), dict)
                    else {},
                }
            )

        return fields

    def _normalize_columns(
        self,
        raw_columns: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_columns, list):
            return []

        columns: list[dict[str, Any]] = []
        for index, raw in enumerate(raw_columns):
            if not isinstance(raw, dict):
                continue

            field = str(raw.get("field") or raw.get("dataIndex") or raw.get("name") or f"column_{index + 1}")
            columns.append(
                {
                    **raw,
                    "field": field,
                    "label": str(raw.get("label") or raw.get("title") or field),
                    "type": str(raw.get("type") or "text"),
                }
            )
        return columns

    def _normalize_actions(
        self,
        raw_actions: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_actions, list):
            return []

        actions: list[dict[str, Any]] = []
        for index, raw in enumerate(raw_actions):
            if not isinstance(raw, dict):
                continue

            action_type = str(raw.get("type") or "custom")
            action_id = str(raw.get("actionId") or raw.get("action_id") or f"{action_type}_{index + 1}")
            actions.append(
                {
                    **raw,
                    "actionId": action_id,
                    "type": action_type,
                    "label": str(raw.get("label") or action_type),
                    "mockAction": raw.get("mockAction") or f"mock_{action_id}",
                    "apiBinding": raw.get("apiBinding") or "",
                    "successMessage": raw.get("successMessage") or "Mock 操作已完成。",
                }
            )

        return actions

    def _normalize_mock_data(
        self,
        raw_mock_data: Any,
        fallback_mock_data: dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(raw_mock_data, dict) or not raw_mock_data:
            return fallback_mock_data

        return raw_mock_data

    def _normalize_api_bindings(
        self,
        raw_api_bindings: Any,
        fallback_api_bindings: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        if not isinstance(raw_api_bindings, list) or not raw_api_bindings:
            return fallback_api_bindings

        return [
            item
            for item in raw_api_bindings
            if isinstance(item, dict)
        ] or fallback_api_bindings

    def _normalize_development_rules(
        self,
        raw_rules: Any,
        fallback_rules: dict[str, Any],
    ) -> dict[str, Any]:
        rules = dict(raw_rules) if isinstance(raw_rules, dict) else {}
        merged = dict(fallback_rules)
        merged.update(rules)
        merged["mustFollowLayout"] = True
        merged["mustFollowFields"] = True
        merged["mustFollowValidationRules"] = True
        merged["mustFollowInteractionFlow"] = True
        merged["mustFollowApiBindings"] = True
        merged["breakingChangeRequiresReview"] = True
        merged.setdefault("allowStyleOptimization", True)
        return merged

    def _fallback_artifact(
        self,
        request: AgentTaskRequest,
        error: Exception | None,
    ) -> UiContractArtifact:
        module = self._extract_current_module(request)
        module_name = str(module.get("module_name") or module.get("moduleName") or module.get("name") or "当前页面")
        module_key = str(module.get("module_key") or module.get("moduleKey") or module.get("key") or "current_page")
        page_kind = self._infer_page_kind(module)
        layout = self._infer_layout_type(module, page_kind)

        page_id = f"{module_key}_page"
        route_path = self._extract_extra(module, "routePath") or self._extract_extra(module, "route_path") or f"/mock/{module_key}"

        components = self._fallback_components(
            module_name=module_name,
            module_key=module_key,
            page_kind=page_kind,
            layout=layout,
        )

        mock_data = self._fallback_mock_data(module_name, page_kind)
        api_bindings = self._fallback_api_bindings(module_key, page_kind)

        return UiContractArtifact(
            artifact_type="UI_CONTRACT",
            schemaVersion="1.0",
            module=module,
            routes=[
                {
                    "path": route_path,
                    "pageId": page_id,
                    "title": module_name,
                }
            ],
            pages=[
                {
                    "pageId": page_id,
                    "pageName": module_name,
                    "layout": layout,
                    "description": f"{module_name}的页面级 Mock 预览蓝图，后续开发 Agent 必须基于该 UI_CONTRACT 生成真实页面。",
                    "components": components,
                    "states": {
                        "loading": True,
                        "empty": True,
                        "error": True,
                        "success": True,
                    },
                }
            ],
            mockData=mock_data,
            apiBindings=api_bindings,
            developmentRules={
                "mustFollowLayout": True,
                "mustFollowFields": True,
                "mustFollowValidationRules": True,
                "mustFollowInteractionFlow": True,
                "mustFollowApiBindings": True,
                "allowStyleOptimization": True,
                "breakingChangeRequiresReview": True,
            },
            analysisSummary=(
                "已生成页面级 UI_CONTRACT，可由 Mock Preview Runtime 渲染，并可作为后续开发 Agent 的页面蓝图。"
                + (f" LLM 原始生成失败，已启用兜底策略：{type(error).__name__}。" if error else "")
            ),
            markdown="",
        )

    def _fallback_components(
        self,
        *,
        module_name: str,
        module_key: str,
        page_kind: str,
        layout: str,
    ) -> list[dict[str, Any]]:
        if page_kind == "DASHBOARD_PAGE":
            return [
                {
                    "componentId": "dashboard_metrics",
                    "type": "card",
                    "title": "核心指标",
                    "items": [
                        {"label": "待处理", "value": "12", "trend": "+8%"},
                        {"label": "处理中", "value": "36", "trend": "+3%"},
                        {"label": "已完成", "value": "128", "trend": "+16%"},
                    ],
                },
                {
                    "componentId": "dashboard_table",
                    "type": "table",
                    "title": f"{module_name}明细",
                    "dataSource": {
                        "mockBinding": "records",
                        "realApiBinding": f"GET /api/{module_key}/records",
                    },
                    "columns": [
                        {"field": "code", "label": "编号", "type": "text"},
                        {"field": "name", "label": "名称", "type": "text"},
                        {"field": "status", "label": "状态", "type": "tag"},
                        {"field": "owner", "label": "负责人", "type": "text"},
                    ],
                    "rowActions": [
                        {
                            "actionId": "view_detail",
                            "type": "navigate",
                            "label": "查看",
                            "mockAction": "mockViewDetail",
                            "apiBinding": f"GET /api/{module_key}/records/{{id}}",
                            "successMessage": "已打开 Mock 详情。",
                        }
                    ],
                },
            ]

        if page_kind == "DETAIL_PAGE":
            return [
                {
                    "componentId": "detail_summary",
                    "type": "description",
                    "title": "详情摘要",
                    "dataSource": {
                        "mockBinding": "detail",
                        "realApiBinding": f"GET /api/{module_key}/records/{{id}}",
                    },
                    "fields": [
                        {"field": "code", "label": "编号"},
                        {"field": "name", "label": "名称"},
                        {"field": "status", "label": "状态"},
                        {"field": "owner", "label": "负责人"},
                        {"field": "createdAt", "label": "创建时间"},
                    ],
                },
                {
                    "componentId": "detail_tabs",
                    "type": "tabs",
                    "title": "关联信息",
                    "tabs": [
                        {
                            "key": "logs",
                            "title": "操作日志",
                            "components": [
                                {
                                    "componentId": "log_table",
                                    "type": "table",
                                    "title": "操作日志",
                                    "dataSource": {"mockBinding": "logs"},
                                    "columns": [
                                        {"field": "time", "label": "时间"},
                                        {"field": "operator", "label": "操作人"},
                                        {"field": "action", "label": "动作"},
                                    ],
                                }
                            ],
                        }
                    ],
                },
            ]

        if page_kind == "APPROVAL_PAGE":
            return [
                {
                    "componentId": "approval_steps",
                    "type": "steps",
                    "title": "审批进度",
                    "items": [
                        {"title": "提交", "status": "done"},
                        {"title": "审批", "status": "active"},
                        {"title": "归档", "status": "pending"},
                    ],
                },
                {
                    "componentId": "approval_detail",
                    "type": "description",
                    "title": "业务详情",
                    "dataSource": {"mockBinding": "detail"},
                    "fields": [
                        {"field": "code", "label": "编号"},
                        {"field": "name", "label": "名称"},
                        {"field": "status", "label": "状态"},
                    ],
                },
                {
                    "componentId": "approval_form",
                    "type": "form",
                    "title": "审批意见",
                    "fields": [
                        {
                            "field": "approvalOpinion",
                            "label": "审批意见",
                            "type": "textarea",
                            "required": True,
                            "placeholder": "请输入审批意见",
                            "validation": {"required": True, "maxLength": 500},
                        }
                    ],
                    "actions": [
                        {
                            "actionId": "approve",
                            "type": "approve",
                            "label": "审批通过",
                            "mockAction": "mockApprove",
                            "apiBinding": f"POST /api/{module_key}/records/{{id}}/approve",
                            "successMessage": "Mock 审批通过。",
                        },
                        {
                            "actionId": "reject",
                            "type": "reject",
                            "label": "驳回",
                            "mockAction": "mockReject",
                            "apiBinding": f"POST /api/{module_key}/records/{{id}}/reject",
                            "successMessage": "Mock 驳回成功。",
                        },
                    ],
                },
            ]

        if page_kind == "LIST_PAGE":
            return [
                {
                    "componentId": "query_form",
                    "type": "form",
                    "title": "查询条件",
                    "fields": [
                        {
                            "field": "keyword",
                            "label": "关键词",
                            "type": "input",
                            "required": False,
                            "placeholder": "请输入名称或编号",
                            "validation": {"maxLength": 64},
                        },
                        {
                            "field": "status",
                            "label": "状态",
                            "type": "select",
                            "required": False,
                            "placeholder": "请选择状态",
                            "options": [
                                {"label": "草稿", "value": "DRAFT"},
                                {"label": "已提交", "value": "SUBMITTED"},
                                {"label": "已完成", "value": "COMPLETED"},
                            ],
                            "validation": {},
                        },
                    ],
                    "actions": [
                        {
                            "actionId": "query",
                            "type": "query",
                            "label": "查询",
                            "mockAction": "mockQueryRecords",
                            "apiBinding": f"GET /api/{module_key}/records",
                            "successMessage": "已按 Mock 条件查询。",
                        },
                        {
                            "actionId": "reset_query",
                            "type": "reset-form",
                            "label": "重置",
                            "mockAction": "mockResetQuery",
                            "apiBinding": "",
                            "successMessage": "查询条件已重置。",
                        },
                    ],
                },
                {
                    "componentId": "toolbar",
                    "type": "toolbar",
                    "title": "列表工具栏",
                    "actions": [
                        {
                            "actionId": "create",
                            "type": "open-modal",
                            "label": "新增",
                            "targetModalId": "create_modal",
                            "mockAction": "mockOpenCreateModal",
                            "apiBinding": "",
                            "successMessage": "已打开新增弹窗。",
                        },
                        {
                            "actionId": "export",
                            "type": "export",
                            "label": "导出",
                            "mockAction": "mockExport",
                            "apiBinding": f"GET /api/{module_key}/records/export",
                            "successMessage": "Mock 导出任务已创建。",
                        },
                    ],
                },
                self._records_table_component(module_key),
                self._create_modal_component(module_name, module_key),
            ]

        return [
            {
                "componentId": "main_form",
                "type": "form",
                "title": f"{module_name}信息录入",
                "fields": self._base_form_fields(),
                "actions": [
                    {
                        "actionId": "save_draft",
                        "type": "submit",
                        "label": "保存草稿",
                        "mockAction": "mockSaveDraft",
                        "apiBinding": f"POST /api/{module_key}/records/draft",
                        "successMessage": "草稿已保存到 Mock 数据。",
                    },
                    {
                        "actionId": "submit",
                        "type": "submit",
                        "label": "提交",
                        "mockAction": "mockSubmit",
                        "apiBinding": f"POST /api/{module_key}/records/submit",
                        "successMessage": "提交成功，已写入 Mock 数据。",
                    },
                    {
                        "actionId": "reset_form",
                        "type": "reset-form",
                        "label": "重置",
                        "mockAction": "mockResetForm",
                        "apiBinding": "",
                        "successMessage": "表单已重置。",
                    },
                ],
            },
            self._records_table_component(module_key),
            {
                "componentId": "confirm_submit_modal",
                "type": "modal",
                "modalId": "confirm_submit_modal",
                "title": "提交确认",
                "body": "确认提交当前表单内容吗？提交后将进入后续业务流程。",
                "actions": [
                    {
                        "actionId": "confirm_submit",
                        "type": "submit",
                        "label": "确认提交",
                        "mockAction": "mockSubmit",
                        "apiBinding": f"POST /api/{module_key}/records/submit",
                        "successMessage": "Mock 提交成功。",
                    }
                ],
            },
        ]

    def _base_form_fields(self) -> list[dict[str, Any]]:
        return [
            {
                "field": "name",
                "label": "名称",
                "type": "input",
                "required": True,
                "placeholder": "请输入名称",
                "validation": {"required": True, "maxLength": 64},
            },
            {
                "field": "status",
                "label": "状态",
                "type": "select",
                "required": True,
                "placeholder": "请选择状态",
                "options": [
                    {"label": "草稿", "value": "DRAFT"},
                    {"label": "已提交", "value": "SUBMITTED"},
                    {"label": "已完成", "value": "COMPLETED"},
                ],
                "validation": {"required": True},
            },
            {
                "field": "owner",
                "label": "负责人",
                "type": "input",
                "required": False,
                "placeholder": "请输入负责人",
                "validation": {"maxLength": 32},
            },
            {
                "field": "remark",
                "label": "备注",
                "type": "textarea",
                "required": False,
                "placeholder": "请输入备注",
                "validation": {"maxLength": 500},
            },
        ]

    def _records_table_component(self, module_key: str) -> dict[str, Any]:
        return {
            "componentId": "records_table",
            "type": "table",
            "title": "数据列表",
            "dataSource": {
                "mockBinding": "records",
                "realApiBinding": f"GET /api/{module_key}/records",
            },
            "columns": [
                {"field": "code", "label": "编号", "type": "text"},
                {"field": "name", "label": "名称", "type": "text"},
                {"field": "status", "label": "状态", "type": "tag"},
                {"field": "owner", "label": "负责人", "type": "text"},
                {"field": "createdAt", "label": "创建时间", "type": "datetime"},
            ],
            "rowActions": [
                {
                    "actionId": "view",
                    "type": "navigate",
                    "label": "查看",
                    "mockAction": "mockViewDetail",
                    "apiBinding": f"GET /api/{module_key}/records/{{id}}",
                    "successMessage": "已打开 Mock 详情。",
                },
                {
                    "actionId": "delete",
                    "type": "delete",
                    "label": "删除",
                    "mockAction": "mockDeleteRecord",
                    "apiBinding": f"DELETE /api/{module_key}/records/{{id}}",
                    "successMessage": "Mock 数据已删除。",
                },
            ],
        }

    def _create_modal_component(self, module_name: str, module_key: str) -> dict[str, Any]:
        return {
            "componentId": "create_modal",
            "type": "modal",
            "modalId": "create_modal",
            "title": f"新增{module_name}",
            "body": "在真实系统中这里应展示新增表单；Mock Runtime 中点击确认会写入模拟数据。",
            "actions": [
                {
                    "actionId": "confirm_create",
                    "type": "submit",
                    "label": "确认新增",
                    "mockAction": "mockCreateRecord",
                    "apiBinding": f"POST /api/{module_key}/records",
                    "successMessage": "Mock 新增成功。",
                }
            ],
        }

    def _fallback_mock_data(
        self,
        module_name: str,
        page_kind: str,
    ) -> dict[str, Any]:
        return {
            "records": [
                {
                    "id": "MOCK-001",
                    "code": "MOCK-001",
                    "name": f"{module_name}示例记录 A",
                    "status": "DRAFT",
                    "owner": "张三",
                    "remark": "用于需求评审的 Mock 数据。",
                    "createdAt": "2026-07-14 09:00",
                },
                {
                    "id": "MOCK-002",
                    "code": "MOCK-002",
                    "name": f"{module_name}示例记录 B",
                    "status": "SUBMITTED",
                    "owner": "李四",
                    "remark": "用于展示列表和状态。",
                    "createdAt": "2026-07-14 10:30",
                },
            ],
            "detail": {
                "id": "MOCK-001",
                "code": "MOCK-001",
                "name": f"{module_name}示例详情",
                "status": "SUBMITTED",
                "owner": "张三",
                "createdAt": "2026-07-14 09:00",
            },
            "logs": [
                {"time": "2026-07-14 09:05", "operator": "张三", "action": "创建记录"},
                {"time": "2026-07-14 10:20", "operator": "李四", "action": "提交审批"},
            ],
        }

    def _fallback_api_bindings(
        self,
        module_key: str,
        page_kind: str,
    ) -> list[dict[str, Any]]:
        return [
            {
                "bindingKey": "query_records",
                "method": "GET",
                "path": f"/api/{module_key}/records",
                "mockBinding": "records",
                "usedBy": ["query_form", "records_table"],
            },
            {
                "bindingKey": "create_record",
                "method": "POST",
                "path": f"/api/{module_key}/records",
                "mockAction": "mockCreateRecord",
                "usedBy": ["main_form", "create_modal"],
            },
            {
                "bindingKey": "submit_record",
                "method": "POST",
                "path": f"/api/{module_key}/records/submit",
                "mockAction": "mockSubmit",
                "usedBy": ["main_form", "confirm_submit_modal"],
            },
        ]

    def _extract_current_module(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, Any]:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        for source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
        ]:
            if isinstance(source, dict):
                return source

        return {
            "module_id": request.module_id,
            "module_key": "",
            "module_name": "",
            "module_type": "",
            "module_path": "",
            "extra": {},
        }

    def _get_requirement_text(self, request: AgentTaskRequest) -> str:
        input_payload = request.input or {}

        for key in ["requirement_text", "requirementText", "supplement"]:
            value = input_payload.get(key)
            if value:
                return str(value)

        return request.requirement_text or ""

    def _infer_page_kind(self, module: dict[str, Any]) -> str:
        explicit = (
            self._extract_extra(module, "pageKind")
            or self._extract_extra(module, "page_kind")
            or module.get("pageKind")
            or module.get("page_kind")
        )

        if explicit:
            return str(explicit)

        name = str(module.get("module_name") or module.get("moduleName") or module.get("name") or "")

        if "列表" in name or "查询" in name or "台账" in name:
            return "LIST_PAGE"

        if "详情" in name or "明细" in name:
            return "DETAIL_PAGE"

        if "审批" in name or "审核" in name:
            return "APPROVAL_PAGE"

        if "看板" in name or "统计" in name or "分析" in name:
            return "DASHBOARD_PAGE"

        if "设置" in name or "配置" in name:
            return "SETTINGS_PAGE"

        return "FORM_PAGE"

    def _infer_layout_type(
        self,
        module: dict[str, Any],
        page_kind: str,
    ) -> str:
        explicit = (
            self._extract_extra(module, "layoutType")
            or self._extract_extra(module, "layout_type")
            or module.get("layoutType")
            or module.get("layout_type")
        )

        if explicit:
            return str(explicit)

        return {
            "LIST_PAGE": "table-list",
            "FORM_PAGE": "form-table",
            "DETAIL_PAGE": "detail-tabs",
            "APPROVAL_PAGE": "approval-workbench",
            "DASHBOARD_PAGE": "dashboard",
            "SETTINGS_PAGE": "form-detail",
        }.get(page_kind, "form-table")

    def _extract_extra(
        self,
        module: dict[str, Any],
        key: str,
    ) -> Any:
        extra = module.get("extra")
        if isinstance(extra, dict):
            return extra.get(key)
        return None

    def _build_markdown(
        self,
        artifact: UiContractArtifact,
    ) -> str:
        module_name = (
            artifact.module.get("module_name")
            or artifact.module.get("moduleName")
            or artifact.module.get("name")
            or "当前页面"
        )

        lines = [
            f"# {module_name}交互原型 / UI_CONTRACT",
            "",
            "## 1. 资产定位",
            "本资产是 PAGE 级 UI_CONTRACT，不是一次性截图或静态说明。它同时服务于需求阶段 Mock Preview 和后续开发 Agent 真实页面生成。",
            "",
            "## 2. 页面路由",
        ]

        if artifact.routes:
            for route in artifact.routes:
                lines.append(f"- {route.get('title', '-')}: `{route.get('path', '-')}` → `{route.get('pageId', '-')}`")
        else:
            lines.append("- 未定义路由。")

        lines.extend(["", "## 3. 页面与组件"])

        for page in artifact.pages:
            components = page.get("components") or []
            lines.append(
                f"- **{page.get('pageName') or page.get('title') or page.get('pageId')}**："
                f"{page.get('layout', '-')}，组件数 {len(components)}"
            )
            for component in components:
                if isinstance(component, dict):
                    lines.append(
                        f"  - {component.get('type', '-')}："
                        f"{component.get('title') or component.get('label') or component.get('componentId')}"
                    )

        lines.extend(
            [
                "",
                "## 4. API 绑定",
            ]
        )

        if artifact.apiBindings:
            for binding in artifact.apiBindings:
                lines.append(
                    f"- `{binding.get('method', '-')}` `{binding.get('path', '-')}`"
                    f"：{binding.get('bindingKey') or binding.get('mockBinding') or binding.get('mockAction') or '-'}"
                )
        else:
            lines.append("- 暂无 API 绑定。")

        lines.extend(
            [
                "",
                "## 5. 开发一致性规则",
                f"- 必须遵循布局：{artifact.developmentRules.get('mustFollowLayout')}",
                f"- 必须遵循字段：{artifact.developmentRules.get('mustFollowFields')}",
                f"- 必须遵循校验规则：{artifact.developmentRules.get('mustFollowValidationRules')}",
                f"- 必须遵循交互流程：{artifact.developmentRules.get('mustFollowInteractionFlow')}",
                f"- 必须遵循接口绑定：{artifact.developmentRules.get('mustFollowApiBindings')}",
                f"- 破坏性变更需要评审：{artifact.developmentRules.get('breakingChangeRequiresReview')}",
                "",
                "## 6. 分析摘要",
                artifact.analysisSummary or "-",
            ]
        )

        return "\n".join(lines)

    def _strip_code_fence(
        self,
        text: str,
    ) -> str:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()

        if cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()

        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()

        return cleaned.strip()