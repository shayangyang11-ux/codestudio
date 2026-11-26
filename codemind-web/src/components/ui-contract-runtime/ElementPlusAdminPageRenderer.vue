<template>
  <section class="a9-admin-page" data-ui-admin-page-renderer="true">
    <ElCard v-if="filterForms.length" class="a9-admin-card a9-admin-filter-card" shadow="never">
      <template #header>
        <div class="a9-admin-card-header">
          <div>
            <strong
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="filterForms[0]?.componentId"
              :data-ui-component-type="componentType(filterForms[0])"
              :data-ui-element-label="componentTitle(filterForms[0]) || '查询条件'"
            >{{ componentTitle(filterForms[0]) || '查询条件' }}</strong>
            <span
              v-if="componentDescription(filterForms[0])"
              data-ui-selectable="true"
              data-ui-element-kind="DESCRIPTION"
              data-ui-text-key="description"
              :data-ui-component-id="filterForms[0]?.componentId"
              :data-ui-component-type="componentType(filterForms[0])"
              :data-ui-element-label="componentDescription(filterForms[0])"
            >{{ componentDescription(filterForms[0]) }}</span>
          </div>

          <ElSpace wrap size="small">
            <ElButton
              v-for="actionId in orderedActionIds(filterActionIds, filterForms[0])"
              :key="actionId"
              :type="actionButtonType(actionId)"
              :size="actionButtonSize(actionId, filterForms[0])"
              :style="actionInlineStyle(actionId, filterForms[0])"
              data-ui-selectable="true"
              data-ui-element-kind="ACTION"
              :data-ui-component-id="filterForms[0]?.componentId"
              :data-ui-component-type="componentType(filterForms[0])"
              :data-ui-action-id="actionId"
              :data-ui-element-label="actionLabel(actionId)"
              @click="emitAction(actionId, filterForms[0]?.componentId)"
            >{{ actionLabel(actionId) }}</ElButton>
          </ElSpace>
        </div>
      </template>

      <ElForm class="a9-admin-form" label-position="top" :model="formValues">
        <div class="a9-admin-form-grid">
          <ElFormItem
            v-for="field in primaryFormFields"
            :key="fieldKey(field)"
            :label="fieldLabel(field)"
            :error="fieldError(field)"
            class="a9-admin-form-item"
            data-ui-selectable="true"
            data-ui-element-kind="FIELD"
            :data-ui-component-id="filterForms[0]?.componentId"
            :data-ui-component-type="componentType(filterForms[0])"
            :data-ui-field-key="fieldKey(field)"
            :data-ui-element-label="fieldLabel(field)"
            :style="fieldInlineStyle(field, filterForms[0])"
          >
            <ElSelect
              v-if="fieldControlKind(field) === 'SELECT'"
              class="a9-admin-control"
              :model-value="fieldStringValue(field)"
              :placeholder="fieldPlaceholder(field)"
              clearable
              filterable
              :teleported="true"
              popper-class="a9-admin-popper"
              @update:model-value="updateFieldValue(field, $event)"
            >
              <ElOption
                v-for="option in fieldOptions(field)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>

            <ElDatePicker
              v-else-if="fieldControlKind(field) === 'DATE'"
              class="a9-admin-control"
              :model-value="fieldAnyValue(field)"
              type="date"
              :placeholder="fieldPlaceholder(field)"
              :teleported="true"
              popper-class="a9-admin-popper"
              @update:model-value="updateFieldValue(field, $event)"
            />

            <ElDatePicker
              v-else-if="fieldControlKind(field) === 'DATERANGE'"
              class="a9-admin-control"
              :model-value="fieldAnyValue(field)"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :teleported="true"
              popper-class="a9-admin-popper"
              @update:model-value="updateFieldValue(field, $event)"
            />

            <ElInputNumber
              v-else-if="fieldControlKind(field) === 'NUMBER'"
              class="a9-admin-control"
              :model-value="fieldNumberValue(field)"
              controls-position="right"
              @update:model-value="updateFieldValue(field, $event)"
            />

            <ElSwitch
              v-else-if="fieldControlKind(field) === 'SWITCH'"
              :model-value="fieldBooleanValue(field)"
              @update:model-value="updateFieldValue(field, $event)"
            />

            <ElInput
              v-else
              class="a9-admin-control"
              :model-value="fieldStringValue(field)"
              :type="fieldControlKind(field) === 'TEXTAREA' ? 'textarea' : 'text'"
              :rows="fieldControlKind(field) === 'TEXTAREA' ? 3 : undefined"
              :placeholder="fieldPlaceholder(field)"
              clearable
              @update:model-value="updateFieldValue(field, $event)"
            />
          </ElFormItem>
        </div>
      </ElForm>
    </ElCard>

    <ElCard v-if="primaryTable" class="a9-admin-card a9-admin-table-card" shadow="never">
      <template #header>
        <div class="a9-admin-card-header">
          <div>
            <strong
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-element-label="componentTitle(primaryTable) || '数据列表'"
            >{{ componentTitle(primaryTable) || '数据列表' }}</strong>
            <span
              v-if="componentDescription(primaryTable)"
              data-ui-selectable="true"
              data-ui-element-kind="DESCRIPTION"
              data-ui-text-key="description"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-element-label="componentDescription(primaryTable)"
            >{{ componentDescription(primaryTable) }}</span>
          </div>

          <ElSpace wrap size="small">
            <ElButton
              v-for="actionId in orderedActionIds(tableHeaderActionIds, primaryTable)"
              :key="actionId"
              :type="actionButtonType(actionId)"
              :size="actionButtonSize(actionId, primaryTable)"
              :style="actionInlineStyle(actionId, primaryTable)"
              data-ui-selectable="true"
              data-ui-element-kind="ACTION"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-action-id="actionId"
              :data-ui-element-label="actionLabel(actionId)"
              @click="emitAction(actionId, primaryTable.componentId)"
            >{{ actionLabel(actionId) }}</ElButton>
          </ElSpace>
        </div>
      </template>

      <ElTable
        class="a9-admin-table"
        :data="primaryRows"
        border
        stripe
        size="small"
        table-layout="fixed"
        :fit="true"
        :row-style="tableRowStyle"
        :cell-style="tableCellStyle"
      >
        <ElTableColumn
          v-for="column in primaryColumns"
          :key="column.columnKey"
          :prop="column.fieldKey"
          :min-width="columnWidth(column)"
          show-overflow-tooltip
        >
          <template #header>
            <div
              class="a9-admin-column-select-target"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_COLUMN"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-column-key="column.columnKey"
              :data-ui-element-label="`表格列 · ${column.label || column.columnKey}`"
              :style="columnHeaderSelectStyle(column)"
            >
              <span
                class="a9-admin-column-label-hit"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_COLUMN_LABEL"
                data-ui-text-key="columnLabel"
                data-ui-text-role="label"
                :data-ui-component-id="primaryTable.componentId"
                :data-ui-component-type="componentType(primaryTable)"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="`列名 · ${column.label || column.columnKey}`"
                :style="columnLabelInlineStyle(column)"
              >{{ column.label || column.columnKey }}</span>
            </div>
          </template>

          <template #default="{ row }">
            <ElTag
              v-if="isTagColumn(column)"
              :type="statusTagType(row[column.fieldKey])"
              effect="light"
              size="small"
              class="a9-admin-cell-mock-value"
              data-ui-mock-value="true"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_CELL"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-column-key="column.columnKey"
              :data-ui-element-label="`表格单元格 · ${column.label || column.columnKey}`"
              :style="cellInlineStyle(column)"
            >{{ displayValue(row[column.fieldKey]) }}</ElTag>
            <span
              v-else
              class="a9-admin-cell-mock-value a9-admin-cell-content"
              data-ui-mock-value="true"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_CELL"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              :data-ui-column-key="column.columnKey"
              :data-ui-element-label="`表格单元格 · ${column.label || column.columnKey}`"
              :style="cellInlineStyle(column)"
            >{{ displayValue(row[column.fieldKey]) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn
          v-if="rowActionIds.length"
          :label="actionColumnLabel()"
          :width="actionColumnWidth()"
          fixed="right"
        >
          <template #header>
            <div
              class="a9-admin-column-select-target a9-admin-action-column-select-target"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_COLUMN"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              data-ui-column-key="__row_actions"
              :data-ui-element-label="`表格列 · ${actionColumnLabel()}`"
              :style="actionColumnHeaderStyle()"
            >
              <span
                class="a9-admin-column-label-hit"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_COLUMN_LABEL"
                data-ui-text-key="actionColumnLabel"
                data-ui-text-role="label"
                :data-ui-component-id="primaryTable.componentId"
                :data-ui-component-type="componentType(primaryTable)"
                data-ui-column-key="__row_actions"
                :data-ui-element-label="`列名 · ${actionColumnLabel()}`"
                :style="actionColumnLabelInlineStyle()"
              >{{ actionColumnLabel() }}</span>
            </div>
          </template>

          <template #default="{ row }">
            <ElSpace
              wrap
              size="small"
              class="a9-admin-action-cell-select-target"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_COLUMN"
              :data-ui-component-id="primaryTable.componentId"
              :data-ui-component-type="componentType(primaryTable)"
              data-ui-column-key="__row_actions"
              :data-ui-element-label="`表格列 · ${actionColumnLabel()}`"
              :style="actionColumnCellStyle()"
            >
              <ElButton
                v-for="actionId in orderedActionIds(rowActionIds, primaryTable)"
                :key="actionId"
                link
                type="primary"
                :size="actionButtonSize(actionId, primaryTable)"
                :style="actionInlineStyle(actionId, primaryTable)"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_ACTION"
                :data-ui-component-id="primaryTable.componentId"
                :data-ui-component-type="componentType(primaryTable)"
                :data-ui-action-id="actionId"
                :data-ui-element-label="actionLabel(actionId)"
                @click="emitAction(actionId, primaryTable.componentId, row)"
              >{{ actionLabel(actionId) }}</ElButton>
            </ElSpace>
          </template>
        </ElTableColumn>

        <template #empty>
          <ElEmpty description="暂无数据" />
        </template>
      </ElTable>
    </ElCard>

    <ElementPlusRuntimeNode
      v-for="component in fallbackComponents"
      :key="component.componentId"
      :component="component"
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
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import ElementPlusRuntimeNode from './ElementPlusUiContractRuntimeNode.vue'
import type {
  UiContractComponent,
  UiContractInteraction,
} from '../../types/uiContract'

defineOptions({
  name: 'ElementPlusAdminPageRenderer',
})

const props = defineProps<{
  components: UiContractComponent[]
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
  execute: [payload: { actionId: string; componentId: string; row?: Record<string, unknown>; label?: string; dataBindingKey?: string; tableComponentId?: string; sourceComponentType?: string }]
  updateField: [payload: { fieldKey: string; value: unknown }]
  switchTab: [payload: { componentId: string; tabKey: string }]
}>()

type LooseRecord = Record<string, any>

const flattenedComponents = computed(() => {
  const result: UiContractComponent[] = []
  const visit = (component: UiContractComponent | null | undefined) => {
    if (!component) return
    const type = componentType(component)
    if (isLayoutType(type)) {
      childrenOf(component).forEach(visit)
      return
    }
    result.push(component)
  }
  props.components.forEach(visit)
  return result
})

const filterForms = computed(() => flattenedComponents.value.filter((component) => isFormType(componentType(component))))
const tables = computed(() => flattenedComponents.value.filter((component) => isTableType(componentType(component))))
const actionBars = computed(() => flattenedComponents.value.filter((component) => isActionType(componentType(component))))

const consumedIds = computed(() => {
  const ids = new Set<string>()
  filterForms.value.forEach((item) => item.componentId && ids.add(item.componentId))
  tables.value.forEach((item) => item.componentId && ids.add(item.componentId))
  actionBars.value.forEach((item) => item.componentId && ids.add(item.componentId))
  return ids
})

const fallbackComponents = computed(() => flattenedComponents.value.filter((component) => {
  if (!component.componentId) return true
  return !consumedIds.value.has(component.componentId)
}))

const primaryFormFields = computed(() => {
  const form = filterForms.value[0]
  return form ? fieldsOf(form) : []
})

const primaryTable = computed(() => tables.value[0] || null)
const primaryColumns = computed(() => primaryTable.value ? columnsOf(primaryTable.value) : [])
const primaryRows = computed(() => primaryTable.value ? rowsOf(primaryTable.value, primaryColumns.value) : [])

const filterActionIds = computed(() => {
  const form = filterForms.value[0]
  const explicit = form ? actionIdsOf(form) : []
  return explicit.length ? explicit : ['search', 'reset']
})

const tableHeaderActionIds = computed(() => {
  const ids = uniqueStrings([
    ...actionBars.value.flatMap(actionIdsOf),
    ...(primaryTable.value ? actionIdsOf(primaryTable.value) : []),
  ])
  const headerIds = ids.filter((actionId) => !isRowAction(actionId))
  return headerIds.length ? headerIds : ['create']
})

const rowActionIds = computed(() => {
  if (!primaryTable.value) return []
  const tableProps = propsOf(primaryTable.value)
  const explicit = uniqueStrings([
    ...normalizeActionIds(tableProps.rowActionIds),
    ...normalizeActionIds(tableProps.rowActions),
    ...normalizeActionIds(asRecord(primaryTable.value).rowActionIds),
  ])
  if (explicit.length) return explicit
  const ids = uniqueStrings([
    ...(primaryTable.value ? actionIdsOf(primaryTable.value) : []),
    ...props.interactions.map((interaction) => String(asRecord(interaction).actionId || '')).filter(Boolean),
  ])
  const rowIds = ids.filter(isRowAction)
  return rowIds.length ? rowIds.slice(0, 4) : ['viewDetail', 'edit']
})

function asRecord(value: unknown): LooseRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as LooseRecord : {}
}
function arrayValue(value: unknown): unknown[] { return Array.isArray(value) ? value : [] }
function uniqueStrings(values: string[]): string[] {
  const result: string[] = []
  const seen = new Set<string>()
  for (const value of values) {
    const normalized = String(value || '').trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}
function propsOf(component?: UiContractComponent | null): LooseRecord {
  const record = asRecord(component)
  return asRecord(record.props || record.properties || {})
}
function componentType(component?: UiContractComponent | null): string {
  const record = asRecord(component)
  return String(record.type || record.componentType || '').trim().toUpperCase()
}
function componentTitle(component?: UiContractComponent | null): string {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  return String(record.title || record.label || componentProps.title || '').trim()
}
function componentDescription(component?: UiContractComponent | null): string {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  return String(record.description || record.subtitle || componentProps.description || '').trim()
}
function childrenOf(component: UiContractComponent): UiContractComponent[] {
  return arrayValue(asRecord(component).children).filter((item) => item && typeof item === 'object') as UiContractComponent[]
}
function isLayoutType(type: string): boolean {
  return ['SECTION', 'CARD', 'CONTAINER', 'GROUP', 'PANEL', 'PAGE_SECTION', 'CONTENT_BLOCK', 'LAYOUT'].includes(type)
}
function isFormType(type: string): boolean {
  return ['FORM', 'FIELD_GROUP', 'FILTER_FORM', 'SEARCH_FORM', 'QUERY_FORM', 'ADVANCED_FILTER', 'CREATE_FORM', 'EDIT_FORM'].includes(type)
}
function isTableType(type: string): boolean {
  return ['TABLE', 'DATA_TABLE', 'LIST_TABLE', 'RESULT_TABLE', 'GRID_TABLE'].includes(type)
}
function isActionType(type: string): boolean {
  return ['ACTION_BAR', 'TOOLBAR', 'BUTTON_GROUP', 'ACTIONS', 'BUTTON', 'PRIMARY_ACTION', 'LINK'].includes(type)
}
function fieldsOf(component: UiContractComponent): LooseRecord[] {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  return arrayValue(record.fields || componentProps.fields || componentProps.formFields || componentProps.controls)
    .map(asRecord)
    .filter((field) => field.hidden !== true && String(field.type || '').toUpperCase() !== 'HIDDEN')
}
function columnsOf(component: UiContractComponent): LooseRecord[] {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  const columns = arrayValue(record.columns || componentProps.columns || componentProps.tableColumns)
    .map(asRecord)
    .filter((column) => Object.keys(column).length && column.hidden !== true)
  return columns.map((column, index) => {
    const key = String(column.columnKey || column.fieldKey || column.prop || column.key || `column_${index + 1}`)
    return { ...column, columnKey: key, fieldKey: String(column.fieldKey || column.prop || key), label: String(column.label || column.title || key) }
  })
}
function rowsOf(component: UiContractComponent, columns: LooseRecord[]): LooseRecord[] {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  const runtimeData = props.mockData || {}
  const dataKey = String(
    componentProps.dataKey ||
      componentProps.mockDataKey ||
      componentProps.dataBindingKey ||
      record.dataKey ||
      record.dataBindingKey ||
      component.componentId ||
      '',
  ).trim()

  const candidates = [
    dataKey ? runtimeData[dataKey] : null,
    component.componentId ? runtimeData[component.componentId] : null,
    runtimeData.rows,
    runtimeData.list,
    runtimeData.tableRows,
    componentProps.rows,
    componentProps.data,
    record.rows,
    record.data,
  ]

  for (const candidate of candidates) {
    const rows = arrayValue(candidate).map(asRecord).filter((row) => Object.keys(row).length)
    if (rows.length) return rows
  }

  return buildFallbackRows(columns)
}






function normalizeActionIds(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    if (typeof item === 'string') return item.trim()
    const record = asRecord(item)
    return String(record.actionId || record.id || record.key || '').trim()
  }).filter(Boolean)
}
function actionIdsOf(component: UiContractComponent): string[] {
  const record = asRecord(component)
  const componentProps = propsOf(component)
  return uniqueStrings([...normalizeActionIds(record.actionIds), ...normalizeActionIds(componentProps.actionIds), ...normalizeActionIds(componentProps.actions)])
}
function findAction(actionId?: unknown): LooseRecord | null {
  const id = String(actionId || '').trim()
  if (!id) return null
  return props.interactions.find((item: UiContractInteraction) => String(asRecord(item).actionId || '') === id) as unknown as LooseRecord || null
}
function actionLabel(actionId: unknown): string {
  const id = String(actionId || '').trim()
  const action = findAction(id)
  return String(action?.label || action?.title || action?.name || inferActionLabel(id) || id || '操作')
}
function inferActionLabel(actionId: string): string {
  const text = actionId.toLowerCase()
  if (text.includes('create') || text.includes('add') || text.includes('new')) return '新增'
  if (text.includes('save') || text.includes('submit')) return '保存'
  if (text.includes('search') || text.includes('query')) return '查询'
  if (text.includes('reset')) return '重置'
  if (text.includes('review') || text.includes('audit')) return '查看审核'
  if (text.includes('detail') || text.includes('view')) return '查看详情'
  if (text.includes('edit') || text.includes('update')) return '编辑'
  if (text.includes('delete') || text.includes('remove')) return '删除'
  if (text.includes('cancel')) return '取消'
  if (text.includes('export')) return '导出'
  if (text.includes('import')) return '导入'
  return ''
}

// A9.10.14.4.3.15.6.24I_PROTOTYPE_EDIT_INTENT_GUARD
function actionRecordFor(actionId: unknown, owner?: UiContractComponent | null): LooseRecord {
  const id = String(actionId || '').trim()
  if (!id) return {}

  const ownerProps = propsOf(owner)
  const candidates = [
    ...arrayValue(asRecord(owner).actions),
    ...arrayValue(asRecord(owner).actionIds),
    ...arrayValue(asRecord(owner).rowActions),
    ...arrayValue(asRecord(owner).rowActionIds),
    ...arrayValue(ownerProps.actions),
    ...arrayValue(ownerProps.actionIds),
    ...arrayValue(ownerProps.rowActions),
    ...arrayValue(ownerProps.rowActionIds),
  ].map(asRecord)

  return candidates.find((item) => {
    const itemId = String(item.actionId || item.id || item.key || item.value || item.label || '').trim()
    return itemId === id
  }) || {}
}

function actionInlineStyle(actionId: unknown, owner?: UiContractComponent | null): Record<string, string> {
  const id = String(actionId || '').trim()
  const ownerRecord = asRecord(owner)
  const ownerProps = propsOf(owner)
  const elementStyles = asRecord(ownerProps.elementStyles)
  const actionRecord = actionRecordFor(actionId, owner)
  const sourceStyle = {
    ...asRecord(ownerRecord.style),
    ...asRecord(ownerProps.style),
    ...asRecord(actionRecord.style || actionRecord.inlineStyle),
    ...asRecord(id ? elementStyles[`action:${id}`] : null),
  }

  const style: Record<string, string> = {}
  for (const key of ['backgroundColor','color','borderColor','borderRadius','minWidth','width','height','minHeight','padding','paddingInline','fontSize','fontWeight','marginLeft','marginRight','justifySelf','alignSelf','textAlign','display','justifyContent','order','transform','whiteSpace']) {
    const value = sourceStyle[key]
    if (value !== undefined && value !== null && String(value).trim()) style[key] = String(value)
  }
  return style
}

function fieldInlineStyle(field: LooseRecord, owner?: UiContractComponent | null): Record<string, string> {
  const key = fieldKey(field)
  const elementStyles = asRecord(propsOf(owner).elementStyles)
  const sourceStyle = {
    ...asRecord(field.style),
    ...asRecord(key ? elementStyles[`field:${key}`] : null),
    ...asRecord(key ? elementStyles[`fieldControl:${key}`] : null),
    ...asRecord(key ? elementStyles[`fieldLabel:${key}`] : null),
  }
  const style: Record<string, string> = {}
  for (const item of ['gridColumn','width','maxWidth','minWidth','display','order','marginLeft','marginRight','justifySelf','alignSelf','textAlign','transform']) {
    const value = sourceStyle[item]
    if (value !== undefined && value !== null && String(value).trim()) style[item] = String(value)
  }
  return style
}

function actionButtonSize(actionId: unknown, owner?: UiContractComponent | null): 'small' | 'default' | 'large' {
  const actionRecord = actionRecordFor(actionId, owner)
  const ownerProps = propsOf(owner)
  const id = String(actionId || '').trim()
  const elementStyles = asRecord(ownerProps.elementStyles)
  const actionStyle = asRecord(id ? elementStyles[`action:${id}`] : null)

  // A9.10.14.4.3.15.6.24J3_SCOPED_ACTION_SIZE
  // Header/table/row action buttons are children of the same owner component.
  // Never let ownerProps.size resize every action in the toolbar.
  const raw = String(
    actionRecord.size ||
      asRecord(actionRecord.props).size ||
      actionStyle.size ||
      (!id ? ownerProps.size : '') ||
      (!id ? asRecord(owner).size : '') ||
      '',
  ).toLowerCase()

  if (raw.includes('large') || raw.includes('big') || raw.includes('大')) return 'large'
  if (raw.includes('small') || raw.includes('mini') || raw.includes('小')) return 'small'
  return 'small'
}


// A9.10.14.4.3.15.6.24J5_COLUMN_SELECT_LABEL_AND_ACTION_ORDER
function orderedActionIds(actionIds: unknown, owner?: UiContractComponent | null): string[] {
  const ids = Array.isArray(actionIds)
    ? actionIds.map((item) => String(item || '').trim()).filter(Boolean)
    : []

  return [...ids].sort((left, right) => {
    const leftOrder = actionOrderValue(left, owner)
    const rightOrder = actionOrderValue(right, owner)

    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder
    }

    return ids.indexOf(left) - ids.indexOf(right)
  })
}

function actionOrderValue(actionId: string, owner?: UiContractComponent | null): number {
  const id = String(actionId || '').trim()
  if (!id) return 0

  const ownerProps = propsOf(owner)
  const elementStyles = asRecord(ownerProps.elementStyles)
  const actionStyle = asRecord(elementStyles[`action:${id}`])
  const actionRecord = actionRecordFor(id, owner)

  const raw =
    actionStyle.order ??
    asRecord(actionRecord.style).order ??
    actionRecord.order ??
    asRecord(actionRecord.props).order ??
    0

  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : 0
}

function actionButtonType(actionId: unknown): '' | 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const label = actionLabel(actionId)
  const text = `${actionId || ''} ${label}`.toLowerCase()
  if (text.includes('delete') || text.includes('remove') || label.includes('删除')) return 'danger'
  if (text.includes('cancel') || label.includes('取消')) return 'warning'
  if (text.includes('submit') || text.includes('save') || text.includes('create') || text.includes('add') || label.includes('保存') || label.includes('新增') || label.includes('提交')) return 'primary'
  if (text.includes('export') || label.includes('导出')) return 'success'
  return ''
}
function isRowAction(actionId: string): boolean {
  const label = actionLabel(actionId)
  const text = `${actionId} ${label}`.toLowerCase()
  return text.includes('view') || text.includes('detail') || text.includes('edit') || text.includes('delete') || text.includes('remove') || text.includes('cancel') || text.includes('review') || text.includes('audit') || text.includes('row') || ['查看', '详情', '查看详情', '编辑', '删除', '取消', '查看审核'].includes(label)
}
function emitAction(actionId: string, componentId?: string, row?: LooseRecord) {
  const sourceComponentId = String(componentId || '').trim()
  const sourceComponent = sourceComponentId ? props.componentMap[sourceComponentId] : null
  const table = primaryTable.value || sourceComponent
  const tableProps = table ? propsOf(table) : {}
  const sourceType = sourceComponent ? componentType(sourceComponent) : ''
  const tableId = String(table?.componentId || '').trim()
  const dataBindingKey = String(
    tableProps.dataKey ||
      tableProps.mockDataKey ||
      tableId ||
      sourceComponentId ||
      'rows',
  ).trim()

  emit('execute', {
    actionId,
    componentId: sourceComponentId,
    row,
    label: actionLabel(actionId),
    dataBindingKey,
    tableComponentId: tableId,
    sourceComponentType: sourceType,
  })
}


function fieldKey(field: LooseRecord): string { return String(field.fieldKey || field.key || field.name || '').trim() }
function fieldLabel(field: LooseRecord): string { return String(field.label || field.title || field.name || fieldKey(field) || '字段') }
function fieldPlaceholder(field: LooseRecord): string { return String(field.placeholder || `请输入${fieldLabel(field)}`) }
function fieldRawValue(field: LooseRecord): unknown {
  const key = fieldKey(field)
  if (!key) return field.defaultValue ?? field.value ?? ''
  return props.formValues[key] ?? field.value ?? field.defaultValue ?? ''
}
function fieldAnyValue(field: LooseRecord): any { return fieldRawValue(field) as any }
function fieldStringValue(field: LooseRecord): string {
  const value = fieldRawValue(field)
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.join(',')
  return String(value)
}
function fieldNumberValue(field: LooseRecord): number | undefined {
  const value = Number(fieldRawValue(field))
  return Number.isFinite(value) ? value : undefined
}
function fieldBooleanValue(field: LooseRecord): boolean { return Boolean(fieldRawValue(field)) }
function fieldError(field: LooseRecord): string {
  const key = fieldKey(field)
  return key ? props.fieldErrors[key] || '' : ''
}
function fieldControlKind(field: LooseRecord): string {
  const raw = String(field.type || field.fieldType || field.controlType || field.widget || '').toUpperCase()
  if (raw.includes('SELECT') || raw.includes('ENUM')) return 'SELECT'
  if (raw.includes('DATE_RANGE') || raw.includes('DATERANGE') || raw.includes('RANGE')) return 'DATERANGE'
  if (raw.includes('DATE') || raw.includes('TIME')) return 'DATE'
  if (raw.includes('BOOL') || raw.includes('SWITCH') || raw.includes('CHECKBOX')) return 'SWITCH'
  if (raw.includes('NUMBER') || raw.includes('AMOUNT') || raw.includes('PRICE') || raw.includes('COUNT')) return 'NUMBER'
  if (raw.includes('TEXTAREA') || raw.includes('MULTILINE') || raw.includes('LONG_TEXT')) return 'TEXTAREA'
  return 'INPUT'
}
function fieldOptions(field: LooseRecord): Array<{ label: string; value: string }> {
  const explicitOptions = arrayValue(field.options || field.enumValues || field.values).map((option, index) => {
    if (typeof option === 'string') return { label: option, value: option }
    const record = asRecord(option)
    const value = String(record.value || record.key || record.id || record.label || index)
    const label = String(record.label || record.name || record.title || value)
    return { label, value }
  }).filter((option) => option.label && option.value)
  if (explicitOptions.length) return explicitOptions
  const text = `${fieldLabel(field)} ${fieldKey(field)}`.toLowerCase()
  if (text.includes('状态') || text.includes('阶段') || text.includes('status') || text.includes('state')) return [{ label: '待处理', value: '待处理' }, { label: '处理中', value: '处理中' }, { label: '已完成', value: '已完成' }, { label: '已取消', value: '已取消' }]
  if (text.includes('类型') || text.includes('分类') || text.includes('category') || text.includes('type')) return [{ label: '类型一', value: '类型一' }, { label: '类型二', value: '类型二' }, { label: '类型三', value: '类型三' }]
  return [{ label: '选项一', value: '选项一' }, { label: '选项二', value: '选项二' }, { label: '选项三', value: '选项三' }]
}
function updateFieldValue(field: LooseRecord, value: unknown) {
  const key = fieldKey(field)
  if (!key) return
  emit('updateField', { fieldKey: key, value })
}
function buildFallbackRows(columns: LooseRecord[]): LooseRecord[] {
  if (!columns.length) return []
  return [0, 1, 2].map((rowIndex) => {
    const row: LooseRecord = {}
    columns.forEach((column, columnIndex) => {
      const fieldKey = String(column.fieldKey || column.prop || column.columnKey || `field_${columnIndex + 1}`)
      const label = String(column.label || column.title || fieldKey)
      const text = `${fieldKey} ${label}`.toLowerCase()
      if (text.includes('状态') || text.includes('阶段') || text.includes('status') || text.includes('state')) row[fieldKey] = ['待处理', '处理中', '已完成'][rowIndex % 3]
      else if (text.includes('日期') || text.includes('时间') || text.includes('date') || text.includes('time')) row[fieldKey] = `2026-08-${String(18 + rowIndex).padStart(2, '0')}`
      else if (text.includes('金额') || text.includes('数量') || text.includes('amount') || text.includes('count')) row[fieldKey] = (rowIndex + 1) * 10
      else if (text.includes('创建人') || text.includes('负责人') || text.includes('人员') || text.includes('user')) row[fieldKey] = ['张三', '李四', '王五'][rowIndex % 3]
      else if (text.includes('编号') || text.includes('编码') || fieldKey.toLowerCase().endsWith('id')) row[fieldKey] = `${mockCodePrefix(label)}-${String(rowIndex + 1).padStart(3, '0')}`
      else row[fieldKey] = `${label}${rowIndex + 1}`
    })
    return row
  })
}
function mockCodePrefix(label: string): string {
  const ascii = label.replace(/[^A-Za-z0-9]+/g, '').toUpperCase()
  return ascii ? ascii.slice(0, 12) : 'ITEM'
}

// A9.10.14.4.3.15.6.24J4_COLUMN_SELECT_AND_ACTION_ORDER

// A9.10.14.4.3.15.6.24J7B_TABLE_HIT_TEST_MODEL
function pickStyle(sourceStyle: LooseRecord, keys: string[]): Record<string, string> {
  const style: Record<string, string> = {}

  for (const key of keys) {
    const value = sourceStyle[key]
    if (value !== undefined && value !== null && String(value).trim()) {
      style[key] = String(value)
    }
  }

  return style
}

function columnLabelInlineStyle(column: LooseRecord): Record<string, string> {
  const key = String(column.columnKey || column.key || column.fieldKey || '').trim()
  const elementStyles = asRecord(propsOf(primaryTable.value).elementStyles)
  const sourceStyle = {
    ...asRecord(key ? elementStyles[`columnLabel:${key}`] : null),
    ...asRecord(key ? elementStyles[`text:columnLabel:${key}`] : null),
  }

  return pickStyle(sourceStyle, [
    'color',
    'backgroundColor',
    'borderColor',
    'borderRadius',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'padding',
    'textAlign',
    'display',
    'transform',
  ])
}

function cellInlineStyle(column: LooseRecord): Record<string, string> {
  const key = String(column.columnKey || column.key || column.fieldKey || '').trim()
  const elementStyles = asRecord(propsOf(primaryTable.value).elementStyles)
  const sourceStyle = {
    ...asRecord(key ? elementStyles[`cell:${key}`] : null),
    ...asRecord(key ? elementStyles[`columnCell:${key}`] : null),
  }

  return pickStyle(sourceStyle, [
    'color',
    'backgroundColor',
    'borderColor',
    'borderRadius',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'padding',
    'minWidth',
    'width',
    'textAlign',
    'display',
    'justifyContent',
    'transform',
  ])
}


// A9.10.14.4.3.15.6.24J7E_TABLE_ROW_HEIGHT_EXECUTOR
function tableCellStyle(): Record<string, string> {
  const elementStyles = asRecord(propsOf(primaryTable.value).elementStyles)
  const sourceStyle = asRecord(elementStyles.rowTemplate)
  const style = pickStyle(sourceStyle, [
    'backgroundColor',
    'color',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'height',
    'minHeight',
  ])

  const paddingBlock = String(
    sourceStyle.cellPaddingBlock ||
      sourceStyle.paddingBlock ||
      sourceStyle.paddingY ||
      '',
  ).trim()

  if (paddingBlock) {
    style.paddingTop = paddingBlock
    style.paddingBottom = paddingBlock
  }

  return style
}

function tableRowStyle(): Record<string, string> {
  const elementStyles = asRecord(propsOf(primaryTable.value).elementStyles)
  const sourceStyle = asRecord(elementStyles.rowTemplate)

  return pickStyle(sourceStyle, [
    'height',
    'minHeight',
    'backgroundColor',
    'color',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'transform',
  ])
}

function columnHeaderSelectStyle(column: LooseRecord): Record<string, string> {
  const style = columnInlineStyle(column)
  return {
    ...style,
    display: style.display || 'flex',
    alignItems: 'center',
    width: '100%',
    minHeight: '34px',
    cursor: 'crosshair',
    userSelect: 'none',
  }
}

function columnInlineStyle(column: LooseRecord): Record<string, string> {
  const key = String(column.columnKey || column.key || column.fieldKey || '').trim()
  const ownerProps = propsOf(primaryTable.value)
  const elementStyles = asRecord(ownerProps.elementStyles)
  const sourceStyle = {
    ...asRecord(column.style),
    ...asRecord(key ? elementStyles[`column:${key}`] : null),
  }
  const style: Record<string, string> = {}

  for (const item of [
    'width',
    'minWidth',
    'maxWidth',
    'display',
    'order',
    'marginLeft',
    'marginRight',
    'justifySelf',
    'alignSelf',
    'textAlign',
    'transform',
  ]) {
    const value = sourceStyle[item]
    if (value !== undefined && value !== null && String(value).trim()) {
      style[item] = String(value)
    }
  }

  return style
}


// A9.10.14.4.3.15.6.24J7D_ACTION_COLUMN_SELECTABLE
const ACTION_COLUMN_KEY = '__row_actions'

function actionColumnLabel(): string {
  const tableProps = propsOf(primaryTable.value)
  return String(tableProps.actionColumnLabel || tableProps.rowActionColumnLabel || tableProps.operationColumnLabel || '操作')
}

function actionColumnStyleSource(): LooseRecord {
  const tableProps = propsOf(primaryTable.value)
  const elementStyles = asRecord(tableProps.elementStyles)

  return {
    ...asRecord(elementStyles[`column:${ACTION_COLUMN_KEY}`]),
    ...asRecord(elementStyles.actionColumn),
  }
}

function actionColumnLabelStyleSource(): LooseRecord {
  const tableProps = propsOf(primaryTable.value)
  const elementStyles = asRecord(tableProps.elementStyles)

  return {
    ...asRecord(elementStyles[`columnLabel:${ACTION_COLUMN_KEY}`]),
    ...asRecord(elementStyles[`text:actionColumnLabel`]),
    ...asRecord(elementStyles.actionColumnLabel),
  }
}

function actionColumnWidth(): number {
  const sourceStyle = actionColumnStyleSource()
  const raw = Number(sourceStyle.width || sourceStyle.minWidth || propsOf(primaryTable.value).actionColumnWidth || 190)
  return Number.isFinite(raw) && raw > 0 ? raw : 190
}

function actionColumnHeaderStyle(): Record<string, string> {
  const sourceStyle = actionColumnStyleSource()
  const style = pickStyle(sourceStyle, [
    'color',
    'backgroundColor',
    'borderColor',
    'borderRadius',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'padding',
    'textAlign',
    'display',
    'justifyContent',
    'transform',
  ])

  return {
    ...style,
    display: style.display || 'flex',
    alignItems: 'center',
    width: '100%',
    minHeight: '34px',
    cursor: 'crosshair',
    userSelect: 'none',
  }
}

function actionColumnLabelInlineStyle(): Record<string, string> {
  return pickStyle(actionColumnLabelStyleSource(), [
    'color',
    'backgroundColor',
    'borderColor',
    'borderRadius',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'padding',
    'textAlign',
    'display',
    'transform',
  ])
}

function actionColumnCellStyle(): Record<string, string> {
  const sourceStyle = actionColumnStyleSource()

  return pickStyle(sourceStyle, [
    'color',
    'backgroundColor',
    'borderColor',
    'borderRadius',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'padding',
    'textAlign',
    'display',
    'justifyContent',
    'transform',
  ])
}

function columnWidth(column: LooseRecord): number {
  const width = Number(column.width || column.minWidth || 0)
  if (Number.isFinite(width) && width > 0) return width
  const label = String(column.label || column.columnKey || '')
  return Math.max(100, Math.min(210, label.length * 14 + 64))
}
function isTagColumn(column: LooseRecord): boolean {
  const type = String(column.type || column.renderType || '').toUpperCase()
  if (type.includes('TAG') || type.includes('STATUS')) return true
  const text = `${column.columnKey || ''} ${column.fieldKey || ''} ${column.label || ''}`.toLowerCase()
  return text.includes('status') || text.includes('state') || text.includes('状态') || text.includes('阶段')
}
function statusTagType(value: unknown): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  const text = String(value || '').toLowerCase()
  if (text.includes('失败') || text.includes('拒绝') || text.includes('删除') || text.includes('异常') || text.includes('danger') || text.includes('failed')) return 'danger'
  if (text.includes('待') || text.includes('审核') || text.includes('处理中') || text.includes('pending') || text.includes('warning')) return 'warning'
  if (text.includes('完成') || text.includes('成功') || text.includes('启用') || text.includes('通过') || text.includes('success') || text.includes('done')) return 'success'
  if (text.includes('关闭') || text.includes('取消') || text.includes('停用') || text.includes('cancel')) return 'info'
  return 'primary'
}
function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') {
    try { return JSON.stringify(value) } catch { return String(value) }
  }
  return String(value)
}
</script>

<style scoped>
.a9-admin-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  width: 100%;
  min-width: 0;
}

.a9-admin-card {
  width: 100%;
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.035);
  overflow: visible;
}

.a9-admin-card :deep(.el-card__header) {
  padding: 13px 16px;
  border-bottom: 1px solid #eef2f7;
}

.a9-admin-card :deep(.el-card__body) {
  padding: 14px 16px 16px;
}

.a9-admin-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.a9-admin-card-header > div:first-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.a9-admin-card-header strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.a9-admin-card-header span {
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.a9-admin-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px 14px;
  width: 100%;
  min-width: 0;
}

.a9-admin-form-item {
  min-width: 0;
  margin-bottom: 0;
}

.a9-admin-form :deep(.el-form-item__label) {
  height: auto;
  min-height: 18px;
  padding: 0 0 5px;
  color: #344054;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.35;
}

.a9-admin-form :deep(.el-form-item__content) {
  display: flex;
  width: 100%;
  min-width: 0;
  line-height: normal;
}

.a9-admin-control,
.a9-admin-form :deep(.el-input),
.a9-admin-form :deep(.el-select),
.a9-admin-form :deep(.el-date-editor),
.a9-admin-form :deep(.el-input-number) {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  flex: 1 1 auto;
  box-sizing: border-box;
}

.a9-admin-form :deep(.el-input__wrapper),
.a9-admin-form :deep(.el-select__wrapper),
.a9-admin-form :deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
  min-width: 0;
  height: 32px;
  min-height: 32px;
  box-sizing: border-box;
  border-radius: 6px;
  background: #ffffff;
}

.a9-admin-form :deep(.el-input__inner),
.a9-admin-form :deep(.el-select__placeholder),
.a9-admin-form :deep(.el-select__selected-item),
.a9-admin-form :deep(.el-select__input),
.a9-admin-form :deep(.el-date-editor .el-range-input) {
  font-size: 12px;
}

.a9-admin-table-card :deep(.el-card__body) {
  padding: 10px 16px 14px;
}

.a9-admin-table {
  width: 100%;
  font-size: 12px;
}

.a9-admin-table :deep(.el-table__header th) {
  height: 38px;
  background: #f8fafc;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
}

.a9-admin-table :deep(.el-table__cell) {
  padding: 8px 0;
  color: #475467;
  font-size: 12px;
}

.a9-admin-table :deep(.cell) {
  line-height: 1.45;
}

.a9-admin-table :deep(.el-button.is-link) {
  height: 24px;
  padding: 0 4px;
  font-size: 12px;
}

.a9-admin-table :deep(.el-tag) {
  height: 22px;
  padding: 0 7px;
  font-size: 11.5px;
}

:global(.a9-admin-popper) {
  z-index: 2147483600 !important;
}


/* A9.10.14.4.3.15.6.24J4_COLUMN_SELECT_AND_ACTION_ORDER */
.a9-admin-column-select-target {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 34px;
  cursor: crosshair;
}

.a9-admin-cell-select-target {
  display: block;
  width: 100%;
  min-height: 22px;
  cursor: crosshair;
}

/* A9.10.14.4.3.15.6.24J7A_TABLE_SCOPE_STABILIZE
   Table edit scope policy:
   - TABLE_COLUMN: column template target. Width/name/visibility apply to this column only.
   - TABLE_CELL: cell display target. Row-related commands should apply to row template, not one mock row.
   - TABLE_ACTION: row action template target. Same actionId in every row is updated together.
   Keep Element Plus table header layout native; do not absolute-position header target. */
.a9-admin-table :deep(.el-table__header-wrapper th) {
  height: 42px !important;
  background: #f8fafc !important;
  vertical-align: middle !important;
}

.a9-admin-table :deep(.el-table__header-wrapper th .cell) {
  position: static !important;
  display: flex !important;
  align-items: center !important;
  height: 42px !important;
  min-height: 0 !important;
  padding: 0 10px !important;
  line-height: 1.35 !important;
  box-sizing: border-box !important;
}

.a9-admin-table :deep(.el-table__header-wrapper th .a9-admin-column-select-target) {
  position: relative !important;
  inset: auto !important;
  display: flex !important;
  align-items: center !important;
  width: 100% !important;
  min-width: 0 !important;
  min-height: 0 !important;
  height: 100% !important;
  padding: 0 !important;
  line-height: 1.35 !important;
  cursor: crosshair !important;
  box-sizing: border-box !important;
  user-select: none !important;
  white-space: normal !important;
  overflow: visible !important;
}

.a9-admin-table :deep(.el-table__header-wrapper th .a9-admin-column-select-target.a9-component-inspect-locked) {
  outline-offset: -2px !important;
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.88) !important;
}

.a9-admin-table :deep(.el-table__header-wrapper th .a9-admin-column-select-target.a9-component-inspect-locked)::after,
.a9-admin-table :deep(.el-table__header-wrapper th .a9-admin-column-select-target.a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}

.a9-admin-table :deep(.a9-admin-cell-select-target) {
  display: block !important;
  width: 100% !important;
  min-height: 22px !important;
  cursor: crosshair !important;
  box-sizing: border-box !important;
}


/* A9.10.14.4.3.15.6.24J7B_TABLE_HIT_TEST_MODEL
   Hit model:
   - header text span = TABLE_COLUMN_LABEL
   - header cell outer target = TABLE_COLUMN
   - body text/tag = TABLE_CELL
   - row blank area is synthesized as TABLE_ROW by UiContractPreviewRuntime */
.a9-admin-table :deep(.a9-admin-column-label-hit) {
  position: relative !important;
  z-index: 2 !important;
  display: inline-flex !important;
  align-items: center !important;
  max-width: 100% !important;
  min-height: 22px !important;
  padding: 1px 3px !important;
  border-radius: 5px !important;
  cursor: text !important;
  box-sizing: border-box !important;
}

.a9-admin-table :deep(.a9-admin-column-label-hit:hover) {
  background: rgba(37, 99, 235, 0.08) !important;
}

.a9-admin-table :deep(.a9-admin-column-label-hit.a9-component-inspect-locked) {
  outline: 2px solid rgba(37, 99, 235, 0.98) !important;
  outline-offset: 1px !important;
  box-shadow: none !important;
}

.a9-admin-table :deep(.a9-admin-column-label-hit.a9-component-inspect-locked)::after,
.a9-admin-table :deep(.a9-admin-column-label-hit.a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}

.a9-admin-table :deep(.el-table__body tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-hover > td) {
  box-shadow: inset 0 0 0 1px rgba(14, 165, 233, 0.88) !important;
}

.a9-admin-table :deep(.el-table__body tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked > td) {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.92) !important;
}

.a9-admin-table :deep(.el-table__body tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked)::after,
.a9-admin-table :deep(.el-table__body tr[data-ui-element-kind="TABLE_ROW"].a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}


/* A9.10.14.4.3.15.6.24J7D_ACTION_COLUMN_SELECTABLE
   Make the fixed row-action column selectable as a column/header,
   while keeping each row button selectable as TABLE_ACTION. */
.a9-admin-table :deep(.a9-admin-action-column-select-target) {
  justify-content: flex-start !important;
}

.a9-admin-table :deep(.a9-admin-action-cell-select-target) {
  min-height: 28px !important;
  width: 100% !important;
  cursor: crosshair !important;
  box-sizing: border-box !important;
}

.a9-admin-table :deep(.a9-admin-action-cell-select-target.a9-component-inspect-locked) {
  outline: 2px solid rgba(37, 99, 235, 0.92) !important;
  outline-offset: -2px !important;
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.14) !important;
}

.a9-admin-table :deep(.a9-admin-action-cell-select-target.a9-component-inspect-locked)::after,
.a9-admin-table :deep(.a9-admin-action-cell-select-target.a9-component-inspect-locked)::before {
  display: none !important;
  content: none !important;
}


/* A9.10.14.4.3.15.6.24J7E_TABLE_ROW_HEIGHT_EXECUTOR
   Row template height is applied through ElTable row-style and cell-style.
   td padding/height makes visual row-height changes obvious. */
.a9-admin-table :deep(.el-table__body tr[data-ui-element-kind="TABLE_ROW"] > td),
.a9-admin-table :deep(.el-table__body tr > td) {
  transition: height .16s ease, padding .16s ease, background-color .16s ease !important;
}

.a9-admin-table :deep(.el-table__body tr.a9-component-inspect-locked > td) {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.92) !important;
}


/* A9.10.14.4.3.15.6.24J7F_MOCK_CELL_NOT_SELECTABLE
   Mock row values are demo data, not editable prototype structure.
   Do not show picker cursor on generated table data text. */
.a9-admin-table :deep(.a9-admin-cell-mock-value) {
  cursor: default !important;
  user-select: text !important;
}

.a9-admin-table :deep(.a9-admin-cell-content) {
  display: inline-block !important;
  max-width: 100% !important;
}

</style>
