<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="preview-mask"
      @click.self="$emit('close')"
    >
      <aside class="preview-drawer">
        <header class="preview-header">
          <div>
            <p class="eyebrow">交互原型 / UI_CONTRACT</p>
            <h2>{{ title }}</h2>
            <span>
              Mock Preview Runtime · 同一份契约将作为后续开发 Agent 真实页面生成蓝图
            </span>
          </div>

          <button
            class="close-button"
            @click="$emit('close')"
          >
            ×
          </button>
        </header>

        <div class="contract-notice">
          当前为需求阶段 Mock 预览。数据、提交、删除、审批等动作均为模拟；但页面布局、字段、按钮、校验规则、接口绑定和交互流程会作为正式开发约束。
        </div>

        <div
          v-if="contract"
          class="preview-tabs"
        >
          <button
            :class="{ active: activeTab === 'runtime' }"
            @click="activeTab = 'runtime'"
          >
            Mock 预览
          </button>

          <button
            :class="{ active: activeTab === 'contract' }"
            @click="activeTab = 'contract'"
          >
            契约说明
          </button>
        </div>

        <UiContractPreviewRuntime
          v-if="contract && activeTab === 'runtime'"
          :contract="contract"
        />

        <UiContractView
          v-else-if="contract && activeTab === 'contract'"
          :contract="contract"
        />

        <div
          v-else
          class="empty-box"
        >
          <strong>当前资产不是有效的 UI_CONTRACT，无法预览。</strong>
          <span>请先在 PAGE 页面节点生成交互原型，或确认资产 content 中包含 pages、routes 和 mockData。</span>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  computed,
  ref,
  watch,
} from 'vue'
import type { ArtifactResponse } from '../types/codemind'
import type { UiContractContent } from '../types/uiContract'
import UiContractPreviewRuntime from './UiContractPreviewRuntime.vue'
import UiContractView from './UiContractView.vue'

const props = defineProps<{
  visible: boolean
  artifact: ArtifactResponse | null
}>()

defineEmits<{
  close: []
}>()

const activeTab = ref<'runtime' | 'contract'>('runtime')

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      activeTab.value = 'runtime'
    }
  },
)

const contract = computed<UiContractContent | null>(() => {
  const content = props.artifact?.content

  if (!content || typeof content !== 'object') {
    return null
  }

  const candidate = content as UiContractContent

  if (
    candidate.artifactType !== 'UI_CONTRACT' &&
    props.artifact?.artifactType !== 'UI_CONTRACT'
  ) {
    return null
  }

  if (!Array.isArray(candidate.pages) || !candidate.pages.length) {
    return null
  }

  return {
    schemaVersion: candidate.schemaVersion || '1.0',
    artifactType: 'UI_CONTRACT',
    module: candidate.module || {},
    routes: Array.isArray(candidate.routes) ? candidate.routes : [],
    pages: candidate.pages,
    mockData: candidate.mockData || {},
    apiBindings: Array.isArray(candidate.apiBindings) ? candidate.apiBindings : [],
    developmentRules: candidate.developmentRules || {},
    analysisSummary: candidate.analysisSummary || '',
  }
})

const title = computed(() => {
  const moduleName =
    contract.value?.module?.module_name ||
    contract.value?.module?.moduleName ||
    contract.value?.pages?.[0]?.pageName ||
    contract.value?.pages?.[0]?.title ||
    props.artifact?.title ||
    '交互原型'

  return String(moduleName)
})
</script>

<style scoped>
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.35);
}

.preview-drawer {
  width: min(1280px, 96vw);
  height: 100vh;
  overflow: auto;
  background: #f8fafc;
  box-shadow: -16px 0 40px rgba(15, 23, 42, 0.18);
}

.preview-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
}

.eyebrow {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.preview-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.preview-header span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.close-button {
  width: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #ffffff;
  color: #334155;
  font-size: 20px;
  cursor: pointer;
}

.contract-notice {
  margin: 16px 24px 0;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  padding: 12px 14px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 13px;
  line-height: 1.7;
}

.preview-tabs {
  display: flex;
  gap: 8px;
  margin: 14px 24px 0;
  border-bottom: 1px solid #e2e8f0;
}

.preview-tabs button {
  border: none;
  border-bottom: 2px solid transparent;
  padding: 10px 12px;
  background: transparent;
  color: #64748b;
  font-weight: 800;
  cursor: pointer;
}

.preview-tabs button.active {
  border-color: #2563eb;
  color: #1d4ed8;
}

.empty-box {
  display: grid;
  gap: 8px;
  margin: 24px;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  padding: 24px;
  background: #ffffff;
  color: #64748b;
}

.empty-box strong {
  color: #0f172a;
}
</style>
