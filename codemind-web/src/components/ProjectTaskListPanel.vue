<template>
  <section class="task-list-section">
    <div class="task-list-header">
      <div>
        <h2>任务明细</h2>
        <p>支持按状态、任务类型、模块和创建人筛选；终态任务可归档清理，默认不再显示。</p>
      </div>

      <button class="secondary-button" :disabled="loading" @click="$emit('refresh')">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div class="filters">
      <label>
        状态
        <select v-model="localFilters.status">
          <option value="">全部</option>
          <option value="QUEUED">排队中</option>
          <option value="RUNNING">生成中</option>
          <option value="RETRYING">等待重试</option>
          <option value="BLOCKED">等待依赖</option>
          <option value="FAILED">失败</option>
          <option value="TIMEOUT">超时</option>
          <option value="SUCCESS">已完成</option>
          <option value="PARTIAL_SUCCESS">部分完成</option>
          <option value="CANCELLED">已取消</option>
        </select>
      </label>

      <label>
        任务类型
        <select v-model="localFilters.taskType">
          <option value="">全部</option>
          <option value="GENERATE_MODULE_PRD">模块 PRD</option>
          <option value="GENERATE_BUSINESS_FLOW">业务流程</option>
          <option value="GENERATE_PAGE_SPEC">页面规范</option>
          <option value="GENERATE_DATA_MODEL">数据模型</option>
          <option value="GENERATE_API_CONTRACT">接口契约</option>
          <option value="GENERATE_TEST_SPEC">测试规格</option>
          <option value="GENERATE_UI_CONTRACT">交互原型</option>
          <option value="GENERATE_DEV_HANDOFF_PACKAGE">开发交接包</option>
        </select>
      </label>

      <label>
        模块 ID
        <input v-model="localFilters.moduleId" placeholder="点击矩阵单元格可自动填充">
      </label>

      <label>
        创建人
        <input v-model="localFilters.createdBy" placeholder="createdBy">
      </label>

      <label>
        排序
        <select v-model="localFilters.sort">
          <option value="createdAtDesc">创建时间倒序</option>
          <option value="createdAtAsc">创建时间正序</option>
          <option value="updatedAtDesc">更新时间倒序</option>
          <option value="updatedAtAsc">更新时间正序</option>
          <option value="priorityDesc">优先级倒序</option>
          <option value="statusAsc">状态正序</option>
        </select>
      </label>

      <button class="primary-button" @click="applyFilters">
        查询
      </button>

      <button class="secondary-button" @click="clearFilters">
        清空
      </button>
    </div>

    <div v-if="loading" class="empty-list">
      正在加载任务明细...
    </div>

    <div v-else-if="!pageData || pageData.content.length === 0" class="empty-list">
      暂无符合条件的任务。
    </div>

    <div v-else class="task-list">
      <article v-for="task in pageData.content" :key="task.id" class="task-card">
        <div class="task-main">
          <div class="task-title">
            <strong>{{ task.taskTitle || task.taskType }}</strong>
            <span :class="['status-pill', statusClass(task.status)]">
              {{ task.statusText || task.status }}
            </span>
          </div>

          <div class="task-meta">
            <span>任务 ID：{{ shortId(task.id) }}</span>
            <span v-if="task.moduleId">模块：{{ shortId(task.moduleId) }}</span>
            <span>尝试：{{ task.attemptCount ?? 0 }}/{{ task.maxAttempts ?? '-' }}</span>
            <span v-if="task.createdBy">创建人：{{ task.createdBy }}</span>
            <span v-if="task.createdAt">创建：{{ formatTime(task.createdAt) }}</span>
            <span v-if="task.updatedAt">更新：{{ formatTime(task.updatedAt) }}</span>
          </div>

          <p v-if="task.requirementTextPreview" class="task-preview">
            {{ task.requirementTextPreview }}
          </p>

          <p v-if="task.displayMessage" class="task-message">
            {{ task.displayMessage }}
          </p>
        </div>

        <div class="task-actions">
          <button class="task-action-button neutral" @click="$emit('view-detail', task.id)">
            详情
          </button>

          <button
            v-if="canCancel(task.status)"
            class="task-action-button neutral"
            @click="$emit('cancel', task.id)"
          >
            取消
          </button>

          <button
            v-if="canRetry(task.status)"
            class="task-action-button warning"
            @click="$emit('retry', task.id)"
          >
            重试
          </button>

          <button
            v-if="canArchive(task.status)"
            class="task-action-button danger"
            title="归档后默认不再出现在任务中心，数据库审计记录仍保留。"
            @click="$emit('archive', task.id)"
          >
            归档
          </button>
        </div>
      </article>
    </div>

    <div v-if="pageData && pageData.totalElements > 0" class="pagination">
      <button
        class="secondary-button"
        :disabled="pageData.first || loading"
        @click="$emit('change-page', pageData.page - 1)"
      >
        上一页
      </button>

      <span>
        第 {{ pageData.page + 1 }} / {{ Math.max(pageData.totalPages, 1) }} 页，
        共 {{ pageData.totalElements }} 条
      </span>

      <button
        class="secondary-button"
        :disabled="pageData.last || loading"
        @click="$emit('change-page', pageData.page + 1)"
      >
        下一页
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type {
  AgentTaskStatus,
  ProjectAgentTaskPage,
  ProjectAgentTaskPageQuery,
} from '../types/codemind'

const props = defineProps<{
  pageData: ProjectAgentTaskPage | null
  filters: ProjectAgentTaskPageQuery
  loading?: boolean
}>()

const emit = defineEmits<{
  search: [filters: ProjectAgentTaskPageQuery]
  refresh: []
  'change-page': [page: number]
  cancel: [taskId: string]
  retry: [taskId: string]
  archive: [taskId: string]
  'view-detail': [taskId: string]
}>()

const localFilters = reactive<ProjectAgentTaskPageQuery>({
  status: '',
  taskType: '',
  moduleId: '',
  createdBy: '',
  page: 0,
  size: 20,
  sort: 'createdAtDesc',
})

watch(
  () => props.filters,
  (next) => {
    localFilters.status = next.status || ''
    localFilters.taskType = next.taskType || ''
    localFilters.moduleId = next.moduleId || ''
    localFilters.createdBy = next.createdBy || ''
    localFilters.page = next.page ?? 0
    localFilters.size = next.size ?? 20
    localFilters.sort = next.sort || 'createdAtDesc'
  },
  {
    immediate: true,
    deep: true,
  },
)

function applyFilters() {
  emit('search', {
    ...localFilters,
    page: 0,
  })
}

function clearFilters() {
  emit('search', {
    status: '',
    taskType: '',
    moduleId: '',
    createdBy: '',
    page: 0,
    size: 20,
    sort: 'createdAtDesc',
  })
}

function canCancel(status: AgentTaskStatus): boolean {
  return ['PENDING', 'QUEUED', 'RUNNING', 'RETRYING'].includes(status)
}

function canRetry(status: AgentTaskStatus): boolean {
  return ['FAILED', 'TIMEOUT', 'CANCELLED', 'BLOCKED'].includes(status)
}

function canArchive(status: AgentTaskStatus): boolean {
  return ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED', 'BLOCKED'].includes(status)
}

function statusClass(status: AgentTaskStatus): string {
  if (status === 'RUNNING') return 'running'
  if (status === 'QUEUED' || status === 'RETRYING') return 'queued'
  if (status === 'BLOCKED') return 'blocked'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'failed'
  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS') return 'success'
  if (status === 'CANCELLED') return 'cancelled'
  return 'pending'
}

function shortId(value?: string | null): string {
  if (!value) {
    return '-'
  }

  if (value.length <= 12) {
    return value
  }

  return `${value.slice(0, 8)}...${value.slice(-4)}`
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.task-list-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 20px;
}

.task-list-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.task-list-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #0f172a;
}

.task-list-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr)) auto auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 16px;
}

.filters label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 12px;
}

.filters input,
.filters select {
  height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 0 10px;
  background: #ffffff;
  color: #0f172a;
}

.primary-button,
.secondary-button,
.warning-button,
.danger-button {
  border-radius: 10px;
  padding: 9px 14px;
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

.danger-button {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
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

.danger-button:hover:not(:disabled) {
  border-color: #dc2626;
  background: #fee2e2;
}

.primary-button:disabled,
.secondary-button:disabled,
.warning-button:disabled,
.danger-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.task-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 34px;
  border-radius: 10px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.task-action-button.neutral {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.task-action-button.warning {
  border: 1px solid #f59e0b;
  background: #fffbeb;
  color: #92400e;
}

.task-action-button.danger {
  border: 1px solid #fecaca;
  background: #ffffff;
  color: #dc2626;
}

.task-action-button.neutral:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 8%);
}

.task-action-button.warning:hover:not(:disabled) {
  border-color: #f59e0b;
  background: #fef3c7;
  box-shadow: 0 0 0 3px rgb(245 158 11 / 10%);
}

.task-action-button.danger:hover:not(:disabled) {
  border-color: #dc2626;
  background: #fef2f2;
  box-shadow: 0 0 0 3px rgb(220 38 38 / 8%);
}

.task-action-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty-list {
  padding: 32px;
  text-align: center;
  color: #64748b;
  background: #f8fafc;
  border-radius: 14px;
}

.task-list {
  display: grid;
  gap: 12px;
}

.task-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px;
  background: #ffffff;
}

.task-main {
  min-width: 0;
  flex: 1 1 auto;
}

.task-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.task-title strong {
  color: #0f172a;
}

.status-pill {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  border: 1px solid #e2e8f0;
}

.status-pill.success {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.status-pill.running {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.status-pill.queued {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #475569;
}

.status-pill.blocked {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.status-pill.failed {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.status-pill.cancelled,
.status-pill.pending {
  background: #f8fafc;
  border-color: #e2e8f0;
  color: #64748b;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.task-preview {
  margin: 10px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.task-message {
  margin: 10px 0 0;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
}

.task-actions {
  flex: 0 0 auto;
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  white-space: nowrap;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 18px;
  color: #475569;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-card {
    flex-direction: column;
  }

  .task-actions {
    justify-content: flex-start;
    overflow-x: auto;
    max-width: 100%;
    padding-bottom: 2px;
  }
}
</style>
