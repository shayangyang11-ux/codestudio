
<template>
  <main class="development-handoff-panel">
    <section class="handoff-card">
      <header class="handoff-header">
        <div>
          <p class="eyebrow">Development Agent Gate</p>
          <h2>开发 Agent 准备度</h2>
          <span>
            进入正式代码生成前，系统会检查正式需求资产、页面交互原型确认快照和开发上下文是否可用。
          </span>
        </div>

        <div class="header-actions">
          <button
            class="secondary-button"
            :disabled="!selectedModule || loading"
            @click="$emit('refresh')"
          >
            {{ loading ? '检查中...' : '刷新准备度' }}
          </button>

          <button
            class="secondary-button"
            :disabled="!selectedModule"
            @click="$emit('open-quality-check')"
          >
            查看资产质量
          </button>
        </div>
      </header>

      <div v-if="!selectedModule" class="empty-box">
        请先从左侧模块树选择一个业务域、模块、页面或功能点节点。
      </div>

      <div v-else-if="error" class="error-box">
        {{ error }}
      </div>

      <div v-else-if="loading && !readiness" class="empty-box">
        正在检查开发准备度...
      </div>

      <template v-else-if="readiness">
        <section class="summary-card" :class="readinessClass">
          <div class="score-circle">
            <strong>{{ readiness.score }}</strong>
            <span>/ 100</span>
          </div>

          <div class="summary-content">
            <div class="summary-title-row">
              <div>
                <h3>{{ readiness.readinessText }}</h3>
                <p>{{ readiness.readinessDescription }}</p>
              </div>

              <span class="node-badge">
                {{ readiness.nodeTypeLabel }}
              </span>
            </div>

            <div class="summary-grid">
              <div>
                <strong>{{ readiness.assets.length }}</strong>
                <span>检查资产</span>
              </div>
              <div>
                <strong>{{ readiness.blockers.length }}</strong>
                <span>阻断项</span>
              </div>
              <div>
                <strong>{{ readiness.warnings.length }}</strong>
                <span>警告项</span>
              </div>
              <div>
                <strong>{{ readiness.passes.length }}</strong>
                <span>通过项</span>
              </div>
            </div>
          </div>
        </section>

        <section class="node-info-card">
          <div>
            <strong>{{ readiness.moduleName || selectedModule.moduleName || '当前节点' }}</strong>
            <span>{{ readiness.moduleKey || selectedModule.moduleKey || selectedModule.id }}</span>
          </div>

          <p>
            当前门禁只判断“是否可以交给 Development Agent 使用”。它不会重新生成资产，也不会替代需求阶段的质量检查。
          </p>
        </section>        <section
          class="phase4-acceptance-section"
          :class="{ ready: phase4Acceptance?.readyForNextPhase }"
        >
          <div class="section-heading">
            <h3>Phase 4 验收清单</h3>
            <p>
              验收 UI_CONTRACT、用户确认、开发计划确认与交接包准备情况。该清单通过后，需求 Agent 的 Phase 4 可以进入 Git 提交与 Phase 5 开发。
            </p>
          </div>

          <div class="context-actions">
            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || phase4AcceptanceLoading"
              @click="loadPhase4Acceptance"
            >
              {{ phase4AcceptanceLoading ? '验收中...' : '刷新 Phase 4 验收' }}
            </button>
          </div>

          <div v-if="phase4AcceptanceError" class="error-box">
            {{ phase4AcceptanceError }}
          </div>

          <div v-if="phase4Acceptance" class="phase4-summary-grid">
            <div>
              <strong>{{ phase4Acceptance.completionPercent }}%</strong>
              <span>完成度</span>
            </div>
            <div>
              <strong>{{ phase4Acceptance.completionLevel }}</strong>
              <span>验收状态</span>
            </div>
            <div>
              <strong>{{ phase4Acceptance.readyForNextPhase ? '可以' : '暂不能' }}</strong>
              <span>进入 Phase 5</span>
            </div>
            <div>
              <strong>{{ formatTime(phase4Acceptance.generatedAt) }}</strong>
              <span>检查时间</span>
            </div>
          </div>

          <p
            v-if="phase4Acceptance"
            class="phase4-summary-text"
          >
            {{ phase4Acceptance.summaryText }}
          </p>

          <div
            v-if="phase4Acceptance"
            class="phase4-item-grid"
          >
            <article
              v-for="item in phase4Acceptance.items"
              :key="item.id"
              class="phase4-item-card"
              :class="item.status.toLowerCase()"
            >
              <header>
                <strong>{{ item.title }}</strong>
                <span>{{ item.required ? '必需' : '可选' }}</span>
              </header>
              <p>{{ item.description }}</p>
              <footer>{{ item.statusText }}</footer>
            </article>
          </div>

          <div
            v-if="phase4Acceptance?.blockers?.length"
            class="phase4-blocker-list"
          >
            <strong>剩余阻断项</strong>
            <p
              v-for="blocker in phase4Acceptance.blockers"
              :key="blocker"
            >
              {{ blocker }}
            </p>
          </div>

          <div
            v-if="phase4Acceptance?.nextPhases?.length"
            class="package-section-list"
          >
            <span
              v-for="phase in phase4Acceptance.nextPhases"
              :key="phase"
            >
              {{ phase }}
            </span>
          </div>
        </section>



        <section
          v-if="readiness.nodeType === 'PAGE'"
          class="snapshot-card"
          :class="{ ready: readiness.uiContractSnapshot?.exists && readiness.uiContractSnapshot?.matchesCurrentArtifact }"
        >
          <div>
            <p class="eyebrow">UI_CONTRACT Confirmation</p>
            <h3>页面交互原型确认快照</h3>
            <span>
              {{ snapshotStatusText }}
            </span>
          </div>

          <button
            class="secondary-button"
            :disabled="!canPreviewUiContract"
            @click="$emit('preview-ui-contract')"
          >
            打开交互预览
          </button>
        </section>

        <section class="asset-section">
          <div class="section-heading">
            <h3>开发输入资产</h3>
            <p>必要资产缺失或不可用会阻断正式开发；建议资产缺失只形成警告。</p>
          </div>

          <div class="asset-grid">
            <article
              v-for="asset in readiness.assets"
              :key="asset.artifactType"
              class="asset-card"
              :class="assetLevelClass(asset)"
            >
              <header>
                <strong>{{ asset.title }}</strong>
                <span :class="{ required: asset.required }">
                  {{ asset.required ? '必要' : '建议' }}
                </span>
              </header>

              <p>{{ asset.statusText }}</p>

              <footer>
                <small>{{ asset.exists ? `v${asset.versionNo || '-'}` : '未生成' }}</small>
                <small>{{ formatTime(asset.updatedAt) }}</small>
              </footer>
            </article>
          </div>
        </section>

        <section class="issue-section">
          <div class="section-heading">
            <h3>阻断项</h3>
            <p>存在阻断项时，不允许正式 Development Agent 代码生成。</p>
          </div>

          <div v-if="readiness.blockers.length === 0" class="empty-mini">
            当前没有阻断项。
          </div>

          <article
            v-for="issue in readiness.blockers"
            :key="issue.id"
            class="issue-card blocker"
          >
            <strong>{{ issue.title }}</strong>
            <p>{{ issue.description }}</p>
          </article>
        </section>

        <section class="issue-section">
          <div class="section-heading">
            <h3>警告项</h3>
            <p>警告项不阻断预开发，但正式交付前建议处理。</p>
          </div>

          <div v-if="readiness.warnings.length === 0" class="empty-mini">
            当前没有警告项。
          </div>

          <article
            v-for="issue in readiness.warnings"
            :key="issue.id"
            class="issue-card warning"
          >
            <strong>{{ issue.title }}</strong>
            <p>{{ issue.description }}</p>
          </article>
        </section>

        <section class="context-section">
          <div class="section-heading">
            <h3>开发上下文预览</h3>
            <p>展示 Development Agent 将读取的资产、确认后的 UI_CONTRACT 和主题快照。</p>
          </div>

          <div class="context-actions">
            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || contextLoading"
              @click="handleLoadContext"
            >
              {{ contextLoading ? '读取中...' : '查看开发上下文' }}
            </button>

            <button
              class="primary-button"
              :disabled="!canGeneratePlan || planLoading"
              @click="handleGeneratePlan"
            >
              {{ planLoading ? '生成中...' : '生成开发实施计划' }}
            </button>
          </div>

          <div v-if="!canUseDevelopmentApi" class="empty-mini">
            当前开发面板缺少 workspaceId 或 projectCode。请确认 ProjectWorkspacePage.vue 已向 DevelopmentHandoffGatePanel 传入 workspace-id 和 project-code。
          </div>

          <div v-if="contextError" class="error-box">
            {{ contextError }}
          </div>

          <div v-if="planError" class="error-box">
            {{ planError }}
          </div>

          <div v-if="handoffContext" class="context-grid">
            <article
              v-for="artifact in handoffContext.artifacts"
              :key="artifact.artifactType"
              class="context-asset-card"
              :class="{ required: artifact.required }"
            >
              <strong>{{ artifact.title }}</strong>
              <span>{{ artifact.required ? '必要资产' : '建议资产' }}</span>
              <p>{{ artifact.status || '-' }} / v{{ artifact.versionNo || '-' }}</p>
            </article>
          </div>

          <div
            v-if="handoffContext?.confirmedUiContract"
            class="context-json-card"
          >
            <strong>已确认 UI_CONTRACT</strong>
            <p>已纳入开发上下文，后续真实页面代码应保持布局、字段、动作、状态与确认快照一致。</p>
          </div>

          <div
            v-if="handoffContext?.themeProfileSnapshot"
            class="context-json-card"
          >
            <strong>主题风格快照</strong>
            <p>已纳入开发上下文，后续真实页面代码应沿用确认时的项目级风格方向。</p>
          </div>
        </section>

        <section
          v-if="developmentPlan || planConfirmation"
          class="plan-confirm-section"
          :class="{ confirmed: isCurrentPlanConfirmed, stale: planConfirmation && !isCurrentPlanConfirmed }"
        >
          <div class="section-heading">
            <h3>开发实施计划确认</h3>
            <p>{{ planConfirmationStatusText }}</p>
          </div>

          <div class="plan-confirm-meta">
            <span>当前计划校验值：{{ developmentPlan?.planChecksum || '-' }}</span>
            <span>已确认校验值：{{ planConfirmation?.planChecksum || '-' }}</span>
            <span>确认时间：{{ formatTime(planConfirmation?.confirmedAt) }}</span>
          </div>

          <div class="context-actions">
            <button
              class="primary-button"
              :disabled="!developmentPlan || confirmingPlan || isCurrentPlanConfirmed"
              @click="handleConfirmPlan"
            >
              {{ confirmingPlan ? '确认中...' : isCurrentPlanConfirmed ? '当前计划已确认' : '确认开发实施计划' }}
            </button>

            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || planConfirmationLoading"
              @click="loadCurrentPlanConfirmation"
            >
              {{ planConfirmationLoading ? '读取中...' : '刷新确认状态' }}
            </button>

            <button
              class="primary-button"
              :disabled="!canGenerateHandoffPackage || handoffPackageLoading"
              @click="handleGenerateHandoffPackage"
            >
              {{ handoffPackageLoading ? '生成中...' : '生成开发交接包' }}
            </button>
          </div>

          <div v-if="planConfirmationError" class="error-box">
            {{ planConfirmationError }}
          </div>

          <div v-if="handoffPackageError" class="error-box">
            {{ handoffPackageError }}
          </div>
        </section>

        <section
          v-if="handoffPackage"
          class="handoff-package-section"
        >
          <div class="section-heading">
            <h3>Development Agent 交接包</h3>
            <p>
              已基于确认后的 Development Plan 生成交接包，后续真实代码生成应读取该包，而不是重新临时推理。
            </p>
          </div>

          <div class="package-summary-grid">
            <div>
              <strong>{{ handoffPackage.packageStatus }}</strong>
              <span>交接包状态</span>
            </div>
            <div>
              <strong>{{ handoffPackage.assets.length }}</strong>
              <span>资产版本</span>
            </div>
            <div>
              <strong>{{ handoffPackage.manifest.includedSections.length }}</strong>
              <span>包内分区</span>
            </div>
            <div>
              <strong>{{ formatTime(handoffPackage.generatedAt) }}</strong>
              <span>生成时间</span>
            </div>
          </div>

          <article class="context-json-card">
            <strong>交接包校验值</strong>
            <p>{{ handoffPackage.packageChecksum }}</p>
          </article>

          <article class="context-json-card">
            <strong>已确认计划校验值</strong>
            <p>{{ handoffPackage.manifest.developmentPlanChecksum }}</p>
          </article>

          <div class="package-section-list">
            <span
              v-for="section in handoffPackage.manifest.includedSections"
              :key="section"
            >
              {{ section }}
            </span>
          </div>

          <article class="plan-card">
            <h4>纳入交接包的资产版本</h4>
            <div
              v-for="asset in handoffPackage.assets"
              :key="`${asset.artifactType}-${asset.artifactId || '-'}`"
              class="plan-item"
            >
              <strong>{{ asset.artifactType }} / v{{ asset.versionNo || '-' }}</strong>
              <p>{{ asset.title || '-' }}</p>
              <span>{{ asset.status || '-' }}</span>
            </div>
          </article>
        </section>


        <section
          v-if="developmentPlan"
          class="plan-section"
        >
          <div class="section-heading">
            <h3>开发实施计划</h3>
            <p>{{ developmentPlan.summary }}</p>
          </div>

          <div class="plan-principles">
            <span
              v-for="principle in developmentPlan.principles"
              :key="principle"
            >
              {{ principle }}
            </span>
          </div>

          <div class="plan-columns">
            <article class="plan-card">
              <h4>前端文件</h4>
              <div
                v-for="file in developmentPlan.frontendFiles"
                :key="file.filePath"
                class="plan-item"
              >
                <strong>{{ file.filePath }}</strong>
                <p>{{ file.description }}</p>
                <span>{{ file.action }} / {{ file.source }}</span>
              </div>
            </article>

            <article class="plan-card">
              <h4>后端文件</h4>
              <div
                v-for="file in developmentPlan.backendFiles"
                :key="file.filePath"
                class="plan-item"
              >
                <strong>{{ file.filePath }}</strong>
                <p>{{ file.description }}</p>
                <span>{{ file.action }} / {{ file.source }}</span>
              </div>
            </article>
          </div>

          <article class="plan-card">
            <h4>接口计划</h4>
            <div
              v-for="api in developmentPlan.apiItems"
              :key="`${api.method}-${api.path}`"
              class="plan-item"
            >
              <strong>{{ api.method }} {{ api.path }}</strong>
              <p>{{ api.purpose }}</p>
              <span>{{ api.implementationHint }}</span>
            </div>
          </article>

          <article class="plan-card">
            <h4>实施步骤</h4>
            <div
              v-for="step in developmentPlan.implementationSteps"
              :key="step.stepNo"
              class="plan-item"
            >
              <strong>{{ step.stepNo }}. {{ step.title }}</strong>
              <p>{{ step.description }}</p>
              <span>{{ step.requiredBeforeCodeGeneration ? '代码生成前必须完成' : '可在代码生成后细化' }}</span>
            </div>
          </article>

          <article class="plan-card">
            <h4>来源映射</h4>
            <div
              v-for="mapping in developmentPlan.sourceMappings"
              :key="`${mapping.sourceType}-${mapping.usedFor}`"
              class="plan-item"
            >
              <strong>{{ mapping.sourceTitle }}</strong>
              <p>{{ mapping.usedFor }}</p>
              <span>置信度：{{ mapping.confidence }}</span>
            </div>
          </article>
        </section>

        <footer class="handoff-footer">
          <button
            class="primary-button"
            :disabled="!readiness.ready || !isCurrentPlanConfirmed"
            title="真实代码生成 Agent 将在 Phase 9 接入；当前 Phase 4 只完成交接闭环。"
          >
            {{
              !readiness.ready
                ? '暂不能进入正式开发 Agent'
                : isCurrentPlanConfirmed
                  ? 'Phase 4 交接链路已闭合，代码生成将在 Phase 9 接入'
                  : '需确认开发实施计划后进入代码生成'
            }}
          </button>
        </footer>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  confirmDevelopmentPlanSnapshot,
  fetchCurrentDevelopmentPlanConfirmation,
  fetchDevelopmentHandoffContext,
  fetchDevelopmentPhase4Acceptance,
  generateDevelopmentHandoffPackage,
  generateDevelopmentPlan,
} from '../api/developmentHandoffApi'
import type {
  ConfirmedDevelopmentPlanSnapshot,
  DevelopmentHandoffAsset,
  DevelopmentHandoffContextResponse,
  DevelopmentHandoffPackageResponse,
  DevelopmentHandoffReadinessResponse,
  DevelopmentPhase4AcceptanceResponse,
  DevelopmentPlanResponse,
} from '../types/developmentHandoff'

const props = defineProps<{
  workspaceId?: string | null
  projectCode?: string | null
  selectedModule: ModuleTreeNode | null
  readiness: DevelopmentHandoffReadinessResponse | null
  loading: boolean
  error: string
}>()

defineEmits<{
  refresh: []
  'open-quality-check': []
  'preview-ui-contract': []
}>()

const handoffContext = ref<DevelopmentHandoffContextResponse | null>(null)
const contextLoading = ref(false)
const contextError = ref('')

const developmentPlan = ref<DevelopmentPlanResponse | null>(null)
const planLoading = ref(false)
const planError = ref('')

const planConfirmation = ref<ConfirmedDevelopmentPlanSnapshot | null>(null)
const planConfirmationLoading = ref(false)
const planConfirmationError = ref('')
const confirmingPlan = ref(false)

const handoffPackage = ref<DevelopmentHandoffPackageResponse | null>(null)
const handoffPackageLoading = ref(false)
const handoffPackageError = ref('')

const phase4Acceptance = ref<DevelopmentPhase4AcceptanceResponse | null>(null)
const phase4AcceptanceLoading = ref(false)
const phase4AcceptanceError = ref('')

const readinessClass = computed(() => {
  if (!props.readiness) {
    return 'blocked'
  }

  if (props.readiness.readinessLevel === 'READY') {
    return 'ready'
  }

  if (props.readiness.readinessLevel === 'WARNING') {
    return 'warning'
  }

  return 'blocked'
})

const canPreviewUiContract = computed(() =>
  props.readiness?.nodeType === 'PAGE' &&
  props.readiness.assets.some(
    (asset: DevelopmentHandoffAsset) =>
      asset.artifactType === 'UI_CONTRACT' && asset.exists,
  ),
)

const canUseDevelopmentApi = computed(() =>
  Boolean(
    props.workspaceId &&
    props.projectCode &&
    props.selectedModule?.id,
  ),
)

const canGeneratePlan = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(props.readiness?.ready),
)

const isCurrentPlanConfirmed = computed(() =>
  Boolean(
    developmentPlan.value?.planChecksum &&
    planConfirmation.value?.planChecksum &&
    developmentPlan.value.planChecksum === planConfirmation.value.planChecksum,
  ),
)

const canGenerateHandoffPackage = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(planConfirmation.value?.id) &&
  (
    !developmentPlan.value ||
    isCurrentPlanConfirmed.value
  ),
)

const planConfirmationStatusText = computed(() => {
  if (!planConfirmation.value) {
    return '当前节点尚未确认开发实施计划。生成计划并确认后，后续真实代码生成 Agent 才能读取该计划。'
  }

  if (!developmentPlan.value) {
    return `已有确认计划，确认人 ${planConfirmation.value.confirmedBy || '-'}，确认时间 ${formatTime(planConfirmation.value.confirmedAt)}。`
  }

  if (isCurrentPlanConfirmed.value) {
    return '当前页面展示的 Development Plan 与已确认快照一致，可作为后续代码生成依据。'
  }

  return '当前 Development Plan 与已确认快照不一致，说明计划已重新生成或输入资产发生变化，需要重新确认。'
})

const snapshotStatusText = computed(() => {
  const snapshot = props.readiness?.uiContractSnapshot

  if (!snapshot?.exists) {
    return '尚未保存用户确认快照。请先打开交互预览，确认当前页面效果。'
  }

  if (!snapshot.matchesCurrentArtifact) {
    return '确认快照已滞后。当前 UI_CONTRACT 更新后，需要重新预览并确认。'
  }

  return `已确认：${snapshot.pageName || '-'}，版本 v${snapshot.artifactVersionNo || '-'}，确认时间 ${formatTime(snapshot.confirmedAt)}。`
})

watch(
  () => [
    props.workspaceId,
    props.projectCode,
    props.selectedModule?.id,
  ],
  async () => {
    handoffContext.value = null
    developmentPlan.value = null
    contextError.value = ''
    planError.value = ''
    planConfirmation.value = null
    planConfirmationError.value = ''
    handoffPackage.value = null
    handoffPackageError.value = ''
    phase4Acceptance.value = null
    phase4AcceptanceError.value = ''

    await loadCurrentPlanConfirmation()
    await loadPhase4Acceptance()
  },
  {
    immediate: true,
  },
)

async function loadPhase4Acceptance() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    phase4Acceptance.value = null
    return
  }

  phase4AcceptanceLoading.value = true
  phase4AcceptanceError.value = ''

  try {
    phase4Acceptance.value = await fetchDevelopmentPhase4Acceptance(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    phase4Acceptance.value = null
    phase4AcceptanceError.value = resolveErrorMessage(
      error,
      '读取 Phase 4 验收状态失败。',
    )
  } finally {
    phase4AcceptanceLoading.value = false
  }
}

async function handleLoadContext() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    contextError.value = '缺少 workspaceId、projectCode 或当前节点 ID，无法读取开发上下文。'
    return
  }

  contextLoading.value = true
  contextError.value = ''

  try {
    handoffContext.value = await fetchDevelopmentHandoffContext(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    contextError.value = resolveErrorMessage(
      error,
      '读取开发上下文失败。',
    )
  } finally {
    contextLoading.value = false
  }
}

async function handleGeneratePlan() {
  if (!canGeneratePlan.value || !props.selectedModule?.id) {
    planError.value = '当前节点尚未通过开发交接门禁，不能生成开发实施计划。'
    return
  }

  planLoading.value = true
  planError.value = ''

  try {
    developmentPlan.value = await generateDevelopmentPlan(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        planScope: props.readiness?.nodeType || 'MODULE',
        targetStack: 'CodeMind Studio default stack',
      },
    )

    handoffContext.value = developmentPlan.value.handoffContext
    await loadCurrentPlanConfirmation()
  } catch (error) {
    planError.value = resolveErrorMessage(
      error,
      '生成开发实施计划失败。',
    )
  } finally {
    planLoading.value = false
  }
}

async function loadCurrentPlanConfirmation() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    planConfirmation.value = null
    return
  }

  planConfirmationLoading.value = true
  planConfirmationError.value = ''

  try {
    planConfirmation.value = await fetchCurrentDevelopmentPlanConfirmation(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    planConfirmation.value = null
    planConfirmationError.value = resolveErrorMessage(
      error,
      '读取开发实施计划确认快照失败。',
    )
  } finally {
    planConfirmationLoading.value = false
  }
}

async function handleConfirmPlan() {
  if (!developmentPlan.value || !props.selectedModule?.id) {
    planConfirmationError.value = '请先生成开发实施计划，再确认。'
    return
  }

  const confirmationNote = window.prompt(
    '确认该开发实施计划后，后续代码生成 Agent 将以此计划作为施工依据。可填写确认备注：',
    '',
  )

  if (confirmationNote === null) {
    return
  }

  confirmingPlan.value = true
  planConfirmationError.value = ''

  try {
    planConfirmation.value = await confirmDevelopmentPlanSnapshot(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        plan: developmentPlan.value as unknown as Record<string, unknown>,
        planChecksum: developmentPlan.value.planChecksum || '',
        confirmationNote,
      },
    )

    await loadPhase4Acceptance()
  } catch (error) {
    planConfirmationError.value = resolveErrorMessage(
      error,
      '确认开发实施计划失败。',
    )
  } finally {
    confirmingPlan.value = false
  }
}

async function handleGenerateHandoffPackage() {
  if (!canGenerateHandoffPackage.value || !props.selectedModule?.id) {
    handoffPackageError.value = '请先确认开发实施计划，再生成 Development Agent 交接包。'
    return
  }

  handoffPackageLoading.value = true
  handoffPackageError.value = ''

  try {
    handoffPackage.value = await generateDevelopmentHandoffPackage(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )

    await loadPhase4Acceptance()
  } catch (error) {
    handoffPackageError.value = resolveErrorMessage(
      error,
      '生成 Development Agent 交接包失败。',
    )
  } finally {
    handoffPackageLoading.value = false
  }
}

function assetLevelClass(
  asset: DevelopmentHandoffAsset,
): string {
  if (!asset.exists || !asset.contentReady || asset.status === 'REJECTED') {
    return asset.required ? 'blocker' : 'warning'
  }

  if (
    asset.status === 'DRAFT' ||
    asset.status === 'ACTIVE' ||
    asset.status === 'PENDING_REVIEW' ||
    asset.status === 'NEEDS_REVIEW' ||
    asset.current === false
  ) {
    return 'warning'
  }

  return 'pass'
}

function formatTime(
  value?: string | null,
): string {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
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
</script>

<style scoped>
.development-handoff-panel {
  flex: 1;
  min-width: 0;
  padding: 22px;
  overflow-y: auto;
  background: #f8fafc;
}

.handoff-card {
  display: grid;
  gap: 16px;
}

.handoff-header,
.summary-card,
.node-info-card,
.snapshot-card,
.asset-section,
.issue-section,
.context-section,
.plan-confirm-section,
.handoff-package-section,
.phase4-acceptance-section,
.plan-section {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
}

.handoff-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.handoff-header h2 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 24px;
}

.handoff-header span,
.node-info-card p,
.section-heading p,
.snapshot-card span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-content: flex-start;
  justify-content: flex-end;
}

.summary-card {
  display: flex;
  gap: 18px;
  padding: 20px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.summary-card.ready {
  border-color: #bbf7d0;
  background: linear-gradient(135deg, #f0fdf4, #ffffff);
}

.summary-card.warning {
  border-color: #fde68a;
  background: linear-gradient(135deg, #fffbeb, #ffffff);
}

.summary-card.blocked {
  border-color: #fecaca;
  background: linear-gradient(135deg, #fef2f2, #ffffff);
}

.score-circle {
  width: 112px;
  height: 112px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #2563eb;
  color: #ffffff;
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 30px;
  box-sizing: border-box;
}

.ready .score-circle {
  background: #16a34a;
}

.warning .score-circle {
  background: #d97706;
}

.blocked .score-circle {
  background: #dc2626;
}

.score-circle strong {
  font-size: 42px;
  line-height: 1;
}

.score-circle span {
  font-size: 15px;
}

.summary-content {
  flex: 1;
  min-width: 0;
}

.summary-title-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.summary-title-row h3 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 20px;
}

.summary-title-row p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 13px;
}

.node-badge {
  align-self: flex-start;
  border-radius: 999px;
  padding: 6px 12px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.summary-grid strong,
.summary-grid span {
  display: block;
}

.summary-grid strong {
  color: #0f172a;
  font-size: 20px;
}

.summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.node-info-card,
.snapshot-card {
  padding: 16px;
}

.node-info-card div {
  display: flex;
  gap: 10px;
  align-items: center;
}

.node-info-card strong {
  color: #0f172a;
}

.node-info-card span {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
}

.snapshot-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-color: #fecaca;
  background: #fef2f2;
}

.snapshot-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.snapshot-card h3 {
  margin: 0 0 6px;
  color: #0f172a;
}

.asset-section,
.issue-section,
.context-section,
.plan-confirm-section,
.handoff-package-section,
.phase4-acceptance-section,
.plan-section {
  padding: 18px;
}

.section-heading {
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 5px;
  color: #0f172a;
  font-size: 17px;
}

.asset-grid,
.context-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
}

.asset-card,
.context-asset-card,
.context-json-card,
.plan-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.asset-card header,
.asset-card footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.asset-card strong,
.context-asset-card strong,
.context-json-card strong,
.plan-card h4 {
  color: #0f172a;
  font-size: 14px;
}

.asset-card header span,
.context-asset-card span,
.plan-item span {
  border-radius: 999px;
  padding: 2px 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.asset-card header span.required,
.context-asset-card.required span {
  background: #eff6ff;
  color: #1d4ed8;
}

.asset-card p,
.context-asset-card p,
.context-json-card p,
.plan-item p {
  margin: 8px 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.asset-card small {
  color: #64748b;
  font-size: 12px;
}

.asset-card.pass {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.asset-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.asset-card.blocker {
  border-color: #fecaca;
  background: #fef2f2;
}

.issue-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 10px;
}

.issue-card strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 14px;
}

.issue-card p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 13px;
}

.issue-card.blocker {
  border-color: #fecaca;
  background: #fef2f2;
}

.issue-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.empty-box,
.error-box,
.empty-mini {
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  padding: 18px;
  background: #ffffff;
  color: #64748b;
  line-height: 1.7;
}

.error-box {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.empty-mini {
  padding: 14px;
  background: #f8fafc;
}

.context-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.context-json-card {
  margin-top: 10px;
  background: #f8fafc;
}

.package-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.package-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.package-summary-grid strong,
.package-summary-grid span {
  display: block;
}

.package-summary-grid strong {
  color: #0f172a;
  font-size: 15px;
  word-break: break-all;
}

.package-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.package-section-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.package-section-list span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.plan-confirm-section.confirmed {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.plan-confirm-section.stale {
  border-color: #fde68a;
  background: #fffbeb;
}

.plan-confirm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.plan-confirm-meta span,
.plan-principles span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.plan-principles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.plan-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.plan-card {
  margin-bottom: 12px;
}

.plan-card h4 {
  margin: 0 0 10px;
  font-size: 15px;
}

.plan-item {
  border-top: 1px solid #e2e8f0;
  padding: 10px 0;
}

.plan-item:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.plan-item strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.handoff-footer {
  display: flex;
  justify-content: flex-end;
}

.primary-button,
.secondary-button {
  border-radius: 999px;
  padding: 9px 14px;
  font-size: 13px;
  cursor: pointer;
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

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}


.phase4-acceptance-section.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.phase4-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.phase4-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.phase4-summary-grid strong,
.phase4-summary-grid span {
  display: block;
}

.phase4-summary-grid strong {
  color: #0f172a;
  font-size: 18px;
  word-break: break-word;
}

.phase4-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.phase4-summary-text {
  margin: 0 0 12px;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.phase4-item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 10px;
}

.phase4-item-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.phase4-item-card.pass,
.phase4-item-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.phase4-item-card.blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.phase4-item-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.phase4-item-card header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.phase4-item-card strong {
  color: #0f172a;
  font-size: 14px;
}

.phase4-item-card header span {
  border-radius: 999px;
  padding: 2px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.phase4-item-card p {
  margin: 8px 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.phase4-item-card footer {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.phase4-blocker-list {
  margin-top: 12px;
  border: 1px solid #fecaca;
  border-radius: 14px;
  padding: 12px;
  background: #fef2f2;
}

.phase4-blocker-list strong {
  display: block;
  margin-bottom: 6px;
  color: #991b1b;
  font-size: 13px;
}

.phase4-blocker-list p {
  margin: 4px 0;
  color: #7f1d1d;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 820px) {
  .handoff-header,
  .summary-card,
  .snapshot-card {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plan-columns {
    grid-template-columns: 1fr;
  }
}
</style>


