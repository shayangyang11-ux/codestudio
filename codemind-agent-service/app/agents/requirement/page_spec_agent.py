# A9.10.14.4.2.2_EXISTING_PRD_SEMANTIC_BACKFILL
# A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN

from __future__ import annotations
import re

import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.page_spec import (
    PAGE_SPEC_LLM_JSON_SCHEMA,
    PageActionSpec,
    PageSpecArtifact,
    PageSpecLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.graphs.requirement.context.requirement_asset_contract_policy import (
    RequirementAssetContractPolicy,
)
from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
from app.agents.requirement.prd_semantic_contract import build_page_semantic_contract
from app.agents.requirement.requirement_asset_readiness_guard import (
    assert_requirement_asset_ready_for_page_spec,
)  # A9.10.14.4.3.15.3.3_REQUIREMENT_ASSET_READINESS_GATE


class PageSpecAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()
        self.asset_contract_policy = RequirementAssetContractPolicy()

    async def run(self, request: AgentTaskRequest) -> PageSpecArtifact:

        # A9.10.14.4.3.15.3.3_REQUIREMENT_ASSET_READINESS_GATE
        # PAGE_SPEC is internal, but it must still be generated from a
        # stable MODULE_PRD.page_requirements contract. Fail before LLM
        # to save tokens and prevent wrong PAGE shapes.
        requirement_asset_readiness = assert_requirement_asset_ready_for_page_spec(request)

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("page_spec.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )


        # A9.10.14.4.3.14_PAGE_SPEC_ALLOW_LLM_AFTER_EMPTY_CONTRACT
        # Final user-facing flow is one generation pipeline.  A deterministic
        # PAGE_SPEC is still used when it is strong enough, but an empty PRD
        # semantic contract must no longer make the LLM branch unreachable.
        locked_spec = self._a9_10_14_4_3_13_2_1_build_no_llm_page_spec(request)
        if locked_spec is not None:
            print(
                "[A9.10.14.4.3.14] PageSpecAgent returned deterministic PAGE_SPEC seed",
                flush=True,
            )
            return locked_spec

        print(
            "[A9.10.14.4.3.14] PageSpecAgent PRD contract seed is empty; using one LLM pass for PAGE_SPEC",
            flush=True,
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="page_spec_output",
            output_json_schema=PAGE_SPEC_LLM_JSON_SCHEMA,
        )

        parsed = PageSpecLlmOutput.model_validate_json(llm_result.text)
        parsed = self._ensure_feature_action_specs(
            parsed,
            request,
        )
        markdown = self._build_markdown(parsed)
        # A9.10.14.4.3.13.1_PAGE_SPEC_SEMANTIC_CONTRACT_LOCK
        page_semantic_contract = self._a9_10_14_4_3_13_1_build_page_semantic_contract(request)

        return PageSpecArtifact(
            basic_info=parsed.basic_info,
            layout_sections=parsed.layout_sections,
            component_specs=parsed.component_specs,
            action_specs=parsed.action_specs,
            form_fields=parsed.form_fields,
            table_columns=parsed.table_columns,
            api_bindings=parsed.api_bindings,
            page_semantic_contract=page_semantic_contract,
            page_states=parsed.page_states,
            permission_specs=parsed.permission_specs,
            exception_specs=parsed.exception_specs,
            acceptance_criteria=parsed.acceptance_criteria,
            downstream_hints=parsed.downstream_hints,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )


    # A9.10.14.4.3.13.2.1_NO_LLM_HOTFIX_PAGE_SPEC
    def _a9_10_14_4_3_13_2_1_build_no_llm_page_spec(
        self,
        request: AgentTaskRequest,
    ) -> PageSpecArtifact | None:
        contract = self._a9_10_14_4_3_13_1_build_page_semantic_contract(request)
        if not isinstance(contract, dict) or not (
            contract.get("filterFields")
            or contract.get("tableColumns")
            or contract.get("formFields")
            or contract.get("primaryActions")
            or contract.get("rowActions")
        ):
            return None

        context_package = request.context_package or {}
        input_payload = request.input or {}
        current_module = self._extract_current_module(context_package, input_payload)

        def text(*values: Any) -> str:
            for value in values:
                if value is not None and str(value).strip():
                    return str(value).strip()
            return ""

        def safe(value: Any, fallback: str) -> str:
            return self._safe_key(text(value), fallback)

        def normalize_page_type(value: Any) -> str:
            raw = str(value or "").strip().upper()
            if raw in {"LIST", "DETAIL", "FORM", "DASHBOARD", "WORKBENCH", "SETTING", "WIZARD", "OTHER"}:
                return raw
            if raw in {"APPROVAL", "AUDIT"}:
                return "FORM"
            return "LIST" if contract.get("tableColumns") else "FORM" if contract.get("formFields") else "OTHER"

        page_name = text(
            current_module.get("module_name"),
            current_module.get("moduleName"),
            current_module.get("pageName"),
            current_module.get("name"),
            contract.get("pageName"),
            "当前页面",
        )
        page_key = safe(
            text(
                current_module.get("module_key"),
                current_module.get("moduleKey"),
                current_module.get("pageKey"),
                current_module.get("key"),
                contract.get("pageKey"),
                page_name,
            ),
            "page",
        )
        related_module_name = text(current_module.get("parent_module_name"), current_module.get("modulePath"), page_name)
        page_type = normalize_page_type(contract.get("pageType"))
        route = text(current_module.get("routePath"), current_module.get("route"), f"/{page_key.replace('_', '-')}")

        filter_fields = [item for item in contract.get("filterFields") or [] if isinstance(item, dict)]
        table_columns = [item for item in contract.get("tableColumns") or [] if isinstance(item, dict)]
        form_fields = [item for item in (contract.get("formFields") or contract.get("tableColumns") or contract.get("filterFields") or []) if isinstance(item, dict)]
        primary_actions = [item for item in contract.get("primaryActions") or [] if isinstance(item, dict)]
        row_actions = [item for item in contract.get("rowActions") or [] if isinstance(item, dict)]

        def field_name(item: dict[str, Any]) -> str:
            return text(item.get("label"), item.get("fieldName"), item.get("fieldKey"), "字段")

        def field_key(item: dict[str, Any]) -> str:
            return safe(text(item.get("fieldKey"), item.get("key"), field_name(item)), "field")

        def action_name(item: dict[str, Any]) -> str:
            return text(item.get("label"), item.get("actionName"), item.get("actionKey"), "操作")

        def action_key(item: dict[str, Any]) -> str:
            return safe(text(item.get("actionKey"), item.get("key"), action_name(item)), "action")

        layout_sections: list[dict[str, Any]] = []
        component_specs: list[dict[str, Any]] = []

        if filter_fields:
            layout_sections.append({
                "section_key": f"{page_key}_filter_section",
                "section_name": "查询条件",
                "position": "页面上方",
                "description": "根据 PRD 明确筛选字段组织查询区域。",
                "components": ["查询筛选表单"],
            })
            component_specs.append({
                "component_key": f"{page_key}_filter_form",
                "component_name": "查询筛选表单",
                "component_type": "FORM",
                "description": "筛选字段：" + "、".join(field_name(item) for item in filter_fields),
                "data_source": "PRD pageSemanticContract.filterFields",
                "interactions": ["查询", "重置"],
                "display_rules": ["按 PRD 字段顺序展示，不自动增加关键词模板字段。"],
            })

        if table_columns:
            layout_sections.append({
                "section_key": f"{page_key}_table_section",
                "section_name": "数据列表",
                "position": "页面主体",
                "description": "根据 PRD 明确表格列展示业务数据。",
                "components": ["主要操作栏", "数据表格"],
            })
            component_specs.append({
                "component_key": f"{page_key}_data_table",
                "component_name": "数据表格",
                "component_type": "TABLE",
                "description": "表格列：" + "、".join(field_name(item) for item in table_columns),
                "data_source": "PRD pageSemanticContract.tableColumns",
                "interactions": [action_name(item) for item in row_actions],
                "display_rules": ["按 PRD 表格列顺序展示，不保留未声明模板列。"],
            })

        action_specs: list[dict[str, Any]] = []
        for item in [*primary_actions, *row_actions]:
            name = action_name(item)
            kind = str(item.get("kind") or "CUSTOM").upper()
            interaction_type = "QUERY_REFRESH"
            expected_result = f"页面完成“{name}”并展示模拟反馈。"
            if kind == "CREATE":
                interaction_type = "OPEN_MODAL"
                expected_result = "打开新建弹窗；提交后在当前 mock 列表中临时新增一条记录。"
            elif kind == "EDIT":
                interaction_type = "OPEN_MODAL"
                expected_result = "打开编辑弹窗；提交后在当前 mock 列表中临时更新当前记录。"
            elif kind == "DELETE":
                interaction_type = "STATE_CHANGE"
                expected_result = "确认后从当前 mock 列表中临时移除当前记录，刷新页面恢复。"
            elif kind in {"VIEW", "APPROVAL"} or item.get("navigationIntent"):
                interaction_type = "SUCCESS_NAVIGATION"
                expected_result = text(item.get("targetPageName"), f"进入{name}相关页面或展示模拟反馈。")
            elif kind in {"QUERY", "RESET", "REFRESH"}:
                interaction_type = "QUERY_REFRESH"
                expected_result = "刷新或重置当前模拟数据展示。"

            action_specs.append({
                "action_key": action_key(item),
                "action_name": name,
                "trigger_position": "页面主操作区" if item in primary_actions else "表格行操作区",
                "actor": "当前页面用户",
                "description": text(item.get("description"), f"PRD 明确要求的{name}操作。"),
                "interaction_type": interaction_type,
                "expected_result": expected_result,
                "preconditions": [],
                "business_rules": [],
                "success_feedback": f"{name}操作成功。",
                "failure_feedback": f"{name}操作失败时展示原因并允许重试。",
            })

        page_form_fields = [{
            "field_key": field_key(item),
            "field_name": field_name(item),
            "field_type": text(item.get("type"), "INPUT"),
            "required": bool(item.get("required", False)),
            "default_value": None,
            "validation_rules": [],
            "description": f"PRD 页面语义契约字段：{field_name(item)}。",
        } for item in form_fields]

        page_table_columns = [{
            "column_key": field_key(item),
            "column_name": field_name(item),
            "data_field": field_key(item),
            "display_type": text(item.get("type"), "TEXT"),
            "sortable": bool(item.get("sortable", False)),
            "filterable": any(field_key(item) == field_key(f) for f in filter_fields),
            "description": f"PRD 页面语义契约表格列：{field_name(item)}。",
        } for item in table_columns]

        markdown = "\n".join([
            f"# {page_name} 页面规范（PRD 契约锁定 / No LLM）",
            "",
            "## 页面契约",
            f"- 筛选字段：{'、'.join(field_name(item) for item in filter_fields) or '无'}",
            f"- 表格列：{'、'.join(field_name(item) for item in table_columns) or '无'}",
            f"- 主操作：{'、'.join(action_name(item) for item in primary_actions) or '无'}",
            f"- 行操作：{'、'.join(action_name(item) for item in row_actions) or '无'}",
            "",
            "本 PAGE_SPEC 由 MODULE_PRD 的 pageSemanticContract 确定性生成，未调用大模型。",
        ])

        return PageSpecArtifact(
            basic_info={
                "page_key": page_key,
                "page_name": page_name,
                "related_module_key": page_key,
                "related_module_name": related_module_name,
                "page_type": page_type,
                "route_suggestion": route,
                "page_goal": text(contract.get("description"), f"承载{page_name}业务操作。"),
                "primary_user_role": "当前页面用户",
            },
            layout_sections=layout_sections,
            component_specs=component_specs,
            action_specs=action_specs,
            form_fields=page_form_fields,
            table_columns=page_table_columns,
            api_bindings=[],
            page_semantic_contract=contract,
            page_states=[
                {"state_key": "DEFAULT", "state_name": "默认态", "description": "展示页面数据与操作入口。", "ui_behavior": "展示 PRD 契约字段和操作。"},
                {"state_key": "EMPTY", "state_name": "空状态", "description": "查询结果为空。", "ui_behavior": "展示空状态并允许重新查询。"},
                {"state_key": "SUCCESS", "state_name": "成功态", "description": "模拟操作成功。", "ui_behavior": "展示成功提示。"},
                {"state_key": "ERROR", "state_name": "错误态", "description": "模拟操作失败。", "ui_behavior": "展示失败原因并允许重试。"},
            ],
            permission_specs=[],
            exception_specs=[],
            acceptance_criteria=[{
                "criterion_key": "prd_contract_coverage",
                "description": "UI_CONTRACT 必须覆盖 PAGE_SPEC.page_semantic_contract 中明确声明的字段、按钮、表格列和行操作。",
                "verification_method": "PRD Coverage Gate / UI_CONTRACT Reconciler",
            }],
            downstream_hints={
                "data_model_hints": ["以 page_semantic_contract 中的字段和表格列作为数据建模候选。"],
                "api_contract_hints": ["后续开发阶段再将 mock 行为绑定真实接口。"],
                "test_spec_hints": ["验证 PRD 明确字段、按钮和行操作均可见且可模拟。"],
                "frontend_component_hints": ["UI_CONTRACT 应严格使用 page_semantic_contract 生成结构化组件。"],
                "dev_handoff_hints": ["Development Agent 以最终确认的 UI_CONTRACT 为准生成真实代码。"],
            },
            development_notes=["A9.10.14.4.3.13.2.1: PAGE_SPEC 由 PRD semantic contract 确定性生成，未调用大模型。"],
            testing_notes=["检查页面字段、按钮、表格列、行操作与 PRD 可见文档一致。"],
            analysis_summary="已从 MODULE_PRD 抽取页面语义契约并生成 PAGE_SPEC，未调用大模型。",
            markdown=markdown,
        )


    def _ensure_feature_action_specs(
        self,
        spec: PageSpecLlmOutput,
        request: AgentTaskRequest,
    ) -> PageSpecLlmOutput:
        """
        Phase 10-A4.3 safety completion.

        PAGE_SPEC is the canonical carrier of page-level behavior.
        For PAGE nodes, every current PAGE child FEATURE must have at least one
        action_specs item. Otherwise UI_CONTRACT readiness will block later.

        This does not create FEATURE_PRD. It only fills the PAGE_SPEC action list
        from current PAGE FEATURE nodes when the LLM omitted a feature.
        """
        feature_nodes = self._extract_page_feature_nodes(request)

        if not feature_nodes:
            return spec

        existing_actions = list(spec.action_specs or [])
        completed_actions = list(existing_actions)
        added_names: list[str] = []

        for index, feature in enumerate(feature_nodes):
            feature_key = self._feature_key(
                feature,
                index,
            )
            feature_name = self._feature_name(
                feature,
                index,
            )

            if self._action_specs_cover_feature(
                existing_actions,
                feature_key,
                feature_name,
            ):
                continue

            completed_actions.append(
                self._build_fallback_action_spec(
                    feature,
                    index,
                    feature_key,
                    feature_name,
                )
            )
            added_names.append(feature_name)

        if not added_names:
            return spec

        notes = list(spec.development_notes or [])
        notes.append(
            "系统已根据当前 PAGE 下的 FEATURE 自动补齐基础交互定义："
            + "、".join(added_names)
            + "。"
        )

        testing_notes = list(spec.testing_notes or [])
        testing_notes.append(
            "UI_CONTRACT 生成前应确认 PAGE_SPEC 的 action_specs 已覆盖当前 PAGE 下全部 FEATURE。"
        )

        return spec.model_copy(
            update={
                "action_specs": completed_actions,
                "development_notes": notes,
                "testing_notes": testing_notes,
                "analysis_summary": (
                    spec.analysis_summary
                    + " 已执行 PAGE FEATURE 基础交互补齐，确保页面规范可作为 UI_CONTRACT 生成输入。"
                ),
            }
        )

    def _extract_page_feature_nodes(
        self,
        request: AgentTaskRequest,
    ) -> list[dict[str, Any]]:
        context_package = request.context_package or {}
        input_payload = request.input or {}

        candidates: list[dict[str, Any]] = []

        def extend_from(value: Any) -> None:
            if isinstance(value, list):
                candidates.extend(
                    item
                    for item in value
                    if isinstance(item, dict)
                )

        extend_from(context_package.get("feature_nodes"))
        extend_from(input_payload.get("feature_nodes"))

        readiness_candidates = [
            context_package.get("ui_contract_readiness"),
            input_payload.get("ui_contract_readiness"),
            (context_package.get("input") or {}).get("ui_contract_readiness")
            if isinstance(context_package.get("input"), dict)
            else None,
        ]

        for readiness in readiness_candidates:
            if isinstance(readiness, dict):
                extend_from(readiness.get("features"))

        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, item in enumerate(candidates):
            feature_key = self._feature_key(
                item,
                index,
            )
            feature_name = self._feature_name(
                item,
                index,
            )
            identity = feature_key or feature_name

            if not identity or identity in seen:
                continue

            result.append(item)
            seen.add(identity)

        return result

    def _action_specs_cover_feature(
        self,
        actions: list[PageActionSpec],
        feature_key: str,
        feature_name: str,
    ) -> bool:
        feature_key_norm = self._normalize_match_text(feature_key)
        feature_name_norm = self._normalize_match_text(feature_name)

        for action in actions:
            action_text = self._normalize_match_text(
                " ".join(
                    [
                        action.action_key,
                        action.action_name,
                        action.description,
                        action.trigger_position,
                        action.interaction_type,
                        action.expected_result,
                    ]
                )
            )

            if feature_key_norm and feature_key_norm in action_text:
                return True

            if feature_name_norm and feature_name_norm in action_text:
                return True

            if self._semantic_action_match(
                action_text,
                feature_key_norm,
                feature_name_norm,
            ):
                return True

        return False

    def _build_fallback_action_spec(
        self,
        feature: dict[str, Any],
        index: int,
        feature_key: str,
        feature_name: str,
    ) -> PageActionSpec:
        interaction_type = self._infer_interaction_type(
            feature_key,
            feature_name,
            feature,
        )

        trigger = self._infer_trigger(
            interaction_type,
            feature_name,
        )
        expected_result = self._infer_expected_result(
            interaction_type,
            feature_name,
        )

        return PageActionSpec(
            action_key=self._safe_key(
                feature_key,
                f"feature_action_{index + 1}",
            ),
            action_name=feature_name or f"功能点 {index + 1}",
            trigger_position=trigger,
            actor="当前页面用户",
            description=self._first_text(
                feature,
                [
                    "description",
                    "featureDescription",
                    "summary",
                    "requirement",
                ],
            )
            or f"覆盖页面功能点：{feature_name}。",
            interaction_type=interaction_type,
            expected_result=expected_result,
            preconditions=[],
            business_rules=[],
            success_feedback=self._infer_success_feedback(
                interaction_type,
                feature_name,
            ),
            failure_feedback=self._infer_failure_feedback(
                interaction_type,
                feature_name,
            ),
        )

    def _infer_interaction_type(
        self,
        feature_key: str,
        feature_name: str,
        feature: dict[str, Any],
    ) -> str:
        explicit = self._first_text(
            feature,
            [
                "interactionType",
                "interaction_type",
                "actionType",
                "type",
                "featureKind",
                "feature_kind",
            ],
        )

        if explicit:
            value = explicit.strip().upper()
            if value in {
                "FORM_SUBMIT",
                "FIELD_VALIDATION",
                "ERROR_FEEDBACK",
                "SUCCESS_NAVIGATION",
                "QUERY_REFRESH",
                "OPEN_MODAL",
                "SUCCESS_FEEDBACK",
                "STATE_CHANGE",
            }:
                return value

        text = self._normalize_match_text(
            " ".join(
                [
                    feature_key,
                    feature_name,
                    str(feature),
                ]
            )
        )

        if any(token in text for token in ["错误", "失败", "异常", "提示", "error", "fail"]):
            return "ERROR_FEEDBACK"

        if any(token in text for token in ["成功", "跳转", "进入", "redirect", "navigate", "success"]):
            return "SUCCESS_NAVIGATION"

        if any(token in text for token in ["校验", "验证", "validate", "validation"]):
            return "FIELD_VALIDATION"

        if any(token in text for token in ["查询", "搜索", "筛选", "刷新", "search", "query", "filter"]):
            return "QUERY_REFRESH"

        if any(token in text for token in ["弹窗", "打开", "modal", "dialog", "open"]):
            return "OPEN_MODAL"

        if any(token in text for token in ["表单", "提交", "注册", "登录", "保存", "submit", "form", "register", "login", "save"]):
            return "FORM_SUBMIT"

        return "FORM_SUBMIT"

    def _infer_trigger(
        self,
        interaction_type: str,
        feature_name: str,
    ) -> str:
        if interaction_type == "ERROR_FEEDBACK":
            return "用户提交表单、字段校验失败或接口返回失败结果时触发。"

        if interaction_type == "SUCCESS_NAVIGATION":
            return "用户完成关键操作且接口返回成功结果时触发。"

        if interaction_type == "FIELD_VALIDATION":
            return "用户输入字段、字段失焦或提交表单前触发。"

        if interaction_type == "QUERY_REFRESH":
            return "用户输入查询条件、点击查询/刷新或进入页面加载数据时触发。"

        if interaction_type == "OPEN_MODAL":
            return "用户点击相关按钮、链接或操作入口时触发。"

        return f"用户在页面中执行“{feature_name}”相关操作时触发。"

    def _infer_expected_result(
        self,
        interaction_type: str,
        feature_name: str,
    ) -> str:
        if interaction_type == "ERROR_FEEDBACK":
            return "页面展示清晰错误提示，保留用户已输入内容，并允许用户修改后重新提交。"

        if interaction_type == "SUCCESS_NAVIGATION":
            return "页面展示成功反馈，并按产品流程跳转到目标页面或进入成功态。"

        if interaction_type == "FIELD_VALIDATION":
            return "系统完成字段合法性检查，错误字段给出明确提示，合法字段允许继续操作。"

        if interaction_type == "QUERY_REFRESH":
            return "页面刷新数据区域，展示查询结果、空状态或错误状态。"

        if interaction_type == "OPEN_MODAL":
            return "页面打开对应弹窗或局部操作区，用户可以继续确认或取消。"

        return f"页面完成“{feature_name}”对应业务动作，并反馈处理结果。"

    def _infer_success_feedback(
        self,
        interaction_type: str,
        feature_name: str,
    ) -> str:
        if interaction_type == "ERROR_FEEDBACK":
            return "用户修正后错误提示消失，可重新提交。"

        if interaction_type == "SUCCESS_NAVIGATION":
            return "展示操作成功提示，并进入目标页面或成功状态。"

        if interaction_type == "QUERY_REFRESH":
            return "展示最新查询结果。"

        return f"{feature_name}处理成功，页面给出明确成功反馈。"

    def _infer_failure_feedback(
        self,
        interaction_type: str,
        feature_name: str,
    ) -> str:
        if interaction_type == "SUCCESS_NAVIGATION":
            return "跳转前置操作失败时停留当前页面，并展示失败原因。"

        if interaction_type == "QUERY_REFRESH":
            return "查询失败时展示错误提示，并保留查询条件。"

        return f"{feature_name}处理失败时展示错误原因，并允许用户修正后重试。"

    def _semantic_action_match(
        self,
        action_text: str,
        feature_key: str,
        feature_name: str,
    ) -> bool:
        feature_text = f"{feature_key} {feature_name}"

        pairs = [
            ["注册", "register"],
            ["登录", "login"],
            ["错误", "error"],
            ["失败", "fail"],
            ["成功", "success"],
            ["跳转", "navigate"],
            ["表单", "form"],
            ["提交", "submit"],
            ["查询", "query"],
            ["搜索", "search"],
        ]

        for pair in pairs:
            if any(token in feature_text for token in pair) and any(token in action_text for token in pair):
                return True

        return False

    def _feature_key(
        self,
        feature: dict[str, Any],
        index: int,
    ) -> str:
        return str(
            feature.get("module_key")
            or feature.get("moduleKey")
            or feature.get("featureKey")
            or feature.get("feature_key")
            or feature.get("key")
            or feature.get("module_id")
            or feature.get("id")
            or f"feature_{index + 1}"
        ).strip()

    def _feature_name(
        self,
        feature: dict[str, Any],
        index: int,
    ) -> str:
        return str(
            feature.get("module_name")
            or feature.get("moduleName")
            or feature.get("featureName")
            or feature.get("feature_name")
            or feature.get("name")
            or self._feature_key(feature, index)
            or f"功能点 {index + 1}"
        ).strip()

    def _first_text(
        self,
        source: dict[str, Any],
        keys: list[str],
    ) -> str:
        for key in keys:
            value = source.get(key)

            if value is not None and str(value).strip():
                return str(value).strip()

        extra = source.get("extra")

        if isinstance(extra, dict):
            for key in keys:
                value = extra.get(key)

                if value is not None and str(value).strip():
                    return str(value).strip()

        return ""

    def _normalize_match_text(
        self,
        value: str,
    ) -> str:
        return re.sub(
            r"[\s_\-/\-]+",
            "",
            str(value or "").strip().lower(),
        )

    def _safe_key(
        self,
        value: str,
        fallback: str,
    ) -> str:
        text = str(value or "").strip().lower()
        text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")
        return text or fallback


    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        requirement_text = self._get_requirement_text(request)
        context_package = request.context_package or {}
        input_payload = request.input or {}

        current_module = self._extract_current_module(context_package, input_payload)
        module_prd = self._extract_artifact(context_package, "MODULE_PRD")
        module_prd = backfill_module_prd_semantic_backbone(
            module_prd,
            current_page=current_module,
        )
        current_page_prd_requirement = self._a9_10_14_4_3_12_1_current_page_prd_requirement(
            module_prd,
            current_module,
        )
        business_flow = self._extract_artifact(context_package, "BUSINESS_FLOW")
        module_tree = self._extract_artifact(context_package, "MODULE_TREE")

        return "\n".join(
            [
                "请为当前指定模块生成企业级 PAGE_SPEC 页面规范资产。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"module_id: {request.module_id}",
                f"task_type: {request.task_type}",
                "",
                "【当前模块】",
                json.dumps(current_module, ensure_ascii=False, default=str),
                "",
                *self.asset_contract_policy.build_prompt_sections(
                    request=request,
                ),
                "",
                "【用户补充需求】",
                requirement_text or "无额外补充需求。",
                "",
                "【当前页面在 PRD 中的明确要求（最高优先级）】",
                json.dumps(current_page_prd_requirement or {}, ensure_ascii=False, default=str),
                "",
                "【当前模块 PRD】",
                json.dumps(module_prd, ensure_ascii=False, default=str),
                "",
                "【当前模块业务流程】",
                json.dumps(business_flow, ensure_ascii=False, default=str),
                "",
                "【模块树摘要】",
                json.dumps(module_tree, ensure_ascii=False, default=str),
                "",
                "【完整上下文包】",
                json.dumps(context_package, ensure_ascii=False, default=str),
                "",
                "请只输出符合 JSON Schema 的 JSON 对象。",
                "不要输出 markdown 字段。",
                "不要输出 JSON 代码块。",
            ]
        )


    # A9.10.14.4.3.12.1_TOKEN_GUARD_AND_PRD_UI_SYNC_PATCH_PAGE_SPEC_PRD_SLICE
    def _a9_10_14_4_3_12_1_current_page_prd_requirement(
        self,
        module_prd: Any,
        current_module: dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(module_prd, dict):
            return {}

        page_requirements = (
            module_prd.get("pageRequirements")
            or module_prd.get("page_requirements")
            or []
        )
        if not isinstance(page_requirements, list):
            return {}

        current_names = [
            current_module.get("pageName"),
            current_module.get("page_name"),
            current_module.get("module_name"),
            current_module.get("moduleName"),
            current_module.get("name"),
        ]
        current_keys = [
            current_module.get("pageKey"),
            current_module.get("page_key"),
            current_module.get("module_key"),
            current_module.get("moduleKey"),
            current_module.get("key"),
        ]

        def norm(value: Any) -> str:
            return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())

        name_set = {norm(item) for item in current_names if str(item or "").strip()}
        key_set = {norm(item) for item in current_keys if str(item or "").strip()}

        best: dict[str, Any] | None = None
        best_score = -1

        for raw in page_requirements:
            if not isinstance(raw, dict):
                continue
            page_name = norm(raw.get("pageName") or raw.get("page_name") or raw.get("name") or raw.get("title"))
            page_key = norm(raw.get("pageKey") or raw.get("page_key") or raw.get("key") or raw.get("pageId") or raw.get("page_id"))
            summary = norm(raw.get("summary") or raw.get("description"))

            score = 0
            if page_key and page_key in key_set:
                score += 100
            if page_name and page_name in name_set:
                score += 100
            if any(name and (name in page_name or page_name in name or name in summary) for name in name_set):
                score += 50
            if any(key and (key in page_key or page_key in key or key in summary) for key in key_set):
                score += 30

            if score > best_score:
                best = raw
                best_score = score

        return dict(best or {})

    def _a9_10_14_4_3_13_1_build_page_semantic_contract(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, Any]:
        context_package = request.context_package or {}
        input_payload = request.input or {}
        current_module = self._extract_current_module(context_package, input_payload)
        module_prd = self._extract_artifact(context_package, "MODULE_PRD")
        module_prd = backfill_module_prd_semantic_backbone(
            module_prd,
            current_page=current_module,
        )
        return build_page_semantic_contract(
            module_prd,
            current_module,
        )

    def _get_requirement_text(self, request: AgentTaskRequest) -> str:
        if request.input.get("requirement_text"):
            return str(request.input["requirement_text"])

        if request.input.get("supplement"):
            return str(request.input["supplement"])

        if request.requirement_text:
            return request.requirement_text

        return ""

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

    def _extract_artifact(
        self,
        context_package: dict[str, Any],
        artifact_type: str,
    ) -> Any:
        artifacts = context_package.get("artifacts")
        normalized_type = str(artifact_type or "").strip().upper()

        if isinstance(artifacts, dict):
            for key, value in artifacts.items():
                value_type = ""
                if isinstance(value, dict):
                    value_type = str(
                        value.get("artifact_type")
                        or value.get("artifactType")
                        or value.get("type")
                        or ""
                    ).strip().upper()
                if str(key).strip().upper() == normalized_type or value_type == normalized_type:
                    return self._unwrap_artifact_payload(value)

        if isinstance(artifacts, list):
            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                if str(artifact.get("artifact_type") or artifact.get("artifactType") or "").strip().upper() == normalized_type:
                    return self._unwrap_artifact_payload(artifact)

        return {}

    def _unwrap_artifact_payload(
        self,
        value: Any,
    ) -> Any:
        if not isinstance(value, dict):
            return value

        content = value.get("content")
        if isinstance(content, dict):
            payload = dict(content)
            markdown = (
                value.get("content_markdown")
                or value.get("contentMarkdown")
                or payload.get("content_markdown")
                or payload.get("contentMarkdown")
                or payload.get("markdown")
            )
            if markdown:
                payload.setdefault("content_markdown", markdown)
                payload.setdefault("contentMarkdown", markdown)
                payload.setdefault("markdown", markdown)
            return payload

        return value

    def _build_markdown(self, spec: PageSpecLlmOutput) -> str:
        info = spec.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块页面规范",
            "",
            "## 1. 页面基本信息",
            f"- **页面 Key**：{info.page_key}",
            f"- **页面名称**：{info.page_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **页面类型**：{info.page_type}",
            f"- **路由建议**：`{info.route_suggestion}`",
            f"- **页面目标**：{info.page_goal}",
            f"- **主要使用角色**：{info.primary_user_role}",
            "",
            "## 2. 页面布局区域",
        ]

        if spec.layout_sections:
            for section in spec.layout_sections:
                lines.extend(
                    [
                        f"- **{section.section_name}**（{section.section_key}）",
                        f"  - 位置：{section.position}",
                        f"  - 说明：{section.description}",
                        f"  - 组件：{self._join_list(section.components)}",
                    ]
                )
        else:
            lines.append("- 未识别出明确布局区域。")

        lines.extend(["", "## 3. 页面组件规范"])

        if spec.component_specs:
            for component in spec.component_specs:
                lines.extend(
                    [
                        f"- **{component.component_name}**（{component.component_key}）",
                        f"  - 类型：{component.component_type}",
                        f"  - 说明：{component.description}",
                        f"  - 数据来源：{component.data_source}",
                        f"  - 交互：{self._join_list(component.interactions)}",
                        f"  - 展示规则：{self._join_list(component.display_rules)}",
                    ]
                )
        else:
            lines.append("- 未识别出明确页面组件。")

        lines.extend(["", "## 4. 基础交互定义"])

        if spec.action_specs:
            lines.extend(
                [
                    "| 功能/操作 | 触发方式 | 交互类型 | 预期结果 |",
                    "|---|---|---|---|",
                ]
            )

            for action in spec.action_specs:
                lines.append(
                    f"| {action.action_name} | {action.trigger_position} | {action.interaction_type} | {action.expected_result} |"
                )
        else:
            lines.append("- 未识别出明确基础交互定义。")

        lines.extend(["", "## 5. 页面操作"])

        if spec.action_specs:
            for action in spec.action_specs:
                lines.extend(
                    [
                        f"- **{action.action_name}**（{action.action_key}）",
                        f"  - 触发位置：{action.trigger_position}",
                        f"  - 交互类型：{action.interaction_type}",
                        f"  - 预期结果：{action.expected_result}",
                        f"  - 执行角色：{action.actor}",
                        f"  - 说明：{action.description}",
                        f"  - 前置条件：{self._join_list(action.preconditions)}",
                        f"  - 业务规则：{self._join_list(action.business_rules)}",
                        f"  - 成功反馈：{action.success_feedback}",
                        f"  - 失败反馈：{action.failure_feedback}",
                    ]
                )
        else:
            lines.append("- 未识别出明确页面操作。")

        lines.extend(["", "## 6. 表单字段"])

        if spec.form_fields:
            for field in spec.form_fields:
                required_text = "是" if field.required else "否"
                lines.extend(
                    [
                        f"- **{field.field_name}**（{field.field_key}）",
                        f"  - 类型：{field.field_type}",
                        f"  - 必填：{required_text}",
                        f"  - 默认值：{field.default_value or '无'}",
                        f"  - 校验规则：{self._join_list(field.validation_rules)}",
                        f"  - 说明：{field.description}",
                    ]
                )
        else:
            lines.append("- 当前页面无明确表单字段。")

        lines.extend(["", "## 7. 表格列"])

        if spec.table_columns:
            for column in spec.table_columns:
                lines.extend(
                    [
                        f"- **{column.column_name}**（{column.column_key}）",
                        f"  - 数据字段：{column.data_field}",
                        f"  - 展示类型：{column.display_type}",
                        f"  - 可排序：{'是' if column.sortable else '否'}",
                        f"  - 可筛选：{'是' if column.filterable else '否'}",
                        f"  - 说明：{column.description}",
                    ]
                )
        else:
            lines.append("- 当前页面无明确表格列。")

        lines.extend(["", "## 8. API 绑定"])

        if spec.api_bindings:
            for api in spec.api_bindings:
                lines.extend(
                    [
                        f"- **{api.api_name}**（{api.api_key}）",
                        f"  - 方法：{api.method}",
                        f"  - 路径：`{api.path_suggestion}`",
                        f"  - 触发时机：{api.trigger_timing}",
                        f"  - 请求摘要：{api.request_summary}",
                        f"  - 响应用途：{api.response_usage}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确 API 绑定。")

        lines.extend(["", "## 9. 页面状态"])

        if spec.page_states:
            for state in spec.page_states:
                lines.extend(
                    [
                        f"- **{state.state_name}**（{state.state_key}）",
                        f"  - 说明：{state.description}",
                        f"  - UI 行为：{state.ui_behavior}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确页面状态。")

        lines.extend(["", "## 10. 权限控制"])

        if spec.permission_specs:
            for permission in spec.permission_specs:
                lines.extend(
                    [
                        f"- **{permission.permission_name}**（{permission.permission_key}）",
                        f"  - 角色：{self._join_list(permission.roles)}",
                        f"  - 受限元素：{self._join_list(permission.restricted_elements)}",
                        f"  - 说明：{permission.description}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确权限控制。")

        lines.extend(["", "## 11. 异常与空状态"])

        if spec.exception_specs:
            for exception in spec.exception_specs:
                lines.extend(
                    [
                        f"- **{exception.title}**（{exception.exception_key}）",
                        f"  - 场景：{exception.scenario}",
                        f"  - UI 反馈：{exception.ui_feedback}",
                        f"  - 恢复动作：{exception.recovery_action}",
                    ]
                )
        else:
            lines.append("- 当前页面未识别出明确异常状态。")

        lines.extend(["", "## 12. 验收标准"])

        if spec.acceptance_criteria:
            for criterion in spec.acceptance_criteria:
                lines.extend(
                    [
                        f"- **{criterion.criterion_key}**：{criterion.description}",
                        f"  - 验证方式：{criterion.verification_method}",
                    ]
                )
        else:
            lines.append("- 未生成页面验收标准。")

        hints = spec.downstream_hints

        lines.extend(
            [
                "",
                "## 13. 下游工程资产生成提示",
                "### 13.1 数据模型提示",
                self._bullet_list(hints.data_model_hints),
                "",
                "### 13.2 接口契约提示",
                self._bullet_list(hints.api_contract_hints),
                "",
                "### 13.3 测试规格提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 13.4 前端组件提示",
                self._bullet_list(hints.frontend_component_hints),
                "",
                "### 13.5 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "## 14. 开发注意事项",
                self._bullet_list(spec.development_notes),
                "",
                "## 15. 测试注意事项",
                self._bullet_list(spec.testing_notes),
                "",
                "## 16. 分析摘要",
                spec.analysis_summary,
            ]
        )

        return "\n".join(lines)

    def _join_list(self, values: list[str]) -> str:
        if not values:
            return "无"

        return "；".join(values)

    def _bullet_list(self, values: list[str]) -> str:
        if not values:
            return "- 无"

        return "\n".join([f"- {value}" for value in values])

    def _load_prompt(self, filename: str) -> str:
        prompt_path = (
            Path(__file__).resolve().parents[2]
            / "prompts"
            / "requirement"
            / filename
        )

        return prompt_path.read_text(encoding="utf-8")

# A9.10.14.4.3.15.3.2_PAGE_SPEC_UNIVERSAL_SEMANTIC_GUARD
# Universal PAGE_SPEC semantic guard.
# Purpose: when MODULE_PRD.page_requirements already provides a page contract,
# PAGE_SPEC must preserve the page type, fields, columns, actions, states, and
# navigation boundary before UI_CONTRACT generation.  LLM remains available only
# when the semantic contract is missing or too weak.
_A9_10_14_4_3_15_3_2_MARK = "A9.10.14.4.3.15.3.2_PAGE_SPEC_UNIVERSAL_SEMANTIC_GUARD"

try:
    _A9_10_14_4_3_15_3_2_ORIGINAL_NO_LLM_PAGE_SPEC = PageSpecAgent._a9_10_14_4_3_13_2_1_build_no_llm_page_spec
except Exception:  # pragma: no cover
    _A9_10_14_4_3_15_3_2_ORIGINAL_NO_LLM_PAGE_SPEC = None


def _a91532_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a91532_norm(value: Any) -> str:
    return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value or "").strip().lower())


def _a91532_safe_key(value: Any, fallback: str) -> str:
    text = str(value or "").strip().lower()
    text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or fallback


def _a91532_as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _a91532_label(item: Any, fallback: str = "") -> str:
    if isinstance(item, dict):
        return _a91532_text(
            item.get("label"),
            item.get("fieldName"), item.get("field_name"),
            item.get("columnName"), item.get("column_name"),
            item.get("actionName"), item.get("action_name"),
            item.get("name"), item.get("title"),
            item.get("fieldKey"), item.get("field_key"),
            item.get("columnKey"), item.get("column_key"),
            item.get("actionKey"), item.get("action_key"),
            fallback,
        )
    return _a91532_text(item, fallback)


def _a91532_key(item: Any, label: str, fallback: str) -> str:
    if isinstance(item, dict):
        return _a91532_safe_key(
            _a91532_text(
                item.get("fieldKey"), item.get("field_key"),
                item.get("columnKey"), item.get("column_key"),
                item.get("actionKey"), item.get("action_key"),
                item.get("key"), item.get("id"),
                label,
            ),
            fallback,
        )
    return _a91532_safe_key(label, fallback)


def _a91532_field_type(label: str, item: Any = None) -> str:
    raw = ""
    if isinstance(item, dict):
        raw = _a91532_text(item.get("type"), item.get("fieldType"), item.get("field_type"), item.get("displayType"), item.get("display_type"))
    raw = raw.upper()
    if raw:
        if raw in {"TEXT", "STRING"}:
            return "INPUT"
        return raw
    if re.search(r"状态|类型|分类|供应商|仓库|客户|角色|部门|国家|地区", label):
        return "SELECT"
    if re.search(r"日期|日$", label):
        return "DATE"
    if re.search(r"时间|时刻", label):
        return "DATETIME"
    if re.search(r"数量|金额|价格|库存|比例|重量|体积|分数|评分|天数|次数", label):
        return "NUMBER"
    if re.search(r"备注|说明|描述|意见|原因|内容|地址", label):
        return "TEXTAREA"
    return "INPUT"


def _a91532_column_type(label: str, item: Any = None) -> str:
    raw = ""
    if isinstance(item, dict):
        raw = _a91532_text(item.get("type"), item.get("displayType"), item.get("display_type"), item.get("columnType"), item.get("column_type"))
    raw = raw.upper()
    if raw:
        if raw in {"INPUT", "TEXTAREA", "SELECT"}:
            return "TEXT"
        return raw
    if "状态" in label:
        return "TAG"
    if re.search(r"数量|金额|价格|库存|比例|重量|体积|分数|评分", label):
        return "NUMBER"
    if "日期" in label:
        return "DATE"
    if "时间" in label:
        return "DATETIME"
    return "TEXT"


def _a91532_action_kind(label: str, item: Any = None) -> str:
    raw = ""
    if isinstance(item, dict):
        raw = _a91532_text(item.get("kind"), item.get("type"), item.get("actionType"), item.get("action_type"))
    raw = raw.upper()
    if raw:
        return raw
    if re.search(r"查询|搜索|筛选|检索", label): return "QUERY"
    if re.search(r"重置|清空", label): return "RESET"
    if re.search(r"新增|新建|创建|添加|发起|录入", label): return "CREATE"
    if re.search(r"保存|提交|确定|发布|确认", label): return "SUBMIT"
    if re.search(r"取消|返回|关闭", label): return "CANCEL"
    if re.search(r"删除|移除|作废", label): return "DELETE"
    if re.search(r"编辑|修改|维护|调整", label): return "EDIT"
    if re.search(r"审核|审批|通过|驳回|退回|复核", label): return "APPROVAL"
    if re.search(r"查看|详情|预览", label): return "VIEW"
    if re.search(r"导出|下载", label): return "EXPORT"
    if re.search(r"刷新|同步", label): return "REFRESH"
    return "CUSTOM"


def _a91532_interaction_type(kind: str, label: str, page_type: str) -> str:
    kind = str(kind or "").upper()
    if kind in {"QUERY", "RESET", "REFRESH"}:
        return "QUERY_REFRESH"
    if kind in {"CREATE", "EDIT", "VIEW"} and page_type == "LIST":
        return "SUCCESS_NAVIGATION" if kind in {"VIEW", "EDIT", "CREATE"} else "OPEN_MODAL"
    if kind in {"SUBMIT", "APPROVAL", "CREATE", "EDIT"}:
        return "FORM_SUBMIT"
    if kind in {"DELETE"}:
        return "STATE_CHANGE"
    if re.search(r"跳转|进入|详情|查看", label):
        return "SUCCESS_NAVIGATION"
    return "SUCCESS_FEEDBACK"


def _a91532_norm_page_type(contract: dict[str, Any], page_name: str, page_key: str) -> str:
    raw = _a91532_text(contract.get("pageType"), contract.get("page_type"), contract.get("layoutType"), contract.get("layout_type"))
    text = _a91532_norm(" ".join([raw, page_name, page_key, _a91532_text(contract.get("description"), contract.get("summary"))]))
    if re.search(r"审核|审批|复核|approval|audit|review", text): return "APPROVAL"
    if re.search(r"列表|查询|搜索|检索|管理|list|search|query|table", text): return "LIST"
    if re.search(r"创建|新建|新增|录入|编辑|修改|表单|create|add|edit|form|entry", text): return "FORM"
    if re.search(r"详情|查看|profile|detail|view", text): return "DETAIL"
    if re.search(r"看板|工作台|仪表盘|dashboard|workbench|overview", text): return "DASHBOARD"
    if re.search(r"配置|设置|setting|settings|config", text): return "CONFIG"
    if re.search(r"报表|分析|统计|report|analytics|analysis", text): return "REPORT"
    if re.search(r"门户|首页|频道|落地页|portal|home|landing", text): return "PORTAL"
    if re.search(r"向导|分步|wizard|step", text): return "WIZARD"
    if contract.get("tableColumns") or contract.get("filterFields"):
        return "LIST"
    if contract.get("formFields"):
        return "FORM"
    return "OTHER"


def _a91532_schema_page_type(page_type: str) -> str:
    page_type = str(page_type or "").upper()
    if page_type in {"LIST", "SEARCH"}: return "LIST"
    if page_type in {"FORM", "CREATE", "EDIT", "APPROVAL"}: return "FORM"
    if page_type in {"DETAIL", "PROFILE"}: return "DETAIL"
    if page_type in {"DASHBOARD", "REPORT"}: return "DASHBOARD"
    if page_type in {"CONFIG", "SETTING", "SETTINGS"}: return "SETTING"
    if page_type == "WIZARD": return "WIZARD"
    return "OTHER"


def _a91532_field_specs(items: list[Any], fallback_labels: list[str] | None = None) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, item in enumerate([*items, *(fallback_labels or [])]):
        label = _a91532_label(item, f"字段{idx + 1}")
        label = re.sub(r"\s+", "", label).strip("，,。；;:：、")
        if not label:
            continue
        key = _a91532_key(item, label, f"field_{idx + 1}")
        if key in seen:
            continue
        seen.add(key)
        required = False
        if isinstance(item, dict):
            required = bool(item.get("required") or item.get("isRequired") or item.get("mandatory"))
        result.append({
            "field_key": key,
            "field_name": label,
            "field_type": _a91532_field_type(label, item),
            "required": required,
            "default_value": None,
            "validation_rules": ["按业务规则完成合法性校验。"] if required else [],
            "description": f"来自 PRD 页面契约的业务字段：{label}。",
        })
    return result


def _a91532_table_column_specs(items: list[Any], filter_fields: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
    filter_keys = {str(x.get("field_key") or "") for x in (filter_fields or []) if isinstance(x, dict)}
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, item in enumerate(items):
        label = _a91532_label(item, f"列{idx + 1}")
        label = re.sub(r"\s+", "", label).strip("，,。；;:：、")
        if not label:
            continue
        key = _a91532_key(item, label, f"column_{idx + 1}")
        if key in seen:
            continue
        seen.add(key)
        result.append({
            "column_key": key,
            "column_name": label,
            "data_field": key,
            "display_type": _a91532_column_type(label, item),
            "sortable": bool(re.search(r"时间|日期|数量|金额|价格|分数|评分", label)),
            "filterable": key in filter_keys,
            "description": f"来自 PRD 页面契约的列表字段：{label}。",
        })
    return result


def _a91532_action_specs(items: list[Any], page_type: str, *, scope: str = "页面操作") -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, item in enumerate(items):
        label = _a91532_label(item, f"操作{idx + 1}")
        label = label.strip("，,。；;:：、")
        if not label:
            continue
        key = _a91532_key(item, label, f"action_{idx + 1}")
        if key in seen:
            continue
        seen.add(key)
        kind = _a91532_action_kind(label, item)
        interaction = _a91532_interaction_type(kind, label, page_type)
        result.append({
            "action_key": key,
            "action_name": label,
            "trigger_position": scope,
            "actor": "当前页面用户",
            "description": f"PRD 明确要求的“{label}”操作。",
            "interaction_type": interaction,
            "expected_result": f"用户执行“{label}”后，页面应展示结果反馈、状态变化或进入目标页面。",
            "preconditions": [],
            "business_rules": [],
            "success_feedback": f"{label}成功。",
            "failure_feedback": f"{label}失败时展示原因，并允许用户修正后重试。",
        })
    return result


def _a91532_default_actions(page_type: str, existing: list[Any]) -> list[str]:
    labels = [_a91532_label(x) for x in existing]
    compact = _a91532_norm(" ".join(labels))
    defaults: list[str] = []
    if page_type == "LIST":
        defaults = ["查询", "重置"]
    elif page_type == "FORM":
        defaults = ["保存", "取消"]
    elif page_type == "APPROVAL":
        defaults = ["通过", "驳回", "取消"]
    elif page_type == "DETAIL":
        defaults = ["返回"]
    elif page_type == "CONFIG":
        defaults = ["保存配置", "重置"]
    elif page_type in {"DASHBOARD", "REPORT"}:
        defaults = ["刷新", "导出"]
    elif page_type == "WIZARD":
        defaults = ["上一步", "下一步", "完成"]
    for item in defaults:
        if _a91532_norm(item) not in compact:
            labels.append(item)
    return labels


def _a91532_has_contract(contract: Any) -> bool:
    if not isinstance(contract, dict):
        return False
    return bool(
        contract.get("pageType") or contract.get("page_type") or
        contract.get("formFields") or contract.get("form_fields") or
        contract.get("filterFields") or contract.get("filter_fields") or
        contract.get("tableColumns") or contract.get("table_columns") or
        contract.get("primaryActions") or contract.get("primary_actions") or
        contract.get("rowActions") or contract.get("row_actions") or
        contract.get("navigationTargets") or contract.get("navigation_targets")
    )


def _a91532_build_universal_page_spec(self: PageSpecAgent, request: AgentTaskRequest, contract: dict[str, Any]) -> PageSpecArtifact:
    context_package = request.context_package or {}
    input_payload = request.input or {}
    current_module = self._extract_current_module(context_package, input_payload)

    page_name = _a91532_text(
        current_module.get("module_name"), current_module.get("moduleName"),
        current_module.get("pageName"), current_module.get("name"),
        contract.get("pageName"), contract.get("page_name"), "当前页面",
    )
    page_key = _a91532_safe_key(_a91532_text(
        current_module.get("module_key"), current_module.get("moduleKey"),
        current_module.get("pageKey"), current_module.get("key"),
        contract.get("pageKey"), contract.get("page_key"), page_name,
    ), "page")
    route = _a91532_text(
        current_module.get("routePath"), current_module.get("route_path"), current_module.get("route"),
        contract.get("routePath"), contract.get("route_path"), contract.get("route"),
        f"/{page_key.replace('_', '-')}",
    )
    universal_page_type = _a91532_norm_page_type(contract, page_name, page_key)
    schema_page_type = _a91532_schema_page_type(universal_page_type)

    filter_items = _a91532_as_list(contract.get("filterFields") or contract.get("filter_fields"))
    table_items = _a91532_as_list(contract.get("tableColumns") or contract.get("table_columns") or contract.get("columns"))
    form_items = _a91532_as_list(contract.get("formFields") or contract.get("form_fields"))

    filter_specs = _a91532_field_specs(filter_items)
    form_source = form_items or ([] if universal_page_type == "LIST" else table_items or filter_items)
    form_specs = _a91532_field_specs(form_source)
    table_specs = _a91532_table_column_specs(table_items or ([] if universal_page_type != "LIST" else form_items or filter_items), filter_specs)

    raw_primary = _a91532_as_list(contract.get("primaryActions") or contract.get("primary_actions") or contract.get("actions"))
    raw_row = _a91532_as_list(contract.get("rowActions") or contract.get("row_actions"))
    primary_labels = _a91532_default_actions(universal_page_type, raw_primary)
    action_specs = _a91532_action_specs(primary_labels, universal_page_type, scope="页面主操作区")
    action_specs.extend(_a91532_action_specs(raw_row, universal_page_type, scope="表格行操作区"))

    layout_sections: list[dict[str, Any]] = []
    component_specs: list[dict[str, Any]] = []

    def add_section(key: str, name: str, position: str, description: str, components: list[str]) -> None:
        layout_sections.append({
            "section_key": key,
            "section_name": name,
            "position": position,
            "description": description,
            "components": components,
        })

    def add_component(key: str, name: str, typ: str, description: str, interactions: list[str] | None = None) -> None:
        component_specs.append({
            "component_key": key,
            "component_name": name,
            "component_type": typ,
            "description": description,
            "data_source": "MODULE_PRD.page_requirements 页面契约",
            "interactions": interactions or [],
            "display_rules": ["必须覆盖 PRD 页面契约，不得删除或改名业务字段、按钮、列和跳转边界。"],
        })

    add_section(f"{page_key}_header", "页面标题与上下文", "页面顶部", f"展示{page_name}标题、说明、状态或关键上下文。", ["页面标题区"])
    add_component(f"{page_key}_header_component", "页面标题区", "PAGE_HEADER", f"说明页面目标：{_a91532_text(contract.get('description'), page_name)}")

    if universal_page_type == "LIST":
        add_section(f"{page_key}_filter", "查询筛选区", "页面上方", "承载筛选条件、查询与重置操作。", ["查询筛选表单"])
        add_section(f"{page_key}_result", "结果列表区", "页面主体", "展示业务数据、分页、空状态和行级操作。", ["主要操作栏", "数据表格", "空状态"])
        add_component(f"{page_key}_filter_form", "查询筛选表单", "FORM", "按 PRD filter_fields 展示筛选字段。", ["查询", "重置"])
        add_component(f"{page_key}_data_table", "数据表格", "TABLE", "按 PRD table_columns 展示列表列，按 row_actions 展示行操作。", [_a91532_label(x) for x in raw_row])
    elif universal_page_type in {"FORM", "APPROVAL"}:
        add_section(f"{page_key}_form", "业务表单区", "页面主体", "承载业务录入、编辑、提交或审核字段。", ["业务表单", "规则提示", "底部操作栏"])
        add_component(f"{page_key}_main_form", "业务表单", "FORM", "按 PRD form_fields 展示表单字段。", [x["action_name"] for x in action_specs])
        if universal_page_type == "APPROVAL":
            if not any(_a91532_norm(x.get("field_name")) == _a91532_norm("审核意见") for x in form_specs):
                form_specs.append({
                    "field_key": "approval_opinion",
                    "field_name": "审核意见",
                    "field_type": "TEXTAREA",
                    "required": True,
                    "default_value": None,
                    "validation_rules": ["驳回或退回时必须填写审核意见。"],
                    "description": "审核人员填写通过、驳回或退回原因。",
                })
            add_section(f"{page_key}_audit_trace", "审核记录区", "页面下方", "展示历史审核节点、处理人、处理时间和意见。", ["审核记录"])
            add_component(f"{page_key}_approval_record", "审核记录", "TABLE", "展示审核流转记录。", [])
    elif universal_page_type == "DETAIL":
        detail_items = form_specs or _a91532_field_specs(table_items or filter_items)
        form_specs = detail_items
        add_section(f"{page_key}_summary", "核心摘要区", "页面上方", "展示业务对象关键状态、编号和摘要。", ["摘要卡片"])
        add_section(f"{page_key}_detail", "详情信息区", "页面主体", "按 PRD 字段分组展示详情信息。", ["详情字段组"])
        add_component(f"{page_key}_detail_fields", "详情字段组", "DETAIL", "按 PRD 字段只读展示业务详情。", [x["action_name"] for x in action_specs])
    elif universal_page_type in {"DASHBOARD", "REPORT"}:
        add_section(f"{page_key}_metrics", "指标概览区", "页面上方", "展示关键指标、趋势和异常提醒。", ["指标卡片", "趋势图"])
        add_section(f"{page_key}_analysis", "分析明细区", "页面主体", "展示报表明细、图表和数据解释。", ["图表区", "明细表格"])
        add_component(f"{page_key}_metric_cards", "指标卡片", "CARD", "展示核心业务指标。", [x["action_name"] for x in action_specs])
    elif universal_page_type == "CONFIG":
        add_section(f"{page_key}_settings", "配置项区域", "页面主体", "展示配置项、作用范围、启停状态和保存重置操作。", ["配置表单"])
        add_component(f"{page_key}_settings_form", "配置表单", "FORM", "按 PRD 字段展示配置项。", [x["action_name"] for x in action_specs])
    elif universal_page_type == "WIZARD":
        add_section(f"{page_key}_steps", "分步办理区", "页面主体", "展示步骤进度、当前步骤表单和上一步/下一步/完成操作。", ["步骤条", "步骤表单"])
        add_component(f"{page_key}_wizard_steps", "步骤办理", "WIZARD", "按 PRD 页面契约组织分步业务操作。", [x["action_name"] for x in action_specs])
    else:
        add_section(f"{page_key}_content", "主要内容区", "页面主体", "展示当前页面主要内容、操作入口和状态反馈。", ["内容区", "操作区"])
        add_component(f"{page_key}_content_component", "内容区", "SECTION", "按 PRD 页面契约展示业务内容。", [x["action_name"] for x in action_specs])

    page_states = [
        {"state_key": "DEFAULT", "state_name": "默认态", "description": "展示页面主要内容和操作入口。", "ui_behavior": "按 PRD 页面契约展示字段、按钮、列表、详情或配置项。"},
        {"state_key": "LOADING", "state_name": "加载中", "description": "页面数据加载或操作提交中。", "ui_behavior": "展示加载状态并避免重复提交。"},
        {"state_key": "EMPTY", "state_name": "空状态", "description": "无可展示数据或无匹配结果。", "ui_behavior": "展示空状态文案和可恢复操作。"},
        {"state_key": "SUCCESS", "state_name": "成功态", "description": "关键操作完成。", "ui_behavior": "展示成功反馈、刷新数据或进入下一步。"},
        {"state_key": "ERROR", "state_name": "错误态", "description": "校验、保存、查询或审核失败。", "ui_behavior": "展示错误原因并保留用户输入。"},
    ]
    if universal_page_type == "APPROVAL":
        page_states.insert(1, {"state_key": "PENDING_APPROVAL", "state_name": "待审核", "description": "当前业务对象等待审核处理。", "ui_behavior": "展示审核对象、审核意见和通过/驳回操作。"})

    markdown = _a91532_build_page_spec_markdown(page_name, page_key, route, universal_page_type, form_specs, filter_specs, table_specs, action_specs)

    return PageSpecArtifact.model_validate({
        "basic_info": {
            "page_key": page_key,
            "page_name": page_name,
            "related_module_key": page_key,
            "related_module_name": _a91532_text(current_module.get("parent_module_name"), current_module.get("modulePath"), page_name),
            "page_type": schema_page_type,
            "route_suggestion": route,
            "page_goal": _a91532_text(contract.get("description"), contract.get("summary"), f"承载{page_name}业务场景。"),
            "primary_user_role": "当前页面用户",
        },
        "layout_sections": layout_sections,
        "component_specs": component_specs,
        "action_specs": action_specs,
        "form_fields": form_specs,
        "table_columns": table_specs,
        "api_bindings": [],
        "page_semantic_contract": {
            **dict(contract),
            "universalPageType": universal_page_type,
            "schemaPageType": schema_page_type,
            "guardMarker": _A9_10_14_4_3_15_3_2_MARK,
        },
        "page_states": page_states,
        "permission_specs": [],
        "exception_specs": [
            {"exception_key": "validation_error", "title": "字段校验失败", "scenario": "用户提交缺少必填项或格式不合法的数据。", "ui_feedback": "在对应字段附近展示错误提示。", "recovery_action": "用户修正后重新提交。"},
            {"exception_key": "operation_failed", "title": "操作失败", "scenario": "保存、查询、审核或跳转失败。", "ui_feedback": "展示失败原因和重试入口。", "recovery_action": "保留当前上下文并允许重试。"},
        ],
        "acceptance_criteria": [
            {"criterion_key": "prd_page_contract_coverage", "description": "PAGE_SPEC 必须覆盖 MODULE_PRD.page_requirements 中的页面类型、字段、按钮、列、状态和跳转边界。", "verification_method": "PAGE_SPEC Semantic Guard / UI_CONTRACT Semantic Guard"},
        ],
        "downstream_hints": {
            "data_model_hints": ["从 page_semantic_contract 的字段、列和业务对象推导数据模型候选。"],
            "api_contract_hints": ["从 action_specs 推导查询、保存、审核、删除、导出等接口候选。"],
            "test_spec_hints": ["测试必须验证页面类型、字段、按钮、列、行操作和异常状态不丢失。"],
            "frontend_component_hints": ["UI_CONTRACT 生成时必须保持 PAGE_SPEC.page_semantic_contract 的语义边界。"],
            "dev_handoff_hints": ["Development Agent 只能使用确认后的 PAGE_SPEC / UI_CONTRACT baseline。"],
        },
        "development_notes": [f"{_A9_10_14_4_3_15_3_2_MARK}: PAGE_SPEC 由 PRD 页面契约通用守卫生成。"],
        "testing_notes": ["批量测试阶段统一验证所有页面类型的 PAGE_SPEC 与 UI_CONTRACT 是否严格覆盖 PRD 页面契约。"],
        "analysis_summary": f"已按通用页面类型 {universal_page_type} 从 MODULE_PRD.page_requirements 生成 PAGE_SPEC 语义基线。",
        "markdown": markdown,
    })


def _a91532_build_page_spec_markdown(
    page_name: str,
    page_key: str,
    route: str,
    page_type: str,
    form_specs: list[dict[str, Any]],
    filter_specs: list[dict[str, Any]],
    table_specs: list[dict[str, Any]],
    action_specs: list[dict[str, Any]],
) -> str:
    def names(items: list[dict[str, Any]], key: str) -> str:
        values = [str(x.get(key) or "").strip() for x in items if isinstance(x, dict) and str(x.get(key) or "").strip()]
        return "、".join(values) if values else "无"
    return "\n".join([
        f"# {page_name} 页面规范",
        "",
        "## 1. 页面基本信息",
        f"- 页面 Key：`{page_key}`",
        f"- 页面类型：{page_type}",
        f"- 路由建议：`{route}`",
        "",
        "## 2. PRD 页面契约覆盖",
        f"- 表单/详情字段：{names(form_specs, 'field_name')}",
        f"- 筛选字段：{names(filter_specs, 'field_name')}",
        f"- 表格列：{names(table_specs, 'column_name')}",
        f"- 页面操作：{names(action_specs, 'action_name')}",
        "",
        "## 3. 生成约束",
        "- 后续 UI_CONTRACT 允许优化视觉布局，但不得删除或改名 PRD 明确字段、按钮、列和跳转边界。",
        "- 页面类型必须保持与 PRD 契约一致。",
    ])


def _a91532_no_llm_page_spec_guard(self: PageSpecAgent, request: AgentTaskRequest) -> PageSpecArtifact | None:
    contract: dict[str, Any] = {}
    try:
        value = self._a9_10_14_4_3_13_1_build_page_semantic_contract(request)
        if isinstance(value, dict):
            contract = value
    except Exception as exc:
        print(f"[{_A9_10_14_4_3_15_3_2_MARK}] failed to read page semantic contract: {type(exc).__name__}: {exc}", flush=True)

    if _a91532_has_contract(contract):
        try:
            artifact = _a91532_build_universal_page_spec(self, request, contract)
            print(f"[{_A9_10_14_4_3_15_3_2_MARK}] PageSpecAgent returned PRD-locked universal PAGE_SPEC", flush=True)
            return artifact
        except Exception as exc:
            print(f"[{_A9_10_14_4_3_15_3_2_MARK}] universal PAGE_SPEC guard failed; falling back to previous path: {type(exc).__name__}: {exc}", flush=True)

    if callable(_A9_10_14_4_3_15_3_2_ORIGINAL_NO_LLM_PAGE_SPEC):
        return _A9_10_14_4_3_15_3_2_ORIGINAL_NO_LLM_PAGE_SPEC(self, request)
    return None


PageSpecAgent._a9_10_14_4_3_13_2_1_build_no_llm_page_spec = _a91532_no_llm_page_spec_guard

