export type UiContractComponentType =
  | 'PAGE_HEADER'
  | 'SECTION'
  | 'CARD'
  | 'FORM'
  | 'FIELD_GROUP'
  | 'TABLE'
  | 'TOOLBAR'
  | 'BUTTON'
  | 'LINK'
  | 'TABS'
  | 'MODAL'
  | 'ALERT'
  | 'DESCRIPTION_LIST'
  | 'STEPS'
  | 'DIVIDER'
  | 'TEXT'
  | 'EMPTY_STATE'

export type UiContractFieldType =
  | 'INPUT'
  | 'PASSWORD'
  | 'TEXTAREA'
  | 'SELECT'
  | 'CHECKBOX'
  | 'RADIO'
  | 'NUMBER'
  | 'EMAIL'
  | 'DATE'
  | 'DATETIME'
  | 'SWITCH'
  | 'HIDDEN'

export type UiContractActionType =
  | 'SET_VALUE'
  | 'RESET_FORM'
  | 'SUBMIT_FORM'
  | 'OPEN_MODAL'
  | 'CLOSE_MODAL'
  | 'SWITCH_TAB'
  | 'NAVIGATE'
  | 'SHOW_TOAST'
  | 'OPEN_CONFIRM'
  | 'CONFIRM_ACTION'
  | 'CANCEL_ACTION'
  | 'SET_PAGE_STATE'
  | 'QUERY_DATA'
  | 'DELETE_ROW'

export type UiContractValidationType =
  | 'REQUIRED'
  | 'MIN_LENGTH'
  | 'MAX_LENGTH'
  | 'PATTERN'
  | 'EMAIL'
  | 'MIN_VALUE'
  | 'MAX_VALUE'
  | 'MATCH_FIELD'

export type UiContractPageStateType =
  | 'DEFAULT'
  | 'LOADING'
  | 'EMPTY'
  | 'ERROR'
  | 'SUCCESS'
  | 'DISABLED'

export type UiContractCoverageStatus =
  | 'FULL'
  | 'PARTIAL'
  | 'MISSING'
  | 'NOT_APPLICABLE'

export interface UiContractIdentity {
  projectId: string
  pageId: string
  pageKey: string
  pageName: string
  pageKind: string
}

export interface UiContractLayoutRegion {
  regionId: string
  title?: string
  componentIds?: string[]
}

export interface UiContractLayout {
  type:
    | 'CENTERED_FORM'
    | 'FORM_DETAIL'
    | 'TABLE_LIST'
    | 'DETAIL_TABS'
    | 'APPROVAL_WORKBENCH'
    | 'DASHBOARD'
    | 'SETTINGS'
    | 'CUSTOM_GRID'
  columns?: number
  regions?: UiContractLayoutRegion[]
}

export interface UiContractOption {
  label: string
  value: string
  disabled?: boolean
}

export interface UiContractField {
  fieldKey: string
  label: string
  type: UiContractFieldType
  required?: boolean
  placeholder?: string
  defaultValue?: unknown
  options?: UiContractOption[]
  validationRuleIds?: string[]
  featureIds?: string[]
  helpText?: string
  disabled?: boolean
}

export interface UiContractColumn {
  columnKey: string
  fieldKey: string
  label: string
  type?: 'TEXT' | 'TAG' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'ACTIONS'
  width?: number | null
  sortable?: boolean
}

export interface UiContractTab {
  tabKey: string
  title: string
  componentIds?: string[]
}

export interface UiContractComponent {
  componentId: string
  type: UiContractComponentType
  title?: string
  description?: string
  featureIds?: string[]
  actionIds?: string[]
  dataBindingKey?: string | null
  modalId?: string | null
  fields?: UiContractField[]
  columns?: UiContractColumn[]
  tabs?: UiContractTab[]
  children?: UiContractComponent[]
  props?: Record<string, unknown>
}

export interface UiContractPage {
  pageId: string
  pageKey: string
  pageName: string
  route: string
  title: string
  description: string
  pageKind: string
  layout: UiContractLayout
  componentTree: UiContractComponent[]
}

export interface UiContractInteraction {
  actionId: string
  type: UiContractActionType
  label?: string
  sourceComponentId?: string | null
  targetId?: string | null
  targetPageId?: string | null
  targetTabKey?: string | null
  dataBindingKey?: string | null
  scenarioKey?: string | null
  payload?: Record<string, unknown>
  successActionIds?: string[]
  failureActionIds?: string[]
  featureIds?: string[]
  confirmationMessage?: string
}

export interface UiContractValidationRule {
  ruleId: string
  fieldKey: string
  type: UiContractValidationType
  message: string
  value?: unknown
  compareFieldKey?: string | null
}

export interface UiContractPageState {
  stateKey: string
  type: UiContractPageStateType
  title?: string
  message?: string
  visibleComponentIds?: string[]
  disabledComponentIds?: string[]
}

export interface UiContractMockScenario {
  scenarioKey: string
  actionId: string
  outcome: 'SUCCESS' | 'FAILURE'
  delayMs?: number
  message?: string
  nextPageState?: string | null
  response?: Record<string, unknown>
}

export interface UiContractApiBinding {
  bindingKey: string
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  path: string
  status?: 'MOCK' | 'PLANNED' | 'BOUND'
  requestSchema?: Record<string, unknown>
  responseSchema?: Record<string, unknown>
  usedByActionIds?: string[]
}

export interface UiContractNavigationTarget {
  targetPageId: string
  targetPageKey: string
  targetPageName: string
  route: string
  status?: 'RESOLVED' | 'PLANNED'
}

export interface UiContractFeatureCoverage {
  featureId: string
  featureKey: string
  featureName: string
  status: UiContractCoverageStatus
  componentIds?: string[]
  actionIds?: string[]
  notes?: string[]
}

export interface UiContractQualitySummary {
  score: number
  coverageRate: number
  generationMode: 'LLM_STRUCTURED'
  blockers?: string[]
  warnings?: string[]
}

export interface UiContractDevelopmentRules {
  mustFollowLayout?: boolean
  mustFollowFields?: boolean
  mustFollowValidationRules?: boolean
  mustFollowInteractionFlow?: boolean
  mustFollowApiBindings?: boolean
  allowStyleOptimization?: boolean
  breakingChangeRequiresReview?: boolean
  arbitraryScriptForbidden?: boolean
  mockMustUseScenarioEngine?: boolean
}

export interface UiContractContent {
  schemaVersion: '1.0'
  artifactType: 'UI_CONTRACT'
  identity: UiContractIdentity
  page: UiContractPage
  interactions: UiContractInteraction[]
  validationRules: UiContractValidationRule[]
  pageStates: UiContractPageState[]
  mockData: Record<string, unknown>
  mockScenarios: UiContractMockScenario[]
  apiBindings: UiContractApiBinding[]
  navigationTargets: UiContractNavigationTarget[]
  featureCoverage: UiContractFeatureCoverage[]
  assumptions: string[]
  qualitySummary: UiContractQualitySummary
  developmentRules: UiContractDevelopmentRules
  analysisSummary: string
}

export function isUiContractContent(
  value: unknown,
): value is UiContractContent {
  if (!isRecord(value)) {
    return false
  }

  if (
    value.schemaVersion !== '1.0' ||
    value.artifactType !== 'UI_CONTRACT'
  ) {
    return false
  }

  if (!isRecord(value.identity) || !isRecord(value.page)) {
    return false
  }

  const identity = value.identity
  const page = value.page

  return (
    isNonEmptyString(identity.projectId) &&
    isNonEmptyString(identity.pageId) &&
    isNonEmptyString(identity.pageKey) &&
    isNonEmptyString(identity.pageName) &&
    isNonEmptyString(page.pageId) &&
    isNonEmptyString(page.pageKey) &&
    isNonEmptyString(page.pageName) &&
    isNonEmptyString(page.route) &&
    isRecord(page.layout) &&
    Array.isArray(page.componentTree) &&
    Array.isArray(value.interactions) &&
    Array.isArray(value.validationRules) &&
    Array.isArray(value.pageStates) &&
    isRecord(value.mockData) &&
    Array.isArray(value.mockScenarios) &&
    Array.isArray(value.apiBindings) &&
    Array.isArray(value.navigationTargets) &&
    Array.isArray(value.featureCoverage) &&
    isRecord(value.qualitySummary)
  )
}

function isRecord(
  value: unknown,
): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNonEmptyString(
  value: unknown,
): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
