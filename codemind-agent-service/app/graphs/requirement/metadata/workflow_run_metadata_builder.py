from __future__ import annotations

from typing import Any

from app.graphs.requirement.state import RequirementGraphState


class WorkflowRunMetadataBuilder:
    """
    Requirement LangGraph 运行时 metadata 构建器。

    目标：
    1. 把分散在 state 中的调试信息整理成统一结构。
    2. 方便 Java 后端写入 agent_task、agent_execution_log、artifact_version metadata。
    3. 方便前端展示工作流状态、质量评分、JSON 修复情况、上下文质量。
    """

    def build(
        self,
        *,
        state: RequirementGraphState,
        workflow_name: str,
        artifact_type: str,
    ) -> dict[str, Any]:
        node_trace = state.get("node_trace", [])
        failed_nodes = self._filter_nodes(node_trace, "FAILED")
        warning_nodes = self._filter_nodes(node_trace, "WARNING")

        prompt_debug = state.get("prompt_debug", {})
        dependency_status = state.get("dependency_status", {})
        context_quality = state.get("context_quality", {})

        prompt_debug_only = bool(state.get("prompt_debug_only", False))
        fallback_used = bool(state.get("fallback_used", False))
        generation_mode = state.get("generation_mode", "UNKNOWN")

        return {
            "workflow_run": {
                "workflow_name": workflow_name,
                "artifact_type": artifact_type,
                "task_type": state.get("task_type"),
                "generation_mode": generation_mode,
                "fallback_used": fallback_used,
                "prompt_debug_only": prompt_debug_only,
                "run_started_at": state.get("run_started_at"),
                "run_finished_at": state.get("run_finished_at"),
                "node_count": len(node_trace),
                "failed_node_count": len(failed_nodes),
                "warning_node_count": len(warning_nodes),
                "has_failed_node": len(failed_nodes) > 0,
                "has_warning_node": len(warning_nodes) > 0,
            },
            "node_trace": node_trace,
            "node_trace_summary": {
                "executed_nodes": [
                    item.get("node")
                    for item in node_trace
                    if isinstance(item, dict)
                ],
                "failed_nodes": failed_nodes,
                "warning_nodes": warning_nodes,
                "llm_generate_executed": self._has_node(node_trace, "llm_generate"),
                "json_repair_executed": self._has_node(node_trace, "json_repair"),
                "fallback_executed": self._has_node(node_trace, "fallback"),
            },
            "context_result": {
                "dependency_status": dependency_status,
                "context_quality": context_quality,
                "missing_required_artifacts": dependency_status.get(
                    "missing_required_artifacts",
                    [],
                ),
                "has_missing_required_artifacts": dependency_status.get(
                    "has_missing_required_artifacts",
                    False,
                ),
                "context_quality_score": context_quality.get("score"),
                "context_quality_passed": context_quality.get("passed"),
            },
            "prompt_result": {
                "prompt_debug_only": prompt_debug_only,
                "system_prompt_chars": prompt_debug.get("system_prompt_chars"),
                "user_prompt_chars": prompt_debug.get("user_prompt_chars"),
                "total_prompt_chars": prompt_debug.get("total_prompt_chars"),
                "compact_context_chars": prompt_debug.get("compact_context_chars"),
                "warnings": prompt_debug.get("warnings", []),
                "required_section_presence": prompt_debug.get(
                    "required_section_presence",
                    {},
                ),
                "artifact_section_presence": prompt_debug.get(
                    "artifact_section_presence",
                    {},
                ),
            },
            "json_result": {
                "json_parse_valid": state.get("json_parse_valid", False),
                "json_repaired": state.get("json_repaired", False),
                "repair_attempts": state.get("repair_attempts", 0),
                "schema_valid": state.get("schema_valid", False),
            },
            "quality_result": {
                "quality_score": state.get("quality_score", 0),
                "quality_passed": state.get("quality_passed", False),
                "quality_notes": state.get("quality_notes", []),
            },
            "llm_result": {
                "llm_usage": state.get("llm_usage", {}),
                "llm_raw_text_chars": len(state.get("llm_raw_text", "") or ""),
            },
            "error_result": {
                "error_code": state.get("error_code"),
                "error_message": state.get("error_message"),
            },
            "persistence_hint": {
                "should_write_artifact": not prompt_debug_only,
                "should_write_execution_log": True,
                "recommended_task_status": "SUCCESS"
                if not failed_nodes
                else "SUCCESS_WITH_WARNINGS",
                "recommended_generation_mode": generation_mode,
            },
        }

    def _filter_nodes(
        self,
        node_trace: list[dict[str, Any]],
        status: str,
    ) -> list[dict[str, Any]]:
        return [
            item
            for item in node_trace
            if isinstance(item, dict) and item.get("status") == status
        ]

    def _has_node(
        self,
        node_trace: list[dict[str, Any]],
        node_name: str,
    ) -> bool:
        return any(
            isinstance(item, dict) and item.get("node") == node_name
            for item in node_trace
        )