<template>
  <section class="theme-history-panel">
    <header>
      <div>
        <p class="eyebrow">Theme Provenance</p>
        <h4>风格应用记录</h4>
      </div>

      <button
        type="button"
        :disabled="isLoading || isRollingBack"
        @click="loadLogs"
      >
        刷新
      </button>
    </header>

    <div
      v-if="errorMessage"
      class="message error"
    >
      {{ errorMessage }}
    </div>

    <div
      v-if="successMessage"
      class="message success"
    >
      {{ successMessage }}
    </div>

    <div
      v-if="isLoading"
      class="empty"
    >
      正在加载风格记录...
    </div>

    <div
      v-else-if="!logs.length"
      class="empty"
    >
      暂无风格应用记录。后续手动保存或使用风格意图助手应用后，会在这里留下来源记录。
    </div>

    <ol v-else>
      <li
        v-for="log in logs"
        :key="log.id"
      >
        <div class="log-main">
          <strong>{{ sourceLabel(log.source) }}</strong>
          <span>{{ formatDate(log.createdAt) }}</span>
        </div>

        <p v-if="log.summary">{{ log.summary }}</p>

        <div class="badges">
          <em v-if="log.agentMode">{{ log.agentMode }}</em>
          <em v-if="log.confidence != null">置信度 {{ Math.round(log.confidence * 100) }}%</em>
          <em v-if="log.changedFields.length">变更 {{ log.changedFields.join(' / ') }}</em>
        </div>

        <div class="log-actions">
          <button
            type="button"
            class="rollback-button"
            :disabled="isRollingBack || !canRollback(log)"
            @click="rollback(log)"
          >
            {{ rollingBackLogId === log.id ? '回滚中...' : '回滚到此版本' }}
          </button>
          <span v-if="!canRollback(log)">该记录缺少 beforeProfile，不能回滚。</span>
        </div>

        <details v-if="log.styleText || log.reasons.length || log.warnings.length">
          <summary>查看依据</summary>
          <blockquote v-if="log.styleText">{{ log.styleText }}</blockquote>
          <ul v-if="log.reasons.length">
            <li
              v-for="reason in log.reasons"
              :key="reason"
            >
              {{ reason }}
            </li>
          </ul>
          <ul
            v-if="log.warnings.length"
            class="warnings"
          >
            <li
              v-for="warning in log.warnings"
              :key="warning"
            >
              {{ warning }}
            </li>
          </ul>
        </details>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import {
  onMounted,
  ref,
  watch,
} from 'vue'
import {
  listProjectThemeProfileApplicationLogs,
  rollbackProjectThemeProfileToApplicationLog,
} from '../api/themeProfileProvenanceApi'
import type {
  ProjectThemeProfile,
} from '../types/themeProfile'
import type {
  ProjectThemeProfileApplicationLogResponse,
  ProjectThemeProfileApplicationSource,
} from '../types/themeProfileProvenance'

const props = defineProps<{
  workspaceId: string
  projectCode: string
  refreshKey?: number
}>()

const emit = defineEmits<{
  rollbackApplied: [profile: ProjectThemeProfile]
}>()

const logs = ref<ProjectThemeProfileApplicationLogResponse[]>([])
const isLoading = ref(false)
const isRollingBack = ref(false)
const rollingBackLogId = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

watch(
  () => [props.workspaceId, props.projectCode, props.refreshKey],
  () => {
    void loadLogs()
  },
)

onMounted(() => {
  void loadLogs()
})

async function loadLogs() {
  if (!props.workspaceId || !props.projectCode) {
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    logs.value = await listProjectThemeProfileApplicationLogs(
      props.workspaceId,
      props.projectCode,
      20,
    )
  } catch (error) {
    errorMessage.value = resolveErrorMessage(
      error,
      '加载风格应用记录失败。',
    )
  } finally {
    isLoading.value = false
  }
}

async function rollback(
  log: ProjectThemeProfileApplicationLogResponse,
) {
  if (!canRollback(log)) {
    return
  }

  const confirmed = window.confirm(
    '确认要回滚到这条风格记录应用前的项目风格吗？该操作会写入新的回滚记录。',
  )

  if (!confirmed) {
    return
  }

  isRollingBack.value = true
  rollingBackLogId.value = log.id
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const response = await rollbackProjectThemeProfileToApplicationLog(
      props.workspaceId,
      props.projectCode,
      log.id,
    )

    successMessage.value = '已回滚项目风格，并写入回滚来源记录。'
    emit('rollbackApplied', response.resultProfile)
    await loadLogs()
  } catch (error) {
    errorMessage.value = resolveErrorMessage(
      error,
      '回滚项目风格失败。',
    )
  } finally {
    isRollingBack.value = false
    rollingBackLogId.value = null
  }
}

function canRollback(
  log: ProjectThemeProfileApplicationLogResponse,
): boolean {
  return Boolean(log.beforeProfile && Object.keys(log.beforeProfile).length)
}

function sourceLabel(source: ProjectThemeProfileApplicationSource): string {
  const labels: Record<ProjectThemeProfileApplicationSource, string> = {
    MANUAL: '手动保存',
    STYLE_INTENT_AGENT: '风格意图 Agent',
    STYLE_INTENT_RULE_FALLBACK: '规则兜底',
    REFERENCE_STYLE_AGENT: '参考风格 Agent',
    SYSTEM: '系统默认',
    THEME_PROFILE_ROLLBACK: '风格回滚',
  }

  return labels[source] || source
}

function formatDate(value: string) {
  if (!value) {
    return ''
  }

  return new Date(value).toLocaleString()
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
.theme-history-panel {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.theme-history-panel header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h4 {
  margin: 0;
  color: #0f172a;
}

button {
  height: 32px;
  border: 1px solid #dbe4ef;
  border-radius: 10px;
  padding: 0 11px;
  background: #ffffff;
  color: #334155;
  font-weight: 800;
  cursor: pointer;
}

button:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.empty,
.message {
  border-radius: 12px;
  padding: 11px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
  background: #ffffff;
}

.message.error {
  background: #fef2f2;
  color: #b91c1c;
}

.message.success {
  margin-bottom: 10px;
  background: #f0fdf4;
  color: #166534;
}

ol {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

ol > li {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.log-main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.log-main strong {
  color: #0f172a;
  font-size: 13px;
}

.log-main span,
p,
summary,
blockquote,
li,
.log-actions span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

p {
  margin: 8px 0 0;
}

.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.badges em {
  border-radius: 999px;
  padding: 3px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.log-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 10px;
}

.rollback-button {
  border-color: #cbd5e1;
  background: #fff7ed;
  color: #9a3412;
}

details {
  margin-top: 9px;
}

summary {
  cursor: pointer;
  font-weight: 800;
}

blockquote {
  margin: 8px 0;
  border-left: 3px solid #bfdbfe;
  padding-left: 9px;
}

ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

.warnings li {
  color: #b45309;
}
</style>
