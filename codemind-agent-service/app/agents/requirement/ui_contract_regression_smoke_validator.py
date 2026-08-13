# A9.10.14.4.3.15.3.4_UI_CONTRACT_REGRESSION_SMOKE_VALIDATOR
from __future__ import annotations

import re
from typing import Any

from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
from app.agents.requirement.prd_semantic_contract import build_page_semantic_contract
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.ui_contract import UiContractArtifact


class UiContractRegressionSmokeValidator:
    """Static regression smoke checks for PAGE UI_CONTRACT.

    This validator is intentionally deterministic and token-free.  It does not
    replace the LLM.  It checks whether the final UI_CONTRACT still obeys the
    MODULE_PRD.page_requirements contract after visual design, hardening, repair
    and Java-authority finalization.

    Strict mode is used on the LLM-designed path.  If the LLM output misses a
    hard requirement, raising here lets UiContractAgent fall back to the
    PRD-locked contract seed without another LLM retry.  Non-strict mode is used
    on deterministic fallback so we record warnings but do not break emergency
    fallback persistence.
    """

    MARK = "A9.10.14.4.3.15.3.4_UI_CONTRACT_REGRESSION_SMOKE_VALIDATOR"

    def validate(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
        *,
        strict: bool = True,
    ) -> UiContractArtifact:
        contract = self._build_contract(request)
        if not self._has_contract(contract):
            return artifact

        try:
            data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
        except Exception:
            return artifact

        if not isinstance(data, dict):
            return artifact

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        tree = page.get("componentTree") if isinstance(page.get("componentTree"), list) else []
        interactions = data.get("interactions") if isinstance(data.get("interactions"), list) else []
        page_states = data.get("pageStates") if isinstance(data.get("pageStates"), list) else []

        component_records = self._collect_component_records(tree)
        component_types = {
            self._text(item.get("type"), item.get("componentType")).upper()
            for item in component_records
            if isinstance(item, dict)
        }
        component_ids = {
            self._text(item.get("componentId"), item.get("component_id"), item.get("id"))
            for item in component_records
            if isinstance(item, dict)
        }
        action_ids = {
            self._text(item.get("actionId"), item.get("action_id"), item.get("id"))
            for item in interactions
            if isinstance(item, dict)
        }
        state_keys = {
            self._text(item.get("stateKey"), item.get("state_key"), item.get("key")).upper()
            for item in page_states
            if isinstance(item, dict)
        }

        visible_text = self._visible_contract_text(data)
        norm_visible = self._norm(visible_text)
        page_type = self._norm_page_type(contract)

        form_fields = self._items(contract, "formFields", "form_fields")
        filter_fields = self._items(contract, "filterFields", "filter_fields", "filters")
        table_columns = self._items(contract, "tableColumns", "table_columns", "columns")
        primary_actions = self._items(contract, "primaryActions", "primary_actions", "toolbarActions", "toolbar_actions")
        row_actions = self._items(contract, "rowActions", "row_actions")
        navigation_targets = self._items(contract, "navigationTargets", "navigation_targets", "navigationActions", "navigation_actions")

        blockers: list[str] = []
        warnings: list[str] = []

        if not component_records:
            blockers.append("componentTree is empty; current PAGE has no renderable UI components.")

        self._check_component_action_references(component_records, action_ids, blockers, warnings)
        self._check_action_targets(interactions, component_ids, blockers, warnings)
        self._check_page_states(state_keys, warnings)

        # Explicit contract labels must remain visible/selectable.  These are
        # cross-industry checks: product, order, ticket, warehouse document,
        # customer, dashboard and config pages all use the same label coverage
        # rule once PRD made a field/button/column explicit.
        self._require_labels("form field", form_fields, norm_visible, blockers)
        self._require_labels("filter field", filter_fields, norm_visible, blockers)
        self._require_labels("table column", table_columns, norm_visible, blockers)
        self._require_labels("primary action", primary_actions, norm_visible, blockers)
        self._require_labels("row action", row_actions, norm_visible, blockers)

        if page_type in {"LIST", "SEARCH"}:
            if table_columns and "TABLE" not in component_types:
                blockers.append("LIST/SEARCH page declares tableColumns but UI_CONTRACT has no TABLE component.")
            if filter_fields and not ({"FORM", "FIELD_GROUP"} & component_types):
                blockers.append("LIST/SEARCH page declares filterFields but UI_CONTRACT has no FORM/FIELD_GROUP filter component.")
            if row_actions and "TABLE" not in component_types:
                blockers.append("LIST/SEARCH page declares rowActions but has no TABLE to host row actions.")
            if "EMPTY_STATE" not in component_types:
                warnings.append("LIST/SEARCH page has no explicit EMPTY_STATE boundary component.")

        elif page_type in {"FORM", "CREATE", "EDIT"}:
            if form_fields and not ({"FORM", "FIELD_GROUP"} & component_types):
                blockers.append("FORM/CREATE/EDIT page declares formFields but UI_CONTRACT has no FORM/FIELD_GROUP component.")
            if not self._has_submit_like_action(interactions, primary_actions):
                blockers.append("FORM/CREATE/EDIT page has no submit/save/confirm primary interaction.")
            if not ({"SUCCESS", "ERROR"} <= state_keys):
                warnings.append("FORM/CREATE/EDIT page should expose SUCCESS and ERROR page states.")

        elif page_type == "DETAIL":
            if form_fields or table_columns:
                if not ({"CARD", "SECTION", "FORM", "FIELD_GROUP", "DESCRIPTION_LIST", "TABS"} & component_types):
                    blockers.append("DETAIL page declares fields but has no detail display section/card/form/tabs component.")
            if "PAGE_HEADER" not in component_types:
                warnings.append("DETAIL page has no PAGE_HEADER component.")

        elif page_type == "APPROVAL":
            if not ({"FORM", "FIELD_GROUP", "CARD", "SECTION"} & component_types):
                blockers.append("APPROVAL page has no approval form/card/section component.")
            if not self._has_approval_action(interactions, primary_actions):
                blockers.append("APPROVAL page has no approve/reject/pass/return style interaction.")
            if not ({"SUCCESS", "ERROR"} <= state_keys):
                warnings.append("APPROVAL page should expose SUCCESS and ERROR states.")

        elif page_type in {"DASHBOARD", "REPORT"}:
            if len(component_records) < 3:
                blockers.append("DASHBOARD/REPORT page should contain multiple visual regions, not a single bare component.")
            if not ({"CARD", "SECTION", "TABLE", "CHART", "METRIC", "STAT_CARD"} & component_types):
                warnings.append("DASHBOARD/REPORT page has no obvious metric/chart/table/card region.")

        elif page_type == "CONFIG":
            if form_fields and not ({"FORM", "FIELD_GROUP"} & component_types):
                blockers.append("CONFIG page declares configuration fields but has no FORM/FIELD_GROUP component.")
            if primary_actions and not self._has_any_action_label(interactions, primary_actions):
                blockers.append("CONFIG page primary actions from PRD are not materialized as interactions.")

        elif page_type in {"PORTAL", "HOME", "PROFILE", "WIZARD"}:
            if len(component_records) < 2:
                blockers.append(f"{page_type} page should have at least header/navigation/content structure.")
            if navigation_targets and not self._has_navigation_boundary(data):
                warnings.append(f"{page_type} page declares navigation targets but UI_CONTRACT navigationTargets/interactions are weak.")

        self._append_quality(data, blockers, warnings, page_type)

        if blockers and strict:
            raise ValueError(
                "UI_CONTRACT_REGRESSION_SMOKE_FAILED: "
                + "; ".join(blockers[:8])
            )

        try:
            return artifact.__class__.model_validate(data)
        except Exception:
            try:
                return UiContractArtifact.model_validate(data)
            except Exception:
                return artifact

    def _build_contract(self, request: AgentTaskRequest) -> dict[str, Any]:
        module_prd = self._extract_module_prd(request)
        current_page = self._extract_current_module(request)
        if isinstance(module_prd, dict):
            try:
                module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)
            except Exception:
                pass
        try:
            contract = build_page_semantic_contract(module_prd, current_page)
        except Exception:
            contract = {}
        return contract if isinstance(contract, dict) else {}

    def _has_contract(self, contract: dict[str, Any]) -> bool:
        if not isinstance(contract, dict) or not contract:
            return False
        if self._text(contract.get("pageType"), contract.get("page_type"), contract.get("universalPageType")):
            return True
        for key in ["formFields", "filterFields", "tableColumns", "primaryActions", "rowActions", "navigationTargets"]:
            if isinstance(contract.get(key), list) and contract.get(key):
                return True
        return False

    def _extract_module_prd(self, request: AgentTaskRequest) -> Any:
        context_package = request.context_package or {}
        artifacts = context_package.get("artifacts")
        if isinstance(artifacts, dict):
            for key, value in artifacts.items():
                if str(key or "").strip().upper() == "MODULE_PRD":
                    return self._unwrap_artifact_payload(value)
                if isinstance(value, dict):
                    value_type = self._text(value.get("artifact_type"), value.get("artifactType"), value.get("type")).upper()
                    if value_type == "MODULE_PRD":
                        return self._unwrap_artifact_payload(value)
        if isinstance(artifacts, list):
            for value in artifacts:
                if not isinstance(value, dict):
                    continue
                value_type = self._text(value.get("artifact_type"), value.get("artifactType"), value.get("type")).upper()
                if value_type == "MODULE_PRD":
                    return self._unwrap_artifact_payload(value)
        return {}

    def _extract_current_module(self, request: AgentTaskRequest) -> dict[str, Any]:
        context_package = request.context_package or {}
        input_payload = request.input or {}
        for candidate in [input_payload.get("current_module"), context_package.get("current_module"), context_package.get("module")]:
            if isinstance(candidate, dict):
                return candidate
        return {
            "module_id": request.module_id,
            "module_key": "",
            "module_name": "",
            "module_type": "PAGE",
        }

    def _unwrap_artifact_payload(self, value: Any) -> Any:
        if isinstance(value, dict) and isinstance(value.get("content"), dict):
            payload = dict(value.get("content") or {})
            for key in ["markdown", "contentMarkdown", "content_markdown"]:
                if key not in payload and value.get(key):
                    payload[key] = value.get(key)
            return payload
        return value

    def _collect_component_records(self, value: Any) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        def visit(node: Any) -> None:
            if isinstance(node, dict):
                if self._text(node.get("componentId"), node.get("component_id")) and self._text(node.get("type"), node.get("componentType")):
                    result.append(node)
                for key in ["children", "components", "items"]:
                    child = node.get(key)
                    if isinstance(child, list):
                        for item in child:
                            visit(item)
                tabs = node.get("tabs")
                if isinstance(tabs, list):
                    for tab in tabs:
                        if isinstance(tab, dict):
                            for key in ["children", "components", "items"]:
                                child = tab.get(key)
                                if isinstance(child, list):
                                    for item in child:
                                        visit(item)
            elif isinstance(node, list):
                for item in node:
                    visit(item)

        visit(value)
        return result

    def _visible_contract_text(self, value: Any) -> str:
        parts: list[str] = []

        def visit(node: Any) -> None:
            if isinstance(node, dict):
                for key, raw in node.items():
                    if key in {"markdown", "analysisSummary", "assumptions", "qualitySummary", "developmentRules"}:
                        continue
                    if isinstance(raw, str):
                        parts.append(raw)
                    elif isinstance(raw, (int, float, bool)):
                        parts.append(str(raw))
                    else:
                        visit(raw)
            elif isinstance(node, list):
                for item in node:
                    visit(item)

        visit(value)
        return " ".join(parts)

    def _items(self, source: dict[str, Any], *keys: str) -> list[Any]:
        for key in keys:
            value = source.get(key)
            if isinstance(value, list):
                return [item for item in value if item is not None]
        return []

    def _label(self, item: Any) -> str:
        if isinstance(item, dict):
            return self._text(
                item.get("label"),
                item.get("fieldName"),
                item.get("field_name"),
                item.get("columnName"),
                item.get("column_name"),
                item.get("actionName"),
                item.get("action_name"),
                item.get("name"),
                item.get("title"),
                item.get("fieldKey"),
                item.get("columnKey"),
                item.get("actionKey"),
                item.get("key"),
            )
        return self._text(item)

    def _require_labels(self, kind: str, items: list[Any], norm_visible: str, blockers: list[str]) -> None:
        for item in items:
            label = self._label(item)
            key = self._text(item.get("fieldKey"), item.get("columnKey"), item.get("actionKey"), item.get("key")) if isinstance(item, dict) else ""
            if not label and not key:
                continue
            probes = [self._norm(label), self._norm(key)]
            if not any(probe and probe in norm_visible for probe in probes):
                blockers.append(f"missing explicit PRD {kind}: {label or key}")

    def _check_component_action_references(self, components: list[dict[str, Any]], action_ids: set[str], blockers: list[str], warnings: list[str]) -> None:
        for component in components:
            cid = self._text(component.get("componentId"), component.get("component_id"), component.get("id"))
            action_values: list[Any] = []
            if isinstance(component.get("actionIds"), list):
                action_values.extend(component.get("actionIds") or [])
            props = component.get("props") if isinstance(component.get("props"), dict) else {}
            if isinstance(props.get("rowActionIds"), list):
                action_values.extend(props.get("rowActionIds") or [])
            for raw in action_values:
                aid = self._text(raw)
                if aid and aid not in action_ids:
                    blockers.append(f"component {cid} references missing interaction actionId={aid}")

    def _check_action_targets(self, interactions: list[Any], component_ids: set[str], blockers: list[str], warnings: list[str]) -> None:
        for action in interactions:
            if not isinstance(action, dict):
                continue
            aid = self._text(action.get("actionId"), action.get("action_id"), action.get("id"))
            typ = self._text(action.get("type")).upper()
            target_id = self._text(action.get("targetId"), action.get("target_id"))
            source_id = self._text(action.get("sourceComponentId"), action.get("source_component_id"))
            if source_id and source_id not in component_ids:
                blockers.append(f"interaction {aid} has invalid sourceComponentId={source_id}")
            if typ != "NAVIGATE" and target_id and target_id not in component_ids and not re.match(r"^[A-Z0-9_]+$", target_id):
                blockers.append(f"interaction {aid} has invalid targetId={target_id}")
            if typ == "NAVIGATE" and not self._text(action.get("targetPageId"), action.get("target_page_id")):
                blockers.append(f"NAVIGATE interaction {aid} has no targetPageId")

    def _check_page_states(self, state_keys: set[str], warnings: list[str]) -> None:
        if "DEFAULT" not in state_keys:
            warnings.append("pageStates has no DEFAULT state.")
        if "ERROR" not in state_keys:
            warnings.append("pageStates has no ERROR state.")

    def _has_submit_like_action(self, interactions: list[Any], primary_actions: list[Any]) -> bool:
        text = self._norm(" ".join(self._action_texts(interactions) + [self._label(x) for x in primary_actions]))
        return bool(re.search(r"保存|提交|确定|通过|确认|save|submit|confirm|approve", text, re.I))

    def _has_approval_action(self, interactions: list[Any], primary_actions: list[Any]) -> bool:
        text = self._norm(" ".join(self._action_texts(interactions) + [self._label(x) for x in primary_actions]))
        return bool(re.search(r"审核|审批|通过|驳回|退回|拒绝|approve|reject|audit|pass|return", text, re.I))

    def _has_any_action_label(self, interactions: list[Any], action_items: list[Any]) -> bool:
        text = self._norm(" ".join(self._action_texts(interactions)))
        return any(self._norm(self._label(item)) in text for item in action_items if self._label(item))

    def _action_texts(self, interactions: list[Any]) -> list[str]:
        result: list[str] = []
        for item in interactions:
            if isinstance(item, dict):
                result.append(self._text(item.get("label"), item.get("title"), item.get("actionId"), item.get("action_id"), item.get("id"), item.get("type")))
        return result

    def _has_navigation_boundary(self, data: dict[str, Any]) -> bool:
        targets = data.get("navigationTargets")
        interactions = data.get("interactions")
        return bool(isinstance(targets, list) and targets) or bool(
            isinstance(interactions, list)
            and any(isinstance(x, dict) and self._text(x.get("type")).upper() == "NAVIGATE" for x in interactions)
        )

    def _append_quality(self, data: dict[str, Any], blockers: list[str], warnings: list[str], page_type: str) -> None:
        quality = data.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            data["qualitySummary"] = quality

        marker = f"{self.MARK}: checked universalPageType={page_type or 'UNKNOWN'}"
        current_warnings = quality.get("warnings") if isinstance(quality.get("warnings"), list) else []
        current_blockers = quality.get("blockers") if isinstance(quality.get("blockers"), list) else []

        def merge(existing: list[Any], extra: list[str]) -> list[str]:
            result: list[str] = []
            seen: set[str] = set()
            for value in [*existing, *extra]:
                text = str(value or "").strip()
                if text and text not in seen:
                    seen.add(text)
                    result.append(text)
            return result

        quality["warnings"] = merge(current_warnings, [marker, *warnings])
        quality["blockers"] = merge(current_blockers, blockers)
        if blockers:
            try:
                quality["score"] = min(int(quality.get("score") or 80), 65)
            except Exception:
                quality["score"] = 65
        elif warnings:
            try:
                quality["score"] = min(int(quality.get("score") or 95), 88)
            except Exception:
                quality["score"] = 88

    def _norm_page_type(self, contract: dict[str, Any]) -> str:
        raw = self._text(
            contract.get("universalPageType"),
            contract.get("universal_page_type"),
            contract.get("pageType"),
            contract.get("page_type"),
            contract.get("layoutType"),
            contract.get("layout_type"),
        ).upper()
        text = self._norm(raw)
        if any(token in text for token in ["CREATE", "ADD", "EDIT", "FORM", "新增", "新建", "创建", "编辑", "录入"]):
            return "FORM"
        if any(token in text for token in ["APPROVAL", "AUDIT", "审核", "审批"]):
            return "APPROVAL"
        if any(token in text for token in ["DETAIL", "VIEW", "详情", "查看"]):
            return "DETAIL"
        if any(token in text for token in ["LIST", "SEARCH", "TABLE", "查询", "列表", "搜索"]):
            return "LIST"
        if any(token in text for token in ["DASHBOARD", "WORKBENCH", "KANBAN", "看板", "工作台"]):
            return "DASHBOARD"
        if any(token in text for token in ["REPORT", "ANALYTICS", "报表", "分析"]):
            return "REPORT"
        if any(token in text for token in ["CONFIG", "SETTING", "设置", "配置"]):
            return "CONFIG"
        if any(token in text for token in ["PORTAL", "HOME", "门户", "首页"]):
            return "PORTAL"
        if any(token in text for token in ["PROFILE", "个人中心", "资料"]):
            return "PROFILE"
        if any(token in text for token in ["WIZARD", "STEP", "向导", "步骤"]):
            return "WIZARD"
        if contract.get("tableColumns") or contract.get("filterFields"):
            return "LIST"
        if contract.get("formFields"):
            return "FORM"
        return "PAGE"

    def _text(self, *values: Any) -> str:
        for value in values:
            if value is not None and str(value).strip():
                return str(value).strip()
        return ""

    def _norm(self, value: Any) -> str:
        return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value or "").strip().lower())
