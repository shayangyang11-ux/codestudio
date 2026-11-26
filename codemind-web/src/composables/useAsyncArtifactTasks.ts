// A9.10.14.4.3.10_CURRENT_FILE_CLEANUP
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  cancelAgentTask,
  createApiContractTaskAsync,
  createBusinessFlowTaskAsync,
  createDataModelTaskAsync,
  createDevHandoffPackageTaskAsync,
  createFullChainTaskAsync,
  createModulePrdTaskAsync,
  createPageSpecTaskAsync,
  createTestSpecTaskAsync,
  createUiContractTaskAsync,
  fetchAgentTask,
  fetchProjectAgentTasks,
  retryAgentTask,
} from '../api'
import {
  ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS,
  getRequirementArtifactDefinitionByTaskType,
  type AsyncRequirementTaskType,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'

import {
  getRequirementArtifactTitleForNode,
  getRequirementTaskUnavailableReason,
} from '../domain/requirementNodePolicy'

import { buildAsyncRequirementTaskPrompt } from '../domain/requirementGenerationPrompts'
import type {
  AgentTaskSummary,
  ArtifactResponse,
  AssistantStep,
  AsyncAgentTaskResponse,
  FullChainAsyncTaskResponse,
  ModuleArtifactTaskItem,
  ModuleTreeNode,
  UiContractGenerationReadinessResponse,
} from '../types/codemind'
import type { ProjectThemeProfile } from '../types/themeProfile'

export interface UseAsyncArtifactTasksOptions {
  resolveWorkspaceId: () => string
  resolveProjectCode: () => string
  resolveProjectId: () => string
  resolveSelectedModule: () => ModuleTreeNode | null
  resolveModuleDisplayName: (module: ModuleTreeNode) => string
  getArtifactByType: (artifactType: RequirementArtifactType) => ArtifactResponse | null
  loadArtifactByType: (
    artifactType: RequirementArtifactType,
    module: ModuleTreeNode,
  ) => Promise<boolean>
  clearArtifactsAfterArtifact: (artifactType: RequirementArtifactType) => void
  setStepStatus: (
    stepId: string,
    status: AssistantStep['status'],
  ) => void
  resolveUiContractReadiness?: () =>
    UiContractGenerationReadinessResponse | null
  resolveUiContractReadinessLoading?: () => boolean
  resolveThemeProfile?: () => ProjectThemeProfile | null
  onTaskBlocked?: (
    message: string,
    taskType: AsyncRequirementTaskType,
  ) => void | Promise<void>
  onTaskFailed?: (
    message: string,
    taskType: AsyncRequirementTaskType,
  ) => void | Promise<void>
  onTaskCreated?: (
    task: AgentTaskSummary,
    definition: {
      artifactType: RequirementArtifactType
      taskType: AsyncRequirementTaskType
      stepId: string
      title: string
    },
  ) => void | Promise<void>

  resolveRequirementText: () => string
  clearAllArtifacts: () => void
  onFullChainCreated?: (
    response: FullChainAsyncTaskResponse,
  ) => void | Promise<void>

  onTaskTerminalStatusChanged?: (payload: {
    task: AgentTaskSummary
    previousStatus?: string | null
    definition: {
      artifactType: RequirementArtifactType
      taskType: AsyncRequirementTaskType
      stepId: string
      title: string
    }
  }) => void | Promise<void>
}

export function useAsyncArtifactTasks(
  options: UseAsyncArtifactTasksOptions,
) {
  const moduleAgentTasks = ref<Record<string, AgentTaskSummary | null>>({})

  const isGeneratingPrd = ref(false)
  const isGeneratingBusinessFlow = ref(false)
  const isGeneratingPageSpec = ref(false)
  const isGeneratingDataModel = ref(false)
  const isGeneratingApiContract = ref(false)
  const isGeneratingTestSpec = ref(false)
  const isGeneratingUiContract = ref(false)
  const isGeneratingDevHandoffPackage = ref(false)
  const isGeneratingFullChain = ref(false)

  let taskPollingTimer: number | null = null
  const notifiedTerminalTaskKeys = new Set<string>()

  // A9.10.14.4.2.3_PAGE_SPEC_BEFORE_UI_FRONTEND_CHAIN
  // PAGE_SPEC is hidden/internal for users, but UI_CONTRACT should still be
  // generated from a page-level semantic specification when it is missing.
  // User clicks "生成交互原型" once: first prepare PAGE_SPEC, then continue UI_CONTRACT.
  const pendingUiContractAfterPageSpecModuleId = ref<string | null>(null)
// A9.10.14.4.3.8.2_FINAL_FORCE_PAGESPEC_CHAIN: one-shot bypass so PAGE_SPEC -> UI_CONTRACT does not recurse.
const a9AllowUiContractAfterForcedPageSpec = ref(false)

  // A9.10.14.4.3.7.1_ROBUST_PRD_IMPACT_FRONTEND_FIX
  // User-facing '重新生成交互原型' always refreshes hidden PAGE_SPEC first.
  // This guard allows the automatic continuation to UI_CONTRACT without looping.
  const continuingUiContractAfterPageSpec = ref(false)

  const moduleArtifactTaskItems = computed<ModuleArtifactTaskItem[]>(() => {
    const module = options.resolveSelectedModule()

    return ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => {
      const artifact = options.getArtifactByType(definition.artifactType)
      const nodeUnavailableReason =
        getRequirementTaskUnavailableReason(
          definition.taskType,
          module,
        )

      const unavailableReason =
        resolveTaskUnavailableReason(
          definition.taskType,
          nodeUnavailableReason,
        )

      return {
        artifactType: definition.artifactType,
        taskType: definition.taskType,
        title: getRequirementArtifactTitleForNode(
          definition.artifactType,
          module,
        ),
        artifactExists: Boolean(artifact?.artifactId || artifact?.exists),
        artifactVersionNo: artifact?.versionNo,
        artifactStatus: artifact?.status || null,
        task: moduleAgentTasks.value[definition.taskType] || null,
        unavailableReason,
      }
    })
  })

  // A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN
  // PAGE_SPEC is hidden from users. The visible "重新生成交互原型" action must
  // refresh PAGE_SPEC not only when it is missing, but also when PRD edits have
  // marked it as needing regeneration.
  // A9.10.14.4.3.6_PRD_IMPACT_BANNER_UI_INTEGRITY_FIX
  // PAGE_SPEC is hidden/internal. The user-visible action is still only
  // "重新生成交互原型", but when the module PRD is newer, the hidden PAGE_SPEC
  // must be refreshed even if an older PAGE_SPEC already exists.
  function hasCurrentPageSpecArtifact(): boolean {
    const pageSpec = options.getArtifactByType('PAGE_SPEC')
    return Boolean(pageSpec?.artifactId || pageSpec?.exists)
  }

void hasCurrentPageSpecArtifact // A9.10.14.4.3.15.6.18_BUILD_TYPE_HOTFIX


  function artifactNeedsRegeneration(artifact: ArtifactResponse | null | undefined): boolean {
    const status = String((artifact as any)?.status || '').trim().toUpperCase()
    return [
      'NEEDS_REVIEW',
      'STALE',
      'OUTDATED',
      'IMPACTED',
      'DIRTY',
      'INVALIDATED',
    ].includes(status)
  }

void artifactNeedsRegeneration // A9.10.14.4.3.15.6.18_BUILD_TYPE_HOTFIX


  function artifactUpdatedMillis(artifact: ArtifactResponse | null | undefined): number {
    if (!artifact) return 0
    const record = artifact as any
    for (const key of [
      'updatedAt',
      'updated_at',
      'modifiedAt',
      'modified_at',
      'createdAt',
      'created_at',
      'generatedAt',
      'generated_at',
    ]) {
      const raw = record[key]
      if (!raw) continue
      const time = new Date(String(raw)).getTime()
      if (Number.isFinite(time) && time > 0) return time
    }
    return 0
  }

  function sourceNewerThanTarget(
    source: ArtifactResponse | null | undefined,
    target: ArtifactResponse | null | undefined,
  ): boolean {
    const sourceTime = artifactUpdatedMillis(source)
    if (!sourceTime) return false
    const targetTime = artifactUpdatedMillis(target)
    return !targetTime || sourceTime > targetTime
  }

void sourceNewerThanTarget // A9.10.14.4.3.15.6.18_BUILD_TYPE_HOTFIX


  function shouldPreparePageSpecBeforeUi(): boolean {
    // A9.10.14.4.3.10_CURRENT_FILE_CLEANUP
    // PAGE_SPEC is hidden/internal. Every explicit UI_CONTRACT regeneration
    // should refresh PAGE_SPEC first. The automatic continuation sets
    // a9AllowUiContractAfterForcedPageSpec to bypass this once.
    return !a9AllowUiContractAfterForcedPageSpec.value
  }

void shouldPreparePageSpecBeforeUi // A9.10.14.4.3.15.6.18_BUILD_TYPE_HOTFIX


  function resolveTaskUnavailableReason(
    taskType: AsyncRequirementTaskType,
    nodeUnavailableReason: string | null,
  ): string | null {
    if (nodeUnavailableReason) {
      return nodeUnavailableReason
    }

    if (taskType !== 'GENERATE_UI_CONTRACT') {
      return null
    }

    if (options.resolveUiContractReadinessLoading?.()) {
      return '正在检查交互原型生成条件，请稍候。'
    }

    const readiness =
      options.resolveUiContractReadiness?.() || null

    if (!readiness) {
      return '尚未完成交互原型生成准备度检查。'
    }

    if (!readiness.ready) {
      return readiness.blockers.length
        ? readiness.blockers.join('；')
        : '当前 PAGE 尚未满足交互原型生成条件。'
    }

    return null
  }

  async function handleGenerateArtifactTaskFromPanel(taskType: string) {
    if (!isAsyncRequirementTaskType(taskType)) {
      return
    }

    await startAsyncArtifactTask(taskType)
  }

  async function startFullChainTask(
    overwriteExisting = false,
  ) {
    const module = options.resolveSelectedModule()

    if (!module?.id) {
      return
    }

    const moduleName = options.resolveModuleDisplayName(module)
    const baseRequirementText = options.resolveRequirementText()

    isGeneratingFullChain.value = true

    try {
      options.setStepStatus('receive', 'success')
      options.setStepStatus('module-prd', 'running')
      options.setStepStatus('artifact', 'running')

      if (overwriteExisting) {
        options.clearAllArtifacts()
      }

      const response = await createFullChainTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        module.id,
        {
          requirementText:
            baseRequirementText && baseRequirementText.trim()
              ? baseRequirementText.trim()
              : `请为「${moduleName}」模块一键生成完整需求工程资产链路。`,
          overwriteExisting,
        },
      )

      await loadCurrentModuleAgentTasks()

      await options.onFullChainCreated?.(response)

      syncGeneratingFlagsFromTasks()
      syncStepStatusFromTasks()
      startTaskPolling()
    } catch (error) {
      console.error(error)
      options.setStepStatus('module-prd', 'failed')
      options.setStepStatus('artifact', 'failed')
    } finally {
      isGeneratingFullChain.value = false
    }
  }

  async function startAsyncArtifactTask(
    taskType: AsyncRequirementTaskType,
  ) {
    const module = options.resolveSelectedModule()

    if (!module?.id) {
      return
    }

    const unavailableReason = resolveTaskUnavailableReason(
      taskType,
      getRequirementTaskUnavailableReason(
        taskType,
        module,
      ),
    )

    if (unavailableReason) {
      await options.onTaskBlocked?.(
        unavailableReason,
        taskType,
      )
      return
    }

    // A9.10.14.4.3.12.1_TOKEN_GUARD_AND_PRD_UI_SYNC_PATCH_FRONTEND_ACTIVE_DEDUPE
    // Avoid duplicate token-burning task creation from repeated clicks or stale UI state.
    if (
      taskType === 'GENERATE_UI_CONTRACT' &&
      isTaskActive(moduleAgentTasks.value.GENERATE_UI_CONTRACT)
    ) {
      startTaskPolling()
      return
    }

    // A9.10.14.4.2.3_PAGE_SPEC_BEFORE_UI_FRONTEND_CHAIN
    // UI_CONTRACT has a PAGE_SPEC upstream contract, but PAGE_SPEC is intentionally
    // not exposed as the main user button. Prepare it automatically when missing.
    if (taskType === 'GENERATE_UI_CONTRACT' && !continuingUiContractAfterPageSpec.value) {
      pendingUiContractAfterPageSpecModuleId.value = module.id
      isGeneratingUiContract.value = true

      if (isTaskActive(moduleAgentTasks.value.GENERATE_PAGE_SPEC)) {
        startTaskPolling()
        return
      }

      await startAsyncArtifactTask('GENERATE_PAGE_SPEC')
      return
    }

    if (taskType === 'GENERATE_UI_CONTRACT' && continuingUiContractAfterPageSpec.value) {
      continuingUiContractAfterPageSpec.value = false
    }

    const definition = getRequirementArtifactDefinitionByTaskType(taskType)

    // A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_ASYNC_DEFINITION_GUARD

    if (!definition) {

      const message = `${taskType}任务定义不存在。`

      options.setStepStatus('artifact', 'failed')

      setGeneratingFlag(taskType, false)

      await options.onTaskFailed?.(message, taskType)

      return

    }
    const moduleName = options.resolveModuleDisplayName(module)

    setGeneratingFlag(taskType, true)

    try {
      options.setStepStatus('receive', 'success')
      options.setStepStatus(definition.stepId, 'running')
      options.setStepStatus('artifact', 'running')

      const response = await createAsyncArtifactTask(
        taskType,
        module.id,
        moduleName,
      )

      clearDownstreamArtifactsAndTasksBeforeAsyncRegenerate(
        definition.artifactType,
      )

      const task = await fetchAgentTask(response.taskId)

      moduleAgentTasks.value = {
        ...moduleAgentTasks.value,
        [task.taskType]: task,
      }

      await options.onTaskCreated?.(task, {
        artifactType: definition.artifactType,
        taskType,
        stepId: definition.stepId,
        title: definition.title,
      })

      syncGeneratingFlagsFromTasks()
      syncStepStatusFromTasks()
      startTaskPolling()
    } catch (error) {
      console.error(error)
      options.setStepStatus(definition.stepId, 'failed')
      options.setStepStatus('artifact', 'failed')
      setGeneratingFlag(taskType, false)

      await options.onTaskFailed?.(
        resolveTaskErrorMessage(
          error,
          `${definition.title}任务创建失败。`,
        ),
        taskType,
      )
    }
  }

  async function createAsyncArtifactTask(
    taskType: AsyncRequirementTaskType,
    moduleId: string,
    moduleName: string,
  ): Promise<AsyncAgentTaskResponse> {
    const themeProfile =
      taskType === 'GENERATE_UI_CONTRACT'
        ? options.resolveThemeProfile?.() || null
        : null

    const payload = {
      requirementText: buildAsyncRequirementTaskPrompt(
        taskType,
        moduleName,
      ),
      ...(['GENERATE_UI_CONTRACT', 'GENERATE_PAGE_SPEC'].includes(taskType)
        ? {
            forceRegenerate: true,
            // A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN
          }
        : {}),
      ...(themeProfile
        ? {
            themeProfile: normalizeThemeProfileForTaskPayload(themeProfile),
          }
        : {}),
    }

    if (taskType === 'GENERATE_MODULE_PRD') {
      return createModulePrdTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_BUSINESS_FLOW') {
      return createBusinessFlowTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_PAGE_SPEC') {
      return createPageSpecTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_DATA_MODEL') {
      return createDataModelTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_API_CONTRACT') {
      return createApiContractTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_TEST_SPEC') {
      return createTestSpecTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (taskType === 'GENERATE_UI_CONTRACT') {
      return createUiContractTaskAsync(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    return createDevHandoffPackageTaskAsync(
      options.resolveWorkspaceId(),
      options.resolveProjectCode(),
      moduleId,
      payload,
    )
  }

  function normalizeThemeProfileForTaskPayload(
    profile: ProjectThemeProfile,
  ): ProjectThemeProfile {
    return {
      ...profile,
      notes: profile.notes || null,
      secondaryColor: profile.secondaryColor || null,
      accentColor: profile.accentColor || null,
    }
  }

  async function handleCancelAgentTask(taskId: string) {
    try {
      await cancelAgentTask(taskId, '用户在模块工作区取消任务。')
      await loadCurrentModuleAgentTasks()
      syncGeneratingFlagsFromTasks()
      syncStepStatusFromTasks()
    } catch (error) {
      console.error(error)
    }
  }

  async function handleRetryAgentTask(taskId: string) {
    try {
      const task = await retryAgentTask(
        taskId,
        '用户在模块工作区重试任务。',
      )

      moduleAgentTasks.value = {
        ...moduleAgentTasks.value,
        [task.taskType]: task,
      }

      syncGeneratingFlagsFromTasks()
      syncStepStatusFromTasks()
      startTaskPolling()
    } catch (error) {
      console.error(error)
    }
  }

  async function loadCurrentModuleAgentTasks() {
    const module = options.resolveSelectedModule()

    if (!module?.id) {
      clearCurrentModuleAgentTasks()
      return
    }

    try {
      const projectId = options.resolveProjectId()

      if (!projectId) {
        clearCurrentModuleAgentTasks()
        return
      }

      const previousTasks = moduleAgentTasks.value
      const tasks = await fetchProjectAgentTasks(projectId, module.id)

      const asyncTaskTypes = ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.map(
        (definition) => definition.taskType,
      )

      const latestTasks: Record<string, AgentTaskSummary> = {}

      const scopedTasks = tasks
        .filter((task) => task.moduleId === module.id)
        .filter((task) =>
          asyncTaskTypes.includes(task.taskType as AsyncRequirementTaskType),
        )
        .sort((left, right) => {
          const leftTime = new Date(left.createdAt || '').getTime() || 0
          const rightTime = new Date(right.createdAt || '').getTime() || 0
          return rightTime - leftTime
        })

      for (const task of scopedTasks) {
        if (!latestTasks[task.taskType]) {
          latestTasks[task.taskType] = task
        }
      }

      moduleAgentTasks.value = latestTasks

      syncGeneratingFlagsFromTasks()
      syncStepStatusFromTasks()

      await notifyTaskTerminalStatusChanges(
        previousTasks,
        latestTasks,
      )
    } catch (error) {
      console.error(error)
    }
  }

  function clearCurrentModuleAgentTasks() {
    moduleAgentTasks.value = {}
    syncGeneratingFlagsFromTasks()
  }

  function startTaskPolling() {
    if (taskPollingTimer !== null) {
      return
    }

    taskPollingTimer = window.setInterval(() => {
      void pollCurrentModuleAgentTasks()
    }, 3000)
  }

  function stopTaskPolling() {
    if (taskPollingTimer === null) {
      return
    }

    window.clearInterval(taskPollingTimer)
    taskPollingTimer = null
  }

  async function pollCurrentModuleAgentTasks() {
    await loadCurrentModuleAgentTasks()
    await refreshArtifactsForFinishedTasks()
    await startPendingUiContractAfterPageSpecIfReady()

    if (!hasActiveModuleTasks()) {
      stopTaskPolling()
    }
  }

  async function startPendingUiContractAfterPageSpecIfReady() {
    const pendingModuleId = pendingUiContractAfterPageSpecModuleId.value
    const module = options.resolveSelectedModule()

    if (!pendingModuleId || !module?.id || module.id !== pendingModuleId) {
      return
    }

    if (isTaskActive(moduleAgentTasks.value.GENERATE_PAGE_SPEC)) {
      isGeneratingUiContract.value = true
      return
    }

    if (isTaskActive(moduleAgentTasks.value.GENERATE_UI_CONTRACT)) {
      return
    }

    const pageSpecTask = moduleAgentTasks.value.GENERATE_PAGE_SPEC

    if (!pageSpecTask) {
      return
    }

    if (!isSuccessTaskStatus(pageSpecTask.status)) {
      if (['FAILED', 'TIMEOUT', 'CANCELLED'].includes(pageSpecTask.status || '')) {
        pendingUiContractAfterPageSpecModuleId.value = null
        continuingUiContractAfterPageSpec.value = false
        a9AllowUiContractAfterForcedPageSpec.value = false
        isGeneratingUiContract.value = false
      }
      return
    }

    await options.loadArtifactByType('PAGE_SPEC', module)

    pendingUiContractAfterPageSpecModuleId.value = null
    continuingUiContractAfterPageSpec.value = true
    a9AllowUiContractAfterForcedPageSpec.value = true

    try {
      await startAsyncArtifactTask('GENERATE_UI_CONTRACT')
    } finally {
      a9AllowUiContractAfterForcedPageSpec.value = false
    }
  }

  async function refreshArtifactsForFinishedTasks() {
    const module = options.resolveSelectedModule()

    if (!module?.id) {
      return
    }

    for (const definition of ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS) {
      const task = moduleAgentTasks.value[definition.taskType]

      if (!task || !isSuccessTaskStatus(task.status)) {
        continue
      }

      const existingArtifact = options.getArtifactByType(definition.artifactType)

      if (
        existingArtifact?.artifactId &&
        task.resultArtifactId &&
        existingArtifact.artifactId === task.resultArtifactId
      ) {
        continue
      }

      await options.loadArtifactByType(definition.artifactType, module)
    }
  }

  function clearDownstreamArtifactsAndTasksBeforeAsyncRegenerate(
    artifactType: RequirementArtifactType,
  ) {
    options.clearArtifactsAfterArtifact(artifactType)

    const definition =
      ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.find(
        (item) => item.artifactType === artifactType,
      )

    if (!definition) {
      return
    }

    const downstreamAsyncTaskTypes =
      ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS
        .filter((item) =>
          definition.downstreamArtifactTypes.includes(item.artifactType),
        )
        .map((item) => item.taskType)

    clearModuleAgentTasks(...downstreamAsyncTaskTypes)

    for (const downstreamDefinition of ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS) {
      if (
        definition.downstreamArtifactTypes.includes(
          downstreamDefinition.artifactType,
        )
      ) {
        options.setStepStatus(downstreamDefinition.stepId, 'pending')
      }
    }
  }

  function clearModuleAgentTasks(
    ...taskTypes: AsyncRequirementTaskType[]
  ) {
    const nextTasks = {
      ...moduleAgentTasks.value,
    }

    for (const taskType of taskTypes) {
      delete nextTasks[taskType]
    }

    moduleAgentTasks.value = nextTasks
  }

  function syncGeneratingFlagsFromTasks() {
    isGeneratingPrd.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_MODULE_PRD,
    )
    isGeneratingBusinessFlow.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_BUSINESS_FLOW,
    )
    isGeneratingPageSpec.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_PAGE_SPEC,
    )
    isGeneratingDataModel.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_DATA_MODEL,
    )
    isGeneratingApiContract.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_API_CONTRACT,
    )
    isGeneratingTestSpec.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_TEST_SPEC,
    )
    isGeneratingUiContract.value =
      isTaskActive(moduleAgentTasks.value.GENERATE_UI_CONTRACT) ||
      Boolean(
        pendingUiContractAfterPageSpecModuleId.value &&
          isTaskActive(moduleAgentTasks.value.GENERATE_PAGE_SPEC),
      )
    isGeneratingDevHandoffPackage.value = isTaskActive(
      moduleAgentTasks.value.GENERATE_DEV_HANDOFF_PACKAGE,
    )
  }

  function syncStepStatusFromTasks() {
    let hasRunningTask = false
    let hasFailedTask = false
    let hasSuccessTask = false

    for (const definition of ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS) {
      const task = moduleAgentTasks.value[definition.taskType]

      if (!task) {
        continue
      }

      if (isTaskActive(task)) {
        hasRunningTask = true
        options.setStepStatus(definition.stepId, 'running')
        continue
      }

      if (isSuccessTaskStatus(task.status)) {
        hasSuccessTask = true
        options.setStepStatus(definition.stepId, 'success')
        continue
      }

      if (['FAILED', 'TIMEOUT'].includes(task.status)) {
        hasFailedTask = true
        options.setStepStatus(definition.stepId, 'failed')
        continue
      }

      if (task.status === 'CANCELLED') {
        options.setStepStatus(definition.stepId, 'pending')
      }
    }

    if (hasRunningTask) {
      options.setStepStatus('artifact', 'running')
      return
    }

    if (hasFailedTask) {
      options.setStepStatus('artifact', 'failed')
      return
    }

    if (hasSuccessTask) {
      options.setStepStatus('artifact', 'success')
    }
  }

  async function notifyTaskTerminalStatusChanges(
    previousTasks: Record<string, AgentTaskSummary | null>,
    latestTasks: Record<string, AgentTaskSummary | null>,
  ) {
    if (!options.onTaskTerminalStatusChanged) {
      return
    }

    const module = options.resolveSelectedModule()

    for (const definition of ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS) {
      const task = latestTasks[definition.taskType]
      const previousTask = previousTasks[definition.taskType]

      if (!task || !isNotifiableTerminalTaskStatus(task.status)) {
        continue
      }

      if (!previousTask || previousTask.id !== task.id) {
        continue
      }

      if (!isTaskActiveStatus(previousTask.status)) {
        continue
      }

      const notificationKey = `${task.id}:${task.status}`

      if (notifiedTerminalTaskKeys.has(notificationKey)) {
        continue
      }

      notifiedTerminalTaskKeys.add(notificationKey)

      await options.onTaskTerminalStatusChanged({
        task,
        previousStatus: previousTask.status,
        definition: {
          artifactType: definition.artifactType,
          taskType: definition.taskType,
          stepId: definition.stepId,
          title: getRequirementArtifactTitleForNode(
            definition.artifactType,
            module,
          ),
        },
      })
    }
  }

  function hasActiveModuleTasks(): boolean {
    return Object.values(moduleAgentTasks.value).some((task) =>
      isTaskActive(task),
    )
  }

  function isTaskActive(
    task?: AgentTaskSummary | null,
  ): boolean {
    if (!task) {
      return false
    }

    return isTaskActiveStatus(task.status)
  }

  function isTaskActiveStatus(
    status?: string | null,
  ): boolean {
    return ['QUEUED', 'RUNNING', 'RETRYING', 'BLOCKED'].includes(status || '')
  }

  function isNotifiableTerminalTaskStatus(
    status?: string | null,
  ): boolean {
    return [
      'SUCCESS',
      'PARTIAL_SUCCESS',
      'FAILED',
      'TIMEOUT',
      'CANCELLED',
    ].includes(status || '')
  }

  function isSuccessTaskStatus(
    status?: string | null,
  ): boolean {
    return status === 'SUCCESS' || status === 'PARTIAL_SUCCESS'
  }

  function setGeneratingFlag(
    taskType: AsyncRequirementTaskType,
    value: boolean,
  ) {
    if (taskType === 'GENERATE_MODULE_PRD') {
      isGeneratingPrd.value = value
      return
    }

    if (taskType === 'GENERATE_BUSINESS_FLOW') {
      isGeneratingBusinessFlow.value = value
      return
    }

    if (taskType === 'GENERATE_PAGE_SPEC') {
      isGeneratingPageSpec.value = value
      return
    }

    if (taskType === 'GENERATE_DATA_MODEL') {
      isGeneratingDataModel.value = value
      return
    }

    if (taskType === 'GENERATE_API_CONTRACT') {
      isGeneratingApiContract.value = value
      return
    }

    if (taskType === 'GENERATE_TEST_SPEC') {
      isGeneratingTestSpec.value = value
      return
    }

    if (taskType === 'GENERATE_UI_CONTRACT') {
      isGeneratingUiContract.value = value
      return
    }

    isGeneratingDevHandoffPackage.value = value
  }

  function isAsyncRequirementTaskType(
    value: string,
  ): value is AsyncRequirementTaskType {
    return ASYNC_REQUIREMENT_ARTIFACT_DEFINITIONS.some(
      (definition) => definition.taskType === value,
    )
  }

  function resolveTaskErrorMessage(
    error: unknown,
    fallback: string,
  ): string {
    if (error instanceof Error && error.message) {
      return error.message
    }

    return fallback
  }

  onBeforeUnmount(() => {
    stopTaskPolling()
  })

  return {
    moduleAgentTasks,
    moduleArtifactTaskItems,

    isGeneratingPrd,
    isGeneratingBusinessFlow,
    isGeneratingPageSpec,
    isGeneratingDataModel,
    isGeneratingApiContract,
    isGeneratingTestSpec,
    isGeneratingUiContract,
    isGeneratingDevHandoffPackage,
    isGeneratingFullChain,

    handleGenerateArtifactTaskFromPanel,
    startAsyncArtifactTask,
    startFullChainTask,
    handleCancelAgentTask,
    handleRetryAgentTask,

    loadCurrentModuleAgentTasks,
    clearCurrentModuleAgentTasks,
    startTaskPolling,
    stopTaskPolling,
    pollCurrentModuleAgentTasks,
    refreshArtifactsForFinishedTasks,

    syncGeneratingFlagsFromTasks,
    syncStepStatusFromTasks,
    hasActiveModuleTasks,
    isTaskActive,
    isSuccessTaskStatus,
  }
}

