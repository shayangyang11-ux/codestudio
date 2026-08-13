<template>
  <aside class="module-tree-panel">
    <div class="panel-title">
      <div class="panel-title-text">
        <h2>需求骨架</h2>
        <p>按模块、功能点和页面组织需求结构</p>
      </div>

      <button class="add-root-button" title="新增根模块" @click="$emit('create-root')">
        +
      </button>
    </div>

    <div v-if="modules.length === 0" class="empty-tree">
      暂无需求骨架。请先输入需求生成模块与功能点，或点击右上角新增根模块。
    </div>

    <div v-else class="tree-list">
      <ModuleTreeNodeItem
        v-for="node in modules"
        :key="node.id || node.moduleKey || node.key"
        :node="node"
        :level="0"
        :selected-module-id="selectedModuleId"
        @select="$emit('select', $event)"
        @create-child="$emit('create-child', $event)"
        @rename="$emit('rename-node', $event)"
        @move="$emit('move-node', $event)"
        @delete="$emit('delete-node', $event)"
      />
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
  width: 285px;
  min-width: 280px;
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 0;
  overflow-y: auto;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 15px 8px;
  border-bottom: 1px solid #e2e8f0;
}

.panel-title-text {
  min-width: 0;
}

.panel-title h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
  color: #0f172a;
}

.panel-title p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.35;
  color: #64748b;
}

.add-root-button {
  flex: 0 0 auto;
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
  margin: 10px 8px;
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
  gap: 3px;
  padding: 10px 8px 18px;
}

/* A9_7_6_TREE_INDEPENDENT_SCROLL_FIX */
.module-tree-panel {
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.panel-title {
  position: sticky;
  top: 0;
  z-index: 2;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
}



/* A9.10.14.4.3.15.2.6_PRD_UNIFIED_UI_TREE_COLLAPSE
   Make the tree panel friendlier for recursive collapse rows.
*/
.tree-list {
  gap: 1px !important;
  padding: 10px 8px 18px !important;
}

.module-tree-panel {
  scrollbar-gutter: stable !important;
}

</style>