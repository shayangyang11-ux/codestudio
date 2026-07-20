export type UiContractCoverageStatus = 'READY' | 'PARTIAL' | 'MISSING'

export interface UiContractCoverageReference {
  id: string
  label: string
  type: 'COMPONENT' | 'ACTION' | 'API' | 'STATE' | 'MOCK'
}

export interface UiContractCoverageIssue {
  level: 'BLOCKER' | 'WARNING' | 'INFO'
  message: string
  suggestion?: string
}

export interface UiContractCoverageRow {
  featureId: string
  featureName: string
  featureKind: string
  status: UiContractCoverageStatus
  statusLabel: string
  sourceStatus: string
  componentRefs: UiContractCoverageReference[]
  actionRefs: UiContractCoverageReference[]
  apiRefs: UiContractCoverageReference[]
  stateRefs: UiContractCoverageReference[]
  mockRefs: UiContractCoverageReference[]
  issues: UiContractCoverageIssue[]
}

export interface UiContractHandoffChecklistItem {
  id: string
  label: string
  passed: boolean
  level: 'BLOCKER' | 'WARNING' | 'INFO'
  message: string
  suggestion?: string
}

export interface UiContractCoverageSummary {
  featureCount: number
  readyCount: number
  partialCount: number
  missingCount: number
  coverageRate: number
  coverageLabel: string
  canHandoffToDevAgent: boolean
  rows: UiContractCoverageRow[]
  checklist: UiContractHandoffChecklistItem[]
}

type AnyRecord = Record<string, unknown>

export function analyzeUiContractCoverage(
  contract: unknown,
): UiContractCoverageSummary {
  const source = getRecord(contract)
  const page = resolvePage(source)
  const components = collectComponents(page)
  const actions = collectActions(components)
  const apiBindings = asArray(source.apiBindings).map(getRecord)
  const mockData = getRecord(source.mockData || source.mock_data)
  const pageStates = resolvePageStates(source, page)
  const featureCoverage = asArray(source.featureCoverage || source.feature_coverage).map(getRecord)
  const developmentRules = getRecord(source.developmentRules || source.development_rules)

  const rows = featureCoverage.length
    ? featureCoverage.map((feature, index) => buildCoverageRow(
      feature,
      index,
      {
        components,
        actions,
        apiBindings,
        mockData,
        pageStates,
      },
    ))
    : []

  const readyCount = rows.filter((row) => row.status === 'READY').length
  const partialCount = rows.filter((row) => row.status === 'PARTIAL').length
  const missingCount = rows.filter((row) => row.status === 'MISSING').length
  const coverageRate = rows.length
    ? Math.round((readyCount / rows.length) * 100)
    : 0

  const checklist = buildHandoffChecklist(
    source,
    page,
    {
      components,
      actions,
      apiBindings,
      mockData,
      pageStates,
      featureCoverage,
      developmentRules,
      rows,
    },
  )

  const blockingItems = checklist.filter((item) => item.level === 'BLOCKER' && !item.passed)

  return {
    featureCount: rows.length,
    readyCount,
    partialCount,
    missingCount,
    coverageRate,
    coverageLabel: rows.length
      ? `${readyCount}/${rows.length} 功能点可交付`
      : '未声明功能覆盖',
    canHandoffToDevAgent: blockingItems.length === 0 && rows.length > 0 && missingCount === 0,
    rows,
    checklist,
  }
}

function buildCoverageRow(
  feature: AnyRecord,
  index: number,
  context: {
    components: AnyRecord[]
    actions: AnyRecord[]
    apiBindings: AnyRecord[]
    mockData: AnyRecord
    pageStates: AnyRecord[]
  },
): UiContractCoverageRow {
  const featureId = firstText(
    feature.featureId,
    feature.featureKey,
    feature.id,
    feature.key,
    `feature_${index + 1}`,
  )

  const featureName = firstText(
    feature.featureName,
    feature.featureTitle,
    feature.name,
    feature.title,
    feature.label,
    featureId,
  )

  const featureKind = firstText(
    feature.featureKind,
    feature.kind,
    feature.type,
    'FEATURE',
  )

  const componentIds = normalizeStringArray(
    feature.componentIds
      || feature.component_ids
      || feature.components
      || feature.componentRefs,
  )
  const actionIds = normalizeStringArray(
    feature.actionIds
      || feature.action_ids
      || feature.actions
      || feature.actionRefs,
  )
  const apiBindingKeys = normalizeStringArray(
    feature.apiBindingKeys
      || feature.api_binding_keys
      || feature.apiBindings
      || feature.api_refs,
  )
  const stateKeys = normalizeStringArray(
    feature.pageStateKeys
      || feature.stateKeys
      || feature.states
      || feature.pageStates,
  )
  const mockKeys = normalizeStringArray(
    feature.mockDataKeys
      || feature.mockKeys
      || feature.mockData
      || feature.dataKeys,
  )

  const componentRefs = resolveComponentRefs(
    componentIds,
    context.components,
  )
  const actionRefs = resolveActionRefs(
    actionIds,
    context.actions,
  )
  const apiRefs = resolveApiRefs(
    apiBindingKeys,
    context.apiBindings,
  )
  const stateRefs = resolveStateRefs(
    stateKeys,
    context.pageStates,
  )
  const mockRefs = resolveMockRefs(
    mockKeys,
    context.mockData,
  )

  const issues: UiContractCoverageIssue[] = []
  const sourceStatus = asString(feature.status || feature.coverageStatus || feature.covered || '')

  if (!componentRefs.length) {
    issues.push({
      level: 'WARNING',
      message: '未绑定组件，开发 Agent 难以知道该功能由哪个 UI 区块承载。',
      suggestion: '在 featureCoverage 中补充 componentIds。',
    })
  }

  if (!actionRefs.length) {
    issues.push({
      level: 'INFO',
      message: '未绑定动作。纯展示功能可以接受，提交、查询、删除、审批类功能需要绑定 actionIds。',
      suggestion: '交互类功能建议补充 actionIds。',
    })
  }

  if (actionRefs.length && !apiRefs.length) {
    issues.push({
      level: 'WARNING',
      message: '已绑定动作，但没有绑定接口。',
      suggestion: '为动作补充 apiBindingKeys，或确保 action.apiBinding 可映射到 apiBindings。',
    })
  }

  if (mockKeys.length && !mockRefs.length) {
    issues.push({
      level: 'WARNING',
      message: '声明了 Mock 数据键，但 mockData 中没有对应数据。',
      suggestion: '补齐 mockData，保证预览和后续开发示例一致。',
    })
  }

  const explicitMissing = String(sourceStatus).toUpperCase() === 'UNCOVERED'
    || String(sourceStatus).toUpperCase() === 'MISSING'
    || sourceStatus === 'false'

  let status: UiContractCoverageStatus = 'READY'

  if (explicitMissing || (!componentRefs.length && !actionRefs.length && !apiRefs.length)) {
    status = 'MISSING'
  } else if (issues.some((issue) => issue.level === 'WARNING')) {
    status = 'PARTIAL'
  }

  return {
    featureId,
    featureName,
    featureKind,
    status,
    statusLabel: statusLabel(status),
    sourceStatus,
    componentRefs,
    actionRefs,
    apiRefs,
    stateRefs,
    mockRefs,
    issues,
  }
}

function buildHandoffChecklist(
  source: AnyRecord,
  page: AnyRecord | null,
  context: {
    components: AnyRecord[]
    actions: AnyRecord[]
    apiBindings: AnyRecord[]
    mockData: AnyRecord
    pageStates: AnyRecord[]
    featureCoverage: AnyRecord[]
    developmentRules: AnyRecord
    rows: UiContractCoverageRow[]
  },
): UiContractHandoffChecklistItem[] {
  const pageName = firstText(
    page?.pageName,
    page?.title,
    getRecord(source.identity).pageName,
    getRecord(source.page).pageName,
  )

  const hasComponentIds = context.components.every((component) => {
    return Boolean(componentId(component))
  })

  const actionWithBindingCount = context.actions.filter((action) => {
    return Boolean(asString(action.apiBinding || action.api_binding || action.bindingKey))
  }).length

  const completeApiBindingCount = context.apiBindings.filter((binding) => {
    return Boolean(asString(binding.method) && asString(binding.path))
  }).length

  const mockDataKeys = Object.keys(context.mockData)
  const stateKeys = new Set(
    context.pageStates.map((state) => stateKey(state).toUpperCase()).filter(Boolean),
  )

  return [
    {
      id: 'identity',
      label: '页面身份',
      passed: Boolean(pageName),
      level: 'BLOCKER',
      message: pageName
        ? `页面名称为：${pageName}。`
        : '缺少页面名称，开发 Agent 难以识别目标页面。',
      suggestion: '补充 pageName、pageKey、pageKind。',
    },
    {
      id: 'components',
      label: '组件树',
      passed: context.components.length > 0,
      level: 'BLOCKER',
      message: context.components.length
        ? `已识别 ${context.components.length} 个组件。`
        : '组件树为空，无法进入开发交接。',
      suggestion: '至少包含页面主容器和核心业务组件。',
    },
    {
      id: 'componentIds',
      label: '组件 ID',
      passed: Boolean(context.components.length) && hasComponentIds,
      level: 'WARNING',
      message: hasComponentIds
        ? '组件均具备稳定 ID。'
        : '存在组件缺少 componentId，后续代码生成映射会不稳定。',
      suggestion: '为每个组件补充全局唯一 componentId。',
    },
    {
      id: 'featureCoverage',
      label: '功能覆盖',
      passed: context.featureCoverage.length > 0 && context.rows.every((row) => row.status !== 'MISSING'),
      level: 'BLOCKER',
      message: context.featureCoverage.length
        ? `已声明 ${context.featureCoverage.length} 个功能覆盖项。`
        : '未声明 featureCoverage，无法证明 UI_CONTRACT 覆盖了 PAGE 下的 FEATURE。',
      suggestion: '补充 featureCoverage，将 FEATURE 映射到组件、动作、接口和状态。',
    },
    {
      id: 'actions',
      label: '交互动作',
      passed: context.actions.length === 0 || actionWithBindingCount > 0,
      level: 'WARNING',
      message: context.actions.length
        ? `共 ${context.actions.length} 个动作，其中 ${actionWithBindingCount} 个有接口绑定。`
        : '未声明动作；如果是展示页可以接受，业务操作页需要补充。',
      suggestion: '查询、提交、删除、审批、导入、导出等动作需要 actionId、mockAction、apiBinding。',
    },
    {
      id: 'apiBindings',
      label: '接口绑定',
      passed: context.apiBindings.length > 0 && completeApiBindingCount === context.apiBindings.length,
      level: 'WARNING',
      message: context.apiBindings.length
        ? `共 ${context.apiBindings.length} 个接口绑定，其中 ${completeApiBindingCount} 个具备 method/path。`
        : '没有 apiBindings，开发 Agent 无法直接衔接接口。',
      suggestion: '补充 apiBindings.method、apiBindings.path、bindingKey。',
    },
    {
      id: 'mockData',
      label: 'Mock 数据',
      passed: mockDataKeys.length > 0,
      level: 'WARNING',
      message: mockDataKeys.length
        ? `已声明 ${mockDataKeys.length} 个 Mock 数据键。`
        : 'mockData 为空，预览和开发示例数据不足。',
      suggestion: '为列表、详情、统计卡片、表单默认值补充 Mock 数据。',
    },
    {
      id: 'pageStates',
      label: '页面状态',
      passed: stateKeys.has('DEFAULT') && stateKeys.has('LOADING') && stateKeys.has('ERROR'),
      level: 'INFO',
      message: stateKeys.size
        ? `已声明状态：${Array.from(stateKeys).join('、')}。`
        : '未声明页面状态。',
      suggestion: '建议至少包含 DEFAULT、LOADING、ERROR，列表页补充 EMPTY，提交页补充 SUCCESS。',
    },
    {
      id: 'developmentRules',
      label: '开发约束',
      passed: Object.keys(context.developmentRules).length > 0,
      level: 'WARNING',
      message: Object.keys(context.developmentRules).length
        ? '已声明 developmentRules。'
        : '缺少 developmentRules，后续开发 Agent 可能无法严格继承原型约束。',
      suggestion: '补充布局、字段、接口绑定、状态处理、权限等开发约束。',
    },
  ]
}

function resolveComponentRefs(
  ids: string[],
  components: AnyRecord[],
): UiContractCoverageReference[] {
  if (!ids.length) {
    return []
  }

  return ids.map((id) => {
    const found = components.find((component) => componentId(component) === id)
    return {
      id,
      label: firstText(found?.title, found?.label, found?.name, id),
      type: 'COMPONENT',
    }
  })
}

function resolveActionRefs(
  ids: string[],
  actions: AnyRecord[],
): UiContractCoverageReference[] {
  if (!ids.length) {
    return []
  }

  return ids.map((id) => {
    const found = actions.find((action) => actionId(action) === id)
    return {
      id,
      label: firstText(found?.label, found?.title, found?.name, id),
      type: 'ACTION',
    }
  })
}

function resolveApiRefs(
  keys: string[],
  apiBindings: AnyRecord[],
): UiContractCoverageReference[] {
  if (!keys.length) {
    return []
  }

  return keys.map((key) => {
    const found = apiBindings.find((binding) => apiBindingKey(binding) === key)
    return {
      id: key,
      label: found
        ? `${asString(found.method || '-')} ${asString(found.path || '-')}`
        : key,
      type: 'API',
    }
  })
}

function resolveStateRefs(
  keys: string[],
  pageStates: AnyRecord[],
): UiContractCoverageReference[] {
  if (!keys.length) {
    return []
  }

  return keys.map((key) => {
    const found = pageStates.find((state) => stateKey(state) === key)
    return {
      id: key,
      label: firstText(found?.stateName, found?.name, key),
      type: 'STATE',
    }
  })
}

function resolveMockRefs(
  keys: string[],
  mockData: AnyRecord,
): UiContractCoverageReference[] {
  return keys
    .filter((key) => Object.prototype.hasOwnProperty.call(mockData, key))
    .map((key) => ({
      id: key,
      label: key,
      type: 'MOCK',
    }))
}

function resolvePage(
  source: AnyRecord,
): AnyRecord | null {
  if (isRecord(source.page)) {
    return source.page
  }

  const pages = asArray(source.pages).map(getRecord)
  return pages[0] || null
}

function collectComponents(
  page: AnyRecord | null,
): AnyRecord[] {
  if (!page) {
    return []
  }

  const root = [
    ...asArray(page.components),
    ...asArray(page.sections),
    ...asArray(page.nodes),
  ]

  const result: AnyRecord[] = []

  const visit = (value: unknown) => {
    const component = getRecord(value)

    if (!Object.keys(component).length) {
      return
    }

    result.push(component)

    for (const child of asArray(component.components)) visit(child)
    for (const child of asArray(component.children)) visit(child)
    for (const child of asArray(component.items)) {
      if (isRecord(child) && (child.type || child.componentId || child.components)) {
        visit(child)
      }
    }

    for (const tab of asArray(component.tabs)) {
      const tabRecord = getRecord(tab)
      for (const child of asArray(tabRecord.components)) visit(child)
      for (const child of asArray(tabRecord.children)) visit(child)
    }
  }

  for (const item of root) {
    visit(item)
  }

  return result
}

function collectActions(
  components: AnyRecord[],
): AnyRecord[] {
  const result: AnyRecord[] = []

  for (const component of components) {
    for (const action of asArray(component.actions)) {
      result.push(getRecord(action))
    }

    for (const action of asArray(component.rowActions)) {
      result.push(getRecord(action))
    }

    if (component.actionId || component.mockAction || component.apiBinding) {
      result.push(component)
    }
  }

  return result.filter((item) => Object.keys(item).length)
}

function resolvePageStates(
  source: AnyRecord,
  page: AnyRecord | null,
): AnyRecord[] {
  const raw = source.pageStates
    || source.page_states
    || page?.pageStates
    || page?.page_states
    || page?.states
    || source.states

  if (Array.isArray(raw)) {
    return raw.map(getRecord).filter((item) => Object.keys(item).length)
  }

  const record = getRecord(raw)

  return Object.entries(record).map(([key, value]) => {
    if (isRecord(value)) {
      return {
        key,
        ...value,
      }
    }

    return {
      key,
      name: key,
      description: asString(value),
    }
  })
}

function normalizeStringArray(
  value: unknown,
): string[] {
  if (!Array.isArray(value)) {
    if (typeof value === 'string' && value.trim()) {
      return [value.trim()]
    }

    return []
  }

  return value
    .map((item) => {
      if (typeof item === 'string') {
        return item.trim()
      }

      const record = getRecord(item)
      return firstText(
        record.id,
        record.key,
        record.componentId,
        record.actionId,
        record.bindingKey,
        record.stateKey,
        record.mockKey,
      )
    })
    .filter(Boolean)
}

function componentId(
  component: AnyRecord,
): string {
  return firstText(
    component.componentId,
    component.id,
    component.key,
  )
}

function actionId(
  action: AnyRecord,
): string {
  return firstText(
    action.actionId,
    action.id,
    action.key,
    action.mockAction,
  )
}

function apiBindingKey(
  binding: AnyRecord,
): string {
  return firstText(
    binding.bindingKey,
    binding.mockBinding,
    binding.mockAction,
    binding.id,
    binding.key,
  )
}

function stateKey(
  state: AnyRecord,
): string {
  return firstText(
    state.stateKey,
    state.key,
    state.name,
  )
}

function statusLabel(
  status: UiContractCoverageStatus,
): string {
  if (status === 'READY') return '可交付'
  if (status === 'PARTIAL') return '部分覆盖'
  return '缺失覆盖'
}

function isRecord(
  value: unknown,
): value is AnyRecord {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function getRecord(
  value: unknown,
): AnyRecord {
  return isRecord(value)
    ? value
    : {}
}

function asArray(
  value: unknown,
): unknown[] {
  return Array.isArray(value)
    ? value
    : []
}

function asString(
  value: unknown,
): string {
  if (value == null) {
    return ''
  }

  return String(value)
}

function firstText(
  ...values: unknown[]
): string {
  for (const value of values) {
    const text = asString(value).trim()

    if (text) {
      return text
    }
  }

  return ''
}
