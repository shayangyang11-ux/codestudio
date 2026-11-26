export type RequirementArtifactType =
  | 'MODULE_PRD'
  | 'BUSINESS_FLOW'
  | 'PAGE_SPEC'
  | 'DATA_MODEL'
  | 'API_CONTRACT'
  | 'TEST_SPEC'
  | 'UI_CONTRACT'
  | 'DEV_HANDOFF_PACKAGE'

export type RequirementTaskType =
  | 'GENERATE_MODULE_PRD'
  | 'GENERATE_BUSINESS_FLOW'
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

export const REQUIREMENT_ARTIFACT_DEFINITIONS: RequirementArtifactDefinition[] = [
  {
    artifactType: 'MODULE_PRD',
    taskType: 'GENERATE_MODULE_PRD',
    stepId: 'module-prd',
    title: '模块 PRD',
    shortTitle: 'PRD',
    description: '为当前模块生成结构化产品需求说明，明确业务边界、功能范围、数据要求、异常场景和验收标准。',
    async: true,
    upstreamArtifactTypes: [],
    downstreamArtifactTypes: [
      'BUSINESS_FLOW',
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'UI_CONTRACT',
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
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
      'TEST_SPEC',
      'UI_CONTRACT',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'PAGE_SPEC',
    taskType: 'GENERATE_PAGE_SPEC',
    stepId: 'page-spec',
    title: '页面规范',
    shortTitle: '页面',
    description: '基于模块 PRD 和业务流程生成页面布局、组件、字段、操作、状态和接口绑定说明。',
    async: true,
    upstreamArtifactTypes: ['MODULE_PRD', 'BUSINESS_FLOW'],
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
    description: '基于 PRD、业务流程和页面规范生成实体、字段、关系、索引、约束和状态模型。',
    async: true,
    upstreamArtifactTypes: ['MODULE_PRD', 'BUSINESS_FLOW', 'PAGE_SPEC'],
    downstreamArtifactTypes: [
      'API_CONTRACT',
      'TEST_SPEC',
      'UI_CONTRACT',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'API_CONTRACT',
    taskType: 'GENERATE_API_CONTRACT',
    stepId: 'api-contract',
    title: '接口契约',
    shortTitle: '接口',
    description: '基于页面规范和数据模型生成接口清单、请求响应、错误码、权限要求和联调约束。',
    async: true,
    upstreamArtifactTypes: ['PAGE_SPEC', 'DATA_MODEL'],
    downstreamArtifactTypes: [
      'TEST_SPEC',
      'UI_CONTRACT',
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
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
    ],
    downstreamArtifactTypes: [
      'UI_CONTRACT',
      'DEV_HANDOFF_PACKAGE',
    ],
  },
  {
    artifactType: 'UI_CONTRACT',
    taskType: 'GENERATE_UI_CONTRACT',
    stepId: 'ui-contract',
    title: '交互原型',
    shortTitle: '原型',
    description:
      '基于页面规范、业务流程、数据模型和接口契约生成可预览、可被开发 Agent 复用的 UI 契约。',
    async: true,
    upstreamArtifactTypes: [
      'MODULE_PRD',
      'BUSINESS_FLOW',
      'PAGE_SPEC',
      'DATA_MODEL',
      'API_CONTRACT',
    ],
    downstreamArtifactTypes: ['DEV_HANDOFF_PACKAGE'],
  },
  {
    artifactType: 'DEV_HANDOFF_PACKAGE',
    taskType: 'GENERATE_DEV_HANDOFF_PACKAGE',
    stepId: 'dev-handoff-package',
    title: '开发交接包',
    shortTitle: '交接包',
    description: '汇总 PRD、流程、页面、数据、接口、测试规格和 UI 契约，形成开发 Agent 可直接使用的交付输入包。',
    async: true,
    upstreamArtifactTypes: [
      'MODULE_PRD',
      'BUSINESS_FLOW',
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
    } => definition.async,
  )

export const REQUIREMENT_ARTIFACT_TYPE_ORDER =
  REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => definition.artifactType)

export const ASYNC_REQUIREMENT_TASK_TYPES =
  ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => definition.taskType)

export function isRequirementArtifactType(
  value: string,
): value is RequirementArtifactType {
  return REQUIREMENT_ARTIFACT_DEFINITIONS.some(
    (definition) => definition.artifactType === value,
  )
}

export function isRequirementTaskType(
  value: string,
): value is RequirementTaskType {
  return REQUIREMENT_ARTIFACT_DEFINITIONS.some(
    (definition) => definition.taskType === value,
  )
}

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

export function getRequirementArtifactDefinitionByTaskType(
  taskType: RequirementTaskType,
): RequirementArtifactDefinition {
  const definition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (item) => item.taskType === taskType,
  )

  if (!definition) {
    throw new Error(`Unknown requirement task type: ${taskType}`)
  }

  return definition
}

export function getRequirementArtifactTitle(
  artifactType: RequirementArtifactType,
): string {
  return getRequirementArtifactDefinitionByArtifactType(artifactType).title
}

export function getRequirementArtifactTaskTitle(
  taskType: RequirementTaskType,
): string {
  return getRequirementArtifactDefinitionByTaskType(taskType).title
}

export function getDownstreamArtifactTypes(
  artifactType: RequirementArtifactType,
): RequirementArtifactType[] {
  return getRequirementArtifactDefinitionByArtifactType(artifactType)
    .downstreamArtifactTypes
}

export function getUpstreamArtifactTypes(
  artifactType: RequirementArtifactType,
): RequirementArtifactType[] {
  return getRequirementArtifactDefinitionByArtifactType(artifactType)
    .upstreamArtifactTypes
}

export function formatRequirementArtifactTypes(
  artifactTypes: RequirementArtifactType[],
): string {
  return artifactTypes
    .map((artifactType) => getRequirementArtifactTitle(artifactType))
    .join('、')
}
