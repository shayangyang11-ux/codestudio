from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.module_prd import (
    MODULE_PRD_LLM_JSON_SCHEMA,
    ModulePrdArtifact,
    ModulePrdLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


class ModulePrdAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> ModulePrdArtifact:
        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("module_prd.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="module_prd_output",
            output_json_schema=MODULE_PRD_LLM_JSON_SCHEMA,
        )

        parsed = ModulePrdLlmOutput.model_validate_json(llm_result.text)
        markdown = self._build_markdown(parsed)

        return ModulePrdArtifact(
            module_basic_info=parsed.module_basic_info,
            business_background=parsed.business_background,
            user_roles=parsed.user_roles,
            functional_requirements=parsed.functional_requirements,
            business_rules=parsed.business_rules,
            state_rules=parsed.state_rules,
            page_requirements=parsed.page_requirements,
            data_requirements=parsed.data_requirements,
            api_requirements=parsed.api_requirements,
            permission_requirements=parsed.permission_requirements,
            exception_scenarios=parsed.exception_scenarios,
            acceptance_criteria=parsed.acceptance_criteria,
            non_functional_requirements=parsed.non_functional_requirements,
            downstream_artifact_hints=parsed.downstream_artifact_hints,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            markdown=markdown,
            analysis_summary=parsed.analysis_summary,
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        requirement_text = self._get_requirement_text(request)
        context_package = request.context_package or {}
        input_payload = request.input or {}

        current_module = self._extract_current_module(context_package, input_payload)
        module_tree_summary = self._extract_module_tree_summary(context_package)
        related_artifacts = self._extract_related_artifacts(context_package)

        return "\n".join(
            [
                "请为当前指定模块生成企业级 MODULE_PRD。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"module_id: {request.module_id}",
                f"task_type: {request.task_type}",
                "",
                "【当前模块】",
                json.dumps(
                    current_module,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【用户补充需求】",
                requirement_text or "无额外补充需求。",
                "",
                "【模块树摘要】",
                json.dumps(
                    module_tree_summary,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【相关已有产物】",
                json.dumps(
                    related_artifacts,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【完整上下文包】",
                json.dumps(
                    context_package,
                    ensure_ascii=False,
                    default=str,
                ),
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

    def _extract_module_tree_summary(
            self,
            context_package: dict[str, Any],
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            module_tree = artifacts.get("MODULE_TREE")
            if module_tree is not None:
                return module_tree

        if isinstance(artifacts, list):
            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                if artifact.get("artifact_type") == "MODULE_TREE":
                    return artifact

        return {}

    def _extract_related_artifacts(
            self,
            context_package: dict[str, Any],
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if artifacts is None:
            return []

        return artifacts

    def _build_markdown(self, prd: ModulePrdLlmOutput) -> str:
        info = prd.module_basic_info

        lines: list[str] = [
            f"# {info.module_name} 模块 PRD",
            "",
            "## 1. 模块基本信息",
            f"- **模块 Key**：{info.module_key}",
            f"- **模块名称**：{info.module_name}",
            f"- **模块类型**：{info.module_type}",
            f"- **模块位置**：{info.module_position}",
            f"- **模块目标**：{info.module_goal}",
            f"- **职责边界**：{info.responsibility_boundary}",
            "",
            "## 2. 业务背景",
            prd.business_background,
            "",
            "## 3. 用户角色",
        ]

        for role in prd.user_roles:
            lines.append(f"- **{role.role_name}**（{role.role_key}）：{role.description}")

        lines.extend(["", "## 4. 功能需求"])

        for index, item in enumerate(prd.functional_requirements, start=1):
            lines.extend(
                [
                    f"### 4.{index} {item.title}（{item.priority}）",
                    f"- **需求 Key**：{item.requirement_key}",
                    f"- **描述**：{item.description}",
                    f"- **输入**：{item.input_description}",
                    f"- **输出**：{item.output_description}",
                    "- **前置条件**：" + self._join_list(item.preconditions),
                    "- **后置条件**：" + self._join_list(item.postconditions),
                    "",
                ]
            )

        lines.extend(["## 5. 业务规则"])

        for item in prd.business_rules:
            lines.extend(
                [
                    f"- **{item.title}**（{item.rule_key}）",
                    f"  - 说明：{item.description}",
                    f"  - 触发条件：{item.trigger_condition}",
                    f"  - 期望结果：{item.expected_result}",
                ]
            )

        lines.extend(["", "## 6. 状态规则"])

        if prd.state_rules:
            for item in prd.state_rules:
                lines.extend(
                    [
                        f"- **{item.state_name}**（{item.state_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 可流转至：{self._join_list(item.allowed_transitions)}",
                    ]
                )
        else:
            lines.append("- 当前模块未识别出明确状态机。")

        lines.extend(["", "## 7. 页面需求"])

        if prd.page_requirements:
            for item in prd.page_requirements:
                lines.extend(
                    [
                        f"- **{item.page_name}**（{item.page_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 主要组件：{self._join_list(item.main_components)}",
                        f"  - 用户操作：{self._join_list(item.user_actions)}",
                    ]
                )
        else:
            lines.append("- 当前模块暂无独立页面需求。")

        lines.extend(["", "## 8. 数据需求"])

        for item in prd.data_requirements:
            lines.extend(
                [
                    f"- **{item.entity_name}**（{item.entity_key}）",
                    f"  - 说明：{item.description}",
                    f"  - 关键字段：{self._join_list(item.important_fields)}",
                    f"  - 约束：{self._join_list(item.constraints)}",
                ]
            )

        lines.extend(["", "## 9. 接口需求"])

        for item in prd.api_requirements:
            lines.extend(
                [
                    f"- **{item.api_name}**（{item.api_key}）",
                    f"  - 方法：{item.method}",
                    f"  - 路径建议：`{item.path_suggestion}`",
                    f"  - 说明：{item.description}",
                    f"  - 请求摘要：{item.request_summary}",
                    f"  - 响应摘要：{item.response_summary}",
                ]
            )

        lines.extend(["", "## 10. 权限需求"])

        for item in prd.permission_requirements:
            lines.extend(
                [
                    f"- **{item.permission_name}**（{item.permission_key}）",
                    f"  - 说明：{item.description}",
                    f"  - 适用角色：{self._join_list(item.roles)}",
                ]
            )

        lines.extend(["", "## 11. 异常场景"])

        for item in prd.exception_scenarios:
            lines.extend(
                [
                    f"- **{item.title}**（{item.scenario_key}）",
                    f"  - 场景：{item.description}",
                    f"  - 处理策略：{item.handling_strategy}",
                ]
            )

        lines.extend(["", "## 12. 验收标准"])

        for item in prd.acceptance_criteria:
            lines.extend(
                [
                    f"- **{item.criterion_key}**：{item.description}",
                    f"  - 验证方式：{item.verification_method}",
                ]
            )

        lines.extend(["", "## 13. 非功能需求"])

        for item in prd.non_functional_requirements:
            lines.extend(
                [
                    f"- **{item.category}**（{item.nfr_key}）",
                    f"  - 要求：{item.description}",
                    f"  - 目标：{item.target}",
                ]
            )

        lines.extend(["", "## 14. 后续工程资产生成提示"])

        hints = prd.downstream_artifact_hints

        lines.extend(
            [
                "### 14.1 业务流程生成提示",
                self._bullet_list(hints.business_flow_hints),
                "",
                "### 14.2 页面规范生成提示",
                self._bullet_list(hints.page_spec_hints),
                "",
                "### 14.3 数据模型生成提示",
                self._bullet_list(hints.data_model_hints),
                "",
                "### 14.4 接口契约生成提示",
                self._bullet_list(hints.api_contract_hints),
                "",
                "### 14.5 测试规格生成提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 14.6 开发交接包生成提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "## 15. 开发注意事项",
                self._bullet_list(prd.development_notes),
                "",
                "## 16. 测试注意事项",
                self._bullet_list(prd.testing_notes),
                "",
                "## 17. 分析摘要",
                prd.analysis_summary,
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