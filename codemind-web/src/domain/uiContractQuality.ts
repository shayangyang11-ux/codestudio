export type UiContractQualityLevel = 'BLOCKER' | 'WARNING' | 'INFO' | 'PASS'

export type UiContractQualityCategory =
  | 'STRUCTURE'
  | 'RENDERABILITY'
  | 'DATA'
  | 'INTERACTION'
  | 'STATE'
  | 'HANDOFF'

export interface UiContractQualityIssue {
  id: string
  level: UiContractQualityLevel
  category: UiContractQualityCategory
  title: string
  message: string
  path?: string
  suggestion?: string
}

export interface UiContractQualitySummary {
  score: number
  grade: 'EXCELLENT' | 'GOOD' | 'USABLE' | 'NEEDS_FIX' | 'BLOCKED'
  gradeLabel: string
  canPreview: boolean
  canHandoff: boolean
  blockerCount: number
  warningCount: number
  infoCount: number
  passCount: number
  componentCount: number
  actionCount: number
  fieldCount: number
  bindingCount: number
  mockDataKeyCount: number
  issues: UiContractQualityIssue[]
}

type AnyRecord = Record<string, unknown>

const SUPPORTED_COMPONENT_TYPES = new Set([
  'section',
  'toolbar',
  'card',
  'description',
  'steps',
  'tabs',
  'form',
  'table',
  'modal',
  'button',
  'metric',
  'chart',
  'list',
  'empty',
])

const REQUIRED_PAGE_STATES = [
  'DEFAULT',
  'LOADING',
  'ERROR',
]

const OPTIONAL_PAGE_STATES = [
  'EMPTY',
  'SUCCESS',
]

export function analyzeUiContractQuality(
  contract: unknown,
): UiContractQualitySummary {
  const issues: UiContractQualityIssue[] = []

  if (!isRecord(contract)) {
    issues.push({
      id: 'contract.invalid',
      level: 'BLOCKER',
      category: 'STRUCTURE',
      title: '不是有效的 UI_CONTRACT 对象',
      message: '当前资产 content 不是对象，无法作为交互原型渲染。',
      suggestion: '重新生成 PAGE 节点的 UI_CONTRACT。',
    })

    return buildSummary(
      issues,
      {
        componentCount: 0,
        actionCount: 0,
        fieldCount: 0,
        bindingCount: 0,
        mockDataKeyCount: 0,
      },
    )
  }

  const source = contract as AnyRecord
  const pages = resolvePages(source)
  const primaryPage = pages[0] || resolveSinglePage(source)
  const components = collectComponents(primaryPage)
  const actions = collectActions(components)
  const fields = collectFields(components)
  const apiBindings = asArray(source.apiBindings)
  const mockData = resolveMockData(source)
  const pageStates = resolvePageStates(source, primaryPage)
  const featureCoverage = resolveFeatureCoverage(source)

  if (!asString(source.schemaVersion)) {
    issues.push({
      id: 'schema.version.missing',
      level: 'WARNING',
      category: 'STRUCTURE',
      title: '缺少 schemaVersion',
      message: '契约未显式声明版本，后续开发 Agent 难以判断兼容策略。',
      path: 'schemaVersion',
      suggestion: '使用 schemaVersion = 1.0。',
    })
  } else {
    issues.push(passIssue(
      'schema.version.pass',
      'STRUCTURE',
      '已声明契约版本',
      `schemaVersion = ${asString(source.schemaVersion)}`,
      'schemaVersion',
    ))
  }

  if (asString(source.artifactType) !== 'UI_CONTRACT') {
    issues.push({
      id: 'artifact.type.invalid',
      level: 'BLOCKER',
      category: 'STRUCTURE',
      title: 'artifactType 不是 UI_CONTRACT',
      message: '当前资产类型无法被交互原型运行时稳定识别。',
      path: 'artifactType',
      suggestion: '确保 content.artifactType = UI_CONTRACT。',
    })
  }

  if (!primaryPage) {
    issues.push({
      id: 'page.missing',
      level: 'BLOCKER',
      category: 'STRUCTURE',
      title: '缺少页面定义',
      message: '没有发现 page 或 pages[0]，运行时无法渲染页面。',
      path: 'page / pages',
      suggestion: '生成单 PAGE UI_CONTRACT，并包含页面身份、页面名称和组件树。',
    })
  } else {
    const pageName = firstText(
      primaryPage.pageName,
      primaryPage.title,
      primaryPage.name,
      getRecord(source.identity).pageName,
      getRecord(source.page).pageName,
    )

    if (!pageName) {
      issues.push({
        id: 'page.name.missing',
        level: 'WARNING',
        category: 'STRUCTURE',
        title: '页面名称缺失',
        message: '页面缺少可读名称，用户和开发 Agent 难以识别该原型对应哪个页面。',
        path: 'page.pageName / pages[0].pageName',
        suggestion: '补充 pageName 或 identity.pageName。',
      })
    } else {
      issues.push(passIssue(
        'page.name.pass',
        'STRUCTURE',
        '页面身份清晰',
        `页面名称：${pageName}`,
        'pageName',
      ))
    }
  }

  if (!components.length) {
    issues.push({
      id: 'components.empty',
      level: 'BLOCKER',
      category: 'RENDERABILITY',
      title: '组件树为空',
      message: 'UI_CONTRACT 没有可渲染组件，预览页会变成空白。',
      path: 'page.components / pages[0].components',
      suggestion: '至少生成一个主容器，并包含表单、表格、卡片、按钮等页面核心组件。',
    })
  } else {
    issues.push(passIssue(
      'components.pass',
      'RENDERABILITY',
      '组件树可渲染',
      `共识别 ${components.length} 个组件。`,
      'components',
    ))
  }

  validateComponentTypes(
    components,
    issues,
  )
  validateComponentIds(
    components,
    issues,
  )
  validateForms(
    components,
    issues,
  )
  validateTables(
    components,
    mockData,
    issues,
  )
  validateActions(
    actions,
    apiBindings,
    issues,
  )
  validateApiBindings(
    apiBindings,
    actions,
    issues,
  )
  validateMockData(
    mockData,
    components,
    issues,
  )
  validatePageStates(
    pageStates,
    issues,
  )
  validateFeatureCoverage(
    featureCoverage,
    issues,
  )
  validateDevelopmentRules(
    source,
    issues,
  )

  return buildSummary(
    issues,
    {
      componentCount: components.length,
      actionCount: actions.length,
      fieldCount: fields.length,
      bindingCount: apiBindings.length,
      mockDataKeyCount: Object.keys(mockData).length,
    },
  )
}

function validateComponentTypes(
  components: AnyRecord[],
  issues: UiContractQualityIssue[],
) {
  const unsupported = components
    .map((component, index) => ({
      index,
      type: normalizeType(component.type),
      componentId: asString(component.componentId || component.id || component.key),
    }))
    .filter((item) => item.type && !SUPPORTED_COMPONENT_TYPES.has(item.type))

  if (!unsupported.length) {
    issues.push(passIssue(
      'component.type.pass',
      'RENDERABILITY',
      '组件类型受支持',
      '所有组件都属于运行时可识别类型。',
      'components[*].type',
    ))
    return
  }

  for (const item of unsupported.slice(0, 8)) {
    issues.push({
      id: `component.type.unsupported.${item.index}`,
      level: 'WARNING',
      category: 'RENDERABILITY',
      title: '存在运行时不支持的组件类型',
      message: `组件 ${item.componentId || item.index + 1} 使用了不稳定类型：${item.type}。`,
      path: `components[${item.index}].type`,
      suggestion: '改为 section、card、form、table、tabs、modal、toolbar、button 等受支持类型。',
    })
  }
}

function validateComponentIds(
  components: AnyRecord[],
  issues: UiContractQualityIssue[],
) {
  const idMap = new Map<string, number>()
  let missingCount = 0

  for (const component of components) {
    const id = asString(component.componentId || component.id || component.key)
    if (!id) {
      missingCount += 1
      continue
    }

    idMap.set(
      id,
      (idMap.get(id) || 0) + 1,
    )
  }

  const duplicated = Array.from(idMap.entries())
    .filter(([, count]) => count > 1)
    .map(([id]) => id)

  if (missingCount) {
    issues.push({
      id: 'component.id.missing',
      level: 'WARNING',
      category: 'RENDERABILITY',
      title: '组件缺少 componentId',
      message: `发现 ${missingCount} 个组件没有稳定 componentId，后续交互绑定和开发映射会不稳定。`,
      path: 'components[*].componentId',
      suggestion: '为每个组件补充唯一 componentId。',
    })
  }

  if (duplicated.length) {
    issues.push({
      id: 'component.id.duplicated',
      level: 'BLOCKER',
      category: 'RENDERABILITY',
      title: '组件 ID 重复',
      message: `发现重复 componentId：${duplicated.slice(0, 5).join('、')}。`,
      path: 'components[*].componentId',
      suggestion: '确保所有 componentId 全局唯一。',
    })
    return
  }

  if (!missingCount) {
    issues.push(passIssue(
      'component.id.pass',
      'RENDERABILITY',
      '组件 ID 稳定',
      '所有组件都有唯一 ID，可用于运行时交互和开发映射。',
      'components[*].componentId',
    ))
  }
}

function validateForms(
  components: AnyRecord[],
  issues: UiContractQualityIssue[],
) {
  const forms = components.filter((component) => normalizeType(component.type) === 'form')

  if (!forms.length) {
    issues.push({
      id: 'form.missing',
      level: 'INFO',
      category: 'RENDERABILITY',
      title: '未发现表单组件',
      message: '当前页面可能不是表单页；如果是登录、注册、编辑、设置类页面，需要补充 form 组件。',
      path: 'components[type=form]',
    })
    return
  }

  for (const [index, form] of forms.entries()) {
    const fields = asArray(form.fields)
    const actions = asArray(form.actions)

    if (!fields.length) {
      issues.push({
        id: `form.fields.empty.${index}`,
        level: 'BLOCKER',
        category: 'RENDERABILITY',
        title: '表单缺少字段',
        message: `表单 ${componentName(form, index)} 没有 fields，无法形成有效交互。`,
        path: `${componentPath(form)}.fields`,
        suggestion: '为表单补充字段、标签、类型、必填规则和校验信息。',
      })
    }

    const invalidFields = fields.filter((field) => {
      const item = getRecord(field)
      return !asString(item.field || item.name || item.key) || !asString(item.label)
    })

    if (invalidFields.length) {
      issues.push({
        id: `form.fields.invalid.${index}`,
        level: 'WARNING',
        category: 'RENDERABILITY',
        title: '表单字段定义不完整',
        message: `表单 ${componentName(form, index)} 有 ${invalidFields.length} 个字段缺少 field 或 label。`,
        path: `${componentPath(form)}.fields[*]`,
        suggestion: '每个字段至少需要 field、label、type、required。',
      })
    }

    if (!actions.length) {
      issues.push({
        id: `form.actions.empty.${index}`,
        level: 'WARNING',
        category: 'INTERACTION',
        title: '表单缺少动作按钮',
        message: `表单 ${componentName(form, index)} 没有 actions，用户无法提交或重置。`,
        path: `${componentPath(form)}.actions`,
        suggestion: '至少补充提交和重置动作。',
      })
    }
  }
}

function validateTables(
  components: AnyRecord[],
  mockData: AnyRecord,
  issues: UiContractQualityIssue[],
) {
  const tables = components.filter((component) => normalizeType(component.type) === 'table')

  if (!tables.length) {
    return
  }

  for (const [index, table] of tables.entries()) {
    const columns = asArray(table.columns)
    const dataSource = getRecord(table.dataSource)
    const mockBinding = asString(dataSource.mockBinding || dataSource.bindingKey || table.mockBinding)

    if (!columns.length) {
      issues.push({
        id: `table.columns.empty.${index}`,
        level: 'BLOCKER',
        category: 'RENDERABILITY',
        title: '表格缺少列定义',
        message: `表格 ${componentName(table, index)} 没有 columns，运行时无法展示数据。`,
        path: `${componentPath(table)}.columns`,
        suggestion: '为表格补充 columns，每列包含 field、label、type。',
      })
    }

    if (!mockBinding) {
      issues.push({
        id: `table.mockbinding.missing.${index}`,
        level: 'WARNING',
        category: 'DATA',
        title: '表格缺少 mock 数据绑定',
        message: `表格 ${componentName(table, index)} 没有 dataSource.mockBinding。`,
        path: `${componentPath(table)}.dataSource.mockBinding`,
        suggestion: '将表格绑定到 mockData 中的数组数据，例如 records。',
      })
    } else if (!Array.isArray(mockData[mockBinding])) {
      issues.push({
        id: `table.mockdata.missing.${index}`,
        level: 'WARNING',
        category: 'DATA',
        title: '表格绑定的数据不存在',
        message: `表格 ${componentName(table, index)} 绑定了 ${mockBinding}，但 mockData 中没有对应数组。`,
        path: `mockData.${mockBinding}`,
        suggestion: '在 mockData 中补充同名数组，保证预览打开就有数据。',
      })
    }
  }
}

function validateActions(
  actions: AnyRecord[],
  apiBindings: unknown[],
  issues: UiContractQualityIssue[],
) {
  if (!actions.length) {
    issues.push({
      id: 'actions.empty',
      level: 'INFO',
      category: 'INTERACTION',
      title: '未发现动作定义',
      message: '当前原型没有显式动作。如果页面需要提交、删除、审批、跳转，应补充 actions。',
      path: 'components[*].actions',
    })
    return
  }

  const bindingKeys = new Set(
    apiBindings
      .map((binding) => {
        const item = getRecord(binding)
        return asString(item.bindingKey || item.mockBinding || item.mockAction || item.apiBinding)
      })
      .filter(Boolean),
  )

  let missingId = 0
  let missingLabel = 0
  let missingMockAction = 0
  let missingBinding = 0

  for (const action of actions) {
    if (!asString(action.actionId || action.id || action.key)) missingId += 1
    if (!asString(action.label || action.title)) missingLabel += 1
    if (!asString(action.mockAction || action.mock_action)) missingMockAction += 1

    const apiBinding = asString(action.apiBinding || action.api_binding || action.bindingKey)
    if (!apiBinding) {
      missingBinding += 1
    } else if (bindingKeys.size && !bindingKeys.has(apiBinding)) {
      issues.push({
        id: `action.binding.unmatched.${asString(action.actionId || action.label)}`,
        level: 'WARNING',
        category: 'INTERACTION',
        title: '动作绑定了不存在的接口',
        message: `动作 ${asString(action.label || action.actionId)} 引用了 ${apiBinding}，但 apiBindings 中没有对应项。`,
        path: 'actions[*].apiBinding',
        suggestion: '补充同名 apiBinding，或调整 action.apiBinding。',
      })
    }
  }

  if (missingId || missingLabel || missingMockAction || missingBinding) {
    issues.push({
      id: 'actions.incomplete',
      level: 'WARNING',
      category: 'INTERACTION',
      title: '部分动作定义不完整',
      message: `缺少 actionId：${missingId} 个；缺少 label：${missingLabel} 个；缺少 mockAction：${missingMockAction} 个；缺少 apiBinding：${missingBinding} 个。`,
      path: 'components[*].actions',
      suggestion: '动作至少包含 actionId、label、mockAction、apiBinding、successMessage。',
    })
  } else {
    issues.push(passIssue(
      'actions.pass',
      'INTERACTION',
      '动作定义完整',
      `共识别 ${actions.length} 个动作，均具备基本交互字段。`,
      'actions',
    ))
  }
}

function validateApiBindings(
  apiBindings: unknown[],
  actions: AnyRecord[],
  issues: UiContractQualityIssue[],
) {
  if (!apiBindings.length) {
    issues.push({
      id: 'api.bindings.empty',
      level: 'WARNING',
      category: 'HANDOFF',
      title: '缺少接口绑定',
      message: '没有 apiBindings，开发 Agent 难以把按钮和真实接口衔接起来。',
      path: 'apiBindings',
      suggestion: '为查询、提交、删除、审批等动作补充 method、path、bindingKey。',
    })
    return
  }

  const invalidBindings = apiBindings.filter((binding) => {
    const item = getRecord(binding)
    return !asString(item.method) || !asString(item.path)
  })

  if (invalidBindings.length) {
    issues.push({
      id: 'api.bindings.incomplete',
      level: 'WARNING',
      category: 'HANDOFF',
      title: '接口绑定不完整',
      message: `发现 ${invalidBindings.length} 个 apiBinding 缺少 method 或 path。`,
      path: 'apiBindings[*]',
      suggestion: '每个接口绑定至少包含 method、path、bindingKey。',
    })
  } else {
    issues.push(passIssue(
      'api.bindings.pass',
      'HANDOFF',
      '接口绑定可交付',
      `共识别 ${apiBindings.length} 个接口绑定。`,
      'apiBindings',
    ))
  }

  if (actions.length && apiBindings.length) {
    issues.push(passIssue(
      'handoff.link.pass',
      'HANDOFF',
      '已具备开发交接入口',
      '动作与接口绑定均存在，后续 Development Agent 可继续消费。',
      'actions + apiBindings',
    ))
  }
}

function validateMockData(
  mockData: AnyRecord,
  components: AnyRecord[],
  issues: UiContractQualityIssue[],
) {
  const keys = Object.keys(mockData)

  if (!keys.length) {
    issues.push({
      id: 'mockdata.empty',
      level: 'WARNING',
      category: 'DATA',
      title: '缺少 Mock 数据',
      message: 'mockData 为空，预览页可能无法展示表格、卡片或详情数据。',
      path: 'mockData',
      suggestion: '为主要列表、详情、统计卡片补充示例数据。',
    })
    return
  }

  const tableCount = components.filter((component) => normalizeType(component.type) === 'table').length
  const hasArrayData = Object.values(mockData).some((value) => Array.isArray(value))

  if (tableCount && !hasArrayData) {
    issues.push({
      id: 'mockdata.noarray',
      level: 'WARNING',
      category: 'DATA',
      title: 'Mock 数据缺少数组',
      message: '页面包含表格，但 mockData 中没有数组数据。',
      path: 'mockData',
      suggestion: '至少提供一个数组数据源供 table 渲染。',
    })
  } else {
    issues.push(passIssue(
      'mockdata.pass',
      'DATA',
      'Mock 数据可用',
      `共识别 ${keys.length} 个 Mock 数据键。`,
      'mockData',
    ))
  }
}

function validatePageStates(
  pageStates: AnyRecord,
  issues: UiContractQualityIssue[],
) {
  const stateKeys = new Set(
    Object.keys(pageStates).map((key) => key.toUpperCase()),
  )

  const missingRequired = REQUIRED_PAGE_STATES.filter((key) => !stateKeys.has(key))
  const missingOptional = OPTIONAL_PAGE_STATES.filter((key) => !stateKeys.has(key))

  if (missingRequired.length) {
    issues.push({
      id: 'states.required.missing',
      level: 'WARNING',
      category: 'STATE',
      title: '页面基础状态不完整',
      message: `缺少状态：${missingRequired.join('、')}。`,
      path: 'pageStates / states',
      suggestion: '至少定义 DEFAULT、LOADING、ERROR，便于开发 Agent 生成稳定状态处理。',
    })
  } else {
    issues.push(passIssue(
      'states.required.pass',
      'STATE',
      '基础页面状态完整',
      '已包含 DEFAULT、LOADING、ERROR 等关键状态。',
      'pageStates',
    ))
  }

  if (missingOptional.length) {
    issues.push({
      id: 'states.optional.missing',
      level: 'INFO',
      category: 'STATE',
      title: '可补充扩展状态',
      message: `建议补充：${missingOptional.join('、')}。`,
      path: 'pageStates / states',
      suggestion: '列表、提交类页面建议补充 EMPTY 和 SUCCESS。',
    })
  }
}

function validateFeatureCoverage(
  featureCoverage: unknown[],
  issues: UiContractQualityIssue[],
) {
  if (!featureCoverage.length) {
    issues.push({
      id: 'feature.coverage.empty',
      level: 'INFO',
      category: 'HANDOFF',
      title: '缺少功能覆盖说明',
      message: '未发现 featureCoverage，难以确认当前 UI_CONTRACT 是否覆盖 PAGE 下的 FEATURE。',
      path: 'featureCoverage',
      suggestion: '补充 featureCoverage，记录每个 FEATURE 对应的组件、动作和状态。',
    })
    return
  }

  const uncovered = featureCoverage.filter((item) => {
    const row = getRecord(item)
    const covered = row.covered ?? row.isCovered ?? row.status
    if (typeof covered === 'boolean') return !covered
    return String(covered || '').toUpperCase() === 'UNCOVERED'
  })

  if (uncovered.length) {
    issues.push({
      id: 'feature.coverage.uncovered',
      level: 'WARNING',
      category: 'HANDOFF',
      title: '存在未覆盖功能点',
      message: `featureCoverage 中有 ${uncovered.length} 个功能点未覆盖。`,
      path: 'featureCoverage',
      suggestion: '补齐对应组件、字段、动作或状态。',
    })
  } else {
    issues.push(passIssue(
      'feature.coverage.pass',
      'HANDOFF',
      '功能覆盖已声明',
      `共记录 ${featureCoverage.length} 个功能覆盖项。`,
      'featureCoverage',
    ))
  }
}

function validateDevelopmentRules(
  source: AnyRecord,
  issues: UiContractQualityIssue[],
) {
  const rules = getRecord(source.developmentRules || source.development_rules)

  if (!Object.keys(rules).length) {
    issues.push({
      id: 'development.rules.empty',
      level: 'WARNING',
      category: 'HANDOFF',
      title: '缺少开发约束',
      message: 'developmentRules 为空，后续开发 Agent 可能无法严格继承布局、字段、校验和接口绑定。',
      path: 'developmentRules',
      suggestion: '补充 mustFollowLayout、mustFollowFields、mustFollowApiBindings 等规则。',
    })
    return
  }

  const keyText = Object.keys(rules).join(' ').toLowerCase()
  const hasLayoutRule = keyText.includes('layout')
  const hasFieldRule = keyText.includes('field')
  const hasApiRule = keyText.includes('api') || keyText.includes('binding')

  if (!hasLayoutRule || !hasFieldRule || !hasApiRule) {
    issues.push({
      id: 'development.rules.weak',
      level: 'INFO',
      category: 'HANDOFF',
      title: '开发约束还可以更明确',
      message: '建议显式约束布局、字段、交互流程和接口绑定。',
      path: 'developmentRules',
      suggestion: '补充 mustFollowLayout、mustFollowFields、mustFollowInteractionFlow、mustFollowApiBindings。',
    })
    return
  }

  issues.push(passIssue(
    'development.rules.pass',
    'HANDOFF',
    '开发约束可交付',
    'developmentRules 已包含布局、字段和接口相关约束。',
    'developmentRules',
  ))
}

function buildSummary(
  issues: UiContractQualityIssue[],
  metrics: {
    componentCount: number
    actionCount: number
    fieldCount: number
    bindingCount: number
    mockDataKeyCount: number
  },
): UiContractQualitySummary {
  const blockerCount = issues.filter((item) => item.level === 'BLOCKER').length
  const warningCount = issues.filter((item) => item.level === 'WARNING').length
  const infoCount = issues.filter((item) => item.level === 'INFO').length
  const passCount = issues.filter((item) => item.level === 'PASS').length

  const score = Math.max(
    0,
    Math.min(
      100,
      100
        - blockerCount * 22
        - warningCount * 7
        - infoCount * 2,
    ),
  )

  let grade: UiContractQualitySummary['grade'] = 'EXCELLENT'
  let gradeLabel = '优秀，可进入开发交接'

  if (blockerCount > 0) {
    grade = 'BLOCKED'
    gradeLabel = '阻断，需要先修复'
  } else if (score < 70) {
    grade = 'NEEDS_FIX'
    gradeLabel = '需要修复'
  } else if (score < 82) {
    grade = 'USABLE'
    gradeLabel = '可预览，交接前建议优化'
  } else if (score < 92) {
    grade = 'GOOD'
    gradeLabel = '良好，可交接'
  }

  return {
    score,
    grade,
    gradeLabel,
    canPreview: blockerCount === 0 && metrics.componentCount > 0,
    canHandoff: blockerCount === 0 && score >= 82,
    blockerCount,
    warningCount,
    infoCount,
    passCount,
    componentCount: metrics.componentCount,
    actionCount: metrics.actionCount,
    fieldCount: metrics.fieldCount,
    bindingCount: metrics.bindingCount,
    mockDataKeyCount: metrics.mockDataKeyCount,
    issues: orderIssues(issues),
  }
}

function orderIssues(
  issues: UiContractQualityIssue[],
) {
  const levelOrder: Record<UiContractQualityLevel, number> = {
    BLOCKER: 0,
    WARNING: 1,
    INFO: 2,
    PASS: 3,
  }

  return [...issues].sort((left, right) => {
    return levelOrder[left.level] - levelOrder[right.level]
  })
}

function passIssue(
  id: string,
  category: UiContractQualityCategory,
  title: string,
  message: string,
  path?: string,
): UiContractQualityIssue {
  return {
    id,
    level: 'PASS',
    category,
    title,
    message,
    path,
  }
}

function resolvePages(
  source: AnyRecord,
): AnyRecord[] {
  const pages = asArray(source.pages).map(getRecord).filter((item) => Object.keys(item).length)

  if (pages.length) {
    return pages
  }

  const page = resolveSinglePage(source)
  return page ? [page] : []
}

function resolveSinglePage(
  source: AnyRecord,
): AnyRecord | null {
  const page = getRecord(source.page)

  if (Object.keys(page).length) {
    return page
  }

  return null
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
      for (const child of asArray(getRecord(tab).components)) visit(child)
      for (const child of asArray(getRecord(tab).children)) visit(child)
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

function collectFields(
  components: AnyRecord[],
): AnyRecord[] {
  const result: AnyRecord[] = []

  for (const component of components) {
    for (const field of asArray(component.fields)) {
      result.push(getRecord(field))
    }
  }

  return result.filter((item) => Object.keys(item).length)
}

function resolveMockData(
  source: AnyRecord,
): AnyRecord {
  return getRecord(source.mockData || source.mock_data)
}

function resolvePageStates(
  source: AnyRecord,
  page: AnyRecord | null,
): AnyRecord {
  return getRecord(
    source.pageStates
      || source.page_states
      || page?.pageStates
      || page?.page_states
      || page?.states
      || source.states,
  )
}

function resolveFeatureCoverage(
  source: AnyRecord,
): unknown[] {
  return asArray(source.featureCoverage || source.feature_coverage)
}

function componentName(
  component: AnyRecord,
  index: number,
) {
  return firstText(
    component.title,
    component.label,
    component.componentId,
    component.id,
    `#${index + 1}`,
  )
}

function componentPath(
  component: AnyRecord,
) {
  return asString(component.componentId || component.id || component.key) || 'components[*]'
}

function normalizeType(
  value: unknown,
) {
  return asString(value).trim().toLowerCase()
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
) {
  for (const value of values) {
    const text = asString(value).trim()

    if (text) {
      return text
    }
  }

  return ''
}
