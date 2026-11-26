<template>
  <LegacyUiContractRuntimeNode
    v-if="shouldUseLegacyRuntime"
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

  <section
    v-else-if="isVisible"
    class="ep-runtime-node"
    :class="nodeClassList"
    data-ui-selectable="true"
    data-ui-element-kind="COMPONENT"
    :data-ui-component-id="componentId"
    :data-ui-component-type="componentType"
    :data-ui-element-label="componentTitle || componentDescription || componentId"
  >
    <template v-if="isContainerNode">
      <section class="ep-container-panel">
        <header v-if="shouldRenderContainerHeader" class="ep-container-header">
          <div>
            <strong
              v-if="componentTitle"
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="componentId"
              :data-ui-component-type="componentType"
              :data-ui-element-label="componentTitle"
            >
              {{ componentTitle }}
            </strong>
            <span
              v-if="componentDescription"
              data-ui-selectable="true"
              data-ui-element-kind="DESCRIPTION"
              data-ui-text-key="description"
              :data-ui-component-id="componentId"
              :data-ui-component-type="componentType"
              :data-ui-element-label="componentDescription"
            >
              {{ componentDescription }}
            </span>
          </div>

          <ElSpace v-if="actionIds.length" wrap>
            <ElButton
              v-for="actionId in actionIds"
              :key="actionId"
              :type="actionButtonType(actionId)"
              :disabled="isDisabled || actionDisabled(actionId)"
              v-show="!actionHidden(actionId)"
              data-ui-selectable="true"
              data-ui-element-kind="ACTION"
              :data-ui-component-id="componentId"
              :data-ui-component-type="componentType"
              :data-ui-action-id="actionId"
              :data-ui-element-label="actionLabel(actionId)"
              @click="emitAction(actionId)"
            >
              {{ actionLabel(actionId) }}
            </ElButton>
          </ElSpace>
        </header>

        <div v-if="children.length" class="ep-children-grid">
          <ElementPlusUiContractRuntimeNode
            v-for="child in children"
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

        <ElEmpty v-else description="暂无内容" />
      </section>
    </template>

    <template v-else-if="isFormNode">
      <ElCard class="ep-node-card ep-form-card" shadow="never">
        <template #header>
          <div class="ep-card-header">
            <div>
              <strong
                data-ui-selectable="true"
                data-ui-element-kind="TITLE"
                data-ui-text-key="title"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentTitle || '查询条件'"
              >
                {{ componentTitle || defaultFormTitle }}
              </strong>
              <span
                v-if="componentDescription"
                data-ui-selectable="true"
                data-ui-element-kind="DESCRIPTION"
                data-ui-text-key="description"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentDescription"
              >
                {{ componentDescription }}
              </span>
            </div>

            <ElSpace v-if="actionIds.length" wrap>
              <ElButton
                v-for="actionId in actionIds"
                :key="actionId"
                :type="actionButtonType(actionId)"
                :disabled="isDisabled || actionDisabled(actionId)"
                v-show="!actionHidden(actionId)"
                data-ui-selectable="true"
                data-ui-element-kind="ACTION"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-action-id="actionId"
                :data-ui-element-label="actionLabel(actionId)"
                @click="emitAction(actionId)"
              >
                {{ actionLabel(actionId) }}
              </ElButton>
            </ElSpace>
          </div>
        </template>

        <ElForm class="ep-form" label-position="top" :model="formValues">
          <ElRow :gutter="16">
            <ElCol
              v-for="field in visibleFields"
              :key="fieldKey(field)"
              :span="fieldSpan(field)"
            >
              <ElFormItem
                :label="fieldLabel(field)"
                :error="fieldError(field)"
                data-ui-selectable="true"
                data-ui-element-kind="FIELD"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-field-key="fieldKey(field)"
                :data-ui-element-label="fieldLabel(field)"
              >
                <ElSelect
                  v-if="fieldControlKind(field) === 'SELECT'"
                  class="ep-control"
                  :model-value="fieldStringValue(field)"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  clearable
                  :teleported="true"
                  popper-class="ep-runtime-select-popper"
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
                  class="ep-control"
                  :model-value="fieldAnyValue(field)"
                  type="date"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  :teleported="true"
                  popper-class="ep-runtime-picker-popper"
                  @update:model-value="updateFieldValue(field, $event)"
                />

                <ElDatePicker
                  v-else-if="fieldControlKind(field) === 'DATERANGE'"
                  class="ep-control"
                  :model-value="fieldAnyValue(field)"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  :teleported="true"
                  popper-class="ep-runtime-picker-popper"
                  @update:model-value="updateFieldValue(field, $event)"
                />

                <ElSwitch
                  v-else-if="fieldControlKind(field) === 'SWITCH'"
                  :model-value="fieldBooleanValue(field)"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  @update:model-value="updateFieldValue(field, $event)"
                />

                <ElInputNumber
                  v-else-if="fieldControlKind(field) === 'NUMBER'"
                  class="ep-control"
                  :model-value="fieldNumberValue(field)"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  @update:model-value="updateFieldValue(field, $event)"
                />

                <ElInput
                  v-else
                  class="ep-control"
                  :model-value="fieldStringValue(field)"
                  :type="fieldControlKind(field) === 'TEXTAREA' ? 'textarea' : 'text'"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="isDisabled || fieldIsDisabled(field)"
                  clearable
                  @update:model-value="updateFieldValue(field, $event)"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </ElCard>
    </template>

    <template v-else-if="isTableNode">
      <ElCard class="ep-node-card ep-table-card" shadow="never">
        <template #header>
          <div class="ep-card-header">
            <div>
              <strong
                data-ui-selectable="true"
                data-ui-element-kind="TITLE"
                data-ui-text-key="title"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentTitle || defaultTableTitle"
              >
                {{ componentTitle || defaultTableTitle }}
              </strong>
              <span
                v-if="componentDescription"
                data-ui-selectable="true"
                data-ui-element-kind="DESCRIPTION"
                data-ui-text-key="description"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentDescription"
              >
                {{ componentDescription }}
              </span>
            </div>

            <ElSpace v-if="tableHeaderActionIds.length" wrap>
              <ElButton
                v-for="actionId in tableHeaderActionIds"
                :key="actionId"
                :type="actionButtonType(actionId)"
                :disabled="isDisabled || actionDisabled(actionId)"
                v-show="!actionHidden(actionId)"
                data-ui-selectable="true"
                data-ui-element-kind="ACTION"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-action-id="actionId"
                :data-ui-element-label="actionLabel(actionId)"
                @click="emitAction(actionId)"
              >
                {{ actionLabel(actionId) }}
              </ElButton>
            </ElSpace>
          </div>
        </template>

        <ElTable
          class="ep-table"
          :data="tableRows"
          border
          stripe
          size="small"
          table-layout="auto"
        >
          <ElTableColumn
            v-for="column in renderColumns"
            :key="column.columnKey"
            :prop="column.fieldKey"
            :min-width="columnWidth(column)"
          >
            <template #header>
              <span
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_COLUMN"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="column.label || column.columnKey"
              >
                {{ column.label || column.columnKey }}
              </span>
            </template>

            <template #default="{ row }">
              <ElTag
                v-if="isTagColumn(column)"
                :type="statusTagType(row[column.fieldKey])"
                effect="light"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_CELL"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="column.label || column.columnKey"
              >
                {{ displayValue(row[column.fieldKey]) }}
              </ElTag>

              <span
                v-else
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_CELL"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="column.label || column.columnKey"
              >
                {{ displayValue(row[column.fieldKey]) }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn
            v-if="rowActionIds.length"
            label="操作"
            fixed="right"
            width="196"
          >
            <template #default="{ row }">
              <ElSpace wrap>
                <ElButton
                  v-for="actionId in rowActionIds"
                  :key="actionId"
                  link
                  type="primary"
                  :disabled="isDisabled || actionDisabled(actionId)"
                  v-show="!actionHidden(actionId)"
                  data-ui-selectable="true"
                  data-ui-element-kind="TABLE_ACTION"
                  :data-ui-component-id="componentId"
                  :data-ui-component-type="componentType"
                  :data-ui-action-id="actionId"
                  :data-ui-element-label="actionLabel(actionId)"
                  @click="emitAction(actionId, row)"
                >
                  {{ actionLabel(actionId) }}
                </ElButton>
              </ElSpace>
            </template>
          </ElTableColumn>

          <template #empty>
            <ElEmpty description="暂无数据" />
          </template>
        </ElTable>
      </ElCard>
    </template>

    <template v-else-if="isStatsNode">
      <ElRow :gutter="16" class="ep-stats-row">
        <ElCol
          v-for="(item, index) in statsItems"
          :key="itemDataKey(item, index)"
          :span="statsSpan"
        >
          <ElCard
            class="ep-stat-card"
            shadow="never"
            data-ui-selectable="true"
            data-ui-element-kind="METRIC"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-field-key="itemFieldKey(item, index)"
            :data-ui-element-label="String(item.label || item.title || item.key || index)"
          >
            <ElStatistic
              :title="String(item.label || item.title || '-')"
              :value="displayValue(item.value ?? item.count ?? item.amount ?? '-')"
            />
            <span v-if="item.hint || item.trend || item.description">
              {{ item.hint || item.trend || item.description }}
            </span>
          </ElCard>
        </ElCol>

        <ElCol v-if="!statsItems.length" :span="24">
          <ElEmpty description="暂无统计数据" />
        </ElCol>
      </ElRow>
    </template>

    <template v-else-if="isActionNode">
      <section
        class="ep-action-toolbar"
        data-ui-selectable="true"
        data-ui-element-kind="ACTION_BAR"
        :data-ui-component-id="componentId"
        :data-ui-component-type="componentType"
        :data-ui-element-label="componentTitle || '操作区'"
      >
        <ElSpace wrap>
          <ElButton
            v-for="actionId in actionIds"
            :key="actionId"
            :type="actionButtonType(actionId)"
            :disabled="isDisabled || actionDisabled(actionId)"
            v-show="!actionHidden(actionId)"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </ElButton>

          <ElButton
            v-if="!actionIds.length"
            type="primary"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentTitle || '操作'"
            @click="emitAction(componentId || 'action')"
          >
            {{ componentTitle || '操作' }}
          </ElButton>
        </ElSpace>
      </section>
    </template>

    <template v-else-if="isDialogNode">
      <ElCard class="ep-node-card ep-dialog-card" shadow="always">
        <template #header>
          <div class="ep-card-header">
            <div>
              <strong
                data-ui-selectable="true"
                data-ui-element-kind="TITLE"
                data-ui-text-key="title"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentTitle || '弹窗'"
              >
                {{ componentTitle || defaultDialogTitle }}
              </strong>
              <span
                data-ui-selectable="true"
                data-ui-element-kind="DESCRIPTION"
                data-ui-text-key="description"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-element-label="componentDescription || textProp('message') || '弹窗内容'"
              >
                {{ componentDescription || textProp('message') || '这里展示弹窗内容、表单或二次确认信息。' }}
              </span>
            </div>
          </div>
        </template>

        <div v-if="children.length" class="ep-children-grid">
          <ElementPlusUiContractRuntimeNode
            v-for="child in children"
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

        <ElDivider />

        <ElSpace class="ep-dialog-actions" wrap>
          <ElButton
            v-for="actionId in actionIds"
            :key="actionId"
            :type="actionButtonType(actionId)"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </ElButton>
        </ElSpace>
      </ElCard>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElCol,
  ElDatePicker,
  ElDivider,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRow,
  ElSelect,
  ElSpace,
  ElStatistic,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import LegacyUiContractRuntimeNode from '../UiContractRuntimeNode.vue'
import type {
  UiContractComponent,
  UiContractInteraction,
} from '../../types/uiContract'

defineOptions({
  name: 'ElementPlusUiContractRuntimeNode',
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

type LooseRecord = Record<string, any>

const rawComponent = computed<LooseRecord>(() => asRecord(props.component))

const componentId = computed(() =>
  String(rawComponent.value.componentId || rawComponent.value.id || '').trim(),
)

const componentType = computed(() =>
  String(rawComponent.value.type || rawComponent.value.componentType || '').trim().toUpperCase(),
)

const componentTitle = computed(() =>
  String(rawComponent.value.title || rawComponent.value.label || textProp('title') || '').trim(),
)

const componentDescription = computed(() =>
  String(rawComponent.value.description || rawComponent.value.subtitle || textProp('description') || '').trim(),
)

const componentProps = computed<LooseRecord>(() =>
  asRecord(rawComponent.value.props || rawComponent.value.properties || {}),
)

const children = computed<UiContractComponent[]>(() =>
  arrayValue(rawComponent.value.children).filter(isObjectValue) as UiContractComponent[],
)

const actionIds = computed<string[]>(() =>
  uniqueStrings([
    ...normalizeActionIds(rawComponent.value.actionIds),
    ...normalizeActionIds(componentProps.value.actionIds),
    ...normalizeActionIds(componentProps.value.actions),
  ]),
)

const elementPlusSupportedTypes = new Set([
  'SECTION',
  'CARD',
  'CONTAINER',
  'GROUP',
  'PANEL',
  'PAGE_SECTION',
  'CONTENT_BLOCK',
  'LAYOUT',
  'FORM',
  'FIELD_GROUP',
  'FILTER_FORM',
  'SEARCH_FORM',
  'QUERY_FORM',
  'ADVANCED_FILTER',
  'CREATE_FORM',
  'EDIT_FORM',
  'TABLE',
  'DATA_TABLE',
  'LIST_TABLE',
  'RESULT_TABLE',
  'GRID_TABLE',
  'STATS',
  'STAT_GRID',
  'KPI',
  'KPI_CARD',
  'METRIC_GRID',
  'METRICS',
  'ACTION_BAR',
  'TOOLBAR',
  'BUTTON_GROUP',
  'ACTIONS',
  'BUTTON',
  'PRIMARY_ACTION',
  'LINK',
  'DIALOG',
  'MODAL',
  'CONFIRM_DIALOG',
  'CONFIRM_MODAL',
  'DRAWER',
  'SIDE_PANEL',
  'DETAIL_DRAWER',
])

const shouldUseLegacyRuntime = computed(() => {
  if (!componentType.value) return true
  return !elementPlusSupportedTypes.has(componentType.value)
})

const isVisible = computed(() => {
  if (!componentId.value) return true
  if (!props.visibleComponentIds.length) return true
  return props.visibleComponentIds.includes(componentId.value)
})

const isDisabled = computed(() =>
  Boolean(componentId.value && props.disabledComponentIds.includes(componentId.value)),
)

const nodeClassList = computed(() => [
  `ep-node-${componentType.value.toLowerCase() || 'generic'}`,
  {
    'is-disabled': isDisabled.value,
  },
])

const hasHeader = computed(() => Boolean(componentTitle.value || componentDescription.value))

// A9.10.14.4.3.15.6.24D3_CONTAINER_HEADER_TABLE_FONT
// SECTION / CONTAINER / LAYOUT usually only group real business components.
// Showing their title creates duplicate headings such as:
// 查询条件 -> 查询条件 card, 数据列表 -> 数据列表 table.
// Keep container header only when it has actions or when it is a true CARD/PANEL
// without children.
const shouldRenderContainerHeader = computed(() => {
  if (actionIds.value.length > 0) return true
  if (!hasHeader.value) return false

  if (isComponentType('CARD', 'PANEL')) {
    return children.value.length === 0
  }

  return false
})

const isFormNode = computed(() =>
  isComponentType('FORM', 'FIELD_GROUP', 'FILTER_FORM', 'SEARCH_FORM', 'QUERY_FORM', 'ADVANCED_FILTER', 'CREATE_FORM', 'EDIT_FORM'),
)

const isTableNode = computed(() =>
  isComponentType('TABLE', 'DATA_TABLE', 'LIST_TABLE', 'RESULT_TABLE', 'GRID_TABLE'),
)

const isStatsNode = computed(() =>
  isComponentType('STATS', 'STAT_GRID', 'KPI', 'KPI_CARD', 'METRIC_GRID', 'METRICS'),
)

const isActionNode = computed(() =>
  isComponentType('ACTION_BAR', 'TOOLBAR', 'BUTTON_GROUP', 'ACTIONS', 'BUTTON', 'PRIMARY_ACTION', 'LINK'),
)

const isDialogNode = computed(() =>
  isComponentType('DIALOG', 'MODAL', 'CONFIRM_DIALOG', 'CONFIRM_MODAL', 'DRAWER', 'SIDE_PANEL', 'DETAIL_DRAWER'),
)

const isContainerNode = computed(() =>
  isComponentType('SECTION', 'CARD', 'CONTAINER', 'GROUP', 'PANEL', 'PAGE_SECTION', 'CONTENT_BLOCK', 'LAYOUT'),
)

const defaultFormTitle = computed(() =>
  componentType.value.includes('FILTER') || componentType.value.includes('QUERY') || componentType.value.includes('SEARCH')
    ? '查询条件'
    : '表单信息',
)

const defaultTableTitle = computed(() => '数据列表')
const defaultDialogTitle = computed(() =>
  componentType.value.includes('DRAWER') || componentType.value.includes('PANEL') ? '详情面板' : '弹窗',
)

const visibleFields = computed<LooseRecord[]>(() =>
  arrayValue(
    rawComponent.value.fields ||
      componentProps.value.fields ||
      componentProps.value.formFields ||
      componentProps.value.controls,
  )
    .map(asRecord)
    .filter((field) => field.hidden !== true && field.type !== 'HIDDEN'),
)

const tableRows = computed<LooseRecord[]>(() => {
  const dataKey = String(
    componentProps.value.dataKey ||
      componentProps.value.mockDataKey ||
      rawComponent.value.dataKey ||
      componentId.value ||
      '',
  ).trim()

  const candidates = [
    componentProps.value.rows,
    componentProps.value.data,
    rawComponent.value.rows,
    rawComponent.value.data,
    dataKey ? props.mockData[dataKey] : null,
    componentId.value ? props.mockData[componentId.value] : null,
    props.mockData.rows,
    props.mockData.list,
    props.mockData.tableRows,
  ]

  for (const candidate of candidates) {
    const rows = arrayValue(candidate).map(asRecord).filter((row) => Object.keys(row).length)
    if (rows.length) return rows
  }

  const fallbackColumns = arrayValue(
    rawComponent.value.columns ||
      componentProps.value.columns ||
      componentProps.value.tableColumns,
  )
    .map(asRecord)
    .filter((column) => Object.keys(column).length)

  return buildFallbackRows(fallbackColumns)
})

const renderColumns = computed<LooseRecord[]>(() => {
  const columns = arrayValue(
    rawComponent.value.columns ||
      componentProps.value.columns ||
      componentProps.value.tableColumns,
  )
    .map(asRecord)
    .filter((column) => Object.keys(column).length)

  if (columns.length) {
    return columns.map((column, index) => {
      const key = String(column.columnKey || column.fieldKey || column.prop || column.key || `column_${index + 1}`)
      return {
        ...column,
        columnKey: key,
        fieldKey: String(column.fieldKey || column.prop || key),
        label: String(column.label || column.title || key),
      }
    })
  }

  const firstRow = tableRows.value[0]
  if (!firstRow) return []

  return Object.keys(firstRow).slice(0, 8).map((key) => ({
    columnKey: key,
    fieldKey: key,
    label: key,
  }))
})

const tableHeaderActionIds = computed(() =>
  actionIds.value.filter((actionId) => {
    const label = actionLabel(actionId)
    const text = `${actionId} ${label}`.toLowerCase()

    return !(
      text.includes('view') ||
      text.includes('detail') ||
      text.includes('edit') ||
      text.includes('delete') ||
      text.includes('remove') ||
      text.includes('cancel') ||
      text.includes('row') ||
      ['查看', '详情', '编辑', '删除', '取消'].includes(label)
    )
  }),
)

const rowActionIds = computed(() => {
  const explicit = uniqueStrings([
    ...normalizeActionIds(componentProps.value.rowActionIds),
    ...normalizeActionIds(componentProps.value.rowActions),
    ...normalizeActionIds(rawComponent.value.rowActionIds),
  ])

  if (explicit.length) return explicit

  return actionIds.value.filter((actionId) => !tableHeaderActionIds.value.includes(actionId))
})

const statsItems = computed<LooseRecord[]>(() =>
  arrayValue(
    componentProps.value.items ||
      componentProps.value.metrics ||
      rawComponent.value.items ||
      rawComponent.value.metrics ||
      props.mockData[`${componentId.value}:metrics`],
  )
    .map(asRecord)
    .filter((item) => Object.keys(item).length),
)

const statsSpan = computed(() => {
  const count = Math.max(1, statsItems.value.length)
  if (count <= 2) return 12
  if (count === 3) return 8
  return 6
})

function isComponentType(...types: string[]): boolean {
  return types.map((type) => type.toUpperCase()).includes(componentType.value)
}

function asRecord(value: unknown): LooseRecord {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as LooseRecord
    : {}
}

function isObjectValue(value: unknown): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

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

function normalizeActionIds(value: unknown): string[] {
  if (!Array.isArray(value)) return []

  return value
    .map((item) => {
      if (typeof item === 'string') return item.trim()

      const record = asRecord(item)
      return String(record.actionId || record.id || record.key || '').trim()
    })
    .filter(Boolean)
}

function findAction(actionId?: unknown): LooseRecord | null {
  const id = String(actionId || '').trim()
  if (!id) return null

  return (props.interactions.find((item: UiContractInteraction) => {
    const record = item as unknown as LooseRecord
    return String(record.actionId || '') === id
  }) as unknown as LooseRecord) || null
}

function actionLabel(actionId: unknown): string {
  const id = String(actionId || '').trim()
  const action = findAction(id)

  return String(
    action?.label ||
      action?.title ||
      action?.name ||
      inferActionLabel(id) ||
      id ||
      '操作',
  )
}

function inferActionLabel(actionId: string): string {
  const text = actionId.toLowerCase()

  if (text.includes('create') || text.includes('add') || text.includes('new')) return '新增'
  if (text.includes('save') || text.includes('submit')) return '保存'
  if (text.includes('search') || text.includes('query')) return '查询'
  if (text.includes('reset')) return '重置'
  if (text.includes('edit') || text.includes('update')) return '编辑'
  if (text.includes('detail') || text.includes('view')) return '查看'
  if (text.includes('delete') || text.includes('remove')) return '删除'
  if (text.includes('cancel')) return '取消'
  if (text.includes('export')) return '导出'
  if (text.includes('import')) return '导入'

  return ''
}

function actionButtonType(actionId: unknown): any {
  const label = actionLabel(actionId)
  const text = `${actionId || ''} ${label}`.toLowerCase()

  if (text.includes('delete') || text.includes('remove') || label.includes('删除')) return 'danger'
  if (text.includes('cancel') || label.includes('取消')) return 'warning'
  if (
    text.includes('submit') ||
    text.includes('save') ||
    text.includes('create') ||
    text.includes('add') ||
    label.includes('保存') ||
    label.includes('新增') ||
    label.includes('提交')
  ) return 'primary'
  if (text.includes('export') || label.includes('导出')) return 'success'

  return undefined
}

function actionDisabled(actionId: unknown): boolean {
  return Boolean(findAction(actionId)?.disabled)
}

function actionHidden(actionId: unknown): boolean {
  return Boolean(findAction(actionId)?.hidden)
}

function emitAction(actionId: string, row?: LooseRecord) {
  emit('execute', {
    actionId,
    componentId: componentId.value,
    row,
  })
}

function fieldKey(field: LooseRecord): string {
  return String(field.fieldKey || field.key || field.name || '').trim()
}

function fieldLabel(field: LooseRecord): string {
  return String(field.label || field.title || field.name || fieldKey(field) || '字段')
}

function fieldPlaceholder(field: LooseRecord): string {
  return String(field.placeholder || `请输入${fieldLabel(field)}`)
}

function fieldRawValue(field: LooseRecord): unknown {
  const key = fieldKey(field)
  if (!key) return field.defaultValue ?? field.value ?? ''

  return props.formValues[key] ?? field.value ?? field.defaultValue ?? ''
}

function fieldAnyValue(field: LooseRecord): any {
  return fieldRawValue(field) as any
}

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

function fieldBooleanValue(field: LooseRecord): boolean {
  return Boolean(fieldRawValue(field))
}

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
  const explicitOptions = arrayValue(field.options || field.enumValues || field.values)
    .map((option, index) => {
      if (typeof option === 'string') {
        return { label: option, value: option }
      }

      const record = asRecord(option)
      const value = String(record.value || record.key || record.id || record.label || index)
      const label = String(record.label || record.name || record.title || value)

      return { label, value }
    })
    .filter((option) => option.label && option.value)

  if (explicitOptions.length) {
    return explicitOptions
  }

  const label = fieldLabel(field)
  const key = fieldKey(field)
  const text = `${label} ${key}`.toLowerCase()

  if (text.includes('状态') || text.includes('阶段') || text.includes('status') || text.includes('state')) {
    return [
      { label: '待处理', value: '待处理' },
      { label: '处理中', value: '处理中' },
      { label: '已完成', value: '已完成' },
      { label: '已取消', value: '已取消' },
    ]
  }

  if (text.includes('类型') || text.includes('分类') || text.includes('category') || text.includes('type')) {
    return [
      { label: '类型一', value: '类型一' },
      { label: '类型二', value: '类型二' },
      { label: '类型三', value: '类型三' },
    ]
  }

  if (text.includes('等级') || text.includes('级别') || text.includes('level') || text.includes('priority')) {
    return [
      { label: '高', value: '高' },
      { label: '中', value: '中' },
      { label: '低', value: '低' },
    ]
  }

  return [
    { label: '选项一', value: '选项一' },
    { label: '选项二', value: '选项二' },
    { label: '选项三', value: '选项三' },
  ]
}

function fieldSpan(field: LooseRecord): number {
  const span = Number(field.span || field.gridSpan || field.colSpan || 6)
  if (Number.isFinite(span) && span > 0 && span <= 24) return span

  return 6
}

function fieldIsDisabled(field: LooseRecord): boolean {
  return Boolean(field.disabled || field.readonly || field.readOnly)
}

function updateFieldValue(field: LooseRecord, value: unknown) {
  const key = fieldKey(field)
  if (!key) return

  emit('updateField', {
    fieldKey: key,
    value,
  })
}


function buildFallbackRows(columns: LooseRecord[]): LooseRecord[] {
  if (!columns.length) return []

  return [0, 1, 2].map((rowIndex) => {
    const row: LooseRecord = {}

    columns.forEach((column, columnIndex) => {
      const fieldKey = String(column.fieldKey || column.prop || column.columnKey || `field_${columnIndex + 1}`)
      const label = String(column.label || column.title || fieldKey)
      const text = `${fieldKey} ${label}`.toLowerCase()

      if (text.includes('状态') || text.includes('阶段') || text.includes('status') || text.includes('state')) {
        row[fieldKey] = ['待处理', '处理中', '已完成'][rowIndex % 3]
      } else if (text.includes('日期') || text.includes('时间') || text.includes('date') || text.includes('time')) {
        row[fieldKey] = `2026-08-${String(18 + rowIndex).padStart(2, '0')}`
      } else if (text.includes('金额') || text.includes('数量') || text.includes('amount') || text.includes('count')) {
        row[fieldKey] = (rowIndex + 1) * 10
      } else if (text.includes('编号') || text.includes('编码') || fieldKey.toLowerCase().endsWith('id')) {
        row[fieldKey] = `${normalizeMockCodePrefix(label)}-${String(rowIndex + 1).padStart(3, '0')}`
      } else if (text.includes('创建人') || text.includes('负责人') || text.includes('人员') || text.includes('user')) {
        row[fieldKey] = ['张三', '李四', '王五'][rowIndex % 3]
      } else {
        row[fieldKey] = `${label}${rowIndex + 1}`
      }
    })

    return row
  })
}

function normalizeMockCodePrefix(label: string): string {
  const ascii = label
    .replace(/[^A-Za-z0-9]+/g, '')
    .toUpperCase()

  if (ascii) return ascii.slice(0, 12)

  return 'ITEM'
}

function columnWidth(column: LooseRecord): string | number {
  const width = column.width || column.minWidth
  if (width) return width

  const label = String(column.label || column.columnKey || '')
  return Math.max(120, Math.min(260, label.length * 18 + 72))
}

function isTagColumn(column: LooseRecord): boolean {
  const type = String(column.type || column.renderType || '').toUpperCase()
  if (type.includes('TAG') || type.includes('STATUS')) return true

  const text = `${column.columnKey || ''} ${column.fieldKey || ''} ${column.label || ''}`.toLowerCase()
  return text.includes('status') || text.includes('state') || text.includes('状态') || text.includes('阶段')
}

function statusTagType(value: unknown): any {
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
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }

  return String(value)
}

function itemDataKey(item: LooseRecord, index: number): string {
  return String(item.key || item.id || item.label || item.title || index)
}

function itemFieldKey(item: LooseRecord, index: number): string {
  return String(item.fieldKey || item.key || item.id || index)
}

function textProp(key: string): string {
  return String(componentProps.value[key] || '')
}
</script>

<style scoped>
.ep-runtime-node {
  min-width: 0;
}

.ep-runtime-node + .ep-runtime-node {
  margin-top: 14px;
}

.ep-node-card {
  border-radius: 14px;
  border-color: #e5e7eb;
  background: #ffffff;
}

.ep-container-card {
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.ep-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ep-card-header > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ep-card-header strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.4;
}

.ep-card-header span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.ep-children-grid {
  display: grid;
  gap: 14px;
}

.ep-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.ep-control {
  width: 100%;
}

.ep-table-card :deep(.el-card__body) {
  padding-top: 12px;
}

.ep-table {
  width: 100%;
}

.ep-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

.ep-stats-row {
  width: 100%;
}

.ep-stat-card {
  border-radius: 14px;
  border-color: #e5e7eb;
  min-height: 112px;
}

.ep-stat-card span {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.ep-action-card :deep(.el-card__body) {
  display: flex;
  justify-content: flex-end;
}

.ep-dialog-card {
  max-width: 760px;
  margin: 0 auto;
  border-color: #dbeafe;
}

.ep-dialog-actions {
  display: flex;
  justify-content: flex-end;
  width: 100%;
}

.is-disabled {
  opacity: 0.72;
}

/* A9.10.14.4.3.15.6.24D_RUNTIME_LAYOUT_DATA_FALLBACK */
.ep-container-panel {
  min-width: 0;
  width: 100%;
}

.ep-container-panel > .ep-container-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 0 0 12px;
}

.ep-container-panel > .ep-container-header > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ep-container-panel > .ep-container-header strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.4;
}

.ep-container-panel > .ep-container-header span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.ep-form {
  width: 100%;
  min-width: 0;
}

.ep-form :deep(.el-row) {
  width: 100%;
  min-width: 0;
}

.ep-form :deep(.el-col) {
  min-width: 0;
}

.ep-control,
.ep-control :deep(.el-input),
.ep-control :deep(.el-select),
.ep-control :deep(.el-date-editor),
.ep-control :deep(.el-input__wrapper) {
  width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box;
}

.ep-table-card {
  overflow: hidden;
}

.ep-table-card :deep(.el-card__body) {
  overflow-x: auto;
}

.ep-table :deep(.el-table__empty-block) {
  min-height: 220px;
}



/* A9.10.14.4.3.15.6.24D2_RUNTIME_VISUAL_RATIO */
.ep-runtime-node {
  width: 100%;
  box-sizing: border-box;
}

.ep-children-grid {
  gap: 18px;
}

.ep-node-card {
  border-radius: 12px !important;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04) !important;
}

.ep-node-card :deep(.el-card__header) {
  padding: 16px 20px !important;
}

.ep-node-card :deep(.el-card__body) {
  padding: 18px 20px !important;
}

.ep-form-card,
.ep-table-card {
  width: 100%;
  max-width: 100%;
  overflow: visible;
}

.ep-form :deep(.el-form-item__label) {
  color: #374151;
  font-size: 13px;
  line-height: 1.3;
  margin-bottom: 6px;
}

.ep-form :deep(.el-input),
.ep-form :deep(.el-select),
.ep-form :deep(.el-date-editor) {
  width: 100% !important;
  max-width: 100% !important;
}

.ep-form :deep(.el-input__wrapper) {
  width: 100% !important;
  box-sizing: border-box;
}

.ep-table-card :deep(.el-card__body) {
  padding: 12px 20px 20px !important;
}

.ep-table :deep(.el-table__cell) {
  padding: 11px 0;
}

.ep-table :deep(.el-table__header th) {
  height: 44px;
}

:global(.ep-runtime-select-popper),
:global(.ep-runtime-picker-popper),
:global(.el-picker__popper),
:global(.el-select__popper) {
  z-index: 2147483600 !important;
}


/* A9.10.14.4.3.15.6.24D3_CONTAINER_HEADER_TABLE_FONT */
.ep-container-panel > .ep-container-header {
  display: none;
}

.ep-table {
  font-size: 12.5px;
}

.ep-table :deep(.el-table__header th) {
  font-size: 12.5px !important;
  font-weight: 600 !important;
  color: #475569 !important;
  height: 40px !important;
}

.ep-table :deep(.el-table__cell) {
  font-size: 12.5px !important;
  color: #475569;
  padding: 9px 0 !important;
}

.ep-table :deep(.cell) {
  line-height: 1.45;
}

.ep-table :deep(.el-button.is-link) {
  font-size: 12.5px;
  padding: 2px 4px;
}

.ep-card-header strong {
  font-size: 14px !important;
}

.ep-card-header span {
  font-size: 12px !important;
}

.ep-form :deep(.el-form-item__label) {
  font-size: 12.5px !important;
}

.ep-form :deep(.el-input__inner),
.ep-form :deep(.el-select__placeholder),
.ep-form :deep(.el-date-editor .el-range-input) {
  font-size: 12.5px !important;
}


/* A9.10.14.4.3.15.6.24D4_ADMIN_DENSITY_POPPER */
.ep-node-card {
  border-radius: 10px !important;
  border-color: #e6eaf0 !important;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.035) !important;
}

.ep-node-card :deep(.el-card__header) {
  padding: 14px 18px !important;
}

.ep-node-card :deep(.el-card__body) {
  padding: 16px 18px !important;
}

.ep-card-header strong {
  font-size: 13.5px !important;
  font-weight: 700 !important;
}

.ep-card-header span {
  font-size: 12px !important;
  color: #667085 !important;
}

.ep-form {
  width: 100%;
  overflow: visible;
}

.ep-form :deep(.el-row) {
  width: 100%;
  margin-left: -8px !important;
  margin-right: -8px !important;
}

.ep-form :deep(.el-col) {
  min-width: 0;
  padding-left: 8px !important;
  padding-right: 8px !important;
}

.ep-form :deep(.el-form-item) {
  width: 100%;
  margin-bottom: 10px !important;
}

.ep-form :deep(.el-form-item__label) {
  height: auto;
  min-height: 18px;
  padding: 0 0 5px !important;
  color: #344054;
  font-size: 12px !important;
  font-weight: 500;
  line-height: 1.35;
}

.ep-form :deep(.el-form-item__content) {
  width: 100%;
  min-width: 0;
  display: flex;
}

.ep-control,
.ep-form :deep(.el-input),
.ep-form :deep(.el-select),
.ep-form :deep(.el-date-editor),
.ep-form :deep(.el-input-number) {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  flex: 1 1 auto !important;
  box-sizing: border-box !important;
}

.ep-form :deep(.el-input__wrapper),
.ep-form :deep(.el-select__wrapper) {
  width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}

.ep-form :deep(.el-input__inner),
.ep-form :deep(.el-select__placeholder),
.ep-form :deep(.el-select__selected-item),
.ep-form :deep(.el-date-editor .el-range-input) {
  font-size: 12px !important;
}

.ep-form :deep(.el-input__wrapper),
.ep-form :deep(.el-select__wrapper),
.ep-form :deep(.el-date-editor.el-input__wrapper) {
  min-height: 32px !important;
  height: 32px !important;
}

.ep-table-card :deep(.el-card__body) {
  padding: 10px 18px 16px !important;
}

.ep-table {
  font-size: 12px !important;
}

.ep-table :deep(.el-table__header th) {
  height: 38px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #475467 !important;
  background: #f8fafc !important;
}

.ep-table :deep(.el-table__cell) {
  padding: 8px 0 !important;
  font-size: 12px !important;
  color: #475467;
}

.ep-table :deep(.cell) {
  line-height: 1.45;
}

.ep-table :deep(.el-button.is-link) {
  height: 24px;
  padding: 0 4px;
  font-size: 12px !important;
}

.ep-table :deep(.el-tag) {
  height: 22px;
  padding: 0 7px;
  font-size: 11.5px;
}

:global(.ep-runtime-select-popper),
:global(.ep-runtime-picker-popper),
:global(.el-picker__popper),
:global(.el-select__popper) {
  z-index: 2147483600 !important;
}


/* A9.10.14.4.3.15.6.24D5_ADMIN_ALIGNMENT_ACTIONBAR */
.ep-action-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  width: 100%;
  min-height: 0;
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.ep-action-toolbar + .ep-runtime-node {
  margin-top: 10px;
}

.ep-form :deep(.el-form-item[data-ui-selectable='true']),
.ep-form :deep(.el-form-item) {
  padding: 0 !important;
  margin-bottom: 10px !important;
  border: 0 !important;
  outline: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.ep-form :deep(.el-form-item__content) {
  width: 100% !important;
  min-width: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  outline: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.ep-form :deep(.el-form-item__content > *) {
  width: 100% !important;
  max-width: 100% !important;
  flex: 1 1 auto !important;
}

.ep-form :deep(.el-input),
.ep-form :deep(.el-select),
.ep-form :deep(.el-date-editor),
.ep-form :deep(.el-input-number) {
  display: flex !important;
  width: 100% !important;
}

.ep-form :deep(.el-input__wrapper),
.ep-form :deep(.el-select__wrapper),
.ep-form :deep(.el-date-editor.el-input__wrapper) {
  width: 100% !important;
  height: 32px !important;
  min-height: 32px !important;
  border-radius: 6px !important;
  box-shadow: 0 0 0 1px #d0d5dd inset !important;
}

.ep-form :deep(.el-input__wrapper:hover),
.ep-form :deep(.el-select__wrapper:hover),
.ep-form :deep(.el-date-editor.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #98a2b3 inset !important;
}

.ep-form :deep(.el-input__wrapper.is-focus),
.ep-form :deep(.el-select__wrapper.is-focused),
.ep-form :deep(.el-date-editor.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.ep-form :deep(.el-col) {
  box-sizing: border-box !important;
}

.ep-table-card {
  margin-top: 0 !important;
}

.ep-table-card :deep(.el-card__header) {
  padding-top: 13px !important;
  padding-bottom: 12px !important;
}

.ep-table-card :deep(.el-card__body) {
  padding-top: 10px !important;
}

</style>
