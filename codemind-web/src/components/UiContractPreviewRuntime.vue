<template>
  <div
    class="runtime-shell"
    :class="[
      `runtime-pattern-${runtimeContext.pattern.toLowerCase()}`,
      `runtime-theme-${runtimeContext.theme.key}`,
      {
        'runtime-shell--artboard': isArtboardMode,
      },
    ]"
    :style="themeVars"
  >
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
          :class="`layout-${contract.page.layout.type.toLowerCase()}`"
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

        <UiContractRuntimeNode
          :component="activeModal"
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
  }>(),
  {
    displayMode: 'workbench',
    projectThemeProfile: null,
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

const scenarioMode = ref<ScenarioMode>('SUCCESS')
const currentPageStateKey = ref('')
const currentRoute = ref('')
const activeModalId = ref<string | null>(null)
const pendingConfirmAction = ref<UiContractInteraction | null>(null)
const pendingConfirmContext = ref<ExecutePayload | null>(null)
const toastSequence = ref(0)

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

const visibleComponentIds = computed(() => {
  return currentState.value?.visibleComponentIds || []
})

const disabledComponentIds = computed(() => {
  return currentState.value?.disabledComponentIds || []
})

const canvasComponents = computed(() => {
  return props.contract.page.componentTree.filter(
    (component) => component.type !== 'MODAL',
  )
})

const activeModal = computed<UiContractComponent | null>(() => {
  if (!activeModalId.value) return null
  const component = componentMap.value[activeModalId.value]
  return component?.type === 'MODAL' ? component : null
})

watch(
  () => props.contract,
  () => resetRuntime(),
  {
    immediate: true,
    deep: true,
  },
)

function resetRuntime() {
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

  switch (action.type) {
    case 'SET_VALUE':
      applySetValue(action)
      break

    case 'RESET_FORM':
      resetForm(action.targetId)
      showToast(action.label || '表单已重置', 'INFO')
      break

    case 'SUBMIT_FORM':
      if (!validateForm(action.targetId)) {
        showToast('请先修正表单校验错误。', 'ERROR')
        return
      }
      await runScenario(action, context, visited)
      break

    case 'OPEN_MODAL':
      openModal(action.targetId)
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
      navigateTo(action)
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

function openModal(
  targetId?: string | null,
) {
  if (!targetId || componentMap.value[targetId]?.type !== 'MODAL') {
    showToast('目标弹窗不存在。', 'ERROR')
    return
  }

  activeModalId.value = targetId
}

function closeModal() {
  activeModalId.value = null
}

function navigateTo(
  action: UiContractInteraction,
) {
  const target = props.contract.navigationTargets.find(
    (item) => item.targetPageId === action.targetPageId,
  )

  if (!target) {
    showToast('目标页面未在契约中声明。', 'ERROR')
    return
  }

  currentRoute.value = target.route
  showToast(`已模拟跳转到：${target.targetPageName}`, 'INFO')
}

function deleteRow(
  action: UiContractInteraction,
  row?: RuntimeRow,
) {
  if (!row || !action.dataBindingKey) return

  const value = runtimeData[action.dataBindingKey]
  if (!Array.isArray(value)) return

  const identity = row.id ?? row.key ?? row.code
  runtimeData[action.dataBindingKey] = value.filter((item) => {
    if (typeof item !== 'object' || item === null) return true
    const candidate = item as RuntimeRow
    return (candidate.id ?? candidate.key ?? candidate.code) !== identity
  })
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
</style>