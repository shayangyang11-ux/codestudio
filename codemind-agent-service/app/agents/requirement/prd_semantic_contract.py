# A9.10.14.4.3.13.1_UNIVERSAL_PRD_SEMANTIC_CONTRACT
from __future__ import annotations
import re
from typing import Any

try:
    from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
except Exception:
    backfill_module_prd_semantic_backbone = None  # type: ignore


def build_page_semantic_contract(module_prd: Any, current_page: dict[str, Any] | None = None) -> dict[str, Any]:
    current_page = current_page or {}
    prd = _unwrap(module_prd)
    if isinstance(prd, dict) and backfill_module_prd_semantic_backbone:
        try:
            prd = backfill_module_prd_semantic_backbone(prd, current_page=current_page)
        except Exception:
            pass
    if not isinstance(prd, dict):
        return {}

    # A9.10.14.4.3.13.2_NO_LLM_PRD_CONTRACT_LOCK
    # User edits PRD in markdown. Existing module_prd.content.page_requirements may
    # be stale/generated before the edit. Always parse markdown section 8 and let
    # the matched markdown page requirement override/complete structured cards.
    structured_page_items = _as_list(_first(prd, "page_semantic_contracts", "pageSemanticContracts"))
    if not structured_page_items:
        structured_page_items = _as_list(_first(prd, "page_requirements", "pageRequirements"))

    markdown = _text(_first(prd, "markdown", "contentMarkdown", "content_markdown"))
    markdown_page_items = _derive_pages_from_markdown(markdown, current_page) if markdown else []

    structured_page_req = _match_page(structured_page_items, current_page)
    markdown_page_req = _match_page(markdown_page_items, current_page)
    page_req = _merge_page_requirements(structured_page_req, markdown_page_req)

    # A9.10.14.4.3.14.1_FORM_CONTRACT_SEED_LOCK_FIX_PRD
    # If the current PAGE (for example 创建/编辑/录入页) is not explicitly
    # written as a separate MODULE_PRD page card, infer a small but stable
    # page semantic contract from sibling page requirements and data fields.
    # This is not a visual design replacement; it is the business contract seed
    # that prevents a blind LLM pass from turning a form page into a generic
    # detail/process page.
    if not page_req:
        page_req = _infer_page_requirement_from_module_context(prd, current_page)
        if not page_req:
            return {}

    desc = _text(page_req.get("description"), page_req.get("summary"), page_req.get("body"), page_req.get("requirement"))
    page_name = _text(page_req.get("pageName"), page_req.get("page_name"), page_req.get("name"), page_req.get("title"),
                      current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"))
    page_key = _safe_key(_text(page_req.get("pageKey"), page_req.get("page_key"), page_req.get("key"),
                               current_page.get("module_key"), current_page.get("moduleKey"), page_name), "page")

    filters = _uniq([*_labels(page_req.get("filterFields") or page_req.get("filter_fields") or page_req.get("filters") or page_req.get("fields")),
                     *_extract_between(desc, [r"筛选区应支持按", r"应支持按", r"支持按", r"按"], [r"进行查询", r"查询", r"筛选"])])
    columns = _uniq([*_labels(page_req.get("tableColumns") or page_req.get("table_columns") or page_req.get("columns") or page_req.get("displayFields")),
                     *_extract_between(desc, [r"结果列表至少展示", r"列表至少展示", r"至少展示"], [r"字段"])])
    row_actions = _uniq([*_labels(page_req.get("rowActions") or page_req.get("row_actions")), *_row_actions(desc)])
    quoted = _quoted(desc)
    primary = _uniq([*_labels(page_req.get("primaryActions") or page_req.get("primary_actions") or page_req.get("toolbarActions") or page_req.get("toolbar_actions")),
                     *_primary_actions(desc),
                     *[x for x in _labels(page_req.get("userActions") or page_req.get("user_actions") or page_req.get("actions")) if x not in row_actions],
                     *[x for x in quoted if x not in row_actions and not _looks_row(x)]])
    navs = _nav_actions(desc)
    for nav in navs:
        if _looks_row(nav["label"]) and nav["label"] not in row_actions:
            row_actions.append(nav["label"])
    if filters:
        primary = _uniq(["查询", "重置", *primary])

    field_objs = [_field(x) for x in filters]
    col_objs = [_column(x) for x in columns]
    primary_objs = [_action(x, "PRIMARY") for x in primary]
    row_objs = [_action(x, "ROW") for x in row_actions]
    primary_objs, row_objs = _normalize_action_scopes(primary_objs, row_objs)

    for act in primary_objs + row_objs:
        for nav in navs:
            if _norm(act["label"]) == _norm(nav["label"]):
                act["navigationIntent"] = True
                act["targetPageName"] = nav["targetPageName"]

    return {
        "schemaVersion": "1.0",
        "contractType": "PAGE_SEMANTIC_CONTRACT",
        "source": page_req.get("source") or "MODULE_PRD",
        "pageKey": page_key,
        "pageName": page_name,
        "pageType": _page_type(page_name, page_key, desc, filters, columns),
        "description": desc,
        "filterFields": field_objs,
        "tableColumns": col_objs,
        "formFields": [_field(x) for x in _labels(page_req.get("formFields") or page_req.get("form_fields"))],
        "primaryActions": primary_objs,
        "rowActions": row_objs,
        "navigationActions": navs,
        "strictness": {
            "removeUnspecifiedFilters": bool(field_objs),
            "removeUnspecifiedTableColumns": bool(col_objs),
            "removeUnspecifiedRowActions": bool(row_objs),
            "failOnMissingExplicitRequirement": bool(field_objs or col_objs or row_objs or primary_objs),
        },
        "coverageRules": [
            *[{"kind": "FILTER_FIELD", "key": x["fieldKey"], "label": x["label"]} for x in field_objs],
            *[{"kind": "TABLE_COLUMN", "key": x["columnKey"], "label": x["label"]} for x in col_objs],
            *[{"kind": "PRIMARY_ACTION", "key": x["actionKey"], "label": x["label"]} for x in primary_objs],
            *[{"kind": "ROW_ACTION", "key": x["actionKey"], "label": x["label"]} for x in row_objs],
        ],
    }


def _derive_pages_from_markdown(markdown: str, current_page: dict[str, Any]) -> list[dict[str, Any]]:
    result = []
    current = None
    heading = re.compile(r"^\s*(?:#{1,6}\s*)?8\.(\d+)\s+(.+?)\s*$")
    any_head = re.compile(r"^\s*(?:#{1,6}\s*)?\d+(?:\.\d+)?\s+.+?\s*$")
    for raw in markdown.splitlines():
        line = raw.strip()
        m = heading.match(line)
        if m:
            if current:
                result.append({"pageName": current["title"], "description": "\n".join(current["body"]).strip(), "source": "MARKDOWN_SECTION_8"})
            current = {"title": m.group(2).strip(), "body": []}
            continue
        if current and any_head.match(line) and not heading.match(line):
            result.append({"pageName": current["title"], "description": "\n".join(current["body"]).strip(), "source": "MARKDOWN_SECTION_8"})
            current = None
            continue
        if current:
            current["body"].append(raw)
    if current:
        result.append({"pageName": current["title"], "description": "\n".join(current["body"]).strip(), "source": "MARKDOWN_SECTION_8"})
    return result



# A9.10.14.4.3.14.1_FORM_CONTRACT_SEED_LOCK_FIX_PRD
def _infer_page_requirement_from_module_context(
    prd: dict[str, Any],
    current_page: dict[str, Any],
) -> dict[str, Any]:
    """Infer a minimal PAGE requirement when PRD has only sibling/data hints.

    This keeps the final one-button pipeline balanced: LLM may still design the
    page, but fields/actions/page type come from a deterministic contract seed.
    """
    page_name = _text(
        current_page.get("module_name"), current_page.get("moduleName"),
        current_page.get("pageName"), current_page.get("name"),
    )
    page_key = _safe_key(_text(
        current_page.get("module_key"), current_page.get("moduleKey"),
        current_page.get("pageKey"), current_page.get("key"), page_name,
    ), "page")
    if not page_name and not page_key:
        return {}

    inferred_type = _infer_current_page_type(page_name, page_key, current_page)
    if inferred_type not in {"FORM", "DETAIL", "APPROVAL", "LIST"}:
        return {}

    page_items = _as_list(_first(prd, "page_requirements", "pageRequirements", "page_semantic_contracts", "pageSemanticContracts"))
    data_items = _as_list(_first(prd, "data_requirements", "dataRequirements"))
    current_stem = _domain_stem(" ".join([page_name, page_key]))

    sibling_fields: list[str] = []
    sibling_filters: list[str] = []
    sibling_actions: list[str] = []

    for item in page_items:
        if not isinstance(item, dict):
            continue
        page_text = " ".join([
            _text(_first(item, "pageName", "page_name", "name", "title")),
            _text(_first(item, "pageKey", "page_key", "key", "pageId", "id")),
            _text(_first(item, "description", "summary", "body", "requirement")),
        ])
        item_stem = _domain_stem(page_text)
        if current_stem and item_stem and current_stem not in item_stem and item_stem not in current_stem:
            # Keep broad module pages only when there is no better signal.
            if sibling_fields:
                continue
        desc = _text(_first(item, "description", "summary", "body", "requirement"))
        sibling_fields.extend(_labels(_first(item, "formFields", "form_fields")))
        sibling_fields.extend(_labels(_first(item, "tableColumns", "table_columns", "columns", "displayFields")))
        sibling_fields.extend(_extract_between(desc, [r"结果列表至少展示", r"列表至少展示", r"至少展示", r"字段至少包含"], [r"字段", r"。", r"\n"]))
        sibling_filters.extend(_labels(_first(item, "filterFields", "filter_fields", "filters", "fields")))
        sibling_filters.extend(_extract_between(desc, [r"筛选区应支持按", r"应支持按", r"支持按", r"按"], [r"进行查询", r"查询", r"筛选"]))
        sibling_actions.extend(_labels(_first(item, "primaryActions", "primary_actions", "userActions", "user_actions", "actions")))
        sibling_actions.extend(_quoted(desc))

    data_fields: list[str] = []
    for item in data_items:
        if not isinstance(item, dict):
            continue
        entity_text = " ".join([
            _text(_first(item, "entity_name", "entityName", "name", "title")),
            _text(_first(item, "entity_key", "entityKey", "key")),
            _text(_first(item, "description", "summary")),
        ])
        entity_stem = _domain_stem(entity_text)
        if current_stem and entity_stem and current_stem not in entity_stem and entity_stem not in current_stem:
            continue
        data_fields.extend(_labels(_first(item, "important_fields", "importantFields", "fields", "fieldNames")))

    candidate_fields = _uniq([*data_fields, *sibling_fields, *sibling_filters])
    candidate_fields = _editable_business_fields(candidate_fields, page_name, inferred_type)

    if inferred_type in {"FORM", "APPROVAL"}:
        if not candidate_fields:
            return {}
        actions = ["保存", "取消"]
        if inferred_type == "APPROVAL":
            actions = ["通过", "驳回", "取消"]
        return {
            "pageKey": page_key,
            "pageName": page_name or page_key,
            "description": f"{page_name or page_key}用于录入、维护和提交业务信息。页面应展示核心表单字段，并提供“{'”“'.join(actions)}”操作。",
            "formFields": candidate_fields,
            "primaryActions": actions,
            "rowActions": [],
            "tableColumns": [],
            "filterFields": [],
            "source": "A9_INFERRED_FROM_SIBLING_PRD_AND_DATA_FIELDS",
        }

    if inferred_type == "DETAIL":
        if not candidate_fields:
            return {}
        return {
            "pageKey": page_key,
            "pageName": page_name or page_key,
            "description": f"{page_name or page_key}用于查看业务对象的核心字段和状态。",
            "formFields": candidate_fields,
            "primaryActions": ["返回"],
            "rowActions": [],
            "tableColumns": [],
            "filterFields": [],
            "source": "A9_INFERRED_FROM_SIBLING_PRD_AND_DATA_FIELDS",
        }

    if inferred_type == "LIST" and candidate_fields:
        filters = _uniq(sibling_filters[:5] or candidate_fields[:4])
        return {
            "pageKey": page_key,
            "pageName": page_name or page_key,
            "description": f"{page_name or page_key}用于查询和管理业务数据。",
            "filterFields": filters,
            "tableColumns": candidate_fields,
            "primaryActions": _uniq(["查询", "重置", *[x for x in sibling_actions if not _looks_row(x)]])[:6],
            "rowActions": _uniq([x for x in sibling_actions if _looks_row(x)])[:5],
            "formFields": [],
            "source": "A9_INFERRED_FROM_SIBLING_PRD_AND_DATA_FIELDS",
        }

    return {}


def _infer_current_page_type(page_name: str, page_key: str, current_page: dict[str, Any]) -> str:
    raw = _text(current_page.get("pageKind"), current_page.get("page_kind"), current_page.get("module_type"), current_page.get("moduleType"))
    text = _norm(" ".join([page_name, page_key, raw]))
    if any(token in text for token in ["创建", "新建", "新增", "录入", "编辑", "修改", "create", "add", "edit", "form"]):
        return "FORM"
    if any(token in text for token in ["审核", "审批", "approval", "audit"]):
        return "APPROVAL"
    if any(token in text for token in ["详情", "查看", "detail", "view"]):
        return "DETAIL"
    if any(token in text for token in ["列表", "查询", "搜索", "管理", "list", "search", "table"]):
        return "LIST"
    return "PAGE"


def _domain_stem(value: Any) -> str:
    text = _norm(value)
    for token in [
        "页面", "页", "列表", "查询", "搜索", "管理", "详情", "查看", "创建", "新建", "新增", "录入", "编辑", "修改", "审核", "审批",
        "page", "list", "search", "table", "detail", "view", "create", "add", "edit", "form", "approval", "audit",
    ]:
        text = text.replace(token, "")
    return text


def _editable_business_fields(labels: list[str], page_name: str, page_type: str) -> list[str]:
    result: list[str] = []
    is_create = bool(re.search(r"创建|新建|新增|录入|create|add", page_name, re.I))
    for label in labels:
        x = _semantic_label(label)
        if not x:
            continue
        if re.search(r"操作|按钮|创建人|创建时间|更新人|更新时间|审核人|审核时间|删除人|删除时间", x):
            continue
        if is_create and re.search(r"状态$|流程状态|审批状态|审核状态", x):
            continue
        if x not in result:
            result.append(x)
    return result[:12]

def _merge_page_requirements(
    structured: dict[str, Any] | None,
    markdown: dict[str, Any] | None,
) -> dict[str, Any]:
    # A9.10.14.4.3.13.2.9.1_MARKDOWN_WITH_STRUCTURED_FALLBACK
    # Visible markdown edits are authoritative for descriptive text, but
    # structured PRD arrays remain a safe fallback when regex extraction misses
    # columns/actions. Do not drop structured arrays wholesale; doing so can
    # turn a LIST page into CUSTOM_GRID and make QualityGate reject TABLE/EMPTY.
    structured = dict(structured or {})
    markdown = dict(markdown or {})

    if not structured:
        return markdown
    if not markdown:
        return structured

    merged = dict(structured)

    markdown_body = _text(
        markdown.get("description"),
        markdown.get("summary"),
        markdown.get("body"),
        markdown.get("requirement"),
    )
    if markdown_body:
        # Text requirement comes from user-edited markdown.
        merged["description"] = markdown_body
        merged["source"] = markdown.get("source") or "MARKDOWN_SECTION_8"
        merged["_merged_from_markdown"] = True
        merged["_markdown_source"] = markdown.get("source") or "MARKDOWN_SECTION_8"
        merged["_structured_arrays_kept_as_fallback"] = True

    for key in ["pageName", "page_name", "name", "title", "source"]:
        if _text(markdown.get(key)):
            merged[key] = markdown.get(key)
        elif not _text(merged.get(key)) and _text(structured.get(key)):
            merged[key] = structured.get(key)

    return merged


def _match_page(items: list[Any], current: dict[str, Any]) -> dict[str, Any]:
    names = {_norm(x) for x in [current.get("module_name"), current.get("moduleName"), current.get("pageName"), current.get("name")] if _text(x)}
    keys = {_norm(x) for x in [current.get("module_key"), current.get("moduleKey"), current.get("pageKey"), current.get("key")] if _text(x)}
    best, score = {}, -1
    for i, raw in enumerate(items):
        if not isinstance(raw, dict):
            continue
        n = _norm(_first(raw, "pageName", "page_name", "name", "title"))
        k = _norm(_first(raw, "pageKey", "page_key", "key", "pageId", "id"))
        d = _norm(_first(raw, "description", "summary", "body", "requirement"))
        s = 0
        if k and k in keys: s += 120
        if n and n in names: s += 120
        if any(x and (x in n or n in x or x in d) for x in names): s += 60
        if any(x and (x in k or k in x or x in d) for x in keys): s += 30
        if s == 0 and i == 0 and not (names or keys): s = 1
        if s > score:
            best, score = dict(raw), s
    return best if score > 0 else {}


def _extract_between(text: str, starts: list[str], ends: list[str]) -> list[str]:
    out = []
    for start in starts:
        idx = text.find(start)
        if idx < 0: continue
        frag = text[idx + len(start):]
        end_idx = len(frag)
        for end in ends:
            j = frag.find(end)
            if j >= 0: end_idx = min(end_idx, j)
        out.extend(_split(frag[:end_idx]))
    return _clean(out)


def _row_actions(text: str) -> list[str]:
    out = []
    for m in re.finditer(r"每行.*?提供(.+?)操作", text or ""):
        out.extend(_quoted(m.group(0)) or _split(m.group(1)))
    return _clean(out)


def _primary_actions(text: str) -> list[str]:
    out = []
    for m in re.finditer(r"(?:页面|列表页|工具栏|主要操作区).*?提供(.+?)(?:按钮|入口)", text or ""):
        out.extend(_quoted(m.group(0)) or _split(m.group(1)))
    for m in re.finditer(r"(?:将|优先展示)[“\"]([^”\"]+)[”\"](?:作为主要操作入口|入口)", text or ""):
        out.append(m.group(1))
    return _clean(out)


def _nav_actions(text: str) -> list[dict[str, str]]:
    return [{"label": m.group(1).strip(), "targetPageName": m.group(2).strip(), "source": "PRD_CLICK_ENTER"} for m in re.finditer(r"点击[“\"]([^”\"]+)[”\"]进入(.+?)(?:，|。|\n|$)", text or "")]


def _quoted(text: str) -> list[str]:
    return _uniq(re.findall(r"[“\"]([^”\"]+)[”\"]", text or ""))


def _labels(value: Any) -> list[str]:
    if isinstance(value, list):
        out: list[str] = []
        for item in value:
            out.append(_semantic_label(item))
        return _clean(out)
    if isinstance(value, str):
        return _clean(_split(value))
    return []

def _semantic_label(value: Any) -> str:
    if isinstance(value, dict):
        raw = _text(
            value.get("label"),
            value.get("fieldName"),
            value.get("columnName"),
            value.get("actionName"),
            value.get("name"),
            value.get("title"),
            value.get("key"),
        )
    else:
        raw = _text(value)

    x = str(raw or "").strip(" \t\r\n，,。；;:：、")
    x = re.sub(r"\s+", "", x)
    x = re.sub(r"^(按|以|根据|和|及|以及|或|并|包含|包括|支持|提供)+", "", x)

    suffixes = [
        "进行查询",
        "进行筛选",
        "进行检索",
        "用于查询",
        "用于筛选",
        "字段",
        "按钮",
        "操作",
        "入口",
        "查询",
        "筛选",
        "检索",
        "进行",
    ]
    changed = True
    while changed and x:
        changed = False
        for suffix in suffixes:
            if x.endswith(suffix) and len(x) > len(suffix):
                x = x[: -len(suffix)].strip(" \t\r\n，,。；;:：、")
                changed = True

    return x


def _dedupe_action_specs(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        if not isinstance(item, dict):
            continue
        label = _semantic_label(item.get("label") or item.get("actionName") or item.get("actionKey"))
        if not label:
            continue
        normalized = dict(item)
        normalized["label"] = label
        normalized["actionKey"] = _action_key(label)
        normalized["kind"] = _action_kind(label)
        key = _norm(normalized["actionKey"] or label)
        if key and key not in seen:
            seen.add(key)
            result.append(normalized)
    return result


def _normalize_action_scopes(
    primary_actions: list[dict[str, Any]],
    row_actions: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    primary: list[dict[str, Any]] = []
    row: list[dict[str, Any]] = []

    def add_unique(bucket: list[dict[str, Any]], action: dict[str, Any]) -> None:
        label = _semantic_label(action.get("label") or action.get("actionName") or action.get("actionKey"))
        if not label:
            return
        normalized = dict(action)
        normalized["label"] = label
        normalized["actionKey"] = _action_key(label)
        normalized["kind"] = _action_kind(label)
        key = _norm(normalized["actionKey"] or label)
        if key and all(_norm(x.get("actionKey") or x.get("label")) != key for x in bucket):
            bucket.append(normalized)

    row_kinds = {"VIEW", "EDIT", "APPROVAL", "DELETE"}
    primary_kinds = {"CREATE", "EXPORT", "REFRESH", "CUSTOM"}

    for action in _dedupe_action_specs(primary_actions):
        kind = str(action.get("kind") or "").upper()
        if kind in row_kinds:
            add_unique(row, action)
        elif kind in {"QUERY", "RESET"}:
            add_unique(primary, action)
        elif kind in primary_kinds:
            add_unique(primary, action)
        else:
            add_unique(primary, action)

    for action in _dedupe_action_specs(row_actions):
        kind = str(action.get("kind") or "").upper()
        if kind == "CREATE":
            add_unique(primary, action)
        elif kind in {"QUERY", "RESET", "EXPORT", "REFRESH"}:
            add_unique(primary, action)
        else:
            add_unique(row, action)

    return primary, row

def _field(label: str) -> dict[str, Any]:
    label = _semantic_label(label)
    key = _field_key(label)
    typ = _field_type(label)
    return {
        "fieldKey": key,
        "label": label,
        "type": typ,
        "required": False,
        "placeholder": ("请选择" if typ in {"SELECT", "DATE", "DATETIME"} else "请输入") + label,
    }

def _column(label: str) -> dict[str, Any]:
    label = _semantic_label(label)
    key = _field_key(label)
    return {
        "columnKey": key,
        "fieldKey": key,
        "label": label,
        "type": _column_type(label),
        "sortable": bool(re.search(r"时间|日期|数量|金额", label)),
    }

def _action(label: str, scope: str) -> dict[str, Any]:
    label = _semantic_label(label)
    return {
        "actionKey": _action_key(label),
        "label": label,
        "scope": scope,
        "kind": _action_kind(label),
    }

def _action_kind(label: str) -> str:
    if re.search(r"查询|搜索", label): return "QUERY"
    if re.search(r"重置|清空", label): return "RESET"
    if re.search(r"新增|新建|创建|添加", label): return "CREATE"
    if re.search(r"删除|移除|作废", label): return "DELETE"
    if re.search(r"编辑|修改", label): return "EDIT"
    if re.search(r"审核|审批", label): return "APPROVAL"
    if re.search(r"查看|详情", label): return "VIEW"
    if re.search(r"导出", label): return "EXPORT"
    if re.search(r"刷新", label): return "REFRESH"
    return "CUSTOM"


def _field_type(label: str) -> str:
    if re.search(r"状态|类型|供应商|仓库|客户|角色|分类", label): return "SELECT"
    if "日期" in label: return "DATE"
    if "时间" in label: return "DATETIME"
    if re.search(r"数量|金额|价格|库存", label): return "NUMBER"
    return "INPUT"


def _column_type(label: str) -> str:
    if "状态" in label: return "TAG"
    if re.search(r"数量|金额|价格|库存", label): return "NUMBER"
    if "日期" in label: return "DATE"
    if "时间" in label: return "DATETIME"
    return "TEXT"


def _field_key(label: str) -> str:
    mapping = [("入库单号","inbound_order_no"),("出库单号","outbound_order_no"),("入库状态","inbound_status"),("出库状态","outbound_status"),("入库总数量","inbound_total_quantity"),("创建日期","created_date"),("创建时间","created_time"),("创建人","creator"),("订单号","order_no"),("单号","order_no"),("供应商","supplier"),("仓库","warehouse"),("客户","customer"),("经办人","handler"),("总数量","total_quantity"),("数量","quantity"),("状态","status"),("备注","remark"),("编号","code"),("名称","name")]
    for token, key in mapping:
        if token in label: return key
    return _safe_key(label, "field")


def _action_key(label: str) -> str:
    mapping = [("查询","query"),("重置","reset"),("新建","create"),("新增","create"),("创建","create"),("查看详情","view_detail"),("查看审核","view_approval"),("编辑数量","edit_quantity"),("编辑","edit"),("审核","approval"),("详情","view_detail"),("删除","delete"),("导出","export"),("刷新","refresh"),("保存","save"),("取消","cancel")]
    for token, key in mapping:
        if token in label: return key
    return _safe_key(label, "action")


def _page_type(name: str, key: str, desc: str, filters: list[str], columns: list[str]) -> str:
    t = _norm(" ".join([name, key, desc]))
    if filters or columns or "列表" in t or "查询" in t: return "LIST"
    if "详情" in t: return "DETAIL"
    if any(x in t for x in ["创建","新建","新增","编辑","表单","录入"]): return "FORM"
    if any(x in t for x in ["审核","审批"]): return "APPROVAL"
    return "PAGE"


def _looks_row(label: str) -> bool:
    return bool(re.search(r"查看|详情|编辑|修改|审核|审批|删除|移除|作废", label))


def _split(value: Any) -> list[str]:
    return [x.strip(" \n\t，,。；;:：") for x in re.split(r"[、,，/和及]+", re.sub(r"[。；;:：]", "、", str(value or ""))) if x.strip(" \n\t，,。；;:：")]


def _clean(items: list[str]) -> list[str]:
    out: list[str] = []
    for item in items:
        x = _semantic_label(item)
        if not x:
            continue
        if x in {"查询", "重置", "按钮", "字段", "操作", "入口", "进行"}:
            continue
        out.append(x)
    return _uniq(out)

def _unwrap(value: Any) -> Any:
    if isinstance(value, dict) and isinstance(value.get("content"), dict):
        res = dict(value["content"])
        for k in ["markdown","contentMarkdown","content_markdown"]:
            if k not in res and value.get(k): res[k] = value[k]
        return res
    return value


def _first(source: dict[str, Any], *keys: str) -> Any:
    for k in keys:
        if k in source and source.get(k) not in (None, ""): return source.get(k)
    return None


def _as_list(value: Any) -> list[Any]: return value if isinstance(value, list) else []
def _text(*values: Any) -> str:
    for v in values:
        if v is not None and str(v).strip(): return str(v).strip()
    return ""
def _norm(value: Any) -> str: return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())
def _safe_key(value: Any, fallback: str) -> str:
    t = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", str(value or "").strip().lower())
    return re.sub(r"_+", "_", t).strip("_") or fallback
def _uniq(items: list[str]) -> list[str]:
    out, seen = [], set()
    for item in items:
        x = str(item or "").strip()
        k = _norm(x)
        if x and k not in seen:
            seen.add(k); out.append(x)
    return out

# A9.10.14.4.3.14.2.1_FORM_IDENTITY_WRAPPER_FIX
# Robust page-identity wrapper.  Do not depend on fragile inline anchors.
# Current PAGE names such as 创建/新建/新增/录入/编辑 are business identity signals;
# when MODULE_PRD lacks a dedicated page card, synthesize a locked FORM contract
# from sibling page/data fields so LLM can design visuals without changing page type.
try:
    _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT = build_page_semantic_contract
except NameError:  # pragma: no cover
    _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT = None


def _a9_14_2_1_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a9_14_2_1_norm(value: Any) -> str:
    return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())


def _a9_14_2_1_current_text(current_page: dict[str, Any]) -> str:
    if not isinstance(current_page, dict):
        return ""
    extra = current_page.get("extra") if isinstance(current_page.get("extra"), dict) else {}
    return " ".join([
        _a9_14_2_1_text(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name")),
        _a9_14_2_1_text(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key")),
        _a9_14_2_1_text(current_page.get("module_type"), current_page.get("moduleType"), current_page.get("pageKind"), extra.get("pageKind")),
        _a9_14_2_1_text(current_page.get("module_path"), current_page.get("modulePath"), extra.get("modulePath")),
    ])


def _a9_14_2_1_is_form_like_current_page(current_page: dict[str, Any]) -> bool:
    text = _a9_14_2_1_norm(_a9_14_2_1_current_text(current_page))
    return bool(re.search(r"创建|新建|新增|录入|登记|编辑|修改|create|add|edit|update|form", text, re.I))


def _a9_14_2_1_is_list_like_page_item(item: dict[str, Any]) -> bool:
    text = _a9_14_2_1_norm(" ".join([
        _a9_14_2_1_text(item.get("pageName"), item.get("page_name"), item.get("name"), item.get("title")),
        _a9_14_2_1_text(item.get("pageKey"), item.get("page_key"), item.get("key")),
        _a9_14_2_1_text(item.get("description"), item.get("summary"), item.get("body")),
    ]))
    return bool(re.search(r"列表|查询|搜索|管理|list|search|table", text, re.I))


def _a9_14_2_1_domain_tokens(current_page: dict[str, Any]) -> list[str]:
    name = _a9_14_2_1_text(current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"))
    key = _a9_14_2_1_text(current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"))
    text = re.sub(r"创建|新建|新增|录入|登记|编辑|修改|页面|页|表单|create|add|edit|update|form|page|[_\-]", " ", f"{name} {key}", flags=re.I)
    return [x for x in re.split(r"\s+", text.strip()) if x]


def _a9_14_2_1_split_terms(value: Any) -> list[str]:
    cleaned = re.sub(r"[。；;:：]", "、", str(value or ""))
    return [x.strip(" 、,，.。\n\t") for x in re.split(r"[、,，/和及]+", cleaned) if x.strip(" 、,，.。\n\t")]


def _a9_14_2_1_extract_between(text: str, starts: list[str], ends: list[str]) -> list[str]:
    result: list[str] = []
    for start in starts:
        idx = text.find(start)
        if idx < 0:
            continue
        frag = text[idx + len(start):]
        end_idx = len(frag)
        for end in ends:
            j = frag.find(end)
            if j >= 0:
                end_idx = min(end_idx, j)
        result.extend(_a9_14_2_1_split_terms(frag[:end_idx]))
    return result


def _a9_14_2_1_labels(value: Any) -> list[str]:
    labels: list[str] = []
    if isinstance(value, list):
        for item in value:
            if isinstance(item, dict):
                labels.append(_a9_14_2_1_text(item.get("label"), item.get("fieldName"), item.get("columnName"), item.get("name"), item.get("title"), item.get("fieldKey"), item.get("columnKey")))
            else:
                labels.append(str(item or ""))
    elif isinstance(value, str):
        labels.extend(_a9_14_2_1_split_terms(value))
    return [x for x in labels if str(x or "").strip()]


def _a9_14_2_1_unwrap_prd(value: Any) -> Any:
    try:
        return _unwrap(value)
    except Exception:
        if isinstance(value, dict) and isinstance(value.get("content"), dict):
            return value.get("content")
        return value


def _a9_14_2_1_collect_form_seed_labels(prd: Any, current_page: dict[str, Any], existing_contract: dict[str, Any] | None = None) -> list[str]:
    labels: list[str] = []
    existing_contract = existing_contract if isinstance(existing_contract, dict) else {}
    for key in ["formFields", "tableColumns", "filterFields"]:
        labels.extend(_a9_14_2_1_labels(existing_contract.get(key)))

    source = _a9_14_2_1_unwrap_prd(prd)
    if not isinstance(source, dict):
        source = {}

    pages = []
    for key in ["page_semantic_contracts", "pageSemanticContracts", "page_requirements", "pageRequirements"]:
        value = source.get(key)
        if isinstance(value, list):
            pages.extend([x for x in value if isinstance(x, dict)])

    tokens = _a9_14_2_1_domain_tokens(current_page)

    def same_domain(item: dict[str, Any]) -> bool:
        if not tokens:
            return True
        txt = _a9_14_2_1_norm(" ".join([
            _a9_14_2_1_text(item.get("pageName"), item.get("page_name"), item.get("name"), item.get("title")),
            _a9_14_2_1_text(item.get("pageKey"), item.get("page_key"), item.get("key")),
            _a9_14_2_1_text(item.get("description"), item.get("summary"), item.get("body")),
        ]))
        return any(_a9_14_2_1_norm(t) and _a9_14_2_1_norm(t) in txt for t in tokens)

    for page in pages:
        if not same_domain(page):
            continue
        # Prefer sibling list/detail contract fields to seed create/edit forms.
        if _a9_14_2_1_is_list_like_page_item(page) or not labels:
            for key in ["formFields", "form_fields", "tableColumns", "table_columns", "columns", "displayFields", "fields", "filterFields", "filter_fields"]:
                labels.extend(_a9_14_2_1_labels(page.get(key)))
            body = _a9_14_2_1_text(page.get("description"), page.get("summary"), page.get("body"), page.get("requirement"))
            labels.extend(_a9_14_2_1_extract_between(body, ["结果列表至少展示", "列表至少展示", "至少展示", "字段至少包含"], ["字段", "。", "\n"]))
            labels.extend(_a9_14_2_1_extract_between(body, ["筛选区应支持按", "支持按", "按"], ["进行查询", "查询", "筛选", "。", "\n"]))

    data_reqs = []
    for key in ["data_requirements", "dataRequirements"]:
        value = source.get(key)
        if isinstance(value, list):
            data_reqs.extend([x for x in value if isinstance(x, dict)])
    for item in data_reqs:
        labels.extend(_a9_14_2_1_labels(item.get("important_fields") or item.get("importantFields") or item.get("fields")))

    if not labels:
        current_text = _a9_14_2_1_current_text(current_page)
        if "入库" in current_text:
            labels.extend(["入库单号", "供应商", "仓库", "入库总数量", "备注"])
        elif "出库" in current_text:
            labels.extend(["出库单号", "客户", "仓库", "出库总数量", "备注"])
        else:
            labels.extend(["业务编号", "业务名称", "数量", "备注"])

    return _a9_14_2_1_editable_business_labels(labels)


def _a9_14_2_1_editable_business_labels(labels: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    system_re = re.compile(r"操作|创建人|创建时间|创建日期|更新时间|更新人|审核人|审核时间|审批人|审批时间|经办人|状态|流程|处理进度|关联记录|详情|查看|编辑$|删除$|id$", re.I)
    for raw in labels:
        try:
            label = _semantic_label(raw)
        except Exception:
            label = str(raw or "").strip()
        label = str(label or "").strip(" ，,。；;:：、")
        if not label or system_re.search(label):
            continue
        key = _a9_14_2_1_norm(label)
        if key and key not in seen:
            seen.add(key)
            result.append(label)
    if len(result) < 2:
        for fallback in ["业务编号", "业务名称", "数量", "备注"]:
            key = _a9_14_2_1_norm(fallback)
            if key not in seen:
                seen.add(key)
                result.append(fallback)
    return result[:12]


def _a9_14_2_1_inferred_form_contract(module_prd: Any, current_page: dict[str, Any], existing_contract: dict[str, Any] | None = None) -> dict[str, Any]:
    if not _a9_14_2_1_is_form_like_current_page(current_page):
        return {}
    existing_contract = existing_contract if isinstance(existing_contract, dict) else {}
    page_name = _a9_14_2_1_text(
        current_page.get("module_name"), current_page.get("moduleName"), current_page.get("pageName"), current_page.get("name"),
        existing_contract.get("pageName"), "表单页面",
    )
    page_key = _safe_key(_a9_14_2_1_text(
        current_page.get("module_key"), current_page.get("moduleKey"), current_page.get("pageKey"), current_page.get("key"),
        existing_contract.get("pageKey"), page_name,
    ), "form_page")
    labels = _a9_14_2_1_collect_form_seed_labels(module_prd, current_page, existing_contract)
    fields = [_field(x) for x in labels]
    primary = [_action("保存", "PRIMARY"), _action("取消", "PRIMARY")]
    return {
        "schemaVersion": "1.0",
        "contractType": "PAGE_SEMANTIC_CONTRACT",
        "source": "A9.10.14.4.3.14.2.1_FORM_IDENTITY_WRAPPER_FIX",
        "pageKey": page_key,
        "pageName": page_name,
        "pageType": "FORM",
        "description": existing_contract.get("description") or f"{page_name}用于录入和提交业务单据信息。",
        "filterFields": [],
        "tableColumns": [],
        "formFields": fields,
        "primaryActions": primary,
        "rowActions": [],
        "navigationActions": [],
        "strictness": {
            "removeUnspecifiedFilters": True,
            "removeUnspecifiedTableColumns": True,
            "removeUnspecifiedRowActions": True,
            "failOnMissingExplicitRequirement": True,
            "pageTypeLocked": "FORM",
        },
        "coverageRules": [
            *[{"kind": "FORM_FIELD", "key": x["fieldKey"], "label": x["label"]} for x in fields],
            *[{"kind": "PRIMARY_ACTION", "key": x["actionKey"], "label": x["label"]} for x in primary],
        ],
    }


def build_page_semantic_contract(module_prd: Any, current_page: dict[str, Any] | None = None) -> dict[str, Any]:  # type: ignore[override]
    current_page = current_page or {}
    original = _A9_10_14_4_3_14_2_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT
    contract: dict[str, Any] = {}
    if callable(original):
        try:
            value = original(module_prd, current_page)
            if isinstance(value, dict):
                contract = value
        except Exception:
            contract = {}
    if _a9_14_2_1_is_form_like_current_page(current_page):
        # Even if the original matched a stale LIST/DETAIL contract, FORM identity wins.
        form_contract = _a9_14_2_1_inferred_form_contract(module_prd, current_page, contract)
        if form_contract:
            return form_contract
    return contract
# A9.10.14.4.3.15.3.1_UNIVERSAL_PAGE_REQUIREMENT_CONTRACT_ENRICHER
# Enrich PAGE_SEMANTIC_CONTRACT from structured MODULE_PRD.page_requirements.
# This keeps the engine universal: ecommerce, CRM, WMS, approval, portal,
# dashboard and configuration pages are normalized into the same contract shape.
try:
    _A9_15_3_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT = build_page_semantic_contract
except NameError:  # pragma: no cover
    _A9_15_3_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT = None


def _a91531_text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _a91531_norm_page_type(value: Any, *, page_name: str = "", page_key: str = "", description: str = "") -> str:
    raw = str(value or "").strip().upper().replace("-", "_")
    mapping = {
        "CREATE": "FORM", "ADD": "FORM", "NEW": "FORM", "EDIT": "FORM", "UPDATE": "FORM",
        "FORM_PAGE": "FORM", "CREATE_PAGE": "FORM", "EDIT_PAGE": "FORM",
        "LIST_PAGE": "LIST", "TABLE": "LIST", "TABLE_LIST": "LIST", "QUERY": "LIST", "SEARCH": "LIST",
        "DETAIL_PAGE": "DETAIL", "VIEW": "DETAIL", "VIEW_PAGE": "DETAIL",
        "APPROVAL_PAGE": "APPROVAL", "AUDIT": "APPROVAL", "AUDIT_PAGE": "APPROVAL", "REVIEW": "APPROVAL",
        "SETTING": "CONFIG", "SETTINGS": "CONFIG", "CONFIG_PAGE": "CONFIG",
        "HOME": "PORTAL", "PORTAL_PAGE": "PORTAL", "INDEX": "PORTAL", "WORKBENCH": "DASHBOARD",
        "REPORT": "REPORT", "REPORT_PAGE": "REPORT", "DASHBOARD_PAGE": "DASHBOARD",
    }
    if raw in {"FORM", "LIST", "DETAIL", "APPROVAL", "DASHBOARD", "CONFIG", "PORTAL", "REPORT", "WIZARD", "PROFILE", "LOGIN", "SEARCH"}:
        return raw
    if raw in mapping:
        return mapping[raw]
    text = _norm(" ".join([page_name, page_key, description, str(value or "")]))
    if re.search(r"审核|审批|复核|approve|approval|audit|review", text, re.I):
        return "APPROVAL"
    if re.search(r"创建|新建|新增|录入|登记|编辑|修改|create|add|new|edit|update|form", text, re.I):
        return "FORM"
    if re.search(r"列表|查询|搜索|检索|管理|list|query|search|table", text, re.I):
        return "LIST"
    if re.search(r"详情|明细|查看|detail|view|profile", text, re.I):
        return "DETAIL"
    if re.search(r"看板|仪表盘|工作台|dashboard|workbench|analytics", text, re.I):
        return "DASHBOARD"
    if re.search(r"设置|配置|参数|setting|config", text, re.I):
        return "CONFIG"
    if re.search(r"首页|门户|频道|portal|home|index", text, re.I):
        return "PORTAL"
    return "PAGE"


def _a91531_field_obj(item: Any, index: int = 0) -> dict[str, Any]:
    if isinstance(item, dict):
        label = _a91531_text(item.get("label"), item.get("fieldName"), item.get("field_name"), item.get("name"), item.get("title"), item.get("fieldKey"), item.get("field_key"), f"字段{index + 1}")
        key = _a91531_text(item.get("fieldKey"), item.get("field_key"), item.get("key"), item.get("name"), label)
        typ = _a91531_text(item.get("type"), item.get("fieldType"), item.get("field_type"), item.get("displayType"), item.get("display_type"))
        required = bool(item.get("required", False))
    else:
        label = _semantic_label(item)
        key = label
        typ = ""
        required = False
    label = _semantic_label(label) or f"字段{index + 1}"
    typ = str(typ or _field_type(label)).upper()
    return {
        "fieldKey": _field_key(key or label),
        "label": label,
        "type": typ,
        "required": required,
        "placeholder": ("请选择" if typ in {"SELECT", "DATE", "DATETIME"} else "请输入") + label,
    }


def _a91531_column_obj(item: Any, index: int = 0) -> dict[str, Any]:
    if isinstance(item, dict):
        label = _a91531_text(item.get("label"), item.get("columnName"), item.get("column_name"), item.get("fieldName"), item.get("name"), item.get("title"), item.get("columnKey"), item.get("fieldKey"), f"字段{index + 1}")
        column_key = _a91531_text(item.get("columnKey"), item.get("column_key"), item.get("fieldKey"), item.get("field_key"), item.get("key"), item.get("name"), label)
        field_key = _a91531_text(item.get("fieldKey"), item.get("field_key"), item.get("dataField"), item.get("data_field"), column_key)
        typ = _a91531_text(item.get("type"), item.get("displayType"), item.get("display_type"))
    else:
        label = _semantic_label(item)
        column_key = field_key = label
        typ = ""
    label = _semantic_label(label) or f"字段{index + 1}"
    typ = str(typ or _column_type(label)).upper()
    return {
        "columnKey": _field_key(column_key or label),
        "fieldKey": _field_key(field_key or label),
        "label": label,
        "type": typ,
        "sortable": bool(isinstance(item, dict) and item.get("sortable", False)),
    }


def _a91531_action_obj(item: Any, index: int = 0, scope: str = "PRIMARY") -> dict[str, Any]:
    if isinstance(item, dict):
        label = _a91531_text(item.get("label"), item.get("actionName"), item.get("action_name"), item.get("name"), item.get("title"), item.get("actionKey"), item.get("action_key"), f"操作{index + 1}")
        key = _a91531_text(item.get("actionKey"), item.get("action_key"), item.get("key"), item.get("name"), label)
        kind = _a91531_text(item.get("kind"), item.get("type"), item.get("actionType"), item.get("action_type"))
        target_name = _a91531_text(item.get("targetPageName"), item.get("target_page_name"), item.get("targetPageKey"), item.get("target_page_key"), item.get("target"))
    else:
        label = str(item or f"操作{index + 1}").strip()
        key = label
        kind = ""
        target_name = ""
    label = _semantic_label(label) or f"操作{index + 1}"
    result = {
        "actionKey": _action_key(key or label),
        "label": label,
        "scope": scope,
        "kind": str(kind or _action_kind(label)).upper(),
    }
    if target_name:
        result["navigationIntent"] = True
        result["targetPageName"] = target_name
    return result


def _a91531_list_items(value: Any) -> list[Any]:
    if isinstance(value, list):
        return [item for item in value if item not in (None, "")]
    if isinstance(value, str) and value.strip():
        return _split(value)
    return []


def _a91531_extract_structured_page_requirement(module_prd: Any, current_page: dict[str, Any]) -> dict[str, Any]:
    prd = _unwrap(module_prd)
    if isinstance(prd, dict) and backfill_module_prd_semantic_backbone:
        try:
            prd = backfill_module_prd_semantic_backbone(prd, current_page=current_page)
        except Exception:
            pass
    if not isinstance(prd, dict):
        return {}
    items = []
    for key in ["page_requirements", "pageRequirements", "page_semantic_contracts", "pageSemanticContracts"]:
        value = prd.get(key)
        if isinstance(value, list):
            items.extend(value)
    return _match_page(items, current_page) if items else {}


def _a91531_merge_structured_fields(contract: dict[str, Any], page_req: dict[str, Any]) -> dict[str, Any]:
    merged = dict(contract or {})
    desc = _a91531_text(_first(page_req, "description", "summary", "body", "requirement"), merged.get("description"))
    page_name = _a91531_text(_first(page_req, "pageName", "page_name", "name", "title"), merged.get("pageName"))
    page_key = _safe_key(_a91531_text(_first(page_req, "pageKey", "page_key", "key", "pageId", "id"), merged.get("pageKey"), page_name), "page")
    explicit_type = _first(page_req, "pageType", "page_type", "type", "layoutType", "layout_type")
    page_type = _a91531_norm_page_type(explicit_type or merged.get("pageType"), page_name=page_name, page_key=page_key, description=desc)
    merged.update({
        "schemaVersion": "1.0",
        "contractType": "PAGE_SEMANTIC_CONTRACT",
        "source": page_req.get("source") or merged.get("source") or "MODULE_PRD.page_requirements",
        "pageKey": page_key,
        "pageName": page_name,
        "pageType": page_type,
        "description": desc,
    })
    route = _a91531_text(_first(page_req, "routePath", "route_path", "route", "path"), merged.get("routePath"), merged.get("route"))
    if route:
        merged["routePath"] = route
    layout = _a91531_text(_first(page_req, "layoutType", "layout_type", "layout"), merged.get("layoutType"))
    if layout:
        merged["layoutType"] = layout

    fields = _a91531_list_items(_first(page_req, "filterFields", "filter_fields", "filters", "queryFields", "query_fields"))
    columns = _a91531_list_items(_first(page_req, "tableColumns", "table_columns", "columns", "displayFields", "display_fields"))
    form_fields = _a91531_list_items(_first(page_req, "formFields", "form_fields", "inputFields", "input_fields", "fields"))
    primary = _a91531_list_items(_first(page_req, "primaryActions", "primary_actions", "toolbarActions", "toolbar_actions", "actions", "userActions", "user_actions"))
    row = _a91531_list_items(_first(page_req, "rowActions", "row_actions"))

    if fields:
        merged["filterFields"] = [_a91531_field_obj(item, idx) for idx, item in enumerate(fields)]
    if columns:
        merged["tableColumns"] = [_a91531_column_obj(item, idx) for idx, item in enumerate(columns)]
    if form_fields:
        merged["formFields"] = [_a91531_field_obj(item, idx) for idx, item in enumerate(form_fields)]
    if primary:
        merged["primaryActions"] = [_a91531_action_obj(item, idx, "PRIMARY") for idx, item in enumerate(primary)]
    if row:
        merged["rowActions"] = [_a91531_action_obj(item, idx, "ROW") for idx, item in enumerate(row)]

    nav_targets = _a91531_list_items(_first(page_req, "navigationTargets", "navigation_targets", "navigation", "links"))
    if nav_targets:
        merged["navigationTargets"] = nav_targets
        navigation_actions: list[dict[str, str]] = []
        for idx, item in enumerate(nav_targets):
            if isinstance(item, dict):
                label = _a91531_text(item.get("label"), item.get("actionLabel"), item.get("action_label"), item.get("name"), item.get("title"), f"跳转{idx + 1}")
                target = _a91531_text(item.get("targetPageName"), item.get("target_page_name"), item.get("pageName"), item.get("page_name"), item.get("targetPageKey"), item.get("target_page_key"), item.get("pageKey"), item.get("page_key"))
                if label or target:
                    navigation_actions.append({"label": label or f"跳转{idx + 1}", "targetPageName": target})
            elif str(item or "").strip():
                navigation_actions.append({"label": str(item).strip(), "targetPageName": str(item).strip()})
        if navigation_actions:
            merged["navigationActions"] = navigation_actions

    coverage = []
    for field in merged.get("formFields") or []:
        if isinstance(field, dict):
            coverage.append({"kind": "FORM_FIELD", "key": field.get("fieldKey"), "label": field.get("label")})
    for field in merged.get("filterFields") or []:
        if isinstance(field, dict):
            coverage.append({"kind": "FILTER_FIELD", "key": field.get("fieldKey"), "label": field.get("label")})
    for column in merged.get("tableColumns") or []:
        if isinstance(column, dict):
            coverage.append({"kind": "TABLE_COLUMN", "key": column.get("columnKey"), "label": column.get("label")})
    for action in (merged.get("primaryActions") or []) + (merged.get("rowActions") or []):
        if isinstance(action, dict):
            coverage.append({"kind": "ACTION", "key": action.get("actionKey"), "label": action.get("label")})
    if coverage:
        merged["coverageRules"] = coverage
    strictness = merged.get("strictness") if isinstance(merged.get("strictness"), dict) else {}
    strictness.update({"pageTypeLocked": page_type, "requireDeclaredFieldsAndActions": True})
    merged["strictness"] = strictness
    return merged


def build_page_semantic_contract(module_prd: Any, current_page: dict[str, Any] | None = None) -> dict[str, Any]:  # type: ignore[override]
    current_page = current_page or {}
    original = _A9_15_3_1_ORIGINAL_BUILD_PAGE_SEMANTIC_CONTRACT
    base: dict[str, Any] = {}
    if callable(original):
        try:
            value = original(module_prd, current_page)
            if isinstance(value, dict):
                base = value
        except Exception:
            base = {}
    page_req = _a91531_extract_structured_page_requirement(module_prd, current_page)
    if isinstance(page_req, dict) and page_req:
        return _a91531_merge_structured_fields(base, page_req)
    return base

