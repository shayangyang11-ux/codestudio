# A9.10.14.4.3.15.4.9.1_INDUSTRY_PAGE_QUALITY_BASELINE
from __future__ import annotations

from typing import Any

from app.schemas.ui_contract import UiContractArtifact

try:
    from app.agents.requirement.industry_style_profile import attach_industry_style_profile_to_ui_contract
except Exception:  # pragma: no cover - optional during partial imports
    attach_industry_style_profile_to_ui_contract = None  # type: ignore[assignment]

PATCH_TOKEN = "A9.10.14.4.3.15.4.9.1_INDUSTRY_PAGE_QUALITY_BASELINE"


def apply_industry_page_quality_baseline(
    artifact: UiContractArtifact,
    request: Any = None,
) -> UiContractArtifact:
    """Attach the minimum industry/page-pattern metadata needed by 15.4.9.

    This is deliberately not a visual-design beautifier.  It makes the generated
    UI_CONTRACT auditable for industry intent, page pattern, and template smell,
    while leaving the actual page structure controlled by the existing
    UiContractPagePatternIntelligence hardener.
    """
    original = artifact

    try:
        if attach_industry_style_profile_to_ui_contract is not None:
            artifact = attach_industry_style_profile_to_ui_contract(artifact, request=request)
    except Exception:
        artifact = original

    try:
        payload = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
        page = _dict(payload.get("page"))
        mock_data = _dict(payload.get("mockData"))
        quality = _dict(payload.get("qualitySummary"))

        style_profile = _dict(
            mock_data.get("__a9IndustryStyleProfile")
            or mock_data.get("industryStyleProfile")
            or payload.get("industryStyleProfile")
        )
        page_pattern = _infer_page_pattern(page)
        component_summary = _component_summary(page.get("componentTree"))
        template_smells = _template_smells(payload, page, style_profile, component_summary)

        baseline = {
            "marker": PATCH_TOKEN,
            "industryCode": style_profile.get("industryCode") or style_profile.get("effectiveIndustryCode") or "GENERAL",
            "industryName": style_profile.get("industryName") or style_profile.get("effectiveIndustryName") or "通用企业系统",
            "pagePattern": page_pattern,
            "layoutType": _dict(page.get("layout")).get("type") or "",
            "componentTypeCount": component_summary.get("componentTypeCount", {}),
            "visualRoles": component_summary.get("visualRoles", []),
            "requiredRegions": style_profile.get("pageTypeStyleRules", {}).get("requiredRegions") if isinstance(style_profile.get("pageTypeStyleRules"), dict) else [],
            "industryRules": style_profile.get("industryRules") or [],
            "componentGuidelines": style_profile.get("componentGuidelines") or [],
            "templateSmells": template_smells,
            "policy": "15.4.9 necessary baseline only: industry intent and page pattern must be traceable; visual richness is deferred to 15.4.11.",
        }
        mock_data["__a9IndustryPageQualityBaseline"] = baseline
        payload["mockData"] = mock_data

        payload["page"] = _attach_component_industry_tokens(page, baseline)

        warnings = list(quality.get("warnings") or []) if isinstance(quality.get("warnings"), list) else []
        marker = "A9.10.14.4.3.15.4.9.1 industry/page-pattern baseline attached."
        if marker not in warnings:
            warnings.append(marker)
        if template_smells:
            smell_warning = "15.4.9: potential generic template smell: " + "; ".join(template_smells[:3])
            if smell_warning not in warnings:
                warnings.append(smell_warning)
        quality["warnings"] = warnings
        quality.setdefault("blockers", [])
        payload["qualitySummary"] = quality

        summary = str(payload.get("analysisSummary") or "").strip()
        suffix = " 已补充 15.4.9 行业页面质量必要版追踪：行业意图、页面模式、组件角色和模板化风险已写入 mockData.__a9IndustryPageQualityBaseline。"
        if PATCH_TOKEN not in summary:
            payload["analysisSummary"] = (summary + suffix).strip() or suffix.strip()

        return artifact.__class__.model_validate(payload)
    except Exception:
        return artifact


def _dict(value: Any) -> dict[str, Any]:
    return dict(value) if isinstance(value, dict) else {}


def _list(value: Any) -> list[Any]:
    return list(value) if isinstance(value, list) else []


def _infer_page_pattern(page: dict[str, Any]) -> str:
    layout = _dict(page.get("layout"))
    text = " ".join(
        str(item or "")
        for item in [
            layout.get("type"),
            page.get("pageKind"),
            page.get("pageName"),
            page.get("pageKey"),
            page.get("title"),
        ]
    ).upper()
    if any(token in text for token in ["LOGIN", "AUTH"]):
        return "AUTH"
    if any(token in text for token in ["DASHBOARD", "看板", "首页", "工作台"]):
        return "DASHBOARD"
    if any(token in text for token in ["TABLE", "LIST", "SEARCH", "QUERY", "列表", "查询", "搜索"]):
        return "SEARCH_LIST"
    if any(token in text for token in ["DETAIL", "TABS", "详情", "明细"]):
        return "DETAIL_VIEW"
    if any(token in text for token in ["WORKFLOW", "APPROVAL", "审批", "审核"]):
        return "WORKFLOW"
    if any(token in text for token in ["UPLOAD", "导入", "上传"]):
        return "UPLOAD"
    if any(token in text for token in ["SETTING", "配置", "设置"]):
        return "SETTINGS_FORM"
    return "FORM_PAGE"


def _component_summary(components: Any) -> dict[str, Any]:
    flat: list[dict[str, Any]] = []

    def visit(items: Any) -> None:
        for item in _list(items):
            if not isinstance(item, dict):
                continue
            flat.append(item)
            visit(item.get("children"))

    visit(components)
    type_count: dict[str, int] = {}
    visual_roles: list[str] = []
    for item in flat:
        component_type = str(item.get("type") or "UNKNOWN").upper()
        type_count[component_type] = type_count.get(component_type, 0) + 1
        role = _dict(item.get("props")).get("visualRole")
        if role:
            visual_roles.append(str(role))
    return {
        "componentCount": len(flat),
        "componentTypeCount": type_count,
        "visualRoles": list(dict.fromkeys(visual_roles)),
    }


def _template_smells(
    payload: dict[str, Any],
    page: dict[str, Any],
    style_profile: dict[str, Any],
    component_summary: dict[str, Any],
) -> list[str]:
    smells: list[str] = []
    text = " ".join(
        str(item or "")
        for item in [
            page.get("pageName"),
            page.get("title"),
            page.get("description"),
            payload.get("analysisSummary"),
        ]
    )
    generic_terms = ["业务页面", "业务列表", "业务资源", "默认页面", "通用页面", "资源管理", "示例"]
    if any(term in text for term in generic_terms):
        smells.append("页面标题/说明仍包含通用模板词")

    industry = str(style_profile.get("industryCode") or style_profile.get("effectiveIndustryCode") or "GENERAL").upper()
    if industry == "GENERAL":
        smells.append("行业意图仍为 GENERAL")

    type_count = _dict(component_summary.get("componentTypeCount"))
    if int(component_summary.get("componentCount") or 0) < 3:
        smells.append("组件数量过少，复杂业务页可能仍像低保真模板")
    if not any(key in type_count for key in ["FORM", "TABLE", "CARD", "TABS", "STEPS", "TIMELINE", "TOOLBAR"]):
        smells.append("缺少可识别业务组件角色")

    return list(dict.fromkeys(smells))


def _attach_component_industry_tokens(page: dict[str, Any], baseline: dict[str, Any]) -> dict[str, Any]:
    def visit(items: Any) -> None:
        for item in _list(items):
            if not isinstance(item, dict):
                continue
            props = _dict(item.get("props"))
            props.setdefault("a9IndustryQualityBaseline", {
                "marker": PATCH_TOKEN,
                "industryCode": baseline.get("industryCode"),
                "pagePattern": baseline.get("pagePattern"),
            })
            item["props"] = props
            visit(item.get("children"))

    visit(page.get("componentTree"))
    return page
