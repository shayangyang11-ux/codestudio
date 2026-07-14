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


class CodeMindApiContractBase(BaseModel):
    model_config = ConfigDict(
        coerce_numbers_to_str=True,
        extra="ignore",
    )


class ApiContractBasicInfo(CodeMindApiContractBase):
    contract_key: str = Field(description="接口契约资产唯一标识，例如 order_api_contract")
    contract_name: str = Field(description="接口契约名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    api_style: str = Field(description="接口风格，例如 RESTful、RPC、GraphQL")
    base_path: str = Field(description="接口基础路径，例如 /api/orders")
    design_goal: str = Field(description="接口设计目标")
    version_strategy: str = Field(description="接口版本策略，例如 /api/v1 或 Header 版本控制")


class ApiRequestParameter(CodeMindApiContractBase):
    param_key: str = Field(description="参数唯一标识")
    name: str = Field(description="参数名称")
    in_location: str = Field(description="参数位置，例如 path、query、header、cookie")
    data_type: str = Field(description="参数类型，例如 string、uuid、integer、boolean")
    required: bool = Field(default=False, description="是否必填")
    default_value: OptionalText = Field(default=None, description="默认值")
    description: str = Field(description="参数说明")
    validation_rules: StringList = Field(default_factory=list, max_length=3, description="校验规则")

    @field_validator("validation_rules", mode="before")
    @classmethod
    def limit_validation_rules(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiRequestBodyField(CodeMindApiContractBase):
    field_key: str = Field(description="请求体字段唯一标识")
    name: str = Field(description="请求体字段名")
    data_type: str = Field(description="字段类型")
    required: bool = Field(default=False, description="是否必填")
    default_value: OptionalText = Field(default=None, description="默认值")
    description: str = Field(description="字段说明")
    validation_rules: StringList = Field(default_factory=list, max_length=3, description="校验规则")
    related_entity_field: OptionalText = Field(default=None, description="关联数据模型字段")

    @field_validator("validation_rules", mode="before")
    @classmethod
    def limit_validation_rules(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiResponseField(CodeMindApiContractBase):
    field_key: str = Field(description="响应字段唯一标识")
    name: str = Field(description="响应字段名")
    data_type: str = Field(description="字段类型")
    nullable: bool = Field(default=True, description="是否可能为空")
    description: str = Field(description="字段说明")
    related_entity_field: OptionalText = Field(default=None, description="关联数据模型字段")


class ApiErrorCodeSpec(CodeMindApiContractBase):
    error_key: str = Field(description="错误码唯一标识")
    http_status: str = Field(description="HTTP 状态码")
    error_code: str = Field(description="业务错误码")
    message: str = Field(description="错误提示")
    scenario: str = Field(description="触发场景")
    handling_suggestion: str = Field(description="前端或调用方处理建议")


class ApiEndpointSpec(CodeMindApiContractBase):
    api_key: str = Field(description="接口唯一标识，例如 create_order")
    api_name: str = Field(description="接口名称")
    method: str = Field(description="HTTP 方法，例如 GET、POST、PUT、PATCH、DELETE")
    path: str = Field(description="接口路径，例如 /api/orders/{orderId}")
    summary: str = Field(description="接口摘要")
    business_purpose: str = Field(description="接口业务目的")

    related_page_keys: StringList = Field(default_factory=list, max_length=3, description="关联页面 Key")
    related_entity_keys: StringList = Field(default_factory=list, max_length=3, description="关联数据实体 Key")
    related_flow_step_keys: StringList = Field(default_factory=list, max_length=3, description="关联业务流程步骤 Key")
    permission_keys: StringList = Field(default_factory=list, max_length=3, description="所需权限 Key")

    preconditions: StringList = Field(default_factory=list, max_length=2, description="业务前置条件")
    postconditions: StringList = Field(default_factory=list, max_length=2, description="业务后置条件")

    request_parameters: list[ApiRequestParameter] = Field(default_factory=list, max_length=2, description="请求参数")
    request_body_fields: list[ApiRequestBodyField] = Field(default_factory=list, max_length=4, description="请求体字段")
    response_fields: list[ApiResponseField] = Field(default_factory=list, max_length=4, description="响应字段")

    success_status_code: str = Field(description="成功 HTTP 状态码，例如 200、201、204")

    error_codes: list[ApiErrorCodeSpec] = Field(default_factory=list, max_length=3, description="错误码列表")

    idempotent: bool = Field(default=False, description="是否幂等")
    transaction_required: bool = Field(default=False, description="是否需要事务")
    transaction_boundary: str = Field(description="事务边界说明")
    rate_limit_rule: OptionalText = Field(default=None, description="限流规则")
    audit_log_required: bool = Field(default=False, description="是否需要审计日志")

    test_focus: StringList = Field(default_factory=list, max_length=3, description="测试关注点")

    @field_validator("method", mode="before")
    @classmethod
    def normalize_method(cls, value: Any) -> str:
        if value is None:
            return "GET"
        return str(value).upper()

    @field_validator(
        "related_page_keys",
        "related_entity_keys",
        "related_flow_step_keys",
        "permission_keys",
        mode="before",
    )
    @classmethod
    def limit_three_string_items(cls, value: Any) -> Any:
        return limit_list(value, 3)

    @field_validator("preconditions", "postconditions", mode="before")
    @classmethod
    def limit_two_string_items(cls, value: Any) -> Any:
        return limit_list(value, 2)

    @field_validator("request_parameters", mode="before")
    @classmethod
    def limit_request_parameters(cls, value: Any) -> Any:
        return limit_list(value, 2)

    @field_validator("request_body_fields", "response_fields", mode="before")
    @classmethod
    def limit_four_fields(cls, value: Any) -> Any:
        return limit_list(value, 4)

    @field_validator("error_codes", "test_focus", mode="before")
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiPermissionRequirement(CodeMindApiContractBase):
    permission_key: str = Field(description="权限唯一标识")
    permission_name: str = Field(description="权限名称")
    roles: StringList = Field(default_factory=list, max_length=3, description="允许角色")
    api_keys: StringList = Field(default_factory=list, max_length=3, description="涉及接口 Key")
    description: str = Field(description="权限说明")

    @field_validator("roles", "api_keys", mode="before")
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiDataMapping(CodeMindApiContractBase):
    mapping_key: str = Field(description="数据映射唯一标识")
    api_key: str = Field(description="接口 Key")
    entity_key: str = Field(description="实体 Key")
    mapping_type: str = Field(description="映射类型，例如 REQUEST_TO_ENTITY、ENTITY_TO_RESPONSE")
    field_mappings: StringList = Field(default_factory=list, max_length=4, description="字段映射说明")
    description: str = Field(description="映射说明")

    @field_validator("field_mappings", mode="before")
    @classmethod
    def limit_field_mappings(cls, value: Any) -> Any:
        return limit_list(value, 4)


class ApiWorkflowBinding(CodeMindApiContractBase):
    binding_key: str = Field(description="流程绑定唯一标识")
    api_key: str = Field(description="接口 Key")
    flow_step_key: str = Field(description="流程步骤 Key")
    trigger_timing: str = Field(description="触发时机")
    description: str = Field(description="接口与流程步骤的关系说明")


class ApiSecurityRequirement(CodeMindApiContractBase):
    security_key: str = Field(description="安全要求唯一标识")
    api_key: str = Field(description="接口 Key")
    security_type: str = Field(description="安全类型，例如 AUTHENTICATION、AUTHORIZATION、ENCRYPTION、DESENSITIZATION、AUDIT")
    description: str = Field(description="安全要求说明")
    implementation_hint: str = Field(description="实现建议")


class ApiCompatibilityRequirement(CodeMindApiContractBase):
    compatibility_key: str = Field(description="兼容性要求唯一标识")
    api_key: str = Field(description="接口 Key")
    scenario: str = Field(description="兼容场景")
    requirement: str = Field(description="兼容性要求")
    recommendation: str = Field(description="处理建议")


class ApiContractDownstreamHints(CodeMindApiContractBase):
    backend_code_hints: StringList = Field(default_factory=list, max_length=3)
    frontend_integration_hints: StringList = Field(default_factory=list, max_length=3)
    test_spec_hints: StringList = Field(default_factory=list, max_length=3)
    dev_handoff_hints: StringList = Field(default_factory=list, max_length=3)
    documentation_hints: StringList = Field(default_factory=list, max_length=3)

    @field_validator(
        "backend_code_hints",
        "frontend_integration_hints",
        "test_spec_hints",
        "dev_handoff_hints",
        "documentation_hints",
        mode="before",
    )
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiContractLlmOutput(CodeMindApiContractBase):
    basic_info: ApiContractBasicInfo

    endpoints: list[ApiEndpointSpec] = Field(default_factory=list, max_length=3)
    permission_requirements: list[ApiPermissionRequirement] = Field(default_factory=list, max_length=3)
    data_mappings: list[ApiDataMapping] = Field(default_factory=list, max_length=3)
    workflow_bindings: list[ApiWorkflowBinding] = Field(default_factory=list, max_length=3)
    security_requirements: list[ApiSecurityRequirement] = Field(default_factory=list, max_length=3)
    compatibility_requirements: list[ApiCompatibilityRequirement] = Field(default_factory=list, max_length=2)

    downstream_hints: ApiContractDownstreamHints

    development_notes: StringList = Field(default_factory=list, max_length=5)
    testing_notes: StringList = Field(default_factory=list, max_length=5)
    analysis_summary: str

    @field_validator("endpoints", mode="before")
    @classmethod
    def limit_endpoints(cls, value: Any) -> Any:
        return limit_list(value, 3)

    @field_validator(
        "permission_requirements",
        "data_mappings",
        "workflow_bindings",
        "security_requirements",
        mode="before",
    )
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)

    @field_validator("compatibility_requirements", mode="before")
    @classmethod
    def limit_two_items(cls, value: Any) -> Any:
        return limit_list(value, 2)

    @field_validator("development_notes", "testing_notes", mode="before")
    @classmethod
    def limit_five_string_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class ApiContractArtifact(ApiContractLlmOutput):
    artifact_type: str = "API_CONTRACT"
    markdown: str


API_CONTRACT_LLM_JSON_SCHEMA = ApiContractLlmOutput.model_json_schema()