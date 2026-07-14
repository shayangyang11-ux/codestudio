<script setup lang="ts">
import type { AssistantStep } from '../types/codemind'

defineProps<{
  steps: AssistantStep[]
  lastError?: string
}>()
</script>

<template>
  <aside class="assistant-panel">
    <div class="panel-header">
      <div class="panel-title">AI Assistant</div>
      <div class="panel-subtitle">任务执行进度</div>
    </div>

    <div class="steps">
      <div
        v-for="step in steps"
        :key="step.id"
        class="step"
        :class="step.status"
      >
        <div class="step-dot"></div>
        <div>
          <div class="step-title">{{ step.title }}</div>
          <div class="step-desc">{{ step.description }}</div>
        </div>
      </div>
    </div>

    <div v-if="lastError" class="error-box">
      {{ lastError }}
    </div>
  </aside>
</template>

<style scoped>
.assistant-panel {
  width: 320px;
  min-width: 320px;
  height: 100%;
  background: #ffffff;
  border-left: 1px solid #e4e7ec;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 18px;
  border-bottom: 1px solid #e4e7ec;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #101828;
}

.panel-subtitle {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.steps {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.step {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e4e7ec;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-top: 5px;
  background: #d0d5dd;
}

.step.running .step-dot {
  background: #2563eb;
}

.step.success .step-dot {
  background: #16a34a;
}

.step.failed .step-dot {
  background: #dc2626;
}

.step-title {
  font-size: 14px;
  font-weight: 700;
  color: #101828;
}

.step-desc {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.error-box {
  margin: 0 18px 18px;
  padding: 12px;
  border-radius: 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.6;
}
</style>