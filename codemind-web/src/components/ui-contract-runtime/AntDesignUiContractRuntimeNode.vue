<!-- A9.10.14.4.3.15.7.24L4_ANTD_RUNTIME_NODE
     Ant Design Vue 递归运行时节点：按 UI_CONTRACT 组件类型分发到真实 ant-design 组件。
     与 ElementPlusUiContractRuntimeNode 同构，但统一走 Ant Design，保证原型 = 交付代码。 -->
<template>
  <template v-if="isVisible">
    <!-- 容器：SECTION / CARD / GROUP / PANEL，递归渲染 children -->
    <ACard
      v-if="isContainerNode"
      :class="[antNodeClass, 'a9-ant-node-container']"
      :bordered="containerBordered"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || componentDescription || componentId"
    >
      <template v-if="shouldRenderHeader" #title>
        <div class="a9-ant-node-heading">
          <strong
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="title"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentTitle"
          >{{ componentTitle }}</strong>
          <span
            v-if="componentDescription"
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentDescription"
          >{{ componentDescription }}</span>
        </div>
      </template>

      <div class="a9-ant-node-children" :class="{ 'is-metric-grid': isMetricGrid }" :style="childrenStyle">
        <AntDesignUiContractRuntimeNode
          v-for="child in children"
          :key="childKey(child)"
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
        <AEmpty v-if="!children.length" description="暂无内容" />
      </div>
    </ACard>

    <!-- 指标卡：CARD / STAT / KPI 带 items -->
    <ACard
      v-else-if="isStatNode"
      :class="[antNodeClass, 'a9-ant-stat-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="METRIC"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-field-key="statFieldKey"
      :data-ui-element-label="statTitle()"
    >
      <div class="a9-ant-stat-grid" :style="statGridStyle">
        <div
          v-for="(item, index) in displayStatItems"
          :key="statItemKey(item, index)"
          class="a9-ant-stat-item"
          data-ui-selectable="true"
          data-ui-element-kind="METRIC"
          :data-ui-field-key="statItemKey(item, index)"
          :data-ui-element-label="statItemTitle(item)"
        >
          <AStatistic
            :title="statItemTitle(item)"
            :value="statItemValue(item)"
            :precision="statItemPrecision(item)"
          />
          <span v-if="statItemHint(item)" class="a9-ant-stat-hint">{{ statItemHint(item) }}</span>
        </div>
      </div>
      <span v-if="!displayStatItems.length" class="a9-ant-stat-hint">暂无统计数据</span>
    </ACard>

    <!-- 图表占位：CARD 带 chartType / series -->
    <ACard
      v-else-if="isChartNode"
      :class="[antNodeClass, 'a9-ant-chart-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '趋势分析'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '趋势分析' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <div class="a9-ant-chart-bars">
        <div
          v-for="(item, index) in chartItems"
          :key="index"
          class="a9-ant-chart-bar"
          :style="{ height: chartHeight(item) }"
          :title="`${chartLabel(item)}: ${chartValue(item)}`"
        >
          <span>{{ chartLabel(item) }}</span>
        </div>
      </div>
    </ACard>

    <!-- 表单：FORM / FILTER_FORM / ... -->
    <ACard
      v-else-if="isFormNode"
      :class="[antNodeClass, 'a9-ant-form-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || defaultFormTitle"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="title"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentTitle || defaultFormTitle"
          >{{ componentTitle || defaultFormTitle }}</strong>
          <span
            v-if="componentDescription"
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentDescription"
          >{{ componentDescription }}</span>
        </div>
      </template>

      <template v-if="formHeaderActions.length" #extra>
        <ASpace wrap>
          <AButton
            v-for="actionId in formHeaderActions"
            :key="actionId"
            :type="antButtonType(actionId)"
            :danger="actionDanger(actionId)"
            size="middle"
            :disabled="isDisabled || actionDisabled(actionId)"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >{{ actionLabel(actionId) }}</AButton>
        </ASpace>
      </template>

      <AForm class="a9-ant-node-form" :layout="formLabelPlacement" :model="formValues">
        <div class="a9-ant-form-grid" :style="{ gridTemplateColumns: `repeat(${formGridColumns}, minmax(0, 1fr))` }">
          <AFormItem
            v-for="field in visibleFields"
            :key="fieldKey(field)"
            :label="fieldLabel(field)"
            :validate-status="fieldError(field) ? 'error' : undefined"
            :help="fieldError(field) || undefined"
            class="a9-ant-form-item"
            data-ui-selectable="true"
            data-ui-element-kind="FIELD"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-field-key="fieldKey(field)"
            :data-ui-element-label="fieldLabel(field)"
          >
            <ASelect
              v-if="fieldControlKind(field) === 'SELECT'"
              class="a9-ant-control"
              :value="fieldStringValue(field)"
              :placeholder="fieldPlaceholder(field)"
              :options="fieldOptions(field)"
              :disabled="isDisabled || fieldIsDisabled(field)"
              allow-clear
              show-search
              @update:value="updateFieldValue(field, $event)"
            />

            <ADatePicker
              v-else-if="fieldControlKind(field) === 'DATE'"
              class="a9-ant-control"
              :value="fieldAnyValue(field)"
              :placeholder="fieldPlaceholder(field)"
              :disabled="isDisabled || fieldIsDisabled(field)"
              @update:value="updateFieldValue(field, $event)"
            />

            <AInputNumber
              v-else-if="fieldControlKind(field) === 'NUMBER'"
              class="a9-ant-control"
              :value="fieldNumberValue(field)"
              :disabled="isDisabled || fieldIsDisabled(field)"
              @update:value="updateFieldValue(field, $event)"
            />

            <ASwitch
              v-else-if="fieldControlKind(field) === 'SWITCH'"
              :checked="fieldBooleanValue(field)"
              :disabled="isDisabled || fieldIsDisabled(field)"
              @update:checked="updateFieldValue(field, $event)"
            />

            <ATextarea
              v-else-if="fieldControlKind(field) === 'TEXTAREA'"
              class="a9-ant-control"
              :value="fieldStringValue(field)"
              :placeholder="fieldPlaceholder(field)"
              :rows="3"
              :disabled="isDisabled || fieldIsDisabled(field)"
              allow-clear
              @update:value="updateFieldValue(field, $event)"
            />

            <AInput
              v-else
              class="a9-ant-control"
              :value="fieldStringValue(field)"
              :placeholder="fieldPlaceholder(field)"
              :type="fieldInputType(field)"
              :disabled="isDisabled || fieldIsDisabled(field)"
              allow-clear
              @update:value="updateFieldValue(field, $event)"
            />
          </AFormItem>
        </div>
      </AForm>
    </ACard>

    <!-- 表格：TABLE / DATA_TABLE / ... -->
    <ACard
      v-else-if="isTableNode"
      :class="[antNodeClass, 'a9-ant-table-card']"
      :bordered="catalogValue('bordered', true)"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || defaultTableTitle"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="title"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentTitle || defaultTableTitle"
          >{{ componentTitle || defaultTableTitle }}</strong>
          <span
            v-if="componentDescription"
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="description"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-element-label="componentDescription"
          >{{ componentDescription }}</span>
        </div>
      </template>

      <template v-if="tableHeaderActions.length" #extra>
        <ASpace wrap>
          <AButton
            v-for="actionId in tableHeaderActions"
            :key="actionId"
            :type="antButtonType(actionId)"
            :danger="actionDanger(actionId)"
            size="middle"
            :disabled="isDisabled || actionDisabled(actionId)"
            data-ui-selectable="true"
            data-ui-element-kind="ACTION"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-action-id="actionId"
            :data-ui-element-label="actionLabel(actionId)"
            @click="emitAction(actionId)"
          >{{ actionLabel(actionId) }}</AButton>
        </ASpace>
      </template>

      <ATable
        class="a9-ant-table"
        :columns="antdColumns"
        :data-source="tableRows"
        :row-key="rowKey"
        :pagination="catalogValue('pagination', false)"
        :bordered="tableBordered"
        :size="tableDensity"
        :loading="tableLoading"
        :row-class-name="tableRowClassName"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === ACTION_COLUMN_KEY">
            <ASpace wrap size="small" class="a9-ant-action-cell">
              <AButton
                v-for="actionId in rowActions"
                :key="actionId"
                type="link"
                size="small"
                :danger="actionDanger(actionId)"
                :disabled="isDisabled || actionDisabled(actionId)"
                data-ui-selectable="true"
                data-ui-element-kind="TABLE_ACTION"
                :data-ui-component-id="componentId"
                :data-ui-component-type="componentType"
                :data-ui-action-id="actionId"
                :data-ui-element-label="actionLabel(actionId)"
                @click="emitAction(actionId, record)"
              >{{ actionLabel(actionId) }}</AButton>
            </ASpace>
          </template>

          <template v-else>
            <ATag
              v-if="isTagColumnByKey(String(column.key || column.dataIndex || ''))"
              :color="statusTagColor(record[String(column.dataIndex)])"
              class="a9-ant-cell-mock-value"
              data-ui-mock-value="true"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_CELL"
              :data-ui-component-id="componentId"
              :data-ui-component-type="componentType"
              :data-ui-column-key="String(column.key || column.dataIndex || '')"
              :data-ui-element-label="`表格单元格 · ${String(column.title || column.key || '')}`"
            >{{ displayValue(record[String(column.dataIndex)]) }}</ATag>

            <span
              v-else
              class="a9-ant-cell-mock-value a9-ant-cell-content"
              data-ui-mock-value="true"
              data-ui-selectable="true"
              data-ui-element-kind="TABLE_CELL"
              :data-ui-component-id="componentId"
              :data-ui-component-type="componentType"
              :data-ui-column-key="String(column.key || column.dataIndex || '')"
              :data-ui-element-label="`表格单元格 · ${String(column.title || column.key || '')}`"
            >{{ displayValue(record[String(column.dataIndex)]) }}</span>
          </template>
        </template>

        <template #emptyText>
          <AEmpty description="暂无数据" />
        </template>
      </ATable>
    </ACard>

    <!-- 操作栏：TOOLBAR / ACTION_BAR -->
    <div
      v-else-if="isToolbarNode"
      :class="[antNodeClass, 'a9-ant-toolbar']"
      data-ui-selectable="true"
      data-ui-element-kind="ACTION_BAR"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '操作区'"
    >
      <ASpace wrap>
        <AButton
          v-for="actionId in actionIds"
          :key="actionId"
          :type="antButtonType(actionId)"
          :danger="actionDanger(actionId)"
          :disabled="isDisabled || actionDisabled(actionId)"
          data-ui-selectable="true"
          data-ui-element-kind="ACTION"
          :data-ui-component-id="componentId"
          :data-ui-component-type="componentType"
          :data-ui-action-id="actionId"
          :data-ui-element-label="actionLabel(actionId)"
          @click="emitAction(actionId)"
        >{{ actionLabel(actionId) }}</AButton>
      </ASpace>
    </div>

    <!-- 描述列表：DESCRIPTION_LIST / DESCRIPTIONS -->
    <ACard
      v-else-if="isDescriptionNode"
      :class="[antNodeClass, 'a9-ant-descriptions-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '概要信息'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '概要信息' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <ADescriptions :column="descriptionColumns" :bordered="descriptionBordered" :size="descriptionSize">
        <ADescriptionsItem
          v-for="item in descriptionItems"
          :key="descriptionItemKey(item)"
          :label="descriptionItemLabel(item)"
        >
          <span
            data-ui-selectable="true"
            data-ui-element-kind="FIELD"
            :data-ui-component-id="componentId"
            :data-ui-component-type="componentType"
            :data-ui-field-key="descriptionItemKey(item)"
            :data-ui-element-label="descriptionItemLabel(item)"
          >{{ displayValue(descriptionItemValue(item)) }}</span>
        </ADescriptionsItem>
      </ADescriptions>
    </ACard>

    <!-- 步骤：STEPS / WIZARD -->
    <ACard
      v-else-if="isStepsNode"
      :class="[antNodeClass, 'a9-ant-steps-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '处理进度'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '处理进度' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <ASteps :current="stepsCurrent" :status="stepsStatus" :direction="stepsDirection">
        <AStep
          v-for="item in stepItems"
          :key="stepItemKey(item)"
          :title="stepItemTitle(item)"
          :description="stepItemDescription(item)"
        />
      </ASteps>
    </ACard>

    <!-- 时间线：TIMELINE -->
    <ACard
      v-else-if="isTimelineNode"
      :class="[antNodeClass, 'a9-ant-timeline-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '动态记录'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '动态记录' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <ATimeline>
        <ATimelineItem
          v-for="item in timelineItems"
          :key="timelineItemKey(item)"
          :color="timelineItemColor(item)"
        >
          <strong>{{ timelineItemTitle(item) }}</strong>
          <p v-if="timelineItemContent(item)">{{ timelineItemContent(item) }}</p>
          <small v-if="timelineItemTime(item)">{{ timelineItemTime(item) }}</small>
        </ATimelineItem>
      </ATimeline>
    </ACard>

    <!-- 页签：TABS -->
    <ACard
      v-else-if="isTabsNode"
      :class="[antNodeClass, 'a9-ant-tabs-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '页签'"
    >
      <ATabs :active-key="activeTabKey" @update:activeKey="switchTab">
        <ATabPane v-for="tab in tabItems" :key="tabItemKey(tab)" :tab="tabItemTitle(tab)">
          <div class="a9-ant-tab-panel">{{ tabItemContent(tab) || '暂无内容' }}</div>
        </ATabPane>
      </ATabs>
    </ACard>

    <!-- 列表：LIST -->
    <ACard
      v-else-if="isListNode"
      :class="[antNodeClass, 'a9-ant-list-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '列表'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '列表' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <AList :data-source="listItems" item-layout="horizontal">
        <template #renderItem="{ item }">
          <AListItem>
            <AListItemMeta :title="listItemTitle(item)" :description="listItemDescription(item)" />
          </AListItem>
        </template>
      </AList>
    </ACard>

    <!-- 树：TREE -->
    <ACard
      v-else-if="isTreeNode"
      :class="[antNodeClass, 'a9-ant-tree-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '树形结构'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '树形结构' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <ATree :tree-data="treeData" default-expand-all />
    </ACard>

    <!-- 折叠面板：COLLAPSE -->
    <ACard
      v-else-if="isCollapseNode"
      :class="[antNodeClass, 'a9-ant-collapse-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '折叠面板'"
    >
      <template #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '折叠面板' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <ACollapse>
        <ACollapsePanel
          v-for="item in collapseItems"
          :key="collapseItemKey(item)"
          :header="collapseItemTitle(item)"
        >
          <p>{{ collapseItemContent(item) || '暂无内容' }}</p>
        </ACollapsePanel>
      </ACollapse>
    </ACard>

    <!-- 提示：ALERT -->
    <AAlert
      v-else-if="isAlertNode"
      :class="[antNodeClass, 'a9-ant-alert']"
      :message="alertMessage"
      :description="alertDescription"
      :type="alertType"
      show-icon
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="alertMessage"
    />

    <!-- 空状态：EMPTY_STATE -->
    <ACard
      v-else-if="isEmptyNode"
      :class="[antNodeClass, 'a9-ant-empty-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '暂无数据'"
    >
      <AEmpty :description="componentTitle || componentDescription || '暂无数据'" />
    </ACard>

    <!-- 结果页：RESULT -->
    <ACard
      v-else-if="isResultNode"
      :class="[antNodeClass, 'a9-ant-result-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '处理结果'"
    >
      <AResult :status="resultStatus" :title="componentTitle || '处理结果'" :sub-title="componentDescription" />
    </ACard>

    <!-- 进度条：PROGRESS -->
    <ACard
      v-else-if="isProgressNode"
      :class="[antNodeClass, 'a9-ant-progress-card']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '进度'"
    >
      <AProgress :percent="progressPercent" :status="progressStatus" />
    </ACard>

    <!-- 分割线 -->
    <ADivider
      v-else-if="isDividerNode"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '分割线'"
    >{{ componentTitle }}</ADivider>

    <!-- 标签 / 徽标 / 面包屑 / 头像 / 文本 -->
    <ATag
      v-else-if="isTagNode"
      :color="componentProps.color"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '标签'"
    >{{ componentTitle || '标签' }}</ATag>

    <ABadge v-else-if="isBadgeNode" :count="componentProps.count" :status="badgeStatus">
      <span>{{ componentTitle || '内容' }}</span>
    </ABadge>

    <ABreadcrumb
      v-else-if="isBreadcrumbNode"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || '面包屑'"
    >
      <ABreadcrumbItem v-for="item in breadcrumbItems" :key="breadcrumbItemKey(item)">
        {{ breadcrumbItemLabel(item) }}
      </ABreadcrumbItem>
    </ABreadcrumb>

    <AAvatar
      v-else-if="isAvatarNode"
      :src="componentProps.src"
      :size="componentProps.size"
    >{{ componentTitle || '头像' }}</AAvatar>

    <AText v-else-if="isTextNode" :type="componentProps.type || componentProps.textType">
      {{ componentTitle || componentDescription || textProp('content') || '文本' }}
    </AText>

    <ASpin v-else-if="isSpinNode" />

    <ASegmented
      v-else-if="isSegmentedNode"
      :options="segmentedOptions"
      :value="segmentedValue"
      @update:value="updateSegmentedValue"
    />

    <APagination
      v-else-if="isPaginationNode"
      class="a9-ant-pagination"
      :total="paginationTotal"
      :page-size="paginationPageSize"
      :current="paginationCurrent"
      show-size-changer
      @update:current="updatePaginationCurrent"
      @update:pageSize="updatePaginationPageSize"
    />

    <!-- 未知类型：通用卡片兜底 -->
    <ACard
      v-else
      :class="[antNodeClass, 'a9-ant-node-generic']"
      :bordered="true"
      data-ui-selectable="true"
      data-ui-element-kind="COMPONENT"
      :data-ui-component-id="componentId"
      :data-ui-component-type="componentType"
      :data-ui-element-label="componentTitle || componentDescription || componentId"
    >
      <template v-if="componentTitle || componentDescription" #title>
        <div class="a9-ant-node-heading">
          <strong>{{ componentTitle || '内容区' }}</strong>
          <span v-if="componentDescription">{{ componentDescription }}</span>
        </div>
      </template>
      <div v-if="children.length" class="a9-ant-node-children">
        <AntDesignUiContractRuntimeNode
          v-for="child in children"
          :key="childKey(child)"
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
      <p v-else>{{ componentDescription || componentTitle || '内容区' }}</p>
    </ACard>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Alert as AAlert,
  Avatar as AAvatar,
  Badge as ABadge,
  Breadcrumb as ABreadcrumb,
  BreadcrumbItem as ABreadcrumbItem,
  Button as AButton,
  Card as ACard,
  Collapse as ACollapse,
  DatePicker as ADatePicker,
  Descriptions as ADescriptions,
  Divider as ADivider,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  InputNumber as AInputNumber,
  List as AList,
  Pagination as APagination,
  Progress as AProgress,
  Result as AResult,
  Segmented as ASegmented,
  Select as ASelect,
  Space as ASpace,
  Spin as ASpin,
  Statistic as AStatistic,
  Steps as ASteps,
  Switch as ASwitch,
  Table as ATable,
  Tabs as ATabs,
  TabPane as ATabPane,
  Tag as ATag,
  Timeline as ATimeline,
  Tree as ATree,
  Typography as ATypography,
} from 'ant-design-vue'
import {
  classifyComponentKind,
  presetClassNames,
  presetProps,
} from './antDesignComponentCatalog'
import type {
  UiContractComponent,
  UiContractInteraction,
} from '../../types/uiContract'

const ATextarea = AInput.TextArea
const ADescriptionsItem = ADescriptions.Item
const AStep = ASteps.Step
const ATimelineItem = ATimeline.Item
const ACollapsePanel = ACollapse.Panel
const AText = ATypography.Text

// ant-design-vue 的 List.Item / List.Item.Meta 子组件
const AListItem = (AList as any).Item as any
const AListItemMeta = ((AList as any).Item as any)?.Meta as any

defineOptions({
  name: 'AntDesignUiContractRuntimeNode',
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
    label?: string
    dataBindingKey?: string
    tableComponentId?: string
    sourceComponentType?: string
  }]
  updateField: [payload: { fieldKey: string; value: unknown }]
  switchTab: [payload: { componentId: string; tabKey: string }]
}>()

type LooseRecord = Record<string, any>

const ACTION_COLUMN_KEY = '__row_actions'

const rawComponent = computed<LooseRecord>(() => asRecord(props.component))
const componentId = computed(() => String(rawComponent.value.componentId || rawComponent.value.id || '').trim())
const componentType = computed(() => String(rawComponent.value.type || rawComponent.value.componentType || '').trim().toUpperCase())
const componentTitle = computed(() => String(rawComponent.value.title || rawComponent.value.label || textProp('title') || '').trim())
const componentDescription = computed(() => String(rawComponent.value.description || rawComponent.value.subtitle || textProp('description') || '').trim())
const componentProps = computed<LooseRecord>(() => asRecord(rawComponent.value.props || rawComponent.value.properties || {}))
const children = computed<UiContractComponent[]>(() => arrayValue(rawComponent.value.children).filter(isObjectValue) as UiContractComponent[])

const kind = computed(() => classifyComponentKind(componentType.value))
const catalogProps = computed<LooseRecord>(() => asRecord(presetProps(componentType.value, componentProps.value)))
const catalogClasses = computed(() => presetClassNames(componentType.value, componentProps.value))
const antNodeClass = computed(() => [
  'a9-ant-node',
  `a9-ant-kind-${String(kind.value || 'unknown').toLowerCase().replace(/_/g, '-')}`,
  ...catalogClasses.value,
])

function catalogValue<T>(key: string, fallback: T): T {
  const value = catalogProps.value[key]
  return value === undefined ? fallback : value as T
}

const actionIds = computed<string[]>(() => uniqueStrings([
  ...normalizeActionIds(rawComponent.value.actionIds),
  ...normalizeActionIds(componentProps.value.actionIds),
  ...normalizeActionIds(componentProps.value.actions),
  ...normalizeActionIds(componentProps.value.buttons),
]))

const isVisible = computed(() => {
  if (!componentId.value) return true
  if (!props.visibleComponentIds.length) return true
  return props.visibleComponentIds.includes(componentId.value)
})

const isDisabled = computed(() => Boolean(componentId.value && props.disabledComponentIds.includes(componentId.value)))

const hasHeader = computed(() => Boolean(componentTitle.value || componentDescription.value))

const statItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items ||
    componentProps.value.metrics ||
    rawComponent.value.items ||
    rawComponent.value.metrics,
).map(asRecord).filter((item) => Object.keys(item).length))

const displayStatItems = computed<LooseRecord[]>(() => {
  if (statItems.value.length) return statItems.value
  const fallbackValue = statValue()
  if (fallbackValue === '-' && !statTitle()) return []
  return [{ label: statTitle(), value: fallbackValue }]
})

const statGridStyle = computed<Record<string, string>>(() => {
  const count = displayStatItems.value.length
  const columns = Math.max(1, Math.min(4, count || 1))
  return { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }
})

const isContainerNode = computed(() => kind.value === 'CONTAINER' && children.value.length > 0)
const isStatNode = computed(() => {
  if (kind.value === 'STAT') return true
  return kind.value === 'CONTAINER' && children.value.length === 0 && statItems.value.length > 0 && !isChartShape()
})
const isChartNode = computed(() => kind.value === 'CHART' || (kind.value === 'CONTAINER' && isChartShape()))
const isFormNode = computed(() => kind.value === 'FORM')
const isTableNode = computed(() => kind.value === 'TABLE')
const isToolbarNode = computed(() => kind.value === 'ACTION_BAR')
const isDescriptionNode = computed(() => kind.value === 'DESCRIPTION_LIST')
const isStepsNode = computed(() => kind.value === 'STEPS')
const isTimelineNode = computed(() => kind.value === 'TIMELINE')
const isTabsNode = computed(() => kind.value === 'TABS')
const isListNode = computed(() => kind.value === 'LIST')
const isTreeNode = computed(() => kind.value === 'TREE')
const isCollapseNode = computed(() => kind.value === 'COLLAPSE')
const isAlertNode = computed(() => kind.value === 'ALERT')
const isEmptyNode = computed(() => kind.value === 'EMPTY_STATE')
const isResultNode = computed(() => kind.value === 'RESULT')
const isProgressNode = computed(() => kind.value === 'PROGRESS')
const isDividerNode = computed(() => kind.value === 'DIVIDER')
const isTagNode = computed(() => kind.value === 'TAG')
const isBadgeNode = computed(() => kind.value === 'BADGE')
const isBreadcrumbNode = computed(() => kind.value === 'BREADCRUMB')
const isAvatarNode = computed(() => kind.value === 'AVATAR')
const isTextNode = computed(() => kind.value === 'TEXT')
const isSpinNode = computed(() => kind.value === 'SPIN')
const isSegmentedNode = computed(() => kind.value === 'SEGMENTED')
const isPaginationNode = computed(() => kind.value === 'PAGINATION')

const shouldRenderHeader = computed(() => {
  if (actionIds.value.length > 0) return true
  if (!hasHeader.value) return false
  return true
})

const containerBordered = computed(() => componentProps.value.bordered !== false)

const isMetricGrid = computed(() => children.value.length > 0 && children.value.every((child) => {
  const childType = String((child as LooseRecord)?.type || (child as LooseRecord)?.componentType || '').toUpperCase()
  const childKind = classifyComponentKind(childType)
  const childRecord = asRecord(child)
  const childProps = asRecord(childRecord.props)
  const hasItems = arrayValue(childProps.items || childRecord.items).length > 0
  return childKind === 'STAT' || (childKind === 'CONTAINER' && hasItems)
}))

const childrenStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {}
  if (!isMetricGrid.value) return style
  const layout = asRecord(componentProps.value.layout)
  const columns = Number(layout.columns) || Math.min(children.value.length || 4, 4)
  style.gridTemplateColumns = `repeat(${columns}, minmax(0, 1fr))`
  return style
})

const defaultFormTitle = computed(() => /FILTER|QUERY|SEARCH|ADVANCED/i.test(componentType.value) ? '查询条件' : '表单信息')
const defaultTableTitle = computed(() => '数据列表')

// ---- 表单 ----
const visibleFields = computed<LooseRecord[]>(() => arrayValue(
  rawComponent.value.fields ||
    componentProps.value.fields ||
    componentProps.value.formFields ||
    componentProps.value.controls,
).map(asRecord).filter((field) => field.hidden !== true && String(field.type || '').toUpperCase() !== 'HIDDEN'))

const formLabelPlacement = computed(() => {
  const placement = String(componentProps.value.labelPlacement || 'top').toLowerCase()
  return placement === 'left' || placement === 'right' ? 'horizontal' : 'vertical'
})

const formGridColumns = computed(() => {
  const layout = asRecord(componentProps.value.layout)
  const columns = Number(layout.columns) || Number(componentProps.value.columns) || 4
  return columns >= 1 && columns <= 12 ? columns : 4
})

const formHeaderActions = computed(() => {
  return dedupeActionIds(actionIds.value.filter((actionId) => !isRowAction(actionId)))
})

// ---- 表格 ----
const columns = computed<LooseRecord[]>(() => arrayValue(
  rawComponent.value.columns ||
    componentProps.value.columns ||
    componentProps.value.tableColumns,
).map(asRecord).filter((column) => Object.keys(column).length && column.hidden !== true))

const renderColumns = computed<LooseRecord[]>(() => {
  if (columns.value.length) {
    return columns.value.map((column, index) => {
      const key = String(column.columnKey || column.fieldKey || column.prop || column.key || `column_${index + 1}`)
      return {
        ...column,
        columnKey: key,
        fieldKey: String(column.fieldKey || column.prop || key),
        label: String(column.label || column.title || key),
      }
    })
  }

  const firstRow = rowsOf()[0]
  if (!firstRow) return []
  return Object.keys(firstRow).slice(0, 8).map((key) => ({ columnKey: key, fieldKey: key, label: key }))
})

const tableRows = computed<LooseRecord[]>(() => {
  const rows = rowsOf()
  return a9NormalizeRows(rows, renderColumns.value)
})

const tableDensity = computed(() => {
  const size = String(catalogValue('size', '') || '').toLowerCase()
  if (['small', 'middle', 'large'].includes(size)) return size
  const density = String(componentProps.value.density || '').toLowerCase()
  return density === 'compact' ? 'small' : 'middle'
})

const tableBordered = computed(() => Boolean(catalogValue('bordered', componentProps.value.bordered === true)))
const tableLoading = computed(() => props.activeTabs[`${componentId.value}:loading`] === 'true')

function tableRowClassName(_record: LooseRecord, index: number): string {
  const className = String(catalogValue('rowClassName', '') || '').trim()
  if (!className) return ''
  if (className === 'a9-ant-striped-row') return index % 2 === 1 ? className : ''
  return className
}

const rowActions = computed(() => {
  const explicit = uniqueStrings([
    ...normalizeActionIds(componentProps.value.rowActionIds),
    ...normalizeActionIds(componentProps.value.rowActions),
    ...normalizeActionIds(rawComponent.value.rowActionIds),
  ])

  const fromActionIds = actionIds.value.filter((actionId) => isRowAction(actionId))
  const merged = explicit.length ? explicit : fromActionIds
  return dedupeActionIds(merged).slice(0, 4)
})

const tableHeaderActions = computed(() => {
  return dedupeActionIds(actionIds.value.filter((actionId) => {
    if (isQueryOrResetAction(actionId)) return false
    if (isRowAction(actionId)) return false
    return true
  }))
})

const antdColumns = computed<LooseRecord[]>(() => {
  const result: LooseRecord[] = renderColumns.value.map((column) => ({
    title: column.label || column.columnKey,
    dataIndex: column.fieldKey,
    key: column.columnKey,
    width: columnWidth(column),
    ellipsis: true,
  }))

  if (rowActions.value.length) {
    result.push({
      title: actionColumnLabel(),
      dataIndex: ACTION_COLUMN_KEY,
      key: ACTION_COLUMN_KEY,
      width: actionColumnWidth(),
      fixed: 'right' as const,
    })
  }

  return result
})

// ---- 描述列表 ----
const descriptionItems = computed<LooseRecord[]>(() => {
  const fields = visibleFields.value
  if (fields.length) return fields

  return arrayValue(componentProps.value.items || rawComponent.value.items).map(asRecord).filter((item) => Object.keys(item).length)
})

const descriptionColumns = computed(() => {
  const columns = Number(componentProps.value.columns || componentProps.value.column)
  return Number.isFinite(columns) && columns > 0 ? columns : 2
})

const descriptionBordered = computed(() => Boolean(catalogValue('bordered', componentProps.value.bordered === true)))
const descriptionSize = computed(() => String(catalogValue('size', componentProps.value.size || 'middle')))

// ---- 步骤 ----
const stepItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items || componentProps.value.steps || rawComponent.value.steps,
).map(asRecord).filter((item) => Object.keys(item).length))

const stepsCurrent = computed(() => {
  const index = stepItems.value.findIndex((item) => String(item.status || '').toLowerCase() === 'process')
  return index >= 0 ? index : 0
})

const stepsStatus = computed(() => {
  const anyProcess = stepItems.value.some((item) => String(item.status || '').toLowerCase() === 'process')
  return anyProcess ? 'process' : 'finish'
})

const stepsDirection = computed(() => {
  const direction = String(componentProps.value.direction || 'horizontal').toLowerCase()
  return direction === 'vertical' ? 'vertical' : 'horizontal'
})

// ---- 时间线 ----
const timelineItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items || componentProps.value.records || rawComponent.value.items,
).map(asRecord).filter((item) => Object.keys(item).length))

// ---- 页签 ----
const tabItems = computed<LooseRecord[]>(() => arrayValue(
  rawComponent.value.tabs || componentProps.value.tabs,
).map(asRecord).filter((item) => Object.keys(item).length))

const activeTabKey = computed(() => props.activeTabs[componentId.value] || tabItemKey(tabItems.value[0]) || 'default')

// ---- 列表 ----
const listItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items || componentProps.value.data || rawComponent.value.items,
).map(asRecord).filter((item) => Object.keys(item).length))

// ---- 树 ----
const treeData = computed<any[]>(() => normalizeTreeData(arrayValue(
  componentProps.value.nodes || componentProps.value.items || componentProps.value.data || rawComponent.value.items,
)))

// ---- 折叠面板 ----
const collapseItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items || componentProps.value.panels || rawComponent.value.items,
).map(asRecord).filter((item) => Object.keys(item).length))

// ---- 提示 ----
const alertMessage = computed(() => String(componentTitle.value || textProp('message') || '提示'))
const alertDescription = computed(() => String(componentDescription.value || textProp('description') || ''))
const alertType = computed(() => {
  const level = String(componentProps.value.level || componentProps.value.type || 'info').toLowerCase()
  if (['success', 'warning', 'error', 'info'].includes(level)) return level as any
  return 'info'
})

// ---- 结果页 ----
const resultStatus = computed(() => {
  const status = String(componentProps.value.status || componentProps.value.result || 'success').toLowerCase()
  return ['success', 'error', 'info', 'warning', '404', '403', '500'].includes(status) ? (status as any) : 'success'
})

// ---- 进度 ----
const progressPercent = computed(() => {
  const percent = Number(componentProps.value.percent ?? componentProps.value.value ?? firstItemValue())
  return Number.isFinite(percent) ? Math.max(0, Math.min(100, percent)) : 60
})

const progressStatus = computed(() => {
  const status = String(componentProps.value.status || '').toLowerCase()
  return ['success', 'exception', 'normal', 'active'].includes(status) ? (status as any) : undefined
})

// ---- 图表 ----
const chartItems = computed<LooseRecord[]>(() => {
  const raw = arrayValue(
    componentProps.value.items || componentProps.value.series || rawComponent.value.items,
  ).map(asRecord).filter((item) => Object.keys(item).length)
  return raw.length ? raw : [{ label: '一', value: 40 }, { label: '二', value: 72 }, { label: '三', value: 56 }, { label: '四', value: 88 }]
})

// ---- 面包屑 ----
const breadcrumbItems = computed<LooseRecord[]>(() => arrayValue(
  componentProps.value.items || rawComponent.value.items,
).map(asRecord).filter((item) => Object.keys(item).length))

// ---- 分段控制器 ----
const segmentedOptions = computed(() => arrayValue(
  componentProps.value.options || rawComponent.value.options,
).map((option) => {
  if (typeof option === 'string') return { label: option, value: option }
  const record = asRecord(option)
  return { label: String(record.label || record.title || record.value || ''), value: String(record.value || record.label || '') }
}).filter((option) => option.label && option.value))

const segmentedValue = computed(() => props.formValues[`${componentId.value}:segmented`] ?? componentProps.value.value)

// ---- 分页 ----
const paginationTotal = computed(() => Number(componentProps.value.total || 100))
const paginationPageSize = computed(() => Number(componentProps.value.pageSize || componentProps.value.page_size || 20))
const paginationCurrent = computed(() => Number(componentProps.value.current || 1))

// ---- 徽标 ----
const badgeStatus = computed(() => {
  const status = String(componentProps.value.status || '').toLowerCase()
  return ['success', 'processing', 'default', 'error', 'warning'].includes(status) ? (status as any) : undefined
})

function isChartShape(): boolean {
  const role = String(componentProps.value.visualRole || '').toLowerCase()
  return role.includes('chart') || Boolean(componentProps.value.chartType)
}

function chartLabel(item: LooseRecord): string {
  return String(item.label || item.name || item.title || '')
}

function chartValue(item: LooseRecord): number {
  const raw = Number(item.value ?? item.count ?? item.amount ?? 0)
  return Number.isFinite(raw) ? raw : 0
}

function chartHeight(item: LooseRecord): string {
  const max = Math.max(1, ...chartItems.value.map(chartValue))
  const value = chartValue(item)
  return `${Math.max(8, Math.round((value / max) * 100))}%`
}

function statItemKey(item: LooseRecord, index: number): string {
  return String(item.fieldKey || item.key || item.id || `stat_${index}`)
}

function statItemTitle(item: LooseRecord): string {
  return String(item.label || item.title || item.name || item.key || '指标')
}

function statItemValue(item: LooseRecord): string | number {
  const raw = item.value ?? item.count ?? item.amount ?? item.number ?? item.total
  if (raw === null || raw === undefined) return '-'
  return typeof raw === 'number' ? raw : String(raw)
}

function statItemHint(item: LooseRecord): string {
  return String(item.hint || item.trend || item.description || '')
}

function statItemPrecision(item: LooseRecord): number | undefined {
  const value = statItemValue(item)
  return typeof value === 'number' && !Number.isInteger(value) ? 2 : undefined
}

// 单指标兜底：STAT 组件若直接携带 value/count 而没有 items 数组时使用。
function singleStatValue(): string | number {
  const raw = componentProps.value.value ?? componentProps.value.count ?? componentProps.value.amount
  if (raw === null || raw === undefined) return '-'
  return typeof raw === 'number' ? raw : String(raw)
}

function statTitle(): string {
  const item = statItems.value[0] || {}
  return String(componentTitle.value || item.label || item.title || item.key || '指标')
}

function statValue(): string | number {
  const item = statItems.value[0]
  if (item && Object.keys(item).length) return statItemValue(item)
  return singleStatValue()
}

const statFieldKey = computed(() => {
  const item = statItems.value[0] || {}
  return String(item.fieldKey || item.key || item.id || componentId.value || 'stat')
})

function firstItemValue(): number {
  const item = statItems.value[0] || chartItems.value[0] || {}
  const raw = Number(item.value ?? item.count ?? item.amount)
  return Number.isFinite(raw) ? raw : 0
}

// ---- 工具函数 ----
function asRecord(value: unknown): LooseRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as LooseRecord : {}
}

function isObjectValue(value: unknown): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function uniqueStrings(values: unknown[]): string[] {
  const result: string[] = []
  for (const value of values) {
    const item = String(value || '').trim()
    if (item && !result.includes(item)) result.push(item)
  }
  return result
}

function normalizeActionIds(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    if (typeof item === 'string') return item.trim()
    const record = asRecord(item)
    return String(record.actionId || record.id || record.key || '').trim()
  }).filter(Boolean)
}

function actionById(actionId: unknown): LooseRecord | undefined {
  const id = String(actionId || '').trim()
  return props.interactions.find((action) => String(asRecord(action).actionId || asRecord(action).interactionId || asRecord(action).id) === id) as LooseRecord | undefined
}

function actionLabel(actionId: unknown): string {
  const id = String(actionId || '').trim()
  const action = actionById(id)
  const label = String(action?.label || action?.title || action?.name || '').trim()
  if (label) return label
  return inferActionLabel(id) || id || '操作'
}

function actionText(actionId: unknown): string {
  const id = String(actionId || '').trim()
  const action = actionById(id)
  return `${id} ${action?.type || action?.interactionType || ''} ${action?.label || ''}`.toLowerCase()
}

function inferActionLabel(actionId: string): string {
  const text = actionId.toLowerCase()
  if (text.includes('create') || text.includes('add') || text.includes('new')) return '新增'
  if (text.includes('save')) return '保存'
  if (text.includes('submit')) return '提交'
  if (text.includes('search') || text.includes('query')) return '查询'
  if (text.includes('reset')) return '重置'
  if (text.includes('edit') || text.includes('update')) return '编辑'
  if (text.includes('detail') || text.includes('view')) return '查看'
  if (text.includes('delete') || text.includes('remove')) return '删除'
  if (text.includes('audit') || text.includes('approve')) return '审核'
  if (text.includes('cancel')) return '取消'
  if (text.includes('export')) return '导出'
  if (text.includes('import')) return '导入'
  if (text.includes('back')) return '返回'
  if (text.includes('refresh')) return '刷新'
  return ''
}

function isQueryOrResetAction(actionId: unknown): boolean {
  const text = actionText(actionId)
  const label = actionLabel(actionId)
  return text.includes('query') || text.includes('search') || text.includes('filter') || text.includes('reset') ||
    label.includes('查询') || label.includes('搜索') || label.includes('筛选') || label.includes('重置')
}

function isCreateAction(actionId: unknown): boolean {
  const text = actionText(actionId)
  const label = actionLabel(actionId)
  return text.includes('create') || text.includes('add') || text.includes('new') ||
    label.includes('新增') || label.includes('新建') || label.includes('创建') || label.includes('录入')
}

function isRowAction(actionId: unknown): boolean {
  const text = actionText(actionId)
  const label = actionLabel(actionId)
  return text.includes('view') || text.includes('detail') || text.includes('edit') || text.includes('delete') ||
    text.includes('remove') || text.includes('audit') ||
    label.includes('查看') || label.includes('详情') || label.includes('编辑') || label.includes('删除') || label.includes('审核')
}

function actionSemanticKey(actionId: string): string {
  if (isCreateAction(actionId)) return 'CREATE'
  if (isQueryOrResetAction(actionId)) return actionLabel(actionId).includes('重置') ? 'RESET' : 'QUERY'
  if (isRowAction(actionId)) return `ROW:${actionLabel(actionId)}`
  return `ACTION:${actionLabel(actionId)}`
}

function dedupeActionIds(actionIds: string[]): string[] {
  const result: string[] = []
  const seen = new Set<string>()
  for (const actionId of actionIds) {
    const semantic = actionSemanticKey(actionId)
    if (seen.has(semantic)) continue
    seen.add(semantic)
    result.push(actionId)
  }
  return result
}

function antButtonType(actionId: unknown): 'primary' | 'default' | 'dashed' | 'link' | 'text' {
  const label = actionLabel(actionId)
  const text = actionText(actionId)
  if (isCreateAction(actionId) || label.includes('保存') || label.includes('提交') || text.includes('submit') || text.includes('save')) return 'primary'
  if (isRowAction(String(actionId || ''))) return 'link'
  return 'default'
}

function actionDanger(actionId: unknown): boolean {
  const label = actionLabel(actionId)
  const text = actionText(actionId)
  return text.includes('delete') || text.includes('remove') || label.includes('删除') || label.includes('作废')
}

function actionDisabled(actionId: unknown): boolean {
  return Boolean(actionById(actionId)?.disabled)
}

function emitAction(actionId: string, row?: LooseRecord) {
  const sourceId = componentId.value
  const sourceComponent = sourceId ? (props.componentMap[sourceId] || props.component) : props.component
  const sourceType = componentType.value
  const table = isTableNode.value ? props.component : sourceComponent
  const tableProps = asRecord(asRecord(table).props || {})
  const tableId = String(asRecord(table).componentId || '')
  const dataBindingKey = String(tableProps.dataKey || tableProps.mockDataKey || tableId || sourceId || 'rows')

  emit('execute', {
    actionId,
    componentId: sourceId,
    row,
    label: actionLabel(actionId),
    dataBindingKey,
    tableComponentId: tableId,
    sourceComponentType: sourceType,
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
  if (raw.includes('DATE') || raw.includes('TIME')) return 'DATE'
  if (raw.includes('BOOL') || raw.includes('SWITCH') || raw.includes('CHECKBOX')) return 'SWITCH'
  if (raw.includes('NUMBER') || raw.includes('AMOUNT') || raw.includes('PRICE') || raw.includes('COUNT')) return 'NUMBER'
  if (raw.includes('TEXTAREA') || raw.includes('MULTILINE') || raw.includes('LONG_TEXT')) return 'TEXTAREA'
  return 'INPUT'
}

function fieldInputType(field: LooseRecord): 'text' | 'password' | 'email' {
  const raw = String(field.type || '').toUpperCase()
  if (raw === 'PASSWORD') return 'password'
  if (raw === 'EMAIL') return 'email'
  return 'text'
}

function fieldOptions(field: LooseRecord): Array<{ label: string; value: string }> {
  const explicit = arrayValue(field.options || field.enumValues || field.values).map((option) => {
    if (typeof option === 'string') {
      const text = option.trim()
      return text ? { label: text, value: text } : null
    }
    const record = asRecord(option)
    const value = String(record.value || record.key || record.code || record.label || '').trim()
    const label = String(record.label || record.name || record.title || value).trim()
    return value ? { label: label || value, value } : null
  }).filter(Boolean) as Array<{ label: string; value: string }>

  if (explicit.length) return explicit

  const text = `${fieldLabel(field)} ${fieldKey(field)}`.toLowerCase()
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
  return [
    { label: '选项一', value: '选项一' },
    { label: '选项二', value: '选项二' },
    { label: '选项三', value: '选项三' },
  ]
}

function fieldIsDisabled(field: LooseRecord): boolean {
  return Boolean(field.disabled || field.readonly || field.readOnly)
}

function updateFieldValue(field: LooseRecord, value: unknown) {
  const key = fieldKey(field)
  if (!key) return
  emit('updateField', { fieldKey: key, value })
}

function rowsOf(): LooseRecord[] {
  const componentPropsRec = componentProps.value
  const dataKey = String(componentPropsRec.dataKey || componentPropsRec.mockDataKey || rawComponent.value.dataKey || componentId.value || 'rows').trim()
  const runtimeData = asRecord(props.mockData)

  const candidates = [
    runtimeData[dataKey],
    componentId.value ? runtimeData[componentId.value] : null,
    runtimeData.rows,
    runtimeData.list,
    runtimeData.tableRows,
    componentPropsRec.rows,
    componentPropsRec.data,
    rawComponent.value.rows,
    rawComponent.value.data,
  ]

  for (const candidate of candidates) {
    const rows = arrayValue(candidate).map(asRecord).filter((row) => Object.keys(row).length)
    if (rows.length) return rows
  }

  return []
}

function actionColumnLabel(): string {
  return String(componentProps.value.actionColumnLabel || componentProps.value.rowActionColumnLabel || componentProps.value.operationColumnLabel || '操作')
}

function actionColumnWidth(): number {
  const raw = Number(componentProps.value.actionColumnWidth || 190)
  return Number.isFinite(raw) && raw > 0 ? raw : 190
}

function columnWidth(column: LooseRecord): number {
  const width = Number(column.width || column.minWidth || 0)
  if (Number.isFinite(width) && width > 0) return width
  const label = String(column.label || column.columnKey || '')
  return Math.max(110, Math.min(220, label.length * 14 + 72))
}

function isTagColumnByKey(key: string): boolean {
  const column = renderColumns.value.find((item) => item.columnKey === key || item.fieldKey === key)
  if (!column) return false
  const text = `${column.fieldKey || ''} ${column.columnKey || ''} ${column.label || ''} ${column.type || ''}`.toLowerCase()
  return text.includes('status') || text.includes('state') || text.includes('tag') || text.includes('状态') || text.includes('阶段')
}

function statusTagColor(value: unknown): string {
  const text = String(value || '')
  if (/失败|拒绝|驳回|取消|作废|异常|error|fail|reject|cancel/i.test(text)) return 'red'
  if (/待|草稿|审核|pending|draft|review/i.test(text)) return 'orange'
  if (/进行|处理中|执行|process|running/i.test(text)) return 'blue'
  if (/完成|成功|通过|已生效|success|done|finish|approved/i.test(text)) return 'green'
  return 'default'
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function rowKey(row: LooseRecord, index?: number): string {
  return String(row.id || row.key || row.orderNo || row.code || row.no || `row_${index ?? 0}`)
}

function textProp(key: string): string {
  return String(componentProps.value[key] || '')
}

// ---- Mock 数据增强（真实业务观感）----
function a9Placeholder(value: unknown): boolean {
  const text = String(value ?? '').trim()
  return !text || /示例\d*|样例\d*|example\d*|mock\d*/i.test(text)
}

function a9IsStatusName(text: unknown): boolean {
  return /状态|阶段|流程|审核|status|state|stage/i.test(String(text ?? ''))
}

function a9IsDateName(text: unknown): boolean {
  return /日期|时间|创建|更新|date|time|created|updated/i.test(String(text ?? ''))
}

function a9IsAmountName(text: unknown): boolean {
  return /金额|价格|费用|总额|数量|amount|price|fee|count|number/i.test(String(text ?? ''))
}

function a9DefaultStatusOptions(): Array<{ label: string; value: string }> {
  return [
    { label: '草稿', value: '草稿' },
    { label: '待处理', value: '待处理' },
    { label: '处理中', value: '处理中' },
    { label: '已完成', value: '已完成' },
    { label: '已取消', value: '已取消' },
  ]
}

function a9StatusOptionsFromColumns(columns: LooseRecord[]): Array<{ label: string; value: string }> {
  const result: Array<{ label: string; value: string }> = []
  for (const column of columns) {
    const key = String(column.fieldKey || column.columnKey || '')
    const label = String(column.label || column.title || key)
    if (!a9IsStatusName(`${key} ${label}`)) continue
    for (const option of arrayValue(column.options || column.enumValues || column.values)) {
      const value = typeof option === 'string' ? option.trim() : String(asRecord(option).value || asRecord(option).label || '').trim()
      if (value && !result.some((item) => item.value === value)) result.push({ label: value, value })
    }
  }
  return result.length ? result : a9DefaultStatusOptions()
}

function a9MockValue(column: LooseRecord, rowIndex: number, statusOptions: Array<{ label: string; value: string }>): string {
  const key = String(column.fieldKey || column.columnKey || '')
  const label = String(column.label || column.title || key)
  const text = `${key} ${label}`
  const seq = rowIndex + 1

  if (a9IsStatusName(text)) return statusOptions[rowIndex % statusOptions.length].value
  if (/编号|单号|编码|code|no|number/i.test(text)) return `NO-20260819-${String(seq).padStart(3, '0')}`
  if (/客户|公司|企业|client|customer|company/i.test(text)) return ['上海华贸进出口有限公司', '宁波远航国际贸易有限公司', '深圳联海供应链有限公司'][rowIndex % 3]
  if (/负责人|经办人|联系人|owner|manager|person/i.test(text)) return ['张伟', '李娜', '王磊'][rowIndex % 3]
  if (a9IsAmountName(text)) return ['¥128,000.00', '¥76,500.00', '¥214,300.00'][rowIndex % 3]
  if (a9IsDateName(text)) return ['2026-08-19 09:30', '2026-08-18 15:20', '2026-08-17 11:05'][rowIndex % 3]
  if (/备注|说明|描述|remark|description/i.test(text)) return ['按合同约定执行', '等待客户确认', '需关注交付节点'][rowIndex % 3]

  return `${label}${seq}`
}

function a9NormalizeRows(rows: LooseRecord[], columns: LooseRecord[]): LooseRecord[] {
  if (!columns.length) return rows
  const statusOptions = a9StatusOptionsFromColumns(columns)
  const normalized = rows.map((row) => ({ ...row }))

  while (normalized.length < 3) normalized.push({})

  return normalized.slice(0, Math.max(3, Math.min(5, normalized.length))).map((row, rowIndex) => {
    for (const column of columns) {
      const key = String(column.fieldKey || column.columnKey || '')
      if (!key) continue
      const label = String(column.label || column.title || key)
      const nameText = `${key} ${label}`
      const current = row[key]

      if (a9IsStatusName(nameText)) {
        const allowed = statusOptions.map((item) => item.value)
        if (a9Placeholder(current) || !allowed.includes(String(current ?? ''))) {
          row[key] = statusOptions[rowIndex % statusOptions.length].value
        }
        continue
      }

      if (a9Placeholder(current)) {
        row[key] = a9MockValue(column, rowIndex, statusOptions)
      }
    }
    return row
  })
}

// ---- 各节点子项取值 ----
function descriptionItemKey(item: LooseRecord): string {
  return String(item.fieldKey || item.key || item.id || item.label || '')
}

function descriptionItemLabel(item: LooseRecord): string {
  return String(item.label || item.title || item.name || descriptionItemKey(item) || '字段')
}

function descriptionItemValue(item: LooseRecord): unknown {
  const key = descriptionItemKey(item)
  if (item.value !== undefined) return item.value
  if (key) {
    const runtime = asRecord(props.mockData)
    const detail = runtime.detailData || runtime.detail || runtime.formData || {}
    if (asRecord(detail)[key] !== undefined) return asRecord(detail)[key]
    if (props.formValues[key] !== undefined) return props.formValues[key]
  }
  return item.defaultValue ?? item.value ?? ''
}

function stepItemKey(item: LooseRecord): string {
  return String(item.key || item.id || item.title || item.label || '')
}

function stepItemTitle(item: LooseRecord): string {
  return String(item.title || item.label || item.name || '步骤')
}

function stepItemDescription(item: LooseRecord): string {
  return String(item.description || item.subtitle || '')
}

function timelineItemKey(item: LooseRecord): string {
  return String(item.key || item.id || item.title || item.time || item.createdAt || '')
}

function timelineItemTitle(item: LooseRecord): string {
  return String(item.title || item.operator || item.name || item.label || '记录')
}

function timelineItemContent(item: LooseRecord): string {
  return String(item.description || item.content || item.message || '')
}

function timelineItemTime(item: LooseRecord): string {
  return String(item.time || item.createdAt || item.date || '')
}

function timelineItemColor(item: LooseRecord): string {
  const status = String(item.status || item.type || '').toLowerCase()
  if (status.includes('finish') || status.includes('success') || status.includes('done')) return 'green'
  if (status.includes('process') || status.includes('running')) return 'blue'
  if (status.includes('error') || status.includes('fail')) return 'red'
  return 'gray'
}

function tabItemKey(tab: LooseRecord | undefined): string {
  if (!tab) return ''
  return String(tab.tabKey || tab.key || tab.id || '')
}

function tabItemTitle(tab: LooseRecord): string {
  return String(tab.title || tab.label || tab.name || tabItemKey(tab) || '页签')
}

function tabItemContent(tab: LooseRecord): string {
  return String(tab.description || tab.content || '')
}

function switchTab(key: string | number) {
  emit('switchTab', { componentId: componentId.value, tabKey: String(key ?? '') })
}

function listItemTitle(item: LooseRecord): string {
  return String(item.title || item.label || item.name || '')
}

function listItemDescription(item: LooseRecord): string {
  return String(item.description || item.subtitle || (item.value ?? ''))
}

function normalizeTreeData(items: unknown[]): LooseRecord[] {
  return items.map((item) => {
    if (typeof item === 'string') return { key: item, title: item }
    const record = asRecord(item)
    const key = String(record.key || record.id || record.title || record.label || '')
    const children = arrayValue(record.children).length ? normalizeTreeData(arrayValue(record.children)) : undefined
    return {
      key,
      title: String(record.title || record.label || record.name || key),
      children,
    }
  })
}

function collapseItemKey(item: LooseRecord): string {
  return String(item.key || item.id || item.title || item.label || '')
}

function collapseItemTitle(item: LooseRecord): string {
  return String(item.title || item.label || item.name || '面板')
}

function collapseItemContent(item: LooseRecord): string {
  return String(item.content || item.description || (item.value ?? ''))
}

function breadcrumbItemKey(item: LooseRecord): string {
  return String(item.key || item.id || item.label || item.title || '')
}

function breadcrumbItemLabel(item: LooseRecord): string {
  if (typeof item === 'string') return item
  return String(item.label || item.title || item.name || '')
}

function updateSegmentedValue(value: unknown) {
  emit('updateField', { fieldKey: `${componentId.value}:segmented`, value })
}

function updatePaginationCurrent(value: number) {
  emit('updateField', { fieldKey: `${componentId.value}:page`, value })
}

function updatePaginationPageSize(value: number) {
  emit('updateField', { fieldKey: `${componentId.value}:pageSize`, value })
}

function childKey(child: UiContractComponent): string {
  const record = child as unknown as LooseRecord
  return String(record.componentId || record.id || '')
}
</script>

<style scoped>
.a9-ant-node {
  min-width: 0;
  width: 100%;
  border: 1px solid #e6eaf2 !important;
  border-radius: 14px !important;
  background: #ffffff !important;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05) !important;
}

.a9-ant-node + .a9-ant-node,
.a9-ant-toolbar + .a9-ant-node,
.a9-ant-node + .a9-ant-toolbar {
  margin-top: 14px;
}

.a9-ant-node :deep(.ant-card-head) {
  min-height: 54px;
  padding: 0 18px;
  border-bottom: 1px solid #eef1f6;
}

.a9-ant-node :deep(.ant-card-body) {
  padding: 18px;
}

.a9-ant-preset-shadow {
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
}

.a9-ant-preset-bordered {
  border-color: #dbe3ef !important;
}

.a9-ant-preset-card {
  background: #ffffff;
}

.a9-ant-preset-compact :deep(.ant-card-body) {
  padding: 12px 14px;
}

.a9-ant-preset-compact.a9-ant-table-card :deep(.ant-table-tbody > tr > td) {
  height: 38px;
  padding: 7px 10px !important;
}

.a9-ant-preset-striped :deep(.a9-ant-striped-row > td) {
  background: #fbfdff;
}

.a9-ant-preset-action-right {
  justify-content: flex-end;
}

.a9-ant-preset-inline :deep(.a9-ant-form-grid) {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)) !important;
}

.a9-ant-preset-stat-card {
  background: linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.a9-ant-preset-descriptions-bordered :deep(.ant-descriptions-view) {
  border-radius: 12px;
  overflow: hidden;
}

.a9-ant-preset-chart-line .a9-ant-chart-bars {
  align-items: center;
}

.a9-ant-node-heading {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-ant-node-heading strong {
  color: #172033;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
}

.a9-ant-node-heading span {
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.a9-ant-node-children {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.a9-ant-node-children.is-metric-grid {
  gap: 14px;
}

.a9-ant-node-children.is-metric-grid :deep(.a9-ant-stat-card) {
  margin-top: 0;
}

.a9-ant-stat-card {
  min-height: 108px;
}

.a9-ant-stat-card :deep(.ant-statistic-title) {
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.a9-ant-stat-card :deep(.ant-statistic-content) {
  color: #172033;
  font-size: 26px;
  font-weight: 800;
}

.a9-ant-stat-hint {
  display: block;
  margin-top: 8px;
  color: #16a34a;
  font-size: 12px;
  font-weight: 600;
}

.a9-ant-stat-grid {
  display: grid;
  gap: 14px;
}

.a9-ant-stat-item {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  background: #fafbfc;
}

.a9-ant-stat-item .a9-ant-stat-hint {
  margin-top: 4px;
}

.a9-ant-chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 180px;
  padding: 8px 4px;
}

.a9-ant-chart-bar {
  position: relative;
  flex: 1;
  min-width: 32px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(180deg, #60a5fa, #2563eb);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 6px;
}

.a9-ant-chart-bar span {
  position: absolute;
  bottom: -24px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.a9-ant-form-grid {
  display: grid;
  gap: 14px 16px;
  width: 100%;
  min-width: 0;
}

.a9-ant-form-item {
  min-width: 0;
  margin-bottom: 0;
}

.a9-ant-node-form :deep(.ant-form-item-label > label) {
  height: auto;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.a9-ant-control,
.a9-ant-node-form :deep(.ant-picker),
.a9-ant-node-form :deep(.ant-input-number),
.a9-ant-node-form :deep(.ant-select),
.a9-ant-node-form :deep(.ant-input) {
  width: 100%;
  min-width: 0;
}

.a9-ant-node-form :deep(.ant-input),
.a9-ant-node-form :deep(.ant-picker),
.a9-ant-node-form :deep(.ant-select-selector),
.a9-ant-node-form :deep(.ant-input-number) {
  min-height: 34px;
  border-radius: 8px !important;
}

.a9-ant-table-card :deep(.ant-card-body) {
  padding: 14px 18px 18px;
}

.a9-ant-table {
  border-radius: 12px;
}

.a9-ant-table :deep(.ant-table) {
  overflow: hidden;
  border-radius: 12px !important;
  color: #344054;
  font-size: 12px;
}

.a9-ant-table :deep(.ant-table-thead > tr > th) {
  height: 42px;
  background: #f8fafc !important;
  color: #475467 !important;
  font-size: 12px;
  font-weight: 700 !important;
}

.a9-ant-table :deep(.ant-table-tbody > tr > td) {
  height: 46px;
  padding: 10px 12px !important;
  color: #344054;
  font-size: 12px;
}

.a9-ant-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #f7fbff !important;
}

.a9-ant-table :deep(.ant-btn-link) {
  height: 24px;
  padding: 0 4px;
  font-size: 12px;
  font-weight: 600;
}

.a9-ant-cell-content {
  display: inline-block;
  max-width: 100%;
}

.a9-ant-cell-mock-value {
  user-select: text;
}

.a9-ant-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  width: 100%;
  padding: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.a9-ant-descriptions-card :deep(.ant-descriptions-item-label) {
  color: #667085;
  font-weight: 600;
}

.a9-ant-steps-card :deep(.ant-steps-item-title) {
  font-size: 13px;
  font-weight: 600;
}

.a9-ant-timeline-card :deep(.ant-timeline-item-content) p {
  margin: 4px 0;
  color: #475467;
}

.a9-ant-timeline-card :deep(.ant-timeline-item-content) small {
  color: #98a2b3;
}

.a9-ant-tab-panel {
  padding: 8px 0;
  color: #475467;
}

.a9-ant-alert {
  border-radius: 12px;
}

.a9-ant-empty-card :deep(.ant-card-body) {
  display: grid;
  place-items: center;
  min-height: 160px;
}

.a9-ant-result-card :deep(.ant-card-body) {
  display: grid;
  place-items: center;
}

.a9-ant-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

.is-disabled {
  opacity: 0.72;
}
</style>