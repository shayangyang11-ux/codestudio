<template>
  <div class="tree-node">
    <div class="tree-node-row" :class="{
      active: node.id === selectedModuleId,
      page: isPage,
      feature: isFeature,
    }">
      <button class="tree-node-button" @click="$emit('select', node)">
        <span class="node-main">
  
          <span class="node-name">
            {{ node.moduleName || node.name || node.title || node.moduleKey || '未命名节点' }}
          </span>

          <span v-if="showNodeTypeBadge" class="node-type-badge" :class="badgeClass">
            {{ nodeTypeLabel }}
          </span>
        </span>
      </button>

      <div class="node-actions">
        <button v-if="canCreateChild" class="node-action-button" title="新增子节点"
          @click.stop="$emit('create-child', node)">
          +
        </button>

        <button class="node-action-button" title="重命名" @click.stop="$emit('rename', node)">
          ✎
        </button>

        <button class="node-action-button" title="移动" @click.stop="$emit('move', node)">
          ↕
        </button>

        <button class="node-action-button danger" title="删除" @click.stop="$emit('delete', node)">
          ×
        </button>
      </div>
    </div>

    <ModuleTreeNodeItem v-for="child in node.children" :key="child.id || child.moduleKey || child.key" :node="child"
      :selected-module-id="selectedModuleId" @select="$emit('select', $event)"
      @create-child="$emit('create-child', $event)" @rename="$emit('rename', $event)" @move="$emit('move', $event)"
      @delete="$emit('delete', $event)" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  getRequirementNodeBadgeClass,
  getRequirementNodeTypeLabel,
  isFeatureNode,
  isPageNode,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'

const props = defineProps<{
  node: ModuleTreeNode
  selectedModuleId?: string
}>()

defineEmits<{
  select: [node: ModuleTreeNode]
  'create-child': [node: ModuleTreeNode]
  rename: [node: ModuleTreeNode]
  move: [node: ModuleTreeNode]
  delete: [node: ModuleTreeNode]
}>()

const nodeType = computed(() => resolveRequirementNodeType(props.node))
const nodeTypeLabel = computed(() => getRequirementNodeTypeLabel(nodeType.value))
const badgeClass = computed(() => getRequirementNodeBadgeClass(nodeType.value))
const isPage = computed(() => isPageNode(props.node))
const isFeature = computed(() => isFeatureNode(props.node))

const showNodeTypeBadge = computed(() => {
  return !isPage.value && !isFeature.value
})

const canCreateChild = computed(() => {
  return !isFeature.value
})
</script>

<style scoped>
.tree-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tree-node-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 1px 4px 1px 6px;
  background: transparent;
  color: #0f172a;
  transition: all 0.16s ease;
}

.tree-node-row:hover {
  background: #f8fafc;
  border-color: #dbeafe;
}

.tree-node-row.active {
  background: #eff6ff;
  border-color: #2563eb;
  color: #1d4ed8;
}

.tree-node-row.feature {
  color: #334155;
}

.tree-node-button {
  flex: 1;
  min-width: 0;
  border: none;
  padding: 5px 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.node-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.node-name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-row.feature .node-name {
  font-size: 13px;
  font-weight: 500;
}

.node-type-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 7px;
  font-size: 11px;
  line-height: 1.4;
  font-weight: 600;
  white-space: nowrap;
}

.node-type-badge.domain {
  background: #fef3c7;
  color: #92400e;
}

.node-type-badge.module {
  background: #dbeafe;
  color: #1d4ed8;
}

.node-type-badge.submodule {
  background: #e0e7ff;
  color: #3730a3;
}

.node-type-badge.unknown {
  background: #e2e8f0;
  color: #475569;
}

.node-actions {
  display: none;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

.tree-node-row:hover .node-actions,
.tree-node-row.active .node-actions {
  display: flex;
}

.node-action-button {
  width: 22px;
  height: 22px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.node-action-button:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.node-action-button.danger:hover {
  border-color: #ef4444;
  color: #dc2626;
}

.tree-node-children {
  margin-left: 10px;
  padding-left: 8px;
  border-left: 1px solid #e5e7eb;
}
</style>