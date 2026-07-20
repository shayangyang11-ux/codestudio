from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

ProjectIndustry = Literal["GENERAL", "COMMERCE", "TECH", "INDUSTRIAL", "FINANCE", "GOV", "EDUCATION", "HEALTHCARE"]
ProjectThemeKey = Literal["enterprise_default", "tech_modern", "commerce_light", "industrial_pro", "finance_stable", "gov_clean"]
ProjectVisualTone = Literal["CLEAN", "TECH", "COMMERCE", "INDUSTRIAL", "FINANCE", "GOV"]
ProjectThemeDensity = Literal["COMPACT", "STANDARD", "COMFORTABLE"]
ProjectThemeRadius = Literal["SMALL", "MEDIUM", "LARGE"]
ProjectLoginLayout = Literal["BRAND_SPLIT", "CENTER_CARD", "MINIMAL"]
ProjectNavigationStyle = Literal["SIDE_NAV", "TOP_NAV", "MIXED"]
StyleAnalysisMode = Literal[
    "STYLE_INTENT_TEXT",
    "REFERENCE_TEXT",
    "REFERENCE_IMAGE",
    "MIXED_REFERENCE",
    "REFERENCE_IMAGE_PENDING",
    "MIXED_REFERENCE_PENDING",
    "JAVA_RULE_FALLBACK",
    "PYTHON_RULE_FALLBACK",
]


class StyleAssistantAgentRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="allow")

    instruction: str = ""
    reference_text: str | None = Field(default=None, alias="referenceText")
    reference_notes: str | None = Field(default=None, alias="referenceNotes")
    reference_mode: str | None = Field(default="AUTO", alias="referenceMode")
    target_page_type: str | None = Field(default=None, alias="targetPageType")
    allow_dark_mode: bool | None = Field(default=None, alias="allowDarkMode")
    attachment_ids: list[str] = Field(default_factory=list, alias="attachmentIds")
    attachment_names: list[str] = Field(default_factory=list, alias="attachmentNames")
    current_profile: dict[str, Any] = Field(default_factory=dict, alias="currentProfile")


class StyleAssistantAgentOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    industry: ProjectIndustry = "GENERAL"
    themeKey: ProjectThemeKey = "enterprise_default"
    visualTone: ProjectVisualTone = "CLEAN"
    primaryColor: str = "#2563eb"
    secondaryColor: str | None = None
    accentColor: str | None = None
    density: ProjectThemeDensity = "STANDARD"
    radius: ProjectThemeRadius = "MEDIUM"
    loginLayout: ProjectLoginLayout = "BRAND_SPLIT"
    navigationStyle: ProjectNavigationStyle = "SIDE_NAV"
    darkModeEnabled: bool = False
    analysisMode: StyleAnalysisMode = "STYLE_INTENT_TEXT"
    extractedSignals: dict[str, Any] = Field(default_factory=dict)
    summary: str = ""
    reasons: list[str] = Field(default_factory=list, max_length=10)
    warnings: list[str] = Field(default_factory=list, max_length=10)
    confidence: float = 0.72

    @field_validator("primaryColor", "secondaryColor", "accentColor", mode="before")
    @classmethod
    def normalize_color(cls, value: Any) -> Any:
        if value is None:
            return None
        text = str(value).strip()
        if not text:
            return None
        if not text.startswith("#"):
            text = "#" + text
        if len(text) == 4:
            text = "#" + "".join(ch * 2 for ch in text[1:])
        return text.lower()


class StyleAssistantAgentResponse(StyleAssistantAgentOutput):
    agentMode: str = "STYLE_ASSISTANT_LLM"
    attachmentIds: list[str] = Field(default_factory=list)
    attachmentNames: list[str] = Field(default_factory=list)
    updatePayload: dict[str, Any] = Field(default_factory=dict)


STYLE_ASSISTANT_LLM_JSON_SCHEMA: dict[str, Any] = StyleAssistantAgentOutput.model_json_schema()
