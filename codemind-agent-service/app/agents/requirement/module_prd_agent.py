
from __future__ import annotations
import re

import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.module_prd import (
    MODULE_PRD_LLM_JSON_SCHEMA,
    ModulePrdArtifact,
    ModulePrdLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.graphs.requirement.context.requirement_asset_contract_policy import (
    RequirementAssetContractPolicy,
)



# A9.10.14.4.3.15.2.4.3_MODULE_PRD_GLOBAL_MARK_HOTFIX
# Some older A9.10.14.4.3.14.3 helper code references MARK at module scope.
# Keep this compatibility constant so all f-string log/note usages resolve.
MARK = 'A9.10.14.4.3.14.3'

class ModulePrdAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()
        self.asset_contract_policy = RequirementAssetContractPolicy()

    async def run(self, request: AgentTaskRequest) -> ModulePrdArtifact:
        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("module_prd.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="module_prd_output",
            output_json_schema=MODULE_PRD_LLM_JSON_SCHEMA,
        )

        parsed = ModulePrdLlmOutput.model_validate_json(llm_result.text)
        markdown = self._build_markdown(parsed)
        semantic_projection = self._build_semantic_projection(parsed, markdown, request)

        return ModulePrdArtifact(
            # A9.10.14.4.2_PRD_SEMANTIC_BACKBONE
            prd_contract_version=semantic_projection.get("prd_contract_version", "2.0"),
            public_document=semantic_projection.get("public_document", {}),
            structured_requirements=semantic_projection.get("structured_requirements", []),
            navigation_requirements=semantic_projection.get("navigation_requirements", []),
            traceability_map=semantic_projection.get("traceability_map", {}),
            semantic_sync_policy=semantic_projection.get("semantic_sync_policy", {}),
            source_outline=semantic_projection.get("source_outline", {}),
            module_basic_info=parsed.module_basic_info,
            business_background=parsed.business_background,
            user_roles=parsed.user_roles,
            functional_requirements=parsed.functional_requirements,
            business_rules=parsed.business_rules,
            state_rules=parsed.state_rules,
            page_requirements=parsed.page_requirements,
            data_requirements=parsed.data_requirements,
            api_requirements=parsed.api_requirements,
            permission_requirements=parsed.permission_requirements,
            exception_scenarios=parsed.exception_scenarios,
            acceptance_criteria=parsed.acceptance_criteria,
            non_functional_requirements=parsed.non_functional_requirements,
            custom_sections=getattr(parsed, "custom_sections", []),
            downstream_artifact_hints=parsed.downstream_artifact_hints,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            markdown=markdown,
            analysis_summary=parsed.analysis_summary,
        )


    # A9.10.14.4.2_PRD_SEMANTIC_BACKBONE
    def _build_semantic_projection(
        self,
        prd: ModulePrdLlmOutput,
        markdown: str,
        request: AgentTaskRequest,
    ) -> dict[str, Any]:
        """Build the hidden/system-facing PRD semantic backbone.

        This deliberately keeps the visible PRD as readable markdown while adding
        machine-readable indexes for PRD <-> PAGE_SPEC <-> UI_CONTRACT sync,
        navigation authority, impact analysis, and Development Agent safety.
        """
        module_info = prd.module_basic_info
        module_key = self._safe_semantic_key(
            getattr(module_info, "module_key", "") or getattr(module_info, "module_name", ""),
            "module",
        )
        module_name = str(getattr(module_info, "module_name", "") or module_key)

        pages = list(prd.page_requirements or [])
        page_index = [
            {
                "pageKey": self._safe_semantic_key(getattr(page, "page_key", ""), f"page_{index + 1}"),
                "pageName": str(getattr(page, "page_name", "") or getattr(page, "page_key", "") or f"页面 {index + 1}"),
                "description": str(getattr(page, "description", "") or ""),
                "mainComponents": self._list_values(getattr(page, "main_components", [])),
                "userActions": self._list_values(getattr(page, "user_actions", [])),
            }
            for index, page in enumerate(pages)
        ]

        structured_requirements: list[dict[str, Any]] = []

        def related_pages_for_text(text: str) -> list[str]:
            normalized = text.lower()
            result: list[str] = []
            for page in page_index:
                key = str(page.get("pageKey") or "")
                name = str(page.get("pageName") or "")
                if key and key.lower() in normalized:
                    result.append(key)
                elif name and name.lower() in normalized:
                    result.append(key)
                elif name and name in text:
                    result.append(key)
            return list(dict.fromkeys(result))

        def add_requirement(
            *,
            raw_key: str,
            title: str,
            requirement_type: str,
            description: str,
            priority: str = "MEDIUM",
            source_section: str,
            related_page_keys: list[str] | None = None,
            extra: dict[str, Any] | None = None,
        ) -> None:
            base_key = self._safe_semantic_key(raw_key or title, f"{requirement_type.lower()}_{len(structured_requirements) + 1}")
            requirement_id = f"req_{module_key}_{base_key}"
            text = " ".join([title, description, str(extra or {})])
            pages_for_item = related_page_keys if related_page_keys is not None else related_pages_for_text(text)
            structured_requirements.append({
                "requirementId": requirement_id,
                "requirementKey": base_key,
                "title": title or base_key,
                "type": requirement_type,
                "scope": "MODULE",
                "description": description or title or base_key,
                "priority": priority or "MEDIUM",
                "sourceSection": source_section,
                "relatedPageKeys": list(dict.fromkeys(pages_for_item or [])),
                "relatedFeatureKeys": [],
                "relatedFieldKeys": [],
                "relatedActionKeys": [],
                "relatedRuleKeys": [],
                "acceptanceCriteriaKeys": [],
                "status": "ACTIVE",
                "syncRole": "AUTHORITATIVE_REQUIREMENT_SOURCE",
                "extra": extra or {},
            })

        for item in prd.functional_requirements or []:
            add_requirement(
                raw_key=str(getattr(item, "requirement_key", "") or ""),
                title=str(getattr(item, "title", "") or ""),
                requirement_type="FUNCTIONAL_REQUIREMENT",
                description=str(getattr(item, "description", "") or ""),
                priority=str(getattr(item, "priority", "MEDIUM") or "MEDIUM"),
                source_section="functional_requirements",
                extra={
                    "inputDescription": str(getattr(item, "input_description", "") or ""),
                    "outputDescription": str(getattr(item, "output_description", "") or ""),
                    "preconditions": self._list_values(getattr(item, "preconditions", [])),
                    "postconditions": self._list_values(getattr(item, "postconditions", [])),
                },
            )

        for item in prd.business_rules or []:
            add_requirement(
                raw_key=str(getattr(item, "rule_key", "") or ""),
                title=str(getattr(item, "title", "") or ""),
                requirement_type="BUSINESS_RULE",
                description=str(getattr(item, "description", "") or ""),
                priority="HIGH",
                source_section="business_rules",
                extra={
                    "triggerCondition": str(getattr(item, "trigger_condition", "") or ""),
                    "expectedResult": str(getattr(item, "expected_result", "") or ""),
                },
            )

        for item in prd.state_rules or []:
            state_key = str(getattr(item, "state_key", "") or "")
            add_requirement(
                raw_key=state_key,
                title=str(getattr(item, "state_name", "") or state_key),
                requirement_type="STATE_RULE",
                description=str(getattr(item, "description", "") or ""),
                priority="MEDIUM",
                source_section="state_rules",
                extra={
                    "allowedTransitions": self._list_values(getattr(item, "allowed_transitions", [])),
                },
            )

        for page in page_index:
            add_requirement(
                raw_key=str(page.get("pageKey") or ""),
                title=str(page.get("pageName") or page.get("pageKey") or ""),
                requirement_type="PAGE_REQUIREMENT",
                description=str(page.get("description") or ""),
                priority="HIGH",
                source_section="page_requirements",
                related_page_keys=[str(page.get("pageKey") or "")],
                extra={
                    "mainComponents": page.get("mainComponents") or [],
                    "userActions": page.get("userActions") or [],
                },
            )

        for item in prd.data_requirements or []:
            add_requirement(
                raw_key=str(getattr(item, "entity_key", "") or ""),
                title=str(getattr(item, "entity_name", "") or ""),
                requirement_type="DATA_REQUIREMENT",
                description=str(getattr(item, "description", "") or ""),
                priority="HIGH",
                source_section="data_requirements",
                extra={
                    "importantFields": self._list_values(getattr(item, "important_fields", [])),
                    "constraints": self._list_values(getattr(item, "constraints", [])),
                },
            )

        for item in prd.api_requirements or []:
            add_requirement(
                raw_key=str(getattr(item, "api_key", "") or ""),
                title=str(getattr(item, "api_name", "") or ""),
                requirement_type="API_REQUIREMENT",
                description=str(getattr(item, "description", "") or ""),
                priority="MEDIUM",
                source_section="api_requirements",
                extra={
                    "method": str(getattr(item, "method", "") or ""),
                    "pathSuggestion": str(getattr(item, "path_suggestion", "") or ""),
                    "requestSummary": str(getattr(item, "request_summary", "") or ""),
                    "responseSummary": str(getattr(item, "response_summary", "") or ""),
                },
            )

        for item in prd.acceptance_criteria or []:
            add_requirement(
                raw_key=str(getattr(item, "criterion_key", "") or ""),
                title=str(getattr(item, "criterion_key", "") or "验收标准"),
                requirement_type="ACCEPTANCE_CRITERION",
                description=str(getattr(item, "description", "") or ""),
                priority="HIGH",
                source_section="acceptance_criteria",
                extra={
                    "verificationMethod": str(getattr(item, "verification_method", "") or ""),
                },
            )

        navigation_requirements = self._derive_navigation_requirements(
            module_key=module_key,
            pages=page_index,
            structured_requirements=structured_requirements,
        )

        traceability = {
            "requirementToPages": [
                {
                    "requirementId": item["requirementId"],
                    "pageKeys": item.get("relatedPageKeys") or [],
                    "sourceSection": item.get("sourceSection"),
                }
                for item in structured_requirements
                if item.get("relatedPageKeys")
            ],
            "requirementToActions": [],
            "requirementToFields": [],
            "requirementToRules": [
                {
                    "requirementId": item["requirementId"],
                    "requirementKey": item.get("requirementKey"),
                    "sourceSection": item.get("sourceSection"),
                }
                for item in structured_requirements
                if item.get("type") in {"BUSINESS_RULE", "STATE_RULE", "ACCEPTANCE_CRITERION"}
            ],
            "navigationToPages": [
                {
                    "navigationId": item.get("navigationId"),
                    "sourcePageKey": item.get("sourcePageKey"),
                    "targetPageKey": item.get("targetPageKey"),
                    "status": item.get("status"),
                }
                for item in navigation_requirements
            ],
        }

        return {
            "prd_contract_version": "2.0",
            "public_document": {
                "title": f"{module_name} 模块 PRD",
                "moduleKey": module_key,
                "moduleName": module_name,
                "summary": str(prd.analysis_summary or ""),
                "markdown": markdown,
                "visibleToUser": True,
            },
            "structured_requirements": structured_requirements,
            "navigation_requirements": navigation_requirements,
            "traceability_map": traceability,
            "semantic_sync_policy": {
                "prototypeToPrd": {
                    "autoDetectChangeTypes": [
                        "ADD_FIELD", "REMOVE_FIELD", "RENAME_FIELD", "CHANGE_FIELD_REQUIRED",
                        "ADD_ACTION", "REMOVE_ACTION", "CHANGE_ACTION_BEHAVIOR",
                        "ADD_TABLE_COLUMN", "REMOVE_TABLE_COLUMN", "CHANGE_VALIDATION_RULE",
                        "ADD_NAVIGATION", "CHANGE_NAVIGATION_TARGET", "CHANGE_BUSINESS_RULE",
                    ],
                    "ignoreVisualOnlyChanges": True,
                    "requiresUserConfirmation": True,
                    "targetArtifacts": ["MODULE_PRD", "PAGE_SPEC", "MODULE_APP_CONTRACT", "UI_CONTRACT"],
                },
                "prdToPrototype": {
                    "markImpactedPageSpecs": True,
                    "markImpactedUiContracts": True,
                    "markDevelopmentBaselineStale": True,
                    "regenerateOnlyImpactedPages": True,
                },
                "developmentSafety": {
                    "blockWhenUpstreamRequirementChanged": True,
                    "blockWhenNavigationTargetUnresolved": True,
                    "requireConfirmedUiContractSnapshot": True,
                },
            },
            "source_outline": {
                "functionalRequirementCount": len(prd.functional_requirements or []),
                "businessRuleCount": len(prd.business_rules or []),
                "stateRuleCount": len(prd.state_rules or []),
                "pageRequirementCount": len(prd.page_requirements or []),
                "dataRequirementCount": len(prd.data_requirements or []),
                "customSectionCount": len(getattr(prd, "custom_sections", []) or []),
                "apiRequirementCount": len(prd.api_requirements or []),
                "acceptanceCriteriaCount": len(prd.acceptance_criteria or []),
            },
        }

    def _derive_navigation_requirements(
        self,
        *,
        module_key: str,
        pages: list[dict[str, Any]],
        structured_requirements: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        def is_navigation_text(value: str) -> bool:
            text = str(value or "")
            return any(token in text.lower() for token in [
                "navigate", "redirect", "route", "detail", "open", "link",
            ]) or any(token in text for token in [
                "跳转", "进入", "打开", "查看", "详情", "导航", "链接",
            ])

        def page_route(page_key: str) -> str:
            key = self._safe_semantic_key(page_key, "page")
            return f"/{module_key}/{key}".replace("__", "_")

        def add_edge(
            *,
            source_page: dict[str, Any],
            target_page: dict[str, Any],
            action_key: str,
            action_label: str,
            reason: str,
        ) -> None:
            source_key = str(source_page.get("pageKey") or "").strip()
            target_key = str(target_page.get("pageKey") or "").strip()
            if not source_key or not target_key or source_key == target_key:
                return
            nav_id = self._safe_semantic_key(
                f"nav_{module_key}_{source_key}_{action_key}_{target_key}",
                f"nav_{len(result) + 1}",
            )
            if nav_id in seen:
                return
            seen.add(nav_id)
            result.append({
                "navigationId": nav_id,
                "sourcePageId": "",
                "sourcePageKey": source_key,
                "sourcePageName": str(source_page.get("pageName") or source_key),
                "sourceActionKey": self._safe_semantic_key(action_key, "navigate"),
                "sourceActionLabel": action_label or "查看",
                "targetPageId": "",
                "targetPageKey": target_key,
                "targetPageName": str(target_page.get("pageName") or target_key),
                "targetRoute": page_route(target_key),
                "scope": "INTRA_MODULE",
                "status": "PLANNED",
                "source": "MODULE_PRD_SEMANTIC_PROJECTION",
                "reason": reason,
            })

        for source_page in pages:
            actions = source_page.get("userActions") or []
            for action in actions:
                action_text = str(action or "")
                if not is_navigation_text(action_text):
                    continue
                for target_page in pages:
                    target_key = str(target_page.get("pageKey") or "")
                    target_name = str(target_page.get("pageName") or "")
                    if not target_key or target_key == source_page.get("pageKey"):
                        continue
                    if target_key in action_text or target_name in action_text:
                        add_edge(
                            source_page=source_page,
                            target_page=target_page,
                            action_key=action_text,
                            action_label=action_text,
                            reason="由 PRD 页面用户操作显式提到目标页面。",
                        )

        list_tokens = ["列表", "查询", "搜索", "管理", "list", "search", "table"]
        detail_tokens = ["详情", "查看", "detail", "view"]
        create_tokens = ["新增", "创建", "录入", "create", "add"]

        def has_token(page: dict[str, Any], tokens: list[str]) -> bool:
            text = " ".join([str(page.get("pageKey") or ""), str(page.get("pageName") or ""), str(page.get("description") or "")]).lower()
            return any(token.lower() in text for token in tokens)

        def stem(page: dict[str, Any]) -> str:
            text = " ".join([str(page.get("pageKey") or ""), str(page.get("pageName") or "")]).lower()
            for token in ["列表", "查询", "搜索", "管理", "详情", "查看", "新增", "创建", "录入", "页面", "页", "list", "search", "table", "detail", "view", "create", "add", "page", "_"]:
                text = text.replace(token, "")
            return re.sub(r"[^a-z0-9一-鿿]+", "", text)

        for source_page in pages:
            for target_page in pages:
                if source_page.get("pageKey") == target_page.get("pageKey"):
                    continue
                same_domain = bool(stem(source_page) and stem(target_page) and (stem(source_page) in stem(target_page) or stem(target_page) in stem(source_page)))
                if has_token(source_page, list_tokens) and has_token(target_page, detail_tokens) and same_domain:
                    add_edge(
                        source_page=source_page,
                        target_page=target_page,
                        action_key="view_detail",
                        action_label="查看详情",
                        reason="由 PRD 页面命名推断列表页到详情页导航。",
                    )
                if has_token(source_page, list_tokens) and has_token(target_page, create_tokens) and same_domain:
                    add_edge(
                        source_page=source_page,
                        target_page=target_page,
                        action_key="create_record",
                        action_label="新增",
                        reason="由 PRD 页面命名推断列表页到创建页导航。",
                    )

        return result

    def _safe_semantic_key(self, value: Any, fallback: str) -> str:
        text = str(value or "").strip().lower()
        text = re.sub(r"[^a-z0-9_\-一-鿿]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")
        return text or fallback

    def _list_values(self, value: Any) -> list[str]:
        if not isinstance(value, list):
            return []
        return [str(item).strip() for item in value if str(item or "").strip()]

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        requirement_text = self._get_requirement_text(request)
        context_package = request.context_package or {}
        input_payload = request.input or {}

        current_module = self._extract_current_module(context_package, input_payload)
        module_tree_summary = self._extract_module_tree_summary(context_package)
        related_artifacts = self._extract_related_artifacts(context_package)

        return "\n".join(
            [
                "请为当前指定模块生成企业级 MODULE_PRD。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"module_id: {request.module_id}",
                f"task_type: {request.task_type}",
                "",
                "【当前模块】",
                json.dumps(
                    current_module,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                *self.asset_contract_policy.build_prompt_sections(
                    request=request,
                ),
                "",
                "【用户补充需求】",
                requirement_text or "无额外补充需求。",
                "",
                "【模块树摘要】",
                json.dumps(
                    module_tree_summary,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【相关已有产物】",
                json.dumps(
                    related_artifacts,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【完整上下文包】",
                json.dumps(
                    context_package,
                    ensure_ascii=False,
                    default=str,
                ),
                "",

                "输出应区分用户可读内容和系统内部合同字段：markdown 面向普通业务用户，不要暴露 module_key、page_key、route_path、layout_type、api_key、permission_key 等内部标识。",
                "允许输出 custom_sections 数组保存用户自由补充说明；custom_sections 只作为 DOCUMENT_ONLY 文档内容，不直接改变模块树、PAGE_SPEC 或 UI_CONTRACT。",
                "请只输出符合 JSON Schema 的 JSON 对象。",
                "不要输出 markdown 字段。",
                "不要输出 JSON 代码块。",
            ]
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

    def _extract_module_tree_summary(
            self,
            context_package: dict[str, Any],
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            module_tree = artifacts.get("MODULE_TREE")
            if module_tree is not None:
                return module_tree

        if isinstance(artifacts, list):
            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                if artifact.get("artifact_type") == "MODULE_TREE":
                    return artifact

        return {}

    def _extract_related_artifacts(
            self,
            context_package: dict[str, Any],
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if artifacts is None:
            return []

        return artifacts

    # A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
    def _build_markdown(self, prd: ModulePrdLlmOutput) -> str:
        info = prd.module_basic_info

        lines: list[str] = [
            f"# {info.module_name}",
            "",
            "## 模块概述",
            f"模块位置：{info.module_position}",
            "",
            f"模块目标：{info.module_goal}",
            "",
            f"职责边界：{info.responsibility_boundary}",
            "",
            "## 业务背景",
            prd.business_background,
            "",
            "## 用户角色",
        ]

        for role in prd.user_roles:
            lines.append(f"- **{role.role_name}**：{role.description}")

        lines.extend(["", "## 功能需求"])

        for index, item in enumerate(prd.functional_requirements, start=1):
            lines.extend(
                [
                    f"### {index}. {item.title}",
                    item.description,
                    f"- 优先级：{item.priority}",
                    f"- 主要输入：{item.input_description}",
                    f"- 处理结果：{item.output_description}",
                    "- 前置条件：" + self._join_list(item.preconditions),
                    "- 完成后结果：" + self._join_list(item.postconditions),
                    "",
                ]
            )

        lines.extend(["## 业务规则"])

        for item in prd.business_rules:
            lines.extend(
                [
                    f"- **{item.title}**",
                    f"  - 说明：{item.description}",
                    f"  - 触发条件：{item.trigger_condition}",
                    f"  - 期望结果：{item.expected_result}",
                ]
            )

        lines.extend(["", "## 状态与流转"])

        if prd.state_rules:
            for item in prd.state_rules:
                lines.extend(
                    [
                        f"- **{item.state_name}**",
                        f"  - 说明：{item.description}",
                        f"  - 可流转至：{self._join_list(item.allowed_transitions)}",
                    ]
                )
        else:
            lines.append("- 当前模块未识别出明确状态流转。")

        lines.extend(["", "## 页面与操作设计"])

        if prd.page_requirements:
            for item in prd.page_requirements:
                page_type = str(getattr(item, "page_type", "") or "").strip()
                lines.extend(
                    [
                        f"- **{item.page_name}**" + (f"（{page_type}）" if page_type else ""),
                        f"  - 页面说明：{item.description}",
                        f"  - 主要信息：{self._join_list(getattr(item, 'form_fields', []) or getattr(item, 'main_components', []))}",
                        f"  - 筛选条件：{self._join_list(getattr(item, 'filter_fields', []))}",
                        f"  - 列表字段：{self._join_list(getattr(item, 'table_columns', []))}",
                        f"  - 主要操作：{self._join_list(getattr(item, 'primary_actions', []) or getattr(item, 'user_actions', []))}",
                        f"  - 行操作：{self._join_list(getattr(item, 'row_actions', []))}",
                        f"  - 关联页面：{self._join_list(getattr(item, 'navigation_targets', []))}",
                    ]
                )
        else:
            lines.append("- 当前模块暂无独立页面设计。")

        lines.extend(["", "## 数据需求"])

        for item in prd.data_requirements:
            lines.extend(
                [
                    f"- **{item.entity_name}**",
                    f"  - 说明：{item.description}",
                    f"  - 关键字段：{self._join_list(item.important_fields)}",
                    f"  - 约束：{self._join_list(item.constraints)}",
                ]
            )

        lines.extend(["", "## 异常场景"])

        for item in prd.exception_scenarios:
            lines.extend(
                [
                    f"- **{item.title}**",
                    f"  - 场景：{item.description}",
                    f"  - 处理策略：{item.handling_strategy}",
                ]
            )

        lines.extend(["", "## 验收标准"])

        for index, item in enumerate(prd.acceptance_criteria, start=1):
            lines.extend(
                [
                    f"- **验收标准 {index}**：{item.description}",
                    f"  - 验证方式：{item.verification_method}",
                ]
            )

        lines.extend(["", "## 非功能需求"])

        for item in prd.non_functional_requirements:
            lines.extend(
                [
                    f"- **{item.category}**",
                    f"  - 要求：{item.description}",
                    f"  - 目标：{item.target}",
                ]
            )

        for custom in getattr(prd, "custom_sections", []) or []:
            title = str(getattr(custom, "title", "") or "").strip()
            body = str(getattr(custom, "body", "") or "").strip()
            if not title and not body:
                continue
            lines.extend(["", f"## {title or '补充说明'}"])
            if body:
                lines.append(body)

        lines.extend(["", "## 分析摘要", prd.analysis_summary])

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

# A9.10.14.4.3.14.3_MODULE_PRD_PAGE_TREE_SYNC_FIX
# PRD is a dual-layer asset: user-readable markdown + structured page contracts.
# After the LLM creates the business PRD, deterministically align page_requirements
# with the current MODULE_TREE PAGE nodes.  This keeps module-tree add/update/delete
# reflected in PRD and prevents PAGE_SPEC/UI_CONTRACT from receiving an empty or
# stale page seed.
try:
    _A9_10_14_4_3_14_3_ORIGINAL_MODULE_PRD_RUN = ModulePrdAgent.run
except NameError:  # pragma: no cover
    _A9_10_14_4_3_14_3_ORIGINAL_MODULE_PRD_RUN = None


def _a9_14_3_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a9_14_3_norm(value: Any) -> str:
    return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value or "").strip().lower())


def _a9_14_3_safe_key(value: Any, fallback: str = "page") -> str:
    text = str(value or "").strip().lower()
    text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or fallback


def _a9_14_3_as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _a9_14_3_unique_text(values: list[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value or "").strip(" ，,。；;:：、\n\t")
        if not text:
            continue
        key = _a9_14_3_norm(text)
        if key and key not in seen:
            seen.add(key)
            result.append(text)
    return result


def _a9_14_3_unwrap_artifact(value: Any) -> Any:
    if isinstance(value, dict) and isinstance(value.get("content"), dict):
        result = dict(value["content"])
        for key in ["markdown", "contentMarkdown", "content_markdown"]:
            if key not in result and value.get(key):
                result[key] = value.get(key)
        return result
    return value


def _a9_14_3_extract_artifact(context_package: dict[str, Any], artifact_type: str) -> Any:
    artifacts = context_package.get("artifacts")
    target = str(artifact_type or "").strip().upper()
    if isinstance(artifacts, dict):
        direct = artifacts.get(target) or artifacts.get(target.lower())
        if direct is not None:
            return _a9_14_3_unwrap_artifact(direct)
        for key, value in artifacts.items():
            value_type = ""
            if isinstance(value, dict):
                value_type = str(value.get("artifact_type") or value.get("artifactType") or value.get("type") or "").upper()
            if str(key).upper() == target or value_type == target:
                return _a9_14_3_unwrap_artifact(value)
    if isinstance(artifacts, list):
        for item in artifacts:
            if not isinstance(item, dict):
                continue
            value_type = str(item.get("artifact_type") or item.get("artifactType") or item.get("type") or "").upper()
            if value_type == target:
                return _a9_14_3_unwrap_artifact(item)
    return {}


def _a9_14_3_current_module(request: AgentTaskRequest) -> dict[str, Any]:
    ctx = request.context_package or {}
    inp = request.input or {}
    for value in [inp.get("current_module"), ctx.get("current_module"), ctx.get("module")]:
        if isinstance(value, dict):
            return value
    return {"module_id": request.module_id, "module_key": "", "module_name": "", "module_type": "MODULE"}


def _a9_14_3_node_id(node: dict[str, Any]) -> str:
    return _a9_14_3_text(node.get("id"), node.get("moduleId"), node.get("module_id"), node.get("nodeId"), node.get("node_id"))


def _a9_14_3_node_key(node: dict[str, Any]) -> str:
    return _a9_14_3_text(node.get("key"), node.get("moduleKey"), node.get("module_key"), node.get("pageKey"), node.get("page_key"))


def _a9_14_3_node_name(node: dict[str, Any]) -> str:
    return _a9_14_3_text(node.get("name"), node.get("moduleName"), node.get("module_name"), node.get("pageName"), node.get("page_name"), _a9_14_3_node_key(node))


def _a9_14_3_node_type(node: dict[str, Any]) -> str:
    return str(_a9_14_3_text(node.get("type"), node.get("moduleType"), node.get("module_type"), node.get("nodeType"), node.get("node_type"))).upper()


def _a9_14_3_page_kind_from_text(value: Any) -> str:
    text = _a9_14_3_norm(value)
    if re.search(r"列表|查询|搜索|管理|list|search|table", text, re.I):
        return "LIST_PAGE"
    if re.search(r"创建|新建|新增|录入|登记|编辑|修改|create|add|edit|update|form", text, re.I):
        return "FORM_PAGE"
    if re.search(r"审核|审批|approval|audit", text, re.I):
        return "APPROVAL_PAGE"
    if re.search(r"详情|明细|查看|detail|view", text, re.I):
        return "DETAIL_PAGE"
    if re.search(r"看板|统计|分析|dashboard", text, re.I):
        return "DASHBOARD_PAGE"
    return "FORM_PAGE"


def _a9_14_3_page_type_from_kind(kind: str) -> str:
    text = str(kind or "").upper()
    if "LIST" in text:
        return "LIST"
    if "APPROVAL" in text or "AUDIT" in text:
        return "APPROVAL"
    if "DETAIL" in text or "VIEW" in text:
        return "DETAIL"
    if "FORM" in text or "CREATE" in text or "EDIT" in text or "ADD" in text:
        return "FORM"
    if "DASHBOARD" in text:
        return "DASHBOARD"
    return "FORM"


def _a9_14_3_layout_from_page_type(page_type: str) -> str:
    return {
        "LIST": "table-list",
        "FORM": "form-detail",
        "DETAIL": "detail-tabs",
        "APPROVAL": "approval-workbench",
        "DASHBOARD": "dashboard",
    }.get(page_type, "form-detail")


def _a9_14_3_flatten_tree(value: Any, parent_key: str = "", parent_id: str = "") -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    if isinstance(value, dict):
        if isinstance(value.get("content"), dict):
            return _a9_14_3_flatten_tree(value.get("content"), parent_key, parent_id)
        if isinstance(value.get("flat_modules"), list):
            for item in value.get("flat_modules") or []:
                if isinstance(item, dict):
                    node = dict(item)
                    node.setdefault("parent_key", node.get("parentKey") or "")
                    result.append(node)
        roots = value.get("modules") or value.get("children") or []
        if isinstance(roots, list):
            for child in roots:
                result.extend(_a9_14_3_flatten_tree(child, parent_key, parent_id))
        return result
    if isinstance(value, list):
        for item in value:
            result.extend(_a9_14_3_flatten_tree(item, parent_key, parent_id))
        return result
    if not isinstance(value, dict):
        return result
    return result


def _a9_14_3_flatten_nested_nodes(value: Any, parent_key: str = "", parent_id: str = "") -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    if isinstance(value, list):
        for item in value:
            result.extend(_a9_14_3_flatten_nested_nodes(item, parent_key, parent_id))
        return result
    if not isinstance(value, dict):
        return result
    if isinstance(value.get("content"), dict):
        return _a9_14_3_flatten_nested_nodes(value.get("content"), parent_key, parent_id)
    if isinstance(value.get("flat_modules"), list):
        return [dict(item) for item in value.get("flat_modules") if isinstance(item, dict)]
    if isinstance(value.get("modules"), list) and not any(k in value for k in ["key", "moduleKey", "module_key", "name", "moduleName", "module_name", "type", "moduleType", "module_type"]):
        result: list[dict[str, Any]] = []
        for item in value.get("modules") or []:
            result.extend(_a9_14_3_flatten_nested_nodes(item, parent_key, parent_id))
        return result
    node = dict(value)
    if any(k in node for k in ["key", "moduleKey", "module_key", "name", "moduleName", "module_name", "type", "moduleType", "module_type"]):
        if parent_key and not _a9_14_3_text(node.get("parent_key"), node.get("parentKey")):
            node["parent_key"] = parent_key
        if parent_id and not _a9_14_3_text(node.get("parent_id"), node.get("parentId")):
            node["parent_id"] = parent_id
        result.append(node)
        next_parent_key = _a9_14_3_node_key(node)
        next_parent_id = _a9_14_3_node_id(node)
    else:
        next_parent_key = parent_key
        next_parent_id = parent_id
    for child in _a9_14_3_as_list(value.get("children")):
        result.extend(_a9_14_3_flatten_nested_nodes(child, next_parent_key, next_parent_id))
    return result


def _a9_14_3_extract_pages_from_module_tree(request: AgentTaskRequest) -> list[dict[str, Any]]:
    ctx = request.context_package or {}
    current = _a9_14_3_current_module(request)
    tree = _a9_14_3_extract_artifact(ctx, "MODULE_TREE") or ctx.get("module_tree") or ctx.get("moduleTree") or {}
    flat = _a9_14_3_flatten_nested_nodes(tree)
    if not flat:
        # The current module may already carry a modulePrototypeMap from ModuleTreeAgent.
        mp = current.get("modulePrototypeMap") or current.get("module_prototype_map") or current.get("module_prototype_map")
        if isinstance(mp, dict) and isinstance(mp.get("pages"), list):
            return [dict(item) for item in mp.get("pages") if isinstance(item, dict)]
        return []

    by_key = {_a9_14_3_node_key(node): node for node in flat if _a9_14_3_node_key(node)}
    by_id = {_a9_14_3_node_id(node): node for node in flat if _a9_14_3_node_id(node)}
    cur_ids = {_a9_14_3_text(current.get(k)) for k in ["id", "moduleId", "module_id", "nodeId", "node_id"] if _a9_14_3_text(current.get(k))}
    cur_keys = {_a9_14_3_text(current.get(k)) for k in ["key", "moduleKey", "module_key"] if _a9_14_3_text(current.get(k))}
    root_node = None
    for x in cur_ids:
        if x in by_id:
            root_node = by_id[x]
            break
    if root_node is None:
        for x in cur_keys:
            if x in by_key:
                root_node = by_key[x]
                break

    parent_children: dict[str, list[dict[str, Any]]] = {}
    for node in flat:
        parent = _a9_14_3_text(node.get("parent_key"), node.get("parentKey"), node.get("parent_id"), node.get("parentId"))
        parent_children.setdefault(parent, []).append(node)

    def descendants(root: dict[str, Any]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        queue = [_a9_14_3_node_key(root), _a9_14_3_node_id(root)]
        seen: set[str] = set()
        while queue:
            parent = queue.pop(0)
            if not parent or parent in seen:
                continue
            seen.add(parent)
            for child in parent_children.get(parent, []):
                result.append(child)
                queue.append(_a9_14_3_node_key(child))
                queue.append(_a9_14_3_node_id(child))
        return result

    scope = descendants(root_node) if root_node is not None else flat
    pages = [node for node in scope if _a9_14_3_node_type(node) == "PAGE"]
    if not pages and _a9_14_3_node_type(current) == "PAGE":
        pages = [current]
    if not pages and root_node is None:
        pages = [node for node in flat if _a9_14_3_node_type(node) == "PAGE"]
    return pages


def _a9_14_3_page_proto(node: dict[str, Any]) -> dict[str, Any]:
    for key in ["pagePrototypeContext", "page_prototype_context", "prototypeContext", "prototype_context"]:
        value = node.get(key)
        if isinstance(value, dict):
            return value
    extra = node.get("extra")
    if isinstance(extra, dict):
        for key in ["pagePrototypeContext", "page_prototype_context"]:
            value = extra.get(key)
            if isinstance(value, dict):
                return value
    return {}


def _a9_14_3_fields_for_page(node: dict[str, Any], page_type: str, existing: dict[str, Any]) -> dict[str, list[str]]:
    proto = _a9_14_3_page_proto(node)
    core = _a9_14_3_unique_text(
        _a9_14_3_as_list(proto.get("coreFields"))
        + _a9_14_3_as_list(proto.get("core_fields"))
        + _a9_14_3_as_list(existing.get("form_fields"))
        + _a9_14_3_as_list(existing.get("formFields"))
        + _a9_14_3_as_list(existing.get("table_columns"))
        + _a9_14_3_as_list(existing.get("tableColumns"))
        + _a9_14_3_as_list(existing.get("filter_fields"))
        + _a9_14_3_as_list(existing.get("filterFields"))
    )
    if not core:
        txt = " ".join([_a9_14_3_node_name(node), _a9_14_3_node_key(node), str(proto)])
        if "入库" in txt:
            core = ["入库单号", "供应商", "仓库", "入库状态", "经办人", "创建时间", "入库数量", "备注"]
        elif "出库" in txt:
            core = ["出库单号", "客户/领用方", "仓库", "出库状态", "经办人", "创建时间", "出库数量", "备注"]
        elif "库存" in txt:
            core = ["物料编码", "物料名称", "仓库", "库存数量", "可用数量", "预警状态", "更新时间"]
        else:
            core = ["业务编号", "业务名称", "状态", "负责人", "创建时间", "备注"]

    def editable(labels: list[str]) -> list[str]:
        out: list[str] = []
        deny = re.compile(r"操作|创建人|创建时间|创建日期|更新人|更新时间|审核人|审核时间|审批人|审批时间|流程|处理进度|关联记录|详情|查看|删除|状态$", re.I)
        for label in labels:
            if not label or deny.search(label):
                continue
            out.append(label)
        return _a9_14_3_unique_text(out)[:12]

    if page_type == "LIST":
        return {"filter_fields": core[:5], "table_columns": core[:10], "form_fields": []}
    if page_type in {"FORM", "APPROVAL"}:
        return {"filter_fields": [], "table_columns": [], "form_fields": editable(core) or core[:5]}
    if page_type == "DETAIL":
        return {"filter_fields": [], "table_columns": [], "form_fields": core[:12]}
    return {"filter_fields": [], "table_columns": [], "form_fields": core[:8]}


def _a9_14_3_actions_for_page(node: dict[str, Any], page_type: str, all_pages: list[dict[str, Any]], existing: dict[str, Any]) -> dict[str, list[str]]:
    proto = _a9_14_3_page_proto(node)
    raw_actions = _a9_14_3_unique_text(
        _a9_14_3_as_list(proto.get("mainActions"))
        + _a9_14_3_as_list(proto.get("main_actions"))
        + _a9_14_3_as_list(existing.get("primary_actions"))
        + _a9_14_3_as_list(existing.get("primaryActions"))
        + _a9_14_3_as_list(existing.get("user_actions"))
        + _a9_14_3_as_list(existing.get("userActions"))
    )
    sibling_names = " ".join(_a9_14_3_node_name(p) for p in all_pages if p is not node)
    if page_type == "LIST":
        primary = ["查询", "重置"]
        if re.search(r"创建|新建|新增|录入|create|add", sibling_names, re.I):
            primary.append("新建")
        primary.extend([x for x in raw_actions if re.search(r"导出|刷新", x)])
        row = []
        if re.search(r"详情|查看|detail|view", sibling_names, re.I):
            row.append("查看详情")
        if re.search(r"编辑|修改|edit", sibling_names, re.I):
            row.append("编辑")
        if re.search(r"审核|审批|approval|audit", sibling_names, re.I):
            row.append("查看审核")
        return {"primary_actions": _a9_14_3_unique_text(primary), "row_actions": _a9_14_3_unique_text(row)}
    if page_type == "FORM":
        actions = raw_actions or ["保存", "取消"]
        keep = [x for x in actions if re.search(r"保存|提交|取消|返回", x)] or ["保存", "取消"]
        return {"primary_actions": _a9_14_3_unique_text(keep), "row_actions": []}
    if page_type == "APPROVAL":
        actions = raw_actions or ["通过", "驳回", "返回"]
        keep = [x for x in actions if re.search(r"通过|驳回|退回|保存|提交|取消|返回", x)] or ["通过", "驳回", "返回"]
        return {"primary_actions": _a9_14_3_unique_text(keep), "row_actions": []}
    if page_type == "DETAIL":
        primary = ["返回"]
        if re.search(r"编辑|修改|edit", sibling_names, re.I):
            primary.append("编辑")
        if re.search(r"审核|审批|approval|audit", sibling_names, re.I):
            primary.append("提交审核")
        return {"primary_actions": _a9_14_3_unique_text(primary), "row_actions": []}
    return {"primary_actions": raw_actions or ["确认", "取消"], "row_actions": []}


def _a9_14_3_requirement_to_dict(item: Any) -> dict[str, Any]:
    if isinstance(item, dict):
        return dict(item)
    if hasattr(item, "model_dump"):
        return item.model_dump(mode="python")
    if hasattr(item, "dict"):
        return item.dict()
    return {}


def _a9_14_3_align_page_requirements(existing_items: list[Any], pages: list[dict[str, Any]]) -> list[Any]:
    from app.schemas.module_prd import PageRequirement

    existing_by_key: dict[str, dict[str, Any]] = {}
    for item in existing_items or []:
        data = _a9_14_3_requirement_to_dict(item)
        key = _a9_14_3_safe_key(_a9_14_3_text(data.get("page_key"), data.get("pageKey"), data.get("key"), data.get("page_name"), data.get("pageName")), "page")
        if key:
            existing_by_key[key] = data

    page_keys = []
    for page in pages:
        key = _a9_14_3_safe_key(_a9_14_3_text(_a9_14_3_node_key(page), _a9_14_3_node_name(page)), "page")
        page_keys.append(key)

    aligned: list[PageRequirement] = []
    for page in pages:
        key = _a9_14_3_safe_key(_a9_14_3_text(_a9_14_3_node_key(page), _a9_14_3_node_name(page)), "page")
        existing = existing_by_key.get(key, {})
        name = _a9_14_3_text(_a9_14_3_node_name(page), existing.get("page_name"), existing.get("pageName"), key)
        page_kind = _a9_14_3_text(page.get("pageKind"), page.get("page_kind"), page.get("page_type"), existing.get("page_type")) or _a9_14_3_page_kind_from_text(" ".join([name, key]))
        page_type = _a9_14_3_page_type_from_kind(page_kind)
        fields = _a9_14_3_fields_for_page(page, page_type, existing)
        actions = _a9_14_3_actions_for_page(page, page_type, pages, existing)
        proto = _a9_14_3_page_proto(page)
        parent_feature_key = _a9_14_3_text(page.get("parentFeatureKey"), page.get("parent_feature_key"), page.get("parent_key"), page.get("parentKey"), proto.get("parentFeatureKey"), proto.get("parent_feature_key"))
        parent_feature_name = _a9_14_3_text(page.get("parentFeatureName"), page.get("parent_feature_name"), proto.get("parentFeatureName"), proto.get("parent_feature_name"))
        route = _a9_14_3_text(page.get("routePath"), page.get("route_path"), page.get("route"), existing.get("route_path"), existing.get("routePath"), f"/{key.replace('_', '-')}")
        layout_type = _a9_14_3_text(page.get("layoutType"), page.get("layout_type"), existing.get("layout_type"), existing.get("layoutType"), _a9_14_3_layout_from_page_type(page_type))
        primary_entity = _a9_14_3_text(page.get("primaryEntity"), page.get("primary_entity"), existing.get("primary_entity"), existing.get("primaryEntity"), name.replace("页", ""))
        description = _a9_14_3_text(existing.get("description"), page.get("description"), proto.get("goal"))
        if not description:
            description = f"{name}用于承载{primary_entity}的{page_type.lower()}页面任务。"

        main_components = _a9_14_3_unique_text(_a9_14_3_as_list(existing.get("main_components")) + _a9_14_3_as_list(existing.get("mainComponents")))
        if not main_components:
            main_components = {
                "LIST": ["查询筛选区", "数据列表", "行操作区", "空状态"],
                "FORM": ["基础信息表单", "校验提示", "底部操作栏"],
                "DETAIL": ["概要信息", "详情信息区", "操作记录"],
                "APPROVAL": ["审核信息", "业务详情", "审批操作区"],
                "DASHBOARD": ["指标卡片", "趋势区域", "快捷入口"],
            }.get(page_type, ["页面主体", "操作区"])

        user_actions = _a9_14_3_unique_text(actions["primary_actions"] + actions["row_actions"])
        navigation_targets = []
        for target in pages:
            target_key = _a9_14_3_safe_key(_a9_14_3_text(_a9_14_3_node_key(target), _a9_14_3_node_name(target)), "page")
            if target_key != key:
                navigation_targets.append(target_key)

        aligned.append(PageRequirement(
            page_key=key,
            page_name=name,
            description=description,
            main_components=main_components,
            user_actions=user_actions,
            page_type=page_type,
            route_path=route,
            layout_type=layout_type,
            primary_entity=primary_entity,
            parent_feature_key=parent_feature_key,
            parent_feature_name=parent_feature_name,
            source_page_id=_a9_14_3_node_id(page),
            sync_status="SYNCED_FROM_MODULE_TREE",
            filter_fields=fields["filter_fields"],
            table_columns=fields["table_columns"],
            form_fields=fields["form_fields"],
            primary_actions=actions["primary_actions"],
            row_actions=actions["row_actions"],
            navigation_targets=_a9_14_3_unique_text(navigation_targets),
        ))
    return aligned


def _a9_14_3_complete_module_prd_artifact(self: ModulePrdAgent, artifact: ModulePrdArtifact, request: AgentTaskRequest) -> ModulePrdArtifact:
    # A9.10.14.4.3.15.2.4.2_MODULE_PRD_MARK_NAMEERROR_HOTFIX: legacy 14.3 completion code references MARK during page_requirements sync.
    MARK = "A9.10.14.4.3.14.3_MODULE_PRD_PAGE_TREE_SYNC"
    pages = _a9_14_3_extract_pages_from_module_tree(request)
    if not pages:
        return artifact
    completed_pages = _a9_14_3_align_page_requirements(list(artifact.page_requirements or []), pages)
    if not completed_pages:
        return artifact
    updated = artifact.model_copy(update={"page_requirements": completed_pages})
    markdown = self._build_markdown(updated)  # same fields as ModulePrdLlmOutput/Artifact
    projection = self._build_semantic_projection(updated, markdown, request)
    notes = list(updated.development_notes or [])
    note = f"{MARK}: 已根据当前 MODULE_TREE 对 page_requirements 执行新增/修改/删除同步，共 {len(completed_pages)} 个 PAGE。"
    if note not in notes:
        notes.append(note)
    return updated.model_copy(update={
        "prd_contract_version": projection.get("prd_contract_version", "2.1"),
        "public_document": projection.get("public_document", {}),
        "structured_requirements": projection.get("structured_requirements", []),
        "navigation_requirements": projection.get("navigation_requirements", []),
        "traceability_map": projection.get("traceability_map", {}),
        "semantic_sync_policy": projection.get("semantic_sync_policy", {}),
        "source_outline": projection.get("source_outline", {}),
        "markdown": markdown,
        "development_notes": notes,
    })


async def _a9_14_3_module_prd_run(self: ModulePrdAgent, request: AgentTaskRequest) -> ModulePrdArtifact:
    if _A9_10_14_4_3_14_3_ORIGINAL_MODULE_PRD_RUN is None:
        raise RuntimeError("Original ModulePrdAgent.run is unavailable")
    artifact = await _A9_10_14_4_3_14_3_ORIGINAL_MODULE_PRD_RUN(self, request)
    artifact = _a9_14_3_complete_module_prd_artifact(self, artifact, request)
    print(f"[{MARK}] ModulePrdAgent returned PRD with MODULE_TREE-synced page requirements", flush=True)
    return artifact


ModulePrdAgent._a9_14_3_complete_module_prd_artifact = _a9_14_3_complete_module_prd_artifact  # type: ignore[attr-defined]
ModulePrdAgent.run = _a9_14_3_module_prd_run  # type: ignore[assignment]

