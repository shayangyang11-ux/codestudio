from __future__ import annotations

import json
from typing import Any, Callable

from app.schemas.agent_protocol import AgentTaskRequest


class PromptTemplateRenderer:
    def resolve_system_prompt(
        self,
        request: AgentTaskRequest,
        fallback_system_prompt: str,
    ) -> str:
        snapshot = self._extract_snapshot(request)

        if snapshot and snapshot.get("system_prompt"):
            return str(snapshot["system_prompt"])

        return fallback_system_prompt

    def render_user_prompt(
        self,
        request: AgentTaskRequest,
        fallback_user_prompt_builder: Callable[[], str],
    ) -> str:
        snapshot = self._extract_snapshot(request)

        if not snapshot or not snapshot.get("user_prompt_template"):
            return fallback_user_prompt_builder()

        template = str(snapshot["user_prompt_template"])

        variables = self._build_variables(request)

        for key, value in variables.items():
            template = template.replace("{{" + key + "}}", value)

        return template

    def _extract_snapshot(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, Any] | None:
        if getattr(request, "prompt_template", None):
            value = request.prompt_template
            if hasattr(value, "model_dump"):
                return value.model_dump()
            if isinstance(value, dict):
                return value

        input_payload = request.input or {}

        value = input_payload.get("prompt_template")

        if isinstance(value, dict):
            return value

        return None

    def _build_variables(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, str]:
        context_package = request.context_package or {}
        input_payload = request.input or {}

        current_module = (
            input_payload.get("current_module")
            or context_package.get("current_module")
            or context_package.get("module")
            or {}
        )

        requirement_text = (
            input_payload.get("requirement_text")
            or input_payload.get("supplement")
            or request.requirement_text
            or ""
        )

        return {
            "task_id": str(request.task_id or ""),
            "project_id": str(request.project_id or ""),
            "project_code": str(request.project_code or ""),
            "module_id": str(request.module_id or ""),
            "task_type": str(request.task_type or ""),
            "requirement_text": str(requirement_text),
            "current_module_json": json.dumps(current_module, ensure_ascii=False, default=str),
            "context_package_json": json.dumps(context_package, ensure_ascii=False, default=str),
            "input_json": json.dumps(input_payload, ensure_ascii=False, default=str),
        }