// A9.10.14.4.3.15.7.24L4_ANTD_COMPONENT_CATALOG
// Ant Design Vue 组件目录 + 样式模板注册表。
//
// 职责：
// 1. 把 UI_CONTRACT 的 componentType 归类到一个有限的组件 kind 上（多对一）。
// 2. 为每个 kind 注册一套样式模板（preset），每个 preset 映射到真实 ant-design 组件的
//    props + 一组渲染器 class。渲染器与后续真实代码生成器读同一张表，保证原型 = 代码。
//
// 注意：本文件不 import 任何 ant-design-vue 组件，只做「元数据 + 样式契约」，
// 因此可以被 UI 渲染层、PRD 同步层、以及将来的代码生成器共同引用而不引入重依赖。

export type AntDesignPresetKey = string

export type AntDesignPreset = {
  key: AntDesignPresetKey
  label: string
  classNames?: string[]
  props?: Record<string, unknown>
}

export type AntDesignComponentKind =
  | 'CONTAINER'
  | 'FORM'
  | 'TABLE'
  | 'ACTION_BAR'
  | 'STAT'
  | 'CHART'
  | 'TIMELINE'
  | 'STEPS'
  | 'TABS'
  | 'DESCRIPTION_LIST'
  | 'ALERT'
  | 'PROGRESS'
  | 'MODAL'
  | 'DRAWER'
  | 'EMPTY_STATE'
  | 'DIVIDER'
  | 'TAG'
  | 'BADGE'
  | 'RESULT'
  | 'COLLAPSE'
  | 'LIST'
  | 'TREE'
  | 'TEXT'
  | 'AVATAR'
  | 'SEGMENTED'
  | 'BREADCRUMB'
  | 'SPIN'
  | 'PAGINATION'

export type AntDesignComponentSpec = {
  kind: AntDesignComponentKind
  componentName: string
  defaultPresetKey: AntDesignPresetKey
  presets: AntDesignPreset[]
}

const CONTAINER_SPEC: AntDesignComponentSpec = {
  kind: 'CONTAINER',
  componentName: 'Card',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '默认容器', classNames: ['a9-ant-preset-container'] },
    { key: 'bordered', label: '带边框', classNames: ['a9-ant-preset-bordered'], props: { bordered: true } },
    { key: 'shadow', label: '阴影卡片', classNames: ['a9-ant-preset-shadow'] },
  ],
}

const FORM_SPEC: AntDesignComponentSpec = {
  kind: 'FORM',
  componentName: 'Form',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '默认表单', classNames: ['a9-ant-preset-form'] },
    { key: 'inline', label: '行内表单', classNames: ['a9-ant-preset-inline'], props: { layout: 'inline' } },
    { key: 'card', label: '卡片表单', classNames: ['a9-ant-preset-card'] },
  ],
}

const TABLE_SPEC: AntDesignComponentSpec = {
  kind: 'TABLE',
  componentName: 'Table',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '默认表格', classNames: ['a9-ant-preset-table'] },
    { key: 'bordered', label: '带边框', classNames: ['a9-ant-preset-bordered'], props: { bordered: true, size: 'middle' } },
    // ant-design-vue Table 没有 stripe prop，斑马纹通过 rowClassName + CSS 自研实现。
    { key: 'striped', label: '斑马纹', classNames: ['a9-ant-preset-striped'], props: { rowClassName: 'a9-ant-striped-row', size: 'middle' } },
    { key: 'compact', label: '紧凑', classNames: ['a9-ant-preset-compact'], props: { size: 'small' } },
  ],
}

const ACTION_BAR_SPEC: AntDesignComponentSpec = {
  kind: 'ACTION_BAR',
  componentName: 'Space',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '默认操作栏', classNames: ['a9-ant-preset-action-bar'] },
    { key: 'right', label: '右对齐', classNames: ['a9-ant-preset-action-right'] },
  ],
}

const STAT_SPEC: AntDesignComponentSpec = {
  kind: 'STAT',
  componentName: 'Statistic',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '指标卡', classNames: ['a9-ant-preset-stat'] },
    { key: 'card', label: '卡片指标', classNames: ['a9-ant-preset-stat-card'], props: { bordered: false } },
  ],
}

const CHART_SPEC: AntDesignComponentSpec = {
  kind: 'CHART',
  componentName: 'ChartCard',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '趋势图表', classNames: ['a9-ant-preset-chart'] },
    { key: 'bar', label: '柱状趋势', classNames: ['a9-ant-preset-chart-bar'], props: { chartType: 'bar' } },
    { key: 'line', label: '折线趋势', classNames: ['a9-ant-preset-chart-line'], props: { chartType: 'line' } },
  ],
}

const TIMELINE_SPEC: AntDesignComponentSpec = {
  kind: 'TIMELINE',
  componentName: 'Timeline',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '时间线', classNames: ['a9-ant-preset-timeline'] },
    { key: 'pending', label: '进行中', classNames: ['a9-ant-preset-timeline-pending'], props: { pending: true } },
  ],
}

const STEPS_SPEC: AntDesignComponentSpec = {
  kind: 'STEPS',
  componentName: 'Steps',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '流程步骤', classNames: ['a9-ant-preset-steps'] },
    { key: 'horizontal', label: '横向步骤', classNames: ['a9-ant-preset-steps-h'], props: { direction: 'horizontal', labelPlacement: 'horizontal' } },
    { key: 'vertical', label: '纵向步骤', classNames: ['a9-ant-preset-steps-v'], props: { direction: 'vertical' } },
  ],
}

const TABS_SPEC: AntDesignComponentSpec = {
  kind: 'TABS',
  componentName: 'Tabs',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '页签', classNames: ['a9-ant-preset-tabs'] },
    { key: 'card', label: '卡片页签', classNames: ['a9-ant-preset-tabs-card'], props: { type: 'card' } },
  ],
}

const DESCRIPTION_LIST_SPEC: AntDesignComponentSpec = {
  kind: 'DESCRIPTION_LIST',
  componentName: 'Descriptions',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '描述列表', classNames: ['a9-ant-preset-descriptions'], props: { column: 2 } },
    { key: 'bordered', label: '带边框描述', classNames: ['a9-ant-preset-descriptions-bordered'], props: { bordered: true, column: 2 } },
  ],
}

const ALERT_SPEC: AntDesignComponentSpec = {
  kind: 'ALERT',
  componentName: 'Alert',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '提示', classNames: ['a9-ant-preset-alert'] },
  ],
}

const PROGRESS_SPEC: AntDesignComponentSpec = {
  kind: 'PROGRESS',
  componentName: 'Progress',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '进度条', classNames: ['a9-ant-preset-progress'] },
    { key: 'circle', label: '环形进度', classNames: ['a9-ant-preset-progress-circle'], props: { type: 'circle' } },
  ],
}

const MODAL_SPEC: AntDesignComponentSpec = {
  kind: 'MODAL',
  componentName: 'Modal',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '弹窗', classNames: ['a9-ant-preset-modal'] },
  ],
}

const DRAWER_SPEC: AntDesignComponentSpec = {
  kind: 'DRAWER',
  componentName: 'Drawer',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '抽屉', classNames: ['a9-ant-preset-drawer'] },
  ],
}

const EMPTY_STATE_SPEC: AntDesignComponentSpec = {
  kind: 'EMPTY_STATE',
  componentName: 'Empty',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '空状态', classNames: ['a9-ant-preset-empty'] },
  ],
}

const DIVIDER_SPEC: AntDesignComponentSpec = {
  kind: 'DIVIDER',
  componentName: 'Divider',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '分割线', classNames: ['a9-ant-preset-divider'] },
  ],
}

const TAG_SPEC: AntDesignComponentSpec = {
  kind: 'TAG',
  componentName: 'Tag',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '标签', classNames: ['a9-ant-preset-tag'] },
  ],
}

const BADGE_SPEC: AntDesignComponentSpec = {
  kind: 'BADGE',
  componentName: 'Badge',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '徽标', classNames: ['a9-ant-preset-badge'] },
  ],
}

const RESULT_SPEC: AntDesignComponentSpec = {
  kind: 'RESULT',
  componentName: 'Result',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '结果页', classNames: ['a9-ant-preset-result'] },
  ],
}

const COLLAPSE_SPEC: AntDesignComponentSpec = {
  kind: 'COLLAPSE',
  componentName: 'Collapse',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '折叠面板', classNames: ['a9-ant-preset-collapse'] },
  ],
}

const LIST_SPEC: AntDesignComponentSpec = {
  kind: 'LIST',
  componentName: 'List',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '列表', classNames: ['a9-ant-preset-list'] },
  ],
}

const TREE_SPEC: AntDesignComponentSpec = {
  kind: 'TREE',
  componentName: 'Tree',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '树形结构', classNames: ['a9-ant-preset-tree'] },
  ],
}

const TEXT_SPEC: AntDesignComponentSpec = {
  kind: 'TEXT',
  componentName: 'TypographyText',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '文本', classNames: ['a9-ant-preset-text'] },
  ],
}

const AVATAR_SPEC: AntDesignComponentSpec = {
  kind: 'AVATAR',
  componentName: 'Avatar',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '头像', classNames: ['a9-ant-preset-avatar'] },
  ],
}

const SEGMENTED_SPEC: AntDesignComponentSpec = {
  kind: 'SEGMENTED',
  componentName: 'Segmented',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '分段控制器', classNames: ['a9-ant-preset-segmented'] },
  ],
}

const BREADCRUMB_SPEC: AntDesignComponentSpec = {
  kind: 'BREADCRUMB',
  componentName: 'Breadcrumb',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '面包屑', classNames: ['a9-ant-preset-breadcrumb'] },
  ],
}

const SPIN_SPEC: AntDesignComponentSpec = {
  kind: 'SPIN',
  componentName: 'Spin',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '加载中', classNames: ['a9-ant-preset-spin'] },
  ],
}

const PAGINATION_SPEC: AntDesignComponentSpec = {
  kind: 'PAGINATION',
  componentName: 'Pagination',
  defaultPresetKey: 'default',
  presets: [
    { key: 'default', label: '分页', classNames: ['a9-ant-preset-pagination'] },
  ],
}

export const ANT_DESIGN_COMPONENT_CATALOG: Record<AntDesignComponentKind, AntDesignComponentSpec> = {
  CONTAINER: CONTAINER_SPEC,
  FORM: FORM_SPEC,
  TABLE: TABLE_SPEC,
  ACTION_BAR: ACTION_BAR_SPEC,
  STAT: STAT_SPEC,
  CHART: CHART_SPEC,
  TIMELINE: TIMELINE_SPEC,
  STEPS: STEPS_SPEC,
  TABS: TABS_SPEC,
  DESCRIPTION_LIST: DESCRIPTION_LIST_SPEC,
  ALERT: ALERT_SPEC,
  PROGRESS: PROGRESS_SPEC,
  MODAL: MODAL_SPEC,
  DRAWER: DRAWER_SPEC,
  EMPTY_STATE: EMPTY_STATE_SPEC,
  DIVIDER: DIVIDER_SPEC,
  TAG: TAG_SPEC,
  BADGE: BADGE_SPEC,
  RESULT: RESULT_SPEC,
  COLLAPSE: COLLAPSE_SPEC,
  LIST: LIST_SPEC,
  TREE: TREE_SPEC,
  TEXT: TEXT_SPEC,
  AVATAR: AVATAR_SPEC,
  SEGMENTED: SEGMENTED_SPEC,
  BREADCRUMB: BREADCRUMB_SPEC,
  SPIN: SPIN_SPEC,
  PAGINATION: PAGINATION_SPEC,
}

// 契约 componentType → kind 的多对一映射。这里用大写字符串匹配，容忍 PRD/契约里
// 出现的各种同义类型写法。
const CONTRACT_TYPE_TO_KIND: Record<string, AntDesignComponentKind> = {
  // 布局/容器
  SECTION: 'CONTAINER',
  CARD: 'CONTAINER',
  CONTAINER: 'CONTAINER',
  GROUP: 'CONTAINER',
  PANEL: 'CONTAINER',
  PAGE_SECTION: 'CONTAINER',
  CONTENT_BLOCK: 'CONTAINER',
  LAYOUT: 'CONTAINER',
  // 表单
  FORM: 'FORM',
  FIELD_GROUP: 'FORM',
  FILTER_FORM: 'FORM',
  SEARCH_FORM: 'FORM',
  QUERY_FORM: 'FORM',
  ADVANCED_FILTER: 'FORM',
  CREATE_FORM: 'FORM',
  EDIT_FORM: 'FORM',
  DETAIL_FORM: 'FORM',
  // 表格
  TABLE: 'TABLE',
  DATA_TABLE: 'TABLE',
  LIST_TABLE: 'TABLE',
  RESULT_TABLE: 'TABLE',
  GRID_TABLE: 'TABLE',
  ANT_TABLE: 'TABLE',
  // 操作栏
  ACTION_BAR: 'ACTION_BAR',
  TOOLBAR: 'ACTION_BAR',
  BUTTON_GROUP: 'ACTION_BAR',
  ACTIONS: 'ACTION_BAR',
  BUTTON: 'ACTION_BAR',
  PRIMARY_ACTION: 'ACTION_BAR',
  LINK: 'ACTION_BAR',
  // 指标
  STATS: 'STAT',
  STAT_GRID: 'STAT',
  STATISTIC: 'STAT',
  KPI: 'STAT',
  KPI_CARD: 'STAT',
  METRIC_GRID: 'STAT',
  METRICS: 'STAT',
  // 图表
  CHART: 'CHART',
  TREND_CHART: 'CHART',
  LINE_CHART: 'CHART',
  BAR_CHART: 'CHART',
  PIE_CHART: 'CHART',
  ANALYTICS_CHART: 'CHART',
  // 其余
  TIMELINE: 'TIMELINE',
  STEPS: 'STEPS',
  WIZARD: 'STEPS',
  TABS: 'TABS',
  DETAIL_TABS: 'TABS',
  DESCRIPTION_LIST: 'DESCRIPTION_LIST',
  DESCRIPTIONS: 'DESCRIPTION_LIST',
  ALERT: 'ALERT',
  NOTICE: 'ALERT',
  PROGRESS: 'PROGRESS',
  MODAL: 'MODAL',
  DIALOG: 'MODAL',
  CONFIRM_DIALOG: 'MODAL',
  CONFIRM_MODAL: 'MODAL',
  DRAWER: 'DRAWER',
  SIDE_PANEL: 'DRAWER',
  DETAIL_DRAWER: 'DRAWER',
  EMPTY_STATE: 'EMPTY_STATE',
  DIVIDER: 'DIVIDER',
  TAG: 'TAG',
  BADGE: 'BADGE',
  RESULT: 'RESULT',
  COLLAPSE: 'COLLAPSE',
  COLLAPSIBLE_PANEL: 'COLLAPSE',
  LIST: 'LIST',
  CARD_LIST: 'LIST',
  TREE: 'TREE',
  TEXT: 'TEXT',
  AVATAR: 'AVATAR',
  SEGMENTED: 'SEGMENTED',
  BREADCRUMB: 'BREADCRUMB',
  SPIN: 'SPIN',
  PAGINATION: 'PAGINATION',
}

export function normalizeComponentType(value: unknown): string {
  return String(value ?? '').trim().toUpperCase()
}

export function classifyComponentKind(type: unknown): AntDesignComponentKind | null {
  const normalized = normalizeComponentType(type)
  if (!normalized) return null
  return CONTRACT_TYPE_TO_KIND[normalized] || null
}

export function resolveComponentSpec(type: unknown): AntDesignComponentSpec | null {
  const kind = classifyComponentKind(type)
  return kind ? ANT_DESIGN_COMPONENT_CATALOG[kind] : null
}

// 从组件 props 里解析当前样式模板 key。
// 优先级：props.variant > props.preset > props.styleTemplate > 默认 preset。
export function resolvePresetKey(
  type: unknown,
  componentProps?: Record<string, unknown> | null,
): AntDesignPresetKey {
  const spec = resolveComponentSpec(type)
  if (!spec) return 'default'

  const source = componentProps || {}
  const explicit = String(
    source.variant ||
      source.preset ||
      source.styleTemplate ||
      source.stylePreset ||
      '',
  ).trim()

  if (explicit && spec.presets.some((preset) => preset.key === explicit)) {
    return explicit
  }

  return spec.defaultPresetKey
}

export function resolvePreset(
  type: unknown,
  componentProps?: Record<string, unknown> | null,
): AntDesignPreset {
  const spec = resolveComponentSpec(type)
  if (!spec) return { key: 'default', label: '默认' }

  const key = resolvePresetKey(type, componentProps)
  return spec.presets.find((preset) => preset.key === key) || spec.presets[0]
}

export function presetClassNames(type: unknown, componentProps?: Record<string, unknown> | null): string[] {
  const preset = resolvePreset(type, componentProps)
  return preset.classNames || []
}

export function presetProps(type: unknown, componentProps?: Record<string, unknown> | null): Record<string, unknown> {
  const preset = resolvePreset(type, componentProps)
  return preset.props || {}
}