
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.business_flow import (
    BUSINESS_FLOW_LLM_JSON_SCHEMA,
    BusinessFlowArtifact,
    BusinessFlowLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.graphs.requirement.context.requirement_asset_contract_policy import (
    RequirementAssetContractPolicy,
)


class BusinessFlowAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()
        self.asset_contract_policy = RequirementAssetContractPolicy()

    async def run(self, request: AgentTaskRequest) -> BusinessFlowArtifact:
        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("business_flow.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="business_flow_output",
            output_json_schema=BUSINESS_FLOW_LLM_JSON_SCHEMA,
        )

        parsed = BusinessFlowLlmOutput.model_validate_json(llm_result.text)
        markdown = self._build_markdown(parsed)

        return BusinessFlowArtifact(
            basic_info=parsed.basic_info,
            participants=parsed.participants,
            main_steps=parsed.main_steps,
            branches=parsed.branches,
            state_transitions=parsed.state_transitions,
            exception_scenarios=parsed.exception_scenarios,
            data_interactions=parsed.data_interactions,
            api_interactions=parsed.api_interactions,
            permission_points=parsed.permission_points,
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
        module_tree = self._extract_artifact(context_package, "MODULE_TREE")

        return "\n".join(
            [
                "请为当前指定模块生成企业级 BUSINESS_FLOW 业务流程资产。",
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
                *self.asset_contract_policy.build_prompt_sections(
                    request=request,
                ),
                "",
                "【用户补充需求】",
                requirement_text or "无额外补充需求。",
                "",
                "【当前模块 PRD】",
                json.dumps(module_prd, ensure_ascii=False, default=str),
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

    def _build_markdown(self, flow: BusinessFlowLlmOutput) -> str:
        info = flow.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块业务流程",
            "",
            "## 1. 流程基本信息",
            f"- **流程 Key**：{info.flow_key}",
            f"- **流程名称**：{info.flow_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **流程目标**：{info.flow_goal}",
            f"- **触发事件**：{info.trigger_event}",
            f"- **主要参与者**：{info.primary_actor}",
            f"- **流程边界**：{info.boundary}",
            "",
            "## 2. 参与角色",
        ]

        if flow.participants:
            for participant in flow.participants:
                lines.append(
                    f"- **{participant.role_name}**（{participant.role_key}）："
                    f"{participant.responsibility}"
                )
        else:
            lines.append("- 未识别出明确参与角色。")

        lines.extend(["", "## 3. 主流程步骤"])

        if flow.main_steps:
            for step in sorted(flow.main_steps, key=lambda item: item.step_order):
                lines.extend(
                    [
                        f"### 3.{step.step_order} {step.step_name}",
                        f"- **步骤 Key**：{step.step_key}",
                        f"- **执行角色**：{step.actor}",
                        f"- **步骤说明**：{step.description}",
                        f"- **输入数据**：{self._join_list(step.input_data)}",
                        f"- **输出数据**：{self._join_list(step.output_data)}",
                        f"- **业务规则**：{self._join_list(step.business_rules)}",
                        f"- **异常点**：{self._join_list(step.exception_points)}",
                        "",
                    ]
                )
        else:
            lines.append("- 未生成主流程步骤。")

        lines.extend(["", "## 4. 分支流程"])

        if flow.branches:
            for branch in flow.branches:
                lines.extend(
                    [
                        f"- **{branch.branch_name}**（{branch.branch_key}）",
                        f"  - 条件：{branch.condition}",
                        f"  - 说明：{branch.description}",
                        f"  - 目标步骤：{branch.target_step_key or '无'}",
                    ]
                )
        else:
            lines.append("- 当前流程未识别出独立分支流程。")

        lines.extend(["", "## 5. 状态流转"])

        if flow.state_transitions:
            for transition in flow.state_transitions:
                lines.append(
                    f"- **{transition.from_state}** --[{transition.event}]--> "
                    f"**{transition.to_state}**：{transition.description}"
                )
        else:
            lines.append("- 当前流程未识别出明确状态流转。")

        lines.extend(["", "## 6. 异常场景与处理"])

        if flow.exception_scenarios:
            for scenario in flow.exception_scenarios:
                lines.extend(
                    [
                        f"- **{scenario.title}**（{scenario.scenario_key}）",
                        f"  - 场景：{scenario.description}",
                        f"  - 处理策略：{scenario.handling_strategy}",
                        f"  - 恢复动作：{scenario.recovery_action}",
                    ]
                )
        else:
            lines.append("- 当前流程未识别出明确异常场景。")

        lines.extend(["", "## 7. 数据交互"])

        if flow.data_interactions:
            for item in flow.data_interactions:
                lines.extend(
                    [
                        f"- **{item.entity_name}**（{item.entity_key}）",
                        f"  - 操作：{item.operation}",
                        f"  - 说明：{item.description}",
                        f"  - 关联步骤：{self._join_list(item.related_step_keys)}",
                    ]
                )
        else:
            lines.append("- 当前流程未识别出明确数据交互。")

        lines.extend(["", "## 8. 接口交互"])

        if flow.api_interactions:
            for item in flow.api_interactions:
                lines.extend(
                    [
                        f"- **{item.api_name}**（{item.api_key}）",
                        f"  - 方法：{item.method}",
                        f"  - 路径建议：`{item.path_suggestion}`",
                        f"  - 说明：{item.description}",
                        f"  - 关联步骤：{self._join_list(item.related_step_keys)}",
                    ]
                )
        else:
            lines.append("- 当前流程未识别出明确接口交互。")

        lines.extend(["", "## 9. 权限控制点"])

        if flow.permission_points:
            for item in flow.permission_points:
                lines.extend(
                    [
                        f"- **{item.permission_name}**（{item.permission_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 适用角色：{self._join_list(item.roles)}",
                        f"  - 关联步骤：{self._join_list(item.related_step_keys)}",
                    ]
                )
        else:
            lines.append("- 当前流程未识别出明确权限控制点。")

        hints = flow.downstream_hints

        lines.extend(
            [
                "",
                "## 10. 下游工程资产生成提示",
                "### 10.1 页面规范提示",
                self._bullet_list(hints.page_spec_hints),
                "",
                "### 10.2 数据模型提示",
                self._bullet_list(hints.data_model_hints),
                "",
                "### 10.3 接口契约提示",
                self._bullet_list(hints.api_contract_hints),
                "",
                "### 10.4 测试规格提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 10.5 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "## 11. 开发注意事项",
                self._bullet_list(flow.development_notes),
                "",
                "## 12. 测试注意事项",
                self._bullet_list(flow.testing_notes),
                "",
                "## 13. 分析摘要",
                flow.analysis_summary,
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

