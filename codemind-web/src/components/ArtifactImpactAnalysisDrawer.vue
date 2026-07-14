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
            <span>
              下游受影响资产 {{ items.length }} 项
            </span>
          </div>

          <button
            class="close-button"
            @click="$emit('close')"
          >
            ×
          </button>
        </header>

        <section class="impact-summary">
          <h3>影响范围说明</h3>
          <p>
            当前资产发生变更后，下游资产可能需要重新生成，以保证需求、流程、页面、数据、接口、测试和交接包之间保持一致。
          </p>
          <p>
            系统将按照需求资产链路顺序提交重生成任务，已有版本不会删除，新结果会作为新版本保存。
          </p>
        </section>

        <section class="impact-body">
          <div class="impact-list-header">
            <div>
              <h3>受影响资产</h3>
              <p>请选择需要重生成的下游资产。</p>
            </div>

            <button
              class="secondary-button"
              :disabled="items.length === 0"
              @click="$emit('select-all')"
            >
              全选
            </button>
          </div>

          <div
            v-if="items.length === 0"
            class="empty-box"
          >
            当前资产没有下游依赖，无需进行影响重生成。
          </div>

          <div
            v-else
            class="impact-list"
          >
            <label
              v-for="item in items"
              :key="item.artifactType"
              class="impact-item"
              :class="{ active: selectedArtifactTypes.includes(item.artifactType) }"
            >
              <input
                type="checkbox"
                :checked="selectedArtifactTypes.includes(item.artifactType)"
                @change="handleToggle(item.artifactType, $event)"
              />

              <div class="impact-item-main">
                <div class="impact-item-title">
                  <strong>{{ item.title }}</strong>

                  <span
                    v-if="item.hasActiveTask"
                    class="status-chip running"
                  >
                    任务进行中
                  </span>

                  <span
                    v-else-if="item.artifactExists"
                    class="status-chip exists"
                  >
                    已有 v{{ item.versionNo || '-' }}
                  </span>

                  <span
                    v-else
                    class="status-chip missing"
                  >
                    未生成
                  </span>
                </div>

                <p>{{ item.description }}</p>

                <small>
                  任务类型：{{ item.taskType }}
                  <span v-if="item.taskStatus">
                    · 当前任务状态：{{ taskStatusText(item.taskStatus) }}
                  </span>
                </small>
              </div>
            </label>
          </div>
        </section>

        <footer class="impact-footer">
          <p
            v-if="error"
            class="error-text"
          >
            {{ error }}
          </p>

          <div class="footer-actions">
            <button
              class="secondary-button"
              @click="$emit('close')"
            >
              取消
            </button>

            <button
              class="primary-button"
              :disabled="submitting || selectedArtifactTypes.length === 0"
              @click="$emit('regenerate')"
            >
              {{ submitting ? '正在提交任务...' : `一键重生成 ${selectedArtifactTypes.length} 项` }}
            </button>
          </div>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type {
  RequirementArtifactType,
  RequirementTaskType,
} from '../domain/requirementArtifactDefinitions'

export interface ImpactAnalysisItem {
  artifactType: RequirementArtifactType
  taskType: RequirementTaskType
  title: string
  description: string
  artifactExists: boolean
  versionNo?: number | null
  status?: string | null
  taskStatus?: string | null
  hasActiveTask: boolean
}

defineProps<{
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
  'select-all': []
  'toggle-artifact': [artifactType: RequirementArtifactType, checked: boolean]
}>()

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

.eyebrow {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
}

.impact-header h2 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 20px;
}

.impact-header span {
  color: #64748b;
  font-size: 13px;
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

.close-button:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.impact-summary {
  margin: 18px 24px 0;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  padding: 16px;
  background: #eff6ff;
}

.impact-summary h3,
.impact-list-header h3 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 16px;
}

.impact-summary p,
.impact-list-header p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.impact-body {
  padding: 18px 24px 20px;
}

.impact-list-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.impact-list {
  display: grid;
  gap: 10px;
}

.impact-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
  cursor: pointer;
}

.impact-item:hover {
  border-color: #93c5fd;
  background: #f8fbff;
}

.impact-item.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.08);
}

.impact-item input {
  margin-top: 4px;
}

.impact-item-main {
  min-width: 0;
}

.impact-item-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.impact-item-title strong {
  color: #0f172a;
  font-size: 15px;
}

.impact-item-main p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.impact-item-main small {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.status-chip {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  white-space: nowrap;
}

.status-chip.exists {
  background: #dcfce7;
  color: #166534;
}

.status-chip.missing {
  background: #f1f5f9;
  color: #475569;
}

.status-chip.running {
  background: #fef3c7;
  color: #92400e;
}

.empty-box {
  border-radius: 14px;
  padding: 24px;
  text-align: center;
  color: #64748b;
  background: #f8fafc;
}

.impact-footer {
  margin-top: auto;
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}

.error-text {
  margin: 0;
  color: #b91c1c;
  font-size: 13px;
}

.footer-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}

.primary-button,
.secondary-button {
  border-radius: 10px;
  padding: 8px 13px;
  white-space: nowrap;
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

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .impact-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .footer-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>