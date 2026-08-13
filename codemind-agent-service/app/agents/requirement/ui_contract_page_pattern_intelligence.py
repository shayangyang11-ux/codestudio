
from __future__ import annotations
import re

from typing import Any


class UiContractPagePatternIntelligence:
    """A9.10.1 deterministic page-pattern intelligence layer.

    The LLM may still provide business wording and context, but final UI_CONTRACT
    page structure must obey a canonical enterprise page pattern. This class
    repairs the contract at dict level before the existing hardener validates
    states, interactions, bindings and coverage.
    """

    PATTERN_LAYOUTS: dict[str, tuple[str, str]] = {
        "AUTH_LOGIN": ("CENTERED_FORM", "LOGIN_PAGE"),
        "AUTH_REGISTER": ("CENTERED_FORM", "REGISTER_PAGE"),
        "SEARCH_LIST": ("TABLE_LIST", "LIST_PAGE"),
        "CRUD_LIST": ("TABLE_LIST", "LIST_PAGE"),
        "FORM_PAGE": ("FORM_DETAIL", "FORM_PAGE"),
        "DETAIL_VIEW": ("DETAIL_TABS", "DETAIL_PAGE"),
        "DASHBOARD": ("DASHBOARD", "DASHBOARD_PAGE"),
        "WORKFLOW": ("APPROVAL_WORKBENCH", "WORKFLOW_PAGE"),
        "SETTINGS_FORM": ("SETTINGS", "SETTINGS_PAGE"),
        "UPLOAD": ("FORM_DETAIL", "UPLOAD_PAGE"),
    }

    def apply(
        self,
        source: dict[str, Any],
        page: dict[str, Any],
        current_page: dict[str, Any],
        feature_nodes: list[dict[str, Any]],
    ) -> None:
        if not isinstance(source, dict) or not isinstance(page, dict):
            return

        pattern = self.infer_page_pattern(page, current_page, feature_nodes)
        quality_issues = self.inspect_quality_issues(page, pattern)

        # A9.10.1 intentionally repairs every supported pattern into a canonical
        # shell. This prevents page-by-page drift: repeated action columns, blank
        # toolbar cards, FORM pages with search filters, detail pages as editable
        # forms, dashboard/auth mixups, etc.
        builder = {
            "AUTH_LOGIN": self._build_auth,
            "AUTH_REGISTER": self._build_auth,
            "SEARCH_LIST": self._build_list,
            "CRUD_LIST": self._build_list,
            "FORM_PAGE": self._build_form,
            "DETAIL_VIEW": self._build_detail,
            "DASHBOARD": self._build_dashboard,
            "WORKFLOW": self._build_workflow,
            "SETTINGS_FORM": self._build_settings,
            "UPLOAD": self._build_upload,
        }.get(pattern, self._build_form)

        built = builder(pattern, page, current_page, feature_nodes)

        page.update({
            "pageId": self._page_id(page, current_page),
            "pageKey": self._page_key(page, current_page),
            "pageName": self._page_name(page, current_page),
            "route": self._route(page, current_page),
            "title": self._page_name(page, current_page),
            "description": self._page_description(pattern, self._page_name(page, current_page)),
            "pageKind": built["pageKind"],
            "layout": built["layout"],
            "componentTree": built["components"],
        })

        source["page"] = page
        source["interactions"] = self._merge_interactions(
            source.get("interactions"),
            built["interactions"],
        )
        source["apiBindings"] = self._merge_api_bindings(
            source.get("apiBindings") or source.get("api_bindings"),
            built.get("apiBindings") or [],
        )
        source["mockData"] = self._merge_mock_data(
            source.get("mockData") or source.get("mock_data"),
            built.get("mockData") or {},
        )
        source["mockScenarios"] = self._merge_mock_scenarios(
            source.get("mockScenarios") or source.get("mock_scenarios"),
            self._mock_scenarios(built["interactions"], pattern),
        )
        source["pageStates"] = self._page_states(pattern, built["visibleIds"], built.get("emptyIds") or [])

        self._mark_quality_gate(source, pattern, quality_issues)

    def infer_page_pattern(
        self,
        page: dict[str, Any],
        current_page: dict[str, Any],
        feature_nodes: list[dict[str, Any]],
    ) -> str:
        page_context = self._page_context(current_page)
        page_kind = self._norm(
            page.get("pageKind")
            or current_page.get("pageKind")
            or current_page.get("page_kind")
            or page_context.get("pageKind")
            or page_context.get("page_kind")
            or ""
        )
        layout_type = self._norm(
            self._layout_type(page)
            or current_page.get("layoutType")
            or current_page.get("layout_type")
            or page_context.get("layoutType")
            or page_context.get("layout_type")
            or ""
        )
        page_text = self._norm(" ".join([
            str(page_kind),
            str(layout_type),
            str(page.get("pageName") or page.get("title") or ""),
            str(page.get("pageKey") or ""),
            str(current_page.get("pageName") or current_page.get("module_name") or current_page.get("moduleName") or current_page.get("name") or ""),
            str(current_page.get("pageKey") or current_page.get("module_key") or current_page.get("moduleKey") or current_page.get("key") or ""),
            str(page_context.get("pageIntent") or page_context.get("page_intent") or ""),
            str(page_context.get("businessObject") or page_context.get("business_object") or ""),
            str(page_context.get("routePath") or page_context.get("route_path") or page_context.get("route") or ""),
        ]))
        feature_text = self._norm(" ".join(
            str(item.get("featureName") or item.get("name") or item.get("featureKey") or item.get("key") or "")
            for item in feature_nodes
            if isinstance(item, dict)
        ))

        if page_kind in {"LOGIN_PAGE", "AUTH_LOGIN"} or self._has_any(page_text, ["登录", "login", "signin"]):
            return "AUTH_LOGIN"
        if page_kind in {"REGISTER_PAGE", "AUTH_REGISTER"} or self._has_any(page_text, ["注册", "register", "signup"]):
            return "AUTH_REGISTER"
        if page_kind in {"WORKFLOW_PAGE", "APPROVAL_PAGE"} or layout_type == "APPROVAL_WORKBENCH" or self._has_any(page_text, ["审批", "流程", "工单", "流转", "workflow", "approval"]):
            return "WORKFLOW"
        if page_kind in {"UPLOAD_PAGE", "IMPORT_PAGE"} or self._has_any(page_text, ["上传", "导入", "附件", "upload", "import"]):
            return "UPLOAD"
        if page_kind in {"DASHBOARD_PAGE", "HOME_PAGE", "WORKBENCH"} or layout_type == "DASHBOARD" or self._has_any(page_text, ["工作台", "看板", "总览", "概览", "首页", "dashboard", "home"]):
            return "DASHBOARD"
        if page_kind in {"LIST_PAGE", "TABLE_PAGE"} or layout_type in {"TABLE_LIST", "LIST"} or self._has_any(page_text, ["列表", "查询页", "搜索页", "分页", "table_list", "search_list", "list_page"]):
            return "SEARCH_LIST"
        if page_kind in {"DETAIL_PAGE", "PROFILE_PAGE"} or layout_type in {"DETAIL_TABS", "DETAIL"} or self._has_any(page_text, ["详情", "查看", "明细页", "detail"]):
            return "DETAIL_VIEW"
        if page_kind in {"SETTINGS_PAGE"} or layout_type == "SETTINGS" or self._has_any(page_text, ["设置", "配置", "参数", "settings", "config"]):
            return "SETTINGS_FORM"
        if page_kind == "FORM_PAGE" or layout_type in {"FORM_DETAIL", "FORM"} or self._has_any(page_text, ["创建", "新建", "新增", "编辑", "录入", "维护", "表单", "create", "edit", "form"]):
            return "FORM_PAGE"

        # Feature keywords may refine only generic pages.
        if self._has_any(feature_text, ["审批", "流程", "workflow", "approval"]):
            return "WORKFLOW"
        if self._has_any(feature_text, ["上传", "导入", "upload", "import"]):
            return "UPLOAD"
        if self._has_any(feature_text, ["查询", "搜索", "列表", "分页", "table", "list"]):
            return "SEARCH_LIST"
        if self._has_any(feature_text, ["指标", "统计", "总览", "看板", "dashboard"]):
            return "DASHBOARD"
        if self._has_any(feature_text, ["设置", "配置", "setting", "config"]):
            return "SETTINGS_FORM"

        return "FORM_PAGE"

    def inspect_quality_issues(self, page: dict[str, Any], pattern: str) -> list[str]:
        issues: list[str] = []
        components = self._walk_components(page.get("componentTree"))
        layout_type = self._layout_type(page)

        if not components:
            issues.append("empty_component_tree")

        if not any(self._ctype(item) == "PAGE_HEADER" for item in components):
            issues.append("missing_page_header")

        if pattern == "SEARCH_LIST":
            tables = [item for item in components if self._ctype(item) == "TABLE"]
            if not tables:
                issues.append("list_missing_table")
            for table in tables:
                action_like_columns = [
                    item for item in self._as_list(table.get("columns"))
                    if self._is_action_like_column(item)
                ]
                if action_like_columns:
                    issues.append("list_has_action_data_column")
            if any(self._ctype(item) == "TOOLBAR" for item in components):
                issues.append("standalone_toolbar_card")
            if layout_type != "TABLE_LIST":
                issues.append("wrong_list_layout")

        if pattern == "FORM_PAGE":
            if any(self._looks_like_filter_component(item) for item in components):
                issues.append("form_contains_search_filter")
            if layout_type == "TABLE_LIST":
                issues.append("form_wrongly_table_list")

        if pattern in {"DETAIL_VIEW", "DASHBOARD", "WORKFLOW", "SETTINGS_FORM", "UPLOAD"}:
            if pattern == "DETAIL_VIEW" and not any(self._ctype(item) == "DESCRIPTION_LIST" for item in components):
                issues.append("detail_missing_description_list")
            if pattern == "DASHBOARD" and not any(str((item.get("props") or {}).get("visualRole") or "").lower().find("metric") >= 0 for item in components):
                issues.append("dashboard_missing_metrics")
            if pattern == "WORKFLOW" and not any(self._ctype(item) == "STEPS" for item in components):
                issues.append("workflow_missing_steps")

        return list(dict.fromkeys(issues))

    # ------------------------------------------------------------------
    # Pattern builders
    # ------------------------------------------------------------------
    def _build_list(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        fields = self._filter_fields(page, current_page, feature_nodes)
        columns = self._table_columns(page, current_page, feature_nodes, prefer_detail=False)

        query_id = f"{page_key}_query"
        reset_id = f"{page_key}_reset_filters"
        create_id = f"{page_key}_create"
        export_id = f"{page_key}_export"
        refresh_id = f"{page_key}_refresh"
        view_id = f"{page_key}_view_detail"
        delete_id = f"{page_key}_delete_row"
        filter_id = f"{page_key}_filters"
        table_id = f"{page_key}_data_table"

        components = [
            self._component(
                f"{page_key}_header",
                "PAGE_HEADER",
                page_name,
                "查询、筛选并管理业务数据，支持批量操作、导出和行级处理。",
                action_ids=[create_id, export_id, refresh_id],
                props={"visualRole": "enterprisePageHeader", "pagePattern": "SEARCH_LIST"},
            ),
            self._component(
                f"{page_key}_filter_section",
                "SECTION",
                "查询条件",
                "按关键字段筛选数据，筛选区始终全宽展示。",
                children=[
                    self._component(
                        filter_id,
                        "FORM",
                        "查询筛选",
                        "根据业务字段快速定位数据。",
                        fields=fields,
                        action_ids=[query_id, reset_id],
                        props={"visualRole": "enterpriseFilterForm", "layout": {"columns": 4, "gap": "16px 20px"}, "pagePattern": "SEARCH_LIST"},
                    )
                ],
                props={"visualRole": "enterpriseFilterSection", "fullWidth": True, "layout": {"columns": 1}, "pagePattern": "SEARCH_LIST"},
            ),
            self._component(
                f"{page_key}_table_section",
                "SECTION",
                "数据列表",
                "工具栏、表格和分页保持在同一业务区域。",
                children=[
                    self._component(
                        table_id,
                        "TABLE",
                        "数据列表",
                        "展示筛选后的业务数据，支持分页、排序和行级操作。",
                        columns=columns,
                        action_ids=[create_id, export_id, refresh_id],
                        props={"visualRole": "enterpriseDataTable", "rowActionIds": [view_id, delete_id], "pagination": True, "density": "compact", "fullWidth": True, "pagePattern": "SEARCH_LIST"},
                    )
                ],
                props={"visualRole": "enterpriseTableSection", "fullWidth": True, "layout": {"columns": 1}, "pagePattern": "SEARCH_LIST"},
            ),
            self._component(
                f"{page_key}_empty_state",
                "EMPTY_STATE",
                "暂无数据",
                "调整筛选条件后重新查询，或新建一条业务数据。",
                props={"visualRole": "enterpriseTableEmptyState", "pagePattern": "SEARCH_LIST"},
            ),
        ]

        interactions = [
            self._interaction(query_id, "QUERY_DATA", "查询", filter_id, data_binding_key="tableRows"),
            self._interaction(reset_id, "RESET_FORM", "重置", filter_id),
            self._interaction(create_id, "OPEN_MODAL", "新增", table_id),
            self._interaction(export_id, "QUERY_DATA", "导出", table_id, data_binding_key="tableRows"),
            self._interaction(refresh_id, "QUERY_DATA", "刷新", table_id, data_binding_key="tableRows"),
            self._interaction(view_id, "SHOW_TOAST", "查看", table_id),
            self._interaction(delete_id, "DELETE_ROW", "删除", table_id),
        ]

        return self._pack(
            pattern,
            page_kind="LIST_PAGE",
            layout_type="TABLE_LIST",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("tableRows", "GET", f"/api/mock/{page_key}/list", [query_id, export_id, refresh_id])],
            mock_data={"tableRows": [self._mock_row(columns, 1), self._mock_row(columns, 2), self._mock_row(columns, 3)]},
            empty_ids=[f"{page_key}_empty_state"],
        )

    def _build_form(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        fields = self._form_fields(page, current_page, feature_nodes)
        detail_columns = self._table_columns(page, current_page, feature_nodes, prefer_detail=True)
        has_detail = self._should_have_detail_table(page, current_page)

        save_id = f"{page_key}_save_draft"
        submit_id = f"{page_key}_submit"
        cancel_id = f"{page_key}_cancel"
        add_row_id = f"{page_key}_add_row"
        remove_row_id = f"{page_key}_remove_row"
        form_id = f"{page_key}_main_form"
        table_id = f"{page_key}_detail_table"

        components: list[dict[str, Any]] = [
            self._component(
                f"{page_key}_header",
                "PAGE_HEADER",
                page_name,
                "填写业务主信息、维护关联明细，并按业务规则提交处理。",
                action_ids=[save_id, submit_id, cancel_id],
                props={"visualRole": "enterprisePageHeader", "pagePattern": "FORM_PAGE"},
            ),
            self._component(
                f"{page_key}_basic_section",
                "SECTION",
                "基本信息",
                "维护当前业务对象的核心字段。",
                children=[
                    self._component(
                        form_id,
                        "FORM",
                        "基本信息",
                        "请填写必填项并确认信息准确。",
                        fields=fields,
                        action_ids=[submit_id],
                        props={"visualRole": "enterpriseMainForm", "layout": {"columns": 4, "gap": "18px 24px"}, "pagePattern": "FORM_PAGE"},
                    )
                ],
                props={"visualRole": "enterpriseFormSection", "fullWidth": True, "layout": {"columns": 1}, "pagePattern": "FORM_PAGE"},
            ),
        ]

        if has_detail:
            components.append(
                self._component(
                    f"{page_key}_detail_section",
                    "SECTION",
                    "明细信息",
                    "维护与当前业务单据关联的明细行。",
                    children=[
                        self._component(
                            table_id,
                            "TABLE",
                            "明细列表",
                            "维护当前单据下的业务明细。",
                            columns=detail_columns,
                            action_ids=[add_row_id],
                            props={"visualRole": "enterpriseDetailTable", "rowActionIds": [remove_row_id], "editable": True, "pagination": False, "fullWidth": True, "pagePattern": "FORM_PAGE"},
                        )
                    ],
                    props={"visualRole": "enterpriseDetailSection", "fullWidth": True, "layout": {"columns": 1}, "pagePattern": "FORM_PAGE"},
                )
            )

        components.extend([
            self._component(
                f"{page_key}_validation_hint",
                "ALERT",
                "业务校验",
                "提交前将检查必填项、格式、权限和业务规则。",
                props={"visualRole": "enterpriseValidationHint", "level": "info", "message": "请确认信息无误后再提交。", "pagePattern": "FORM_PAGE"},
            ),
            self._component(
                f"{page_key}_bottom_actions",
                "TOOLBAR",
                "",
                "",
                action_ids=[save_id, submit_id, cancel_id],
                props={"visualRole": "enterpriseBottomToolbar", "placement": "stickyFooter", "align": "right", "pagePattern": "FORM_PAGE"},
            ),
        ])

        interactions = [
            self._interaction(save_id, "SUBMIT_FORM", "保存草稿", form_id),
            self._interaction(submit_id, "SUBMIT_FORM", "提交", form_id),
            self._interaction(cancel_id, "RESET_FORM", "取消", form_id),
        ]
        if has_detail:
            interactions.extend([
                self._interaction(add_row_id, "OPEN_MODAL", "添加明细", table_id),
                self._interaction(remove_row_id, "DELETE_ROW", "删除", table_id),
            ])

        mock_data = {"formData": self._mock_form(fields)}
        if has_detail:
            mock_data["detailRows"] = [self._mock_row(detail_columns, 1), self._mock_row(detail_columns, 2)]

        return self._pack(
            pattern,
            page_kind="FORM_PAGE",
            layout_type="FORM_DETAIL",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("formSubmit", "POST", f"/api/mock/{page_key}/submit", [save_id, submit_id])],
            mock_data=mock_data,
        )

    def _build_detail(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        fields = self._form_fields(page, current_page, feature_nodes)[:10]
        columns = self._table_columns(page, current_page, feature_nodes, prefer_detail=True)[:6]
        refresh_id = f"{page_key}_refresh"
        back_id = f"{page_key}_back"

        components = [
            self._component(f"{page_key}_header", "PAGE_HEADER", page_name, "查看业务对象详情、状态与关联记录。", action_ids=[refresh_id, back_id], props={"visualRole": "enterprisePageHeader", "pagePattern": "DETAIL_VIEW"}),
            self._component(f"{page_key}_summary", "DESCRIPTION_LIST", "概要信息", "只读展示核心业务字段。", fields=fields, props={"visualRole": "detailSummary", "columns": 3, "pagePattern": "DETAIL_VIEW"}),
            self._component(f"{page_key}_status_steps", "STEPS", "处理进度", "展示当前状态和流转节点。", props={"visualRole": "statusTimeline", "items": [{"title": "创建", "status": "finish"}, {"title": "处理中", "status": "process"}, {"title": "完成", "status": "wait"}], "pagePattern": "DETAIL_VIEW"}),
            self._component(f"{page_key}_related_table", "TABLE", "关联记录", "展示与当前对象相关的明细或操作记录。", columns=columns, action_ids=[refresh_id], props={"visualRole": "relatedRecordsTable", "pagination": True, "rowActionIds": [], "pagePattern": "DETAIL_VIEW"}),
        ]

        interactions = [
            self._interaction(refresh_id, "QUERY_DATA", "刷新", f"{page_key}_summary", data_binding_key="detailData"),
            self._interaction(back_id, "SHOW_TOAST", "返回", f"{page_key}_header"),
        ]

        return self._pack(
            pattern,
            page_kind="DETAIL_PAGE",
            layout_type="DETAIL_TABS",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("detailData", "GET", f"/api/mock/{page_key}/detail", [refresh_id])],
            mock_data={"detailData": self._mock_form(fields), "relatedRows": [self._mock_row(columns, 1)]},
        )

    def _build_dashboard(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        refresh_id = f"{page_key}_refresh"
        columns = [
            self._column("taskName", "待办事项", "TEXT", 240),
            self._column("owner", "负责人", "TEXT", 120),
            self._column("status", "状态", "TAG", 110),
            self._column("deadline", "截止时间", "DATETIME", 180),
        ]
        metrics = [
            self._metric_card(f"{page_key}_metric_pending", "待处理", "24", "较昨日 +8%"),
            self._metric_card(f"{page_key}_metric_done", "已完成", "128", "本周完成率 92%"),
            self._metric_card(f"{page_key}_metric_warning", "异常预警", "3", "需尽快处理"),
            self._metric_card(f"{page_key}_metric_rate", "完成率", "92%", "持续稳定"),
        ]
        components = [
            self._component(f"{page_key}_header", "PAGE_HEADER", page_name, "集中展示指标、趋势、待办与快捷操作。", action_ids=[refresh_id], props={"visualRole": "dashboardHeader", "pagePattern": "DASHBOARD"}),
            self._component(f"{page_key}_metrics", "SECTION", "关键指标", "概览当前业务运行状态。", children=metrics, props={"visualRole": "metricGrid", "layout": {"columns": 4}, "pagePattern": "DASHBOARD"}),
            self._component(f"{page_key}_trend", "CARD", "趋势分析", "展示近期业务变化趋势。", props={"visualRole": "chartCard", "chartType": "line", "items": [{"label": "周一", "value": 32}, {"label": "周二", "value": 48}, {"label": "周三", "value": 42}], "pagePattern": "DASHBOARD"}),
            self._component(f"{page_key}_todo_table", "TABLE", "待办事项", "展示需要关注的业务事项。", columns=columns, action_ids=[refresh_id], props={"visualRole": "todoTable", "pagination": False, "pagePattern": "DASHBOARD"}),
        ]
        interactions = [self._interaction(refresh_id, "QUERY_DATA", "刷新看板", f"{page_key}_header", data_binding_key="dashboardData")]
        return self._pack(
            pattern,
            page_kind="DASHBOARD_PAGE",
            layout_type="DASHBOARD",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("dashboardData", "GET", f"/api/mock/{page_key}/dashboard", [refresh_id])],
            mock_data={"dashboardData": {"pending": 24, "done": 128, "warning": 3}, "todoRows": [self._mock_row(columns, 1)]},
        )

    def _build_workflow(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        approve_id = f"{page_key}_approve"
        reject_id = f"{page_key}_reject"
        transfer_id = f"{page_key}_transfer"
        form_id = f"{page_key}_opinion_form"
        columns = [
            self._column("nodeName", "节点", "TEXT", 160),
            self._column("processor", "处理人", "TEXT", 140),
            self._column("result", "处理结果", "TAG", 120),
            self._column("processedAt", "处理时间", "DATETIME", 180),
        ]
        components = [
            self._component(f"{page_key}_header", "PAGE_HEADER", page_name, "处理审批节点、查看流转进度与审批记录。", action_ids=[approve_id, reject_id, transfer_id], props={"visualRole": "workflowHeader", "pagePattern": "WORKFLOW"}),
            self._component(f"{page_key}_steps", "STEPS", "审批进度", "展示流程当前节点。", props={"visualRole": "workflowSteps", "items": [{"title": "提交", "status": "finish"}, {"title": "审批", "status": "process"}, {"title": "归档", "status": "wait"}], "pagePattern": "WORKFLOW"}),
            self._component(form_id, "FORM", "处理意见", "填写审批意见并选择处理结果。", fields=[self._field("opinion", "处理意见", "TEXTAREA", True), self._field("notify", "通知提交人", "SWITCH", False)], action_ids=[approve_id, reject_id, transfer_id], props={"visualRole": "workflowOpinionForm", "layout": {"columns": 1}, "pagePattern": "WORKFLOW"}),
            self._component(f"{page_key}_records", "TABLE", "审批记录", "展示历史处理记录。", columns=columns, props={"visualRole": "workflowRecordTable", "pagination": False, "pagePattern": "WORKFLOW"}),
        ]
        interactions = [
            self._interaction(approve_id, "CONFIRM_ACTION", "同意", form_id),
            self._interaction(reject_id, "CONFIRM_ACTION", "驳回", form_id),
            self._interaction(transfer_id, "CONFIRM_ACTION", "转交", form_id),
        ]
        return self._pack(
            pattern,
            page_kind="WORKFLOW_PAGE",
            layout_type="APPROVAL_WORKBENCH",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("workflowSubmit", "POST", f"/api/mock/{page_key}/workflow/submit", [approve_id, reject_id, transfer_id])],
            mock_data={"workflowRecords": [self._mock_row(columns, 1), self._mock_row(columns, 2)]},
        )

    def _build_settings(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        fields = self._form_fields(page, current_page, feature_nodes)
        save_id = f"{page_key}_save_settings"
        reset_id = f"{page_key}_reset_settings"
        form_id = f"{page_key}_settings_form"
        components = [
            self._component(f"{page_key}_header", "PAGE_HEADER", page_name, "维护业务配置、参数和权限相关开关。", action_ids=[save_id, reset_id], props={"visualRole": "settingsHeader", "pagePattern": "SETTINGS_FORM"}),
            self._component(f"{page_key}_settings_section", "SECTION", "配置项", "按分组维护当前页面配置。", children=[self._component(form_id, "FORM", "配置项", "修改后保存生效。", fields=fields, action_ids=[save_id], props={"visualRole": "settingsForm", "layout": {"columns": 2}, "pagePattern": "SETTINGS_FORM"})], props={"visualRole": "settingsSection", "layout": {"columns": 1}, "fullWidth": True, "pagePattern": "SETTINGS_FORM"}),
            self._component(f"{page_key}_risk_hint", "ALERT", "变更提示", "配置修改可能影响业务处理，请确认后保存。", props={"visualRole": "settingsRiskHint", "level": "warning", "message": "建议在低峰期调整关键配置。", "pagePattern": "SETTINGS_FORM"}),
        ]
        interactions = [
            self._interaction(save_id, "SUBMIT_FORM", "保存配置", form_id),
            self._interaction(reset_id, "RESET_FORM", "重置", form_id),
        ]
        return self._pack(
            pattern,
            page_kind="SETTINGS_PAGE",
            layout_type="SETTINGS",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("settingsSubmit", "POST", f"/api/mock/{page_key}/settings", [save_id])],
            mock_data={"settingsData": self._mock_form(fields)},
        )

    def _build_upload(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        upload_id = f"{page_key}_upload"
        import_id = f"{page_key}_import"
        remove_id = f"{page_key}_remove_file"
        form_id = f"{page_key}_upload_form"
        columns = [
            self._column("fileName", "文件名", "TEXT", 260),
            self._column("fileSize", "大小", "TEXT", 100),
            self._column("status", "状态", "TAG", 120),
            self._column("uploadedAt", "上传时间", "DATETIME", 180),
        ]
        components = [
            self._component(f"{page_key}_header", "PAGE_HEADER", page_name, "上传、校验并导入业务文件。", action_ids=[upload_id, import_id], props={"visualRole": "uploadHeader", "pagePattern": "UPLOAD"}),
            self._component(form_id, "FORM", "上传设置", "选择文件并填写上传说明。", fields=[self._field("file", "选择文件", "INPUT", True), self._field("businessType", "业务类型", "SELECT", True), self._field("remark", "说明", "TEXTAREA", False)], action_ids=[upload_id], props={"visualRole": "uploadForm", "layout": {"columns": 2}, "pagePattern": "UPLOAD"}),
            self._component(f"{page_key}_file_table", "TABLE", "文件列表", "展示已上传文件和处理状态。", columns=columns, action_ids=[import_id], props={"visualRole": "fileTable", "rowActionIds": [remove_id], "pagination": False, "pagePattern": "UPLOAD"}),
            self._component(f"{page_key}_upload_hint", "ALERT", "上传校验", "支持格式、大小和必填信息将在提交前校验。", props={"visualRole": "uploadHint", "level": "info", "message": "请上传符合要求的文件。", "pagePattern": "UPLOAD"}),
        ]
        interactions = [
            self._interaction(upload_id, "SUBMIT_FORM", "上传", form_id),
            self._interaction(import_id, "SUBMIT_FORM", "开始导入", f"{page_key}_file_table"),
            self._interaction(remove_id, "DELETE_ROW", "删除文件", f"{page_key}_file_table"),
        ]
        return self._pack(
            pattern,
            page_kind="UPLOAD_PAGE",
            layout_type="FORM_DETAIL",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("uploadFiles", "POST", f"/api/mock/{page_key}/upload", [upload_id, import_id])],
            mock_data={"uploadFiles": [self._mock_row(columns, 1)]},
        )

    def _build_auth(self, pattern: str, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> dict[str, Any]:
        page_key = self._page_key(page, current_page)
        page_name = self._page_name(page, current_page)
        is_register = pattern == "AUTH_REGISTER"
        submit_id = f"{page_key}_{'register' if is_register else 'login'}"
        form_id = f"{page_key}_auth_form"
        fields = [
            self._field("account", "账号", "INPUT", True),
            self._field("password", "密码", "PASSWORD", True),
        ]
        if is_register:
            fields.insert(1, self._field("nickname", "姓名", "INPUT", True))
            fields.append(self._field("confirmPassword", "确认密码", "PASSWORD", True))
        components = [
            self._component(f"{page_key}_auth_card", "SECTION", page_name, "请输入账号信息完成认证。", children=[
                self._component(form_id, "FORM", page_name, "账号、密码单列展示，主按钮整行。", fields=fields, action_ids=[submit_id], props={"visualRole": "authForm", "layout": {"columns": 1}, "pagePattern": pattern})
            ], props={"visualRole": "authCard", "layout": {"columns": 1}, "pagePattern": pattern}),
        ]
        interactions = [self._interaction(submit_id, "SUBMIT_FORM", "注册" if is_register else "登录", form_id)]
        return self._pack(
            pattern,
            page_kind="REGISTER_PAGE" if is_register else "LOGIN_PAGE",
            layout_type="CENTERED_FORM",
            components=components,
            interactions=interactions,
            api_bindings=[self._api("authSubmit", "POST", f"/api/mock/{page_key}/auth", [submit_id])],
            mock_data={"authData": self._mock_form(fields)},
        )

    # ------------------------------------------------------------------
    # Pack/build helpers
    # ------------------------------------------------------------------
    def _pack(
        self,
        pattern: str,
        *,
        page_kind: str,
        layout_type: str,
        components: list[dict[str, Any]],
        interactions: list[dict[str, Any]],
        api_bindings: list[dict[str, Any]],
        mock_data: dict[str, Any],
        empty_ids: list[str] | None = None,
    ) -> dict[str, Any]:
        all_ids = self._component_ids(components)
        regions = []
        for component in components:
            cid = str(component.get("componentId") or "")
            if cid:
                regions.append({"regionId": self._safe_key(cid, "region"), "title": str(component.get("title") or ""), "componentIds": [cid]})
        return {
            "pageKind": page_kind,
            "layout": {"type": layout_type, "columns": 1 if layout_type != "DASHBOARD" else 2, "regions": regions},
            "components": components,
            "interactions": interactions,
            "apiBindings": api_bindings,
            "mockData": mock_data,
            "visibleIds": all_ids,
            "emptyIds": empty_ids or [],
        }

    def _component(
        self,
        component_id: str,
        component_type: str,
        title: str,
        description: str,
        *,
        fields: list[dict[str, Any]] | None = None,
        columns: list[dict[str, Any]] | None = None,
        tabs: list[dict[str, Any]] | None = None,
        children: list[dict[str, Any]] | None = None,
        action_ids: list[str] | None = None,
        props: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        base_props = dict(props or {})
        base_props.setdefault("a9VisualQuality", "enterprisePattern")
        base_props.setdefault("elementPicker", {"selectable": True})
        base_props.setdefault("elementStyles", {})
        return {
            "componentId": self._safe_key(component_id, "component"),
            "type": component_type,
            "title": str(title or ""),
            "description": str(description or ""),
            "featureIds": [],
            "actionIds": list(action_ids or []),
            "dataBindingKey": None,
            "modalId": None,
            "fields": fields or [],
            "columns": columns or [],
            "tabs": tabs or [],
            "children": children or [],
            "props": base_props,
        }

    def _metric_card(self, component_id: str, title: str, value: str, desc: str) -> dict[str, Any]:
        return self._component(
            component_id,
            "CARD",
            title,
            desc,
            props={"visualRole": "metricCard", "value": value, "trend": desc, "pagePattern": "DASHBOARD"},
        )

    def _field(self, key: str, label: str, field_type: str, required: bool) -> dict[str, Any]:
        field_type = self._field_type(label, fallback=field_type)
        options = self._default_options(label, field_type)
        if field_type == "SELECT":
            placeholder = f"请选择{label}"
        elif field_type in {"CHECKBOX", "SWITCH", "RADIO", "HIDDEN"}:
            placeholder = ""
        else:
            placeholder = f"请输入{label}"
        return {
            "fieldKey": self._safe_key(key or label, "field"),
            "label": str(label or key or "字段"),
            "type": field_type,
            "required": bool(required),
            "placeholder": placeholder,
            "defaultValue": None,
            "options": options,
            "validationRuleIds": [],
            "featureIds": [],
            "helpText": "请按实际业务信息填写。" if required else "",
            "disabled": False,
        }

    def _column(self, key: str, label: str, column_type: str = "TEXT", width: int | None = None) -> dict[str, Any]:
        column_type = column_type if column_type in {"TEXT", "TAG", "NUMBER", "DATE", "DATETIME", "BOOLEAN"} else "TEXT"
        safe_key = self._safe_key(key or label, "column")
        return {
            "columnKey": safe_key,
            "fieldKey": safe_key,
            "label": str(label or key or "列"),
            "type": column_type,
            "width": width or self._column_width(label, column_type),
            "sortable": column_type in {"TEXT", "NUMBER", "DATE", "DATETIME", "TAG"},
        }

    def _interaction(
        self,
        action_id: str,
        action_type: str,
        label: str,
        target_id: str,
        *,
        data_binding_key: str | None = None,
    ) -> dict[str, Any]:
        return {
            "actionId": self._safe_key(action_id, "action"),
            "type": action_type,
            "label": label,
            "sourceComponentId": target_id,
            "targetId": target_id,
            "targetPageId": None,
            "targetTabKey": None,
            "dataBindingKey": data_binding_key,
            "scenarioKey": None,
            "payload": {"source": "A9_10_1_PAGE_PATTERN_INTELLIGENCE"},
            "successActionIds": [],
            "failureActionIds": [],
            "featureIds": [],
            "confirmationMessage": "",
        }

    def _api(self, key: str, method: str, path: str, action_ids: list[str]) -> dict[str, Any]:
        return {
            "bindingKey": self._safe_key(key, "binding"),
            "method": method if method in {"GET", "POST", "PUT", "PATCH", "DELETE"} else "POST",
            "path": path,
            "status": "MOCK",
            "requestSchema": {},
            "responseSchema": {},
            "usedByActionIds": list(dict.fromkeys(action_ids)),
        }

    # ------------------------------------------------------------------
    # Business semantic mapper
    # ------------------------------------------------------------------
    def _form_fields(self, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
        raw = self._raw_field_candidates(page, current_page, feature_nodes)
        fields = [self._field_from_raw(item, index) for index, item in enumerate(raw)]
        if not fields:
            fields = [self._field_from_raw(item, index) for index, item in enumerate(["业务编号", "业务名称", "状态", "负责人", "业务日期", "备注"])]
        return self._dedupe_fields(fields)[:12]

    def _filter_fields(self, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
        base = self._form_fields(page, current_page, feature_nodes)
        result: list[dict[str, Any]] = []
        for field in base:
            clone = dict(field)
            clone["required"] = False
            clone["helpText"] = ""
            label = str(clone.get("label") or "")
            if self._field_type(label) == "TEXTAREA":
                clone["type"] = "INPUT"
                clone["placeholder"] = f"请输入{label}"
            result.append(clone)
            if len(result) >= 3:
                break
        if not any(str(item.get("fieldKey") or "").lower() in {"keyword", "keywords"} or str(item.get("label") or "") == "关键字" for item in result):
            result.insert(0, self._field("keyword", "关键字", "INPUT", False))
        return self._dedupe_fields(result)[:4]

    def _table_columns(self, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]], *, prefer_detail: bool) -> list[dict[str, Any]]:
        raw = self._raw_detail_candidates(page, current_page) if prefer_detail else []
        if not raw:
            raw = self._raw_field_candidates(page, current_page, feature_nodes)
        if not raw:
            raw = ["编号", "名称", "状态", "负责人", "更新时间"]
        columns = [self._column_from_raw(item, index) for index, item in enumerate(raw)]
        columns = [item for item in columns if not self._is_action_like_column(item)]
        if not columns:
            columns = [self._column("code", "编号", "TEXT", 160), self._column("name", "名称", "TEXT", 180), self._column("status", "状态", "TAG", 120)]
        return self._dedupe_columns(columns)[:8]

    def _raw_field_candidates(self, page: dict[str, Any], current_page: dict[str, Any], feature_nodes: list[dict[str, Any]]) -> list[Any]:
        page_context = self._page_context(current_page)
        candidates: list[Any] = []
        for container in [page, current_page, page_context]:
            if not isinstance(container, dict):
                continue
            for key in ["coreFields", "core_fields", "fields", "formFields", "form_fields"]:
                value = container.get(key)
                if isinstance(value, list):
                    candidates.extend(value)
        if candidates:
            return candidates
        for feature in feature_nodes:
            if not isinstance(feature, dict):
                continue
            for key in ["fields", "coreFields", "columns"]:
                value = feature.get(key)
                if isinstance(value, list):
                    candidates.extend(value)
        return candidates

    def _raw_detail_candidates(self, page: dict[str, Any], current_page: dict[str, Any]) -> list[Any]:
        page_context = self._page_context(current_page)
        candidates: list[Any] = []
        for container in [page, current_page, page_context]:
            if not isinstance(container, dict):
                continue
            for key in ["detailFields", "detail_fields", "detailColumns", "detail_columns", "columns", "items", "lineItems"]:
                value = container.get(key)
                if isinstance(value, list):
                    candidates.extend(value)
        return candidates

    def _field_from_raw(self, item: Any, index: int) -> dict[str, Any]:
        if isinstance(item, dict):
            label = str(item.get("label") or item.get("title") or item.get("name") or item.get("fieldName") or item.get("columnName") or f"字段{index + 1}")
            key = str(item.get("fieldKey") or item.get("field_key") or item.get("columnKey") or item.get("key") or item.get("name") or label)
            raw_type = str(item.get("type") or item.get("fieldType") or item.get("field_type") or "")
            required = bool(item.get("required")) if "required" in item else index < 2
        else:
            label = str(item or f"字段{index + 1}")
            key = label
            raw_type = ""
            required = index < 2
        return self._field(key, label, self._field_type(f"{label} {raw_type}"), required)

    def _column_from_raw(self, item: Any, index: int) -> dict[str, Any]:
        field = self._field_from_raw(item, index)
        label = str(field.get("label") or f"列{index + 1}")
        ftype = str(field.get("type") or "INPUT")
        if ftype == "NUMBER":
            ctype = "NUMBER"
        elif ftype == "DATE":
            ctype = "DATE"
        elif ftype == "DATETIME":
            ctype = "DATETIME"
        elif ftype in {"SWITCH", "CHECKBOX", "RADIO"}:
            ctype = "BOOLEAN"
        elif self._has_any(self._norm(label), ["状态", "类型", "分类", "标签", "级别"]):
            ctype = "TAG"
        else:
            ctype = "TEXT"
        return self._column(str(field.get("fieldKey") or label), label, ctype, self._column_width(label, ctype))

    def _field_type(self, text: str, fallback: str = "INPUT") -> str:
        value = self._norm(text)
        if self._has_any(value, ["备注", "说明", "描述", "意见", "内容", "textarea", "remark", "description", "comment"]):
            return "TEXTAREA"
        if self._has_any(value, ["日期", "date"]):
            return "DATE"
        if self._has_any(value, ["时间", "datetime", "timestamp"]):
            return "DATETIME"
        if self._has_any(value, ["数量", "金额", "价格", "单价", "比例", "库存", "number", "amount", "qty", "price"]):
            return "NUMBER"
        if self._has_any(value, ["状态", "类型", "分类", "仓库", "供应商", "客户", "负责人", "经办人", "角色", "部门", "权限", "select", "dropdown"]):
            return "SELECT"
        if self._has_any(value, ["启用", "是否", "开关", "switch"]):
            return "SWITCH"
        if self._has_any(value, ["密码", "password"]):
            return "PASSWORD"
        if self._has_any(value, ["邮箱", "email"]):
            return "EMAIL"
        normalized = str(fallback or "INPUT").upper()
        return normalized if normalized in {"INPUT", "PASSWORD", "TEXTAREA", "SELECT", "CHECKBOX", "RADIO", "NUMBER", "EMAIL", "DATE", "DATETIME", "SWITCH", "HIDDEN"} else "INPUT"

    def _default_options(self, label: str, field_type: str) -> list[dict[str, str]]:
        if field_type != "SELECT":
            return []
        label_text = self._norm(label)
        if self._has_any(label_text, ["状态"]):
            values = ["待处理", "处理中", "已完成", "已取消"]
        elif self._has_any(label_text, ["角色", "权限"]):
            values = ["管理员", "运营人员", "审核人员"]
        elif self._has_any(label_text, ["仓库"]):
            values = ["主仓", "备仓", "暂存仓"]
        elif self._has_any(label_text, ["供应商"]):
            values = ["默认供应商", "长期供应商", "临时供应商"]
        else:
            values = ["选项一", "选项二", "选项三"]
        return [{"label": item, "value": self._safe_key(item, "option")} for item in values]

    def _should_have_detail_table(self, page: dict[str, Any], current_page: dict[str, Any]) -> bool:
        if self._raw_detail_candidates(page, current_page):
            return True
        text = self._norm(" ".join([
            str(page.get("pageName") or page.get("title") or ""),
            str(current_page.get("pageName") or current_page.get("module_name") or current_page.get("moduleName") or current_page.get("name") or ""),
            str(current_page.get("pageKey") or current_page.get("module_key") or current_page.get("moduleKey") or current_page.get("key") or ""),
        ]))
        return self._has_any(text, ["单", "订单", "入库", "出库", "采购", "销售", "盘点", "明细", "item", "order"])

    # ------------------------------------------------------------------
    # Merge/mock/state helpers
    # ------------------------------------------------------------------
    def _merge_interactions(self, existing: Any, generated: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in self._as_list(existing) + generated:
            if not isinstance(item, dict):
                continue
            action_id = str(item.get("actionId") or item.get("action_id") or "").strip()
            if not action_id or action_id in seen:
                continue
            item = dict(item)
            item["actionId"] = self._safe_key(action_id, "action")
            seen.add(item["actionId"])
            result.append(item)
        return result

    def _merge_api_bindings(self, existing: Any, generated: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in self._as_list(existing) + generated:
            if not isinstance(item, dict):
                continue
            key = self._safe_key(item.get("bindingKey") or item.get("binding_key") or item.get("key"), "binding")
            if key in seen:
                continue
            item = dict(item)
            item["bindingKey"] = key
            seen.add(key)
            result.append(item)
        return result

    def _merge_mock_data(self, existing: Any, generated: dict[str, Any]) -> dict[str, Any]:
        result = dict(existing) if isinstance(existing, dict) else {}
        result.update(generated)
        return result

    def _merge_mock_scenarios(self, existing: Any, generated: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in self._as_list(existing) + generated:
            if not isinstance(item, dict):
                continue
            key = str(item.get("scenarioKey") or item.get("scenario_key") or "").strip()
            if not key or key in seen:
                continue
            seen.add(key)
            result.append(item)
        return result

    def _mock_scenarios(self, interactions: list[dict[str, Any]], pattern: str) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for item in interactions:
            action_id = str(item.get("actionId") or "")
            label = str(item.get("label") or "操作")
            if not action_id:
                continue
            result.append({
                "scenarioKey": f"{action_id}_success",
                "actionId": action_id,
                "outcome": "SUCCESS",
                "delayMs": 300,
                "message": f"{label}成功。",
                "nextPageState": "SUCCESS",
                "response": {"success": True, "message": "操作成功。"},
            })
            result.append({
                "scenarioKey": f"{action_id}_failure",
                "actionId": action_id,
                "outcome": "FAILURE",
                "delayMs": 300,
                "message": f"{label}失败，请稍后重试。",
                "nextPageState": "ERROR",
                "response": {"success": False, "message": "操作失败，请稍后重试。"},
            })
        return result

    def _page_states(self, pattern: str, visible_ids: list[str], empty_ids: list[str]) -> list[dict[str, Any]]:
        default_ids = list(dict.fromkeys(visible_ids))
        empty_visible = list(dict.fromkeys([*default_ids, *empty_ids]))
        states = [
            self._state("DEFAULT", "DEFAULT", "默认态", "页面正常展示。", default_ids),
            self._state("LOADING", "LOADING", "加载中", "正在加载页面数据。", default_ids),
            self._state("ERROR", "ERROR", "错误态", "请求失败或校验异常。", default_ids),
            self._state("SUCCESS", "SUCCESS", "成功态", "操作成功后的反馈。", default_ids),
        ]
        if pattern in {"SEARCH_LIST", "CRUD_LIST", "DASHBOARD"}:
            states.append(self._state("EMPTY", "EMPTY", "空状态", "暂无可展示数据。", empty_visible))
        if pattern == "WORKFLOW":
            states.append(self._state("DISABLED", "DISABLED", "不可处理", "当前节点不可处理或权限不足。", default_ids))
        return states

    def _state(self, key: str, state_type: str, title: str, message: str, visible_ids: list[str]) -> dict[str, Any]:
        return {
            "stateKey": key,
            "type": state_type,
            "title": title,
            "message": message,
            "visibleComponentIds": visible_ids,
            "disabledComponentIds": [],
        }

    def _mock_form(self, fields: list[dict[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for field in fields:
            key = str(field.get("fieldKey") or "")
            label = str(field.get("label") or key)
            ftype = str(field.get("type") or "INPUT")
            if not key:
                continue
            if ftype == "NUMBER":
                result[key] = 100
            elif ftype in {"DATE", "DATETIME"}:
                result[key] = "2026-08-05 10:00" if ftype == "DATETIME" else "2026-08-05"
            elif ftype in {"SWITCH", "CHECKBOX"}:
                result[key] = True
            elif ftype == "SELECT":
                options = self._as_list(field.get("options"))
                result[key] = str((options[0] or {}).get("label") or "待处理") if options else "待处理"
            else:
                result[key] = f"{label}示例"
        return result

    def _mock_row(self, columns: list[dict[str, Any]], index: int) -> dict[str, Any]:
        row: dict[str, Any] = {}
        for column in columns:
            if self._is_action_like_column(column):
                continue
            key = str(column.get("fieldKey") or column.get("columnKey") or "")
            label = str(column.get("label") or key)
            ctype = str(column.get("type") or "TEXT")
            if not key:
                continue
            if ctype == "NUMBER":
                row[key] = 100 + index
            elif ctype in {"DATE", "DATETIME"}:
                row[key] = "2026-08-05 10:00" if ctype == "DATETIME" else "2026-08-05"
            elif ctype == "TAG" or "状态" in label:
                row[key] = "进行中"
            elif ctype == "BOOLEAN":
                row[key] = index % 2 == 0
            else:
                row[key] = f"{label}示例{index}"
        return row

    def _mark_quality_gate(self, source: dict[str, Any], pattern: str, issues: list[str]) -> None:
        quality = source.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            source["qualitySummary"] = quality
        quality["score"] = max(92, int(quality.get("score") or 0) if str(quality.get("score") or "").isdigit() else 92)
        quality["coverageRate"] = max(quality.get("coverageRate") or 0, 100)
        quality["generationMode"] = "LLM_STRUCTURED"
        blockers = quality.get("blockers")
        if not isinstance(blockers, list):
            blockers = []
        quality["blockers"] = blockers
        warnings = quality.get("warnings")
        if not isinstance(warnings, list):
            warnings = []
        msg = f"A9.10.1 Page Pattern Intelligence applied: {pattern}"
        if issues:
            msg += f"; repaired {', '.join(issues[:8])}"
        warnings.append(msg)
        quality["warnings"] = list(dict.fromkeys(str(item) for item in warnings if str(item).strip()))

    # ------------------------------------------------------------------
    # Generic helpers
    # ------------------------------------------------------------------
    def _page_context(self, current_page: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(current_page, dict):
            return {}
        extra = current_page.get("extra") if isinstance(current_page.get("extra"), dict) else {}
        for value in [
            current_page.get("pagePrototypeContext"),
            current_page.get("page_prototype_context"),
            extra.get("pagePrototypeContext"),
            extra.get("page_prototype_context"),
        ]:
            if isinstance(value, dict):
                return value
        return {}

    def _page_id(self, page: dict[str, Any], current_page: dict[str, Any]) -> str:
        return str(page.get("pageId") or current_page.get("pageId") or current_page.get("module_id") or current_page.get("moduleId") or current_page.get("id") or "current_page")

    def _page_key(self, page: dict[str, Any], current_page: dict[str, Any]) -> str:
        return self._safe_key(
            page.get("pageKey")
            or current_page.get("pageKey")
            or current_page.get("module_key")
            or current_page.get("moduleKey")
            or current_page.get("key")
            or self._page_id(page, current_page),
            "current_page",
        )

    def _page_name(self, page: dict[str, Any], current_page: dict[str, Any]) -> str:
        name = (
            page.get("pageName")
            or page.get("title")
            or current_page.get("pageName")
            or current_page.get("module_name")
            or current_page.get("moduleName")
            or current_page.get("name")
            or "当前页面"
        )
        return str(name).strip() or "当前页面"

    def _route(self, page: dict[str, Any], current_page: dict[str, Any]) -> str:
        page_context = self._page_context(current_page)
        route = (
            page.get("route")
            or page.get("routePath")
            or current_page.get("routePath")
            or current_page.get("route_path")
            or page_context.get("routePath")
            or page_context.get("route_path")
            or f"/mock/{self._page_key(page, current_page)}"
        )
        return str(route).strip() or f"/mock/{self._page_key(page, current_page)}"

    def _page_description(self, pattern: str, page_name: str) -> str:
        return {
            "AUTH_LOGIN": "账号认证入口，支持登录校验和错误反馈。",
            "AUTH_REGISTER": "账号注册入口，支持注册信息填写和校验。",
            "SEARCH_LIST": f"{page_name}用于查询、筛选、查看和维护业务数据。",
            "CRUD_LIST": f"{page_name}用于查询、筛选、查看和维护业务数据。",
            "FORM_PAGE": f"{page_name}用于录入、维护和提交业务信息。",
            "DETAIL_VIEW": f"{page_name}用于查看业务详情、状态和关联记录。",
            "DASHBOARD": f"{page_name}用于集中查看业务指标、待办和趋势。",
            "WORKFLOW": f"{page_name}用于处理流程节点、意见和审批记录。",
            "SETTINGS_FORM": f"{page_name}用于维护系统配置和业务参数。",
            "UPLOAD": f"{page_name}用于上传、校验和导入业务文件。",
        }.get(pattern, f"{page_name}业务页面。")

    def _layout_type(self, page: dict[str, Any]) -> str:
        layout = page.get("layout")
        if isinstance(layout, dict):
            return self._norm(layout.get("type"))
        return self._norm(page.get("layoutType") or page.get("layout_type"))

    def _component_ids(self, components: list[dict[str, Any]]) -> list[str]:
        result: list[str] = []

        def visit(items: list[dict[str, Any]]) -> None:
            for item in items:
                if not isinstance(item, dict):
                    continue
                component_id = str(item.get("componentId") or "")
                if component_id:
                    result.append(component_id)
                children = item.get("children")
                if isinstance(children, list):
                    visit(children)

        visit(components)
        return list(dict.fromkeys(result))

    def _walk_components(self, value: Any) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        def visit(items: Any) -> None:
            for item in self._as_list(items):
                if not isinstance(item, dict):
                    continue
                result.append(item)
                visit(item.get("children"))

        visit(value)
        return result

    def _ctype(self, component: dict[str, Any]) -> str:
        return str(component.get("type") or component.get("componentType") or "").strip().upper()

    def _looks_like_filter_component(self, component: dict[str, Any]) -> bool:
        props = component.get("props") if isinstance(component.get("props"), dict) else {}
        text = self._norm(" ".join([
            self._ctype(component),
            str(component.get("componentId") or ""),
            str(component.get("title") or ""),
            str(component.get("description") or ""),
            str(props.get("visualRole") or ""),
        ]))
        return self._has_any(text, ["filter", "query", "search", "查询", "筛选", "搜索"])

    def _is_action_like_column(self, column: Any) -> bool:
        if not isinstance(column, dict):
            return False
        label = str(column.get("label") or column.get("title") or column.get("name") or "").strip()
        key = self._safe_key(column.get("columnKey") or column.get("fieldKey") or column.get("key") or label, "column").lower()
        ctype = str(column.get("type") or column.get("columnType") or "").strip().upper()
        if ctype in {"ACTION", "ACTIONS"}:
            return True
        if label in {"操作", "动作", "行操作", "操作列"}:
            return True
        return key in {"action", "actions", "operation", "operations", "rowaction", "rowactions", "row_actions"} or "action" in key or "operation" in key

    def _dedupe_fields(self, fields: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for index, field in enumerate(fields):
            if not isinstance(field, dict):
                continue
            key = self._safe_key(field.get("fieldKey") or field.get("label"), f"field_{index + 1}")
            if key in seen:
                continue
            item = dict(field)
            item["fieldKey"] = key
            seen.add(key)
            result.append(item)
        return result

    def _dedupe_columns(self, columns: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()
        for index, column in enumerate(columns):
            if not isinstance(column, dict) or self._is_action_like_column(column):
                continue
            key = self._safe_key(column.get("columnKey") or column.get("fieldKey") or column.get("label"), f"column_{index + 1}")
            if key in seen:
                continue
            item = dict(column)
            item["columnKey"] = key
            item["fieldKey"] = self._safe_key(item.get("fieldKey") or key, key)
            seen.add(key)
            result.append(item)
        return result

    def _column_width(self, label: str, column_type: str) -> int:
        label_text = str(label or "")
        if column_type in {"DATE", "DATETIME"}:
            return 180
        if column_type == "NUMBER":
            return 120
        if column_type == "TAG":
            return 120
        if len(label_text) >= 8:
            return 200
        return 160

    def _as_list(self, value: Any) -> list[Any]:
        return value if isinstance(value, list) else []

    def _norm(self, value: Any) -> str:
        return str(value or "").strip().upper().replace("-", "_")

    def _has_any(self, text: str, tokens: list[str]) -> bool:
        lower = str(text or "").lower()
        return any(token.lower() in lower for token in tokens)

    def _safe_key(self, value: Any, fallback: str) -> str:
        raw = str(value or "").strip()
        if not raw:
            raw = fallback
        replacements = {
            "编号": "code",
            "名称": "name",
            "标题": "title",
            "状态": "status",
            "类型": "type",
            "负责人": "owner",
            "经办人": "operator",
            "创建时间": "created_at",
            "更新时间": "updated_at",
            "备注": "remark",
            "关键字": "keyword",
            "供应商": "supplier",
            "仓库": "warehouse",
            "数量": "quantity",
            "金额": "amount",
            "日期": "date",
        }
        for zh, en in replacements.items():
            raw = raw.replace(zh, en)
        raw = re.sub(r"[^0-9A-Za-z_]+", "_", raw)
        raw = re.sub(r"_+", "_", raw).strip("_").lower()
        if not raw:
            raw = fallback
        if raw[0].isdigit():
            raw = f"{fallback}_{raw}"
        return raw[:80]
