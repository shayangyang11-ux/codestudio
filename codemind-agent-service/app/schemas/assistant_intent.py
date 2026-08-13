from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


AssistantIntentType = Literal[
    "GENERATE_MODULE_TREE",
    "PROPOSE_MODULE_TREE_PATCH",
    "GENERATE_REQUIREMENT_ARTIFACT",
    "EXPLAIN_CURRENT_ARTIFACT",
    "ANALYZE_ATTACHMENT",
    "GENERAL_QA",
]

AssistantRequirementArtifactType = Literal[
    "MODULE_PRD",
    "BUSINESS_FLOW",
    "PAGE_MAP",
    "PAGE_SPEC",
    "DATA_MODEL",
    "API_CONTRACT",
    "TEST_SPEC",
    "UI_CONTRACT",
    "DEV_HANDOFF_PACKAGE",
    "FULL_CHAIN",
]

AssistantRequirementTaskType = Literal[
    "GENERATE_MODULE_PRD",
    "GENERATE_BUSINESS_FLOW",
    "GENERATE_PAGE_MAP",
    "GENERATE_PAGE_SPEC",
    "GENERATE_DATA_MODEL",
    "GENERATE_API_CONTRACT",
    "GENERATE_TEST_SPEC",
    "GENERATE_UI_CONTRACT",
    "GENERATE_DEV_HANDOFF_PACKAGE",
]


class AssistantIntentClassificationArtifact(BaseModel):
    artifact_type: str = "ASSISTANT_INTENT_CLASSIFICATION"
    intent: AssistantIntentType = "GENERAL_QA"
    confidence: float = 0.0
    source: str = "LLM"
    reason: str = ""
    normalizedMessage: str = ""
    suggestedReply: str = ""
    requiresUserConfirmation: bool = False
    artifactType: AssistantRequirementArtifactType | None = None
    taskType: AssistantRequirementTaskType | None = None
    fullChain: bool = False
    coreOnly: bool = True
    riskNotes: list[str] = Field(default_factory=list)


ASSISTANT_INTENT_CLASSIFICATION_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": [
        "intent",
        "confidence",
        "source",
        "reason",
        "normalizedMessage",
        "suggestedReply",
        "requiresUserConfirmation",
        "artifactType",
        "taskType",
        "fullChain",
        "coreOnly",
        "riskNotes",
    ],
    "properties": {
        "intent": {
            "type": "string",
            "enum": [
                "GENERATE_MODULE_TREE",
                "PROPOSE_MODULE_TREE_PATCH",
                "GENERATE_REQUIREMENT_ARTIFACT",
                "EXPLAIN_CURRENT_ARTIFACT",
                "ANALYZE_ATTACHMENT",
                "GENERAL_QA",
            ],
        },
        "confidence": {
            "type": "number",
            "minimum": 0,
            "maximum": 1,
        },
        "source": {
            "type": "string",
            "enum": ["LLM"],
        },
        "reason": {
            "type": "string",
        },
        "normalizedMessage": {
            "type": "string",
        },
        "suggestedReply": {
            "type": "string",
        },
        "requiresUserConfirmation": {
            "type": "boolean",
        },
        "artifactType": {
            "type": ["string", "null"],
            "enum": [
                "MODULE_PRD",
                "BUSINESS_FLOW",
                "PAGE_MAP",
    "PAGE_SPEC",
                "DATA_MODEL",
                "API_CONTRACT",
                "TEST_SPEC",
                "UI_CONTRACT",
                "DEV_HANDOFF_PACKAGE",
                "FULL_CHAIN",
                None,
            ],
        },
        "taskType": {
            "type": ["string", "null"],
            "enum": [
                "GENERATE_MODULE_PRD",
                "GENERATE_BUSINESS_FLOW",
                "GENERATE_PAGE_MAP",
    "GENERATE_PAGE_SPEC",
                "GENERATE_DATA_MODEL",
                "GENERATE_API_CONTRACT",
                "GENERATE_TEST_SPEC",
                "GENERATE_UI_CONTRACT",
                "GENERATE_DEV_HANDOFF_PACKAGE",
                None,
            ],
        },
        "fullChain": {
            "type": "boolean",
        },
        "coreOnly": {
            "type": "boolean",
        },
        "riskNotes": {
            "type": "array",
            "items": {
                "type": "string",
            },
        },
    },
}