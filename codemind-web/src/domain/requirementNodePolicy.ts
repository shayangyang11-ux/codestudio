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

export type RequirementNodeGenerationRole =
  | 'CONTEXT_ONLY'
  | 'DEVELOPMENT_SLICE'
  | 'GROUP_ONLY'
  | 'PAGE_CONTRACT'
  | 'INLINE_DETAIL'
  | 'UNKNOWN'

/**
 * Phase 10-A: node types are not equal to asset-generation units.
 *
 * The tree keeps five levels for expression and traceability, but the default
 * user-facing generation surface is intentionally light:
 * - MODULE is the main development slice entry.
 * - PAGE is the page contract entry.
 * - DOMAIN / SUBMODULE / FEATURE are context/group/inline layers by default.
 *
 * Advanced assets are still available for review/debugging, but they are no
 * longer presented as the default path for normal users.
 */
const CORE_NODE_ARTIFACT_TYPES: Record<Exclude<RequirementNodeType, 'UNKNOWN'>, RequirementArtifactType[]> = {
  DOMAIN: [],
  MODULE: [
    'MODULE_PRD',
    'BUSINESS_FLOW',
    'PAGE_SPEC',
    'DATA_MODEL',
    'API_CONTRACT',
    'TEST_SPEC',
    'DEV_HANDOFF_PACKAGE',
  ],
  SUBMODULE: [],
  PAGE: [
    'PAGE_SPEC',
    'UI_CONTRACT',
  ],
  FEATURE: [],
}

const ADVANCED_NODE_ARTIFACT_TYPES: Record<Exclude<RequirementNodeType, 'UNKNOWN'>, RequirementArtifactType[]> = {
  DOMAIN: [],
  MODULE: [],
  SUBMODULE: [],
  PAGE: [],
  FEATURE: [],
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


export type ModuleRequirementMode =
  | 'DRAFT_MODULE'
  | 'GROUP_MODULE'
  | 'REQUIREMENT_MODULE'
  | 'FUNCTION_POINT'
  | 'PAGE_CONTRACT'
  | 'UNKNOWN'

export function resolveModuleRequirementMode(
  node?: ModuleTreeNode | null,
): ModuleRequirementMode {
  if (!node) {
    return 'UNKNOWN'
  }

  const raw = String(
    node.extra?.requirementMode ||
      node.extra?.requirement_mode ||
      node.extra?.moduleRequirementMode ||
      '',
  ).trim().toUpperCase()

  if (raw === 'GROUP_MODULE') return 'GROUP_MODULE'
  if (raw === 'REQUIREMENT_MODULE') return 'REQUIREMENT_MODULE'
  if (raw === 'FUNCTION_POINT') return 'FUNCTION_POINT'
  if (raw === 'PAGE_CONTRACT') return 'PAGE_CONTRACT'
  if (raw === 'DRAFT_MODULE') return 'DRAFT_MODULE'

  return 'UNKNOWN'
}

export function isMaterializedRequirementModule(
  node?: ModuleTreeNode | null,
): boolean {
  return resolveRequirementNodeType(node) === 'MODULE' &&
    resolveModuleRequirementMode(node) === 'REQUIREMENT_MODULE'
}

// A9.10.14.4.3.15.4.10.4.6_FINAL_MODULE_RULE_CLOSURE
// Final MODULE means the module already has reviewed/generated MODULE_PRD semantics
// and should only be refined through FEATURE -> PAGE children, not by adding more modules.
function readNodeExtraText(
  node: ModuleTreeNode | null | undefined,
  ...keys: string[]
): string {
  const extra = node?.extra || {}

  for (const key of keys) {
    const value = extra[key]
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value).trim()
    }
  }

  return ''
}

function readNodeExtraBool(
  node: ModuleTreeNode | null | undefined,
  ...keys: string[]
): boolean {
  const extra = node?.extra || {}

  for (const key of keys) {
    const value = extra[key]
    if (value === true) return true
    if (typeof value === 'string' && ['true', 'yes', '1'].includes(value.trim().toLowerCase())) {
      return true
    }
  }

  return false
}

export function hasModulePrdArtifact(
  node?: ModuleTreeNode | null,
): boolean {
  return readNodeExtraBool(
    node,
    'hasModulePrd',
    'has_module_prd',
    'modulePrdGenerated',
    'module_prd_generated',
    'modulePrdAvailable',
    'module_prd_available',
  )
}

export function isFinalRequirementModule(
  node?: ModuleTreeNode | null,
): boolean {
  if (resolveRequirementNodeType(node) !== 'MODULE') {
    return false
  }

  if (isMaterializedRequirementModule(node) || hasModulePrdArtifact(node)) {
    return true
  }

  if (readNodeExtraBool(node, 'finalRequirementModule', 'final_requirement_module', 'leafRequirementModule')) {
    return true
  }

  const materializedBy = readNodeExtraText(
    node,
    'moduleMaterializedBy',
    'module_materialized_by',
    'modulePrdTreeCompletionSource',
    'module_prd_tree_completion_source',
  ).toUpperCase()

  return materializedBy.includes('MODULE_PRD') || materializedBy.includes('MANUAL_COMPLETION')
}

export function hasDirectChildModule(
  node?: ModuleTreeNode | null,
): boolean {
  return Boolean((node?.children || []).some((child) => resolveRequirementNodeType(child) === 'MODULE'))
}

export function getAllowedRequirementChildTypes(
  node?: ModuleTreeNode | null,
): RequirementNodeType[] {
  const nodeType = resolveRequirementNodeType(node)

  if (nodeType === 'DOMAIN' || nodeType === 'SUBMODULE' || nodeType === 'MODULE') {
    if (isFinalRequirementModule(node)) {
      return ['PAGE']
    }

    if (hasDirectChildModule(node)) {
      return ['MODULE']
    }

    return ['MODULE', 'PAGE']
  }

  if (nodeType === 'FEATURE') {
    return ['PAGE']
  }

  return []
}





export function canCreateRequirementChild(
  node?: ModuleTreeNode | null,
): boolean {
  return getAllowedRequirementChildTypes(node).length > 0
}

export function getCreateChildActionLabel(
  node?: ModuleTreeNode | null,
): string {
  const allowed = getAllowedRequirementChildTypes(node)

  if (allowed.length === 1 && allowed[0] === 'PAGE') {
    return '新增页面'
  }

  if (allowed.length === 1 && allowed[0] === 'MODULE') {
    return '新增子模块'
  }

  if (allowed.includes('PAGE') && !allowed.includes('MODULE')) {
    return '新增页面'
  }

  return '新增下级节点'
}





export function canMoveRequirementNodeToParent(
  movingNode?: ModuleTreeNode | null,
  parentNode?: ModuleTreeNode | null,
): boolean {
  const movingType = resolveRequirementNodeType(movingNode)

  if (movingType === 'UNKNOWN') {
    return false
  }

  if (!parentNode) {
    return movingType === 'MODULE'
  }

  const allowed = getAllowedRequirementChildTypes(parentNode)
  return allowed.includes(movingType)
}

export function resolveRequirementNodeGenerationRole(
  node?: ModuleTreeNode | null,
): RequirementNodeGenerationRole {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'DOMAIN':
      return 'CONTEXT_ONLY'
    case 'MODULE':
      return 'DEVELOPMENT_SLICE'
    case 'SUBMODULE':
      return 'GROUP_ONLY'
    case 'PAGE':
      return 'PAGE_CONTRACT'
    case 'FEATURE':
      return 'INLINE_DETAIL' // A9.10.14.4.3.15.6.24L0A_FEATURE_LAYER_RETIRE_TO_CAPABILITY: legacy capability binding, hidden from tree UX
    default:
      return 'UNKNOWN'
  }
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
    case 'SUBMODULE':
    case 'MODULE':
      return '模块'
    case 'FEATURE':
      return '能力绑定'
    case 'PAGE':
      return '页面'
    default:
      return '节点'
  }
}

export function getRequirementNodeGenerationRoleLabel(
  role: RequirementNodeGenerationRole,
): string {
  switch (role) {
    case 'CONTEXT_ONLY':
      return '上下文层'
    case 'DEVELOPMENT_SLICE':
      return '开发切片入口'
    case 'GROUP_ONLY':
      return '分组层'
    case 'PAGE_CONTRACT':
      return '页面契约入口'
    case 'INLINE_DETAIL':
      return '内联细节层'
    default:
      return '未识别'
  }
}

export function getRequirementNodeBadgeClass(
  nodeType: RequirementNodeType,
): string {
  switch (nodeType) {
    case 'DOMAIN':
    case 'SUBMODULE':
    case 'MODULE':
      return 'module'
    case 'FEATURE':
      return 'feature'
    case 'PAGE':
      return 'page'
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

  if (nodeType === 'DOMAIN') {
    return '模块默认作为上下文层，不建议在这里生成开发资产。请选择具体模块准备开发资产。'
  }

  if (nodeType === 'SUBMODULE') {
    return '模块默认作为分组层，不建议单独生成开发资产。请选择上层模块整体准备，或选择具体页面生成页面契约。'
  }

  if (nodeType === 'FEATURE') {
    return '能力绑定默认作为父页面的内联细节，不建议单独生成完整资产。请选择所属页面生成页面契约。'
  }

  if (taskType === 'GENERATE_UI_CONTRACT') {
    return '交互原型只能基于 PAGE 页面节点生成。请在模块树中选择具体页面节点。'
  }

  return `当前${getRequirementNodeTypeLabel(nodeType)}节点不适用该资产生成任务。`
}

export function getRequirementArtifactTitleForNode(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  if (artifactType === 'MODULE_PRD') {
    if (nodeType === 'DOMAIN') return '模块概览'
    if (nodeType === 'SUBMODULE') return '场景说明'
    if (nodeType === 'PAGE') return '页面需求摘要'
    if (nodeType === 'FEATURE') return '能力绑定说明'
    return '模块 PRD'
  }

  if (artifactType === 'BUSINESS_FLOW') {
    if (nodeType === 'DOMAIN') return '模块流程总览'
    if (nodeType === 'SUBMODULE') return '场景流程'
    if (nodeType === 'PAGE') return '页面流程摘要'
    if (nodeType === 'FEATURE') return '能力绑定流程'
    return '业务流程'
  }

  if (artifactType === 'PAGE_MAP') {
    return nodeType === 'MODULE' ? '模块页面地图' : '页面地图'
  }

  if (artifactType === 'PAGE_SPEC') {
    if (nodeType === 'DOMAIN') return '模块/页面地图'
    if (nodeType === 'SUBMODULE') return '模块页面范围'
    if (nodeType === 'PAGE') return '页面规范'
    return '页面地图'
  }

  if (artifactType === 'DATA_MODEL') {
    if (nodeType === 'DOMAIN') return '数据主题总览'
    if (nodeType === 'SUBMODULE') return '场景数据约束'
    if (nodeType === 'PAGE') return '页面数据摘要'
    return '数据模型'
  }

  if (artifactType === 'API_CONTRACT') {
    if (nodeType === 'DOMAIN') return '接口域总览'
    if (nodeType === 'SUBMODULE') return '场景接口清单'
    if (nodeType === 'PAGE') return '页面接口摘要'
    if (nodeType === 'FEATURE') return '能力绑定接口'
    return '接口契约'
  }

  if (artifactType === 'TEST_SPEC') {
    if (nodeType === 'DOMAIN') return '模块测试范围'
    if (nodeType === 'SUBMODULE') return '场景测试规格'
    if (nodeType === 'PAGE') return '页面测试摘要'
    if (nodeType === 'FEATURE') return '能力绑定测试'
    return '测试规格'
  }

  if (artifactType === 'UI_CONTRACT') {
    return '交互原型 / UI 契约'
  }

  if (artifactType === 'DEV_HANDOFF_PACKAGE') {
    if (nodeType === 'DOMAIN') return '模块交付说明'
    if (nodeType === 'SUBMODULE') return '场景开发说明'
    if (nodeType === 'PAGE') return '页面开发交接包'
    if (nodeType === 'FEATURE') return '能力绑定开发说明'
    return '开发交接包'
  }

  return artifactType
}

export function getRequirementArtifactDescriptionForNode(
  artifactType: RequirementArtifactType,
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  if (artifactType === 'MODULE_PRD') {
    if (nodeType === 'DOMAIN') return '说明模块目标、边界、模块组成和高层验收标准。'
    if (nodeType === 'SUBMODULE') return '说明当前业务场景的目标、边界、页面组成和关键规则。'
    if (nodeType === 'PAGE') return '说明页面目标、入口、用户任务和验收标准。'
    if (nodeType === 'FEATURE') return '说明能力绑定触发条件、规则边界、成功失败路径和验收标准。'
    return '说明模块边界、角色、核心能力、页面组成、业务规则和验收标准。'
  }

  if (artifactType === 'BUSINESS_FLOW') {
    if (nodeType === 'PAGE') return '描述页面内按钮、提交、状态变化和异常提示。'
    if (nodeType === 'FEATURE') return '描述能力绑定处理步骤、成功失败分支和边界条件。'
    return '描述主流程、分支流程、异常流程、状态流转和关键规则。'
  }

  if (artifactType === 'PAGE_SPEC') {
    if (nodeType === 'PAGE') return '定义页面布局、字段、按钮、校验、状态、接口绑定和验收标准。'
    return '定义页面清单、页面职责、入口出口、跳转关系和信息架构。'
  }

  if (artifactType === 'DATA_MODEL') {
    return '定义实体、字段、关系、约束、状态模型和接口/页面绑定所需数据。'
  }

  if (artifactType === 'API_CONTRACT') {
    return '定义接口清单、请求响应、错误码、权限要求和前后端联调约束。'
  }

  if (artifactType === 'TEST_SPEC') {
    return '定义端到端场景、字段校验、异常路径、测试数据和验收标准。'
  }

  if (artifactType === 'UI_CONTRACT') {
    return '生成可预览并供开发 Agent 使用的页面级 UI 契约。'
  }

  if (artifactType === 'DEV_HANDOFF_PACKAGE') {
    return '汇总需求、页面、数据、接口、测试和 UI 契约，形成 Dev Agent 可读取的开发输入包。'
  }

  return ''
}

export function getRequirementNodeGenerationHint(
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'DOMAIN':
      return '模块默认是上下文层，用来组织模块和统一边界；通常不需要在这里生成开发资产。'
    case 'MODULE':
      return '模块是默认开发准备入口。点击一键准备后，系统会为当前模块生成 PRD、流程、页面地图、数据模型、接口契约、测试规格和开发交接包，并生成 MODULE 级 PAGE_MAP；页面级 PAGE_SPEC / UI_CONTRACT 由 PAGE 节点或 AI 助手显式准备。'
    case 'SUBMODULE':
      return '模块默认是分组层，用来组织页面和流程；通常不单独生成资产，建议选择上层模块一键准备或选择具体页面生成页面契约。'
    case 'PAGE':
      return '页面是页面契约入口。系统会生成页面规范和交互原型，作为 Dev Agent 生成页面代码的视觉与交互依据。页面通常不需要单独生成完整模块交接包。'
    case 'FEATURE':
      return '能力绑定默认是父页面的内联细节，用来表达按钮、校验、跳转和异常场景；通常不单独生成完整资产。'
    default:
      return '请选择模块树节点后查看开发准备建议。'
  }
}

export function getRequirementCoreAssetsButtonText(
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'MODULE':
      return '一键准备模块开发资产'
    case 'PAGE':
      return '生成 / 更新页面资产'
    case 'DOMAIN':
      return '上下文层无需一键生成'
    case 'SUBMODULE':
      return '分组层无需一键生成'
    case 'FEATURE':
      return '能力绑定默认内联到页面'
    default:
      return '生成开发资产'
  }
}

export function getRequirementNodeDefaultActionText(
  node?: ModuleTreeNode | null,
): string {
  const nodeType = resolveRequirementNodeType(node)

  switch (nodeType) {
    case 'DOMAIN':
      return '请选择下方模块进行开发准备。'
    case 'MODULE':
      return '点击一键准备模块开发资产；系统会自动覆盖模块级资产、子页面页面规范、交互原型和最终开发交接包。'
    case 'SUBMODULE':
      return '建议返回上层模块整体准备，或选择具体页面补齐页面契约。'
    case 'PAGE':
      return '点击生成 / 更新页面资产，得到当前页面的页面规范和交互原型。'
    case 'FEATURE':
      return '该能力绑定会进入父页面规格和测试场景；复杂功能建议升级为独立页面或模块开发切片。'
    default:
      return '请先选择模块树节点。'
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

// A9.10.14.4.3.15.6.24L0B_FIX_CREATE_PAGE_POLICY_BACKEND_DIALOG: visible tree creation uses MODULE -> PAGE; FEATURE remains legacy capability metadata.

// A9.10.14.4.3.15.6.24L0B_FINAL_CREATE_PAGE_POLICY_FIX: frontend create policy exposes PAGE, not visible FEATURE.
