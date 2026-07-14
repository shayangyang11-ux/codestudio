from __future__ import annotations

import json
from typing import Any

from app.graphs.requirement.context.artifact_context_profile import (
    ArtifactContextProfile,
    get_context_profile,
)
from app.schemas.agent_protocol import AgentTaskRequest


class ArtifactContextCompactor:
    """
    面向需求资产生成的上下文压缩器。

    目标：
    1. 按目标 artifact_type 选择必要上游资产。
    2. 删除 markdown、metadata、content_markdown 等长文本和运行态字段。
    3. 对列表和字典做递归裁剪。
    4. 保留结构化字段，避免简单字符串硬截断导致关键信息丢失。
    """

    DROP_KEYS = {
        "markdown",
        "content_markdown",
        "metadata",
        "raw",
        "raw_response",
        "llm_raw_text",
        "debug",
        "trace",
        "node_trace",
        "created_at",
        "updated_at",
        "createdBy",
        "updatedBy",
    }

    PRIORITY_KEYS = {
        "basic_info",
        "modules",
        "requirements",
        "functional_requirements",
        "business_rules",
        "flow_steps",
        "pages",
        "components",
        "fields",
        "entities",
        "relationships",
        "indexes",
        "constraints",
        "status_models",
        "endpoints",
        "permission_requirements",
        "test_scope",
        "acceptance_criteria",
        "functional_test_cases",
        "api_test_cases",
        "development_objectives",
        "module_boundaries",
        "frontend_implementation_items",
        "backend_implementation_items",
        "api_implementation_items",
        "data_implementation_items",
        "downstream_hints",
        "analysis_summary",
    }

    def compact(
        self,
        *,
        request: AgentTaskRequest,
        artifact_type: str,
    ) -> dict[str, Any]:
        profile = get_context_profile(artifact_type)
        context_package = request.context_package or {}
        input_payload = request.input or {}

        artifacts = self._extract_artifacts(context_package)

        compact_artifacts: dict[str, Any] = {}
        missing_required: list[str] = []

        for upstream_type in profile.required_artifacts:
            artifact = artifacts.get(upstream_type)

            if not artifact:
                missing_required.append(upstream_type)
                compact_artifacts[upstream_type] = {}
                continue

            compact_artifacts[upstream_type] = self._compact_artifact(
                artifact=artifact,
                max_chars=profile.max_artifact_chars,
            )

        for upstream_type in profile.optional_artifacts:
            artifact = artifacts.get(upstream_type)
            if artifact:
                compact_artifacts[upstream_type] = self._compact_artifact(
                    artifact=artifact,
                    max_chars=profile.max_artifact_chars,
                )

        compact_context = {
            "context_profile": {
                "artifact_type": profile.artifact_type,
                "required_artifacts": list(profile.required_artifacts),
                "optional_artifacts": list(profile.optional_artifacts),
                "max_artifact_chars": profile.max_artifact_chars,
                "max_total_chars": profile.max_total_chars,
            },
            "task": {
                "task_id": request.task_id,
                "project_id": request.project_id,
                "project_code": request.project_code,
                "module_id": request.module_id,
                "task_type": request.task_type,
                "target_artifact_type": artifact_type,
            },
            "current_module": self._compact_value(
                self._extract_current_module(context_package, input_payload),
                max_depth=3,
                max_list_items=20,
            ),
            "requirement_text": self._get_requirement_text(request),
            "artifacts": compact_artifacts,
            "dependency_status": {
                "missing_required_artifacts": missing_required,
                "has_missing_required_artifacts": len(missing_required) > 0,
            },
        }

        compact_context["context_quality"] = self._build_context_quality(
            compact_context=compact_context,
            profile=profile,
        )

        return self._enforce_total_budget(
            compact_context=compact_context,
            profile=profile,
        )

    def _compact_artifact(
        self,
        *,
        artifact: Any,
        max_chars: int,
    ) -> dict[str, Any]:
        value = artifact

        if isinstance(value, dict) and isinstance(value.get("content"), dict):
            value = value["content"]

        compacted = self._compact_value(
            value,
            max_depth=5,
            max_list_items=8,
        )

        return self._enforce_object_chars(compacted, max_chars)

    def _compact_value(
        self,
        value: Any,
        *,
        max_depth: int,
        max_list_items: int,
    ) -> Any:
        if max_depth <= 0:
            return self._short_text(value, 500)

        if value is None:
            return None

        if isinstance(value, (str, int, float, bool)):
            if isinstance(value, str):
                return self._short_text(value, 1200)
            return value

        if isinstance(value, list):
            return [
                self._compact_value(
                    item,
                    max_depth=max_depth - 1,
                    max_list_items=max_list_items,
                )
                for item in value[:max_list_items]
            ]

        if isinstance(value, dict):
            result: dict[str, Any] = {}

            ordered_keys = sorted(
                value.keys(),
                key=lambda key: 0 if key in self.PRIORITY_KEYS else 1,
            )

            for key in ordered_keys:
                if key in self.DROP_KEYS:
                    continue

                result[key] = self._compact_value(
                    value[key],
                    max_depth=max_depth - 1,
                    max_list_items=max_list_items,
                )

            return result

        return self._short_text(value, 500)

    def _enforce_object_chars(
        self,
        value: dict[str, Any],
        max_chars: int,
    ) -> dict[str, Any]:
        text = json.dumps(value, ensure_ascii=False, default=str)

        if len(text) <= max_chars:
            return value

        result = dict(value)
        result["_compact_note"] = f"该资产摘要已按 {max_chars} 字符预算裁剪。"

        while len(json.dumps(result, ensure_ascii=False, default=str)) > max_chars:
            removable_key = self._find_low_priority_key(result)

            if not removable_key:
                break

            result.pop(removable_key, None)

        return result

    def _find_low_priority_key(
        self,
        value: dict[str, Any],
    ) -> str | None:
        for key in reversed(list(value.keys())):
            if key not in self.PRIORITY_KEYS and not key.startswith("_"):
                return key

        for key in reversed(list(value.keys())):
            if key not in {"basic_info", "_compact_note"}:
                return key

        return None

    def _enforce_total_budget(
        self,
        *,
        compact_context: dict[str, Any],
        profile: ArtifactContextProfile,
    ) -> dict[str, Any]:
        text = json.dumps(compact_context, ensure_ascii=False, default=str)

        if len(text) <= profile.max_total_chars:
            compact_context["context_quality"]["total_chars"] = len(text)
            return compact_context

        compact_context["context_quality"]["budget_trimmed"] = True

        artifacts = compact_context.get("artifacts", {})
        if isinstance(artifacts, dict):
            for artifact_type in reversed(list(artifacts.keys())):
                artifact = artifacts.get(artifact_type)
                if isinstance(artifact, dict):
                    artifacts[artifact_type] = self._enforce_object_chars(
                        artifact,
                        max(1200, profile.max_artifact_chars // 2),
                    )

                text = json.dumps(compact_context, ensure_ascii=False, default=str)
                if len(text) <= profile.max_total_chars:
                    break

        compact_context["context_quality"]["total_chars"] = len(
            json.dumps(compact_context, ensure_ascii=False, default=str)
        )

        return compact_context

    def _build_context_quality(
        self,
        *,
        compact_context: dict[str, Any],
        profile: ArtifactContextProfile,
    ) -> dict[str, Any]:
        artifacts = compact_context.get("artifacts", {})
        missing = compact_context.get("dependency_status", {}).get(
            "missing_required_artifacts",
            [],
        )

        available = [
            artifact_type
            for artifact_type in profile.required_artifacts
            if isinstance(artifacts, dict) and artifacts.get(artifact_type)
        ]

        return {
            "available_required_artifacts": available,
            "missing_required_artifacts": missing,
            "required_artifact_count": len(profile.required_artifacts),
            "available_required_artifact_count": len(available),
            "budget_trimmed": False,
            "total_chars": 0,
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
                    result[str(artifact_type)] = artifact

            return result

        return {}

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

    def _short_text(
        self,
        value: Any,
        max_chars: int,
    ) -> str:
        text = str(value)

        if len(text) <= max_chars:
            return text

        return text[:max_chars] + f"...【已裁剪，原长度 {len(text)}】"