from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from app.agents.requirement.module_tree_upload_evidence_extractor import ModuleTreeUploadEvidenceExtractor
from app.schemas.agent_protocol import AgentTaskRequest


@dataclass
class ModuleTreeGenerationPlan:
    recommendedMode: str = "SKELETON"
    actualModelingMode: str = "PROGRESSIVE_MODULE_OUTLINE"
    maturityLevel: str = "LOW"
    confidence: float = 0.0
    reasons: list[str] = field(default_factory=list)
    missingSignals: list[str] = field(default_factory=list)
    sourceEvidence: list[dict[str, Any]] = field(default_factory=list)
    userOverride: bool = False
    uploadedEvidenceStrength: str = "NONE"
    documentEvidenceSummary: str = ""
    documentEvidence: dict[str, Any] = field(default_factory=dict)
    plannerVersion: str = "A9.10.14.4.3.15.4.10.4.3"

    def to_dict(self) -> dict[str, Any]:
        return {
            "recommendedMode": self.recommendedMode,
            "actualModelingMode": self.actualModelingMode,
            "maturityLevel": self.maturityLevel,
            "confidence": round(float(self.confidence), 3),
            "reasons": list(self.reasons),
            "missingSignals": list(self.missingSignals),
            "sourceEvidence": list(self.sourceEvidence),
            "userOverride": bool(self.userOverride),
            "uploadedEvidenceStrength": self.uploadedEvidenceStrength,
            "documentEvidenceSummary": self.documentEvidenceSummary,
            "documentEvidence": dict(self.documentEvidence),
            "plannerVersion": self.plannerVersion,
        }


class ModuleTreeGenerationPlanner:
    """Evidence-aware mode planner for module-tree generation.

    Public UX remains simple: module skeleton / full module tree / organize
    materials first. Internally this planner considers user text plus uploaded
    and parsed document evidence so complex files do not force premature page
    generation.
    """

    SKELETON = "SKELETON"
    FULL = "FULL_BLUEPRINT"
    ORGANIZE = "ORGANIZE_THEN_GENERATE"

    def __init__(self) -> None:
        self.upload_evidence_extractor = ModuleTreeUploadEvidenceExtractor()

    def build_plan(self, request: AgentTaskRequest, requirement_text: str) -> dict[str, Any]:
        input_payload = self._as_dict(getattr(request, "input", None))
        context_package = self._as_dict(getattr(request, "context_package", None))
        raw_mode = self._explicit_mode(input_payload, context_package)

        document_evidence = self.upload_evidence_extractor.extract(
            requirement_text=requirement_text or "",
            input_payload=input_payload,
            context_package=context_package,
        )

        corpus = self._build_corpus(requirement_text, input_payload, context_package)
        evidence: list[dict[str, Any]] = []
        reasons: list[str] = []
        missing: list[str] = []

        signals = {
            "businessObjects": self._collect_keywords(corpus, [
                "订单", "入库单", "出库单", "库存", "仓库", "库位", "供应商", "客户", "商品", "物料", "采购", "销售",
                "合同", "发票", "工单", "设备", "车辆", "员工", "角色", "权限", "审批", "审核", "报表", "看板", "台账", "任务",
            ]),
            "actions": self._collect_keywords(corpus, [
                "创建", "新增", "新建", "录入", "查询", "搜索", "筛选", "查看", "详情", "明细", "编辑", "修改", "维护", "删除", "提交",
                "审核", "审批", "驳回", "通过", "导入", "导出", "统计", "分析", "预警", "确认", "流转", "保存", "打印",
            ]),
            "pages": self._collect_keywords(corpus, [
                "页面", "列表页", "详情页", "明细页", "创建页", "新建页", "新增页", "编辑页", "审核页", "审批页", "工作台", "看板", "首页", "配置页", "报表页",
            ]),
            "fields": self._collect_keywords(corpus, [
                "字段", "表单", "表格列", "字段表", "数据字典", "供应商", "仓库", "状态", "创建时间", "经办人", "数量", "金额", "备注", "单号", "编号", "名称",
            ]),
            "roles": self._collect_keywords(corpus, [
                "角色", "管理员", "主管", "经办人", "操作员", "审核人", "审批人", "负责人", "用户", "客户", "供应商", "采购人员", "财务", "运营",
            ]),
            "flows": self._collect_keywords(corpus, [
                "流程", "流转", "状态", "草稿", "待审核", "已审核", "已驳回", "已完成", "待处理", "处理中", "通过", "驳回", "关闭", "归档",
            ]),
            "rules": self._collect_keywords(corpus, [
                "规则", "校验", "必填", "唯一", "权限", "不允许", "限制", "异常", "冲突", "审计", "留痕", "必须", "不能", "只允许",
            ]),
            "modules": self._collect_keywords(corpus, [
                "模块", "子模块", "业务域", "管理", "中心", "平台", "工作台", "配置", "报表", "分析", "运营", "基础资料",
            ]),
        }

        self._merge_document_signals(signals, document_evidence)
        evidence.extend(self._extract_source_evidence(requirement_text, signals))
        evidence.extend(self._document_source_evidence(document_evidence))

        attachment_profile = self._attachment_profile(input_payload, context_package, corpus, document_evidence)
        uploaded_strength = attachment_profile["strength"]
        mixed_sources = bool(attachment_profile["mixed"])
        normalized_mode = self._normalize_requested_mode(raw_mode)

        detail_score = 0
        detail_score += min(len(signals["businessObjects"]), 4) * 2
        detail_score += min(len(signals["actions"]), 5) * 2
        detail_score += min(len(signals["pages"]), 4) * 3
        detail_score += min(len(signals["fields"]), 5)
        detail_score += min(len(signals["roles"]), 3)
        detail_score += min(len(signals["flows"]), 3)
        detail_score += min(len(signals["rules"]), 3)
        detail_score += min(len(signals["modules"]), 4)

        if len(requirement_text or "") > 700:
            detail_score += 3
        if len(requirement_text or "") > 1600:
            detail_score += 2
        if attachment_profile["count"] > 0:
            detail_score += 2
        if uploaded_strength == "STRONG":
            detail_score += 4
        elif uploaded_strength == "MEDIUM":
            detail_score += 2
        elif uploaded_strength == "MIXED":
            detail_score += 1

        if signals["businessObjects"]:
            reasons.append("识别到明确业务对象：" + "、".join(signals["businessObjects"][:6]))
        if signals["actions"]:
            reasons.append("识别到功能动作：" + "、".join(signals["actions"][:8]))
        if signals["pages"]:
            reasons.append("识别到页面线索：" + "、".join(signals["pages"][:6]))
        if signals["fields"]:
            reasons.append("识别到字段/表单/表格线索：" + "、".join(signals["fields"][:8]))
        if document_evidence.get("documentCount", 0) > 0:
            reasons.append(str(document_evidence.get("summary") or "已识别上传资料证据。"))

        for label, key in [
            ("业务对象", "businessObjects"),
            ("功能动作", "actions"),
            ("页面线索", "pages"),
            ("字段信息", "fields"),
            ("角色/权限", "roles"),
            ("状态流程", "flows"),
            ("业务规则", "rules"),
        ]:
            if not signals[key]:
                missing.append(f"缺少{label}")

        # Explicit user or UI choice always wins; diagnostics are still recorded.
        if normalized_mode:
            recommended = normalized_mode
            user_override = True
        elif mixed_sources:
            recommended = self.ORGANIZE
            user_override = False
            reasons.append("上传资料来源较多、结构混杂或存在不确定项，建议先整理资料再生成。")
        elif document_evidence.get("recommendationBias") == self.FULL and detail_score >= 13:
            recommended = self.FULL
            user_override = False
        elif detail_score >= 17 and len(signals["businessObjects"]) >= 1 and len(signals["actions"]) >= 2 and (signals["pages"] or signals["fields"] or signals["flows"]):
            recommended = self.FULL
            user_override = False
        elif detail_score >= 13 and uploaded_strength == "STRONG" and not mixed_sources:
            recommended = self.FULL
            user_override = False
        else:
            recommended = self.SKELETON
            user_override = False

        if recommended == self.FULL:
            actual_mode = "FULL_ENTERPRISE_BLUEPRINT"
            maturity = "HIGH" if detail_score >= 17 or uploaded_strength == "STRONG" else "MEDIUM"
            confidence = min(0.95, 0.56 + detail_score / 46)
        elif recommended == self.ORGANIZE:
            actual_mode = "PROGRESSIVE_MODULE_OUTLINE"
            maturity = "MIXED"
            confidence = 0.78 if mixed_sources else 0.70
        else:
            actual_mode = "PROGRESSIVE_MODULE_OUTLINE"
            maturity = "LOW" if detail_score < 10 else "MEDIUM"
            confidence = min(0.84, 0.46 + detail_score / 52)

        if not reasons:
            reasons.append("当前输入偏概括，建议先确定模块边界。")

        plan = ModuleTreeGenerationPlan(
            recommendedMode=recommended,
            actualModelingMode=actual_mode,
            maturityLevel=maturity,
            confidence=confidence,
            reasons=self._dedupe(reasons)[:9],
            missingSignals=self._dedupe(missing)[:10],
            sourceEvidence=evidence[:28],
            userOverride=user_override,
            uploadedEvidenceStrength=uploaded_strength,
            documentEvidenceSummary=str(document_evidence.get("summary") or ""),
            documentEvidence=document_evidence,
        )
        return plan.to_dict()

    def apply_plan_to_request(self, request: AgentTaskRequest, plan: dict[str, Any]) -> AgentTaskRequest:
        input_payload = dict(self._as_dict(getattr(request, "input", None)))
        context_package = dict(self._as_dict(getattr(request, "context_package", None)))

        actual_mode = str(plan.get("actualModelingMode") or "PROGRESSIVE_MODULE_OUTLINE")
        input_payload["modeling_mode"] = actual_mode
        input_payload["modelingMode"] = actual_mode
        input_payload["moduleTreeGenerationPlan"] = plan
        input_payload["module_tree_generation_plan"] = plan
        context_package["module_tree_generation_plan"] = plan
        context_package["moduleTreeGenerationPlan"] = plan

        try:
            return request.model_copy(update={"input": input_payload, "context_package": context_package})
        except Exception:
            try:
                setattr(request, "input", input_payload)
                setattr(request, "context_package", context_package)
            except Exception:
                pass
            return request

    def prompt_section(self, plan: dict[str, Any]) -> str:
        recommended = str(plan.get("recommendedMode") or "SKELETON")
        actual = str(plan.get("actualModelingMode") or "PROGRESSIVE_MODULE_OUTLINE")
        reasons = self._string_list(plan.get("reasons"))
        missing = self._string_list(plan.get("missingSignals"))
        evidence = plan.get("sourceEvidence") if isinstance(plan.get("sourceEvidence"), list) else []
        document_evidence = plan.get("documentEvidence") if isinstance(plan.get("documentEvidence"), dict) else {}

        labels = {
            self.SKELETON: "模块骨架",
            self.FULL: "完整模块树",
            self.ORGANIZE: "先整理资料再生成",
        }
        lines = [
            "【模块树生成计划】",
            f"- 推荐方式：{labels.get(recommended, recommended)}（{recommended}）。",
            f"- 实际建模模式：{actual}。",
            f"- 成熟度：{plan.get('maturityLevel') or '-'}；置信度：{plan.get('confidence') or '-'}。",
        ]
        if reasons:
            lines.append("- 推荐原因：" + "；".join(reasons[:5]))
        if missing:
            lines.append("- 缺失信号：" + "；".join(missing[:6]))
        if document_evidence:
            lines.append("- 上传资料判断：" + str(document_evidence.get("summary") or "已识别上传资料。"))
            conflict_notes = self._string_list(document_evidence.get("conflictNotes"))
            if conflict_notes:
                lines.append("- 资料不确定项：" + "；".join(conflict_notes[:5]))
        if evidence:
            sample = []
            for item in evidence[:8]:
                if isinstance(item, dict):
                    sample.append(str(item.get("text") or item.get("keyword") or item.get("source") or "").strip())
            sample = [item for item in sample if item]
            if sample:
                lines.append("- 证据摘要：" + "；".join(sample[:7]))
        if recommended == self.ORGANIZE:
            lines.extend([
                "- 本轮是资料整理优先：只输出可信的 MODULE 候选骨架，不输出 FEATURE，不输出 PAGE。",
                "- 节点 description 必须写明资料来源、候选依据和不确定边界；不要为了显得完整而编造页面。",
                "- analysis_summary 必须总结：已识别业务域、模块候选、功能/页面候选、不确定项、建议下一步。",
                "- 后续由用户确认资料整理结果后，再补全业务能力和页面。",
            ])
        elif recommended == self.FULL:
            lines.extend([
                "- 本轮可以输出 MODULE / FEATURE / PAGE，但必须保持 MODULE -> FEATURE -> PAGE。",
                "- 完整树必须让每个 PAGE 回溯到明确业务能力；不要把按钮、字段、接口或校验项当作 FEATURE。",
                "- 对上传资料中低置信度或冲突的信息，只写入 description/analysis_summary，不要硬生成为页面。",
            ])
        else:
            lines.extend([
                "- 本轮只稳定模块边界：不要提前生成 FEATURE/PAGE。",
                "- 末级 MODULE 后续可在生成 MODULE_PRD 后由系统补全业务能力和页面。",
            ])
        return "\n".join(lines)

    def _explicit_mode(self, input_payload: dict[str, Any], context_package: dict[str, Any]) -> str:
        return str(
            input_payload.get("modeling_mode")
            or input_payload.get("modelingMode")
            or input_payload.get("recommendedMode")
            or context_package.get("modeling_mode")
            or context_package.get("modelingMode")
            or ""
        ).strip()

    def _normalize_requested_mode(self, raw: str) -> str | None:
        normalized = str(raw or "").strip().upper()
        if not normalized:
            return None
        if normalized in {"PROGRESSIVE_MODULE_OUTLINE", "MODULE_OUTLINE", "OUTLINE", "PROGRESSIVE", "MODULE_ONLY", "SKELETON"}:
            return self.SKELETON
        if normalized in {"FULL_ENTERPRISE_BLUEPRINT", "FULL_BLUEPRINT", "ENTERPRISE_BLUEPRINT", "FULL", "BLUEPRINT", "COMPLETE"}:
            return self.FULL
        if normalized in {"ORGANIZE_THEN_GENERATE", "ORGANIZE", "ORGANIZE_FIRST", "REVIEW_FIRST", "EVIDENCE_FIRST"}:
            return self.ORGANIZE
        return None

    def _build_corpus(self, requirement_text: str, input_payload: dict[str, Any], context_package: dict[str, Any]) -> str:
        parts = [requirement_text or ""]
        for value in [input_payload, context_package]:
            try:
                parts.append(json.dumps(value, ensure_ascii=False, default=str)[:30000])
            except Exception:
                parts.append(str(value)[:30000])
        return "\n".join(parts)

    def _merge_document_signals(self, signals: dict[str, list[str]], document_evidence: dict[str, Any]) -> None:
        aggregate = document_evidence.get("aggregateSignals") if isinstance(document_evidence, dict) else None
        if not isinstance(aggregate, dict):
            return
        for key, values in aggregate.items():
            if not isinstance(values, list):
                continue
            signals.setdefault(key, [])
            signals[key] = self._dedupe(signals[key] + [str(item) for item in values if str(item).strip()])

    def _collect_keywords(self, text: str, keywords: list[str]) -> list[str]:
        result: list[str] = []
        for keyword in keywords:
            if keyword and keyword in text and keyword not in result:
                result.append(keyword)
        return result

    def _extract_source_evidence(self, requirement_text: str, signals: dict[str, list[str]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for category, values in signals.items():
            for value in values[:6]:
                result.append({
                    "source": "requirement_text_or_context",
                    "evidenceType": category,
                    "keyword": value,
                    "text": value,
                })
        if requirement_text.strip():
            result.insert(0, {
                "source": "user_message",
                "evidenceType": "requirement_summary",
                "text": requirement_text.strip()[:280],
            })
        return result

    def _document_source_evidence(self, document_evidence: dict[str, Any]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        documents = document_evidence.get("documents") if isinstance(document_evidence, dict) else None
        if not isinstance(documents, list):
            return result
        for doc in documents[:10]:
            if not isinstance(doc, dict):
                continue
            result.append({
                "source": doc.get("source") or "upload_document",
                "evidenceType": "upload_document",
                "documentType": doc.get("documentType"),
                "title": doc.get("title"),
                "text": doc.get("textDigest"),
                "confidence": doc.get("confidence"),
            })
        return result

    def _attachment_profile(
        self,
        input_payload: dict[str, Any],
        context_package: dict[str, Any],
        corpus: str,
        document_evidence: dict[str, Any],
    ) -> dict[str, Any]:
        strength = str(document_evidence.get("strength") or "NONE")
        mixed = bool(document_evidence.get("mixed"))
        count = int(document_evidence.get("documentCount") or 0)

        text = corpus.lower()
        attachment_words = ["attachment", "attachments", "upload", "uploaded", "file", "files", "文档", "附件", "上传", "解析", "parsed"]
        document_words = ["prd", "需求文档", "需求说明", "功能清单", "页面清单", "角色权限", "流程图", "字段表", "接口文档", "会议纪要", "截图", "excel"]

        heuristic_count = 0
        for word in attachment_words:
            if word in text:
                heuristic_count += 1
        for word in document_words:
            if word.lower() in text:
                heuristic_count += 1

        if count <= 0 and heuristic_count > 0:
            count = heuristic_count
            strength = "WEAK"
        if heuristic_count >= 5 and strength != "STRONG":
            mixed = True
            strength = "MIXED"

        return {"count": count, "mixed": mixed, "strength": strength}

    def _as_dict(self, value: Any) -> dict[str, Any]:
        return dict(value) if isinstance(value, dict) else {}

    def _string_list(self, value: Any) -> list[str]:
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        for value in values:
            value = str(value or "").strip()
            if value and value not in result:
                result.append(value)
        return result
