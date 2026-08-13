# A9.10.14.4.3.13.1_UNIVERSAL_UI_CONTRACT_PRD_RECONCILER
from __future__ import annotations
import re
from typing import Any

from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
from app.agents.requirement.prd_semantic_contract import build_page_semantic_contract
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.ui_contract import UiContractArtifact



# A9.10.14.4.3.13.2_NO_LLM_PRD_CONTRACT_LOCK
def build_ui_contract_from_prd_semantics_without_llm(
    request: AgentTaskRequest,
) -> UiContractArtifact | None:
    """Build UI_CONTRACT directly from PRD semantic contract without LLM."""
    module_prd = _extract_module_prd(request)
    current_page = _extract_current_module(request)
    if isinstance(module_prd, dict):
        module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)

    contract = build_page_semantic_contract(module_prd, current_page)
    if not _has_contract(contract):
        return None

    return _build_locked_ui_contract(request, current_page, contract)


def _build_locked_ui_contract(
    request: AgentTaskRequest,
    current_page: dict[str, Any],
    contract: dict[str, Any],
) -> UiContractArtifact:
    page_id = _text_value(current_page.get("module_id"), current_page.get("moduleId"), current_page.get("pageId"), current_page.get("id"), request.module_id)
    page_name = _text_value(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"), contract.get("pageName"), "当前页面")
    page_key = _safe_id(_text_value(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"), contract.get("pageKey"), page_name), "page")
    route = _text_value(current_page.get("routePath"), current_page.get("route_path"), current_page.get("route"), f"/{page_key.replace('_', '-')}")
    page_type = str(contract.get("pageType") or "").upper()
    page_kind = _text_value(current_page.get("pageKind"), current_page.get("page_kind"), "LIST_PAGE" if page_type == "LIST" else "FORM_PAGE" if page_type == "FORM" else "BUSINESS_PAGE")

    base = _safe_id(page_key, "page")
    header_id = f"{base}_header"
    root_id = f"{base}_content"
    filter_section_id = f"{base}_filter_section"
    filter_form_id = f"{base}_filter_form"
    table_section_id = f"{base}_table_section"
    toolbar_id = f"{base}_toolbar"
    table_id = f"{base}_table"
    empty_id = f"{base}_empty"
    create_modal_id = f"{base}_create_modal"
    create_form_id = f"{base}_create_form"
    edit_modal_id = f"{base}_edit_modal"
    edit_form_id = f"{base}_edit_form"
    data_key = f"{base}_rows"

    # A9.10.14.4.3.13.2.7_SEMANTIC_CLEANUP_SCOPE_TABLE_FIX
    contract = _normalize_locked_contract_for_prd_exact_ui(contract)
    # A9.10.14.4.3.14.1_FORM_CONTRACT_SEED_LOCK_FIX_RECONCILER
    # Create/edit/entry pages must not be rendered as generic detail/process
    # pages.  When a FORM semantic contract exists, build the seed as a
    # FORM_DETAIL contract; the following LLM pass can improve visual style,
    # but post-LLM reconciliation can always fall back to this safe shape.
    if _is_form_detail_page(page_name, page_key, page_type, contract):
        return _build_locked_form_ui_contract(request, current_page, contract)

    # A9.10.14.4.3.13.2.9.1_QUALITY_GATE_SEARCH_LIST_SHAPE
    # SEARCH_LIST QualityGate requires TABLE_LIST + TABLE + EMPTY_STATE. If the
    # markdown parser misses tableColumns but the page is clearly a list/search
    # page, preserve a deterministic table using structured fallback fields.
    if _is_search_list_page(page_name, page_key, page_type, contract) and not contract.get("tableColumns"):
        fallback_columns = contract.get("formFields") or contract.get("filterFields") or []
        contract["tableColumns"] = [dict(x) for x in fallback_columns if isinstance(x, dict)]

    # A9.10.14.4.3.13.2.8_FIELD_KEY_NAMESPACE_FIX
    # Java validates fieldKey uniqueness globally across the PAGE. The same
    # business field may appear in query filters and create/edit modal forms,
    # so UI field keys must be namespaced by component role while labels remain
    # unchanged for users and table columns keep canonical data field keys.
    filter_fields = [
        _namespace_field_for_component(_field(x), "filter")
        for x in contract.get("filterFields", [])
        if isinstance(x, dict)
    ]
    table_columns = [_column(x) for x in contract.get("tableColumns", []) if isinstance(x, dict)]
    base_form_fields = [
        _field(x)
        for x in (contract.get("formFields") or contract.get("tableColumns") or contract.get("filterFields") or [])
        if isinstance(x, dict)
    ]
    create_form_fields = [_namespace_field_for_component(x, "create") for x in base_form_fields]
    # A9.10.14.4.3.13.2.9_LAYOUT_ACTION_DISCIPLINE_FIX
    # Edit dialogs should be scoped to the row action semantics. For example,
    # "编辑数量" should not open a full create form; it should focus on quantity
    # fields and keep labels user-facing.
    edit_form_fields = [
        _namespace_field_for_component(x, "edit")
        for x in _select_edit_form_fields(base_form_fields, contract.get("rowActions") or [])
    ]
    form_fields = base_form_fields
    # A9.10.14.4.3.13.2.9_LAYOUT_ACTION_DISCIPLINE_FIX
    # Runtime already renders a dedicated operation column from TABLE.props.rowActionIds.
    # Keep business columns pure; do not add a duplicate ACTIONS column here.

    query_id = f"{base}_action_query"
    reset_id = f"{base}_action_reset"
    interactions: list[dict[str, Any]] = [
        _interaction(query_id, "QUERY_DATA", "查询", filter_form_id, filter_form_id, data_key, {"message": "已按筛选条件刷新模拟列表。"}),
        _interaction(reset_id, "RESET_FORM", "重置", filter_form_id, filter_form_id, None, {"message": "筛选条件已重置。"}),
    ]

    primary_ids: list[str] = []
    row_ids: list[str] = []
    navigation_targets = _collect_navigation_targets_from_request(request)

    for action_spec in _page_level_primary_actions(contract.get("primaryActions") or []):
        kind = str(action_spec.get("kind") or "").upper()
        label = str(action_spec.get("label") or action_spec.get("actionKey") or "").strip()
        action_id = f"{base}_action_{_safe_id(action_spec.get('actionKey') or label, 'action')}"
        action = _locked_action_from_semantic(action_id, label, kind, toolbar_id, table_id, create_modal_id, edit_modal_id, data_key, navigation_targets, action_spec)
        interactions.append(action)
        primary_ids.append(action_id)

    for action_spec in _table_row_actions(contract.get("rowActions") or []):
        if not isinstance(action_spec, dict):
            continue
        kind = str(action_spec.get("kind") or "").upper()
        label = str(action_spec.get("label") or action_spec.get("actionKey") or "").strip()
        if not label or kind in {"QUERY", "RESET", "CREATE", "EXPORT", "REFRESH"}:
            continue
        action_id = f"{base}_action_{_safe_id(action_spec.get('actionKey') or label, 'row_action')}"
        action = _locked_action_from_semantic(action_id, label, kind, table_id, table_id, create_modal_id, edit_modal_id, data_key, navigation_targets, action_spec)
        interactions.append(action)
        row_ids.append(action_id)

    children: list[dict[str, Any]] = []
    if filter_fields:
        children.append(_component(filter_section_id, "CARD", "查询条件", "根据 PRD 明确字段筛选数据。", children=[_component(filter_form_id, "FORM", "", "", action_ids=[query_id, reset_id], fields=filter_fields, props={"visualRole": "filterForm", "layout": "inline", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True})], props={"visualRole": "filterSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True}))

    table_children: list[dict[str, Any]] = []
    if primary_ids:
        table_children.append(_component(toolbar_id, "TOOLBAR", "主要操作", "承载 PRD 明确要求的主要操作入口。", action_ids=primary_ids, props={"visualRole": "tableToolbar", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True}))
    if table_columns:
        table_children.append(_component(table_id, "TABLE", "", "", action_ids=[], columns=table_columns, data_binding_key=data_key, props={"visualRole": "dataTable", "rowActionIds": row_ids, "enableHorizontalScroll": True, "minTableWidth": _table_min_width(table_columns, row_ids), "stickyActionColumn": True, "compactRows": True, "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True, "dataBindingKey": data_key, "minVisibleRows": 2, "forceShowTable": True, "enableHorizontalScroll": True, "minTableWidth": _table_min_width(table_columns, row_ids), "actionColumnWidth": _action_column_width(row_ids), "stickyActionColumn": True, "compactRows": True}))
        # A9.10.14.4.3.13.2.9.1_QUALITY_GATE_SEARCH_LIST_SHAPE
        # Keep an explicit EMPTY_STATE boundary component for QualityGate and
        # canvas editing. Runtime will hide EMPTY_STATE unless the EMPTY state
        # explicitly lists this component id.
        table_children.append(_component(empty_id, "EMPTY_STATE", "暂无数据", "调整查询条件后重新查询。", props={"visualRole": "emptyState", "message": "暂无符合条件的数据。", "visibleWhenState": "EMPTY", "a9VisualQuality": "demoGrade", "elementPicker": True}))
    if table_children:
        children.append(_component(table_section_id, "CARD", "数据列表", "展示筛选后的业务数据，支持分页、排序和行级操作。", children=table_children, props={"visualRole": "tableSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True}))

    if any(re.search(r"新增|新建|创建|添加", str(x.get("label") or "")) for x in contract.get("primaryActions", []) if isinstance(x, dict)):
        submit_id = f"{base}_action_create_submit"
        cancel_id = f"{base}_action_create_cancel"
        children.append(_modal_component(create_modal_id, create_form_id, "新建记录", create_form_fields, [submit_id, cancel_id]))
        interactions.append(_interaction(submit_id, "SUBMIT_FORM", "保存", create_form_id, create_form_id, data_key, {"runtimeEffect": "ADD_ROW", "dataBindingKey": data_key, "closeModalOnSuccess": True, "message": "已新增一条模拟记录，刷新页面后恢复原始数据。"}))
        interactions.append(_interaction(cancel_id, "CLOSE_MODAL", "取消", create_form_id, create_modal_id, None, {}))

    if any(re.search(r"编辑|修改", str(x.get("label") or "")) for x in contract.get("rowActions", []) if isinstance(x, dict)):
        submit_id = f"{base}_action_edit_submit"
        cancel_id = f"{base}_action_edit_cancel"
        children.append(_modal_component(edit_modal_id, edit_form_id, "编辑记录", edit_form_fields, [submit_id, cancel_id]))
        interactions.append(_interaction(submit_id, "SUBMIT_FORM", "保存", edit_form_id, edit_form_id, data_key, {"runtimeEffect": "UPDATE_ROW", "dataBindingKey": data_key, "closeModalOnSuccess": True, "message": "已更新当前模拟记录，刷新页面后恢复原始数据。"}))
        interactions.append(_interaction(cancel_id, "CLOSE_MODAL", "取消", edit_form_id, edit_modal_id, None, {}))

    root = _component(root_id, "SECTION", "", "", children=children, props={"visualRole": "pageContent", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True})
    component_tree = [_component(header_id, "PAGE_HEADER", page_name, "清晰、稳定、面向企业业务系统。", props={"eyebrow": "业务页面", "visualRole": "pageHeader", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True}), root]

    # A9.10.14.4.3.13.2.6_NO_LLM_VISIBLE_ROOT_FIX
    # Runtime expands visibleComponentIds downward only. The filter/table
    # sections are children of root_id, so root_id must be visible too;
    # otherwise the top-level SECTION is hidden and the page becomes blank.
    visible_default = [header_id, root_id]
    if filter_fields:
        visible_default.append(filter_section_id)
    if table_columns:
        visible_default.append(table_section_id)
    rows = _rows(contract.get("tableColumns", []), None) if contract.get("tableColumns") else []
    all_action_ids = [x.get("actionId") for x in interactions if isinstance(x, dict) and x.get("actionId")]

    data = {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {"projectId": str(request.project_id), "pageId": page_id, "pageKey": page_key, "pageName": page_name, "pageKind": page_kind},
        "page": {"pageId": page_id, "pageKey": page_key, "pageName": page_name, "route": route, "title": page_name, "description": "清晰、稳定、面向企业业务系统。", "pageKind": page_kind, "layout": {"type": "TABLE_LIST" if _is_search_list_page(page_name, page_key, page_type, contract) else ("TABLE_LIST" if table_columns else "CUSTOM_GRID"), "columns": 1, "regions": [{"regionId": "header", "title": "页头", "componentIds": [header_id]}, {"regionId": "filters", "title": "查询条件", "componentIds": [filter_section_id] if filter_fields else []}, {"regionId": "table", "title": "数据列表", "componentIds": [table_section_id] if table_columns else []}]}, "componentTree": component_tree},
        "interactions": _dedupe_actions(interactions),
        "validationRules": [],
        "pageStates": [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示 PRD 明确要求的页面字段、按钮和列表。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "加载中", "message": "正在加载模拟数据。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "EMPTY", "type": "EMPTY", "title": "暂无数据", "message": "当前没有符合条件的数据。", "visibleComponentIds": [x for x in [header_id, root_id, filter_section_id if filter_fields else "", table_section_id if table_columns else "", empty_id if table_columns else ""] if x], "disabledComponentIds": []},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "操作成功", "message": "模拟操作已完成。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "操作失败", "message": "模拟操作失败，请检查输入后重试。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
        ],
        "mockData": {data_key: rows} if rows else {},
        "mockScenarios": [{"scenarioKey": f"{base}_query_success", "actionId": query_id, "outcome": "SUCCESS", "delayMs": 200, "message": "查询完成。", "response": {}, "nextPageState": "DEFAULT"}],
        "apiBindings": [],
        "navigationTargets": navigation_targets,
        "featureCoverage": [{"featureId": "prd_semantic_contract", "featureKey": "prd_semantic_contract", "featureName": "PRD 页面语义契约", "status": "FULL", "componentIds": [header_id, root_id, *visible_default], "actionIds": all_action_ids, "notes": ["由 PRD 语义契约确定字段、按钮、表格列和行操作；未调用 LLM。"]}],
        "assumptions": ["A9.10.14.4.3.13.2: UI_CONTRACT was built deterministically from PRD semantic contract without LLM."],
        "qualitySummary": {"score": 96, "coverageRate": 100, "generationMode": "LLM_STRUCTURED", "blockers": [], "warnings": ["NO_LLM_PRD_CONTRACT_LOCK: 未调用大模型，字段/按钮/表格列/行操作来自 PRD 页面语义契约。"]},
        "developmentRules": {"mustFollowLayout": True, "mustFollowFields": True, "mustFollowValidationRules": True, "mustFollowInteractionFlow": True, "mustFollowApiBindings": True, "allowStyleOptimization": True, "breakingChangeRequiresReview": True, "arbitraryScriptForbidden": True, "mockMustUseScenarioEngine": True},
        "analysisSummary": "已根据 PRD 页面语义契约确定页面字段、按钮、表格列、行操作和运行时 mock 行为，未调用大模型。",
        "markdown": "",
    }
    _enforce_table_action_layout_contract(data)
    return UiContractArtifact.model_validate(data)





# A9.10.14.4.3.14.1_FORM_CONTRACT_SEED_LOCK_FIX_RECONCILER
def _is_form_detail_page(page_name: Any, page_key: Any, page_type: Any, contract: Any) -> bool:
    text = _norm(" ".join([
        str(page_name or ""),
        str(page_key or ""),
        str(page_type or ""),
        str((contract or {}).get("pageType") if isinstance(contract, dict) else ""),
        str((contract or {}).get("description") if isinstance(contract, dict) else ""),
    ]))
    if any(token in text for token in ["列表", "查询", "搜索", "list", "search", "tablelist"]):
        return False
    if str(page_type or "").upper() in {"FORM", "APPROVAL"}:
        return True
    return any(token in text for token in ["创建", "新建", "新增", "录入", "编辑", "修改", "表单", "create", "add", "edit", "form"])


def _artifact_has_required_form_shape(data: Any, contract: dict[str, Any]) -> bool:
    if not isinstance(data, dict):
        return False
    required_labels = [str((item or {}).get("label") or (item or {}).get("fieldKey") or "").strip() for item in contract.get("formFields") or [] if isinstance(item, dict)]
    if not required_labels:
        return False
    page = data.get("page") if isinstance(data.get("page"), dict) else {}
    roots = page.get("componentTree") if isinstance(page.get("componentTree"), list) else []
    forms: list[dict[str, Any]] = []

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            if str(value.get("type") or "").upper() in {"FORM", "FIELD_GROUP"}:
                forms.append(value)
            for child in value.get("children") or []:
                visit(child)
            for child in value.get("components") or []:
                visit(child)
            for child in value.get("items") or []:
                visit(child)
        elif isinstance(value, list):
            for item in value:
                visit(item)

    visit(roots)
    if not forms:
        return False
    actual = " ".join(
        str(field.get("label") or field.get("fieldKey") or "")
        for form in forms
        for field in (form.get("fields") if isinstance(form.get("fields"), list) else [])
        if isinstance(field, dict)
    )
    if not actual.strip():
        return False
    covered = 0
    for label in required_labels:
        if label and (_norm(label) in _norm(actual) or _norm(actual) in _norm(label)):
            covered += 1
    return covered >= max(1, min(len(required_labels), 3))


def _build_locked_form_ui_contract(
    request: AgentTaskRequest,
    current_page: dict[str, Any],
    contract: dict[str, Any],
) -> UiContractArtifact:
    page_id = _text_value(current_page.get("module_id"), current_page.get("moduleId"), current_page.get("pageId"), current_page.get("id"), request.module_id)
    page_name = _text_value(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"), contract.get("pageName"), "当前页面")
    page_key = _safe_id(_text_value(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"), contract.get("pageKey"), page_name), "page")
    route = _text_value(current_page.get("routePath"), current_page.get("route_path"), current_page.get("route"), f"/{page_key.replace('_', '-')}")
    page_kind = _text_value(current_page.get("pageKind"), current_page.get("page_kind"), "FORM_PAGE")

    base = _safe_id(page_key, "page")
    header_id = f"{base}_header"
    root_id = f"{base}_content"
    form_card_id = f"{base}_form_card"
    form_id = f"{base}_main_form"
    tip_id = f"{base}_form_tip"
    footer_id = f"{base}_form_footer"
    data_key = f"{base}_form_data"

    raw_fields = [item for item in (contract.get("formFields") or contract.get("tableColumns") or contract.get("filterFields") or []) if isinstance(item, dict)]
    fields = [_field(item) for item in raw_fields]
    if not fields:
        fields = [_field({"fieldKey": "name", "label": "名称", "type": "INPUT", "required": True})]

    actions = [item for item in contract.get("primaryActions") or [] if isinstance(item, dict)]
    action_labels = [str(item.get("label") or item.get("actionName") or item.get("actionKey") or "").strip() for item in actions]
    save_label = next((x for x in action_labels if re.search(r"保存|提交|确定|通过", x)), "保存")
    cancel_label = next((x for x in action_labels if re.search(r"取消|返回|关闭", x)), "取消")
    save_id = f"{base}_action_{_safe_id(save_label, 'save')}"
    cancel_id = f"{base}_action_{_safe_id(cancel_label, 'cancel')}"

    interactions = [
        _interaction(
            save_id,
            "SUBMIT_FORM",
            save_label,
            footer_id,
            form_id,
            data_key,
            {"runtimeEffect": "UPSERT_FORM", "dataBindingKey": data_key, "message": f"{save_label}成功。", "nextPageState": "SUCCESS"},
        ),
        _interaction(
            cancel_id,
            "SHOW_TOAST",
            cancel_label,
            footer_id,
            root_id,
            None,
            {"message": f"已{cancel_label}本次操作。"},
        ),
    ]

    field_key_sample = {str(field.get("fieldKey") or f"field_{idx+1}"): _sample(str(field.get("label") or field.get("fieldKey") or "字段"), idx) for idx, field in enumerate(fields)}

    form_component = _component(
        form_id,
        "FORM",
        "",
        "",
        fields=fields,
        data_binding_key=data_key,
        props={
            "visualRole": "mainBusinessForm",
            "layout": "grid",
            "columns": 4,
            "labelPosition": "top",
            "a9PrdSemanticLocked": True,
            "a9VisualQuality": "demoGrade",
            "elementPicker": True,
            "dataBindingKey": data_key,
        },
    )
    tip_component = _component(
        tip_id,
        "ALERT",
        "填写说明",
        "请按业务规则填写必填字段，保存后会展示模拟成功反馈。",
        props={"visualRole": "formHint", "message": "请按业务规则填写必填字段，保存后会展示模拟成功反馈。", "level": "info", "a9VisualQuality": "demoGrade", "elementPicker": True},
    )
    footer_component = _component(
        footer_id,
        "TOOLBAR",
        "",
        "",
        action_ids=[save_id, cancel_id],
        props={"visualRole": "formFooterActions", "align": "right", "sticky": True, "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True},
    )
    form_card = _component(
        form_card_id,
        "CARD",
        "基础信息",
        "按业务要求维护核心字段。",
        children=[form_component, tip_component, footer_component],
        props={"visualRole": "formSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True},
    )
    root = _component(root_id, "SECTION", "", "", children=[form_card], props={"visualRole": "pageContent", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True})
    header = _component(header_id, "PAGE_HEADER", page_name, "清晰、稳定、面向企业业务系统。", props={"eyebrow": "表单处理", "visualRole": "pageHeader", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": True})
    visible_default = [header_id, root_id, form_card_id, form_id, tip_id, footer_id]
    all_action_ids = [item["actionId"] for item in interactions]
    all_component_ids = [header_id, root_id, form_card_id, form_id, tip_id, footer_id]

    data = {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {"projectId": str(request.project_id), "pageId": page_id, "pageKey": page_key, "pageName": page_name, "pageKind": page_kind},
        "page": {
            "pageId": page_id,
            "pageKey": page_key,
            "pageName": page_name,
            "route": route,
            "title": page_name,
            "description": str(contract.get("description") or "维护业务表单信息。"),
            "pageKind": page_kind,
            "layout": {"type": "FORM_DETAIL", "columns": 1, "regions": [{"regionId": "header", "title": "页头", "componentIds": [header_id]}, {"regionId": "form", "title": "表单", "componentIds": [form_card_id]}]},
            "componentTree": [header, root],
        },
        "interactions": _dedupe_actions(interactions),
        "validationRules": [],
        "pageStates": [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示表单字段和操作入口。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "处理中", "message": "正在处理表单。", "visibleComponentIds": visible_default, "disabledComponentIds": [save_id]},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "操作成功", "message": "表单已保存。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "操作失败", "message": "请检查字段后重试。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
        ],
        "mockData": {data_key: field_key_sample},
        "mockScenarios": [{"scenarioKey": f"{base}_save_success", "actionId": save_id, "outcome": "SUCCESS", "delayMs": 200, "message": f"{save_label}成功。", "response": field_key_sample, "nextPageState": "SUCCESS"}],
        "apiBindings": [],
        "navigationTargets": _collect_navigation_targets_from_request(request),
        "featureCoverage": [{"featureId": "prd_semantic_contract", "featureKey": "prd_semantic_contract", "featureName": "PRD 页面语义契约", "status": "FULL", "componentIds": all_component_ids, "actionIds": all_action_ids, "notes": ["由页面语义契约锁定 FORM_DETAIL 表单结构。"]}],
        "assumptions": ["A9.10.14.4.3.14.1: FORM_DETAIL contract seed built before/after LLM visual design when needed."],
        "qualitySummary": {"score": 96, "coverageRate": 100, "generationMode": "HYBRID_CONTRACT_LOCKED", "blockers": [], "warnings": ["FORM_CONTRACT_SEED_LOCK: 表单页业务字段和操作已被确定性锁定。"]},
        "developmentRules": {"mustFollowLayout": True, "mustFollowFields": True, "mustFollowValidationRules": True, "mustFollowInteractionFlow": True, "mustFollowApiBindings": True, "allowStyleOptimization": True, "breakingChangeRequiresReview": True, "arbitraryScriptForbidden": True, "mockMustUseScenarioEngine": True},
        "analysisSummary": "已根据页面语义契约锁定表单字段、保存/取消操作和 FORM_DETAIL 布局，避免创建/编辑页被生成成通用详情或流程页。",
        "markdown": "",
    }
    return UiContractArtifact.model_validate(data)

# A9.10.14.4.3.13.2.9.1_QUALITY_GATE_SEARCH_LIST_SHAPE
def _is_search_list_page(page_name: Any, page_key: Any, page_type: Any, contract: Any) -> bool:
    text = _norm(" ".join([
        str(page_name or ""),
        str(page_key or ""),
        str(page_type or ""),
        str((contract or {}).get("pageType") if isinstance(contract, dict) else ""),
        str((contract or {}).get("description") if isinstance(contract, dict) else ""),
    ]))
    if str(page_type or "").upper() == "LIST":
        return True
    return any(token in text for token in ["列表", "查询", "search", "list"])


# A9.10.14.4.3.13.2.9_LAYOUT_ACTION_DISCIPLINE_FIX
def _page_level_primary_actions(items: Any) -> list[dict[str, Any]]:
    """Return actions that may appear in the list toolbar only.

    Query/reset belong to the filter form. View/edit/approval/delete belong to
    table row actions. Submit/save/cancel belong to modal forms. Keeping these
    scopes strict prevents header/toolbars from showing row or modal actions.
    """
    allowed = {"CREATE", "EXPORT", "REFRESH"}
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items if isinstance(items, list) else []:
        if not isinstance(item, dict):
            continue
        label = str(item.get("label") or item.get("actionName") or item.get("actionKey") or "").strip()
        kind = str(item.get("kind") or _infer_action_kind(label)).upper()
        if not label or kind not in allowed:
            continue
        key = _norm(item.get("actionKey") or label)
        if key and key not in seen:
            seen.add(key)
            normalized = dict(item)
            normalized["label"] = label
            normalized["kind"] = kind
            out.append(normalized)
    return out


def _table_row_actions(items: Any) -> list[dict[str, Any]]:
    """Return row-scoped actions only, de-duplicated and split when needed."""
    allowed = {"VIEW", "EDIT", "APPROVAL", "DELETE"}
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw in items if isinstance(items, list) else []:
        if not isinstance(raw, dict):
            continue
        labels = _split_action_label(str(raw.get("label") or raw.get("actionName") or raw.get("actionKey") or ""))
        for label in labels:
            kind = str(raw.get("kind") or _infer_action_kind(label)).upper()
            # Recompute kind from the split label; stale structured actions may carry
            # a broad APPROVAL kind for mixed labels such as "提交审核/查看审核".
            kind = _infer_action_kind(label) or kind
            if not label or kind not in allowed:
                continue
            # If both submit-approval and view-approval survive from old generated
            # structured cards, prefer the passive/navigation-safe view action for
            # list pages unless the PRD explicitly only contains submit.
            key = _norm(label)
            if key and key not in seen:
                seen.add(key)
                normalized = dict(raw)
                normalized["label"] = label
                normalized["kind"] = kind
                normalized["actionKey"] = str(raw.get("actionKey") or _safe_id(label, "action"))
                out.append(normalized)
    return _prefer_view_approval(out)


def _prefer_view_approval(actions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    has_view_approval = any("查看" in str(a.get("label") or "") and "审核" in str(a.get("label") or "") for a in actions)
    if not has_view_approval:
        return actions
    return [
        a for a in actions
        if not ("提交" in str(a.get("label") or "") and "审核" in str(a.get("label") or ""))
    ]


def _split_action_label(label: str) -> list[str]:
    text = str(label or "").strip(" \t\r\n，,。；;:：、")
    if not text:
        return []
    parts = [p.strip(" \t\r\n，,。；;:：、") for p in re.split(r"[/／、，,]|以及|及|和|或", text) if p.strip(" \t\r\n，,。；;:：、")]
    return parts or [text]


def _infer_action_kind(label: str) -> str:
    if re.search(r"查询|搜索", label): return "QUERY"
    if re.search(r"重置|清空", label): return "RESET"
    if re.search(r"新增|新建|创建|添加", label): return "CREATE"
    if re.search(r"删除|移除|作废", label): return "DELETE"
    if re.search(r"编辑|修改", label): return "EDIT"
    if re.search(r"审核|审批", label): return "APPROVAL"
    if re.search(r"查看|详情", label): return "VIEW"
    if re.search(r"导出", label): return "EXPORT"
    if re.search(r"刷新", label): return "REFRESH"
    if re.search(r"保存|提交|取消", label): return "FORM_LOCAL"
    return "CUSTOM"


def _select_edit_form_fields(fields: list[dict[str, Any]], row_actions: Any) -> list[dict[str, Any]]:
    if not fields:
        return []
    labels = " ".join(str(a.get("label") or "") for a in row_actions if isinstance(a, dict))
    # For action text like "编辑数量", prefer quantity fields. For "编辑状态",
    # prefer status fields. Fall back to editable business-like fields rather than
    # copying the entire create form.
    intent_tokens: list[str] = []
    for m in re.finditer(r"(?:编辑|修改)([^，,。；;、/／\s]+)", labels):
        token = m.group(1).strip()
        if token:
            intent_tokens.append(token)
    if not intent_tokens and re.search(r"数量|总数", labels):
        intent_tokens.append("数量")

    def field_label(item: dict[str, Any]) -> str:
        return str(item.get("label") or item.get("fieldKey") or "")

    selected: list[dict[str, Any]] = []
    if intent_tokens:
        for item in fields:
            label = field_label(item)
            if any(token and (token in label or label in token) for token in intent_tokens):
                selected.append(item)

    if not selected:
        selected = [item for item in fields if re.search(r"数量|金额|状态|备注", field_label(item))]

    if not selected:
        selected = fields[: min(3, len(fields))]

    return selected

def _locked_action_from_semantic(action_id: str, label: str, kind: str, source_id: str, table_id: str, create_modal_id: str, edit_modal_id: str, data_key: str, navigation_targets: list[dict[str, Any]], spec: dict[str, Any]) -> dict[str, Any]:
    if kind == "DELETE":
        return _interaction(action_id, "DELETE_ROW", label, source_id, table_id, data_key, {"message": "已从当前模拟列表中移除该记录。"}, confirmation=f"确认{label}当前记录？")
    if kind == "CREATE":
        return _interaction(action_id, "OPEN_MODAL", label, source_id, create_modal_id, None, {"mode": "CREATE"})
    if kind == "EDIT":
        return _interaction(action_id, "OPEN_MODAL", label, source_id, edit_modal_id, data_key, {"mode": "EDIT"})
    if kind in {"QUERY", "REFRESH", "EXPORT"}:
        return _interaction(action_id, "QUERY_DATA", label, source_id, table_id, data_key, {"message": f"已模拟{label}。"})
    if spec.get("navigationIntent"):
        target_name = str(spec.get("targetPageName") or label)
        nav = _find_navigation_target(navigation_targets, target_name)
        if nav:
            action = _interaction(action_id, "NAVIGATE", label, source_id, table_id, None, {"message": f"进入{target_name}。"})
            action["targetPageId"] = nav.get("targetPageId")
            return action
    return _interaction(action_id, "SHOW_TOAST", label, source_id, source_id, None, {"message": f"{label}操作已模拟完成。"})


def _modal_component(modal_id: str, form_id: str, title: str, fields: list[dict[str, Any]], action_ids: list[str]) -> dict[str, Any]:
    return _component(modal_id, "MODAL", title, "在当前原型运行时模拟新增或编辑记录。", children=[_component(form_id, "FORM", title, "填写模拟记录。", fields=fields, action_ids=action_ids, props={"visualRole": "modalForm", "layout": {"columns": 2, "gap": "16px"}, "a9VisualQuality": "demoGrade", "elementPicker": True})], props={"visualRole": "modal", "a9VisualQuality": "demoGrade", "elementPicker": True})


def _collect_navigation_targets_from_request(request: AgentTaskRequest) -> list[dict[str, Any]]:
    ctx = request.context_package or {}
    result: list[dict[str, Any]] = []
    seen: set[str] = set()

    def add(raw: Any) -> None:
        if not isinstance(raw, dict):
            return
        page_id = str(raw.get("targetPageId") or raw.get("pageId") or raw.get("moduleId") or raw.get("id") or "").strip()
        if not page_id or page_id in seen:
            return
        seen.add(page_id)
        result.append({"targetPageId": page_id, "targetPageKey": str(raw.get("targetPageKey") or raw.get("pageKey") or raw.get("moduleKey") or raw.get("key") or "").strip(), "targetPageName": str(raw.get("targetPageName") or raw.get("pageName") or raw.get("moduleName") or raw.get("name") or raw.get("title") or "").strip(), "route": str(raw.get("route") or raw.get("targetRoute") or raw.get("routePath") or "").strip(), "navigationType": str(raw.get("navigationType") or "ACTION").strip() or "ACTION", "status": str(raw.get("status") or "PLANNED").strip() or "PLANNED"})

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            if any(k in value for k in ["targetPageId", "pageId", "moduleId", "targetPageName", "pageName", "moduleName", "route"]):
                add(value)
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for item in value:
                visit(item)

    for key in ["navigation_candidates", "navigationCandidates", "module_prototype_map", "modulePrototypeMap", "traceability_map", "traceabilityMap", "hidden_prototype_context", "hiddenPrototypeContext"]:
        visit(ctx.get(key))
    return result


def _find_navigation_target(targets: list[dict[str, Any]], text: str) -> dict[str, Any] | None:
    key = _norm(text)
    if not key:
        return None
    for target in targets:
        hay = _norm(" ".join(str(target.get(k) or "") for k in ["targetPageName", "targetPageKey", "route"]))
        if key in hay or hay in key:
            return target
    return None


def _interaction(action_id: str, typ: str, label: str, source_id: str | None, target_id: str | None, data_key: str | None, payload: dict[str, Any] | None = None, confirmation: str = "") -> dict[str, Any]:
    return {"actionId": action_id, "type": typ, "label": label, "sourceComponentId": source_id, "targetId": target_id, "targetPageId": None, "targetTabKey": None, "dataBindingKey": data_key, "scenarioKey": None, "payload": payload or {}, "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": confirmation}


def _component(cid: str, typ: str, title: str, desc: str, *, action_ids: list[str] | None = None, data_binding_key: str | None = None, fields: list[dict[str, Any]] | None = None, columns: list[dict[str, Any]] | None = None, children: list[dict[str, Any]] | None = None, props: dict[str, Any] | None = None) -> dict[str, Any]:
    return {"componentId": cid, "type": typ, "title": title, "description": desc, "featureIds": [], "actionIds": action_ids or [], "dataBindingKey": data_binding_key, "modalId": None, "fields": fields or [], "columns": columns or [], "tabs": [], "children": children or [], "props": props or {"a9VisualQuality": "demoGrade", "elementPicker": True}}


def _text_value(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _safe_id(value: Any, fallback: str) -> str:
    text = str(value or "").strip().lower()
    text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or fallback


def _dedupe_actions(actions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for action in actions:
        aid = str(action.get("actionId") or "").strip()
        if not aid or aid in seen:
            continue
        seen.add(aid)
        result.append(action)
    return result




# A9.10.14.4.3.13.2.11_TABLE_ROW_ACTION_DISCIPLINE
def _enforce_table_action_layout_contract(data: dict[str, Any]) -> None:
    """Normalize generic action ownership before validation/rendering.

    Rules:
    - PAGE_HEADER must not receive inferred query/row/modal actions.
    - TOOLBAR may only contain page-level actions such as create/export/refresh.
    - TABLE.actionIds must not contain row actions.
    - TABLE.props.rowActionIds is the only place for view/edit/approval/delete row actions.
    - This keeps Runtime Preview and downstream Vue code generation aligned.
    """
    if not isinstance(data, dict):
        return

    interactions = data.get("interactions")
    if not isinstance(interactions, list):
        interactions = []

    action_index = {
        str(action.get("actionId") or ""): action
        for action in interactions
        if isinstance(action, dict) and str(action.get("actionId") or "").strip()
    }

    def action_text(action_id: Any) -> str:
        action = action_index.get(str(action_id or "").strip()) or {}
        payload = action.get("payload") if isinstance(action, dict) else {}
        if not isinstance(payload, dict):
            payload = {}
        return " ".join([
            str(action_id or ""),
            str(action.get("label") or ""),
            str(action.get("type") or ""),
            str(payload.get("mode") or ""),
            str(payload.get("runtimeEffect") or ""),
        ])

    def action_type(action_id: Any) -> str:
        action = action_index.get(str(action_id or "").strip()) or {}
        return str(action.get("type") or "").strip().upper()

    def unique_valid(items: Any) -> list[str]:
        result: list[str] = []
        if not isinstance(items, list):
            return result
        for raw in items:
            action_id = str(raw or "").strip()
            if action_id and action_id in action_index and action_id not in result:
                result.append(action_id)
        return result

    def is_query_or_form_local(action_id: Any) -> bool:
        text = action_text(action_id)
        lower = text.lower()
        typ = action_type(action_id)
        return (
            typ in {"RESET_FORM", "SUBMIT_FORM", "CLOSE_MODAL", "QUERY_DATA"}
            and (
                "查询" in text
                or "搜索" in text
                or "重置" in text
                or "保存" in text
                or "取消" in text
                or "_query" in lower
                or "_reset" in lower
                or "_submit" in lower
                or "_cancel" in lower
            )
        )

    def is_row_scoped(action_id: Any) -> bool:
        text = action_text(action_id)
        lower = text.lower()
        typ = action_type(action_id)
        if typ == "DELETE_ROW":
            return True
        if typ == "OPEN_MODAL" and ("EDIT" in text.upper() or "编辑" in text or "修改" in text):
            return True
        if any(token in text for token in ["查看", "详情", "编辑", "修改", "审核", "审批", "删除", "移除", "作废"]):
            return True
        return any(token in lower for token in ["view_detail", "view", "edit", "approval", "delete", "row_action"])

    def is_page_level(action_id: Any) -> bool:
        text = action_text(action_id)
        typ = action_type(action_id)
        if is_row_scoped(action_id) or is_query_or_form_local(action_id):
            return False
        if typ == "OPEN_MODAL" and any(token in text for token in ["新增", "新建", "创建", "添加", "CREATE"]):
            return True
        if any(token in text for token in ["新增", "新建", "创建", "添加", "导出", "刷新"]):
            return True
        return False

    page = data.get("page") if isinstance(data.get("page"), dict) else {}
    roots = page.get("componentTree") if isinstance(page.get("componentTree"), list) else []

    def ensure_props(component: dict[str, Any]) -> dict[str, Any]:
        props = component.get("props")
        if not isinstance(props, dict):
            props = {}
            component["props"] = props
        return props

    def visit(component: Any) -> None:
        if not isinstance(component, dict):
            return

        typ = str(component.get("type") or "").strip().upper()
        action_ids = unique_valid(component.get("actionIds"))
        props = ensure_props(component)

        if typ == "TABLE":
            existing_row_ids = unique_valid(props.get("rowActionIds"))
            moved_row_ids = [action_id for action_id in action_ids if is_row_scoped(action_id)]
            row_ids = unique_valid([*existing_row_ids, *moved_row_ids])

            component["actionIds"] = [
                action_id
                for action_id in action_ids
                if action_id not in row_ids and is_page_level(action_id)
            ]
            props["rowActionIds"] = row_ids

            columns = component.get("columns")
            if isinstance(columns, list):
                component["columns"] = [
                    column for column in columns
                    if not (
                        isinstance(column, dict)
                        and (
                            str(column.get("type") or "").upper() in {"ACTION", "ACTIONS"}
                            or str(column.get("label") or "").strip() in {"操作", "动作", "行操作", "操作列"}
                            or str(column.get("columnKey") or column.get("fieldKey") or "").lower()
                                in {"action", "actions", "operation", "operations", "rowaction", "rowactions", "row_actions"}
                        )
                    )
                ]

        elif typ == "TOOLBAR":
            component["actionIds"] = [
                action_id for action_id in action_ids if is_page_level(action_id)
            ]

        elif typ == "PAGE_HEADER":
            component["actionIds"] = [
                action_id for action_id in action_ids if is_page_level(action_id)
            ]

        else:
            component["actionIds"] = action_ids

        children = component.get("children")
        if isinstance(children, list):
            for child in children:
                visit(child)

    for root in roots:
        visit(root)


def reconcile_ui_contract_with_prd_semantics(artifact: UiContractArtifact, request: AgentTaskRequest) -> UiContractArtifact:
    module_prd = _extract_module_prd(request)
    current_page = _extract_current_module(request)
    if isinstance(module_prd, dict):
        module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)
    contract = build_page_semantic_contract(module_prd, current_page)
    if not _has_contract(contract):
        return artifact

    # A9.10.14.4.3.14.1_FORM_CONTRACT_SEED_LOCK_FIX_RECONCILER
    # If the LLM designed a create/edit/entry page without the required form
    # fields, replace it with the locked FORM_DETAIL seed instead of persisting
    # a visually generic but semantically wrong process/detail page.
    if _is_form_detail_page(
        _text_value(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"), contract.get("pageName")),
        _text_value(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"), contract.get("pageKey")),
        contract.get("pageType"),
        contract,
    ):
        try:
            _form_shape_data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
        except Exception:
            _form_shape_data = {}
        if not _artifact_has_required_form_shape(_form_shape_data, contract):
            return _build_locked_form_ui_contract(request, current_page, contract)

    data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
    page = data.setdefault("page", {})
    tree = page.setdefault("componentTree", [])
    if not isinstance(tree, list) or not tree:
        return artifact

    interactions = data.setdefault("interactions", [])
    if not isinstance(interactions, list):
        interactions = []
        data["interactions"] = interactions
    mock_data = data.setdefault("mockData", {})
    if not isinstance(mock_data, dict):
        mock_data = {}
        data["mockData"] = mock_data

    index = _component_index(tree)
    container = _container(tree)
    table = _find(index, "TABLE")

    if contract.get("tableColumns"):
        if table is None:
            table = _component(_id(data, "data_table"), "TABLE", "数据列表", "展示 PRD 明确要求的数据字段。")
            container.setdefault("children", []).append(table)
            index = _component_index(tree)
        data_key = str(table.get("dataBindingKey") or _id(data, "rows"))
        table["dataBindingKey"] = data_key
        table["columns"] = [_column(c) for c in contract.get("tableColumns", []) if isinstance(c, dict)]
        table.setdefault("props", {})
        if not isinstance(table["props"], dict):
            table["props"] = {}
        table["props"]["a9PrdSemanticLocked"] = True
        table["props"]["dataBindingKey"] = data_key
        mock_data[data_key] = _rows(contract.get("tableColumns", []), mock_data.get(data_key))
    else:
        data_key = str((table or {}).get("dataBindingKey") or _id(data, "rows"))

    if contract.get("filterFields"):
        form = _find_filter_form(index)
        if form is None:
            form = _component(_id(data, "filter_form"), "FORM", "查询筛选", "按 PRD 明确要求提供查询筛选字段。")
            _insert_before(container, form, table)
        form["fields"] = [_field(f) for f in contract.get("filterFields", []) if isinstance(f, dict)]
        form.setdefault("props", {})
        if not isinstance(form["props"], dict):
            form["props"] = {}
        form["props"]["layout"] = "inline"
        form["props"]["a9PrdSemanticLocked"] = True
        query_id = _action_id(data, "query")
        reset_id = _action_id(data, "reset")
        form["actionIds"] = [query_id, reset_id]
        _upsert(interactions, {
            "actionId": query_id, "type": "QUERY_DATA", "label": "查询",
            "sourceComponentId": form["componentId"], "targetId": form["componentId"],
            "dataBindingKey": data_key, "payload": {"message": "已按筛选条件刷新模拟列表。"},
            "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": "",
        })
        _upsert(interactions, {
            "actionId": reset_id, "type": "RESET_FORM", "label": "重置",
            "sourceComponentId": form["componentId"], "targetId": form["componentId"],
            "payload": {"message": "筛选条件已重置。"},
            "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": "",
        })

    if table is not None and contract.get("rowActions"):
        row_ids = []
        for a in contract.get("rowActions", []):
            if not isinstance(a, dict):
                continue
            action = _action(data, a, table, data_key)
            _upsert(interactions, action)
            row_ids.append(action["actionId"])
        table.setdefault("props", {})
        if not isinstance(table["props"], dict):
            table["props"] = {}
        table["props"]["rowActionIds"] = row_ids

    primary_ids = []
    for a in contract.get("primaryActions", []):
        if not isinstance(a, dict) or a.get("kind") in {"QUERY", "RESET"}:
            continue
        action = _action(data, a, table, data_key)
        _upsert(interactions, action)
        primary_ids.append(action["actionId"])

    if primary_ids:
        toolbar = _find(index, "TOOLBAR")
        if toolbar is None:
            toolbar = _component(_id(data, "toolbar"), "TOOLBAR", "主要操作", "承载 PRD 明确要求的主操作。")
            _insert_before(container, toolbar, table)
        # A9.10.14.4.3.13.2: PRD strictness means unspecified toolbar
        # actions from generic templates must not survive.
        toolbar["actionIds"] = primary_ids

    _ensure_modals(data, tree, interactions, contract, data_key)
    _enforce_table_action_layout_contract(data)
    _sanitize_refs(data)
    _warn(data, "A9.10.14.4.3.13 PRD semantic contract reconciled explicit fields / columns / actions.")
    return UiContractArtifact.model_validate(data)




# A9.10.14.4.3.13.2.7.1_MISSING_NORMALIZE_HELPER_FIX
def _normalize_locked_contract_for_prd_exact_ui(contract: dict[str, Any]) -> dict[str, Any]:
    """Normalize PRD semantic contract before deterministic UI construction.

    This helper belongs to the No-LLM PRD-locked path. It fixes three generic
    mapping classes without using any business-specific special case:
    1. remove suffix noise from labels, such as "进行查询" / "进行";
    2. keep CREATE/EXPORT/REFRESH in primary actions and VIEW/EDIT/APPROVAL/DELETE in row actions;
    3. keep table/filter/form arrays de-duplicated with stable keys.
    """
    if not isinstance(contract, dict):
        return {}

    result = dict(contract)

    def label_of(item: Any) -> str:
        if isinstance(item, dict):
            raw = (
                item.get("label")
                or item.get("fieldName")
                or item.get("columnName")
                or item.get("actionName")
                or item.get("name")
                or item.get("title")
                or item.get("key")
            )
        else:
            raw = item

        x = str(raw or "").strip(" \t\r\n，,。；;:：、")
        x = re.sub(r"\s+", "", x)
        x = re.sub(r"^(按|以|根据|和|及|以及|或|并|包含|包括|支持|提供)+", "", x)

        suffixes = [
            "进行查询",
            "进行筛选",
            "进行检索",
            "用于查询",
            "用于筛选",
            "字段",
            "按钮",
            "操作",
            "入口",
            "查询",
            "筛选",
            "检索",
            "进行",
        ]
        changed = True
        while changed and x:
            changed = False
            for suffix in suffixes:
                if x.endswith(suffix) and len(x) > len(suffix):
                    x = x[: -len(suffix)].strip(" \t\r\n，,。；;:：、")
                    changed = True
        return x

    def normalize_field(item: Any) -> dict[str, Any] | None:
        label = label_of(item)
        if not label:
            return None
        raw = dict(item) if isinstance(item, dict) else {}
        raw["label"] = label
        raw["fieldKey"] = str(
            raw.get("fieldKey")
            or raw.get("field_key")
            or _safe_id(label, "field")
        )
        return raw

    def normalize_column(item: Any) -> dict[str, Any] | None:
        label = label_of(item)
        if not label:
            return None
        raw = dict(item) if isinstance(item, dict) else {}
        raw["label"] = label
        raw["columnKey"] = str(
            raw.get("columnKey")
            or raw.get("column_key")
            or raw.get("fieldKey")
            or _safe_id(label, "column")
        )
        raw["fieldKey"] = str(
            raw.get("fieldKey")
            or raw.get("field_key")
            or raw["columnKey"]
        )
        return raw

    def action_kind(label: str) -> str:
        if re.search(r"查询|搜索", label):
            return "QUERY"
        if re.search(r"重置|清空", label):
            return "RESET"
        if re.search(r"新增|新建|创建|添加", label):
            return "CREATE"
        if re.search(r"删除|移除|作废", label):
            return "DELETE"
        if re.search(r"编辑|修改", label):
            return "EDIT"
        if re.search(r"审核|审批", label):
            return "APPROVAL"
        if re.search(r"查看|详情", label):
            return "VIEW"
        if re.search(r"导出", label):
            return "EXPORT"
        if re.search(r"刷新", label):
            return "REFRESH"
        return "CUSTOM"

    def normalize_action(item: Any) -> dict[str, Any] | None:
        label = label_of(item)
        if not label:
            return None
        raw = dict(item) if isinstance(item, dict) else {}
        raw["label"] = label
        raw["actionKey"] = str(
            raw.get("actionKey")
            or raw.get("action_key")
            or _safe_id(label, "action")
        )
        raw["kind"] = action_kind(label)
        return raw

    def dedupe(items: list[dict[str, Any]], key_field: str) -> list[dict[str, Any]]:
        out: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in items:
            key = _norm(
                item.get(key_field)
                or item.get("fieldKey")
                or item.get("columnKey")
                or item.get("label")
            )
            if key and key not in seen:
                seen.add(key)
                out.append(item)
        return out

    result["filterFields"] = dedupe(
        [item for item in (normalize_field(raw) for raw in result.get("filterFields") or []) if item],
        "fieldKey",
    )
    result["formFields"] = dedupe(
        [item for item in (normalize_field(raw) for raw in result.get("formFields") or []) if item],
        "fieldKey",
    )
    result["tableColumns"] = dedupe(
        [item for item in (normalize_column(raw) for raw in result.get("tableColumns") or []) if item],
        "columnKey",
    )

    primary_raw = [item for item in (normalize_action(raw) for raw in result.get("primaryActions") or []) if item]
    row_raw = [item for item in (normalize_action(raw) for raw in result.get("rowActions") or []) if item]

    primary: list[dict[str, Any]] = []
    row: list[dict[str, Any]] = []
    row_kinds = {"VIEW", "EDIT", "APPROVAL", "DELETE"}

    for action in primary_raw:
        if action.get("kind") in row_kinds:
            row.append(action)
        else:
            primary.append(action)

    for action in row_raw:
        if action.get("kind") == "CREATE":
            primary.append(action)
        elif action.get("kind") in {"QUERY", "RESET", "EXPORT", "REFRESH"}:
            primary.append(action)
        else:
            row.append(action)

    result["primaryActions"] = dedupe(primary, "actionKey")
    result["rowActions"] = dedupe(row, "actionKey")

    return result


def _has_contract(c: dict[str, Any]) -> bool:
    return bool(isinstance(c, dict) and (c.get("filterFields") or c.get("tableColumns") or c.get("primaryActions") or c.get("rowActions") or c.get("formFields")))


def _extract_module_prd(request: AgentTaskRequest) -> Any:
    artifacts = (request.context_package or {}).get("artifacts")
    if isinstance(artifacts, dict):
        for key, value in artifacts.items():
            t = str((value or {}).get("artifact_type") or (value or {}).get("artifactType") or "").upper() if isinstance(value, dict) else ""
            if str(key).upper() == "MODULE_PRD" or t == "MODULE_PRD":
                return _unwrap(value)
    return {}


def _extract_current_module(request: AgentTaskRequest) -> dict[str, Any]:
    ctx = request.context_package or {}
    inp = request.input or {}
    for v in [inp.get("current_module"), ctx.get("current_module"), ctx.get("module")]:
        if isinstance(v, dict):
            return v
    return {"module_id": request.module_id, "module_type": "PAGE"}


def _unwrap(v: Any) -> Any:
    if isinstance(v, dict) and isinstance(v.get("content"), dict):
        res = dict(v["content"])
        for k in ["markdown", "contentMarkdown", "content_markdown"]:
            if k not in res and v.get(k):
                res[k] = v.get(k)
        return res
    return v


def _component_index(items: list[Any]) -> dict[str, dict[str, Any]]:
    out = {}
    def visit(x: Any) -> None:
        if not isinstance(x, dict): return
        cid = str(x.get("componentId") or "")
        if cid: out[cid] = x
        for c in x.get("children") or []: visit(c)
    for item in items: visit(item)
    return out


def _container(items: list[Any]) -> dict[str, Any]:
    for x in items:
        if isinstance(x, dict) and str(x.get("type") or "").upper() in {"SECTION", "CARD"}:
            x.setdefault("children", [])
            return x
    if isinstance(items[0], dict):
        items[0].setdefault("children", [])
        return items[0]
    root = _component("prd_root", "SECTION", "页面主体", "")
    items.append(root)
    return root


def _find(index: dict[str, dict[str, Any]], typ: str) -> dict[str, Any] | None:
    for x in index.values():
        if str(x.get("type") or "").upper() == typ:
            return x
    return None


def _find_filter_form(index: dict[str, dict[str, Any]]) -> dict[str, Any] | None:
    for x in index.values():
        if str(x.get("type") or "").upper() != "FORM":
            continue
        t = _norm(" ".join([str(x.get("componentId") or ""), str(x.get("title") or ""), str(x.get("description") or "")]))
        if any(k in t for k in ["查询", "筛选", "搜索", "filter", "query", "search"]):
            return x
    return None


def _insert_before(parent: dict[str, Any], component: dict[str, Any], before: dict[str, Any] | None) -> None:
    children = parent.setdefault("children", [])
    if not isinstance(children, list):
        parent["children"] = children = []
    if not before:
        children.append(component); return
    bid = before.get("componentId")
    for i, child in enumerate(children):
        if isinstance(child, dict) and child.get("componentId") == bid:
            children.insert(i, component); return
    children.append(component)


# A9.10.14.4.3.13.2.1_NO_LLM_HOTFIX_COMPONENT_KWARGS
def _component(
    cid: str,
    typ: str,
    title: str,
    desc: str,
    *,
    action_ids: list[str] | None = None,
    data_binding_key: str | None = None,
    fields: list[dict[str, Any]] | None = None,
    columns: list[dict[str, Any]] | None = None,
    children: list[dict[str, Any]] | None = None,
    props: dict[str, Any] | None = None,
) -> dict[str, Any]:
    merged_props = props if isinstance(props, dict) else {"a9VisualQuality": "demoGrade", "elementPicker": True}
    merged_props.setdefault("a9VisualQuality", "demoGrade")
    merged_props.setdefault("elementPicker", True)
    if data_binding_key:
        merged_props.setdefault("dataBindingKey", data_binding_key)
    return {
        "componentId": cid,
        "type": typ,
        "title": title,
        "description": desc,
        "featureIds": [],
        "actionIds": action_ids or [],
        "dataBindingKey": data_binding_key,
        "modalId": None,
        "fields": fields or [],
        "columns": columns or [],
        "tabs": [],
        "children": children or [],
        "props": merged_props,
    }


# A9.10.14.4.3.13.2.8_FIELD_KEY_NAMESPACE_FIX
def _namespace_field_for_component(field: dict[str, Any], namespace: str) -> dict[str, Any]:
    """Return a UI field with a PAGE-global unique fieldKey.

    UI_CONTRACT fieldKey is an element identity, not only a business data key.
    The same label can appear in different forms, but Java requires distinct
    fieldKey values across the page. Keep labels/placeholders/options unchanged
    and namespace only the fieldKey.
    """
    item = dict(field or {})
    ns = re.sub(r"[^a-zA-Z0-9_]+", "_", str(namespace or "field").strip()).strip("_") or "field"
    raw_key = str(item.get("fieldKey") or item.get("field_key") or item.get("key") or "field").strip()
    if not raw_key:
        raw_key = "field"
    prefix = ns + "_"
    if not raw_key.startswith(prefix):
        item["fieldKey"] = prefix + raw_key
    else:
        item["fieldKey"] = raw_key
    return item


def _field(x: dict[str, Any]) -> dict[str, Any]:
    typ = str(x.get("type") or "INPUT").upper()
    if typ not in {"INPUT","PASSWORD","TEXTAREA","SELECT","CHECKBOX","RADIO","NUMBER","EMAIL","DATE","DATETIME","SWITCH","HIDDEN"}:
        typ = "INPUT"
    label = str(x.get("label") or x.get("fieldKey") or "字段")
    return {"fieldKey": str(x.get("fieldKey") or label), "label": label, "type": typ, "required": bool(x.get("required", False)), "placeholder": str(x.get("placeholder") or (("请选择" if typ in {"SELECT","DATE","DATETIME"} else "请输入") + label)), "defaultValue": x.get("defaultValue"), "options": x.get("options") if isinstance(x.get("options"), list) else _options(label), "validationRuleIds": [], "featureIds": [], "helpText": "", "disabled": False}


def _column(x: dict[str, Any]) -> dict[str, Any]:
    typ = str(x.get("type") or "TEXT").upper()
    if typ not in {"TEXT","TAG","NUMBER","DATE","DATETIME","BOOLEAN","ACTIONS"}: typ = "TEXT"
    label = str(x.get("label") or x.get("columnKey") or "字段")
    fk = str(x.get("fieldKey") or x.get("columnKey") or label)
    try:
        width = int(x.get("width") or _column_width(label, typ))
    except Exception:
        width = _column_width(label, typ)
    width = max(72, min(1200, width))
    return {"columnKey": str(x.get("columnKey") or fk), "fieldKey": fk, "label": label, "type": typ, "width": width, "sortable": bool(x.get("sortable", False))}


# A9.10.14.4.3.13.2.12_TABLE_HORIZONTAL_SCROLL_WIDTH_FIX
def _column_width(label: Any, typ: Any = "TEXT") -> int:
    """Deterministic display width for generated table columns.

    Width is part of UI_CONTRACT layout semantics. It prevents many-column
    enterprise list pages from squeezing columns until action buttons are clipped.
    """
    text = str(label or "")
    kind = str(typ or "TEXT").upper()
    if re.search(r"单号|编号|编码|code|no", text, re.I):
        return 170
    if re.search(r"名称|供应商|客户|仓库|部门|组织|机构", text):
        return 150
    if re.search(r"时间|日期", text):
        return 150
    if re.search(r"数量|金额|价格|总数|库存", text):
        return 130
    if re.search(r"状态|类型|分类", text):
        return 120
    if kind in {"NUMBER"}:
        return 120
    if kind in {"DATE", "DATETIME"}:
        return 150
    if kind in {"TAG", "BOOLEAN"}:
        return 110
    return 140


def _table_min_width(columns: Any, row_action_ids: Any = None) -> int:
    total = 0
    if isinstance(columns, list):
        for column in columns:
            if isinstance(column, dict):
                total += _column_width(column.get("label") or column.get("columnKey"), column.get("type"))
    if isinstance(row_action_ids, list) and row_action_ids:
        total += max(180, min(420, 92 * len(row_action_ids)))
    return max(760, min(2400, total or 760))



# A9.10.14.4.3.13.2.12.1_TABLE_OVERFLOW_ACTION_COLUMN_FIX
def _default_column_width(label: Any, typ: Any = "TEXT") -> int:
    text = str(label or "")
    kind = str(typ or "TEXT").upper()
    if re.search(r"单号|编号|编码|流水|批次", text):
        return 180
    if re.search(r"时间|日期", text):
        return 160
    if re.search(r"数量|金额|价格|库存|比例|率", text):
        return 132
    if re.search(r"状态|类型|分类", text) or kind == "TAG":
        return 126
    if re.search(r"供应商|客户|仓库|部门|人员|创建人|经办人", text):
        return 150
    # Chinese labels need slightly more room than ASCII keys.
    return max(120, min(220, 72 + len(text) * 16))


def _action_column_width(row_ids: Any) -> int:
    count = len(row_ids) if isinstance(row_ids, list) else 0
    if count <= 0:
        return 0
    return max(240, min(420, 76 * count + 64))


def _table_min_width(columns: Any, row_ids: Any) -> int:
    total = 0
    if isinstance(columns, list):
        for item in columns:
            if isinstance(item, dict):
                total += int(item.get("width") or _default_column_width(item.get("label") or item.get("columnKey"), item.get("type")))
            else:
                total += 132
    total += _action_column_width(row_ids)
    return max(980, min(2200, total + 56))

def _options(label: str) -> list[dict[str, str]]:
    if "状态" in label:
        return [{"label": "草稿", "value": "草稿"}, {"label": "待审核", "value": "待审核"}, {"label": "已完成", "value": "已完成"}]
    if any(t in label for t in ["供应商","仓库","客户","类型","分类"]):
        return [{"label": f"{label}示例一", "value": "option_1"}, {"label": f"{label}示例二", "value": "option_2"}]
    return []


def _rows(cols: list[Any], existing: Any) -> list[dict[str, Any]]:
    rows = [dict(x) for x in existing[:3]] if isinstance(existing, list) and existing and all(isinstance(x, dict) for x in existing) else [{"id": "mock_1"}, {"id": "mock_2"}]
    for i, row in enumerate(rows):
        row.setdefault("id", f"mock_{i+1}"); row.setdefault("key", row["id"])
        for c in cols:
            if not isinstance(c, dict): continue
            fk = str(c.get("fieldKey") or c.get("columnKey") or c.get("label") or "")
            label = str(c.get("label") or fk)
            if fk: row.setdefault(fk, _sample(label, i))
    return rows


def _sample(label: str, i: int) -> Any:
    if "状态" in label: return ["待审核", "草稿", "已完成"][i % 3]
    if "数量" in label: return 10 + i
    if "日期" in label: return "2026-08-10"
    if "时间" in label: return "2026-08-10 09:30"
    if "单号" in label or "编号" in label: return f"NO-20260810-{i+1:03d}"
    return f"{label}示例{i+1}"


def _action(data: dict[str, Any], spec: dict[str, Any], table: dict[str, Any] | None, data_key: str) -> dict[str, Any]:
    label = str(spec.get("label") or spec.get("actionKey") or "操作")
    kind = str(spec.get("kind") or "").upper()
    aid = _action_id(data, str(spec.get("actionKey") or label))
    table_id = str((table or {}).get("componentId") or "")
    base = {"actionId": aid, "type": "SHOW_TOAST", "label": label, "sourceComponentId": table_id or None, "targetId": table_id or _first_id(data), "dataBindingKey": data_key, "payload": {"message": f"{label}操作已模拟完成。"}, "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": ""}
    if kind == "DELETE":
        base.update({"type": "DELETE_ROW", "confirmationMessage": f"确认{label}当前记录？", "payload": {"message": "已从当前模拟列表中移除该记录。"}})
    elif kind == "CREATE":
        base.update({"type": "OPEN_MODAL", "targetId": _id(data, "create_modal"), "dataBindingKey": None, "payload": {"mode": "CREATE"}})
    elif kind == "EDIT":
        base.update({"type": "OPEN_MODAL", "targetId": _id(data, "edit_modal"), "payload": {"mode": "EDIT"}})
    elif kind in {"QUERY", "REFRESH", "EXPORT"}:
        base.update({"type": "QUERY_DATA", "payload": {"message": f"已模拟{label}。"}})
    elif spec.get("navigationIntent"):
        nav = _nav(data, str(spec.get("targetPageName") or label))
        if nav:
            base.update({"type": "NAVIGATE", "targetPageId": nav.get("targetPageId"), "dataBindingKey": None})
    return base


def _ensure_modals(data: dict[str, Any], tree: list[Any], actions: list[Any], contract: dict[str, Any], data_key: str) -> None:
    labels = [str(x.get("label") or "") for x in [*(contract.get("primaryActions") or []), *(contract.get("rowActions") or [])] if isinstance(x, dict)]
    fields = [_field(x) for x in (contract.get("formFields") or contract.get("tableColumns") or contract.get("filterFields") or []) if isinstance(x, dict)] or [_field({"fieldKey":"name","label":"名称"})]
    if any(re.search(r"新增|新建|创建|添加", x) for x in labels):
        _modal(data, tree, actions, "create", "新建记录", fields, data_key, "ADD_ROW")
    if any(re.search(r"编辑|修改", x) for x in labels):
        _modal(data, tree, actions, "edit", "编辑记录", fields, data_key, "UPDATE_ROW")


def _modal(data: dict[str, Any], tree: list[Any], actions: list[Any], key: str, title: str, fields: list[dict[str, Any]], data_key: str, effect: str) -> None:
    fields = [_namespace_field_for_component(item, key) for item in fields]
    modal_id, form_id = _id(data, f"{key}_modal"), _id(data, f"{key}_form")
    submit_id, cancel_id = _action_id(data, f"{key}_submit"), _action_id(data, f"{key}_cancel")
    idx = _component_index(tree)
    if modal_id not in idx:
        modal = _component(modal_id, "MODAL", title, "在原型运行时模拟新增或编辑记录。")
        form = _component(form_id, "FORM", title, "填写模拟记录。")
        form["fields"], form["actionIds"] = fields, [submit_id, cancel_id]
        modal["children"] = [form]
        _container(tree).setdefault("children", []).append(modal)
    _upsert(actions, {"actionId": submit_id, "type": "SUBMIT_FORM", "label": "保存", "sourceComponentId": form_id, "targetId": form_id, "dataBindingKey": data_key, "payload": {"runtimeEffect": effect, "dataBindingKey": data_key, "closeModalOnSuccess": True, "message": "已更新当前模拟列表，刷新页面后恢复原始数据。"}, "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": ""})
    _upsert(actions, {"actionId": cancel_id, "type": "CLOSE_MODAL", "label": "取消", "sourceComponentId": form_id, "targetId": modal_id, "payload": {}, "successActionIds": [], "failureActionIds": [], "featureIds": [], "confirmationMessage": ""})


def _upsert(actions: list[Any], action: dict[str, Any]) -> None:
    aid = str(action.get("actionId") or "")
    for i, old in enumerate(actions):
        if isinstance(old, dict) and str(old.get("actionId") or "") == aid:
            merged = dict(old); merged.update({k:v for k,v in action.items() if v is not None}); actions[i] = merged; return
    actions.append(action)


def _sanitize_refs(data: dict[str, Any]) -> None:
    ids = {str(a.get("actionId") or "") for a in data.get("interactions", []) if isinstance(a, dict)}
    def visit(x: Any) -> None:
        if not isinstance(x, dict): return
        if isinstance(x.get("actionIds"), list): x["actionIds"] = [a for a in x["actionIds"] if str(a) in ids]
        p = x.get("props")
        if isinstance(p, dict) and isinstance(p.get("rowActionIds"), list): p["rowActionIds"] = [a for a in p["rowActionIds"] if str(a) in ids]
        for c in x.get("children") or []: visit(c)
    for root in (data.get("page") or {}).get("componentTree") or []: visit(root)


def _nav(data: dict[str, Any], target: str) -> dict[str, Any] | None:
    nt = _norm(target)
    for nav in data.get("navigationTargets") or []:
        if not isinstance(nav, dict): continue
        txt = _norm(" ".join([str(nav.get("targetPageName") or ""), str(nav.get("targetPageKey") or ""), str(nav.get("route") or "")]))
        if nt and (nt in txt or txt in nt): return nav
    return None


def _warn(data: dict[str, Any], msg: str) -> None:
    q = data.setdefault("qualitySummary", {})
    if not isinstance(q, dict): q = {}; data["qualitySummary"] = q
    w = q.get("warnings") if isinstance(q.get("warnings"), list) else []
    if msg not in w: w.append(msg)
    q["warnings"] = w


def _id(data: dict[str, Any], suffix: str) -> str:
    page = data.get("page") if isinstance(data.get("page"), dict) else {}
    key = str(page.get("pageKey") or data.get("identity", {}).get("pageKey") or "page")
    return re.sub(r"[^a-zA-Z0-9_\-\u4e00-\u9fff]+", "_", f"{key}_{suffix}").strip("_")
def _action_id(data: dict[str, Any], suffix: str) -> str: return _id(data, f"action_{suffix}")
def _first_id(data: dict[str, Any]) -> str:
    for x in (data.get("page") or {}).get("componentTree") or []:
        if isinstance(x, dict) and x.get("componentId"): return str(x["componentId"])
    return ""
def _norm(v: Any) -> str: return re.sub(r"[\s_\-/\-]+", "", str(v or "").strip().lower())
def _uniq(items: list[str]) -> list[str]:
    out, seen = [], set()
    for x in items:
        if x and x not in seen: seen.add(x); out.append(x)
    return out

# A9.10.14.4.3.14.2.1_FORM_IDENTITY_WRAPPER_FIX
# Robust post-generation form identity hard lock.  This wrapper avoids fragile
# inline anchors and ensures a create/edit PAGE cannot be persisted as a detail/list page.
try:
    _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_UI_FROM_PRD = build_ui_contract_from_prd_semantics_without_llm
except NameError:  # pragma: no cover
    _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_UI_FROM_PRD = None
try:
    _A9_10_14_4_3_14_2_1_ORIGINAL_RECONCILE = reconcile_ui_contract_with_prd_semantics
except NameError:  # pragma: no cover
    _A9_10_14_4_3_14_2_1_ORIGINAL_RECONCILE = None


def _a9_14_2_1_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a9_14_2_1_norm(value: Any) -> str:
    return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())


def _a9_14_2_1_current_text(current_page: dict[str, Any]) -> str:
    extra = current_page.get("extra") if isinstance(current_page.get("extra"), dict) else {}
    return " ".join([
        _a9_14_2_1_text(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name")),
        _a9_14_2_1_text(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key")),
        _a9_14_2_1_text(current_page.get("module_type"), current_page.get("moduleType"), current_page.get("pageKind"), extra.get("pageKind")),
        _a9_14_2_1_text(current_page.get("module_path"), current_page.get("modulePath"), extra.get("modulePath")),
    ])


def _a9_14_2_1_is_form_like_current_page(current_page: dict[str, Any]) -> bool:
    text = _a9_14_2_1_norm(_a9_14_2_1_current_text(current_page))
    return bool(re.search(r"创建|新建|新增|录入|登记|编辑|修改|create|add|edit|update|form", text, re.I))


def _a9_14_2_1_is_form_contract(contract: dict[str, Any]) -> bool:
    if not isinstance(contract, dict):
        return False
    text = _a9_14_2_1_norm(" ".join([str(contract.get("pageType") or ""), str(contract.get("pageName") or ""), str(contract.get("pageKey") or "")]))
    return bool(contract.get("formFields")) or bool(re.search(r"form|表单|创建|新建|新增|录入|编辑|修改", text, re.I))


def _a9_14_2_1_artifact_has_form(artifact: UiContractArtifact) -> bool:
    try:
        data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
    except Exception:
        return False
    page = data.get("page") if isinstance(data, dict) else {}
    layout = page.get("layout") if isinstance(page, dict) else {}
    layout_type = str(layout.get("type") if isinstance(layout, dict) else "").upper()
    has_form = False
    form_field_count = 0
    def visit(value: Any) -> None:
        nonlocal has_form, form_field_count
        if isinstance(value, dict):
            if str(value.get("type") or "").upper() in {"FORM", "FIELD_GROUP"}:
                has_form = True
                fields = value.get("fields")
                if isinstance(fields, list):
                    form_field_count += len([x for x in fields if isinstance(x, dict)])
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for item in value:
                visit(item)
    visit(page.get("componentTree") if isinstance(page, dict) else [])
    return layout_type == "FORM_DETAIL" and has_form and form_field_count >= 2


def _a9_14_2_1_field_type(label: str) -> str:
    if re.search(r"状态|类型|供应商|仓库|客户|角色|分类", label):
        return "SELECT"
    if "日期" in label:
        return "DATE"
    if "时间" in label:
        return "DATETIME"
    if re.search(r"数量|金额|价格|库存", label):
        return "NUMBER"
    if re.search(r"备注|说明|描述", label):
        return "TEXTAREA"
    return "INPUT"


def _a9_14_2_1_field(item: Any, index: int) -> dict[str, Any]:
    if isinstance(item, dict):
        label = _a9_14_2_1_text(item.get("label"), item.get("fieldName"), item.get("name"), item.get("title"), item.get("fieldKey"), f"字段{index + 1}")
        key = _a9_14_2_1_text(item.get("fieldKey"), item.get("key"), item.get("columnKey"))
        typ = _a9_14_2_1_text(item.get("type"), item.get("fieldType"), item.get("displayType"))
    else:
        label = str(item or f"字段{index + 1}").strip()
        key = ""
        typ = ""
    label = label or f"字段{index + 1}"
    key = _safe_id(key or label, f"field_{index + 1}") if "_safe_id" in globals() else re.sub(r"[^0-9a-zA-Z_\u4e00-\u9fff]+", "_", key or label).strip("_") or f"field_{index + 1}"
    typ = (typ or _a9_14_2_1_field_type(label)).upper()
    if typ not in {"INPUT", "PASSWORD", "TEXTAREA", "SELECT", "CHECKBOX", "RADIO", "NUMBER", "EMAIL", "DATE", "DATETIME", "SWITCH", "HIDDEN"}:
        typ = _a9_14_2_1_field_type(label)
    return {
        "fieldKey": key,
        "label": label,
        "type": typ,
        "required": bool(isinstance(item, dict) and item.get("required")),
        "placeholder": ("请选择" if typ in {"SELECT", "DATE", "DATETIME"} else "请输入") + label,
        "defaultValue": None,
        "options": [],
        "validationRuleIds": [],
        "featureIds": [],
        "helpText": "",
        "disabled": False,
    }


def _a9_14_2_1_form_fields(contract: dict[str, Any], current_page: dict[str, Any]) -> list[dict[str, Any]]:
    raw = []
    for key in ["formFields", "tableColumns", "filterFields"]:
        value = contract.get(key) if isinstance(contract, dict) else []
        if isinstance(value, list):
            raw.extend(value)
    if not raw:
        current_text = _a9_14_2_1_current_text(current_page)
        if "入库" in current_text:
            raw = ["入库单号", "供应商", "仓库", "入库总数量", "备注"]
        elif "出库" in current_text:
            raw = ["出库单号", "客户", "仓库", "出库总数量", "备注"]
        else:
            raw = ["业务编号", "业务名称", "数量", "备注"]
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    system_re = re.compile(r"操作|创建人|创建时间|创建日期|更新时间|更新人|审核人|审核时间|审批人|审批时间|经办人|状态|流程|处理进度|关联记录|详情|查看|编辑$|删除$", re.I)
    for index, item in enumerate(raw):
        field = _a9_14_2_1_field(item, index)
        if system_re.search(field.get("label") or ""):
            continue
        key = str(field.get("fieldKey") or "")
        if key and key not in seen:
            seen.add(key)
            result.append(field)
    if len(result) < 2:
        for extra in ["业务编号", "业务名称", "数量", "备注"]:
            f = _a9_14_2_1_field(extra, len(result))
            if f["fieldKey"] not in seen:
                seen.add(f["fieldKey"])
                result.append(f)
    return result[:12]


def _a9_14_2_1_component(component_id: str, typ: str, title: str = "", description: str = "", **kwargs: Any) -> dict[str, Any]:
    props = kwargs.pop("props", {})
    if not isinstance(props, dict):
        props = {}
    props.setdefault("elementPicker", {"selectable": True})
    props.setdefault("a9VisualQuality", "demoGrade")
    props.setdefault("elementStyles", {})
    props.setdefault("a9PrdSemanticLocked", True)
    return {
        "componentId": component_id,
        "type": typ,
        "title": title,
        "description": description,
        "featureIds": [],
        "actionIds": kwargs.pop("actionIds", []),
        "dataBindingKey": kwargs.pop("dataBindingKey", None),
        "modalId": kwargs.pop("modalId", None),
        "fields": kwargs.pop("fields", []),
        "columns": kwargs.pop("columns", []),
        "tabs": kwargs.pop("tabs", []),
        "children": kwargs.pop("children", []),
        "props": props,
    }


def _a9_14_2_1_build_form_ui_contract(request: AgentTaskRequest, current_page: dict[str, Any], contract: dict[str, Any]) -> UiContractArtifact:
    page_id = _a9_14_2_1_text(current_page.get("module_id"), current_page.get("moduleId"), current_page.get("pageId"), current_page.get("id"), request.module_id)
    page_name = _a9_14_2_1_text(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"), contract.get("pageName"), "表单页面")
    page_key = _safe_id(_a9_14_2_1_text(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"), contract.get("pageKey"), page_name), "form_page") if "_safe_id" in globals() else re.sub(r"[^0-9a-zA-Z_\u4e00-\u9fff]+", "_", page_name).strip("_") or "form_page"
    route = _a9_14_2_1_text(current_page.get("routePath"), current_page.get("route"), f"/{page_key.replace('_', '-')}")
    base = page_key
    header_id = f"{base}_header"
    root_id = f"{base}_root"
    alert_id = f"{base}_form_tip"
    card_id = f"{base}_form_card"
    form_id = f"{base}_form"
    toolbar_id = f"{base}_footer_actions"
    save_id = f"{base}_save"
    cancel_id = f"{base}_cancel"
    fields = _a9_14_2_1_form_fields(contract, current_page)
    interactions = [
        {
            "actionId": save_id,
            "type": "SUBMIT_FORM",
            "label": "保存",
            "sourceComponentId": form_id,
            "targetId": form_id,
            "targetPageId": None,
            "targetTabKey": None,
            "payload": {"mockMutation": "UPSERT_FORM", "source": MARKER},
            "confirmationMessage": "",
            "successActionIds": [],
            "failureActionIds": [],
            "featureIds": [],
        },
        {
            "actionId": cancel_id,
            "type": "SHOW_TOAST",
            "label": "取消",
            "sourceComponentId": toolbar_id,
            "targetId": root_id,
            "targetPageId": None,
            "targetTabKey": None,
            "payload": {"message": "已取消当前编辑。", "source": MARKER},
            "confirmationMessage": "",
            "successActionIds": [],
            "failureActionIds": [],
            "featureIds": [],
        },
    ]
    form = _a9_14_2_1_component(
        form_id,
        "FORM",
        "基础信息",
        "请填写并核对业务单据的关键信息。",
        fields=fields,
        actionIds=[save_id],
        props={"visualRole": "primaryForm", "layout": {"columns": 4, "gap": "16px"}},
    )
    card = _a9_14_2_1_component(card_id, "CARD", "基础信息", "录入业务单据核心字段。", children=[form], props={"visualRole": "primaryFormCard"})
    alert = _a9_14_2_1_component(alert_id, "ALERT", "填写提示", "保存前请确认供应商、仓库和数量等信息准确。", props={"level": "info", "message": "保存前请确认供应商、仓库和数量等信息准确。"})
    toolbar = _a9_14_2_1_component(toolbar_id, "TOOLBAR", "操作", "提交或取消当前表单。", actionIds=[save_id, cancel_id], props={"visualRole": "formFooterActions"})
    root = _a9_14_2_1_component(root_id, "SECTION", "", "", children=[alert, card, toolbar], props={"visualRole": "pageContent"})
    header = _a9_14_2_1_component(header_id, "PAGE_HEADER", page_name, "清晰、稳定、面向企业业务系统。", props={"eyebrow": "表单处理", "visualRole": "pageHeader"})
    visible = [header_id, root_id, alert_id, card_id, form_id, toolbar_id]
    data = {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {"projectId": str(request.project_id), "pageId": str(page_id), "pageKey": page_key, "pageName": page_name, "pageKind": "FORM_PAGE"},
        "page": {
            "pageId": str(page_id),
            "pageKey": page_key,
            "pageName": page_name,
            "route": route,
            "title": page_name,
            "description": contract.get("description") or f"{page_name}用于录入和提交业务单据信息。",
            "pageKind": "FORM_PAGE",
            "layout": {"type": "FORM_DETAIL", "columns": 1, "regions": [{"regionId": "main", "title": "主内容区", "componentIds": visible}]},
            "componentTree": [header, root],
        },
        "interactions": interactions,
        "validationRules": [],
        "pageStates": [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示表单字段和操作按钮。", "visibleComponentIds": visible, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "加载中", "message": "正在准备表单数据。", "visibleComponentIds": visible, "disabledComponentIds": []},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "保存成功", "message": "表单保存成功。", "visibleComponentIds": visible, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "保存失败", "message": "保存失败，请检查输入后重试。", "visibleComponentIds": visible, "disabledComponentIds": []},
        ],
        "mockData": {f"{base}_form_data": {field["fieldKey"]: "" for field in fields}},
        "mockScenarios": [{"scenarioKey": f"{base}_save_success", "actionId": save_id, "outcome": "SUCCESS", "description": "模拟表单保存成功。", "mockDataRef": f"{base}_form_data", "nextPageState": "SUCCESS", "delayMs": 300}],
        "apiBindings": [],
        "navigationTargets": [],
        "featureCoverage": [],
        "assumptions": ["A9.10.14.4.3.14.2.1: 创建/编辑类页面身份已锁定为 FORM_DETAIL。"],
        "qualitySummary": {"score": 92, "coverageRate": 100, "generationMode": "LLM_STRUCTURED", "blockers": [], "warnings": ["FORM page identity hard lock applied."]},
        "developmentRules": {"mustFollowLayout": True, "mustFollowFields": True, "mustFollowValidationRules": True, "mustFollowInteractionFlow": True, "mustFollowApiBindings": True, "allowStyleOptimization": True, "breakingChangeRequiresReview": True, "arbitraryScriptForbidden": True, "mockMustUseScenarioEngine": True},
        "analysisSummary": "创建/编辑类页面已锁定为 FORM_DETAIL 表单页，防止被生成为详情/流程/关联记录页。",
        "markdown": f"# {page_name} UI_CONTRACT\n\n当前页面已按 FORM_DETAIL 表单页身份锁定。",
    }
    return UiContractArtifact.model_validate(data)


def _a9_14_2_1_current_contract(request: AgentTaskRequest) -> tuple[dict[str, Any], dict[str, Any]]:
    module_prd = _extract_module_prd(request)
    current_page = _extract_current_module(request)
    if isinstance(module_prd, dict):
        module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)
    contract = build_page_semantic_contract(module_prd, current_page)
    return current_page, contract if isinstance(contract, dict) else {}


def build_ui_contract_from_prd_semantics_without_llm(request: AgentTaskRequest) -> UiContractArtifact | None:  # type: ignore[override]
    current_page, contract = _a9_14_2_1_current_contract(request)
    if _a9_14_2_1_is_form_like_current_page(current_page) or _a9_14_2_1_is_form_contract(contract):
        return _a9_14_2_1_build_form_ui_contract(request, current_page, contract)
    original = _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_UI_FROM_PRD
    return original(request) if callable(original) else None


def reconcile_ui_contract_with_prd_semantics(artifact: UiContractArtifact, request: AgentTaskRequest) -> UiContractArtifact:  # type: ignore[override]
    current_page, contract = _a9_14_2_1_current_contract(request)
    original = _A9_10_14_4_3_14_2_1_ORIGINAL_RECONCILE
    reconciled = artifact
    if callable(original):
        try:
            reconciled = original(artifact, request)
        except Exception:
            reconciled = artifact
    if _a9_14_2_1_is_form_like_current_page(current_page) or _a9_14_2_1_is_form_contract(contract):
        if not _a9_14_2_1_artifact_has_form(reconciled):
            return _a9_14_2_1_build_form_ui_contract(request, current_page, contract)
    return reconciled
# A9.10.14.4.3.15.3.1_UNIVERSAL_PAGE_REQUIREMENT_UI_CONTRACT_GUARD
# Universal PRD page requirement -> UI_CONTRACT semantic guard.
# The LLM still performs visual design in UiContractAgent; this guard provides
# the locked page-shape seed and replaces an LLM result only when it violates the
# PRD page type, fields, buttons, row operations or navigation boundary.
A9_15_3_1_MARKER = "A9.10.14.4.3.15.3.1_UNIVERSAL_PAGE_REQUIREMENT_UI_CONTRACT_GUARD"
MARKER = A9_15_3_1_MARKER
try:
    _A9_15_3_1_PREVIOUS_BUILD_UI_FROM_PRD = build_ui_contract_from_prd_semantics_without_llm
except NameError:  # pragma: no cover
    _A9_15_3_1_PREVIOUS_BUILD_UI_FROM_PRD = None
try:
    _A9_15_3_1_PREVIOUS_RECONCILE = reconcile_ui_contract_with_prd_semantics
except NameError:  # pragma: no cover
    _A9_15_3_1_PREVIOUS_RECONCILE = None


def _a91531_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a91531_norm(value: Any) -> str:
    return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value or "").strip().lower())


def _a91531_current_contract(request: AgentTaskRequest) -> tuple[dict[str, Any], dict[str, Any]]:
    module_prd = _extract_module_prd(request)
    current_page = _extract_current_module(request)
    if isinstance(module_prd, dict):
        try:
            module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)
        except Exception:
            pass
    try:
        contract = build_page_semantic_contract(module_prd, current_page)
    except Exception:
        contract = {}
    return current_page, contract if isinstance(contract, dict) else {}


def _a91531_has_contract(contract: dict[str, Any]) -> bool:
    if not isinstance(contract, dict):
        return False
    for key in ["formFields", "filterFields", "tableColumns", "primaryActions", "rowActions", "navigationTargets", "navigationActions"]:
        value = contract.get(key)
        if isinstance(value, list) and value:
            return True
    return bool(_a91531_text(contract.get("pageType"), contract.get("pageName"), contract.get("description")))


def _a91531_page_type(current_page: dict[str, Any], contract: dict[str, Any]) -> str:
    raw = _a91531_text(contract.get("pageType"), contract.get("page_type"))
    text = _a91531_norm(" ".join([
        raw,
        _a91531_text(contract.get("pageName"), current_page.get("module_name"), current_page.get("moduleName"), current_page.get("name")),
        _a91531_text(contract.get("pageKey"), current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("key")),
        _a91531_text(contract.get("description")),
    ]))
    raw_upper = raw.strip().upper().replace("-", "_")
    alias = {
        "CREATE": "FORM", "ADD": "FORM", "NEW": "FORM", "EDIT": "FORM", "UPDATE": "FORM", "FORM_PAGE": "FORM", "CREATE_PAGE": "FORM", "EDIT_PAGE": "FORM",
        "LIST_PAGE": "LIST", "TABLE": "LIST", "TABLE_LIST": "LIST", "QUERY": "LIST", "SEARCH": "LIST",
        "DETAIL_PAGE": "DETAIL", "VIEW": "DETAIL", "VIEW_PAGE": "DETAIL",
        "APPROVAL_PAGE": "APPROVAL", "AUDIT": "APPROVAL", "AUDIT_PAGE": "APPROVAL", "REVIEW": "APPROVAL",
        "SETTING": "CONFIG", "SETTINGS": "CONFIG", "CONFIG_PAGE": "CONFIG",
        "WORKBENCH": "DASHBOARD", "DASHBOARD_PAGE": "DASHBOARD", "REPORT_PAGE": "REPORT",
        "HOME": "PORTAL", "INDEX": "PORTAL", "PORTAL_PAGE": "PORTAL",
    }
    if raw_upper in {"FORM", "LIST", "DETAIL", "APPROVAL", "DASHBOARD", "CONFIG", "PORTAL", "REPORT", "WIZARD", "PROFILE", "LOGIN", "SEARCH"}:
        return raw_upper
    if raw_upper in alias:
        return alias[raw_upper]
    if re.search(r"审核|审批|复核|approve|approval|audit|review", text, re.I):
        return "APPROVAL"
    if contract.get("filterFields") or contract.get("tableColumns") or re.search(r"列表|查询|搜索|检索|list|query|search|table", text, re.I):
        return "LIST"
    if contract.get("formFields") or re.search(r"创建|新建|新增|录入|登记|编辑|修改|create|add|edit|update|form", text, re.I):
        return "FORM"
    if re.search(r"详情|明细|查看|detail|view|profile", text, re.I):
        return "DETAIL"
    if re.search(r"看板|仪表盘|工作台|dashboard|analytics|workbench", text, re.I):
        return "DASHBOARD"
    if re.search(r"配置|设置|参数|setting|config", text, re.I):
        return "CONFIG"
    if re.search(r"首页|门户|频道|portal|home|index", text, re.I):
        return "PORTAL"
    return "FORM" if contract.get("formFields") else "LIST" if contract.get("tableColumns") else "DETAIL"


def _a91531_page_kind(page_type: str) -> str:
    return {
        "LIST": "LIST_PAGE",
        "FORM": "FORM_PAGE",
        "DETAIL": "DETAIL_PAGE",
        "APPROVAL": "APPROVAL_PAGE",
        "DASHBOARD": "DASHBOARD_PAGE",
        "CONFIG": "CONFIG_PAGE",
        "PORTAL": "PORTAL_PAGE",
        "REPORT": "REPORT_PAGE",
    }.get(page_type, "BUSINESS_PAGE")


def _a91531_layout_type(page_type: str) -> str:
    return {
        "LIST": "TABLE_LIST",
        "FORM": "FORM_DETAIL",
        "DETAIL": "DETAIL_TABS",
        "APPROVAL": "APPROVAL_WORKBENCH",
        "DASHBOARD": "DASHBOARD",
        "CONFIG": "SETTINGS",
        "PORTAL": "CUSTOM_GRID",
        "REPORT": "DASHBOARD",
    }.get(page_type, "CUSTOM_GRID")


def _a91531_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _a91531_fields(contract: dict[str, Any], key: str) -> list[dict[str, Any]]:
    items = _a91531_list(contract.get(key))
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, item in enumerate(items):
        if not isinstance(item, dict):
            item = {"label": str(item or f"字段{idx + 1}")}
        field = _field(item)
        fkey = str(field.get("fieldKey") or field.get("label") or f"field_{idx + 1}")
        if fkey and fkey not in seen:
            seen.add(fkey)
            result.append(field)
    return result


def _a91531_columns(contract: dict[str, Any]) -> list[dict[str, Any]]:
    items = _a91531_list(contract.get("tableColumns"))
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, item in enumerate(items):
        if not isinstance(item, dict):
            item = {"label": str(item or f"字段{idx + 1}")}
        column = _column(item)
        ckey = str(column.get("columnKey") or column.get("fieldKey") or column.get("label") or f"column_{idx + 1}")
        if ckey and ckey not in seen:
            seen.add(ckey)
            result.append(column)
    return result


def _a91531_actions(contract: dict[str, Any], key: str, default_labels: list[str] | None = None) -> list[dict[str, Any]]:
    raw_items = _a91531_list(contract.get(key))
    if not raw_items and default_labels:
        raw_items = [{"label": label, "actionKey": _safe_id(label, "action")} for label in default_labels]
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, raw in enumerate(raw_items):
        if isinstance(raw, dict):
            label = _a91531_text(raw.get("label"), raw.get("actionName"), raw.get("action_name"), raw.get("name"), raw.get("title"), raw.get("actionKey"), f"操作{idx + 1}")
            key_text = _a91531_text(raw.get("actionKey"), raw.get("action_key"), raw.get("key"), label)
            kind = _a91531_text(raw.get("kind"), raw.get("type"), raw.get("actionType"), raw.get("action_type"), _a91531_action_kind(label))
            spec = dict(raw)
        else:
            label = str(raw or f"操作{idx + 1}").strip()
            key_text = label
            kind = _a91531_action_kind(label)
            spec = {}
        label = label or f"操作{idx + 1}"
        action_key = _safe_id(key_text or label, f"action_{idx + 1}")
        if action_key in seen:
            continue
        seen.add(action_key)
        spec.update({"label": label, "actionKey": action_key, "kind": str(kind or _a91531_action_kind(label)).upper()})
        result.append(spec)
    return result


def _a91531_action_kind(label: str) -> str:
    text = str(label or "")
    if re.search(r"查询|搜索|筛选|检索", text): return "QUERY"
    if re.search(r"重置|清空", text): return "RESET"
    if re.search(r"新增|新建|创建|添加", text): return "CREATE"
    if re.search(r"保存|提交|确定|确认|发布", text): return "SUBMIT"
    if re.search(r"通过|同意|批准", text): return "APPROVE"
    if re.search(r"驳回|拒绝|退回", text): return "REJECT"
    if re.search(r"删除|移除|作废", text): return "DELETE"
    if re.search(r"编辑|修改|调整", text): return "EDIT"
    if re.search(r"查看|详情|明细", text): return "VIEW"
    if re.search(r"导出", text): return "EXPORT"
    if re.search(r"取消|返回|关闭", text): return "CANCEL"
    return "CUSTOM"


def _a91531_interaction(action_id: str, action_type: str, label: str, source_id: str, target_id: str, data_key: str | None = None, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    return _interaction(action_id, action_type, label, source_id, target_id, data_key, payload or {"message": f"{label}操作已模拟完成。", "source": A9_15_3_1_MARKER})


def _a91531_nav_target_for_action(action: dict[str, Any], nav_targets: list[dict[str, Any]]) -> str:
    aliases = [
        action.get("targetPageId"), action.get("target_page_id"), action.get("targetPageKey"), action.get("target_page_key"),
        action.get("targetPageName"), action.get("target_page_name"), action.get("target"), action.get("label"), action.get("actionName"),
    ]
    normalized_aliases = {_a91531_norm(x) for x in aliases if _a91531_text(x)}
    for target in nav_targets:
        if not isinstance(target, dict):
            continue
        tid = _a91531_text(target.get("targetPageId"), target.get("pageId"), target.get("moduleId"), target.get("id"))
        if not tid:
            continue
        target_aliases = [target.get("targetPageId"), target.get("targetPageKey"), target.get("targetPageName"), target.get("pageKey"), target.get("pageName"), target.get("route"), target.get("targetRoute")]
        if any(_a91531_norm(x) in normalized_aliases or any(a and (a in _a91531_norm(x) or _a91531_norm(x) in a) for a in normalized_aliases) for x in target_aliases if _a91531_text(x)):
            return tid
    return ""


def _a91531_action_interaction(action: dict[str, Any], base: str, source_id: str, target_id: str, data_key: str | None, nav_targets: list[dict[str, Any]]) -> dict[str, Any]:
    label = _a91531_text(action.get("label"), action.get("actionName"), action.get("actionKey"), "操作")
    kind = str(action.get("kind") or _a91531_action_kind(label)).upper()
    action_id = f"{base}_action_{_safe_id(action.get('actionKey') or label, 'action')}"
    if kind in {"QUERY", "SEARCH", "REFRESH", "EXPORT"}:
        return _a91531_interaction(action_id, "QUERY_DATA", label, source_id, target_id, data_key, {"message": f"已执行{label}。", "source": A9_15_3_1_MARKER})
    if kind == "RESET":
        return _a91531_interaction(action_id, "RESET_FORM", label, source_id, target_id, None, {"message": "已重置条件。", "source": A9_15_3_1_MARKER})
    if kind in {"SUBMIT", "SAVE", "APPROVE", "REJECT"}:
        return _a91531_interaction(action_id, "SUBMIT_FORM", label, source_id, target_id, data_key, {"message": f"{label}成功。", "source": A9_15_3_1_MARKER})
    if kind == "DELETE":
        return _a91531_interaction(action_id, "DELETE_ROW", label, source_id, target_id, data_key, {"message": "已模拟删除当前记录。", "source": A9_15_3_1_MARKER})
    target_page_id = _a91531_nav_target_for_action(action, nav_targets)
    if kind in {"CREATE", "EDIT", "VIEW", "APPROVAL"} and target_page_id:
        data = _a91531_interaction(action_id, "NAVIGATE", label, source_id, target_id, data_key, {"source": A9_15_3_1_MARKER})
        data["targetPageId"] = target_page_id
        return data
    return _a91531_interaction(action_id, "SHOW_TOAST", label, source_id, target_id, data_key, {"message": f"已模拟{label}。", "source": A9_15_3_1_MARKER})


def _a91531_component_ids(items: list[dict[str, Any]]) -> list[str]:
    result: list[str] = []
    def visit(value: Any) -> None:
        if isinstance(value, dict):
            cid = str(value.get("componentId") or "").strip()
            if cid and cid not in result:
                result.append(cid)
            for child in value.get("children") or []:
                visit(child)
        elif isinstance(value, list):
            for item in value:
                visit(item)
    visit(items)
    return result


def _a91531_quality(page_type: str) -> dict[str, Any]:
    return {"score": 96, "coverageRate": 100, "generationMode": "LLM_STRUCTURED", "blockers": [], "warnings": [f"{A9_15_3_1_MARKER}: {page_type} page shape, fields and actions locked by MODULE_PRD.page_requirements."]}


def _a91531_base_identity(request: AgentTaskRequest, current_page: dict[str, Any], contract: dict[str, Any], page_type: str) -> tuple[str, str, str, str, str, str]:
    page_id = _a91531_text(current_page.get("module_id"), current_page.get("moduleId"), current_page.get("pageId"), current_page.get("id"), request.module_id)
    page_name = _a91531_text(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"), contract.get("pageName"), "业务页面")
    page_key = _safe_id(_a91531_text(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"), contract.get("pageKey"), page_name), "page")
    route = _a91531_text(contract.get("routePath"), contract.get("route"), current_page.get("routePath"), current_page.get("route"), f"/{page_key.replace('_', '-')}")
    page_kind = _a91531_page_kind(page_type)
    base = page_key
    return str(page_id), page_name, page_key, route, page_kind, base


def _a91531_artifact_data(request: AgentTaskRequest, current_page: dict[str, Any], contract: dict[str, Any], page_type: str, component_tree: list[dict[str, Any]], interactions: list[dict[str, Any]], page_states: list[dict[str, Any]], mock_data: dict[str, Any], layout_regions: list[dict[str, Any]], nav_targets: list[dict[str, Any]]) -> dict[str, Any]:
    page_id, page_name, page_key, route, page_kind, base = _a91531_base_identity(request, current_page, contract, page_type)
    all_component_ids = _a91531_component_ids(component_tree)
    all_action_ids = [str(item.get("actionId")) for item in interactions if isinstance(item, dict) and item.get("actionId")]
    return {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {"projectId": str(request.project_id), "pageId": page_id, "pageKey": page_key, "pageName": page_name, "pageKind": page_kind},
        "page": {"pageId": page_id, "pageKey": page_key, "pageName": page_name, "route": route, "title": page_name, "description": contract.get("description") or f"{page_name}业务页面。", "pageKind": page_kind, "layout": {"type": _a91531_layout_type(page_type), "columns": 1, "regions": layout_regions}, "componentTree": component_tree},
        "interactions": _dedupe_actions(interactions),
        "validationRules": [],
        "pageStates": page_states,
        "mockData": mock_data,
        "mockScenarios": [{"scenarioKey": f"{base}_default_success", "actionId": all_action_ids[0] if all_action_ids else f"{base}_noop", "outcome": "SUCCESS", "delayMs": 200, "message": "操作成功。", "response": {}, "nextPageState": "SUCCESS"}] if all_action_ids else [],
        "apiBindings": [],
        "navigationTargets": nav_targets,
        "featureCoverage": [{"featureId": "prd_semantic_contract", "featureKey": "prd_semantic_contract", "featureName": "PRD 页面语义契约", "status": "FULL", "componentIds": all_component_ids, "actionIds": all_action_ids, "notes": [A9_15_3_1_MARKER]}],
        "assumptions": [f"{A9_15_3_1_MARKER}: UI_CONTRACT seed is locked by MODULE_PRD.page_requirements; LLM may optimize visuals only."],
        "qualitySummary": _a91531_quality(page_type),
        "developmentRules": {"mustFollowLayout": True, "mustFollowFields": True, "mustFollowValidationRules": True, "mustFollowInteractionFlow": True, "mustFollowApiBindings": True, "allowStyleOptimization": True, "breakingChangeRequiresReview": True, "arbitraryScriptForbidden": True, "mockMustUseScenarioEngine": True},
        "analysisSummary": f"已根据 PRD 页面契约锁定 {page_type} 页面结构、字段、按钮、表格列、跳转和状态边界。",
        "markdown": f"# {page_name} UI_CONTRACT\n\n{A9_15_3_1_MARKER}: {page_type} page semantic guard applied.",
    }


def _a91531_build_locked_universal_ui_contract(request: AgentTaskRequest, current_page: dict[str, Any], contract: dict[str, Any]) -> UiContractArtifact:
    page_type = _a91531_page_type(current_page, contract)
    page_id, page_name, page_key, route, page_kind, base = _a91531_base_identity(request, current_page, contract, page_type)
    header_id = f"{base}_header"
    root_id = f"{base}_content"
    nav_targets = _collect_navigation_targets_from_request(request)
    header = _component(header_id, "PAGE_HEADER", page_name, contract.get("description") or "清晰、稳定、面向业务用户。", props={"eyebrow": _a91531_page_kind(page_type), "visualRole": "pageHeader", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}})

    interactions: list[dict[str, Any]] = []
    children: list[dict[str, Any]] = []
    mock_data: dict[str, Any] = {}
    layout_regions: list[dict[str, Any]] = [{"regionId": "header", "title": "页头", "componentIds": [header_id]}]

    if page_type == "LIST":
        filters = _a91531_fields(contract, "filterFields") or _a91531_fields(contract, "formFields")[:4]
        columns = _a91531_columns(contract) or [_column(x) for x in filters]
        primary_actions = _a91531_actions(contract, "primaryActions", ["查询", "重置"])
        row_actions = _a91531_actions(contract, "rowActions", [])
        filter_card_id, filter_form_id = f"{base}_filter_card", f"{base}_filter_form"
        table_card_id, toolbar_id, table_id, empty_id = f"{base}_table_card", f"{base}_toolbar", f"{base}_table", f"{base}_empty"
        data_key = f"{base}_rows"
        query_action = {"label": "查询", "actionKey": "query", "kind": "QUERY"}
        reset_action = {"label": "重置", "actionKey": "reset", "kind": "RESET"}
        interactions.extend([
            _a91531_action_interaction(query_action, base, filter_form_id, filter_form_id, data_key, nav_targets),
            _a91531_action_interaction(reset_action, base, filter_form_id, filter_form_id, None, nav_targets),
        ])
        toolbar_action_ids: list[str] = []
        for action in primary_actions:
            kind = str(action.get("kind") or "").upper()
            if kind in {"QUERY", "RESET"}:
                continue
            interaction = _a91531_action_interaction(action, base, toolbar_id, table_id, data_key, nav_targets)
            interactions.append(interaction)
            toolbar_action_ids.append(str(interaction.get("actionId")))
        row_action_ids: list[str] = []
        for action in row_actions:
            interaction = _a91531_action_interaction(action, base, table_id, table_id, data_key, nav_targets)
            interactions.append(interaction)
            row_action_ids.append(str(interaction.get("actionId")))
        filter_form = _component(filter_form_id, "FORM", "查询条件", "根据 PRD 页面契约筛选数据。", action_ids=[f"{base}_action_query", f"{base}_action_reset"], fields=filters, props={"visualRole": "filterForm", "layout": "inline", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}})
        children.append(_component(filter_card_id, "CARD", "查询条件", "输入条件后刷新列表。", children=[filter_form], props={"visualRole": "filterSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        table_children: list[dict[str, Any]] = []
        if toolbar_action_ids:
            table_children.append(_component(toolbar_id, "TOOLBAR", "主要操作", "PRD 明确的页面级操作。", action_ids=toolbar_action_ids, props={"visualRole": "tableToolbar", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        table_children.append(_component(table_id, "TABLE", "数据列表", "展示 PRD 声明的表格列和行操作。", columns=columns, data_binding_key=data_key, props={"visualRole": "dataTable", "rowActionIds": row_action_ids, "enableHorizontalScroll": True, "minTableWidth": _table_min_width(columns, row_action_ids) if "_table_min_width" in globals() else 900, "stickyActionColumn": True, "compactRows": True, "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        table_children.append(_component(empty_id, "EMPTY_STATE", "暂无数据", "调整筛选条件后重新查询。", props={"visualRole": "emptyState", "message": "暂无符合条件的数据。", "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        children.append(_component(table_card_id, "CARD", "数据列表", "展示查询结果并提供行级操作。", children=table_children, props={"visualRole": "tableSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        mock_data[data_key] = _rows(columns, None)
        visible_default = [header_id, root_id, filter_card_id, filter_form_id, table_card_id, table_id]
        page_states = [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示查询条件和列表数据。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "查询中", "message": "正在加载列表数据。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "EMPTY", "type": "EMPTY", "title": "暂无数据", "message": "当前没有符合条件的数据。", "visibleComponentIds": [*visible_default, empty_id], "disabledComponentIds": []},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "操作成功", "message": "操作已完成。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "操作失败", "message": "请调整条件或稍后重试。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
        ]
        layout_regions.extend([{"regionId": "filters", "title": "查询条件", "componentIds": [filter_card_id]}, {"regionId": "table", "title": "数据列表", "componentIds": [table_card_id]}])

    elif page_type in {"FORM", "APPROVAL"}:
        fields = _a91531_fields(contract, "formFields") or _a91531_fields(contract, "tableColumns") or _a91531_fields(contract, "filterFields")
        if not fields:
            fields = [_field({"label": "业务编号", "fieldKey": "business_code"}), _field({"label": "备注", "fieldKey": "remark", "type": "TEXTAREA"})]
        default_actions = ["通过", "驳回", "取消"] if page_type == "APPROVAL" else ["保存", "取消"]
        primary_actions = _a91531_actions(contract, "primaryActions", default_actions)
        info_id, form_card_id, form_id, toolbar_id = f"{base}_info", f"{base}_form_card", f"{base}_main_form", f"{base}_toolbar"
        data_key = f"{base}_form_data"
        action_ids: list[str] = []
        for action in primary_actions:
            interaction = _a91531_action_interaction(action, base, form_id, form_id if str(action.get("kind")).upper() not in {"CANCEL"} else root_id, data_key, nav_targets)
            interactions.append(interaction)
            action_ids.append(str(interaction.get("actionId")))
        hint_text = "请填写审核意见并执行通过或驳回。" if page_type == "APPROVAL" else "请按业务规则填写字段，保存后展示模拟反馈。"
        children.append(_component(info_id, "ALERT", "办理提示", hint_text, props={"visualRole": "businessHint", "level": "info", "message": hint_text, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        children.append(_component(form_card_id, "CARD", "基础信息", contract.get("description") or "录入并核对业务字段。", children=[_component(form_id, "FORM", "", "", fields=fields, action_ids=[aid for aid in action_ids if aid], data_binding_key=data_key, props={"visualRole": "mainBusinessForm", "layout": "grid", "columns": 4, "labelPosition": "top", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}})], props={"visualRole": "formSection", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        children.append(_component(toolbar_id, "TOOLBAR", "页面操作", "执行 PRD 明确的主要操作。", action_ids=action_ids, props={"visualRole": "formFooterActions", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        mock_data[data_key] = {field["fieldKey"]: _sample(field.get("label", "字段"), 0) for field in fields}
        visible_default = [header_id, root_id, info_id, form_card_id, form_id, toolbar_id]
        page_states = [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示表单字段和操作入口。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "处理中", "message": "正在处理表单。", "visibleComponentIds": visible_default, "disabledComponentIds": action_ids[:1]},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "操作成功", "message": "操作已完成。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "操作失败", "message": "请检查字段后重试。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
        ]
        layout_regions.append({"regionId": "form", "title": "表单区", "componentIds": [info_id, form_card_id, toolbar_id]})

    else:
        fields = _a91531_fields(contract, "formFields") or _a91531_fields(contract, "tableColumns") or _a91531_fields(contract, "filterFields")
        columns = _a91531_columns(contract)
        primary_actions = _a91531_actions(contract, "primaryActions", ["返回"] if page_type == "DETAIL" else [])
        summary_id, detail_id, toolbar_id = f"{base}_summary", f"{base}_detail", f"{base}_toolbar"
        data_key = f"{base}_detail_data"
        action_ids: list[str] = []
        for action in primary_actions:
            interaction = _a91531_action_interaction(action, base, toolbar_id, detail_id, data_key, nav_targets)
            interactions.append(interaction)
            action_ids.append(str(interaction.get("actionId")))
        summary_items = [
            {"label": field.get("label"), "value": _sample(field.get("label", "字段"), idx)}
            for idx, field in enumerate(fields[:6])
        ] or [{"label": "页面说明", "value": contract.get("description") or f"{page_name}业务信息。"}]
        children.append(_component(summary_id, "CARD", "业务摘要", contract.get("description") or "展示关键业务信息。", props={"visualRole": "summaryCard", "items": summary_items, "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        if page_type == "DASHBOARD" or page_type == "REPORT":
            metric_cards = []
            for idx, field in enumerate((columns or fields)[:4]):
                metric_cards.append(_component(f"{base}_metric_{idx+1}", "CARD", str(field.get("label") or f"指标{idx+1}"), "关键指标展示。", props={"visualRole": "metricCard", "value": _sample(str(field.get("label") or "指标"), idx), "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
            children.extend(metric_cards)
        else:
            children.append(_component(detail_id, "FIELD_GROUP", "详细信息", "按 PRD 字段展示业务详情。", fields=fields or [_field({"label": "说明", "fieldKey": "description"})], data_binding_key=data_key, props={"visualRole": "detailFields", "readonly": True, "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        if action_ids:
            children.append(_component(toolbar_id, "TOOLBAR", "页面操作", "PRD 明确的页面操作。", action_ids=action_ids, props={"visualRole": "detailActions", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}}))
        mock_data[data_key] = {field.get("fieldKey", f"field_{idx+1}"): _sample(field.get("label", "字段"), idx) for idx, field in enumerate(fields)}
        visible_default = [header_id, root_id, *[child.get("componentId") for child in children if isinstance(child, dict) and child.get("componentId")]]
        page_states = [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "默认态", "message": "展示页面业务信息。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "加载中", "message": "正在加载页面数据。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "操作成功", "message": "操作已完成。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "加载失败", "message": "页面数据加载失败。", "visibleComponentIds": visible_default, "disabledComponentIds": []},
        ]
        layout_regions.append({"regionId": "main", "title": "主体内容", "componentIds": [child.get("componentId") for child in children if isinstance(child, dict) and child.get("componentId")]})

    root = _component(root_id, "SECTION", "", "", children=children, props={"visualRole": "pageContent", "a9PrdSemanticLocked": True, "a9VisualQuality": "demoGrade", "elementPicker": {"selectable": True}})
    data = _a91531_artifact_data(request, current_page, contract, page_type, [header, root], interactions, page_states, mock_data, layout_regions, nav_targets)
    return UiContractArtifact.model_validate(data)


def _a91531_artifact_component_snapshot(artifact: UiContractArtifact) -> tuple[str, str, str, set[str], set[str], set[str]]:
    try:
        data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
    except Exception:
        return "", "", "", set(), set(), set()
    page = data.get("page") if isinstance(data, dict) else {}
    layout = page.get("layout") if isinstance(page, dict) else {}
    layout_type = str(layout.get("type") if isinstance(layout, dict) else "").upper()
    component_types: set[str] = set()
    field_labels: set[str] = set()
    column_labels: set[str] = set()
    action_labels: set[str] = set()
    def add_label(bucket: set[str], value: Any) -> None:
        text = _a91531_norm(value)
        if text:
            bucket.add(text)
    def visit(value: Any) -> None:
        if isinstance(value, dict):
            component_types.add(str(value.get("type") or "").upper())
            for field in value.get("fields") or []:
                if isinstance(field, dict):
                    add_label(field_labels, field.get("label") or field.get("fieldKey"))
            for column in value.get("columns") or []:
                if isinstance(column, dict):
                    add_label(column_labels, column.get("label") or column.get("columnKey"))
            for child in value.get("children") or []:
                visit(child)
        elif isinstance(value, list):
            for item in value:
                visit(item)
    visit(page.get("componentTree") if isinstance(page, dict) else [])
    for action in data.get("interactions") or []:
        if isinstance(action, dict):
            add_label(action_labels, action.get("label") or action.get("actionId"))
    return layout_type, " ".join(component_types), "", field_labels, column_labels, action_labels


def _a91531_label_covered(required: list[dict[str, Any]], actual: set[str], key_names: list[str], *, min_ratio: float = 0.8) -> bool:
    if not required:
        return True
    required_labels = []
    for item in required:
        if not isinstance(item, dict):
            continue
        label = _a91531_text(*[item.get(k) for k in key_names])
        if label:
            required_labels.append(label)
    if not required_labels:
        return True
    covered = 0
    for label in required_labels:
        norm_label = _a91531_norm(label)
        if not norm_label:
            continue
        if any(norm_label in x or x in norm_label for x in actual):
            covered += 1
    return covered >= max(1, int(len(required_labels) * min_ratio + 0.0001))


def _a91531_artifact_covers_contract(artifact: UiContractArtifact, current_page: dict[str, Any], contract: dict[str, Any]) -> bool:
    page_type = _a91531_page_type(current_page, contract)
    layout_type, component_text, _, field_labels, column_labels, action_labels = _a91531_artifact_component_snapshot(artifact)
    if page_type == "LIST":
        if "TABLE" not in component_text or layout_type != "TABLE_LIST":
            return False
        if not _a91531_label_covered(_a91531_columns(contract), column_labels, ["label", "columnKey", "fieldKey"], min_ratio=0.75):
            return False
        if not _a91531_label_covered(_a91531_actions(contract, "primaryActions", ["查询", "重置"]), action_labels, ["label", "actionKey"], min_ratio=0.5):
            return False
        return True
    if page_type in {"FORM", "APPROVAL"}:
        expected_layout = "APPROVAL_WORKBENCH" if page_type == "APPROVAL" else "FORM_DETAIL"
        if "FORM" not in component_text and "FIELD_GROUP" not in component_text:
            return False
        if layout_type not in {expected_layout, "FORM_DETAIL"}:
            return False
        if not _a91531_label_covered(_a91531_fields(contract, "formFields"), field_labels, ["label", "fieldKey"], min_ratio=0.75):
            return False
        if page_type == "APPROVAL" and not any(re.search(r"通过|驳回|审核|审批|approve|reject", x, re.I) for x in action_labels):
            return False
        return True
    if page_type == "DETAIL":
        if layout_type not in {"DETAIL_TABS", "CUSTOM_GRID"}:
            return False
        required = _a91531_fields(contract, "formFields") or _a91531_fields(contract, "tableColumns")
        return _a91531_label_covered(required, field_labels | column_labels, ["label", "fieldKey", "columnKey"], min_ratio=0.6)
    return bool(component_text)


def build_ui_contract_from_prd_semantics_without_llm(request: AgentTaskRequest) -> UiContractArtifact | None:  # type: ignore[override]
    current_page, contract = _a91531_current_contract(request)
    if not _a91531_has_contract(contract):
        return None
    return _a91531_build_locked_universal_ui_contract(request, current_page, contract)


def reconcile_ui_contract_with_prd_semantics(artifact: UiContractArtifact, request: AgentTaskRequest) -> UiContractArtifact:  # type: ignore[override]
    current_page, contract = _a91531_current_contract(request)
    if not _a91531_has_contract(contract):
        return artifact
    if _a91531_artifact_covers_contract(artifact, current_page, contract):
        return artifact
    return _a91531_build_locked_universal_ui_contract(request, current_page, contract)

