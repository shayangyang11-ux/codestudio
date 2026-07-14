from __future__ import annotations

from typing import Annotated, Any

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, field_validator


def normalize_optional_text(value: Any) -> str | None:
    if value is None:
        return None

    if value == []:
        return None

    if isinstance(value, list):
        if len(value) == 0:
            return None
        return "；".join([str(item) for item in value])

    if isinstance(value, dict):
        return str(value)

    return str(value)


def normalize_string_list(value: Any) -> list[str]:
    if value is None:
        return []

    if isinstance(value, list):
        return [str(item) for item in value if item is not None]

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
    package_key: str = Field(description="开发交接包唯一标识，例如 order_dev_handoff_package")
    package_name: str = Field(description="开发交接包名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    package_goal: str = Field(description="开发交接目标")
    target_agents: StringList = Field(default_factory=list, max_length=4, description="目标下游 Agent")
    implementation_scope: str = Field(description="实现范围说明")

    @field_validator("target_agents", mode="before")
    @classmethod
    def limit_target_agents(cls, value: Any) -> Any:
        return limit_list(value, 4)


class DevelopmentObjective(CodeMindDevHandoffBase):
    objective_key: str = Field(description="开发目标唯一标识")
    title: str = Field(description="开发目标标题")
    description: str = Field(description="开发目标说明")
    priority: str = Field(description="优先级，例如 P0、P1、P2")
    acceptance_reference: StringList = Field(default_factory=list, max_length=4, description="关联验收标准或测试规格")

    @field_validator("acceptance_reference", mode="before")
    @classmethod
    def limit_acceptance_reference(cls, value: Any) -> Any:
        return limit_list(value, 4)


class ModuleBoundary(CodeMindDevHandoffBase):
    boundary_key: str = Field(description="边界唯一标识")
    responsibility: str = Field(description="本模块职责")
    included_scope: StringList = Field(default_factory=list, max_length=5, description="包含范围")
    excluded_scope: StringList = Field(default_factory=list, max_length=5, description="排除范围")
    dependency_modules: StringList = Field(default_factory=list, max_length=5, description="依赖模块")

    @field_validator("included_scope", "excluded_scope", "dependency_modules", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class FrontendImplementationItem(CodeMindDevHandoffBase):
    item_key: str = Field(description="前端实现项唯一标识")
    page_key: str = Field(description="页面 Key")
    page_name: str = Field(description="页面名称")
    route_path: OptionalText = Field(default=None, description="前端路由路径")
    components: StringList = Field(default_factory=list, max_length=5, description="建议组件")
    interactions: StringList = Field(default_factory=list, max_length=5, description="交互行为")
    api_dependencies: StringList = Field(default_factory=list, max_length=5, description="依赖接口")
    validation_points: StringList = Field(default_factory=list, max_length=5, description="前端校验点")

    @field_validator("components", "interactions", "api_dependencies", "validation_points", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class BackendImplementationItem(CodeMindDevHandoffBase):
    item_key: str = Field(description="后端实现项唯一标识")
    service_name: str = Field(description="服务或领域对象名称")
    responsibility: str = Field(description="职责说明")
    api_keys: StringList = Field(default_factory=list, max_length=5, description="关联接口 Key")
    entity_keys: StringList = Field(default_factory=list, max_length=5, description="关联实体 Key")
    business_rules: StringList = Field(default_factory=list, max_length=5, description="业务规则")
    transaction_notes: StringList = Field(default_factory=list, max_length=4, description="事务注意事项")

    @field_validator("api_keys", "entity_keys", "business_rules", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)

    @field_validator("transaction_notes", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)


class ApiImplementationItem(CodeMindDevHandoffBase):
    item_key: str = Field(description="接口实现项唯一标识")
    api_key: str = Field(description="接口 Key")
    method: str = Field(description="HTTP 方法")
    path: str = Field(description="接口路径")
    request_summary: str = Field(description="请求摘要")
    response_summary: str = Field(description="响应摘要")
    permission_keys: StringList = Field(default_factory=list, max_length=4)
    error_handling: StringList = Field(default_factory=list, max_length=4)
    test_reference: StringList = Field(default_factory=list, max_length=4)

    @field_validator("method", mode="before")
    @classmethod
    def normalize_method(cls, value: Any) -> str:
        if value is None:
            return "GET"
        return str(value).upper()

    @field_validator("permission_keys", "error_handling", "test_reference", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)


class DataImplementationItem(CodeMindDevHandoffBase):
    item_key: str = Field(description="数据实现项唯一标识")
    entity_key: str = Field(description="实体 Key")
    table_name: OptionalText = Field(default=None, description="建议表名")
    important_fields: StringList = Field(default_factory=list, max_length=6)
    constraints: StringList = Field(default_factory=list, max_length=5)
    indexes: StringList = Field(default_factory=list, max_length=5)
    migration_notes: StringList = Field(default_factory=list, max_length=4)

    @field_validator("important_fields", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)

    @field_validator("constraints", "indexes", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)

    @field_validator("migration_notes", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)


class PermissionAndSecurityItem(CodeMindDevHandoffBase):
    item_key: str = Field(description="权限与安全项唯一标识")
    security_type: str = Field(description="安全类型，例如 AUTH、PERMISSION、AUDIT、DESENSITIZATION")
    description: str = Field(description="说明")
    implementation_hint: str = Field(description="实现建议")
    related_api_keys: StringList = Field(default_factory=list, max_length=4)

    @field_validator("related_api_keys", mode="before")
    @classmethod
    def limit_related_api_keys(cls, value: Any) -> Any:
        return limit_list(value, 4)


class FileGenerationPlan(CodeMindDevHandoffBase):
    file_key: str = Field(description="文件计划唯一标识")
    layer: str = Field(description="层级，例如 frontend、backend、database、test")
    suggested_path: str = Field(description="建议文件路径")
    file_purpose: str = Field(description="文件用途")
    generation_priority: str = Field(description="生成优先级")
    depends_on: StringList = Field(default_factory=list, max_length=4)

    @field_validator("depends_on", mode="before")
    @classmethod
    def limit_depends_on(cls, value: Any) -> Any:
        return limit_list(value, 4)


class DevelopmentSequenceStep(CodeMindDevHandoffBase):
    step_key: str = Field(description="开发步骤唯一标识")
    step_order: int = Field(description="步骤顺序")
    title: str = Field(description="步骤标题")
    description: str = Field(description="步骤说明")
    deliverables: StringList = Field(default_factory=list, max_length=5)
    dependencies: StringList = Field(default_factory=list, max_length=5)

    @field_validator("deliverables", "dependencies", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class TestAndAcceptanceHandoff(CodeMindDevHandoffBase):
    item_key: str = Field(description="测试交接项唯一标识")
    test_type: str = Field(description="测试类型")
    reference_cases: StringList = Field(default_factory=list, max_length=5)
    acceptance_points: StringList = Field(default_factory=list, max_length=5)
    automation_hint: OptionalText = Field(default=None, description="自动化测试提示")

    @field_validator("reference_cases", "acceptance_points", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class RiskAndAttentionItem(CodeMindDevHandoffBase):
    risk_key: str = Field(description="风险唯一标识")
    title: str = Field(description="风险标题")
    risk_level: str = Field(description="风险等级，例如 HIGH、MEDIUM、LOW")
    description: str = Field(description="风险说明")
    mitigation: str = Field(description="规避措施")


class DevHandoffDownstreamHints(CodeMindDevHandoffBase):
    backend_agent_hints: StringList = Field(default_factory=list, max_length=5)
    frontend_agent_hints: StringList = Field(default_factory=list, max_length=5)
    test_agent_hints: StringList = Field(default_factory=list, max_length=5)
    integration_hints: StringList = Field(default_factory=list, max_length=5)

    @field_validator(
        "backend_agent_hints",
        "frontend_agent_hints",
        "test_agent_hints",
        "integration_hints",
        mode="before",
    )
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class DevHandoffPackageLlmOutput(CodeMindDevHandoffBase):
    basic_info: DevHandoffBasicInfo
    development_objectives: list[DevelopmentObjective] = Field(default_factory=list, max_length=6)
    module_boundaries: list[ModuleBoundary] = Field(default_factory=list, max_length=4)
    frontend_implementation_items: list[FrontendImplementationItem] = Field(default_factory=list, max_length=6)
    backend_implementation_items: list[BackendImplementationItem] = Field(default_factory=list, max_length=6)
    api_implementation_items: list[ApiImplementationItem] = Field(default_factory=list, max_length=6)
    data_implementation_items: list[DataImplementationItem] = Field(default_factory=list, max_length=6)
    permission_and_security_items: list[PermissionAndSecurityItem] = Field(default_factory=list, max_length=5)
    file_generation_plan: list[FileGenerationPlan] = Field(default_factory=list, max_length=10)
    development_sequence: list[DevelopmentSequenceStep] = Field(default_factory=list, max_length=8)
    test_and_acceptance_handoff: list[TestAndAcceptanceHandoff] = Field(default_factory=list, max_length=6)
    risks_and_attention: list[RiskAndAttentionItem] = Field(default_factory=list, max_length=6)
    downstream_hints: DevHandoffDownstreamHints
    analysis_summary: str

    @field_validator(
        "development_objectives",
        "frontend_implementation_items",
        "backend_implementation_items",
        "api_implementation_items",
        "data_implementation_items",
        "test_and_acceptance_handoff",
        "risks_and_attention",
        mode="before",
    )
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)

    @field_validator("module_boundaries", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)

    @field_validator("permission_and_security_items", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)

    @field_validator("file_generation_plan", mode="before")
    @classmethod
    def limit_ten_items(cls, value: Any) -> Any:
        return limit_list(value, 10)

    @field_validator("development_sequence", mode="before")
    @classmethod
    def limit_eight_items(cls, value: Any) -> Any:
        return limit_list(value, 8)


class DevHandoffPackageArtifact(DevHandoffPackageLlmOutput):
    artifact_type: str = "DEV_HANDOFF_PACKAGE"
    markdown: str


DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA = DevHandoffPackageLlmOutput.model_json_schema()