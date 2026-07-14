from __future__ import annotations

import json
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.module_tree_patch import (
    MODULE_TREE_PATCH_PROPOSAL_JSON_SCHEMA,
    ModuleTreePatchImpact,
    ModuleTreePatchProposalArtifact,
)
from app.services.llm_service import LlmService


class ModuleTreePatchAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()

    async def run(
        self,
        request: AgentTaskRequest,
    ) -> ModuleTreePatchProposalArtifact:
        instruction = self._extract_instruction(request)
        module_tree = self._extract_module_tree(request)
        selected_node_id = self._extract_selected_node_id(request)

        if not instruction.strip():
            return self._empty_proposal(
                "未提供模块树修改要求，请输入需要新增、移动、删除或修改的内容。"
            )

        system_prompt = self._build_system_prompt()
        user_prompt = self._build_user_prompt(
            request=request,
            instruction=instruction,
            module_tree=module_tree,
            selected_node_id=selected_node_id,
        )

        try:
            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="module_tree_patch_proposal",
                output_json_schema=MODULE_TREE_PATCH_PROPOSAL_JSON_SCHEMA,
            )

            parsed_json = self._strip_code_fence(llm_result.text)
            data = json.loads(parsed_json)

            artifact = ModuleTreePatchProposalArtifact.model_validate(
                {
                    **data,
                    "source": "AI_TREE_PATCH",
                }
            )

            artifact.markdown = self._build_markdown(artifact)

            return artifact

        except Exception as exc:
            return ModuleTreePatchProposalArtifact(
                description="AI Tree Patch 提案生成失败",
                source="AI_TREE_PATCH",
                operations=[],
                impact=ModuleTreePatchImpact(
                    summary="AI 未能生成可用的模块树变更提案。",
                    affectedNodeIds=[],
                    affectedArtifacts=[],
                    riskNotes=[
                        str(exc),
                        "请简化修改要求后重试，例如：给用户中心增加密码找回页。",
                    ],
                ),
                markdown="AI Tree Patch 提案生成失败，请调整输入后重试。",
                analysis_summary=str(exc),
            )

    def _build_system_prompt(self) -> str:
        return (
            "你是 CodeMind Studio 的模块树结构变更 Agent。"
            "你的任务是根据用户自然语言要求，基于现有模块树生成 Tree Patch 变更提案。"
            "你只能生成提案，不能直接应用修改。"
            "输出必须严格符合 JSON Schema。"
        )

    def _build_user_prompt(
        self,
        *,
        request: AgentTaskRequest,
        instruction: str,
        module_tree: Any,
        selected_node_id: str | None,
    ) -> str:
        return "\n".join(
            [
                "请根据用户要求生成模块树 Tree Patch 变更提案。",
                "",
                "【重要规则】",
                "1. 不要直接返回完整模块树，只返回 operations。",
                "2. operations 只允许使用 ADD_NODE、UPDATE_NODE、MOVE_NODE、DELETE_NODE。",
                "3. 如果是新增节点，必须提供 parentId、moduleName、moduleType、extra。",
                "4. 如果是修改节点，必须提供 nodeId，以及需要修改的字段。",
                "5. 如果是移动节点，必须提供 nodeId 和 newParentId。",
                "6. 如果是删除节点，必须提供 nodeId。",
                "7. 如果找不到明确目标节点，不要编造 nodeId，应返回空 operations，并在 riskNotes 里说明需要用户确认。",
                "",
                "【节点类型规则】",
                "- DOMAIN：业务域。",
                "- MODULE：业务模块。",
                "- SUBMODULE：子模块 / 业务场景。",
                "- PAGE：最小页面，可生成 UI_CONTRACT。",
                "- FEATURE：页面内功能点，不单独生成完整页面。",
                "",
                "【层级规则】",
                "- 根节点只能是 DOMAIN 或 MODULE。",
                "- DOMAIN 下只能放 MODULE。",
                "- MODULE 下可以放 SUBMODULE 或 PAGE。",
                "- SUBMODULE 下只能放 PAGE。",
                "- PAGE 下只能放 FEATURE。",
                "- FEATURE 下不能继续放子节点。",
                "",
                "【PAGE extra 要求】",
                "PAGE 节点 extra 必须包含：",
                "- moduleType=PAGE",
                "- type=PAGE",
                "- canGenerateUiContract=true",
                "- pageKind：LIST_PAGE / FORM_PAGE / DETAIL_PAGE / APPROVAL_PAGE / DASHBOARD_PAGE / SETTINGS_PAGE",
                "- routePath：页面路由，例如 /user/forgot-password",
                "- layoutType：table-list / form-detail / detail-tabs / approval-workbench / dashboard",
                "- primaryEntity：主业务对象",
                "",
                "【FEATURE extra 要求】",
                "FEATURE 节点 extra 必须包含：",
                "- moduleType=FEATURE",
                "- type=FEATURE",
                "- canGenerateUiContract=false",
                "- featureRole：query / create / update / delete / submit / approve / reject / export / import / navigate / validate / custom",
                "",
                "【影响分析要求】",
                "impact 必须说明：",
                "- affectedNodeIds",
                "- affectedArtifacts",
                "- riskNotes",
                "",
                "【任务信息】",
                json.dumps(
                    {
                        "task_id": request.task_id,
                        "project_id": request.project_id,
                        "project_code": request.project_code,
                        "module_id": request.module_id,
                        "task_type": request.task_type,
                        "selected_node_id": selected_node_id,
                    },
                    ensure_ascii=False,
                ),
                "",
                "【用户修改要求】",
                instruction,
                "",
                "【当前模块树】",
                json.dumps(
                    module_tree,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "只输出 JSON，不要输出 Markdown 代码块，不要输出解释性前后缀。",
            ]
        )

    def _extract_instruction(
        self,
        request: AgentTaskRequest,
    ) -> str:
        input_payload = request.input or {}

        for key in [
            "instruction",
            "user_instruction",
            "change_request",
            "requirementText",
            "requirement_text",
        ]:
            value = input_payload.get(key)

            if value:
                return str(value)

        if request.requirement_text:
            return request.requirement_text

        return ""

    def _extract_selected_node_id(
        self,
        request: AgentTaskRequest,
    ) -> str | None:
        input_payload = request.input or {}

        value = input_payload.get("selected_node_id") or input_payload.get("selectedNodeId")

        if value:
            return str(value)

        return request.module_id

    def _extract_module_tree(
        self,
        request: AgentTaskRequest,
    ) -> Any:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        if "module_tree" in input_payload:
            return input_payload["module_tree"]

        if "moduleTree" in input_payload:
            return input_payload["moduleTree"]

        if "module_tree" in context_package:
            return context_package["module_tree"]

        if "moduleTree" in context_package:
            return context_package["moduleTree"]

        return []

    def _empty_proposal(
        self,
        reason: str,
    ) -> ModuleTreePatchProposalArtifact:
        artifact = ModuleTreePatchProposalArtifact(
            description="未生成模块树变更提案",
            source="AI_TREE_PATCH",
            operations=[],
            impact=ModuleTreePatchImpact(
                summary=reason,
                affectedNodeIds=[],
                affectedArtifacts=[],
                riskNotes=[reason],
            ),
            analysis_summary=reason,
        )

        artifact.markdown = self._build_markdown(artifact)

        return artifact

    def _build_markdown(
        self,
        artifact: ModuleTreePatchProposalArtifact,
    ) -> str:
        lines = [
            "# AI 模块树变更提案",
            "",
            f"## 变更说明",
            artifact.description or "未提供。",
            "",
            "## 操作列表",
        ]

        if not artifact.operations:
            lines.append("- 暂无可执行操作。")
        else:
            for index, operation in enumerate(artifact.operations, start=1):
                lines.append(
                    f"- {index}. {operation.op}："
                    f"{operation.moduleName or operation.nodeId or '-'}"
                )

        lines.extend(
            [
                "",
                "## 影响分析",
                artifact.impact.summary or "无。",
                "",
                "## 风险提示",
            ]
        )

        if not artifact.impact.riskNotes:
            lines.append("- 无。")
        else:
            for note in artifact.impact.riskNotes:
                lines.append(f"- {note}")

        lines.extend(
            [
                "",
                "## 分析摘要",
                artifact.analysis_summary or "无。",
            ]
        )

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