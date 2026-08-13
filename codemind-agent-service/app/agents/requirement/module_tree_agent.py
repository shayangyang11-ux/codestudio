
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.module_tree import (
    MODULE_TREE_LLM_JSON_SCHEMA,
    FlatModuleNode,
    ModuleNode,
    ModuleTreeArtifact,
    ModuleTreeLlmOutput,
)
from app.services.llm_service import LlmService
from app.agents.requirement.module_tree_quality_calibrator import ModuleTreeQualityCalibrator


class ModuleTreeAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()
        self.quality_calibrator = ModuleTreeQualityCalibrator()

    async def run(self, request: AgentTaskRequest) -> ModuleTreeArtifact:
        requirement_text = self._get_requirement_text(request)
        modeling_mode = self._resolve_modeling_mode(request, requirement_text)
        generation_plan = (
            (request.input or {}).get("moduleTreeGenerationPlan")
            or (request.input or {}).get("module_tree_generation_plan")
            or (request.context_package or {}).get("moduleTreeGenerationPlan")
            or (request.context_package or {}).get("module_tree_generation_plan")
        )
        quality_notes: list[str] = []

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._fallback_system_prompt(),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(
                request,
                requirement_text,
                modeling_mode,
            ),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="module_tree_output",
            output_json_schema=MODULE_TREE_LLM_JSON_SCHEMA,
        )

        parsed = ModuleTreeLlmOutput.model_validate_json(
            self._strip_code_fence(llm_result.text)
        )

        normalized_flat_modules = [
            self._normalize_flat_node(item)
            for item in parsed.modules
        ]

        normalized_flat_modules = self._harden_flat_modules_for_mode(
            normalized_flat_modules,
            modeling_mode,
            requirement_text,
        )

        calibrator = getattr(self, "quality_calibrator", None)
        if calibrator is None:
            calibrator = ModuleTreeQualityCalibrator()
            setattr(self, "quality_calibrator", calibrator)

        quality_result = calibrator.calibrate(
            normalized_flat_modules,
            modeling_mode=modeling_mode,
            requirement_text=requirement_text,
            generation_plan=generation_plan,
        )
        normalized_flat_modules = quality_result.nodes
        quality_notes = quality_result.notes

        normalized_flat_modules = self._enrich_hidden_prototype_contexts(
            normalized_flat_modules,
            modeling_mode,
            requirement_text,
        )

        tree_modules = self._build_tree(normalized_flat_modules)

        markdown = parsed.markdown
        if not markdown.strip():
            markdown = self._build_markdown(tree_modules, requirement_text)

        return ModuleTreeArtifact(
            modules=tree_modules,
            flat_modules=normalized_flat_modules,
            markdown=markdown,
            analysis_summary=self._merge_analysis_summary(parsed.analysis_summary, quality_notes),
        )

    def _merge_analysis_summary(
        self,
        analysis_summary: str,
        quality_notes: list[str] | None,
    ) -> str:
        notes = [str(item).strip() for item in (quality_notes or []) if str(item).strip()]
        base = (analysis_summary or "").strip()
        if not notes:
            return base
        lines = [base] if base else []
        lines.append("模块树质量校准：" + "；".join(notes[:8]))
        return "\n".join(lines)

    def _fallback_system_prompt(self) -> str:
        return (
            "你是 CodeMind Studio 的企业级需求架构 Agent。"
            "你的任务是把原始业务需求拆解成可落地的企业级需求骨架。"
            "支持两种建模模式：PROGRESSIVE_MODULE_OUTLINE 只生成可继续拆分的 MODULE 层级；"
            "FULL_ENTERPRISE_BLUEPRINT 生成 MODULE -> FEATURE -> PAGE 的完整蓝图。"
            "不要生成交互原型或组件；UI_CONTRACT 只能在用户选中 PAGE 后单独生成。"
            "如果输入很粗，默认更偏向渐进式模块骨架，不要过早固定页面。"
        )


    def _resolve_modeling_mode(
        self,
        request: AgentTaskRequest,
        requirement_text: str,
    ) -> str:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        raw = (
            input_payload.get("modeling_mode")
            or input_payload.get("modelingMode")
            or context_package.get("modeling_mode")
            or context_package.get("modelingMode")
        )

        normalized = str(raw or "").strip().upper()

        if normalized in {
            "PROGRESSIVE_MODULE_OUTLINE",
            "MODULE_OUTLINE",
            "OUTLINE",
            "PROGRESSIVE",
            "MODULE_ONLY",
        }:
            return "PROGRESSIVE_MODULE_OUTLINE"

        if normalized in {
            "FULL_ENTERPRISE_BLUEPRINT",
            "FULL_BLUEPRINT",
            "ENTERPRISE_BLUEPRINT",
            "FULL",
            "BLUEPRINT",
        }:
            return "FULL_ENTERPRISE_BLUEPRINT"

        if self._is_coarse_requirement(requirement_text):
            return "PROGRESSIVE_MODULE_OUTLINE"

        return "FULL_ENTERPRISE_BLUEPRINT"

    def _is_coarse_requirement(
        self,
        requirement_text: str,
    ) -> bool:
        text = (requirement_text or "").strip()

        if not text:
            return True

        explicit_detail_keywords = [
            "页面",
            "列表页",
            "详情页",
            "编辑页",
            "新建页",
            "审批页",
            "工作台",
            "路由",
            "字段",
            "按钮",
            "表格列",
            "原型",
            "UI_CONTRACT",
            "PAGE_SPEC",
        ]

        if any(keyword in text for keyword in explicit_detail_keywords):
            return False

        business_keywords = [
            "模块",
            "管理",
            "系统",
            "平台",
            "支持",
            "包含",
            "需要",
            "入库",
            "出库",
            "库存",
            "订单",
            "用户",
            "供应商",
            "仓库",
        ]

        if len(text) <= 260 and any(keyword in text for keyword in business_keywords):
            return True

        return len(text) <= 160

    def _build_modeling_mode_instruction(
        self,
        modeling_mode: str,
    ) -> str:
        if modeling_mode == "PROGRESSIVE_MODULE_OUTLINE":
            return "\n".join(
                [
                    "【渐进式模块骨架模式】",
                    "- 只输出 MODULE 节点，不输出 FEATURE，不输出 PAGE。",
                    "- 可以输出多层 MODULE，例如：业务域模块 -> 业务子域模块 -> 末级候选模块。",
                    "- 末级 MODULE 仍保持草稿状态，后续由用户继续拆模块或定稿生成 PRD。",
                    "- 对粗需求不要猜具体页面，不要过早固定功能点和页面。",
                    "- 建议根模块数量 4 到 8 个；重点体现企业级系统边界。",
                ]
            )

        return "\n".join(
            [
                "【完整企业蓝图模式】",
                "- 允许输出 MODULE / FEATURE / PAGE。",
                "- 必须先有多层 MODULE 信息架构，再在末级需求 MODULE 下输出 FEATURE。",
                "- PAGE 必须挂在 FEATURE 下，不允许 PAGE 直接挂 MODULE 下。",
                "- 每个核心业务末级模块建议有 2 到 5 个 FEATURE；每个 FEATURE 下建议有 1 到 3 个 PAGE。",
                "- 生成内容要像企业级项目蓝图，不能只有一层模块或一组随意页面。",
                "- 每个末级需求 MODULE 必须在 requirement_context.moduleRequirementMarkdown 中提供一份完整的用户可读模块需求文档。",
                "- 模块需求文档必须覆盖模块定位、业务目标、使用角色、业务范围、业务对象、主流程、功能点需求、页面规划、状态规则、异常场景、权限边界和验收要点。",
                "- 模块需求文档不要写 PAGE_SPEC、UI_CONTRACT、traceability、prototypeContext 等内部工程词。",
            ]
        )

    def _harden_flat_modules_for_mode(
        self,
        nodes: list[FlatModuleNode],
        modeling_mode: str,
        requirement_text: str,
    ) -> list[FlatModuleNode]:
        if modeling_mode == "PROGRESSIVE_MODULE_OUTLINE":
            return self._harden_progressive_outline(nodes, requirement_text)

        return self._harden_full_blueprint(nodes)

    def _harden_progressive_outline(
        self,
        nodes: list[FlatModuleNode],
        requirement_text: str,
    ) -> list[FlatModuleNode]:
        module_nodes = [
            FlatModuleNode(
                key=self._safe_key(item.key),
                parent_key=self._safe_key(item.parent_key) if item.parent_key else "",
                name=item.name,
                type="MODULE",
                description=item.description,
                sort_order=item.sort_order,
                page_kind=None,
                route_path=None,
                layout_type=None,
                primary_entity=None,
                can_generate_ui_contract=False,
                feature_role=None,
            )
            for item in nodes
        ]

        module_nodes = self._drop_invalid_parent_references(module_nodes)

        if len(module_nodes) >= 4:
            return module_nodes

        return self._fallback_progressive_outline(requirement_text)

    def _harden_full_blueprint(
        self,
        nodes: list[FlatModuleNode],
    ) -> list[FlatModuleNode]:
        result: list[FlatModuleNode] = []
        by_key = {item.key: item for item in nodes}

        for item in nodes:
            node_type = item.type
            parent = by_key.get(item.parent_key)

            if node_type == "PAGE" and (not parent or parent.type != "FEATURE"):
                feature_key = f"{item.key}_feature"
                feature_name = item.name.replace("页", "")
                result.append(
                    FlatModuleNode(
                        key=feature_key,
                        parent_key=parent.key if parent and parent.type == "MODULE" else "",
                        name=feature_name or "页面功能",
                        type="FEATURE",
                        description=f"承载{item.name}的业务功能点。",
                        sort_order=max(1, item.sort_order),
                        page_kind=None,
                        route_path=None,
                        layout_type=None,
                        primary_entity=None,
                        can_generate_ui_contract=False,
                        feature_role="page_capability",
                    )
                )
                item = item.model_copy(update={"parent_key": feature_key})

            if node_type == "FEATURE" and parent and parent.type == "PAGE":
                module_key = f"{item.key}_module"
                result.append(
                    FlatModuleNode(
                        key=module_key,
                        parent_key="",
                        name=item.name.replace("功能", "管理") or "业务模块",
                        type="MODULE",
                        description=f"承载{item.name}的需求模块。",
                        sort_order=item.sort_order,
                        page_kind=None,
                        route_path=None,
                        layout_type=None,
                        primary_entity=None,
                        can_generate_ui_contract=False,
                        feature_role=None,
                    )
                )
                item = item.model_copy(update={"parent_key": module_key})

            result.append(item)

        result = self._drop_invalid_parent_references(result)
        return result

    def _drop_invalid_parent_references(
        self,
        nodes: list[FlatModuleNode],
    ) -> list[FlatModuleNode]:
        keys = {item.key for item in nodes}
        result: list[FlatModuleNode] = []

        for item in nodes:
            parent_key = item.parent_key if item.parent_key in keys else ""
            result.append(item.model_copy(update={"parent_key": parent_key}))

        return result

    def _fallback_progressive_outline(
        self,
        requirement_text: str,
    ) -> list[FlatModuleNode]:
        text = requirement_text or ""
        candidates = [
            ("core_business", "核心业务管理", "承载主要业务作业和核心流程。"),
            ("operation_management", "运营管理", "承载查询、跟踪、预警和运营分析。"),
            ("master_data", "基础资料管理", "承载主数据、基础档案和参数维护。"),
            ("workflow_approval", "流程审批管理", "承载审核、确认和状态流转。"),
            ("report_analysis", "报表分析", "承载统计、看板和管理分析。"),
            ("system_admin", "系统管理", "承载权限、账号、配置和审计。"),
        ]

        if "仓储" in text or "库存" in text or "入库" in text or "出库" in text:
            candidates = [
                ("warehouse_operations", "仓储作业管理", "承载入库、出库、调拨等仓储作业。"),
                ("inventory_operations", "库存运营管理", "承载库存查询、库存预警、盘点和库存分析。"),
                ("master_data", "基础资料管理", "承载供应商、仓库、物料等基础资料。"),
                ("workflow_approval", "流程审核管理", "承载入库审核、出库审核和异常处理。"),
                ("report_analysis", "报表分析", "承载库存、作业效率和业务统计。"),
                ("system_admin", "系统管理", "承载权限、角色、配置和审计。"),
            ]

        result: list[FlatModuleNode] = []

        for index, (key, name, description) in enumerate(candidates, start=1):
            result.append(
                FlatModuleNode(
                    key=key,
                    parent_key="",
                    name=name,
                    type="MODULE",
                    description=description,
                    sort_order=index,
                    page_kind=None,
                    route_path=None,
                    layout_type=None,
                    primary_entity=None,
                    can_generate_ui_contract=False,
                    feature_role=None,
                )
            )

        return result

    def _safe_key(
        self,
        value: str,
    ) -> str:
        cleaned = (value or "").strip().lower().replace("-", "_")
        cleaned = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in cleaned)
        while "__" in cleaned:
            cleaned = cleaned.replace("__", "_")
        return cleaned.strip("_") or "module"

    def _get_requirement_text(self, request: AgentTaskRequest) -> str:
        if request.input and request.input.get("requirement_text"):
            return str(request.input["requirement_text"])

        if request.requirement_text:
            return request.requirement_text

        return ""

    def _build_user_prompt(
        self,
        request: AgentTaskRequest,
        requirement_text: str,
        modeling_mode: str,
    ) -> str:
        context_package = request.context_package or {}

        mode_instruction = self._build_modeling_mode_instruction(modeling_mode)

        return "\n".join(
            [
                "请根据以下项目需求生成企业级需求骨架。",
                "",
                "【本次建模模式】",
                modeling_mode,
                mode_instruction,
                "",
                "【统一结构规则】",
                "1. MODULE 可以嵌套 MODULE，用来表达业务域、子域、业务模块和末级需求模块。",
                "2. 如果 MODULE 仍是分组或草稿模块，它下面只能继续挂 MODULE；不要过早挂 FEATURE / PAGE。",
                "3. 如果 MODULE 已经是末级需求模块，它下面只能挂 FEATURE 功能点。",
                "4. FEATURE 表示业务功能点，不是按钮、输入框、字段、校验项或 UI 组件。",
                "5. FEATURE 下面只能挂 PAGE；PAGE 是后续交互原型的入口，内部生成 UI_CONTRACT，不向用户暴露 PAGE_SPEC。",
                "6. PAGE 下面不得再挂业务节点；初始模块树生成阶段绝不生成 UI_CONTRACT 或组件。",
                "",
                "【企业级蓝图质量要求】",
                "- 不要生成只有一层模块的简陋树。",
                "- 企业系统应包含主业务作业、运营管理、基础资料、权限/系统配置、报表分析或审计等合理分层。",
                "- 模块名称必须像真实企业项目的信息架构，而不是临时 demo 清单。",
                "- 完整蓝图模式下，至少应出现“模块分组 MODULE -> 末级需求 MODULE -> FEATURE -> PAGE”的路径。",
                "- 完整蓝图模式下，末级需求 MODULE 必须能够沉淀成用户可读的模块需求文档，内容要覆盖模块定位、业务目标、功能范围、页面方向、关键规则和后续原型生成依据。",
                "",
                "【PAGE 节点要求】",
                "每个 PAGE 节点必须提供：",
                "- page_kind：LIST_PAGE / FORM_PAGE / DETAIL_PAGE / APPROVAL_PAGE / DASHBOARD_PAGE / SETTINGS_PAGE",
                "- route_path：页面路由，例如 /warehouse/inbound/orders",
                "- layout_type：table-list / form-detail / detail-tabs / approval-workbench / dashboard",
                "- primary_entity：页面主业务对象",
                "- can_generate_ui_contract：必须为 true",
                "",
                "【输出要求】",
                "1. 输出必须严格符合 JSON Schema。",
                "2. modules 是扁平数组，通过 parent_key 表示父子关系。",
                "3. key 必须是稳定英文小写下划线。",
                "4. PAGE 节点名称必须是具体页面，不要写成抽象功能。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"task_type: {request.task_type}",
                f"modeling_mode: {modeling_mode}",
                "",
                "【用户原始需求】",
                requirement_text,
                "",
                "【上下文包】",
                json.dumps(
                    context_package,
                    ensure_ascii=False,
                    default=str,
                ),
            ]
        )

    def _normalize_flat_node(
        self,
        item: FlatModuleNode,
    ) -> FlatModuleNode:
        node_type = self._normalize_node_type(item.type)

        page_kind = item.page_kind
        route_path = item.route_path
        layout_type = item.layout_type
        primary_entity = item.primary_entity
        can_generate_ui_contract = item.can_generate_ui_contract
        feature_role = item.feature_role

        if node_type == "PAGE":
            can_generate_ui_contract = True

            if not page_kind:
                page_kind = self._infer_page_kind(item.name)

            if not layout_type:
                layout_type = self._infer_layout_type(page_kind)

            if not route_path:
                route_path = "/" + item.key.replace("_", "/")

            if not primary_entity:
                primary_entity = item.name.replace("页", "")

        else:
            can_generate_ui_contract = False

            if node_type != "FEATURE":
                feature_role = None

        return FlatModuleNode(
            key=item.key,
            parent_key=item.parent_key,
            name=item.name,
            type=node_type,
            description=item.description,
            sort_order=item.sort_order,
            page_kind=page_kind,
            route_path=route_path,
            layout_type=layout_type,
            primary_entity=primary_entity,
            can_generate_ui_contract=can_generate_ui_contract,
            feature_role=feature_role,
            requirement_context=item.requirement_context,
            function_context=item.function_context,
            page_prototype_context=item.page_prototype_context,
            module_prototype_map=item.module_prototype_map,
            traceability_map=item.traceability_map,
        )

    def _normalize_node_type(
        self,
        value: str,
    ) -> str:
        normalized = (value or "").strip().upper()

        mapping = {
            "DOMAIN_MODULE": "MODULE",
            "BUSINESS_DOMAIN": "MODULE",
            "DOMAIN": "MODULE",
            "BUSINESS_MODULE": "MODULE",
            "FEATURE_MODULE": "MODULE",
            "MODULE": "MODULE",
            "SUBMODULE": "MODULE",
            "SUB_MODULE": "MODULE",
            "PAGE_MODULE": "PAGE",
            "PAGE": "PAGE",
            "FEATURE": "FEATURE",
            "FUNCTION": "FEATURE",
            "ACTION": "FEATURE",
            "INTEGRATION_MODULE": "MODULE",
            "INFRA_MODULE": "MODULE",
        }

        return mapping.get(normalized, "MODULE")

    def _infer_page_kind(
        self,
        page_name: str,
    ) -> str:
        if "列表" in page_name or "台账" in page_name or "查询" in page_name:
            return "LIST_PAGE"

        if "创建" in page_name or "编辑" in page_name or "新增" in page_name:
            return "FORM_PAGE"

        if "详情" in page_name or "明细" in page_name:
            return "DETAIL_PAGE"

        if "审批" in page_name or "审核" in page_name:
            return "APPROVAL_PAGE"

        if "看板" in page_name or "统计" in page_name or "分析" in page_name:
            return "DASHBOARD_PAGE"

        if "设置" in page_name or "配置" in page_name:
            return "SETTINGS_PAGE"

        return "FORM_PAGE"

    def _infer_layout_type(
        self,
        page_kind: str | None,
    ) -> str:
        mapping = {
            "LIST_PAGE": "table-list",
            "FORM_PAGE": "form-detail",
            "DETAIL_PAGE": "detail-tabs",
            "APPROVAL_PAGE": "approval-workbench",
            "DASHBOARD_PAGE": "dashboard",
            "SETTINGS_PAGE": "form-detail",
        }

        return mapping.get(page_kind or "", "form-detail")


    def _enrich_hidden_prototype_contexts(
        self,
        nodes: list[FlatModuleNode],
        modeling_mode: str,
        requirement_text: str,
    ) -> list[FlatModuleNode]:
        if modeling_mode != "FULL_ENTERPRISE_BLUEPRINT":
            return nodes

        by_key = {item.key: item for item in nodes}
        children_by_parent: dict[str, list[FlatModuleNode]] = {}
        for item in nodes:
            children_by_parent.setdefault(item.parent_key or "", []).append(item)

        updates: dict[str, dict[str, Any]] = {}

        def child_nodes(node: FlatModuleNode, node_type: str | None = None) -> list[FlatModuleNode]:
            children = children_by_parent.get(node.key, [])
            if node_type is None:
                return children
            return [child for child in children if child.type == node_type]

        def page_nodes_under(node: FlatModuleNode) -> list[FlatModuleNode]:
            result: list[FlatModuleNode] = []
            stack = list(children_by_parent.get(node.key, []))
            while stack:
                current = stack.pop(0)
                if current.type == "PAGE":
                    result.append(current)
                stack.extend(children_by_parent.get(current.key, []))
            return result

        def feature_nodes_under(node: FlatModuleNode) -> list[FlatModuleNode]:
            result: list[FlatModuleNode] = []
            stack = list(children_by_parent.get(node.key, []))
            while stack:
                current = stack.pop(0)
                if current.type == "FEATURE":
                    result.append(current)
                stack.extend(children_by_parent.get(current.key, []))
            return result

        def owning_module_for(node: FlatModuleNode | None) -> FlatModuleNode | None:
            current = node
            while current and current.parent_key:
                parent = by_key.get(current.parent_key)
                if parent and parent.type == "MODULE":
                    return parent
                current = parent
            return current if current and current.type == "MODULE" else None

        for item in nodes:
            if item.type != "MODULE":
                continue

            features = feature_nodes_under(item)
            pages = page_nodes_under(item)
            if not features and not pages:
                continue

            feature_summaries = [
                {
                    "featureKey": feature.key,
                    "featureName": feature.name,
                    "summary": feature.description or f"支持{feature.name}。",
                    "pageKeys": [page.key for page in child_nodes(feature, "PAGE")],
                    "pageNames": [page.name for page in child_nodes(feature, "PAGE")],
                }
                for feature in features
            ]

            page_summaries = [
                {
                    "pageKey": page.key,
                    "pageName": page.name,
                    "route": page.route_path or "/" + page.key.replace("_", "/"),
                    "pageKind": page.page_kind or self._infer_page_kind(page.name),
                    "layoutType": page.layout_type or self._infer_layout_type(page.page_kind),
                    "primaryEntity": page.primary_entity or page.name.replace("页", ""),
                    "parentFeatureKey": page.parent_key,
                    "parentFeatureName": (by_key.get(page.parent_key).name if by_key.get(page.parent_key) else ""),
                }
                for page in pages
            ]

            module_requirement_markdown = self._build_module_requirement_markdown(
                item,
                features,
                pages,
                feature_summaries,
                page_summaries,
                requirement_text,
            )

            requirement_context = {
                "source": "FULL_ENTERPRISE_BLUEPRINT",
                "moduleKey": item.key,
                "moduleName": item.name,
                "requirementSummary": item.description or f"{item.name}负责承载本模块的核心业务能力。",
                "businessBoundary": item.description or f"围绕{item.name}组织功能点、页面和交互原型。",
                "requirementText": item.description or f"{item.name}负责承载本模块的核心业务能力。",
                "moduleRequirementMarkdown": module_requirement_markdown,
                "functionKeys": [feature.key for feature in features],
                "functionNames": [feature.name for feature in features],
                "pageKeys": [page.key for page in pages],
                "pageNames": [page.name for page in pages],
                "hiddenPrototypeContextReady": True,
            }

            module_prototype_map = self._build_module_prototype_map(item, features, pages, by_key, children_by_parent)
            traceability_map = {
                "source": "FULL_ENTERPRISE_BLUEPRINT",
                "moduleKey": item.key,
                "moduleName": item.name,
                "requirementTextDigest": (requirement_text or "")[:500],
                "featureCoverage": feature_summaries,
                "pageCoverage": page_summaries,
                "lineageHiddenFromUser": True,
            }

            updates.setdefault(item.key, {}).update(
                {
                    "requirement_context": requirement_context,
                    "module_prototype_map": module_prototype_map,
                    "traceability_map": traceability_map,
                }
            )

        for item in nodes:
            if item.type == "FEATURE":
                pages = child_nodes(item, "PAGE")
                function_context = {
                    "source": "FULL_ENTERPRISE_BLUEPRINT",
                    "featureKey": item.key,
                    "featureName": item.name,
                    "summary": item.description or f"支持{item.name}。",
                    "goal": item.description or f"完成{item.name}相关业务操作。",
                    "trigger": self._infer_feature_trigger(item.name),
                    "interactionType": self._infer_feature_interaction_type(item.name),
                    "expectedResult": self._infer_feature_expected_result(item.name),
                    "pageKeys": [page.key for page in pages],
                    "pageNames": [page.name for page in pages],
                    "hiddenFromUser": True,
                }
                updates.setdefault(item.key, {}).update({"function_context": function_context})

            if item.type == "PAGE":
                feature = by_key.get(item.parent_key)
                owner_module = owning_module_for(feature)
                sibling_pages = child_nodes(feature, "PAGE") if feature else []
                page_prototype_context = {
                    "source": "FULL_ENTERPRISE_BLUEPRINT",
                    "pageKey": item.key,
                    "pageName": item.name,
                    "pageKind": item.page_kind or self._infer_page_kind(item.name),
                    "routePath": item.route_path or "/" + item.key.replace("_", "/"),
                    "layoutType": item.layout_type or self._infer_layout_type(item.page_kind),
                    "primaryEntity": item.primary_entity or item.name.replace("页", ""),
                    "parentFeatureKey": feature.key if feature else "",
                    "parentFeatureName": feature.name if feature else "",
                    "ownerModuleKey": owner_module.key if owner_module else "",
                    "ownerModuleName": owner_module.name if owner_module else "",
                    "goal": item.description or f"为{feature.name if feature else item.name}提供可操作的页面原型。",
                    "coreFields": self._infer_core_fields(item, feature, owner_module, requirement_text),
                    "mainActions": self._infer_main_actions(item, feature, sibling_pages),
                    "siblingPageKeys": [page.key for page in sibling_pages if page.key != item.key],
                    "hiddenFromUser": True,
                }
                updates.setdefault(item.key, {}).update({"page_prototype_context": page_prototype_context})

        return [item.model_copy(update=updates.get(item.key, {})) for item in nodes]

    def _build_module_requirement_markdown(
        self,
        module: FlatModuleNode,
        features: list[FlatModuleNode],
        pages: list[FlatModuleNode],
        feature_summaries: list[dict[str, Any]],
        page_summaries: list[dict[str, Any]],
        requirement_text: str,
    ) -> str:
        feature_names = [item.name for item in features if item.name]
        page_names = [item.name for item in pages if item.name]

        core_fields: list[str] = []
        for page in pages:
            parent_feature = next((feature for feature in features if feature.key == page.parent_key), None)
            for field in self._infer_core_fields(page, parent_feature, module, requirement_text):
                if field not in core_fields:
                    core_fields.append(field)

        if not core_fields:
            core_fields = ["业务编号", "业务名称", "状态", "负责人", "创建时间", "备注"]

        summary = module.description or f"{module.name}负责承载当前业务范围内的核心操作、查询、维护和状态流转。"
        feature_text = "、".join(feature_names) if feature_names else "核心业务能力"
        field_text = "、".join(core_fields[:10])

        lines: list[str] = [
            f"# {module.name}模块需求",
            "",
            "## 1. 模块定位",
            summary,
            "",
            "## 2. 业务目标",
            f"{module.name}需要支撑{feature_text}，帮助业务人员在清晰的页面入口中完成查询、创建、查看、编辑、审核或维护等关键任务。",
            "",
            "## 3. 业务范围",
            f"本模块围绕{field_text}等核心信息组织业务内容，并与上级业务域、相关基础资料和后续开发交付保持一致。",
            "",
            "## 4. 核心功能",
            *self._markdown_list(feature_names, [f"围绕{module.name}继续补充功能点。"]),
            "",
            "## 5. 页面方向",
            *self._markdown_list(page_names, [f"在功能点下面继续补齐页面，并在页面中生成交互原型。"]),
            "",
            "## 6. 关键业务规则",
            "- 模块需求只展示用户需要确认的业务边界、功能范围和页面方向。",
            "- 字段、按钮、表格列、卡片和跳转细节会在交互原型中直接查看、选择和修改。",
            "- 后续交互原型生成会读取本模块需求、功能点、页面方向和隐藏原型上下文，避免页面内容脱离业务需求。",
            "- 如果用户修改模块需求，功能点、页面和交互原型应以最新模块需求为准继续同步。",
            "",
            "## 7. 后续交互原型生成依据",
            f"页面层只暴露交互原型入口。系统会基于本模块需求、功能点和页面方向准备页面上下文、页面跳转关系和可复用的前端代码蓝图，确保原型既能给用户确认，也能被后续开发 Agent 复用。",
        ]

        return "\n".join(lines)

    def _markdown_list(
        self,
        items: list[str],
        fallback: list[str],
    ) -> list[str]:
        source = items if items else fallback
        return [f"- {item}" for item in source]


    def _build_module_prototype_map(
        self,
        module: FlatModuleNode,
        features: list[FlatModuleNode],
        pages: list[FlatModuleNode],
        by_key: dict[str, FlatModuleNode],
        children_by_parent: dict[str, list[FlatModuleNode]],
    ) -> dict[str, Any]:
        # Build the formal MODULE_APP_CONTRACT above PAGE_UI_CONTRACT.
        # PAGE_UI_CONTRACT remains a single-page prototype without app chrome.
        # MODULE_APP_CONTRACT owns the app shell, navigation placement, action edges,
        # embedding policy, and Development Agent routing hints.

        def text_of(*values: object) -> str:
            return " ".join(str(value or "") for value in values)

        def page_route(page: FlatModuleNode) -> str:
            return page.route_path or "/" + page.key.replace("_", "/")

        def page_kind(page: FlatModuleNode) -> str:
            return page.page_kind or self._infer_page_kind(page.name)

        def page_surface_type(page: FlatModuleNode) -> str:
            text = text_of(page.name, page.key, page_kind(page)).upper()
            if any(word in text for word in ["LIST", "QUERY", "DASHBOARD", "HOME", "列表", "查询", "搜索", "看板", "首页", "工作台"]):
                return "MAIN_NAV_PAGE"
            if any(word in text for word in ["CREATE", "NEW", "ADD", "FORM", "DETAIL", "EDIT", "APPROVAL", "AUDIT", "新建", "新增", "创建", "详情", "明细", "编辑", "修改", "审核", "审批", "提交"]):
                return "ACTION_ROUTE_PAGE"
            return "SECONDARY_ROUTE_PAGE"

        def navigation_visible(page: FlatModuleNode, index: int) -> bool:
            surface_type = page_surface_type(page)
            if surface_type == "MAIN_NAV_PAGE":
                return True
            return index == 0 and len(pages) == 1

        def infer_shell_layout(module_text: str, nav_count: int, edge_count: int) -> str:
            lower_text = module_text.lower()
            if any(word in module_text for word in ["电商", "商城", "店铺", "商品浏览", "购物", "首页", "门户", "官网", "内容", "营销", "C端", "小程序"]):
                return "TOP_NAV"
            if nav_count <= 0 and edge_count > 0:
                return "ACTION_HUB"
            if any(word in module_text for word in ["后台", "管理", "审批", "审核", "仓储", "库存", "入库", "出库", "订单管理", "运营", "系统配置", "权限"]):
                return "SIDEBAR"
            if any(word in lower_text for word in ["admin", "dashboard", "management", "console", "workflow"]):
                return "SIDEBAR"
            if nav_count <= 1 and edge_count >= 3:
                return "ACTION_HUB"
            return "SIDEBAR"

        def navigation_placement(layout: str) -> str:
            return {
                "SIDEBAR": "left",
                "TOP_NAV": "top",
                "ACTION_HUB": "none",
                "NO_NAV": "none",
            }.get(layout, "left")

        sorted_pages = sorted(pages, key=lambda item: item.sort_order)
        page_items: list[dict[str, Any]] = []

        for index, page in enumerate(sorted_pages):
            parent_feature = by_key.get(page.parent_key)
            surface_type = page_surface_type(page)
            visible = navigation_visible(page, index)
            page_items.append(
                {
                    "pageKey": page.key,
                    "pageName": page.name,
                    "route": page_route(page),
                    "routePath": page_route(page),
                    "pageKind": page_kind(page),
                    "layoutType": page.layout_type or self._infer_layout_type(page.page_kind),
                    "surfaceType": surface_type,
                    "displayRole": surface_type,
                    "navigationVisible": visible,
                    "entryMode": "NAVIGATION" if visible else "ACTION_ROUTE",
                    "previewMode": "APP_SHELL_ROUTE",
                    "functionKey": parent_feature.key if parent_feature else "",
                    "functionName": parent_feature.name if parent_feature else "",
                    "primaryEntity": page.primary_entity or page.name.replace("页", ""),
                    "embeddingPolicy": {
                        "sourceArtifactType": "UI_CONTRACT",
                        "sourceNodeType": "PAGE",
                        "fallbackWhenMissing": "PAGE_CONTRACT_PLACEHOLDER",
                        "autoRefreshWhenGenerated": True,
                    },
                    "mainActions": self._infer_main_actions(page, parent_feature, sorted_pages),
                }
            )

        default_page = next((item for item in page_items if item.get("navigationVisible")), page_items[0] if page_items else None)
        navigation_edges: list[dict[str, Any]] = []

        for source in sorted_pages:
            source_text = source.name + " " + source.key
            for target in sorted_pages:
                if target.key == source.key:
                    continue
                action_id = self._infer_navigation_action_id(source_text, target.name + " " + target.key)
                if not action_id:
                    continue
                target_item = next((item for item in page_items if item["pageKey"] == target.key), None)
                navigation_edges.append(
                    {
                        "sourcePageKey": source.key,
                        "sourcePageName": source.name,
                        "sourceRoute": page_route(source),
                        "actionId": action_id,
                        "actionLabel": self._navigation_label(action_id, target.name),
                        "label": self._navigation_label(action_id, target.name),
                        "interactionType": "ROUTE_NAVIGATION",
                        "targetSurfaceType": target_item.get("surfaceType") if target_item else page_surface_type(target),
                        "targetPageKey": target.key,
                        "targetPageName": target.name,
                        "targetRoute": page_route(target),
                        "navigationType": "IN_MODULE",
                        "navigationStatus": "RESOLVED_PAGE_ONLY",
                        "renderAs": "ACTION_BUTTON",
                    }
                )

        app_navigation = [
            {
                "label": item["pageName"],
                "pageKey": item["pageKey"],
                "route": item["routePath"],
                "visible": bool(item["navigationVisible"]),
                "surfaceType": item["surfaceType"],
            }
            for item in page_items
            if item.get("navigationVisible")
        ]

        module_text = text_of(module.name, module.key, module.description, " ".join(page.name for page in sorted_pages))
        shell_layout = infer_shell_layout(module_text, len(app_navigation), len(navigation_edges))
        if shell_layout in {"ACTION_HUB", "NO_NAV"}:
            for item in app_navigation:
                item["visible"] = False
            app_navigation = []

        return {
            "schemaVersion": "1.1",
            "artifactType": "MODULE_APP_CONTRACT",
            "contractType": "MODULE_APP_CONTRACT",
            "source": "FULL_ENTERPRISE_BLUEPRINT",
            "moduleKey": module.key,
            "moduleName": module.name,
            "appShell": {
                "layout": shell_layout,
                "navigationPlacement": navigation_placement(shell_layout),
                "shellKind": "ADMIN_SIDEBAR" if shell_layout == "SIDEBAR" else ("WEBSITE_TOP_NAV" if shell_layout == "TOP_NAV" else "ACTION_FLOW"),
                "title": module.name,
                "defaultPageKey": default_page.get("pageKey") if default_page else "",
                "defaultRoute": default_page.get("routePath") if default_page else "",
                "navigation": app_navigation,
                "hiddenNavigationCount": len([item for item in page_items if not item.get("navigationVisible")]),
                "style": {
                    "navTheme": "dark" if shell_layout == "SIDEBAR" else "light",
                    "density": "business",
                },
            },
            "pages": page_items,
            "navigationEdges": navigation_edges,
            "pageEmbeddingPolicy": {
                "contractType": "PAGE_UI_CONTRACT",
                "sourceNodeType": "PAGE",
                "whenMissing": "SHOW_PLACEHOLDER_AND_GENERATE_PAGE_PROTOTYPE_ACTION",
                "refresh": "AUTO_ON_PAGE_UI_CONTRACT_AVAILABLE_OR_MANUAL_REFRESH",
            },
            "developmentHints": [
                "PAGE_UI_CONTRACT keeps single-page prototype without app navigation.",
                "MODULE_APP_CONTRACT owns app shell layout, navigation placement, and action-driven page transitions.",
                "Development Agent must consume MODULE_APP_CONTRACT for App.vue/router/sidebar/topbar/action links.",
                "Development Agent must consume PAGE_UI_CONTRACT for individual page component implementation.",
                "Pages without PAGE_UI_CONTRACT remain visible in the module app as placeholders until their PAGE prototype is generated.",
            ],
            "hiddenFromUser": True,
        }


    def _infer_feature_trigger(self, feature_name: str) -> str:
        text = feature_name or ""
        if any(word in text for word in ["查询", "搜索", "筛选", "列表"]):
            return "用户输入筛选条件或进入页面加载列表数据时触发。"
        if any(word in text for word in ["新建", "创建", "新增"]):
            return "用户点击新增入口并填写业务表单时触发。"
        if any(word in text for word in ["详情", "明细", "查看"]):
            return "用户从列表或业务入口点击查看时触发。"
        if any(word in text for word in ["编辑", "修改", "维护"]):
            return "用户在详情或列表中点击编辑入口时触发。"
        if any(word in text for word in ["审核", "审批", "提交"]):
            return "用户发起审核、提交或处理待办任务时触发。"
        return "用户执行该功能点对应的主业务操作时触发。"

    def _infer_feature_interaction_type(self, feature_name: str) -> str:
        text = feature_name or ""
        if any(word in text for word in ["查询", "搜索", "筛选", "列表"]):
            return "QUERY_REFRESH"
        if any(word in text for word in ["新建", "创建", "新增", "编辑", "修改", "维护", "提交"]):
            return "FORM_SUBMIT"
        if any(word in text for word in ["详情", "明细", "查看"]):
            return "OPEN_DETAIL"
        if any(word in text for word in ["审核", "审批"]):
            return "APPROVAL_ACTION"
        return "PAGE_INTERACTION"

    def _infer_feature_expected_result(self, feature_name: str) -> str:
        text = feature_name or ""
        if any(word in text for word in ["查询", "搜索", "筛选", "列表"]):
            return "页面刷新数据区域，展示查询结果、空状态或错误状态。"
        if any(word in text for word in ["新建", "创建", "新增"]):
            return "系统保存新业务记录并给出成功反馈，必要时返回列表或进入详情。"
        if any(word in text for word in ["编辑", "修改", "维护"]):
            return "系统保存变更并同步更新页面状态。"
        if any(word in text for word in ["审核", "审批", "提交"]):
            return "系统完成状态流转，展示审核结果、下一步处理人或业务反馈。"
        return "页面完成该功能点对应的交互反馈、状态变化或数据更新。"

    def _infer_core_fields(
        self,
        page: FlatModuleNode,
        feature: FlatModuleNode | None,
        owner_module: FlatModuleNode | None,
        requirement_text: str,
    ) -> list[str]:
        text = " ".join(
            [
                page.name or "",
                page.description or "",
                feature.name if feature else "",
                feature.description if feature else "",
                owner_module.name if owner_module else "",
                owner_module.description if owner_module else "",
                requirement_text or "",
            ]
        )

        if "入库" in text:
            fields = ["入库单号", "供应商", "仓库", "入库状态", "经办人", "创建时间", "入库数量", "备注"]
        elif "出库" in text:
            fields = ["出库单号", "客户/领用方", "仓库", "出库状态", "经办人", "创建时间", "出库数量", "备注"]
        elif "库存" in text:
            fields = ["物料编码", "物料名称", "仓库", "库存数量", "可用数量", "预警状态", "更新时间"]
        else:
            fields = ["业务编号", "业务名称", "状态", "负责人", "创建时间", "备注"]

        return fields

    def _infer_main_actions(
        self,
        page: FlatModuleNode,
        feature: FlatModuleNode | None,
        sibling_pages: list[FlatModuleNode],
    ) -> list[str]:
        text = f"{page.name} {feature.name if feature else ''}"
        actions: list[str] = []
        if any(word in text for word in ["列表", "查询", "搜索"]):
            actions.extend(["查询", "重置", "查看详情"])
            if any(any(word in sibling.name for word in ["新建", "新增", "创建"]) for sibling in sibling_pages):
                actions.append("新建")
        elif any(word in text for word in ["新建", "新增", "创建"]):
            actions.extend(["保存", "提交", "取消"])
        elif any(word in text for word in ["详情", "明细", "查看"]):
            actions.extend(["返回", "编辑", "提交审核"])
        elif any(word in text for word in ["编辑", "修改"]):
            actions.extend(["保存修改", "取消"])
        elif any(word in text for word in ["审核", "审批"]):
            actions.extend(["通过", "驳回", "查看详情"])
        else:
            actions.extend(["确认", "取消", "刷新"])
        return list(dict.fromkeys(actions))

    def _infer_navigation_action_id(self, source_text: str, target_text: str) -> str:
        source = source_text or ""
        target = target_text or ""
        if any(word in source for word in ["列表", "查询", "搜索"]) and any(word in target for word in ["新建", "新增", "创建"]):
            return "create_record"
        if any(word in source for word in ["列表", "查询", "搜索"]) and any(word in target for word in ["详情", "明细", "查看"]):
            return "view_detail"
        if "详情" in source and any(word in target for word in ["编辑", "修改"]):
            return "edit_record"
        if "详情" in source and any(word in target for word in ["审核", "审批"]):
            return "submit_approval"
        if any(word in source for word in ["新建", "新增", "创建", "编辑", "修改", "审核", "审批"]) and any(word in target for word in ["列表", "查询"]):
            return "back_to_list"
        return ""

    def _navigation_label(self, action_id: str, target_name: str) -> str:
        return {
            "create_record": "新建",
            "view_detail": "查看详情",
            "edit_record": "编辑",
            "submit_approval": "提交审核",
            "back_to_list": "返回列表",
        }.get(action_id, f"进入{target_name}")

    def _build_tree(
        self,
        flat_modules: list[FlatModuleNode],
    ) -> list[ModuleNode]:
        node_map: dict[str, ModuleNode] = {}

        sorted_flat_modules = sorted(
            flat_modules,
            key=lambda item: (item.parent_key or "", item.sort_order),
        )

        for item in sorted_flat_modules:
            if item.key not in node_map:
                node_map[item.key] = ModuleNode(
                    key=item.key,
                    name=item.name,
                    type=item.type,
                    description=item.description,
                    pageKind=item.page_kind,
                    routePath=item.route_path,
                    layoutType=item.layout_type,
                    primaryEntity=item.primary_entity,
                    canGenerateUiContract=item.can_generate_ui_contract,
                    featureRole=item.feature_role,
                    requirementContext=item.requirement_context,
                    functionContext=item.function_context,
                    pagePrototypeContext=item.page_prototype_context,
                    modulePrototypeMap=item.module_prototype_map,
                    traceabilityMap=item.traceability_map,
                    children=[],
                )

        roots: list[ModuleNode] = []

        for item in sorted_flat_modules:
            node = node_map[item.key]
            parent_key = item.parent_key.strip()

            if not parent_key:
                roots.append(node)
                continue

            parent = node_map.get(parent_key)

            if parent is None:
                roots.append(node)
                continue

            parent.children.append(node)

        return roots

    def _build_markdown(
        self,
        modules: list[ModuleNode],
        requirement_text: str,
    ) -> str:
        lines = [
            "# 项目需求骨架",
            "",
            "## 原始需求摘要",
            requirement_text or "未提供需求文本。",
            "",
            "## 拆分原则",
            "- 初次生成只输出需求骨架；UI_CONTRACT 只在用户选中 PAGE 后单独生成。",
            "- 渐进式模式只生成 MODULE 层级；完整蓝图模式使用 MODULE → FEATURE → PAGE。",
            "- 后续开发 Agent 仍基于 PAGE 级 UI_CONTRACT 生成真实页面。",
            "",
            "## 树结构",
        ]

        def walk(node: ModuleNode, depth: int) -> None:
            prefix = "  " * depth
            label = f"{node.name}（{node.type}）"

            if node.type == "PAGE":
                label += f" · {node.pageKind or '-'} · {node.routePath or '-'}"

            lines.append(f"{prefix}- {label}")

            if node.description:
                lines.append(f"{prefix}  - 说明：{node.description}")

            for child in node.children:
                walk(child, depth + 1)

        for module in modules:
            walk(module, 0)

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

    def _load_prompt(self, filename: str) -> str:
        prompt_path = (
            Path(__file__).resolve().parents[2]
            / "prompts"
            / "requirement"
            / filename
        )

        return prompt_path.read_text(encoding="utf-8")

# A9.10.14.4.3.15.4.10.4.1_MODULE_TREE_GENERATION_PLANNER
# Internal generation planner: keeps the user-facing choice simple while adding
# evidence-aware recommendations for skeleton / full tree / organize-first modes.
try:
    from app.agents.requirement.module_tree_generation_planner import ModuleTreeGenerationPlanner
except Exception:  # pragma: no cover - keeps older runtime safe during rollout.
    ModuleTreeGenerationPlanner = None  # type: ignore[assignment]

_A9_10_4_1_ORIGINAL_MODULE_TREE_RUN = ModuleTreeAgent.run
_A9_10_4_1_ORIGINAL_BUILD_USER_PROMPT = ModuleTreeAgent._build_user_prompt
_A9_10_4_1_ORIGINAL_FALLBACK_SYSTEM_PROMPT = ModuleTreeAgent._fallback_system_prompt


def _a9_10_4_1_planner_for(agent: ModuleTreeAgent):
    planner = getattr(agent, "_a9_module_tree_generation_planner", None)
    if planner is None and ModuleTreeGenerationPlanner is not None:
        planner = ModuleTreeGenerationPlanner()
        setattr(agent, "_a9_module_tree_generation_planner", planner)
    return planner


async def _a9_10_4_1_module_tree_run(self: ModuleTreeAgent, request: AgentTaskRequest) -> ModuleTreeArtifact:
    planner = _a9_10_4_1_planner_for(self)
    if planner is None:
        return await _A9_10_4_1_ORIGINAL_MODULE_TREE_RUN(self, request)

    requirement_text = self._get_requirement_text(request)
    generation_plan = planner.build_plan(request, requirement_text)
    planned_request = planner.apply_plan_to_request(request, generation_plan)

    artifact = await _A9_10_4_1_ORIGINAL_MODULE_TREE_RUN(self, planned_request)

    try:
        summary = str(getattr(artifact, "analysis_summary", "") or "").strip()
        plan_prefix = (
            "模块树生成计划："
            f"{generation_plan.get('recommendedMode')} / "
            f"成熟度 {generation_plan.get('maturityLevel')} / "
            f"置信度 {generation_plan.get('confidence')}。"
        )
        next_summary = plan_prefix + ("\n" + summary if summary else "")
        return artifact.model_copy(
            update={
                "generation_plan": generation_plan,
                "analysis_summary": next_summary,
            }
        )
    except Exception:
        try:
            setattr(artifact, "generation_plan", generation_plan)
        except Exception:
            pass
        return artifact


def _a9_10_4_1_build_user_prompt(
    self: ModuleTreeAgent,
    request: AgentTaskRequest,
    requirement_text: str,
    modeling_mode: str,
) -> str:
    prompt = _A9_10_4_1_ORIGINAL_BUILD_USER_PROMPT(self, request, requirement_text, modeling_mode)
    planner = _a9_10_4_1_planner_for(self)
    if planner is None:
        return prompt

    context_package = request.context_package or {}
    generation_plan = context_package.get("module_tree_generation_plan") or context_package.get("moduleTreeGenerationPlan")
    if not isinstance(generation_plan, dict):
        return prompt

    plan_section = planner.prompt_section(generation_plan)
    marker = "【用户原始需求】"
    if marker in prompt:
        return prompt.replace(marker, plan_section + "\n\n" + marker, 1)
    return prompt + "\n\n" + plan_section


def _a9_10_4_1_fallback_system_prompt(self: ModuleTreeAgent) -> str:
    base = _A9_10_4_1_ORIGINAL_FALLBACK_SYSTEM_PROMPT(self)
    extra = (
        "支持三种用户侧生成方式：模块骨架、完整模块树、先整理资料再生成。"
        "先整理资料再生成时，本轮只输出可信 MODULE 候选骨架，不输出 FEATURE/PAGE；"
        "完整模块树必须保持 MODULE -> FEATURE -> PAGE；模块骨架只输出 MODULE。"
    )
    if extra in base:
        return base
    return base + extra


ModuleTreeAgent.run = _a9_10_4_1_module_tree_run  # type: ignore[assignment]
ModuleTreeAgent._build_user_prompt = _a9_10_4_1_build_user_prompt  # type: ignore[assignment]
ModuleTreeAgent._fallback_system_prompt = _a9_10_4_1_fallback_system_prompt  # type: ignore[assignment]

