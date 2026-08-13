from __future__ import annotations

from typing import Annotated, Any

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, field_validator


def normalize_optional_text(value: Any) -> str | None:
    if value is None or value == []:
        return None

    if isinstance(value, list):
        items = [str(item) for item in value if item is not None and str(item).strip()]
        return "；".join(items) if items else None

    if isinstance(value, dict):
        return str(value)

    text = str(value).strip()
    return text or None


def normalize_string_list(value: Any) -> list[str]:
    if value is None:
        return []

    if isinstance(value, list):
        return [str(item).strip() for item in value if item is not None and str(item).strip()]

    if isinstance(value, str):
        text = value.strip()
        return [text] if text else []

    if isinstance(value, dict):
        return [str(value)]

    return [str(value)]


def limit_list(value: Any, max_items: int) -> Any:
    if value is None:
        return []

    if isinstance(value, list):
        return value[:max_items]

    return value


StringList = Annotated[list[str], BeforeValidator(normalize_string_list)]
OptionalText = Annotated[str | None, BeforeValidator(normalize_optional_text)]


class CodeMindDevHandoffBase(BaseModel):
    model_config = ConfigDict(
        coerce_numbers_to_str=True,
        extra="ignore",
    )


class DevHandoffBasicInfo(CodeMindDevHandoffBase):
    package_key: str = Field(description="开发交接包唯一标识，例如 order_page_dev_handoff_package")
    package_name: str = Field(description="开发交接包名称")
    related_module_key: str = Field(description="关联节点 Key，兼容历史字段名 related_module_key")
    related_module_name: str = Field(description="关联节点名称，兼容历史字段名 related_module_name")
    related_node_type: OptionalText = Field(default=None, description="关联节点层级，例如 DOMAIN、MODULE、SUBMODULE、PAGE、FEATURE")
    package_goal: str = Field(description="语言无关的开发交接目标")
    target_agents: StringList = Field(default_factory=list, max_length=6, description="目标下游 Agent / 生成器")
    implementation_scope: str = Field(description="实现范围说明")
    blueprint_version: str = Field(default="2.0", description="Dev Blueprint 版本")
    language_neutral: bool = Field(default=True, description="是否保持语言无关")

    @field_validator("target_agents", mode="before")
    @classmethod
    def limit_target_agents(cls, value: Any) -> Any:
        return limit_list(value, 6)


class DevelopmentObjective(CodeMindDevHandoffBase):
    objective_key: str = Field(description="开发目标唯一标识")
    title: str = Field(description="开发目标标题")
    description: str = Field(description="开发目标说明")
    priority: str = Field(description="优先级，例如 P0、P1、P2")
    source_refs: StringList = Field(default_factory=list, max_length=6, description="来源资产引用")
    acceptance_reference: StringList = Field(default_factory=list, max_length=6, description="关联验收标准或测试规格")

    @field_validator("source_refs", "acceptance_reference", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class ModuleBoundary(CodeMindDevHandoffBase):
    boundary_key: str = Field(description="边界唯一标识")
    responsibility: str = Field(description="当前节点职责")
    included_scope: StringList = Field(default_factory=list, max_length=6, description="包含范围")
    excluded_scope: StringList = Field(default_factory=list, max_length=6, description="排除范围")
    dependency_nodes: StringList = Field(default_factory=list, max_length=6, description="依赖节点")
    dependency_modules: StringList = Field(default_factory=list, max_length=6, description="兼容历史字段：依赖模块")

    @field_validator("included_scope", "excluded_scope", "dependency_nodes", "dependency_modules", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class PageBlueprintItem(CodeMindDevHandoffBase):
    page_key: str = Field(description="页面 Key")
    page_name: str = Field(description="页面名称")
    page_goal: str = Field(description="页面目标")
    route_semantics: OptionalText = Field(default=None, description="路由语义，不绑定具体前端框架")
    ui_contract_refs: StringList = Field(default_factory=list, max_length=5, description="UI_CONTRACT 引用")
    page_spec_refs: StringList = Field(default_factory=list, max_length=5, description="PAGE_SPEC 引用")
    interactions: StringList = Field(default_factory=list, max_length=8, description="页面交互")
    state_rules: StringList = Field(default_factory=list, max_length=6, description="页面状态规则")
    api_dependencies: StringList = Field(default_factory=list, max_length=8, description="依赖接口")
    validation_points: StringList = Field(default_factory=list, max_length=8, description="页面校验点")

    @field_validator(
        "ui_contract_refs",
        "page_spec_refs",
        "interactions",
        "state_rules",
        "api_dependencies",
        "validation_points",
        mode="before",
    )
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class FeatureBlueprintItem(CodeMindDevHandoffBase):
    feature_key: str = Field(description="功能点 Key")
    feature_name: str = Field(description="功能点名称")
    parent_page_key: OptionalText = Field(default=None, description="所属页面 Key")
    trigger: str = Field(description="触发条件")
    input_summary: OptionalText = Field(default=None, description="输入摘要")
    output_summary: OptionalText = Field(default=None, description="输出摘要")
    business_rules: StringList = Field(default_factory=list, max_length=8, description="业务规则")
    api_dependencies: StringList = Field(default_factory=list, max_length=6, description="接口依赖")
    acceptance_refs: StringList = Field(default_factory=list, max_length=6, description="验收引用")

    @field_validator("business_rules", "api_dependencies", "acceptance_refs", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class ApiBlueprintItem(CodeMindDevHandoffBase):
    api_key: str = Field(description="接口 Key")
    capability: str = Field(description="接口业务能力")
    method: str = Field(description="HTTP 方法")
    path: str = Field(description="接口路径")
    request_summary: str = Field(description="请求摘要")
    response_summary: str = Field(description="响应摘要")
    data_entities: StringList = Field(default_factory=list, max_length=6, description="关联数据实体")
    permission_keys: StringList = Field(default_factory=list, max_length=6, description="权限点")
    error_scenarios: StringList = Field(default_factory=list, max_length=6, description="错误场景")
    idempotency: OptionalText = Field(default=None, description="幂等性要求")
    test_refs: StringList = Field(default_factory=list, max_length=6, description="测试引用")

    @field_validator("method", mode="before")
    @classmethod
    def normalize_method(cls, value: Any) -> str:
        if value is None:
            return "GET"
        return str(value).strip().upper() or "GET"

    @field_validator("data_entities", "permission_keys", "error_scenarios", "test_refs", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class DataEntityBlueprintItem(CodeMindDevHandoffBase):
    entity_key: str = Field(description="实体 Key")
    entity_name: str = Field(description="实体名称")
    business_meaning: str = Field(description="业务含义")
    fields: StringList = Field(default_factory=list, max_length=10, description="核心字段语义")
    relationships: StringList = Field(default_factory=list, max_length=8, description="实体关系")
    constraints: StringList = Field(default_factory=list, max_length=8, description="业务约束")
    lifecycle_states: StringList = Field(default_factory=list, max_length=8, description="生命周期状态")
    security_notes: StringList = Field(default_factory=list, max_length=6, description="安全与合规注意事项")

    @field_validator("fields", "relationships", "constraints", "lifecycle_states", "security_notes", mode="before")
    @classmethod
    def limit_ten_items(cls, value: Any) -> Any:
        return limit_list(value, 10)


class UiBlueprintItem(CodeMindDevHandoffBase):
    ui_key: str = Field(description="UI 契约引用 Key")
    page_key: str = Field(description="页面 Key")
    layout_summary: str = Field(description="布局摘要")
    key_regions: StringList = Field(default_factory=list, max_length=8, description="关键区域")
    controls: StringList = Field(default_factory=list, max_length=10, description="关键控件")
    interaction_rules: StringList = Field(default_factory=list, max_length=8, description="交互规则")
    visual_consistency_rules: StringList = Field(default_factory=list, max_length=6, description="视觉一致性约束")
    preview_data_policy: str = Field(default="可使用预览场景数据验证页面状态，但不得作为最终业务实现。", description="预览场景数据使用边界")

    @field_validator("key_regions", "controls", "interaction_rules", "visual_consistency_rules", mode="before")
    @classmethod
    def limit_ten_items(cls, value: Any) -> Any:
        return limit_list(value, 10)


class PermissionRuleItem(CodeMindDevHandoffBase):
    rule_key: str = Field(description="权限规则 Key")
    rule_type: str = Field(description="规则类型，例如 AUTH、PERMISSION、AUDIT、DATA_SCOPE、DESENSITIZATION")
    description: str = Field(description="规则说明")
    applies_to: StringList = Field(default_factory=list, max_length=8, description="适用页面、接口、数据或功能")
    confirmation_needed: bool = Field(default=False, description="是否需要人工确认")

    @field_validator("applies_to", mode="before")
    @classmethod
    def limit_applies_to(cls, value: Any) -> Any:
        return limit_list(value, 8)


class AcceptanceBlueprintItem(CodeMindDevHandoffBase):
    acceptance_key: str = Field(description="验收项 Key")
    title: str = Field(description="验收标题")
    scenario_type: str = Field(description="场景类型，例如 MAIN_FLOW、EXCEPTION、BOUNDARY、PERMISSION、INTEGRATION")
    preconditions: StringList = Field(default_factory=list, max_length=5, description="前置条件")
    steps: StringList = Field(default_factory=list, max_length=8, description="验收步骤")
    expected_result: str = Field(description="期望结果")
    source_refs: StringList = Field(default_factory=list, max_length=6, description="来源引用")

    @field_validator("preconditions", "steps", "source_refs", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class SourceReferenceItem(CodeMindDevHandoffBase):
    source_key: str = Field(description="来源引用 Key")
    artifact_type: str = Field(description="来源资产类型")
    artifact_title: str = Field(description="来源资产标题")
    version_no: OptionalText = Field(default=None, description="来源资产版本")
    status: OptionalText = Field(default=None, description="来源资产状态")
    used_for: StringList = Field(default_factory=list, max_length=8, description="该来源用于哪些开发决策")
    confidence: str = Field(default="MEDIUM", description="引用可信度 HIGH、MEDIUM、LOW")
    notes: OptionalText = Field(default=None, description="补充说明")

    @field_validator("used_for", mode="before")
    @classmethod
    def limit_used_for(cls, value: Any) -> Any:
        return limit_list(value, 8)


class DevBlueprint(CodeMindDevHandoffBase):
    blueprint_key: str = Field(description="语言无关开发蓝图 Key")
    blueprint_name: str = Field(description="语言无关开发蓝图名称")
    scope_type: str = Field(description="开发范围类型，例如 DOMAIN、MODULE、SUBMODULE、PAGE、FEATURE")
    target_node_key: str = Field(description="目标节点 Key")
    target_node_name: str = Field(description="目标节点名称")
    target_node_type: str = Field(description="目标节点类型")
    business_goal: str = Field(description="业务目标")
    stack_policy: str = Field(default="LANGUAGE_NEUTRAL_REQUIREMENT_BLUEPRINT", description="技术栈策略")
    supported_stack_modes: StringList = Field(default_factory=list, max_length=8, description="后续可转换的目标栈模式")
    generation_boundaries: StringList = Field(default_factory=list, max_length=8, description="生成边界")
    pages: list[PageBlueprintItem] = Field(default_factory=list, max_length=8)
    features: list[FeatureBlueprintItem] = Field(default_factory=list, max_length=10)
    apis: list[ApiBlueprintItem] = Field(default_factory=list, max_length=10)
    data_entities: list[DataEntityBlueprintItem] = Field(default_factory=list, max_length=10)
    ui_contracts: list[UiBlueprintItem] = Field(default_factory=list, max_length=6)
    permission_rules: list[PermissionRuleItem] = Field(default_factory=list, max_length=8)
    acceptance_criteria: list[AcceptanceBlueprintItem] = Field(default_factory=list, max_length=10)
    unresolved_questions: StringList = Field(default_factory=list, max_length=8)

    @field_validator("supported_stack_modes", "generation_boundaries", "unresolved_questions", mode="before")
    @classmethod
    def limit_eight_strings(cls, value: Any) -> Any:
        return limit_list(value, 8)

    @field_validator("pages", "permission_rules", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)

    @field_validator("features", "apis", "data_entities", "acceptance_criteria", mode="before")
    @classmethod
    def limit_ten_items(cls, value: Any) -> Any:
        return limit_list(value, 10)

    @field_validator("ui_contracts", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class DevelopmentSequenceStep(CodeMindDevHandoffBase):
    step_key: str = Field(description="开发步骤唯一标识")
    step_order: int = Field(description="步骤顺序")
    title: str = Field(description="步骤标题")
    description: str = Field(description="步骤说明")
    deliverables: StringList = Field(default_factory=list, max_length=6)
    dependencies: StringList = Field(default_factory=list, max_length=6)

    @field_validator("deliverables", "dependencies", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class RiskAndAttentionItem(CodeMindDevHandoffBase):
    risk_key: str = Field(description="风险唯一标识")
    title: str = Field(description="风险标题")
    risk_level: str = Field(description="风险等级，例如 HIGH、MEDIUM、LOW")
    description: str = Field(description="风险说明")
    mitigation: str = Field(description="规避措施")


class DevHandoffDownstreamHints(CodeMindDevHandoffBase):
    dev_agent_hints: StringList = Field(default_factory=list, max_length=8)
    frontend_generator_hints: StringList = Field(default_factory=list, max_length=8)
    backend_generator_hints: StringList = Field(default_factory=list, max_length=8)
    data_generator_hints: StringList = Field(default_factory=list, max_length=8)
    test_generator_hints: StringList = Field(default_factory=list, max_length=8)
    integration_hints: StringList = Field(default_factory=list, max_length=8)
    # Backward-compatible historical names.
    backend_agent_hints: StringList = Field(default_factory=list, max_length=8)
    frontend_agent_hints: StringList = Field(default_factory=list, max_length=8)
    test_agent_hints: StringList = Field(default_factory=list, max_length=8)

    @field_validator(
        "dev_agent_hints",
        "frontend_generator_hints",
        "backend_generator_hints",
        "data_generator_hints",
        "test_generator_hints",
        "integration_hints",
        "backend_agent_hints",
        "frontend_agent_hints",
        "test_agent_hints",
        mode="before",
    )
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class DevHandoffReadiness(CodeMindDevHandoffBase):
    ready_for_dev_agent: bool = Field(default=False)
    readiness_level: str = Field(default="WARNING", description="READY、WARNING、BLOCKED")
    blockers: StringList = Field(default_factory=list, max_length=8)
    warnings: StringList = Field(default_factory=list, max_length=8)
    required_source_artifacts: StringList = Field(default_factory=list, max_length=8)
    recommended_source_artifacts: StringList = Field(default_factory=list, max_length=8)

    @field_validator("blockers", "warnings", "required_source_artifacts", "recommended_source_artifacts", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class DevHandoffPackageLlmOutput(CodeMindDevHandoffBase):
    basic_info: DevHandoffBasicInfo
    dev_blueprint: DevBlueprint
    source_reference_matrix: list[SourceReferenceItem] = Field(default_factory=list, max_length=12)
    development_objectives: list[DevelopmentObjective] = Field(default_factory=list, max_length=8)
    module_boundaries: list[ModuleBoundary] = Field(default_factory=list, max_length=6)
    development_sequence: list[DevelopmentSequenceStep] = Field(default_factory=list, max_length=8)
    readiness: DevHandoffReadiness = Field(default_factory=DevHandoffReadiness)
    risks_and_attention: list[RiskAndAttentionItem] = Field(default_factory=list, max_length=8)
    downstream_hints: DevHandoffDownstreamHints = Field(default_factory=DevHandoffDownstreamHints)
    analysis_summary: str

    # Backward-compatible historical fields. They are not the main contract anymore.
    frontend_implementation_items: list[dict[str, Any]] = Field(default_factory=list, max_length=8)
    backend_implementation_items: list[dict[str, Any]] = Field(default_factory=list, max_length=8)
    api_implementation_items: list[dict[str, Any]] = Field(default_factory=list, max_length=10)
    data_implementation_items: list[dict[str, Any]] = Field(default_factory=list, max_length=10)
    permission_and_security_items: list[dict[str, Any]] = Field(default_factory=list, max_length=8)
    file_generation_plan: list[dict[str, Any]] = Field(default_factory=list, max_length=10)
    test_and_acceptance_handoff: list[dict[str, Any]] = Field(default_factory=list, max_length=10)

    @field_validator("source_reference_matrix", mode="before")
    @classmethod
    def limit_source_refs(cls, value: Any) -> Any:
        return limit_list(value, 12)

    @field_validator("development_objectives", "development_sequence", "risks_and_attention", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)

    @field_validator("module_boundaries", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)


class DevHandoffPackageArtifact(DevHandoffPackageLlmOutput):
    artifact_type: str = "DEV_HANDOFF_PACKAGE"
    markdown: str


DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA = DevHandoffPackageLlmOutput.model_json_schema()
