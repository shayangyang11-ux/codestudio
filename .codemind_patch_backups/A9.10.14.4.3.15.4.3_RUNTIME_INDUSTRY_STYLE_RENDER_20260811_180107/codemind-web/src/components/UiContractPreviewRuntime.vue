<template>
  <div
    class="runtime-shell"
    @mousemove.capture="handlePrototypeInspectPointerMove"
    @click.capture="handlePrototypeInspectClick"
    @mouseleave="clearPrototypeInspectHover"
    :class="[
      `runtime-pattern-${runtimeContext.pattern.toLowerCase()}`,
      `runtime-theme-${runtimeContext.theme.key}`,
      {
        'runtime-shell--artboard': isArtboardMode,
        'runtime-shell--inspect': props.inspectComponentMode,
      },
    ]"
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
            <button @click="resetRuntime">
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
import UiContractRuntimeNode from './UiContractRuntimeNode.vue'
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
}

interface PrototypeNavigatePayload {
  actionId: string
  actionType: string
  label: string
  targetPageId?: string | null
  targetPageKey?: string | null
  targetPageName?: string | null
  route?: string | null
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
    focusRuntimeComponent(String(componentId || ''), target)
  },
  { immediate: true },
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
const activeTabs = reactive<Record<string, string>>({})
const toasts = ref<RuntimeToast[]>([])

const isArtboardMode = computed(() => props.displayMode === 'artboard')
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


function handlePrototypeInspectClick(event: MouseEvent) {
  if (!props.inspectComponentMode) {
    return
  }

  const target = resolvePrototypeInspectTarget(event)

  if (!target) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()

  clearPrototypeInspectHover()
  lockRuntimeComponent(target.dataset.uiComponentId || '', true, target)

  emit('selectComponent', buildPrototypeElementSelectionPayload(target) as any)
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

  const preciseAttrs: Array<[string, string]> = [
    ['textKey', 'data-ui-text-key'],
    ['textRole', 'data-ui-text-role'],
    ['fieldKey', 'data-ui-field-key'],
    ['actionId', 'data-ui-action-id'],
    ['columnKey', 'data-ui-column-key'],
    ['tabKey', 'data-ui-tab-key'],
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

  const directTarget = resolveDirectPrototypePickTarget(rawTarget)
  if (directTarget) {
    return directTarget
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
  if (target.dataset.uiColumnKey) {
    return 'TABLE_COLUMN'
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
    TABLE_CELL: '单元格',
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

function resetRuntime() {
  clearPrototypeInspectSelection()
  clearRecord(formValues)
  clearRecord(initialFormValues)
  clearRecord(fieldErrors)
  clearRecord(runtimeData)
  clearRecord(activeTabs)

  Object.assign(runtimeData, deepClone(props.contract.mockData || {}))

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

  const action = props.contract.interactions.find(
    (item) => item.actionId === actionId,
  )

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
        applyRuntimeMockMutation(action, context)
        if (action.payload?.closeModalOnSuccess !== false) {
          closeModal()
        }
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
      await navigateTo(action)
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
    showToast(action.label || 'Mock 操作已完成。', 'SUCCESS')
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
      (scenario.outcome === 'SUCCESS' ? 'Mock 操作成功。' : 'Mock 操作失败。'),
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
) {
  const effect = String(
    action.payload?.runtimeEffect ||
      action.payload?.mockEffect ||
      '',
  ).toUpperCase()

  if (!effect) return

  if (effect === 'ADD_ROW') {
    addRuntimeRow(action)
    return
  }

  if (effect === 'UPDATE_ROW') {
    updateRuntimeRow(action, context.row)
    return
  }
}

function addRuntimeRow(
  action: UiContractInteraction,
) {
  const bindingKey = resolveRuntimeBindingKey(action)
  if (!bindingKey) return

  const current = runtimeData[bindingKey]
  const rows = Array.isArray(current) ? [...current] : []
  const nextRow = buildRuntimeRowFromForms()
  nextRow.id = nextRow.id || nextRow.key || `mock_${Date.now()}`
  nextRow.key = nextRow.key || nextRow.id
  rows.unshift(nextRow)
  runtimeData[bindingKey] = rows
  showToast(String(action.payload?.message || '已新增一条模拟记录，刷新页面后恢复原始数据。'), 'SUCCESS')
}

function updateRuntimeRow(
  action: UiContractInteraction,
  row?: RuntimeRow,
) {
  const bindingKey = resolveRuntimeBindingKey(action)
  if (!bindingKey || !row) return

  const current = runtimeData[bindingKey]
  if (!Array.isArray(current)) return

  const identity = rowIdentity(row)
  const nextValues = buildRuntimeRowFromForms()
  runtimeData[bindingKey] = current.map((item) => {
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      return item
    }
    const candidate = item as RuntimeRow
    return rowIdentity(candidate) === identity
      ? { ...candidate, ...nextValues, id: candidate.id || nextValues.id, key: candidate.key || nextValues.key }
      : item
  })
  showToast(String(action.payload?.message || '已更新当前模拟记录，刷新页面后恢复原始数据。'), 'SUCCESS')
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
  return String(
    action.payload?.dataBindingKey ||
      action.dataBindingKey ||
      '',
  ).trim()
}

function rowIdentity(
  row: RuntimeRow,
): unknown {
  return row.id ?? row.key ?? row.code ?? row.order_no ?? row.inbound_order_no ?? JSON.stringify(row)
}

async function navigateTo(
  action: UiContractInteraction,
): Promise<void> {
  const target = props.contract.navigationTargets.find(
    (item) =>
      String(item.targetPageId || '') ===
      String(action.targetPageId || ''),
  )

  if (!target) {
    showToast(
      '目标页面未在当前 UI_CONTRACT 的 navigationTargets 中声明。',
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
  }

  if (!props.navigateRequest) {
    emit('prototypeNavigate', payload)

    showToast(
      `已发出页面切换请求：${target.targetPageName}`,
      'INFO',
    )
    return
  }

  try {
    const result = await props.navigateRequest(payload)

    if (!result.ok) {
      showToast(
        result.message ||
        `无法切换到：${target.targetPageName}`,
        'ERROR',
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
        : '原型页面切换发生未知错误。'

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
    LOADING: '正在加载模拟数据。',
    EMPTY: '当前没有可展示的数据。',
    ERROR: '模拟业务处理失败。',
    SUCCESS: '模拟业务处理成功。',
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
    TABLE_CELL: '单元格',
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
    block: 'center',
    inline: 'center',
    behavior: 'smooth',
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
</style>