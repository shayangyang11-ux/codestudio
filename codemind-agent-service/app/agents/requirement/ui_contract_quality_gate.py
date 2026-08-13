
from __future__ import annotations
import re

from dataclasses import dataclass, field
from typing import Any

from app.schemas.ui_contract import UiContractArtifact


class UiContractQualityGateFailure(RuntimeError):
    """Raised when a generated UI_CONTRACT is structurally unsafe to persist."""

    def __init__(self, blockers: list[str], warnings: list[str] | None = None) -> None:
        self.blockers = blockers
        self.warnings = warnings or []
        super().__init__(
            "UI_CONTRACT_QUALITY_GATE_FAILED\n"
            + "\n".join(f"- {item}" for item in blockers)
        )


@dataclass
class UiContractQualityGateReport:
    blockers: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.blockers


class UiContractQualityGate:
    """
    A9.10.14.1 Agent-first UI quality gate.

    This class is deliberately deterministic and runs after the LLM output has
    been normalized/hardened.  It is not a CSS beautifier.  It checks whether the
    UI_CONTRACT is good enough to become the PAGE current version and later the
    MODULE development baseline.
    """

    TECHNICAL_VISIBLE_TERMS = [
        "UI_CONTRACT",
        "Contract",
        "Mock",
        "component",
        "featureCoverage",
        "schema",
        "development",
        "开发说明",
        "契约",
        "组件说明",
    ]

    SAFE_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_\-:.]*$")

    def enforce(
        self,
        artifact: UiContractArtifact,
        *,
        raise_on_blocker: bool = True,
    ) -> UiContractArtifact:
        report = self.audit(artifact)

        if report.blockers and raise_on_blocker:
            raise UiContractQualityGateFailure(report.blockers, report.warnings)

        return self._attach_report_to_quality_summary(artifact, report)

    def audit(
        self,
        artifact: UiContractArtifact,
    ) -> UiContractQualityGateReport:
        data = artifact.model_dump(
            mode="python",
            by_alias=True,
            exclude_none=False,
        )

        report = UiContractQualityGateReport()
        page = self._as_dict(data.get("page"))
        components = self._collect_components(page.get("componentTree"))
        component_index = self._component_index(components)
        interactions = self._as_list(data.get("interactions"))
        action_index = self._action_index(interactions)
        mock_data = self._as_dict(data.get("mockData"))
        page_states = self._as_list(data.get("pageStates"))
        navigation_targets = self._as_list(data.get("navigationTargets"))
        quality = self._as_dict(data.get("qualitySummary"))

        self._check_industry_page_quality_contract(data, page, components, report)
        self._check_visual_design_system_contract(data, page, components, report)

        self._check_basic_contract_shape(
            data,
            page,
            components,
            component_index,
            action_index,
            page_states,
            quality,
            report,
        )
        self._check_component_quality(
            components,
            component_index,
            action_index,
            mock_data,
            report,
        )
        self._check_interaction_closure(
            interactions,
            component_index,
            action_index,
            navigation_targets,
            page_states,
            report,
        )
        self._check_page_pattern_contract(
            page,
            components,
            action_index,
            mock_data,
            report,
        )
        self._check_visible_copy(
            page,
            components,
            report,
        )

        return report

    # A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM: deterministic visual contract gate.
    def _check_visual_design_system_contract(
            self,
            data: dict[str, Any],
            page: dict[str, Any],
            components: list[dict[str, Any]],
            report: UiContractQualityGateReport,
    ) -> None:
        mock_data = self._as_dict(data.get("mockData"))
        design_system = self._as_dict(mock_data.get("__a9VisualDesignSystem"))
        if not design_system:
            report.blockers.append("mockData.__a9VisualDesignSystem is required for 15.4.11 visual quality system.")
            return

        if str(design_system.get("version") or "") != "A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM":
            report.warnings.append("visual design system version is missing or not current.")

        tokens = self._as_dict(design_system.get("tokens"))
        for key in ["palette", "typography", "radius", "shadow", "spacing"]:
            if not isinstance(tokens.get(key), dict):
                report.blockers.append(f"visual design tokens missing section: {key}.")

        pattern_rules = self._as_dict(design_system.get("patternRules"))
        if not pattern_rules.get("layout"):
            report.blockers.append("visual design patternRules.layout is required.")

        layout = self._as_dict(page.get("layout"))
        if not self._as_list(layout.get("regions")):
            report.warnings.append("page.layout.regions is empty; visual region traceability is weak.")

        required_component_types = {
            "PAGE_HEADER", "SECTION", "CARD", "FORM", "FIELD_GROUP", "TABLE", "TOOLBAR", "ALERT", "EMPTY_STATE"
        }
        for component in components:
            component_type = str(component.get("type") or "").upper()
            if component_type not in required_component_types:
                continue

            component_id = str(component.get("componentId") or "").strip()
            props = self._as_dict(component.get("props"))
            if not isinstance(props.get("visualDesignTokens"), dict):
                report.blockers.append(f"{component_id}: props.visualDesignTokens is required.")
            if not isinstance(props.get("layoutPolish"), dict):
                report.blockers.append(f"{component_id}: props.layoutPolish is required.")
            visual_system = self._as_dict(props.get("a9VisualSystem"))
            if visual_system.get("version") != "A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM":
                report.blockers.append(f"{component_id}: props.a9VisualSystem.version is required.")

    def _check_basic_contract_shape(
        self,
        data: dict[str, Any],
        page: dict[str, Any],
        components: list[dict[str, Any]],
        component_index: dict[str, dict[str, Any]],
        action_index: dict[str, dict[str, Any]],
        page_states: list[Any],
        quality: dict[str, Any],
        report: UiContractQualityGateReport,
    ) -> None:
        if data.get("schemaVersion") != "1.0":
            report.blockers.append("schemaVersion must be 1.0.")

        if data.get("artifactType") != "UI_CONTRACT":
            report.blockers.append("artifactType must be UI_CONTRACT.")

        if not page:
            report.blockers.append("page is missing.")
            return

        if not components:
            report.blockers.append("page.componentTree is empty.")
            return

        duplicate_ids = self._duplicates(
            [str(item.get("componentId") or "") for item in components]
        )
        if duplicate_ids:
            report.blockers.append(f"duplicated componentId(s): {', '.join(duplicate_ids)}.")

        for component_id in component_index:
            if not self.SAFE_ID_RE.match(component_id):
                report.blockers.append(
                    f"componentId must be stable ASCII key, got: {component_id}."
                )

        if not any(str(item.get("type") or "").upper() == "PAGE_HEADER" for item in components):
            report.blockers.append("PAGE_HEADER is required for production-grade UI_CONTRACT.")

        if len(components) < 3:
            report.warnings.append("component count is low; complex business pages should have multiple regions.")

        if not isinstance(page_states, list) or not page_states:
            report.blockers.append("pageStates must be non-empty.")
        else:
            state_keys = {
                str(item.get("stateKey") or "").upper()
                for item in page_states
                if isinstance(item, dict)
            }
            for required_state in ["DEFAULT", "LOADING", "ERROR"]:
                if required_state not in state_keys:
                    report.blockers.append(f"pageStates missing required state: {required_state}.")

        existing_blockers = [
            str(item)
            for item in self._as_list(quality.get("blockers"))
            if str(item).strip()
        ]
        if existing_blockers:
            report.blockers.append(
                "qualitySummary.blockers must be empty before persistence: "
                + "; ".join(existing_blockers)
            )

        if not action_index:
            report.warnings.append("No interaction is defined; static pages are allowed but should be intentional.")

    def _check_component_quality(
        self,
        components: list[dict[str, Any]],
        component_index: dict[str, dict[str, Any]],
        action_index: dict[str, dict[str, Any]],
        mock_data: dict[str, Any],
        report: UiContractQualityGateReport,
    ) -> None:
        for component in components:
            component_id = str(component.get("componentId") or "")
            component_type = str(component.get("type") or "").upper()
            props = self._as_dict(component.get("props"))

            if props.get("a9VisualQuality") != "demoGrade":
                report.blockers.append(
                    f"{component_id}: props.a9VisualQuality must be demoGrade."
                )

            if not isinstance(props.get("elementPicker"), dict):
                report.blockers.append(
                    f"{component_id}: props.elementPicker is required."
                )

            if not str(props.get("visualRole") or "").strip():
                report.blockers.append(
                    f"{component_id}: props.visualRole is required."
                )

            for action_id in self._normalize_str_list(component.get("actionIds")):
                if action_id not in action_index:
                    report.blockers.append(f"{component_id}: unknown actionId {action_id}.")

            for action_id in self._normalize_str_list(props.get("rowActionIds")):
                if action_id not in action_index:
                    report.blockers.append(f"{component_id}: unknown rowActionId {action_id}.")

            if component_type in {"FORM", "FIELD_GROUP"}:
                self._check_form_component(
                    component,
                    action_index,
                    report,
                )

            if component_type == "TABLE":
                self._check_table_component(
                    component,
                    action_index,
                    mock_data,
                    report,
                )

            if component_type == "TABS":
                tab_component_ids: list[str] = []
                for tab in self._as_list(component.get("tabs")):
                    if isinstance(tab, dict):
                        tab_component_ids.extend(
                            self._normalize_str_list(tab.get("componentIds"))
                        )

                for child_id in tab_component_ids:
                    if child_id not in component_index:
                        report.blockers.append(
                            f"{component_id}: tab references unknown componentId {child_id}."
                        )

    def _check_form_component(
        self,
        component: dict[str, Any],
        action_index: dict[str, dict[str, Any]],
        report: UiContractQualityGateReport,
    ) -> None:
        component_id = str(component.get("componentId") or "")
        fields = self._as_list(component.get("fields"))
        props = self._as_dict(component.get("props"))
        visual_role = str(props.get("visualRole") or "").lower()

        if not fields:
            report.blockers.append(f"{component_id}: FORM/FIELD_GROUP must have fields.")
            return

        seen_fields: set[str] = set()
        for index, field in enumerate(fields):
            if not isinstance(field, dict):
                report.blockers.append(f"{component_id}: invalid field at index {index}.")
                continue

            field_key = str(field.get("fieldKey") or "").strip()
            if not field_key:
                report.blockers.append(f"{component_id}: fieldKey missing.")
                continue

            if field_key in seen_fields:
                report.blockers.append(f"{component_id}: duplicated fieldKey {field_key}.")
            seen_fields.add(field_key)

            if not self.SAFE_ID_RE.match(field_key):
                report.blockers.append(f"{component_id}.{field_key}: fieldKey must be stable ASCII key.")

            if not str(field.get("label") or "").strip():
                report.blockers.append(f"{component_id}.{field_key}: label missing.")

            field_type = str(field.get("type") or "").upper()
            if field_type not in {"CHECKBOX", "RADIO", "SWITCH", "HIDDEN"}:
                if not str(field.get("placeholder") or "").strip():
                    report.blockers.append(f"{component_id}.{field_key}: placeholder missing.")

        owned_action_ids = set(self._normalize_str_list(component.get("actionIds")))
        owned_actions = [
            action_index[action_id]
            for action_id in owned_action_ids
            if action_id in action_index
        ]
        owned_types = {
            str(action.get("type") or "").upper()
            for action in owned_actions
        }

        if visual_role in {"enterprisefilterform", "filterform"}:
            if "QUERY_DATA" not in owned_types:
                report.blockers.append(f"{component_id}: filter form must bind QUERY_DATA.")
            if "RESET_FORM" not in owned_types:
                report.blockers.append(f"{component_id}: filter form must bind RESET_FORM.")
            return

        form_target_actions = [
            action
            for action in action_index.values()
            if str(action.get("targetId") or "") == component_id
        ]
        target_action_types = {
            str(action.get("type") or "").upper()
            for action in form_target_actions
        }

        if "SUBMIT_FORM" not in owned_types and "SUBMIT_FORM" not in target_action_types:
            report.blockers.append(f"{component_id}: business form must have a SUBMIT_FORM action targeting it.")

    def _check_table_component(
        self,
        component: dict[str, Any],
        action_index: dict[str, dict[str, Any]],
        mock_data: dict[str, Any],
        report: UiContractQualityGateReport,
    ) -> None:
        component_id = str(component.get("componentId") or "")
        columns = self._as_list(component.get("columns"))
        props = self._as_dict(component.get("props"))

        if not columns:
            report.blockers.append(f"{component_id}: TABLE must have columns.")
            return

        seen_columns: set[str] = set()
        for index, column in enumerate(columns):
            if not isinstance(column, dict):
                report.blockers.append(f"{component_id}: invalid column at index {index}.")
                continue

            column_key = str(column.get("columnKey") or "").strip()
            field_key = str(column.get("fieldKey") or "").strip()

            if not column_key:
                report.blockers.append(f"{component_id}: columnKey missing.")
                continue

            if column_key in seen_columns:
                report.blockers.append(f"{component_id}: duplicated columnKey {column_key}.")
            seen_columns.add(column_key)

            if not self.SAFE_ID_RE.match(column_key):
                report.blockers.append(f"{component_id}.{column_key}: columnKey must be stable ASCII key.")

            if not field_key:
                report.blockers.append(f"{component_id}.{column_key}: fieldKey missing.")

            if not str(column.get("label") or "").strip():
                report.blockers.append(f"{component_id}.{column_key}: label missing.")

        binding_key = str(component.get("dataBindingKey") or "").strip()
        if not binding_key:
            report.blockers.append(f"{component_id}: dataBindingKey missing.")
        elif not isinstance(mock_data.get(binding_key), list) or not mock_data.get(binding_key):
            report.blockers.append(f"{component_id}: mockData.{binding_key} must be a non-empty list.")

        row_action_ids = self._normalize_str_list(props.get("rowActionIds"))
        if not row_action_ids:
            report.blockers.append(f"{component_id}: props.rowActionIds required for table row operations.")
        else:
            for action_id in row_action_ids:
                if action_id not in action_index:
                    report.blockers.append(f"{component_id}: unknown rowActionId {action_id}.")

    def _check_interaction_closure(
        self,
        interactions: list[Any],
        component_index: dict[str, dict[str, Any]],
        action_index: dict[str, dict[str, Any]],
        navigation_targets: list[Any],
        page_states: list[Any],
        report: UiContractQualityGateReport,
    ) -> None:
        action_ids = [
            str(item.get("actionId") or "")
            for item in interactions
            if isinstance(item, dict)
        ]
        for duplicate in self._duplicates(action_ids):
            report.blockers.append(f"duplicated actionId: {duplicate}.")

        state_keys = {
            str(item.get("stateKey") or "").upper()
            for item in page_states
            if isinstance(item, dict)
        }
        target_page_ids = {
            str(item.get("targetPageId") or "")
            for item in navigation_targets
            if isinstance(item, dict)
        }

        for action in interactions:
            if not isinstance(action, dict):
                continue

            action_id = str(action.get("actionId") or "")
            action_type = str(action.get("type") or "").upper()
            target_id = str(action.get("targetId") or "").strip()
            source_id = str(action.get("sourceComponentId") or "").strip()

            if source_id and source_id not in component_index:
                report.blockers.append(f"{action_id}: sourceComponentId points to unknown component {source_id}.")

            if action_type in {"SUBMIT_FORM", "RESET_FORM"}:
                target_component = component_index.get(target_id)
                target_type = str(self._as_dict(target_component).get("type") or "").upper()
                if not target_id:
                    report.blockers.append(f"{action_id}: {action_type} requires targetId.")
                elif target_id not in component_index:
                    report.blockers.append(f"{action_id}: {action_type} targetId points to unknown component {target_id}.")
                elif target_type not in {"FORM", "FIELD_GROUP"}:
                    report.blockers.append(f"{action_id}: {action_type} targetId must point to FORM/FIELD_GROUP, got {target_type or 'UNKNOWN'}.")
            elif action_type == "SET_PAGE_STATE":
                if target_id and target_id.upper() not in state_keys:
                    report.blockers.append(f"{action_id}: targetId must be a known page state.")
            elif action_type == "NAVIGATE":
                target_page_id = str(action.get("targetPageId") or "").strip()
                if not target_page_id:
                    report.blockers.append(f"{action_id}: NAVIGATE requires targetPageId.")
                elif target_page_id not in target_page_ids:
                    report.blockers.append(f"{action_id}: NAVIGATE targetPageId is not declared in navigationTargets.")
            else:
                if target_id and target_id not in component_index:
                    report.blockers.append(f"{action_id}: targetId points to unknown component {target_id}.")

            for chain_key in ["successActionIds", "failureActionIds"]:
                for chained_action_id in self._normalize_str_list(action.get(chain_key)):
                    if chained_action_id not in action_index:
                        report.blockers.append(f"{action_id}: {chain_key} references unknown action {chained_action_id}.")

    def _check_page_pattern_contract(
        self,
        page: dict[str, Any],
        components: list[dict[str, Any]],
        action_index: dict[str, dict[str, Any]],
        mock_data: dict[str, Any],
        report: UiContractQualityGateReport,
    ) -> None:
        pattern = self._infer_page_pattern(page)
        layout = self._as_dict(page.get("layout"))
        layout_type = str(layout.get("type") or "").upper()
        layout_columns = int(layout.get("columns") or 1)

        component_types = {
            str(component.get("type") or "").upper()
            for component in components
        }
        visual_roles = {
            str(self._as_dict(component.get("props")).get("visualRole") or "").lower()
            for component in components
        }

        if pattern == "SEARCH_LIST":
            if layout_type != "TABLE_LIST":
                report.blockers.append("SEARCH_LIST page must use layout.type TABLE_LIST.")
            if layout_columns != 1:
                report.blockers.append("SEARCH_LIST page must use one full-width column.")
            if not any(role in visual_roles for role in {"enterprisefilterform", "filterform"}):
                report.blockers.append("SEARCH_LIST page must contain an enterprise filter form.")
            if "TABLE" not in component_types:
                report.blockers.append("SEARCH_LIST page must contain a TABLE.")
            if "EMPTY_STATE" not in component_types:
                report.blockers.append("SEARCH_LIST page must contain an EMPTY_STATE boundary.")

            table_components = [
                component
                for component in components
                if str(component.get("type") or "").upper() == "TABLE"
            ]
            for table in table_components:
                binding_key = str(table.get("dataBindingKey") or "")
                if binding_key and not mock_data.get(binding_key):
                    report.blockers.append(
                        f"SEARCH_LIST table {table.get('componentId')} has no mock rows."
                    )

        if pattern == "FORM_PAGE":
            if layout_type != "FORM_DETAIL":
                report.blockers.append("FORM_PAGE must use layout.type FORM_DETAIL.")
            if layout_columns != 1:
                report.blockers.append("FORM_PAGE must use one full-width root column.")

            forbidden_filter_roles = {
                "filterform",
                "enterprisefilterform",
                "enterprisefiltersection",
            }
            if visual_roles.intersection(forbidden_filter_roles):
                report.blockers.append("FORM_PAGE must not contain search filter form/section.")

            has_form = any(
                str(component.get("type") or "").upper() in {"FORM", "FIELD_GROUP"}
                for component in components
            )
            if not has_form:
                report.blockers.append("FORM_PAGE must contain FORM or FIELD_GROUP.")

            submit_targets = [
                str(action.get("targetId") or "")
                for action in action_index.values()
                if str(action.get("type") or "").upper() == "SUBMIT_FORM"
            ]
            if not submit_targets:
                report.blockers.append("FORM_PAGE must contain at least one SUBMIT_FORM interaction.")

        if pattern == "DASHBOARD":
            if "CARD" not in component_types and not component_types.intersection({"STATS", "STAT_GRID", "KPI", "KPI_CARD", "METRIC_GRID", "METRICS"}):
                report.warnings.append("DASHBOARD page should contain metric or summary cards.")

    def _check_visible_copy(
        self,
        page: dict[str, Any],
        components: list[dict[str, Any]],
        report: UiContractQualityGateReport,
    ) -> None:
        texts: list[tuple[str, str]] = []
        for key in ["pageName", "title", "description"]:
            value = str(page.get(key) or "")
            if value.strip():
                texts.append((f"page.{key}", value))

        for component in components:
            component_id = str(component.get("componentId") or "")
            for key in ["title", "description"]:
                value = str(component.get(key) or "")
                if value.strip():
                    texts.append((f"{component_id}.{key}", value))

            for field in self._as_list(component.get("fields")):
                if not isinstance(field, dict):
                    continue
                field_key = str(field.get("fieldKey") or "")
                for key in ["label", "placeholder", "helpText"]:
                    value = str(field.get(key) or "")
                    if value.strip():
                        texts.append((f"{component_id}.{field_key}.{key}", value))

            for column in self._as_list(component.get("columns")):
                if not isinstance(column, dict):
                    continue
                column_key = str(column.get("columnKey") or "")
                value = str(column.get("label") or "")
                if value.strip():
                    texts.append((f"{component_id}.{column_key}.label", value))

        for location, value in texts:
            for term in self.TECHNICAL_VISIBLE_TERMS:
                if term and term in value:
                    report.warnings.append(
                        f"{location}: visible copy contains technical term '{term}'."
                    )

    def _attach_report_to_quality_summary(
        self,
        artifact: UiContractArtifact,
        report: UiContractQualityGateReport,
    ) -> UiContractArtifact:
        existing_quality = artifact.qualitySummary
        existing_warnings = list(existing_quality.warnings or [])
        gate_warnings = [
            f"A9.10.14.1 Quality Gate: {warning}"
            for warning in report.warnings
        ]
        marker = "A9.10.14.1 Agent UI Quality Gate passed."
        warnings = list(dict.fromkeys([*existing_warnings, *gate_warnings, marker]))

        score_penalty = min(12, len(report.warnings) * 2)
        score = max(0, min(100, int(existing_quality.score or 0) - score_penalty))

        quality_summary = existing_quality.model_copy(
            update={
                "score": score,
                "blockers": [],
                "warnings": warnings,
            }
        )

        return artifact.model_copy(
            update={
                "qualitySummary": quality_summary,
            }
        )


    # A9.10.14.4.3.15.4.9.1_INDUSTRY_PAGE_QUALITY_BASELINE
    def _check_industry_page_quality_contract(
        self,
        data: dict[str, Any],
        page: dict[str, Any],
        components: list[dict[str, Any]],
        report: UiContractQualityGateReport,
    ) -> None:
        mock_data = self._as_dict(data.get("mockData"))
        style = self._as_dict(
            mock_data.get("__a9IndustryStyleProfile")
            or mock_data.get("industryStyleProfile")
            or data.get("industryStyleProfile")
        )
        baseline = self._as_dict(mock_data.get("__a9IndustryPageQualityBaseline"))
        layout = self._as_dict(page.get("layout"))
        layout_type = str(layout.get("type") or page.get("pageKind") or "").upper()

        if not style:
            report.warnings.append(
                "15.4.9: industryStyleProfile is missing; UI_CONTRACT can still pass, but industry intent is not traceable."
            )
        if not baseline:
            report.warnings.append(
                "15.4.9: industry page quality baseline is missing; template-smell diagnostics will be weaker."
            )

        visual_roles: set[str] = set()
        component_types: set[str] = set()
        for component in components:
            if not isinstance(component, dict):
                continue
            component_type = str(component.get("type") or "").upper()
            if component_type:
                component_types.add(component_type)
            props = self._as_dict(component.get("props"))
            role = str(props.get("visualRole") or "").strip()
            if role:
                visual_roles.add(role)

        if layout_type in {"TABLE_LIST", "LIST_PAGE", "SEARCH_LIST"} and "TABLE" not in component_types:
            report.warnings.append("15.4.9: list/search page should expose a TABLE component for industry page quality.")
        if layout_type in {"FORM_DETAIL", "FORM_PAGE", "SETTINGS"} and "FORM" not in component_types:
            report.warnings.append("15.4.9: form/settings page should expose a FORM component for industry page quality.")
        if len(components) >= 4 and not visual_roles:
            report.warnings.append("15.4.9: component visualRole metadata is empty; page may look like a generic template.")

        template_smells = baseline.get("templateSmells") if isinstance(baseline.get("templateSmells"), list) else []
        if template_smells:
            report.warnings.append("15.4.9: template-smell diagnostics: " + "; ".join(str(item) for item in template_smells[:3]))

    def _infer_page_pattern(
        self,
        page: dict[str, Any],
    ) -> str:
        layout = self._as_dict(page.get("layout"))
        layout_type = str(layout.get("type") or "").upper()
        page_kind = str(page.get("pageKind") or "").upper()
        text = " ".join(
            [
                layout_type,
                page_kind,
                str(page.get("pageName") or ""),
                str(page.get("pageKey") or ""),
                str(page.get("title") or ""),
            ]
        ).lower()

        if page_kind in {"LIST_PAGE", "TABLE_PAGE"} or layout_type == "TABLE_LIST" or any(token in text for token in ["列表", "查询", "搜索", "list"]):
            return "SEARCH_LIST"
        if page_kind in {"DASHBOARD_PAGE", "HOME_PAGE", "WORKBENCH"} or layout_type == "DASHBOARD" or any(token in text for token in ["看板", "工作台", "dashboard"]):
            return "DASHBOARD"
        if page_kind in {"DETAIL_PAGE", "PROFILE_PAGE"} or layout_type == "DETAIL_TABS" or any(token in text for token in ["详情", "detail"]):
            return "DETAIL_VIEW"
        return "FORM_PAGE"

    def _collect_components(
        self,
        value: Any,
    ) -> list[dict[str, Any]]:
        roots = self._as_list(value)
        result: list[dict[str, Any]] = []

        def visit(component: Any) -> None:
            if not isinstance(component, dict):
                return

            result.append(component)

            for child_key in ["children", "components", "items"]:
                for child in self._as_list(component.get(child_key)):
                    visit(child)

            for tab in self._as_list(component.get("tabs")):
                if not isinstance(tab, dict):
                    continue
                for child_key in ["children", "components", "items"]:
                    for child in self._as_list(tab.get(child_key)):
                        visit(child)

        for item in roots:
            visit(item)

        return result

    def _component_index(
        self,
        components: list[dict[str, Any]],
    ) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for component in components:
            component_id = str(component.get("componentId") or "").strip()
            if component_id and component_id not in result:
                result[component_id] = component
        return result

    def _action_index(
        self,
        interactions: list[Any],
    ) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for interaction in interactions:
            if not isinstance(interaction, dict):
                continue
            action_id = str(interaction.get("actionId") or "").strip()
            if action_id and action_id not in result:
                result[action_id] = interaction
        return result

    def _duplicates(
        self,
        values: list[str],
    ) -> list[str]:
        seen: set[str] = set()
        duplicates: list[str] = []
        for value in values:
            if not value:
                continue
            if value in seen and value not in duplicates:
                duplicates.append(value)
            seen.add(value)
        return duplicates

    def _as_list(
        self,
        value: Any,
    ) -> list[Any]:
        return value if isinstance(value, list) else []

    def _as_dict(
        self,
        value: Any,
    ) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _normalize_str_list(
        self,
        value: Any,
    ) -> list[str]:
        if not isinstance(value, list):
            return []
        result: list[str] = []
        for item in value:
            text = str(item or "").strip()
            if text:
                result.append(text)
        return result
