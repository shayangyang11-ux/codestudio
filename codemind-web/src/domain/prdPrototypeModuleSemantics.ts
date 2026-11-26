import type { ModuleTreeNode } from '../types/codemind'
import { inferSurface } from './pageSemantics'
type LooseRecord = Record<string, any>

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
  interaction_notes?: unknown
  interactionNotes?: unknown

  [key: string]: any
}

export function normalizePrdPrototypeModuleRequirement(
  requirement: unknown,
  context: {
    pageNode?: ModuleTreeNode | null
    uiContract?: unknown
    modulePrdArtifact?: unknown
    uiContractArtifact?: unknown
  } = {},
): PrdPagePrototypeModuleRequirement {
  const source = recordValue(requirement)
  const pageNode = context.pageNode || null
  const uiContract = recordValue(context.uiContract)
  const uiContractArtifact = recordValue(context.uiContractArtifact)
  const normalized: PrdPagePrototypeModuleRequirement = {
    ...source,
  }

  const pageName = textValue(
    source.page_name ||
      source.pageName ||
      source.name ||
      source.title ||
      pageNode?.name ||
      pageNode?.title,
  )

  const pageKey = textValue(
    source.page_key ||
      source.pageKey ||
      source.key ||
      source.route_path ||
      source.routePath ||
      pageNode?.key ||
      safeKey(pageName, 'page'),
  )

  const pageId = textValue(
    source.page_id ||
      source.pageId ||
      source.id ||
      pageNode?.id ||
      pageKey,
  )

  normalized.page_id = pageId
  normalized.pageId = pageId
  normalized.page_key = pageKey
  normalized.pageKey = pageKey
  normalized.page_name = pageName
  normalized.pageName = pageName
  normalized.route_path = textValue(source.route_path || source.routePath || source.route || pageNode?.routePath)
  normalized.routePath = normalized.route_path
  normalized.source_text = textValue(source.source_text || source.sourceText || source.description || source.summary || source.interaction_notes || source.interactionNotes)
  normalized.sourceText = normalized.source_text

  const existingSemanticItems = normalizeExistingSemanticItems(
    source.semantic_items || source.semanticItems,
  )

  const generatedSemanticItems = [
    ...semanticItemsFromList(pageKey, 'FILTER_FIELD', source.filter_fields || source.filterFields, 'filter_fields'),
    ...semanticItemsFromList(pageKey, 'FORM_FIELD', source.form_fields || source.formFields, 'form_fields'),
    ...semanticItemsFromList(pageKey, 'TABLE_COLUMN', source.table_columns || source.tableColumns, 'table_columns'),
    ...semanticItemsFromList(pageKey, 'ACTION', source.primary_actions || source.primaryActions || source.user_actions || source.userActions, 'primary_actions'),
    ...semanticItemsFromList(pageKey, 'ROW_ACTION', source.row_actions || source.rowActions, 'row_actions'),
    ...semanticItemsFromList(pageKey, 'NAVIGATION', source.navigation_targets || source.navigationTargets, 'navigation_targets'),
    ...semanticItemsFromNotes(pageKey, source.interaction_notes || source.interactionNotes),
  ]

  const semanticItems = mergeSemanticItems(existingSemanticItems, generatedSemanticItems)

  normalized.semantic_items = semanticItems
  normalized.semanticItems = semanticItems

  const uiIntent = normalizeUiIntent(source.ui_intent || source.uiIntent, semanticItems, normalized.source_text || '')
  normalized.ui_intent = uiIntent
  normalized.uiIntent = uiIntent

  const binding = normalizePrototypeBinding(
    source.prototype_binding || source.prototypeBinding,
    uiContract,
    uiContractArtifact,
  )
  normalized.prototype_binding = binding
  normalized.prototypeBinding = binding

  return normalized
}

export function normalizePrdPrototypeModuleRequirements(
  requirements: unknown,
  context: {
    pageNodes?: ModuleTreeNode[]
    uiContractByPageKey?: Record<string, unknown>
  } = {},
): PrdPagePrototypeModuleRequirement[] {
  const list = Array.isArray(requirements) ? requirements : []
  return list.map((item) => {
    const record = recordValue(item)
    const pageKey = textValue(record.page_key || record.pageKey || record.key)
    const pageNode = context.pageNodes?.find((node) => {
      const nodeKey = textValue((node as any).page_key || (node as any).pageKey || (node as any).key || node.id)
      return nodeKey && pageKey && normalizeIdentity(nodeKey) === normalizeIdentity(pageKey)
    }) || null

    return normalizePrdPrototypeModuleRequirement(item, {
      pageNode,
      uiContract: pageKey ? context.uiContractByPageKey?.[pageKey] : null,
    })
  })
}

export function semanticLabelsByKind(
  requirement: unknown,
  kinds: PrdPrototypeSemanticKind[],
): string[] {
  const req = normalizePrdPrototypeModuleRequirement(requirement)
  const kindSet = new Set(kinds)
  return (req.semanticItems || [])
    .filter((item) => kindSet.has(item.kind) && item.status !== 'REMOVED')
    .map((item) => textValue(item.label))
    .filter(Boolean)
}

function normalizeExistingSemanticItems(value: unknown): PrdPrototypeSemanticItem[] {
  if (!Array.isArray(value)) return []

  return value
    .map((item) => {
      const record = recordValue(item)
      const kind = normalizeSemanticKind(record.kind || record.semanticKind)
      const label = textValue(record.label || record.name || record.title)
      if (!label) return null

      return {
        semanticId: textValue(record.semanticId || record.semantic_id) || buildSemanticId('page', kind, label),
        kind,
        label,
        sourceText: textValue(record.sourceText || record.source_text) || null,
        sourceField: textValue(record.sourceField || record.source_field) || null,
        groupKey: textValue(record.groupKey || record.group_key) || null,
        confidence: typeof record.confidence === 'number' ? record.confidence : null,
        status: normalizeSemanticStatus(record.status),
        metadata: recordValue(record.metadata),
      } satisfies PrdPrototypeSemanticItem
    })
    .filter(Boolean) as PrdPrototypeSemanticItem[]
}

function semanticItemsFromList(
  pageKey: string,
  kind: PrdPrototypeSemanticKind,
  value: unknown,
  sourceField: string,
): PrdPrototypeSemanticItem[] {
  return listText(value).map((label) => ({
    semanticId: buildSemanticId(pageKey, kind, label),
    kind,
    label,
    sourceText: label,
    sourceField,
    confidence: 0.9,
    status: 'ACTIVE',
  }))
}

function semanticItemsFromNotes(pageKey: string, value: unknown): PrdPrototypeSemanticItem[] {
  const text = textValue(value)
  if (!text) return []

  const items: PrdPrototypeSemanticItem[] = []

  if (/必填|只读|不可编辑|校验|限制|规则|权限|审批后|审核后/.test(text)) {
    items.push({
      semanticId: buildSemanticId(pageKey, 'RULE', text.slice(0, 32)),
      kind: 'RULE',
      label: text.slice(0, 60),
      sourceText: text,
      sourceField: 'interaction_notes',
      confidence: 0.72,
      status: 'ACTIVE',
    })
  }

  if (/状态|草稿|待|已|完成|取消|关闭|异常|失败|成功/.test(text)) {
    items.push({
      semanticId: buildSemanticId(pageKey, 'STATE', text.slice(0, 32)),
      kind: 'STATE',
      label: text.slice(0, 60),
      sourceText: text,
      sourceField: 'interaction_notes',
      confidence: 0.68,
      status: 'ACTIVE',
    })
  }

  if (!items.length) {
    items.push({
      semanticId: buildSemanticId(pageKey, 'TEXT', text.slice(0, 32)),
      kind: 'TEXT',
      label: text.slice(0, 60),
      sourceText: text,
      sourceField: 'interaction_notes',
      confidence: 0.6,
      status: 'ACTIVE',
    })
  }

  return items
}

function mergeSemanticItems(
  existing: PrdPrototypeSemanticItem[],
  generated: PrdPrototypeSemanticItem[],
): PrdPrototypeSemanticItem[] {
  const map = new Map<string, PrdPrototypeSemanticItem>()

  for (const item of generated) {
    map.set(item.semanticId, item)
  }

  for (const item of existing) {
    map.set(item.semanticId, {
      ...map.get(item.semanticId),
      ...item,
    })
  }

  return Array.from(map.values())
}

function normalizeUiIntent(
  value: unknown,
  semanticItems: PrdPrototypeSemanticItem[],
  sourceText: string,
): PrdPrototypeUiIntent {
  const record = recordValue(value)
  const text = `${sourceText} ${semanticItems.map((item) => item.label).join(' ')}`

  const hasTable = semanticItems.some((item) => item.kind === 'TABLE_COLUMN')
  const hasFilter = semanticItems.some((item) => item.kind === 'FILTER_FIELD')
  const hasForm = semanticItems.some((item) => item.kind === 'FORM_FIELD')
  const hasWorkflow = /审批|审核|流程|节点|流转|状态变更/.test(text)
  const hasDashboard = /统计|指标|趋势|图表|看板|大屏|驾驶舱/.test(text)

  const surface = inferSurface(text)
  const layoutMode = normalizeLayoutMode(record.layoutMode || record.layout_mode) ||
    (surface === 'DASHBOARD' ? 'DASHBOARD'
      : surface === 'APPROVAL' ? 'WORKFLOW'
      : surface === 'SEARCH_LIST' || surface === 'LIST' ? 'LIST'
      : surface === 'FORM' || surface === 'UPLOAD' || surface === 'WIZARD' ? 'FORM'
      : surface === 'DETAIL' ? 'DETAIL'
      : 'CUSTOM')

  return {
    layoutMode,
    density: normalizeDensity(record.density) || (hasTable || hasDashboard ? 'HIGH' : 'MEDIUM'),
    interactionMode: normalizeInteractionMode(record.interactionMode || record.interaction_mode) ||
      (/弹窗|modal/i.test(text) ? 'MODAL' : /抽屉|drawer/i.test(text) ? 'DRAWER' : 'PAGE'),
    dataView: normalizeDataView(record.dataView || record.data_view) ||
      (hasDashboard ? 'CHART' : hasTable ? 'TABLE' : hasForm ? 'FORM' : 'MIXED'),
    notes: Array.isArray(record.notes) ? record.notes.map((item) => textValue(item)).filter(Boolean) : [],
  }
}

function normalizePrototypeBinding(
  value: unknown,
  uiContract: LooseRecord,
  uiContractArtifact: LooseRecord,
): PrdPrototypeBinding {
  const record = recordValue(value)
  const artifactId = textValue(
    record.uiContractArtifactId ||
      record.ui_contract_artifact_id ||
      uiContractArtifact.artifactId ||
      uiContractArtifact.id,
  )

  const versionNo = numberValue(
    record.uiContractVersionNo ||
      record.ui_contract_version_no ||
      uiContractArtifact.versionNo,
  )

  return {
    uiContractArtifactId: artifactId || null,
    uiContractVersionNo: versionNo,
    lastSyncedPrdArtifactId: textValue(record.lastSyncedPrdArtifactId || record.last_synced_prd_artifact_id) || null,
    lastSyncedPrdVersionNo: numberValue(record.lastSyncedPrdVersionNo || record.last_synced_prd_version_no),
    syncStatus: normalizeSyncStatus(record.syncStatus || record.sync_status || (Object.keys(uiContract).length ? 'SYNCED' : 'NOT_GENERATED')),
    lastSyncedAt: textValue(record.lastSyncedAt || record.last_synced_at) || null,
    manualPrototypeEditRevision: textValue(record.manualPrototypeEditRevision || record.manual_prototype_edit_revision) || null,
  }
}

function normalizeSemanticKind(value: unknown): PrdPrototypeSemanticKind {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeSemanticKind[] = [
    'FIELD',
    'FILTER_FIELD',
    'FORM_FIELD',
    'TABLE_COLUMN',
    'ACTION',
    'ROW_ACTION',
    'NAVIGATION',
    'STATE',
    'RULE',
    'DATA_NEED',
    'LAYOUT_INTENT',
    'TEXT',
    'UNKNOWN',
  ]
  return allowed.includes(text as PrdPrototypeSemanticKind) ? text as PrdPrototypeSemanticKind : 'UNKNOWN'
}

function normalizeSemanticStatus(value: unknown): 'ACTIVE' | 'REMOVED' | 'UNCERTAIN' {
  const text = textValue(value).toUpperCase()
  return text === 'REMOVED' || text === 'UNCERTAIN' ? text : 'ACTIVE'
}

function normalizeLayoutMode(value: unknown): PrdPrototypeLayoutMode | null {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeLayoutMode[] = ['LIST', 'FORM', 'DETAIL', 'DASHBOARD', 'WORKFLOW', 'CUSTOM', 'UNKNOWN']
  return allowed.includes(text as PrdPrototypeLayoutMode) ? text as PrdPrototypeLayoutMode : null
}

function normalizeDensity(value: unknown): PrdPrototypeDensity | null {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeDensity[] = ['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']
  return allowed.includes(text as PrdPrototypeDensity) ? text as PrdPrototypeDensity : null
}

function normalizeInteractionMode(value: unknown): PrdPrototypeInteractionMode | null {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeInteractionMode[] = ['PAGE', 'MODAL', 'DRAWER', 'INLINE', 'MIXED', 'UNKNOWN']
  return allowed.includes(text as PrdPrototypeInteractionMode) ? text as PrdPrototypeInteractionMode : null
}

function normalizeDataView(value: unknown): PrdPrototypeDataView | null {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeDataView[] = ['TABLE', 'CARD', 'CHART', 'TIMELINE', 'FORM', 'MIXED', 'UNKNOWN']
  return allowed.includes(text as PrdPrototypeDataView) ? text as PrdPrototypeDataView : null
}

function normalizeSyncStatus(value: unknown): PrdPrototypeSyncStatus {
  const text = textValue(value).toUpperCase()
  const allowed: PrdPrototypeSyncStatus[] = ['NOT_GENERATED', 'SYNCED', 'PRD_CHANGED', 'PROTOTYPE_CHANGED', 'CONFLICT', 'UNKNOWN']
  return allowed.includes(text as PrdPrototypeSyncStatus) ? text as PrdPrototypeSyncStatus : 'UNKNOWN'
}

function buildSemanticId(pageKey: string, kind: string, label: string): string {
  return `${safeKey(pageKey, 'page')}__${safeKey(kind, 'kind')}__${safeKey(label, 'item')}`
}

function listText(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (typeof item === 'string') return item.trim()
      const record = recordValue(item)
      return textValue(record.label || record.name || record.title || record.fieldKey || record.key || record.actionId)
    })
    .filter(Boolean)
}

function safeKey(value: unknown, fallback: string): string {
  const text = textValue(value)
  if (!text) return fallback

  const ascii = text
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '_')
    .replace(/^_+|_+$/g, '')

  return ascii || fallback
}

function normalizeIdentity(value: unknown): string {
  return textValue(value).toLowerCase().replace(/\s+/g, '').replace(/[_-]+/g, '')
}

function recordValue(value: unknown): LooseRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as LooseRecord : {}
}

function textValue(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

function numberValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}