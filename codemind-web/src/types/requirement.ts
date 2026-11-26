// A9.10.13.0.1 Requirement Module App Prototype types.
// PAGE_UI_CONTRACT remains a single-page prototype. MODULE_APP_CONTRACT aggregates
// module pages into a navigable app-shell prototype for module-level preview and later Dev Agent consumption.

export type ModuleAppShellLayout =
  | 'NONE'
  | 'SIDEBAR'
  | 'TOPBAR'
  | 'SIDEBAR_TOPBAR'
  | 'ACTION_HUB'

export type ModuleAppNavigationStyle =
  | 'ENTERPRISE_SIDEBAR'
  | 'ENTERPRISE_MIXED'
  | 'PORTAL_TOPBAR'
  | 'COMMERCE_TOPBAR'
  | 'WORKFLOW_HUB'

export type ModuleAppShellPolicy =
  | 'WITH_SHELL'
  | 'WITHOUT_SHELL'
  | 'ACTION_ENTRY'

export interface ModuleAppShellMenuItem {
  itemKey: string
  label: string
  pageKey?: string | null
  route?: string | null
  parentKey?: string | null
  level?: number
  order?: number | null
  visible?: boolean
  children?: ModuleAppShellMenuItem[]
}

export interface ModuleAppShellConfig {
  layout: ModuleAppShellLayout
  navigationStyle: ModuleAppNavigationStyle
  title?: string | null
  menuItems?: ModuleAppShellMenuItem[]
  navigation?: ModuleAppNavigationItem[]
  generatedFrom?: 'MODULE_TREE' | 'MODULE_APP_CONTRACT' | 'MIXED' | string | null
  stableSignature?: string | null
}

export type ModuleAppSurfaceType =
  | 'MAIN_NAV_PAGE'
  | 'SECONDARY_ROUTE'
  | 'OVERLAY_SURFACE'
  | 'PAGE_STATE'
  | 'UNKNOWN'

export type ModuleAppEntryMode =
  | 'NAVIGATION'
  | 'ACTION_ROUTE'
  | 'OVERLAY'
  | 'STATE'
  | 'UNKNOWN'

export type PrdPrototypeSemanticKind =
  | 'FIELD'
  | 'FILTER_FIELD'
  | 'FORM_FIELD'
  | 'TABLE_COLUMN'
  | 'ACTION'
  | 'ROW_ACTION'
  | 'NAVIGATION'
  | 'STATE'
  | 'RULE'
  | 'DATA_NEED'
  | 'LAYOUT_INTENT'
  | 'TEXT'
  | 'UNKNOWN'

export type PrdPrototypeLayoutMode =
  | 'LIST'
  | 'FORM'
  | 'DETAIL'
  | 'DASHBOARD'
  | 'WORKFLOW'
  | 'CUSTOM'
  | 'UNKNOWN'

export type PrdPrototypeDensity =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'UNKNOWN'

export type PrdPrototypeInteractionMode =
  | 'PAGE'
  | 'MODAL'
  | 'DRAWER'
  | 'INLINE'
  | 'MIXED'
  | 'UNKNOWN'

export type PrdPrototypeDataView =
  | 'TABLE'
  | 'CARD'
  | 'CHART'
  | 'TIMELINE'
  | 'FORM'
  | 'MIXED'
  | 'UNKNOWN'

export type PrdPrototypeSyncStatus =
  | 'NOT_GENERATED'
  | 'SYNCED'
  | 'PRD_CHANGED'
  | 'PROTOTYPE_CHANGED'
  | 'CONFLICT'
  | 'UNKNOWN'

export interface ModuleAppNavigationItem {
  label: string
  route?: string | null
  pageKey?: string | null
  visible?: boolean
  order?: number | null
}

export interface ModuleAppPageSurface {
  pageKey: string
  pageName: string
  routePath?: string | null
  route?: string | null
  pageKind?: string | null
  surfaceType?: ModuleAppSurfaceType | string | null
  entryMode?: ModuleAppEntryMode | string | null
  navigationVisible?: boolean
  parentPageKey?: string | null
  entryFromPageKey?: string | null
  entryActionId?: string | null
  primaryEntity?: string | null
}

export interface ModuleAppNavigationEdge {
  sourcePageKey?: string | null
  sourcePageName?: string | null
  actionId?: string | null
  label?: string | null
  targetPageKey?: string | null
  targetPageName?: string | null
  targetRoute?: string | null
  navigationType?: string | null
  navigationStatus?: string | null
  interactionKind?: string | null
  dataEffect?: string | null
}

export interface PrdPageNavigationEdge {
  action?: string | null
  actionLabel?: string | null
  label?: string | null
  interactionKind?: string | null
  interaction_kind?: string | null
  dataEffect?: string | null
  data_effect?: string | null
  runtimeEffect?: string | null
  runtime_effect?: string | null
  targetPageId?: string | null
  target_page_id?: string | null
  targetPageKey?: string | null
  target_page_key?: string | null
  targetPageName?: string | null
  target_page_name?: string | null
  targetRoute?: string | null
  target_route?: string | null
  source?: string | null
}

export interface ModuleAppContract {
  schemaVersion?: string
  artifactType?: 'MODULE_APP_CONTRACT' | string
  moduleKey?: string | null
  moduleName?: string | null
  appShell?: ModuleAppShellConfig
  pages: ModuleAppPageSurface[]
  navigationEdges?: ModuleAppNavigationEdge[]
  source?: string | null
  hiddenFromUser?: boolean
}

export interface PrdPrototypeSemanticItem {
  semanticId: string
  kind: PrdPrototypeSemanticKind
  label: string
  sourceText?: string | null
  sourceField?: string | null
  groupKey?: string | null
  confidence?: number | null
  status?: 'ACTIVE' | 'REMOVED' | 'UNCERTAIN'
  metadata?: Record<string, unknown>
}

export interface PrdPrototypeUiIntent {
  layoutMode: PrdPrototypeLayoutMode
  density: PrdPrototypeDensity
  interactionMode: PrdPrototypeInteractionMode
  dataView: PrdPrototypeDataView
  notes?: string[]
}
export interface PrdPrototypeBinding {
  uiContractArtifactId?: string | null
  uiContractVersionNo?: number | null
  lastSyncedPrdArtifactId?: string | null
  lastSyncedPrdVersionNo?: number | null
  syncStatus: PrdPrototypeSyncStatus
  lastSyncedAt?: string | null
  manualPrototypeEditRevision?: string | null
}

export interface PrdPagePrototypeModuleRequirement {
  page_id?: string | null
  pageId?: string | null
  page_key?: string | null
  pageKey?: string | null
  page_name?: string | null
  pageName?: string | null
  route_path?: string | null
  routePath?: string | null
  module_id?: string | null
  moduleId?: string | null
  module_name?: string | null
  moduleName?: string | null

  source_text?: string | null
  sourceText?: string | null

  ui_intent?: PrdPrototypeUiIntent | null
  uiIntent?: PrdPrototypeUiIntent | null
  semantic_items?: PrdPrototypeSemanticItem[]
  semanticItems?: PrdPrototypeSemanticItem[]
  prototype_binding?: PrdPrototypeBinding | null
  prototypeBinding?: PrdPrototypeBinding | null

  filter_fields?: unknown
  filterFields?: unknown
  form_fields?: unknown
  formFields?: unknown
  table_columns?: unknown
  tableColumns?: unknown
  primary_actions?: unknown
  primaryActions?: unknown
  user_actions?: unknown
  userActions?: unknown
  row_actions?: unknown
  rowActions?: unknown
  navigation_targets?: unknown
  navigationTargets?: unknown
  navigation_edges?: PrdPageNavigationEdge[] | unknown
  navigationEdges?: PrdPageNavigationEdge[] | unknown
  interaction_notes?: unknown
  interactionNotes?: unknown

  [key: string]: unknown
}
