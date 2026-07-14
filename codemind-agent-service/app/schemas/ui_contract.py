from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


UI_CONTRACT_LLM_JSON_SCHEMA: dict[str, Any] = {
    "type": "object",
    "required": [
        "schemaVersion",
        "artifactType",
        "module",
        "routes",
        "pages",
        "mockData",
        "apiBindings",
        "developmentRules",
        "analysisSummary",
    ],
    "properties": {
        "schemaVersion": {
            "type": "string",
            "description": "UI contract schema version. Use 1.0."
        },
        "artifactType": {
            "type": "string",
            "enum": ["UI_CONTRACT"],
        },
        "module": {
            "type": "object",
            "description": "Current PAGE node and its module tree metadata.",
        },
        "routes": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "required": ["path", "pageId", "title"],
                "properties": {
                    "path": {"type": "string"},
                    "pageId": {"type": "string"},
                    "title": {"type": "string"},
                },
            },
        },
        "pages": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "required": ["pageId", "pageName", "layout", "description", "components"],
                "properties": {
                    "pageId": {"type": "string"},
                    "pageName": {"type": "string"},
                    "layout": {
                        "type": "string",
                        "description": "table-list / form-detail / detail-tabs / approval-workbench / dashboard / form-table."
                    },
                    "description": {"type": "string"},
                    "components": {
                        "type": "array",
                        "minItems": 2,
                        "items": {
                            "type": "object",
                            "description": "Renderable component. Supported type: form, table, card, description, steps, tabs, modal, button, toolbar, section.",
                        },
                    },
                    "states": {
                        "type": "object",
                    },
                },
            },
        },
        "mockData": {
            "type": "object",
            "description": "Named arrays or objects used by the Mock Preview Runtime."
        },
        "apiBindings": {
            "type": "array",
            "items": {"type": "object"},
            "description": "API bindings used by component actions and data sources."
        },
        "developmentRules": {
            "type": "object",
            "description": "Rules that downstream development agents must obey."
        },
        "analysisSummary": {
            "type": "string",
        },
    },
}


class UiContractArtifact(BaseModel):
    model_config = ConfigDict(extra="allow")

    artifact_type: Literal["UI_CONTRACT"] = "UI_CONTRACT"
    schemaVersion: str = "1.0"
    module: dict[str, Any] = Field(default_factory=dict)
    routes: list[dict[str, Any]] = Field(default_factory=list)
    pages: list[dict[str, Any]] = Field(default_factory=list)
    mockData: dict[str, Any] = Field(default_factory=dict)
    apiBindings: list[dict[str, Any]] = Field(default_factory=list)
    developmentRules: dict[str, Any] = Field(default_factory=dict)
    analysisSummary: str = ""
    markdown: str = ""
