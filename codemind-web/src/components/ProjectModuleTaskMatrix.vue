<template>
  <section class="matrix-section">
    <div class="matrix-header">
      <div>
        <h2>模块任务矩阵</h2>
        <p>按模块树层级展示需求资产状态，避免所有节点一股脑堆在一起。</p>
      </div>

      <div class="matrix-toolbar">
        <span class="selected-count">
          已选择 {{ selectedCount }} 个节点
        </span>

        <button class="secondary-button" :disabled="loading || matrixRows.length === 0 || batchGenerating" @click="emitToggleAll(true)">
          全选
        </button>

        <button class="secondary-button" :disabled="loading || selectedCount === 0 || batchGenerating" @click="emitToggleAll(false)">
          清空
        </button>

        <button
          class="secondary-button"
          :disabled="loading || expandableModuleIds.length === 0"
          @click="expandAllNodes"
        >
          全部展开
        </button>

        <button
          class="secondary-button"
          :disabled="loading || expandableModuleIds.length === 0"
          @click="collapseAllNodes"
        >
          收起层级
        </button>

        <button
          class="warning-button"
          :disabled="loading || selectedCount === 0 || !selectedFailedTaskCount || batchGenerating"
          @click="$emit('batch-retry-failed')"
        >
          重试失败项 {{ selectedFailedTaskCount || 0 }}
        </button>

        <button class="primary-button" :disabled="loading || selectedCount === 0 || batchGenerating" @click="$emit('batch-full-chain')">
          {{ batchGenerating ? '批量提交中...' : '批量生成核心资产' }}
        </button>
      </div>
    </div>

    <div v-if="batchMessage" class="batch-message success">
      {{ batchMessage }}
    </div>

    <div v-if="batchError" class="batch-message error">
      {{ batchError }}
    </div>

    <div v-if="loading" class="matrix-empty">
      正在加载模块任务矩阵...
    </div>

    <div v-else-if="!matrix || matrix.modules.length === 0" class="matrix-empty">
      当前项目暂无模块任务数据。
    </div>

    <div v-else class="matrix-table-wrap">
      <table class="matrix-table">
        <thead>
          <tr>
            <th class="select-col">
              <input
                type="checkbox"
                :checked="allSelected"
                :disabled="matrixRows.length === 0 || batchGenerating"
                @change="handleHeaderCheckboxChange"
              >
            </th>

            <th class="module-col">模块树节点</th>

            <th v-for="column in columns" :key="column.artifactType">
              {{ column.artifactTitle }}
            </th>
          </tr>
        </thead>

        <tbody>
          <template v-for="row in matrixRows" :key="row.module.moduleId">
            <tr v-if="row.isGroupStart" class="group-row">
              <td :colspan="columns.length + 2">
                {{ row.groupLabel }}
              </td>
            </tr>

            <tr :class="['node-row', `level-${Math.min(row.depth, 4)}`]">
              <td class="select-col">
                <input
                  type="checkbox"
                  :checked="isModuleSelected(row.module.moduleId)"
                  :disabled="batchGenerating"
                  @click.stop
                  @change="handleModuleCheckboxChange(row.module.moduleId, $event)"
                >
              </td>

              <td class="module-col">
                <div class="module-node" :style="{ paddingLeft: `${row.depth * 14}px` }">
                  <button
                    v-if="row.hasChildren"
                    class="node-collapse-button"
                    :title="isNodeCollapsed(row.module.moduleId) ? '展开子节点' : '收起子节点'"
                    @click.stop="toggleNodeCollapse(row.module.moduleId)"
                  >
                    {{ isNodeCollapsed(row.module.moduleId) ? '＋' : '－' }}
                  </button>

                  <span v-else class="node-collapse-spacer"></span>

                  <div class="module-node-content">
                    <span class="node-type-chip">{{ moduleTypeLabel(row.module.moduleType) }}</span>
                    <strong>{{ row.module.moduleName }}</strong>
                    <small>{{ row.module.modulePath || row.module.moduleKey }}</small>
                  </div>
                </div>
              </td>

              <td v-for="column in columns" :key="`${row.module.moduleId}-${column.artifactType}`">
                <div
                  class="matrix-status"
                  :class="statusClass(findItem(row.module, column.artifactType)?.latestTaskStatus, findItem(row.module, column.artifactType)?.artifactExists)"
                >
                  <button
                    class="matrix-status-content"
                    @click="handleCellClick(row.module.moduleId, findItem(row.module, column.artifactType)?.taskType)"
                  >
                    <strong>
                      {{ findItem(row.module, column.artifactType)?.statusText || '未开始' }}
                    </strong>

                    <span v-if="findItem(row.module, column.artifactType)?.artifactExists">
                      v{{ findItem(row.module, column.artifactType)?.artifactVersionNo || 1 }}
                    </span>

                    <small v-if="findItem(row.module, column.artifactType)?.displayMessage">
                      {{ findItem(row.module, column.artifactType)?.displayMessage }}
                    </small>
                  </button>

                  <button
                    v-if="canRetryMatrixItem(findItem(row.module, column.artifactType))"
                    class="cell-retry-button"
                    @click="handleRetryMatrixItem(findItem(row.module, column.artifactType))"
                  >
                    重试此项
                  </button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  ProjectAgentTaskMatrix,
  ProjectModuleTaskMatrixItem,
  ProjectModuleTaskMatrixModule,
} from '../types/codemind'

const props = defineProps<{
  matrix: ProjectAgentTaskMatrix | null
  loading?: boolean
  selectedModuleIds: string[]
  selectedFailedTaskCount?: number
  batchGenerating?: boolean
  batchMessage?: string
  batchError?: string
}>()

const emit = defineEmits<{
  filter: [payload: {
    moduleId?: string | null
    taskType?: string | null
  }]
  'toggle-module': [payload: {
    moduleId: string
    checked: boolean
  }]
  'toggle-all-modules': [payload: {
    moduleIds: string[]
    checked: boolean
  }]
  'batch-full-chain': []
  'retry-task': [taskId: string]
  'batch-retry-failed': []
}>()

interface MatrixRow {
  module: ProjectModuleTaskMatrixModule
  depth: number
  hasChildren: boolean
  isGroupStart: boolean
  groupLabel: string
}

const fallbackColumns = [
  { artifactType: 'MODULE_PRD', artifactTitle: '需求说明' },
  { artifactType: 'BUSINESS_FLOW', artifactTitle: '流程' },
  { artifactType: 'PAGE_SPEC', artifactTitle: '页面/清单' },
  { artifactType: 'DATA_MODEL', artifactTitle: '数据' },
  { artifactType: 'API_CONTRACT', artifactTitle: '接口' },
  { artifactType: 'TEST_SPEC', artifactTitle: '测试' },
  { artifactType: 'UI_CONTRACT', artifactTitle: '原型' },
  { artifactType: 'DEV_HANDOFF_PACKAGE', artifactTitle: '交接' },
]

const collapsedModuleIds = ref<Set<string>>(new Set())

const selectedModuleIdSet = computed(() =>
  new Set(props.selectedModuleIds || []),
)

const moduleChildrenMap = computed(() => {
  const modules = props.matrix?.modules || []
  const moduleMap = new Map<string, ProjectModuleTaskMatrixModule>()
  const childrenMap = new Map<string, ProjectModuleTaskMatrixModule[]>()

  for (const module of modules) {
    moduleMap.set(module.moduleId, module)
  }

  for (const module of modules) {
    const parentId = module.parentId || ''

    if (!parentId || !moduleMap.has(parentId)) {
      continue
    }

    const children = childrenMap.get(parentId) || []
    children.push(module)
    childrenMap.set(parentId, children)
  }

  return childrenMap
})

const expandableModuleIds = computed(() =>
  Array.from(moduleChildrenMap.value.entries())
    .filter(([, children]) => children.length > 0)
    .map(([moduleId]) => moduleId),
)

const matrixRows = computed<MatrixRow[]>(() => {
  const modules = props.matrix?.modules || []
  const moduleMap = new Map<string, ProjectModuleTaskMatrixModule>()
  const roots: ProjectModuleTaskMatrixModule[] = []

  for (const module of modules) {
    moduleMap.set(module.moduleId, module)
  }

  for (const module of modules) {
    const parentId = module.parentId || ''

    if (!parentId || !moduleMap.has(parentId)) {
      roots.push(module)
    }
  }

  const sortModules = (items: ProjectModuleTaskMatrixModule[]) =>
    [...items].sort((left, right) => {
      const leftLevel = left.levelNo ?? 0
      const rightLevel = right.levelNo ?? 0

      if (leftLevel !== rightLevel) {
        return leftLevel - rightLevel
      }

      const leftSort = left.sortOrder ?? 0
      const rightSort = right.sortOrder ?? 0

      if (leftSort !== rightSort) {
        return leftSort - rightSort
      }

      return left.moduleName.localeCompare(right.moduleName)
    })

  const rows: MatrixRow[] = []

  const visit = (
    module: ProjectModuleTaskMatrixModule,
    depth: number,
    rootLabel: string,
    isRoot: boolean,
  ) => {
    const children = sortModules(moduleChildrenMap.value.get(module.moduleId) || [])
    const collapsed = collapsedModuleIds.value.has(module.moduleId)

    rows.push({
      module,
      depth,
      hasChildren: children.length > 0,
      isGroupStart: isRoot,
      groupLabel: rootLabel,
    })

    if (collapsed) {
      return
    }

    for (const child of children) {
      visit(
        child,
        depth + 1,
        rootLabel,
        false,
      )
    }
  }

  for (const root of sortModules(roots)) {
    visit(
      root,
      0,
      `${moduleTypeLabel(root.moduleType)}：${root.moduleName}`,
      true,
    )
  }

  return rows
})

const selectedCount = computed(() =>
  matrixRows.value.filter((row) =>
    selectedModuleIdSet.value.has(row.module.moduleId),
  ).length,
)

const allSelected = computed(() =>
  matrixRows.value.length > 0 &&
  selectedCount.value === matrixRows.value.length,
)

const columns = computed(() => {
  const firstRow = matrixRows.value[0]

  if (!firstRow?.module.items?.length) {
    return fallbackColumns
  }

  return [...firstRow.module.items]
    .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
    .map((item) => ({
      artifactType: item.artifactType,
      artifactTitle: item.artifactTitle,
    }))
})

function isNodeCollapsed(moduleId: string): boolean {
  return collapsedModuleIds.value.has(moduleId)
}

function toggleNodeCollapse(moduleId: string) {
  const next = new Set(collapsedModuleIds.value)

  if (next.has(moduleId)) {
    next.delete(moduleId)
  } else {
    next.add(moduleId)
  }

  collapsedModuleIds.value = next
}

function expandAllNodes() {
  collapsedModuleIds.value = new Set()
}

function collapseAllNodes() {
  collapsedModuleIds.value = new Set(expandableModuleIds.value)
}

function findItem(
  module: ProjectModuleTaskMatrixModule,
  artifactType: string,
): ProjectModuleTaskMatrixItem | null {
  return module.items.find((item) => item.artifactType === artifactType) || null
}

function isModuleSelected(moduleId: string): boolean {
  return selectedModuleIdSet.value.has(moduleId)
}

function handleCellClick(
  moduleId: string,
  taskType?: string | null,
) {
  emit('filter', {
    moduleId,
    taskType: taskType || null,
  })
}

function handleModuleCheckboxChange(
  moduleId: string,
  event: Event,
) {
  const checked = (event.target as HTMLInputElement).checked

  emit('toggle-module', {
    moduleId,
    checked,
  })
}

function handleHeaderCheckboxChange(event: Event) {
  const checked = (event.target as HTMLInputElement).checked

  emitToggleAll(checked)
}

function emitToggleAll(checked: boolean) {
  emit('toggle-all-modules', {
    moduleIds: matrixRows.value.map((row) => row.module.moduleId),
    checked,
  })
}

function canRetryMatrixItem(
  item?: ProjectModuleTaskMatrixItem | null,
): boolean {
  if (!item?.latestTaskId || !item.latestTaskStatus) {
    return false
  }

  return ['FAILED', 'TIMEOUT', 'BLOCKED', 'CANCELLED'].includes(item.latestTaskStatus)
}

function handleRetryMatrixItem(
  item?: ProjectModuleTaskMatrixItem | null,
) {
  if (!item?.latestTaskId) {
    return
  }

  emit('retry-task', item.latestTaskId)
}

function statusClass(
  status?: string | null,
  artifactExists?: boolean,
): string {
  if (status === 'RUNNING') return 'running'
  if (status === 'QUEUED' || status === 'RETRYING') return 'queued'
  if (status === 'BLOCKED') return 'blocked'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'failed'
  if (status === 'CANCELLED') return 'cancelled'
  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS' || artifactExists) return 'success'
  return 'pending'
}

function normalizeModuleType(value?: string | null): string {
  return String(value || '').toUpperCase()
}

function moduleTypeLabel(value?: string | null): string {
  const type = normalizeModuleType(value)

  if (type === 'DOMAIN') return '业务域'
  if (type === 'MODULE') return '模块'
  if (type === 'SUBMODULE') return '场景'
  if (type === 'PAGE' || type === 'PAGE_MODULE') return '页面'
  if (type === 'FEATURE') return '功能点'

  return type || '节点'
}

</script>

<style scoped>
.matrix-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 20px;
}

.matrix-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.matrix-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #0f172a;
}

.matrix-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.matrix-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
}

.selected-count {
  color: #475569;
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 6px 10px;
}

.primary-button,
.secondary-button,
.warning-button {
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  white-space: nowrap;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.warning-button {
  border: 1px solid #f59e0b;
  background: #fffbeb;
  color: #92400e;
}

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.warning-button:hover:not(:disabled) {
  background: #fef3c7;
}

.primary-button:disabled,
.secondary-button:disabled,
.warning-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.batch-message {
  margin-bottom: 12px;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
}

.batch-message.success {
  color: #166534;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.batch-message.error {
  color: #991b1b;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.matrix-empty {
  padding: 32px;
  text-align: center;
  color: #64748b;
  background: #f8fafc;
  border-radius: 14px;
}

.matrix-table-wrap {
  overflow-x: auto;
}

.matrix-table {
  width: 100%;
  min-width: 1180px;
  border-collapse: separate;
  border-spacing: 0;
}

.matrix-table th {
  text-align: left;
  padding: 10px;
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.matrix-table td {
  padding: 10px;
  vertical-align: top;
  border-bottom: 1px solid #f1f5f9;
}

.group-row td {
  padding: 9px 10px;
  color: #334155;
  background: #eef2ff;
  font-size: 12px;
  font-weight: 800;
  border-top: 1px solid #c7d2fe;
  border-bottom: 1px solid #c7d2fe;
}

.select-col {
  width: 52px;
  text-align: center;
  position: sticky;
  left: 0;
  background: #ffffff;
  z-index: 2;
}

.matrix-table th.select-col {
  background: #f8fafc;
}

.select-col input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.module-col {
  width: 270px;
  position: sticky;
  left: 52px;
  background: #ffffff;
  z-index: 1;
}

.matrix-table th.module-col {
  background: #f8fafc;
}

.node-row.level-0 .module-col {
  background: #ffffff;
}

.node-row.level-1 .module-col {
  background: #fbfdff;
}

.node-row.level-2 .module-col,
.node-row.level-3 .module-col,
.node-row.level-4 .module-col {
  background: #f8fafc;
}

.module-node {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 6px;
  align-items: flex-start;
}

.module-node-content {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.node-collapse-button,
.node-collapse-spacer {
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border-radius: 7px;
}

.node-collapse-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  font-weight: 800;
  line-height: 1;
}

.node-collapse-button:hover {
  border-color: #2563eb;
  color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 8%);
}

.node-collapse-spacer {
  display: inline-block;
}

.node-type-chip {
  width: fit-content;
  border-radius: 999px;
  padding: 2px 7px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 11px;
  font-weight: 700;
}

.module-node strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.module-node small {
  display: block;
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}

.matrix-status {
  width: 100%;
  min-height: 76px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 8px;
  background: #ffffff;
  text-align: left;
}

.matrix-status.success {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.matrix-status.running {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.matrix-status.queued {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.matrix-status.blocked {
  background: #fffbeb;
  border-color: #fde68a;
}

.matrix-status.failed {
  background: #fef2f2;
  border-color: #fecaca;
}

.matrix-status.cancelled,
.matrix-status.pending {
  background: #f8fafc;
  border-color: #e2e8f0;
}

.matrix-status-content {
  display: block;
  width: 100%;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.matrix-status-content strong {
  display: block;
  font-size: 12px;
  margin-bottom: 4px;
}

.matrix-status-content span {
  display: block;
  font-size: 12px;
  color: #475569;
}

.matrix-status-content small {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
}

.cell-retry-button {
  margin-top: 8px;
  border: 1px solid #f59e0b;
  border-radius: 999px;
  padding: 4px 8px;
  background: #fffbeb;
  color: #92400e;
  cursor: pointer;
  font-size: 12px;
}

.cell-retry-button:hover {
  background: #fef3c7;
}

@media (max-width: 900px) {
  .matrix-header {
    flex-direction: column;
  }

  .matrix-toolbar {
    justify-content: flex-start;
  }
}
</style>



