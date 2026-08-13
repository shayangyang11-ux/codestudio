from __future__ import annotations

import json
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.graphs.requirement.context.requirement_asset_contract_policy import (
    RequirementAssetContractPolicy,
)


class RequirementPromptContextBuilder:
    """
    面向最终版 LangGraph 工作流的 Prompt 上下文构建器。

    目标：
    1. 优先识别 compact_context。
    2. 统一输出任务信息、当前模块、用户补充需求、依赖状态、上下文质量。
    3. 统一输出上游资产摘要。
    4. 避免每个 Agent 各自重复处理 context_package。
    """

    def __init__(self) -> None:
        self.asset_contract_policy = RequirementAssetContractPolicy()

    def build_common_sections(
        self,
        *,
        request: AgentTaskRequest,
        artifact_order: list[str],
        artifact_title_map: dict[str, str] | None = None,
        max_artifact_chars: int = 3500,
    ) -> list[str]:
        context_package = request.context_package or {}
        artifact_title_map = artifact_title_map or {}

        sections: list[str] = []

        sections.extend(
            [
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"module_id: {request.module_id}",
                f"task_type: {request.task_type}",
                "",
                "【当前模块】",
                self._compact_json(
                    self._extract_current_module(context_package),
                    1500,
                ),
                "",
                "【用户补充需求】",
                self._get_requirement_text(request) or "无额外补充需求。",
                "",
            ]
        )

        sections.extend(
            self.asset_contract_policy.build_prompt_sections(
                request=request,
            )
        )
        sections.append("")

        dependency_status = context_package.get("dependency_status")
        if isinstance(dependency_status, dict):
            sections.extend(
                [
                    "【上游依赖状态】",
                    self._compact_json(dependency_status, 1500),
                    "",
                ]
            )

        context_quality = context_package.get("context_quality")
        if isinstance(context_quality, dict):
            sections.extend(
                [
                    "【上下文质量】",
                    self._compact_json(context_quality, 1500),
                    "",
                ]
            )

        artifacts = self._extract_artifacts(context_package)

        for artifact_type in artifact_order:
            title = artifact_title_map.get(artifact_type, artifact_type)
            artifact = artifacts.get(artifact_type, {})

            sections.extend(
                [
                    f"【{title}】",
                    self._compact_json(artifact, max_artifact_chars),
                    "",
                ]
            )

        return sections

    def build_json_output_rules(self) -> list[str]:
        return [
            "请只输出符合 JSON Schema 的 JSON 对象。",
            "不要输出 markdown 字段。",
            "不要输出 JSON 代码块。",
            "不要输出解释性文字。",
        ]

    def _extract_current_module(
        self,
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        current_module = context_package.get("current_module")
        if isinstance(current_module, dict):
            return current_module

        module = context_package.get("module")
        if isinstance(module, dict):
            return module

        return {
            "module_id": None,
            "module_key": "",
            "module_name": "",
            "module_type": "",
            "module_path": "",
        }

    def _extract_artifacts(
        self,
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            return artifacts

        if isinstance(artifacts, list):
            result: dict[str, Any] = {}

            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                artifact_type = artifact.get("artifact_type") or artifact.get("type")
                if artifact_type:
                    if isinstance(artifact.get("content"), dict):
                        result[str(artifact_type)] = artifact["content"]
                    else:
                        result[str(artifact_type)] = artifact

            return result

        return {}

    def _get_requirement_text(
        self,
        request: AgentTaskRequest,
    ) -> str:
        if request.input.get("requirement_text"):
            return str(request.input["requirement_text"])

        if request.input.get("supplement"):
            return str(request.input["supplement"])

        if request.requirement_text:
            return request.requirement_text

        return ""

    def _compact_json(
        self,
        value: Any,
        max_chars: int,
    ) -> str:
        text = json.dumps(value or {}, ensure_ascii=False, default=str)

        if len(text) <= max_chars:
            return text

        return text[:max_chars] + f"\n...【上下文已裁剪，原长度 {len(text)}】"

