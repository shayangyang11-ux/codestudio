<script setup lang="ts">
import type { AgentTab } from '../types/codemind'

const props = defineProps<{
  activeTab: AgentTab
  projectCode?: string | null
}>()

const emit = defineEmits<{
  (e: 'change-tab', tab: AgentTab): void
}>()
</script>

<template>
  <header class="app-header">
    <div class="brand">
      <div class="brand-logo">CM</div>
      <div>
        <div class="brand-title">CodeMind Studio</div>
        <div class="brand-subtitle">Enterprise Multi-Agent Development Platform</div>
      </div>
    </div>

    <nav class="agent-tabs">
      <button
        class="agent-tab"
        :class="{ active: activeTab === 'requirement' }"
        @click="emit('change-tab', 'requirement')"
      >
        需求 Agent
      </button>
      <button
        class="agent-tab"
        :class="{ active: activeTab === 'development' }"
        @click="emit('change-tab', 'development')"
      >
        开发 Agent
      </button>
      <button
        class="agent-tab"
        :class="{ active: activeTab === 'testing' }"
        @click="emit('change-tab', 'testing')"
      >
        测试 Agent
      </button>
    </nav>

    <div class="project-badge">
      Project：{{ props.projectCode || '-' }}
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #101828;
  color: #fff;
  border-bottom: 1px solid #1d2939;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.brand-title {
  font-size: 16px;
  font-weight: 700;
}

.brand-subtitle {
  font-size: 12px;
  color: #98a2b3;
}

.agent-tabs {
  display: flex;
  gap: 8px;
}

.agent-tab {
  border: 1px solid #344054;
  background: #1d2939;
  color: #d0d5dd;
  padding: 9px 18px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 14px;
}

.agent-tab.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.project-badge {
  font-size: 13px;
  color: #d0d5dd;
  background: #1d2939;
  border: 1px solid #344054;
  padding: 7px 12px;
  border-radius: 8px;
}
</style>
