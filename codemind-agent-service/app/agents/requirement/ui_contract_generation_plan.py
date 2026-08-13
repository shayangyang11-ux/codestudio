# A9.10.14.4.2.2_EXISTING_PRD_SEMANTIC_BACKFILL
# A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN

from __future__ import annotations
import re

from dataclasses import dataclass
from typing import Any
from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone

# A9.10.14.4.2.1_PRD_SEMANTIC_BACKBONE_PLAN_RESUME


@dataclass(frozen=True)
class UiContractGenerationPlan:
    """
    Deterministic capability boundary for one PAGE UI_CONTRACT generation.

    The LLM may organize the page inside this boundary, but it must not invent:
    - features outside allowed_feature_keys;
    - navigation outside allowed_navigation_target_ids;
    - actions unrelated to PAGE_SPEC and FEATURE definitions.
    """

    page: dict[str, Any]
    features: list[dict[str, Any]]
    allowed_feature_keys: list[str]
    allowed_feature_names: list[str]
    navigation_targets: list[dict[str, Any]]
    allowed_navigation_target_ids: list[str]
    allow_navigation: bool
    page_pattern: str
    required_state_keys: list[str]
    forbidden_terms: list[str]

    def to_prompt_payload(self) -> dict[str, Any]:
        return {
            "page": self.page,
            "features": self.features,
            "allowedFeatureKeys": self.allowed_feature_keys,
            "allowedFeatureNames": self.allowed_feature_names,
            "navigationPolicy": {
                "allowNavigation": self.allow_navigation,
                "allowedTargetIds": self.allowed_navigation_target_ids,
                "allowedTargets": self.navigation_targets,
            },
            "pagePattern": self.page_pattern,
            "requiredStateKeys": self.required_state_keys,
            "forbiddenTerms": self.forbidden_terms,
        }


class UiContractGenerationPlanBuilder:
    """
    Builds the enterprise safety plan before LLM generation.

    This is intentionally deterministic. The model receives a narrow page
    capability plan instead of freely deciding which actions/pages/features
    should exist.
    """

    def build(
        self,
        request: Any,
    ) -> UiContractGenerationPlan:
        context_package = getattr(request, "context_package", None) or {}
        input_payload = getattr(request, "input", None) or {}

        current_page = self._extract_current_page(
            request=request,
            context_package=context_package,
            input_payload=input_payload,
        )

        features = self._extract_features(
            context_package=context_package,
            input_payload=input_payload,
        )

        navigation_targets = self._extract_navigation_targets(
            context_package=context_package,
            input_payload=input_payload,
            current_page=current_page,
        )

        allowed_feature_keys = [
            item["featureKey"]
            for item in features
            if self._has_text(item.get("featureKey"))
        ]

        allowed_feature_names = [
            item["featureName"]
            for item in features
            if self._has_text(item.get("featureName"))
        ]

        page_pattern = self._infer_page_pattern(
            page=current_page,
            features=features,
        )

        # A9.10.14.4.1:
        # Navigation intent alone is not enough. PAGE UI_CONTRACT may only
        # emit NAVIGATE when a real target page identity is supplied by the
        # module/project navigation authority.
        allow_navigation = bool(navigation_targets) and any(
            self._feature_is_navigation(item)
            for item in features
        )

        required_state_keys = self._required_state_keys(
            page_pattern=page_pattern,
            features=features,
        )

        forbidden_terms = self._forbidden_terms(
            page=current_page,
            features=features,
            allow_navigation=allow_navigation,
        )

        return UiContractGenerationPlan(
            page=current_page,
            features=features,
            allowed_feature_keys=allowed_feature_keys,
            allowed_feature_names=allowed_feature_names,
            navigation_targets=navigation_targets,
            allowed_navigation_target_ids=[
                item["targetPageId"]
                for item in navigation_targets
                if self._has_text(item.get("targetPageId"))
            ],
            allow_navigation=allow_navigation,
            page_pattern=page_pattern,
            required_state_keys=required_state_keys,
            forbidden_terms=forbidden_terms,
        )

    def _extract_current_page(
        self,
        request: Any,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> dict[str, Any]:
        hidden_context = context_package.get("hidden_prototype_context")
        if not isinstance(hidden_context, dict):
            hidden_context = input_payload.get("hidden_prototype_context")
        if not isinstance(hidden_context, dict):
            hidden_context = context_package.get("hiddenPrototypeContext")
        if not isinstance(hidden_context, dict):
            hidden_context = {}

        page_prototype_context = (
            hidden_context.get("page_prototype_context")
            or hidden_context.get("pagePrototypeContext")
            or context_package.get("page_prototype_context")
            or context_package.get("pagePrototypeContext")
            or input_payload.get("page_prototype_context")
            or input_payload.get("pagePrototypeContext")
        )
        if not isinstance(page_prototype_context, dict):
            page_prototype_context = {}

        def first_text(*values: Any) -> str:
            for value in values:
                if value is not None and str(value).strip():
                    return str(value).strip()
            return ""

        def first_value(*values: Any) -> Any:
            for value in values:
                if value is None:
                    continue
                if isinstance(value, str) and not value.strip():
                    continue
                if isinstance(value, (list, dict)) and not value:
                    continue
                return value
            return None

        for source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
        ]:
            if not isinstance(source, dict):
                continue

            extra = source.get("extra") if isinstance(source.get("extra"), dict) else {}
            source_page_context = (
                extra.get("pagePrototypeContext")
                or extra.get("page_prototype_context")
                or source.get("pagePrototypeContext")
                or source.get("page_prototype_context")
            )
            if not isinstance(source_page_context, dict):
                source_page_context = {}

            proto = {
                **page_prototype_context,
                **source_page_context,
            }

            result = {
                "pageId": first_text(
                    source.get("module_id"),
                    source.get("moduleId"),
                    source.get("id"),
                    getattr(request, "module_id", ""),
                ),
                "pageKey": first_text(
                    source.get("module_key"),
                    source.get("moduleKey"),
                    source.get("key"),
                    proto.get("pageKey"),
                    proto.get("page_key"),
                ),
                "pageName": first_text(
                    source.get("module_name"),
                    source.get("moduleName"),
                    source.get("name"),
                    proto.get("pageName"),
                    proto.get("page_name"),
                ),
                "pageKind": first_text(
                    proto.get("pageKind"),
                    proto.get("page_kind"),
                    self._read_extra(source, "pageKind"),
                    source.get("pageKind"),
                    "FORM_PAGE",
                ).upper(),
            }

            for key, aliases in {
                "layoutType": ["layoutType", "layout_type", "layout"],
                "routePath": ["routePath", "route_path", "path", "route"],
                "pageIntent": ["pageIntent", "page_intent", "intent"],
                "businessObject": ["businessObject", "business_object", "entityName", "entity_name"],
                "businessDomain": ["businessDomain", "business_domain", "domain"],
                "coreFields": ["coreFields", "core_fields", "fields"],
                "mainActions": ["mainActions", "main_actions", "actions"],
                "detailFields": ["detailFields", "detail_fields", "detailColumns", "detail_columns", "columns"],
                "stateModel": ["stateModel", "state_model", "states"],
            }.items():
                result[key] = first_value(*(proto.get(alias) for alias in aliases))

            if proto:
                result["pagePrototypeContext"] = proto

            return result

        return {
            "pageId": str(getattr(request, "module_id", "") or ""),
            "pageKey": first_text(page_prototype_context.get("pageKey"), page_prototype_context.get("page_key")),
            "pageName": first_text(page_prototype_context.get("pageName"), page_prototype_context.get("page_name")),
            "pageKind": first_text(page_prototype_context.get("pageKind"), page_prototype_context.get("page_kind"), "FORM_PAGE").upper(),
            "layoutType": first_value(page_prototype_context.get("layoutType"), page_prototype_context.get("layout_type")),
            "routePath": first_value(page_prototype_context.get("routePath"), page_prototype_context.get("route_path")),
            "coreFields": first_value(page_prototype_context.get("coreFields"), page_prototype_context.get("core_fields"), page_prototype_context.get("fields")),
            "mainActions": first_value(page_prototype_context.get("mainActions"), page_prototype_context.get("main_actions"), page_prototype_context.get("actions")),
            "pagePrototypeContext": page_prototype_context,
        }

    def _extract_features(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> list[dict[str, Any]]:
        candidates: list[dict[str, Any]] = []

        hidden_context = context_package.get("hidden_prototype_context")
        if not isinstance(hidden_context, dict):
            hidden_context = {}

        page_prototype_context = hidden_context.get("page_prototype_context") or hidden_context.get("pagePrototypeContext")
        selected_function_context = hidden_context.get("selected_function_context") or hidden_context.get("selectedFunctionContext")

        for source in [
            context_package.get("feature_nodes"),
            input_payload.get("feature_nodes"),
            selected_function_context,
            page_prototype_context,
            (context_package.get("ui_contract_readiness") or {}).get("features")
            if isinstance(context_package.get("ui_contract_readiness"), dict)
            else None,
            (input_payload.get("ui_contract_readiness") or {}).get("features")
            if isinstance(input_payload.get("ui_contract_readiness"), dict)
            else None,
        ]:
            if isinstance(source, list):
                candidates.extend(
                    item for item in source if isinstance(item, dict)
                )
            elif isinstance(source, dict):
                candidates.append(source)

        # Phase 10-A4.11:
        # UI_CONTRACT generation must still know PAGE FEATURE semantics even when
        # Java task payload does not pass feature_nodes directly.
        # PAGE_SPEC is the canonical page-level behavior carrier, so use it as a
        # deterministic fallback.
        page_spec = self._extract_page_spec_artifact(
            context_package,
            input_payload,
        )
        candidates.extend(
            self._extract_features_from_page_spec(page_spec)
        )

        result: list[dict[str, Any]] = []
        seen_keys: set[str] = set()

        for item in candidates:
            feature_key = str(
                item.get("featureKey")
                or item.get("feature_key")
                or item.get("module_key")
                or item.get("moduleKey")
                or item.get("key")
                or ""
            ).strip()

            feature_name = str(
                item.get("featureName")
                or item.get("feature_name")
                or item.get("module_name")
                or item.get("moduleName")
                or item.get("name")
                or feature_key
            ).strip()

            if not feature_key:
                continue

            if feature_key in seen_keys:
                continue

            seen_keys.add(feature_key)

            extra = item.get("extra") if isinstance(item.get("extra"), dict) else {}
            function_context = (
                item.get("functionContext")
                or item.get("function_context")
                or extra.get("functionContext")
                or extra.get("function_context")
            )
            if not isinstance(function_context, dict):
                function_context = {}

            result.append(
                {
                    "featureId": str(
                        item.get("featureId")
                        or item.get("feature_id")
                        or item.get("module_id")
                        or item.get("moduleId")
                        or item.get("id")
                        or ""
                    ),
                    "featureKey": feature_key,
                    "featureName": feature_name,
                    "description": str(
                        item.get("description")
                        or item.get("featureDescription")
                        or function_context.get("summary")
                        or function_context.get("goal")
                        or function_context.get("description")
                        or extra.get("description")
                        or extra.get("featureDescription")
                        or ""
                    ),
                    "trigger": str(
                        item.get("trigger")
                        or function_context.get("trigger")
                        or function_context.get("triggerCondition")
                        or extra.get("trigger")
                        or extra.get("triggerCondition")
                        or extra.get("userTrigger")
                        or ""
                    ),
                    "interactionType": str(
                        item.get("interactionType")
                        or item.get("interaction_type")
                        or function_context.get("interactionType")
                        or function_context.get("interaction_type")
                        or function_context.get("actionType")
                        or extra.get("interactionType")
                        or extra.get("interaction_type")
                        or extra.get("actionType")
                        or ""
                    ),
                    "expectedResult": str(
                        item.get("expectedResult")
                        or item.get("expected_result")
                        or function_context.get("expectedResult")
                        or function_context.get("expected_result")
                        or function_context.get("result")
                        or extra.get("expectedResult")
                        or extra.get("expected_result")
                        or extra.get("result")
                        or ""
                    ),
                }
            )

        return result

    def _extract_page_spec_artifact(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> Any:
        for source in [
            context_package.get("artifacts"),
            input_payload.get("artifacts"),
            (context_package.get("input") or {}).get("artifacts")
            if isinstance(context_package.get("input"), dict)
            else None,
        ]:
            if not isinstance(source, dict):
                continue

            for key, value in source.items():
                if str(key).strip().upper() == "PAGE_SPEC":
                    return self._unwrap_artifact_payload(value)

        for key in [
            "PAGE_SPEC",
            "page_spec",
            "pageSpec",
        ]:
            if key in context_package:
                return self._unwrap_artifact_payload(context_package.get(key))
            if key in input_payload:
                return self._unwrap_artifact_payload(input_payload.get(key))

        return None

    def _unwrap_artifact_payload(
        self,
        value: Any,
    ) -> Any:
        if not isinstance(value, dict):
            return value

        content = value.get("content")
        if isinstance(content, dict):
            payload = dict(content)
            markdown = (
                value.get("content_markdown")
                or value.get("contentMarkdown")
                or payload.get("content_markdown")
                or payload.get("contentMarkdown")
                or payload.get("markdown")
            )
            if markdown:
                payload.setdefault("content_markdown", markdown)
                payload.setdefault("contentMarkdown", markdown)
                payload.setdefault("markdown", markdown)
            return payload

        return value

    def _extract_features_from_page_spec(
        self,
        page_spec: Any,
    ) -> list[dict[str, Any]]:
        if not isinstance(page_spec, dict):
            return []

        candidates: list[dict[str, Any]] = []

        def extend_from(value: Any) -> None:
            if isinstance(value, list):
                candidates.extend(item for item in value if isinstance(item, dict))

        for key in [
            "features",
            "feature_specs",
            "featureSpecs",
            "feature_requirements",
            "featureRequirements",
            "functional_requirements",
            "functionalRequirements",
            "action_specs",
            "actionSpecs",
            "interactions",
            "page_actions",
            "pageActions",
        ]:
            extend_from(page_spec.get(key))

        result: list[dict[str, Any]] = []

        for index, item in enumerate(candidates):
            name = str(
                item.get("featureName")
                or item.get("feature_name")
                or item.get("action_name")
                or item.get("actionName")
                or item.get("name")
                or item.get("title")
                or ""
            ).strip()

            key = str(
                item.get("featureKey")
                or item.get("feature_key")
                or item.get("action_key")
                or item.get("actionKey")
                or item.get("key")
                or ""
            ).strip()

            if not name and not key:
                continue

            if not key:
                key = self._safe_feature_key(name, index)

            description = str(
                item.get("description")
                or item.get("expectedResult")
                or item.get("expected_result")
                or item.get("summary")
                or ""
            ).strip()

            result.append(
                {
                    "featureId": str(item.get("featureId") or item.get("id") or key),
                    "featureKey": key,
                    "featureName": name or key,
                    "description": description,
                    "trigger": str(item.get("trigger") or item.get("trigger_position") or item.get("triggerPosition") or ""),
                    "interactionType": str(item.get("interactionType") or item.get("interaction_type") or ""),
                    "expectedResult": str(item.get("expectedResult") or item.get("expected_result") or ""),
                }
            )

        return result

    def _safe_feature_key(
        self,
        name: str,
        index: int,
    ) -> str:
        text = str(name or "").strip().lower()
        replacements = {
            "用户信息展示": "user_info_display",
            "退出登录按钮": "logout_button",
            "登录状态守卫": "login_status_guard",
            "注册错误提示": "register_error_feedback",
            "注册成功跳转": "register_success_navigation",
        }

        if name in replacements:
            return replacements[name]

        text = re.sub(r"[^a-z0-9_\\-\\u4e00-\\u9fff]+", "_", text)
        text = re.sub(r"_+", "_", text).strip("_")
        return text or f"feature_{index + 1}"


    def _extract_navigation_targets(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any] | None = None,
        current_page: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        context_package = self._a9_10_14_4_2_2_backfill_prd_navigation_context(
            context_package,
            locals().get("input_payload"),
            locals().get("current_page"),
        )
        """Resolve current PAGE outgoing navigation targets from authority sources."""
        input_payload = input_payload or {}
        current_page = current_page or {}

        hidden_context = context_package.get("hidden_prototype_context")
        if not isinstance(hidden_context, dict):
            hidden_context = input_payload.get("hidden_prototype_context")
        if not isinstance(hidden_context, dict):
            hidden_context = context_package.get("hiddenPrototypeContext")
        if not isinstance(hidden_context, dict):
            hidden_context = {}

        def as_list(value: Any) -> list[Any]:
            return value if isinstance(value, list) else []

        def text(*values: Any) -> str:
            for value in values:
                if value is not None and str(value).strip():
                    return str(value).strip()
            return ""

        def norm(value: Any) -> str:
            return str(value or "").strip().lower().replace("-", "_").replace(" ", "_")

        current_ids = {
            text(current_page.get("pageId"), current_page.get("module_id"), current_page.get("moduleId"), current_page.get("id")),
        }
        current_keys = {
            text(current_page.get("pageKey"), current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("key")),
        }
        current_names = {
            text(current_page.get("pageName"), current_page.get("module_name"), current_page.get("moduleName"), current_page.get("name")),
        }
        current_ids = {item for item in current_ids if item}
        current_keys = {item for item in current_keys if item}
        current_names = {item for item in current_names if item}
        current_norms = {norm(item) for item in [*current_ids, *current_keys, *current_names] if item}

        artifacts = context_package.get("artifacts")
        if not isinstance(artifacts, dict):
            artifacts = input_payload.get("artifacts")
        if not isinstance(artifacts, dict):
            artifacts = {}

        current_module = (
            input_payload.get("current_module")
            or context_package.get("current_module")
            or context_package.get("module")
            or {}
        )
        if not isinstance(current_module, dict):
            current_module = {}
        module_extra = current_module.get("extra") if isinstance(current_module.get("extra"), dict) else {}

        containers: list[Any] = [
            hidden_context,
            context_package,
            input_payload,
            module_extra,
            module_extra.get("requirementContext") if isinstance(module_extra.get("requirementContext"), dict) else None,
            module_extra.get("requirement_context") if isinstance(module_extra.get("requirement_context"), dict) else None,
            module_extra.get("traceabilityMap"),
            module_extra.get("traceability_map"),
            module_extra.get("modulePrototypeMap"),
            module_extra.get("module_prototype_map"),
            context_package.get("module_prototype_map"),
            context_package.get("modulePrototypeMap"),
            context_package.get("traceability_map"),
            context_package.get("traceabilityMap"),
            artifacts.get("MODULE_APP_CONTRACT"),
            artifacts.get("MODULE_PROTOTYPE_SNAPSHOT"),
            artifacts.get("MODULE_TREE"),
            artifacts.get("PAGE_MAP"),
            artifacts.get("MODULE_PRD"),
        ]

        def unwrap(value: Any) -> Any:
            while isinstance(value, dict):
                for key in ["content", "contentJson", "content_json", "payload", "artifact", "snapshot", "contract"]:
                    nested = value.get(key)
                    if isinstance(nested, dict):
                        value = nested
                        break
                else:
                    return value
            return value

        containers = [unwrap(item) for item in containers if item]

        page_index_by_id: dict[str, dict[str, Any]] = {}
        page_index_by_key: dict[str, dict[str, Any]] = {}
        page_index_by_name: dict[str, dict[str, Any]] = {}

        def index_page(raw: Any) -> None:
            if not isinstance(raw, dict):
                return
            page_id = text(raw.get("page_id"), raw.get("pageId"), raw.get("targetPageId"), raw.get("module_id"), raw.get("moduleId"), raw.get("id"))
            page_key = text(raw.get("page_key"), raw.get("pageKey"), raw.get("targetPageKey"), raw.get("module_key"), raw.get("moduleKey"), raw.get("key"))
            page_name = text(raw.get("page_name"), raw.get("pageName"), raw.get("targetPageName"), raw.get("module_name"), raw.get("moduleName"), raw.get("name"))
            route = text(raw.get("route"), raw.get("targetRoute"), raw.get("routePath"), raw.get("route_path"), raw.get("page_path"), raw.get("pagePath"), raw.get("path"))
            page_kind = text(raw.get("pageKind"), raw.get("page_kind"), raw.get("module_type"), raw.get("moduleType"))
            if not (page_id or page_key or page_name):
                return
            item = {
                "targetPageId": page_id or page_key or page_name,
                "targetPageKey": page_key or page_id or page_name,
                "targetPageName": page_name or page_key or page_id,
                "route": route,
                "pageKind": page_kind,
                "status": "RESOLVED" if route else "PLANNED",
            }
            if page_id:
                page_index_by_id[page_id] = item
            if page_key:
                page_index_by_key[page_key] = item
            if page_name:
                page_index_by_name[page_name] = item

        def scan_pages(value: Any) -> None:
            if isinstance(value, dict):
                for key in [
                    "pages", "pageSurfaces", "page_surfaces", "pageCoverage", "page_coverage", "screens", "routes", "children",
                ]:
                    for item in as_list(value.get(key)):
                        index_page(item)
                        scan_pages(item)
                trace = value.get("traceabilityMap") or value.get("traceability_map")
                if isinstance(trace, dict):
                    scan_pages(trace)
                proto = value.get("modulePrototypeMap") or value.get("module_prototype_map")
                if isinstance(proto, dict):
                    scan_pages(proto)
            elif isinstance(value, list):
                for item in value:
                    index_page(item)
                    scan_pages(item)

        for container in containers:
            scan_pages(container)

        result: list[dict[str, Any]] = []
        seen_ids: set[str] = set()

        def resolve_page(raw: dict[str, Any]) -> dict[str, Any] | None:
            page_id = text(raw.get("targetPageId"), raw.get("target_page_id"), raw.get("toPageId"), raw.get("to_page_id"), raw.get("page_id"), raw.get("pageId"), raw.get("id"))
            page_key = text(raw.get("targetPageKey"), raw.get("target_page_key"), raw.get("toPageKey"), raw.get("to_page_key"), raw.get("page_key"), raw.get("pageKey"), raw.get("key"))
            page_name = text(raw.get("targetPageName"), raw.get("target_page_name"), raw.get("toPageName"), raw.get("to_page_name"), raw.get("page_name"), raw.get("pageName"), raw.get("name"), raw.get("label"))
            resolved = (
                (page_id and page_index_by_id.get(page_id))
                or (page_key and page_index_by_key.get(page_key))
                or (page_name and page_index_by_name.get(page_name))
            )
            if resolved:
                item = dict(resolved)
            elif page_id or page_key or page_name:
                item = {
                    "targetPageId": page_id or page_key or page_name,
                    "targetPageKey": page_key or page_id or page_name,
                    "targetPageName": page_name or page_key or page_id,
                    "route": text(raw.get("route"), raw.get("targetRoute"), raw.get("path"), raw.get("pagePath"), raw.get("page_path")),
                    "status": "PLANNED",
                }
            else:
                return None

            target_id = text(item.get("targetPageId"))
            target_key = text(item.get("targetPageKey"))
            target_name = text(item.get("targetPageName"))
            if target_id in current_ids or target_key in current_keys or target_name in current_names:
                return None
            if norm(target_id) in current_norms or norm(target_key) in current_norms or norm(target_name) in current_norms:
                return None
            return item

        def add_target(raw: Any) -> None:
            if not isinstance(raw, dict):
                return
            item = resolve_page(raw)
            if not item:
                return
            target_id = text(item.get("targetPageId"))
            if not target_id or target_id in seen_ids:
                return
            seen_ids.add(target_id)
            result.append(
                {
                    "targetPageId": target_id,
                    "targetPageKey": text(item.get("targetPageKey"), target_id),
                    "targetPageName": text(item.get("targetPageName"), item.get("targetPageKey"), target_id),
                    "route": text(item.get("route")),
                    "status": text(item.get("status"), "PLANNED"),
                    "sourceScope": text(item.get("sourceScope"), "NAVIGATION_AUTHORITY"),
                    "actionId": text(raw.get("action_id"), raw.get("actionId"), raw.get("sourceActionId"), raw.get("sourceActionKey"), raw.get("source_action_key")),
                    "label": text(raw.get("label"), raw.get("actionLabel"), raw.get("sourceActionLabel"), raw.get("source_action_label")),
                }
            )

        for source in [
            hidden_context.get("navigation_targets"),
            hidden_context.get("navigationTargets"),
            context_package.get("navigation_candidates"),
            input_payload.get("navigation_candidates"),
        ]:
            for item in as_list(source):
                add_target(item)

        def source_matches(raw: dict[str, Any]) -> bool:
            values = [
                raw.get("sourcePageId"), raw.get("source_page_id"), raw.get("fromPageId"), raw.get("from_page_id"),
                raw.get("sourcePageKey"), raw.get("source_page_key"), raw.get("fromPageKey"), raw.get("from_page_key"),
                raw.get("sourcePageName"), raw.get("source_page_name"), raw.get("fromPageName"), raw.get("from_page_name"),
            ]
            explicit = [text(value) for value in values if text(value)]
            if not explicit:
                return True
            return any(value in current_ids or value in current_keys or value in current_names or norm(value) in current_norms for value in explicit)

        def scan_edges(value: Any) -> None:
            if isinstance(value, dict):
                for key in ["navigationEdges", "navigation_edges", "navigationRequirements", "navigation_requirements", "edges", "links", "pageLinks", "page_links", "routes"]:
                    for edge in as_list(value.get(key)):
                        if not isinstance(edge, dict) or not source_matches(edge):
                            continue
                        add_target(edge)
                for child_key in ["modulePrototypeMap", "module_prototype_map", "traceabilityMap", "traceability_map", "appShell"]:
                    child = value.get(child_key)
                    if isinstance(child, (dict, list)):
                        scan_edges(child)
            elif isinstance(value, list):
                for item in value:
                    scan_edges(item)

        for container in containers:
            scan_edges(container)

        if not result and page_index_by_key:
            current_key_norm = next(iter({norm(item) for item in current_keys if item}), "")
            current_name_text = next(iter(current_names), "")
            is_list_like = any(token in current_key_norm or token in current_name_text for token in ["list", "table", "列表", "查询"])
            if is_list_like:
                for page in list(page_index_by_key.values()):
                    key_norm = norm(page.get("targetPageKey"))
                    name_text = text(page.get("targetPageName"))
                    if any(token in key_norm or token in name_text for token in ["detail", "form", "edit", "详情", "编辑", "创建", "新增"]):
                        add_target(page)

        return result



    def _a9_10_14_4_2_2_backfill_prd_navigation_context(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any] | None = None,
        current_page: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Enhance legacy MODULE_PRD context before navigation extraction.

        Existing PRDs may only have page_requirements / functional_requirements.
        This bridge derives structuredRequirements / navigationRequirements and
        exposes the derived navigation edges as navigation_candidates so older
        and newer navigation extractors both see the same planned targets.
        """
        if not isinstance(context_package, dict):
            return context_package

        result: dict[str, Any] = dict(context_package)
        input_payload = input_payload if isinstance(input_payload, dict) else {}

        def normalize_artifacts(value: Any) -> dict[str, Any]:
            artifacts: dict[str, Any] = {}
            if isinstance(value, dict):
                for key, item in value.items():
                    normalized_key = str(key).strip().upper()
                    if isinstance(item, dict):
                        artifact_type = str(
                            item.get("artifact_type")
                            or item.get("artifactType")
                            or item.get("type")
                            or ""
                        ).strip().upper()
                        if artifact_type:
                            normalized_key = artifact_type
                    artifacts[normalized_key] = item
            elif isinstance(value, list):
                for item in value:
                    if not isinstance(item, dict):
                        continue
                    artifact_type = str(
                        item.get("artifact_type")
                        or item.get("artifactType")
                        or item.get("type")
                        or ""
                    ).strip().upper()
                    if artifact_type:
                        artifacts[artifact_type] = item.get("content") if isinstance(item.get("content"), dict) else item
            return artifacts

        artifacts = normalize_artifacts(result.get("artifacts"))
        if not artifacts:
            artifacts = normalize_artifacts(input_payload.get("artifacts"))

        module_prd = (
            artifacts.get("MODULE_PRD")
            or result.get("MODULE_PRD")
            or result.get("module_prd")
            or input_payload.get("MODULE_PRD")
            or input_payload.get("module_prd")
        )
        if not isinstance(module_prd, dict):
            return result

        enriched_prd = backfill_module_prd_semantic_backbone(
            module_prd,
            current_page=current_page or {},
        )
        if not isinstance(enriched_prd, dict):
            return result

        artifacts["MODULE_PRD"] = enriched_prd
        result["artifacts"] = artifacts
        result["MODULE_PRD"] = enriched_prd
        result["module_prd"] = enriched_prd

        existing_candidates = result.get("navigation_candidates")
        candidates: list[Any] = list(existing_candidates) if isinstance(existing_candidates, list) else []
        seen_ids = {
            str(item.get("targetPageId") or item.get("page_id") or item.get("pageId") or "").strip()
            + "::"
            + str(item.get("sourcePageId") or item.get("source_page_id") or item.get("sourcePageKey") or "").strip()
            for item in candidates
            if isinstance(item, dict)
        }

        for nav in (
            enriched_prd.get("navigationRequirements")
            or enriched_prd.get("navigation_requirements")
            or []
        ):
            if not isinstance(nav, dict):
                continue
            target_page_id = str(nav.get("targetPageId") or nav.get("pageId") or "").strip()
            if not target_page_id:
                continue
            identity = target_page_id + "::" + str(nav.get("sourcePageId") or nav.get("sourcePageKey") or "").strip()
            if identity in seen_ids:
                continue
            candidates.append({
                "page_id": target_page_id,
                "pageId": target_page_id,
                "targetPageId": target_page_id,
                "page_key": str(nav.get("targetPageKey") or nav.get("pageKey") or target_page_id),
                "pageKey": str(nav.get("targetPageKey") or nav.get("pageKey") or target_page_id),
                "targetPageKey": str(nav.get("targetPageKey") or nav.get("pageKey") or target_page_id),
                "page_name": str(nav.get("targetPageName") or nav.get("pageName") or target_page_id),
                "pageName": str(nav.get("targetPageName") or nav.get("pageName") or target_page_id),
                "targetPageName": str(nav.get("targetPageName") or nav.get("pageName") or target_page_id),
                "route": str(nav.get("targetRoute") or nav.get("route") or nav.get("path") or ""),
                "targetRoute": str(nav.get("targetRoute") or nav.get("route") or nav.get("path") or ""),
                "action_id": str(nav.get("sourceActionId") or nav.get("sourceActionKey") or nav.get("actionId") or ""),
                "actionId": str(nav.get("sourceActionId") or nav.get("sourceActionKey") or nav.get("actionId") or ""),
                "label": str(nav.get("sourceActionLabel") or nav.get("label") or ""),
                "actionLabel": str(nav.get("sourceActionLabel") or nav.get("label") or ""),
                "sourcePageId": str(nav.get("sourcePageId") or ""),
                "sourcePageKey": str(nav.get("sourcePageKey") or ""),
                "source": "MODULE_PRD_BACKFILL_NAVIGATION_REQUIREMENT",
                "status": str(nav.get("status") or "PLANNED"),
            })
            seen_ids.add(identity)

        result["navigation_candidates"] = candidates
        return result

    def _infer_page_pattern(
        self,
        page: dict[str, Any],
        features: list[dict[str, Any]],
    ) -> str:
        """
        A9.9.7 page pattern policy.

        PAGE identity and hidden pagePrototypeContext have priority over feature
        keywords. A FORM_PAGE that contains a detail table is still a form page,
        not a TABLE_LIST. Search filters belong only to true list pages.
        """
        page_text = self._join_search_text([page])
        feature_text = self._join_search_text(features)
        page_kind = str(page.get("pageKind") or "FORM_PAGE").upper()
        layout_type = str(page.get("layoutType") or "").upper()

        if self._is_login_page(page):
            return "AUTH_LOGIN"

        if self._is_register_page(page):
            return "AUTH_REGISTER"

        if page_kind in {"WORKFLOW_PAGE", "APPROVAL_PAGE"} or self._contains_any(page_text, ["审批", "流程", "工单", "流转", "workflow", "approval", "approve"]):
            return "WORKFLOW"

        if page_kind in {"UPLOAD_PAGE", "IMPORT_PAGE"} or self._contains_any(page_text, ["上传", "导入", "附件", "upload", "import", "attachment"]):
            return "UPLOAD"

        if page_kind in {"DASHBOARD_PAGE", "HOME_PAGE", "WORKBENCH"} or layout_type == "DASHBOARD":
            return "DASHBOARD"

        if page_kind in {"LIST_PAGE", "TABLE_PAGE"} or layout_type in {"TABLE_LIST", "LIST"}:
            return "SEARCH_LIST"

        if page_kind in {"DETAIL_PAGE", "PROFILE_PAGE"} or layout_type in {"DETAIL_TABS", "DETAIL"}:
            return "DETAIL_VIEW"

        if page_kind in {"SETTINGS_PAGE"} or layout_type in {"SETTINGS"}:
            return "SETTINGS_FORM"

        if page_kind == "FORM_PAGE" or layout_type in {"FORM_DETAIL", "FORM", "FORM-DETAIL"}:
            return "FORM_PAGE"

        if self._contains_any(page_text, ["首页", "home", "工作台", "workbench", "dashboard", "看板", "总览", "概览"]):
            return "DASHBOARD"

        if self._contains_any(page_text, ["列表", "查询", "搜索", "分页", "table", "list", "search"]):
            return "SEARCH_LIST"

        if self._contains_any(page_text, ["详情", "detail", "查看"]):
            return "DETAIL_VIEW"

        if self._contains_any(page_text, ["配置", "设置", "setting", "config"]):
            return "SETTINGS_FORM"

        if self._contains_any(page_text, ["创建", "新建", "新增", "编辑", "录入", "create", "edit", "form"]):
            return "FORM_PAGE"

        # Feature text may refine only generic PAGE nodes.
        if self._contains_any(feature_text, ["上传", "导入", "upload", "import"]):
            return "UPLOAD"
        if self._contains_any(feature_text, ["审批", "流程", "workflow", "approval"]):
            return "WORKFLOW"
        if self._contains_any(feature_text, ["查询", "搜索", "列表", "分页", "table", "list", "search"]):
            return "SEARCH_LIST"
        if self._contains_any(feature_text, ["设置", "配置", "setting", "config"]):
            return "SETTINGS_FORM"
        if self._contains_any(feature_text, ["指标", "统计", "总览", "看板", "dashboard"]):
            return "DASHBOARD"

        return "FORM_PAGE"

    def _is_login_page(
        self,
        page: dict[str, Any],
    ) -> bool:
        page_text = self._join_search_text([page])
        page_key = str(page.get("pageKey") or "").lower()
        page_name = str(page.get("pageName") or "").lower()
        page_kind = str(page.get("pageKind") or "").lower()

        positive = (
            page_key in {"login", "login_page", "signin", "sign_in", "auth_login"}
            or page_name in {"登录", "登录页", "登录页面"}
            or page_kind in {"login_page", "login", "auth_login"}
            or self._contains_any(page_text, ["登录页", "登录页面", "login page", "signin page", "sign in page"])
        )

        negative = self._contains_any(
            page_text,
            ["退出登录", "登录状态", "状态守卫", "session", "logout", "用户首页", "首页", "home", "工作台"],
        )

        return positive and not negative

    def _is_register_page(
        self,
        page: dict[str, Any],
    ) -> bool:
        page_text = self._join_search_text([page])
        page_key = str(page.get("pageKey") or "").lower()
        page_name = str(page.get("pageName") or "").lower()
        page_kind = str(page.get("pageKind") or "").lower()

        positive = (
            page_key in {"register", "register_page", "signup", "sign_up"}
            or page_name in {"注册", "注册页", "注册页面"}
            or page_kind in {"register_page", "register", "auth_register"}
            or self._contains_any(page_text, ["注册页", "注册页面", "register page", "signup page", "sign up page"])
        )

        negative = self._contains_any(
            page_text,
            ["注册状态", "注册成功跳转", "用户首页", "首页", "home", "工作台"],
        )

        return positive and not negative

    def _required_state_keys(
        self,
        page_pattern: str,
        features: list[dict[str, Any]],
    ) -> list[str]:
        keys = ["DEFAULT", "LOADING", "ERROR"]

        if page_pattern in {
            "LOGIN_FORM",
            "REGISTER_FORM",
            "FORM_PAGE",
            "SETTINGS_FORM",
        }:
            keys.append("SUCCESS")

        if page_pattern in {
            "SEARCH_LIST",
            "CRUD_LIST",
            "DASHBOARD",
        }:
            keys.append("EMPTY")

        text = self._join_search_text(features)

        if self._contains_any(text, ["上传", "upload"]):
            keys.append("UPLOADING")

        if self._contains_any(text, ["保存", "save"]):
            keys.append("SAVING")

        return list(dict.fromkeys(keys))

    def _forbidden_terms(
        self,
        page: dict[str, Any],
        features: list[dict[str, Any]],
        allow_navigation: bool,
    ) -> list[str]:
        text = self._join_search_text([page, *features])
        terms: list[str] = []

        is_auth_page = self._is_login_page(page) or self._is_register_page(page)

        if not is_auth_page:
            terms.extend(
                [
                    "welcome back",
                    "create account",
                    "auth page pattern",
                    "login_form",
                    "register_form",
                    "auth_card",
                    "password_login",
                    "欢迎登录",
                    "欢迎注册",
                    "账号密码登录",
                    "密码登录",
                    "请输入账号信息进入系统",
                    "请输入账号与密码进入系统",
                    "立即登录",
                    "立即注册",
                    "跳转到注册页",
                    "跳转到登录页",
                ]
            )

        if not self._contains_any(text, ["短信", "验证码", "sms", "captcha", "verification code"]):
            terms.extend(
                [
                    "sms",
                    "captcha",
                    "verification",
                    "verify_code",
                    "sms_code",
                    "countdown",
                    "短信",
                    "验证码",
                    "倒计时",
                    "获取验证码",
                ]
            )

        if not allow_navigation:
            terms.extend(
                [
                    "navigate",
                    "redirect",
                    "route_to",
                    "jump_to",
                    "跳转到",
                    "重定向到",
                ]
            )

        return list(dict.fromkeys([item for item in terms if item]))

    def _feature_is_navigation(
        self,
        item: dict[str, Any],
    ) -> bool:
        text = self._join_search_text([item])
        return self._contains_any(
            text,
            [
                "跳转",
                "导航",
                "进入",
                "打开",
                "navigate",
                "navigation",
                "redirect",
                "route",
                "link",
                "register",
                "注册",
                "详情",
                "detail",
            ],
        )

    def _join_search_text(
        self,
        items: list[dict[str, Any]] | dict[str, Any],
    ) -> str:
        if isinstance(items, dict):
            items = [items]

        values: list[str] = []
        for item in items:
            for value in item.values():
                if isinstance(value, (str, int, float, bool)):
                    values.append(str(value))
        return " ".join(values).lower()

    def _contains_any(
        self,
        text: str,
        keywords: list[str],
    ) -> bool:
        lowered = (text or "").lower()
        return any(keyword.lower() in lowered for keyword in keywords)

    def _read_extra(
        self,
        source: dict[str, Any],
        key: str,
    ) -> Any:
        extra = source.get("extra")
        if isinstance(extra, dict):
            return extra.get(key)
        return None

    def _has_text(
        self,
        value: Any,
    ) -> bool:
        return value is not None and str(value).strip() != ""
