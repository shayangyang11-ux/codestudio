import {
  type AsyncRequirementTaskType,
  type RequirementArtifactType,
  type RequirementTaskType,
} from './requirementArtifactDefinitions'

export function buildRequirementArtifactPrompt(
  artifactType: RequirementArtifactType,
  moduleName: string,
): string {
  if (artifactType === 'MODULE_PRD') return buildModulePrdPrompt(moduleName)
  if (artifactType === 'BUSINESS_FLOW') return buildBusinessFlowPrompt(moduleName)
  if (artifactType === 'PAGE_SPEC') return buildPageSpecPrompt(moduleName)
  if (artifactType === 'DATA_MODEL') return buildDataModelPrompt(moduleName)
  if (artifactType === 'API_CONTRACT') return buildApiContractPrompt(moduleName)
  if (artifactType === 'TEST_SPEC') return buildTestSpecPrompt(moduleName)
  if (artifactType === 'UI_CONTRACT') return buildUiContractPrompt(moduleName)

  return buildDevHandoffPackagePrompt(moduleName)
}

export function buildRequirementTaskPrompt(
  taskType: RequirementTaskType,
  moduleName: string,
): string {
  if (taskType === 'GENERATE_MODULE_PRD') return buildModulePrdPrompt(moduleName)
  if (taskType === 'GENERATE_BUSINESS_FLOW') return buildBusinessFlowPrompt(moduleName)
  if (taskType === 'GENERATE_PAGE_SPEC') return buildPageSpecPrompt(moduleName)
  if (taskType === 'GENERATE_DATA_MODEL') return buildDataModelPrompt(moduleName)
  if (taskType === 'GENERATE_API_CONTRACT') return buildApiContractPrompt(moduleName)
  if (taskType === 'GENERATE_TEST_SPEC') return buildTestSpecPrompt(moduleName)
  if (taskType === 'GENERATE_UI_CONTRACT') return buildUiContractPrompt(moduleName)

  return buildDevHandoffPackagePrompt(moduleName)
}

export function buildAsyncRequirementTaskPrompt(
  taskType: AsyncRequirementTaskType,
  moduleName: string,
): string {
  return buildRequirementTaskPrompt(taskType, moduleName)
}

export function buildModulePrdPrompt(
  moduleName: string,
): string {
  return (
    `请为「${moduleName}」模块生成企业级模块 PRD，` +
    '重点关注业务边界、功能需求、数据需求、接口需求、异常场景、权限控制和验收标准。'
  )
}

export function buildBusinessFlowPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块 PRD 生成完整业务流程，` +
    '重点说明主流程、分支流程、异常流程、状态流转、数据交互、接口交互和权限控制点。'
  )
}

export function buildPageSpecPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块 PRD 和业务流程生成页面规范，` +
    '重点说明页面布局、组件、操作、表单字段、表格列、接口绑定、权限控制、异常状态和验收标准。'
  )
}

export function buildDataModelPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块 PRD、业务流程和页面规范生成数据模型，` +
    '重点说明实体、字段、表名、关系、索引、约束、状态模型、页面数据绑定和 API 数据使用提示。'
  )
}

export function buildApiContractPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块 PRD、业务流程、页面规范和数据模型生成核心接口契约，` +
    '重点说明接口清单、请求参数、请求体、响应字段、错误码、权限要求、事务边界、数据映射和测试关注点。'
  )
}

export function buildTestSpecPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块 PRD、业务流程、页面规范、数据模型和接口契约生成测试规格，` +
    '重点覆盖功能测试、接口测试、流程测试、权限测试、数据校验、异常场景、测试数据和自动化测试建议。'
  )
}

export function buildUiContractPrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块的 PRD、业务流程、页面规范、数据模型和接口契约生成交互式 UI 契约。` +
    '该 UI 契约不是一次性 Mock 页面，而是当前 Mock Preview Runtime 和后续开发 Agent 真实页面生成共同使用的页面蓝图。' +
    '必须覆盖页面路由、布局、组件树、字段、校验规则、按钮动作、Mock 数据、真实 API 绑定点、权限点、加载状态、空状态、错误状态、成功状态和开发一致性规则。' +
    '后续开发 Agent 只能将 Mock 数据源和 Mock 动作替换成真实 API 与真实 Service，不得擅自改变页面布局、字段、按钮、校验规则和交互流程。'
  )
}

export function buildDevHandoffPackagePrompt(
  moduleName: string,
): string {
  return (
    `请基于「${moduleName}」模块的 PRD、业务流程、页面规范、数据模型、接口契约、测试规格和 UI 契约生成开发交接包，` +
    '重点说明开发目标、模块边界、前端实现、后端实现、接口实现、数据实现、权限安全、UI 契约落地要求、文件生成建议、开发顺序、测试验收和风险注意事项。'
  )
}