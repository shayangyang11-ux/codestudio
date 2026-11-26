<template>
  <div
    class="runtime-shell"
    @mousemove.capture="handlePrototypeInspectPointerMove"
    @mousedown.capture="handlePrototypeInspectPointerDown"
    @click.capture="handlePrototypeInspectClick"
    @mouseleave="clearPrototypeInspectHover"
    :class="[
      `runtime-pattern-${runtimeContext.pattern.toLowerCase()}`,
      `runtime-theme-${runtimeContext.theme.key}`,
      `runtime-industry-${runtimeContext.industryStyle.cssClass}`,
      {
        'runtime-shell--artboard': isArtboardMode,
        'runtime-shell--inspect': props.inspectComponentMode,
      },
    ]"
    :data-ui-runtime-renderer="runtimeRendererName"
    :data-ui-runtime-mode="props.displayMode"
    :style="themeVars"
  >
    <div
      v-if="props.inspectComponentMode"
      class="a9-inspect-toolbar"
      @click.stop
      @mousedown.stop
      @mouseup.stop
    >
      <div class="a9-inspect-toolbar-main">
        <strong>元素选择器</strong>
        <span>{{ inspectScopeLabel }}</span>
      </div>

      <div class="a9-inspect-toolbar-actions" role="group" aria-label="元素选择模式">
        <button
          type="button"
          :class="{ active: inspectPickScope === 'FINE' }"
          @click="inspectPickScope = 'FINE'"
        >
          精细选择
        </button>
        <button
          type="button"
          :class="{ active: inspectPickScope === 'OUTER' }"
          @click="inspectPickScope = 'OUTER'"
        >
          外层选择
        </button>
        <button type="button" @click="clearPrototypeSelectionFromToolbar">
          清除锁定
        </button>
        <button type="button" class="danger" @click="requestExitPrototypeInspectMode">
          退出选择
        </button>
      </div>
    </div>

    <template v-if="isArtboardMode">
      <main class="artboard-runtime-main">
        <UiContractPatternRenderer
          :contract="contract"
          :runtime-context="runtimeContext"
          :component-map="componentMap"
          :interactions="contract.interactions"
          :form-values="formValues"
          :field-errors="fieldErrors"
          :mock-data="runtimeData"
          :active-tabs="activeTabs"
          :visible-component-ids="visibleComponentIds"
          :disabled-component-ids="disabledComponentIds"
          :canvas-components="canvasComponents"
          @execute="handleExecute"
          @update-field="handleUpdateField"
          @switch-tab="handleSwitchTab"
        />

        <section
          v-if="currentState && currentState.type !== 'DEFAULT'"
          class="state-banner artboard-state-banner"
          :class="currentState.type.toLowerCase()"
        >
          <strong>{{ currentState.title || currentState.type }}</strong>
          <span>{{ currentState.message || stateFallbackMessage(currentState.type) }}</span>
        </section>
      </main>
    </template>

    <template v-else>
      <aside class="runtime-sidebar">
        <section>
          <p class="eyebrow">Page Contract</p>
          <h3>{{ contract.page.pageName }}</h3>
          <dl>
            <dt>路由</dt>
            <dd>{{ currentRoute }}</dd>
            <dt>页面类型</dt>
            <dd>{{ contract.page.pageKind }}</dd>
            <dt>布局</dt>
            <dd>{{ contract.page.layout.type }}</dd>
            <dt>质量分</dt>
            <dd>{{ contract.qualitySummary.score }}</dd>
            <dt>覆盖率</dt>
            <dd>{{ contract.qualitySummary.coverageRate }}%</dd>
          </dl>
        </section>

        <section>
          <p class="eyebrow">Mock Scenario</p>
          <h3>场景模式</h3>
          <div class="segmented">
            <button
              :class="{ active: scenarioMode === 'SUCCESS' }"
              @click="scenarioMode = 'SUCCESS'"
            >
              成功
            </button>
            <button
              :class="{ active: scenarioMode === 'FAILURE' }"
              @click="scenarioMode = 'FAILURE'"
            >
              失败
            </button>
          </div>
          <p class="helper">
            提交、查询和删除动作会优先执行当前结果类型对应的 Mock 场景。
          </p>
        </section>

        <section>
          <p class="eyebrow">Page State</p>
          <h3>状态模拟</h3>
          <button
            v-for="state in contract.pageStates"
            :key="state.stateKey"
            class="state-button"
            :class="{ active: state.stateKey === currentPageStateKey }"
            @click="setPageState(state.stateKey)"
          >
            {{ state.title || state.type }}
          </button>
        </section>

        <section>
          <p class="eyebrow">Feature Coverage</p>
          <h3>功能点覆盖</h3>
          <div
            v-for="coverage in contract.featureCoverage"
            :key="coverage.featureId"
            class="coverage-row"
          >
            <div>
              <strong>{{ coverage.featureName }}</strong>
              <span>{{ coverage.featureKey }}</span>
            </div>
            <em :class="coverage.status.toLowerCase()">
              {{ coverage.status }}
            </em>
          </div>
        </section>
      </aside>

      <main class="runtime-main">
        <header class="runtime-header">
          <div>
            <p class="eyebrow">Mock Preview Runtime</p>
            <h2>{{ contract.page.title }}</h2>
            <span>{{ contract.page.description }}</span>
          </div>

          <div class="header-actions">
            <button @click="resetRuntime(true)">
              重置 Mock
            </button>
          </div>
        </header>

        <section
          v-if="currentState && currentState.type !== 'DEFAULT'"
          class="state-banner"
          :class="currentState.type.toLowerCase()"
        >
          <strong>{{ currentState.title || currentState.type }}</strong>
          <span>{{ currentState.message || stateFallbackMessage(currentState.type) }}</span>
        </section>

        <section
          class="page-canvas"
          :class="`layout-${normalizedPreviewLayoutType.toLowerCase()}`"
        >
          <UiContractRuntimeNode
            v-for="component in canvasComponents"
            :key="component.componentId"
            :component="component"
            :component-map="componentMap"
            :interactions="contract.interactions"
            :form-values="formValues"
            :field-errors="fieldErrors"
            :mock-data="runtimeData"
            :active-tabs="activeTabs"
            :visible-component-ids="visibleComponentIds"
            :disabled-component-ids="disabledComponentIds"
            @execute="handleExecute"
            @update-field="handleUpdateField"
            @switch-tab="handleSwitchTab"
          />
        </section>
      </main>

      <aside class="runtime-inspector">
        <section>
          <p class="eyebrow">API Bindings</p>
          <h3>接口绑定</h3>
          <div
            v-for="binding in contract.apiBindings"
            :key="binding.bindingKey"
            class="binding-card"
          >
            <strong>{{ binding.method }} {{ binding.path }}</strong>
            <span>{{ binding.bindingKey }}</span>
            <em>{{ binding.status || 'MOCK' }}</em>
          </div>
          <p v-if="!contract.apiBindings.length" class="empty-tip">
            当前没有正式接口绑定，Runtime 使用 Mock Scenario。
          </p>
        </section>

        <section>
          <p class="eyebrow">Runtime Data</p>
          <h3>模拟数据</h3>
          <div
            v-for="(value, key) in runtimeData"
            :key="key"
            class="data-card"
          >
            <strong>{{ key }}</strong>
            <span>
              {{ Array.isArray(value) ? `${value.length} 条` : valueType(value) }}
            </span>
          </div>
        </section>

        <section v-if="contract.assumptions.length">
          <p class="eyebrow">Assumptions</p>
          <h3>当前假设</h3>
          <ul class="assumption-list">
            <li
              v-for="assumption in contract.assumptions"
              :key="assumption"
            >
              {{ assumption }}
            </li>
          </ul>
        </section>
      </aside>
    </template>

    <div class="toast-stack">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="toast.level.toLowerCase()"
      >
        {{ toast.message }}
      </div>
    </div>

    <div
      v-if="activeModal"
      class="modal-mask"
      @click.self="closeModal"
    >
      <article class="modal-card">
        <header>
          <div>
            <p class="eyebrow">Modal</p>
            <h3>{{ activeModal.title || '弹窗' }}</h3>
          </div>
          <button @click="closeModal">×</button>
        </header>

        <div class="runtime-modal-content">


          <!-- A9.10.14.4.3.13.2.10.1_RUNTIME_MODAL_PORTAL_FIX_ROBUST: render MODAL children in the portal shell instead of rendering the MODAL component itself. -->


          <UiContractRuntimeNode


            v-for="component in activeModalRenderableChildren"


            :key="component.componentId"


            :component="component"


            :component-map="componentMap"


            :interactions="contract.interactions"


            :form-values="formValues"


            :field-errors="fieldErrors"


            :mock-data="runtimeData"


            :active-tabs="activeTabs"


            :visible-component-ids="[]"


            :disabled-component-ids="disabledComponentIds"


            @execute="handleExecute"


            @update-field="handleUpdateField"


            @switch-tab="handleSwitchTab"


          />


          <p v-if="!activeModalRenderableChildren.length" class="empty-tip">


            当前弹窗没有可渲染内容。


          </p>


        </div>
      </article>
    </div>

    <div
      v-if="pendingConfirmAction"
      class="modal-mask"
      @click.self="cancelConfirm"
    >
      <article class="confirm-card">
        <p class="eyebrow">Confirm</p>
        <h3>{{ pendingConfirmAction.label || '确认操作' }}</h3>
        <p>
          {{
            pendingConfirmAction.confirmationMessage ||
              '确认执行当前操作吗？'
          }}
        </p>
        <footer>
          <button class="ghost-button" @click="cancelConfirm">
            取消
          </button>
          <button class="primary-button" @click="confirmPendingAction">
            确认
          </button>
        </footer>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  reactive,
  ref,
  watch,
  nextTick,
} from 'vue'
import UiContractRuntimeNode from './ui-contract-runtime/ElementPlusUiContractRuntimeNode.vue'
import UiContractPatternRenderer from './ui-contract-runtime/UiContractPatternRenderer.vue'
import {
  getThemeCssVars,
  resolveUiRuntimeContext,
} from './ui-contract-runtime/uiContractTheme'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type {
  UiContractComponent,
  UiContractContent,
  UiContractInteraction,
  UiContractMockScenario,
  UiContractPageState,
  UiContractValidationRule,
} from '../types/uiContract'

const props = withDefaults(
  defineProps<{
    contract: UiContractContent
    displayMode?: 'workbench' | 'artboard'
    projectThemeProfile?: ProjectThemeProfile | null
    focusComponentId?: string | null
    focusElementSelection?: Record<string, unknown> | null
    inspectComponentMode?: boolean
    initialRowContext?: RuntimeRow | null
    initialRuntimeDataSession?: Record<string, unknown> | null
    navigateRequest?: (
      payload: PrototypeNavigatePayload,
    ) => Promise<PrototypeNavigationResult> | PrototypeNavigationResult
  }>(),
  {
    displayMode: 'workbench',
    projectThemeProfile: null,
    focusComponentId: null,
    focusElementSelection: null,
    inspectComponentMode: false,
    initialRowContext: null,
    initialRuntimeDataSession: null,
  },
)

type RuntimeRow = Record<string, unknown>
type ScenarioMode = 'SUCCESS' | 'FAILURE'
type ToastLevel = 'SUCCESS' | 'ERROR' | 'INFO' | 'WARNING'

interface RuntimeToast {
  id: number
  message: string
  level: ToastLevel
}

interface ExecutePayload {
  actionId: string
  componentId: string
  row?: RuntimeRow
  label?: string
  dataBindingKey?: string
  tableComponentId?: string
  sourceComponentType?: string
}

interface PrototypeNavigatePayload {
  actionId: string
  actionType: string
  label: string
  targetPageId?: string | null
  targetPageKey?: string | null
  targetPageName?: string | null
  route?: string | null
  runtimeDataPatch?: Record<string, unknown> | null
  rowContext?: RuntimeRow | null
}

interface PrototypeActionPayload {
  actionId: string
  actionType: string
  label: string
  targetPageId?: string | null
  targetId?: string | null
}

interface PrototypeElementSelectionPayload {
  componentId: string
  componentType?: string
  componentTitle?: string
  elementKind?: string
  elementLabel?: string
  fieldKey?: string
  actionId?: string
  columnKey?: string
  tabKey?: string
  textKey?: string
  textRole?: string
  inspectPrecision?: string
}

interface PrototypeNavigationResult {
  ok: boolean
  code:
    | 'SUCCESS'
    | 'TARGET_NOT_FOUND'
    | 'ARTIFACT_NOT_FOUND'
    | 'INVALID_CONTRACT'
    | 'LOAD_FAILED'
    | 'BUSY'
    | 'ROUTER_UNAVAILABLE'
  message: string
  targetPageId?: string | null
  targetPageKey?: string | null
  targetPageName?: string | null
  route?: string | null
}

const emit = defineEmits<{
  prototypeNavigate: [payload: PrototypeNavigatePayload]
  prototypeAction: [payload: PrototypeActionPayload]
  selectComponent: [payload: PrototypeElementSelectionPayload]
  exitInspectMode: []
  clearSelection: []
}>()


watch(
  () => [
    props.focusComponentId || '',
    prototypeSelectionSignature(props.focusElementSelection),
  ],
  async ([componentId]) => {
    await nextTick()

    const target = resolveFocusedPrototypeElement(
      String(componentId || ''),
      props.focusElementSelection,
    )

    lockRuntimeComponent(String(componentId || ''), false, target)

    // A9.10.14.4.3.15.6.24G8_FOCUS_INLINE_NEAREST
    // Inspect mode selection must not call scrollIntoView, otherwise selecting
    // right-side buttons horizontally scrolls the artboard and shifts the page.
    if (!props.inspectComponentMode) {
      focusRuntimeComponent(String(componentId || ''), target)
    }
  },
  { immediate: true },
)

watch(
  () => props.initialRowContext,
  () => {
    applyInitialRowContextToForms()
  },
  { deep: true },
)

watch(
  () => props.initialRuntimeDataSession,
  () => {
    applyInitialRuntimeDataSession()
  },
  { deep: true },
)

const scenarioMode = ref<ScenarioMode>('SUCCESS')

const normalizedPreviewLayoutType = computed(() => {
  const pageKind = String(props.contract.page.pageKind || '').toUpperCase()
  const pageName = String(props.contract.page.pageName || props.contract.page.title || '')
  const rawLayout = String(props.contract.page.layout?.type || '').toUpperCase()
  const formByName = /鍒涘缓|鏂板缓|鏂板|缂栬緫|褰曞叆|琛ㄥ崟/.test(pageName)

  // A9.9.1 enterprise layout class repair:
  // existing artifacts may have pageKind=FORM_PAGE but layout.type=TABLE_LIST.
  if (pageKind === 'FORM_PAGE' || formByName) return 'FORM_DETAIL'
  if (pageKind === 'LIST_PAGE') return 'TABLE_LIST'
  if (pageKind === 'DETAIL_PAGE') return 'DETAIL_TABS'
  if (pageKind === 'DASHBOARD_PAGE') return 'DASHBOARD'
  return rawLayout || 'CUSTOM_GRID'
})
const currentPageStateKey = ref('')
const currentRoute = ref('')
const activeModalId = ref<string | null>(null)
const pendingConfirmAction = ref<UiContractInteraction | null>(null)
const pendingConfirmContext = ref<ExecutePayload | null>(null)
const toastSequence = ref(0)
const inspectHoverElement = ref<HTMLElement | null>(null)
const inspectSelectedElement = ref<HTMLElement | null>(null)

type InspectPickScope = 'FINE' | 'OUTER'
const inspectPickScope = ref<InspectPickScope>('FINE')
const inspectScopeLabel = computed(() =>
  inspectPickScope.value === 'OUTER'
    ? '外层选择：优先选中字段外壳、表单、卡片等容器。'
    : '精细选择：优先选中输入框、按钮、字段名、表格列等最小元素。',
)

const formValues = reactive<Record<string, unknown>>({})
const initialFormValues = reactive<Record<string, unknown>>({})
const fieldErrors = reactive<Record<string, string>>({})
const runtimeData = reactive<Record<string, unknown>>({})
const runtimeDataSession = reactive<Record<string, unknown>>({})
const activeTabs = reactive<Record<string, string>>({})
const toasts = ref<RuntimeToast[]>([])

const isArtboardMode = computed(() => props.displayMode === 'artboard')
const runtimeRendererName = computed(() => isArtboardMode.value ? 'ant-design-vue' : 'legacy-workbench')
const runtimeContext = computed(() =>
  resolveUiRuntimeContext(
    props.contract,
    props.projectThemeProfile,
  ),
)
const themeVars = computed(() => getThemeCssVars(runtimeContext.value.theme))

const componentMap = computed<Record<string, UiContractComponent>>(() => {
  const result: Record<string, UiContractComponent> = {}

  const visit = (components: UiContractComponent[]) => {
    for (const component of components) {
      result[component.componentId] = component
      visit(component.children || [])
    }
  }

  visit(props.contract.page.componentTree)
  return result
})

const currentState = computed<UiContractPageState | null>(() => {
  return props.contract.pageStates.find(
    (state) => state.stateKey === currentPageStateKey.value,
  ) || null
})

// A9.10.14.4.3.13.2.9.1_EMPTY_STATE_EXPLICIT_VISIBILITY
// visibleComponentIds normally expand downward so top-level regions render all
// children. EMPTY_STATE is different: it is a required boundary component for
// QualityGate/canvas editing, but it must not appear with non-empty tables unless
// the current page state explicitly lists that EMPTY_STATE component id.
function collectVisibleDescendantComponentIds(
  componentId: string,
  result: Set<string>,
  explicitIds: Set<string>,
) {
  const component = componentMap.value[componentId]

  if (!component) {
    return
  }

  const children = Array.isArray(component.children)
    ? component.children
    : []

  for (const child of children) {
    const childId = String(child?.componentId || '').trim()

    if (!childId || result.has(childId)) {
      continue
    }

    const childType = String(child?.type || '').toUpperCase()
    if (childType === 'EMPTY_STATE' && !explicitIds.has(childId)) {
      continue
    }

    result.add(childId)
    collectVisibleDescendantComponentIds(childId, result, explicitIds)
  }
}

function expandVisibleComponentIds(componentIds: string[] | undefined | null): string[] {
  const explicitIds = Array.isArray(componentIds)
    ? componentIds
        .map((item) => String(item || '').trim())
        .filter(Boolean)
    : []

  if (!explicitIds.length) {
    return []
  }

  const explicitIdSet = new Set(explicitIds)
  const result = new Set<string>()

  for (const componentId of explicitIds) {
    result.add(componentId)
    collectVisibleDescendantComponentIds(componentId, result, explicitIdSet)
  }

  return Array.from(result)
}

// A9.9.1 descendant visibility repair:
// pageState.visibleComponentIds can declare only top-level regions.
// Runtime must still render all child FORM / TABLE / BUTTON nodes under them.
const visibleComponentIds = computed(() => {
  return expandVisibleComponentIds(currentState.value?.visibleComponentIds || [])
})

const disabledComponentIds = computed(() => {
  return currentState.value?.disabledComponentIds || []
})

// A9.10.14.4.3.13.2.10.1_RUNTIME_MODAL_PORTAL_FIX_ROBUST
// UI_CONTRACT may keep MODAL / EMPTY_STATE nodes inside normal containers so
// QualityGate and schema stay strict. Runtime must treat them as portal/boundary
// components: MODAL renders only through the modal portal, and EMPTY_STATE only
// renders for the EMPTY page state.
function runtimeComponentType(component?: UiContractComponent | null): string {
  return String(component?.type || '').trim().toUpperCase()
}

function isRuntimePortalComponentType(component?: UiContractComponent | null): boolean {
  return ['MODAL', 'DIALOG', 'CONFIRM_MODAL'].includes(runtimeComponentType(component))
}

function isRuntimeEmptyBoundaryComponentType(component?: UiContractComponent | null): boolean {
  return runtimeComponentType(component) === 'EMPTY_STATE'
}

function isRuntimeEmptyStateActive(): boolean {
  const state = currentState.value
  return String(state?.type || '').toUpperCase() === 'EMPTY' || String(state?.stateKey || '').toUpperCase() === 'EMPTY'
}

function shouldRenderRuntimeCanvasComponent(component?: UiContractComponent | null): boolean {
  if (!component) return false
  if (isRuntimePortalComponentType(component)) return false
  if (isRuntimeEmptyBoundaryComponentType(component)) return isRuntimeEmptyStateActive()
  return true
}

function cloneRuntimeCanvasComponent(component: UiContractComponent): UiContractComponent | null {
  if (!shouldRenderRuntimeCanvasComponent(component)) return null

  const cloned = {
    ...component,
    children: (component.children || [])
      .map((child) => cloneRuntimeCanvasComponent(child))
      .filter((child): child is UiContractComponent => Boolean(child)),
  }

  return cloned
}

const canvasComponents = computed(() => {
  return props.contract.page.componentTree
    .map((component) => cloneRuntimeCanvasComponent(component))
    .filter((component): component is UiContractComponent => Boolean(component))
})

const activeModal = computed<UiContractComponent | null>(() => {
  if (!activeModalId.value) return null
  const component = componentMap.value[activeModalId.value]
  return component?.type === 'MODAL' ? component : null
})

const activeModalRenderableChildren = computed(() => {
  return (activeModal.value?.children || [])
    .map((component) => cloneRuntimeCanvasComponent(component))
    .filter((component): component is UiContractComponent => Boolean(component))
})

watch(
  () => props.contract,
  () => resetRuntime(),
  {
    immediate: true,
    deep: true,
  },
)


watch(
  () => props.inspectComponentMode,
  (enabled) => {
    if (!enabled) {
      clearPrototypeInspectHover()
    }
  },
)


// A9.10.14.4.3.15.6.24G5_SELECTION_SCROLL_LOCK
type PrototypeHorizontalScrollSnapshot = Array<{
  element: HTMLElement
  scrollLeft: number
}>

function capturePrototypeHorizontalScroll(source?: EventTarget | null): PrototypeHorizontalScrollSnapshot {
  if (typeof document === 'undefined') return []

  const root = source instanceof HTMLElement
    ? source.closest('.runtime-shell') as HTMLElement | null
    : null

  const selectors = [
    '.runtime-shell',
    '.runtime-shell--artboard',
    '.artboard-runtime-main',
    '.pattern-runtime',
    '.business-page',
    '.business-body',
    '.module-app-artboard-host',
    '.module-app-artboard-scale',
    '.module-app-shell',
    '.module-app-content',
  ]

  const bucket = new Set<HTMLElement>()

  for (const selector of selectors) {
    if (root?.matches(selector)) {
      bucket.add(root)
    }

    root?.querySelectorAll(selector).forEach((element) => {
      if (element instanceof HTMLElement) {
        bucket.add(element)
      }
    })

    document.querySelectorAll(selector).forEach((element) => {
      if (element instanceof HTMLElement) {
        bucket.add(element)
      }
    })
  }

  return Array.from(bucket)
    .filter((element) => element.scrollWidth > element.clientWidth || element.scrollLeft !== 0)
    .map((element) => ({
      element,
      scrollLeft: element.scrollLeft,
    }))
}

function restorePrototypeHorizontalScroll(snapshot: PrototypeHorizontalScrollSnapshot) {
  for (const item of snapshot) {
    item.element.scrollLeft = item.scrollLeft
  }

  forcePrototypeInspectHorizontalZero()
}


function handlePrototypeInspectPointerMove(event: MouseEvent) {
  if (!props.inspectComponentMode) {
    return
  }

  const target = resolvePrototypeInspectTarget(event)

  if (target === inspectHoverElement.value) {
    return
  }

  clearPrototypeInspectHover()

  if (target) {
    inspectHoverElement.value = target
    target.classList.add('a9-component-inspect-hover')
  }
}



function handlePrototypeInspectPointerDown(event: MouseEvent) {
  if (!props.inspectComponentMode) {
    return
  }

  const target = resolvePrototypeInspectTarget(event)

  if (!target) {
    return
  }

  const snapshot = capturePrototypeHorizontalScroll(event.target)
  event.preventDefault()

  restorePrototypeHorizontalScroll(snapshot)

  window.requestAnimationFrame(() => {
    restorePrototypeHorizontalScroll(snapshot)
  })
}


// A9.10.14.4.3.15.6.24G7_ADMIN_SELECTION_SCROLL_ROOTFIX
function forcePrototypeInspectHorizontalZero(source?: EventTarget | null) {
  if (typeof document === 'undefined') return

  const root = source instanceof HTMLElement
    ? source.closest('.runtime-shell') as HTMLElement | null
    : null

  const selectors = [
    '.runtime-shell',
    '.runtime-shell--artboard',
    '.artboard-runtime-main',
    '.pattern-runtime',
    '.business-page',
    '.business-body',
    '.a9-admin-page',
    '.a9-admin-card',
    '.a9-admin-table-card',
    '.el-table__body-wrapper',
    '.el-table__header-wrapper',
    '.el-scrollbar__wrap',
    '.module-app-runtime',
    '.module-app-shell-page',
    '.module-app-shell',
  ]

  const bucket = new Set<HTMLElement>()

  for (const selector of selectors) {
    if (root?.matches(selector)) {
      bucket.add(root)
    }

    root?.querySelectorAll(selector).forEach((element) => {
      if (element instanceof HTMLElement) {
        bucket.add(element)
      }
    })

    document.querySelectorAll(selector).forEach((element) => {
      if (element instanceof HTMLElement) {
        bucket.add(element)
      }
    })
  }

  for (const element of bucket) {
    if (element.scrollLeft !== 0) {
      element.scrollLeft = 0
    }
  }
}


function handlePrototypeInspectClick(event: MouseEvent) {
  if (!props.inspectComponentMode) {
    return
  }

  const horizontalScrollSnapshot = capturePrototypeHorizontalScroll(event.target)
  const target = resolvePrototypeInspectTarget(event)

  if (!target) {
    restorePrototypeHorizontalScroll(horizontalScrollSnapshot)
    return
  }

  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()

  clearPrototypeInspectHover()
  lockRuntimeComponent(target.dataset.uiComponentId || '', true, target)

  emit('selectComponent', buildPrototypeElementSelectionPayload(target) as any)
  forcePrototypeInspectHorizontalZero(event.target)

  window.requestAnimationFrame(() => {
    forcePrototypeInspectHorizontalZero(event.target)
  })

  window.setTimeout(() => {
    forcePrototypeInspectHorizontalZero(event.target)
  }, 0)
  restorePrototypeHorizontalScroll(horizontalScrollSnapshot)

  window.requestAnimationFrame(() => {
    restorePrototypeHorizontalScroll(horizontalScrollSnapshot)
  })
}


function prototypeSelectionSignature(selection: unknown): string {
  const record = asPrototypeSelectionRecord(selection)

  if (!record) {
    return ''
  }

  return [
    selectionString(record.componentId),
    selectionString(record.elementKind),
    selectionString(record.fieldKey),
    selectionString(record.actionId),
    selectionString(record.columnKey),
    selectionString(record.tabKey),
    selectionString(record.textKey),
    selectionString(record.textRole),
    selectionString(record.elementLabel),
  ].join('|')
}

function asPrototypeSelectionRecord(selection: unknown): Record<string, unknown> | null {
  return selection && typeof selection === 'object'
    ? selection as Record<string, unknown>
    : null
}

function selectionString(value: unknown): string {
  return String(value || '').trim()
}

function resolveFocusedPrototypeElement(
  componentId: string,
  selection: unknown,
): HTMLElement | null {
  if (typeof document === 'undefined') {
    return null
  }

  const record = asPrototypeSelectionRecord(selection)
  const normalizedComponentId = selectionString(componentId || record?.componentId)

  if (!normalizedComponentId) {
    return null
  }

  const base = `[data-ui-component-id="${escapeCssSelectorValue(normalizedComponentId)}"]`

  // A9.10.14.4.3.15.6.24J7C_COLUMN_FOCUS_COMPOSITE_KEY
  // Focus replay must use composite identity.
  // Column labels all share data-ui-text-key="columnLabel"; querying textKey first
  // returns the first column. Always prefer componentId + columnKey + kind/textKey.
  if (record) {
    const kind = selectionString(record.elementKind).toUpperCase()
    const columnKey = selectionString(record.columnKey)
    const actionId = selectionString(record.actionId)
    const fieldKey = selectionString(record.fieldKey)
    const tabKey = selectionString(record.tabKey)
    const textKey = selectionString(record.textKey)
    const textRole = selectionString(record.textRole)
    const rowIndex = selectionString(record.rowIndex)

    if (columnKey) {
      const columnSelector = `${base}[data-ui-column-key="${escapeCssSelectorValue(columnKey)}"]`

      if (
        kind.includes('COLUMN_LABEL') ||
        textKey === 'columnLabel' ||
        textRole === 'label'
      ) {
        const labelTarget = document.querySelector(
          `${columnSelector}[data-ui-element-kind="TABLE_COLUMN_LABEL"], ${columnSelector}[data-ui-text-key="columnLabel"]`,
        ) as HTMLElement | null

        if (labelTarget) {
          return labelTarget
        }
      }

      if (kind.includes('TABLE_CELL') || kind.includes('CELL')) {
        const cellTarget = document.querySelector(
          `${columnSelector}[data-ui-element-kind="TABLE_CELL"]`,
        ) as HTMLElement | null

        if (cellTarget) {
          return cellTarget
        }
      }

      const columnTarget = document.querySelector(
        `${columnSelector}[data-ui-element-kind="TABLE_COLUMN"]`,
      ) as HTMLElement | null

      if (columnTarget) {
        return columnTarget
      }

      const fallbackColumnTarget = document.querySelector(columnSelector) as HTMLElement | null
      if (fallbackColumnTarget) {
        return fallbackColumnTarget
      }
    }

    if (kind.includes('TABLE_ROW')) {
      const rowSelector = rowIndex
        ? `${base}[data-ui-element-kind="TABLE_ROW"][data-ui-row-index="${escapeCssSelectorValue(rowIndex)}"]`
        : `${base}[data-ui-element-kind="TABLE_ROW"]`
      const rowTarget = document.querySelector(rowSelector) as HTMLElement | null

      if (rowTarget) {
        return rowTarget
      }
    }

    if (actionId) {
      const actionSelector = `${base}[data-ui-action-id="${escapeCssSelectorValue(actionId)}"]`
      const kindActionTarget = kind
        ? document.querySelector(`${actionSelector}[data-ui-element-kind="${escapeCssSelectorValue(kind)}"]`) as HTMLElement | null
        : null

      if (kindActionTarget) {
        return kindActionTarget
      }

      const actionTarget = document.querySelector(actionSelector) as HTMLElement | null
      if (actionTarget) {
        return actionTarget
      }
    }

    if (fieldKey) {
      const fieldSelector = `${base}[data-ui-field-key="${escapeCssSelectorValue(fieldKey)}"]`
      const kindFieldTarget = kind
        ? document.querySelector(`${fieldSelector}[data-ui-element-kind="${escapeCssSelectorValue(kind)}"]`) as HTMLElement | null
        : null

      if (kindFieldTarget) {
        return kindFieldTarget
      }

      const fieldTarget = document.querySelector(fieldSelector) as HTMLElement | null
      if (fieldTarget) {
        return fieldTarget
      }
    }

    if (tabKey) {
      const tabTarget = document.querySelector(
        `${base}[data-ui-tab-key="${escapeCssSelectorValue(tabKey)}"]`,
      ) as HTMLElement | null

      if (tabTarget) {
        return tabTarget
      }
    }
  }

  const preciseAttrs: Array<[string, string]> = [
    ['fieldKey', 'data-ui-field-key'],
    ['actionId', 'data-ui-action-id'],
    ['columnKey', 'data-ui-column-key'],
    ['tabKey', 'data-ui-tab-key'],
    ['textKey', 'data-ui-text-key'],
    ['textRole', 'data-ui-text-role'],
  ]

  if (record) {
    for (const [key, attr] of preciseAttrs) {
      const value = selectionString(record[key])
      if (!value) continue

      const target = document.querySelector(
        `${base}[${attr}="${escapeCssSelectorValue(value)}"]`,
      ) as HTMLElement | null

      if (target) {
        return target
      }
    }

    const kind = selectionString(record.elementKind).toUpperCase()
    if (kind) {
      const kindTarget = document.querySelector(
        `${base}[data-ui-element-kind="${escapeCssSelectorValue(kind)}"]`,
      ) as HTMLElement | null

      if (kindTarget) {
        return kindTarget
      }
    }
  }

  return findRuntimeComponentElement(normalizedComponentId)
}

function resolveDirectPrototypePickTarget(rawTarget: HTMLElement): HTMLElement | null {
  const direct = rawTarget.closest('[data-ui-selectable="true"]') as HTMLElement | null

  if (!direct) {
    return null
  }

  const tag = direct.tagName.toUpperCase()
  const kind = String(
    direct.dataset.uiElementKind ||
      direct.dataset.uiComponentType ||
      '',
  ).toUpperCase()

  if (
    direct.dataset.uiTextKey ||
    direct.dataset.uiTextRole ||
    direct.dataset.uiFieldKey ||
    direct.dataset.uiActionId ||
    direct.dataset.uiColumnKey ||
    direct.dataset.uiTabKey
  ) {
    return direct
  }

  if (['H1', 'H2', 'H3', 'H4', 'P', 'SPAN', 'LABEL', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'TH', 'TD'].includes(tag)) {
    return direct
  }

  if (['TITLE', 'TEXT', 'DESCRIPTION', 'ACTION', 'BUTTON', 'FIELD_CONTROL', 'FIELD_LABEL', 'TABLE_COLUMN', 'TABLE_CELL', 'TAB'].includes(kind)) {
    return direct
  }

  return null
}


// A9.10.14.4.3.15.6.24J7B_TABLE_HIT_TEST_MODEL
function resolveTableSemanticInspectTarget(
  event: MouseEvent,
  rawTarget: HTMLElement,
): HTMLElement | null {
  const row = rawTarget.closest('.a9-admin-table .el-table__body-wrapper tr') as HTMLElement | null

  if (!row) {
    return null
  }

  // If the pointer is really on a selectable text/tag/button, direct target
  // already returned above. Reaching here means user clicked row/cell blank area.
  const owner = row.querySelector('[data-ui-component-id]') as HTMLElement | null
  const componentId = String(owner?.dataset.uiComponentId || '').trim()

  if (!componentId) {
    return null
  }

  const siblings = Array.from(row.parentElement?.children || []).filter((item) =>
    item instanceof HTMLElement && item.tagName.toUpperCase() === 'TR',
  )
  const rowIndex = Math.max(0, siblings.indexOf(row)) + 1
  const componentType = String(owner?.dataset.uiComponentType || '').trim()

  row.dataset.uiSelectable = 'true'
  row.dataset.uiElementKind = 'TABLE_ROW'
  row.dataset.uiComponentId = componentId
  row.dataset.uiComponentType = componentType
  row.dataset.uiElementLabel = `表格行模板 · 第${rowIndex}行`
  row.dataset.uiRowIndex = String(rowIndex)

  return row
}


// A9.10.14.4.3.15.6.24J7F_MOCK_CELL_NOT_SELECTABLE
function isMockTableCellValueTarget(rawTarget: HTMLElement): boolean {
  const mockValue = rawTarget.closest('[data-ui-mock-value="true"], .a9-admin-cell-mock-value') as HTMLElement | null

  if (!mockValue) {
    return false
  }

  if (!mockValue.closest('.a9-admin-table .el-table__body-wrapper')) {
    return false
  }

  // Row action buttons are real prototype actions, not mock values.
  if (rawTarget.closest('[data-ui-element-kind="TABLE_ACTION"], button, .el-button')) {
    return false
  }

  return true
}

function resolvePrototypeInspectTargetBase(event: Event): HTMLElement | null {
  if (!props.inspectComponentMode) {
    return null
  }

  const rawTarget = event.target

  if (!(rawTarget instanceof HTMLElement)) {
    return null
  }

  if (!(event instanceof MouseEvent)) {
    return rawTarget.closest('[data-ui-selectable="true"], [data-ui-component-id]') as HTMLElement | null
  }

  // A9.10.14.4.3.15.6.24J7F_MOCK_CELL_NOT_SELECTABLE
  // Mock cell text is generated preview data. Clicking it should not select a fake data value.
  if (isMockTableCellValueTarget(rawTarget)) {
    return null
  }

  const directTarget = resolveDirectPrototypePickTarget(rawTarget)
  if (directTarget) {
    return directTarget
  }

  // A9.10.14.4.3.15.6.24J7B_TABLE_HIT_TEST_MODEL
  // Element Plus <tr> does not carry data-ui-selectable.
  // If user clicks row blank area, synthesize TABLE_ROW on the real row element.
  const tableSemanticTarget = resolveTableSemanticInspectTarget(event, rawTarget)
  if (tableSemanticTarget) {
    return tableSemanticTarget
  }

  const runtimeRoot = rawTarget.closest('.runtime-shell') as HTMLElement | null
  const candidates = collectPrototypeInspectCandidates(event, runtimeRoot)

  if (!candidates.length) {
    return rawTarget.closest('[data-ui-selectable="true"], [data-ui-component-id]') as HTMLElement | null
  }

  const preferOuter = event.altKey || event.shiftKey

  return candidates
    .map((element) => ({
      element,
      score: scorePrototypeInspectCandidate(element, event, runtimeRoot, preferOuter),
    }))
    .sort((left, right) => right.score - left.score)[0]?.element || null
}

// A9.6.8 HIT PRECISION HELPERS BEGIN
function collectPrototypeInspectCandidates(
  event: MouseEvent,
  runtimeRoot: HTMLElement | null,
): HTMLElement[] {
  const pointElements = typeof document !== 'undefined' && document.elementsFromPoint
    ? document.elementsFromPoint(event.clientX, event.clientY)
    : []

  const candidates: HTMLElement[] = []

  for (const element of pointElements) {
    if (!(element instanceof HTMLElement)) {
      continue
    }

    if (runtimeRoot && !runtimeRoot.contains(element)) {
      continue
    }

    collectSelectableAncestors(element, runtimeRoot, candidates)
  }

  if (!candidates.length && event.target instanceof HTMLElement) {
    collectSelectableAncestors(event.target, runtimeRoot, candidates)
  }

  return dedupePrototypeInspectCandidates(candidates)
    .filter((element) => !isPrototypeInspectRootOnlyElement(element, runtimeRoot))
}

function collectSelectableAncestors(
  start: HTMLElement,
  runtimeRoot: HTMLElement | null,
  bucket: HTMLElement[],
) {
  let cursor: HTMLElement | null = start

  while (cursor) {
    if (runtimeRoot && !runtimeRoot.contains(cursor)) {
      break
    }

    if (isPrototypeSelectableElement(cursor)) {
      bucket.push(cursor)
    }

    if (cursor === runtimeRoot) {
      break
    }

    cursor = cursor.parentElement
  }
}

function isPrototypeSelectableElement(element: HTMLElement): boolean {
  return element.matches('[data-ui-selectable="true"], [data-ui-component-id]')
}

function dedupePrototypeInspectCandidates(elements: HTMLElement[]): HTMLElement[] {
  const seen = new Set<HTMLElement>()
  const result: HTMLElement[] = []

  for (const element of elements) {
    if (seen.has(element)) {
      continue
    }

    seen.add(element)
    result.push(element)
  }

  return result
}

function isPrototypeInspectRootOnlyElement(
  element: HTMLElement,
  runtimeRoot: HTMLElement | null,
): boolean {
  if (!runtimeRoot) {
    return false
  }

  if (element === runtimeRoot) {
    return true
  }

  const kind = String(element.dataset.uiElementKind || '').toUpperCase()
  const componentType = String(element.dataset.uiComponentType || '').toUpperCase()

  return ['PATTERN_ROOT', 'PAGE_ROOT', 'ROOT'].includes(kind) ||
    ['PATTERN_ROOT', 'PAGE_ROOT', 'ROOT'].includes(componentType)
}

function scorePrototypeInspectCandidate(
  element: HTMLElement,
  event: MouseEvent,
  runtimeRoot: HTMLElement | null,
  preferOuter: boolean,
): number {
  const rect = element.getBoundingClientRect()
  const area = Math.max(1, rect.width * rect.height)
  const depth = prototypeElementDepth(element, runtimeRoot)
  const kindPriority = prototypeElementKindPriority(element)
  const targetBonus = event.target === element ? 16 : 0
  const centerBonus = prototypePointerCenterBonus(element, event)

  if (preferOuter) {
    const containerBonus = prototypeOuterContainerBonus(element)
    const areaBonus = Math.min(90, Math.log10(area + 1) * 24)
    const finePenalty = prototypeFineElementPenalty(element)

    return containerBonus + areaBonus + centerBonus + targetBonus - finePenalty - depth * 0.35
  }

  const compactBonus = Math.max(0, 72000 - Math.min(area, 72000)) / 820
  const fineBonus = prototypeFineElementBonus(element)

  return kindPriority + compactBonus + depth * 2.4 + centerBonus + targetBonus + fineBonus
}

function prototypeElementKindPriority(element: HTMLElement): number {
  const kind = String(
    element.dataset.uiElementKind ||
      element.dataset.uiElementRole ||
      element.dataset.uiComponentType ||
      element.tagName ||
      '',
  ).toUpperCase()

  const tag = element.tagName.toUpperCase()

  if (['INPUT', 'SELECT', 'TEXTAREA'].includes(tag)) return 145
  if (['FIELD_CONTROL', 'INPUT', 'SELECT', 'TEXTAREA', 'CHECKBOX', 'RADIO', 'SWITCH'].includes(kind)) return 142
  if (['BUTTON', 'ACTION', 'PRIMARY_ACTION', 'SECONDARY_ACTION', 'ROW_ACTION', 'LINK'].includes(kind)) return 136
  if (['FIELD_LABEL', 'LABEL'].includes(kind)) return 126
  if (['TABLE_CELL', 'CELL'].includes(kind)) return 116
  if (['TABLE_COLUMN', 'COLUMN', 'TABLE_HEADER'].includes(kind)) return 108
  if (['TAB', 'TAB_ITEM'].includes(kind)) return 104
  if (['TEXT', 'TITLE', 'DESCRIPTION', 'HELP_TEXT', 'HINT', 'MESSAGE'].includes(kind)) return 98
  if (['FIELD', 'FORM_FIELD'].includes(kind)) return 86
  if (['METRIC_ITEM', 'STAT_ITEM', 'CARD_ITEM'].includes(kind)) return 78
  if (['FORM', 'FIELD_GROUP', 'TABLE', 'CARD', 'SECTION', 'TOOLBAR', 'PAGE_HEADER'].includes(kind)) return 48
  if (element.dataset.uiComponentId) return 36

  return 0
}

function prototypeOuterContainerBonus(element: HTMLElement): number {
  const kind = String(
    element.dataset.uiElementKind ||
      element.dataset.uiComponentType ||
      '',
  ).toUpperCase()

  if (['SECTION', 'CARD', 'FORM', 'FIELD_GROUP', 'TABLE', 'TOOLBAR', 'PAGE_HEADER', 'EMPTY_STATE', 'ALERT'].includes(kind)) return 130
  if (element.dataset.uiComponentId && !element.dataset.uiFieldKey && !element.dataset.uiActionId && !element.dataset.uiColumnKey) return 92
  if (['FIELD', 'FORM_FIELD'].includes(kind)) return 58
  return 12
}

function prototypeFineElementBonus(element: HTMLElement): number {
  const tag = element.tagName.toUpperCase()
  const kind = String(element.dataset.uiElementKind || '').toUpperCase()

  if (['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(tag)) return 38
  if (element.dataset.uiFieldKey || element.dataset.uiActionId || element.dataset.uiColumnKey || element.dataset.uiTabKey) return 34
  if (['FIELD_CONTROL', 'FIELD_LABEL', 'ACTION', 'BUTTON', 'TABLE_CELL', 'TABLE_COLUMN', 'TAB', 'TEXT'].includes(kind)) return 26
  return 0
}

function prototypeFineElementPenalty(element: HTMLElement): number {
  const tag = element.tagName.toUpperCase()
  const kind = String(element.dataset.uiElementKind || '').toUpperCase()

  if (['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(tag)) return 68
  if (['FIELD_CONTROL', 'FIELD_LABEL', 'ACTION', 'TABLE_CELL', 'TABLE_COLUMN', 'TAB', 'TEXT'].includes(kind)) return 52
  return 0
}

function prototypePointerCenterBonus(element: HTMLElement, event: MouseEvent): number {
  const rect = element.getBoundingClientRect()
  if (!rect.width || !rect.height) {
    return 0
  }

  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const dx = Math.abs(event.clientX - centerX) / Math.max(1, rect.width)
  const dy = Math.abs(event.clientY - centerY) / Math.max(1, rect.height)
  const distance = Math.min(1, Math.sqrt(dx * dx + dy * dy))

  return (1 - distance) * 10
}

function prototypeElementDepth(
  element: HTMLElement,
  runtimeRoot: HTMLElement | null,
): number {
  let depth = 0
  let cursor: HTMLElement | null = element

  while (cursor && cursor !== runtimeRoot) {
    depth += 1
    cursor = cursor.parentElement
  }

  return depth
}

function buildPrototypeElementSelectionPayload(
  target: HTMLElement,
): Record<string, unknown> {
  const componentId = String(
    target.dataset.uiComponentId ||
      target.closest('[data-ui-component-id]')?.getAttribute('data-ui-component-id') ||
      '',
  )

  const component = componentId ? componentMap.value[componentId] : null
  const label = runtimeComponentReadableLabel(componentId, target)

  return {
    componentId,
    componentType: String(component?.type || target.dataset.uiComponentType || ''),
    componentTitle: label,
    elementKind: String(target.dataset.uiElementKind || target.dataset.uiComponentType || ''),
    elementLabel: label,
    fieldKey: target.dataset.uiFieldKey || '',
    actionId: target.dataset.uiActionId || '',
    columnKey: target.dataset.uiColumnKey || '',
    tabKey: target.dataset.uiTabKey || '',
    textKey: target.dataset.uiTextKey || '',
    textRole: target.dataset.uiTextRole || target.dataset.uiTextKey || '',
    elementPath: buildPrototypeElementPath(target),
    inspectPrecision: eventSelectionPrecisionHint(target),
  }
}

function buildPrototypeElementPath(target: HTMLElement): string[] {
  const result: string[] = []
  let cursor: HTMLElement | null = target
  const runtimeRoot = target.closest('.runtime-shell') as HTMLElement | null

  while (cursor && cursor !== runtimeRoot) {
    if (isPrototypeSelectableElement(cursor)) {
      const componentId = cursor.dataset.uiComponentId || ''
      const label = runtimeComponentReadableLabel(componentId, cursor)
      const kind = normalizePrototypeElementKindLabel(
        cursor.dataset.uiElementKind || cursor.dataset.uiComponentType || cursor.tagName,
      )
      result.push(label ? `${kind}：${label}` : kind)
    }
    cursor = cursor.parentElement
  }

  return result.reverse().slice(-8)
}

function eventSelectionPrecisionHint(target: HTMLElement): string {
  const kind = String(target.dataset.uiElementKind || target.dataset.uiComponentType || target.tagName || '').toUpperCase()

  if (['INPUT', 'SELECT', 'TEXTAREA', 'FIELD_CONTROL'].includes(kind) || ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName.toUpperCase())) {
    return 'FIELD_CONTROL'
  }
  if (target.dataset.uiActionId || ['BUTTON', 'ACTION'].includes(kind) || target.tagName.toUpperCase() === 'BUTTON') {
    return 'ACTION'
  }
  if (kind === 'TABLE_ROW') {
    return 'TABLE_ROW'
  }
  if (kind === 'TABLE_COLUMN_LABEL') {
    return 'TABLE_COLUMN_LABEL'
  }
  if (target.dataset.uiColumnKey) {
    return kind === 'TABLE_CELL' ? 'TABLE_CELL' : 'TABLE_COLUMN'
  }
  if (target.dataset.uiFieldKey) {
    return 'FIELD'
  }
  if (target.dataset.uiTextKey || target.dataset.uiTextRole) {
    return 'TEXT'
  }

  return 'COMPONENT'
}

function normalizePrototypeElementKindLabel(kind: unknown): string {
  const normalized = String(kind || '').toUpperCase()
  const map: Record<string, string> = {
    FIELD_CONTROL: '输入控件',
    FIELD_LABEL: '字段名',
    FIELD: '字段',
    FORM_FIELD: '字段',
    ACTION: '按钮',
    BUTTON: '按钮',
    PRIMARY_ACTION: '主按钮',
    SECONDARY_ACTION: '次按钮',
    ROW_ACTION: '行操作',
    TABLE_COLUMN: '表格列',
    TABLE_COLUMN_LABEL: '列名',
    TABLE_CELL: '单元格',
    TABLE_ROW: '表格行',
    TAB: '标签页',
    TEXT: '文案',
    TITLE: '标题',
    DESCRIPTION: '说明',
    CARD: '卡片',
    SECTION: '区域',
    FORM: '表单',
    TABLE: '表格',
  }

  return map[normalized] || normalized || '元素'
}
// A9.6.8 HIT PRECISION HELPERS END

function buildPrototypeElementSelection(
  target: HTMLElement,
): PrototypeElementSelectionPayload | null {
  const componentId = String(target.dataset.uiComponentId || '').trim()

  if (!componentId) {
    return null
  }

  const selectedComponent = componentMap.value[componentId]
  const componentType = String(
    selectedComponent?.type ||
      target.dataset.uiComponentType ||
      '',
  )

  const elementKind = String(
    target.dataset.uiElementKind ||
      'COMPONENT',
  ).toUpperCase()

  const elementLabel = runtimeComponentReadableLabel(componentId, target)

  return {
    componentId,
    componentType,
    componentTitle: String(
      selectedComponent?.title ||
        selectedComponent?.description ||
        componentId,
    ),
    elementKind,
    elementLabel,
    fieldKey: target.dataset.uiFieldKey || '',
    actionId: target.dataset.uiActionId || '',
    columnKey: target.dataset.uiColumnKey || '',
    tabKey: target.dataset.uiTabKey || '',
    textKey: target.dataset.uiTextKey || '',
  }
}


function resolvePrototypeInspectTarget(event: Event): HTMLElement | null {
  const baseTarget = resolvePrototypeInspectTargetBase(event)

  if (!baseTarget) {
    return null
  }

  if (inspectPickScope.value === 'OUTER') {
    return resolvePrototypeOuterInspectableElement(baseTarget)
  }

  return baseTarget
}

function resolvePrototypeOuterInspectableElement(
  baseTarget: HTMLElement,
): HTMLElement {
  const root = baseTarget.closest('.runtime-shell')
  let cursor: HTMLElement | null = baseTarget.parentElement

  while (cursor && cursor !== root) {
    if (
      cursor.hasAttribute('data-ui-component-id') ||
      cursor.getAttribute('data-ui-selectable') === 'true'
    ) {
      return cursor
    }

    cursor = cursor.parentElement
  }

  const componentRoot = baseTarget.closest('[data-ui-component-id]') as HTMLElement | null
  return componentRoot || baseTarget
}

function clearPrototypeSelectionFromToolbar() {
  clearPrototypeInspectHover()
  clearPrototypeInspectSelection()
  emit('clearSelection')
}

function requestExitPrototypeInspectMode() {
  clearPrototypeInspectHover()
  emit('exitInspectMode')
}

function clearPrototypeInspectHover() {
  if (inspectHoverElement.value) {
    inspectHoverElement.value.classList.remove('a9-component-inspect-hover')
  }

  inspectHoverElement.value = null
}

function resetRuntime(clearSession = false) {
  clearPrototypeInspectSelection()
  clearRecord(formValues)
  clearRecord(initialFormValues)
  clearRecord(fieldErrors)
  clearRecord(runtimeData)
  clearRecord(activeTabs)

  if (clearSession) {
    clearRecord(runtimeDataSession)
  }

  Object.assign(runtimeData, deepClone(props.contract.mockData || {}))
  applyInitialRuntimeDataSession()
  mergeRuntimeDataSessionIntoCurrentPage()

  for (const component of Object.values(componentMap.value)) {
    for (const field of component.fields || []) {
      const value = deepClone(field.defaultValue ?? defaultFieldValue(field.type))
      initialFormValues[field.fieldKey] = value
      formValues[field.fieldKey] = deepClone(value)
    }

    if (component.type === 'TABS' && component.tabs?.length) {
      activeTabs[component.componentId] = component.tabs[0].tabKey
    }
  }

  applyInitialRowContextToForms()

  currentRoute.value = props.contract.page.route
  currentPageStateKey.value =
    props.contract.pageStates.find((state) => state.type === 'DEFAULT')?.stateKey ||
    props.contract.pageStates[0]?.stateKey ||
    ''

  activeModalId.value = null
  pendingConfirmAction.value = null
  pendingConfirmContext.value = null
  toasts.value = []
}

function handleUpdateField(
  payload: {
    fieldKey: string
    value: unknown
  },
) {
  formValues[payload.fieldKey] = payload.value
  delete fieldErrors[payload.fieldKey]
}

function handleSwitchTab(
  payload: {
    componentId: string
    tabKey: string
  },
) {
  activeTabs[payload.componentId] = payload.tabKey
}

function applyInitialRuntimeDataSession() {
  const session = props.initialRuntimeDataSession
  if (!session || typeof session !== 'object' || Array.isArray(session)) return

  Object.assign(runtimeData, deepClone(session))
  Object.assign(runtimeDataSession, deepClone(session))
}

function applyInitialRowContextToForms() {
  const row = props.initialRowContext
  if (!row || typeof row !== 'object' || Array.isArray(row)) return

  for (const component of Object.values(componentMap.value)) {
    for (const field of component.fields || []) {
      if (!(field.fieldKey in row)) continue
      const value = deepClone(row[field.fieldKey])
      formValues[field.fieldKey] = value
      initialFormValues[field.fieldKey] = deepClone(value)
      delete fieldErrors[field.fieldKey]
    }
  }
}

async function handleExecute(
  payload: ExecutePayload,
) {
  await executeAction(payload.actionId, payload)
}

async function executeAction(
  actionId: string,
  context: ExecutePayload,
  visited = new Set<string>(),
): Promise<void> {
  if (visited.has(actionId)) {
    showToast(`检测到动作循环：${actionId}`, 'ERROR')
    return
  }

  visited.add(actionId)

  const action =
    props.contract.interactions.find((item) => item.actionId === actionId) ||
    buildA924L2AFallbackAction(actionId, context)

  if (!action) {
    showToast(`动作不存在：${actionId}`, 'ERROR')
    return
  }

  if (String(action.type || '').toUpperCase() !== 'NAVIGATE') {
    emit('prototypeAction', {
      actionId: action.actionId,
      actionType: String(action.type || ''),
      label: String(action.label || ''),
      targetPageId: action.targetPageId || null,
      targetId: action.targetId || null,
    })
  }

  switch (action.type) {
    case 'SET_VALUE':
      applySetValue(action)
      break

    case 'RESET_FORM':
      resetForm(action.targetId)
      showToast(action.label || '表单已重置', 'INFO')
      break

    case 'SUBMIT_FORM': {
      if (!validateForm(action.targetId)) {
        showToast('请先修正表单校验错误。', 'ERROR')
        return
      }
      const succeeded = await runScenario(action, context, visited)
      if (succeeded) {
        const mutatedRow = applyRuntimeMockMutation(action, context)
        if (action.payload?.closeModalOnSuccess !== false) {
          closeModal()
        }
        await navigateAfterSubmit(action, context, mutatedRow)
      }
      break
    }

    case 'OPEN_MODAL':
      openModal(action.targetId, context.row)
      break

    case 'CLOSE_MODAL':
      closeModal()
      break

    case 'SWITCH_TAB':
      if (action.targetId && action.targetTabKey) {
        activeTabs[action.targetId] = action.targetTabKey
      }
      break

    case 'NAVIGATE':
      await navigateTo(action, context)
      break

    case 'SHOW_TOAST':
      showToast(
        String(action.payload?.message || action.label || '操作完成'),
        normalizeToastLevel(action.payload?.level),
      )
      break

    case 'OPEN_CONFIRM':
      pendingConfirmAction.value = action
      pendingConfirmContext.value = context
      break

    case 'CONFIRM_ACTION':
      await executeChainedActions(
        action.successActionIds || [],
        context,
        visited,
      )
      pendingConfirmAction.value = null
      pendingConfirmContext.value = null
      break

    case 'CANCEL_ACTION':
      cancelConfirm()
      break

    case 'SET_PAGE_STATE':
      if (action.targetId) setPageState(action.targetId)
      break

    case 'QUERY_DATA':
      await runScenario(action, context, visited)
      applyA924L2AQueryFilter(action)
      break

    case 'DELETE_ROW': {
      const succeeded = await runScenario(action, context, visited)
      if (succeeded) {
        deleteRow(action, context.row)
      }
      break
    }
  }
}

function validateForm(
  targetId?: string | null,
): boolean {
  clearRecord(fieldErrors)

  const target = targetId ? componentMap.value[targetId] : null
  const fields = target?.fields || []

  for (const field of fields) {
    const rules = props.contract.validationRules.filter(
      (rule) =>
        rule.fieldKey === field.fieldKey &&
        (
          !field.validationRuleIds?.length ||
          field.validationRuleIds.includes(rule.ruleId)
        ),
    )

    if (field.required && !rules.some((rule) => rule.type === 'REQUIRED')) {
      rules.unshift({
        ruleId: `runtime_required_${field.fieldKey}`,
        fieldKey: field.fieldKey,
        type: 'REQUIRED',
        message: `${field.label}不能为空。`,
      })
    }

    for (const rule of rules) {
      const message = validateRule(rule)
      if (message) {
        fieldErrors[field.fieldKey] = message
        break
      }
    }
  }

  return Object.keys(fieldErrors).length === 0
}

function validateRule(
  rule: UiContractValidationRule,
): string {
  const value = formValues[rule.fieldKey]
  const text = value === null || value === undefined ? '' : String(value)

  switch (rule.type) {
    case 'REQUIRED':
      return value === null || value === undefined || text.trim() === ''
        ? rule.message
        : ''

    case 'MIN_LENGTH':
      return text.length < Number(rule.value || 0) ? rule.message : ''

    case 'MAX_LENGTH':
      return text.length > Number(rule.value || 0) ? rule.message : ''

    case 'PATTERN':
      try {
        return new RegExp(String(rule.value || '')).test(text)
          ? ''
          : rule.message
      } catch {
        return rule.message
      }

    case 'EMAIL':
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)
        ? ''
        : rule.message

    case 'MIN_VALUE':
      return Number(value) < Number(rule.value) ? rule.message : ''

    case 'MAX_VALUE':
      return Number(value) > Number(rule.value) ? rule.message : ''

    case 'MATCH_FIELD':
      return value !== formValues[rule.compareFieldKey || '']
        ? rule.message
        : ''
  }

  return ''
}

async function runScenario(
  action: UiContractInteraction,
  context: ExecutePayload,
  visited: Set<string>,
): Promise<boolean> {
  const scenario = selectScenario(action)

  if (!scenario) {
    showToast(action.label || '演示操作已完成。', 'SUCCESS')
    await executeChainedActions(
      action.successActionIds || [],
      context,
      visited,
    )
    return true
  }

  const loadingState = props.contract.pageStates.find(
    (state) => state.type === 'LOADING',
  )

  if (loadingState) {
    currentPageStateKey.value = loadingState.stateKey
  }

  await delay(scenario.delayMs || 0)

  if (scenario.nextPageState) {
    setPageState(scenario.nextPageState)
  } else {
    const fallbackState = props.contract.pageStates.find(
      (state) =>
        state.type ===
        (scenario.outcome === 'SUCCESS' ? 'SUCCESS' : 'ERROR'),
    )

    if (fallbackState) {
      currentPageStateKey.value = fallbackState.stateKey
    } else {
      setDefaultState()
    }
  }

  if (
    scenario.outcome === 'SUCCESS' &&
    action.dataBindingKey &&
    scenario.response &&
    Object.keys(scenario.response).length
  ) {
    const responseValue =
      Object.prototype.hasOwnProperty.call(scenario.response, 'data')
        ? scenario.response.data
        : scenario.response

    runtimeData[action.dataBindingKey] = deepClone(responseValue)
  }

  showToast(
    scenario.message ||
      (scenario.outcome === 'SUCCESS' ? '演示操作成功。' : '演示操作失败。'),
    scenario.outcome === 'SUCCESS' ? 'SUCCESS' : 'ERROR',
  )

  await executeChainedActions(
    scenario.outcome === 'SUCCESS'
      ? action.successActionIds || []
      : action.failureActionIds || [],
    context,
    visited,
  )

  return scenario.outcome === 'SUCCESS'
}

function selectScenario(
  action: UiContractInteraction,
): UiContractMockScenario | null {
  if (action.scenarioKey) {
    const exact = props.contract.mockScenarios.find(
      (scenario) => scenario.scenarioKey === action.scenarioKey,
    )
    if (exact) return exact
  }

  return props.contract.mockScenarios.find(
    (scenario) =>
      scenario.actionId === action.actionId &&
      scenario.outcome === scenarioMode.value,
  ) || null
}

async function executeChainedActions(
  actionIds: string[],
  context: ExecutePayload,
  visited: Set<string>,
) {
  for (const actionId of actionIds) {
    await executeAction(actionId, context, new Set(visited))
  }
}

function applySetValue(
  action: UiContractInteraction,
) {
  const fieldKey = String(
    action.payload?.fieldKey ||
      action.targetId ||
      '',
  )

  if (!fieldKey) return

  formValues[fieldKey] = deepClone(action.payload?.value)
  delete fieldErrors[fieldKey]
}

function resetForm(
  targetId?: string | null,
) {
  const target = targetId ? componentMap.value[targetId] : null

  for (const field of target?.fields || []) {
    formValues[field.fieldKey] = deepClone(
      initialFormValues[field.fieldKey],
    )
    delete fieldErrors[field.fieldKey]
  }
}

// A9.10.14.4.3.13.1_RUNTIME_MOCK_MUTATIONS
function openModal(
  targetId?: string | null,
  row?: RuntimeRow,
) {
  if (!targetId || componentMap.value[targetId]?.type !== 'MODAL') {
    showToast('目标弹窗不存在。', 'ERROR')
    return
  }

  activeModalId.value = targetId
  hydrateModalForms(targetId, row)
}

function hydrateModalForms(
  modalId: string,
  row?: RuntimeRow,
) {
  const modal = componentMap.value[modalId]
  if (!modal) return

  const forms = collectForms(modal)
  for (const form of forms) {
    for (const field of form.fields || []) {
      const fallback = field.defaultValue ?? defaultFieldValue(field.type)
      const value = row && Object.prototype.hasOwnProperty.call(row, field.fieldKey)
        ? row[field.fieldKey]
        : fallback
      formValues[field.fieldKey] = deepClone(value)
      initialFormValues[field.fieldKey] = deepClone(value)
      delete fieldErrors[field.fieldKey]
    }
  }
}

function collectForms(
  component: UiContractComponent,
): UiContractComponent[] {
  const result: UiContractComponent[] = []

  const visit = (item: UiContractComponent) => {
    if (item.type === 'FORM') {
      result.push(item)
    }
    for (const child of item.children || []) {
      visit(child)
    }
  }

  visit(component)
  return result
}

function closeModal() {
  activeModalId.value = null
}

function applyRuntimeMockMutation(
  action: UiContractInteraction,
  context: ExecutePayload,
): RuntimeRow | null {
  const effect = String(
    action.payload?.runtimeEffect ||
      action.payload?.mockEffect ||
      '',
  ).toUpperCase()

  if (!effect) return null

  if (effect === 'ADD_ROW') {
    return addRuntimeRow(action)
  }

  if (effect === 'UPDATE_ROW') {
    return updateRuntimeRow(action, context.row)
  }

  return null
}

function addRuntimeRow(
  action: UiContractInteraction,
): RuntimeRow {
  const bindingKey = resolveRuntimeBindingKey(action)
  const nextRow = buildRuntimeRowFromForms()
  nextRow.id = nextRow.id || nextRow.key || `mock_${Date.now()}`
  nextRow.key = nextRow.key || nextRow.id

  if (!bindingKey) return nextRow

  for (const key of resolveRuntimeMutationBindingKeys(action, bindingKey)) {
    const current = runtimeData[key]
    const rows = Array.isArray(current) ? [...current] : []
    rows.unshift(deepClone(nextRow))
    runtimeData[key] = rows
    runtimeDataSession[key] = deepClone(rows)
  }

  showToast(String(action.payload?.message || '已新增一条演示记录，并同步到目标列表页。'), 'SUCCESS')
  return nextRow
}

function updateRuntimeRow(
  action: UiContractInteraction,
  row?: RuntimeRow,
): RuntimeRow {
  const bindingKey = resolveRuntimeBindingKey(action)
  const identity = row ? rowIdentity(row) : rowIdentity(buildRuntimeRowFromForms())
  const nextValues = buildRuntimeRowFromForms()
  let updated = false
  let mutatedRow: RuntimeRow = row ? { ...row, ...nextValues } : { ...nextValues }

  if (!bindingKey) return mutatedRow

  for (const key of resolveRuntimeMutationBindingKeys(action, bindingKey)) {
    const current = runtimeData[key]
    if (!Array.isArray(current)) continue

    const rows = current.map((item) => {
      if (typeof item !== 'object' || item === null || Array.isArray(item)) {
        return item
      }
      const candidate = item as RuntimeRow
      if (rowIdentity(candidate) !== identity) return item
      updated = true
      mutatedRow = { ...candidate, ...nextValues, id: candidate.id || nextValues.id, key: candidate.key || nextValues.key }
      return mutatedRow
    })

    runtimeData[key] = rows
    runtimeDataSession[key] = deepClone(rows)
  }

  if (!updated && !row) {
    const targetKey = resolveRuntimeMutationBindingKeys(action, bindingKey)[0]
    if (targetKey) {
      const current = runtimeData[targetKey]
      const rows = Array.isArray(current) ? [...current] : []
      const nextRow = { ...nextValues }
      nextRow.id = nextRow.id || nextRow.key || `mock_${Date.now()}`
      nextRow.key = nextRow.key || nextRow.id
      rows.unshift(nextRow)
      runtimeData[targetKey] = rows
      runtimeDataSession[targetKey] = deepClone(rows)
      mutatedRow = nextRow
    }
  }

  showToast(String(action.payload?.message || '已更新当前演示记录，并同步到目标列表页。'), 'SUCCESS')
  return mutatedRow
}

function resolveRuntimeMutationBindingKeys(
  action: UiContractInteraction,
  fallback: string,
): string[] {
  const payload = action.payload || {}
  const keys = [
    payload.targetDataBindingKey,
    payload.target_data_binding_key,
    payload.dataBindingKey,
    action.dataBindingKey,
    fallback,
    'rows',
  ]
    .map((item) => String(item || '').trim())
    .filter(Boolean)

  return Array.from(new Set(keys))
}

function mergeRuntimeDataSessionIntoCurrentPage() {
  for (const [key, value] of Object.entries(runtimeDataSession)) {
    runtimeData[key] = deepClone(value)
  }
}

function buildRuntimeRowFromForms(): RuntimeRow {
  const row: RuntimeRow = {}
  for (const [key, value] of Object.entries(formValues)) {
    row[key] = deepClone(value)
  }
  return row
}

function resolveRuntimeBindingKey(
  action: UiContractInteraction,
): string {
  const explicit = String(
    action.payload?.dataBindingKey ||
      action.dataBindingKey ||
      '',
  ).trim()

  if (explicit) {
    return explicit
  }

  return resolveA924L2ADefaultBindingKey()
}






// A9.10.14.4.3.15.6.24L2A_RUNTIME_DEMO_ACTION_FALLBACK
// Runtime demo fallback: older or weak UI_CONTRACTs may render buttons with
// data-ui-action-id but without a complete interaction object. Instead of
// failing with "动作不存在", infer a safe demo behavior from the action label.
function buildA924L2AFallbackAction(
  actionId: string,
  context: ExecutePayload,
): UiContractInteraction | null {
  const label = actionLabelFromRuntime(actionId, context)
  if (!label) return null

  const bindingKey = String(context.dataBindingKey || '').trim() || resolveA924L2ADefaultBindingKey()
  const navigationTarget = findA924L2ANavigationTargetForAction(label)

  if (navigationTarget) {
    return {
      actionId,
      label,
      type: 'NAVIGATE',
      targetPageId: String(navigationTarget.targetPageId || ''),
      targetPageKey: String(navigationTarget.targetPageKey || ''),
      targetPageName: String(navigationTarget.targetPageName || ''),
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
        targetPageKey: String(navigationTarget.targetPageKey || ''),
        targetPageName: String(navigationTarget.targetPageName || ''),
        route: String(navigationTarget.route || ''),
      },
    } as UiContractInteraction
  }

  if (/查询|搜索|筛选|检索/i.test(label)) {
    ensureA924L2A3RuntimeRows(bindingKey, context)
    return {
      actionId,
      label,
      type: 'QUERY_DATA',
      dataBindingKey: bindingKey,
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
      },
    } as UiContractInteraction
  }

  if (/重置|清空/i.test(label)) {
    return {
      actionId,
      label,
      type: 'RESET_FORM',
      targetId: findA924L2AFirstFormComponentId(),
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
        resetBindingKey: bindingKey,
      },
    } as UiContractInteraction
  }

  if (/删除|移除/i.test(label)) {
    ensureA924L2A3RuntimeRows(bindingKey, context)
    return {
      actionId,
      label,
      type: 'DELETE_ROW',
      dataBindingKey: bindingKey,
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
        message: '已从当前演示列表中移除该记录，刷新页面后恢复原始数据。',
      },
    } as UiContractInteraction
  }

  if (/保存|确定|确认|提交|状态更新|取消订单|解锁|异常记录/i.test(label)) {
    ensureA924L2A3RuntimeRows(bindingKey, context)
    return {
      actionId,
      label,
      type: 'SUBMIT_FORM',
      targetId: findA924L2AFirstFormComponentId(),
      dataBindingKey: bindingKey,
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
        runtimeEffect: inferA924L2ARuntimeEffect(label),
        closeModalOnSuccess: true,
        message: inferA924L2ASubmitMessage(label),
      },
    } as UiContractInteraction
  }

  if (/返回|取消|关闭/i.test(label)) {
    return {
      actionId,
      label,
      type: activeModalId.value ? 'CLOSE_MODAL' : 'SHOW_TOAST',
      payload: {
        inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
        message: activeModalId.value ? '已关闭当前弹窗。' : '当前页面已完成演示操作。',
        level: 'INFO',
      },
    } as UiContractInteraction
  }

  return {
    actionId,
    label,
    type: 'SHOW_TOAST',
    payload: {
      inferredBy: 'A9.10.14.4.3.15.6.24L2A3',
      message: `${label} 已完成演示处理。`,
      level: 'SUCCESS',
    },
  } as UiContractInteraction
}





function actionLabelFromRuntime(
  actionId: string,
  context: ExecutePayload,
): string {
  const fromContext = String(context.label || '').trim()
  if (fromContext) return fromContext

  const fromInteraction = props.contract.interactions.find((item) => item.actionId === actionId)
  const fromComponent = actionId
    ? Object.values(componentMap.value).find((component) =>
        Array.isArray(component.actionIds) && component.actionIds.includes(actionId),
      )
    : null

  const fromDomLabel = findA924L2A3ActionDomLabel(actionId)

  return String(
    fromInteraction?.label ||
      fromDomLabel ||
      fromComponent?.title ||
      fromComponent?.description ||
      actionId ||
      '',
  ).trim()
}



function findA924L2ANavigationTargetForAction(labelInput: string): Record<string, any> | null {
  const label = String(labelInput || '').trim()
  const targets = Array.isArray(props.contract.navigationTargets)
    ? props.contract.navigationTargets
    : []

  if (!label) return null

  const tokenGroups = a924L2ANavigationTokenGroups(label)
  const normalizedLabel = comparableRuntimeText(label)

  for (const target of targets) {
    const hay = comparableRuntimeText([
      target.label,
      target.actionLabel,
      target.targetPageName,
      target.targetPageKey,
      target.targetPageId,
      target.route,
    ].join(' '))

    if (normalizedLabel && hay.includes(normalizedLabel)) {
      return target
    }

    for (const group of tokenGroups) {
      if (group.some((token) => hay.includes(comparableRuntimeText(token)))) {
        return target
      }
    }
  }

  const inferredTargetName = inferA924L2ANavigationTargetName(label)
  if (inferredTargetName && props.navigateRequest) {
    return {
      targetPageId: '',
      targetPageKey: '',
      targetPageName: inferredTargetName,
      route: '',
      source: 'A9.10.14.4.3.15.6.24L2A2_INFERRED_RUNTIME_TARGET',
    }
  }

  return null
}



function inferA924L2ARuntimeEffect(labelInput: string): 'ADD_ROW' | 'UPDATE_ROW' {
  const label = String(labelInput || '')
  const pageText = String(
    props.contract.page?.pageName ||
      props.contract.page?.title ||
      props.contract.page?.pageKey ||
      '',
  )

  if (/新增|新建|创建|录入/i.test(label) || /新增|新建|创建|录入/i.test(pageText)) {
    return 'ADD_ROW'
  }

  return 'UPDATE_ROW'
}

function inferA924L2ASubmitMessage(labelInput: string): string {
  const label = String(labelInput || '')
  if (/取消订单|作废/i.test(label)) return '已更新当前演示记录状态，并记录本次取消原因。'
  if (/解锁/i.test(label)) return '已更新当前演示记录状态，并记录本次解锁原因。'
  if (/异常/i.test(label)) return '已追加当前演示记录的异常说明。'
  if (/状态/i.test(label)) return '已更新当前演示记录状态。'
  if (/新增|新建|创建|录入/i.test(label)) return '已新增一条演示记录，刷新页面后恢复原始数据。'
  return '已保存当前演示记录，刷新页面后恢复原始数据。'
}

function findA924L2AFirstFormComponentId(): string {
  for (const component of Object.values(componentMap.value)) {
    const type = String(component.type || '').toUpperCase()
    if (type === 'FORM' || type === 'FIELD_GROUP') {
      return component.componentId
    }
  }

  return ''
}

function resolveA924L2ADefaultBindingKey(): string {
  const table = findA924L2APrimaryTableComponent()

  if (table) {
    const key = a924L2ATableBindingKey(table)

    if (key) {
      ensureA924L2ARuntimeRowsFromTable(table, key)
      return key
    }
  }

  const keys = Object.keys(runtimeData)
  const arrayKey = keys.find((key) => Array.isArray(runtimeData[key]) && !key.endsWith('__a924L2AOriginalRows'))
  if (arrayKey) return arrayKey

  const mockData = props.contract.mockData || {}
  if (mockData && typeof mockData === 'object' && !Array.isArray(mockData)) {
    const key = Object.keys(mockData).find((item) => Array.isArray((mockData as Record<string, unknown>)[item]))
    if (key) return key
  }

  return 'rows'
}



function applyA924L2AQueryFilter(action: UiContractInteraction): void {
  const bindingKey = resolveRuntimeBindingKey(action)
  if (!bindingKey) return

  const current = runtimeData[bindingKey]
  if (!Array.isArray(current)) return

  const cacheKey = `${bindingKey}__a924L2AOriginalRows`
  if (!Array.isArray(runtimeData[cacheKey])) {
    runtimeData[cacheKey] = deepClone(current)
  }

  const source = Array.isArray(runtimeData[cacheKey])
    ? deepClone(runtimeData[cacheKey]) as RuntimeRow[]
    : deepClone(current) as RuntimeRow[]

  const filters = Object.entries(formValues)
    .map(([key, value]) => [key, normalizeA924L2AFilterValue(value)] as const)
    .filter(([, value]) => value)

  if (!filters.length) {
    runtimeData[bindingKey] = source
    runtimeData.rows = source
    showToast('已重置查询条件，展示全部演示数据。', 'INFO')
    return
  }

  const filtered = source.filter((row) => {
    return filters.every(([key, expected]) => rowMatchesA924L2AFilter(row, key, expected))
  })

  runtimeData[bindingKey] = filtered
  runtimeData.rows = filtered
  showToast(`查询完成，命中 ${filtered.length} 条演示记录。`, 'SUCCESS')
}



function rowMatchesA924L2AFilter(
  row: RuntimeRow,
  key: string,
  expected: string,
): boolean {
  const candidates = expandA924L2AFilterCandidates(expected)
  const exact = row && typeof row === 'object' ? row[key] : undefined

  if (exact !== undefined && exact !== null) {
    const exactText = comparableRuntimeText(exact)
    if (candidates.some((candidate) => exactText.includes(candidate))) {
      return true
    }
  }

  const joined = Object.values(row || {})
    .map((value) => comparableRuntimeText(value))
    .join(' ')

  return candidates.some((candidate) => joined.includes(candidate))
}



function normalizeA924L2AFilterValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) {
    return value.map(normalizeA924L2AFilterValue).filter(Boolean).join(' ')
  }

  return comparableRuntimeText(value)
}

function comparableRuntimeText(value: unknown): string {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '')
}


// A9.10.14.4.3.15.6.24L2A2_NAV_AND_QUERY_SOURCE_FIX
function a924L2ANavigationTokenGroups(labelInput: string): string[][] {
  const label = String(labelInput || '')
  const groups: string[][] = []

  if (/新增|新建|创建|录入/i.test(label)) groups.push(['新增', '新建', '创建', '录入', 'create', 'add'])
  if (/详情|查看详情|查看/i.test(label)) groups.push(['详情', '查看', 'detail', 'view'])
  if (/编辑|修改/i.test(label)) groups.push(['编辑', '修改', 'edit'])
  if (/返回/i.test(label)) groups.push(['列表', '查询', '管理', 'list'])
  if (/日志|记录/i.test(label)) groups.push(['日志', '操作日志', '记录', 'log'])
  if (/提交|确认|审核|审批/i.test(label)) groups.push(['提交', '确认', '审核', '审批'])
  if (/取消订单|取消|作废/i.test(label)) groups.push(['取消', '作废'])
  if (/解锁/i.test(label)) groups.push(['解锁'])
  if (/状态/i.test(label)) groups.push(['状态'])
  if (/异常/i.test(label)) groups.push(['异常'])

  return groups
}

function inferA924L2ANavigationTargetName(labelInput: string): string {
  const label = String(labelInput || '')

  if (/新增|新建|创建|录入/i.test(label)) return '新建页'
  if (/详情|查看详情|查看/i.test(label)) return '详情页'
  if (/编辑|修改/i.test(label)) return '编辑页'
  if (/返回/i.test(label)) return '列表页'
  if (/日志|操作日志/i.test(label)) return '操作日志页'
  if (/提交|确认|审核|审批/i.test(label)) return '提交确认弹窗'
  if (/取消订单|取消|作废/i.test(label)) return '订单取消确认弹窗'
  if (/解锁/i.test(label)) return '订单解锁确认弹窗'
  if (/状态/i.test(label)) return '状态更新弹窗'
  if (/异常/i.test(label)) return '异常记录弹窗'

  return ''
}

function resolveA924L2ANavigateTarget(action: UiContractInteraction): Record<string, any> | null {
  const targets = Array.isArray(props.contract.navigationTargets)
    ? props.contract.navigationTargets
    : []

  const actionAny = action as any
  const payload = (actionAny.payload || {}) as Record<string, any>
  const wantedId = comparableRuntimeText(action.targetPageId || payload.targetPageId || '')
  const wantedKey = comparableRuntimeText(actionAny.targetPageKey || payload.targetPageKey || '')
  const wantedName = comparableRuntimeText(actionAny.targetPageName || payload.targetPageName || inferA924L2ANavigationTargetName(String(action.label || '')))
  const wantedRoute = comparableRuntimeText(actionAny.route || payload.route || '')

  const matched = targets.find((target) => {
    const targetId = comparableRuntimeText(target.targetPageId || '')
    const targetKey = comparableRuntimeText(target.targetPageKey || '')
    const targetName = comparableRuntimeText(target.targetPageName || '')
    const targetRoute = comparableRuntimeText(target.route || '')

    return Boolean(
      (wantedId && targetId === wantedId) ||
      (wantedKey && targetKey === wantedKey) ||
      (wantedName && targetName.includes(wantedName)) ||
      (wantedRoute && targetRoute === wantedRoute),
    )
  })

  if (matched) return matched

  if (wantedName || wantedKey || wantedId || wantedRoute) {
    return {
      targetPageId: action.targetPageId || payload.targetPageId || '',
      targetPageKey: actionAny.targetPageKey || payload.targetPageKey || '',
      targetPageName: actionAny.targetPageName || payload.targetPageName || inferA924L2ANavigationTargetName(String(action.label || '')),
      route: actionAny.route || payload.route || '',
      source: 'A9.10.14.4.3.15.6.24L2A2_SYNTHETIC_NAV_TARGET',
    }
  }

  return null
}

function findA924L2APrimaryTableComponent(): UiContractComponent | null {
  for (const component of Object.values(componentMap.value)) {
    const type = String((component as any).type || '').toUpperCase()
    if (['TABLE', 'DATA_TABLE', 'LIST_TABLE'].includes(type)) {
      return component
    }
  }

  return null
}

function a924L2ATableBindingKey(component: UiContractComponent): string {
  const record = component as any
  const componentProps = a924L2APropsOf(component)
  return String(
    componentProps.dataKey ||
    componentProps.mockDataKey ||
    record.dataKey ||
    component.componentId ||
    'rows',
  ).trim()
}

function ensureA924L2ARuntimeRowsFromTable(
  component: UiContractComponent,
  key: string,
): void {
  if (!key || Array.isArray(runtimeData[key])) return

  const rows = a924L2AExtractRowsFromTableComponent(component)
  if (rows.length) {
    runtimeData[key] = deepClone(rows)
  }

  if (key !== 'rows' && !Array.isArray(runtimeData.rows) && rows.length) {
    runtimeData.rows = deepClone(rows)
  }
}

function a924L2AExtractRowsFromTableComponent(component: UiContractComponent): RuntimeRow[] {
  const record = component as any
  const componentProps = a924L2APropsOf(component)
  const candidates = [
    componentProps.rows,
    componentProps.data,
    record.rows,
    record.data,
  ]

  for (const candidate of candidates) {
    if (!Array.isArray(candidate)) continue

    const rows = candidate
      .filter((item) => item && typeof item === 'object' && !Array.isArray(item))
      .map((item) => ({ ...(item as RuntimeRow) }))

    if (rows.length) return rows
  }

  return []
}

function a924L2APropsOf(component: UiContractComponent): Record<string, any> {
  const record = component as any
  return {
    ...(record.props || {}),
    ...(record.componentProps || {}),
    ...(record.options || {}),
  }
}

function expandA924L2AFilterCandidates(expectedInput: string): string[] {
  const base = comparableRuntimeText(expectedInput)
  const result = new Set<string>()

  if (base) result.add(base)

  const groups = [
    ['已完成', '完成', 'finished', 'complete', 'completed', 'done', 'success', 'closed'],
    ['处理中', '处理', 'process', 'processing', 'running', 'inprogress'],
    ['待处理', '待办', 'pending', 'todo', 'waiting'],
    ['已取消', '取消', 'cancelled', 'canceled', 'void', '作废'],
    ['异常', 'error', 'exception', 'failed', 'fail'],
    ['已确认', '确认', 'confirmed', 'approved'],
  ]

  for (const group of groups) {
    if (group.some((item) => comparableRuntimeText(item) === base)) {
      for (const item of group) result.add(comparableRuntimeText(item))
    }
  }

  return Array.from(result).filter(Boolean)
}

// A9.10.14.4.3.15.6.24L2A3_GENERIC_RUNTIME_ACTION_CONTEXT
function findA924L2A3ActionDomLabel(actionId: string): string {
  if (!actionId || typeof document === 'undefined') return ''

  const escaped = actionId.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  const el = document.querySelector(`[data-ui-action-id="${escaped}"]`) as HTMLElement | null

  return String(
    el?.getAttribute('data-ui-element-label') ||
      el?.textContent ||
      '',
  ).trim()
}

function ensureA924L2A3RuntimeRows(
  bindingKey: string,
  context: ExecutePayload,
): void {
  if (!bindingKey) return
  if (Array.isArray(runtimeData[bindingKey]) && runtimeData[bindingKey].length) return

  const tableId = String(context.tableComponentId || '').trim()
  const table =
    (tableId && componentMap.value[tableId]) ||
    findA924L2APrimaryTableComponent()

  if (!table) return

  const rows = a924L2AExtractRowsFromTableComponent(table)
  if (!rows.length) return

  runtimeData[bindingKey] = deepClone(rows)
  runtimeData.rows = deepClone(rows)

  const cacheKey = `${bindingKey}__a924L2AOriginalRows`
  if (!Array.isArray(runtimeData[cacheKey])) {
    runtimeData[cacheKey] = deepClone(rows)
  }
}

function rowIdentity(
  row: RuntimeRow,
): unknown {
  return row.id ?? row.key ?? row.code ?? row.order_no ?? row.inbound_order_no ?? JSON.stringify(row)
}

async function navigateAfterSubmit(
  action: UiContractInteraction,
  context: ExecutePayload,
  mutatedRow?: RuntimeRow | null,
): Promise<void> {
  const afterSubmit = action.payload?.afterSubmit
  if (!afterSubmit || typeof afterSubmit !== 'object' || Array.isArray(afterSubmit)) return

  const record = afterSubmit as Record<string, unknown>
  const targetPageId = String(record.targetPageId || record.target_page_id || '').trim()
  const targetPageKey = String(record.targetPageKey || record.target_page_key || '').trim()
  const targetPageName = String(record.targetPageName || record.target_page_name || '').trim()
  const route = String(record.route || '').trim()
  const targetDataBindingKey = String(
    record.targetDataBindingKey ||
      record.target_data_binding_key ||
      action.payload?.targetDataBindingKey ||
      action.payload?.target_data_binding_key ||
      '',
  ).trim()

  if (!targetPageId && !targetPageKey && !targetPageName && !route) return

  await navigateTo({
    actionId: `${action.actionId}_after_submit`,
    type: 'NAVIGATE',
    label: String(action.label || '保存后返回'),
    targetPageId: targetPageId || null,
    dataBindingKey: action.dataBindingKey,
    payload: {
      targetPageId,
      targetPageKey,
      targetPageName,
      route,
      sourceDataBindingKey: action.payload?.sourceDataBindingKey || action.payload?.source_data_binding_key || action.dataBindingKey || '',
      targetDataBindingKey,
      runtimeDataPatch: snapshotRuntimeDataPatch(),
      rowContext: deepClone(mutatedRow || context.row || buildRuntimeRowFromForms()),
    },
  } as UiContractInteraction, context)
}

function snapshotRuntimeDataPatch(): Record<string, unknown> {
  const patch: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(runtimeDataSession)) {
    patch[key] = deepClone(value)
  }
  return patch
}

function resolveNavigationRowContext(
  action: UiContractInteraction,
  context?: ExecutePayload,
): RuntimeRow | null {
  if (action.payload?.rowContext && typeof action.payload.rowContext === 'object') {
    return deepClone(action.payload.rowContext) as RuntimeRow
  }

  const shouldCarry = Boolean(
    action.payload?.rowContext ||
      action.payload?.carryRowContext ||
      action.payload?.carry_row_context ||
      context?.row,
  )

  if (!shouldCarry || !context?.row) return null
  return deepClone(context.row)
}

async function navigateTo(
  action: UiContractInteraction,
  context?: ExecutePayload,
): Promise<void> {
  const target = resolveA924L2ANavigateTarget(action)

  if (!target) {
    showToast(
      '目标页面未在当前模块页面目录或当前 UI_CONTRACT 的 navigationTargets 中声明。',
      'ERROR',
    )
    return
  }

  const payload: PrototypeNavigatePayload = {
    actionId: action.actionId,
    actionType: String(action.type || ''),
    label: String(action.label || ''),
    targetPageId:
      target.targetPageId ||
      action.targetPageId ||
      null,
    targetPageKey: target.targetPageKey || null,
    targetPageName: target.targetPageName || null,
    route: target.route || null,
    runtimeDataPatch: action.payload?.runtimeDataPatch && typeof action.payload.runtimeDataPatch === 'object'
      ? deepClone(action.payload.runtimeDataPatch) as Record<string, unknown>
      : snapshotRuntimeDataPatch(),
    rowContext: resolveNavigationRowContext(action, context),
  }

  if (!props.navigateRequest) {
    emit('prototypeNavigate', payload)

    showToast(
      `已发出页面切换请求：${target.targetPageName || payload.label}`,
      'INFO',
    )
    return
  }

  try {
    const result = await props.navigateRequest(payload)

    if (!result.ok) {
      showToast(
        result.message ||
        `无法切换到：${target.targetPageName || payload.label}`,
        result.code === 'ARTIFACT_NOT_FOUND' ? 'WARNING' : 'ERROR',
      )
      return
    }

    currentRoute.value =
      result.route ||
      target.route ||
      currentRoute.value

    showToast(
      result.message ||
      `已切换到：${result.targetPageName || target.targetPageName}`,
      'SUCCESS',
    )
  } catch (error) {
    const message =
      error instanceof Error && error.message
        ? error.message
        : '页面切换失败。'
    showToast(message, 'ERROR')
  }
}



function deleteRow(
  action: UiContractInteraction,
  row?: RuntimeRow,
) {
  const bindingKey = resolveRuntimeBindingKey(action)
  if (!row || !bindingKey) return

  const value = runtimeData[bindingKey]
  if (!Array.isArray(value)) return

  const identity = rowIdentity(row)
  runtimeData[bindingKey] = value.filter((item) => {
    if (typeof item !== 'object' || item === null) return true
    const candidate = item as RuntimeRow
    return rowIdentity(candidate) !== identity
  })
  showToast('已从当前模拟列表中删除该记录，刷新页面后恢复原始数据。', 'SUCCESS')
}

function setPageState(
  stateKey: string,
) {
  if (
    props.contract.pageStates.some(
      (state) => state.stateKey === stateKey,
    )
  ) {
    currentPageStateKey.value = stateKey
  }
}

function setDefaultState() {
  const state = props.contract.pageStates.find(
    (item) => item.type === 'DEFAULT',
  )

  if (state) currentPageStateKey.value = state.stateKey
}

async function confirmPendingAction() {
  const action = pendingConfirmAction.value
  const context = pendingConfirmContext.value

  if (!action || !context) {
    cancelConfirm()
    return
  }

  pendingConfirmAction.value = null
  pendingConfirmContext.value = null

  await executeChainedActions(
    action.successActionIds || [],
    context,
    new Set([action.actionId]),
  )
}

function cancelConfirm() {
  pendingConfirmAction.value = null
  pendingConfirmContext.value = null
}

function showToast(
  message: string,
  level: ToastLevel,
) {
  const id = ++toastSequence.value
  toasts.value.push({
    id,
    message,
    level,
  })

  window.setTimeout(() => {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }, 3000)
}

function normalizeToastLevel(
  value: unknown,
): ToastLevel {
  const level = String(value || '').toUpperCase()
  return ['SUCCESS', 'ERROR', 'INFO', 'WARNING'].includes(level)
    ? (level as ToastLevel)
    : 'INFO'
}

function stateFallbackMessage(
  type: UiContractPageState['type'],
): string {
  return {
    LOADING: '正在加载演示数据。',
    EMPTY: '当前没有可展示的数据。',
    ERROR: '演示业务处理失败。',
    SUCCESS: '演示业务处理成功。',
    DISABLED: '当前页面处于不可操作状态。',
    DEFAULT: '',
  }[type]
}

function defaultFieldValue(
  type: string,
): unknown {
  return ['CHECKBOX', 'SWITCH'].includes(type) ? false : ''
}

function clearRecord(
  target: Record<string, unknown>,
) {
  for (const key of Object.keys(target)) {
    delete target[key]
  }
}

function deepClone<T>(
  value: T,
): T {
  return value === undefined
    ? value
    : JSON.parse(JSON.stringify(value)) as T
}

function delay(
  milliseconds: number,
) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, Math.max(0, milliseconds))
  })
}

function valueType(
  value: unknown,
): string {
  if (value === null) return 'null'
  if (Array.isArray(value)) return `${value.length} 条`
  return typeof value
}


function lockRuntimeComponent(
  componentId?: string | null,
  withPulse = false,
  providedTarget?: HTMLElement | null,
) {
  clearPrototypeInspectSelection()

  const normalized = String(componentId || '').trim()

  if (!normalized || typeof document === 'undefined') {
    return
  }

  const target = providedTarget || findRuntimeComponentElement(normalized)

  if (!target) {
    return
  }

  const label = runtimeComponentReadableLabel(normalized, target)

  inspectSelectedElement.value = target
  target.classList.add('a9-component-inspect-locked')
  target.setAttribute('data-a9-selected-label', label)

  if (withPulse) {
    target.classList.add('a9-component-inspect-selected')
    window.setTimeout(() => {
      target.classList.remove('a9-component-inspect-selected')
    }, 1200)
  }
}

function clearPrototypeInspectSelection() {
  if (inspectSelectedElement.value) {
    inspectSelectedElement.value.classList.remove(
      'a9-component-inspect-locked',
      'a9-component-inspect-selected',
    )
    inspectSelectedElement.value.removeAttribute('data-a9-selected-label')
  }

  inspectSelectedElement.value = null
}

function findRuntimeComponentElement(
  componentId: string,
): HTMLElement | null {
  const escaped = escapeCssSelectorValue(componentId)
  const selectableSelector = `[data-ui-selectable="true"][data-ui-component-id="${escaped}"]`
  const componentSelector = `[data-ui-component-id="${escaped}"]`

  return (
    document.querySelector(selectableSelector) ||
    document.querySelector(componentSelector)
  ) as HTMLElement | null
}


function runtimeComponentReadableLabel(
  componentId: string,
  target?: HTMLElement | null,
): string {
  const component = componentMap.value[componentId]
  const dataset = target?.dataset || {}
  const kindLabel = normalizePrototypeElementKindLabel(
    dataset.uiElementKind || dataset.uiComponentType || component?.type || target?.tagName || '',
  )

  const explicitLabel = String(
    dataset.uiElementLabel ||
      dataset.uiLabel ||
      dataset.uiText ||
      dataset.uiFieldLabel ||
      dataset.uiActionLabel ||
      dataset.uiColumnLabel ||
      '',
  ).trim()

  const fieldKey = String(dataset.uiFieldKey || '').trim()
  const actionId = String(dataset.uiActionId || '').trim()
  const columnKey = String(dataset.uiColumnKey || '').trim()
  const tabKey = String(dataset.uiTabKey || '').trim()
  const textKey = String(dataset.uiTextKey || dataset.uiTextRole || '').trim()

  const domLabel = String(
    target?.getAttribute('aria-label') ||
      target?.getAttribute('placeholder') ||
      target?.querySelector('h1,h2,h3,h4,strong,button,label,span')?.textContent?.trim() ||
      target?.textContent?.trim() ||
      '',
  ).replace(/\s+/g, ' ').trim()

  const componentTitle = String(
    component?.title ||
      component?.description ||
      componentId ||
      '',
  ).trim()

  const identity = explicitLabel || domLabel || componentTitle || fieldKey || actionId || columnKey || tabKey || textKey || componentId

  if (fieldKey) {
    return `${kindLabel} · ${identity || fieldKey}`
  }

  if (actionId) {
    return `${kindLabel} · ${identity || actionId}`
  }

  if (columnKey) {
    return `${kindLabel} · ${identity || columnKey}`
  }

  if (tabKey) {
    return `${kindLabel} · ${identity || tabKey}`
  }

  if (textKey) {
    return `${kindLabel} · ${identity || textKey}`
  }

  if (kindLabel && identity && identity !== componentId) {
    return `${kindLabel} · ${identity}`
  }

  return identity || kindLabel || componentId
}

function elementKindLabel(kind: string): string {
  const normalized = String(kind || '').toUpperCase()

  const labels: Record<string, string> = {
    COMPONENT: '组件',
    TEXT: '文案',
    TITLE: '标题',
    DESCRIPTION: '说明',
    FIELD: '字段',
    FIELD_LABEL: '字段名',
    FIELD_INPUT: '输入框',
    FIELD_OPTION: '选项',
    ACTION: '按钮',
    TABLE: '表格',
    TABLE_COLUMN: '表格列',
    TABLE_COLUMN_LABEL: '列名',
    TABLE_CELL: '单元格',
    TABLE_ROW: '表格行',
    TABLE_ACTION: '行操作',
    TAB: '标签页',
    METRIC: '指标卡',
  }

  return labels[normalized] || normalized
}

function focusRuntimeComponent(
  componentId?: string | null,
  providedTarget?: HTMLElement | null,
) {
  const normalized = String(componentId || '').trim()

  if (!normalized || typeof document === 'undefined') {
    return
  }

  const target = providedTarget || findRuntimeComponentElement(normalized)

  if (!target) {
    return
  }

  target.scrollIntoView({
    block: isArtboardMode.value ? 'nearest' : 'center',
    inline: 'nearest',
    behavior: isArtboardMode.value ? 'auto' : 'smooth',
  })

  target.classList.remove('a9-component-focus-pulse')
  void target.offsetWidth
  target.classList.add('a9-component-focus-pulse')

  window.setTimeout(() => {
    target.classList.remove('a9-component-focus-pulse')
  }, 1800)
}

function escapeCssSelectorValue(value: string): string {
  const cssGlobal = (window as Window & { CSS?: { escape?: (value: string) => string } }).CSS

  if (cssGlobal?.escape) {
    return cssGlobal.escape(value)
  }

  return value.replace(/["\\]/g, '\\$&')
}

// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: selection helpers are retained for runtime inspector variants.
void buildPrototypeElementSelection
void elementKindLabel

</script>

<style scoped>
.runtime-shell {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 250px;
  min-height: calc(100vh - 170px);
}

.runtime-sidebar,
.runtime-inspector {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 18px;
  background: #f8fafc;
}

.runtime-sidebar {
  border-right: 1px solid #e2e8f0;
}

.runtime-inspector {
  border-left: 1px solid #e2e8f0;
}

.runtime-sidebar section,
.runtime-inspector section {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2,
h3 {
  margin: 0;
  color: #0f172a;
}

dl {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  margin: 14px 0 0;
  font-size: 12px;
}

dt {
  color: #94a3b8;
}

dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: #334155;
}

.segmented {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: 12px;
}

.segmented button,
.state-button,
.header-actions button {
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  padding: 8px 10px;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
}

.segmented button.active,
.state-button.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.state-button {
  width: 100%;
  margin-top: 7px;
  text-align: left;
}

.helper,
.empty-tip {
  margin: 9px 0 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.6;
}

.coverage-row,
.binding-card,
.data-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  border-radius: 10px;
  padding: 9px;
  background: #f8fafc;
}

.coverage-row strong,
.coverage-row span,
.binding-card strong,
.binding-card span,
.data-card strong,
.data-card span {
  display: block;
  font-size: 11px;
}

.coverage-row span,
.binding-card span,
.data-card span {
  margin-top: 3px;
  color: #64748b;
}

.coverage-row em,
.binding-card em {
  border-radius: 999px;
  padding: 3px 7px;
  background: #e2e8f0;
  color: #475569;
  font-size: 10px;
  font-style: normal;
}

.coverage-row em.full {
  background: #dcfce7;
  color: #166534;
}

.coverage-row em.partial {
  background: #fef3c7;
  color: #92400e;
}

.coverage-row em.missing {
  background: #fee2e2;
  color: #991b1b;
}

.runtime-main {
  min-width: 0;
  background: #f1f5f9;
}

.runtime-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
}

.runtime-header span {
  display: block;
  margin-top: 7px;
  color: #64748b;
  font-size: 13px;
}

.page-canvas {
  display: grid;
  gap: 16px;
  padding: 22px;
}

.layout-centered_form {
  align-content: start;
  max-width: 760px;
  margin: 0 auto;
}

.layout-dashboard,
.layout-custom_grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.state-banner {
  display: grid;
  gap: 4px;
  margin: 18px 22px 0;
  border-radius: 12px;
  padding: 13px 14px;
  background: #eff6ff;
  color: #1e40af;
}

.state-banner.error {
  background: #fef2f2;
  color: #991b1b;
}

.state-banner.success {
  background: #f0fdf4;
  color: #166534;
}

.state-banner.empty,
.state-banner.disabled {
  background: #f8fafc;
  color: #475569;
}

.runtime-shell--artboard {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--runtime-page-bg);
}

.artboard-runtime-main {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.artboard-state-banner {
  position: absolute;
  top: 28px;
  left: 50%;
  z-index: 50;
  width: min(560px, calc(100% - 96px));
  margin: 0;
  transform: translateX(-50%);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.16);
}

.assumption-list {
  display: grid;
  gap: 7px;
  margin: 10px 0 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.toast-stack {
  position: fixed;
  top: 90px;
  right: 28px;
  z-index: 1500;
  display: grid;
  gap: 8px;
}

.toast {
  min-width: 240px;
  border-radius: 12px;
  padding: 11px 13px;
  background: #334155;
  color: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.2);
}

.toast.success {
  background: #15803d;
}

.toast.error {
  background: #b91c1c;
}

.toast.warning {
  background: #a16207;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.42);
}

.modal-card,
.confirm-card {
  width: min(680px, 94vw);
  max-height: 88vh;
  overflow: auto;
  border-radius: 20px;
  padding: 18px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.25);
}

.runtime-modal-content {
  display: grid;
  gap: 14px;
}

.runtime-modal-content :deep(.runtime-node.modal),
.runtime-modal-content :deep(.runtime-node.dialog),
.runtime-modal-content :deep(.runtime-node.confirm-modal) {
  border: 0;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.modal-card > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.modal-card > header button {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 24px;
  cursor: pointer;
}

.confirm-card {
  width: min(460px, 94vw);
}

.confirm-card h3 {
  margin-top: 4px;
}

.confirm-card p {
  color: #475569;
  line-height: 1.7;
}

.confirm-card footer {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 18px;
}

.ghost-button,
.primary-button {
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  padding: 9px 14px;
  background: #ffffff;
  cursor: pointer;
}

.primary-button {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

@media (max-width: 1180px) {
  .runtime-shell {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .runtime-inspector {
    grid-column: 1 / -1;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    border-top: 1px solid #e2e8f0;
    border-left: none;
  }
}

@media (max-width: 760px) {
  .runtime-shell {
    grid-template-columns: 1fr;
  }

  .runtime-sidebar,
  .runtime-inspector {
    grid-template-columns: 1fr;
    border: none;
  }

  .runtime-header {
    display: grid;
  }

  .layout-dashboard,
  .layout-custom_grid {
    grid-template-columns: 1fr;
  }
}

.runtime-shell :deep(.a9-component-focus-pulse) {
  position: relative;
  z-index: 8;
  outline: 3px solid rgba(37, 99, 235, 0.9);
  outline-offset: 4px;
  box-shadow:
    0 0 0 8px rgba(37, 99, 235, 0.16),
    0 18px 42px rgba(37, 99, 235, 0.22);
  animation: a9ComponentFocusPulse 1.2s ease-in-out 1;
}

@keyframes a9ComponentFocusPulse {
  0% {
    transform: scale(1);
  }
  35% {
    transform: scale(1.025);
  }
  100% {
    transform: scale(1);
  }
}


.runtime-shell--inspect {
  cursor: crosshair;
  user-select: none;
}

.runtime-shell--inspect :deep([data-ui-selectable="true"]),
.runtime-shell--inspect :deep([data-ui-component-id]) {
  cursor: crosshair !important;
}

.runtime-shell :deep(.a9-component-inspect-hover) {
  position: relative;
  z-index: 12;
  outline: 2px solid rgba(14, 165, 233, 0.98);
  outline-offset: 4px;
  box-shadow:
    0 0 0 7px rgba(14, 165, 233, 0.13),
    0 20px 48px rgba(14, 165, 233, 0.22);
}

.runtime-shell :deep(.a9-component-inspect-selected) {
  position: relative;
  z-index: 14;
  outline: 3px solid rgba(37, 99, 235, 0.98);
  outline-offset: 5px;
  box-shadow:
    0 0 0 10px rgba(37, 99, 235, 0.16),
    0 26px 64px rgba(37, 99, 235, 0.24);
  animation: a9InspectSelectedPulse 1.05s ease-in-out 1;
}

@keyframes a9InspectSelectedPulse {
  0% { transform: scale(1); }
  42% { transform: scale(1.022); }
  100% { transform: scale(1); }
}


.runtime-shell :deep(.a9-component-inspect-locked) {
  position: relative;
  z-index: 18;
  outline: 3px solid rgba(37, 99, 235, 0.98);
  outline-offset: 6px;
  box-shadow:
    0 0 0 11px rgba(37, 99, 235, 0.15),
    0 30px 72px rgba(37, 99, 235, 0.28) !important;
}

.runtime-shell :deep(.a9-component-inspect-locked)::after {
  content: "已选中 · " attr(data-a9-selected-label);
  position: absolute;
  left: 0;
  top: -38px;
  z-index: 30;
  max-width: min(460px, 80vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid rgba(37, 99, 235, 0.28);
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(37, 99, 235, 0.96);
  color: #ffffff;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: 0.01em;
  box-shadow: 0 14px 34px rgba(37, 99, 235, 0.30);
  pointer-events: none;
}

.runtime-shell :deep(.a9-component-inspect-locked)::before {
  content: "";
  position: absolute;
  left: 18px;
  top: -9px;
  z-index: 29;
  width: 14px;
  height: 14px;
  background: rgba(37, 99, 235, 0.96);
  transform: rotate(45deg);
  pointer-events: none;
}


/* A9.6.8 element-level hit precision visual rules BEGIN */
.runtime-shell--inspect :deep([data-ui-selectable="true"]),
.runtime-shell--inspect :deep([data-ui-component-id]) {
  cursor: crosshair !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-hover) {
  position: relative;
  z-index: 24;
  outline: 2px solid rgba(14, 165, 233, 0.98);
  outline-offset: 3px;
  box-shadow:
    0 0 0 6px rgba(14, 165, 233, 0.13),
    0 16px 42px rgba(14, 165, 233, 0.20) !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-selected) {
  position: relative;
  z-index: 28;
  outline: 3px solid rgba(37, 99, 235, 0.98);
  outline-offset: 4px;
  box-shadow:
    0 0 0 9px rgba(37, 99, 235, 0.16),
    0 24px 58px rgba(37, 99, 235, 0.24) !important;
  animation: a9InspectSelectedPulse 1.05s ease-in-out 1;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked) {
  position: relative;
  z-index: 32;
  outline: 3px solid rgba(37, 99, 235, 0.98);
  outline-offset: 5px;
  box-shadow:
    0 0 0 10px rgba(37, 99, 235, 0.15),
    0 28px 66px rgba(37, 99, 235, 0.28) !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::after {
  content: "已选中 · " attr(data-a9-selected-label);
  position: absolute;
  left: 0;
  top: -36px;
  z-index: 60;
  max-width: min(520px, 82vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid rgba(37, 99, 235, 0.28);
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(37, 99, 235, 0.96);
  color: #ffffff;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: 0.01em;
  box-shadow: 0 14px 34px rgba(37, 99, 235, 0.30);
  pointer-events: none;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::before {
  content: "";
  position: absolute;
  left: 18px;
  top: -8px;
  z-index: 59;
  width: 14px;
  height: 14px;
  background: rgba(37, 99, 235, 0.96);
  transform: rotate(45deg);
  pointer-events: none;
}
/* A9.6.8 element-level hit precision visual rules END */


.a9-inspect-toolbar {
  position: absolute;
  top: 14px;
  left: 50%;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: min(760px, calc(100% - 40px));
  transform: translateX(-50%);
  border: 1px solid rgba(37, 99, 235, 0.24);
  border-radius: 18px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(18px);
  pointer-events: auto;
}

.a9-inspect-toolbar-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.a9-inspect-toolbar-main strong {
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 950;
}

.a9-inspect-toolbar-main span {
  overflow: hidden;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-inspect-toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
}

.a9-inspect-toolbar-actions button {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 7px 11px;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.a9-inspect-toolbar-actions button.active {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
  box-shadow: 0 10px 26px rgba(37, 99, 235, 0.22);
}

.a9-inspect-toolbar-actions button.danger {
  border-color: #fecaca;
  color: #b91c1c;
}

@media (max-width: 820px) {
  .a9-inspect-toolbar {
    display: grid;
  }

  .a9-inspect-toolbar-actions {
    flex-wrap: wrap;
  }
}

/* A9_7_CORE_FIDELITY_PATCH: canvas-first precise inspect feedback. */
.runtime-shell--inspect :deep([data-ui-selectable="true"]) {
  cursor: crosshair !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-hover) {
  position: relative;
  z-index: 120 !important;
  outline: 2px solid rgba(14, 165, 233, 0.98) !important;
  outline-offset: 3px !important;
  box-shadow:
    0 0 0 6px rgba(14, 165, 233, 0.12),
    0 14px 34px rgba(14, 165, 233, 0.22) !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked) {
  position: relative;
  z-index: 160 !important;
  outline: 3px solid rgba(37, 99, 235, 0.98) !important;
  outline-offset: 4px !important;
  box-shadow:
    0 0 0 8px rgba(37, 99, 235, 0.17),
    0 18px 46px rgba(37, 99, 235, 0.28) !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::after {
  content: "已选中 · " attr(data-a9-selected-label);
  position: absolute;
  left: 0;
  top: -34px;
  z-index: 200;
  max-width: min(460px, 80vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(37, 99, 235, 0.98);
  color: #ffffff;
  font-size: 12px;
  font-weight: 950;
  pointer-events: none;
}

/* A9.9.2 Preview Canvas Scaling: render as a real web page, then let the preview shell scale it. */
.a9-runtime-design-canvas {
  --a9-real-page-width: 1440px;
  --a9-real-page-min-height: 900px;
  --a9-real-page-horizontal-padding: 40px;
  --a9-real-page-vertical-padding: 32px;
  align-items: stretch;
  background: #f3f6fb;
  overflow: visible;
}

.a9-runtime-design-canvas .runtime-screen {
  flex: 0 0 var(--a9-real-page-width);
  width: var(--a9-real-page-width);
  min-width: var(--a9-real-page-width);
  max-width: none;
  min-height: var(--a9-real-page-min-height);
  margin: 0 auto;
  overflow: visible;
  background: #f3f6fb;
  box-sizing: border-box;
}

.a9-runtime-design-canvas .screen-header {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}

.a9-runtime-design-canvas .page-canvas {
  width: 100%;
  min-width: 0;
  max-width: none;
  min-height: var(--a9-real-page-min-height);
  padding: var(--a9-real-page-vertical-padding) var(--a9-real-page-horizontal-padding) 88px;
  box-sizing: border-box;
  background: #f3f6fb;
  overflow: visible;
}

.a9-runtime-design-canvas .page-canvas.layout-table_list,
.a9-runtime-design-canvas .page-canvas.layout-TABLE_LIST,
.a9-runtime-design-canvas .page-canvas.layout-table-list,
.a9-runtime-design-canvas .page-canvas.layout-form_detail,
.a9-runtime-design-canvas .page-canvas.layout-FORM_DETAIL,
.a9-runtime-design-canvas .page-canvas.layout-form-detail,
.a9-runtime-design-canvas .page-canvas.layout-form,
.a9-runtime-design-canvas .page-canvas.layout-FORM,
.a9-runtime-design-canvas .page-canvas.layout-default {
  display: block;
}

.a9-runtime-design-canvas .page-canvas > * {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}

.a9-runtime-design-canvas .runtime-component,
.a9-runtime-design-canvas .runtime-node,
.a9-runtime-design-canvas .component-card,
.a9-runtime-design-canvas .node-card,
.a9-runtime-design-canvas .component-form,
.a9-runtime-design-canvas .node-form,
.a9-runtime-design-canvas .component-table,
.a9-runtime-design-canvas .node-table {
  max-width: none;
  box-sizing: border-box;
}

.a9-runtime-design-canvas .component-form,
.a9-runtime-design-canvas .node-form {
  width: 100%;
}

.a9-runtime-design-canvas .form-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 24px;
  align-items: start;
}

.a9-runtime-design-canvas .component-table,
.a9-runtime-design-canvas .node-table,
.a9-runtime-design-canvas table {
  width: 100%;
}

.a9-runtime-design-canvas .component-actions,
.a9-runtime-design-canvas .node-actions,
.a9-runtime-design-canvas .actions-row {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* A9.10.14.4.3.13.2.12.1_TABLE_OVERFLOW_ACTION_COLUMN_FIX
   Page-level override: earlier list-page CSS forced table min-width:100% and hid
   overflow in cards, which made action buttons squeeze columns instead of
   producing a horizontal scrollbar. This block must stay near the end. */
.business-page--list :deep(.table-wrap),
.business-page--list :deep(.table-wrapper),
.business-page--list :deep(.table-scroll),
.business-page--list :deep(.runtime-table) {
  width: 100% !important;
  max-width: 100% !important;
  overflow-x: auto !important;
  overflow-y: visible !important;
  padding-bottom: 8px !important;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
}

.business-page--list :deep(.node-card:has(table)),
.business-page--list :deep(.component-card:has(table)) {
  overflow: visible !important;
}

.business-page--list :deep(.table-wrap table),
.business-page--list :deep(.runtime-table table),
.business-page--list :deep(table) {
  width: max-content !important;
  min-width: max(100%, var(--a9-runtime-table-min-width, 1280px)) !important;
  max-width: none !important;
  table-layout: auto !important;
}

.business-page--list :deep(.table-wrap th),
.business-page--list :deep(.table-wrap td),
.business-page--list :deep(table th),
.business-page--list :deep(table td) {
  white-space: nowrap !important;
}

.business-page--list :deep(th.row-action-header),
.business-page--list :deep(td.row-actions) {
  width: var(--a9-runtime-action-column-width, 320px) !important;
  min-width: var(--a9-runtime-action-column-width, 320px) !important;
  max-width: none !important;
  background: rgba(255, 255, 255, 0.98) !important;
}

.business-page--list :deep(th.row-action-header) {
  position: sticky !important;
  right: 0 !important;
  z-index: 4 !important;
  box-shadow: -10px 0 18px rgba(15, 23, 42, 0.06) !important;
}

.business-page--list :deep(td.row-actions) {
  display: table-cell !important;
  position: sticky !important;
  right: 0 !important;
  z-index: 3 !important;
  text-align: left !important;
  box-shadow: -10px 0 18px rgba(15, 23, 42, 0.04) !important;
}

.business-page--list :deep(td.row-actions button) {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  white-space: nowrap !important;
  margin-right: 8px !important;
  padding: 6px 10px !important;
  min-width: 72px !important;
}

.business-page--list :deep(td.row-actions button:last-child) {
  margin-right: 0 !important;
}

/* A9.10.14.4.3.15.4.3_RUNTIME_INDUSTRY_STYLE_RENDER */
.runtime-shell {
  background: var(--runtime-page-bg);
}

.runtime-main,
.a9-runtime-design-canvas,
.a9-runtime-design-canvas .runtime-screen,
.a9-runtime-design-canvas .page-canvas {
  background: var(--runtime-page-bg);
}

.runtime-header,
.runtime-sidebar section,
.runtime-inspector section,
.modal-card,
.confirm-card {
  border-color: var(--runtime-border);
  border-radius: var(--runtime-radius);
  background: var(--runtime-surface);
  box-shadow: var(--runtime-card-shadow);
}

.segmented button,
.state-button,
.header-actions button,
.ghost-button,
.primary-button {
  border-radius: var(--runtime-button-radius);
}

.segmented button.active,
.state-button.active,
.primary-button {
  border-color: var(--runtime-primary);
  background: var(--runtime-primary);
  color: #ffffff;
}

.eyebrow {
  color: var(--runtime-primary);
}

.state-banner.success {
  background: var(--runtime-status-success-bg);
  color: var(--runtime-status-success-text);
}

.state-banner.warning {
  background: var(--runtime-status-warning-bg);
  color: var(--runtime-status-warning-text);
}

.state-banner.error {
  background: var(--runtime-status-error-bg);
  color: var(--runtime-status-error-text);
}

.runtime-industry-warehouse,
.runtime-industry-manufacturing {
  --a9-runtime-table-min-width: 1360px;
}

.runtime-industry-commerce {
  --a9-runtime-table-min-width: 1120px;
}

.runtime-industry-work-order,
.runtime-industry-foreign-trade,
.runtime-industry-crm {
  --a9-runtime-action-column-width: 340px;
}

/* A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM */
.runtime-shell {
  --a9-vq-header-radius: calc(var(--runtime-radius) + 8px);
  --a9-vq-panel-border: color-mix(in srgb, var(--runtime-border) 86%, transparent);
  --a9-vq-soft-surface: color-mix(in srgb, var(--runtime-surface) 88%, var(--runtime-page-bg));
  --a9-vq-hero-glow: color-mix(in srgb, var(--runtime-primary) 13%, transparent);
  background:
    radial-gradient(circle at 18% 8%, var(--a9-vq-hero-glow), transparent 32%),
    var(--runtime-page-gradient);
  color: var(--runtime-text-main);
}

.runtime-shell--artboard .artboard-runtime-main {
  padding: clamp(18px, 2.4vw, 34px);
}

.runtime-shell :deep(.business-page),
.runtime-shell :deep(.auth-page) {
  min-height: 100%;
  background: transparent;
}

.runtime-shell :deep(.business-header) {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--a9-vq-panel-border);
  border-radius: var(--a9-vq-header-radius);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--runtime-surface) 94%, var(--runtime-primary)) 0%, var(--runtime-surface) 56%, color-mix(in srgb, var(--runtime-page-bg) 70%, #ffffff) 100%);
  box-shadow: var(--runtime-card-shadow);
}

.runtime-shell :deep(.business-header)::after {
  content: "";
  position: absolute;
  inset: auto -80px -110px auto;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--runtime-primary) 10%, transparent);
  pointer-events: none;
}

.runtime-shell :deep(.business-header h1),
.runtime-shell :deep(.auth-visual h1) {
  letter-spacing: -0.045em;
}

.runtime-shell :deep(.runtime-node),
.runtime-shell :deep(.auth-card),
.runtime-shell :deep(.modal-card),
.runtime-shell :deep(.confirm-card) {
  border: 1px solid var(--a9-vq-panel-border);
  background: var(--a9-vq-soft-surface);
  backdrop-filter: blur(10px);
}

.runtime-shell :deep(.runtime-node:hover) {
  border-color: color-mix(in srgb, var(--runtime-primary) 24%, var(--runtime-border));
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.10);
}

.runtime-shell :deep(button:not(:disabled)) {
  transition: transform .16s ease, box-shadow .16s ease, background .16s ease, border-color .16s ease;
}

.runtime-shell :deep(button:not(:disabled):hover) {
  transform: translateY(-1px);
}

.runtime-shell :deep(input:focus),
.runtime-shell :deep(select:focus),
.runtime-shell :deep(textarea:focus) {
  border-color: color-mix(in srgb, var(--runtime-primary) 62%, var(--runtime-border));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--runtime-primary) 12%, transparent);
}


/* A9.10.14.4.3.15.6.24G4_SELECTION_OVERFLOW_FIX */
.runtime-shell--artboard,
.runtime-shell--artboard .artboard-runtime-main {
  overflow-x: hidden !important;
}

.runtime-shell--artboard :deep(.pattern-runtime),
.runtime-shell--artboard :deep(.business-page),
.runtime-shell--artboard :deep(.business-body) {
  overflow-x: hidden !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
}

.runtime-shell--artboard :deep([data-ui-selectable]) {
  box-sizing: border-box !important;
  min-width: 0 !important;
}


/* A9.10.14.4.3.15.6.24G5_SELECTION_SCROLL_LOCK
   The inline selected label used ::before/::after on the selected element.
   On right-edge buttons it expands horizontal scroll and shifts the whole canvas.
   The right Prototype Copilot panel already shows the selected element, so keep
   the blue outline but suppress the layout-affecting inline badge. */
.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::after,
.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::before,
.runtime-shell :deep(.a9-component-inspect-locked)::after,
.runtime-shell :deep(.a9-component-inspect-locked)::before {
  content: none !important;
  display: none !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked),
.runtime-shell :deep(.a9-component-inspect-locked) {
  outline-offset: -2px !important;
  box-sizing: border-box !important;
}


/* A9.10.14.4.3.15.6.24G6_SELECTION_BADGE_STABLE
   Keep the selected badge, but prevent it from enlarging horizontal scroll.
   The badge expands to the left of the selected element, and the selected
   shadow is changed to non-layout-expanding outline/inset shadow. */
.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked),
.runtime-shell :deep(.a9-component-inspect-locked) {
  position: relative !important;
  outline: 2px solid rgba(37, 99, 235, 0.98) !important;
  outline-offset: -2px !important;
  box-shadow:
    inset 0 0 0 2px rgba(37, 99, 235, 0.20),
    0 0 0 3px rgba(37, 99, 235, 0.12) !important;
  box-sizing: border-box !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-selected),
.runtime-shell :deep(.a9-component-inspect-selected) {
  box-shadow:
    inset 0 0 0 2px rgba(37, 99, 235, 0.20),
    0 0 0 3px rgba(37, 99, 235, 0.12) !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::after,
.runtime-shell :deep(.a9-component-inspect-locked)::after {
  content: "已选中 · " attr(data-a9-selected-label) !important;
  display: block !important;
  position: absolute !important;
  left: auto !important;
  right: 0 !important;
  top: -34px !important;
  z-index: 2147483500 !important;
  max-width: min(280px, 72vw) !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
  border: 1px solid rgba(37, 99, 235, 0.28) !important;
  border-radius: 999px !important;
  padding: 6px 10px !important;
  background: rgba(37, 99, 235, 0.96) !important;
  color: #ffffff !important;
  font-size: 12px !important;
  font-weight: 900 !important;
  line-height: 1.2 !important;
  letter-spacing: 0.01em !important;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22) !important;
  pointer-events: none !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::before,
.runtime-shell :deep(.a9-component-inspect-locked)::before {
  content: "" !important;
  display: block !important;
  position: absolute !important;
  left: auto !important;
  right: 16px !important;
  top: -7px !important;
  z-index: 2147483499 !important;
  width: 12px !important;
  height: 12px !important;
  background: rgba(37, 99, 235, 0.96) !important;
  transform: rotate(45deg) !important;
  pointer-events: none !important;
}

.runtime-shell--artboard,
.runtime-shell--artboard .artboard-runtime-main,
.runtime-shell--artboard :deep(.pattern-runtime),
.runtime-shell--artboard :deep(.business-page),
.runtime-shell--artboard :deep(.business-body) {
  overflow-x: hidden !important;
}


/* A9.10.14.4.3.15.6.24G7_ADMIN_SELECTION_SCROLL_ROOTFIX
   Root cause fix:
   older list-page CSS forced table width:max-content and min-width:1280px.
   Admin renderer tables must stay inside the visible canvas, otherwise selecting
   right-side actions changes horizontal scrollLeft and shifts the page. */
.runtime-shell :deep(.a9-admin-table),
.runtime-shell :deep(.a9-admin-table .el-table__inner-wrapper),
.runtime-shell :deep(.a9-admin-table .el-table__header-wrapper),
.runtime-shell :deep(.a9-admin-table .el-table__body-wrapper),
.runtime-shell :deep(.a9-admin-table .el-scrollbar),
.runtime-shell :deep(.a9-admin-table .el-scrollbar__wrap),
.runtime-shell :deep(.a9-admin-table .el-scrollbar__view) {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  overflow-x: hidden !important;
  box-sizing: border-box !important;
}

.runtime-shell :deep(.a9-admin-table table),
.runtime-shell :deep(.business-page--list .a9-admin-table table),
.runtime-shell :deep(.business-page--list .a9-admin-table .el-table__body),
.runtime-shell :deep(.business-page--list .a9-admin-table .el-table__header) {
  width: 100% !important;
  min-width: 100% !important;
  max-width: 100% !important;
  table-layout: fixed !important;
}

.runtime-shell :deep(.a9-admin-table .el-table__fixed-right),
.runtime-shell :deep(.a9-admin-table .el-table__fixed) {
  max-width: 100% !important;
}

.runtime-shell,
.runtime-shell--artboard,
.runtime-shell--artboard .artboard-runtime-main,
.runtime-shell :deep(.pattern-runtime),
.runtime-shell :deep(.business-page),
.runtime-shell :deep(.business-body),
.runtime-shell :deep(.a9-admin-page) {
  overflow-x: hidden !important;
  scroll-behavior: auto !important;
}

/* Keep selected badge visible, but make it expand left inside the canvas. */
.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::after,
.runtime-shell :deep(.a9-component-inspect-locked)::after {
  content: "已选中 · " attr(data-a9-selected-label) !important;
  display: block !important;
  left: auto !important;
  right: 0 !important;
  max-width: min(260px, 70vw) !important;
  transform: none !important;
}

.runtime-shell :deep([data-ui-selectable="true"].a9-component-inspect-locked)::before,
.runtime-shell :deep(.a9-component-inspect-locked)::before {
  left: auto !important;
  right: 16px !important;
}


/* A9.10.14.4.3.15.6.24J7B_TABLE_HIT_TEST_MODEL
   TABLE_ROW is a synthesized <tr>; pseudo badge on <tr> is unreliable.
   Keep row outline through td shadows and use right-side selected panel for label. */
.runtime-shell :deep(tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-hover > td) {
  box-shadow: inset 0 0 0 1px rgba(14, 165, 233, 0.92) !important;
}

.runtime-shell :deep(tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked > td) {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.95) !important;
}

.runtime-shell :deep(tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked)::after,
.runtime-shell :deep(tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}

.runtime-shell :deep([data-ui-element-kind="TABLE_COLUMN_LABEL"].a9-component-inspect-locked)::after,
.runtime-shell :deep([data-ui-element-kind="TABLE_COLUMN_LABEL"].a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}

</style>
<!-- A9.10.14.4.3.15.6.24L2A_RUNTIME_DEMO_ACTION_FALLBACK: runtime demo actions can execute from actionId even when interactions are incomplete. -->
