from __future__ import annotations

from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest


class RequirementAssetContractPolicy:
    """
    Central prompt-side policy for requirement assets.

    This policy is intentionally language-neutral. Requirement Agent outputs should
    describe business capability, page behavior, data semantics, API contract and
    acceptance criteria. Concrete technology choices such as Express, Spring Boot,
    FastAPI, Vue, Prisma, SQLite, Jest or Playwright belong to the downstream Dev
    Agent stack generator.
    """

    CONTRACT_MARKER = "[[CODEMIND_REQUIREMENT_ASSET_CONTRACT_V2]]"

    TASK_TO_ARTIFACT_TYPE = {
        "GENERATE_MODULE_PRD": "MODULE_PRD",
        "GENERATE_BUSINESS_FLOW": "BUSINESS_FLOW",
        "GENERATE_PAGE_SPEC": "PAGE_SPEC",
        "GENERATE_DATA_MODEL": "DATA_MODEL",
        "GENERATE_API_CONTRACT": "API_CONTRACT",
        "GENERATE_TEST_SPEC": "TEST_SPEC",
        "GENERATE_UI_CONTRACT": "UI_CONTRACT",
        "GENERATE_DEV_HANDOFF_PACKAGE": "DEV_HANDOFF_PACKAGE",
    }

    VALID_NODE_TYPES = {"DOMAIN", "MODULE", "SUBMODULE", "PAGE", "FEATURE"}

    def should_apply(self, request: AgentTaskRequest) -> bool:
        artifact_type = self.resolve_artifact_type(request)
        task_type = str(request.task_type or "").upper()
        return bool(artifact_type) or task_type.startswith("GENERATE_")

    def append_to_prompt(
        self,
        *,
        request: AgentTaskRequest,
        prompt: str,
        artifact_type: str | None = None,
    ) -> str:
        if self.CONTRACT_MARKER in prompt:
            return prompt

        if not self.should_apply(request):
            return prompt

        sections = self.build_prompt_sections(
            request=request,
            artifact_type=artifact_type,
        )

        if not sections:
            return prompt

        return prompt.rstrip() + "\n\n" + "\n".join(sections)

    def build_prompt_sections(
        self,
        *,
        request: AgentTaskRequest,
        artifact_type: str | None = None,
    ) -> list[str]:
        artifact_type = self._normalize_artifact_type(
            artifact_type or self.resolve_artifact_type(request)
        )
        node_type = self.resolve_node_type(request)
        importance = self.resolve_artifact_importance(request)

        sections = [
            self.CONTRACT_MARKER,
            "【统一资产契约：最高优先级】",
            "1. 需求 Agent 输出必须保持语言无关；不要把 Node.js、Express、NestJS、Vue、React、Spring Boot、FastAPI、Prisma、SQLite、PostgreSQL、Jest、Playwright 等具体技术栈写成实现要求。",
            "2. 技术栈、框架、文件路径、数据库方言和测试框架由后续 Dev Agent 根据 targetStack 决定；本资产只描述业务能力、页面行为、数据语义、接口契约、权限规则和验收标准。",
            "3. 用户阅读内容要短、清楚、分层，不要堆砌大段背景、重复章节、内部 ID、checksum、baseVersion、Artifact 字样或调试信息。",
            "4. 每个资产只写自己职责范围内的信息；缺失的细节用 assumptions、development_notes 或 downstream_hints 简短说明，不要编造。",
            "5. 上层节点只给边界和约束，下层节点补充细节；不要把 DOMAIN / MODULE 的背景在 PAGE / FEATURE 中重复展开。",
            "6. 若上游资产互相冲突，优先级为：用户补充说明 > 当前节点信息 > PAGE_SPEC / UI_CONTRACT > API_CONTRACT > DATA_MODEL > BUSINESS_FLOW > MODULE_PRD > 模块树摘要。",
            "",
            "【当前节点层级约束】",
            self.node_scope_guidance(node_type),
            "",
            "【当前资产职责边界】",
            self.artifact_scope_guidance(artifact_type, node_type),
            "",
            "【去重与精简要求】",
            *self.deduplication_rules(artifact_type, node_type),
        ]

        if importance:
            sections.extend(
                [
                    "",
                    "【资产重要性】",
                    self.importance_guidance(importance),
                ]
            )

        return sections

    def resolve_artifact_type(self, request: AgentTaskRequest) -> str:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        for value in [
            input_payload.get("artifact_type"),
            input_payload.get("artifactType"),
            context_package.get("artifact_type"),
            context_package.get("artifactType"),
            self._extract_from_generation_scope(input_payload, "artifactType"),
            self._extract_from_generation_scope(context_package, "artifactType"),
        ]:
            normalized = self._normalize_artifact_type(value)
            if normalized:
                return normalized

        task_type = str(
            input_payload.get("task_type")
            or input_payload.get("taskType")
            or context_package.get("task_type")
            or context_package.get("taskType")
            or request.task_type
            or ""
        ).upper()

        return self.TASK_TO_ARTIFACT_TYPE.get(task_type, "")

    def resolve_node_type(self, request: AgentTaskRequest) -> str:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        current_module = self._extract_current_module(input_payload, context_package)

        for value in [
            input_payload.get("node_type"),
            input_payload.get("nodeType"),
            input_payload.get("module_type"),
            input_payload.get("moduleType"),
            context_package.get("node_type"),
            context_package.get("nodeType"),
            context_package.get("module_type"),
            context_package.get("moduleType"),
            self._extract_from_generation_scope(input_payload, "nodeType"),
            self._extract_from_generation_scope(input_payload, "generationLevel"),
            self._extract_from_generation_scope(context_package, "nodeType"),
            self._extract_from_generation_scope(context_package, "generationLevel"),
            current_module.get("module_type"),
            current_module.get("moduleType"),
            current_module.get("node_type"),
            current_module.get("nodeType"),
            current_module.get("type"),
        ]:
            normalized = self._normalize_node_type(value)
            if normalized:
                return normalized

        return "MODULE"

    def resolve_artifact_importance(self, request: AgentTaskRequest) -> str:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        for value in [
            input_payload.get("artifact_importance"),
            input_payload.get("artifactImportance"),
            context_package.get("artifact_importance"),
            context_package.get("artifactImportance"),
            self._extract_from_generation_scope(input_payload, "artifactImportance"),
            self._extract_from_generation_scope(context_package, "artifactImportance"),
        ]:
            if value is None:
                continue
            normalized = str(value).strip().upper()
            if normalized in {"CORE", "ADVANCED"}:
                return normalized

        return ""

    def node_scope_guidance(self, node_type: str) -> str:
        return {
            "DOMAIN": "DOMAIN 只描述业务域目标、边界、核心角色、模块组成、跨模块流程、全局术语和高层约束；不得展开页面字段、按钮、接口参数或数据库字段。",
            "MODULE": "MODULE 只描述模块职责、模块能力、模块级规则、页面地图、核心数据对象和上下游依赖；页面布局、按钮交互、字段校验和功能点动作留给 PAGE / FEATURE。",
            "SUBMODULE": "SUBMODULE 只描述业务场景、局部流程、涉及页面、页面协作和上下游衔接；不得重复 DOMAIN / MODULE 的大段背景。",
            "PAGE": "PAGE 是开发 Agent 的页面级核心输入，重点描述页面目标、入口、字段、操作、状态、权限、接口绑定、UI 契约和验收标准；只引用必要上层约束。",
            "FEATURE": "FEATURE 只描述页面内单个功能点的触发条件、输入输出、校验、成功/失败路径、接口动作、状态影响、权限点和测试点；不得生成完整页面规范。",
        }.get(node_type, "按当前节点的实际层级生成，不要越级展开下层细节，也不要重复上层背景。")

    def artifact_scope_guidance(self, artifact_type: str, node_type: str) -> str:
        if artifact_type == "MODULE_PRD":
            return {
                "DOMAIN": "输出业务域概览：目标、边界、角色、模块组成、跨模块规则和高层验收。",
                "MODULE": "输出模块 PRD：职责、能力、业务规则、页面地图、核心数据对象、异常场景和验收标准。",
                "SUBMODULE": "输出场景需求说明：场景目标、参与页面、局部规则、上下游关系和验收标准。",
                "PAGE": "输出页面需求摘要：用户任务、关键字段、关键操作、状态、权限和验收，不要写成模块级 PRD。",
                "FEATURE": "输出功能点需求：触发、校验、处理规则、失败路径、权限和验收。",
            }.get(node_type, "输出当前节点需求说明，保持边界清楚、可验收。")

        if artifact_type == "BUSINESS_FLOW":
            return "只描述流程、参与者、前置条件、步骤、分支、异常、状态变化和结果；不要描述页面视觉、组件样式、数据库实现或代码实现。"

        if artifact_type == "PAGE_SPEC":
            if node_type in {"DOMAIN", "MODULE", "SUBMODULE"}:
                return "在非 PAGE 节点上只输出页面地图/页面清单/页面职责，不展开单页字段、按钮、表格列和弹窗细节。"
            if node_type == "FEATURE":
                return "FEATURE 不生成完整 PAGE_SPEC；仅说明该功能点对所属页面局部区域、字段、按钮或状态的影响。"
            return "输出页面规范：页面目标、入口、区域、组件、字段、操作、状态、权限、异常/空状态、接口绑定和验收标准；不要写具体框架代码。"

        if artifact_type == "UI_CONTRACT":
            return "只为 PAGE 节点生成页面视觉与交互契约：布局、组件树、字段、按钮、状态、反馈、空/加载/错误状态和可预览场景；不要输出代码，不要把预览数据当成业务实现。"

        if artifact_type == "API_CONTRACT":
            return "输出语言无关接口契约：接口用途、方法、路径、请求参数、请求体、响应字段、错误码、权限、事务边界、幂等和调用时机；不要写 Controller、Router、Decorator、ORM 查询或框架注解。"

        if artifact_type == "DATA_MODEL":
            return "输出业务数据模型：实体、字段语义、类型、必填、唯一性、枚举、关系、约束、生命周期和安全要求；不要写数据库方言、DDL、ORM schema 或框架实体代码。"

        if artifact_type == "TEST_SPEC":
            return "输出验收与测试规格：核心场景、异常场景、边界条件、权限场景、接口验收、数据校验和最小测试数据；不要绑定具体测试框架或脚本。"

        if artifact_type == "DEV_HANDOFF_PACKAGE":
            return "输出语言无关开发交接包：开发目标、范围边界、页面/接口/数据/权限/测试引用、开发顺序、风险和待确认项；不得指定具体技术栈，文件计划只表达层级和用途。"

        return "输出当前资产所需的最小充分信息，不跨资产职责。"

    def deduplication_rules(self, artifact_type: str, node_type: str) -> list[str]:
        common = [
            "1. 不重复完整上游资产，只提炼本资产需要继承的结论。",
            "2. 不输出全项目通用安全、日志、审计、性能模板；只写与当前节点直接相关的要求。",
            "3. 每个列表优先保留 P0/P1 核心项，普通项放入 development_notes 或 downstream_hints。",
            "4. 字段、接口、测试用例只列当前节点最核心内容；不要为了显得完整而堆砌。",
        ]

        if artifact_type == "MODULE_PRD":
            common.append("5. MODULE_PRD 只给需求边界和规则，不展开完整接口契约、完整数据模型或完整测试用例。")
        elif artifact_type == "BUSINESS_FLOW":
            common.append("5. BUSINESS_FLOW 不重复页面规范和接口字段，只保留流程中需要的数据/接口名称和用途。")
        elif artifact_type == "PAGE_SPEC":
            common.append("5. PAGE_SPEC 不重复完整业务背景和完整数据模型，只保留页面需要的字段、操作、状态和绑定关系。")
        elif artifact_type == "API_CONTRACT":
            common.append("5. API_CONTRACT 不重复测试用例和页面长说明，只保留接口可实现、可联调、可验收的契约信息。")
        elif artifact_type == "DATA_MODEL":
            common.append("5. DATA_MODEL 不重复页面布局和接口细节，只保留数据语义、约束和关系。")
        elif artifact_type == "TEST_SPEC":
            common.append("5. TEST_SPEC 不重复 PRD 长文和接口文档，只提炼可执行验收与高风险测试点。")
        elif artifact_type == "DEV_HANDOFF_PACKAGE":
            common.append("5. DEV_HANDOFF_PACKAGE 不拼接全文资产，只做摘要、引用、开发顺序和风险清单。")

        if node_type in {"DOMAIN", "MODULE", "SUBMODULE"}:
            common.append("6. 当前是上层节点，输出应偏汇总和规划，避免下钻到页面内控件细节。")
        elif node_type in {"PAGE", "FEATURE"}:
            common.append("6. 当前是页面/功能点节点，输出应偏可执行细节，避免重复项目背景和模块概述。")

        return common

    def importance_guidance(self, importance: str) -> str:
        if importance == "CORE":
            return "本资产是当前层级核心资产，必须优先保证甲方可读、开发 Agent 可用、信息不冗余。"
        if importance == "ADVANCED":
            return "本资产是高级资产，只补充正式交付、深度审查或开发准备度需要的信息，不重复核心资产。"
        return "按当前生成任务的重要性控制内容深度。"

    def _extract_current_module(
        self,
        input_payload: dict[str, Any],
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        for value in [
            input_payload.get("current_module"),
            input_payload.get("currentModule"),
            context_package.get("current_module"),
            context_package.get("currentModule"),
            context_package.get("module"),
        ]:
            if isinstance(value, dict):
                return value
        return {}

    def _extract_from_generation_scope(
        self,
        container: dict[str, Any],
        key: str,
    ) -> Any:
        scope = container.get("generation_scope") or container.get("generationScope")
        if not isinstance(scope, dict):
            return None
        return scope.get(key)

    def _normalize_node_type(self, value: Any) -> str:
        if value is None:
            return ""

        raw = str(value).strip().upper()
        if not raw:
            return ""

        raw = raw.replace("-", "_").replace(" ", "_")

        if raw.endswith("_LEVEL"):
            raw = raw[:-6]

        aliases = {
            "BUSINESS_DOMAIN": "DOMAIN",
            "DOMAIN_MODULE": "DOMAIN",
            "BUSINESS_MODULE": "MODULE",
            "FEATURE_MODULE": "MODULE",
            "SUB_MODULE": "SUBMODULE",
            "SCENE": "SUBMODULE",
            "BUSINESS_SCENE": "SUBMODULE",
            "PAGE_MODULE": "PAGE",
            "SCREEN": "PAGE",
            "VIEW": "PAGE",
            "FUNCTION": "FEATURE",
            "ACTION": "FEATURE",
            "OPERATION": "FEATURE",
        }

        raw = aliases.get(raw, raw)
        return raw if raw in self.VALID_NODE_TYPES else ""

    def _normalize_artifact_type(self, value: Any) -> str:
        if value is None:
            return ""

        raw = str(value).strip().upper()
        if not raw:
            return ""

        raw = raw.replace("-", "_").replace(" ", "_")
        if raw in set(self.TASK_TO_ARTIFACT_TYPE.values()):
            return raw
        return ""
