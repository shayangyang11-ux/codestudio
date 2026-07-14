<template>
  <div class="ui-runtime-shell">
    <aside class="runtime-sidebar">
      <div class="sidebar-block">
        <p class="eyebrow">Mock Pages</p>
        <h3>页面列表</h3>

        <button
          v-for="page in pages"
          :key="page.pageId"
          class="page-item"
          :class="{ active: currentPageId === page.pageId }"
          @click="selectPage(page.pageId)"
        >
          <strong>{{ page.pageName || page.title || page.pageId }}</strong>
          <span>{{ routeOf(page.pageId) }}</span>
        </button>
      </div>

      <div class="sidebar-block">
        <p class="eyebrow">Contract Rules</p>
        <h3>开发约束</h3>

        <dl class="rule-list">
          <template
            v-for="(value, key) in contract.developmentRules || {}"
            :key="String(key)"
          >
            <dt>{{ formatRuleKey(String(key)) }}</dt>
            <dd>{{ formatRuleValue(value) }}</dd>
          </template>
        </dl>
      </div>
    </aside>

    <main class="runtime-screen">
      <header class="screen-header">
        <div>
          <p class="eyebrow">Mock Preview Runtime</p>
          <h2>
            {{
              currentPage?.pageName ||
              currentPage?.title ||
              currentPage?.pageId ||
              '页面预览'
            }}
          </h2>
          <span>
            {{
              currentPage?.description ||
              '当前页面由 UI_CONTRACT 渲染，所有数据和动作均为 Mock。'
            }}
          </span>
        </div>

        <div class="screen-actions">
          <button class="ghost-button" @click="toggleStatePanel">
            {{ statePanelVisible ? '隐藏状态' : '状态面板' }}
          </button>
          <button class="ghost-button" @click="resetRuntime">
            重置 Mock
          </button>
        </div>
      </header>

      <section
        v-if="statePanelVisible"
        class="state-panel"
      >
        <div>
          <strong>页面状态</strong>
          <span>
            loading / empty / error / success 均可在真实开发阶段映射为组件状态。
          </span>
        </div>

        <div class="state-buttons">
          <button
            v-for="state in runtimeStates"
            :key="state"
            :class="{ active: runtimeState === state }"
            @click="runtimeState = state"
          >
            {{ stateLabel(state) }}
          </button>
        </div>
      </section>

      <section
        v-if="runtimeState !== 'normal'"
        class="runtime-state-box"
        :class="runtimeState"
      >
        <strong>{{ stateLabel(runtimeState) }}</strong>
        <span>{{ stateDescription(runtimeState) }}</span>
      </section>

      <section
        v-else
        class="page-canvas"
        :class="`layout-${currentPage?.layout || 'default'}`"
      >
        <article
          v-for="component in currentPage?.components || []"
          :key="component.componentId"
          class="runtime-component"
          :class="`component-${normalizeType(component.type)}`"
        >
          <header
            v-if="component.title || component.label"
            class="component-header"
          >
            <div>
              <h3>{{ component.title || component.label }}</h3>
              <p v-if="component.description">
                {{ String(component.description) }}
              </p>
            </div>
          </header>

          <template
            v-if="
              normalizeType(component.type) === 'section' ||
              normalizeType(component.type) === 'card'
            "
          >
            <div
              v-if="component.items?.length"
              class="metric-grid"
            >
              <div
                v-for="(item, index) in component.items"
                :key="index"
                class="metric-card"
              >
                <span>{{ displayValue(item.label) }}</span>
                <strong>{{ displayValue(item.value) }}</strong>
                <em v-if="item.trend">{{ displayValue(item.trend) }}</em>
              </div>
            </div>

            <div
              v-else-if="component.components?.length"
              class="nested-components"
            >
              <div
                v-for="child in component.components"
                :key="child.componentId"
                class="nested-component"
              >
                <strong>
                  {{ child.title || child.label || child.componentId }}
                </strong>
                <span>{{ componentSummary(child) }}</span>
              </div>
            </div>

            <p
              v-else
              class="plain-text"
            >
              {{ componentText(component) }}
            </p>
          </template>

          <template v-else-if="normalizeType(component.type) === 'toolbar'">
            <div class="toolbar-actions">
              <button
                v-for="action in component.actions || []"
                :key="action.actionId || action.label || action.type"
                :class="buttonClass(action)"
                @click="handleAction(action, component)"
              >
                {{ action.label || action.type }}
              </button>
            </div>
          </template>

          <template v-else-if="normalizeType(component.type) === 'form'">
            <div class="form-grid">
              <div
                v-for="field in component.fields || []"
                :key="field.field"
                class="form-field"
              >
                <span>
                  {{ field.label }}
                  <em v-if="field.required">*</em>
                </span>

                <input
                  v-if="isInputField(field)"
                  :value="textControlValue(component.componentId, field.field)"
                  :type="inputType(field)"
                  :placeholder="field.placeholder ?? ''"
                  @input="
                    handleTextControlInput(
                      component.componentId,
                      field,
                      $event,
                    )
                  "
                />

                <textarea
                  v-else-if="
                    normalizeFieldType(field.type) === 'textarea'
                  "
                  :value="textControlValue(component.componentId, field.field)"
                  class="prototype-textarea"
                  :placeholder="field.placeholder ?? ''"
                  @input="
                    handleTextControlInput(
                      component.componentId,
                      field,
                      $event,
                    )
                  "
                ></textarea>

                <select
                  v-else-if="normalizeFieldType(field.type) === 'select'"
                  :value="textControlValue(component.componentId, field.field)"
                  @change="
                    handleSelectChange(
                      component.componentId,
                      field.field,
                      $event,
                    )
                  "
                >
                  <option value="">请选择</option>
                  <option
                    v-for="option in field.options || []"
                    :key="String(option.value)"
                    :value="String(option.value)"
                  >
                    {{ option.label }}
                  </option>
                </select>

                <div
                  v-else-if="normalizeFieldType(field.type) === 'radio'"
                  class="option-group"
                >
                  <label
                    v-for="option in field.options || []"
                    :key="String(option.value)"
                  >
                    <input
                      type="radio"
                      :name="`${component.componentId}-${field.field}`"
                      :value="String(option.value)"
                      :checked="
                        textControlValue(
                          component.componentId,
                          field.field,
                        ) === String(option.value)
                      "
                      @change="
                        setFormValue(
                          component.componentId,
                          field.field,
                          String(option.value),
                        )
                      "
                    />
                    {{ option.label }}
                  </label>
                </div>

                <label
                  v-else-if="normalizeFieldType(field.type) === 'switch'"
                  class="switch-field"
                >
                  <input
                    type="checkbox"
                    :checked="
                      booleanControlValue(
                        component.componentId,
                        field.field,
                      )
                    "
                    @change="
                      handleCheckboxChange(
                        component.componentId,
                        field.field,
                        $event,
                      )
                    "
                  />
                  <span>启用</span>
                </label>

                <input
                  v-else
                  :value="textControlValue(component.componentId, field.field)"
                  :placeholder="field.placeholder ?? ''"
                  @input="
                    handleTextControlInput(
                      component.componentId,
                      field,
                      $event,
                    )
                  "
                />

                <small v-if="field.description">
                  {{ field.description }}
                </small>
              </div>
            </div>

            <div class="action-row">
              <button
                v-for="action in component.actions || []"
                :key="action.actionId || action.label || action.type"
                :class="buttonClass(action)"
                @click="handleAction(action, component)"
              >
                {{ action.label || action.type }}
              </button>
            </div>
          </template>

          <template v-else-if="normalizeType(component.type) === 'table'">
            <div class="table-meta">
              <span>
                数据源：{{ component.dataSource?.mockBinding || '-' }}
              </span>
              <span>
                真实接口：{{ component.dataSource?.realApiBinding || '-' }}
              </span>
            </div>

            <div class="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th
                      v-for="column in component.columns || []"
                      :key="column.field"
                    >
                      {{ column.label }}
                    </th>
                    <th v-if="component.rowActions?.length">操作</th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="(row, rowIndex) in tableRows(component)"
                    :key="rowKey(row, rowIndex)"
                  >
                    <td
                      v-for="column in component.columns || []"
                      :key="column.field"
                    >
                      <span
                        v-if="column.type === 'tag'"
                        class="status-tag"
                      >
                        {{ displayCell(row, column.field) }}
                      </span>
                      <span v-else>
                        {{ displayCell(row, column.field) }}
                      </span>
                    </td>

                    <td
                      v-if="component.rowActions?.length"
                      class="row-actions"
                    >
                      <button
                        v-for="action in component.rowActions"
                        :key="action.actionId || action.label || action.type"
                        @click="handleAction(action, component, row)"
                      >
                        {{ action.label || action.type }}
                      </button>
                    </td>
                  </tr>

                  <tr v-if="tableRows(component).length === 0">
                    <td
                      :colspan="
                        (component.columns?.length || 1) +
                        (component.rowActions?.length ? 1 : 0)
                      "
                      class="empty-cell"
                    >
                      暂无 Mock 数据
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div
              v-if="component.actions?.length"
              class="action-row"
            >
              <button
                v-for="action in component.actions"
                :key="action.actionId || action.label || action.type"
                :class="buttonClass(action)"
                @click="handleAction(action, component)"
              >
                {{ action.label || action.type }}
              </button>
            </div>
          </template>

          <template
            v-else-if="normalizeType(component.type) === 'description'"
          >
            <dl class="description-list">
              <template
                v-for="field in component.fields || []"
                :key="field.field"
              >
                <dt>{{ field.label }}</dt>
                <dd>
                  {{
                    displayCell(
                      descriptionData(component),
                      field.field,
                    )
                  }}
                </dd>
              </template>
            </dl>
          </template>

          <template v-else-if="normalizeType(component.type) === 'steps'">
            <ol class="steps">
              <li
                v-for="(item, index) in component.items || []"
                :key="index"
                :class="String(item.status || '')"
              >
                <strong>
                  {{
                    displayValue(
                      item.title ||
                      item.label ||
                      `步骤${index + 1}`,
                    )
                  }}
                </strong>
                <span v-if="item.description">
                  {{ displayValue(item.description) }}
                </span>
              </li>
            </ol>
          </template>

          <template v-else-if="normalizeType(component.type) === 'tabs'">
            <div class="tabs-preview">
              <button
                v-for="tab in component.tabs || []"
                :key="tab.key"
                :class="{
                  active: activeTabs[component.componentId] === tab.key,
                }"
                @click="activeTabs[component.componentId] = tab.key"
              >
                {{ tab.title }}
              </button>
            </div>

            <div class="tab-panel">
              <div
                v-for="child in activeTabComponents(component)"
                :key="child.componentId"
                class="nested-component"
              >
                <strong>
                  {{ child.title || child.label || child.componentId }}
                </strong>
                <span>{{ componentSummary(child) }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="normalizeType(component.type) === 'button'">
            <div class="toolbar-actions">
              <button
                v-for="action in component.actions || []"
                :key="action.actionId || action.label || action.type"
                :class="buttonClass(action)"
                @click="handleAction(action, component)"
              >
                {{ action.label || action.type }}
              </button>
            </div>
          </template>

          <template v-else-if="normalizeType(component.type) === 'modal'">
            <div class="modal-placeholder">
              <strong>
                {{ component.title || component.modalId || '弹窗' }}
              </strong>
              <span>
                {{ component.body || '该弹窗可由按钮动作打开。' }}
              </span>
              <button
                @click="
                  openModal(
                    String(component.modalId || component.componentId),
                  )
                "
              >
                打开弹窗
              </button>
            </div>
          </template>

          <template v-else>
            <div class="unsupported">
              暂不支持组件类型：{{ component.type }}
            </div>
          </template>
        </article>
      </section>

      <div
        v-if="runtimeMessage"
        class="runtime-message"
      >
        {{ runtimeMessage }}
      </div>
    </main>

    <aside class="runtime-inspector">
      <div class="sidebar-block">
        <p class="eyebrow">API Bindings</p>
        <h3>接口绑定</h3>

        <div
          v-for="binding in contract.apiBindings || []"
          :key="bindingKey(binding)"
          class="binding-card"
        >
          <strong>
            {{ binding.method || '-' }} {{ binding.path || '-' }}
          </strong>
          <span>
            {{
              binding.bindingKey ||
              binding.mockBinding ||
              binding.mockAction ||
              '-'
            }}
          </span>
        </div>

        <p
          v-if="!contract.apiBindings?.length"
          class="empty-tip"
        >
          暂无接口绑定。
        </p>
      </div>

      <div class="sidebar-block">
        <p class="eyebrow">Mock Data</p>
        <h3>运行时数据</h3>

        <div
          v-for="(rows, key) in runtimeData"
          :key="key"
          class="data-card"
        >
          <strong>{{ key }}</strong>
          <span>{{ Array.isArray(rows) ? rows.length : 1 }} 条</span>
        </div>
      </div>
    </aside>

    <div
      v-if="activeModal"
      class="modal-mask"
      @click.self="closeModal"
    >
      <div class="runtime-modal">
        <header>
          <h3>
            {{ activeModal.title || activeModal.modalId || '弹窗' }}
          </h3>
          <button @click="closeModal">×</button>
        </header>

        <p>
          {{
            activeModal.body ||
            '这是由 UI_CONTRACT 定义的 Mock 弹窗。'
          }}
        </p>

        <footer>
          <button
            v-for="action in activeModal.actions || []"
            :key="action.actionId || action.label || action.type"
            :class="buttonClass(action)"
            @click="handleAction(action, activeModal)"
          >
            {{ action.label || action.type }}
          </button>
          <button class="ghost-button" @click="closeModal">
            关闭
          </button>
        </footer>
      </div>
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
import type {
  UiContractAction,
  UiContractComponent,
  UiContractContent,
  UiContractField,
  UiContractPage,
} from '../types/uiContract'

const props = defineProps<{
  contract: UiContractContent
}>()

type RuntimeState = 'normal' | 'loading' | 'empty' | 'error'
type RuntimeRow = Record<string, unknown>
type RuntimeFormValue =
  | string
  | number
  | boolean
  | string[]
  | null
type RuntimeFormBucket = Record<string, RuntimeFormValue>
type RuntimeFormState = Record<string, RuntimeFormBucket>
type RuntimeApiBinding = NonNullable<
  UiContractContent['apiBindings']
>[number]

const currentPageId = ref('')
const runtimeMessage = ref('')
const runtimeState = ref<RuntimeState>('normal')
const statePanelVisible = ref(false)
const activeModal = ref<UiContractComponent | null>(null)

const formState = reactive<RuntimeFormState>({})
const runtimeData = reactive<Record<string, unknown>>({})
const activeTabs = reactive<Record<string, string>>({})
const runtimeStates: RuntimeState[] = [
  'normal',
  'loading',
  'empty',
  'error',
]

const pages = computed<UiContractPage[]>(
  () => props.contract.pages || [],
)

const currentPage = computed<UiContractPage | null>(() => {
  const firstPage = pages.value[0]

  if (!firstPage) {
    return null
  }

  return (
    pages.value.find(
      (page) => page.pageId === currentPageId.value,
    ) || firstPage
  )
})

watch(
  () => props.contract,
  () => {
    resetRuntime()
  },
  {
    immediate: true,
    deep: true,
  },
)

function selectPage(pageId: string) {
  currentPageId.value = pageId
  runtimeMessage.value = ''
  runtimeState.value = 'normal'
}

function resetRuntime() {
  clearReactiveRecord(runtimeData)
  clearReactiveRecord(formState)
  clearReactiveRecord(activeTabs)

  const mockData = props.contract.mockData || {}

  Object.entries(mockData).forEach(([key, value]) => {
    runtimeData[key] = cloneValue(value)
  })

  for (const page of props.contract.pages || []) {
    for (const component of page.components || []) {
      initTabs(component)
      initFormDefaults(component)
    }
  }

  currentPageId.value =
    props.contract.pages?.[0]?.pageId || ''
  runtimeMessage.value = ''
  runtimeState.value = 'normal'
  activeModal.value = null
}

function clearReactiveRecord(
  record: Record<string, unknown>,
) {
  Object.keys(record).forEach((key) => {
    delete record[key]
  })
}

function initTabs(component: UiContractComponent) {
  const firstTab = component.tabs?.[0]

  if (
    normalizeType(component.type) === 'tabs' &&
    firstTab
  ) {
    activeTabs[component.componentId] = firstTab.key
  }

  for (const child of component.components || []) {
    initTabs(child)
  }

  for (const tab of component.tabs || []) {
    for (const child of tab.components || []) {
      initTabs(child)
    }
  }
}

function initFormDefaults(component: UiContractComponent) {
  if (normalizeType(component.type) === 'form') {
    initializeFormComponent(component)
  }

  for (const child of component.components || []) {
    initFormDefaults(child)
  }

  for (const tab of component.tabs || []) {
    for (const child of tab.components || []) {
      initFormDefaults(child)
    }
  }
}

function initializeFormComponent(
  component: UiContractComponent,
) {
  const bucket = ensureFormBucket(component.componentId)

  for (const field of component.fields || []) {
    if (bucket[field.field] === undefined) {
      bucket[field.field] = normalizeFormValue(
        field.defaultValue,
        field.type,
      )
    }
  }
}

function resetForm(component?: UiContractComponent) {
  if (
    !component ||
    normalizeType(component.type) !== 'form'
  ) {
    clearReactiveRecord(formState)
    return
  }

  delete formState[component.componentId]
  initializeFormComponent(component)
}

function ensureFormBucket(
  componentId: string,
): RuntimeFormBucket {
  if (!formState[componentId]) {
    formState[componentId] = {}
  }

  return formState[componentId]
}

function getFormValue(
  componentId: string,
  fieldKey: string,
): RuntimeFormValue {
  return formState[componentId]?.[fieldKey] ?? ''
}

function setFormValue(
  componentId: string,
  fieldKey: string,
  value: RuntimeFormValue,
) {
  ensureFormBucket(componentId)[fieldKey] = value
}

function textControlValue(
  componentId: string,
  fieldKey: string,
): string {
  const value = getFormValue(componentId, fieldKey)

  if (value === null) {
    return ''
  }

  if (Array.isArray(value)) {
    return value.join(',')
  }

  return String(value)
}

function booleanControlValue(
  componentId: string,
  fieldKey: string,
): boolean {
  return getFormValue(componentId, fieldKey) === true
}

function normalizeFormValue(
  value: unknown,
  fieldType?: string,
): RuntimeFormValue {
  if (value === undefined) {
    return normalizeFieldType(fieldType) === 'switch'
      ? false
      : ''
  }

  if (
    value === null ||
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return value
  }

  if (Array.isArray(value)) {
    return value.map((item) => String(item))
  }

  return String(value)
}

function handleTextControlInput(
  componentId: string,
  field: UiContractField,
  event: Event,
) {
  const target = event.target

  if (
    !(
      target instanceof HTMLInputElement ||
      target instanceof HTMLTextAreaElement
    )
  ) {
    return
  }

  if (normalizeFieldType(field.type) === 'number') {
    setFormValue(
      componentId,
      field.field,
      target.value === ''
        ? ''
        : Number(target.value),
    )
    return
  }

  setFormValue(
    componentId,
    field.field,
    target.value,
  )
}

function handleSelectChange(
  componentId: string,
  fieldKey: string,
  event: Event,
) {
  const target = event.target

  if (!(target instanceof HTMLSelectElement)) {
    return
  }

  setFormValue(
    componentId,
    fieldKey,
    target.value,
  )
}

function handleCheckboxChange(
  componentId: string,
  fieldKey: string,
  event: Event,
) {
  const target = event.target

  if (!(target instanceof HTMLInputElement)) {
    return
  }

  setFormValue(
    componentId,
    fieldKey,
    target.checked,
  )
}

function cloneValue(value: unknown): unknown {
  try {
    return JSON.parse(JSON.stringify(value))
  } catch {
    return value
  }
}

function normalizeType(type?: string): string {
  return String(type || '').trim().toLowerCase()
}

function normalizeFieldType(type?: string): string {
  return String(type || 'input')
    .trim()
    .toLowerCase()
}

function isInputField(
  field: UiContractField,
): boolean {
  return [
    'input',
    'text',
    'number',
    'date',
    'datetime',
    'password',
  ].includes(normalizeFieldType(field.type))
}

function inputType(field: UiContractField): string {
  const type = normalizeFieldType(field.type)

  if (type === 'number') return 'number'
  if (type === 'date') return 'date'
  if (type === 'datetime') return 'datetime-local'
  if (type === 'password') return 'password'

  return 'text'
}

function routeOf(pageId: string): string {
  return (
    props.contract.routes?.find(
      (item) => item.pageId === pageId,
    )?.path || '-'
  )
}

function formatRuleKey(key: string): string {
  const map: Record<string, string> = {
    mustFollowLayout: '遵循布局',
    mustFollowFields: '遵循字段',
    mustFollowValidationRules: '遵循校验',
    mustFollowInteractionFlow: '遵循交互',
    mustFollowApiBindings: '遵循接口绑定',
    allowStyleOptimization: '允许样式优化',
    breakingChangeRequiresReview: '破坏性变更需评审',
  }

  return map[key] || key
}

function formatRuleValue(value: unknown): string {
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }

  return String(value)
}

function stateLabel(state: string): string {
  const map: Record<string, string> = {
    normal: '正常',
    loading: '加载中',
    empty: '空状态',
    error: '错误状态',
  }

  return map[state] || state
}

function stateDescription(state: string): string {
  const map: Record<string, string> = {
    loading:
      '模拟接口加载中的骨架态，真实开发时应绑定接口请求状态。',
    empty:
      '模拟没有数据时的空状态，真实开发时应绑定列表或详情数据为空。',
    error:
      '模拟接口异常或权限不足时的错误态，真实开发时应绑定错误码与恢复动作。',
  }

  return map[state] || ''
}

function toggleStatePanel() {
  statePanelVisible.value =
    !statePanelVisible.value
}

function tableRows(
  component: UiContractComponent,
): RuntimeRow[] {
  const binding =
    component.dataSource?.mockBinding

  if (!binding) {
    return []
  }

  const value = runtimeData[binding]

  return Array.isArray(value)
    ? value.filter(
        (item): item is RuntimeRow =>
          isRecord(item),
      )
    : []
}

function descriptionData(
  component: UiContractComponent,
): RuntimeRow {
  const binding =
    component.dataSource?.mockBinding

  if (!binding) {
    return {}
  }

  const value = runtimeData[binding]

  if (Array.isArray(value)) {
    const first = value.find(isRecord)
    return first || {}
  }

  return isRecord(value) ? value : {}
}

function isRecord(
  value: unknown,
): value is RuntimeRow {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value)
  )
}

function displayCell(
  row: RuntimeRow,
  field: string,
): string {
  return displayValue(row[field])
}

function displayValue(value: unknown): string {
  if (
    value === null ||
    value === undefined ||
    value === ''
  ) {
    return '-'
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function rowKey(
  row: RuntimeRow,
  index: number,
): string {
  return String(
    row.id ||
    row.code ||
    index,
  )
}

function buttonClass(
  action: UiContractAction,
): string {
  const type = String(action.type || '')
    .toLowerCase()

  if (
    ['delete', 'reject', 'danger'].includes(type)
  ) {
    return 'danger-button'
  }

  if (
    ['reset-form', 'cancel'].includes(type)
  ) {
    return 'ghost-button'
  }

  if (
    ['approve', 'submit', 'create', 'save'].includes(type)
  ) {
    return 'primary-button'
  }

  return 'secondary-button'
}

function handleAction(
  action: UiContractAction,
  component?: UiContractComponent,
  row?: RuntimeRow,
) {
  const type = String(action.type || '')
    .toLowerCase()

  if (type === 'reset-form') {
    resetForm(component)
    runtimeMessage.value =
      action.successMessage || '表单已重置。'
    return
  }

  if (
    type === 'open-modal' &&
    action.targetModalId
  ) {
    openModal(action.targetModalId)
    runtimeMessage.value =
      action.successMessage ||
      '已打开 Mock 弹窗。'
    return
  }

  if (type === 'delete') {
    deleteRuntimeRow(component, row)
    runtimeMessage.value =
      action.successMessage ||
      'Mock 数据已删除。'
    return
  }

  if (action.nextPage) {
    currentPageId.value = action.nextPage
  }

  if (
    [
      'submit',
      'create',
      'save',
      'approve',
      'reject',
    ].includes(type) ||
    action.mockAction
  ) {
    if (!validateCurrentForm(component)) {
      return
    }

    pushFormToRuntimeData(
      component,
      row,
      type,
    )

    runtimeMessage.value =
      action.successMessage ||
      'Mock 操作已完成。'

    if (activeModal.value) {
      closeModal()
    }

    return
  }

  runtimeMessage.value =
    action.successMessage ||
    `已触发动作：${
      action.label ||
      action.type ||
      '操作'
    }`
}

function validateCurrentForm(
  component?: UiContractComponent,
): boolean {
  if (
    !component ||
    normalizeType(component.type) !== 'form'
  ) {
    return true
  }

  const bucket =
    formState[component.componentId] || {}

  for (const field of component.fields || []) {
    const value = bucket[field.field]

    if (
      field.required &&
      (
        value === undefined ||
        value === null ||
        value === ''
      )
    ) {
      runtimeMessage.value =
        `请填写必填字段：${field.label}`
      return false
    }

    const maxLength =
      field.validation?.maxLength

    if (
      typeof maxLength === 'number' &&
      typeof value === 'string' &&
      value.length > maxLength
    ) {
      runtimeMessage.value =
        `${field.label}长度不能超过 ${maxLength}`
      return false
    }
  }

  return true
}

function formPayload(
  component?: UiContractComponent,
): RuntimeRow {
  if (
    !component ||
    normalizeType(component.type) !== 'form'
  ) {
    return {}
  }

  const bucket =
    formState[component.componentId] || {}

  return cloneValue(bucket) as RuntimeRow
}

function pushFormToRuntimeData(
  component?: UiContractComponent,
  row?: RuntimeRow,
  actionType?: string,
) {
  const key =
    component?.dataSource?.mockBinding ||
    firstArrayDataKey()

  if (!key) {
    return
  }

  if (!Array.isArray(runtimeData[key])) {
    runtimeData[key] = []
  }

  const rows =
    runtimeData[key] as RuntimeRow[]
  const payload =
    formPayload(component)

  if (row) {
    Object.assign(
      row,
      payload,
      {
        status:
          actionType === 'approve'
            ? 'APPROVED'
            : actionType === 'reject'
              ? 'REJECTED'
              : row.status,
      },
    )
    return
  }

  const nextStatus =
    actionType === 'approve'
      ? 'APPROVED'
      : actionType === 'reject'
        ? 'REJECTED'
        : payload.status || 'DRAFT'

  rows.unshift({
    ...payload,
    id: `MOCK-${Date.now()}`,
    code: `MOCK-${String(
      rows.length + 1,
    ).padStart(3, '0')}`,
    status: nextStatus,
    createdAt:
      new Date().toLocaleString(),
  })
}

function deleteRuntimeRow(
  component?: UiContractComponent,
  row?: RuntimeRow,
) {
  if (!row) {
    return
  }

  const key =
    component?.dataSource?.mockBinding ||
    firstArrayDataKey()

  if (
    !key ||
    !Array.isArray(runtimeData[key])
  ) {
    return
  }

  runtimeData[key] = (
    runtimeData[key] as RuntimeRow[]
  ).filter((item) => item !== row)
}

function firstArrayDataKey(): string | null {
  const key = Object.keys(runtimeData).find(
    (item) =>
      Array.isArray(runtimeData[item]),
  )

  return key || null
}

function openModal(modalId: string) {
  const modal = findModal(modalId)

  if (modal) {
    activeModal.value = modal
  }
}

function closeModal() {
  activeModal.value = null
}

function findModal(
  modalId: string,
): UiContractComponent | null {
  for (const page of pages.value) {
    for (
      const component of
      page.components || []
    ) {
      const found =
        findModalInComponent(
          component,
          modalId,
        )

      if (found) {
        return found
      }
    }
  }

  return null
}

function findModalInComponent(
  component: UiContractComponent,
  modalId: string,
): UiContractComponent | null {
  if (
    normalizeType(component.type) === 'modal' &&
    String(
      component.modalId ||
      component.componentId,
    ) === modalId
  ) {
    return component
  }

  for (const child of component.components || []) {
    const found =
      findModalInComponent(
        child,
        modalId,
      )

    if (found) {
      return found
    }
  }

  for (const tab of component.tabs || []) {
    for (const child of tab.components || []) {
      const found =
        findModalInComponent(
          child,
          modalId,
        )

      if (found) {
        return found
      }
    }
  }

  return null
}

function activeTabComponents(
  component: UiContractComponent,
): UiContractComponent[] {
  const tabs = component.tabs || []
  const firstTab = tabs[0]

  if (!firstTab) {
    return []
  }

  const activeKey =
    activeTabs[component.componentId] ||
    firstTab.key

  const activeTab =
    tabs.find(
      (item) => item.key === activeKey,
    ) || firstTab

  return activeTab.components || []
}

function componentSummary(
  component: UiContractComponent,
): string {
  const type = normalizeType(component.type)

  if (type === 'form') {
    return (
      `表单字段 ${
        component.fields?.length || 0
      } 个`
    )
  }

  if (type === 'table') {
    return (
      `表格列 ${
        component.columns?.length || 0
      } 个`
    )
  }

  if (type === 'description') {
    return (
      `描述字段 ${
        component.fields?.length || 0
      } 个`
    )
  }

  return component.description
    ? String(component.description)
    : `组件类型：${type}`
}

function componentText(
  component: UiContractComponent,
): string {
  return String(
    component.body ||
    component.description ||
    '该组件由 UI_CONTRACT 定义。',
  )
}

function bindingKey(
  binding: RuntimeApiBinding,
): string {
  return String(
    binding.bindingKey ||
    `${binding.method || ''}-${
      binding.path || ''
    }-${
      binding.mockBinding || ''
    }-${
      binding.mockAction || ''
    }`,
  )
}
</script>

<style scoped>
.ui-runtime-shell {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 280px;
  gap: 18px;
  padding: 20px 24px 32px;
}

.runtime-sidebar,
.runtime-inspector {
  display: grid;
  gap: 14px;
  align-content: start;
}

.sidebar-block,
.runtime-screen,
.runtime-component {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
}

.sidebar-block {
  padding: 16px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sidebar-block h3,
.component-header h3,
.screen-header h2 {
  margin: 0;
  color: #0f172a;
}

.sidebar-block h3 {
  font-size: 15px;
}

.page-item {
  width: 100%;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 13px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}

.page-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.page-item strong,
.page-item span {
  display: block;
}

.page-item span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.rule-list {
  margin: 10px 0 0;
}

.rule-list dt {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.rule-list dd {
  margin: 2px 0 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.runtime-screen {
  min-height: 680px;
  padding: 18px;
  background: #f8fafc;
}

.screen-header,
.state-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
  padding: 16px;
}

.screen-header span,
.state-panel span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.screen-actions,
.state-buttons,
.action-row,
.toolbar-actions,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.state-panel {
  margin-top: 14px;
}

.state-buttons button {
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  padding: 7px 10px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.state-buttons button.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 800;
}

.runtime-state-box {
  margin-top: 18px;
  padding: 38px;
  border: 1px dashed #cbd5e1;
  border-radius: 18px;
  background: #ffffff;
  text-align: center;
}

.runtime-state-box strong,
.runtime-state-box span {
  display: block;
}

.runtime-state-box strong {
  color: #0f172a;
  font-size: 18px;
}

.runtime-state-box span {
  margin-top: 8px;
  color: #64748b;
}

.page-canvas {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.runtime-component {
  padding: 18px;
}

.component-header {
  margin-bottom: 14px;
}

.component-header p,
.plain-text {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.metric-card span,
.metric-card em {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.metric-card strong {
  display: block;
  margin: 8px 0 4px;
  color: #0f172a;
  font-size: 24px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-field {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.form-field em {
  color: #dc2626;
  font-style: normal;
}

.form-field small {
  color: #94a3b8;
  font-weight: 400;
}

input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 11px;
  padding: 9px 10px;
  background: #ffffff;
  color: #0f172a;
  font: inherit;
}

textarea {
  min-height: 82px;
  resize: vertical;
}

.option-group,
.switch-field {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-weight: 400;
}

.option-group label,
.switch-field {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.option-group input,
.switch-field input {
  width: auto;
}

.action-row,
.toolbar-actions {
  margin-top: 16px;
}

.primary-button,
.secondary-button,
.ghost-button,
.danger-button {
  border-radius: 999px;
  padding: 8px 13px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.ghost-button {
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  color: #334155;
}

.danger-button {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.table-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}

.table-wrapper {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

table {
  width: 100%;
  border-collapse: collapse;
  background: #ffffff;
  font-size: 13px;
}

th,
td {
  border-bottom: 1px solid #e2e8f0;
  padding: 10px;
  text-align: left;
  white-space: nowrap;
}

th {
  background: #f8fafc;
  color: #334155;
  font-weight: 800;
}

.empty-cell {
  color: #94a3b8;
  text-align: center;
}

.status-tag {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 8px;
  background: #eef2ff;
  color: #4338ca;
  font-weight: 700;
}

.row-actions button {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}

.description-list {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.description-list dt {
  color: #64748b;
}

.description-list dd {
  margin: 0;
  color: #0f172a;
  font-weight: 700;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.steps li {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #f8fafc;
}

.steps li.done {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.steps li.active {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.steps strong,
.steps span {
  display: block;
}

.steps span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.tabs-preview {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #e2e8f0;
}

.tabs-preview button {
  border: none;
  border-bottom: 2px solid transparent;
  padding: 9px 10px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.tabs-preview button.active {
  border-color: #2563eb;
  color: #1d4ed8;
  font-weight: 800;
}

.tab-panel,
.nested-components {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.nested-component,
.modal-placeholder {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #f8fafc;
}

.nested-component strong,
.nested-component span,
.modal-placeholder strong,
.modal-placeholder span {
  display: block;
}

.nested-component span,
.modal-placeholder span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.modal-placeholder button {
  margin-top: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  padding: 7px 11px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.unsupported {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 18px;
  color: #64748b;
  background: #f8fafc;
}

.runtime-message {
  position: sticky;
  bottom: 14px;
  margin-top: 16px;
  border: 1px solid #bbf7d0;
  border-radius: 14px;
  padding: 12px 14px;
  background: #f0fdf4;
  color: #166534;
  font-size: 13px;
  font-weight: 800;
}

.binding-card,
.data-card {
  border-top: 1px solid #e2e8f0;
  padding: 10px 0;
}

.binding-card strong,
.binding-card span,
.data-card strong,
.data-card span {
  display: block;
}

.binding-card strong,
.data-card strong {
  color: #0f172a;
  font-size: 13px;
}

.binding-card span,
.data-card span,
.empty-tip {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1300;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.35);
}

.runtime-modal {
  width: min(460px, 92vw);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
}

.runtime-modal header,
.runtime-modal footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.runtime-modal header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.runtime-modal header button {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 22px;
  cursor: pointer;
}

.runtime-modal p {
  margin: 0;
  padding: 18px 16px;
  color: #334155;
  line-height: 1.7;
}

.runtime-modal footer {
  justify-content: flex-end;
  border-top: 1px solid #e2e8f0;
  border-bottom: none;
}

@media (max-width: 1080px) {
  .ui-runtime-shell {
    grid-template-columns: 1fr;
  }

  .runtime-sidebar,
  .runtime-inspector {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .form-grid,
  .metric-grid,
  .steps,
  .runtime-sidebar,
  .runtime-inspector {
    grid-template-columns: 1fr;
  }

  .screen-header,
  .state-panel {
    display: grid;
  }
}
</style>
