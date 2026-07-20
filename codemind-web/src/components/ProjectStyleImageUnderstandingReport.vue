<template>
  <section
    v-if="items.length"
    class="image-understanding-report"
  >
    <header>
      <div>
        <p class="eyebrow">Reference Image Understanding</p>
        <h4>参考图片理解报告</h4>
        <span>
          已读取 {{ items.length }} 张参考图的服务端视觉理解结果。这里展示的是抽象视觉信号，不用于复制具体竞品页面。
        </span>
      </div>

      <button
        type="button"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开' }}
      </button>
    </header>

    <div class="summary-grid">
      <article>
        <small>图片数量</small>
        <strong>{{ items.length }}</strong>
      </article>
      <article>
        <small>主要页面类型</small>
        <strong>{{ formatPageTypeHint(dominantPageType) }}</strong>
      </article>
      <article>
        <small>主色候选</small>
        <strong>
          <i
            v-if="dominantPrimaryColor !== '—'"
            :style="{ background: dominantPrimaryColor }"
          />
          {{ dominantPrimaryColor }}
        </strong>
      </article>
      <article>
        <small>整体倾向</small>
        <strong>{{ formatToneLabel(dominantToneLabel) }}</strong>
      </article>
    </div>

    <div
      v-if="expanded"
      class="image-report-list"
    >
      <article
        v-for="item in items"
        :key="item.attachmentId || item.fileName"
        class="image-report-card"
      >
        <div class="image-report-title">
          <div>
            <strong>{{ item.fileName || '未命名参考图' }}</strong>
            <span>
              {{ formatSize(item.imageWidth, item.imageHeight) }}
              <em v-if="item.pageTypeHint">· {{ formatPageTypeHint(item.pageTypeHint) }}</em>
              <em v-if="item.status">· {{ formatStatus(item.status) }}</em>
            </span>
          </div>

          <span class="source-tag">{{ formatSource(item.source) }}</span>
        </div>

        <p v-if="item.summary">
          {{ item.summary }}
        </p>

        <div
          v-if="item.palette.length"
          class="palette-row"
        >
          <small>色板</small>
          <div>
            <i
              v-for="color in item.palette"
              :key="color"
              :style="{ background: color }"
              :title="color"
            />
          </div>
        </div>

        <div class="signal-columns">
          <section v-if="item.layoutSignals.length">
            <h5>布局信号</h5>
            <ul>
              <li
                v-for="signal in item.layoutSignals"
                :key="signal"
              >
                {{ formatImageSignal(signal) }}
              </li>
            </ul>
          </section>

          <section v-if="item.visualToneSignals.length">
            <h5>视觉气质</h5>
            <ul>
              <li
                v-for="signal in item.visualToneSignals"
                :key="signal"
              >
                {{ formatImageSignal(signal) }}
              </li>
            </ul>
          </section>

          <section v-if="item.componentSignals.length">
            <h5>组件倾向</h5>
            <ul>
              <li
                v-for="signal in item.componentSignals"
                :key="signal"
              >
                {{ formatImageSignal(signal) }}
              </li>
            </ul>
          </section>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
  ref,
} from 'vue'

interface NormalizedImageUnderstanding {
  attachmentId: string
  fileName: string
  mimeType: string
  status: string
  source: string
  imageWidth: number | null
  imageHeight: number | null
  primaryColor: string
  palette: string[]
  toneLabel: string
  pageTypeHint: string
  layoutSignals: string[]
  visualToneSignals: string[]
  componentSignals: string[]
  summary: string
}

const props = defineProps<{
  signals: Record<string, unknown> | null | undefined
}>()

const expanded = ref(true)

const items = computed<NormalizedImageUnderstanding[]>(() => {
  const raw = resolveRawImageUnderstandings(props.signals)

  return raw
    .map(normalizeImageUnderstanding)
    .filter((item) => Boolean(item.fileName || item.attachmentId || item.summary))
})

const dominantPageType = computed(() => {
  return mostFrequent(
    items.value.map((item) => item.pageTypeHint).filter(Boolean),
  ) || '—'
})

const dominantPrimaryColor = computed(() => {
  return mostFrequent(
    items.value.map((item) => item.primaryColor).filter(Boolean),
  ) || '—'
})

const dominantToneLabel = computed(() => {
  return mostFrequent(
    items.value.map((item) => item.toneLabel).filter(Boolean),
  ) || '—'
})

function resolveRawImageUnderstandings(
  signals: Record<string, unknown> | null | undefined,
): Record<string, unknown>[] {
  if (!signals) {
    return []
  }

  const direct = signals.serverImageUnderstandings
    || signals.server_image_understandings

  if (Array.isArray(direct)) {
    return direct
      .filter((item): item is Record<string, unknown> => Boolean(item && typeof item === 'object'))
  }

  return []
}

function normalizeImageUnderstanding(
  value: Record<string, unknown>,
): NormalizedImageUnderstanding {
  return {
    attachmentId: asString(value.attachmentId || value.attachment_id),
    fileName: asString(value.fileName || value.file_name),
    mimeType: asString(value.mimeType || value.mime_type),
    status: asString(value.status),
    source: asString(value.source),
    imageWidth: asNumber(value.imageWidth || value.image_width),
    imageHeight: asNumber(value.imageHeight || value.image_height),
    primaryColor: normalizeColor(asString(value.primaryColor || value.primary_color)),
    palette: normalizePalette(value.palette),
    toneLabel: asString(value.toneLabel || value.tone_label),
    pageTypeHint: asString(value.pageTypeHint || value.page_type_hint),
    layoutSignals: normalizeStringList(value.layoutSignals || value.layout_signals),
    visualToneSignals: normalizeStringList(value.visualToneSignals || value.visual_tone_signals),
    componentSignals: normalizeStringList(value.componentSignals || value.component_signals),
    summary: asString(value.summary),
  }
}

function normalizePalette(
  value: unknown,
): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => normalizeColor(asString(item)))
    .filter(Boolean)
    .slice(0, 8)
}

function normalizeStringList(
  value: unknown,
): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => asString(item))
    .filter(Boolean)
    .slice(0, 12)
}

function mostFrequent(
  values: string[],
): string {
  const counts = new Map<string, number>()

  for (const value of values) {
    counts.set(
      value,
      (counts.get(value) || 0) + 1,
    )
  }

  return Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1])[0]?.[0] || ''
}

function formatSize(
  width: number | null,
  height: number | null,
) {
  if (!width || !height) {
    return '尺寸未知'
  }

  return `${width}×${height}`
}


function formatPageTypeHint(
  value: string,
) {
  const map: Record<string, string> = {
    ECOMMERCE_HOME: '电商首页',
    DASHBOARD: '数据看板 / 管理后台',
    AUTH_PAGE: '登录 / 注册页',
    BRAND_GRAPHIC: '品牌宣传图',
    WIDE_PAGE_OR_BANNER: '横向页面 / Banner',
    UNKNOWN_REFERENCE_IMAGE: '未知参考图',
  }

  return map[value] || value || '—'
}

function formatStatus(
  value: string,
) {
  const map: Record<string, string> = {
    UNDERSTOOD: '已理解',
    SKIPPED_NON_IMAGE: '非图片',
    IMAGE_FILE_NOT_FOUND: '文件未找到',
    IMAGE_READ_FAILED: '读取失败',
    IMAGE_READ_EXCEPTION: '读取异常',
  }

  return map[value] || value || '未知状态'
}

function formatSource(
  value: string,
) {
  const map: Record<string, string> = {
    SERVER_IMAGE_UNDERSTANDING_BASIC: '服务端图片理解',
    SERVER_IMAGE_UNDERSTANDING: '服务端图片理解',
  }

  return map[value] || value || '服务端图片理解'
}

function formatToneLabel(
  value: string,
) {
  return value || '—'
}

function formatImageSignal(
  value: string,
) {
  const map: Record<string, string> = {
    side_navigation: '侧边导航',
    top_navigation: '顶部导航',
    card_grid: '卡片网格',
    hero_banner: '大横幅 / 轮播区域',
    wide_horizontal_composition: '横向宽屏构图',
    layout_unknown_from_pixels: '仅凭像素暂无法判断明确布局',
    dark_background_tendency: '深色背景倾向',
    light_background_tendency: '浅色背景倾向',
    high_saturation: '高饱和',
    low_saturation: '低饱和',
    tech_modern_tone: '现代科技感',
    commerce_promotional_tone: '电商促销感',
    large_radius_cards: '大圆角卡片',
    prominent_call_to_action: '突出按钮 / 搜索入口',
    repeated_card_items: '重复卡片内容',
    visually_strong_accent_blocks: '高识别度强调色块',
    component_signals_need_manual_confirmation: '组件特征需要人工确认',
    metadata_only: '仅基于附件元数据判断',
  }

  if (value.startsWith('primary_color_candidate:')) {
    return `主色候选：${value.replace('primary_color_candidate:', '')}`
  }

  return map[value] || value
}

function asString(
  value: unknown,
) {
  if (value == null) {
    return ''
  }

  return String(value)
}

function asNumber(
  value: unknown,
) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed)
      ? parsed
      : null
  }

  return null
}

function normalizeColor(
  value: string,
) {
  const text = value.trim()

  if (/^#[0-9a-fA-F]{6}$/.test(text)) {
    return text.toLowerCase()
  }

  return ''
}
</script>

<style scoped>
.image-understanding-report {
  display: grid;
  gap: 12px;
  border: 1px solid #bae6fd;
  border-radius: 16px;
  padding: 14px;
  background:
    radial-gradient(circle at 100% 0, rgba(14, 165, 233, 0.12), transparent 32%),
    #f8fafc;
}

.image-understanding-report,
.image-understanding-report * {
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.eyebrow {
  margin: 0 0 5px;
  color: #0284c7;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h4,
h5 {
  margin: 0;
  color: #0f172a;
}

header span,
p,
small,
li {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

header button {
  align-self: flex-start;
  min-height: 34px;
  border: 1px solid #bae6fd;
  border-radius: 11px;
  padding: 0 12px;
  background: #ffffff;
  color: #0369a1;
  font-weight: 850;
  cursor: pointer;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.summary-grid article {
  display: grid;
  gap: 5px;
  border-radius: 12px;
  padding: 10px;
  background: #ffffff;
}

.summary-grid strong {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #0f172a;
  font-size: 13px;
}

.summary-grid i,
.palette-row i {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.14);
}

.image-report-list {
  display: grid;
  gap: 10px;
}

.image-report-card {
  display: grid;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.image-report-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.image-report-title div {
  display: grid;
  gap: 4px;
}

.image-report-title strong {
  color: #0f172a;
  font-size: 13px;
}

.image-report-title span {
  color: #64748b;
  font-size: 12px;
}

.image-report-title em {
  font-style: normal;
}

.source-tag {
  align-self: flex-start;
  border-radius: 999px;
  padding: 5px 8px;
  background: #e0f2fe;
  color: #0369a1 !important;
  font-size: 11px !important;
  font-weight: 900;
  white-space: nowrap;
}

p {
  margin: 0;
}

.palette-row {
  display: grid;
  gap: 7px;
}

.palette-row div {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.signal-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.signal-columns section {
  min-width: 0;
  border-radius: 12px;
  padding: 10px;
  background: #f8fafc;
}

ul {
  margin: 7px 0 0;
  padding-left: 18px;
}

@media (max-width: 840px) {
  .summary-grid,
  .signal-columns {
    grid-template-columns: 1fr;
  }

  header,
  .image-report-title {
    display: grid;
  }
}
</style>
