import type { ModuleTreeNode } from '../types/codemind'

export function normalizeModuleNodes(raw: unknown): ModuleTreeNode[] {
  if (!Array.isArray(raw)) {
    return []
  }

  return raw.map((item) => normalizeModuleNode(item))
}

function normalizeModuleNode(raw: unknown): ModuleTreeNode {
  const item = isRecord(raw) ? raw : {}
  const extra = isRecord(item.extra) ? item.extra : undefined

  const childrenRaw = item.children
  const children = Array.isArray(childrenRaw)
    ? childrenRaw.map((child) => normalizeModuleNode(child))
    : []

  const moduleType =
    toStringValue(item.moduleType) ||
    toStringValue(item.type) ||
    toStringValue(extra?.moduleType) ||
    toStringValue(extra?.type)

  return {
    id: toStringValue(item.id),
    parentId: toNullableStringValue(item.parentId),
    moduleKey: toStringValue(item.moduleKey) || toStringValue(item.key),
    moduleName:
      toStringValue(item.moduleName) ||
      toStringValue(item.name) ||
      toStringValue(item.title),
    moduleType,
    modulePath: toStringValue(item.modulePath),
    levelNo: toNumberValue(item.levelNo),
    sortOrder: toNumberValue(item.sortOrder),
    status: toStringValue(item.status),
    extra,

    pageKind:
      toStringValue(item.pageKind) ||
      toStringValue(item.page_kind) ||
      toStringValue(extra?.pageKind) ||
      toStringValue(extra?.page_kind),
    routePath:
      toStringValue(item.routePath) ||
      toStringValue(item.route_path) ||
      toStringValue(extra?.routePath) ||
      toStringValue(extra?.route_path),
    layoutType:
      toStringValue(item.layoutType) ||
      toStringValue(item.layout_type) ||
      toStringValue(extra?.layoutType) ||
      toStringValue(extra?.layout_type),
    primaryEntity:
      toStringValue(item.primaryEntity) ||
      toStringValue(item.primary_entity) ||
      toStringValue(extra?.primaryEntity) ||
      toStringValue(extra?.primary_entity),
    canGenerateUiContract: toBooleanValue(
      item.canGenerateUiContract ??
        item.can_generate_ui_contract ??
        extra?.canGenerateUiContract ??
        extra?.can_generate_ui_contract,
    ),
    featureRole:
      toStringValue(item.featureRole) ||
      toStringValue(item.feature_role) ||
      toStringValue(extra?.featureRole) ||
      toStringValue(extra?.feature_role),

    children,
  }
}

export function getModuleDisplayName(
  node?: ModuleTreeNode | null,
): string {
  if (!node) {
    return ''
  }

  return node.moduleName || node.name || node.title || node.moduleKey || node.key || '未命名模块'
}

export function getModuleStableKey(
  node?: ModuleTreeNode | null,
): string {
  if (!node) {
    return ''
  }

  return node.moduleKey || node.key || ''
}

function toStringValue(value: unknown): string | undefined {
  if (value === null || value === undefined) {
    return undefined
  }

  return String(value)
}

function toNullableStringValue(value: unknown): string | null | undefined {
  if (value === null) {
    return null
  }

  if (value === undefined) {
    return undefined
  }

  return String(value)
}

function toNumberValue(value: unknown): number | undefined {
  if (typeof value === 'number') {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? undefined : parsed
  }

  return undefined
}

function toBooleanValue(value: unknown): boolean | undefined {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()

    if (normalized === 'true') return true
    if (normalized === 'false') return false
  }

  return undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}