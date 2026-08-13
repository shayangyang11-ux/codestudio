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

      <AcceptanceRequirementWorkspace
        :requirement-impact-page-ids="requirementImpactPageIdList" v-else :workspace-id="props.workspace.workspaceId" :project-code="props.project.projectCode" :selected-module="selectedModule" :module-tree="modules"
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
        @save-manual-artifact-edit="handleSaveManualArtifactEdit"
            @complete-module-tree-from-prd="handleCompleteModuleTreeFromPrd"         @save-prototype-draft="handleSavePrototypeEditDraft"
        @discard-prototype-draft="handleDiscardPrototypeEditDraft"
        @publish-prototype="handlePublishCurrentPrototype"
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
      <!-- A9.10.14.4.3.3_ASSISTANT_ONLY_PRD_IMPACT: PRD / prototype impact guidance is shown in RightAiAssistantPanel; the old technical downstream-asset drawer is intentionally not mounted. -->
      <ArtifactQualityCheckDrawer :visible="qualityCheckDrawerVisible"
        :module-name="selectedModule ? getModuleDisplayName(selectedModule) : '当前模块'"
        :result="artifactQualityCheckResult" @close="closeQualityCheckDrawer" @export-zip="handleExportArtifactsZip" />

      <UiContractPreviewDrawer v-if="false"
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
// A9.10.14.4.3.15.2.1.1_PROJECT_WORKSPACE_STRING_HOTFIX
import { computed, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import ModuleTreePanel from '../components/ModuleTreePanel.vue'
import AcceptanceRequirementWorkspace from '../components/AcceptanceRequirementWorkspace.vue'
import DevelopmentHandoffGatePanel from '../components/DevelopmentHandoffGatePanel.vue'
import ArtifactVersionHistoryDrawer from '../components/ArtifactVersionHistoryDrawer.vue'
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
  fetchCurrentModuleArtifact,
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
import type { ProjectThemeProfile, UpdateProjectThemeProfileRequest } from '../types/themeProfile'
import type { ConfirmedUiContractSnapshot } from '../types/uiContractSnapshot'
import type { DevelopmentHandoffReadinessResponse } from '../types/developmentHandoff'
import { completeModuleTreeFromModulePrd } from '../api/moduleApi'
import {
  applyPrototypeLocalEdit,
  isPrototypeLocalEditInstruction,
  type PrototypeElementSelection,
} from '../domain/uiContractPrototypeLocalEdit'

const router = useRouter()

type ModuleTreeModelingMode =
  | 'PROGRESSIVE_MODULE_OUTLINE'
  | 'FULL_ENTERPRISE_BLUEPRINT'
  | 'ORGANIZE_THEN_GENERATE'

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

// A9.10.14.4.3.15.2.4_EXTERNAL_PRD_IMPORT_AND_LEGACY_PRD_MIGRATION_PROJECT_WORKSPACE
// Avoid silently spending tokens: old PRD migration is auto-detected, then exposed as an explicit Assistant quick action.
const a91524LegacyPrdMigrationHintKeys = ref<Set<string>>(new Set())

// A9.10.14.4.3.7.1_ROBUST_PRD_IMPACT_FRONTEND_FIX
// User-facing PRD impact state. When MODULE_PRD is saved, mark all descendant PAGE
// nodes locally so the PAGE prototype card can immediately show a simple regeneration hint.
const requirementImpactPageIds = ref<Set<string>>(new Set())
const requirementImpactPageIdList = computed(() => Array.from(requirementImpactPageIds.value))

function a94371NodeType(node: ModuleTreeNode | null | undefined): string {
  const extra = node?.extra && typeof node.extra === 'object' && !Array.isArray(node.extra)
    ? node.extra as Record<string, unknown>
    : {}
  return String(node?.moduleType || node?.type || extra.nodeType || extra.node_type || '').trim().toUpperCase()
}

function a94371MarkRequirementImpactForDescendantPages(root: ModuleTreeNode | null | undefined): void {
  if (!root) return

  const next = new Set(requirementImpactPageIds.value)
  const visit = (node: ModuleTreeNode | null | undefined) => {
    if (!node) return
    if (a94371NodeType(node) === 'PAGE' && node.id) {
      next.add(String(node.id))
    }
    for (const child of node.children || []) {
      visit(child)
    }
  }

  visit(root)
  requirementImpactPageIds.value = next
}

function a94371ClearRequirementImpactForPage(node: ModuleTreeNode | null | undefined): void {
  if (!node?.id) return
  const next = new Set(requirementImpactPageIds.value)
  next.delete(String(node.id))
  requirementImpactPageIds.value = next
}

// A9.10.14.4.3.15.4.10.4.5_PRD_EDIT_IMPACT_PRECISION
// Mark only the PAGE nodes that can be traced from the edited MODULE_PRD section.
// The older descendant-wide marker is kept for tree-structure sync, but manual PRD
// edits now use this precise marker to avoid unnecessary prototype regeneration.
function a94371MarkRequirementImpactForPageIds(pageIds: Array<string | null | undefined>): void {
  const normalized = pageIds
    .map((id) => String(id || '').trim())
    .filter(Boolean)

  if (!normalized.length) return

  const next = new Set(requirementImpactPageIds.value)
  for (const id of normalized) {
    next.add(id)
  }
  requirementImpactPageIds.value = next
}

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
  saveProjectThemeProfile,
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

// A9.10.3 Prototype lifecycle: "published" means the current UI_CONTRACT
// version has an explicit confirmed snapshot. Development handoff must not
// silently consume a dirty draft or a stale current artifact.
const isCurrentUiContractPublished = computed(() => {
  const currentArtifactId = normalizeA9103Id(uiContractArtifact.value?.artifactId)
  const snapshotArtifactId = normalizeA9103Id((uiContractSnapshot.value as any)?.artifactId)

  return Boolean(currentArtifactId && snapshotArtifactId && currentArtifactId === snapshotArtifactId)
})

function normalizeA9103Id(value: unknown): string {
  return String(value || '').trim().toLowerCase()
}


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
    a94371ClearRequirementImpactForPage(selectedModule.value)
    await loadDevelopmentHandoffReadiness(selectedModule.value)

    selectedArtifactType.value = 'UI_CONTRACT'

    syncA9VirtualComponentNodesForPage(selectedModule.value)

    if (uiContractArtifact.value?.content) {
      uiContractPreviewVisible.value = false
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

        const successSuffix =
      modelingMode === 'FULL_ENTERPRISE_BLUEPRINT'
        ? '\n本次已生成完整模块树：模块需求、业务能力和页面入口会保持同源；页面层只面向交互原型。'
        : modelingMode === 'ORGANIZE_THEN_GENERATE'
          ? '\n本次已先整理资料并生成模块候选骨架：当前只沉淀可信模块边界和不确定项，确认后可继续补全业务能力和页面。'
          : '\n本次已生成模块骨架：当前只确定模块边界，后续可继续拆分或完善模块需求。'

    const successMessage =
      buildModuleTreeGeneratedAssistantMessage(
        modules.value,
        preferredNode,
      ) + successSuffix

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

function resolveAssistantActionModelingMode(
  response: AssistantMessageResponse,
): ModuleTreeModelingMode | null {
  const action = response.actions?.find(
    (item) => item.type === 'GENERATE_MODULE_TREE',
  )

  const value = String(action?.payload?.modelingMode || '').trim()

  if (value === 'PROGRESSIVE_MODULE_OUTLINE' || value === 'FULL_ENTERPRISE_BLUEPRINT' || value === 'ORGANIZE_THEN_GENERATE') {
    return value as ModuleTreeModelingMode
  }

  return null
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
        '请回复 1、2 或 3，或者直接点击下面的生成方式按钮。',
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
    '请选择这次生成方式：',
    '生成模块骨架：适合只有初步想法时，先确定系统模块边界，后续继续拆分或完善。',
    '生成完整模块树：适合已经有明确业务范围时，一次生成模块、业务能力和页面。',
    '先整理资料：适合上传文件很多、旧 PRD / 会议纪要 / 截图 / 表格混在一起时，先整理候选和不确定项。',
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
        label: '生成完整模块树',
        payload: {
          text: '生成完整模块树',
          modelingMode: 'FULL_ENTERPRISE_BLUEPRINT',
          requirementText,
        },
      },
      {
        type: 'A9_MODULE_TREE_MODE_CHOICE',
        label: '先整理资料',
        payload: {
          text: '先整理资料',
          modelingMode: 'ORGANIZE_THEN_GENERATE',
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

  if (/^(3|三|c|C)/.test(text.trim()) || /先整理|整理资料|资料整理|整理后生成|不确定项|候选/.test(text)) {
    return 'ORGANIZE_THEN_GENERATE'
  }

  if (normalized === '3' || normalized === 'c') {
    return 'ORGANIZE_THEN_GENERATE'
  }

  return null
}

function resolveExplicitModuleTreeModelingMode(
  text: string,
): ModuleTreeModelingMode | null {
  if (/只生成模块|模块骨架|先生成模块|渐进式|不要页面|不要功能点/.test(text)) {
    return 'PROGRESSIVE_MODULE_OUTLINE'
  }

  if (/完整蓝图|完整模块树|直接生成完整|模块.*功能点.*页面|功能点.*页面/.test(text)) {
    return 'FULL_ENTERPRISE_BLUEPRINT'
  }

  if (/整理资料|先整理|资料整理|整理后生成|先分析资料|资料太杂|不确定项/.test(text)) {
    return 'ORGANIZE_THEN_GENERATE'
  }

  return null
}

function looksLikeInitialProjectRequirement(text: string): boolean {
  if (text.length < 8) {
    return false
  }

  if (/^\s*(1|2|3|一|二|三|a|b|c)\s*$/i.test(text)) {
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
  const hasReadyAttachment = Boolean(payload.attachments?.length)

  if (!trimmedText) {
    return
  }


  const actionPayload = (payload.actionPayload || {}) as Record<string, unknown>
  const actionType = String(payload.actionType || actionPayload['type'] || '').trim()
  const actionModelingMode = String(
    payload.modelingMode || actionPayload['modelingMode'] || '',
  ).trim()

  if (await handleProjectStyleOverrideAssistantCommand(trimmedText)) {
    return
  }

  if (
    actionType === 'A9_MODULE_TREE_MODE_CHOICE' ||
    actionModelingMode === 'PROGRESSIVE_MODULE_OUTLINE' ||
    actionModelingMode === 'FULL_ENTERPRISE_BLUEPRINT'
  ) {
    const choiceText =
      actionModelingMode === 'PROGRESSIVE_MODULE_OUTLINE' || /模块骨架|渐进|只生成模块/.test(trimmedText)
        ? '生成模块骨架'
        : '生成完整模块树'
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

  // A9.10.14.4.3.15.2.4: when the first message carries a PRD attachment, do not
  // short-circuit in the local modeling-choice flow; send it to Assistant backend so the
  // parsed attachment text is merged into the module-tree generation requirement.
  if (!hasReadyAttachment && await handleInitialModuleTreeModelingChoice(trimmedText)) {
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
        resolveAssistantActionModelingMode(response) || (hasReadyAttachment ? 'FULL_ENTERPRISE_BLUEPRINT' : 'PROGRESSIVE_MODULE_OUTLINE'),
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
      message: buildPrototypeSaveFeedbackMessage(
        response as unknown as Record<string, any>,
        content,
      ),
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


function buildPrototypeSaveFeedbackMessage(
  response: Record<string, any>,
  content: Record<string, unknown>,
): string {
  const sync = resolvePrototypeSaveRequirementSyncSummary(content)
  const version = response.versionNo ? ` V${response.versionNo}` : ''

  if (sync.visualOnly) {
    return `原型视觉/布局调整已保存为 UI_CONTRACT 新版本${version}；本次不影响 MODULE_PRD / PAGE_SPEC，可继续在模块完整预览中确认开发基线。`
  }

  return `原型语义调整已保存为 UI_CONTRACT 新版本${version}；系统已生成 MODULE_PRD / PAGE_SPEC 同步复核提示，请在确认开发基线前检查字段、按钮、跳转或页面文案是否需要同步。`
}

function resolvePrototypeSaveRequirementSyncSummary(
  content: Record<string, unknown>,
): {
  visualOnly: boolean
} {
  const metadata = projectLooseRecord(content.metadata)
  const summary = projectLooseRecord(metadata.prototypeRequirementSync)

  if (summary) {
    const mode = String(summary.mode || summary.classification || '').toUpperCase()
    return {
      visualOnly: Boolean(summary.visualOnly) || mode === 'UI_CONTRACT_ONLY',
    }
  }

  const edits = Array.isArray(metadata.prototypeLocalEdits)
    ? metadata.prototypeLocalEdits
    : []

  if (!edits.length) {
    return {
      visualOnly: false,
    }
  }

  return {
    visualOnly: edits.every((item) => {
      const edit = projectLooseRecord(item)
      const impact = projectLooseRecord(edit.requirementSyncImpact)
      const classification = String(
        impact.classification ||
          edit.requirementSyncClassification ||
          '',
      ).toUpperCase()
      return Boolean(impact.visualOnly || edit.visualOnly || classification === 'VISUAL_ONLY')
    }),
  }
}

function projectLooseRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : {}
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

  const previousPrdContentForImpact = payload.artifactType === 'MODULE_PRD'
    ? a91545ResolveCurrentModulePrdContentForImpact()
    : null

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
        // A9.10.14.4.3.15.1.1_PRESERVE_MANUAL_ARTIFACT_CONTENT
        // Section-level PRD editing updates structured content; keep it when saving.
        content: (payload as any).content || undefined,
        reason: payload.reason || '',
      } as any,
    )

    let precisePrdImpactMessage = ''

    if (payload.artifactType === 'MODULE_PRD') {
      await a91522SyncModuleTreeFromModulePrdPageRequirements(payload, response)
      precisePrdImpactMessage = a91545ApplyPrecisePrdEditImpact(
        payload,
        response,
        previousPrdContentForImpact,
      )
    }

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

      if (downstream.length > 0 && artifactType !== 'MODULE_PRD') {
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
        precisePrdImpactMessage ||
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
      reply: `${savedMessage} 新版本已保存，旧版本已保留。系统会在后台维护内部资产状态；需要时请检查并重新生成交互原型。`,
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

  if (actionType === 'UPDATE_PROJECT_THEME_PROFILE' || actionType === 'LOCK_PROJECT_STYLE') {
    await applyProjectStyleOverrideFromAction(action)
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


// A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK
type A91544StylePreset = {
  industry: UpdateProjectThemeProfileRequest['industry']
  themeKey: UpdateProjectThemeProfileRequest['themeKey']
  visualTone: UpdateProjectThemeProfileRequest['visualTone']
  primaryColor: string
  secondaryColor: string | null
  accentColor: string | null
  density: UpdateProjectThemeProfileRequest['density']
  radius: UpdateProjectThemeProfileRequest['radius']
  loginLayout: UpdateProjectThemeProfileRequest['loginLayout']
  navigationStyle: UpdateProjectThemeProfileRequest['navigationStyle']
  label: string
  summary: string
}

async function handleProjectStyleOverrideAssistantCommand(
  text: string,
): Promise<boolean> {
  const instruction = String(text || '').trim()

  if (!instruction) {
    return false
  }

  if (isProjectStyleAutoModeCommand(instruction)) {
    await applyProjectStyleAutoMode(instruction)
    return true
  }

  const preset = resolveProjectStyleOverridePreset(instruction)

  if (!preset) {
    return false
  }

  await applyLockedProjectStyleOverride(preset, instruction)
  return true
}

function isProjectStyleAutoModeCommand(
  text: string,
): boolean {
  return /(恢复|回到|取消|解除).*(自动识别|自动风格|自动判断|自动推断|风格锁定)|自动识别.*(风格|样式|主题)/.test(text)
}

function resolveProjectStyleOverridePreset(
  text: string,
): A91544StylePreset | null {
  if (!looksLikeProjectStyleOverrideInstruction(text)) {
    return null
  }

  const normalized = text.toLowerCase()

  if (/电商|商城|零售|商品|购物|订单运营|commerce|retail|shop/.test(text) || /commerce|retail|shop/.test(normalized)) {
    return {
      industry: 'COMMERCE' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'commerce_light',
      visualTone: 'COMMERCE',
      primaryColor: '#f97316',
      secondaryColor: '#fff7ed',
      accentColor: '#0ea5e9',
      density: 'COMFORTABLE',
      radius: 'LARGE',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'TOP_NAV',
      label: '电商零售风',
      summary: '更明亮、卡片感更强，突出商品、订单、库存和转化动作。',
    }
  }

  if (/仓储|仓库|库存|入库|出库|盘点|库位|供应链|wms|warehouse|inventory|stock/.test(text) || /warehouse|inventory|stock|wms/.test(normalized)) {
    return {
      industry: 'WAREHOUSE' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'industrial_pro',
      visualTone: 'INDUSTRIAL',
      primaryColor: '#2563eb',
      secondaryColor: '#f1f5f9',
      accentColor: '#f59e0b',
      density: 'COMPACT',
      radius: 'MEDIUM',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '仓储供应链风',
      summary: '更专业紧凑，突出单据号、仓库、库位、数量、状态和表格效率。',
    }
  }

  if (/外贸|跨境|贸易|询盘|报价|报关|出口|进口|海运|shipment|export|quotation|inquiry|foreign/.test(text) || /shipment|export|quotation|inquiry|foreign/.test(normalized)) {
    return {
      industry: 'FOREIGN_TRADE' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'enterprise_default',
      visualTone: 'CLEAN',
      primaryColor: '#2563eb',
      secondaryColor: '#eff6ff',
      accentColor: '#14b8a6',
      density: 'STANDARD',
      radius: 'MEDIUM',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '外贸国际化风',
      summary: '更干净国际化，突出客户、询盘、报价、订单和物流节点。',
    }
  }

  if (/工单|派单|受理|维修|客服|服务流程|sla|ticket|service\s*desk|workflow/.test(text) || /ticket|service\s*desk|workflow|sla/.test(normalized)) {
    return {
      industry: 'WORK_ORDER' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'tech_modern',
      visualTone: 'TECH',
      primaryColor: '#0ea5e9',
      secondaryColor: '#e0f2fe',
      accentColor: '#8b5cf6',
      density: 'STANDARD',
      radius: 'MEDIUM',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '工单服务风',
      summary: '更强调状态、负责人、SLA、优先级、处理记录和下一步动作。',
    }
  }

  if (/crm|客户关系|线索|商机|跟进|联系人|销售|pipeline|lead|opportunity/.test(text) || /crm|pipeline|lead|opportunity/.test(normalized)) {
    return {
      industry: 'CRM' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'enterprise_default',
      visualTone: 'CLEAN',
      primaryColor: '#7c3aed',
      secondaryColor: '#f5f3ff',
      accentColor: '#06b6d4',
      density: 'STANDARD',
      radius: 'LARGE',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: 'CRM 客户关系风',
      summary: '更突出客户画像、跟进记录、销售阶段、负责人和下一步动作。',
    }
  }

  if (/制造|生产|设备|产线|质检|排产|工艺|mes|manufacturing|quality/.test(text) || /manufacturing|mes|quality/.test(normalized)) {
    return {
      industry: 'MANUFACTURING' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'industrial_pro',
      visualTone: 'INDUSTRIAL',
      primaryColor: '#1d4ed8',
      secondaryColor: '#eef2ff',
      accentColor: '#f59e0b',
      density: 'COMPACT',
      radius: 'SMALL',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '制造生产风',
      summary: '更强调批次、设备、工序、异常、产能和高密度数据管理。',
    }
  }

  if (/金融|风控|财务|账务|结算|发票|对账|报销|finance|risk|invoice|settlement/.test(text) || /finance|risk|invoice|settlement/.test(normalized)) {
    return {
      industry: 'FINANCE' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'finance_stable',
      visualTone: 'FINANCE',
      primaryColor: '#1e3a8a',
      secondaryColor: '#eff6ff',
      accentColor: '#059669',
      density: 'STANDARD',
      radius: 'MEDIUM',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '金融稳健风',
      summary: '更克制可信，突出金额、风险、状态、审计和稳健操作。',
    }
  }

  if (/政企|政务|政府|监管|公共服务|办件|申报|gov|government/.test(text) || /gov|government/.test(normalized)) {
    return {
      industry: 'GOV' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'gov_clean',
      visualTone: 'GOV',
      primaryColor: '#1d4ed8',
      secondaryColor: '#f8fafc',
      accentColor: '#0369a1',
      density: 'STANDARD',
      radius: 'SMALL',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'TOP_NAV',
      label: '政企清晰风',
      summary: '更规整克制，突出办理状态、材料清单、层级和正式说明。',
    }
  }

  if (/saas|现代|科技|数据平台|云平台|ai|智能|极简|卡片|更现代/.test(text) || /saas|modern|tech|ai/.test(normalized)) {
    return {
      industry: 'SAAS' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'tech_modern',
      visualTone: 'TECH',
      primaryColor: '#4f46e5',
      secondaryColor: '#eef2ff',
      accentColor: '#22c55e',
      density: 'STANDARD',
      radius: 'LARGE',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'MIXED',
      label: '现代 SaaS 风',
      summary: '更现代、轻量、卡片层级更明显，适合工作台和数据平台。',
    }
  }

  if (/蓝灰|严肃后台|专业后台|传统后台|企业后台|克制|不要太花|简洁|clean|enterprise/.test(text) || /clean|enterprise/.test(normalized)) {
    return {
      industry: 'GENERAL' as unknown as UpdateProjectThemeProfileRequest['industry'],
      themeKey: 'enterprise_default',
      visualTone: 'CLEAN',
      primaryColor: '#2563eb',
      secondaryColor: '#f8fafc',
      accentColor: '#64748b',
      density: /紧凑|高密度|compact/.test(text) ? 'COMPACT' : 'STANDARD',
      radius: /小圆角|严肃|传统/.test(text) ? 'SMALL' : 'MEDIUM',
      loginLayout: 'BRAND_SPLIT',
      navigationStyle: 'SIDE_NAV',
      label: '蓝灰专业后台风',
      summary: '更克制、清晰、专业，适合通用企业后台和管理系统。',
    }
  }

  return null
}

function looksLikeProjectStyleOverrideInstruction(
  text: string,
): boolean {
  const styleSignal =
    /(风格|样式|主题|视觉|配色|颜色|界面|UI|圆角|密度|卡片|蓝灰|SaaS|saas|电商|仓储|外贸|工单|金融|政企|制造|CRM|crm|严肃|现代|科技|国际化)/.test(text)

  const changeSignal =
    /(换成|改成|改为|调整成|变成|做成|做得|统一成|更像|偏|不要太|改一下|优化成|切换到)/.test(text)

  const explicitStyleName =
    /(现代\s*SaaS|蓝灰专业|严肃后台|电商风|仓储风|外贸风|工单风|金融风|政企风|制造风|CRM)/i.test(text)

  return styleSignal && (changeSignal || explicitStyleName)
}

async function applyLockedProjectStyleOverride(
  preset: A91544StylePreset,
  instruction: string,
): Promise<void> {
  const current = themeProfile.value

  const payload: UpdateProjectThemeProfileRequest = {
    industry: preset.industry,
    themeKey: preset.themeKey,
    visualTone: preset.visualTone,
    primaryColor: preset.primaryColor,
    secondaryColor: preset.secondaryColor,
    accentColor: preset.accentColor,
    density: preset.density,
    radius: preset.radius,
    loginLayout: current?.loginLayout || preset.loginLayout,
    navigationStyle: preset.navigationStyle,
    darkModeEnabled: Boolean(current?.darkModeEnabled),
    locked: true,
    notes: [
      `用户通过 AI Assistant 锁定为：${preset.label}。`,
      `原始指令：${instruction}`,
      preset.summary,
      '该操作只影响项目视觉风格，不修改 PRD、字段、按钮、页面类型、跳转关系或业务状态。',
    ].join('\n'),
  }

  try {
    const profile = await saveProjectThemeProfile(payload)
    themeProfile.value = profile

    artifactTaskFeedback.value = {
      level: 'success',
      message: `已切换为${preset.label}，并锁定为用户风格。当前业务字段和按钮不会被改变。`,
    }

    latestAssistantResponse.value = buildLocalAssistantResponse(
      [
        `已切换为${preset.label}，并锁定为用户风格。`,
        '',
        '这次只改变项目视觉方向：颜色、密度、圆角、卡片层次、按钮和状态标签表达。',
        '我没有修改模块需求、页面字段、按钮、页面类型、跳转关系或业务状态。',
        '',
        selectedModule.value && isPageNode(selectedModule.value)
          ? '当前页面预览会优先使用这个项目风格；后续重新生成交互原型时也会沿用该风格。'
          : '后续进入 PAGE 生成交互原型时，会默认沿用该风格。',
      ].join('\n'),
      'project_style_override',
    )

    aiAssistantCollapsed.value = false
  } catch (error) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: resolveErrorMessage(error, '保存项目风格失败。'),
    }
    showAssistantSafeError(error, '保存项目风格失败。')
  }
}

async function applyProjectStyleAutoMode(
  instruction: string,
): Promise<void> {
  const current = themeProfile.value

  const payload: UpdateProjectThemeProfileRequest = {
    industry: current?.industry || 'GENERAL',
    themeKey: current?.themeKey || 'enterprise_default',
    visualTone: current?.visualTone || 'CLEAN',
    primaryColor: current?.primaryColor || '#2563eb',
    secondaryColor: current?.secondaryColor || null,
    accentColor: current?.accentColor || null,
    density: current?.density || 'STANDARD',
    radius: current?.radius || 'MEDIUM',
    loginLayout: current?.loginLayout || 'BRAND_SPLIT',
    navigationStyle: current?.navigationStyle || 'SIDE_NAV',
    darkModeEnabled: Boolean(current?.darkModeEnabled),
    locked: false,
    notes: [
      '用户通过 AI Assistant 解除风格锁定，恢复自动识别。',
      `原始指令：${instruction}`,
    ].join('\n'),
  }

  try {
    const profile = await saveProjectThemeProfile(payload)
    themeProfile.value = profile

    artifactTaskFeedback.value = {
      level: 'success',
      message: '已解除用户风格锁定，后续页面会继续按系统需求自动识别风格。',
    }

    latestAssistantResponse.value = buildLocalAssistantResponse(
      [
        '已解除用户风格锁定。',
        '',
        '后续生成交互原型时，系统会重新根据项目需求和页面类型自动识别行业风格。',
        '已有 PRD、字段、按钮、页面类型和跳转关系不会被修改。',
      ].join('\n'),
      'project_style_auto_mode',
    )

    aiAssistantCollapsed.value = false
  } catch (error) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: resolveErrorMessage(error, '解除项目风格锁定失败。'),
    }
    showAssistantSafeError(error, '解除项目风格锁定失败。')
  }
}

async function applyProjectStyleOverrideFromAction(
  action: AssistantActionResponse,
): Promise<void> {
  const payloadRecord = action.payload || {}
  const instruction = String(payloadRecord.instruction || action.label || 'AI Assistant 风格覆盖指令')
  const preset = resolveProjectStyleOverridePreset(
    [
      instruction,
      payloadRecord.industry,
      payloadRecord.themeKey,
      payloadRecord.visualTone,
      payloadRecord.label,
    ].filter(Boolean).join(' '),
  )

  if (preset) {
    await applyLockedProjectStyleOverride(preset, instruction)
    return
  }

  await applyProjectStyleAutoMode(instruction)
}


function containsAnyText(
  text: string,
  keywords: string[],
): boolean {
  return keywords.some((keyword) => text.includes(keyword))
}




// A9.10.14.4.3.15.2.4.1_LEGACY_PRD_MIGRATION_ACTION_HOTFIX
// No-op tree proposals can be executable when they represent legacy MODULE_PRD
// migration. In that case the executable action is an LLM MODULE_PRD upgrade,
// not a module-tree patch apply.
function a915241IsLegacyPrdMigrationProposal(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
): boolean {
  if (!proposal) {
    return false
  }

  const record = proposal as unknown as Record<string, any>
  const nested = a915241Record(record.proposal)
  const impact = a915241Record(nested.impact) || a915241Record(record.impact)
  const operations = Array.isArray(nested.operations)
    ? nested.operations
    : []
  const affectedArtifacts = Array.isArray(impact.affectedArtifacts)
    ? impact.affectedArtifacts.map(String)
    : Array.isArray(record.affectedArtifacts)
      ? record.affectedArtifacts.map(String)
      : []
  const riskNotes = Array.isArray(impact.riskNotes)
    ? impact.riskNotes.map(String)
    : []
  const text = [
    record.source,
    nested.source,
    record.description,
    nested.description,
    record.summary,
    nested.summary,
    impact.summary,
    ...riskNotes,
  ]
    .filter(Boolean)
    .map(String)
    .join('\n')
    .toUpperCase()
  const affectsModulePrd = affectedArtifacts
    .map((item) => item.toUpperCase())
    .includes('MODULE_PRD')

  if (operations.length > 0 || !affectsModulePrd) {
    return false
  }

  return (
    text.includes('LEGACY_PRD') ||
    text.includes('LEGACY PRD') ||
    text.includes('旧 PRD') ||
    text.includes('旧PRD') ||
    text.includes('老 PRD') ||
    text.includes('老PRD') ||
    text.includes('重新生成 MODULE_PRD') ||
    (
      text.includes('MODULE_PRD') &&
      text.includes('PAGE_REQUIREMENTS') &&
      (text.includes('升级') || text.includes('迁移') || text.includes('补齐'))
    )
  )
}

function a915241Record(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : {}
}

function a915241BuildLegacyPrdMigrationRequirementText(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
): string {
  const module = selectedModule.value
  const moduleName = module
    ? getModuleDisplayName(module)
    : '当前模块'
  const pageNames = module
    ? collectDescendantPageNodes(module)
        .map((page) => getModuleDisplayName(page))
        .filter(Boolean)
    : []

  const record = proposal as unknown as Record<string, any> | null | undefined
  const nested = a915241Record(record?.proposal)
  const impact = a915241Record(nested.impact) || a915241Record(record?.impact)
  const summary = String(
    impact.summary ||
      nested.description ||
      record?.description ||
      '',
  ).trim()

  return [
    `请用 LLM 升级当前「${moduleName}」MODULE_PRD。`,
    '这是旧 PRD 迁移，不是模块树结构变更。',
    '请保留当前旧 PRD 中已经合理的业务内容、业务规则、状态规则、异常场景和验收要点。',
    '请重构为新版结构化 MODULE_PRD，并必须补齐 content.page_requirements 与“页面需求与原型契约”章节。',
    pageNames.length
      ? `page_requirements 必须覆盖当前 MODULE 下全部 PAGE：${pageNames.join('、')}。`
      : 'page_requirements 必须覆盖当前 MODULE 下全部 PAGE。',
    '每个 page_requirement 必须包含 page_key、page_name、page_type、route_path、layout_type、primary_entity、filter_fields、table_columns、form_fields、primary_actions、row_actions、navigation_targets。',
    'page_key、page_name、page_type、route_path 必须以模块树事实为准；LLM 只补业务字段、按钮、状态、跳转和说明。',
    '不要修改 Development Agent，不生成 Node.js 代码，不直接改 UI_CONTRACT。',
    summary ? `当前迁移提案摘要：${summary}` : '',
  ]
    .filter(Boolean)
    .join('\n')
}

async function a915241ApplyLegacyPrdMigrationProposal(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
) {
  const module = selectedModule.value

  if (!module?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个 MODULE，再升级旧 PRD。',
    }
    return
  }

  if (resolveRequirementNodeType(module) !== 'MODULE') {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '旧 PRD 升级只作用于 MODULE，请先选择左侧模块节点。',
    }
    return
  }

  const previousRequirementText = requirementText.value
  const migrationInstruction = a915241BuildLegacyPrdMigrationRequirementText(proposal)

  isApplyingTreePatch.value = true
  selectedArtifactType.value = 'MODULE_PRD'
  requirementText.value = migrationInstruction
  treePatchProposal.value = null

  try {
    artifactTaskFeedback.value = {
      level: 'info',
      message: '正在创建旧 PRD 升级任务：Requirement Agent 将调用 LLM 补齐页面需求与原型契约。',
    }

    await startAsyncArtifactTask('GENERATE_MODULE_PRD')

    await Promise.allSettled([
      loadCurrentModuleAgentTasks(),
      saveCurrentWorkspaceState({
        selectedModuleId: module.id || null,
        selectedArtifactType: 'MODULE_PRD',
        selectedArtifactId: prdArtifact.value?.artifactId || null,
        activeAgentTab: activeTab.value,
        uiState: {
          lastAction: 'a9-legacy-prd-migration-start',
          source: 'A9.10.14.4.3.15.2.4.1_LEGACY_PRD_MIGRATION_ACTION_HOTFIX',
        },
      }),
    ])

    startTaskPolling()

    latestAssistantResponse.value = {
      messageId: `legacy_prd_migration_started_${Date.now()}`,
      intent: 'GENERATE_REQUIREMENT_ARTIFACT',
      reply: '已创建旧 PRD 升级任务。完成后请回到 MODULE_PRD 查看“页面需求与原型契约”章节，并确认每个 PAGE 都有 page_requirements。',
      requiresUserConfirmation: false,
      proposal: null,
      requirementChangeProposal: null,
      assetEditProposal: null,
      attachmentDrivenChangeProposal: null,
      attachmentArtifactDiffProposal: null,
      actions: [],
    }
  } catch (error) {
    showAssistantSafeError(error, '创建旧 PRD 升级任务失败。')
  } finally {
    requirementText.value = previousRequirementText
    isApplyingTreePatch.value = false
  }
}

async function handleApplyTreePatchProposal(
  proposalFromMessage?: ModuleTreePatchProposalResponse,
) {
  const proposal = proposalFromMessage || treePatchProposal.value
  const operations = proposal?.proposal?.operations || []

  if (!operations.length) {
    if (a915241IsLegacyPrdMigrationProposal(proposal)) {
      await a915241ApplyLegacyPrdMigrationProposal(proposal)
      return
    }

    showAssistantInlineError(new Error('当前没有可应用的模块树变更操作。'), '当前没有可应用的模块树变更操作。')
    return
  }

  isApplyingTreePatch.value = true

  try {
    const previousTree = a9152DeepCloneModuleTree(modules.value)
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

    await a9152SyncModuleTreePageRequirementsToModulePrd({
      previousTree,
      nextTree: response.tree,
      source: 'AI_TREE_PATCH',
      preferredNodeId: currentSelectedModuleId,
    })

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
    ? '新增 PAGE 已自动同步到所属模块 PRD 的页面需求章节。建议下一步打开对应页面生成交互原型；系统会优先使用最新 PRD 页面契约。'
    : '建议下一步检查新增节点的核心需求资产；如已有上游资产受影响，可在影响分析中使用 AI 局部同步修订。'

  return [
    `模块树变更提案已应用。${createdPart}`,
    nextStep,
    '模块树中的 PAGE 变化会自动同步到所属模块 PRD；已有交互原型不会自动改代码，需要时在页面上重新生成交互原型。',
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

  a91524MaybeSuggestLegacyPrdMigration(node)
}

function a91524ModulePrdHasStructuredPageRequirements(artifact: unknown): boolean {
  const record = artifact && typeof artifact === 'object' && !Array.isArray(artifact)
    ? artifact as Record<string, any>
    : {}
  const content = record.content && typeof record.content === 'object' && !Array.isArray(record.content)
    ? record.content as Record<string, any>
    : {}
  const pageRequirements = content.page_requirements || content.pageRequirements
  return Array.isArray(pageRequirements) && pageRequirements.length > 0
}

function a91524HasCurrentModulePrdArtifact(): boolean {
  const artifact = prdArtifact.value as any
  return Boolean(artifact?.exists || artifact?.artifactId || artifact?.id)
}

function a91524MaybeSuggestLegacyPrdMigration(node: ModuleTreeNode): void {
  if (!node?.id || resolveRequirementNodeType(node) !== 'MODULE') {
    return
  }

  if (!a91524HasCurrentModulePrdArtifact()) {
    return
  }

  if (a91524ModulePrdHasStructuredPageRequirements(prdArtifact.value)) {
    return
  }

  const pageNodes = collectDescendantPageNodes(node)
  if (!pageNodes.length) {
    return
  }

  const artifact = prdArtifact.value as any
  const hintKey = `${node.id}:${artifact?.artifactId || artifact?.id || artifact?.versionNo || 'current'}`
  if (a91524LegacyPrdMigrationHintKeys.value.has(hintKey)) {
    return
  }

  a91524LegacyPrdMigrationHintKeys.value = new Set([
    ...a91524LegacyPrdMigrationHintKeys.value,
    hintKey,
  ])

  const pageNames = pageNodes
    .slice(0, 6)
    .map((pageNode) => getModuleDisplayName(pageNode))
    .join('、')

  const instruction = [
    '请重新生成当前 MODULE_PRD：基于当前模块树和旧 PRD，调用 Requirement Agent 升级为新版结构化 PRD。',
    '必须保留旧 PRD 已有业务内容和章节语义，只补齐或重构结构化 content.page_requirements / 页面需求与原型契约。',
    'page_requirements 必须覆盖当前 MODULE 下全部 PAGE，并锁定 page_key、page_name、page_type、route_path、form_fields、table_columns、primary_actions、row_actions。',
    '不要修改 Development Agent，不要生成 Node.js 代码，不要直接改 UI_CONTRACT。',
  ].join('\n')

  latestAssistantResponse.value = {
    messageId: `a91524_legacy_prd_migration_hint_${Date.now()}`,
    intent: 'GENERAL_QA',
    reply: [
      `检测到当前模块“${getModuleDisplayName(node)}”已有旧版 PRD，但还没有新版结构化页面契约 page_requirements。`,
      `当前模块树下已有 ${pageNodes.length} 个 PAGE：${pageNames}${pageNodes.length > 6 ? '等' : ''}。`,
      '建议用 LLM 升级旧 PRD：保留旧文档语义，同时补齐“页面需求与原型契约”章节。',
    ].join('\n'),
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    assetEditProposal: null,
    attachmentDrivenChangeProposal: null,
    attachmentArtifactDiffProposal: null,
    actions: [
      {
        type: 'QUICK_REPLY',
        label: '用 LLM 升级旧 PRD',
        payload: { text: instruction },
      },
    ],
  } as any
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

  const previousTree = a9152DeepCloneModuleTree(modules.value)

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

    await applyModuleMutationResponse(response, currentNodeId, previousTree, 'MOVE_NODE')
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
  const previousTree = a9152DeepCloneModuleTree(modules.value)

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
      await applyModuleMutationResponse(response, response.node?.id || null, previousTree, 'CREATE_ROOT_NODE')
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
      await applyModuleMutationResponse(response, response.node?.id || null, previousTree, 'CREATE_CHILD_NODE')
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
      await applyModuleMutationResponse(response, nodeEditorTarget.value.id, previousTree, 'UPDATE_NODE')
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
  const deletedNode = nodeDeleteTarget.value
  const previousTree = a9152DeepCloneModuleTree(modules.value)

  try {
    const response = await deleteProjectModuleNode(
      props.workspace.workspaceId,
      props.project.projectCode,
      deletedId,
    )

    closeNodeDeleteDialog()

    modules.value = response.tree

    await a9152SyncModuleTreePageRequirementsToModulePrd({
      previousTree,
      nextTree: response.tree,
      source: 'DELETE_NODE',
      preferredNodeId: null,
      deletedNode,
    })

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
  previousTree: ModuleTreeNode[] = a9152DeepCloneModuleTree(modules.value),
  source = 'MODULE_TREE_MUTATION',
) {
  modules.value = response.tree

  await a9152SyncModuleTreePageRequirementsToModulePrd({
    previousTree,
    nextTree: response.tree,
    source,
    preferredNodeId: preferredSelectedNodeId,
  })

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

// A9.10.14.4.3.15.2.1_MODULE_TREE_TO_PRD_PAGE_SYNC
// MODULE_TREE is the authority for PAGE existence; MODULE_PRD.page_requirements
// is the authority for page business contract. This deterministic sync keeps
// PAGE nodes and PRD page cards aligned without touching Development Agent code.
type A9152PrdPageGroup = {
  module: ModuleTreeNode
  pages: ModuleTreeNode[]
  signature: string
}

type A9152SyncRequest = {
  previousTree: ModuleTreeNode[]
  nextTree: ModuleTreeNode[]
  source: string
  preferredNodeId?: string | null
  deletedNode?: ModuleTreeNode | null
}

async function a9152SyncModuleTreePageRequirementsToModulePrd(
  request: A9152SyncRequest,
): Promise<void> {
  const beforeGroups = a9152CollectPrdPageGroups(request.previousTree)
  const afterGroups = a9152CollectPrdPageGroups(request.nextTree)
  const affectedModuleIds = new Set<string>()

  for (const [moduleId, before] of beforeGroups.entries()) {
    const after = afterGroups.get(moduleId)
    if (after?.signature !== before.signature) {
      affectedModuleIds.add(moduleId)
    }
  }

  for (const [moduleId, after] of afterGroups.entries()) {
    const before = beforeGroups.get(moduleId)
    if (before?.signature !== after.signature) {
      affectedModuleIds.add(moduleId)
    }
  }

  const deletedOwnerId = a9152ResolveOwnerModuleIdFromOldTree(
    request.previousTree,
    request.deletedNode,
  )
  if (deletedOwnerId && afterGroups.has(deletedOwnerId)) {
    affectedModuleIds.add(deletedOwnerId)
  }

  if (affectedModuleIds.size === 0) {
    return
  }

  let syncedCount = 0

  for (const moduleId of affectedModuleIds) {
    const group = afterGroups.get(moduleId)
    if (!group?.module?.id) {
      continue
    }

    try {
      const changed = await a9152SaveSyncedModulePrdPageRequirements(
        group.module,
        group.pages,
        request.source,
      )

      if (changed) {
        syncedCount += 1
        a94371MarkRequirementImpactForDescendantPages(group.module)
      }
    } catch (error) {
      console.warn('[A9.10.14.4.3.15.2.1] MODULE_PRD page_requirements sync failed:', error)
    }
  }

  if (syncedCount > 0) {
    artifactTaskFeedback.value = {
      level: 'success',
      message: `模块树 PAGE 变化已自动同步到 ${syncedCount} 个模块需求的“页面需求与原型契约”章节。`,
    }

    if (
      selectedModule.value?.id &&
      affectedModuleIds.has(selectedModule.value.id) &&
      resolveRequirementNodeType(selectedModule.value) === 'MODULE'
    ) {
      await Promise.allSettled([
        loadCurrentArtifactByType(selectedModule.value, 'MODULE_PRD'),
        loadAllCurrentModuleArtifacts(selectedModule.value),
      ])
    }
  }
}

function a9152CollectPrdPageGroups(tree: ModuleTreeNode[]): Map<string, A9152PrdPageGroup> {
  const groups = new Map<string, { module: ModuleTreeNode; pages: ModuleTreeNode[] }>()

  const visit = (node: ModuleTreeNode, owner: ModuleTreeNode | null) => {
    const nodeType = resolveRequirementNodeType(node)
    const nextOwner = nodeType === 'MODULE' ? node : owner

    if (nodeType === 'PAGE' && nextOwner?.id) {
      const key = String(nextOwner.id)
      const existing = groups.get(key) || { module: nextOwner, pages: [] }
      existing.pages.push(node)
      groups.set(key, existing)
    }

    for (const child of node.children || []) {
      visit(child, nextOwner)
    }
  }

  for (const root of tree || []) {
    visit(root, null)
  }

  const result = new Map<string, A9152PrdPageGroup>()

  for (const [moduleId, group] of groups.entries()) {
    const pages = [...group.pages].sort((a, b) =>
      a9152ComparablePageIdentity(a).localeCompare(a9152ComparablePageIdentity(b)),
    )
    result.set(moduleId, {
      module: group.module,
      pages,
      signature: JSON.stringify(pages.map(a9152PageSignature)),
    })
  }

  return result
}

function a9152PageSignature(node: ModuleTreeNode): Record<string, unknown> {
  return {
    id: node.id || '',
    parentId: node.parentId || '',
    key: a9152NodeStableKey(node),
    name: getModuleDisplayName(node),
    type: resolveRequirementNodeType(node),
    pageType: a9152InferPageType(node, null),
    route: a9152NodeRoute(node),
  }
}

function a9152ComparablePageIdentity(node: ModuleTreeNode): string {
  return [
    a9152NodeStableKey(node),
    getModuleDisplayName(node),
    node.id || '',
  ].join('|')
}

function a9152ResolveOwnerModuleIdFromOldTree(
  tree: ModuleTreeNode[],
  node: ModuleTreeNode | null | undefined,
): string {
  if (!node?.id) return ''

  const path = findRequirementNodePath(tree, node.id) || []
  for (let index = path.length - 1; index >= 0; index -= 1) {
    const candidate = path[index]
    if (resolveRequirementNodeType(candidate) === 'MODULE' && candidate.id) {
      return candidate.id
    }
  }

  return ''
}

async function a9152SaveSyncedModulePrdPageRequirements(
  moduleNode: ModuleTreeNode,
  pageNodes: ModuleTreeNode[],
  source: string,
): Promise<boolean> {
  if (!moduleNode.id) return false

  let artifact: any | null = null

  try {
    artifact = await fetchCurrentModuleArtifact(
      props.workspace.workspaceId,
      props.project.projectCode,
      moduleNode.id,
      'MODULE_PRD',
    )
  } catch {
    artifact = null
  }

  const existingContent = a9152Record(artifact?.content)
    ? a9152DeepCloneRecord(artifact.content as Record<string, any>)
    : {}

  const beforeSignature = a9152PrdPageRequirementsSignature(existingContent)

  const nextContent = a9152BuildSyncedModulePrdContent(
    existingContent,
    moduleNode,
    pageNodes,
    source,
  )

  const afterSignature = a9152PrdPageRequirementsSignature(nextContent)

  if (beforeSignature === afterSignature) {
    return false
  }

  const nextMarkdown = a9152BuildSyncedModulePrdMarkdown(
    nextContent,
    moduleNode,
    artifact?.contentMarkdown || '',
  )

  await saveManualArtifactEdit(
    props.workspace.workspaceId,
    props.project.projectCode,
    moduleNode.id,
    {
      artifactId: artifact?.artifactId || null,
      artifactType: 'MODULE_PRD',
      content: nextContent,
      contentMarkdown: nextMarkdown,
      reason: `模块树 PAGE 变化自动同步到 PRD 页面需求：${source}`,
    } as any,
  )

  return true
}

function a9152BuildSyncedModulePrdContent(
  existingContent: Record<string, any>,
  moduleNode: ModuleTreeNode,
  pageNodes: ModuleTreeNode[],
  source: string,
): Record<string, any> {
  const nextContent = a9152DeepCloneRecord(existingContent)
  const oldBasicInfo = a9152Record(nextContent.module_basic_info)
    ? nextContent.module_basic_info as Record<string, any>
    : {}

  nextContent.module_basic_info = {
    ...oldBasicInfo,
    module_name: getModuleDisplayName(moduleNode),
    module_key: a9152NodeStableKey(moduleNode),
    module_type: resolveRequirementNodeType(moduleNode) || a9152NodeTypeRaw(moduleNode) || 'MODULE',
    module_position: oldBasicInfo.module_position || moduleNode.modulePath || '',
  }

  const existingPages = Array.isArray(nextContent.page_requirements)
    ? nextContent.page_requirements
        .map((item: unknown) => a9152Record(item) ? item as Record<string, any> : null)
        .filter((item: Record<string, any> | null): item is Record<string, any> => Boolean(item))
    : []

  const usedExistingIndexes = new Set<number>()
  const syncedPages = pageNodes.map((pageNode) => {
    const matched = a9152FindExistingPageRequirement(existingPages, pageNode, usedExistingIndexes)
    return a9152BuildSyncedPageRequirement(pageNode, matched, moduleNode, pageNodes)
  })

  const activeKeys = new Set(syncedPages.map((item) => a9152NormalizeKey(item.page_key || item.pageKey || item.page_name || item.pageName)))
  const archived = [
    ...a9152Array(nextContent._archived_page_requirements)
      .filter((item): item is Record<string, any> => a9152Record(item)),
    ...existingPages
      .filter((_item, index) => !usedExistingIndexes.has(index))
      .filter((item) => !activeKeys.has(a9152NormalizeKey(item.page_key || item.pageKey || item.page_name || item.pageName)))
      .map((item) => ({
        ...item,
        archived: true,
        archived_at: new Date().toISOString(),
        archived_reason: 'MODULE_TREE_PAGE_REMOVED_OR_MOVED',
      })),
  ]

  nextContent.page_requirements = syncedPages

  if (archived.length > 0) {
    nextContent._archived_page_requirements = a9152DedupeRecordsByKey(
      archived,
      (item) => a9152NormalizeKey(item.page_key || item.pageKey || item.page_name || item.pageName || JSON.stringify(item)),
    )
  }

  nextContent._module_tree_prd_sync = {
    source,
    syncedAt: new Date().toISOString(),
    pageCount: syncedPages.length,
    authority: 'MODULE_TREE_PAGE_EXISTENCE_TO_MODULE_PRD_PAGE_REQUIREMENTS',
    marker: 'A9.10.14.4.3.15.2.1',
  }

  return nextContent
}

function a9152FindExistingPageRequirement(
  existingPages: Record<string, any>[],
  pageNode: ModuleTreeNode,
  usedIndexes: Set<number>,
): Record<string, any> | null {
  const candidates = [
    pageNode.id,
    a9152NodeStableKey(pageNode),
    getModuleDisplayName(pageNode),
    a9152NodeRoute(pageNode),
  ].map(a9152NormalizeKey).filter(Boolean)

  let bestIndex = -1
  let bestScore = 0

  existingPages.forEach((item, index) => {
    if (usedIndexes.has(index)) return

    const keys = [
      item.node_id,
      item.nodeId,
      item.page_node_id,
      item.pageNodeId,
      item.module_id,
      item.page_key,
      item.pageKey,
      item.page_name,
      item.pageName,
      item.route_path,
      item.routePath,
      item.route,
    ].map(a9152NormalizeKey).filter(Boolean)

    let score = 0
    for (const candidate of candidates) {
      if (candidate && keys.includes(candidate)) {
        score += 1
      }
    }

    if (score > bestScore) {
      bestScore = score
      bestIndex = index
    }
  })

  if (bestIndex < 0) {
    return null
  }

  usedIndexes.add(bestIndex)
  return existingPages[bestIndex]
}

function a9152BuildSyncedPageRequirement(
  pageNode: ModuleTreeNode,
  existing: Record<string, any> | null,
  moduleNode: ModuleTreeNode,
  siblingPages: ModuleTreeNode[],
): Record<string, any> {
  const pageName = getModuleDisplayName(pageNode)
  const pageKey = a9152NodeStableKey(pageNode)
  const pageType = a9152InferPageType(pageNode, existing)
  const routePath = a9152NodeRoute(pageNode) || a9152InferRoutePath(moduleNode, pageNode)
  const entity = a9152InferPrimaryEntity(moduleNode, pageNode, existing)
  const defaults = a9152DefaultPageContract(entity, pageType, pageName, siblingPages)

  const result: Record<string, any> = {
    ...(existing || {}),
    node_id: pageNode.id || existing?.node_id || existing?.nodeId || null,
    page_key: pageKey,
    page_name: pageName,
    page_type: pageType,
    route_path: routePath,
    layout_type: a9152LayoutTypeForPageType(pageType),
    primary_entity: entity,
    description: a9152Text(existing?.description) || a9152DefaultPageDescription(pageName, pageType, entity),
    main_components: a9152ListOrDefault(existing?.main_components || existing?.mainComponents, defaults.mainComponents),
    user_actions: a9152ListOrDefault(existing?.user_actions || existing?.userActions, defaults.userActions),
    filter_fields: a9152ListOrDefault(existing?.filter_fields || existing?.filterFields, defaults.filterFields),
    table_columns: a9152ListOrDefault(existing?.table_columns || existing?.tableColumns, defaults.tableColumns),
    form_fields: a9152ListOrDefault(existing?.form_fields || existing?.formFields, defaults.formFields),
    primary_actions: a9152ListOrDefault(existing?.primary_actions || existing?.primaryActions, defaults.primaryActions),
    row_actions: a9152ListOrDefault(existing?.row_actions || existing?.rowActions, defaults.rowActions),
    navigation_targets: a9152ListOrDefault(existing?.navigation_targets || existing?.navigationTargets, defaults.navigationTargets),
    source: existing?.source || 'MODULE_TREE_SYNC',
    sync_status: 'SYNCED_FROM_MODULE_TREE',
  }

  delete result.archived
  delete result.archived_at
  delete result.archived_reason

  return result
}

function a9152DefaultPageContract(
  entity: string,
  pageType: string,
  pageName: string,
  siblingPages: ModuleTreeNode[],
): {
  mainComponents: string[]
  userActions: string[]
  filterFields: string[]
  tableColumns: string[]
  formFields: string[]
  primaryActions: string[]
  rowActions: string[]
  navigationTargets: string[]
} {
  const domain = a9152DomainFields(entity, pageName)
  const navigationTargets = siblingPages
    .filter((page) => getModuleDisplayName(page) !== pageName)
    .map((page) => getModuleDisplayName(page))
    .filter(Boolean)

  if (pageType === 'LIST') {
    return {
      mainComponents: ['筛选区', '结果列表', '行操作区'],
      userActions: ['查询', '重置', `新建${entity}`],
      filterFields: domain.filterFields,
      tableColumns: domain.tableColumns,
      formFields: [],
      primaryActions: ['查询', '重置', `新建${entity}`],
      rowActions: ['查看详情', '编辑', '查看审核'],
      navigationTargets,
    }
  }

  if (pageType === 'DETAIL') {
    return {
      mainComponents: ['概要信息', '明细信息', '状态与操作记录'],
      userActions: ['返回', '编辑', '查看审核'],
      filterFields: [],
      tableColumns: domain.detailColumns,
      formFields: [],
      primaryActions: ['返回'],
      rowActions: [],
      navigationTargets,
    }
  }

  if (pageType === 'APPROVAL') {
    return {
      mainComponents: ['审核对象信息', '审核意见表单', '审核记录'],
      userActions: ['通过', '驳回', '返回'],
      filterFields: [],
      tableColumns: domain.detailColumns,
      formFields: ['审核意见', '审核备注'],
      primaryActions: ['通过', '驳回', '返回'],
      rowActions: [],
      navigationTargets,
    }
  }

  return {
    mainComponents: ['基础信息表单', '操作区'],
    userActions: ['保存', '取消'],
    filterFields: [],
    tableColumns: [],
    formFields: domain.formFields,
    primaryActions: ['保存', '取消'],
    rowActions: [],
    navigationTargets,
  }
}

function a9152DomainFields(entity: string, pageName: string): {
  filterFields: string[]
  tableColumns: string[]
  formFields: string[]
  detailColumns: string[]
} {
  const text = `${entity} ${pageName}`

  if (/入库/.test(text)) {
    return {
      filterFields: ['入库单号', '供应商', '仓库', '入库状态', '创建日期'],
      tableColumns: ['入库单号', '供应商', '仓库', '入库总数量', '入库状态', '创建人', '创建时间'],
      formFields: ['入库单号', '供应商', '仓库', '入库总数量', '备注'],
      detailColumns: ['入库单号', '供应商', '仓库', '入库总数量', '入库状态', '创建人', '创建时间', '备注'],
    }
  }

  if (/出库/.test(text)) {
    return {
      filterFields: ['出库单号', '客户/领用方', '仓库', '出库状态', '创建日期'],
      tableColumns: ['出库单号', '客户/领用方', '仓库', '出库总数量', '出库状态', '创建人', '创建时间'],
      formFields: ['出库单号', '客户/领用方', '仓库', '出库总数量', '备注'],
      detailColumns: ['出库单号', '客户/领用方', '仓库', '出库总数量', '出库状态', '创建人', '创建时间', '备注'],
    }
  }

  if (/库存/.test(text)) {
    return {
      filterFields: ['物料/商品', '仓库', '库存状态', '更新时间'],
      tableColumns: ['物料/商品', '仓库', '可用数量', '锁定数量', '库存状态', '更新时间'],
      formFields: ['物料/商品', '仓库', '库存数量', '预警阈值', '备注'],
      detailColumns: ['物料/商品', '仓库', '可用数量', '锁定数量', '预警阈值', '库存状态', '更新时间'],
    }
  }

  const base = entity && entity !== '业务对象' ? entity : '业务对象'
  return {
    filterFields: [`${base}编号`, `${base}名称`, '状态', '创建日期'],
    tableColumns: [`${base}编号`, `${base}名称`, '状态', '创建人', '创建时间'],
    formFields: [`${base}名称`, '状态', '备注'],
    detailColumns: [`${base}编号`, `${base}名称`, '状态', '创建人', '创建时间', '备注'],
  }
}

function a9152InferPageType(
  pageNode: ModuleTreeNode,
  existing: Record<string, any> | null,
): string {
  const extra = a9152NodeExtra(pageNode)
  const raw = a9152Text(
    pageNode.pageKind ||
      pageNode.layoutType ||
      extra.pageType ||
      extra.page_type ||
      extra.pageKind ||
      extra.page_kind ||
      existing?.page_type ||
      existing?.pageType ||
      existing?.layout_type ||
      existing?.layoutType,
  ).toUpperCase()

  const name = `${getModuleDisplayName(pageNode)} ${a9152NodeStableKey(pageNode)} ${raw}`

  if (/APPROVAL|AUDIT|审核|审批/.test(name)) return 'APPROVAL'
  if (/DETAIL|VIEW|详情|查看/.test(name)) return 'DETAIL'
  if (/LIST|SEARCH|INDEX|列表|查询|管理/.test(name)) return 'LIST'
  if (/FORM|CREATE|NEW|ADD|EDIT|UPDATE|创建|新建|新增|录入|编辑|修改|数量/.test(name)) return 'FORM'

  return raw || 'FORM'
}

function a9152LayoutTypeForPageType(pageType: string): string {
  if (pageType === 'LIST') return 'TABLE_LIST'
  if (pageType === 'DETAIL') return 'DETAIL_VIEW'
  if (pageType === 'APPROVAL') return 'APPROVAL_FORM'
  return 'FORM_DETAIL'
}

function a9152DefaultPageDescription(
  pageName: string,
  pageType: string,
  entity: string,
): string {
  if (pageType === 'LIST') return `${pageName}用于查询和管理${entity}，提供筛选、列表展示和行级操作。`
  if (pageType === 'DETAIL') return `${pageName}用于查看${entity}的完整信息、状态和处理记录。`
  if (pageType === 'APPROVAL') return `${pageName}用于完成${entity}审核处理，支持审核意见、通过和驳回。`
  return `${pageName}用于录入或维护${entity}基础信息，提供保存和取消操作。`
}

function a9152InferPrimaryEntity(
  moduleNode: ModuleTreeNode,
  pageNode: ModuleTreeNode,
  existing: Record<string, any> | null,
): string {
  const explicit = a9152Text(
    existing?.primary_entity ||
      existing?.primaryEntity ||
      pageNode.primaryEntity ||
      a9152NodeExtra(pageNode).primaryEntity ||
      a9152NodeExtra(pageNode).primary_entity ||
      a9152NodeExtra(moduleNode).primaryEntity ||
      a9152NodeExtra(moduleNode).primary_entity,
  )

  if (explicit) return explicit

  const text = `${getModuleDisplayName(moduleNode)} ${getModuleDisplayName(pageNode)}`
  if (/入库/.test(text)) return '入库单'
  if (/出库/.test(text)) return '出库单'
  if (/库存/.test(text)) return '库存记录'
  if (/供应商/.test(text)) return '供应商'
  if (/订单/.test(text)) return '订单'
  return '业务对象'
}

function a9152InferRoutePath(moduleNode: ModuleTreeNode, pageNode: ModuleTreeNode): string {
  const moduleKey = a9152NodeStableKey(moduleNode) || 'module'
  const pageKey = a9152NodeStableKey(pageNode) || 'page'
  return `/${moduleKey.replace(/_/g, '-')}/${pageKey.replace(/_/g, '-')}`
}

function a9152NodeRoute(node: ModuleTreeNode): string {
  const extra = a9152NodeExtra(node)
  return a9152Text(
    node.routePath ||
      extra.routePath ||
      extra.route_path ||
      extra.route,
  )
}

function a9152NodeStableKey(node: ModuleTreeNode): string {
  const extra = a9152NodeExtra(node)
  return a9152Text(
    node.moduleKey ||
      node.key ||
      extra.moduleKey ||
      extra.module_key ||
      extra.pageKey ||
      extra.page_key ||
      a9152Slugify(getModuleDisplayName(node)),
  )
}

function a9152NodeTypeRaw(node: ModuleTreeNode): string {
  const extra = a9152NodeExtra(node)
  return a9152Text(node.moduleType || node.type || extra.nodeType || extra.node_type)
}

function a9152NodeExtra(node: ModuleTreeNode | null | undefined): Record<string, unknown> {
  return node?.extra && typeof node.extra === 'object' && !Array.isArray(node.extra)
    ? node.extra as Record<string, unknown>
    : {}
}

function a9152PrdPageRequirementsSignature(content: Record<string, any>): string {
  return JSON.stringify({
    module_basic_info: content.module_basic_info || {},
    page_requirements: content.page_requirements || [],
    archived: content._archived_page_requirements || [],
  })
}

function a9152BuildSyncedModulePrdMarkdown(
  content: Record<string, any>,
  moduleNode: ModuleTreeNode,
  fallbackMarkdown: string,
): string {
  const info = a9152Record(content.module_basic_info) ? content.module_basic_info as Record<string, any> : {}
  const title = a9152Text(info.module_name) || getModuleDisplayName(moduleNode) || '模块需求'
  const lines: string[] = [`# ${title}模块需求`]

  const positioning = [
    a9152Text(info.module_position),
    a9152Text(info.module_goal),
    a9152Text(info.responsibility_boundary),
  ].filter(Boolean)

  if (positioning.length) {
    lines.push('## 1. 模块定位', positioning.join('\n\n'))
  } else if (fallbackMarkdown.trim()) {
    const fallbackIntro = fallbackMarkdown.split(/\r?\n##\s+/)[0]?.trim()
    if (fallbackIntro) {
      lines.push('## 1. 模块定位', fallbackIntro.replace(/^#\s+.+?\n?/, '').trim() || `${title}用于承载当前模块的核心业务需求。`)
    }
  }

  a9152PushTextSection(lines, '2. 业务背景', content.business_background)
  a9152PushArraySection(lines, '3. 使用角色', content.user_roles, (item) => `${a9152Text(item.role_name || item.name || item.role_key) || '用户角色'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '4. 功能点需求', content.functional_requirements, (item) => `${a9152Text(item.title || item.requirement_name || item.requirement_key) || '功能需求'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '5. 关键业务规则', content.business_rules, (item) => `${a9152Text(item.title || item.rule_key) || '业务规则'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '6. 状态与流转规则', content.state_rules, (item) => `${a9152Text(item.state_name || item.state_key) || '状态'}：${a9152Text(item.description)}`)
  a9152PushPageRequirementsSection(lines, content.page_requirements)
  a9152PushArraySection(lines, '8. 数据需求', content.data_requirements, (item) => `${a9152Text(item.entity_name || item.entity_key) || '数据对象'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '9. 接口需求', content.api_requirements, (item) => `${a9152Text(item.api_name || item.path_suggestion || item.api_key) || '接口'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '10. 权限与操作边界', content.permission_requirements, (item) => `${a9152Text(item.permission_name || item.permission_key) || '权限'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '11. 异常场景', content.exception_scenarios, (item) => `${a9152Text(item.title || item.scenario_key) || '异常场景'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '12. 验收要点', content.acceptance_criteria, (item) => `${a9152Text(item.criterion_key) || '验收标准'}：${a9152Text(item.description)}`)
  a9152PushArraySection(lines, '13. 非功能需求', content.non_functional_requirements, (item) => `${a9152Text(item.category || item.nfr_key) || '非功能需求'}：${a9152Text(item.description || item.target)}`)
  a9152PushTextSection(lines, '14. 分析摘要', content.analysis_summary)

  return lines.filter((item) => String(item || '').trim()).join('\n\n')
}

function a9152PushTextSection(lines: string[], title: string, value: unknown): void {
  const text = a9152Text(value)
  if (!text) return
  lines.push(`## ${title}`, text)
}

function a9152PushArraySection(
  lines: string[],
  title: string,
  value: unknown,
  formatter: (item: Record<string, any>) => string,
): void {
  const items = a9152Array(value).filter((item): item is Record<string, any> => a9152Record(item))
  if (!items.length) return
  lines.push(`## ${title}`)
  lines.push(...items.map((item) => `- ${formatter(item).replace(/\s+/g, ' ').trim()}`))
}

function a9152PushPageRequirementsSection(lines: string[], value: unknown): void {
  const items = a9152Array(value).filter((item): item is Record<string, any> => a9152Record(item))
  if (!items.length) return

  lines.push('## 7. 页面规划')

  items.forEach((item, index) => {
    const title = a9152Text(item.page_name || item.pageName || item.page_key || item.pageKey) || `页面 ${index + 1}`
    const details = [
      a9152Text(item.description),
      `- 页面类型：${a9152Text(item.page_type || item.pageType) || '未设置'}`,
      a9152Text(item.route_path || item.routePath || item.route) ? `- 路由：${a9152Text(item.route_path || item.routePath || item.route)}` : '',
      a9152ListLine('筛选字段', item.filter_fields || item.filterFields),
      a9152ListLine('表格列', item.table_columns || item.tableColumns),
      a9152ListLine('表单字段', item.form_fields || item.formFields),
      a9152ListLine('主操作', item.primary_actions || item.primaryActions || item.user_actions || item.userActions),
      a9152ListLine('行操作', item.row_actions || item.rowActions),
      a9152ListLine('跳转目标', item.navigation_targets || item.navigationTargets),
    ].filter(Boolean)

    lines.push(`### 7.${index + 1} ${title}`, details.join('\n'))
  })
}

function a9152ListLine(label: string, value: unknown): string {
  const items = a9152NonEmptyArray(value)
  return items.length ? `- ${label}：${items.join('、')}` : ''
}

function a9152DedupeRecordsByKey(
  items: Record<string, any>[],
  getKey: (item: Record<string, any>) => string,
): Record<string, any>[] {
  const seen = new Set<string>()
  const result: Record<string, any>[] = []

  for (const item of items) {
    const key = getKey(item)
    if (!key || seen.has(key)) continue
    seen.add(key)
    result.push(item)
  }

  return result
}

function a9152DeepCloneModuleTree(tree: ModuleTreeNode[]): ModuleTreeNode[] {
  try {
    return JSON.parse(JSON.stringify(tree || []))
  } catch {
    return [...(tree || [])]
  }
}

function a9152DeepCloneRecord(value: Record<string, any>): Record<string, any> {
  try {
    return JSON.parse(JSON.stringify(value || {}))
  } catch {
    return { ...(value || {}) }
  }
}

function a9152Record(value: unknown): value is Record<string, any> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function a9152Array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function a9152NonEmptyArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.map(a9152Text).filter(Boolean)
}

function a9152ListOrDefault(value: unknown, fallback: string[]): string[] {
  const items = a9152NonEmptyArray(value)
  return items.length ? items : fallback
}

function a9152Text(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(a9152Text).filter(Boolean).join('、')
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    return a9152Text(record.label || record.name || record.title || record.description || '')
  }
  return String(value || '').trim()
}

function a9152NormalizeKey(value: unknown): string {
  return a9152Text(value)
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-:/\\]+/g, '')
}

function a9152Slugify(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/gi, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 64)
}


// A9.10.14.4.3.15.2.2_PRD_PAGE_REQUIREMENTS_TO_MODULE_TREE_SYNC
// PRD.page_requirements is the authority for page business contract; when the
// user edits that section, synchronize PAGE existence/name/key/type/route back
// to the module tree. This does not touch UI_CONTRACT or Development Agent code.
type A91522PageRequirementRecord = Record<string, any>

type A91522SyncSummary = {
  created: number
  updated: number
  deleted: number
  skipped: number
}

async function a91522SyncModuleTreeFromModulePrdPageRequirements(
  payload: ManualArtifactEditPayload,
  response: Record<string, any>,
): Promise<void> {
  const moduleNode = selectedModule.value

  if (!moduleNode?.id || resolveRequirementNodeType(moduleNode) !== 'MODULE') {
    return
  }

  const content = a91522ResolveSavedPrdContent(payload, response)
  if (!content || !Array.isArray(content.page_requirements)) {
    return
  }

  const editedSection = a91522String(
    a91522Record(content._prd_section_edit)
      ? (content._prd_section_edit as Record<string, any>).sectionKey || (content._prd_section_edit as Record<string, any>).section_key
      : '',
  )

  // For now, only structured page_requirements edits drive reverse tree sync.
  // Full markdown-only edits remain document edits and do not mutate the tree.
  if (editedSection && editedSection !== 'page_requirements') {
    return
  }

  if (!editedSection && !Array.isArray((payload as any).content?.page_requirements)) {
    return
  }

  const requirements = a91522NormalizePageRequirements(content.page_requirements)
  const owner = findA9ModuleTreeNodeById(modules.value, moduleNode.id) || moduleNode
  const currentPages = a91522CollectOwnedPageNodes(owner)

  if (!requirements.length && currentPages.length > 0) {
    artifactTaskFeedback.value = {
      level: 'info',
      message: '页面需求章节为空，已跳过模块树 PAGE 自动删除，避免误删现有页面。',
    }
    return
  }

  const summary: A91522SyncSummary = {
    created: 0,
    updated: 0,
    deleted: 0,
    skipped: 0,
  }

  const usedPageIds = new Set<string>()

  for (const requirement of requirements) {
    const matchedPage = a91522FindMatchingPageNode(requirement, currentPages, usedPageIds)
    const parentId = a91522ResolveParentIdForPageRequirement(requirement, owner)
    const nextName = a91522PageRequirementName(requirement)
    const nextKey = a91522PageRequirementKey(requirement)
    const nextExtra = a91522BuildPageNodeExtra(requirement, matchedPage)

    if (!nextName) {
      summary.skipped += 1
      continue
    }

    if (matchedPage?.id) {
      if (!a91522ShouldUpdatePageNode(matchedPage, requirement, nextName, nextKey, nextExtra)) {
        continue
      }

      try {
        const updateResponse = await updateProjectModuleNode(
          props.workspace.workspaceId,
          props.project.projectCode,
          matchedPage.id,
          {
            moduleName: nextName,
            moduleKey: nextKey || a9152NodeStableKey(matchedPage),
            moduleType: 'PAGE',
            extra: nextExtra,
          },
        )
        modules.value = updateResponse.tree
        summary.updated += 1
      } catch (error) {
        console.warn('[A9.10.14.4.3.15.2.2] update PAGE node from PRD failed:', error)
        summary.skipped += 1
      }
      continue
    }

    if (!parentId) {
      summary.skipped += 1
      continue
    }

    try {
      const createResponse = await createProjectModuleNode(
        props.workspace.workspaceId,
        props.project.projectCode,
        {
          parentId,
          moduleName: nextName,
          moduleKey: nextKey || a91522Slugify(nextName),
          moduleType: 'PAGE',
          extra: nextExtra,
        },
      )
      modules.value = createResponse.tree
      if (createResponse.node?.id) {
        usedPageIds.add(String(createResponse.node.id))
      }
      summary.created += 1
    } catch (error) {
      console.warn('[A9.10.14.4.3.15.2.2] create PAGE node from PRD failed:', error)
      summary.skipped += 1
    }
  }

  const activeRequirementKeys = new Set(requirements.flatMap(a91522PageRequirementIdentityKeys))
  const removablePages = currentPages.filter((page) => {
    if (!page.id || usedPageIds.has(String(page.id))) return false
    const pageKeys = a91522PageNodeIdentityKeys(page)
    return !pageKeys.some((key) => activeRequirementKeys.has(key))
  })

  // If a page card is removed from structured PRD.page_requirements, reflect that
  // in the module tree. The empty-array case is already blocked above as a safety net.
  for (const page of removablePages) {
    if (!page.id) continue
    try {
      const deleteResponse = await deleteProjectModuleNode(
        props.workspace.workspaceId,
        props.project.projectCode,
        page.id,
      )
      modules.value = deleteResponse.tree
      summary.deleted += 1
    } catch (error) {
      console.warn('[A9.10.14.4.3.15.2.2] delete PAGE node from PRD failed:', error)
      summary.skipped += 1
    }
  }

  const freshOwner = findA9ModuleTreeNodeById(modules.value, moduleNode.id) || moduleNode
  if (freshOwner?.id) {
    selectModule(freshOwner)
  }

  if (summary.created || summary.updated || summary.deleted || summary.skipped) {
    artifactTaskFeedback.value = {
      level: summary.skipped ? 'info' : 'success',
      message: [
        'PRD 页面需求已自动同步到模块树。',
        summary.created ? `新增 PAGE ${summary.created} 个` : '',
        summary.updated ? `更新 PAGE ${summary.updated} 个` : '',
        summary.deleted ? `删除 PAGE ${summary.deleted} 个` : '',
        summary.skipped ? `跳过 ${summary.skipped} 项` : '',
      ].filter(Boolean).join('，'),
    }
  }
}


// A9.10.14.4.3.15.4.10.4.5_PRD_EDIT_IMPACT_PRECISION
// Manual MODULE_PRD edits should not dirty every descendant PAGE.  This analyzer
// uses the edited section, structured PRD content, page identity keys, feature
// ownership, and page-name mentions to mark only traceable PAGE nodes.
type A91545PrdImpactResult = {
  sectionKey: string
  sectionLabel: string
  documentOnly: boolean
  affectedPageIds: string[]
  affectedPageNames: string[]
  confidence: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH'
  reason: string
}

function a91545ResolveCurrentModulePrdContentForImpact(): Record<string, any> | null {
  const content = (prdArtifact.value as any)?.content
  if (!a91522Record(content)) {
    return null
  }
  return a91545CloneRecord(content as Record<string, any>)
}

function a91545ApplyPrecisePrdEditImpact(
  payload: ManualArtifactEditPayload,
  response: Record<string, any>,
  previousContent: Record<string, any> | null,
): string {
  const moduleNode = selectedModule.value
  if (!moduleNode?.id || resolveRequirementNodeType(moduleNode) !== 'MODULE') {
    return ''
  }

  const nextContent = a91522ResolveSavedPrdContent(payload, response)
  if (!nextContent) {
    return ''
  }

  const owner = findA9ModuleTreeNodeById(modules.value, moduleNode.id) || moduleNode
  const result = a91545ResolvePrecisePrdImpact(previousContent, nextContent, owner)

  if (result.documentOnly) {
    return `PRD 已保存为新版本 V${response.versionNo || ''}；本次为补充说明/自由栏目变更，不会标记模块树、页面规范或交互原型为需更新。`
  }

  if (result.affectedPageIds.length > 0) {
    a94371MarkRequirementImpactForPageIds(result.affectedPageIds)
    const pageText = result.affectedPageNames.length
      ? result.affectedPageNames.join('、')
      : `${result.affectedPageIds.length} 个页面`
    return `PRD 已保存为新版本 V${response.versionNo || ''}；已精确定位受影响页面：${pageText}。仅这些页面会提示重新生成 PAGE_SPEC / 交互原型，未命中的页面不会被误标记。`
  }

  if (result.confidence === 'NONE') {
    return `PRD 已保存为新版本 V${response.versionNo || ''}；${result.reason || '本次未识别到页面字段、按钮、表格列、跳转或状态变更，未标记页面原型。'}`
  }

  return `PRD 已保存为新版本 V${response.versionNo || ''}；检测到“${result.sectionLabel}”变化，但未可靠定位到具体页面。系统不会自动点亮页面重生成提示，避免误耗 token；请按需检查模块树或页面设计。`
}

function a91545ResolvePrecisePrdImpact(
  previousContent: Record<string, any> | null,
  nextContent: Record<string, any>,
  owner: ModuleTreeNode,
): A91545PrdImpactResult {
  const sectionKey = a91545EditedSectionKey(nextContent)
  const sectionLabel = a91545SectionLabel(sectionKey)
  const pages = a91522CollectOwnedPageNodes(owner)
  const affectedIds = new Set<string>()

  if (a91545IsDocumentOnlyPrdEdit(nextContent)) {
    return a91545BuildImpactResult(sectionKey, sectionLabel, true, affectedIds, pages, 'NONE', '本次只改文档补充说明。')
  }

  if (a91545SectionChanged(previousContent, nextContent, 'page_requirements') || sectionKey === 'page_requirements') {
    a91545CollectChangedPageRequirementIds(previousContent, nextContent, pages, affectedIds)
  }

  const structuralSections = [
    'functional_requirements',
    'business_rules',
    'state_rules',
    'data_requirements',
    'api_requirements',
    'permission_requirements',
    'exception_scenarios',
    'acceptance_criteria',
  ]

  const candidateSections = sectionKey
    ? [sectionKey]
    : structuralSections.filter((key) => a91545SectionChanged(previousContent, nextContent, key))

  for (const key of candidateSections) {
    if (key === 'page_requirements') continue
    if (!structuralSections.includes(key)) continue

    const changedRecords = a91545ChangedRecordsForSection(previousContent, nextContent, key)
    const changedText = changedRecords.map(a91545StableJson).join('\n')
    a91545CollectPageIdsMentionedInText(changedText, pages, affectedIds)

    if (key === 'functional_requirements') {
      a91545CollectFeatureOwnedPageIds(owner, changedRecords, affectedIds)
    }
  }

  const hasAnyPrdChange = Boolean(sectionKey) || a91545StableJson(previousContent || {}) !== a91545StableJson(nextContent || {})
  const confidence: A91545PrdImpactResult['confidence'] = affectedIds.size > 0
    ? 'HIGH'
    : hasAnyPrdChange
      ? 'LOW'
      : 'NONE'

  const reason = !hasAnyPrdChange
    ? '本次 PRD 内容没有可识别变化。'
    : affectedIds.size > 0
      ? '已通过页面 Key / 页面名称 / 路由 / 功能归属定位页面。'
      : '未发现可可靠绑定到具体 PAGE 的页面名称、页面 Key、路由或功能归属。'

  return a91545BuildImpactResult(sectionKey, sectionLabel, false, affectedIds, pages, confidence, reason)
}

function a91545CollectChangedPageRequirementIds(
  previousContent: Record<string, any> | null,
  nextContent: Record<string, any>,
  pages: ModuleTreeNode[],
  affectedIds: Set<string>,
): void {
  const previousRequirements = a91522NormalizePageRequirements(previousContent?.page_requirements)
  const nextRequirements = a91522NormalizePageRequirements(nextContent.page_requirements)
  const previousByIdentity = new Map<string, A91522PageRequirementRecord>()

  for (const requirement of previousRequirements) {
    for (const key of a91522PageRequirementIdentityKeys(requirement)) {
      previousByIdentity.set(key, requirement)
    }
  }

  for (const requirement of nextRequirements) {
    const oldRequirement = a91522PageRequirementIdentityKeys(requirement)
      .map((key) => previousByIdentity.get(key))
      .find(Boolean)

    if (oldRequirement && a91545StableJson(oldRequirement) === a91545StableJson(requirement)) {
      continue
    }

    const matchedPage = a91522FindMatchingPageNode(requirement, pages, new Set())
    if (matchedPage?.id) {
      affectedIds.add(String(matchedPage.id))
    }

    const requirementText = a91545StableJson(requirement)
    a91545CollectPageIdsMentionedInText(requirementText, pages, affectedIds)
  }
}

function a91545CollectFeatureOwnedPageIds(
  owner: ModuleTreeNode,
  changedRecords: Record<string, any>[],
  affectedIds: Set<string>,
): void {
  if (!changedRecords.length) return

  const features = a91522CollectDescendantNodesByType(owner, 'FEATURE')
  if (!features.length) return

  for (const record of changedRecords) {
    const signals = a91545RecordIdentitySignals(record)
    if (!signals.length) continue

    for (const feature of features) {
      const featureSignals = a91545FeatureIdentitySignals(feature)
      const matched = signals.some((signal) => featureSignals.includes(signal))
      if (!matched) continue

      for (const page of a91522CollectOwnedPageNodes(feature)) {
        if (page.id) {
          affectedIds.add(String(page.id))
        }
      }
    }
  }
}

function a91545CollectPageIdsMentionedInText(
  text: string,
  pages: ModuleTreeNode[],
  affectedIds: Set<string>,
): void {
  const normalizedText = a91522NormalizeKey(text)
  if (!normalizedText) return

  for (const page of pages) {
    if (!page.id) continue
    const aliases = a91545PageIdentityAliases(page)
    const matched = aliases.some((alias) => alias.length >= 2 && normalizedText.includes(alias))
    if (matched) {
      affectedIds.add(String(page.id))
    }
  }
}

function a91545ChangedRecordsForSection(
  previousContent: Record<string, any> | null,
  nextContent: Record<string, any>,
  sectionKey: string,
): Record<string, any>[] {
  const previousItems = a91545ArrayRecords(previousContent?.[sectionKey])
  const nextItems = a91545ArrayRecords(nextContent?.[sectionKey])
  const previousByIdentity = new Map<string, Record<string, any>>()

  for (const item of previousItems) {
    const keys = a91545RecordIdentitySignals(item)
    for (const key of keys) previousByIdentity.set(key, item)
  }

  const changed: Record<string, any>[] = []
  for (const item of nextItems) {
    const oldItem = a91545RecordIdentitySignals(item)
      .map((key) => previousByIdentity.get(key))
      .find(Boolean)
    if (!oldItem || a91545StableJson(oldItem) !== a91545StableJson(item)) {
      changed.push(item)
    }
  }

  if (!changed.length && a91545SectionChanged(previousContent, nextContent, sectionKey)) {
    const nextValue = nextContent?.[sectionKey]
    if (a91522Record(nextValue)) {
      changed.push(nextValue as Record<string, any>)
    }
  }

  return changed
}

function a91545SectionChanged(
  previousContent: Record<string, any> | null,
  nextContent: Record<string, any>,
  sectionKey: string,
): boolean {
  return a91545StableJson(previousContent?.[sectionKey]) !== a91545StableJson(nextContent?.[sectionKey])
}

function a91545EditedSectionKey(content: Record<string, any>): string {
  const sectionEdit = a91522SafeRecord(content._prd_section_edit)
  const manualEdit = a91522SafeRecord(content._manual_document_edit)
  return a91522String(
    sectionEdit.sectionKey ||
      sectionEdit.section_key ||
      manualEdit.sectionKey ||
      manualEdit.section_key,
  )
}

function a91545IsDocumentOnlyPrdEdit(content: Record<string, any>): boolean {
  const sectionEdit = a91522SafeRecord(content._prd_section_edit)
  const sectionKey = a91522String(sectionEdit.sectionKey || sectionEdit.section_key)
  const editorMode = a91522String(sectionEdit.editorMode || sectionEdit.editor_mode)
  const impactPolicy = a91522String(sectionEdit.impactPolicy || sectionEdit.impact_policy).toUpperCase()

  return impactPolicy === 'DOCUMENT_ONLY' ||
    sectionKey === 'custom_sections' ||
    sectionKey.startsWith('custom_section_') ||
    editorMode.startsWith('CUSTOM_SECTION')
}

function a91545BuildImpactResult(
  sectionKey: string,
  sectionLabel: string,
  documentOnly: boolean,
  affectedIds: Set<string>,
  pages: ModuleTreeNode[],
  confidence: A91545PrdImpactResult['confidence'],
  reason: string,
): A91545PrdImpactResult {
  const ids = Array.from(affectedIds)
  const names = ids
    .map((id) => pages.find((page) => String(page.id || '') === id))
    .filter((page): page is ModuleTreeNode => Boolean(page))
    .map((page) => getModuleDisplayName(page))
    .filter(Boolean)

  return {
    sectionKey,
    sectionLabel,
    documentOnly,
    affectedPageIds: ids,
    affectedPageNames: Array.from(new Set(names)),
    confidence,
    reason,
  }
}

function a91545SectionLabel(sectionKey: string): string {
  const labels: Record<string, string> = {
    module_basic_info: '模块定位',
    business_background: '业务背景',
    user_roles: '用户角色',
    functional_requirements: '功能需求',
    business_rules: '业务规则',
    state_rules: '状态与流转',
    page_requirements: '页面与操作设计',
    data_requirements: '数据需求',
    api_requirements: '接口需求',
    permission_requirements: '权限要求',
    exception_scenarios: '异常场景',
    acceptance_criteria: '验收标准',
    non_functional_requirements: '非功能需求',
    custom_sections: '补充说明',
  }
  return labels[sectionKey] || sectionKey || '模块需求'
}

function a91545ArrayRecords(value: unknown): Record<string, any>[] {
  return Array.isArray(value)
    ? value
        .map((item) => a91522Record(item) ? item as Record<string, any> : null)
        .filter((item): item is Record<string, any> => Boolean(item))
    : []
}

function a91545RecordIdentitySignals(record: Record<string, any>): string[] {
  const values = [
    record.requirement_key,
    record.requirementKey,
    record.feature_key,
    record.featureKey,
    record.rule_key,
    record.ruleKey,
    record.state_key,
    record.stateKey,
    record.entity_key,
    record.entityKey,
    record.api_key,
    record.apiKey,
    record.criterion_key,
    record.criterionKey,
    record.title,
    record.name,
    record.requirement_name,
    record.requirementName,
    record.feature_name,
    record.featureName,
  ]
    .map(a91522NormalizeKey)
    .filter(Boolean)

  return Array.from(new Set(values))
}

function a91545FeatureIdentitySignals(feature: ModuleTreeNode): string[] {
  const extra = a91522SafeRecord(feature.extra)
  const functionContext = a91522SafeRecord(extra.functionContext || extra.function_context)
  const values = [
    feature.moduleKey,
    feature.key,
    getModuleDisplayName(feature),
    extra.requirementKey,
    extra.requirement_key,
    extra.featureKey,
    extra.feature_key,
    extra.prdRequirementKey,
    functionContext.requirementKey,
    functionContext.requirement_key,
    functionContext.featureKey,
    functionContext.feature_key,
    functionContext.title,
    functionContext.name,
    functionContext.featureName,
    functionContext.feature_name,
  ]
    .map(a91522NormalizeKey)
    .filter(Boolean)

  return Array.from(new Set(values))
}

function a91545PageIdentityAliases(page: ModuleTreeNode): string[] {
  const extra = a91522SafeRecord(page.extra)
  const values = [
    page.id,
    page.moduleKey,
    page.key,
    getModuleDisplayName(page),
    page.routePath,
    extra.pageKey,
    extra.page_key,
    extra.prdPageRequirementKey,
    extra.prdPageRequirementName,
    extra.routePath,
    extra.route_path,
    extra.route,
  ]
    .map(a91522NormalizeKey)
    .filter(Boolean)
  return Array.from(new Set(values))
}

function a91545StableJson(value: unknown): string {
  if (value === undefined || value === null) return ''
  try {
    return JSON.stringify(a91545NormalizeForCompare(value))
  } catch {
    return String(value || '')
  }
}

function a91545NormalizeForCompare(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(a91545NormalizeForCompare)
  }

  if (a91522Record(value)) {
    const result: Record<string, unknown> = {}
    for (const key of Object.keys(value).sort()) {
      if (key.startsWith('_manual_document_edit') || key === '_prd_section_edit') continue
      if (['editedAt', 'updatedAt', 'prdSyncedAt'].includes(key)) continue
      result[key] = a91545NormalizeForCompare((value as Record<string, unknown>)[key])
    }
    return result
  }

  return value
}

function a91545CloneRecord(value: Record<string, any>): Record<string, any> {
  try {
    return JSON.parse(JSON.stringify(value)) as Record<string, any>
  } catch {
    return { ...value }
  }
}

function a91522ResolveSavedPrdContent(
  payload: ManualArtifactEditPayload,
  response: Record<string, any>,
): Record<string, any> | null {
  const payloadContent = (payload as any).content
  if (a91522Record(payloadContent)) {
    return payloadContent as Record<string, any>
  }

  const responseContent = response?.content
  if (a91522Record(responseContent)) {
    return responseContent as Record<string, any>
  }

  return null
}

function a91522NormalizePageRequirements(value: unknown): A91522PageRequirementRecord[] {
  return Array.isArray(value)
    ? value
        .map((item) => a91522Record(item) ? item as A91522PageRequirementRecord : null)
        .filter((item): item is A91522PageRequirementRecord => Boolean(item))
        .filter((item) => !a91522Bool(item.archived) && !a91522Bool(item.deleted) && !a91522Bool(item._deleted))
    : []
}

function a91522CollectOwnedPageNodes(owner: ModuleTreeNode): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []

  const visit = (node: ModuleTreeNode) => {
    for (const child of node.children || []) {
      if (resolveRequirementNodeType(child) === 'PAGE') {
        result.push(child)
      }
      visit(child)
    }
  }

  visit(owner)
  return result
}

function a91522FindMatchingPageNode(
  requirement: A91522PageRequirementRecord,
  pages: ModuleTreeNode[],
  usedPageIds: Set<string>,
): ModuleTreeNode | null {
  const requirementKeys = a91522PageRequirementIdentityKeys(requirement)
  let best: { page: ModuleTreeNode; score: number } | null = null

  for (const page of pages) {
    if (page.id && usedPageIds.has(String(page.id))) continue

    const pageKeys = a91522PageNodeIdentityKeys(page)
    let score = 0
    for (const key of requirementKeys) {
      if (pageKeys.includes(key)) {
        score += key.startsWith('id:') ? 100 : key.startsWith('route:') ? 60 : 40
      }
    }

    if (!best || score > best.score) {
      best = { page, score }
    }
  }

  if (!best || best.score <= 0) {
    return null
  }

  if (best.page.id) {
    usedPageIds.add(String(best.page.id))
  }

  return best.page
}

function a91522PageRequirementIdentityKeys(requirement: A91522PageRequirementRecord): string[] {
  const values: Array<[string, unknown]> = [
    ['id', requirement.node_id || requirement.nodeId || requirement.page_node_id || requirement.pageNodeId],
    ['key', requirement.page_key || requirement.pageKey || requirement.key],
    ['name', requirement.page_name || requirement.pageName || requirement.name || requirement.title],
    ['route', requirement.route_path || requirement.routePath || requirement.route],
  ]

  return values
    .map(([prefix, value]) => {
      const normalized = a91522NormalizeKey(value)
      return normalized ? `${prefix}:${normalized}` : ''
    })
    .filter(Boolean)
}

function a91522PageNodeIdentityKeys(page: ModuleTreeNode): string[] {
  const extra = a91522SafeRecord(page.extra)
  const values: Array<[string, unknown]> = [
    ['id', page.id],
    ['key', page.moduleKey || page.key || extra.pageKey || extra.page_key || extra.prdPageRequirementKey],
    ['name', getModuleDisplayName(page)],
    ['route', page.routePath || extra.routePath || extra.route_path || extra.route],
  ]

  return values
    .map(([prefix, value]) => {
      const normalized = a91522NormalizeKey(value)
      return normalized ? `${prefix}:${normalized}` : ''
    })
    .filter(Boolean)
}

function a91522ResolveParentIdForPageRequirement(
  requirement: A91522PageRequirementRecord,
  owner: ModuleTreeNode,
): string | null {
  const explicitParentId = a91522String(requirement.parent_node_id || requirement.parentNodeId || requirement.parent_id || requirement.parentId)
  if (explicitParentId && findA9ModuleTreeNodeById([owner], explicitParentId)) {
    return explicitParentId
  }

  const featureKey = a91522NormalizeKey(requirement.feature_key || requirement.featureKey || requirement.requirement_key || requirement.requirementKey)
  const featureName = a91522NormalizeKey(requirement.feature_name || requirement.featureName || requirement.feature)
  if (featureKey || featureName) {
    const features = a91522CollectDescendantNodesByType(owner, 'FEATURE')
    const matchedFeature = features.find((node) => {
      const keys = [
        a91522NormalizeKey(node.moduleKey || node.key),
        a91522NormalizeKey(getModuleDisplayName(node)),
      ]
      return Boolean((featureKey && keys.includes(featureKey)) || (featureName && keys.includes(featureName)))
    })
    if (matchedFeature?.id) {
      return matchedFeature.id
    }
  }

  return owner.id || null
}

function a91522CollectDescendantNodesByType(owner: ModuleTreeNode, type: string): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []
  const visit = (node: ModuleTreeNode) => {
    for (const child of node.children || []) {
      if (resolveRequirementNodeType(child) === type) {
        result.push(child)
      }
      visit(child)
    }
  }
  visit(owner)
  return result
}

function a91522ShouldUpdatePageNode(
  page: ModuleTreeNode,
  requirement: A91522PageRequirementRecord,
  nextName: string,
  nextKey: string,
  nextExtra: Record<string, unknown>,
): boolean {
  const currentExtra = a91522SafeRecord(page.extra)
  const currentRoute = a91522String(page.routePath || currentExtra.routePath || currentExtra.route_path || currentExtra.route)
  const nextRoute = a91522String(nextExtra.routePath || nextExtra.route_path || nextExtra.route)
  const currentPageType = a91522String(currentExtra.pageType || currentExtra.page_type || page.pageKind || page.layoutType).toUpperCase()
  const nextPageType = a91522String(nextExtra.pageType || nextExtra.page_type).toUpperCase()

  return Boolean(
    getModuleDisplayName(page) !== nextName ||
      a91522String(page.moduleKey || page.key) !== nextKey ||
      currentRoute !== nextRoute ||
      currentPageType !== nextPageType ||
      a91522String(currentExtra.prdPageRequirementKey) !== a91522PageRequirementKey(requirement)
  )
}

function a91522BuildPageNodeExtra(
  requirement: A91522PageRequirementRecord,
  currentPage: ModuleTreeNode | null,
): Record<string, unknown> {
  const currentExtra = a91522SafeRecord(currentPage?.extra)
  const pageType = a91522PageRequirementType(requirement)
  const routePath = a91522String(requirement.route_path || requirement.routePath || requirement.route)
  const pageKey = a91522PageRequirementKey(requirement)

  return {
    ...currentExtra,
    nodeType: 'PAGE',
    node_type: 'PAGE',
    pageType,
    page_type: pageType,
    pageKind: pageType,
    page_kind: pageType,
    layoutType: a91522String(requirement.layout_type || requirement.layoutType) || a9152LayoutTypeForPageType(pageType),
    layout_type: a91522String(requirement.layout_type || requirement.layoutType) || a9152LayoutTypeForPageType(pageType),
    routePath,
    route_path: routePath,
    route: routePath,
    primaryEntity: a91522String(requirement.primary_entity || requirement.primaryEntity),
    primary_entity: a91522String(requirement.primary_entity || requirement.primaryEntity),
    prdPageRequirementKey: pageKey,
    prdPageRequirementName: a91522PageRequirementName(requirement),
    prdSyncSource: 'MODULE_PRD_PAGE_REQUIREMENTS',
    prdSyncedAt: new Date().toISOString(),
  }
}

function a91522PageRequirementName(requirement: A91522PageRequirementRecord): string {
  return a91522String(requirement.page_name || requirement.pageName || requirement.name || requirement.title || requirement.label)
}

function a91522PageRequirementKey(requirement: A91522PageRequirementRecord): string {
  const explicit = a91522String(requirement.page_key || requirement.pageKey || requirement.key)
  return explicit || a91522Slugify(a91522PageRequirementName(requirement))
}

function a91522PageRequirementType(requirement: A91522PageRequirementRecord): string {
  const raw = a91522String(requirement.page_type || requirement.pageType || requirement.layout_type || requirement.layoutType).toUpperCase()
  const name = `${a91522PageRequirementName(requirement)} ${a91522PageRequirementKey(requirement)} ${raw}`

  if (/APPROVAL|AUDIT|审核|审批/.test(name)) return 'APPROVAL'
  if (/DETAIL|VIEW|详情|查看/.test(name)) return 'DETAIL'
  if (/LIST|SEARCH|INDEX|列表|查询|管理/.test(name)) return 'LIST'
  if (/FORM|CREATE|NEW|ADD|EDIT|UPDATE|创建|新建|新增|录入|编辑|修改|数量/.test(name)) return 'FORM'
  return raw || 'FORM'
}

function a91522SafeRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : {}
}

function a91522Record(value: unknown): value is Record<string, any> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function a91522String(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value || '').trim()
}

function a91522Bool(value: unknown): boolean {
  return value === true || a91522String(value).toLowerCase() === 'true' || a91522String(value) === '1'
}

function a91522NormalizeKey(value: unknown): string {
  return a91522String(value)
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-:/\\]+/g, '')
}

function a91522Slugify(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/gi, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 64)
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

  if (prototypeEditDraftDirty.value || uiContractDraftContent.value) {
    const confirmed = window.confirm(
      '当前页面存在未保存的原型草稿。\n\n重新生成交互原型会丢弃这些本地调整。确认要放弃草稿并重新生成吗？',
    )

    if (!confirmed) {
      artifactTaskFeedback.value = {
        level: 'info',
        message: '已取消重新生成，当前本地原型草稿仍保留。',
      }
      return
    }

    resetPrototypeLocalEditDraft()
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


// A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION
function isPrdTreeCompletionRecord(
  value: unknown,
): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function buildModulePrdTreeCompletionPayload(): Record<string, unknown> {
  const artifact = prdArtifact.value as
    | {
        content?: unknown
        artifactId?: string | null
        versionNo?: number | string | null
      }
    | null
    | undefined

  const content = isPrdTreeCompletionRecord(artifact?.content)
    ? { ...artifact.content }
    : {}

  if (modulePrdMarkdown.value.trim()) {
    content.markdown = modulePrdMarkdown.value
  }

  return {
    modulePrdContent: content,
    sourceArtifactId: artifact?.artifactId || null,
    sourceVersionNo: artifact?.versionNo || null,
  }
}

async function handleCompleteModuleTreeFromPrd() {
  const module = selectedModule.value

  if (!module?.id) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择一个已生成模块需求的模块。',
    }
    return
  }

  const payload = buildModulePrdTreeCompletionPayload()
  const modulePrdContent = payload.modulePrdContent

  if (
    !isPrdTreeCompletionRecord(modulePrdContent) ||
    Object.keys(modulePrdContent).length === 0
  ) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前模块还没有可用于生成业务能力和页面的 MODULE_PRD 内容。',
    }
    return
  }

  const confirmed = window.confirm(
    '将根据当前 MODULE_PRD 生成该模块下的业务能力和页面入口。请确认你已经检查或保存过模块需求内容。',
  )

  if (!confirmed) {
    return
  }

  artifactTaskFeedback.value = {
    level: 'info',
    message: '正在根据模块需求生成业务能力和页面入口...',
  }

  try {
    const response = await completeModuleTreeFromModulePrd(
      props.workspace.workspaceId,
      props.project.projectCode,
      module.id,
      payload as {
        modulePrdContent: Record<string, unknown>
        sourceArtifactId?: string | null
        sourceVersionNo?: number | string | null
      },
    )

    modules.value = Array.isArray(response.tree) && response.tree.length > 0
      ? response.tree
      : await fetchProjectModules(
          props.workspace.workspaceId,
          props.project.projectCode,
        )

    const refreshedNode = restoreSelectedModuleById(
      modules.value,
      module.id,
    )

    if (refreshedNode) {
      await handleSelectModule(refreshedNode)
    }

    await Promise.allSettled([
      loadCurrentModuleAgentTasks(),
      refreshedNode ? loadAllCurrentModuleArtifacts(refreshedNode) : Promise.resolve(),
    ])

    const createdFeatureCount = Number(response.createdFeatureCount || 0)
    const createdPageCount = Number(response.createdPageCount || 0)
    const updatedFeatureCount = Number(response.updatedFeatureCount || 0)
    const updatedPageCount = Number(response.updatedPageCount || 0)

    if (response.skipped) {
  artifactTaskFeedback.value = {
    level: 'info',
    message: response.reason
      ? `未生成业务能力和页面：${response.reason}`
      : '当前模块暂不满足从 MODULE_PRD 生成业务能力和页面的条件。',
  }
  return
}

    artifactTaskFeedback.value = {
      level: 'success',
      message:
        `已根据模块需求生成业务能力和页面：新增业务能力 ${createdFeatureCount} 个、页面 ${createdPageCount} 个；更新业务能力 ${updatedFeatureCount} 个、页面 ${updatedPageCount} 个。`,
    }
  } catch (error) {
    console.error(error)
    artifactTaskFeedback.value = {
      level: 'error',
      message:
        error instanceof Error
          ? error.message
          : '根据模块需求生成业务能力和页面失败，请稍后重试。',
    }
  }
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

  if (isPageNode(module) && uiContractArtifact.value?.artifactId && !isCurrentUiContractPublished.value) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前页面的交互原型尚未发布。请先在 Prototype Studio 中保存并发布 UI_CONTRACT，再生成开发交接包。',
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
  uiContractPreviewVisible.value = false
}

async function handlePublishCurrentPrototype() {
  if (!selectedModule.value?.id || !isPageNode(selectedModule.value)) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '请先选择具体 PAGE 页面节点，再发布交互原型。',
    }
    return
  }

  if (prototypeEditDraftDirty.value || uiContractDraftContent.value) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前存在未保存的原型草稿。请先保存为 UI_CONTRACT 新版本，再发布。',
    }
    return
  }

  if (!uiContractArtifact.value?.artifactId) {
    artifactTaskFeedback.value = {
      level: 'error',
      message: '当前页面还没有可发布的 UI_CONTRACT 版本。',
    }
    return
  }

  if (isCurrentUiContractPublished.value) {
    artifactTaskFeedback.value = {
      level: 'success',
      message: '当前 UI_CONTRACT 版本已经发布，可用于后续 Development Agent。',
    }
    return
  }

  const confirmed = window.confirm(
    '确认发布当前 UI_CONTRACT 版本吗？\n\n发布后，后续 Development Agent 将以该版本作为页面蓝图；如果之后继续编辑或重新生成，需要重新发布。',
  )

  if (!confirmed) {
    return
  }

  await handleConfirmUiContractSnapshot({
    confirmationNote: 'A9.10.3 Prototype Studio 发布当前 UI_CONTRACT 版本。',
  })
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


// A9.10.14.4.3.3_ASSISTANT_ONLY_PRD_IMPACT
// Keep the internal downstream asset state for gates and Dev Agent, but do not expose
// PAGE_SPEC / NEEDS_REVIEW / asset-chain operations to normal users. Surface a concise
// product-facing message in the right AI assistant panel instead.
function publishUserFacingPrdImpactNotice(
  artifactType: RequirementArtifactType,
) {
  const sourceTitle = impactAnalysisSourceTitle.value || buildUserFacingImpactSourceTitle(artifactType)
  const moduleName = selectedModule.value
    ? getModuleDisplayName(selectedModule.value)
    : '当前模块'

  latestAssistantResponse.value = {
    messageId: `prd_ui_impact_notice_${Date.now()}`,
    intent: 'PRD_UI_IMPACT_NOTICE',
    reply: buildUserFacingPrdImpactReply(artifactType, sourceTitle, moduleName),
    requiresUserConfirmation: false,
    proposal: null,
    requirementChangeProposal: null,
    actions: [],
  } as any

  aiAssistantCollapsed.value = false
}

function buildUserFacingImpactSourceTitle(
  artifactType: RequirementArtifactType,
): string {
  if (artifactType === 'MODULE_PRD') return '模块 PRD'
  if (artifactType === 'UI_CONTRACT') return '交互原型'
  return '需求内容'
}

function buildUserFacingPrdImpactReply(
  artifactType: RequirementArtifactType,
  sourceTitle: string,
  moduleName: string,
): string {
  const title = sourceTitle || buildUserFacingImpactSourceTitle(artifactType)

  if (artifactType === 'UI_CONTRACT') {
    return [
      `「${moduleName}」的交互原型已保存。`,
      '这次原型调整可能需要同步回 PRD 中的页面字段、按钮、跳转或状态规则描述。',
      '系统已经记录同步建议；当前不需要你处理内部资产。后续可以在 PRD 中核对这些变化，或继续调整原型。',
    ].join('\n\n')
  }

  if (artifactType === 'MODULE_PRD') {
    return [
      `「${moduleName}」的${title}已保存。`,
      '这次需求修改可能影响交互原型中的筛选条件、表格字段、操作按钮、页面跳转或状态规则。',
      '建议下一步进入受影响的页面，点击“重新生成交互原型”。系统会在后台处理必要的页面规范更新，不需要你手动处理 PAGE_SPEC、NEEDS_REVIEW 或其他内部资产。',
    ].join('\n\n')
  }

  return [
    `「${moduleName}」的${title}已保存。`,
    '系统已经记录本次需求变化，并会在后台维护相关内部资产状态。',
    '普通用户只需要继续检查交互原型是否符合预期；需要时点击“重新生成交互原型”。',
  ].join('\n\n')
}

function handleOpenImpactAnalysis(
  artifactType: RequirementArtifactType,
) {
  if (!selectedModule.value?.id) {
    return
  }

  // A9.10.14.4.3.3_ASSISTANT_ONLY_PRD_IMPACT: do not open the technical downstream-asset drawer for normal users.
  // Send product-facing impact guidance to the right AI assistant panel instead.
  impactAnalysisDrawerVisible.value = false
  impactAnalysisError.value = ''
  impactAnalysisSourceArtifactType.value = artifactType
  impactAnalysisSelectedTypes.value = []
  publishUserFacingPrdImpactNotice(artifactType)
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
        '已在右侧 AI Assistant 中生成说明，请按提示检查交互原型是否需要重新生成。',
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

      if (artifact.artifactType === 'UI_CONTRACT') {
        resetPrototypeLocalEditDraft()
        await Promise.allSettled([
          loadCurrentUiContractSnapshot(selectedModule.value),
          loadDevelopmentHandoffReadiness(selectedModule.value),
        ])
        artifactTaskFeedback.value = {
          level: 'info',
          message: `已回滚 UI_CONTRACT 到 V${artifact.versionNo || ''}。回滚后需要重新发布该版本，Development Agent 才会使用它。`,
        }
      }

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
// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: workspace handlers are retained for dormant drawers and menu branches.
void buildPrototypeSelectionLabel
void collectDevelopmentPreparationNodeIds
void handleToggleImpactedArtifact
void selectAllImpactedArtifacts
void handleSyncSelectedImpactedArtifact
void handleRegenerateImpactedArtifacts
void closeImpactAnalysisDrawer

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


/* A9.10.8.1 DEVELOPMENT WORKSPACE SCROLL SHELL FIX */
.app-shell.development-shell :deep(.development-workspace-panel),
.main-layout.with-workspace-topbar :deep(.development-workspace-panel) {
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
  box-sizing: border-box;
  padding: 14px 18px 24px;
}

.app-shell.development-shell :deep(.development-workspace-panel::-webkit-scrollbar),
.main-layout.with-workspace-topbar :deep(.development-workspace-panel::-webkit-scrollbar) {
  width: 10px;
}

.app-shell.development-shell :deep(.development-workspace-panel::-webkit-scrollbar-thumb),
.main-layout.with-workspace-topbar :deep(.development-workspace-panel::-webkit-scrollbar-thumb) {
  border: 3px solid rgba(248, 250, 252, 0.96);
  border-radius: 999px;
  background: #cbd5e1;
}

.app-shell.development-shell :deep(.development-workspace-panel::-webkit-scrollbar-track),
.main-layout.with-workspace-topbar :deep(.development-workspace-panel::-webkit-scrollbar-track) {
  background: transparent;
}
</style>


