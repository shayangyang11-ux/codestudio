from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.page_spec import (
    PAGE_SPEC_LLM_JSON_SCHEMA,
    PageSpecArtifact,
    PageSpecLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


class PageSpecAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> PageSpecArtifact:

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("page_spec.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="page_spec_output",
            output_json_schema=PAGE_SPEC_LLM_JSON_SCHEMA,
        )

        parsed = PageSpecLlmOutput.model_validate_json(llm_result.text)
        markdown = self._build_markdown(parsed)

        return PageSpecArtifact(
            basic_info=parsed.basic_info,
            layout_sections=parsed.layout_sections,
            component_specs=parsed.component_specs,
            action_specs=parsed.action_specs,
            form_fields=parsed.form_fields,
            table_columns=parsed.table_columns,
            api_bindings=parsed.api_bindings,
            page_states=parsed.page_states,
            permission_specs=parsed.permission_specs,
            exception_specs=parsed.exception_specs,
            acceptance_criteria=parsed.acceptance_criteria,
            downstream_hints=parsed.downstream_hints,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        requirement_text = self._get_requirement_text(request)
        context_package = request.context_package or {}
        input_payload = request.input or {}

        current_module = self._extract_current_module(context_package, input_payload)
        module_prd = self._extract_artifact(context_package, "MODULE_PRD")
        business_flow = self._extract_artifact(context_package, "BUSINESS_FLOW")
        module_tree = self._extract_artifact(context_package, "MODULE_TREE")

        return "\n".join(
            [
                "请为当前指定模块生成企业级 PAGE_SPEC 页面规范资产。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"module_id: {request.module_id}",
                f"task_type: {request.task_type}",
                "",
                "【当前模块】",
                json.dumps(current_module, ensure_ascii=False, default=str),
                "",
                "【用户补充需求】",
                requirement_text or "无额外补充需求。",
                "",
                "【当前模块 PRD】",
                json.dumps(module_prd, ensure_ascii=False, default=str),
                "",
                "【当前模块业务流程】",
                json.dumps(business_flow, ensure_ascii=False, default=str),
                "",
                "【模块树摘要】",
                json.dumps(module_tree, ensure_ascii=False, default=str),
                "",
                "【完整上下文包】",
                json.dumps(context_package, ensure_ascii=False, default=str),
                "",
                "请只输出符合 JSON Schema 的 JSON 对象。",
                "不要输出 markdown 字段。",
                "不要输出 JSON 代码块。",
            ]
        )

    def _get_requirement_text(self, request: AgentTaskRequest) -> str:
        if request.input.get("requirement_text"):
            return str(request.input["requirement_text"])

        if request.input.get("supplement"):
            return str(request.input["supplement"])

        if request.requirement_text:
            return request.requirement_text

        return ""

    def _extract_current_module(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> dict[str, Any]:
        if isinstance(input_payload.get("current_module"), dict):
            return input_payload["current_module"]

        if isinstance(context_package.get("current_module"), dict):
            return context_package["current_module"]

        if isinstance(context_package.get("module"), dict):
            return context_package["module"]

        return {
            "module_id": None,
            "module_key": "",
            "module_name": "",
            "module_type": "",
            "module_path": "",
        }

    def _extract_artifact(
        self,
        context_package: dict[str, Any],
        artifact_type: str,
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            return artifacts.get(artifact_type, {})

        if isinstance(artifacts, list):
            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                if artifact.get("artifact_type") == artifact_type:
                    return artifact

        return {}

    def _build_markdown(self, spec: PageSpecLlmOutput) -> str:
        info = spec.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块页面规范",
            "",
            "## 1. 页面基本信息",
            f"- **页面 Key**：{info.page_key}",
            f"- **页面名称**：{info.page_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **页面类型**：{info.page_type}",
            f"- **路由建议**：`{info.route_suggestion}`",
            f"- **页面目标**：{info.page_goal}",
            f"- **主要使用角色**：{info.primary_user_role}",
            "",
            "## 2. 页面布局区域",
        ]

        if spec.layout_sections:
            for section in spec.layout_sections:
                lines.extend(
                    [
                        f"- **{section.section_name}**（{section.section_key}）",
                        f"  - 位置：{section.position}",
                        f"  - 说明：{section.description}",
                        f"  - 组件：{self._join_list(section.components)}",
                    ]
                )
        else:
            lines.append("- 未识别出明确布局区域。")

        lines.extend(["", "## 3. 页面组件规范"])

        if spec.component_specs:
            for component in spec.component_specs:
                lines.extend(
                    [
                        f"- **{component.component_name}**（{component.component_key}）",
                        f"  - 类型：{component.component_type}",
                        f"  - 说明：{component.description}",
                        f"  - 数据来源：{component.data_source}",
                        f"  - 交互：{self._join_list(component.interactions)}",
                        f"  - 展示规则：{self._join_list(component.display_rules)}",
                    ]
                )
        else:
            lines.append("- 未识别出明确页面组件。")

        lines.extend(["", "## 4. 页面操作"])

        if spec.action_specs:
            for action in spec.action_specs:
                lines.extend(
                    [
                        f"- **{action.action_name}**（{action.action_key}）",
                        f"  - 触发位置：{action.trigger_position}",
                        f"  - 执行角色：{action.actor}",
                        f"  - 说明：{action.description}",
                        f"  - 前置条件：{self._join_list(action.preconditions)}",
                        f"  - 业务规则：{self._join_list(action.business_rules)}",
                        f"  - 成功反馈：{action.success_feedback}",
                        f"  - 失败反馈：{action.failure_feedback}",
                    ]
                )
        else:
            lines.append("- 未识别出明确页面操作。")

        lines.extend(["", "## 5. 表单字段"])

        if spec.form_fields:
            for field in spec.form_fields:
                required_text = "是" if field.required else "否"
                lines.extend(
                    [
                        f"- **{field.field_name}**（{field.field_key}）",
                        f"  - 类型：{field.field_type}",
                        f"  - 必填：{required_text}",
                        f"  - 默认值：{field.default_value or '无'}",
                        f"  - 校验规则：{self._join_list(field.validation_rules)}",
                        f"  - 说明：{field.description}",
                    ]
                )
        else:
            lines.append("- 当前页面无明确表单字段。")

        lines.extend(["", "## 6. 表格列"])

        if spec.table_columns:
            for column in spec.table_columns:
                lines.extend(
                    [
                        f"- **{column.column_name}**（{column.column_key}）",
                        f"  - 数据字段：{column.data_field}",
                        f"  - 展示类型：{column.display_type}",
                        f"  - 可排序：{'是' if column.sortable else '否'}",
                        f"  - 可筛选：{'是' if column.filterable else '否'}",
                        f"  - 说明：{column.description}",
                    ]
                )
        else:
            lines.append("- 当前页面无明确表格列。")

        lines.extend(["", "## 7. API 绑定"])

        if spec.api_bindings:
            for api in spec.api_bindings:
                lines.extend(
                    [
                        f"- **{api.api_name}**（{api.api_key}）",
                        f"  - 方法：{api.method}",
                        f"  - 路径：`{api.path_suggestion}`",
                        f"  - 触发时机：{api.trigger_timing}",
                        f"  - 请求摘要：{api.request_summary}",
                        f"  - 响应用途：{api.response_usage}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确 API 绑定。")

        lines.extend(["", "## 8. 页面状态"])

        if spec.page_states:
            for state in spec.page_states:
                lines.extend(
                    [
                        f"- **{state.state_name}**（{state.state_key}）",
                        f"  - 说明：{state.description}",
                        f"  - UI 行为：{state.ui_behavior}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确页面状态。")

        lines.extend(["", "## 9. 权限控制"])

        if spec.permission_specs:
            for permission in spec.permission_specs:
                lines.extend(
                    [
                        f"- **{permission.permission_name}**（{permission.permission_key}）",
                        f"  - 角色：{self._join_list(permission.roles)}",
                        f"  - 受限元素：{self._join_list(permission.restricted_elements)}",
                        f"  - 说明：{permission.description}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确权限控制。")

        lines.extend(["", "## 10. 异常与空状态"])

        if spec.exception_specs:
            for exception in spec.exception_specs:
                lines.extend(
                    [
                        f"- **{exception.title}**（{exception.exception_key}）",
                        f"  - 场景：{exception.scenario}",
                        f"  - UI 反馈：{exception.ui_feedback}",
                        f"  - 恢复动作：{exception.recovery_action}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确异常状态。")

        lines.extend(["", "## 11. 验收标准"])

        if spec.acceptance_criteria:
            for criterion in spec.acceptance_criteria:
                lines.extend(
                    [
                        f"- **{criterion.criterion_key}**：{criterion.description}",
                        f"  - 验证方式：{criterion.verification_method}",
                    ]
                )
        else:
            lines.append("- 未生成页面验收标准。")

        hints = spec.downstream_hints

        lines.extend(
            [
                "",
                "## 12. 下游工程资产生成提示",
                "### 12.1 数据模型提示",
                self._bullet_list(hints.data_model_hints),
                "",
                "### 12.2 接口契约提示",
                self._bullet_list(hints.api_contract_hints),
                "",
                "### 12.3 测试规格提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 12.4 前端组件提示",
                self._bullet_list(hints.frontend_component_hints),
                "",
                "### 12.5 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "## 13. 开发注意事项",
                self._bullet_list(spec.development_notes),
                "",
                "## 14. 测试注意事项",
                self._bullet_list(spec.testing_notes),
                "",
                "## 15. 分析摘要",
                spec.analysis_summary,
            ]
        )

        return "\n".join(lines)

    def _join_list(self, values: list[str]) -> str:
        if not values:
            return "无"

        return "；".join(values)

    def _bullet_list(self, values: list[str]) -> str:
        if not values:
            return "- 无"

        return "\n".join([f"- {value}" for value in values])

    def _load_prompt(self, filename: str) -> str:
        prompt_path = (
            Path(__file__).resolve().parents[2]
            / "prompts"
            / "requirement"
            / filename
        )

        return prompt_path.read_text(encoding="utf-8")