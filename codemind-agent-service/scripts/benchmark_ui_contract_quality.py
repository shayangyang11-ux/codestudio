from __future__ import annotations

import argparse
import copy
import dataclasses
import datetime as _dt
import json
import math
import re
import sys
from pathlib import Path
from typing import Any, Iterable


A9_VERSION = "A9.10.2"


BLOCKER = "BLOCKER"
WARNING = "WARNING"
INFO = "INFO"


@dataclasses.dataclass
class QualityIssue:
    severity: str
    rule: str
    message: str
    path: str = "$"
    suggestion: str = ""

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


@dataclasses.dataclass
class QualityCaseResult:
    source: str
    contract_name: str
    inferred_pattern: str
    page_kind: str
    layout_type: str
    score: int
    status: str
    blockers: list[QualityIssue]
    warnings: list[QualityIssue]
    infos: list[QualityIssue]
    metrics: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "contractName": self.contract_name,
            "inferredPattern": self.inferred_pattern,
            "pageKind": self.page_kind,
            "layoutType": self.layout_type,
            "score": self.score,
            "status": self.status,
            "blockers": [item.to_dict() for item in self.blockers],
            "warnings": [item.to_dict() for item in self.warnings],
            "infos": [item.to_dict() for item in self.infos],
            "metrics": self.metrics,
        }


class UiContractQualityBenchmark:
    """Deterministic UI_CONTRACT prototype quality benchmark.

    This checker is intentionally independent from the live LLM and the Python
    agent runtime. It only needs JSON files, so it can be run before/after each
    Agent or Hardener change to catch regressions in generated prototypes.
    """

    ACTION_COLUMN_LABELS = {"操作", "动作", "actions", "action", "operation", "operations", "行操作"}
    QUERY_WORDS = ("查询", "搜索", "筛选", "过滤", "检索", "query", "search", "filter")
    AUTH_WORDS = ("登录", "注册", "密码", "验证码", "账号", "login", "register", "password")
    FORM_WORDS = ("新增", "创建", "编辑", "录入", "申请", "维护", "配置")
    LIST_WORDS = ("列表", "查询", "管理", "台账", "明细列表", "清单")
    DETAIL_WORDS = ("详情", "查看", "概览", "档案")
    DASHBOARD_WORDS = ("看板", "工作台", "首页", "总览", "dashboard")
    WORKFLOW_WORDS = ("审批", "流程", "工单", "待办", "处理", "审核")
    SETTINGS_WORDS = ("设置", "配置", "参数", "规则")
    UPLOAD_WORDS = ("上传", "导入", "附件", "文件")

    PATTERN_EXPECTED_LAYOUT: dict[str, set[str]] = {
        "AUTH": {"CENTERED_FORM"},
        "LIST": {"TABLE_LIST", "CUSTOM_GRID"},
        "FORM": {"FORM_DETAIL", "SETTINGS"},
        "DETAIL": {"DETAIL_TABS", "CUSTOM_GRID"},
        "DASHBOARD": {"DASHBOARD", "CUSTOM_GRID"},
        "WORKFLOW": {"APPROVAL_WORKBENCH", "CUSTOM_GRID"},
        "SETTINGS": {"SETTINGS", "FORM_DETAIL"},
        "UPLOAD": {"FORM_DETAIL", "CUSTOM_GRID"},
    }

    PATTERN_EXPECTED_PAGE_KIND: dict[str, tuple[str, ...]] = {
        "AUTH": ("LOGIN", "REGISTER", "AUTH"),
        "LIST": ("LIST", "TABLE", "SEARCH", "CRUD"),
        "FORM": ("FORM", "CREATE", "EDIT", "UPLOAD"),
        "DETAIL": ("DETAIL", "VIEW"),
        "DASHBOARD": ("DASHBOARD", "HOME", "WORKBENCH"),
        "WORKFLOW": ("WORKFLOW", "APPROVAL", "PROCESS", "TASK"),
        "SETTINGS": ("SETTING", "CONFIG"),
        "UPLOAD": ("UPLOAD", "IMPORT", "FILE"),
    }

    def evaluate(self, contract: dict[str, Any], source: str = "<memory>") -> QualityCaseResult:
        contract = self._unwrap_contract(contract)
        page = self._as_dict(contract.get("page"))
        identity = self._as_dict(contract.get("identity"))
        layout = self._as_dict(page.get("layout"))
        page_kind = self._string(page.get("pageKind") or identity.get("pageKind"))
        layout_type = self._string(layout.get("type"))
        page_name = self._string(page.get("pageName") or identity.get("pageName") or page.get("title") or Path(source).stem)
        components = self._walk_components(page.get("componentTree"))
        interactions = self._as_list(contract.get("interactions"))
        mock_scenarios = self._as_list(contract.get("mockScenarios"))
        api_bindings = self._as_list(contract.get("apiBindings"))
        feature_coverage = self._as_list(contract.get("featureCoverage"))
        component_index = {
            self._string(item.get("componentId")): item
            for item in components
            if self._string(item.get("componentId"))
        }
        action_index = {
            self._string(item.get("actionId")): item
            for item in interactions
            if self._string(item.get("actionId"))
        }

        metrics = {
            "componentCount": len(components),
            "interactionCount": len(interactions),
            "fieldCount": sum(len(self._as_list(item.get("fields"))) for item in components),
            "columnCount": sum(len(self._as_list(item.get("columns"))) for item in components),
            "mockScenarioCount": len(mock_scenarios),
            "apiBindingCount": len(api_bindings),
            "featureCoverageCount": len(feature_coverage),
            "componentTypes": self._count_by(components, lambda item: self._component_type(item) or "UNKNOWN"),
        }

        pattern = self._infer_pattern(contract, page, components)
        issues: list[QualityIssue] = []
        issues.extend(self._check_contract_shape(contract, page, layout, source))
        issues.extend(self._check_component_identity(components))
        issues.extend(self._check_interaction_integrity(components, interactions, mock_scenarios, api_bindings, component_index, action_index))
        issues.extend(self._check_page_pattern(pattern, page_kind, layout_type, page, components, interactions))
        issues.extend(self._check_duplicate_action_columns(components))
        issues.extend(self._check_toolbar_cards(page, components))
        issues.extend(self._check_editability(components, interactions))
        issues.extend(self._check_mock_data(pattern, contract, page, components, mock_scenarios))
        issues.extend(self._check_low_fidelity(pattern, page, components, interactions))
        issues.extend(self._check_user_visible_language(page, components))
        issues.extend(self._check_quality_summary(contract, issues))

        blockers = [item for item in issues if item.severity == BLOCKER]
        warnings = [item for item in issues if item.severity == WARNING]
        infos = [item for item in issues if item.severity == INFO]
        score = self._score(blockers, warnings, infos)
        status = "FAIL" if blockers or score < 70 else "WARN" if warnings or score < 90 else "PASS"

        return QualityCaseResult(
            source=source,
            contract_name=page_name,
            inferred_pattern=pattern,
            page_kind=page_kind,
            layout_type=layout_type,
            score=score,
            status=status,
            blockers=blockers,
            warnings=warnings,
            infos=infos,
            metrics=metrics,
        )

    def _check_contract_shape(
        self,
        contract: dict[str, Any],
        page: dict[str, Any],
        layout: dict[str, Any],
        source: str,
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []

        if not isinstance(contract, dict) or not contract:
            return [
                QualityIssue(
                    BLOCKER,
                    "contract.shape",
                    "文件没有解析出有效 UI_CONTRACT JSON 对象。",
                    "$",
                    "确认输入文件是 UI_CONTRACT JSON，或包含 content/contentJson 字段。",
                )
            ]

        if not page:
            issues.append(QualityIssue(BLOCKER, "contract.page", "缺少 page 对象。", "$.page"))

        if not page.get("componentTree"):
            issues.append(
                QualityIssue(
                    BLOCKER,
                    "contract.componentTree",
                    "缺少 page.componentTree，Runtime 无法渲染页面主体。",
                    "$.page.componentTree",
                    "至少输出 PAGE_HEADER 和一个核心业务 SECTION/FORM/TABLE。",
                )
            )

        if not layout.get("type"):
            issues.append(
                QualityIssue(
                    WARNING,
                    "contract.layout",
                    "缺少 page.layout.type，无法稳定判断页面模式。",
                    "$.page.layout.type",
                    "按页面类型设置 CENTERED_FORM / FORM_DETAIL / TABLE_LIST / DETAIL_TABS / DASHBOARD / APPROVAL_WORKBENCH / SETTINGS。",
                )
            )

        artifact_type = self._string(contract.get("artifactType"))
        if artifact_type and artifact_type != "UI_CONTRACT":
            issues.append(
                QualityIssue(
                    WARNING,
                    "contract.artifactType",
                    f"artifactType 当前为 {artifact_type!r}，不是 UI_CONTRACT。",
                    "$.artifactType",
                )
            )

        return issues

    def _check_component_identity(self, components: list[dict[str, Any]]) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        seen: dict[str, int] = {}

        for index, component in enumerate(components):
            cid = self._string(component.get("componentId"))
            ctype = self._component_type(component)
            path = f"$.page.componentTree..[{index}]"

            if not cid:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "component.id.missing",
                        f"{ctype or 'UNKNOWN'} 组件缺少 componentId，无法被选择、交互或保存。",
                        path,
                    )
                )
                continue

            seen[cid] = seen.get(cid, 0) + 1

            if re.search(r"[\u4e00-\u9fff\s]", cid):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "component.id.unstable",
                        f"componentId={cid!r} 包含中文或空格，不利于稳定选择和代码生成。",
                        path + ".componentId",
                        "使用 snake_case / kebab_case 的英文稳定 ID。",
                    )
                )

            if not ctype:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "component.type.missing",
                        f"componentId={cid!r} 缺少 type/componentType。",
                        path + ".type",
                    )
                )

        for cid, count in seen.items():
            if count > 1:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "component.id.duplicate",
                        f"componentId={cid!r} 重复出现 {count} 次。",
                        "$.page.componentTree",
                        "每个可见组件必须有全局唯一 componentId。",
                    )
                )

        return issues

    def _check_interaction_integrity(
        self,
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
        mock_scenarios: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        component_index: dict[str, dict[str, Any]],
        action_index: dict[str, dict[str, Any]],
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        action_seen: dict[str, int] = {}

        for index, action in enumerate(interactions):
            action_id = self._string(action.get("actionId"))
            target_id = self._string(action.get("targetId"))
            path = f"$.interactions[{index}]"

            if not action_id:
                issues.append(QualityIssue(BLOCKER, "interaction.id.missing", "interaction 缺少 actionId。", path))
                continue

            action_seen[action_id] = action_seen.get(action_id, 0) + 1

            if not target_id:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "interaction.target.missing",
                        f"actionId={action_id!r} 缺少 targetId。",
                        path + ".targetId",
                    )
                )
            elif target_id not in component_index:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "interaction.target.invalid",
                        f"actionId={action_id!r} 的 targetId={target_id!r} 不在 componentTree 中。",
                        path + ".targetId",
                    )
                )

            if not self._string(action.get("label")):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "interaction.label.missing",
                        f"actionId={action_id!r} 缺少 label，按钮文案可能不可见或不可编辑。",
                        path + ".label",
                    )
                )

        for action_id, count in action_seen.items():
            if count > 1:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "interaction.id.duplicate",
                        f"actionId={action_id!r} 重复出现 {count} 次。",
                        "$.interactions",
                    )
                )

        referenced = self._referenced_action_ids(components)
        for action_id, path in referenced:
            if action_id not in action_index:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "component.action.invalid",
                        f"组件引用了不存在的 actionId={action_id!r}。",
                        path,
                    )
                )

        for index, scenario in enumerate(mock_scenarios):
            action_id = self._string(scenario.get("actionId"))
            if action_id and action_id not in action_index:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "mock.action.invalid",
                        f"mockScenarios[{index}] 引用了不存在的 actionId={action_id!r}。",
                        f"$.mockScenarios[{index}].actionId",
                    )
                )

        for index, binding in enumerate(api_bindings):
            for item in self._as_list(binding.get("usedByActionIds")):
                action_id = self._string(item)
                if action_id and action_id not in action_index:
                    issues.append(
                        QualityIssue(
                            WARNING,
                            "api.action.invalid",
                            f"apiBindings[{index}].usedByActionIds 引用了不存在的 actionId={action_id!r}。",
                            f"$.apiBindings[{index}].usedByActionIds",
                        )
                    )

        return issues

    def _check_page_pattern(
        self,
        pattern: str,
        page_kind: str,
        layout_type: str,
        page: dict[str, Any],
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        upper_kind = page_kind.upper()
        upper_layout = layout_type.upper()
        component_types = {self._component_type(item) for item in components}
        visible_text = self._visible_text(page, components)

        expected_layouts = self.PATTERN_EXPECTED_LAYOUT.get(pattern, set())
        if expected_layouts and upper_layout and upper_layout not in expected_layouts:
            issues.append(
                QualityIssue(
                    BLOCKER if pattern in {"LIST", "FORM", "AUTH"} else WARNING,
                    "pattern.layout.mismatch",
                    f"识别为 {pattern} 页面，但 layout.type={layout_type!r} 不在期望范围 {sorted(expected_layouts)}。",
                    "$.page.layout.type",
                    "让 Page Pattern Intelligence 在返回前统一修复 layout.type。",
                )
            )

        expected_kind_tokens = self.PATTERN_EXPECTED_PAGE_KIND.get(pattern, ())
        if expected_kind_tokens and upper_kind and not any(token in upper_kind for token in expected_kind_tokens):
            issues.append(
                QualityIssue(
                    WARNING,
                    "pattern.pageKind.mismatch",
                    f"识别为 {pattern} 页面，但 pageKind={page_kind!r} 与该模式不一致。",
                    "$.page.pageKind",
                )
            )

        if pattern == "LIST":
            if not self._has_query_form(components):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.list.query.missing",
                        "列表页缺少查询/筛选区。",
                        "$.page.componentTree",
                        "列表页标准结构应包含查询筛选 FORM。",
                    )
                )
            if "TABLE" not in component_types:
                issues.append(
                    QualityIssue(BLOCKER, "pattern.list.table.missing", "列表页缺少 TABLE 数据区。", "$.page.componentTree")
                )
            if not self._has_primary_action(interactions):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.list.primaryAction.missing",
                        "列表页缺少主操作按钮，例如新增、导出或批量操作。",
                        "$.interactions",
                    )
                )
            if "EMPTY_STATE" not in component_types:
                issues.append(
                    QualityIssue(
                        INFO,
                        "pattern.list.emptyState.missing",
                        "列表页没有 EMPTY_STATE，空数据状态不可演示。",
                        "$.page.componentTree",
                    )
                )

        if pattern == "FORM":
            if self._has_query_form(components):
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "pattern.form.query.leak",
                        "表单页混入查询/筛选 FORM，说明页面模式漂移成列表页。",
                        "$.page.componentTree",
                        "创建/编辑/录入页应是业务表单，不应出现查询筛选区。",
                    )
                )
            if "FORM" not in component_types:
                issues.append(
                    QualityIssue(BLOCKER, "pattern.form.form.missing", "表单页缺少 FORM 组件。", "$.page.componentTree")
                )
            if not self._has_submit_action(interactions):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.form.submit.missing",
                        "表单页缺少保存/提交类主动作。",
                        "$.interactions",
                    )
                )

        if pattern == "DETAIL":
            if self._has_large_edit_form(components):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.detail.editForm.suspicious",
                        "详情页包含大型可编辑 FORM，可能误生成成编辑页。",
                        "$.page.componentTree",
                    )
                )
            if not ({"DESCRIPTION_LIST", "STEPS", "TABLE", "CARD"} & component_types):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.detail.structure.weak",
                        "详情页缺少概要、进度、关联记录或信息卡结构。",
                        "$.page.componentTree",
                    )
                )

        if pattern == "DASHBOARD":
            card_count = sum(1 for item in components if self._component_type(item) == "CARD")
            table_count = sum(1 for item in components if self._component_type(item) == "TABLE")
            if card_count < 3:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.dashboard.metricCards.missing",
                        "看板页指标卡数量不足，页面看起来不像真实工作台。",
                        "$.page.componentTree",
                        "至少输出 3 个指标/摘要卡片。",
                    )
                )
            if table_count < 1 and "STEPS" not in component_types:
                issues.append(
                    QualityIssue(
                        INFO,
                        "pattern.dashboard.workItems.missing",
                        "看板页缺少待办、趋势或业务明细区域。",
                        "$.page.componentTree",
                    )
                )

        if pattern == "WORKFLOW":
            if "STEPS" not in component_types:
                issues.append(
                    QualityIssue(WARNING, "pattern.workflow.steps.missing", "审批/流程页缺少 STEPS 步骤条。", "$.page.componentTree")
                )
            if not any(self._contains_any(self._component_text(item), ("意见", "审批", "处理")) for item in components):
                issues.append(
                    QualityIssue(WARNING, "pattern.workflow.opinion.missing", "审批/流程页缺少处理意见或审批操作区。", "$.page.componentTree")
                )

        if pattern == "SETTINGS":
            if "FORM" not in component_types:
                issues.append(QualityIssue(WARNING, "pattern.settings.form.missing", "设置页缺少配置 FORM。", "$.page.componentTree"))
            if not any(self._component_type(item) == "ALERT" for item in components):
                issues.append(QualityIssue(INFO, "pattern.settings.alert.missing", "设置页缺少风险提示或保存影响说明。", "$.page.componentTree"))

        if pattern == "UPLOAD":
            if not any("UPLOAD" in self._component_type(item) or self._contains_any(self._component_text(item), self.UPLOAD_WORDS) for item in components):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "pattern.upload.area.missing",
                        "上传/导入页缺少上传区域或文件列表语义。",
                        "$.page.componentTree",
                    )
                )

        if pattern != "AUTH" and self._contains_any(visible_text, self.AUTH_WORDS):
            auth_noise = "密码" in visible_text or "欢迎登录" in visible_text or "注册账号" in visible_text
            if auth_noise:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "pattern.auth.leak",
                        "非认证页面出现登录/注册/密码等认证页结构，页面模式误判。",
                        "$.page.componentTree",
                    )
                )

        return issues

    def _check_duplicate_action_columns(self, components: list[dict[str, Any]]) -> list[QualityIssue]:
        issues: list[QualityIssue] = []

        for table in (item for item in components if self._component_type(item) == "TABLE"):
            table_id = self._string(table.get("componentId"))
            columns = self._as_list(table.get("columns"))
            props = self._as_dict(table.get("props"))
            row_action_ids = [self._string(item) for item in self._as_list(props.get("rowActionIds")) if self._string(item)]
            action_like_columns = [
                column
                for column in columns
                if self._is_action_column(column)
            ]

            if len(action_like_columns) > 1:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "table.actionColumn.duplicate",
                        f"表格 {table_id!r} 包含 {len(action_like_columns)} 个操作列。",
                        f"$.page.componentTree..{table_id}.columns",
                        "普通 columns 中只能有业务列；行操作应通过 props.rowActionIds 或唯一操作列表达，不能双写。",
                    )
                )

            if row_action_ids and action_like_columns:
                issues.append(
                    QualityIssue(
                        BLOCKER,
                        "table.actionColumn.doubleRendered",
                        f"表格 {table_id!r} 同时存在 columns 操作列和 props.rowActionIds，Runtime 可能渲染两个“操作”。",
                        f"$.page.componentTree..{table_id}",
                        "移除 columns 里的操作列，只保留 props.rowActionIds。",
                    )
                )

        return issues

    def _check_toolbar_cards(self, page: dict[str, Any], components: list[dict[str, Any]]) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        root_ids = {self._string(item.get("componentId")) for item in self._root_components(page)}

        for component in components:
            if self._component_type(component) != "TOOLBAR":
                continue

            cid = self._string(component.get("componentId"))
            action_ids = self._as_list(component.get("actionIds"))
            children = self._as_list(component.get("children"))
            parent_kind = self._string(component.get("parentKind") or component.get("parentType"))
            text = self._component_text(component)
            is_root_like = cid in root_ids

            if is_root_like:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "toolbar.root.card",
                        f"TOOLBAR {cid!r} 位于根级，容易渲染成空白工具栏卡片。",
                        f"$.page.componentTree..{cid}",
                        "列表页工具栏应合并进数据列表 SECTION/TABLE header，不应独立占一个大卡片。",
                    )
                )

            if not action_ids and not children and len(text.strip()) <= 8:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "toolbar.empty",
                        f"TOOLBAR {cid!r} 缺少 actionIds/children，可见价值不足。",
                        f"$.page.componentTree..{cid}",
                    )
                )

        return issues

    def _check_editability(self, components: list[dict[str, Any]], interactions: list[dict[str, Any]]) -> list[QualityIssue]:
        issues: list[QualityIssue] = []

        for component in components:
            cid = self._string(component.get("componentId")) or "<missing>"
            ctype = self._component_type(component)
            fields = self._as_list(component.get("fields"))
            columns = self._as_list(component.get("columns"))
            tabs = self._as_list(component.get("tabs"))

            for index, field in enumerate(fields):
                field_key = self._string(field.get("fieldKey"))
                label = self._string(field.get("label"))
                ftype = self._string(field.get("type"))

                if not field_key:
                    issues.append(
                        QualityIssue(
                            BLOCKER,
                            "field.key.missing",
                            f"组件 {cid!r} 的 fields[{index}] 缺少 fieldKey，无法被画布精确选择。",
                            f"$.page.componentTree..{cid}.fields[{index}].fieldKey",
                        )
                    )
                if not label:
                    issues.append(QualityIssue(WARNING, "field.label.missing", f"字段 {field_key or index!r} 缺少 label。", f"$.page.componentTree..{cid}.fields[{index}].label"))
                if not ftype:
                    issues.append(QualityIssue(WARNING, "field.type.missing", f"字段 {field_key or index!r} 缺少 type。", f"$.page.componentTree..{cid}.fields[{index}].type"))

            for index, column in enumerate(columns):
                column_key = self._string(column.get("columnKey"))
                label = self._string(column.get("label"))

                if not column_key:
                    issues.append(
                        QualityIssue(
                            BLOCKER,
                            "column.key.missing",
                            f"表格 {cid!r} 的 columns[{index}] 缺少 columnKey，无法被画布精确选择。",
                            f"$.page.componentTree..{cid}.columns[{index}].columnKey",
                        )
                    )
                if not label:
                    issues.append(QualityIssue(WARNING, "column.label.missing", f"列 {column_key or index!r} 缺少 label。", f"$.page.componentTree..{cid}.columns[{index}].label"))

            for index, tab in enumerate(tabs):
                tab_key = self._string(tab.get("tabKey"))
                title = self._string(tab.get("title"))
                if not tab_key:
                    issues.append(QualityIssue(BLOCKER, "tab.key.missing", f"组件 {cid!r} 的 tabs[{index}] 缺少 tabKey。", f"$.page.componentTree..{cid}.tabs[{index}].tabKey"))
                if not title:
                    issues.append(QualityIssue(WARNING, "tab.title.missing", f"Tab {tab_key or index!r} 缺少 title。", f"$.page.componentTree..{cid}.tabs[{index}].title"))

            if ctype in {"BUTTON", "LINK"} and not self._as_list(component.get("actionIds")) and not self._string(component.get("actionId")):
                issues.append(
                    QualityIssue(
                        WARNING,
                        "button.action.missing",
                        f"{ctype} 组件 {cid!r} 没有绑定 actionId/actionIds，点击行为不可追踪。",
                        f"$.page.componentTree..{cid}",
                    )
                )

        for index, action in enumerate(interactions):
            if not self._string(action.get("actionId")):
                issues.append(QualityIssue(BLOCKER, "action.key.missing", f"interactions[{index}] 缺少 actionId。", f"$.interactions[{index}].actionId"))

        return issues

    def _check_mock_data(
        self,
        pattern: str,
        contract: dict[str, Any],
        page: dict[str, Any],
        components: list[dict[str, Any]],
        mock_scenarios: list[dict[str, Any]],
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        mock_data = self._as_dict(contract.get("mockData") or page.get("mockData"))
        table_components = [item for item in components if self._component_type(item) == "TABLE"]
        has_rows = self._contract_has_demo_rows(contract, page, table_components)

        if pattern in {"LIST", "DASHBOARD", "DETAIL", "WORKFLOW"} and table_components and not has_rows:
            issues.append(
                QualityIssue(
                    WARNING,
                    "mockData.tableRows.missing",
                    "页面包含 TABLE，但没有可识别的示例行数据，预览可能显得空。",
                    "$.mockData",
                    "为每张表提供至少 2 行 mock 数据，或在 TABLE.props.mockRows 中给出示例。",
                )
            )

        if pattern in {"FORM", "AUTH", "UPLOAD", "SETTINGS"} and not mock_scenarios:
            issues.append(
                QualityIssue(
                    INFO,
                    "mockScenarios.missing",
                    "表单/认证/上传类页面缺少 mockScenarios，成功/失败状态不可演示。",
                    "$.mockScenarios",
                )
            )

        if not mock_data and not mock_scenarios and pattern not in {"AUTH"}:
            issues.append(
                QualityIssue(
                    INFO,
                    "mockData.empty",
                    "没有 mockData 或 mockScenarios，原型演示丰富度不足。",
                    "$.mockData",
                )
            )

        return issues

    def _check_low_fidelity(
        self,
        pattern: str,
        page: dict[str, Any],
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        component_types = [self._component_type(item) for item in components]
        root_components = self._root_components(page)

        if "PAGE_HEADER" not in set(component_types):
            issues.append(
                QualityIssue(
                    WARNING,
                    "visual.pageHeader.missing",
                    "缺少 PAGE_HEADER，页面缺少真实企业后台页头。",
                    "$.page.componentTree",
                )
            )

        meaningful = [
            item
            for item in components
            if self._component_type(item) in {"SECTION", "CARD", "FORM", "TABLE", "DESCRIPTION_LIST", "STEPS", "ALERT", "TABS", "EMPTY_STATE"}
        ]

        if len(meaningful) < 2 and pattern not in {"AUTH"}:
            issues.append(
                QualityIssue(
                    WARNING,
                    "visual.sections.tooFew",
                    "页面视觉分区过少，可能只是低保真字段清单。",
                    "$.page.componentTree",
                    "至少包含页头、核心业务区、状态/反馈/辅助信息区。",
                )
            )

        if len(root_components) == 1:
            root = root_components[0]
            root_type = self._component_type(root)
            if root_type in {"FORM", "TABLE"}:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "visual.bareRoot",
                        f"根节点只有一个裸 {root_type}，页面不像可演示产品原型。",
                        "$.page.componentTree",
                    )
                )

        if pattern == "LIST":
            tables = [item for item in components if self._component_type(item) == "TABLE"]
            for table in tables:
                props = self._as_dict(table.get("props"))
                visual_role = self._string(props.get("visualRole"))
                if not visual_role and len(self._as_list(table.get("columns"))) < 3:
                    issues.append(
                        QualityIssue(
                            WARNING,
                            "visual.table.weak",
                            f"表格 {self._string(table.get('componentId'))!r} 列过少且缺少 visualRole，列表页像草图。",
                            "$.page.componentTree",
                        )
                    )

        if len(interactions) == 0 and pattern not in {"DETAIL", "DASHBOARD"}:
            issues.append(
                QualityIssue(
                    WARNING,
                    "interaction.none",
                    "页面没有任何 interactions，按钮/提交/查询无法演示。",
                    "$.interactions",
                )
            )

        return issues

    def _check_user_visible_language(self, page: dict[str, Any], components: list[dict[str, Any]]) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        forbidden = ("schema", "contract", "component", "featureCoverage", "mock", "开发说明", "组件说明", "契约")
        text = self._visible_text(page, components)
        lower = text.lower()

        for term in forbidden:
            if term.lower() in lower:
                issues.append(
                    QualityIssue(
                        WARNING,
                        "copywriting.technicalTerm",
                        f"用户可见文案中出现技术词 {term!r}。",
                        "$.page.componentTree",
                        "用户最终页面不应展示 schema/contract/mock/component/featureCoverage 等开发词。",
                    )
                )
                break

        return issues

    def _check_quality_summary(
        self,
        contract: dict[str, Any],
        current_issues: list[QualityIssue],
    ) -> list[QualityIssue]:
        issues: list[QualityIssue] = []
        summary = self._as_dict(contract.get("qualitySummary"))

        if not summary:
            return [
                QualityIssue(
                    INFO,
                    "qualitySummary.missing",
                    "缺少 qualitySummary，无法在资产层记录生成质量。",
                    "$.qualitySummary",
                )
            ]

        score = summary.get("score")
        blockers = self._as_list(summary.get("blockers"))
        warnings = self._as_list(summary.get("warnings"))

        if isinstance(score, (int, float)) and int(score) >= 100 and any(item.severity in {BLOCKER, WARNING} for item in current_issues):
            issues.append(
                QualityIssue(
                    WARNING,
                    "qualitySummary.unrealistic",
                    "qualitySummary.score 为满分，但基准检查发现阻断项或警告，质量摘要不真实。",
                    "$.qualitySummary.score",
                )
            )

        if any(item.severity == BLOCKER for item in current_issues) and not blockers:
            issues.append(
                QualityIssue(
                    INFO,
                    "qualitySummary.blockers.notSynced",
                    "基准发现阻断项，但 qualitySummary.blockers 未同步记录。",
                    "$.qualitySummary.blockers",
                )
            )

        if any(item.severity == WARNING for item in current_issues) and not warnings:
            issues.append(
                QualityIssue(
                    INFO,
                    "qualitySummary.warnings.notSynced",
                    "基准发现警告项，但 qualitySummary.warnings 未同步记录。",
                    "$.qualitySummary.warnings",
                )
            )

        return issues

    def _infer_pattern(
        self,
        contract: dict[str, Any],
        page: dict[str, Any],
        components: list[dict[str, Any]],
    ) -> str:
        layout = self._string(self._as_dict(page.get("layout")).get("type")).upper()
        page_kind = self._string(page.get("pageKind") or self._as_dict(contract.get("identity")).get("pageKind")).upper()
        text = self._visible_text(page, components)

        # Strong signals first: pageKind and layout are produced by the Agent/
        # hardener and should override incidental words in titles such as "处理进度".
        if layout == "CENTERED_FORM" or any(token in page_kind for token in ("LOGIN", "REGISTER", "AUTH")):
            return "AUTH"

        if any(token in page_kind for token in ("DASHBOARD", "HOME", "WORKBENCH")) or layout == "DASHBOARD":
            return "DASHBOARD"

        if any(token in page_kind for token in ("SETTING", "CONFIG")) or layout == "SETTINGS":
            return "SETTINGS"

        if any(token in page_kind for token in ("UPLOAD", "IMPORT", "FILE")):
            return "UPLOAD"

        if any(token in page_kind for token in ("DETAIL", "VIEW")) or layout == "DETAIL_TABS":
            return "DETAIL"

        if any(token in page_kind for token in ("WORKFLOW", "APPROVAL", "PROCESS", "TASK")) or layout == "APPROVAL_WORKBENCH":
            return "WORKFLOW"

        if any(token in page_kind for token in ("LIST", "TABLE", "SEARCH", "CRUD")) or layout == "TABLE_LIST":
            return "LIST"

        if any(token in page_kind for token in ("FORM", "CREATE", "EDIT")) or layout == "FORM_DETAIL":
            if self._contains_any(text, self.UPLOAD_WORDS):
                return "UPLOAD"
            return "FORM"

        # Weak text fallback only applies when pageKind/layout are not decisive.
        if self._contains_any(text, self.AUTH_WORDS):
            return "AUTH"
        if self._contains_any(text, self.DASHBOARD_WORDS):
            return "DASHBOARD"
        if self._contains_any(text, self.SETTINGS_WORDS):
            return "SETTINGS"
        if self._contains_any(text, self.UPLOAD_WORDS):
            return "UPLOAD"
        if self._contains_any(text, self.DETAIL_WORDS):
            return "DETAIL"
        if self._contains_any(text, self.WORKFLOW_WORDS):
            return "WORKFLOW"
        if self._contains_any(text, self.LIST_WORDS):
            return "LIST"
        if self._contains_any(text, self.FORM_WORDS):
            return "FORM"

        component_types = {self._component_type(item) for item in components}
        if "TABLE" in component_types and self._has_query_form(components):
            return "LIST"
        if "FORM" in component_types:
            return "FORM"

        return "CUSTOM"

    def _score(self, blockers: list[QualityIssue], warnings: list[QualityIssue], infos: list[QualityIssue]) -> int:
        raw = 100 - len(blockers) * 14 - len(warnings) * 5 - len(infos) * 1
        return max(0, min(100, int(raw)))

    def _walk_components(self, raw: Any) -> list[dict[str, Any]]:
        roots = self._as_list(raw)
        if isinstance(raw, dict):
            roots = [raw]

        result: list[dict[str, Any]] = []

        def visit(item: Any) -> None:
            if not isinstance(item, dict):
                return
            result.append(item)
            for child in self._as_list(item.get("children")):
                visit(child)

        for root in roots:
            visit(root)

        return result

    def _root_components(self, page: dict[str, Any]) -> list[dict[str, Any]]:
        raw = page.get("componentTree")
        if isinstance(raw, dict):
            return [raw]
        return [item for item in self._as_list(raw) if isinstance(item, dict)]

    def _component_type(self, component: dict[str, Any]) -> str:
        return self._string(component.get("type") or component.get("componentType")).upper()

    def _component_text(self, component: dict[str, Any]) -> str:
        chunks: list[str] = []
        props = self._as_dict(component.get("props"))

        for key in ("title", "label", "description", "text", "message", "placeholder", "eyebrow", "kicker"):
            value = component.get(key)
            if value is not None:
                chunks.append(str(value))
            value = props.get(key)
            if value is not None:
                chunks.append(str(value))

        for field in self._as_list(component.get("fields")):
            if isinstance(field, dict):
                chunks.extend(str(field.get(key) or "") for key in ("label", "placeholder", "helpText", "fieldKey"))

        for column in self._as_list(component.get("columns")):
            if isinstance(column, dict):
                chunks.extend(str(column.get(key) or "") for key in ("label", "columnKey", "fieldKey"))

        for tab in self._as_list(component.get("tabs")):
            if isinstance(tab, dict):
                chunks.extend(str(tab.get(key) or "") for key in ("title", "tabKey"))

        return " ".join(item for item in chunks if item)

    def _visible_text(self, page: dict[str, Any], components: list[dict[str, Any]]) -> str:
        chunks = [
            self._string(page.get("pageName")),
            self._string(page.get("title")),
            self._string(page.get("description")),
        ]
        chunks.extend(self._component_text(item) for item in components)
        return " ".join(item for item in chunks if item)

    def _referenced_action_ids(self, components: list[dict[str, Any]]) -> list[tuple[str, str]]:
        result: list[tuple[str, str]] = []

        for component in components:
            cid = self._string(component.get("componentId")) or "<missing>"
            for index, action_id in enumerate(self._as_list(component.get("actionIds"))):
                value = self._string(action_id)
                if value:
                    result.append((value, f"$.page.componentTree..{cid}.actionIds[{index}]"))

            props = self._as_dict(component.get("props"))
            for index, action_id in enumerate(self._as_list(props.get("rowActionIds"))):
                value = self._string(action_id)
                if value:
                    result.append((value, f"$.page.componentTree..{cid}.props.rowActionIds[{index}]"))

            direct = self._string(component.get("actionId"))
            if direct:
                result.append((direct, f"$.page.componentTree..{cid}.actionId"))

        return result

    def _is_action_column(self, column: Any) -> bool:
        if not isinstance(column, dict):
            return False

        key = self._string(column.get("columnKey") or column.get("fieldKey")).lower()
        label = self._string(column.get("label")).strip().lower()
        ctype = self._string(column.get("type")).upper()

        if ctype == "ACTIONS":
            return True

        if key in self.ACTION_COLUMN_LABELS:
            return True

        if label in {item.lower() for item in self.ACTION_COLUMN_LABELS}:
            return True

        return False

    def _has_query_form(self, components: list[dict[str, Any]]) -> bool:
        for component in components:
            ctype = self._component_type(component)
            text = self._component_text(component)
            props = self._as_dict(component.get("props"))
            role = self._string(props.get("visualRole") or props.get("role"))
            if ctype == "FORM" and (self._contains_any(text, self.QUERY_WORDS) or self._contains_any(role, self.QUERY_WORDS)):
                return True
            if ctype == "SECTION" and self._contains_any(text, ("查询筛选", "搜索条件", "筛选条件")):
                return True
        return False

    def _has_large_edit_form(self, components: list[dict[str, Any]]) -> bool:
        for component in components:
            if self._component_type(component) != "FORM":
                continue
            fields = self._as_list(component.get("fields"))
            if len(fields) >= 4:
                return True
        return False

    def _has_primary_action(self, interactions: list[dict[str, Any]]) -> bool:
        return any(
            self._contains_any(self._string(item.get("label") or item.get("actionId")), ("新增", "新建", "创建", "导出", "保存", "提交", "发起", "上传"))
            for item in interactions
            if isinstance(item, dict)
        )

    def _has_submit_action(self, interactions: list[dict[str, Any]]) -> bool:
        return any(
            self._contains_any(self._string(item.get("label") or item.get("type") or item.get("actionId")), ("保存", "提交", "确认", "完成", "SUBMIT_FORM"))
            for item in interactions
            if isinstance(item, dict)
        )

    def _contract_has_demo_rows(
        self,
        contract: dict[str, Any],
        page: dict[str, Any],
        tables: list[dict[str, Any]],
    ) -> bool:
        candidates = [
            contract.get("mockData"),
            page.get("mockData"),
        ]
        for table in tables:
            props = self._as_dict(table.get("props"))
            candidates.extend([props.get("rows"), props.get("mockRows"), props.get("dataSource")])

        def has_rows(value: Any) -> bool:
            if isinstance(value, list):
                return len(value) > 0 and any(isinstance(item, dict) for item in value)
            if isinstance(value, dict):
                for key in ("rows", "items", "list", "records", "data", "tableData", "mockRows"):
                    if has_rows(value.get(key)):
                        return True
                return any(has_rows(item) for item in value.values())
            return False

        return any(has_rows(item) for item in candidates)

    def _contains_any(self, value: str, words: Iterable[str]) -> bool:
        source = self._string(value).lower()
        return any(str(word).lower() in source for word in words)

    def _count_by(self, items: list[dict[str, Any]], key_fn) -> dict[str, int]:
        result: dict[str, int] = {}
        for item in items:
            key = str(key_fn(item))
            result[key] = result.get(key, 0) + 1
        return dict(sorted(result.items()))

    def _unwrap_contract(self, raw: Any) -> dict[str, Any]:
        if isinstance(raw, list):
            for item in raw:
                unwrapped = self._unwrap_contract(item)
                if unwrapped:
                    return unwrapped
            return {}

        if not isinstance(raw, dict):
            return {}

        if "page" in raw and isinstance(raw.get("page"), dict):
            return raw

        for key in ("content", "contentJson", "artifactContent", "payload", "data", "result"):
            value = raw.get(key)
            if isinstance(value, dict):
                unwrapped = self._unwrap_contract(value)
                if unwrapped:
                    return unwrapped
            if isinstance(value, str):
                try:
                    parsed = json.loads(value)
                except Exception:
                    continue
                unwrapped = self._unwrap_contract(parsed)
                if unwrapped:
                    return unwrapped

        return raw

    def _as_dict(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _as_list(self, value: Any) -> list[Any]:
        if isinstance(value, list):
            return value
        return []

    def _string(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()


def load_json_documents(path: Path) -> list[tuple[str, dict[str, Any]]]:
    if not path.exists():
        raise FileNotFoundError(f"INPUT_NOT_FOUND: {path}")

    files: list[Path]
    if path.is_file():
        files = [path]
    else:
        files = sorted(item for item in path.rglob("*.json") if item.is_file())

    docs: list[tuple[str, dict[str, Any]]] = []

    for file in files:
        try:
            parsed = json.loads(file.read_text(encoding="utf-8", errors="replace"))
        except Exception as exc:
            docs.append((str(file), {"__parseError": str(exc)}))
            continue

        if isinstance(parsed, list):
            for index, item in enumerate(parsed):
                if isinstance(item, dict):
                    docs.append((f"{file}#{index}", item))
        elif isinstance(parsed, dict):
            docs.append((str(file), parsed))

    return docs


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def write_markdown_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    summary = report["summary"]
    lines = [
        "# UI_CONTRACT Prototype Quality Benchmark",
        "",
        f"- Version: `{report['version']}`",
        f"- Generated At: `{report['generatedAt']}`",
        f"- Total: **{summary['total']}**",
        f"- Pass: **{summary['pass']}**",
        f"- Warn: **{summary['warn']}**",
        f"- Fail: **{summary['fail']}**",
        f"- Average Score: **{summary['averageScore']}**",
        "",
        "## Cases",
        "",
        "| Status | Score | Pattern | Page Kind | Layout | Contract | Source |",
        "|---|---:|---|---|---|---|---|",
    ]

    for case in report["cases"]:
        lines.append(
            "| {status} | {score} | {pattern} | {kind} | {layout} | {name} | `{source}` |".format(
                status=case["status"],
                score=case["score"],
                pattern=case["inferredPattern"],
                kind=case["pageKind"] or "-",
                layout=case["layoutType"] or "-",
                name=str(case["contractName"]).replace("|", "\\|"),
                source=str(case["source"]).replace("|", "\\|"),
            )
        )

    for case in report["cases"]:
        if not case["blockers"] and not case["warnings"] and not case["infos"]:
            continue

        lines.extend(["", f"## {case['status']} · {case['contractName']} · {case['score']} 分", ""])

        for severity, title in (("blockers", "阻断项"), ("warnings", "警告项"), ("infos", "提示项")):
            items = case[severity]
            if not items:
                continue
            lines.extend([f"### {title}", ""])
            for item in items:
                line = f"- **{item['rule']}** `{item['path']}`：{item['message']}"
                if item.get("suggestion"):
                    line += f" 建议：{item['suggestion']}"
                lines.append(line)
            lines.append("")

    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def build_report(results: list[QualityCaseResult], input_path: str) -> dict[str, Any]:
    cases = [item.to_dict() for item in results]
    total = len(cases)
    pass_count = sum(1 for item in cases if item["status"] == "PASS")
    warn_count = sum(1 for item in cases if item["status"] == "WARN")
    fail_count = sum(1 for item in cases if item["status"] == "FAIL")
    average = 0 if total == 0 else round(sum(int(item["score"]) for item in cases) / total, 2)

    return {
        "version": A9_VERSION,
        "generatedAt": _dt.datetime.now().isoformat(timespec="seconds"),
        "input": input_path,
        "summary": {
            "total": total,
            "pass": pass_count,
            "warn": warn_count,
            "fail": fail_count,
            "averageScore": average,
        },
        "cases": cases,
    }


def create_fixture_contract(pattern: str) -> dict[str, Any]:
    base = {
        "schemaVersion": "1.0",
        "artifactType": "UI_CONTRACT",
        "identity": {
            "projectId": "benchmark",
            "pageId": f"{pattern.lower()}_page",
            "pageKey": f"{pattern.lower()}_page",
            "pageName": "",
            "pageKind": "",
        },
        "page": {
            "pageId": f"{pattern.lower()}_page",
            "pageKey": f"{pattern.lower()}_page",
            "pageName": "",
            "pageKind": "",
            "route": f"/benchmark/{pattern.lower()}",
            "layout": {"type": "CUSTOM_GRID", "columns": 12, "regions": []},
            "componentTree": [],
        },
        "interactions": [],
        "mockScenarios": [],
        "apiBindings": [],
        "featureCoverage": [],
        "qualitySummary": {
            "score": 90,
            "coverageRate": 100,
            "blockers": [],
            "warnings": [],
        },
        "analysisSummary": f"A9.10.2 benchmark fixture for {pattern}.",
    }

    def header(title: str, eyebrow: str = "业务页面") -> dict[str, Any]:
        return {
            "componentId": f"{pattern.lower()}_header",
            "type": "PAGE_HEADER",
            "title": title,
            "description": "用于验证页面模式、结构质量和可编辑能力。",
            "props": {"eyebrow": eyebrow, "visualRole": "enterprisePageHeader"},
        }

    if pattern == "list":
        base["identity"]["pageName"] = base["page"]["pageName"] = "入库单列表"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "LIST_PAGE"
        base["page"]["layout"]["type"] = "TABLE_LIST"
        base["page"]["componentTree"] = [
            header("入库单列表", "查询列表"),
            {
                "componentId": "filter_section",
                "type": "SECTION",
                "title": "查询筛选",
                "children": [
                    {
                        "componentId": "filter_form",
                        "type": "FORM",
                        "title": "查询条件",
                        "fields": [
                            {"fieldKey": "order_no", "label": "入库单号", "type": "INPUT", "placeholder": "请输入入库单号"},
                            {"fieldKey": "status", "label": "入库状态", "type": "SELECT", "placeholder": "请选择状态"},
                        ],
                        "actionIds": ["query", "reset"],
                    }
                ],
            },
            {
                "componentId": "table_section",
                "type": "SECTION",
                "title": "数据列表",
                "children": [
                    {
                        "componentId": "inbound_table",
                        "type": "TABLE",
                        "title": "入库单数据",
                        "columns": [
                            {"columnKey": "order_no", "fieldKey": "order_no", "label": "入库单号", "type": "TEXT"},
                            {"columnKey": "supplier", "fieldKey": "supplier", "label": "供应商", "type": "TEXT"},
                            {"columnKey": "status", "fieldKey": "status", "label": "状态", "type": "TAG"},
                        ],
                        "props": {
                            "rowActionIds": ["view", "delete"],
                            "mockRows": [
                                {"order_no": "RK20260805001", "supplier": "华南供应链", "status": "待提交"},
                                {"order_no": "RK20260805002", "supplier": "北区仓配", "status": "已完成"},
                            ],
                        },
                        "actionIds": ["create", "export"],
                    }
                ],
            },
            {"componentId": "empty_state", "type": "EMPTY_STATE", "title": "暂无入库单数据", "description": "调整查询条件后重试。"},
        ]
        base["interactions"] = [
            {"actionId": "query", "type": "QUERY_DATA", "targetId": "filter_form", "label": "查询"},
            {"actionId": "reset", "type": "RESET_FORM", "targetId": "filter_form", "label": "重置"},
            {"actionId": "create", "type": "NAVIGATE", "targetId": "inbound_table", "label": "新增"},
            {"actionId": "export", "type": "QUERY_DATA", "targetId": "inbound_table", "label": "导出"},
            {"actionId": "view", "type": "OPEN_MODAL", "targetId": "inbound_table", "label": "查看"},
            {"actionId": "delete", "type": "DELETE_ROW", "targetId": "inbound_table", "label": "删除"},
        ]
        base["mockScenarios"] = [{"scenarioKey": "query_success", "actionId": "query", "title": "查询成功"}]

    elif pattern == "bad_duplicate_action_columns":
        base = create_fixture_contract("list")
        base["page"]["pageName"] = base["identity"]["pageName"] = "重复操作列示例"
        table = base["page"]["componentTree"][2]["children"][0]
        table["columns"].append({"columnKey": "actions", "fieldKey": "actions", "label": "操作", "type": "ACTIONS"})
        table["columns"].append({"columnKey": "operation", "fieldKey": "operation", "label": "操作", "type": "ACTIONS"})
        base["qualitySummary"]["score"] = 100

    elif pattern == "bad_form_with_filter":
        base = create_fixture_contract("form")
        base["page"]["pageName"] = base["identity"]["pageName"] = "创建页混入查询筛选示例"
        base["page"]["componentTree"].insert(
            1,
            {
                "componentId": "wrong_filter_section",
                "type": "SECTION",
                "title": "查询筛选",
                "children": [
                    {
                        "componentId": "wrong_filter_form",
                        "type": "FORM",
                        "title": "查询条件",
                        "fields": [{"fieldKey": "keyword", "label": "关键词", "type": "INPUT", "placeholder": "请输入关键词"}],
                        "actionIds": ["query"],
                    }
                ],
            },
        )
        base["interactions"].append({"actionId": "query", "type": "QUERY_DATA", "targetId": "wrong_filter_form", "label": "查询"})
        base["qualitySummary"]["score"] = 100

    elif pattern == "form":
        base["identity"]["pageName"] = base["page"]["pageName"] = "入库单创建"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "FORM_PAGE"
        base["page"]["layout"]["type"] = "FORM_DETAIL"
        base["page"]["componentTree"] = [
            header("入库单创建", "业务表单"),
            {
                "componentId": "basic_form_section",
                "type": "SECTION",
                "title": "基本信息",
                "children": [
                    {
                        "componentId": "basic_form",
                        "type": "FORM",
                        "title": "基础资料",
                        "fields": [
                            {"fieldKey": "order_no", "label": "入库单号", "type": "INPUT", "placeholder": "系统自动生成"},
                            {"fieldKey": "supplier", "label": "供应商", "type": "SELECT", "placeholder": "请选择供应商"},
                            {"fieldKey": "warehouse", "label": "入库仓库", "type": "SELECT", "placeholder": "请选择仓库"},
                            {"fieldKey": "remark", "label": "备注", "type": "TEXTAREA", "placeholder": "请输入备注"},
                        ],
                        "actionIds": ["save", "submit", "cancel"],
                    }
                ],
            },
            {
                "componentId": "detail_table_section",
                "type": "SECTION",
                "title": "入库明细",
                "children": [
                    {
                        "componentId": "detail_table",
                        "type": "TABLE",
                        "title": "商品明细",
                        "columns": [
                            {"columnKey": "sku", "fieldKey": "sku", "label": "商品编码", "type": "TEXT"},
                            {"columnKey": "name", "fieldKey": "name", "label": "商品名称", "type": "TEXT"},
                            {"columnKey": "qty", "fieldKey": "qty", "label": "数量", "type": "NUMBER"},
                        ],
                        "props": {"mockRows": [{"sku": "SKU001", "name": "标准件", "qty": 10}]},
                    }
                ],
            },
            {"componentId": "form_tip", "type": "ALERT", "title": "保存提示", "description": "提交前请确认供应商和仓库信息。"},
        ]
        base["interactions"] = [
            {"actionId": "save", "type": "SUBMIT_FORM", "targetId": "basic_form", "label": "保存"},
            {"actionId": "submit", "type": "SUBMIT_FORM", "targetId": "basic_form", "label": "提交"},
            {"actionId": "cancel", "type": "NAVIGATE", "targetId": "basic_form", "label": "取消"},
        ]
        base["mockScenarios"] = [{"scenarioKey": "save_success", "actionId": "save", "title": "保存成功"}]

    elif pattern == "detail":
        base["identity"]["pageName"] = base["page"]["pageName"] = "入库单详情"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "DETAIL_PAGE"
        base["page"]["layout"]["type"] = "DETAIL_TABS"
        base["page"]["componentTree"] = [
            header("入库单详情", "业务详情"),
            {"componentId": "summary_card", "type": "CARD", "title": "当前状态", "description": "待审核 · 3 项商品 · 合计 120 件"},
            {
                "componentId": "detail_info",
                "type": "DESCRIPTION_LIST",
                "title": "基础信息",
                "fields": [
                    {"fieldKey": "order_no", "label": "入库单号", "type": "INPUT"},
                    {"fieldKey": "supplier", "label": "供应商", "type": "INPUT"},
                ],
            },
            {"componentId": "process_steps", "type": "STEPS", "title": "处理进度", "props": {"items": ["创建", "审核", "入库"]}},
            {
                "componentId": "record_table",
                "type": "TABLE",
                "title": "操作记录",
                "columns": [
                    {"columnKey": "time", "fieldKey": "time", "label": "时间", "type": "DATETIME"},
                    {"columnKey": "operator", "fieldKey": "operator", "label": "操作人", "type": "TEXT"},
                    {"columnKey": "result", "fieldKey": "result", "label": "结果", "type": "TEXT"},
                ],
                "props": {"mockRows": [{"time": "2026-08-05 10:00", "operator": "王五", "result": "创建"}]},
            },
        ]

    elif pattern == "dashboard":
        base["identity"]["pageName"] = base["page"]["pageName"] = "仓储工作台"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "DASHBOARD_PAGE"
        base["page"]["layout"]["type"] = "DASHBOARD"
        base["page"]["componentTree"] = [
            header("仓储工作台", "数据看板"),
            {"componentId": "metric_inbound", "type": "CARD", "title": "今日入库", "description": "128 单"},
            {"componentId": "metric_pending", "type": "CARD", "title": "待审核", "description": "18 单"},
            {"componentId": "metric_exception", "type": "CARD", "title": "异常预警", "description": "3 项"},
            {
                "componentId": "todo_table",
                "type": "TABLE",
                "title": "待办任务",
                "columns": [
                    {"columnKey": "task", "fieldKey": "task", "label": "任务", "type": "TEXT"},
                    {"columnKey": "owner", "fieldKey": "owner", "label": "负责人", "type": "TEXT"},
                    {"columnKey": "deadline", "fieldKey": "deadline", "label": "截止时间", "type": "DATETIME"},
                ],
                "props": {"mockRows": [{"task": "审核入库单", "owner": "李四", "deadline": "今日 18:00"}]},
            },
        ]

    elif pattern == "workflow":
        base["identity"]["pageName"] = base["page"]["pageName"] = "入库审批"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "WORKFLOW_PAGE"
        base["page"]["layout"]["type"] = "APPROVAL_WORKBENCH"
        base["page"]["componentTree"] = [
            header("入库审批", "审批流程"),
            {"componentId": "approval_steps", "type": "STEPS", "title": "审批进度", "props": {"items": ["提交", "主管审核", "仓库确认"]}},
            {
                "componentId": "opinion_form",
                "type": "FORM",
                "title": "处理意见",
                "fields": [{"fieldKey": "opinion", "label": "审批意见", "type": "TEXTAREA", "placeholder": "请输入处理意见"}],
                "actionIds": ["approve", "reject"],
            },
            {
                "componentId": "approval_record",
                "type": "TABLE",
                "title": "审批记录",
                "columns": [
                    {"columnKey": "node", "fieldKey": "node", "label": "节点", "type": "TEXT"},
                    {"columnKey": "operator", "fieldKey": "operator", "label": "处理人", "type": "TEXT"},
                ],
                "props": {"mockRows": [{"node": "提交", "operator": "张三"}]},
            },
        ]
        base["interactions"] = [
            {"actionId": "approve", "type": "CONFIRM_ACTION", "targetId": "opinion_form", "label": "通过"},
            {"actionId": "reject", "type": "CONFIRM_ACTION", "targetId": "opinion_form", "label": "驳回"},
        ]

    elif pattern == "settings":
        base["identity"]["pageName"] = base["page"]["pageName"] = "系统参数设置"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "SETTINGS_PAGE"
        base["page"]["layout"]["type"] = "SETTINGS"
        base["page"]["componentTree"] = [
            header("系统参数设置", "配置中心"),
            {
                "componentId": "settings_form",
                "type": "FORM",
                "title": "基础配置",
                "fields": [
                    {"fieldKey": "auto_audit", "label": "自动审核", "type": "SWITCH"},
                    {"fieldKey": "warning_threshold", "label": "预警阈值", "type": "NUMBER"},
                ],
                "actionIds": ["save"],
            },
            {"componentId": "risk_alert", "type": "ALERT", "title": "配置提示", "description": "参数变更将影响后续入库流程。"},
        ]
        base["interactions"] = [{"actionId": "save", "type": "SUBMIT_FORM", "targetId": "settings_form", "label": "保存配置"}]

    elif pattern == "upload":
        base["identity"]["pageName"] = base["page"]["pageName"] = "批量导入入库单"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "UPLOAD_PAGE"
        base["page"]["layout"]["type"] = "FORM_DETAIL"
        base["page"]["componentTree"] = [
            header("批量导入入库单", "上传导入"),
            {
                "componentId": "upload_form",
                "type": "FORM",
                "title": "上传设置",
                "fields": [
                    {"fieldKey": "file", "label": "上传文件", "type": "INPUT", "placeholder": "请选择 Excel 文件"},
                    {"fieldKey": "warehouse", "label": "默认仓库", "type": "SELECT"},
                ],
                "actionIds": ["upload", "validate"],
            },
            {
                "componentId": "file_table",
                "type": "TABLE",
                "title": "导入结果",
                "columns": [
                    {"columnKey": "row", "fieldKey": "row", "label": "行号", "type": "NUMBER"},
                    {"columnKey": "result", "fieldKey": "result", "label": "校验结果", "type": "TEXT"},
                ],
                "props": {"mockRows": [{"row": 1, "result": "通过"}]},
            },
            {"componentId": "upload_alert", "type": "ALERT", "title": "模板要求", "description": "请使用系统模板上传。"},
        ]
        base["interactions"] = [
            {"actionId": "upload", "type": "SUBMIT_FORM", "targetId": "upload_form", "label": "上传"},
            {"actionId": "validate", "type": "QUERY_DATA", "targetId": "upload_form", "label": "校验"},
        ]

    elif pattern == "auth":
        base["identity"]["pageName"] = base["page"]["pageName"] = "用户登录"
        base["identity"]["pageKind"] = base["page"]["pageKind"] = "LOGIN_PAGE"
        base["page"]["layout"]["type"] = "CENTERED_FORM"
        base["page"]["componentTree"] = [
            {
                "componentId": "login_header",
                "type": "PAGE_HEADER",
                "title": "欢迎登录",
                "description": "请输入账号和密码登录系统。",
                "props": {"eyebrow": "CodeMind"},
            },
            {
                "componentId": "login_form",
                "type": "FORM",
                "title": "登录信息",
                "fields": [
                    {"fieldKey": "account", "label": "账号", "type": "INPUT", "placeholder": "请输入账号"},
                    {"fieldKey": "password", "label": "密码", "type": "PASSWORD", "placeholder": "请输入密码"},
                ],
                "actionIds": ["login"],
            },
        ]
        base["interactions"] = [{"actionId": "login", "type": "SUBMIT_FORM", "targetId": "login_form", "label": "登录"}]
        base["mockScenarios"] = [{"scenarioKey": "login_success", "actionId": "login", "title": "登录成功"}]

    else:
        raise ValueError(f"UNKNOWN_FIXTURE_PATTERN: {pattern}")

    return base


def write_fixtures(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    patterns = [
        "list",
        "form",
        "detail",
        "dashboard",
        "workflow",
        "settings",
        "upload",
        "auth",
        "bad_duplicate_action_columns",
        "bad_form_with_filter",
    ]

    for pattern in patterns:
        write_json(output_dir / f"{pattern}.ui_contract.json", create_fixture_contract(pattern))


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="A9.10.2 UI_CONTRACT Prototype Quality Benchmark",
    )
    parser.add_argument(
        "--input",
        "-i",
        type=Path,
        default=None,
        help="UI_CONTRACT JSON file or directory. Directory mode scans *.json recursively.",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=Path("generated/ui_contract_quality_benchmark"),
        help="Report output directory.",
    )
    parser.add_argument(
        "--min-score",
        type=int,
        default=80,
        help="Minimum score used by --strict.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit with code 1 if any case FAILs or average score is below --min-score.",
    )
    parser.add_argument(
        "--write-fixtures",
        type=Path,
        default=None,
        help="Write built-in benchmark fixtures into this directory and exit unless --input is also provided.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    if args.write_fixtures:
        write_fixtures(args.write_fixtures)
        print(f"Fixtures written: {args.write_fixtures}")

        if args.input is None:
            print("Run benchmark example:")
            print(f"  python scripts/benchmark_ui_contract_quality.py --input {args.write_fixtures} --output {args.output}")
            return 0

    if args.input is None:
        print("Missing --input. Use --write-fixtures to create sample contracts first.", file=sys.stderr)
        return 2

    checker = UiContractQualityBenchmark()
    results: list[QualityCaseResult] = []
    docs = load_json_documents(args.input)

    for source, doc in docs:
        if "__parseError" in doc:
            issue = QualityIssue(BLOCKER, "json.parse", f"JSON 解析失败：{doc['__parseError']}", "$")
            results.append(
                QualityCaseResult(
                    source=source,
                    contract_name=Path(source.split("#", 1)[0]).name,
                    inferred_pattern="UNKNOWN",
                    page_kind="",
                    layout_type="",
                    score=0,
                    status="FAIL",
                    blockers=[issue],
                    warnings=[],
                    infos=[],
                    metrics={},
                )
            )
            continue

        results.append(checker.evaluate(doc, source=source))

    report = build_report(results, str(args.input))
    json_path = args.output / "quality-report.json"
    md_path = args.output / "quality-report.md"
    write_json(json_path, report)
    write_markdown_report(md_path, report)

    summary = report["summary"]
    print(f"A9.10.2 benchmark done: total={summary['total']} pass={summary['pass']} warn={summary['warn']} fail={summary['fail']} avg={summary['averageScore']}")
    print(f"JSON report: {json_path}")
    print(f"Markdown report: {md_path}")

    if args.strict and (summary["fail"] > 0 or float(summary["averageScore"]) < args.min_score):
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
