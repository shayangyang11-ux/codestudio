from __future__ import annotations

import json
import re
from typing import Any

from app.schemas.style_assistant import (
    STYLE_ASSISTANT_LLM_JSON_SCHEMA,
    StyleAssistantAgentOutput,
    StyleAssistantAgentRequest,
    StyleAssistantAgentResponse,
)
from app.services.llm_service import LlmService


class StyleAssistantAgent:
    THEME_BY_INDUSTRY = {
        "COMMERCE": "commerce_light",
        "TECH": "tech_modern",
        "INDUSTRIAL": "industrial_pro",
        "FINANCE": "finance_stable",
        "GOV": "gov_clean",
        "GENERAL": "enterprise_default",
        "EDUCATION": "enterprise_default",
        "HEALTHCARE": "enterprise_default",
    }
    TONE_BY_THEME = {
        "commerce_light": "COMMERCE",
        "tech_modern": "TECH",
        "industrial_pro": "INDUSTRIAL",
        "finance_stable": "FINANCE",
        "gov_clean": "GOV",
        "enterprise_default": "CLEAN",
    }
    PRIMARY_BY_THEME = {
        "commerce_light": "#2563eb",
        "tech_modern": "#4f46e5",
        "industrial_pro": "#0f766e",
        "finance_stable": "#1e3a8a",
        "gov_clean": "#1d4ed8",
        "enterprise_default": "#2563eb",
    }
    COLOR_PATTERN = re.compile(r"^#[0-9a-fA-F]{6}$")

    def __init__(self) -> None:
        self.llm_service = LlmService()

    async def run(
        self,
        request: StyleAssistantAgentRequest,
    ) -> StyleAssistantAgentResponse:
        try:
            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=self._system_prompt(),
                user_prompt=self._user_prompt(request),
                output_schema_name="unified_style_assistant_theme_profile_output",
                output_json_schema=STYLE_ASSISTANT_LLM_JSON_SCHEMA,
            )
            parsed = json.loads(self._strip_code_fence(llm_result.text))
            output = StyleAssistantAgentOutput.model_validate(parsed)
            response = self._normalize_output(
                output,
                request,
                "STYLE_ASSISTANT_LLM",
            )
        except Exception as exc:
            response = self._fallback_response(request, exc)

        response.updatePayload = self._build_update_payload(response)
        return response

    def _system_prompt(self) -> str:
        return "\n".join([
            "你是 CodeMind Studio 的统一风格助手 Style Assistant。",
            "你的任务是把用户输入的风格目标、参考风格描述、竞品截图说明、品牌图说明、附件名称和服务端图片理解结果，统一转换成项目级 Theme Profile 建议。",
            "你不是页面生成器，不得输出 HTML、CSS、Vue、React、图片或源码。",
            "你不得复刻、复制、仿冒任何具体网站、App、竞品页面或品牌视觉，只能抽象其视觉气质、色彩倾向、密度、圆角、导航和布局策略。",
            "如果参考文本中包含【服务端参考图视觉理解】，说明后端已经读取图片像素并提取了主色、色板、明度、饱和度、页面类型和布局信号；应优先吸收这些抽象信号。",
            "只输出 JSON。",
            "允许 industry：GENERAL, COMMERCE, TECH, INDUSTRIAL, FINANCE, GOV, EDUCATION, HEALTHCARE。",
            "允许 themeKey：enterprise_default, tech_modern, commerce_light, industrial_pro, finance_stable, gov_clean。",
            "允许 visualTone：CLEAN, TECH, COMMERCE, INDUSTRIAL, FINANCE, GOV。",
            "允许 density：COMPACT, STANDARD, COMFORTABLE。",
            "允许 radius：SMALL, MEDIUM, LARGE。",
            "允许 loginLayout：BRAND_SPLIT, CENTER_CARD, MINIMAL。",
            "允许 navigationStyle：SIDE_NAV, TOP_NAV, MIXED。",
            "允许 analysisMode：STYLE_INTENT_TEXT, REFERENCE_TEXT, REFERENCE_IMAGE, MIXED_REFERENCE, REFERENCE_IMAGE_PENDING, MIXED_REFERENCE_PENDING。",
            "颜色必须使用 #RRGGBB。",
            "extractedSignals 必须描述抽象后的视觉信号，例如 colorIntent, layoutIntent, densityIntent, radiusIntent, toneKeywords, serverImageUnderstanding，不得包含复刻具体竞品的指令。",
        ])

    def _user_prompt(
        self,
        request: StyleAssistantAgentRequest,
    ) -> str:
        return "\n".join([
            "请根据以下输入生成统一项目 Theme Profile 建议。",
            "## 用户指令",
            request.instruction or "",
            "## 参考风格文本",
            request.reference_text or "",
            "## 参考备注",
            request.reference_notes or "",
            "## 参考模式",
            request.reference_mode or "AUTO",
            "## 目标页面类型",
            request.target_page_type or "",
            "## 是否允许暗色风格",
            str(bool(request.allow_dark_mode)),
            "## 附件 ID 列表",
            json.dumps(request.attachment_ids or [], ensure_ascii=False),
            "## 附件名称列表",
            json.dumps(request.attachment_names or [], ensure_ascii=False),
            "## 当前项目风格配置 JSON",
            json.dumps(request.current_profile or {}, ensure_ascii=False, default=str),
            "请输出严格 JSON，字段必须包含 industry, themeKey, visualTone, primaryColor, secondaryColor, accentColor, density, radius, loginLayout, navigationStyle, darkModeEnabled, analysisMode, extractedSignals, summary, reasons, warnings, confidence。",
        ])

    def _normalize_output(
        self,
        output: StyleAssistantAgentOutput,
        request: StyleAssistantAgentRequest,
        agent_mode: str,
    ) -> StyleAssistantAgentResponse:
        text = self._combined_text(request).lower()
        industry = self._normalize_industry(output.industry)
        if industry == "GENERAL":
            industry = self._infer_industry(text)

        theme_key = self._normalize_theme_key(output.themeKey, industry)
        visual_tone = self.TONE_BY_THEME.get(theme_key, output.visualTone or "CLEAN")
        primary_color = self._normalize_color(
            output.primaryColor,
            self.PRIMARY_BY_THEME.get(theme_key, "#2563eb"),
        )
        secondary_color = self._normalize_optional_color(output.secondaryColor)
        accent_color = self._normalize_optional_color(output.accentColor)
        analysis_mode = self._resolve_analysis_mode(request, output.analysisMode)
        extracted = output.extractedSignals if isinstance(output.extractedSignals, dict) else {}

        reasons = self._clean_text_list(output.reasons) or [f"已根据用户输入抽象出 {industry} / {theme_key} 项目风格。"]
        warnings = self._clean_text_list(output.warnings)
        warnings.append("仅提取抽象风格特征，不复制具体竞品页面、品牌资产或文案。")

        summary = output.summary.strip() if output.summary else f"建议使用 {industry} / {theme_key} 项目风格。"

        return StyleAssistantAgentResponse(
            industry=industry,
            themeKey=theme_key,
            visualTone=visual_tone,
            primaryColor=primary_color,
            secondaryColor=secondary_color,
            accentColor=accent_color,
            density=output.density,
            radius=output.radius,
            loginLayout=output.loginLayout,
            navigationStyle=output.navigationStyle,
            darkModeEnabled=bool(output.darkModeEnabled),
            analysisMode=analysis_mode,
            extractedSignals=extracted,
            summary=summary,
            reasons=reasons[:10],
            warnings=warnings[:10],
            confidence=self._clamp_confidence(output.confidence),
            agentMode=agent_mode,
            attachmentIds=request.attachment_ids or [],
            attachmentNames=request.attachment_names or [],
            updatePayload={},
        )

    def _fallback_response(
        self,
        request: StyleAssistantAgentRequest,
        exc: Exception,
    ) -> StyleAssistantAgentResponse:
        text = self._combined_text(request).lower()
        industry = self._infer_industry(text)
        theme_key = self.THEME_BY_INDUSTRY.get(industry, "enterprise_default")
        visual_tone = self.TONE_BY_THEME.get(theme_key, "CLEAN")
        density = self._infer_density(text)
        radius = self._infer_radius(text, industry)
        login_layout = self._infer_login_layout(text)
        navigation_style = self._infer_navigation_style(text)
        dark_mode = bool(request.allow_dark_mode) or self._contains_any(text, "暗色", "深色", "黑色", "dark", "夜间")
        primary = self._infer_primary_color(text, theme_key)
        analysis_mode = self._resolve_analysis_mode(request, None)

        return StyleAssistantAgentResponse(
            industry=industry,
            themeKey=theme_key,
            visualTone=visual_tone,
            primaryColor=primary,
            secondaryColor="#06b6d4" if industry == "TECH" else None,
            accentColor="#22c55e" if industry == "TECH" else None,
            density=density,
            radius=radius,
            loginLayout=login_layout,
            navigationStyle=navigation_style,
            darkModeEnabled=dark_mode,
            analysisMode=analysis_mode,
            extractedSignals={
                "styleKeywords": self._extract_keywords(text),
                "source": "PYTHON_RULE_FALLBACK",
                "hasReferenceMaterial": self._has_reference_material(request),
                "hasServerImageUnderstanding": "【服务端参考图视觉理解】" in self._combined_text(request),
            },
            summary=f"已根据输入抽象为 {industry} / {theme_key} 项目风格。",
            reasons=[
                f"根据关键词识别行业方向为 {industry}。",
                f"匹配主题 {theme_key} 与视觉气质 {visual_tone}。",
            ],
            warnings=[
                "Style Assistant LLM 调用失败，已使用 Python 规则兜底。原因：" + str(exc)[:180],
                "仅提取抽象风格特征，不复制具体竞品页面、品牌资产或文案。",
            ],
            confidence=0.62 if "【服务端参考图视觉理解】" in self._combined_text(request) else 0.58,
            agentMode="PYTHON_RULE_FALLBACK",
            attachmentIds=request.attachment_ids or [],
            attachmentNames=request.attachment_names or [],
            updatePayload={},
        )

    def _build_update_payload(
        self,
        response: StyleAssistantAgentResponse,
    ) -> dict[str, Any]:
        return {
            "industry": response.industry,
            "themeKey": response.themeKey,
            "visualTone": response.visualTone,
            "primaryColor": response.primaryColor,
            "secondaryColor": response.secondaryColor,
            "accentColor": response.accentColor,
            "density": response.density,
            "radius": response.radius,
            "loginLayout": response.loginLayout,
            "navigationStyle": response.navigationStyle,
            "darkModeEnabled": response.darkModeEnabled,
            "locked": False,
            "notes": response.summary,
        }

    def _resolve_analysis_mode(
        self,
        request: StyleAssistantAgentRequest,
        proposed: str | None,
    ) -> str:
        text = self._combined_text(request)
        has_attachment = bool(request.attachment_ids or request.attachment_names)
        has_reference_text = bool((request.reference_text or "").strip() or (request.reference_notes or "").strip())
        has_server_image_understanding = "【服务端参考图视觉理解】" in text
        lower_text = text.lower()
        mentions_reference = self._contains_any(lower_text, "参考", "截图", "竞品", "品牌图", "上传", "image", "screenshot", "reference")

        if has_server_image_understanding and (has_reference_text or mentions_reference):
            return "MIXED_REFERENCE"
        if has_server_image_understanding:
            return "REFERENCE_IMAGE"
        if has_attachment and (has_reference_text or mentions_reference):
            return "MIXED_REFERENCE_PENDING"
        if has_attachment:
            return "REFERENCE_IMAGE_PENDING"
        if has_reference_text or mentions_reference:
            return "REFERENCE_TEXT"
        if proposed in {"STYLE_INTENT_TEXT", "REFERENCE_TEXT", "REFERENCE_IMAGE", "MIXED_REFERENCE", "REFERENCE_IMAGE_PENDING", "MIXED_REFERENCE_PENDING"}:
            return proposed
        return "STYLE_INTENT_TEXT"

    def _has_reference_material(
        self,
        request: StyleAssistantAgentRequest,
    ) -> bool:
        return self._resolve_analysis_mode(request, None) != "STYLE_INTENT_TEXT"

    def _infer_industry(self, text: str) -> str:
        if self._contains_any(text, "电商", "商城", "零售", "订单", "商品", "购物", "会员", "用户中心", "转化", "营销", "mall", "shop", "ecommerce", "京东"):
            return "COMMERCE"
        if self._contains_any(text, "科技", "智能", "ai", "算法", "数据平台", "saas", "云平台", "大数据", "数字化", "中台", "低代码", "研发"):
            return "TECH"
        if self._contains_any(text, "工业", "制造", "工厂", "设备", "产线", "mes", "iot", "传感器", "运维", "监控", "工控"):
            return "INDUSTRIAL"
        if self._contains_any(text, "金融", "风控", "银行", "支付", "交易", "账务", "结算", "授信", "finance", "risk"):
            return "FINANCE"
        if self._contains_any(text, "政务", "政府", "监管", "公共服务", "办事", "审批", "gov"):
            return "GOV"
        return "GENERAL"

    def _infer_density(self, text: str) -> str:
        if self._contains_any(text, "紧凑", "高密度", "后台", "表格多", "信息多", "compact"):
            return "COMPACT"
        if self._contains_any(text, "舒展", "留白", "高级", "大屏", "营销", "comfortable"):
            return "COMFORTABLE"
        return "STANDARD"

    def _infer_radius(self, text: str, industry: str) -> str:
        if self._contains_any(text, "圆润", "大圆角", "年轻", "亲和", "c端", "现代"):
            return "LARGE"
        if self._contains_any(text, "严肃", "规整", "硬朗", "工业", "金融", "政务", "小圆角"):
            return "SMALL"
        if industry in {"TECH", "COMMERCE"}:
            return "LARGE"
        if industry in {"INDUSTRIAL", "FINANCE", "GOV"}:
            return "SMALL"
        return "MEDIUM"

    def _infer_login_layout(self, text: str) -> str:
        if self._contains_any(text, "居中", "居中卡片", "center"):
            return "CENTER_CARD"
        if self._contains_any(text, "极简", "简单", "minimal"):
            return "MINIMAL"
        return "BRAND_SPLIT"

    def _infer_navigation_style(self, text: str) -> str:
        if self._contains_any(text, "顶部导航", "top nav", "官网", "c端", "搜索栏"):
            return "TOP_NAV"
        if self._contains_any(text, "混合导航", "顶部加侧边", "mixed"):
            return "MIXED"
        return "SIDE_NAV"

    def _infer_primary_color(self, text: str, theme_key: str) -> str:
        match = re.search(r"本地主色候选\s*(#[0-9a-fA-F]{6})", text)
        if not match:
            match = re.search(r"主色候选\s*(#[0-9a-fA-F]{6})", text)
        if match:
            return match.group(1).lower()
        if self._contains_any(text, "紫色", "紫蓝", "科技紫", "violet", "purple"):
            return "#4f46e5"
        if self._contains_any(text, "青色", "青绿", "绿色", "teal", "green"):
            return "#0f766e"
        if self._contains_any(text, "橙色", "橙黄", "orange"):
            return "#f97316"
        if self._contains_any(text, "红色", "玫红", "red", "rose"):
            return "#e11d48"
        if self._contains_any(text, "深蓝", "藏蓝", "navy"):
            return "#1e3a8a"
        return self.PRIMARY_BY_THEME.get(theme_key, "#2563eb")

    def _normalize_industry(self, value: str | None) -> str:
        text = (value or "GENERAL").strip().upper()
        if text in self.THEME_BY_INDUSTRY:
            return text
        return "GENERAL"

    def _normalize_theme_key(self, value: str | None, industry: str) -> str:
        text = (value or "").strip()
        if text in self.TONE_BY_THEME:
            return text
        return self.THEME_BY_INDUSTRY.get(industry, "enterprise_default")

    def _normalize_color(self, value: str | None, fallback: str) -> str:
        text = (value or fallback or "#2563eb").strip()
        if not text.startswith("#"):
            text = "#" + text
        if len(text) == 4:
            text = "#" + "".join(ch * 2 for ch in text[1:])
        if not self.COLOR_PATTERN.match(text):
            return fallback
        return text.lower()

    def _normalize_optional_color(self, value: str | None) -> str | None:
        if not value:
            return None
        text = value.strip()
        if not text.startswith("#"):
            text = "#" + text
        if len(text) == 4:
            text = "#" + "".join(ch * 2 for ch in text[1:])
        if not self.COLOR_PATTERN.match(text):
            return None
        return text.lower()

    def _combined_text(self, request: StyleAssistantAgentRequest) -> str:
        parts = [
            request.instruction,
            request.reference_text,
            request.reference_notes,
            request.target_page_type,
            " ".join(request.attachment_names or []),
        ]
        return " ".join(part.strip() for part in parts if part and part.strip())

    def _contains_any(self, text: str, *keywords: str) -> bool:
        return any(keyword.lower() in text for keyword in keywords)

    def _extract_keywords(self, text: str) -> list[str]:
        keywords = [
            "科技", "SaaS", "深色", "蓝紫", "金融", "电商", "工业", "政务",
            "紧凑", "舒展", "大圆角", "小圆角", "左侧导航", "顶部导航", "品牌分栏",
        ]
        return [keyword for keyword in keywords if keyword.lower() in text]

    def _clean_text_list(self, values: list[Any] | None) -> list[str]:
        result: list[str] = []
        for item in values or []:
            text = str(item).strip()
            if text:
                result.append(text[:240])
        return result

    def _clamp_confidence(self, value: float | int | None) -> float:
        try:
            number = float(value)
        except Exception:
            number = 0.72
        return max(0.35, min(0.95, number))

    def _strip_code_fence(self, value: str) -> str:
        text = value.strip()
        if text.startswith("```"):
            lines = text.splitlines()
            if lines and lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            return "\n".join(lines).strip()
        return text
