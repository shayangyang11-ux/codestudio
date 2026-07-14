<template>
  <Teleport to="body">
    <div v-if="visible" class="drawer-mask" @click.self="$emit('close')">
      <aside class="drawer-panel">
        <header class="drawer-header">
          <div>
            <p class="eyebrow">任务详情</p>
            <h2>{{ task?.taskType || 'Agent Task' }}</h2>
            <span v-if="task" :class="['status-pill', statusClass(task.status)]">
              {{ task.status }}
            </span>
          </div>

          <div class="drawer-header-actions">
            <button
              v-if="task && canCancel(task.status)"
              class="task-action-button neutral"
              :disabled="operationLoading"
              @click="$emit('cancel', task.id)"
            >
              取消
            </button>

            <button
              v-if="task && canRetry(task.status)"
              class="task-action-button warning"
              :disabled="operationLoading"
              @click="$emit('retry', task.id)"
            >
              重试
            </button>

            <button
              v-if="task && canArchive(task.status)"
              class="task-action-button danger"
              :disabled="operationLoading"
              title="归档后默认不再显示在任务中心，数据库审计记录仍保留。"
              @click="$emit('archive', task.id)"
            >
              归档
            </button>

            <button class="close-button" @click="$emit('close')">
              ×
            </button>
          </div>
        </header>

        <div v-if="loading" class="drawer-empty">
          正在加载任务详情...
        </div>

        <div v-else-if="error" class="drawer-error">
          {{ error }}
        </div>

        <div v-else-if="task" class="drawer-content">
          <section class="detail-section">
            <h3>基础信息</h3>

            <div class="info-grid">
              <label>
                任务 ID
                <span>{{ task.id }}</span>
              </label>

              <label>
                项目 ID
                <span>{{ task.projectId || '-' }}</span>
              </label>

              <label>
                模块 ID
                <span>{{ task.moduleId || '-' }}</span>
              </label>

              <label>
                队列
                <span>{{ task.queueName || '-' }}</span>
              </label>

              <label>
                尝试次数
                <span>{{ task.attemptCount ?? 0 }} / {{ task.maxAttempts ?? '-' }}</span>
              </label>

              <label>
                结果资产
                <span>{{ task.resultArtifactId || '-' }}</span>
              </label>
            </div>
          </section>

          <section class="detail-section">
            <h3>时间信息</h3>

            <div class="info-grid">
              <label>
                创建时间
                <span>{{ formatTime(task.createdAt) }}</span>
              </label>

              <label>
                开始时间
                <span>{{ formatTime(task.startedAt) }}</span>
              </label>

              <label>
                结束时间
                <span>{{ formatTime(task.finishedAt) }}</span>
              </label>

              <label>
                更新时间
                <span>{{ formatTime(task.updatedAt) }}</span>
              </label>
            </div>
          </section>

          <section v-if="task.errorMessage || task.errorCode || task.lastErrorMessage || task.lastErrorCode"
            class="detail-section">
            <h3>失败诊断</h3>

            <div v-if="task.errorMessage || task.errorCode" class="friendly-error">
              <strong>{{ task.errorCode || '任务失败' }}</strong>
              <p>{{ task.errorMessage || '-' }}</p>
            </div>

            <details v-if="task.lastErrorMessage || task.lastErrorCode" class="raw-error">
              <summary>查看原始错误</summary>

              <div class="raw-error-body">
                <label>
                  原始错误码
                  <span>{{ task.lastErrorCode || '-' }}</span>
                </label>

                <label>
                  原始错误信息
                  <pre>{{ task.lastErrorMessage || '-' }}</pre>
                </label>
              </div>
            </details>
          </section>
          <section class="detail-section">
            <h3>Prompt 模板追溯</h3>

            <dl class="detail-grid">
              <div>
                <dt>模板名称</dt>
                <dd>{{ promptTemplateName() }}</dd>
              </div>

              <div>
                <dt>模板 Key</dt>
                <dd>{{ promptTemplateKey() }}</dd>
              </div>

              <div>
                <dt>模板版本</dt>
                <dd>{{ promptTemplateVersion() }}</dd>
              </div>

              <div class="full-row">
                <dt>模板校验值</dt>
                <dd class="checksum">
                  {{ promptTemplateChecksum() }}
                </dd>
              </div>

              <div class="full-row">
                <dt>模板版本 ID</dt>
                <dd class="checksum">
                  {{ promptTemplateVersionId() }}
                </dd>
              </div>
            </dl>
          </section>
          <section class="detail-section technical-section">
            <details>
              <summary>
                <span>请求 Payload</span>
                <small>技术调试信息</small>
              </summary>

              <div class="section-title-row payload-toolbar">
                <p>
                  原始请求参数、上下文包和 Prompt 模板快照。用于问题排查、任务复现和技术审计。
                </p>

                <button class="copy-button" :disabled="!task.requestPayload"
                  @click.stop="copyJson(task.requestPayload)">
                  复制
                </button>
              </div>

              <pre class="json-box">{{ prettyJson(task.requestPayload) }}</pre>
            </details>
          </section>

          <section class="detail-section technical-section">
            <details>
              <summary>
                <span>响应 Payload</span>
                <small>技术调试信息</small>
              </summary>

              <div class="section-title-row payload-toolbar">
                <p>
                  Agent 执行后的原始响应结果。用于核对生成状态、资产内容和异常排查。
                </p>

                <button class="copy-button" :disabled="!task.responsePayload"
                  @click.stop="copyJson(task.responsePayload)">
                  复制
                </button>
              </div>

              <pre class="json-box">{{ prettyJson(task.responsePayload) }}</pre>
            </details>
          </section>
        </div>

        <div v-else class="drawer-empty">
          暂无任务详情。
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type {
  AgentTaskPayloadResponse,
  AgentTaskStatus,
} from '../types/codemind'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  operationLoading?: boolean
  task: AgentTaskPayloadResponse | null
  error?: string
}>()

defineEmits<{
  close: []
  cancel: [taskId: string]
  retry: [taskId: string]
  archive: [taskId: string]
}>()

function statusClass(status?: AgentTaskStatus | null): string {
  if (status === 'RUNNING') return 'running'
  if (status === 'QUEUED' || status === 'RETRYING') return 'queued'
  if (status === 'BLOCKED') return 'blocked'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'failed'
  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS') return 'success'
  if (status === 'CANCELLED') return 'cancelled'
  return 'pending'
}

function canCancel(status?: AgentTaskStatus | null): boolean {
  return Boolean(status && ['PENDING', 'QUEUED', 'RUNNING', 'RETRYING'].includes(status))
}

function canRetry(status?: AgentTaskStatus | null): boolean {
  return Boolean(status && ['FAILED', 'TIMEOUT', 'CANCELLED', 'BLOCKED'].includes(status))
}

function canArchive(status?: AgentTaskStatus | null): boolean {
  return Boolean(status && ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED', 'BLOCKED'].includes(status))
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

function prettyJson(value?: Record<string, unknown> | null): string {
  if (!value) {
    return '-'
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

async function copyJson(value?: Record<string, unknown> | null) {
  if (!value || !navigator.clipboard) {
    return
  }

  await navigator.clipboard.writeText(prettyJson(value))
}

type PromptTemplatePayload = {
  template_name?: string
  templateName?: string
  template_key?: string
  templateKey?: string
  version?: string
  template_version?: string
  templateVersion?: string
  checksum?: string
  template_checksum?: string
  templateChecksum?: string
  template_version_id?: string
  templateVersionId?: string
}

function getPromptTemplatePayload(): PromptTemplatePayload | null {
  const requestPayload = props.task?.requestPayload

  if (!requestPayload || typeof requestPayload !== 'object') {
    return null
  }

  const payload = requestPayload as Record<string, unknown>

  const snakeCaseValue = payload.prompt_template
  const camelCaseValue = payload.promptTemplate

  if (snakeCaseValue && typeof snakeCaseValue === 'object') {
    return snakeCaseValue as PromptTemplatePayload
  }

  if (camelCaseValue && typeof camelCaseValue === 'object') {
    return camelCaseValue as PromptTemplatePayload
  }

  return null
}

function promptTemplateName(): string {
  const payload = getPromptTemplatePayload()

  return (
    payload?.template_name ||
    payload?.templateName ||
    '-'
  )
}

function promptTemplateKey(): string {
  const payload = getPromptTemplatePayload()

  return (
    props.task?.promptTemplateKey ||
    payload?.template_key ||
    payload?.templateKey ||
    '-'
  )
}

function promptTemplateVersion(): string {
  const payload = getPromptTemplatePayload()

  return (
    props.task?.promptTemplateVersion ||
    payload?.version ||
    payload?.template_version ||
    payload?.templateVersion ||
    '-'
  )
}

function promptTemplateChecksum(): string {
  const payload = getPromptTemplatePayload()

  return (
    props.task?.promptTemplateChecksum ||
    payload?.checksum ||
    payload?.template_checksum ||
    payload?.templateChecksum ||
    '-'
  )
}

function promptTemplateVersionId(): string {
  const payload = getPromptTemplatePayload()

  return (
    props.task?.promptTemplateVersionId ||
    payload?.template_version_id ||
    payload?.templateVersionId ||
    '-'
  )
}
</script>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.36);
}

.drawer-panel {
  width: min(760px, 96vw);
  height: 100vh;
  overflow-y: auto;
  background: #ffffff;
  box-shadow: -20px 0 60px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
}

.drawer-header {
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

.drawer-header h2 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 20px;
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

.drawer-header-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}

.task-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 34px;
  border-radius: 10px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.task-action-button.neutral {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.task-action-button.warning {
  border: 1px solid #f59e0b;
  background: #fffbeb;
  color: #92400e;
}

.task-action-button.danger {
  border: 1px solid #fecaca;
  background: #ffffff;
  color: #dc2626;
}

.task-action-button.neutral:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 8%);
}

.task-action-button.warning:hover:not(:disabled) {
  border-color: #f59e0b;
  background: #fef3c7;
  box-shadow: 0 0 0 3px rgb(245 158 11 / 10%);
}

.task-action-button.danger:hover:not(:disabled) {
  border-color: #dc2626;
  background: #fef2f2;
  box-shadow: 0 0 0 3px rgb(220 38 38 / 8%);
}

.task-action-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.drawer-content {
  display: grid;
  gap: 16px;
  padding: 18px 24px 28px;
}

.detail-section {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px;
  background: #ffffff;
}

.detail-section h3 {
  margin: 0 0 12px;
  color: #0f172a;
  font-size: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-grid label,
.raw-error-body label {
  display: grid;
  gap: 6px;
  color: #64748b;
  font-size: 12px;
}

.info-grid span,
.raw-error-body span {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #0f172a;
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 12px;
  border: 1px solid #e2e8f0;
}

.status-pill.success {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.status-pill.running {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.status-pill.queued {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #475569;
}

.status-pill.blocked {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.status-pill.failed {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.status-pill.cancelled,
.status-pill.pending {
  background: #f8fafc;
  border-color: #e2e8f0;
  color: #64748b;
}

.friendly-error {
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 12px;
  background: #fffbeb;
}

.friendly-error strong {
  display: block;
  margin-bottom: 6px;
  color: #92400e;
  font-size: 13px;
}

.friendly-error p {
  margin: 0;
  color: #92400e;
  font-size: 13px;
  line-height: 1.6;
}

.raw-error {
  margin-top: 12px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  padding: 10px 12px;
  background: #fef2f2;
}

.raw-error summary {
  cursor: pointer;
  color: #991b1b;
  font-size: 13px;
}

.raw-error-body {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.raw-error pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
  color: #7f1d1d;
  font-size: 12px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.section-title-row h3 {
  margin: 0;
}

.copy-button {
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  padding: 5px 10px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  font-size: 12px;
}

.copy-button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.copy-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.json-box {
  max-height: 360px;
  overflow: auto;
  margin: 0;
  border-radius: 12px;
  padding: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.drawer-empty,
.drawer-error {
  margin: 20px 24px;
  border-radius: 14px;
  padding: 24px;
  text-align: center;
}

.drawer-empty {
  color: #64748b;
  background: #f8fafc;
}

.drawer-error {
  color: #991b1b;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

@media (max-width: 760px) {
  .drawer-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .drawer-header-actions {
    width: 100%;
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}

.checksum {
  font-family: Consolas, Monaco, monospace;
  word-break: break-all;
}

.full-row {
  grid-column: 1 / -1;
}

.full-row {
  grid-column: 1 / -1;
}

.checksum {
  font-family: Consolas, Monaco, monospace;
  word-break: break-all;
}

.technical-section {
  background: #f8fafc;
}

.technical-section details {
  width: 100%;
}

.technical-section summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  list-style: none;
  font-weight: 700;
  color: #0f172a;
}

.technical-section summary::-webkit-details-marker {
  display: none;
}

.technical-section summary::after {
  content: '展开';
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.technical-section details[open] summary::after {
  content: '收起';
}

.technical-section summary small {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.payload-toolbar {
  margin-top: 12px;
  align-items: flex-start;
}

.payload-toolbar p {
  margin: 0;
  max-width: 680px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}
</style>


