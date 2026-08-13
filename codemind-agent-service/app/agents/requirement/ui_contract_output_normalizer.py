
from __future__ import annotations

from typing import Any

from app.agents.requirement.ui_contract_generation_plan import UiContractGenerationPlan
from app.agents.requirement.ui_contract_semantic_skeleton import UiContractSemanticSkeletonBuilder


class UiContractOutputNormalizer:
    """
    Normalizes deterministic LLM boundary issues before strict schema validation.

    This class does not weaken validation. It only applies safe, deterministic
    corrections based on the generation plan:
    - feature keys must belong to current PAGE;
    - navigation must target allowed PAGE ids;
    - interaction targetId must point to an existing component;
    - page state references must point to existing pageStates;
    - known invented capabilities are removed before Java persistence validation.
    """

    def normalize(
        self,
        value: Any,
        plan: UiContractGenerationPlan,
    ) -> dict[str, Any]:
        if not isinstance(value, dict):
            raise ValueError("UI_CONTRACT model output must be a JSON object.")

        source = dict(value)
        warnings: list[str] = []

        self._normalize_identity_and_page_metadata(source, plan)
        self._ensure_page_states(source, plan)

        page = source.get("page")
        if not isinstance(page, dict):
            page = {}
            source["page"] = page

        self._normalize_component_tree(source, plan, warnings)
        component_index = self._build_component_index(source)
        action_owner_index = self._build_action_owner_index(component_index)

        self._normalize_navigation_targets(source, plan, warnings)
        self._normalize_interactions(
            source,
            plan,
            component_index,
            action_owner_index,
            warnings,
        )

        self._remove_invalid_action_references(source)
        self._normalize_page_state_actions(source, warnings)
        self._normalize_feature_references(source, plan, warnings)
        self._rebuild_feature_coverage(source, plan)
        self._append_quality_warnings(source, warnings)
        self._strip_schema_invalid_inline_feature_keys(source)
        self._strip_schema_invalid_navigation_fields(source)

        # UI_CONTRACT v1 正式规则：
        # pageStates 只描述页面状态，不保存状态动作。
        # 状态切换统一由 interactions 和 mockScenarios 表达。
        self._strip_page_state_actions(source, warnings)

        # 最终递归兜底：
        # 处理 mockScenarios、stateTransitions、scenario steps 等其他容器中
        # 仍然可能残留的 stateKey=componentId 问题。
        self._normalize_page_state_action_references_deep(source, warnings)

        UiContractSemanticSkeletonBuilder().apply(
            source,
            plan,
            warnings,
        )
        self._append_quality_warnings(source, warnings)

        return source

    def _normalize_identity_and_page_metadata(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
    ) -> None:
        identity = source.get("identity")
        if not isinstance(identity, dict):
            identity = {}
            source["identity"] = identity

        page = source.get("page")
        if not isinstance(page, dict):
            page = {}
            source["page"] = page

        for key, value in [
            ("pageId", plan.page.get("pageId")),
            ("pageKey", plan.page.get("pageKey")),
            ("pageName", plan.page.get("pageName")),
            ("pageKind", plan.page.get("pageKind")),
        ]:
            if value:
                identity[key] = str(value)
                page[key] = str(value)

        source["schemaVersion"] = "1.0"
        source["artifactType"] = "UI_CONTRACT"

    def _ensure_page_states(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
    ) -> None:
        raw_states = source.get("pageStates")

        states: list[dict[str, Any]] = []
        seen: set[str] = set()

        if isinstance(raw_states, list):
            for item in raw_states:
                if not isinstance(item, dict):
                    continue
                state_key = self._first_text(
                    item,
                    "stateKey",
                    "state_key",
                    "key",
                    "name",
                ).upper()

                if not state_key:
                    continue

                if state_key in seen:
                    continue

                state = dict(item)
                state["stateKey"] = state_key
                state.pop("state_key", None)
                state.pop("key", None)

                states.append(state)
                seen.add(state_key)

        for state_key in plan.required_state_keys:
            normalized_key = str(state_key).upper()
            if normalized_key in seen:
                continue

            states.append(
                {
                    "stateKey": normalized_key,
                    "stateName": self._default_state_name(normalized_key),
                    "description": self._default_state_description(normalized_key),
                    "visibleComponentIds": [],
                    "disabledComponentIds": [],
                }
            )
            seen.add(normalized_key)

        source["pageStates"] = states

    def _normalize_component_tree(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
        warnings: list[str],
    ) -> None:
        page = source.get("page")
        if not isinstance(page, dict):
            return

        tree = page.get("componentTree")

        if isinstance(tree, dict):
            normalized_tree = self._normalize_component(
                tree,
                plan,
                warnings,
            )
            page["componentTree"] = normalized_tree or tree
            return

        if isinstance(tree, list):
            normalized_components = [
                component
                for item in tree
                if isinstance(item, dict)
                for component in [
                    self._normalize_component(item, plan, warnings)
                ]
                if component is not None
            ]
            page["componentTree"] = normalized_components
            return

    def _normalize_component(
        self,
        component: dict[str, Any],
        plan: UiContractGenerationPlan,
        warnings: list[str],
    ) -> dict[str, Any] | None:
        component = dict(component)

        search_text = self._search_text(component)

        if self._contains_forbidden_term(search_text, plan.forbidden_terms):
            warnings.append(
                f"已移除超出当前 PAGE 能力边界的组件："
                f"{component.get('componentId') or component.get('title') or '-'}。"
            )
            return None

        # Component schema does not allow featureKeys.
        # Feature binding is expressed only through featureCoverage.
        component.pop("featureKeys", None)
        component.pop("feature_keys", None)

        self._normalize_fields(component, plan, warnings)

        children = component.get("children")
        if isinstance(children, list):
            normalized_children: list[dict[str, Any]] = []

            for child in children:
                if not isinstance(child, dict):
                    continue

                normalized_child = self._normalize_component(
                    child,
                    plan,
                    warnings,
                )

                if normalized_child is not None:
                    normalized_children.append(normalized_child)

            component["children"] = normalized_children

        tabs = component.get("tabs")
        if isinstance(tabs, list):
            component["tabs"] = [
                tab
                for tab in tabs
                if isinstance(tab, dict)
                and not self._contains_forbidden_term(
                    self._search_text(tab),
                    plan.forbidden_terms,
                )
            ]

        return component

    def _normalize_fields(
        self,
        component: dict[str, Any],
        plan: UiContractGenerationPlan,
        warnings: list[str],
    ) -> None:
        fields = component.get("fields")
        if not isinstance(fields, list):
            return

        normalized_fields: list[dict[str, Any]] = []

        for field in fields:
            if not isinstance(field, dict):
                continue

            field_copy = dict(field)
            search_text = self._search_text(field_copy)

            if self._contains_forbidden_term(search_text, plan.forbidden_terms):
                warnings.append(
                    f"已移除超出当前 PAGE 能力边界的字段："
                    f"{field_copy.get('fieldKey') or field_copy.get('field') or field_copy.get('label') or '-'}。"
                )
                continue

            # Field schema does not allow featureKeys.
            # Feature binding is expressed only through featureCoverage.
            field_copy.pop("featureKeys", None)
            field_copy.pop("feature_keys", None)

            normalized_fields.append(field_copy)

        component["fields"] = normalized_fields

    def _normalize_navigation_targets(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
        warnings: list[str],
    ) -> None:
        raw_targets = source.get("navigationTargets")

        requested: list[dict[str, Any]] = []

        if isinstance(raw_targets, list):
            requested.extend(item for item in raw_targets if isinstance(item, dict))

        for interaction in source.get("interactions") or []:
            if not isinstance(interaction, dict):
                continue
            if self._interaction_type(interaction) != "NAVIGATE":
                continue
            requested.append(interaction)

        normalized: list[dict[str, Any]] = []
        seen_ids: set[str] = set()

        for raw in requested:
            target = self._resolve_navigation_target(raw, plan)

            if target is None:
                label = (
                    raw.get("targetPageName")
                    or raw.get("targetPageKey")
                    or raw.get("targetPageId")
                    or raw.get("label")
                    or "-"
                )
                warnings.append(f"已移除未授权或不存在的导航目标：{label}。")
                continue

            target_id = str(target.get("targetPageId") or "")
            if not target_id or target_id in seen_ids:
                continue

            seen_ids.add(target_id)
            normalized.append(dict(target))

        source["navigationTargets"] = normalized

    def _normalize_interactions(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
        component_index: dict[str, dict[str, Any]],
        action_owner_index: dict[str, str],
        warnings: list[str],
    ) -> None:
        raw_interactions = source.get("interactions")

        if not isinstance(raw_interactions, list):
            source["interactions"] = []
            return

        normalized: list[dict[str, Any]] = []
        seen_ids: set[str] = set()
        component_ids = set(component_index.keys())
        state_keys = self._state_keys(source)

        for item in raw_interactions:
            if not isinstance(item, dict):
                continue

            interaction = dict(item)
            action_id = self._first_text(interaction, "actionId", "action_id", "id")

            if not action_id or action_id in seen_ids:
                continue

            search_text = self._search_text(interaction)

            if self._contains_forbidden_term(search_text, plan.forbidden_terms):
                warnings.append(
                    f"已移除超出当前 PAGE 能力边界的动作：{action_id}。"
                )
                continue

            action_type = self._interaction_type(interaction)
            interaction["actionId"] = action_id
            interaction["type"] = action_type

            interaction_feature_keys = self._normalize_feature_key_list(
                interaction.get("featureKeys"),
                plan,
                search_text=search_text,
            )

            if not interaction_feature_keys:
                interaction_feature_keys = self._infer_feature_keys_from_text(
                    action_id + " " + search_text,
                    plan,
                )

            # Interaction schema does not allow featureKeys.
            # Keep feature inference internal and rebuild featureCoverage later.
            interaction.pop("featureKeys", None)
            interaction.pop("feature_keys", None)

            if action_type == "NAVIGATE":
                if not plan.allow_navigation:
                    warnings.append(
                        f"已移除当前 PAGE 未授权的跳转动作：{action_id}。"
                    )
                    continue

                target = self._resolve_navigation_target(interaction, plan)
                if target is None:
                    warnings.append(
                        f"已移除没有真实目标页面的跳转动作：{action_id}。"
                    )
                    continue

                interaction["targetPageId"] = target["targetPageId"]
            else:
                interaction.pop("targetPageId", None)

            interaction.pop("target_page_id", None)
            interaction.pop("targetPageKey", None)
            interaction.pop("target_page_key", None)
            interaction.pop("targetPageName", None)
            interaction.pop("target_page_name", None)

            target_id = self._first_text(
                interaction,
                "targetId",
                "target_id",
                "componentId",
                "component_id",
            )

            if not target_id:
                target_id = action_owner_index.get(action_id, "")

            if target_id and target_id not in component_ids:
                fallback_target = action_owner_index.get(action_id, "")
                if fallback_target in component_ids:
                    target_id = fallback_target

            if action_type != "NAVIGATE":
                if not target_id or target_id not in component_ids:
                    warnings.append(
                        f"已移除缺少有效 targetId 的动作：{action_id}。"
                    )
                    continue

                interaction["targetId"] = target_id

            else:
                if target_id and target_id in component_ids:
                    interaction["targetId"] = target_id
                elif action_owner_index.get(action_id) in component_ids:
                    interaction["targetId"] = action_owner_index[action_id]

            target_state_key = self._first_text(
                interaction,
                "targetStateKey",
                "target_state_key",
                "stateKey",
                "state_key",
            ).upper()

            if target_state_key:
                if target_state_key not in state_keys:
                    semantic_state = self._semantic_state_for_action(
                        action_id,
                        action_type,
                        state_keys,
                    )
                    if semantic_state:
                        interaction["targetStateKey"] = semantic_state
                    elif target_state_key in component_ids:
                        interaction.pop("targetStateKey", None)
                        interaction.pop("stateKey", None)
                    else:
                        warnings.append(
                            f"动作 {action_id} 引用了不存在的页面状态：{target_state_key}。"
                        )
                        interaction.pop("targetStateKey", None)
                        interaction.pop("stateKey", None)
                else:
                    interaction["targetStateKey"] = target_state_key

            interaction.pop("target_id", None)
            interaction.pop("componentId", None)
            interaction.pop("component_id", None)
            interaction.pop("target_state_key", None)
            interaction.pop("stateKey", None)
            interaction.pop("state_key", None)

            normalized.append(interaction)
            seen_ids.add(action_id)

        source["interactions"] = normalized

    def _remove_invalid_action_references(
        self,
        source: dict[str, Any],
    ) -> None:
        valid_action_ids = {
            str(item.get("actionId"))
            for item in source.get("interactions") or []
            if isinstance(item, dict) and item.get("actionId")
        }

        for component in self._walk_components(source):
            action_ids = component.get("actionIds")
            if isinstance(action_ids, list):
                component["actionIds"] = [
                    str(action_id)
                    for action_id in action_ids
                    if str(action_id) in valid_action_ids
                ]

            row_action_ids = component.get("rowActionIds")
            if isinstance(row_action_ids, list):
                component["rowActionIds"] = [
                    str(action_id)
                    for action_id in row_action_ids
                    if str(action_id) in valid_action_ids
                ]

    def _normalize_page_state_actions(
        self,
        source: dict[str, Any],
        warnings: list[str],
    ) -> None:
        valid_action_ids = {
            str(item.get("actionId"))
            for item in source.get("interactions") or []
            if isinstance(item, dict) and item.get("actionId")
        }

        state_keys = self._state_keys(source)

        for state in source.get("pageStates") or []:
            if not isinstance(state, dict):
                continue

            for key in [
                "actions",
                "stateActions",
                "entryActions",
                "exitActions",
                "onEnter",
                "onExit",
            ]:
                actions = state.get(key)
                if not isinstance(actions, list):
                    continue

                normalized_actions: list[dict[str, Any]] = []

                for action in actions:
                    if not isinstance(action, dict):
                        continue

                    action_id = self._first_text(action, "actionId", "action_id")
                    if action_id and action_id not in valid_action_ids:
                        warnings.append(
                            f"已移除引用不存在 interaction 的页面状态动作：{action_id}。"
                        )
                        continue

                    state_ref = self._first_text(
                        action,
                        "targetStateKey",
                        "target_state_key",
                        "stateKey",
                        "state_key",
                    ).upper()

                    if state_ref and state_ref not in state_keys:
                        warnings.append(
                            f"已移除引用不存在 stateKey 的页面状态动作：{action_id or '-'}。"
                        )
                        continue

                    normalized_actions.append(action)

                state[key] = normalized_actions

    def _normalize_feature_references(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
        warnings: list[str],
    ) -> None:
        """
        Remove schema-invalid inline feature references.

        UI_CONTRACT v1 expresses feature binding only through:
        - featureCoverage[].componentIds
        - featureCoverage[].actionIds

        Component, field and interaction objects must not contain featureKeys.
        """
        for component in self._walk_components(source):
            component.pop("featureKeys", None)
            component.pop("feature_keys", None)

            fields = component.get("fields")
            if isinstance(fields, list):
                for field in fields:
                    if isinstance(field, dict):
                        field.pop("featureKeys", None)
                        field.pop("feature_keys", None)

        for interaction in source.get("interactions") or []:
            if isinstance(interaction, dict):
                interaction.pop("featureKeys", None)
                interaction.pop("feature_keys", None)

    def _rebuild_feature_coverage(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
    ) -> None:
        component_ids_by_feature: dict[str, set[str]] = {
            key: set()
            for key in plan.allowed_feature_keys
        }
        action_ids_by_feature: dict[str, set[str]] = {
            key: set()
            for key in plan.allowed_feature_keys
        }

        for component in self._walk_components(source):
            component_id = str(component.get("componentId") or "")
            if not component_id:
                continue

            inferred_feature_keys = self._infer_feature_keys_from_text(
                self._search_text(component),
                plan,
            )

            for feature_key in inferred_feature_keys:
                if feature_key in component_ids_by_feature:
                    component_ids_by_feature[feature_key].add(component_id)

            fields = component.get("fields")
            if isinstance(fields, list):
                for field in fields:
                    if not isinstance(field, dict):
                        continue

                    field_feature_keys = self._infer_feature_keys_from_text(
                        self._search_text(field),
                        plan,
                    )

                    for feature_key in field_feature_keys:
                        if feature_key in component_ids_by_feature:
                            component_ids_by_feature[feature_key].add(component_id)

        for interaction in source.get("interactions") or []:
            if not isinstance(interaction, dict):
                continue

            action_id = str(interaction.get("actionId") or "")
            target_id = str(interaction.get("targetId") or "")

            if not action_id:
                continue

            feature_keys = self._infer_feature_keys_from_text(
                self._search_text(interaction),
                plan,
            )

            for feature_key in feature_keys:
                if feature_key in action_ids_by_feature:
                    action_ids_by_feature[feature_key].add(action_id)
                    if target_id:
                        component_ids_by_feature[feature_key].add(target_id)

        all_component_ids = [
            str(component.get("componentId") or "")
            for component in self._walk_components(source)
            if component.get("componentId")
        ]

        all_action_ids = [
            str(action.get("actionId") or "")
            for action in source.get("interactions") or []
            if isinstance(action, dict) and action.get("actionId")
        ]

        coverage: list[dict[str, Any]] = []

        for feature in plan.features:
            feature_key = str(feature.get("featureKey") or "")
            if not feature_key:
                continue

            component_ids = sorted(component_ids_by_feature.get(feature_key) or [])
            action_ids = sorted(action_ids_by_feature.get(feature_key) or [])

            if not component_ids and all_component_ids:
                inferred_component = self._best_component_for_feature(
                    feature,
                    source,
                )
                if inferred_component:
                    component_ids = [inferred_component]

            if not action_ids and all_action_ids:
                inferred_action = self._best_action_for_feature(
                    feature,
                    source,
                )
                if inferred_action:
                    action_ids = [inferred_action]

            status = "FULL" if component_ids and action_ids else "PARTIAL"

            coverage.append(
                {
                    "featureId": str(feature.get("featureId") or ""),
                    "featureKey": feature_key,
                    "featureName": str(feature.get("featureName") or feature_key),
                    "status": status,
                    "componentIds": component_ids,
                    "actionIds": action_ids,
                    "notes": [
                        "由当前 UI_CONTRACT 引用归一化层根据真实 PAGE FEATURE 重建。"
                    ],
                }
            )

        source["featureCoverage"] = coverage

    def _append_quality_warnings(
        self,
        source: dict[str, Any],
        warnings: list[str],
    ) -> None:
        if not warnings:
            return

        quality = source.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            source["qualitySummary"] = quality

        existing = quality.get("warnings")
        if not isinstance(existing, list):
            existing = []

        quality["warnings"] = [
            *existing,
            *[
                warning
                for warning in warnings
                if warning not in existing
            ],
        ]

    def _strip_schema_invalid_inline_feature_keys(
        self,
        source: dict[str, Any],
    ) -> None:
        """
        Defensive final cleanup before Pydantic validation.

        UI_CONTRACT v1 schema forbids featureKeys in component, field and
        interaction objects.
        """
        for component in self._walk_components(source):
            component.pop("featureKeys", None)
            component.pop("feature_keys", None)

            fields = component.get("fields")
            if isinstance(fields, list):
                for field in fields:
                    if isinstance(field, dict):
                        field.pop("featureKeys", None)
                        field.pop("feature_keys", None)

        for interaction in source.get("interactions") or []:
            if isinstance(interaction, dict):
                interaction.pop("featureKeys", None)
                interaction.pop("feature_keys", None)

    def _strip_schema_invalid_navigation_fields(
        self,
        source: dict[str, Any],
    ) -> None:
        """
        Defensive final cleanup for navigationTargets.
        """
        raw_targets = source.get("navigationTargets")
        if not isinstance(raw_targets, list):
            return

        source["navigationTargets"] = [
            self._navigation_target_schema_dict(item)
            for item in raw_targets
            if isinstance(item, dict)
        ]

    def _strip_page_state_actions(
            self,
            source: dict[str, Any],
            warnings: list[str],
    ) -> None:
        """
        UI_CONTRACT v1 rule:
        pageStates are declarative state descriptions only.

        State transitions must be represented by:
        - interactions
        - mockScenarios
        - runtime event handling

        Do not persist action-like objects inside pageStates, because models often
        confuse componentId and stateKey there. Java Validator also treats these
        references strictly.
        """
        raw_states = source.get("pageStates")

        if not isinstance(raw_states, list):
            return

        action_keys = [
            "actions",
            "stateActions",
            "entryActions",
            "exitActions",
            "onEnter",
            "onExit",
            "transitions",
            "stateTransitions",
        ]

        removed_count = 0

        for state in raw_states:
            if not isinstance(state, dict):
                continue

            for key in action_keys:
                value = state.get(key)

                if isinstance(value, list) and value:
                    removed_count += len(value)

                if key in state:
                    state.pop(key, None)

        if removed_count > 0:
            warnings.append(
                "已按 UI_CONTRACT v1 规则清理 pageStates 内部状态动作；"
                "状态切换统一由 interactions 和 mockScenarios 表达。"
            )

    def _normalize_page_state_action_references_deep(
        self,
        source: dict[str, Any],
        warnings: list[str],
    ) -> None:
        """
        Defensive final pass for page-state action references.

        Java persistence validation checks page-state actions outside the
        ordinary interaction list as well. Some models incorrectly put a form
        componentId such as "login_form" into stateKey. That is not a page
        state. This pass fixes action-like objects wherever they appear:
        - stateKey / targetStateKey must reference pageStates[].stateKey;
        - when a componentId is found in stateKey, move it to targetId;
        - infer SUCCESS / ERROR / LOADING / DEFAULT from actionId/type/label.
        """
        state_keys = self._state_keys(source)
        component_ids = {
            str(component.get("componentId") or "")
            for component in self._walk_components(source)
            if component.get("componentId")
        }

        if not state_keys:
            return

        def normalize_action_like(item: dict[str, Any]) -> bool:
            action_id = self._first_text(
                item,
                "actionId",
                "action_id",
                "actionKey",
                "action_key",
                "id",
                "key",
                "name",
            )

            action_type = self._first_text(
                item,
                "type",
                "actionType",
                "action_type",
            )

            label = self._first_text(
                item,
                "label",
                "title",
                "name",
            )

            has_state_reference = any(
                key in item
                for key in [
                    "targetStateKey",
                    "target_state_key",
                    "stateKey",
                    "state_key",
                ]
            )

            if not action_id and not action_type and not has_state_reference:
                return True

            raw_state_key = self._first_text(
                item,
                "targetStateKey",
                "target_state_key",
                "stateKey",
                "state_key",
            )

            if not raw_state_key:
                return True

            normalized_state_key = raw_state_key.upper()

            if normalized_state_key in state_keys:
                self._write_state_key_back(
                    item,
                    normalized_state_key,
                )
                return True

            semantic_state = self._semantic_state_for_action(
                f"{action_id} {action_type} {label}",
                action_type,
                state_keys,
            )

            if raw_state_key in component_ids:
                target_id = self._first_text(
                    item,
                    "targetId",
                    "target_id",
                    "componentId",
                    "component_id",
                )

                if not target_id:
                    item["targetId"] = raw_state_key

                if semantic_state:
                    self._write_state_key_back(
                        item,
                        semantic_state,
                    )
                    warnings.append(
                        f"已将页面状态动作 {action_id or action_type} "
                        f"中的组件 ID stateKey={raw_state_key} 修正为 {semantic_state}。"
                    )
                    return True

                self._remove_state_key_fields(item)
                warnings.append(
                    f"已清理页面状态动作 {action_id or action_type} "
                    f"中错误的组件型 stateKey={raw_state_key}。"
                )
                return True

            if semantic_state:
                self._write_state_key_back(
                    item,
                    semantic_state,
                )
                warnings.append(
                    f"已将页面状态动作 {action_id or action_type} "
                    f"中不存在的 stateKey={raw_state_key} 修正为 {semantic_state}。"
                )
                return True

            warnings.append(
                f"已移除引用不存在 stateKey={raw_state_key} 的页面状态动作："
                f"{action_id or action_type or '-'}。"
            )
            return False

        def walk(value: Any) -> Any:
            if isinstance(value, list):
                normalized_items: list[Any] = []

                for item in value:
                    if isinstance(item, dict):
                        if not normalize_action_like(item):
                            continue
                        normalized_items.append(walk(item))
                    else:
                        normalized_items.append(walk(item))

                return normalized_items

            if isinstance(value, dict):
                if not normalize_action_like(value):
                    return {}

                for key, child in list(value.items()):
                    value[key] = walk(child)

                return value

            return value

        walk(source)

    def _write_state_key_back(
            self,
            item: dict[str, Any],
            state_key: str,
    ) -> None:
        """
        Rewrite every existing state-reference field to the validated state_key.

        Important:
        Some LLM outputs contain both:
        - targetStateKey = SUCCESS
        - stateKey = login_form

        The old implementation updated only the first matched field and left
        stateKey=login_form behind, which was then rejected by Java Validator.
        """
        touched = False

        if "stateKey" in item:
            item["stateKey"] = state_key
            touched = True

        if "targetStateKey" in item:
            item["targetStateKey"] = state_key
            touched = True

        if "state_key" in item:
            item["stateKey"] = state_key
            item.pop("state_key", None)
            touched = True

        if "target_state_key" in item:
            item["targetStateKey"] = state_key
            item.pop("target_state_key", None)
            touched = True

        if not touched:
            item["stateKey"] = state_key

    def _remove_state_key_fields(
        self,
        item: dict[str, Any],
    ) -> None:
        item.pop("targetStateKey", None)
        item.pop("target_state_key", None)
        item.pop("stateKey", None)
        item.pop("state_key", None)

    def _build_component_index(
        self,
        source: dict[str, Any],
    ) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}

        for component in self._walk_components(source):
            component_id = str(component.get("componentId") or "")
            if component_id:
                result[component_id] = component

        return result

    def _build_action_owner_index(
        self,
        component_index: dict[str, dict[str, Any]],
    ) -> dict[str, str]:
        result: dict[str, str] = {}

        for component_id, component in component_index.items():
            for action_id in component.get("actionIds") or []:
                result.setdefault(str(action_id), component_id)

            for action_id in component.get("rowActionIds") or []:
                result.setdefault(str(action_id), component_id)

            actions = component.get("actions")
            if isinstance(actions, list):
                for action in actions:
                    if isinstance(action, dict) and action.get("actionId"):
                        result.setdefault(str(action["actionId"]), component_id)

        return result

    def _walk_components(
        self,
        source: dict[str, Any],
    ) -> list[dict[str, Any]]:
        page = source.get("page")
        if not isinstance(page, dict):
            return []

        tree = page.get("componentTree")

        roots: list[dict[str, Any]] = []

        if isinstance(tree, dict):
            roots = [tree]
        elif isinstance(tree, list):
            roots = [item for item in tree if isinstance(item, dict)]

        result: list[dict[str, Any]] = []

        def visit(component: dict[str, Any]) -> None:
            result.append(component)

            children = component.get("children")
            if isinstance(children, list):
                for child in children:
                    if isinstance(child, dict):
                        visit(child)

        for root in roots:
            visit(root)

        return result

    def _resolve_navigation_target(
        self,
        source: dict[str, Any],
        plan: UiContractGenerationPlan,
    ) -> dict[str, Any] | None:
        if not plan.allow_navigation:
            return None

        target_id = self._first_text(
            source,
            "targetPageId",
            "target_page_id",
            "pageId",
            "page_id",
        )

        target_key = self._first_text(
            source,
            "targetPageKey",
            "target_page_key",
            "pageKey",
            "page_key",
        )

        target_name = self._first_text(
            source,
            "targetPageName",
            "target_page_name",
            "pageName",
            "page_name",
            "label",
        )

        for target in plan.navigation_targets:
            if target_id and target_id == target.get("targetPageId"):
                return target

        for target in plan.navigation_targets:
            if target_key and target_key == target.get("targetPageKey"):
                return target

        for target in plan.navigation_targets:
            if target_name and target_name == target.get("targetPageName"):
                return target

        search_text = self._search_text(source)
        for target in plan.navigation_targets:
            if target.get("targetPageName") and str(target["targetPageName"]) in search_text:
                return target
            if target.get("targetPageKey") and str(target["targetPageKey"]) in search_text:
                return target

        return None

    def _navigation_target_schema_dict(
        self,
        target: dict[str, Any],
    ) -> dict[str, Any]:
        """
        Keep only fields accepted by UiContractNavigationTarget.

        Context-only hints such as sameParent/sourceScope must never be saved
        into UI_CONTRACT.
        """
        result: dict[str, Any] = {}

        for key in [
            "targetPageId",
            "targetPageKey",
            "targetPageName",
            "route",
            "description",
        ]:
            value = target.get(key)
            if value is not None and str(value).strip():
                result[key] = value

        return result

    def _normalize_feature_key_list(
        self,
        raw_value: Any,
        plan: UiContractGenerationPlan,
        *,
        search_text: str,
    ) -> list[str]:
        raw_items: list[Any] = []

        if isinstance(raw_value, list):
            raw_items.extend(raw_value)
        elif isinstance(raw_value, str):
            raw_items.append(raw_value)

        result: list[str] = []

        for raw in raw_items:
            resolved = self._resolve_feature_key(str(raw), plan)
            if resolved and resolved not in result:
                result.append(resolved)

        if result:
            return result

        return self._infer_feature_keys_from_text(search_text, plan)

    def _resolve_feature_key(
        self,
        value: str,
        plan: UiContractGenerationPlan,
    ) -> str:
        raw = (value or "").strip()
        if not raw:
            return ""

        lowered = raw.lower()

        for key in plan.allowed_feature_keys:
            if lowered == key.lower():
                return key

        for feature in plan.features:
            feature_key = str(feature.get("featureKey") or "")
            feature_name = str(feature.get("featureName") or "")

            if lowered == feature_name.lower():
                return feature_key

            if lowered.endswith("_" + feature_key.lower()):
                return feature_key

            if feature_key.lower() in lowered:
                return feature_key

            if feature_name and feature_name.lower() in lowered:
                return feature_key

        return ""

    def _infer_feature_keys_from_text(
        self,
        text: str,
        plan: UiContractGenerationPlan,
    ) -> list[str]:
        lowered = (text or "").lower()
        result: list[str] = []

        for feature in plan.features:
            feature_key = str(feature.get("featureKey") or "")
            feature_name = str(feature.get("featureName") or "")

            if not feature_key:
                continue

            if feature_key.lower() in lowered:
                result.append(feature_key)
                continue

            if feature_name and feature_name.lower() in lowered:
                result.append(feature_key)
                continue

            if lowered.endswith("_" + feature_key.lower()):
                result.append(feature_key)
                continue

            if self._semantic_feature_match(lowered, feature_name, feature_key):
                result.append(feature_key)

        return list(dict.fromkeys(result))

    def _semantic_feature_match(
        self,
        text: str,
        feature_name: str,
        feature_key: str,
    ) -> bool:
        name = feature_name.lower()
        key = feature_key.lower()

        if any(item in text for item in ["login", "登录", "sign_in", "signin"]):
            if any(item in name or item in key for item in ["login", "登录"]):
                return True

        if any(item in text for item in ["register", "注册", "sign_up", "signup"]):
            if any(item in name or item in key for item in ["register", "注册"]):
                return True

        if any(item in text for item in ["save", "保存"]):
            if any(item in name or item in key for item in ["save", "保存"]):
                return True

        if any(item in text for item in ["query", "search", "查询", "搜索"]):
            if any(item in name or item in key for item in ["query", "search", "查询", "搜索"]):
                return True

        return False

    def _best_component_for_feature(
        self,
        feature: dict[str, Any],
        source: dict[str, Any],
    ) -> str:
        text = self._search_text(feature)

        for component in self._walk_components(source):
            component_id = str(component.get("componentId") or "")
            if not component_id:
                continue

            component_text = self._search_text(component)
            if any(part and part in component_text for part in text.split()):
                return component_id

        components = self._walk_components(source)
        return str(components[0].get("componentId") or "") if components else ""

    def _best_action_for_feature(
        self,
        feature: dict[str, Any],
        source: dict[str, Any],
    ) -> str:
        feature_text = self._search_text(feature)

        for action in source.get("interactions") or []:
            if not isinstance(action, dict):
                continue
            action_id = str(action.get("actionId") or "")
            action_text = self._search_text(action)
            if any(part and part in action_text for part in feature_text.split()):
                return action_id

        interactions = [
            action
            for action in source.get("interactions") or []
            if isinstance(action, dict) and action.get("actionId")
        ]
        return str(interactions[0].get("actionId") or "") if interactions else ""

    def _state_keys(
        self,
        source: dict[str, Any],
    ) -> set[str]:
        result: set[str] = set()

        for state in source.get("pageStates") or []:
            if isinstance(state, dict):
                state_key = str(state.get("stateKey") or "").upper()
                if state_key:
                    result.add(state_key)

        return result

    def _semantic_state_for_action(
        self,
        action_id: str,
        action_type: str,
        state_keys: set[str],
    ) -> str:
        text = f"{action_id} {action_type}".lower()

        pairs = [
            ("SUCCESS", ["success", "成功"]),
            ("ERROR", ["error", "fail", "失败", "错误"]),
            ("LOADING", ["loading", "submit", "login", "保存", "提交"]),
            ("EMPTY", ["empty", "空"]),
        ]

        for state_key, keywords in pairs:
            if state_key in state_keys and any(keyword in text for keyword in keywords):
                return state_key

        return ""

    def _interaction_type(
        self,
        source: dict[str, Any],
    ) -> str:
        value = self._first_text(source, "type", "actionType", "action_type")
        if not value:
            return "CUSTOM"
        return value.strip().upper()

    def _contains_forbidden_term(
        self,
        text: str,
        forbidden_terms: list[str],
    ) -> bool:
        lowered = (text or "").lower()

        return any(
            term.lower() in lowered
            for term in forbidden_terms
            if term and term.lower()
        )

    def _search_text(
        self,
        source: dict[str, Any],
    ) -> str:
        values: list[str] = []

        def visit(value: Any) -> None:
            if value is None:
                return

            if isinstance(value, (str, int, float, bool)):
                values.append(str(value))
                return

            if isinstance(value, list):
                for item in value:
                    visit(item)
                return

            if isinstance(value, dict):
                for item in value.values():
                    visit(item)

        visit(source)
        return " ".join(values).lower()

    def _first_text(
        self,
        source: dict[str, Any],
        *keys: str,
    ) -> str:
        for key in keys:
            value = source.get(key)
            if value is not None and str(value).strip():
                return str(value).strip()
        return ""

    def _default_state_name(
        self,
        state_key: str,
    ) -> str:
        return {
            "DEFAULT": "默认态",
            "LOADING": "加载中",
            "ERROR": "错误态",
            "SUCCESS": "成功态",
            "EMPTY": "空状态",
            "UPLOADING": "上传中",
            "SAVING": "保存中",
        }.get(state_key, state_key)

    def _default_state_description(
        self,
        state_key: str,
    ) -> str:
        return {
            "DEFAULT": "页面初始可交互状态。",
            "LOADING": "请求或提交正在处理中，核心操作暂时禁用。",
            "ERROR": "操作失败或校验未通过时显示错误信息。",
            "SUCCESS": "提交或关键动作成功后的反馈状态。",
            "EMPTY": "列表或数据区域无可展示内容。",
            "UPLOADING": "文件上传处理中。",
            "SAVING": "保存处理中。",
        }.get(state_key, "由 UI_CONTRACT 归一化层补齐的页面状态。")
