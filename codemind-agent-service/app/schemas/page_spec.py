from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator


class PageBasicInfo(BaseModel):
    page_key: str = Field(description="页面唯一标识，例如 order_list_page")
    page_name: str = Field(description="页面名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    page_type: Literal[
        "LIST",
        "DETAIL",
        "FORM",
        "DASHBOARD",
        "WORKBENCH",
        "SETTING",
        "WIZARD",
        "OTHER",
    ] = Field(description="页面类型")
    route_suggestion: str = Field(description="前端路由建议")
    page_goal: str = Field(description="页面目标")
    primary_user_role: str = Field(description="主要使用角色")


class PageLayoutSection(BaseModel):
    section_key: str
    section_name: str
    position: str
    description: str
    components: list[str] = Field(default_factory=list)


class PageComponentSpec(BaseModel):
    component_key: str
    component_name: str
    component_type: str
    description: str
    data_source: str
    interactions: list[str] = Field(default_factory=list)
    display_rules: list[str] = Field(default_factory=list)


class PageActionSpec(BaseModel):
    action_key: str
    action_name: str
    trigger_position: str
    actor: str
    description: str
    preconditions: list[str] = Field(default_factory=list)
    business_rules: list[str] = Field(default_factory=list)
    success_feedback: str
    failure_feedback: str


class PageFormFieldSpec(BaseModel):
    field_key: str
    field_name: str
    field_type: str
    required: bool = False
    default_value: str | None = None
    validation_rules: list[str] = Field(default_factory=list)
    description: str

    @field_validator("default_value", mode="before")
    @classmethod
    def normalize_default_value(cls, value):
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


class PageTableColumnSpec(BaseModel):
    column_key: str
    column_name: str
    data_field: str
    display_type: str
    sortable: bool = False
    filterable: bool = False
    description: str


class PageApiBinding(BaseModel):
    api_key: str
    api_name: str
    method: str
    path_suggestion: str
    trigger_timing: str
    request_summary: str
    response_usage: str


class PageStateSpec(BaseModel):
    state_key: str
    state_name: str
    description: str
    ui_behavior: str


class PagePermissionSpec(BaseModel):
    permission_key: str
    permission_name: str
    roles: list[str] = Field(default_factory=list)
    restricted_elements: list[str] = Field(default_factory=list)
    description: str


class PageExceptionSpec(BaseModel):
    exception_key: str
    title: str
    scenario: str
    ui_feedback: str
    recovery_action: str


class PageAcceptanceCriterion(BaseModel):
    criterion_key: str
    description: str
    verification_method: str


class PageDownstreamHints(BaseModel):
    data_model_hints: list[str] = Field(default_factory=list)
    api_contract_hints: list[str] = Field(default_factory=list)
    test_spec_hints: list[str] = Field(default_factory=list)
    frontend_component_hints: list[str] = Field(default_factory=list)
    dev_handoff_hints: list[str] = Field(default_factory=list)


class PageSpecLlmOutput(BaseModel):
    basic_info: PageBasicInfo
    layout_sections: list[PageLayoutSection] = Field(default_factory=list)
    component_specs: list[PageComponentSpec] = Field(default_factory=list)
    action_specs: list[PageActionSpec] = Field(default_factory=list)
    form_fields: list[PageFormFieldSpec] = Field(default_factory=list)
    table_columns: list[PageTableColumnSpec] = Field(default_factory=list)
    api_bindings: list[PageApiBinding] = Field(default_factory=list)
    page_states: list[PageStateSpec] = Field(default_factory=list)
    permission_specs: list[PagePermissionSpec] = Field(default_factory=list)
    exception_specs: list[PageExceptionSpec] = Field(default_factory=list)
    acceptance_criteria: list[PageAcceptanceCriterion] = Field(default_factory=list)
    downstream_hints: PageDownstreamHints
    development_notes: list[str] = Field(default_factory=list)
    testing_notes: list[str] = Field(default_factory=list)
    analysis_summary: str


class PageSpecArtifact(PageSpecLlmOutput):
    artifact_type: str = "PAGE_SPEC"
    markdown: str


PAGE_SPEC_LLM_JSON_SCHEMA = PageSpecLlmOutput.model_json_schema()