<template>
  <div class="app-shell" :class="{ 'development-shell': activeTab === 'development' }">
    <div class="platform-header-shell">
      <AppHeader :active-tab="activeTab" :project-code="project.projectCode" @change-tab="handleChangeAgentTab" />
    </div>

    <div class="workspace-topbar">
      <div class="workspace-topbar-left">
        <button class="back-home-button" @click="$emit('backHome')">
          ← 返回项目首页
        </button>

        <div class="workspace-project-meta">
          <strong>{{ project.projectName }}</strong>
          <span>{{ workspace.workspaceName }} / {{ project.projectCode }}</span>
        </div>
      </div>

      <div class="workspace-topbar-right">
        <div class="workspace-actions-menu" @keydown.esc="projectActionMenuOpen = false">
          <button
            type="button"
            class="workspace-actions-trigger"
            :aria-expanded="projectActionMenuOpen"
            aria-haspopup="menu"
            @click="projectActionMenuOpen = !projectActionMenuOpen"
          >
            <span>项目操作</span>
            <span class="workspace-actions-trigger-chevron">▾</span>
          </button>

          <nav
            v-if="projectActionMenuOpen && !themeProfileDrawerVisible"
            class="workspace-actions-popover"
            role="menu"
            aria-label="项目操作"
          >
            <button
              class="workspace-action-item"
              type="button"
              role="menuitem"
              @click="projectActionMenuOpen = false; openProjectTaskCenter()"
            >
              项目任务中心
            </button>

            <button
              class="workspace-action-item"
              type="button"
              role="menuitem"
              @click="projectActionMenuOpen = false; handleExportProjectZip()"
            >
              导出项目交付包
            </button>

            <button
              class="workspace-action-item"
              type="button"
              role="menuitem"
              @click="projectActionMenuOpen = false; openThemeProfileDrawer()"
            >
              项目风格
            </button>

            <button
              class="workspace-action-item danger"
              type="button"
              role="menuitem"
              @click="projectActionMenuOpen = false; handleLogout()"
            >
              退出登录
            </button>
          </nav>
        </div>
      </div>
    </div>

    <button
      v-if="projectActionMenuOpen && !themeProfileDrawerVisible"
      class="workspace-actions-backdrop"
      type="button"
      aria-label="关闭项目操作菜单"
      @click="projectActionMenuOpen = false"
    />

    <div class="main-layout with-workspace-topbar">
      <ModuleTreePanel :modules="modules" :selected-module-id="selectedRequirementTreeNodeId || selectedModule?.id" @select="handleSelectModule"
        @create-root="handleCreateRootModuleNode" @create-child="handleCreateChildModuleNode"
        @rename-node="handleRenameModuleNode" @move-node="handleMoveModuleNode" @delete-node="handleDeleteModuleNode" />

      <DevelopmentHandoffGatePanel
        v-if="activeTab === 'development'"
        :workspace-id="props.workspace.workspaceId"
        :project-code="props.project.projectCode"
        :selected-module="selectedModule"
        :readiness="developmentHandoffReadiness"
        :loading="developmentHandoffLoading"
        :error="developmentHandoffError"
        @refresh="loadDevelopmentHandoffReadiness"
        @open-quality-check="handleOpenQualityCheck"
        @preview-ui-contract="handlePreviewUiContract"
      />

      <AcceptanceRequirementWorkspace v-else :selected-module="selectedModule" :module-tree="modules"
        :focused-component-id="focusedUiComponentId"
        :focused-component-label="focusedUiComponentLabel"
        :focused-prototype-element-selection="focusedPrototypeElementSelection"
        :focused-element-selection="focusedUiElementSelection"
        :project-theme-profile="themeProfile" :selected-artifact-type="selectedArtifactType"
        :is-generating-prd="isGeneratingPrd" :is-generating-business-flow="isGeneratingBusinessFlow"
        :is-generating-page-spec="isGeneratingPageSpec" :is-generating-data-model="isGeneratingDataModel"
        :is-generating-api-contract="isGeneratingApiContract" :is-generating-test-spec="isGeneratingTestSpec"
        :is-generating-dev-handoff-package="isGeneratingDevHandoffPackage || isGeneratingDevHandoffPackageDirect" :module-prd-markdown="modulePrdMarkdown"
        :is-generating-full-chain="isGeneratingFullChain" :prd-artifact="prdArtifact" :module-prd-artifact="prdArtifact"
        :business-flow-markdown="businessFlowMarkdown" :business-flow-artifact="businessFlowArtifact"
        :page-spec-markdown="pageSpecMarkdown" :page-spec-artifact="pageSpecArtifact"
        :page-map-markdown="pageMapMarkdown" :page-map-artifact="pageMapArtifact"
        :data-model-markdown="dataModelMarkdown" :data-model-artifact="dataModelArtifact"
        :api-contract-markdown="apiContractMarkdown" :api-contract-artifact="apiContractArtifact"
        :test-spec-markdown="testSpecMarkdown" :test-spec-artifact="testSpecArtifact"
        :is-generating-ui-contract="isGeneratingUiContract" :ui-contract-markdown="uiContractMarkdown"
        :ui-contract-draft-content="uiContractDraftContent"
        :prototype-edit-draft-dirty="prototypeEditDraftDirty"
        :prototype-edit-draft-summary="prototypeEditDraftSummary"
        :ui-contract-artifact="uiContractArtifact" :confirmed-ui-contract-snapshot="uiContractSnapshot" :development-handoff-readiness="developmentHandoffReadiness" :ui-contract-readiness="uiContractReadiness"
        :ui-contract-readiness-loading="uiContractReadinessLoading"
        :ui-contract-readiness-error="uiContractReadinessError"
        :artifact-task-feedback="artifactTaskFeedback"
        :dev-handoff-package-markdown="devHandoffPackageMarkdown"
        :dev-handoff-package-artifact="devHandoffPackageArtifact" :artifact-task-items="moduleArtifactTaskItems"
        :manual-edit-saving="manualArtifactEditSaving"
        @view-artifact="handleViewArtifact"
        @open-related-node-artifact="handleOpenRelatedNodeArtifact"
        @generate-full-chain="handleGenerateFullChain"
        @generate-module-prd="handleGenerateModulePrd" @generate-business-flow="handleGenerateBusinessFlow"
        @generate-page-spec="handleGeneratePageSpec" @generate-data-model="handleGenerateDataModel"
        @generate-api-contract="handleGenerateApiContract" @generate-test-spec="handleGenerateTestSpec"
        @generate-dev-handoff-package="handleGenerateDevHandoffPackage"
        @generate-artifact-task="handleGenerateArtifactTaskFromPanel" @cancel-artifact-task="handleCancelAgentTask"
        @retry-artifact-task="handleRetryAgentTask" @open-artifact-versions="handleOpenArtifactVersions"
        @open-impact-analysis="handleOpenImpactAnalysis" @export-artifacts-zip="handleExportArtifactsZip"
        @export-artifacts-docx="handleExportArtifactsDocx" @open-quality-check="handleOpenQualityCheck"
        @generate-ui-contract="handleGenerateUiContract"
        @refresh-ui-contract-readiness="handleRefreshUiContractReadiness"
        @preview-ui-contract="handlePreviewUiContract"
        @save-manual-artifact-edit="handleSaveManualArtifactEdit"         @save-prototype-draft="handleSavePrototypeEditDraft"
        @discard-prototype-draft="handleDiscardPrototypeEditDraft"
              @select-prototype-component="handleSelectPrototypeComponentFromCanvas"
        @prototype-quick-edit="handlePrototypeQuickEdit"
        @clear-prototype-selection="handleClearPrototypeElementSelection"
      />

      <RightAiAssistantPanel v-model:collapsed="aiAssistantCollapsed" :module-tree-exists="moduleTreeExists"
        :is-generating-tree="isGeneratingTree" :is-sending-message="isSendingAssistantMessage"
        :is-applying-tree-patch="isApplyingTreePatch" :assistant-response="latestAssistantResponse"
        :history-messages="assistantHistoryMessages"
        :upload-attachment="handleUploadAssistantAttachment" @send="handleAssistantSend"
        @clear-conversation="handleClearAssistantConversation"
        @clear-proposal="treePatchProposal = null"
        @apply-proposal="handleApplyTreePatchProposal"
        @confirm-requirement-change="handleConfirmRequirementChangeProposal"
        @confirm-asset-edit-proposal="handleConfirmAssetEditProposal"
        @execute-attachment-change="handleExecuteAttachmentDrivenChange"
        @execute-attachment-diff-action="handleExecuteAttachmentArtifactDiffAction" />

      <ArtifactVersionHistoryDrawer :visible="artifactVersionDrawerVisible" :loading="artifactVersionLoading"
        :rollback-loading="artifactVersionRollbackLoading" :artifact-title="artifactVersionTitle"
        :versions="artifactVersionSummaries" :selected-version="selectedArtifactVersion" :error="artifactVersionError"
        @close="closeArtifactVersionDrawer" @refresh="loadArtifactVersionHistory"
        @view-version="handleViewArtifactVersion" @rollback-version="handleRollbackArtifactVersion"
        @submit-review="handleSubmitArtifactReview" @approve-version="handleApproveArtifactVersion"
        @reject-version="handleRejectArtifactVersion" @archive-version="handleArchiveArtifactVersion"
        @sync-affected-version="handleSyncAffectedArtifactVersion" />

      <ArtifactImpactAnalysisDrawer :visible="impactAnalysisDrawerVisible" :submitting="impactAnalysisSubmitting"
        :source-title="impactAnalysisSourceTitle" :source-artifact-type="impactAnalysisSourceArtifactType"
        :items="impactAnalysisItems" :selected-artifact-types="impactAnalysisSelectedTypes" :error="impactAnalysisError"
        @close="closeImpactAnalysisDrawer" @select-all="selectAllImpactedArtifacts"
        @toggle-artifact="handleToggleImpactedArtifact"
        @sync-selected="handleSyncSelectedImpactedArtifact"
        @regenerate="handleRegenerateImpactedArtifacts" />

      <ArtifactQualityCheckDrawer :visible="qualityCheckDrawerVisible"
        :module-name="selectedModule ? getModuleDisplayName(selectedModule) : '当前模块'"
        :result="artifactQualityCheckResult" @close="closeQualityCheckDrawer" @export-zip="handleExportArtifactsZip" />

      <UiContractPreviewDrawer
        :visible="uiContractPreviewVisible"
        :artifact="uiContractArtifact"
        :workspace-id="props.workspace.workspaceId"
        :project-code="props.project.projectCode"
        :module-tree="modules"
        :project-theme-profile="themeProfile"
        :confirmed-snapshot="uiContractSnapshot"
        :snapshot-loading="uiContractSnapshotLoading"
        :confirming="uiContractConfirming"
        :snapshot-error="uiContractSnapshotError"
        @close="uiContractPreviewVisible = false"
        @confirm-ui-contract="handleConfirmUiContractSnapshot"
      />

      <div
        v-if="themeProfileDrawerVisible"
        class="theme-profile-mask"
        @click.self="closeThemeProfileDrawer"
      >
        <aside class="theme-profile-drawer">
          <header class="theme-profile-drawer-header">
            <div>
              <p>Project Theme Profile</p>
              <h2>项目风格配置</h2>
              <span>统一当前项目所有交互原型和后续页面生成的视觉方向。</span>
            </div>

            <button
              type="button"
              @click="closeThemeProfileDrawer"
            >
              ×
            </button>
          </header>

          <ProjectThemeProfilePanel
            :workspace-id="props.workspace.workspaceId"
            :project-code="props.project.projectCode"
            @changed="handleThemeProfileChanged"
          />
        </aside>
      </div>

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
import AcceptanceRequirementWorkspace from '../components/AcceptanceRequirementWorkspace.vue'
import DevelopmentHandoffGatePanel from '../components/DevelopmentHandoffGatePanel.vue'
import ArtifactVersionHistoryDrawer from '../components/ArtifactVersionHistoryDrawer.vue'
import ArtifactImpactAnalysisDrawer from '../components/ArtifactImpactAnalysisDrawer.vue'
import ArtifactQualityCheckDrawer from '../components/ArtifactQualityCheckDrawer.vue'
import UiContractPreviewDrawer from '../components/UiContractPreviewDrawer.vue'
import ProjectThemeProfilePanel from '../components/ProjectThemeProfilePanel.vue'
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
  createBatchFullChainTasksAsync,
  createAssistantTaskNotification,
  fetchAssistantMessages,
  sendAssistantMessage,
  uploadAssistantAttachment,
  proposeAssistantAssetEdit,
  confirmAssistantAssetEditProposal,
} from '../api'
import {
  fetchUiContractReadiness,
} from '../api/agentTaskApi'
import {
  fetchDevelopmentHandoffReadiness,
} from '../api/developmentHandoffApi'
import {
  confirmUiContractSnapshot,
  fetchCurrentUiContractSnapshot,
} from '../api/uiContractSnapshotApi'

import {
  approveModuleArtifactVersion,
  archiveModuleArtifactVersion,
  downloadModuleArtifactExportDocx,
  downloadModuleArtifactExportZip,
  downloadProjectArtifactExportZip,
  fetchModuleArtifactVersion,
  fetchModuleArtifactVersions,
  generateDevHandoffPackageAsset,
  rejectModuleArtifactVersion,
  rollbackModuleArtifactVersion,
  submitModuleArtifactReview,
  saveManualArtifactEdit,
} from '../api/artifactApi'

import {
  isRequirementArtifactType,
  REQUIREMENT_ARTIFACT_DEFINITIONS,
  type AsyncRequirementTaskType,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'
import {
  getNodeAwareDownstreamArtifactTypes,
  isFeatureNode,
  isPageNode,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'
import { buildArtifactQualityGate } from '../domain/artifactQualityGate'
import { useAssistantSteps } from '../composables/useAssistantSteps'
import { useAsyncArtifactTasks } from '../composables/useAsyncArtifactTasks'
import { useProjectThemeProfile } from '../composables/useProjectThemeProfile'
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
  ArtifactVersionDetailResponse,
  ArtifactVersionSummaryResponse,
  AgentTaskSummary,
  AssistantActionResponse,
  AssistantAttachmentRef,
  AssistantConversationMessageResponse,
  AttachmentDrivenChangeAction,
  AttachmentDrivenChangeProposal,
  AttachmentArtifactDiffAction,
  AttachmentArtifactDiffItem,
  AttachmentArtifactDiffProposal,
  AssistantMessageResponse,
  AssetEditProposal,
  ManualArtifactEditPayload,
  ModuleTreeNode,
  RequirementChangeProposal,
  ModuleTreePatchProposalResponse,
  ModuleTreePatchOperationRequest,
  ProjectSummary,
  UiContractGenerationReadinessResponse,
  WorkspaceSummary,
} from '../types/codemind'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type { ConfirmedUiContractSnapshot } from '../types/uiContractSnapshot'
import type { DevelopmentHandoffReadinessResponse } from '../types/developmentHandoff'
import {
  applyPrototypeLocalEdit,
  isPrototypeLocalEditInstruction,
  type PrototypeElementSelection,
} from '../domain/uiContractPrototypeLocalEdit'

const router = useRouter()

type ModuleTreeModelingMode =
  | 'PROGRESSIVE_MODULE_OUTLINE'
  | 'FULL_ENTERPRISE_BLUEPRINT'

const props = defineProps<{
  workspace: WorkspaceSummary
  project: ProjectSummary
}>()

defineEmits<{
  backHome: []
}>()

const activeTab = ref<AgentTab>('requirement')
const projectActionMenuOpen = ref(false)
const modules = ref<ModuleTreeNode[]>([])
const aiAssistantCollapsed = ref(false)
const latestAssistantResponse = ref<AssistantMessageResponse | null>(null)
const assistantHistoryMessages = ref<AssistantConversationMessageResponse[]>([])
const treePatchProposal = ref<ModuleTreePatchProposalResponse | null>(null)
const isSendingAssistantMessage = ref(false)
const uiContractDraftContent = ref<any | null>(null)
const prototypeEditDraftDirty = ref(false)
const prototypeEditDraftSummary = ref('')
const isApplyingTreePatch = ref(false)
const manualArtifactEditSaving = ref(false)
const isGeneratingDevHandoffPackageDirect = ref(false)
const pendingModuleTreeModelingChoice = ref<{ requirementText: string } | null>(null)

const moduleTreeExists = computed(() => modules.value.length > 0)
const requirementText = ref('我要做一个电商系统，包含用户、订单、支付、库存、后台管理、权限控制等模块。')
const selectedArtifactType = ref<RequirementArtifactType | null>(null)
const selectedRequirementTreeNodeId = ref<string | null>(null)
const focusedUiComponentId = ref<string | null>(null)
const focusedUiComponentLabel = ref('')
const focusedPrototypeElementSelection = ref<any | null>(null)
const focusedUiElementSelection = ref<PrototypeElementSelection | null>(null)
const artifactVersionDrawerVisible = ref(false)
const artifactVersionLoading = ref(false)
const artifactVersionRollbackLoading = ref(false)
const artifactVersionError = ref('')
const artifactVersionArtifactType = ref<RequirementArtifactType | null>(null)
const artifactVersionSummaries = ref<ArtifactVersionSummaryResponse[]>([])
const selectedArtifactVersion = ref<ArtifactVersionDetailResponse | null>(null)

type ArtifactTaskFeedback = {
  level: 'info' | 'success' | 'error'
  message: string
}

const uiContractReadiness =
  ref<UiContractGenerationReadinessResponse | null>(null)
const uiContractReadinessLoading = ref(false)
const uiContractReadinessError = ref('')
const artifactTaskFeedback =
  ref<ArtifactTaskFeedback | null>(null)

const uiContractPreviewVisible = ref(false)
const uiContractSnapshot = ref<ConfirmedUiContractSnapshot | null>(null)
const uiContractSnapshotLoading = ref(false)
const uiContractConfirming = ref(false)
const uiContractSnapshotError = ref('')
const developmentHandoffReadiness = ref<DevelopmentHandoffReadinessResponse | null>(null)
const developmentHandoffLoading = ref(false)
const developmentHandoffError = ref('')
const themeProfileDrawerVisible = ref(false)
const qualityCheckDrawerVisible = ref(false)
const impactAnalysisDrawerVisible = ref(false)
const impactAnalysisSubmitting = ref(false)
const impactAnalysisError = ref('')
const impactAnalysisSourceArtifactType = ref<RequirementArtifactType | null>(null)
const impactAnalysisSelectedTypes = ref<RequirementArtifactType[]>([])

const impactAnalysisAgentSyncSupportedTypes: RequirementArtifactType[] = [
  'MODULE_PRD',
  'BUSINESS_FLOW',
  'PAGE_MAP',
  'PAGE_SPEC',
  'DATA_MODEL',
  'API_CONTRACT',
  'TEST_SPEC',
]


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
  themeProfile,
  loadProjectThemeProfile,
} = useProjectThemeProfile({
  resolveWorkspaceId: () => props.workspace.workspaceId,
  resolveProjectCode: () => props.project.projectCode,
})

const {
  prdArtifact,
  modulePrdMarkdown,
  businessFlowArtifact,
  businessFlowMarkdown,
  pageSpecArtifact,
  pageSpecMarkdown,
  pageMapArtifact,
  pageMapMarkdown,
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
  resolveUiContractReadiness: () =>
    uiContractReadiness.value,
  resolveUiContractReadinessLoading: () =>
    uiContractReadinessLoading.value,
  resolveThemeProfile: () =>
    themeProfile.value,
  onTaskBlocked: async (message) => {
    artifactTaskFeedback.value = {
      level: 'error',
      message,
    }
  },
  onTaskFailed: async (message) => {
    artifactTaskFeedback.value = {
      level: 'error',
      message,
    }
  },
  onTaskCreated: async (task, definition) => {
    selectedArtifactType.value = definition.artifactType
    artifactTaskFeedback.value = {
      level: 'success',
      message: `${definition.title}任务已创建，系统正在后台生成。`,
    }

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

const artifactQualityCheckResult = computed(() =>
  buildArtifactQualityGate({
    node: selectedModule.value,
    getArtifactByType,
    getArtifactMarkdownByType,
    confirmedUiContractSnapshot: uiContractSnapshot.value,
  }),
)

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

  await Promise.allSettled([
    loadAssistantMessageHistory(),
    loadProjectThemeProfile(),
  ])

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

  if (
    payload.definition.artifactType === 'PAGE_SPEC' ||
    payload.definition.artifactType === 'UI_CONTRACT'
  ) {
    await loadUiContractReadiness()
  }

  if (
    payload.definition.artifactType === 'UI_CONTRACT' &&
    (
      task.status === 'SUCCESS' ||
      task.status === 'PARTIAL_SUCCESS'
    ) &&
    selectedModule.value
  ) {
    await loadCurrentArtifactByType(
      selectedModule.value,
      'UI_CONTRACT',
    )

    await loadCurrentUiContractSnapshot(selectedModule.value)
    await loadDevelopmentHandoffReadiness(selectedModule.value)

    selectedArtifactType.value = 'UI_CONTRACT'

    syncA9VirtualComponentNodesForPage(selectedModule.value)

    if (uiContractArtifact.value?.content) {
      uiContractPreviewVisible.value = true
    }

    artifactTaskFeedback.value = {
      level: 'success',
      message:
        task.status === 'PARTIAL_SUCCESS'
          ? '交互原型已生成，但存在质量警告，请在契约检查中确认。'
          : '交互原型已生成，已自动打开预览。',
    }
  } else if (
    task.status === 'FAILED' ||
    task.status === 'TIMEOUT'
  ) {
    artifactTaskFeedback.value = {
      level: 'error',
      message:
        task.errorMessage ||
        task.lastErrorMessage ||
        `${payload.definition.title}生成失败。`,
    }
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
      await loadUiContractReadiness(matchedModule)
      await loadCurrentUiContractSnapshot(matchedModule)
      await loadDevelopmentHandoffReadiness(matchedModule)

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
  await loadUiContractReadiness(matchedModule)
  await loadCurrentUiContractSnapshot(matchedModule)
  await loadDevelopmentHandoffReadiness(matchedModule)

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

async function handleGenerateModuleTree(
  requirementTextOverride?: string,
  modelingMode: ModuleTreeModelingMode = 'PROGRESSIVE_MODULE_OUTLINE',
) {
  const moduleTreeRequirementText = requirementTextOverride?.trim() || requirementText.value

  isGeneratingTree.value = true
  stopTaskPolling()
  resetSteps()

  try {
    setStepStatus('receive', 'success')
    setStepStatus('module-tree', 'running')

    latestAssistantResponse.value = {
      messageId: `module_tree_generation_started_${Date.now()}`,
      intent: 'GENERATE_MODULE_TREE',
      reply: '已读取你的文字和附件材料，正在生成需求骨架。生成完成后会自动刷新左侧结构，并优先选择一个适合继续完善的模块。',
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      attachmentDrivenChangeProposal: null,
      attachmentArtifactDiffProposal: null,
      actions: [],
    }

    await generateModuleTree(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementText: moduleTreeRequirementText,
        modelingMode,
      },
    )

    setStepStatus('module-tree', 'success')
    setStepStatus('artifact', 'running')

    modules.value = await fetchProjectModules(
      props.workspace.workspaceId,
      props.project.projectCode,
    )

    const preferredNode = resolvePreferredNodeAfterModuleTreeGenerated(
      modules.value,
    )

    if (preferredNode) {
      await handleSelectModule(preferredNode)
    } else {
      clearSelectedModuleAndArtifacts()

      await saveCurrentWorkspaceState({
        selectedModuleId: null,
        selectedArtifactType: null,
        selectedArtifactId: null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: 'generate-module-tree',
          moduleTreeNodeCount: 0,
        },
      })
    }

    const successMessage = buildModuleTreeGeneratedAssistantMessage(
      modules.value,
      preferredNode,
    ) + (modelingMode === 'FULL_ENTERPRISE_BLUEPRINT'
      ? '\n本次已生成完整企业蓝图：模块需求、功能点和页面入口会保持同源；页面层只面向交互原型。'
      : '\n本次已生成模块骨架：当前只确定模块边界，后续可继续拆分或完善模块需求。')

    artifactTaskFeedback.value = {
      level: 'success',
      message: successMessage,
    }

    latestAssistantResponse.value = {
      messageId: `module_tree_generated_${Date.now()}`,
      intent: 'GENERATE_MODULE_TREE',
      reply: successMessage,
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      attachmentDrivenChangeProposal: null,
      attachmentArtifactDiffProposal: null,
      actions: [],
    }

    setStepStatus('artifact', 'success')
  } catch (error) {
    console.error(error)
    setStepStatus('module-tree', 'failed')
    setStepStatus('artifact', 'failed')
    showAssistantInlineError(
      error,
      '模块树生成失败。请检查附件是否已解析完成，或缩短需求描述后重试。',
    )
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

function resolvePreferredNodeAfterModuleTreeGenerated(
  tree: ModuleTreeNode[],
): ModuleTreeNode | null {
  const flatNodes = flattenModuleTreeNodes(tree)

  return (
    flatNodes.find((node) => resolveRequirementNodeType(node) === 'MODULE') ||
    flatNodes.find((node) => resolveRequirementNodeType(node) === 'PAGE') ||
    flatNodes.find((node) => resolveRequirementNodeType(node) === 'SUBMODULE') ||
    flatNodes[0] ||
    null
  )
}

function buildModuleTreeGeneratedAssistantMessage(
  tree: ModuleTreeNode[],
  selectedNode: ModuleTreeNode | null,
): string {
  const summary = summarizeModuleTreeNodes(tree)
  const selectedNodeName = selectedNode
    ? getModuleDisplayName(selectedNode)
    : ''

  const selectedNodeType = selectedNode
    ? resolveRequirementNodeType(selectedNode)
    : null

  const selectedPart = selectedNode
    ? `已自动选中“${selectedNodeName}”，你可以直接查看和完善当前内容。`
    : '当前需求骨架已生成，请在左侧选择一个模块继续查看。'

  return [
    `已生成需求骨架：${summary.module} 个模块、${summary.feature} 个功能点、${summary.page} 个页面，共 ${summary.total} 个节点。`,
    selectedPart,
    '模块负责承载模块需求；功能点负责表达业务能力；页面只暴露交互原型入口。',
    '建议先检查当前模块需求是否合理；进入页面后可直接生成和编辑交互原型。',
  ].join('\n')
}

function summarizeModuleTreeNodes(
  tree: ModuleTreeNode[],
): {
  total: number
  domain: number
  module: number
  submodule: number
  page: number
  feature: number
  other: number
} {
  const summary = {
    total: 0,
    domain: 0,
    module: 0,
    submodule: 0,
    page: 0,
    feature: 0,
    other: 0,
  }

  for (const node of flattenModuleTreeNodes(tree)) {
    summary.total += 1

    const nodeType = resolveRequirementNodeType(node)

    if (nodeType === 'DOMAIN') {
      summary.domain += 1
    } else if (nodeType === 'MODULE') {
      summary.module += 1
    } else if (nodeType === 'SUBMODULE') {
      summary.submodule += 1
    } else if (nodeType === 'PAGE') {
      summary.page += 1
    } else if (nodeType === 'FEATURE') {
      summary.feature += 1
    } else {
      summary.other += 1
    }
  }

  return summary
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


function findRequirementNodePath(
  nodes: ModuleTreeNode[],
  nodeId: string,
  path: ModuleTreeNode[] = [],
): ModuleTreeNode[] | null {
  for (const node of nodes) {
    const nextPath = [...path, node]

    if (node.id === nodeId) {
      return nextPath
    }

    const childPath = findRequirementNodePath(node.children || [], nodeId, nextPath)

    if (childPath) {
      return childPath
    }
  }

  return null
}

function collectDescendantPageNodes(
  node: ModuleTreeNode,
): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []

  const visit = (current: ModuleTreeNode) => {
    if (resolveRequirementNodeType(current) === 'PAGE') {
      result.push(current)
    }

    ;(current.children || []).forEach(visit)
  }

  ;(node.children || []).forEach(visit)
  return result
}

function resolveOwningModuleForSubmodule(
  node: ModuleTreeNode,
): ModuleTreeNode | null {
  const path = findRequirementNodePath(modules.value, node.id || '') || []

  for (let index = path.length - 1; index >= 0; index -= 1) {
    const candidate = path[index]

    if (resolveRequirementNodeType(candidate) === 'MODULE') {
      return candidate
    }
  }

  return null
}

function buildLocalAssistantResponse(
  reply: string,
  idPrefix = 'local_assistant_response',
): AssistantMessageResponse {
  return {
    messageId: `${idPrefix}_${Date.now()}`,
    intent: 'GENERAL_QA',
    reply,
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    attachmentDrivenChangeProposal: null,
    attachmentArtifactDiffProposal: null,
    actions: [],
  }
}

function isSubmodulePreparationIntent(
  text: string,
): boolean {
  return /准备|补齐|资产|进入开发|开发前|一键|全部|生成/.test(text)
}

function isSubmodulePageAssetChoice(
  text: string,
): boolean {
  return /^(1|一|本范围|页面资产|页面|PAGE|PAGE_SPEC|UI_CONTRACT)/i.test(text.trim())
    || /本范围.*页面|页面.*资产|PAGE_SPEC.*UI_CONTRACT|UI_CONTRACT.*PAGE_SPEC/i.test(text)
}

function isSubmoduleOwnerModuleChoice(
  text: string,
): boolean {
  return /^(2|二|所属|上级|MODULE|模块核心|核心资产)/i.test(text.trim())
    || /所属.*MODULE|上级.*MODULE|模块.*核心|核心.*资产/i.test(text)
}

async function handleSubmoduleAssetPreparationAssistantCommand(
  text: string,
): Promise<boolean> {
  const module = selectedModule.value

  if (!module || resolveRequirementNodeType(module) !== 'SUBMODULE') {
    return false
  }

  if (isSubmodulePageAssetChoice(text)) {
    const pageNodes = collectDescendantPageNodes(module)

    if (pageNodes.length === 0) {
      latestAssistantResponse.value = buildLocalAssistantResponse(
        '当前 SUBMODULE 下还没有 PAGE 节点，不能准备页面级 交互原型。请先补齐页面节点，或选择“准备所属 MODULE 核心资产”。',
        'submodule_page_asset_empty',
      )
      return true
    }

    artifactTaskFeedback.value = {
      level: 'info',
      message: `正在为当前 SUBMODULE 下 ${pageNodes.length} 个 PAGE 创建页面资产任务：PAGE_SPEC + UI_CONTRACT。`,
    }

    const response = await createBatchFullChainTasksAsync(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        moduleIds: pageNodes
            .map((pageNode) => pageNode.id)
            .filter((id): id is string => Boolean(id)),
        overwriteExisting: false,
        requirementText: requirementText.value,
      },
    )

    latestAssistantResponse.value = buildLocalAssistantResponse(
      `已为当前 SUBMODULE 下 ${response.successModuleCount || pageNodes.length} 个 PAGE 创建页面资产任务。页面资产范围为 PAGE_SPEC + UI_CONTRACT，不会在 SUBMODULE 上生成独立资产。`,
      'submodule_page_asset_tasks',
    )

    await loadCurrentModuleAgentTasks()
    startTaskPolling()
    return true
  }

  if (isSubmoduleOwnerModuleChoice(text)) {
    const ownerModule = resolveOwningModuleForSubmodule(module)

    if (!ownerModule) {
      latestAssistantResponse.value = buildLocalAssistantResponse(
        '没有找到当前 SUBMODULE 的所属 MODULE，无法创建 MODULE 核心资产任务。请先检查模块树层级。',
        'submodule_owner_missing',
      )
      return true
    }

    artifactTaskFeedback.value = {
      level: 'info',
      message: `正在为所属 MODULE“${getModuleDisplayName(ownerModule)}”创建核心资产任务；不会自动生成 UI_CONTRACT。`,
    }

    const response = await createBatchFullChainTasksAsync(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        moduleIds: [ownerModule.id as string],
        overwriteExisting: false,
        requirementText: requirementText.value,
      },
    )

    latestAssistantResponse.value = buildLocalAssistantResponse(
      `已为所属 MODULE“${getModuleDisplayName(ownerModule)}”创建核心资产任务。核心资产包含 MODULE_PRD、BUSINESS_FLOW、PAGE_MAP、DATA_MODEL、API_CONTRACT、TEST_SPEC、DEV_HANDOFF_PACKAGE；UI_CONTRACT 仍由 PAGE 节点显式生成。`,
      'submodule_owner_module_tasks',
    )

    if (response.successModuleCount > 0) {
      selectModule(ownerModule)
    }

    await loadCurrentModuleAgentTasks()
    startTaskPolling()
    return true
  }

  if (!isSubmodulePreparationIntent(text)) {
    return false
  }

  latestAssistantResponse.value = buildLocalAssistantResponse(
    [
      '当前选中的是 SUBMODULE，它只作为场景/分组层，不生成独立 PRD 或 UI_CONTRACT。',
      '',
      '请回复：',
      '1. 准备本范围页面资产：为该 SUBMODULE 下的 PAGE 创建 PAGE_SPEC + UI_CONTRACT。',
      '2. 准备所属 MODULE 核心资产：创建 MODULE_PRD、BUSINESS_FLOW、PAGE_MAP、DATA_MODEL、API_CONTRACT、TEST_SPEC、DEV_HANDOFF_PACKAGE。',
    ].join('\\n'),
    'submodule_asset_choice',
  )
  return true
}


async function handleInitialModuleTreeModelingChoice(
  text: string,
): Promise<boolean> {
  const trimmedText = text.trim()

  if (!trimmedText || moduleTreeExists.value) {
    pendingModuleTreeModelingChoice.value = null
    return false
  }

  const pendingChoice = pendingModuleTreeModelingChoice.value

  if (pendingChoice) {
    const mode = resolveModuleTreeModelingModeChoice(trimmedText)

    if (!mode) {
      latestAssistantResponse.value = buildModuleTreeModelingChoiceResponse(
        pendingChoice.requirementText,
        '请回复 1 或 2，或者直接点击下面的建模模式按钮。',
      )
      return true
    }

    pendingModuleTreeModelingChoice.value = null
    requirementText.value = pendingChoice.requirementText

    await handleGenerateModuleTree(
      pendingChoice.requirementText,
      mode,
    )

    return true
  }

  if (!looksLikeInitialProjectRequirement(trimmedText)) {
    return false
  }

  const explicitMode = resolveExplicitModuleTreeModelingMode(trimmedText)

  if (explicitMode) {
    requirementText.value = trimmedText
    await handleGenerateModuleTree(trimmedText, explicitMode)
    return true
  }

  pendingModuleTreeModelingChoice.value = {
    requirementText: trimmedText,
  }

  latestAssistantResponse.value = buildModuleTreeModelingChoiceResponse(
    trimmedText,
    [
      '我看当前描述还是偏粗粒度。直接生成完整模块 / 功能点 / 页面树容易显得随意，并且会过早固定页面层级。',
      '',
      '请选择这次建模方式：',
      '生成模块骨架：适合只有初步想法时，先确定系统模块边界，后续继续拆分或完善。',
      '生成完整企业蓝图：适合已经有较明确业务范围时，一次生成模块、功能点和页面入口。',
    ].join('\n'),
  )

  return true
}

function buildModuleTreeModelingChoiceResponse(
  requirementText: string,
  reply: string,
): AssistantMessageResponse {
  return {
    messageId: `a9_module_tree_modeling_choice_${Date.now()}`,
    intent: 'GENERAL_QA',
    reply,
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    attachmentDrivenChangeProposal: null,
    attachmentArtifactDiffProposal: null,
    actions: [
      {
        type: 'A9_MODULE_TREE_MODE_CHOICE',
        label: '生成模块骨架',
        payload: {
          text: '生成模块骨架',
          modelingMode: 'PROGRESSIVE_MODULE_OUTLINE',
          requirementText,
        },
      },
      {
        type: 'A9_MODULE_TREE_MODE_CHOICE',
        label: '生成完整企业蓝图',
        payload: {
          text: '生成完整企业蓝图',
          modelingMode: 'FULL_ENTERPRISE_BLUEPRINT',
          requirementText,
        },
      },
    ],
  }
}

function resolveModuleTreeModelingModeChoice(
  text: string,
): ModuleTreeModelingMode | null {
  const normalized = text.trim().toLowerCase()

  if (/^(1|一|a|A)\b/.test(text.trim()) || /模块骨架|渐进|只生成模块|先生成模块|模块层级/.test(text)) {
    return 'PROGRESSIVE_MODULE_OUTLINE'
  }

  if (/^(2|二|b|B)\b/.test(text.trim()) || /完整|蓝图|全量|放权|生成页面|功能点和页面|MODULE.*PAGE/i.test(text)) {
    return 'FULL_ENTERPRISE_BLUEPRINT'
  }

  if (normalized === '1' || normalized === 'a') {
    return 'PROGRESSIVE_MODULE_OUTLINE'
  }

  if (normalized === '2' || normalized === 'b') {
    return 'FULL_ENTERPRISE_BLUEPRINT'
  }

  return null
}

function resolveExplicitModuleTreeModelingMode(
  text: string,
): ModuleTreeModelingMode | null {
  if (/只生成模块|模块骨架|先生成模块|渐进式|不要页面|不要功能点/.test(text)) {
    return 'PROGRESSIVE_MODULE_OUTLINE'
  }

  if (/完整蓝图|完整项目蓝图|直接生成完整|模块.*功能点.*页面|功能点.*页面/.test(text)) {
    return 'FULL_ENTERPRISE_BLUEPRINT'
  }

  return null
}

function looksLikeInitialProjectRequirement(text: string): boolean {
  if (text.length < 8) {
    return false
  }

  if (/^\s*(1|2|一|二|a|b)\s*$/i.test(text)) {
    return false
  }

  if (/项目|系统|平台|管理|业务|模块|功能|支持|需要|包含|仓储|库存|订单|用户|供应商|入库|出库|财务|审批/.test(text)) {
    return true
  }

  return text.length >= 24
}




function isUnauthorizedAssistantError(error: unknown): boolean {
  const record = error as Record<string, unknown> | null | undefined
  const response = record && typeof record === 'object'
    ? (record['response'] as Record<string, unknown> | null | undefined)
    : null
  const responseData = response && typeof response === 'object'
    ? (response['data'] as Record<string, unknown> | string | null | undefined)
    : null

  const candidates = [
    typeof error === 'string' ? error : '',
    record && typeof record['message'] === 'string' ? record['message'] : '',
    response && typeof response['status'] !== 'undefined' ? String(response['status']) : '',
    responseData && typeof responseData === 'object' && typeof responseData['code'] === 'string'
      ? responseData['code']
      : '',
    responseData && typeof responseData === 'object' && typeof responseData['message'] === 'string'
      ? responseData['message']
      : '',
    typeof responseData === 'string' ? responseData : '',
  ]
    .map((item) => String(item || '').trim().toUpperCase())
    .filter(Boolean)

  return candidates.some((item) => item === 'UNAUTHORIZED' || item.includes('UNAUTHORIZED') || item === '401')
}

function showAssistantSafeError(error: unknown, fallbackMessage: string): void {
  if (isUnauthorizedAssistantError(error)) {
    const message = '登录状态已过期，请重新登录后再继续。'
    showAssistantInlineError(new Error(message), message)
    return
  }

  showAssistantInlineError(error, fallbackMessage)
}

async function handleAssistantSend(payload: {
  text: string
  attachments?: AssistantAttachmentRef[]
  actionType?: string | null
  actionPayload?: Record<string, unknown> | null
  modelingMode?: ModuleTreeModelingMode | null
  requirementText?: string | null
}) {
  const trimmedText = payload.text.trim()

  if (!trimmedText) {
    return
  }


  const actionPayload = (payload.actionPayload || {}) as Record<string, unknown>
  const actionType = String(payload.actionType || actionPayload['type'] || '').trim()
  const actionModelingMode = String(
    payload.modelingMode || actionPayload['modelingMode'] || '',
  ).trim()

  if (
    actionType === 'A9_MODULE_TREE_MODE_CHOICE' ||
    actionModelingMode === 'PROGRESSIVE_MODULE_OUTLINE' ||
    actionModelingMode === 'FULL_ENTERPRISE_BLUEPRINT'
  ) {
    const choiceText =
      actionModelingMode === 'PROGRESSIVE_MODULE_OUTLINE' || /模块骨架|渐进|只生成模块/.test(trimmedText)
        ? '生成模块骨架'
        : '生成完整企业蓝图'
    const originalRequirementText = String(
      payload.requirementText || actionPayload['requirementText'] || requirementText.value || '',
    ).trim()

    if (originalRequirementText) {
      requirementText.value = originalRequirementText
    }

    if (await handleInitialModuleTreeModelingChoice(choiceText)) {
      return
    }
  }

  requirementText.value = trimmedText

    if (await handleSubmoduleAssetPreparationAssistantCommand(trimmedText)) {
      return
    }

  if (await handleInitialModuleTreeModelingChoice(trimmedText)) {
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  if (await tryHandlePrototypeLocalEdit(trimmedText)) {
    isSendingAssistantMessage.value = false
    return
  }

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

    if (response.intent === 'EXECUTE_UI_COMMAND') {
      await executeAssistantActions(response.actions || [])
      return
    }

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
    showAssistantSafeError(error, 'AI Assistant 消息处理失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}



async function handlePrototypeQuickEdit(instruction: string) {
  const text = String(instruction || '').trim()

  if (!text) {
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const handled = await tryHandlePrototypeLocalEdit(text)

    if (!handled) {
      artifactTaskFeedback.value = {
        level: 'error',
        message: '当前快捷编辑没有找到可修改的原型元素，请先选择一个页面元素。',
      }
    }
  } finally {
    isSendingAssistantMessage.value = false
  }
}

async function tryHandlePrototypeLocalEdit(instruction: string): Promise<boolean> {
  if (!selectedModule.value?.id || !isPageNode(selectedModule.value)) {
    return false
  }

  if (!isPrototypeLocalEditInstruction(instruction)) {
    return false
  }

  const sourceContract = uiContractDraftContent.value || uiContractArtifact.value?.content

  if (!sourceContract) {
    return false
  }

  try {
    const result = applyPrototypeLocalEdit({
      contract: sourceContract as any,
      instruction,
      focusedComponentId: focusedUiComponentId.value || null,
      selectionRef: focusedPrototypeElementSelection.value || null,
    })

    if (!result.changed) {
      artifactTaskFeedback.value = {
        level: 'error',
        message: result.summary,
      }
      return true
    }

    uiContractDraftContent.value = result.contract as any
    prototypeEditDraftDirty.value = true
    prototypeEditDraftSummary.value = result.summary

    artifactTaskFeedback.value = {
      level: 'success',
      message: `${result.summary}（本地预览，尚未保存为新版本）`,
    }

    latestAssistantResponse.value = {
      messageId: `prototype_edit_local_${Date.now()}`,
      intent: 'GENERAL_QA',
      reply: [
        result.summary,
        '',
        '这次修改还没有保存为 UI_CONTRACT 新版本。确认画布效果后，请点击页面中的“保存为新版本”；不满意可以点击“放弃”。',
      ].join('\n'),
      message: result.summary,
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      attachmentDrivenChangeProposal: null,
      attachmentArtifactDiffProposal: null,
      actions: [],
    } as any

    return true
  } catch (error) {
    console.error(error)
    artifactTaskFeedback.value = {
      level: 'error',
      message: '原型本地调整失败，请重新选择页面或重新生成交互原型。',
    }
    return true
  }
}

function handleClearPrototypeElementSelection() {
  focusedUiComponentId.value = null
  focusedUiComponentLabel.value = ''
  focusedPrototypeElementSelection.value = null
  focusedUiElementSelection.value = null
  artifactTaskFeedback.value = {
    level: 'info',
    message: '已清除原型元素锁定。你可以重新进入元素选择模式后再指定修改目标。',
  }
}

function handleSelectPrototypeComponentFromCanvas(payload: {
  componentId?: string | null
  componentType?: string | null
  componentTitle?: string | null
  elementKind?: string | null
  elementLabel?: string | null
  fieldKey?: string | null
  actionId?: string | null
  columnKey?: string | null
  tabKey?: string | null
  textKey?: string | null
  textRole?: string | null
  elementPath?: string | string[] | null
  [key: string]: unknown
}) {
  const componentId = String(payload?.componentId || '').trim()

  if (!componentId) {
    return
  }

  const selection = {
    componentId,
    componentType: stringOrNull(payload.componentType),
    componentTitle: stringOrNull(payload.componentTitle),
    elementKind: stringOrNull(payload.elementKind || payload.componentType || 'COMPONENT'),
    elementLabel: stringOrNull(payload.elementLabel || payload.componentTitle || payload.componentId),
    fieldKey: stringOrNull(payload.fieldKey),
    actionId: stringOrNull(payload.actionId),
    columnKey: stringOrNull(payload.columnKey),
    tabKey: stringOrNull(payload.tabKey),
    textKey: stringOrNull(payload.textKey),
    textRole: stringOrNull(payload.textRole),
    elementPath: stringOrNull(payload.elementPath),
  }

  focusedUiComponentId.value = componentId
  focusedPrototypeElementSelection.value = selection
  focusedUiElementSelection.value = selection as PrototypeElementSelection
  focusedUiComponentLabel.value = buildPrototypeElementSelectionLabel(selection)

  artifactTaskFeedback.value = {
    level: 'info',
    message: `已锁定页面元素：${focusedUiComponentLabel.value}。现在可以在 AI Assistant 里说“把这个字段改成必填 / 把这个按钮文案改成提交”。`,
  }
}

function stringOrNull(value: unknown): string | null {
  const text = String(value ?? '').trim()
  return text ? text : null
}

function buildPrototypeElementSelectionLabel(selection: {
  componentId?: string | null
  componentType?: string | null
  componentTitle?: string | null
  elementKind?: string | null
  elementLabel?: string | null
  fieldKey?: string | null
  actionId?: string | null
  columnKey?: string | null
  tabKey?: string | null
  textKey?: string | null
  textRole?: string | null
}): string {
  const kind = String(selection.elementKind || 'COMPONENT').toUpperCase()
  const kindLabelMap: Record<string, string> = {
    COMPONENT: '组件',
    FIELD: '字段',
    FIELD_LABEL: '字段名',
    FIELD_CONTROL: '输入框',
    ACTION: '按钮',
    BUTTON: '按钮',
    TEXT: '文案',
    TITLE: '标题',
    DESCRIPTION: '说明',
    TABLE_COLUMN: '表格列',
    TABLE_CELL: '表格单元格',
    TABLE_ACTION: '行操作',
    TAB: '标签页',
    METRIC: '指标卡',
  }

  const label =
    selection.elementLabel ||
    selection.fieldKey ||
    selection.actionId ||
    selection.columnKey ||
    selection.tabKey ||
    selection.textKey ||
    selection.componentTitle ||
    selection.componentId ||
    '已选元素'

  return `${kindLabelMap[kind] || kind} · ${label}`
}


function buildPrototypeSelectionLabel(
  selection: PrototypeElementSelection,
): string {
  const label = String(selection.elementLabel || '').trim()
  const kind = String(selection.elementKind || '').toUpperCase()

  const kindLabelMap: Record<string, string> = {
    COMPONENT: '组件',
    TEXT: '文案',
    TITLE: '标题',
    DESCRIPTION: '说明',
    FIELD: '字段',
    FIELD_LABEL: '字段名',
    FIELD_INPUT: '输入框',
    ACTION: '按钮/动作',
    TABLE_COLUMN: '表格列',
    TABLE_CELL: '表格单元格',
    TABLE_ACTION: '表格行操作',
    TAB: '标签页',
    METRIC: '指标卡',
  }

  const kindLabel = kindLabelMap[kind] || kind || '元素'

  if (label) {
    return `${kindLabel} · ${label}`
  }

  if (selection.fieldKey) return `${kindLabel} · ${selection.fieldKey}`
  if (selection.actionId) return `${kindLabel} · ${selection.actionId}`
  if (selection.columnKey) return `${kindLabel} · ${selection.columnKey}`
  if (selection.tabKey) return `${kindLabel} · ${selection.tabKey}`

  return `${kindLabel} · ${selection.componentId}`
}

async function handleSavePrototypeEditDraft() {
  if (!selectedModule.value?.id || !isPageNode(selectedModule.value)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个 PAGE 页面，再保存原型修改。',
    }
    return
  }

  if (!uiContractDraftContent.value) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前没有可保存的原型调整。',
    }
    return
  }

  const confirmed = window.confirm(
    '确认将当前原型调整保存为新的 UI_CONTRACT 版本吗？\n\n旧版本会保留，新版本会进入版本体系，并标记后续开发交接资产需要更新。',
  )

  if (!confirmed) {
    return
  }

  manualArtifactEditSaving.value = true

  try {
    const content = uiContractDraftContent.value as Record<string, unknown>

    const response = await saveManualArtifactEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      {
        artifactId: uiContractArtifact.value?.artifactId || null,
        artifactType: 'UI_CONTRACT',
        content,
        contentMarkdown: JSON.stringify(content, null, 2),
        reason:
          prototypeEditDraftSummary.value ||
          'A9 自然语言原型局部调整保存。',
      } as any,
    )

    selectedArtifactType.value = 'UI_CONTRACT'

    await Promise.allSettled([
      loadCurrentArtifactByType(selectedModule.value, 'UI_CONTRACT'),
      loadAllCurrentModuleArtifacts(selectedModule.value),
      loadCurrentModuleAgentTasks(),
      loadDevelopmentHandoffReadiness(),
    ])

    resetPrototypeLocalEditDraft()

    // A9_6_14_REFRESH_AFTER_PROTOTYPE_SAVE
    if (selectedModule.value) {
      await Promise.allSettled([
        loadCurrentUiContractSnapshot(selectedModule.value),
        loadDevelopmentHandoffReadiness(selectedModule.value),
      ])
    }


    artifactTaskFeedback.value = {
      level: 'success',
      message:
        response.message ||
        `原型修改已保存为 UI_CONTRACT 新版本 V${response.versionNo || ''}。`,
    }

    await saveCurrentWorkspaceState({
      selectedModuleId: selectedModule.value.id,
      selectedArtifactType: 'UI_CONTRACT',
      selectedArtifactId: response.artifactId || null,
      activeAgentTab: activeTab.value,
      uiState: {
        lastAction: 'a9-prototype-local-edit-save',
        versionNo: response.versionNo || null,
      },
    })
  } catch (error) {
    window.alert(resolveErrorMessage(error, '保存原型修改失败。'))
  } finally {
    manualArtifactEditSaving.value = false
  }
}

function handleDiscardPrototypeEditDraft() {
  resetPrototypeLocalEditDraft()

  artifactTaskFeedback.value = {
    level: 'info',
    message: '已放弃本地原型调整，当前 UI_CONTRACT 未保存新版本。',
  }
}


function resetPrototypeLocalEditDraft() {
  uiContractDraftContent.value = null
  prototypeEditDraftDirty.value = false
  prototypeEditDraftSummary.value = ''
  focusedPrototypeElementSelection.value = null
  focusedUiElementSelection.value = null
  focusedUiComponentId.value = null
  focusedUiComponentLabel.value = ''
}

watch(
  () => [selectedModule.value?.id || null, uiContractArtifact.value?.artifactId || null],
  () => resetPrototypeLocalEditDraft(),
)

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

async function handleConfirmRequirementChangeProposal(
  proposal: RequirementChangeProposal,
) {
  if (!proposal) {
    return
  }

  const originalInstruction = String(proposal.originalInstruction || '').trim()

  if (shouldConvertRequirementChangeToTreePatch(proposal)) {
    await dispatchAssistantFollowup({
      message:
        '用户已确认以下需求变更草案，请将其转换为可审查的模块树变更提案，不要直接应用：' +
        '\n\n变更摘要：' +
        (proposal.summary || originalInstruction || '未提供') +
        '\n\n原始要求：' +
        (originalInstruction || proposal.summary || '未提供'),
      expectedIntent: 'PROPOSE_MODULE_TREE_PATCH',
    })
    return
  }

  await proposeAssetEditFromRequirementChange(proposal)
}

async function proposeAssetEditFromRequirementChange(
  proposal: RequirementChangeProposal,
) {
  if (!selectedModule.value?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先在模块树中选择一个节点，再确认资产编辑类需求变更。',
    }
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const response = await proposeAssistantAssetEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementChangeProposal: proposal,
        instruction: String(proposal.originalInstruction || proposal.summary || ''),
        selectedNodeId: selectedModule.value.id || null,
        selectedArtifactType: resolvePrimaryAffectedArtifactType(proposal) || selectedArtifactType.value,
      },
    )

    latestAssistantResponse.value = response

    const targetArtifactType = resolveAssetEditProposalArtifactType(response)

    if (targetArtifactType) {
      await handleViewArtifact(targetArtifactType)
    }

    artifactTaskFeedback.value = {
      level: 'info',
      message: '已根据确认后的需求变更生成资产编辑提案。请在右侧 Assistant 卡片中确认是否生成“新版本待确认”。',
    }
  } catch (error) {
    showAssistantSafeError(error, '生成资产编辑提案失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}


async function handleExecuteAttachmentDrivenChange(
  proposal: AttachmentDrivenChangeProposal,
  action: AttachmentDrivenChangeAction,
) {
  const actionType = normalizeAttachmentDrivenActionType(action)
  const followupMessage = buildAttachmentDrivenFollowupMessage(
    proposal,
    action,
  )

  if (actionType === 'MODULE_TREE_PATCH') {
    await dispatchAssistantFollowup({
      message: followupMessage,
      expectedIntent: 'PROPOSE_MODULE_TREE_PATCH',
      selectedArtifactType: null,
      attachments: buildAttachmentRefsFromDrivenProposal(proposal),
    })
    return
  }

  if (actionType === 'ASSET_EDIT_PROPOSAL') {
    await proposeAssetEditFromAttachmentDrivenChange(
      proposal,
      action,
      followupMessage,
    )
    return
  }

  if (actionType === 'GENERATE_REQUIREMENT_ARTIFACT') {
    await dispatchAssistantFollowup({
      message: followupMessage,
      expectedIntent: 'GENERATE_REQUIREMENT_ARTIFACT',
      selectedArtifactType:
        resolveAttachmentDrivenTargetArtifactType(proposal, action) ||
        selectedArtifactType.value ||
        null,
      attachments: buildAttachmentRefsFromDrivenProposal(proposal),
    })
    return
  }

  if (actionType === 'ATTACHMENT_ARTIFACT_DIFF') {
    await dispatchAssistantFollowup({
      message: buildAttachmentArtifactDiffRequestMessage(proposal, action),
      expectedIntent: 'ANALYZE_ATTACHMENT',
      selectedArtifactType:
        resolveAttachmentDrivenTargetArtifactType(proposal, action) ||
        selectedArtifactType.value ||
        null,
      attachments: buildAttachmentRefsFromDrivenProposal(proposal),
    })
    return
  }

  artifactTaskFeedback.value = {
    level: 'info',
    message:
      '已保留附件分析摘要。你可以继续说明“根据附件补全模块树”或“根据附件修改当前需求资产”。',
  }

  latestAssistantResponse.value = {
    messageId: `attachment_change_ack_${Date.now()}`,
    intent: 'ANALYZE_ATTACHMENT',
    reply:
      '已保留附件分析摘要，暂不执行模块树或资产变更。你可以继续用自然语言说明下一步处理方式。',
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    actions: [],
  }
}

async function proposeAssetEditFromAttachmentDrivenChange(
  proposal: AttachmentDrivenChangeProposal,
  action: AttachmentDrivenChangeAction,
  instruction: string,
) {
  if (!selectedModule.value?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先在模块树中选择一个节点，再根据附件生成资产局部修订提案。',
    }
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const targetArtifactType =
      resolveAttachmentDrivenTargetArtifactType(proposal, action) ||
      selectedArtifactType.value

    const response = await proposeAssistantAssetEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementChangeProposal: {
          proposalId: proposal.proposalId || null,
          source: proposal.source || 'ATTACHMENT_DRIVEN_CHANGE',
          status: 'CONFIRMED',
          originalInstruction: instruction,
          summary:
            proposal.summary ||
            '根据附件材料生成资产局部修订提案。',
          changeType: 'UPDATE',
          scopeType: 'ATTACHMENT_DRIVEN_ASSET_PATCH',
          targetNode: proposal.targetNode || {
            nodeId: selectedModule.value.id,
            nodeName: getModuleDisplayName(selectedModule.value),
            nodeType: selectedModule.value.moduleType || null,
            moduleKey: selectedModule.value.moduleKey || null,
          },
          affectedArtifacts:
            targetArtifactType && isRequirementArtifactType(targetArtifactType)
              ? [targetArtifactType]
              : proposal.affectedArtifacts || [],
          riskNotes: proposal.riskNotes || [
            '附件驱动修订只生成资产编辑提案，不会直接覆盖当前版本。',
          ],
          recommendedSteps: proposal.recommendedSteps || [
            '查看右侧 Assistant 的资产编辑提案。',
            '确认后保存为新的待确认版本。',
          ],
          attachmentSummary: proposal.attachmentSummary || null,
        },
        instruction,
        selectedNodeId: selectedModule.value.id || null,
        selectedArtifactType: targetArtifactType || selectedArtifactType.value,
      },
    )

    latestAssistantResponse.value = response

    const responseArtifactType =
      resolveAssetEditProposalArtifactType(response) ||
      targetArtifactType

    if (responseArtifactType) {
      await handleViewArtifact(responseArtifactType)
    }

    artifactTaskFeedback.value = {
      level: 'info',
      message:
        '已根据附件材料生成资产局部修订提案，请在右侧 Assistant 中确认是否保存为新版本。',
    }
  } catch (error) {
    showAssistantSafeError(error, '根据附件生成资产编辑提案失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}


async function handleExecuteAttachmentArtifactDiffAction(
  proposal: AttachmentArtifactDiffProposal,
  action: AttachmentArtifactDiffAction,
  selectedDiffItemIds: string[] = [],
) {
  const actionType = normalizeAttachmentArtifactDiffActionType(action)
  const normalizedSelectedDiffItemIds = resolveSelectedAttachmentDiffItemIds(
    proposal,
    selectedDiffItemIds,
  )

  if ((proposal.diffItems?.length || 0) > 0 && !normalizedSelectedDiffItemIds.length) {
    artifactTaskFeedback.value = {
      level: 'info',
      message: '请至少选择一条差异后再继续生成提案。',
    }
    return
  }

  if (actionType === 'ASSET_EDIT_PROPOSAL') {
    await proposeAssetEditFromAttachmentArtifactDiff(
      proposal,
      action,
      normalizedSelectedDiffItemIds,
    )
    return
  }

  if (actionType === 'MODULE_TREE_PATCH') {
    await dispatchAssistantFollowup({
      message: buildAttachmentArtifactDiffFollowupMessage(
        proposal,
        action,
        normalizedSelectedDiffItemIds,
      ),
      expectedIntent: 'PROPOSE_MODULE_TREE_PATCH',
      selectedArtifactType: null,
      attachments: buildAttachmentRefsFromDiffProposal(proposal),
      selectedDiffItemIds: normalizedSelectedDiffItemIds,
      selectedDiffItems: getSelectedAttachmentDiffItems(
        proposal,
        normalizedSelectedDiffItemIds,
      ),
      ...resolveAttachmentDiffBaseline(proposal),
    })
    return
  }

  const selectedSummary = buildAttachmentDiffSelectionSummary(
    proposal,
    normalizedSelectedDiffItemIds,
  )

  artifactTaskFeedback.value = {
    level: 'info',
    message:
      '已保留已选择的附件与当前资产差异摘要。你可以继续说明希望基于哪一条差异生成局部修订提案。',
  }

  latestAssistantResponse.value = {
    messageId: `attachment_diff_ack_${Date.now()}`,
    intent: 'ANALYZE_ATTACHMENT',
    reply:
      `已保留附件与当前资产差异摘要，暂不执行资产修订或模块树变更。${selectedSummary}`,
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    attachmentDrivenChangeProposal: null,
    attachmentArtifactDiffProposal: null,
    actions: [],
  }
}

async function proposeAssetEditFromAttachmentArtifactDiff(
  proposal: AttachmentArtifactDiffProposal,
  action: AttachmentArtifactDiffAction,
  selectedDiffItemIds: string[] = [],
) {
  if (!selectedModule.value?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先在模块树中选择一个节点，再基于差异生成资产局部修订提案。',
    }
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const targetArtifactType =
      resolveAttachmentDiffTargetArtifactType(proposal, action) ||
      selectedArtifactType.value
    const normalizedSelectedDiffItemIds = resolveSelectedAttachmentDiffItemIds(
      proposal,
      selectedDiffItemIds,
    )
    const selectedDiffItems = getSelectedAttachmentDiffItems(
      proposal,
      normalizedSelectedDiffItemIds,
    )
    const instruction = buildAttachmentArtifactDiffFollowupMessage(
      proposal,
      action,
      normalizedSelectedDiffItemIds,
    )
    const baseline = resolveAttachmentDiffBaseline(proposal)

    const response = await proposeAssistantAssetEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementChangeProposal: {
          proposalId: proposal.proposalId || null,
          source: proposal.source || 'ATTACHMENT_ARTIFACT_DIFF',
          status: 'CONFIRMED',
          originalInstruction: instruction,
          summary:
            proposal.summary ||
            '根据附件与当前资产差异分析生成资产局部修订提案。',
          changeType: 'UPDATE',
          scopeType: 'ATTACHMENT_ARTIFACT_DIFF_PATCH',
          selectedDiffItemIds: normalizedSelectedDiffItemIds,
          selectedDiffItems,
          baseArtifactId: baseline.baseArtifactId,
          baseVersionNo: baseline.baseVersionNo,
          baseChecksum: baseline.baseChecksum,
          baseModuleTreeHash: baseline.baseModuleTreeHash,
          baseModuleTreeNodeCount: baseline.baseModuleTreeNodeCount,
          baselineValidationPolicy: 'BLOCK_IF_CURRENT_ARTIFACT_OR_MODULE_TREE_CHANGED',
          targetNode: proposal.targetNode || {
            nodeId: selectedModule.value.id,
            nodeName: getModuleDisplayName(selectedModule.value),
            nodeType: selectedModule.value.moduleType || null,
            moduleKey: selectedModule.value.moduleKey || null,
          },
          affectedArtifacts:
            targetArtifactType && isRequirementArtifactType(targetArtifactType)
              ? [targetArtifactType]
              : proposal.affectedArtifacts || [],
          riskNotes: proposal.riskNotes || [
            '差异分析只作为修订依据，保存前仍需要人工确认资产编辑提案。',
          ],
          recommendedSteps: proposal.recommendedSteps || [
            '查看右侧 Assistant 中的资产编辑提案。',
            '确认后保存为新的待确认版本。',
            '再根据影响分析选择性同步下游资产。',
          ],
          attachmentSummary: proposal.attachmentSummary || null,
        },
        instruction,
        selectedNodeId: selectedModule.value.id || null,
        selectedArtifactType: targetArtifactType || selectedArtifactType.value,
        selectedDiffItemIds: normalizedSelectedDiffItemIds,
        selectedDiffItems,
        ...baseline,
      },
    )

    latestAssistantResponse.value = response

    const responseArtifactType =
      resolveAssetEditProposalArtifactType(response) ||
      targetArtifactType

    if (responseArtifactType) {
      await handleViewArtifact(responseArtifactType)
    }

    artifactTaskFeedback.value = {
      level: 'info',
      message:
        '已基于附件与当前资产差异生成资产局部修订提案，请在右侧 Assistant 中确认是否保存为新版本。',
    }
  } catch (error) {
    showAssistantSafeError(error, '基于差异生成资产编辑提案失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}

function getAttachmentDiffItemId(
  item: AttachmentArtifactDiffItem,
  index: number,
): string {
  const id = item.id

  if (id !== null && id !== undefined && String(id).trim()) {
    return String(id)
  }

  return `diff-item-${index + 1}`
}

function resolveSelectedAttachmentDiffItemIds(
  proposal: AttachmentArtifactDiffProposal,
  selectedDiffItemIds: string[],
): string[] {
  const validIds = new Set(
    (proposal.diffItems || []).map((item, index) => getAttachmentDiffItemId(item, index)),
  )

  return Array.from(new Set(selectedDiffItemIds.map(String)))
    .filter((id) => validIds.has(id))
}

function getSelectedAttachmentDiffItems(
  proposal: AttachmentArtifactDiffProposal,
  selectedDiffItemIds: string[],
): AttachmentArtifactDiffItem[] {
  const selectedIds = new Set(resolveSelectedAttachmentDiffItemIds(
    proposal,
    selectedDiffItemIds,
  ))

  return (proposal.diffItems || []).filter((item, index) => {
    return selectedIds.has(getAttachmentDiffItemId(item, index))
  })
}

function buildAttachmentDiffSelectionSummary(
  proposal: AttachmentArtifactDiffProposal,
  selectedDiffItemIds: string[],
): string {
  const total = proposal.diffItems?.length || 0

  if (!total) {
    return ''
  }

  const selectedCount = resolveSelectedAttachmentDiffItemIds(
    proposal,
    selectedDiffItemIds,
  ).length

  return `本次选择了 ${selectedCount} / ${total} 条差异。`
}


function resolveAttachmentDiffBaseline(
  proposal: AttachmentArtifactDiffProposal,
): {
  baseArtifactId: string | null
  baseVersionNo: number | null
  baseChecksum: string | null
  baseModuleTreeHash: string | null
  baseModuleTreeNodeCount: number | null
} {
  const targetArtifact = proposal.targetArtifact
  const rawVersionNo =
    proposal.baseVersionNo ??
    targetArtifact?.versionNo ??
    null
  const parsedVersionNo =
    typeof rawVersionNo === 'number'
      ? rawVersionNo
      : Number(rawVersionNo || 0)

  return {
    baseArtifactId:
      proposal.baseArtifactId ||
      targetArtifact?.artifactId ||
      null,
    baseVersionNo: Number.isFinite(parsedVersionNo) && parsedVersionNo > 0
      ? parsedVersionNo
      : null,
    baseChecksum:
      proposal.baseChecksum ||
      targetArtifact?.checksum ||
      null,
    baseModuleTreeHash: proposal.baseModuleTreeHash || null,
    baseModuleTreeNodeCount:
      typeof proposal.baseModuleTreeNodeCount === 'number'
        ? proposal.baseModuleTreeNodeCount
        : null,
  }
}

function normalizeAttachmentArtifactDiffActionType(
  action: AttachmentArtifactDiffAction | null | undefined,
): string {
  return String(action?.type || '').trim().toUpperCase()
}

function resolveAttachmentDiffTargetArtifactType(
  proposal: AttachmentArtifactDiffProposal,
  action: AttachmentArtifactDiffAction,
): RequirementArtifactType | null {
  const candidates = [
    action.targetArtifactType,
    String(action.payload?.artifactType || ''),
    proposal.targetArtifactType,
    proposal.targetArtifact?.artifactType,
    ...(proposal.affectedArtifacts || []),
  ]

  for (const candidate of candidates) {
    if (typeof candidate === 'string' && isRequirementArtifactType(candidate)) {
      return candidate
    }
  }

  return null
}

function buildAttachmentArtifactDiffRequestMessage(
  proposal: AttachmentDrivenChangeProposal,
  action: AttachmentDrivenChangeAction,
): string {
  return [
    '请将附件材料与当前选中的需求资产进行差异对比，输出可审查的差异分析卡片，不要直接修改资产。',
    `目标资产：${resolveAttachmentDrivenTargetArtifactType(proposal, action) || selectedArtifactType.value || ''}`,
    proposal.summary ? `附件分析摘要：${proposal.summary}` : '',
    proposal.materialInstruction ? `材料理解：\n${proposal.materialInstruction}` : '',
  ].filter(Boolean).join('\n\n')
}

function buildAttachmentArtifactDiffFollowupMessage(
  proposal: AttachmentArtifactDiffProposal,
  action: AttachmentArtifactDiffAction,
  selectedDiffItemIds: string[] = [],
): string {
  const actionType = normalizeAttachmentArtifactDiffActionType(action)
  const normalizedSelectedDiffItemIds = resolveSelectedAttachmentDiffItemIds(
    proposal,
    selectedDiffItemIds,
  )
  const allDiffItems = proposal.diffItems || []
  const selectedDiffItems = normalizedSelectedDiffItemIds.length
    ? getSelectedAttachmentDiffItems(proposal, normalizedSelectedDiffItemIds)
    : allDiffItems
  const selectionSummary = allDiffItems.length
    ? `本次用户选择：${selectedDiffItems.length} / ${allDiffItems.length} 条差异。`
    : ''
  const selectedTitles = selectedDiffItems
    .map((item, index) => `${index + 1}. ${item.title || '待核对差异'}`)
    .join('\n')
  const diffSummary = selectedDiffItems
    .map((item, index) => {
      return [
        `${index + 1}. ${item.type || 'NEEDS_REVIEW'}：${item.title || '待核对差异'}`,
        item.attachmentExcerpt ? `附件依据：${item.attachmentExcerpt}` : '',
        item.currentEvidence ? `当前资产依据：${item.currentEvidence}` : '',
        item.recommendation ? `建议：${item.recommendation}` : '',
      ].filter(Boolean).join('\n')
    })
    .join('\n\n')

  const actionHint: Record<string, string> = {
    ASSET_EDIT_PROPOSAL:
      '请仅基于用户已选择的附件与当前资产差异，生成当前资产的局部修订提案，不要整份重写，也不要处理未选择的差异。',
    MODULE_TREE_PATCH:
      '请仅基于用户已选择的附件与当前资产差异，生成可审查的模块树变更提案，不要直接应用，也不要处理未选择的差异。',
    ANALYSIS_ONLY:
      '请仅保留用户已选择的差异摘要，暂不执行变更。',
  }

  return [
    actionHint[actionType] || '请基于以下已选择差异继续处理。',
    selectionSummary,
    selectedTitles ? `已选择差异标题：\n${selectedTitles}` : '',
    `目标资产：${resolveAttachmentDiffTargetArtifactType(proposal, action) || selectedArtifactType.value || ''}`,
    `原始指令：${proposal.originalInstruction || ''}`,
    proposal.summary ? `差异摘要：${proposal.summary}` : '',
    proposal.missingRequirements?.length
      ? `当前资产缺失，仅供背景参考，不代表全部都已被选择：\n${proposal.missingRequirements.join('\n')}`
      : '',
    proposal.possibleConflicts?.length
      ? `可能冲突，仅供背景参考，不代表全部都已被选择：\n${proposal.possibleConflicts.join('\n')}`
      : '',
    diffSummary ? `已选择差异清单：\n${diffSummary}` : '',
  ].filter(Boolean).join('\n\n')
}

function buildAttachmentRefsFromDiffProposal(
  proposal: AttachmentArtifactDiffProposal,
): AssistantAttachmentRef[] {
  const items = proposal.attachmentSummary?.items || []

  return items
    .map((item) => {
      const attachmentId = String(item.attachmentId || '').trim()

      if (!attachmentId) {
        return null
      }

      const sizeValue = item.size
      const size = typeof sizeValue === 'number'
        ? sizeValue
        : Number(sizeValue || 0)

      return {
        attachmentId,
        fileName: String(item.fileName || '附件'),
        mimeType: String(item.mimeType || 'application/octet-stream'),
        size: Number.isFinite(size) ? size : 0,
        parseStatus: String(item.parseStatus || 'PARSED'),
        parsedTextExcerpt: String(item.parsedTextExcerpt || ''),
        metadata: isPlainRecord(item.metadata) ? item.metadata : {},
      }
    })
    .filter((item): item is NonNullable<typeof item> => item !== null)
}

function normalizeAttachmentDrivenActionType(
  action: AttachmentDrivenChangeAction | null | undefined,
): string {
  return String(action?.type || '').trim().toUpperCase()
}

function resolveAttachmentDrivenTargetArtifactType(
  proposal: AttachmentDrivenChangeProposal,
  action: AttachmentDrivenChangeAction,
): RequirementArtifactType | null {
  const candidates = [
    action.targetArtifactType,
    String(action.payload?.artifactType || ''),
    proposal.targetArtifactType,
    ...(proposal.affectedArtifacts || []),
  ]

  for (const candidate of candidates) {
    if (typeof candidate === 'string' && isRequirementArtifactType(candidate)) {
      return candidate
    }
  }

  return null
}

function buildAttachmentDrivenFollowupMessage(
  proposal: AttachmentDrivenChangeProposal,
  action: AttachmentDrivenChangeAction,
): string {
  const actionType = normalizeAttachmentDrivenActionType(action)
  const targetArtifactType =
    resolveAttachmentDrivenTargetArtifactType(proposal, action) ||
    selectedArtifactType.value ||
    null

  const actionHint: Record<string, string> = {
    MODULE_TREE_PATCH:
      '请基于以下附件驱动变更分析，生成可审查的模块树变更提案，不要直接应用。',
    ASSET_EDIT_PROPOSAL:
      '请基于以下附件驱动变更分析，生成当前资产的局部修订提案，不要整份重写。',
    GENERATE_REQUIREMENT_ARTIFACT:
      '请基于以下附件驱动变更分析，为当前节点创建对应需求资产生成任务。',
    PAGE_UI_CHANGE_PROPOSAL:
      '请基于以下图片、原型图或页面截图材料，生成页面结构与 UI_CONTRACT 变更建议；优先输出 PAGE_SPEC 局部修订建议，不要直接覆盖 UI_CONTRACT。',
    ANALYSIS_ONLY:
      '请仅保留附件摘要，暂不执行变更。',
  }

  const attachmentItems = proposal.attachmentSummary?.items || []
  const attachmentSummary = attachmentItems
    .map(formatAttachmentDrivenFollowupAttachmentItem)
    .join('\n\n')

  const hasImageAttachment = attachmentItems.some(isImageAttachmentSummaryItem)
  const imageBoundary = hasImageAttachment
    ? '图片处理边界：当前阶段仅基于图片元数据、文件名和用户文字描述形成页面变更建议；未接入正式视觉理解模型，不得编造图片中的具体字段、按钮、颜色、布局或业务含义。用户需要人工补充关键信息。'
    : ''

  return [
    actionHint[actionType] || '请基于以下附件驱动变更分析继续处理。',
    targetArtifactType ? `目标资产：${targetArtifactType}` : '',
    imageBoundary,
    `原始指令：${proposal.originalInstruction || ''}`,
    proposal.summary ? `分析摘要：${proposal.summary}` : '',
    proposal.materialInstruction ? `材料理解：\n${proposal.materialInstruction}` : '',
    attachmentSummary ? `附件内容：\n${attachmentSummary}` : '',
  ].filter(Boolean).join('\n\n')
}

function formatAttachmentDrivenFollowupAttachmentItem(
  item: Record<string, unknown>,
): string {
  const fileName = String(item.fileName || '未命名附件')
  const mimeType = String(item.mimeType || '')
  const parseStatus = String(item.parseStatus || '')
  const excerpt = String(item.parsedTextExcerpt || '').trim()
  const dimensions = formatAttachmentDrivenImageDimensions(item)
  const metadata = isPlainRecord(item.metadata)
    ? item.metadata
    : {}
  const parser = typeof metadata.parser === 'string'
    ? metadata.parser
    : ''

  return [
    `附件：${fileName}`,
    mimeType ? `MIME：${mimeType}` : '',
    parseStatus ? `解析状态：${parseStatus}` : '',
    dimensions ? `图片尺寸：${dimensions}` : '',
    parser ? `解析器：${parser}` : '',
    excerpt ? `摘录：${excerpt}` : '摘录：暂无可用文本。',
  ].filter(Boolean).join('\n')
}

function isImageAttachmentSummaryItem(
  item: Record<string, unknown>,
): boolean {
  const mimeType = String(item.mimeType || '').toLowerCase()
  const fileName = String(item.fileName || '').toLowerCase()

  return mimeType.startsWith('image/') ||
    /\.(png|jpg|jpeg|webp|gif|bmp|svg)$/i.test(fileName)
}

function formatAttachmentDrivenImageDimensions(
  item: Record<string, unknown>,
): string {
  const metadata = isPlainRecord(item.metadata)
    ? item.metadata
    : {}

  const width =
    item.imageWidth ||
    item.width ||
    metadata.imageWidth ||
    metadata.width

  const height =
    item.imageHeight ||
    item.height ||
    metadata.imageHeight ||
    metadata.height

  if (!width || !height) {
    return ''
  }

  return `${width}×${height}`
}


function buildAttachmentRefsFromDrivenProposal(
  proposal: AttachmentDrivenChangeProposal,
): AssistantAttachmentRef[] {
  const items = proposal.attachmentSummary?.items || []

  return items
    .map((item) => {
      const attachmentId = String(item.attachmentId || '').trim()

      if (!attachmentId) {
        return null
      }

      const sizeValue = item.size
      const size = typeof sizeValue === 'number'
        ? sizeValue
        : Number(sizeValue || 0)

      return {
        attachmentId,
        fileName: String(item.fileName || '附件'),
        mimeType: String(item.mimeType || 'application/octet-stream'),
        size: Number.isFinite(size) ? size : 0,
        parseStatus: String(item.parseStatus || 'PARSED'),
        parsedTextExcerpt: String(item.parsedTextExcerpt || ''),
        metadata: isPlainRecord(item.metadata) ? item.metadata : {},
      }
    })
    .filter((item): item is NonNullable<typeof item> => item !== null)
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}



async function handleSaveManualArtifactEdit(
  payload: ManualArtifactEditPayload,
) {
  if (!selectedModule.value?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个模块树节点，再保存资产文档新版本。',
    }
    return
  }

  if (!isRequirementArtifactType(payload.artifactType)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前资产类型不支持保存。',
    }
    return
  }

  const documentLabel = payload.artifactType === 'MODULE_PRD'
    ? '模块需求'
    : '文档'

  const confirmed = window.confirm(
    `确认保存当前${documentLabel}修改吗？\n\n` +
      '保存后会生成新的版本，旧版本仍会保留；后续交互原型和开发交接会以新版本为准。',
  )

  if (!confirmed) {
    return
  }

  manualArtifactEditSaving.value = true

  try {
    const response = await saveManualArtifactEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      {
        artifactId: payload.artifactId || null,
        artifactType: payload.artifactType,
        contentMarkdown: payload.contentMarkdown,
        reason: payload.reason || '',
      },
    )

    const artifactType = response.artifactType

    if (isRequirementArtifactType(artifactType)) {
      selectedArtifactType.value = artifactType

      await loadCurrentArtifactByType(
        selectedModule.value,
        artifactType,
      )

      await Promise.allSettled([
        loadAllCurrentModuleArtifacts(selectedModule.value),
        loadCurrentModuleAgentTasks(),
        loadUiContractReadiness(),
        loadDevelopmentHandoffReadiness(),
      ])

      const downstream = getNodeAwareDownstreamArtifactTypes(
        artifactType,
        selectedModule.value,
      )

      if (downstream.length > 0) {
        handleOpenImpactAnalysis(artifactType)
      }

      await saveCurrentWorkspaceState({
        selectedModuleId: selectedModule.value.id,
        selectedArtifactType: artifactType,
        selectedArtifactId: response.artifactId || null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: 'manual-artifact-document-edit',
          artifactType,
          versionNo: response.versionNo,
        },
      })
    }

    artifactTaskFeedback.value = {
      level: 'success',
      message:
        response.message ||
        `${documentLabel}已保存为新版本 V${response.versionNo}。`,
    }
  } catch (error) {
    window.alert(resolveErrorMessage(error, '保存文档编辑版本失败。'))
  } finally {
    manualArtifactEditSaving.value = false
  }
}


async function handleConfirmAssetEditProposal(
  proposal: AssetEditProposal,
) {
  if (!proposal) {
    return
  }

  const confirmed = window.confirm(
    '确认保存这次局部修订吗？\n\n系统只会应用本次局部 Patch，保存为新的待确认版本；旧版本会保留，受影响下游资产会标记为“受上游影响”。',
  )

  if (!confirmed) {
    return
  }

  isSendingAssistantMessage.value = true

  try {
    const response = await confirmAssistantAssetEditProposal(
      props.workspace.workspaceId,
      props.project.projectCode,
      proposal,
      '',
    )

    const artifactType = response.artifactType

    if (isRequirementArtifactType(artifactType) && selectedModule.value) {
      await loadCurrentArtifactByType(
        selectedModule.value,
        artifactType,
      )
      selectedArtifactType.value = artifactType

      const downstream = getNodeAwareDownstreamArtifactTypes(
        artifactType,
        selectedModule.value,
      )

      if (downstream.length > 0) {
        handleOpenImpactAnalysis(artifactType)
      }
    }

    await Promise.allSettled([
      selectedModule.value ? loadAllCurrentModuleArtifacts(selectedModule.value) : Promise.resolve(),
      loadCurrentModuleAgentTasks(),
      loadUiContractReadiness(),
      loadDevelopmentHandoffReadiness(),
    ])

    const savedMessage =
      response.message ||
      `已保存 ${response.artifactType} 局部修订版本 V${response.versionNo}。`

    artifactTaskFeedback.value = {
      level: 'success',
      message: savedMessage,
    }

    latestAssistantResponse.value = {
      messageId: `asset_edit_saved_${Date.now()}`,
      intent: 'CONFIRM_ASSET_EDIT',
      reply: `${savedMessage} 新版本状态为“新版本待确认”；旧版本已保留。下游若显示“受上游影响”，后续可在影响分析中逐个使用 AI 局部同步修订。`,
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      actions: [],
    }
  } catch (error) {
    showAssistantSafeError(error, '确认资产编辑提案失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}

function resolveAssetEditProposalArtifactType(
  response: AssistantMessageResponse,
): RequirementArtifactType | null {
  const action = response.actions?.find((item) =>
    item.type === 'ASSET_EDIT_PROPOSAL' || item.type === 'OPEN_ASSET_EDIT_PROPOSAL',
  )

  const payload = action?.payload || {}
  const proposal = payload.proposal as AssetEditProposal | undefined
  const artifactType = proposal?.targetArtifactType || proposal?.targetArtifact?.artifactType

  if (typeof artifactType === 'string' && isRequirementArtifactType(artifactType)) {
    return artifactType
  }

  return null
}

async function dispatchAssistantFollowup(payload: {
  message: string
  expectedIntent: string
  selectedArtifactType?: string | null
  attachments?: AssistantAttachmentRef[]
  selectedDiffItemIds?: string[]
  selectedDiffItems?: AttachmentArtifactDiffItem[]
  baseArtifactId?: string | null
  baseVersionNo?: number | null
  baseChecksum?: string | null
  baseModuleTreeHash?: string | null
  baseModuleTreeNodeCount?: number | null
}) {
  const text = payload.message.trim()

  if (!text) {
    return
  }

  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true

  try {
    const response = await sendAssistantMessage(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        message: text,
        selectedNodeId: selectedModule.value?.id || null,
        selectedArtifactType:
          payload.selectedArtifactType === undefined
            ? selectedArtifactType.value || null
            : payload.selectedArtifactType,
        expectedIntent: payload.expectedIntent,
        attachments: payload.attachments || [],
        selectedDiffItemIds: payload.selectedDiffItemIds || [],
        selectedDiffItems: payload.selectedDiffItems || [],
        baseArtifactId: payload.baseArtifactId || null,
        baseVersionNo: payload.baseVersionNo ?? null,
        baseChecksum: payload.baseChecksum || null,
        baseModuleTreeHash: payload.baseModuleTreeHash || null,
        baseModuleTreeNodeCount: payload.baseModuleTreeNodeCount ?? null,
      },
    )

    latestAssistantResponse.value = response
    await loadAssistantMessageHistory()

    if (response.intent === 'PROPOSE_MODULE_TREE_PATCH' && response.proposal) {
      treePatchProposal.value = response.proposal
      return
    }

    if (response.intent === 'EXECUTE_UI_COMMAND') {
      await executeAssistantActions(response.actions || [])
      return
    }

    if (response.intent === 'GENERATE_REQUIREMENT_ARTIFACT') {
      await refreshAfterAssistantTaskDispatch()
    }
  } catch (error) {
    showAssistantSafeError(error, 'AI Assistant 后续执行失败。')
  } finally {
    isSendingAssistantMessage.value = false
  }
}

function shouldConvertRequirementChangeToTreePatch(
  proposal: RequirementChangeProposal,
): boolean {
  const instruction = String(
    proposal.originalInstruction ||
      proposal.summary ||
      '',
  ).trim()

  const scopeType = String(proposal.scopeType || '').toUpperCase()
  const changeType = String(proposal.changeType || '').toUpperCase()

  const structuralChangeTypes = [
    'ADD',
    'CREATE',
    'NEW',
    'REMOVE',
    'DELETE',
    'MOVE',
    'SPLIT',
    'MERGE',
    'RENAME',
    'ADD_NODE',
    'ADD_PAGE',
    'MOVE_NODE',
    'DELETE_NODE',
    'UPDATE_NODE',
  ]

  const structuralScopeTypes = [
    'MODULE_TREE',
    'PAGE',
    'PAGE_OR_UI',
    'PAGE_STRUCTURE',
    'ROUTE',
    'NAVIGATION',
    'MODULE',
    'SUBMODULE',
    'FEATURE',
    'NODE',
  ]

  const isStructuralChange = structuralChangeTypes.some(
    (type) => changeType === type || changeType.includes(type),
  )

  const isStructuralScope = structuralScopeTypes.some(
    (type) => scopeType === type || scopeType.includes(type),
  )

  if (scopeType === 'MODULE_TREE') {
    return true
  }

  if (isStructuralChange && isStructuralScope) {
    return true
  }

  const hasStructuralAction = containsAnyText(
    instruction,
    [
      '新增',
      '增加',
      '添加',
      '创建',
      '新建',
      '删掉',
      '删除',
      '移除',
      '去掉',
      '移动',
      '挪到',
      '放到',
      '归到',
      '拆成',
      '拆分',
      '合并',
      '重命名',
      '改名',
    ],
  )

  const hasStructuralObject = containsAnyText(
    instruction,
    [
      '模块树',
      '节点',
      '业务域',
      '模块',
      '子模块',
      '页面',
      '页',
      '功能点',
      '路由',
      '菜单',
      '用户中心',
      '登录页',
      '注册页',
      '密码找回页',
      '找回密码页',
      '列表页',
      '详情页',
      '表单页',
    ],
  )

  const hasPlacementExpression = containsAnyText(
    instruction,
    [
      '放在',
      '放到',
      '归到',
      '下面',
      '下方',
      '上面',
      '挂到',
      '属于',
      '移动到',
      '挪到',
    ],
  )

  return hasStructuralAction && (hasStructuralObject || hasPlacementExpression)
}

function resolvePrimaryAffectedArtifactType(
  proposal: RequirementChangeProposal,
): RequirementArtifactType | null {
  const affectedArtifacts = proposal.affectedArtifacts || []

  for (const artifact of affectedArtifacts) {
    if (isRequirementArtifactType(artifact)) {
      return artifact
    }
  }

  if (selectedArtifactType.value) {
    return selectedArtifactType.value
  }

  return null
}

async function executeAssistantActions(
  actions: AssistantActionResponse[],
) {
  if (!actions?.length) {
    return
  }

  for (const action of actions) {
    await executeAssistantAction(action)
  }
}

async function executeAssistantAction(
  action: AssistantActionResponse,
) {
  const actionType = String(action.type || '').toUpperCase()
  const artifactType = resolveArtifactTypeFromAction(action)

  if (actionType === 'VIEW_ARTIFACT') {
    if (!artifactType) {
      artifactTaskFeedback.value = {
        level: 'error',
        message: 'AI Assistant 已识别为查看资产指令，但没有定位到资产类型。',
      }
      return
    }

    await handleViewArtifact(artifactType)
    return
  }

  if (actionType === 'OPEN_QUALITY_CHECK') {
    handleOpenQualityCheck()
    return
  }

  if (actionType === 'OPEN_ARTIFACT_VERSIONS') {
    if (!artifactType) {
      artifactTaskFeedback.value = {
        level: 'error',
        message: '请先选择一个资产，再通过聊天打开版本历史。',
      }
      return
    }

    await handleOpenArtifactVersions(artifactType)
    return
  }

  if (actionType === 'OPEN_IMPACT_ANALYSIS') {
    if (!artifactType) {
      artifactTaskFeedback.value = {
        level: 'error',
        message: '请先选择一个资产，再通过聊天打开影响分析。',
      }
      return
    }

    handleOpenImpactAnalysis(artifactType)
    return
  }

  if (actionType === 'OPEN_UI_CONTRACT_PREVIEW') {
    await handlePreviewUiContract()
    return
  }

  if (actionType === 'REFRESH_UI_CONTRACT_READINESS') {
    await handleRefreshUiContractReadiness()
    artifactTaskFeedback.value = {
      level: 'info',
      message: '已根据聊天指令刷新交互原型生成准备度。',
    }
    return
  }

  if (actionType === 'OPEN_DEVELOPMENT_TAB') {
    await handleChangeAgentTab('development')
    return
  }

  if (actionType === 'OPEN_REQUIREMENT_TAB') {
    await handleChangeAgentTab('requirement')
    return
  }

  if (actionType === 'OPEN_PROJECT_TASK_CENTER') {
    await openProjectTaskCenter()
    return
  }
}

function resolveArtifactTypeFromAction(
  action: AssistantActionResponse,
): RequirementArtifactType | null {
  const payload = action.payload || {}
  const rawArtifactType = payload.artifactType

  if (
    typeof rawArtifactType === 'string' &&
    isRequirementArtifactType(rawArtifactType)
  ) {
    return rawArtifactType
  }

  return selectedArtifactType.value
}

function containsAnyText(
  text: string,
  keywords: string[],
): boolean {
  return keywords.some((keyword) => text.includes(keyword))
}


async function handleApplyTreePatchProposal(
  proposalFromMessage?: ModuleTreePatchProposalResponse,
) {
  const proposal = proposalFromMessage || treePatchProposal.value
  const operations = proposal?.proposal?.operations || []

  if (!operations.length) {
    showAssistantInlineError(new Error('当前没有可应用的模块树变更操作。'), '当前没有可应用的模块树变更操作。')
    return
  }

  isApplyingTreePatch.value = true

  try {
    const currentSelectedModuleId = selectedModule.value?.id || null
    const addOperations = operations.filter(
      (operation) => operation.op === 'ADD_NODE',
    )

const proposalRecord = proposal as unknown as Record<string, unknown>

const proposalPayload =
  proposalRecord['proposal'] &&
  typeof proposalRecord['proposal'] === 'object'
    ? proposalRecord['proposal'] as Record<string, unknown>
    : {}

const patchDescription = String(
  proposalRecord['description'] ||
    proposalPayload['description'] ||
    'AI 模块树变更提案',
)

const patchSource = String(
  proposalRecord['source'] ||
    proposalPayload['source'] ||
    'AI_TREE_PATCH',
)

const response = await applyModuleTreePatch(
  props.workspace.workspaceId,
  props.project.projectCode,
  {
    description: patchDescription,
    source: patchSource,
    operations,
    baseModuleTreeHash:
      typeof proposalPayload['baseModuleTreeHash'] === 'string'
        ? proposalPayload['baseModuleTreeHash']
        : null,
    baseModuleTreeNodeCount:
      typeof proposalPayload['baseModuleTreeNodeCount'] === 'number'
        ? proposalPayload['baseModuleTreeNodeCount']
        : null,
  },
)

    modules.value = response.tree
    treePatchProposal.value = null

    const createdNodes = resolveCreatedNodesFromTreePatch(
      addOperations,
      modules.value,
    )
    const preferredCreatedNode = resolvePreferredCreatedNode(createdNodes)

    if (preferredCreatedNode) {
      await handleSelectModule(preferredCreatedNode)

      const recommendedArtifactType =
        resolveRecommendedArtifactTypeForCreatedNode(preferredCreatedNode)

      if (recommendedArtifactType) {
        selectedArtifactType.value = recommendedArtifactType

        await saveCurrentWorkspaceState({
          selectedModuleId: preferredCreatedNode.id || null,
          selectedArtifactType: recommendedArtifactType,
          selectedArtifactId: getArtifactByType(recommendedArtifactType)?.artifactId || null,
          activeAgentTab: activeTab.value,
          uiState: {
            lastAction: 'apply-module-tree-patch-select-created-node',
            assistantIntent: 'PROPOSE_MODULE_TREE_PATCH',
          },
        })
      }
    } else if (currentSelectedModuleId) {
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

    const successMessage = buildTreePatchApplySuccessMessage(
      createdNodes,
      response.appliedOperations || [],
    )

    artifactTaskFeedback.value = {
      level: 'success',
      message: successMessage,
    }

    latestAssistantResponse.value = {
      messageId: `module_tree_patch_applied_${Date.now()}`,
      intent: 'PROPOSE_MODULE_TREE_PATCH',
      reply: successMessage,
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      actions: [],
    }
  } catch (error) {
    showAssistantSafeError(error, '应用 AI 模块树变更提案失败。')
  } finally {
    isApplyingTreePatch.value = false
  }
}

function resolveCreatedNodesFromTreePatch(
  addOperations: ModuleTreePatchOperationRequest[],
  tree: ModuleTreeNode[],
): ModuleTreeNode[] {
  if (!addOperations.length || !tree.length) {
    return []
  }

  const flatNodes = flattenModuleTreeNodes(tree)
  const result: ModuleTreeNode[] = []

  for (const operation of addOperations) {
    const matchedNode = findCreatedNodeForAddOperation(
      operation,
      flatNodes,
    )

    if (
      matchedNode?.id &&
      !result.some((item) => item.id === matchedNode.id)
    ) {
      result.push(matchedNode)
    }
  }

  return result
}

function findCreatedNodeForAddOperation(
  operation: ModuleTreePatchOperationRequest,
  flatNodes: ModuleTreeNode[],
): ModuleTreeNode | null {
  const expectedName = normalizeTreePatchMatchText(operation.moduleName)
  const expectedKey = normalizeTreePatchMatchText(operation.moduleKey)
  const expectedType = normalizeTreePatchMatchText(operation.moduleType)
  const expectedParentId = normalizeTreePatchMatchText(operation.parentId)

  const candidates = flatNodes
    .map((node) => {
      const nodeName = normalizeTreePatchMatchText(
        node.moduleName || node.name || node.title,
      )
      const nodeKey = normalizeTreePatchMatchText(node.moduleKey || node.key)
      const nodeType = normalizeTreePatchMatchText(node.moduleType || node.type)
      const nodeParentId = normalizeTreePatchMatchText(node.parentId)

      let score = 0

      if (expectedKey && nodeKey && expectedKey === nodeKey) {
        score += 100
      }

      if (expectedName && nodeName && expectedName === nodeName) {
        score += 80
      }

      if (expectedType && nodeType && expectedType === nodeType) {
        score += 30
      }

      if (expectedParentId && nodeParentId && expectedParentId === nodeParentId) {
        score += 20
      }

      return {
        node,
        score,
      }
    })
    .filter((item) => item.score >= 80)
    .sort((a, b) => b.score - a.score)

  return candidates[0]?.node || null
}

function resolvePreferredCreatedNode(
  createdNodes: ModuleTreeNode[],
): ModuleTreeNode | null {
  if (!createdNodes.length) {
    return null
  }

  return (
    createdNodes.find((node) => isPageNode(node)) ||
    createdNodes.find((node) => isFeatureNode(node)) ||
    createdNodes[0] ||
    null
  )
}

function resolveRecommendedArtifactTypeForCreatedNode(
  node: ModuleTreeNode | null,
): RequirementArtifactType | null {
  if (!node) {
    return null
  }

  if (isPageNode(node)) {
    return 'PAGE_SPEC'
  }

  if (isFeatureNode(node)) {
    return 'MODULE_PRD'
  }

  return 'MODULE_PRD'
}

function buildTreePatchApplySuccessMessage(
  createdNodes: ModuleTreeNode[],
  appliedOperations: string[],
): string {
  const createdNodeNames = createdNodes
    .map((node) => {
      const type = node.moduleType || node.type || 'NODE'
      const name = getModuleDisplayName(node)
      return `“${name}”/${type}`
    })
    .filter(Boolean)

  const createdPart = createdNodeNames.length
    ? `已新增节点：${createdNodeNames.join('、')}。`
    : appliedOperations.length
      ? `已应用操作：${appliedOperations.join('、')}。`
      : '模块树变更提案已应用。'

  const hasPageNode = createdNodes.some((node) => isPageNode(node))

  const nextStep = hasPageNode
    ? '建议下一步先为新增页面生成 PAGE_SPEC；PAGE_SPEC 确认后，再生成 UI_CONTRACT 和后续开发交接资产。'
    : '建议下一步检查新增节点的核心需求资产；如已有上游资产受影响，可在影响分析中使用 AI 局部同步修订。'

  return [
    `模块树变更提案已应用。${createdPart}`,
    nextStep,
    '系统不会自动改写旧资产；旧资产需要同步时，请在影响分析或版本历史中按需处理。',
  ].join('\n')
}

function flattenModuleTreeNodes(
  nodes: ModuleTreeNode[],
): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []

  const walk = (items: ModuleTreeNode[]) => {
    for (const item of items) {
      result.push(item)
      walk(item.children || [])
    }
  }

  walk(nodes || [])
  return result
}

function normalizeTreePatchMatchText(
  value: unknown,
): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '')
}


function isA9VirtualComponentNode(node: ModuleTreeNode | null | undefined): boolean {
  const extra = safeRecord(node?.extra)
  return (
    String(node?.moduleType || node?.type || extra.nodeType || '').toUpperCase() === 'COMPONENT' ||
    Boolean(extra.virtualComponent)
  )
}

function resolveA9VirtualComponentSelection(
  node: ModuleTreeNode | null | undefined,
): { owningPageNodeId: string; componentId: string } | null {
  if (!node || !isA9VirtualComponentNode(node)) return null
  const extra = safeRecord(node.extra)
  const owningPageNodeId = String(extra.owningPageNodeId || node.parentId || '').trim()
  const componentId = String(extra.componentId || node.moduleKey || node.id || '').trim()

  if (!owningPageNodeId || !componentId) {
    return null
  }

  return {
    owningPageNodeId,
    componentId,
  }
}

function syncA9VirtualComponentNodesForPage(pageNode: ModuleTreeNode | null | undefined) {
  if (!pageNode?.id || resolveRequirementNodeType(pageNode) !== 'PAGE') {
    return
  }

  const target = findA9ModuleTreeNodeById(modules.value, pageNode.id) || pageNode
  const persistentChildren = (target.children || []).filter((child) => !isA9VirtualComponentNode(child))
  const virtualChildren = buildA9VirtualComponentNodes(target)

  target.children = [...persistentChildren, ...virtualChildren]
  modules.value = [...modules.value]
}

function buildA9VirtualComponentNodes(pageNode: ModuleTreeNode): ModuleTreeNode[] {
  const contract = normalizeA9UiContractCandidate(
    uiContractArtifact.value?.content || tryParseA9Json(uiContractMarkdown.value),
  )

  const components = flattenA9UiContractComponents(contract?.page?.componentTree || [])

  return components
    .filter((component) => Boolean(String(component.componentId || '').trim()))
    .slice(0, 0)
    .map((component, index) => {
      const componentRecord = component as Record<string, any>
      const componentId = String(component.componentId || '').trim()
      const componentType = String(component.type || '').toUpperCase() || 'COMPONENT'
      const componentTitle = String(
        component.title ||
          componentRecord.label ||
          componentRecord.name ||
          componentRecord.text ||
          componentId,
      ).trim()

      return {
        id: `component:${pageNode.id}:${componentId}`,
        parentId: pageNode.id || null,
        moduleKey: componentId,
        moduleName: componentTitle || componentId,
        moduleType: 'COMPONENT',
        modulePath: `${pageNode.modulePath || pageNode.moduleName || '页面'} / ${componentTitle || componentId}`,
        sortOrder: index + 1,
        status: 'VIRTUAL',
        extra: {
          virtualComponent: true,
          nodeType: 'COMPONENT',
          owningPageNodeId: pageNode.id,
          componentId,
          componentType,
          componentTitle,
        },
        children: [],
      } as ModuleTreeNode
    })
}

function flattenA9UiContractComponents(components: any[]): any[] {
  const result: any[] = []
  const visit = (items: any[]) => {
    for (const item of items || []) {
      if (!item || typeof item !== 'object') continue
      result.push(item)
      if (Array.isArray(item.children)) visit(item.children)
    }
  }
  visit(components)
  return result
}

function normalizeA9UiContractCandidate(value: unknown): any | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const candidate = value as Record<string, any>
  const page = candidate.page
  if (!page || typeof page !== 'object') return null
  if (!Array.isArray(page.componentTree)) return null
  return candidate
}

function tryParseA9Json(value: unknown): unknown {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}


function findA9ModuleTreeNodeById(
  nodes: ModuleTreeNode[],
  nodeId: string | null | undefined,
): ModuleTreeNode | null {
  if (!nodeId) return null

  for (const node of nodes || []) {
    if (node.id === nodeId) return node

    const matched = findA9ModuleTreeNodeById(node.children || [], nodeId)
    if (matched) return matched
  }

  return null
}

function safeRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : {}
}

async function handleSelectModule(node: ModuleTreeNode) {
  const componentSelection = resolveA9VirtualComponentSelection(node)

  if (componentSelection) {
    const pageNode = findA9ModuleTreeNodeById(
      modules.value,
      componentSelection.owningPageNodeId,
    )

    if (pageNode) {
      selectedRequirementTreeNodeId.value = node.id || null
      focusedUiComponentId.value = componentSelection.componentId
      focusedUiComponentLabel.value = componentSelection.componentId
      await handleSelectModule(pageNode)
      selectedRequirementTreeNodeId.value = node.id || null
      focusedUiComponentId.value = componentSelection.componentId
    }

    return
  }

  selectedRequirementTreeNodeId.value = node.id || null
  focusedUiComponentId.value = null
  focusedUiComponentLabel.value = ''
  focusedPrototypeElementSelection.value = null
  focusedUiElementSelection.value = null

  stopTaskPolling()

  selectModule(node)
  uiContractReadiness.value = null
  uiContractReadinessError.value = ''
  artifactTaskFeedback.value = null
  clearArtifacts()
  clearCurrentModuleAgentTasks()
  selectedArtifactType.value = null

  for (const definition of REQUIREMENT_ARTIFACT_DEFINITIONS) {
    setStepStatus(definition.stepId, 'pending')
  }

  await loadAllCurrentModuleArtifacts(node)
  await loadCurrentModuleAgentTasks()
  await loadUiContractReadiness(node)
  await loadCurrentUiContractSnapshot(node)

  syncA9VirtualComponentNodesForPage(node)
  await loadDevelopmentHandoffReadiness(node)

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


function showAssistantInlineError(
  error: unknown,
  fallback: string,
) {
  const message = resolveErrorMessage(error, fallback)

  artifactTaskFeedback.value = {
    level: 'error',
    message,
  }

  latestAssistantResponse.value = {
    messageId: `assistant_error_${Date.now()}`,
    intent: 'GENERAL_QA',
    reply: message,
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    attachmentDrivenChangeProposal: null,
    attachmentArtifactDiffProposal: null,
    actions: [],
  }
}

async function loadUiContractReadiness(
  node: ModuleTreeNode | null = selectedModule.value,
): Promise<UiContractGenerationReadinessResponse | null> {
  if (!node?.id || !isPageNode(node)) {
    uiContractReadiness.value = null
    uiContractReadinessLoading.value = false
    uiContractReadinessError.value = ''
    return null
  }

  const targetNodeId = node.id

  uiContractReadinessLoading.value = true
  uiContractReadinessError.value = ''

  try {
    const readiness = await fetchUiContractReadiness(
      props.workspace.workspaceId,
      props.project.projectCode,
      targetNodeId,
    )

    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    uiContractReadiness.value = readiness
    return readiness
  } catch (error) {
    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    const message = resolveErrorMessage(
      error,
      '交互原型生成准备度检查失败。',
    )

    uiContractReadiness.value = null
    uiContractReadinessError.value = message
    return null
  } finally {
    if (selectedModule.value?.id === targetNodeId) {
      uiContractReadinessLoading.value = false
    }
  }
}

async function handleRefreshUiContractReadiness() {
  await loadUiContractReadiness()
}

async function loadCurrentUiContractSnapshot(
  node: ModuleTreeNode | null = selectedModule.value,
): Promise<ConfirmedUiContractSnapshot | null> {
  if (!node?.id || !isPageNode(node)) {
    uiContractSnapshot.value = null
    uiContractSnapshotLoading.value = false
    uiContractSnapshotError.value = ''
    return null
  }

  const targetNodeId = node.id

  uiContractSnapshotLoading.value = true
  uiContractSnapshotError.value = ''

  try {
    const snapshot = await fetchCurrentUiContractSnapshot(
      props.workspace.workspaceId,
      props.project.projectCode,
      targetNodeId,
    )

    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    uiContractSnapshot.value = snapshot
    return snapshot
  } catch (error) {
    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    uiContractSnapshot.value = null
    uiContractSnapshotError.value = resolveErrorMessage(
      error,
      '读取交互原型确认快照失败。',
    )
    return null
  } finally {
    if (selectedModule.value?.id === targetNodeId) {
      uiContractSnapshotLoading.value = false
    }
  }
}


async function loadDevelopmentHandoffReadiness(
  node: ModuleTreeNode | null = selectedModule.value,
): Promise<DevelopmentHandoffReadinessResponse | null> {
  if (!node?.id) {
    developmentHandoffReadiness.value = null
    developmentHandoffLoading.value = false
    developmentHandoffError.value = ''
    return null
  }

  const targetNodeId = node.id

  developmentHandoffLoading.value = true
  developmentHandoffError.value = ''

  try {
    const readiness = await fetchDevelopmentHandoffReadiness(
      props.workspace.workspaceId,
      props.project.projectCode,
      targetNodeId,
    )

    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    developmentHandoffReadiness.value = readiness
    return readiness
  } catch (error) {
    if (selectedModule.value?.id !== targetNodeId) {
      return null
    }

    developmentHandoffReadiness.value = null
    developmentHandoffError.value = resolveErrorMessage(
      error,
      '开发交接准备度检查失败。',
    )
    return null
  } finally {
    if (selectedModule.value?.id === targetNodeId) {
      developmentHandoffLoading.value = false
    }
  }
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
  const module = selectedModule.value

  if (!module) {
    return
  }

  const nodeType = resolveRequirementNodeType(module)

  if (nodeType === 'SUBMODULE') {
    const reply = [
      '当前选中的是 SUBMODULE，它是场景/分组层，不直接生成独立需求资产。',
      '',
      '你可以在右侧 AI 助手继续输入：',
      '1. 准备本范围页面资产：为该 SUBMODULE 下的 PAGE 创建 PAGE_SPEC + UI_CONTRACT 任务。',
      '2. 准备所属 MODULE 核心资产：为上级 MODULE 创建 MODULE_PRD、BUSINESS_FLOW、PAGE_MAP、DATA_MODEL、API_CONTRACT、TEST_SPEC、DEV_HANDOFF_PACKAGE。',
    ].join('\n')

    artifactTaskFeedback.value = {
      level: 'info',
      message: 'SUBMODULE 不直接生成资产，请选择准备本范围页面资产或所属 MODULE 核心资产。',
    }

    latestAssistantResponse.value = buildLocalAssistantResponse(reply, 'submodule_asset_options')
    return
  }

  if (nodeType === 'MODULE') {
    artifactTaskFeedback.value = {
      level: 'info',
      message: '正在为当前 MODULE 创建核心资产任务：MODULE_PRD、BUSINESS_FLOW、PAGE_MAP、DATA_MODEL、API_CONTRACT、TEST_SPEC、DEV_HANDOFF_PACKAGE；不会自动生成 UI_CONTRACT。',
    }
  }

  await startFullChainTask(false)
}


function collectDevelopmentPreparationNodeIds(root: ModuleTreeNode): string[] {
  const ids: string[] = []
  const seen = new Set<string>()

  function push(node: ModuleTreeNode) {
    if (!node.id || seen.has(node.id)) {
      return
    }

    ids.push(node.id)
    seen.add(node.id)
  }

  function visit(node: ModuleTreeNode) {
    const nodeType = resolveRequirementNodeType(node)

    if (node === root || nodeType === 'PAGE') {
      push(node)
    }

    for (const child of node.children || []) {
      visit(child)
    }
  }

  visit(root)

  return ids
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
async function handleGenerateUiContract() {
  const module = selectedModule.value

  if (!module) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个页面节点。',
    }
    return
  }

  if (isFeatureNode(module)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message:
        '当前选中的是页面功能点。请回到其所属 PAGE 页面节点生成交互原型。',
    }
    return
  }

  if (!isPageNode(module)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message:
        '交互原型只能基于 PAGE 页面节点生成。请在模块树中选择具体页面。',
    }
    return
  }

  const readiness = await loadUiContractReadiness(module)

  if (!readiness?.ready) {
    artifactTaskFeedback.value = {
      level: 'error',
      message:
        readiness?.blockers?.join('；') ||
        uiContractReadinessError.value ||
        '当前页面尚未满足交互原型生成条件。',
    }
    return
  }

  await startAsyncArtifactTask('GENERATE_UI_CONTRACT')
}

async function handleGenerateDevHandoffPackage() {
  const module = selectedModule.value

  if (!module?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个需求树节点，再生成开发交接包。',
    }
    return
  }

  isGeneratingDevHandoffPackageDirect.value = true
  artifactTaskFeedback.value = {
    level: 'info',
    message: '正在汇总当前节点的需求资产并生成开发交接包。',
  }

  try {
    const response = await generateDevHandoffPackageAsset(
      props.workspace.workspaceId,
      props.project.projectCode,
      module.id,
      {
        includeUnapproved: true,
        note: '由 Requirement Agent 主线入口生成。',
      },
    )

    await loadCurrentArtifactByType(
      module,
      'DEV_HANDOFF_PACKAGE',
    )

    selectedArtifactType.value = 'DEV_HANDOFF_PACKAGE'

    await Promise.allSettled([
      loadAllCurrentModuleArtifacts(module),
      loadCurrentModuleAgentTasks(),
      loadDevelopmentHandoffReadiness(module),
    ])

    await saveCurrentWorkspaceState({
      selectedModuleId: module.id,
      selectedArtifactType: 'DEV_HANDOFF_PACKAGE',
      selectedArtifactId: response.artifactId || null,
      activeAgentTab: activeTab.value,
      uiState: {
        lastAction: 'generate-dev-handoff-package-asset',
        artifactId: response.artifactId,
        versionNo: response.versionNo,
        blockerCount: response.blockers?.length || 0,
        warningCount: response.warnings?.length || 0,
      },
    })

    const blockerCount = response.blockers?.length || 0
    const warningCount = response.warnings?.length || 0

    artifactTaskFeedback.value = {
      level: blockerCount > 0 ? 'info' : 'success',
      message:
        response.message ||
        `开发交接包已保存为 V${response.versionNo}。`,
    }

    latestAssistantResponse.value = {
      messageId: `dev_handoff_package_${Date.now()}`,
      intent: 'GENERATE_REQUIREMENT_ARTIFACT',
      reply:
        response.message ||
        `已生成开发交接包 V${response.versionNo}。` +
          (blockerCount > 0
            ? ` 存在 ${blockerCount} 个阻断项，请先处理。`
            : warningCount > 0
              ? ` 存在 ${warningCount} 个待复核提醒。`
              : ' 可进入评审或交付给后续开发 Agent。'),
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      attachmentDrivenChangeProposal: null,
      attachmentArtifactDiffProposal: null,
      actions: [
        {
          type: 'VIEW_ARTIFACT',
          label: '查看开发交接包',
          payload: {
            artifactType: 'DEV_HANDOFF_PACKAGE',
            artifactId: response.artifactId,
            versionNo: response.versionNo,
          },
        },
      ],
    }
  } catch (error) {
    showAssistantInlineError(
      error,
      '生成开发交接包失败。',
    )
  } finally {
    isGeneratingDevHandoffPackageDirect.value = false
  }
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

async function handleOpenRelatedNodeArtifact(payload: {
  nodeId: string
  artifactType: RequirementArtifactType
}) {
  if (!payload.nodeId || !isRequirementArtifactType(payload.artifactType)) {
    return
  }

  const targetNode = findModuleTreeNodeById(
    modules.value,
    payload.nodeId,
  )

  if (!targetNode?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '未找到关联需求节点，请刷新模块树后重试。',
    }
    return
  }

  await handleSelectModule(targetNode)

  selectedArtifactType.value = payload.artifactType

  const artifact = getArtifactByType(payload.artifactType)

  await saveCurrentWorkspaceState({
    selectedModuleId: targetNode.id || null,
    selectedArtifactType: payload.artifactType,
    selectedArtifactId: artifact?.artifactId || null,
    activeAgentTab: activeTab.value,
    uiState: {
      lastAction: 'open-related-node-artifact',
      fromNodeId: payload.nodeId,
      artifactType: payload.artifactType,
    },
  })
}

function findModuleTreeNodeById(
  nodes: ModuleTreeNode[],
  nodeId: string,
): ModuleTreeNode | null {
  for (const node of nodes || []) {
    if (node.id === nodeId) {
      return node
    }

    const matchedChild = findModuleTreeNodeById(
      node.children || [],
      nodeId,
    )

    if (matchedChild) {
      return matchedChild
    }
  }

  return null
}

async function handlePreviewUiContract() {
  if (!selectedModule.value || !isPageNode(selectedModule.value)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择具体 PAGE 页面节点，再预览交互原型。',
    }
    return
  }

  if (!uiContractArtifact.value?.content) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前页面还没有可预览的 UI 契约，请先生成交互原型。',
    }
    return
  }

  await loadCurrentUiContractSnapshot(selectedModule.value)

  artifactTaskFeedback.value = null
  uiContractPreviewVisible.value = true
}

async function handleConfirmUiContractSnapshot(payload: {
  confirmationNote?: string
}) {
  if (!selectedModule.value?.id || !isPageNode(selectedModule.value)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择具体 PAGE 页面节点，再确认交互原型。',
    }
    return
  }

  if (!uiContractArtifact.value?.artifactId) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前页面还没有可确认的 UI_CONTRACT 版本。',
    }
    return
  }

  uiContractConfirming.value = true
  uiContractSnapshotError.value = ''

  try {
    const snapshot = await confirmUiContractSnapshot(
      props.workspace.workspaceId,
      props.project.projectCode,
      selectedModule.value.id,
      {
        artifactId: uiContractArtifact.value.artifactId,
        confirmationNote: payload.confirmationNote || '',
      },
    )

    uiContractSnapshot.value = snapshot
    await loadDevelopmentHandoffReadiness(selectedModule.value)
    artifactTaskFeedback.value = {
      level: 'success',
      message: '页面效果已确认，后续 Development Agent 将以该版 UI_CONTRACT 作为页面蓝图。',
    }
  } catch (error) {
    const message = resolveErrorMessage(
      error,
      '确认交互原型失败。',
    )

    uiContractSnapshotError.value = message
    window.alert(message)
  } finally {
    uiContractConfirming.value = false
  }
}

function openThemeProfileDrawer() {
  projectActionMenuOpen.value = false
  themeProfileDrawerVisible.value = true
}

function closeThemeProfileDrawer() {
  themeProfileDrawerVisible.value = false
}

function handleThemeProfileChanged(
  profile: ProjectThemeProfile,
) {
  themeProfile.value = profile
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

  impactAnalysisError.value = ''
  impactAnalysisSourceArtifactType.value = artifactType
  // 受上游影响后，不默认勾选任何下游资产，避免误触发整份重生成。
  // 用户可以直接“暂不处理下游”，也可以显式选择一个资产交给 LangGraph Agent 局部同步修订。
  impactAnalysisSelectedTypes.value = []
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
  if (artifactType === 'PAGE_MAP') return pageMapMarkdown.value || ''
  if (artifactType === 'PAGE_SPEC') return pageSpecMarkdown.value || ''
  if (artifactType === 'DATA_MODEL') return dataModelMarkdown.value || ''
  if (artifactType === 'API_CONTRACT') return apiContractMarkdown.value || ''
  if (artifactType === 'TEST_SPEC') return testSpecMarkdown.value || ''
  if (artifactType === 'DEV_HANDOFF_PACKAGE') return devHandoffPackageMarkdown.value || ''
  if (artifactType === 'UI_CONTRACT') return uiContractMarkdown.value || ''
  return ''
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

async function handleSyncSelectedImpactedArtifact() {
  if (!selectedModule.value?.id) {
    impactAnalysisError.value = '请先选择一个模块树节点，再执行 AI 局部同步修订。'
    return
  }

  if (impactAnalysisSelectedTypes.value.length === 0) {
    impactAnalysisError.value = '请先选择 1 个需要同步修订的下游资产；也可以点击“暂不处理下游”直接关闭。'
    return
  }

  if (impactAnalysisSelectedTypes.value.length > 1) {
    impactAnalysisError.value = 'AI 局部同步修订建议一次处理 1 个资产，避免多个编辑提案混在一起。请只勾选 1 项。'
    return
  }

  const artifactType = impactAnalysisSelectedTypes.value[0]

  if (!impactAnalysisAgentSyncSupportedTypes.includes(artifactType)) {
    impactAnalysisError.value = `${artifactType} 暂不支持在影响分析中直接做通用资产同步修订。请先同步修订 PAGE_SPEC、DATA_MODEL、API_CONTRACT 或 TEST_SPEC 等上游正式资产，再按专用流程处理该资产。`
    return
  }

  const targetItem = impactAnalysisItems.value.find(
    (item) => item.artifactType === artifactType,
  )

  if (!targetItem?.artifactExists) {
    impactAnalysisError.value = '选中的下游资产尚未生成，无法做局部同步修订。请先生成该资产的初始版本。'
    return
  }

  const sourceTitle = impactAnalysisSourceTitle.value
  const targetTitle = targetItem.title || artifactType

  const confirmed = window.confirm(
    [
      `将使用 LangGraph 资产变更 Agent 局部同步修订：${targetTitle}`,
      `同步依据：${sourceTitle} 的上游变更。`,
      '系统只会先生成“资产编辑提案”，不会直接覆盖当前版本；确认提案后才会创建新版本。是否继续？',
    ].join('\n\n'),
  )

  if (!confirmed) {
    return
  }

  impactAnalysisSubmitting.value = true
  impactAnalysisError.value = ''
  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true
  aiAssistantCollapsed.value = false

  try {
    const response = await proposeAssistantAssetEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementChangeProposal: {
          proposalId: null,
          source: 'IMPACT_ANALYSIS_AGENT_SYNC',
          status: 'CONFIRMED',
          originalInstruction:
            `上游资产 ${sourceTitle} 已发生修改，请基于最新上游内容，对下游资产 ${targetTitle} 做局部同步修订。要求尽量只修改受影响段落，不要整份重写，也不要创建重新生成任务。`,
          summary:
            `根据 ${sourceTitle} 的变更，使用 LangGraph Agent 局部同步修订 ${targetTitle}。`,
          changeType: 'SYNC_WITH_UPSTREAM',
          scopeType: 'DOWNSTREAM_ASSET_SYNC',
          targetNode: {
            nodeId: selectedModule.value.id,
            nodeName: getModuleDisplayName(selectedModule.value),
            nodeType: selectedModule.value.moduleType || null,
            moduleKey: selectedModule.value.moduleKey || null,
          },
          affectedArtifacts: [artifactType],
          riskNotes: [
            '本操作只生成资产编辑提案，不会直接覆盖当前版本。',
            '请重点核对 Agent 是否只同步了与上游变更相关的内容。',
          ],
          recommendedSteps: [
            '查看右侧 Assistant 中的资产编辑提案。',
            '确认后生成新的待确认版本。',
            '再根据新的影响分析继续同步更下游资产。',
          ],
        },
        instruction:
          `请基于上游资产 ${sourceTitle} 的最新变更，对当前下游资产 ${targetTitle} 进行局部同步修订，生成资产编辑提案，不要整份重生成。`,
        selectedNodeId: selectedModule.value.id,
        selectedArtifactType: artifactType,
      },
    )

    const hasAssetEditProposal =
      Boolean(response.assetEditProposal) ||
      Boolean(
        response.actions?.some((action) =>
          action.type === 'ASSET_EDIT_PROPOSAL' ||
          action.type === 'OPEN_ASSET_EDIT_PROPOSAL',
        ),
      )

    latestAssistantResponse.value = {
      ...response,
      messageId: response.messageId
        ? `${response.messageId}_${Date.now()}`
        : `impact_sync_${Date.now()}`,
    }

    if (!hasAssetEditProposal) {
      impactAnalysisError.value =
        'AI 局部同步修订请求已返回，但没有生成资产编辑提案。请查看后端或 Python Agent 日志。'
      window.alert(impactAnalysisError.value)
      return
    }

    selectedArtifactType.value = artifactType
    await handleViewArtifact(artifactType)

    artifactTaskFeedback.value = {
      level: 'info',
      message:
        '已生成下游资产局部同步修订提案，请在右侧 AI Assistant 中确认是否生成“新版本待确认”。',
    }

    impactAnalysisDrawerVisible.value = false
  } catch (error) {
    const message = resolveErrorMessage(
      error,
      'AI 局部同步修订失败，请查看后端或 Python Agent 日志。',
    )

    impactAnalysisError.value = message
    window.alert(message)
  } finally {
    impactAnalysisSubmitting.value = false
    isSendingAssistantMessage.value = false
  }
}

async function handleRegenerateImpactedArtifacts() {
  if (
    !selectedModule.value?.id ||
    impactAnalysisSelectedTypes.value.length === 0
  ) {
    impactAnalysisError.value = '请先选择需要重新生成的下游资产；不想处理下游时可以直接点击“暂不处理下游”。'
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
    [
      '确认要重新生成选中的下游资产吗？',
      selectedTitles,
      '注意：重新生成是备选动作。优先建议使用“AI 局部同步修订”，只同步上游改动影响到的内容。',
    ].join('\n\n'),
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

async function handleSyncAffectedArtifactVersion(
  artifactId: string,
) {
  if (
    !selectedModule.value?.id ||
    !artifactVersionArtifactType.value ||
    !artifactId
  ) {
    window.alert('缺少当前模块、资产类型或资产版本，无法执行 AI 局部同步修订。')
    return
  }

  if (!isRequirementArtifactType(artifactVersionArtifactType.value)) {
    artifactVersionError.value = '当前资产类型不支持 AI 局部同步修订。'
    window.alert(artifactVersionError.value)
    return
  }

  const artifactType = artifactVersionArtifactType.value

  if (!impactAnalysisAgentSyncSupportedTypes.includes(artifactType)) {
    artifactVersionError.value =
      '该资产暂不支持通用资产同步修订。请先同步修订 PAGE_SPEC、DATA_MODEL、API_CONTRACT 或 TEST_SPEC 等上游正式资产，再按专用流程处理该资产。'
    window.alert(artifactVersionError.value)
    return
  }

  const confirmed = window.confirm(
    [
      '将使用 LangGraph 资产变更 Agent 同步修订当前受上游影响的资产。',
      '系统会先生成“资产编辑提案”，不会直接覆盖当前版本。确认提案后才会创建新版本。是否继续？',
    ].join('\n\n'),
  )

  if (!confirmed) {
    return
  }

  artifactVersionRollbackLoading.value = true
  artifactVersionError.value = ''
  latestAssistantResponse.value = null
  isSendingAssistantMessage.value = true
  aiAssistantCollapsed.value = false

  try {
    const response = await proposeAssistantAssetEdit(
      props.workspace.workspaceId,
      props.project.projectCode,
      {
        requirementChangeProposal: {
          proposalId: null,
          source: 'UPSTREAM_IMPACT_SYNC',
          status: 'CONFIRMED',
          originalInstruction:
            '当前资产状态为受上游影响。请基于最新上游资产内容，使用 LangGraph 资产变更 Agent 对当前资产进行局部同步修订，尽量只修改受影响段落，不要整篇重写。',
          summary:
            '同步修订受上游影响的资产，生成可人工确认的资产编辑提案。',
          changeType: 'SYNC_WITH_UPSTREAM',
          scopeType: 'ASSET_SYNC_FROM_UPSTREAM',
          targetNode: {
            nodeId: selectedModule.value.id,
            nodeName: getModuleDisplayName(selectedModule.value),
            nodeType: selectedModule.value.moduleType || null,
            moduleKey: selectedModule.value.moduleKey || null,
          },
          affectedArtifacts: [artifactType],
          riskNotes: [
            '当前资产受上游资产变化影响，需要先由 Agent 局部同步修订，再由人工确认新版本。',
          ],
          recommendedSteps: [
            '由 LangGraph Agent 生成资产编辑提案。',
            '人工确认提案后生成新的待确认版本。',
            '新版本进入“新版本待确认”状态。',
          ],
        },
        instruction:
          '当前资产受上游影响，请基于上游最新资产进行局部同步修订，生成资产编辑提案，不要重新生成整份资产。',
        selectedNodeId: selectedModule.value.id,
        selectedArtifactType: artifactType,
      },
    )

    const hasAssetEditProposal =
      Boolean(response.assetEditProposal) ||
      Boolean(
        response.actions?.some((action) =>
          action.type === 'ASSET_EDIT_PROPOSAL' ||
          action.type === 'OPEN_ASSET_EDIT_PROPOSAL',
        ),
      )

    latestAssistantResponse.value = {
      ...response,
      messageId: response.messageId
        ? `${response.messageId}_${Date.now()}`
        : `asset_sync_${Date.now()}`,
    }

    if (!hasAssetEditProposal) {
      artifactVersionError.value =
        'AI 局部同步修订请求已返回，但没有生成资产编辑提案。请查看后端或 Python Agent 日志。'
      window.alert(artifactVersionError.value)
      return
    }

    selectedArtifactType.value = artifactType
    await handleViewArtifact(artifactType)

    artifactTaskFeedback.value = {
      level: 'info',
      message:
        '已生成资产编辑提案，请在右侧 AI Assistant 中确认是否生成“新版本待确认”。',
    }

    artifactVersionDrawerVisible.value = false
  } catch (error) {
    const message = resolveErrorMessage(
      error,
      'AI 局部同步修订失败，请查看后端或 Python Agent 日志。',
    )

    artifactVersionError.value = message
    window.alert(message)
  } finally {
    artifactVersionRollbackLoading.value = false
    isSendingAssistantMessage.value = false
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

  if (tab === 'development') {
    await loadDevelopmentHandoffReadiness()
  }

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
  uiContractReadiness.value = null
  uiContractReadinessLoading.value = false
  uiContractReadinessError.value = ''
  uiContractSnapshot.value = null
  uiContractSnapshotLoading.value = false
  uiContractConfirming.value = false
  uiContractSnapshotError.value = ''
  developmentHandoffReadiness.value = null
  developmentHandoffLoading.value = false
  developmentHandoffError.value = ''
  artifactTaskFeedback.value = null
  clearSelectedModuleAndArtifacts()
  resetSteps()
}

function clearSelectedModuleAndArtifacts() {
  clearSelectedModule()
  clearArtifacts()
  clearCurrentModuleAgentTasks()
  selectedArtifactType.value = null
  uiContractReadiness.value = null
  uiContractReadinessLoading.value = false
  uiContractReadinessError.value = ''
  uiContractSnapshot.value = null
  uiContractSnapshotLoading.value = false
  uiContractConfirming.value = false
  uiContractSnapshotError.value = ''
  developmentHandoffReadiness.value = null
  developmentHandoffLoading.value = false
  developmentHandoffError.value = ''
  artifactTaskFeedback.value = null
}

function handleLogout() {
  clearAuthStorage()
  window.location.reload()
}
</script>

<style scoped>

.workspace-topbar {
  position: relative;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
}

.workspace-topbar-left,
.workspace-topbar-right {
  display: flex;
  align-items: center;
  min-width: 0;
}

.workspace-topbar-left {
  gap: 14px;
}

.workspace-topbar-right {
  position: relative;
  z-index: 42;
  margin-left: auto;
}

.back-home-button {
  flex: 0 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  padding: 8px 13px;
  background: #ffffff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition: all 0.16s ease;
}

.back-home-button:hover {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.workspace-project-meta {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.workspace-project-meta strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-project-meta span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-actions-menu {
  position: relative;
  z-index: 91;
}

.workspace-actions-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 92px;
  border: 1px solid #d5e3ff;
  border-radius: 10px;
  height: 34px;
  padding: 0 14px;
  background: #ffffff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
  transition: all 0.16s ease;
}

.workspace-actions-trigger:hover,
.workspace-actions-trigger[aria-expanded='true'] {
  border-color: #93c5fd;
  background: #f8fbff;
  color: #1d4ed8;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
}

.workspace-actions-trigger-chevron {
  color: currentColor;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 10px;
  height: 10px;
  font-size: 10px;
  line-height: 1;
  opacity: 0.72;
  transform-origin: 50% 50%;
  transition: transform 0.16s ease;
}

.workspace-actions-trigger[aria-expanded='true'] .workspace-actions-trigger-chevron {
  transform: rotate(180deg);
}

.workspace-actions-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: default;
}

.workspace-actions-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 92;
  display: grid;
  gap: 4px;
  width: 176px;
  border: 1px solid #dbe4ef;
  border-radius: 14px;
  padding: 6px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
}

.workspace-action-item {
  display: flex;
  align-items: center;
  width: 100%;
  border: 0;
  border-radius: 10px;
  padding: 9px 11px;
  background: transparent;
  color: #334155;
  text-align: left;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.16s ease;
}

.workspace-action-item:hover {
  background: #eff6ff;
  color: #1d4ed8;
}

.workspace-action-item.danger {
  color: #b91c1c;
}

.workspace-action-item.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}

.main-layout {
  display: flex;
  min-height: calc(100vh - 128px);
  background: #f8fafc;
  overflow: hidden;
}

.main-layout.with-workspace-topbar {
  min-height: calc(100vh - 128px);
}

.app-shell.development-shell {
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.app-shell.development-shell .platform-header-shell {
  flex: 0 0 auto;
  flex-shrink: 0;
  width: 100%;
}

.app-shell.development-shell .workspace-topbar {
  flex: 0 0 auto;
  flex-shrink: 0;
}

.app-shell.development-shell .main-layout.with-workspace-topbar {
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
  max-height: none;
  overflow: hidden;
  display: flex;
  align-items: stretch;
}

.app-shell.development-shell :deep(.module-tree-panel) {
  flex: 0 0 285px;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.app-shell.development-shell :deep(.development-handoff-panel) {
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.app-shell.development-shell :deep(.development-handoff-panel .handoff-card) {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.app-shell.development-shell :deep(.ai-assistant-panel) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.app-shell.development-shell :deep(.assistant-message-list) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.workspace-panel {
  flex: 1;
  min-width: 0;
}

.theme-profile-mask {
  position: fixed;
  inset: 0;
  z-index: 6000;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(4px);
}

.theme-profile-drawer {
  position: relative;
  z-index: 1;
  width: min(520px, 96vw);
  height: 100%;
  overflow: auto;
  border-left: 1px solid #dbe4ef;
  padding: 20px;
  background: #f8fafc;
  box-shadow: -24px 0 60px rgba(15, 23, 42, 0.18);
}

.theme-profile-drawer-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 18px;
  background: #ffffff;
}

.theme-profile-drawer-header p {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.theme-profile-drawer-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.theme-profile-drawer-header span {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.theme-profile-drawer-header button {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid #dbe4ef;
  border-radius: 11px;
  background: #ffffff;
  color: #334155;
  font-size: 22px;
  cursor: pointer;
}


/* A9_7_6_INDEPENDENT_SCROLL_FIX */
.app-shell {
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.platform-header-shell,
.workspace-topbar {
  flex: 0 0 auto;
}

.main-layout,
.main-layout.with-workspace-topbar {
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
  max-height: none;
  overflow: hidden;
  display: flex;
  align-items: stretch;
}

.main-layout.with-workspace-topbar :deep(.module-tree-panel) {
  flex: 0 0 285px;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.main-layout.with-workspace-topbar :deep(.a9-workspace) {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.main-layout.with-workspace-topbar :deep(.ai-assistant-panel) {
  flex: 0 0 auto;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.main-layout.with-workspace-topbar :deep(.assistant-header),
.main-layout.with-workspace-topbar :deep(.assistant-input-area) {
  flex: 0 0 auto;
}

.main-layout.with-workspace-topbar :deep(.assistant-message-list) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.main-layout.with-workspace-topbar :deep(.development-handoff-panel) {
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.main-layout.with-workspace-topbar :deep(.development-handoff-panel .handoff-card) {
  height: 100%;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

</style>

