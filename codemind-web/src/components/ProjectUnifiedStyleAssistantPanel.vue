<template>
  <section class="unified-style-assistant">
    <header class="assistant-header">
      <div>
        <p class="eyebrow">Unified Style Assistant</p>
        <h3>风格助手</h3>
        <span>
          输入目标风格，或上传参考图/竞品截图/品牌图并说明要提取的风格特征，统一转换为项目 Theme Profile。
        </span>
      </div>
    </header>

    <label class="field-block">
      <span>风格说明</span>
      <textarea
        v-model="instruction"
        :disabled="isSuggesting || isApplying || isUploading"
        placeholder="例如：参考这个竞品后台截图，但不要照搬，只提取它的科技感、层次和配色。或者：把系统做得更像金融后台，稳重、专业、信息密度高。"
      />
    </label>

    <label class="field-block">
      <span>参考材料补充说明</span>
      <textarea
        v-model="referenceNotes"
        :disabled="isSuggesting || isApplying || isUploading"
        placeholder="可选。例如：截图里是深色科技后台，蓝紫渐变、高级卡片、大圆角、左侧导航。"
      />
    </label>

    <section class="upload-zone">
      <div class="upload-copy">
        <strong>参考图 / 截图 / 品牌图</strong>
        <span>
          图片会先上传为项目附件，再由后端读取像素、提取主色、色板、明度、饱和度和布局提示；浏览器本地解析作为即时兜底。
        </span>
      </div>

      <label class="upload-button">
        <input
          type="file"
          accept="image/*"
          multiple
          :disabled="isSuggesting || isApplying || isUploading"
          @change="handleFileChanged"
        />
        <span>{{ isUploading ? '上传中' : '选择文件' }}</span>
      </label>
    </section>

    <div
      v-if="attachmentDrafts.length"
      class="file-list"
    >
      <article
        v-for="draft in attachmentDrafts"
        :key="draft.localId"
        class="file-pill"
        :class="draft.status.toLowerCase()"
        :title="draft.errorMessage || draft.fileName"
      >
        {{ draft.fileName }}
        <em v-if="draft.status === 'UPLOADING'">上传中</em>
        <em v-else-if="draft.status === 'READY'">已上传</em>
        <em v-else>失败</em>
        <button
          type="button"
          :disabled="isSuggesting || isApplying || isUploading"
          @click="removeAttachment(draft.localId)"
        >
          ×
        </button>
      </article>
    </div>

    <div
      v-if="imageAnalyses.length"
      class="image-analysis-list"
    >
      <article
        v-for="analysis in imageAnalyses"
        :key="analysis.fileName"
        class="image-analysis-card"
      >
        <div>
          <strong>{{ analysis.fileName }}</strong>
          <span>{{ analysis.width }}×{{ analysis.height }}，{{ analysis.toneLabel }}</span>
        </div>
        <div class="palette">
          <i
            v-for="color in analysis.palette"
            :key="color"
            :style="{ background: color }"
          />
        </div>
      </article>
    </div>

    <div class="control-row">
      <label>
        <span>目标页面</span>
        <select
          v-model="targetPageType"
          :disabled="isSuggesting || isApplying || isUploading"
        >
          <option value="">不限</option>
          <option value="LOGIN_FORM">登录/注册页</option>
          <option value="CRUD_LIST">列表/CRUD 页面</option>
          <option value="DASHBOARD">Dashboard</option>
          <option value="DETAIL_PAGE">详情页</option>
          <option value="PROFILE_PAGE">资料页</option>
          <option value="SETTINGS_FORM">设置页</option>
        </select>
      </label>

      <label class="check-row">
        <input
          v-model="allowDarkMode"
          :disabled="isSuggesting || isApplying || isUploading"
          type="checkbox"
        />
        <span>允许暗色风格建议</span>
      </label>
    </div>

    <div
      v-if="errorMessage"
      class="message error"
    >
      {{ errorMessage }}
    </div>

    <div
      v-if="appliedMessage"
      class="message success"
    >
      {{ appliedMessage }}
    </div>

    <div class="actions">
      <button
        type="button"
        :disabled="!canSuggest || isSuggesting || isApplying || isUploading"
        @click="suggest"
      >
        {{ isSuggesting ? '分析中...' : '生成风格建议' }}
      </button>

      <button
        type="button"
        class="primary"
        :disabled="!canApplySelectedChanges"
        @click="applySuggestion"
      >
        {{ isApplying ? '应用中...' : '应用所选风格字段' }}
      </button>
    </div>

    <article
      v-if="suggestion"
      class="suggestion-card"
    >
      <header>
        <strong>{{ friendlySummary }}</strong>
        <span>
          识别模式：{{ formatAnalysisMode(suggestion.analysisMode) }}
          <em>|</em>
          Agent：{{ formatAgentMode(suggestion.agentMode) }}
          <em>|</em>
          置信度：{{ Math.round((suggestion.confidence || 0) * 100) }}%
        </span>
      </header>

      <section class="apply-guard">
        <div class="guard-head">
          <div>
            <h4>应用前确认</h4>
            <p>
              共识别 {{ changedRows.length }} 项变更，已选择 {{ selectedChangedCount }} 项。
              默认只勾选发生变化的字段；低置信度建议需要人工核对后再应用。
            </p>
          </div>

          <div class="guard-actions">
            <button
              type="button"
              :disabled="!changedRows.length || isApplying"
              @click="selectAllChangedFields"
            >
              全选变更
            </button>
            <button
              type="button"
              :disabled="!selectedChangedCount || isApplying"
              @click="clearSelectedFields"
            >
              清空选择
            </button>
          </div>
        </div>

        <div
          v-if="!currentProfile"
          class="message warning"
        >
          当前风格配置尚未加载完成，建议刷新后再应用字段级变更。
        </div>

        <div class="change-table">
          <article
            v-for="row in changeRows"
            :key="row.key"
            class="change-row"
            :class="{
              changed: row.changed,
              unchanged: !row.changed,
              selected: row.selected,
              low: row.lowConfidence,
            }"
          >
            <label>
              <input
                type="checkbox"
                :disabled="!row.changed || isApplying"
                :checked="row.selected"
                @change="toggleChangeField(row.key)"
              />
              <span>{{ row.label }}</span>
            </label>

            <div class="value-pair">
              <div>
                <small>当前</small>
                <strong>
                  <i
                    v-if="row.isColor && row.before !== '—'"
                    :style="{ background: row.before }"
                  />
                  {{ row.before }}
                </strong>
              </div>

              <b>→</b>

              <div>
                <small>建议</small>
                <strong>
                  <i
                    v-if="row.isColor && row.after !== '—'"
                    :style="{ background: row.after }"
                  />
                  {{ row.after }}
                </strong>
              </div>
            </div>

            <em v-if="!row.changed">无变化</em>
            <em
              v-else-if="row.lowConfidence"
              class="risk"
            >
              低置信度
            </em>
          </article>
        </div>
      </section>

      <ProjectStyleImageUnderstandingReport
        v-if="suggestion.extractedSignals"
        :signals="suggestion.extractedSignals"
      />

      <dl>
        <div>
          <dt>行业</dt>
          <dd>{{ formatFieldValue('industry', suggestion.industry) }}</dd>
        </div>
        <div>
          <dt>主题</dt>
          <dd>{{ formatFieldValue('themeKey', suggestion.themeKey) }}</dd>
        </div>
        <div>
          <dt>主色</dt>
          <dd>
            <i :style="{ background: suggestion.primaryColor }" />
            {{ suggestion.primaryColor }}
          </dd>
        </div>
        <div>
          <dt>密度</dt>
          <dd>{{ formatFieldValue('density', suggestion.density) }}</dd>
        </div>
        <div>
          <dt>圆角</dt>
          <dd>{{ formatFieldValue('radius', suggestion.radius) }}</dd>
        </div>
        <div>
          <dt>导航</dt>
          <dd>{{ formatFieldValue('navigationStyle', suggestion.navigationStyle) }}</dd>
        </div>
      </dl>

      <section v-if="extractedSignalEntries.length">
        <h4>提取到的风格信号</h4>
        <ul>
          <li
            v-for="signal in extractedSignalEntries"
            :key="signal.key"
          >
            <strong>{{ signal.key }}：</strong>{{ signal.value }}
          </li>
        </ul>
      </section>

      <section v-if="friendlyReasons.length">
        <h4>判断依据</h4>
        <ul>
          <li
            v-for="reason in friendlyReasons"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul>
      </section>

      <section v-if="friendlyWarnings.length">
        <h4>提醒</h4>
        <ul>
          <li
            v-for="warning in friendlyWarnings"
            :key="warning"
          >
            {{ warning }}
          </li>
        </ul>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
  ref,
} from 'vue'
import ProjectStyleImageUnderstandingReport from './ProjectStyleImageUnderstandingReport.vue'
import {
  fetchProjectThemeProfile,
  updateProjectThemeProfile,
} from '../api/themeProfileApi'
import { analyzeProjectStyleAssistant } from '../api/themeProfileStyleAssistantApi'
import { uploadAssistantAttachment } from '../api/assistantApi'
import type {
  ProjectThemeProfile,
  UpdateProjectThemeProfileRequest,
} from '../types/themeProfile'
import type {
  ProjectStyleAssistantResponse,
} from '../types/themeProfileStyleAssistant'

type AttachmentUploadStatus = 'UPLOADING' | 'READY' | 'FAILED'

type ThemeChangeFieldKey =
  | 'industry'
  | 'themeKey'
  | 'visualTone'
  | 'primaryColor'
  | 'secondaryColor'
  | 'accentColor'
  | 'density'
  | 'radius'
  | 'loginLayout'
  | 'navigationStyle'
  | 'darkModeEnabled'
  | 'notes'

interface ThemeChangeField {
  key: ThemeChangeFieldKey
  label: string
  isColor?: boolean
}

interface LocalImageAnalysis {
  fileName: string
  width: number
  height: number
  primaryColor: string
  palette: string[]
  averageLightness: number
  averageSaturation: number
  toneLabel: string
  summary: string
}

interface AttachmentDraft {
  localId: string
  fileName: string
  status: AttachmentUploadStatus
  attachmentId?: string
  mimeType?: string
  size?: number
  parseStatus?: string
  metadata?: Record<string, unknown>
  errorMessage?: string
}

interface SignalEntry {
  key: string
  value: string
}

interface ChangeRow {
  key: ThemeChangeFieldKey
  label: string
  before: string
  after: string
  rawBefore: unknown
  rawAfter: unknown
  changed: boolean
  selected: boolean
  lowConfidence: boolean
  isColor: boolean
}

const THEME_CHANGE_FIELDS: ThemeChangeField[] = [
  {
    key: 'industry',
    label: '项目行业',
  },
  {
    key: 'themeKey',
    label: '主题风格',
  },
  {
    key: 'visualTone',
    label: '视觉气质',
  },
  {
    key: 'primaryColor',
    label: '主色',
    isColor: true,
  },
  {
    key: 'secondaryColor',
    label: '辅助色',
    isColor: true,
  },
  {
    key: 'accentColor',
    label: '强调色',
    isColor: true,
  },
  {
    key: 'density',
    label: '页面密度',
  },
  {
    key: 'radius',
    label: '圆角风格',
  },
  {
    key: 'loginLayout',
    label: '登录布局',
  },
  {
    key: 'navigationStyle',
    label: '导航风格',
  },
  {
    key: 'darkModeEnabled',
    label: '暗色模式偏好',
  },

]

const FIELD_VALUE_LABELS: Partial<Record<ThemeChangeFieldKey, Record<string, string>>> = {
  industry: {
    GENERAL: '通用企业',
    COMMERCE: '电商零售',
    TECH: '科技平台',
    INDUSTRIAL: '工业制造',
    FINANCE: '金融业务',
    GOV: '政务公共服务',
    EDUCATION: '教育培训',
    HEALTHCARE: '医疗健康',
  },
  themeKey: {
    enterprise_default: '企业默认',
    tech_modern: '现代科技',
    commerce_light: '轻电商',
    industrial_pro: '工业专业',
    finance_stable: '金融稳重',
    gov_clean: '政务清爽',
  },
  visualTone: {
    CLEAN: '清爽克制',
    TECH: '科技感',
    COMMERCE: '电商活力',
    INDUSTRIAL: '工业专业',
    FINANCE: '金融稳重',
    GOV: '政务清爽',
  },
  density: {
    COMPACT: '紧凑',
    STANDARD: '标准',
    COMFORTABLE: '舒展',
  },
  radius: {
    SMALL: '小圆角',
    MEDIUM: '中圆角',
    LARGE: '大圆角',
  },
  loginLayout: {
    BRAND_SPLIT: '品牌分栏',
    CENTER_CARD: '居中卡片',
    MINIMAL: '极简登录',
  },
  navigationStyle: {
    SIDE_NAV: '侧边导航',
    TOP_NAV: '顶部导航',
    MIXED: '混合导航',
  },
}

const SIGNAL_KEY_LABELS: Record<string, string> = {
  colorIntent: '颜色倾向',
  layoutIntent: '布局倾向',
  densityIntent: '信息密度',
  radiusIntent: '圆角倾向',
  toneKeywords: '风格关键词',
  source: '判断来源',
  hasReferenceMaterial: '是否使用参考材料',
  hasServerImageUnderstanding: '是否使用服务端图片理解',
  styleKeywords: '识别关键词',
  analysisMode: '识别模式',
}

const SIGNAL_VALUE_LABELS: Record<string, string> = {
  'vibrant pink-red primary with light, high-saturation palette': '参考图主色偏粉红，高饱和、偏明亮',
  'spacious, clean layout with emphasis on visual impact': '布局偏舒展清爽，强调视觉冲击力',
  'standard spacing, not too compact': '信息密度标准，不偏紧凑',
  'medium rounded corners for a friendly feel': '中等圆角，整体更亲和',
  energetic: '活力',
  consumer: '消费端',
  modern: '现代',
  light: '浅色',
  STYLE_INTENT_TEXT: '文字风格意图',
  REFERENCE_TEXT: '参考说明',
  REFERENCE_IMAGE: '参考图片',
  MIXED_REFERENCE: '图文混合参考',
  REFERENCE_IMAGE_PENDING: '参考图片待解析',
  MIXED_REFERENCE_PENDING: '图文混合参考待解析',
  JAVA_RULE_FALLBACK: 'Java 规则兜底',
  PYTHON_RULE_FALLBACK: 'Python 规则兜底',
}

const props = defineProps<{
  workspaceId: string
  projectCode: string
}>()

const emit = defineEmits<{
  suggested: [suggestion: ProjectStyleAssistantResponse]
  applied: [profile: ProjectThemeProfile]
}>()

const instruction = ref('')
const referenceNotes = ref('')
const targetPageType = ref('')
const allowDarkMode = ref(false)
const attachmentDrafts = ref<AttachmentDraft[]>([])
const imageAnalyses = ref<LocalImageAnalysis[]>([])
const suggestion = ref<ProjectStyleAssistantResponse | null>(null)
const currentProfile = ref<ProjectThemeProfile | null>(null)
const selectedChangeKeys = ref<ThemeChangeFieldKey[]>([])
const isSuggesting = ref(false)
const isApplying = ref(false)
const isUploading = ref(false)
const errorMessage = ref('')
const appliedMessage = ref('')

const readyAttachments = computed(() => {
  return attachmentDrafts.value.filter((item) => item.status === 'READY' && item.attachmentId)
})

const canSuggest = computed(() => {
  return Boolean(
    instruction.value.trim()
      || referenceNotes.value.trim()
      || readyAttachments.value.length
      || imageAnalyses.value.length,
  )
})

const changedRows = computed(() => {
  return changeRows.value.filter((row) => row.changed)
})

const selectedChangedCount = computed(() => {
  return changeRows.value.filter((row) => row.changed && row.selected).length
})

const friendlySummary = computed(() => {
  if (!suggestion.value) {
    return ''
  }

  const changedLabels = changedRows.value
    .filter((row) => row.changed)
    .map((row) => row.label)

  const base = `建议使用 ${formatFieldValue('industry', suggestion.value.industry)} / ${formatFieldValue('themeKey', suggestion.value.themeKey)}，主色为 ${suggestion.value.primaryColor}，页面密度为 ${formatFieldValue('density', suggestion.value.density)}。`

  if (!changedLabels.length) {
    return `${base} 当前项目风格与本次建议基本一致，无需大幅调整。`
  }

  return `${base} 本次主要建议调整：${changedLabels.join('、')}。`
})

const friendlyReasons = computed(() => {
  if (!suggestion.value) {
    return []
  }

  const reasons: string[] = []
  const mode = suggestion.value.analysisMode

  if (mode === 'REFERENCE_IMAGE' || mode === 'MIXED_REFERENCE') {
    reasons.push('已结合服务端参考图片理解结果，提取主色、色板、布局信号和视觉气质。')
  } else if (mode === 'REFERENCE_TEXT' || mode === 'MIXED_REFERENCE_PENDING') {
    reasons.push('已根据参考说明抽象出风格方向，不复制具体竞品页面。')
  } else {
    reasons.push('已根据用户输入的目标风格生成项目级风格建议。')
  }

  reasons.push(`建议主题为 ${formatFieldValue('themeKey', suggestion.value.themeKey)}，视觉气质为 ${formatFieldValue('visualTone', suggestion.value.visualTone)}。`)
  reasons.push(`建议页面密度为 ${formatFieldValue('density', suggestion.value.density)}，圆角为 ${formatFieldValue('radius', suggestion.value.radius)}，导航为 ${formatFieldValue('navigationStyle', suggestion.value.navigationStyle)}。`)

  if (changedRows.value.length) {
    reasons.push(`本次与当前配置相比共有 ${changedRows.value.length} 项字段变化，可在“应用前确认”中选择性应用。`)
  }

  return reasons
})

const friendlyWarnings = computed(() => {
  const warnings: string[] = []

  if (!suggestion.value) {
    return warnings
  }

  if ((suggestion.value.confidence || 0) < 0.62) {
    warnings.push('本次建议置信度偏低，建议只勾选明确需要调整的字段。')
  }

  const primary = String(suggestion.value.primaryColor || '')
  if (primary && /#(ff|f6|ef|e1|dc)/i.test(primary)) {
    warnings.push('当前主色偏鲜艳，作为大面积背景使用前建议检查可读性和品牌适配度。')
  }

  for (const warning of suggestion.value.warnings || []) {
    if (/[一-龥]/.test(warning) && !warnings.includes(warning)) {
      warnings.push(warning)
    }
  }

  warnings.push('系统只提取抽象风格特征，不复制具体竞品页面、品牌资产或文案。')

  return Array.from(new Set(warnings)).slice(0, 8)
})


const canApplySelectedChanges = computed(() => {
  return Boolean(
    suggestion.value
      && selectedChangedCount.value
      && !isSuggesting.value
      && !isApplying.value
      && !isUploading.value,
  )
})

const changeRows = computed<ChangeRow[]>(() => {
  if (!suggestion.value) {
    return []
  }

  const confidence = Number(suggestion.value.confidence || 0)

  return THEME_CHANGE_FIELDS.map((field) => {
    const before = resolveCurrentFieldValue(field.key)
    const after = resolveSuggestedFieldValue(field.key)
    const changed = !isEqualFieldValue(before, after)

    return {
      key: field.key,
      label: field.label,
      before: formatFieldValue(field.key, before),
      after: formatFieldValue(field.key, after),
      rawBefore: before,
      rawAfter: after,
      changed,
      selected: selectedChangeKeys.value.includes(field.key),
      lowConfidence: changed && confidence > 0 && confidence < 0.62,
      isColor: Boolean(field.isColor),
    }
  })
})

const extractedSignalEntries = computed<SignalEntry[]>(() => {
  const raw = suggestion.value?.extractedSignals

  if (!raw) {
    return []
  }

  if (Array.isArray(raw)) {
    return raw.map((value, index) => ({
      key: `signal_${index + 1}`,
      value: stringifySignalValue(value),
    }))
  }

  if (typeof raw === 'object') {
    const hiddenKeys = new Set([
      'serverImageUnderstanding',
      'serverImageUnderstandingCount',
      'serverImageUnderstandings',
      'server_image_understanding',
      'server_image_understanding_count',
      'server_image_understandings',
    ])

    return Object.entries(raw)
      .filter(([key]) => !hiddenKeys.has(key))
      .map(([key, value]) => ({
        key: formatSignalKey(key),
        value: formatSignalValue(value),
      }))
  }

  return [
    {
      key: 'signal',
      value: stringifySignalValue(raw),
    },
  ]
})

async function handleFileChanged(
  event: Event,
) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])

  suggestion.value = null
  selectedChangeKeys.value = []
  errorMessage.value = ''
  appliedMessage.value = ''

  if (!files.length) {
    return
  }

  isUploading.value = true

  try {
    for (const file of files) {
      const localId = `${file.name}-${file.size}-${Date.now()}-${Math.random().toString(16).slice(2)}`
      const draft: AttachmentDraft = {
        localId,
        fileName: file.name,
        status: 'UPLOADING',
        mimeType: file.type,
        size: file.size,
      }

      attachmentDrafts.value.push(draft)

      if (file.type.startsWith('image/')) {
        try {
          imageAnalyses.value.push(await analyzeImageFile(file))
        } catch (error) {
          imageAnalyses.value.push({
            fileName: file.name,
            width: 0,
            height: 0,
            primaryColor: '#64748b',
            palette: ['#64748b'],
            averageLightness: 0.5,
            averageSaturation: 0.2,
            toneLabel: '浏览器本地解析失败',
            summary: `${file.name} 本地解析失败：${resolveErrorMessage(error, '未知错误')}`,
          })
        }
      }

      try {
        const response = await uploadAssistantAttachment(
          props.workspaceId,
          props.projectCode,
          file,
        )

        draft.status = 'READY'
        draft.attachmentId = String(response.attachmentId)
        draft.fileName = response.fileName || file.name
        draft.mimeType = response.mimeType || file.type
        draft.size = response.size || file.size
        draft.parseStatus = response.parseStatus || ''
        draft.metadata = (response.metadata || {}) as Record<string, unknown>
      } catch (error) {
        draft.status = 'FAILED'
        draft.errorMessage = resolveErrorMessage(
          error,
          '附件上传失败。',
        )
      }
    }
  } finally {
    input.value = ''
    isUploading.value = false
  }
}

async function suggest() {
  if (!canSuggest.value) {
    return
  }

  isSuggesting.value = true
  errorMessage.value = ''
  appliedMessage.value = ''

  try {
    const mergedReferenceText = buildMergedReferenceText()

    const [profile, response] = await Promise.all([
      fetchProjectThemeProfile(
        props.workspaceId,
        props.projectCode,
      ),
      analyzeProjectStyleAssistant(
        props.workspaceId,
        props.projectCode,
        {
          instruction: instruction.value.trim() || mergedReferenceText || '请根据参考材料提取项目风格。',
          referenceText: mergedReferenceText || null,
          referenceNotes: referenceNotes.value.trim() || null,
          targetPageType: targetPageType.value || null,
          allowDarkMode: allowDarkMode.value,
          referenceMode: readyAttachments.value.length ? 'MIXED_REFERENCE' : 'AUTO',
          attachmentIds: readyAttachments.value.map((item) => item.attachmentId as string),
          attachmentNames: readyAttachments.value.map((item) => item.fileName),
        },
      ),
    ])

    currentProfile.value = profile
    suggestion.value = response
    selectedChangeKeys.value = resolveDefaultSelectedFields(response, profile)

    emit('suggested', response)
  } catch (error) {
    errorMessage.value = resolveErrorMessage(
      error,
      '生成风格建议失败。',
    )
  } finally {
    isSuggesting.value = false
  }
}

async function applySuggestion() {
  if (!suggestion.value || !selectedChangedCount.value) {
    return
  }

  isApplying.value = true
  errorMessage.value = ''
  appliedMessage.value = ''

  try {
    const baseProfile = currentProfile.value || await fetchProjectThemeProfile(
      props.workspaceId,
      props.projectCode,
    )

    currentProfile.value = baseProfile

    const appliedCount = selectedChangedCount.value

    const payload = buildSelectedUpdatePayload(
      baseProfile,
      suggestion.value,
      selectedChangeKeys.value,
    )

    const response = await updateProjectThemeProfile(
      props.workspaceId,
      props.projectCode,
      payload,
    )

    currentProfile.value = response
    selectedChangeKeys.value = []
    appliedMessage.value = `已应用 ${appliedCount} 个风格字段。`
    emit('applied', response)
  } catch (error) {
    errorMessage.value = resolveErrorMessage(
      error,
      '应用风格建议失败。',
    )
  } finally {
    isApplying.value = false
  }
}

function selectAllChangedFields() {
  selectedChangeKeys.value = changedRows.value.map((row) => row.key)
}

function clearSelectedFields() {
  selectedChangeKeys.value = []
}

function toggleChangeField(
  key: ThemeChangeFieldKey,
) {
  if (selectedChangeKeys.value.includes(key)) {
    selectedChangeKeys.value = selectedChangeKeys.value.filter((item) => item !== key)
  } else {
    selectedChangeKeys.value = [...selectedChangeKeys.value, key]
  }
}

function removeAttachment(
  localId: string,
) {
  const draft = attachmentDrafts.value.find((item) => item.localId === localId)

  attachmentDrafts.value = attachmentDrafts.value.filter((item) => item.localId !== localId)

  if (draft) {
    imageAnalyses.value = imageAnalyses.value.filter((item) => item.fileName !== draft.fileName)
  }

  suggestion.value = null
  selectedChangeKeys.value = []
}

function buildMergedReferenceText() {
  const parts: string[] = []

  if (referenceNotes.value.trim()) {
    parts.push(referenceNotes.value.trim())
  }

  if (imageAnalyses.value.length) {
    parts.push(
      [
        '【浏览器本地参考图解析】',
        ...imageAnalyses.value.map((analysis) => analysis.summary),
      ].join('\n'),
    )
  }

  if (readyAttachments.value.length) {
    parts.push(
      [
        '【已上传服务端附件】',
        ...readyAttachments.value.map((attachment) => {
          const metadataText = attachment.metadata
            ? JSON.stringify(attachment.metadata)
            : '{}'

          return `${attachment.fileName}：attachmentId=${attachment.attachmentId}，parseStatus=${attachment.parseStatus || 'UNKNOWN'}，metadata=${metadataText}`
        }),
      ].join('\n'),
    )
  }

  return parts.join('\n\n')
}

function resolveDefaultSelectedFields(
  response: ProjectStyleAssistantResponse,
  profile: ProjectThemeProfile,
) {
  return THEME_CHANGE_FIELDS
    .filter((field) => {
      const before = resolveProfileFieldValue(
        profile,
        field.key,
      )
      const after = resolveSuggestionFieldValue(
        response,
        field.key,
      )

      return !isEqualFieldValue(before, after)
    })
    .map((field) => field.key)
}

function buildSelectedUpdatePayload(
  profile: ProjectThemeProfile,
  response: ProjectStyleAssistantResponse,
  selectedKeys: ThemeChangeFieldKey[],
): UpdateProjectThemeProfileRequest {
  const payload: Record<string, unknown> = {
    industry: profile.industry,
    themeKey: profile.themeKey,
    visualTone: profile.visualTone,
    primaryColor: profile.primaryColor,
    secondaryColor: profile.secondaryColor || null,
    accentColor: profile.accentColor || null,
    density: profile.density,
    radius: profile.radius,
    loginLayout: profile.loginLayout,
    navigationStyle: profile.navigationStyle,
    darkModeEnabled: profile.darkModeEnabled,
    locked: profile.locked,
    notes: profile.notes || null,
  }

  for (const key of selectedKeys) {
    payload[key] = resolveSuggestionFieldValue(
      response,
      key,
    )
  }

  return payload as unknown as UpdateProjectThemeProfileRequest
}

function resolveCurrentFieldValue(
  key: ThemeChangeFieldKey,
) {
  return currentProfile.value
    ? resolveProfileFieldValue(currentProfile.value, key)
    : null
}

function resolveProfileFieldValue(
  profile: ProjectThemeProfile,
  key: ThemeChangeFieldKey,
) {
  if (key === 'notes') {
    return profile.notes || null
  }

  return (profile as unknown as Record<string, unknown>)[key] ?? null
}

function resolveSuggestedFieldValue(
  key: ThemeChangeFieldKey,
) {
  return suggestion.value
    ? resolveSuggestionFieldValue(suggestion.value, key)
    : null
}

function resolveSuggestionFieldValue(
  response: ProjectStyleAssistantResponse,
  key: ThemeChangeFieldKey,
) {
  const updatePayload = (response.updatePayload || {}) as unknown as Record<string, unknown>

  if (Object.prototype.hasOwnProperty.call(updatePayload, key)) {
    return updatePayload[key]
  }

  if (key === 'notes') {
    return response.summary || null
  }

  return (response as unknown as Record<string, unknown>)[key] ?? null
}

function isEqualFieldValue(
  first: unknown,
  second: unknown,
) {
  return normalizeComparableValue(first) === normalizeComparableValue(second)
}

function normalizeComparableValue(
  value: unknown,
) {
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }

  if (typeof value === 'string') {
    return value.trim()
  }

  if (value == null) {
    return ''
  }

  return JSON.stringify(value)
}

function formatFieldValue(
  key: ThemeChangeFieldKey,
  value: unknown,
) {
  if (value == null || value === '') {
    return '—'
  }

  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }

  const text = typeof value === 'string'
    ? value.trim()
    : stringifySignalValue(value)

  const labelMap = FIELD_VALUE_LABELS[key] || {}

  return labelMap[text] || text
}

function analyzeImageFile(
  file: File,
): Promise<LocalImageAnalysis> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    const url = URL.createObjectURL(file)

    image.onload = () => {
      try {
        const maxSize = 96
        const scale = Math.min(
          1,
          maxSize / Math.max(image.width, image.height),
        )
        const width = Math.max(1, Math.round(image.width * scale))
        const height = Math.max(1, Math.round(image.height * scale))
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height

        const context = canvas.getContext('2d', {
          willReadFrequently: true,
        })

        if (!context) {
          throw new Error('无法创建 Canvas 解析上下文。')
        }

        context.drawImage(image, 0, 0, width, height)

        const data = context.getImageData(0, 0, width, height).data
        const buckets = new Map<string, number>()
        let lightnessSum = 0
        let saturationSum = 0
        let sampleCount = 0

        for (let index = 0; index < data.length; index += 16) {
          const alpha = data[index + 3]
          if (alpha < 80) {
            continue
          }

          const r = data[index]
          const g = data[index + 1]
          const b = data[index + 2]
          const hsl = rgbToHsl(r, g, b)

          lightnessSum += hsl.l
          saturationSum += hsl.s
          sampleCount++

          const qr = Math.round(r / 32) * 32
          const qg = Math.round(g / 32) * 32
          const qb = Math.round(b / 32) * 32
          const color = rgbToHex(
            Math.min(255, qr),
            Math.min(255, qg),
            Math.min(255, qb),
          )

          buckets.set(
            color,
            (buckets.get(color) || 0) + 1,
          )
        }

        const palette = Array.from(buckets.entries())
          .sort((a, b) => b[1] - a[1])
          .map(([color]) => color)
          .slice(0, 6)

        const finalPalette = palette.length ? palette : ['#64748b']
        const averageLightness = sampleCount ? lightnessSum / sampleCount : 0.5
        const averageSaturation = sampleCount ? saturationSum / sampleCount : 0.2
        const toneLabel = buildToneLabel(averageLightness, averageSaturation)
        const primaryColor = pickPrimaryColor(finalPalette)

        resolve({
          fileName: file.name,
          width: image.width,
          height: image.height,
          primaryColor,
          palette: finalPalette,
          averageLightness,
          averageSaturation,
          toneLabel,
          summary: `${file.name}：浏览器本地解析尺寸 ${image.width}×${image.height}，主色候选 ${primaryColor}，主要色板 ${finalPalette.join(', ')}，整体倾向 ${toneLabel}。`,
        })
      } catch (error) {
        reject(error)
      } finally {
        URL.revokeObjectURL(url)
      }
    }

    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片加载失败。'))
    }

    image.src = url
  })
}


function formatAnalysisMode(
  value: string,
) {
  return SIGNAL_VALUE_LABELS[value] || value || '未知'
}

function formatAgentMode(
  value: string | null | undefined,
) {
  if (!value) {
    return '未知'
  }

  if (value === 'STYLE_ASSISTANT_LLM') {
    return '风格助手 Agent'
  }

  if (value === 'JAVA_RULE_FALLBACK') {
    return 'Java 规则兜底'
  }

  if (value === 'PYTHON_RULE_FALLBACK') {
    return 'Python 规则兜底'
  }

  return value
}

function formatSignalKey(
  key: string,
): string {
  return SIGNAL_KEY_LABELS[key] || key
}

function formatSignalValue(
  value: unknown,
): string {
  if (Array.isArray(value)) {
    return value
      .map((item) => translateSignalToken(String(item)))
      .join('、')
  }

  if (typeof value === 'object' && value !== null) {
    return simplifyObjectSignal(value as Record<string, unknown>)
  }

  return translateSignalToken(String(value ?? ''))
}

function simplifyObjectSignal(
  value: Record<string, unknown>,
): string {
  const compact = Object.entries(value)
    .slice(0, 6)
    .map(([signalKey, item]) => `${formatSignalKey(signalKey)}：${formatSignalValue(item)}`)

  return compact.join('；')
}

function translateSignalToken(
  value: string,
): string {
  const trimmed = value.trim()

  if (!trimmed) {
    return ''
  }

  return SIGNAL_VALUE_LABELS[trimmed] || trimmed
}

function stringifySignalValue(
  value: unknown,
) {
  if (value == null) {
    return ''
  }

  if (typeof value === 'string') {
    return value
  }

  if (
    typeof value === 'number'
    || typeof value === 'boolean'
  ) {
    return String(value)
  }

  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function pickPrimaryColor(
  palette: string[],
) {
  const nonNeutral = palette.find((color) => {
    const { s, l } = hexToHsl(color)
    return s > 0.18 && l > 0.12 && l < 0.88
  })

  return nonNeutral || palette[0] || '#2563eb'
}

function buildToneLabel(
  lightness: number,
  saturation: number,
) {
  const lightLabel = lightness < 0.34
    ? '偏深色'
    : lightness > 0.72
      ? '偏浅色'
      : '明度适中'

  const saturationLabel = saturation < 0.2
    ? '低饱和'
    : saturation > 0.52
      ? '高饱和'
      : '中等饱和'

  return `${lightLabel}、${saturationLabel}`
}

function rgbToHex(
  r: number,
  g: number,
  b: number,
) {
  return `#${[r, g, b]
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('')}`
}

function rgbToHsl(
  r: number,
  g: number,
  b: number,
) {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  let h = 0
  let s = 0
  const l = (max + min) / 2

  if (max !== min) {
    const d = max - min
    s = l > 0.5
      ? d / (2 - max - min)
      : d / (max + min)

    switch (max) {
      case rn:
        h = (gn - bn) / d + (gn < bn ? 6 : 0)
        break
      case gn:
        h = (bn - rn) / d + 2
        break
      default:
        h = (rn - gn) / d + 4
        break
    }

    h /= 6
  }

  return {
    h,
    s,
    l,
  }
}

function hexToHsl(
  color: string,
) {
  const normalized = color.replace('#', '')
  const r = parseInt(normalized.slice(0, 2), 16)
  const g = parseInt(normalized.slice(2, 4), 16)
  const b = parseInt(normalized.slice(4, 6), 16)
  return rgbToHsl(r, g, b)
}

function resolveErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}
</script>

<style scoped>
.unified-style-assistant {
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 18px;
  background:
    radial-gradient(circle at 0 0, rgba(79, 70, 229, 0.10), transparent 34%),
    #ffffff;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.06);
}

.unified-style-assistant,
.unified-style-assistant * {
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.assistant-header {
  margin-bottom: 14px;
}

.eyebrow {
  margin: 0 0 5px;
  color: #4f46e5;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h3,
h4 {
  margin: 0;
  color: #0f172a;
}

.assistant-header span,
.upload-copy span,
dt,
li {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.field-block,
.control-row label {
  display: grid;
  gap: 7px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.field-block + .field-block {
  margin-top: 14px;
}

textarea,
select {
  width: 100%;
  min-width: 0;
  min-height: 38px;
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 10px;
  background: #ffffff;
  color: #334155;
  outline: none;
}

textarea {
  min-height: 88px;
  resize: vertical;
}

.upload-zone {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  border: 1px dashed #c7d2fe;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.upload-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.upload-copy strong {
  color: #0f172a;
  font-size: 13px;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 86px;
  max-width: 120px;
  min-height: 38px;
  border-radius: 12px;
  background: #4f46e5;
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  white-space: nowrap;
}

.upload-button input {
  display: none;
}

.file-list,
.image-analysis-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.file-pill {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  padding: 7px 11px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 12px;
  font-weight: 900;
}

.file-pill.ready {
  background: #ecfdf5;
  color: #047857;
}

.file-pill.failed {
  background: #fef2f2;
  color: #b91c1c;
}

.file-pill em {
  font-style: normal;
  opacity: 0.72;
}

.file-pill button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-weight: 900;
}

.image-analysis-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #dbe4ef;
  border-radius: 13px;
  padding: 10px;
  background: #ffffff;
}

.image-analysis-card div:first-child {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.image-analysis-card strong {
  color: #0f172a;
  font-size: 12px;
}

.image-analysis-card span {
  color: #64748b;
  font-size: 12px;
}

.palette {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.palette i,
dd i,
.value-pair i {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.12);
}

.control-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  margin-top: 14px;
  align-items: end;
}

.check-row {
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 8px;
  min-height: 38px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.actions button,
.guard-actions button {
  min-height: 38px;
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 0 14px;
  background: #ffffff;
  color: #334155;
  font-weight: 850;
  cursor: pointer;
}

.actions button.primary {
  border-color: #4f46e5;
  background: #4f46e5;
  color: #ffffff;
}

.actions button:disabled,
.guard-actions button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.message {
  margin-top: 14px;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
}

.message.error {
  background: #fef2f2;
  color: #b91c1c;
}

.message.success {
  background: #f0fdf4;
  color: #166534;
}

.message.warning {
  background: #fffbeb;
  color: #92400e;
}

.suggestion-card {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  border: 1px solid #dbe4ef;
  border-radius: 16px;
  padding: 16px;
  background: rgba(248, 250, 252, 0.72);
}

.suggestion-card header {
  display: grid;
  gap: 4px;
}

.suggestion-card header strong {
  color: #0f172a;
  line-height: 1.6;
}

.suggestion-card header span {
  color: #64748b;
  font-size: 12px;
}

.suggestion-card header em {
  margin: 0 6px;
  color: #cbd5e1;
  font-style: normal;
}

.apply-guard {
  display: grid;
  gap: 12px;
  border: 1px solid #c7d2fe;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.guard-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.guard-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.guard-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  justify-content: flex-end;
}

.change-table {
  display: grid;
  gap: 8px;
}

.change-row {
  display: grid;
  grid-template-columns: minmax(110px, 0.72fr) minmax(0, 1.8fr) auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px;
  background: #ffffff;
}

.change-row.changed {
  border-color: #c7d2fe;
}

.change-row.selected {
  background: #eef2ff;
}

.change-row.unchanged {
  opacity: 0.72;
}

.change-row.low {
  border-color: #fbbf24;
}

.change-row label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 900;
}

.value-pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.value-pair div {
  min-width: 0;
  display: grid;
  gap: 4px;
  border-radius: 10px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.68);
}

.value-pair small {
  color: #94a3b8;
  font-size: 11px;
}

.value-pair strong {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #0f172a;
  font-size: 12px;
  font-weight: 850;
}

.value-pair b {
  color: #94a3b8;
}

.change-row > em {
  min-width: 54px;
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
  text-align: right;
}

.change-row > em.risk {
  color: #b45309;
  font-weight: 900;
}

dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

dl div {
  min-width: 0;
  border-radius: 12px;
  padding: 10px;
  background: #ffffff;
}

dt {
  margin-bottom: 4px;
}

dd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 850;
}

ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

li strong {
  color: #334155;
}

@media (max-width: 760px) {
  .upload-zone,
  .control-row,
  .image-analysis-card,
  dl,
  .change-row,
  .guard-head {
    grid-template-columns: 1fr;
  }

  .guard-head {
    display: grid;
  }

  .guard-actions {
    justify-content: flex-start;
  }

  .palette {
    justify-content: flex-start;
  }

  .change-row > em {
    text-align: left;
  }
}
</style>
