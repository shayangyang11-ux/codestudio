# A9.10.14.4.3.15.3.3_REQUIREMENT_ASSET_READINESS_GATE
from __future__ import annotations

from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
from app.agents.requirement.prd_semantic_contract import build_page_semantic_contract


_MARK = "A9.10.14.4.3.15.3.3"


def assert_requirement_asset_ready_for_page_spec(request: AgentTaskRequest) -> dict[str, Any]:
    """PAGE_SPEC is hidden, but it must still be grounded by MODULE_PRD page_requirements."""
    return _assert_ready(request, stage="PAGE_SPEC")


def assert_requirement_asset_ready_for_ui_contract(request: AgentTaskRequest) -> dict[str, Any]:
    """UI_CONTRACT may call LLM, but only after PRD page contract is available."""
    return _assert_ready(request, stage="UI_CONTRACT")


def evaluate_requirement_asset_readiness(request: AgentTaskRequest, *, stage: str) -> dict[str, Any]:
    ctx = request.context_package or {}
    inp = request.input or {}
    current_page = _extract_current_module(request)
    node_type = _node_type(current_page)

    warnings: list[str] = []
    blockers: list[str] = []

    # Only PAGE generation requires a page-level contract. MODULE generation and
    # non-page tasks are intentionally not blocked by this guard.
    if node_type and "PAGE" not in node_type:
        return {
            "ready": True,
            "stage": stage,
            "guardVersion": _MARK,
            "currentPage": _page_identity(current_page),
            "contractAvailable": False,
            "contractSummary": {},
            "warnings": [],
            "blockers": [],
            "skipped": True,
            "skipReason": f"nodeType={node_type} is not PAGE",
        }

    module_prd = _extract_artifact(ctx, "MODULE_PRD")
    if not isinstance(module_prd, dict) or not module_prd:
        blockers.append(
            "当前 PAGE 缺少可用的 MODULE_PRD 上下文，不能生成 PAGE_SPEC / UI_CONTRACT。请先生成或升级所属模块 PRD。"
        )
        contract = {}
    else:
        try:
            module_prd = backfill_module_prd_semantic_backbone(module_prd, current_page=current_page)
        except Exception as exc:  # pragma: no cover - defensive guard
            warnings.append(f"MODULE_PRD semantic backfill skipped: {type(exc).__name__}: {exc}")
        try:
            contract = build_page_semantic_contract(module_prd, current_page)
        except Exception as exc:
            contract = {}
            blockers.append(f"MODULE_PRD 页面契约解析失败：{type(exc).__name__}: {exc}")

    if isinstance(module_prd, dict) and module_prd:
        page_items = _as_list(_first(module_prd, "page_requirements", "pageRequirements", "page_semantic_contracts", "pageSemanticContracts"))
        if not page_items:
            blockers.append(
                "当前 MODULE_PRD 没有结构化 page_requirements，不能稳定生成交互原型。请先用 LLM 升级旧 PRD 或补齐“页面需求与原型契约”。"
            )

    summary = _contract_summary(contract)
    if not _contract_is_actionable(contract):
        page_name = _text(
            current_page.get("module_name"),
            current_page.get("moduleName"),
            current_page.get("pageName"),
            current_page.get("name"),
            request.module_id,
        )
        blockers.append(
            f"当前 PAGE“{page_name}”没有匹配到可执行页面契约。请在 MODULE_PRD.page_requirements 中补齐该页面的页面类型、字段、按钮、表格列或跳转关系。"
        )

    # PAGE_SPEC is required before UI_CONTRACT, but the frontend chain may be
    # about to generate PAGE_SPEC automatically. Treat it as warning here; the
    # one-click PAGE_SPEC -> UI_CONTRACT chain remains responsible for ordering.
    if stage.upper() == "UI_CONTRACT":
        page_spec = _extract_artifact(ctx, "PAGE_SPEC")
        if not isinstance(page_spec, dict) or not page_spec:
            warnings.append("当前 PAGE_SPEC 尚未出现在上下文中；如果是一次点击生成交互原型，应先自动生成 PAGE_SPEC 再继续 UI_CONTRACT。")
        else:
            page_spec_contract = _first(page_spec, "page_semantic_contract", "pageSemanticContract")
            if not isinstance(page_spec_contract, dict) or not page_spec_contract:
                warnings.append("当前 PAGE_SPEC 缺少 page_semantic_contract；UI_CONTRACT 将以 MODULE_PRD.page_requirements 为最高语义来源。")

    ready = not blockers
    return {
        "ready": ready,
        "stage": stage,
        "guardVersion": _MARK,
        "currentPage": _page_identity(current_page),
        "contractAvailable": bool(contract),
        "contractSummary": summary,
        "warnings": _dedupe(warnings),
        "blockers": _dedupe(blockers),
        "skipped": False,
    }


def _assert_ready(request: AgentTaskRequest, *, stage: str) -> dict[str, Any]:
    readiness = evaluate_requirement_asset_readiness(request, stage=stage)
    if readiness.get("ready"):
        print(
            f"[{_MARK}] {stage} requirement asset readiness PASS page={readiness.get('currentPage')} summary={readiness.get('contractSummary')}",
            flush=True,
        )
        return readiness

    message = "；".join(str(item) for item in readiness.get("blockers") or [] if str(item).strip())
    if not message:
        message = f"{stage} requirement asset readiness failed."
    print(
        f"[{_MARK}] {stage} requirement asset readiness BLOCKED: {message}",
        flush=True,
    )
    raise ValueError(message)


def _extract_current_module(request: AgentTaskRequest) -> dict[str, Any]:
    inp = request.input or {}
    ctx = request.context_package or {}
    for value in [
        inp.get("current_module"),
        inp.get("currentModule"),
        ctx.get("current_module"),
        ctx.get("currentModule"),
        ctx.get("module"),
    ]:
        if isinstance(value, dict):
            return value
    return {
        "module_id": request.module_id,
        "module_key": "",
        "module_name": "",
        "module_type": "PAGE",
    }


def _extract_artifact(context_package: dict[str, Any], artifact_type: str) -> Any:
    artifacts = context_package.get("artifacts")
    normalized = str(artifact_type or "").strip().upper()
    if isinstance(artifacts, dict):
        for key, value in artifacts.items():
            value_type = ""
            if isinstance(value, dict):
                value_type = str(value.get("artifact_type") or value.get("artifactType") or value.get("type") or "").strip().upper()
            if str(key).strip().upper() == normalized or value_type == normalized:
                return _unwrap_artifact(value)
    if isinstance(artifacts, list):
        for item in artifacts:
            if isinstance(item, dict) and str(item.get("artifact_type") or item.get("artifactType") or "").strip().upper() == normalized:
                return _unwrap_artifact(item)
    return {}


def _unwrap_artifact(value: Any) -> Any:
    if not isinstance(value, dict):
        return value
    content = value.get("content")
    if isinstance(content, dict):
        result = dict(content)
        markdown = _text(value.get("content_markdown"), value.get("contentMarkdown"), result.get("markdown"), result.get("contentMarkdown"), result.get("content_markdown"))
        if markdown:
            result.setdefault("markdown", markdown)
            result.setdefault("contentMarkdown", markdown)
            result.setdefault("content_markdown", markdown)
        return result
    return value


def _contract_is_actionable(contract: Any) -> bool:
    if not isinstance(contract, dict) or not contract:
        return False
    page_type = _text(contract.get("pageType"), contract.get("universalPageType"), contract.get("layoutType"))
    description = _text(contract.get("description"), contract.get("summary"))
    lists = [
        contract.get("formFields"),
        contract.get("filterFields"),
        contract.get("tableColumns"),
        contract.get("primaryActions"),
        contract.get("rowActions"),
        contract.get("navigationActions"),
        contract.get("navigationTargets"),
        contract.get("widgets"),
        contract.get("metrics"),
        contract.get("sections"),
    ]
    if any(isinstance(item, list) and len(item) > 0 for item in lists):
        return True
    # Some pages such as PORTAL / DASHBOARD may start as description-led, but
    # still must carry a non-generic pageType to prevent CRUD fallback.
    return bool(page_type and page_type.upper() not in {"PAGE", "OTHER", ""} and description)


def _contract_summary(contract: Any) -> dict[str, Any]:
    if not isinstance(contract, dict):
        return {}
    return {
        "pageType": _text(contract.get("pageType"), contract.get("universalPageType")),
        "layoutType": _text(contract.get("layoutType"), contract.get("layout_type")),
        "formFieldCount": len(_as_list(contract.get("formFields"))),
        "filterFieldCount": len(_as_list(contract.get("filterFields"))),
        "tableColumnCount": len(_as_list(contract.get("tableColumns"))),
        "primaryActionCount": len(_as_list(contract.get("primaryActions"))),
        "rowActionCount": len(_as_list(contract.get("rowActions"))),
        "navigationCount": len(_as_list(contract.get("navigationActions"))) + len(_as_list(contract.get("navigationTargets"))),
    }


def _page_identity(page: dict[str, Any]) -> dict[str, Any]:
    return {
        "pageId": _text(page.get("module_id"), page.get("moduleId"), page.get("pageId"), page.get("id")),
        "pageKey": _text(page.get("module_key"), page.get("moduleKey"), page.get("pageKey"), page.get("key")),
        "pageName": _text(page.get("module_name"), page.get("moduleName"), page.get("pageName"), page.get("name")),
        "nodeType": _node_type(page),
    }


def _node_type(page: dict[str, Any]) -> str:
    extra = page.get("extra") if isinstance(page.get("extra"), dict) else {}
    return _text(
        page.get("nodeType"),
        page.get("node_type"),
        page.get("moduleType"),
        page.get("module_type"),
        page.get("type"),
        page.get("pageKind"),
        extra.get("nodeType"),
        extra.get("moduleType"),
    ).upper()


def _first(source: dict[str, Any], *keys: str) -> Any:
    if not isinstance(source, dict):
        return None
    for key in keys:
        if key in source and source.get(key) not in (None, ""):
            return source.get(key)
    return None


def _as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _dedupe(values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value or "").strip()
        if text and text not in seen:
            result.append(text)
            seen.add(text)
    return result
