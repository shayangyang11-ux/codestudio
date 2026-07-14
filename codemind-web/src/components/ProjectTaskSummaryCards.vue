<template>
  <section class="summary-section">
    <div class="summary-header">
      <div>
        <h2>项目任务总览</h2>
        <p>当前项目范围内 Agent 任务的实时统计。</p>
      </div>

      <button
        class="secondary-button"
        :disabled="loading"
        @click="$emit('refresh')"
      >
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div class="summary-grid">
      <div
        v-for="card in cards"
        :key="card.key"
        class="summary-card"
        :class="card.tone"
      >
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectAgentTaskStatusSummary } from '../types/codemind'

const props = defineProps<{
  summary: ProjectAgentTaskStatusSummary | null
  loading?: boolean
}>()

defineEmits<{
  refresh: []
}>()

const cards = computed(() => {
  const summary = props.summary

  return [
    {
      key: 'total',
      label: '总任务',
      value: summary?.total || 0,
      tone: 'neutral',
    },
    {
      key: 'active',
      label: '活跃任务',
      value: summary?.active || 0,
      tone: 'blue',
    },
    {
      key: 'queued',
      label: '排队中',
      value: summary?.queued || 0,
      tone: 'blue',
    },
    {
      key: 'running',
      label: '生成中',
      value: summary?.running || 0,
      tone: 'green',
    },
    {
      key: 'blocked',
      label: '等待依赖',
      value: summary?.blocked || 0,
      tone: 'amber',
    },
    {
      key: 'failed',
      label: '失败',
      value: summary?.failed || 0,
      tone: 'red',
    },
    {
      key: 'timeout',
      label: '超时',
      value: summary?.timeout || 0,
      tone: 'red',
    },
    {
      key: 'success',
      label: '已完成',
      value: (summary?.success || 0) + (summary?.partialSuccess || 0),
      tone: 'green',
    },
  ]
})
</script>

<style scoped>
.summary-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 20px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.summary-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #0f172a;
}

.summary-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  border-radius: 14px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.summary-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.summary-card strong {
  font-size: 24px;
  color: #0f172a;
}

.summary-card.blue {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.summary-card.green {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.summary-card.amber {
  background: #fffbeb;
  border-color: #fde68a;
}

.summary-card.red {
  background: #fef2f2;
  border-color: #fecaca;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 9px 14px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.secondary-button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.secondary-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>