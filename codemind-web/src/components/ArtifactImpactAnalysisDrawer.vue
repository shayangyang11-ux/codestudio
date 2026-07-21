<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="impact-mask"
      @click.self="$emit('close')"
    >
      <aside class="impact-drawer">
        <header class="impact-header">
          <div>
            <p class="eyebrow">需求变更影响分析</p>
            <h2>{{ sourceTitle }}</h2>
            <span>下游受影响资产 {{ items.length }} 项</span>
          </div>

          <button
            class="close-button"
            @click="$emit('close')"
          >
            ×
          </button>
        </header>

        <section class="impact-summary agent-first-summary">
          <h3>处理原则</h3>
          <p>
            当前资产发生变化后，下游资产可能需要同步更新。系统会优先使用 LangGraph 资产变更 Agent 做局部同步修订，
            尽量只修改受上游变化影响的段落、字段、流程或测试点；整份重新生成只是备选动作。
          </p>
          <p class="muted-tip">
            不想立即处理下游时，可以直接点击“暂不处理下游”。下游资产会继续保持“受上游影响”状态，后续可再进入本面板处理。
          </p>
        </section>

        <div
          v-if="error"
          class="error-box"
        >
          {{ error }}
        </div>

        <section class="impact-body">
          <div class="impact-toolbar">
            <div>
              <strong>选择下游资产</strong>
              <p>AI 局部同步修订一次只处理 1 个资产，确保每份编辑提案都能单独人工确认。</p>
            </div>

            <button
              class="secondary-button"
              :disabled="!items.length || submitting"
              @click="$emit('select-all')"
            >
              全选
            </button>
          </div>

          <div
            v-if="items.length === 0"
            class="empty-box"
          >
            当前资产没有配置下游影响链路。
          </div>

          <div
            v-else
            class="impact-list"
          >
            <label
              v-for="item in items"
              :key="item.artifactType"
              class="impact-item"
              :class="{
                selected: selectedArtifactTypes.includes(item.artifactType),
                unsupported: !isAgentSyncSupported(item.artifactType),
              }"
            >
              <input
                type="checkbox"
                :checked="selectedArtifactTypes.includes(item.artifactType)"
                :disabled="submitting"
                @change="handleToggle(item.artifactType, $event)"
              />

              <div class="impact-main">
                <div class="impact-title-row">
                  <strong>{{ item.title }}</strong>
                  <span :class="['status-chip', statusClass(item.status)]">
                    {{ statusText(item.status) }}
                  </span>
                </div>

                <p>{{ item.description }}</p>

                <div class="impact-meta">
                  <span>{{ item.artifactExists ? `已有版本 V${item.versionNo || '-'}` : '尚未生成' }}</span>
                  <span>任务状态：{{ taskStatusText(item.taskStatus) }}</span>
                  <span v-if="item.hasActiveTask">当前有任务执行中</span>
                  <span v-if="!isAgentSyncSupported(item.artifactType)">需专用流程处理</span>
                </div>
              </div>
            </label>
          </div>
        </section>

        <footer class="impact-footer">
          <div class="footer-note">
            <strong>{{ selectedArtifactTypes.length }} 项已选择</strong>
            <span>{{ footerHint }}</span>
          </div>

          <div class="footer-actions">
            <button
              class="secondary-button"
              :disabled="submitting"
              @click="$emit('close')"
            >
              暂不处理下游
            </button>

            <button
              class="primary-button"
              :disabled="submitting || selectedArtifactTypes.length !== 1 || !selectedOneCanSync"
              @click="$emit('sync-selected')"
            >
              {{ submitting ? '处理中...' : syncButtonText }}
            </button>

            <button
              class="danger-outline-button"
              :disabled="submitting || selectedArtifactTypes.length === 0"
              title="备选动作：当局部同步修订不适合时再使用整份重新生成。"
              @click="$emit('regenerate')"
            >
              重新生成选中项（备选）
            </button>
          </div>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'

export interface ImpactAnalysisItem {
  artifactType: RequirementArtifactType
  taskType: string
  title: string
  description: string
  artifactExists: boolean
  versionNo?: number | null
  status?: string | null
  taskStatus?: string | null
  hasActiveTask: boolean
}

const props = defineProps<{
  visible: boolean
  submitting?: boolean
  sourceTitle: string
  sourceArtifactType: RequirementArtifactType | null
  items: ImpactAnalysisItem[]
  selectedArtifactTypes: RequirementArtifactType[]
  error?: string
}>()

const emit = defineEmits<{
  close: []
  regenerate: []
  'sync-selected': []
  'select-all': []
  'toggle-artifact': [artifactType: RequirementArtifactType, checked: boolean]
}>()

const agentSyncSupportedTypes: RequirementArtifactType[] = [
  'MODULE_PRD',
  'BUSINESS_FLOW',
  'PAGE_SPEC',
  'DATA_MODEL',
  'API_CONTRACT',
  'TEST_SPEC',
]

const selectedOneCanSync = computed(() => {
  if (props.selectedArtifactTypes.length !== 1) {
    return false
  }

  const artifactType = props.selectedArtifactTypes[0]
  const item = props.items.find((entry) => entry.artifactType === artifactType)

  return Boolean(item?.artifactExists && isAgentSyncSupported(artifactType))
})

const syncButtonText = computed(() => {
  if (props.selectedArtifactTypes.length === 0) {
    return '选择 1 项后 AI 局部同步修订'
  }

  if (props.selectedArtifactTypes.length > 1) {
    return '请只选择 1 项'
  }

  return 'AI 局部同步修订选中项'
})

const footerHint = computed(() => {
  if (props.selectedArtifactTypes.length === 0) {
    return '可以暂不处理下游，也可以选择 1 个资产交给 Agent 局部同步修订。'
  }

  if (props.selectedArtifactTypes.length > 1) {
    return 'AI 局部同步修订一次只处理 1 个资产；重新生成可以处理多个，但不建议作为默认动作。'
  }

  if (!selectedOneCanSync.value) {
    return '该资产暂不支持通用 Agent 局部同步修订，或者尚未生成初始版本。'
  }

  return '将生成资产编辑提案，不会直接覆盖当前版本；确认后生成“新版本待确认”。'
})

function isAgentSyncSupported(
  artifactType: RequirementArtifactType,
): boolean {
  return agentSyncSupportedTypes.includes(artifactType)
}

function handleToggle(
  artifactType: RequirementArtifactType,
  event: Event,
) {
  emit(
    'toggle-artifact',
    artifactType,
    (event.target as HTMLInputElement).checked,
  )
}

function statusText(status?: string | null): string {
  if (status === 'PENDING_REVIEW') return '新版本待确认'
  if (status === 'NEEDS_REVIEW') return '受上游影响'
  if (status === 'APPROVED') return '已确认'
  if (status === 'REJECTED') return '已退回'
  if (status === 'ARCHIVED') return '已归档'
  if (status === 'SUPERSEDED') return '已被替代'
  if (status === 'ACTIVE' || status === 'DRAFT') return '未确认'
  return status || '未生成'
}

function statusClass(status?: string | null): string {
  if (status === 'PENDING_REVIEW') return 'pending'
  if (status === 'NEEDS_REVIEW') return 'affected'
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'default'
}

function taskStatusText(status?: string | null): string {
  if (status === 'QUEUED') return '排队中'
  if (status === 'RUNNING') return '生成中'
  if (status === 'RETRYING') return '等待重试'
  if (status === 'BLOCKED') return '等待依赖'
  if (status === 'FAILED') return '生成失败'
  if (status === 'TIMEOUT') return '执行超时'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'SUCCESS') return '已完成'
  if (status === 'PARTIAL_SUCCESS') return '部分完成'
  return status || '-'
}
</script>

<style scoped>
.impact-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.36);
}

.impact-drawer {
  width: min(860px, 96vw);
  height: 100vh;
  overflow-y: auto;
  background: #ffffff;
  box-shadow: -20px 0 60px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
}

.impact-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
}

.impact-header h2 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 20px;
}

.impact-header span {
  color: #64748b;
  font-size: 13px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.close-button {
  width: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}

.impact-summary,
.impact-body {
  margin: 18px 24px 0;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  padding: 16px;
  background: #eff6ff;
}

.agent-first-summary h3,
.impact-toolbar strong {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.agent-first-summary p,
.impact-toolbar p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.muted-tip {
  color: #64748b !important;
}

.error-box {
  margin: 14px 24px 0;
  border: 1px solid #fecaca;
  border-radius: 14px;
  padding: 12px 14px;
  background: #fef2f2;
  color: #991b1b;
  font-size: 13px;
}

.impact-body {
  background: #ffffff;
  border-color: #e2e8f0;
  flex: 1;
}

.impact-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.impact-list {
  display: grid;
  gap: 10px;
}

.impact-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: flex-start;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 13px;
  cursor: pointer;
  background: #ffffff;
}

.impact-item.selected {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.impact-item.unsupported {
  background: #f8fafc;
}

.impact-main {
  min-width: 0;
}

.impact-title-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.impact-title-row strong {
  color: #0f172a;
  font-size: 14px;
}

.impact-main p {
  margin: 7px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.impact-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.impact-meta span {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.status-chip {
  border-radius: 999px;
  padding: 3px 8px;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
}

.status-chip.pending {
  background: #fef3c7;
  color: #92400e;
}

.status-chip.affected {
  background: #ffedd5;
  color: #c2410c;
}

.status-chip.approved {
  background: #dcfce7;
  color: #166534;
}

.status-chip.rejected {
  background: #fee2e2;
  color: #991b1b;
}

.status-chip.default {
  background: #f1f5f9;
  color: #475569;
}

.empty-box {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 24px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.impact-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  margin-top: 18px;
  border-top: 1px solid #e2e8f0;
  padding: 14px 24px;
  background: #ffffff;
}

.footer-note {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.footer-note strong {
  color: #0f172a;
  font-size: 13px;
}

.footer-note span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.footer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.primary-button,
.secondary-button,
.danger-outline-button {
  border-radius: 999px;
  padding: 8px 13px;
  font-size: 13px;
  font-weight: 800;
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

.danger-outline-button {
  border: 1px solid #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.primary-button:disabled,
.secondary-button:disabled,
.danger-outline-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .impact-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .footer-actions {
    justify-content: stretch;
  }

  .footer-actions button {
    flex: 1;
  }
}
</style>
