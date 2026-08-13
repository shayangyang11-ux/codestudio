
from __future__ import annotations

import json
from typing import Any, Literal, TypedDict

from pydantic import BaseModel, Field

try:
    from langgraph.graph import END, StateGraph
except Exception:  # pragma: no cover - import fallback for static checking only
    END = "__end__"
    StateGraph = None  # type: ignore[assignment]

from app.schemas.agent_protocol import AgentTaskRequest
from app.services.llm_service import LlmService


ASSET_CHANGE_OUTPUT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "summary": {"type": "string"},
        "targetArtifactType": {"type": "string"},
        "proposedContentMarkdown": {"type": "string"},
        "proposedPatchOperations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "op": {"type": "string"},
                    "sectionTitle": {"type": "string"},
                    "findText": {"type": "string"},
                    "replaceWith": {"type": "string"},
                    "insertAfter": {"type": "string"},
                    "insertBefore": {"type": "string"},
                    "rationale": {"type": "string"},
                    "confidence": {"type": "number"}
                },
                "required": ["op", "sectionTitle", "rationale"],
                "additionalProperties": True,
            },
        },
        "proposedPatchSummary": {
            "type": "array",
            "items": {"type": "string"},
        },
        "changedSections": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "sectionTitle": {"type": "string"},
                    "changeType": {"type": "string"},
                    "beforeSummary": {"type": "string"},
                    "afterSummary": {"type": "string"},
                },
                "required": ["sectionTitle", "changeType", "beforeSummary", "afterSummary"],
                "additionalProperties": True,
            },
        },
        "downstreamArtifacts": {
            "type": "array",
            "items": {"type": "string"},
        },
        "riskNotes": {
            "type": "array",
            "items": {"type": "string"},
        },
        "reviewChecklist": {
            "type": "array",
            "items": {"type": "string"},
        },
        "confidence": {"type": "number"},
    },
    "required": [
        "summary",
        "targetArtifactType",
        "proposedPatchOperations",
        "proposedPatchSummary",
        "changedSections",
        "downstreamArtifacts",
        "riskNotes",
        "reviewChecklist",
        "confidence",
    ],
    "additionalProperties": True,
}


class AssistantArtifactReviewArtifact(BaseModel):
    artifact_type: str = "ASSISTANT_ARTIFACT_REVIEW"
    summary: str = ""
    riskLevel: str = "MEDIUM"
    conclusion: str = ""
    issues: list[dict[str, Any]] = Field(default_factory=list)
    consistencyChecks: list[dict[str, Any]] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    nextActions: list[str] = Field(default_factory=list)

    targetArtifactType: str | None = None
    proposedContentMarkdown: str | None = None
    proposedPatchOperations: list[dict[str, Any]] = Field(default_factory=list)
    proposedPatchSummary: list[str] = Field(default_factory=list)
    changedSections: list[dict[str, Any]] = Field(default_factory=list)
    downstreamArtifacts: list[str] = Field(default_factory=list)
    riskNotes: list[str] = Field(default_factory=list)
    reviewChecklist: list[str] = Field(default_factory=list)
    confidence: float = 0.0
    markdown: str | None = None


class ArtifactChangeState(TypedDict, total=False):
    request: AgentTaskRequest
    review_input: dict[str, Any]
    instruction: str
    target_artifact_type: str
    current_markdown: str
    current_content: dict[str, Any]
    system_prompt: str
    user_prompt: str
    llm_result: dict[str, Any]
    error: str
    artifact: AssistantArtifactReviewArtifact


class AssistantArtifactReviewAgent:
    """
    LangGraph-based generic asset change agent.

    It is used by the Java AssistantAssetEditProposalService to turn a natural
    language requirement change into patch-first local edits.
    The model should return small patch operations and metadata. Java applies
    those operations to the current full artifact markdown, then stores a full
    artifact_version snapshot for traceability.
    """

    def __init__(self) -> None:
        self.llm = LlmService()
        self.graph = self._build_graph()

    async def run(self, request: AgentTaskRequest) -> AssistantArtifactReviewArtifact:
        initial_state: ArtifactChangeState = {
            "request": request,
        }

        if self.graph is None:
            state = await self._fallback(initial_state)
        else:
            state = await self.graph.ainvoke(initial_state)

        artifact = state.get("artifact")
        if isinstance(artifact, AssistantArtifactReviewArtifact):
            return artifact

        return self._build_safe_fallback_artifact(
            request=request,
            reason="LangGraph workflow did not return an artifact.",
        )

    def _build_graph(self):
        if StateGraph is None:
            return None

        workflow = StateGraph(ArtifactChangeState)
        workflow.add_node("prepare_context", self._prepare_context)
        workflow.add_node("build_prompt", self._build_prompt)
        workflow.add_node("llm_generate", self._llm_generate)
        workflow.add_node("schema_validate", self._schema_validate)
        workflow.add_node("fallback", self._fallback)

        workflow.set_entry_point("prepare_context")
        workflow.add_edge("prepare_context", "build_prompt")
        workflow.add_edge("build_prompt", "llm_generate")
        workflow.add_conditional_edges(
            "llm_generate",
            self._route_after_llm,
            {
                "schema_validate": "schema_validate",
                "fallback": "fallback",
            },
        )
        workflow.add_edge("schema_validate", END)
        workflow.add_edge("fallback", END)
        return workflow.compile()

    async def _prepare_context(self, state: ArtifactChangeState) -> ArtifactChangeState:
        request = state["request"]
        review_input = self._as_dict(
            (request.context_package or {}).get("review_input")
            or request.input
            or {}
        )

        instruction = str(
            review_input.get("instruction")
            or request.requirement_text
            or "请根据用户变更要求修订当前资产。"
        ).strip()

        target_artifact_type = str(
            review_input.get("targetArtifactType")
            or review_input.get("selected_artifact_type")
            or request.input.get("selected_artifact_type")
            or "MODULE_PRD"
        ).strip().upper()

        current_markdown = str(
            review_input.get("currentMarkdown")
            or review_input.get("contentMarkdown")
            or ""
        ).strip()

        current_content = self._as_dict(review_input.get("currentContent"))

        state.update(
            {
                "review_input": review_input,
                "instruction": instruction,
                "target_artifact_type": target_artifact_type,
                "current_markdown": current_markdown,
                "current_content": current_content,
            }
        )
        return state

    async def _build_prompt(self, state: ArtifactChangeState) -> ArtifactChangeState:
        review_input = state.get("review_input", {})
        current_markdown = self._trim(state.get("current_markdown", ""), 18000)
        current_content = self._trim_json(state.get("current_content", {}), 6000)
        requirement_change = self._trim_json(
            review_input.get("requirementChangeProposal") or {},
            5000,
        )
        target_node = self._trim_json(review_input.get("targetNode") or {}, 2000)
        current_artifact = self._trim_json(review_input.get("currentArtifact") or {}, 2000)
        related_artifacts = self._trim_json(review_input.get("relatedArtifacts") or [], 12000)

        system_prompt = "\n".join(
            [
                "你是 CodeMind Studio 的 LangGraph 资产变更 Agent。",
                "当前使用 Patch-first 局部修订模式。你的首要任务不是重写整份资产，而是输出可由 Java 后端应用的局部 patch operations。",
                "每个 patch operation 应尽量包含 findText 和 replaceWith，findText 必须来自当前资产 Markdown 上下文中的原文片段。",
                "只有当无法稳定表达为局部 patch 时，才允许附带 proposedContentMarkdown 作为兜底全文草案。",
                "保持原文结构、标题层级和工程语气，尽量只修改必要段落。",
                "如果 relatedArtifacts 中提供了上游资产，请只同步上游变化影响到当前资产的相关段落。",
                "不要编造不存在的业务事实；不确定时在风险提示和确认清单中说明。",
                "输出必须是 JSON 对象，不要输出 Markdown 代码块。",
            ]
        )

        user_prompt = "\n\n".join(
            [
                f"目标资产类型：{state.get('target_artifact_type', '')}",
                f"用户修改指令：\n{state.get('instruction', '')}",
                f"当前节点上下文：\n{target_node}",
                f"当前资产信息：\n{current_artifact}",
                f"上游/相关资产上下文：\n{related_artifacts}",
                f"需求变更草案：\n{requirement_change}",
                f"当前资产结构化内容 JSON 摘要：\n{current_content}",
                f"当前资产 Markdown 局部上下文：\n{current_markdown}",
                "请输出：summary、targetArtifactType、proposedPatchOperations、proposedPatchSummary、changedSections、downstreamArtifacts、riskNotes、reviewChecklist、confidence。",
                "proposedPatchOperations 是必填数组；优先使用 replace 操作，字段示例：{op, sectionTitle, findText, replaceWith, rationale, confidence}。",
                "不要输出整份资产全文；只有 patch 无法表达时才附带 proposedContentMarkdown 兜底。",
            ]
        )

        state["system_prompt"] = system_prompt
        state["user_prompt"] = user_prompt
        return state

    async def _llm_generate(self, state: ArtifactChangeState) -> ArtifactChangeState:
        try:
            result = await self.llm.generate_structured_json(
                system_prompt=state.get("system_prompt", ""),
                user_prompt=state.get("user_prompt", ""),
                output_schema_name="asset_change_proposal_output",
                output_json_schema=ASSET_CHANGE_OUTPUT_SCHEMA,
                validate_json=True,
            )
            state["llm_result"] = json.loads(result.text)
        except Exception as exc:
            state["error"] = str(exc)
        return state

    def _route_after_llm(self, state: ArtifactChangeState) -> Literal["schema_validate", "fallback"]:
        value = state.get("llm_result")
        if isinstance(value, dict) and (value.get("proposedPatchOperations") or value.get("proposedContentMarkdown")):
            return "schema_validate"
        return "fallback"

    async def _schema_validate(self, state: ArtifactChangeState) -> ArtifactChangeState:
        result = self._as_dict(state.get("llm_result"))
        proposed_markdown = str(result.get("proposedContentMarkdown") or "").strip()
        patch_operations = self._object_list(result.get("proposedPatchOperations"))

        if not patch_operations and not proposed_markdown:
            return await self._fallback(state)

        patch_summary = self._string_list(result.get("proposedPatchSummary"))
        if not patch_summary:
            patch_summary = ["根据用户指令输出 Patch-first 局部修订操作。"]

        risk_notes = self._string_list(result.get("riskNotes"))
        review_checklist = self._string_list(result.get("reviewChecklist"))
        downstream = self._normalize_artifact_list(result.get("downstreamArtifacts"))
        changed_sections = self._object_list(result.get("changedSections"))

        confidence = self._float_value(result.get("confidence"), 0.7)
        target_artifact_type = str(
            result.get("targetArtifactType") or state.get("target_artifact_type") or "MODULE_PRD"
        ).upper()

        summary = str(result.get("summary") or "已生成资产局部修订草案。")

        markdown = self._build_markdown_summary(
            summary=summary,
            target_artifact_type=target_artifact_type,
            patch_summary=patch_summary,
            risk_notes=risk_notes,
            review_checklist=review_checklist,
        )

        state["artifact"] = AssistantArtifactReviewArtifact(
            summary=summary,
            riskLevel="MEDIUM" if risk_notes else "LOW",
            conclusion="已基于 LangGraph 资产变更 Agent 生成 Patch-first 局部修订操作。",
            issues=[],
            consistencyChecks=changed_sections,
            suggestions=patch_summary,
            nextActions=review_checklist,
            targetArtifactType=target_artifact_type,
            proposedContentMarkdown=proposed_markdown or None,
            proposedPatchOperations=patch_operations,
            proposedPatchSummary=patch_summary,
            changedSections=changed_sections,
            downstreamArtifacts=downstream,
            riskNotes=risk_notes,
            reviewChecklist=review_checklist,
            confidence=confidence,
            markdown=markdown,
        )
        return state

    async def _fallback(self, state: ArtifactChangeState) -> ArtifactChangeState:
        request = state.get("request")
        if request is None:
            raise RuntimeError("Missing AgentTaskRequest")

        state["artifact"] = self._build_safe_fallback_artifact(
            request=request,
            review_input=state.get("review_input") or {},
            reason=state.get("error") or "LLM did not return usable proposedPatchOperations.",
        )
        return state

    def _build_safe_fallback_artifact(
        self,
        *,
        request: AgentTaskRequest,
        review_input: dict[str, Any] | None = None,
        reason: str,
    ) -> AssistantArtifactReviewArtifact:
        review_input = review_input or self._as_dict(
            (request.context_package or {}).get("review_input")
            or request.input
            or {}
        )
        instruction = str(
            review_input.get("instruction")
            or request.requirement_text
            or "请根据用户变更要求修订当前资产。"
        ).strip()
        target_artifact_type = str(
            review_input.get("targetArtifactType")
            or review_input.get("selected_artifact_type")
            or "MODULE_PRD"
        ).strip().upper()
        current_markdown = str(
            review_input.get("currentMarkdown")
            or review_input.get("contentMarkdown")
            or "# 需求资产"
        ).strip()

        fallback_section = "\n".join(
            [
                "## AI Assistant 需求变更补充",
                f"**用户要求**：{instruction}",
                "**处理方式**：LangGraph 资产变更 Agent 未能返回稳定 patch，本版本采用安全补充方式，不执行不可验证的关键词替换。",
                "**建议动作**：优先在文档视图中基于上游变化进行局部修订；只有差异过大时再重生成整份资产。",
                "**待人工确认**：请在文档视图中核对并细化具体修改位置。",
            ]
        )
        patch_operations = [
            {
                "op": "append_section",
                "sectionTitle": "AI Assistant 需求变更补充",
                "replaceWith": fallback_section,
                "rationale": "LLM 未返回稳定局部 patch，采用追加补充区的保守策略。",
                "confidence": 0.25,
            }
        ]

        patch_summary = [
            "保留原资产正文，追加用户需求变更补充说明。",
            "未执行不可验证的自动替换，避免误改业务语义。",
            "需要人工核对后再确认新版本。",
        ]
        risk_notes = [
            "LangGraph 资产变更 Agent 进入安全兜底：" + reason,
            "本次输出不代表最终业务事实，需人工确认。",
        ]
        checklist = [
            "检查补充说明是否准确表达用户意图。",
            "必要时在文档视图中继续手动精修。",
            "确认后再选择性重生成下游资产。",
        ]

        return AssistantArtifactReviewArtifact(
            summary="已生成安全兜底型资产编辑提案。",
            riskLevel="MEDIUM",
            conclusion="LangGraph Agent 未能完成稳定 patch，已生成可审查的保守补充 patch。",
            issues=[{"type": "AGENT_FALLBACK", "message": reason}],
            consistencyChecks=[],
            suggestions=patch_summary,
            nextActions=checklist,
            targetArtifactType=target_artifact_type,
            proposedContentMarkdown=None,
            proposedPatchOperations=patch_operations,
            proposedPatchSummary=patch_summary,
            changedSections=[],
            downstreamArtifacts=self._default_downstream(target_artifact_type),
            riskNotes=risk_notes,
            reviewChecklist=checklist,
            confidence=0.25,
            markdown=self._build_markdown_summary(
                summary="已生成安全兜底型资产编辑提案。",
                target_artifact_type=target_artifact_type,
                patch_summary=patch_summary,
                risk_notes=risk_notes,
                review_checklist=checklist,
            ),
        )

    def _build_markdown_summary(
        self,
        *,
        summary: str,
        target_artifact_type: str,
        patch_summary: list[str],
        risk_notes: list[str],
        review_checklist: list[str],
    ) -> str:
        lines = [
            "# 资产编辑提案",
            "",
            f"目标资产：{target_artifact_type}",
            "",
            f"摘要：{summary}",
            "",
            "## 拟局部修改内容",
        ]
        lines.extend([f"- {item}" for item in patch_summary])
        lines.extend(["", "## 风险提示"])
        lines.extend([f"- {item}" for item in (risk_notes or ["暂无明显风险。"] )])
        lines.extend(["", "## 确认清单"])
        lines.extend([f"- {item}" for item in (review_checklist or ["人工确认新版本内容。"] )])
        return "\n".join(lines)

    def _default_downstream(self, artifact_type: str) -> list[str]:
        mapping = {
            "MODULE_PRD": ["BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            "BUSINESS_FLOW": ["PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            "PAGE_SPEC": ["DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            "DATA_MODEL": ["API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            "API_CONTRACT": ["TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            "TEST_SPEC": ["UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
        }
        return mapping.get(artifact_type, ["DEV_HANDOFF_PACKAGE"])

    def _normalize_artifact_list(self, value: Any) -> list[str]:
        allowed = {
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "DATA_MODEL",
            "API_CONTRACT",
            "TEST_SPEC",
            "UI_CONTRACT",
            "DEV_HANDOFF_PACKAGE",
        }
        result = []
        for item in self._string_list(value):
            normalized = item.strip().upper()
            if normalized in allowed and normalized not in result:
                result.append(normalized)
        return result

    def _object_list(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        result: list[dict[str, Any]] = []
        for item in value:
            if isinstance(item, dict):
                result.append(dict(item))
        return result

    def _string_list(self, value: Any) -> list[str]:
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    def _as_dict(self, value: Any) -> dict[str, Any]:
        if isinstance(value, dict):
            return dict(value)
        return {}

    def _float_value(self, value: Any, default: float) -> float:
        try:
            return float(value)
        except Exception:
            return default

    def _trim_json(self, value: Any, max_chars: int) -> str:
        try:
            return self._trim(json.dumps(value or {}, ensure_ascii=False, default=str), max_chars)
        except Exception:
            return self._trim(str(value), max_chars)

    def _trim(self, value: str, max_chars: int) -> str:
        text = value or ""
        if len(text) <= max_chars:
            return text
        return text[:max_chars] + "\n...[TRUNCATED]"
