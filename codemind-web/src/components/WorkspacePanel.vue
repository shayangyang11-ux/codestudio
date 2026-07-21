<template>
  <main class="workspace-panel">
    <section class="workspace-card">
      <div class="workspace-header">
        <div>
          <h2>需求 Agent 工作区</h2>
          <p>
            请选择左侧模块树节点。系统会按业务域、模块、子模块、页面、功能点的不同粒度，只展示当前层级最值得生成和查看的需求资产。
          </p>
        </div>
      </div>

      <div
        v-if="artifactTaskFeedback"
        class="artifact-task-feedback"
        :class="artifactTaskFeedback.level"
      >
        <strong>
          {{
            artifactTaskFeedback.level === 'error'
              ? '操作未完成'
              : artifactTaskFeedback.level === 'success'
                ? '操作已提交'
                : '提示'
          }}
        </strong>
        <span>{{ artifactTaskFeedback.message }}</span>
      </div>

      <section class="selected-module-card compact-module-card">
        <div class="selected-module-header compact">
          <div>
            <h3>当前选中节点</h3>
            <p v-if="selectedModule">
              {{ moduleName }}
              <span v-if="nodeTypeLabel"> / {{ nodeTypeLabel }}</span>
            </p>
            <p v-else>请从左侧模块树选择一个节点。</p>
          </div>

          <button
            class="secondary-button collapse-toggle-button"
            :disabled="!selectedModule"
            @click="selectedModulePanelExpanded = !selectedModulePanelExpanded"
          >
            {{ selectedModulePanelExpanded ? '收起资产面板' : '展开资产面板' }}
          </button>
        </div>

        <div v-if="selectedModule" class="compact-module-summary">
          <span>Key：{{ moduleKey || '-' }}</span>
          <span>{{ assetStatusSummary }}</span>
          <span>{{ readinessSummary }}</span>
        </div>

        <div v-if="selectedModulePanelExpanded" class="selected-module-expanded">
          <div class="node-generation-guidance">
            <div>
              <strong>{{ nodeTypeLabel }}级核心资产</strong>
              <p>{{ nodeGenerationHint }}</p>
            </div>

            <button
              class="full-chain-button"
              :disabled="!selectedModule || isGeneratingFullChain"
              @click="$emit('generate-full-chain')"
            >
              {{ isGeneratingFullChain ? '创建核心资产中...' : coreAssetsButtonText }}
            </button>
          </div>

          <ModuleArtifactTaskStatusPanel
            v-if="selectedModule"
            :items="coreArtifactTaskItems"
            @generate="$emit('generate-artifact-task', $event)"
            @cancel="$emit('cancel-artifact-task', $event)"
            @retry="$emit('retry-artifact-task', $event)"
            @view="$emit('view-artifact', $event as RequirementArtifactType)"
          />

          <details v-if="advancedArtifactTaskItems.length" class="advanced-asset-fold">
            <summary>
              <div>
                <strong>高级资产</strong>
                <p>用于正式交付、深度审查或进入开发 Agent 前补齐。默认不批量生成，避免 Token 浪费。</p>
              </div>
              <span>展开</span>
            </summary>

            <ModuleArtifactTaskStatusPanel
              :items="advancedArtifactTaskItems"
              @generate="$emit('generate-artifact-task', $event)"
              @cancel="$emit('cancel-artifact-task', $event)"
              @retry="$emit('retry-artifact-task', $event)"
              @view="$emit('view-artifact', $event as RequirementArtifactType)"
            />
          </details>

          <div v-if="selectedModule" class="module-meta">
            <span>ID：{{ selectedModule.id || '-' }}</span>
          </div>
        </div>

        <div v-if="!selectedModule" class="empty-hint">
          请从左侧模块树选择一个节点。选择后可展开资产面板，也可以直接查看已生成资产。
        </div>
      </section>

      <UiContractReadinessPanel
        v-if="nodeType === 'PAGE'"
        :readiness="uiContractReadiness"
        :loading="uiContractReadinessLoading"
        :error="uiContractReadinessError"
        :generating="isGeneratingUiContract"
        @refresh="$emit('refresh-ui-contract-readiness')"
        @generate="$emit('generate-ui-contract')"
      />

      <section v-if="selectedModule" class="artifact-view-switcher compact-asset-switcher">
        <details class="asset-status-fold" open>
          <summary>
            <div>
              <h4>资产导航与状态</h4>
              <p>{{ assetStatusSummary }}，当前查看：{{ selectedArtifactTitle }}</p>
            </div>

            <span class="fold-hint">展开</span>
          </summary>

          <div class="artifact-view-actions folded-actions">
            <strong>{{ selectedArtifactTitle }}</strong>

            <button
              class="secondary-button history-button"
              :disabled="!selectedArtifactType"
              @click="handleOpenArtifactVersions"
            >
              版本历史
            </button>

            <button
              class="secondary-button history-button"
              :disabled="!selectedArtifactType || !selectedArtifactHasDownstream"
              @click="handleOpenImpactAnalysis"
            >
              影响分析
            </button>

            <button
              class="secondary-button history-button"
              :disabled="!selectedModule"
              @click="$emit('open-quality-check')"
            >
              质量检查
            </button>

            <button
              class="secondary-button history-button"
              :disabled="!selectedModule"
              @click="$emit('export-artifacts-zip')"
            >
              导出交接包
            </button>

            <button
              class="secondary-button history-button"
              :disabled="!selectedModule"
              @click="$emit('export-artifacts-docx')"
            >
              导出 Word
            </button>
          </div>

          <div class="asset-status-section">
            <div class="asset-status-section-title">核心资产</div>
            <div class="asset-status-grid">
              <button
                v-for="item in coreAssetStatusItems"
                :key="item.artifactType"
                class="asset-status-item"
                :class="{ active: selectedArtifactType === item.artifactType }"
                :disabled="!item.exists"
                @click="$emit('view-artifact', item.artifactType)"
              >
                <span class="asset-status-dot" :class="item.statusClass" />
                <span class="asset-status-title">{{ item.title }}</span>
                <span class="asset-status-text">{{ item.statusText }}</span>
              </button>
            </div>
          </div>

          <details v-if="advancedAssetStatusItems.length" class="asset-status-section advanced-status-fold">
            <summary>
              <span>高级资产</span>
              <em>{{ advancedGeneratedCount }} / {{ advancedAssetStatusItems.length }}</em>
            </summary>

            <div class="asset-status-grid">
              <button
                v-for="item in advancedAssetStatusItems"
                :key="item.artifactType"
                class="asset-status-item advanced"
                :class="{ active: selectedArtifactType === item.artifactType }"
                :disabled="!item.exists"
                @click="$emit('view-artifact', item.artifactType)"
              >
                <span class="asset-status-dot" :class="item.statusClass" />
                <span class="asset-status-title">{{ item.title }}</span>
                <span class="asset-status-text">{{ item.statusText }}</span>
              </button>
            </div>
          </details>
        </details>
      </section>

      <section class="prd-card artifact-view-card">
        <div v-if="!selectedModule" class="empty-hint">
          请从左侧模块树选择一个节点。
        </div>

        <div v-else-if="!selectedArtifactType" class="empty-hint">
          当前节点暂无已选中的资产。请先生成核心资产，或在上方点击“查看”。
        </div>

        <ModulePrdView
          v-else-if="selectedArtifactType === 'MODULE_PRD' && (prdArtifact?.content || modulePrdMarkdown)"
          :artifact="prdArtifact"
          :markdown="modulePrdMarkdown"
          :manual-edit-saving="manualEditSaving"
          @save-manual-edit="$emit('save-manual-artifact-edit', $event)"
        />

        <BusinessFlowView
          v-else-if="selectedArtifactType === 'BUSINESS_FLOW' && (businessFlowArtifact?.content || businessFlowMarkdown)"
          :artifact="businessFlowArtifact"
          :markdown="businessFlowMarkdown"
          :manual-edit-saving="manualEditSaving"
          @save-manual-edit="$emit('save-manual-artifact-edit', $event)"
        />

        <PageSpecView
          v-else-if="selectedArtifactType === 'PAGE_SPEC' && (pageSpecArtifact?.content || pageSpecMarkdown)"
          :artifact="pageSpecArtifact"
          :markdown="pageSpecMarkdown"
          :manual-edit-saving="manualEditSaving"
          @save-manual-edit="$emit('save-manual-artifact-edit', $event)"
        />

        <DataModelView
          v-else-if="selectedArtifactType === 'DATA_MODEL' && (dataModelArtifact?.content || dataModelMarkdown)"
          :artifact="dataModelArtifact"
          :markdown="dataModelMarkdown"
        />

        <ApiContractView
          v-else-if="selectedArtifactType === 'API_CONTRACT' && (apiContractArtifact?.content || apiContractMarkdown)"
          :artifact="apiContractArtifact"
          :markdown="apiContractMarkdown"
        />

        <TestSpecView
          v-else-if="selectedArtifactType === 'TEST_SPEC' && (testSpecArtifact?.content || testSpecMarkdown)"
          :artifact="testSpecArtifact"
          :markdown="testSpecMarkdown"
          :manual-edit-saving="manualEditSaving"
          @save-manual-edit="$emit('save-manual-artifact-edit', $event)"
        />

        <div
          v-else-if="selectedArtifactType === 'UI_CONTRACT' && (uiContractArtifact?.content || uiContractMarkdown)"
          class="ui-contract-summary-card"
        >
          <div class="ui-contract-summary-header">
            <div>
              <p class="ui-contract-eyebrow">UI_CONTRACT</p>
              <h3>交互原型 / UI 契约</h3>
              <p>
                当前资产不是一次性 Mock 页面，而是需求阶段 Mock Preview 和后续开发 Agent 真实页面生成共同使用的页面蓝图。
              </p>
            </div>

            <button
              class="primary-button"
              :disabled="!uiContractArtifact?.content"
              @click="$emit('preview-ui-contract')"
            >
              预览界面
            </button>
          </div>

          <div class="ui-contract-summary-grid">
            <div>
              <strong>当前用途</strong>
              <span>Mock Preview Runtime 运行</span>
            </div>
            <div>
              <strong>后续用途</strong>
              <span>开发 Agent 真实页面生成依据</span>
            </div>
            <div>
              <strong>一致性约束</strong>
              <span>布局、字段、按钮、校验和交互流程不得擅自改变</span>
            </div>
          </div>

          <div v-if="uiContractMarkdown" class="markdown-preview">
            <pre>{{ uiContractMarkdown }}</pre>
          </div>
        </div>

        <DevHandoffPackageView
          v-else-if="selectedArtifactType === 'DEV_HANDOFF_PACKAGE' && (devHandoffPackageArtifact?.content || devHandoffPackageMarkdown)"
          :artifact="devHandoffPackageArtifact"
          :markdown="devHandoffPackageMarkdown"
        />

        <div v-else class="empty-hint">
          当前资产尚未生成。请先在上方生成对应层级的核心资产，或展开高级资产按需生成。
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  ArtifactResponse,
  ModuleArtifactTaskItem,
  ModuleTreeNode,
  UiContractGenerationReadinessResponse,
  ManualArtifactEditPayload,
} from '../types/codemind'
import { getModuleDisplayName, getModuleStableKey } from '../api'
import ApiContractView from './ApiContractView.vue'
import BusinessFlowView from './BusinessFlowView.vue'
import DataModelView from './DataModelView.vue'
import DevHandoffPackageView from './DevHandoffPackageView.vue'
import ModuleArtifactTaskStatusPanel from './ModuleArtifactTaskStatusPanel.vue'
import ModulePrdView from './ModulePrdView.vue'
import PageSpecView from './PageSpecView.vue'
import TestSpecView from './TestSpecView.vue'
import UiContractReadinessPanel from './UiContractReadinessPanel.vue'
import {
  isRequirementArtifactType,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'
import {
  getAdvancedRequirementArtifactTypesForNode,
  getCoreRequirementArtifactTypesForNode,
  getNodeAwareDownstreamArtifactTypes,
  getRequirementArtifactTitleForNode,
  getRequirementCoreAssetsButtonText,
  getRequirementNodeGenerationHint,
  getRequirementNodeTypeLabel,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'

const props = defineProps<{
  selectedModule: ModuleTreeNode | null
  artifactTaskItems: ModuleArtifactTaskItem[]
  selectedArtifactType: RequirementArtifactType | null
  uiContractReadiness: UiContractGenerationReadinessResponse | null
  uiContractReadinessLoading: boolean
  uiContractReadinessError: string
  artifactTaskFeedback: {
    level: 'info' | 'success' | 'error'
    message: string
  } | null
  manualEditSaving?: boolean

  isGeneratingPrd: boolean
  isGeneratingBusinessFlow: boolean
  isGeneratingPageSpec: boolean
  isGeneratingDataModel: boolean
  isGeneratingApiContract: boolean
  isGeneratingTestSpec: boolean
  isGeneratingDevHandoffPackage: boolean
  isGeneratingFullChain: boolean

  modulePrdMarkdown: string
  prdArtifact: ArtifactResponse | null

  businessFlowMarkdown: string
  businessFlowArtifact: ArtifactResponse | null

  pageSpecMarkdown: string
  pageSpecArtifact: ArtifactResponse | null

  dataModelMarkdown: string
  dataModelArtifact: ArtifactResponse | null

  apiContractMarkdown: string
  apiContractArtifact: ArtifactResponse | null

  testSpecMarkdown: string
  testSpecArtifact: ArtifactResponse | null

  devHandoffPackageMarkdown: string
  devHandoffPackageArtifact: ArtifactResponse | null

  isGeneratingUiContract: boolean

  uiContractMarkdown: string
  uiContractArtifact: ArtifactResponse | null
}>()

const emit = defineEmits<{
  'generate-module-prd': []
  'generate-business-flow': []
  'generate-page-spec': []
  'generate-data-model': []
  'generate-api-contract': []
  'generate-test-spec': []
  'generate-dev-handoff-package': []
  'generate-full-chain': []
  'generate-artifact-task': [taskType: string]
  'cancel-artifact-task': [taskId: string]
  'retry-artifact-task': [taskId: string]
  'view-artifact': [artifactType: RequirementArtifactType]
  'open-artifact-versions': [artifactType: RequirementArtifactType]
  'open-impact-analysis': [artifactType: RequirementArtifactType]
  'export-artifacts-zip': []
  'export-artifacts-docx': []
  'open-quality-check': []
  'generate-ui-contract': []
  'refresh-ui-contract-readiness': []
  'preview-ui-contract': []
  'save-manual-artifact-edit': [payload: ManualArtifactEditPayload]
}>()

const selectedModulePanelExpanded = ref(false)

const moduleName = computed(() => getModuleDisplayName(props.selectedModule))
const moduleKey = computed(() => getModuleStableKey(props.selectedModule))
const nodeType = computed(() => resolveRequirementNodeType(props.selectedModule))
const nodeTypeLabel = computed(() => getRequirementNodeTypeLabel(nodeType.value))
const nodeGenerationHint = computed(() => getRequirementNodeGenerationHint(props.selectedModule))
const coreAssetsButtonText = computed(() => getRequirementCoreAssetsButtonText(props.selectedModule))

const coreArtifactTypes = computed(() =>
  getCoreRequirementArtifactTypesForNode(props.selectedModule),
)

const advancedArtifactTypes = computed(() =>
  getAdvancedRequirementArtifactTypesForNode(props.selectedModule),
)

const coreArtifactTaskItems = computed<ModuleArtifactTaskItem[]>(() =>
  props.artifactTaskItems
    .filter((item) => isArtifactTaskItemInTypes(item, coreArtifactTypes.value))
    .map(enrichTaskItemTitle),
)

const advancedArtifactTaskItems = computed<ModuleArtifactTaskItem[]>(() =>
  props.artifactTaskItems
    .filter((item) => isArtifactTaskItemInTypes(item, advancedArtifactTypes.value))
    .map(enrichTaskItemTitle),
)

const coreAssetStatusItems = computed(() =>
  buildAssetStatusItems(coreArtifactTypes.value),
)

const advancedAssetStatusItems = computed(() =>
  buildAssetStatusItems(advancedArtifactTypes.value),
)

const allAssetStatusItems = computed(() => [
  ...coreAssetStatusItems.value,
  ...advancedAssetStatusItems.value,
])

const generatedCoreArtifactCount = computed(() =>
  coreAssetStatusItems.value.filter((item) => item.exists).length,
)

const generatedAdvancedArtifactCount = computed(() =>
  advancedAssetStatusItems.value.filter((item) => item.exists).length,
)

const advancedGeneratedCount = computed(() => generatedAdvancedArtifactCount.value)

const assetStatusSummary = computed(() => {
  if (!props.selectedModule) {
    return '未选择节点'
  }

  return `核心资产 ${generatedCoreArtifactCount.value} / ${coreAssetStatusItems.value.length}`
})

const readinessSummary = computed(() => {
  if (!props.selectedModule) {
    return '未选择节点'
  }

  if (generatedCoreArtifactCount.value === coreAssetStatusItems.value.length) {
    return '需求准备度：核心资产已完成'
  }

  return '需求准备度：待补齐核心资产'
})

const selectedArtifactTitle = computed(() => {
  return allAssetStatusItems.value.find(
    (item) => item.artifactType === props.selectedArtifactType,
  )?.title || '工程资产'
})

const selectedArtifactHasDownstream = computed(() => {
  if (!props.selectedArtifactType) {
    return false
  }

  return getNodeAwareDownstreamArtifactTypes(
    props.selectedArtifactType,
    props.selectedModule,
  ).length > 0
})

function isArtifactTaskItemInTypes(
  item: ModuleArtifactTaskItem,
  artifactTypes: RequirementArtifactType[],
): boolean {
  return isRequirementArtifactType(item.artifactType) &&
    artifactTypes.includes(item.artifactType)
}

function enrichTaskItemTitle(
  item: ModuleArtifactTaskItem,
): ModuleArtifactTaskItem {
  if (!isRequirementArtifactType(item.artifactType)) {
    return item
  }

  return {
    ...item,
    title: getRequirementArtifactTitleForNode(
      item.artifactType,
      props.selectedModule,
    ),
  }
}

function buildAssetStatusItems(
  artifactTypes: RequirementArtifactType[],
) {
  return artifactTypes.map((artifactType) => {
    const artifact = getArtifactByType(artifactType)
    const taskItem = props.artifactTaskItems.find(
      (task) => task.artifactType === artifactType,
    )
    const taskStatus = taskItem?.task?.status || null
    const isTaskBlocked = taskStatus === 'BLOCKED'
    const isTaskRunning = ['QUEUED', 'RUNNING', 'RETRYING'].includes(
      taskStatus || '',
    )
    const artifactStatus = artifact?.status || taskItem?.artifactStatus || null
    const isPendingReview = artifactStatus === 'PENDING_REVIEW'
    const isNeedsReview = artifactStatus === 'NEEDS_REVIEW'
    const isApproved = artifactStatus === 'APPROVED'
    const exists = Boolean(
      artifact?.content ||
      artifact?.contentMarkdown ||
      artifact?.artifactId ||
      artifact?.exists ||
      getArtifactMarkdownByType(artifactType),
    )

    return {
      artifactType,
      title: getRequirementArtifactTitleForNode(
        artifactType,
        props.selectedModule,
      ),
      exists,
      statusClass: isTaskBlocked
        ? 'blocked'
        : isTaskRunning
          ? 'running'
          : isPendingReview
            ? 'review'
            : isNeedsReview
              ? 'affected'
              : isApproved
                ? 'generated'
                : exists
                  ? 'generated'
                  : 'missing',
      statusText: isTaskBlocked
        ? '等待依赖'
        : isTaskRunning
          ? '生成中'
          : isPendingReview
            ? '新版本待确认'
            : isNeedsReview
              ? '受上游影响'
              : isApproved
                ? '已确认'
                : exists
                  ? '已生成'
                  : '未生成',
    }
  })
}

function getArtifactByType(
  artifactType: RequirementArtifactType,
): ArtifactResponse | null {
  if (artifactType === 'MODULE_PRD') return props.prdArtifact
  if (artifactType === 'BUSINESS_FLOW') return props.businessFlowArtifact
  if (artifactType === 'PAGE_SPEC') return props.pageSpecArtifact
  if (artifactType === 'DATA_MODEL') return props.dataModelArtifact
  if (artifactType === 'API_CONTRACT') return props.apiContractArtifact
  if (artifactType === 'TEST_SPEC') return props.testSpecArtifact
  if (artifactType === 'UI_CONTRACT') return props.uiContractArtifact
  if (artifactType === 'DEV_HANDOFF_PACKAGE') return props.devHandoffPackageArtifact
  return null
}

function getArtifactMarkdownByType(
  artifactType: RequirementArtifactType,
): string {
  if (artifactType === 'MODULE_PRD') return props.modulePrdMarkdown || ''
  if (artifactType === 'BUSINESS_FLOW') return props.businessFlowMarkdown || ''
  if (artifactType === 'PAGE_SPEC') return props.pageSpecMarkdown || ''
  if (artifactType === 'DATA_MODEL') return props.dataModelMarkdown || ''
  if (artifactType === 'API_CONTRACT') return props.apiContractMarkdown || ''
  if (artifactType === 'TEST_SPEC') return props.testSpecMarkdown || ''
  if (artifactType === 'UI_CONTRACT') return props.uiContractMarkdown || ''
  if (artifactType === 'DEV_HANDOFF_PACKAGE') return props.devHandoffPackageMarkdown || ''
  return ''
}

function handleOpenArtifactVersions() {
  if (!props.selectedArtifactType) {
    return
  }

  emit('open-artifact-versions', props.selectedArtifactType)
}

function handleOpenImpactAnalysis() {
  if (!props.selectedArtifactType) {
    return
  }

  emit('open-impact-analysis', props.selectedArtifactType)
}
</script>

<style scoped>
.workspace-panel {
  flex: 1;
  min-width: 0;
  padding: 22px;
  overflow-y: auto;
  background: #f8fafc;
}

.workspace-card {
  max-width: 100%;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.workspace-header h2 {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
}

.workspace-header p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.artifact-task-feedback {
  display: grid;
  gap: 5px;
  margin-bottom: 16px;
  border-radius: 14px;
  padding: 12px 14px;
  background: #eff6ff;
  color: #1e40af;
}

.artifact-task-feedback.success {
  background: #f0fdf4;
  color: #166534;
}

.artifact-task-feedback.error {
  background: #fef2f2;
  color: #991b1b;
}

.artifact-task-feedback strong {
  font-size: 13px;
}

.artifact-task-feedback span {
  font-size: 12px;
  line-height: 1.6;
}

.selected-module-card,
.prd-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 18px;
  background: #ffffff;
}

.selected-module-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.selected-module-header h3 {
  margin: 0 0 6px;
  color: #0f172a;
}

.selected-module-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.secondary-button,
.primary-button {
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}

.secondary-button {
  border: 1px solid #2563eb;
  background: #ffffff;
  color: #2563eb;
}

.secondary-button:hover:not(:disabled) {
  background: #eff6ff;
}

.primary-button,
.full-chain-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button:disabled,
.primary-button:disabled,
.full-chain-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-hint {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 18px;
  background: #f8fafc;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.compact-module-card {
  padding: 14px 18px;
}

.selected-module-header.compact {
  align-items: center;
}

.collapse-toggle-button {
  white-space: nowrap;
}

.compact-module-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.compact-module-summary span {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 9px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
}

.selected-module-expanded {
  display: grid;
  gap: 14px;
  margin-top: 14px;
  border-top: 1px solid #e2e8f0;
  padding-top: 14px;
}

.node-generation-guidance {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 12px;
  background: #eff6ff;
}

.node-generation-guidance strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.node-generation-guidance p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.full-chain-button {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 700;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18);
}

.advanced-asset-fold {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 0;
  background: #ffffff;
  overflow: hidden;
}

.advanced-asset-fold summary {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  cursor: pointer;
  list-style: none;
}

.advanced-asset-fold summary::-webkit-details-marker {
  display: none;
}

.advanced-asset-fold summary strong {
  color: #0f172a;
  font-size: 14px;
}

.advanced-asset-fold summary p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.advanced-asset-fold summary span {
  border-radius: 999px;
  padding: 4px 10px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.advanced-asset-fold[open] summary {
  border-bottom: 1px solid #e2e8f0;
}

.module-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.module-meta span {
  border-radius: 999px;
  padding: 4px 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
}

.artifact-view-switcher {
  margin-top: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px;
  background: #ffffff;
}

.compact-asset-switcher {
  padding: 0;
  overflow: hidden;
}

.asset-status-fold {
  display: block;
}

.asset-status-fold > summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  cursor: pointer;
  list-style: none;
}

.asset-status-fold > summary::-webkit-details-marker {
  display: none;
}

.asset-status-fold summary h4 {
  margin: 0 0 4px;
  font-size: 15px;
  color: #0f172a;
}

.asset-status-fold summary p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.fold-hint {
  border-radius: 999px;
  padding: 4px 10px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.asset-status-fold[open] .fold-hint {
  background: #f1f5f9;
  color: #475569;
  font-size: 0;
}

.asset-status-fold[open] .fold-hint::before {
  content: '收起';
  font-size: 12px;
}

.artifact-view-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.folded-actions {
  padding: 0 16px 12px;
  flex-wrap: wrap;
}

.history-button {
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
}

.asset-status-section {
  padding: 0 16px 16px;
}

.asset-status-section-title {
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.asset-status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 8px;
}

.asset-status-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 9px 10px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  text-align: left;
}

.asset-status-item.advanced {
  background: #fcfcfd;
}

.asset-status-item:hover:not(:disabled) {
  border-color: #2563eb;
  background: #eff6ff;
}

.asset-status-item.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.asset-status-item:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.asset-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #cbd5e1;
}

.asset-status-dot.generated {
  background: #22c55e;
}

.asset-status-dot.missing {
  background: #cbd5e1;
}

.asset-status-dot.review {
  background: #f59e0b;
}

.asset-status-dot.affected {
  background: #f97316;
}

.asset-status-dot.running {
  background: #2563eb;
}

.asset-status-dot.blocked {
  background: #f59e0b;
}

.asset-status-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 700;
}

.asset-status-text {
  font-size: 12px;
  color: #64748b;
}

.advanced-status-fold {
  margin: 0 16px 16px;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}

.advanced-status-fold summary {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  cursor: pointer;
  list-style: none;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.advanced-status-fold summary::-webkit-details-marker {
  display: none;
}

.advanced-status-fold summary em {
  font-style: normal;
  color: #64748b;
}

.advanced-status-fold .asset-status-grid {
  padding: 0 12px 12px;
}

.artifact-view-card {
  margin-top: 16px;
}

.ui-contract-summary-card {
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  padding: 18px;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
}

.ui-contract-summary-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.ui-contract-summary-header h3 {
  margin: 2px 0 8px;
  color: #0f172a;
}

.ui-contract-summary-header p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.ui-contract-eyebrow {
  margin: 0;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.ui-contract-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.ui-contract-summary-grid div {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.ui-contract-summary-grid strong,
.ui-contract-summary-grid span {
  display: block;
}

.ui-contract-summary-grid strong {
  color: #0f172a;
  font-size: 13px;
}

.ui-contract-summary-grid span {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.markdown-preview {
  margin-top: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  overflow: auto;
}

.markdown-preview pre {
  margin: 0;
  padding: 14px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}
</style>




