<template>
  <section
    v-if="visible"
    class="runtime-node"
    :class="[
      `node-${component.type.toLowerCase()}`,
      { disabled: isDisabled },
    ]"
  >
    <template v-if="component.type === 'DIVIDER'">
      <hr />
    </template>

    <template v-else-if="component.type === 'TEXT'">
      <p class="plain-text">
        {{ textProp('text') || component.description || component.title }}
      </p>
    </template>

    <template v-else-if="component.type === 'PAGE_HEADER'">
      <header class="page-header-node">
        <div>
          <h2>{{ component.title }}</h2>
          <p>{{ component.description }}</p>
        </div>

        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </header>
    </template>

    <template v-else-if="component.type === 'BUTTON'">
      <button
        class="primary-button"
        type="button"
        :disabled="isDisabled"
        @click="emitFirstAction"
      >
        {{ component.title || actionLabel(component.actionIds?.[0]) }}
      </button>
    </template>

    <template v-else-if="component.type === 'LINK'">
      <button
        class="link-button"
        type="button"
        :disabled="isDisabled"
        @click="emitFirstAction"
      >
        {{ component.title || actionLabel(component.actionIds?.[0]) }}
      </button>
    </template>

    <template v-else-if="component.type === 'TOOLBAR'">
      <div class="toolbar-node">
        <div>
          <h3 v-if="component.title">{{ component.title }}</h3>
          <p v-if="component.description">{{ component.description }}</p>
        </div>

        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            :disabled="isDisabled"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </div>
    </template>

    <template v-else-if="component.type === 'FORM' || component.type === 'FIELD_GROUP'">
      <header v-if="component.title || component.description" class="node-header">
        <h3>{{ component.title }}</h3>
        <p>{{ component.description }}</p>
      </header>

      <div class="form-grid">
        <label
          v-for="field in component.fields || []"
          :key="field.fieldKey"
          class="form-field"
          :class="{ invalid: Boolean(fieldErrors[field.fieldKey]) }"
        >
          <span>
            {{ field.label }}
            <em v-if="field.required">*</em>
          </span>

          <input
            v-if="isTextInput(field.type)"
            :type="inputType(field.type)"
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || field.disabled"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <textarea
            v-else-if="field.type === 'TEXTAREA'"
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || field.disabled"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <select
            v-else-if="field.type === 'SELECT'"
            :value="displayInputValue(formValues[field.fieldKey])"
            :disabled="isDisabled || field.disabled"
            @change="updateField(field.fieldKey, inputValue($event))"
          >
            <option value="">
              {{ field.placeholder || '请选择' }}
            </option>
            <option
              v-for="option in field.options || []"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </option>
          </select>

          <div
            v-else-if="field.type === 'RADIO'"
            class="option-group"
          >
            <label
              v-for="option in field.options || []"
              :key="option.value"
            >
              <input
                type="radio"
                :name="field.fieldKey"
                :value="option.value"
                :checked="formValues[field.fieldKey] === option.value"
                :disabled="isDisabled || field.disabled || option.disabled"
                @change="updateField(field.fieldKey, option.value)"
              />
              {{ option.label }}
            </label>
          </div>

          <label
            v-else-if="field.type === 'CHECKBOX' || field.type === 'SWITCH'"
            class="checkbox-field"
          >
            <input
              type="checkbox"
              :checked="Boolean(formValues[field.fieldKey])"
              :disabled="isDisabled || field.disabled"
              @change="updateField(field.fieldKey, checkedValue($event))"
            />
            <span>{{ field.helpText || (field.type === 'SWITCH' ? '启用' : field.label) }}</span>
          </label>

          <input
            v-else-if="field.type === 'HIDDEN'"
            type="hidden"
            :value="displayInputValue(formValues[field.fieldKey])"
          />

          <input
            v-else
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || field.disabled"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <small v-if="fieldErrors[field.fieldKey]" class="field-error">
            {{ fieldErrors[field.fieldKey] }}
          </small>
          <small v-else-if="field.helpText">
            {{ field.helpText }}
          </small>
        </label>
      </div>

      <div class="action-row">
        <button
          v-for="actionId in component.actionIds || []"
          :key="actionId"
          type="button"
          :disabled="isDisabled"
          @click="emitAction(actionId)"
        >
          {{ actionLabel(actionId) }}
        </button>
      </div>
    </template>

    <template v-else-if="component.type === 'TABLE'">
      <header class="node-header">
        <div>
          <h3>{{ component.title }}</h3>
          <p>{{ component.description }}</p>
        </div>

        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            :disabled="isDisabled"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </header>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th
                v-for="column in component.columns || []"
                :key="column.columnKey"
                :style="{ width: column.width ? `${column.width}px` : undefined }"
              >
                {{ column.label }}
              </th>
              <th v-if="rowActionIds.length">操作</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(row, rowIndex) in tableRows"
              :key="rowKey(row, rowIndex)"
            >
              <td
                v-for="column in component.columns || []"
                :key="column.columnKey"
              >
                <span
                  v-if="column.type === 'TAG'"
                  class="tag"
                >
                  {{ displayValue(row[column.fieldKey]) }}
                </span>
                <span v-else>
                  {{ displayValue(row[column.fieldKey]) }}
                </span>
              </td>

              <td v-if="rowActionIds.length" class="row-actions">
                <button
                  v-for="actionId in rowActionIds"
                  :key="actionId"
                  type="button"
                  :disabled="isDisabled"
                  @click="emitAction(actionId, row)"
                >
                  {{ actionLabel(actionId) }}
                </button>
              </td>
            </tr>

            <tr v-if="!tableRows.length">
              <td
                :colspan="(component.columns?.length || 0) + (rowActionIds.length ? 1 : 0)"
                class="empty-cell"
              >
                暂无数据
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else-if="component.type === 'TABS'">
      <header v-if="component.title || component.description" class="node-header">
        <h3>{{ component.title }}</h3>
        <p>{{ component.description }}</p>
      </header>

      <div class="tabs">
        <button
          v-for="tab in component.tabs || []"
          :key="tab.tabKey"
          type="button"
          :class="{ active: activeTabKey === tab.tabKey }"
          @click="switchTab(tab.tabKey)"
        >
          {{ tab.title }}
        </button>
      </div>

      <div class="tab-content">
        <UiContractRuntimeNode
          v-for="child in activeTabComponents"
          :key="child.componentId"
          :component="child"
          :component-map="componentMap"
          :interactions="interactions"
          :form-values="formValues"
          :field-errors="fieldErrors"
          :mock-data="mockData"
          :active-tabs="activeTabs"
          :visible-component-ids="visibleComponentIds"
          :disabled-component-ids="disabledComponentIds"
          @execute="$emit('execute', $event)"
          @update-field="$emit('updateField', $event)"
          @switch-tab="$emit('switchTab', $event)"
        />
      </div>
    </template>

    <template v-else-if="component.type === 'DESCRIPTION_LIST'">
      <header class="node-header">
        <h3>{{ component.title }}</h3>
        <p>{{ component.description }}</p>
      </header>

      <dl class="description-list">
        <template
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.fieldKey || index)"
        >
          <dt>{{ String(item.label || item.key || '-') }}</dt>
          <dd>
            {{
              displayValue(
                item.value ??
                  boundObject[String(item.fieldKey || item.key || '')],
              )
            }}
          </dd>
        </template>
      </dl>
    </template>

    <template v-else-if="component.type === 'STEPS'">
      <header class="node-header">
        <h3>{{ component.title }}</h3>
        <p>{{ component.description }}</p>
      </header>

      <ol class="steps">
        <li
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.title || index)"
          :class="String(item.status || '').toLowerCase()"
        >
          <span>{{ index + 1 }}</span>
          <div>
            <strong>{{ String(item.title || '-') }}</strong>
            <small>{{ String(item.description || item.status || '') }}</small>
          </div>
        </li>
      </ol>
    </template>

    <template v-else-if="component.type === 'ALERT'">
      <div
        class="alert-node"
        :class="textProp('level').toLowerCase()"
      >
        <strong>{{ component.title }}</strong>
        <span>{{ component.description || textProp('message') }}</span>
      </div>
    </template>

    <template v-else-if="component.type === 'EMPTY_STATE'">
      <div class="empty-state">
        <strong>{{ component.title || '暂无内容' }}</strong>
        <span>{{ component.description }}</span>
        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </div>
    </template>

    <template v-else>
      <header v-if="component.title || component.description" class="node-header">
        <h3>{{ component.title }}</h3>
        <p>{{ component.description }}</p>
      </header>

      <div
        v-if="component.type === 'CARD' && objectArrayProp('items').length"
        class="metric-grid"
      >
        <article
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.label || index)"
        >
          <span>{{ String(item.label || '-') }}</span>
          <strong>{{ displayValue(item.value) }}</strong>
          <small>{{ String(item.hint || item.trend || '') }}</small>
        </article>
      </div>

      <div v-if="component.children?.length" class="children-grid">
        <UiContractRuntimeNode
          v-for="child in component.children"
          :key="child.componentId"
          :component="child"
          :component-map="componentMap"
          :interactions="interactions"
          :form-values="formValues"
          :field-errors="fieldErrors"
          :mock-data="mockData"
          :active-tabs="activeTabs"
          :visible-component-ids="visibleComponentIds"
          :disabled-component-ids="disabledComponentIds"
          @execute="$emit('execute', $event)"
          @update-field="$emit('updateField', $event)"
          @switch-tab="$emit('switchTab', $event)"
        />
      </div>

      <div class="action-row">
        <button
          v-for="actionId in component.actionIds || []"
          :key="actionId"
          type="button"
          :disabled="isDisabled"
          @click="emitAction(actionId)"
        >
          {{ actionLabel(actionId) }}
        </button>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  UiContractComponent,
  UiContractFieldType,
  UiContractInteraction,
} from '../types/uiContract'

defineOptions({
  name: 'UiContractRuntimeNode',
})

const props = defineProps<{
  component: UiContractComponent
  componentMap: Record<string, UiContractComponent>
  interactions: UiContractInteraction[]
  formValues: Record<string, unknown>
  fieldErrors: Record<string, string>
  mockData: Record<string, unknown>
  activeTabs: Record<string, string>
  visibleComponentIds: string[]
  disabledComponentIds: string[]
}>()

const emit = defineEmits<{
  execute: [payload: {
    actionId: string
    componentId: string
    row?: Record<string, unknown>
  }]
  updateField: [payload: {
    fieldKey: string
    value: unknown
  }]
  switchTab: [payload: {
    componentId: string
    tabKey: string
  }]
}>()

const visible = computed(() => {
  return (
    props.visibleComponentIds.length === 0 ||
    props.visibleComponentIds.includes(props.component.componentId)
  )
})

const isDisabled = computed(() => {
  return props.disabledComponentIds.includes(props.component.componentId)
})

const activeTabKey = computed(() => {
  const configured = props.activeTabs[props.component.componentId]
  if (configured) return configured
  return props.component.tabs?.[0]?.tabKey || ''
})

const activeTabComponents = computed(() => {
  const tab = props.component.tabs?.find(
    (item) => item.tabKey === activeTabKey.value,
  )

  return (tab?.componentIds || [])
    .map((componentId) => props.componentMap[componentId])
    .filter((component): component is UiContractComponent => Boolean(component))
})

const tableRows = computed<Record<string, unknown>[]>(() => {
  const value = props.component.dataBindingKey
    ? props.mockData[props.component.dataBindingKey]
    : []

  if (!Array.isArray(value)) return []

  return value.filter(
    (item): item is Record<string, unknown> =>
      typeof item === 'object' && item !== null && !Array.isArray(item),
  )
})

const boundObject = computed<Record<string, unknown>>(() => {
  const value = props.component.dataBindingKey
    ? props.mockData[props.component.dataBindingKey]
    : null

  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
})

const rowActionIds = computed(() => {
  const raw = props.component.props?.rowActionIds
  return Array.isArray(raw)
    ? raw.filter((value): value is string => typeof value === 'string')
    : []
})

function emitAction(
  actionId: string,
  row?: Record<string, unknown>,
) {
  emit('execute', {
    actionId,
    componentId: props.component.componentId,
    row,
  })
}

function emitFirstAction() {
  const actionId = props.component.actionIds?.[0]
  if (actionId) emitAction(actionId)
}

function updateField(
  fieldKey: string,
  value: unknown,
) {
  emit('updateField', {
    fieldKey,
    value,
  })
}

function switchTab(
  tabKey: string,
) {
  emit('switchTab', {
    componentId: props.component.componentId,
    tabKey,
  })
}

function actionLabel(
  actionId?: string,
): string {
  if (!actionId) return '操作'
  return props.interactions.find((item) => item.actionId === actionId)?.label || actionId
}

function isTextInput(
  type: UiContractFieldType,
): boolean {
  return [
    'INPUT',
    'PASSWORD',
    'NUMBER',
    'EMAIL',
    'DATE',
    'DATETIME',
  ].includes(type)
}

function inputType(
  type: UiContractFieldType,
): string {
  const inputTypeMap: Partial<Record<UiContractFieldType, string>> = {
    PASSWORD: 'password',
    NUMBER: 'number',
    EMAIL: 'email',
    DATE: 'date',
    DATETIME: 'datetime-local',
  }

  return inputTypeMap[type] || 'text'
}

function inputValue(
  event: Event,
): string {
  return (event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement).value
}

function checkedValue(
  event: Event,
): boolean {
  return (event.target as HTMLInputElement).checked
}

function displayInputValue(
  value: unknown,
): string | number {
  if (typeof value === 'number') return value
  if (value === null || value === undefined) return ''
  return String(value)
}

function displayValue(
  value: unknown,
): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function rowKey(
  row: Record<string, unknown>,
  index: number,
): string {
  return String(row.id || row.key || row.code || index)
}

function textProp(
  key: string,
): string {
  const value = props.component.props?.[key]
  return value === null || value === undefined ? '' : String(value)
}

function objectArrayProp(
  key: string,
): Record<string, unknown>[] {
  const value = props.component.props?.[key]
  if (!Array.isArray(value)) return []

  return value.filter(
    (item): item is Record<string, unknown> =>
      typeof item === 'object' && item !== null && !Array.isArray(item),
  )
}
</script>

<style scoped>
.runtime-node {
  min-width: 0;
}

.runtime-node:not(.node-button):not(.node-link):not(.node-divider) {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 18px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.runtime-node.disabled {
  opacity: 0.58;
  pointer-events: none;
}

.node-header,
.page-header-node,
.toolbar-node {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.node-header h3,
.page-header-node h2,
.toolbar-node h3 {
  margin: 0;
  color: #0f172a;
}

.node-header p,
.page-header-node p,
.toolbar-node p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.form-grid,
.children-grid,
.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-field {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 13px;
}

.form-field > span {
  font-weight: 700;
}

.form-field em {
  color: #dc2626;
  font-style: normal;
}

.form-field input,
.form-field textarea,
.form-field select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 11px;
  background: #ffffff;
  color: #0f172a;
  outline: none;
}

.form-field input:focus,
.form-field textarea:focus,
.form-field select:focus {
  border-color: #2563eb;
}

.form-field.invalid input,
.form-field.invalid textarea,
.form-field.invalid select {
  border-color: #ef4444;
}

.form-field textarea {
  min-height: 92px;
  resize: vertical;
}

.form-field small {
  color: #64748b;
}

.form-field .field-error {
  color: #dc2626;
}

.option-group,
.checkbox-field {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.checkbox-field {
  align-items: center;
}

.action-row,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.action-row {
  justify-content: flex-end;
  margin-top: 16px;
}

button {
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  padding: 8px 13px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 700;
  cursor: pointer;
}

button:hover {
  background: #dbeafe;
}

button:disabled {
  cursor: not-allowed;
}

.primary-button {
  background: #2563eb;
  color: #ffffff;
}

.link-button {
  border: none;
  padding: 0;
  background: transparent;
  color: #2563eb;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  border-bottom: 1px solid #e2e8f0;
  padding: 11px 12px;
  text-align: left;
  color: #334155;
  font-size: 13px;
}

th {
  background: #f8fafc;
  color: #475569;
}

.empty-cell {
  padding: 28px;
  text-align: center;
  color: #94a3b8;
}

.tag {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 8px;
  background: #eef2ff;
  color: #4338ca;
}

.tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #e2e8f0;
}

.tabs button {
  border: none;
  border-radius: 8px 8px 0 0;
  background: transparent;
  color: #64748b;
}

.tabs button.active {
  background: #eff6ff;
  color: #1d4ed8;
}

.tab-content {
  display: grid;
  gap: 14px;
  padding-top: 16px;
}

.description-list {
  display: grid;
  grid-template-columns: 160px 1fr;
  margin: 0;
}

.description-list dt,
.description-list dd {
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
}

.description-list dt {
  background: #f8fafc;
  color: #64748b;
}

.steps {
  display: grid;
  gap: 12px;
  padding: 0;
  list-style: none;
}

.steps li {
  display: flex;
  gap: 12px;
}

.steps li > span {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  background: #e2e8f0;
}

.steps li.done > span,
.steps li.completed > span {
  background: #dcfce7;
  color: #166534;
}

.steps li.active > span {
  background: #dbeafe;
  color: #1d4ed8;
}

.steps strong,
.steps small {
  display: block;
}

.steps small {
  margin-top: 3px;
  color: #64748b;
}

.alert-node {
  display: grid;
  gap: 5px;
  border-radius: 12px;
  padding: 13px 14px;
  background: #eff6ff;
  color: #1e40af;
}

.alert-node.error {
  background: #fef2f2;
  color: #991b1b;
}

.alert-node.warning {
  background: #fffbeb;
  color: #92400e;
}

.empty-state {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding: 32px;
  color: #64748b;
  text-align: center;
}

.metric-grid article {
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.metric-grid span,
.metric-grid strong,
.metric-grid small {
  display: block;
}

.metric-grid strong {
  margin: 7px 0;
  color: #0f172a;
  font-size: 24px;
}

.plain-text {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

hr {
  border: none;
  border-top: 1px solid #e2e8f0;
}

@media (max-width: 720px) {
  .form-grid,
  .children-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .node-header,
  .page-header-node,
  .toolbar-node {
    display: grid;
  }

  .description-list {
    grid-template-columns: 1fr;
  }
}
</style>
