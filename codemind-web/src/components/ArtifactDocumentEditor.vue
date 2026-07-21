<template>
  <div
    class="artifact-document-shell"
    :class="{ fullscreen: fullscreen }"
  >
    <header class="document-toolbar">
      <div>
        <p class="document-eyebrow">文档视图</p>
        <h3>{{ title }}</h3>
        <span v-if="artifact?.artifactId">
          Artifact：{{ artifact.artifactId }}
          <em v-if="artifact?.versionNo"> / V{{ artifact.versionNo }}</em>
        </span>
        <span v-else>
          当前资产暂无文档内容。
        </span>
      </div>

      <div class="document-actions">
        <button
          v-if="editable && !editing"
          type="button"
          class="secondary-button"
          :disabled="!artifact?.artifactId || !markdown"
          @click="startEditing"
        >
          编辑文档
        </button>

        <button
          v-if="editing"
          type="button"
          class="secondary-button"
          :disabled="saving"
          @click="cancelEditing"
        >
          取消编辑
        </button>

        <button
          v-if="editing"
          type="button"
          class="primary-button"
          :disabled="saving || !draftChanged || !draftMarkdown.trim()"
          @click="submitManualEdit"
        >
          {{ saving ? '保存中...' : '保存为新版本' }}
        </button>

        <button
          type="button"
          class="fullscreen-button"
          :title="fullscreen ? '退出全屏' : '全屏阅读'"
          @click="toggleFullscreen"
        >
          <span aria-hidden="true">{{ fullscreen ? '⤢' : '⛶' }}</span>
          <em>{{ fullscreen ? '退出全屏' : '全屏' }}</em>
        </button>
      </div>
    </header>

    <div
      v-if="editable"
      class="document-edit-hint"
      :class="{ editing }"
    >
      <strong>{{ editing ? '正在编辑文档视图' : '受控编辑' }}</strong>
      <span>
        这里保存的是正式资产的新版本。旧版本会保留，新版本保存后卡片视图会按最新文档重新展示。
      </span>
    </div>

    <section
      v-if="editing"
      class="document-editor-grid"
    >
      <div class="editor-pane">
        <div class="pane-title">
          <strong>Markdown 编辑区</strong>
          <span>{{ draftMarkdown.length }} 字符</span>
        </div>

        <textarea
          v-model="draftMarkdown"
          class="document-textarea"
          spellcheck="false"
        />
      </div>

      <div class="preview-pane">
        <div class="pane-title">
          <strong>实时预览</strong>
          <span>保存后生成新版本</span>
        </div>

        <article
          class="prd-document document-preview"
          v-html="renderedDraftMarkdown"
        />
      </div>
    </section>

    <article
      v-else
      class="prd-document document-preview"
      v-html="renderedMarkdown"
    />
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type {
  ArtifactResponse,
  ManualArtifactEditPayload,
} from '../types/codemind'

const props = defineProps<{
  artifact: ArtifactResponse | null
  artifactType: string
  title: string
  markdown: string
  editable?: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  save: [payload: ManualArtifactEditPayload]
}>()

const fullscreen = ref(false)
const editing = ref(false)
const draftMarkdown = ref('')

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const renderedMarkdown = computed(() => {
  const rawHtml = markdownRenderer.render(props.markdown || '')
  return DOMPurify.sanitize(rawHtml)
})

const renderedDraftMarkdown = computed(() => {
  const rawHtml = markdownRenderer.render(draftMarkdown.value || '')
  return DOMPurify.sanitize(rawHtml)
})

const draftChanged = computed(() =>
  normalizeMarkdown(draftMarkdown.value) !== normalizeMarkdown(props.markdown || ''),
)

watch(
  () => props.markdown,
  (value) => {
    if (!editing.value) {
      draftMarkdown.value = value || ''
    }
  },
  {
    immediate: true,
  },
)

watch(
  () => fullscreen.value,
  (value) => {
    document.body.classList.toggle('artifact-document-fullscreen-lock', value)
  },
)

function startEditing() {
  draftMarkdown.value = props.markdown || ''
  editing.value = true
}

function cancelEditing() {
  if (draftChanged.value) {
    const confirmed = window.confirm(
      '当前文档有未保存修改，确定放弃本次编辑吗？',
    )

    if (!confirmed) {
      return
    }
  }

  draftMarkdown.value = props.markdown || ''
  editing.value = false
}

function submitManualEdit() {
  if (!props.artifact?.artifactId) {
    window.alert('当前资产缺少 artifactId，无法保存新版本。')
    return
  }

  if (!draftMarkdown.value.trim()) {
    window.alert('文档内容不能为空。')
    return
  }

  if (!draftChanged.value) {
    window.alert('文档内容没有变化，无需生成新版本。')
    return
  }

  const reason = window.prompt(
    '请输入本次文档修改说明，便于后续版本追踪：',
    '手动编辑文档视图',
  )

  if (reason === null) {
    return
  }

  emit('save', {
    artifactId: props.artifact.artifactId,
    artifactType: props.artifactType,
    contentMarkdown: draftMarkdown.value,
    reason: reason.trim(),
  })

  editing.value = false
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && fullscreen.value) {
    fullscreen.value = false
  }
}

function normalizeMarkdown(value: string): string {
  return value.replace(/\r\n/g, '\n').trim()
}

window.addEventListener('keydown', handleKeydown)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.classList.remove('artifact-document-fullscreen-lock')
})
</script>

<style scoped>
.artifact-document-shell {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
  overflow: hidden;
}

.artifact-document-shell.fullscreen {
  position: fixed;
  inset: 14px;
  z-index: 1400;
  display: flex;
  flex-direction: column;
  border-radius: 22px;
  box-shadow: 0 32px 90px rgba(15, 23, 42, 0.28);
}

.document-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  border-bottom: 1px solid #e2e8f0;
  padding: 14px 16px;
  background: #f8fafc;
}

.document-toolbar h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.document-toolbar span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
}

.document-toolbar em {
  font-style: normal;
}

.document-eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.document-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.secondary-button,
.primary-button,
.fullscreen-button {
  border-radius: 11px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button,
.fullscreen-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button:disabled,
.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.fullscreen-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.fullscreen-button span {
  display: inline-flex;
  width: 16px;
  justify-content: center;
  margin: 0;
  color: inherit;
  font-size: 15px;
  line-height: 1;
}

.fullscreen-button em {
  font-style: normal;
}

.document-edit-hint {
  display: flex;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid #dbeafe;
  padding: 10px 16px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 12px;
}

.document-edit-hint.editing {
  border-bottom-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.document-edit-hint strong {
  flex: 0 0 auto;
}

.document-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0;
  min-height: 620px;
}

.fullscreen .document-editor-grid {
  min-height: 0;
  flex: 1;
}

.editor-pane,
.preview-pane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-pane {
  border-right: 1px solid #e2e8f0;
}

.pane-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e2e8f0;
  padding: 9px 12px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
}

.pane-title span {
  color: #94a3b8;
}

.document-textarea {
  flex: 1;
  width: 100%;
  min-height: 560px;
  border: none;
  padding: 18px;
  resize: none;
  outline: none;
  color: #0f172a;
  font-family:
    "JetBrains Mono",
    "Cascadia Code",
    Consolas,
    monospace;
  font-size: 14px;
  line-height: 1.75;
}

.fullscreen .document-textarea {
  min-height: 0;
}

.document-preview {
  flex: 1;
  min-height: 560px;
  overflow: auto;
  padding: 24px 28px;
}

.fullscreen .document-preview {
  min-height: 0;
}

@media (max-width: 960px) {
  .document-editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor-pane {
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }
}
</style>

<style>
body.artifact-document-fullscreen-lock {
  overflow: hidden;
}
</style>
