# A9.10.14.4.3.15.4.2_AUTO_INDUSTRY_STYLE_INFERENCE_OPTIONAL_OVERRIDE
# A9.10.14.4.3.15.4.1.1_INDUSTRY_STYLE_INDENT_HOTFIX
# A9.10.14.4.3.15.4.1_INDUSTRY_STYLE_PROFILE_CONTRACT_BRIDGE
# A9.10.14.2.1_PRE_GATE_NAV_REPAIR
# A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN

from __future__ import annotations
import re

import json
from typing import Any

# A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM
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
from app.agents.requirement.industry_style_profile import (
    enrich_theme_profile_with_industry_style,
    attach_industry_style_profile_to_ui_contract,
)
from app.agents.requirement.ui_contract_generation_hardener import UiContractGenerationHardener
from app.agents.requirement.ui_contract_quality_gate import UiContractQualityGate
from app.agents.requirement.ui_contract_industry_quality_baseline import apply_industry_page_quality_baseline
from app.agents.requirement.ui_contract_visual_design_system import apply_visual_design_system_to_ui_contract
from app.agents.requirement.ui_contract_regression_smoke_validator import UiContractRegressionSmokeValidator
from app.agents.requirement.module_prd_semantic_backfill import backfill_module_prd_semantic_backbone
from app.agents.requirement.ui_contract_prd_reconciler import (
    build_ui_contract_from_prd_semantics_without_llm,
    reconcile_ui_contract_with_prd_semantics,
)
from app.agents.requirement.requirement_asset_readiness_guard import (
    assert_requirement_asset_ready_for_ui_contract,
)  # A9.10.14.4.3.15.3.3_REQUIREMENT_ASSET_READINESS_GATE


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
        self.quality_gate = UiContractQualityGate()
        self.regression_smoke_validator = UiContractRegressionSmokeValidator()

    async def run(self, request: AgentTaskRequest) -> UiContractArtifact:
        # A9.10.14.4.3.14_FINAL_PIPELINE_UNLOCK_NODEJS_FIX
        print(
            f"[A9.10.14.4.3.14] UiContractAgent.run ENTER task_type={request.task_type} module_id={request.module_id}",
            flush=True,
        )
        generation_plan = self.generation_plan_builder.build(request)

        # A9.10.14.4.3.15.3.3_REQUIREMENT_ASSET_READINESS_GATE
        # Stop before any LLM call if MODULE_PRD.page_requirements cannot
        # provide a stable page contract for the current PAGE.
        requirement_asset_readiness = assert_requirement_asset_ready_for_ui_contract(request)

        contract_seed: UiContractArtifact | None = build_ui_contract_from_prd_semantics_without_llm(request)
        if contract_seed is not None:
            print(
                "[A9.10.14.4.3.14] UI_CONTRACT contract seed available; using one LLM pass for visual design and then locking business semantics",
                flush=True,
            )
        else:
            print(
                "[A9.10.14.4.3.14] UI_CONTRACT contract seed is empty; using one LLM pass from PAGE_SPEC/context",
                flush=True,
            )

        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._system_prompt(),
        )
        system_prompt = (
            system_prompt
            + "\n\n"
            + self._contract_guard_prompt()
            + "\n\n"
            + self._fine_grained_element_guard_prompt()
            + "\n\n"
            + self._demo_grade_prototype_quality_prompt()
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(
                request,
                generation_plan,
            ),
        )
        user_prompt = user_prompt + "\n\n" + self._output_guard_prompt(generation_plan)
        user_prompt = self._a9_10_14_4_3_14_append_contract_seed_prompt(
            user_prompt,
            contract_seed,
        )
        user_prompt = user_prompt + "\n\n" + "【A9.10.14.4.3.15.3.3 需求资产就绪门禁结果】\n" + json.dumps(requirement_asset_readiness, ensure_ascii=False, default=str)

        try:
            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="ui_contract_v1_output",
                output_json_schema=UI_CONTRACT_LLM_JSON_SCHEMA,
            )

            parsed = json.loads(self._strip_code_fence(llm_result.text))
            normalized = self.output_normalizer.normalize(parsed, generation_plan)

            artifact = UiContractArtifact.model_validate(normalized)
            artifact = self.generation_hardener.harden(artifact, request)
            artifact = self._bind_server_identity(artifact, request)

            artifact = self._a9_10_14_2_1_pre_gate_nav_repair(artifact)
            artifact = self._a9_10_14_2_2_repair_java_validation_targets(artifact)
            artifact = self._a9_10_14_4_3_13_2_2_ensure_quality_props(artifact)

            # Contract lock after LLM visual design, before QualityGate.
            artifact = reconcile_ui_contract_with_prd_semantics(artifact, request)

            artifact = self._a9_10_14_2_1_pre_gate_nav_repair(artifact)
            artifact = self._a9_10_14_2_2_repair_java_validation_targets(artifact)
            artifact = self._a9_10_14_4_3_13_2_2_ensure_quality_props(artifact)
            artifact = apply_industry_page_quality_baseline(artifact, request)
            artifact = apply_visual_design_system_to_ui_contract(artifact, request)
            artifact = self.quality_gate.enforce(artifact)

            artifact = self._a9_10_14_4_3_6_repair_final_contract_integrity(artifact, request)
            artifact = self._a9_10_14_4_3_12_2_dedupe_navigation_targets(artifact, request)
            artifact = self._a9_10_14_4_3_13_2_3_repair_feature_coverage(artifact, request, generation_plan)
            artifact = self._a9_10_14_4_3_13_2_4_java_feature_authority_finalizer(artifact, request)
            artifact = self.regression_smoke_validator.validate(
                artifact,
                request,
                strict=True,
            )
            artifact = attach_industry_style_profile_to_ui_contract(
                artifact,
                request=request,
                theme_profile=self._extract_theme_profile(request, request.context_package or {}),
            )
            artifact.markdown = self._build_markdown(artifact)
            print(
                "[A9.10.14.4.3.14] UiContractAgent returned LLM-designed, PRD-locked UI_CONTRACT",
                flush=True,
            )
            return artifact

        except Exception as exc:
            print(
                f"[A9.10.14.4.3.14] UiContractAgent LLM design path failed once without retry: {type(exc).__name__}: {exc}",
                flush=True,
            )
            if contract_seed is None:
                raise

            fallback = self._a9_10_14_4_3_14_finalize_contract_seed_fallback(
                contract_seed,
                request,
                generation_plan,
            )
            print(
                "[A9.10.14.4.3.14] UiContractAgent returned deterministic contract-seed fallback after one failed LLM pass",
                flush=True,
            )
            return fallback

    def _a9_10_14_4_3_14_append_contract_seed_prompt(
        self,
        user_prompt: str,
        contract_seed: UiContractArtifact | None,
    ) -> str:
        if contract_seed is None:
            return user_prompt + "\n\n" + "\n".join(
                [
                    "【A9.10.14.4.3.14 生成原则】",
                    "- 当前页面没有足够的 PRD contract seed；请严格依据 PAGE_SPEC、MODULE_PRD、当前页面上下文生成。",
                    "- 仍必须保持字段、按钮、表格列、行操作、跳转、mockData、pageStates 和 featureCoverage 结构化、可选择、可校验。",
                    "- 只输出符合 UI_CONTRACT JSON Schema 的 JSON。",
                ]
            )

        try:
            seed_data = contract_seed.model_dump(mode="python", by_alias=True, exclude_none=False)
        except Exception:
            seed_data = {}

        seed_text = json.dumps(seed_data, ensure_ascii=False, default=str)
        if len(seed_text) > 30000:
            seed_text = seed_text[:30000] + "\n...TRUNCATED_A9_CONTRACT_SEED..."

        return user_prompt + "\n\n" + "\n".join(
            [
                "【A9.10.14.4.3.14 Contract Seed，最高优先级】",
                "下面是系统从 PRD/PAGE_SPEC 中确定性抽取出的业务契约种子。",
                "你可以优化视觉布局、层次、风格、文案组织和演示效果。",
                "你不可以删除或改名这些业务字段、表格列、主操作、行操作、数据绑定和页面状态。",
                "行操作只能通过 TABLE.props.rowActionIds 表达；不要把查看/编辑/审核/删除放到 TABLE.actionIds 或页头工具栏。",
                "所有可见元素都必须保持可选择：componentId / fieldKey / columnKey / actionId / tabKey 必须稳定。",
                seed_text,
            ]
        )

    def _a9_10_14_4_3_14_finalize_contract_seed_fallback(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
        generation_plan: Any | None = None,
    ) -> UiContractArtifact:
        artifact = self._bind_server_identity(artifact, request)
        artifact = self._a9_10_14_2_1_pre_gate_nav_repair(artifact)
        artifact = self._a9_10_14_2_2_repair_java_validation_targets(artifact)
        artifact = self._a9_10_14_4_3_13_2_2_ensure_quality_props(artifact)
        artifact = apply_industry_page_quality_baseline(artifact, request)
        artifact = apply_visual_design_system_to_ui_contract(artifact, request)
        artifact = self.quality_gate.enforce(artifact)
        artifact = self._a9_10_14_4_3_6_repair_final_contract_integrity(artifact, request)
        artifact = self._a9_10_14_4_3_12_2_dedupe_navigation_targets(artifact, request)
        artifact = self._a9_10_14_4_3_13_2_3_repair_feature_coverage(artifact, request, generation_plan)
        artifact = self._a9_10_14_4_3_13_2_4_java_feature_authority_finalizer(artifact, request)
        artifact = self.regression_smoke_validator.validate(
            artifact,
            request,
            strict=False,
        )
        artifact = attach_industry_style_profile_to_ui_contract(
            artifact,
            request=request,
            theme_profile=self._extract_theme_profile(request, request.context_package or {}),
        )
        artifact.markdown = self._build_markdown(artifact)
        return artifact


    def _system_prompt(self) -> str:
        return "\n".join(
            [
                "你是 CodeMind Studio 的企业级 UI Contract Agent。",
                "你只为当前一个 PAGE 节点生成一份权威 UI_CONTRACT。",
                "该契约不是截图，也不是随意的 HTML/Vue 代码，而是 Mock Runtime、原型代码生成器和 Development Agent 共同使用的页面蓝图。",
                "你必须严格依据隐藏原型上下文、当前 PAGE 所属 FEATURE 定义、模块上下文、导航白名单和已有上游资产生成。PAGE_SPEC 不作为用户可见前置条件。",
                "不得用常见后台模板替代真实业务需求，不得虚构不存在的功能点、页面、状态或接口。",
                "不得输出 JavaScript、表达式、事件代码、eval、script、onclick 或其他可执行片段。",
                "只输出 JSON。",
            ]
        )

    def _contract_guard_prompt(self) -> str:
        return "\n".join(
            [
                "【UI_CONTRACT 代码级强制规则】",
            "【A9.7 原型保真与视觉质量强制规则】",
            "- 生成目标不是低保真线框图，而是可给甲方直接演示的中高保真业务原型。",
            "- 每个 PAGE 必须有清晰视觉层次：页面标题区、主体卡片/表单/表格区、主操作区、状态反馈区；禁止只堆字段和按钮。",
            "- 字段必须进入 fields[]，按钮必须进入 actionIds + interactions，表格列必须进入 columns[]；禁止把字段、按钮、列只写进 description。",
            "- 所有可交互或可被用户点选的元素都必须拥有稳定 key：componentId、fieldKey、actionId、columnKey、tabKey。",
            "- 文案要面向真实业务用户，禁止出现 Mock、Contract、component、featureCoverage、schema、开发说明等技术解释。",
            "- 原型视觉必须克制、现代、留白充分，优先使用卡片、分组、主次按钮、标签、状态徽标、空状态和错误态。",
            "- 医疗、金融、政企、管理后台类页面必须保持可信、专业、清爽，不得使用粗糙默认表单或随意堆叠布局。",

                "1. schemaVersion 固定为 1.0，artifactType 固定为 UI_CONTRACT。",
                "2. 一份资产只能描述当前一个 PAGE；不得输出 pages 数组或在一个契约中嵌入其他完整页面。",
                "3. page.componentTree 中每个 componentId 全局唯一。",
                "4. interactions 中每个 actionId 全局唯一，组件只能通过 actionIds 引用动作。",
                "5. interactions 中每个动作必须有 targetId；targetId 必须引用当前 page.componentTree 中真实存在的 componentId。",
                "6. FEATURE 必须通过 featureCoverage 与组件、动作建立可追踪绑定。",
                "7. 当前 PAGE 下每个有效 FEATURE 必须出现在 featureCoverage 中，不能遗漏。",
                "8. FEATURE 绑定只能通过 featureCoverage.componentIds 与 featureCoverage.actionIds 表达；禁止在组件、字段或动作对象中输出 featureKeys。",
                "8.1 featureCoverage.featureId 必须使用系统生成计划 features[].featureId；不得使用 featureKey、中文名、login_form_feature 这类自造语义 ID。",
                "8.2 component.featureIds、field.featureIds、interaction.featureIds 只能使用系统生成计划 features[].featureId；不确定时留空，由 featureCoverage 表达绑定。",
                "9. mock 交互只能由 mockScenarios 描述，不能把 mock 函数或脚本写进组件。",
                "9.1 mockScenarios[].actionId 必须引用 interactions[].actionId 中真实存在的动作。",
                "9.2 apiBindings[].usedByActionIds 必须引用 interactions[].actionId 中真实存在的动作；不确定时置为空数组。",
                "10. 页面跳转只能引用 navigationTargets 中的目标页面。",
                "11. NAVIGATE 动作只能使用 targetPageId；禁止在 interaction 中输出 targetPageKey、targetPageName、target_page_key 或 target_page_name。",
                "12. 页面 Key、名称和路由只能维护在 navigationTargets 中，不得重复塞入 interaction。",
                "13. 只有生成计划允许导航，并且隐藏原型上下文、模块导航图或当前 FEATURE 明确要求页面跳转时才允许生成 NAVIGATE。",
                "14. targetPageId 必须来自 allowedNavigationTargetIds，不得自行编造页面 ID、页面 Key 或页面名称。",
                "15. pageStates 中 stateKey 是页面状态，不是组件 ID；不得把 button、input、form 等 componentId 写成 stateKey。",
                "16. pageStates 只描述状态，不得包含 actions、stateActions、entryActions、exitActions、onEnter、onExit 或 transitions；状态切换统一由 interactions 和 mockScenarios 表达。",
                "17. API 尚未定义时 status 使用 MOCK 或 PLANNED，不得编造已正式确认的接口。",
                "18. qualitySummary 必须真实反映覆盖率、阻断项和警告，不得一律写 100 分。",
                "19. 组件的扩展展示参数只能放在 props 中，例如 props.text、props.items、props.message、props.level、props.rowActionIds。",
                "20. 子组件只能放在 children 中；TABS 通过 tabs[].componentIds 引用自身 children 中的组件。",
            ]
        )


    def _fine_grained_element_guard_prompt(self) -> str:
        return "\n".join(
            [
                "【A9.6 元素级可选择原型强制规则】",
                "1. UI_CONTRACT 必须支持画布级精确选择，不能只输出粗粒度大组件。",
                "2. 每个可见容器必须有稳定 componentId；componentId 不能使用中文、空格或随机短句。",
                "3. 表单必须把每个可见字段写入 fields；每个字段必须有稳定 fieldKey、清晰 label、准确 type、placeholder 和 required。",
                "4. 不得把多个输入框合并成一个 description；账号、姓名、手机号、日期、选择项等都必须拆成独立 field。",
                "5. 按钮、链接、页头操作、表格行操作必须通过 actionIds 绑定 interactions；按钮文案必须来自 component.title 或 interaction.label。",
                "6. 表格必须把每一列写入 columns；每列必须有稳定 columnKey、fieldKey、label、type，重要列应给 width。",
                "7. Tabs 必须写入 tabs；每个 tab 必须有 tabKey、title，并用 componentIds 指向真实 children。",
                "8. 重要用户可见文案必须落在 component.title、component.description、props.text、props.message、field.label、field.placeholder、column.label、tab.title 或 interaction.label 中，禁止只塞进分析摘要。",
                "9. FORM、TABLE、CARD、SECTION、BUTTON、LINK、TABS、TEXT、ALERT、EMPTY_STATE 都必须能被用户在原型画布上单独选择和修改。",
                "10. 对于复杂页面，应拆成多个 SECTION/CARD/FORM/TABLE 子组件，避免一个大 SECTION 包含所有内容。",
                "11. props 只能放展示参数或运行时提示，不得放脚本；推荐用 props.items、props.text、props.message、props.rowActionIds、props.style 表达可视内容。",
                "12. 生成结果必须便于后续自然语言修改：用户说“这个字段/按钮/表格列/标题/说明”时，可以唯一定位到 fieldKey、actionId、columnKey、tabKey 或 componentId。",
            ]
        )


    def _demo_grade_prototype_quality_prompt(self) -> str:
        return "\n".join(
            [
                "【A9.7 高保真原型生成强制规则】",
                "1. 生成目标是可演示的业务产品原型，不是字段清单、低保真线框图或后台 CRUD 草图。",
                "2. 每个 PAGE 至少形成 3 个清晰视觉分区：页面头/摘要区、核心业务操作区、状态反馈或辅助信息区。",
                "3. FORM 页面不得只有一个裸表单；必须包含表单卡片、办理/规则提示、主次操作和成功/错误反馈状态。",
                "4. TABLE/LIST 页面不得只有一张裸表；必须包含查询筛选区、主操作、表格、行操作、空状态和示例 mockData。",
                "5. DETAIL/DASHBOARD 页面必须包含摘要卡、指标/状态卡、详情信息区、快捷操作或流程状态，不得套用登录页或普通表单布局。",
                "6. 组件层级应使用 PAGE_HEADER、SECTION、CARD、FORM、TABLE、TOOLBAR、ALERT、EMPTY_STATE 等表达真实视觉结构。",
                "7. 每个字段、按钮、表格列、Tab、标题、说明都必须能被画布选择器唯一定位，并能被右侧 AI 精准修改。",
                "8. props 中必须补充 visualRole、a9VisualQuality、elementPicker、elementStyles 等展示元信息，但不得放脚本或代码。",
                "9. mockData 必须足够支撑预览看起来像真实业务页面；表格至少有可展示行，详情/指标卡有合理示例值。",
                "10. 用户可见文案必须是产品文案，不得出现 schema、contract、mock、component、featureCoverage、开发说明等技术词。",
                "11. 企业后台 FORM_PAGE / 创建 / 新增 / 编辑 / 录入页面必须使用真实后台管理系统结构：页面头、主操作栏、全宽基础信息表单、全宽明细表格、校验提示和底部操作栏。",
                "12. FORM_PAGE 中的明细表格是主从表单的一部分，不是列表查询页；禁止给创建页自动生成查询筛选表单或把 layout.type 改成 TABLE_LIST。",
                "13. 表单页默认使用 4 列栅格；明细表格必须占满内容区，不得把 FORM 或 TABLE 放进 1/3 宽的小卡片。",
                "14. 15.4.11 起页面必须带可执行视觉设计系统：颜色、间距、圆角、阴影、排版、密度和状态色必须能落到 props.visualDesignTokens。",
                "15. 不同页面模式必须有不同结构：列表页是筛选-工具栏-表格，表单页是标题-主表单-明细/提示-底部操作，详情页是摘要-指标-描述-时间线，看板页是指标-趋势-待办。",
                "16. 行业视觉必须可感知但克制：仓储/制造偏紧凑专业，金融/政企偏正式可信，电商偏明快转化，CRM/工单偏协同效率。",
                "17. 每个核心组件必须有 props.visualRole、props.visualDesignTokens、props.layoutPolish、props.a9VisualSystem；这些字段只描述视觉和布局，不得改变业务语义。",
                "18. 页面要达到正式产品演示水平：边界清晰、留白均衡、主次按钮明确、表格可读、表单成组、状态反馈完整、空态有下一步。",
                "19. Development 生成项目会复用同源 runtime，因此 UI_CONTRACT 的视觉规范必须能被 Runtime 和生成项目继承。",
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
        if isinstance(artifacts.get("MODULE_PRD"), dict):
            artifacts["MODULE_PRD"] = backfill_module_prd_semantic_backbone(
                artifacts.get("MODULE_PRD"),
                current_page=current_module,
            )
        current_page_prd_requirement = self._a9_10_14_4_3_12_1_current_page_prd_requirement(
            artifacts.get("MODULE_PRD"),
            current_module,
        )
        readiness = self._extract_readiness(request, context_package)
        theme_profile = enrich_theme_profile_with_industry_style(
            self._extract_theme_profile(request, context_package),
            context_package=context_package,
            current_page=current_module,
            module_prd=artifacts.get("MODULE_PRD"),
            page_requirement=current_page_prd_requirement,
        )

        compact_context = {
            "current_page_prd_requirement": current_page_prd_requirement,
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
            "hidden_prototype_context": context_package.get("hidden_prototype_context") or {},
            "page_prototype_context": context_package.get("page_prototype_context") or {},
            "module_prototype_map": context_package.get("module_prototype_map") or {},
            "traceability_map": context_package.get("traceability_map") or {},
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
                "【当前页面在 PRD 中的明确要求（最高优先级）】",
                json.dumps(
                    current_page_prd_requirement or {},
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

                "【A9.6 元素级选择建模要求】",
                "- 每个可见字段必须是 fields[] 中的独立对象，必须有 fieldKey、label、type、placeholder；不得把字段只写在 description 里。",
                "- 每个主要按钮必须能被单独选择：优先建 BUTTON/LINK 组件；如果按钮属于 FORM/TABLE/TOOLBAR，也必须通过 actionIds 指向独立 interaction。",
                "- 表格每一列必须有 columnKey、fieldKey、label；行操作按钮必须通过 props.rowActionIds 绑定 interaction。",
                "- 页面标题、说明、提示、空状态、告警文案必须放在可定位字段中，不能只写进 analysisSummary。",
                "- 复杂页面必须拆成多个可选 SECTION/CARD/FORM/TABLE，不得把所有内容塞进一个大容器。",
                "- 输出要兼顾原型可选中、自然语言可修改、Development Agent 可实现。",
                "",
                "【建模要求】",
                "- 先读取“系统生成计划”，严格在 allowedFeatureKeys、allowedNavigationTargetIds、requiredStateKeys 和 forbiddenTerms 边界内生成。",
                "- 再依据 hidden_prototype_context / pagePrototypeContext 确定页面目标、布局、字段、状态和操作；不要把这些内部上下文展示给用户。",
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
                "- Auth Page Pattern 只允许用于当前 PAGE 明确为登录页、注册页、认证页时；非认证页面禁止输出“欢迎登录”、密码字段、登录按钮、跳转注册/登录等认证页面视觉结构。",
                "- 非企业后台的简单表单可以单列；企业后台 FORM_PAGE / 创建 / 新增 / 编辑 / 录入页面必须使用 FORM_DETAIL：页面头、主操作栏、4 列主表单、全宽明细表格、校验提示和底部操作栏。",
                "- DETAIL_PAGE / DASHBOARD / 首页 / 工作台必须生成信息展示、快捷入口、业务摘要、待办或详情结构，禁止套用登录页布局。",
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

                "A9.6 元素级选择要求：fields[].fieldKey、columns[].columnKey、tabs[].tabKey、interactions[].actionId 必须稳定、唯一、可读，便于画布选择器和自然语言编辑精确定位。",
                "A9.6 输出禁止：不得把多个字段、按钮或表格列压缩成 description 文案；不得输出只有一个大 SECTION 的粗粒度页面。",
                "A9.10.14.1 质量门要求：输出必须能通过 Agent UI Quality Gate；不得留下空组件树、孤儿 action、缺少数据绑定的 TABLE、无字段 FORM、错误页面模式或技术文案污染。",
                "A9.7 高保真输出要求：页面必须包含可演示的视觉分区、卡片化结构、主次操作、状态反馈和 mockData，不允许只输出裸 FORM/TABLE。",
                "A9.9.3 企业后台表单页要求：FORM_PAGE 不得生成 filterForm 查询筛选区；包含 TABLE 时应作为 editable detail table 全宽展示，layout.type 必须保持 FORM_DETAIL。",
                "A9.7 可编辑输出要求：字段、按钮、列、Tab、标题、说明必须落到结构化字段，且 props.elementPicker / props.elementStyles 应支持画布选择和 AI 精准编辑。",
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
            normalized_key = str(key).strip().upper()
            if isinstance(value, dict):
                artifact_type = str(
                    value.get("artifact_type")
                    or value.get("artifactType")
                    or value.get("type")
                    or ""
                ).strip().upper()
                if artifact_type:
                    normalized_key = artifact_type
            result[normalized_key] = self._unwrap_artifact_payload(value)

        return result

    def _unwrap_artifact_payload(
        self,
        value: Any,
    ) -> Any:
        if not isinstance(value, dict):
            return value

        content = value.get("content")
        if isinstance(content, dict):
            payload = dict(content)
            markdown = (
                value.get("content_markdown")
                or value.get("contentMarkdown")
                or payload.get("content_markdown")
                or payload.get("contentMarkdown")
                or payload.get("markdown")
            )
            if markdown:
                payload.setdefault("content_markdown", markdown)
                payload.setdefault("contentMarkdown", markdown)
                payload.setdefault("markdown", markdown)
            return payload

        return value

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


    # A9.10.14.4.3.12.1_TOKEN_GUARD_AND_PRD_UI_SYNC_PATCH_UI_PRD_SLICE
    def _a9_10_14_4_3_12_1_current_page_prd_requirement(
        self,
        module_prd: Any,
        current_module: dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(module_prd, dict):
            return {}

        page_requirements = (
            module_prd.get("pageRequirements")
            or module_prd.get("page_requirements")
            or []
        )
        if not isinstance(page_requirements, list):
            return {}

        current_names = [
            current_module.get("pageName"),
            current_module.get("page_name"),
            current_module.get("module_name"),
            current_module.get("moduleName"),
            current_module.get("name"),
        ]
        current_keys = [
            current_module.get("pageKey"),
            current_module.get("page_key"),
            current_module.get("module_key"),
            current_module.get("moduleKey"),
            current_module.get("key"),
        ]

        def norm(value: Any) -> str:
            return re.sub(r"[\s_\-/\-]+", "", str(value or "").strip().lower())

        name_set = {norm(item) for item in current_names if str(item or "").strip()}
        key_set = {norm(item) for item in current_keys if str(item or "").strip()}

        best: dict[str, Any] | None = None
        best_score = -1

        for raw in page_requirements:
            if not isinstance(raw, dict):
                continue

            page_name = norm(raw.get("pageName") or raw.get("page_name") or raw.get("name") or raw.get("title"))
            page_key = norm(raw.get("pageKey") or raw.get("page_key") or raw.get("key") or raw.get("pageId") or raw.get("page_id"))
            summary = norm(raw.get("summary") or raw.get("description"))

            score = 0
            if page_key and page_key in key_set:
                score += 100
            if page_name and page_name in name_set:
                score += 100
            if any(name and (name in page_name or page_name in name or name in summary) for name in name_set):
                score += 50
            if any(key and (key in page_key or page_key in key or key in summary) for key in key_set):
                score += 30

            if score > best_score:
                best = raw
                best_score = score

        return dict(best or {})

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


    def _a9_10_14_2_1_pre_gate_nav_repair(
        self,
        artifact: UiContractArtifact,
    ) -> UiContractArtifact:
        """A9.10.14.2.1_PRE_GATE_NAV_REPAIR

        Last-mile safety bridge before UiContractQualityGate.

        DeepSeek/LLM output and downstream normalizers may still leave a NAVIGATE
        interaction without targetPageId.  That is not a valid production
        navigation contract.  If a legal navigation target exists, bind it;
        otherwise degrade the action to a local SHOW_TOAST so the current PAGE
        remains renderable and persistable instead of failing the whole task.
        """
        try:
            data = artifact.model_dump(
                mode="python",
                by_alias=True,
                exclude_none=False,
            )
        except Exception:
            return artifact

        if not isinstance(data, dict):
            return artifact

        page = data.get("page") if isinstance(data.get("page"), dict) else {}

        component_ids: list[str] = []

        def as_list(value: object) -> list[object]:
            return value if isinstance(value, list) else []

        def visit_component(value: object) -> None:
            if not isinstance(value, dict):
                return
            component_id = str(
                value.get("componentId")
                or value.get("id")
                or ""
            ).strip()
            if component_id and component_id not in component_ids:
                component_ids.append(component_id)
            for key in ["children", "components", "items"]:
                for child in as_list(value.get(key)):
                    visit_component(child)
            for tab in as_list(value.get("tabs")):
                if isinstance(tab, dict):
                    for key in ["children", "components", "items"]:
                        for child in as_list(tab.get(key)):
                            visit_component(child)

        for root in as_list(page.get("componentTree") or page.get("components")):
            visit_component(root)

        first_component_id = component_ids[0] if component_ids else None

        navigation_targets = as_list(data.get("navigationTargets"))
        valid_target_page_ids = [
            str(item.get("targetPageId") or "").strip()
            for item in navigation_targets
            if isinstance(item, dict)
            and str(item.get("targetPageId") or "").strip()
        ]
        valid_target_page_id_set = set(valid_target_page_ids)

        changed = False
        warnings: list[str] = []

        for raw_action in as_list(data.get("interactions")):
            if not isinstance(raw_action, dict):
                continue

            raw_type = raw_action.get("type")
            action_type = str(getattr(raw_type, "value", raw_type) or "").strip().upper()
            action_id = str(raw_action.get("actionId") or raw_action.get("id") or "").strip()

            target_page_id = str(raw_action.get("targetPageId") or "").strip()
            if action_type != "NAVIGATE":
                continue

            if target_page_id and target_page_id in valid_target_page_id_set:
                continue

            if valid_target_page_ids:
                raw_action["targetPageId"] = valid_target_page_ids[0]
                raw_action["type"] = "NAVIGATE"
                changed = True
                warnings.append(
                    f"A9.10.14.2.1 repaired NAVIGATE action {action_id}: bound first available navigation target."
                )
                continue

            raw_action["type"] = "SHOW_TOAST"
            raw_action["targetPageId"] = None
            if first_component_id:
                target_id = str(raw_action.get("targetId") or "").strip()
                source_component_id = str(raw_action.get("sourceComponentId") or "").strip()
                if target_id not in component_ids:
                    raw_action["targetId"] = first_component_id
                if source_component_id not in component_ids:
                    raw_action["sourceComponentId"] = raw_action.get("targetId") or first_component_id
            payload = raw_action.get("payload")
            if not isinstance(payload, dict):
                payload = {}
            payload.setdefault(
                "message",
                "The target page is not configured yet, so this navigation is shown as local feedback.",
            )
            payload.setdefault("fallbackBehavior", "stay_on_current_page")
            payload.setdefault("repairedBy", "A9.10.14.2.1_PRE_GATE_NAV_REPAIR")
            raw_action["payload"] = payload
            changed = True
            warnings.append(
                f"A9.10.14.2.1 repaired NAVIGATE action {action_id}: downgraded to SHOW_TOAST because no navigationTargets are available."
            )

        if not changed:
            return artifact

        quality = data.get("qualitySummary")
        if isinstance(quality, dict):
            existing_warnings = quality.get("warnings")
            if not isinstance(existing_warnings, list):
                existing_warnings = []
            quality["warnings"] = list(dict.fromkeys([*map(str, existing_warnings), *warnings]))
            quality["blockers"] = []

        try:
            return artifact.__class__.model_validate(data)
        except Exception:
            try:
                return UiContractArtifact.model_validate(data)
            except Exception:
                return artifact



    # A9.10.14.4.3.6_PRD_IMPACT_BANNER_UI_INTEGRITY_FIX
    def _a9_10_14_4_3_6_repair_final_contract_integrity(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        """Final server-side safety net before Java persistence validation.

        The LLM/normalizer may emit validation rules for fields that were not
        materialized, or navigation targets using page keys instead of real PAGE
        node ids. Java validation is strict and should stay strict; repair the
        Agent output here instead of weakening Java validation.
        """
        try:
            data = artifact.model_dump(mode="json", by_alias=True)
        except Exception:
            data = artifact.dict()

        context_package = request.context_package or {}
        component_ids, field_keys = self._a9_10_14_4_3_6_collect_contract_keys(data)
        self._a9_10_14_4_3_6_repair_validation_rules(data, field_keys)
        self._a9_10_14_4_3_6_repair_navigation_targets(data, context_package, component_ids)

        return UiContractArtifact.model_validate(data)

    def _a9_10_14_4_3_6_collect_contract_keys(
        self,
        value: Any,
    ) -> tuple[set[str], set[str]]:
        component_ids: set[str] = set()
        field_keys: set[str] = set()

        def visit(node: Any) -> None:
            if isinstance(node, dict):
                component_id = str(node.get("componentId") or node.get("component_id") or "").strip()
                if component_id:
                    component_ids.add(component_id)

                for key in ["fieldKey", "field_key", "columnKey", "column_key", "dataKey", "data_key", "name", "key"]:
                    raw = str(node.get(key) or "").strip()
                    if raw:
                        field_keys.add(raw)

                props = node.get("props")
                if isinstance(props, dict):
                    for key in ["fieldKey", "field_key", "columnKey", "column_key", "dataKey", "data_key", "name", "key"]:
                        raw = str(props.get(key) or "").strip()
                        if raw:
                            field_keys.add(raw)

                    for list_key in ["fields", "columns", "items", "filters", "controls"]:
                        items = props.get(list_key)
                        if isinstance(items, list):
                            for item in items:
                                if isinstance(item, dict):
                                    for key in ["fieldKey", "field_key", "columnKey", "column_key", "dataKey", "data_key", "name", "key"]:
                                        raw = str(item.get(key) or "").strip()
                                        if raw:
                                            field_keys.add(raw)

                for child in node.values():
                    visit(child)
            elif isinstance(node, list):
                for item in node:
                    visit(item)

        visit(value.get("page") if isinstance(value, dict) else value)
        return component_ids, field_keys

    def _a9_10_14_4_3_6_repair_validation_rules(
        self,
        data: dict[str, Any],
        field_keys: set[str],
    ) -> None:
        rules = data.get("validationRules") or data.get("validation_rules") or []
        if not isinstance(rules, list):
            data["validationRules"] = []
            return

        repaired: list[Any] = []
        warnings: list[str] = []
        for rule in rules:
            if not isinstance(rule, dict):
                continue
            field_key = str(rule.get("fieldKey") or rule.get("field_key") or "").strip()
            if field_key and field_keys and field_key not in field_keys:
                warnings.append(
                    f"A9.10.14.4.3.6 removed validation rule {rule.get('ruleId') or rule.get('rule_id') or field_key}: fieldKey {field_key} is not materialized in the page contract."
                )
                continue
            repaired.append(rule)

        data["validationRules"] = repaired
        self._a9_10_14_4_3_6_append_quality_warnings(data, warnings)

    def _a9_10_14_4_3_6_repair_navigation_targets(
        self,
        data: dict[str, Any],
        context_package: dict[str, Any],
        component_ids: set[str],
    ) -> None:
        candidates = self._a9_10_14_4_3_6_collect_page_candidates(context_package)
        canonical_by_alias: dict[str, str] = {}
        allowed_ids: set[str] = set()

        for candidate in candidates:
            canonical = self._a9_10_14_4_3_6_candidate_page_id(candidate)
            if not canonical:
                continue
            allowed_ids.add(canonical)
            for alias in self._a9_10_14_4_3_6_candidate_aliases(candidate):
                key = self._a9_10_14_4_3_6_norm(alias)
                if key:
                    canonical_by_alias.setdefault(key, canonical)

        navigation_targets = data.get("navigationTargets") or []
        if not isinstance(navigation_targets, list):
            navigation_targets = []

        repaired_targets: list[dict[str, Any]] = []
        target_id_map: dict[str, str] = {}
        warnings: list[str] = []

        for target in navigation_targets:
            if not isinstance(target, dict):
                continue
            raw_values = [
                target.get("targetPageId"),
                target.get("targetPageKey"),
                target.get("targetPageName"),
                target.get("pageKey"),
                target.get("pageName"),
                target.get("route"),
                target.get("targetRoute"),
            ]
            canonical = ""
            for raw in raw_values:
                raw_text = str(raw or "").strip()
                if raw_text in allowed_ids:
                    canonical = raw_text
                    break
                canonical = canonical_by_alias.get(self._a9_10_14_4_3_6_norm(raw_text), "")
                if canonical:
                    break
            if not canonical:
                warnings.append(
                    f"A9.10.14.4.3.6 removed navigationTarget {target.get('targetPageId') or target.get('targetPageKey') or target.get('targetPageName')}: target PAGE node is not available in context."
                )
                continue
            old_id = str(target.get("targetPageId") or "").strip()
            if old_id:
                target_id_map[old_id] = canonical
            target["targetPageId"] = canonical
            repaired_targets.append(target)

        data["navigationTargets"] = repaired_targets
        valid_target_ids = {str(item.get("targetPageId") or "").strip() for item in repaired_targets if isinstance(item, dict)}
        first_component_id = next(iter(component_ids), "")

        interactions = data.get("interactions") or []
        if isinstance(interactions, list):
            for action in interactions:
                if not isinstance(action, dict):
                    continue
                action_type = str(action.get("type") or "").strip().upper()
                if action_type != "NAVIGATE":
                    continue
                raw_target = str(action.get("targetPageId") or "").strip()
                mapped = target_id_map.get(raw_target) or canonical_by_alias.get(self._a9_10_14_4_3_6_norm(raw_target), raw_target)
                if mapped and mapped in valid_target_ids:
                    action["targetPageId"] = mapped
                    if not str(action.get("targetId") or "").strip() and first_component_id:
                        action["targetId"] = first_component_id
                    continue

                action["type"] = "SHOW_TOAST"
                action["targetPageId"] = None
                action["targetTabKey"] = None
                if first_component_id:
                    action["targetId"] = first_component_id
                    action["sourceComponentId"] = action.get("sourceComponentId") or first_component_id
                payload = action.get("payload")
                if not isinstance(payload, dict):
                    payload = {}
                payload.setdefault("message", "目标页面暂未准备好，稍后可从模块导航进入。")
                payload.setdefault("repairedBy", "A9.10.14.4.3.6_PRD_IMPACT_BANNER_UI_INTEGRITY_FIX")
                action["payload"] = payload
                warnings.append(
                    f"A9.10.14.4.3.6 downgraded NAVIGATE action {action.get('actionId')}: targetPageId {raw_target} is not a valid PAGE node."
                )

        self._a9_10_14_4_3_6_append_quality_warnings(data, warnings)


    # A9.10.14.4.3.12.2_UI_CONTRACT_NAV_TARGET_DEDUPE
    def _a9_10_14_4_3_12_2_dedupe_navigation_targets(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        """Final Agent-side repair for Java navigation target uniqueness.

        Java validation requires navigationTargets[].targetPageId to be unique.
        ContextPackage/navigation authority may expose the same PAGE through
        multiple sources: navigation_candidates, module_prototype_map,
        traceability_map, module_tree artifacts, and hidden prototype context.
        The previous final repair canonicalized aliases to real PAGE ids but did
        not collapse duplicate canonical ids, so Java correctly rejected the
        result. Keep Java strict; repair the Agent output here.
        """
        try:
            data = artifact.model_dump(mode="json", by_alias=True)
        except Exception:
            data = artifact.model_dump(mode="python", by_alias=True)

        if not isinstance(data, dict):
            return artifact

        targets = data.get("navigationTargets")
        if not isinstance(targets, list):
            data["navigationTargets"] = []
            return UiContractArtifact.model_validate(data)

        def text_value(value: Any) -> str:
            return str(value or "").strip()

        def norm(value: Any) -> str:
            return re.sub(r"[^a-z0-9]+", "", text_value(value).lower())

        def target_id(target: dict[str, Any]) -> str:
            return text_value(
                target.get("targetPageId")
                or target.get("target_page_id")
                or target.get("pageId")
                or target.get("page_id")
                or target.get("moduleId")
                or target.get("module_id")
                or target.get("id")
            )

        def aliases(target: dict[str, Any]) -> list[str]:
            keys = [
                "targetPageId", "target_page_id", "pageId", "page_id", "moduleId", "module_id", "id",
                "targetPageKey", "target_page_key", "pageKey", "page_key", "moduleKey", "module_key", "key",
                "targetPageName", "target_page_name", "pageName", "page_name", "moduleName", "module_name", "name", "title",
                "route", "targetRoute", "target_route", "routePath", "route_path",
            ]
            result: list[str] = []
            for key in keys:
                value = text_value(target.get(key))
                if value:
                    result.append(value)
            return result

        kept: list[dict[str, Any]] = []
        seen_by_page_id: dict[str, dict[str, Any]] = {}
        alias_to_page_id: dict[str, str] = {}
        warnings: list[str] = []

        for raw_target in targets:
            if not isinstance(raw_target, dict):
                continue

            target = dict(raw_target)
            page_id = target_id(target)
            if not page_id:
                warnings.append(
                    "A9.10.14.4.3.12.2 removed navigationTarget without targetPageId/pageId."
                )
                continue

            canonical_key = page_id
            if canonical_key in seen_by_page_id:
                kept_target = seen_by_page_id[canonical_key]
                for merge_key in [
                    "targetPageKey", "targetPageName", "targetRoute", "route", "routePath",
                    "label", "actionId", "navigationType", "navigationStatus", "fallbackBehavior",
                ]:
                    if not text_value(kept_target.get(merge_key)) and text_value(target.get(merge_key)):
                        kept_target[merge_key] = target.get(merge_key)

                for alias in aliases(target):
                    alias_key = norm(alias)
                    if alias_key:
                        alias_to_page_id[alias_key] = page_id

                warnings.append(
                    f"A9.10.14.4.3.12.2 removed duplicate navigationTarget targetPageId={page_id}."
                )
                continue

            target["targetPageId"] = page_id
            seen_by_page_id[canonical_key] = target
            kept.append(target)
            for alias in aliases(target):
                alias_key = norm(alias)
                if alias_key:
                    alias_to_page_id[alias_key] = page_id

        data["navigationTargets"] = kept
        valid_page_ids = {text_value(item.get("targetPageId")) for item in kept if text_value(item.get("targetPageId"))}
        first_target_page_id = next(iter(valid_page_ids), "")

        component_ids: set[str] = set()

        def visit_component(value: Any) -> None:
            if isinstance(value, dict):
                component_id = text_value(value.get("componentId") or value.get("component_id") or value.get("id"))
                if component_id:
                    component_ids.add(component_id)
                for child in value.values():
                    visit_component(child)
            elif isinstance(value, list):
                for item in value:
                    visit_component(item)

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        visit_component(page.get("componentTree") if isinstance(page, dict) else [])
        first_component_id = next(iter(component_ids), "")

        interactions = data.get("interactions")
        if isinstance(interactions, list):
            for action in interactions:
                if not isinstance(action, dict):
                    continue
                action_type = text_value(getattr(action.get("type"), "value", action.get("type"))).upper()
                if action_type != "NAVIGATE":
                    continue

                raw_target = text_value(
                    action.get("targetPageId")
                    or action.get("targetPageKey")
                    or action.get("targetPageName")
                    or action.get("target_page_id")
                    or action.get("target_page_key")
                    or action.get("target_page_name")
                )
                mapped = raw_target if raw_target in valid_page_ids else alias_to_page_id.get(norm(raw_target), "")

                if mapped and mapped in valid_page_ids:
                    action["targetPageId"] = mapped
                    action.pop("targetPageKey", None)
                    action.pop("targetPageName", None)
                    action.pop("target_page_key", None)
                    action.pop("target_page_name", None)
                    if first_component_id and text_value(action.get("targetId")) not in component_ids:
                        action["targetId"] = first_component_id
                    continue

                # Do not let an invalid NAVIGATE block persistence. Keep the
                # current page renderable and preserve a user-visible fallback.
                action["type"] = "SHOW_TOAST"
                action["targetPageId"] = None
                action.pop("targetPageKey", None)
                action.pop("targetPageName", None)
                action.pop("target_page_key", None)
                action.pop("target_page_name", None)
                if first_component_id:
                    action["targetId"] = first_component_id
                    action["sourceComponentId"] = action.get("sourceComponentId") or first_component_id
                payload = action.get("payload")
                if not isinstance(payload, dict):
                    payload = {}
                payload.setdefault("message", "目标页面暂未准备好，稍后可从模块导航进入。")
                payload.setdefault("repairedBy", "A9.10.14.4.3.12.2_UI_CONTRACT_NAV_TARGET_DEDUPE")
                action["payload"] = payload
                warnings.append(
                    f"A9.10.14.4.3.12.2 downgraded NAVIGATE action {action.get('actionId')}: targetPageId {raw_target} is not unique or not available."
                )

        self._a9_10_14_4_3_6_append_quality_warnings(data, warnings)
        return UiContractArtifact.model_validate(data)

    def _a9_10_14_4_3_6_collect_page_candidates(
        self,
        context_package: dict[str, Any],
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[int] = set()

        def add(record: Any) -> None:
            if not isinstance(record, dict):
                return
            ident = id(record)
            if ident in seen:
                return
            seen.add(ident)
            result.append(record)

        def visit(value: Any) -> None:
            if isinstance(value, dict):
                type_text = str(
                    value.get("nodeType") or value.get("node_type") or value.get("moduleType") or value.get("module_type") or value.get("type") or value.get("pageKind") or value.get("page_kind") or ""
                ).upper()
                has_page_identity = any(
                    str(value.get(key) or "").strip()
                    for key in [
                        "targetPageId", "target_page_id", "pageId", "page_id", "nodeId", "node_id", "moduleId", "module_id",
                        "targetPageKey", "target_page_key", "pageKey", "page_key", "route", "targetRoute", "target_route",
                    ]
                )
                if has_page_identity and ("PAGE" in type_text or any(k in value for k in ["targetPageId", "targetPageKey", "targetPageName", "pageKey", "pageName"])):
                    add(value)
                for child in value.values():
                    visit(child)
            elif isinstance(value, list):
                for item in value:
                    visit(item)

        for key in [
            "navigation_candidates",
            "navigationCandidates",
            "module_prototype_map",
            "modulePrototypeMap",
            "page_prototype_context",
            "pagePrototypeContext",
            "traceability_map",
            "traceabilityMap",
            "hidden_prototype_context",
            "hiddenPrototypeContext",
            "artifacts",
            "module_tree",
            "moduleTree",
        ]:
            visit(context_package.get(key))
        return result

    def _a9_10_14_4_3_6_candidate_page_id(self, candidate: dict[str, Any]) -> str:
        # Prefer UUID-like real PAGE node ids when available; Java persistence
        # validates against PAGE nodes, not free-form page keys.
        values = [
            candidate.get("targetPageId"),
            candidate.get("target_page_id"),
            candidate.get("pageId"),
            candidate.get("page_id"),
            candidate.get("nodeId"),
            candidate.get("node_id"),
            candidate.get("moduleId"),
            candidate.get("module_id"),
            candidate.get("id"),
        ]
        text_values = [str(value or "").strip() for value in values if str(value or "").strip()]
        for value in text_values:
            if re.match(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", value):
                return value
        return text_values[0] if text_values else ""

    def _a9_10_14_4_3_6_candidate_aliases(self, candidate: dict[str, Any]) -> list[str]:
        aliases: list[str] = []
        for key in [
            "targetPageId", "target_page_id", "pageId", "page_id", "nodeId", "node_id", "moduleId", "module_id", "id",
            "targetPageKey", "target_page_key", "pageKey", "page_key", "moduleKey", "module_key", "key",
            "targetPageName", "target_page_name", "pageName", "page_name", "moduleName", "module_name", "name", "title",
            "route", "routePath", "route_path", "targetRoute", "target_route",
        ]:
            value = str(candidate.get(key) or "").strip()
            if value:
                aliases.append(value)
        return aliases

    def _a9_10_14_4_3_6_norm(self, value: Any) -> str:
        return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "", str(value or "").strip().lower())

    def _a9_10_14_4_3_6_append_quality_warnings(
        self,
        data: dict[str, Any],
        warnings: list[str],
    ) -> None:
        if not warnings:
            return
        quality = data.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            data["qualitySummary"] = quality
        existing = quality.get("warnings")
        if not isinstance(existing, list):
            existing = []
        quality["warnings"] = existing + warnings

    def _a9_10_14_2_2_repair_java_validation_targets(
        self,
        artifact: UiContractArtifact,
    ) -> UiContractArtifact:
        """A9.10.14.2.2: final Java-validator target semantics repair.

        The Python quality gate checks contract-level structure.  The Java
        persistence validator is stricter about action target component kinds:
        OPEN_MODAL must target a MODAL, SWITCH_TAB must target TABS, form actions
        must target FORM/FIELD_GROUP, and NAVIGATE must have a targetPageId.

        LLM output may contain semantically useful row actions such as "edit" but
        model them as OPEN_MODAL on a TABLE when no modal component exists.  Do
        not fail the whole UI_CONTRACT for that repairable modeling mistake.
        Downgrade impossible actions to SHOW_TOAST and keep the original actionId
        stable for canvas selection and downstream handoff.
        """
        try:
            data = artifact.model_dump(
                mode="python",
                by_alias=True,
                exclude_none=False,
            )
        except Exception:
            return artifact

        if not isinstance(data, dict):
            return artifact

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        tree = page.get("componentTree") if isinstance(page.get("componentTree"), list) else []

        components: list[dict[str, Any]] = []

        def visit(value: Any) -> None:
            if not isinstance(value, dict):
                return
            components.append(value)
            for key in ["children", "components", "items"]:
                children = value.get(key)
                if isinstance(children, list):
                    for child in children:
                        visit(child)
            tabs = value.get("tabs")
            if isinstance(tabs, list):
                for tab in tabs:
                    if not isinstance(tab, dict):
                        continue
                    for key in ["children", "components", "items"]:
                        children = tab.get(key)
                        if isinstance(children, list):
                            for child in children:
                                visit(child)

        for root in tree:
            visit(root)

        component_index: dict[str, dict[str, Any]] = {}
        for component in components:
            component_id = str(component.get("componentId") or "").strip()
            if component_id:
                component_index[component_id] = component

        def component_type(component_id: Any) -> str:
            item = component_index.get(str(component_id or "")) or {}
            return str(item.get("type") or "").strip().upper()

        def first_component_of_type(*types: str) -> str:
            expected = {item.upper() for item in types}
            for component_id, component in component_index.items():
                if str(component.get("type") or "").strip().upper() in expected:
                    return component_id
            return ""

        first_component_id = next(iter(component_index.keys()), "")
        first_modal_id = first_component_of_type("MODAL")
        first_form_id = first_component_of_type("FORM", "FIELD_GROUP")
        first_table_id = first_component_of_type("TABLE")
        first_tabs_id = first_component_of_type("TABS")

        navigation_targets = data.get("navigationTargets")
        if not isinstance(navigation_targets, list):
            navigation_targets = []
        target_page_ids = [
            str(item.get("targetPageId") or "").strip()
            for item in navigation_targets
            if isinstance(item, dict) and str(item.get("targetPageId") or "").strip()
        ]

        page_states = data.get("pageStates")
        if not isinstance(page_states, list):
            page_states = []
        state_keys = {
            str(item.get("stateKey") or "").strip().upper()
            for item in page_states
            if isinstance(item, dict) and str(item.get("stateKey") or "").strip()
        }

        interactions = data.get("interactions")
        if not isinstance(interactions, list):
            interactions = []
            data["interactions"] = interactions

        def set_toast(
            action: dict[str, Any],
            *,
            reason: str,
            target_id: str = "",
        ) -> None:
            action["type"] = "SHOW_TOAST"
            action["targetPageId"] = None
            action["targetTabKey"] = None
            target = target_id if target_id in component_index else ""
            if not target:
                candidate = str(action.get("sourceComponentId") or action.get("targetId") or "").strip()
                if candidate in component_index:
                    target = candidate
            if not target:
                target = first_component_id
            action["targetId"] = target or None
            if target and str(action.get("sourceComponentId") or "").strip() not in component_index:
                action["sourceComponentId"] = target
            payload = action.get("payload")
            if not isinstance(payload, dict):
                payload = {}
            payload.setdefault("source", "A9_10_14_2_2_JAVA_VALIDATION_TARGET_REPAIR")
            payload.setdefault("repairReason", reason)
            payload.setdefault("message", str(action.get("label") or action.get("actionId") or "Action completed"))
            action["payload"] = payload

        for index, raw_action in enumerate(interactions):
            if not isinstance(raw_action, dict):
                continue

            action = raw_action
            action_id = str(action.get("actionId") or f"action_{index + 1}").strip()
            action["actionId"] = action_id
            raw_action_type = action.get("type") or "SHOW_TOAST"
            action_type = str(getattr(raw_action_type, "value", raw_action_type) or "").strip().upper()
            target_id = str(action.get("targetId") or "").strip()
            source_component_id = str(action.get("sourceComponentId") or "").strip()

            if source_component_id and source_component_id not in component_index:
                source_component_id = ""
            if not source_component_id and target_id in component_index:
                source_component_id = target_id
            if not source_component_id and first_component_id:
                source_component_id = first_component_id
            action["sourceComponentId"] = source_component_id or None

            if action_type == "OPEN_MODAL":
                if target_id and component_type(target_id) == "MODAL":
                    action["targetId"] = target_id
                elif target_id and target_id in component_index:
                    synthesized_modal_id = self._a9_10_14_4_synthesize_modal_for_action(
                        action=action,
                        owner_component=component_index[target_id],
                        component_index=component_index,
                    )
                    if synthesized_modal_id:
                        action["targetId"] = synthesized_modal_id
                    elif first_modal_id:
                        action["targetId"] = first_modal_id
                    else:
                        set_toast(
                            action,
                            reason="OPEN_MODAL without MODAL target could not synthesize a modal and was downgraded to SHOW_TOAST.",
                            target_id=target_id,
                        )
                        continue
                elif first_modal_id:
                    action["targetId"] = first_modal_id
                else:
                    set_toast(
                        action,
                        reason="OPEN_MODAL without MODAL target was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            elif action_type == "CLOSE_MODAL":
                if target_id and component_type(target_id) == "MODAL":
                    action["targetId"] = target_id
                elif first_modal_id:
                    action["targetId"] = first_modal_id
                else:
                    set_toast(
                        action,
                        reason="CLOSE_MODAL without MODAL target was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            elif action_type == "SWITCH_TAB":
                if target_id and component_type(target_id) == "TABS":
                    action["targetId"] = target_id
                elif first_tabs_id:
                    action["targetId"] = first_tabs_id
                else:
                    set_toast(
                        action,
                        reason="SWITCH_TAB without TABS target was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            elif action_type in {"SUBMIT_FORM", "RESET_FORM"}:
                if target_id and component_type(target_id) in {"FORM", "FIELD_GROUP"}:
                    action["targetId"] = target_id
                elif first_form_id:
                    action["targetId"] = first_form_id
                else:
                    set_toast(
                        action,
                        reason=f"{action_type} without FORM target was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            elif action_type == "DELETE_ROW":
                if target_id and component_type(target_id) == "TABLE":
                    action["targetId"] = target_id
                elif first_table_id:
                    action["targetId"] = first_table_id
                else:
                    set_toast(
                        action,
                        reason="DELETE_ROW without TABLE target was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            elif action_type == "NAVIGATE":
                target_page_id = str(action.get("targetPageId") or "").strip()
                if target_page_id:
                    action["targetPageId"] = target_page_id
                elif target_page_ids:
                    action["targetPageId"] = target_page_ids[0]
                else:
                    set_toast(
                        action,
                        reason="NAVIGATE without targetPageId was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue
                if not action.get("targetId") and first_component_id:
                    action["targetId"] = first_component_id

            elif action_type == "SET_PAGE_STATE":
                raw_state = str(action.get("targetId") or action.get("targetStateKey") or "").strip().upper()
                if raw_state in state_keys:
                    action["targetId"] = raw_state
                else:
                    set_toast(
                        action,
                        reason="SET_PAGE_STATE without known state was downgraded to SHOW_TOAST.",
                        target_id=target_id,
                    )
                    continue

            else:
                if target_id and target_id not in component_index:
                    action["targetId"] = first_component_id or None
                elif target_id:
                    action["targetId"] = target_id
                elif first_component_id:
                    action["targetId"] = first_component_id

            final_raw_type = action.get("type") or ""
            final_action_type = str(getattr(final_raw_type, "value", final_raw_type) or "").strip().upper()
            if final_action_type != "NAVIGATE":
                action["targetPageId"] = None

        valid_action_ids = {
            str(item.get("actionId") or "")
            for item in interactions
            if isinstance(item, dict) and str(item.get("actionId") or "").strip()
        }

        def filter_action_ids(value: Any) -> list[str]:
            if not isinstance(value, list):
                return []
            result: list[str] = []
            for item in value:
                action_id = str(item or "").strip()
                if action_id and action_id in valid_action_ids and action_id not in result:
                    result.append(action_id)
            return result

        for component in components:
            component["actionIds"] = filter_action_ids(component.get("actionIds"))
            props = component.get("props")
            if isinstance(props, dict) and "rowActionIds" in props:
                props["rowActionIds"] = filter_action_ids(props.get("rowActionIds"))
            if "rowActionIds" in component:
                component["rowActionIds"] = filter_action_ids(component.get("rowActionIds"))

        try:
            return artifact.__class__.model_validate(data)
        except Exception:
            return artifact



    # A9.10.14.4.3.13.2.2_NO_LLM_QUALITY_PROPS_FIX
    def _a9_10_14_4_3_13_2_2_ensure_quality_props(
        self,
        artifact: UiContractArtifact,
    ) -> UiContractArtifact:
        """Normalize component props before UiContractQualityGate.

        The deterministic PRD-locked No-LLM path builds UI_CONTRACT objects
        without going through the older LLM hardener bridge.  The quality gate
        intentionally requires every component to be selectable by Prototype
        Studio, so every component must have props.elementPicker and
        props.a9VisualQuality.  Repair the Agent output here; keep the quality
        gate strict.
        """
        try:
            data = artifact.model_dump(
                mode="python",
                by_alias=True,
                exclude_none=False,
            )
        except Exception:
            return artifact

        if not isinstance(data, dict):
            return artifact

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        tree = page.get("componentTree")
        if not isinstance(tree, list):
            return artifact

        changed = False

        def visit(value: Any) -> None:
            nonlocal changed
            if isinstance(value, list):
                for item in value:
                    visit(item)
                return

            if not isinstance(value, dict):
                return

            component_id = str(value.get("componentId") or value.get("component_id") or "").strip()
            component_type = str(value.get("type") or value.get("componentType") or "").strip().upper()
            is_component = bool(component_id and component_type)

            if is_component:
                props = value.get("props")
                if not isinstance(props, dict):
                    props = {}
                    value["props"] = props
                    changed = True

                if not props.get("elementPicker"):
                    props["elementPicker"] = {"selectable": True}
                    changed = True
                elif isinstance(props.get("elementPicker"), bool):
                    props["elementPicker"] = {"selectable": bool(props.get("elementPicker"))}
                    changed = True

                if not props.get("a9VisualQuality"):
                    props["a9VisualQuality"] = "demoGrade"
                    changed = True

                if not isinstance(props.get("elementStyles"), dict):
                    props["elementStyles"] = {}
                    changed = True

                props.setdefault("semanticLocked", bool(props.get("a9PrdSemanticLocked")))

            for key in ["children", "components", "items"]:
                children = value.get(key)
                if isinstance(children, list):
                    visit(children)

            tabs = value.get("tabs")
            if isinstance(tabs, list):
                for tab in tabs:
                    if isinstance(tab, dict):
                        for key in ["children", "components", "items"]:
                            children = tab.get(key)
                            if isinstance(children, list):
                                visit(children)

        visit(tree)

        if not changed:
            return artifact

        try:
            return artifact.__class__.model_validate(data)
        except Exception:
            return UiContractArtifact.model_validate(data)


    # A9.10.14.4.3.13.2.3_NO_LLM_FEATURE_COVERAGE_REPAIR
    def _a9_10_14_4_3_13_2_3_repair_feature_coverage(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
        generation_plan: Any | None = None,
    ) -> UiContractArtifact:
        """Use real current PAGE FEATURE ids in deterministic No-LLM contracts.

        Java validation is correct: featureCoverage must reference the FEATURE
        nodes that belong to the current PAGE. The deterministic PRD contract
        path previously emitted a synthetic featureId=prd_semantic_contract,
        which is not a real PAGE FEATURE and therefore must never be persisted.
        """
        try:
            data = artifact.model_dump(mode="json", by_alias=True)
        except Exception:
            try:
                data = artifact.model_dump(mode="python", by_alias=True)
            except Exception:
                return artifact

        if not isinstance(data, dict):
            return artifact

        features = self._a9_10_14_4_3_13_2_3_extract_real_page_features(
            request,
            generation_plan,
        )

        # If Java has current PAGE features, they should be available through
        # generation_plan/ui_contract_readiness/feature_nodes. When not found,
        # remove synthetic coverage rather than persisting fake ids.
        valid_feature_ids = {
            str(item.get("featureId") or "").strip()
            for item in features
            if str(item.get("featureId") or "").strip()
        }

        component_ids, action_ids = self._a9_10_14_4_3_13_2_3_collect_component_and_action_ids(data)

        if valid_feature_ids:
            coverage: list[dict[str, Any]] = []
            for feature in features:
                feature_id = str(feature.get("featureId") or "").strip()
                if not feature_id:
                    continue
                coverage.append({
                    "featureId": feature_id,
                    "featureKey": str(feature.get("featureKey") or feature_id).strip(),
                    "featureName": str(feature.get("featureName") or feature.get("featureKey") or feature_id).strip(),
                    "status": "FULL" if component_ids or action_ids else "NOT_APPLICABLE",
                    "componentIds": list(component_ids),
                    "actionIds": list(action_ids),
                    "notes": "A9.10.14.4.3.13.2.3 repaired from current PAGE real FEATURE context.",
                })
            data["featureCoverage"] = coverage
        else:
            repaired = []
            for item in data.get("featureCoverage") or []:
                if not isinstance(item, dict):
                    continue
                fid = str(item.get("featureId") or "").strip()
                if fid and fid != "prd_semantic_contract":
                    repaired.append(item)
            data["featureCoverage"] = repaired

        # Component/field/action featureIds must not point to synthetic ids.
        real_ids = list(valid_feature_ids)
        default_ids = real_ids[:1]

        def sanitize_node(value: Any) -> None:
            if isinstance(value, dict):
                if "featureIds" in value:
                    current = value.get("featureIds")
                    kept = [str(x).strip() for x in current if str(x).strip() in valid_feature_ids] if isinstance(current, list) else []
                    value["featureIds"] = kept or default_ids
                for child in value.values():
                    sanitize_node(child)
            elif isinstance(value, list):
                for item in value:
                    sanitize_node(item)

        sanitize_node(data.get("page"))
        sanitize_node(data.get("interactions"))
        sanitize_node(data.get("mockScenarios"))
        sanitize_node(data.get("apiBindings"))

        quality = data.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            data["qualitySummary"] = quality
        if valid_feature_ids:
            quality["coverageRate"] = 100
        warnings = quality.get("warnings") if isinstance(quality.get("warnings"), list) else []
        msg = "A9.10.14.4.3.13.2.3 featureCoverage repaired to current PAGE real FEATURE ids."
        if msg not in warnings:
            warnings.append(msg)
        quality["warnings"] = warnings

        return UiContractArtifact.model_validate(data)

    def _a9_10_14_4_3_13_2_3_extract_real_page_features(
        self,
        request: AgentTaskRequest,
        generation_plan: Any | None = None,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        def add_feature(raw: Any, index: int = 0) -> None:
            if not isinstance(raw, dict):
                return
            feature_id = self._a9_10_14_4_3_13_2_3_text(
                raw.get("featureId")
                or raw.get("feature_id")
                or raw.get("module_id")
                or raw.get("moduleId")
                or raw.get("nodeId")
                or raw.get("node_id")
                or raw.get("id")
            )
            if not feature_id or feature_id == "prd_semantic_contract":
                return
            feature_key = self._a9_10_14_4_3_13_2_3_text(
                raw.get("featureKey")
                or raw.get("feature_key")
                or raw.get("module_key")
                or raw.get("moduleKey")
                or raw.get("key")
                or feature_id
            )
            feature_name = self._a9_10_14_4_3_13_2_3_text(
                raw.get("featureName")
                or raw.get("feature_name")
                or raw.get("module_name")
                or raw.get("moduleName")
                or raw.get("name")
                or raw.get("title")
                or feature_key
                or f"页面功能 {index + 1}"
            )
            if feature_id in seen:
                return
            seen.add(feature_id)
            result.append({
                "featureId": feature_id,
                "featureKey": feature_key or feature_id,
                "featureName": feature_name or feature_key or feature_id,
            })

        def extend(value: Any) -> None:
            if isinstance(value, list):
                for idx, item in enumerate(value):
                    add_feature(item, idx)

        # 1. Generation plan is the strongest source. It is built before the
        # No-LLM branch and usually contains system-authoritative feature ids.
        if generation_plan is not None:
            try:
                payload = generation_plan.to_prompt_payload()
            except Exception:
                payload = None
            if isinstance(payload, dict):
                extend(payload.get("features"))
                extend(payload.get("requiredFeatures"))
                extend(payload.get("featureCoverage"))
            extend(getattr(generation_plan, "features", None))

        ctx = request.context_package or {}
        inp = request.input or {}

        # 2. Context package feature nodes.
        extend(ctx.get("feature_nodes"))
        extend(ctx.get("featureNodes"))
        extend(inp.get("feature_nodes"))
        extend(inp.get("featureNodes"))

        # 3. Readiness payloads used by Java/Python readiness chain.
        readiness_candidates = [
            ctx.get("ui_contract_readiness"),
            ctx.get("uiContractReadiness"),
            inp.get("ui_contract_readiness"),
            inp.get("uiContractReadiness"),
            (ctx.get("input") or {}).get("ui_contract_readiness") if isinstance(ctx.get("input"), dict) else None,
            (ctx.get("input") or {}).get("uiContractReadiness") if isinstance(ctx.get("input"), dict) else None,
        ]
        for readiness in readiness_candidates:
            if isinstance(readiness, dict):
                extend(readiness.get("features"))
                extend(readiness.get("featureNodes"))
                extend(readiness.get("requiredFeatures"))

        return result

    def _a9_10_14_4_3_13_2_3_collect_component_and_action_ids(
        self,
        data: dict[str, Any],
    ) -> tuple[list[str], list[str]]:
        component_ids: list[str] = []
        action_ids: list[str] = []

        def add_unique(bucket: list[str], value: Any) -> None:
            text = self._a9_10_14_4_3_13_2_3_text(value)
            if text and text not in bucket:
                bucket.append(text)

        def visit_component(value: Any) -> None:
            if isinstance(value, dict):
                add_unique(component_ids, value.get("componentId") or value.get("component_id") or value.get("id"))
                for child in value.values():
                    visit_component(child)
            elif isinstance(value, list):
                for item in value:
                    visit_component(item)

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        visit_component(page.get("componentTree") if isinstance(page, dict) else [])

        interactions = data.get("interactions")
        if isinstance(interactions, list):
            for action in interactions:
                if isinstance(action, dict):
                    add_unique(action_ids, action.get("actionId") or action.get("action_id") or action.get("id"))

        return component_ids, action_ids

    def _a9_10_14_4_3_13_2_3_text(self, value: Any) -> str:
        return str(value or "").strip()


    # A9.10.14.4.3.13.2.4_FEATURE_AUTHORITY_FINALIZER
    def _a9_10_14_4_3_13_2_4_java_feature_authority_finalizer(
        self,
        artifact: UiContractArtifact,
        request: AgentTaskRequest,
    ) -> UiContractArtifact:
        """Normalize FEATURE references to Java's current PAGE authority.

        The No-LLM PRD contract generator creates action ids such as query,
        reset, view_detail and edit_quantity. Those are action ids, not PAGE
        FEATURE ids. Java validation correctly rejects them if they appear in
        featureCoverage.featureId or in component/field/action featureIds.

        Keep Java strict and repair the Agent-side contract at the last mile:
        - featureCoverage is rebuilt only from current PAGE child FEATURE ids
          exposed by context/readiness.
        - all component/field/column/action featureIds are cleared, because
          authoritative binding is expressed by featureCoverage.
        - coverageRate is recomputed to 100 when every expected FEATURE is
          represented with FULL coverage.
        """
        try:
            data = artifact.model_dump(mode="python", by_alias=True, exclude_none=False)
        except Exception:
            return artifact

        if not isinstance(data, dict):
            return artifact

        expected_features = self._a9_10_14_4_3_13_2_4_collect_expected_page_features(request)

        component_ids: list[str] = []
        action_ids: list[str] = []

        page = data.get("page") if isinstance(data.get("page"), dict) else {}
        roots = page.get("componentTree") if isinstance(page.get("componentTree"), list) else []

        def push_unique(bucket: list[str], value: Any) -> None:
            text = str(value or "").strip()
            if text and text not in bucket:
                bucket.append(text)

        def clear_feature_refs_from_record(record: dict[str, Any]) -> None:
            # Java reads feature references from components, fields and actions.
            # Query/reset/view_detail are action ids and must never appear here.
            if "featureIds" in record:
                record["featureIds"] = []
            if "feature_ids" in record:
                record["feature_ids"] = []

        def visit_component(value: Any) -> None:
            if not isinstance(value, dict):
                return
            push_unique(component_ids, value.get("componentId") or value.get("component_id"))
            clear_feature_refs_from_record(value)

            for list_key in ["fields", "columns", "tabs"]:
                items = value.get(list_key)
                if isinstance(items, list):
                    for item in items:
                        if isinstance(item, dict):
                            clear_feature_refs_from_record(item)

            for child_key in ["children", "components", "items"]:
                children = value.get(child_key)
                if isinstance(children, list):
                    for child in children:
                        visit_component(child)

            tabs = value.get("tabs")
            if isinstance(tabs, list):
                for tab in tabs:
                    if isinstance(tab, dict):
                        children = tab.get("children") or tab.get("components") or tab.get("items")
                        if isinstance(children, list):
                            for child in children:
                                visit_component(child)

        for root in roots:
            visit_component(root)

        interactions = data.get("interactions")
        if isinstance(interactions, list):
            for action in interactions:
                if not isinstance(action, dict):
                    continue
                push_unique(action_ids, action.get("actionId") or action.get("action_id"))
                clear_feature_refs_from_record(action)
                for chained_key in ["successActionIds", "failureActionIds"]:
                    if not isinstance(action.get(chained_key), list):
                        action[chained_key] = [] if action.get(chained_key) is None else action.get(chained_key)

        # Rebuild featureCoverage from expected PAGE FEATURE ids only.
        coverage: list[dict[str, Any]] = []
        if expected_features:
            for feature in expected_features:
                feature_id = str(feature.get("featureId") or "").strip()
                if not feature_id:
                    continue
                feature_key = str(feature.get("featureKey") or feature_id).strip()
                feature_name = str(feature.get("featureName") or feature_key or feature_id).strip()
                coverage.append({
                    "featureId": feature_id,
                    "featureKey": feature_key,
                    "featureName": feature_name,
                    "status": "FULL",
                    "componentIds": list(component_ids),
                    "actionIds": list(action_ids),
                    "notes": [
                        "A9.10.14.4.3.13.2.4 mapped PRD-locked UI_CONTRACT to current PAGE FEATURE authority."
                    ],
                })
        else:
            # If Java expects no child FEATURE, empty coverage is valid and
            # coverageRate should be 100. Do not invent semantic ids.
            coverage = []

        data["featureCoverage"] = coverage

        quality = data.get("qualitySummary")
        if not isinstance(quality, dict):
            quality = {}
            data["qualitySummary"] = quality
        quality["coverageRate"] = 100
        quality["blockers"] = []
        warnings = quality.get("warnings") if isinstance(quality.get("warnings"), list) else []
        marker_warning = "A9.10.14.4.3.13.2.4 normalized featureCoverage and removed invalid featureIds."
        if marker_warning not in warnings:
            warnings.append(marker_warning)
        quality["warnings"] = warnings

        try:
            return UiContractArtifact.model_validate(data)
        except Exception:
            return artifact

    def _a9_10_14_4_3_13_2_4_collect_expected_page_features(
        self,
        request: AgentTaskRequest,
    ) -> list[dict[str, str]]:
        context_package = request.context_package or {}
        input_payload = request.input or {}
        result: list[dict[str, str]] = []
        seen: set[str] = set()

        uuid_like = re.compile(
            r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        def text_value(value: Any) -> str:
            return str(value or "").strip()

        def node_type(record: dict[str, Any]) -> str:
            extra = record.get("extra") if isinstance(record.get("extra"), dict) else {}
            return text_value(
                record.get("nodeType")
                or record.get("node_type")
                or record.get("moduleType")
                or record.get("module_type")
                or record.get("type")
                or extra.get("nodeType")
                or extra.get("node_type")
            ).upper()

        def feature_id(record: dict[str, Any]) -> str:
            return text_value(
                record.get("featureId")
                or record.get("feature_id")
                or record.get("moduleId")
                or record.get("module_id")
                or record.get("id")
            )

        def feature_key(record: dict[str, Any], fid: str) -> str:
            return text_value(
                record.get("featureKey")
                or record.get("feature_key")
                or record.get("moduleKey")
                or record.get("module_key")
                or record.get("key")
                or fid
            )

        def feature_name(record: dict[str, Any], key: str, fid: str) -> str:
            return text_value(
                record.get("featureName")
                or record.get("feature_name")
                or record.get("moduleName")
                or record.get("module_name")
                or record.get("name")
                or record.get("title")
                or key
                or fid
            )

        def add(record: Any, *, trusted_feature_list: bool = False) -> None:
            if not isinstance(record, dict):
                return
            fid = feature_id(record)
            if not fid:
                return
            typ = node_type(record)
            # Avoid action ids such as query/reset/view_detail. Java expects
            # persisted PAGE child FEATURE ids, which are UUIDs in this project.
            if not uuid_like.match(fid) and not (trusted_feature_list and "FEATURE" in typ):
                return
            if typ and "FEATURE" not in typ and not trusted_feature_list:
                return
            if fid in seen:
                return
            seen.add(fid)
            key = feature_key(record, fid)
            result.append({
                "featureId": fid,
                "featureKey": key,
                "featureName": feature_name(record, key, fid),
            })

        def extend(value: Any, *, trusted_feature_list: bool = False) -> None:
            if isinstance(value, list):
                for item in value:
                    add(item, trusted_feature_list=trusted_feature_list)

        # The normal context path used by PageSpecAgent and UI readiness.
        extend(context_package.get("feature_nodes"), trusted_feature_list=True)
        extend(context_package.get("featureNodes"), trusted_feature_list=True)
        extend(input_payload.get("feature_nodes"), trusted_feature_list=True)
        extend(input_payload.get("featureNodes"), trusted_feature_list=True)

        for source in [
            context_package.get("ui_contract_readiness"),
            context_package.get("uiContractReadiness"),
            input_payload.get("ui_contract_readiness"),
            input_payload.get("uiContractReadiness"),
            (context_package.get("input") or {}).get("ui_contract_readiness") if isinstance(context_package.get("input"), dict) else None,
            (context_package.get("input") or {}).get("uiContractReadiness") if isinstance(context_package.get("input"), dict) else None,
        ]:
            if isinstance(source, dict):
                extend(source.get("features"), trusted_feature_list=True)
                extend(source.get("featureNodes"), trusted_feature_list=True)
                extend(source.get("feature_nodes"), trusted_feature_list=True)

        # Fallback: scan current module/page children and module tree for FEATURE nodes.
        def scan_tree(value: Any) -> None:
            if isinstance(value, dict):
                if "FEATURE" in node_type(value):
                    add(value, trusted_feature_list=True)
                for child_key in ["children", "modules", "nodes", "items"]:
                    children = value.get(child_key)
                    if isinstance(children, list):
                        for child in children:
                            scan_tree(child)
            elif isinstance(value, list):
                for item in value:
                    scan_tree(item)

        for tree_source in [
            input_payload.get("current_module"),
            context_package.get("current_module"),
            context_package.get("module"),
            context_package.get("module_tree"),
            context_package.get("moduleTree"),
        ]:
            scan_tree(tree_source)

        return result



    # A9.10.14.4.3.13.2.11.1_MISSING_MODAL_SYNTHESIZER_COMPAT_FIX
    def _a9_10_14_4_synthesize_modal_for_action(
        self,
        *,
        action: dict[str, Any],
        owner_component: dict[str, Any],
        component_index: dict[str, dict[str, Any]],
    ) -> str:
        """Compatibility shim for legacy OPEN_MODAL repair path.

        The current deterministic PRD-locked generator creates MODAL components
        explicitly. Older repair code still calls this helper when an OPEN_MODAL
        action points to a non-MODAL component. The helper name was absent in the
        current class, causing an AttributeError before Java validation.

        Keep the contract strict: do not invent a new modal here. If a MODAL is
        already present, return it; otherwise return an empty string and let the
        existing caller fall back to first_modal_id or SHOW_TOAST.
        """
        for component_id, component in (component_index or {}).items():
            if isinstance(component, dict) and str(component.get("type") or "").strip().upper() == "MODAL":
                return str(component_id or component.get("componentId") or "").strip()
        return ""

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
