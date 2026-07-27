<template>
  <div class="tree-node" :style="nodeLevelStyle">
    <div
      class="tree-node-row"
      :class="{
        active: node.id === selectedModuleId,
        page: isPage,
        feature: isFeature,
        root: level === 0,
      }"
    >
      <button class="tree-node-button" @click="$emit('select', node)">
        <span v-if="level > 0" class="node-branch-mark" />

        <span class="node-main">
          <span class="node-name">
            {{ node.moduleName || node.name || node.title || node.moduleKey || '未命名节点' }}
          </span>

          <span v-if="showNodeTypeBadge" class="node-type-badge" :class="badgeClass">
            {{ nodeTypeLabel }}
          </span>
        </span>
      </button>

      <div
        class="node-action-menu-wrap"
        @mouseleave="actionMenuOpen = false"
        @keydown.esc="actionMenuOpen = false"
      >
        <button
          class="node-more-button"
          type="button"
          title="节点操作"
          :aria-expanded="actionMenuOpen"
          @click.stop="actionMenuOpen = !actionMenuOpen"
        >
          ⋯
        </button>

        <div v-if="actionMenuOpen" class="node-action-popover" role="menu">
          <button
            v-if="canCreateChild"
            class="node-action-item"
            type="button"
            role="menuitem"
            @click.stop="handleCreateChild"
          >
            新增子节点
          </button>

          <button
            class="node-action-item"
            type="button"
            role="menuitem"
            @click.stop="handleRename"
          >
            重命名
          </button>

          <button
            class="node-action-item"
            type="button"
            role="menuitem"
            @click.stop="handleMove"
          >
            移动节点
          </button>

          <button
            class="node-action-item danger"
            type="button"
            role="menuitem"
            @click.stop="handleDelete"
          >
            删除节点
          </button>
        </div>
      </div>
    </div>

    <div v-if="node.children?.length" class="tree-node-children">
      <ModuleTreeNodeItem
        v-for="child in node.children"
        :key="child.id || child.moduleKey || child.key"
        :node="child"
        :level="level + 1"
        :selected-module-id="selectedModuleId"
        @select="$emit('select', $event)"
        @create-child="$emit('create-child', $event)"
        @rename="$emit('rename', $event)"
        @move="$emit('move', $event)"
        @delete="$emit('delete', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  getRequirementNodeBadgeClass,
  getRequirementNodeTypeLabel,
  isFeatureNode,
  isPageNode,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'

const props = withDefaults(defineProps<{
  node: ModuleTreeNode
  selectedModuleId?: string
  level?: number
}>(), {
  level: 0,
})

const emit = defineEmits<{
  select: [node: ModuleTreeNode]
  'create-child': [node: ModuleTreeNode]
  rename: [node: ModuleTreeNode]
  move: [node: ModuleTreeNode]
  delete: [node: ModuleTreeNode]
}>()

const actionMenuOpen = ref(false)

const nodeType = computed(() => resolveRequirementNodeType(props.node))
const nodeTypeLabel = computed(() => getRequirementNodeTypeLabel(nodeType.value))
const badgeClass = computed(() => getRequirementNodeBadgeClass(nodeType.value))
const isPage = computed(() => isPageNode(props.node))
const isFeature = computed(() => isFeatureNode(props.node))

const nodeLevelStyle = computed<Record<string, string>>(() => ({
  '--tree-level': String(props.level),
}))

const showNodeTypeBadge = computed(() => {
  return !isPage.value && !isFeature.value
})

const canCreateChild = computed(() => {
  return !isFeature.value
})

function handleCreateChild() {
  actionMenuOpen.value = false
  emit('create-child', props.node)
}

function handleRename() {
  actionMenuOpen.value = false
  emit('rename', props.node)
}

function handleMove() {
  actionMenuOpen.value = false
  emit('move', props.node)
}

function handleDelete() {
  actionMenuOpen.value = false
  emit('delete', props.node)
}
</script>

<style scoped>
.tree-node {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tree-node-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 2px 4px 2px 6px;
  background: transparent;
  color: #0f172a;
  transition: all 0.16s ease;
}

.tree-node-row:hover {
  border-color: #dbeafe;
  background: #f8fafc;
}

.tree-node-row.active {
  border-color: #2564eb36;
  background: linear-gradient(90deg, #eff3ff91 0%, #ffffff 100%);
  color: #1d4ed8;
  box-shadow: inset 0.5px 0 0 #2564eb50;
}

.tree-node-row.root {
  margin-top: 2px;
}

.tree-node-row.page .node-name {
  font-weight: 800;
}

.tree-node-row.feature {
  color: #334155;
}

.tree-node-button {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  border: none;
  padding: 5px 0 5px 2px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.node-branch-mark {
  position: absolute;
  left: -13px;
  width: 10px;
  height: 1px;
  background: #cbd5e1;
}

.node-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.node-name {
  overflow: hidden;
  color: inherit;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.45;
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
  font-weight: 700;
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

.node-action-menu-wrap {
  position: relative;
  display: none;
  flex-shrink: 0;
}

.tree-node-row:hover .node-action-menu-wrap,
.tree-node-row.active .node-action-menu-wrap,
.node-action-menu-wrap:focus-within {
  display: block;
}

.node-more-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid #d6e1f0;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.16s ease;
}

.node-more-button:hover,
.node-more-button[aria-expanded='true'] {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}

.node-action-popover {
  position: absolute;
  top: 28px;
  right: 0;
  z-index: 20;
  display: grid;
  gap: 3px;
  width: 118px;
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 5px;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.16);
}

.node-action-item {
  border: 0;
  border-radius: 9px;
  padding: 7px 9px;
  background: transparent;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
  transition: all 0.16s ease;
}

.node-action-item:hover {
  background: #eff6ff;
  color: #1d4ed8;
}

.node-action-item.danger {
  color: #b91c1c;
}

.node-action-item.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}

.tree-node-children {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-left: 10px;
  padding-left: 8px;
  border-left: 1px solid #e2e8f0;
}
</style>
