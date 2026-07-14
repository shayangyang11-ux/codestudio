<template>
  <div class="task-center-page">
    <header class="task-center-header">
      <div>
        <button class="back-button" @click="backToWorkspace">
          ← 返回项目工作区
        </button>

        <h1>项目任务中心</h1>
        <p>
          {{ selectedWorkspace?.workspaceName || routeWorkspaceId }}
          /
          {{ currentProject?.projectName || routeProjectCode }}
        </p>
      </div>

      <button class="primary-button" :disabled="loadingAll" @click="loadAll(false)">
        {{ loadingAll ? '刷新中...' : '刷新当前视图' }}
      </button>
    </header>

    <ProjectTaskSummaryCards :summary="summary" :loading="loadingSummary" @refresh="loadSummary" />

    <section class="task-center-tabs">
      <button
        :class="['tab-button', { active: activeTaskTab === 'details' }]"
        @click="switchTaskTab('details')"
      >
        任务明细
        <span v-if="pageData">{{ pageData.totalElements }}</span>
      </button>

      <button
        :class="['tab-button', { active: activeTaskTab === 'matrix' }]"
        @click="switchTaskTab('matrix')"
      >
        任务模块矩阵
        <span v-if="matrix">{{ matrix.modules.length }}</span>
      </button>
    </section>

    <ProjectTaskListPanel
      v-if="activeTaskTab === 'details'"
      :page-data="pageData"
      :filters="filters"
      :loading="loadingPage"
      @search="handleSearch"
      @refresh="loadPage"
      @change-page="handleChangePage"
      @cancel="handleCancelTask"
      @retry="handleRetryTask"
      @archive="handleArchiveTask"
      @view-detail="handleViewTaskDetail"
    />

    <ProjectModuleTaskMatrix
      v-else
      :matrix="matrix"
      :loading="loadingMatrix"
      :selected-module-ids="selectedBatchModuleIds"
      :selected-failed-task-count="selectedFailedTaskCount"
      :batch-generating="batchFullChainLoading || batchRetryFailedLoading"
      :batch-message="batchFullChainMessage"
      :batch-error="batchFullChainError"
      @filter="handleMatrixFilter"
      @toggle-module="handleToggleMatrixModule"
      @toggle-all-modules="handleToggleAllMatrixModules"
      @batch-full-chain="handleBatchFullChain"
      @retry-task="handleRetryMatrixTask"
      @batch-retry-failed="handleBatchRetryFailedTasks"
    />

    <ProjectTaskDetailDrawer
      :visible="taskDetailVisible"
      :loading="taskDetailLoading"
      :operation-loading="taskDetailActionLoading"
      :task="selectedTaskDetail"
      :error="taskDetailError"
      @cancel="handleDetailCancelTask"
      @retry="handleDetailRetryTask"
      @archive="handleDetailArchiveTask"
      @close="closeTaskDetail"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProjectModuleTaskMatrix from '../components/ProjectModuleTaskMatrix.vue'
import ProjectTaskDetailDrawer from '../components/ProjectTaskDetailDrawer.vue'
import ProjectTaskListPanel from '../components/ProjectTaskListPanel.vue'
import ProjectTaskSummaryCards from '../components/ProjectTaskSummaryCards.vue'
import {
  fetchProjectAgentTaskMatrix,
  fetchProjectAgentTaskPage,
  fetchProjectAgentTaskSummary,
} from '../api/projectTaskCenterApi'
import { createBatchFullChainTasksAsync } from '../api/agentApi'
import {
  archiveAgentTask,
  cancelAgentTask,
  fetchAgentTaskPayload,
  retryAgentTask,
} from '../api/agentTaskApi'
import { useAppSession } from '../composables/useAppSession'
import type {
  AgentTaskPayloadResponse,
  ProjectAgentTaskMatrix,
  ProjectAgentTaskPage,
  ProjectAgentTaskPageQuery,
  ProjectAgentTaskStatusSummary,
} from '../types/codemind'

type TaskCenterTab = 'details' | 'matrix'

const route = useRoute()
const router = useRouter()

const {
  selectedWorkspace,
  currentProject,
  bootstrapAuth,
  ensureProjectSelected,
} = useAppSession()

const summary = ref<ProjectAgentTaskStatusSummary | null>(null)
const matrix = ref<ProjectAgentTaskMatrix | null>(null)
const pageData = ref<ProjectAgentTaskPage | null>(null)
const taskDetailVisible = ref(false)
const taskDetailLoading = ref(false)
const taskDetailActionLoading = ref(false)
const selectedTaskDetail = ref<AgentTaskPayloadResponse | null>(null)
const taskDetailError = ref('')
const activeTaskTab = ref<TaskCenterTab>('details')

const loadingSummary = ref(false)
const loadingMatrix = ref(false)
const loadingPage = ref(false)
const loadingRoute = ref(false)

const selectedBatchModuleIds = ref<string[]>([])
const batchFullChainLoading = ref(false)
let taskCenterPollingTimer: number | null = null
const batchFullChainMessage = ref('')
const batchFullChainError = ref('')
const batchRetryFailedLoading = ref(false)

const filters = ref<ProjectAgentTaskPageQuery>({
  status: '',
  taskType: '',
  moduleId: '',
  createdBy: '',
  page: 0,
  size: 20,
  sort: 'createdAtDesc',
})

const routeWorkspaceId = computed(() =>
  getRouteParam(route.params.workspaceId),
)

const routeProjectCode = computed(() =>
  getRouteParam(route.params.projectCode),
)

const projectId = computed(() =>
  currentProject.value?.projectId || '',
)

const loadingAll = computed(() =>
  loadingRoute.value ||
  loadingSummary.value ||
  loadingMatrix.value ||
  loadingPage.value ||
  batchFullChainLoading.value ||
  batchRetryFailedLoading.value,
)

const selectedFailedTaskIds = computed(() => {
  const selectedModuleIdSet = new Set(selectedBatchModuleIds.value)

  const retryableStatuses = new Set([
    'FAILED',
    'TIMEOUT',
    'BLOCKED',
    'CANCELLED',
  ])

  if (!matrix.value?.modules?.length || selectedModuleIdSet.size === 0) {
    return []
  }

  const taskIds: string[] = []

  for (const module of matrix.value.modules) {
    if (!selectedModuleIdSet.has(module.moduleId)) {
      continue
    }

    for (const item of module.items || []) {
      if (
        item.latestTaskId &&
        item.latestTaskStatus &&
        retryableStatuses.has(item.latestTaskStatus)
      ) {
        taskIds.push(item.latestTaskId)
      }
    }
  }

  return Array.from(new Set(taskIds))
})

const selectedFailedTaskCount = computed(() =>
  selectedFailedTaskIds.value.length,
)

onMounted(async () => {
  await loadFromRoute()
})

onBeforeUnmount(() => {
  stopTaskCenterPolling()
})

watch(
  () => [
    route.params.workspaceId,
    route.params.projectCode,
  ],
  async () => {
    await loadFromRoute()
  },
)

async function loadFromRoute() {
  loadingRoute.value = true

  try {
    await bootstrapAuth()

    if (!routeWorkspaceId.value || !routeProjectCode.value) {
      await router.replace({
        name: 'workspaces',
      })
      return
    }

    const result = await ensureProjectSelected(
      routeWorkspaceId.value,
      routeProjectCode.value,
    )

    if (!result) {
      await router.replace({
        name: 'workspaces',
      })
      return
    }

    await loadAll(false)
  } finally {
    loadingRoute.value = false
  }
}

async function loadAll(silent = false) {
  if (!projectId.value) {
    return
  }

  await Promise.all([
    loadSummary(silent),
    loadActiveTaskTab(silent),
  ])

  if (hasActiveProjectTasks()) {
    startTaskCenterPolling()
  } else {
    stopTaskCenterPolling()
  }
}

async function loadAllKeepScroll() {
  const scrollTop = window.scrollY

  await loadAll(true)
  await nextTick()

  window.scrollTo({
    top: scrollTop,
    behavior: 'auto',
  })
}

async function loadActiveTaskTab(silent = false) {
  if (activeTaskTab.value === 'matrix') {
    await loadMatrix(silent)
    return
  }

  await loadPage(silent)
}

async function switchTaskTab(tab: TaskCenterTab) {
  if (activeTaskTab.value === tab) {
    return
  }

  activeTaskTab.value = tab

  if (tab === 'matrix' && !matrix.value) {
    await loadMatrix(false)
  }

  if (tab === 'details' && !pageData.value) {
    await loadPage(false)
  }
}

function startTaskCenterPolling() {
  if (taskCenterPollingTimer !== null) {
    return
  }

  taskCenterPollingTimer = window.setInterval(() => {
    void pollTaskCenter()
  }, 3000)
}

function stopTaskCenterPolling() {
  if (taskCenterPollingTimer === null) {
    return
  }

  window.clearInterval(taskCenterPollingTimer)
  taskCenterPollingTimer = null
}

async function pollTaskCenter() {
  await Promise.all([
    loadSummary(true),
    loadActiveTaskTab(true),
  ])

  if (!hasActiveProjectTasks()) {
    stopTaskCenterPolling()
  }
}

function hasActiveProjectTasks(): boolean {
  const activeStatuses = new Set([
    'QUEUED',
    'RUNNING',
    'RETRYING',
    'BLOCKED',
  ])

  if (summary.value && summary.value.active > 0) {
    return true
  }

  if (!matrix.value?.modules?.length) {
    return false
  }

  return matrix.value.modules.some((module) =>
    (module.items || []).some((item) =>
      item.latestTaskStatus &&
      activeStatuses.has(item.latestTaskStatus),
    ),
  )
}

async function loadSummary(silent = false) {
  if (!projectId.value) {
    return
  }

  if (!silent) {
    loadingSummary.value = true
  }

  try {
    summary.value = await fetchProjectAgentTaskSummary(projectId.value)
  } finally {
    if (!silent) {
      loadingSummary.value = false
    }
  }
}

async function loadMatrix(silent = false) {
  if (!projectId.value) {
    return
  }

  if (!silent) {
    loadingMatrix.value = true
  }

  try {
    matrix.value = await fetchProjectAgentTaskMatrix(projectId.value)
  } finally {
    if (!silent) {
      loadingMatrix.value = false
    }
  }
}

async function loadPage(silent = false) {
  if (!projectId.value) {
    return
  }

  if (!silent) {
    loadingPage.value = true
  }

  try {
    pageData.value = await fetchProjectAgentTaskPage(
      projectId.value,
      filters.value,
    )
  } finally {
    if (!silent) {
      loadingPage.value = false
    }
  }
}

async function handleSearch(
  nextFilters: ProjectAgentTaskPageQuery,
) {
  filters.value = {
    ...filters.value,
    ...nextFilters,
    page: nextFilters.page ?? 0,
    size: nextFilters.size ?? filters.value.size ?? 20,
    sort: nextFilters.sort || 'createdAtDesc',
  }

  await loadPage(false)
}

async function handleChangePage(page: number) {
  filters.value = {
    ...filters.value,
    page: Math.max(page, 0),
  }

  await loadPage(false)
}

async function handleMatrixFilter(payload: {
  moduleId?: string | null
  taskType?: string | null
}) {
  filters.value = {
    ...filters.value,
    moduleId: payload.moduleId || '',
    taskType: payload.taskType || '',
    page: 0,
  }

  activeTaskTab.value = 'details'

  await loadPage(false)
}

function handleToggleMatrixModule(payload: {
  moduleId: string
  checked: boolean
}) {
  const nextSet = new Set(selectedBatchModuleIds.value)

  if (payload.checked) {
    nextSet.add(payload.moduleId)
  } else {
    nextSet.delete(payload.moduleId)
  }

  selectedBatchModuleIds.value = Array.from(nextSet)
  batchFullChainMessage.value = ''
  batchFullChainError.value = ''
}

function handleToggleAllMatrixModules(payload: {
  moduleIds: string[]
  checked: boolean
}) {
  if (payload.checked) {
    selectedBatchModuleIds.value = Array.from(
      new Set(payload.moduleIds.filter(Boolean)),
    )
  } else {
    selectedBatchModuleIds.value = []
  }

  batchFullChainMessage.value = ''
  batchFullChainError.value = ''
}

async function handleBatchFullChain() {
  if (!routeWorkspaceId.value || !routeProjectCode.value) {
    return
  }

  const moduleIds = selectedBatchModuleIds.value.filter(Boolean)

  if (moduleIds.length === 0) {
    batchFullChainError.value = '请先选择至少一个模块。'
    batchFullChainMessage.value = ''
    return
  }

  batchFullChainLoading.value = true
  batchFullChainMessage.value = ''
  batchFullChainError.value = ''

  try {
    const response = await createBatchFullChainTasksAsync(
      routeWorkspaceId.value,
      routeProjectCode.value,
      {
        moduleIds,
        requirementText: '请为选中模块批量补齐当前层级核心需求资产。',
        overwriteExisting: false,
      },
    )

    batchFullChainMessage.value =
      `批量补齐任务已提交：成功 ${response.successModuleCount} 个模块，` +
      `失败 ${response.failedModuleCount} 个模块，` +
      `新建 ${response.totalCreatedTaskCount} 个任务，` +
      `复用 ${response.totalReusedTaskCount} 个任务，` +
      `跳过 ${response.totalSkippedArtifactCount} 个已有资产。`

    if (
      response.totalCreatedTaskCount === 0 &&
      response.totalReusedTaskCount === 0 &&
      response.totalSkippedArtifactCount > 0
    ) {
      batchFullChainMessage.value +=
        ' 当前操作只补齐缺失资产，已有资产不会重新生成；如需处理失败项，请点击“重试失败项”。'
    }

    if (response.failedModuleCount > 0) {
      batchFullChainError.value =
        response.failedModules
          .map((item) => `${item.moduleId}: ${item.errorMessage || item.errorCode}`)
          .join('；')
    }

    await loadAllKeepScroll()
    startTaskCenterPolling()
  } catch (error) {
    console.error(error)
    batchFullChainError.value =
      error instanceof Error
        ? error.message
        : '批量创建需求资产任务失败。'
  } finally {
    batchFullChainLoading.value = false
  }
}

async function handleRetryMatrixTask(taskId: string) {
  if (!taskId) {
    return
  }

  batchFullChainMessage.value = ''
  batchFullChainError.value = ''

  try {
    await retryAgentTask(
      taskId,
      '用户在项目任务中心矩阵中重试失败任务。',
    )

    batchFullChainMessage.value = '失败项重试已提交。'

    await loadAllKeepScroll()
    startTaskCenterPolling()
  } catch (error) {
    console.error(error)

    batchFullChainError.value =
      error instanceof Error
        ? error.message
        : '重试失败任务失败。'
  }
}

async function handleBatchRetryFailedTasks() {
  const taskIds = selectedFailedTaskIds.value

  if (taskIds.length === 0) {
    batchFullChainError.value = '选中模块中没有可重试的失败任务。'
    batchFullChainMessage.value = ''
    return
  }

  batchRetryFailedLoading.value = true
  batchFullChainMessage.value = ''
  batchFullChainError.value = ''

  let successCount = 0
  const failedMessages: string[] = []

  try {
    for (const taskId of taskIds) {
      try {
        await retryAgentTask(
          taskId,
          '用户在项目任务中心批量重试失败任务。',
        )

        successCount += 1
      } catch (error) {
        const message =
          error instanceof Error
            ? error.message
            : '未知错误'

        failedMessages.push(`${taskId}: ${message}`)
      }
    }

    batchFullChainMessage.value =
      `失败项重试已提交：成功 ${successCount} 个任务，失败 ${failedMessages.length} 个任务。`

    if (failedMessages.length > 0) {
      batchFullChainError.value = failedMessages.join('；')
    }

    await loadAllKeepScroll()
    startTaskCenterPolling()
  } finally {
    batchRetryFailedLoading.value = false
  }
}

async function handleCancelTask(taskId: string) {
  await cancelAgentTask(taskId, '用户在项目任务中心取消任务。')
  await loadAllKeepScroll()
  startTaskCenterPolling()
}

async function handleRetryTask(taskId: string) {
  await retryAgentTask(taskId, '用户在项目任务中心重试任务。')
  await loadAllKeepScroll()
  startTaskCenterPolling()
}

async function handleArchiveTask(taskId: string) {
  if (!taskId) {
    return
  }

  const confirmed = window.confirm(
    '确定要归档这条任务明细吗？\n\n归档后默认不再显示在任务中心，但数据库记录仍保留用于审计追踪。运行中或排队中的任务请先取消后再归档。',
  )

  if (!confirmed) {
    return
  }

  try {
    await archiveAgentTask(
      taskId,
      '用户在项目任务中心归档任务明细。',
    )

    if (selectedTaskDetail.value?.id === taskId) {
      closeTaskDetail()
    }

    await loadAllKeepScroll()
  } catch (error) {
    window.alert(
      error instanceof Error
        ? error.message
        : '归档任务失败。',
    )
  }
}

async function handleViewTaskDetail(taskId: string) {
  if (!taskId) {
    return
  }

  taskDetailVisible.value = true
  taskDetailLoading.value = true
  taskDetailActionLoading.value = false
  taskDetailError.value = ''
  selectedTaskDetail.value = null

  try {
    selectedTaskDetail.value = await fetchAgentTaskPayload(taskId)
  } catch (error) {
    console.error(error)

    taskDetailError.value =
      error instanceof Error
        ? error.message
        : '加载任务详情失败。'
  } finally {
    taskDetailLoading.value = false
  }
}

async function refreshTaskDetailIfStillOpen(taskId: string) {
  if (!taskDetailVisible.value || selectedTaskDetail.value?.id !== taskId) {
    return
  }

  try {
    selectedTaskDetail.value = await fetchAgentTaskPayload(taskId)
  } catch (error) {
    taskDetailError.value =
      error instanceof Error
        ? error.message
        : '刷新任务详情失败。'
  }
}

async function handleDetailCancelTask(taskId: string) {
  if (!taskId) {
    return
  }

  taskDetailActionLoading.value = true

  try {
    await handleCancelTask(taskId)
    await refreshTaskDetailIfStillOpen(taskId)
  } finally {
    taskDetailActionLoading.value = false
  }
}

async function handleDetailRetryTask(taskId: string) {
  if (!taskId) {
    return
  }

  taskDetailActionLoading.value = true

  try {
    await handleRetryTask(taskId)
    await refreshTaskDetailIfStillOpen(taskId)
  } finally {
    taskDetailActionLoading.value = false
  }
}

async function handleDetailArchiveTask(taskId: string) {
  if (!taskId) {
    return
  }

  taskDetailActionLoading.value = true

  try {
    await handleArchiveTask(taskId)
  } finally {
    taskDetailActionLoading.value = false
  }
}

function closeTaskDetail() {
  taskDetailVisible.value = false
  taskDetailLoading.value = false
  taskDetailActionLoading.value = false
  taskDetailError.value = ''
  selectedTaskDetail.value = null
}

async function backToWorkspace() {
  await router.push({
    name: 'project-workspace',
    params: {
      workspaceId: routeWorkspaceId.value,
      projectCode: routeProjectCode.value,
    },
  })
}

function getRouteParam(value: string | string[] | undefined): string {
  if (Array.isArray(value)) {
    return value[0] || ''
  }

  return value || ''
}
</script>

<style scoped>
.task-center-page {
  min-height: 100vh;
  display: grid;
  gap: 18px;
  padding: 24px;
  background: #f8fafc;
}

.task-center-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 20px;
}

.task-center-header h1 {
  margin: 12px 0 6px;
  color: #0f172a;
  font-size: 26px;
}

.task-center-header p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.back-button {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  padding: 0;
  font-size: 14px;
}

.primary-button {
  border: 1px solid #2563eb;
  border-radius: 10px;
  padding: 10px 16px;
  background: #2563eb;
  color: #ffffff;
  cursor: pointer;
}

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.primary-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.task-center-tabs {
  display: flex;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 6px;
  background: #ffffff;
  width: fit-content;
}

.tab-button {
  border: none;
  border-radius: 12px;
  padding: 10px 14px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
}

.tab-button span {
  margin-left: 6px;
  border-radius: 999px;
  padding: 2px 7px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.tab-button.active {
  background: #2563eb;
  color: #ffffff;
}

.tab-button.active span {
  background: rgba(255, 255, 255, 0.22);
  color: #ffffff;
}
</style>



