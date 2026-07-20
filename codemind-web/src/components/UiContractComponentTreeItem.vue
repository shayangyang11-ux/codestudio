<template>
  <li class="component-item">
    <div class="component-line">
      <code>{{ component.type }}</code>
      <strong>{{ component.title || component.componentId }}</strong>
      <span>{{ component.componentId }}</span>
      <em>{{ componentHint }}</em>
    </div>

    <ul v-if="component.children?.length">
      <UiContractComponentTreeItem
        v-for="child in component.children"
        :key="child.componentId"
        :component="child"
      />
    </ul>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { UiContractComponent } from '../types/uiContract'

defineOptions({
  name: 'UiContractComponentTreeItem',
})

const props = defineProps<{
  component: UiContractComponent
}>()

const componentHint = computed(() => {
  if (
    props.component.type === 'FORM' ||
    props.component.type === 'FIELD_GROUP'
  ) {
    return `字段 ${props.component.fields?.length || 0} 个`
  }

  if (props.component.type === 'TABLE') {
    return `列 ${props.component.columns?.length || 0} 个`
  }

  if (props.component.type === 'TABS') {
    return `页签 ${props.component.tabs?.length || 0} 个`
  }

  return `动作 ${props.component.actionIds?.length || 0} 个`
})
</script>

<style scoped>
.component-item,
.component-item ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  list-style: none;
}

.component-line {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 10px;
  padding: 9px 10px;
  background: #f8fafc;
}

.component-line code {
  border-radius: 6px;
  padding: 3px 6px;
  background: #e0e7ff;
  color: #3730a3;
}

.component-line span {
  color: #64748b;
  font-size: 12px;
}

.component-line em {
  margin-left: auto;
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}
</style>
