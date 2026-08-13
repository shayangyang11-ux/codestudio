export type RequirementArtifactType =
  | 'MODULE_PRD'
  | 'BUSINESS_FLOW'
  | 'PAGE_MAP'
  | 'PAGE_SPEC'
  | 'DATA_MODEL'
  | 'API_CONTRACT'
  | 'TEST_SPEC'
  | 'UI_CONTRACT'
  | 'DEV_HANDOFF_PACKAGE'

export type RequirementTaskType =
  | 'GENERATE_MODULE_PRD'
  | 'GENERATE_BUSINESS_FLOW'
  | 'GENERATE_PAGE_MAP'
  | 'GENERATE_PAGE_SPEC'
  | 'GENERATE_DATA_MODEL'
  | 'GENERATE_API_CONTRACT'
  | 'GENERATE_TEST_SPEC'
  | 'GENERATE_UI_CONTRACT'
  | 'GENERATE_DEV_HANDOFF_PACKAGE'

export type AsyncRequirementTaskType = RequirementTaskType

export type AssistantStepId =
  | 'module-prd'
  | 'business-flow'
  | 'page-map'
  | 'page-spec'
  | 'data-model'
  | 'api-contract'
  | 'test-spec'
  | 'ui-contract'
  | 'dev-handoff-package'

export interface RequirementArtifactDefinition {
  artifactType: RequirementArtifactType
  taskType: RequirementTaskType
  stepId: AssistantStepId
  title: string
  shortTitle: string
  description: string
  async: boolean
  upstreamArtifactTypes: RequirementArtifactType[]
  downstreamArtifactTypes: RequirementArtifactType[]
}

export const REQUIREMENT_ARTIFACT_TYPES: RequirementArtifactType[] = [
  'MODULE_PRD',
  'BUSINESS_FLOW',
  'PAGE_MAP',
  'PAGE_SPEC',
  'DATA_MODEL',
  'API_CONTRACT',
  'TEST_SPEC',
  'UI_CONTRACT',
  'DEV_HANDOFF_PACKAGE',
]

export const REQUIREMENT_TASK_TYPES: RequirementTaskType[] = [
  'GENERATE_MODULE_PRD',
  'GENERATE_BUSINESS_FLOW',
  'GENERATE_PAGE_MAP',
  'GENERATE_PAGE_SPEC',
  'GENERATE_DATA_MODEL',
  'GENERATE_API_CONTRACT',
  'GENERATE_TEST_SPEC',
  'GENERATE_UI_CONTRACT',
  'GENERATE_DEV_HANDOFF_PACKAGE',
]

export const REQUIREMENT_ARTIFACT_DEFINITIONS: RequirementArtifactDefinition[] = [
  {
    artifactType: 'MODULE_PRD',
    taskType: 'GENERATE_MODULE_PRD',
    stepId: 'module-prd',
    title: '模块 PRD',
    shortTitle: 'PRD',
    description: '为当前 MODULE 生成结构化产品需求说明，明确业务边界、功能范围、规则、异常场景和验收标准。',
    async: true,
    upstreamArtifactTypes: [],
    downstreamArtifactTypes: [
      'BUSINESS_FLOW',
      'PAGE_MAP',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'BUSINESS_FLOW',
    taskType: 'GENERATE_BUSINESS_FLOW',
    stepId: 'business-flow',
    title: '业务流程',
    shortTitle: '流程',
    description: '基于模块 PRD 生成主流程、分支流程、异常流程、状态流转和关键业务规则。',
    async: true,
    upstreamArtifactTypes: ['MODULE_PRD'],
    downstreamArtifactTypes: [
      'PAGE_MAP',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'PAGE_MAP',
    taskType: 'GENERATE_PAGE_MAP',
    stepId: 'page-map',
    title: '页面地图',
    shortTitle: '地图',
    description: 'MODULE 级页面地图，定义页面清单、页面职责、入口出口、路由建议、页面跳转关系和跨模块导航引用；不展开单页字段与按钮细节。',
    async: true,
    upstreamArtifactTypes: ['MODULE_PRD', 'BUSINESS_FLOW'],
    downstreamArtifactTypes: [
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'PAGE_SPEC',
    taskType: 'GENERATE_PAGE_SPEC',
    stepId: 'page-spec',
    title: '页面规范',
    shortTitle: '页面',
    description: 'PAGE 级页面规范，定义单页布局、组件、字段、操作、状态、接口绑定和与 FEATURE 的关系。',
    async: true,
    upstreamArtifactTypes: ['PAGE_MAP'],
    downstreamArtifactTypes: [
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'UI_CONTRACT',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'DATA_MODEL',
    taskType: 'GENERATE_DATA_MODEL',
    stepId: 'data-model',
    title: '数据模型',
    shortTitle: '数据',
    description: '基于 PRD、业务流程、页面地图和页面规范生成实体、字段、关系、索引、约束和状态模型。',
    async: true,
    upstreamArtifactTypes: ['MODULE_PRD', 'BUSINESS_FLOW', 'PAGE_MAP', 'PAGE_SPEC'],
    downstreamArtifactTypes: [
      'API_CONTRACT',
      'TEST_SPEC',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'API_CONTRACT',
    taskType: 'GENERATE_API_CONTRACT',
    stepId: 'api-contract',
    title: '接口契约',
    shortTitle: '接口',
    description: '基于页面地图、页面规范和数据模型生成接口清单、请求响应、错误码、权限要求和联调约束。',
    async: true,
    upstreamArtifactTypes: ['PAGE_MAP', 'PAGE_SPEC', 'DATA_MODEL'],
    downstreamArtifactTypes: [
      'TEST_SPEC',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'TEST_SPEC',
    taskType: 'GENERATE_TEST_SPEC',
    stepId: 'test-spec',
    title: '测试规格',
    shortTitle: '测试',
    description: '基于页面、数据和接口资产生成测试用例、验收标准、测试数据和自动化测试建议。',
    async: true,
    upstreamArtifactTypes: [
      'PAGE_MAP',
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
    ],
    downstreamArtifactTypes: ['DEV_HANDOFF_PACKAGE'],
  },
  {
    artifactType: 'UI_CONTRACT',
    taskType: 'GENERATE_UI_CONTRACT',
    stepId: 'ui-contract',
    title: '交互原型',
    shortTitle: '原型',
    description:
      'PAGE 级 UI 契约。以当前 PAGE_SPEC 为硬前置，读取所属 MODULE 的 PAGE_MAP 和项目页面索引补齐跳转；跨 MODULE 跳转可先占位但必须标记状态。',
    async: true,
    upstreamArtifactTypes: ['PAGE_SPEC', 'PAGE_MAP'],
    downstreamArtifactTypes: ['DEV_HANDOFF_PACKAGE'],
  },
  {
    artifactType: 'DEV_HANDOFF_PACKAGE',
    taskType: 'GENERATE_DEV_HANDOFF_PACKAGE',
    stepId: 'dev-handoff-package',
    title: '开发交接包',
    shortTitle: '交接包',
    description: '汇总模块核心资产和已确认的页面级资产，生成给开发 Agent 使用的交接包。',
    async: true,
    upstreamArtifactTypes: [
      'MODULE_PRD',
      'BUSINESS_FLOW',
      'PAGE_MAP',
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'UI_CONTRACT',
    ],
    downstreamArtifactTypes: [],
  },
]


export const ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS =
  REQUIREMENT_ARTIFACT_DEFINITIONS.filter(
    (definition): definition is RequirementArtifactDefinition & {
      taskType: AsyncRequirementTaskType
    } => Boolean(definition.async),
  )


export const ASYNC_REQUIREMENT_TASK_TYPES =
  ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => definition.taskType)

export function isRequirementArtifactType(
  value: unknown,
): value is RequirementArtifactType {
  return typeof value === 'string'
    && REQUIREMENT_ARTIFACT_TYPES.includes(value as RequirementArtifactType)
}

export function isRequirementTaskType(
  value: unknown,
): value is RequirementTaskType {
  return typeof value === 'string'
    && REQUIREMENT_TASK_TYPES.includes(value as RequirementTaskType)
}

export function getRequirementArtifactDefinition(
  artifactType: RequirementArtifactType | string | null | undefined,
): RequirementArtifactDefinition | null {
  if (!artifactType) return null

  return REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.artifactType === artifactType,
  ) || null
}

export const getRequirementArtifactDefinitionByType = getRequirementArtifactDefinition

export function getRequirementArtifactDefinitionByTaskType(
  taskType: RequirementTaskType | string | null | undefined,
): RequirementArtifactDefinition | null {
  if (!taskType) return null

  return REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.taskType === taskType,
  ) || null
}

export function getRequirementArtifactTitle(
  artifactType: RequirementArtifactType | string | null | undefined,
): string {
  return getRequirementArtifactDefinition(artifactType)?.title || String(artifactType || '需求资产')
}

export const getRequirementArtifactTitleByType = getRequirementArtifactTitle

export function getRequirementArtifactShortTitle(
  artifactType: RequirementArtifactType | string | null | undefined,
): string {
  return getRequirementArtifactDefinition(artifactType)?.shortTitle || String(artifactType || '资产')
}

export function getRequirementTaskTypeForArtifactType(
  artifactType: RequirementArtifactType | string | null | undefined,
): RequirementTaskType | null {
  const definition = getRequirementArtifactDefinition(artifactType)
  return definition?.taskType || null
}

export function getRequirementArtifactTypeForTaskType(
  taskType: RequirementTaskType | string | null | undefined,
): RequirementArtifactType | null {
  const definition = getRequirementArtifactDefinitionByTaskType(taskType)
  return definition?.artifactType || null
}

export function getUpstreamArtifactTypes(
  artifactType: RequirementArtifactType | string | null | undefined,
): RequirementArtifactType[] {
  return [...(getRequirementArtifactDefinition(artifactType)?.upstreamArtifactTypes || [])]
}

export function getDownstreamArtifactTypes(
  artifactType: RequirementArtifactType | string | null | undefined,
): RequirementArtifactType[] {
  return [...(getRequirementArtifactDefinition(artifactType)?.downstreamArtifactTypes || [])]
}

export const REQUIREMENT_ARTIFACT_TYPE_ORDER =
  REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => definition.artifactType)

export function isAsyncRequirementTaskType(
  value: string,
): value is AsyncRequirementTaskType {
  return ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.some(
    (definition) => definition.taskType === value,
  )
}

export function getRequirementArtifactDefinitionByArtifactType(
  artifactType: RequirementArtifactType,
): RequirementArtifactDefinition {
  const definition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (item) => item.artifactType === artifactType,
  )

  if (!definition) {
    throw new Error(`Unknown requirement artifact type: ${artifactType}`)
  }

  return definition
}

export function getRequirementArtifactTaskTitle(
  taskType: RequirementTaskType,
): string {
  return getRequirementArtifactDefinitionByTaskType(taskType)?.title || taskType
}

export function formatRequirementArtifactTypes(
  artifactTypes: RequirementArtifactType[],
): string {
  return artifactTypes
    .map((artifactType) => getRequirementArtifactTitle(artifactType))
    .join('、')
}
