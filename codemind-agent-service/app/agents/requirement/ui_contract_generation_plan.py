from __future__ import annotations

from dataclasses import dataclass
from typing import Any


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

        allow_navigation = any(
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
        for source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
        ]:
            if isinstance(source, dict):
                return {
                    "pageId": str(
                        source.get("module_id")
                        or source.get("moduleId")
                        or source.get("id")
                        or getattr(request, "module_id", "")
                        or ""
                    ),
                    "pageKey": str(
                        source.get("module_key")
                        or source.get("moduleKey")
                        or source.get("key")
                        or ""
                    ),
                    "pageName": str(
                        source.get("module_name")
                        or source.get("moduleName")
                        or source.get("name")
                        or ""
                    ),
                    "pageKind": str(
                        self._read_extra(source, "pageKind")
                        or source.get("pageKind")
                        or "FORM_PAGE"
                    ).upper(),
                }

        return {
            "pageId": str(getattr(request, "module_id", "") or ""),
            "pageKey": "",
            "pageName": "",
            "pageKind": "FORM_PAGE",
        }

    def _extract_features(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> list[dict[str, Any]]:
        candidates: list[dict[str, Any]] = []

        for source in [
            context_package.get("feature_nodes"),
            input_payload.get("feature_nodes"),
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
                        or extra.get("description")
                        or extra.get("featureDescription")
                        or ""
                    ),
                    "trigger": str(
                        item.get("trigger")
                        or extra.get("trigger")
                        or extra.get("triggerCondition")
                        or extra.get("userTrigger")
                        or ""
                    ),
                    "interactionType": str(
                        item.get("interactionType")
                        or item.get("interaction_type")
                        or extra.get("interactionType")
                        or extra.get("interaction_type")
                        or extra.get("actionType")
                        or ""
                    ),
                    "expectedResult": str(
                        item.get("expectedResult")
                        or item.get("expected_result")
                        or extra.get("expectedResult")
                        or extra.get("expected_result")
                        or extra.get("result")
                        or ""
                    ),
                }
            )

        return result

    def _extract_navigation_targets(
        self,
        context_package: dict[str, Any],
    ) -> list[dict[str, Any]]:
        raw_items = context_package.get("navigation_candidates")
        if not isinstance(raw_items, list):
            return []

        result: list[dict[str, Any]] = []
        seen_ids: set[str] = set()

        for item in raw_items:
            if not isinstance(item, dict):
                continue

            page_id = str(
                item.get("page_id")
                or item.get("pageId")
                or item.get("targetPageId")
                or item.get("id")
                or ""
            ).strip()

            if not page_id or page_id in seen_ids:
                continue

            seen_ids.add(page_id)

            result.append(
                {
                    "targetPageId": page_id,
                    "targetPageKey": str(
                        item.get("page_key")
                        or item.get("pageKey")
                        or item.get("targetPageKey")
                        or item.get("key")
                        or ""
                    ),
                    "targetPageName": str(
                        item.get("page_name")
                        or item.get("pageName")
                        or item.get("targetPageName")
                        or item.get("name")
                        or ""
                    ),
                    "route": str(
                        item.get("route")
                        or item.get("page_path")
                        or item.get("pagePath")
                        or item.get("path")
                        or ""
                    ),
                    # Internal hint only; do not expose sameParent into UI_CONTRACT schema payload.
                    # The output schema does not allow navigationTargets.sameParent.
                    "sourceScope": "SAME_PARENT"
                    if bool(
                        item.get("same_parent")
                        or item.get("sameParent")
                        or False
                    )
                    else "CANDIDATE",
                }
            )

        return result

    def _infer_page_pattern(
        self,
        page: dict[str, Any],
        features: list[dict[str, Any]],
    ) -> str:
        text = self._join_search_text([page, *features])

        if self._contains_any(text, ["登录", "login", "sign in", "signin"]):
            return "LOGIN_FORM"

        if self._contains_any(text, ["注册", "register", "sign up", "signup"]):
            return "REGISTER_FORM"

        if self._contains_any(text, ["列表", "查询", "搜索", "分页", "table", "list", "search"]):
            return "SEARCH_LIST"

        if self._contains_any(text, ["详情", "detail", "profile", "资料"]):
            return "DETAIL_VIEW"

        if self._contains_any(text, ["配置", "设置", "setting", "config"]):
            return "SETTINGS_FORM"

        if self._contains_any(text, ["看板", "dashboard", "统计", "指标"]):
            return "DASHBOARD"

        return str(page.get("pageKind") or "FORM_PAGE").upper()

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
                    "跳转",
                    "进入",
                ]
            )

        if self._contains_any(
            self._join_search_text([page]),
            ["登录", "login", "sign in", "signin"],
        ):
            if not self._contains_any(text, ["订单", "order"]):
                terms.extend(["order", "订单", "订单列表"])
            if not self._contains_any(text, ["工作台", "dashboard", "首页", "home"]):
                terms.extend(["dashboard", "workbench", "home", "首页", "工作台"])

            if not self._contains_any(text, ["切换登录方式", "登录方式切换", "tab", "tabs"]):
                terms.extend(["switch_to_password_tab", "switch_to_sms_tab", "login_method_tabs"])

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
