
import type { UiContractContent } from '../types/uiContract'
import type { PrototypeElementSelection } from './uiContractPrototypeLocalEdit'
import { applyPrototypeLocalEdit } from './uiContractPrototypeLocalEdit'

type LooseRecord = Record<string, unknown>

export interface PrototypeEditPlan {
  summary?: string
  source?: string
  requirementImpact?: string
  operations?: Array<Record<string, unknown>>
  warnings?: string[]
  [key: string]: unknown
}

export function buildPrototypeEditPlannerPayload(args: {
  instruction: string
  contract: UiContractContent
  selection?: PrototypeElementSelection | null
  focusedComponentId?: string | null
  page?: Record<string, unknown> | null
}): Record<string, unknown> {
  return {
    schemaVersion: 'A9.10.14.4.3.15.6.24J2',
    instruction: args.instruction,
    selection: args.selection || null,
    focusedComponentId: args.focusedComponentId || args.selection?.componentId || null,
    page: args.page || null,
    contractSummary: summarizeUiContract(args.contract),
  }
}

export function buildPrototypeEditPlannerFallbackPlan(instruction: string): PrototypeEditPlan {
  const text = String(instruction || '')
  const operations: Array<Record<string, unknown>> = []
  const color = resolveColor(text)
  const hasText = /文案|文字|标题|按钮文案|字段名|列名|标签名|显示文字|placeholder|占位|提示语|输入提示|命名为|显示为|叫做|[“"'][^”"']+[”"']/.test(text)
  const explicit = extractText(text)
  if (color && !(hasText && explicit)) operations.push({ type: 'SET_COLOR', colorPreset: color })
  if (hasText && explicit) operations.push({ type: 'SET_TEXT', text: explicit })
  if (/调高|行高|高一点|变高|加高|更高/.test(text)) operations.push({ type: 'SET_SIZE', size: 'taller', axis: 'rowHeight' })
  if (/调矮|矮一点|变矮|降低高度|降低行高/.test(text)) operations.push({ type: 'SET_SIZE', size: 'shorter', axis: 'rowHeight' })
  if (/放大|大一点|更大|加大/.test(text)) operations.push({ type: 'SET_SIZE', size: 'large' })
  if (/缩小|小一点|更小/.test(text)) operations.push({ type: 'SET_SIZE', size: 'small' })
  if (/列宽|表格列.*宽|加宽|宽一点|更宽/.test(text)) operations.push({ type: /列|表格/.test(text) ? 'SET_COLUMN_WIDTH' : 'SET_SIZE', delta: 48 })
  if (/收窄|窄一点|变窄/.test(text)) operations.push({ type: /列|表格/.test(text) ? 'SET_COLUMN_WIDTH' : 'SET_SIZE', delta: -36 })
  const position = resolvePosition(text)
  if (position) operations.push({ type: 'SET_POSITION', position })
  if (/主按钮/.test(text)) operations.push({ type: 'SET_VARIANT', variant: 'primary' })
  if (/次按钮|辅助按钮/.test(text)) operations.push({ type: 'SET_VARIANT', variant: 'secondary' })
  if (/危险按钮|警告按钮/.test(text)) operations.push({ type: 'SET_VARIANT', variant: 'danger' })
  if (/必填/.test(text)) operations.push({ type: 'SET_FIELD_REQUIRED', required: !/非必填|不是必填|可选/.test(text) })
  if (/隐藏|不要显示|先不显示/.test(text)) operations.push({ type: 'SET_VISIBILITY', visible: false })
  else if (/显示出来|恢复显示/.test(text)) operations.push({ type: 'SET_VISIBILITY', visible: true })
  return { schemaVersion: 'A9.10.14.4.3.15.6.24J2', source: 'FRONTEND_FALLBACK', summary: operations.length ? '已生成本地原型编辑计划。' : '没有识别到可执行的原型修改。', requirementImpact: operations.some((op) => ['SET_TEXT', 'SET_FIELD_REQUIRED', 'SET_VISIBILITY'].includes(String(op.type))) ? 'REQUIREMENT_SYNC_REVIEW_REQUIRED' : 'VISUAL_ONLY', operations, warnings: operations.length ? [] : ['NO_SUPPORTED_OPERATION'] }
}

export function applyPrototypeEditPlanBridge(args: {
  contract: UiContractContent
  instruction: string
  plan?: PrototypeEditPlan | null
  focusedComponentId?: string | null
  selection?: PrototypeElementSelection | null
}) {
  const operations = Array.isArray(args.plan?.operations) ? args.plan?.operations || [] : []
  if (!operations.length) {
    return applyPrototypeLocalEdit({ contract: args.contract, instruction: args.instruction, focusedComponentId: args.focusedComponentId, prototypeElementSelection: args.selection || null, focusedPrototypeElementSelection: args.selection || null, selectionRef: args.selection || null })
  }
  let current = args.contract
  const summaries: string[] = []
  let changed = false
  for (const op of operations) {
    const instruction = operationToInstruction(op, args.instruction)
    const result = applyPrototypeLocalEdit({ contract: current, instruction, focusedComponentId: args.focusedComponentId, prototypeElementSelection: args.selection || null, focusedPrototypeElementSelection: args.selection || null, selectionRef: args.selection || null })
    if (result.changed) {
      current = result.contract
      changed = true
      summaries.push(result.summary)
    }
  }
  if (!changed) return { changed: false, contract: current, summary: args.plan?.summary || '已生成编辑计划，但没有产生可见修改。', targetComponentId: args.focusedComponentId || null, targetSelection: args.selection || null, warnings: args.plan?.warnings || ['NO_CHANGES'] }
  return { changed: true, contract: current, summary: summaries[summaries.length - 1] || args.plan?.summary || '已完成原型编辑。', targetComponentId: args.focusedComponentId || null, targetSelection: args.selection || null, warnings: [] }
}

function operationToInstruction(op: Record<string, unknown>, fallback: string): string {
  const type = String(op.type || op.op || '').toUpperCase()
  if (type === 'SET_TEXT') return `把当前选中元素文案改成“${String(op.text || op.value || '')}”`
  if (type === 'SET_COLOR') return `把当前选中元素改成${colorLabel(String(op.colorPreset || ''))}`
  if (type === 'SET_SIZE') {
    // A9.10.14.4.3.15.6.24J7E_TABLE_ROW_HEIGHT_EXECUTOR
    // For TABLE_ROW, “调高/行高” must remain height semantics.
    // Otherwise the local executor receives “放大一点” and writes width/padding,
    // which is invisible for table rows.
    const fallbackText = String(fallback || '')
    const axis = String(op.axis || '').toLowerCase()
    const size = String(op.size || '').toLowerCase()

    if (
      axis.includes('rowheight') ||
      (/行|row/i.test(fallbackText) && /调高|行高|高一点|变高|加高|更高/.test(fallbackText))
    ) {
      return size.includes('short') || /调矮|矮一点|变矮|降低/.test(fallbackText)
        ? '把这一行变矮一点'
        : '把这一行调高一点'
    }

    return size.includes('small') || size.includes('short')
      ? '把当前选中元素缩小一点'
      : '把当前选中元素放大一点'
  }
  if (type === 'SET_POSITION') {
    // A9.10.14.4.3.15.6.24J4_COLUMN_SELECT_AND_ACTION_ORDER
    // Preserve order intent. “最左边” is not the same as CSS left-align.
    if (/最左边|最前面|第一个|排第一个|开头/.test(fallback)) return '把当前选中元素放到最左边'
    if (/最右边|最后面|最后一个|排最后|末尾/.test(fallback)) return '把当前选中元素放到最右边'
    return `把当前选中元素${positionLabel(String(op.position || op.align || ''))}`
  }
  if (type === 'SET_COLUMN_WIDTH') return Number(op.delta || 0) < 0 ? '把这个表格列收窄一点' : '把这个表格列加宽一点'
  if (type === 'SET_FIELD_REQUIRED') return op.required === false ? '把这个字段设为非必填' : '把这个字段设为必填'
  if (type === 'SET_VISIBILITY') return op.visible === false ? '隐藏当前选中元素' : '显示当前选中元素'
  if (type === 'SET_VARIANT') return variantInstruction(String(op.variant || ''))
  return fallback
}

function summarizeUiContract(contract: UiContractContent): Record<string, unknown> {
  const record = contract as unknown as LooseRecord
  const page = ((record.page || record) || {}) as LooseRecord
  const roots = Array.isArray(page.componentTree) ? page.componentTree as unknown[] : []
  const components: LooseRecord[] = []
  const visit = (item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return
    const component = item as LooseRecord
    if (component.componentId) components.push(component)
    for (const key of ['children', 'components', 'items']) {
      const value = component[key]
      if (Array.isArray(value)) value.forEach(visit)
    }
  }
  roots.forEach(visit)
  return { pageKey: page.pageKey || page.key || '', pageName: page.pageName || page.title || page.name || '', pageKind: page.pageKind || '', componentCount: components.length, components: components.slice(0, 80).map((component) => ({ componentId: component.componentId || '', type: component.type || component.componentType || '', title: component.title || '', fields: Array.isArray(component.fields) ? component.fields : [], columns: Array.isArray(component.columns) ? component.columns : [], actionIds: Array.isArray(component.actionIds) ? component.actionIds : [] })) }
}

function resolveColor(text: string): string {
  if (/蓝色|蓝|主按钮/.test(text)) return 'primary'
  if (/绿色|绿|成功/.test(text)) return 'success'
  if (/红色|红|危险/.test(text)) return 'danger'
  if (/橙色|橙|警告/.test(text)) return 'warning'
  if (/紫色|紫/.test(text)) return 'purple'
  if (/灰色|灰/.test(text)) return 'gray'
  if (/白色|白/.test(text)) return 'white'
  if (/黑色|黑/.test(text)) return 'black'
  return ''
}

function extractText(text: string): string {
  const quoted = text.match(/[“"']([^”"']{1,80})[”"']/)
  if (quoted?.[1]) return quoted[1].trim()
  const match = text.match(/(?:文案|文字|标题|按钮文案|字段名|列名|标签名|显示文字|placeholder|占位|提示语|输入提示|命名为|显示为|叫做)\s*(?:改成|改为|换成|为|:|：)?\s*([^，。；;\n]{1,50})/)
  return String(match?.[1] || '').replace(/^(把|将|这个|该|当前|的|改成|改为|换成|为)/, '').trim()
}

function resolvePosition(text: string): string {
  if (/靠左|左对齐/.test(text)) return 'left'
  if (/靠右|右对齐/.test(text)) return 'right'
  if (/居中/.test(text)) return 'center'
  if (/右上角/.test(text)) return 'top-right'
  if (/上方|顶部/.test(text)) return 'top'
  if (/下方|底部/.test(text)) return 'bottom'
  return ''
}

function colorLabel(value: string): string {
  const map: Record<string, string> = { primary: '蓝色', success: '绿色', green: '绿色', danger: '红色', warning: '橙色', purple: '紫色', gray: '灰色', white: '白色', black: '黑色' }
  return map[value] || '蓝色'
}

function positionLabel(value: string): string {
  const map: Record<string, string> = { first: '放到最左边', last: '放到最右边', left: '靠左', right: '靠右', center: '居中', 'top-right': '放到右上角', top: '放到上方', bottom: '放到下方' }
  return map[value] || '居中'
}

function variantInstruction(value: string): string {
  if (value.includes('primary')) return '把当前选中元素改成蓝色主按钮'
  if (value.includes('danger') || value.includes('warning')) return '把当前选中元素改成危险按钮'
  if (value.includes('success')) return '把当前选中元素改成绿色'
  return '把当前选中元素改成次按钮'
}
