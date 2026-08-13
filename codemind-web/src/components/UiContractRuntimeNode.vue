<template>
  <section
    v-if="visible"
    class="runtime-node"
    data-ui-selectable="true"
    data-ui-element-kind="COMPONENT"
    :data-ui-component-id="component.componentId"
    :data-ui-component-type="component.type"
    :data-ui-element-label="component.title || component.description || component.componentId"
    :style="componentInlineStyle"
    :class="[
      `node-${component.type.toLowerCase()}`,
      { disabled: isDisabled },
    ]"
  >
    <template v-if="component.type === 'DIVIDER'">
      <hr />
    </template>

    <template v-else-if="component.type === 'TEXT'">
      <p
        class="plain-text"
        data-ui-selectable="true"
        data-ui-element-kind="TEXT"
        data-ui-text-key="props.text"
        :data-ui-component-id="component.componentId"
        :data-ui-component-type="component.type"
        :data-ui-element-label="textProp('text') || component.description || component.title || component.componentId"
        :style="textInlineStyle('text')"
      >
        {{ textProp('text') || component.description || component.title }}
      </p>
    </template>

    <template v-else-if="component.type === 'PAGE_HEADER'">
      <header class="page-header-node">
        <div>
          <h2
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="title"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.title || component.componentId"
          :style="textInlineStyle('title')"
          >{{ component.title }}</h2>
          <p
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.description || component.componentId"
          :style="textInlineStyle('description')"
          >{{ component.description }}</p>
        </div>

        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
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
        data-ui-selectable="true"
        data-ui-element-kind="ACTION"
        :data-ui-component-id="component.componentId"
        :data-ui-component-type="component.type"
        :data-ui-action-id="component.actionIds?.[0] || ''"
        :data-ui-element-label="component.title || actionLabel(component.actionIds?.[0])"
        :disabled="isDisabled || actionDisabled(component.actionIds?.[0])"
        v-show="!actionHidden(component.actionIds?.[0])"
        :style="actionInlineStyle(component.actionIds?.[0])"
        @click="emitFirstAction"
      >
        {{ component.title || actionLabel(component.actionIds?.[0]) }}
      </button>
    </template>

    <template v-else-if="component.type === 'LINK'">
      <button
        class="link-button"
        type="button"
        data-ui-selectable="true"
        data-ui-element-kind="ACTION"
        :data-ui-component-id="component.componentId"
        :data-ui-component-type="component.type"
        :data-ui-action-id="component.actionIds?.[0] || ''"
        :data-ui-element-label="component.title || actionLabel(component.actionIds?.[0])"
        :disabled="isDisabled || actionDisabled(component.actionIds?.[0])"
        v-show="!actionHidden(component.actionIds?.[0])"
        :style="actionInlineStyle(component.actionIds?.[0])"
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
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            :disabled="isDisabled || actionDisabled(actionId)"
            v-show="!actionHidden(actionId)"
            :style="actionInlineStyle(actionId)"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </div>
    </template>

    <template v-else-if="component.type === 'FORM' || component.type === 'FIELD_GROUP'">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <div class="form-grid">
        <label
          v-for="field in component.fields || []"
          :key="field.fieldKey"
          class="form-field"
          v-show="fieldVisible(field)"
          :style="fieldInlineStyle(field, 'field')"
          data-ui-selectable="true"
          data-ui-element-kind="FIELD"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-field-key="field.fieldKey"
          :data-ui-element-label="field.label || field.fieldKey"
          :class="{ invalid: Boolean(fieldErrors[field.fieldKey]) }"
        >
          <span
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_LABEL"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldLabel')"
          >
            {{ field.label }}
            <em v-if="field.required">*</em>
          </span>

          <input
            v-if="isTextInput(field.type)"
            :type="inputType(field.type)"
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.placeholder || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || fieldIsDisabled(field)"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <textarea
            v-else-if="field.type === 'TEXTAREA'"
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.placeholder || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || fieldIsDisabled(field)"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <select
            v-else-if="field.type === 'SELECT'"
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.placeholder || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
            :value="displayInputValue(formValues[field.fieldKey])"
            :disabled="isDisabled || fieldIsDisabled(field)"
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
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
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
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.helpText || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
          >
            <input
              type="checkbox"
              :checked="Boolean(formValues[field.fieldKey])"
              :disabled="isDisabled || fieldIsDisabled(field)"
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
            data-ui-selectable="true"
            data-ui-element-kind="FIELD_INPUT"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.label || field.placeholder || field.fieldKey"
            :style="fieldInlineStyle(field, 'fieldControl')"
            :value="displayInputValue(formValues[field.fieldKey])"
            :placeholder="field.placeholder || ''"
            :disabled="isDisabled || fieldIsDisabled(field)"
            @input="updateField(field.fieldKey, inputValue($event))"
          />

          <small
            v-if="fieldErrors[field.fieldKey]"
            class="field-error"
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="fieldError"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="fieldErrors[field.fieldKey]"
          >
            {{ fieldErrors[field.fieldKey] }}
          </small>
          <small
            v-else-if="field.helpText"
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="helpText"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="field.fieldKey"
            :data-ui-element-label="field.helpText"
          >
            {{ field.helpText }}
          </small>
        </label>
      </div>

      <div class="action-row">
        <button
          v-for="actionId in component.actionIds || []"
          :key="actionId"
          type="button"
          data-ui-selectable="true"
          data-ui-element-kind="ACTION"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-action-id="actionId"
          :data-ui-element-label="actionLabel(actionId)"
          :disabled="isDisabled || actionDisabled(actionId)"
          v-show="!actionHidden(actionId)"
          :style="actionInlineStyle(actionId)"
          @click="emitAction(actionId)"
        >
          {{ actionLabel(actionId) }}
        </button>
      </div>
    </template>

    <template v-else-if="component.type === 'TABLE'">
      <header
        v-if="component.title || component.description || tableHeaderActionIds.length"
        class="node-header"
      >
        <div>
          <h3 v-if="component.title">{{ component.title }}</h3>
          <p v-if="component.description">{{ component.description }}</p>
        </div>

        <div class="action-row">
          <button
            v-for="actionId in tableHeaderActionIds"
            :key="actionId"
            type="button"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            :disabled="isDisabled || actionDisabled(actionId)"
            v-show="!actionHidden(actionId)"
            :style="actionInlineStyle(actionId)"
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
                v-for="column in renderColumns"
                :key="column.columnKey"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_COLUMN"
                :data-ui-component-id="component.componentId"
                :data-ui-component-type="component.type"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="column.label || column.columnKey"
                :style="columnHeaderStyle(column)"
              >
                {{ column.label }}
              </th>
              <th v-if="rowActionIds.length" class="row-action-header">操作</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(row, rowIndex) in tableRows"
              :key="rowKey(row, rowIndex)"
            >
              <td
                v-for="column in renderColumns"
                :key="column.columnKey"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_CELL"
                :data-ui-component-id="component.componentId"
                :data-ui-component-type="component.type"
                :data-ui-column-key="column.columnKey"
                :data-ui-element-label="column.label || column.columnKey"
                :style="columnCellStyle(column)"
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
                  data-ui-selectable="true"
                  data-ui-element-kind="TABLE_ACTION"
                  :data-ui-component-id="component.componentId"
                  :data-ui-component-type="component.type"
                  :data-ui-action-id="actionId"
                  :data-ui-element-label="actionLabel(actionId)"
                  :disabled="isDisabled"
                  @click="emitAction(actionId, row)"
                >
                  {{ actionLabel(actionId) }}
                </button>
              </td>
            </tr>

            <tr v-if="!tableRows.length">
              <td
                :colspan="renderColumns.length + (rowActionIds.length ? 1 : 0)"
                class="empty-cell"
              >
                暂无数据
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else-if="isComponentType('STATS', 'STAT_GRID', 'KPI', 'KPI_CARD', 'METRIC_GRID', 'METRICS')">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <div class="stats-grid">
        <article
          v-for="(item, index) in statsItems"
          :key="itemDataKey(item, index)"
          class="stat-card"
          data-ui-selectable="true"
          data-ui-element-kind="METRIC"
          data-ui-text-key="metric"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-field-key="itemFieldKey(item, index)"
          :data-ui-element-label="itemLabel(item, index)"
          :style="itemInlineStyle(item, index)"
        >
          <header>
            <span
              data-ui-selectable="true"
              data-ui-element-kind="TEXT"
              data-ui-text-key="metricLabel"
              :data-ui-component-id="component.componentId"
              :data-ui-component-type="component.type"
              :data-ui-field-key="itemFieldKey(item, index)"
              :data-ui-element-label="itemLabel(item, index)"
            >{{ itemLabel(item, index) }}</span>
            <em :class="metricTrendClass(item)">{{ itemHint(item) }}</em>
          </header>
          <strong
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="metricValue"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="itemFieldKey(item, index)"
            :data-ui-element-label="displayValue(itemValue(item))"
          >{{ displayValue(itemValue(item)) }}</strong>
          <small
            v-if="itemDescription(item)"
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="metricDescription"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="itemFieldKey(item, index)"
            :data-ui-element-label="itemDescription(item)"
          >{{ itemDescription(item) }}</small>
        </article>
      </div>
    </template>

    <template v-else-if="isComponentType('CHART', 'TREND_CHART', 'LINE_CHART', 'BAR_CHART', 'PIE_CHART', 'AREA_CHART')">
      <header class="node-header">
        <div>
          <h3
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="title"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.title || component.componentId"
          >{{ component.title || '趋势分析' }}</h3>
          <p
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.description || textProp('subtitle') || component.componentId"
          >{{ component.description || textProp('subtitle') }}</p>
        </div>
        <span class="chart-badge">{{ chartKind }}</span>
      </header>

      <div class="chart-node" :class="`chart-${chartKind}`">
        <div
          v-for="(item, index) in chartItems"
          :key="itemDataKey(item, index)"
          class="chart-row"
          data-ui-selectable="true"
          data-ui-element-kind="CHART_ITEM"
          data-ui-text-key="chartItem"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-field-key="itemFieldKey(item, index)"
          :data-ui-element-label="itemLabel(item, index)"
        >
          <span>{{ itemLabel(item, index) }}</span>
          <div class="chart-track"><i :style="chartBarStyle(item)"></i></div>
          <strong>{{ displayValue(itemValue(item)) }}</strong>
        </div>
        <div v-if="!chartItems.length" class="chart-empty">暂无趋势数据</div>
      </div>
    </template>

    <template v-else-if="isComponentType('TIMELINE', 'ACTIVITY_TIMELINE', 'AUDIT_LOG', 'PROCESS_LOG')">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <ol class="timeline-node">
        <li
          v-for="(item, index) in timelineItems"
          :key="itemDataKey(item, index)"
          data-ui-selectable="true"
          data-ui-element-kind="TIMELINE_ITEM"
          data-ui-text-key="timelineItem"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-field-key="itemFieldKey(item, index)"
          :data-ui-element-label="itemLabel(item, index)"
          :class="itemStatusClass(item)"
        >
          <span class="timeline-dot"></span>
          <div>
            <strong>{{ itemLabel(item, index) }}</strong>
            <small>{{ itemTime(item) }}</small>
            <p>{{ itemDescription(item) || displayValue(itemValue(item)) }}</p>
          </div>
        </li>
      </ol>
    </template>

    <template v-else-if="isComponentType('UPLOAD', 'FILE_UPLOAD', 'ATTACHMENT_UPLOAD', 'IMPORT_UPLOAD')">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <div class="upload-node">
        <div
          class="upload-dropzone"
          data-ui-selectable="true"
          data-ui-element-kind="UPLOAD_DROPZONE"
          data-ui-text-key="uploadDropzone"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="textProp('placeholder') || '上传文件'"
        >
          <strong>{{ textProp('placeholder') || textProp('title') || '点击或拖拽文件到这里上传' }}</strong>
          <span>{{ textProp('accept') || textProp('description') || '支持批量上传、校验和附件预览' }}</span>
        </div>
        <ul class="upload-list">
          <li
            v-for="(item, index) in uploadItems"
            :key="itemDataKey(item, index)"
            data-ui-selectable="true"
            data-ui-element-kind="UPLOAD_ITEM"
            data-ui-text-key="fileName"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="itemFieldKey(item, index)"
            :data-ui-element-label="itemLabel(item, index)"
            :class="itemStatusClass(item)"
          >
            <span>{{ itemLabel(item, index) }}</span>
            <small>{{ itemSize(item) }}</small>
            <em>{{ itemStatus(item) || '已上传' }}</em>
          </li>
        </ul>
      </div>
    </template>

    <template v-else-if="isComponentType('TREE', 'TREE_SELECT', 'ORG_TREE', 'PERMISSION_TREE')">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <ul class="tree-node-list">
        <li
          v-for="(item, index) in treeItems"
          :key="itemDataKey(item, index)"
          data-ui-selectable="true"
          data-ui-element-kind="TREE_NODE"
          data-ui-text-key="treeNode"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-field-key="itemFieldKey(item, index)"
          :data-ui-element-label="itemLabel(item, index)"
        >
          <div class="tree-row"><span></span>{{ itemLabel(item, index) }}</div>
          <ul v-if="treeChildren(item).length">
            <li
              v-for="(child, childIndex) in treeChildren(item)"
              :key="itemDataKey(child, childIndex)"
              data-ui-selectable="true"
              data-ui-element-kind="TREE_NODE"
              data-ui-text-key="treeNode"
              :data-ui-component-id="component.componentId"
              :data-ui-component-type="component.type"
              :data-ui-field-key="itemFieldKey(child, childIndex)"
              :data-ui-element-label="itemLabel(child, childIndex)"
            >{{ itemLabel(child, childIndex) }}</li>
          </ul>
        </li>
      </ul>
    </template>

    <template v-else-if="isComponentType('RESULT', 'STATUS_RESULT', 'SUCCESS_RESULT', 'ERROR_RESULT')">
      <div
        class="result-node"
        data-ui-selectable="true"
        data-ui-element-kind="RESULT"
        data-ui-text-key="result"
        :data-ui-component-id="component.componentId"
        :data-ui-component-type="component.type"
        :data-ui-element-label="component.title || component.description || component.componentId"
        :class="textProp('status').toLowerCase()"
      >
        <span>{{ resultIcon() }}</span>
        <strong>{{ component.title || textProp('title') || '处理完成' }}</strong>
        <p>{{ component.description || textProp('description') || textProp('message') }}</p>
        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >{{ actionLabel(actionId) }}</button>
        </div>
      </div>
    </template>

    <template v-else-if="isComponentType('MODAL', 'DIALOG', 'CONFIRM_MODAL')">
      <div class="dialog-preview modal-preview">
        <div class="dialog-panel">
          <header>
            <h3
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="component.componentId"
              :data-ui-component-type="component.type"
              :data-ui-element-label="component.title || component.componentId"
            >{{ component.title || '弹窗标题' }}</h3>
            <button type="button">×</button>
          </header>
          <p
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.description || component.componentId"
          >{{ component.description || textProp('message') || '这里展示弹窗内容、表单或二次确认信息。' }}</p>
          <div v-if="component.children?.length" class="children-grid dialog-children">
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
          <footer class="action-row">
            <button
              v-for="actionId in component.actionIds || []"
              :key="actionId"
              type="button"
              data-ui-selectable="true"
              data-ui-element-kind="ACTION"
              :data-ui-component-id="component.componentId"
              :data-ui-component-type="component.type"
              :data-ui-action-id="actionId"
              :data-ui-element-label="actionLabel(actionId)"
              @click="emitAction(actionId)"
            >{{ actionLabel(actionId) }}</button>
          </footer>
        </div>
      </div>
    </template>

    <template v-else-if="isComponentType('DRAWER', 'SIDE_PANEL', 'DETAIL_DRAWER')">
      <div class="dialog-preview drawer-preview">
        <aside class="drawer-panel">
          <header>
            <h3
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="component.componentId"
              :data-ui-component-type="component.type"
              :data-ui-element-label="component.title || component.componentId"
            >{{ component.title || '侧滑面板' }}</h3>
            <button type="button">×</button>
          </header>
          <p
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-element-label="component.description || component.componentId"
          >{{ component.description || textProp('message') || '这里展示侧滑详情、关联记录或处理表单。' }}</p>
          <div v-if="component.children?.length" class="dialog-children">
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
        </aside>
      </div>
    </template>

    <template v-else-if="isComponentType('TABS', 'TAB_PANEL', 'TAB_VIEW')">
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <div class="tabs">
        <button
          v-for="tab in component.tabs || []"
          :key="tab.tabKey"
          type="button"
          data-ui-selectable="true"
          data-ui-element-kind="TAB"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-tab-key="tab.tabKey"
          :data-ui-element-label="tab.title || tab.tabKey"
          :class="{ active: activeTabKey === tab.tabKey }"
          :style="tabInlineStyle(tab)"
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

    <template v-else-if="isComponentType('DESCRIPTION_LIST', 'DESCRIPTIONS', 'DETAIL_LIST', 'INFO_LIST')">
      <header class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <dl class="description-list">
        <template
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.fieldKey || index)"
        >
          <dt
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="descriptionLabel"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="String(item.fieldKey || item.key || '')"
            :data-ui-element-label="String(item.label || item.key || '-')"
          >{{ String(item.label || item.key || '-') }}</dt>
          <dd
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="descriptionValue"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-field-key="String(item.fieldKey || item.key || '')"
            :data-ui-element-label="displayValue(item.value ?? boundObject[String(item.fieldKey || item.key || '')])"
          >
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

    <template v-else-if="isComponentType('STEPS', 'STEP_LIST', 'PROGRESS_STEPS', 'PROCESS_STEPS')">
      <header class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <ol class="steps">
        <li
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.title || index)"
          data-ui-selectable="true"
          data-ui-element-kind="TEXT"
          data-ui-text-key="step"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="String(item.title || item.description || item.key || index)"
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

    <template v-else-if="isComponentType('ALERT', 'NOTICE', 'TIP', 'WARNING')">
      <div
        class="alert-node"
        data-ui-selectable="true"
        data-ui-element-kind="TEXT"
        data-ui-text-key="alert"
        :data-ui-component-id="component.componentId"
        :data-ui-component-type="component.type"
        :data-ui-element-label="component.title || component.description || textProp('message') || component.componentId"
        :class="textProp('level').toLowerCase()"
      >
        <strong>{{ component.title }}</strong>
        <span>{{ component.description || textProp('message') }}</span>
      </div>
    </template>

    <template v-else-if="isComponentType('EMPTY_STATE', 'EMPTY', 'NO_DATA')">
      <div class="empty-state">
        <strong>{{ component.title || '暂无内容' }}</strong>
        <span>{{ component.description }}</span>
        <div class="action-row">
          <button
            v-for="actionId in component.actionIds || []"
            :key="actionId"
            type="button"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="component.componentId"
            :data-ui-component-type="component.type"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >
            {{ actionLabel(actionId) }}
          </button>
        </div>
      </div>
    </template>

    <template v-else>
      <header v-if="component.title || component.description" class="node-header">
        <h3
          data-ui-selectable="true"
          data-ui-element-kind="TITLE"
          data-ui-text-key="title"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.title || component.componentId"
        >{{ component.title }}</h3>
        <p
          data-ui-selectable="true"
          data-ui-element-kind="DESCRIPTION"
          data-ui-text-key="description"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="component.description || component.componentId"
        >{{ component.description }}</p>
      </header>

      <div
        v-if="component.type === 'CARD' && objectArrayProp('items').length"
        class="metric-grid"
      >
        <article
          v-for="(item, index) in objectArrayProp('items')"
          :key="String(item.key || item.label || index)"
          data-ui-selectable="true"
          data-ui-element-kind="METRIC"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-element-label="String(item.label || item.key || index)"
          :style="metricInlineStyle(item)"
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
          data-ui-selectable="true"
          data-ui-element-kind="ACTION"
          :data-ui-component-id="component.componentId"
          :data-ui-component-type="component.type"
          :data-ui-action-id="actionId"
          :data-ui-element-label="actionLabel(actionId)"
          :disabled="isDisabled || actionDisabled(actionId)"
          v-show="!actionHidden(actionId)"
          :style="actionInlineStyle(actionId)"
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

// A9.10.4 Runtime Component Coverage: normalize aliases so Agent patterns render predictably.
const componentType = computed(() =>
  String((props.component as unknown as Record<string, unknown>).type || '').trim().toUpperCase(),
)

const statsItems = computed(() => {
  const items = objectArrayPropAny(['items', 'metrics', 'stats', 'kpis', 'cards'])
  if (items.length > 0) return items

  if (isComponentType('KPI_CARD', 'KPI', 'STAT')) {
    return [
      {
        key: props.component.componentId,
        label: props.component.title || props.component.props?.label || '指标',
        value: props.component.props?.value || props.component.props?.count || props.component.props?.amount || '-',
        hint: props.component.props?.hint || props.component.props?.trend || '',
        description: props.component.description || props.component.props?.description || '',
      },
    ]
  }

  return []
})

const chartItems = computed(() =>
  objectArrayPropAny(['data', 'series', 'items', 'metrics', 'values']),
)

const timelineItems = computed(() =>
  objectArrayPropAny(['items', 'timeline', 'records', 'logs', 'events']),
)

const uploadItems = computed(() =>
  objectArrayPropAny(['files', 'fileList', 'items', 'records', 'data']),
)

const treeItems = computed(() =>
  objectArrayPropAny(['nodes', 'treeData', 'items', 'options']),
)

const chartMaxValue = computed(() => {
  const values = chartItems.value
    .map((item) => numericItemValue(item))
    .filter((value) => Number.isFinite(value) && value > 0)

  return Math.max(1, ...values)
})

const chartKind = computed(() =>
  String(
    props.component.props?.chartType ||
      props.component.props?.type ||
      props.component.props?.visualType ||
      componentType.value,
  )
    .trim()
    .toLowerCase(),
)

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
    ? (value as unknown as Record<string, unknown>)
    : {}
})

const rowActionIds = computed(() => {
  const raw = props.component.props?.rowActionIds
  return Array.isArray(raw)
    ? raw.filter((value): value is string => typeof value === 'string')
    : []
})


const renderColumns = computed(() => {
  const columns = Array.isArray(props.component.columns) ? props.component.columns : []
  const result: typeof columns = []
  const seen = new Set<string>()

  columns.forEach((column, index) => {
    if (isActionLikeColumn(column)) return

    const identity = columnIdentity(column, index)
    if (seen.has(identity)) return

    seen.add(identity)
    result.push(column)
  })

  return result
})

// A9.10.14.4.3.13.2.11_TABLE_ROW_ACTION_DISCIPLINE
const tableHeaderActionIds = computed(() => {
  const ids = Array.isArray(props.component.actionIds) ? props.component.actionIds : []

  if (props.component.type !== 'TABLE') {
    return ids.filter((actionId) => isPageLevelAction(actionId))
  }

  const rowIds = new Set(rowActionIds.value)
  return ids.filter((actionId) =>
    !rowIds.has(actionId) &&
    isPageLevelAction(actionId) &&
    !isRowScopedTableAction(actionId),
  )
})

function actionRecord(actionId?: string) {
  if (!actionId) return null
  return props.interactions.find((item) => item.actionId === actionId) || null
}

function actionText(actionId?: string): string {
  const action = actionRecord(actionId)
  const payload = action && typeof action.payload === 'object' && action.payload !== null
    ? action.payload as unknown as Record<string, unknown>
    : {}

  return [
    actionId || '',
    action?.label || '',
    action?.type || '',
    String(payload.mode || ''),
    String(payload.runtimeEffect || ''),
  ].join(' ')
}

function isRowScopedTableAction(actionId?: string): boolean {
  const action = actionRecord(actionId)
  const text = actionText(actionId)
  const lower = text.toLowerCase()
  const type = String(action?.type || '').toUpperCase()

  if (type === 'DELETE_ROW') return true
  if (type === 'OPEN_MODAL' && /(编辑|修改|edit)/i.test(text)) return true
  if (/(查看|详情|编辑|修改|审核|审批|删除|移除|作废)/.test(text)) return true
  return /(view_detail|row_action|rowaction|edit|approval|delete)/i.test(lower)
}

function isQueryOrFormLocalAction(actionId?: string): boolean {
  const action = actionRecord(actionId)
  const text = actionText(actionId)
  const lower = text.toLowerCase()
  const type = String(action?.type || '').toUpperCase()

  return (
    ['QUERY_DATA', 'RESET_FORM', 'SUBMIT_FORM', 'CLOSE_MODAL'].includes(type) &&
    (
      /(查询|搜索|重置|保存|取消)/.test(text) ||
      /(_query|_reset|_submit|_cancel|search)/i.test(lower)
    )
  )
}

function isPageLevelAction(actionId?: string): boolean {
  const action = actionRecord(actionId)
  const text = actionText(actionId)
  const type = String(action?.type || '').toUpperCase()

  if (!action || isRowScopedTableAction(actionId) || isQueryOrFormLocalAction(actionId)) {
    return false
  }

  if (type === 'OPEN_MODAL' && /(新增|新建|创建|添加|create)/i.test(text)) {
    return true
  }

  return /(新增|新建|创建|添加|导出|刷新)/.test(text)
}

function isActionLikeColumn(column: unknown): boolean {
  const record = asRecord(column)
  const label = String(record.label || record.title || record.name || '').trim()
  const type = String(record.type || record.columnType || '').trim().toUpperCase()
  const key = String(record.columnKey || record.fieldKey || record.key || '').trim().toLowerCase()

  if (type === 'ACTIONS' || type === 'ACTION') return true
  if (['操作', '动作', '行操作', '操作列'].includes(label)) return true
  if (['action', 'actions', 'operation', 'operations', 'rowaction', 'rowactions', 'row_actions'].includes(key)) return true
  if (/(^|[_-])(action|actions|operation|operations|rowaction|rowactions|row_actions)([_-]|$)/.test(key)) return true

  return false
}

function columnIdentity(column: unknown, index: number): string {
  const record = asRecord(column)
  const key = String(record.columnKey || record.fieldKey || record.key || '').trim().toLowerCase()
  const label = String(record.label || record.title || record.name || '').trim().toLowerCase()

  return key || label || `column_${index + 1}`
}

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



const componentInlineStyle = computed<Record<string, string>>(() => {
  return normalizeComponentInlineStyle(props.component?.props?.style)
})

function normalizeComponentInlineStyle(value: unknown): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  const source = value as unknown as Record<string, unknown>
  const allowedKeys = [
    'backgroundColor',
    'color',
    'borderColor',
    'transform',
    'marginLeft',
    'marginRight',
    'marginTop',
    'marginBottom',
    'padding',
    'minWidth',
    'maxWidth',
    'width',
    'justifySelf',
    'alignSelf',
    'justifyContent',
    'alignItems',
    'placeSelf',
    'order',
    'gridColumn',
    'gridRow',
    'flex',
    'display',
    'gap',
    'paddingInline',
    'paddingBlock',
    'whiteSpace',
    'height',
    'minHeight',
    'fontSize',
    'fontWeight',
    'borderRadius',
    'opacity',
    'display',
    'boxShadow',
  ]
  const result: Record<string, string> = {}

  for (const key of allowedKeys) {
    const raw = source[key]
    if (typeof raw === 'string' && raw.trim()) {
      result[key] = raw.trim()
    }
  }

  if (props.component?.props?.hidden === true) {
    result.display = 'none'
  }

  return result
}


function elementInlineStyle(
  key: string | null | undefined,
): Record<string, string> {
  const normalizedKey = String(key || '').trim()
  if (!normalizedKey) return {}

  const componentProps = props.component.props
  const elementStyles = componentProps?.elementStyles

  if (!elementStyles || typeof elementStyles !== 'object' || Array.isArray(elementStyles)) {
    return {}
  }

  const value = (elementStyles as unknown as Record<string, unknown>)[normalizedKey]
  return normalizeComponentInlineStyle(value)
}

function fieldInlineStyle(
  field: unknown,
  scope: 'field' | 'fieldLabel' | 'fieldControl',
): Record<string, string> {
  const record = asRecord(field)
  const fieldKey = String(record.fieldKey || record.key || '')
  if (!fieldKey) return {}

  const styleKey =
    scope === 'fieldLabel'
      ? `fieldLabel:${fieldKey}`
      : scope === 'fieldControl'
        ? `fieldControl:${fieldKey}`
        : `field:${fieldKey}`

  const style = elementInlineStyle(styleKey)

  if (scope === 'field' && record.hidden === true) {
    style.display = 'none'
  }

  return style
}

function fieldVisible(
  field: unknown,
): boolean {
  return asRecord(field).hidden !== true
}

function fieldIsDisabled(
  field: unknown,
): boolean {
  return asRecord(field).disabled === true
}

function actionInlineStyle(
  actionId?: unknown,
): Record<string, string> {
  const id = String(actionId || '').trim()
  if (!id) return {}

  const style = elementInlineStyle(`action:${id}`)
  const action = findAction(id)

  if (action && (action as unknown as Record<string, unknown>).hidden === true) {
    style.display = 'none'
  }

  return style
}

function actionDisabled(
  actionId?: unknown,
): boolean {
  const action = findAction(actionId)
  return Boolean(action && (action as unknown as Record<string, unknown>).disabled === true)
}

function actionHidden(
  actionId?: unknown,
): boolean {
  const action = findAction(actionId)
  return Boolean(action && (action as unknown as Record<string, unknown>).hidden === true)
}

function columnHeaderStyle(
  column: unknown,
): Record<string, string> {
  const record = asRecord(column)
  const columnKey = String(record.columnKey || record.key || '')
  const style = elementInlineStyle(columnKey ? `column:${columnKey}` : '')
  const width = Number(record.width || 0)

  if (Number.isFinite(width) && width > 0) {
    style.width = `${width}px`
  }

  if (record.hidden === true) {
    style.display = 'none'
  }

  return style
}

function columnCellStyle(
  column: unknown,
): Record<string, string> {
  const record = asRecord(column)
  const columnKey = String(record.columnKey || record.key || '')
  const style = elementInlineStyle(columnKey ? `column:${columnKey}` : '')

  if (record.hidden === true) {
    style.display = 'none'
  }

  return style
}

function tabInlineStyle(
  tab: unknown,
): Record<string, string> {
  const record = asRecord(tab)
  const tabKey = String(record.tabKey || record.key || '')
  const style = elementInlineStyle(tabKey ? `tab:${tabKey}` : '')

  if (record.hidden === true) {
    style.display = 'none'
  }

  return style
}

function textInlineStyle(
  role: string,
): Record<string, string> {
  return elementInlineStyle(`text:${role}`)
}

function metricInlineStyle(
  item: unknown,
): Record<string, string> {
  const record = asRecord(item)
  const key = String(record.key || record.fieldKey || record.label || '')
  return elementInlineStyle(key ? `metric:${key}` : '')
}


function isComponentType(...types: string[]): boolean {
  const normalized = componentType.value
  return types.some((type) => normalized === type || normalized.includes(type))
}

function objectArrayPropAny(keys: string[]): Record<string, unknown>[] {
  for (const key of keys) {
    const value = objectArrayProp(key)
    if (value.length > 0) return value
  }

  return []
}

function itemDataKey(item: unknown, index: number): string {
  const record = asRecord(item)
  return String(record.key || record.id || record.fieldKey || record.code || record.name || record.title || record.label || index)
}

function itemFieldKey(item: unknown, index: number): string {
  return itemDataKey(item, index)
}

function itemLabel(item: unknown, index = 0): string {
  const record = asRecord(item)
  return String(record.label || record.title || record.name || record.fileName || record.filename || record.key || record.id || `项目 ${index + 1}`)
}

function itemValue(item: unknown): unknown {
  const record = asRecord(item)
  return record.value ?? record.count ?? record.amount ?? record.total ?? record.percent ?? record.rate ?? record.status ?? ''
}

function numericItemValue(item: unknown): number {
  const raw = itemValue(item)
  if (typeof raw === 'number') return raw
  const parsed = Number(String(raw || '').replace(/[^0-9.-]/g, ''))
  return Number.isFinite(parsed) ? parsed : 0
}

function itemHint(item: unknown): string {
  const record = asRecord(item)
  return String(record.trend || record.delta || record.hint || record.badge || '')
}

function itemDescription(item: unknown): string {
  const record = asRecord(item)
  return String(record.description || record.subtitle || record.remark || record.message || '')
}

function itemStatus(item: unknown): string {
  const record = asRecord(item)
  return String(record.status || record.state || record.result || '')
}

function itemTime(item: unknown): string {
  const record = asRecord(item)
  return String(record.time || record.datetime || record.date || record.createdAt || record.updatedAt || '')
}

function itemSize(item: unknown): string {
  const record = asRecord(item)
  const value = record.size || record.fileSize || record.length || ''
  if (typeof value === 'number') {
    if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
    if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`
    return `${value} B`
  }
  return String(value || itemDescription(item) || '已校验')
}

function itemStatusClass(item: unknown): string {
  const status = itemStatus(item).toLowerCase()
  if (['success', 'done', 'completed', 'passed', 'approved', '已完成', '成功', '通过'].includes(status)) return 'success'
  if (['warning', 'pending', 'processing', '待处理', '处理中'].includes(status)) return 'warning'
  if (['error', 'failed', 'rejected', '失败', '驳回'].includes(status)) return 'error'
  return status || 'default'
}

function metricTrendClass(item: unknown): string {
  const hint = itemHint(item)
  if (/[-↓降低]/.test(hint)) return 'down'
  if (/[+↑增升]/.test(hint)) return 'up'
  return ''
}

function chartBarStyle(item: unknown): Record<string, string> {
  const percent = Math.max(6, Math.min(100, Math.round((numericItemValue(item) / chartMaxValue.value) * 100)))
  return { width: `${percent}%` }
}

function treeChildren(item: unknown): Record<string, unknown>[] {
  const record = asRecord(item)
  const raw = record.children || record.items || record.nodes
  if (!Array.isArray(raw)) return []
  return raw.filter(
    (child): child is Record<string, unknown> =>
      Boolean(child) && typeof child === 'object' && !Array.isArray(child),
  )
}

function resultIcon(): string {
  const status = String(props.component.props?.status || props.component.props?.level || '').toLowerCase()
  if (status.includes('error') || status.includes('fail')) return '×'
  if (status.includes('warn')) return '!'
  return '✓'
}

function itemInlineStyle(item: unknown, index: number): Record<string, string> {
  const key = itemFieldKey(item, index)
  return elementInlineStyle(`field:${key}`)
}

function findAction(
  actionId?: unknown,
): UiContractInteraction | null {
  const id = String(actionId || '').trim()
  if (!id) return null
  return props.interactions.find((item) => item.actionId === id) || null
}

function asRecord(
  value: unknown,
): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as unknown as Record<string, unknown>
    : {}
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
  const value = props.component.props?.[key] ?? (props.component as unknown as Record<string, unknown>)[key]
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


/* A9.10.4 Runtime Component Coverage */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  border: 1px solid rgba(203, 213, 225, 0.76);
  border-radius: 18px;
  padding: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
}

.stat-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stat-card header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.stat-card header em {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.stat-card header em.up {
  background: #dcfce7;
  color: #166534;
}

.stat-card header em.down {
  background: #fee2e2;
  color: #991b1b;
}

.stat-card strong {
  display: block;
  margin-top: 12px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.1;
}

.stat-card small {
  display: block;
  margin-top: 8px;
  color: #64748b;
}

.chart-badge {
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.chart-node {
  display: grid;
  gap: 12px;
}

.chart-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 80px;
  align-items: center;
  gap: 12px;
}

.chart-row > span,
.chart-row > strong {
  color: #334155;
  font-size: 13px;
}

.chart-track {
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: #e2e8f0;
}

.chart-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
}

.chart-empty {
  display: grid;
  min-height: 160px;
  place-items: center;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  color: #94a3b8;
}

.timeline-node {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline-node li {
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 18px;
}

.timeline-node li::before {
  position: absolute;
  top: 20px;
  bottom: 0;
  left: 7px;
  width: 2px;
  background: #e2e8f0;
  content: '';
}

.timeline-node li:last-child::before {
  display: none;
}

.timeline-dot {
  z-index: 1;
  width: 16px;
  height: 16px;
  margin-top: 3px;
  border: 3px solid #bfdbfe;
  border-radius: 999px;
  background: #2563eb;
}

.timeline-node strong,
.timeline-node small,
.timeline-node p {
  display: block;
  margin: 0;
}

.timeline-node small {
  margin-top: 3px;
  color: #94a3b8;
}

.timeline-node p {
  margin-top: 7px;
  color: #475569;
  line-height: 1.6;
}

.upload-node {
  display: grid;
  gap: 14px;
}

.upload-dropzone {
  display: grid;
  place-items: center;
  gap: 7px;
  min-height: 142px;
  border: 1px dashed #93c5fd;
  border-radius: 18px;
  background: linear-gradient(180deg, #eff6ff, #ffffff);
  color: #1d4ed8;
  text-align: center;
}

.upload-dropzone span {
  color: #64748b;
  font-size: 13px;
}

.upload-list,
.tree-node-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.upload-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px 12px;
  background: #ffffff;
}

.upload-list span {
  color: #0f172a;
  font-weight: 800;
}

.upload-list small {
  color: #64748b;
}

.upload-list em {
  border-radius: 999px;
  padding: 3px 8px;
  background: #dcfce7;
  color: #166534;
  font-style: normal;
}

.upload-list li.error em {
  background: #fee2e2;
  color: #991b1b;
}

.tree-node-list li {
  display: grid;
  gap: 7px;
}

.tree-node-list ul {
  display: grid;
  gap: 6px;
  margin: 0 0 0 24px;
  padding: 0;
  list-style: none;
}

.tree-row,
.tree-node-list ul li {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 9px 11px;
  background: #ffffff;
  color: #334155;
  font-weight: 700;
}

.tree-row span {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
  background: #2563eb;
}

.result-node {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 36px 24px;
  text-align: center;
}

.result-node > span {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 999px;
  background: #dcfce7;
  color: #166534;
  font-size: 28px;
  font-weight: 900;
}

.result-node.error > span {
  background: #fee2e2;
  color: #991b1b;
}

.result-node strong {
  color: #0f172a;
  font-size: 22px;
}

.result-node p {
  max-width: 560px;
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.dialog-preview {
  display: grid;
  min-height: 320px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.08);
}

.modal-preview {
  place-items: center;
}

.dialog-panel,
.drawer-panel {
  overflow: auto;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.18);
}

.dialog-panel {
  width: min(620px, 92%);
  border-radius: 20px;
  padding: 18px;
}

.drawer-preview {
  justify-content: end;
}

.drawer-panel {
  width: min(430px, 92%);
  height: 100%;
  border-radius: 18px 0 0 18px;
  padding: 18px;
}

.dialog-panel > header,
.drawer-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.dialog-panel h3,
.drawer-panel h3 {
  margin: 0;
  color: #0f172a;
}

.dialog-panel p,
.drawer-panel p {
  color: #475569;
  line-height: 1.7;
}

.dialog-children {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

@media (max-width: 720px) {
  .form-grid,
  .children-grid,
  .metric-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .chart-row,
  .upload-list li {
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

/* A9_7_PREMIUM_RUNTIME_NODE_VISUALS: avoid rough native-looking generated controls. */
.runtime-node :is(input, select, textarea) {
  min-height: 44px;
  border-color: rgba(148, 163, 184, 0.45);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.runtime-node :is(input, select, textarea):focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.runtime-node button {
  border-radius: 999px;
  font-weight: 850;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.10);
}

.runtime-node:not(.node-button):not(.node-link):not(.node-divider) {
  border-color: rgba(203, 213, 225, 0.82);
  background: linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.96));
  box-shadow: 0 20px 52px rgba(15, 23, 42, 0.08);
}

/* A9.9.11 Single Action Column Runtime Closure */
.table-wrap {
  overflow-x: auto !important;
  overflow-y: visible !important;
}

.table-wrap table {
  min-width: max(100%, 980px) !important;
  table-layout: auto !important;
}

.table-wrap th,
.table-wrap td {
  white-space: nowrap !important;
}

.table-wrap th.row-action-header,
.table-wrap td.row-actions {
  width: 148px !important;
  min-width: 148px !important;
  max-width: 180px !important;
  text-align: center !important;
  white-space: nowrap !important;
}

.row-actions {
  display: flex !important;
  flex-wrap: nowrap !important;
  justify-content: center !important;
  align-items: center !important;
  gap: 8px !important;
}

.row-actions button {
  white-space: nowrap !important;
  flex: 0 0 auto !important;
}



/* A9.10.14.4.3.13.2.11_TABLE_ROW_ACTION_DISCIPLINE */
.table-wrap {
  overflow-x: auto;
  overflow-y: visible;
}

.table-wrap table {
  min-width: max(100%, 1080px);
  table-layout: auto;
}

.row-action-header,
.row-actions {
  min-width: 240px;
  width: 260px;
  text-align: right;
  white-space: nowrap;
}

.row-actions {
  flex-wrap: nowrap;
  justify-content: flex-end;
}

.row-actions button {
  padding: 7px 10px;
  white-space: nowrap;
}

/* A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM */
.runtime-node {
  position: relative;
  overflow: hidden;
}

.runtime-node::before {
  content: "";
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, var(--runtime-primary), var(--runtime-secondary));
  opacity: .62;
  pointer-events: none;
}

.node-header {
  gap: 6px;
  margin-bottom: calc(var(--runtime-grid-gap) * .72);
}

.node-header h3 {
  letter-spacing: -0.018em;
}

.node-header p {
  max-width: 820px;
  line-height: 1.72;
}

.form-grid label > span,
.auth-field > span {
  font-weight: 850;
  color: var(--runtime-text-main);
}

.form-grid input,
.form-grid select,
.form-grid textarea,
.auth-field input,
.auth-field select,
.auth-field textarea {
  min-height: var(--runtime-control-height);
  border-radius: var(--runtime-radius-sm);
  background: color-mix(in srgb, var(--runtime-surface) 92%, var(--runtime-page-bg));
}

.table-wrap {
  border-radius: calc(var(--runtime-radius) - 2px);
  border: 1px solid var(--runtime-border);
  overflow: auto;
  background: var(--runtime-surface);
}

.table-wrap table {
  border-collapse: separate;
  border-spacing: 0;
}

.table-wrap thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 1px solid var(--runtime-border);
  backdrop-filter: blur(12px);
}

.table-wrap tbody tr {
  transition: background .14s ease, transform .14s ease;
}

.table-wrap tbody tr:hover {
  background: color-mix(in srgb, var(--runtime-primary) 5%, transparent);
}

.tag,
.chart-badge,
.coverage-row em,
.binding-card em {
  border: 1px solid color-mix(in srgb, currentColor 16%, transparent);
  border-radius: 999px;
  font-weight: 850;
}

.alert-node,
.state-banner,
.empty-state {
  border: 1px solid var(--runtime-border);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.72);
}

.row-actions {
  white-space: nowrap;
}

</style>

<style scoped>
/* A9.10.14.4.3.13.2.12_TABLE_HORIZONTAL_SCROLL_WIDTH_FIX
   Generic enterprise table overflow discipline.
   Many-column tables must remain usable in preview and generated apps.
*/
:deep(.table-wrapper),
:deep(.table-scroll),
.table-wrapper,
.table-scroll {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
}

:deep(.table-wrapper::-webkit-scrollbar),
:deep(.table-scroll::-webkit-scrollbar),
.table-wrapper::-webkit-scrollbar,
.table-scroll::-webkit-scrollbar {
  height: 10px;
}

:deep(.table-wrapper::-webkit-scrollbar-thumb),
:deep(.table-scroll::-webkit-scrollbar-thumb),
.table-wrapper::-webkit-scrollbar-thumb,
.table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.55);
}

:deep(.runtime-table),
.runtime-table {
  width: max-content;
  min-width: max(100%, var(--a9-runtime-table-min-width, 880px));
  table-layout: auto;
}

:deep(.runtime-table th),
:deep(.runtime-table td),
.runtime-table th,
.runtime-table td {
  white-space: nowrap;
}

:deep(.runtime-table th:last-child),
:deep(.runtime-table td:last-child),
.runtime-table th:last-child,
.runtime-table td:last-child {
  min-width: 180px;
}

:deep(.row-actions),
.row-actions {
  flex-wrap: nowrap;
  justify-content: flex-start;
  min-width: max-content;
}

/* A9.10.14.4.3.13.2.12.1_TABLE_OVERFLOW_ACTION_COLUMN_FIX
   Generic table overflow contract: data columns and row actions must never squeeze
   each other. Keep the horizontal scroll inside the table card and reserve a
   stable action column for any LIST/TABLE page. */
.table-wrap {
  width: 100% !important;
  max-width: 100% !important;
  overflow-x: auto !important;
  overflow-y: visible !important;
  padding-bottom: 8px !important;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
}

.table-wrap table {
  width: max-content !important;
  min-width: max(100%, var(--a9-runtime-table-min-width, 1280px)) !important;
  table-layout: auto !important;
  border-collapse: separate !important;
  border-spacing: 0 !important;
}

.table-wrap th,
.table-wrap td {
  white-space: nowrap !important;
  vertical-align: middle !important;
}

.table-wrap th.row-action-header,
.table-wrap td.row-actions {
  width: var(--a9-runtime-action-column-width, 320px) !important;
  min-width: var(--a9-runtime-action-column-width, 320px) !important;
  max-width: none !important;
  white-space: nowrap !important;
  text-align: left !important;
  background: rgba(255, 255, 255, 0.98) !important;
}

.table-wrap th.row-action-header {
  position: sticky !important;
  right: 0 !important;
  z-index: 4 !important;
  box-shadow: -10px 0 18px rgba(15, 23, 42, 0.06) !important;
}

.table-wrap td.row-actions {
  display: table-cell !important;
  position: sticky !important;
  right: 0 !important;
  z-index: 3 !important;
  box-shadow: -10px 0 18px rgba(15, 23, 42, 0.04) !important;
}

.table-wrap td.row-actions button {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  flex: 0 0 auto !important;
  white-space: nowrap !important;
  margin-right: 8px !important;
  padding: 6px 10px !important;
  min-width: 72px !important;
}

.table-wrap td.row-actions button:last-child {
  margin-right: 0 !important;
}

/* A9.10.14.4.3.15.4.3_RUNTIME_INDUSTRY_STYLE_RENDER */
.runtime-node:not(.node-button):not(.node-link):not(.node-divider) {
  border-color: var(--runtime-border);
  border-radius: var(--runtime-radius);
  padding: var(--runtime-card-padding);
  background: var(--runtime-surface);
  box-shadow: var(--runtime-card-shadow);
}

.node-header,
.page-header-node,
.toolbar-node {
  gap: var(--runtime-grid-gap);
  margin-bottom: var(--runtime-grid-gap);
}

.form-grid,
.children-grid,
.metric-grid,
.stats-grid {
  gap: var(--runtime-grid-gap);
}

.form-field {
  gap: max(6px, calc(var(--runtime-grid-gap) * 0.55));
}

.form-field input,
.form-field textarea,
.form-field select,
.auth-field input,
.auth-field textarea,
.auth-field select {
  min-height: var(--runtime-control-height);
  border-color: var(--runtime-border);
  border-radius: var(--runtime-radius-sm);
}

.form-field input:focus,
.form-field textarea:focus,
.form-field select:focus {
  border-color: var(--runtime-primary);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--runtime-primary) 12%, transparent);
}

button,
.primary-button,
.link-button {
  border-radius: var(--runtime-button-radius);
}

.primary-button {
  border-color: var(--runtime-primary);
  background: var(--runtime-primary);
}

button:not(.link-button) {
  border-color: color-mix(in srgb, var(--runtime-primary) 24%, var(--runtime-border));
  color: var(--runtime-primary);
}

button:not(.link-button):hover {
  background: color-mix(in srgb, var(--runtime-primary) 10%, #ffffff);
}

th,
td {
  padding: var(--runtime-table-cell-padding-y) var(--runtime-table-cell-padding-x);
}

th {
  background: color-mix(in srgb, var(--runtime-primary) 6%, #ffffff);
  color: var(--runtime-text-muted);
}

.tag,
.chart-badge,
.coverage-row em,
.binding-card em {
  background: var(--runtime-tag-bg);
  color: var(--runtime-tag-text);
}

.stat-card,
.metric-grid article {
  border-color: var(--runtime-border);
  border-radius: var(--runtime-radius);
  background: linear-gradient(180deg, var(--runtime-surface) 0%, color-mix(in srgb, var(--runtime-page-bg) 55%, #ffffff) 100%);
  box-shadow: var(--runtime-card-shadow);
}

.chart-track i {
  background: linear-gradient(90deg, var(--runtime-primary), var(--runtime-secondary));
}

.timeline-dot,
.tree-row span {
  background: var(--runtime-primary);
  border-color: color-mix(in srgb, var(--runtime-primary) 28%, #ffffff);
}

.alert-node,
.state-banner {
  border-radius: var(--runtime-radius-sm);
}

.alert-node.warning,
.timeline-node li.warning .timeline-dot {
  background: var(--runtime-status-warning-bg);
  color: var(--runtime-status-warning-text);
}

.alert-node.error,
.timeline-node li.error .timeline-dot {
  background: var(--runtime-status-error-bg);
  color: var(--runtime-status-error-text);
}

.upload-dropzone {
  border-color: color-mix(in srgb, var(--runtime-primary) 38%, transparent);
  background: color-mix(in srgb, var(--runtime-primary) 8%, #ffffff);
  color: var(--runtime-primary);
}

.table-wrap th.row-action-header,
.table-wrap td.row-actions {
  width: var(--runtime-action-column-width) !important;
  min-width: var(--runtime-action-column-width) !important;
}

</style>

