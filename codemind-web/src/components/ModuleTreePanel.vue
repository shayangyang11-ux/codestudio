<template>
  <aside class="module-tree-panel">
    <div class="panel-title">
      <div>
        <h2>模块树</h2>
        <p>按业务域、模块、页面和功能点组织需求结构</p>
      </div>

      <button class="add-root-button" title="新增根节点" @click="$emit('create-root')">
        +
      </button>
    </div>

    <div v-if="modules.length === 0" class="empty-tree">
      暂无模块树。请先输入需求并生成模块树，或点击右上角新增根节点。
    </div>

    <div v-else class="tree-list">
      <ModuleTreeNodeItem v-for="node in modules" :key="node.id || node.moduleKey || node.key" :node="node"
        :selected-module-id="selectedModuleId" @select="$emit('select', $event)"
        @create-child="$emit('create-child', $event)" @rename="$emit('rename-node', $event)"
        @move="$emit('move-node', $event)" @delete="$emit('delete-node', $event)" />
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { ModuleTreeNode } from '../types/codemind'
import ModuleTreeNodeItem from './ModuleTreeNodeItem.vue'

defineProps<{
  modules: ModuleTreeNode[]
  selectedModuleId?: string
}>()

defineEmits<{
  select: [node: ModuleTreeNode]
  'create-root': []
  'create-child': [node: ModuleTreeNode]
  'rename-node': [node: ModuleTreeNode]
  'move-node': [node: ModuleTreeNode]
  'delete-node': [node: ModuleTreeNode]
}>()
</script>

<style scoped>
.module-tree-panel {
  width: 300px;
  min-width: 280px;
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 18px 14px;
  overflow-y: auto;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}

.panel-title h2 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.panel-title p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
}

.add-root-button {
  width: 28px;
  height: 28px;
  border: 1px solid #bfdbfe;
  border-radius: 9px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.add-root-button:hover {
  background: #dbeafe;
}

.empty-tree {
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 14px;
  font-size: 13px;
  color: #64748b;
  background: #f8fafc;
}

.tree-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>