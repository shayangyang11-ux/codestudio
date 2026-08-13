# A9.10.14.4.2.2_EXISTING_PRD_SEMANTIC_BACKFILL
# A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN

from __future__ import annotations
import re

from typing import Any


def backfill_module_prd_semantic_backbone(
    prd: Any,
    *,
    current_page: dict[str, Any] | None = None,
) -> Any:
    """Return a MODULE_PRD dict with a hidden semantic backbone.

    The user-facing markdown remains unchanged.  This function is intentionally
    deterministic so existing PRDs generated before A9.10.14.4.2 can be consumed
    by PAGE_SPEC / UI_CONTRACT / Development Gate without forcing DeepSeek
    regeneration.
    """
    if not isinstance(prd, dict):
        return prd

    # A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN
    # ContextPackageService passes artifact "cards" with content/content_markdown.
    # PAGE_SPEC/UI_CONTRACT need the actual PRD payload, not only artifact metadata.
    source = _unwrap_prd_payload(prd)
    result: dict[str, Any] = dict(source if isinstance(source, dict) else prd)

    page_requirements = _as_list(_first(result, "page_requirements", "pageRequirements"))
    if not page_requirements:
        page_requirements = _derive_page_requirements_from_markdown(
            _text(_first(result, "markdown", "contentMarkdown", "content_markdown")),
            current_page or {},
        )
        if page_requirements:
            result["page_requirements"] = page_requirements
            result["pageRequirements"] = page_requirements

    functional_requirements = _as_list(_first(result, "functional_requirements", "functionalRequirements"))
    if not functional_requirements:
        functional_requirements = _derive_functional_requirements_from_markdown(
            _text(_first(result, "markdown", "contentMarkdown", "content_markdown")),
        )
        if functional_requirements:
            result["functional_requirements"] = functional_requirements
            result["functionalRequirements"] = functional_requirements
    business_rules = _as_list(_first(result, "business_rules", "businessRules"))
    state_rules = _as_list(_first(result, "state_rules", "stateRules"))
    data_requirements = _as_list(_first(result, "data_requirements", "dataRequirements"))
    api_requirements = _as_list(_first(result, "api_requirements", "apiRequirements"))
    acceptance_criteria = _as_list(_first(result, "acceptance_criteria", "acceptanceCriteria"))

    # A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
    custom_sections = _as_list(_first(result, "custom_sections", "customSections"))
    if not custom_sections:
        custom_sections = _derive_custom_sections_from_markdown(
            _text(_first(result, "markdown", "contentMarkdown", "content_markdown")),
        )
        if custom_sections:
            result["custom_sections"] = custom_sections
            result["customSections"] = custom_sections

    structured = _as_list(_first(result, "structured_requirements", "structuredRequirements"))
    if not structured:
        structured = _build_structured_requirements(
            functional_requirements=functional_requirements,
            page_requirements=page_requirements,
            business_rules=business_rules,
            state_rules=state_rules,
            data_requirements=data_requirements,
            api_requirements=api_requirements,
            acceptance_criteria=acceptance_criteria,
        )

    navigation = _as_list(_first(result, "navigation_requirements", "navigationRequirements"))
    if not navigation:
        navigation = _infer_navigation_requirements(
            page_requirements=page_requirements,
            current_page=current_page or {},
        )

    traceability = _first(result, "traceability_map", "traceabilityMap")
    if not isinstance(traceability, dict):
        traceability = _build_traceability_map(
            structured_requirements=structured,
            page_requirements=page_requirements,
            navigation_requirements=navigation,
        )

    public_document = _first(result, "public_document", "publicDocument")
    if not isinstance(public_document, dict):
        public_document = _build_public_document(result)

    semantic_sync_policy = _first(result, "semantic_sync_policy", "semanticSyncPolicy")
    if not isinstance(semantic_sync_policy, dict):
        semantic_sync_policy = {
            "mode": "DUAL_LAYER_PRD",
            "userVisibleSource": "markdown",
            "hiddenSemanticSource": "structuredRequirements",
            "prototypeToPrdSync": {
                "fields": "PROPOSE_REQUIREMENT_SYNC",
                "actions": "PROPOSE_REQUIREMENT_SYNC",
                "tableColumns": "PROPOSE_REQUIREMENT_SYNC",
                "navigation": "PROPOSE_REQUIREMENT_SYNC_AND_NAVIGATION_GRAPH_SYNC",
                "visualOnly": "UI_CONTRACT_ONLY",
            },
            "prdToPrototypeSync": {
                "pageRequirementChanged": ["PAGE_SPEC", "UI_CONTRACT", "MODULE_APP_CONTRACT"],
                "functionalRequirementChanged": ["PAGE_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
                "businessRuleChanged": ["PAGE_SPEC", "UI_CONTRACT", "TEST_SPEC", "DEV_HANDOFF_PACKAGE"],
                "navigationRequirementChanged": ["MODULE_APP_CONTRACT", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE"],
            },
            "developmentGate": {
                "requireConsistentUpstreamVersions": True,
                "blockWhenPrdNewerThanPrototype": True,
            },
        }

    source_outline = _first(result, "source_outline", "sourceOutline")
    if not isinstance(source_outline, dict):
        source_outline = {
            "generatedBy": "A9.10.14.4.2.2_EXISTING_PRD_SEMANTIC_BACKFILL",
            "sourceShape": "LEGACY_MODULE_PRD" if not _first(result, "structured_requirements", "structuredRequirements") else "STRUCTURED_MODULE_PRD",
            "counts": {
                "functionalRequirements": len(functional_requirements),
                "pageRequirements": len(page_requirements),
                "businessRules": len(business_rules),
                "stateRules": len(state_rules),
                "dataRequirements": len(data_requirements),
                "apiRequirements": len(api_requirements),
                "structuredRequirements": len(structured),
                "navigationRequirements": len(navigation),
                "customSections": len(custom_sections),
            },
        }

    # Keep both snake_case and camelCase for compatibility with Java, frontend,
    # prompts, and older Python context builders.
    result["public_document"] = public_document
    result["publicDocument"] = public_document
    result["structured_requirements"] = structured
    result["structuredRequirements"] = structured
    result["navigation_requirements"] = navigation
    result["navigationRequirements"] = navigation
    result["custom_sections"] = custom_sections
    result["customSections"] = custom_sections
    result["traceability_map"] = traceability
    result["traceabilityMap"] = traceability
    result["semantic_sync_policy"] = semantic_sync_policy
    result["semanticSyncPolicy"] = semantic_sync_policy
    result["source_outline"] = source_outline
    result["sourceOutline"] = source_outline
    return result



# A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
_STANDARD_USER_PRD_SECTION_TITLES = {
    "模块概述",
    "模块基本信息",
    "模块基础信息",
    "业务背景",
    "用户角色",
    "功能需求",
    "功能点需求",
    "业务规则",
    "关键业务规则",
    "状态与流转",
    "状态规则",
    "状态与流转规则",
    "页面需求",
    "页面与操作设计",
    "页面需求与原型契约",
    "数据需求",
    "接口需求",
    "权限需求",
    "权限与操作边界",
    "异常场景",
    "验收标准",
    "验收要点",
    "非功能需求",
    "开发注意事项",
    "测试注意事项",
    "后续工程资产生成提示",
    "分析摘要",
}


def _derive_custom_sections_from_markdown(markdown: str) -> list[dict[str, Any]]:
    """Keep legacy/uploaded PRD free text instead of forcing it into structured contracts."""
    if not markdown or not markdown.strip():
        return []

    sections: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None

    def normalized_title(value: str) -> str:
        text = re.sub(r"^\s*\d+(?:\.\d+)*\s*[\.、\)]?\s*", "", value or "").strip()
        return text

    def flush() -> None:
        nonlocal current
        if not current:
            return
        title = normalized_title(str(current.get("title") or ""))
        body = "\n".join(current.get("body") or []).strip()
        title_key = _normalize_text(title)
        standard_keys = {_normalize_text(item) for item in _STANDARD_USER_PRD_SECTION_TITLES}
        if title and body and title_key not in standard_keys:
            sections.append({
                "section_id": _safe_key(title, f"custom_section_{len(sections) + 1}"),
                "title": title,
                "body": body,
                "order": len(sections) + 1,
                "visibility": "USER_VISIBLE",
                "source": "LEGACY_OR_UPLOADED_PRD",
                "impact_policy": "DOCUMENT_ONLY",
            })
        current = None

    heading_re = re.compile(r"^\s*#{2,4}\s+(.+?)\s*$")
    for raw_line in markdown.splitlines():
        match = heading_re.match(raw_line)
        if match:
            flush()
            current = {"title": match.group(1).strip(), "body": []}
            continue
        if current is not None:
            current["body"].append(raw_line)

    flush()
    return sections

def _first(source: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in source and source.get(key) not in (None, ""):
            return source.get(key)
    return None


def _as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _unwrap_prd_payload(prd: dict[str, Any]) -> dict[str, Any]:
    content = prd.get("content")
    if isinstance(content, dict):
        result: dict[str, Any] = dict(content)
        markdown = _text(
            result.get("markdown"),
            result.get("contentMarkdown"),
            result.get("content_markdown"),
            prd.get("contentMarkdown"),
            prd.get("content_markdown"),
        )
        if markdown:
            result.setdefault("markdown", markdown)
            result.setdefault("contentMarkdown", markdown)
            result.setdefault("content_markdown", markdown)

        for source_key, target_key in [
            ("artifact_id", "sourceArtifactId"),
            ("artifactId", "sourceArtifactId"),
            ("version_no", "sourceVersionNo"),
            ("versionNo", "sourceVersionNo"),
            ("status", "sourceArtifactStatus"),
            ("artifact_type", "sourceArtifactType"),
            ("artifactType", "sourceArtifactType"),
        ]:
            if source_key in prd and target_key not in result:
                result[target_key] = prd.get(source_key)
        return result

    return prd


def _derive_page_requirements_from_markdown(
    markdown: str,
    current_page: dict[str, Any],
) -> list[dict[str, Any]]:
    sections = _extract_numbered_sections(markdown, "8")
    if not sections:
        return []

    current_name = _text(
        current_page.get("pageName"),
        current_page.get("page_name"),
        current_page.get("module_name"),
        current_page.get("moduleName"),
        current_page.get("name"),
    )
    current_id = _text(
        current_page.get("pageId"),
        current_page.get("page_id"),
        current_page.get("module_id"),
        current_page.get("moduleId"),
        current_page.get("id"),
    )
    current_key = _text(
        current_page.get("pageKey"),
        current_page.get("page_key"),
        current_page.get("module_key"),
        current_page.get("moduleKey"),
        current_page.get("key"),
    )

    result: list[dict[str, Any]] = []
    for index, section in enumerate(sections):
        title = section["title"]
        body = section["body"]
        name_match_current = (
            bool(current_name)
            and (_normalize_text(current_name) in _normalize_text(title)
                 or _normalize_text(title) in _normalize_text(current_name))
        )

        page_key = current_key if name_match_current and current_key else _safe_key(title, f"page_{index + 1}")
        page_id = current_id if name_match_current and current_id else page_key
        fields = _extract_terms_after_keywords(
            body,
            ["支持按", "筛选区应支持按", "按"],
            ["进行查询", "查询", "筛选"],
        )
        columns = _extract_terms_after_keywords(
            body,
            ["至少展示", "结果列表至少展示", "列表字段至少包含", "字段至少包含"],
            ["字段", "。", "\n"],
        )
        actions = _unique_text(re.findall(r"[“\"]([^”\"]+)[”\"]", body))
        components: list[str] = []
        if "筛选" in body or fields:
            components.append("筛选区")
        if "结果列表" in body or "数据列表" in body or "表格" in body or columns:
            components.append("结果列表")
        if "行操作" in body or actions:
            components.append("行操作区")
        if "状态" in body:
            components.append("状态展示")

        result.append({
            "pageId": page_id,
            "pageKey": page_key,
            "pageName": title,
            "description": body or title,
            "summary": body or title,
            "mainComponents": components,
            "main_components": components,
            "userActions": actions,
            "user_actions": actions,
            "fields": fields,
            "filterFields": fields,
            "filter_fields": fields,
            "tableColumns": columns,
            "table_columns": columns,
            "source": "MARKDOWN_SECTION_8_PAGE_PLANNING",
        })

    return result


def _derive_functional_requirements_from_markdown(markdown: str) -> list[dict[str, Any]]:
    sections = _extract_numbered_sections(markdown, "7")
    result: list[dict[str, Any]] = []
    for index, section in enumerate(sections):
        title = section["title"]
        body = section["body"]
        result.append({
            "featureKey": _safe_key(title, f"feature_{index + 1}"),
            "featureName": title,
            "title": title,
            "description": body or title,
            "source": "MARKDOWN_SECTION_7_FEATURE_REQUIREMENTS",
        })
    return result


def _extract_numbered_sections(markdown: str, major: str) -> list[dict[str, str]]:
    if not markdown or not markdown.strip():
        return []

    sections: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None
    heading_re = re.compile(rf"^\s*(?:#{{1,6}}\s*)?{re.escape(major)}\.(\d+)\s+(.+?)\s*$")
    any_heading_re = re.compile(r"^\s*(?:#{1,6}\s*)?\d+(?:\.\d+)?\s+.+?\s*$")

    for raw_line in markdown.splitlines():
        line = raw_line.strip()
        match = heading_re.match(line)
        if match:
            if current:
                sections.append({
                    "title": current["title"],
                    "body": "\n".join(current["body"]).strip(),
                })
            current = {"title": match.group(2).strip(), "body": []}
            continue

        if current and any_heading_re.match(line) and not heading_re.match(line):
            sections.append({
                "title": current["title"],
                "body": "\n".join(current["body"]).strip(),
            })
            current = None
            continue

        if current is not None:
            current["body"].append(raw_line)

    if current:
        sections.append({
            "title": current["title"],
            "body": "\n".join(current["body"]).strip(),
        })

    return sections


def _extract_terms_after_keywords(
    text: str,
    start_keywords: list[str],
    end_keywords: list[str],
) -> list[str]:
    if not text:
        return []

    candidates: list[str] = []
    for start in start_keywords:
        start_index = text.find(start)
        if start_index < 0:
            continue
        fragment = text[start_index + len(start):]
        end_index = len(fragment)
        for end in end_keywords:
            candidate_index = fragment.find(end)
            if candidate_index >= 0:
                end_index = min(end_index, candidate_index)
        candidates.extend(_split_terms(fragment[:end_index]))

    return _unique_text(candidates)


def _split_terms(value: str) -> list[str]:
    cleaned = re.sub(r"[。；;:：]", "、", value or "")
    return [
        item.strip(" 、,，.。\n\t")
        for item in re.split(r"[、,，/和及]+", cleaned)
        if item.strip(" 、,，.。\n\t")
    ]


def _unique_text(items: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        text = str(item or "").strip()
        if not text:
            continue
        key = _normalize_text(text)
        if key in seen:
            continue
        seen.add(key)
        result.append(text)
    return result


def _text(*values: Any) -> str:
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _safe_key(value: Any, fallback: str) -> str:
    text = str(value or "").strip().lower()
    text = re.sub(r"[^a-z0-9_\-\u4e00-\u9fff]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or fallback


def _normalize_text(value: Any) -> str:
    return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())


def _item_text(item: Any, *keys: str) -> str:
    if not isinstance(item, dict):
        return ""
    return _text(*(item.get(key) for key in keys))


def _page_key(item: dict[str, Any], index: int) -> str:
    return _safe_key(
        _text(
            item.get("page_key"),
            item.get("pageKey"),
            item.get("key"),
            item.get("page_id"),
            item.get("pageId"),
            item.get("id"),
            item.get("page_name"),
            item.get("pageName"),
            item.get("name"),
        ),
        f"page_{index + 1}",
    )


def _page_name(item: dict[str, Any], index: int) -> str:
    return _text(
        item.get("page_name"),
        item.get("pageName"),
        item.get("name"),
        item.get("title"),
        _page_key(item, index),
    )


def _page_id(item: dict[str, Any], index: int) -> str:
    return _text(
        item.get("page_id"),
        item.get("pageId"),
        item.get("targetPageId"),
        item.get("id"),
        _page_key(item, index),
    )


def _build_structured_requirements(
    *,
    functional_requirements: list[Any],
    page_requirements: list[Any],
    business_rules: list[Any],
    state_rules: list[Any],
    data_requirements: list[Any],
    api_requirements: list[Any],
    acceptance_criteria: list[Any],
) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []

    def add(item: Any, index: int, kind: str, key_candidates: list[str], title_candidates: list[str], description_candidates: list[str], extra: dict[str, Any] | None = None) -> None:
        if not isinstance(item, dict):
            return
        key = _safe_key(_text(*(item.get(key) for key in key_candidates)), f"{kind.lower()}_{index + 1}")
        title = _text(*(item.get(key) for key in title_candidates), key)
        description = _text(*(item.get(key) for key in description_candidates), title)
        result.append({
            "requirementId": f"req_{kind.lower()}_{key}",
            "requirementKey": key,
            "title": title,
            "type": kind,
            "scope": "MODULE",
            "description": description,
            "relatedPageIds": _normalize_str_list(item.get("relatedPageIds") or item.get("related_page_ids")),
            "relatedPageKeys": _normalize_str_list(item.get("relatedPageKeys") or item.get("related_page_keys")),
            "relatedFeatureIds": _normalize_str_list(item.get("relatedFeatureIds") or item.get("related_feature_ids")),
            "relatedFieldKeys": _normalize_str_list(item.get("relatedFieldKeys") or item.get("related_field_keys")),
            "relatedActionIds": _normalize_str_list(item.get("relatedActionIds") or item.get("related_action_ids")),
            "businessRules": _normalize_str_list(item.get("business_rules") or item.get("businessRules")),
            "acceptanceCriteria": _normalize_str_list(item.get("acceptance_criteria") or item.get("acceptanceCriteria")),
            "status": "ACTIVE",
            "source": {
                "artifactType": "MODULE_PRD",
                "legacySection": kind,
                "sourceKey": key,
            },
            **(extra or {}),
        })

    for i, item in enumerate(functional_requirements):
        add(
            item,
            i,
            "FEATURE",
            ["requirement_key", "requirementKey", "feature_key", "featureKey", "key"],
            ["title", "requirement_name", "requirementName", "feature_name", "featureName", "name"],
            ["description", "summary", "input_description", "inputDescription", "output_description", "outputDescription"],
        )

    for i, item in enumerate(page_requirements):
        if not isinstance(item, dict):
            continue
        page_id = _page_id(item, i)
        page_key = _page_key(item, i)
        add(
            item,
            i,
            "PAGE",
            ["page_key", "pageKey", "key", "page_id", "pageId", "id"],
            ["page_name", "pageName", "name", "title"],
            ["description", "summary", "page_goal", "pageGoal"],
            {
                "relatedPageIds": [page_id] if page_id else [],
                "relatedPageKeys": [page_key] if page_key else [],
                "mainComponents": _normalize_str_list(item.get("main_components") or item.get("mainComponents")),
                "userActions": _normalize_str_list(item.get("user_actions") or item.get("userActions")),
            },
        )

    for i, item in enumerate(business_rules):
        add(
            item,
            i,
            "BUSINESS_RULE",
            ["rule_key", "ruleKey", "key"],
            ["title", "rule_name", "ruleName", "name"],
            ["description", "trigger_condition", "triggerCondition", "expected_result", "expectedResult"],
        )

    for i, item in enumerate(state_rules):
        add(
            item,
            i,
            "STATE_RULE",
            ["state_key", "stateKey", "key"],
            ["state_name", "stateName", "name", "title"],
            ["description"],
        )

    for i, item in enumerate(data_requirements):
        add(
            item,
            i,
            "DATA_REQUIREMENT",
            ["entity_key", "entityKey", "key"],
            ["entity_name", "entityName", "name", "title"],
            ["description"],
            {
                "relatedFieldKeys": _normalize_str_list(item.get("important_fields") or item.get("importantFields")),
            },
        )

    for i, item in enumerate(api_requirements):
        add(
            item,
            i,
            "API_REQUIREMENT",
            ["api_key", "apiKey", "key"],
            ["api_name", "apiName", "name", "title"],
            ["description", "request_summary", "requestSummary", "response_summary", "responseSummary"],
        )

    for i, item in enumerate(acceptance_criteria):
        add(
            item,
            i,
            "ACCEPTANCE_CRITERION",
            ["criterion_key", "criterionKey", "key"],
            ["title", "criterion_name", "criterionName", "name"],
            ["description", "verification_method", "verificationMethod"],
        )

    return result


def _infer_navigation_requirements(
    *,
    page_requirements: list[Any],
    current_page: dict[str, Any],
) -> list[dict[str, Any]]:
    pages: list[dict[str, Any]] = []
    for index, item in enumerate(page_requirements):
        if not isinstance(item, dict):
            continue
        key = _page_key(item, index)
        name = _page_name(item, index)
        page_id = _page_id(item, index)
        route = _text(item.get("route"), item.get("routePath"), item.get("route_path"), f"/{key}")
        actions = _normalize_str_list(item.get("user_actions") or item.get("userActions") or item.get("actions"))
        pages.append({
            "pageId": page_id,
            "pageKey": key,
            "pageName": name,
            "route": route,
            "actions": actions,
            "raw": item,
            "kind": _infer_page_kind(key, name, item),
        })

    if len(pages) < 2:
        return []

    result: list[dict[str, Any]] = []
    seen: set[str] = set()

    def add_edge(source: dict[str, Any], target: dict[str, Any], action_key: str, label: str, reason: str) -> None:
        if not source or not target:
            return
        if source.get("pageId") == target.get("pageId"):
            return
        edge_id = f"nav_{source.get('pageKey')}_{action_key}_to_{target.get('pageKey')}"
        if edge_id in seen:
            return
        seen.add(edge_id)
        result.append({
            "navigationId": edge_id,
            "sourcePageId": source.get("pageId"),
            "sourcePageKey": source.get("pageKey"),
            "sourcePageName": source.get("pageName"),
            "sourceActionKey": action_key,
            "sourceActionLabel": label,
            "targetPageId": target.get("pageId"),
            "targetPageKey": target.get("pageKey"),
            "targetPageName": target.get("pageName"),
            "targetRoute": target.get("route"),
            "route": target.get("route"),
            "scope": "INTRA_MODULE",
            "status": "PLANNED",
            "source": "MODULE_PRD_BACKFILL",
            "reason": reason,
        })

    list_pages = [p for p in pages if p.get("kind") == "LIST"]
    detail_pages = [p for p in pages if p.get("kind") == "DETAIL"]
    create_pages = [p for p in pages if p.get("kind") == "CREATE"]
    edit_pages = [p for p in pages if p.get("kind") == "EDIT"]

    for source in list_pages:
        for target in detail_pages:
            add_edge(source, target, "view_detail", "查看详情", "列表页通常通过行操作进入详情页。")
        for target in create_pages:
            add_edge(source, target, "create", "新增", "列表页通常提供新增入口。")

    for source in detail_pages:
        for target in edit_pages:
            add_edge(source, target, "edit", "编辑", "详情页通常提供编辑入口。")
        for target in list_pages[:1]:
            add_edge(source, target, "back_to_list", "返回列表", "详情页通常可返回列表页。")

    for source in create_pages + edit_pages:
        for target in list_pages[:1]:
            add_edge(source, target, "submit_success", "提交成功返回列表", "表单提交成功后通常返回列表页或刷新列表。")

    # Explicit text-driven edges from user_actions: if an action mentions another
    # page name/key, keep that planned relationship.
    for source in pages:
        for action_index, action_text in enumerate(source.get("actions") or []):
            norm_action = _normalize_text(action_text)
            if not norm_action:
                continue
            for target in pages:
                if target.get("pageId") == source.get("pageId"):
                    continue
                target_terms = [target.get("pageKey"), target.get("pageName")]
                if any(term and _normalize_text(term) in norm_action for term in target_terms):
                    add_edge(
                        source,
                        target,
                        _safe_key(action_text, f"action_{action_index + 1}"),
                        str(action_text),
                        "PRD user_actions 文案显式提到了目标页面。",
                    )

    return result


def _infer_page_kind(key: str, name: str, item: dict[str, Any]) -> str:
    text = _normalize_text(" ".join([
        key,
        name,
        str(item.get("description") or ""),
        " ".join(_normalize_str_list(item.get("user_actions") or item.get("userActions"))),
    ]))
    if any(token in text for token in ["列表", "查询", "搜索", "list", "table", "search"]):
        return "LIST"
    if any(token in text for token in ["详情", "detail", "查看"]):
        return "DETAIL"
    if any(token in text for token in ["创建", "新建", "新增", "录入", "create", "add"]):
        return "CREATE"
    if any(token in text for token in ["编辑", "修改", "edit", "update"]):
        return "EDIT"
    return "PAGE"


def _build_traceability_map(
    *,
    structured_requirements: list[dict[str, Any]],
    page_requirements: list[Any],
    navigation_requirements: list[dict[str, Any]],
) -> dict[str, Any]:
    requirement_to_pages: list[dict[str, Any]] = []
    requirement_to_actions: list[dict[str, Any]] = []
    requirement_to_navigation: list[dict[str, Any]] = []

    for requirement in structured_requirements:
        if not isinstance(requirement, dict):
            continue
        req_id = str(requirement.get("requirementId") or "").strip()
        if not req_id:
            continue
        for page_id in _normalize_str_list(requirement.get("relatedPageIds")):
            requirement_to_pages.append({"requirementId": req_id, "pageId": page_id, "source": "structuredRequirements.relatedPageIds"})
        for action_id in _normalize_str_list(requirement.get("relatedActionIds")):
            requirement_to_actions.append({"requirementId": req_id, "actionId": action_id, "source": "structuredRequirements.relatedActionIds"})

    for index, item in enumerate(page_requirements):
        if not isinstance(item, dict):
            continue
        page_id = _page_id(item, index)
        page_key = _page_key(item, index)
        page_req_id = f"req_page_{page_key}"
        if page_id:
            requirement_to_pages.append({"requirementId": page_req_id, "pageId": page_id, "pageKey": page_key, "source": "MODULE_PRD.page_requirements"})
        for action_index, action_text in enumerate(_normalize_str_list(item.get("user_actions") or item.get("userActions"))):
            requirement_to_actions.append({
                "requirementId": page_req_id,
                "actionKey": _safe_key(action_text, f"action_{action_index + 1}"),
                "actionLabel": action_text,
                "source": "MODULE_PRD.page_requirements.user_actions",
            })

    for nav in navigation_requirements:
        nav_id = str(nav.get("navigationId") or "").strip()
        source_key = _safe_key(nav.get("sourcePageKey") or nav.get("sourcePageId"), "source")
        if nav_id:
            requirement_to_navigation.append({
                "requirementId": f"req_navigation_{source_key}",
                "navigationId": nav_id,
                "sourcePageId": nav.get("sourcePageId"),
                "targetPageId": nav.get("targetPageId"),
                "source": "navigationRequirements",
            })

    return {
        "requirementToPages": requirement_to_pages,
        "requirementToFields": [],
        "requirementToActions": requirement_to_actions,
        "requirementToNavigation": requirement_to_navigation,
        "syncStatus": "BACKFILLED",
        "generatedBy": "A9.10.14.4.2.2_EXISTING_PRD_SEMANTIC_BACKFILL",
    }


def _build_public_document(prd: dict[str, Any]) -> dict[str, Any]:
    info = prd.get("module_basic_info") or prd.get("moduleBasicInfo") or {}
    if not isinstance(info, dict):
        info = {}
    title = _text(
        prd.get("title"),
        info.get("module_name"),
        info.get("moduleName"),
        info.get("name"),
        "模块 PRD",
    )
    markdown = _text(prd.get("markdown"), prd.get("contentMarkdown"), prd.get("content_markdown"))
    return {
        "title": title,
        "markdown": markdown,
        "customSections": _as_list(_first(prd, "custom_sections", "customSections")),
        "audience": "USER_VISIBLE",
        "readability": "HIGH",
    }


def _normalize_str_list(value: Any) -> list[str]:
    if isinstance(value, list):
        result: list[str] = []
        for item in value:
            text = str(item or "").strip()
            if text and text not in result:
                result.append(text)
        return result
    if isinstance(value, str) and value.strip():
        parts = re.split(r"[；;、,，\n]+", value)
        return [part.strip() for part in parts if part.strip()]
    return []
