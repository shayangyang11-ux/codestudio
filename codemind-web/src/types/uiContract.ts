export interface UiContractRoute {
  path?: string
  pageId: string
  title?: string
}

export interface UiContractOption {
  label: string
  value: string | number | boolean
}

export interface UiContractField {
  field: string
  label: string
  type?: string
  required?: boolean
  placeholder?: string
  options?: UiContractOption[]
  validation?: Record<string, unknown>
  defaultValue?: unknown
  description?: string
}

export interface UiContractAction {
  actionId?: string
  type: string
  label?: string
  mockAction?: string
  apiBinding?: string
  successMessage?: string
  nextPage?: string
  targetModalId?: string
  confirmMessage?: string
}

export interface UiContractColumn {
  field: string
  label: string
  type?: string
  width?: string | number
}

export interface UiContractDataSource {
  mockBinding?: string
  realApiBinding?: string
}

export interface UiContractTab {
  key: string
  title: string
  components?: UiContractComponent[]
}

export interface UiContractComponent {
  componentId: string
  type: string
  title?: string
  label?: string
  field?: string
  fields?: UiContractField[]
  actions?: UiContractAction[]
  rowActions?: UiContractAction[]
  columns?: UiContractColumn[]
  dataSource?: UiContractDataSource
  components?: UiContractComponent[]
  tabs?: UiContractTab[]
  items?: Array<Record<string, unknown>>
  modalId?: string
  body?: string
  states?: Record<string, unknown>
  [key: string]: unknown
}

export interface UiContractPage {
  pageId: string
  pageName?: string
  title?: string
  layout?: string
  description?: string
  components?: UiContractComponent[]
  states?: Record<string, unknown>
}

export interface UiContractContent {
  schemaVersion?: string
  artifactType?: string
  module?: Record<string, unknown>
  routes?: UiContractRoute[]
  pages?: UiContractPage[]
  mockData?: Record<string, unknown>
  apiBindings?: Record<string, unknown>[]
  developmentRules?: Record<string, unknown>
  analysisSummary?: string
}