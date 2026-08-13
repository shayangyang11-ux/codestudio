<template>
  <Teleport to="body">
    <section
      v-if="visible"
      class="module-app-studio-mask"
      role="dialog"
      aria-modal="true"
      aria-label="模块应用原型工作台"
    >
      <header class="module-app-studio-topbar">
        <div class="module-app-studio-title">
          <button type="button" class="studio-back-button" @click="emit('close')">
            ← 返回需求工作区
          </button>

          <div>
            <p>Module App Prototype Studio</p>
            <h2>{{ moduleTitle || '模块应用原型' }}</h2>
            <span>
              {{ shellLayoutLabel }} · {{ VIEWPORT.width }} × {{ VIEWPORT.height }} · 工作台缩放 {{ Math.round(effectiveScale * 100) }}%
            </span>
          </div>
        </div>

        <div class="module-app-studio-actions">
          <label class="prototype-zoom-select" aria-label="缩放比例">
            <span>缩放</span>
            <select v-model="zoomMode">
              <option v-for="option in zoomOptions" :key="option.value" :value="option.value">
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

          <button v-if="prototypeEditDraftDirty" type="button" class="studio-ghost-button" @click="emit('discardDraft')">
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

          <button
            type="button"
            class="studio-confirm-button"
            :disabled="saving || prototypeEditDraftDirty || !moduleAppContract"
            @click="emit('confirmSnapshot')"
          >
            确认为开发基线
          </button>

          <button type="button" class="studio-close-button" @click="emit('close')">×</button>
        </div>
      </header>

      <main class="module-app-studio-body">
        <!-- A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR: outer workbench page list, not generated business UI. -->
        <aside
          v-if="studioNavigatorPages.length"
          class="module-app-page-navigator"
          aria-label="模块页面目录"
        >
          <header>
            <p>Pages</p>
            <h3>页面目录</h3>
            <span>点击页面切换中间画布；目录只属于工作台，不写入原型页面。</span>
          </header>

          <div class="module-app-page-navigator-list">
            <article
              v-for="(page, index) in studioNavigatorPages"
              :key="pageNavigatorKey(page, index)"
              class="module-app-page-navigator-item"
              :class="[
                `status-${pageNavigatorStatus(page).toLowerCase()}`,
                { active: isNavigatorPageActive(page) },
              ]"
              role="button"
              tabindex="0"
              @click="handleNavigatorPageClick($event, page)"
              @keydown.enter.prevent="handleNavigatorPageKeyboard($event, page)"
              @keydown.space.prevent="handleNavigatorPageKeyboard($event, page)"
            >
              <div class="module-app-page-thumb-shell">
                <em>{{ String(index + 1).padStart(2, '0') }}</em>

                <div v-if="page.thumbnailContract" class="module-app-page-thumb-real">
                  <div class="module-app-page-thumb-scale">
                    <UiContractPreviewRuntime
                      class="module-app-page-thumb-runtime"
                      :key="`${pageNavigatorKey(page, index)}::thumb`"
                      :contract="page.thumbnailContract"
                      :project-theme-profile="projectThemeProfile || null"
                      display-mode="artboard"
                    />
                  </div>
                </div>

                <div v-else class="module-app-page-thumb-empty">
                  <strong>{{ pageNavigatorStatusLabel(page) }}</strong>
                  <span>{{ pageNavigatorStatus(page) === 'MISSING' ? '等待页面原型' : '正在检查页面' }}</span>
                </div>

                <i>{{ pageNavigatorStatusLabel(page) }}</i>
              </div>

              <div class="module-app-page-thumb-caption">
                <strong>{{ pageNavigatorTitle(page) }}</strong>
                <span>{{ pageNavigatorKindLabel(page) }}</span>
              </div>
            </article>
          </div>
        </aside>

        <section ref="stageRef" class="module-app-studio-stage">
          <div class="module-app-studio-grid-bg" />

          <div class="module-app-artboard-frame" :style="artboardFrameStyle">
            <div class="module-app-artboard-scale" :style="artboardScaleStyle">
              <div class="module-app-artboard-host">
                <div
                  class="module-app-shell"
                  :class="[shellLayoutClass, `theme-${shellTheme}`, { inspecting: inspectMode }]"
                >
                  <header
                    v-if="usesTopNavigation"
                    class="module-app-shell-topbar"
                    :class="{ selected: isSelectedShell('module-app-shell:navigation') }"
                    data-ui-selectable="true"
                    data-ui-component-id="module-app-shell:navigation"
                    @click="handleShellSelect($event, 'module-app-shell:navigation', 'MODULE_NAVIGATION', '顶部导航栏')"
                  >
                    <div
                      class="module-app-topbar-brand"
                      :class="{ selected: isSelectedShell('module-app-shell:title') }"
                      data-ui-selectable="true"
                      data-ui-component-id="module-app-shell:title"
                      @click="handleShellSelect($event, 'module-app-shell:title', 'MODULE_TITLE', moduleTitle || '模块标题')"
                    >
                      <p>Module</p>
                      <strong>{{ moduleTitle || '模块应用' }}</strong>
                    </div>

                    <nav v-if="visibleNavigation.length" class="module-app-topbar-menu" aria-label="模块顶部导航">
                      <button
                        v-for="item in visibleNavigation"
                        :key="item.pageKey || item.route || item.label"
                        type="button"
                        :class="{
                          active: activePage?.pageKey === item.pageKey,
                          selected: isSelectedShell(`module-app-shell:navigation-item:${item.pageKey || item.label || item.route}`),
                        }"
                        data-ui-selectable="true"
                        :data-ui-component-id="`module-app-shell:navigation-item:${item.pageKey || item.label || item.route}`"
                        @click="handleNavClick($event, item)"
                      >
                        {{ item.label || item.pageName || '页面' }}
                      </button>
                    </nav>
                  </header>

                  <aside
                    v-if="usesSidebarNavigation"
                    class="module-app-shell-sidebar"
                    :class="{ selected: isSelectedShell('module-app-shell:navigation') }"
                    data-ui-selectable="true"
                    data-ui-component-id="module-app-shell:navigation"
                    @click="handleShellSelect($event, 'module-app-shell:navigation', 'MODULE_NAVIGATION', '左侧导航栏')"
                  >
                    <header
                      class="module-app-shell-nav-title"
                      :class="{ selected: isSelectedShell('module-app-shell:title') }"
                      data-ui-selectable="true"
                      data-ui-component-id="module-app-shell:title"
                      @click="handleShellSelect($event, 'module-app-shell:title', 'MODULE_TITLE', moduleTitle || '模块标题')"
                    >
                      <p>Module</p>
                      <h3>{{ moduleTitle || '模块应用' }}</h3>
                      <span>导航栏属于模块应用原型，可选中后保存到 MODULE_APP_CONTRACT。</span>
                    </header>

                    <nav v-if="visibleNavigation.length" class="module-app-shell-menu" aria-label="模块左侧导航">
                      <button
                        v-for="item in visibleNavigation"
                        :key="item.pageKey || item.route || item.label"
                        type="button"
                        :class="{
                          active: activePage?.pageKey === item.pageKey,
                          selected: isSelectedShell(`module-app-shell:navigation-item:${item.pageKey || item.label || item.route}`),
                        }"
                        data-ui-selectable="true"
                        :data-ui-component-id="`module-app-shell:navigation-item:${item.pageKey || item.label || item.route}`"
                        @click="handleNavClick($event, item)"
                      >
                        <strong>{{ item.label || item.pageName || '页面' }}</strong>
                        <span>{{ item.route || item.routePath || '' }}</span>
                      </button>
                    </nav>

                    <div v-else class="module-app-empty-note">
                      当前模块没有主导航入口；可通过页面动作进入下级页面。
                    </div>
                  </aside>

                  <main
                    class="module-app-shell-page"
                    :class="{ selected: isSelectedShell('module-app-shell:page-container') }"
                    data-ui-selectable="true"
                    data-ui-component-id="module-app-shell:page-container"
                    @click.self="handleShellSelect($event, 'module-app-shell:page-container', 'MODULE_PAGE_CONTAINER', '页面内容容器')"
                  >
                    <section v-if="usesActionHub" class="module-app-action-hub">
                      <div>
                        <p>Module Flow</p>
                        <h3>{{ moduleTitle || '模块应用' }}</h3>
                        <span>该模块以按钮/动作流转为主，不强制显示导航栏。</span>
                      </div>

                      <div v-if="globalEdgeActions.length" class="module-app-action-hub-actions">
                        <button
                          v-for="edge in globalEdgeActions"
                          :key="edgeKey(edge)"
                          type="button"
                          :class="{ selected: isSelectedShell(edgeComponentId(edge)) }"
                          data-ui-selectable="true"
                          :data-ui-component-id="edgeComponentId(edge)"
                          @click="handleEdgeClick($event, edge)"
                        >
                          {{ edgeLabel(edge) }}
                        </button>
                      </div>
                    </section>

                    <div class="module-app-page-meta">
                      <div>
                        <p>{{ activePage?.surfaceType || 'MAIN_NAV_PAGE' }}</p>
                        <h3>{{ activePage?.pageName || '请选择页面' }}</h3>
                        <span>{{ activePageRoute || '无独立 route，由父页面动作触发' }}</span>
                      </div>

                      <div v-if="activeEdgeActions.length" class="module-app-edge-actions">
                        <button
                          v-for="edge in activeEdgeActions"
                          :key="edgeKey(edge)"
                          type="button"
                          :class="{ selected: isSelectedShell(edgeComponentId(edge)) }"
                          data-ui-selectable="true"
                          :data-ui-component-id="edgeComponentId(edge)"
                          @click="handleEdgeClick($event, edge)"
                        >
                          {{ edgeLabel(edge) }}
                        </button>
                      </div>
                    </div>

                    <div v-if="loading" class="module-app-contract-state">
                      正在加载该页面的真实交互原型...
                    </div>

                    <UiContractPreviewRuntime
                      v-else-if="activeContract"
                      class="module-app-runtime"
                      :key="runtimeKey"
                      :contract="activeContract"
                      :project-theme-profile="projectThemeProfile || null"
                      :focus-component-id="pageFocusedComponentId"
                      :focus-element-selection="pageFocusedElementSelection"
                      :inspect-component-mode="inspectMode"
                      :navigate-request="runtimeNavigateRequest"
                      display-mode="artboard"
                      @select-component="handlePageSelect"
                      @exit-inspect-mode="inspectMode = false"
                      @clear-selection="handleClearSelection"
                    />

                    <div v-else class="module-app-contract-state warning">
                      <strong>该页面还没有可嵌入的 PAGE_UI_CONTRACT</strong>
                      <span>
                        MODULE_APP_CONTRACT 会先保留导航、按钮跳转和页面关系。等该 PAGE 节点生成交互原型后，模块应用工作台会自动加载最新 PAGE_UI_CONTRACT。
                      </span>
                      <em v-if="error">{{ error }}</em>
                    </div>
                  </main>
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside class="module-app-copilot-panel">
          <header>
            <div>
              <p>Prototype Copilot</p>
              <h3>原型编辑助手</h3>
            </div>
            <span :class="{ dirty: prototypeEditDraftDirty }">{{ prototypeEditDraftDirty ? '未保存' : '已同步' }}</span>
          </header>

          <section class="copilot-selection-card" :class="{ empty: !focusedComponentId }">
            <span>当前选中</span>
            <strong>{{ selectionDisplayLabel }}</strong>
            <p>{{ selectionHelpText }}</p>
          </section>

          <section v-if="quickActions.length" class="copilot-quick-actions">
            <span>快捷操作</span>
            <div>
              <button v-for="action in quickActions" :key="action.key" type="button" @click="handleQuickAction(action)">
                {{ action.label }}
              </button>
            </div>
          </section>

          <section class="copilot-input-card">
            <label for="module-app-prototype-copilot-input">编辑指令</label>
            <textarea
              id="module-app-prototype-copilot-input"
              v-model="instructionText"
              rows="4"
              placeholder="例如：把导航栏改到顶部 / 隐藏导航栏 / 把新增按钮改成创建入库单"
              @keydown.enter.exact.prevent="submitInstruction"
            />
            <button type="button" class="studio-primary-button full" :disabled="!instructionText.trim()" @click="submitInstruction">
              发送到当前原型
            </button>
            <p>导航栏、动作按钮和应用壳保存到 MODULE_APP_CONTRACT；页面内容保存到当前 PAGE 的 UI_CONTRACT。</p>
          </section>

          <section v-if="prototypeEditDraftSummary || prototypeEditDraftDirty" class="copilot-result-card">
            <span>最近修改</span>
            <strong>{{ prototypeEditDraftSummary || '原型草稿已更新' }}</strong>
            <p>保存时会按选中目标分流：模块壳 → MODULE_APP_CONTRACT；页面元素 → PAGE_UI_CONTRACT。</p>
          </section>

          <footer class="module-app-copilot-footer">
            <span>{{ prototypeSizeLabel }}</span>
            <span>{{ prototypeComponentCount }} 个组件</span>
            <span>{{ shellLayoutLabel }}</span>
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
  name: 'ModuleAppPrototypeStudioOverlay',
})

type ZoomMode = 'auto' | '50' | '67' | '75' | '80' | '90' | '100' | '110' | '125'
type ShellLayout = 'SIDEBAR' | 'TOP_NAV' | 'ACTION_HUB' | 'NO_NAV'

type QuickAction = {
  key: string
  label: string
  instruction: string
  prompt?: string
  placeholder?: string
}

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
  elementPath?: string | string[] | null
  pageKey?: string | null
  targetPageKey?: string | null
  edgeKey?: string | null
  [key: string]: unknown
}

const VIEWPORT = {
  width: 1440,
  height: 900,
} as const

const props = withDefaults(
  defineProps<{
    visible: boolean
    moduleTitle?: string
    moduleAppContract?: Record<string, any> | null
    navigation?: Record<string, any>[]
    pageNavigatorItems?: Record<string, any>[]
    activePage?: Record<string, any> | null
    activePageRoute?: string
    activeContract?: UiContractContent | null
    projectThemeProfile?: ProjectThemeProfile | null
    focusedComponentId?: string | null
    focusedComponentLabel?: string
    focusedElementSelection?: Record<string, unknown> | null
    prototypeEditDraftDirty?: boolean
    prototypeEditDraftSummary?: string
    saving?: boolean
    loading?: boolean
    error?: string
    prototypeSizeLabel?: string
    prototypeComponentCount?: number
    navigateRequest?: ((payload: Record<string, unknown>) => Promise<Record<string, unknown>> | Record<string, unknown>) | null
  }>(),
  {
    moduleTitle: '',
    moduleAppContract: null,
    navigation: () => [],
    pageNavigatorItems: () => [],
    activePage: null,
    activePageRoute: '',
    activeContract: null,
    projectThemeProfile: null,
    focusedComponentId: null,
    focusedComponentLabel: '',
    focusedElementSelection: null,
    prototypeEditDraftDirty: false,
    prototypeEditDraftSummary: '',
    saving: false,
    loading: false,
    error: '',
    prototypeSizeLabel: '',
    prototypeComponentCount: 0,
    navigateRequest: null,
  },
)

const emit = defineEmits<{
  close: []
  navigatePage: [item: Record<string, any>]
  selectComponent: [payload: PrototypeElementSelection]
  clearSelection: []
  sendInstruction: [instruction: string]
  saveDraft: []
  discardDraft: []
  confirmSnapshot: []
}>()

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

const zoomMode = ref<ZoomMode>('80')
const inspectMode = ref(false)
const instructionText = ref('')
const stageRef = ref<HTMLElement | null>(null)
const stageSize = ref({ width: 0, height: 0 })
let resizeObserver: ResizeObserver | null = null

const appShell = computed<Record<string, any>>(() => recordValue(props.moduleAppContract?.appShell) || {})
const allPages = computed<Record<string, any>[]>(() => arrayValue(props.moduleAppContract?.pages).map((item) => recordValue(item)).filter(Boolean) as Record<string, any>[])
const rawNavigationEdges = computed<Record<string, any>[]>(() => arrayValue(props.moduleAppContract?.navigationEdges).map((item) => recordValue(item)).filter(Boolean) as Record<string, any>[])
const visibleNavigation = computed<Record<string, any>[]>(() => {
  const explicit = (props.navigation || []).filter((item) => item && item.visible !== false)
  if (explicit.length) return explicit
  return allPages.value
    .filter((page) => page.navigationVisible !== false)
    .map((page) => ({
      label: page.pageName || page.title || page.pageKey,
      route: page.routePath || page.route,
      pageKey: page.pageKey,
      visible: true,
      surfaceType: page.surfaceType,
    }))
})

// A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR
// This is the Prototype Studio's own PPT-like page list. It is not part of the generated module app shell.
const studioNavigatorPages = computed<Record<string, any>[]>(() => {
  const source = props.pageNavigatorItems?.length
    ? props.pageNavigatorItems
    : allPages.value

  return source
    .map((item, index) => normalizeStudioNavigatorPage(item, index))
    .filter((page) => Boolean(page.pageKey || page.pageName || page.routePath || page.route))
    .sort((left, right) => Number(left.order || 0) - Number(right.order || 0))
})

const normalizedLayout = computed<ShellLayout>(() => {
  const raw = String(
    appShell.value.layout ||
      appShell.value.layoutType ||
      appShell.value.navigationPlacement ||
      appShell.value.navigationMode ||
      appShell.value.shellLayout ||
      '',
  ).toUpperCase()

  const text = `${raw} ${props.moduleTitle} ${props.moduleAppContract?.moduleName || ''}`

  if (/NO_NAV|NONE|WITHOUT_NAV|ACTION_HUB|BUTTON_FLOW|FLOW_ONLY|无导航|按钮/.test(text)) {
    return visibleNavigation.value.length ? 'ACTION_HUB' : 'NO_NAV'
  }

  if (/TOP|HEADER|HORIZONTAL|TABS|WEBSITE|PORTAL|ECOMMERCE|MARKETPLACE|顶部|横向|电商|商城|门户|官网/.test(text)) {
    return 'TOP_NAV'
  }

  if (!visibleNavigation.value.length) {
    return raw ? 'NO_NAV' : 'ACTION_HUB'
  }

  return 'SIDEBAR'
})

const shellLayoutClass = computed(() => `layout-${normalizedLayout.value.toLowerCase().replace(/_/g, '-')}`)
const usesSidebarNavigation = computed(() => normalizedLayout.value === 'SIDEBAR')
const usesTopNavigation = computed(() => normalizedLayout.value === 'TOP_NAV')
const usesActionHub = computed(() => normalizedLayout.value === 'ACTION_HUB' || normalizedLayout.value === 'NO_NAV')
const shellLayoutLabel = computed(() => {
  if (normalizedLayout.value === 'TOP_NAV') return '顶部导航应用壳'
  if (normalizedLayout.value === 'ACTION_HUB') return '动作流转应用壳'
  if (normalizedLayout.value === 'NO_NAV') return '无导航应用壳'
  return '左侧导航应用壳'
})
const shellTheme = computed(() => {
  const style = recordValue(appShell.value.style)
  const theme = String(style?.navTheme || appShell.value.theme || '').toLowerCase()
  if (['blue', 'light', 'dark', 'green'].includes(theme)) return theme
  return usesTopNavigation.value ? 'light' : 'dark'
})

const activeEdgeActions = computed<Record<string, any>[]>(() => {
  const active = props.activePage
  if (!active) return []
  const activeKeys = [
    active.pageKey,
    active.pageName,
    active.routePath,
    active.route,
  ].map(comparable).filter(Boolean)

  return rawNavigationEdges.value.filter((edge) => {
    const sourceKeys = [edge.sourcePageKey, edge.sourcePageName, edge.sourceRoute, edge.route].map(comparable).filter(Boolean)
    if (!sourceKeys.length) return true
    return sourceKeys.some((key) => activeKeys.includes(key))
  })
})

const globalEdgeActions = computed<Record<string, any>[]>(() => {
  if (activeEdgeActions.value.length) return activeEdgeActions.value
  return rawNavigationEdges.value.slice(0, 8)
})

const fitScale = computed(() => {
  if (!stageSize.value.width || !stageSize.value.height) return 1
  const availableWidth = Math.max(stageSize.value.width - 48, 360)
  const availableHeight = Math.max(stageSize.value.height - 48, 360)
  const scale = Math.min(availableWidth / VIEWPORT.width, availableHeight / VIEWPORT.height)
  return Math.min(Math.max(scale, 0.35), 1)
})

const effectiveScale = computed(() => {
  if (zoomMode.value === 'auto') return fitScale.value
  const value = Number(zoomMode.value)
  return Number.isFinite(value) && value > 0 ? value / 100 : 1
})

const artboardFrameStyle = computed(() => {
  const width = VIEWPORT.width * effectiveScale.value
  const height = VIEWPORT.height * effectiveScale.value
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
  width: `${VIEWPORT.width}px`,
  height: `${VIEWPORT.height}px`,
  minWidth: `${VIEWPORT.width}px`,
  minHeight: `${VIEWPORT.height}px`,
  maxWidth: `${VIEWPORT.width}px`,
  maxHeight: `${VIEWPORT.height}px`,
  transform: `scale(${effectiveScale.value})`,
  '--prototype-device-width': `${VIEWPORT.width}px`,
  '--prototype-device-height': `${VIEWPORT.height}px`,
}))


// A9.10.14.4.3.15.4.5.2_REAL_THUMBNAIL_AND_TYPE_HOTFIX
// Keep the child Runtime prop typed loosely here because UiContractPreviewRuntime
// owns the exact PrototypeNavigatePayload type and this overlay only forwards it.
const runtimeNavigateRequest = computed<((payload: any) => any) | undefined>(() => {
  const handler = props.navigateRequest
  if (!handler) return undefined
  return (payload: any) => handler(recordValue(payload) || {})
})

const runtimeKey = computed(() => {
  const contract = props.activeContract
  return [
    props.activePage?.pageKey,
    props.activePage?.routePath || props.activePage?.route,
    contract?.identity?.pageId,
    contract?.identity?.pageKey,
    contract?.page?.pageId,
    contract?.page?.pageKey,
    contract?.page?.pageName,
    props.prototypeEditDraftDirty ? 'draft' : 'saved',
  ].map((item) => String(item || '').trim()).filter(Boolean).join('::') || 'module-app-prototype-runtime'
})


const selectionRecord = computed<Record<string, unknown>>(() => {
  const value = props.focusedElementSelection
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
})
const selectionKind = computed(() => String(selectionRecord.value.elementKind || selectionRecord.value.kind || selectionRecord.value.componentType || '').toUpperCase())
const isShellFocused = computed(() => String(props.focusedComponentId || '').startsWith('module-app-shell:') || selectionKind.value.startsWith('MODULE_'))
const pageFocusedComponentId = computed(() => isShellFocused.value ? null : props.focusedComponentId)
const pageFocusedElementSelection = computed(() => isShellFocused.value ? null : props.focusedElementSelection)

const selectionDisplayLabel = computed(() => {
  if (props.focusedComponentLabel) return props.focusedComponentLabel
  const record = selectionRecord.value
  return String(
    record.elementLabel || record.fieldKey || record.actionId || record.columnKey || record.tabKey || record.textKey || record.componentTitle || props.focusedComponentId || '尚未选择元素',
  )
})

const selectionHelpText = computed(() => {
  if (!props.focusedComponentId) {
    return '点击“选择页面元素”，可选择导航栏、菜单项、动作按钮、页面字段、表格列或文案。'
  }
  return isShellFocused.value
    ? '当前选中的是模块应用壳，修改会保存到 MODULE_APP_CONTRACT，Development Agent 会用于导航、路由和应用布局。'
    : '当前选中的是页面元素，修改会保存到当前 active PAGE 的 UI_CONTRACT。'
})

const quickActions = computed<QuickAction[]>(() => {
  if (!props.focusedComponentId) return []
  const kind = selectionKind.value
  const label = readableDefault(selectionDisplayLabel.value)

  if (isShellFocused.value) {
    return [
      { key: 'shell-sidebar', label: '左侧导航', instruction: '把模块导航改成左侧导航' },
      { key: 'shell-topbar', label: '顶部导航', instruction: '把模块导航改成顶部导航' },
      { key: 'shell-no-nav', label: '无导航按钮流', instruction: '把模块应用改成无导航，只通过按钮流转页面' },
      { key: 'shell-blue', label: '蓝色导航', instruction: '把导航栏改成蓝色' },
      { key: 'shell-title', label: '改模块标题', instruction: '把模块标题改成', prompt: '请输入新的模块标题', placeholder: label },
    ]
  }

  if (kind.includes('FIELD') || kind.includes('INPUT') || kind.includes('CONTROL')) {
    return [
      { key: 'field-label', label: '改字段名', instruction: '把这个字段改成', prompt: '请输入新的字段名', placeholder: label },
      { key: 'field-placeholder', label: '改提示文案', instruction: '把这个输入框 placeholder 改成', prompt: '请输入新的 placeholder', placeholder: `请输入${label}` },
      { key: 'field-required', label: '设为必填', instruction: '把这个字段设为必填' },
      { key: 'field-optional', label: '设为非必填', instruction: '把这个字段设为非必填' },
    ]
  }

  if (kind.includes('ACTION') || kind.includes('BUTTON') || kind.includes('LINK')) {
    return [
      { key: 'button-text', label: '改按钮文案', instruction: '把这个按钮文案改成', prompt: '请输入新的按钮文案', placeholder: label },
      { key: 'button-primary', label: '蓝色主按钮', instruction: '把这个按钮改成蓝色主按钮' },
      { key: 'button-green', label: '改成绿色', instruction: '把这个按钮改成绿色' },
      { key: 'button-large', label: '放大一点', instruction: '把这个按钮放大一点' },
    ]
  }

  if (kind.includes('COLUMN') || kind.includes('CELL') || kind.includes('TABLE')) {
    return [
      { key: 'column-label', label: '改列名', instruction: '把这个表格列改成', prompt: '请输入新的列名', placeholder: label },
      { key: 'column-wide', label: '加宽一点', instruction: '把这个表格列加宽一点' },
      { key: 'column-narrow', label: '收窄一点', instruction: '把这个表格列收窄一点' },
    ]
  }

  return [
    { key: 'text-change', label: '改文案', instruction: '把这个元素文案改成', prompt: '请输入新的文案', placeholder: label },
    { key: 'text-blue', label: '改成蓝色', instruction: '把这个元素改成蓝色' },
    { key: 'text-large', label: '放大一点', instruction: '把这个元素放大一点' },
  ]
})

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      inspectMode.value = false
      zoomMode.value = '80'
      await nextTick()
      ensureResizeObserver()
      return
    }
    disconnectResizeObserver()
    inspectMode.value = false
    instructionText.value = ''
  },
)

onBeforeUnmount(() => disconnectResizeObserver())

function ensureResizeObserver() {
  disconnectResizeObserver()
  if (!stageRef.value) return
  resizeObserver = new ResizeObserver(() => refreshStageSize())
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
  if (!stageRef.value) return
  const rect = stageRef.value.getBoundingClientRect()
  stageSize.value = { width: rect.width, height: rect.height }
}

function normalizeStudioNavigatorPage(item: Record<string, any>, index: number): Record<string, any> {
  const page = recordValue(item) || {}
  const pageKey = String(page.pageKey || page.page_key || page.key || '').trim()
  const pageName = String(page.pageName || page.page_name || page.title || page.label || pageKey || `页面 ${index + 1}`).trim()
  const routePath = String(page.routePath || page.route_path || page.route || '').trim()
  const status = pageNavigatorStatus(page)

  const thumbnailContract = normalizeNavigatorContract(
    page.thumbnailContract ||
      page.uiContract ||
      page.ui_contract ||
      page.contract ||
      page.activeContract ||
      page.content,
  )

  return {
    ...page,
    pageKey,
    pageName,
    routePath,
    route: routePath || page.route || '',
    order: Number(page.order || page.navigatorOrder || index + 1),
    navigatorStatus: status,
    thumbnailContract,
  }
}

function pageNavigatorKey(page: Record<string, any>, index: number): string {
  return String(page.pageKey || page.routePath || page.route || page.pageName || index)
}

function pageNavigatorTitle(page: Record<string, any>): string {
  return String(page.pageName || page.label || page.title || page.pageKey || '未命名页面')
}

function pageNavigatorRoute(page: Record<string, any>): string {
  return String(page.routePath || page.route || page.surfaceType || '无独立路由')
}


function normalizeNavigatorContract(value: unknown): UiContractContent | null {
  if (!value) return null

  if (typeof value === 'string') {
    try {
      return normalizeNavigatorContract(JSON.parse(value))
    } catch {
      return null
    }
  }

  const record = recordValue(value)
  if (!record) return null

  const content = recordValue(record.content)
  if (content && (content.page || content.componentTree || content.interactions || content.mockData)) {
    return content as unknown as UiContractContent
  }

  if (record.page || record.componentTree || record.interactions || record.mockData) {
    return record as unknown as UiContractContent
  }

  const nested = recordValue(record.uiContract || record.ui_contract || record.contract)
  if (nested) {
    return normalizeNavigatorContract(nested)
  }

  return null
}

function pageNavigatorStatus(page: Record<string, any>): string {
  const raw = String(page.navigatorStatus || page.status || '').trim().toUpperCase()

  if (raw === 'DRAFT') return 'DRAFT'
  if (raw === 'READY' || raw === 'GENERATED' || raw === 'HAS_UI_CONTRACT') return 'READY'
  if (raw === 'LOADING' || raw === 'CHECKING') return 'LOADING'
  if (raw === 'MISSING' || raw === 'NO_UI_CONTRACT' || raw === 'NOT_GENERATED') return 'MISSING'
  if (page.hasUiContract === true) return 'READY'
  if (page.hasUiContract === false) return 'MISSING'

  return 'UNKNOWN'
}

function pageNavigatorStatusLabel(page: Record<string, any>): string {
  const status = pageNavigatorStatus(page)
  if (status === 'DRAFT') return '草稿'
  if (status === 'READY') return '已生成'
  if (status === 'LOADING') return '检查中'
  if (status === 'MISSING') return '未生成'
  return '待检查'
}

function pageNavigatorKind(page: Record<string, any>): string {
  const text = [
    page.pageKind,
    page.pageType,
    page.layoutType,
    page.surfaceType,
    page.pageName,
    page.title,
    page.routePath,
    page.route,
  ].map((item) => String(item || '')).join(' ').toUpperCase()

  if (/APPROVAL|AUDIT|REVIEW|审批|审核|复核/.test(text)) return 'APPROVAL'
  if (/DETAIL|PROFILE|详情|档案/.test(text)) return 'DETAIL'
  if (/CREATE|NEW|ADD|EDIT|FORM|WIZARD|创建|新建|新增|编辑|修改|表单/.test(text)) return 'FORM'
  if (/DASHBOARD|REPORT|ANALYTICS|看板|统计|报表|分析/.test(text)) return 'DASHBOARD'
  if (/CONFIG|SETTING|设置|配置/.test(text)) return 'CONFIG'
  if (/PORTAL|HOME|门户|首页/.test(text)) return 'PORTAL'
  return 'LIST'
}

function pageNavigatorKindLabel(page: Record<string, any>): string {
  const kind = pageNavigatorKind(page)
  if (kind === 'APPROVAL') return '审核页'
  if (kind === 'DETAIL') return '详情页'
  if (kind === 'FORM') return '表单页'
  if (kind === 'DASHBOARD') return '看板页'
  if (kind === 'CONFIG') return '配置页'
  if (kind === 'PORTAL') return '门户页'
  return '列表页'
}

function pageNavigatorThumbnailBlocks(page: Record<string, any>): string[] {
  const kind = pageNavigatorKind(page)
  if (kind === 'FORM') {
    return ['header', 'field-a', 'field-b', 'field-c', 'field-d', 'actions']
  }
  if (kind === 'DETAIL') {
    return ['header', 'summary', 'detail-row-a', 'detail-row-b', 'detail-row-c', 'table']
  }
  if (kind === 'APPROVAL') {
    return ['header', 'summary', 'steps', 'field-a', 'field-b', 'actions']
  }
  if (kind === 'DASHBOARD') {
    return ['metric-a', 'metric-b', 'metric-c', 'chart', 'table']
  }
  if (kind === 'CONFIG') {
    return ['header', 'field-a', 'field-b', 'field-c', 'actions']
  }
  if (kind === 'PORTAL') {
    return ['hero', 'card-a', 'card-b', 'card-c', 'actions']
  }
  return ['filters', 'table-head', 'table-row-a', 'table-row-b', 'table-row-c', 'actions']
}

function isNavigatorPageActive(page: Record<string, any>): boolean {
  const active = props.activePage || {}
  const pageKeys = [page.pageKey, page.pageName, page.routePath, page.route].map(comparable).filter(Boolean)
  const activeKeys = [active.pageKey, active.pageName, active.routePath, active.route].map(comparable).filter(Boolean)
  return Boolean(pageKeys.length && activeKeys.some((key) => pageKeys.includes(key)))
}

function handleNavigatorPageClick(event: MouseEvent, page: Record<string, any>) {
  event.stopPropagation()
  emit('navigatePage', page)
}

function handleNavigatorPageKeyboard(event: KeyboardEvent, page: Record<string, any>) {
  event.stopPropagation()
  emit('navigatePage', page)
}

function handleNavClick(event: MouseEvent, item: Record<string, any>) {
  event.stopPropagation()
  if (inspectMode.value) {
    const componentId = `module-app-shell:navigation-item:${item.pageKey || item.label || item.route}`
    emit('selectComponent', {
      componentId,
      componentType: 'MODULE_NAVIGATION_ITEM',
      componentTitle: String(item.label || item.pageName || '导航项'),
      elementKind: 'MODULE_NAVIGATION_ITEM',
      elementLabel: String(item.label || item.pageName || '导航项'),
      pageKey: String(item.pageKey || ''),
    })
    inspectMode.value = false
    return
  }
  emit('navigatePage', item)
}

async function handleEdgeClick(event: MouseEvent, edge: Record<string, any>) {
  event.stopPropagation()
  if (inspectMode.value) {
    emit('selectComponent', {
      componentId: edgeComponentId(edge),
      componentType: 'MODULE_ACTION_EDGE',
      componentTitle: edgeLabel(edge),
      elementKind: 'MODULE_ACTION_EDGE',
      elementLabel: edgeLabel(edge),
      actionId: String(edge.actionId || edge.label || ''),
      edgeKey: edgeKey(edge),
      targetPageKey: String(edge.targetPageKey || ''),
      pageKey: String(edge.sourcePageKey || props.activePage?.pageKey || ''),
    })
    inspectMode.value = false
    return
  }

  const target = findTargetPage(edge)
  if (target) {
    emit('navigatePage', target)
    return
  }

  if (props.navigateRequest) {
    await props.navigateRequest({
      actionId: edge.actionId || edge.label,
      targetPageKey: edge.targetPageKey,
      targetPageName: edge.targetPageName,
      route: edge.targetRoute,
    })
  }
}

function handleShellSelect(event: MouseEvent, componentId: string, kind: string, label: string) {
  if (!inspectMode.value) return
  event.stopPropagation()
  emit('selectComponent', {
    componentId,
    componentType: kind,
    componentTitle: label,
    elementKind: kind,
    elementLabel: label,
  })
  inspectMode.value = false
}

function handlePageSelect(payload: any) {
  const componentId = String(payload?.componentId || '').trim()
  if (!componentId) return
  emit('selectComponent', {
    componentId,
    componentType: stringOrNull(payload.componentType),
    componentTitle: stringOrNull(payload.componentTitle),
    elementKind: stringOrNull(payload.elementKind || payload.componentType || 'COMPONENT'),
    elementLabel: stringOrNull(payload.elementLabel || payload.componentTitle || payload.componentId),
    fieldKey: stringOrNull(payload.fieldKey),
    actionId: stringOrNull(payload.actionId),
    columnKey: stringOrNull(payload.columnKey),
    tabKey: stringOrNull(payload.tabKey),
    textKey: stringOrNull(payload.textKey),
    textRole: stringOrNull(payload.textRole),
    elementPath: Array.isArray(payload.elementPath) ? (payload.elementPath as unknown[]).map((item: unknown) => String(item)).filter(Boolean) : stringOrNull(payload.elementPath),
  })
  inspectMode.value = false
}

function handleClearSelection() {
  inspectMode.value = false
  emit('clearSelection')
}

function submitInstruction() {
  const text = instructionText.value.trim()
  if (!text) return
  emit('sendInstruction', text)
  instructionText.value = ''
  clearSelectionAfterInstruction()
}

function handleQuickAction(action: QuickAction) {
  let instruction = action.instruction
  if (action.prompt) {
    const value = window.prompt(action.prompt, action.placeholder || '')
    if (!value || !value.trim()) return
    instruction = `${instruction}“${value.trim()}”`
  }
  emit('sendInstruction', instruction)
  clearSelectionAfterInstruction()
}

function clearSelectionAfterInstruction() {
  inspectMode.value = false
  window.setTimeout(() => emit('clearSelection'), 0)
}

function isSelectedShell(componentId: string): boolean {
  return String(props.focusedComponentId || '') === componentId
}

function findTargetPage(edge: Record<string, any>): Record<string, any> | null {
  const targetKeys = [edge.targetPageKey, edge.targetPageName, edge.targetRoute, edge.route].map(comparable).filter(Boolean)
  if (!targetKeys.length) return null
  return allPages.value.find((page) => {
    const pageKeys = [page.pageKey, page.pageName, page.routePath, page.route].map(comparable).filter(Boolean)
    return targetKeys.some((key) => pageKeys.includes(key))
  }) || null
}

function edgeLabel(edge: Record<string, any>): string {
  return String(edge.label || edge.actionLabel || edge.actionId || edge.targetPageName || edge.targetPageKey || '进入目标页面')
}

function edgeKey(edge: Record<string, any>): string {
  return [edge.sourcePageKey || props.activePage?.pageKey || 'page', edge.actionId || edge.label || 'action', edge.targetPageKey || edge.targetRoute || 'target'].map((item) => String(item || '').trim()).join('::')
}

function edgeComponentId(edge: Record<string, any>): string {
  return `module-app-shell:action-edge:${edgeKey(edge)}`
}

function recordValue(value: unknown): Record<string, any> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : null
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function comparable(value: unknown): string {
  return String(value || '').trim().toLowerCase().replace(/^\/+|\/+$/g, '').replace(/[\s_\-]+/g, '')
}

function stringOrNull(value: unknown): string | null {
  const text = String(value ?? '').trim()
  return text ? text : null
}

function readableDefault(value: unknown): string {
  const text = String(value || '').replace(/^[A-Z_]+\s*[·:-]\s*/i, '').replace(/^已选中\s*[·:-]\s*/i, '').trim()
  return text || '当前元素'
}
// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: navigator helpers are retained for thumbnail/list fallback variants.
void pageNavigatorRoute
void pageNavigatorThumbnailBlocks

</script>

<style scoped>
.module-app-studio-mask {
  position: fixed;
  inset: 0;
  z-index: 2700;
  display: grid;
  grid-template-rows: 68px minmax(0, 1fr);
  background: radial-gradient(circle at 14% 4%, rgba(59, 130, 246, 0.18), transparent 24%), linear-gradient(135deg, #08111f 0%, #0f172a 48%, #020617 100%);
  color: #ffffff;
}

.module-app-studio-topbar {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  padding: 9px 14px;
  background: rgba(8, 17, 33, 0.92);
  box-shadow: 0 12px 30px rgba(2, 6, 23, 0.28);
  backdrop-filter: blur(16px);
}

.module-app-studio-title {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: center;
  gap: 12px;
}

.module-app-studio-title p {
  margin: 0 0 1px;
  color: #7dd3fc;
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.module-app-studio-title h2 {
  margin: 0;
  max-width: min(520px, 34vw);
  overflow: hidden;
  color: #ffffff;
  font-size: 18px;
  font-weight: 950;
  line-height: 1.12;
  letter-spacing: -0.03em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-studio-title span,
.copilot-selection-card p,
.copilot-input-card p,
.copilot-result-card p,
.module-app-copilot-footer span {
  color: rgba(203, 213, 225, 0.78);
  font-size: 12px;
  line-height: 1.5;
}

.module-app-studio-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  max-width: min(760px, 58vw);
  overflow: hidden;
}

.studio-back-button,
.studio-ghost-button,
.studio-primary-button,
.studio-close-button {
  height: 32px;
  min-height: 32px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 999px;
  padding: 0 12px;
  background: rgba(15, 23, 42, 0.66);
  color: #e2e8f0;
  font-size: 12px;
  font-weight: 900;
  line-height: 30px;
  white-space: nowrap;
  cursor: pointer;
}

.studio-back-button:hover,
.studio-ghost-button:hover,
.studio-ghost-button.active {
  border-color: rgba(147, 197, 253, 0.8);
  background: rgba(37, 99, 235, 0.22);
  color: #ffffff;
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
  width: 32px;
  padding: 0;
  color: #ffffff;
  font-size: 22px;
}

.prototype-zoom-select {
  display: inline-flex;
  flex: 0 0 auto;
  height: 32px;
  align-items: center;
  gap: 7px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  padding: 0 10px 0 12px;
  background: rgba(15, 23, 42, 0.82);
}

.prototype-zoom-select span {
  color: rgba(203, 213, 225, 0.72);
  font-size: 12px;
  font-weight: 800;
}

.prototype-zoom-select select {
  width: 76px;
  height: 26px;
  border: 1px solid rgba(203, 213, 225, 0.95);
  border-radius: 999px;
  outline: none;
  padding: 0 8px;
  background: #ffffff;
  color: #0f172a;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.module-app-studio-body {
  display: grid;
  grid-template-columns: 226px minmax(0, 1fr) 324px;
  min-height: 0;
  overflow: hidden;
}

/* A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR */
.module-app-page-navigator {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-right: 1px solid rgba(148, 163, 184, 0.22);
  padding: 14px 12px;
  background: rgba(8, 17, 33, 0.96);
  box-shadow: 20px 0 44px rgba(2, 6, 23, 0.22);
}

.module-app-page-navigator header {
  display: grid;
  gap: 4px;
  padding: 2px 2px 8px;
}

.module-app-page-navigator header p {
  margin: 0;
  color: #7dd3fc;
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.module-app-page-navigator header h3 {
  margin: 0;
  color: #ffffff;
  font-size: 16px;
  font-weight: 950;
}

.module-app-page-navigator header span {
  color: rgba(203, 213, 225, 0.74);
  font-size: 12px;
  line-height: 1.45;
}

.module-app-page-navigator-list {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  overflow: auto;
  padding: 2px 2px 12px;
  scrollbar-gutter: stable;
}

.module-app-page-navigator-list::-webkit-scrollbar {
  width: 8px;
}

.module-app-page-navigator-list::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.82);
}

.module-app-page-navigator-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.9);
}

.module-app-page-navigator-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 9px;
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  padding: 10px 9px;
  background: rgba(15, 23, 42, 0.68);
  color: #e5e7eb;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.module-app-page-navigator-item:hover,
.module-app-page-navigator-item.active {
  border-color: rgba(96, 165, 250, 0.74);
  background: rgba(37, 99, 235, 0.28);
  transform: translateY(-1px);
}

.module-app-page-navigator-item em {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 10px;
  background: rgba(30, 41, 59, 0.88);
  color: #93c5fd;
  font-size: 11px;
  font-style: normal;
  font-weight: 950;
}

.module-app-page-navigator-item.active em {
  background: #2563eb;
  color: #ffffff;
}

.module-app-page-navigator-text {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.module-app-page-navigator-text strong,
.module-app-page-navigator-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-page-navigator-text strong {
  color: inherit;
  font-size: 12px;
  font-weight: 950;
}

.module-app-page-navigator-text small {
  color: rgba(203, 213, 225, 0.62);
  font-size: 10px;
}

.module-app-page-navigator-item i {
  grid-column: 2;
  justify-self: start;
  border-radius: 999px;
  padding: 3px 7px;
  background: rgba(37, 99, 235, 0.16);
  color: #bfdbfe;
  font-size: 10px;
  font-style: normal;
  font-weight: 900;
}

.module-app-page-navigator-item.status-draft i {
  background: rgba(245, 158, 11, 0.18);
  color: #fde68a;
}

.module-app-page-navigator-item.status-missing {
  opacity: 0.68;
}

.module-app-page-navigator-item.status-missing i {
  background: rgba(71, 85, 105, 0.42);
  color: #cbd5e1;
}

.module-app-page-navigator-item.status-loading i,
.module-app-page-navigator-item.status-unknown i {
  background: rgba(148, 163, 184, 0.14);
  color: #cbd5e1;
}

.module-app-studio-stage {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  align-items: center;
  justify-content: center;
  overflow: auto;
  padding: 20px 24px;
  background: radial-gradient(circle at 50% 34%, rgba(96, 165, 250, 0.08), transparent 36%), linear-gradient(180deg, rgba(15, 23, 42, 0.12), rgba(2, 6, 23, 0.1));
  overscroll-behavior: contain;
}

.module-app-studio-grid-bg {
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image: linear-gradient(rgba(148, 163, 184, 0.16) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, 0.16) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

.module-app-artboard-frame {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.48);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.32), 0 8px 24px rgba(15, 23, 42, 0.22), 0 0 0 1px rgba(255, 255, 255, 0.08);
}

.module-app-artboard-scale {
  overflow: hidden;
  background: #f5f7fb;
  transform-origin: top left;
}

.module-app-artboard-host {
  width: 1440px;
  height: 900px;
  overflow: hidden;
  background: #f5f7fb;
}

.module-app-shell {
  width: 1440px;
  height: 900px;
  overflow: hidden;
  background: #f5f7fb;
}

.module-app-shell.layout-sidebar {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
}

.module-app-shell.layout-top-nav {
  display: grid;
  grid-template-rows: 72px minmax(0, 1fr);
}

.module-app-shell.layout-action-hub,
.module-app-shell.layout-no-nav {
  display: block;
}

.module-app-shell-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  padding: 22px 16px;
  background: #111827;
  color: #ffffff;
}

.module-app-shell.theme-blue .module-app-shell-sidebar,
.module-app-shell.theme-blue .module-app-shell-topbar {
  background: linear-gradient(180deg, #1d4ed8 0%, #1e3a8a 100%);
}

.module-app-shell.theme-light .module-app-shell-sidebar,
.module-app-shell.theme-light .module-app-shell-topbar {
  border-color: #dbeafe;
  background: #ffffff;
  color: #0f172a;
}

.module-app-shell-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  border-bottom: 1px solid #dbeafe;
  padding: 0 28px;
  background: #ffffff;
  color: #0f172a;
}

.module-app-topbar-brand {
  display: grid;
  min-width: 180px;
  gap: 2px;
}

.module-app-topbar-brand p,
.module-app-shell-nav-title p,
.module-app-page-meta p,
.module-app-action-hub p {
  margin: 0;
  color: #60a5fa;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.11em;
  text-transform: uppercase;
}

.module-app-topbar-brand strong {
  font-size: 20px;
  font-weight: 950;
}

.module-app-topbar-menu {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.module-app-topbar-menu button,
.module-app-shell-menu button,
.module-app-edge-actions button,
.module-app-action-hub-actions button {
  border: 1px solid transparent;
  cursor: pointer;
}

.module-app-topbar-menu button {
  border-radius: 999px;
  padding: 9px 14px;
  background: transparent;
  color: inherit;
  font-size: 13px;
  font-weight: 900;
}

.module-app-topbar-menu button:hover,
.module-app-topbar-menu button.active {
  background: #2563eb;
  color: #ffffff;
}

.module-app-shell-nav-title {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
}

.module-app-shell-nav-title h3 {
  margin: 0;
  color: inherit;
  font-size: 20px;
  font-weight: 950;
}

.module-app-shell-nav-title span {
  color: #bfdbfe;
  font-size: 12px;
  line-height: 1.5;
}

.module-app-shell.theme-light .module-app-shell-nav-title span {
  color: #64748b;
}

.module-app-shell-menu {
  display: grid;
  gap: 8px;
}

.module-app-shell-menu button {
  display: grid;
  gap: 3px;
  width: 100%;
  border-radius: 14px;
  padding: 11px 12px;
  background: transparent;
  color: #dbeafe;
  text-align: left;
}

.module-app-shell.theme-light .module-app-shell-menu button {
  color: #334155;
}

.module-app-shell-menu button strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-shell-menu button span {
  overflow: hidden;
  color: #93a4bd;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-shell-menu button:hover,
.module-app-shell-menu button.active {
  border-color: rgba(147, 197, 253, 0.5);
  background: #2563eb;
  color: #ffffff;
}

.module-app-shell-page {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 26px 34px;
  background: #f5f7fb;
  scrollbar-gutter: stable;
}

.layout-top-nav .module-app-shell-page,
.layout-action-hub .module-app-shell-page,
.layout-no-nav .module-app-shell-page {
  height: 100%;
}

.layout-top-nav .module-app-shell-page {
  height: 828px;
}

.module-app-page-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.module-app-page-meta h3,
.module-app-action-hub h3 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 25px;
  font-weight: 950;
  letter-spacing: -0.03em;
}

.module-app-page-meta span,
.module-app-action-hub span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
}

.module-app-edge-actions,
.module-app-action-hub-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.module-app-edge-actions button,
.module-app-action-hub-actions button {
  border-color: #bfdbfe;
  border-radius: 12px;
  padding: 9px 13px;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.08);
}

.module-app-edge-actions button:hover,
.module-app-action-hub-actions button:hover {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.module-app-action-hub {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #ffffff 0%, #eff6ff 100%);
}

.module-app-runtime {
  width: 100%;
}

.module-app-shell-page :deep(.runtime-shell--artboard),
.module-app-shell-page :deep(.artboard-runtime-main),
.module-app-shell-page :deep(.pattern-runtime),
.module-app-shell-page :deep(.ui-runtime-shell),
.module-app-shell-page :deep(.runtime-screen),
.module-app-shell-page :deep(.a9-runtime-design-canvas) {
  width: 100% !important;
  min-width: 100% !important;
  max-width: none !important;
  height: auto !important;
  min-height: 720px !important;
  max-height: none !important;
  overflow: visible !important;
  box-sizing: border-box !important;
}

.module-app-contract-state {
  display: grid;
  gap: 8px;
  margin: 28px 0;
  border: 1px dashed #bfdbfe;
  border-radius: 16px;
  padding: 18px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 13px;
}

.module-app-contract-state.warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.module-app-contract-state strong,
.module-app-contract-state span,
.module-app-contract-state em {
  color: #92400e;
  font-size: 12px;
  font-style: normal;
  line-height: 1.7;
}

.module-app-contract-state strong {
  font-size: 14px;
}

.module-app-empty-note {
  border: 1px dashed rgba(147, 197, 253, 0.3);
  border-radius: 14px;
  padding: 12px;
  color: #93a4bd;
  font-size: 12px;
  line-height: 1.6;
}

.module-app-shell.inspecting .module-app-shell-nav.selected,
.module-app-shell.inspecting .module-app-shell-sidebar.selected,
.module-app-shell.inspecting .module-app-shell-topbar.selected,
.module-app-shell.inspecting .module-app-shell-page.selected,
.module-app-shell.inspecting .module-app-shell-nav-title.selected,
.module-app-shell.inspecting .module-app-topbar-brand.selected,
.module-app-shell.inspecting button.selected {
  outline: 3px solid rgba(14, 165, 233, 0.82);
  outline-offset: 2px;
  box-shadow: 0 0 0 6px rgba(14, 165, 233, 0.16);
}

.module-app-copilot-panel {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, auto) auto 1fr auto;
  gap: 11px;
  min-height: 0;
  overflow: auto;
  border-left: 1px solid rgba(148, 163, 184, 0.22);
  padding: 14px;
  background: rgba(8, 17, 33, 0.94);
  box-shadow: -24px 0 60px rgba(0, 0, 0, 0.22);
}

.module-app-copilot-panel > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 10px;
}

.module-app-copilot-panel > header p,
.copilot-selection-card span,
.copilot-quick-actions > span,
.copilot-input-card label,
.copilot-result-card span {
  margin: 0;
  color: #7dd3fc;
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.module-app-copilot-panel > header h3 {
  margin: 3px 0 0;
  color: #ffffff;
  font-size: 16px;
}

.module-app-copilot-panel > header > span {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(22, 163, 74, 0.15);
  color: #86efac;
  font-size: 11px;
  font-weight: 950;
}

.module-app-copilot-panel > header > span.dirty {
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
  background: rgba(15, 23, 42, 0.58);
}

.copilot-selection-card.empty {
  border-style: dashed;
  background: rgba(15, 23, 42, 0.46);
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
  min-height: 88px;
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

.module-app-copilot-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-self: end;
}

.module-app-copilot-footer span {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(148, 163, 184, 0.12);
}

/* A9.10.14.4.3.15.4.5.1_PROTOTYPE_NAVIGATOR_THUMBNAIL_HOTFIX */

.module-app-studio-body {
  grid-template-columns: 232px minmax(0, 1fr) 324px !important;
}

.module-app-page-navigator {
  gap: 10px !important;
  padding: 12px 10px !important;
}

.module-app-page-navigator header {
  padding: 2px 4px 6px !important;
}

.module-app-page-navigator header h3 {
  font-size: 15px !important;
}

.module-app-page-navigator header span {
  display: none !important;
}

.module-app-page-navigator-list {
  gap: 10px !important;
  padding: 2px 3px 16px !important;
}

.module-app-page-navigator-item {
  position: relative !important;
  display: grid !important;
  grid-template-columns: 1fr !important;
  gap: 6px !important;
  border-radius: 18px !important;
  padding: 8px !important;
  background: rgba(15, 23, 42, 0.58) !important;
}

.module-app-page-navigator-item:hover,
.module-app-page-navigator-item.active {
  border-color: rgba(96, 165, 250, 0.82) !important;
  background: rgba(37, 99, 235, 0.22) !important;
  transform: translateY(-1px) !important;
}

.module-app-page-thumb {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 13px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef4ff 100%);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.75),
    0 12px 26px rgba(2, 6, 23, 0.2);
}

.module-app-page-navigator-item.active .module-app-page-thumb {
  border-color: rgba(191, 219, 254, 0.92);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.82),
    0 0 0 3px rgba(37, 99, 235, 0.22),
    0 14px 30px rgba(2, 6, 23, 0.24);
}

.module-app-page-thumb-index {
  position: absolute;
  top: 6px;
  left: 7px;
  z-index: 2;
  display: grid;
  min-width: 20px;
  height: 18px;
  place-items: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.1);
  color: rgba(15, 23, 42, 0.46);
  font-size: 9px;
  font-weight: 950;
  letter-spacing: -0.02em;
}

.module-app-page-navigator-item.active .module-app-page-thumb-index {
  background: rgba(37, 99, 235, 0.14);
  color: rgba(29, 78, 216, 0.75);
}

.module-app-page-thumb-chrome {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 17px;
  padding: 0 7px 0 34px;
  background: rgba(226, 232, 240, 0.72);
}

.module-app-page-thumb-chrome span {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.38);
}

.module-app-page-thumb-canvas {
  position: relative;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-auto-rows: 7px;
  gap: 5px;
  padding: 9px 8px 24px;
}

.module-app-page-thumb-canvas > span {
  display: block;
  min-width: 0;
  border-radius: 4px;
  background: rgba(37, 99, 235, 0.16);
}

.module-app-page-thumb .thumb-block-header,
.module-app-page-thumb .thumb-block-filters,
.module-app-page-thumb .thumb-block-hero {
  grid-column: 1 / -1;
  height: 13px;
  background: rgba(37, 99, 235, 0.26);
}

.module-app-page-thumb .thumb-block-summary,
.module-app-page-thumb .thumb-block-chart {
  grid-column: span 4;
  height: 24px;
  background: rgba(59, 130, 246, 0.12);
}

.module-app-page-thumb .thumb-block-field-a,
.module-app-page-thumb .thumb-block-field-b,
.module-app-page-thumb .thumb-block-field-c,
.module-app-page-thumb .thumb-block-field-d {
  grid-column: span 3;
  height: 12px;
  background: rgba(15, 23, 42, 0.1);
}

.module-app-page-thumb .thumb-block-actions {
  grid-column: 5 / -1;
  height: 10px;
  background: rgba(37, 99, 235, 0.34);
}

.module-app-page-thumb .thumb-block-table,
.module-app-page-thumb .thumb-block-table-head,
.module-app-page-thumb .thumb-block-table-row-a,
.module-app-page-thumb .thumb-block-table-row-b,
.module-app-page-thumb .thumb-block-table-row-c {
  grid-column: 1 / -1;
  height: 8px;
  background: rgba(15, 23, 42, 0.1);
}

.module-app-page-thumb .thumb-block-table-head {
  background: rgba(37, 99, 235, 0.2);
}

.module-app-page-thumb .thumb-block-detail-row-a,
.module-app-page-thumb .thumb-block-detail-row-b,
.module-app-page-thumb .thumb-block-detail-row-c {
  grid-column: 1 / -1;
  height: 9px;
  background: rgba(15, 23, 42, 0.09);
}

.module-app-page-thumb .thumb-block-steps {
  grid-column: 1 / 3;
  grid-row: span 3;
  height: 27px;
  background: rgba(245, 158, 11, 0.2);
}

.module-app-page-thumb .thumb-block-metric-a,
.module-app-page-thumb .thumb-block-metric-b,
.module-app-page-thumb .thumb-block-metric-c,
.module-app-page-thumb .thumb-block-card-a,
.module-app-page-thumb .thumb-block-card-b,
.module-app-page-thumb .thumb-block-card-c {
  grid-column: span 2;
  height: 20px;
  background: rgba(59, 130, 246, 0.16);
}

.module-app-page-thumb.kind-form .thumb-block-header,
.module-app-page-thumb.kind-approval .thumb-block-header {
  background: rgba(37, 99, 235, 0.22);
}

.module-app-page-thumb.kind-approval .thumb-block-actions {
  background: rgba(22, 163, 74, 0.28);
}

.module-app-page-thumb.kind-dashboard .thumb-block-chart {
  grid-column: 1 / -1;
  height: 30px;
  background: rgba(14, 165, 233, 0.16);
}

.module-app-page-thumb.status-missing {
  background: linear-gradient(180deg, #1f2937 0%, #111827 100%);
  opacity: 0.78;
}

.module-app-page-thumb-empty {
  position: absolute;
  inset: 17px 0 0;
  display: grid;
  place-content: center;
  gap: 2px;
  color: rgba(203, 213, 225, 0.72);
  text-align: center;
}

.module-app-page-thumb-empty strong {
  color: rgba(226, 232, 240, 0.84);
  font-size: 11px;
}

.module-app-page-thumb-empty span {
  color: rgba(148, 163, 184, 0.72);
  font-size: 9px;
}

.module-app-page-thumb-status {
  position: absolute;
  right: 6px;
  bottom: 6px;
  border-radius: 999px;
  padding: 2px 6px;
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
  font-size: 9px;
  font-weight: 950;
}

.module-app-page-thumb.status-missing .module-app-page-thumb-status {
  background: rgba(148, 163, 184, 0.16);
  color: rgba(226, 232, 240, 0.82);
}

.module-app-page-thumb-title {
  display: block;
  overflow: hidden;
  color: #e5e7eb;
  font-size: 11px;
  font-weight: 950;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-page-thumb-meta {
  display: inline-flex;
  width: max-content;
  max-width: 100%;
  overflow: hidden;
  border-radius: 999px;
  padding: 2px 7px;
  background: rgba(148, 163, 184, 0.12);
  color: rgba(203, 213, 225, 0.66);
  font-size: 9px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .module-app-studio-body {
    grid-template-columns: 210px minmax(0, 1fr) 300px !important;
  }
}

@media (max-width: 1280px) {
  .module-app-studio-body {
    grid-template-columns: 198px minmax(0, 1fr) 300px;
  }

  .module-app-copilot-panel {
    width: 300px;
  }
}

@media (max-width: 1180px) {
  .module-app-studio-body {
    grid-template-columns: 1fr;
  }

  .module-app-page-navigator {
    display: none;
  }

  .module-app-copilot-panel {
    position: absolute;
    right: 16px;
    bottom: 16px;
    z-index: 3;
    width: min(340px, calc(100vw - 32px));
    max-height: min(620px, calc(100vh - 96px));
    border: 1px solid rgba(148, 163, 184, 0.3);
    border-radius: 22px;
  }
}


/* A9.10.14.4.3.15.4.5.4_DIRECT_FULL_FILE_REAL_THUMBNAIL_FIX */
.module-app-page-navigator-item {
  display: grid !important;
  grid-template-columns: 1fr !important;
  gap: 7px !important;
  width: 100% !important;
  padding: 8px !important;
  border-radius: 18px !important;
  background: rgba(15, 23, 42, 0.58) !important;
}

.module-app-page-thumb-shell {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 14px;
  background: #f8fafc;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.82),
    0 12px 26px rgba(2, 6, 23, 0.2);
}

.module-app-page-navigator-item.active .module-app-page-thumb-shell {
  border-color: rgba(191, 219, 254, 0.96);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.9),
    0 0 0 3px rgba(37, 99, 235, 0.24),
    0 16px 34px rgba(2, 6, 23, 0.28);
}

.module-app-page-thumb-shell > em {
  position: absolute;
  top: 6px;
  left: 7px;
  z-index: 5;
  display: grid;
  min-width: 20px;
  height: 18px;
  place-items: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: rgba(15, 23, 42, 0.48);
  font-size: 9px;
  font-style: normal;
  font-weight: 950;
  letter-spacing: -0.02em;
}

.module-app-page-navigator-item.active .module-app-page-thumb-shell > em {
  background: rgba(37, 99, 235, 0.14);
  color: rgba(29, 78, 216, 0.78);
}

.module-app-page-thumb-real {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: #ffffff;
  pointer-events: none;
}

.module-app-page-thumb-scale {
  position: absolute;
  top: 0;
  left: 0;
  width: 1440px;
  height: 900px;
  transform: scale(0.126);
  transform-origin: top left;
  pointer-events: none;
}

.module-app-page-thumb-runtime {
  width: 1440px !important;
  height: 900px !important;
  min-width: 1440px !important;
  min-height: 900px !important;
  max-width: 1440px !important;
  max-height: 900px !important;
  overflow: hidden !important;
  pointer-events: none !important;
}

.module-app-page-thumb-real :deep(.runtime-shell--artboard),
.module-app-page-thumb-real :deep(.artboard-runtime-main),
.module-app-page-thumb-real :deep(.pattern-runtime),
.module-app-page-thumb-real :deep(.ui-runtime-shell),
.module-app-page-thumb-real :deep(.runtime-screen),
.module-app-page-thumb-real :deep(.a9-runtime-design-canvas),
.module-app-page-thumb-real :deep(.business-page),
.module-app-page-thumb-real :deep(.auth-page),
.module-app-page-thumb-real :deep(.business-body),
.module-app-page-thumb-real :deep(.page-canvas) {
  width: 1440px !important;
  min-width: 1440px !important;
  max-width: 1440px !important;
  height: 900px !important;
  min-height: 900px !important;
  max-height: 900px !important;
  overflow: hidden !important;
  box-sizing: border-box !important;
}

.module-app-page-thumb-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 3px;
  background: linear-gradient(180deg, rgba(31, 41, 55, 0.96), rgba(17, 24, 39, 0.98));
  color: rgba(203, 213, 225, 0.72);
  text-align: center;
}

.module-app-page-thumb-empty strong {
  color: rgba(226, 232, 240, 0.88);
  font-size: 12px;
  font-weight: 950;
}

.module-app-page-thumb-empty span {
  color: rgba(148, 163, 184, 0.78);
  font-size: 9px;
}

.module-app-page-thumb-shell > i {
  position: absolute;
  right: 6px;
  bottom: 6px;
  z-index: 5;
  border-radius: 999px;
  padding: 2px 7px;
  background: rgba(37, 99, 235, 0.16);
  color: #1d4ed8;
  font-size: 9px;
  font-style: normal;
  font-weight: 950;
}

.module-app-page-navigator-item.status-draft .module-app-page-thumb-shell > i {
  background: rgba(245, 158, 11, 0.2);
  color: #92400e;
}

.module-app-page-navigator-item.status-missing .module-app-page-thumb-shell > i {
  background: rgba(148, 163, 184, 0.16);
  color: rgba(226, 232, 240, 0.84);
}

.module-app-page-thumb-caption {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.module-app-page-thumb-caption strong {
  overflow: hidden;
  color: #f8fafc;
  font-size: 11px;
  font-weight: 950;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-app-page-thumb-caption span {
  width: max-content;
  max-width: 100%;
  overflow: hidden;
  border-radius: 999px;
  padding: 2px 7px;
  background: rgba(148, 163, 184, 0.12);
  color: rgba(203, 213, 225, 0.68);
  font-size: 9px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.studio-confirm-button {
  border: 1px solid rgba(34, 197, 94, 0.54);
  border-radius: 999px;
  min-height: 34px;
  padding: 0 14px;
  background: linear-gradient(135deg, #16a34a 0%, #15803d 100%);
  color: #f0fdf4;
  font-size: 12px;
  font-weight: 950;
  cursor: pointer;
  box-shadow: 0 12px 26px rgba(22, 163, 74, 0.24);
}

.studio-confirm-button:disabled {
  cursor: not-allowed;
  border-color: rgba(148, 163, 184, 0.26);
  background: rgba(71, 85, 105, 0.58);
  color: #94a3b8;
  box-shadow: none;
}

</style>
