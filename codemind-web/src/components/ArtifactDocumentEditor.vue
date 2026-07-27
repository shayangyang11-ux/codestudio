<template>
  <div
    class="artifact-document-shell"
    :class="{ fullscreen: fullscreen, compact: compact }"
  >
    <header
      class="document-toolbar"
      :class="{ compact: compact }"
    >
      <div v-if="!compact">
        <p class="document-eyebrow">文档视图</p>
        <h3>{{ title }}</h3>
        <span v-if="artifact?.versionNo">
          版本 V{{ artifact.versionNo }}
        </span>
        <span v-else>
          当前资产暂无文档内容。
        </span>
      </div>

      <div class="document-actions">
        <button
          v-if="editable && !editing"
          type="button"
          class="secondary-button compact-action-button"
          :disabled="!artifact?.artifactId || !markdown"
          @click="startEditing"
        >
          编辑文档
        </button>

        <button
          v-if="editing"
          type="button"
          class="secondary-button compact-action-button"
          :disabled="saving"
          @click="cancelEditing"
        >
          取消编辑
        </button>

        <button
          v-if="editing"
          type="button"
          class="primary-button compact-action-button"
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
      v-if="editable && editing"
      class="document-edit-hint editing"
    >
      <strong>正在编辑可视化文档</strong>
      <span>可直接在正文中修改文字和结构。保存后会生成正式资产的新版本，旧版本仍会保留。</span>
    </div>

    <section
      v-if="editing"
      class="visual-document-editor"
    >
      <div class="visual-editor-guide">
        <strong>可视化编辑模式</strong>
        <span>直接点击正文修改内容，系统会在保存时转换为资产 Markdown。</span>
      </div>

      <article
        ref="visualEditorRef"
        class="prd-document document-preview visual-editor-surface"
        contenteditable="true"
        spellcheck="true"
        v-html="draftHtml"
        @input="handleVisualEditorInput"
      />
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
  nextTick,
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
  compact?: boolean
}>()

const emit = defineEmits<{
  save: [payload: ManualArtifactEditPayload]
}>()

const fullscreen = ref(false)
const editing = ref(false)
const draftMarkdown = ref('')
const draftHtml = ref('')
const visualEditorRef = ref<HTMLElement | null>(null)

const compact = computed(() => props.compact === true)

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const renderedMarkdown = computed(() => {
  const rawHtml = markdownRenderer.render(props.markdown || '')
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
      draftHtml.value = renderMarkdownToHtml(value || '')
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
  draftHtml.value = renderMarkdownToHtml(props.markdown || '')
  editing.value = true

  nextTick(() => {
    visualEditorRef.value?.focus()
  })
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
  draftHtml.value = renderMarkdownToHtml(props.markdown || '')
  editing.value = false
}

function submitManualEdit() {
  syncDraftFromVisualEditor()

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


function handleVisualEditorInput() {
  syncDraftFromVisualEditor()
}

function syncDraftFromVisualEditor() {
  draftMarkdown.value = htmlToMarkdown(visualEditorRef.value)
}

function renderMarkdownToHtml(value: string): string {
  const rawHtml = markdownRenderer.render(value || '')
  return DOMPurify.sanitize(rawHtml)
}

function htmlToMarkdown(root: HTMLElement | null): string {
  if (!root) {
    return draftMarkdown.value
  }

  const blocks: string[] = []

  root.childNodes.forEach((node) => {
    const markdown = nodeToMarkdown(node, 0).trim()

    if (markdown) {
      blocks.push(markdown)
    }
  })

  return normalizeMarkdown(blocks.join('\n\n'))
}

function nodeToMarkdown(node: Node, depth: number): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return normalizeInlineText(node.textContent || '')
  }

  if (!(node instanceof HTMLElement)) {
    return ''
  }

  const tagName = node.tagName.toLowerCase()

  if (/^h[1-6]$/.test(tagName)) {
    const level = Number(tagName.slice(1))
    return `${'#'.repeat(level)} ${collectInlineMarkdown(node).trim()}`
  }

  if (tagName === 'p' || tagName === 'div' || tagName === 'section' || tagName === 'article') {
    const text = collectInlineMarkdown(node).trim()
    return text || collectBlockMarkdown(node, depth)
  }

  if (tagName === 'ul' || tagName === 'ol') {
    const ordered = tagName === 'ol'
    return Array.from(node.children)
      .filter((child) => child.tagName.toLowerCase() === 'li')
      .map((child, index) => {
        const marker = ordered ? `${index + 1}.` : '-'
        return `${'  '.repeat(depth)}${marker} ${collectInlineMarkdown(child as HTMLElement).trim()}`
      })
      .filter(Boolean)
      .join('\n')
  }

  if (tagName === 'li') {
    return `${'  '.repeat(depth)}- ${collectInlineMarkdown(node).trim()}`
  }

  if (tagName === 'blockquote') {
    return collectBlockMarkdown(node, depth)
      .split('\n')
      .filter((line) => line.trim())
      .map((line) => `> ${line}`)
      .join('\n')
  }

  if (tagName === 'pre') {
    return `\`\`\`\n${node.innerText.trim()}\n\`\`\``
  }

  if (tagName === 'table') {
    return tableToMarkdown(node)
  }

  if (tagName === 'br') {
    return '\n'
  }

  return collectBlockMarkdown(node, depth)
}

function collectBlockMarkdown(element: HTMLElement, depth: number): string {
  return Array.from(element.childNodes)
    .map((child) => nodeToMarkdown(child, depth))
    .filter((value) => value.trim())
    .join('\n\n')
}

function collectInlineMarkdown(element: HTMLElement): string {
  const parts = Array.from(element.childNodes).map((node) => inlineNodeToMarkdown(node))
  return normalizeInlineText(parts.join(''))
}

function inlineNodeToMarkdown(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return node.textContent || ''
  }

  if (!(node instanceof HTMLElement)) {
    return ''
  }

  const tagName = node.tagName.toLowerCase()
  const text = Array.from(node.childNodes)
    .map((child) => inlineNodeToMarkdown(child))
    .join('')

  if (tagName === 'strong' || tagName === 'b') {
    return text.trim() ? `**${text.trim()}**` : ''
  }

  if (tagName === 'em' || tagName === 'i') {
    return text.trim() ? `*${text.trim()}*` : ''
  }

  if (tagName === 'code') {
    return text.trim() ? `\`${text.trim()}\`` : ''
  }

  if (tagName === 'a') {
    const href = node.getAttribute('href') || ''
    return href && text.trim() ? `[${text.trim()}](${href})` : text
  }

  if (tagName === 'br') {
    return '\n'
  }

  return text
}

function tableToMarkdown(table: HTMLElement): string {
  const rows = Array.from(table.querySelectorAll('tr')).map((row) =>
    Array.from(row.children).map((cell) => collectInlineMarkdown(cell as HTMLElement).trim()),
  )

  if (!rows.length) {
    return ''
  }

  const header = rows[0] || []
  const separator = header.map(() => '---')
  const body = rows.slice(1)
  const formatRow = (cells: string[]) => `| ${cells.join(' | ')} |`

  return [
    formatRow(header),
    formatRow(separator),
    ...body.map(formatRow),
  ].join('\n')
}

function normalizeInlineText(value: string): string {
  return value
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
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

.artifact-document-shell.compact {
  border: 0;
  border-radius: 0;
  background: transparent;
  overflow: visible;
}

.artifact-document-shell.fullscreen {
  position: fixed !important;
  inset: 0 !important;
  z-index: 99999 !important;
  width: 100vw !important;
  height: 100vh !important;
  max-width: none !important;
  max-height: none !important;
  margin: 0 !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  border: 0 !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
  overflow: hidden !important;
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

.document-toolbar.compact {
  justify-content: flex-end;
  border-bottom: 0;
  padding: 0 0 8px;
  background: transparent;
}

.document-toolbar.compact .document-actions {
  gap: 8px;
}

.artifact-document-shell.fullscreen .document-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  flex: 0 0 auto;
  justify-content: flex-end;
  border-bottom: 1px solid #e5e7eb;
  padding: 12px 18px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
}

.artifact-document-shell.fullscreen .document-actions {
  justify-content: flex-end;
}

.document-toolbar.compact .secondary-button,
.document-toolbar.compact .primary-button,
.document-toolbar.compact .fullscreen-button {
  min-height: 0;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  box-shadow: none;
}

.document-toolbar.compact .secondary-button,
.document-toolbar.compact .fullscreen-button {
  border-color: #dbeafe;
  background: #ffffff;
  color: #2563eb;
}

.document-toolbar.compact .primary-button {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
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

.compact .document-edit-hint {
  margin-bottom: 12px;
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 9px 12px;
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
  flex: 1;
  min-height: 0 !important;
  overflow: hidden;
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
  flex: 1;
  min-height: 0 !important;
  height: auto !important;
}

.document-preview {
  flex: 1;
  min-height: 560px;
  overflow: auto;
  padding: 24px 28px;
}

.compact .document-preview {
  min-height: 540px;
  border-top: 0;
  padding: 0px 3px 18px;
}

.fullscreen .document-preview {
  flex: 1;
  min-height: 0 !important;
  height: auto !important;
  max-height: none !important;
  padding: 20px 32px 40px !important;
  overflow: auto !important;
  background: #ffffff;
}

.visual-document-editor {
  display: grid;
  gap: 10px;
}

.visual-editor-guide {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  padding: 8px 12px;
  background: #f8fbff;
  color: #1d4ed8;
  font-size: 12px;
}

.visual-editor-guide strong {
  flex: 0 0 auto;
}

.visual-editor-guide span {
  color: #475569;
}

.visual-editor-surface {
  min-height: 560px;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  outline: none;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.04);
}

.visual-editor-surface:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
}

.compact .visual-editor-surface {
  min-height: 520px;
  padding: 12px 16px 22px;
}

.fullscreen .visual-document-editor {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.fullscreen .visual-editor-surface {
  flex: 1;
  min-height: 0 !important;
  overflow: auto !important;
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

/* Reader micro-polish: lighter inline tools and balanced reading area */
.document-toolbar.compact {
  padding: 0 0 6px;
}

.document-toolbar.compact .document-actions {
  align-items: center;
  gap: 8px;
}

.document-toolbar.compact .secondary-button,
.document-toolbar.compact .primary-button,
.document-toolbar.compact .fullscreen-button {
  border-color: #dbeafe;
  background: #f8fbff;
  color: #1d4ed8;
}

.document-toolbar.compact .secondary-button:hover,
.document-toolbar.compact .fullscreen-button:hover {
  background: #eff6ff;
}

.compact .document-preview {
  padding: 2px 4px 20px;
}

.fullscreen .document-preview {
  padding: 22px 34px 42px !important;
}



/* Compact reader participates in the outer asset-card scroll so the outer title can stay sticky. */
.artifact-document-shell.compact:not(.fullscreen) .document-preview {
  min-height: 0;
  overflow: visible;
  padding: 0 4px 22px;
}

.artifact-document-shell.compact:not(.fullscreen) .prd-document {
  max-height: none;
  overflow: visible;
}

.artifact-document-shell.compact:not(.fullscreen) .document-toolbar.compact {
  background: rgba(255, 255, 255, 0.97);
}

.artifact-document-shell.fullscreen .document-preview {
  overflow: auto !important;
}

</style>

<style>
body.artifact-document-fullscreen-lock {
  overflow: hidden !important;
}
</style>

