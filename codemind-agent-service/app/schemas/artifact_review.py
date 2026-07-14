from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


ReviewRiskLevel = Literal["LOW", "MEDIUM", "HIGH", "BLOCKER"]
ReviewSeverity = Literal["INFO", "WARNING", "BLOCKER"]


class ArtifactReviewIssue(BaseModel):
    code: str = ""
    severity: ReviewSeverity = "WARNING"
    title: str = ""
    description: str = ""
    suggestion: str = ""
    artifactType: str | None = None


class ArtifactConsistencyCheck(BaseModel):
    dimension: str = ""
    level: ReviewSeverity = "INFO"
    result: str = ""
    evidence: str = ""
    recommendation: str = ""


class ArtifactReviewSuggestion(BaseModel):
    code: str = ""
    severity: ReviewSeverity = "INFO"
    title: str = ""
    description: str = ""
    suggestion: str = ""
    artifactType: str | None = None


class AssistantArtifactReviewArtifact(BaseModel):
    artifact_type: str = "ASSISTANT_ARTIFACT_REVIEW"
    summary: str = ""
    riskLevel: ReviewRiskLevel = "MEDIUM"
    conclusion: str = ""
    issues: list[ArtifactReviewIssue] = Field(default_factory=list)
    consistencyChecks: list[ArtifactConsistencyCheck] = Field(default_factory=list)
    suggestions: list[ArtifactReviewSuggestion] = Field(default_factory=list)
    nextActions: list[str] = Field(default_factory=list)
    markdown: str = ""


ASSISTANT_ARTIFACT_REVIEW_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": [
        "summary",
        "riskLevel",
        "conclusion",
        "issues",
        "consistencyChecks",
        "suggestions",
        "nextActions",
    ],
    "properties": {
        "summary": {"type": "string"},
        "riskLevel": {
            "type": "string",
            "enum": ["LOW", "MEDIUM", "HIGH", "BLOCKER"],
        },
        "conclusion": {"type": "string"},
        "issues": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "code",
                    "severity",
                    "title",
                    "description",
                    "suggestion",
                    "artifactType",
                ],
                "properties": {
                    "code": {"type": "string"},
                    "severity": {
                        "type": "string",
                        "enum": ["INFO", "WARNING", "BLOCKER"],
                    },
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "suggestion": {"type": "string"},
                    "artifactType": {"type": ["string", "null"]},
                },
            },
        },
        "consistencyChecks": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "dimension",
                    "level",
                    "result",
                    "evidence",
                    "recommendation",
                ],
                "properties": {
                    "dimension": {"type": "string"},
                    "level": {
                        "type": "string",
                        "enum": ["INFO", "WARNING", "BLOCKER"],
                    },
                    "result": {"type": "string"},
                    "evidence": {"type": "string"},
                    "recommendation": {"type": "string"},
                },
            },
        },
        "suggestions": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "code",
                    "severity",
                    "title",
                    "description",
                    "suggestion",
                    "artifactType",
                ],
                "properties": {
                    "code": {"type": "string"},
                    "severity": {
                        "type": "string",
                        "enum": ["INFO", "WARNING", "BLOCKER"],
                    },
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "suggestion": {"type": "string"},
                    "artifactType": {"type": ["string", "null"]},
                },
            },
        },
        "nextActions": {
            "type": "array",
            "items": {"type": "string"},
        },
    },
}
