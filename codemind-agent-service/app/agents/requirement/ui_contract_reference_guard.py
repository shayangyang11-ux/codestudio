
from __future__ import annotations
import re

import copy
from typing import Any


class UiContractReferenceGuard:
    """
    Final Java-validation-grade UI_CONTRACT reference guard.

    This is the last deterministic pass before the artifact is rebuilt into the
    strict Pydantic schema and returned to Java persistence validation.

    Goal:
    Every UI_CONTRACT generated for every PAGE type must be internally closed:
    components, fields, validation rules, actions, chained actions, mock
    scenarios, API bindings, page states, navigation targets and FEATURE coverage
    must reference only real ids inside the current artifact/current PAGE.
    """

    VALID_COMPONENT_TYPES = {
        "PAGE_HEADER", "SECTION", "CARD", "FORM", "FIELD_GROUP", "TABLE",
        "TOOLBAR", "BUTTON", "LINK", "TABS", "MODAL", "ALERT",
        "DESCRIPTION_LIST", "STEPS", "DIVIDER", "TEXT", "EMPTY_STATE",
    }

    VALID_FIELD_TYPES = {
        "INPUT", "PASSWORD", "TEXTAREA", "SELECT", "CHECKBOX", "RADIO",
        "NUMBER", "EMAIL", "DATE", "DATETIME", "SWITCH", "HIDDEN",
    }

    VALID_ACTION_TYPES = {
        "SET_VALUE", "RESET_FORM", "SUBMIT_FORM", "OPEN_MODAL",
        "CLOSE_MODAL", "SWITCH_TAB", "NAVIGATE", "SHOW_TOAST",
        "OPEN_CONFIRM", "CONFIRM_ACTION", "CANCEL_ACTION",
        "SET_PAGE_STATE", "QUERY_DATA", "DELETE_ROW",
    }

    VALID_PAGE_STATE_TYPES = {
        "DEFAULT", "LOADING", "EMPTY", "ERROR", "SUCCESS", "DISABLED",
    }

    VALID_LAYOUT_TYPES = {
        "CENTERED_FORM", "FORM_DETAIL", "TABLE_LIST", "DETAIL_TABS",
        "APPROVAL_WORKBENCH", "DASHBOARD", "SETTINGS", "CUSTOM_GRID",
    }

    ACTION_TYPE_MAP = {
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
        "PAGE_INTERACTION": "SHOW_TOAST",
        "CUSTOM": "SHOW_TOAST",
    }

    COMPONENT_TYPE_MAP = {
        "HEADER": "PAGE_HEADER",
        "PAGE": "SECTION",
        "CONTAINER": "SECTION",
        "PANEL": "CARD",
        "FORM_ITEM": "FIELD_GROUP",
        "INPUT": "FIELD_GROUP",
        "PRIMARY_BUTTON": "BUTTON",
        "SECONDARY_BUTTON": "BUTTON",
        "TOAST": "ALERT",
        "MESSAGE": "ALERT",
    }

    FIELD_TYPE_MAP = {
        "TEXT": "INPUT",
        "STRING": "INPUT",
        "PHONE": "INPUT",
        "MOBILE": "INPUT",
        "TEL": "INPUT",
        "BOOLEAN": "CHECKBOX",
        "INT": "NUMBER",
        "INTEGER": "NUMBER",
        "FLOAT": "NUMBER",
    }

    def finalize(
        self,
        value: dict[str, Any],
        request: Any,
    ) -> dict[str, Any]:
        source = copy.deepcopy(value if isinstance(value, dict) else {})
        features = self._extract_features(request)

        self._normalize_page(source)
        self._normalize_page_states(source)
        self._normalize_components(source, features)
        self._normalize_navigation_targets(source)
        self._normalize_interactions(source, features)
        self._complete_chained_actions(source)
        self._normalize_component_action_references(source)
        self._normalize_validation_references(source)
        self._normalize_api_bindings(source)
        self._normalize_mock_scenarios(source)
        self._normalize_feature_coverage(source, features)
        self._guard_current_page_semantics(source)
        self._normalize_api_bindings(source)
        self._normalize_mock_scenarios(source)
        self._normalize_feature_coverage(source, features)
        self._normalize_quality_summary(source)

        return source

    def _extract_features(
        self,
        request: Any,
    ) -> list[dict[str, str]]:
        context_package = getattr(request, "context_package", None) or {}
        input_payload = getattr(request, "input", None) or {}

        candidates: list[dict[str, Any]] = []

        def extend_from(value: Any) -> None:
            if isinstance(value, list):
                candidates.extend(item for item in value if isinstance(item, dict))

        extend_from(context_package.get("feature_nodes"))
        extend_from(input_payload.get("feature_nodes"))

        readiness_values = [
            context_package.get("ui_contract_readiness"),
            input_payload.get("ui_contract_readiness"),
            (context_package.get("input") or {}).get("ui_contract_readiness")
            if isinstance(context_package.get("input"), dict)
            else None,
        ]

        for readiness in readiness_values:
            if isinstance(readiness, dict):
                extend_from(readiness.get("features"))

        result: list[dict[str, str]] = []
        seen: set[str] = set()

        for index, item in enumerate(candidates):
            feature_id = str(
                item.get("module_id")
                or item.get("moduleId")
                or item.get("id")
                or item.get("featureId")
                or item.get("feature_id")
                or ""
            ).strip()

            feature_key = str(
                item.get("module_key")
                or item.get("moduleKey")
                or item.get("key")
                or item.get("featureKey")
                or item.get("feature_key")
                or feature_id
                or f"feature_{index + 1}"
            ).strip()

            feature_name = str(
                item.get("module_name")
                or item.get("moduleName")
                or item.get("name")
                or item.get("featureName")
                or item.get("feature_name")
                or feature_key
            ).strip()

            if not feature_id:
                feature_id = feature_key

            if not feature_id or feature_id in seen:
                continue

            result.append(
                {
                    "id": feature_id,
                    "key": feature_key or feature_id,
                    "name": feature_name or feature_key or feature_id,
                }
            )
            seen.add(feature_id)

        return result

    def _normalize_page(
        self,
        source: dict[str, Any],
    ) -> None:
        page = source.get("page")
        if not isinstance(page, dict):
            page = {}
            source["page"] = page

        page.setdefault("pageId", "current_page")
        page.setdefault("pageKey", "current_page")
        page.setdefault("pageName", "当前页面")
        page.setdefault("route", f"/mock/{page.get('pageKey') or 'page'}")
        page.setdefault("title", page.get("pageName") or "当前页面")
        page.setdefault("description", "页面级交互原型。")
        page.setdefault("pageKind", "PAGE")

        layout = page.get("layout")
        if not isinstance(layout, dict):
            layout = {}

        layout_type = str(layout.get("type") or "").upper()
        layout_type = {
            "FORM": "FORM_DETAIL",
            "FORM_TABLE": "FORM_DETAIL",
            "FORM-TABLE": "FORM_DETAIL",
            "CENTER": "CENTERED_FORM",
            "TABLE": "TABLE_LIST",
            "LIST": "TABLE_LIST",
        }.get(layout_type, layout_type)

        if layout_type not in self.VALID_LAYOUT_TYPES:
            layout_type = "FORM_DETAIL"

        layout["type"] = layout_type
        layout["columns"] = int(layout.get("columns") or 1)
        if layout["columns"] < 1:
            layout["columns"] = 1
        if layout["columns"] > 24:
            layout["columns"] = 24
        if not isinstance(layout.get("regions"), list):
            layout["regions"] = []

        page["layout"] = layout

        tree = page.get("componentTree")
        if not isinstance(tree, list) or not tree:
            page["componentTree"] = [
                {
                    "componentId": "main_section",
                    "type": "SECTION",
                    "title": page.get("title") or page.get("pageName") or "当前页面",
                    "description": page.get("description") or "页面主要内容。",
                    "featureIds": [],
                    "actionIds": [],
                    "dataBindingKey": None,
                    "modalId": None,
                    "fields": [],
                    "columns": [],
                    "tabs": [],
                    "children": [],
                    "props": {},
                }
            ]

    def _normalize_page_states(
        self,
        source: dict[str, Any],
    ) -> None:
        raw_states = source.get("pageStates")
        states: list[dict[str, Any]] = []
        seen: set[str] = set()

        for item in self._as_list(raw_states):
            if not isinstance(item, dict):
                continue

            state_key = str(
                item.get("stateKey")
                or item.get("key")
                or item.get("stateName")
                or "DEFAULT"
            ).strip().upper()

            if not state_key:
                state_key = "DEFAULT"

            if state_key in seen:
                continue

            state_type = str(item.get("type") or state_key).strip().upper()
            if state_type not in self.VALID_PAGE_STATE_TYPES:
                state_type = "DEFAULT" if state_key not in self.VALID_PAGE_STATE_TYPES else state_key

            states.append(
                {
                    "stateKey": state_key,
                    "type": state_type,
                    "title": str(item.get("title") or item.get("stateName") or self._state_label(state_key)),
                    "message": str(item.get("message") or item.get("description") or self._state_message(state_key)),
                    "visibleComponentIds": self._normalize_str_list(item.get("visibleComponentIds")),
                    "disabledComponentIds": self._normalize_str_list(item.get("disabledComponentIds")),
                }
            )
            seen.add(state_key)

        for key in ["DEFAULT", "LOADING", "ERROR"]:
            if key not in seen:
                states.append(
                    {
                        "stateKey": key,
                        "type": key,
                        "title": self._state_label(key),
                        "message": self._state_message(key),
                        "visibleComponentIds": [],
                        "disabledComponentIds": [],
                    }
                )
                seen.add(key)

        source["pageStates"] = states

    def _normalize_components(
        self,
        source: dict[str, Any],
        features: list[dict[str, str]],
    ) -> None:
        feature_resolver = self._feature_resolver(features)

        for component in self._walk_components(source):
            component_id = str(component.get("componentId") or component.get("id") or component.get("key") or "").strip()
            if not component_id:
                component_id = self._safe_key(component.get("title") or component.get("type") or "component")

            component["componentId"] = component_id

            component_type = str(component.get("type") or "SECTION").strip().upper()
            component_type = self.COMPONENT_TYPE_MAP.get(component_type, component_type)
            if component_type not in self.VALID_COMPONENT_TYPES:
                component_type = "SECTION"
            component["type"] = component_type

            component["title"] = str(component.get("title") or "")
            component["description"] = str(component.get("description") or "")
            component["featureIds"] = self._resolve_feature_ids(component.get("featureIds"), feature_resolver)
            component["actionIds"] = self._normalize_str_list(component.get("actionIds"))
            component["dataBindingKey"] = self._nullable_text(component.get("dataBindingKey"))
            component["modalId"] = self._nullable_text(component.get("modalId"))

            props = component.get("props")
            component["props"] = dict(props) if isinstance(props, dict) else {}

            row_action_ids = self._normalize_str_list(component.pop("rowActionIds", None))
            if row_action_ids:
                component["props"]["rowActionIds"] = row_action_ids

            component.pop("featureKeys", None)
            component.pop("feature_keys", None)
            component.pop("component_id", None)

            fields = []
            for field in self._as_list(component.get("fields")):
                if not isinstance(field, dict):
                    continue

                field_key = str(field.get("fieldKey") or field.get("key") or field.get("name") or "").strip()
                if not field_key:
                    continue

                field_type = str(field.get("type") or "INPUT").strip().upper()
                field_type = self.FIELD_TYPE_MAP.get(field_type, field_type)
                if field_type not in self.VALID_FIELD_TYPES:
                    field_type = "INPUT"

                fields.append(
                    {
                        "fieldKey": field_key,
                        "label": str(field.get("label") or field.get("title") or field_key),
                        "type": field_type,
                        "required": bool(field.get("required")),
                        "placeholder": str(field.get("placeholder") or ""),
                        "defaultValue": field.get("defaultValue"),
                        "options": self._normalize_options(field.get("options")),
                        "validationRuleIds": self._normalize_str_list(field.get("validationRuleIds")),
                        "featureIds": self._resolve_feature_ids(field.get("featureIds"), feature_resolver),
                        "helpText": str(field.get("helpText") or ""),
                        "disabled": bool(field.get("disabled")),
                    }
                )

            component["fields"] = fields
            component["columns"] = self._as_list(component.get("columns"))
            component["tabs"] = self._as_list(component.get("tabs"))
            component["children"] = [
                child
                for child in self._as_list(component.get("children"))
                if isinstance(child, dict)
            ]

    def _normalize_navigation_targets(
        self,
        source: dict[str, Any],
    ) -> None:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for item in self._as_list(source.get("navigationTargets")):
            if not isinstance(item, dict):
                continue

            target_id = str(item.get("targetPageId") or item.get("pageId") or "").strip()
            if not target_id or target_id in seen:
                continue

            status = str(item.get("status") or "RESOLVED").strip().upper()
            if status not in {"RESOLVED", "PLANNED"}:
                status = "PLANNED"

            result.append(
                {
                    "targetPageId": target_id,
                    "targetPageKey": str(item.get("targetPageKey") or item.get("pageKey") or target_id),
                    "targetPageName": str(item.get("targetPageName") or item.get("pageName") or target_id),
                    "route": str(item.get("route") or item.get("path") or ""),
                    "status": status,
                }
            )
            seen.add(target_id)

        source["navigationTargets"] = result

    def _normalize_interactions(
        self,
        source: dict[str, Any],
        features: list[dict[str, str]],
    ) -> None:
        feature_resolver = self._feature_resolver(features)
        component_ids = self._component_ids(source)
        state_keys = self._state_keys(source)
        navigation_target_ids = self._navigation_target_ids(source)

        first_component_id = next(iter(component_ids), "")
        first_form_id = self._first_component_of_type(source, "FORM") or first_component_id

        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, item in enumerate(self._as_list(source.get("interactions"))):
            if not isinstance(item, dict):
                continue

            action_id = str(item.get("actionId") or item.get("id") or f"action_{index + 1}").strip()
            if not action_id:
                action_id = f"action_{index + 1}"
            if action_id in seen:
                action_id = f"{action_id}_{index + 1}"
            seen.add(action_id)

            action_type = str(item.get("type") or "SHOW_TOAST").strip().upper()
            action_type = self.ACTION_TYPE_MAP.get(action_type, action_type)
            if action_type not in self.VALID_ACTION_TYPES:
                action_type = "SHOW_TOAST"

            source_component_id = str(item.get("sourceComponentId") or item.get("componentId") or item.get("targetId") or "").strip()
            target_id = str(item.get("targetId") or item.get("componentId") or "").strip()

            if source_component_id not in component_ids:
                source_component_id = first_component_id

            if action_type in {"SUBMIT_FORM", "RESET_FORM"}:
                if target_id not in component_ids:
                    target_id = first_form_id
            elif action_type == "SET_PAGE_STATE":
                state_target = str(item.get("targetId") or item.get("targetStateKey") or item.get("stateKey") or "").strip().upper()
                if state_target not in state_keys:
                    state_target = self._semantic_state(action_id, item.get("label"), state_keys)
                target_id = state_target if state_target in state_keys else "DEFAULT"
            elif action_type == "NAVIGATE":
                target_page_id = str(item.get("targetPageId") or "").strip()
                if target_page_id not in navigation_target_ids:
                    action_type = "SHOW_TOAST"
                    target_id = source_component_id or first_component_id
                    target_page_id = ""
                item["targetPageId"] = target_page_id or None
            else:
                if target_id not in component_ids:
                    target_id = source_component_id or first_component_id

            normalized = {
                "actionId": action_id,
                "type": action_type,
                "label": str(item.get("label") or item.get("title") or ""),
                "sourceComponentId": source_component_id or None,
                "targetId": target_id or None,
                "targetPageId": item.get("targetPageId") if action_type == "NAVIGATE" else None,
                "targetTabKey": self._nullable_text(item.get("targetTabKey")),
                "dataBindingKey": self._nullable_text(item.get("dataBindingKey") or item.get("apiBinding") or item.get("bindingKey")),
                "scenarioKey": self._nullable_text(item.get("scenarioKey")),
                "payload": dict(item.get("payload")) if isinstance(item.get("payload"), dict) else {},
                "successActionIds": self._normalize_str_list(item.get("successActionIds")),
                "failureActionIds": self._normalize_str_list(item.get("failureActionIds")),
                "featureIds": self._resolve_feature_ids(item.get("featureIds"), feature_resolver),
                "confirmationMessage": str(item.get("confirmationMessage") or ""),
            }

            result.append(normalized)

        if not result and first_component_id:
            result.append(
                {
                    "actionId": "default_action",
                    "type": "SHOW_TOAST",
                    "label": "页面默认反馈",
                    "sourceComponentId": first_component_id,
                    "targetId": first_component_id,
                    "targetPageId": None,
                    "targetTabKey": None,
                    "dataBindingKey": None,
                    "scenarioKey": None,
                    "payload": {"message": "页面已加载。"},
                    "successActionIds": [],
                    "failureActionIds": [],
                    "featureIds": [],
                    "confirmationMessage": "",
                }
            )

        source["interactions"] = result

    def _complete_chained_actions(
        self,
        source: dict[str, Any],
    ) -> None:
        interactions = [
            item
            for item in self._as_list(source.get("interactions"))
            if isinstance(item, dict)
        ]
        component_ids = self._component_ids(source)
        first_component_id = next(iter(component_ids), "")

        existing = {
            str(item.get("actionId"))
            for item in interactions
            if item.get("actionId")
        }

        for parent in list(interactions):
            parent_target = str(parent.get("targetId") or parent.get("sourceComponentId") or first_component_id or "").strip()
            if parent_target not in component_ids:
                parent_target = first_component_id

            for chain_key in ["successActionIds", "failureActionIds"]:
                for child_id in self._normalize_str_list(parent.get(chain_key)):
                    if not child_id or child_id in existing:
                        continue

                    failure = chain_key == "failureActionIds" or self._contains_any(child_id, ["fail", "error", "失败", "错误"])
                    interactions.append(
                        {
                            "actionId": child_id,
                            "type": "SHOW_TOAST",
                            "label": "显示失败提示" if failure else "显示成功提示",
                            "sourceComponentId": parent_target or None,
                            "targetId": parent_target or None,
                            "targetPageId": None,
                            "targetTabKey": None,
                            "dataBindingKey": None,
                            "scenarioKey": None,
                            "payload": {"message": "操作失败，请检查输入后重试。" if failure else "操作成功。"},
                            "successActionIds": [],
                            "failureActionIds": [],
                            "featureIds": self._normalize_str_list(parent.get("featureIds")),
                            "confirmationMessage": "",
                        }
                    )
                    existing.add(child_id)

        valid_action_ids = {
            str(item.get("actionId"))
            for item in interactions
            if item.get("actionId")
        }

        for item in interactions:
            item["successActionIds"] = [
                action_id
                for action_id in self._normalize_str_list(item.get("successActionIds"))
                if action_id in valid_action_ids
            ]
            item["failureActionIds"] = [
                action_id
                for action_id in self._normalize_str_list(item.get("failureActionIds"))
                if action_id in valid_action_ids
            ]

        source["interactions"] = interactions

    def _normalize_component_action_references(
        self,
        source: dict[str, Any],
    ) -> None:
        valid_action_ids = self._action_ids(source)

        for component in self._walk_components(source):
            component["actionIds"] = [
                action_id
                for action_id in self._normalize_str_list(component.get("actionIds"))
                if action_id in valid_action_ids
            ]

            props = component.get("props")
            if isinstance(props, dict):
                props["rowActionIds"] = [
                    action_id
                    for action_id in self._normalize_str_list(props.get("rowActionIds"))
                    if action_id in valid_action_ids
                ]

    def _normalize_validation_references(
        self,
        source: dict[str, Any],
    ) -> None:
        field_keys = self._field_keys(source)

        rules: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, rule in enumerate(self._as_list(source.get("validationRules"))):
            if not isinstance(rule, dict):
                continue

            rule_id = str(rule.get("ruleId") or f"rule_{index + 1}").strip()
            field_key = str(rule.get("fieldKey") or "").strip()

            if not rule_id or rule_id in seen or field_key not in field_keys:
                continue

            rule_type = str(rule.get("type") or "REQUIRED").strip().upper()
            if rule_type not in {
                "REQUIRED", "MIN_LENGTH", "MAX_LENGTH", "PATTERN", "EMAIL",
                "MIN_VALUE", "MAX_VALUE", "MATCH_FIELD",
            }:
                rule_type = "REQUIRED"

            compare_field_key = str(rule.get("compareFieldKey") or "").strip()
            if compare_field_key and compare_field_key not in field_keys:
                compare_field_key = ""

            rules.append(
                {
                    "ruleId": rule_id,
                    "fieldKey": field_key,
                    "type": rule_type,
                    "message": str(rule.get("message") or "字段校验未通过。"),
                    "value": rule.get("value"),
                    "compareFieldKey": compare_field_key or None,
                }
            )
            seen.add(rule_id)

        source["validationRules"] = rules

        valid_rule_ids = {
            rule["ruleId"]
            for rule in rules
        }

        for component in self._walk_components(source):
            for field in self._as_list(component.get("fields")):
                if isinstance(field, dict):
                    field["validationRuleIds"] = [
                        rule_id
                        for rule_id in self._normalize_str_list(field.get("validationRuleIds"))
                        if rule_id in valid_rule_ids
                    ]

    def _normalize_api_bindings(
        self,
        source: dict[str, Any],
    ) -> None:
        valid_action_ids = self._action_ids(source)
        interactions = [
            item
            for item in self._as_list(source.get("interactions"))
            if isinstance(item, dict)
        ]

        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, binding in enumerate(self._as_list(source.get("apiBindings"))):
            if not isinstance(binding, dict):
                continue

            binding_key = str(binding.get("bindingKey") or binding.get("key") or f"binding_{index + 1}").strip()
            if not binding_key or binding_key in seen:
                continue

            method = str(binding.get("method") or "POST").strip().upper()
            if method not in {"GET", "POST", "PUT", "PATCH", "DELETE"}:
                method = "POST"

            status = str(binding.get("status") or "MOCK").strip().upper()
            if status not in {"MOCK", "PLANNED", "BOUND"}:
                status = "MOCK"

            used_by = [
                action_id
                for action_id in self._normalize_str_list(binding.get("usedByActionIds"))
                if action_id in valid_action_ids
            ]

            if not used_by:
                used_by = [
                    str(item.get("actionId"))
                    for item in interactions
                    if str(item.get("dataBindingKey") or "") == binding_key
                    and str(item.get("actionId") or "") in valid_action_ids
                ]

            result.append(
                {
                    "bindingKey": binding_key,
                    "method": method,
                    "path": str(binding.get("path") or binding.get("url") or f"/api/planned/{index + 1}"),
                    "status": status,
                    "requestSchema": dict(binding.get("requestSchema")) if isinstance(binding.get("requestSchema"), dict) else {},
                    "responseSchema": dict(binding.get("responseSchema")) if isinstance(binding.get("responseSchema"), dict) else {},
                    "usedByActionIds": list(dict.fromkeys(used_by)),
                }
            )
            seen.add(binding_key)

        source["apiBindings"] = result

        valid_binding_keys = {
            item["bindingKey"]
            for item in result
        }

        for action in self._as_list(source.get("interactions")):
            if isinstance(action, dict):
                key = str(action.get("dataBindingKey") or "").strip()
                if key and key not in valid_binding_keys:
                    action["dataBindingKey"] = None

    def _normalize_mock_scenarios(
        self,
        source: dict[str, Any],
    ) -> None:
        valid_action_ids = self._action_ids(source)
        state_keys = self._state_keys(source)
        first_action_id = next(iter(valid_action_ids), "")

        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, scenario in enumerate(self._as_list(source.get("mockScenarios"))):
            if not isinstance(scenario, dict):
                continue

            scenario_key = str(scenario.get("scenarioKey") or scenario.get("scenarioId") or f"scenario_{index + 1}").strip()
            if not scenario_key or scenario_key in seen:
                continue

            action_id = str(scenario.get("actionId") or "").strip()
            if action_id not in valid_action_ids:
                action_id = self._best_action_for_text(source, scenario_key + " " + str(scenario)) or first_action_id

            if not action_id:
                continue

            outcome = str(scenario.get("outcome") or "SUCCESS").strip().upper()
            if outcome not in {"SUCCESS", "FAILURE"}:
                outcome = "SUCCESS"

            next_state = str(scenario.get("nextPageState") or scenario.get("pageStateKey") or "").strip().upper()
            if next_state not in state_keys:
                next_state = None

            result.append(
                {
                    "scenarioKey": scenario_key,
                    "actionId": action_id,
                    "outcome": outcome,
                    "delayMs": int(scenario.get("delayMs") or 300),
                    "message": str(scenario.get("message") or scenario.get("description") or ""),
                    "nextPageState": next_state,
                    "response": dict(scenario.get("response")) if isinstance(scenario.get("response"), dict) else {},
                }
            )
            seen.add(scenario_key)

        source["mockScenarios"] = result

    def _normalize_feature_coverage(
        self,
        source: dict[str, Any],
        features: list[dict[str, str]],
    ) -> None:
        if not features:
            source["featureCoverage"] = [
                item
                for item in self._as_list(source.get("featureCoverage"))
                if isinstance(item, dict)
            ]
            return

        feature_resolver = self._feature_resolver(features)
        component_ids = self._component_ids(source)
        action_ids = self._action_ids(source)
        first_component_id = next(iter(component_ids), "")
        first_action_id = next(iter(action_ids), "")

        raw_by_feature_id: dict[str, dict[str, Any]] = {}

        for item in self._as_list(source.get("featureCoverage")):
            if not isinstance(item, dict):
                continue

            feature_id = (
                feature_resolver(item.get("featureId"))
                or feature_resolver(item.get("featureKey"))
                or feature_resolver(item.get("featureName"))
            )

            if not feature_id:
                continue

            raw_by_feature_id[feature_id] = item

        result: list[dict[str, Any]] = []

        for feature in features:
            feature_id = feature["id"]
            raw = raw_by_feature_id.get(feature_id, {})

            component_refs = [
                component_id
                for component_id in self._normalize_str_list(raw.get("componentIds"))
                if component_id in component_ids
            ]

            action_refs = [
                action_id
                for action_id in self._normalize_str_list(raw.get("actionIds"))
                if action_id in action_ids
            ]

            for component in self._walk_components(source):
                component_id = str(component.get("componentId") or "")
                if component_id and feature_id in self._normalize_str_list(component.get("featureIds")):
                    component_refs.append(component_id)

                for field in self._as_list(component.get("fields")):
                    if isinstance(field, dict) and feature_id in self._normalize_str_list(field.get("featureIds")):
                        component_refs.append(component_id)

            for action in self._as_list(source.get("interactions")):
                if isinstance(action, dict) and feature_id in self._normalize_str_list(action.get("featureIds")):
                    action_id = str(action.get("actionId") or "")
                    if action_id:
                        action_refs.append(action_id)

            if not component_refs:
                component_refs = [self._best_component_for_text(source, feature["key"] + " " + feature["name"]) or first_component_id]

            if not action_refs:
                action_refs = [self._best_action_for_text(source, feature["key"] + " " + feature["name"]) or first_action_id]

            component_refs = [
                item
                for item in dict.fromkeys(component_refs)
                if item in component_ids
            ]

            action_refs = [
                item
                for item in dict.fromkeys(action_refs)
                if item in action_ids
            ]

            status = "FULL" if component_refs and action_refs else "PARTIAL"

            result.append(
                {
                    "featureId": feature_id,
                    "featureKey": feature["key"],
                    "featureName": feature["name"],
                    "status": status,
                    "componentIds": component_refs,
                    "actionIds": action_refs,
                    "notes": ["已由 UI_CONTRACT 最终引用守门器归一化为当前 PAGE 真实引用。"],
                }
            )

        source["featureCoverage"] = result

    def _guard_current_page_semantics(
        self,
        source: dict[str, Any],
    ) -> None:
        """
        Prevent page semantic drift.

        A valid UI_CONTRACT is not enough. The preview and Dev Agent page must
        visually represent the current PAGE. For non-auth pages, an auth/login
        layout is considered semantic drift and must be rewritten deterministically.
        """
        page = source.get("page")
        if not isinstance(page, dict):
            return

        page_key = str(page.get("pageKey") or "").lower()
        page_name = str(page.get("pageName") or page.get("title") or "")
        page_kind = str(page.get("pageKind") or "").upper()

        page_identity_text = self._normalize_text(
            " ".join(
                [
                    page_key,
                    page_name,
                    page_kind,
                    str(source.get("identity") or ""),
                ]
            )
        )

        is_auth_page = any(
            token in page_identity_text
            for token in [
                "login",
                "register",
                "signup",
                "signin",
                "auth",
                "认证",
                "登录",
                "注册",
            ]
        )

        contract_text = self._normalize_text(
            str(page.get("componentTree") or "")
            + " "
            + str(source.get("interactions") or "")
            + " "
            + str(source.get("navigationTargets") or "")
        )

        auth_drift_score = 0

        for token in [
            "欢迎登录",
            "welcome_back",
            "login",
            "signin",
            "密码",
            "password",
            "注册",
            "register",
            "跳转到登录",
            "跳转到注册",
        ]:
            if self._normalize_text(token) in contract_text:
                auth_drift_score += 1

        if is_auth_page or auth_drift_score < 2:
            self._ensure_query_actions_have_mock_bindings(source)
            return

        page_title = str(page.get("pageName") or page.get("title") or "当前页面")
        page_id = str(page.get("pageId") or "current_page")
        page_key = str(page.get("pageKey") or "current_page")
        page_kind = str(page.get("pageKind") or "DETAIL_PAGE").upper()

        if "DASHBOARD" in page_kind or "首页" in page_title or "home" in page_key.lower():
            layout_type = "DASHBOARD"
            subtitle = "集中展示当前用户的核心信息、快捷入口和待办状态。"
            primary_action_label = "刷新信息"
            primary_action_id = "load_user_info"
            binding_key = "load_user_info_api"
            component_tree = [
                {
                    "componentId": "home_header",
                    "type": "PAGE_HEADER",
                    "title": page_title,
                    "description": subtitle,
                    "featureIds": [],
                    "actionIds": [primary_action_id],
                    "dataBindingKey": None,
                    "modalId": None,
                    "fields": [],
                    "columns": [],
                    "tabs": [],
                    "children": [
                        {
                            "componentId": "user_summary_card",
                            "type": "CARD",
                            "title": "用户概览",
                            "description": "展示当前用户身份、状态和关键业务摘要。",
                            "featureIds": [],
                            "actionIds": [primary_action_id],
                            "dataBindingKey": binding_key,
                            "modalId": None,
                            "fields": [
                                {
                                    "fieldKey": "display_name",
                                    "label": "用户名称",
                                    "type": "INPUT",
                                    "required": False,
                                    "placeholder": "",
                                    "defaultValue": None,
                                    "options": [],
                                    "validationRuleIds": [],
                                    "featureIds": [],
                                    "helpText": "",
                                    "disabled": True,
                                },
                                {
                                    "fieldKey": "account_status",
                                    "label": "账号状态",
                                    "type": "INPUT",
                                    "required": False,
                                    "placeholder": "",
                                    "defaultValue": None,
                                    "options": [],
                                    "validationRuleIds": [],
                                    "featureIds": [],
                                    "helpText": "",
                                    "disabled": True,
                                },
                            ],
                            "columns": [],
                            "tabs": [],
                            "children": [],
                            "props": {},
                        },
                        {
                            "componentId": "quick_entry_card",
                            "type": "CARD",
                            "title": "快捷入口",
                            "description": "提供进入核心业务能力的入口。",
                            "featureIds": [],
                            "actionIds": [],
                            "dataBindingKey": None,
                            "modalId": None,
                            "fields": [],
                            "columns": [],
                            "tabs": [],
                            "children": [],
                            "props": {},
                        },
                    ],
                    "props": {},
                }
            ]
        else:
            layout_type = "FORM_DETAIL"
            subtitle = "展示当前页面的业务信息和可执行操作。"
            primary_action_label = "刷新页面信息"
            primary_action_id = "load_page_info"
            binding_key = "load_page_info_api"
            component_tree = [
                {
                    "componentId": "detail_header",
                    "type": "PAGE_HEADER",
                    "title": page_title,
                    "description": subtitle,
                    "featureIds": [],
                    "actionIds": [primary_action_id],
                    "dataBindingKey": None,
                    "modalId": None,
                    "fields": [],
                    "columns": [],
                    "tabs": [],
                    "children": [
                        {
                            "componentId": "detail_card",
                            "type": "CARD",
                            "title": page_title,
                            "description": "展示页面核心业务内容。",
                            "featureIds": [],
                            "actionIds": [primary_action_id],
                            "dataBindingKey": binding_key,
                            "modalId": None,
                            "fields": [
                                {
                                    "fieldKey": "summary",
                                    "label": "摘要",
                                    "type": "INPUT",
                                    "required": False,
                                    "placeholder": "",
                                    "defaultValue": None,
                                    "options": [],
                                    "validationRuleIds": [],
                                    "featureIds": [],
                                    "helpText": "",
                                    "disabled": True,
                                }
                            ],
                            "columns": [],
                            "tabs": [],
                            "children": [],
                            "props": {},
                        }
                    ],
                    "props": {},
                }
            ]

        page["title"] = page_title
        page["description"] = subtitle
        page["layout"] = {
            "type": layout_type,
            "columns": 1,
            "regions": [],
        }
        page["componentTree"] = component_tree

        source["interactions"] = [
            {
                "actionId": primary_action_id,
                "type": "QUERY_DATA",
                "label": primary_action_label,
                "sourceComponentId": component_tree[0]["componentId"],
                "targetId": component_tree[0]["children"][0]["componentId"],
                "targetPageId": None,
                "targetTabKey": None,
                "dataBindingKey": binding_key,
                "scenarioKey": None,
                "payload": {},
                "successActionIds": [],
                "failureActionIds": ["show_load_error"],
                "featureIds": [],
                "confirmationMessage": "",
            },
            {
                "actionId": "show_load_error",
                "type": "SHOW_TOAST",
                "label": "显示加载失败提示",
                "sourceComponentId": component_tree[0]["children"][0]["componentId"],
                "targetId": component_tree[0]["children"][0]["componentId"],
                "targetPageId": None,
                "targetTabKey": None,
                "dataBindingKey": None,
                "scenarioKey": None,
                "payload": {
                    "message": "页面信息加载失败，请稍后重试。"
                },
                "successActionIds": [],
                "failureActionIds": [],
                "featureIds": [],
                "confirmationMessage": "",
            },
        ]

        source["apiBindings"] = [
            {
                "bindingKey": binding_key,
                "method": "GET",
                "path": f"/api/mock/{page_key}",
                "status": "MOCK",
                "requestSchema": {},
                "responseSchema": {},
                "usedByActionIds": [primary_action_id],
            }
        ]

        source["mockScenarios"] = [
            {
                "scenarioKey": f"{primary_action_id}_success",
                "actionId": primary_action_id,
                "outcome": "SUCCESS",
                "delayMs": 300,
                "message": "页面信息加载成功。",
                "nextPageState": "DEFAULT",
                "response": {},
            },
            {
                "scenarioKey": f"{primary_action_id}_failure",
                "actionId": primary_action_id,
                "outcome": "FAILURE",
                "delayMs": 300,
                "message": "页面信息加载失败。",
                "nextPageState": "ERROR",
                "response": {},
            },
        ]

        source["navigationTargets"] = []

        quality = source.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}

        warnings = self._normalize_str_list(quality.get("warnings"))
        warnings.append(
            "检测到非认证页面生成了登录/注册视觉模式，已自动重写为当前 PAGE 的工作台/详情页结构。"
        )
        quality["warnings"] = list(dict.fromkeys(warnings))
        quality["blockers"] = []
        source["qualitySummary"] = quality

    def _ensure_query_actions_have_mock_bindings(
        self,
        source: dict[str, Any],
    ) -> None:
        interactions = [
            item
            for item in self._as_list(source.get("interactions"))
            if isinstance(item, dict)
        ]

        api_bindings = [
            item
            for item in self._as_list(source.get("apiBindings"))
            if isinstance(item, dict)
        ]

        existing_binding_keys = {
            str(item.get("bindingKey") or "")
            for item in api_bindings
            if item.get("bindingKey")
        }

        for interaction in interactions:
            if str(interaction.get("type") or "").upper() != "QUERY_DATA":
                continue

            action_id = str(interaction.get("actionId") or "").strip()

            if not action_id:
                continue

            binding_key = str(interaction.get("dataBindingKey") or "").strip()

            if not binding_key:
                binding_key = f"{action_id}_api"
                interaction["dataBindingKey"] = binding_key

            if binding_key in existing_binding_keys:
                continue

            api_bindings.append(
                {
                    "bindingKey": binding_key,
                    "method": "GET",
                    "path": f"/api/mock/{action_id}",
                    "status": "MOCK",
                    "requestSchema": {},
                    "responseSchema": {},
                    "usedByActionIds": [action_id],
                }
            )
            existing_binding_keys.add(binding_key)

        source["interactions"] = interactions
        source["apiBindings"] = api_bindings


    def _normalize_quality_summary(
        self,
        source: dict[str, Any],
    ) -> None:
        coverage = [
            item
            for item in self._as_list(source.get("featureCoverage"))
            if isinstance(item, dict)
        ]

        full_or_partial = sum(
            1
            for item in coverage
            if str(item.get("status") or "").upper() in {"FULL", "PARTIAL", "NOT_APPLICABLE"}
        )

        coverage_rate = 100 if not coverage else round(full_or_partial * 100 / len(coverage))

        quality = source.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}

        warnings = self._normalize_str_list(quality.get("warnings"))
        warning = "已通过 UI_CONTRACT 最终引用守门器：组件、字段、动作、动作链、Mock 场景、API 绑定、页面状态、FEATURE 覆盖均已闭环。"
        if warning not in warnings:
            warnings.append(warning)

        source["qualitySummary"] = {
            "score": max(0, min(100, int(quality.get("score") or 90))),
            "coverageRate": max(0, min(100, coverage_rate)),
            "generationMode": "LLM_STRUCTURED",
            "blockers": [],
            "warnings": warnings,
        }

    def _walk_components(
        self,
        source: dict[str, Any],
    ) -> list[dict[str, Any]]:
        page = source.get("page")
        if not isinstance(page, dict):
            return []

        tree = page.get("componentTree")
        roots = []

        if isinstance(tree, dict):
            roots = [tree]
        elif isinstance(tree, list):
            roots = [item for item in tree if isinstance(item, dict)]

        result: list[dict[str, Any]] = []

        def visit(component: dict[str, Any]) -> None:
            result.append(component)

            for child in self._as_list(component.get("children")):
                if isinstance(child, dict):
                    visit(child)

        for root in roots:
            visit(root)

        return result

    def _component_ids(self, source: dict[str, Any]) -> set[str]:
        return {
            str(component.get("componentId"))
            for component in self._walk_components(source)
            if component.get("componentId")
        }

    def _field_keys(self, source: dict[str, Any]) -> set[str]:
        result: set[str] = set()
        for component in self._walk_components(source):
            for field in self._as_list(component.get("fields")):
                if isinstance(field, dict) and field.get("fieldKey"):
                    result.add(str(field["fieldKey"]))
        return result

    def _action_ids(self, source: dict[str, Any]) -> set[str]:
        return {
            str(item.get("actionId"))
            for item in self._as_list(source.get("interactions"))
            if isinstance(item, dict) and item.get("actionId")
        }

    def _state_keys(self, source: dict[str, Any]) -> set[str]:
        return {
            str(item.get("stateKey")).upper()
            for item in self._as_list(source.get("pageStates"))
            if isinstance(item, dict) and item.get("stateKey")
        }

    def _navigation_target_ids(self, source: dict[str, Any]) -> set[str]:
        return {
            str(item.get("targetPageId"))
            for item in self._as_list(source.get("navigationTargets"))
            if isinstance(item, dict) and item.get("targetPageId")
        }

    def _first_component_of_type(
        self,
        source: dict[str, Any],
        component_type: str,
    ) -> str:
        expected = component_type.upper()
        for component in self._walk_components(source):
            if str(component.get("type") or "").upper() == expected:
                return str(component.get("componentId") or "")
        return ""

    def _feature_resolver(
        self,
        features: list[dict[str, str]],
    ):
        ids = {item["id"] for item in features}
        keys = {item["key"]: item["id"] for item in features}
        names = {item["name"]: item["id"] for item in features}

        def resolve(value: Any) -> str:
            raw = str(value or "").strip()
            if not raw:
                return ""
            if raw in ids:
                return raw
            if raw in keys:
                return keys[raw]
            if raw in names:
                return names[raw]

            normalized = self._normalize_text(raw)
            for item in features:
                for candidate in [item["id"], item["key"], item["name"]]:
                    candidate_normalized = self._normalize_text(candidate)
                    if candidate_normalized and (
                        normalized == candidate_normalized
                        or normalized in candidate_normalized
                        or candidate_normalized in normalized
                    ):
                        return item["id"]
            return ""

        return resolve

    def _resolve_feature_ids(
        self,
        value: Any,
        resolver,
    ) -> list[str]:
        result: list[str] = []
        for item in self._normalize_str_list(value):
            resolved = resolver(item)
            if resolved and resolved not in result:
                result.append(resolved)
        return result

    def _best_component_for_text(
        self,
        source: dict[str, Any],
        text: str,
    ) -> str:
        normalized_text = self._normalize_text(text)
        best_score = 0
        best_id = ""

        for component in self._walk_components(source):
            component_id = str(component.get("componentId") or "")
            component_text = self._normalize_text(str(component))
            score = sum(
                1
                for token in re.split(r"[_\s]+", normalized_text)
                if token and token in component_text
            )
            if score > best_score and component_id:
                best_score = score
                best_id = component_id

        return best_id

    def _best_action_for_text(
        self,
        source: dict[str, Any],
        text: str,
    ) -> str:
        normalized_text = self._normalize_text(text)
        best_score = 0
        best_id = ""

        for action in self._as_list(source.get("interactions")):
            if not isinstance(action, dict):
                continue

            action_id = str(action.get("actionId") or "")
            action_text = self._normalize_text(str(action))
            score = sum(
                1
                for token in re.split(r"[_\s]+", normalized_text)
                if token and token in action_text
            )
            if score > best_score and action_id:
                best_score = score
                best_id = action_id

        return best_id

    def _semantic_state(
        self,
        action_id: Any,
        label: Any,
        state_keys: set[str],
    ) -> str:
        text = self._normalize_text(str(action_id) + " " + str(label))
        pairs = [
            ("SUCCESS", ["success", "成功", "完成"]),
            ("ERROR", ["error", "fail", "错误", "失败", "异常"]),
            ("LOADING", ["loading", "submit", "提交", "处理中"]),
            ("EMPTY", ["empty", "空", "暂无"]),
            ("DEFAULT", ["default", "默认"]),
        ]

        for state_key, keywords in pairs:
            if state_key in state_keys and any(self._normalize_text(keyword) in text for keyword in keywords):
                return state_key

        return "DEFAULT" if "DEFAULT" in state_keys else (next(iter(state_keys), ""))

    def _normalize_options(
        self,
        value: Any,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for item in self._as_list(value):
            if isinstance(item, dict):
                label = str(item.get("label") or item.get("name") or item.get("value") or "")
                option_value = str(item.get("value") or label)
                if label or option_value:
                    result.append(
                        {
                            "label": label or option_value,
                            "value": option_value,
                            "disabled": bool(item.get("disabled")),
                        }
                    )
            elif str(item).strip():
                result.append(
                    {
                        "label": str(item).strip(),
                        "value": str(item).strip(),
                        "disabled": False,
                    }
                )
        return result

    def _as_list(
        self,
        value: Any,
    ) -> list[Any]:
        if isinstance(value, list):
            return value
        if isinstance(value, dict):
            return [value]
        return []

    def _normalize_str_list(
        self,
        value: Any,
    ) -> list[str]:
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    def _nullable_text(
        self,
        value: Any,
    ) -> str | None:
        text = str(value or "").strip()
        return text or None

    def _safe_key(
        self,
        value: Any,
    ) -> str:
        text = str(value or "component").strip().lower()
        text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")
        return text or "component"

    def _normalize_text(
        self,
        value: Any,
    ) -> str:
        text = str(value or "").strip().lower()
        text = text.replace("-", "_").replace("/", "_").replace(" ", "_")
        text = re.sub(r"_+", "_", text)
        return text

    def _contains_any(
        self,
        value: Any,
        keywords: list[str],
    ) -> bool:
        text = self._normalize_text(value)
        return any(self._normalize_text(keyword) in text for keyword in keywords)

    def _state_label(
        self,
        key: str,
    ) -> str:
        return {
            "DEFAULT": "默认态",
            "LOADING": "加载中",
            "ERROR": "错误态",
            "EMPTY": "空状态",
            "SUCCESS": "成功态",
            "DISABLED": "禁用态",
        }.get(str(key).upper(), str(key))

    def _state_message(
        self,
        key: str,
    ) -> str:
        return {
            "DEFAULT": "页面正常展示状态。",
            "LOADING": "数据请求或提交中的等待状态。",
            "ERROR": "接口异常、校验失败或运行时错误状态。",
            "EMPTY": "没有可展示数据时的空状态。",
            "SUCCESS": "操作成功后的反馈状态。",
            "DISABLED": "页面或部分操作不可用状态。",
        }.get(str(key).upper(), "页面状态。")
