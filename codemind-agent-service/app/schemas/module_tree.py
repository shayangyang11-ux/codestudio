from __future__ import annotations

from typing import Literal

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
        description="DOMAIN / MODULE / SUBMODULE / PAGE / FEATURE."
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
        description="Only for FEATURE. query / create / update / delete / submit / approve / reject / export / import / navigate / validate.",
    )


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