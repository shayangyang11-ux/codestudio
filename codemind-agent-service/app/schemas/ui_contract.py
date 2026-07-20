from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


ComponentType = Literal[
    "PAGE_HEADER", "SECTION", "CARD", "FORM", "FIELD_GROUP", "TABLE",
    "TOOLBAR", "BUTTON", "LINK", "TABS", "MODAL", "ALERT",
    "DESCRIPTION_LIST", "STEPS", "DIVIDER", "TEXT", "EMPTY_STATE",
]

FieldType = Literal[
    "INPUT", "PASSWORD", "TEXTAREA", "SELECT", "CHECKBOX", "RADIO",
    "NUMBER", "EMAIL", "DATE", "DATETIME", "SWITCH", "HIDDEN",
]

ActionType = Literal[
    "SET_VALUE", "RESET_FORM", "SUBMIT_FORM", "OPEN_MODAL",
    "CLOSE_MODAL", "SWITCH_TAB", "NAVIGATE", "SHOW_TOAST",
    "OPEN_CONFIRM", "CONFIRM_ACTION", "CANCEL_ACTION",
    "SET_PAGE_STATE", "QUERY_DATA", "DELETE_ROW",
]

ValidationType = Literal[
    "REQUIRED", "MIN_LENGTH", "MAX_LENGTH", "PATTERN", "EMAIL",
    "MIN_VALUE", "MAX_VALUE", "MATCH_FIELD",
]

PageStateType = Literal[
    "DEFAULT", "LOADING", "EMPTY", "ERROR", "SUCCESS", "DISABLED",
]

CoverageStatus = Literal["FULL", "PARTIAL", "MISSING", "NOT_APPLICABLE"]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class UiContractIdentity(StrictModel):
    projectId: str
    pageId: str
    pageKey: str
    pageName: str
    pageKind: str


class UiContractLayoutRegion(StrictModel):
    regionId: str
    title: str = ""
    componentIds: list[str] = Field(default_factory=list)


class UiContractLayout(StrictModel):
    type: Literal[
        "CENTERED_FORM", "FORM_DETAIL", "TABLE_LIST", "DETAIL_TABS",
        "APPROVAL_WORKBENCH", "DASHBOARD", "SETTINGS", "CUSTOM_GRID",
    ]
    columns: int = Field(default=1, ge=1, le=24)
    regions: list[UiContractLayoutRegion] = Field(default_factory=list)


class UiContractOption(StrictModel):
    label: str
    value: str
    disabled: bool = False


class UiContractField(StrictModel):
    fieldKey: str
    label: str
    type: FieldType
    required: bool = False
    placeholder: str = ""
    defaultValue: Any = None
    options: list[UiContractOption] = Field(default_factory=list)
    validationRuleIds: list[str] = Field(default_factory=list)
    featureIds: list[str] = Field(default_factory=list)
    helpText: str = ""
    disabled: bool = False


class UiContractColumn(StrictModel):
    columnKey: str
    fieldKey: str
    label: str
    type: Literal[
        "TEXT", "TAG", "NUMBER", "DATE", "DATETIME", "BOOLEAN", "ACTIONS"
    ] = "TEXT"
    width: int | None = Field(default=None, ge=40, le=1200)
    sortable: bool = False


class UiContractTab(StrictModel):
    tabKey: str
    title: str
    componentIds: list[str] = Field(default_factory=list)


class UiContractComponent(StrictModel):
    componentId: str
    type: ComponentType
    title: str = ""
    description: str = ""
    featureIds: list[str] = Field(default_factory=list)
    actionIds: list[str] = Field(default_factory=list)
    dataBindingKey: str | None = None
    modalId: str | None = None
    fields: list[UiContractField] = Field(default_factory=list)
    columns: list[UiContractColumn] = Field(default_factory=list)
    tabs: list[UiContractTab] = Field(default_factory=list)
    children: list["UiContractComponent"] = Field(default_factory=list)
    props: dict[str, Any] = Field(default_factory=dict)


class UiContractPage(StrictModel):
    pageId: str
    pageKey: str
    pageName: str
    route: str
    title: str
    description: str
    pageKind: str
    layout: UiContractLayout
    componentTree: list[UiContractComponent] = Field(min_length=1)


class UiContractInteraction(StrictModel):
    actionId: str
    type: ActionType
    label: str = ""
    sourceComponentId: str | None = None
    targetId: str | None = None
    targetPageId: str | None = None
    targetTabKey: str | None = None
    dataBindingKey: str | None = None
    scenarioKey: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)
    successActionIds: list[str] = Field(default_factory=list)
    failureActionIds: list[str] = Field(default_factory=list)
    featureIds: list[str] = Field(default_factory=list)
    confirmationMessage: str = ""


class UiContractValidationRule(StrictModel):
    ruleId: str
    fieldKey: str
    type: ValidationType
    message: str
    value: Any = None
    compareFieldKey: str | None = None


class UiContractPageState(StrictModel):
    stateKey: str
    type: PageStateType
    title: str = ""
    message: str = ""
    visibleComponentIds: list[str] = Field(default_factory=list)
    disabledComponentIds: list[str] = Field(default_factory=list)


class UiContractMockScenario(StrictModel):
    scenarioKey: str
    actionId: str
    outcome: Literal["SUCCESS", "FAILURE"]
    delayMs: int = Field(default=300, ge=0, le=10000)
    message: str = ""
    nextPageState: str | None = None
    response: dict[str, Any] = Field(default_factory=dict)


class UiContractApiBinding(StrictModel):
    bindingKey: str
    method: Literal["GET", "POST", "PUT", "PATCH", "DELETE"]
    path: str
    status: Literal["MOCK", "PLANNED", "BOUND"] = "MOCK"
    requestSchema: dict[str, Any] = Field(default_factory=dict)
    responseSchema: dict[str, Any] = Field(default_factory=dict)
    usedByActionIds: list[str] = Field(default_factory=list)


class UiContractNavigationTarget(StrictModel):
    targetPageId: str
    targetPageKey: str
    targetPageName: str
    route: str
    status: Literal["RESOLVED", "PLANNED"] = "RESOLVED"


class UiContractFeatureCoverage(StrictModel):
    featureId: str
    featureKey: str
    featureName: str
    status: CoverageStatus
    componentIds: list[str] = Field(default_factory=list)
    actionIds: list[str] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)


class UiContractQualitySummary(StrictModel):
    score: int = Field(ge=0, le=100)
    coverageRate: int = Field(ge=0, le=100)
    generationMode: Literal["LLM_STRUCTURED"]
    blockers: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class UiContractDevelopmentRules(StrictModel):
    mustFollowLayout: bool = True
    mustFollowFields: bool = True
    mustFollowValidationRules: bool = True
    mustFollowInteractionFlow: bool = True
    mustFollowApiBindings: bool = True
    allowStyleOptimization: bool = True
    breakingChangeRequiresReview: bool = True
    arbitraryScriptForbidden: bool = True
    mockMustUseScenarioEngine: bool = True


class UiContractArtifact(StrictModel):
    schemaVersion: Literal["1.0"] = "1.0"
    artifactType: Literal["UI_CONTRACT"] = "UI_CONTRACT"
    identity: UiContractIdentity
    page: UiContractPage
    interactions: list[UiContractInteraction] = Field(default_factory=list)
    validationRules: list[UiContractValidationRule] = Field(default_factory=list)
    pageStates: list[UiContractPageState] = Field(min_length=1)
    mockData: dict[str, Any] = Field(default_factory=dict)
    mockScenarios: list[UiContractMockScenario] = Field(default_factory=list)
    apiBindings: list[UiContractApiBinding] = Field(default_factory=list)
    navigationTargets: list[UiContractNavigationTarget] = Field(default_factory=list)
    featureCoverage: list[UiContractFeatureCoverage] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    qualitySummary: UiContractQualitySummary
    developmentRules: UiContractDevelopmentRules = Field(
        default_factory=UiContractDevelopmentRules
    )
    analysisSummary: str
    markdown: str = ""

    @model_validator(mode="after")
    def validate_identity_consistency(self) -> "UiContractArtifact":
        if self.identity.pageId != self.page.pageId:
            raise ValueError("identity.pageId must equal page.pageId")
        if self.identity.pageKey != self.page.pageKey:
            raise ValueError("identity.pageKey must equal page.pageKey")
        if self.identity.pageName != self.page.pageName:
            raise ValueError("identity.pageName must equal page.pageName")
        return self


UiContractComponent.model_rebuild()

UI_CONTRACT_LLM_JSON_SCHEMA: dict[str, Any] = UiContractArtifact.model_json_schema()
UI_CONTRACT_LLM_JSON_SCHEMA.get("properties", {}).pop("markdown", None)

required_fields = UI_CONTRACT_LLM_JSON_SCHEMA.get("required")
if isinstance(required_fields, list) and "markdown" in required_fields:
    required_fields.remove("markdown")
