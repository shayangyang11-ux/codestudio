from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any


@dataclass
class UploadDocumentEvidence:
    source: str = "context"
    title: str = ""
    documentType: str = "UNKNOWN"
    confidence: float = 0.0
    textDigest: str = ""
    signals: dict[str, list[str]] = field(default_factory=dict)
    uncertaintyNotes: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "title": self.title,
            "documentType": self.documentType,
            "confidence": round(float(self.confidence), 3),
            "textDigest": self.textDigest,
            "signals": self.signals,
            "uncertaintyNotes": self.uncertaintyNotes,
        }


class ModuleTreeUploadEvidenceExtractor:
    """Extract deterministic evidence from uploaded / parsed document context.

    The goal is not to fully understand every document. It is to keep the module
    tree planner safe: strong structured evidence may justify a full blueprint;
    mixed or noisy evidence should enter organize-first mode instead of forcing
    premature FEATURE/PAGE generation.
    """

    DOCUMENT_KEYS = {
        "attachments", "attachmentContexts", "attachment_contexts",
        "parsedAttachments", "parsed_attachment_contexts", "parsedAttachmentContexts",
        "sourceDocuments", "source_documents", "documents", "files", "uploads",
        "uploadedFiles", "uploaded_files", "externalDocuments", "external_documents",
    }

    SIGNAL_KEYWORDS: dict[str, list[str]] = {
        "businessObjects": [
            "订单", "入库单", "出库单", "库存", "仓库", "库位", "供应商", "客户", "商品", "物料", "SKU",
            "采购单", "销售单", "合同", "发票", "工单", "设备", "车辆", "员工", "部门", "角色", "权限", "审批", "审核",
            "报表", "看板", "台账", "任务", "项目", "客户档案", "商机", "线索", "门店", "会员", "课程", "病例", "患者",
        ],
        "actions": [
            "创建", "新增", "新建", "录入", "导入", "提交", "保存", "查询", "搜索", "筛选", "查看", "明细", "详情",
            "编辑", "修改", "维护", "删除", "作废", "审核", "审批", "驳回", "通过", "确认", "分配", "派发", "接单",
            "导出", "打印", "统计", "分析", "预警", "流转", "归档", "同步", "结算", "支付", "退款",
        ],
        "pages": [
            "页面", "页", "列表页", "详情页", "明细页", "创建页", "新增页", "新建页", "编辑页", "审核页", "审批页",
            "工作台", "看板", "首页", "配置页", "设置页", "报表页", "台账页", "弹窗", "抽屉", "向导",
        ],
        "fields": [
            "字段", "表单", "表格列", "列名", "字段名", "数据字典", "编号", "单号", "名称", "状态", "类型", "时间",
            "创建时间", "更新时间", "负责人", "经办人", "数量", "金额", "价格", "备注", "附件", "图片", "地址", "手机号",
        ],
        "roles": [
            "角色", "管理员", "主管", "经办人", "操作员", "审核人", "审批人", "负责人", "用户", "客户", "供应商", "采购人员",
            "仓库管理员", "财务", "运营", "客服", "销售", "班组长", "调度员",
        ],
        "flows": [
            "流程", "流转", "状态流", "状态机", "草稿", "待提交", "待审核", "审核中", "已审核", "已驳回", "已取消",
            "已完成", "待处理", "处理中", "通过", "驳回", "退回", "关闭", "归档",
        ],
        "rules": [
            "规则", "校验", "必填", "唯一", "权限", "可见", "可编辑", "不允许", "限制", "异常", "冲突", "审计", "留痕",
            "超过", "小于", "必须", "不能", "只允许", "默认", "自动", "手动",
        ],
        "modules": [
            "模块", "子模块", "业务域", "管理", "中心", "平台", "工作台", "配置", "设置", "报表", "分析", "运营", "基础资料",
        ],
    }

    DOC_TYPE_HINTS: dict[str, list[str]] = {
        "PRD": ["prd", "产品需求", "需求规格", "需求说明", "业务需求", "需求文档", "brd", "mrd", "srs"],
        "FUNCTION_LIST": ["功能清单", "功能列表", "功能点", "需求列表", "backlog", "user story", "用户故事"],
        "PAGE_LIST": ["页面清单", "页面列表", "原型", "线框", "wireframe", "prototype", "页面设计", "交互稿"],
        "FIELD_TABLE": ["字段表", "字段清单", "数据字典", "表结构", "表格", "excel", "xlsx", "csv", "数据模型"],
        "PERMISSION_MATRIX": ["权限矩阵", "角色权限", "权限表", "菜单权限", "数据权限"],
        "WORKFLOW": ["流程图", "业务流程", "审批流", "状态流", "状态机", "泳道图", "bpmn"],
        "API_DOC": ["接口文档", "api", "swagger", "openapi", "endpoint", "request", "response"],
        "MEETING_NOTES": ["会议纪要", "访谈", "沟通记录", "聊天记录", "讨论", "纪要"],
        "SCREENSHOT": ["截图", "图片", "png", "jpg", "jpeg", "原型截图", "界面截图"],
    }

    CONFLICT_MARKERS = ["冲突", "多个版本", "旧版", "新版", "待确认", "不确定", "可能", "TODO", "草稿", "暂定", "不一致"]

    def extract(
        self,
        *,
        requirement_text: str,
        input_payload: dict[str, Any],
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        documents = self._collect_documents(input_payload, context_package)
        evidences: list[UploadDocumentEvidence] = []
        aggregate_signals: dict[str, list[str]] = {key: [] for key in self.SIGNAL_KEYWORDS}
        conflict_notes: list[str] = []

        for index, doc in enumerate(documents, start=1):
            title = self._title_of(doc, index)
            text = self._text_of(doc)
            if not text and not title:
                continue

            doc_type = self._classify_document(title, text)
            signals = self._extract_signals(text + "\n" + title)
            for key, values in signals.items():
                aggregate_signals.setdefault(key, [])
                aggregate_signals[key].extend(values)

            notes = self._uncertainty_notes(title, text, doc_type)
            conflict_notes.extend(notes)
            confidence = self._document_confidence(doc_type, text, signals, notes)
            evidences.append(
                UploadDocumentEvidence(
                    source=str(doc.get("source") or doc.get("origin") or doc.get("attachmentId") or doc.get("fileId") or f"document_{index}"),
                    title=title,
                    documentType=doc_type,
                    confidence=confidence,
                    textDigest=self._trim(text or title, 360),
                    signals={key: self._dedupe(values)[:12] for key, values in signals.items() if values},
                    uncertaintyNotes=notes[:6],
                )
            )

        for key, values in list(aggregate_signals.items()):
            aggregate_signals[key] = self._dedupe(values)[:24]

        doc_types = self._dedupe([item.documentType for item in evidences if item.documentType])
        structured_types = {"PRD", "FUNCTION_LIST", "PAGE_LIST", "FIELD_TABLE", "PERMISSION_MATRIX", "WORKFLOW"}
        noisy_types = {"MEETING_NOTES", "SCREENSHOT", "API_DOC", "UNKNOWN"}
        structured_count = sum(1 for item in evidences if item.documentType in structured_types)
        noisy_count = sum(1 for item in evidences if item.documentType in noisy_types)
        strong_docs = [item for item in evidences if item.confidence >= 0.68 and item.documentType in structured_types]

        has_pages = bool(aggregate_signals.get("pages"))
        has_actions = bool(aggregate_signals.get("actions"))
        has_fields = bool(aggregate_signals.get("fields"))
        has_objects = bool(aggregate_signals.get("businessObjects"))
        has_roles_or_flows = bool(aggregate_signals.get("roles") or aggregate_signals.get("flows"))

        mixed = False
        if len(doc_types) >= 3 and noisy_count >= 1:
            mixed = True
        if len(evidences) >= 3 and noisy_count >= 2:
            mixed = True
        if len(conflict_notes) >= 2:
            mixed = True
        if len(evidences) >= 5 and not strong_docs:
            mixed = True

        if not evidences:
            strength = "NONE"
        elif mixed:
            strength = "MIXED"
        elif strong_docs and has_objects and has_actions and (has_pages or has_fields or has_roles_or_flows):
            strength = "STRONG"
        elif structured_count >= 1:
            strength = "MEDIUM"
        else:
            strength = "WEAK"

        recommendation_bias = "NONE"
        if strength == "MIXED":
            recommendation_bias = "ORGANIZE_THEN_GENERATE"
        elif strength == "STRONG" and (has_pages or has_fields) and has_actions:
            recommendation_bias = "FULL_BLUEPRINT"
        elif strength in {"WEAK", "MEDIUM"}:
            recommendation_bias = "SKELETON"

        summary = self._build_summary(evidences, strength, aggregate_signals, conflict_notes)
        return {
            "documentCount": len(evidences),
            "documentTypes": doc_types,
            "structuredDocumentCount": structured_count,
            "noisyDocumentCount": noisy_count,
            "strength": strength,
            "mixed": mixed,
            "recommendationBias": recommendation_bias,
            "summary": summary,
            "documents": [item.to_dict() for item in evidences[:12]],
            "aggregateSignals": aggregate_signals,
            "conflictNotes": self._dedupe(conflict_notes)[:12],
            "extractorVersion": "A9.10.14.4.3.15.4.10.4.3",
        }

    def _collect_documents(self, input_payload: dict[str, Any], context_package: dict[str, Any]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen_fingerprints: set[str] = set()

        def add_doc(value: Any, source: str) -> None:
            if isinstance(value, dict):
                doc = dict(value)
                text = self._text_of(doc)
                title = self._title_of(doc, len(result) + 1)
                if not text and not title:
                    return
                doc.setdefault("source", source)
                fingerprint = (title + "::" + text[:500]).strip()
                if fingerprint and fingerprint not in seen_fingerprints:
                    seen_fingerprints.add(fingerprint)
                    result.append(doc)
            elif isinstance(value, str) and value.strip():
                doc = {"source": source, "title": source, "text": value.strip()}
                fingerprint = f"{source}::{value.strip()[:500]}"
                if fingerprint not in seen_fingerprints:
                    seen_fingerprints.add(fingerprint)
                    result.append(doc)

        for container_name, container in [("input", input_payload), ("context", context_package)]:
            if not isinstance(container, dict):
                continue
            for key, value in container.items():
                if key in self.DOCUMENT_KEYS:
                    if isinstance(value, list):
                        for item in value:
                            add_doc(item, f"{container_name}.{key}")
                    elif isinstance(value, dict):
                        # Some maps are {fileId: parsedContext}; keep each value when possible.
                        if any(isinstance(v, (dict, str)) for v in value.values()):
                            for sub_key, sub_value in value.items():
                                add_doc(sub_value, f"{container_name}.{key}.{sub_key}")
                        add_doc(value, f"{container_name}.{key}")
                    elif isinstance(value, str):
                        add_doc(value, f"{container_name}.{key}")

        # Some backends merge parsed attachment text into nested metadata fields.
        self._walk_for_document_like_values(input_payload, "input", result, seen_fingerprints)
        self._walk_for_document_like_values(context_package, "context", result, seen_fingerprints)
        return result[:24]

    def _walk_for_document_like_values(
        self,
        value: Any,
        source: str,
        result: list[dict[str, Any]],
        seen: set[str],
        depth: int = 0,
    ) -> None:
        if depth > 5:
            return
        if isinstance(value, dict):
            keys = {str(key) for key in value.keys()}
            text_keys = {"text", "parsedText", "content", "markdown", "summary", "rawText", "extractedText"}
            title_keys = {"title", "fileName", "filename", "name", "displayName"}
            if keys & text_keys and (keys & title_keys or keys & {"mimeType", "contentType", "attachmentId", "fileId"}):
                text = self._text_of(value)
                title = self._title_of(value, len(result) + 1)
                fingerprint = (title + "::" + text[:500]).strip()
                if (text or title) and fingerprint not in seen:
                    seen.add(fingerprint)
                    doc = dict(value)
                    doc.setdefault("source", source)
                    result.append(doc)
            for key, child in list(value.items())[:80]:
                if str(key) in {"moduleTree", "modules", "flat_modules", "children"}:
                    continue
                self._walk_for_document_like_values(child, f"{source}.{key}", result, seen, depth + 1)
        elif isinstance(value, list):
            for index, child in enumerate(value[:50]):
                self._walk_for_document_like_values(child, f"{source}[{index}]", result, seen, depth + 1)

    def _title_of(self, doc: dict[str, Any], index: int) -> str:
        for key in ["title", "fileName", "filename", "name", "displayName", "originalFilename", "attachmentName"]:
            value = doc.get(key)
            if value:
                return str(value).strip()[:160]
        return f"上传资料 {index}"

    def _text_of(self, doc: dict[str, Any]) -> str:
        candidates: list[str] = []
        for key in ["parsedText", "extractedText", "text", "content", "contentMarkdown", "markdown", "summary", "description", "rawText"]:
            value = doc.get(key)
            if isinstance(value, str) and value.strip():
                candidates.append(value.strip())
            elif isinstance(value, (dict, list)):
                try:
                    candidates.append(json.dumps(value, ensure_ascii=False, default=str)[:12000])
                except Exception:
                    candidates.append(str(value)[:12000])
        if not candidates:
            try:
                compact = json.dumps(doc, ensure_ascii=False, default=str)
                if any(token in compact for token in ["需求", "页面", "字段", "流程", "模块", "功能", "接口"]):
                    candidates.append(compact[:12000])
            except Exception:
                pass
        return "\n".join(candidates)[:16000]

    def _classify_document(self, title: str, text: str) -> str:
        corpus = (title + "\n" + text[:5000]).lower()
        best_type = "UNKNOWN"
        best_score = 0
        for doc_type, hints in self.DOC_TYPE_HINTS.items():
            score = 0
            for hint in hints:
                if hint.lower() in corpus:
                    score += 2 if hint.lower() in title.lower() else 1
            if score > best_score:
                best_score = score
                best_type = doc_type
        return best_type

    def _extract_signals(self, text: str) -> dict[str, list[str]]:
        signals: dict[str, list[str]] = {}
        for key, keywords in self.SIGNAL_KEYWORDS.items():
            hits = [keyword for keyword in keywords if keyword and keyword in text]
            signals[key] = self._dedupe(hits)

        # Extract explicit page-like headings such as “入库单列表页”.
        page_matches = re.findall(r"[\u4e00-\u9fa5A-Za-z0-9]{2,24}(?:列表页|详情页|明细页|创建页|新增页|编辑页|审核页|审批页|看板|工作台|页面)", text)
        if page_matches:
            signals.setdefault("pages", [])
            signals["pages"].extend(page_matches[:20])

        module_matches = re.findall(r"[\u4e00-\u9fa5A-Za-z0-9]{2,24}(?:管理|中心|模块|工作台|配置|设置)", text)
        if module_matches:
            signals.setdefault("modules", [])
            signals["modules"].extend(module_matches[:20])

        return {key: self._dedupe(values) for key, values in signals.items() if values}

    def _uncertainty_notes(self, title: str, text: str, doc_type: str) -> list[str]:
        corpus = title + "\n" + text[:5000]
        notes: list[str] = []
        for marker in self.CONFLICT_MARKERS:
            if marker in corpus:
                notes.append(f"资料包含不确定/冲突标记：{marker}")
        if doc_type == "API_DOC":
            notes.append("接口文档只能辅助识别业务对象和动作，不能单独代表完整模块边界。")
        if doc_type == "SCREENSHOT":
            notes.append("截图/图片只能辅助识别页面线索，不能单独代表完整需求范围。")
        if doc_type == "MEETING_NOTES":
            notes.append("会议纪要通常需要先整理候选和不确定项。")
        return self._dedupe(notes)

    def _document_confidence(self, doc_type: str, text: str, signals: dict[str, list[str]], notes: list[str]) -> float:
        score = 0.22
        if doc_type != "UNKNOWN":
            score += 0.18
        if doc_type in {"PRD", "FUNCTION_LIST", "PAGE_LIST"}:
            score += 0.18
        if signals.get("businessObjects"):
            score += 0.10
        if signals.get("actions"):
            score += 0.10
        if signals.get("pages"):
            score += 0.12
        if signals.get("fields"):
            score += 0.08
        if signals.get("roles") or signals.get("flows"):
            score += 0.08
        if len(text) > 3000:
            score += 0.05
        if notes:
            score -= min(0.18, 0.04 * len(notes))
        return max(0.05, min(0.95, score))

    def _build_summary(
        self,
        evidences: list[UploadDocumentEvidence],
        strength: str,
        aggregate_signals: dict[str, list[str]],
        conflict_notes: list[str],
    ) -> str:
        if not evidences:
            return "未识别到可用上传资料证据。"
        doc_types = self._dedupe([item.documentType for item in evidences])
        parts = [f"识别到 {len(evidences)} 份上传/解析资料", f"资料强度：{strength}"]
        if doc_types:
            parts.append("资料类型：" + "、".join(doc_types[:8]))
        if aggregate_signals.get("businessObjects"):
            parts.append("业务对象：" + "、".join(aggregate_signals["businessObjects"][:8]))
        if aggregate_signals.get("actions"):
            parts.append("功能动作：" + "、".join(aggregate_signals["actions"][:8]))
        if aggregate_signals.get("pages"):
            parts.append("页面线索：" + "、".join(aggregate_signals["pages"][:8]))
        if conflict_notes:
            parts.append("不确定项：" + "、".join(self._dedupe(conflict_notes)[:4]))
        return "；".join(parts) + "。"

    def _trim(self, value: str, max_chars: int) -> str:
        text = str(value or "").strip()
        if len(text) <= max_chars:
            return text
        return text[:max_chars] + "..."

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        for value in values:
            item = str(value or "").strip()
            if item and item not in result:
                result.append(item)
        return result
