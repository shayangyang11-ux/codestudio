from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


ModuleTreePatchOperationType = Literal[
    "ADD_NODE",
    "UPDATE_NODE",
    "MOVE_NODE",
    "DELETE_NODE",
]


class ModuleTreePatchOperation(BaseModel):
    op: ModuleTreePatchOperationType
    nodeId: str | None = None
    parentId: str | None = None
    newParentId: str | None = None
    moduleName: str | None = None
    moduleKey: str | None = None
    moduleType: str | None = None
    sortOrder: int | None = None
    extra: dict[str, Any] = Field(default_factory=dict)


class ModuleTreePatchImpact(BaseModel):
    summary: str = ""
    affectedNodeIds: list[str] = Field(default_factory=list)
    affectedArtifacts: list[str] = Field(default_factory=list)
    riskNotes: list[str] = Field(default_factory=list)


class ModuleTreePatchProposalArtifact(BaseModel):
    artifact_type: str = "MODULE_TREE_PATCH_PROPOSAL"
    description: str = ""
    source: str = "AI_TREE_PATCH"
    operations: list[ModuleTreePatchOperation] = Field(default_factory=list)
    impact: ModuleTreePatchImpact = Field(default_factory=ModuleTreePatchImpact)
    markdown: str = ""
    analysis_summary: str = ""


MODULE_TREE_PATCH_PROPOSAL_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": [
        "description",
        "source",
        "operations",
        "impact",
        "analysis_summary",
    ],
    "properties": {
        "description": {
            "type": "string",
        },
        "source": {
            "type": "string",
            "enum": ["AI_TREE_PATCH"],
        },
        "operations": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "op",
                    "nodeId",
                    "parentId",
                    "newParentId",
                    "moduleName",
                    "moduleKey",
                    "moduleType",
                    "sortOrder",
                    "extra",
                ],
                "properties": {
                    "op": {
                        "type": "string",
                        "enum": [
                            "ADD_NODE",
                            "UPDATE_NODE",
                            "MOVE_NODE",
                            "DELETE_NODE",
                        ],
                    },
                    "nodeId": {
                        "type": ["string", "null"],
                    },
                    "parentId": {
                        "type": ["string", "null"],
                    },
                    "newParentId": {
                        "type": ["string", "null"],
                    },
                    "moduleName": {
                        "type": ["string", "null"],
                    },
                    "moduleKey": {
                        "type": ["string", "null"],
                    },
                    "moduleType": {
                        "type": ["string", "null"],
                        "enum": [
                            "DOMAIN",
                            "MODULE",
                            "SUBMODULE",
                            "PAGE",
                            "FEATURE",
                            None,
                        ],
                    },
                    "sortOrder": {
                        "type": ["integer", "null"],
                    },
                    "extra": {
                        "type": "object",
                    },
                },
            },
        },
        "impact": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "summary",
                "affectedNodeIds",
                "affectedArtifacts",
                "riskNotes",
            ],
            "properties": {
                "summary": {
                    "type": "string",
                },
                "affectedNodeIds": {
                    "type": "array",
                    "items": {
                        "type": "string",
                    },
                },
                "affectedArtifacts": {
                    "type": "array",
                    "items": {
                        "type": "string",
                    },
                },
                "riskNotes": {
                    "type": "array",
                    "items": {
                        "type": "string",
                    },
                },
            },
        },
        "analysis_summary": {
            "type": "string",
        },
    },
}