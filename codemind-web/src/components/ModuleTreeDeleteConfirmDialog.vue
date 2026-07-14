<template>
  <div
    v-if="visible"
    class="dialog-mask"
  >
    <section class="dialog-card">
      <header class="dialog-header">
        <div>
          <h3>删除节点确认</h3>
          <p>删除模块树节点前，请确认影响范围。</p>
        </div>

        <button
          class="icon-button"
          @click="$emit('cancel')"
        >
          ×
        </button>
      </header>

      <div class="dialog-body">
        <div class="danger-card">
          <span class="danger-label">即将删除</span>
          <strong>{{ nodeName }}</strong>
          <small>{{ nodeTypeLabel }}</small>
        </div>

        <div class="impact-grid">
          <div class="impact-item">
            <span>下级节点</span>
            <strong>{{ descendantCount }}</strong>
          </div>

          <div class="impact-item">
            <span>删除方式</span>
            <strong>软删除</strong>
          </div>
        </div>

        <div class="impact-section">
          <h4>可能影响的需求资产</h4>

          <ul>
            <li
              v-for="item in affectedArtifacts"
              :key="item"
            >
              {{ item }}
            </li>
          </ul>
        </div>

        <div class="warning-box">
          删除后，该节点及其下级节点会从模块树中移除；相关需求资产不会立即物理删除，但后续需要重新评估、归档或重新生成。
        </div>
      </div>

      <footer class="dialog-footer">
        <button
          class="secondary-button"
          @click="$emit('cancel')"
        >
          取消
        </button>

        <button
          class="danger-button"
          @click="$emit('confirm')"
        >
          确认删除
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  getRequirementNodeTypeLabel,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'

const props = defineProps<{
  visible: boolean
  node?: ModuleTreeNode | null
}>()

defineEmits<{
  cancel: []
  confirm: []
}>()

const nodeType = computed(() => resolveRequirementNodeType(props.node))

const nodeTypeLabel = computed(() => {
  return getRequirementNodeTypeLabel(nodeType.value)
})

const nodeName = computed(() => {
  const node = props.node

  return node?.moduleName || node?.name || node?.title || node?.moduleKey || '未命名节点'
})

const descendantCount = computed(() => {
  if (!props.node) {
    return 0
  }

  return countDescendants(props.node)
})

const affectedArtifacts = computed(() => {
  if (nodeType.value === 'PAGE') {
    return [
      '页面需求',
      '页面交互流程',
      '页面规范',
      '页面数据模型',
      '页面接口契约',
      '页面测试规格',
      '交互原型 UI_CONTRACT',
      '页面开发交接包',
      '父级模块页面地图',
    ]
  }

  if (nodeType.value === 'FEATURE') {
    return [
      '功能点需求',
      '功能点流程',
      '功能点接口',
      '功能点测试',
      '所属页面规范',
      '所属页面交互原型',
      '所属页面开发交接包',
    ]
  }

  if (nodeType.value === 'MODULE' || nodeType.value === 'SUBMODULE') {
    return [
      '模块 PRD',
      '业务流程',
      '页面地图',
      '数据模型',
      '接口契约',
      '测试规格',
      '开发交接包',
      '下级页面交互原型',
    ]
  }

  if (nodeType.value === 'DOMAIN') {
    return [
      '业务域下全部模块结构',
      '模块 PRD',
      '业务流程',
      '页面地图',
      '下级页面交互原型',
      '项目级交付包',
    ]
  }

  return [
    '当前节点相关需求资产',
    '下级节点相关需求资产',
  ]
})

function countDescendants(node: ModuleTreeNode): number {
  if (!node.children?.length) {
    return 0
  }

  return node.children.reduce((total, child) => {
    return total + 1 + countDescendants(child)
  }, 0)
}
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
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

.danger-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  padding: 12px;
  background: #fef2f2;
}

.danger-label {
  font-size: 12px;
  color: #b91c1c;
}

.danger-card strong {
  font-size: 16px;
  color: #991b1b;
}

.danger-card small {
  color: #7f1d1d;
}

.impact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.impact-item {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
}

.impact-item span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.impact-item strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  color: #0f172a;
}

.impact-section {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
}

.impact-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #0f172a;
}

.impact-section ul {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.warning-box {
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 12px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  line-height: 1.6;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px 18px;
  border-top: 1px solid #e2e8f0;
}

.secondary-button,
.danger-button {
  border-radius: 10px;
  padding: 8px 14px;
  font-size: 14px;
  cursor: pointer;
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

.danger-button {
  border: 1px solid #dc2626;
  background: #dc2626;
  color: #ffffff;
}

.danger-button:hover {
  background: #b91c1c;
}
</style>