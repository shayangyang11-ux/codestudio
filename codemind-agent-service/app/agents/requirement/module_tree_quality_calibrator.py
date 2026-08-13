from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from app.schemas.module_tree import FlatModuleNode


@dataclass
class ModuleTreeQualityCalibrationResult:
    nodes: list[FlatModuleNode]
    notes: list[str] = field(default_factory=list)


class ModuleTreeQualityCalibrator:
    """
    Defensive post-LLM calibrator for module tree generation.

    User-facing policy stays simple, but the internal tree must be stable:
    - progressive / organize modes expose only MODULE hierarchy;
    - full blueprint uses MODULE -> FEATURE -> PAGE;
    - FEATURE is a business capability, never a page component or field;
    - PAGE is the only node that can generate UI_CONTRACT;
    - invalid parent references and legacy order mistakes are repaired locally.
    """

    MODULE_TYPES = {"MODULE", "DOMAIN", "SUBMODULE"}
    VALID_TYPES = {"MODULE", "FEATURE", "PAGE"}

    def calibrate(
        self,
        nodes: list[FlatModuleNode],
        *,
        modeling_mode: str,
        requirement_text: str = "",
        generation_plan: Any | None = None,
    ) -> ModuleTreeQualityCalibrationResult:
        mode = str(modeling_mode or "").strip().upper()
        plan_mode = self._extract_plan_mode(generation_plan)
        if plan_mode:
            mode = plan_mode

        notes: list[str] = []
        normalized = self._normalize_unique_keys(nodes, notes)

        if mode in {"PROGRESSIVE_MODULE_OUTLINE", "MODULE_OUTLINE", "ORGANIZE_THEN_GENERATE", "SKELETON"}:
            calibrated = self._calibrate_module_only(normalized, notes)
        else:
            calibrated = self._calibrate_full_blueprint(normalized, notes, requirement_text)

        calibrated = self._drop_invalid_parent_references(calibrated, notes)
        calibrated = self._sort_nodes(calibrated)
        return ModuleTreeQualityCalibrationResult(nodes=calibrated, notes=self._dedupe(notes))

    def _extract_plan_mode(self, generation_plan: Any | None) -> str:
        if generation_plan is None:
            return ""
        raw: Any = None
        if isinstance(generation_plan, dict):
            raw = (
                generation_plan.get("actualModelingMode")
                or generation_plan.get("actual_modeling_mode")
                or generation_plan.get("modelingMode")
                or generation_plan.get("modeling_mode")
            )
        else:
            raw = (
                getattr(generation_plan, "actualModelingMode", None)
                or getattr(generation_plan, "actual_modeling_mode", None)
                or getattr(generation_plan, "modelingMode", None)
                or getattr(generation_plan, "modeling_mode", None)
            )
        return str(raw or "").strip().upper()

    def _normalize_unique_keys(self, nodes: list[FlatModuleNode], notes: list[str]) -> list[FlatModuleNode]:
        result: list[FlatModuleNode] = []
        used: set[str] = set()
        key_remap: dict[str, str] = {}

        for index, item in enumerate(nodes, start=1):
            base_key = self._safe_key(item.key or f"node_{index}")
            unique_key = base_key
            suffix = 2
            while unique_key in used:
                unique_key = f"{base_key}_{suffix}"
                suffix += 1

            if unique_key != item.key:
                notes.append(f"已规范化重复或非法节点 key：{item.key or '-'} -> {unique_key}。")
            if item.key and item.key not in key_remap:
                key_remap[item.key] = unique_key

            used.add(unique_key)
            node_type = self._normalize_type(item.type)
            result.append(
                item.model_copy(
                    update={
                        "key": unique_key,
                        "type": node_type,
                        "parent_key": self._safe_key(item.parent_key) if item.parent_key else "",
                        "sort_order": item.sort_order or index,
                        "can_generate_ui_contract": node_type == "PAGE",
                        "feature_role": item.feature_role if node_type == "FEATURE" else None,
                    }
                )
            )

        if key_remap:
            remapped: list[FlatModuleNode] = []
            for item in result:
                parent_key = key_remap.get(item.parent_key, item.parent_key)
                remapped.append(item.model_copy(update={"parent_key": parent_key}))
            result = remapped

        return result

    def _calibrate_module_only(self, nodes: list[FlatModuleNode], notes: list[str]) -> list[FlatModuleNode]:
        result: list[FlatModuleNode] = []
        keys_to_keep: set[str] = set()

        for item in nodes:
            if item.type == "MODULE":
                keys_to_keep.add(item.key)
                result.append(
                    item.model_copy(
                        update={
                            "type": "MODULE",
                            "page_kind": None,
                            "route_path": None,
                            "layout_type": None,
                            "primary_entity": None,
                            "can_generate_ui_contract": False,
                            "feature_role": None,
                        }
                    )
                )
            else:
                notes.append(f"模块骨架/资料整理模式已忽略非模块节点：{item.name}（{item.type}）。")

        fixed: list[FlatModuleNode] = []
        for item in result:
            parent_key = item.parent_key if item.parent_key in keys_to_keep else ""
            if item.parent_key and not parent_key:
                notes.append(f"模块骨架模式已移除无效父级引用：{item.name}。")
            fixed.append(item.model_copy(update={"parent_key": parent_key}))

        return fixed

    def _calibrate_full_blueprint(
        self,
        nodes: list[FlatModuleNode],
        notes: list[str],
        requirement_text: str,
    ) -> list[FlatModuleNode]:
        if not nodes:
            return []

        by_key = {item.key: item for item in nodes}
        result: list[FlatModuleNode] = []
        result_keys: set[str] = set()

        default_module_key = ""

        def append_node(item: FlatModuleNode) -> None:
            if item.key in result_keys:
                return
            result_keys.add(item.key)
            result.append(item)

        def ensure_default_module() -> str:
            nonlocal default_module_key
            if default_module_key:
                return default_module_key
            base = self._safe_key(self._infer_domain_prefix(requirement_text) + "_core_module")
            key = self._unique_key(base, {item.key for item in result} | {item.key for item in nodes})
            default_module_key = key
            append_node(
                FlatModuleNode(
                    key=key,
                    parent_key="",
                    name="核心业务模块",
                    type="MODULE",
                    description="承载未能定位父级的业务能力和页面，建议用户确认后归入合适模块。",
                    sort_order=1,
                    page_kind=None,
                    route_path=None,
                    layout_type=None,
                    primary_entity=None,
                    can_generate_ui_contract=False,
                    feature_role=None,
                )
            )
            notes.append("完整模块树模式已补充兜底业务模块，用于承接未定位父级的功能或页面。")
            return key

        def nearest_module_key(item: FlatModuleNode | None) -> str:
            current = item
            seen: set[str] = set()
            while current and current.parent_key and current.parent_key not in seen:
                seen.add(current.parent_key)
                parent = by_key.get(current.parent_key)
                if parent is None:
                    break
                if parent.type == "MODULE":
                    return parent.key
                current = parent
            if current and current.type == "MODULE":
                return current.key
            return ensure_default_module()

        # First keep all MODULE nodes, but never below FEATURE / PAGE.
        for item in nodes:
            if item.type != "MODULE":
                continue
            parent = by_key.get(item.parent_key)
            parent_key = item.parent_key
            if parent and parent.type in {"FEATURE", "PAGE"}:
                parent_key = nearest_module_key(parent)
                notes.append(f"已修正 MODULE 节点父级：{item.name} 不应挂在 {parent.type} 下。")
            append_node(
                item.model_copy(
                    update={
                        "parent_key": parent_key if parent_key != item.key else "",
                        "can_generate_ui_contract": False,
                        "feature_role": None,
                    }
                )
            )

        # Then keep FEATURE nodes under MODULE.
        feature_keys: set[str] = set()
        for item in nodes:
            if item.type != "FEATURE":
                continue
            parent = by_key.get(item.parent_key)
            parent_key = item.parent_key if parent and parent.type == "MODULE" else nearest_module_key(item)
            if not parent or parent.type != "MODULE":
                notes.append(f"已修正 FEATURE 节点父级：{item.name} 必须挂在末级模块下。")
            fixed = item.model_copy(
                update={
                    "parent_key": parent_key,
                    "page_kind": None,
                    "route_path": None,
                    "layout_type": None,
                    "primary_entity": None,
                    "can_generate_ui_contract": False,
                    "feature_role": item.feature_role or "business_capability",
                }
            )
            feature_keys.add(fixed.key)
            append_node(fixed)

        # Finally ensure every PAGE is below a FEATURE. PAGE directly under MODULE gets a wrapper FEATURE.
        wrapper_by_parent_and_name: dict[tuple[str, str], str] = {}
        for item in nodes:
            if item.type != "PAGE":
                continue
            parent = by_key.get(item.parent_key)
            if parent and parent.type == "FEATURE":
                parent_key = parent.key
            else:
                module_key = nearest_module_key(parent or item)
                feature_name = self._infer_feature_name_from_page(item.name)
                wrapper_key_base = self._safe_key(f"{module_key}_{feature_name}_feature")
                cache_key = (module_key, feature_name)
                parent_key = wrapper_by_parent_and_name.get(cache_key, "")
                if not parent_key:
                    parent_key = self._unique_key(wrapper_key_base, result_keys)
                    wrapper_by_parent_and_name[cache_key] = parent_key
                    feature_keys.add(parent_key)
                    append_node(
                        FlatModuleNode(
                            key=parent_key,
                            parent_key=module_key,
                            name=feature_name,
                            type="FEATURE",
                            description=f"承载{item.name}相关业务能力。",
                            sort_order=max(1, item.sort_order),
                            page_kind=None,
                            route_path=None,
                            layout_type=None,
                            primary_entity=None,
                            can_generate_ui_contract=False,
                            feature_role="page_capability",
                        )
                    )
                notes.append(f"已为页面 {item.name} 补充业务能力分组：{feature_name}。")

            append_node(
                item.model_copy(
                    update={
                        "parent_key": parent_key,
                        "page_kind": item.page_kind or self._infer_page_kind(item.name),
                        "layout_type": item.layout_type or self._infer_layout_type(item.page_kind or self._infer_page_kind(item.name)),
                        "route_path": item.route_path or "/" + item.key.replace("_", "/"),
                        "primary_entity": item.primary_entity or item.name.replace("页面", "").replace("页", ""),
                        "can_generate_ui_contract": True,
                        "feature_role": None,
                    }
                )
            )

        # Full blueprint should not leave FEATURE without a PAGE.
        pages_by_parent: dict[str, int] = {}
        for item in result:
            if item.type == "PAGE":
                pages_by_parent[item.parent_key] = pages_by_parent.get(item.parent_key, 0) + 1

        for item in list(result):
            if item.type != "FEATURE" or pages_by_parent.get(item.key, 0) > 0:
                continue
            page_name = self._infer_page_name_from_feature(item.name)
            page_key = self._unique_key(self._safe_key(f"{item.key}_page"), result_keys)
            result_keys.add(page_key)
            result.append(
                FlatModuleNode(
                    key=page_key,
                    parent_key=item.key,
                    name=page_name,
                    type="PAGE",
                    description=f"用于承载{item.name}的主要页面入口。",
                    sort_order=item.sort_order,
                    page_kind=self._infer_page_kind(page_name),
                    route_path="/" + page_key.replace("_", "/"),
                    layout_type=self._infer_layout_type(self._infer_page_kind(page_name)),
                    primary_entity=page_name.replace("页面", "").replace("页", ""),
                    can_generate_ui_contract=True,
                    feature_role=None,
                )
            )
            notes.append(f"完整模块树模式已为业务能力 {item.name} 补充默认页面入口：{page_name}。")

        return result

    def _drop_invalid_parent_references(self, nodes: list[FlatModuleNode], notes: list[str]) -> list[FlatModuleNode]:
        keys = {item.key for item in nodes}
        result: list[FlatModuleNode] = []
        for item in nodes:
            parent_key = item.parent_key if item.parent_key in keys and item.parent_key != item.key else ""
            if item.parent_key and item.parent_key != parent_key:
                notes.append(f"已移除节点 {item.name} 的无效父级引用。")
            result.append(item.model_copy(update={"parent_key": parent_key}))
        return result

    def _sort_nodes(self, nodes: list[FlatModuleNode]) -> list[FlatModuleNode]:
        return sorted(nodes, key=lambda item: (item.parent_key or "", item.sort_order, item.key))

    def _normalize_type(self, value: Any) -> str:
        raw = str(value or "").strip().upper()
        aliases = {
            "DOMAIN": "MODULE",
            "DOMAIN_MODULE": "MODULE",
            "BUSINESS_DOMAIN": "MODULE",
            "SUBMODULE": "MODULE",
            "SUB_MODULE": "MODULE",
            "BUSINESS_MODULE": "MODULE",
            "FEATURE_MODULE": "MODULE",
            "FUNCTION": "FEATURE",
            "FUNCTION_POINT": "FEATURE",
            "ACTION": "FEATURE",
            "PAGE_MODULE": "PAGE",
        }
        normalized = aliases.get(raw, raw)
        return normalized if normalized in self.VALID_TYPES else "MODULE"

    def _safe_key(self, value: Any) -> str:
        cleaned = str(value or "").strip().lower().replace("-", "_")
        cleaned = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in cleaned)
        while "__" in cleaned:
            cleaned = cleaned.replace("__", "_")
        return cleaned.strip("_") or "node"

    def _unique_key(self, base: str, used: set[str]) -> str:
        key = self._safe_key(base)
        candidate = key
        suffix = 2
        while candidate in used:
            candidate = f"{key}_{suffix}"
            suffix += 1
        used.add(candidate)
        return candidate

    def _infer_domain_prefix(self, requirement_text: str) -> str:
        text = requirement_text or ""
        if any(token in text for token in ["仓储", "库存", "入库", "出库"]):
            return "warehouse"
        if any(token in text for token in ["订单", "电商", "商城"]):
            return "order"
        if any(token in text for token in ["生产", "工单", "制造"]):
            return "production"
        if any(token in text for token in ["财务", "发票", "付款", "收款"]):
            return "finance"
        return "business"

    def _infer_feature_name_from_page(self, page_name: str) -> str:
        name = (page_name or "页面功能").strip()
        for suffix in ["页面", "页"]:
            if name.endswith(suffix):
                name = name[: -len(suffix)]
                break
        return name or "页面功能"

    def _infer_page_name_from_feature(self, feature_name: str) -> str:
        name = (feature_name or "业务能力").strip()
        if name.endswith(("页", "页面")):
            return name
        if any(token in name for token in ["查询", "搜索", "列表", "台账"]):
            return f"{name}页" if name.endswith("列表") else f"{name}列表页"
        if any(token in name for token in ["创建", "新增", "新建"]):
            return f"{name}页"
        if any(token in name for token in ["详情", "明细", "查看"]):
            return f"{name}页"
        if any(token in name for token in ["审核", "审批"]):
            return f"{name}页"
        return f"{name}页"

    def _infer_page_kind(self, page_name: str) -> str:
        text = page_name or ""
        if any(token in text for token in ["列表", "台账", "查询", "搜索"]):
            return "LIST_PAGE"
        if any(token in text for token in ["创建", "新建", "新增", "编辑", "维护"]):
            return "FORM_PAGE"
        if any(token in text for token in ["详情", "明细", "查看"]):
            return "DETAIL_PAGE"
        if any(token in text for token in ["审核", "审批"]):
            return "APPROVAL_PAGE"
        if any(token in text for token in ["看板", "统计", "分析"]):
            return "DASHBOARD_PAGE"
        if any(token in text for token in ["设置", "配置"]):
            return "SETTINGS_PAGE"
        return "FORM_PAGE"

    def _infer_layout_type(self, page_kind: str | None) -> str:
        return {
            "LIST_PAGE": "table-list",
            "FORM_PAGE": "form-detail",
            "DETAIL_PAGE": "detail-tabs",
            "APPROVAL_PAGE": "approval-workbench",
            "DASHBOARD_PAGE": "dashboard",
            "SETTINGS_PAGE": "form-detail",
        }.get(page_kind or "", "form-detail")

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        for value in values:
            if value and value not in result:
                result.append(value)
        return result
