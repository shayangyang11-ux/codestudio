from __future__ import annotations

import re
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.ui_contract import UiContractArtifact


class UiContractGenerationHardener:
    """
    Post-process UI_CONTRACT output so the generated artifact is not only renderable,
    but also ready for quality inspection and downstream Development Agent handoff.

    This hardener does not replace the LLM. It repairs and enriches structured fields
    that are easy for the LLM to omit:
    - featureCoverage
    - pageStates
    - mockScenarios
    - interactions
    - apiBindings
    - mockData
    - developmentRules
    - qualitySummary
    """

    DEFAULT_PAGE_STATES = [
        {
            "stateKey": "DEFAULT",
            "stateName": "默认态",
            "description": "页面正常展示状态。",
        },
        {
            "stateKey": "LOADING",
            "stateName": "加载中",
            "description": "数据请求或提交中的等待状态。",
        },
        {
            "stateKey": "ERROR",
            "stateName": "错误态",
            "description": "接口异常、校验失败或运行时错误状态。",
        },
    ]

    FEATURE_KIND_ACTION_TYPE = {
        "query": "QUERY",
        "search": "QUERY",
        "list": "QUERY",
        "create": "SUBMIT",
        "add": "SUBMIT",
        "update": "SUBMIT",
        "edit": "SUBMIT",
        "delete": "DELETE",
        "remove": "DELETE",
        "submit": "SUBMIT",
        "approve": "APPROVE",
        "reject": "REJECT",
        "export": "EXPORT",
        "import": "IMPORT",
        "navigate": "NAVIGATE",
        "validate": "VALIDATE",
    }

    FEATURE_KIND_METHOD = {
        "query": "GET",
        "search": "GET",
        "list": "GET",
        "create": "POST",
        "add": "POST",
        "update": "PUT",
        "edit": "PUT",
        "delete": "DELETE",
        "remove": "DELETE",
        "submit": "POST",
        "approve": "POST",
        "reject": "POST",
        "export": "GET",
        "import": "POST",
        "navigate": "GET",
        "validate": "POST",
    }

    def harden(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        source = self._to_dict(artifact)
        context_package = request.context_package or {}
        current_page = self._extract_current_page(
            request,
            context_package,
            source,
        )
        feature_nodes = self._extract_list(
            context_package,
            "feature_nodes",
        )
        navigation_candidates = self._extract_list(
            context_package,
            "navigation_candidates",
        )
        artifacts = self._normalize_artifacts(
            context_package.get("artifacts"),
        )

        pages = self._resolve_pages(source, current_page)
        page = self._resolve_page(source, pages, current_page)
        components = self._collect_components_from_contract(source, page, pages)
        page_states = self._harden_page_states(
            source,
            page,
            feature_nodes,
        )
        mock_data = self._harden_mock_data(
            source.get("mockData") or source.get("mock_data"),
            page,
            components,
            feature_nodes,
            artifacts,
        )
        api_bindings = self._harden_api_bindings(
            source.get("apiBindings") or source.get("api_bindings"),
            feature_nodes,
            current_page,
            artifacts,
        )
        interactions = self._harden_interactions(
            source.get("interactions"),
            feature_nodes,
            components,
            api_bindings,
            navigation_candidates,
            page_states,
        )
        navigation_targets = self._harden_navigation_targets(
            source.get("navigationTargets") or source.get("navigation_targets"),
            navigation_candidates,
        )
        feature_coverage = self._harden_feature_coverage(
            source.get("featureCoverage") or source.get("feature_coverage"),
            feature_nodes,
            components,
            interactions,
            api_bindings,
            page_states,
            mock_data,
        )
        mock_scenarios = self._harden_mock_scenarios(
            source.get("mockScenarios") or source.get("mock_scenarios"),
            feature_nodes,
            page_states,
        )
        development_rules = self._harden_development_rules(
            source.get("developmentRules") or source.get("development_rules"),
        )
        quality_summary = self._harden_quality_summary(
            source.get("qualitySummary") or source.get("quality_summary"),
            feature_coverage,
            components,
            interactions,
            api_bindings,
            mock_data,
            development_rules,
        )
        handoff_summary = self._build_handoff_summary(
            feature_coverage,
            components,
            interactions,
            api_bindings,
            mock_data,
            page_states,
        )

        pages = self._inject_states_into_pages(
            pages,
            page_states,
        )

        enriched = dict(source)
        enriched["artifactType"] = "UI_CONTRACT"
        enriched["schemaVersion"] = "1.0"
        enriched["pages"] = pages
        enriched["page"] = self._merge_page(
            page,
            current_page,
            page_states,
        )
        enriched["mockData"] = mock_data
        enriched["apiBindings"] = api_bindings
        enriched["developmentRules"] = development_rules
        enriched["interactions"] = interactions
        enriched["navigationTargets"] = navigation_targets
        enriched["featureCoverage"] = feature_coverage
        enriched["pageStates"] = page_states
        enriched["mockScenarios"] = mock_scenarios
        enriched["qualitySummary"] = quality_summary
        enriched["developmentHandoff"] = handoff_summary
        enriched["analysisSummary"] = self._append_hardening_summary(
            str(source.get("analysisSummary") or source.get("analysis_summary") or ""),
            feature_coverage,
            quality_summary,
        )

        return self._rebuild_artifact(
            artifact,
            enriched,
        )

    def _to_dict(
        self,
        artifact: UiContractArtifact,
    ) -> dict[str, Any]:
        if hasattr(artifact, "model_dump"):
            return artifact.model_dump(
                mode="python",
                by_alias=True,
                exclude_none=False,
            )

        if isinstance(artifact, dict):
            return dict(artifact)

        return {}

    def _rebuild_artifact(
        self,
        original: UiContractArtifact,
        value: dict[str, Any],
    ) -> UiContractArtifact:
        """Rebuild a strict ``UiContractArtifact`` after deterministic hardening.

        ``BaseModel.model_copy(update=...)`` does not validate nested update
        values in Pydantic v2.  The old fallback path could therefore place
        plain ``dict`` values into fields such as ``page`` or
        ``qualitySummary``.  Downstream code then failed with errors like
        ``'dict' object has no attribute 'model_copy'``.

        Keep the original happy path unchanged, but if direct validation fails
        because the hardener added legacy/helper fields, coerce the enriched
        payload back to the current strict UI_CONTRACT schema before returning.
        If even that fails, return the original validated artifact instead of a
        partially validated model.
        """
        try:
            return original.__class__.model_validate(value)
        except Exception:
            pass

        try:
            coerced = self._coerce_current_schema_payload(original, value)
            return original.__class__.model_validate(coerced)
        except Exception:
            return original

    def _coerce_current_schema_payload(
        self,
        original: UiContractArtifact,
        value: dict[str, Any],
    ) -> dict[str, Any]:
        base = original.model_dump(
            mode="python",
            by_alias=True,
            exclude_none=False,
        )

        component_index = self._collect_component_index(base.get("page"))
        component_ids = list(component_index.keys())

        base["artifactType"] = "UI_CONTRACT"
        base["schemaVersion"] = "1.0"
        base["page"] = self._coerce_page(
            base.get("page"),
            value.get("page"),
        )
        component_index = self._collect_component_index(base.get("page"))
        component_ids = list(component_index.keys())

        base["pageStates"] = self._coerce_page_states(
            value.get("pageStates") or base.get("pageStates"),
            component_ids,
        )
        base["apiBindings"] = self._coerce_api_bindings(
            value.get("apiBindings") or base.get("apiBindings"),
        )
        base["interactions"] = self._coerce_interactions(
            value.get("interactions") or base.get("interactions"),
            component_index,
            base.get("apiBindings") or [],
            base.get("pageStates") or [],
            base.get("navigationTargets") or [],
        )
        base["navigationTargets"] = self._coerce_navigation_targets(
            value.get("navigationTargets") or base.get("navigationTargets"),
        )
        base["featureCoverage"] = self._coerce_feature_coverage(
            value.get("featureCoverage") or base.get("featureCoverage"),
        )
        base["mockScenarios"] = self._coerce_mock_scenarios(
            value.get("mockScenarios") or base.get("mockScenarios"),
            base.get("interactions") or [],
            base.get("pageStates") or [],
        )
        base["mockData"] = (
            dict(value.get("mockData"))
            if isinstance(value.get("mockData"), dict)
            else base.get("mockData") or {}
        )
        base["developmentRules"] = self._coerce_development_rules(
            value.get("developmentRules") or base.get("developmentRules"),
        )
        base["qualitySummary"] = self._coerce_quality_summary(
            value.get("qualitySummary") or base.get("qualitySummary"),
        )

        analysis_summary = str(
            value.get("analysisSummary")
            or base.get("analysisSummary")
            or "已生成页面级 UI_CONTRACT。"
        ).strip()
        base["analysisSummary"] = analysis_summary or "已生成页面级 UI_CONTRACT。"

        # Do not carry legacy helper fields such as pages/developmentHandoff into
        # the strict schema model.
        base.pop("pages", None)
        base.pop("developmentHandoff", None)

        return base

    def _coerce_page(
        self,
        base_page: Any,
        candidate_page: Any,
    ) -> dict[str, Any]:
        page = dict(base_page) if isinstance(base_page, dict) else {}

        if isinstance(candidate_page, dict):
            for key in [
                "pageId",
                "pageKey",
                "pageName",
                "route",
                "title",
                "description",
                "pageKind",
            ]:
                value = candidate_page.get(key)
                if value not in (None, ""):
                    page[key] = value

            layout = candidate_page.get("layout")
            if isinstance(layout, dict):
                layout_type = str(layout.get("type") or "").strip().upper()
                if layout_type in {
                    "CENTERED_FORM",
                    "FORM_DETAIL",
                    "TABLE_LIST",
                    "DETAIL_TABS",
                    "APPROVAL_WORKBENCH",
                    "DASHBOARD",
                    "SETTINGS",
                    "CUSTOM_GRID",
                }:
                    page["layout"] = layout

            component_tree = candidate_page.get("componentTree")
            if isinstance(component_tree, list) and component_tree:
                page["componentTree"] = component_tree

        page.setdefault("pageId", "current_page")
        page.setdefault("pageKey", "current_page")
        page.setdefault("pageName", "当前页面")
        page.setdefault("route", f"/mock/{page.get('pageKey') or 'page'}")
        page.setdefault("title", page.get("pageName") or "当前页面")
        page.setdefault("description", "页面级交互原型。")
        page.setdefault("pageKind", "PAGE")
        page.setdefault("layout", {"type": "FORM_DETAIL", "columns": 1, "regions": []})
        page.setdefault("componentTree", [])

        if not isinstance(page.get("layout"), dict):
            page["layout"] = {"type": "FORM_DETAIL", "columns": 1, "regions": []}

        return page

    def _coerce_page_states(
        self,
        raw_states: Any,
        component_ids: list[str],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for item in self._as_list(raw_states):
            if not isinstance(item, dict):
                continue

            state_key = str(
                item.get("stateKey")
                or item.get("key")
                or item.get("name")
                or item.get("pageStateKey")
                or ""
            ).strip().upper()
            if not state_key or state_key in seen:
                continue

            state_type = state_key if state_key in {
                "DEFAULT",
                "LOADING",
                "EMPTY",
                "ERROR",
                "SUCCESS",
                "DISABLED",
            } else "DEFAULT"

            result.append(
                {
                    "stateKey": state_key,
                    "type": state_type,
                    "title": str(
                        item.get("title")
                        or item.get("stateName")
                        or item.get("name")
                        or self._state_label(state_key)
                    ),
                    "message": str(
                        item.get("message")
                        or item.get("description")
                        or self._state_description(state_key)
                    ),
                    "visibleComponentIds": self._filter_known_ids(
                        item.get("visibleComponentIds"),
                        component_ids,
                    ),
                    "disabledComponentIds": self._filter_known_ids(
                        item.get("disabledComponentIds"),
                        component_ids,
                    ),
                }
            )
            seen.add(state_key)

        if not result:
            result.append(
                {
                    "stateKey": "DEFAULT",
                    "type": "DEFAULT",
                    "title": "默认态",
                    "message": "页面正常展示状态。",
                    "visibleComponentIds": component_ids,
                    "disabledComponentIds": [],
                }
            )

        return result

    def _coerce_api_bindings(
        self,
        raw_bindings: Any,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, item in enumerate(self._as_list(raw_bindings)):
            if not isinstance(item, dict):
                continue

            binding_key = str(
                item.get("bindingKey")
                or item.get("binding_key")
                or item.get("id")
                or item.get("key")
                or f"binding_{index + 1}"
            ).strip()
            if not binding_key or binding_key in seen:
                continue

            method = str(item.get("method") or "POST").strip().upper()
            if method not in {"GET", "POST", "PUT", "PATCH", "DELETE"}:
                method = "POST"

            status = str(item.get("status") or "PLANNED").strip().upper()
            if status not in {"MOCK", "PLANNED", "BOUND"}:
                status = "PLANNED"

            result.append(
                {
                    "bindingKey": binding_key,
                    "method": method,
                    "path": str(item.get("path") or item.get("url") or f"/api/planned/{index + 1}"),
                    "status": status,
                    "requestSchema": dict(item.get("requestSchema")) if isinstance(item.get("requestSchema"), dict) else {},
                    "responseSchema": dict(item.get("responseSchema")) if isinstance(item.get("responseSchema"), dict) else {},
                    "usedByActionIds": self._normalize_str_list(item.get("usedByActionIds")),
                }
            )
            seen.add(binding_key)

        return result

    def _coerce_interactions(
        self,
        raw_interactions: Any,
        component_index: dict[str, dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
        navigation_targets: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        component_ids = list(component_index.keys())
        first_component_id = component_ids[0] if component_ids else None
        state_keys = {
            str(item.get("stateKey") or "").upper()
            for item in page_states
            if isinstance(item, dict) and item.get("stateKey")
        }
        navigation_target_ids = {
            str(item.get("targetPageId") or "")
            for item in navigation_targets
            if isinstance(item, dict) and item.get("targetPageId")
        }
        api_binding_keys = {
            str(item.get("bindingKey"))
            for item in api_bindings
            if isinstance(item, dict) and item.get("bindingKey")
        }

        type_map = {
            "QUERY": "QUERY_DATA",
            "SEARCH": "QUERY_DATA",
            "LIST": "QUERY_DATA",
            "SUBMIT": "SUBMIT_FORM",
            "VALIDATE": "SUBMIT_FORM",
            "APPROVE": "SUBMIT_FORM",
            "REJECT": "SUBMIT_FORM",
            "EXPORT": "SUBMIT_FORM",
            "IMPORT": "SUBMIT_FORM",
            "DELETE": "DELETE_ROW",
        }
        allowed_types = {
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

        for index, item in enumerate(self._as_list(raw_interactions)):
            if not isinstance(item, dict):
                continue

            action_id = str(item.get("actionId") or item.get("id") or f"action_{index + 1}").strip()
            if not action_id:
                action_id = f"action_{index + 1}"
            if action_id in seen:
                action_id = f"{action_id}_{index + 1}"
            seen.add(action_id)

            raw_type = str(item.get("type") or "SUBMIT_FORM").strip().upper()
            action_type = type_map.get(raw_type, raw_type)
            if action_type not in allowed_types:
                action_type = "SUBMIT_FORM"

            label = str(item.get("label") or item.get("title") or "")
            target_id = str(
                item.get("targetId")
                or item.get("componentId")
                or item.get("sourceComponentId")
                or ""
            ).strip()
            source_component_id = str(
                item.get("sourceComponentId")
                or item.get("componentId")
                or item.get("targetId")
                or first_component_id
                or ""
            ).strip()

            action_type, target_id, source_component_id = self._normalize_action_target(
                action_id,
                action_type,
                label,
                target_id,
                source_component_id,
                component_index,
                state_keys,
                bool(item.get("targetPageId")),
            )

            target_page_id = str(item.get("targetPageId") or "").strip()
            if action_type == "NAVIGATE" and target_page_id not in navigation_target_ids:
                action_type = "SHOW_TOAST"
                target_page_id = ""

            data_binding_key = str(
                item.get("dataBindingKey")
                or item.get("apiBinding")
                or item.get("bindingKey")
                or ""
            ).strip()
            if data_binding_key and api_binding_keys and data_binding_key not in api_binding_keys:
                data_binding_key = ""

            result.append(
                {
                    "actionId": action_id,
                    "type": action_type,
                    "label": label,
                    "sourceComponentId": source_component_id or None,
                    "targetId": target_id or None,
                    "targetPageId": target_page_id or None,
                    "targetTabKey": str(item.get("targetTabKey") or "") or None,
                    "dataBindingKey": data_binding_key or None,
                    "scenarioKey": str(item.get("scenarioKey") or "") or None,
                    "payload": dict(item.get("payload")) if isinstance(item.get("payload"), dict) else {},
                    "successActionIds": self._normalize_str_list(item.get("successActionIds")),
                    "failureActionIds": self._normalize_str_list(item.get("failureActionIds")),
                    "featureIds": self._normalize_str_list(item.get("featureIds")),
                    "confirmationMessage": str(item.get("confirmationMessage") or ""),
                }
            )

        return result

    def _coerce_navigation_targets(
        self,
        raw_targets: Any,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, item in enumerate(self._as_list(raw_targets)):
            if not isinstance(item, dict):
                continue

            target_id = str(item.get("targetPageId") or item.get("pageId") or f"target_{index + 1}").strip()
            if not target_id or target_id in seen:
                continue

            status = str(item.get("status") or "PLANNED").strip().upper()
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

        return result

    def _coerce_feature_coverage(
        self,
        raw_coverage: Any,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, item in enumerate(self._as_list(raw_coverage)):
            if not isinstance(item, dict):
                continue

            feature_id = str(item.get("featureId") or item.get("id") or f"feature_{index + 1}").strip()
            if not feature_id or feature_id in seen:
                continue

            status = str(item.get("status") or "PARTIAL").strip().upper()
            if status not in {"FULL", "PARTIAL", "MISSING", "NOT_APPLICABLE"}:
                status = "PARTIAL"

            notes = self._normalize_str_list(item.get("notes"))
            notes.extend(self._normalize_str_list(item.get("warnings")))

            result.append(
                {
                    "featureId": feature_id,
                    "featureKey": str(item.get("featureKey") or item.get("key") or feature_id),
                    "featureName": str(item.get("featureName") or item.get("name") or feature_id),
                    "status": status,
                    "componentIds": self._normalize_str_list(item.get("componentIds")),
                    "actionIds": self._normalize_str_list(item.get("actionIds")),
                    "notes": notes,
                }
            )
            seen.add(feature_id)

        return result

    def _coerce_mock_scenarios(
        self,
        raw_scenarios: Any,
        interactions: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        default_action_id = str(interactions[0].get("actionId")) if interactions else "default_action"
        state_keys = [
            str(item.get("stateKey"))
            for item in page_states
            if isinstance(item, dict) and item.get("stateKey")
        ]
        default_state = state_keys[0] if state_keys else None

        for index, item in enumerate(self._as_list(raw_scenarios)):
            if not isinstance(item, dict):
                continue

            scenario_key = str(item.get("scenarioKey") or item.get("scenarioId") or f"scenario_{index + 1}").strip()
            if not scenario_key or scenario_key in seen:
                continue

            outcome = str(item.get("outcome") or "SUCCESS").strip().upper()
            if outcome not in {"SUCCESS", "FAILURE"}:
                outcome = "SUCCESS"

            result.append(
                {
                    "scenarioKey": scenario_key,
                    "actionId": str(item.get("actionId") or default_action_id),
                    "outcome": outcome,
                    "delayMs": int(item.get("delayMs") or 300),
                    "message": str(item.get("message") or item.get("description") or ""),
                    "nextPageState": str(item.get("nextPageState") or item.get("pageStateKey") or default_state or "") or None,
                    "response": dict(item.get("response")) if isinstance(item.get("response"), dict) else {},
                }
            )
            seen.add(scenario_key)

        return result

    def _coerce_development_rules(
        self,
        raw_rules: Any,
    ) -> dict[str, Any]:
        rules = dict(raw_rules) if isinstance(raw_rules, dict) else {}
        allowed = {
            "mustFollowLayout",
            "mustFollowFields",
            "mustFollowValidationRules",
            "mustFollowInteractionFlow",
            "mustFollowApiBindings",
            "allowStyleOptimization",
            "breakingChangeRequiresReview",
            "arbitraryScriptForbidden",
            "mockMustUseScenarioEngine",
        }
        result = {
            key: bool(rules.get(key))
            for key in allowed
            if key in rules
        }
        result.setdefault("mustFollowLayout", True)
        result.setdefault("mustFollowFields", True)
        result.setdefault("mustFollowValidationRules", True)
        result.setdefault("mustFollowInteractionFlow", True)
        result.setdefault("mustFollowApiBindings", True)
        result.setdefault("allowStyleOptimization", True)
        result.setdefault("breakingChangeRequiresReview", True)
        result.setdefault("arbitraryScriptForbidden", True)
        result.setdefault("mockMustUseScenarioEngine", True)
        return result

    def _coerce_quality_summary(
        self,
        raw_summary: Any,
    ) -> dict[str, Any]:
        summary = dict(raw_summary) if isinstance(raw_summary, dict) else {}
        score = int(summary.get("score") or 80)
        coverage_rate = int(summary.get("coverageRate") or 0)
        return {
            "score": max(0, min(100, score)),
            "coverageRate": max(0, min(100, coverage_rate)),
            "generationMode": "LLM_STRUCTURED",
            "blockers": self._normalize_str_list(summary.get("blockers")),
            "warnings": self._normalize_str_list(summary.get("warnings")),
        }

    def _collect_component_ids(
        self,
        page: Any,
    ) -> list[str]:
        if not isinstance(page, dict):
            return []

        result: list[str] = []

        def visit(value: Any) -> None:
            if not isinstance(value, dict):
                return

            component_id = str(value.get("componentId") or "").strip()
            if component_id and component_id not in result:
                result.append(component_id)

            for child in self._as_list(value.get("children")):
                visit(child)

        for component in self._as_list(page.get("componentTree")):
            visit(component)

        return result

    def _collect_component_index(
        self,
        page: Any,
    ) -> dict[str, dict[str, Any]]:
        if not isinstance(page, dict):
            return {}

        result: dict[str, dict[str, Any]] = {}

        def visit(value: Any) -> None:
            if not isinstance(value, dict):
                return

            component_id = str(value.get("componentId") or value.get("id") or value.get("key") or "").strip()
            if component_id:
                result[component_id] = value

            for child in self._as_list(value.get("children")):
                visit(child)

            for child in self._as_list(value.get("components")):
                visit(child)

            for child in self._as_list(value.get("items")):
                visit(child)

        for component in self._as_list(page.get("componentTree") or page.get("components")):
            visit(component)

        return result

    def _filter_known_ids(
        self,
        raw_ids: Any,
        known_ids: list[str],
    ) -> list[str]:
        values = self._normalize_str_list(raw_ids)
        if not known_ids:
            return values
        return [value for value in values if value in known_ids]

    def _extract_current_page(
        self,
        request: AgentTaskRequest,
        context_package: dict[str, Any],
        artifact: dict[str, Any],
    ) -> dict[str, Any]:
        input_payload = request.input or {}

        for source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
            context_package.get("current_page"),
            artifact.get("module"),
            artifact.get("identity"),
            artifact.get("page"),
        ]:
            if isinstance(source, dict) and source:
                return source

        return {
            "module_id": request.module_id,
            "module_key": "",
            "module_name": "",
            "module_type": "PAGE",
            "extra": {},
        }

    def _resolve_pages(
        self,
        source: dict[str, Any],
        current_page: dict[str, Any],
    ) -> list[dict[str, Any]]:
        pages = [
            item
            for item in self._as_list(source.get("pages"))
            if isinstance(item, dict)
        ]

        if pages:
            return pages

        page = source.get("page")
        if isinstance(page, dict) and page:
            components = page.get("components") or page.get("componentTree") or []
            return [
                {
                    "pageId": self._page_id(current_page, page),
                    "pageKey": self._page_key(current_page, page),
                    "pageName": self._page_name(current_page, page),
                    "pageKind": self._page_kind(current_page, page),
                    "layout": self._layout_type(page),
                    "description": str(page.get("description") or "页面级交互原型。"),
                    "components": components if isinstance(components, list) else [components],
                }
            ]

        return [
            {
                "pageId": self._page_id(current_page, {}),
                "pageKey": self._page_key(current_page, {}),
                "pageName": self._page_name(current_page, {}),
                "pageKind": self._page_kind(current_page, {}),
                "layout": "form-table",
                "description": "页面级交互原型。",
                "components": [],
            }
        ]

    def _resolve_page(
        self,
        source: dict[str, Any],
        pages: list[dict[str, Any]],
        current_page: dict[str, Any],
    ) -> dict[str, Any]:
        page = source.get("page")
        if isinstance(page, dict) and page:
            return page

        first_page = pages[0] if pages else {}
        return {
            "pageId": self._page_id(current_page, first_page),
            "pageKey": self._page_key(current_page, first_page),
            "pageName": self._page_name(current_page, first_page),
            "pageKind": self._page_kind(current_page, first_page),
            "route": str(first_page.get("route") or first_page.get("path") or f"/mock/{self._page_key(current_page, first_page) or 'page'}"),
            "layout": {
                "type": str(first_page.get("layout") or "form-table"),
            },
            "componentTree": first_page.get("components") or [],
        }

    def _merge_page(
        self,
        page: dict[str, Any],
        current_page: dict[str, Any],
        page_states: list[dict[str, Any]],
    ) -> dict[str, Any]:
        result = dict(page)
        result.setdefault("pageId", self._page_id(current_page, page))
        result.setdefault("pageKey", self._page_key(current_page, page))
        result.setdefault("pageName", self._page_name(current_page, page))
        result.setdefault("pageKind", self._page_kind(current_page, page))
        result.setdefault("route", f"/mock/{result.get('pageKey') or 'page'}")
        result.setdefault("layout", {"type": "form-table"})
        result.setdefault("pageStateKeys", [item["stateKey"] for item in page_states])
        return result

    def _inject_states_into_pages(
        self,
        pages: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        keys = [item["stateKey"] for item in page_states]
        state_object = {
            key.lower(): True
            for key in keys
        }

        result: list[dict[str, Any]] = []
        for page in pages:
            item = dict(page)
            item.setdefault("states", state_object)
            item.setdefault("pageStateKeys", keys)
            result.append(item)

        return result

    def _collect_components_from_contract(
        self,
        source: dict[str, Any],
        page: dict[str, Any],
        pages: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        root: list[Any] = []

        for key in ["componentTree", "components", "children"]:
            value = page.get(key)
            if isinstance(value, list):
                root.extend(value)
            elif isinstance(value, dict):
                root.append(value)

        if not root and pages:
            for key in ["components", "componentTree", "children"]:
                value = pages[0].get(key)
                if isinstance(value, list):
                    root.extend(value)
                elif isinstance(value, dict):
                    root.append(value)

        components: list[dict[str, Any]] = []

        def visit(value: Any) -> None:
            if not isinstance(value, dict):
                return

            components.append(value)

            for child_key in ["components", "children", "items"]:
                children = value.get(child_key)
                if isinstance(children, list):
                    for child in children:
                        if isinstance(child, dict) and (
                            child.get("type")
                            or child.get("componentId")
                            or child.get("children")
                            or child.get("components")
                        ):
                            visit(child)

            tabs = value.get("tabs")
            if isinstance(tabs, list):
                for tab in tabs:
                    if isinstance(tab, dict):
                        for child in self._as_list(tab.get("components")) + self._as_list(tab.get("children")):
                            visit(child)

        for item in root:
            visit(item)

        return components

    def _harden_page_states(
        self,
        source: dict[str, Any],
        page: dict[str, Any],
        feature_nodes: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        existing = source.get("pageStates") or source.get("page_states") or page.get("pageStates") or page.get("states")

        normalized: dict[str, dict[str, Any]] = {}

        if isinstance(existing, list):
            for item in existing:
                if not isinstance(item, dict):
                    continue
                key = str(item.get("stateKey") or item.get("key") or item.get("name") or "").upper()
                if key:
                    normalized[key] = {
                        "stateKey": key,
                        "stateName": str(item.get("stateName") or item.get("name") or self._state_label(key)),
                        "description": str(item.get("description") or item.get("message") or self._state_description(key)),
                    }
        elif isinstance(existing, dict):
            for key, item in existing.items():
                state_key = str(key).upper()
                if not state_key:
                    continue
                if isinstance(item, dict):
                    normalized[state_key] = {
                        "stateKey": state_key,
                        "stateName": str(item.get("stateName") or item.get("name") or self._state_label(state_key)),
                        "description": str(item.get("description") or item.get("message") or self._state_description(state_key)),
                    }
                elif item:
                    normalized[state_key] = {
                        "stateKey": state_key,
                        "stateName": self._state_label(state_key),
                        "description": self._state_description(state_key),
                    }

        for item in self.DEFAULT_PAGE_STATES:
            normalized.setdefault(item["stateKey"], item)

        if any(self._feature_kind(feature) in {"query", "search", "list"} for feature in feature_nodes):
            normalized.setdefault(
                "EMPTY",
                {
                    "stateKey": "EMPTY",
                    "stateName": "空状态",
                    "description": "列表、查询或结果区域暂无数据时展示。",
                },
            )

        if any(self._feature_kind(feature) in {"create", "add", "update", "edit", "submit", "approve", "reject"} for feature in feature_nodes):
            normalized.setdefault(
                "SUCCESS",
                {
                    "stateKey": "SUCCESS",
                    "stateName": "成功态",
                    "description": "提交、保存或审批成功后的反馈状态。",
                },
            )

        order = ["DEFAULT", "LOADING", "ERROR", "EMPTY", "SUCCESS"]
        return [
            normalized[key]
            for key in order
            if key in normalized
        ] + [
            value
            for key, value in normalized.items()
            if key not in order
        ]

    def _harden_mock_data(
        self,
        raw_mock_data: Any,
        page: dict[str, Any],
        components: list[dict[str, Any]],
        feature_nodes: list[dict[str, Any]],
        artifacts: dict[str, Any],
    ) -> dict[str, Any]:
        mock_data = dict(raw_mock_data) if isinstance(raw_mock_data, dict) else {}

        table_bindings = self._extract_table_mock_bindings(components)
        for binding in table_bindings:
            mock_data.setdefault(
                binding,
                [
                    {
                        "id": "mock_001",
                        "code": "MOCK-001",
                        "name": "示例记录",
                        "status": "待处理",
                        "owner": "系统用户",
                        "createdAt": "2026-01-01 09:00",
                    }
                ],
            )

        if any(self._component_type(component) in {"description", "card"} for component in components):
            mock_data.setdefault(
                "detail",
                {
                    "id": "mock_001",
                    "code": "MOCK-001",
                    "name": str(page.get("pageName") or "示例详情"),
                    "status": "待处理",
                    "owner": "系统用户",
                    "createdAt": "2026-01-01 09:00",
                },
            )

        if feature_nodes:
            mock_data.setdefault(
                "featureCoveragePreview",
                [
                    {
                        "featureId": self._feature_id(feature, index),
                        "featureName": self._feature_name(feature, index),
                        "status": "已覆盖",
                    }
                    for index, feature in enumerate(feature_nodes)
                ],
            )

        mock_data.setdefault(
            "pageMeta",
            {
                "pageName": str(page.get("pageName") or "当前页面"),
                "generatedFor": "UI_CONTRACT_MOCK_PREVIEW",
            },
        )

        return mock_data

    def _harden_api_bindings(
        self,
        raw_api_bindings: Any,
        feature_nodes: list[dict[str, Any]],
        current_page: dict[str, Any],
        artifacts: dict[str, Any],
    ) -> list[dict[str, Any]]:
        api_bindings = [
            dict(item)
            for item in self._as_list(raw_api_bindings)
            if isinstance(item, dict)
        ]

        existing_keys = {
            self._api_binding_key(item)
            for item in api_bindings
            if self._api_binding_key(item)
        }

        api_contract = artifacts.get("API_CONTRACT")
        extracted = self._extract_api_bindings_from_api_contract(api_contract)
        for binding in extracted:
            key = self._api_binding_key(binding)
            if key and key not in existing_keys:
                api_bindings.append(binding)
                existing_keys.add(key)

        page_key = self._safe_key(
            self._page_key(current_page, {}),
            "page",
        )

        for index, feature in enumerate(feature_nodes):
            kind = self._feature_kind(feature)
            feature_key = self._safe_key(
                self._feature_key(feature, index),
                f"feature_{index + 1}",
            )
            binding_key = f"{feature_key}_{kind or 'action'}"

            if binding_key in existing_keys:
                continue

            method = self.FEATURE_KIND_METHOD.get(kind, "POST")
            path = f"/api/{page_key}/{feature_key.replace('_', '-')}"
            api_bindings.append(
                {
                    "bindingKey": binding_key,
                    "method": method,
                    "path": path,
                    "status": "PLANNED",
                    "description": f"{self._feature_name(feature, index)}的计划接口绑定。",
                    "source": "UI_CONTRACT_GENERATION_HARDENER",
                }
            )
            existing_keys.add(binding_key)

        return api_bindings

    def _harden_interactions(
        self,
        raw_interactions: Any,
        feature_nodes: list[dict[str, Any]],
        components: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        navigation_candidates: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        interactions = [
            dict(item)
            for item in self._as_list(raw_interactions)
            if isinstance(item, dict)
        ]

        existing_ids = {
            str(item.get("actionId") or item.get("id") or "")
            for item in interactions
        }

        component_ids = [
            self._component_id(component)
            for component in components
            if self._component_id(component)
        ]
        primary_component_id = component_ids[0] if component_ids else ""

        api_keys = {
            self._api_binding_key(binding)
            for binding in api_bindings
            if self._api_binding_key(binding)
        }

        for index, feature in enumerate(feature_nodes):
            kind = self._feature_kind(feature)
            feature_key = self._safe_key(
                self._feature_key(feature, index),
                f"feature_{index + 1}",
            )
            action_id = f"{feature_key}_{kind or 'action'}"

            if action_id in existing_ids:
                continue

            binding_key = action_id if action_id in api_keys else next(iter(api_keys), "")
            interaction_type = self.FEATURE_KIND_ACTION_TYPE.get(kind, "SUBMIT")
            item: dict[str, Any] = {
                "actionId": action_id,
                "type": interaction_type,
                "label": self._feature_name(feature, index),
                "componentId": self._best_component_for_feature(
                    feature,
                    components,
                    primary_component_id,
                ),
                "mockAction": f"mock_{action_id}",
                "apiBinding": binding_key,
                "successMessage": f"{self._feature_name(feature, index)}已完成。",
                "sourceFeatureId": self._feature_id(feature, index),
            }

            if interaction_type == "NAVIGATE":
                target_id = self._first_navigation_page_id(navigation_candidates)
                if target_id:
                    item["targetPageId"] = target_id
                else:
                    # No valid navigation target, keep it as planned local feedback instead.
                    item["type"] = "SUBMIT"
                    item["successMessage"] = "操作已完成，当前上下文未提供可跳转页面。"

            interactions.append(item)
            existing_ids.add(action_id)

        return self._sanitize_interactions(
            interactions,
            components,
            page_states,
            navigation_candidates,
        )

    def _harden_navigation_targets(
        self,
        raw_targets: Any,
        navigation_candidates: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        targets = [
            dict(item)
            for item in self._as_list(raw_targets)
            if isinstance(item, dict)
        ]

        existing_ids = {
            str(item.get("targetPageId") or item.get("pageId") or "")
            for item in targets
        }

        for candidate in navigation_candidates:
            page_id = str(candidate.get("page_id") or candidate.get("pageId") or "")
            if not page_id or page_id in existing_ids:
                continue

            targets.append(
                {
                    "targetPageId": page_id,
                    "targetPageKey": str(candidate.get("page_key") or candidate.get("pageKey") or ""),
                    "targetPageName": str(candidate.get("page_name") or candidate.get("pageName") or ""),
                    "route": str(candidate.get("route") or candidate.get("path") or candidate.get("page_path") or candidate.get("pagePath") or ""),
                    "source": "navigation_candidates",
                }
            )
            existing_ids.add(page_id)

        return targets

    def _harden_feature_coverage(
        self,
        raw_feature_coverage: Any,
        feature_nodes: list[dict[str, Any]],
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
        mock_data: dict[str, Any],
    ) -> list[dict[str, Any]]:
        coverage = [
            dict(item)
            for item in self._as_list(raw_feature_coverage)
            if isinstance(item, dict)
        ]

        by_feature_id = {
            str(item.get("featureId") or item.get("feature_id") or item.get("id") or ""): item
            for item in coverage
            if str(item.get("featureId") or item.get("feature_id") or item.get("id") or "")
        }

        if not feature_nodes and coverage:
            return [self._normalize_coverage_item(item) for item in coverage]

        result: list[dict[str, Any]] = []

        for index, feature in enumerate(feature_nodes):
            feature_id = self._feature_id(feature, index)
            feature_key = self._feature_key(feature, index)
            feature_name = self._feature_name(feature, index)
            kind = self._feature_kind(feature)

            existing = by_feature_id.get(feature_id, {})
            component_ids = self._normalize_str_list(existing.get("componentIds") or existing.get("component_ids"))
            action_ids = self._normalize_str_list(existing.get("actionIds") or existing.get("action_ids"))
            api_keys = self._normalize_str_list(existing.get("apiBindingKeys") or existing.get("api_binding_keys"))
            state_keys = self._normalize_str_list(existing.get("pageStateKeys") or existing.get("stateKeys"))
            mock_keys = self._normalize_str_list(existing.get("mockDataKeys") or existing.get("mockKeys"))

            if not component_ids:
                matched_component = self._best_component_for_feature(feature, components, "")
                if matched_component:
                    component_ids = [matched_component]

            if not action_ids:
                action_ids = [
                    str(item.get("actionId"))
                    for item in interactions
                    if str(item.get("sourceFeatureId") or "") == feature_id
                    and item.get("actionId")
                ]

            if not api_keys:
                api_keys = [
                    str(item.get("apiBinding"))
                    for item in interactions
                    if str(item.get("sourceFeatureId") or "") == feature_id
                    and item.get("apiBinding")
                ]

            if not api_keys:
                expected_key = f"{self._safe_key(feature_key, f'feature_{index + 1}')}_{kind or 'action'}"
                if any(self._api_binding_key(binding) == expected_key for binding in api_bindings):
                    api_keys = [expected_key]

            if not state_keys:
                state_keys = ["DEFAULT"]
                if kind in {"query", "search", "list"}:
                    state_keys.extend(["LOADING", "EMPTY", "ERROR"])
                elif kind in {"create", "add", "update", "edit", "submit", "approve", "reject"}:
                    state_keys.extend(["LOADING", "SUCCESS", "ERROR"])

            if not mock_keys:
                if kind in {"query", "search", "list"} and "records" in mock_data:
                    mock_keys = ["records"]
                elif "detail" in mock_data:
                    mock_keys = ["detail"]

            status = "FULL"
            warnings: list[str] = []
            if not component_ids:
                status = "MISSING"
                warnings.append("缺少组件承载。")
            elif action_ids and not api_keys:
                status = "PARTIAL"
                warnings.append("动作缺少接口绑定。")
            elif kind in {"query", "search", "list"} and not mock_keys:
                status = "PARTIAL"
                warnings.append("缺少 Mock 数据。")

            result.append(
                {
                    "featureId": feature_id,
                    "featureKey": feature_key,
                    "featureName": feature_name,
                    "featureKind": kind or "feature",
                    "status": status,
                    "componentIds": component_ids,
                    "actionIds": action_ids,
                    "apiBindingKeys": api_keys,
                    "pageStateKeys": sorted(set(state_keys)),
                    "mockDataKeys": sorted(set(mock_keys)),
                    "warnings": warnings,
                    "source": "UI_CONTRACT_GENERATION_HARDENER",
                }
            )

        return result

    def _normalize_coverage_item(
        self,
        item: dict[str, Any],
    ) -> dict[str, Any]:
        result = dict(item)
        result.setdefault("featureId", str(item.get("feature_id") or item.get("id") or item.get("key") or "feature"))
        result.setdefault("featureKey", str(item.get("feature_key") or item.get("key") or result["featureId"]))
        result.setdefault("featureName", str(item.get("feature_name") or item.get("name") or item.get("title") or result["featureKey"]))
        result.setdefault("featureKind", str(item.get("feature_kind") or item.get("kind") or item.get("type") or "feature"))
        result.setdefault("status", "PARTIAL")
        result.setdefault("componentIds", self._normalize_str_list(item.get("componentIds") or item.get("component_ids")))
        result.setdefault("actionIds", self._normalize_str_list(item.get("actionIds") or item.get("action_ids")))
        result.setdefault("apiBindingKeys", self._normalize_str_list(item.get("apiBindingKeys") or item.get("api_binding_keys")))
        result.setdefault("pageStateKeys", self._normalize_str_list(item.get("pageStateKeys") or item.get("stateKeys")))
        result.setdefault("mockDataKeys", self._normalize_str_list(item.get("mockDataKeys") or item.get("mockKeys")))
        return result

    def _harden_mock_scenarios(
        self,
        raw_scenarios: Any,
        feature_nodes: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        scenarios = [
            dict(item)
            for item in self._as_list(raw_scenarios)
            if isinstance(item, dict)
        ]

        if scenarios:
            return scenarios

        result = [
            {
                "scenarioId": "default",
                "scenarioName": "默认数据",
                "description": "页面正常加载并展示 Mock 数据。",
                "pageStateKey": "DEFAULT",
            },
            {
                "scenarioId": "loading",
                "scenarioName": "加载中",
                "description": "模拟接口请求等待。",
                "pageStateKey": "LOADING",
            },
            {
                "scenarioId": "error",
                "scenarioName": "异常提示",
                "description": "模拟接口异常或校验失败。",
                "pageStateKey": "ERROR",
            },
        ]

        state_keys = {item["stateKey"] for item in page_states}
        if "EMPTY" in state_keys:
            result.append(
                {
                    "scenarioId": "empty",
                    "scenarioName": "空数据",
                    "description": "模拟无查询结果或无列表数据。",
                    "pageStateKey": "EMPTY",
                }
            )
        if "SUCCESS" in state_keys:
            result.append(
                {
                    "scenarioId": "success",
                    "scenarioName": "操作成功",
                    "description": "模拟提交、保存或审批成功。",
                    "pageStateKey": "SUCCESS",
                }
            )

        for index, feature in enumerate(feature_nodes[:4]):
            result.append(
                {
                    "scenarioId": f"feature_{index + 1}",
                    "scenarioName": self._feature_name(feature, index),
                    "description": f"覆盖功能点：{self._feature_name(feature, index)}。",
                    "sourceFeatureId": self._feature_id(feature, index),
                    "pageStateKey": "DEFAULT",
                }
            )

        return result

    def _harden_development_rules(
        self,
        raw_rules: Any,
    ) -> dict[str, Any]:
        rules = dict(raw_rules) if isinstance(raw_rules, dict) else {}
        rules.update(
            {
                "mustFollowLayout": True,
                "mustFollowFields": True,
                "mustFollowValidationRules": True,
                "mustFollowInteractionFlow": True,
                "mustFollowApiBindings": True,
                "mustFollowFeatureCoverage": True,
                "mustPreserveComponentIds": True,
                "mustPreserveActionIds": True,
                "mustHandlePageStates": True,
                "mustUseMockDataAsExamples": True,
                "breakingChangeRequiresReview": True,
            }
        )
        rules.setdefault("allowStyleOptimization", True)
        rules.setdefault(
            "devAgentHandoffNote",
            "Development Agent 必须基于 UI_CONTRACT 的组件、动作、接口绑定、功能覆盖矩阵和页面状态生成真实代码。",
        )
        return rules

    def _harden_quality_summary(
        self,
        raw_summary: Any,
        feature_coverage: list[dict[str, Any]],
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        mock_data: dict[str, Any],
        development_rules: dict[str, Any],
    ) -> dict[str, Any]:
        summary = dict(raw_summary) if isinstance(raw_summary, dict) else {}
        blockers: list[str] = []
        warnings: list[str] = []

        if not components:
            blockers.append("组件树为空，无法渲染页面。")

        if feature_coverage and any(item.get("status") == "MISSING" for item in feature_coverage):
            blockers.append("存在未覆盖 FEATURE。")

        if not feature_coverage:
            warnings.append("未声明 FEATURE 覆盖矩阵。")

        if not api_bindings:
            warnings.append("缺少 API 绑定。")

        if not mock_data:
            warnings.append("缺少 Mock 数据。")

        if not development_rules:
            warnings.append("缺少开发约束。")

        full_count = sum(1 for item in feature_coverage if item.get("status") == "FULL")
        coverage_rate = 100 if not feature_coverage else round(full_count * 100 / len(feature_coverage))
        score = max(
            0,
            min(
                100,
                100
                - len(blockers) * 24
                - len(warnings) * 8
                - max(0, len(feature_coverage) - full_count) * 5,
            ),
        )

        summary.update(
            {
                "score": int(summary.get("score") or score),
                "coverageRate": coverage_rate,
                "blockers": blockers,
                "warnings": warnings,
                "componentCount": len(components),
                "actionCount": len(interactions),
                "apiBindingCount": len(api_bindings),
                "mockDataKeyCount": len(mock_data),
                "canPreview": not blockers and bool(components),
                "canHandoffToDevAgent": not blockers and coverage_rate >= 80 and bool(api_bindings),
            }
        )
        return summary

    def _build_handoff_summary(
        self,
        feature_coverage: list[dict[str, Any]],
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        mock_data: dict[str, Any],
        page_states: list[dict[str, Any]],
    ) -> dict[str, Any]:
        return {
            "handoffTarget": "DEVELOPMENT_AGENT",
            "ready": bool(components) and bool(feature_coverage) and all(item.get("status") != "MISSING" for item in feature_coverage),
            "featureCoverageCount": len(feature_coverage),
            "componentCount": len(components),
            "interactionCount": len(interactions),
            "apiBindingCount": len(api_bindings),
            "mockDataKeys": list(mock_data.keys()),
            "pageStateKeys": [item["stateKey"] for item in page_states],
            "handoffChecklist": [
                "preserve componentId",
                "preserve actionId",
                "respect apiBindings",
                "implement pageStates",
                "use mockData as examples",
                "do not change featureCoverage without review",
            ],
        }

    def _append_hardening_summary(
        self,
        text: str,
        feature_coverage: list[dict[str, Any]],
        quality_summary: dict[str, Any],
    ) -> str:
        base = text.strip() or "已生成页面级 UI_CONTRACT。"
        return (
            f"{base} 已补强 featureCoverage、pageStates、mockScenarios、apiBindings、mockData、developmentRules "
            f"和 Development Agent 交接摘要；当前 FEATURE 覆盖率 {quality_summary.get('coverageRate', 0)}%，"
            f"功能覆盖项 {len(feature_coverage)} 个。"
        )

    def _extract_api_bindings_from_api_contract(
        self,
        value: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(value, dict):
            return []

        result: list[dict[str, Any]] = []
        for key in [
            "apiBindings",
            "api_bindings",
            "endpoints",
            "interfaces",
            "api_endpoints",
        ]:
            candidates = value.get(key)
            if not isinstance(candidates, list):
                continue

            for index, item in enumerate(candidates):
                if not isinstance(item, dict):
                    continue

                method = str(item.get("method") or item.get("httpMethod") or item.get("http_method") or "POST").upper()
                path = str(item.get("path") or item.get("url") or item.get("endpoint") or f"/api/planned/{index + 1}")
                binding_key = str(
                    item.get("bindingKey")
                    or item.get("binding_key")
                    or item.get("operationId")
                    or item.get("operation_id")
                    or self._safe_key(path, f"api_{index + 1}")
                )

                result.append(
                    {
                        "bindingKey": binding_key,
                        "method": method,
                        "path": path,
                        "status": str(item.get("status") or "PLANNED"),
                        "description": str(item.get("description") or item.get("summary") or ""),
                        "source": "API_CONTRACT",
                    }
                )

        return result

    def _extract_table_mock_bindings(
        self,
        components: list[dict[str, Any]],
    ) -> list[str]:
        bindings: list[str] = []
        for component in components:
            if self._component_type(component) != "table":
                continue

            data_source = component.get("dataSource") or component.get("data_source")
            if not isinstance(data_source, dict):
                data_source = {}
            binding = str(data_source.get("mockBinding") or data_source.get("bindingKey") or component.get("mockBinding") or "records")
            if binding and binding not in bindings:
                bindings.append(binding)

        return bindings or ["records"]

    def _sanitize_interactions(
        self,
        interactions: list[dict[str, Any]],
        components: list[dict[str, Any]],
        page_states: list[dict[str, Any]],
        navigation_candidates: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        component_index = {
            self._component_id(component): component
            for component in components
            if self._component_id(component)
        }
        state_keys = {
            str(item.get("stateKey") or "").upper()
            for item in page_states
            if isinstance(item, dict) and item.get("stateKey")
        }
        navigation_target_ids = {
            str(item.get("page_id") or item.get("pageId") or "")
            for item in navigation_candidates
            if isinstance(item, dict) and (item.get("page_id") or item.get("pageId"))
        }

        for index, item in enumerate(interactions):
            action_id = str(item.get("actionId") or item.get("id") or f"action_{index + 1}")
            if action_id in seen:
                action_id = f"{action_id}_{index + 1}"

            seen.add(action_id)
            normalized = dict(item)
            normalized["actionId"] = action_id
            action_type = str(normalized.get("type") or "").upper()
            label = str(normalized.get("label") or normalized.get("title") or "")
            target_id = str(
                normalized.get("targetId")
                or normalized.get("componentId")
                or normalized.get("sourceComponentId")
                or ""
            ).strip()
            source_component_id = str(
                normalized.get("sourceComponentId")
                or normalized.get("componentId")
                or normalized.get("targetId")
                or ""
            ).strip()

            action_type, target_id, source_component_id = self._normalize_action_target(
                action_id,
                action_type,
                label,
                target_id,
                source_component_id,
                component_index,
                state_keys,
                bool(normalized.get("targetPageId")),
            )

            target_page_id = str(normalized.get("targetPageId") or "").strip()
            if action_type == "NAVIGATE" and target_page_id not in navigation_target_ids:
                action_type = "SHOW_TOAST"
                target_page_id = ""

            normalized["type"] = action_type
            normalized["sourceComponentId"] = source_component_id or None
            normalized["targetId"] = target_id or None
            normalized["targetPageId"] = target_page_id or None
            normalized.pop("componentId", None)
            normalized.pop("component_id", None)
            normalized.pop("targetPageKey", None)
            normalized.pop("targetPageName", None)
            normalized.pop("target_page_key", None)
            normalized.pop("target_page_name", None)
            result.append(normalized)

        return result

    def _normalize_action_target(
        self,
        action_id: str,
        action_type: str,
        label: str,
        target_id: str,
        source_component_id: str,
        component_index: dict[str, dict[str, Any]],
        state_keys: set[str],
        has_navigation_target: bool,
    ) -> tuple[str, str, str]:
        normalized_type = str(action_type or "").strip().upper() or "SHOW_TOAST"
        normalized_target = str(target_id or "").strip()
        normalized_source = str(source_component_id or "").strip()

        if normalized_source and normalized_source not in component_index:
            normalized_source = ""

        if self._looks_like_navigation_action(action_id, normalized_type, label):
            if normalized_type != "NAVIGATE" or not has_navigation_target:
                normalized_type = "SHOW_TOAST"
            if normalized_target and normalized_target not in component_index:
                normalized_target = ""
            return normalized_type, normalized_target, normalized_source

        if normalized_type == "SET_PAGE_STATE":
            state_key = normalized_target.upper()
            if state_key not in state_keys:
                state_key = self._semantic_state_for_action_text(
                    f"{action_id} {label} {normalized_type}",
                    state_keys,
                )
            return normalized_type, state_key, normalized_source

        if normalized_type in {"SUBMIT_FORM", "RESET_FORM"}:
            form_id = self._resolve_form_target(
                normalized_target,
                normalized_source,
                component_index,
            )
            if form_id:
                return normalized_type, form_id, normalized_source or form_id

            return "SHOW_TOAST", "", normalized_source

        if normalized_type == "OPEN_MODAL":
            modal_id = self._resolve_component_target(
                normalized_target,
                component_index,
                {"modal"},
            )
            if modal_id:
                return normalized_type, modal_id, normalized_source
            return "SHOW_TOAST", "", normalized_source

        if normalized_type == "SWITCH_TAB":
            tabs_id = self._resolve_component_target(
                normalized_target,
                component_index,
                {"tabs"},
            )
            if tabs_id:
                return normalized_type, tabs_id, normalized_source
            return "SHOW_TOAST", "", normalized_source

        if normalized_type in {"QUERY_DATA", "DELETE_ROW", "SET_VALUE"}:
            if normalized_target and normalized_target not in component_index:
                normalized_target = ""
            return normalized_type, normalized_target, normalized_source

        if normalized_target and normalized_target not in component_index:
            normalized_target = ""

        return normalized_type, normalized_target, normalized_source

    def _looks_like_navigation_action(
        self,
        action_id: str,
        action_type: str,
        label: str,
    ) -> bool:
        text = f"{action_id} {action_type} {label}".lower()
        return any(
            token in text
            for token in [
                "navigate",
                "navigation",
                "switch_to",
                "switch-to",
                "jump",
                "route",
                "redirect",
                "register",
                "signup",
                "sign_up",
                "login",
                "登录",
                "注册",
                "跳转",
                "切换",
                "返回",
            ]
        ) and not any(
            token in text
            for token in [
                "submit",
                "save",
                "confirm",
                "提交",
                "保存",
                "确认",
            ]
        )

    def _semantic_state_for_action_text(
        self,
        text: str,
        state_keys: set[str],
    ) -> str:
        lower = text.lower()
        candidates: list[str]

        if any(token in lower for token in ["success", "succeed", "complete", "done", "成功", "完成"]):
            candidates = ["SUCCESS", "DEFAULT"]
        elif any(token in lower for token in ["error", "fail", "failure", "invalid", "异常", "错误", "失败"]):
            candidates = ["ERROR", "DEFAULT"]
        elif any(token in lower for token in ["loading", "pending", "submitting", "加载", "处理中", "提交中"]):
            candidates = ["LOADING", "DEFAULT"]
        elif any(token in lower for token in ["empty", "none", "暂无", "为空"]):
            candidates = ["EMPTY", "DEFAULT"]
        else:
            candidates = ["DEFAULT", "SUCCESS", "ERROR", "LOADING"]

        for candidate in candidates:
            if candidate in state_keys:
                return candidate

        return sorted(state_keys)[0] if state_keys else "DEFAULT"

    def _resolve_form_target(
        self,
        target_id: str,
        source_component_id: str,
        component_index: dict[str, dict[str, Any]],
    ) -> str:
        direct = self._resolve_component_target(
            target_id,
            component_index,
            {"form"},
        )
        if direct:
            return direct

        source_direct = self._resolve_component_target(
            source_component_id,
            component_index,
            {"form"},
        )
        if source_direct:
            return source_direct

        for value in [target_id, source_component_id]:
            if not value:
                continue

            candidates = []
            if value.endswith("_button"):
                candidates.append(value.removesuffix("_button") + "_form")
            if value.endswith("_btn"):
                candidates.append(value.removesuffix("_btn") + "_form")
            if "button" in value:
                candidates.append(value.replace("button", "form"))
            if "btn" in value:
                candidates.append(value.replace("btn", "form"))

            for candidate in candidates:
                if self._component_type(component_index.get(candidate, {})) == "form":
                    return candidate

        for component_id, component in component_index.items():
            if self._component_type(component) == "form":
                return component_id

        return ""

    def _resolve_component_target(
        self,
        target_id: str,
        component_index: dict[str, dict[str, Any]],
        expected_types: set[str],
    ) -> str:
        if target_id and self._component_type(component_index.get(target_id, {})) in expected_types:
            return target_id

        for component_id, component in component_index.items():
            if self._component_type(component) in expected_types:
                return component_id

        return ""

    def _best_component_for_feature(
        self,
        feature: dict[str, Any],
        components: list[dict[str, Any]],
        fallback: str,
    ) -> str:
        tokens = {
            token
            for token in re.split(
                r"[\s_\-/]+",
                " ".join(
                    [
                        self._feature_key(feature, 0),
                        self._feature_name(feature, 0),
                        self._feature_kind(feature),
                    ]
                ).lower(),
            )
            if token
        }

        scored: list[tuple[int, str]] = []
        for component in components:
            component_id = self._component_id(component)
            if not component_id:
                continue

            text = " ".join(
                [
                    str(component.get("componentId") or ""),
                    str(component.get("title") or ""),
                    str(component.get("label") or ""),
                    str(component.get("type") or ""),
                ]
            ).lower()

            score = sum(1 for token in tokens if token and token in text)
            if self._feature_kind(feature) in {"query", "search", "list"} and self._component_type(component) in {"table", "form", "toolbar"}:
                score += 2
            if self._feature_kind(feature) in {"create", "update", "submit", "approve", "reject"} and self._component_type(component) in {"form", "modal", "button"}:
                score += 2

            if score > 0:
                scored.append((score, component_id))

        if scored:
            scored.sort(reverse=True)
            return scored[0][1]

        return fallback or (self._component_id(components[0]) if components else "")

    def _first_navigation_page_id(
        self,
        navigation_candidates: list[dict[str, Any]],
    ) -> str:
        for item in navigation_candidates:
            page_id = str(item.get("page_id") or item.get("pageId") or "")
            if page_id:
                return page_id
        return ""

    def _page_id(self, current_page: dict[str, Any], fallback: dict[str, Any]) -> str:
        return str(
            current_page.get("module_id")
            or current_page.get("id")
            or current_page.get("pageId")
            or fallback.get("pageId")
            or fallback.get("page_id")
            or "current_page"
        )

    def _page_key(self, current_page: dict[str, Any], fallback: dict[str, Any]) -> str:
        return str(
            current_page.get("module_key")
            or current_page.get("moduleKey")
            or current_page.get("key")
            or current_page.get("pageKey")
            or fallback.get("pageKey")
            or fallback.get("page_key")
            or "current_page"
        )

    def _page_name(self, current_page: dict[str, Any], fallback: dict[str, Any]) -> str:
        return str(
            current_page.get("module_name")
            or current_page.get("moduleName")
            or current_page.get("name")
            or current_page.get("pageName")
            or fallback.get("pageName")
            or fallback.get("page_name")
            or "当前页面"
        )

    def _page_kind(self, current_page: dict[str, Any], fallback: dict[str, Any]) -> str:
        return str(
            self._extract_extra(current_page, "pageKind")
            or current_page.get("pageKind")
            or current_page.get("module_type")
            or current_page.get("moduleType")
            or fallback.get("pageKind")
            or fallback.get("page_kind")
            or "PAGE"
        )

    def _layout_type(self, page: dict[str, Any]) -> str:
        layout = page.get("layout")
        if isinstance(layout, dict):
            return str(layout.get("type") or "form-table")
        return str(layout or "form-table")

    def _feature_id(self, feature: dict[str, Any], index: int) -> str:
        return str(feature.get("module_id") or feature.get("id") or feature.get("featureId") or f"feature_{index + 1}")

    def _feature_key(self, feature: dict[str, Any], index: int) -> str:
        return str(feature.get("module_key") or feature.get("key") or feature.get("featureKey") or self._feature_id(feature, index))

    def _feature_name(self, feature: dict[str, Any], index: int) -> str:
        return str(feature.get("module_name") or feature.get("name") or feature.get("featureName") or self._feature_key(feature, index))

    def _feature_kind(self, feature: dict[str, Any]) -> str:
        raw = (
            feature.get("feature_kind")
            or feature.get("featureKind")
            or feature.get("kind")
            or feature.get("actionType")
            or feature.get("type")
            or feature.get("operation")
            or ""
        )
        return str(raw).strip().lower()

    def _component_id(self, component: dict[str, Any]) -> str:
        return str(component.get("componentId") or component.get("id") or component.get("key") or "")

    def _component_type(self, component: dict[str, Any]) -> str:
        return str(component.get("type") or "").strip().lower()

    def _api_binding_key(self, binding: dict[str, Any]) -> str:
        return str(binding.get("bindingKey") or binding.get("mockBinding") or binding.get("mockAction") or binding.get("id") or binding.get("key") or "")

    def _state_label(self, key: str) -> str:
        return {
            "DEFAULT": "默认态",
            "LOADING": "加载中",
            "ERROR": "错误态",
            "EMPTY": "空状态",
            "SUCCESS": "成功态",
        }.get(key.upper(), key)

    def _state_description(self, key: str) -> str:
        return {
            "DEFAULT": "页面正常展示状态。",
            "LOADING": "数据加载或提交等待状态。",
            "ERROR": "接口异常、校验失败或运行时错误状态。",
            "EMPTY": "没有可展示数据时的空状态。",
            "SUCCESS": "提交、保存或审批成功后的反馈状态。",
        }.get(key.upper(), "")

    def _extract_extra(
        self,
        module: dict[str, Any],
        key: str,
    ) -> Any:
        extra = module.get("extra")
        if isinstance(extra, dict):
            return extra.get(key)
        return None

    def _extract_list(
        self,
        source: dict[str, Any],
        key: str,
    ) -> list[dict[str, Any]]:
        value = source.get(key)
        if not isinstance(value, list):
            return []
        return [item for item in value if isinstance(item, dict)]

    def _normalize_artifacts(
        self,
        raw_artifacts: Any,
    ) -> dict[str, Any]:
        if not isinstance(raw_artifacts, dict):
            return {}

        return {
            str(key).strip().upper(): value
            for key, value in raw_artifacts.items()
        }

    def _normalize_str_list(
        self,
        value: Any,
    ) -> list[str]:
        if isinstance(value, list):
            return [
                str(item).strip()
                for item in value
                if str(item).strip()
            ]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    def _as_list(
        self,
        value: Any,
    ) -> list[Any]:
        if isinstance(value, list):
            return value
        if isinstance(value, dict):
            return [value]
        return []

    def _safe_key(
        self,
        value: str,
        fallback: str,
    ) -> str:
        text = str(value or "").strip().lower()
        text = re.sub(r"[^a-z0-9_\-]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")
        return text or fallback
