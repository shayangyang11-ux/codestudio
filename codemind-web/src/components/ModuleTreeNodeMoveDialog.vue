<template>
  <div
    v-if="visible"
    class="dialog-mask"
  >
    <section class="dialog-card">
      <header class="dialog-header">
        <div>
          <h3>移动节点</h3>
          <p>
            将「{{ movingNodeName }}」移动到新的父节点下。
          </p>
        </div>

        <button
          class="icon-button"
          @click="$emit('cancel')"
        >
          ×
        </button>
      </header>

      <div class="dialog-body">
        <div class="current-node-card">
          <span class="label">当前节点</span>
          <strong>{{ movingNodeName }}</strong>
          <small>{{ movingNodeTypeLabel }}</small>
        </div>

        <label class="form-item">
          <span>新的父节点</span>
          <select v-model="selectedParentId">
            <option
              v-for="option in parentOptions"
              :key="option.value || 'ROOT'"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>

        <p
          v-if="parentOptions.length === 0"
          class="empty-tip"
        >
          当前节点没有可用的移动目标。
        </p>
      </div>

      <footer class="dialog-footer">
        <button
          class="secondary-button"
          @click="$emit('cancel')"
        >
          取消
        </button>

        <button
          class="primary-button"
          :disabled="parentOptions.length === 0"
          @click="handleSubmit"
        >
          确认移动
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  getRequirementNodeTypeLabel,
  resolveRequirementNodeType,
  type RequirementNodeType,
} from '../domain/requirementNodePolicy'

interface ParentOption {
  value: string | null
  label: string
}

const props = defineProps<{
  visible: boolean
  modules: ModuleTreeNode[]
  movingNode?: ModuleTreeNode | null
}>()

const emit = defineEmits<{
  cancel: []
  submit: [payload: { parentId: string | null }]
}>()

const selectedParentId = ref<string | null>(null)

const movingNodeName = computed(() => {
  const node = props.movingNode

  return node?.moduleName || node?.name || node?.title || node?.moduleKey || '未命名节点'
})

const movingNodeType = computed(() => {
  return resolveRequirementNodeType(props.movingNode)
})

const movingNodeTypeLabel = computed(() => {
  return getRequirementNodeTypeLabel(movingNodeType.value)
})

const parentOptions = computed<ParentOption[]>(() => {
  const node = props.movingNode

  if (!node?.id) {
    return []
  }

  const options: ParentOption[] = []
  const nodeType = resolveRequirementNodeType(node)

  if (canMoveToRoot(nodeType)) {
    options.push({
      value: null,
      label: '根节点',
    })
  }

  const descendants = collectDescendantIds(node)

  flattenModules(props.modules).forEach((candidate) => {
    if (!candidate.id) {
      return
    }

    if (candidate.id === node.id) {
      return
    }

    if (descendants.has(candidate.id)) {
      return
    }

    const candidateType = resolveRequirementNodeType(candidate)

    if (!isAllowedParent(nodeType, candidateType)) {
      return
    }

    options.push({
      value: candidate.id,
      label: `${candidate.modulePath || candidate.moduleName || candidate.name || candidate.moduleKey}（${getRequirementNodeTypeLabel(candidateType)}）`,
    })
  })

  return options
})

watch(
  () => [props.visible, props.movingNode?.id],
  () => {
    if (!props.visible) {
      return
    }

    const currentParentId = props.movingNode?.parentId || null

    const exists = parentOptions.value.some((option) => option.value === currentParentId)

    selectedParentId.value = exists
      ? currentParentId
      : parentOptions.value[0]?.value ?? null
  },
  { immediate: true },
)

function handleSubmit() {
  emit('submit', {
    parentId: selectedParentId.value || null,
  })
}

function canMoveToRoot(nodeType: RequirementNodeType): boolean {
  return nodeType === 'DOMAIN' || nodeType === 'MODULE'
}

function isAllowedParent(
  nodeType: RequirementNodeType,
  parentType: RequirementNodeType,
): boolean {
  if (nodeType === 'MODULE') {
    return parentType === 'DOMAIN'
  }

  if (nodeType === 'SUBMODULE') {
    return parentType === 'MODULE'
  }

  if (nodeType === 'PAGE') {
    return parentType === 'MODULE' || parentType === 'SUBMODULE'
  }

  if (nodeType === 'FEATURE') {
    return parentType === 'PAGE'
  }

  return false
}

function flattenModules(nodes: ModuleTreeNode[]): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []

  const walk = (items: ModuleTreeNode[]) => {
    items.forEach((item) => {
      result.push(item)

      if (item.children?.length) {
        walk(item.children)
      }
    })
  }

  walk(nodes)

  return result
}

function collectDescendantIds(node: ModuleTreeNode): Set<string> {
  const result = new Set<string>()

  const walk = (item: ModuleTreeNode) => {
    item.children?.forEach((child) => {
      if (child.id) {
        result.add(child.id)
      }

      walk(child)
    })
  }

  walk(node)

  return result
}
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 38%);
}

.dialog-card {
  width: min(520px, calc(100vw - 40px));
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgb(15 23 42 / 28%);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.dialog-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.icon-button {
  width: 32px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 20px;
}

.current-node-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
}

.current-node-card .label {
  font-size: 12px;
  color: #64748b;
}

.current-node-card strong {
  font-size: 15px;
  color: #0f172a;
}

.current-node-card small {
  color: #475569;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item span {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-item select {
  height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 0 10px;
  font-size: 14px;
  color: #0f172a;
  background: #ffffff;
}

.form-item select:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

.empty-tip {
  margin: 0;
  color: #b45309;
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px 18px;
  border-top: 1px solid #e2e8f0;
}

.primary-button,
.secondary-button {
  border-radius: 10px;
  padding: 8px 14px;
  font-size: 14px;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.primary-button:not(:disabled):hover {
  background: #1d4ed8;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.secondary-button:hover {
  border-color: #2563eb;
  color: #2563eb;
}
</style>