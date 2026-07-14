<template>
  <section class="artifact-task-panel">
    <div class="artifact-task-header">
      <div>
        <h4>工程资产生成状态</h4>
        <p>展示当前节点关键工程资产的生成进度、依赖状态和可执行操作。</p>
      </div>

      <span class="artifact-task-count">
        {{ items.length }} 项
      </span>
    </div>

    <div class="artifact-task-list">
      <div
        v-for="item in items"
        :key="item.taskType"
        class="artifact-task-row"
      >
        <div class="artifact-task-main">
          <div class="artifact-task-title-line">
            <strong>{{ item.title }}</strong>

            <span
              class="artifact-task-badge"
              :class="statusClass(item)"
            >
              {{ statusText(item) }}
            </span>

            <span
              v-if="item.artifactExists && item.artifactVersionNo"
              class="artifact-version-mini"
            >
              v{{ item.artifactVersionNo }}
            </span>
          </div>

          <p class="artifact-task-description">
            {{ descriptionText(item) }}
          </p>

          <p
            v-if="item.task?.errorMessage"
            class="artifact-task-error"
          >
            {{ friendlyError(item.task.errorMessage) }}
          </p>
        </div>

        <div class="artifact-task-actions">
          <button
            v-if="canGenerate(item)"
            class="mini-button"
            @click="$emit('generate', item.taskType)"
          >
            {{ item.artifactExists ? '重新生成' : '生成' }}
          </button>

          <button
            v-if="canCancel(item)"
            class="mini-button warning"
            @click="$emit('cancel', item.task!.id)"
          >
            取消
          </button>

          <button
            v-if="canRetry(item)"
            class="mini-button"
            @click="$emit('retry', item.task!.id)"
          >
            {{ retryButtonText(item) }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ModuleArtifactTaskItem } from '../types/codemind'

const props = defineProps<{
  items: ModuleArtifactTaskItem[]
}>()

defineEmits<{
  generate: [taskType: string]
  cancel: [taskId: string]
  retry: [taskId: string]
}>()

function statusText(item: ModuleArtifactTaskItem): string {
  const status = item.task?.status

  if (status === 'QUEUED') return '排队中'
  if (status === 'RUNNING') return '生成中'
  if (status === 'RETRYING') return '等待重试'
  if (status === 'BLOCKED') return '等待依赖'
  if (status === 'FAILED') return '生成失败'
  if (status === 'TIMEOUT') return '生成超时'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS') return '已生成'

  if (item.artifactExists) return '已生成'

  return '未生成'
}

function statusClass(item: ModuleArtifactTaskItem): string {
  const status = item.task?.status

  if (status === 'QUEUED' || status === 'RUNNING' || status === 'RETRYING') {
    return 'running'
  }

  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS' || item.artifactExists) {
    return 'success'
  }

  if (status === 'BLOCKED') {
    return 'blocked'
  }

  if (status === 'FAILED' || status === 'TIMEOUT') {
    return 'failed'
  }

  if (status === 'CANCELLED') {
    return 'cancelled'
  }

  return 'pending'
}

function descriptionText(item: ModuleArtifactTaskItem): string {
  const status = item.task?.status

  if (status === 'QUEUED') return '任务已进入队列，系统会自动调度执行。'
  if (status === 'RUNNING') return 'Agent 正在生成该资产，请稍候。'
  if (status === 'RETRYING') return '上一次执行未成功，系统稍后会自动重试。'
  if (status === 'BLOCKED') return '当前任务正在等待上游依赖。系统会在依赖资产生成后自动继续，也可以补齐依赖后点击“重试检查”。'
  if (status === 'FAILED') return '生成失败，可以点击重试。'
  if (status === 'TIMEOUT') return '生成超时，可以稍后重试。'
  if (status === 'CANCELLED') return '任务已取消，可以重新生成。'

  if (item.artifactExists) {
    return '当前节点已有该资产，可继续查看或重新生成。'
  }

  return '当前节点尚未生成该资产。'
}

function canGenerate(item: ModuleArtifactTaskItem): boolean {
  const status = item.task?.status
  return !status || ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED'].includes(status)
}

function canCancel(item: ModuleArtifactTaskItem): boolean {
  const status = item.task?.status
  return Boolean(item.task?.id && ['QUEUED', 'RUNNING', 'RETRYING'].includes(status || ''))
}

function canRetry(item: ModuleArtifactTaskItem): boolean {
  const status = item.task?.status
  return Boolean(item.task?.id && ['FAILED', 'TIMEOUT', 'CANCELLED', 'BLOCKED'].includes(status || ''))
}

function retryButtonText(item: ModuleArtifactTaskItem): string {
  return item.task?.status === 'BLOCKED' ? '重试检查' : '重试'
}

function friendlyError(message: string): string {
  return message
    .replace('Missing required upstream artifacts:', '缺少上游资产：')
    .replaceAll('MODULE_PRD', artifactTitleByType('MODULE_PRD'))
    .replaceAll('BUSINESS_FLOW', artifactTitleByType('BUSINESS_FLOW'))
    .replaceAll('PAGE_SPEC', artifactTitleByType('PAGE_SPEC'))
    .replaceAll('DATA_MODEL', artifactTitleByType('DATA_MODEL'))
    .replaceAll('API_CONTRACT', artifactTitleByType('API_CONTRACT'))
    .replaceAll('TEST_SPEC', artifactTitleByType('TEST_SPEC'))
    .replaceAll('UI_CONTRACT', artifactTitleByType('UI_CONTRACT'))
    .replaceAll('DEV_HANDOFF_PACKAGE', artifactTitleByType('DEV_HANDOFF_PACKAGE'))
}

function artifactTitleByType(artifactType: string): string {
  const matched = props.items.find((item) => item.artifactType === artifactType)

  if (matched?.title) {
    return matched.title
  }

  const fallback: Record<string, string> = {
    MODULE_PRD: '需求说明',
    BUSINESS_FLOW: '流程说明',
    PAGE_SPEC: '页面/清单说明',
    DATA_MODEL: '数据说明',
    API_CONTRACT: '接口说明',
    TEST_SPEC: '测试说明',
    UI_CONTRACT: '交互原型',
    DEV_HANDOFF_PACKAGE: '开发说明',
  }

  return fallback[artifactType] || artifactType
}
</script>

<style scoped>
.artifact-task-panel {
  margin-top: 16px;
  border: 1px solid #dbe7ff;
  border-radius: 18px;
  padding: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.artifact-task-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.artifact-task-header h4 {
  margin: 0 0 4px;
  font-size: 15px;
  color: #0f172a;
}

.artifact-task-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.artifact-task-count,
.artifact-version-mini {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  background: #eaf2ff;
  color: #2563eb;
}

.artifact-task-list {
  display: grid;
  gap: 10px;
}

.artifact-task-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  background: #ffffff;
}

.artifact-task-main {
  min-width: 0;
}

.artifact-task-title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.artifact-task-title-line strong {
  color: #0f172a;
  font-size: 14px;
}

.artifact-task-badge {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  background: #f1f5f9;
  color: #64748b;
}

.artifact-task-badge.success {
  background: #dcfce7;
  color: #15803d;
}

.artifact-task-badge.running {
  background: #dbeafe;
  color: #1d4ed8;
}

.artifact-task-badge.blocked {
  background: #fef3c7;
  color: #b45309;
}

.artifact-task-badge.failed {
  background: #fee2e2;
  color: #b91c1c;
}

.artifact-task-badge.cancelled {
  background: #f1f5f9;
  color: #64748b;
}

.artifact-task-description {
  margin: 5px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.artifact-task-error {
  margin: 6px 0 0;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.5;
}

.artifact-task-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.mini-button {
  border: 1px solid #2563eb;
  border-radius: 10px;
  padding: 7px 11px;
  background: #ffffff;
  color: #2563eb;
  cursor: pointer;
  white-space: nowrap;
}

.mini-button:hover {
  background: #eff6ff;
}

.mini-button.warning {
  border-color: #f97316;
  color: #ea580c;
}

@media (max-width: 900px) {
  .artifact-task-row {
    grid-template-columns: 1fr;
  }

  .artifact-task-actions {
    justify-content: flex-start;
  }
}
</style>
