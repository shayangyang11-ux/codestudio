<template>
  <Teleport to="body">
    <section
      v-if="visible && contract"
      class="prototype-studio-mask"
      role="dialog"
      aria-modal="true"
      aria-label="Prototype Studio 原型工作台"
    >
      <header class="prototype-studio-topbar">
        <div class="prototype-studio-title">
          <button type="button" class="studio-back-button" @click="emit('close')">
            ← 返回需求工作区
          </button>

          <div>
            <p>Prototype Studio</p>
            <h2>{{ pageTitle || contract.page?.title || contract.page?.pageName || '交互原型' }}</h2>
            <div class="prototype-studio-lifecycle-line">
              <span>{{ selectedDevice.label }}真实视口 · {{ selectedDevice.width }} × {{ selectedDevice.height }} · 工作台缩放 {{ Math.round(effectiveScale * 100) }}%</span>
            </div>
          </div>
        </div>

        <div class="prototype-studio-actions">
          <label class="prototype-zoom-select" aria-label="缩放比例">
            <span>缩放</span>
            <select v-model="zoomMode">
              <option
                v-for="option in zoomOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </label>

          <button
            type="button"
            class="studio-ghost-button"
            :class="{ active: inspectMode }"
            @click="inspectMode = !inspectMode"
          >
            {{ inspectMode ? '退出选择' : '选择页面元素' }}
          </button>

          <button type="button" class="studio-ghost-button" @click="handleClearSelection">
            清除选择
          </button>

          <button
            v-if="prototypeEditDraftDirty"
            type="button"
            class="studio-ghost-button"
            @click="emit('discardDraft')"
          >
            放弃
          </button>

          <button
            type="button"
            class="studio-primary-button"
            :disabled="!prototypeEditDraftDirty || saving"
            @click="emit('saveDraft')"
          >
            {{ saving ? '保存中...' : prototypeEditDraftDirty ? '保存为新版本' : '暂无修改' }}
          </button>

          <button type="button" class="studio-close-button" @click="emit('close')">
            ×
          </button>
        </div>
      </header>

      <main class="prototype-studio-body">
        <section ref="stageRef" class="prototype-studio-stage">
          <div class="prototype-studio-grid-bg" />

          <div class="prototype-studio-artboard-frame" :style="artboardFrameStyle">
            <div class="prototype-studio-artboard-scale" :style="artboardScaleStyle">
              <div class="prototype-studio-runtime-host">
              <UiContractPreviewRuntime
                :key="runtimeKey"
                :contract="contract"
                :project-theme-profile="projectThemeProfile || null"
                :focus-component-id="focusedComponentId || null"
                :focus-element-selection="focusedElementSelection || null"
                :inspect-component-mode="inspectMode"
                display-mode="artboard"
                @select-component="handleSelectComponent"
                @exit-inspect-mode="inspectMode = false"
                @clear-selection="handleClearSelection"
              />
              </div>
            </div>
          </div>
        </section>

        <aside class="prototype-copilot-panel">
          <header>
            <div>
              <p>Prototype Copilot</p>
              <h3>原型编辑助手</h3>
            </div>
            <span :class="{ dirty: prototypeEditDraftDirty }">
              {{ prototypeEditDraftDirty ? '未保存' : '已同步' }}
            </span>
          </header>

          <section class="copilot-selection-card" :class="{ empty: !focusedComponentId }">
            <span>当前选中</span>
            <strong>{{ selectionDisplayLabel }}</strong>
            <p>
              {{ focusedComponentId ? '下面输入指令会优先修改这个元素。' : '点击“选择页面元素”，再在画布中点字段、按钮、表格列或文案。' }}
            </p>
          </section>

          <section v-if="quickActions.length" class="copilot-quick-actions">
            <span>快捷操作</span>
            <div>
              <button
                v-for="action in quickActions"
                :key="action.key"
                type="button"
                @click="handleQuickAction(action)"
              >
                {{ action.label }}
              </button>
            </div>
          </section>

          <section class="copilot-input-card">
            <label for="prototype-copilot-input">编辑指令</label>
            <textarea
              id="prototype-copilot-input"
              v-model="instructionText"
              rows="4"
              placeholder="例如：把这个按钮改成绿色，并放大一点"
              @keydown.enter.exact.prevent="submitInstruction"
            />
            <button
              type="button"
              class="studio-primary-button full"
              :disabled="!instructionText.trim()"
              @click="submitInstruction"
            >
              发送到当前原型
            </button>
            <p>提示：按 Enter 发送，Shift + Enter 换行。</p>
          </section>

          <section v-if="prototypeEditDraftSummary || prototypeEditDraftDirty" class="copilot-result-card">
            <span>最近修改</span>
            <strong>{{ prototypeEditDraftSummary || '原型草稿已更新' }}</strong>
            <p>
              当前阶段：{{ prototypeVersionLabel }} · {{ prototypePublicationLabel }}。
              保存后会进入 PAGE_UI_CONTRACT 版本链路；Development Agent 只使用模块汇总原型确认后的开发基线。
            </p>
          </section>

          <footer class="prototype-copilot-footer">
            <span>{{ prototypeSizeLabel }}</span>
            <span>{{ prototypeComponentCount }} 个组件</span>
          </footer>
        </aside>
      </main>
    </section>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type { UiContractContent } from '../types/uiContract'
import UiContractPreviewRuntime from './UiContractPreviewRuntime.vue'

defineOptions({
  name: 'PrototypeStudioOverlay',
})

type PrototypeElementSelection = {
  componentId: string
  componentType?: string | null
  componentTitle?: string | null
  elementKind?: string | null
  elementLabel?: string | null
  fieldKey?: string | null
  actionId?: string | null
  columnKey?: string | null
  tabKey?: string | null
  textKey?: string | null
  textRole?: string | null
  elementPath?: string | null
}

type QuickAction = {
  key: string
  label: string
  instruction: string
  prompt?: string
  placeholder?: string
}

type DevicePreset = {
  key: string
  label: string
  width: number
  height: number
}

type ZoomMode = 'auto' | '50' | '67' | '75' | '80' | '90' | '100' | '110' | '125' 

const props = withDefaults(
  defineProps<{
    visible: boolean
    contract: UiContractContent | null
    pageTitle?: string
    projectThemeProfile?: ProjectThemeProfile | null
    focusedComponentId?: string | null
    focusedComponentLabel?: string
    focusedElementSelection?: PrototypeElementSelection | Record<string, unknown> | null
    prototypeEditDraftDirty?: boolean
    prototypeEditDraftSummary?: string
    prototypePublicationState?: 'DRAFT' | 'SAVED_UNPUBLISHED' | 'PUBLISHED' | 'EMPTY' | string
    prototypePublicationLabel?: string
    prototypeVersionLabel?: string
    canPublishPrototype?: boolean
    prototypeSizeLabel?: string
    prototypeComponentCount?: number
    saving?: boolean
    startInInspectMode?: boolean
  }>(),
  {
    pageTitle: '',
    projectThemeProfile: null,
    focusedComponentId: null,
    focusedComponentLabel: '',
    focusedElementSelection: null,
    prototypeEditDraftDirty: false,
    prototypeEditDraftSummary: '',
    prototypePublicationState: 'EMPTY',
    prototypePublicationLabel: '尚未生成',
    prototypeVersionLabel: '未生成版本',
    canPublishPrototype: false,
    prototypeSizeLabel: '',
    prototypeComponentCount: 0,
    saving: false,
    startInInspectMode: false,
  },
)

const emit = defineEmits<{
  close: []
  selectComponent: [payload: PrototypeElementSelection]
  clearSelection: []
  sendInstruction: [instruction: string]
  saveDraft: []
  discardDraft: []
}>()

const devicePresets: DevicePreset[] = [
  {
    key: 'desktop',
    label: '桌面网页',
    width: 1440,
    height: 900,
  },
  {
    key: 'admin',
    label: '后台系统',
    width: 1440,
    height: 960,
  },
]

const selectedDeviceKey = ref('desktop')
const zoomOptions: Array<{ value: ZoomMode; label: string }> = [
  { value: 'auto', label: '适配' },
  { value: '50', label: '50%' },
  { value: '67', label: '67%' },
  { value: '75', label: '75%' },
  { value: '80', label: '80%' },
  { value: '90', label: '90%' },
  { value: '100', label: '100%' },
  { value: '110', label: '110%' },
  { value: '125', label: '125%' },
]

const DESIGN_VIEWPORT = {
  width: 1440,
  height: 900,
} as const

const zoomMode = ref<ZoomMode>('100') // A9.10.13.0.6 DEFAULT 100 ZOOM
const inspectMode = ref(false)
const instructionText = ref('')
const stageRef = ref<HTMLElement | null>(null)
const stageSize = ref({
  width: 0,
  height: 0,
})

let resizeObserver: ResizeObserver | null = null

const selectedDevice = computed<DevicePreset>(() =>
  devicePresets.find((item) => item.key === selectedDeviceKey.value) || devicePresets[0],
)

const prototypePublicationState = computed(() =>
  String(props.prototypePublicationState || 'EMPTY'),
)

const prototypePublicationLabel = computed(() =>
  props.prototypePublicationLabel || '尚未生成',
)

const prototypeVersionLabel = computed(() =>
  props.prototypeVersionLabel || '未生成版本',
)

const canPublishPrototype = computed(() =>
  Boolean(props.canPublishPrototype),
)

const fitScale = computed(() => {
  if (!stageSize.value.width || !stageSize.value.height) {
    return 0.75
  }

  const availableWidth = Math.max(stageSize.value.width - 48, 360)
  const availableHeight = Math.max(stageSize.value.height - 48, 360)
  const scale = Math.min(
    availableWidth / DESIGN_VIEWPORT.width,
    availableHeight / DESIGN_VIEWPORT.height,
  )

  return Math.min(Math.max(scale, 0.35), 1)
})

const effectiveScale = computed(() => {
  if (zoomMode.value === 'auto') {
    return fitScale.value
  }

  const value = Number(zoomMode.value)
  if (!Number.isFinite(value) || value <= 0) {
    return 0.75
  }

  return value / 100
})

const artboardFrameStyle = computed(() => {
  const width = DESIGN_VIEWPORT.width * effectiveScale.value
  const height = DESIGN_VIEWPORT.height * effectiveScale.value

  return {
    width: `${width}px`,
    height: `${height}px`,
    minWidth: `${width}px`,
    minHeight: `${height}px`,
    maxWidth: `${width}px`,
    maxHeight: `${height}px`,
  }
})

const artboardScaleStyle = computed(() => ({
  width: `${DESIGN_VIEWPORT.width}px`,
  height: `${DESIGN_VIEWPORT.height}px`,
  minWidth: `${DESIGN_VIEWPORT.width}px`,
  minHeight: `${DESIGN_VIEWPORT.height}px`,
  maxWidth: `${DESIGN_VIEWPORT.width}px`,
  maxHeight: `${DESIGN_VIEWPORT.height}px`,
  transform: `scale(${effectiveScale.value})`,
  '--prototype-device-width': `${DESIGN_VIEWPORT.width}px`,
  '--prototype-device-height': `${DESIGN_VIEWPORT.height}px`,
}))

const runtimeKey = computed(() => {
  const contract = props.contract

  return [
    contract?.identity?.pageId,
    contract?.identity?.pageKey,
    contract?.page?.pageId,
    contract?.page?.pageKey,
    contract?.page?.pageName,
    contract?.page?.route,
    props.prototypeEditDraftDirty ? 'draft' : 'saved',
  ]
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .join('::') || 'prototype-studio-runtime'
})

const selectionRecord = computed<Record<string, unknown>>(() => {
  const value = props.focusedElementSelection
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
})

const selectionKind = computed(() =>
  String(
    selectionRecord.value.elementKind ||
      selectionRecord.value.kind ||
      selectionRecord.value.componentType ||
      '',
  ).toUpperCase(),
)

const selectionDisplayLabel = computed(() => {
  if (props.focusedComponentLabel) {
    return props.focusedComponentLabel
  }

  const record = selectionRecord.value

  return String(
    record.elementLabel ||
      record.fieldKey ||
      record.actionId ||
      record.columnKey ||
      record.tabKey ||
      record.textKey ||
      record.componentTitle ||
      props.focusedComponentId ||
      '尚未选择元素',
  )
})

const quickActions = computed<QuickAction[]>(() => {
  if (!props.focusedComponentId) {
    return []
  }

  const kind = selectionKind.value
  const label = readableDefault(selectionDisplayLabel.value)

  if (kind.includes('FIELD') || kind.includes('INPUT') || kind.includes('CONTROL')) {
    return [
      {
        key: 'field-label',
        label: '改字段名',
        instruction: '把这个字段改成',
        prompt: '请输入新的字段名',
        placeholder: label,
      },
      {
        key: 'field-placeholder',
        label: '改提示文案',
        instruction: '把这个输入框 placeholder 改成',
        prompt: '请输入新的 placeholder',
        placeholder: `请输入${label}`,
      },
      {
        key: 'field-required',
        label: '设为必填',
        instruction: '把这个字段设为必填',
      },
      {
        key: 'field-optional',
        label: '设为非必填',
        instruction: '把这个字段设为非必填',
      },
    ]
  }

  if (kind.includes('ACTION') || kind.includes('BUTTON') || kind.includes('LINK')) {
    return [
      {
        key: 'button-text',
        label: '改按钮文案',
        instruction: '把这个按钮文案改成',
        prompt: '请输入新的按钮文案',
        placeholder: label,
      },
      {
        key: 'button-primary',
        label: '蓝色主按钮',
        instruction: '把这个按钮改成蓝色主按钮',
      },
      {
        key: 'button-green',
        label: '改成绿色',
        instruction: '把这个按钮改成绿色',
      },
      {
        key: 'button-large',
        label: '放大一点',
        instruction: '把这个按钮放大一点',
      },
    ]
  }

  if (kind.includes('COLUMN') || kind.includes('CELL') || kind.includes('TABLE')) {
    return [
      {
        key: 'column-label',
        label: '改列名',
        instruction: '把这个表格列改成',
        prompt: '请输入新的列名',
        placeholder: label,
      },
      {
        key: 'column-wide',
        label: '加宽一点',
        instruction: '把这个表格列加宽一点',
      },
      {
        key: 'column-narrow',
        label: '收窄一点',
        instruction: '把这个表格列收窄一点',
      },
    ]
  }

  return [
    {
      key: 'text-change',
      label: '改文案',
      instruction: '把这个元素文案改成',
      prompt: '请输入新的文案',
      placeholder: label,
    },
    {
      key: 'text-blue',
      label: '改成蓝色',
      instruction: '把这个元素改成蓝色',
    },
    {
      key: 'text-large',
      label: '放大一点',
      instruction: '把这个元素放大一点',
    },
  ]
})

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      inspectMode.value = props.startInInspectMode
      zoomMode.value = '100' // A9.10.13.0.6 DEFAULT 100 ZOOM
      await nextTick()
      ensureResizeObserver()
      return
    }

    disconnectResizeObserver()
    inspectMode.value = false
    instructionText.value = ''
  },
)

watch(
  () => props.startInInspectMode,
  (value) => {
    if (props.visible && value) {
      inspectMode.value = true
    }
  },
)

onBeforeUnmount(() => {
  disconnectResizeObserver()
})

function ensureResizeObserver() {
  disconnectResizeObserver()

  if (!stageRef.value) {
    return
  }

  resizeObserver = new ResizeObserver(() => {
    refreshStageSize()
  })

  resizeObserver.observe(stageRef.value)
  refreshStageSize()
}

function disconnectResizeObserver() {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
}

function refreshStageSize() {
  if (!stageRef.value) {
    return
  }

  const rect = stageRef.value.getBoundingClientRect()
  stageSize.value = {
    width: rect.width,
    height: rect.height,
  }
}

function handleSelectComponent(payload: PrototypeElementSelection) {
  emit('selectComponent', payload)
  inspectMode.value = false
}

function handleClearSelection() {
  inspectMode.value = false
  emit('clearSelection')
}

function submitInstruction() {
  const text = instructionText.value.trim()

  if (!text) {
    return
  }

  emit('sendInstruction', text)
  instructionText.value = ''
  clearSelectionAfterInstruction()
}

function handleQuickAction(action: QuickAction) {
  let instruction = action.instruction

  if (action.prompt) {
    const value = window.prompt(action.prompt, action.placeholder || '')
    if (!value || !value.trim()) {
      return
    }

    instruction = `${instruction}“${value.trim()}”`
  }

  emit('sendInstruction', instruction)
  clearSelectionAfterInstruction()
}

function clearSelectionAfterInstruction() {
  inspectMode.value = false

  window.setTimeout(() => {
    emit('clearSelection')
  }, 0)
}

function readableDefault(value: unknown): string {
  const text = String(value || '')
    .replace(/^[A-Z_]+\s*[·:-]\s*/i, '')
    .replace(/^已选中\s*[·:-]\s*/i, '')
    .trim()

  return text || '当前元素'
}
// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: publication flags are intentionally preserved for alternate footer variants.
void prototypePublicationState
void canPublishPrototype

</script>

<style scoped>
.prototype-studio-mask {
  position: fixed;
  inset: 0;
  z-index: 2050;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  background:
    radial-gradient(circle at 12% 8%, rgba(37, 99, 235, 0.18), transparent 28%),
    linear-gradient(135deg, #0f172a 0%, #111827 45%, #020617 100%);
  color: #ffffff;
}

.prototype-studio-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.28);
  padding: 12px 16px;
  background: rgba(15, 23, 42, 0.86);
  backdrop-filter: blur(16px);
}

.prototype-studio-title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 14px;
}

.prototype-studio-title p {
  margin: 0 0 3px;
  color: #93c5fd;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.prototype-studio-title h2 {
  margin: 0;
  overflow: hidden;
  color: #ffffff;
  font-size: 18px;
  font-weight: 950;
  letter-spacing: -0.03em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prototype-studio-title span,
.copilot-selection-card p,
.copilot-input-card p,
.copilot-result-card p,
.prototype-copilot-footer span {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.55;
}

.studio-back-button,
.studio-ghost-button,
.studio-primary-button,
.studio-close-button,
.studio-segmented-control button {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 999px;
  min-height: 34px;
  padding: 0 12px;
  background: rgba(15, 23, 42, 0.66);
  color: #e2e8f0;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.studio-back-button:hover,
.studio-ghost-button:hover,
.studio-ghost-button.active,
.studio-segmented-control button.active {
  border-color: rgba(147, 197, 253, 0.8);
  background: rgba(37, 99, 235, 0.22);
  color: #ffffff;
}

.prototype-studio-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.studio-segmented-control {
  display: inline-grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 999px;
  padding: 3px;
  background: rgba(2, 6, 23, 0.4);
}

.studio-segmented-control button {
  min-height: 28px;
  border: 0;
  padding: 0 10px;
  background: transparent;
}

.studio-primary-button {
  border-color: rgba(59, 130, 246, 0.86);
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.28);
}

.studio-primary-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

.studio-primary-button.full {
  width: 100%;
  justify-content: center;
}

.studio-close-button {
  width: 34px;
  padding: 0;
  border-radius: 12px;
  color: #ffffff;
  font-size: 22px;
  line-height: 1;
}

.prototype-studio-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  min-height: 0;
}

.prototype-studio-stage {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 0;
  place-items: center;
  overflow: auto;
  padding: 28px;
  overscroll-behavior: contain;
}

.prototype-studio-grid-bg {
  position: absolute;
  inset: 0;
  opacity: 0.26;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.18) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.18) 1px, transparent 1px);
  background-size: 36px 36px;
  pointer-events: none;
}

.prototype-studio-artboard-frame {
  position: relative;
  z-index: 1;
  overflow: hidden;
  border: 1px solid rgba(219, 234, 254, 0.35);
  border-radius: 24px;
  background: #ffffff;
  box-shadow:
    0 40px 120px rgba(0, 0, 0, 0.38),
    0 0 0 1px rgba(255, 255, 255, 0.08);
}

.prototype-studio-artboard-scale {
  transform-origin: top left;
}

.prototype-studio-artboard-scale :deep(.runtime-shell--artboard) {
  width: var(--prototype-device-width) !important;
  min-width: var(--prototype-device-width) !important;
  max-width: none !important;
  min-height: var(--prototype-device-height) !important;
  height: auto !important;
  box-sizing: border-box;
}

.prototype-studio-artboard-scale :deep(.artboard-runtime-main),
.prototype-studio-artboard-scale :deep(.pattern-runtime),
.prototype-studio-artboard-scale :deep(.business-page),
.prototype-studio-artboard-scale :deep(.auth-page) {
  width: var(--prototype-device-width) !important;
  min-width: var(--prototype-device-width) !important;
  max-width: none !important;
  min-height: var(--prototype-device-height) !important;
  box-sizing: border-box;
}

.prototype-studio-artboard-scale :deep(.business-body),
.prototype-studio-artboard-scale :deep(.page-canvas) {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}

.prototype-studio-artboard-scale :deep(.business-page--dashboard .business-body),
.prototype-studio-artboard-scale :deep(.business-page--list .business-body),
.prototype-studio-artboard-scale :deep(.business-page--detail .business-body),
.prototype-studio-artboard-scale :deep(.business-page--form .business-body) {
  width: 100%;
  max-width: none;
}

.prototype-copilot-panel {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, auto) auto 1fr auto;
  gap: 12px;
  min-height: 0;
  overflow: auto;
  border-left: 1px solid rgba(148, 163, 184, 0.24);
  padding: 16px;
  background: rgba(15, 23, 42, 0.9);
  box-shadow: -24px 0 60px rgba(0, 0, 0, 0.22);
}

.prototype-copilot-panel > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.prototype-copilot-panel > header p,
.copilot-selection-card span,
.copilot-quick-actions > span,
.copilot-input-card label,
.copilot-result-card span {
  margin: 0;
  color: #93c5fd;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.prototype-copilot-panel > header h3 {
  margin: 3px 0 0;
  color: #ffffff;
  font-size: 17px;
}

.prototype-copilot-panel > header > span {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(22, 163, 74, 0.15);
  color: #86efac;
  font-size: 11px;
  font-weight: 950;
}

.prototype-copilot-panel > header > span.dirty {
  background: rgba(245, 158, 11, 0.18);
  color: #fcd34d;
}

.copilot-selection-card,
.copilot-input-card,
.copilot-result-card,
.copilot-quick-actions {
  display: grid;
  gap: 9px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 18px;
  padding: 13px;
  background: rgba(30, 41, 59, 0.72);
}

.copilot-selection-card.empty {
  border-style: dashed;
  background: rgba(15, 23, 42, 0.56);
}

.copilot-selection-card strong,
.copilot-result-card strong {
  overflow: hidden;
  color: #ffffff;
  font-size: 15px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copilot-quick-actions div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.copilot-quick-actions button {
  border: 1px solid rgba(147, 197, 253, 0.32);
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(37, 99, 235, 0.12);
  color: #bfdbfe;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.copilot-quick-actions button:hover {
  border-color: rgba(147, 197, 253, 0.9);
  background: rgba(37, 99, 235, 0.28);
  color: #ffffff;
}

.copilot-input-card textarea {
  width: 100%;
  min-height: 104px;
  resize: vertical;
  box-sizing: border-box;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 14px;
  padding: 11px 12px;
  background: rgba(2, 6, 23, 0.56);
  color: #ffffff;
  font-size: 13px;
  line-height: 1.6;
  outline: none;
}

.copilot-input-card textarea:focus {
  border-color: rgba(96, 165, 250, 0.92);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.24);
}

.prototype-copilot-footer {
  display: flex;
  gap: 8px;
  align-self: end;
}

.prototype-copilot-footer span {
  border-radius: 999px;
  padding: 5px 8px;
  background: rgba(148, 163, 184, 0.12);
}

@media (max-width: 1180px) {
  .prototype-studio-body {
    grid-template-columns: 1fr;
  }

  .prototype-copilot-panel {
    position: absolute;
    right: 16px;
    bottom: 16px;
    width: min(340px, calc(100vw - 32px));
    max-height: min(620px, calc(100vh - 96px));
    border: 1px solid rgba(148, 163, 184, 0.3);
    border-radius: 22px;
  }
}

/* A9.9.2 Preview Canvas Scaling: prototype studio shows a scaled real web viewport. */
.prototype-studio-artboard,
.prototype-studio-artboard-page {
  width: 1440px;
  min-width: 1440px;
  max-width: none;
  min-height: 900px;
}

.prototype-studio-artboard-scale {
  transform-origin: top center !important;
}

.prototype-studio-artboard-scale :deep(.a9-runtime-design-canvas),
.prototype-studio-artboard-scale :deep(.ui-runtime-shell) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: none !important;
  min-height: 900px !important;
  background: #f3f6fb !important;
}

.prototype-studio-artboard-scale :deep(.runtime-screen) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: none !important;
  min-height: 900px !important;
  overflow: visible !important;
}

.prototype-studio-artboard-scale :deep(.page-canvas) {
  width: 1440px !important;
  max-width: none !important;
  min-height: 900px !important;
  padding: 32px 40px 88px !important;
  box-sizing: border-box !important;
}

.prototype-studio-artboard-scale :deep(.business-page),
.prototype-studio-artboard-scale :deep(.business-body),
.prototype-studio-artboard-scale :deep(.runtime-component),
.prototype-studio-artboard-scale :deep(.runtime-node),
.prototype-studio-artboard-scale :deep(.component-card),
.prototype-studio-artboard-scale :deep(.node-card),
.prototype-studio-artboard-scale :deep(.component-form),
.prototype-studio-artboard-scale :deep(.node-form),
.prototype-studio-artboard-scale :deep(.component-table),
.prototype-studio-artboard-scale :deep(.node-table) {
  max-width: none !important;
  box-sizing: border-box !important;
}

.prototype-studio-artboard-scale :deep(.business-page),
.prototype-studio-artboard-scale :deep(.business-body),
.prototype-studio-artboard-scale :deep(.component-form),
.prototype-studio-artboard-scale :deep(.node-form),
.prototype-studio-artboard-scale :deep(.component-table),
.prototype-studio-artboard-scale :deep(.node-table) {
  width: 100% !important;
}

.prototype-studio-artboard-scale :deep(.form-grid) {
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
  gap: 18px 24px !important;
}

/* A9.9.5 Inline Fixed Viewport Zoom
   Prototype Studio renders a fixed 1440 × 900 real web viewport.
   The outer frame is scaled by preset zoom; long pages scroll inside the viewport. */
.prototype-zoom-control {
  max-width: min(720px, 55vw);
  overflow-x: auto;
}

.prototype-zoom-control button {
  white-space: nowrap;
}

.prototype-studio-stage {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 24px !important;
  overflow: auto !important;
  min-width: 0 !important;
  min-height: 0 !important;
}

.prototype-studio-artboard-frame {
  flex: 0 0 auto !important;
  overflow: hidden !important;
  border-radius: 18px !important;
  background: #ffffff !important;
}

.prototype-studio-artboard-scale {
  width: 1440px !important;
  height: 900px !important;
  min-width: 1440px !important;
  min-height: 900px !important;
  max-width: 1440px !important;
  max-height: 900px !important;
  overflow: hidden !important;
  transform-origin: top left !important;
  flex: 0 0 auto !important;
  background: #f5f7fb !important;
}

.prototype-studio-runtime-host :deep(.runtime-shell--artboard),
.prototype-studio-runtime-host :deep(.artboard-runtime-main),
.prototype-studio-runtime-host :deep(.pattern-runtime),
.prototype-studio-runtime-host :deep(.a9-runtime-design-canvas),
.prototype-studio-runtime-host :deep(.ui-runtime-shell),
.prototype-studio-runtime-host :deep(.runtime-screen) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: 1440px !important;
  height: 900px !important;
  min-height: 900px !important;
  max-height: 900px !important;
  overflow: auto !important;
  box-sizing: border-box !important;
}

.prototype-studio-runtime-host :deep(.business-page),
.prototype-studio-runtime-host :deep(.auth-page),
.prototype-studio-runtime-host :deep(.business-body),
.prototype-studio-runtime-host :deep(.page-canvas) {
  width: 100% !important;
  max-width: none !important;
  box-sizing: border-box !important;
}


/* A9.9.6 Prototype Studio Polish
   Only polish the studio shell: toolbar, zoom selector, stage, artboard shadow,
   and copilot panel. It does not change generated UI_CONTRACT page semantics. */
.prototype-studio-mask {
  grid-template-rows: 76px minmax(0, 1fr) !important;
  background:
    radial-gradient(circle at 14% 4%, rgba(59, 130, 246, 0.18), transparent 24%),
    linear-gradient(135deg, #08111f 0%, #0f172a 48%, #020617 100%) !important;
}

.prototype-studio-topbar {
  min-height: 76px !important;
  padding: 10px 18px !important;
  gap: 14px !important;
  align-items: center !important;
  border-bottom-color: rgba(148, 163, 184, 0.22) !important;
  background: rgba(8, 17, 33, 0.92) !important;
  box-shadow: 0 12px 30px rgba(2, 6, 23, 0.28) !important;
}

.prototype-studio-title {
  min-width: 0 !important;
  flex: 1 1 auto !important;
  align-items: center !important;
  gap: 12px !important;
}

.prototype-studio-title > div {
  min-width: 0 !important;
}

.prototype-studio-title p {
  margin-bottom: 2px !important;
  font-size: 10px !important;
  letter-spacing: 0.13em !important;
  color: #7dd3fc !important;
}

.prototype-studio-title h2 {
  max-width: min(520px, 34vw) !important;
  font-size: 18px !important;
  line-height: 1.15 !important;
}

.prototype-studio-title span {
  display: block !important;
  margin-top: 3px !important;
  color: rgba(203, 213, 225, 0.78) !important;
  font-size: 12px !important;
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
}

.studio-back-button,
.studio-ghost-button,
.studio-primary-button {
  height: 32px !important;
  min-height: 32px !important;
  padding: 0 12px !important;
  border-radius: 999px !important;
  font-size: 12px !important;
  line-height: 30px !important;
  white-space: nowrap !important;
}

.studio-close-button {
  width: 32px !important;
  height: 32px !important;
  min-height: 32px !important;
  border-radius: 999px !important;
  line-height: 30px !important;
}

.prototype-studio-actions {
  flex: 0 0 auto !important;
  display: flex !important;
  align-items: center !important;
  justify-content: flex-end !important;
  gap: 8px !important;
  min-width: 0 !important;
  max-width: min(720px, 56vw) !important;
  overflow: hidden !important;
  flex-wrap: nowrap !important;
}

.prototype-zoom-control {
  display: none !important;
}

.prototype-zoom-select {
  display: inline-flex !important;
  align-items: center !important;
  gap: 7px !important;
  height: 32px !important;
  flex: 0 0 auto !important;
  border: 1px solid rgba(148, 163, 184, 0.28) !important;
  border-radius: 999px !important;
  padding: 0 10px 0 12px !important;
  background: rgba(15, 23, 42, 0.82) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04) !important;
}

.prototype-zoom-select span {
  color: rgba(203, 213, 225, 0.72) !important;
  font-size: 12px !important;
  font-weight: 800 !important;
}

.prototype-zoom-select select {
  width: 76px !important;
  height: 26px !important;
  border: 0 !important;
  outline: none !important;
  border-radius: 999px !important;
  padding: 0 8px !important;
  background: #ffffff !important;
  color: #0f172a !important;
  font-size: 12px !important;
  font-weight: 900 !important;
  cursor: pointer !important;
  border: 1px solid rgba(203, 213, 225, 0.95) !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08) !important;
}

.prototype-studio-body {
  grid-template-columns: minmax(0, 1fr) 324px !important;
  min-height: 0 !important;
  overflow: hidden !important;
}

.prototype-studio-stage {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 22px 30px !important;
  overflow: auto !important;
  background:
    radial-gradient(circle at 50% 34%, rgba(96, 165, 250, 0.08), transparent 36%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.12), rgba(2, 6, 23, 0.1)) !important;
}

.prototype-studio-grid-bg {
  opacity: 0.18 !important;
  background-size: 40px 40px !important;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.16) 1px, transparent 1px) !important;
}

.prototype-studio-artboard-frame {
  border-radius: 20px !important;
  border: 1px solid rgba(226, 232, 240, 0.48) !important;
  background: #ffffff !important;
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.32),
    0 8px 24px rgba(15, 23, 42, 0.22),
    0 0 0 1px rgba(255, 255, 255, 0.08) !important;
}

.prototype-studio-artboard-scale {
  border-radius: 18px !important;
}

.prototype-copilot-panel {
  width: 324px !important;
  padding: 14px !important;
  gap: 11px !important;
  background: rgba(8, 17, 33, 0.94) !important;
  border-left-color: rgba(148, 163, 184, 0.22) !important;
}

.prototype-copilot-panel > header {
  padding-bottom: 10px !important;
}

.prototype-copilot-panel > header p,
.copilot-selection-card span,
.copilot-quick-actions span,
.copilot-input-card label,
.copilot-result-card span {
  font-size: 10px !important;
  letter-spacing: 0.12em !important;
  color: #7dd3fc !important;
}

.prototype-copilot-panel > header h3 {
  font-size: 16px !important;
}

.copilot-selection-card,
.copilot-input-card,
.copilot-result-card {
  border-radius: 18px !important;
  background: rgba(15, 23, 42, 0.58) !important;
  border-color: rgba(148, 163, 184, 0.22) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03) !important;
}

.copilot-input-card textarea {
  min-height: 88px !important;
  border-radius: 14px !important;
}

.prototype-copilot-footer {
  gap: 8px !important;
}

.prototype-copilot-footer span {
  border-radius: 999px !important;
  padding: 5px 9px !important;
}

@media (max-width: 1280px) {
  .prototype-studio-body {
    grid-template-columns: minmax(0, 1fr) 300px !important;
  }

  .prototype-copilot-panel {
    width: 300px !important;
  }

  .prototype-studio-title h2 {
    max-width: 360px !important;
  }
}


.prototype-zoom-select select option {
  background: #ffffff !important;
  color: #0f172a !important;
}


/* A9.9.10 Fixed Viewport Internal Scroll
   The artboard remains a fixed 1440 × 900 real browser viewport.
   Long generated pages scroll inside this viewport instead of being clipped. */
.prototype-studio-runtime-host {
  width: 1440px !important;
  height: 900px !important;
  min-width: 1440px !important;
  min-height: 900px !important;
  max-width: 1440px !important;
  max-height: 900px !important;
  overflow-y: auto !important;
  overflow-x: hidden !important;
  overscroll-behavior: contain !important;
  scrollbar-gutter: stable !important;
  border-radius: 18px !important;
  background: #f5f7fb !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar {
  width: 10px !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar-track {
  background: rgba(226, 232, 240, 0.9) !important;
  border-radius: 999px !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.95) !important;
  border-radius: 999px !important;
  border: 2px solid rgba(226, 232, 240, 0.95) !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.95) !important;
}

.prototype-studio-artboard-scale {
  overflow: hidden !important;
}

/* Make the generated runtime content grow naturally.
   The host above is the single browser-like scroll container. */
.prototype-studio-runtime-host :deep(.runtime-shell--artboard),
.prototype-studio-runtime-host :deep(.artboard-runtime-main),
.prototype-studio-runtime-host :deep(.pattern-runtime),
.prototype-studio-runtime-host :deep(.ui-runtime-shell),
.prototype-studio-runtime-host :deep(.runtime-screen),
.prototype-studio-runtime-host :deep(.a9-runtime-design-canvas) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: 1440px !important;
  height: auto !important;
  min-height: 900px !important;
  max-height: none !important;
  overflow: visible !important;
  box-sizing: border-box !important;
}

/* Prevent page roots from being forced to a clipped 900px height. */
.prototype-studio-runtime-host :deep(.business-page),
.prototype-studio-runtime-host :deep(.auth-page),
.prototype-studio-runtime-host :deep(.business-body),
.prototype-studio-runtime-host :deep(.page-canvas) {
  height: auto !important;
  min-height: 100% !important;
  max-height: none !important;
  overflow: visible !important;
}


/* A9.9.11 Fixed Viewport Scroll Closure
   Split scaled artboard and browser-like scroll viewport.
   The scale layer never scrolls; the runtime host is the fixed 1440×900 viewport. */
.prototype-studio-artboard-scale {
  width: 1440px !important;
  height: 900px !important;
  min-width: 1440px !important;
  min-height: 900px !important;
  max-width: 1440px !important;
  max-height: 900px !important;
  overflow: hidden !important;
  transform-origin: top left !important;
  flex: 0 0 auto !important;
  background: #f5f7fb !important;
  border-radius: 18px !important;
}

.prototype-studio-runtime-host {
  width: 1440px !important;
  height: 900px !important;
  min-width: 1440px !important;
  min-height: 900px !important;
  max-width: 1440px !important;
  max-height: 900px !important;
  overflow-y: auto !important;
  overflow-x: hidden !important;
  overscroll-behavior: contain !important;
  scrollbar-gutter: stable !important;
  transform: none !important;
  background: #f5f7fb !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar {
  width: 10px !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar-track {
  background: rgba(226, 232, 240, 0.92) !important;
}

.prototype-studio-runtime-host::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.9) !important;
  border-radius: 999px !important;
  border: 2px solid rgba(226, 232, 240, 0.92) !important;
}

.prototype-studio-runtime-host :deep(.runtime-shell--artboard),
.prototype-studio-runtime-host :deep(.artboard-runtime-main),
.prototype-studio-runtime-host :deep(.pattern-runtime),
.prototype-studio-runtime-host :deep(.ui-runtime-shell),
.prototype-studio-runtime-host :deep(.runtime-screen),
.prototype-studio-runtime-host :deep(.a9-runtime-design-canvas) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: 1440px !important;
  height: auto !important;
  min-height: 900px !important;
  max-height: none !important;
  overflow: visible !important;
  box-sizing: border-box !important;
}

.prototype-studio-runtime-host :deep(.business-page),
.prototype-studio-runtime-host :deep(.auth-page),
.prototype-studio-runtime-host :deep(.business-body),
.prototype-studio-runtime-host :deep(.page-canvas) {
  height: auto !important;
  min-height: 100% !important;
  max-height: none !important;
  overflow: visible !important;
}

/* A9.10.3 Prototype Save / Version / Publish Closure */
.prototype-studio-lifecycle-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.prototype-studio-lifecycle-line em {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(15, 23, 42, 0.72);
  color: #cbd5e1;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.prototype-studio-lifecycle-line em.state-published {
  border-color: rgba(34, 197, 94, 0.38);
  background: rgba(22, 163, 74, 0.16);
  color: #bbf7d0;
}

.prototype-studio-lifecycle-line em.state-draft {
  border-color: rgba(245, 158, 11, 0.42);
  background: rgba(245, 158, 11, 0.16);
  color: #fde68a;
}

.prototype-studio-lifecycle-line em.state-saved_unpublished {
  border-color: rgba(59, 130, 246, 0.42);
  background: rgba(37, 99, 235, 0.16);
  color: #bfdbfe;
}
</style>
