from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel
from datetime import datetime, timezone
from app.graphs.requirement.context.artifact_context_compactor import (
    ArtifactContextCompactor,
)
from app.graphs.requirement.context.context_quality_checker import (
    ContextQualityChecker,
)
from app.graphs.requirement.metadata.workflow_run_metadata_builder import (
    WorkflowRunMetadataBuilder,
)
from app.graphs.requirement.context.prompt_debugger import RequirementPromptDebugger
from app.graphs.requirement.json.json_repair_service import JsonRepairService
from app.graphs.requirement.json.json_text_extractor import JsonTextExtractor
from app.graphs.requirement.state import RequirementGraphState
from app.schemas.agent_protocol import AgentTaskRequest, AgentTaskResponse


class LlmArtifactWorkflowNodes:
    """
    通用 LLM 资产生成最终工作流节点。

    标准链路：
    prepare_context
    -> compact_context
    -> dependency_check
    -> build_prompt
    -> llm_generate
    -> json_parse
    -> json_repair
    -> schema_validate
    -> quality_check
    -> fallback
    -> build_artifact
    -> finalize
    """

    def __init__(
        self,
        *,
        agent: Any,
        artifact_type: str,
        prompt_filename: str,
        output_schema_name: str,
        output_json_schema: dict[str, Any],
        llm_output_model: type[BaseModel],
        artifact_model: type[BaseModel],
        workflow_name: str,
    ) -> None:
        self.agent = agent
        self.artifact_type = artifact_type
        self.prompt_filename = prompt_filename
        self.output_schema_name = output_schema_name
        self.output_json_schema = output_json_schema
        self.llm_output_model = llm_output_model
        self.artifact_model = artifact_model
        self.workflow_name = workflow_name

        self.context_compactor = ArtifactContextCompactor()
        self.context_quality_checker = ContextQualityChecker()
        self.prompt_debugger = RequirementPromptDebugger()
        self.workflow_metadata_builder = WorkflowRunMetadataBuilder()
        self.json_text_extractor = JsonTextExtractor()
        self.json_repair_service = JsonRepairService()


    async def prepare_context(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        state["artifact_type"] = self.artifact_type
        state["task_type"] = request.task_type
        state["raw_context_package"] = request.context_package or {}
        state["generation_mode"] = "PENDING"
        state["fallback_used"] = False
        state["json_parse_valid"] = False
        state["json_repaired"] = False
        state["repair_attempts"] = 0
        state["schema_valid"] = False
        state["quality_score"] = 0
        state["quality_passed"] = False
        state["quality_notes"] = []
        state["prompt_debug"] = {}
        state["prompt_debug_only"] = self._is_prompt_debug_only(request)
        state["node_trace"] = []

        self._trace(state, "prepare_context", "SUCCESS")
        return state

    async def compact_context(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        try:
            compact_context = self.context_compactor.compact(
                request=request,
                artifact_type=self.artifact_type,
            )

            state["compact_context"] = compact_context
            state["dependency_status"] = compact_context.get("dependency_status", {})
            state["context_quality"] = self.context_quality_checker.check(compact_context)

            if hasattr(request, "model_copy"):
                state["effective_request"] = request.model_copy(
                    update={
                        "context_package": compact_context,
                    }
                )
            else:
                state["effective_request"] = request

            self._trace(state, "compact_context", "SUCCESS")

        except Exception as exc:
            state["error_code"] = f"{self.artifact_type}_CONTEXT_COMPACT_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "compact_context", "FAILED", state["error_message"])

        return state

    async def dependency_check(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        dependency_status = state.get("dependency_status", {})
        context_quality = state.get("context_quality", {})

        missing = dependency_status.get("missing_required_artifacts", [])

        state["dependency_status"] = {
            **dependency_status,
            "checked": True,
            "blocking": False,
            "warning": bool(missing),
            "message": (
                f"缺少上游资产：{', '.join(missing)}。系统将继续生成，但结果可能偏保守。"
                if missing
                else "上游依赖资产检查通过。"
            ),
            "context_quality_score": context_quality.get("score"),
        }

        self._trace(state, "dependency_check", "SUCCESS")
        return state

    async def build_prompt(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        if state.get("error_code"):
            return state

        request = state.get("effective_request") or state["request"]

        try:
            state["system_prompt"] = self.agent._load_prompt(self.prompt_filename)
            state["user_prompt"] = self.agent._build_user_prompt(request)
            state["output_schema_name"] = self.output_schema_name
            state["output_json_schema"] = self.output_json_schema

            state["prompt_debug"] = self.prompt_debugger.build_debug_info(
                artifact_type=self.artifact_type,
                system_prompt=state["system_prompt"],
                user_prompt=state["user_prompt"],
                compact_context=state.get("compact_context", {}),
                dependency_status=state.get("dependency_status", {}),
                context_quality=state.get("context_quality", {}),
            )

            self._trace(
                state,
                "build_prompt",
                "SUCCESS",
                f"user_prompt_chars={state['prompt_debug'].get('user_prompt_chars')}",
            )

        except Exception as exc:
            state["error_code"] = f"{self.artifact_type}_PROMPT_BUILD_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "build_prompt", "FAILED", state["error_message"])

        return state
    
    def route_after_build_prompt(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("error_code"):
            return self._fallback_node_name()

        if state.get("prompt_debug_only"):
            return self._prompt_debug_response_node_name()

        return self._llm_generate_node_name()
    async def build_prompt_debug_response(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        prompt_debug = state.get("prompt_debug", {})
        compact_context = state.get("compact_context", {})
        dependency_status = state.get("dependency_status", {})
        context_quality = state.get("context_quality", {})

        content = {
            "artifact_type": self.artifact_type,
            "workflow": self.workflow_name,
            "prompt_debug_only": True,
            "prompt_debug": prompt_debug,
            "dependency_status": dependency_status,
            "context_quality": context_quality,
            "compact_context_profile": compact_context.get("context_profile", {}),
        }

        state["response"] = AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type=f"{self.artifact_type}_PROMPT_DEBUG",
            content=content,
            content_markdown=self._build_prompt_debug_markdown(content),
            status="SUCCESS",
            metadata={
                "graph_name": "requirement_graph",
                "workflow": self.workflow_name,
                "generation_mode": "PROMPT_DEBUG_ONLY",
                "prompt_debug": prompt_debug,
                "dependency_status": dependency_status,
                "context_quality": context_quality,
            },
        )

        state["generation_mode"] = "PROMPT_DEBUG_ONLY"
        self._trace(state, "build_prompt_debug_response", "SUCCESS")
        return state

    def _build_prompt_debug_markdown(
        self,
        content: dict[str, Any],
    ) -> str:
        prompt_debug = content.get("prompt_debug", {})
        dependency_status = content.get("dependency_status", {})
        context_quality = content.get("context_quality", {})

        lines = [
            f"# {self.artifact_type} Prompt Debug",
            "",
            "## 1. Prompt 长度统计",
            f"- system_prompt_chars: {prompt_debug.get('system_prompt_chars')}",
            f"- user_prompt_chars: {prompt_debug.get('user_prompt_chars')}",
            f"- total_prompt_chars: {prompt_debug.get('total_prompt_chars')}",
            f"- compact_context_chars: {prompt_debug.get('compact_context_chars')}",
            "",
            "## 2. 上游依赖状态",
            f"```json\n{json.dumps(dependency_status, ensure_ascii=False, indent=2, default=str)}\n```",
            "",
            "## 3. 上下文质量",
            f"```json\n{json.dumps(context_quality, ensure_ascii=False, indent=2, default=str)}\n```",
            "",
            "## 4. Prompt 章节",
            f"```json\n{json.dumps(prompt_debug.get('user_sections', []), ensure_ascii=False, indent=2)}\n```",
            "",
            "## 5. 检查提示",
            f"```json\n{json.dumps(prompt_debug.get('warnings', []), ensure_ascii=False, indent=2)}\n```",
            "",
            "## 6. Prompt 开头预览",
            "```text",
            str(prompt_debug.get("user_prompt_preview_head", "")),
            "```",
        ]

        return "\n".join(lines)
    
    async def llm_generate(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        if state.get("error_code"):
            return state

        try:
            llm_result = await self.agent.llm_service.generate_structured_json(
                system_prompt=state["system_prompt"],
                user_prompt=state["user_prompt"],
                output_schema_name=state["output_schema_name"],
                output_json_schema=state["output_json_schema"],
                validate_json=False,
            )

            state["llm_raw_text"] = llm_result.text
            state["llm_usage"] = llm_result.usage
            state["generation_mode"] = "LLM"

            self._trace(state, "llm_generate", "SUCCESS")

        except Exception as exc:
            state["error_code"] = f"{self.artifact_type}_LLM_GENERATE_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "llm_generate", "FAILED", state["error_message"])

        return state

    async def json_parse(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        if state.get("error_code"):
            return state

        try:
            json_text = self.json_text_extractor.extract(state.get("llm_raw_text", ""))
            parsed_json = json.loads(json_text)

            if not isinstance(parsed_json, dict):
                raise RuntimeError("LLM output JSON root is not object.")

            state["json_text"] = json_text
            state["parsed_json"] = parsed_json
            state["json_parse_valid"] = True
            state["json_repaired"] = False

            self._trace(state, "json_parse", "SUCCESS")

        except Exception as exc:
            state["json_parse_valid"] = False
            state["error_code"] = f"{self.artifact_type}_JSON_PARSE_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "json_parse", "FAILED", state["error_message"])

        return state

    def route_after_json_parse(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("json_parse_valid"):
            return self._schema_validate_node_name()

        return self._json_repair_node_name()

    async def json_repair(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        try:
            repaired_text, parsed_json, attempts = self.json_repair_service.repair(
                state.get("llm_raw_text", "")
            )

            state["json_text"] = repaired_text
            state["parsed_json"] = parsed_json
            state["json_parse_valid"] = True
            state["json_repaired"] = True
            state["repair_attempts"] = len(attempts)
            state["generation_mode"] = "REPAIRED"

            state.pop("error_code", None)
            state.pop("error_message", None)

            self._trace(
                state,
                "json_repair",
                "SUCCESS",
                "；".join(attempts),
            )

        except Exception as exc:
            state["error_code"] = f"{self.artifact_type}_JSON_REPAIR_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "json_repair", "FAILED", state["error_message"])

        return state

    def route_after_json_repair(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("json_parse_valid"):
            return self._schema_validate_node_name()

        return self._fallback_node_name()

    async def schema_validate(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        if state.get("error_code"):
            return state

        try:
            parsed_json = state.get("parsed_json")
            if not isinstance(parsed_json, dict):
                raise RuntimeError("parsed_json is missing or not object.")

            parsed = self.llm_output_model.model_validate(parsed_json)

            state["parsed_model"] = parsed
            state["schema_valid"] = True

            if state.get("generation_mode") not in {"REPAIRED"}:
                state["generation_mode"] = "LLM"

            state["fallback_used"] = False

            self._trace(state, "schema_validate", "SUCCESS")

        except Exception as exc:
            state["schema_valid"] = False
            state["error_code"] = f"{self.artifact_type}_SCHEMA_VALIDATE_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "schema_validate", "FAILED", state["error_message"])

        return state

    def route_after_schema_validate(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("schema_valid"):
            return self._quality_check_node_name()

        return self._fallback_node_name()

    async def quality_check(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        if state.get("error_code"):
            return state

        try:
            parsed = state.get("parsed_model")

            if not isinstance(parsed, self.llm_output_model):
                raise RuntimeError(
                    f"parsed_model is not {self.llm_output_model.__name__}."
                )

            score, notes = self._evaluate_artifact_quality(parsed)

            state["quality_score"] = score
            state["quality_notes"] = notes
            state["quality_passed"] = score >= 60

            if not state["quality_passed"]:
                state["error_code"] = f"{self.artifact_type}_QUALITY_CHECK_FAILED"
                state["error_message"] = "；".join(notes)

            self._trace(
                state,
                "quality_check",
                "SUCCESS" if state["quality_passed"] else "FAILED",
                f"score={score}; " + "；".join(notes),
            )

        except Exception as exc:
            state["quality_score"] = 0
            state["quality_passed"] = False
            state["quality_notes"] = [f"{type(exc).__name__}: {exc}"]
            state["error_code"] = f"{self.artifact_type}_QUALITY_CHECK_FAILED"
            state["error_message"] = f"{type(exc).__name__}: {exc}"
            self._trace(state, "quality_check", "FAILED", state["error_message"])

        return state

    def route_after_quality_check(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("quality_passed"):
            return self._build_artifact_node_name()

        return self._fallback_node_name()

    async def fallback(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        error_code = state.get("error_code", f"{self.artifact_type}_UNKNOWN_ERROR")
        error_message = state.get("error_message", "Unknown artifact generation error.")

        fallback_error = RuntimeError(f"{error_code}: {error_message}")

        parsed = self.agent._build_fallback_output(
            request=request,
            error=fallback_error,
        )

        state["parsed_model"] = parsed
        state["generation_mode"] = "FALLBACK"
        state["fallback_used"] = True
        state["schema_valid"] = True
        state["quality_score"] = 60
        state["quality_passed"] = True
        state["quality_notes"] = [
            "已启用兜底资产，保证下游流程可继续执行。",
            f"兜底原因：{error_code}",
        ]

        self._trace(state, "fallback", "SUCCESS", f"{error_code}: {error_message}")
        return state

    async def build_artifact(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        try:
            parsed = state.get("parsed_model")

            if not isinstance(parsed, self.llm_output_model):
                raise RuntimeError(
                    f"parsed_model is not {self.llm_output_model.__name__}."
                )

            markdown = self.agent._build_markdown(parsed)

            artifact_payload = parsed.model_dump()
            artifact_payload["markdown"] = markdown

            artifact = self.artifact_model.model_validate(artifact_payload)

            state["artifact"] = artifact
            state["response"] = self._build_success_response(
                request=request,
                artifact=artifact,
                node_name=self._build_artifact_node_name(),
                extra_metadata={
                    "workflow": self.workflow_name,
                    "generation_mode": state.get("generation_mode", "UNKNOWN"),
                    "fallback_used": state.get("fallback_used", False),
                    "json_repaired": state.get("json_repaired", False),
                    "repair_attempts": state.get("repair_attempts", 0),
                    "schema_valid": state.get("schema_valid", False),
                    "quality_score": state.get("quality_score", 0),
                    "quality_passed": state.get("quality_passed", False),
                    "quality_notes": state.get("quality_notes", []),
                    "prompt_debug": state.get("prompt_debug", {}),
                    "context_quality": state.get("context_quality", {}),
                    "dependency_status": state.get("dependency_status", {}),
                    "llm_usage": state.get("llm_usage", {}),
                    "error_code_before_fallback": state.get("error_code"),
                    "error_message_before_fallback": state.get("error_message"),
                },
            )

            self._trace(state, "build_artifact", "SUCCESS")

        except Exception as exc:
            state["response"] = self._build_failed_response(
                request=request,
                error_code=f"{self.artifact_type}_ARTIFACT_BUILD_FAILED",
                error_message=f"{type(exc).__name__}: {exc}",
            )
            self._trace(state, "build_artifact", "FAILED", f"{type(exc).__name__}: {exc}")

        return state

    async def finalize(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        state["run_finished_at"] = self._now_iso()
        self._trace(state, "finalize", "SUCCESS")

        workflow_metadata = self.workflow_metadata_builder.build(
            state=state,
            workflow_name=self.workflow_name,
            artifact_type=self.artifact_type,
        )

        state["workflow_metadata"] = workflow_metadata

        response = state.get("response")

        if isinstance(response, AgentTaskResponse):
            metadata = response.metadata or {}

            metadata.update(workflow_metadata)
            metadata["final_artifact_type"] = self.artifact_type
            metadata["final_generation_mode"] = state.get("generation_mode", "UNKNOWN")
            metadata["prompt_debug_only"] = state.get("prompt_debug_only", False)

            state["response"] = response.model_copy(
                update={
                    "metadata": metadata,
                }
            )

        return state

    def route_after_llm_generate(
        self,
        state: RequirementGraphState,
    ) -> str:
        if state.get("error_code"):
            return self._fallback_node_name()

        return self._json_parse_node_name()

    def _evaluate_artifact_quality(
        self,
        parsed: BaseModel,
    ) -> tuple[int, list[str]]:
        payload = parsed.model_dump()
        score = 100
        notes: list[str] = []

        if not payload.get("basic_info"):
            score -= 30
            notes.append("缺少 basic_info。")

        list_fields = [
            key
            for key, value in payload.items()
            if isinstance(value, list)
        ]

        non_empty_list_count = sum(
            1
            for key in list_fields
            if payload.get(key)
        )

        if list_fields and non_empty_list_count == 0:
            score -= 40
            notes.append("主要列表字段均为空。")

        if self.artifact_type == "DATA_MODEL" and not payload.get("entities"):
            score -= 30
            notes.append("DATA_MODEL 缺少 entities。")

        if self.artifact_type == "API_CONTRACT" and not payload.get("endpoints"):
            score -= 30
            notes.append("API_CONTRACT 缺少 endpoints。")

        if self.artifact_type == "TEST_SPEC":
            if not payload.get("functional_test_cases") and not payload.get("api_test_cases"):
                score -= 30
                notes.append("TEST_SPEC 缺少功能或接口测试用例。")

        if self.artifact_type == "DEV_HANDOFF_PACKAGE":
            dev_blueprint = payload.get("dev_blueprint") or {}
            if not dev_blueprint and not payload.get("backend_implementation_items") and not payload.get("frontend_implementation_items"):
                score -= 30
                notes.append("DEV_HANDOFF_PACKAGE 缺少 dev_blueprint 或历史实现清单。")
            elif isinstance(dev_blueprint, dict):
                blueprint_sections = [
                    dev_blueprint.get("pages"),
                    dev_blueprint.get("features"),
                    dev_blueprint.get("apis"),
                    dev_blueprint.get("data_entities"),
                    dev_blueprint.get("acceptance_criteria"),
                ]
                if not any(isinstance(section, list) and section for section in blueprint_sections):
                    score -= 20
                    notes.append("DEV_HANDOFF_PACKAGE 的 dev_blueprint 缺少页面、功能、接口、数据或验收条目。")

            readiness = payload.get("readiness")
            if isinstance(readiness, dict) and readiness.get("readiness_level") == "BLOCKED":
                score -= 10
                notes.append("DEV_HANDOFF_PACKAGE 仍存在 Dev Agent 阻断项。")

        if not payload.get("analysis_summary"):
            score -= 10
            notes.append("缺少 analysis_summary。")

        score = max(0, min(100, score))

        if not notes:
            notes.append("资产质量检查通过。")

        return score, notes

    def _build_success_response(
        self,
        request: AgentTaskRequest,
        artifact: Any,
        node_name: str,
        extra_metadata: dict[str, Any] | None = None,
    ) -> AgentTaskResponse:
        metadata: dict[str, Any] = {
            "graph_name": "requirement_graph",
            "node_name": node_name,
        }

        if extra_metadata:
            metadata.update(extra_metadata)

        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type=artifact.artifact_type,
            content=artifact.model_dump(),
            content_markdown=artifact.markdown,
            status="SUCCESS",
            metadata=metadata,
        )

    def _build_failed_response(
        self,
        request: AgentTaskRequest,
        error_code: str,
        error_message: str,
    ) -> AgentTaskResponse:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="UNKNOWN",
            content={},
            status="FAILED",
            error_code=error_code,
            error_message=error_message,
            metadata={
                "graph_name": "requirement_graph",
                "workflow": self.workflow_name,
                "generation_mode": "FAILED",
            },
        )

    def _trace(
        self,
        state: RequirementGraphState,
        node_name: str,
        status: str,
        message: str | None = None,
    ) -> None:
        trace = state.setdefault("node_trace", [])

        trace.append(
            {
                "sequence": len(trace) + 1,
                "node": node_name,
                "status": status,
                "message": message or "",
                "artifact_type": self.artifact_type,
                "generation_mode": state.get("generation_mode", "UNKNOWN"),
                "timestamp": self._now_iso(),
            }
        )

    def _now_iso(self) -> str:
        return datetime.now(timezone.utc).isoformat()
    
    def _is_prompt_debug_only(
        self,
        request: AgentTaskRequest,
    ) -> bool:
        input_payload = request.input or {}

        return bool(
            input_payload.get("prompt_debug_only")
            or input_payload.get("debug_prompt_only")
            or input_payload.get("dry_run_prompt")
        )
    
    def _node_prefix(self) -> str:
        return self.artifact_type.lower()

    def _compact_context_node_name(self) -> str:
        return f"{self._node_prefix()}_compact_context"

    def _dependency_check_node_name(self) -> str:
        return f"{self._node_prefix()}_dependency_check"

    def _build_prompt_node_name(self) -> str:
        return f"{self._node_prefix()}_build_prompt"

    def _llm_generate_node_name(self) -> str:
        return f"{self._node_prefix()}_llm_generate"
    
    def _prompt_debug_response_node_name(self) -> str:
        return f"{self._node_prefix()}_prompt_debug_response"

    def _json_parse_node_name(self) -> str:
        return f"{self._node_prefix()}_json_parse"

    def _json_repair_node_name(self) -> str:
        return f"{self._node_prefix()}_json_repair"

    def _schema_validate_node_name(self) -> str:
        return f"{self._node_prefix()}_schema_validate"

    def _quality_check_node_name(self) -> str:
        return f"{self._node_prefix()}_quality_check"

    def _fallback_node_name(self) -> str:
        return f"{self._node_prefix()}_fallback"

    def _build_artifact_node_name(self) -> str:
        return f"{self._node_prefix()}_build_artifact"

    def _finalize_node_name(self) -> str:
        return f"{self._node_prefix()}_finalize"


