<template>
  <div class="app-shell">
    <AppHeader :active-tab="activeTab" :project-code="project.projectCode" @change-tab="handleChangeAgentTab" />

    <div class="workspace-topbar">
      <div class="workspace-topbar-left">
        <button class="back-home-button" @click="$emit('backHome')">
          ← 返回项目首页
        </button>

        <div>
          <strong>{{ project.projectName }}</strong>
          <span>{{ workspace.workspaceName }} / {{ project.projectCode }}</span>
        </div>
      </div>

      <button class="logout-button" @click="openProjectTaskCenter">
        项目任务中心
      </button>

      <button class="logout-button" @click="handleExportProjectZip">
        导出项目交付包
      </button>

      <button class="logout-button" @click="handleLogout">
        退出登录
      </button>
    </div>

    <div class="main-layout with-workspace-topbar">
      <ModuleTreePanel :modules="modules" :selected-module-id="selectedModule?.id" @select="handleSelectModule"
        @create-root="handleCreateRootModuleNode" @create-child="handleCreateChildModuleNode"
        @rename-node="handleRenameModuleNode" @move-node="handleMoveModuleNode" @delete-node="handleDeleteModuleNode" />

      <WorkspacePanel :selected-module="selectedModule" :selected-artifact-type="selectedArtifactType"
        :is-generating-prd="isGeneratingPrd" :is-generating-business-flow="isGeneratingBusinessFlow"
        :is-generating-page-spec="isGeneratingPageSpec" :is-generating-data-model="isGeneratingDataModel"
        :is-generating-api-contract="isGeneratingApiContract" :is-generating-test-spec="isGeneratingTestSpec"
        :is-generating-dev-handoff-package="isGeneratingDevHandoffPackage" :module-prd-markdown="modulePrdMarkdown"
        :is-generating-full-chain="isGeneratingFullChain" :prd-artifact="prdArtifact"
        :business-flow-markdown="businessFlowMarkdown" :business-flow-artifact="businessFlowArtifact"
        :page-spec-markdown="pageSpecMarkdown" :page-spec-artifact="pageSpecArtifact"
        :data-model-markdown="dataModelMarkdown" :data-model-artifact="dataModelArtifact"
        :api-contract-markdown="apiContractMarkdown" :api-contract-artifact="apiContractArtifact"
        :test-spec-markdown="testSpecMarkdown" :test-spec-artifact="testSpecArtifact"
        :is-generating-ui-contract="isGeneratingUiContract" :ui-contract-markdown="uiContractMarkdown"
        :ui-contract-artifact="uiContractArtifact" :dev-handoff-package-markdown="devHandoffPackageMarkdown"
        :dev-handoff-package-artifact="devHandoffPackageArtifact" :artifact-task-items="moduleArtifactTaskItems"
        @view-artifact="handleViewArtifact" @generate-full-chain="handleGenerateFullChain"
        @generate-module-prd="handleGenerateModulePrd" @generate-business-flow="handleGenerateBusinessFlow"
        @generate-page-spec="handleGeneratePageSpec" @generate-data-model="handleGenerateDataModel"
        @generate-api-contract="handleGenerateApiContract" @generate-test-spec="handleGenerateTestSpec"
        @generate-dev-handoff-package="handleGenerateDevHandoffPackage"
        @generate-artifact-task="handleGenerateArtifactTaskFromPanel" @cancel-artifact-task="handleCancelAgentTask"
        @retry-artifact-task="handleRetryAgentTask" @open-artifact-versions="handleOpenArtifactVersions"
        @open-impact-analysis="handleOpenImpactAnalysis" @export-artifacts-zip="handleExportArtifactsZip"
        @export-artifacts-docx="handleExportArtifactsDocx" @open-quality-check="handleOpenQualityCheck"
        @generate-ui-contract="handleGenerateUiContract" @preview-ui-contract="handlePreviewUiContract" />

      <RightAiAssistantPanel v-model:collapsed="aiAssistantCollapsed" :module-tree-exists="moduleTreeExists"
        :is-generating-tree="isGeneratingTree" :is-sending-message="isSendingAssistantMessage"
        :is-applying-tree-patch="isApplyingTreePatch" :assistant-response="latestAssistantResponse"
        :history-messages="assistantHistoryMessages"
        :upload-attachment="handleUploadAssistantAttachment" @send="handleAssistantSend"
        @clear-conversation="handleClearAssistantConversation"
        @clear-proposal="treePatchProposal = null" @apply-proposal="handleApplyTreePatchProposal" />

      <ArtifactVersionHistoryDrawer :visible="artifactVersionDrawerVisible" :loading="artifactVersionLoading"
        :rollback-loading="artifactVersionRollbackLoading" :artifact-title="artifactVersionTitle"
        :versions="artifactVersionSummaries" :selected-version="selectedArtifactVersion" :error="artifactVersionError"
        @close="closeArtifactVersionDrawer" @refresh="loadArtifactVersionHistory"
        @view-version="handleViewArtifactVersion" @rollback-version="handleRollbackArtifactVersion"
        @submit-review="handleSubmitArtifactReview" @approve-version="handleApproveArtifactVersion"
        @reject-version="handleRejectArtifactVersion" @archive-version="handleArchiveArtifactVersion" />

      <ArtifactImpactAnalysisDrawer :visible="impactAnalysisDrawerVisible" :submitting="impactAnalysisSubmitting"
        :source-title="impactAnalysisSourceTitle" :source-artifact-type="impactAnalysisSourceArtifactType"
        :items="impactAnalysisItems" :selected-artifact-types="impactAnalysisSelectedTypes" :error="impactAnalysisError"
        @close="closeImpactAnalysisDrawer" @select-all="selectAllImpactedArtifacts"
        @toggle-artifact="handleToggleImpactedArtifact" @regenerate="handleRegenerateImpactedArtifacts" />

      <ArtifactQualityCheckDrawer :visible="qualityCheckDrawerVisible"
        :module-name="selectedModule ? getModuleDisplayName(selectedModule) : '当前模块'"
        :result="artifactQualityCheckResult" @close="closeQualityCheckDrawer" @export-zip="handleExportArtifactsZip" />

      <UiContractPreviewDrawer :visible="uiContractPreviewVisible" :artifact="uiContractArtifact"
        @close="uiContractPreviewVisible = false" />

      <ModuleTreeNodeEditorDialog :visible="nodeEditorVisible" :mode="nodeEditorMode" :parent-node="nodeEditorParent"
        :editing-node="nodeEditorTarget" @cancel="closeNodeEditor" @submit="handleSubmitNodeEditor" />

      <ModuleTreeNodeMoveDialog :visible="nodeMoveDialogVisible" :modules="modules" :moving-node="nodeMoveTarget"
        @cancel="closeNodeMoveDialog" @submit="handleSubmitMoveModuleNode" />

      <ModuleTreeDeleteConfirmDialog :visible="nodeDeleteDialogVisible" :node="nodeDeleteTarget"
        @cancel="closeNodeDeleteDialog" @confirm="handleConfirmDeleteModuleNode" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import ModuleTreePanel from '../components/ModuleTreePanel.vue'
import WorkspacePanel from '../components/WorkspacePanel.vue'
import ArtifactVersionHistoryDrawer from '../components/ArtifactVersionHistoryDrawer.vue'
import ArtifactImpactAnalysisDrawer from '../components/ArtifactImpactAnalysisDrawer.vue'
import ArtifactQualityCheckDrawer from '../components/ArtifactQualityCheckDrawer.vue'
import UiContractPreviewDrawer from '../components/UiContractPreviewDrawer.vue'
import ModuleTreeNodeEditorDialog from '../components/ModuleTreeNodeEditorDialog.vue'
import ModuleTreeNodeMoveDialog from '../components/ModuleTreeNodeMoveDialog.vue'
import ModuleTreeDeleteConfirmDialog from '../components/ModuleTreeDeleteConfirmDialog.vue'
import RightAiAssistantPanel from '../components/RightAiAssistantPanel.vue'
import { useRouter } from 'vue-router'
import {
  clearAuthStorage,
  createProjectModuleNode,
  deleteProjectModuleNode,
  fetchProjectModules,
  generateModuleTree,
  getModuleDisplayName,
  updateProjectModuleNode,
  moveProjectModuleNode,
  applyModuleTreePatch,
  archiveAssistantMessages,
  createAssistantTaskNotification,
  fetchAssistantMessages,
  sendAssistantMessage,
  uploadAssistantAttachment,
} from '../api'

import {
  approveModuleArtifactVersion,
  archiveModuleArtifactVersion,
  downloadModuleArtifactExportDocx,
  downloadModuleArtifactExportZip,
  downloadProjectArtifactExportZip,
  fetchModuleArtifactVersion,
  fetchModuleArtifactVersions,
  rejectModuleArtifactVersion,
  rollbackModuleArtifactVersion,
  submitModuleArtifactReview,
} from '../api/artifactApi'

import {
  isRequirementArtifactType,
  REQUIREMENT_ARTIFACT_DEFINITIONS,
  type AsyncRequirementTaskType,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'
import { useAssistantSteps } from '../composables/useAssistantSteps'
import { useAsyncArtifactTasks } from '../composables/useAsyncArtifactTasks'
import {
  getArtifactModuleId,
  useModuleArtifacts,
} from '../composables/useModuleArtifacts'
import { useModuleTreeSelection } from '../composables/useModuleTreeSelection'
import {
  normalizeAgentTab,
  useProjectWorkspacePersistence,
} from '../composables/useProjectWorkspacePersistence'

import type {
  AgentTab,
  ArtifactResponse,
  ArtifactVersionDetailResponse,
  ArtifactVersionSummaryResponse,
  AgentTaskSummary,
  AssistantAttachmentRef,
  AssistantConversationMessageResponse,
  AssistantMessageResponse,
  ModuleTreeNode,
  ModuleTreePatchProposalResponse,
  ProjectSummary,
  WorkspaceSummary,
} from '../types/codemind'

const router = useRouter()

const props = defineProps<{
  workspace: WorkspaceSummary
  project: ProjectSummary
}>()

defineEmits<{
  backHome: []
}>()

const activeTab = ref<AgentTab>('requirement')
const modules = ref<ModuleTreeNode[]>([])
const aiAssistantCollapsed = ref(false)
const latestAssistantResponse = ref<AssistantMessageResponse | null>(null)
const assistantHistoryMessages = ref<AssistantConversationMessageResponse[]>([])
const treePatchProposal = ref<ModuleTreePatchProposalResponse | null>(null)
const isSendingAssistantMessage = ref(false)
const isApplyingTreePatch = ref(false)

const moduleTreeExists = computed(() => modules.value.length > 0)
const requirementText = ref('我要做一个电商系统，包含用户、订单、支付、库存、后台管理、权限控制等模块。')
const selectedArtifactType = ref<RequirementArtifactType | null>(null)
const artifactVersionDrawerVisible = ref(false)
const artifactVersionLoading = ref(false)
const artifactVersionRollbackLoading = ref(false)
const artifactVersionError = ref('')
const artifactVersionArtifactType = ref<RequirementArtifactType | null>(null)
const artifactVersionSummaries = ref<ArtifactVersionSummaryResponse[]>([])
const selectedArtifactVersion = ref<ArtifactVersionDetailResponse | null>(null)

const uiContractPreviewVisible = ref(false)
const qualityCheckDrawerVisible = ref(false)
const impactAnalysisDrawerVisible = ref(false)
const impactAnalysisSubmitting = ref(false)
const impactAnalysisError = ref('')
const impactAnalysisSourceArtifactType = ref<RequirementArtifactType | null>(null)
const impactAnalysisSelectedTypes = ref<RequirementArtifactType[]>([])

const nodeEditorVisible = ref(false)
const nodeEditorMode = ref<'create-root' | 'create-child' | 'edit'>('create-root')
const nodeEditorParent = ref<ModuleTreeNode | null>(null)
const nodeEditorTarget = ref<ModuleTreeNode | null>(null)

const nodeMoveDialogVisible = ref(false)
const nodeMoveTarget = ref<ModuleTreeNode | null>(null)

const nodeDeleteDialogVisible = ref(false)
const nodeDeleteTarget = ref<ModuleTreeNode | null>(null)

const artifactVersionTitle = computed(() => {
  if (!artifactVersionArtifactType.value) {
    return '工程资产'
  }

  return REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.artifactType === artifactVersionArtifactType.value,
  )?.title || artifactVersionArtifactType.value
})

const isGeneratingTree = ref(false)

const {
  resetSteps,
  setStepStatus,
} = useAssistantSteps()

const {
  selectedModule,
  selectModule,
  clearSelectedModule,
  restoreSelectedModuleById,
} = useModuleTreeSelection()

const {
  loadPersistedWorkspaceState,
  saveCurrentWorkspaceState,
} = useProjectWorkspacePersistence(
  () => props.workspace.workspaceId,
  () => props.project.projectCode,
)

const {
  prdArtifact,
  modulePrdMarkdown,
  businessFlowArtifact,
  businessFlowMarkdown,
  pageSpecArtifact,
  pageSpecMarkdown,
  dataModelArtifact,
  dataModelMarkdown,
  apiContractArtifact,
  apiContractMarkdown,
  testSpecArtifact,
  testSpecMarkdown,
  devHandoffPackageArtifact,
  devHandoffPackageMarkdown,

  loadAllCurrentModuleArtifacts,
  loadCurrentArtifactByType,
  loadLatestProjectArtifactByType,

  getArtifactByType,
  clearArtifacts,
  clearArtifactsAfterArtifact,

  uiContractArtifact,
  uiContractMarkdown,
  getCurrentSelectedArtifactType,
  getCurrentSelectedArtifactId,
} = useModuleArtifacts(
  () => props.workspace.workspaceId,
  () => props.project.projectCode,
  setStepStatus,
)

const {
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
  refreshArtifactsForFinishedTasks,
  hasActiveModuleTasks,
} = useAsyncArtifactTasks({
  resolveWorkspaceId: () => props.workspace.workspaceId,
  resolveProjectCode: () => props.project.projectCode,
  resolveProjectId: () => props.project.projectId,
  resolveSelectedModule: () => selectedModule.value,
  resolveRequirementText: () => requirementText.value,
  resolveModuleDisplayName: getModuleDisplayName,
  getArtifactByType,
  loadArtifactByType: (artifactType, module) =>
    loadCurrentArtifactByType(module, artifactType),
  clearArtifactsAfterArtifact,
  clearAllArtifacts: clearArtifacts,
  setStepStatus,
  onTaskCreated: async (task, definition) => {
    selectedArtifactType.value = definition.artifactType

    await saveCurrentWorkspaceState({
      selectedModuleId: selectedModule.value?.id || null,
      selectedArtifactType: definition.artifactType,
      selectedArtifactId: task.resultArtifactId || null,
      activeAgentTab: activeTab.value,
      uiState: {
        lastAction: `create-${definition.taskType.toLowerCase()}`,
        taskId: task.id,
      },
    })
  },

  onFullChainCreated: async (response) => {
    selectedArtifactType.value = 'MODULE_PRD'

    await saveCurrentWorkspaceState({
      selectedModuleId: selectedModule.value?.id || null,
      selectedArtifactType: selectedArtifactType.value,
      selectedArtifactId: null,
      activeAgentTab: activeTab.value,
      uiState: {
        lastAction: 'create-full-chain',
        createdTaskCount: response.createdTasks.length,
        reusedTaskCount: response.reusedTasks.length,
        skippedArtifactCount: response.skippedArtifacts.length,
      },
    })
  },

  onTaskTerminalStatusChanged: handleAssistantTaskTerminalNotification,
})

const impactAnalysisSourceTitle = computed(() => {
  if (!impactAnalysisSourceArtifactType.value) {
    return '工程资产'
  }

  return REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.artifactType === impactAnalysisSourceArtifactType.value,
  )?.title || impactAnalysisSourceArtifactType.value
})

const impactAnalysisItems = computed(() => {
  if (!impactAnalysisSourceArtifactType.value) {
    return []
  }

  const sourceDefinition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.artifactType === impactAnalysisSourceArtifactType.value,
  )

  if (!sourceDefinition) {
    return []
  }

  return sourceDefinition.downstreamArtifactTypes
    .map((artifactType) => {
      const definition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
        (item) => item.artifactType === artifactType,
      )

      if (!definition) {
        return null
      }

      const artifact = getArtifactByType(artifactType)

      const taskItem = moduleArtifactTaskItems.value.find(
        (item) => item.artifactType === artifactType,
      )

      const taskStatus = taskItem?.task?.status || null

      return {
        artifactType,
        taskType: definition.taskType,
        title: definition.title,
        description: definition.description,
        artifactExists: Boolean(
          artifact?.artifactId ||
          artifact?.exists ||
          taskItem?.artifactExists,
        ),
        versionNo: artifact?.versionNo || taskItem?.artifactVersionNo || null,
        status: artifact?.status || null,
        taskStatus,
        hasActiveTask: ['QUEUED', 'RUNNING', 'RETRYING'].includes(
          taskStatus || '',
        ),
      }
    })
    .filter((item): item is NonNullable<typeof item> => Boolean(item))
})

const artifactQualityCheckResult = computed(() => {
  const assets = REQUIREMENT_ARTIFACT_DEFINITIONS.map((definition) => {
    const artifact = getArtifactByType(definition.artifactType)
    const markdown = getArtifactMarkdownByType(definition.artifactType)
    const exists = Boolean(
      artifact?.artifactId ||
      artifact?.exists ||
      artifact?.content ||
      artifact?.contentMarkdown ||
      markdown,
    )

    const contentOk = hasMeaningfulArtifactContent(
      artifact,
      markdown,
    )

    const status = artifact?.status || null

    let level: 'pass' | 'warning' | 'blocker' = 'pass'
    let statusText = '已生成'

    if (!exists) {
      level = 'blocker'
      statusText = '缺失'
    } else if (!contentOk) {
      level = 'blocker'
      statusText = '内容为空'
    } else if (status === 'REJECTED') {
      level = 'blocker'
      statusText = '已退回'
    } else if (status === 'DRAFT' || status === 'ACTIVE' || status === 'PENDING_REVIEW') {
      level = 'warning'
      statusText = status === 'PENDING_REVIEW' ? '待审核' : '未确认'
    } else if (status === 'APPROVED') {
      level = 'pass'
      statusText = '已确认'
    } else {
      level = 'warning'
      statusText = status || '状态未知'
    }

    if (artifact?.current === false && level !== 'blocker') {
      level = 'warning'
      statusText = '非当前版本'
    }

    return {
      artifactType: definition.artifactType,
      title: definition.title,
      exists,
      versionNo: artifact?.versionNo || null,
      status,
      current: artifact?.current,
      updatedAt: artifact?.updatedAt || null,
      level,
      statusText,
    }
  })

  const blockers = []
  const warnings = []
  const passes = []

  for (const asset of assets) {
    if (!asset.exists) {
      blockers.push({
        id: `missing-${asset.artifactType}`,
        level: 'blocker' as const,
        title: `${asset.title}缺失`,
        description: `当前模块尚未生成${asset.title}，会影响完整交付包。`,
        artifactType: asset.artifactType,
      })
      continue
    }

    if (asset.status === 'REJECTED') {
      blockers.push({
        id: `rejected-${asset.artifactType}`,
        level: 'blocker' as const,
        title: `${asset.title}已退回`,
        description: `${asset.title}当前处于已退回状态，建议修改并重新提交审核。`,
        artifactType: asset.artifactType,
      })
      continue
    }

    if (asset.level === 'blocker') {
      blockers.push({
        id: `empty-${asset.artifactType}`,
        level: 'blocker' as const,
        title: `${asset.title}内容不可用`,
        description: `${asset.title}存在空内容或占位内容，无法作为正式交付依据。`,
        artifactType: asset.artifactType,
      })
      continue
    }

    if (asset.level === 'warning') {
      warnings.push({
        id: `status-${asset.artifactType}`,
        level: 'warning' as const,
        title: `${asset.title}尚未确认`,
        description: `${asset.title}当前状态为“${asset.statusText}”，建议确认通过后再交付。`,
        artifactType: asset.artifactType,
      })
    } else {
      passes.push({
        id: `pass-${asset.artifactType}`,
        level: 'pass' as const,
        title: `${asset.title}已生成`,
        description: `${asset.title}存在有效内容，可以进入交付检查。`,
        artifactType: asset.artifactType,
      })
    }
  }

  for (const definition of REQUIREMENT_ARTIFACT_DEFINITIONS) {
    const artifact = getArtifactByType(definition.artifactType)

    if (!artifact?.updatedAt) {
      continue
    }

    for (const upstreamType of definition.upstreamArtifactTypes) {
      const upstreamArtifact = getArtifactByType(upstreamType)

      if (!upstreamArtifact?.updatedAt) {
        continue
      }

      const currentUpdatedAt = Date.parse(artifact.updatedAt)
      const upstreamUpdatedAt = Date.parse(upstreamArtifact.updatedAt)

      if (
        Number.isFinite(currentUpdatedAt) &&
        Number.isFinite(upstreamUpdatedAt) &&
        upstreamUpdatedAt > currentUpdatedAt
      ) {
        const upstreamTitle = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
          (item) => item.artifactType === upstreamType,
        )?.title || upstreamType

        warnings.push({
          id: `stale-${definition.artifactType}-${upstreamType}`,
          level: 'warning' as const,
          title: `${definition.title}可能已滞后`,
          description: `${upstreamTitle}的更新时间晚于${definition.title}，建议重新生成${definition.title}以保持链路一致。`,
          artifactType: definition.artifactType,
        })
      }
    }
  }

  const score = Math.max(
    0,
    100 - blockers.length * 18 - warnings.length * 6,
  )

  return {
    score,
    generatedAt: new Date().toLocaleString(),
    assets,
    blockers,
    warnings,
    passes,
  }
})

watch(
  () => [
    props.workspace.workspaceId,
    props.project.projectCode,
  ],
  async () => {
    await loadProjectWorkspace()
  },
  {
    immediate: true,
  },
)

async function loadProjectWorkspace() {
  stopTaskPolling()
  resetWorkspaceState()

  await loadAssistantMessageHistory()

  modules.value = await fetchProjectModules(
    props.workspace.workspaceId,
    props.project.projectCode,
  )

  await restoreProjectWorkspaceState()
}

async function loadAssistantMessageHistory() {
  try {
    assistantHistoryMessages.value = await fetchAssistantMessages(
      props.workspace.workspaceId,
      props.project.projectCode,
      100,
    )
  } catch (error) {
    console.error(error)
    assistantHistoryMessages.value = []
  }
}

async function handleClearAssistantConversation() {
  const confirmed = window.confirm(
    '确定要清空当前项目的 AI Assistant 对话视图吗？\n\n' +
      '该操作只会隐藏当前对话记录，不会删除项目审计记录和任务记录。',
  )

  if (!confirmed) {
    return
  }

  try {
    await archiveAssistantMessages(
      props.workspace.workspaceId,
      props.project.projectCode,
    )

    assistantHistoryMessages.value = []
    latestAssistantResponse.value = null
    treePatchProposal.value = null
  } catch (error) {
    window.alert(resolveErrorMessage(error, '清空 AI Assistant 对话失败。'))
  }
}

async function handleAssistantTaskTerminalNotification(payload: {
  task: AgentTaskSummary
  previousStatus?: string | null
  definition: {
    artifactType: RequirementArtifactType
    taskType: AsyncRequirementTaskType
    stepId: string
    title: string
  }
}) {
  const task = payload.task

  if (!isAssistantTaskNotificationStatus(task.status)) {
    return
  }

  try {
    const response = await createAssistantTaskNotification(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        taskId: task.id,
        moduleId: task.moduleId || selectedModule.value?.id || null,
        resultArtifactId: task.resultArtifactId || null,
        taskType: task.taskType,
        artifactType: payload.definition.artifactType,
        artifactTitle: payload.definition.title,
        status: task.status,
        errorCode: task.errorCode || task.lastErrorCode || null,
        errorMessage: task.errorMessage || task.lastErrorMessage || null,
      },
    )

    latestAssistantResponse.value = response
  } catch (error) {
    console.error(error)
  }
}

function isAssistantTaskNotificationStatus(
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

async function restoreProjectWorkspaceState() {
  const state = await loadPersistedWorkspaceState()

  const persistedTab = normalizeAgentTab(state?.activeAgentTab)

  if (persistedTab) {
    activeTab.value = persistedTab
  }

  if (state?.exists && state.selectedModuleId) {
    const matchedModule = restoreSelectedModuleById(
      modules.value,
      state.selectedModuleId,
    )

    if (matchedModule) {
      await loadAllCurrentModuleArtifacts(matchedModule)
      await loadCurrentModuleAgentTasks()

      if (state.selectedArtifactType && isRequirementArtifactType(state.selectedArtifactType)) {
        selectedArtifactType.value = state.selectedArtifactType
      } else {
        selectedArtifactType.value = getCurrentSelectedArtifactType()
      }

      if (hasActiveModuleTasks()) {
        startTaskPolling()
      }

      if (selectedArtifactType.value || getCurrentSelectedArtifactType()) {
        return
      }
    }
  }

  await restoreLatestModulePrd()
}

async function restoreLatestModulePrd() {
  const artifact = await loadLatestProjectArtifactByType('MODULE_PRD')
  const artifactModuleId = getArtifactModuleId(artifact)

  if (!artifact?.exists || !artifactModuleId) {
    selectedArtifactType.value = null
    return
  }

  const matchedModule = restoreSelectedModuleById(
    modules.value,
    artifactModuleId,
  )

  if (!matchedModule) {
    selectedArtifactType.value = null
    return
  }

  await loadAllCurrentModuleArtifacts(matchedModule)
  await loadCurrentModuleAgentTasks()

  selectedArtifactType.value = getCurrentSelectedArtifactType()

  if (hasActiveModuleTasks()) {
    startTaskPolling()
  }

  await saveCurrentWorkspaceState({
    selectedModuleId: artifactModuleId,
    selectedArtifactType: selectedArtifactType.value,
    selectedArtifactId: getCurrentSelectedArtifactId(),
    activeAgentTab: activeTab.value,
    uiState: {
      restoredFrom: 'latest-module-prd',
    },
  })

  setStepStatus('module-prd', 'success')
}

async function handleGenerateModuleTree(requirementTextOverride?: string) {
  const moduleTreeRequirementText = requirementTextOverride?.trim() || requirementText.value

  isGeneratingTree.value = true
  stopTaskPolling()
  resetSteps()

  try {
    setStepStatus('receive', 'success')
    setStepStatus('module-tree', 'running')

    await generateModuleTree(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementText: moduleTreeRequirementText,
      },
    )

    setStepStatus('module-tree', 'success')
    setStepStatus('artifact', 'running')

    modules.value = await fetchProjectModules(
      props.workspace.workspaceId,
      props.project.projectCode,
    )

    clearSelectedModuleAndArtifacts()

    await saveCurrentWorkspaceState({
      selectedModuleId: null,
      selectedArtifactType: null,
      selectedArtifactId: null,
      activeAgentTab: activeTab.value,
      uiState: {
        lastAction: 'generate-module-tree',
      },
    })

    setStepStatus('artifact', 'success')
  } catch (error) {
    console.error(error)
    setStepStatus('module-tree', 'failed')
    setStepStatus('artifact', 'failed')
  } finally {
    isGeneratingTree.value = false
  }
}

function resolveAssistantActionRequirementText(
  response: AssistantMessageResponse,
): string {
  const action = response.actions?.find(
    (item) => item.type === 'GENERATE_MODULE_TREE',
  )

  const value = action?.payload?.requirementText

  return typeof value === 'string' ? value : ''
}

async function handleUploadAssistantAttachment(
  file: File,
): Promise<AssistantAttachmentRef> {
  const response = await uploadAssistantAttachment(
    props.workspace.workspaceId,
    props.project.projectCode,
    file,
  )

  return {
    attachmentId: response.attachmentId,
    fileName: response.fileName,
    mimeType: response.mimeType,
    size: response.size,
    parseStatus: response.parseStatus,
    parsedTextExcerpt: response.parsedTextExcerpt || null,
    metadata: response.metadata || {},
  }
}

async function handleAssistantSend(payload: {
  text: string
  attachments?: AssistantAttachmentRef[]
}) {
  const trimmedText = payload.text.trim()

  if (!trimmedText) {
    return
  }

  requirementText.value = trimmedText
  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const response = await sendAssistantMessage(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        message: trimmedText,
        selectedNodeId: selectedModule.value?.id || null,
        selectedArtifactType: selectedArtifactType.value || null,
        attachments: payload.attachments || [],
      },
    )

    latestAssistantResponse.value = response
    await loadAssistantMessageHistory()

    if (response.intent === 'GENERATE_MODULE_TREE') {
      await handleGenerateModuleTree(
        resolveAssistantActionRequirementText(response) || trimmedText,
      )
      return
    }

    if (response.intent === 'PROPOSE_MODULE_TREE_PATCH' && response.proposal) {
      treePatchProposal.value = response.proposal
      return
    }

    if (response.intent === 'GENERATE_REQUIREMENT_ARTIFACT') {
      await refreshAfterAssistantTaskDispatch()
      return
    }
  } catch (error) {
    window.alert(resolveErrorMessage(error, 'AI Assistant 消息处理失败。'))
  } finally {
    isSendingAssistantMessage.value = false
  }
}

async function refreshAfterAssistantTaskDispatch() {
  const module = selectedModule.value

  if (!module?.id) {
    return
  }

  await Promise.allSettled([
    loadAllCurrentModuleArtifacts(module),
    loadCurrentModuleAgentTasks(),
  ])

  await refreshArtifactsForFinishedTasks()

  if (hasActiveModuleTasks()) {
    startTaskPolling()
  }

  const preferredArtifactType = resolveAssistantDispatchedArtifactType(
    latestAssistantResponse.value,
  )

  if (preferredArtifactType) {
    selectedArtifactType.value = preferredArtifactType
  }

  await saveCurrentWorkspaceState({
    selectedModuleId: module.id,
    selectedArtifactType: selectedArtifactType.value,
    selectedArtifactId: selectedArtifactType.value
      ? getArtifactByType(selectedArtifactType.value)?.artifactId || null
      : null,
    activeAgentTab: activeTab.value,
    uiState: {
      lastAction: 'assistant-create-artifact-task',
      assistantIntent: latestAssistantResponse.value?.intent || null,
    },
  })
}

function resolveAssistantDispatchedArtifactType(
  response: AssistantMessageResponse | null,
): RequirementArtifactType | null {
  if (!response?.actions?.length) {
    return selectedArtifactType.value
  }

  for (const action of response.actions) {
    const payload = action.payload || {}

    const artifactType = payload.artifactType

    if (
      typeof artifactType === 'string' &&
      isRequirementArtifactType(artifactType)
    ) {
      return artifactType
    }

    const fullChain = payload.fullChain as {
      createdTasks?: Array<{ artifactType?: string }>
      reusedTasks?: Array<{ artifactType?: string }>
    } | undefined

    const firstFullChainTask =
      fullChain?.createdTasks?.[0] ||
      fullChain?.reusedTasks?.[0]

    if (
      firstFullChainTask?.artifactType &&
      isRequirementArtifactType(firstFullChainTask.artifactType)
    ) {
      return firstFullChainTask.artifactType
    }
  }

  return selectedArtifactType.value
}

async function handleApplyTreePatchProposal(
  proposalFromMessage?: ModuleTreePatchProposalResponse,
) {
  const proposal = proposalFromMessage || treePatchProposal.value

  if (!proposal?.proposal?.operations?.length) {
    window.alert('当前没有可应用的模块树变更操作。')
    return
  }

  isApplyingTreePatch.value = true

  try {
    const currentSelectedModuleId = selectedModule.value?.id || null

    const response = await applyModuleTreePatch(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        description: proposal.description || proposal.proposal.description || 'AI 模块树变更提案',
        source: proposal.source || proposal.proposal.source || 'AI_TREE_PATCH',
        operations: proposal.proposal.operations,
      },
    )

    modules.value = response.tree
    treePatchProposal.value = null

    if (currentSelectedModuleId) {
      const matchedModule = restoreSelectedModuleById(
        modules.value,
        currentSelectedModuleId,
      )

      if (matchedModule) {
        await handleSelectModule(matchedModule)
      } else {
        clearSelectedModuleAndArtifacts()
      }
    }
  } catch (error) {
    window.alert(resolveErrorMessage(error, '应用 AI 模块树变更提案失败。'))
  } finally {
    isApplyingTreePatch.value = false
  }
}


async function handleSelectModule(node: ModuleTreeNode) {
  stopTaskPolling()

  selectModule(node)
  clearArtifacts()
  clearCurrentModuleAgentTasks()
  selectedArtifactType.value = null

  for (const definition of REQUIREMENT_ARTIFACT_DEFINITIONS) {
    setStepStatus(definition.stepId, 'pending')
  }

  await loadAllCurrentModuleArtifacts(node)
  await loadCurrentModuleAgentTasks()

  selectedArtifactType.value = getCurrentSelectedArtifactType()

  if (hasActiveModuleTasks()) {
    startTaskPolling()
  }

  await saveCurrentWorkspaceState({
    selectedModuleId: node.id || null,
    selectedArtifactType: selectedArtifactType.value,
    selectedArtifactId: getCurrentSelectedArtifactId(),
    activeAgentTab: activeTab.value,
    uiState: {
      lastAction: 'select-module',
    },
  })
}

function handleCreateRootModuleNode() {
  nodeEditorMode.value = 'create-root'
  nodeEditorParent.value = null
  nodeEditorTarget.value = null
  nodeEditorVisible.value = true
}

function handleCreateChildModuleNode(parent: ModuleTreeNode) {
  nodeEditorMode.value = 'create-child'
  nodeEditorParent.value = parent
  nodeEditorTarget.value = null
  nodeEditorVisible.value = true
}

function handleRenameModuleNode(node: ModuleTreeNode) {
  nodeEditorMode.value = 'edit'
  nodeEditorParent.value = null
  nodeEditorTarget.value = node
  nodeEditorVisible.value = true
}

function handleMoveModuleNode(node: ModuleTreeNode) {
  if (!node.id) {
    return
  }

  nodeMoveTarget.value = node
  nodeMoveDialogVisible.value = true
}

function closeNodeMoveDialog() {
  nodeMoveDialogVisible.value = false
  nodeMoveTarget.value = null
}

async function handleSubmitMoveModuleNode(payload: { parentId: string | null }) {
  if (!nodeMoveTarget.value?.id) {
    window.alert('缺少当前节点，无法移动。')
    return
  }

  try {
    const currentNodeId = nodeMoveTarget.value.id

    const response = await moveProjectModuleNode(
      props.workspace.workspaceId,
      props.project.projectCode,
      currentNodeId,
      {
        parentId: payload.parentId,
      },
    )

    closeNodeMoveDialog()

    await applyModuleMutationResponse(response, currentNodeId)
  } catch (error) {
    window.alert(resolveErrorMessage(error, '移动节点失败。'))
  }
}

function closeNodeEditor() {
  nodeEditorVisible.value = false
  nodeEditorParent.value = null
  nodeEditorTarget.value = null
}

async function handleSubmitNodeEditor(payload: {
  moduleName: string
  moduleKey?: string
  moduleType: string
  extra: Record<string, unknown>
}) {
  try {
    if (nodeEditorMode.value === 'create-root') {
      const response = await createProjectModuleNode(
        props.workspace.workspaceId,
        props.project.projectCode,
        {
          parentId: null,
          moduleName: payload.moduleName,
          moduleKey: payload.moduleKey,
          moduleType: payload.moduleType,
          extra: payload.extra,
        },
      )

      closeNodeEditor()
      await applyModuleMutationResponse(response, response.node?.id || null)
      return
    }

    if (nodeEditorMode.value === 'create-child') {
      if (!nodeEditorParent.value?.id) {
        window.alert('缺少父节点，无法创建子节点。')
        return
      }

      const response = await createProjectModuleNode(
        props.workspace.workspaceId,
        props.project.projectCode,
        {
          parentId: nodeEditorParent.value.id,
          moduleName: payload.moduleName,
          moduleKey: payload.moduleKey,
          moduleType: payload.moduleType,
          extra: payload.extra,
        },
      )

      closeNodeEditor()
      await applyModuleMutationResponse(response, response.node?.id || null)
      return
    }

    if (nodeEditorMode.value === 'edit') {
      if (!nodeEditorTarget.value?.id) {
        window.alert('缺少当前节点，无法保存修改。')
        return
      }

      const response = await updateProjectModuleNode(
        props.workspace.workspaceId,
        props.project.projectCode,
        nodeEditorTarget.value.id,
        {
          moduleName: payload.moduleName,
          moduleKey: payload.moduleKey,
          moduleType: payload.moduleType,
          extra: payload.extra,
        },
      )

      closeNodeEditor()
      await applyModuleMutationResponse(response, nodeEditorTarget.value.id)
    }
  } catch (error) {
    window.alert(resolveErrorMessage(error, '保存节点失败。'))
  }
}

function handleDeleteModuleNode(node: ModuleTreeNode) {
  if (!node.id) {
    return
  }

  nodeDeleteTarget.value = node
  nodeDeleteDialogVisible.value = true
}

function closeNodeDeleteDialog() {
  nodeDeleteDialogVisible.value = false
  nodeDeleteTarget.value = null
}

async function handleConfirmDeleteModuleNode() {
  if (!nodeDeleteTarget.value?.id) {
    window.alert('缺少当前节点，无法删除。')
    return
  }

  const deletedId = nodeDeleteTarget.value.id

  try {
    const response = await deleteProjectModuleNode(
      props.workspace.workspaceId,
      props.project.projectCode,
      deletedId,
    )

    closeNodeDeleteDialog()

    modules.value = response.tree

    if (selectedModule.value?.id === deletedId) {
      clearSelectedModuleAndArtifacts()

      await saveCurrentWorkspaceState({
        selectedModuleId: null,
        selectedArtifactType: null,
        selectedArtifactId: null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: 'delete-module-node',
        },
      })

      return
    }

    if (selectedModule.value?.id) {
      const matchedModule = restoreSelectedModuleById(
        modules.value,
        selectedModule.value.id,
      )

      if (matchedModule) {
        await handleSelectModule(matchedModule)
      }
    }
  } catch (error) {
    window.alert(resolveErrorMessage(error, '删除节点失败。'))
  }
}

async function applyModuleMutationResponse(
  response: { node?: ModuleTreeNode | null; tree: ModuleTreeNode[] },
  preferredSelectedNodeId: string | null,
) {
  modules.value = response.tree

  if (!preferredSelectedNodeId) {
    return
  }

  const matchedModule = restoreSelectedModuleById(
    modules.value,
    preferredSelectedNodeId,
  )

  if (matchedModule) {
    await handleSelectModule(matchedModule)
  }
}

function resolveErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

async function handleGenerateModulePrd() {
  await startAsyncArtifactTask('GENERATE_MODULE_PRD')
}

async function handleGenerateBusinessFlow() {
  await startAsyncArtifactTask('GENERATE_BUSINESS_FLOW')
}

async function handleGeneratePageSpec() {
  await startAsyncArtifactTask('GENERATE_PAGE_SPEC')
}

async function handleGenerateFullChain() {
  await startFullChainTask(false)
}

async function handleGenerateDataModel() {
  await startAsyncArtifactTask('GENERATE_DATA_MODEL')
}

async function handleGenerateApiContract() {
  await startAsyncArtifactTask('GENERATE_API_CONTRACT')
}

async function handleGenerateTestSpec() {
  await startAsyncArtifactTask('GENERATE_TEST_SPEC')
}
function isPageNode(node: ModuleTreeNode | null): boolean {
  if (!node) {
    return false
  }

  const moduleType = String(
    node.moduleType ||
    node.type ||
    node.extra?.moduleType ||
    node.extra?.type ||
    '',
  ).toUpperCase()

  return moduleType === 'PAGE' || moduleType === 'PAGE_MODULE'
}

function isFeatureNode(node: ModuleTreeNode | null): boolean {
  if (!node) {
    return false
  }

  const moduleType = String(
    node.moduleType ||
    node.type ||
    node.extra?.moduleType ||
    node.extra?.type ||
    '',
  ).toUpperCase()

  return moduleType === 'FEATURE'
}

async function handleGenerateUiContract() {
  if (!selectedModule.value) {
    window.alert('请先选择一个页面节点。')
    return
  }

  if (isFeatureNode(selectedModule.value)) {
    window.alert('当前选中的是页面功能点。请回到其所属 PAGE 页面节点生成交互原型。')
    return
  }

  if (!isPageNode(selectedModule.value)) {
    window.alert('交互原型只能基于 PAGE 页面节点生成。请在模块树中选择具体页面，例如列表页、创建/编辑页、详情页或审批页。')
    return
  }

  await startAsyncArtifactTask('GENERATE_UI_CONTRACT')
}

async function handleGenerateDevHandoffPackage() {
  await startAsyncArtifactTask('GENERATE_DEV_HANDOFF_PACKAGE')
}

async function handleViewArtifact(
  artifactType: RequirementArtifactType | string,
) {
  if (!isRequirementArtifactType(artifactType)) {
    return
  }

  selectedArtifactType.value = artifactType

  const artifact = getArtifactByType(artifactType)

  await saveCurrentWorkspaceState({
    selectedModuleId: selectedModule.value?.id || null,
    selectedArtifactType: artifactType,
    selectedArtifactId: artifact?.artifactId || null,
    activeAgentTab: activeTab.value,
    uiState: {
      lastAction: `view-${artifactType.toLowerCase()}`,
    },
  })
}

function handlePreviewUiContract() {
  if (!selectedModule.value || !isPageNode(selectedModule.value)) {
    window.alert('请先选择具体 PAGE 页面节点，再预览交互原型。')
    return
  }

  if (!uiContractArtifact.value?.content) {
    window.alert('当前页面还没有可预览的 UI 契约，请先生成交互原型。')
    return
  }

  uiContractPreviewVisible.value = true
}

async function handleOpenArtifactVersions(
  artifactType: RequirementArtifactType,
) {
  if (!selectedModule.value?.id) {
    return
  }

  artifactVersionDrawerVisible.value = true
  artifactVersionArtifactType.value = artifactType

  await loadArtifactVersionHistory()
}

function handleOpenImpactAnalysis(
  artifactType: RequirementArtifactType,
) {
  if (!selectedModule.value?.id) {
    return
  }

  const sourceDefinition = REQUIREMENT_ARTIFACT_DEFINITIONS.find(
    (definition) => definition.artifactType === artifactType,
  )

  impactAnalysisError.value = ''
  impactAnalysisSourceArtifactType.value = artifactType
  impactAnalysisSelectedTypes.value = sourceDefinition
    ? [...sourceDefinition.downstreamArtifactTypes]
    : []
  impactAnalysisDrawerVisible.value = true
}

function handleOpenQualityCheck() {
  if (!selectedModule.value?.id) {
    return
  }

  qualityCheckDrawerVisible.value = true
}

function closeQualityCheckDrawer() {
  qualityCheckDrawerVisible.value = false
}

function getArtifactMarkdownByType(
  artifactType: RequirementArtifactType,
): string {
  if (artifactType === 'MODULE_PRD') return modulePrdMarkdown.value || ''
  if (artifactType === 'BUSINESS_FLOW') return businessFlowMarkdown.value || ''
  if (artifactType === 'PAGE_SPEC') return pageSpecMarkdown.value || ''
  if (artifactType === 'DATA_MODEL') return dataModelMarkdown.value || ''
  if (artifactType === 'API_CONTRACT') return apiContractMarkdown.value || ''
  if (artifactType === 'TEST_SPEC') return testSpecMarkdown.value || ''
  if (artifactType === 'DEV_HANDOFF_PACKAGE') return devHandoffPackageMarkdown.value || ''
  if (artifactType === 'UI_CONTRACT') return uiContractMarkdown.value || ''
  return ''
}

function hasMeaningfulArtifactContent(
  artifact: ArtifactResponse | null | undefined,
  fallbackMarkdown: string,
): boolean {
  const markdown = artifact?.contentMarkdown || fallbackMarkdown

  if (markdown && markdown.trim().length > 20) {
    const normalizedMarkdown = markdown.trim()

    if (
      normalizedMarkdown.includes('当前模块尚未生成') ||
      normalizedMarkdown.includes('暂无内容') ||
      normalizedMarkdown.includes('TODO') ||
      normalizedMarkdown.includes('占位')
    ) {
      return false
    }

    return true
  }

  if (!artifact?.content) {
    return false
  }

  try {
    const contentText = JSON.stringify(artifact.content)

    return contentText.length > 20 &&
      contentText !== '{}' &&
      !contentText.includes('当前模块尚未生成') &&
      !contentText.includes('暂无内容')
  } catch {
    return false
  }
}

async function handleExportArtifactsZip() {
  if (!selectedModule.value?.id) {
    return
  }

  const forceExport = resolveExportForceFlag()

  if (forceExport === null) {
    qualityCheckDrawerVisible.value = true
    return
  }

  try {
    const {
      blob,
      contentDisposition,
    } = await downloadModuleArtifactExportZip(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      forceExport,
    )

    await assertZipBlob(blob)

    const fileName =
      parseContentDispositionFileName(contentDisposition || '') ||
      `codemind_${props.project.projectCode}_${selectedModule.value.moduleKey || selectedModule.value.id}_requirements.zip`

    const savedByPicker = await saveBlobWithFilePicker(
      blob,
      fileName,
    )

    if (savedByPicker) {
      return
    }

    downloadBlobByBrowser(
      blob,
      fileName,
    )
  } catch (error) {
    console.error(error)

    window.alert(
      error instanceof Error
        ? error.message
        : '导出交接包失败，请稍后重试。',
    )
  }
}

async function handleExportArtifactsDocx() {
  if (!selectedModule.value?.id) {
    return
  }

  const forceExport = resolveExportForceFlag()

  if (forceExport === null) {
    qualityCheckDrawerVisible.value = true
    return
  }

  try {
    const {
      blob,
      contentDisposition,
    } = await downloadModuleArtifactExportDocx(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      forceExport,
    )

    await assertOfficeOpenXmlBlob(blob)

    const fileName =
      parseContentDispositionFileName(contentDisposition || '') ||
      `codemind_${props.project.projectCode}_${selectedModule.value.moduleKey || selectedModule.value.id}_requirements.docx`

    const savedByPicker = await saveBlobWithFilePicker(
      blob,
      fileName,
    )

    if (savedByPicker) {
      return
    }

    downloadBlobByBrowser(
      blob,
      fileName,
    )
  } catch (error) {
    console.error(error)

    window.alert(
      error instanceof Error
        ? error.message
        : '导出 Word 失败，请稍后重试。',
    )
  }
}

async function handleExportProjectZip() {
  try {
    await downloadAndSaveProjectZip(false)
  } catch (error) {
    console.error(error)

    const message =
      error instanceof Error
        ? error.message
        : '项目交付包导出失败，请稍后重试。'

    if (!isProjectExportBlockedError(message)) {
      window.alert(message)
      return
    }

    const confirmed = window.confirm(
      '当前项目存在模块交付阻断项，不建议直接导出。\n\n' +
      '是否仍然强制导出项目交付包？\n\n' +
      '强制导出的 ZIP 中会包含 PROJECT_QUALITY_REPORT.md，' +
      '并在 project_manifest.json 中标记 forced_export = true。',
    )

    if (!confirmed) {
      return
    }

    try {
      await downloadAndSaveProjectZip(true)
    } catch (forceError) {
      console.error(forceError)

      window.alert(
        forceError instanceof Error
          ? forceError.message
          : '项目交付包强制导出失败，请稍后重试。',
      )
    }
  }
}

async function downloadAndSaveProjectZip(
  forceExport: boolean,
) {
  const {
    blob,
    contentDisposition,
  } = await downloadProjectArtifactExportZip(
    props.workspace.workspaceId,
    props.project.projectCode,
    forceExport,
  )

  await assertZipBlob(blob)

  const fileName =
    parseContentDispositionFileName(contentDisposition || '') ||
    `codemind_${props.project.projectCode}_project_delivery.zip`

  const savedByPicker = await saveBlobWithFilePicker(
    blob,
    fileName,
  )

  if (savedByPicker) {
    return
  }

  downloadBlobByBrowser(
    blob,
    fileName,
  )
}

function isProjectExportBlockedError(
  message: string,
): boolean {
  return (
    message.includes('HTTP 409') ||
    message.includes('交付阻断项') ||
    message.includes('BLOCKED')
  )
}

function resolveExportForceFlag(): boolean | null {
  const qualityResult = artifactQualityCheckResult.value
  const blockerCount = qualityResult.blockers.length
  const warningCount = qualityResult.warnings.length

  if (blockerCount > 0) {
    const confirmed = window.confirm(
      `当前模块存在 ${blockerCount} 个交付阻断项，不建议直接导出。\n\n` +
      `是否仍然强制导出？\n\n` +
      `强制导出的文件会保留质量检查结论，并标记为强制导出。`,
    )

    return confirmed ? true : null
  }

  if (warningCount > 0) {
    const confirmed = window.confirm(
      `当前模块存在 ${warningCount} 个交付警告项。\n\n` +
      `建议确认后再导出。是否继续导出？`,
    )

    return confirmed ? false : null
  }

  return false
}

function parseContentDispositionFileName(
  contentDisposition: string,
): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)

  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const normalMatch = contentDisposition.match(/filename="?([^";]+)"?/i)

  if (normalMatch?.[1]) {
    return normalMatch[1]
  }

  return null
}

async function assertZipBlob(
  blob: Blob,
) {
  if (!blob || blob.size === 0) {
    throw new Error('导出失败：后端返回了空文件，不是有效 ZIP。')
  }

  const header = new Uint8Array(
    await blob.slice(0, 4).arrayBuffer(),
  )

  const isZip =
    header[0] === 0x50 &&
    header[1] === 0x4b

  if (isZip) {
    return
  }

  const previewText = await blob.text()

  throw new Error(
    '导出失败：后端返回的内容不是有效 ZIP。返回内容预览：\n\n' +
    previewText.slice(0, 500),
  )
}

async function assertOfficeOpenXmlBlob(
  blob: Blob,
) {
  if (!blob || blob.size === 0) {
    throw new Error('导出失败：后端返回了空文件，不是有效 Office 文档。')
  }

  const header = new Uint8Array(
    await blob.slice(0, 4).arrayBuffer(),
  )

  const isOfficeOpenXml =
    header[0] === 0x50 &&
    header[1] === 0x4b

  if (isOfficeOpenXml) {
    return
  }

  const previewText = await blob.text()

  throw new Error(
    '导出失败：后端返回的内容不是有效 Office 文档。返回内容预览：\n\n' +
    previewText.slice(0, 500),
  )
}

type FilePickerWritable = {
  write: (data: Blob) => Promise<void>
  close: () => Promise<void>
}

type FilePickerHandle = {
  createWritable: () => Promise<FilePickerWritable>
}

type FilePickerWindow = Window & {
  showSaveFilePicker?: (options: {
    suggestedName?: string
    types?: Array<{
      description: string
      accept: Record<string, string[]>
    }>
  }) => Promise<FilePickerHandle>
}

async function saveBlobWithFilePicker(
  blob: Blob,
  fileName: string,
): Promise<boolean> {
  const pickerWindow = window as FilePickerWindow

  if (!pickerWindow.showSaveFilePicker) {
    return false
  }

  try {
    const fileHandle = await pickerWindow.showSaveFilePicker({
      suggestedName: fileName,
      types: [
        {
          description: 'ZIP 压缩包',
          accept: {
            'application/zip': ['.zip'],
          },
        },
      ],
    })

    const writable = await fileHandle.createWritable()
    await writable.write(blob)
    await writable.close()

    return true
  } catch (error) {
    if (
      error instanceof DOMException &&
      error.name === 'AbortError'
    ) {
      return true
    }

    throw error
  }
}

function downloadBlobByBrowser(
  blob: Blob,
  fileName: string,
) {
  const downloadUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = downloadUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()

  URL.revokeObjectURL(downloadUrl)
}

function handleToggleImpactedArtifact(
  artifactType: RequirementArtifactType,
  checked: boolean,
) {
  if (checked) {
    if (!impactAnalysisSelectedTypes.value.includes(artifactType)) {
      impactAnalysisSelectedTypes.value = [
        ...impactAnalysisSelectedTypes.value,
        artifactType,
      ]
    }

    return
  }

  impactAnalysisSelectedTypes.value = impactAnalysisSelectedTypes.value.filter(
    (item) => item !== artifactType,
  )
}

function selectAllImpactedArtifacts() {
  impactAnalysisSelectedTypes.value = impactAnalysisItems.value.map(
    (item) => item.artifactType,
  )
}

async function handleRegenerateImpactedArtifacts() {
  if (
    !selectedModule.value?.id ||
    impactAnalysisSelectedTypes.value.length === 0
  ) {
    return
  }

  const selectedDefinitions = REQUIREMENT_ARTIFACT_DEFINITIONS.filter(
    (definition) =>
      impactAnalysisSelectedTypes.value.includes(definition.artifactType),
  )

  if (selectedDefinitions.length === 0) {
    return
  }

  const selectedTitles = selectedDefinitions
    .map((definition) => definition.title)
    .join('、')

  const confirmed = window.confirm(
    `确定要按链路顺序重生成以下受影响资产吗？\n\n${selectedTitles}\n\n系统会创建新的异步任务，已有历史版本不会删除。`,
  )

  if (!confirmed) {
    return
  }

  impactAnalysisSubmitting.value = true
  impactAnalysisError.value = ''

  try {
    for (const definition of selectedDefinitions) {
      await startAsyncArtifactTask(
        definition.taskType as AsyncRequirementTaskType,
      )
    }

    await loadCurrentModuleAgentTasks()

    impactAnalysisDrawerVisible.value = false
  } catch (error) {
    console.error(error)

    impactAnalysisError.value =
      error instanceof Error
        ? error.message
        : '受影响资产重生成任务提交失败，请稍后重试。'
  } finally {
    impactAnalysisSubmitting.value = false
  }
}

function closeImpactAnalysisDrawer() {
  impactAnalysisDrawerVisible.value = false
  impactAnalysisSubmitting.value = false
  impactAnalysisError.value = ''
  impactAnalysisSourceArtifactType.value = null
  impactAnalysisSelectedTypes.value = []
}

async function loadArtifactVersionHistory() {
  if (
    !selectedModule.value?.id ||
    !artifactVersionArtifactType.value
  ) {
    return
  }

  artifactVersionLoading.value = true
  artifactVersionError.value = ''

  try {
    const response = await fetchModuleArtifactVersions(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      artifactVersionArtifactType.value,
    )

    artifactVersionSummaries.value = response.versions || []

    const preferredVersion =
      artifactVersionSummaries.value.find((version) => version.current) ||
      artifactVersionSummaries.value[0]

    if (preferredVersion) {
      await handleViewArtifactVersion(preferredVersion.artifactId)
    } else {
      selectedArtifactVersion.value = null
    }
  } catch (error) {
    console.error(error)

    artifactVersionError.value =
      error instanceof Error
        ? error.message
        : '版本历史加载失败，请稍后重试。'
  } finally {
    artifactVersionLoading.value = false
  }
}

async function handleViewArtifactVersion(
  artifactId: string,
) {
  if (
    !selectedModule.value?.id ||
    !artifactVersionArtifactType.value ||
    !artifactId
  ) {
    return
  }

  try {
    selectedArtifactVersion.value = await fetchModuleArtifactVersion(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      artifactId,
      artifactVersionArtifactType.value,
    )
  } catch (error) {
    console.error(error)

    artifactVersionError.value =
      error instanceof Error
        ? error.message
        : '版本详情加载失败，请稍后重试。'
  }
}

async function handleRollbackArtifactVersion(
  artifactId: string,
) {
  if (
    !selectedModule.value?.id ||
    !artifactVersionArtifactType.value ||
    !artifactId
  ) {
    return
  }

  const confirmed = window.confirm(
    '确定要将该历史版本回滚为当前版本吗？此操作不会删除其他历史版本。',
  )

  if (!confirmed) {
    return
  }

  artifactVersionRollbackLoading.value = true
  artifactVersionError.value = ''

  try {
    const artifact = await rollbackModuleArtifactVersion(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      artifactId,
      artifactVersionArtifactType.value,
    )

    if (isRequirementArtifactType(artifact.artifactType)) {
      selectedArtifactType.value = artifact.artifactType

      await loadCurrentArtifactByType(
        selectedModule.value,
        artifact.artifactType,
      )

      await saveCurrentWorkspaceState({
        selectedModuleId: selectedModule.value.id || null,
        selectedArtifactType: artifact.artifactType,
        selectedArtifactId: artifact.artifactId || null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: 'rollback-artifact-version',
          artifactType: artifact.artifactType,
          artifactId: artifact.artifactId,
          versionNo: artifact.versionNo,
        },
      })
    }

    await loadArtifactVersionHistory()
  } catch (error) {
    console.error(error)

    artifactVersionError.value =
      error instanceof Error
        ? error.message
        : '版本回滚失败，请稍后重试。'
  } finally {
    artifactVersionRollbackLoading.value = false
  }
}

async function handleSubmitArtifactReview(
  artifactId: string,
) {
  await handleArtifactReviewAction(
    artifactId,
    'submit-review',
  )
}

async function handleApproveArtifactVersion(
  artifactId: string,
) {
  const confirmed = window.confirm(
    '确定要确认通过该版本吗？确认后该版本将作为已认可的需求资产。',
  )

  if (!confirmed) {
    return
  }

  await handleArtifactReviewAction(
    artifactId,
    'approve',
  )
}

async function handleRejectArtifactVersion(
  artifactId: string,
) {
  const reason = window.prompt(
    '请输入退回原因，便于后续修改和审计追踪：',
    '',
  )

  if (reason === null) {
    return
  }

  await handleArtifactReviewAction(
    artifactId,
    'reject',
    reason,
  )
}

async function handleArchiveArtifactVersion(
  artifactId: string,
) {
  const reason = window.prompt(
    '请输入归档原因。归档仅适用于非当前历史版本：',
    '',
  )

  if (reason === null) {
    return
  }

  await handleArtifactReviewAction(
    artifactId,
    'archive',
    reason,
  )
}

async function handleArtifactReviewAction(
  artifactId: string,
  action: 'submit-review' | 'approve' | 'reject' | 'archive',
  reason?: string,
) {
  if (
    !selectedModule.value?.id ||
    !artifactVersionArtifactType.value ||
    !artifactId
  ) {
    return
  }

  artifactVersionRollbackLoading.value = true
  artifactVersionError.value = ''

  try {
    let artifact: ArtifactVersionDetailResponse

    if (action === 'submit-review') {
      artifact = await submitModuleArtifactReview(
        props.workspace.workspaceId,
        props.project.projectCode,
        selectedModule.value.id,
        artifactId,
        artifactVersionArtifactType.value,
        reason,
      )
    } else if (action === 'approve') {
      artifact = await approveModuleArtifactVersion(
        props.workspace.workspaceId,
        props.project.projectCode,
        selectedModule.value.id,
        artifactId,
        artifactVersionArtifactType.value,
        reason,
      )
    } else if (action === 'reject') {
      artifact = await rejectModuleArtifactVersion(
        props.workspace.workspaceId,
        props.project.projectCode,
        selectedModule.value.id,
        artifactId,
        artifactVersionArtifactType.value,
        reason,
      )
    } else {
      artifact = await archiveModuleArtifactVersion(
        props.workspace.workspaceId,
        props.project.projectCode,
        selectedModule.value.id,
        artifactId,
        artifactVersionArtifactType.value,
        reason,
      )
    }

    selectedArtifactVersion.value = artifact

    if (
      artifact.current &&
      isRequirementArtifactType(artifact.artifactType)
    ) {
      await loadCurrentArtifactByType(
        selectedModule.value,
        artifact.artifactType,
      )

      await saveCurrentWorkspaceState({
        selectedModuleId: selectedModule.value.id || null,
        selectedArtifactType: artifact.artifactType,
        selectedArtifactId: artifact.artifactId || null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: `artifact-review-${action}`,
          artifactType: artifact.artifactType,
          artifactId: artifact.artifactId,
          versionNo: artifact.versionNo,
          status: artifact.status,
        },
      })
    }

    await loadArtifactVersionHistory()
  } catch (error) {
    console.error(error)

    artifactVersionError.value =
      error instanceof Error
        ? error.message
        : '资产审核状态更新失败，请稍后重试。'
  } finally {
    artifactVersionRollbackLoading.value = false
  }
}

function closeArtifactVersionDrawer() {
  artifactVersionDrawerVisible.value = false
  artifactVersionLoading.value = false
  artifactVersionRollbackLoading.value = false
  artifactVersionError.value = ''
  artifactVersionArtifactType.value = null
  artifactVersionSummaries.value = []
  selectedArtifactVersion.value = null
}

async function handleChangeAgentTab(tab: AgentTab) {
  activeTab.value = tab

  await saveCurrentWorkspaceState({
    selectedModuleId: selectedModule.value?.id || null,
    selectedArtifactType: selectedArtifactType.value,
    selectedArtifactId: selectedArtifactType.value
      ? getArtifactByType(selectedArtifactType.value)?.artifactId || null
      : null,
    activeAgentTab: activeTab.value,
    uiState: {
      lastAction: 'change-agent-tab',
    },
  })
}

async function openProjectTaskCenter() {
  await router.push({
    name: 'project-task-center',
    params: {
      workspaceId: props.workspace.workspaceId,
      projectCode: props.project.projectCode,
    },
  })
}

function resetWorkspaceState() {
  activeTab.value = 'requirement'
  modules.value = []
  assistantHistoryMessages.value = []
  latestAssistantResponse.value = null
  clearSelectedModuleAndArtifacts()
  resetSteps()
}

function clearSelectedModuleAndArtifacts() {
  clearSelectedModule()
  clearArtifacts()
  clearCurrentModuleAgentTasks()
  selectedArtifactType.value = null
}

function handleLogout() {
  clearAuthStorage()
  window.location.reload()
}
</script>

<style scoped>
.main-layout {
  display: flex;
  min-height: calc(100vh - 128px);
  background: #f8fafc;
  overflow: hidden;
}

.main-layout.with-workspace-topbar {
  min-height: calc(100vh - 128px);
}

.workspace-panel {
  flex: 1;
  min-width: 0;
}
</style>