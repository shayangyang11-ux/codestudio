from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.agents.requirement.ui_contract_quality_gate import UiContractQualityGate
from app.schemas.ui_contract import UiContractArtifact


class GateFailure(Exception):
    pass


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def base_contract(page_key: str, page_name: str, page_kind: str, layout_type: str, components: list[dict[str, Any]], interactions: list[dict[str, Any]], mock_data: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {
            "projectId": "demo_project",
            "pageId": page_key,
            "pageKey": page_key,
            "pageName": page_name,
            "pageKind": page_kind,
        },
        "page": {
            "pageId": page_key,
            "pageKey": page_key,
            "pageName": page_name,
            "route": f"/mock/{page_key}",
            "title": page_name,
            "description": f"{page_name} demo-grade prototype.",
            "pageKind": page_kind,
            "layout": {
                "type": layout_type,
                "columns": 1,
                "regions": [],
            },
            "componentTree": components,
        },
        "interactions": interactions,
        "validationRules": [],
        "pageStates": [
            {"stateKey": "DEFAULT", "type": "DEFAULT", "title": "Default", "message": "Default state.", "visibleComponentIds": [], "disabledComponentIds": []},
            {"stateKey": "LOADING", "type": "LOADING", "title": "Loading", "message": "Loading state.", "visibleComponentIds": [], "disabledComponentIds": []},
            {"stateKey": "ERROR", "type": "ERROR", "title": "Error", "message": "Error state.", "visibleComponentIds": [], "disabledComponentIds": []},
            {"stateKey": "SUCCESS", "type": "SUCCESS", "title": "Success", "message": "Success state.", "visibleComponentIds": [], "disabledComponentIds": []},
            {"stateKey": "EMPTY", "type": "EMPTY", "title": "Empty", "message": "Empty state.", "visibleComponentIds": [], "disabledComponentIds": []},
        ],
        "mockData": mock_data,
        "mockScenarios": [],
        "apiBindings": [],
        "navigationTargets": [],
        "featureCoverage": [],
        "assumptions": [],
        "qualitySummary": {
            "score": 95,
            "coverageRate": 95,
            "generationMode": "LLM_STRUCTURED",
            "blockers": [],
            "warnings": [],
        },
        "developmentRules": {
            "mustFollowLayout": True,
            "mustFollowFields": True,
            "mustFollowValidationRules": True,
            "mustFollowInteractionFlow": True,
            "mustFollowApiBindings": True,
            "allowStyleOptimization": True,
            "breakingChangeRequiresReview": True,
            "arbitraryScriptForbidden": True,
            "mockMustUseScenarioEngine": True,
        },
        "analysisSummary": f"{page_name} demo-grade UI contract.",
        "markdown": "",
    }


def component(component_id: str, component_type: str, title: str, visual_role: str, **kwargs: Any) -> dict[str, Any]:
    item = {
        "componentId": component_id,
        "type": component_type,
        "title": title,
        "description": kwargs.pop("description", f"{title} description."),
        "featureIds": [],
        "actionIds": kwargs.pop("actionIds", []),
        "dataBindingKey": kwargs.pop("dataBindingKey", None),
        "modalId": None,
        "fields": kwargs.pop("fields", []),
        "columns": kwargs.pop("columns", []),
        "tabs": kwargs.pop("tabs", []),
        "children": kwargs.pop("children", []),
        "props": {
            "a9VisualQuality": "demoGrade",
            "visualRole": visual_role,
            "elementPicker": {"selectable": True},
            "elementStyles": {},
        },
    }
    item["props"].update(kwargs.pop("props", {}))
    item.update(kwargs)
    return item


def interaction(action_id: str, action_type: str, label: str, target_id: str, *, data_binding_key: str | None = None, target_page_id: str | None = None) -> dict[str, Any]:
    return {
        "actionId": action_id,
        "type": action_type,
        "label": label,
        "sourceComponentId": target_id,
        "targetId": target_id,
        "targetPageId": target_page_id,
        "targetTabKey": None,
        "dataBindingKey": data_binding_key,
        "scenarioKey": None,
        "payload": {},
        "successActionIds": [],
        "failureActionIds": [],
        "featureIds": [],
        "confirmationMessage": "",
    }


def field(field_key: str, label: str, field_type: str = "INPUT", required: bool = False) -> dict[str, Any]:
    return {
        "fieldKey": field_key,
        "label": label,
        "type": field_type,
        "required": required,
        "placeholder": ("请选择" if field_type == "SELECT" else "请输入") + label,
        "defaultValue": None,
        "options": [{"label": "选项一", "value": "one", "disabled": False}] if field_type == "SELECT" else [],
        "validationRuleIds": [],
        "featureIds": [],
        "helpText": "",
        "disabled": False,
    }


def column(column_key: str, label: str, column_type: str = "TEXT") -> dict[str, Any]:
    return {
        "columnKey": column_key,
        "fieldKey": column_key,
        "label": label,
        "type": column_type,
        "width": 160,
        "sortable": True,
    }


def build_form_case() -> dict[str, Any]:
    interactions = [
        interaction("save_draft", "SUBMIT_FORM", "保存草稿", "main_form"),
        interaction("submit_form", "SUBMIT_FORM", "提交", "main_form"),
        interaction("cancel_form", "RESET_FORM", "取消", "main_form"),
        interaction("add_item", "SHOW_TOAST", "添加明细", "detail_table"),
        interaction("remove_item", "DELETE_ROW", "删除", "detail_table"),
    ]
    form = component(
        "main_form",
        "FORM",
        "基本信息",
        "enterpriseMainForm",
        fields=[field("businessNo", "业务单号", required=True), field("ownerId", "经办人", "SELECT", True)],
    )
    table = component(
        "detail_table",
        "TABLE",
        "明细信息",
        "enterpriseEditableTable",
        dataBindingKey="detailItems",
        columns=[column("itemCode", "明细编号"), column("quantity", "数量", "NUMBER")],
        actionIds=["add_item"],
        props={"rowActionIds": ["remove_item"]},
    )
    components = [
        component("form_header", "PAGE_HEADER", "新建业务单", "enterprisePageHeader", actionIds=["cancel_form"]),
        component("top_toolbar", "TOOLBAR", "", "enterpriseTopToolbar", actionIds=["save_draft", "submit_form", "cancel_form"]),
        component("basic_section", "SECTION", "基本信息", "enterpriseFormSection", children=[form]),
        component("detail_section", "SECTION", "明细信息", "enterpriseDetailSection", children=[table]),
        component("bottom_toolbar", "TOOLBAR", "", "enterpriseBottomToolbar", actionIds=["save_draft", "submit_form", "cancel_form"]),
    ]
    return base_contract("form_page", "新建业务单", "FORM_PAGE", "FORM_DETAIL", components, interactions, {"detailItems": [{"itemCode": "D-001", "quantity": 10}]})


def build_table_case() -> dict[str, Any]:
    interactions = [
        interaction("query_records", "QUERY_DATA", "查询", "filters", data_binding_key="tableRows"),
        interaction("reset_filters", "RESET_FORM", "重置", "filters"),
        interaction("create_record", "SHOW_TOAST", "新增", "data_table"),
        interaction("export_records", "QUERY_DATA", "导出", "data_table", data_binding_key="tableRows"),
        interaction("view_record", "SHOW_TOAST", "查看", "data_table"),
        interaction("delete_record", "DELETE_ROW", "删除", "data_table"),
    ]
    filter_form = component(
        "filters",
        "FORM",
        "查询筛选",
        "enterpriseFilterForm",
        fields=[field("keyword", "关键字"), field("status", "状态", "SELECT")],
        actionIds=["query_records", "reset_filters"],
    )
    table = component(
        "data_table",
        "TABLE",
        "数据列表",
        "enterpriseDataTable",
        dataBindingKey="tableRows",
        columns=[column("code", "编号"), column("status", "状态", "TAG")],
        actionIds=["create_record", "export_records"],
        props={"rowActionIds": ["view_record", "delete_record"]},
    )
    components = [
        component("table_header", "PAGE_HEADER", "业务列表", "enterprisePageHeader", actionIds=["query_records", "create_record", "export_records"]),
        component("filter_section", "SECTION", "查询条件", "enterpriseFilterSection", children=[filter_form]),
        component("table_section", "SECTION", "数据列表", "enterpriseTableSection", children=[table]),
        component("empty_section", "SECTION", "", "enterpriseEmptySection", children=[
            component("empty_state", "EMPTY_STATE", "暂无数据", "enterpriseTableEmptyState")
        ]),
    ]
    return base_contract("table_page", "业务列表", "LIST_PAGE", "TABLE_LIST", components, interactions, {"tableRows": [{"code": "A-001", "status": "待处理"}]})


def build_detail_case() -> dict[str, Any]:
    components = [
        component("detail_header", "PAGE_HEADER", "详情页", "enterprisePageHeader"),
        component("summary_card", "CARD", "摘要信息", "insightCard", props={"items": [{"key": "status", "label": "状态", "value": "正常"}]}),
        component("detail_list", "DESCRIPTION_LIST", "详情信息", "descriptionPanel", dataBindingKey="detail", props={"items": [{"key": "owner", "label": "负责人", "value": "张三"}]}),
    ]
    return base_contract("detail_page", "详情页", "DETAIL_PAGE", "DETAIL_TABS", components, [], {"detail": {"owner": "张三"}})


def build_dashboard_case() -> dict[str, Any]:
    components = [
        component("dashboard_header", "PAGE_HEADER", "业务看板", "enterprisePageHeader"),
        component("metric_card", "CARD", "核心指标", "metricCard", props={"items": [{"key": "total", "label": "总数", "value": "128"}]}),
        component("status_card", "CARD", "状态分布", "insightCard", props={"items": [{"key": "pending", "label": "待处理", "value": "18"}]}),
    ]
    return base_contract("dashboard_page", "业务看板", "DASHBOARD_PAGE", "DASHBOARD", components, [], {})


def load_json_file(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def check_contract(payload: dict[str, Any], label: str) -> None:
    artifact = UiContractArtifact.model_validate(payload)
    UiContractQualityGate().enforce(artifact)
    print(f"{label}_OK")


def main() -> int:
    try:
        if len(sys.argv) > 1:
            path = Path(sys.argv[1])
            check_contract(load_json_file(path), path.name)
            print("DEMO_GRADE_GATE_OK")
            return 0

        cases = [
            ("FORM_PAGE", build_form_case()),
            ("TABLE_LIST", build_table_case()),
            ("DETAIL_PAGE", build_detail_case()),
            ("DASHBOARD", build_dashboard_case()),
        ]

        for label, payload in cases:
            check_contract(payload, label)

        print("DEMO_GRADE_GATE_OK")
        return 0

    except Exception as exc:
        print("DEMO_GRADE_GATE_FAILED")
        print(str(exc))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
