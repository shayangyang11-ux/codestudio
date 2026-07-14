from __future__ import annotations

import json
import re
from typing import Any


class RequirementPromptDebugger:
    """
    Prompt 预览与统计器。

    注意：
    1. 默认不保存完整 prompt，避免日志过长和潜在敏感信息泄露。
    2. 只保存长度、章节、上下文质量、依赖状态和前后片段预览。
    3. 用于验证 compact_context 是否真正进入 prompt。
    """

    REQUIRED_SECTIONS = [
        "任务信息",
        "当前模块",
        "用户补充需求",
        "上游依赖状态",
        "上下文质量",
    ]

    def build_debug_info(
        self,
        *,
        artifact_type: str,
        system_prompt: str,
        user_prompt: str,
        compact_context: dict[str, Any] | None,
        dependency_status: dict[str, Any] | None,
        context_quality: dict[str, Any] | None,
    ) -> dict[str, Any]:
        compact_context = compact_context or {}
        dependency_status = dependency_status or {}
        context_quality = context_quality or {}

        user_sections = self._extract_sections(user_prompt)
        system_sections = self._extract_sections(system_prompt)

        compact_context_text = json.dumps(
            compact_context,
            ensure_ascii=False,
            default=str,
        )

        required_section_presence = {
            section: section in user_sections
            for section in self.REQUIRED_SECTIONS
        }

        artifact_section_presence = self._build_artifact_section_presence(
            compact_context=compact_context,
            user_prompt=user_prompt,
        )

        warnings = self._build_warnings(
            user_prompt=user_prompt,
            compact_context=compact_context,
            required_section_presence=required_section_presence,
            dependency_status=dependency_status,
            context_quality=context_quality,
        )

        return {
            "artifact_type": artifact_type,
            "system_prompt_chars": len(system_prompt),
            "user_prompt_chars": len(user_prompt),
            "total_prompt_chars": len(system_prompt) + len(user_prompt),
            "system_prompt_lines": len(system_prompt.splitlines()),
            "user_prompt_lines": len(user_prompt.splitlines()),
            "compact_context_chars": len(compact_context_text),
            "system_sections": system_sections,
            "user_sections": user_sections,
            "required_section_presence": required_section_presence,
            "artifact_section_presence": artifact_section_presence,
            "dependency_status": dependency_status,
            "context_quality": context_quality,
            "warnings": warnings,
            "user_prompt_preview_head": user_prompt[:800],
            "user_prompt_preview_tail": user_prompt[-500:] if len(user_prompt) > 500 else user_prompt,
        }

    def _extract_sections(
        self,
        prompt: str,
    ) -> list[str]:
        return re.findall(r"【([^】]+)】", prompt or "")

    def _build_artifact_section_presence(
        self,
        *,
        compact_context: dict[str, Any],
        user_prompt: str,
    ) -> dict[str, bool]:
        artifacts = compact_context.get("artifacts", {})
        if not isinstance(artifacts, dict):
            return {}

        result: dict[str, bool] = {}

        for artifact_type in artifacts.keys():
            result[str(artifact_type)] = str(artifact_type) in user_prompt

        return result

    def _build_warnings(
        self,
        *,
        user_prompt: str,
        compact_context: dict[str, Any],
        required_section_presence: dict[str, bool],
        dependency_status: dict[str, Any],
        context_quality: dict[str, Any],
    ) -> list[str]:
        warnings: list[str] = []

        missing_sections = [
            section
            for section, exists in required_section_presence.items()
            if not exists
        ]

        if missing_sections:
            warnings.append(f"Prompt 缺少基础章节：{', '.join(missing_sections)}。")

        missing_artifacts = dependency_status.get("missing_required_artifacts", [])
        if missing_artifacts:
            warnings.append(f"缺少上游资产：{', '.join(missing_artifacts)}。")

        if context_quality.get("score") is not None and int(context_quality.get("score", 0)) < 60:
            warnings.append("上下文质量评分偏低，生成结果可能偏保守。")

        compact_quality = compact_context.get("context_quality", {})
        if isinstance(compact_quality, dict) and compact_quality.get("budget_trimmed"):
            warnings.append("compact_context 发生预算裁剪。")

        if len(user_prompt) > 40000:
            warnings.append("user_prompt 超过 40000 字符，存在输出截断风险。")
        elif len(user_prompt) > 25000:
            warnings.append("user_prompt 超过 25000 字符，建议关注 token 消耗。")

        if not warnings:
            warnings.append("Prompt 结构检查通过。")

        return warnings