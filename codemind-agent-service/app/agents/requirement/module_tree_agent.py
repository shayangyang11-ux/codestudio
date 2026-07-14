from __future__ import annotations

import json
from pathlib import Path

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


class ModuleTreeAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> ModuleTreeArtifact:
        requirement_text = self._get_requirement_text(request)

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._fallback_system_prompt(),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(
                request,
                requirement_text,
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

        tree_modules = self._build_tree(normalized_flat_modules)

        markdown = parsed.markdown
        if not markdown.strip():
            markdown = self._build_markdown(tree_modules, requirement_text)

        return ModuleTreeArtifact(
            modules=tree_modules,
            flat_modules=normalized_flat_modules,
            markdown=markdown,
            analysis_summary=parsed.analysis_summary,
        )

    def _fallback_system_prompt(self) -> str:
        return (
            "你是 CodeMind Studio 的企业级需求架构 Agent。"
            "你的任务是把原始业务需求拆解成可落地的需求资产树。"
            "模块树必须精细化到 PAGE 页面节点，并在 PAGE 节点下挂 FEATURE 功能点。"
            "交互原型和 UI_CONTRACT 只能基于 PAGE 节点生成，不能基于 MODULE 或 FEATURE 节点生成。"
        )

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
    ) -> str:
        context_package = request.context_package or {}

        return "\n".join(
            [
                "请根据以下项目需求生成企业级页面级模块树。",
                "",
                "【核心拆分原则】",
                "1. 不要只按业务功能粗分模块，必须拆到最小页面。",
                "2. 树结构必须是：DOMAIN → MODULE → SUBMODULE → PAGE → FEATURE。",
                "3. PAGE 是可生成 UI_CONTRACT 和 Mock Preview 的最小单位。",
                "4. FEATURE 是页面内部的按钮、操作、校验、跳转、状态流转或局部交互，不单独生成完整页面。",
                "5. 后续开发 Agent 将基于 PAGE 级 UI_CONTRACT 生成真实页面，因此 PAGE 节点必须对应真实系统中的一个页面。",
                "",
                "【节点类型定义】",
                "- DOMAIN：业务域，例如生产管理、订单管理、权限管理。",
                "- MODULE：业务模块，例如加工任务管理、订单管理、用户管理。",
                "- SUBMODULE：业务场景或子模块，例如加工任务执行、订单履约、用户权限配置。",
                "- PAGE：最小页面，例如加工任务单列表页、加工任务创建/编辑页、加工任务详情页、加工任务审批页。",
                "- FEATURE：页面内功能点，例如查询、筛选、保存草稿、提交、审批通过、驳回、导出。",
                "",
                "【PAGE 节点要求】",
                "每个 PAGE 节点必须提供：",
                "- page_kind：LIST_PAGE / FORM_PAGE / DETAIL_PAGE / APPROVAL_PAGE / DASHBOARD_PAGE / SETTINGS_PAGE",
                "- route_path：页面路由，例如 /production/tasks/create",
                "- layout_type：table-list / form-detail / detail-tabs / approval-workbench / dashboard",
                "- primary_entity：页面主业务对象",
                "- can_generate_ui_contract：必须为 true",
                "",
                "【FEATURE 节点要求】",
                "每个 FEATURE 必须挂在 PAGE 节点下面，不能直接挂在 MODULE 下面。",
                "FEATURE 节点的 can_generate_ui_contract 必须为 false。",
                "",
                "【输出要求】",
                "1. 输出必须严格符合 JSON Schema。",
                "2. modules 是扁平数组，通过 parent_key 表示父子关系。",
                "3. key 必须是稳定英文小写下划线。",
                "4. 页面节点名称必须是具体页面，不要写成抽象功能。",
                "",
                "【任务信息】",
                f"task_id: {request.task_id}",
                f"project_id: {request.project_id}",
                f"project_code: {request.project_code}",
                f"task_type: {request.task_type}",
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
        )

    def _normalize_node_type(
        self,
        value: str,
    ) -> str:
        normalized = (value or "").strip().upper()

        mapping = {
            "DOMAIN_MODULE": "DOMAIN",
            "BUSINESS_DOMAIN": "DOMAIN",
            "DOMAIN": "DOMAIN",
            "BUSINESS_MODULE": "MODULE",
            "FEATURE_MODULE": "MODULE",
            "MODULE": "MODULE",
            "SUBMODULE": "SUBMODULE",
            "SUB_MODULE": "SUBMODULE",
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
            "# 项目页面级模块树",
            "",
            "## 原始需求摘要",
            requirement_text or "未提供需求文本。",
            "",
            "## 拆分原则",
            "- UI_CONTRACT 只基于 PAGE 节点生成。",
            "- FEATURE 节点只作为 PAGE 内部功能点，不单独生成完整页面。",
            "- 后续开发 Agent 必须基于 PAGE 级 UI_CONTRACT 生成真实页面。",
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