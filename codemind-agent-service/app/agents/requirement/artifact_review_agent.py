from __future__ import annotations

import json
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.artifact_review import (
    ASSISTANT_ARTIFACT_REVIEW_JSON_SCHEMA,
    AssistantArtifactReviewArtifact,
    ArtifactConsistencyCheck,
    ArtifactReviewIssue,
    ArtifactReviewSuggestion,
)
from app.services.llm_service import LlmService


class AssistantArtifactReviewAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()

    async def run(
        self,
        request: AgentTaskRequest,
    ) -> AssistantArtifactReviewArtifact:
        review_input = self._extract_review_input(request)
        target_artifact = review_input.get("targetArtifact") or {}

        if not target_artifact:
            return self._fallback_no_artifact()

        system_prompt = self._build_system_prompt()
        user_prompt = self._build_user_prompt(review_input)

        try:
            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="assistant_artifact_review",
                output_json_schema=ASSISTANT_ARTIFACT_REVIEW_JSON_SCHEMA,
            )
            data = json.loads(self._strip_code_fence(llm_result.text))
            artifact = AssistantArtifactReviewArtifact.model_validate(
                {
                    **data,
                    "artifact_type": "ASSISTANT_ARTIFACT_REVIEW",
                }
            )
            artifact.markdown = self._build_markdown(artifact)
            return artifact
        except Exception as exc:
            return self._heuristic_review(
                review_input=review_input,
                reason=f"LLM 资产审查失败，已使用 Python 规则兜底：{exc}",
            )

    def _build_system_prompt(self) -> str:
        return "\n".join(
            [
                "你是 CodeMind Studio 的企业级需求资产审查 Agent。",
                "你的任务是审查一个当前节点上的需求工程资产，并结合相关上下游资产、附件解析内容和节点上下文给出可执行结论。",
                "不要编造上下文中不存在的业务事实；如果依据不足，要明确说明依据不足。",
                "不要输出 Markdown，只输出严格符合 JSON Schema 的 JSON 对象。",
                "审查重点：",
                "1. 完整性：目标、范围、角色、规则、字段、接口、异常、验收是否缺失。",
                "2. 可执行性：开发 Agent 是否能据此实现，测试 Agent 是否能据此写测试。",
                "3. 一致性：与需求、页面规范、接口契约、测试规格、附件材料是否冲突或遗漏。",
                "4. 层级职责：DOMAIN/MODULE/SUBMODULE/PAGE/FEATURE 资产是否写越界或写得过泛。",
                "5. 风险：把真正会阻塞后续开发/测试/交付的问题标为 BLOCKER，不要滥用阻塞级别。",
                "输出的 issues、consistencyChecks、suggestions 必须短而具体，能直接指导下一步修改。",
            ]
        )

    def _build_user_prompt(
        self,
        review_input: dict[str, Any],
    ) -> str:
        compact_input = {
            "instruction": review_input.get("instruction"),
            "currentNode": review_input.get("currentNode"),
            "targetArtifact": self._compact_artifact(review_input.get("targetArtifact") or {}, 18000),
            "relatedArtifacts": [
                self._compact_artifact(item, 6000)
                for item in (review_input.get("relatedArtifacts") or [])[:8]
                if isinstance(item, dict)
            ],
            "attachmentContexts": self._compact_attachment_contexts(
                review_input.get("attachmentContexts") or []
            ),
            "reviewPolicy": review_input.get("reviewPolicy") or {},
        }

        return "\n".join(
            [
                "请审查下面的需求资产，并只输出 JSON。",
                json.dumps(compact_input, ensure_ascii=False, default=str),
            ]
        )

    def _extract_review_input(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, Any]:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        if isinstance(context_package.get("review_input"), dict):
            merged = dict(context_package["review_input"])
            merged.update(input_payload)
            return merged

        return input_payload

    def _compact_artifact(
        self,
        artifact: dict[str, Any],
        max_chars: int,
    ) -> dict[str, Any]:
        result = dict(artifact)
        markdown = str(result.get("contentMarkdown") or "")
        if len(markdown) > max_chars:
            result["contentMarkdown"] = markdown[:max_chars] + "\n...【内容已截断】"
        return result

    def _compact_attachment_contexts(
        self,
        contexts: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(contexts, list):
            return []

        result: list[dict[str, Any]] = []
        for item in contexts[:5]:
            if not isinstance(item, dict):
                continue
            excerpt = str(item.get("parsedTextExcerpt") or "")
            result.append(
                {
                    "attachmentId": item.get("attachmentId"),
                    "fileName": item.get("fileName"),
                    "mimeType": item.get("mimeType"),
                    "parseStatus": item.get("parseStatus"),
                    "parsedTextExcerpt": excerpt[:6000],
                    "metadata": item.get("metadata") or {},
                }
            )
        return result

    def _heuristic_review(
        self,
        *,
        review_input: dict[str, Any],
        reason: str,
    ) -> AssistantArtifactReviewArtifact:
        target = review_input.get("targetArtifact") or {}
        artifact_type = str(target.get("artifactType") or "UNKNOWN")
        markdown = str(target.get("contentMarkdown") or "")
        related = review_input.get("relatedArtifacts") or []
        attachments = review_input.get("attachmentContexts") or []

        issues: list[ArtifactReviewIssue] = []
        checks: list[ArtifactConsistencyCheck] = []
        suggestions: list[ArtifactReviewSuggestion] = []

        if len(markdown.strip()) < 120:
            issues.append(
                ArtifactReviewIssue(
                    code="CONTENT_TOO_THIN",
                    severity="BLOCKER",
                    title="资产内容不足",
                    description="当前资产正文为空或过短，无法支撑正式开发、测试或评审。",
                    suggestion="重新生成该资产，并确保包含目标、规则、异常、字段/接口/测试点和验收标准。",
                    artifactType=artifact_type,
                )
            )

        if artifact_type in {"API_CONTRACT", "TEST_SPEC"} and not self._has_related(related, "MODULE_PRD"):
            issues.append(
                ArtifactReviewIssue(
                    code="MISSING_REQUIREMENT_CONTEXT",
                    severity="WARNING",
                    title="缺少需求说明上下文",
                    description="当前审查的接口或测试资产缺少需求说明作为直接依据。",
                    suggestion="先补齐或确认当前节点需求说明，再复核接口/测试是否覆盖业务规则。",
                    artifactType="MODULE_PRD",
                )
            )

        if artifact_type == "UI_CONTRACT" and not self._has_related(related, "PAGE_SPEC"):
            issues.append(
                ArtifactReviewIssue(
                    code="MISSING_PAGE_SPEC_CONTEXT",
                    severity="WARNING",
                    title="交互原型缺少页面规范依据",
                    description="未发现页面规范，UI 契约可能缺少字段、布局、状态和交互来源。",
                    suggestion="先生成页面规范，再生成或审查 UI_CONTRACT。",
                    artifactType="PAGE_SPEC",
                )
            )

        if attachments:
            checks.append(
                ArtifactConsistencyCheck(
                    dimension="附件对照",
                    level="WARNING",
                    result="本次请求包含附件，需要人工或 LLM 逐项核对附件要求是否全部进入资产。",
                    evidence="检测到 attachmentContexts。",
                    recommendation="重点核对附件中的页面、字段、流程、异常、权限和验收要求是否遗漏。",
                )
            )

        if not issues:
            issues.append(
                ArtifactReviewIssue(
                    code="NO_OBVIOUS_BLOCKER",
                    severity="INFO",
                    title="未发现明显阻塞问题",
                    description="Python 兜底规则未发现明显阻塞项，但未完成深度语义审查。",
                    suggestion="建议在 LLM 可用时重新执行资产审查，或进入人工审核。",
                    artifactType=artifact_type,
                )
            )

        suggestions.append(
            ArtifactReviewSuggestion(
                code="REVIEW_NEXT_STEP",
                severity="INFO",
                title="继续做上下游一致性复核",
                description="将当前资产与需求、页面规范、接口、测试规格进行交叉检查。",
                suggestion="如果有上游资产更新，重新生成当前资产或进行影响分析。",
                artifactType=artifact_type,
            )
        )

        risk_level = "HIGH" if any(item.severity == "BLOCKER" for item in issues) else "MEDIUM"

        artifact = AssistantArtifactReviewArtifact(
            summary="已完成资产审查；LLM 未成功调用，因此采用规则兜底。",
            riskLevel=risk_level,  # type: ignore[arg-type]
            conclusion=(
                "当前资产存在阻塞风险，需要先修复。"
                if risk_level == "HIGH"
                else "当前资产未发现明显阻塞问题，但建议继续做一致性复核。"
            ),
            issues=issues,
            consistencyChecks=checks,
            suggestions=suggestions,
            nextActions=[
                "修复阻塞问题后重新生成或重新审查",
                "打开质量检查查看完整资产链路",
                "必要时基于附件补充遗漏需求",
            ],
            markdown="",
        )
        artifact.markdown = self._build_markdown(artifact, note=reason)
        return artifact

    def _fallback_no_artifact(self) -> AssistantArtifactReviewArtifact:
        artifact = AssistantArtifactReviewArtifact(
            summary="没有检测到可审查的目标资产。",
            riskLevel="HIGH",
            conclusion="请先生成或选择一个当前资产，再执行资产审查。",
            issues=[
                ArtifactReviewIssue(
                    code="NO_TARGET_ARTIFACT",
                    severity="BLOCKER",
                    title="缺少目标资产",
                    description="本次请求没有提供 targetArtifact。",
                    suggestion="先选择左侧节点和资产，或生成当前节点核心资产。",
                    artifactType=None,
                )
            ],
            nextActions=["选择具体资产", "生成当前节点核心资产"],
        )
        artifact.markdown = self._build_markdown(artifact)
        return artifact

    def _has_related(
        self,
        related: Any,
        artifact_type: str,
    ) -> bool:
        if not isinstance(related, list):
            return False
        return any(
            isinstance(item, dict) and str(item.get("artifactType")) == artifact_type
            for item in related
        )

    def _build_markdown(
        self,
        artifact: AssistantArtifactReviewArtifact,
        note: str | None = None,
    ) -> str:
        lines = [
            "# AI 资产审查结果",
            "",
            f"- 风险等级：{artifact.riskLevel}",
            f"- 结论：{artifact.conclusion}",
            "",
            "## 概要",
            artifact.summary or "-",
        ]
        if note:
            lines.extend(["", "## 运行说明", note])
        if artifact.issues:
            lines.extend(["", "## 主要问题"])
            for issue in artifact.issues:
                lines.append(f"- 【{issue.severity}】{issue.title}：{issue.description} 建议：{issue.suggestion}")
        if artifact.consistencyChecks:
            lines.extend(["", "## 一致性检查"])
            for check in artifact.consistencyChecks:
                lines.append(f"- 【{check.level}】{check.dimension}：{check.result} 建议：{check.recommendation}")
        if artifact.suggestions:
            lines.extend(["", "## 修改建议"])
            for suggestion in artifact.suggestions:
                lines.append(f"- {suggestion.title}：{suggestion.suggestion or suggestion.description}")
        if artifact.nextActions:
            lines.extend(["", "## 下一步"])
            for action in artifact.nextActions:
                lines.append(f"- {action}")
        return "\n".join(lines)

    def _strip_code_fence(
        self,
        text: str,
    ) -> str:
        cleaned = text.strip()
        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()
        if cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()
        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()
        return cleaned.strip()
