<template>
  <section class="manual-document-card-view">
    <div class="manual-edit-banner">
      <div>
        <p>手动文档修订版</p>
        <h4>{{ title }}</h4>
        <span>
          当前卡片视图根据已保存的文档视图重新拆分展示，因此会跟随文档编辑结果同步变化。
        </span>
      </div>

      <div class="manual-edit-meta">
        <strong v-if="artifact?.versionNo">V{{ artifact.versionNo }}</strong>
        <span v-if="editedAt">编辑时间：{{ formatDateTime(editedAt) }}</span>
        <span v-if="editedBy">编辑人：{{ editedBy }}</span>
      </div>
    </div>

    <div
      v-if="sections.length"
      class="manual-section-grid"
    >
      <article
        v-for="section in sections"
        :key="section.key"
        class="manual-section-card"
      >
        <header>
          <span>{{ section.levelLabel }}</span>
          <h5>{{ section.title }}</h5>
        </header>

        <div
          class="manual-section-body"
          v-html="section.html"
        />
      </article>
    </div>

    <article
      v-else
      class="manual-section-card"
    >
      <header>
        <span>DOCUMENT</span>
        <h5>{{ title }}</h5>
      </header>

      <div
        class="manual-section-body"
        v-html="renderedMarkdown"
      />
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type { ArtifactResponse } from '../types/codemind'

type SectionItem = {
  key: string
  title: string
  body: string
  html: string
  levelLabel: string
}

const props = defineProps<{
  title: string
  artifact: ArtifactResponse | null
  markdown: string
}>()

const renderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const manualEditInfo = computed<Record<string, unknown>>(() => {
  const content = props.artifact?.content

  if (!content || typeof content !== 'object') {
    return {}
  }

  const value = (content as Record<string, unknown>)['_manual_document_edit']

  return value && typeof value === 'object'
    ? value as Record<string, unknown>
    : {}
})

const editedAt = computed(() =>
  typeof manualEditInfo.value.editedAt === 'string'
    ? manualEditInfo.value.editedAt
    : '',
)

const editedBy = computed(() =>
  typeof manualEditInfo.value.editedBy === 'string'
    ? manualEditInfo.value.editedBy
    : '',
)

const renderedMarkdown = computed(() =>
  DOMPurify.sanitize(renderer.render(props.markdown || '')),
)

const sections = computed<SectionItem[]>(() =>
  parseMarkdownSections(props.markdown || '').slice(0, 40),
)

function parseMarkdownSections(markdown: string): SectionItem[] {
  const lines = markdown.split(/\r?\n/)
  const result: SectionItem[] = []

  let currentTitle = ''
  let currentLevel = 0
  let currentBody: string[] = []

  function flush() {
    if (!currentTitle && currentBody.join('\n').trim().length === 0) {
      return
    }

    const body = currentBody.join('\n').trim()
    const html = DOMPurify.sanitize(renderer.render(body || '暂无正文。'))

    result.push({
      key: `${result.length}_${currentTitle || 'document'}`,
      title: currentTitle || props.title,
      body,
      html,
      levelLabel: currentLevel ? `H${currentLevel}` : 'DOCUMENT',
    })
  }

  for (const line of lines) {
    const heading = line.match(/^(#{1,4})\s+(.+?)\s*$/)

    if (heading) {
      flush()
      currentTitle = heading[2].trim()
      currentLevel = heading[1].length
      currentBody = []
      continue
    }

    currentBody.push(line)
  }

  flush()

  return result.filter((item) => item.title || item.body)
}

function formatDateTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}
</script>

<style scoped>
.manual-document-card-view {
  display: grid;
  gap: 16px;
}

.manual-edit-banner {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  padding: 16px;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
}

.manual-edit-banner p {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.manual-edit-banner h4 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.manual-edit-banner span {
  display: block;
  margin-top: 7px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.manual-edit-meta {
  display: grid;
  gap: 4px;
  align-content: start;
  min-width: 180px;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

.manual-edit-meta strong {
  color: #1d4ed8;
  font-size: 16px;
}

.manual-section-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.manual-section-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 15px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.manual-section-card header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.manual-section-card header span {
  border-radius: 999px;
  padding: 3px 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 10px;
  font-weight: 900;
}

.manual-section-card header h5 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.manual-section-body {
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.manual-section-body :deep(p) {
  margin: 0 0 8px;
}

.manual-section-body :deep(ul),
.manual-section-body :deep(ol) {
  margin: 0 0 8px;
  padding-left: 18px;
}

.manual-section-body :deep(code) {
  border-radius: 5px;
  padding: 1px 4px;
  background: #f1f5f9;
  color: #334155;
}
</style>
