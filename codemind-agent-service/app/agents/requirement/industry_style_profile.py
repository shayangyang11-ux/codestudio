# A9.10.14.4.3.15.4.2_AUTO_INDUSTRY_STYLE_INFERENCE_OPTIONAL_OVERRIDE
from __future__ import annotations

import re
from typing import Any


STYLE_MARKER = "A9.10.14.4.3.15.4.2_AUTO_INDUSTRY_STYLE_INFERENCE_OPTIONAL_OVERRIDE"

_GENERAL_INDUSTRY_VALUES = {"", "GENERAL", "UNKNOWN", "OTHER", "通用", "通用企业系统"}
_GENERIC_THEME_VALUES = {"", "enterprise_default", "default", "general", "GENERAL"}

_INDUSTRY_PRESETS: dict[str, dict[str, Any]] = {
    "COMMERCE": {
        "label": "电商 / 零售",
        "themeKey": "commerce_light",
        "visualTone": "COMMERCE",
        "primaryColor": "#f97316",
        "secondaryColor": "#fff7ed",
        "accentColor": "#0ea5e9",
        "density": "COMFORTABLE",
        "radius": "LARGE",
        "navigationStyle": "TOP_NAV",
        "keywords": ["电商", "商城", "零售", "商品", "购物车", "支付", "订单", "售后", "sku", "mall", "commerce", "retail", "shop", "storefront"],
        "rules": ["突出商品、订单、金额、库存和转化动作。", "列表页可使用卡片与表格混合表达。", "主操作醒目但不得覆盖 PRD 业务边界。"],
    },
    "FOREIGN_TRADE": {
        "label": "外贸 / 跨境业务",
        "themeKey": "enterprise_default",
        "visualTone": "CLEAN",
        "primaryColor": "#2563eb",
        "secondaryColor": "#eff6ff",
        "accentColor": "#14b8a6",
        "density": "STANDARD",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["外贸", "跨境", "询盘", "报价", "报关", "物流", "海运", "客户跟进", "贸易", "出口", "进口", "export", "quotation", "inquiry", "shipment"],
        "rules": ["强调客户、询盘、报价、订单、物流节点的连续性。", "详情页突出贸易状态、金额、币种和时间线。", "操作文案保持商务、稳健、可追踪。"],
    },
    "WORK_ORDER": {
        "label": "工单 / 服务流程",
        "themeKey": "tech_modern",
        "visualTone": "TECH",
        "primaryColor": "#0ea5e9",
        "secondaryColor": "#e0f2fe",
        "accentColor": "#8b5cf6",
        "density": "STANDARD",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["工单", "派单", "受理", "处理", "维修", "客服", "验收", "评价", "sla", "ticket", "service desk", "workflow"],
        "rules": ["突出状态流、负责人、时限、优先级和处理记录。", "处理/审核页必须体现意见、附件、记录和下一步动作。", "列表页优先显示优先级和超时风险。"],
    },
    "WAREHOUSE": {
        "label": "仓储 / 供应链",
        "themeKey": "industrial_pro",
        "visualTone": "INDUSTRIAL",
        "primaryColor": "#2563eb",
        "secondaryColor": "#f1f5f9",
        "accentColor": "#f59e0b",
        "density": "COMPACT",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["仓库", "仓储", "库存", "入库", "出库", "盘点", "库位", "供应商", "物料", "wms", "warehouse", "inventory", "stock"],
        "rules": ["强调单据号、仓库、库位、数量、状态和时间。", "FORM 页面采用高密度业务表单与明细表。", "表格列紧凑，状态标签清晰。"],
    },
    "MANUFACTURING": {
        "label": "制造 / 生产管理",
        "themeKey": "industrial_pro",
        "visualTone": "INDUSTRIAL",
        "primaryColor": "#1d4ed8",
        "secondaryColor": "#eef2ff",
        "accentColor": "#f59e0b",
        "density": "COMPACT",
        "radius": "SMALL",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["生产", "制造", "设备", "工艺", "产线", "质检", "bom", "mes", "排产", "manufacturing", "quality"],
        "rules": ["突出工序、设备、批次、状态、异常和产能指标。", "Dashboard 包含产线状态、异常告警和趋势。", "页面密度偏高但信息分组必须清楚。"],
    },
    "CRM": {
        "label": "CRM / 客户关系",
        "themeKey": "enterprise_default",
        "visualTone": "CLEAN",
        "primaryColor": "#7c3aed",
        "secondaryColor": "#f5f3ff",
        "accentColor": "#06b6d4",
        "density": "STANDARD",
        "radius": "LARGE",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["crm", "客户", "线索", "商机", "跟进", "联系人", "销售", "回访", "pipeline", "lead", "opportunity"],
        "rules": ["突出客户画像、跟进记录、阶段、负责人和下一步动作。", "详情页应有摘要卡、时间线和关联商机。", "操作按钮保持业务友好。"],
    },
    "FINANCE": {
        "label": "金融 / 财务",
        "themeKey": "finance_stable",
        "visualTone": "FINANCE",
        "primaryColor": "#1e40af",
        "secondaryColor": "#eff6ff",
        "accentColor": "#16a34a",
        "density": "STANDARD",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["金融", "财务", "账单", "结算", "发票", "收款", "付款", "对账", "报销", "finance", "invoice", "settlement"],
        "rules": ["强调可信、审计、金额、状态和风险提示。", "金额与审批状态应视觉突出。", "避免过度活泼的促销式风格。"],
    },
    "GOV": {
        "label": "政企 / 公共服务",
        "themeKey": "gov_clean",
        "visualTone": "GOV",
        "primaryColor": "#1d4ed8",
        "secondaryColor": "#f8fafc",
        "accentColor": "#dc2626",
        "density": "STANDARD",
        "radius": "SMALL",
        "navigationStyle": "TOP_NAV",
        "keywords": ["政务", "政府", "监管", "审批", "申报", "公共服务", "办件", "gov", "government", "supervision"],
        "rules": ["强调规范、层级、办理状态和材料清单。", "文案正式克制，状态说明明确。", "避免花哨动效和强营销色。"],
    },
    "SAAS": {
        "label": "SaaS / 数据平台",
        "themeKey": "tech_modern",
        "visualTone": "TECH",
        "primaryColor": "#2563eb",
        "secondaryColor": "#eff6ff",
        "accentColor": "#8b5cf6",
        "density": "STANDARD",
        "radius": "LARGE",
        "navigationStyle": "MIXED",
        "keywords": ["saas", "平台", "数据", "看板", "成员", "权限", "订阅", "用量", "ai", "智能", "workspace", "tenant"],
        "rules": ["突出工作台、指标、权限、团队协作和快捷入口。", "Dashboard 使用指标卡和趋势区。", "配置页保持清晰分组和说明。"],
    },
    "MEDICAL": {
        "label": "医疗 / 健康服务",
        "themeKey": "enterprise_default",
        "visualTone": "CLEAN",
        "primaryColor": "#0f766e",
        "secondaryColor": "#ecfdf5",
        "accentColor": "#2563eb",
        "density": "STANDARD",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": ["医疗", "医院", "患者", "医生", "问诊", "病历", "处方", "健康", "clinic", "medical", "health"],
        "rules": ["强调可信、隐私、记录、风险提示和流程边界。", "详情页突出患者/记录摘要和时间线。", "避免娱乐化表达。"],
    },
    "EDUCATION": {
        "label": "教育 / 培训",
        "themeKey": "enterprise_default",
        "visualTone": "CLEAN",
        "primaryColor": "#4f46e5",
        "secondaryColor": "#eef2ff",
        "accentColor": "#f97316",
        "density": "COMFORTABLE",
        "radius": "LARGE",
        "navigationStyle": "TOP_NAV",
        "keywords": ["教育", "课程", "学生", "老师", "学习", "考试", "培训", "作业", "education", "course", "student"],
        "rules": ["强调学习进度、课程内容、任务和反馈。", "门户页可更亲和，后台管理页保持清晰。", "表单和列表适合非技术用户。"],
    },
    "GENERAL": {
        "label": "通用企业系统",
        "themeKey": "enterprise_default",
        "visualTone": "CLEAN",
        "primaryColor": "#2563eb",
        "secondaryColor": "#f8fafc",
        "accentColor": "#0ea5e9",
        "density": "STANDARD",
        "radius": "MEDIUM",
        "navigationStyle": "SIDE_NAV",
        "keywords": [],
        "rules": ["保持企业级、克制、清晰和可演示。", "不同页面类型使用对应业务结构，不套统一 CRUD 模板。", "字段、按钮、表格列和状态必须服务于 PRD 页面契约。"],
    },
}

_PAGE_TYPE_STYLE_RULES: dict[str, dict[str, Any]] = {
    "LIST": {"layoutIntent": "search_table_or_card_list", "requiredRegions": ["pageHeader", "filterBar", "primaryToolbar", "resultTable", "emptyState"], "densityHint": "compact_when_many_columns", "visualRules": ["筛选区和结果区边界清楚。", "行操作放在表格行级操作列。", "空状态不能缺失。"]},
    "SEARCH": {"layoutIntent": "search_results", "requiredRegions": ["searchBox", "filters", "resultList", "emptyState"], "densityHint": "standard", "visualRules": ["搜索入口突出，结果摘要清晰。", "筛选项不要淹没结果。"]},
    "FORM": {"layoutIntent": "business_form", "requiredRegions": ["pageHeader", "formCard", "ruleHint", "footerActions", "successErrorFeedback"], "densityHint": "grid_for_enterprise_forms", "visualRules": ["表单字段按业务分组。", "保存/提交/取消动作固定在清晰操作区。", "不要生成查询筛选区。"]},
    "DETAIL": {"layoutIntent": "summary_detail_timeline", "requiredRegions": ["summaryCard", "detailSections", "statusTimeline", "relatedActions"], "densityHint": "standard", "visualRules": ["详情页以查看和状态理解为主。", "字段展示不得变成可编辑表单，除非 PRD 明确要求。"]},
    "APPROVAL": {"layoutIntent": "approval_workspace", "requiredRegions": ["reviewObject", "approvalOpinion", "approvalActions", "approvalHistory"], "densityHint": "standard", "visualRules": ["通过/驳回/退回动作必须明确。", "审核意见和审核记录必须可见。"]},
    "DASHBOARD": {"layoutIntent": "metric_dashboard", "requiredRegions": ["metricCards", "trendArea", "alertArea", "quickEntries"], "densityHint": "comfortable", "visualRules": ["用指标卡和趋势区表达业务状态。", "不要套成普通列表页或表单页。"]},
    "REPORT": {"layoutIntent": "report_analytics", "requiredRegions": ["parameterBar", "summaryMetrics", "chartArea", "reportTable"], "densityHint": "standard", "visualRules": ["参数区、图表区、明细区分层。", "导出/刷新动作清晰。"]},
    "CONFIG": {"layoutIntent": "settings_form", "requiredRegions": ["settingsGroups", "scopeHint", "saveResetActions"], "densityHint": "standard", "visualRules": ["配置项必须分组并说明影响范围。", "保存/重置/启停动作不能遗漏。"]},
    "PORTAL": {"layoutIntent": "portal_home", "requiredRegions": ["heroOrHeader", "navigationEntries", "contentBlocks", "quickActions"], "densityHint": "comfortable", "visualRules": ["入口清楚、内容分区自然。", "不要生成后台 CRUD 首页。"]},
    "WIZARD": {"layoutIntent": "step_wizard", "requiredRegions": ["stepper", "currentStepForm", "stepActions", "validationFeedback"], "densityHint": "standard", "visualRules": ["步骤条和当前步骤目标必须清楚。", "上一步/下一步/提交动作完整。"]},
    "PROFILE": {"layoutIntent": "profile_detail_edit", "requiredRegions": ["profileSummary", "detailGroups", "securityOrPreference", "editActions"], "densityHint": "comfortable", "visualRules": ["突出主体摘要和信息分组。", "敏感信息和权限提示要清楚。"]},
}


def enrich_theme_profile_with_industry_style(
    theme_profile: Any,
    *,
    context_package: dict[str, Any] | None = None,
    current_page: dict[str, Any] | None = None,
    module_prd: Any = None,
    page_requirement: Any = None,
) -> dict[str, Any]:
    """Auto infer industry style by default and respect optional user override.

    Product rule:
    - users do not have to select an industry/style in the normal flow;
    - if they explicitly set/lock a profile, that user choice wins;
    - auto inference fills missing/default style slots only and never changes PRD facts.
    """
    profile = dict(theme_profile or {}) if isinstance(theme_profile, dict) else {}
    context_package = context_package or {}
    current_page = current_page or {}
    page_requirement = page_requirement if isinstance(page_requirement, dict) else {}

    text = _collect_signal_text(profile, context_package, current_page, module_prd, page_requirement)
    inference = _infer_industry_with_confidence(text)
    explicit_industry = _normalize_industry_code(profile.get("industry"))
    locked = _is_user_style_locked(profile)
    explicit_non_general = explicit_industry not in _GENERAL_INDUSTRY_VALUES

    if locked and explicit_non_general:
        industry = explicit_industry
        style_source = "USER_OVERRIDE_LOCKED"
        override_policy = "User-locked project theme profile wins; automatic inference may only provide diagnostic signals."
    elif locked:
        industry = explicit_industry if explicit_industry in _INDUSTRY_PRESETS else "GENERAL"
        style_source = "USER_LOCKED_GENERAL"
        override_policy = "User locked a general/custom project theme; keep user tokens and do not auto-switch industry."
    elif explicit_non_general:
        industry = explicit_industry
        style_source = "USER_SELECTED"
        override_policy = "User-selected industry wins for this project; automatic inference may only fill missing tokens."
    else:
        industry = inference["industryCode"] or "GENERAL"
        style_source = "AUTO_INFERRED" if industry != "GENERAL" else "AUTO_GENERAL"
        override_policy = "Automatic industry inference fills default project style; users can override and lock it later."

    if industry not in _INDUSTRY_PRESETS:
        industry = "GENERAL"
    preset = _INDUSTRY_PRESETS[industry]

    profile["industry"] = _choose_profile_value(
        profile.get("industry"),
        industry,
        locked=locked,
        allow_replace_default=not explicit_non_general,
    )
    _fill_style_defaults(profile, preset, locked=locked, style_source=style_source)

    page_type = _normalize_page_type(
        page_requirement.get("page_type")
        or page_requirement.get("pageType")
        or page_requirement.get("layout_type")
        or page_requirement.get("layoutType")
        or current_page.get("pageKind")
        or current_page.get("page_kind")
        or current_page.get("layoutType")
        or current_page.get("layout_type")
        or text
    )
    page_style = _PAGE_TYPE_STYLE_RULES.get(page_type, _PAGE_TYPE_STYLE_RULES["FORM"])

    style_profile = {
        "marker": STYLE_MARKER,
        "styleSource": style_source,
        "overridePolicy": override_policy,
        "userOverrideLocked": locked,
        "autoInferred": style_source in {"AUTO_INFERRED", "AUTO_GENERAL"},
        "industryCode": industry,
        "industryName": preset["label"],
        "inferredIndustryCode": inference.get("industryCode"),
        "inferredIndustryName": (_INDUSTRY_PRESETS.get(inference.get("industryCode") or "") or _INDUSTRY_PRESETS["GENERAL"])["label"],
        "inferenceConfidence": inference.get("confidence", 0),
        "inferenceMatchedSignals": inference.get("matchedSignals", []),
        "themeKey": profile.get("themeKey"),
        "visualTone": profile.get("visualTone"),
        "density": profile.get("density"),
        "radius": profile.get("radius"),
        "navigationStyle": profile.get("navigationStyle"),
        "palette": {
            "primary": profile.get("primaryColor") or preset["primaryColor"],
            "secondary": profile.get("secondaryColor") or preset["secondaryColor"],
            "accent": profile.get("accentColor") or preset["accentColor"],
            "surface": "#ffffff",
            "background": "#f8fafc" if profile.get("density") != "COMFORTABLE" else "#f9fafb",
        },
        "currentPageType": page_type,
        "pageTypeStyleRules": page_style,
        "industryRules": preset.get("rules") or [],
        "componentGuidelines": _component_guidelines(industry, page_type),
        "llmBoundaries": [
            "LLM may optimize layout, spacing, hierarchy, copy organization and demo visual quality.",
            "LLM must not change page type, PRD fields, table columns, actions, navigation targets or page states.",
            "Style choices must remain stable across pages unless the user explicitly overrides the project style.",
        ],
        "sourceSignals": _source_signals(text),
    }

    profile["industryStyleProfile"] = style_profile
    profile["styleInference"] = {
        "marker": STYLE_MARKER,
        "styleSource": style_source,
        "inferredIndustryCode": inference.get("industryCode"),
        "inferenceConfidence": inference.get("confidence", 0),
        "matchedSignals": inference.get("matchedSignals", []),
        "userOverrideLocked": locked,
        "effectiveIndustryCode": industry,
        "effectiveIndustryName": preset["label"],
    }
    profile.setdefault("notes", "")
    return profile


def attach_industry_style_profile_to_ui_contract(
    artifact: Any,
    *,
    request: Any = None,
    theme_profile: Any = None,
    contract: Any = None,
) -> Any:
    """Attach schema-safe industry style metadata to a UiContractArtifact.

    UiContractArtifact/developmentRules are strict models.  Durable style data is
    therefore stored in component.props and mockData.__a9IndustryStyleProfile,
    plus a human-readable assumption/warning.  This avoids validation failures
    while keeping Development Agent and runtime able to read the style signal.
    """
    if artifact is None:
        return artifact
    try:
        data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
    except Exception:
        return artifact
    if not isinstance(data, dict):
        return artifact

    context_package = getattr(request, "context_package", None) or {}
    current_page = _extract_current_page_from_request(request)
    module_prd = _extract_module_prd_from_context(context_package)
    page_requirement = _match_page_requirement(module_prd, current_page)
    profile = enrich_theme_profile_with_industry_style(
        theme_profile or _extract_theme_profile_from_request(request),
        context_package=context_package,
        current_page=current_page,
        module_prd=module_prd,
        page_requirement=page_requirement or contract,
    )
    style_profile = profile.get("industryStyleProfile") if isinstance(profile.get("industryStyleProfile"), dict) else {}

    page = data.get("page") if isinstance(data.get("page"), dict) else {}
    _attach_component_style_tokens(page.get("componentTree"), style_profile)

    mock_data = data.get("mockData") if isinstance(data.get("mockData"), dict) else {}
    mock_data["__a9IndustryStyleProfile"] = _compact_style_profile_for_payload(style_profile)
    data["mockData"] = mock_data

    assumptions = data.get("assumptions") if isinstance(data.get("assumptions"), list) else []
    note = (
        f"{STYLE_MARKER}: industry style source={style_profile.get('styleSource')}; "
        f"effective={style_profile.get('industryCode')}; inferred={style_profile.get('inferredIndustryCode')}; "
        f"userOverrideLocked={style_profile.get('userOverrideLocked')}."
    )
    if note not in assumptions:
        assumptions.append(note)
    data["assumptions"] = assumptions

    quality = data.get("qualitySummary") if isinstance(data.get("qualitySummary"), dict) else {}
    warnings = quality.get("warnings") if isinstance(quality.get("warnings"), list) else []
    warning = (
        f"A9.10.14.4.3.15.4.2 style profile applied: "
        f"{style_profile.get('industryName')} / {style_profile.get('styleSource')}."
    )
    if warning not in warnings:
        warnings.append(warning)
    quality["warnings"] = warnings
    data["qualitySummary"] = quality

    try:
        return artifact.__class__.model_validate(data)
    except Exception:
        # Last-resort compatibility; should normally not be needed because this
        # function now writes only schema-safe slots.
        try:
            return artifact.model_copy(update=data)
        except Exception:
            return artifact


def _fill_style_defaults(profile: dict[str, Any], preset: dict[str, Any], *, locked: bool, style_source: str) -> None:
    allow_replace_default = not locked and style_source in {"AUTO_INFERRED", "AUTO_GENERAL"}
    fields = {
        "themeKey": preset["themeKey"],
        "visualTone": preset["visualTone"],
        "primaryColor": preset["primaryColor"],
        "secondaryColor": preset["secondaryColor"],
        "accentColor": preset["accentColor"],
        "density": preset["density"],
        "radius": preset["radius"],
        "navigationStyle": preset["navigationStyle"],
        "loginLayout": "BRAND_SPLIT",
        "darkModeEnabled": False,
    }
    for key, fallback in fields.items():
        current = profile.get(key)
        if _is_empty_value(current):
            profile[key] = fallback
            continue
        if allow_replace_default and key in {"themeKey", "visualTone", "primaryColor", "secondaryColor", "accentColor", "density", "radius", "navigationStyle"}:
            if _looks_default_style_token(key, current):
                profile[key] = fallback


def _choose_profile_value(current: Any, fallback: Any, *, locked: bool, allow_replace_default: bool) -> Any:
    if _is_empty_value(current):
        return fallback
    if locked:
        return current
    normalized = _normalize_industry_code(current)
    if allow_replace_default and normalized in _GENERAL_INDUSTRY_VALUES:
        return fallback
    return current


def _looks_default_style_token(key: str, value: Any) -> bool:
    text = str(value or "").strip()
    upper = text.upper()
    if key == "themeKey":
        return text in _GENERIC_THEME_VALUES or upper in _GENERIC_THEME_VALUES
    if key == "visualTone":
        return upper in {"", "CLEAN", "GENERAL", "DEFAULT"}
    if key == "primaryColor":
        return text.lower() in {"#2563eb", "#1677ff", "#1890ff"}
    if key in {"secondaryColor", "accentColor"}:
        return text == "" or text.lower() in {"#f8fafc", "#eff6ff", "#0ea5e9"}
    if key == "density":
        return upper in {"", "STANDARD", "DEFAULT"}
    if key == "radius":
        return upper in {"", "MEDIUM", "DEFAULT"}
    if key == "navigationStyle":
        return upper in {"", "SIDE_NAV", "DEFAULT"}
    return False


def _is_user_style_locked(profile: dict[str, Any]) -> bool:
    for key in ["locked", "styleLocked", "style_locked", "userStyleLocked", "user_style_locked", "manualOverride", "manual_override", "styleOverrideLocked"]:
        value = profile.get(key)
        if value is True:
            return True
        if isinstance(value, str) and value.strip().lower() in {"true", "1", "yes", "locked", "manual"}:
            return True
    return False


def _infer_industry_with_confidence(text: str) -> dict[str, Any]:
    normalized = str(text or "").lower()
    scores: dict[str, int] = {}
    signals: dict[str, list[str]] = {}
    for code, preset in _INDUSTRY_PRESETS.items():
        if code == "GENERAL":
            continue
        for keyword in preset.get("keywords") or []:
            key = str(keyword).lower()
            if key and key in normalized:
                scores[code] = scores.get(code, 0) + _keyword_weight(key)
                signals.setdefault(code, [])
                if keyword not in signals[code]:
                    signals[code].append(str(keyword))
    if not scores:
        return {"industryCode": "GENERAL", "confidence": 0, "matchedSignals": []}
    ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    winner, score = ranked[0]
    runner = ranked[1][1] if len(ranked) > 1 else 0
    confidence = min(95, max(35, 35 + score * 10 + max(0, score - runner) * 8))
    return {"industryCode": winner, "confidence": confidence, "matchedSignals": signals.get(winner, [])[:16]}


def _keyword_weight(keyword: str) -> int:
    if len(keyword) >= 6:
        return 2
    if keyword in {"入库", "出库", "库存", "工单", "询盘", "报价", "支付", "商品", "政务", "医疗", "生产"}:
        return 3
    return 1


def _compact_style_profile_for_payload(style_profile: dict[str, Any]) -> dict[str, Any]:
    keys = [
        "marker", "styleSource", "overridePolicy", "userOverrideLocked", "autoInferred",
        "industryCode", "industryName", "inferredIndustryCode", "inferenceConfidence",
        "inferenceMatchedSignals", "themeKey", "visualTone", "density", "radius",
        "navigationStyle", "palette", "currentPageType", "pageTypeStyleRules",
        "industryRules", "componentGuidelines", "llmBoundaries",
    ]
    return {key: style_profile.get(key) for key in keys if key in style_profile}


def _attach_component_style_tokens(value: Any, style_profile: dict[str, Any]) -> None:
    if isinstance(value, list):
        for item in value:
            _attach_component_style_tokens(item, style_profile)
        return
    if not isinstance(value, dict):
        return

    component_id = str(value.get("componentId") or "").strip()
    component_type = str(value.get("type") or "").strip().upper()
    if component_id and component_type:
        props = value.get("props") if isinstance(value.get("props"), dict) else {}
        props["a9IndustryStyle"] = {
            "marker": STYLE_MARKER,
            "styleSource": style_profile.get("styleSource"),
            "industryCode": style_profile.get("industryCode"),
            "industryName": style_profile.get("industryName"),
            "inferredIndustryCode": style_profile.get("inferredIndustryCode"),
            "userOverrideLocked": style_profile.get("userOverrideLocked"),
            "pageType": style_profile.get("currentPageType"),
            "visualTone": style_profile.get("visualTone"),
            "density": style_profile.get("density"),
            "radius": style_profile.get("radius"),
            "palette": style_profile.get("palette"),
        }
        element_styles = props.get("elementStyles") if isinstance(props.get("elementStyles"), dict) else {}
        element_styles["industryTone"] = style_profile.get("visualTone")
        element_styles["industryDensity"] = style_profile.get("density")
        element_styles["industryPageType"] = style_profile.get("currentPageType")
        element_styles["industryStyleSource"] = style_profile.get("styleSource")
        element_styles["primaryColor"] = (style_profile.get("palette") or {}).get("primary") if isinstance(style_profile.get("palette"), dict) else None
        element_styles["accentColor"] = (style_profile.get("palette") or {}).get("accent") if isinstance(style_profile.get("palette"), dict) else None
        props["elementStyles"] = element_styles
        value["props"] = props

    for child_key in ["children", "components", "items"]:
        _attach_component_style_tokens(value.get(child_key), style_profile)
    for tab in value.get("tabs") or []:
        if isinstance(tab, dict):
            for child_key in ["children", "components", "items"]:
                _attach_component_style_tokens(tab.get(child_key), style_profile)


def _component_guidelines(industry: str, page_type: str) -> list[str]:
    result = [
        "PAGE_HEADER should explain business purpose, not technical contract details.",
        "FORM/TABLE/DETAIL/APPROVAL components must remain individually selectable.",
    ]
    if industry in {"WAREHOUSE", "MANUFACTURING"}:
        result.append("Use compact enterprise density for data-heavy tables and document forms.")
    if industry in {"COMMERCE", "EDUCATION"}:
        result.append("Use slightly warmer, more approachable copy and action hierarchy.")
    if industry in {"FINANCE", "GOV", "MEDICAL"}:
        result.append("Prefer sober contrast, precise status tags and conservative interaction wording.")
    if industry in {"FOREIGN_TRADE", "CRM"}:
        result.append("Emphasize relationship context, status timeline and responsible person.")
    if page_type == "DASHBOARD":
        result.append("Prioritize metric cards, trend blocks and alert summaries over CRUD tables.")
    if page_type == "APPROVAL":
        result.append("Keep approval opinion, decision actions and audit trail visible in one flow.")
    if page_type == "CONFIG":
        result.append("Group settings by scope and explain the operational impact of changes.")
    return result


def _collect_signal_text(
    profile: dict[str, Any],
    context_package: dict[str, Any],
    current_page: dict[str, Any],
    module_prd: Any,
    page_requirement: dict[str, Any],
) -> str:
    parts: list[str] = []
    for value in [
        profile,
        context_package.get("project"),
        context_package.get("module_ancestry"),
        context_package.get("hidden_prototype_context"),
        current_page,
        page_requirement,
        module_prd,
    ]:
        parts.append(_compact_text(value, 12000))
    return "\n".join(x for x in parts if x)


def _compact_text(value: Any, max_len: int = 8000) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value[:max_len]
    if isinstance(value, dict):
        chunks: list[str] = []
        for key, item in value.items():
            if key in {
                "content", "markdown", "contentMarkdown", "content_markdown",
                "module_basic_info", "business_background", "page_requirements", "pageRequirements",
                "description", "summary", "moduleName", "module_name", "projectName", "project_name",
                "industry", "themeKey", "visualTone", "primaryColor", "notes", "modulePath", "module_path",
                "page_type", "pageType", "route_path", "routePath", "primary_entity", "primaryEntity",
            }:
                chunks.append(f"{key}: {_compact_text(item, max_len // 2)}")
        if not chunks:
            chunks = [str(value)[:max_len]]
        return "\n".join(chunks)[:max_len]
    if isinstance(value, list):
        return "\n".join(_compact_text(x, max_len // max(1, min(len(value), 6))) for x in value[:8])[:max_len]
    return str(value)[:max_len]


def _source_signals(text: str) -> list[str]:
    signals: list[str] = []
    normalized = str(text or "").lower()
    for code, preset in _INDUSTRY_PRESETS.items():
        for keyword in preset.get("keywords") or []:
            if str(keyword).lower() in normalized and str(keyword) not in signals:
                signals.append(str(keyword))
    return signals[:18]


def _extract_theme_profile_from_request(request: Any) -> dict[str, Any]:
    if request is None:
        return {}
    input_payload = getattr(request, "input", None) or {}
    context_package = getattr(request, "context_package", None) or {}
    candidates = [
        input_payload.get("themeProfile") if isinstance(input_payload, dict) else None,
        input_payload.get("theme_profile") if isinstance(input_payload, dict) else None,
        input_payload.get("project_theme_profile") if isinstance(input_payload, dict) else None,
        context_package.get("project_theme_profile") if isinstance(context_package, dict) else None,
        context_package.get("themeProfile") if isinstance(context_package, dict) else None,
    ]
    for candidate in candidates:
        if isinstance(candidate, dict):
            return dict(candidate)
    return {}


def _extract_current_page_from_request(request: Any) -> dict[str, Any]:
    if request is None:
        return {}
    input_payload = getattr(request, "input", None) or {}
    context_package = getattr(request, "context_package", None) or {}
    for candidate in [
        input_payload.get("current_module") if isinstance(input_payload, dict) else None,
        context_package.get("current_module") if isinstance(context_package, dict) else None,
        context_package.get("module") if isinstance(context_package, dict) else None,
    ]:
        if isinstance(candidate, dict):
            return dict(candidate)
    return {"module_id": getattr(request, "module_id", None)}


def _extract_module_prd_from_context(context_package: dict[str, Any]) -> Any:
    artifacts = context_package.get("artifacts") if isinstance(context_package, dict) else None
    if isinstance(artifacts, dict):
        for key, value in artifacts.items():
            normalized_key = str(key or "").upper()
            artifact_type = ""
            if isinstance(value, dict):
                artifact_type = str(value.get("artifact_type") or value.get("artifactType") or value.get("type") or "").upper()
            if normalized_key == "MODULE_PRD" or artifact_type == "MODULE_PRD":
                return value.get("content") if isinstance(value, dict) and isinstance(value.get("content"), dict) else value
    return {}


def _match_page_requirement(module_prd: Any, current_page: dict[str, Any]) -> dict[str, Any]:
    prd = module_prd.get("content") if isinstance(module_prd, dict) and isinstance(module_prd.get("content"), dict) else module_prd
    if not isinstance(prd, dict):
        return {}
    pages = prd.get("page_requirements") or prd.get("pageRequirements") or []
    if not isinstance(pages, list):
        return {}
    current_names = [_norm(current_page.get(k)) for k in ["module_name", "moduleName", "pageName", "name"]]
    current_keys = [_norm(current_page.get(k)) for k in ["module_key", "moduleKey", "pageKey", "key"]]
    best: dict[str, Any] = {}
    best_score = 0
    for item in pages:
        if not isinstance(item, dict):
            continue
        name = _norm(item.get("page_name") or item.get("pageName") or item.get("name") or item.get("title"))
        key = _norm(item.get("page_key") or item.get("pageKey") or item.get("key"))
        desc = _norm(item.get("description") or item.get("summary"))
        score = 0
        if key and key in current_keys:
            score += 100
        if name and name in current_names:
            score += 100
        if any(x and (x in name or name in x or x in desc) for x in current_names):
            score += 50
        if any(x and (x in key or key in x or x in desc) for x in current_keys):
            score += 30
        if score > best_score:
            best = dict(item)
            best_score = score
    return best


def _normalize_industry_code(value: Any) -> str:
    raw = str(value or "").strip().upper()
    aliases = {
        "ECOMMERCE": "COMMERCE", "E_COMMERCE": "COMMERCE", "RETAIL": "COMMERCE", "电商": "COMMERCE", "商城": "COMMERCE",
        "TRADE": "FOREIGN_TRADE", "CROSS_BORDER": "FOREIGN_TRADE", "外贸": "FOREIGN_TRADE", "跨境": "FOREIGN_TRADE",
        "TICKET": "WORK_ORDER", "WORKFLOW": "WORK_ORDER", "SERVICE_DESK": "WORK_ORDER", "工单": "WORK_ORDER",
        "WMS": "WAREHOUSE", "INVENTORY": "WAREHOUSE", "仓储": "WAREHOUSE", "仓库": "WAREHOUSE",
        "MES": "MANUFACTURING", "FACTORY": "MANUFACTURING", "制造": "MANUFACTURING", "生产": "MANUFACTURING",
        "CUSTOMER": "CRM", "SALES": "CRM", "客户": "CRM",
        "FINANCIAL": "FINANCE", "财务": "FINANCE", "金融": "FINANCE",
        "GOVERNMENT": "GOV", "GOV_CLEAN": "GOV", "政企": "GOV", "政务": "GOV",
        "DATA_PLATFORM": "SAAS", "TECH": "SAAS", "平台": "SAAS",
        "HEALTH": "MEDICAL", "医疗": "MEDICAL",
        "EDU": "EDUCATION", "教育": "EDUCATION",
    }
    return aliases.get(raw, raw)


def _normalize_page_type(value: Any) -> str:
    text = _norm(value)
    if any(token in text for token in ["approval", "audit", "审核", "审批"]):
        return "APPROVAL"
    if any(token in text for token in ["dashboard", "workbench", "看板", "工作台", "首页工作台"]):
        return "DASHBOARD"
    if any(token in text for token in ["report", "analytics", "报表", "统计", "分析"]):
        return "REPORT"
    if any(token in text for token in ["config", "setting", "settings", "配置", "设置"]):
        return "CONFIG"
    if any(token in text for token in ["portal", "home", "门户", "首页"]):
        return "PORTAL"
    if any(token in text for token in ["wizard", "step", "向导", "步骤"]):
        return "WIZARD"
    if any(token in text for token in ["profile", "center", "个人中心", "用户中心"]):
        return "PROFILE"
    if any(token in text for token in ["search", "搜索"]):
        return "SEARCH"
    if any(token in text for token in ["list", "table", "列表", "查询", "管理"]):
        return "LIST"
    if any(token in text for token in ["detail", "view", "详情", "查看"]):
        return "DETAIL"
    if any(token in text for token in ["form", "create", "add", "edit", "update", "创建", "新建", "新增", "编辑", "修改", "录入"]):
        return "FORM"
    return "FORM"


def _is_empty_value(value: Any) -> bool:
    return value is None or str(value).strip() == ""


def _norm(value: Any) -> str:
    return re.sub(r"[\s_\-/\\:：]+", "", str(value or "").strip().lower())
