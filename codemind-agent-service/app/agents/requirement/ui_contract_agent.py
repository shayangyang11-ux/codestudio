from __future__ import annotations

import json
from typing import Any

from app.agents.requirement.ui_contract_generation_plan import (
    UiContractGenerationPlan,
    UiContractGenerationPlanBuilder,
)
from app.agents.requirement.ui_contract_output_normalizer import (
    UiContractOutputNormalizer,
)
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.ui_contract import (
    UI_CONTRACT_LLM_JSON_SCHEMA,
    UiContractArtifact,
)
from app.services.llm_service import LlmService
from app.agents.requirement.ui_contract_generation_hardener import UiContractGenerationHardener


class UiContractAgent:
    """
    Generate the authoritative UI_CONTRACT for exactly one PAGE node.

    The contract is consumed by:
    1. requirement-review Mock Preview Runtime;
    2. prototype source-code generator;
    3. downstream Development Agent.

    Enterprise rule:
    The model is not allowed to freely decide page capability boundaries.
    A deterministic generation plan is built first, then the LLM output is
    normalized against that plan before strict Pydantic and Java validation.
    """

    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_template_renderer = PromptTemplateRenderer()
        self.generation_plan_builder = UiContractGenerationPlanBuilder()
        self.output_normalizer = UiContractOutputNormalizer()
        self.generation_hardener = UiContractGenerationHardener()

    async def run(self, request: AgentTaskRequest) -> UiContractArtifact:
        generation_plan = self.generation_plan_builder.build(request)

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._system_prompt(),
        )
        system_prompt = system_prompt + "\n\n" + self._contract_guard_prompt()

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(
                request,
                generation_plan,
            ),
        )
        user_prompt = user_prompt + "\n\n" + self._output_guard_prompt(
            generation_plan,
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="ui_contract_v1_output",
            output_json_schema=UI_CONTRACT_LLM_JSON_SCHEMA,
        )

        parsed = json.loads(self._strip_code_fence(llm_result.text))

        normalized = self.output_normalizer.normalize(
            parsed,
            generation_plan,
        )

        artifact = UiContractArtifact.model_validate(normalized)
        artifact = self.generation_hardener.harden(artifact, request)
        artifact = self._bind_server_identity(artifact, request)
        artifact.markdown = self._build_markdown(artifact)
        return artifact

    def _system_prompt(self) -> str:
        return "\n".join(
            [
                "你是 CodeMind Studio 的企业级 UI Contract Agent。",
                "你只为当前一个 PAGE 节点生成一份权威 UI_CONTRACT。",
                "该契约不是截图，也不是随意的 HTML/Vue 代码，而是 Mock Runtime、原型代码生成器和 Development Agent 共同使用的页面蓝图。",
                "你必须严格依据 PAGE_SPEC、当前 PAGE 下的 FEATURE 定义、模块上下文、导航白名单和已有上游资产生成。",
                "不得用常见后台模板替代真实业务需求，不得虚构不存在的功能点、页面、状态或接口。",
                "不得输出 JavaScript、表达式、事件代码、eval、script、onclick 或其他可执行片段。",
                "只输出 JSON。",
            ]
        )

    def _contract_guard_prompt(self) -> str:
        return "\n".join(
            [
                "【UI_CONTRACT 代码级强制规则】",
                "1. schemaVersion 固定为 1.0，artifactType 固定为 UI_CONTRACT。",
                "2. 一份资产只能描述当前一个 PAGE；不得输出 pages 数组或在一个契约中嵌入其他完整页面。",
                "3. page.componentTree 中每个 componentId 全局唯一。",
                "4. interactions 中每个 actionId 全局唯一，组件只能通过 actionIds 引用动作。",
                "5. interactions 中每个动作必须有 targetId；targetId 必须引用当前 page.componentTree 中真实存在的 componentId。",
                "6. FEATURE 必须通过 featureCoverage 与组件、动作建立可追踪绑定。",
                "7. 当前 PAGE 下每个有效 FEATURE 必须出现在 featureCoverage 中，不能遗漏。",
                "8. FEATURE 绑定只能通过 featureCoverage.componentIds 与 featureCoverage.actionIds 表达；禁止在组件、字段或动作对象中输出 featureKeys。",
                "9. mock 交互只能由 mockScenarios 描述，不能把 mock 函数或脚本写进组件。",
                "10. 页面跳转只能引用 navigationTargets 中的目标页面。",
                "11. NAVIGATE 动作只能使用 targetPageId；禁止在 interaction 中输出 targetPageKey、targetPageName、target_page_key 或 target_page_name。",
                "12. 页面 Key、名称和路由只能维护在 navigationTargets 中，不得重复塞入 interaction。",
                "13. 只有生成计划允许导航，并且 PAGE_SPEC 或当前 FEATURE 明确要求页面跳转时才允许生成 NAVIGATE。",
                "14. targetPageId 必须来自 allowedNavigationTargetIds，不得自行编造页面 ID、页面 Key 或页面名称。",
                "15. pageStates 中 stateKey 是页面状态，不是组件 ID；不得把 button、input、form 等 componentId 写成 stateKey。",
                "16. pageStates 只描述状态，不得包含 actions、stateActions、entryActions、exitActions、onEnter、onExit 或 transitions；状态切换统一由 interactions 和 mockScenarios 表达。",
                "17. API 尚未定义时 status 使用 MOCK 或 PLANNED，不得编造已正式确认的接口。",
                "18. qualitySummary 必须真实反映覆盖率、阻断项和警告，不得一律写 100 分。",
                "19. 组件的扩展展示参数只能放在 props 中，例如 props.text、props.items、props.message、props.level、props.rowActionIds。",
                "20. 子组件只能放在 children 中；TABS 通过 tabs[].componentIds 引用自身 children 中的组件。",
            ]
        )

    def _build_user_prompt(
        self,
        request: AgentTaskRequest,
        generation_plan: UiContractGenerationPlan,
    ) -> str:
        context_package = request.context_package or {}
        current_module = self._extract_current_module(request)
        feature_nodes = self._extract_list(context_package, "feature_nodes")
        navigation_candidates = self._extract_list(
            context_package,
            "navigation_candidates",
        )
        artifacts = self._normalize_artifacts(context_package.get("artifacts"))
        readiness = self._extract_readiness(request, context_package)
        theme_profile = self._extract_theme_profile(request, context_package)

        compact_context = {
            "project": context_package.get("project") or {},
            "current_page": current_module,
            "module_ancestry": context_package.get("module_ancestry") or [],
            "feature_nodes": feature_nodes,
            "navigation_candidates": navigation_candidates,
            "artifacts": {
                key: self._compact_value(value, 8000)
                for key, value in artifacts.items()
                if key
                in {
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                }
            },
            "ui_contract_readiness": readiness,
            "project_theme_profile": theme_profile,
        }

        return "\n".join(
            [
                "请为当前 PAGE 生成正式 UI_CONTRACT。",
                "",
                "【用户补充需求】",
                self._get_requirement_text(request) or "无额外补充需求。",
                "",
                "【项目级视觉风格配置】",
                json.dumps(
                    theme_profile or {},
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【系统生成计划：最高优先级】",
                json.dumps(
                    generation_plan.to_prompt_payload(),
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【完整生成上下文】",
                json.dumps(
                    compact_context,
                    ensure_ascii=False,
                    default=str,
                ),
                "",
                "【建模要求】",
                "- 先读取“系统生成计划”，严格在 allowedFeatureKeys、allowedNavigationTargetIds、requiredStateKeys 和 forbiddenTerms 边界内生成。",
                "- 再依据 PAGE_SPEC 确定页面目标、布局、字段、状态和操作。",
                "- 再逐项读取 feature_nodes，保证每个有效 FEATURE 被组件和动作覆盖。",
                "- FORM 页面必须定义字段、校验、提交动作以及成功和失败 Mock 场景。",
                "- 每个 interaction 必须包含 targetId；targetId 必须引用 page.componentTree 中真实存在的 componentId。",
                "- NAVIGATE 动作必须引用 allowedNavigationTargetIds 中的真实 targetPageId。",
                "- interaction 的 NAVIGATE 只能写 targetPageId，禁止写 targetPageKey 和 targetPageName。",
                "- 页面 Key、页面名称和路由只允许写入 navigationTargets。",
                "- 不得把登录成功、保存成功等动作自动解释为跳转首页、订单列表或工作台，除非系统生成计划明确允许。",
                "- 页面至少包含 requiredStateKeys 中列出的状态。",
                "- 状态动作只能引用 pageStates 中已有的 stateKey。",
                "- 核心按钮必须绑定 actionId，不能只画按钮不定义行为。",
                "- 不得把完整注册页、详情页等其他页面嵌入当前 PAGE。",
                "- 不得输出 forbiddenTerms 中涉及的功能、组件、动作、字段或页面。",
                "- 用户最终页面中不得展示组件说明、FEATURE 覆盖说明、Mock 说明、Contract 说明或开发者解释文案。",
                "- component.title 只有在确实是用户界面可见标题时才允许展示；纯说明性标题应放入 description、assumptions 或 qualitySummary。",
                "- 表单类页面默认单列排布；登录和注册页必须符合 Auth Page Pattern：账号/密码单列、主按钮整行、辅助跳转置于表单底部。",
                "- 列表页必须符合 Search/List Pattern：搜索筛选、主操作、表格/卡片列表和空状态边界清晰。",
                "- Dashboard 页面必须符合 Dashboard Pattern：指标卡、趋势/状态区域和关键操作分区清晰。",
                "- 页面视觉语气、密度、主色倾向和登录布局必须服从 project_theme_profile；没有配置时才按项目语义推断。",
                "- 不得输出任意脚本或框架代码。",
            ]
        )

    def _output_guard_prompt(
        self,
        generation_plan: UiContractGenerationPlan,
    ) -> str:
        return "\n".join(
            [
                "【输出要求】",
                "只输出一个符合 JSON Schema 的 JSON 对象。",
                "不要输出 Markdown、代码块、解释文字或额外字段。",
                "所有枚举值必须严格使用 Schema 中规定的大写值。",
                "interaction 中不得出现 targetPageKey、targetPageName、target_page_key、target_page_name。",
                "用户可见标题、按钮、字段占位符必须是面向最终用户的产品文案，不得使用“包含字段”“组件”“契约”“Mock”“原型”等开发说明。",
                "featureCoverage 中的 featureKey 必须来自 allowedFeatureKeys："
                + json.dumps(
                    generation_plan.allowed_feature_keys,
                    ensure_ascii=False,
                ),
                "所有 targetPageId 必须来自 allowedNavigationTargetIds："
                + json.dumps(
                    generation_plan.allowed_navigation_target_ids,
                    ensure_ascii=False,
                ),
                "不得输出 forbiddenTerms："
                + json.dumps(
                    generation_plan.forbidden_terms,
                    ensure_ascii=False,
                ),
            ]
        )

    def _bind_server_identity(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        current_module = self._extract_current_module(request)

        project_id = str(request.project_id)
        page_id = str(
            current_module.get("module_id")
            or current_module.get("id")
            or request.module_id
            or ""
        )
        page_key = str(
            current_module.get("module_key")
            or current_module.get("moduleKey")
            or current_module.get("key")
            or artifact.identity.pageKey
        )
        page_name = str(
            current_module.get("module_name")
            or current_module.get("moduleName")
            or current_module.get("name")
            or artifact.identity.pageName
        )
        page_kind = str(
            self._extract_extra(current_module, "pageKind")
            or current_module.get("pageKind")
            or artifact.identity.pageKind
        )

        identity = artifact.identity.model_copy(
            update={
                "projectId": project_id,
                "pageId": page_id,
                "pageKey": page_key,
                "pageName": page_name,
                "pageKind": page_kind,
            }
        )

        page = artifact.page.model_copy(
            update={
                "pageId": page_id,
                "pageKey": page_key,
                "pageName": page_name,
                "pageKind": page_kind,
            }
        )

        covered_feature_count = sum(
            1
            for item in artifact.featureCoverage
            if item.status in {"FULL", "PARTIAL", "NOT_APPLICABLE"}
        )
        coverage_rate = (
            100
            if not artifact.featureCoverage
            else round(
                covered_feature_count * 100 / len(artifact.featureCoverage)
            )
        )

        quality_summary = artifact.qualitySummary.model_copy(
            update={
                "coverageRate": coverage_rate,
            }
        )

        return artifact.model_copy(
            update={
                "schemaVersion": "1.0",
                "artifactType": "UI_CONTRACT",
                "identity": identity,
                "page": page,
                "qualitySummary": quality_summary,
            }
        )

    def _extract_current_module(
        self,
        request: AgentTaskRequest,
    ) -> dict[str, Any]:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        for source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
        ]:
            if isinstance(source, dict):
                return source

        return {
            "module_id": request.module_id,
            "module_key": "",
            "module_name": "",
            "module_type": "PAGE",
            "module_path": "",
            "extra": {},
        }

    def _extract_readiness(
        self,
        request: AgentTaskRequest,
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        input_payload = request.input or {}

        for value in [
            input_payload.get("ui_contract_readiness"),
            context_package.get("ui_contract_readiness"),
            (context_package.get("input") or {}).get("ui_contract_readiness")
            if isinstance(context_package.get("input"), dict)
            else None,
        ]:
            if isinstance(value, dict):
                return value

        return {}

    def _extract_theme_profile(
        self,
        request: AgentTaskRequest,
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        input_payload = request.input or {}

        for value in [
            input_payload.get("themeProfile"),
            input_payload.get("theme_profile"),
            input_payload.get("project_theme_profile"),
            context_package.get("project_theme_profile"),
            context_package.get("themeProfile"),
            (context_package.get("input") or {}).get("themeProfile")
            if isinstance(context_package.get("input"), dict)
            else None,
            (context_package.get("input") or {}).get("project_theme_profile")
            if isinstance(context_package.get("input"), dict)
            else None,
        ]:
            if isinstance(value, dict):
                return value

        return {}

    def _extract_list(
        self,
        source: dict[str, Any],
        key: str,
    ) -> list[dict[str, Any]]:
        value = source.get(key)

        if not isinstance(value, list):
            return []

        return [item for item in value if isinstance(item, dict)]

    def _normalize_artifacts(
        self,
        raw_artifacts: Any,
    ) -> dict[str, Any]:
        """
        Normalize artifact-map keys only.

        This method is unrelated to LLM output normalization. It converts keys
        such as "page_spec" to "PAGE_SPEC" for prompt context lookup.
        """
        if not isinstance(raw_artifacts, dict):
            return {}

        result: dict[str, Any] = {}

        for key, value in raw_artifacts.items():
            result[str(key).strip().upper()] = value

        return result

    def _compact_value(
        self,
        value: Any,
        max_chars: int,
    ) -> Any:
        text = json.dumps(value or {}, ensure_ascii=False, default=str)

        if len(text) <= max_chars:
            return value

        return {
            "_truncated": True,
            "excerpt": text[:max_chars],
            "originalLength": len(text),
        }

    def _get_requirement_text(
        self,
        request: AgentTaskRequest,
    ) -> str:
        input_payload = request.input or {}

        for key in ["requirement_text", "requirementText", "supplement"]:
            value = input_payload.get(key)
            if value:
                return str(value)

        return request.requirement_text or ""

    def _extract_extra(
        self,
        module: dict[str, Any],
        key: str,
    ) -> Any:
        extra = module.get("extra")
        if isinstance(extra, dict):
            return extra.get(key)
        return None

    def _build_markdown(
        self,
        artifact: UiContractArtifact,
    ) -> str:
        coverage_lines = [
            (
                f"- {item.featureName}（{item.featureKey}）：{item.status}；"
                f"组件 {len(item.componentIds)} 个，动作 {len(item.actionIds)} 个"
            )
            for item in artifact.featureCoverage
        ]

        interaction_lines = [
            f"- `{item.actionId}`：{item.type}，{item.label or '-'}"
            for item in artifact.interactions
        ]

        lines = [
            f"# {artifact.page.pageName}交互原型 / UI_CONTRACT",
            "",
            "## 1. 页面定位",
            f"- Page ID：`{artifact.page.pageId}`",
            f"- Page Key：`{artifact.page.pageKey}`",
            f"- Route：`{artifact.page.route}`",
            f"- Page Kind：`{artifact.page.pageKind}`",
            f"- Layout：`{artifact.page.layout.type}`",
            "",
            "## 2. FEATURE 覆盖",
            *(coverage_lines or ["- 当前页面未定义 FEATURE。"]),
            "",
            "## 3. 交互动作",
            *(interaction_lines or ["- 当前页面未定义交互动作。"]),
            "",
            "## 4. 质量摘要",
            f"- 质量分：{artifact.qualitySummary.score}",
            f"- FEATURE 覆盖率：{artifact.qualitySummary.coverageRate}%",
            f"- 阻断项：{len(artifact.qualitySummary.blockers)}",
            f"- 警告项：{len(artifact.qualitySummary.warnings)}",
            "",
            "## 5. 分析摘要",
            artifact.analysisSummary,
        ]

        return "\n".join(lines)

    def _strip_code_fence(
        self,
        text: str,
    ) -> str:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()
        elif cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()

        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()

        return cleaned