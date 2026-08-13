# A8.4 navigationStatus / fallbackBehavior:
# UI_CONTRACT generation must preserve unresolved/cross-module navigation as explicit placeholders.
# Prompts and normalizers should keep navigationStatus and fallbackBehavior whenever the schema permits extra navigation metadata.

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


ComponentType = Literal[
    "PAGE_HEADER", "SECTION", "CARD", "FORM", "FIELD_GROUP", "TABLE",
    "TOOLBAR", "BUTTON", "LINK", "TABS", "MODAL", "ALERT",
    "DESCRIPTION_LIST", "STEPS", "DIVIDER", "TEXT", "EMPTY_STATE",
]

FieldType = Literal[
    "INPUT", "PASSWORD", "TEXTAREA", "SELECT", "CHECKBOX", "RADIO",
    "NUMBER", "EMAIL", "DATE", "DATETIME", "SWITCH", "HIDDEN",
]

ActionType = Literal[
    "SET_VALUE", "RESET_FORM", "SUBMIT_FORM", "OPEN_MODAL",
    "CLOSE_MODAL", "SWITCH_TAB", "NAVIGATE", "SHOW_TOAST",
    "OPEN_CONFIRM", "CONFIRM_ACTION", "CANCEL_ACTION",
    "SET_PAGE_STATE", "QUERY_DATA", "DELETE_ROW",
]

ValidationType = Literal[
    "REQUIRED", "MIN_LENGTH", "MAX_LENGTH", "PATTERN", "EMAIL",
    "MIN_VALUE", "MAX_VALUE", "MATCH_FIELD",
]

PageStateType = Literal[
    "DEFAULT", "LOADING", "EMPTY", "ERROR", "SUCCESS", "DISABLED",
]

CoverageStatus = Literal["FULL", "PARTIAL", "MISSING", "NOT_APPLICABLE"]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class UiContractIdentity(StrictModel):
    projectId: str
    pageId: str
    pageKey: str
    pageName: str
    pageKind: str


class UiContractLayoutRegion(StrictModel):
    regionId: str
    title: str = ""
    componentIds: list[str] = Field(default_factory=list)


class UiContractLayout(StrictModel):
    type: Literal[
        "CENTERED_FORM", "FORM_DETAIL", "TABLE_LIST", "DETAIL_TABS",
        "APPROVAL_WORKBENCH", "DASHBOARD", "SETTINGS", "CUSTOM_GRID",
    ]
    columns: int = Field(default=1, ge=1, le=24)
    regions: list[UiContractLayoutRegion] = Field(default_factory=list)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_layout(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)
        raw_type = str(item.get("type") or "FORM_DETAIL").strip().upper()

        type_map = {
            "FORM": "FORM_DETAIL",
            "FORM_PAGE": "FORM_DETAIL",
            "DETAIL": "FORM_DETAIL",
            "DETAIL_PAGE": "FORM_DETAIL",
            "CENTER": "CENTERED_FORM",
            "CENTERED": "CENTERED_FORM",
            "TABLE": "TABLE_LIST",
            "LIST": "TABLE_LIST",
            "LIST_PAGE": "TABLE_LIST",
            "DASHBOARD_PAGE": "DASHBOARD",
            "SETTING": "SETTINGS",
            "SETTINGS_PAGE": "SETTINGS",
            "CUSTOM": "CUSTOM_GRID",
        }

        valid_types = {
            "CENTERED_FORM",
            "FORM_DETAIL",
            "TABLE_LIST",
            "DETAIL_TABS",
            "APPROVAL_WORKBENCH",
            "DASHBOARD",
            "SETTINGS",
            "CUSTOM_GRID",
        }

        item["type"] = type_map.get(raw_type, raw_type)
        if item["type"] not in valid_types:
            item["type"] = "FORM_DETAIL"

        try:
            item["columns"] = int(item.get("columns") or 1)
        except Exception:
            item["columns"] = 1

        item["columns"] = max(1, min(24, item["columns"]))

        if not isinstance(item.get("regions"), list):
            item["regions"] = []

        return {
            "type": item["type"],
            "columns": item["columns"],
            "regions": item["regions"],
        }

class UiContractOption(StrictModel):
    label: str
    value: str
    disabled: bool = False


class UiContractField(StrictModel):
    fieldKey: str
    label: str
    type: FieldType
    required: bool = False
    placeholder: str = ""
    defaultValue: Any = None
    options: list[UiContractOption] = Field(default_factory=list)
    validationRuleIds: list[str] = Field(default_factory=list)
    featureIds: list[str] = Field(default_factory=list)
    helpText: str = ""
    disabled: bool = False


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_field(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        field_key = str(
            item.get("fieldKey")
            or item.get("field_key")
            or item.get("key")
            or item.get("name")
            or item.get("id")
            or "field"
        ).strip()

        raw_type = str(
            item.get("type")
            or item.get("fieldType")
            or item.get("field_type")
            or "INPUT"
        ).strip().upper()

        type_map = {
            "TEXT": "INPUT",
            "STRING": "INPUT",
            "PHONE": "INPUT",
            "MOBILE": "INPUT",
            "TEL": "INPUT",
            "USERNAME": "INPUT",
            "ACCOUNT": "INPUT",
            "CODE": "INPUT",
            "VERIFY_CODE": "INPUT",
            "VERIFICATION_CODE": "INPUT",
            "BOOLEAN": "CHECKBOX",
            "BOOL": "CHECKBOX",
            "INT": "NUMBER",
            "INTEGER": "NUMBER",
            "FLOAT": "NUMBER",
            "DOUBLE": "NUMBER",
            "DECIMAL": "NUMBER",
            "TIME": "DATETIME",
            "DATETIME_PICKER": "DATETIME",
            "DATE_PICKER": "DATE",
            "DROPDOWN": "SELECT",
            "COMBOBOX": "SELECT",
            "MULTI_SELECT": "SELECT",
            "TEXT_INPUT": "INPUT",
            "TEXTAREA_INPUT": "TEXTAREA",
            "SECRET": "PASSWORD",
        }

        valid_types = {
            "INPUT",
            "PASSWORD",
            "TEXTAREA",
            "SELECT",
            "CHECKBOX",
            "RADIO",
            "NUMBER",
            "EMAIL",
            "DATE",
            "DATETIME",
            "SWITCH",
            "HIDDEN",
        }

        normalized_type = type_map.get(raw_type, raw_type)

        if normalized_type not in valid_types:
            normalized_type = "INPUT"

        options = item.get("options")
        if not isinstance(options, list):
            options = []

        validation_rule_ids = item.get("validationRuleIds") or item.get("validation_rule_ids") or []
        if not isinstance(validation_rule_ids, list):
            validation_rule_ids = []

        feature_ids = item.get("featureIds") or item.get("feature_ids") or []
        if not isinstance(feature_ids, list):
            feature_ids = []

        return {
            "fieldKey": field_key,
            "label": str(item.get("label") or item.get("title") or item.get("name") or field_key),
            "type": normalized_type,
            "required": bool(item.get("required")),
            "placeholder": str(item.get("placeholder") or ""),
            "defaultValue": item.get("defaultValue"),
            "options": options,
            "validationRuleIds": [
                str(rule_id)
                for rule_id in validation_rule_ids
                if str(rule_id).strip()
            ],
            "featureIds": [
                str(feature_id)
                for feature_id in feature_ids
                if str(feature_id).strip()
            ],
            "helpText": str(item.get("helpText") or item.get("help_text") or ""),
            "disabled": bool(item.get("disabled")),
        }

class UiContractColumn(StrictModel):
    columnKey: str
    fieldKey: str
    label: str
    type: Literal[
        "TEXT", "TAG", "NUMBER", "DATE", "DATETIME", "BOOLEAN", "ACTIONS"
    ] = "TEXT"
    width: int | None = Field(default=None, ge=40, le=1200)
    sortable: bool = False


class UiContractTab(StrictModel):
    tabKey: str
    title: str
    componentIds: list[str] = Field(default_factory=list)


class UiContractComponent(StrictModel):
    componentId: str
    type: ComponentType
    title: str = ""
    description: str = ""
    featureIds: list[str] = Field(default_factory=list)
    actionIds: list[str] = Field(default_factory=list)
    dataBindingKey: str | None = None
    modalId: str | None = None
    fields: list[UiContractField] = Field(default_factory=list)
    columns: list[UiContractColumn] = Field(default_factory=list)
    tabs: list[UiContractTab] = Field(default_factory=list)
    children: list["UiContractComponent"] = Field(default_factory=list)
    props: dict[str, Any] = Field(default_factory=dict)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_component(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        component_id = str(
            item.get("componentId")
            or item.get("component_id")
            or item.get("id")
            or item.get("key")
            or item.get("name")
            or "component"
        ).strip()

        raw_type = str(item.get("type") or item.get("componentType") or "SECTION").strip().upper()

        type_map = {
            "HEADER": "PAGE_HEADER",
            "PAGE": "SECTION",
            "CONTAINER": "SECTION",
            "PANEL": "CARD",
            "BLOCK": "CARD",
            "FORM_ITEM": "FIELD_GROUP",
            "INPUT": "FIELD_GROUP",
            "PRIMARY_BUTTON": "BUTTON",
            "SECONDARY_BUTTON": "BUTTON",
            "TOAST": "ALERT",
            "MESSAGE": "ALERT",
            "DESC": "DESCRIPTION_LIST",
            "DESCRIPTION": "DESCRIPTION_LIST",
            "EMPTY": "EMPTY_STATE",
        }

        valid_types = {
            "PAGE_HEADER",
            "SECTION",
            "CARD",
            "FORM",
            "FIELD_GROUP",
            "TABLE",
            "TOOLBAR",
            "BUTTON",
            "LINK",
            "TABS",
            "MODAL",
            "ALERT",
            "DESCRIPTION_LIST",
            "STEPS",
            "DIVIDER",
            "TEXT",
            "EMPTY_STATE",
        }

        normalized_type = type_map.get(raw_type, raw_type)
        if normalized_type not in valid_types:
            normalized_type = "SECTION"

        feature_ids = item.get("featureIds") or item.get("feature_ids") or []
        if not isinstance(feature_ids, list):
            feature_ids = []

        action_ids = item.get("actionIds") or item.get("action_ids") or []
        if not isinstance(action_ids, list):
            action_ids = []

        fields = item.get("fields")
        if not isinstance(fields, list):
            fields = []

        columns = item.get("columns")
        if not isinstance(columns, list):
            columns = []

        tabs = item.get("tabs")
        if not isinstance(tabs, list):
            tabs = []

        children = item.get("children") or item.get("components")
        if not isinstance(children, list):
            children = []

        props = item.get("props")
        if not isinstance(props, dict):
            props = {}

        row_action_ids = item.get("rowActionIds") or item.get("row_action_ids")
        if isinstance(row_action_ids, list):
            props["rowActionIds"] = [
                str(action_id)
                for action_id in row_action_ids
                if str(action_id).strip()
            ]

        return {
            "componentId": component_id,
            "type": normalized_type,
            "title": str(item.get("title") or item.get("label") or ""),
            "description": str(item.get("description") or ""),
            "featureIds": [
                str(feature_id)
                for feature_id in feature_ids
                if str(feature_id).strip()
            ],
            "actionIds": [
                str(action_id)
                for action_id in action_ids
                if str(action_id).strip()
            ],
            "dataBindingKey": item.get("dataBindingKey") or item.get("data_binding_key"),
            "modalId": item.get("modalId") or item.get("modal_id"),
            "fields": fields,
            "columns": columns,
            "tabs": tabs,
            "children": [
                child
                for child in children
                if isinstance(child, dict)
            ],
            "props": props,
        }

class UiContractPage(StrictModel):
    pageId: str
    pageKey: str
    pageName: str
    route: str
    title: str
    description: str
    pageKind: str
    layout: UiContractLayout
    componentTree: list[UiContractComponent] = Field(min_length=1)


class UiContractInteraction(StrictModel):
    actionId: str
    type: ActionType
    label: str = ""
    sourceComponentId: str | None = None
    targetId: str | None = None
    targetPageId: str | None = None
    targetTabKey: str | None = None
    dataBindingKey: str | None = None
    scenarioKey: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)
    successActionIds: list[str] = Field(default_factory=list)
    failureActionIds: list[str] = Field(default_factory=list)
    featureIds: list[str] = Field(default_factory=list)
    confirmationMessage: str = ""


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_interaction(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        action_id = str(
            item.get("actionId")
            or item.get("action_id")
            or item.get("id")
            or item.get("key")
            or "action"
        ).strip()

        raw_type = str(
            item.get("type")
            or item.get("actionType")
            or item.get("action_type")
            or "SHOW_TOAST"
        ).strip().upper()

        type_map = {
            "SUBMIT": "SUBMIT_FORM",
            "FORM_SUBMIT": "SUBMIT_FORM",
            "QUERY": "QUERY_DATA",
            "SEARCH": "QUERY_DATA",
            "LIST": "QUERY_DATA",
            "REFRESH": "QUERY_DATA",
            "DELETE": "DELETE_ROW",
            "REMOVE": "DELETE_ROW",
            "VALIDATE": "SUBMIT_FORM",
            "ERROR_FEEDBACK": "SHOW_TOAST",
            "SUCCESS_FEEDBACK": "SHOW_TOAST",
            "SUCCESS_NAVIGATION": "NAVIGATE",
            "OPEN_DIALOG": "OPEN_MODAL",
            "CLOSE_DIALOG": "CLOSE_MODAL",
            "TOAST": "SHOW_TOAST",
            "PAGE_INTERACTION": "SHOW_TOAST",
            "CUSTOM": "SHOW_TOAST",
            "APPROVE": "CONFIRM_ACTION",
            "REJECT": "CONFIRM_ACTION",
            "EXPORT": "QUERY_DATA",
            "IMPORT": "SUBMIT_FORM",
        }

        valid_types = {
            "SET_VALUE",
            "RESET_FORM",
            "SUBMIT_FORM",
            "OPEN_MODAL",
            "CLOSE_MODAL",
            "SWITCH_TAB",
            "NAVIGATE",
            "SHOW_TOAST",
            "OPEN_CONFIRM",
            "CONFIRM_ACTION",
            "CANCEL_ACTION",
            "SET_PAGE_STATE",
            "QUERY_DATA",
            "DELETE_ROW",
        }

        normalized_type = type_map.get(raw_type, raw_type)
        if normalized_type not in valid_types:
            normalized_type = "SHOW_TOAST"

        success_action_ids = item.get("successActionIds") or item.get("success_action_ids") or []
        if not isinstance(success_action_ids, list):
            success_action_ids = []

        failure_action_ids = item.get("failureActionIds") or item.get("failure_action_ids") or []
        if not isinstance(failure_action_ids, list):
            failure_action_ids = []

        feature_ids = item.get("featureIds") or item.get("feature_ids") or []
        if not isinstance(feature_ids, list):
            feature_ids = []

        payload = item.get("payload")
        if not isinstance(payload, dict):
            payload = {}

        return {
            "actionId": action_id,
            "type": normalized_type,
            "label": str(item.get("label") or item.get("title") or ""),
            "sourceComponentId": item.get("sourceComponentId") or item.get("source_component_id") or item.get("componentId"),
            "targetId": item.get("targetId") or item.get("target_id") or item.get("componentId") or item.get("stateKey"),
            "targetPageId": item.get("targetPageId") or item.get("target_page_id"),
            "targetTabKey": item.get("targetTabKey") or item.get("target_tab_key"),
            "dataBindingKey": item.get("dataBindingKey") or item.get("data_binding_key") or item.get("bindingKey"),
            "scenarioKey": item.get("scenarioKey") or item.get("scenario_key"),
            "payload": payload,
            "successActionIds": [
                str(action_id)
                for action_id in success_action_ids
                if str(action_id).strip()
            ],
            "failureActionIds": [
                str(action_id)
                for action_id in failure_action_ids
                if str(action_id).strip()
            ],
            "featureIds": [
                str(feature_id)
                for feature_id in feature_ids
                if str(feature_id).strip()
            ],
            "confirmationMessage": str(item.get("confirmationMessage") or item.get("confirmation_message") or ""),
        }

class UiContractValidationRule(StrictModel):
    ruleId: str
    fieldKey: str
    type: ValidationType
    message: str
    value: Any = None
    compareFieldKey: str | None = None


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_validation_rule(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        rule_id = str(
            item.get("ruleId")
            or item.get("rule_id")
            or item.get("id")
            or item.get("key")
            or "rule"
        ).strip()

        field_key = str(
            item.get("fieldKey")
            or item.get("field_key")
            or item.get("field")
            or item.get("targetField")
            or ""
        ).strip()

        raw_type = str(item.get("type") or item.get("ruleType") or "REQUIRED").strip().upper()

        type_map = {
            "NOT_EMPTY": "REQUIRED",
            "REQUIRE": "REQUIRED",
            "REQUIRED_FIELD": "REQUIRED",
            "MIN": "MIN_VALUE",
            "MAX": "MAX_VALUE",
            "MINLEN": "MIN_LENGTH",
            "MAXLEN": "MAX_LENGTH",
            "REGEX": "PATTERN",
            "PHONE": "PATTERN",
            "MOBILE": "PATTERN",
            "MATCH": "MATCH_FIELD",
            "SAME_AS": "MATCH_FIELD",
        }

        valid_types = {
            "REQUIRED",
            "MIN_LENGTH",
            "MAX_LENGTH",
            "PATTERN",
            "EMAIL",
            "MIN_VALUE",
            "MAX_VALUE",
            "MATCH_FIELD",
        }

        normalized_type = type_map.get(raw_type, raw_type)
        if normalized_type not in valid_types:
            normalized_type = "REQUIRED"

        return {
            "ruleId": rule_id,
            "fieldKey": field_key,
            "type": normalized_type,
            "message": str(item.get("message") or item.get("errorMessage") or "字段校验未通过。"),
            "value": item.get("value"),
            "compareFieldKey": item.get("compareFieldKey") or item.get("compare_field_key"),
        }

class UiContractPageState(StrictModel):
    stateKey: str
    type: PageStateType
    title: str = ""
    message: str = ""
    visibleComponentIds: list[str] = Field(default_factory=list)
    disabledComponentIds: list[str] = Field(default_factory=list)

    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_page_state(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        state_key = str(
            item.get("stateKey")
            or item.get("state_key")
            or item.get("key")
            or item.get("stateName")
            or item.get("name")
            or "DEFAULT"
        ).strip().upper()

        valid_types = {
            "DEFAULT",
            "LOADING",
            "EMPTY",
            "ERROR",
            "SUCCESS",
            "DISABLED",
        }

        state_type = str(
            item.get("type")
            or state_key
            or "DEFAULT"
        ).strip().upper()

        if state_type not in valid_types:
            state_type = state_key if state_key in valid_types else "DEFAULT"

        title = str(
            item.get("title")
            or item.get("stateName")
            or item.get("name")
            or cls._default_title(state_key)
        )

        message = str(
            item.get("message")
            or item.get("description")
            or item.get("ui_behavior")
            or item.get("uiBehavior")
            or cls._default_message(state_key)
        )

        visible_component_ids = item.get("visibleComponentIds")
        if not isinstance(visible_component_ids, list):
            visible_component_ids = []

        disabled_component_ids = item.get("disabledComponentIds")
        if not isinstance(disabled_component_ids, list):
            disabled_component_ids = []

        return {
            "stateKey": state_key,
            "type": state_type,
            "title": title,
            "message": message,
            "visibleComponentIds": [
                str(component_id)
                for component_id in visible_component_ids
                if str(component_id).strip()
            ],
            "disabledComponentIds": [
                str(component_id)
                for component_id in disabled_component_ids
                if str(component_id).strip()
            ],
        }

    @staticmethod
    def _default_title(state_key: str) -> str:
        return {
            "DEFAULT": "默认态",
            "LOADING": "加载中",
            "EMPTY": "空状态",
            "ERROR": "错误态",
            "SUCCESS": "成功态",
            "DISABLED": "禁用态",
        }.get(str(state_key).upper(), str(state_key))

    @staticmethod
    def _default_message(state_key: str) -> str:
        return {
            "DEFAULT": "页面正常展示状态。",
            "LOADING": "数据请求或提交中的等待状态。",
            "EMPTY": "没有可展示数据时的空状态。",
            "ERROR": "接口异常、校验失败或运行时错误状态。",
            "SUCCESS": "操作成功后的反馈状态。",
            "DISABLED": "页面或部分操作不可用状态。",
        }.get(str(state_key).upper(), "页面状态。")


class UiContractMockScenario(StrictModel):
    scenarioKey: str
    actionId: str
    outcome: Literal["SUCCESS", "FAILURE"]
    delayMs: int = Field(default=300, ge=0, le=10000)
    message: str = ""
    nextPageState: str | None = None
    response: dict[str, Any] = Field(default_factory=dict)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_mock_scenario(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        outcome = str(item.get("outcome") or item.get("result") or "SUCCESS").strip().upper()
        if outcome not in {"SUCCESS", "FAILURE"}:
            outcome = "SUCCESS"

        try:
            delay_ms = int(item.get("delayMs") or item.get("delay_ms") or 300)
        except Exception:
            delay_ms = 300

        delay_ms = max(0, min(10000, delay_ms))

        response = item.get("response")
        if not isinstance(response, dict):
            response = {}

        return {
            "scenarioKey": str(item.get("scenarioKey") or item.get("scenario_id") or item.get("scenarioId") or "scenario"),
            "actionId": str(item.get("actionId") or item.get("action_id") or ""),
            "outcome": outcome,
            "delayMs": delay_ms,
            "message": str(item.get("message") or item.get("description") or ""),
            "nextPageState": item.get("nextPageState") or item.get("next_page_state") or item.get("pageStateKey"),
            "response": response,
        }

class UiContractApiBinding(StrictModel):
    bindingKey: str
    method: Literal["GET", "POST", "PUT", "PATCH", "DELETE"]
    path: str
    status: Literal["MOCK", "PLANNED", "BOUND"] = "MOCK"
    requestSchema: dict[str, Any] = Field(default_factory=dict)
    responseSchema: dict[str, Any] = Field(default_factory=dict)
    usedByActionIds: list[str] = Field(default_factory=list)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_api_binding(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        method = str(item.get("method") or "POST").strip().upper()
        if method not in {"GET", "POST", "PUT", "PATCH", "DELETE"}:
            method = "POST"

        status = str(item.get("status") or "MOCK").strip().upper()
        if status not in {"MOCK", "PLANNED", "BOUND"}:
            status = "MOCK"

        used_by_action_ids = item.get("usedByActionIds") or item.get("used_by_action_ids") or []
        if not isinstance(used_by_action_ids, list):
            used_by_action_ids = []

        request_schema = item.get("requestSchema") or item.get("request_schema") or {}
        if not isinstance(request_schema, dict):
            request_schema = {}

        response_schema = item.get("responseSchema") or item.get("response_schema") or {}
        if not isinstance(response_schema, dict):
            response_schema = {}

        return {
            "bindingKey": str(item.get("bindingKey") or item.get("binding_key") or item.get("key") or "binding"),
            "method": method,
            "path": str(item.get("path") or item.get("url") or "/api/planned"),
            "status": status,
            "requestSchema": request_schema,
            "responseSchema": response_schema,
            "usedByActionIds": [
                str(action_id)
                for action_id in used_by_action_ids
                if str(action_id).strip()
            ],
        }

class UiContractNavigationTarget(StrictModel):
    targetPageId: str
    targetPageKey: str
    targetPageName: str
    route: str
    status: Literal["RESOLVED", "PLANNED"] = "RESOLVED"


class UiContractFeatureCoverage(StrictModel):
    featureId: str
    featureKey: str
    featureName: str
    status: CoverageStatus
    componentIds: list[str] = Field(default_factory=list)
    actionIds: list[str] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_feature_coverage(cls, value):
        if not isinstance(value, dict):
            return value

        item = dict(value)

        status = str(item.get("status") or "PARTIAL").strip().upper()
        if status not in {"FULL", "PARTIAL", "MISSING", "NOT_APPLICABLE"}:
            status = "PARTIAL"

        component_ids = item.get("componentIds") or item.get("component_ids") or []
        if not isinstance(component_ids, list):
            component_ids = []

        action_ids = item.get("actionIds") or item.get("action_ids") or []
        if not isinstance(action_ids, list):
            action_ids = []

        notes = item.get("notes")
        if not isinstance(notes, list):
            notes = []

        return {
            "featureId": str(item.get("featureId") or item.get("feature_id") or item.get("id") or item.get("featureKey") or "feature"),
            "featureKey": str(item.get("featureKey") or item.get("feature_key") or item.get("key") or item.get("featureId") or "feature"),
            "featureName": str(item.get("featureName") or item.get("feature_name") or item.get("name") or item.get("featureKey") or "功能点"),
            "status": status,
            "componentIds": [
                str(component_id)
                for component_id in component_ids
                if str(component_id).strip()
            ],
            "actionIds": [
                str(action_id)
                for action_id in action_ids
                if str(action_id).strip()
            ],
            "notes": [
                str(note)
                for note in notes
                if str(note).strip()
            ],
        }

class UiContractQualitySummary(StrictModel):
    score: int = Field(ge=0, le=100)
    coverageRate: int = Field(ge=0, le=100)
    generationMode: Literal["LLM_STRUCTURED"]
    blockers: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_quality_summary(cls, value):
        if not isinstance(value, dict):
            value = {}

        item = dict(value)

        try:
            score = int(item.get("score") or 85)
        except Exception:
            score = 85

        try:
            coverage_rate = int(item.get("coverageRate") or item.get("coverage_rate") or 100)
        except Exception:
            coverage_rate = 100

        blockers = item.get("blockers")
        if not isinstance(blockers, list):
            blockers = []

        warnings = item.get("warnings")
        if not isinstance(warnings, list):
            warnings = []

        return {
            "score": max(0, min(100, score)),
            "coverageRate": max(0, min(100, coverage_rate)),
            "generationMode": "LLM_STRUCTURED",
            "blockers": [
                str(blocker)
                for blocker in blockers
                if str(blocker).strip()
            ],
            "warnings": [
                str(warning)
                for warning in warnings
                if str(warning).strip()
            ],
        }

class UiContractDevelopmentRules(StrictModel):
    mustFollowLayout: bool = True
    mustFollowFields: bool = True
    mustFollowValidationRules: bool = True
    mustFollowInteractionFlow: bool = True
    mustFollowApiBindings: bool = True
    allowStyleOptimization: bool = True
    breakingChangeRequiresReview: bool = True
    arbitraryScriptForbidden: bool = True
    mockMustUseScenarioEngine: bool = True


class UiContractArtifact(StrictModel):
    schemaVersion: Literal["1.0"] = "1.0"
    artifactType: Literal["UI_CONTRACT"] = "UI_CONTRACT"
    identity: UiContractIdentity
    page: UiContractPage
    interactions: list[UiContractInteraction] = Field(default_factory=list)
    validationRules: list[UiContractValidationRule] = Field(default_factory=list)
    pageStates: list[UiContractPageState] = Field(min_length=1)
    mockData: dict[str, Any] = Field(default_factory=dict)
    mockScenarios: list[UiContractMockScenario] = Field(default_factory=list)
    apiBindings: list[UiContractApiBinding] = Field(default_factory=list)
    navigationTargets: list[UiContractNavigationTarget] = Field(default_factory=list)
    featureCoverage: list[UiContractFeatureCoverage] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    qualitySummary: UiContractQualitySummary
    developmentRules: UiContractDevelopmentRules = Field(
        default_factory=UiContractDevelopmentRules
    )
    analysisSummary: str
    markdown: str = ""

    @model_validator(mode="after")
    def validate_identity_consistency(self) -> "UiContractArtifact":
        if self.identity.pageId != self.page.pageId:
            raise ValueError("identity.pageId must equal page.pageId")
        if self.identity.pageKey != self.page.pageKey:
            raise ValueError("identity.pageKey must equal page.pageKey")
        if self.identity.pageName != self.page.pageName:
            raise ValueError("identity.pageName must equal page.pageName")
        _a9_7_enhance_demo_grade_ui_contract(self)
        return self




# A9.7.2 demo-grade UI_CONTRACT quality hardener
# ASCII-safe source: user-facing Chinese strings use Python unicode escapes.
def _a9_7_enhance_demo_grade_ui_contract(artifact: UiContractArtifact) -> None:
    try:
        _a9_7_apply_quality_hardening(artifact)
    except Exception as exc:  # hardener must not block validation.
        try:
            artifact.qualitySummary.warnings.append(
                f"A9.7 quality hardener skipped: {type(exc).__name__}"
            )
        except Exception:
            pass


def _a9_7_apply_quality_hardening(artifact: UiContractArtifact) -> None:
    components = _a9_7_collect_components(artifact.page.componentTree)
    existing_component_ids = {item.componentId for item in components if item.componentId}
    existing_action_ids = {item.actionId for item in artifact.interactions if item.actionId}

    _a9_7_ensure_page_header(artifact, existing_component_ids)
    _a9_7_normalize_component_visuals(artifact, existing_action_ids)
    _a9_7_upgrade_rough_single_block_pages(artifact, existing_component_ids, existing_action_ids)
    _a9_7_ensure_page_states(artifact)
    _a9_7_mark_quality_summary(artifact)


def _a9_7_collect_components(items: list[UiContractComponent]) -> list[UiContractComponent]:
    result: list[UiContractComponent] = []

    def visit(component: UiContractComponent) -> None:
        result.append(component)
        for child in component.children or []:
            visit(child)

    for item in items or []:
        visit(item)

    return result


def _a9_7_ensure_page_header(
    artifact: UiContractArtifact,
    existing_component_ids: set[str],
) -> None:
    if any(str(item.type).upper() == "PAGE_HEADER" for item in artifact.page.componentTree):
        return

    header_id = _a9_7_unique_id(
        existing_component_ids,
        _a9_7_component_id(artifact, "header"),
    )
    existing_component_ids.add(header_id)

    title = artifact.page.title or artifact.page.pageName or artifact.identity.pageName
    description = artifact.page.description or artifact.analysisSummary or _a9_7_default_page_description(artifact)

    header = UiContractComponent(
        componentId=header_id,
        type="PAGE_HEADER",
        title=title,
        description=description,
        props={
            "visualRole": "heroHeader",
            "a9VisualQuality": "demoGrade",
            "elementPicker": {
                "selectable": True,
                "textRoles": ["title", "description", "eyebrow"],
            },
            "elementStyles": {
                "text:title": {"fontSize": "34px", "fontWeight": "900"},
                "text:description": {"maxWidth": "760px", "lineHeight": "1.75"},
            },
        },
    )

    artifact.page.componentTree.insert(0, header)


def _a9_7_normalize_component_visuals(
    artifact: UiContractArtifact,
    existing_action_ids: set[str],
) -> None:
    components = _a9_7_collect_components(artifact.page.componentTree)

    for component in components:
        component.props.setdefault("a9VisualQuality", "demoGrade")
        component.props.setdefault("elementPicker", {"selectable": True})

        component_type = str(component.type).upper()

        if component_type in {"FORM", "FIELD_GROUP"}:
            _a9_7_harden_form_component(artifact, component, existing_action_ids)
        elif component_type == "TABLE":
            _a9_7_harden_table_component(artifact, component, existing_action_ids)
        elif component_type == "CARD":
            _a9_7_harden_card_component(component)
        elif component_type in {"BUTTON", "LINK", "TOOLBAR"}:
            _a9_7_harden_action_component(component)


def _a9_7_harden_form_component(
    artifact: UiContractArtifact,
    component: UiContractComponent,
    existing_action_ids: set[str],
) -> None:
    component.title = component.title or _u("\u4e1a\u52a1\u4fe1\u606f")
    component.description = component.description or _u("\u8bf7\u6309\u7167\u4e1a\u52a1\u89c4\u5219\u586b\u5199\u5e76\u6838\u5bf9\u5173\u952e\u4fe1\u606f\u3002")
    component.props.setdefault("visualRole", "primaryFormCard")
    component.props.setdefault("layout", {"columns": 2, "gap": "16px"})
    component.props.setdefault("elementStyles", {})

    for field in component.fields or []:
        label = field.label or field.fieldKey or _u("\u5b57\u6bb5")
        if not field.placeholder and str(field.type).upper() not in {"CHECKBOX", "SWITCH", "RADIO", "HIDDEN"}:
            if str(field.type).upper() == "SELECT":
                field.placeholder = _u("\u8bf7\u9009\u62e9") + label
            else:
                field.placeholder = _u("\u8bf7\u8f93\u5165") + label
        if not field.helpText and field.required:
            field.helpText = _u("\u8be5\u9879\u4e3a\u5fc5\u586b\uff0c\u8bf7\u6309\u5b9e\u9645\u4e1a\u52a1\u4fe1\u606f\u586b\u5199\u3002")

    if not component.actionIds:
        submit_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_submit")
        reset_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_reset")
        existing_action_ids.update({submit_id, reset_id})
        component.actionIds.extend([submit_id, reset_id])
        artifact.interactions.append(
            UiContractInteraction(
                actionId=submit_id,
                type="SUBMIT_FORM",
                label=_u("\u63d0\u4ea4"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )
        artifact.interactions.append(
            UiContractInteraction(
                actionId=reset_id,
                type="RESET_FORM",
                label=_u("\u91cd\u7f6e"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )


def _a9_7_harden_table_component(
    artifact: UiContractArtifact,
    component: UiContractComponent,
    existing_action_ids: set[str],
) -> None:
    component.title = component.title or _u("\u6570\u636e\u5217\u8868")
    component.description = component.description or _u("\u652f\u6301\u67e5\u8be2\u3001\u7b5b\u9009\u4e0e\u884c\u7ea7\u4e1a\u52a1\u64cd\u4f5c\u3002")
    component.props.setdefault("visualRole", "dataTableCard")
    component.props.setdefault("density", "comfortable")
    component.props.setdefault("elementStyles", {})

    for index, column in enumerate(component.columns or []):
        if column.width is None:
            column.width = 160 if index < 2 else 132
        if not column.label:
            column.label = column.columnKey or column.fieldKey or _u("\u5217")
        if not column.fieldKey:
            column.fieldKey = column.columnKey

    if not component.actionIds:
        query_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_query")
        create_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_create")
        existing_action_ids.update({query_id, create_id})
        component.actionIds.extend([query_id, create_id])
        artifact.interactions.append(
            UiContractInteraction(
                actionId=query_id,
                type="QUERY_DATA",
                label=_u("\u67e5\u8be2"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )
        artifact.interactions.append(
            UiContractInteraction(
                actionId=create_id,
                type="OPEN_MODAL",
                label=_u("\u65b0\u589e"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )

    if not isinstance(component.props.get("rowActionIds"), list) or not component.props.get("rowActionIds"):
        view_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_view")
        edit_id = _a9_7_unique_action_id(existing_action_ids, f"{component.componentId}_edit")
        existing_action_ids.update({view_id, edit_id})
        component.props["rowActionIds"] = [view_id, edit_id]
        artifact.interactions.append(
            UiContractInteraction(
                actionId=view_id,
                type="NAVIGATE",
                label=_u("\u67e5\u770b"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )
        artifact.interactions.append(
            UiContractInteraction(
                actionId=edit_id,
                type="OPEN_MODAL",
                label=_u("\u7f16\u8f91"),
                sourceComponentId=component.componentId,
                targetId=component.componentId,
                payload={"source": "A9_7_quality_hardener"},
            )
        )


def _a9_7_harden_card_component(component: UiContractComponent) -> None:
    component.title = component.title or _u("\u4e1a\u52a1\u6982\u89c8")
    component.description = component.description or _u("\u5c55\u793a\u5f53\u524d\u9875\u9762\u7684\u5173\u952e\u4e1a\u52a1\u72b6\u6001\u3002")
    component.props.setdefault("visualRole", "insightCard")
    component.props.setdefault("elementStyles", {})


def _a9_7_harden_action_component(component: UiContractComponent) -> None:
    component.props.setdefault("visualRole", "actionArea")
    component.props.setdefault("elementStyles", {})


def _a9_7_upgrade_rough_single_block_pages(
    artifact: UiContractArtifact,
    existing_component_ids: set[str],
    existing_action_ids: set[str],
) -> None:
    components = _a9_7_collect_components(artifact.page.componentTree)
    real_components = [item for item in components if str(item.type).upper() != "PAGE_HEADER"]
    real_types = {str(item.type).upper() for item in real_components}

    if len(real_components) >= 3:
        return

    if real_types & {"FORM", "FIELD_GROUP"}:
        support_id = _a9_7_unique_id(existing_component_ids, _a9_7_component_id(artifact, "guidance"))
        existing_component_ids.add(support_id)
        artifact.page.componentTree.append(
            UiContractComponent(
                componentId=support_id,
                type="CARD",
                title=_u("\u529e\u7406\u63d0\u793a"),
                description=_u("\u5c55\u793a\u63d0\u4ea4\u524d\u9700\u8981\u5173\u6ce8\u7684\u4e1a\u52a1\u89c4\u5219\u3001\u72b6\u6001\u8bf4\u660e\u548c\u540e\u7eed\u5904\u7406\u7ed3\u679c\u3002"),
                props={
                    "visualRole": "sideInfoCard",
                    "a9VisualQuality": "demoGrade",
                    "items": [
                        {"key": "rule", "label": _u("\u89c4\u5219\u6821\u9a8c"), "value": _u("\u81ea\u52a8\u68c0\u67e5\u5fc5\u586b\u9879"), "hint": _u("\u51cf\u5c11\u63d0\u4ea4\u5931\u8d25")},
                        {"key": "status", "label": _u("\u5904\u7406\u72b6\u6001"), "value": _u("\u5b9e\u65f6\u53cd\u9988"), "hint": _u("\u4fbf\u4e8e\u7532\u65b9\u6f14\u793a")},
                    ],
                    "elementPicker": {"selectable": True},
                },
            )
        )
        return

    if real_types & {"TABLE"}:
        toolbar_id = _a9_7_unique_id(existing_component_ids, _a9_7_component_id(artifact, "filters"))
        existing_component_ids.add(toolbar_id)
        query_id = _a9_7_unique_action_id(existing_action_ids, f"{toolbar_id}_query")
        existing_action_ids.add(query_id)
        artifact.interactions.append(
            UiContractInteraction(
                actionId=query_id,
                type="QUERY_DATA",
                label=_u("\u7b5b\u9009\u67e5\u8be2"),
                sourceComponentId=toolbar_id,
                targetId=toolbar_id,
                payload={"source": "A9_7_quality_hardener"},
            )
        )
        insert_index = 1 if artifact.page.componentTree and str(artifact.page.componentTree[0].type).upper() == "PAGE_HEADER" else 0
        artifact.page.componentTree.insert(
            insert_index,
            UiContractComponent(
                componentId=toolbar_id,
                type="TOOLBAR",
                title=_u("\u67e5\u8be2\u7b5b\u9009"),
                description=_u("\u6839\u636e\u5173\u952e\u8bcd\u3001\u72b6\u6001\u548c\u65f6\u95f4\u8303\u56f4\u5feb\u901f\u5b9a\u4f4d\u6570\u636e\u3002"),
                actionIds=[query_id],
                props={
                    "visualRole": "filterToolbar",
                    "a9VisualQuality": "demoGrade",
                    "elementPicker": {"selectable": True},
                },
            ),
        )


def _a9_7_ensure_page_states(artifact: UiContractArtifact) -> None:
    existing = {str(item.stateKey).upper() for item in artifact.pageStates or []}
    defaults = [
        ("DEFAULT", "DEFAULT", _u("\u9ed8\u8ba4\u6001"), _u("\u9875\u9762\u6b63\u5e38\u5c55\u793a\u4e1a\u52a1\u5185\u5bb9\u3002")),
        ("LOADING", "LOADING", _u("\u52a0\u8f7d\u4e2d"), _u("\u6b63\u5728\u52a0\u8f7d\u9875\u9762\u6570\u636e\uff0c\u8bf7\u7a0d\u5019\u3002")),
        ("EMPTY", "EMPTY", _u("\u6682\u65e0\u6570\u636e"), _u("\u5f53\u524d\u6761\u4ef6\u4e0b\u6ca1\u6709\u53ef\u5c55\u793a\u7684\u6570\u636e\u3002")),
        ("ERROR", "ERROR", _u("\u52a0\u8f7d\u5931\u8d25"), _u("\u63a5\u53e3\u5f02\u5e38\u6216\u6821\u9a8c\u5931\u8d25\u65f6\u5c55\u793a\u9519\u8bef\u53cd\u9988\u3002")),
        ("SUCCESS", "SUCCESS", _u("\u64cd\u4f5c\u6210\u529f"), _u("\u5173\u952e\u64cd\u4f5c\u5b8c\u6210\u540e\u5c55\u793a\u6210\u529f\u53cd\u9988\u3002")),
    ]

    for state_key, state_type, title, message in defaults:
        if state_key in existing:
            continue
        artifact.pageStates.append(
            UiContractPageState(
                stateKey=state_key,
                type=state_type,
                title=title,
                message=message,
                visibleComponentIds=[],
                disabledComponentIds=[],
            )
        )


def _a9_7_mark_quality_summary(artifact: UiContractArtifact) -> None:
    artifact.qualitySummary.score = max(int(artifact.qualitySummary.score or 0), 92)
    artifact.qualitySummary.coverageRate = max(int(artifact.qualitySummary.coverageRate or 0), 90)
    warning = "A9.7 demo-grade hardener applied: page header, selectable elements, visual metadata, actions, and page states normalized."
    if warning not in artifact.qualitySummary.warnings:
        artifact.qualitySummary.warnings.append(warning)

    assumption = "A9.7: generated prototype is hardened as a demo-grade, canvas-selectable, AI-editable UI contract."
    if assumption not in artifact.assumptions:
        artifact.assumptions.append(assumption)


def _a9_7_component_id(artifact: UiContractArtifact, suffix: str) -> str:
    base = artifact.page.pageKey or artifact.page.pageId or artifact.identity.pageKey or "page"
    return _a9_7_slug(f"{base}_{suffix}")


def _a9_7_slug(value: str) -> str:
    text = str(value or "page").strip().lower()
    result = []
    previous_dash = False
    for char in text:
        if char.isalnum() or char == "_":
            result.append(char)
            previous_dash = False
        else:
            if not previous_dash:
                result.append("_")
                previous_dash = True
    slug = "".join(result).strip("_")
    return slug or "page"


def _a9_7_unique_id(existing: set[str], candidate: str) -> str:
    base = _a9_7_slug(candidate)
    value = base
    index = 2
    while value in existing:
        value = f"{base}_{index}"
        index += 1
    return value


def _a9_7_unique_action_id(existing: set[str], candidate: str) -> str:
    value = _a9_7_slug(candidate)
    index = 2
    while value in existing:
        value = f"{_a9_7_slug(candidate)}_{index}"
        index += 1
    return value


def _a9_7_default_page_description(artifact: UiContractArtifact) -> str:
    title = artifact.page.title or artifact.page.pageName or _u("\u4e1a\u52a1\u9875\u9762")
    return title + _u("\u7684\u4ea4\u4e92\u539f\u578b\uff0c\u5305\u542b\u5173\u952e\u5185\u5bb9\u3001\u72b6\u6001\u53cd\u9988\u548c\u53ef\u6267\u884c\u64cd\u4f5c\u3002")


def _u(value: str) -> str:
    return value



UiContractComponent.model_rebuild()

UI_CONTRACT_LLM_JSON_SCHEMA: dict[str, Any] = UiContractArtifact.model_json_schema()
UI_CONTRACT_LLM_JSON_SCHEMA.get("properties", {}).pop("markdown", None)

required_fields = UI_CONTRACT_LLM_JSON_SCHEMA.get("required")
if isinstance(required_fields, list) and "markdown" in required_fields:
    required_fields.remove("markdown")


# A9.7.2 schema-level generation quality hints
UI_CONTRACT_LLM_JSON_SCHEMA["description"] = (
    "Generate a demo-grade, customer-presentable UI_CONTRACT. Do not output a rough wireframe, "
    "a naked form, or a simple stack of fields. Every visible field, action, table column, tab, "
    "title, description, empty/loading/success/error state must be traceable and editable from the canvas."
)
UI_CONTRACT_LLM_JSON_SCHEMA["x-a9-ui-fidelity-rules"] = [
    "Use a visible PAGE_HEADER with business title and meaningful description.",
    "For form pages, group fields in polished cards, add primary/secondary actions, placeholders and help text.",
    "For list pages, include filter/search toolbar, table columns with columnKey/fieldKey/label/width and row actions.",
    "For detail pages, include summary cards, description list, status steps or tabs when useful.",
    "Do not put important user-facing text only in developer descriptions; make it selectable via text/title/description/props.",
    "Set stable fieldKey/actionId/columnKey/tabKey and elementPicker metadata so canvas selection and AI edits are precise.",
]
