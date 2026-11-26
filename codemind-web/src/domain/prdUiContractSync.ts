import type { ModuleTreeNode } from '../types/codemind'
import type { PrdPageRequirementChangeImpact } from './prdChangeImpactProcessor'
import {
  normalizePrdPrototypeModuleRequirement,
  semanticLabelsByKind,
} from './prdPrototypeModuleSemantics'

type LooseRecord = Record<string, any>

export type PrdUiContractSyncResult = {
  changed: boolean
  contract: LooseRecord
  summary: string
  warnings: string[]
}

export function resolvePrdPageRequirementForNode(
  modulePrdContent: unknown,
  pageNode: ModuleTreeNode | null | undefined,
): LooseRecord | null {
  const content = recordValue(modulePrdContent)
  const requirements = Array.isArray(content.page_requirements)
    ? content.page_requirements
    : Array.isArray(content.pageRequirements)
      ? content.pageRequirements
      : []

  if (!pageNode || !requirements.length) return null

  const nodeAliases = pageNodeIdentityAliases(pageNode)

  for (const item of requirements) {
    const req = recordValue(item)
    const aliases = pageRequirementIdentityAliases(req)
    if (aliases.some((alias) => nodeAliases.includes(alias))) {
      return req
    }
  }

  const pageName = normalizeIdentity(displayNodeName(pageNode))
  if (pageName) {
    return requirements
      .map(recordValue)
      .find((req) => {
        const reqName = normalizeIdentity(textValue(req.page_name || req.pageName || req.name || req.title))
        return Boolean(reqName && (reqName === pageName || reqName.includes(pageName) || pageName.includes(reqName)))
      }) || null
  }

  return null
}

export function applyPrdPageRequirementToUiContract(request: {
  contract: unknown
  pageRequirement: unknown
  previousPageRequirement?: unknown
  changeImpact?: PrdPageRequirementChangeImpact | null
  pageNode?: ModuleTreeNode | null
}): PrdUiContractSyncResult {
  const source = recordValue(request.contract)
  const cloned = deepClone(source)
  const requirement = normalizePrdPrototypeModuleRequirement(request.pageRequirement, {
    pageNode: request.pageNode || null,
    uiContract: request.contract,
  })
  const previousRequirement = recordValue(request.previousPageRequirement)

  if (!Object.keys(cloned).length || !Object.keys(requirement).length) {
    return {
      changed: false,
      contract: cloned,
      summary: '没有找到可同步的 PRD 页面设计或交互原型。',
      warnings: ['EMPTY_SOURCE'],
    }
  }

  const components = collectComponents(cloned)
  const rootComponents = resolveTopLevelComponentContainer(cloned)

  const semanticFilterFields = semanticLabelsByKind(requirement, ['FILTER_FIELD', 'FIELD'])
  const semanticFormFields = semanticLabelsByKind(requirement, ['FORM_FIELD'])
  const semanticTableColumns = semanticLabelsByKind(requirement, ['TABLE_COLUMN'])
  const semanticPrimaryActions = semanticLabelsByKind(requirement, ['ACTION'])
  const semanticRowActions = semanticLabelsByKind(requirement, ['ROW_ACTION'])

  const filterFields = semanticFilterFields.length
    ? semanticFilterFields
    : changedListText(previousRequirement.filter_fields || previousRequirement.filterFields, requirement.filter_fields || requirement.filterFields)

  const formFields = semanticFormFields.length
    ? semanticFormFields
    : changedListText(previousRequirement.form_fields || previousRequirement.formFields, requirement.form_fields || requirement.formFields)

  const tableColumns = semanticTableColumns.length
    ? semanticTableColumns
    : changedListText(previousRequirement.table_columns || previousRequirement.tableColumns, requirement.table_columns || requirement.tableColumns)

  const primaryActions = semanticPrimaryActions.length
    ? semanticPrimaryActions
    : changedListText(previousRequirement.primary_actions || previousRequirement.primaryActions || previousRequirement.user_actions || previousRequirement.userActions, requirement.primary_actions || requirement.primaryActions || requirement.user_actions || requirement.userActions)

  const rowActions = semanticRowActions.length
    ? semanticRowActions
    : changedListText(previousRequirement.row_actions || previousRequirement.rowActions, requirement.row_actions || requirement.rowActions)

  const changes: string[] = []
  const warnings: string[] = []

  const prdPrimaryActionCatalog = listText(
    requirement.primary_actions ||
    requirement.primaryActions ||
    requirement.user_actions ||
    requirement.userActions,
  )
  const prdRowActionCatalog = listText(
    requirement.row_actions ||
    requirement.rowActions,
  )
  const runtimePrimaryActions = primaryActions.length ? primaryActions : prdPrimaryActionCatalog
  const runtimeRowActions = rowActions.length ? rowActions : prdRowActionCatalog
  const navigationTargets = listText(requirement.navigation_targets || requirement.navigationTargets)
  const navigationEdges = normalizeNavigationEdges(requirement.navigation_edges || requirement.navigationEdges)
  const interactionNotes = textValue(requirement.interaction_notes || requirement.interactionNotes)
  const dataBindingKey = resolvePageDataBindingKey(request.pageNode, requirement)
  const syncedNavigationTargets = ensureContractNavigationTargets(cloned, navigationTargets, navigationEdges, dataBindingKey)
  if (syncedNavigationTargets) changes.push(`同步页面衔接目标 ${syncedNavigationTargets} 个`)
  const syncedNavigationEdges = ensureContractNavigationEdges(cloned, navigationEdges, dataBindingKey)
  if (syncedNavigationEdges) changes.push(`同步页面流转规则 ${syncedNavigationEdges} 条`)

  const layoutMode = resolveLayoutMode(requirement)
  const mainComponents = listText(requirement.main_components || requirement.mainComponents)

  // 1. 依据 PRD 定位层（main_components + page_type/layout）补齐页面结构组件，
  //    避免所有页面都退化成“筛选 + 表单 + 表格”。
  const structuralCount = ensureMainComponents({
    components,
    rootComponents,
    mainComponents,
    layoutMode,
  })
  if (structuralCount) changes.push(`补齐页面结构组件 ${structuralCount} 处`)

  // 2. 只有页面确实承载筛选 / 表单 / 表格语义时，才创建对应容器。
  const wantsFilter = filterFields.length > 0 && layoutMode !== 'DASHBOARD'
  const wantsTable = tableColumns.length > 0 && layoutMode !== 'DASHBOARD'
  const wantsForm = formFields.length > 0 && layoutMode !== 'DETAIL' && layoutMode !== 'DASHBOARD'

  const filterForm = wantsFilter
    ? ensureComponent({
        components,
        rootComponents,
        preferredTypes: ['FILTER_FORM', 'SEARCH_FORM', 'QUERY_FORM'],
        fallback: buildFormComponent('prd_sync_filter_form', '筛选条件', 'FILTER_FORM'),
      })
    : null

  const mainForm = wantsForm
    ? ensureComponent({
        components,
        rootComponents,
        preferredTypes: ['FORM', 'EDIT_FORM', 'CREATE_FORM', 'DETAIL_FORM'],
        fallback: buildFormComponent('prd_sync_main_form', '表单信息', 'FORM'),
      })
    : null

  const table = wantsTable
    ? ensureComponent({
        components,
        rootComponents,
        preferredTypes: ['DATA_TABLE', 'TABLE', 'ANT_TABLE'],
        fallback: buildTableComponent('prd_sync_data_table', '数据列表'),
      })
    : null

  if (filterForm && filterFields.length) {
    const count = upsertFields(filterForm, filterFields)
    if (count) changes.push(`同步筛选字段 ${count} 个`)
  }

  if (mainForm && formFields.length) {
    const count = upsertFields(mainForm, formFields)
    if (count) changes.push(`同步表单字段 ${count} 个`)
  }

  // 详情页的表单字段属于“只读展示”，归入描述列表而不是可编辑表单。
  if (layoutMode === 'DETAIL' && formFields.length) {
    const description = ensureComponent({
      components,
      rootComponents,
      preferredTypes: ['DESCRIPTION_LIST', 'DESCRIPTIONS'],
      fallback: buildMainComponent('概要信息', {
        preferredTypes: ['DESCRIPTION_LIST'],
        title: '概要信息',
        description: '展示当前业务的核心信息。',
      }),
    })
    const count = upsertDescriptionItems(description, formFields)
    if (count) changes.push(`同步详情字段 ${count} 个`)
  }

  if (table && tableColumns.length) {
    const count = upsertColumns(table, tableColumns)
    if (count) changes.push(`同步表格列 ${count} 个`)
  }

  if (runtimePrimaryActions.length) {
    let actionBar = findActionBar(components)
    if (!actionBar) {
      actionBar = ensureComponent({
        components,
        rootComponents,
        preferredTypes: ['ACTION_BAR', 'TOOLBAR', 'BUTTON_GROUP'],
        fallback: buildActionBarComponent('prd_sync_action_bar', '操作区'),
      })
    }

    const actionTarget = table || filterForm || actionBar

    const count =
      upsertActions(actionTarget, runtimePrimaryActions, 'PRIMARY') +
      (table && table !== actionTarget ? upsertActions(table, runtimePrimaryActions, 'PRIMARY') : 0) +
      ensureContractInteractions(cloned, runtimePrimaryActions, 'PRIMARY', {
        dataBindingKey,
        navigationEdges,
        layoutMode,
      })

    const visibleCount = ensureRuntimeVisiblePrimaryActions(components, runtimePrimaryActions)

    if (count || visibleCount) {
      changes.push(`同步主操作 ${Math.max(count, visibleCount, runtimePrimaryActions.length)} 个`)
    }
  }

  if (runtimeRowActions.length) {
    const context = { dataBindingKey, navigationEdges, layoutMode }
    const count = table
      ? upsertActions(table, runtimeRowActions, 'ROW') +
        ensureContractInteractions(cloned, runtimeRowActions, 'ROW', context)
      : ensureContractInteractions(cloned, runtimeRowActions, 'ROW', context)

    if (count) changes.push(`同步行操作 ${count} 个`)
  }

  attachPrototypeModuleSemantics(cloned, requirement)

  updatePageMetadata(cloned, {
    pageNode: request.pageNode || null,
    requirement,
    changeImpact: request.changeImpact || null,
    runtimePrimaryActions,
    runtimeRowActions,
    changes,
    navigationTargets,
    interactionNotes,
  })

  if (!changes.length) {
    return {
      changed: false,
      contract: cloned,
      summary: '当前原型已与最新 PRD 页面设计保持一致。',
      warnings,
    }
  }

  return {
    changed: true,
    contract: cloned,
    summary: `已根据最新 PRD 同步原型：${changes.join('，')}。`,
    warnings,
  }
}

function ensureComponent(args: {
  components: LooseRecord[]
  rootComponents: LooseRecord[]
  preferredTypes: string[]
  fallback: LooseRecord
}): LooseRecord {
  const found = args.components.find((component) => {
    const type = componentType(component)
    return args.preferredTypes.some((item) => type.includes(item))
  })

  if (found) return found

  args.rootComponents.push(args.fallback)
  args.components.push(args.fallback)
  return args.fallback
}

function findActionBar(components: LooseRecord[]): LooseRecord | null {
  return components.find((component) => {
    const type = componentType(component)
    return type.includes('ACTION_BAR') || type.includes('TOOLBAR') || type.includes('BUTTON_GROUP')
  }) || null
}

function buildFormComponent(componentId: string, title: string, type: string): LooseRecord {
  return {
    componentId,
    id: componentId,
    componentType: type,
    type,
    title,
    props: {
      title,
      fields: [],
      actions: [],
    },
    fields: [],
    actions: [],
  }
}

function buildTableComponent(componentId: string, title: string): LooseRecord {
  return {
    componentId,
    id: componentId,
    componentType: 'DATA_TABLE',
    type: 'DATA_TABLE',
    title,
    props: {
      title,
      columns: [],
      actions: [],
      rowActions: [],
      dataSource: [],
    },
    columns: [],
    actions: [],
    rowActions: [],
    dataSource: [],
  }
}

function buildActionBarComponent(componentId: string, title: string): LooseRecord {
  return {
    componentId,
    id: componentId,
    componentType: 'ACTION_BAR',
    type: 'ACTION_BAR',
    title,
    props: {
      title,
      actions: [],
    },
    actions: [],
  }
}

type MainComponentSpec = {
  preferredTypes: string[]
  title: string
  description?: string
  props?: LooseRecord
}

// 依据 PRD 的 page_type / layout_type / ui_intent 推导页面布局模式。
// 布局模式决定哪些容器（筛选/表单/表格）需要被创建，以及详情页字段是否走只读描述列表。
function resolveLayoutMode(requirement: LooseRecord): string {
  const pageType = textValue(requirement.page_type || requirement.pageType)
  const fromPageType = normalizeLayoutModeFromPageType(pageType)
  if (fromPageType && fromPageType !== 'UNKNOWN' && fromPageType !== 'FORM') return fromPageType

  const layoutType = textValue(requirement.layout_type || requirement.layoutType)
  const pageName = textValue(requirement.page_name || requirement.pageName || requirement.name || requirement.title)
  const pageGoal = textValue(
    requirement.page_goal ||
    requirement.pageGoal ||
    requirement.page_goal_text ||
    requirement.pageGoalText ||
    requirement.description ||
    requirement.summary,
  )
  const fromPageDescriptor = normalizeLayoutModeFromPageType([
    layoutType,
    pageName,
    pageGoal,
  ].filter(Boolean).join(' '))
  if (fromPageDescriptor && fromPageDescriptor !== 'UNKNOWN' && fromPageDescriptor !== 'FORM') return fromPageDescriptor

  const uiIntent = recordValue(requirement.uiIntent || requirement.ui_intent)
  const fromIntent = normalizeLayoutModeFromPageType(textValue(uiIntent.layoutMode || uiIntent.layout_mode))
  if (fromIntent && fromIntent !== 'UNKNOWN' && fromIntent !== 'FORM') return fromIntent

  if (fromPageType && fromPageType !== 'UNKNOWN') return fromPageType
  if (fromPageDescriptor && fromPageDescriptor !== 'UNKNOWN') return fromPageDescriptor
  if (fromIntent && fromIntent !== 'UNKNOWN') return fromIntent

  return 'UNKNOWN'
}

function normalizeLayoutModeFromPageType(value: unknown): string {
  const pageType = textValue(value).toUpperCase()
  if (/DASHBOARD|看板/.test(pageType)) return 'DASHBOARD'
  if (/APPROVAL|AUDIT|DIALOG|审批|审核/.test(pageType)) return 'WORKFLOW'
  if (/DETAIL|VIEW|详情/.test(pageType)) return 'DETAIL'
  if (/EDIT|UPDATE|修改|编辑/.test(pageType)) return 'EDIT'
  if (/CREATE|ADD|NEW|新增|新建|创建|录入/.test(pageType)) return 'CREATE'
  if (/FORM|表单/.test(pageType)) return 'FORM'
  if (/LIST|SEARCH|TABLE|列表|查询|管理/.test(pageType)) return 'LIST'
  return 'UNKNOWN'
}

// 把 PRD 定位层的中文 main_components 映射为契约组件类型。
// 返回 null 表示该标签由筛选/表单/表格专用路径处理，或属于内部噪声（如“运行时演示数据”）。
function classifyMainComponentSpec(label: string): MainComponentSpec | null {
  if (/运行时演示|演示数据|mock|runtime/i.test(label)) return null
  if (/页面主体|主体区域/i.test(label)) return null
  // 行操作区由表格行操作专用路径处理，避免生成空操作栏。
  if (/行操作|row.?action/i.test(label)) return null

  if (/指标|kpi|metric|stat|统计卡|关键指标/i.test(label)) {
    return {
      preferredTypes: ['STATS', 'STAT_GRID', 'METRIC_GRID', 'KPI'],
      title: '核心指标',
      description: '展示当前业务的关键量化指标。',
      props: {
        items: [
          { label: '累计总数', value: 12846 },
          { label: '今日新增', value: 328 },
          { label: '待处理', value: 47 },
          { label: '完成率', value: '86.2%' },
        ],
      },
    }
  }

  if (/趋势|图表|chart|trend|分析图|统计图/i.test(label)) {
    return {
      preferredTypes: ['CHART', 'TREND_CHART', 'LINE_CHART', 'BAR_CHART'],
      title: '趋势分析',
      description: '展示关键指标的近期变化趋势。',
      props: {
        items: [
          { label: '一月', value: 42 },
          { label: '二月', value: 58 },
          { label: '三月', value: 47 },
          { label: '四月', value: 71 },
          { label: '五月', value: 66 },
          { label: '六月', value: 89 },
        ],
      },
    }
  }

  if (/操作记录|动态|日志|时间线|记录|timeline/i.test(label)) {
    return {
      preferredTypes: ['TIMELINE'],
      title: '操作记录',
      description: '展示业务对象的处理动态与流转轨迹。',
      props: {
        items: [
          { title: '创建', description: '提交并保存业务信息', time: '2026-03-14 09:30', status: 'finish' },
          { title: '审核', description: '待负责人审批', time: '2026-03-14 10:12', status: 'process' },
          { title: '完成', description: '流程处理完成', time: '—', status: 'wait' },
        ],
      },
    }
  }

  if (/步骤|流程|进度|审批进度|审核进度|steps|wizard/i.test(label)) {
    return {
      preferredTypes: ['STEPS'],
      title: '处理进度',
      description: '展示当前业务的处理阶段与流转状态。',
      props: {
        items: [
          { title: '提交申请', description: '已提交' },
          { title: '审批中', description: '当前处理中' },
          { title: '处理完成', description: '等待处理' },
        ],
      },
    }
  }

  if (/审核信息|审批信息|审核人|审批人/i.test(label)) {
    return {
      preferredTypes: ['DESCRIPTION_LIST', 'DESCRIPTIONS'],
      title: '审核信息',
      description: '展示当前业务的审核相关字段。',
    }
  }

  if (/概要|摘要|基本信息|概览|summary|overview/i.test(label)) {
    return {
      preferredTypes: ['DESCRIPTION_LIST', 'DESCRIPTIONS'],
      title: '概要信息',
      description: '展示当前业务的核心信息。',
      props: {
        items: [
          { label: '业务编号', value: 'NO-20260314-001' },
          { label: '业务名称', value: '示例业务' },
          { label: '状态', value: '处理中' },
          { label: '负责人', value: '张伟' },
          { label: '创建时间', value: '2026-03-14 09:30' },
          { label: '备注', value: '按合同约定执行' },
        ],
      },
    }
  }

  if (/详情|明细|detail/i.test(label)) {
    return {
      preferredTypes: ['DESCRIPTION_LIST', 'DESCRIPTIONS'],
      title: '详情信息',
      description: '展示当前业务对象的详细信息。',
    }
  }

  if (/快捷入口|入口|快捷|entry|quick/i.test(label)) {
    return {
      preferredTypes: ['LIST', 'CARD_LIST'],
      title: '快捷入口',
      description: '常用业务功能的快捷进入方式。',
      props: {
        items: [
          { title: '新建业务', description: '发起新的业务申请' },
          { title: '待办处理', description: '查看并处理待办任务' },
          { title: '数据查询', description: '查询历史业务数据' },
        ],
      },
    }
  }

  if (/提示|校验|说明|notice|alert|hint/i.test(label)) {
    return {
      preferredTypes: ['ALERT'],
      title: '办理提示',
      description: '提交前请核对关键信息，必填项需完整填写。',
      props: { level: 'info' },
    }
  }

  if (/操作|按钮|action|toolbar|button/i.test(label)) {
    return {
      preferredTypes: ['ACTION_BAR', 'TOOLBAR', 'BUTTON_GROUP'],
      title: '操作区',
    }
  }

  // 以下标签由筛选/表单/表格专用路径处理，避免重复创建容器。
  if (/表单|form|录入|编辑/i.test(label)) return null
  if (/筛选|查询|搜索|filter|search/i.test(label)) return null
  if (/数据列表|列表|表格|table/i.test(label)) return null

  return null
}

function ensureMainComponents(args: {
  components: LooseRecord[]
  rootComponents: LooseRecord[]
  mainComponents: string[]
  layoutMode: string
}): number {
  let changed = 0

  for (const label of args.mainComponents) {
    const spec = classifyMainComponentSpec(label)
    if (!spec) continue

    const existing = args.components.find((component) => {
      const type = componentType(component)
      const typeMatches = spec.preferredTypes.some((item) => type.includes(item))
      if (!typeMatches) return false
      // 同类型但不同标题（例如 APPROVAL 的“审核信息”与“业务详情”）应各自独立，
      // 只有标题一致或为空时才视为可复用容器。
      const title = textValue(component.title || component.label)
      return !title || normalizeIdentity(title) === normalizeIdentity(spec.title)
    })

    if (existing) {
      const existingProps = recordValue(existing.props)
      const hasItems = Array.isArray(existing.items) && existing.items.length > 0
      const hasPropsItems = Array.isArray(existingProps.items) && existingProps.items.length > 0

      if (spec.props?.items && !hasItems && !hasPropsItems) {
        existing.props = { ...existingProps, items: spec.props.items }
        existing.items = spec.props.items
        changed += 1
      }
      continue
    }

    const component = buildMainComponent(label, spec)
    args.rootComponents.push(component)
    args.components.push(component)
    changed += 1
  }

  return changed
}

function buildMainComponent(label: string, spec: MainComponentSpec): LooseRecord {
  const componentId = `prd_sync_${safeKey(label, 'block') || 'block'}`
  return {
    componentId,
    id: componentId,
    componentType: spec.preferredTypes[0],
    type: spec.preferredTypes[0],
    title: spec.title,
    description: spec.description || '',
    props: {
      title: spec.title,
      description: spec.description || '',
      ...(spec.props || {}),
    },
  }
}

function upsertDescriptionItems(component: LooseRecord, names: string[]): number {
  const items = ensureArray(component, 'items')
  let changed = 0

  for (const name of names) {
    const label = textValue(name)
    if (!label) continue

    const key = safeKey(label, 'field')
    const exists = items.some((item) => {
      const record = recordValue(item)
      return normalizeIdentity(textValue(record.key || record.label || record.fieldKey)) === normalizeIdentity(label)
    })

    if (exists) continue

    items.push({
      key,
      label,
      value: mockDescriptionValue(label),
    })
    changed += 1
  }

  syncArrayToProps(component, 'items', items)
  return changed
}

function mockDescriptionValue(label: string): string {
  if (/编号|单号|编码|code|no/i.test(label)) return 'NO-20260314-001'
  if (/状态|阶段|status|state/i.test(label)) return '处理中'
  if (/时间|日期|date|time/i.test(label)) return '2026-03-14 09:30'
  if (/负责人|经办人|联系人|owner|manager/i.test(label)) return '张伟'
  if (/金额|数量|价格|amount|price|count/i.test(label)) return '12,846'
  if (/名称|name|title/i.test(label)) return '示例业务'
  if (/备注|说明|描述|remark|description/i.test(label)) return '按合同约定执行'
  return '—'
}

function upsertFields(component: LooseRecord, names: string[]): number {
  const fields = ensureArray(component, 'fields')
  let changed = 0

  for (const name of names) {
    const label = textValue(name)
    if (!label) continue

    const key = safeKey(label, 'field')
    const exists = fields.some((item) => identityOfField(item) === key || normalizeIdentity(textValue(item?.label || item?.name || item?.title)) === normalizeIdentity(label))

    if (exists) continue

    fields.push({
      key,
      fieldKey: key,
      name: key,
      label,
      title: label,
      type: inferFieldType(label),
      required: false,
      placeholder: `请输入${label}`,
    })
    changed += 1
  }

  syncArrayToProps(component, 'fields', fields)
  return changed
}

function upsertColumns(component: LooseRecord, names: string[]): number {
  const columns = ensureArray(component, 'columns')
  let changed = 0

  for (const name of names) {
    const label = textValue(name)
    if (!label) continue

    const key = safeKey(label, 'column')
    const exists = columns.some((item) => identityOfColumn(item) === key || normalizeIdentity(textValue(item?.title || item?.label || item?.name)) === normalizeIdentity(label))

    if (exists) continue

    columns.push({
      key,
      dataIndex: key,
      fieldKey: key,
      title: label,
      label,
      width: inferColumnWidth(label),
    })
    changed += 1
  }

  syncArrayToProps(component, 'columns', columns)
  return changed
}

function upsertActions(component: LooseRecord, names: string[], actionScope: 'PRIMARY' | 'ROW'): number {
  const key = actionScope === 'ROW' ? 'rowActions' : 'actions'
  const actions = ensureArray(component, key)
  let changed = 0

  for (const name of names) {
    const label = textValue(name)
    if (!label) continue

    const actionId = safeActionId(label, actionScope)
    const existingIndex = actions.findIndex((item) => actionIdentityMatches(item, label, actionId))

    if (existingIndex >= 0) {
      const existing = actions[existingIndex]

      if (existing && typeof existing === 'object' && !Array.isArray(existing)) {
        const record = existing as LooseRecord
        const oldLabel = textValue(record.label || record.name || record.title || record.text)

        if (oldLabel !== label) {
          record.label = label
          record.name = label
          record.title = label
          record.text = label
          record.actionId = actionId
          record.id = actionId
          record.key = actionId
          record.type = inferActionType(label, actionScope)
          record.interactionType = inferActionType(label, actionScope)
          record.source = 'MODULE_PRD.page_requirements'
          record.generatedBy = 'PRD_SYNC_RUNTIME_VISIBLE_ACTIONS'
          changed += 1
        }
      } else if (typeof existing === 'string' && normalizeIdentity(existing) !== normalizeIdentity(label)) {
        actions[existingIndex] = buildPrdSyncAction(label, actionId, actionScope)
        changed += 1
      }

      continue
    }

    actions.push(buildPrdSyncAction(label, actionId, actionScope))
    changed += 1
  }

  syncArrayToProps(component, key, actions)

  if (actionScope === 'ROW') {
    syncArrayToProps(component, 'row_actions', actions)
    syncActionIdsToProps(component, 'rowActionIds', actions)
    syncActionIdsToProps(component, 'row_action_ids', actions)
  } else {
    syncArrayToProps(component, 'actions', actions)
    syncArrayToProps(component, 'primaryActions', actions)
    syncArrayToProps(component, 'primary_actions', actions)
    syncArrayToProps(component, 'headerActions', actions)
    syncArrayToProps(component, 'tableHeaderActions', actions)
    syncArrayToProps(component, 'toolbarActions', actions)
    syncArrayToProps(component, 'buttons', actions)

    syncActionIdsToProps(component, 'actionIds', actions)
    syncActionIdsToProps(component, 'primaryActionIds', actions)
    syncActionIdsToProps(component, 'headerActionIds', actions)
    syncActionIdsToProps(component, 'tableHeaderActionIds', actions)
    syncActionIdsToProps(component, 'toolbarActionIds', actions)
  }

  return changed
}

function ensureRuntimeVisiblePrimaryActions(components: LooseRecord[] | LooseRecord, names: string[]): number {
  if (!names.length) return 0

  const candidates = Array.isArray(components)
    ? components.filter((component) => componentType(component).includes('TABLE'))
    : components
      ? [components]
      : []

  let changed = 0

  for (const candidate of candidates) {
    changed += upsertActions(candidate, names, 'PRIMARY')
  }

  return changed
}

function ensureContractInteractions(
  contract: LooseRecord,
  names: string[],
  actionScope: 'PRIMARY' | 'ROW',
  context: {
    dataBindingKey: string
    navigationEdges: NormalizedNavigationEdge[]
    layoutMode: string
  },
): number {
  const interactions = ensureArray(contract, 'interactions')
  let changed = 0

  for (const name of names) {
    const label = textValue(name)
    if (!label) continue

    const actionId = safeActionId(label, actionScope)
    const nextInteraction = buildRuntimeInteraction({
      actionId,
      label,
      actionScope,
      dataBindingKey: context.dataBindingKey,
      navigationEdges: context.navigationEdges,
      layoutMode: context.layoutMode,
    })
    const existingIndex = interactions.findIndex((item) => actionIdentityMatches(item, label, actionId))

    if (existingIndex >= 0) {
      const current = recordValue(interactions[existingIndex])
      if (!current) continue

      const upgraded = mergeRuntimeInteraction(current, nextInteraction)
      if (JSON.stringify(current) !== JSON.stringify(upgraded)) {
        interactions[existingIndex] = upgraded
        changed += 1
      }
      continue
    }

    interactions.push(nextInteraction)
    changed += 1
  }

  contract.interactions = interactions
  return changed
}

function mergeRuntimeInteraction(current: LooseRecord, next: LooseRecord): LooseRecord {
  const currentPayload = recordValue(current.payload)
  const nextPayload = recordValue(next.payload)
  return {
    ...current,
    actionId: textValue(current.actionId || next.actionId),
    id: textValue(current.id || next.id),
    key: textValue(current.key || next.key),
    label: textValue(next.label || current.label),
    name: textValue(next.name || current.name),
    title: textValue(next.title || current.title),
    type: textValue(next.type || current.type),
    interactionType: textValue(next.interactionType || current.interactionType),
    interactionKind: textValue(next.interactionKind || current.interactionKind),
    dataEffect: textValue(next.dataEffect || current.dataEffect),
    trigger: textValue(next.trigger || current.trigger),
    source: textValue(current.source || next.source),
    generatedBy: textValue(current.generatedBy || next.generatedBy),
    dataBindingKey: textValue(next.dataBindingKey || current.dataBindingKey),
    targetPageId: next.targetPageId || current.targetPageId || null,
    targetPageKey: next.targetPageKey || current.targetPageKey || '',
    targetPageName: next.targetPageName || current.targetPageName || '',
    payload: {
      ...currentPayload,
      ...nextPayload,
    },
  }
}

function syncActionIdsToProps(component: LooseRecord, key: string, actions: unknown[]): void {
  const ids = actions
    .map((item) => {
      if (typeof item === 'string') return safeActionId(item, 'PRIMARY')
      const record = recordValue(item)
      return textValue(record.actionId || record.action_id || record.id || safeActionId(record.label || record.name || record.title, 'PRIMARY'))
    })
    .filter(Boolean)

  if (!ids.length) return

  component[key] = ids
  const props = recordValue(component.props)
  props[key] = ids
  component.props = props
}

function buildPrdSyncAction(label: string, actionId: string, actionScope: 'PRIMARY' | 'ROW'): LooseRecord {
  return {
    actionId,
    id: actionId,
    key: actionId,
    label,
    name: label,
    title: label,
    text: label,
    type: inferActionType(label, actionScope),
    interactionType: inferActionType(label, actionScope),
    scope: actionScope,
    source: 'MODULE_PRD.page_requirements',
    generatedBy: 'PRD_SYNC_RUNTIME_VISIBLE_ACTIONS',
  }
}

function actionIdentityMatches(item: unknown, label: string, actionId: string): boolean {
  if (typeof item === 'string') {
    return normalizeIdentity(item) === normalizeIdentity(label) ||
      normalizeIdentity(item) === normalizeIdentity(actionId)
  }

  const record = recordValue(item)
  return normalizeIdentity(textValue(
    record.actionId ||
    record.action_id ||
    record.id ||
    record.key ||
    record.label ||
    record.name ||
    record.title ||
    record.text,
  )) === normalizeIdentity(actionId) ||
    normalizeIdentity(textValue(record.label || record.name || record.title || record.text)) === normalizeIdentity(label)
}

function safeActionId(label: unknown, actionScope: 'PRIMARY' | 'ROW'): string {
  const raw = textValue(label)
  const normalized = normalizeIdentity(raw)

  if (/导出|export|excel|csv/i.test(raw)) return 'export'
  if (/导入|import/i.test(raw)) return 'import'
  if (/下载|download/i.test(raw)) return 'download'
  if (/上传|upload/i.test(raw)) return 'upload'
  if (/新增|新建|创建|add|create|new/i.test(raw)) return 'create'
  if (/查询|搜索|筛选|search|query|filter/i.test(raw)) return 'search'
  if (/重置|reset/i.test(raw)) return 'reset'
  if (/保存|save/i.test(raw)) return 'save'
  if (/提交|submit/i.test(raw)) return 'submit'
  if (/审核|审批|approve|audit/i.test(raw)) return 'approve'
  if (/删除|delete|remove/i.test(raw)) return 'delete'
  if (/编辑|edit/i.test(raw)) return 'edit'
  if (/详情|查看|view|detail/i.test(raw)) return 'view_detail'
  if (/取消|关闭|cancel|close/i.test(raw)) return 'cancel'

  return `${actionScope === 'ROW' ? 'row_action' : 'action'}_${normalized || Math.random().toString(36).slice(2, 8)}`
}

function inferActionType(label: string, actionScope: 'PRIMARY' | 'ROW'): string {
  if (/导出|export|excel|csv/i.test(label)) return 'EXPORT'
  if (/导入|import/i.test(label)) return 'IMPORT'
  if (/下载|download/i.test(label)) return 'DOWNLOAD'
  if (/上传|upload/i.test(label)) return 'UPLOAD'
  if (/新增|新建|创建|add|create|new/i.test(label)) return 'NAVIGATE_OR_OPEN_CREATE'
  if (/查询|搜索|筛选|search|query|filter/i.test(label)) return 'QUERY'
  if (/重置|reset/i.test(label)) return 'RESET'
  if (/保存|save/i.test(label)) return 'SUBMIT_FORM'
  if (/提交|submit/i.test(label)) return 'SUBMIT'
  if (/审核|审批|approve|audit/i.test(label)) return 'WORKFLOW_APPROVAL'
  if (/删除|delete|remove/i.test(label)) return 'DELETE'
  if (/编辑|edit/i.test(label)) return 'EDIT'
  if (/详情|查看|view|detail/i.test(label)) return 'VIEW_DETAIL'
  if (/取消|关闭|cancel|close/i.test(label)) return 'CANCEL'
  return actionScope === 'ROW' ? 'ROW_ACTION' : 'SHOW_TOAST'
}

type NormalizedNavigationEdge = {
  action: string
  targetPageId: string
  targetPageKey: string
  targetPageName: string
  route: string
  interactionKind: string
  dataEffect: string
  sourceDataBindingKey: string
  targetDataBindingKey: string
}

function normalizeNavigationEdges(value: unknown): NormalizedNavigationEdge[] {
  if (!Array.isArray(value)) return []
  const result: NormalizedNavigationEdge[] = []
  for (const item of value) {
    const record = recordValue(item)
    const action = textValue(record.action || record.actionLabel || record.label).trim() || '页面衔接'
    const targetPageId = textValue(record.targetPageId || record.target_page_id)
    const targetPageKey = textValue(record.targetPageKey || record.target_page_key)
    const targetPageName = textValue(record.targetPageName || record.target_page_name || record.targetName || record.target)
    const route = textValue(record.route || record.targetRoute || record.target_route)
    const interactionKind = normalizeInteractionKind(record.interactionKind || record.interaction_kind || record.navigationType || record.navigation_type || action)
    const dataEffect = normalizeDataEffect(record.dataEffect || record.data_effect || record.runtimeEffect || record.runtime_effect || action || targetPageName)
    const sourceDataBindingKey = textValue(record.sourceDataBindingKey || record.source_data_binding_key)
    const targetDataBindingKey = textValue(record.targetDataBindingKey || record.target_data_binding_key)
    if (!targetPageId && !targetPageName && !targetPageKey) continue
    result.push({
      action,
      targetPageId,
      targetPageKey,
      targetPageName,
      route,
      interactionKind,
      dataEffect,
      sourceDataBindingKey,
      targetDataBindingKey,
    })
  }
  return result
}

function normalizeInteractionKind(value: unknown): string {
  const text = textValue(value)
  if (/弹窗|确认|dialog|modal/i.test(text)) return 'OPEN_DIALOG'
  if (/返回|return|back/i.test(text)) return 'RETURN_TO_PAGE'
  if (/保存|提交|submit|save/i.test(text)) return 'SUBMIT_AND_NAVIGATE'
  return text || 'OPEN_PAGE'
}

function normalizeDataEffect(value: unknown): string {
  const text = textValue(value)
  if (/新增|新建|创建|录入|create|add|ADD_ROW|CREATE_RECORD/i.test(text)) return 'CREATE_RECORD'
  if (/编辑|修改|更新|edit|update|UPDATE_ROW|UPDATE_RECORD/i.test(text)) return 'UPDATE_RECORD'
  if (/删除|移除|作废|delete|remove|DELETE_ROW/i.test(text)) return 'DELETE_RECORD'
  if (/审核|审批|approve|audit/i.test(text)) return 'APPROVE_RECORD'
  if (/详情|查看|view|detail|READ_RECORD/i.test(text)) return 'READ_RECORD'
  if (/弹窗|确认|dialog|modal/i.test(text)) return 'CONFIRM_ACTION'
  return text || 'NAVIGATE'
}

function ensureContractNavigationTargets(
  contract: LooseRecord,
  targets: string[],
  edges: NormalizedNavigationEdge[],
  dataBindingKey: string,
): number {
  const navigationTargets = ensureArray(contract, 'navigationTargets')
  const existingKeys = new Set(
    navigationTargets.map((item) => navigationTargetIdentity(item)).filter(Boolean),
  )
  let changed = 0

  const upsert = (target: LooseRecord) => {
    const identity = navigationTargetIdentity(target)
    if (!identity || existingKeys.has(identity)) return

    navigationTargets.push({
      targetPageId: textValue(target.targetPageId || target.target_page_id),
      targetPageKey: textValue(target.targetPageKey || target.target_page_key),
      targetPageName: textValue(target.targetPageName || target.target_page_name || target.name || target.label),
      route: textValue(target.route || target.targetRoute || target.target_route),
      actionLabel: textValue(target.action || target.actionLabel || target.label),
      interactionKind: textValue(target.interactionKind || target.interaction_kind),
      dataEffect: textValue(target.dataEffect || target.data_effect),
      sourceDataBindingKey: textValue(target.sourceDataBindingKey || target.source_data_binding_key) || dataBindingKey,
      targetDataBindingKey: textValue(target.targetDataBindingKey || target.target_data_binding_key) || resolveTargetDataBindingKey(target as NormalizedNavigationEdge, dataBindingKey),
      rowContext: shouldCarryRowContext(textValue(target.action || target.actionLabel || target.label), textValue(target.interactionKind || target.interaction_kind), textValue(target.dataEffect || target.data_effect)),
      status: 'RESOLVED',
      source: 'MODULE_PRD.navigation_edges',
    })
    existingKeys.add(identity)
    changed += 1
  }

  for (const edge of edges) {
    upsert(edge)
  }

  for (const targetName of targets) {
    const targetPageName = textValue(targetName)
    if (!targetPageName) continue
    upsert({
      targetPageId: '',
      targetPageKey: '',
      targetPageName,
      route: '',
      status: 'PLANNED',
      source: 'MODULE_PRD.navigation_targets',
    })
  }

  contract.navigationTargets = navigationTargets
  return changed
}

function ensureContractNavigationEdges(
  contract: LooseRecord,
  edges: NormalizedNavigationEdge[],
  dataBindingKey: string,
): number {
  if (!edges.length) return 0

  const currentEdges = ensureArray(contract, 'navigationEdges')
  const existingKeys = new Set(
    currentEdges.map((item) => navigationEdgeIdentity(item)).filter(Boolean),
  )
  let changed = 0

  for (const edge of edges) {
    const edgeId = safeKey([
      textValue(edge.action),
      textValue(edge.targetPageId || edge.targetPageKey || edge.targetPageName),
    ].filter(Boolean).join('_'), 'navigation_edge')
    const normalizedEdge = {
      edgeId,
      sourcePageId: textValue(contract.page?.pageId || contract.identity?.pageId),
      sourcePageKey: textValue(contract.page?.pageKey || contract.identity?.pageKey),
      sourcePageName: textValue(contract.page?.pageName || contract.identity?.pageName),
      actionId: safeActionId(edge.action, 'PRIMARY'),
      actionLabel: edge.action,
      interactionKind: edge.interactionKind || 'OPEN_PAGE',
      dataEffect: edge.dataEffect || 'NAVIGATE',
      sourceDataBindingKey: edge.sourceDataBindingKey || dataBindingKey,
      targetDataBindingKey: edge.targetDataBindingKey || resolveTargetDataBindingKey(edge, dataBindingKey),
      targetPageId: edge.targetPageId,
      targetPageKey: edge.targetPageKey,
      targetPageName: edge.targetPageName,
      route: edge.route,
      rowContext: shouldCarryRowContext(edge.action, edge.interactionKind, edge.dataEffect),
      afterSubmit: shouldNavigateAfterSubmit(edge.action, edge.interactionKind)
        ? {
          type: 'NAVIGATE' as const,
          targetPageId: edge.targetPageId,
          targetPageKey: edge.targetPageKey,
          targetPageName: edge.targetPageName,
          route: edge.route,
          refreshTargetData: true,
        }
        : null,
      status: 'RESOLVED',
      source: 'MODULE_PRD.navigation_edges',
    }

    const identity = navigationEdgeIdentity(normalizedEdge)
    if (!identity || existingKeys.has(identity)) continue

    currentEdges.push(normalizedEdge)
    existingKeys.add(identity)
    changed += 1
  }

  contract.navigationEdges = currentEdges
  return changed
}

function navigationEdgeIdentity(value: unknown): string {
  const record = recordValue(value)
  return normalizeIdentity([
    record.edgeId,
    record.sourcePageId || record.source_page_id,
    record.sourcePageKey || record.source_page_key,
    record.actionId || record.action_id || record.actionLabel || record.action || record.label,
    record.targetPageId || record.target_page_id,
    record.targetPageKey || record.target_page_key,
    record.targetPageName || record.target_page_name,
  ].filter(Boolean).join('|'))
}

function shouldCarryRowContext(action: unknown, interactionKind: unknown, dataEffect: unknown): boolean {
  const text = [action, interactionKind, dataEffect].map(textValue).join(' ')
  return /编辑|修改|详情|查看|审核|审批|UPDATE_RECORD|READ_RECORD|APPROVE_RECORD|edit|detail|view|approve|audit/i.test(text)
}

function shouldNavigateAfterSubmit(action: unknown, interactionKind: unknown): boolean {
  const text = [action, interactionKind].map(textValue).join(' ')
  return /保存|提交|完成|返回|SUBMIT_AND_NAVIGATE|RETURN_TO_PAGE|save|submit|return|back/i.test(text)
}

function navigationTargetIdentity(value: unknown): string {
  const record = recordValue(value)
  return normalizeIdentity(
    record.targetPageId ||
    record.target_page_id ||
    record.targetPageKey ||
    record.target_page_key ||
    record.targetPageName ||
    record.target_page_name ||
    record.name ||
    record.label,
  )
}

function resolvePageDataBindingKey(
  pageNode: ModuleTreeNode | null | undefined,
  requirement: LooseRecord,
): string {
  const explicit = textValue(requirement.dataBindingKey || requirement.data_binding_key)
  if (explicit) return explicit

  const pageKey = textValue(requirement.page_key || requirement.pageKey)
  const fallbackName = pageNode ? displayNodeName(pageNode) : ''
  return `${safeKey(pageKey || fallbackName, 'page')}_rows`
}

function runtimeActionTypeFor(label: string): string {
  if (/查询|搜索|筛选|检索|query|search|filter/i.test(label)) return 'QUERY_DATA'
  if (/删除|移除|作废|delete|remove/i.test(label)) return 'DELETE_ROW'
  if (/保存|提交|save|submit/i.test(label)) return 'SUBMIT_FORM'
  if (/新增|新建|创建|add|create|new/i.test(label)) return 'NAVIGATE'
  if (/编辑|edit/i.test(label)) return 'NAVIGATE'
  if (/详情|查看|view|detail/i.test(label)) return 'NAVIGATE'
  if (/重置|reset/i.test(label)) return 'RESET_FORM'
  if (/取消|关闭|cancel|close/i.test(label)) return 'CLOSE_MODAL'
  return 'SHOW_TOAST'
}

function runtimeEffectFor(label: string, layoutMode: string): 'ADD_ROW' | 'UPDATE_ROW' | 'DELETE_ROW' | undefined {
  if (/删除|移除|作废|delete|remove/i.test(label)) return 'DELETE_ROW'
  if (!/保存|提交|save|submit/i.test(label)) return undefined
  if (/编辑|修改|更新|edit|update/i.test(label) || layoutMode === 'EDIT') return 'UPDATE_ROW'
  if (/新增|新建|创建|录入|add|create|new/i.test(label) || layoutMode === 'CREATE') return 'ADD_ROW'
  if (layoutMode === 'FORM') return 'ADD_ROW'
  return 'UPDATE_ROW'
}

function findNavigationEdgeForAction(
  edges: NormalizedNavigationEdge[],
  label: string,
): NormalizedNavigationEdge | null {
  const normalized = normalizeIdentity(label)
  return edges.find((edge) => {
    const action = normalizeIdentity(edge.action)
    return action && (action === normalized || normalized.includes(action) || action.includes(normalized))
  }) || null
}

function findNavigationEdgeForRuntimeEffect(
  edges: NormalizedNavigationEdge[],
  effect: 'ADD_ROW' | 'UPDATE_ROW' | 'DELETE_ROW' | undefined,
  layoutMode: string,
): NormalizedNavigationEdge | null {
  if (!edges.length) return null

  if (effect === 'ADD_ROW') {
    return edges.find((edge) => /新建|新增|创建|录入|create|add|form/i.test(edge.targetPageName || edge.action)) || null
  }

  if (effect === 'UPDATE_ROW') {
    return edges.find((edge) => /编辑|修改|edit/i.test(edge.targetPageName || edge.action)) || null
  }

  if (layoutMode === 'DETAIL') {
    return edges.find((edge) => /列表|查询|管理|list/i.test(edge.targetPageName || edge.action)) || null
  }

  return null
}

function findPostSubmitNavigationEdge(
  edges: NormalizedNavigationEdge[],
  effect: 'ADD_ROW' | 'UPDATE_ROW' | 'DELETE_ROW',
  layoutMode: string,
): NormalizedNavigationEdge | null {
  const listTarget = edges.find((edge) => /列表|查询|管理|台账|list|table/i.test(edge.targetPageName || edge.action))
  if (listTarget) return listTarget

  if (effect === 'ADD_ROW' || effect === 'UPDATE_ROW' || layoutMode === 'FORM') {
    return edges.find((edge) => /返回|完成|保存|提交/i.test(edge.action)) || null
  }

  return null
}

function resolveTargetDataBindingKey(
  edge: NormalizedNavigationEdge | null,
  fallback: string,
): string {
  if (!edge) return fallback

  const targetKey = safeKey(edge.targetPageKey || edge.targetPageName || edge.targetPageId, '')
  return targetKey ? `${targetKey}_rows` : fallback
}

function buildRuntimeInteraction(args: {
  actionId: string
  label: string
  actionScope: 'PRIMARY' | 'ROW'
  dataBindingKey: string
  navigationEdges: NormalizedNavigationEdge[]
  layoutMode: string
}): LooseRecord {
  const { actionId, label, actionScope } = args
  const runtimeType = runtimeActionTypeFor(label)
  const runtimeEffect = runtimeEffectFor(label, args.layoutMode)
  const navigationEdge =
    findNavigationEdgeForAction(args.navigationEdges, label) ||
    findNavigationEdgeForRuntimeEffect(args.navigationEdges, runtimeEffect, args.layoutMode)

  const base: LooseRecord = {
    actionId,
    id: actionId,
    key: actionId,
    label,
    name: label,
    title: label,
    type: runtimeType,
    interactionType: inferActionType(label, actionScope),
    interactionKind: navigationEdge?.interactionKind || '',
    dataEffect: runtimeEffect || navigationEdge?.dataEffect || '',
    trigger: actionScope === 'ROW' ? 'TABLE_ROW_ACTION' : 'TABLE_HEADER_ACTION',
    source: 'MODULE_PRD.page_requirements',
    generatedBy: 'PRD_SYNC_RUNTIME_VISIBLE_ACTIONS',
    dataBindingKey: args.dataBindingKey,
    targetPageId: null,
    payload: {},
  }

  if (runtimeType === 'NAVIGATE' && navigationEdge) {
    base.targetPageId = navigationEdge.targetPageId || null
    base.targetPageKey = navigationEdge.targetPageKey || ''
    base.targetPageName = navigationEdge.targetPageName || ''
    base.payload = {
      targetPageId: navigationEdge.targetPageId,
      targetPageKey: navigationEdge.targetPageKey,
      targetPageName: navigationEdge.targetPageName,
      route: navigationEdge.route,
      navigationReason: navigationEdge.action,
      interactionKind: navigationEdge.interactionKind || 'OPEN_PAGE',
      dataEffect: navigationEdge.dataEffect || '',
      targetDataBindingKey: navigationEdge.targetDataBindingKey || resolveTargetDataBindingKey(navigationEdge, args.dataBindingKey),
      sourceDataBindingKey: navigationEdge.sourceDataBindingKey || args.dataBindingKey,
      rowContext: actionScope === 'ROW' || shouldCarryRowContext(navigationEdge.action, navigationEdge.interactionKind, navigationEdge.dataEffect),
    }
  }

  if (runtimeEffect) {
    const afterSubmitTarget = runtimeType === 'SUBMIT_FORM'
      ? findPostSubmitNavigationEdge(args.navigationEdges, runtimeEffect, args.layoutMode)
      : null

    base.payload = {
      ...base.payload,
      runtimeEffect,
      dataEffect: runtimeEffect,
      dataBindingKey: args.dataBindingKey,
      targetDataBindingKey: resolveTargetDataBindingKey(afterSubmitTarget, args.dataBindingKey),
      afterSubmit: afterSubmitTarget
        ? {
          type: 'NAVIGATE',
          targetPageId: afterSubmitTarget.targetPageId,
          targetPageKey: afterSubmitTarget.targetPageKey,
          targetPageName: afterSubmitTarget.targetPageName,
          route: afterSubmitTarget.route,
          sourceDataBindingKey: afterSubmitTarget.sourceDataBindingKey || args.dataBindingKey,
          targetDataBindingKey: afterSubmitTarget.targetDataBindingKey || resolveTargetDataBindingKey(afterSubmitTarget, args.dataBindingKey),
          refreshTargetData: true,
        }
        : null,
      message: runtimeEffect === 'DELETE_ROW'
        ? '已从当前演示列表中移除该记录，刷新页面后恢复。'
        : runtimeEffect === 'ADD_ROW'
          ? '已新增一条演示记录，并同步到目标列表页。'
          : '已更新当前演示记录，并同步到目标列表页。',
    }
  }

  if (runtimeType === 'QUERY_DATA') {
    base.payload = {
      ...base.payload,
      dataBindingKey: args.dataBindingKey,
    }
  }

  return base
}


function updatePageMetadata(contract: LooseRecord, args: {
  pageNode: ModuleTreeNode | null
  requirement: LooseRecord
  changeImpact?: PrdPageRequirementChangeImpact | null
  runtimePrimaryActions?: string[]
  runtimeRowActions?: string[]
  changes: string[]
  navigationTargets: string[]
  interactionNotes: string
}) {
  const metadata = recordValue(contract.metadata)

  metadata.prototypeRequirementSync = {
    mode: 'PRD_TO_UI_CONTRACT_SYNC',
    classification: 'PRD_SYNC',
    visualOnly: false,
    source: 'MODULE_PRD.page_requirements',
    syncedAt: new Date().toISOString(),
    pageId: args.pageNode?.id || null,
    pageName: args.pageNode ? displayNodeName(args.pageNode) : textValue(args.requirement.page_name || args.requirement.pageName),
    changes: args.changes,
    runtimePrimaryActions: args.runtimePrimaryActions || [],
    runtimeRowActions: args.runtimeRowActions || [],
    visualReasons: args.changeImpact?.visualReasons || [],
    semanticImpacts: args.changeImpact?.semanticImpacts || [],
    capabilityHints: args.changeImpact?.capabilityHints || [],
    downstreamArtifactTypes: args.changeImpact?.downstreamArtifactTypes || [],
    navigationTargets: args.navigationTargets,
    interactionNotes: args.interactionNotes,
  }

  const edits = Array.isArray(metadata.prototypeLocalEdits)
    ? metadata.prototypeLocalEdits
    : []

  edits.push({
    editId: `prd_ui_sync_${Date.now()}`,
    editedAt: new Date().toISOString(),
    instruction: '同步 PRD 变更到原型',
    targetLabel: metadata.prototypeRequirementSync.pageName || '当前页面',
    changes: args.changes,
    requirementSyncImpact: {
      classification: 'PRD_SYNC',
      visualOnly: false,
      targetArtifactTypes: Array.from(new Set(['UI_CONTRACT', ...(args.changeImpact?.downstreamArtifactTypes || [])])),
      semanticChangeTypes: ['FIELD', 'ACTION', 'TABLE_COLUMN', 'NAVIGATION'],
      semanticImpacts: args.changeImpact?.semanticImpacts || [],
      capabilityHints: args.changeImpact?.capabilityHints || [],
      downstreamArtifactTypes: args.changeImpact?.downstreamArtifactTypes || [],
    },
  })

  metadata.prototypeLocalEdits = edits
  metadata.localEditRevision = Number(metadata.localEditRevision || 0) + 1
  metadata.localEditUpdatedAt = new Date().toISOString()
  contract.metadata = metadata
}

function resolveTopLevelComponentContainer(contract: LooseRecord): LooseRecord[] {
  const page = recordValue(contract.page)

  if (Array.isArray(page.componentTree)) return page.componentTree
  if (Array.isArray(contract.componentTree)) return contract.componentTree
  if (Array.isArray(contract.components)) return contract.components
  if (Array.isArray(page.components)) return page.components

  const layout = recordValue(page.layout || contract.layout)
  if (Array.isArray(layout.components)) return layout.components
  if (Array.isArray(layout.children)) return layout.children

  page.componentTree = []
  return page.componentTree
}

function collectComponents(root: unknown): LooseRecord[] {
  const result: LooseRecord[] = []
  const seen = new Set<unknown>()

  const visit = (value: unknown) => {
    if (!value || typeof value !== 'object' || seen.has(value)) return
    seen.add(value)

    if (!Array.isArray(value)) {
      const record = value as LooseRecord
      if (looksLikeComponent(record)) {
        result.push(record)
      }

      for (const key of ['components', 'componentTree', 'children', 'items', 'sections', 'panels', 'cards', 'slots', 'body']) {
        const child = record[key]
        if (Array.isArray(child)) child.forEach(visit)
        else if (child && typeof child === 'object') visit(child)
      }
    } else {
      value.forEach(visit)
    }
  }

  visit(root)
  return result
}

function looksLikeComponent(record: LooseRecord): boolean {
  return Boolean(
    record.componentId ||
    record.componentType ||
    record.type ||
    Array.isArray(record.fields) ||
    Array.isArray(record.columns) ||
    Array.isArray(record.actions) ||
    Array.isArray(record.rowActions),
  )
}

function componentType(component: LooseRecord): string {
  return textValue(component.componentType || component.type || component.kind || '').toUpperCase()
}

function ensureArray(component: LooseRecord, key: string): any[] {
  if (Array.isArray(component[key])) return component[key]

  const props = recordValue(component.props)
  if (Array.isArray(props[key])) {
    component[key] = props[key]
    return component[key]
  }

  component[key] = []
  props[key] = component[key]
  component.props = props
  return component[key]
}

function syncArrayToProps(component: LooseRecord, key: string, value: any[]) {
  const props = recordValue(component.props)
  component[key] = value
  props[key] = value
  component.props = props
}

function pageNodeIdentityAliases(node: ModuleTreeNode): string[] {
  const extra = recordValue((node as any).extra)
  return unique([
    node.id,
    (node as any).moduleKey,
    (node as any).key,
    displayNodeName(node),
    extra.pageKey,
    extra.page_key,
    extra.route,
    extra.routePath,
    extra.route_path,
  ].map(normalizeIdentity).filter(Boolean))
}

function pageRequirementIdentityAliases(req: LooseRecord): string[] {
  return unique([
    req.page_id,
    req.pageId,
    req.page_key,
    req.pageKey,
    req.key,
    req.route,
    req.route_path,
    req.routePath,
    req.page_name,
    req.pageName,
    req.name,
    req.title,
  ].map(normalizeIdentity).filter(Boolean))
}

function identityOfField(item: any): string {
  return normalizeIdentity(textValue(item?.key || item?.fieldKey || item?.name || item?.id || item?.label || item))
}

function identityOfColumn(item: any): string {
  return normalizeIdentity(textValue(item?.key || item?.dataIndex || item?.fieldKey || item?.name || item?.id || item?.title || item))
}

function inferFieldType(label: string): string {
  if (/时间|日期|date|time/i.test(label)) return 'date'
  if (/金额|数量|价格|总价|number|amount|price|qty/i.test(label)) return 'number'
  if (/状态|类型|类别|等级|status|type/i.test(label)) return 'select'
  if (/备注|说明|描述|原因|note|desc|reason/i.test(label)) return 'textarea'
  return 'input'
}

function inferColumnWidth(label: string): number {
  if (/备注|说明|描述|原因/.test(label)) return 220
  if (/时间|日期/.test(label)) return 180
  if (/状态|类型/.test(label)) return 120
  return 150
}

function safeKey(value: string, fallback: string): string {
  const text = textValue(value)
  const ascii = text
    .trim()
    .toLowerCase()
    .replace(/[\s\-:/\\]+/g, '_')
    .replace(/[^a-z0-9_\u4e00-\u9fa5]+/gi, '')
    .replace(/^_+|_+$/g, '')

  if (ascii) return ascii.slice(0, 64)

  return `${fallback}_${Math.random().toString(36).slice(2, 8)}`
}


function changedListText(previousValue: unknown, nextValue: unknown): string[] {
  const previousItems = listText(previousValue)
  const nextItems = listText(nextValue)

  if (!nextItems.length) return []

  // 没有旧页面设计时，允许做一次完整对齐；正常 PRD 手动保存会带 previousPageRequirement，
  // 所以用户只加“导出”时，这里只返回“导出”。
  if (!previousItems.length) {
    return nextItems
  }

  const previousKeys = new Set(previousItems.map(normalizeIdentity).filter(Boolean))

  return nextItems.filter((item) => {
    const key = normalizeIdentity(item)
    return Boolean(key && !previousKeys.has(key))
  })
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

function displayNodeName(node: ModuleTreeNode): string {
  return textValue((node as any).moduleName || (node as any).name || (node as any).title || (node as any).label || node.id)
}

function normalizeIdentity(value: unknown): string {
  return textValue(value)
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-:/\\]+/g, '')
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

function attachPrototypeModuleSemantics(contract: LooseRecord, requirement: LooseRecord): void {
  const metadata = recordValue(contract.metadata)
  const semanticItems = Array.isArray(requirement.semanticItems)
    ? requirement.semanticItems
    : Array.isArray(requirement.semantic_items)
      ? requirement.semantic_items
      : []

  const uiIntent = recordValue(requirement.uiIntent || requirement.ui_intent)
  const prototypeBinding = recordValue(requirement.prototypeBinding || requirement.prototype_binding)

  metadata.prototypeModuleSemantics = {
    pageId: textValue(requirement.pageId || requirement.page_id),
    pageKey: textValue(requirement.pageKey || requirement.page_key),
    pageName: textValue(requirement.pageName || requirement.page_name),
    uiIntent,
    semanticItems,
    prototypeBinding,
    syncedAt: new Date().toISOString(),
    source: 'MODULE_PRD.page_requirements',
  }

  metadata.requirementSemanticLinks = semanticItems.map((item: LooseRecord) => ({
    sourceKey: textValue(item.semanticId || item.semantic_id),
    semanticKind: textValue(item.kind || item.semanticKind),
    label: textValue(item.label),
    sourceField: textValue(item.sourceField || item.source_field),
    relationType: 'PRD_SEMANTIC_TO_UI_CONTRACT',
    status: textValue(item.status) || 'ACTIVE',
  }))

  contract.metadata = metadata
}

// =====================================================================
// 反向同步：UI_CONTRACT → PRD page_requirements 定位层
// 正向 applyPrdPageRequirementToUiContract 的逆操作，复用同一套组件识别。
// =====================================================================

export type ContractToPrdChangeClass =
  | 'NONE'
  | 'VISUAL_ONLY'
  | 'SEMANTIC'
  | 'STRUCTURAL'

export type PrdLocationSnapshot = {
  filter_fields: string[]
  form_fields: string[]
  table_columns: string[]
  primary_actions: string[]
  row_actions: string[]
}

export type PrdLocationDelta = {
  classification: ContractToPrdChangeClass
  changed: boolean
  filter_fields: { added: string[]; removed: string[] }
  form_fields: { added: string[]; removed: string[] }
  table_columns: { added: string[]; removed: string[] }
  primary_actions: { added: string[]; removed: string[] }
  row_actions: { added: string[]; removed: string[] }
}

function fieldLabels(component: LooseRecord | null | undefined, key: string): string[] {
  if (!component) return []
  const direct = component[key]
  const props = recordValue(component.props)
  return listText(Array.isArray(direct) ? direct : props[key])
}

function diffList(previous: string[], next: string[]): { added: string[]; removed: string[] } {
  const prevKeys = new Set(previous.map(normalizeIdentity).filter(Boolean))
  const nextKeys = new Set(next.map(normalizeIdentity).filter(Boolean))
  return {
    added: next.filter((item) => {
      const key = normalizeIdentity(item)
      return Boolean(key && !prevKeys.has(key))
    }),
    removed: previous.filter((item) => {
      const key = normalizeIdentity(item)
      return Boolean(key && !nextKeys.has(key))
    }),
  }
}

export function extractPrdLocationSnapshot(contract: unknown): PrdLocationSnapshot {
  const components = collectComponents(recordValue(contract))

  const filterForm = components.find((component) => {
    const type = componentType(component)
    return type.includes('FILTER_FORM') || type.includes('SEARCH_FORM') || type.includes('QUERY_FORM')
  })

  const mainForm = components.find((component) => {
    const type = componentType(component)
    return (
      type.includes('FORM') ||
      type.includes('EDIT_FORM') ||
      type.includes('CREATE_FORM') ||
      type.includes('DETAIL_FORM')
    ) && component !== filterForm
  })

  const table = components.find((component) => {
    const type = componentType(component)
    return type.includes('TABLE') || type.includes('DATA_TABLE') || type.includes('ANT_TABLE')
  })

  const actionBar = findActionBar(components)

  return {
    filter_fields: fieldLabels(filterForm, 'fields'),
    form_fields: fieldLabels(mainForm, 'fields'),
    table_columns: fieldLabels(table, 'columns'),
    primary_actions: unique([...fieldLabels(actionBar, 'actions'), ...fieldLabels(table, 'actions')]),
    row_actions: fieldLabels(table, 'rowActions'),
  }
}

export function classifyContractToPrdChange(contract: unknown, previousContract: unknown): PrdLocationDelta {
  const next = extractPrdLocationSnapshot(contract)
  const previous = extractPrdLocationSnapshot(previousContract)

  const delta = {
    filter_fields: diffList(previous.filter_fields, next.filter_fields),
    form_fields: diffList(previous.form_fields, next.form_fields),
    table_columns: diffList(previous.table_columns, next.table_columns),
    primary_actions: diffList(previous.primary_actions, next.primary_actions),
    row_actions: diffList(previous.row_actions, next.row_actions),
  }

  const semanticChanged = Object.values(delta).some((item) => item.added.length || item.removed.length)

  const nextTypes = unique(collectComponents(recordValue(contract)).map(componentType).filter(Boolean)).sort()
  const prevTypes = unique(collectComponents(recordValue(previousContract)).map(componentType).filter(Boolean)).sort()
  const structuralChanged = nextTypes.join('|') !== prevTypes.join('|')

  let classification: ContractToPrdChangeClass = 'NONE'
  if (structuralChanged) classification = 'STRUCTURAL'
  else if (semanticChanged) classification = 'SEMANTIC'
  else classification = 'VISUAL_ONLY'

  return {
    classification,
    changed: semanticChanged || structuralChanged,
    ...delta,
  }
}

export function applyContractDeltaToPrdRequirement(
  requirement: unknown,
  delta: PrdLocationDelta,
): { changed: boolean; requirement: LooseRecord; classification: ContractToPrdChangeClass } {
  const cloned = deepClone(recordValue(requirement))
  if (!Object.keys(cloned).length) {
    return { changed: false, requirement: cloned, classification: 'NONE' }
  }

  const apply = (snapshotKey: keyof PrdLocationSnapshot, keyCandidates: string[]) => {
    const item = delta[snapshotKey]
    if (!item.added.length && !item.removed.length) return

    const targetKey = keyCandidates.find((key) => cloned[key] !== undefined) || keyCandidates[0]
    const current = listText(cloned[targetKey])
    const removedKeys = new Set(item.removed.map(normalizeIdentity).filter(Boolean))
    const kept = current.filter((label) => {
      const key = normalizeIdentity(label)
      return !key || !removedKeys.has(key)
    })
    cloned[targetKey] = unique([...kept, ...item.added])
  }

  apply('filter_fields', ['filter_fields', 'filterFields'])
  apply('form_fields', ['form_fields', 'formFields'])
  apply('table_columns', ['table_columns', 'tableColumns'])
  apply('primary_actions', ['primary_actions', 'primaryActions'])
  apply('row_actions', ['row_actions', 'rowActions'])

  // user_actions 是 primary + row 的派生并集，保持一致性
  if (cloned.user_actions !== undefined || cloned.userActions !== undefined) {
    const primary = listText(cloned.primary_actions ?? cloned.primaryActions)
    const row = listText(cloned.row_actions ?? cloned.rowActions)
    const merged = unique([...primary, ...row])
    if (cloned.user_actions !== undefined) cloned.user_actions = merged
    if (cloned.userActions !== undefined) cloned.userActions = merged
  }

  return { changed: delta.changed, requirement: cloned, classification: delta.classification }
}

export function applyContractDeltaToModulePrd(
  modulePrdContent: unknown,
  pageNode: ModuleTreeNode | null | undefined,
  delta: PrdLocationDelta,
): { changed: boolean; content: LooseRecord } | null {
  const content = deepClone(recordValue(modulePrdContent))
  const requirements = Array.isArray(content.page_requirements)
    ? content.page_requirements
    : Array.isArray(content.pageRequirements)
      ? content.pageRequirements
      : null

  if (!pageNode || !requirements || !delta.changed) return null

  const nodeAliases = pageNodeIdentityAliases(pageNode)
  let matchedIndex = -1

  for (let i = 0; i < requirements.length; i += 1) {
    const aliases = pageRequirementIdentityAliases(recordValue(requirements[i]))
    if (aliases.some((alias) => nodeAliases.includes(alias))) {
      matchedIndex = i
      break
    }
  }

  if (matchedIndex < 0) {
    const pageName = normalizeIdentity(displayNodeName(pageNode))
    if (pageName) {
      matchedIndex = requirements.findIndex((raw) => {
        const req = recordValue(raw)
        const reqName = normalizeIdentity(
          textValue(req.page_name || req.pageName || req.name || req.title),
        )
        return Boolean(reqName && (reqName === pageName || reqName.includes(pageName) || pageName.includes(reqName)))
      })
    }
  }

  if (matchedIndex < 0) return null

  const applied = applyContractDeltaToPrdRequirement(requirements[matchedIndex], delta)
  if (!applied.changed) return { changed: false, content }

  requirements[matchedIndex] = applied.requirement
  return { changed: true, content }
}