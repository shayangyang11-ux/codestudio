<template>
  <div class="tree-node">
    <div
      class="tree-node-row"
      :class="{
        active: isSelected,
        component: isComponentNode,
        page: isPage,
        feature: isFeature,
        root: level === 0,
      }"
    >
      <button
        v-if="hasChildren"
        type="button"
        class="tree-expand-toggle"
        :class="{ collapsed: !expanded }"
        :aria-label="expanded ? '收起下级节点' : '展开下级节点'"
        :aria-expanded="expanded"
        @click.stop="toggleExpanded"
      >
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <path d="M5.2 3.7 9.5 8l-4.3 4.3 1.05 1.05L11.6 8 6.25 2.65 5.2 3.7Z" />
        </svg>
      </button>
      <span v-else class="tree-expand-spacer" />

      <button class="tree-node-button" type="button" @click="$emit('select', node)">
        <span class="node-main">
          <span class="node-name">
            {{ nodeTitle }}
          </span>

          <span v-if="showNodeTypeBadge" class="node-type-badge" :class="badgeClass">
            {{ nodeTypeLabel }}
          </span>
        </span>
      </button>

      <div
        v-if="!isComponentNode"
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
            {{ createChildActionLabel }}
          </button>

          <button
            class="node-action-item"
            type="button"
            role="menuitem"
            @click.stop="handleRename"
          >
            重命名 / 编辑
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

    <div v-if="hasChildren && expanded" class="tree-node-children">
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
// A9.10.14.4.3.15.2.6.3_MODULE_TREE_FULL_REPLACE
import { computed, ref, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  canCreateRequirementChild,
  getCreateChildActionLabel,
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
  selectedModuleId: '',
})

const emit = defineEmits<{
  select: [node: ModuleTreeNode]
  'create-child': [node: ModuleTreeNode]
  rename: [node: ModuleTreeNode]
  move: [node: ModuleTreeNode]
  delete: [node: ModuleTreeNode]
}>()

const actionMenuOpen = ref(false)
const expanded = ref(true)

const nodeType = computed(() => resolveRequirementNodeType(props.node))
const isPage = computed(() => isPageNode(props.node))
const isFeature = computed(() => isFeatureNode(props.node))
const isComponentNode = computed(() => String(nodeType.value || '').toUpperCase() === 'COMPONENT')
const hasChildren = computed(() => Boolean(props.node.children?.length))
const isSelected = computed(() => props.node.id === props.selectedModuleId)

const nodeTitle = computed(() => String(
  props.node.moduleName ||
    (props.node as any).name ||
    (props.node as any).title ||
    props.node.moduleKey ||
    (props.node as any).key ||
    '未命名节点',
))

const nodeTypeLabel = computed(() => isComponentNode.value ? '组件' : getRequirementNodeTypeLabel(nodeType.value))
const badgeClass = computed(() => isComponentNode.value ? 'component' : getRequirementNodeBadgeClass(nodeType.value))
const showNodeTypeBadge = computed(() => !isPage.value && !isFeature.value)

const canCreateChild = computed(() => {
  if (isComponentNode.value) return false
  return canCreateRequirementChild(props.node)
})
const createChildActionLabel = computed(() => getCreateChildActionLabel(props.node))

watch(
  () => props.selectedModuleId,
  (selectedId) => {
    if (!selectedId) return
    if (containsNodeId(props.node, selectedId)) {
      expanded.value = true
    }
  },
  { immediate: true },
)

function containsNodeId(node: ModuleTreeNode | null | undefined, id: string): boolean {
  if (!node || !id) return false
  if (node.id === id) return true
  return Boolean((node.children || []).some((child) => containsNodeId(child, id)))
}

function toggleExpanded() {
  expanded.value = !expanded.value
}

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
/* A9.10.14.4.3.15.2.6.3_MODULE_TREE_FULL_REPLACE
   Full replacement of the tree node component style. This removes the old
   accumulated indentation, removes the visible frame around the ellipsis,
   and removes the broken short connector lines. */
.tree-node {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.tree-node-row {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 2px;
  min-height: 28px;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 1px 2px 1px 0;
  color: #0f172a;
  background: transparent;
  transition: background 0.14s ease, border-color 0.14s ease, color 0.14s ease;
}

.tree-node-row:hover {
  border-color: #e2e8f0;
  background: #f8fafc;
}

.tree-node-row.active {
  border-color: #bfdbfe;
  background: linear-gradient(90deg, #eff6ff 0%, #ffffff 100%);
  color: #1d4ed8;
}

.tree-expand-toggle,
.tree-expand-spacer {
  width: 12px;
  height: 22px;
  flex: 0 0 auto;
}

.tree-expand-toggle {
  display: inline-grid;
  place-items: center;
  border: 0;
  padding: 0;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.tree-expand-toggle svg {
  width: 10px;
  height: 10px;
  fill: currentColor;
  transform: rotate(90deg);
  transition: transform 0.14s ease;
}

.tree-expand-toggle.collapsed svg {
  transform: rotate(0deg);
}

.tree-expand-toggle:hover {
  color: #2563eb;
}

.tree-node-button {
  min-width: 0;
  width: 100%;
  border: 0;
  padding: 2px 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.node-main {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
}

.node-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: inherit;
  font-size: 13px;
  font-weight: 760;
  line-height: 1.45;
}

.tree-node-row.feature .node-name {
  font-size: 12.5px;
  font-weight: 700;
}

.tree-node-row.page .node-name {
  font-size: 12.5px;
  font-weight: 700;
}

.node-type-badge {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 1px 5px;
  font-size: 10px;
  line-height: 1.45;
  font-weight: 780;
  white-space: nowrap;
}

.node-type-badge.domain {
  background: #fef3c7;
  color: #92400e;
}

.node-type-badge.module,
.node-type-badge.submodule {
  background: #dbeafe;
  color: #1d4ed8;
}

.node-type-badge.component,
.node-type-badge.unknown {
  background: #e2e8f0;
  color: #475569;
}

.node-action-menu-wrap {
  position: relative;
  display: inline-flex;
  justify-content: center;
  min-width: 0;
}

.node-more-button {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  padding: 0;
  background: transparent;
  box-shadow: none;
  color: #94a3b8;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.node-more-button:hover,
.node-more-button[aria-expanded='true'] {
  border: 0;
  background: #f1f5f9;
  color: #2563eb;
  box-shadow: none;
}

.node-action-popover {
  position: absolute;
  top: 24px;
  right: 0;
  z-index: 30;
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
  gap: 1px;
  margin-left: 8px;
  padding-left: 6px;
  border-left: 0;
}

.node-branch-mark,
.tree-node::before,
.tree-node::after,
.tree-node-children::before,
.tree-node-children::after {
  display: none !important;
  content: none !important;
}
</style>
