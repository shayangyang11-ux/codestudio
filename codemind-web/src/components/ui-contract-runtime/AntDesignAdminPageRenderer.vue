<!-- A9.10.14.4.3.15.7.24L4_ANTD_ADMIN_PAGE_RENDERER
     Ant Design Vue 页面级渲染器（薄分发器）。
     所有组件语义（表单/表格/工具栏/指标/步骤/时间线/页签/描述列表/树/折叠/提示等）
     统一交给 AntDesignUiContractRuntimeNode 递归渲染，保证预览与交付代码同一套语义。 -->
<template>
  <section
    class="a9-ant-admin-page"
    data-ui-admin-page-renderer="ant-design-vue"
    data-ui-runtime-main-renderer="ant-design-vue"
  >
    <AntDesignUiContractRuntimeNode
      v-for="component in components"
      :key="componentKey(component)"
      :component="component"
      :component-map="componentMap"
      :interactions="interactions"
      :form-values="formValues"
      :field-errors="fieldErrors"
      :mock-data="mockData"
      :active-tabs="activeTabs"
      :visible-component-ids="visibleComponentIds"
      :disabled-component-ids="disabledComponentIds"
      @execute="$emit('execute', $event)"
      @update-field="$emit('updateField', $event)"
      @switch-tab="$emit('switchTab', $event)"
    />
  </section>
</template>

<script setup lang="ts">
import AntDesignUiContractRuntimeNode from './AntDesignUiContractRuntimeNode.vue'
import type {
  UiContractComponent,
  UiContractInteraction,
} from '../../types/uiContract'

defineOptions({
  name: 'AntDesignAdminPageRenderer',
})

const props = defineProps<{
  components: UiContractComponent[]
  componentMap: Record<string, UiContractComponent>
  interactions: UiContractInteraction[]
  formValues: Record<string, unknown>
  fieldErrors: Record<string, string>
  mockData: Record<string, unknown>
  activeTabs: Record<string, string>
  visibleComponentIds: string[]
  disabledComponentIds: string[]
}>()

const emit = defineEmits<{
  execute: [payload: { actionId: string; componentId: string; row?: Record<string, unknown>; label?: string; dataBindingKey?: string; tableComponentId?: string; sourceComponentType?: string }]
  updateField: [payload: { fieldKey: string; value: unknown }]
  switchTab: [payload: { componentId: string; tabKey: string }]
}>()

type LooseRecord = Record<string, any>

function componentKey(component: UiContractComponent): string {
  const record = component as unknown as LooseRecord
  return String(record.componentId || record.id || record.type || '').trim() || JSON.stringify(component)
}
</script>

<style scoped>
.a9-ant-admin-page {
  display: grid;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 4px;
  color: #172033;
}
</style>