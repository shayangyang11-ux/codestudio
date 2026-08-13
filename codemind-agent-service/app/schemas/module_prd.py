from typing import Any

from pydantic import BaseModel, Field


class ModuleBasicInfo(BaseModel):
    module_key: str = Field(description="模块稳定 key。")
    module_name: str = Field(description="模块中文名称。")
    module_type: str = Field(description="模块类型，例如 DOMAIN_MODULE / FEATURE_MODULE / PAGE_MODULE / INTEGRATION_MODULE / INFRA_MODULE。")
    module_position: str = Field(description="该模块在整体系统中的位置。")
    module_goal: str = Field(description="该模块要解决的核心业务目标。")
    responsibility_boundary: str = Field(description="该模块负责什么、不负责什么。")


class UserRole(BaseModel):
    role_key: str
    role_name: str
    description: str


class FunctionalRequirement(BaseModel):
    requirement_key: str
    title: str
    description: str
    priority: str = Field(description="MUST / SHOULD / COULD。")
    input_description: str
    output_description: str
    preconditions: list[str]
    postconditions: list[str]


class BusinessRule(BaseModel):
    rule_key: str
    title: str
    description: str
    trigger_condition: str
    expected_result: str


class StateRule(BaseModel):
    state_key: str
    state_name: str
    description: str
    allowed_transitions: list[str]


class PageRequirement(BaseModel):
    page_key: str
    page_name: str
    description: str
    main_components: list[str]
    user_actions: list[str]
    # A9.10.14.4.3.14.3_MODULE_PRD_PAGE_TREE_SYNC_FIX
    # MODULE_PRD must be a readable PRD plus a machine-readable page contract,
    # not only free text.  These fields are optional for backward compatibility;
    # ModulePrdAgent deterministically completes them from MODULE_TREE.
    page_type: str = ""
    route_path: str = ""
    layout_type: str = ""
    primary_entity: str = ""
    parent_feature_key: str = ""
    parent_feature_name: str = ""
    source_page_id: str = ""
    sync_status: str = ""
    filter_fields: list[str] = Field(default_factory=list)
    table_columns: list[str] = Field(default_factory=list)
    form_fields: list[str] = Field(default_factory=list)
    primary_actions: list[str] = Field(default_factory=list)
    row_actions: list[str] = Field(default_factory=list)
    navigation_targets: list[str] = Field(default_factory=list)


class DataRequirement(BaseModel):
    entity_key: str
    entity_name: str
    description: str
    important_fields: list[str]
    constraints: list[str]


class ApiRequirement(BaseModel):
    api_key: str
    api_name: str
    method: str
    path_suggestion: str
    description: str
    request_summary: str
    response_summary: str


class PermissionRequirement(BaseModel):
    permission_key: str
    permission_name: str
    description: str
    roles: list[str]


class ExceptionScenario(BaseModel):
    scenario_key: str
    title: str
    description: str
    handling_strategy: str


class AcceptanceCriterion(BaseModel):
    criterion_key: str
    description: str
    verification_method: str


class NonFunctionalRequirement(BaseModel):
    nfr_key: str
    category: str = Field(description="performance / security / availability / audit / logging / usability / compatibility。")
    description: str
    target: str


# A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
class CustomSection(BaseModel):
    section_id: str = Field(default="", description="用户自定义栏目稳定标识，普通用户不可见。")
    title: str = Field(default="补充说明", description="自由栏目标题，允许用户修改。")
    body: str = Field(default="", description="自由栏目正文，文档级内容，不直接影响模块树或原型。")
    order: int = Field(default=0, description="自由栏目展示顺序。")
    visibility: str = Field(default="USER_VISIBLE", description="USER_VISIBLE / HIDDEN。")
    source: str = Field(default="USER", description="USER / LEGACY_PRD / UPLOADED_PRD / SYSTEM。")
    impact_policy: str = Field(default="DOCUMENT_ONLY", description="默认 DOCUMENT_ONLY，不直接触发 PAGE_SPEC/UI_CONTRACT 更新。")


class DownstreamArtifactHints(BaseModel):
    business_flow_hints: list[str]
    page_spec_hints: list[str]
    data_model_hints: list[str]
    api_contract_hints: list[str]
    test_spec_hints: list[str]
    dev_handoff_hints: list[str]


class ModulePrdLlmOutput(BaseModel):
    module_basic_info: ModuleBasicInfo
    business_background: str
    user_roles: list[UserRole]
    functional_requirements: list[FunctionalRequirement]
    business_rules: list[BusinessRule]
    state_rules: list[StateRule]
    page_requirements: list[PageRequirement]
    data_requirements: list[DataRequirement]
    api_requirements: list[ApiRequirement]
    permission_requirements: list[PermissionRequirement]
    exception_scenarios: list[ExceptionScenario]
    acceptance_criteria: list[AcceptanceCriterion]
    non_functional_requirements: list[NonFunctionalRequirement]
    custom_sections: list[CustomSection] = Field(default_factory=list)
    downstream_artifact_hints: DownstreamArtifactHints
    development_notes: list[str]
    testing_notes: list[str]
    analysis_summary: str


class ModulePrdArtifact(BaseModel):
    artifact_type: str = "MODULE_PRD"
    # A9.10.14.4.2_PRD_SEMANTIC_BACKBONE
    # User-facing PRD remains markdown/module sections.  The fields below
    # are hidden/system-facing semantic indexes used by downstream agents.
    prd_contract_version: str = "2.0"
    public_document: dict[str, Any] = Field(default_factory=dict)
    structured_requirements: list[dict[str, Any]] = Field(default_factory=list)
    navigation_requirements: list[dict[str, Any]] = Field(default_factory=list)
    traceability_map: dict[str, Any] = Field(default_factory=dict)
    semantic_sync_policy: dict[str, Any] = Field(default_factory=dict)
    source_outline: dict[str, Any] = Field(default_factory=dict)
    module_basic_info: ModuleBasicInfo
    business_background: str
    user_roles: list[UserRole]
    functional_requirements: list[FunctionalRequirement]
    business_rules: list[BusinessRule]
    state_rules: list[StateRule]
    page_requirements: list[PageRequirement]
    data_requirements: list[DataRequirement]
    api_requirements: list[ApiRequirement]
    permission_requirements: list[PermissionRequirement]
    exception_scenarios: list[ExceptionScenario]
    acceptance_criteria: list[AcceptanceCriterion]
    non_functional_requirements: list[NonFunctionalRequirement]
    custom_sections: list[CustomSection] = Field(default_factory=list)
    downstream_artifact_hints: DownstreamArtifactHints
    development_notes: list[str]
    testing_notes: list[str]
    markdown: str
    analysis_summary: str


MODULE_PRD_LLM_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": [
        "module_basic_info",
        "business_background",
        "user_roles",
        "functional_requirements",
        "business_rules",
        "state_rules",
        "page_requirements",
        "data_requirements",
        "api_requirements",
        "permission_requirements",
        "exception_scenarios",
        "acceptance_criteria",
        "non_functional_requirements",
        "downstream_artifact_hints",
        "development_notes",
        "testing_notes",
        "analysis_summary",
    ],
    "properties": {
        "module_basic_info": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "module_key",
                "module_name",
                "module_type",
                "module_position",
                "module_goal",
                "responsibility_boundary",
            ],
            "properties": {
                "module_key": {"type": "string"},
                "module_name": {"type": "string"},
                "module_type": {"type": "string"},
                "module_position": {"type": "string"},
                "module_goal": {"type": "string"},
                "responsibility_boundary": {"type": "string"},
            },
        },
        "business_background": {"type": "string"},
        "user_roles": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["role_key", "role_name", "description"],
                "properties": {
                    "role_key": {"type": "string"},
                    "role_name": {"type": "string"},
                    "description": {"type": "string"},
                },
            },
        },
        "functional_requirements": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "requirement_key",
                    "title",
                    "description",
                    "priority",
                    "input_description",
                    "output_description",
                    "preconditions",
                    "postconditions",
                ],
                "properties": {
                    "requirement_key": {"type": "string"},
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "priority": {"type": "string", "enum": ["MUST", "SHOULD", "COULD"]},
                    "input_description": {"type": "string"},
                    "output_description": {"type": "string"},
                    "preconditions": {"type": "array", "items": {"type": "string"}},
                    "postconditions": {"type": "array", "items": {"type": "string"}},
                },
            },
        },
        "business_rules": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "rule_key",
                    "title",
                    "description",
                    "trigger_condition",
                    "expected_result",
                ],
                "properties": {
                    "rule_key": {"type": "string"},
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "trigger_condition": {"type": "string"},
                    "expected_result": {"type": "string"},
                },
            },
        },
        "state_rules": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "state_key",
                    "state_name",
                    "description",
                    "allowed_transitions",
                ],
                "properties": {
                    "state_key": {"type": "string"},
                    "state_name": {"type": "string"},
                    "description": {"type": "string"},
                    "allowed_transitions": {"type": "array", "items": {"type": "string"}},
                },
            },
        },
        "page_requirements": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "page_key",
                    "page_name",
                    "description",
                    "main_components",
                    "user_actions",
                ],
                "properties": {
                    "page_key": {"type": "string"},
                    "page_name": {"type": "string"},
                    "description": {"type": "string"},
                    "main_components": {"type": "array", "items": {"type": "string"}},
                    "user_actions": {"type": "array", "items": {"type": "string"}},
                    "page_type": {"type": "string"},
                    "route_path": {"type": "string"},
                    "layout_type": {"type": "string"},
                    "primary_entity": {"type": "string"},
                    "parent_feature_key": {"type": "string"},
                    "parent_feature_name": {"type": "string"},
                    "source_page_id": {"type": "string"},
                    "sync_status": {"type": "string"},
                    "filter_fields": {"type": "array", "items": {"type": "string"}},
                    "table_columns": {"type": "array", "items": {"type": "string"}},
                    "form_fields": {"type": "array", "items": {"type": "string"}},
                    "primary_actions": {"type": "array", "items": {"type": "string"}},
                    "row_actions": {"type": "array", "items": {"type": "string"}},
                    "navigation_targets": {"type": "array", "items": {"type": "string"}},
                },
            },
        },
        "data_requirements": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "entity_key",
                    "entity_name",
                    "description",
                    "important_fields",
                    "constraints",
                ],
                "properties": {
                    "entity_key": {"type": "string"},
                    "entity_name": {"type": "string"},
                    "description": {"type": "string"},
                    "important_fields": {"type": "array", "items": {"type": "string"}},
                    "constraints": {"type": "array", "items": {"type": "string"}},
                },
            },
        },
        "api_requirements": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "api_key",
                    "api_name",
                    "method",
                    "path_suggestion",
                    "description",
                    "request_summary",
                    "response_summary",
                ],
                "properties": {
                    "api_key": {"type": "string"},
                    "api_name": {"type": "string"},
                    "method": {"type": "string"},
                    "path_suggestion": {"type": "string"},
                    "description": {"type": "string"},
                    "request_summary": {"type": "string"},
                    "response_summary": {"type": "string"},
                },
            },
        },
        "permission_requirements": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "permission_key",
                    "permission_name",
                    "description",
                    "roles",
                ],
                "properties": {
                    "permission_key": {"type": "string"},
                    "permission_name": {"type": "string"},
                    "description": {"type": "string"},
                    "roles": {"type": "array", "items": {"type": "string"}},
                },
            },
        },
        "exception_scenarios": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "scenario_key",
                    "title",
                    "description",
                    "handling_strategy",
                ],
                "properties": {
                    "scenario_key": {"type": "string"},
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "handling_strategy": {"type": "string"},
                },
            },
        },
        "acceptance_criteria": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "criterion_key",
                    "description",
                    "verification_method",
                ],
                "properties": {
                    "criterion_key": {"type": "string"},
                    "description": {"type": "string"},
                    "verification_method": {"type": "string"},
                },
            },
        },
        "non_functional_requirements": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "nfr_key",
                    "category",
                    "description",
                    "target",
                ],
                "properties": {
                    "nfr_key": {"type": "string"},
                    "category": {"type": "string"},
                    "description": {"type": "string"},
                    "target": {"type": "string"},
                },
            },
        },

        "custom_sections": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["title", "body"],
                "properties": {
                    "section_id": {"type": "string"},
                    "title": {"type": "string"},
                    "body": {"type": "string"},
                    "order": {"type": "integer"},
                    "visibility": {"type": "string"},
                    "source": {"type": "string"},
                    "impact_policy": {"type": "string"},
                },
            },
        },
        "downstream_artifact_hints": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "business_flow_hints",
                "page_spec_hints",
                "data_model_hints",
                "api_contract_hints",
                "test_spec_hints",
                "dev_handoff_hints",
            ],
            "properties": {
                "business_flow_hints": {"type": "array", "items": {"type": "string"}},
                "page_spec_hints": {"type": "array", "items": {"type": "string"}},
                "data_model_hints": {"type": "array", "items": {"type": "string"}},
                "api_contract_hints": {"type": "array", "items": {"type": "string"}},
                "test_spec_hints": {"type": "array", "items": {"type": "string"}},
                "dev_handoff_hints": {"type": "array", "items": {"type": "string"}},
            },
        },
        "development_notes": {"type": "array", "items": {"type": "string"}},
        "testing_notes": {"type": "array", "items": {"type": "string"}},
        "analysis_summary": {"type": "string"},
    },
}