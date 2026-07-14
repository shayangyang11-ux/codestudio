from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CodeMindDataModelBase(BaseModel):
    model_config = ConfigDict(coerce_numbers_to_str=True)


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


class DataModelBasicInfo(CodeMindDataModelBase):
    model_key: str = Field(description="数据模型资产唯一标识，例如 order_data_model")
    model_name: str = Field(description="数据模型名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    design_goal: str = Field(description="数据模型设计目标")
    database_type_suggestion: str = Field(description="数据库类型建议，例如 PostgreSQL、MySQL")
    naming_convention: str = Field(description="表名、字段名、索引名等命名规范说明")


class DataFieldSpec(CodeMindDataModelBase):
    field_key: str = Field(description="字段唯一标识")
    field_name: str = Field(description="字段中文名称")
    column_name: str = Field(description="数据库字段名")
    data_type: str = Field(description="数据库字段类型建议，例如 varchar、bigint、uuid、timestamp、numeric")
    length: str | None = Field(default=None, description="字段长度或精度，例如 64、255、18,2")
    nullable: bool = Field(default=True, description="是否允许为空")
    unique: bool = Field(default=False, description="是否唯一")
    default_value: str | None = Field(default=None, description="默认值")
    description: str = Field(description="字段说明")
    business_meaning: str = Field(description="业务含义")
    validation_rules: list[str] = Field(default_factory=list, description="字段校验规则")
    source_page_fields: list[str] = Field(default_factory=list, description="来自页面规范中的字段或组件")

    @field_validator("length", "default_value", mode="before")
    @classmethod
    def normalize_text_fields(cls, value: Any) -> str | None:
        return normalize_optional_text(value)


class DataEntitySpec(CodeMindDataModelBase):
    entity_key: str = Field(description="实体唯一标识，例如 order")
    entity_name: str = Field(description="实体中文名称")
    table_name: str = Field(description="数据库表名，例如 t_order")
    aggregate_role: Literal[
        "AGGREGATE_ROOT",
        "ENTITY",
        "VALUE_OBJECT",
        "RELATION_TABLE",
        "DICT_TABLE",
        "LOG_TABLE",
        "OTHER",
    ] = Field(description="实体在领域模型中的角色")
    description: str = Field(description="实体说明")
    primary_key: str = Field(description="主键字段名")
    fields: list[DataFieldSpec] = Field(default_factory=list, description="字段列表")
    lifecycle_summary: str = Field(description="实体生命周期说明")


class EntityRelationshipSpec(CodeMindDataModelBase):
    relationship_key: str = Field(description="关系唯一标识")
    source_entity_key: str = Field(description="源实体 Key")
    target_entity_key: str = Field(description="目标实体 Key")
    relationship_type: Literal[
        "ONE_TO_ONE",
        "ONE_TO_MANY",
        "MANY_TO_ONE",
        "MANY_TO_MANY",
        "REFERENCE",
        "COMPOSITION",
        "AGGREGATION",
    ] = Field(description="实体关系类型")
    source_field: str = Field(description="源实体关联字段")
    target_field: str = Field(description="目标实体关联字段")
    description: str = Field(description="关系说明")
    cascade_rules: list[str] = Field(default_factory=list, description="级联规则")


class DataIndexSpec(CodeMindDataModelBase):
    index_key: str = Field(description="索引唯一标识")
    index_name: str = Field(description="索引名称建议")
    entity_key: str = Field(description="所属实体 Key")
    table_name: str = Field(description="所属表名")
    columns: list[str] = Field(default_factory=list, description="索引字段")
    index_type: Literal["PRIMARY", "UNIQUE", "NORMAL", "FULLTEXT", "GIN", "BTREE", "OTHER"] = Field(
        description="索引类型"
    )
    reason: str = Field(description="建立索引原因")


class DataConstraintSpec(CodeMindDataModelBase):
    constraint_key: str = Field(description="约束唯一标识")
    entity_key: str = Field(description="所属实体 Key")
    constraint_type: Literal[
        "PRIMARY_KEY",
        "FOREIGN_KEY",
        "UNIQUE",
        "NOT_NULL",
        "CHECK",
        "BUSINESS",
        "OTHER",
    ] = Field(description="约束类型")
    fields: list[str] = Field(default_factory=list, description="涉及字段")
    rule_description: str = Field(description="约束规则说明")
    violation_handling: str = Field(description="违反约束时的处理策略")


class StatusModelSpec(CodeMindDataModelBase):
    status_key: str = Field(description="状态字段或状态模型标识")
    entity_key: str = Field(description="所属实体 Key")
    status_field: str = Field(description="状态字段名")
    allowed_values: list[str] = Field(default_factory=list, description="允许的状态值")
    initial_value: str = Field(description="初始状态")
    terminal_values: list[str] = Field(default_factory=list, description="终态状态值")
    transition_rules: list[str] = Field(default_factory=list, description="状态流转规则")


class PageDataBindingSpec(CodeMindDataModelBase):
    binding_key: str = Field(description="页面数据绑定标识")
    page_key: str = Field(description="页面 Key")
    page_name: str = Field(description="页面名称")
    entity_key: str = Field(description="绑定实体 Key")
    fields: list[str] = Field(default_factory=list, description="页面使用字段")
    usage_description: str = Field(description="页面如何使用这些数据")


class ApiDataUsageHint(CodeMindDataModelBase):
    api_hint_key: str = Field(description="接口数据使用提示标识")
    api_name: str = Field(description="接口名称建议")
    method: str = Field(description="HTTP 方法")
    path_suggestion: str = Field(description="接口路径建议")
    request_entities: list[str] = Field(default_factory=list, description="请求涉及实体")
    response_entities: list[str] = Field(default_factory=list, description="响应涉及实体")
    description: str = Field(description="接口与数据模型关系说明")


class DataSecuritySpec(CodeMindDataModelBase):
    security_key: str = Field(description="数据安全规则标识")
    entity_key: str = Field(description="所属实体 Key")
    fields: list[str] = Field(default_factory=list, description="涉及字段")
    security_level: Literal["PUBLIC", "INTERNAL", "SENSITIVE", "SECRET"] = Field(
        description="数据安全等级"
    )
    protection_strategy: str = Field(description="保护策略，例如脱敏、加密、访问控制、审计")


class DataMigrationHint(CodeMindDataModelBase):
    migration_key: str = Field(description="迁移提示标识")
    title: str = Field(description="迁移事项标题")
    description: str = Field(description="迁移说明")
    risk: str = Field(description="迁移风险")
    recommendation: str = Field(description="建议处理方式")


class DataModelDownstreamHints(CodeMindDataModelBase):
    api_contract_hints: list[str] = Field(default_factory=list)
    test_spec_hints: list[str] = Field(default_factory=list)
    dev_handoff_hints: list[str] = Field(default_factory=list)
    database_migration_hints: list[str] = Field(default_factory=list)
    backend_code_hints: list[str] = Field(default_factory=list)


class DataModelLlmOutput(CodeMindDataModelBase):
    basic_info: DataModelBasicInfo
    entities: list[DataEntitySpec] = Field(default_factory=list)
    relationships: list[EntityRelationshipSpec] = Field(default_factory=list)
    indexes: list[DataIndexSpec] = Field(default_factory=list)
    constraints: list[DataConstraintSpec] = Field(default_factory=list)
    status_models: list[StatusModelSpec] = Field(default_factory=list)
    page_bindings: list[PageDataBindingSpec] = Field(default_factory=list)
    api_usage_hints: list[ApiDataUsageHint] = Field(default_factory=list)
    data_security: list[DataSecuritySpec] = Field(default_factory=list)
    migration_hints: list[DataMigrationHint] = Field(default_factory=list)
    downstream_hints: DataModelDownstreamHints
    development_notes: list[str] = Field(default_factory=list)
    testing_notes: list[str] = Field(default_factory=list)
    analysis_summary: str


class DataModelArtifact(DataModelLlmOutput):
    artifact_type: str = "DATA_MODEL"
    markdown: str


DATA_MODEL_LLM_JSON_SCHEMA = DataModelLlmOutput.model_json_schema()