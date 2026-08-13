from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from app.schemas.development_codegen import (
    DevelopmentCodeFilePayload,
    DevelopmentNodejsCodegenRequest,
    DevelopmentNodejsCodegenResponse,
)
from app.services.development_ui_contract_vue_codegen import UiContractVueCodegenService

from app.services.development_nodejs_inbound_page_realizer import (
    has_inbound_order_resource,
    render_inbound_backend_routes_index,
    render_inbound_order_page,
    render_inbound_seed_data_js,
    render_inbound_smoke_spec,
)

PATCH_TOKEN = "A9.10.14.4.3.15.4.8_PROTOTYPE_REAL_FULLSTACK"


class NodejsFullstackCodegenOrchestrator:
    # A9.10.13.4.3 RUNTIME_FIDELITY_SYNC
    """
    A9.10.7 Development Build Context -> Node.js fullstack orchestrator.

    The user-facing flow stays one-click. Internally this service consumes the
    Development Build Context V1 prepared by Java, reuses the confirmed
    UI_CONTRACT Vue generator for page fidelity, and adds the missing runnable
    Node.js backend, data store, seed data, root scripts, and smoke tests.
    """

    def __init__(self, ui_contract_vue_codegen: UiContractVueCodegenService | None = None) -> None:
        self.ui_contract_vue_codegen = ui_contract_vue_codegen or UiContractVueCodegenService()

    def generate(
        self,
        request: DevelopmentNodejsCodegenRequest,
        warnings: list[str] | None = None,
    ) -> DevelopmentNodejsCodegenResponse | None:
        local_warnings = warnings if warnings is not None else []
        build_context = self._extract_build_context(request)
        if not build_context:
            return None

        if str(request.target_stack or "").upper() not in {"", "NODEJS_FULLSTACK"}:
            return None

        generation_root = self._normalize_path(request.generation_root)
        module_app_contract = self._extract_module_app_contract(build_context)

        try:
            contracts = self.ui_contract_vue_codegen._extract_confirmed_contracts(request.handoff_context)
        except Exception as exc:
            contracts = []
            local_warnings.append(f"A9.10.11.6.2_UI_CONTRACT_EXTRACTION_SKIPPED={type(exc).__name__}")

        missing_ui_contract_pages = self._strict_missing_ui_contract_pages(
            build_context=build_context,
            contracts=contracts,
            module_app_contract=module_app_contract,
        )
        if missing_ui_contract_pages:
            return self._strict_codegen_failed_response(
                build_context=build_context,
                module_app_contract=module_app_contract,
                missing_pages=missing_ui_contract_pages,
                warnings=local_warnings,
            )

        ui_files: list[DevelopmentCodeFilePayload] = []
        resources = self._resources_from_context_and_vue_files(
            build_context=build_context,
            contracts=contracts,
            vue_files=ui_files,
            module_app_contract=module_app_contract,
        )
        if not resources:
            return self._strict_codegen_failed_response(
                build_context=build_context,
                module_app_contract=module_app_contract,
                missing_pages=["当前开发范围未能从已确认 PAGE_UI_CONTRACT 解析出可生成页面"],
                warnings=local_warnings,
            )

        scaffold_files = self._scaffold_files(
            generation_root=generation_root,
            build_context=build_context,
            resources=resources,
            module_app_contract=module_app_contract,
        )
        files = self._dedupe_files(scaffold_files)

        app_shell = self._module_app_shell(module_app_contract)
        module_pages = self._module_app_pages(module_app_contract)
        module_edges = self._module_app_edges(module_app_contract)

        local_warnings.extend(
            [
                "A9.10.7_NODEJS_FULLSTACK_ORCHESTRATOR_ENABLED",
                "A9.10.7_DEVELOPMENT_BUILD_CONTEXT_CONSUMED",
                f"A9.10.7_RESOURCE_COUNT={len(resources)}",
                "A9.10.7_FRONTEND_CALLS_NODEJS_BACKEND_API",
                "A9.10.7_BACKEND_LOCAL_DATA_STORE_ENABLED",
                "A9.10.11.4_BACKEND_FRONTEND_RUNTIME_CONTRACT_ALIGNMENT",
                "A9.10.11.6.2_CODEGEN_CLOSURE_SINGLE_CANONICAL_FRONTEND_TREE",
                "A9.10.13.3_MODULE_PROTOTYPE_SNAPSHOT_CONSUMED_BY_DEVELOPMENT_AGENT",
                "A9.10.11.6.2_GENERATED_VUE_TEMPLATES_BUILD_SAFE",
                "A9.10.11.6.2_BACKEND_ROUTE_ALIASES_ENABLED",
                "A9.10.13.1_MODULE_APP_CONTRACT_CONSUMED_BY_DEVELOPMENT_AGENT",
                "A9.10.14.4.3.15.4.8.1_REAL_FULLSTACK_BINDING_MAP",
                "A9.10.14.4.3.15.4.11_GENERATED_APP_VISUAL_SYSTEM_INHERITANCE",
                "A9.10.14.4.3.15.4.8.5_GENERATED_MANIFEST_TRACE_CLOSURE",
                "A9.10.14.4.3.15.4.8.4_RUNTIME_FIDELITY_GATE_CLOSURE",
                "A9.10.14.4.3.15.4.8.4_SHARED_UI_RUNTIME_REQUIRED",
                "A9.10.14.4.3.15.4.8.4_GENERIC_GENERATED_RESOURCES_PIPELINE_ENFORCED",
                f"A9.10.13.1_MODULE_APP_SHELL_LAYOUT={app_shell.get('layout') or 'SIDEBAR'}",
                f"A9.10.13.1_MODULE_APP_PAGE_COUNT={len(module_pages)}",
                f"A9.10.13.1_MODULE_APP_EDGE_COUNT={len(module_edges)}",
            ]
        )

        return DevelopmentNodejsCodegenResponse(
            status="AI_GENERATED",
            generator_mode="A9_10_13_3_NODEJS_FULLSTACK_MODULE_PROTOTYPE_SNAPSHOT",
            provider="deterministic",
            model="nodejs-fullstack-orchestrator-v1",
            summary=(
                "已基于 Development Build Context V1 生成 Node.js 全栈项目代码："
                "Vue 页面、前端 API 调用、Express 后端、业务路由、服务层、仓储、本地数据、seed data 和 smoke test。"
                "本版本正式消费 MODULE_PROTOTYPE_SNAPSHOT：App.vue、router、导航壳、按钮跳转和已确认页面由模块汇总开发基线驱动；未纳入页面仅生成占位提示。"
                "15.4.8.1 起生成 fullstackBinding：原型 action / field / route 会落到真实 API、payload、localStore 和 smoke test。"
                "15.4.11 起生成项目继承 UI_CONTRACT 视觉设计系统：design token、行业视觉预设、组件视觉契约和 runtime 样式统一生效。"
            ),
            warnings=self._dedupe(local_warnings),
            files=files,
            usage={
                "provider": "deterministic",
                "model": "nodejs-fullstack-orchestrator-v1",
                "success": True,
                "resourceCount": len(resources),
                "fileCount": len(files),
                "frontendFileCount": len([item for item in files if item.file_type == "FRONTEND"]),
                "backendFileCount": len([item for item in files if item.file_type == "BACKEND"]),
                "moduleAppContractConsumed": True,
                "moduleAppShellLayout": app_shell.get("layout") or "SIDEBAR",
            },
            raw={
                "schema": "a9_10_13_3_development_codegen_module_prototype_snapshot",
                "resourceSegments": [item.get("apiSegment") for item in resources],
                "developmentBuildContextVersion": build_context.get("contractVersion"),
                "canonicalFrontendRoot": "frontend/src/modules/generated/pages",
                "uiContractMaterializedFilesDisabled": True,
                "runtimeFidelityPolicy": {
                    "source": "MODULE_PROTOTYPE_SNAPSHOT",
                    "pageRenderer": "copied Requirement UI_CONTRACT runtime",
                    "rule": "reuse confirmed PAGE_UI_CONTRACT runtime; replace only mock data/actions with API/localStore bindings",
                    "allowLayoutReinterpretation": False,
                },
                "moduleAppContractConsumed": True,
                "generatedManifestTraceEnabled": True,
                "generatedTracePageCount": len(resources),
                "moduleAppShell": app_shell,
                "moduleAppPageCount": len(module_pages),
                "moduleAppEdgeCount": len(module_edges),
            },
        )

    def _extract_build_context(
        self,
        request: DevelopmentNodejsCodegenRequest,
    ) -> dict[str, Any]:
        direct = getattr(request, "development_build_context", None)
        if isinstance(direct, dict) and direct:
            return direct

        handoff_context = request.handoff_context if isinstance(request.handoff_context, dict) else {}
        for key in ("developmentBuildContext", "development_build_context", "buildContext", "build_context"):
            value = handoff_context.get(key)
            if isinstance(value, dict) and value:
                return value

        return {}


    # A9.10.13.1 MODULE_APP_CONTRACT helpers
    def _extract_module_app_contract(self, build_context: dict[str, Any]) -> dict[str, Any]:
        direct = self._find_module_app_contract_payload(build_context)
        if direct:
            return self._normalize_module_app_contract(direct, build_context)
        return self._module_app_contract_from_build_context_pages(build_context)

    def _find_module_app_contract_payload(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            return {}

        for key in (
            "moduleAppContract",
            "module_app_contract",
            "MODULE_APP_CONTRACT",
            "modulePrototypeMap",
            "module_prototype_map",
        ):
            value = payload.get(key)
            if isinstance(value, dict) and value:
                return value

        artifact_type = self._text(payload.get("artifactType") or payload.get("contractType")).upper()
        if artifact_type == "MODULE_APP_CONTRACT":
            return payload

        content = payload.get("content")
        if isinstance(content, dict):
            nested = self._find_module_app_contract_payload(content)
            if nested:
                return nested

        markdown = self._text(payload.get("contentMarkdown") or payload.get("markdown"))
        nested_markdown = self._parse_json_object_from_text(markdown)
        if nested_markdown:
            nested = self._find_module_app_contract_payload(nested_markdown)
            if nested:
                return nested

        requirements = payload.get("requirements")
        if isinstance(requirements, dict):
            for bucket in ("artifacts", "productRequirements", "domainModel", "apiBehavior", "acceptance"):
                for item in self._as_list(requirements.get(bucket)):
                    if isinstance(item, dict):
                        nested = self._find_module_app_contract_payload(item)
                        if nested:
                            return nested

        for key in ("artifacts", "assets", "items"):
            for item in self._as_list(payload.get(key)):
                if isinstance(item, dict):
                    nested = self._find_module_app_contract_payload(item)
                    if nested:
                        return nested

        return {}

    def _parse_json_object_from_text(self, value: str) -> dict[str, Any]:
        text = self._text(value)
        if not text:
            return {}

        candidates = [text]
        fenced = re.findall(r"```(?:json)?\s*([\s\S]*?)```", text, flags=re.IGNORECASE)
        candidates.extend(fenced)

        first = text.find("{")
        last = text.rfind("}")
        if 0 <= first < last:
            candidates.append(text[first:last + 1])

        for candidate in candidates:
            candidate = candidate.strip()
            if not candidate.startswith("{"):
                continue
            try:
                parsed = json.loads(candidate)
            except Exception:
                continue
            if isinstance(parsed, dict):
                return parsed
        return {}

    def _normalize_module_app_contract(
        self,
        raw: dict[str, Any],
        build_context: dict[str, Any],
    ) -> dict[str, Any]:
        pages_payload = raw.get("pages") or raw.get("pageSurfaces") or raw.get("surfaces") or []
        pages: list[dict[str, Any]] = []
        for index, item in enumerate(self._as_list(pages_payload)):
            if not isinstance(item, dict):
                continue
            page = dict(item)
            page_key = self._text(page.get("pageKey") or page.get("key") or page.get("nodeKey") or f"page_{index + 1}")
            page_name = self._text(page.get("pageName") or page.get("name") or page.get("title") or page_key)
            route_path = self._text(page.get("routePath") or page.get("route") or page.get("path"))
            if not route_path:
                route_path = "/" + self._slug(page_key, fallback=f"page-{index + 1}")
            if not route_path.startswith("/"):
                route_path = "/" + route_path.strip("/")

            surface_type = self._text(page.get("surfaceType") or page.get("displayRole") or page.get("pageDisplayRole"))
            if not surface_type:
                surface_type = self._infer_module_surface_type(page_name, page_key, self._text(page.get("pageKind")))
            navigation_visible = page.get("navigationVisible")
            if navigation_visible is None:
                navigation_visible = surface_type == "MAIN_NAV_PAGE" or (index == 0 and len(self._as_list(pages_payload)) == 1)

            page.update(
                {
                    "pageKey": page_key,
                    "pageName": page_name,
                    "routePath": route_path,
                    "route": route_path,
                    "surfaceType": surface_type,
                    "displayRole": surface_type,
                    "navigationVisible": bool(navigation_visible),
                    "entryMode": self._text(page.get("entryMode")) or ("NAVIGATION" if navigation_visible else "ACTION_ROUTE"),
                }
            )
            pages.append(page)

        if not pages:
            return self._module_app_contract_from_build_context_pages(build_context)

        raw_shell = self._dict_value(raw.get("appShell") or raw.get("shell"))
        module_name = self._text(raw.get("moduleName")) or self._text(self._dict_value(build_context.get("scope")).get("nodeName")) or "业务模块"
        module_key = self._text(raw.get("moduleKey")) or self._text(self._dict_value(build_context.get("scope")).get("nodeKey")) or "module"
        edges = self._normalize_module_edges(raw.get("navigationEdges") or raw.get("edges") or [], pages)

        app_navigation = self._as_list(raw_shell.get("navigation"))
        if not app_navigation:
            app_navigation = [
                {
                    "label": page["pageName"],
                    "pageKey": page["pageKey"],
                    "route": page["routePath"],
                    "visible": bool(page.get("navigationVisible")),
                    "surfaceType": page.get("surfaceType"),
                }
                for page in pages
                if page.get("navigationVisible")
            ]

        layout = self._text(raw_shell.get("layout") or raw_shell.get("shellLayout") or raw.get("shellKind")).upper()
        if layout not in {"SIDEBAR", "TOP_NAV", "ACTION_HUB", "NO_NAV"}:
            layout = self._infer_module_shell_layout(module_name + " " + module_key, app_navigation, edges)

        if layout in {"ACTION_HUB", "NO_NAV"}:
            app_navigation = []

        default_page = next((page for page in pages if page.get("navigationVisible")), pages[0])
        app_shell = {
            **raw_shell,
            "layout": layout,
            "navigationPlacement": self._module_navigation_placement(layout),
            "shellKind": raw_shell.get("shellKind") or ("ADMIN_SIDEBAR" if layout == "SIDEBAR" else ("WEBSITE_TOP_NAV" if layout == "TOP_NAV" else "ACTION_FLOW")),
            "title": self._text(raw_shell.get("title")) or module_name,
            "defaultPageKey": self._text(raw_shell.get("defaultPageKey")) or default_page.get("pageKey"),
            "defaultRoute": self._text(raw_shell.get("defaultRoute")) or default_page.get("routePath"),
            "navigation": app_navigation,
        }

        return {
            **raw,
            "schemaVersion": raw.get("schemaVersion") or "1.1",
            "artifactType": "MODULE_APP_CONTRACT",
            "contractType": "MODULE_APP_CONTRACT",
            "moduleKey": module_key,
            "moduleName": module_name,
            "appShell": app_shell,
            "pages": pages,
            "navigationEdges": edges,
            "pageEmbeddingPolicy": raw.get("pageEmbeddingPolicy") or {
                "contractType": "PAGE_UI_CONTRACT",
                "whenMissing": "SHOW_PLACEHOLDER_AND_GENERATE_PAGE_PROTOTYPE_ACTION",
                "refresh": "AUTO_ON_PAGE_UI_CONTRACT_AVAILABLE_OR_MANUAL_REFRESH",
            },
        }

    def _module_app_contract_from_build_context_pages(self, build_context: dict[str, Any]) -> dict[str, Any]:
        scope = self._dict_value(build_context.get("scope"))
        module_name = self._text(scope.get("nodeName") or self._dict_value(scope.get("effectiveModule")).get("nodeName")) or "业务模块"
        module_key = self._text(scope.get("nodeKey") or self._dict_value(scope.get("effectiveModule")).get("nodeKey")) or "module"
        page_items = self._page_items(build_context)

        pages: list[dict[str, Any]] = []
        for index, page_item in enumerate(page_items):
            contract = self._dict_value(page_item.get("uiContract") or page_item.get("contract"))
            page_key = self._text(page_item.get("pageKey") or self._dict_value(page_item.get("node")).get("nodeKey") or f"page_{index + 1}")
            page_name = self._text(page_item.get("pageName") or self._dict_value(page_item.get("node")).get("nodeName") or page_key)
            route_path = self._page_route_path(page_item, {}, contract, index)
            surface_type = self._infer_module_surface_type(page_name, page_key, self._text(page_item.get("pageKind")))
            visible = surface_type == "MAIN_NAV_PAGE" or (index == 0 and len(page_items) == 1)
            pages.append(
                {
                    "pageKey": page_key,
                    "pageName": page_name,
                    "routePath": route_path,
                    "route": route_path,
                    "pageKind": self._text(page_item.get("pageKind")) or self._contract_page_kind(contract),
                    "surfaceType": surface_type,
                    "displayRole": surface_type,
                    "navigationVisible": bool(visible),
                    "entryMode": "NAVIGATION" if visible else "ACTION_ROUTE",
                    "previewMode": "APP_SHELL_ROUTE",
                    "sourcePageId": self._text(page_item.get("pageId")),
                    "confirmed": bool(page_item.get("confirmed")),
                    "embeddingPolicy": {
                        "sourceArtifactType": "UI_CONTRACT",
                        "sourceNodeType": "PAGE",
                        "fallbackWhenMissing": "PAGE_CONTRACT_PLACEHOLDER",
                        "autoRefreshWhenGenerated": True,
                    },
                }
            )

        if not pages:
            return {}

        edges = self._infer_module_navigation_edges(pages)
        navigation = [
            {
                "label": page["pageName"],
                "pageKey": page["pageKey"],
                "route": page["routePath"],
                "visible": True,
                "surfaceType": page["surfaceType"],
            }
            for page in pages
            if page.get("navigationVisible")
        ]
        layout = self._infer_module_shell_layout(module_name + " " + module_key + " " + " ".join(page["pageName"] for page in pages), navigation, edges)
        if layout in {"ACTION_HUB", "NO_NAV"}:
            navigation = []

        default_page = next((page for page in pages if page.get("navigationVisible")), pages[0])
        return {
            "schemaVersion": "1.1",
            "artifactType": "MODULE_APP_CONTRACT",
            "contractType": "MODULE_APP_CONTRACT",
            "source": "DEVELOPMENT_BUILD_CONTEXT_FALLBACK",
            "moduleKey": module_key,
            "moduleName": module_name,
            "appShell": {
                "layout": layout,
                "navigationPlacement": self._module_navigation_placement(layout),
                "shellKind": "ADMIN_SIDEBAR" if layout == "SIDEBAR" else ("WEBSITE_TOP_NAV" if layout == "TOP_NAV" else "ACTION_FLOW"),
                "title": module_name,
                "defaultPageKey": default_page.get("pageKey"),
                "defaultRoute": default_page.get("routePath"),
                "navigation": navigation,
                "style": {
                    "navTheme": "dark" if layout == "SIDEBAR" else "light",
                    "density": "business",
                },
            },
            "pages": pages,
            "navigationEdges": edges,
            "pageEmbeddingPolicy": {
                "contractType": "PAGE_UI_CONTRACT",
                "whenMissing": "SHOW_PLACEHOLDER_AND_GENERATE_PAGE_PROTOTYPE_ACTION",
                "refresh": "AUTO_ON_PAGE_UI_CONTRACT_AVAILABLE_OR_MANUAL_REFRESH",
            },
        }

    def _module_app_shell(self, module_app_contract: dict[str, Any] | None) -> dict[str, Any]:
        contract = self._dict_value(module_app_contract)
        shell = self._dict_value(contract.get("appShell"))
        if not shell:
            shell = {}
        layout = self._text(shell.get("layout")).upper()
        if layout not in {"SIDEBAR", "TOP_NAV", "ACTION_HUB", "NO_NAV"}:
            layout = "SIDEBAR"
        shell = dict(shell)
        shell["layout"] = layout
        shell.setdefault("navigationPlacement", self._module_navigation_placement(layout))
        shell.setdefault("title", self._text(contract.get("moduleName")) or "业务系统")
        shell.setdefault("navigation", [])
        return shell

    def _module_app_pages(self, module_app_contract: dict[str, Any] | None) -> list[dict[str, Any]]:
        pages = self._as_list(self._dict_value(module_app_contract).get("pages"))
        return [item for item in pages if isinstance(item, dict)]

    def _module_app_edges(self, module_app_contract: dict[str, Any] | None) -> list[dict[str, Any]]:
        edges = self._as_list(self._dict_value(module_app_contract).get("navigationEdges"))
        return [item for item in edges if isinstance(item, dict)]

    def _normalize_module_edges(self, edges: Any, pages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        page_by_key = {self._text(page.get("pageKey")): page for page in pages}
        for index, raw in enumerate(self._as_list(edges)):
            if not isinstance(raw, dict):
                continue
            source_key = self._text(raw.get("sourcePageKey") or raw.get("fromPage") or raw.get("fromPageKey"))
            target_key = self._text(raw.get("targetPageKey") or raw.get("toPage") or raw.get("toPageKey"))
            target_page = page_by_key.get(target_key, {})
            source_page = page_by_key.get(source_key, {})
            target_route = self._text(raw.get("targetRoute") or raw.get("toRoute") or target_page.get("routePath"))
            if not target_route:
                continue
            if not target_route.startswith("/"):
                target_route = "/" + target_route.strip("/")
            action_id = self._text(raw.get("actionId") or raw.get("id")) or f"action_{index + 1}"
            label = self._text(raw.get("actionLabel") or raw.get("label")) or self._module_navigation_label(action_id, self._text(target_page.get("pageName") or target_key))
            result.append(
                {
                    **raw,
                    "sourcePageKey": source_key,
                    "sourceRoute": self._text(raw.get("sourceRoute") or source_page.get("routePath")),
                    "targetPageKey": target_key,
                    "targetRoute": target_route,
                    "actionId": action_id,
                    "actionLabel": label,
                    "label": label,
                    "interactionType": self._text(raw.get("interactionType")) or "ROUTE_NAVIGATION",
                    "renderAs": self._text(raw.get("renderAs")) or "ACTION_BUTTON",
                }
            )
        return result

    def _infer_module_navigation_edges(self, pages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        edges: list[dict[str, Any]] = []
        for source in pages:
            source_text = " ".join([self._text(source.get("pageName")), self._text(source.get("pageKey")), self._text(source.get("surfaceType"))])
            is_main = self._text(source.get("surfaceType")) == "MAIN_NAV_PAGE" or any(token in source_text for token in ["列表", "查询", "搜索", "LIST", "QUERY"])
            if not is_main:
                continue
            for target in pages:
                if target.get("pageKey") == source.get("pageKey"):
                    continue
                action_id = self._infer_module_action_id(self._text(target.get("pageName")) + " " + self._text(target.get("pageKey")))
                if not action_id:
                    continue
                label = self._module_navigation_label(action_id, self._text(target.get("pageName")))
                edges.append(
                    {
                        "sourcePageKey": source.get("pageKey"),
                        "sourcePageName": source.get("pageName"),
                        "sourceRoute": source.get("routePath"),
                        "actionId": action_id,
                        "actionLabel": label,
                        "label": label,
                        "interactionType": "ROUTE_NAVIGATION",
                        "targetPageKey": target.get("pageKey"),
                        "targetPageName": target.get("pageName"),
                        "targetRoute": target.get("routePath"),
                        "renderAs": "ACTION_BUTTON",
                    }
                )
        return edges

    def _infer_module_action_id(self, target_text: str) -> str:
        text = target_text or ""
        lower = text.lower()
        if any(token in text for token in ["新建", "新增", "创建", "录入"]) or any(token in lower for token in ["create", "new", "add"]):
            return "create"
        if any(token in text for token in ["详情", "明细", "查看"]) or any(token in lower for token in ["detail", "view"]):
            return "detail"
        if any(token in text for token in ["审核", "审批"]) or any(token in lower for token in ["approval", "approve", "audit"]):
            return "approval"
        if any(token in text for token in ["数量", "编辑", "修改"]) or any(token in lower for token in ["quantity", "edit", "modify"]):
            return "edit"
        return ""

    def _module_navigation_label(self, action_id: str, target_name: str) -> str:
        action = self._text(action_id).lower()
        target = self._text(target_name) or "页面"
        if action == "create":
            return "新建"
        if action == "detail":
            return "查看详情"
        if action == "approval":
            return "审核"
        if action == "edit":
            return "编辑"
        return target

    def _infer_module_surface_type(self, page_name: str, page_key: str, page_kind: str = "") -> str:
        text = " ".join([page_name or "", page_key or "", page_kind or ""])
        upper = text.upper()
        if any(token in text for token in ["列表", "查询", "搜索", "看板", "首页", "工作台"]) or any(token in upper for token in ["LIST", "QUERY", "SEARCH", "DASHBOARD", "HOME"]):
            return "MAIN_NAV_PAGE"
        if any(token in text for token in ["创建", "新建", "新增", "录入", "详情", "明细", "编辑", "修改", "审核", "审批", "提交"]) or any(token in upper for token in ["CREATE", "NEW", "ADD", "FORM", "DETAIL", "EDIT", "APPROVAL", "AUDIT", "SUBMIT"]):
            return "ACTION_ROUTE_PAGE"
        return "SECONDARY_ROUTE_PAGE"

    def _infer_module_shell_layout(
        self,
        module_text: str,
        navigation: list[Any],
        edges: list[Any],
    ) -> str:
        text = module_text or ""
        lower = text.lower()
        if any(token in text for token in ["电商", "商城", "店铺", "购物", "门户", "官网", "内容", "营销", "C端", "小程序"]):
            return "TOP_NAV"
        if not navigation and edges:
            return "ACTION_HUB"
        if any(token in text for token in ["后台", "管理", "审批", "审核", "仓储", "库存", "入库", "出库", "订单管理", "运营", "配置", "权限"]):
            return "SIDEBAR"
        if any(token in lower for token in ["admin", "dashboard", "management", "console", "workflow"]):
            return "SIDEBAR"
        if len(navigation) <= 1 and len(edges) >= 3:
            return "ACTION_HUB"
        return "SIDEBAR"

    def _module_navigation_placement(self, layout: str) -> str:
        return {
            "SIDEBAR": "left",
            "TOP_NAV": "top",
            "ACTION_HUB": "none",
            "NO_NAV": "none",
        }.get(self._text(layout).upper(), "left")

    def _module_app_page_for_resource(
        self,
        resource: dict[str, Any],
        module_app_contract: dict[str, Any] | None,
    ) -> dict[str, Any]:
        pages = self._module_app_pages(module_app_contract)
        if not pages:
            return {}

        resource_page_key = self._text(resource.get("pageKey") or resource.get("resourceKey"))
        resource_route = self._normalize_route(self._text(resource.get("routePath")))
        resource_title = self._text(resource.get("title"))

        for page in pages:
            if resource_page_key and resource_page_key == self._text(page.get("pageKey")):
                return page

        for page in pages:
            if resource_route and resource_route == self._normalize_route(self._text(page.get("routePath") or page.get("route"))):
                return page

        for page in pages:
            page_name = self._text(page.get("pageName"))
            if resource_title and page_name and resource_title == page_name:
                return page

        return {}

    def _normalize_route(self, value: Any) -> str:
        text = self._text(value)
        if not text:
            return ""
        route = "/" + text.strip().strip("/")
        return re.sub(r"/+", "/", route)

    # A9.10.13.3 MODULE_PROTOTYPE_SNAPSHOT development authority helpers
    def _strict_missing_ui_contract_pages(
        self,
        build_context: dict[str, Any],
        contracts: list[dict[str, Any]],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[str]:
        # A9.10.13.3: Development Agent 的授权入口改为 MODULE_PROTOTYPE_SNAPSHOT。
        # Java Handoff 已负责验证快照是否存在、是否过期，以及是否至少包含一个可用 PAGE_UI_CONTRACT。
        # Python 只在完全拿不到任何可用页面契约时阻断，允许模块级 PARTIAL 生成。
        usable_contracts = [
            item for item in contracts
            if self._dict_value(item.get("contract") or item.get("uiContract"))
        ]
        if usable_contracts:
            return []

        for page_item in self._page_items(build_context):
            if self._dict_value(page_item.get("uiContract") or page_item.get("contract")):
                return []

        return ["当前模块开发基线未提供任何可用 PAGE_UI_CONTRACT；请先在需求 Agent 的完整模块预览中确认为开发基线"]

    def _strict_expected_pages(
        self,
        build_context: dict[str, Any],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        pages: list[dict[str, Any]] = []
        module_pages = self._module_app_pages(module_app_contract)
        if module_pages:
            for index, page in enumerate(module_pages):
                item = dict(page)
                item.setdefault("pageKey", page.get("pageKey") or page.get("key") or f"page_{index + 1}")
                item.setdefault("pageName", page.get("pageName") or page.get("title") or item.get("pageKey"))
                pages.append(item)
            return pages

        for index, page_item in enumerate(self._page_items(build_context)):
            item = dict(page_item)
            node = self._dict_value(item.get("node"))
            item.setdefault("pageKey", item.get("pageKey") or item.get("key") or node.get("nodeKey") or f"page_{index + 1}")
            item.setdefault("pageName", item.get("pageName") or item.get("title") or node.get("nodeName") or item.get("pageKey"))
            pages.append(item)
        return pages

    def _strict_page_label(self, page: dict[str, Any], index: int) -> str:
        return (
            self._text(page.get("pageName") or page.get("title"))
            or self._text(page.get("pageKey") or page.get("key"))
            or self._text(self._dict_value(page.get("node")).get("nodeName"))
            or f"页面 {index + 1}"
        )

    def _contract_match_tokens(self, value: Any) -> set[str]:
        result: set[str] = set()
        if not isinstance(value, dict):
            return result

        contract = self._dict_value(value.get("contract") or value.get("uiContract") or value)
        page = self._contract_page(contract)
        identity = self._dict_value(contract.get("identity"))

        candidates = [
            value.get("pageKey"), value.get("key"), value.get("nodeKey"), value.get("pageName"), value.get("title"),
            value.get("routePath"), value.get("route"), value.get("path"),
            page.get("pageKey"), page.get("key"), page.get("pageName"), page.get("title"), page.get("routePath"), page.get("path"),
            identity.get("pageKey"), identity.get("pageName"), identity.get("routePath"),
        ]
        node = self._dict_value(value.get("node"))
        candidates.extend([node.get("nodeKey"), node.get("nodeName"), node.get("previewRoute")])

        for item in candidates:
            text = self._text(item)
            if not text:
                continue
            result.add(text.lower())
            route = self._normalize_route(text)
            if route:
                result.add(route.lower())
        return result

    def _match_confirmed_contract_item(
        self,
        *,
        contracts: list[dict[str, Any]],
        page_item: dict[str, Any],
        module_page: dict[str, Any],
        index: int,
    ) -> dict[str, Any]:
        expected_tokens = self._contract_match_tokens(page_item) | self._contract_match_tokens(module_page)
        if expected_tokens:
            for item in contracts:
                if expected_tokens & self._contract_match_tokens(item):
                    return item
        if index < len(contracts):
            return contracts[index]
        return {}

    def _strict_codegen_failed_response(
        self,
        *,
        build_context: dict[str, Any],
        module_app_contract: dict[str, Any] | None,
        missing_pages: list[str],
        warnings: list[str],
    ) -> DevelopmentNodejsCodegenResponse:
        app_shell = self._module_app_shell(module_app_contract)
        module_pages = self._module_app_pages(module_app_contract)
        local_warnings = self._dedupe([
            *warnings,
            "A9.10.13.3_MODULE_PROTOTYPE_SNAPSHOT_GATE_BLOCKED",
            f"A9.10.13.3_MISSING_MODULE_PROTOTYPE_PAGE_CONTRACT_COUNT={len(missing_pages)}",
            *[f"A9.10.13.3_MISSING_MODULE_PROTOTYPE_PAGE_CONTRACT={name}" for name in missing_pages[:12]],
        ])
        return DevelopmentNodejsCodegenResponse(
            status="AGENT_FAILED",
            generator_mode="A9_10_13_3_MODULE_PROTOTYPE_SNAPSHOT_GATE",
            provider="deterministic",
            model="nodejs-fullstack-orchestrator-v1",
            summary=(
                "Development Agent 已阻断代码生成：当前 MODULE 尚未提供可用的模块汇总原型开发基线。"
                "真实代码只能由需求 Agent 中已确认的 MODULE_PROTOTYPE_SNAPSHOT 驱动；"
                "未纳入开发基线的页面只能显示占位提示，不能使用通用模板或业务 fallback 页面冒充开发结果。"
            ),
            warnings=local_warnings,
            files=[],
            usage={
                "provider": "deterministic",
                "model": "nodejs-fullstack-orchestrator-v1",
                "success": False,
                "modulePrototypeSnapshotGate": True,
                "missingModulePrototypePageContractCount": len(missing_pages),
                "missingModulePrototypePageContractNames": missing_pages,
                "moduleAppContractConsumed": bool(module_app_contract),
                "moduleAppShellLayout": app_shell.get("layout") or "SIDEBAR",
                "moduleAppPageCount": len(module_pages),
            },
            raw={
                "schema": "a9_10_13_3_module_prototype_snapshot_gate_failed",
                "missingPages": missing_pages,
                "moduleAppContract": module_app_contract,
            },
        )

    def _ui_contract_page_meta(
        self,
        contract: dict[str, Any],
        resource: dict[str, Any],
        entry: dict[str, Any],
        index: int,
    ) -> dict[str, str]:
        page = self._contract_page(contract)
        identity = self._dict_value(contract.get("identity"))
        return {
            "title": self._text(entry.get("title") or resource.get("title") or page.get("title") or page.get("pageName") or identity.get("pageName") or f"页面 {index + 1}"),
            "key": self._text(entry.get("pageKey") or resource.get("pageKey") or page.get("pageKey") or page.get("key") or identity.get("pageKey") or entry.get("component") or f"page_{index + 1}"),
            "routePath": self._text(entry.get("path") or resource.get("routePath") or page.get("routePath") or page.get("path") or identity.get("routePath") or "/"),
            "description": self._contract_description(contract) or self._text(resource.get("description")) or "该页面由已确认 PAGE_UI_CONTRACT 生成，并接入真实 Node.js 后端。",
            "pageKind": self._contract_page_kind(contract) or self._text(resource.get("pageKind") or "GENERIC"),
        }

    def _page_ui_contract_runtime_files(
        self,
        root: str,
        resource: dict[str, Any],
        entry: dict[str, Any],
        index: int,
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[DevelopmentCodeFilePayload]:
        contract = self._dict_value(resource.get("uiContract") or resource.get("contract"))
        page_meta = self._ui_contract_page_meta(contract, resource, entry, index)
        component_name = self._text(entry.get("component") or entry.get("name") or f"GeneratedPage{index + 1}")
        base_name = self._safe_identifier(component_name) or f"GeneratedPage{index + 1}"
        api_segment = self._text(entry.get("apiSegment") or resource.get("apiSegment") or self._api_segment_from_route(page_meta.get("routePath")) or base_name).strip("/")

        api_file_path = f"{root}/frontend/src/modules/generated/api/{base_name}Api.js"
        mock_file_path = f"{root}/frontend/src/modules/generated/mock/{base_name}MockData.js"
        types_file_path = f"{root}/frontend/src/modules/generated/types/{base_name}UiContractTypes.js"

        navigation_map = self._page_navigation_map(
            contract,
            entry,
            module_app_contract,
        )

        fullstack_binding = self._dict_value(resource.get("fullstackBinding")) or {
            "bindingVersion": PATCH_TOKEN,
            "apiSegment": api_segment,
            "pageTitle": page_meta.get("title"),
            "routePath": page_meta.get("routePath"),
            "actions": resource.get("actionBindings") or self._action_bindings_from_contract(contract, api_segment),
            "dataBindings": resource.get("dataBindings") or self._data_bindings_from_contract(contract, resource.get("fields") or [], api_segment),
        }

        page_content = self._runtime_contract_wrapper_vue(
            contract=contract,
            page_meta=page_meta,
            navigation_map=navigation_map,
            api_import_path=f"../api/{base_name}Api.js",
            api_segment=api_segment,
            fullstack_binding=fullstack_binding,
        )

        source = "NODEJS_FULLSTACK_ORCHESTRATOR+PAGE_UI_CONTRACT+NODEJS_RUNTIME_BINDING"
        return [
            self._file(entry["filePath"], "FRONTEND", "vue", source, f"{page_meta['title']}：由已确认 PAGE_UI_CONTRACT 生成的真实运行页面。", page_content, 60 + index * 4),
            self._file(api_file_path, "FRONTEND", "javascript", source, f"{page_meta['title']} 的真实 Node.js API 绑定。", self._runtime_api_client_js(contract, api_segment), 61 + index * 4),
            self._file(mock_file_path, "FRONTEND", "javascript", source, f"{page_meta['title']} 的 PAGE_UI_CONTRACT mock 数据兜底。", self.ui_contract_vue_codegen._render_mock_data(contract), 62 + index * 4),
            self._file(types_file_path, "FRONTEND", "javascript", source, f"{page_meta['title']} 的 PAGE_UI_CONTRACT 摘要常量。", self.ui_contract_vue_codegen._render_type_constants(contract, page_meta), 63 + index * 4),
        ]


    # A9.10.13.5_SHARED_UI_RUNTIME_MATERIALIZATION
    def _repository_root(self) -> Path:
        return Path(__file__).resolve().parents[3]

    def _read_runtime_source_file(self, relative_path: str) -> str:
        path = self._repository_root() / relative_path
        if not path.exists():
            raise FileNotFoundError(f"Shared UI runtime source file not found: {path}")
        return path.read_text(encoding="utf-8")

    def _adapt_shared_runtime_source(self, source: str, target_relative_path: str) -> str:
        """
        Runtime files are copied from codemind-web into generated app under:
          frontend/src/shared/ui-runtime
          frontend/src/shared/types

        Most imports already remain valid under that layout. A few older
        snapshots used temporary './types' or '././types' paths, so normalize
        them defensively here.
        """
        text = source.replace("\r\n", "\n")
        normalized = target_relative_path.replace("\\", "/")

        if normalized.endswith("shared/ui-runtime/UiContractPreviewRuntime.vue"):
            text = text.replace("from './types/themeProfile'", "from '../types/themeProfile'")
            text = text.replace("from './types/uiContract'", "from '../types/uiContract'")

        if normalized.endswith("shared/ui-runtime/UiContractRuntimeNode.vue"):
            text = text.replace("from './types/uiContract'", "from '../types/uiContract'")
            text = text.replace("from '././types/uiContract'", "from '../types/uiContract'")

        if normalized.endswith("shared/ui-runtime/ui-contract-runtime/UiContractPatternRenderer.vue"):
            text = text.replace("from './UiContractRuntimeNode.vue'", "from '../UiContractRuntimeNode.vue'")
            text = text.replace("from './types/uiContract'", "from '../../types/uiContract'")
            text = text.replace("from '././types/uiContract'", "from '../../types/uiContract'")
            text = text.replace("from '../types/uiContract'", "from '../../types/uiContract'")

        if normalized.endswith("shared/ui-runtime/ui-contract-runtime/uiContractTheme.ts"):
            text = text.replace("from './types/themeProfile'", "from '../../types/themeProfile'")
            text = text.replace("from '././types/themeProfile'", "from '../../types/themeProfile'")
            text = text.replace("from '../types/themeProfile'", "from '../../types/themeProfile'")
            text = text.replace("from './types/uiContract'", "from '../../types/uiContract'")
            text = text.replace("from '././types/uiContract'", "from '../../types/uiContract'")
            text = text.replace("from '../types/uiContract'", "from '../../types/uiContract'")

        if normalized.endswith("shared/ui-runtime/ui-contract-runtime/themeProfileBridge.ts"):
            text = text.replace("from './types/themeProfile'", "from '../../types/themeProfile'")
            text = text.replace("from '././types/themeProfile'", "from '../../types/themeProfile'")
            text = text.replace("from '../types/themeProfile'", "from '../../types/themeProfile'")

        return text.strip() + "\n"

    def _shared_ui_runtime_files(self, root: str) -> list[DevelopmentCodeFilePayload]:
        """
        Copy the Requirement Agent UI_CONTRACT runtime into the generated app.

        This is the critical A9.10.13.5 change: generated business pages use
        the same Runtime Vue components as the Requirement preview, instead of
        a second Python renderer that tries to imitate them.
        """
        specs = [
            (
                "codemind-web/src/components/UiContractPreviewRuntime.vue",
                f"{root}/frontend/src/shared/ui-runtime/UiContractPreviewRuntime.vue",
                "vue",
                "Requirement UI_CONTRACT preview runtime copied for generated app.",
            ),
            (
                "codemind-web/src/components/UiContractRuntimeNode.vue",
                f"{root}/frontend/src/shared/ui-runtime/UiContractRuntimeNode.vue",
                "vue",
                "Requirement UI_CONTRACT runtime node renderer copied for generated app.",
            ),
            (
                "codemind-web/src/components/ui-contract-runtime/UiContractPatternRenderer.vue",
                f"{root}/frontend/src/shared/ui-runtime/ui-contract-runtime/UiContractPatternRenderer.vue",
                "vue",
                "Requirement UI_CONTRACT pattern renderer copied for generated app.",
            ),
            (
                "codemind-web/src/components/ui-contract-runtime/uiContractTheme.ts",
                f"{root}/frontend/src/shared/ui-runtime/ui-contract-runtime/uiContractTheme.ts",
                "typescript",
                "Requirement UI_CONTRACT runtime theme resolver copied for generated app.",
            ),
            (
                "codemind-web/src/components/ui-contract-runtime/themeProfileBridge.ts",
                f"{root}/frontend/src/shared/ui-runtime/ui-contract-runtime/themeProfileBridge.ts",
                "typescript",
                "Requirement project theme bridge copied for generated app.",
            ),
            (
                "codemind-web/src/types/uiContract.ts",
                f"{root}/frontend/src/shared/types/uiContract.ts",
                "typescript",
                "UI_CONTRACT TypeScript declarations copied for generated app.",
            ),
            (
                "codemind-web/src/types/themeProfile.ts",
                f"{root}/frontend/src/shared/types/themeProfile.ts",
                "typescript",
                "Project theme TypeScript declarations copied for generated app.",
            ),
        ]

        files: list[DevelopmentCodeFilePayload] = []
        for index, (source_relative, target_path, language, description) in enumerate(specs):
            source = self._read_runtime_source_file(source_relative)
            content = self._adapt_shared_runtime_source(source, target_path)
            files.append(
                self._file(
                    target_path,
                    "FRONTEND",
                    language,
                    "SHARED_UI_CONTRACT_RUNTIME",
                    description,
                    content,
                    11 + index,
                )
            )
        return files


    # A9.10.14.4.3.15.4.8.1 Prototype Contract -> Real Fullstack Binding helpers
    def _action_bindings_from_contract(
        self,
        contract: dict[str, Any],
        api_segment: str,
    ) -> list[dict[str, Any]]:
        actions = []
        seen: set[str] = set()
        normalized_api_segment = self._text(api_segment).strip("/") or "generated-resource"

        for index, action in enumerate(self.ui_contract_vue_codegen._interactions(contract)):
            if not isinstance(action, dict):
                continue

            action_id = self._text(
                action.get("interactionId")
                or action.get("actionId")
                or action.get("id")
                or action.get("key")
                or f"action_{index + 1}"
            )
            label = self._text(action.get("label") or action.get("title") or action.get("name") or action_id)
            raw_type = self._text(
                action.get("actionType")
                or action.get("interactionType")
                or action.get("type")
                or action.get("event")
                or action.get("behavior")
            )
            semantic_key = self._action_semantic_key(action_id, label, raw_type)
            action_kind = self._fullstack_action_kind(action, semantic_key)
            api_binding = self._dict_value(action.get("apiBinding") or action.get("api") or action.get("request") or action.get("backend"))

            method = self._text(api_binding.get("method") or action.get("method"))
            if not method:
                method = self._default_method_for_action_kind(action_kind, semantic_key)
            method = method.upper()

            path = self._text(api_binding.get("path") or api_binding.get("url") or action.get("path") or action.get("url"))
            if not path:
                path = self._default_path_for_action_kind(action_kind, normalized_api_segment, action_id)

            if path and not path.startswith("/"):
                path = "/" + path.strip("/")

            key = action_id or f"action_{index + 1}"
            if key in seen:
                continue
            seen.add(key)

            actions.append(
                {
                    "actionId": action_id,
                    "label": label,
                    "actionType": raw_type,
                    "semanticKey": semantic_key,
                    "kind": action_kind,
                    "method": method,
                    "path": path,
                    "apiSegment": normalized_api_segment,
                    "source": "PAGE_UI_CONTRACT",
                    "realization": self._fullstack_realization_for_action(action_kind),
                }
            )

        return actions

    def _data_bindings_from_contract(
        self,
        contract: dict[str, Any],
        fields: list[dict[str, Any]],
        api_segment: str,
    ) -> dict[str, Any]:
        normalized_api_segment = self._text(api_segment).strip("/") or "generated-resource"
        field_payload = [
            {
                "fieldKey": self._text(field.get("key")),
                "label": self._text(field.get("label") or field.get("key")),
                "type": self._text(field.get("type") or "TEXT"),
                "required": bool(field.get("required")),
                "payload": True,
                "tableColumn": True,
            }
            for field in fields
            if isinstance(field, dict) and self._text(field.get("key"))
        ]

        return {
            "apiSegment": normalized_api_segment,
            "list": {
                "method": "GET",
                "path": f"/api/{normalized_api_segment}",
                "bindsTo": "UI_CONTRACT.mockData -> liveRows",
            },
            "create": {
                "method": "POST",
                "path": f"/api/{normalized_api_segment}",
                "payloadFields": [field["fieldKey"] for field in field_payload if field["fieldKey"] != "id"],
            },
            "update": {
                "method": "PUT",
                "path": f"/api/{normalized_api_segment}/:id",
                "payloadFields": [field["fieldKey"] for field in field_payload if field["fieldKey"] != "id"],
            },
            "delete": {
                "method": "DELETE",
                "path": f"/api/{normalized_api_segment}/:id",
            },
            "fields": field_payload,
        }

    def _fullstack_action_kind(self, action: dict[str, Any], semantic_key: str) -> str:
        raw = " ".join(
            [
                self._text(action.get("actionType")),
                self._text(action.get("interactionType")),
                self._text(action.get("type")),
                self._text(action.get("event")),
                self._text(action.get("behavior")),
                self._text(action.get("label")),
                self._text(action.get("title")),
                self._text(action.get("name")),
                self._text(semantic_key),
            ]
        ).lower()

        if any(token in raw for token in ["navigate", "route", "跳转", "打开页面", "返回"]):
            return "NAVIGATE"
        if any(token in raw for token in ["query", "search", "filter", "refresh", "load", "查询", "搜索", "筛选", "刷新", "读取"]):
            return "QUERY_DATA"
        if any(token in raw for token in ["delete", "remove", "删除", "移除"]):
            return "DELETE_ROW"
        if any(token in raw for token in ["update", "edit", "modify", "修改", "编辑"]):
            return "UPDATE_RECORD"
        if any(token in raw for token in ["submit", "save", "create", "new", "add", "approve", "提交", "保存", "创建", "新建", "新增", "审核", "审批"]):
            return "SUBMIT_FORM"
        return "ACTION_COMMAND"

    def _default_method_for_action_kind(self, action_kind: str, semantic_key: str) -> str:
        kind = self._text(action_kind).upper()
        semantic = self._text(semantic_key).lower()
        if kind == "QUERY_DATA" or "query" in semantic or "search" in semantic:
            return "GET"
        if kind == "DELETE_ROW" or "delete" in semantic:
            return "DELETE"
        if kind == "UPDATE_RECORD" or "edit" in semantic:
            return "PUT"
        if kind == "NAVIGATE":
            return "ROUTE"
        return "POST"

    def _default_path_for_action_kind(self, action_kind: str, api_segment: str, action_id: str) -> str:
        segment = self._text(api_segment).strip("/") or "generated-resource"
        kind = self._text(action_kind).upper()
        if kind in {"QUERY_DATA", "SUBMIT_FORM", "UPDATE_RECORD", "DELETE_ROW"}:
            return f"/api/{segment}"
        action_suffix = self._slug(action_id or "action", fallback="action")
        return f"/api/{segment}/{action_suffix}"

    def _fullstack_realization_for_action(self, action_kind: str) -> str:
        kind = self._text(action_kind).upper()
        if kind == "NAVIGATE":
            return "Vue Router navigation driven by MODULE_APP_CONTRACT navigationEdges."
        if kind == "QUERY_DATA":
            return "Vue runtime action -> fetchUiRows -> Express GET route -> localStore list/filter."
        if kind == "SUBMIT_FORM":
            return "Vue runtime action -> runUiAction -> Express POST route -> localStore create."
        if kind == "UPDATE_RECORD":
            return "Vue runtime action -> runUiAction -> Express PUT route -> localStore update."
        if kind == "DELETE_ROW":
            return "Vue runtime action -> runUiAction -> Express DELETE route -> localStore delete."
        return "Vue runtime action -> Express action route -> generated service command handler."


    def _runtime_contract_wrapper_vue(
        self,
        *,
        contract: dict[str, Any],
        page_meta: dict[str, str],
        navigation_map: dict[str, str] | None = None,
        api_import_path: str = "",
        api_segment: str = "",
        fullstack_binding: dict[str, Any] | None = None,
    ) -> str:
        navigation_map = {
            self._text(key): self._text(value)
            for key, value in (navigation_map or {}).items()
            if self._text(key) and self._text(value)
        }
        page_title = self._text(page_meta.get("title")) or self._contract_title(contract) or "业务页面"
        route_path = self._text(page_meta.get("routePath")) or self._text(self._contract_page(contract).get("routePath"))
        source_contract = dict(contract or {})
        source_contract.setdefault("page", self._contract_page(source_contract))
        source_contract.setdefault("interactions", [])
        source_contract.setdefault("pageStates", [])
        source_contract.setdefault("apiBindings", [])
        source_contract.setdefault("featureCoverage", [])
        source_contract.setdefault("qualitySummary", {})
        source_contract.setdefault("mockData", {})
        source_contract.setdefault("assumptions", [])

        project_theme_profile = (
            self._dict_value(source_contract.get("projectThemeProfile"))
            or self._dict_value(source_contract.get("themeProfile"))
        )

        binding_payload = self._dict_value(fullstack_binding)
        if not binding_payload:
            normalized_api_segment = self._text(api_segment).strip("/") or self._api_segment_from_route(route_path) or "generated-resource"
            binding_payload = {
                "apiSegment": normalized_api_segment,
                "pageTitle": page_title,
                "routePath": route_path,
                "actions": self._action_bindings_from_contract(contract, normalized_api_segment),
            }

        api_import_path = self._text(api_import_path) or "../api/GeneratedPageApi.js"

        return f"""<template>
  <section class=\"generated-runtime-page\">
    <UiContractPreviewRuntime
      :contract=\"liveContract\"
      display-mode=\"artboard\"
      :project-theme-profile=\"projectThemeProfile\"
      :navigate-request=\"handleNavigate\"
      @prototype-navigate=\"handlePrototypeNavigate\"
      @prototype-action=\"handlePrototypeAction\"
    />

    <aside v-if=\"runtimeMessage || runtimeError || runtimeLoading\" class=\"runtime-binding-feedback\" aria-live=\"polite\">
      <strong>真实后端绑定</strong>
      <span v-if=\"runtimeLoading\">正在调用 Node.js API...</span>
      <span v-else-if=\"runtimeError\" class=\"is-error\">{{{{ runtimeError }}}}</span>
      <span v-else>{{{{ runtimeMessage }}}}</span>
    </aside>
  </section>
</template>

<script setup>
import {{ computed, onMounted, ref }} from 'vue'
import {{ useRouter }} from 'vue-router'
import UiContractPreviewRuntime from '../../../shared/ui-runtime/UiContractPreviewRuntime.vue'
import {{ fetchUiRows, runUiAction }} from '{api_import_path}'

const contract = {self._js(source_contract)}
const projectThemeProfile = {self._js(project_theme_profile or None)}
const navigationMap = {self._js(navigation_map)}
const fullstackBinding = {self._js(binding_payload)}
const router = useRouter()

const liveRows = ref([])
const runtimeLoading = ref(false)
const runtimeMessage = ref('')
const runtimeError = ref('')

const liveContract = computed(() => mergeLiveData(contract, liveRows.value))

onMounted(() => {{
  refreshRows(false)
}})

async function handleNavigate(payload = {{}}) {{
  const target = resolveNavigationTarget(payload)
  if (target) {{
    await router.push(target)
    return {{
      ok: true,
      code: 'SUCCESS',
      message: '已打开目标页面。',
      route: target,
    }}
  }}

  return {{
    ok: false,
    code: 'TARGET_NOT_FOUND',
    message: '当前动作没有配置目标页面。',
  }}
}}

function handlePrototypeNavigate(payload = {{}}) {{
  handleNavigate(payload)
}}

async function handlePrototypeAction(payload = {{}}) {{
  const target = resolveNavigationTarget(payload)
  if (target) {{
    await router.push(target)
    return {{ ok: true, code: 'NAVIGATED', route: target }}
  }}

  const actionId = String(payload.actionId || payload.id || payload.key || '').trim()
  const label = String(payload.label || payload.actionLabel || payload.title || '').trim()
  const actionType = String(payload.actionType || payload.type || payload.interactionType || '').trim()
  const semantic = semanticKey(actionId, label, actionType)
  const binding = resolveActionBinding(actionId, semantic)

  if (shouldQuery(binding, semantic)) {{
    await refreshRows(true)
    return {{ ok: true, code: 'QUERY_DATA', rows: liveRows.value.length }}
  }}

  runtimeLoading.value = true
  runtimeError.value = ''
  runtimeMessage.value = ''
  try {{
    const result = await runUiAction(actionId || semantic || 'action', normalizeActionPayload(payload, semantic, binding))
    runtimeMessage.value = binding?.label ? `${{binding.label}}已完成，并已同步真实后端。` : '动作已同步真实后端。'
    if (shouldReloadAfterAction(binding, semantic)) {{
      await refreshRows(false)
    }}
    return {{ ok: true, code: 'ACTION_REALIZED', result }}
  }} catch (error) {{
    runtimeError.value = error?.message || '动作调用失败'
    return {{ ok: false, code: 'ACTION_FAILED', message: runtimeError.value }}
  }} finally {{
    runtimeLoading.value = false
  }}
}}

async function refreshRows(showMessage = true) {{
  runtimeLoading.value = true
  runtimeError.value = ''
  try {{
    const rows = await fetchUiRows()
    liveRows.value = Array.isArray(rows) ? rows : []
    if (showMessage) runtimeMessage.value = `已从 Node.js 后端读取 ${{liveRows.value.length}} 条数据。`
  }} catch (error) {{
    runtimeError.value = error?.message || '读取后端数据失败'
  }} finally {{
    runtimeLoading.value = false
  }}
}}

function resolveActionBinding(actionId, semantic) {{
  const actions = Array.isArray(fullstackBinding.actions) ? fullstackBinding.actions : []
  return actions.find((item) => item.actionId === actionId)
    || actions.find((item) => item.semanticKey === semantic)
    || null
}}

function shouldQuery(binding, semantic) {{
  const kind = String(binding?.kind || '').toUpperCase()
  const method = String(binding?.method || '').toUpperCase()
  return kind === 'QUERY_DATA' || method === 'GET' || /query|search|filter|refresh|load|查询|搜索|筛选|刷新|读取/.test(String(semantic || '').toLowerCase())
}}

function shouldReloadAfterAction(binding, semantic) {{
  const kind = String(binding?.kind || '').toUpperCase()
  const method = String(binding?.method || '').toUpperCase()
  return ['SUBMIT_FORM', 'UPDATE_RECORD', 'DELETE_ROW', 'ACTION_COMMAND'].includes(kind)
    || ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
    || /submit|save|create|update|delete|提交|保存|新增|创建|修改|删除|审核|审批/.test(String(semantic || '').toLowerCase())
}}

function normalizeActionPayload(payload = {{}}, semantic = '', binding = null) {{
  const record = payload.record || payload.row || payload.currentRow || payload.selectedRow || null
  const form = payload.form || payload.values || payload.payload || payload.data || record || {{}}
  return {{
    actionId: payload.actionId || payload.id || binding?.actionId || semantic,
    semantic,
    form,
    record,
    row: record,
    value: payload.value,
    source: payload,
  }}
}}

function mergeLiveData(source, rows) {{
  const clone = cloneJson(source || {{}})
  const data = typeof clone.mockData === 'object' && clone.mockData !== null ? clone.mockData : {{}}
  if (Array.isArray(rows) && rows.length) {{
    data.rows = rows
    data.items = rows
    data.records = rows
    data.tableData = rows
    data.list = rows
  }}
  clone.mockData = data
  clone.runtimeBinding = {{
    apiSegment: fullstackBinding.apiSegment,
    actionCount: Array.isArray(fullstackBinding.actions) ? fullstackBinding.actions.length : 0,
    liveRowCount: Array.isArray(rows) ? rows.length : 0,
  }}
  return clone
}}

function cloneJson(value) {{
  try {{
    return JSON.parse(JSON.stringify(value || {{}}))
  }} catch (_error) {{
    return value || {{}}
  }}
}}

function resolveNavigationTarget(payload = {{}}) {{
  const actionId = String(payload.actionId || payload.id || '').trim()
  const label = String(payload.label || payload.actionLabel || payload.title || '').trim()
  const actionType = String(payload.actionType || payload.type || '').trim()
  let target = navigationMap[actionId]

  if (!target) {{
    target = navigationMap[semanticKey(actionId, label, actionType)]
  }}

  if (!target && payload.route) {{
    target = String(payload.route || '')
  }}

  return normalizeRoute(target)
}}

function semanticKey(...values) {{
  const raw = values.filter(Boolean).join(' ').toLowerCase()
  if (!raw) return ''
  if (/返回|back|list/.test(raw)) return 'back_to_list'
  if (/详情|查看|detail|view/.test(raw)) return 'view_detail'
  if (/审核|审批|approve|approval|audit/.test(raw)) return 'approval'
  if (/创建|新建|新增|录入|create|new|add/.test(raw)) return 'create'
  if (/编辑|修改|edit|modify/.test(raw)) return 'edit'
  if (/删除|delete|remove/.test(raw)) return 'delete'
  if (/查询|搜索|筛选|刷新|读取|query|search|filter|refresh|load/.test(raw)) return 'query'
  if (/提交|保存|submit|save/.test(raw)) return 'submit'
  return raw.replace(/[^a-z0-9\\u4e00-\\u9fff]+/g, '_').replace(/^_+|_+$/g, '')
}}

function normalizeRoute(value) {{
  const text = String(value || '').trim()
  if (!text) return ''
  return '/' + text.replace(/^\\/+|\\/+$/g, '')
}}
</script>

<style scoped>
.generated-runtime-page {{
  position: relative;
  min-width: 0;
}}

.generated-runtime-page :deep(.runtime-shell) {{
  min-height: auto;
}}

.runtime-binding-feedback {{
  position: sticky;
  bottom: 18px;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: 720px;
  margin: 16px 0 0 18px;
  padding: 10px 14px;
  border: 1px solid rgba(37, 99, 235, .24);
  border-radius: 999px;
  background: rgba(239, 246, 255, .96);
  color: #1d4ed8;
  box-shadow: 0 14px 34px rgba(15, 23, 42, .12);
  font-size: 13px;
}}

.runtime-binding-feedback strong {{
  font-weight: 900;
}}

.runtime-binding-feedback .is-error {{
  color: #dc2626;
  font-weight: 800;
}}
</style>
""".strip()

    def _action_semantic_key(self, *values: Any) -> str:
        raw = " ".join(self._text(v) for v in values if self._text(v)).lower()
        normalized = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "_", raw).strip("_")
        groups = [
            (("back_to_list", "return_list", "返回列表", "返回"), "back_to_list"),
            (("view_detail", "detail", "查看详情", "明细查看", "查看"), "view_detail"),
            (("submit_approval", "approval", "approve", "审核", "提交审核"), "approval"),
            (("create_record", "create", "new", "add", "新增", "新建", "创建"), "create"),
            (("edit_record", "edit", "编辑", "修改"), "edit"),
            (("delete_record", "delete", "remove", "删除"), "delete"),
        ]
        for aliases, semantic in groups:
            if any(alias in raw or alias in normalized for alias in aliases):
                return semantic
        return normalized

    def _page_navigation_map(
        self,
        contract: dict[str, Any],
        entry: dict[str, Any],
        module_app_contract: dict[str, Any] | None,
    ) -> dict[str, str]:
        current_key = self._text(entry.get("pageKey"))
        current_route = self._text(entry.get("path") or entry.get("routePath"))
        current_route = "/" + current_route.strip("/") if current_route else ""
        edges: list[dict[str, Any]] = []
        for edge in self._module_app_edges(module_app_contract):
            if edge.get("hidden") is True or edge.get("visible") is False:
                continue
            source_key = self._text(edge.get("sourcePageKey") or edge.get("fromPage") or edge.get("fromPageKey"))
            source_route = self._text(edge.get("sourceRoute") or edge.get("fromRoute"))
            source_route = "/" + source_route.strip("/") if source_route else ""
            if current_key and source_key and source_key != current_key:
                continue
            if not source_key and current_route and source_route and source_route != current_route:
                continue
            target = self._text(edge.get("targetRoute") or edge.get("toRoute"))
            if not target:
                continue
            if not target.startswith("/"):
                target = "/" + target.strip("/")
            edges.append({**edge, "targetRoute": target})

        exact: dict[str, str] = {}
        semantic: dict[str, str] = {}
        for edge in edges:
            aid = self._text(edge.get("actionId") or edge.get("id"))
            label = self._text(edge.get("actionLabel") or edge.get("label"))
            target = self._text(edge.get("targetRoute"))
            if aid:
                exact[aid] = target
            key = self._action_semantic_key(aid, label)
            if key and key not in semantic:
                semantic[key] = target

        result: dict[str, str] = {}
        for action in self.ui_contract_vue_codegen._interactions(contract):
            aid = self._text(action.get("interactionId") or action.get("actionId") or action.get("id") or action.get("key"))
            if not aid:
                continue
            if aid in exact:
                result[aid] = exact[aid]
                continue
            key = self._action_semantic_key(aid, action.get("label"), action.get("title"))
            if key in semantic:
                result[aid] = semantic[key]
        return result


    def _runtime_api_client_js(self, contract: dict[str, Any], api_segment: str) -> str:
        api_segment = self._text(api_segment).strip("/") or "generated-resource"
        action_meta = self._action_bindings_from_contract(contract, api_segment)
        return f"""const actionBindings = {self._js(action_meta)}
const basePath = '/api/{api_segment}'

export async function fetchUiRows(params = {{}}) {{
  const data = await request(withQuery(basePath, params))
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.items)) return data.items
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.rows)) return data.rows
  return []
}}

export async function runUiAction(actionId, payload = {{}}) {{
  const binding = findBinding(actionId, payload)
  const method = String(binding.method || defaultMethodFor(actionId, payload)).toUpperCase()

  if (method === 'ROUTE' || binding.kind === 'NAVIGATE') {{
    return {{ skipped: true, reason: 'NAVIGATION_ONLY', actionId }}
  }}

  if (method === 'GET') {{
    return request(withQuery(binding.path || basePath, payload.form || payload.payload || payload))
  }}

  if (method === 'DELETE') {{
    const id = resolveRecordId(payload)
    if (id) return request(`${{basePath}}/${{encodeURIComponent(id)}}`, {{ method: 'DELETE' }})
    return request(`${{basePath}}/${{encodeURIComponent(actionId || 'delete')}}`, {{
      method: 'POST',
      body: JSON.stringify(payload),
    }})
  }}

  if (method === 'PUT' || method === 'PATCH') {{
    const id = resolveRecordId(payload)
    const body = normalizeBody(payload)
    const path = id ? `${{basePath}}/${{encodeURIComponent(id)}}` : basePath
    return request(path, {{
      method: id ? method : 'POST',
      body: JSON.stringify(body),
    }})
  }}

  if (binding.path && binding.path !== basePath && !binding.path.endsWith('/')) {{
    return request(binding.path, {{
      method,
      body: JSON.stringify(normalizeBody(payload)),
    }})
  }}

  return request(basePath, {{
    method: method === 'POST' ? 'POST' : method,
    body: JSON.stringify(normalizeBody(payload)),
  }})
}}

function findBinding(actionId, payload = {{}}) {{
  const semantic = semanticKey(actionId, payload?.semantic, payload?.label, payload?.actionType)
  return actionBindings.find((item) => item.actionId === actionId)
    || actionBindings.find((item) => item.semanticKey === semantic)
    || {{
      actionId,
      semanticKey: semantic,
      kind: inferKind(semantic),
      method: defaultMethodFor(actionId, payload),
      path: basePath,
    }}
}}

function normalizeBody(payload = {{}}) {{
  if (payload.form && typeof payload.form === 'object') return payload.form
  if (payload.values && typeof payload.values === 'object') return payload.values
  if (payload.record && typeof payload.record === 'object') return payload.record
  if (payload.row && typeof payload.row === 'object') return payload.row
  if (payload.payload && typeof payload.payload === 'object') return payload.payload
  return payload
}}

function resolveRecordId(payload = {{}}) {{
  return payload.id
    || payload.record?.id
    || payload.row?.id
    || payload.form?.id
    || payload.values?.id
    || payload.payload?.id
    || null
}}

function defaultMethodFor(actionId, payload = {{}}) {{
  const semantic = semanticKey(actionId, payload?.semantic, payload?.label, payload?.actionType)
  if (/query|search|filter|refresh|load|查询|搜索|筛选|刷新|读取/.test(semantic)) return 'GET'
  if (/delete|remove|删除|移除/.test(semantic)) return 'DELETE'
  if (/edit|update|modify|编辑|修改/.test(semantic)) return 'PUT'
  if (/navigate|route|back|返回|跳转/.test(semantic)) return 'ROUTE'
  return 'POST'
}}

function inferKind(semantic) {{
  if (/query|search|filter|refresh|load|查询|搜索|筛选|刷新|读取/.test(semantic)) return 'QUERY_DATA'
  if (/delete|remove|删除|移除/.test(semantic)) return 'DELETE_ROW'
  if (/edit|update|modify|编辑|修改/.test(semantic)) return 'UPDATE_RECORD'
  if (/navigate|route|back|返回|跳转/.test(semantic)) return 'NAVIGATE'
  return 'SUBMIT_FORM'
}}

function semanticKey(...values) {{
  const raw = values.filter(Boolean).join(' ').toLowerCase()
  if (!raw) return ''
  if (/返回|back|list/.test(raw)) return 'back_to_list'
  if (/详情|查看|detail|view/.test(raw)) return 'view_detail'
  if (/审核|审批|approve|approval|audit/.test(raw)) return 'approval'
  if (/创建|新建|新增|录入|create|new|add/.test(raw)) return 'create'
  if (/编辑|修改|edit|modify/.test(raw)) return 'edit'
  if (/删除|delete|remove/.test(raw)) return 'delete'
  if (/查询|搜索|筛选|刷新|读取|query|search|filter|refresh|load/.test(raw)) return 'query'
  if (/提交|保存|submit|save/.test(raw)) return 'submit'
  return raw.replace(/[^a-z0-9\\u4e00-\\u9fff]+/g, '_').replace(/^_+|_+$/g, '')
}}

function withQuery(path, params = {{}}) {{
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params || {{}})) {{
    if (value === undefined || value === null || value === '') continue
    if (typeof value === 'object') continue
    query.set(key, String(value))
  }}
  const suffix = query.toString()
  return suffix ? `${{path}}?${{suffix}}` : path
}}

async function request(url, options = {{}}) {{
  const response = await fetch(url, {{
    headers: {{ 'Content-Type': 'application/json' }},
    ...options,
  }})
  const body = await response.json().catch(() => ({{}}))
  if (!response.ok || body.success === false) {{
    throw new Error(body?.error?.message || body?.message || `HTTP ${{response.status}}`)
  }}
  return body.data ?? body
}}
""".strip()

    def _missing_ui_contract_blocker_vue(self, resource: dict[str, Any], entry: dict[str, Any]) -> str:
        title = self._escape_html(self._text(entry.get("title") or resource.get("title") or "页面"))
        return f"""<template>
  <section class=\"ucg-page\">
    <div class=\"ucg-card\">
      <p class=\"ucg-eyebrow\">MODULE PROTOTYPE PLACEHOLDER</p>
      <h1>{title}</h1>
      <p>该页面没有纳入当前 MODULE_PROTOTYPE_SNAPSHOT 开发基线，Development Agent 只生成占位提示，不会用通用模板冒充真实业务页面。请回到需求 Agent 完成页面原型并重新确认模块汇总原型。</p>
    </div>
  </section>
</template>
""".strip()


    def _resources_from_context_and_vue_files(
        self,
        *,
        build_context: dict[str, Any],
        contracts: list[dict[str, Any]],
        vue_files: list[DevelopmentCodeFilePayload],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        api_segments = self._api_segments_from_vue_files(vue_files)
        page_items = self._page_items(build_context)
        module_pages = self._module_app_pages(module_app_contract)
        resources: list[dict[str, Any]] = []

        max_count = max(len(api_segments), len(contracts), len(page_items), len(module_pages))
        for index in range(max_count):
            contract_item = contracts[index] if index < len(contracts) else {}
            page_item = page_items[index] if index < len(page_items) else {}
            module_page = module_pages[index] if index < len(module_pages) else {}
            matched_contract_item = self._match_confirmed_contract_item(
                contracts=contracts,
                page_item=page_item,
                module_page=module_page,
                index=index,
            )
            if matched_contract_item:
                contract_item = matched_contract_item

            contract = self._dict_value(contract_item.get("contract"))
            if not contract:
                contract = self._dict_value(page_item.get("uiContract") or page_item.get("contract"))

            placeholder_only = not bool(contract)

            route_path = (
                self._text(module_page.get("routePath") or module_page.get("route"))
                or self._page_route_path(page_item, contract_item, contract, index)
            )
            segment = api_segments[index] if index < len(api_segments) else (
                self._api_segment_from_route(route_path) or f"resource-{index + 1}"
            )

            title = (
                self._text(module_page.get("pageName"))
                or self._text(page_item.get("pageName"))
                or self._text(contract_item.get("pageName"))
                or self._contract_title(contract)
                or f"业务资源 {index + 1}"
            )
            key = self._safe_identifier(
                self._text(module_page.get("pageKey"))
                or self._text(page_item.get("pageKey"))
                or self._text(contract_item.get("pageKey"))
                or segment
            )
            collection_key = key or f"resource{index + 1}"
            fields = self._fields_from_contract(contract) if contract else []
            if contract and not fields:
                fields = self._fallback_fields(title)

            action_bindings = self._action_bindings_from_contract(contract, segment) if contract else []
            data_bindings = self._data_bindings_from_contract(contract, fields, segment) if contract else {}
            fullstack_binding = {
                "bindingVersion": PATCH_TOKEN,
                "pageKey": self._text(module_page.get("pageKey")) or self._text(page_item.get("pageKey")) or self._text(contract_item.get("pageKey")) or key,
                "pageTitle": title,
                "routePath": route_path,
                "apiSegment": segment,
                "routeBinding": {
                    "frontendRoute": route_path,
                    "componentSource": "PAGE_UI_CONTRACT_RUNTIME",
                    "backendMount": f"/api/{segment}",
                },
                "dataBindings": data_bindings,
                "actions": action_bindings,
                "fidelityPolicy": {
                    "source": "MODULE_PROTOTYPE_SNAPSHOT",
                    "rule": "reuse confirmed UI_CONTRACT runtime and replace mock actions/data with real API/DB bindings",
                    "allowLayoutReinterpretation": False,
                },
            }

            resources.append(
                {
                    "resourceKey": collection_key,
                    "apiSegment": segment,
                    "title": title,
                    "pageKey": self._text(module_page.get("pageKey")) or self._text(page_item.get("pageKey")) or self._text(contract_item.get("pageKey")) or key,
                    "routePath": route_path,
                    "description": self._contract_description(contract) or self._text(page_item.get("description")) or (
                        "该页面已纳入模块开发基线，但当前未提供可生成的 PAGE_UI_CONTRACT。" if placeholder_only else "由 Development Build Context 生成的业务资源。"
                    ),
                    "pageKind": self._text(module_page.get("pageKind")) or self._contract_page_kind(contract) or self._text(page_item.get("pageKind")),
                    "surfaceType": self._text(module_page.get("surfaceType")),
                    "navigationVisible": module_page.get("navigationVisible"),
                    "entryMode": self._text(module_page.get("entryMode")),
                    "uiContract": contract,
                    "uiContractSource": "MODULE_PROTOTYPE_SNAPSHOT" if contract else "MODULE_PROTOTYPE_PLACEHOLDER",
                    "placeholderOnly": placeholder_only,
                    "fields": fields,
                    "actionBindings": action_bindings,
                    "dataBindings": data_bindings,
                    "fullstackBinding": fullstack_binding,
                    "seedRecords": self._seed_records(collection_key, title, fields) if contract else [],
                }
            )

        return self._dedupe_resources(resources)

    def _slug(self, value: Any, fallback: str = "generated-resource") -> str:
        """
        A9.10.11.3.1: Normalize arbitrary page/resource keys into a safe URL segment.
        Used by _page_route_path when the Development Build Context has no explicit route.
        """
        text = self._text(value).strip().lower()
        if not text:
            text = fallback

        # Keep ASCII letters/numbers as stable route segments. For Chinese or other
        # non-ASCII labels, fall back to a deterministic generated segment rather
        # than producing an invalid/empty import or URL segment.
        normalized = re.sub(r"[^0-9a-zA-Z]+", "-", text).strip("-").lower()
        if not normalized:
            normalized = fallback
        return normalized

    def _page_route_path(
        self,
        page_item: dict[str, Any],
        contract_item: dict[str, Any],
        contract: dict[str, Any],
        index: int,
    ) -> str:
        page = self._contract_page(contract)
        raw = (
            self._text(page_item.get("routePath"))
            or self._text(page_item.get("route_path"))
            or self._text(self._dict_value(page_item.get("pagePrototypeContext")).get("routePath"))
            or self._text(self._dict_value(page_item.get("pagePrototypeContext")).get("route_path"))
            or self._text(contract_item.get("routePath"))
            or self._text(page.get("routePath"))
            or self._text(page.get("path"))
            or self._text(self._dict_value(page.get("route")).get("path"))
        )
        if not raw:
            label = " ".join(
                filter(
                    None,
                    [
                        self._text(page_item.get("pageName")),
                        self._text(page_item.get("nodeName")),
                        self._text(contract_item.get("pageName")),
                        self._contract_title(contract),
                    ],
                )
            )
            raw = self._semantic_route_from_label(label)

        if not raw:
            key = self._text(page_item.get("pageKey")) or self._text(contract_item.get("pageKey")) or f"resource-{index + 1}"
            raw = "/" + self._slug(key, fallback=f"resource-{index + 1}")
        route = "/" + raw.strip().strip("/")
        return re.sub(r"/+", "/", route) or "/"

    def _semantic_route_from_label(self, label: Any) -> str:
        text = self._text(label)
        lower = text.lower()
        if not text:
            return ""

        # Domain-friendly routes for the inbound-order case currently used by the
        # warehouse management project. These routes are deterministic fallbacks;
        # explicit routePath values from Development Build Context still win.
        if "入库" in text or "inbound" in lower:
            base = "/inbound/orders"
            if any(token in text for token in ["创建", "新建", "新增", "录入"]) or "create" in lower:
                return base + "/create"
            if any(token in text for token in ["审核", "审批"]) or "approval" in lower or "approve" in lower:
                return base + "/approval"
            if any(token in text for token in ["数量", "编辑", "修改"]) or "quantity" in lower or "edit" in lower:
                return base + "/quantity-edit"
            if any(token in text for token in ["详情", "明细", "查看"]) or "detail" in lower:
                return base + "/detail"
            if any(token in text for token in ["列表", "查询", "检索"]) or "list" in lower or "search" in lower:
                return base
            return base
        return ""

    def _api_segment_from_route(self, route_path: str) -> str:
        route = self._text(route_path).strip("/")
        if not route:
            return ""
        route = re.sub(r":[^/]+", "", route)
        parts = [part for part in route.split("/") if part]
        if len(parts) >= 2:
            return "-".join(parts[:2])
        return "-".join(parts)

    def _api_segments_from_vue_files(
        self,
        vue_files: list[DevelopmentCodeFilePayload],
    ) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()
        for file in vue_files:
            path = self._normalize_path(file.file_path)
            match = re.search(r"/frontend/src/modules/[^/]+/api/([^/]+)Api\.js$", path)
            if not match:
                continue
            segment = match.group(1)
            if segment and segment not in seen:
                seen.add(segment)
                result.append(segment)
        return result

    def _page_items(self, build_context: dict[str, Any]) -> list[dict[str, Any]]:
        pages_payload = build_context.get("pages")
        raw_items: Any
        if isinstance(pages_payload, dict):
            raw_items = pages_payload.get("items") or pages_payload.get("pages") or []
        else:
            raw_items = pages_payload

        if not isinstance(raw_items, list):
            return []

        return [item for item in raw_items if isinstance(item, dict)]

    def _fields_from_contract(self, contract: dict[str, Any]) -> list[dict[str, Any]]:
        components = self._flatten_components(self._root_components(contract))
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for component in components:
            for raw in self._as_list(component.get("fields")):
                field = self._field_payload(raw)
                if field and field["key"] not in seen:
                    seen.add(field["key"])
                    result.append(field)

            for raw in self._as_list(component.get("columns")):
                field = self._field_payload(raw)
                if not field:
                    continue
                if self._is_action_like(field["key"], field["label"]):
                    continue
                if field["key"] not in seen:
                    seen.add(field["key"])
                    result.append(field)

            props = component.get("props")
            if isinstance(props, dict):
                for key in ("fields", "columns"):
                    for raw in self._as_list(props.get(key)):
                        field = self._field_payload(raw)
                        if field and not self._is_action_like(field["key"], field["label"]) and field["key"] not in seen:
                            seen.add(field["key"])
                            result.append(field)

        if not any(item["key"] == "id" for item in result):
            result.insert(0, {"key": "id", "label": "ID", "type": "TEXT", "required": False})

        return result[:18]

    def _field_payload(self, raw: Any) -> dict[str, Any]:
        if not isinstance(raw, dict):
            return {}

        key = self._text(
            raw.get("fieldKey")
            or raw.get("columnKey")
            or raw.get("key")
            or raw.get("dataIndex")
            or raw.get("name")
        )
        label = self._text(raw.get("label") or raw.get("title") or raw.get("name") or key)
        if not key:
            key = self._safe_identifier(label)

        key = self._safe_identifier(key)
        if not key:
            return {}

        return {
            "key": key,
            "label": label or key,
            "type": self._normalize_field_type(raw.get("type") or raw.get("valueType") or label or key),
            "required": bool(raw.get("required")),
        }

    def _fallback_fields(self, title: str) -> list[dict[str, Any]]:
        return [
            {"key": "id", "label": "ID", "type": "TEXT", "required": False},
            {"key": "name", "label": title or "名称", "type": "TEXT", "required": True},
            {"key": "status", "label": "状态", "type": "STATUS", "required": False},
            {"key": "createdAt", "label": "创建时间", "type": "DATETIME", "required": False},
            {"key": "remark", "label": "备注", "type": "TEXTAREA", "required": False},
        ]

    def _seed_records(
        self,
        resource_key: str,
        title: str,
        fields: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for index in range(1, 4):
            record: dict[str, Any] = {}
            for field in fields:
                key = str(field.get("key") or "")
                label = str(field.get("label") or key)
                field_type = str(field.get("type") or "TEXT").upper()
                record[key] = self._seed_value(resource_key, title, key, label, field_type, index)
            record.setdefault("id", f"{resource_key}-{index:03d}")
            record.setdefault("createdAt", f"2026-08-{index + 10:02d} 09:30:00")
            records.append(record)
        return records

    def _seed_value(
        self,
        resource_key: str,
        title: str,
        key: str,
        label: str,
        field_type: str,
        index: int,
    ) -> Any:
        lower = (key + " " + label).lower()
        if key == "id" or lower.endswith(" id") or label.upper() == "ID":
            return f"{resource_key}-{index:03d}"
        if "status" in lower or "状态" in label:
            return ["待处理", "处理中", "已完成"][(index - 1) % 3]
        if "date" in lower or "日期" in label:
            return f"2026-08-{index + 10:02d}"
        if "time" in lower or "时间" in label:
            return f"2026-08-{index + 10:02d} 09:30:00"
        if field_type in {"NUMBER", "INTEGER", "DECIMAL", "MONEY"} or any(token in label for token in ["数量", "金额", "价格", "单价", "库存"]):
            return index * 10
        if field_type in {"BOOLEAN", "SWITCH"} or "是否" in label:
            return index % 2 == 1
        if field_type in {"TEXTAREA", "LONG_TEXT"} or any(token in label for token in ["备注", "说明", "描述"]):
            return f"{title}样例说明 {index}"
        return f"{label or title}样例 {index}"

    def _scaffold_files(
        self,
        *,
        generation_root: str,
        build_context: dict[str, Any],
        resources: list[dict[str, Any]],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[DevelopmentCodeFilePayload]:
        root = self._normalize_path(generation_root)
        route_entries = self._resource_route_entries(root, resources, module_app_contract)

        files: list[DevelopmentCodeFilePayload] = [
            self._file(f"{root}/package.json", "CONFIG", "json", "NODEJS_FULLSTACK_ORCHESTRATOR", "Node.js 全栈项目根 package.json。", self._root_package_json(build_context), 1),
            self._file(f"{root}/README.md", "DOC", "markdown", "NODEJS_FULLSTACK_ORCHESTRATOR", "生成项目运行说明。", self._readme(build_context, resources), 2),
            self._file(f"{root}/frontend/package.json", "CONFIG", "json", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vue/Vite 前端 package.json。", self._frontend_package_json(), 3),
            self._file(f"{root}/frontend/index.html", "FRONTEND", "html", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vue 应用入口 HTML。", self._frontend_index_html(build_context), 4),
            self._file(f"{root}/frontend/vite.config.js", "CONFIG", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vite 配置，代理 /api 到 Node.js 后端。", self._vite_config_js(), 5),
            self._file(f"{root}/frontend/src/main.js", "FRONTEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vue 应用 main.js，安装 Vue Router。", self._frontend_main_js(), 6),
            self._file(f"{root}/frontend/src/router/routes.js", "FRONTEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vue Router 路由表，由 MODULE_APP_CONTRACT 标记导航页和动作页。", self._frontend_routes_js(route_entries), 7),
            self._file(f"{root}/frontend/src/App.vue", "FRONTEND", "vue", "NODEJS_FULLSTACK_ORCHESTRATOR", "Vue 应用壳，消费 MODULE_APP_CONTRACT 生成 sidebar/topnav/action hub 和按钮跳转。", self._app_vue(build_context, route_entries, module_app_contract), 8),
            self._file(f"{root}/frontend/src/shared/styles/app.css", "FRONTEND", "css", "NODEJS_FULLSTACK_ORCHESTRATOR", "生成系统全局样式。", self._app_css(), 9),
            self._file(f"{root}/frontend/src/modules/generated/pages/GeneratedHomePage.vue", "FRONTEND", "vue", "NODEJS_FULLSTACK_ORCHESTRATOR", "生成系统首页。", self._generated_home_page_vue(build_context, resources), 10),
            self._file(f"{root}/backend/package.json", "CONFIG", "json", "NODEJS_FULLSTACK_ORCHESTRATOR", "Express 后端 package.json。", self._backend_package_json(), 20),
            self._file(f"{root}/backend/server.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Express 后端启动入口。", self._backend_server_js(), 21),
            self._file(f"{root}/backend/src/app.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Express app 组装。", self._backend_app_js(), 22),
            self._file(f"{root}/backend/src/routes/index.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "后端业务路由聚合。", self._backend_routes_index_js(resources), 23),
            self._file(f"{root}/backend/src/shared/response.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "统一响应工具。", self._backend_response_js(), 24),
            self._file(f"{root}/backend/src/modules/generated/generatedResources.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "由 Development Build Context 生成的资源契约。", self._generated_resources_js(resources), 25),
            self._file(f"{root}/backend/src/modules/generated/generatedManifestTrace.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR+GENERATED_MANIFEST_TRACE", "原型真实化映射追踪 Manifest：页面、动作、接口、localStore 与 smoke case 关系。", self._generated_manifest_trace_js(resources), 26),
            self._file(f"{root}/backend/src/modules/generated/generated-resource.routes.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "通用业务资源 Express Router。", self._generated_resource_routes_js(), 26),
            self._file(f"{root}/backend/src/modules/generated/generated-resource.service.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "通用业务服务层。", self._generated_resource_service_js(), 27),
            self._file(f"{root}/backend/src/modules/generated/generated-resource.repository.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "通用本地数据仓储。", self._generated_resource_repository_js(), 28),
            self._file(f"{root}/backend/src/modules/generated/generated-resource.validation.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "通用输入校验。", self._generated_resource_validation_js(), 29),
            self._file(f"{root}/backend/src/db/seedData.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "后端 seed data。", self._backend_seed_data_js(resources), 30),
            self._file(f"{root}/backend/src/db/localStore.js", "BACKEND", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "内存本地数据层。", self._backend_local_store_js(), 31),
            self._file(f"{root}/database/schema.json", "DATABASE", "json", "NODEJS_FULLSTACK_ORCHESTRATOR", "生成系统数据契约。", self._database_schema_json(resources), 40),
            self._file(f"{root}/database/seed.json", "DATABASE", "json", "NODEJS_FULLSTACK_ORCHESTRATOR", "生成系统 seed 数据。", self._database_seed_json(resources), 41),
            self._file(f"{root}/database/generated-trace.json", "DATABASE", "json", "NODEJS_FULLSTACK_ORCHESTRATOR+GENERATED_MANIFEST_TRACE", "原型合同真实化链路追踪快照。", self._json(self._generated_manifest_trace_payload(resources)), 42),
            self._file(f"{root}/tests/smoke.spec.js", "TEST", "javascript", "NODEJS_FULLSTACK_ORCHESTRATOR", "Node.js 全栈 smoke test。", self._smoke_spec_js(resources), 50),
        ]

        files.extend(self._shared_ui_runtime_files(root))
        files.extend(self._resource_page_files(root, resources, route_entries, module_app_contract))
        return files

    def _resource_route_entries(
        self,
        root: str,
        resources: list[dict[str, Any]],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = []
        seen_paths: set[str] = set()

        for index, resource in enumerate(resources[:24]):
            module_page = self._module_app_page_for_resource(resource, module_app_contract)
            route_path = self._text(module_page.get("routePath") or module_page.get("route")) or self._resource_route_path(resource, index)
            if route_path in seen_paths:
                route_path = f"{route_path.rstrip('/')}-{index + 1}"
            seen_paths.add(route_path)

            component_name = self._resource_page_component_name(resource, index)
            file_path = f"{root}/frontend/src/modules/generated/pages/{component_name}.vue"
            surface_type = self._text(module_page.get("surfaceType")) or self._text(resource.get("surfaceType")) or self._infer_module_surface_type(self._text(resource.get("title")), self._text(resource.get("pageKey")))
            navigation_visible = module_page.get("navigationVisible")
            if navigation_visible is None:
                navigation_visible = resource.get("navigationVisible")
            if navigation_visible is None:
                navigation_visible = surface_type == "MAIN_NAV_PAGE"

            entries.append(
                {
                    "path": route_path,
                    "name": component_name,
                    "title": self._text(module_page.get("pageName")) or self._text(resource.get("title")) or component_name,
                    "component": component_name,
                    "filePath": file_path,
                    "apiSegment": self._text(resource.get("apiSegment")) or "generated-resource",
                    "pageKey": self._text(module_page.get("pageKey")) or self._text(resource.get("pageKey") or resource.get("resourceKey")) or component_name,
                    "surfaceType": surface_type,
                    "navigationVisible": bool(navigation_visible),
                    "entryMode": self._text(module_page.get("entryMode")) or self._text(resource.get("entryMode")) or ("NAVIGATION" if navigation_visible else "ACTION_ROUTE"),
                }
            )

        return entries

    def _resource_page_files(
        self,
        root: str,
        resources: list[dict[str, Any]],
        route_entries: list[dict[str, Any]],
        module_app_contract: dict[str, Any] | None = None,
    ) -> list[DevelopmentCodeFilePayload]:
        files: list[DevelopmentCodeFilePayload] = []
        for index, entry in enumerate(route_entries):
            resource = resources[index] if index < len(resources) else {}
            contract = self._dict_value(resource.get("uiContract") or resource.get("contract"))
            if contract:
                files.extend(self._page_ui_contract_runtime_files(root, resource, entry, index, module_app_contract))
                continue

            files.append(
                self._file(
                    entry["filePath"],
                    "FRONTEND",
                    "vue",
                    "MODULE_PROTOTYPE_PLACEHOLDER",
                    f"{entry['title']} 未纳入模块开发基线，生成占位提示而非伪业务页面。",
                    self._missing_ui_contract_blocker_vue(resource, entry),
                    60 + index,
                )
            )
        return files

    def _resource_route_path(self, resource: dict[str, Any], index: int) -> str:
        raw = (
            self._text(resource.get("routePath"))
            or self._text(resource.get("path"))
            or self._text(resource.get("previewRoute"))
        )
        if not raw:
            segment = self._text(resource.get("apiSegment")) or self._text(resource.get("resourceKey")) or f"resource-{index + 1}"
            raw = "/" + segment.strip("/")
        route = "/" + raw.strip().strip("/")
        return re.sub(r"/+", "/", route) or "/"

    def _resource_page_component_name(self, resource: dict[str, Any], index: int) -> str:
        label = " ".join(
            filter(
                None,
                [
                    self._text(resource.get("title")),
                    self._text(resource.get("pageKey")),
                    self._text(resource.get("routePath")),
                    self._text(resource.get("apiSegment")),
                ],
            )
        )
        lower = label.lower()
        if "入库" in label or "inbound" in lower:
            if any(token in label for token in ["创建", "新建", "新增", "录入"]) or "create" in lower:
                return "InboundOrderCreatePage"
            if any(token in label for token in ["审核", "审批"]) or "approval" in lower or "approve" in lower:
                return "InboundOrderApprovalPage"
            if any(token in label for token in ["数量", "编辑", "修改"]) or "quantity" in lower or "edit" in lower:
                return "InboundOrderQuantityEditPage"
            if any(token in label for token in ["详情", "明细", "查看"]) or "detail" in lower:
                return "InboundOrderDetailPage"
            return "InboundOrderListPage"

        raw = (
            self._text(resource.get("pageKey"))
            or self._text(resource.get("resourceKey"))
            or self._text(resource.get("apiSegment"))
            or self._text(resource.get("title"))
            or f"generated_resource_{index + 1}"
        )
        name = "".join(part[:1].upper() + part[1:] for part in re.split(r"[^0-9a-zA-Z]+", raw) if part)
        if not name:
            name = f"GeneratedResource{index + 1}"
        if name[0].isdigit():
            name = "Generated" + name
        if not name.endswith("Page"):
            name += "Page"
        return name

    def _resource_page_vue(self, resource: dict[str, object], entry: dict[str, object]) -> str:
        contract = self._dict_value(resource.get("uiContract") or resource.get("contract"))
        if contract:
            page_meta = self._ui_contract_page_meta(contract, resource, entry, 0)
            component_name = self._text(entry.get("component") or entry.get("name") or "GeneratedPage")
            base_name = self._safe_identifier(component_name) or "GeneratedPage"
            return self.ui_contract_vue_codegen._render_vue_page(
                contract,
                page_meta,
                f"../api/{base_name}Api.js",
                f"../mock/{base_name}MockData.js",
                f"../types/{base_name}UiContractTypes.js",
            )
        return self._missing_ui_contract_blocker_vue(resource, entry)

    def _a9_10_12_generic_resource_page_vue(self, resource: dict[str, Any], entry: dict[str, Any]) -> str:
        title = self._text(entry.get("title") or resource.get("title") or "业务页面")
        description = self._text(resource.get("description")) or "该页面由 Development Agent 根据需求上下文生成，并接入 Node.js 后端 API。"
        api_segment = self._text(entry.get("apiSegment") or resource.get("apiSegment") or "generated-resource").strip("/")
        page_kind = self._text(resource.get("pageKind") or "GENERIC").upper()
        fields = [field for field in self._as_list(resource.get("fields")) if isinstance(field, dict)]
        if not fields:
            fields = self._fallback_fields(title)
        seed_records = self._as_list(resource.get("seedRecords"))

        page_meta = {
            "title": title,
            "description": description,
            "pageKind": page_kind,
            "apiSegment": api_segment,
            "routePath": entry.get("path"),
        }

        return f"""<template>
  <section class="ucg-page">
    <header class="ucg-page-header">
      <div>
        <p class="ucg-eyebrow">NODE.JS FULLSTACK PAGE</p>
        <h1>{{{{ pageMeta.title }}}}</h1>
        <p>{{{{ pageMeta.description }}}}</p>
      </div>
      <button v-if="isListLike" type="button" @click="loadRows">刷新数据</button>
    </header>

    <div class="ucg-stats">
      <article>
        <span>记录总数</span>
        <strong>{{{{ rows.length }}}}</strong>
      </article>
      <article>
        <span>字段数量</span>
        <strong>{{{{ fields.length }}}}</strong>
      </article>
      <article>
        <span>接口状态</span>
        <strong>{{{{ statusText }}}}</strong>
      </article>
    </div>

    <section v-if="isListLike" class="ucg-card">
      <div class="ucg-toolbar">
        <input v-model="keyword" placeholder="搜索当前页面数据" />
        <button type="button" @click="loadRows">查询</button>
        <button type="button" @click="resetDraft">新建草稿</button>
      </div>
      <p v-if="message" class="ucg-message">{{{{ message }}}}</p>
      <table>
        <thead>
          <tr>
            <th v-for="field in tableFields" :key="field.key">{{{{ field.label || field.key }}}}</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in filteredRows" :key="row.id || row.code || JSON.stringify(row)">
            <td v-for="field in tableFields" :key="field.key">{{{{ displayValue(row[field.key]) }}}}</td>
            <td><button type="button" @click="selectRow(row)">查看</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-else class="ucg-card">
      <h2>{{{{ actionTitle }}}}</h2>
      <p v-if="message" class="ucg-message">{{{{ message }}}}</p>
      <form class="ucg-form" @submit.prevent="submitForm">
        <label v-for="field in editableFields" :key="field.key">
          <span>{{{{ field.label || field.key }}}}</span>
          <textarea v-if="isTextarea(field)" v-model="draft[field.key]" :placeholder="`请输入${{field.label || field.key}}`" />
          <input v-else-if="isNumber(field)" v-model.number="draft[field.key]" type="number" :placeholder="`请输入${{field.label || field.key}}`" />
          <input v-else-if="isDate(field)" v-model="draft[field.key]" type="datetime-local" />
          <select v-else-if="isSelect(field)" v-model="draft[field.key]">
            <option value="">请选择</option>
            <option v-for="option in optionsFor(field)" :key="option" :value="option">{{{{ option }}}}</option>
          </select>
          <input v-else v-model="draft[field.key]" :placeholder="`请输入${{field.label || field.key}}`" />
        </label>
        <div class="ucg-actions">
          <button type="submit">提交到后端</button>
          <button type="button" @click="resetDraft">重置</button>
          <button type="button" @click="loadRows">读取数据</button>
        </div>
      </form>
    </section>
  </section>
</template>

<script setup>
import {{ computed, onMounted, reactive, ref }} from 'vue'

const pageMeta = {self._js(page_meta)}
const fields = {self._js(fields)}
const fallbackRows = {self._js(seed_records)}

const rows = ref([])
const keyword = ref('')
const message = ref('')
const statusText = ref('未检查')
const draft = reactive({{}})

const isListLike = computed(() => {{
  const kind = String(pageMeta.pageKind || '').toUpperCase()
  return kind.includes('LIST') || kind.includes('TABLE') || kind.includes('QUERY') || kind === 'GENERIC'
}})

const actionTitle = computed(() => {{
  const kind = String(pageMeta.pageKind || '').toUpperCase()
  if (kind.includes('WORKFLOW') || kind.includes('APPROVAL')) return '流程处理与审批'
  if (kind.includes('DETAIL')) return '详情信息'
  return '表单录入'
}})

const tableFields = computed(() => fields.slice(0, 8))
const editableFields = computed(() => fields.slice(0, 12))
const filteredRows = computed(() => {{
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((row) => JSON.stringify(row).toLowerCase().includes(q))
}})

function resetDraft() {{
  for (const key of Object.keys(draft)) delete draft[key]
  for (const field of editableFields.value) {{
    draft[field.key] = defaultValue(field)
  }}
}}

function defaultValue(field) {{
  const sample = rows.value[0]?.[field.key]
  if (sample !== undefined && sample !== null) return sample
  const type = String(field.type || '').toUpperCase()
  if (type.includes('NUMBER')) return 0
  return ''
}}

function isNumber(field) {{
  return String(field.type || '').toUpperCase().includes('NUMBER')
}}

function isDate(field) {{
  const type = String(field.type || '').toUpperCase()
  return type.includes('DATE') || type.includes('TIME')
}}

function isTextarea(field) {{
  return String(field.type || '').toUpperCase().includes('TEXTAREA')
}}

function isSelect(field) {{
  const type = String(field.type || '').toUpperCase()
  return type.includes('SELECT') || type.includes('STATUS')
}}

function optionsFor(field) {{
  if (Array.isArray(field.options) && field.options.length) return field.options.map((item) => item.label || item.value || item)
  if (String(field.type || '').toUpperCase().includes('STATUS')) return ['待处理', '已提交', '已审核', '已完成', '异常']
  return ['选项一', '选项二', '选项三']
}}

function displayValue(value) {{
  return value === undefined || value === null || value === '' ? '-' : value
}}

function selectRow(row) {{
  Object.assign(draft, row)
  message.value = '已载入当前记录，可继续编辑或提交。'
}}

async function loadRows() {{
  try {{
    statusText.value = '请求中'
    const response = await fetch(`/api/${{pageMeta.apiSegment}}`)
    if (!response.ok) throw new Error(`HTTP ${{response.status}}`)
    const payload = await response.json()
    const data = payload?.data
    const items = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : Array.isArray(payload?.items) ? payload.items : []
    rows.value = items.length ? items : fallbackRows
    statusText.value = '可访问'
    message.value = items.length ? '已从 Node.js 后端加载数据。' : '后端暂无数据，显示 seed data。'
    resetDraft()
  }} catch (error) {{
    rows.value = fallbackRows
    statusText.value = '本地数据'
    message.value = `接口暂不可用，显示 seed data：${{error.message}}`
    resetDraft()
  }}
}}

async function submitForm() {{
  try {{
    const response = await fetch(`/api/${{pageMeta.apiSegment}}`, {{
      method: 'POST',
      headers: {{ 'Content-Type': 'application/json' }},
      body: JSON.stringify(draft),
    }})
    if (!response.ok) throw new Error(`HTTP ${{response.status}}`)
    message.value = '已提交到 Node.js 后端。'
    await loadRows()
  }} catch (error) {{
    message.value = `提交失败：${{error.message}}`
  }}
}}

onMounted(loadRows)
</script>
""".strip()

    def _frontend_routes_js(self, route_entries: list[dict[str, Any]]) -> str:
        first_path = route_entries[0]["path"] if route_entries else "/"
        routes = [
            f"""  {{
    path: '/',
    redirect: {self._js(first_path)},
  }}"""
        ]

        for index, entry in enumerate(route_entries):
            meta = {
                "title": entry.get("title"),
                "pageKey": entry.get("pageKey"),
                "surfaceType": entry.get("surfaceType"),
                "navigationVisible": bool(entry.get("navigationVisible")),
                "entryMode": entry.get("entryMode"),
                "apiSegment": entry.get("apiSegment"),
            }
            routes.append(
                f"""  {{
    path: {self._js(entry['path'])},
    name: {self._js(entry['name'])},
    title: {self._js(entry['title'])},
    meta: {self._js(meta)},
    component: () => import({self._js(self._relative_import_from_router(entry['filePath']))}),
  }}"""
            )

        routes.append(
            f"""  {{
    path: '/:pathMatch(.*)*',
    redirect: {self._js(first_path)},
  }}"""
        )

        return """import { createRouter, createWebHistory } from 'vue-router'

export const generatedRoutes = [
""" + ",\n".join(routes) + """
]

export const navigationRoutes = generatedRoutes.filter((route) => {
  return route.path && !route.redirect && route.meta?.navigationVisible !== false
})

export const router = createRouter({
  history: createWebHistory(),
  routes: generatedRoutes,
})

export default router
""".strip()

    def _relative_import_from_router(self, file_path: str) -> str:
        normalized = self._normalize_path(file_path)
        marker = "/frontend/src/"
        if marker in normalized:
            return "../" + normalized.split(marker, 1)[1]
        return normalized

    def _root_package_json(self, build_context: dict[str, Any]) -> str:
        project = self._dict_value(build_context.get("project"))
        name = self._safe_package_name(self._text(project.get("projectCode")) or "codemind-generated-app")
        return self._json(
            {
                "name": name,
                "private": True,
                "type": "module",
                "workspaces": ["frontend", "backend"],
                "scripts": {
                    "dev": "concurrently \"npm run dev --workspace frontend\" \"npm run dev --workspace backend\"",
                    "start": "npm run dev",
                    "dev:frontend": "npm run dev --workspace frontend",
                    "dev:backend": "npm run dev --workspace backend",
                    "build": "npm run build --workspace frontend",
                    "test": "node tests/smoke.spec.js",
                    "acceptance": "npm run build && npm test",
                },
                "devDependencies": {
                    "concurrently": "^8.2.2",
                },
            }
        )

    def _frontend_package_json(self) -> str:
        return self._json(
            {
                "name": "codemind-generated-frontend",
                "private": True,
                "type": "module",
                "scripts": {"dev": "vite --host 0.0.0.0 --port 5178", "build": "vite build", "preview": "vite preview --host 0.0.0.0 --port 5178"},
                "dependencies": {"@vitejs/plugin-vue": "^5.1.4", "vite": "^5.4.11", "vue": "^3.5.13", "vue-router": "^4.4.5"},
                "devDependencies": {},
            }
        )

    def _backend_package_json(self) -> str:
        return self._json(
            {
                "name": "codemind-generated-backend",
                "private": True,
                "type": "module",
                "scripts": {"start": "node server.js", "dev": "node server.js"},
                "dependencies": {"cors": "^2.8.5", "express": "^4.21.2"},
                "devDependencies": {},
            }
        )

    def _frontend_index_html(self, build_context: dict[str, Any]) -> str:
        project = self._dict_value(build_context.get("project"))
        title = self._text(project.get("projectName")) or "CodeMind Generated App"
        return f"""<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>{self._escape_html(title)}</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
""".strip()

    def _vite_config_js(self) -> str:
        return """import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5178,
    strictPort: true,
    proxy: {
      '/api': 'http://127.0.0.1:3101',
    },
  },
})
""".strip()

    def _frontend_main_js(self) -> str:
        return """import { createApp } from 'vue'
import App from './App.vue'
import router from './router/routes.js'
import './shared/styles/app.css'

createApp(App).use(router).mount('#app')
""".strip()

    def _app_vue(self, build_context: dict[str, Any], route_entries: list[dict[str, Any]] | None = None, module_app_contract: dict[str, Any] | None = None) -> str:
        project = self._dict_value(build_context.get("project"))
        app_shell = self._module_app_shell(module_app_contract)
        module_name = self._text(app_shell.get("title")) or self._text(project.get("projectName")) or "CodeMind Generated App"
        app_shell["title"] = module_name
        app_shell.setdefault("navigation", [])
        module_edges = self._module_app_edges(module_app_contract)

        return f"""<template>
  <main :class="['app-shell', shellClass]">
    <aside v-if="isSidebar" class="app-sidebar">
      <strong>{{{{ appShell.title }}}}</strong>
      <span>{{{{ shellSubtitle }}}}</span>
      <nav v-if="navigationRoutes.length">
        <button
          v-for="route in navigationRoutes"
          :key="route.path"
          type="button"
          :class="{{ active: currentPath === route.path }}"
          @click="go(route.path)"
        >
          {{{{ route.meta?.title || route.title || route.name }}}}
        </button>
      </nav>
      <p v-else class="shell-hint">该模块没有固定导航，请通过页面动作流转。</p>
    </aside>

    <section class="app-main">
      <header v-if="isTopNav" class="app-topbar">
        <div>
          <strong>{{{{ appShell.title }}}}</strong>
          <span>{{{{ shellSubtitle }}}}</span>
        </div>
        <nav v-if="navigationRoutes.length">
          <button
            v-for="route in navigationRoutes"
            :key="route.path"
            type="button"
            :class="{{ active: currentPath === route.path }}"
            @click="go(route.path)"
          >
            {{{{ route.meta?.title || route.title || route.name }}}}
          </button>
        </nav>
      </header>

      <header v-if="isActionHub" class="app-action-hub">
        <div>
          <small>MODULE ACTION FLOW</small>
          <h1>{{{{ appShell.title }}}}</h1>
          <p>当前模块使用按钮和业务动作串联页面，没有强制固定导航栏。</p>
        </div>
      </header>

      <section v-if="currentActions.length" class="module-action-bar">
        <div>
          <small>页面动作</small>
          <strong>{{{{ currentRouteTitle }}}}</strong>
        </div>
        <div class="module-action-buttons">
          <button
            v-for="edge in currentActions"
            :key="edge.actionId + ':' + edge.targetRoute"
            type="button"
            @click="go(edge.targetRoute)"
          >
            {{{{ edge.actionLabel || edge.label || edge.targetPageName || '打开页面' }}}}
          </button>
        </div>
      </section>

      <section class="app-content">
        <RouterView />
      </section>
    </section>
  </main>
</template>

<script setup>
import {{ computed }} from 'vue'
import {{ RouterView, useRoute, useRouter }} from 'vue-router'
import {{ navigationRoutes }} from './router/routes.js'

const appShell = {self._js(app_shell)}
const navigationEdges = {self._js(module_edges)}

const route = useRoute()
const router = useRouter()

const currentPath = computed(() => route.path)
const currentPageKey = computed(() => String(route.meta?.pageKey || ''))
const currentRouteTitle = computed(() => String(route.meta?.title || route.title || route.name || '当前页面'))

const shellLayout = computed(() => String(appShell.layout || 'SIDEBAR').toUpperCase())
const isSidebar = computed(() => shellLayout.value === 'SIDEBAR')
const isTopNav = computed(() => shellLayout.value === 'TOP_NAV')
const isActionHub = computed(() => shellLayout.value === 'ACTION_HUB' || shellLayout.value === 'NO_NAV')
const shellClass = computed(() => `layout-${{shellLayout.value.toLowerCase().replace(/_/g, '-')}}`)
const shellSubtitle = computed(() => {{
  if (isTopNav.value) return 'Top Navigation'
  if (isActionHub.value) return 'Action Flow'
  return 'Node.js Fullstack'
}})

const currentActions = computed(() => {{
  return navigationEdges.filter((edge) => {{
    const sourcePageKey = String(edge.sourcePageKey || '')
    const sourceRoute = normalizeRoute(edge.sourceRoute || '')
    const targetRoute = normalizeRoute(edge.targetRoute || '')
    if (!targetRoute) return false
    return sourcePageKey === currentPageKey.value || sourceRoute === normalizeRoute(route.path)
  }})
}})

function normalizeRoute(value) {{
  const text = String(value || '').trim()
  if (!text) return ''
  return '/' + text.replace(/^\\/+|\\/+$/g, '')
}}

function go(path) {{
  const target = normalizeRoute(path)
  if (target) router.push(target)
}}
</script>
""".strip()

    def _app_css(self) -> str:
        return """:root {
  color: #0f172a;
  background: #eef4ff;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

* { box-sizing: border-box; }

body { margin: 0; min-width: 1180px; min-height: 100vh; background: #eef4ff; }

button, input, select, textarea { font: inherit; }

.app-shell {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef4ff 0%, #f8fafc 100%);
}

.app-shell.layout-sidebar {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
}

.app-shell.layout-top-nav,
.app-shell.layout-action-hub,
.app-shell.layout-no-nav {
  display: block;
}

.app-sidebar {
  padding: 28px 22px;
  color: #e2e8f0;
  background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  min-height: 100vh;
}

.app-sidebar strong,
.app-topbar strong {
  display: block;
  font-size: 19px;
  color: inherit;
}

.app-sidebar span,
.app-topbar span {
  display: block;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.app-sidebar nav {
  display: grid;
  gap: 8px;
  margin-top: 24px;
}

.app-sidebar button,
.app-topbar button,
.module-action-bar button {
  border: 1px solid rgba(37, 99, 235, .22);
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  font-weight: 800;
}

.app-sidebar button {
  text-align: left;
  background: rgba(255,255,255,.08);
  color: #e2e8f0;
  border-color: rgba(255,255,255,.16);
}

.app-sidebar button:hover,
.app-sidebar button.active {
  background: rgba(37, 99, 235, .85);
  color: #fff;
  border-color: rgba(255,255,255,.25);
}

.shell-hint {
  margin-top: 24px;
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.7;
}

.app-main {
  min-width: 0;
}

.app-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 28px;
  background: rgba(255, 255, 255, .92);
  border-bottom: 1px solid #dbeafe;
  backdrop-filter: blur(14px);
}

.app-topbar nav {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.app-topbar button {
  background: #eff6ff;
  color: #1d4ed8;
}

.app-topbar button.active,
.app-topbar button:hover,
.module-action-bar button:hover {
  background: #2563eb;
  color: #fff;
}

.app-action-hub {
  padding: 28px 28px 0;
}

.app-action-hub > div,
.module-action-bar,
.generated-hero,
.generated-card,
.ucg-card {
  border: 1px solid #dbeafe;
  border-radius: 22px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 18px 45px rgba(15,23,42,.08);
}

.app-action-hub > div {
  padding: 24px;
}

.app-action-hub small,
.module-action-bar small {
  color: #2563eb;
  font-weight: 900;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.app-action-hub h1 {
  margin: 6px 0;
}

.module-action-bar {
  margin: 24px 28px 0;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.module-action-bar strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
}

.module-action-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.module-action-bar button {
  background: #eff6ff;
  color: #1d4ed8;
}

.app-content {
  min-width: 0;
  padding: 28px;
}

/* A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM: generated app shell inherits UI_CONTRACT runtime visual system. */
.app-content .generated-runtime-page,
.app-content .runtime-shell {
  border-radius: 28px;
}

.generated-runtime-page {
  min-height: calc(100vh - 56px);
}

.generated-runtime-page :deep(.runtime-shell) {
  box-shadow: 0 22px 70px rgba(15, 23, 42, .10);
}

.generated-home { display: grid; gap: 18px; }

.generated-hero { padding: 28px; background: linear-gradient(135deg, #ffffff 0%, #eff6ff 100%); }

.generated-hero h1 { margin: 8px 0 8px; font-size: 30px; }

.generated-hero p { margin: 0; color: #64748b; }

.generated-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }

.generated-card { padding: 20px; background: #fff; }

.generated-card small { color: #2563eb; font-weight: 900; }

.generated-card strong { display: block; margin-top: 8px; font-size: 24px; }

.generated-card span { color: #64748b; }

.ucg-page { display: grid; gap: 18px; color: #0f172a; }
.ucg-message { padding: 12px 14px; border-radius: 14px; background: #ecfdf5; color: #047857; font-weight: 800; }
""".strip()

    def _generated_home_page_vue(self, build_context: dict[str, Any], resources: list[dict[str, Any]]) -> str:
        project = self._dict_value(build_context.get("project"))
        scope = self._dict_value(build_context.get("scope"))
        resource_cards = ",\n  ".join(
            self._js({"title": item.get("title"), "api": f"/api/{item.get('apiSegment')}", "fields": len(item.get("fields") or [])})
            for item in resources[:12]
        )
        return f"""<template>
  <section class="generated-home">
    <div class="generated-hero">
      <small>CodeMind Generated System</small>
      <h1>{self._escape_html(self._text(project.get("projectName")) or "Node.js 业务系统")}</h1>
      <p>{self._escape_html(self._text(scope.get("scopeSummary")) or "系统已根据用户确认的需求和原型生成，可继续构建、验收和修复。")}</p>
    </div>

    <div class="generated-grid">
      <article v-for="item in resources" :key="item.api" class="generated-card">
        <small>{{ item.api }}</small>
        <strong>{{ item.title }}</strong>
        <span>{{ item.fields }} 个字段已接入 Node.js API</span>
      </article>
    </div>
  </section>
</template>

<script setup>
const resources = [
  {resource_cards}
]
</script>
""".strip()

    def _backend_server_js(self) -> str:
        return """import app from './src/app.js'

const port = Number(process.env.PORT || 3101)

app.listen(port, '0.0.0.0', () => {
  console.log(`[CodeMind] Generated backend listening on http://127.0.0.1:${port}`)
})
""".strip()

    def _backend_app_js(self) -> str:
        return """import cors from 'cors'
import express from 'express'
import { registerGeneratedRoutes } from './routes/index.js'
import { sendError } from './shared/response.js'

const app = express()

app.use(cors())
app.use(express.json({ limit: '2mb' }))

registerGeneratedRoutes(app)

app.use((error, request, response, next) => {
  console.error(error)
  return sendError(response, error, error.status || 500)
})

export default app
""".strip()

    def _backend_routes_index_js(self, resources: list[dict[str, object]]) -> str:
        # A9.10.14.4.3.15.4.8.4 Runtime Fidelity Gate Closure:
        # every generated module must use the canonical generatedResources router.
        # Industry-specific shortcuts can improve prototypes later, but they must not bypass
        # fullstackBinding/actionBindings/dataBindings in the generated fullstack project.
        return self._a9_10_12_generic_backend_routes_index_js(resources)

    def _a9_10_12_generic_backend_routes_index_js(self, resources: list[dict[str, Any]]) -> str:
        mounts: list[str] = []
        for item in resources:
            key = self._text(item.get("resourceKey"))
            api_segment = self._text(item.get("apiSegment")).strip("/")
            route_path = self._text(item.get("routePath")).strip("/")
            if not key:
                continue
            for segment in self._dedupe([api_segment, route_path]):
                if not segment:
                    continue
                mounts.append(
                    f"mountResource({self._js(segment)}, resourceByKey[{self._js(key)}])"
                )
        mount_lines = "\n".join(mounts) or "// No generated resources were available."
        return f"""import express from 'express'
import {{ generatedResources }} from '../modules/generated/generatedResources.js'
import {{ createGeneratedResourceRouter }} from '../modules/generated/generated-resource.routes.js'
import {{ sendOk }} from '../shared/response.js'

const router = express.Router()
const resourceByKey = Object.fromEntries(generatedResources.map((item) => [item.resourceKey, item]))

function normalizeSegment(value) {{
  return String(value || '').replace(/^\\/+|\\/+$/g, '')
}}

function mountResource(segment, resource) {{
  const normalized = normalizeSegment(segment)
  if (!normalized || !resource) return
  router.use('/' + normalized, createGeneratedResourceRouter(resource))
}}

router.get('/health', (request, response) => {{
  return sendOk(response, {{
    service: 'codemind-generated-nodejs',
    status: 'UP',
    resourceCount: generatedResources.length,
    generatedAt: new Date().toISOString(),
  }})
}})

{mount_lines}

export function registerGeneratedRoutes(app) {{
  app.get('/health', (request, response) => {{
    return sendOk(response, {{
      service: 'codemind-generated-backend',
      status: 'UP',
      resourceCount: generatedResources.length,
      generatedAt: new Date().toISOString(),
    }})
  }})

  app.get('/api/health', (request, response) => {{
    return sendOk(response, {{
      service: 'codemind-generated-backend',
      status: 'UP',
      resourceCount: generatedResources.length,
      generatedAt: new Date().toISOString(),
    }})
  }})

  app.use('/api', router)
  return app
}}

export default router
""".strip()

    def _backend_response_js(self) -> str:
        return """function isExpressResponse(value) {
  return value && typeof value.status === 'function' && typeof value.json === 'function'
}

function normalizeError(errorOrMessage) {
  if (errorOrMessage instanceof Error) {
    return {
      message: errorOrMessage.message || 'Internal server error',
      details: errorOrMessage.stack,
    }
  }

  if (typeof errorOrMessage === 'object' && errorOrMessage !== null) {
    return {
      message: errorOrMessage.message || 'Request failed',
      details: errorOrMessage,
    }
  }

  return {
    message: errorOrMessage || 'Request failed',
    details: undefined,
  }
}

export function ok(target = null, data = null, message = 'OK', meta = undefined) {
  const body = {
    success: true,
    code: 0,
    message,
    data: isExpressResponse(target) ? data : target,
  }

  if (meta !== undefined) {
    body.meta = meta
  }

  if (isExpressResponse(target)) {
    return target.json(body)
  }

  return body
}

export function fail(target = null, message = 'Request failed', status = 400, details = undefined) {
  const body = {
    success: false,
    code: status,
    message,
  }

  if (details !== undefined) {
    body.details = details
  }

  if (isExpressResponse(target)) {
    return target.status(status).json(body)
  }

  return body
}

export function page(items = [], total = 0, pageNo = 1, pageSize = 20) {
  return ok({
    items,
    total,
    pageNo,
    pageSize,
  })
}

export function sendSuccess(res, data = null, message = 'OK', status = 200, meta = undefined) {
  const body = {
    success: true,
    code: 0,
    message,
    data,
  }

  if (meta !== undefined) {
    body.meta = meta
  }

  return res.status(status).json(body)
}

export const sendOk = sendSuccess

export function sendError(res, errorOrMessage = 'Request failed', status = 500, details = undefined) {
  const normalized = normalizeError(errorOrMessage)
  const body = {
    success: false,
    code: status,
    message: normalized.message,
  }

  if (details !== undefined) {
    body.details = details
  } else if (normalized.details !== undefined) {
    body.details = normalized.details
  }

  return res.status(status).json(body)
}

export function sendPaginated(res, items = [], total = 0, pageNo = 1, pageSize = 20, message = 'OK') {
  return sendSuccess(res, {
    items,
    total,
    pageNo,
    pageSize,
  }, message)
}

export function badRequest(res, message = 'Bad request', details = undefined) {
  return sendError(res, message, 400, details)
}

export function notFound(res, message = 'Not found', details = undefined) {
  return sendError(res, message, 404, details)
}

export function serverError(res, error = 'Internal server error', details = undefined) {
  return sendError(res, error, 500, details)
}

export default {
  ok,
  fail,
  page,
  sendSuccess,
  sendOk,
  sendError,
  sendPaginated,
  badRequest,
  notFound,
  serverError,
}
""".strip()

    # A9.10.14.4.3.15.4.8.5 GENERATED_MANIFEST_TRACE_CLOSURE
    def _generated_resources_js(self, resources: list[dict[str, Any]]) -> str:
        traced_resources = [self._resource_with_generated_trace(item, index) for index, item in enumerate(resources or [])]
        manifest = self._generated_manifest_trace_payload(traced_resources)
        return (
            "export const generatedResources = " + self._js(traced_resources) + "\n\n"
            + "export const generatedManifestTrace = " + self._js(manifest) + "\n\n"
            + "export function findGeneratedTraceByRoute(routePath) {\n"
            + "  const normalized = normalizeRoute(routePath)\n"
            + "  return generatedManifestTrace.pages.find((page) => normalizeRoute(page.routePath) === normalized) || null\n"
            + "}\n\n"
            + "export function findGeneratedTraceByAction(actionId) {\n"
            + "  const target = String(actionId || '').trim()\n"
            + "  if (!target) return null\n"
            + "  for (const page of generatedManifestTrace.pages || []) {\n"
            + "    const action = (page.actions || []).find((item) => item.actionId === target)\n"
            + "    if (action) return { page, action }\n"
            + "  }\n"
            + "  return null\n"
            + "}\n\n"
            + "function normalizeRoute(value) {\n"
            + "  const text = String(value || '').trim()\n"
            + "  if (!text) return ''\n"
            + "  return '/' + text.replace(/^\\/+|\\/+$/g, '')\n"
            + "}\n"
        )

    def _generated_manifest_trace_js(self, resources: list[dict[str, Any]]) -> str:
        traced_resources = [self._resource_with_generated_trace(item, index) for index, item in enumerate(resources or [])]
        manifest = self._generated_manifest_trace_payload(traced_resources)
        return (
            "export const generatedManifestTrace = " + self._js(manifest) + "\n\n"
            + "export function assertGeneratedManifestTrace() {\n"
            + "  if (!generatedManifestTrace || !Array.isArray(generatedManifestTrace.pages) || generatedManifestTrace.pages.length === 0) {\n"
            + "    throw new Error('generatedManifestTrace.pages must not be empty')\n"
            + "  }\n"
            + "  for (const page of generatedManifestTrace.pages) {\n"
            + "    for (const key of ['pageKey', 'routePath', 'componentFile', 'apiSegment', 'localStoreBucket', 'smokeCase']) {\n"
            + "      if (!page[key]) throw new Error(`generated trace page missing ${key}`)\n"
            + "    }\n"
            + "  }\n"
            + "  return true\n"
            + "}\n"
        )

    def _resource_with_generated_trace(self, resource: dict[str, Any], index: int) -> dict[str, Any]:
        item = dict(resource or {})
        resource_key = self._text(item.get("resourceKey")) or f"resource{index + 1}"
        api_segment = self._text(item.get("apiSegment")).strip("/") or resource_key
        page_key = self._text(item.get("pageKey")) or resource_key
        route_path = self._normalize_route(self._text(item.get("routePath")) or "/" + api_segment)
        component_name = self._resource_page_component_name(item, index)
        component_file = f"frontend/src/modules/generated/pages/{component_name}.vue"
        api_client_file = f"frontend/src/modules/generated/api/{component_name}Api.js"
        fields = [field for field in self._as_list(item.get("fields")) if isinstance(field, dict)]
        action_bindings = [action for action in self._as_list(item.get("actionBindings")) if isinstance(action, dict)]

        action_trace = []
        for action_index, action in enumerate(action_bindings):
            action_id = self._text(action.get("actionId") or action.get("id") or f"action_{action_index + 1}")
            kind = self._text(action.get("kind") or action.get("actionType") or "ACTION_COMMAND")
            method = self._text(action.get("method") or "POST").upper()
            path = self._text(action.get("path") or f"/api/{api_segment}")
            action_trace.append(
                {
                    "actionId": action_id,
                    "label": self._text(action.get("label") or action_id),
                    "kind": kind,
                    "method": method,
                    "path": path,
                    "frontendEvent": "@prototype-action",
                    "frontendHandler": "handlePrototypeAction -> runUiAction/fetchUiRows",
                    "apiClientFile": api_client_file,
                    "backendRouteFile": "backend/src/modules/generated/generated-resource.routes.js",
                    "backendServiceFile": "backend/src/modules/generated/generated-resource.service.js",
                    "repositoryFile": "backend/src/modules/generated/generated-resource.repository.js",
                    "localStoreBucket": resource_key,
                    "smokeCase": f"verifyActionBindings:{page_key}:{action_id}",
                    "source": "PAGE_UI_CONTRACT.actionId",
                }
            )

        trace = {
            "traceVersion": PATCH_MARKER,
            "sourceArtifactType": "MODULE_PROTOTYPE_SNAPSHOT",
            "sourcePageContractType": "PAGE_UI_CONTRACT",
            "pageKey": page_key,
            "pageTitle": self._text(item.get("title") or page_key),
            "routePath": route_path,
            "componentName": component_name,
            "componentFile": component_file,
            "apiClientFile": api_client_file,
            "apiSegment": api_segment,
            "apiBasePath": f"/api/{api_segment}",
            "localStoreBucket": resource_key,
            "seedFile": "backend/src/db/seedData.js",
            "runtimeRenderer": "UiContractPreviewRuntime",
            "runtimePolicy": "reuse PAGE_UI_CONTRACT runtime; replace mockData/mockScenarios with real API and localStore binding",
            "fields": [
                {
                    "fieldKey": self._text(field.get("key") or field.get("fieldKey")),
                    "label": self._text(field.get("label") or field.get("key") or field.get("fieldKey")),
                    "payload": True,
                    "localStoreField": self._text(field.get("key") or field.get("fieldKey")),
                }
                for field in fields
                if self._text(field.get("key") or field.get("fieldKey"))
            ],
            "actions": action_trace,
            "smokeCase": f"verifyResource:{api_segment}",
            "acceptanceContracts": [
                "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
                "GENERATED_MANIFEST_TRACE_CONTRACT",
            ],
        }

        item["generatedTrace"] = trace
        item["codegenTrace"] = trace
        binding = self._dict_value(item.get("fullstackBinding"))
        if binding:
            binding["manifestTrace"] = {
                "traceVersion": trace["traceVersion"],
                "pageKey": trace["pageKey"],
                "routePath": trace["routePath"],
                "componentFile": trace["componentFile"],
                "apiSegment": trace["apiSegment"],
                "localStoreBucket": trace["localStoreBucket"],
                "smokeCase": trace["smokeCase"],
            }
            item["fullstackBinding"] = binding
        return item

    def _generated_manifest_trace_payload(self, resources: list[dict[str, Any]]) -> dict[str, Any]:
        traced_resources = []
        for index, item in enumerate(resources or []):
            if not isinstance(item, dict):
                continue
            if isinstance(item.get("generatedTrace"), dict):
                traced_resources.append(item)
            else:
                traced_resources.append(self._resource_with_generated_trace(item, index))

        pages = [
            item.get("generatedTrace")
            for item in traced_resources
            if isinstance(item.get("generatedTrace"), dict)
        ]
        return {
            "manifestVersion": PATCH_MARKER,
            "source": "MODULE_PROTOTYPE_SNAPSHOT",
            "contract": "confirmed PAGE_UI_CONTRACT -> generated fullstack code mapping",
            "resourceCount": len(traced_resources),
            "pageCount": len(pages),
            "pages": pages,
            "globalFiles": {
                "generatedResources": "backend/src/modules/generated/generatedResources.js",
                "generatedManifestTrace": "backend/src/modules/generated/generatedManifestTrace.js",
                "routes": "backend/src/routes/index.js",
                "localStore": "backend/src/db/localStore.js",
                "seedData": "backend/src/db/seedData.js",
                "smokeSpec": "tests/smoke.spec.js",
                "databaseTrace": "database/generated-trace.json",
            },
            "acceptanceContracts": [
                "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
                "GENERATED_MANIFEST_TRACE_CONTRACT",
            ],
        }

    def _generated_resource_routes_js(self) -> str:
        return """import express from 'express'
import { fail, ok } from '../../shared/response.js'
import { createGeneratedResourceService } from './generated-resource.service.js'

export function createGeneratedResourceRouter(resource) {
  const router = express.Router()
  const service = createGeneratedResourceService(resource)

  router.get('/', (request, response) => {
    response.json(ok(service.list(request.query)))
  })

  router.post('/', (request, response) => {
    const result = service.create(request.body || {})
    response.status(201).json(ok(result, 'Created'))
  })

  router.get('/:id', (request, response) => {
    const result = service.get(request.params.id)
    if (!result) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(result))
  })

  router.put('/:id', (request, response) => {
    const result = service.update(request.params.id, request.body || {})
    if (!result) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(result, 'Updated'))
  })

  router.delete('/:id', (request, response) => {
    const removed = service.remove(request.params.id)
    if (!removed) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(removed, 'Deleted'))
  })

  router.post('/:actionId', (request, response) => {
    response.json(ok(service.runAction(request.params.actionId, request.body || {}), 'Action completed'))
  })

  return router
}
""".strip()

    def _generated_resource_service_js(self) -> str:
        return """import { getCollection } from './generated-resource.repository.js'
import { validatePayload } from './generated-resource.validation.js'

export function createGeneratedResourceService(resource) {
  const collection = getCollection(resource)

  function list(query = {}) {
    const q = String(query.q || query.keyword || '').trim().toLowerCase()
    let items = collection.items.filter((item) => !item.__deleted)

    if (q) {
      items = items.filter((item) => Object.values(item).some((value) => String(value ?? '').toLowerCase().includes(q)))
    }

    for (const field of resource.fields || []) {
      const value = query[field.key]
      if (value !== undefined && value !== null && String(value).trim() !== '') {
        items = items.filter((item) => String(item[field.key] ?? '') === String(value))
      }
    }

    return {
      items,
      total: items.length,
      resource: {
        resourceKey: resource.resourceKey,
        title: resource.title,
        fields: resource.fields,
      },
    }
  }

  function get(id) {
    return collection.items.find((item) => String(item.id) === String(id) && !item.__deleted) || null
  }

  function create(payload) {
    const errors = validatePayload(resource, payload)
    if (errors.length) {
      const error = new Error('Validation failed: ' + errors.join('; '))
      error.status = 400
      throw error
    }

    const now = new Date().toISOString()
    const record = {
      id: payload.id || `${resource.resourceKey}-${collection.nextId++}`,
      ...payload,
      createdAt: payload.createdAt || now,
      updatedAt: now,
    }
    collection.items.unshift(record)
    return record
  }

  function update(id, payload) {
    const current = get(id)
    if (!current) return null
    Object.assign(current, payload, { updatedAt: new Date().toISOString() })
    return current
  }

  function remove(id) {
    const current = get(id)
    if (!current) return null
    current.__deleted = true
    current.updatedAt = new Date().toISOString()
    return current
  }

  function runAction(actionId, payload) {
    const action = String(actionId || '').toLowerCase()
    if (action.includes('create') || action.includes('save') || action.includes('submit') || action.includes('新增') || action.includes('保存') || action.includes('提交')) {
      const form = payload.form && typeof payload.form === 'object' ? payload.form : payload
      return create(form)
    }
    return {
      actionId,
      payload,
      handledAt: new Date().toISOString(),
      resourceKey: resource.resourceKey,
    }
  }

  return { list, get, create, update, remove, runAction }
}
""".strip()

    def _generated_resource_repository_js(self) -> str:
        return """import { collection as localStoreCollection } from '../../db/localStore.js'

const counters = new Map()

export function getCollection(resource) {
  const key = resource.resourceKey || resource.apiSegment || 'resource'
  const items = localStoreCollection(key)

  if (!counters.has(key)) {
    counters.set(key, 1000)
  }

  return {
    get items() {
      return items
    },
    get nextId() {
      return counters.get(key) || 1000
    },
    set nextId(value) {
      counters.set(key, Number(value) || 1000)
    },
  }
}
""".strip()

    def _generated_resource_validation_js(self) -> str:
        return """export function validatePayload(resource, payload = {}) {
  const errors = []

  for (const field of resource.fields || []) {
    if (!field.required) continue
    const value = payload[field.key]
    if (value === undefined || value === null || String(value).trim() === '') {
      errors.push(`${field.label || field.key} 不能为空`)
    }
  }

  return errors
}
""".strip()

    def _backend_seed_data_js(self, resources: list[dict[str, object]]) -> str:
        # A9.10.14.4.3.15.4.8.4: seed data must follow generatedResources.resourceKey
        # so the runtime repository can read the same collection through localStore.
        return self._a9_10_12_generic_backend_seed_data_js(resources)

    def _a9_10_12_generic_backend_seed_data_js(self, resources: list[dict[str, Any]]) -> str:
        data = {str(item.get("resourceKey")): item.get("seedRecords") or [] for item in resources}
        return "export const seedData = " + self._js(data) + "\n"

    def _backend_local_store_js(self) -> str:
        return """import { seedData } from './seedData.js'

const store = new Map()

export function collection(name) {
  if (!store.has(name)) {
    store.set(name, JSON.parse(JSON.stringify(seedData[name] || [])))
  }
  return store.get(name)
}

export function resetStore() {
  store.clear()
}
""".strip()

    def _database_schema_json(self, resources: list[dict[str, Any]]) -> str:
        traced_resources = [self._resource_with_generated_trace(item, index) for index, item in enumerate(resources or [])]
        return self._json(
            {
                "schema": "codemind_generated_nodejs_fullstack_schema",
                "schemaVersion": PATCH_MARKER,
                "resources": [
                    {
                        "resourceKey": item.get("resourceKey"),
                        "apiSegment": item.get("apiSegment"),
                        "title": item.get("title"),
                        "fields": item.get("fields"),
                        "generatedTrace": item.get("generatedTrace"),
                    }
                    for item in traced_resources
                ],
                "generatedManifestTrace": self._generated_manifest_trace_payload(traced_resources),
            }
        )

    def _database_seed_json(self, resources: list[dict[str, Any]]) -> str:
        return self._json(
            {
                str(item.get("resourceKey")): item.get("seedRecords") or []
                for item in resources
            }
        )

    def _smoke_spec_js(self, resources: list[dict[str, object]]) -> str:
        # A9.10.14.4.3.15.4.8.4: smoke tests must traverse generatedResources and
        # verify non-navigation actionBindings. Domain-specific smoke can be added later
        # as an extension, not as a replacement for the binding contract smoke.
        return self._a9_10_12_generic_smoke_spec_js(resources)


    def _a9_10_12_generic_smoke_spec_js(self, resources: list[dict[str, Any]]) -> str:
        return f"""import http from 'node:http'
import app from '../backend/src/app.js'
import {{ generatedResources }} from '../backend/src/modules/generated/generatedResources.js'

const server = app.listen(0)
const port = await new Promise((resolve) => {{
  server.once('listening', () => resolve(server.address().port))
}})

try {{
  const health = await request('/api/health')
  assert(health.success, 'health endpoint must return success')

  assert(Array.isArray(generatedResources), 'generatedResources must be an array')
  assert(generatedResources.length > 0, 'generatedResources must not be empty')

  for (const resource of generatedResources) {{
    await verifyResource(resource)
  }}

  console.log(`Smoke tests passed for ${{generatedResources.length}} generated resources`)
}} finally {{
  await new Promise((resolve) => server.close(resolve))
}}

async function verifyResource(resource) {{
  const segment = String(resource.apiSegment || resource.resourceKey || '').replace(/^\\/+|\\/+$/g, '')
  assert(segment, 'resource apiSegment must exist')

  const basePath = `/api/${{segment}}`
  const list = await request(basePath)
  assert(list.success, `${{segment}} list endpoint must return success`)
  assert(Array.isArray(list.data.items), `${{segment}} list endpoint must return data.items array`)

  const payload = samplePayload(resource)
  const created = await request(basePath, {{
    method: 'POST',
    body: JSON.stringify(payload),
  }})
  assert(created.success, `${{segment}} create endpoint must return success`)
  assert(created.data.id, `${{segment}} created record must have id`)

  const reread = await request(basePath)
  assert(reread.success, `${{segment}} reread endpoint must return success`)
  assert(reread.data.items.some((item) => String(item.id) === String(created.data.id)), `${{segment}} created record must be readable`)

  await verifyActionBindings(resource, created.data)

  const removed = await request(`${{basePath}}/${{encodeURIComponent(created.data.id)}}`, {{ method: 'DELETE' }})
  assert(removed.success, `${{segment}} delete endpoint must return success`)
}}

async function verifyActionBindings(resource, record) {{
  const bindings = Array.isArray(resource.actionBindings) ? resource.actionBindings : []
  for (const binding of bindings) {{
    const kind = String(binding.kind || '').toUpperCase()
    const method = String(binding.method || '').toUpperCase()
    if (kind === 'NAVIGATE' || method === 'ROUTE') continue

    if (kind === 'QUERY_DATA' || method === 'GET') {{
      const result = await request(normalizeApiPath(binding.path, resource))
      assert(result.success, `${{binding.actionId}} query action must return success`)
      continue
    }}

    if (kind === 'DELETE_ROW' || method === 'DELETE') {{
      continue
    }}

    if (kind === 'UPDATE_RECORD' || method === 'PUT' || method === 'PATCH') {{
      const path = `${{basePathFor(resource)}}/${{encodeURIComponent(record.id)}}`
      const result = await request(path, {{
        method: 'PUT',
        body: JSON.stringify({{ ...record, __smokeUpdated: true }}),
      }})
      assert(result.success, `${{binding.actionId}} update action must return success`)
      continue
    }}

    const path = normalizeApiPath(binding.path, resource)
    const result = await request(path, {{
      method: method === 'POST' ? 'POST' : 'POST',
      body: JSON.stringify({{ actionId: binding.actionId, form: samplePayload(resource), record }}),
    }})
    assert(result.success, `${{binding.actionId}} command action must return success`)
  }}
}}

function samplePayload(resource) {{
  const payload = {{}}
  for (const field of resource.fields || []) {{
    if (!field?.key || field.key === 'id') continue
    payload[field.key] = sampleValue(field, resource)
  }}
  if (!Object.keys(payload).length) {{
    payload.name = `${{resource.title || resource.resourceKey}} Smoke Test Record`
    payload.status = '已创建'
  }}
  return payload
}}

function sampleValue(field, resource) {{
  const key = String(field.key || '').toLowerCase()
  const label = String(field.label || field.key || '')
  const type = String(field.type || '').toUpperCase()
  if (type.includes('NUMBER') || type.includes('INTEGER') || /数量|金额|价格|库存/.test(label)) return 1
  if (type.includes('BOOLEAN') || /是否/.test(label)) return true
  if (type.includes('DATE') && !type.includes('TIME')) return '2026-08-12'
  if (type.includes('TIME') || /时间/.test(label)) return '2026-08-12 09:30:00'
  if (type.includes('STATUS') || /状态/.test(label)) return '已创建'
  if (key.includes('code') || /编号/.test(label)) return `${{resource.resourceKey || 'resource'}}-SMOKE`
  return `${{label || field.key}} Smoke Test`
}}

function normalizeApiPath(path, resource) {{
  const text = String(path || '').trim()
  if (!text) return basePathFor(resource)
  if (text.startsWith('/api/')) return text
  if (text.startsWith('/')) return `/api${{text}}`
  return `/api/${{text.replace(/^\\/+|\\/+$/g, '')}}`
}}

function basePathFor(resource) {{
  const segment = String(resource.apiSegment || resource.resourceKey || '').replace(/^\\/+|\\/+$/g, '')
  return `/api/${{segment}}`
}}

function request(path, options = {{}}) {{
  return new Promise((resolve, reject) => {{
    const req = http.request({{
      hostname: '127.0.0.1',
      port,
      path,
      method: options.method || 'GET',
      headers: {{ 'Content-Type': 'application/json' }},
    }}, (res) => {{
      let text = ''
      res.setEncoding('utf8')
      res.on('data', (chunk) => {{ text += chunk }})
      res.on('end', () => {{
        try {{
          const body = text ? JSON.parse(text) : {{}}
          if (res.statusCode >= 400) reject(new Error(body?.error?.message || text || `HTTP ${{res.statusCode}}`))
          else resolve(body)
        }} catch (error) {{
          reject(error)
        }}
      }})
    }})
    req.on('error', reject)
    if (options.body) req.write(options.body)
    req.end()
  }})
}}

function assert(condition, message) {{
  if (!condition) throw new Error(message)
}}
""".strip()

    def _readme(self, build_context: dict[str, Any], resources: list[dict[str, Any]]) -> str:
        project = self._dict_value(build_context.get("project"))
        project_name = self._text(project.get("projectName")) or "CodeMind Generated App"
        resource_lines = "\n".join(
            f"- `{item.get('title')}`: `/api/{item.get('apiSegment')}`"
            for item in resources
        )
        return f"""# {project_name}

这是 CodeMind A9.10.7 根据 Development Build Context V1 生成的 Node.js 全栈项目。

## 运行

```bash
npm install
npm run build
npm test
npm start
```

## 资源接口

{resource_lines or "- 暂无资源"}

## 说明

前端使用 Vue 3 + Vite，后端使用 Express + 本地内存数据层。业务页面来自用户已确认的 UI_CONTRACT，接口和数据层由 Development Agent 自动生成。
""".strip()

    def _default_resource(self, build_context: dict[str, Any]) -> dict[str, Any]:
        scope = self._dict_value(build_context.get("scope"))
        title = self._text(scope.get("nodeName")) or "业务资源"
        fields = self._fallback_fields(title)
        return {
            "resourceKey": "generatedResource",
            "apiSegment": "generated-resource",
            "title": title,
            "description": "默认业务资源。",
            "pageKind": "GENERIC",
            "fields": fields,
            "seedRecords": self._seed_records("generatedResource", title, fields),
        }

    def _root_components(self, contract: dict[str, Any]) -> list[dict[str, Any]]:
        page = self._dict_value(contract.get("page"))
        content = self._dict_value(contract.get("content"))
        if not page:
            page = self._dict_value(content.get("page"))

        for key in ("componentTree", "components", "children"):
            value = page.get(key) if isinstance(page, dict) else None
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]

        value = contract.get("componentTree")
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
        return []

    def _flatten_components(self, roots: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        def visit(node: Any) -> None:
            if not isinstance(node, dict):
                return
            result.append(node)
            for child in self._as_list(node.get("children")):
                visit(child)

        for root in roots:
            visit(root)
        return result

    def _contract_title(self, contract: dict[str, Any]) -> str:
        page = self._contract_page(contract)
        return self._text(page.get("title") or page.get("pageName") or contract.get("title"))

    def _contract_description(self, contract: dict[str, Any]) -> str:
        page = self._contract_page(contract)
        return self._text(page.get("description") or contract.get("description"))

    def _contract_page_kind(self, contract: dict[str, Any]) -> str:
        page = self._contract_page(contract)
        return self._text(page.get("pageKind") or page.get("kind") or "GENERIC")

    def _contract_page(self, contract: dict[str, Any]) -> dict[str, Any]:
        page = self._dict_value(contract.get("page"))
        if page:
            return page
        content = self._dict_value(contract.get("content"))
        return self._dict_value(content.get("page"))

    def _normalize_field_type(self, value: Any) -> str:
        text = self._text(value).upper()
        lower = self._text(value).lower()
        if any(token in lower for token in ["amount", "price", "quantity", "count", "number"]) or any(token in text for token in ["金额", "数量", "价格", "库存"]):
            return "NUMBER"
        if "date" in lower or "日期" in text:
            return "DATE"
        if "time" in lower or "时间" in text:
            return "DATETIME"
        if "status" in lower or "状态" in text:
            return "STATUS"
        if "textarea" in lower or any(token in text for token in ["备注", "说明", "描述"]):
            return "TEXTAREA"
        if "boolean" in lower or "switch" in lower or "是否" in text:
            return "BOOLEAN"
        if "select" in lower or any(token in text for token in ["类型", "分类", "供应商", "客户", "仓库", "角色", "部门"]):
            return "SELECT"
        return text if text in {"TEXT", "NUMBER", "DATE", "DATETIME", "STATUS", "TEXTAREA", "BOOLEAN", "SELECT"} else "TEXT"

    def _is_action_like(self, key: str, label: str) -> bool:
        merged = f"{key} {label}".lower()
        return any(token in merged for token in ["action", "operate", "operation", "操作", "按钮"])

    def _dedupe_resources(self, resources: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for index, item in enumerate(resources):
            segment = str(item.get("apiSegment") or f"resource-{index + 1}")
            if segment in seen:
                item = dict(item)
                item["apiSegment"] = f"{segment}-{index + 1}"
            seen.add(str(item.get("apiSegment")))
            result.append(item)
        return result[:24]

    def _file(
        self,
        path: str,
        file_type: str,
        language: str,
        source: str,
        description: str,
        content: str,
        priority: int,
    ) -> DevelopmentCodeFilePayload:
        return DevelopmentCodeFilePayload(
            file_path=self._normalize_path(path),
            file_type=file_type,
            action="CREATE_OR_UPDATE",
            source=source,
            description=description,
            language=language,
            content=content.strip() + "\n",
            priority=priority,
        )

    def _dedupe_files(self, files: list[DevelopmentCodeFilePayload]) -> list[DevelopmentCodeFilePayload]:
        by_path: dict[str, DevelopmentCodeFilePayload] = {}
        for file in files:
            path = self._normalize_path(file.file_path)
            if not path:
                continue
            by_path[path] = DevelopmentCodeFilePayload(
                file_path=path,
                file_type=file.file_type,
                action=file.action,
                source=file.source,
                description=file.description,
                language=file.language,
                content=file.content,
                priority=file.priority,
            )
        return sorted(by_path.values(), key=lambda item: (item.priority, item.file_path))

    def _normalize_path(self, value: Any) -> str:
        return str(value or "").replace("\\", "/").replace("//", "/").rstrip("/")

    def _safe_identifier(self, value: Any) -> str:
        text = self._text(value)
        if not text:
            return ""
        text = re.sub(r"[^0-9a-zA-Z_]+", "_", text).strip("_")
        if not text:
            return ""
        if text[0].isdigit():
            text = "r_" + text
        return text[:80]

    def _safe_package_name(self, value: Any) -> str:
        text = self._text(value).lower()
        text = re.sub(r"[^0-9a-z-]+", "-", text).strip("-")
        return text or "codemind-generated-app"

    def _dict_value(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _as_list(self, value: Any) -> list[Any]:
        return value if isinstance(value, list) else []

    def _text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    def _json(self, value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, indent=2)

    def _js(self, value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, indent=2)

    def _escape_html(self, value: Any) -> str:
        text = self._text(value)
        return (
            text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
        )

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()
        for value in values:
            text = self._text(value)
            if text and text not in seen:
                seen.add(text)
                result.append(text)
        return result
