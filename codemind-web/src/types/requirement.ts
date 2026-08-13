// A9.10.13.0.1 Requirement Module App Prototype types.
// PAGE_UI_CONTRACT remains a single-page prototype. MODULE_APP_CONTRACT aggregates
// module pages into a navigable app-shell prototype for module-level preview and later Dev Agent consumption.

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
}

export interface ModuleAppContract {
  schemaVersion?: string
  artifactType?: 'MODULE_APP_CONTRACT' | string
  moduleKey?: string | null
  moduleName?: string | null
  appShell?: {
    layout?: 'sidebar' | 'topbar' | string
    title?: string | null
    navigation?: ModuleAppNavigationItem[]
  }
  pages: ModuleAppPageSurface[]
  navigationEdges?: ModuleAppNavigationEdge[]
  source?: string | null
  hiddenFromUser?: boolean
}
