from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


ModuleNodeType = Literal[
    "DOMAIN",
    "MODULE",
    "SUBMODULE",
    "PAGE",
    "FEATURE",
]


class FlatModuleNode(BaseModel):
    key: str = Field(description="Stable key, lowercase English with underscores.")
    parent_key: str = Field(description="Parent key. Empty string means root node.")
    name: str = Field(description="Chinese display name.")
    type: ModuleNodeType = Field(
        description="A9 visible tree: MODULE / FEATURE(function point) / PAGE. DOMAIN and SUBMODULE are legacy aliases normalized to MODULE."
    )
    description: str = Field(description="Node responsibility summary.")
    sort_order: int = Field(description="Sort order among siblings.")

    page_kind: str | None = Field(
        default=None,
        description="Only for PAGE. LIST_PAGE / FORM_PAGE / DETAIL_PAGE / APPROVAL_PAGE / DASHBOARD_PAGE / SETTINGS_PAGE.",
    )
    route_path: str | None = Field(
        default=None,
        description="Only for PAGE. Route path such as /production/tasks/create.",
    )
    layout_type: str | None = Field(
        default=None,
        description="Only for PAGE. table-list / form-detail / detail-tabs / approval-workbench / dashboard.",
    )
    primary_entity: str | None = Field(
        default=None,
        description="Main business entity controlled by this page.",
    )
    can_generate_ui_contract: bool = Field(
        default=False,
        description="True only for PAGE nodes.",
    )
    feature_role: str | None = Field(
        default=None,
        description="Only for FEATURE(function point). It describes a business capability, not a button, field, validation item, or UI component.",
    )

    requirement_context: dict[str, Any] | None = Field(default=None, description="Hidden MODULE requirement context.")
    function_context: dict[str, Any] | None = Field(default=None, description="Hidden FEATURE function context.")
    page_prototype_context: dict[str, Any] | None = Field(default=None, description="Hidden PAGE prototype context for UI_CONTRACT.")
    module_prototype_map: dict[str, Any] | None = Field(default=None, description="Hidden MODULE page/navigation map.")
    traceability_map: dict[str, Any] | None = Field(default=None, description="Hidden lineage and coverage metadata.")


class ModuleNode(BaseModel):
    key: str
    name: str
    type: ModuleNodeType = "MODULE"
    description: str | None = None

    pageKind: str | None = None
    routePath: str | None = None
    layoutType: str | None = None
    primaryEntity: str | None = None
    canGenerateUiContract: bool = False
    featureRole: str | None = None

    requirementContext: dict[str, Any] | None = None
    functionContext: dict[str, Any] | None = None
    pagePrototypeContext: dict[str, Any] | None = None
    modulePrototypeMap: dict[str, Any] | None = None
    traceabilityMap: dict[str, Any] | None = None

    children: list["ModuleNode"] = Field(default_factory=list)


class ModuleTreeLlmOutput(BaseModel):
    modules: list[FlatModuleNode]
    markdown: str
    analysis_summary: str


class ModuleTreeArtifact(BaseModel):
    artifact_type: str = "MODULE_TREE"
    modules: list[ModuleNode]
    flat_modules: list[FlatModuleNode]
    markdown: str
    analysis_summary: str
    generation_plan: dict[str, Any] | None = Field(
        default=None,
        description="Internal generation recommendation plan: skeleton / full blueprint / organize-first.",
    )


MODULE_TREE_LLM_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": [
        "modules",
        "markdown",
        "analysis_summary",
    ],
    "properties": {
        "modules": {
            "type": "array",
            "minItems": 6,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "key",
                    "parent_key",
                    "name",
                    "type",
                    "description",
                    "sort_order",
                    "can_generate_ui_contract",
                ],
                "properties": {
                    "key": {
                        "type": "string",
                        "description": "Stable lowercase English key, e.g. production_task_create_page.",
                    },
                    "parent_key": {
                        "type": "string",
                        "description": "Parent key. Use empty string for root nodes.",
                    },
                    "name": {
                        "type": "string",
                        "description": "Chinese node name.",
                    },
                    "type": {
                        "type": "string",
                        "enum": [
                            "DOMAIN",
                            "MODULE",
                            "SUBMODULE",
                            "PAGE",
                            "FEATURE",
                        ],
                    },
                    "description": {
                        "type": "string",
                    },
                    "sort_order": {
                        "type": "integer",
                    },
                    "page_kind": {
                        "type": ["string", "null"],
                        "enum": [
                            "LIST_PAGE",
                            "FORM_PAGE",
                            "DETAIL_PAGE",
                            "APPROVAL_PAGE",
                            "DASHBOARD_PAGE",
                            "SETTINGS_PAGE",
                            None,
                        ],
                    },
                    "route_path": {
                        "type": ["string", "null"],
                    },
                    "layout_type": {
                        "type": ["string", "null"],
                    },
                    "primary_entity": {
                        "type": ["string", "null"],
                    },
                    "can_generate_ui_contract": {
                        "type": "boolean",
                    },
                    "feature_role": {
                        "type": ["string", "null"],
                    },
                    "requirement_context": {"type": ["object", "null"], "additionalProperties": True},
                    "function_context": {"type": ["object", "null"], "additionalProperties": True},
                    "page_prototype_context": {"type": ["object", "null"], "additionalProperties": True},
                    "module_prototype_map": {"type": ["object", "null"], "additionalProperties": True},
                    "traceability_map": {"type": ["object", "null"], "additionalProperties": True},
                },
            },
        },
        "markdown": {
            "type": "string",
            "description": "Human-readable module tree summary in Chinese Markdown.",
        },
        "analysis_summary": {
            "type": "string",
            "description": "Short explanation of decomposition logic.",
        },
    },
}