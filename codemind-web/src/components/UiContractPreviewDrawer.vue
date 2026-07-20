
<template>
  <Teleport to="body">
    <div v-if="visible" class="preview-mask" @click.self="$emit('close')">
      <section class="preview-workbench" :class="[
        themeClass,
        {
          'demo-mode': demoMode,
          'inspector-open': inspectorVisible,
        },
      ]">
        <header v-if="!demoMode" class="preview-topbar">
          <div class="topbar-title">
            <p class="eyebrow">交互原型 / UI_CONTRACT</p>
            <h2>{{ title }}</h2>
            <span>
              Artboard Preview · 以真实页面画布等比例预览，调试信息不挤压用户页面
            </span>
          </div>

          <div class="topbar-actions">
            <div class="segmented-control" aria-label="设备尺寸">
              <button v-for="preset in devicePresets" :key="preset.key" type="button"
                :class="{ active: selectedDeviceKey === preset.key }" @click="selectedDeviceKey = preset.key">
                {{ preset.label }}
              </button>
            </div>

            <select v-model="zoomMode" class="zoom-select" aria-label="缩放比例">
              <option value="fit">适应窗口</option>
              <option value="100">100%</option>
              <option value="75">75%</option>
              <option value="50">50%</option>
            </select>

            <button type="button" class="ghost-button" :class="{ active: inspectorVisible }"
              @click="inspectorVisible = !inspectorVisible">
              {{ inspectorVisible ? '收起信息' : '页面信息' }}
            </button>

            <button type="button" class="ghost-button" @click="demoMode = true">
              演示模式
            </button>

            <button
              type="button"
              class="primary-button"
              :disabled="!contract || confirming || confirmedForCurrent"
              @click="handleConfirmClick"
            >
              {{ confirmButtonLabel }}
            </button>

            <button type="button" class="close-button" @click="$emit('close')">
              ×
            </button>
          </div>
        </header>

        <button v-if="demoMode" type="button" class="demo-exit-button" @click="demoMode = false">
          退出演示
        </button>

        <main class="preview-main">
          <section class="preview-content">
            <div v-if="!demoMode" class="preview-tabs">
              <button :class="{ active: activeTab === 'runtime' }" type="button" @click="activeTab = 'runtime'">
                交互预览
              </button>
              <button :class="{ active: activeTab === 'contract' }" type="button" @click="activeTab = 'contract'">
                契约检查
              </button>

              <div
                class="confirmation-meta"
                :class="{
                  confirmed: confirmedForCurrent,
                  stale: confirmedSnapshot && !confirmedForCurrent,
                }"
              >
                <strong>{{ confirmationStatusLabel }}</strong>
                <span>{{ confirmationStatusText }}</span>
              </div>

              <div class="device-meta">
                <strong>{{ selectedDevice.label }}</strong>
                <span>{{ selectedDevice.width }} × {{ selectedDevice.height }}</span>
                <span>{{ Math.round(effectiveScale * 100) }}%</span>
              </div>
            </div>

            <section v-if="contract && activeTab === 'runtime'" ref="stageRef" class="preview-stage">
              <div class="stage-grid-bg" />

              <div class="artboard-frame" :style="artboardFrameStyle">
                <div class="artboard-scale" :style="artboardScaleStyle">
                  <UiContractPreviewRuntime :contract="contract" :project-theme-profile="projectThemeProfile"
                    display-mode="artboard" />
                </div>
              </div>
            </section>


            <section v-else-if="contract && activeTab === 'contract'" class="contract-stage">
              <UiContractView :contract="contract" />
            </section>

            <div v-else class="empty-box">
              <strong>当前资产不是有效的 UI_CONTRACT，无法预览。</strong>
              <span>请先在 PAGE 页面节点生成交互原型，或确认资产 content 中包含 pages、routes 和 mockData。</span>
            </div>
          </section>

          <aside v-if="contract && inspectorVisible && !demoMode" class="inspector-panel">
            <header>
              <div>
                <p class="eyebrow">Inspector</p>
                <h3>页面蓝图信息</h3>
              </div>
              <button type="button" @click="inspectorVisible = false">
                ×
              </button>
            </header>

            <div class="inspector-scroll">
              <section class="inspector-card">
                <p class="eyebrow">Page Contract</p>
                <h4>{{ currentPageTitle }}</h4>

                <dl class="kv-list">
                  <div>
                    <dt>路由</dt>
                    <dd>{{ currentRoute }}</dd>
                  </div>
                  <div>
                    <dt>页面类型</dt>
                    <dd>{{ currentPageKind }}</dd>
                  </div>
                  <div>
                    <dt>布局</dt>
                    <dd>{{ currentLayout }}</dd>
                  </div>
                  <div>
                    <dt>质量分</dt>
                    <dd>{{ qualityScore }}</dd>
                  </div>
                  <div>
                    <dt>覆盖率</dt>
                    <dd>{{ coverageRate }}</dd>
                  </div>
                </dl>
              </section>

              <section v-if="projectThemeProfile" class="inspector-card">
                <p class="eyebrow">Theme Profile</p>
                <h4>{{ projectThemeLabel }}</h4>

                <dl class="kv-list">
                  <div>
                    <dt>行业</dt>
                    <dd>{{ projectThemeProfile.industry }}</dd>
                  </div>
                  <div>
                    <dt>主题</dt>
                    <dd>{{ projectThemeProfile.themeKey }}</dd>
                  </div>
                  <div>
                    <dt>主色</dt>
                    <dd>{{ projectThemeProfile.primaryColor }}</dd>
                  </div>
                  <div>
                    <dt>密度</dt>
                    <dd>{{ projectThemeProfile.density }}</dd>
                  </div>
                </dl>
              </section>

              <section class="inspector-card">
                <p class="eyebrow">Mock Scenario</p>
                <h4>场景模式</h4>

                <div v-if="mockScenarioItems.length" class="pill-list">
                  <span v-for="item in mockScenarioItems" :key="item">
                    {{ item }}
                  </span>
                </div>

                <p v-else class="empty-tip">
                  暂未声明 Mock 场景。
                </p>
              </section>

              <section class="inspector-card">
                <p class="eyebrow">Page State</p>
                <h4>状态模拟</h4>

                <div v-if="pageStateItems.length" class="state-list">
                  <button v-for="item in pageStateItems" :key="item.key" type="button">
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.description }}</span>
                  </button>
                </div>

                <p v-else class="empty-tip">
                  暂未声明页面状态。
                </p>
              </section>

              <section class="inspector-card">
                <p class="eyebrow">API Bindings</p>
                <h4>接口绑定</h4>

                <div v-if="apiBindings.length" class="binding-list">
                  <article v-for="binding in apiBindings" :key="bindingKey(binding)">
                    <strong>{{ binding.method || '-' }} {{ binding.path || '-' }}</strong>
                    <span>{{ binding.bindingKey || binding.mockBinding || binding.mockAction || '-' }}</span>
                    <em>{{ binding.status || 'MOCK' }}</em>
                  </article>
                </div>

                <p v-else class="empty-tip">
                  暂无接口绑定。
                </p>
              </section>

              <section class="inspector-card">
                <p class="eyebrow">Runtime Data</p>
                <h4>模拟数据</h4>

                <div v-if="mockDataItems.length" class="data-list">
                  <article v-for="item in mockDataItems" :key="item.key">
                    <strong>{{ item.key }}</strong>
                    <span>{{ item.summary }}</span>
                  </article>
                </div>

                <p v-else class="empty-tip">
                  暂无模拟数据。
                </p>
              </section>

              <section class="inspector-card">
                <p class="eyebrow">Assumptions</p>
                <h4>当前假设</h4>

                <ul v-if="assumptionItems.length" class="assumption-list">
                  <li v-for="item in assumptionItems" :key="item">
                    {{ item }}
                  </li>
                </ul>

                <p v-else class="empty-tip">
                  暂无显式假设。
                </p>
              </section>
            </div>
          </aside>
        </main>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue'
import type { ArtifactResponse } from '../types/codemind'
import type { UiContractContent } from '../types/uiContract'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type { ConfirmedUiContractSnapshot } from '../types/uiContractSnapshot'
import UiContractPreviewRuntime from './UiContractPreviewRuntime.vue'
import UiContractView from './UiContractView.vue'

type DevicePreset = {
  key: string
  label: string
  width: number
  height: number
}

type RuntimeApiBinding = {
  method?: string
  path?: string
  bindingKey?: string
  mockBinding?: string
  mockAction?: string
  status?: string
}

type PreviewRecord = Record<string, any>

type PreviewContract = UiContractContent & PreviewRecord

const props = defineProps<{
  visible: boolean
  artifact: ArtifactResponse | null
  projectThemeProfile?: ProjectThemeProfile | null
  confirmedSnapshot?: ConfirmedUiContractSnapshot | null
  snapshotLoading?: boolean
  confirming?: boolean
  snapshotError?: string
}>()

const emit = defineEmits<{
  close: []
  confirmUiContract: [payload: { confirmationNote?: string }]
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
  {
    key: 'tablet',
    label: '平板',
    width: 1024,
    height: 768,
  },
  {
    key: 'mobile',
    label: '手机',
    width: 390,
    height: 844,
  },
]

const activeTab = ref<'runtime' | 'contract'>('runtime')
const inspectorVisible = ref(false)
const demoMode = ref(false)
const selectedDeviceKey = ref('desktop')
const zoomMode = ref<'fit' | '100' | '75' | '50'>('fit')
const stageRef = ref<HTMLElement | null>(null)
const stageSize = ref({
  width: 0,
  height: 0,
})

let resizeObserver: ResizeObserver | null = null

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      activeTab.value = 'runtime'
      inspectorVisible.value = false
      demoMode.value = false
      zoomMode.value = 'fit'
      await nextTick()
      refreshStageSize()
      ensureResizeObserver()
      return
    }

    disconnectResizeObserver()
  },
)

watch(
  () => activeTab.value,
  async () => {
    await nextTick()
    refreshStageSize()
  },
)

const contract = computed<PreviewContract | null>(() => {
  const content = props.artifact?.content

  if (!content || typeof content !== 'object') {
    return null
  }

  const candidate = content as PreviewContract

  if (
    candidate.artifactType !== 'UI_CONTRACT' &&
    props.artifact?.artifactType !== 'UI_CONTRACT'
  ) {
    return null
  }

  const normalizedPages = normalizePages(candidate)

  if (!normalizedPages.length) {
    return null
  }

  return {
    schemaVersion: candidate.schemaVersion || '1.0',
    artifactType: 'UI_CONTRACT',

    // 新版 UI_CONTRACT 必填字段
    identity: candidate.identity || {},
    page: candidate.page || {},
    interactions: Array.isArray(candidate.interactions)
      ? candidate.interactions
      : [],
    validationRules: Array.isArray(candidate.validationRules)
      ? candidate.validationRules
      : [],
    navigationTargets: Array.isArray(candidate.navigationTargets)
      ? candidate.navigationTargets
      : [],

    // 兼容旧 Runtime 的适配字段
    module: candidate.module || {},
    routes: Array.isArray(candidate.routes)
      ? candidate.routes
      : [],
    pages: normalizedPages,
    mockData: candidate.mockData || {},
    apiBindings: Array.isArray(candidate.apiBindings)
      ? candidate.apiBindings
      : [],
    developmentRules: candidate.developmentRules || {},
    analysisSummary: candidate.analysisSummary || '',
    mockScenarios: Array.isArray(candidate.mockScenarios)
      ? candidate.mockScenarios
      : [],
    pageStates: Array.isArray(candidate.pageStates)
      ? candidate.pageStates
      : [],
    featureCoverage: Array.isArray(candidate.featureCoverage)
      ? candidate.featureCoverage
      : [],
    qualitySummary:
      typeof candidate.qualitySummary === 'object' && candidate.qualitySummary
        ? candidate.qualitySummary
        : undefined,
    assumptions: Array.isArray(candidate.assumptions)
      ? candidate.assumptions
      : [],
  } as unknown as PreviewContract
})

const title = computed(() => {
  const moduleName =
    contract.value?.module?.module_name ||
    contract.value?.module?.moduleName ||
    firstPage.value?.pageName ||
    firstPage.value?.title ||
    props.artifact?.title ||
    '交互原型'

  return String(moduleName)
})

const firstPage = computed<PreviewRecord | null>(() => {
  return (contract.value?.pages?.[0] || null) as PreviewRecord | null
})

const currentArtifactId = computed(() => {
  return String(props.artifact?.artifactId || '')
})

const confirmedForCurrent = computed(() => {
  const snapshotArtifactId = props.confirmedSnapshot?.artifactId

  return Boolean(
    currentArtifactId.value &&
    snapshotArtifactId &&
    String(snapshotArtifactId) === currentArtifactId.value,
  )
})

const confirmationStatusLabel = computed(() => {
  if (props.snapshotLoading) {
    return '确认状态读取中'
  }

  if (confirmedForCurrent.value) {
    return '已确认'
  }

  if (props.confirmedSnapshot) {
    return '当前版本未确认'
  }

  return '待确认'
})

const confirmationStatusText = computed(() => {
  if (props.snapshotLoading) {
    return '正在读取该页面已确认的原型快照。'
  }

  if (confirmedForCurrent.value) {
    const versionNo = props.confirmedSnapshot?.artifactVersionNo
    const timeText = props.confirmedSnapshot?.confirmedAt
      ? `，确认时间：${formatDateTime(props.confirmedSnapshot.confirmedAt)}`
      : ''

    return `当前页面蓝图已确认${versionNo ? `，版本 V${versionNo}` : ''}${timeText}。`
  }

  if (props.confirmedSnapshot) {
    return '已存在历史确认快照，但当前打开的 UI_CONTRACT 版本尚未确认。'
  }

  if (props.snapshotError) {
    return props.snapshotError
  }

  return '请确认页面布局、风格、字段和交互符合预期后，再作为后续开发蓝图。'
})

const confirmButtonLabel = computed(() => {
  if (props.confirming) {
    return '确认中...'
  }

  return confirmedForCurrent.value ? '已确认' : '确认页面效果'
})

const selectedDevice = computed<DevicePreset>(() => {
  return (
    devicePresets.find((item) => item.key === selectedDeviceKey.value) ||
    devicePresets[0]
  )
})

const fitScale = computed(() => {
  if (!stageSize.value.width || !stageSize.value.height) {
    return 0.72
  }

  const horizontalPadding = demoMode.value ? 48 : 72
  const verticalPadding = demoMode.value ? 48 : 72

  const availableWidth = Math.max(stageSize.value.width - horizontalPadding, 320)
  const availableHeight = Math.max(stageSize.value.height - verticalPadding, 320)

  const scale = Math.min(
    availableWidth / selectedDevice.value.width,
    availableHeight / selectedDevice.value.height,
  )

  return Math.min(Math.max(scale, 0.28), 1)
})

const effectiveScale = computed(() => {
  if (zoomMode.value === '100') {
    return 1
  }

  if (zoomMode.value === '75') {
    return 0.75
  }

  if (zoomMode.value === '50') {
    return 0.5
  }

  return fitScale.value
})

const artboardFrameStyle = computed(() => ({
  width: `${selectedDevice.value.width * effectiveScale.value}px`,
  height: `${selectedDevice.value.height * effectiveScale.value}px`,
}))

const artboardScaleStyle = computed(() => ({
  width: `${selectedDevice.value.width}px`,
  height: `${selectedDevice.value.height}px`,
  transform: `scale(${effectiveScale.value})`,
}))

const artboardPageStyle = computed(() => ({
  width: `${selectedDevice.value.width}px`,
  height: `${selectedDevice.value.height}px`,
}))

const themeClass = computed(() => {
  const configuredClass = themeClassFromProjectTheme(
    props.projectThemeProfile,
  )

  if (configuredClass) {
    return configuredClass
  }

  const text = [
    props.artifact?.title,
    contract.value?.analysisSummary,
    firstPage.value?.pageName,
    firstPage.value?.title,
    firstPage.value?.description,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()

  if (
    includesAny(
      text,
      [
        '电商',
        '商城',
        '商品',
        '订单',
        '购物',
        '用户中心',
        'ecommerce',
        'shop',
        'mall',
        'order',
      ],
    )
  ) {
    return 'theme-commerce'
  }

  if (
    includesAny(
      text,
      [
        '科技',
        '智能',
        '数据',
        'ai',
        'saas',
        'platform',
        'dashboard',
      ],
    )
  ) {
    return 'theme-tech'
  }

  if (
    includesAny(
      text,
      [
        '工业',
        '设备',
        '工厂',
        '生产',
        '监控',
        'industrial',
        'iot',
      ],
    )
  ) {
    return 'theme-industrial'
  }

  return 'theme-enterprise'
})

const currentPageTitle = computed(() => {
  return String(
    firstPage.value?.pageName ||
    firstPage.value?.title ||
    firstPage.value?.pageId ||
    '当前页面',
  )
})

const currentRoute = computed(() => {
  const pageId = firstPage.value?.pageId

  const route = Array.isArray(contract.value?.routes)
    ? contract.value.routes.find(
      (item: PreviewRecord) => String(item.pageId) === String(pageId),
    )
    : undefined

  return String(route?.path || firstPage.value?.route || '-')
})

const currentPageKind = computed(() => {
  return String(firstPage.value?.pageKind || firstPage.value?.pageType || '-')
})

const currentLayout = computed(() => {
  const layout = firstPage.value?.layout

  if (typeof layout === 'string') {
    return layout
  }

  if (layout && typeof layout === 'object') {
    return String((layout as Record<string, unknown>).type || '-')
  }

  return '-'
})

const qualityScore = computed(() => {
  const quality = contract.value?.qualitySummary as PreviewRecord | undefined
  return String(quality?.score ?? '-')
})

const coverageRate = computed(() => {
  const quality = contract.value?.qualitySummary as PreviewRecord | undefined
  const rate = quality?.coverageRate

  if (typeof rate === 'number') {
    return `${rate}%`
  }

  return rate ? String(rate) : '-'
})

const apiBindings = computed<RuntimeApiBinding[]>(() => {
  return ((contract.value?.apiBindings || []) as RuntimeApiBinding[])
})

const mockScenarioItems = computed(() => {
  const items = (contract.value as Record<string, unknown> | null)?.mockScenarios

  if (!Array.isArray(items)) {
    return []
  }

  return items
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return ''
      }

      const source = item as Record<string, unknown>
      return String(
        source.scenarioName ||
        source.name ||
        source.title ||
        source.scenarioId ||
        source.key ||
        '',
      )
    })
    .filter(Boolean)
})

const pageStateItems = computed(() => {
  const items = (contract.value as Record<string, unknown> | null)?.pageStates

  if (!Array.isArray(items)) {
    return []
  }

  return items
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return null
      }

      const source = item as Record<string, unknown>
      const key = String(source.stateKey || source.key || '')
      const name = String(source.stateName || source.name || stateLabel(key))
      const description = String(source.description || source.message || '')

      return {
        key,
        name,
        description,
      }
    })
    .filter((item): item is { key: string; name: string; description: string } => Boolean(item?.key))
})

const mockDataItems = computed(() => {
  const mockData = contract.value?.mockData || {}

  return Object.entries(mockData).map(([key, value]) => {
    let summary = 'object'

    if (Array.isArray(value)) {
      summary = `${value.length} 条`
    } else if (value && typeof value === 'object') {
      summary = `${Object.keys(value as Record<string, unknown>).length} 个字段`
    }

    return {
      key,
      summary,
    }
  })
})

const assumptionItems = computed(() => {
  const assumptions = (contract.value as Record<string, unknown> | null)?.assumptions

  if (!Array.isArray(assumptions)) {
    return []
  }

  return assumptions
    .map((item) => String(item || '').trim())
    .filter(Boolean)
})

const projectThemeLabel = computed(() => {
  const themeKey = props.projectThemeProfile?.themeKey

  if (themeKey === 'commerce_light') return '电商零售'
  if (themeKey === 'tech_modern') return '科技平台'
  if (themeKey === 'industrial_pro') return '工业制造'
  if (themeKey === 'finance_stable') return '金融风控'
  if (themeKey === 'gov_clean') return '政务公共'

  return '通用企业'
})

function themeClassFromProjectTheme(
  profile?: ProjectThemeProfile | null,
): string {
  if (!profile) {
    return ''
  }

  if (profile.themeKey === 'commerce_light') return 'theme-commerce'
  if (profile.themeKey === 'tech_modern') return 'theme-tech'
  if (profile.themeKey === 'industrial_pro') return 'theme-industrial'

  return 'theme-enterprise'
}

function normalizePages(candidate: PreviewContract): PreviewRecord[] {
  if (Array.isArray(candidate.pages) && candidate.pages.length) {
    return candidate.pages
  }

  const singlePage = candidate.page

  if (!singlePage || typeof singlePage !== 'object') {
    return []
  }

  const page = singlePage as PreviewRecord
  const componentTree = page.componentTree
  const components = Array.isArray(page.components)
    ? page.components
    : Array.isArray(componentTree)
      ? componentTree
      : componentTree
        ? [componentTree]
        : []

  return [
    {
      pageId: String(page.pageId || ''),
      pageKey: String(page.pageKey || ''),
      pageName: String(page.pageName || page.title || '页面预览'),
      title: String(page.title || page.pageName || '页面预览'),
      description: String(page.description || ''),
      layout: page.layout || page.pageKind || 'DEFAULT',
      route: String(page.route || ''),
      components,
    },
  ]
}

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

function bindingKey(binding: RuntimeApiBinding): string {
  return [
    binding.method,
    binding.path,
    binding.bindingKey,
    binding.mockBinding,
    binding.mockAction,
  ].filter(Boolean).join(':') || JSON.stringify(binding)
}

function stateLabel(value: string): string {
  const key = String(value || '').toUpperCase()

  const labels: Record<string, string> = {
    DEFAULT: '默认态',
    LOADING: '加载中',
    ERROR: '错误态',
    SUCCESS: '成功态',
    EMPTY: '空状态',
    normal: '正常',
    loading: '加载中',
    error: '错误',
    empty: '空状态',
  }

  return labels[key] || labels[value] || value
}

function handleConfirmClick() {
  if (!contract.value || props.confirming || confirmedForCurrent.value) {
    return
  }

  const note = window.prompt(
    '确认当前页面效果作为后续开发蓝图？\n\n确认后系统会保存这一版 UI_CONTRACT、项目风格快照和资产版本信息。',
    '',
  )

  if (note === null) {
    return
  }

  emit('confirmUiContract', {
    confirmationNote: note.trim(),
  })
}

function formatDateTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}

function includesAny(text: string, keywords: string[]): boolean {
  return keywords.some((keyword) => text.includes(keyword.toLowerCase()))
}

onBeforeUnmount(() => {
  disconnectResizeObserver()
})
</script>

<style scoped>
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background:
    radial-gradient(circle at 15% 15%, rgba(37, 99, 235, 0.22), transparent 32%),
    radial-gradient(circle at 88% 8%, rgba(14, 165, 233, 0.16), transparent 28%),
    rgba(15, 23, 42, 0.54);
}

.preview-workbench {
  --brand-primary: #2563eb;
  --brand-secondary: #0ea5e9;
  --stage-bg: #eef3f8;
  --stage-grid: rgba(100, 116, 139, 0.14);
  --surface: #ffffff;
  --border: #dbe4ef;
  --text-main: #0f172a;
  --text-muted: #64748b;
  --shadow-strong: 0 28px 70px rgba(15, 23, 42, 0.22);
  --shadow-soft: 0 18px 48px rgba(15, 23, 42, 0.12);

  position: fixed;
  inset: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(203, 213, 225, 0.8);
  border-radius: 22px;
  background: #f8fafc;
  box-shadow: var(--shadow-strong);
}

.preview-workbench.theme-commerce {
  --brand-primary: #2563eb;
  --brand-secondary: #f97316;
  --stage-bg: #f4f7fb;
}

.preview-workbench.theme-tech {
  --brand-primary: #4f46e5;
  --brand-secondary: #06b6d4;
  --stage-bg: #edf2ff;
}

.preview-workbench.theme-industrial {
  --brand-primary: #0f766e;
  --brand-secondary: #2563eb;
  --stage-bg: #edf3f5;
}

.preview-workbench.demo-mode {
  inset: 0;
  border: none;
  border-radius: 0;
}

.preview-topbar {
  z-index: 20;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
}

.topbar-title {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 5px;
  color: var(--brand-primary);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.preview-topbar h2,
.inspector-panel h3,
.inspector-card h4 {
  margin: 0;
  color: var(--text-main);
}

.preview-topbar h2 {
  font-size: 21px;
  line-height: 1.2;
}

.preview-topbar span {
  display: block;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.segmented-control {
  display: flex;
  padding: 4px;
  border: 1px solid #dbe4ef;
  border-radius: 13px;
  background: #f8fafc;
}

.segmented-control button,
.preview-tabs button,
.ghost-button,
.primary-button,
.close-button,
.demo-exit-button {
  border: none;
  font: inherit;
  cursor: pointer;
}

.segmented-control button {
  border-radius: 9px;
  padding: 7px 10px;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.segmented-control button.active {
  background: #ffffff;
  color: var(--brand-primary);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.zoom-select {
  height: 36px;
  border: 1px solid #dbe4ef;
  border-radius: 11px;
  padding: 0 12px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.ghost-button,
.primary-button,
.close-button,
.demo-exit-button {
  border: 1px solid #dbe4ef;
  background: #ffffff;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.ghost-button,
.primary-button {
  height: 36px;
  border-radius: 11px;
  padding: 0 13px;
}

.primary-button {
  border-color: var(--brand-primary);
  background: var(--brand-primary);
  color: #ffffff;
}

.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.ghost-button.active {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
}

.close-button {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  font-size: 22px;
  line-height: 1;
}

.demo-exit-button {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 30;
  height: 38px;
  border-radius: 12px;
  padding: 0 14px;
  box-shadow: var(--shadow-soft);
}

.preview-main {
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.preview-workbench.inspector-open .preview-main {
  grid-template-columns: minmax(0, 1fr) 360px;
}

.preview-content {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.preview-tabs {
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: blur(10px);
}

.preview-tabs button {
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 8px 14px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.preview-tabs button.active {
  border-color: var(--brand-primary);
  background: rgba(37, 99, 235, 0.08);
  color: var(--brand-primary);
}

.confirmation-meta {
  display: grid;
  gap: 2px;
  min-width: 220px;
  margin-left: auto;
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 7px 10px;
  background: #ffffff;
  color: #64748b;
  font-size: 12px;
}

.confirmation-meta strong {
  color: #92400e;
  font-size: 12px;
}

.confirmation-meta.confirmed {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.confirmation-meta.confirmed strong {
  color: #166534;
}

.confirmation-meta.stale {
  border-color: #fde68a;
  background: #fffbeb;
}

.confirmation-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-meta {
  margin-left: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.device-meta strong {
  color: #0f172a;
}

.preview-stage {
  position: relative;
  min-width: 0;
  min-height: 0;
  flex: 1;
  display: grid;
  place-items: center;
  overflow: auto;
  padding: 32px;
  background: var(--stage-bg);
}

.stage-grid-bg {
  position: absolute;
  inset: 0;
  opacity: 0.55;
  background-image:
    linear-gradient(var(--stage-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--stage-grid) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(circle at center, #000 0%, transparent 78%);
  pointer-events: none;
}

.artboard-frame {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  overflow: visible;
}

.artboard-scale {
  transform-origin: top left;
}

.artboard-page {
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.42);
  border-radius: 20px;
  background: #ffffff;
  box-shadow:
    0 34px 90px rgba(15, 23, 42, 0.22),
    0 0 0 12px rgba(255, 255, 255, 0.42);
}

.contract-stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px;
  background: #f8fafc;
}

.inspector-panel {
  min-width: 0;
  border-left: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(14px);
  display: flex;
  flex-direction: column;
}

.inspector-panel>header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #e2e8f0;
}

.inspector-panel header button {
  width: 30px;
  height: 30px;
  border: 1px solid #dbe4ef;
  border-radius: 10px;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
}

.inspector-scroll {
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.inspector-card {
  margin-bottom: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 15px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
}

.kv-list {
  margin: 12px 0 0;
  display: grid;
  gap: 8px;
}

.kv-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.kv-list dt {
  color: #64748b;
}

.kv-list dd {
  margin: 0;
  color: #0f172a;
  font-weight: 800;
  text-align: right;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.pill-list span,
.binding-list em {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--brand-primary);
  font-size: 12px;
  font-weight: 800;
}

.state-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.state-list button {
  display: grid;
  gap: 3px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px;
  background: #f8fafc;
  color: #334155;
  text-align: left;
}

.state-list span {
  color: #64748b;
  font-size: 12px;
}

.binding-list,
.data-list {
  display: grid;
  gap: 9px;
  margin-top: 12px;
}

.binding-list article,
.data-list article {
  display: grid;
  gap: 4px;
  border-radius: 12px;
  padding: 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
}

.binding-list strong,
.data-list strong {
  color: #0f172a;
}

.assumption-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.empty-tip {
  margin: 12px 0 0;
  color: #94a3b8;
  font-size: 13px;
}

.empty-box {
  margin: 24px;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 24px;
  background: #ffffff;
  color: #475569;
}

.empty-box strong {
  display: block;
  margin-bottom: 8px;
  color: #0f172a;
}

/* Child runtime adaptation: only the user system page is rendered inside Artboard. */
.artboard-page :deep(.runtime-sidebar),
.artboard-page :deep(.runtime-inspector) {
  display: none !important;
}

.artboard-page :deep(.ui-runtime-shell) {
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: block;
  overflow: hidden;
  background: transparent;
}

.artboard-page :deep(.runtime-screen) {
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}

.artboard-page :deep(.screen-header) {
  flex: 0 0 auto;
  padding: 28px 48px 22px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.92);
}

.artboard-page :deep(.page-canvas) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 32px 48px;
}

.artboard-page :deep(.page-canvas.layout-CENTERED_FORM),
.artboard-page :deep(.page-canvas.layout-centered_form),
.artboard-page :deep(.page-canvas.layout-centered-form) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px;
  background:
    radial-gradient(circle at 18% 18%, rgba(37, 99, 235, 0.12), transparent 28%),
    radial-gradient(circle at 86% 12%, rgba(14, 165, 233, 0.10), transparent 24%),
    linear-gradient(135deg, #f8fafc 0%, #eef4ff 52%, #f8fafc 100%);
}

.theme-commerce .artboard-page :deep(.page-canvas.layout-CENTERED_FORM),
.theme-commerce .artboard-page :deep(.page-canvas.layout-centered_form),
.theme-commerce .artboard-page :deep(.page-canvas.layout-centered-form) {
  background:
    radial-gradient(circle at 16% 18%, rgba(37, 99, 235, 0.12), transparent 28%),
    radial-gradient(circle at 84% 20%, rgba(249, 115, 22, 0.12), transparent 24%),
    linear-gradient(135deg, #f8fbff 0%, #f2f7ff 55%, #fff7ed 100%);
}

.theme-tech .artboard-page :deep(.page-canvas.layout-CENTERED_FORM),
.theme-tech .artboard-page :deep(.page-canvas.layout-centered_form),
.theme-tech .artboard-page :deep(.page-canvas.layout-centered-form) {
  background:
    radial-gradient(circle at 20% 20%, rgba(79, 70, 229, 0.14), transparent 30%),
    radial-gradient(circle at 80% 16%, rgba(6, 182, 212, 0.14), transparent 26%),
    linear-gradient(135deg, #f8fbff 0%, #eef2ff 52%, #ecfeff 100%);
}

.artboard-page :deep(.runtime-component.component-form),
.artboard-page :deep(.node-form) {
  max-width: 760px;
  margin: auto;
  border: 1px solid rgba(203, 213, 225, 0.82);
  border-radius: 24px;
  padding: 28px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.12);
}

.artboard-page :deep(.form-grid) {
  gap: 18px;
}

.artboard-page :deep(input),
.artboard-page :deep(textarea),
.artboard-page :deep(select) {
  min-height: 42px;
  border-radius: 12px;
}

.artboard-page :deep(button) {
  border-radius: 12px;
}

@media (max-width: 1180px) {
  .preview-workbench.inspector-open .preview-main {
    grid-template-columns: minmax(0, 1fr);
  }

  .inspector-panel {
    position: absolute;
    top: 74px;
    right: 16px;
    bottom: 16px;
    z-index: 25;
    width: min(380px, calc(100vw - 48px));
    border: 1px solid #dbe4ef;
    border-radius: 18px;
    box-shadow: var(--shadow-strong);
  }
}

@media (max-width: 720px) {
  .preview-workbench {
    inset: 0;
    border-radius: 0;
  }

  .preview-topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .topbar-actions {
    justify-content: flex-start;
  }

  .preview-stage {
    padding: 18px;
  }

  .confirmation-meta,
  .device-meta {
    display: none;
  }
}
</style>
