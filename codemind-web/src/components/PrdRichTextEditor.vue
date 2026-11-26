<template>
  <section class="prd-rich-editor" :class="{ disabled }">
    <div ref="toolbarSentinel" class="prd-rich-toolbar-sentinel" aria-hidden="true"></div>
    <div ref="toolbarRef" class="prd-rich-toolbar" :class="{ ['is-stuck']: toolbarStuck }" role="toolbar" aria-label="富文本工具栏">
      <button type="button" title="标题" :disabled="!editor || disabled" @click="toggleHeading">
        标题
      </button>
      <button type="button" title="正文" :disabled="!editor || disabled" @click="setParagraph">
        正文
      </button>

      <span class="toolbar-divider" />

      <button type="button" title="加粗" :class="{ active: editor?.isActive('bold') }" :disabled="!editor || disabled" @click="runChain('toggleBold')">
        <strong>B</strong>
      </button>
      <button type="button" title="斜体" :class="{ active: editor?.isActive('italic') }" :disabled="!editor || disabled" @click="runChain('toggleItalic')">
        <em>I</em>
      </button>
      <button type="button" title="下划线" :class="{ active: editor?.isActive('underline') }" :disabled="!editor || disabled" @click="runChain('toggleUnderline')">
        <u>U</u>
      </button>

      <select
        v-model="fontSizeValue"
        class="toolbar-select font-size-select"
        title="字号"
        :disabled="!editor || disabled"
        @change="setFontSize(fontSizeValue)"
      >
        <option value="">字号</option>
        <option v-for="option in fontSizeOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>

      <label class="toolbar-color-control" title="字体颜色">
        字色
        <input
          v-model="fontColorValue"
          type="color"
          :disabled="!editor || disabled"
          @input="setTextColor(fontColorValue)"
        />
      </label>

      <label class="toolbar-color-control" title="文字底色">
        底色
        <input
          v-model="highlightColorValue"
          type="color"
          :disabled="!editor || disabled"
          @input="setBackgroundColor(highlightColorValue)"
        />
      </label>
<span class="toolbar-divider" />

      <button type="button" title="无序列表" :class="{ active: editor?.isActive('bulletList') }" :disabled="!editor || disabled" @click="runChain('toggleBulletList')">
        • 列表
      </button>
      <button type="button" title="有序列表" :class="{ active: editor?.isActive('orderedList') }" :disabled="!editor || disabled" @click="runChain('toggleOrderedList')">
        1. 列表
      </button>

      <span class="toolbar-divider" />

      <button type="button" title="链接" :disabled="!editor || disabled" @click="setLink">
        链接
      </button>
      <button type="button" title="上传本地图片" :disabled="!canUploadImage" @click="triggerImageUpload">
        {{ isUploadingImage ? '上传中...' : '图片' }}
      </button>
      <button type="button" title="插入图片 URL" :disabled="!editor || disabled" @click="insertImageByUrl">
        图片 URL
      </button>

      <span class="toolbar-divider" />

      <div class="table-picker-wrap">
        <button
          type="button"
          title="插入表格"
          :disabled="!editor || disabled"
          @click="toggleTablePicker"
        >
          表格
        </button>

        <div
          v-if="tablePickerOpen"
          class="table-picker-panel"
          @mouseleave="previewTableSize(1, 1)"
        >
          <div class="table-picker-label">
            {{ tablePickerRows }} × {{ tablePickerCols }} 表格
          </div>
          <div class="table-picker-grid">
            <button
              v-for="cell in tablePickerCells"
              :key="cell.key"
              type="button"
              class="table-picker-cell"
              :class="{ active: cell.row <= tablePickerRows && cell.col <= tablePickerCols }"
              @mouseenter="previewTableSize(cell.row, cell.col)"
              @click="insertPickedTable(cell.row, cell.col)"
            />
          </div>
        </div>
      </div>
      <button type="button" title="新增行" :disabled="!editor || disabled" @click="runChain('addRowAfter')">
        +行
      </button>
      <button type="button" title="新增列" :disabled="!editor || disabled" @click="runChain('addColumnAfter')">
        +列
      </button>
      <button type="button" title="删除行" :disabled="!editor || disabled" @click="runChain('deleteRow')">
        -行
      </button>
      <button type="button" title="删除列" :disabled="!editor || disabled" @click="runChain('deleteColumn')">
        -列
      </button>

      <span class="toolbar-divider" />
<input
        ref="imageInputRef"
        class="hidden-file-input"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        @change="handleImageFileSelected"
      />
    </div>

    <div v-if="imageUploadMessage" class="rich-editor-message">
      {{ imageUploadMessage }}
    </div>

    <EditorContent :editor="editor" class="prd-rich-surface" />
  </section>
</template>

<script setup lang="ts">
import { Extension } from '@tiptap/core'
import { computed, onBeforeUnmount, ref, watch, onMounted, nextTick } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import TextAlign from '@tiptap/extension-text-align'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import { Highlight } from '@tiptap/extension-highlight'
import { TableKit } from '@tiptap/extension-table'
import {
  buildAssistantAttachmentContentUrl,
  fetchAssistantAttachmentObjectUrl,
  uploadAssistantAttachment,
} from '../api/assistantApi'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  disabled?: boolean
  workspaceId?: string
  projectCode?: string
}>(), {
  placeholder: '请输入内容，可设置标题、字体、颜色、列表、表格和图片。',
  disabled: false,
  workspaceId: '',
  projectCode: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const imageInputRef = ref<HTMLInputElement | null>(null)
const isUploadingImage = ref(false)
const imageUploadMessage = ref('')
const tablePickerOpen = ref(false)
const tablePickerRows = ref(1)
const tablePickerCols = ref(1)
const tablePickerCells = Array.from({ length: 64 }, (_, index) => ({
  row: Math.floor(index / 8) + 1,
  col: (index % 8) + 1,
  key: `table-picker-${Math.floor(index / 8) + 1}-${(index % 8) + 1}`,
}))
const objectUrls = new Set<string>()
let hydrateSeq = 0

const RichImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      attachmentId: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-attachment-id'),
        renderHTML: (attributes) => attributes.attachmentId
          ? { 'data-attachment-id': attributes.attachmentId }
          : {},
      },
      fileName: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-file-name'),
        renderHTML: (attributes) => attributes.fileName
          ? { 'data-file-name': attributes.fileName }
          : {},
      },
      permanentSrc: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-permanent-src'),
        renderHTML: (attributes) => attributes.permanentSrc
          ? { 'data-permanent-src': attributes.permanentSrc }
          : {},
      },
      loading: {
        default: 'lazy',
        parseHTML: (element) => element.getAttribute('loading') || 'lazy',
        renderHTML: (attributes) => ({ loading: attributes.loading || 'lazy' }),
      },
    }
  },
})


const FontSizeExtension = Extension.create({
  name: 'fontSize',

  addOptions() {
    return {
      types: ['textStyle'],
    }
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          fontSize: {
            default: null,
            parseHTML: (element: HTMLElement) => element.style.fontSize || null,
            renderHTML: (attributes: Record<string, unknown>) => {
              const fontSize = String(attributes.fontSize || '').trim()
              return fontSize ? { style: `font-size: ${fontSize}` } : {}
            },
          },
        },
      },
    ]
  },
})

const editor = useEditor({
  content: normalizeInitialValue(props.modelValue || ''),
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({
      heading: { levels: [2, 3, 4] },
    }),
    Underline,
    TextStyle,
    FontSizeExtension,
    Color,
    Highlight.configure({ multicolor: true }),
    TextAlign.configure({
      types: ['heading', 'paragraph'],
    }),
    Link.configure({
      openOnClick: false,
      autolink: true,
      linkOnPaste: true,
      HTMLAttributes: {
        rel: 'noopener noreferrer',
        target: '_blank',
      },
    }),
    RichImage.configure({
      allowBase64: false,
      inline: false,
      HTMLAttributes: {
        class: 'prd-rich-image',
      },
    }),
    Placeholder.configure({
      placeholder: props.placeholder,
    }),
    (TableKit as any).configure({
      table: {
        resizable: true,
        HTMLAttributes: {
          class: 'prd-rich-table',
        },
      },
    }),
  ],
  editorProps: {
    attributes: {
      class: 'prd-rich-editable',
    },
  },
  onCreate: () => {
    scheduleHydrateAttachmentImages()
  },
  onUpdate: ({ editor: updatedEditor }) => {
    emit('update:modelValue', normalizeEditorHtmlForPersist(updatedEditor.getHTML()))
  },
})

const canUploadImage = computed(() => (
  Boolean(editor.value) &&
  !props.disabled &&
  !isUploadingImage.value &&
  Boolean(String(props.workspaceId || '').trim()) &&
  Boolean(String(props.projectCode || '').trim())
))

watch(
  () => props.modelValue,
  (value) => {
    const instance = editor.value
    if (!instance) return

    const nextHtml = normalizeInitialValue(value || '')
    const currentPersistHtml = normalizeEditorHtmlForPersist(instance.getHTML())

    if (currentPersistHtml !== normalizeEditorHtmlForPersist(nextHtml)) {
      instance.commands.setContent(nextHtml, false)
      scheduleHydrateAttachmentImages()
    }
  },
)

watch(
  () => props.disabled,
  (value) => {
    editor.value?.setEditable(!value)
  },
)

onBeforeUnmount(() => {
  editor.value?.destroy()
  for (const url of objectUrls) {
    window.URL.revokeObjectURL(url)
  }
  objectUrls.clear()
})

function normalizeInitialValue(value: string): string {
  const text = String(value || '').trim()
  if (!text) return ''
  if (looksLikeHtml(text)) return text

  return text
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function looksLikeHtml(value: string): boolean {
  return /<\/?[a-z][\s\S]*>/i.test(String(value || ''))
}

function escapeHtml(value: string): string {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}



const toolbarRef = ref<HTMLElement | null>(null)
const toolbarSentinel = ref<HTMLElement | null>(null)
const toolbarStuck = ref(false)
let toolbarObserver: IntersectionObserver | null = null

function findEditorScrollRoot(element: HTMLElement | null): HTMLElement | null {
  let current = element?.parentElement || null

  while (current) {
    const style = window.getComputedStyle(current)
    const overflowY = `${style.overflowY} ${style.overflow}`
    if (/(auto|scroll|overlay)/i.test(overflowY)) {
      return current
    }
    current = current.parentElement
  }

  return null
}

onMounted(() => {
  nextTick(() => {
    const sentinel = toolbarSentinel.value
    if (!sentinel) return

    const root = findEditorScrollRoot(sentinel)

    toolbarObserver?.disconnect()
    toolbarObserver = new IntersectionObserver(
      ([entry]) => {
        toolbarStuck.value = !entry.isIntersecting
      },
      {
        root,
        threshold: 0.99,
        rootMargin: '0px 0px 0px 0px',
      },
    )

    toolbarObserver.observe(sentinel)
  })
})

onBeforeUnmount(() => {
  toolbarObserver?.disconnect()
  toolbarObserver = null
})


const fontSizeOptions = [
  { label: '12px', value: '12px' },
  { label: '14px', value: '14px' },
  { label: '16px', value: '16px' },
  { label: '18px', value: '18px' },
  { label: '20px', value: '20px' },
  { label: '24px', value: '24px' },
  { label: '28px', value: '28px' },
]

const fontSizeValue = ref('')
const fontColorValue = ref('#111827')
const highlightColorValue = ref('#fff3bf')

function setFontSize(value: string) {
  const size = String(value || '').trim()
  const instance = editor.value as any
  if (!instance || props.disabled) return

  const chain = instance.chain().focus()
  if (!size) {
    chain.setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run()
    return
  }

  chain.setMark('textStyle', { fontSize: size }).run()
}

function setTextColor(value: string) {
  const color = String(value || '').trim()
  const instance = editor.value as any
  if (!instance || props.disabled || !color) return

  const chain = instance.chain().focus()
  if (typeof chain.setColor === 'function') {
    chain.setColor(color).run()
  }
}

function setBackgroundColor(value: string) {
  const color = String(value || '').trim()
  const instance = editor.value as any
  if (!instance || props.disabled || !color) return

  const chain = instance.chain().focus()
  if (typeof chain.setHighlight === 'function') {
    chain.setHighlight({ color }).run()
  } else if (typeof chain.toggleHighlight === 'function') {
    chain.toggleHighlight({ color }).run()
  }
}

function clearInlineStyle() {
  const instance = editor.value as any
  if (!instance || props.disabled) return

  const chain = instance.chain().focus()
  if (typeof chain.unsetColor === 'function') {
    chain.unsetColor()
  }
  if (typeof chain.unsetHighlight === 'function') {
    chain.unsetHighlight()
  }
  chain.setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run()
}

function runChain(commandName: string) {
  const instance = editor.value as any
  if (!instance || props.disabled) return

  const chain = instance.chain().focus()
  if (typeof chain[commandName] === 'function') {
    chain[commandName]().run()
  }
}

function toggleHeading() {
  editor.value?.chain().focus().toggleHeading({ level: 3 }).run()
}

function setParagraph() {
  editor.value?.chain().focus().setParagraph().run()
}

function clearFormat() {
  editor.value?.chain().focus().unsetAllMarks().clearNodes().run()
}

function setLink() {
  const instance = editor.value
  if (!instance || props.disabled) return

  const previousUrl = instance.getAttributes('link').href || ''
  const url = window.prompt('请输入链接地址', previousUrl)

  if (url === null) return

  const trimmed = url.trim()
  if (!trimmed) {
    instance.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }

  instance
    .chain()
    .focus()
    .extendMarkRange('link')
    .setLink({ href: trimmed })
    .run()
}

function toggleTablePicker() {
  if (!editor.value || props.disabled) return

  tablePickerOpen.value = !tablePickerOpen.value
  tablePickerRows.value = 1
  tablePickerCols.value = 1
}

function previewTableSize(rows: number, cols: number) {
  tablePickerRows.value = Math.max(1, Math.min(8, rows))
  tablePickerCols.value = Math.max(1, Math.min(8, cols))
}

function insertPickedTable(rows: number, cols: number) {
  const instance = editor.value as any
  if (!instance || props.disabled) return

  tablePickerOpen.value = false

  instance
    .chain()
    .focus()
    .insertTable({
      rows: Math.max(1, Math.min(8, rows)),
      cols: Math.max(1, Math.min(8, cols)),
      withHeaderRow: false,
    })
    .run()
}

function insertImageByUrl() {
  const instance = editor.value
  if (!instance || props.disabled) return

  const url = window.prompt('请输入图片 URL')
  const src = String(url || '').trim()

  if (!src) return

  ;(instance as any).chain().focus().setImage({
    src,
    alt: '需求说明图片',
    title: '需求说明图片',
    loading: 'lazy',
  }).run()
}

function triggerImageUpload() {
  imageUploadMessage.value = ''

  if (!String(props.workspaceId || '').trim() || !String(props.projectCode || '').trim()) {
    imageUploadMessage.value = '缺少 workspaceId / projectCode，无法上传图片。'
    return
  }

  imageInputRef.value?.click()
}

async function handleImageFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  input.value = ''

  if (!file) return

  if (!/^image\/(png|jpeg|webp)$/i.test(file.type || '')) {
    imageUploadMessage.value = '仅支持 PNG、JPG、WEBP 图片。'
    return
  }

  const workspaceId = String(props.workspaceId || '').trim()
  const projectCode = String(props.projectCode || '').trim()

  if (!workspaceId || !projectCode) {
    imageUploadMessage.value = '缺少 workspaceId / projectCode，无法上传图片。'
    return
  }

  isUploadingImage.value = true
  imageUploadMessage.value = '正在上传图片...'

  try {
    const uploaded = await uploadAssistantAttachment(workspaceId, projectCode, file)
    const attachmentId = String(uploaded.attachmentId || '').trim()

    if (!attachmentId) {
      throw new Error('上传成功但未返回 attachmentId。')
    }

    const permanentSrc = buildAssistantAttachmentContentUrl(workspaceId, projectCode, attachmentId)
    const objectUrl = await fetchAssistantAttachmentObjectUrl(workspaceId, projectCode, attachmentId)
    rememberObjectUrl(objectUrl)

    const fileName = String(uploaded.fileName || file.name || '图片')

    ;(editor.value as any)?.chain().focus().setImage({
      src: objectUrl,
      permanentSrc,
      alt: fileName,
      title: fileName,
      attachmentId,
      fileName,
      loading: 'lazy',
    }).run()

    imageUploadMessage.value = '图片已插入。'
    window.setTimeout(() => {
      if (imageUploadMessage.value === '图片已插入。') {
        imageUploadMessage.value = ''
      }
    }, 1600)
  } catch (error) {
    imageUploadMessage.value = resolveErrorMessage(error, '图片上传失败。')
  } finally {
    isUploadingImage.value = false
  }
}

function rememberObjectUrl(url: string) {
  if (url.startsWith('blob:')) {
    objectUrls.add(url)
  }
}

function scheduleHydrateAttachmentImages() {
  window.setTimeout(() => {
    void hydrateAttachmentImages()
  }, 0)
}

async function hydrateAttachmentImages() {
  const instance = editor.value as any
  const workspaceId = String(props.workspaceId || '').trim()
  const projectCode = String(props.projectCode || '').trim()

  if (!instance || !workspaceId || !projectCode) {
    return
  }

  const seq = ++hydrateSeq
  const targets: Array<{ pos: number; attachmentId: string; permanentSrc: string }> = []

  instance.state.doc.descendants((node: any, pos: number) => {
    if (node?.type?.name !== 'image') return

    const attachmentId = String(node.attrs?.attachmentId || '').trim()
    if (!attachmentId) return

    const permanentSrc = String(node.attrs?.permanentSrc || node.attrs?.src || buildAssistantAttachmentContentUrl(workspaceId, projectCode, attachmentId)).trim()
    const currentSrc = String(node.attrs?.src || '').trim()

    if (currentSrc.startsWith('blob:')) return

    targets.push({ pos, attachmentId, permanentSrc })
  })

  for (const target of targets) {
    try {
      const objectUrl = await fetchAssistantAttachmentObjectUrl(workspaceId, projectCode, target.attachmentId)
      rememberObjectUrl(objectUrl)

      if (seq !== hydrateSeq || editor.value !== instance) {
        window.URL.revokeObjectURL(objectUrl)
        objectUrls.delete(objectUrl)
        continue
      }

      instance
        .chain()
        .setNodeSelection(target.pos)
        .updateAttributes('image', {
          src: objectUrl,
          permanentSrc: target.permanentSrc,
        })
        .run()
    } catch (error) {
      console.warn('[PrdRichTextEditor] hydrate image failed:', error)
    }
  }
}

function normalizeEditorHtmlForPersist(html: string): string {
  const source = String(html || '')

  if (typeof document === 'undefined') {
    return source
  }

  const element = document.createElement('div')
  element.innerHTML = source

  element.querySelectorAll('img').forEach((img) => {
    const permanentSrc = img.getAttribute('data-permanent-src') || ''
    if (permanentSrc) {
      img.setAttribute('src', permanentSrc)
      return
    }

    const attachmentId = img.getAttribute('data-attachment-id') || ''
    if (attachmentId && String(props.workspaceId || '').trim() && String(props.projectCode || '').trim()) {
      img.setAttribute(
        'src',
        buildAssistantAttachmentContentUrl(
          String(props.workspaceId || '').trim(),
          String(props.projectCode || '').trim(),
          attachmentId,
        ),
      )
    }
  })

  return element.innerHTML
}

function resolveErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message
  return fallback
}
</script>

<style scoped>
.prd-rich-editor {
  border: 1px solid #d7deea;
  border-radius: 14px;
  background: #ffffff;
  overflow: hidden;
}

.prd-rich-editor.disabled {
  opacity: 0.72;
}

.prd-rich-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px;
  border-bottom: 1px solid #e5eaf3;
  background: #f8fafc;
}

.prd-rich-toolbar button {
  border: 1px solid #cfd8e6;
  background: #ffffff;
  color: #1f2937;
  border-radius: 8px;
  padding: 5px 9px;
  font-size: 12px;
  cursor: pointer;
}

.prd-rich-toolbar button:hover:not(:disabled),
.prd-rich-toolbar button.active {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.prd-rich-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.toolbar-divider {
  width: 1px;
  min-height: 24px;
  background: #d7deea;
  margin: 0 2px;
}


.table-picker-wrap {
  position: relative;
  display: inline-flex;
}

.table-picker-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 50;
  width: 196px;
  padding: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.18);
}

.table-picker-label {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.table-picker-grid {
  display: grid;
  grid-template-columns: repeat(8, 18px);
  gap: 4px;
}

.prd-rich-toolbar .table-picker-cell {
  width: 18px;
  height: 18px;
  min-width: 18px;
  padding: 0;
  border-radius: 4px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
}

.prd-rich-toolbar .table-picker-cell.active {
  border-color: #2563eb;
  background: #dbeafe;
}

.hidden-file-input {
  display: none;
}

.rich-editor-message {
  padding: 8px 12px;
  border-bottom: 1px solid #e5eaf3;
  background: #f0f9ff;
  color: #0369a1;
  font-size: 12px;
}

.prd-rich-surface {
  min-height: 260px;
}

:deep(.prd-rich-editable) {
  min-height: 260px;
  padding: 16px;
  outline: none;
  line-height: 1.72;
  color: #111827;
}

:deep(.prd-rich-editable p) {
  margin: 0 0 10px;
}

:deep(.prd-rich-editable h2),
:deep(.prd-rich-editable h3),
:deep(.prd-rich-editable h4) {
  margin: 18px 0 10px;
  line-height: 1.35;
}

:deep(.prd-rich-editable ul),
:deep(.prd-rich-editable ol) {
  padding-left: 24px;
  margin: 8px 0 12px;
}

:deep(.prd-rich-editable a) {
  color: #2563eb;
  text-decoration: underline;
}

:deep(.prd-rich-editable img),
:deep(.prd-rich-image) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 12px 0;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

:deep(.prd-rich-editable table),
:deep(.prd-rich-table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  table-layout: fixed;
}

:deep(.prd-rich-editable th),
:deep(.prd-rich-editable td),
:deep(.prd-rich-table th),
:deep(.prd-rich-table td) {
  border: 1px solid #cbd5e1;
  padding: 6px 8px;
  vertical-align: top;
  min-width: 80px;
  height: 34px;
}

:deep(.prd-rich-editable th),
:deep(.prd-rich-table th) {
  background: #f1f5f9;
  font-weight: 700;
}

:deep(.is-editor-empty:first-child::before) {
  color: #94a3b8;
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
}

/* A9.10.14.4.3.15.7.24L3B3A_PRD_HEADER_AND_EDITOR_TOOLBAR_POLISH */
.prd-rich-editor {
  overflow: visible !important;
}

.prd-rich-toolbar {
  position: sticky !important;
  top: 10px !important;
  z-index: 30 !important;
  border: 1px solid #dbeafe !important;
  border-radius: 14px 14px 0 0 !important;
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.98) 0%, rgba(238, 246, 255, 0.96) 100%) !important;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
}

.prd-rich-toolbar .toolbar-select,
.prd-rich-toolbar .toolbar-color-control {
  height: 30px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #1f2937;
  font-size: 12px;
  font-weight: 800;
}

.prd-rich-toolbar .toolbar-select {
  padding: 0 8px;
  outline: none;
}

.prd-rich-toolbar .font-size-select {
  min-width: 74px;
}

.prd-rich-toolbar .toolbar-color-control {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 7px;
  line-height: 1;
}

.prd-rich-toolbar .toolbar-color-control input[type='color'] {
  width: 22px;
  height: 22px;
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.prd-rich-toolbar .toolbar-color-control input[type='color']::-webkit-color-swatch-wrapper {
  padding: 0;
}

.prd-rich-toolbar .toolbar-color-control input[type='color']::-webkit-color-swatch {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

/* A9.10.14.4.3.15.7.24L3B3C_HEADER_BUTTON_AND_STICKY_TOOLBAR_FIX */
.prd-rich-toolbar {
  position: sticky !important;
  top: 0 !important;
  z-index: 80 !important;
  margin-top: 0 !important;
  border-radius: 0 !important;
  border-left: none !important;
  border-right: none !important;
  background: rgba(239, 246, 255, 0.98) !important;
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08) !important;
}

.prd-rich-editor {
  overflow: visible !important;
}

.prd-rich-toolbar {
  gap: 6px !important;
  padding: 8px 10px !important;
}

.prd-rich-toolbar button,
.prd-rich-toolbar .toolbar-select,
.prd-rich-toolbar .toolbar-color-control {
  height: 28px !important;
  min-height: 28px !important;
  border-radius: 7px !important;
  font-size: 12px !important;
}

.prd-rich-toolbar .toolbar-select {
  min-width: 70px !important;
  padding: 0 6px !important;
}

.prd-rich-toolbar .font-size-select {
  min-width: 70px !important;
}

.prd-rich-toolbar .toolbar-color-control {
  gap: 4px !important;
  padding: 0 6px !important;
}

.prd-rich-toolbar .toolbar-color-control input[type='color'] {
  width: 20px !important;
  height: 20px !important;
}

.prd-rich-toolbar .toolbar-divider {
  margin: 0 2px !important;
}

/* A9.10.14.4.3.15.7.24L3B3D_HEADER_ALIGN_AND_SMART_STICKY_TOOLBAR */
.prd-rich-editor {
  position: relative !important;
  overflow: visible !important;
  border-radius: 14px !important;
}

.prd-rich-toolbar-sentinel {
  width: 100%;
  height: 1px;
  margin: 0;
  padding: 0;
  pointer-events: none;
}

.prd-rich-toolbar {
  position: sticky !important;
  top: 0 !important;
  z-index: 90 !important;
  display: flex !important;
  flex-wrap: nowrap !important;
  align-items: center !important;
  gap: 6px !important;
  overflow-x: auto !important;
  overflow-y: hidden !important;
  white-space: nowrap !important;
  margin: 0 !important;
  padding: 8px 10px !important;
  border-radius: 14px 14px 0 0 !important;
  border: 1px solid #dbeafe !important;
  border-bottom-color: #cfe0ff !important;
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.99) 0%, rgba(238, 246, 255, 0.98) 100%) !important;
  box-shadow: none !important;
  backdrop-filter: blur(10px);
  transition: border-radius 0.12s ease, box-shadow 0.12s ease, background 0.12s ease;
  scrollbar-width: thin;
}

.prd-rich-toolbar.is-stuck {
  border-radius: 0 !important;
  border-left-color: transparent !important;
  border-right-color: transparent !important;
  background: rgba(239, 246, 255, 0.985) !important;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08) !important;
}

.prd-rich-toolbar:not(.is-stuck) {
  border-radius: 14px 14px 0 0 !important;
  box-shadow: none !important;
}

.prd-rich-toolbar button,
.prd-rich-toolbar .toolbar-select,
.prd-rich-toolbar .toolbar-color-control {
  flex: 0 0 auto !important;
  height: 28px !important;
  min-height: 28px !important;
  border-radius: 7px !important;
  font-size: 12px !important;
}

.prd-rich-toolbar .toolbar-select {
  min-width: 70px !important;
  padding: 0 6px !important;
}

.prd-rich-toolbar .font-size-select {
  min-width: 70px !important;
}

.prd-rich-toolbar .toolbar-color-control {
  gap: 4px !important;
  padding: 0 6px !important;
}

.prd-rich-toolbar .toolbar-color-control input[type='color'] {
  width: 20px !important;
  height: 20px !important;
}

.prd-rich-toolbar .toolbar-divider {
  flex: 0 0 auto !important;
  margin: 0 2px !important;
}
</style>
