from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class FlowBasicInfo(BaseModel):
    flow_key: str = Field(description="业务流程唯一标识，例如 order_create_flow")
    flow_name: str = Field(description="业务流程名称")
    related_module_key: str = Field(description="关联模块 Key")
    related_module_name: str = Field(description="关联模块名称")
    flow_goal: str = Field(description="流程目标")
    trigger_event: str = Field(description="触发事件")
    primary_actor: str = Field(description="主要参与角色")
    boundary: str = Field(description="流程边界说明")


class FlowParticipant(BaseModel):
    role_key: str
    role_name: str
    responsibility: str


class FlowStep(BaseModel):
    step_key: str
    step_name: str
    step_order: int
    actor: str
    description: str
    input_data: list[str] = Field(default_factory=list)
    output_data: list[str] = Field(default_factory=list)
    business_rules: list[str] = Field(default_factory=list)
    exception_points: list[str] = Field(default_factory=list)


class FlowBranch(BaseModel):
    branch_key: str
    branch_name: str
    condition: str
    description: str
    target_step_key: str | None = None


class FlowStateTransition(BaseModel):
    from_state: str
    event: str
    to_state: str
    description: str


class FlowExceptionScenario(BaseModel):
    scenario_key: str
    title: str
    description: str
    handling_strategy: str
    recovery_action: str


class FlowDataInteraction(BaseModel):
    entity_key: str
    entity_name: str
    operation: Literal["CREATE", "READ", "UPDATE", "DELETE", "QUERY", "OTHER"]
    description: str
    related_step_keys: list[str] = Field(default_factory=list)


class FlowApiInteraction(BaseModel):
    api_key: str
    api_name: str
    method: str
    path_suggestion: str
    description: str
    related_step_keys: list[str] = Field(default_factory=list)


class FlowPermissionPoint(BaseModel):
    permission_key: str
    permission_name: str
    description: str
    roles: list[str] = Field(default_factory=list)
    related_step_keys: list[str] = Field(default_factory=list)


class DownstreamHints(BaseModel):
    page_spec_hints: list[str] = Field(default_factory=list)
    data_model_hints: list[str] = Field(default_factory=list)
    api_contract_hints: list[str] = Field(default_factory=list)
    test_spec_hints: list[str] = Field(default_factory=list)
    dev_handoff_hints: list[str] = Field(default_factory=list)


class BusinessFlowLlmOutput(BaseModel):
    basic_info: FlowBasicInfo
    participants: list[FlowParticipant] = Field(default_factory=list)
    main_steps: list[FlowStep] = Field(default_factory=list)
    branches: list[FlowBranch] = Field(default_factory=list)
    state_transitions: list[FlowStateTransition] = Field(default_factory=list)
    exception_scenarios: list[FlowExceptionScenario] = Field(default_factory=list)
    data_interactions: list[FlowDataInteraction] = Field(default_factory=list)
    api_interactions: list[FlowApiInteraction] = Field(default_factory=list)
    permission_points: list[FlowPermissionPoint] = Field(default_factory=list)
    downstream_hints: DownstreamHints
    development_notes: list[str] = Field(default_factory=list)
    testing_notes: list[str] = Field(default_factory=list)
    analysis_summary: str


class BusinessFlowArtifact(BusinessFlowLlmOutput):
    artifact_type: str = "BUSINESS_FLOW"
    markdown: str


BUSINESS_FLOW_LLM_JSON_SCHEMA = BusinessFlowLlmOutput.model_json_schema()