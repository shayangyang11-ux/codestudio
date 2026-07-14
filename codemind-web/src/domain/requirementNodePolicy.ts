import type { ModuleTreeNode } from '../types/codemind'
import {
  REQUIREMENT_ARTIFACT_DEFINITIONS,
  getDownstreamArtifactTypes,
  type AsyncRequirementTaskType,
  type RequirementArtifactDefinition,
  type RequirementArtifactType,
} from './requirementArtifactDefinitions'

export type RequirementNodeType =
  | 'DOMAIN'
  | 'MODULE'
  | 'SUBMODULE'
  | 'PAGE'
  | 'FEATURE'
  | 'UNKNOWN'

export type RequirementArtifactImportance = 'CORE' | 'ADVANCED' | 'UNAVAILABLE'

const CORE_NODE_ARTIFACT_TYPES: Record<Exclude<RequirementNodeType, 'UNKNOWN'>, RequirementArtifactType[]> = {
  DOMAIN: [
    'MODULE_PRD',
    'BUSINESS_FLOW',
    'PAGE_SPEC',
  ],
  MODULE: [
    'MODULE_PRD',
    'BUSINESS_FLOW',
    'PAGE_SPEC',
    'API_CONTRACT',
  ],
  SUBMODULE: [
    'MODULE_PRD',
    'BUSINESS_FLOW',
    'PAGE_SPEC',
  ],
  PAGE: [
    'MODULE_PRD',
    'PAGE_SPEC',
    'API_CONTRACT',
    'UI_CONTRACT',
  ],
  FEATURE: [
    'MODULE_PRD',
    'API_CONTRACT',
    'TEST_SPEC',
  ],
}

const ADVANCED_NODE_ARTIFACT_TYPES: Record<Exclude<RequirementNodeType, 'UNKNOWN'>, RequirementArtifactType[]> = {
  DOMAIN: [
    'DATA_MODEL',
    'API_CONTRACT',
    'TEST_SPEC',
    'DEV_HANDOFF_PACKAGE',
  ],
  MODULE: [
    'DATA_MODEL',
    'TEST_SPEC',
    'DEV_HANDOFF_PACKAGE',
  ],
  SUBMODULE: [
    'API_CONTRACT',
    'TEST_SPEC',
    'DEV_HANDOFF_PACKAGE',
  ],
  PAGE: [
    'BUSINESS_FLOW',
    'DATA_MODEL',
    'TEST_SPEC',
    'DEV_HANDOFF_PACKAGE',
  ],
  FEATURE: [
    'BUSINESS_FLOW',
    'DEV_HANDOFF_PACKAGE',
  ],
}

export function resolveRequirementNodeType(
  node?: ModuleTreeNode | null,
): RequirementNodeType {
  if (!node) {
    return 'UNKNOWN'
  }

  const rawType = String(
    node.moduleType ||
      node.type ||
      node.extra?.moduleType ||
      node.extra?.type ||
      node.extra?.nodeType ||
      '',
  )
    .trim()
    .toUpperCase()

  if (
    rawType === 'DOMAIN' ||
    rawType === 'DOMAIN_MODULE' ||
    rawType === 'BUSINESS_DOMAIN'
  ) {
    return 'DOMAIN'
  }

  if (
    rawType === 'MODULE' ||
    rawType === 'BUSINESS_MODULE' ||
    rawType === 'FEATURE_MODULE' ||
    rawType === 'INTEGRATION_MODULE' ||
    rawType === 'INFRA_MODULE'
  ) {
    return 'MODULE'
  }

  if (
    rawType === 'SUBMODULE' ||
    rawType === 'SUB_MODULE' ||
    rawType === 'SCENE' ||
    rawType === 'BUSINESS_SCENE'
  ) {
    return 'SUBMODULE'
  }

  if (
    rawType === 'PAGE' ||
    rawType === 'PAGE_MODULE' ||
    rawType === 'SCREEN' ||
    rawType === 'VIEW'
  ) {
    return 'PAGE'
  }

  if (
    rawType === 'FEATURE' ||
    rawType === 'FUNCTION' ||
    rawType === 'ACTION' ||
    rawType === 'OPERATION'
  ) {
    return 'FEATURE'
  }

  return 'MODULE'
}

export function isPageNode(node?: ModuleTreeNode | null): boolean {
  return resolveRequirementNodeType(node) === 'PAGE'
}

export function isFeatureNode(node?: ModuleTreeNode | null): boolean {
  return resolveRequirementNodeType(node) === 'FEATURE'
}

export function getRequirementNodeTypeLabel(
  nodeType: RequirementNodeType,
): string {
  switch (nodeType) {
    case 'DOMAIN':
      return '业务域'
    case 'MODULE':
      return '模块'
    case 'SUBMODULE':
      return '子模块 / 业务场景'
    case 'PAGE':
      return '页面'
    case 'FEATURE':
      return '功能点'
    default:
      return '未知'
  }
}

export function getRequirementNodeBadgeClass(
  nodeType: RequirementNodeType,
): string {
  switch (nodeType) {
    case 'DOMAIN':
      return 'domain'
    case 'MODULE':
      return 'module'
    case 'SUBMODULE':
      return 'submodule'
    case 'PAGE':
      return 'page'
    case 'FEATURE':
      return 'feature'
    default:
      return 'unknown'
  }
}

export function getCoreRequirementArtifactTypesForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactType[] {
  const nodeType = resolveRequirementNodeType(node)

  if (nodeType === 'UNKNOWN') {
    return []
  }

  return CORE_NODE_ARTIFACT_TYPES[nodeType]
}

export function getAdvancedRequirementArtifactTypesForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactType[] {
  const nodeType = resolveRequirementNodeType(node)

  if (nodeType === 'UNKNOWN') {
    return []
  }

  return ADVANCED_NODE_ARTIFACT_TYPES[nodeType]
}

export function getApplicableRequirementArtifactTypesForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactType[] {
  return [
    ...getCoreRequirementArtifactTypesForNode(node),
    ...getAdvancedRequirementArtifactTypesForNode(node),
  ]
}

export function getRequirementArtifactImportanceForNode(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): RequirementArtifactImportance {
  if (getCoreRequirementArtifactTypesForNode(node).includes(artifactType)) {
    return 'CORE'
  }

  if (getAdvancedRequirementArtifactTypesForNode(node).includes(artifactType)) {
    return 'ADVANCED'
  }

  return 'UNAVAILABLE'
}

export function getApplicableRequirementArtifactDefinitionsForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactDefinition[] {
  const artifactTypes = getApplicableRequirementArtifactTypesForNode(node)

  return REQUIREMENT_ARTIFACT_DEFINITIONS.filter((definition) =>
    artifactTypes.includes(definition.artifactType),
  )
}

export function getCoreRequirementArtifactDefinitionsForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactDefinition[] {
  const artifactTypes = getCoreRequirementArtifactTypesForNode(node)

  return REQUIREMENT_ARTIFACT_DEFINITIONS.filter((definition) =>
    artifactTypes.includes(definition.artifactType),
  )
}

export function getAdvancedRequirementArtifactDefinitionsForNode(
  node?: ModuleTreeNode | null,
): RequirementArtifactDefinition[] {
  const artifactTypes = getAdvancedRequirementArtifactTypesForNode(node)

  return REQUIREMENT_ARTIFACT_DEFINITIONS.filter((definition) =>
    artifactTypes.includes(definition.artifactType),
  )
}

export function canGenerateTaskForNode(
  taskType: AsyncRequirementTaskType | string,
  node?: ModuleTreeNode | null,
): boolean {
  const definition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (item) => item.taskType === taskType,
  )

  if (!definition) {
    return false
  }

  return getApplicableRequirementArtifactTypesForNode(node).includes(
    definition.artifactType,
  )
}

export function getRequirementTaskUnavailableReason(
  taskType: AsyncRequirementTaskType | string,
  node?: ModuleTreeNode | null,
): string | null {
  if (canGenerateTaskForNode(taskType, node)) {
    return null
  }

  const nodeType = resolveRequirementNodeType(node)

  if (nodeType === 'UNKNOWN') {
    return '请先选择一个模块树节点。'
  }

  if (taskType === 'GENERATE_UI_CONTRACT') {
    if (nodeType === 'FEATURE') {
      return '当前选中节点是页面功能点，请在其所属 PAGE 页面节点生成交互原型。'
    }

    return '交互原型只能基于 PAGE 页面节点生成。请在模块树中选择具体页面节点。'
  }

  if (nodeType === 'FEATURE' && taskType === 'GENERATE_PAGE_SPEC') {
    return '功能点不生成完整页面规范，请在所属 PAGE 页面节点生成页面规范。'
  }

  if (nodeType === 'FEATURE' && taskType === 'GENERATE_DATA_MODEL') {
    return '功能点不单独生成页面数据模型，请在所属 PAGE 页面节点生成页面级数据模型。'
  }

  return `当前${getRequirementNodeTypeLabel(nodeType)}节点不适用该资产生成任务。`
}

export function getRequirementArtifactTitleForNode(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  if (artifactType === 'MODULE_PRD') {
    if (nodeType === 'DOMAIN') return '业务域概览'
    if (nodeType === 'SUBMODULE') return '场景需求说明'
    if (nodeType === 'PAGE') return '页面需求'
    if (nodeType === 'FEATURE') return '功能点需求'
    return '模块 PRD'
  }

  if (artifactType === 'BUSINESS_FLOW') {
    if (nodeType === 'DOMAIN') return '业务域流程总览'
    if (nodeType === 'SUBMODULE') return '场景业务流程'
    if (nodeType === 'PAGE') return '页面交互流程'
    if (nodeType === 'FEATURE') return '功能点流程'
    return '模块业务流程'
  }

  if (artifactType === 'PAGE_SPEC') {
    if (nodeType === 'DOMAIN') return '模块/页面地图'
    if (nodeType === 'SUBMODULE') return '场景页面清单'
    if (nodeType === 'PAGE') return '页面规范'
    return '页面地图'
  }

  if (artifactType === 'DATA_MODEL') {
    if (nodeType === 'DOMAIN') return '数据主题总览'
    if (nodeType === 'SUBMODULE') return '场景数据约束'
    if (nodeType === 'PAGE') return '页面数据模型'
    return '模块数据模型'
  }

  if (artifactType === 'API_CONTRACT') {
    if (nodeType === 'DOMAIN') return '接口域总览'
    if (nodeType === 'SUBMODULE') return '场景接口清单'
    if (nodeType === 'PAGE') return '页面接口契约'
    if (nodeType === 'FEATURE') return '功能点接口'
    return '模块接口规划'
  }

  if (artifactType === 'TEST_SPEC') {
    if (nodeType === 'DOMAIN') return '业务域测试范围'
    if (nodeType === 'SUBMODULE') return '场景测试规格'
    if (nodeType === 'PAGE') return '页面测试规格'
    if (nodeType === 'FEATURE') return '功能点测试'
    return '模块测试规格'
  }

  if (artifactType === 'UI_CONTRACT') {
    return '交互原型'
  }

  if (artifactType === 'DEV_HANDOFF_PACKAGE') {
    if (nodeType === 'DOMAIN') return '业务域交付说明'
    if (nodeType === 'SUBMODULE') return '场景开发说明'
    if (nodeType === 'PAGE') return '页面开发交接包'
    if (nodeType === 'FEATURE') return '功能点开发说明'
    return '模块开发交接包'
  }

  return artifactType
}

export function getRequirementArtifactDescriptionForNode(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  if (artifactType === 'MODULE_PRD') {
    if (nodeType === 'DOMAIN') return '说明业务域目标、边界、模块组成和高层验收标准，不展开下层页面细节。'
    if (nodeType === 'SUBMODULE') return '说明当前业务场景的目标、边界、页面组成和关键规则。'
    if (nodeType === 'PAGE') return '说明页面目标、入口、用户任务、关键字段、操作和验收标准。'
    if (nodeType === 'FEATURE') return '说明功能点触发条件、输入输出、规则边界、成功失败路径和验收标准。'
    return '说明模块边界、角色、核心能力、页面组成、业务规则和验收标准。'
  }

  if (artifactType === 'BUSINESS_FLOW') {
    if (nodeType === 'DOMAIN') return '概览跨模块主流程、关键分支和模块协作关系，不展开页面内操作。'
    if (nodeType === 'SUBMODULE') return '描述场景内页面流转、规则判断、异常分支和上下游衔接。'
    if (nodeType === 'PAGE') return '描述页面内按钮、弹窗、提交、状态变化和异常提示。'
    if (nodeType === 'FEATURE') return '描述功能点处理步骤、成功失败分支、权限拦截和边界条件。'
    return '描述模块主流程、分支流程、异常流程、状态流转和关键规则。'
  }

  if (artifactType === 'PAGE_SPEC') {
    if (nodeType === 'DOMAIN') return '展示业务域下模块、页面、入口和跳转关系，不写单页字段。'
    if (nodeType === 'SUBMODULE') return '列出场景涉及页面、页面职责、入口出口和页面协作关系。'
    if (nodeType === 'PAGE') return '定义页面布局、字段、表格、按钮、弹窗、校验、空状态和错误状态。'
    return '展示模块页面清单、页面关系、跳转路径和页面职责。'
  }

  if (artifactType === 'DATA_MODEL') {
    if (nodeType === 'DOMAIN') return '概览业务域核心数据主题、主数据、关键关系和跨模块数据边界。'
    if (nodeType === 'SUBMODULE') return '补充场景涉及的数据对象、关键字段、状态约束和上下游数据关系。'
    if (nodeType === 'PAGE') return '定义页面 ViewModel、查询模型、表单模型、展示模型和接口数据绑定。'
    return '定义模块实体、字段、关系、索引、状态模型、约束和数据生命周期。'
  }

  if (artifactType === 'API_CONTRACT') {
    if (nodeType === 'DOMAIN') return '概览业务域接口边界、模块间接口关系、权限策略和联调约束。'
    if (nodeType === 'SUBMODULE') return '列出该场景涉及的查询、提交、校验、审批和状态变更接口。'
    if (nodeType === 'PAGE') return '定义页面绑定接口、请求响应、错误码、权限和前端绑定关系。'
    if (nodeType === 'FEATURE') return '定义功能点动作接口、参数、响应、错误码、权限点和幂等性。'
    return '规划模块接口分组、接口清单、核心对象、权限边界和上下游关系。'
  }

  if (artifactType === 'TEST_SPEC') {
    if (nodeType === 'DOMAIN') return '说明跨模块端到端测试范围、关键风险、回归范围和验收边界。'
    if (nodeType === 'SUBMODULE') return '覆盖场景主路径、分支路径、异常路径、权限和上下游衔接。'
    if (nodeType === 'PAGE') return '覆盖字段校验、页面交互、权限、接口、状态、弹窗和异常状态。'
    if (nodeType === 'FEATURE') return '覆盖功能点正常路径、异常路径、权限、边界条件和接口错误。'
    return '覆盖模块端到端流程、模块边界、权限、异常、数据一致性和验收标准。'
  }

  if (artifactType === 'UI_CONTRACT') {
    return '生成可 Mock 预览并供开发 Agent 使用的页面级 UI 契约。'
  }

  if (artifactType === 'DEV_HANDOFF_PACKAGE') {
    if (nodeType === 'DOMAIN') return '汇总业务域边界、模块组成、关键流程、跨模块约束和开发拆分建议。'
    if (nodeType === 'SUBMODULE') return '汇总场景边界、页面协作、规则约束、接口依赖、测试重点和开发风险。'
    if (nodeType === 'PAGE') return '汇总页面组件、接口、状态、校验、测试和 UI_CONTRACT 落地要求。'
    if (nodeType === 'FEATURE') return '汇总功能边界、接口动作、状态影响、测试点、权限点和开发风险。'
    return '汇总模块约束、页面清单、接口规划、数据模型、测试重点和开发顺序。'
  }

  return ''
}

export function getRequirementNodeGenerationHint(
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'DOMAIN':
      return '业务域层只做业务边界、模块组成、跨模块流程和页面地图的汇总，不展开具体页面字段、按钮和接口细节。'
    case 'MODULE':
      return '模块层重点说明模块边界、业务能力、页面地图和接口规划；下层页面和功能点的详细字段、交互、接口细节留给 PAGE / FEATURE 节点。'
    case 'SUBMODULE':
      return '子模块/场景层重点说明场景边界、场景流程和页面清单；具体页面规范和功能点逻辑留给下层节点。'
    case 'PAGE':
      return '页面层是前端开发 Agent 的主要输入，重点准备页面规范、接口契约和 UI_CONTRACT；功能点细节可由 FEATURE 节点补充。'
    case 'FEATURE':
      return '功能点层只描述页面内某个动作或能力，重点准备需求、接口和测试，不生成完整页面规范或交互原型。'
    default:
      return '请选择模块树节点后生成对应层级的核心资产。'
  }
}

export function getRequirementCoreAssetsButtonText(
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'DOMAIN':
      return '生成业务域核心资产'
    case 'MODULE':
      return '生成模块核心资产'
    case 'SUBMODULE':
      return '生成场景核心资产'
    case 'PAGE':
      return '生成页面开发资产'
    case 'FEATURE':
      return '生成功能点开发资产'
    default:
      return '生成核心资产'
  }
}

export function getNodeAwareDownstreamArtifactTypes(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): RequirementArtifactType[] {
  const applicableTypes = getApplicableRequirementArtifactTypesForNode(node)

  return getDownstreamArtifactTypes(artifactType).filter((item) =>
    applicableTypes.includes(item),
  )
}