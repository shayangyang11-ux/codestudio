from __future__ import annotations

import json
import re
from typing import Any

from app.schemas.development_codegen import (
    DevelopmentCodeFilePayload,
    DevelopmentNodejsCodegenRequest,
    DevelopmentNodejsCodegenResponse,
)


class UiContractVueCodegenService:
    """
    A9.10.5 deterministic UI_CONTRACT -> Vue source generator.

    This generator intentionally reads only handoff_context.confirmedUiContract,
    which is built from confirmed/published UI contract snapshots. It does not
    read artifact payload drafts, so Development codegen cannot silently consume
    a dirty prototype draft.
    """

    def generate(
        self,
        request: DevelopmentNodejsCodegenRequest,
        warnings: list[str] | None = None,
    ) -> DevelopmentNodejsCodegenResponse | None:
        local_warnings = warnings if warnings is not None else []
        contracts = self._extract_confirmed_contracts(request.handoff_context)

        if not contracts:
            return None

        generation_root = self._normalize_path(request.generation_root)
        module_segment = self._module_segment(request, contracts)
        page_files: list[DevelopmentCodeFilePayload] = []
        route_entries: list[dict[str, str]] = []

        for index, item in enumerate(contracts[:24]):
            contract = self._normalize_contract(item.get("contract"))
            if not contract:
                continue

            page_meta = self._page_meta(contract, item, request, index)
            page_segment = self._slug(page_meta["key"] or page_meta["title"] or f"page-{index + 1}")
            class_name = self._pascal(page_meta["key"] or page_meta["title"] or f"Generated{index + 1}")
            if not class_name.endswith("Page"):
                class_name = f"{class_name}Page"

            component_name = class_name
            page_path = f"{generation_root}/frontend/src/modules/{module_segment}/pages/{class_name}.vue"
            api_path = f"{generation_root}/frontend/src/modules/{module_segment}/api/{page_segment}Api.js"
            mock_path = f"{generation_root}/frontend/src/modules/{module_segment}/mock/{page_segment}MockData.js"
            types_path = f"{generation_root}/frontend/src/modules/{module_segment}/types/{page_segment}UiContractTypes.js"

            page_files.append(
                self._file(
                    page_path,
                    "FRONTEND",
                    "CREATE_OR_UPDATE",
                    "UI_CONTRACT",
                    f"由已发布 UI_CONTRACT 生成的 Vue 页面：{page_meta['title']}。",
                    "vue",
                    self._render_vue_page(contract, page_meta, api_path, mock_path, types_path),
                    34 + index,
                )
            )
            page_files.append(
                self._file(
                    api_path,
                    "FRONTEND",
                    "CREATE_OR_UPDATE",
                    "UI_CONTRACT_API_BINDINGS",
                    f"{page_meta['title']} 页面 API client stub。",
                    "javascript",
                    self._render_api_client(contract, page_segment),
                    55 + index,
                )
            )
            page_files.append(
                self._file(
                    mock_path,
                    "FRONTEND",
                    "CREATE_OR_UPDATE",
                    "UI_CONTRACT_MOCK_DATA",
                    f"{page_meta['title']} 页面 mock data。",
                    "javascript",
                    self._render_mock_data(contract),
                    65 + index,
                )
            )
            page_files.append(
                self._file(
                    types_path,
                    "FRONTEND",
                    "CREATE_OR_UPDATE",
                    "UI_CONTRACT_TYPES",
                    f"{page_meta['title']} 页面 UI_CONTRACT 摘要类型常量。",
                    "javascript",
                    self._render_type_constants(contract, page_meta),
                    75 + index,
                )
            )

            route_entries.append(
                {
                    "path": self._route_path(page_meta, index),
                    "name": component_name,
                    "component": component_name,
                    "importPath": f"../modules/{module_segment}/pages/{class_name}.vue",
                    "title": page_meta["title"],
                }
            )

        if not page_files:
            return None

        main_file = self._file(
            f"{generation_root}/frontend/src/main.js",
            "FRONTEND",
            "CREATE_OR_UPDATE",
            "CONFIRMED_UI_CONTRACT",
            "Vue 应用入口，安装 Vue Router。",
            "javascript",
            self._render_main_js(),
            6,
        )
        route_file = self._file(
            f"{generation_root}/frontend/src/router/routes.js",
            "FRONTEND",
            "CREATE_OR_UPDATE",
            "CONFIRMED_UI_CONTRACT",
            "由已发布 UI_CONTRACT 生成页面路由聚合入口。",
            "javascript",
            self._render_routes(route_entries),
            8,
        )
        app_file = self._file(
            f"{generation_root}/frontend/src/App.vue",
            "FRONTEND",
            "CREATE_OR_UPDATE",
            "CONFIRMED_UI_CONTRACT",
            "UI_CONTRACT 生成应用壳层，承载发布后的业务页面。",
            "vue",
            self._render_app_shell(route_entries),
            7,
        )

        files = self._dedupe_files([main_file, app_file, route_file, *page_files])
        local_warnings.extend(
            [
                "A9.10.5_UI_CONTRACT_VUE_CODEGEN_ENABLED",
                "A9.10.5_UI_CONTRACT_SOURCE=confirmedUiContract",
                f"A9.10.5_UI_CONTRACT_PAGE_COUNT={len(route_entries)}",
                "A9.10.5_DIRTY_DRAFT_NOT_USED",
                "A9.10.11.3_FRONTEND_ROUTE_FILE_ALIGNMENT_FIX",
            ]
        )

        return DevelopmentNodejsCodegenResponse(
            status="AI_GENERATED",
            generator_mode="A9_10_5_CONFIRMED_UI_CONTRACT_TO_VUE_CODEGEN",
            provider="deterministic",
            model="ui-contract-vue-codegen-v1",
            summary=(
                "已根据已发布 UI_CONTRACT 生成可运行 Vue 页面源码、路由、API client stub、mockData 和类型常量。"
                "本次没有读取未发布草稿。"
            ),
            warnings=self._dedupe(local_warnings),
            files=files,
            usage={
                "provider": "deterministic",
                "model": "ui-contract-vue-codegen-v1",
                "success": True,
                "confirmedUiContractPageCount": len(route_entries),
                "fileCount": len(files),
            },
            raw={
                "schema": "a9_10_5_confirmed_ui_contract_vue_codegen",
                "confirmedUiContractPageCount": len(route_entries),
                "routePaths": [entry["path"] for entry in route_entries],
            },
        )

    def _extract_confirmed_contracts(self, context: dict[str, Any]) -> list[dict[str, Any]]:
        if not isinstance(context, dict):
            return []

        confirmed = context.get("confirmedUiContract")
        if not isinstance(confirmed, dict) or not confirmed:
            confirmed = self._confirmed_from_development_build_context(
                context.get("developmentBuildContext")
                or context.get("development_build_context")
            )

        if not isinstance(confirmed, dict) or not confirmed:
            return []

        if isinstance(confirmed.get("pages"), list):
            result: list[dict[str, Any]] = []
            for index, page in enumerate(confirmed.get("pages") or []):
                if not isinstance(page, dict):
                    continue
                contract = self._normalize_contract(page.get("uiContract") or page.get("contract"))
                if contract:
                    result.append(
                        {
                            "contract": contract,
                            "pageKey": page.get("pageKey") or page.get("key") or f"page-{index + 1}",
                            "pageName": page.get("pageName") or page.get("title"),
                            "routePath": page.get("routePath"),
                            "snapshotStatus": page.get("status"),
                        }
                    )
            return result

        contract = self._normalize_contract(confirmed)
        if contract:
            return [
                {
                    "contract": contract,
                    "pageKey": confirmed.get("pageKey") or confirmed.get("key"),
                    "pageName": confirmed.get("pageName") or confirmed.get("title"),
                    "routePath": confirmed.get("routePath"),
                    "snapshotStatus": confirmed.get("status"),
                }
            ]

        return []


    def _confirmed_from_development_build_context(self, value: Any) -> dict[str, Any]:
        if not isinstance(value, dict) or not value:
            return {}

        confirmed = value.get("confirmedUiContract")
        if isinstance(confirmed, dict) and confirmed:
            return confirmed

        pages_payload = value.get("pages")
        page_items: list[Any] = []

        if isinstance(pages_payload, dict):
            raw_items = pages_payload.get("items") or pages_payload.get("pages")
            if isinstance(raw_items, list):
                page_items = raw_items
        elif isinstance(pages_payload, list):
            page_items = pages_payload

        pages: list[dict[str, Any]] = []
        for index, item in enumerate(page_items):
            if not isinstance(item, dict):
                continue

            contract = self._normalize_contract(item.get("uiContract") or item.get("contract"))
            if not contract:
                continue

            node_payload = item.get("node") if isinstance(item.get("node"), dict) else {}
            pages.append(
                {
                    "nodeId": item.get("nodeId") or node_payload.get("nodeId"),
                    "pageId": item.get("pageId"),
                    "pageKey": item.get("pageKey") or item.get("key") or f"page-{index + 1}",
                    "pageName": item.get("pageName") or item.get("title"),
                    "routePath": item.get("routePath"),
                    "snapshotId": item.get("snapshotId"),
                    "artifactId": item.get("artifactId"),
                    "artifactVersionNo": item.get("artifactVersionNo"),
                    "status": item.get("status"),
                    "uiContract": contract,
                }
            )

        if not pages:
            return {}

        scope = value.get("scope") if isinstance(value.get("scope"), dict) else {}
        if len(pages) == 1 and str(scope.get("nodeType") or "").upper() == "PAGE":
            single = dict(pages[0].get("uiContract") or {})
            for key in [
                "nodeId",
                "pageId",
                "pageKey",
                "pageName",
                "routePath",
                "snapshotId",
                "artifactId",
                "artifactVersionNo",
                "status",
            ]:
                if pages[0].get(key) is not None:
                    single[key] = pages[0].get(key)
            return single

        return {
            "scopeType": "MODULE",
            "moduleId": scope.get("nodeId"),
            "moduleKey": scope.get("nodeKey"),
            "moduleName": scope.get("nodeName"),
            "pageCount": len(pages),
            "confirmedPageCount": len(pages),
            "pages": pages,
        }


    def _normalize_contract(self, value: Any) -> dict[str, Any]:
        if not isinstance(value, dict):
            return {}

        content = value.get("content")
        if isinstance(content, dict) and (
            "componentTree" in content
            or "page" in content
            or "interactions" in content
            or "mockData" in content
        ):
            merged = dict(content)
            for key in ["identity", "metadata", "themeProfile", "qualitySummary"]:
                if key not in merged and isinstance(value.get(key), (dict, list, str, int, float, bool)):
                    merged[key] = value.get(key)
            return merged

        if "uiContract" in value and isinstance(value.get("uiContract"), dict):
            return self._normalize_contract(value.get("uiContract"))

        if (
            "componentTree" in value
            or "page" in value
            or "interactions" in value
            or "mockData" in value
        ):
            return value

        return {}

    def _module_segment(
        self,
        request: DevelopmentNodejsCodegenRequest,
        contracts: list[dict[str, Any]],
    ) -> str:
        node = request.node if isinstance(request.node, dict) else {}
        context = request.handoff_context if isinstance(request.handoff_context, dict) else {}
        for value in [
            node.get("nodeKey"),
            node.get("nodeName"),
            context.get("moduleKey"),
            context.get("moduleName"),
            contracts[0].get("pageKey") if contracts else None,
        ]:
            segment = self._slug(value)
            if segment:
                return segment
        return "generated"

    def _page_meta(
        self,
        contract: dict[str, Any],
        item: dict[str, Any],
        request: DevelopmentNodejsCodegenRequest,
        index: int,
    ) -> dict[str, str]:
        page = self._dict_at(contract, "page")
        identity = self._dict_at(contract, "identity")
        node = request.node if isinstance(request.node, dict) else {}

        title = self._first_text(
            page.get("title"),
            page.get("pageName"),
            identity.get("pageName"),
            item.get("pageName"),
            node.get("nodeName"),
            f"生成页面 {index + 1}",
        )
        key = self._first_text(
            page.get("pageKey"),
            page.get("key"),
            identity.get("pageKey"),
            item.get("pageKey"),
            node.get("nodeKey"),
            title,
        )
        route = self._first_text(
            page.get("routePath"),
            page.get("path"),
            self._dict_at(page, "route").get("path"),
            identity.get("routePath"),
            item.get("routePath"),
            node.get("previewRoute"),
            "/" + self._slug(key or title or f"page-{index + 1}"),
        )
        description = self._first_text(
            page.get("description"),
            page.get("summary"),
            identity.get("description"),
            contract.get("description"),
            "",
        )
        page_kind = self._first_text(
            page.get("pageKind"),
            page.get("kind"),
            self._dict_at(page, "layout").get("type"),
            self._dict_at(contract, "layout").get("type"),
            "",
        )

        return {
            "title": title,
            "key": key,
            "routePath": route,
            "description": description,
            "pageKind": page_kind,
        }

    def _render_vue_page(
        self,
        contract: dict[str, Any],
        page_meta: dict[str, str],
        api_path: str,
        mock_path: str,
        types_path: str,
    ) -> str:
        roots = self._root_components(contract)
        page_header = self._find_first_type(roots, {"PAGE_HEADER"})
        body_roots = [item for item in roots if self._component_type(item) != "PAGE_HEADER"]
        if not body_roots and page_header:
            body_roots = self._children(page_header)

        table_vars: list[dict[str, Any]] = []
        tab_defaults: dict[str, str] = {}
        form_defaults = self._form_defaults(roots)
        rendered_body = "\n".join(
            self._render_component(node, contract, table_vars, tab_defaults, 3)
            for node in body_roots
        ).strip()

        if not rendered_body:
            rendered_body = "      <section class=\"ucg-card\"><p>当前 UI_CONTRACT 暂无可渲染组件。</p></section>"

        title = self._text(page_header.get("title") if page_header else None) or page_meta["title"]
        description = self._text(page_header.get("description") if page_header else None) or page_meta["description"]
        action_ids = self._string_list(page_header.get("actionIds") if page_header else [])

        api_import = self._relative_import(api_path)
        mock_import = self._relative_import(mock_path)
        type_import = self._relative_import(types_path)

        script_lines = [
            "<script setup>",
            "import { onMounted, reactive, ref } from 'vue'",
            f"import {{ fetchUiRows, runUiAction }} from '{api_import}'",
            f"import {{ mockRows, mockRecord, mockTables }} from '{mock_import}'",
            f"import {{ uiContractPage }} from '{type_import}'",
            "",
            f"const form = reactive({self._js(form_defaults)})",
            "const loading = ref(false)",
            "const message = ref('')",
            f"const activeTabs = reactive({self._js(tab_defaults)})",
        ]

        for table in table_vars:
            script_lines.append(
                f"const {table['varName']} = ref(mockTables[{self._js(table['componentId'])}] || mockRows)"
            )

        # A9.10.7 remote rows: generated pages try the real Node.js backend first,
        # while preserving mock rows as a preview fallback when the runtime is not started.
        if table_vars:
            script_lines.extend(
                [
                    "",
                    "onMounted(loadRemoteRows)",
                    "",
                    "async function loadRemoteRows() {",
                    "  try {",
                    "    const result = await fetchUiRows()",
                    "    const remoteRows = Array.isArray(result) ? result : (result?.items || result?.records || result?.rows || [])",
                    "    if (!Array.isArray(remoteRows) || remoteRows.length === 0) return",
                ]
            )
            for table in table_vars:
                script_lines.append(f"    {table['varName']}.value = remoteRows")
            script_lines.extend(
                [
                    "  } catch (error) {",
                    "    console.warn('UI_CONTRACT remote rows unavailable, using mock rows.', error)",
                    "  }",
                    "}",
                ]
            )

        script_lines.extend(
            [
                "",
                "function displayValue(value) {",
                "  if (value === null || value === undefined || value === '') return '-'",
                "  if (Array.isArray(value)) return value.join('、')",
                "  if (typeof value === 'object') return JSON.stringify(value)",
                "  return String(value)",
                "}",
                "",
                "async function handleAction(actionId, payload = {}) {",
                "  loading.value = true",
                "  message.value = ''",
                "  try {",
                "    await runUiAction(actionId, { ...payload, form: { ...form }, page: uiContractPage })",
                "    message.value = `${actionId || '操作'} 已提交到 Node.js 后端接口。`",
                "  } catch (error) {",
                "    message.value = error?.message || '操作失败'",
                "  } finally {",
                "    loading.value = false",
                "  }",
                "}",
                "</script>",
            ]
        )

        header_actions = "\n".join(
            "          <button class=\"ucg-btn\" type=\"button\" @click=\"handleAction(%s)\">%s</button>"
            % (self._js(action_id), self._escape_html(self._action_label(contract, action_id)))
            for action_id in action_ids
        )

        if not header_actions:
            header_actions = "          <span class=\"ucg-route\">{{ uiContractPage.routePath }}</span>"

        return f"""<template>
  <section class="ucg-page">
    <header class="ucg-page-header">
      <div>
        <p class="ucg-eyebrow">Confirmed UI_CONTRACT</p>
        <h1>{self._escape_html(title)}</h1>
        <p>{self._escape_html(description)}</p>
      </div>
      <div class="ucg-actions">
{header_actions}
      </div>
    </header>

{rendered_body}

    <p v-if="message" class="ucg-message">{{{{ message }}}}</p>
  </section>
</template>

{chr(10).join(script_lines)}

<style scoped>
.ucg-page {{ display: grid; gap: 18px; color: #0f172a; }}
.ucg-page-header {{ display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 24px 28px; border: 1px solid #e2e8f0; border-radius: 22px; background: linear-gradient(135deg, #ffffff 0%, #f8fbff 100%); box-shadow: 0 18px 45px rgba(15,23,42,.08); }}
.ucg-eyebrow {{ margin: 0 0 8px; color: #2563eb; font-size: 12px; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }}
.ucg-page-header h1 {{ margin: 0; font-size: 30px; line-height: 1.18; }}
.ucg-page-header p {{ margin: 8px 0 0; color: #64748b; }}
.ucg-actions, .ucg-row-actions {{ display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }}
.ucg-btn {{ border: 1px solid #cbd5e1; border-radius: 12px; padding: 9px 14px; background: #fff; color: #1e293b; font-weight: 800; cursor: pointer; }}
.ucg-btn.primary {{ background: #2563eb; border-color: #2563eb; color: #fff; }}
.ucg-route {{ color: #64748b; font-size: 13px; }}
.ucg-card {{ border: 1px solid #e2e8f0; border-radius: 20px; background: #fff; padding: 20px; box-shadow: 0 14px 35px rgba(15,23,42,.06); }}
.ucg-card-header {{ display: flex; justify-content: space-between; gap: 12px; margin-bottom: 16px; }}
.ucg-card-header h2, .ucg-card-header h3 {{ margin: 0; font-size: 18px; }}
.ucg-card-header p {{ margin: 6px 0 0; color: #64748b; }}
.ucg-grid {{ display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }}
.ucg-metric {{ padding: 16px; border: 1px solid #e2e8f0; border-radius: 18px; background: #f8fafc; }}
.ucg-metric span {{ color: #64748b; font-size: 13px; }}
.ucg-metric strong {{ display: block; margin-top: 8px; font-size: 26px; }}
.ucg-metric small {{ color: #16a34a; font-weight: 700; }}
.ucg-form {{ display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }}
.ucg-field {{ display: grid; gap: 7px; color: #334155; font-weight: 800; }}
.ucg-field em {{ color: #ef4444; font-style: normal; }}
.ucg-field input, .ucg-field textarea, .ucg-field select {{ min-height: 38px; border: 1px solid #cbd5e1; border-radius: 12px; padding: 9px 12px; font: inherit; background: #fff; }}
.ucg-field textarea {{ min-height: 82px; resize: vertical; }}
.ucg-switch {{ display: inline-flex; align-items: center; gap: 8px; }}
.ucg-table-wrap {{ overflow: auto; border: 1px solid #e2e8f0; border-radius: 16px; }}
.ucg-table {{ width: 100%; border-collapse: collapse; min-width: 760px; }}
.ucg-table th {{ background: #f8fafc; color: #475569; text-align: left; padding: 12px; font-size: 13px; white-space: nowrap; }}
.ucg-table td {{ border-top: 1px solid #e2e8f0; padding: 12px; color: #334155; }}
.ucg-tag {{ display: inline-flex; padding: 3px 9px; border-radius: 999px; background: #eef2ff; color: #3730a3; font-size: 12px; font-weight: 800; }}
.ucg-tabs {{ display: flex; gap: 8px; border-bottom: 1px solid #e2e8f0; margin-bottom: 14px; }}
.ucg-tab {{ border: 0; background: transparent; padding: 10px 12px; font-weight: 800; cursor: pointer; color: #64748b; }}
.ucg-tab.active {{ color: #2563eb; border-bottom: 2px solid #2563eb; }}
.ucg-descriptions {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; border: 1px solid #e2e8f0; border-radius: 16px; overflow: hidden; }}
.ucg-descriptions div {{ display: grid; grid-template-columns: 130px 1fr; border-bottom: 1px solid #e2e8f0; }}
.ucg-descriptions dt, .ucg-descriptions dd {{ margin: 0; padding: 12px; }}
.ucg-descriptions dt {{ background: #f8fafc; color: #64748b; font-weight: 800; }}
.ucg-steps, .ucg-timeline {{ display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }}
.ucg-step, .ucg-timeline-item {{ display: flex; gap: 12px; align-items: flex-start; padding: 12px; border: 1px solid #e2e8f0; border-radius: 16px; background: #f8fafc; }}
.ucg-step-index {{ display: grid; place-items: center; width: 28px; height: 28px; border-radius: 999px; background: #2563eb; color: #fff; font-weight: 900; }}
.ucg-upload {{ border: 1px dashed #93c5fd; border-radius: 18px; padding: 18px; background: #eff6ff; }}
.ucg-upload-list, .ucg-tree {{ display: grid; gap: 8px; margin-top: 12px; }}
.ucg-file, .ucg-tree-node {{ display: flex; justify-content: space-between; gap: 10px; padding: 10px 12px; border-radius: 12px; background: #fff; border: 1px solid #dbeafe; }}
.ucg-chart-bars {{ display: flex; align-items: end; gap: 12px; height: 180px; padding: 16px; border-radius: 16px; background: #f8fafc; }}
.ucg-bar {{ flex: 1; min-width: 28px; border-radius: 10px 10px 0 0; background: linear-gradient(180deg, #60a5fa, #2563eb); display: flex; align-items: end; justify-content: center; color: #fff; font-size: 12px; font-weight: 900; padding-bottom: 6px; }}
.ucg-alert {{ border-radius: 16px; padding: 14px 16px; background: #fff7ed; border: 1px solid #fed7aa; color: #9a3412; }}
.ucg-result {{ display: grid; place-items: center; text-align: center; gap: 8px; min-height: 180px; }}
.ucg-panel {{ border: 1px solid #dbeafe; border-radius: 18px; background: #f8fbff; padding: 18px; }}
.ucg-message {{ position: sticky; bottom: 16px; justify-self: start; padding: 10px 14px; border-radius: 12px; background: #0f172a; color: #fff; box-shadow: 0 12px 30px rgba(15,23,42,.25); }}
@media (max-width: 1100px) {{
  .ucg-grid, .ucg-form, .ucg-descriptions {{ grid-template-columns: 1fr 1fr; }}
}}
</style>
""".strip()

    def _render_component(
        self,
        component: dict[str, Any],
        contract: dict[str, Any],
        table_vars: list[dict[str, Any]],
        tab_defaults: dict[str, str],
        depth: int,
    ) -> str:
        ctype = self._component_type(component)
        indent = "  " * depth
        title = self._text(component.get("title"))
        description = self._text(component.get("description"))
        header = self._card_header(title, description, indent)

        if ctype in {"FORM", "FIELD_GROUP", "SEARCH_FORM"}:
            fields = self._as_list(component.get("fields"))
            field_html = "\n".join(self._render_field(field, indent + "  ") for field in fields if isinstance(field, dict))
            actions = self._action_buttons(contract, self._string_list(component.get("actionIds")), indent + "  ")
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <form class="ucg-form" @submit.prevent="handleAction('submit')">
{field_html or indent + "    <p>暂无表单字段</p>"}
{indent}  </form>
{actions}
{indent}</section>"""

        if ctype == "TABLE":
            columns = [col for col in self._as_list(component.get("columns")) if isinstance(col, dict) and not self._is_action_column(col)]
            if not columns:
                columns = [{"columnKey": "name", "fieldKey": "name", "label": "名称", "type": "TEXT"}]
            component_id = self._text(component.get("componentId")) or f"table_{len(table_vars)+1}"
            var_name = self._safe_var("rows_" + component_id)
            table_vars.append({"componentId": component_id, "varName": var_name, "columns": columns})
            th = "\n".join(
                f'{indent}        <th>{self._escape_html(self._text(col.get("label")) or self._text(col.get("columnKey")) or "-")}</th>'
                for col in columns
            )
            td = "\n".join(
                f'{indent}        <td>{self._table_cell_expr(col)}</td>'
                for col in columns
            )
            row_actions = self._string_list(self._dict_at(component, "props").get("rowActionIds"))
            row_action_html = self._row_action_buttons(contract, row_actions, indent + "        ")
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-table-wrap">
{indent}    <table class="ucg-table">
{indent}      <thead>
{indent}      <tr>
{th}
{indent}        <th v-if="{self._js(bool(row_actions))}">操作</th>
{indent}      </tr>
{indent}      </thead>
{indent}      <tbody>
{indent}      <tr v-for="(row, index) in {var_name}" :key="row.id || index">
{td}
{indent}        <td v-if="{self._js(bool(row_actions))}" class="ucg-row-actions">
{row_action_html or indent + "          -"}
{indent}        </td>
{indent}      </tr>
{indent}      </tbody>
{indent}    </table>
{indent}  </div>
{indent}</section>"""

        if ctype in {"STATS", "KPI", "KPI_CARD", "METRIC_GRID"}:
            items = self._component_items(component) or [
                {"label": title or "指标", "value": "128", "hint": "较昨日 +12%"}
            ]
            cards = "\n".join(
                f"""{indent}    <article class="ucg-metric">
{indent}      <span>{self._escape_html(self._text(item.get("label") or item.get("title") or item.get("key")) or "指标")}</span>
{indent}      <strong>{self._escape_html(self._text(item.get("value")) or "0")}</strong>
{indent}      <small>{self._escape_html(self._text(item.get("hint") or item.get("trend") or item.get("description")) or "")}</small>
{indent}    </article>"""
                for item in items if isinstance(item, dict)
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-grid">
{cards}
{indent}  </div>
{indent}</section>"""

        if ctype in {"CHART", "TREND_CHART", "LINE_CHART", "BAR_CHART", "PIE_CHART"}:
            items = self._component_items(component) or self._as_list(self._dict_at(component, "props").get("series"))
            bars = self._chart_items(items)
            bar_html = "\n".join(
                f'{indent}    <div class="ucg-bar" style="height: {height}%">{self._escape_html(label)}</div>'
                for label, height in bars
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-chart-bars">
{bar_html}
{indent}  </div>
{indent}</section>"""

        if ctype in {"DESCRIPTION_LIST", "DESCRIPTIONS", "DETAIL_LIST"}:
            items = self._component_items(component) or self._description_items_from_fields(component)
            rows = "\n".join(
                f"""{indent}    <div>
{indent}      <dt>{self._escape_html(self._text(item.get("label") or item.get("key")) or "-")}</dt>
{indent}      <dd>{self._escape_html(self._text(item.get("value")) or "{{ displayValue(mockRecord['" + self._escape_js(self._text(item.get("fieldKey") or item.get("key")) or "") + "']) }}")}</dd>
{indent}    </div>"""
                for item in items if isinstance(item, dict)
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <dl class="ucg-descriptions">
{rows or indent + "    <div><dt>说明</dt><dd>-</dd></div>"}
{indent}  </dl>
{indent}</section>"""

        if ctype in {"STEPS", "PROGRESS_STEPS"}:
            items = self._component_items(component) or [{"title": "提交", "description": "用户提交申请"}, {"title": "审核", "description": "业务人员审核"}, {"title": "完成", "description": "流程完成"}]
            rows = "\n".join(
                f"""{indent}    <li class="ucg-step">
{indent}      <span class="ucg-step-index">{idx + 1}</span>
{indent}      <div><strong>{self._escape_html(self._text(item.get("title") or item.get("label")) or "-")}</strong><p>{self._escape_html(self._text(item.get("description") or item.get("status")) or "")}</p></div>
{indent}    </li>"""
                for idx, item in enumerate(items) if isinstance(item, dict)
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <ol class="ucg-steps">
{rows}
{indent}  </ol>
{indent}</section>"""

        if ctype in {"TIMELINE", "AUDIT_LOG", "PROCESS_LOG"}:
            items = self._component_items(component) or [{"title": "创建记录", "description": "系统生成初始化记录", "time": "刚刚"}]
            rows = "\n".join(
                f"""{indent}    <li class="ucg-timeline-item">
{indent}      <span class="ucg-step-index">{idx + 1}</span>
{indent}      <div><strong>{self._escape_html(self._text(item.get("title") or item.get("operator")) or "-")}</strong><p>{self._escape_html(self._text(item.get("description") or item.get("content")) or "")}</p><small>{self._escape_html(self._text(item.get("time") or item.get("createdAt")) or "")}</small></div>
{indent}    </li>"""
                for idx, item in enumerate(items) if isinstance(item, dict)
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <ol class="ucg-timeline">
{rows}
{indent}  </ol>
{indent}</section>"""

        if ctype in {"UPLOAD", "FILE_UPLOAD", "ATTACHMENT_UPLOAD"}:
            items = self._component_items(component) or self._as_list(self._dict_at(component, "props").get("files")) or [{"name": "待上传文件.xlsx", "status": "待校验"}]
            rows = "\n".join(
                f"""{indent}    <div class="ucg-file"><span>{self._escape_html(self._text(item.get("name") or item.get("fileName") or item.get("title")) or "文件")}</span><strong>{self._escape_html(self._text(item.get("status") or item.get("size")) or "待上传")}</strong></div>"""
                for item in items if isinstance(item, dict)
            )
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-upload">
{indent}    <strong>拖拽文件到这里，或点击选择文件</strong>
{indent}    <p>支持根据 UI_CONTRACT 校验上传格式，后续接入真实上传接口。</p>
{indent}    <div class="ucg-upload-list">
{rows}
{indent}    </div>
{indent}  </div>
{indent}</section>"""

        if ctype in {"TREE", "ORG_TREE", "PERMISSION_TREE"}:
            items = self._component_items(component) or self._as_list(self._dict_at(component, "props").get("nodes")) or [{"title": "根节点", "description": "默认节点"}]
            rows = "\n".join(self._render_tree_item(item, indent + "    ") for item in items if isinstance(item, dict))
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-tree">
{rows}
{indent}  </div>
{indent}</section>"""

        if ctype in {"TABS", "TAB_PANEL"}:
            tabs = [item for item in self._as_list(component.get("tabs") or self._dict_at(component, "props").get("tabs")) if isinstance(item, dict)]
            component_id = self._text(component.get("componentId")) or "tabs"
            default_key = self._text(tabs[0].get("tabKey") if tabs else None) or self._text(tabs[0].get("key") if tabs else None) or "default"
            tab_defaults[component_id] = default_key
            buttons = "\n".join(
                f'{indent}    <button class="ucg-tab" :class="{{ active: activeTabs[{self._js(component_id)}] === {self._js(self._text(tab.get("tabKey") or tab.get("key")) or str(idx))} }}" type="button" @click="activeTabs[{self._js(component_id)}] = {self._js(self._text(tab.get("tabKey") or tab.get("key")) or str(idx))}">{self._escape_html(self._text(tab.get("title") or tab.get("label")) or f"页签 {idx + 1}")}</button>'
                for idx, tab in enumerate(tabs)
            )
            panels = "\n".join(
                f'{indent}    <section v-show="activeTabs[{self._js(component_id)}] === {self._js(self._text(tab.get("tabKey") or tab.get("key")) or str(idx))}" class="ucg-panel">{self._escape_html(self._text(tab.get("description") or tab.get("content")) or "暂无内容")}</section>'
                for idx, tab in enumerate(tabs)
            )
            tab_button_fallback = f'{indent}    <button class="ucg-tab active" type="button">默认</button>'
            tab_panel_fallback = f'{indent}  <section class="ucg-panel">暂无内容</section>'
            return f"""{indent}<section class="ucg-card">
{header}
{indent}  <div class="ucg-tabs">
{buttons or tab_button_fallback}
{indent}  </div>
{panels or tab_panel_fallback}
{indent}</section>"""

        if ctype in {"ALERT", "NOTICE"}:
            level = self._text(self._dict_at(component, "props").get("level")) or "info"
            return f"""{indent}<section class="ucg-alert">
{indent}  <strong>{self._escape_html(title or level.upper())}</strong>
{indent}  <span>{self._escape_html(description or self._text(self._dict_at(component, "props").get("message")) or "")}</span>
{indent}</section>"""

        if ctype in {"RESULT", "STATUS_RESULT"}:
            return f"""{indent}<section class="ucg-card ucg-result">
{indent}  <strong>{self._escape_html(title or "处理结果")}</strong>
{indent}  <p>{self._escape_html(description or "当前流程已完成。")}</p>
{indent}</section>"""

        if ctype in {"MODAL", "DIALOG", "DRAWER", "SIDE_PANEL"}:
            return f"""{indent}<section class="ucg-card ucg-panel">
{header}
{indent}  <p>{self._escape_html(description or "这里展示弹窗/抽屉内容预览。")}</p>
{indent}</section>"""

        children = "\n".join(
            self._render_component(child, contract, table_vars, tab_defaults, depth + 1)
            for child in self._children(component)
        )
        actions = self._action_buttons(contract, self._string_list(component.get("actionIds")), indent + "  ")
        return f"""{indent}<section class="ucg-card">
{header}
{children or indent + "  <p>" + self._escape_html(description or title or "内容区") + "</p>"}
{actions}
{indent}</section>"""

    def _render_field(self, field: dict[str, Any], indent: str) -> str:
        key = self._field_key(field)
        label = self._text(field.get("label")) or key
        placeholder = self._text(field.get("placeholder")) or f"请输入{label}"
        required = bool(field.get("required"))
        ftype = self._text(field.get("type") or field.get("fieldType")).upper()

        suffix = "<em>*</em>" if required else ""
        common = f"{indent}<label class=\"ucg-field\"><span>{self._escape_html(label)}{suffix}</span>"

        if ftype == "TEXTAREA":
            return f"""{common}
{indent}  <textarea v-model="form[{self._js(key)}]" placeholder="{self._escape_html(placeholder)}"></textarea>
{indent}</label>"""

        if ftype == "SELECT":
            options = self._as_list(field.get("options")) or [{"label": "请选择", "value": ""}]
            option_html = "\n".join(
                f'{indent}    <option value="{self._escape_html(self._text(opt.get("value")))}">{self._escape_html(self._text(opt.get("label")) or self._text(opt.get("value")) or "选项")}</option>'
                for opt in options if isinstance(opt, dict)
            )
            return f"""{common}
{indent}  <select v-model="form[{self._js(key)}]">
{option_html}
{indent}  </select>
{indent}</label>"""

        if ftype in {"SWITCH", "CHECKBOX"}:
            return f"""{common}
{indent}  <span class="ucg-switch"><input type="checkbox" v-model="form[{self._js(key)}]" /> 启用</span>
{indent}</label>"""

        input_type = "number" if ftype == "NUMBER" else "datetime-local" if ftype in {"DATETIME", "DATE_TIME"} else "date" if ftype == "DATE" else "password" if ftype == "PASSWORD" else "email" if ftype == "EMAIL" else "text"
        return f"""{common}
{indent}  <input type="{input_type}" v-model="form[{self._js(key)}]" placeholder="{self._escape_html(placeholder)}" />
{indent}</label>"""

    def _render_mock_data(self, contract: dict[str, Any]) -> str:
        roots = self._root_components(contract)
        form_defaults = self._form_defaults(roots)
        tables: dict[str, list[dict[str, Any]]] = {}
        table_components = self._find_all_types(roots, {"TABLE"})
        for table in table_components:
            component_id = self._text(table.get("componentId")) or f"table_{len(tables)+1}"
            tables[component_id] = self._table_rows(table, contract)

        first_rows = next(iter(tables.values()), [])
        mock_record = dict(form_defaults)
        if first_rows:
            mock_record.update(first_rows[0])

        return f"""export const mockRecord = {self._js(mock_record)}

export const mockRows = {self._js(first_rows or [mock_record])}

export const mockTables = {self._js(tables)}

export const mockMeta = {{
  generatedBy: 'A9.10.5_UI_CONTRACT_TO_VUE_CODEGEN',
  source: 'confirmedUiContract',
}}
""".strip()

    def _render_api_client(self, contract: dict[str, Any], page_segment: str) -> str:
        actions = self._interactions(contract)
        action_meta = [
            {
                "id": self._interaction_id(action),
                "label": self._text(action.get("label") or action.get("title")),
                "method": self._text(action.get("method") or self._dict_at(action, "apiBinding").get("method") or "POST").upper(),
                "path": self._text(action.get("path") or self._dict_at(action, "apiBinding").get("path") or f"/api/{page_segment}"),
            }
            for action in actions
            if self._interaction_id(action)
        ]

        return f"""const actionBindings = {self._js(action_meta)}

export async function runUiAction(actionId, payload = {{}}) {{
  const binding = actionBindings.find((item) => item.id === actionId) || {{}}
  const method = binding.method || 'POST'
  const path = binding.path || `/api/{page_segment}/${{encodeURIComponent(actionId || 'action')}}`

  return request(path, {{
    method,
    body: method === 'GET' ? undefined : JSON.stringify(payload),
  }}).catch(() => ({{
    success: true,
    data: payload,
    offline: true,
    message: '当前为 UI_CONTRACT 生成的离线 API stub。',
  }}))
}}

export async function fetchUiRows() {{
  return request('/api/{page_segment}').catch(() => [])
}}

async function request(url, options = {{}}) {{
  const response = await fetch(url, {{
    headers: {{ 'Content-Type': 'application/json' }},
    ...options,
  }})

  const body = await response.json().catch(() => ({{}}))

  if (!response.ok || body.success === false) {{
    throw new Error(body?.error?.message || body?.message || 'Request failed')
  }}

  return body.data ?? body
}}
""".strip()

    def _render_type_constants(self, contract: dict[str, Any], page_meta: dict[str, str]) -> str:
        roots = self._root_components(contract)
        components = self._flatten_components(roots)
        fields: list[dict[str, Any]] = []
        columns: list[dict[str, Any]] = []

        for component in components:
            for field in self._as_list(component.get("fields")):
                if isinstance(field, dict):
                    fields.append(
                        {
                            "componentId": component.get("componentId"),
                            "fieldKey": self._field_key(field),
                            "label": self._text(field.get("label")),
                            "type": self._text(field.get("type") or field.get("fieldType")),
                        }
                    )
            for column in self._as_list(component.get("columns")):
                if isinstance(column, dict) and not self._is_action_column(column):
                    columns.append(
                        {
                            "componentId": component.get("componentId"),
                            "columnKey": self._text(column.get("columnKey") or column.get("fieldKey") or column.get("key")),
                            "fieldKey": self._text(column.get("fieldKey") or column.get("columnKey") or column.get("key")),
                            "label": self._text(column.get("label")),
                            "type": self._text(column.get("type") or column.get("columnType")),
                        }
                    )

        summary = {
            "title": page_meta["title"],
            "pageKey": page_meta["key"],
            "routePath": page_meta["routePath"],
            "pageKind": page_meta["pageKind"],
            "componentCount": len(components),
            "fieldCount": len(fields),
            "columnCount": len(columns),
        }

        return f"""export const uiContractPage = {self._js(summary)}

export const uiContractFields = {self._js(fields)}

export const uiContractColumns = {self._js(columns)}

export const uiContractActions = {self._js([
            {
                "id": self._interaction_id(action),
                "label": self._text(action.get("label") or action.get("title")),
            }
            for action in self._interactions(contract)
            if self._interaction_id(action)
        ])}
""".strip()

    def _render_main_js(self) -> str:
        return """import { createApp } from 'vue'
import App from './App.vue'
import router from './router/routes.js'
import './shared/styles/app.css'

createApp(App).use(router).mount('#app')
""".strip()


    def _render_routes(self, entries: list[dict[str, str]]) -> str:
        first_path = entries[0]["path"] if entries else "/"

        routes = [
            f"""  {{
    path: '/',
    redirect: {self._js(first_path)},
  }}"""
        ]

        seen_paths = {"/"}
        for index, entry in enumerate(entries):
            path = entry["path"]
            if path in seen_paths:
                path = f"{path.rstrip('/')}-{index + 1}"
            seen_paths.add(path)
            routes.append(
                f"""  {{
    path: {self._js(path)},
    name: {self._js(entry['name'])},
    title: {self._js(entry['title'])},
    component: () => import({self._js(entry['importPath'])}),
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

export const navigationRoutes = generatedRoutes.filter((route) => route.path && !route.redirect && !route.path.includes(':'))

export const router = createRouter({
  history: createWebHistory(),
  routes: generatedRoutes,
})

export default router
""".strip()

    def _render_app_shell(self, entries: list[dict[str, str]]) -> str:
        return """<template>
  <main class="app-shell">
    <aside class="app-sidebar">
      <strong>CodeMind Generated App</strong>
      <span>Confirmed UI_CONTRACT</span>
      <nav>
        <button
          v-for="route in navigationRoutes"
          :key="route.path"
          type="button"
          :class="{ active: currentPath === route.path }"
          @click="go(route.path)"
        >
          {{ route.title || route.name }}
        </button>
      </nav>
    </aside>
    <section class="app-content">
      <RouterView />
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { navigationRoutes } from './router/routes.js'
import './shared/styles/app.css'

const route = useRoute()
const router = useRouter()
const currentPath = computed(() => route.path)

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.app-sidebar nav { display: grid; gap: 8px; margin-top: 22px; }
.app-sidebar button { text-align: left; border: 1px solid rgba(255,255,255,.16); background: rgba(255,255,255,.08); color: #e2e8f0; border-radius: 12px; padding: 10px 12px; cursor: pointer; }
.app-sidebar button:hover,
.app-sidebar button.active { background: rgba(37, 99, 235, .85); color: #fff; border-color: rgba(255,255,255,.25); }
</style>
""".strip()

    def _card_header(self, title: str, description: str, indent: str) -> str:
        if not title and not description:
            return f"{indent}<header class=\"ucg-card-header\"><div></div></header>"
        return f"""{indent}<header class="ucg-card-header">
{indent}  <div>
{indent}    <h2>{self._escape_html(title or "内容区")}</h2>
{indent}    <p>{self._escape_html(description or "")}</p>
{indent}  </div>
{indent}</header>"""

    def _action_buttons(self, contract: dict[str, Any], action_ids: list[str], indent: str) -> str:
        if not action_ids:
            return ""
        buttons = "\n".join(
            f'{indent}  <button class="ucg-btn primary" type="button" @click="handleAction({self._js(action_id)})">{self._escape_html(self._action_label(contract, action_id))}</button>'
            for action_id in action_ids
        )
        return f"{indent}<div class=\"ucg-actions\">\n{buttons}\n{indent}</div>"

    def _row_action_buttons(self, contract: dict[str, Any], action_ids: list[str], indent: str) -> str:
        return "\n".join(
            f'{indent}<button class="ucg-btn" type="button" @click="handleAction({self._js(action_id)}, row)">{self._escape_html(self._action_label(contract, action_id))}</button>'
            for action_id in action_ids
        )

    def _table_cell_expr(self, column: dict[str, Any]) -> str:
        field_key = self._text(column.get("fieldKey") or column.get("columnKey") or column.get("key"))
        ctype = self._text(column.get("type") or column.get("columnType")).upper()
        expr = "{{ displayValue(row[%s]) }}" % self._js(field_key)
        if ctype in {"TAG", "STATUS", "BADGE"}:
            return f"<span class=\"ucg-tag\">{expr}</span>"
        return expr

    def _render_tree_item(self, item: dict[str, Any], indent: str) -> str:
        title = self._text(item.get("title") or item.get("label") or item.get("name")) or "节点"
        desc = self._text(item.get("description") or item.get("key") or item.get("value"))
        children = self._as_list(item.get("children"))
        child_html = "\n".join(self._render_tree_item(child, indent + "  ") for child in children if isinstance(child, dict))
        return f"""{indent}<div class="ucg-tree-node"><span>{self._escape_html(title)}</span><strong>{self._escape_html(desc)}</strong></div>
{child_html}""".rstrip()

    def _root_components(self, contract: dict[str, Any]) -> list[dict[str, Any]]:
        tree = contract.get("componentTree")
        if tree is None and isinstance(contract.get("content"), dict):
            tree = contract["content"].get("componentTree")

        if isinstance(tree, list):
            return [item for item in tree if isinstance(item, dict)]
        if isinstance(tree, dict):
            children = self._children(tree)
            if children and self._component_type(tree) in {"ROOT", "PAGE", "LAYOUT"}:
                return children
            return [tree]

        components = contract.get("components")
        if isinstance(components, list):
            return [item for item in components if isinstance(item, dict)]
        return []

    def _children(self, component: dict[str, Any]) -> list[dict[str, Any]]:
        children = component.get("children")
        if isinstance(children, list):
            return [item for item in children if isinstance(item, dict)]
        return []

    def _flatten_components(self, components: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        def walk(node: dict[str, Any]) -> None:
            result.append(node)
            for child in self._children(node):
                walk(child)

        for component in components:
            walk(component)
        return result

    def _find_first_type(self, components: list[dict[str, Any]], types: set[str]) -> dict[str, Any] | None:
        for component in self._flatten_components(components):
            if self._component_type(component) in types:
                return component
        return None

    def _find_all_types(self, components: list[dict[str, Any]], types: set[str]) -> list[dict[str, Any]]:
        return [component for component in self._flatten_components(components) if self._component_type(component) in types]

    def _component_type(self, component: dict[str, Any]) -> str:
        return self._text(component.get("type") or component.get("componentType") or component.get("componentKind")).upper()

    def _component_items(self, component: dict[str, Any]) -> list[dict[str, Any]]:
        props = self._dict_at(component, "props")
        for key in ["items", "data", "metrics", "series", "steps", "nodes", "files"]:
            value = component.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
            value = props.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
        return []

    def _description_items_from_fields(self, component: dict[str, Any]) -> list[dict[str, Any]]:
        return [
            {"key": self._field_key(field), "fieldKey": self._field_key(field), "label": self._text(field.get("label"))}
            for field in self._as_list(component.get("fields"))
            if isinstance(field, dict)
        ]

    def _form_defaults(self, roots: list[dict[str, Any]]) -> dict[str, Any]:
        defaults: dict[str, Any] = {}
        for component in self._flatten_components(roots):
            for field in self._as_list(component.get("fields")):
                if not isinstance(field, dict):
                    continue
                key = self._field_key(field)
                ftype = self._text(field.get("type") or field.get("fieldType")).upper()
                if ftype in {"SWITCH", "CHECKBOX"}:
                    defaults[key] = bool(field.get("defaultValue", False))
                elif ftype == "NUMBER":
                    defaults[key] = field.get("defaultValue", 0)
                else:
                    defaults[key] = self._text(field.get("defaultValue") or field.get("value"))
        return defaults

    def _table_rows(self, table: dict[str, Any], contract: dict[str, Any]) -> list[dict[str, Any]]:
        props = self._dict_at(table, "props")
        for value in [table.get("rows"), props.get("rows"), props.get("dataSource")]:
            if isinstance(value, list) and value and isinstance(value[0], dict):
                return [dict(row) for row in value[:8]]

        mock = self._dict_at(contract, "mockData")
        component_id = self._text(table.get("componentId"))
        candidates = [
            mock.get(component_id) if component_id else None,
            mock.get("rows"),
            mock.get("list"),
            mock.get("records"),
            mock.get("tableData"),
        ]
        for value in candidates:
            if isinstance(value, list) and value and isinstance(value[0], dict):
                return [dict(row) for row in value[:8]]
            if isinstance(value, dict):
                rows = value.get("rows") or value.get("list") or value.get("records")
                if isinstance(rows, list) and rows and isinstance(rows[0], dict):
                    return [dict(row) for row in rows[:8]]

        columns = [col for col in self._as_list(table.get("columns")) if isinstance(col, dict) and not self._is_action_column(col)]
        row: dict[str, Any] = {"id": "mock-1"}
        for column in columns:
            key = self._text(column.get("fieldKey") or column.get("columnKey") or column.get("key"))
            label = self._text(column.get("label")) or key
            row[key] = self._sample_value(label, self._text(column.get("type") or column.get("columnType")))
        return [row]

    def _chart_items(self, items: list[Any]) -> list[tuple[str, int]]:
        result: list[tuple[str, int]] = []
        for index, item in enumerate(items[:8]):
            if isinstance(item, dict):
                label = self._text(item.get("label") or item.get("name") or item.get("title")) or f"{index + 1}"
                raw = item.get("value") or item.get("count") or item.get("amount") or 40
            else:
                label = str(index + 1)
                raw = item
            try:
                value = float(raw)
            except Exception:
                value = 40
            result.append((label, max(18, min(100, int(value)))))
        return result or [("一", 48), ("二", 72), ("三", 56), ("四", 88)]

    def _sample_value(self, label: str, value_type: str) -> Any:
        text = f"{label} {value_type}".lower()
        if "date" in text or "时间" in label or "日期" in label:
            return "2026-08-05"
        if "number" in text or "amount" in text or "金额" in label or "数量" in label or "库存" in label:
            return 128
        if "status" in text or "状态" in label:
            return "正常"
        return f"示例{label}"

    def _field_key(self, field: dict[str, Any]) -> str:
        return self._text(field.get("fieldKey") or field.get("key") or field.get("name")) or "field"

    def _is_action_column(self, column: dict[str, Any]) -> bool:
        label = self._text(column.get("label") or column.get("title") or column.get("name"))
        ctype = self._text(column.get("type") or column.get("columnType")).upper()
        key = self._text(column.get("columnKey") or column.get("fieldKey") or column.get("key")).lower()
        return ctype in {"ACTIONS", "ACTION"} or label in {"操作", "动作", "行操作", "操作列"} or key in {"action", "actions", "operation", "operations"}

    def _interactions(self, contract: dict[str, Any]) -> list[dict[str, Any]]:
        interactions = contract.get("interactions")
        if isinstance(interactions, list):
            return [item for item in interactions if isinstance(item, dict)]
        if isinstance(interactions, dict):
            return [item for item in interactions.values() if isinstance(item, dict)]
        return []

    def _interaction_id(self, interaction: dict[str, Any]) -> str:
        return self._text(interaction.get("interactionId") or interaction.get("actionId") or interaction.get("id") or interaction.get("key"))

    def _action_label(self, contract: dict[str, Any], action_id: str) -> str:
        for action in self._interactions(contract):
            if self._interaction_id(action) == action_id:
                return self._text(action.get("label") or action.get("title") or action.get("name")) or action_id
        return action_id or "操作"

    def _route_path(self, page_meta: dict[str, str], index: int) -> str:
        raw = page_meta.get("routePath") or "/" + self._slug(page_meta.get("key") or page_meta.get("title") or f"page-{index + 1}")
        route = "/" + str(raw).strip().strip("/")
        route = re.sub(r"/+", "/", route)
        return route or "/"

    def _relative_import(self, path: str) -> str:
        normalized = self._normalize_path(path)
        marker = "/frontend/src/modules/"
        if marker in normalized:
            tail = normalized.split(marker, 1)[1]
            parts = tail.split("/")
            if len(parts) >= 4:
                # pages/Page.vue -> sibling api/mock/types dirs
                filename = parts[-1]
                folder = parts[-2]
                if folder == "api":
                    return f"../api/{filename}"
                if folder == "mock":
                    return f"../mock/{filename}"
                if folder == "types":
                    return f"../types/{filename}"
        return normalized

    def _file(
        self,
        path: str,
        file_type: str,
        action: str,
        source: str,
        description: str,
        language: str,
        content: str,
        priority: int,
    ) -> DevelopmentCodeFilePayload:
        return DevelopmentCodeFilePayload(
            file_path=self._normalize_path(path),
            file_type=file_type,
            action=action,
            source=source,
            description=description,
            language=language,
            content=content.strip() + "\n",
            priority=priority,
        )

    def _dedupe_files(self, files: list[DevelopmentCodeFilePayload]) -> list[DevelopmentCodeFilePayload]:
        by_path: dict[str, DevelopmentCodeFilePayload] = {}
        for file in files:
            if file and file.file_path:
                by_path[file.file_path] = file
        return sorted(by_path.values(), key=lambda item: (item.priority, item.file_path))

    def _dict_at(self, value: dict[str, Any], key: str) -> dict[str, Any]:
        item = value.get(key) if isinstance(value, dict) else None
        return item if isinstance(item, dict) else {}

    def _as_list(self, value: Any) -> list[Any]:
        return value if isinstance(value, list) else []

    def _string_list(self, value: Any) -> list[str]:
        if not isinstance(value, list):
            return []
        return [str(item).strip() for item in value if str(item).strip()]

    def _first_text(self, *values: Any) -> str:
        for value in values:
            text = self._text(value)
            if text:
                return text
        return ""

    def _text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).replace("\r", " ").strip()

    def _normalize_path(self, value: str | None) -> str:
        return str(value or "").replace("\\", "/").strip().strip("/")

    def _slug(self, value: Any) -> str:
        text = self._text(value).lower()
        if not text:
            return ""
        text = re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "-", text).strip("-")
        if re.search(r"[\u4e00-\u9fff]", text):
            text = "-".join(hex(ord(ch))[2:] if "\u4e00" <= ch <= "\u9fff" else ch for ch in text)
        return text[:80] or "generated"

    def _pascal(self, value: Any) -> str:
        slug = self._slug(value)
        parts = re.split(r"[^0-9a-zA-Z]+", slug)
        name = "".join(part[:1].upper() + part[1:] for part in parts if part)
        if not name:
            name = "Generated"
        if name[0].isdigit():
            name = "Page" + name
        return name

    def _safe_var(self, value: str) -> str:
        text = re.sub(r"[^0-9a-zA-Z_]", "_", value)
        if not text or text[0].isdigit():
            text = "v_" + text
        return text

    def _js(self, value: Any) -> str:
        return json.dumps(value, ensure_ascii=False)

    def _escape_html(self, value: Any) -> str:
        text = self._text(value)
        return (
            text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
        )

    def _escape_js(self, value: Any) -> str:
        return self._text(value).replace("\\", "\\\\").replace("'", "\\'")
