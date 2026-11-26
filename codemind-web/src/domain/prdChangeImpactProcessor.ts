// A9.10.14.4.3.15.7.24L3B5B1_PRD_CHANGE_IMPACT_PROCESSOR
// 通用 PRD 变更影响编排器。
// 用户侧只看到“同步 PRD 变更到原型”；系统侧必须记录视觉影响、语义影响、下游资产和开发 Agent 能力提示。

import type { ModuleTreeNode } from '../types/codemind'

type LooseRecord = Record<string, any>

export type RequirementArtifactTypeLike =
  | 'MODULE_PRD'
  | 'BUSINESS_FLOW'
  | 'PAGE_MAP'
  | 'PAGE_SPEC'
  | 'DATA_MODEL'
  | 'API_CONTRACT'
  | 'TEST_SPEC'
  | 'UI_CONTRACT'
  | 'DEV_HANDOFF_PACKAGE'

export type PrdChangeOperation =
  | 'ADDED'
  | 'REMOVED'
  | 'UPDATED'

export type PrdChangeKind =
  | 'PAGE_FILTER_FIELD'
  | 'PAGE_FORM_FIELD'
  | 'PAGE_TABLE_COLUMN'
  | 'PAGE_PRIMARY_ACTION'
  | 'PAGE_ROW_ACTION'
  | 'PAGE_NAVIGATION'
  | 'PAGE_INTERACTION_NOTE'
  | 'PAGE_CAPABILITY_HINT'
  | 'PAGE_STRUCTURE'
  | 'MODULE_SEMANTIC_SECTION'

export type PrdSemanticImpactRecord = {
  impactId: string
  sourceSection: string
  sourcePageId?: string | null
  sourcePageName?: string
  sourcePageKey?: string
  changeKind: PrdChangeKind
  operation: PrdChangeOperation
  itemName: string
  reason: string
  targetArtifactTypes: RequirementArtifactTypeLike[]
  capabilityHints: string[]
  relatedPageNames: string[]
  hiddenFromUser: boolean
}

export type PrdPageRequirementChangeImpact = {
  changeId: string
  pageId: string | null
  pageName: string
  pageKey: string
  routePath: string
  operation: PrdChangeOperation
  previousRequirement: LooseRecord | null
  nextRequirement: LooseRecord
  changedFields: Array<{
    field: string
    kind: PrdChangeKind
    operation: PrdChangeOperation
    items: string[]
  }>
  visualImpact: boolean
  visualReasons: string[]
  semanticImpacts: PrdSemanticImpactRecord[]
  capabilityHints: string[]
  downstreamArtifactTypes: RequirementArtifactTypeLike[]
}

export type PrdChangeImpactReport = {
  reportId: string
  source: 'MODULE_PRD'
  mode: 'PRD_CHANGE_IMPACT'
  generatedAt: string
  moduleId: string | null
  moduleName: string
  sectionKey: string
  baselineStatus: 'NORMAL' | 'MISSING_PREVIOUS_BASELINE' | 'DOCUMENT_ONLY'
  visualImpactPageIds: string[]
  visualImpactPageNames: string[]
  pageRequirementChanges: PrdPageRequirementChangeImpact[]
  semanticImpacts: PrdSemanticImpactRecord[]
  capabilityHints: string[]
  downstreamArtifactTypes: RequirementArtifactTypeLike[]
  devAgentContext: {
    mustConsume: boolean
    source: string
    summary: string
    capabilityHints: string[]
    downstreamArtifactTypes: RequirementArtifactTypeLike[]
  }
}

export function analyzePrdChangeImpact(request: {
  previousContent: unknown
  nextContent: unknown
  ownerModule?: ModuleTreeNode | null
  pageNodes?: ModuleTreeNode[]
}): PrdChangeImpactReport {
  const previousContent = recordValue(request.previousContent)
  const nextContent = recordValue(request.nextContent)
  const owner = request.ownerModule || null
  const pages = request.pageNodes?.length
    ? request.pageNodes
    : owner
      ? collectOwnedPageNodes(owner)
      : []

  const report: PrdChangeImpactReport = {
    reportId: `prd_change_impact_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    source: 'MODULE_PRD',
    mode: 'PRD_CHANGE_IMPACT',
    generatedAt: new Date().toISOString(),
    moduleId: textValue((owner as any)?.id) || null,
    moduleName: displayNodeName(owner) || textValue(nextContent.module_name || nextContent.moduleName || nextContent.name || '当前模块'),
    sectionKey: editedSectionKey(nextContent),
    baselineStatus: 'NORMAL',
    visualImpactPageIds: [],
    visualImpactPageNames: [],
    pageRequirementChanges: [],
    semanticImpacts: [],
    capabilityHints: [],
    downstreamArtifactTypes: [],
    devAgentContext: {
      mustConsume: false,
      source: 'MODULE_PRD_CHANGE_IMPACT',
      summary: '',
      capabilityHints: [],
      downstreamArtifactTypes: [],
    },
  }

  if (isDocumentOnlyPrdEdit(nextContent)) {
    report.baselineStatus = 'DOCUMENT_ONLY'
    report.devAgentContext.summary = '本次 PRD 修改被标记为文档补充说明，不产生页面视觉同步。'
    return finalizeReport(report)
  }

  const previousRequirements = normalizePageRequirements(previousContent.page_requirements || previousContent.pageRequirements)
  const nextRequirements = normalizePageRequirements(nextContent.page_requirements || nextContent.pageRequirements)

  // 旧 PRD 基线缺失且页面较多时，不做全量视觉误标记；但仍保留报告，方便后续排查。
  if (!previousRequirements.length && nextRequirements.length > 1) {
    report.baselineStatus = 'MISSING_PREVIOUS_BASELINE'
    report.devAgentContext.summary = '旧 PRD 页面设计基线缺失，已避免把所有页面误判为本次视觉变更。'
    return finalizeReport(report)
  }

  const previousByIdentity = new Map<string, LooseRecord>()
  for (const requirement of previousRequirements) {
    for (const key of pageRequirementIdentityKeys(requirement)) {
      previousByIdentity.set(key, requirement)
    }
  }

  for (const nextRequirement of nextRequirements) {
    const oldRequirement = pageRequirementIdentityKeys(nextRequirement)
      .map((key) => previousByIdentity.get(key))
      .find(Boolean) || null

    if (oldRequirement && stableJson(oldRequirement) === stableJson(nextRequirement)) {
      continue
    }

    const matchedPage = findMatchingPageNode(nextRequirement, pages)
    const change = buildPageRequirementChangeImpact({
      oldRequirement,
      nextRequirement,
      matchedPage,
      pages,
    })

    if (!change.changedFields.length && oldRequirement) {
      continue
    }

    report.pageRequirementChanges.push(change)

    if (change.visualImpact && change.pageId) {
      report.visualImpactPageIds.push(change.pageId)
      report.visualImpactPageNames.push(change.pageName)
    }

    report.semanticImpacts.push(...change.semanticImpacts)
    report.capabilityHints.push(...change.capabilityHints)
    report.downstreamArtifactTypes.push(...change.downstreamArtifactTypes)
  }

  addStructuralSectionImpacts({
    previousContent,
    nextContent,
    pages,
    report,
  })

  return finalizeReport(report)
}

export function resolvePrdChangeImpactReport(value: unknown): PrdChangeImpactReport | null {
  const report = recordValue(value)
  if (report.mode !== 'PRD_CHANGE_IMPACT') return null

  return {
    reportId: textValue(report.reportId) || `prd_change_impact_${Date.now()}`,
    source: 'MODULE_PRD',
    mode: 'PRD_CHANGE_IMPACT',
    generatedAt: textValue(report.generatedAt) || new Date().toISOString(),
    moduleId: textValue(report.moduleId) || null,
    moduleName: textValue(report.moduleName),
    sectionKey: textValue(report.sectionKey),
    baselineStatus: ['NORMAL', 'MISSING_PREVIOUS_BASELINE', 'DOCUMENT_ONLY'].includes(textValue(report.baselineStatus))
      ? report.baselineStatus
      : 'NORMAL',
    visualImpactPageIds: listText(report.visualImpactPageIds),
    visualImpactPageNames: listText(report.visualImpactPageNames),
    pageRequirementChanges: Array.isArray(report.pageRequirementChanges)
      ? report.pageRequirementChanges.map((item) => recordValue(item) as PrdPageRequirementChangeImpact)
      : [],
    semanticImpacts: Array.isArray(report.semanticImpacts)
      ? report.semanticImpacts.map((item) => recordValue(item) as PrdSemanticImpactRecord)
      : [],
    capabilityHints: listText(report.capabilityHints),
    downstreamArtifactTypes: normalizeArtifactTypes(report.downstreamArtifactTypes),
    devAgentContext: recordValue(report.devAgentContext) as PrdChangeImpactReport['devAgentContext'],
  }
}

export function visualImpactPageIdsFromReport(report: PrdChangeImpactReport | null | undefined): string[] {
  if (!report) return []
  return unique(report.visualImpactPageIds.map(textValue).filter(Boolean))
}

export function pageChangeImpactForPage(
  report: PrdChangeImpactReport | null | undefined,
  pageId: string | null | undefined,
): PrdPageRequirementChangeImpact | null {
  const id = textValue(pageId)
  if (!report || !id) return null
  return report.pageRequirementChanges.find((item) => textValue(item.pageId) === id) || null
}

function buildPageRequirementChangeImpact(args: {
  oldRequirement: LooseRecord | null
  nextRequirement: LooseRecord
  matchedPage: ModuleTreeNode | null
  pages: ModuleTreeNode[]
}): PrdPageRequirementChangeImpact {
  const nextRequirement = args.nextRequirement
  const oldRequirement = args.oldRequirement
  const pageName = pageRequirementName(nextRequirement) || displayNodeName(args.matchedPage) || '未命名页面'
  const pageKey = pageRequirementKey(nextRequirement)
  const pageId = textValue((args.matchedPage as any)?.id) || null
  const routePath = textValue(nextRequirement.route_path || nextRequirement.routePath || (args.matchedPage as any)?.routePath || '')

  const changedFields: PrdPageRequirementChangeImpact['changedFields'] = []

  addListDiff(changedFields, oldRequirement, nextRequirement, 'filter_fields', 'PAGE_FILTER_FIELD')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'form_fields', 'PAGE_FORM_FIELD')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'table_columns', 'PAGE_TABLE_COLUMN')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'primary_actions', 'PAGE_PRIMARY_ACTION')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'row_actions', 'PAGE_ROW_ACTION')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'navigation_targets', 'PAGE_NAVIGATION')
  addListDiff(changedFields, oldRequirement, nextRequirement, 'capability_hints', 'PAGE_CAPABILITY_HINT')

  const oldNotes = textValue(oldRequirement?.interaction_notes || oldRequirement?.interactionNotes)
  const nextNotes = textValue(nextRequirement.interaction_notes || nextRequirement.interactionNotes)
  if (oldNotes !== nextNotes && nextNotes) {
    changedFields.push({
      field: 'interaction_notes',
      kind: 'PAGE_INTERACTION_NOTE',
      operation: oldNotes ? 'UPDATED' : 'ADDED',
      items: [nextNotes],
    })
  }

  if (!oldRequirement) {
    changedFields.push({
      field: 'page_structure',
      kind: 'PAGE_STRUCTURE',
      operation: 'ADDED',
      items: [pageName],
    })
  }

  const semanticImpacts: PrdSemanticImpactRecord[] = []
  const capabilityHints: string[] = []
  const downstreamArtifactTypes: RequirementArtifactTypeLike[] = []

  for (const field of changedFields) {
    const artifactTypes = artifactsForChangeKind(field.kind, field.items)
    const caps = capabilityHintsForItems(field.items, field.kind)

    downstreamArtifactTypes.push(...artifactTypes)
    capabilityHints.push(...caps)

    for (const itemName of field.items) {
      semanticImpacts.push({
        impactId: `impact_${safeKey(pageName)}_${field.kind}_${safeKey(itemName)}_${Math.random().toString(36).slice(2, 7)}`,
        sourceSection: 'page_requirements',
        sourcePageId: pageId,
        sourcePageName: pageName,
        sourcePageKey: pageKey,
        changeKind: field.kind,
        operation: field.operation,
        itemName,
        reason: semanticReasonForChange(field.kind, itemName),
        targetArtifactTypes: artifactTypes,
        capabilityHints: caps,
        relatedPageNames: relatedPagesFromText(itemName, args.pages),
        hiddenFromUser: true,
      })
    }
  }

  const visualKinds: PrdChangeKind[] = [
    'PAGE_FILTER_FIELD',
    'PAGE_FORM_FIELD',
    'PAGE_TABLE_COLUMN',
    'PAGE_PRIMARY_ACTION',
    'PAGE_ROW_ACTION',
    'PAGE_NAVIGATION',
    'PAGE_STRUCTURE',
  ]

  const visualReasons = changedFields
    .filter((item) => visualKinds.includes(item.kind))
    .map((item) => `${labelForChangeKind(item.kind)}：${item.items.join('、')}`)

  return {
    changeId: `page_req_change_${pageId || safeKey(pageName)}_${Date.now()}`,
    pageId,
    pageName,
    pageKey,
    routePath,
    operation: oldRequirement ? 'UPDATED' : 'ADDED',
    previousRequirement: oldRequirement ? deepClone(oldRequirement) : null,
    nextRequirement: deepClone(nextRequirement),
    changedFields,
    visualImpact: Boolean(pageId && visualReasons.length),
    visualReasons,
    semanticImpacts,
    capabilityHints: unique(capabilityHints),
    downstreamArtifactTypes: uniqueArtifactTypes(downstreamArtifactTypes),
  }
}

function addListDiff(
  changedFields: PrdPageRequirementChangeImpact['changedFields'],
  previousRequirement: LooseRecord | null,
  nextRequirement: LooseRecord,
  key: string,
  kind: PrdChangeKind,
) {
  const previousItems = listText(previousRequirement?.[key] || previousRequirement?.[camelCase(key)])
  const nextItems = listText(nextRequirement[key] || nextRequirement[camelCase(key)])

  if (!nextItems.length && !previousItems.length) return

  if (!previousRequirement) {
    if (nextItems.length) {
      changedFields.push({ field: key, kind, operation: 'ADDED', items: nextItems })
    }
    return
  }

  const previousKeys = new Set(previousItems.map(normalizeIdentity))
  const nextKeys = new Set(nextItems.map(normalizeIdentity))

  const added = nextItems.filter((item) => !previousKeys.has(normalizeIdentity(item)))
  const removed = previousItems.filter((item) => !nextKeys.has(normalizeIdentity(item)))

  if (added.length) {
    changedFields.push({ field: key, kind, operation: 'ADDED', items: added })
  }

  if (removed.length) {
    changedFields.push({ field: key, kind, operation: 'REMOVED', items: removed })
  }
}

function addStructuralSectionImpacts(args: {
  previousContent: LooseRecord
  nextContent: LooseRecord
  pages: ModuleTreeNode[]
  report: PrdChangeImpactReport
}) {
  const sections = [
    'functional_requirements',
    'business_rules',
    'state_rules',
    'data_requirements',
    'api_requirements',
    'permission_requirements',
    'exception_scenarios',
    'acceptance_criteria',
  ]

  for (const section of sections) {
    if (stableJson(args.previousContent[section]) === stableJson(args.nextContent[section])) continue

    const text = stableJson(args.nextContent[section])
    const caps = capabilityHintsForItems([text], 'MODULE_SEMANTIC_SECTION')
    const artifactTypes = artifactsForSemanticSection(section, text)
    const relatedPages = relatedPagesFromText(text, args.pages)

    args.report.semanticImpacts.push({
      impactId: `impact_${section}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      sourceSection: section,
      changeKind: 'MODULE_SEMANTIC_SECTION',
      operation: 'UPDATED',
      itemName: section,
      reason: `${section} 发生变化，可能影响页面规范、数据、接口、测试和开发交接。`,
      targetArtifactTypes: artifactTypes,
      capabilityHints: caps,
      relatedPageNames: relatedPages,
      hiddenFromUser: true,
    })

    args.report.capabilityHints.push(...caps)
    args.report.downstreamArtifactTypes.push(...artifactTypes)
  }
}

function artifactsForChangeKind(kind: PrdChangeKind, items: string[]): RequirementArtifactTypeLike[] {
  const caps = capabilityHintsForItems(items, kind)
  const result: RequirementArtifactTypeLike[] = ['PAGE_SPEC', 'DEV_HANDOFF_PACKAGE']

  if (kind === 'PAGE_NAVIGATION') {
    result.push('PAGE_MAP', 'TEST_SPEC')
  }

  if (kind === 'PAGE_FILTER_FIELD' || kind === 'PAGE_FORM_FIELD' || kind === 'PAGE_TABLE_COLUMN') {
    result.push('DATA_MODEL', 'API_CONTRACT', 'TEST_SPEC')
  }

  if (kind === 'PAGE_PRIMARY_ACTION' || kind === 'PAGE_ROW_ACTION') {
    result.push('API_CONTRACT', 'TEST_SPEC')
  }

  if (caps.includes('WORKFLOW_APPROVAL') || caps.includes('STATUS_TRANSITION')) {
    result.push('BUSINESS_FLOW', 'API_CONTRACT', 'TEST_SPEC')
  }

  if (caps.includes('EXPORT_EXCEL') || caps.includes('IMPORT_EXCEL') || caps.includes('FILE_UPLOAD') || caps.includes('DOWNLOAD_FILE')) {
    result.push('API_CONTRACT', 'TEST_SPEC')
  }

  if (caps.includes('AUDIT_LOG') || caps.includes('PERMISSION_CHECK')) {
    result.push('API_CONTRACT', 'TEST_SPEC')
  }

  return uniqueArtifactTypes(result)
}

function artifactsForSemanticSection(section: string, text: string): RequirementArtifactTypeLike[] {
  const result: RequirementArtifactTypeLike[] = ['DEV_HANDOFF_PACKAGE']

  if (['functional_requirements', 'business_rules', 'state_rules'].includes(section)) {
    result.push('BUSINESS_FLOW', 'PAGE_SPEC', 'API_CONTRACT', 'TEST_SPEC')
  }

  if (section === 'data_requirements') {
    result.push('DATA_MODEL', 'API_CONTRACT', 'TEST_SPEC')
  }

  if (section === 'api_requirements' || section === 'permission_requirements') {
    result.push('API_CONTRACT', 'TEST_SPEC')
  }

  if (section === 'exception_scenarios' || section === 'acceptance_criteria') {
    result.push('TEST_SPEC')
  }

  if (/页面|跳转|路由|入口|出口/.test(text)) {
    result.push('PAGE_MAP', 'PAGE_SPEC')
  }

  return uniqueArtifactTypes(result)
}

function capabilityHintsForItems(items: string[], kind: PrdChangeKind): string[] {
  const text = items.join('、').toLowerCase()
  const hints: string[] = []

  if (/导出|export|excel|csv/.test(text)) hints.push('EXPORT_EXCEL', 'QUERY_CONDITION_REUSE', 'DOWNLOAD_FILE')
  if (/导入|import|批量上传/.test(text)) hints.push('IMPORT_EXCEL', 'FILE_UPLOAD')
  if (/上传|附件|文件/.test(text)) hints.push('FILE_UPLOAD')
  if (/下载|download/.test(text)) hints.push('DOWNLOAD_FILE')
  if (/审批|审核|复核|approval|approve/.test(text)) hints.push('WORKFLOW_APPROVAL', 'PERMISSION_CHECK', 'AUDIT_LOG')
  if (/取消|关闭|作废|删除|恢复|提交|状态|启用|禁用/.test(text)) hints.push('STATUS_TRANSITION', 'AUDIT_LOG')
  if (/权限|角色|可见|授权|permission|role/.test(text)) hints.push('PERMISSION_CHECK')
  if (/日志|记录|留痕|审计|audit/.test(text)) hints.push('AUDIT_LOG')
  if (/批量|batch/.test(text)) hints.push('BATCH_OPERATION')
  if (/通知|消息|提醒/.test(text)) hints.push('NOTIFICATION')
  if (/支付|退款|收款|金额/.test(text)) hints.push('TRANSACTION_FLOW')
  if (/筛选|查询|搜索/.test(text) || kind === 'PAGE_FILTER_FIELD') hints.push('QUERY_CONDITION_REUSE')

  return unique(hints)
}

function semanticReasonForChange(kind: PrdChangeKind, itemName: string): string {
  const caps = capabilityHintsForItems([itemName], kind)

  if (kind === 'PAGE_PRIMARY_ACTION' || kind === 'PAGE_ROW_ACTION') {
    return caps.length
      ? `新增或调整操作“${itemName}”不仅影响按钮显示，还可能影响接口、权限、日志、状态流转、测试和开发交接。`
      : `新增或调整操作“${itemName}”可能需要补充页面行为、接口契约和测试用例。`
  }

  if (kind === 'PAGE_NAVIGATION') {
    return `页面跳转关系“${itemName}”可能影响 PAGE_MAP、源页面行为、目标页面入参和测试路径。`
  }

  if (kind === 'PAGE_TABLE_COLUMN' || kind === 'PAGE_FORM_FIELD' || kind === 'PAGE_FILTER_FIELD') {
    return `字段“${itemName}”可能影响页面规范、数据模型、接口入参/出参和测试数据。`
  }

  return `PRD 页面设计项“${itemName}”发生变化，需要传递给后续资产和 Development Agent。`
}

function labelForChangeKind(kind: PrdChangeKind): string {
  const labels: Record<PrdChangeKind, string> = {
    PAGE_FILTER_FIELD: '筛选字段',
    PAGE_FORM_FIELD: '表单字段',
    PAGE_TABLE_COLUMN: '表格列',
    PAGE_PRIMARY_ACTION: '主要操作',
    PAGE_ROW_ACTION: '行操作',
    PAGE_NAVIGATION: '跳转关系',
    PAGE_INTERACTION_NOTE: '交互说明',
    PAGE_CAPABILITY_HINT: '能力提示',
    PAGE_STRUCTURE: '页面结构',
    MODULE_SEMANTIC_SECTION: '模块语义章节',
  }
  return labels[kind] || kind
}

function finalizeReport(report: PrdChangeImpactReport): PrdChangeImpactReport {
  report.visualImpactPageIds = unique(report.visualImpactPageIds)
  report.visualImpactPageNames = unique(report.visualImpactPageNames)
  report.capabilityHints = unique(report.capabilityHints)
  report.downstreamArtifactTypes = uniqueArtifactTypes(report.downstreamArtifactTypes)
  report.semanticImpacts = dedupeSemanticImpacts(report.semanticImpacts)

  report.devAgentContext = {
    mustConsume: report.semanticImpacts.length > 0 || report.downstreamArtifactTypes.includes('DEV_HANDOFF_PACKAGE'),
    source: 'MODULE_PRD_CHANGE_IMPACT',
    summary: summarizeReport(report),
    capabilityHints: report.capabilityHints,
    downstreamArtifactTypes: report.downstreamArtifactTypes,
  }

  return report
}

function summarizeReport(report: PrdChangeImpactReport): string {
  if (report.baselineStatus === 'DOCUMENT_ONLY') {
    return '本次为文档补充说明，不产生结构化同步影响。'
  }

  if (report.baselineStatus === 'MISSING_PREVIOUS_BASELINE') {
    return '旧 PRD 基线缺失，本次未自动标记视觉同步，避免误影响所有页面。'
  }

  const visual = report.visualImpactPageNames.length
    ? `视觉影响页面：${report.visualImpactPageNames.join('、')}`
    : '没有可靠视觉影响页面'

  const semantic = report.downstreamArtifactTypes.length
    ? `语义影响资产：${report.downstreamArtifactTypes.join('、')}`
    : '没有识别到下游语义资产影响'

  return `${visual}；${semantic}。`
}

function dedupeSemanticImpacts(items: PrdSemanticImpactRecord[]): PrdSemanticImpactRecord[] {
  const seen = new Set<string>()
  const result: PrdSemanticImpactRecord[] = []

  for (const item of items) {
    const key = [
      item.sourceSection,
      item.sourcePageId,
      item.changeKind,
      item.operation,
      normalizeIdentity(item.itemName),
      item.targetArtifactTypes.join('|'),
    ].join('::')

    if (seen.has(key)) continue
    seen.add(key)
    result.push(item)
  }

  return result
}

function normalizePageRequirements(value: unknown): LooseRecord[] {
  return Array.isArray(value)
    ? value.map(recordValue).filter((item) => Object.keys(item).length && !boolValue(item.deleted) && !boolValue(item._deleted) && !boolValue(item.archived))
    : []
}

function collectOwnedPageNodes(root: ModuleTreeNode): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []

  const visit = (node: ModuleTreeNode) => {
    for (const child of node.children || []) {
      const type = textValue((child as any).moduleType || (child as any).type || recordValue((child as any).extra).nodeType || recordValue((child as any).extra).node_type).toUpperCase()
      if (type === 'PAGE') {
        result.push(child)
      }
      visit(child)
    }
  }

  visit(root)
  return result
}

function findMatchingPageNode(requirement: LooseRecord, pages: ModuleTreeNode[]): ModuleTreeNode | null {
  const requirementKeys = pageRequirementIdentityKeys(requirement)
  let best: { page: ModuleTreeNode; score: number } | null = null

  for (const page of pages) {
    const pageKeys = pageNodeIdentityKeys(page)
    let score = 0

    for (const key of requirementKeys) {
      if (pageKeys.includes(key)) {
        score += key.startsWith('id:') ? 100 : key.startsWith('route:') ? 60 : 40
      }
    }

    if (!best || score > best.score) {
      best = { page, score }
    }
  }

  return best && best.score > 0 ? best.page : null
}

function pageRequirementIdentityKeys(requirement: LooseRecord): string[] {
  return [
    ['id', requirement.node_id || requirement.nodeId || requirement.page_node_id || requirement.pageNodeId],
    ['key', requirement.page_key || requirement.pageKey || requirement.key],
    ['name', requirement.page_name || requirement.pageName || requirement.name || requirement.title],
    ['route', requirement.route_path || requirement.routePath || requirement.route],
  ].map(([prefix, value]) => {
    const normalized = normalizeIdentity(value)
    return normalized ? `${prefix}:${normalized}` : ''
  }).filter(Boolean)
}

function pageNodeIdentityKeys(page: ModuleTreeNode): string[] {
  const extra = recordValue((page as any).extra)
  return [
    ['id', (page as any).id],
    ['key', (page as any).moduleKey || (page as any).key || extra.pageKey || extra.page_key],
    ['name', displayNodeName(page)],
    ['route', (page as any).routePath || extra.routePath || extra.route_path || extra.route],
  ].map(([prefix, value]) => {
    const normalized = normalizeIdentity(value)
    return normalized ? `${prefix}:${normalized}` : ''
  }).filter(Boolean)
}

function pageRequirementName(requirement: LooseRecord): string {
  return textValue(requirement.page_name || requirement.pageName || requirement.name || requirement.title)
}

function pageRequirementKey(requirement: LooseRecord): string {
  return textValue(requirement.page_key || requirement.pageKey || requirement.key)
}

function relatedPagesFromText(value: string, pages: ModuleTreeNode[]): string[] {
  const text = normalizeIdentity(value)
  if (!text) return []

  return pages
    .filter((page) => pageNodeIdentityKeys(page).some((key) => {
      const valueOnly = key.split(':').slice(1).join(':')
      return valueOnly.length >= 2 && text.includes(valueOnly)
    }))
    .map(displayNodeName)
    .filter(Boolean)
}

function editedSectionKey(content: LooseRecord): string {
  const sectionEdit = recordValue(content._prd_section_edit)
  const manualEdit = recordValue(content._manual_document_edit)
  return textValue(sectionEdit.sectionKey || sectionEdit.section_key || manualEdit.sectionKey || manualEdit.section_key)
}

function isDocumentOnlyPrdEdit(content: LooseRecord): boolean {
  const sectionEdit = recordValue(content._prd_section_edit)
  const sectionKey = textValue(sectionEdit.sectionKey || sectionEdit.section_key)
  const editorMode = textValue(sectionEdit.editorMode || sectionEdit.editor_mode).toUpperCase()
  const impactPolicy = textValue(sectionEdit.impactPolicy || sectionEdit.impact_policy).toUpperCase()

  return impactPolicy === 'DOCUMENT_ONLY' ||
    sectionKey === 'custom_sections' ||
    sectionKey.startsWith('custom_section_') ||
    editorMode.startsWith('CUSTOM_SECTION')
}

function normalizeArtifactTypes(value: unknown): RequirementArtifactTypeLike[] {
  return uniqueArtifactTypes(Array.isArray(value) ? value.map(textValue) as RequirementArtifactTypeLike[] : [])
}

function uniqueArtifactTypes(items: RequirementArtifactTypeLike[]): RequirementArtifactTypeLike[] {
  const allowed = new Set([
    'MODULE_PRD',
    'BUSINESS_FLOW',
    'PAGE_MAP',
    'PAGE_SPEC',
    'DATA_MODEL',
    'API_CONTRACT',
    'TEST_SPEC',
    'UI_CONTRACT',
    'DEV_HANDOFF_PACKAGE',
  ])

  return unique(items.map(textValue).filter((item) => allowed.has(item)) as RequirementArtifactTypeLike[])
}

function listText(value: unknown): string[] {
  if (Array.isArray(value)) {
    return unique(value.flatMap((item) => {
      if (typeof item === 'string') return splitTextList(item)
      const record = recordValue(item)
      return [textValue(record.label || record.name || record.title || record.field_name || record.fieldName || record.column_name || record.columnName || record.action_name || record.actionName || record.description)]
    }).filter(Boolean))
  }

  return splitTextList(value)
}

function splitTextList(value: unknown): string[] {
  return unique(textValue(value)
    .split(/[\n,，、;；]/)
    .map((item) => item.trim())
    .filter(Boolean))
}

function recordValue(value: unknown): LooseRecord {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as LooseRecord
    : {}
}

function textValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(textValue).filter(Boolean).join('、')
  if (typeof value === 'object') {
    const record = value as LooseRecord
    return textValue(record.label || record.name || record.title || record.description || record.key || '')
  }
  return String(value || '').trim()
}

function displayNodeName(node: ModuleTreeNode | null | undefined): string {
  if (!node) return ''
  return textValue((node as any).moduleName || (node as any).name || (node as any).title || (node as any).label || (node as any).id)
}

function normalizeIdentity(value: unknown): string {
  return textValue(value)
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-:/\\]+/g, '')
}

function stableJson(value: unknown): string {
  if (value === undefined || value === null) return ''
  try {
    return JSON.stringify(normalizeForCompare(value))
  } catch {
    return String(value || '')
  }
}

function normalizeForCompare(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeForCompare)

  if (value && typeof value === 'object') {
    const result: LooseRecord = {}
    for (const key of Object.keys(value as LooseRecord).sort()) {
      if (key.startsWith('_manual_document_edit') || key === '_prd_section_edit' || key === '_prd_change_impact') continue
      if (['editedAt', 'updatedAt', 'prdSyncedAt'].includes(key)) continue
      result[key] = normalizeForCompare((value as LooseRecord)[key])
    }
    return result
  }

  return value
}

function safeKey(value: string): string {
  return normalizeIdentity(value).slice(0, 48) || Math.random().toString(36).slice(2, 8)
}

function camelCase(value: string): string {
  return value.replace(/_([a-z])/g, (_, char) => String(char).toUpperCase())
}

function boolValue(value: unknown): boolean {
  return value === true || value === 'true' || value === 1 || value === '1'
}

function unique<T>(items: T[]): T[] {
  return Array.from(new Set(items))
}

function deepClone<T>(value: T): T {
  try {
    return JSON.parse(JSON.stringify(value || {}))
  } catch {
    return { ...(value as any) }
  }
}
