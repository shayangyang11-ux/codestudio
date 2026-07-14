from __future__ import annotations

from typing import Any, TypedDict

from app.schemas.agent_protocol import AgentTaskRequest, AgentTaskResponse


class RequirementGraphState(TypedDict, total=False):
    request: AgentTaskRequest
    effective_request: AgentTaskRequest
    response: AgentTaskResponse

    artifact_type: str
    task_type: str

    raw_context_package: dict[str, Any]
    compact_context: dict[str, Any]
    dependency_status: dict[str, Any]
    context_quality: dict[str, Any]

    system_prompt: str
    user_prompt: str
    output_schema_name: str
    output_json_schema: dict[str, Any]

    prompt_debug: dict[str, Any]
    prompt_debug_only: bool

    llm_raw_text: str
    llm_usage: dict[str, Any]

    json_text: str
    parsed_json: dict[str, Any]
    json_parse_valid: bool
    json_repaired: bool
    repair_attempts: int

    parsed_model: Any
    artifact: Any

    schema_valid: bool
    quality_score: int
    quality_passed: bool
    quality_notes: list[str]

    generation_mode: str
    fallback_used: bool

    error_code: str
    error_message: str

    node_trace: list[dict[str, Any]]

    run_started_at: str
    run_finished_at: str
    workflow_metadata: dict[str, Any]