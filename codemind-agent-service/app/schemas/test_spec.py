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


class CodeMindTestSpecBase(BaseModel):
    model_config = ConfigDict(
        coerce_numbers_to_str=True,
        extra="ignore",
    )


class TestSpecBasicInfo(CodeMindTestSpecBase):
    test_spec_key: str = Field(description="测试规格资产唯一标识，例如 order_test_spec")
    test_spec_name: str = Field(description="测试规格名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    test_goal: str = Field(description="测试目标")
    test_strategy: str = Field(description="测试策略")
    test_level: str = Field(description="测试层级，例如 UNIT、API、E2E、INTEGRATION、ACCEPTANCE")


class TestScopeItem(CodeMindTestSpecBase):
    scope_key: str = Field(description="测试范围唯一标识")
    scope_name: str = Field(description="测试范围名称")
    scope_type: str = Field(description="测试范围类型，例如 PAGE、API、FLOW、DATA、PERMISSION")
    description: str = Field(description="范围说明")
    included: bool = Field(default=True, description="是否纳入本轮测试")


class AcceptanceCriterion(CodeMindTestSpecBase):
    criterion_key: str = Field(description="验收标准唯一标识")
    title: str = Field(description="验收标准标题")
    description: str = Field(description="验收标准说明")
    related_requirement_keys: StringList = Field(default_factory=list, max_length=3)
    verification_method: str = Field(description="验证方式，例如 手工验证、接口测试、自动化测试、数据核验")

    @field_validator("related_requirement_keys", mode="before")
    @classmethod
    def limit_related_requirement_keys(cls, value: Any) -> Any:
        return limit_list(value, 3)


class FunctionalTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="功能测试用例唯一标识")
    title: str = Field(description="用例标题")
    priority: str = Field(description="优先级，例如 P0、P1、P2")
    related_page_keys: StringList = Field(default_factory=list, max_length=3)
    related_flow_step_keys: StringList = Field(default_factory=list, max_length=3)
    preconditions: StringList = Field(default_factory=list, max_length=3)
    steps: StringList = Field(default_factory=list, max_length=4)
    expected_results: StringList = Field(default_factory=list, max_length=3)
    test_data: OptionalText = Field(default=None, description="测试数据说明")

    @field_validator("steps", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)

    @field_validator("expected_results", mode="before")
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ApiTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="接口测试用例唯一标识")
    title: str = Field(description="用例标题")
    api_key: str = Field(description="关联接口 Key")
    method: str = Field(description="HTTP 方法")
    path: str = Field(description="接口路径")
    request_focus: StringList = Field(default_factory=list, max_length=4)
    expected_status_codes: StringList = Field(default_factory=list, max_length=4)
    expected_response_checks: StringList = Field(default_factory=list, max_length=5)
    negative_cases: StringList = Field(default_factory=list, max_length=4)

    @field_validator("method", mode="before")
    @classmethod
    def normalize_method(cls, value: Any) -> str:
        if value is None:
            return "GET"
        return str(value).upper()

    @field_validator(
        "request_focus",
        "expected_status_codes",
        "negative_cases",
        mode="before",
    )
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)

    @field_validator("expected_response_checks", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class FlowTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="业务流程测试用例唯一标识")
    title: str = Field(description="用例标题")
    flow_key: str = Field(description="业务流程或流程步骤 Key")
    scenario: str = Field(description="流程场景")
    preconditions: StringList = Field(default_factory=list, max_length=3)
    steps: StringList = Field(default_factory=list, max_length=6)
    expected_state_changes: StringList = Field(default_factory=list, max_length=5)

    @field_validator("preconditions", mode="before")
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)

    @field_validator("steps", mode="before")
    @classmethod
    def limit_six_items(cls, value: Any) -> Any:
        return limit_list(value, 6)

    @field_validator("expected_state_changes", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)


class PermissionTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="权限测试用例唯一标识")
    title: str = Field(description="用例标题")
    role: str = Field(description="测试角色")
    permission_key: str = Field(description="权限 Key")
    allowed_actions: StringList = Field(default_factory=list, max_length=4)
    denied_actions: StringList = Field(default_factory=list, max_length=4)
    expected_result: str = Field(description="预期结果")

    @field_validator("allowed_actions", "denied_actions", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)


class DataValidationTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="数据校验测试用例唯一标识")
    title: str = Field(description="用例标题")
    entity_key: str = Field(description="实体 Key")
    field_name: str = Field(description="字段名")
    validation_rule: str = Field(description="校验规则")
    valid_examples: StringList = Field(default_factory=list, max_length=3)
    invalid_examples: StringList = Field(default_factory=list, max_length=3)
    expected_error: str = Field(description="预期错误提示")

    @field_validator("valid_examples", "invalid_examples", mode="before")
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class ExceptionTestCase(CodeMindTestSpecBase):
    case_key: str = Field(description="异常场景测试用例唯一标识")
    title: str = Field(description="用例标题")
    exception_type: str = Field(description="异常类型，例如 PARAM_ERROR、STATE_ERROR、PERMISSION_ERROR、SYSTEM_ERROR")
    trigger_condition: str = Field(description="触发条件")
    expected_behavior: str = Field(description="预期处理")
    related_api_keys: StringList = Field(default_factory=list, max_length=3)

    @field_validator("related_api_keys", mode="before")
    @classmethod
    def limit_related_api_keys(cls, value: Any) -> Any:
        return limit_list(value, 3)


class TestDataRequirement(CodeMindTestSpecBase):
    data_key: str = Field(description="测试数据标识")
    data_name: str = Field(description="测试数据名称")
    related_entity_key: OptionalText = Field(default=None, description="关联实体 Key")
    description: str = Field(description="测试数据说明")
    setup_method: str = Field(description="准备方式，例如 手工准备、SQL 初始化、接口创建")


class AutomationHint(CodeMindTestSpecBase):
    hint_key: str = Field(description="自动化提示唯一标识")
    target: str = Field(description="自动化目标，例如 API、E2E、UNIT、INTEGRATION")
    recommendation: str = Field(description="自动化建议")
    priority: str = Field(description="优先级")


class TestSpecDownstreamHints(CodeMindTestSpecBase):
    test_agent_hints: StringList = Field(default_factory=list, max_length=4)
    backend_fix_hints: StringList = Field(default_factory=list, max_length=4)
    frontend_fix_hints: StringList = Field(default_factory=list, max_length=4)
    dev_handoff_hints: StringList = Field(default_factory=list, max_length=4)

    @field_validator(
        "test_agent_hints",
        "backend_fix_hints",
        "frontend_fix_hints",
        "dev_handoff_hints",
        mode="before",
    )
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)


class TestSpecLlmOutput(CodeMindTestSpecBase):
    basic_info: TestSpecBasicInfo
    test_scope: list[TestScopeItem] = Field(default_factory=list, max_length=5)
    acceptance_criteria: list[AcceptanceCriterion] = Field(default_factory=list, max_length=5)
    functional_test_cases: list[FunctionalTestCase] = Field(default_factory=list, max_length=4)
    api_test_cases: list[ApiTestCase] = Field(default_factory=list, max_length=4)
    flow_test_cases: list[FlowTestCase] = Field(default_factory=list, max_length=3)
    permission_test_cases: list[PermissionTestCase] = Field(default_factory=list, max_length=3)
    data_validation_test_cases: list[DataValidationTestCase] = Field(default_factory=list, max_length=3)
    exception_test_cases: list[ExceptionTestCase] = Field(default_factory=list, max_length=3)
    test_data_requirements: list[TestDataRequirement] = Field(default_factory=list, max_length=3)
    automation_hints: list[AutomationHint] = Field(default_factory=list, max_length=3)
    downstream_hints: TestSpecDownstreamHints
    risk_notes: StringList = Field(default_factory=list, max_length=4)
    analysis_summary: str

    @field_validator("test_scope", "acceptance_criteria", mode="before")
    @classmethod
    def limit_five_items(cls, value: Any) -> Any:
        return limit_list(value, 5)

    @field_validator("functional_test_cases", "api_test_cases", "risk_notes", mode="before")
    @classmethod
    def limit_four_items(cls, value: Any) -> Any:
        return limit_list(value, 4)

    @field_validator(
        "flow_test_cases",
        "permission_test_cases",
        "data_validation_test_cases",
        "exception_test_cases",
        "test_data_requirements",
        "automation_hints",
        mode="before",
    )
    @classmethod
    def limit_three_items(cls, value: Any) -> Any:
        return limit_list(value, 3)


class TestSpecArtifact(TestSpecLlmOutput):
    artifact_type: str = "TEST_SPEC"
    markdown: str


TEST_SPEC_LLM_JSON_SCHEMA = TestSpecLlmOutput.model_json_schema()