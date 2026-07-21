from __future__ import annotations

import hashlib
import json
import re
from typing import Any, Literal, TypedDict

from pydantic import BaseModel, Field

try:
    from langgraph.graph import END, StateGraph
except Exception:  # pragma: no cover - langgraph is available in runtime, this keeps static checks safe.
    END = "__end__"
    StateGraph = None  # type: ignore[assignment]

from app.schemas.agent_protocol import AgentTaskRequest
from app.services.llm_service import LlmService


MODULE_TREE_PATCH_OUTPUT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "description": {"type": "string"},
        "source": {"type": "string"},
        "operations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "op": {"type": "string"},
                    "nodeId": {"type": ["string", "null"]},
                    "parentId": {"type": ["string", "null"]},
                    "newParentId": {"type": ["string", "null"]},
                    "moduleName": {"type": ["string", "null"]},
                    "moduleKey": {"type": ["string", "null"]},
                    "moduleType": {"type": ["string", "null"]},
                    "sortOrder": {"type": ["integer", "null"]},
                    "extra": {"type": "object"},
                },
                "required": ["op"],
                "additionalProperties": True,
            },
        },
        "impact": {
            "type": "object",
            "properties": {
                "summary": {"type": "string"},
                "affectedNodeIds": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "affectedArtifacts": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "riskNotes": {
                    "type": "array",
                    "items": {"type": "string"},
                },
            },
            "required": ["summary", "affectedNodeIds", "affectedArtifacts", "riskNotes"],
            "additionalProperties": True,
        },
        "validationNotes": {
            "type": "array",
            "items": {"type": "string"},
        },
        "confidence": {"type": "number"},
    },
    "required": [
        "description",
        "source",
        "operations",
        "impact",
        "validationNotes",
        "confidence",
    ],
    "additionalProperties": True,
}


class ModuleTreePatchProposalArtifact(BaseModel):
    artifact_type: str = "MODULE_TREE_PATCH_PROPOSAL"
    description: str = ""
    source: str = "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
    proposal: dict[str, Any] = Field(default_factory=dict)
    markdown: str | None = None
    requiresUserConfirmation: bool = True
    confidence: float = 0.0
    validationNotes: list[str] = Field(default_factory=list)


class ModuleTreePatchState(TypedDict, total=False):
    request: AgentTaskRequest
    instruction: str
    selected_node_id: str | None
    module_tree: list[dict[str, Any]]
    flat_nodes: list[dict[str, Any]]
    system_prompt: str
    user_prompt: str
    llm_result: dict[str, Any]
    error: str
    artifact: ModuleTreePatchProposalArtifact


class ModuleTreePatchAgent:
    """
    Phase 6-A: LangGraph-based module-tree patch proposal Agent.

    This Agent never mutates the module tree. It only proposes normalized
    operations that Java will validate and the user must confirm before apply.
    """

    ALLOWED_OPS = {"ADD_NODE", "UPDATE_NODE", "MOVE_NODE", "DELETE_NODE"}
    ALLOWED_TYPES = {"DOMAIN", "MODULE", "SUBMODULE", "PAGE", "FEATURE"}

    def __init__(self) -> None:
        self.llm = LlmService()
        self.graph = self._build_graph()

    async def run(self, request: AgentTaskRequest) -> ModuleTreePatchProposalArtifact:
        initial_state: ModuleTreePatchState = {
            "request": request,
        }

        if self.graph is None:
            state = await self._fallback(initial_state)
        else:
            state = await self.graph.ainvoke(initial_state)

        artifact = state.get("artifact")
        if isinstance(artifact, ModuleTreePatchProposalArtifact):
            return artifact

        return self._build_rule_fallback_artifact(
            request=request,
            reason="LangGraph workflow did not return an artifact.",
        )

    def _build_graph(self):
        if StateGraph is None:
            return None

        workflow = StateGraph(ModuleTreePatchState)
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

    async def _prepare_context(self, state: ModuleTreePatchState) -> ModuleTreePatchState:
        request = state["request"]
        input_payload = dict(request.input or {})
        context_package = dict(request.context_package or {})

        instruction = str(
            input_payload.get("instruction")
            or request.requirement_text
            or ""
        ).strip()

        module_tree = self._as_node_list(
            input_payload.get("module_tree")
            or context_package.get("module_tree")
            or []
        )

        selected_node_id = input_payload.get("selected_node_id") or request.module_id
        selected_node_id = str(selected_node_id).strip() if selected_node_id else None

        flat_nodes = self._flatten_tree(module_tree)

        state.update(
            {
                "instruction": instruction,
                "selected_node_id": selected_node_id,
                "module_tree": module_tree,
                "flat_nodes": flat_nodes,
            }
        )
        return state

    async def _build_prompt(self, state: ModuleTreePatchState) -> ModuleTreePatchState:
        selected_node = self._find_node_by_id(
            state.get("flat_nodes", []),
            state.get("selected_node_id"),
        )

        compact_tree = self._compact_tree(state.get("module_tree", []))
        flat_index = [
            {
                "id": node.get("id"),
                "parentId": node.get("parentId"),
                "moduleName": node.get("moduleName") or node.get("name"),
                "moduleKey": node.get("moduleKey") or node.get("key"),
                "moduleType": node.get("moduleType") or node.get("type"),
                "modulePath": node.get("modulePath"),
                "levelNo": node.get("levelNo"),
            }
            for node in state.get("flat_nodes", [])
        ]

        system_prompt = "\n".join(
            [
                "你是 CodeMind Studio 的 ModuleTreePatchAgent。",
                "你的任务是把用户的自然语言模块树修改要求，转成可审查的模块树 Patch 提案。",
                "只能输出提案，不能声称已经应用，不能直接修改模块树。",
                "操作类型只能是 ADD_NODE、UPDATE_NODE、MOVE_NODE、DELETE_NODE。",
                "节点层级只能使用 DOMAIN、MODULE、SUBMODULE、PAGE、FEATURE。",
                "必须尽量复用现有节点 id；新增节点时必须给出 parentId、moduleName、moduleKey、moduleType。",
                "不要输出不存在于当前 module_tree 的 nodeId；如不确定，写入 riskNotes，而不是胡编 id。",
                "当前选中节点只是用户界面上下文，不是强制父节点；用户显式提到的位置或节点必须优先于当前选中节点。",
                "例如用户在登录功能节点上说‘给用户中心增加密码找回页’，应优先在模块树中寻找用户中心或登录注册节点作为父级，而不是把密码找回写进登录功能需求。",
                "如果用户说‘登录页加一个密码找回入口’，这是页面资产局部修订，不是新增页面节点，应返回空 operations 并提示走资产编辑。",
                "DELETE_NODE 属于高风险操作，除非用户明确要求删除，否则不要使用。",
                "移动节点必须使用 MOVE_NODE，并给出 nodeId 与 newParentId。",
                "如果用户只是修改当前资产正文，不涉及新增/删除/移动/重命名模块树节点，应返回空 operations 并在 riskNotes 说明应走资产编辑流程。",
                "仅返回合法 JSON，不要输出 Markdown 代码块。",
            ]
        )

        user_prompt = "\n\n".join(
            [
                f"用户指令：\n{state.get('instruction', '')}",
                f"当前选中节点：\n{self._json_dump(selected_node or {})}",
                f"当前模块树紧凑结构：\n{self._trim_json(compact_tree, 16000)}",
                f"节点索引：\n{self._trim_json(flat_index, 16000)}",
                "请输出 JSON 字段：description、source、operations、impact、validationNotes、confidence。",
                "operations 中每个对象只保留 Java 可执行字段：op、nodeId、parentId、newParentId、moduleName、moduleKey、moduleType、sortOrder、extra。",
                "impact.affectedArtifacts 可从 MODULE_PRD、BUSINESS_FLOW、PAGE_SPEC、DATA_MODEL、API_CONTRACT、TEST_SPEC、UI_CONTRACT、DEV_HANDOFF_PACKAGE 中选择。",
                "如果是新增页面，通常影响 PAGE_SPEC、UI_CONTRACT、TEST_SPEC、DEV_HANDOFF_PACKAGE；如果新增功能点，通常影响 MODULE_PRD、PAGE_SPEC、TEST_SPEC、UI_CONTRACT。",
            ]
        )

        state["system_prompt"] = system_prompt
        state["user_prompt"] = user_prompt
        return state

    async def _llm_generate(self, state: ModuleTreePatchState) -> ModuleTreePatchState:
        try:
            result = await self.llm.generate_structured_json(
                system_prompt=state.get("system_prompt", ""),
                user_prompt=state.get("user_prompt", ""),
                output_schema_name="module_tree_patch_proposal_output",
                output_json_schema=MODULE_TREE_PATCH_OUTPUT_SCHEMA,
                validate_json=True,
            )
            state["llm_result"] = json.loads(result.text)
        except Exception as exc:
            state["error"] = str(exc)
        return state

    def _route_after_llm(self, state: ModuleTreePatchState) -> Literal["schema_validate", "fallback"]:
        value = state.get("llm_result")
        if not isinstance(value, dict):
            return "fallback"

        if isinstance(value.get("operations"), list):
            return "schema_validate"

        return "fallback"

    async def _schema_validate(self, state: ModuleTreePatchState) -> ModuleTreePatchState:
        result = self._as_dict(state.get("llm_result"))
        raw_operations = result.get("operations")
        raw_impact = self._as_dict(result.get("impact"))
        validation_notes = self._string_list(result.get("validationNotes"))

        operations, normalizer_notes = self._normalize_operations(
            raw_operations,
            state=state,
        )
        validation_notes.extend(normalizer_notes)

        description = str(
            result.get("description")
            or self._default_description(state.get("instruction", ""), operations)
        ).strip()
        source = str(
            result.get("source")
            or "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
        ).strip()

        impact = self._normalize_impact(
            raw_impact,
            operations=operations,
            state=state,
            validation_notes=validation_notes,
        )

        confidence = self._float_value(result.get("confidence"), 0.72)
        if not operations:
            confidence = min(confidence, 0.45)

        proposal = {
            "description": description,
            "source": source,
            "operations": operations,
            "impact": impact,
            "validationNotes": validation_notes,
            "confidence": confidence,
            "agentMode": "LANGGRAPH_PROPOSAL_ONLY",
        }

        markdown = self._build_markdown(
            description=description,
            operations=operations,
            impact=impact,
            validation_notes=validation_notes,
            confidence=confidence,
        )

        state["artifact"] = ModuleTreePatchProposalArtifact(
            description=description,
            source=source,
            proposal=proposal,
            markdown=markdown,
            requiresUserConfirmation=True,
            confidence=confidence,
            validationNotes=validation_notes,
        )
        return state

    async def _fallback(self, state: ModuleTreePatchState) -> ModuleTreePatchState:
        request = state.get("request")
        if request is None:
            raise RuntimeError("Missing AgentTaskRequest")

        state["artifact"] = self._build_rule_fallback_artifact(
            request=request,
            reason=state.get("error") or "LLM did not return a usable module tree patch proposal.",
            prepared_state=state,
        )
        return state

    def _build_rule_fallback_artifact(
        self,
        *,
        request: AgentTaskRequest,
        reason: str,
        prepared_state: ModuleTreePatchState | None = None,
    ) -> ModuleTreePatchProposalArtifact:
        prepared_state = prepared_state or {}
        input_payload = dict(request.input or {})
        module_tree = self._as_node_list(
            prepared_state.get("module_tree")
            or input_payload.get("module_tree")
            or (request.context_package or {}).get("module_tree")
            or []
        )
        flat_nodes = prepared_state.get("flat_nodes") or self._flatten_tree(module_tree)
        instruction = str(
            prepared_state.get("instruction")
            or input_payload.get("instruction")
            or request.requirement_text
            or ""
        ).strip()
        selected_node_id = prepared_state.get("selected_node_id")
        if selected_node_id is None:
            raw_selected = input_payload.get("selected_node_id") or request.module_id
            selected_node_id = str(raw_selected).strip() if raw_selected else None

        operations, notes = self._build_rule_operations(
            instruction=instruction,
            selected_node_id=selected_node_id,
            flat_nodes=flat_nodes,
        )

        risk_notes = [
            "LLM 模块树 Patch Agent 未返回稳定结果，已使用规则兜底生成可审查提案。",
            f"兜底原因：{reason}",
            "确认前请重点检查父节点、节点类型和 moduleKey 是否符合预期。",
        ]
        if not operations:
            risk_notes.append("规则兜底也未能识别出可执行模块树操作，建议补充说明要新增、移动、重命名还是删除哪个节点。")

        impact = {
            "summary": "模块树变更可能影响相关需求资产，确认应用后建议逐项检查受影响资产。",
            "affectedNodeIds": self._affected_node_ids(operations, selected_node_id),
            "affectedArtifacts": self._infer_affected_artifacts(operations),
            "riskNotes": risk_notes,
        }

        description = self._default_description(instruction, operations)
        source = "LANGGRAPH_MODULE_TREE_PATCH_AGENT_RULE_FALLBACK"
        proposal = {
            "description": description,
            "source": source,
            "operations": operations,
            "impact": impact,
            "validationNotes": notes,
            "confidence": 0.45 if operations else 0.2,
            "agentMode": "RULE_FALLBACK_PROPOSAL_ONLY",
        }

        markdown = self._build_markdown(
            description=description,
            operations=operations,
            impact=impact,
            validation_notes=notes,
            confidence=float(proposal["confidence"]),
        )

        return ModuleTreePatchProposalArtifact(
            description=description,
            source=source,
            proposal=proposal,
            markdown=markdown,
            requiresUserConfirmation=True,
            confidence=float(proposal["confidence"]),
            validationNotes=notes,
        )

    def _normalize_operations(
        self,
        raw_operations: Any,
        *,
        state: ModuleTreePatchState,
    ) -> tuple[list[dict[str, Any]], list[str]]:
        notes: list[str] = []
        operations: list[dict[str, Any]] = []
        flat_nodes = state.get("flat_nodes", [])
        selected_node_id = state.get("selected_node_id")

        if not isinstance(raw_operations, list):
            return [], ["Agent 未返回 operations 数组。"]

        for index, raw in enumerate(raw_operations):
            if not isinstance(raw, dict):
                notes.append(f"第 {index + 1} 个操作不是对象，已忽略。")
                continue

            op = str(raw.get("op") or raw.get("operation") or "").strip().upper()
            if op not in self.ALLOWED_OPS:
                notes.append(f"第 {index + 1} 个操作类型 {op or '-'} 不受支持，已忽略。")
                continue

            operation: dict[str, Any] = {"op": op}

            if op == "ADD_NODE":
                module_name = self._clean_text(raw.get("moduleName") or raw.get("name") or raw.get("title"))
                module_type = self._normalize_module_type(raw.get("moduleType") or raw.get("type"), module_name)
                parent_id = self._clean_nullable(raw.get("parentId")) or selected_node_id

                if not module_name:
                    notes.append(f"第 {index + 1} 个 ADD_NODE 缺少 moduleName，已忽略。")
                    continue
                if not module_type:
                    notes.append(f"第 {index + 1} 个 ADD_NODE 缺少 moduleType，已按 PAGE 处理。")
                    module_type = "PAGE"

                operation["parentId"] = parent_id
                operation["moduleName"] = module_name
                operation["moduleKey"] = self._clean_text(raw.get("moduleKey")) or self._generate_module_key(module_name, module_type)
                operation["moduleType"] = module_type

                sort_order = self._int_or_none(raw.get("sortOrder"))
                if sort_order is not None:
                    operation["sortOrder"] = sort_order

                extra = raw.get("extra")
                operation["extra"] = dict(extra) if isinstance(extra, dict) else {}

            elif op == "UPDATE_NODE":
                node_id = self._clean_nullable(raw.get("nodeId"))
                if not node_id or not self._find_node_by_id(flat_nodes, node_id):
                    notes.append(f"第 {index + 1} 个 UPDATE_NODE 缺少有效 nodeId，已忽略。")
                    continue
                operation["nodeId"] = node_id

                module_name = self._clean_text(raw.get("moduleName") or raw.get("name") or raw.get("title"))
                module_key = self._clean_text(raw.get("moduleKey"))
                module_type = self._normalize_module_type(raw.get("moduleType") or raw.get("type"), module_name)

                if module_name:
                    operation["moduleName"] = module_name
                if module_key:
                    operation["moduleKey"] = module_key
                if module_type:
                    operation["moduleType"] = module_type

                sort_order = self._int_or_none(raw.get("sortOrder"))
                if sort_order is not None:
                    operation["sortOrder"] = sort_order

                extra = raw.get("extra")
                if isinstance(extra, dict):
                    operation["extra"] = dict(extra)

                if len(operation) <= 2:
                    notes.append(f"第 {index + 1} 个 UPDATE_NODE 没有可更新字段，已忽略。")
                    continue

            elif op == "MOVE_NODE":
                node_id = self._clean_nullable(raw.get("nodeId"))
                new_parent_id = self._clean_nullable(raw.get("newParentId")) or self._clean_nullable(raw.get("parentId"))
                if not node_id or not self._find_node_by_id(flat_nodes, node_id):
                    notes.append(f"第 {index + 1} 个 MOVE_NODE 缺少有效 nodeId，已忽略。")
                    continue
                if new_parent_id and not self._find_node_by_id(flat_nodes, new_parent_id):
                    notes.append(f"第 {index + 1} 个 MOVE_NODE 的 newParentId 不存在，已忽略。")
                    continue
                if node_id == new_parent_id:
                    notes.append(f"第 {index + 1} 个 MOVE_NODE 不能移动到自身，已忽略。")
                    continue
                operation["nodeId"] = node_id
                operation["newParentId"] = new_parent_id

                sort_order = self._int_or_none(raw.get("sortOrder"))
                if sort_order is not None:
                    operation["sortOrder"] = sort_order

            elif op == "DELETE_NODE":
                node_id = self._clean_nullable(raw.get("nodeId"))
                if not node_id or not self._find_node_by_id(flat_nodes, node_id):
                    notes.append(f"第 {index + 1} 个 DELETE_NODE 缺少有效 nodeId，已忽略。")
                    continue
                operation["nodeId"] = node_id

            operations.append(operation)

        return operations, notes

    def _normalize_impact(
        self,
        raw_impact: dict[str, Any],
        *,
        operations: list[dict[str, Any]],
        state: ModuleTreePatchState,
        validation_notes: list[str],
    ) -> dict[str, Any]:
        affected_node_ids = self._string_list(raw_impact.get("affectedNodeIds"))
        for node_id in self._affected_node_ids(operations, state.get("selected_node_id")):
            if node_id and node_id not in affected_node_ids:
                affected_node_ids.append(node_id)

        affected_artifacts = self._normalize_artifact_list(raw_impact.get("affectedArtifacts"))
        if not affected_artifacts:
            affected_artifacts = self._infer_affected_artifacts(operations)

        risk_notes = self._string_list(raw_impact.get("riskNotes"))
        if validation_notes:
            risk_notes.append("存在提案规范化提示，请确认后再应用。")
        if not operations:
            risk_notes.append("当前提案没有可执行操作，不会修改模块树。")

        summary = str(raw_impact.get("summary") or "").strip()
        if not summary:
            summary = "模块树变更将影响相关节点的需求资产，需要应用后逐项确认。"

        return {
            "summary": summary,
            "affectedNodeIds": affected_node_ids,
            "affectedArtifacts": affected_artifacts,
            "riskNotes": risk_notes,
        }

    def _build_rule_operations(
        self,
        *,
        instruction: str,
        selected_node_id: str | None,
        flat_nodes: list[dict[str, Any]],
    ) -> tuple[list[dict[str, Any]], list[str]]:
        notes: list[str] = []
        text = instruction or ""

        if self._contains_any(text, ["删除", "移除", "去掉"]):
            target_node = self._find_best_mentioned_node(text, flat_nodes)
            if target_node and target_node.get("id"):
                return [{"op": "DELETE_NODE", "nodeId": str(target_node["id"])}], [
                    "识别到删除类操作，删除属于高风险操作，请确认目标节点无误。"
                ]
            return [], ["识别到删除意图，但没有定位到明确节点。"]

        if self._contains_any(text, ["移动", "挪到", "移到", "放到", "归到"]) and self._contains_any(text, ["下面", "下方", "模块", "页面", "节点"]):
            moving_node = self._find_best_mentioned_node(text, flat_nodes)
            new_parent = self._find_parent_by_placement_text(text, flat_nodes)
            if moving_node and new_parent and moving_node.get("id") != new_parent.get("id"):
                return [
                    {
                        "op": "MOVE_NODE",
                        "nodeId": str(moving_node["id"]),
                        "newParentId": str(new_parent["id"]),
                    }
                ], ["规则兜底识别为移动节点，请确认移动对象和新父节点。"]
            notes.append("疑似移动节点意图，但无法同时定位移动节点和新父节点。")

        if self._looks_like_inline_page_asset_edit(text):
            return [], ["识别到用户是在已有页面内增加入口、按钮、字段或规则，应走资产局部修订，不生成模块树 Patch。"]

        add_target = self._extract_add_target(text)
        if add_target:
            module_name, module_type = add_target
            parent = self._find_parent_by_instruction(
                instruction=text,
                selected_node_id=selected_node_id,
                flat_nodes=flat_nodes,
                target_name=module_name,
            )
            parent_id = str(parent["id"]) if parent and parent.get("id") else selected_node_id
            if not parent_id and module_type != "DOMAIN":
                notes.append("未定位到父节点，新增节点将作为根级节点提案；应用前建议补充父节点。")

            return [
                {
                    "op": "ADD_NODE",
                    "parentId": parent_id,
                    "moduleName": module_name,
                    "moduleKey": self._generate_module_key(module_name, module_type),
                    "moduleType": module_type,
                    "extra": {
                        "source": "ModuleTreePatchAgentRuleFallback",
                        "originalInstruction": instruction,
                    },
                }
            ], notes + ["规则兜底识别为新增节点，建议应用前确认父节点和节点类型。"]

        rename_target = self._extract_rename_target(text)
        if rename_target:
            old_node = self._find_best_mentioned_node(text, flat_nodes)
            if old_node and old_node.get("id"):
                new_name, module_type = rename_target
                operation: dict[str, Any] = {
                    "op": "UPDATE_NODE",
                    "nodeId": str(old_node["id"]),
                    "moduleName": new_name,
                    "moduleKey": self._generate_module_key(new_name, module_type),
                }
                if module_type:
                    operation["moduleType"] = module_type
                return [operation], ["规则兜底识别为重命名/更新节点，请确认目标节点和新名称。"]

        return [], notes or ["规则兜底未识别出可执行模块树 Patch。"]

    def _looks_like_inline_page_asset_edit(self, text: str) -> bool:
        normalized = self._normalize_text(text)
        has_page_context = any(keyword in normalized for keyword in ["登录页", "注册页", "页面", "页"])
        has_inline_object = any(keyword in normalized for keyword in [
            "入口", "按钮", "字段", "提示", "文案", "校验", "规则", "状态", "弹窗", "表单项", "链接", "勾选框",
        ])
        has_structural_keyword = any(keyword in normalized for keyword in [
            "新增页面", "增加页面", "添加页面", "创建页面", "新建页面", "新增页", "增加页", "添加页", "创建页", "新建页",
        ])
        return has_page_context and has_inline_object and not has_structural_keyword

    def _extract_add_target(self, text: str) -> tuple[str, str] | None:
        patterns = [
            r"(?:新增|增加|添加|创建|新建|加一个|加个|补一个|补充)(?:一个|个)?(?P<name>[\u4e00-\u9fa5A-Za-z0-9_\- ]{2,40}?)(?P<kind>页面|页|功能点|功能|子模块|模块|节点)",
            r"给.+?(?:新增|增加|添加|创建|新建|加一个|加个)(?:一个|个)?(?P<name>[\u4e00-\u9fa5A-Za-z0-9_\- ]{2,40}?)(?P<kind>页面|页|功能点|功能|子模块|模块|节点)",
        ]

        for pattern in patterns:
            match = re.search(pattern, text)
            if not match:
                continue

            name = self._clean_text(match.group("name"))
            kind = self._clean_text(match.group("kind"))
            module_type = self._module_type_from_kind(kind, name)

            if not name:
                continue

            if kind in {"页", "页面"} and not name.endswith(("页", "页面")):
                name = f"{name}页"
            elif kind == "功能点" and not name.endswith("功能点"):
                name = f"{name}功能点"
            elif kind == "功能" and not name.endswith(("功能", "功能点")):
                name = f"{name}功能"
            elif kind == "子模块" and not name.endswith("子模块"):
                name = f"{name}子模块"
            elif kind == "模块" and not name.endswith("模块"):
                name = f"{name}模块"

            return name, module_type

        return None

    def _extract_rename_target(self, text: str) -> tuple[str, str | None] | None:
        match = re.search(
            r"(?:改名为|重命名为|名称改为|改成)(?P<name>[\u4e00-\u9fa5A-Za-z0-9_\- ]{2,40}?)(?P<kind>页面|页|功能点|功能|子模块|模块|节点)?(?:。|，|,|$)",
            text,
        )
        if not match:
            return None

        name = self._clean_text(match.group("name"))
        kind = self._clean_text(match.group("kind"))
        module_type = self._module_type_from_kind(kind, name) if kind else None
        return (name, module_type) if name else None

    def _find_parent_by_instruction(
        self,
        *,
        instruction: str,
        selected_node_id: str | None,
        flat_nodes: list[dict[str, Any]],
        target_name: str,
    ) -> dict[str, Any] | None:
        # Phase 6-B: explicit user target beats current UI selection.
        explicit_parent = self._find_parent_by_placement_text(instruction, flat_nodes)
        if explicit_parent:
            return explicit_parent

        mentioned = self._find_best_mentioned_node(
            instruction,
            flat_nodes,
            exclude_names=[target_name],
        )
        if mentioned:
            return mentioned

        semantic_parent = self._find_semantic_parent_for_add(
            instruction=instruction,
            target_name=target_name,
            flat_nodes=flat_nodes,
        )
        if semantic_parent:
            return semantic_parent

        return self._find_node_by_id(flat_nodes, selected_node_id)

    def _find_semantic_parent_for_add(
        self,
        *,
        instruction: str,
        target_name: str,
        flat_nodes: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        normalized_instruction = self._normalize_text(instruction)
        normalized_target = self._normalize_text(target_name)

        preferred_names: list[str] = []
        if "用户中心" in instruction or "用户" in instruction:
            preferred_names.extend(["登录注册", "用户中心", "账号中心", "用户管理"])
        if any(keyword in normalized_target for keyword in ["密码找回", "找回密码", "忘记密码"]):
            preferred_names.extend(["登录注册", "用户中心", "账号中心", "用户管理", "登录"])
        if "注册" in instruction:
            preferred_names.extend(["登录注册", "注册页", "用户中心"])
        if "登录" in instruction:
            preferred_names.extend(["登录注册", "登录页", "用户中心"])

        for preferred_name in preferred_names:
            matched = self._find_node_by_exact_or_contains_name(preferred_name, flat_nodes)
            if matched:
                return matched

        # When a parent is explicitly mentioned as a business word but not matched by placement phrase,
        # still prefer it over current selected node.
        for node in sorted(flat_nodes, key=lambda item: len(str(item.get("moduleName") or "")), reverse=True):
            name = str(node.get("moduleName") or node.get("name") or "").strip()
            key = str(node.get("moduleKey") or node.get("key") or "").strip()
            for candidate in [name, key]:
                normalized_candidate = self._normalize_text(candidate)
                if normalized_candidate and normalized_candidate in normalized_instruction:
                    return node

        return None

    def _find_node_by_exact_or_contains_name(
        self,
        expected_name: str,
        flat_nodes: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        normalized_expected = self._normalize_text(expected_name)
        if not normalized_expected:
            return None

        candidates: list[tuple[int, dict[str, Any]]] = []
        for node in flat_nodes:
            name = str(node.get("moduleName") or node.get("name") or "").strip()
            key = str(node.get("moduleKey") or node.get("key") or "").strip()
            path = str(node.get("modulePath") or "").strip()
            normalized_values = [self._normalize_text(item) for item in [name, key, path] if item]
            score = 0
            for normalized_value in normalized_values:
                if normalized_value == normalized_expected:
                    score = max(score, 100 + len(normalized_value))
                elif normalized_expected in normalized_value or normalized_value in normalized_expected:
                    score = max(score, len(normalized_value))
            if score > 0:
                candidates.append((score, node))

        if not candidates:
            return None
        candidates.sort(key=lambda item: item[0], reverse=True)
        return candidates[0][1]

    def _find_parent_by_placement_text(
        self,
        text: str,
        flat_nodes: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        for node in sorted(flat_nodes, key=lambda item: len(str(item.get("moduleName") or "")), reverse=True):
            name = str(node.get("moduleName") or node.get("name") or "").strip()
            key = str(node.get("moduleKey") or node.get("key") or "").strip()
            if not name and not key:
                continue

            candidates = [name, key]
            for candidate in candidates:
                if candidate and (
                    f"放在{candidate}" in text
                    or f"放到{candidate}" in text
                    or f"归到{candidate}" in text
                    or f"移动到{candidate}" in text
                    or f"{candidate}下面" in text
                    or f"{candidate}下方" in text
                    or f"给{candidate}" in text
                ):
                    return node
        return None

    def _find_best_mentioned_node(
        self,
        text: str,
        flat_nodes: list[dict[str, Any]],
        exclude_names: list[str] | None = None,
    ) -> dict[str, Any] | None:
        exclude_names = exclude_names or []
        normalized_excludes = {self._normalize_text(item) for item in exclude_names if item}

        candidates: list[tuple[int, dict[str, Any]]] = []
        normalized_text = self._normalize_text(text)

        for node in flat_nodes:
            names = [
                str(node.get("moduleName") or node.get("name") or "").strip(),
                str(node.get("moduleKey") or node.get("key") or "").strip(),
                str(node.get("modulePath") or "").strip(),
            ]
            score = 0
            for name in names:
                normalized_name = self._normalize_text(name)
                if not normalized_name or normalized_name in normalized_excludes:
                    continue
                if normalized_name in normalized_text:
                    score = max(score, len(normalized_name))
            if score > 0:
                candidates.append((score, node))

        if not candidates:
            return None

        candidates.sort(key=lambda item: item[0], reverse=True)
        return candidates[0][1]

    def _normalize_operations_for_markdown(self, operations: list[dict[str, Any]]) -> list[str]:
        if not operations:
            return ["无可执行操作。"]
        result: list[str] = []
        for operation in operations:
            op = operation.get("op")
            if op == "ADD_NODE":
                result.append(
                    f"ADD_NODE：在 {operation.get('parentId') or '根节点'} 下新增 "
                    f"{operation.get('moduleType')} / {operation.get('moduleName')} / {operation.get('moduleKey')}"
                )
            elif op == "UPDATE_NODE":
                result.append(
                    f"UPDATE_NODE：更新 {operation.get('nodeId')} 为 "
                    f"{operation.get('moduleName') or operation.get('moduleKey') or operation.get('moduleType')}"
                )
            elif op == "MOVE_NODE":
                result.append(
                    f"MOVE_NODE：移动 {operation.get('nodeId')} 到 {operation.get('newParentId') or '根节点'}"
                )
            elif op == "DELETE_NODE":
                result.append(f"DELETE_NODE：删除 {operation.get('nodeId')}")
            else:
                result.append(self._json_dump(operation))
        return result

    def _build_markdown(
        self,
        *,
        description: str,
        operations: list[dict[str, Any]],
        impact: dict[str, Any],
        validation_notes: list[str],
        confidence: float,
    ) -> str:
        lines = [
            "# 模块树变更提案",
            "",
            f"摘要：{description}",
            "",
            f"Agent 置信度：{confidence:.2f}",
            "",
            "## 操作列表",
        ]
        lines.extend([f"- {item}" for item in self._normalize_operations_for_markdown(operations)])
        lines.extend(["", "## 影响范围", f"- {impact.get('summary') or '暂无影响摘要。'}"])

        affected_artifacts = self._string_list(impact.get("affectedArtifacts"))
        if affected_artifacts:
            lines.extend(["", "### 可能影响资产"])
            lines.extend([f"- {item}" for item in affected_artifacts])

        risk_notes = self._string_list(impact.get("riskNotes"))
        if risk_notes:
            lines.extend(["", "## 风险提示"])
            lines.extend([f"- {item}" for item in risk_notes])

        if validation_notes:
            lines.extend(["", "## 规范化提示"])
            lines.extend([f"- {item}" for item in validation_notes])

        lines.extend(["", "> 该结果只是提案，必须由用户确认后才会应用到模块树。"])
        return "\n".join(lines)

    def _default_description(self, instruction: str, operations: list[dict[str, Any]]) -> str:
        if operations:
            first = operations[0]
            if first.get("op") == "ADD_NODE":
                return f"新增模块树节点：{first.get('moduleName') or '新节点'}"
            if first.get("op") == "UPDATE_NODE":
                return "更新模块树节点信息"
            if first.get("op") == "MOVE_NODE":
                return "移动模块树节点位置"
            if first.get("op") == "DELETE_NODE":
                return "删除模块树节点"
        return f"根据用户说明生成模块树变更提案：{self._trim(instruction, 80)}"

    def _infer_affected_artifacts(self, operations: list[dict[str, Any]]) -> list[str]:
        if not operations:
            return []

        types = {str(operation.get("moduleType") or "").upper() for operation in operations}
        ops = {str(operation.get("op") or "").upper() for operation in operations}

        result: list[str] = ["MODULE_PRD"]

        if "PAGE" in types:
            result.extend(["PAGE_SPEC", "UI_CONTRACT", "TEST_SPEC", "DEV_HANDOFF_PACKAGE"])
        elif "FEATURE" in types:
            result.extend(["PAGE_SPEC", "TEST_SPEC", "UI_CONTRACT"])
        elif "MODULE" in types or "SUBMODULE" in types or "DOMAIN" in types:
            result.extend(["BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC"])

        if "DELETE_NODE" in ops or "MOVE_NODE" in ops:
            result.extend(["BUSINESS_FLOW", "PAGE_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"])

        return self._dedupe(result)

    def _affected_node_ids(
        self,
        operations: list[dict[str, Any]],
        selected_node_id: str | None,
    ) -> list[str]:
        result: list[str] = []
        if selected_node_id:
            result.append(selected_node_id)
        for operation in operations:
            for key in ("nodeId", "parentId", "newParentId"):
                value = operation.get(key)
                if value:
                    result.append(str(value))
        return self._dedupe(result)

    def _compact_tree(self, nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for node in nodes:
            children = self._as_node_list(node.get("children"))
            item = {
                "id": node.get("id"),
                "parentId": node.get("parentId"),
                "moduleName": node.get("moduleName") or node.get("name"),
                "moduleKey": node.get("moduleKey") or node.get("key"),
                "moduleType": node.get("moduleType") or node.get("type"),
                "modulePath": node.get("modulePath"),
                "children": self._compact_tree(children),
            }
            result.append(item)
        return result

    def _flatten_tree(
        self,
        nodes: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        def walk(items: list[dict[str, Any]], parent_id: str | None = None) -> None:
            for item in items:
                if not isinstance(item, dict):
                    continue
                normalized = dict(item)
                if normalized.get("parentId") is None and parent_id is not None:
                    normalized["parentId"] = parent_id
                result.append(normalized)
                node_id = normalized.get("id")
                children = self._as_node_list(normalized.get("children"))
                walk(children, str(node_id) if node_id else parent_id)

        walk(nodes)
        return result

    def _find_node_by_id(
        self,
        flat_nodes: list[dict[str, Any]],
        node_id: str | None,
    ) -> dict[str, Any] | None:
        if not node_id:
            return None
        for node in flat_nodes:
            if str(node.get("id") or "") == str(node_id):
                return node
        return None

    def _normalize_module_type(self, value: Any, module_name: str | None = None) -> str | None:
        raw = str(value or "").strip().upper()
        aliases = {
            "DOMAIN_MODULE": "DOMAIN",
            "BUSINESS_DOMAIN": "DOMAIN",
            "BUSINESS_MODULE": "MODULE",
            "MODULE_NODE": "MODULE",
            "SUB_MODULE": "SUBMODULE",
            "SUBMODULE_NODE": "SUBMODULE",
            "PAGE_NODE": "PAGE",
            "FEATURE_NODE": "FEATURE",
            "FUNCTION": "FEATURE",
            "FUNCTION_POINT": "FEATURE",
        }
        normalized = aliases.get(raw, raw)
        if normalized in self.ALLOWED_TYPES:
            return normalized

        if module_name:
            if module_name.endswith(("页", "页面")):
                return "PAGE"
            if module_name.endswith(("功能", "功能点")):
                return "FEATURE"
            if module_name.endswith("子模块"):
                return "SUBMODULE"
            if module_name.endswith("模块"):
                return "MODULE"

        return None

    def _module_type_from_kind(self, kind: str, name: str) -> str:
        if kind in {"页", "页面"}:
            return "PAGE"
        if kind in {"功能", "功能点"}:
            return "FEATURE"
        if kind == "子模块":
            return "SUBMODULE"
        if kind == "模块":
            return "MODULE"
        return self._normalize_module_type(None, name) or "PAGE"

    def _generate_module_key(self, module_name: str, module_type: str | None = None) -> str:
        text = module_name.strip().lower()
        replacements = [
            ("用户中心", "user_center"),
            ("用户", "user"),
            ("密码找回", "password_recovery"),
            ("找回密码", "password_recovery"),
            ("密码", "password"),
            ("找回", "recovery"),
            ("登录注册", "auth"),
            ("登录", "login"),
            ("注册", "register"),
            ("发送验证码", "send_verification_code"),
            ("验证码", "verification_code"),
            ("订单", "order"),
            ("支付", "payment"),
            ("退款", "refund"),
            ("库存", "inventory"),
            ("后台", "admin"),
            ("管理", "management"),
            ("列表", "list"),
            ("详情", "detail"),
            ("编辑", "edit"),
            ("新增", "create"),
            ("设置", "settings"),
            ("页面", "page"),
            ("页", "page"),
            ("功能点", "feature"),
            ("功能", "feature"),
            ("模块", "module"),
            ("子模块", "submodule"),
        ]

        for zh, en in replacements:
            text = text.replace(zh, f"_{en}_")

        text = re.sub(r"[^a-z0-9]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")

        if not text:
            digest = hashlib.md5(module_name.encode("utf-8")).hexdigest()[:8]
            text = f"node_{digest}"

        suffix = {
            "PAGE": "page",
            "FEATURE": "feature",
            "MODULE": "module",
            "SUBMODULE": "submodule",
            "DOMAIN": "domain",
        }.get(str(module_type or "").upper())

        if suffix and not text.endswith(f"_{suffix}") and text != suffix:
            text = f"{text}_{suffix}"

        return text[:80]

    def _normalize_impact_artifact(self, value: str) -> str | None:
        raw = str(value or "").strip().upper()
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
        return raw if raw in allowed else None

    def _normalize_artifact_list(self, value: Any) -> list[str]:
        result: list[str] = []
        for item in self._string_list(value):
            normalized = self._normalize_impact_artifact(item)
            if normalized:
                result.append(normalized)
        return self._dedupe(result)

    def _as_node_list(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        return [dict(item) for item in value if isinstance(item, dict)]

    def _as_dict(self, value: Any) -> dict[str, Any]:
        return dict(value) if isinstance(value, dict) else {}

    def _string_list(self, value: Any) -> list[str]:
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    def _float_value(self, value: Any, fallback: float) -> float:
        try:
            number = float(value)
            if number < 0:
                return 0.0
            if number > 1:
                return 1.0
            return number
        except Exception:
            return fallback

    def _int_or_none(self, value: Any) -> int | None:
        if value is None or value == "":
            return None
        try:
            return int(value)
        except Exception:
            return None

    def _clean_text(self, value: Any) -> str:
        text = str(value or "").strip()
        return re.sub(r"\s+", " ", text)

    def _clean_nullable(self, value: Any) -> str | None:
        text = self._clean_text(value)
        if not text or text.lower() in {"null", "none", "undefined"}:
            return None
        return text

    def _normalize_text(self, value: str) -> str:
        return re.sub(r"\s+", "", str(value or "")).lower()

    def _contains_any(self, text: str, keywords: list[str]) -> bool:
        return any(keyword in text for keyword in keywords)

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        for value in values:
            if value and value not in result:
                result.append(value)
        return result

    def _trim_json(self, value: Any, max_chars: int) -> str:
        try:
            return self._trim(json.dumps(value, ensure_ascii=False, default=str), max_chars)
        except Exception:
            return self._trim(str(value), max_chars)

    def _json_dump(self, value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, default=str)

    def _trim(self, value: str, max_chars: int) -> str:
        text = str(value or "")
        if len(text) <= max_chars:
            return text
        return text[:max_chars] + "\n...[TRUNCATED]"
