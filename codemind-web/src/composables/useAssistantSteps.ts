import { ref } from 'vue'
import type { AssistantStep } from '../types/codemind'

export function useAssistantSteps() {
  const assistantSteps = ref<AssistantStep[]>([
    {
      id: 'receive',
      title: '接收需求',
      description: '等待用户输入需求或选择模块。',
      status: 'pending',
    },
    {
      id: 'module-tree',
      title: '生成模块树',
      description: '调用需求 Agent 生成项目模块结构。',
      status: 'pending',
    },
    {
      id: 'module-prd',
      title: '生成模块 PRD',
      description: '为当前选中模块生成结构化 PRD。',
      status: 'pending',
    },
    {
      id: 'business-flow',
      title: '生成业务流程',
      description: '基于模块 PRD 生成主流程、分支流程、异常流程和状态流转。',
      status: 'pending',
    },
    {
      id: 'page-spec',
      title: '生成页面规范',
      description: '基于模块 PRD 和业务流程生成页面布局、组件、操作和接口绑定。',
      status: 'pending',
    },
    {
      id: 'data-model',
      title: '生成数据模型',
      description: '基于 PRD、业务流程和页面规范生成实体、字段、关系、索引和约束。',
      status: 'pending',
    },
    {
      id: 'api-contract',
      title: '生成接口契约',
      description: '基于数据模型和页面操作生成接口清单、请求响应、错误码和权限要求。',
      status: 'pending',
    },
    {
      id: 'test-spec',
      title: '生成测试规格',
      description: '基于 PRD、流程、页面、数据模型和接口契约生成测试用例与验收标准。',
      status: 'pending',
    },
    {
      id: 'dev-handoff-package',
      title: '生成开发交接包',
      description: '汇总需求、流程、页面、数据、接口和测试资产，形成开发 Agent 输入包。',
      status: 'pending',
    },
    {
      id: 'artifact',
      title: '工程资产入库',
      description: '保存 Agent Task、Artifact Version、Project Module。',
      status: 'pending',
    },
  ])

  function resetSteps() {
    assistantSteps.value = assistantSteps.value.map((step) => ({
      ...step,
      status: 'pending',
    }))
  }

  function setStepStatus(
    stepId: string,
    status: AssistantStep['status'],
  ) {
    assistantSteps.value = assistantSteps.value.map((step) => {
      if (step.id !== stepId) {
        return step
      }

      return {
        ...step,
        status,
      }
    })
  }

  return {
    assistantSteps,
    resetSteps,
    setStepStatus,
  }
}