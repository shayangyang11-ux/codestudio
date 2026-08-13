import type {
  UiContractComponent,
  UiContractContent,
  UiContractInteraction,
} from '../types/uiContract'

type LooseRecord = Record<string, unknown>

export interface PrototypeElementSelection {
  componentId?: string | null
  componentType?: string | null
  componentTitle?: string | null
  elementKind?: string | null
  elementLabel?: string | null
  label?: string | null
  fieldKey?: string | null
  actionId?: string | null
  columnKey?: string | null
  tabKey?: string | null
  textKey?: string | null
  textRole?: string | null
  elementPath?: string[] | null
}

export type PrototypeElementSelectionRef = PrototypeElementSelection

export interface PrototypeLocalEditRequest {
  contract: UiContractContent
  instruction: string
  focusedComponentId?: string | null
  prototypeElementSelection?: PrototypeElementSelection | null
  selectionRef?: PrototypeElementSelection | null
  focusedPrototypeElementSelection?: PrototypeElementSelection | null
}

export interface PrototypeLocalEditResult {
  changed: boolean
  contract: UiContractContent
  summary: string
  targetComponentId?: string | null
  targetSelection?: PrototypeElementSelection | null
  warnings: string[]
}

const COLOR_MAP: Record<string, { label: string; backgroundColor: string; color: string; borderColor: string }> = {
  蓝: { label: '蓝色', backgroundColor: '#2563eb', color: '#ffffff', borderColor: '#1d4ed8' },
  蓝色: { label: '蓝色', backgroundColor: '#2563eb', color: '#ffffff', borderColor: '#1d4ed8' },
  绿色: { label: '绿色', backgroundColor: '#16a34a', color: '#ffffff', borderColor: '#15803d' },
  绿: { label: '绿色', backgroundColor: '#16a34a', color: '#ffffff', borderColor: '#15803d' },
  红色: { label: '红色', backgroundColor: '#dc2626', color: '#ffffff', borderColor: '#b91c1c' },
  红: { label: '红色', backgroundColor: '#dc2626', color: '#ffffff', borderColor: '#b91c1c' },
  橙色: { label: '橙色', backgroundColor: '#f97316', color: '#ffffff', borderColor: '#ea580c' },
  橙: { label: '橙色', backgroundColor: '#f97316', color: '#ffffff', borderColor: '#ea580c' },
  紫色: { label: '紫色', backgroundColor: '#7c3aed', color: '#ffffff', borderColor: '#6d28d9' },
  紫: { label: '紫色', backgroundColor: '#7c3aed', color: '#ffffff', borderColor: '#6d28d9' },
  灰色: { label: '灰色', backgroundColor: '#64748b', color: '#ffffff', borderColor: '#475569' },
  灰: { label: '灰色', backgroundColor: '#64748b', color: '#ffffff', borderColor: '#475569' },
  白色: { label: '白色', backgroundColor: '#ffffff', color: '#0f172a', borderColor: '#cbd5e1' },
  白: { label: '白色', backgroundColor: '#ffffff', color: '#0f172a', borderColor: '#cbd5e1' },
  黑色: { label: '黑色', backgroundColor: '#111827', color: '#ffffff', borderColor: '#020617' },
  黑: { label: '黑色', backgroundColor: '#111827', color: '#ffffff', borderColor: '#020617' },
}

const EDIT_KEYWORDS = [
  '改', '改成', '改为', '换成', '调整', '优化', '修改', '移动', '挪',
  '往左', '往右', '往上', '往下', '居中', '靠左', '靠右', '颜色', '蓝色',
  '绿色', '红色', '橙色', '紫色', '灰色', '白色', '黑色', '文案', '文字',
  '标题', '按钮', '输入框', '表格', '卡片', '字段', '列', '标签页',
  'placeholder', '占位', '提示', '必填', '非必填', '禁用', '启用',
  '大一点', '小一点', '宽一点', '窄一点', '隐藏', '显示', '原型', '页面', '布局',
  '删除', '删掉', '删去', '删了', '去掉', '移除', '去除',
  '最后', '末尾', '结尾', '尾部', '开头', '前面', '第一个', '第几个',
  '加上', '追加', '补上', '前面加', '后面加', '末尾加',
  '替换', '替换成', '替换为',
  '长一点', '变长', '加长', '更长', '短一点', '变短', '全宽', '铺满', '拉满', '占满',
  '整行', '独占一行', '两列', '三列', '四列', '右上角', '左上角', '表格右上角',
  '放到上方', '移到上方', '放到下方', '移到下方', '顶部', '底部', '置顶', '置底',
  '右对齐', '左对齐', '主按钮', '次按钮', '危险按钮', '警告按钮', '链接按钮',
]

export function isPrototypeLocalEditInstruction(text: string): boolean {
  const normalized = String(text || '').trim().toLowerCase()
  if (!normalized) return false

  if (
    normalized.includes('生成') ||
    normalized.includes('准备') ||
    normalized.includes('创建模块') ||
    normalized.includes('模块树')
  ) {
    return false
  }

  return EDIT_KEYWORDS.some((keyword) => normalized.includes(keyword.toLowerCase()))
}

export function applyPrototypeLocalEdit(
  request: PrototypeLocalEditRequest,
): PrototypeLocalEditResult {
  const instruction = String(request.instruction || '').trim()
  const cloned = deepClone(request.contract) as unknown as UiContractContent
  const components = collectComponents(cloned)
  const interactions = Array.isArray((cloned as unknown as LooseRecord).interactions)
    ? ((cloned as unknown as LooseRecord).interactions as unknown as UiContractInteraction[])
    : []

  if (!instruction || components.length === 0) {
    return {
      changed: false,
      contract: cloned,
      summary: '当前原型没有可调整的元素。',
      warnings: ['NO_COMPONENTS'],
    }
  }

  const selection = normalizeSelection(request)
  const target = resolveTargetComponent(
    components,
    instruction,
    selection?.componentId || request.focusedComponentId || null,
  )

  if (!target) {
    return {
      changed: false,
      contract: cloned,
      summary: '没有定位到需要修改的原型元素。',
      warnings: ['TARGET_NOT_FOUND'],
    }
  }

  const changes: string[] = []
  const targetId = String((target as unknown as LooseRecord).componentId || '')
  const targetLabel = readableTargetLabel(target, selection)

  const requiredChange = applyRequiredChange(target, selection, instruction)
  if (requiredChange) changes.push(requiredChange)

  const disabledChange = applyDisabledChange(target, selection, instruction)
  if (disabledChange) changes.push(disabledChange)

  const textTransformChange = applyTextTransformChange(target, interactions, instruction, selection)
  if (textTransformChange) {
    changes.push(textTransformChange)
  } else {
    const nextText = extractTargetText(instruction)
    if (nextText) {
      const textChange = applyTextChange(target, interactions, nextText, selection)
      if (textChange) changes.push(textChange)
    }
  }

  const color = resolveColor(instruction)
  if (color) {
    applyColorChange(target, selection, color)
    changes.push(`颜色改为${color.label}`)
  }

  const variantChange = applyVariantChange(target, interactions, selection, instruction)
  if (variantChange) changes.push(variantChange)

  const moveChange = applyMoveChange(target, selection, instruction)
  if (moveChange) changes.push(moveChange)

  const sizeChange = applySizeChange(target, selection, instruction)
  if (sizeChange) changes.push(sizeChange)

  const visibilityChange = applyVisibilityChange(target, selection, instruction)
  if (visibilityChange) changes.push(visibilityChange)

  if (changes.length === 0) {
    return {
      changed: false,
      contract: cloned,
      summary: `已定位到「${targetLabel}」，但没有识别到可执行的原型修改。`,
      targetComponentId: targetId || null,
      targetSelection: selection,
      warnings: ['NO_SUPPORTED_EDIT'],
    }
  }

  syncVisiblePageTextAfterLocalEdit(cloned, target, selection)
  bumpPrototypeLocalEditRevision(cloned)

  appendLocalEditMetadata(cloned, instruction, targetId, selection, changes)

  return {
    changed: true,
    contract: cloned,
    summary: `已在原型预览中调整「${targetLabel}」：${changes.join('、')}。`,
    targetComponentId: targetId || null,
    targetSelection: selection,
    warnings: [],
  }
}

function normalizeSelection(
  request: PrototypeLocalEditRequest,
): PrototypeElementSelection | null {
  const raw =
    request.prototypeElementSelection ||
    request.selectionRef ||
    request.focusedPrototypeElementSelection ||
    null

  if (raw && typeof raw === 'object') {
    return raw
  }

  if (request.focusedComponentId) {
    return {
      componentId: request.focusedComponentId,
      elementKind: 'COMPONENT',
    }
  }

  return null
}

function deepClone(value: unknown): unknown {
  return JSON.parse(JSON.stringify(value || {}))
}

function collectComponents(contract: UiContractContent): UiContractComponent[] {
  const page = ((contract as unknown as LooseRecord).page || contract) as unknown as LooseRecord
  const roots = firstArray(
    page.componentTree,
    page.components,
    (contract as unknown as LooseRecord).componentTree,
    (contract as unknown as LooseRecord).components,
  )

  const result: UiContractComponent[] = []

  const visit = (item: unknown) => {
    if (!item || typeof item !== 'object') return

    const component = item as unknown as UiContractComponent & LooseRecord

    if (component.componentId) {
      result.push(component)
    }

    for (const key of ['children', 'components', 'componentTree', 'items', 'body', 'content']) {
      const child = component[key]
      if (Array.isArray(child)) {
        child.forEach(visit)
      }
    }

    if (Array.isArray(component.tabs)) {
      component.tabs.forEach((tab) => {
        const tabRecord = tab as unknown as LooseRecord
        const tabComponents = tabRecord.components || tabRecord.children
        if (Array.isArray(tabComponents)) {
          tabComponents.forEach(visit)
        }
      })
    }
  }

  roots.forEach(visit)
  return result
}

function firstArray(...values: unknown[]): unknown[] {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function resolveTargetComponent(
  components: UiContractComponent[],
  instruction: string,
  focusedComponentId: string | null,
): UiContractComponent | null {
  const focusedId = String(focusedComponentId || '').trim()

  if (focusedId) {
    const focused = components.find(
      (component) => String((component as unknown as LooseRecord).componentId || '') === focusedId,
    )
    if (focused) return focused
  }

  const normalized = instruction.toLowerCase()
  const mentioned = components.find((component) => {
    const record = component as unknown as LooseRecord
    return [record.componentId, record.title, record.description]
      .filter(Boolean)
      .some((value) => normalized.includes(String(value).toLowerCase()))
  })
  if (mentioned) return mentioned

  if (containsAny(instruction, ['按钮', 'button', '提交', '确认', '保存'])) {
    return components.find(isButtonLike) || components[0] || null
  }

  if (containsAny(instruction, ['输入框', '输入', '字段', '表单', 'input', 'form'])) {
    return components.find(isFormLike) || components[0] || null
  }

  if (containsAny(instruction, ['表格', '列表', 'table', 'list', '列'])) {
    return components.find(isTableLike) || components[0] || null
  }

  return components[0] || null
}

function readableTargetLabel(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
): string {
  const record = target as unknown as LooseRecord
  const componentTitle = String(record.title || record.componentId || '当前元素')
  const kind = normalizeKind(selection?.elementKind)

  if (selection?.elementLabel || selection?.label) {
    return `${displayKind(kind)}「${selection.elementLabel || selection.label}」`
  }

  if (selection?.fieldKey) {
    const field = findField(target, selection.fieldKey)
    return `${displayKind(kind || 'FIELD')}「${String(field?.label || selection.fieldKey)}」`
  }

  if (selection?.actionId) {
    return `${displayKind(kind || 'ACTION')}「${selection.actionId}」`
  }

  if (selection?.columnKey) {
    const column = findColumn(target, selection.columnKey)
    return `${displayKind(kind || 'TABLE_COLUMN')}「${String(column?.label || selection.columnKey)}」`
  }

  if (selection?.tabKey) {
    const tab = findTab(target, selection.tabKey)
    return `${displayKind(kind || 'TAB')}「${String(tab?.title || selection.tabKey)}」`
  }

  return componentTitle
}

function displayKind(kind: string): string {
  const normalized = normalizeKind(kind)
  if (normalized.includes('FIELD_INPUT') || normalized.includes('FIELD_CONTROL')) return '输入控件'
  if (normalized.includes('FIELD_LABEL')) return '字段名'
  if (normalized.includes('FIELD')) return '字段'
  if (normalized.includes('ACTION') || normalized.includes('BUTTON')) return '按钮'
  if (normalized.includes('COLUMN') || normalized.includes('CELL')) return '表格列'
  if (normalized.includes('TAB')) return '标签页'
  if (normalized.includes('TEXT') || normalized.includes('TITLE') || normalized.includes('DESCRIPTION')) return '文案'
  return '元素'
}

function isButtonLike(component: UiContractComponent): boolean {
  const record = component as unknown as LooseRecord
  const type = String(record.type || record.componentType || '').toUpperCase()
  return type.includes('BUTTON') || Array.isArray(record.actionIds)
}

function isFormLike(component: UiContractComponent): boolean {
  const record = component as unknown as LooseRecord
  const type = String(record.type || record.componentType || '').toUpperCase()
  return type.includes('FORM') || Array.isArray(record.fields)
}

function isTableLike(component: UiContractComponent): boolean {
  const record = component as unknown as LooseRecord
  const type = String(record.type || record.componentType || '').toUpperCase()
  return type.includes('TABLE') || type.includes('LIST') || Array.isArray(record.columns)
}

type PrototypeTextTransformResult = {
  nextText: string
  summary: string
}

function applyTextTransformChange(
  target: UiContractComponent,
  interactions: UiContractInteraction[],
  instruction: string,
  selection: PrototypeElementSelection | null,
): string {
  const currentText = resolveCurrentSelectedText(target, interactions, selection)

  if (!currentText) {
    return ''
  }

  const transform = resolveSelectedTextTransform(currentText, instruction)

  if (!transform || transform.nextText === currentText) {
    return ''
  }

  const textChange = applyTextChange(target, interactions, transform.nextText, selection)

  if (!textChange) {
    return ''
  }

  return `${textChange}（${transform.summary}）`
}

function resolveCurrentSelectedText(
  target: UiContractComponent,
  interactions: UiContractInteraction[],
  selection: PrototypeElementSelection | null,
): string {
  const record = target as unknown as LooseRecord
  const kind = normalizeKind(selection?.elementKind)
  const textKey = String(selection?.textKey || selection?.textRole || '').trim().toLowerCase()
  const instructionKey = `${kind} ${selection?.textRole || ''} ${selection?.textKey || ''}`.toUpperCase()

  if (selection?.fieldKey) {
    const field = findField(target, selection.fieldKey)

    if (field) {
      if (
        kind.includes('INPUT') ||
        kind.includes('CONTROL') ||
        containsAny(instructionKey, ['PLACEHOLDER', 'CONTROL'])
      ) {
        return String(field.placeholder || '')
      }

      if (containsAny(instructionKey, ['HELP', 'HINT'])) {
        return String(field.helpText || '')
      }

      return String(field.label || '')
    }

    const item = findComponentItem(target, selection.fieldKey)
    if (item) {
      return resolveComponentItemText(item, selection)
    }
  }

  if (selection?.columnKey) {
    const column = findColumn(target, selection.columnKey)
    return String(column?.label || '')
  }

  if (selection?.tabKey) {
    const tab = findTab(target, selection.tabKey)
    return String(tab?.title || '')
  }

  if (selection?.actionId) {
    const action = interactions.find(
      (item) => String((item as unknown as LooseRecord).actionId || '') === selection.actionId,
    )

    if (action) {
      return String((action as unknown as LooseRecord).label || '')
    }

    return String(record.title || ensureProps(target).text || '')
  }

  if (
    textKey === 'title' ||
    kind.includes('TITLE')
  ) {
    return String(record.title || ensureProps(target).text || '')
  }

  if (
    textKey === 'description' ||
    kind.includes('DESCRIPTION')
  ) {
    return String(record.description || ensureProps(target).description || '')
  }

  if (
    textKey === 'eyebrow' ||
    textKey === 'kicker' ||
    kind.includes('EYEBROW')
  ) {
    const props = ensureProps(target)
    return String(props.eyebrow || props.kicker || record.eyebrow || '')
  }

  if (selection?.elementLabel || selection?.label) {
    return String(selection.elementLabel || selection.label || '')
  }

  return String(record.title || ensureProps(target).text || record.description || '')
}

function resolveSelectedTextTransform(
  currentText: string,
  instruction: string,
): PrototypeTextTransformResult | null {
  const source = String(currentText || '')
  const text = String(instruction || '').trim()

  if (!source || !text) {
    return null
  }

  const replaceTransform = resolveReplaceTextTransform(source, text)
  if (replaceTransform) {
    return replaceTransform
  }

  const appendTransform = resolveAppendTextTransform(source, text)
  if (appendTransform) {
    return appendTransform
  }

  const deleteTransform = resolveDeleteTextTransform(source, text)
  if (deleteTransform) {
    return deleteTransform
  }

  return null
}

function resolveDeleteTextTransform(
  currentText: string,
  instruction: string,
): PrototypeTextTransformResult | null {
  if (!containsAny(instruction, ['删除', '删掉', '删去', '删了', '去掉', '移除', '去除'])) {
    return null
  }

  const quoted = extractQuotedTextList(instruction)
  const chars = Array.from(currentText)

  if (quoted.length > 0) {
    const fragment = quoted[0]

    if (containsAny(instruction, ['最后', '末尾', '结尾', '尾部', '后缀']) && currentText.endsWith(fragment)) {
      return {
        nextText: currentText.slice(0, currentText.length - fragment.length),
        summary: `删掉末尾“${fragment}”`,
      }
    }

    if (containsAny(instruction, ['开头', '前面', '最前', '前缀']) && currentText.startsWith(fragment)) {
      return {
        nextText: currentText.slice(fragment.length),
        summary: `删掉开头“${fragment}”`,
      }
    }

    if (currentText.includes(fragment)) {
      return {
        nextText: currentText.replace(fragment, ''),
        summary: `删掉“${fragment}”`,
      }
    }
  }

  if (containsAny(instruction, ['最后', '末尾', '结尾', '尾部'])) {
    const count = resolveTextTransformCount(instruction)
    return {
      nextText: chars.slice(0, Math.max(0, chars.length - count)).join(''),
      summary: `删掉最后${count}个字`,
    }
  }

  if (containsAny(instruction, ['开头', '前面', '最前', '首个', '第一个'])) {
    const count = resolveTextTransformCount(instruction)
    return {
      nextText: chars.slice(Math.min(chars.length, count)).join(''),
      summary: `删掉开头${count}个字`,
    }
  }

  const directDelete = instruction.match(/(?:删除|删掉|删去|删了|去掉|移除|去除)\s*([^，。；;\n]{1,20})/)
  const fragment = cleanTransformToken(directDelete?.[1] || '')

  if (fragment && currentText.includes(fragment)) {
    return {
      nextText: currentText.replace(fragment, ''),
      summary: `删掉“${fragment}”`,
    }
  }

  return null
}

function resolveAppendTextTransform(
  currentText: string,
  instruction: string,
): PrototypeTextTransformResult | null {
  if (!containsAny(instruction, ['加上', '追加', '补上', '前面加', '后面加', '末尾加'])) {
    return null
  }

  const quoted = extractQuotedTextList(instruction)
  const quotedText = quoted[0] || ''
  const tailMatch = instruction.match(/(?:最后|末尾|结尾|尾部|后面)\s*(?:加上|追加|补上|加)\s*([^，。；;\n]{1,40})/)
  const headMatch = instruction.match(/(?:开头|前面|最前|前缀)\s*(?:加上|追加|补上|加)\s*([^，。；;\n]{1,40})/)
  const genericMatch = instruction.match(/(?:加上|追加|补上)\s*([^，。；;\n]{1,40})/)
  const rawText = quotedText || tailMatch?.[1] || headMatch?.[1] || genericMatch?.[1] || ''
  const fragment = cleanTransformToken(rawText)

  if (!fragment) {
    return null
  }

  if (headMatch || containsAny(instruction, ['开头', '前面', '最前', '前缀'])) {
    return {
      nextText: `${fragment}${currentText}`,
      summary: `在开头加上“${fragment}”`,
    }
  }

  return {
    nextText: `${currentText}${fragment}`,
    summary: `在末尾加上“${fragment}”`,
  }
}

function resolveReplaceTextTransform(
  currentText: string,
  instruction: string,
): PrototypeTextTransformResult | null {
  if (!containsAny(instruction, ['替换成', '替换为', '替换'])) {
    return null
  }

  const quoted = extractQuotedTextList(instruction)

  if (quoted.length >= 2 && currentText.includes(quoted[0])) {
    return {
      nextText: currentText.replace(quoted[0], quoted[1]),
      summary: `把“${quoted[0]}”替换为“${quoted[1]}”`,
    }
  }

  const match = instruction.match(/(?:把|将)?\s*([^，。；;\n]{1,30})\s*(?:替换成|替换为|替换)\s*([^，。；;\n]{1,30})/)
  const from = cleanTransformToken(match?.[1] || '')
  const to = cleanTransformToken(match?.[2] || '')

  if (!from || !to || !currentText.includes(from)) {
    return null
  }

  return {
    nextText: currentText.replace(from, to),
    summary: `把“${from}”替换为“${to}”`,
  }
}

function resolveTextTransformCount(instruction: string): number {
  const match = instruction.match(/([0-9]+|[一二两三四五六七八九十]+)\s*(?:个|位)?\s*(?:字|字符)?/)
  const count = parseChineseCount(match?.[1] || '')

  return Math.min(Math.max(count, 1), 20)
}

function parseChineseCount(value: string): number {
  const raw = String(value || '').trim()

  if (!raw) {
    return 1
  }

  const digit = raw.match(/[0-9]+/)
  if (digit) {
    return Number(digit[0]) || 1
  }

  const map: Record<string, number> = {
    一: 1,
    二: 2,
    两: 2,
    三: 3,
    四: 4,
    五: 5,
    六: 6,
    七: 7,
    八: 8,
    九: 9,
    十: 10,
  }

  if (map[raw]) {
    return map[raw]
  }

  if (raw.startsWith('十')) {
    return 10 + (map[raw.slice(1)] || 0)
  }

  if (raw.includes('十')) {
    const [left, right] = raw.split('十')
    return (map[left] || 1) * 10 + (map[right] || 0)
  }

  return 1
}

function extractQuotedTextList(instruction: string): string[] {
  const result: string[] = []
  const regex = /[“"']([^”"']{1,80})[”"']/g
  let match: RegExpExecArray | null = regex.exec(instruction)

  while (match) {
    const value = String(match[1] || '').trim()
    if (value) {
      result.push(value)
    }
    match = regex.exec(instruction)
  }

  return result
}

function cleanTransformToken(value: string): string {
  return String(value || '')
    .replace(/^(把|将|为|成|叫做|显示为|加上|追加|补上|删除|删掉|删去|去掉|移除|去除|最后|末尾|结尾|尾部|开头|前面|前缀|后缀|的)/, '')
    .replace(/(这个|这一个|该|文字|文案|字|字符|吧|。|，|；|;)$/g, '')
    .trim()
}

function extractTargetText(instruction: string): string {
  const quoted = instruction.match(/[“"]([^”"]{1,80})[”"]|[']([^']{1,80})[']/)
  const quotedText = (quoted?.[1] || quoted?.[2] || '').trim()
  if (quotedText) return quotedText

  const placeholderMatch = instruction.match(/(?:placeholder|占位|提示语|输入提示)\s*(?:改成|改为|换成|为|:|：)?\s*([^，。；;\n]{1,40})/i)
  if (placeholderMatch?.[1]) return cleanExtractedText(placeholderMatch[1])

  const keywordMatch = instruction.match(/(?:文案|文字|标题|按钮文案|字段名|列名|标签名|显示文字|改成|改为|换成)\s*[:：为]?\s*([^，。；;\n]{1,40})/)
  const text = (keywordMatch?.[1] || '').trim()
  if (!text) return ''

  return cleanExtractedText(text)
}

function cleanExtractedText(value: string): string {
  return value
    .replace(/^(把|为|成|叫做|显示为|改成|改为|换成)/, '')
    .replace(/(一点|一些|吧|。|，)$/g, '')
    .trim()
}

function applyTextChange(
  target: UiContractComponent,
  interactions: UiContractInteraction[],
  nextText: string,
  selection: PrototypeElementSelection | null,
): string {
  const record = target as unknown as LooseRecord
  const kind = normalizeKind(selection?.elementKind)
  const instructionKey = `${kind} ${selection?.textRole || ''} ${selection?.textKey || ''}`.toUpperCase()

  if (selection?.fieldKey) {
    const field = findField(target, selection.fieldKey)

    if (field) {
      if (
        kind.includes('INPUT') ||
        kind.includes('CONTROL') ||
        containsAny(instructionKey, ['PLACEHOLDER', 'CONTROL'])
      ) {
        field.placeholder = nextText
        return `输入提示改为“${nextText}”`
      }

      if (containsAny(instructionKey, ['HELP', 'HINT'])) {
        field.helpText = nextText
        return `字段说明改为“${nextText}”`
      }

      field.label = nextText
      return `字段名改为“${nextText}”`
    }

    const item = findComponentItem(target, selection.fieldKey)
    if (item) {
      return applyComponentItemTextChange(item, nextText, selection)
    }
  }

  if (selection?.columnKey) {
    const column = findColumn(target, selection.columnKey)

    if (column) {
      column.label = nextText
      return `表格列名改为“${nextText}”`
    }
  }

  if (selection?.tabKey) {
    const tab = findTab(target, selection.tabKey)

    if (tab) {
      tab.title = nextText
      return `标签页名称改为“${nextText}”`
    }
  }

  if (selection?.actionId) {
    const action = interactions.find(
      (item) => String((item as unknown as LooseRecord).actionId || '') === selection.actionId,
    )

    if (action) {
      ;(action as unknown as LooseRecord).label = nextText
    }

    if (Array.isArray(record.actionIds) && record.actionIds.map(String).includes(selection.actionId)) {
      record.title = nextText
      ensureProps(target).text = nextText
    }

    return `按钮文案改为“${nextText}”`
  }

  if (
    kind.includes('TITLE') ||
    selection?.textRole === 'title' ||
    selection?.textKey === 'title'
  ) {
    record.title = nextText
    const props = ensureProps(target)
    props.title = nextText
    props.text = nextText
    return `标题改为“${nextText}”`
  }

  if (
    kind.includes('DESCRIPTION') ||
    selection?.textRole === 'description' ||
    selection?.textKey === 'description'
  ) {
    record.description = nextText
    ensureProps(target).description = nextText
    return `说明文案改为“${nextText}”`
  }

  if (
    selection?.textRole === 'eyebrow' ||
    selection?.textKey === 'eyebrow' ||
    selection?.textRole === 'kicker' ||
    selection?.textKey === 'kicker' ||
    kind.includes('EYEBROW') ||
    kind.includes('KICKER')
  ) {
    const props = ensureProps(target)
    record.eyebrow = nextText
    props.eyebrow = nextText
    props.kicker = nextText
    return `眉标文案改为“${nextText}”`
  }

  if (isButtonLike(target)) {
    record.title = nextText
    ensureProps(target).text = nextText
    const actionIds = Array.isArray(record.actionIds) ? record.actionIds.map(String) : []

    interactions
      .filter((item) => actionIds.includes(String((item as unknown as LooseRecord).actionId || '')))
      .forEach((item) => {
        ;(item as unknown as LooseRecord).label = nextText
      })

    return `按钮文案改为“${nextText}”`
  }

  if (isFormLike(target) && Array.isArray(record.fields) && record.fields.length === 1) {
    const field = record.fields[0] as unknown as LooseRecord
    field.label = nextText
    return `字段名改为“${nextText}”`
  }

  record.title = nextText
  ensureProps(target).text = nextText
  return `文案改为“${nextText}”`
}

function applyRequiredChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  if (!selection?.fieldKey || !containsAny(instruction, ['必填', '非必填', '可选', '不是必填'])) {
    return ''
  }

  const field = findField(target, selection.fieldKey)
  if (!field) return ''

  if (containsAny(instruction, ['非必填', '不是必填', '可选'])) {
    field.required = false
    return '字段设为非必填'
  }

  field.required = true
  return '字段设为必填'
}

function applyDisabledChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  if (!containsAny(instruction, ['禁用', '不可编辑', '不可用', '启用', '可编辑', '恢复可用'])) {
    return ''
  }

  if (selection?.fieldKey) {
    const field = findField(target, selection.fieldKey)
    if (!field) return ''

    if (containsAny(instruction, ['启用', '可编辑', '恢复可用'])) {
      field.disabled = false
      return '字段恢复可编辑'
    }

    field.disabled = true
    return '字段设为不可编辑'
  }

  const props = ensureProps(target)

  if (containsAny(instruction, ['启用', '可编辑', '恢复可用'])) {
    props.disabled = false
    return '元素恢复可用'
  }

  props.disabled = true
  return '元素设为不可用'
}

function resolveColor(instruction: string) {
  for (const [keyword, value] of Object.entries(COLOR_MAP)) {
    if (instruction.includes(keyword)) return value
  }
  return null
}

function applyColorChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  color: { label: string; backgroundColor: string; color: string; borderColor: string },
) {
  const style = ensureTargetStyle(target, selection)
  style.backgroundColor = color.backgroundColor
  style.color = color.color
  style.borderColor = color.borderColor
  ensureProps(target).colorVariant = color.label
}

function applyMoveChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  const style = ensureTargetStyle(target, selection)
  const props = ensureProps(target)
  const layoutHints = ensureLayoutHints(target)
  const summaries: string[] = []
  const kind = normalizeKind(selection?.elementKind)
  const isActionTarget = Boolean(selection?.actionId) || kind.includes('ACTION') || kind.includes('BUTTON') || isButtonLike(target)

  if (containsAny(instruction, ['表格右上角', '列表右上角', '右上角'])) {
    style.marginLeft = 'auto'
    style.justifySelf = 'end'
    style.alignSelf = 'start'
    style.order = '-20'
    style.whiteSpace = 'nowrap'
    layoutHints.actionPlacement = 'tableToolbarRight'
    layoutHints.preferredPlacement = 'sectionHeaderRight'
    props.align = 'right'
    summaries.push(isActionTarget ? '按钮放到右上角操作区' : '元素放到右上角')
  }

  if (containsAny(instruction, ['表格上方', '列表上方', '放到上方', '移到上方', '置顶', '顶部'])) {
    style.order = '-10'
    style.marginBottom = style.marginBottom || '12px'
    layoutHints.placement = 'top'
    layoutHints.order = -10
    summaries.push('位置调整到上方')
  }

  if (containsAny(instruction, ['表格下方', '列表下方', '放到下方', '移到下方', '置底', '底部'])) {
    style.order = '10'
    style.marginTop = style.marginTop || '12px'
    layoutHints.placement = 'bottom'
    layoutHints.order = 10
    summaries.push('位置调整到下方')
  }

  if (containsAny(instruction, ['靠右', '右对齐', '居右', '放到右边', '放到右侧', '移到右边', '移到右侧'])) {
    style.justifySelf = 'end'
    style.marginLeft = 'auto'
    style.marginRight = '0'
    style.textAlign = 'right'
    if (isActionTarget) {
      style.display = 'inline-flex'
      style.justifyContent = 'center'
    }
    props.align = 'right'
    layoutHints.align = 'right'
    summaries.push('位置调整为靠右')
  }

  if (containsAny(instruction, ['靠左', '左对齐', '居左', '放到左边', '放到左侧', '移到左边', '移到左侧'])) {
    style.justifySelf = 'start'
    style.marginLeft = '0'
    style.marginRight = 'auto'
    style.textAlign = 'left'
    props.align = 'left'
    layoutHints.align = 'left'
    summaries.push('位置调整为靠左')
  }

  if (containsAny(instruction, ['居中', '中间', '居中对齐', '水平居中'])) {
    style.justifySelf = 'center'
    style.marginLeft = 'auto'
    style.marginRight = 'auto'
    style.textAlign = 'center'
    props.align = 'center'
    layoutHints.align = 'center'
    summaries.push('位置调整为居中')
  }

  const step = containsAny(instruction, ['一点', '一些', '稍微']) ? 12 : 24
  let dx = 0
  let dy = 0

  if (containsAny(instruction, ['往右', '右移', '向右'])) dx += step
  if (containsAny(instruction, ['往左', '左移', '向左'])) dx -= step
  if (containsAny(instruction, ['往下', '下移', '向下'])) dy += step
  if (containsAny(instruction, ['往上', '上移', '向上'])) dy -= step

  if (dx !== 0 || dy !== 0) {
    style.transform = `translate(${dx}px, ${dy}px)`
    summaries.push(`位置微调 ${dx ? `${dx > 0 ? '向右' : '向左'}${Math.abs(dx)}px` : ''}${dx && dy ? '、' : ''}${dy ? `${dy > 0 ? '向下' : '向上'}${Math.abs(dy)}px` : ''}`)
  }

  return uniqueSummaries(summaries).join('、')
}

function applySizeChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  const style = ensureTargetStyle(target, selection)
  const props = ensureProps(target)
  const summaries: string[] = []
  const kind = normalizeKind(selection?.elementKind)
  const isActionTarget = Boolean(selection?.actionId) || kind.includes('ACTION') || kind.includes('BUTTON') || isButtonLike(target)

  if (containsAny(instruction, ['两列布局', '改成两列', '字段两列', '表单两列', '两列排布'])) {
    if (isFormLike(target) && !selection?.fieldKey) {
      const layout = ensureFormLayout(target)
      layout.columns = 2
      layout.gap = layout.gap || '16px 20px'
      ensureLayoutHints(target).fieldGrid = 2
      summaries.push('表单改为两列布局')
    } else {
      style.gridColumn = 'span 2'
      style.width = '100%'
      summaries.push('字段宽度调整为两列')
    }
  }

  if (containsAny(instruction, ['三列布局', '改成三列', '表单三列', '三列排布'])) {
    const layout = ensureFormLayout(target)
    layout.columns = 3
    layout.gap = layout.gap || '16px 20px'
    ensureLayoutHints(target).fieldGrid = 3
    summaries.push('表单改为三列布局')
  }

  if (containsAny(instruction, ['四列布局', '改成四列', '表单四列', '四列排布'])) {
    const layout = ensureFormLayout(target)
    layout.columns = 4
    layout.gap = layout.gap || '16px 20px'
    ensureLayoutHints(target).fieldGrid = 4
    summaries.push('表单改为四列布局')
  }

  if (containsAny(instruction, ['全宽', '铺满', '拉满', '占满', '整行', '独占一行', '一整行'])) {
    if (selection?.columnKey) {
      const column = findColumn(target, selection.columnKey)
      if (column) {
        column.width = Math.max(220, Number(column.width || 160))
        summaries.push('表格列调整为宽列')
      }
    } else if (selection?.fieldKey) {
      style.gridColumn = '1 / -1'
      style.width = '100%'
      style.maxWidth = 'none'
      summaries.push('字段调整为整行全宽')
    } else {
      style.width = '100%'
      style.maxWidth = 'none'
      style.minWidth = isActionTarget ? '160px' : '100%'
      if (isActionTarget) {
        style.display = 'inline-flex'
        style.justifyContent = 'center'
      }
      ensureLayoutHints(target).fullWidth = true
      summaries.push('宽度调整为全宽')
    }
  }

  if (containsAny(instruction, ['长一点', '变长', '加长', '更长', '按钮长', '输入框长'])) {
    style.minWidth = nextPx(style.minWidth, isActionTarget ? 128 : 220, 72, 96, 520)
    if (isActionTarget) {
      style.paddingInline = '24px'
      style.whiteSpace = 'nowrap'
      style.display = 'inline-flex'
      style.justifyContent = 'center'
    }
    summaries.push('长度加长')
  }

  if (containsAny(instruction, ['短一点', '变短', '收短'])) {
    style.minWidth = nextPx(style.minWidth, isActionTarget ? 128 : 220, -48, 72, 520)
    style.maxWidth = style.maxWidth || '180px'
    summaries.push('长度收短')
  }

  if (containsAny(instruction, ['高一点', '变高', '加高', '更高'])) {
    style.minHeight = nextPx(style.minHeight, isActionTarget ? 40 : 88, 24, 32, 360)
    if (isActionTarget) {
      style.height = nextPx(style.height, 40, 6, 32, 72)
    }
    summaries.push('高度加高')
  }

  if (containsAny(instruction, ['矮一点', '变矮', '降低高度'])) {
    style.minHeight = nextPx(style.minHeight, isActionTarget ? 40 : 88, -16, 28, 360)
    summaries.push('高度降低')
  }

  if (containsAny(instruction, ['大一点', '更大', '放大'])) {
    style.minWidth = nextPx(style.minWidth, isActionTarget ? 128 : 160, 48, 96, 520)
    style.padding = style.padding || '14px 22px'
    props.size = 'large'
    summaries.push('尺寸放大')
  }

  if (containsAny(instruction, ['小一点', '更小', '缩小'])) {
    style.minWidth = nextPx(style.minWidth, 128, -32, 72, 520)
    style.padding = '8px 12px'
    props.size = 'small'
    summaries.push('尺寸缩小')
  }

  if (containsAny(instruction, ['宽一点', '加宽', '更宽'])) {
    if (selection?.columnKey) {
      const column = findColumn(target, selection.columnKey)
      if (column) {
        const current = Number(column.width || 140)
        column.width = Math.max(80, current + 48)
        summaries.push('表格列加宽')
      }
    } else {
      style.minWidth = nextPx(style.minWidth, isActionTarget ? 128 : 180, 64, 96, 640)
      if (selection?.fieldKey) {
        style.width = style.width || '100%'
      }
      summaries.push('宽度加宽')
    }
  }

  if (containsAny(instruction, ['窄一点', '变窄', '收窄'])) {
    if (selection?.columnKey) {
      const column = findColumn(target, selection.columnKey)
      if (column) {
        const current = Number(column.width || 140)
        column.width = Math.max(80, current - 36)
        summaries.push('表格列收窄')
      }
    } else {
      style.maxWidth = nextPx(style.maxWidth, 220, -48, 96, 640)
      summaries.push('宽度收窄')
    }
  }

  return uniqueSummaries(summaries).join('、')
}

function applyVariantChange(
  target: UiContractComponent,
  interactions: UiContractInteraction[],
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  if (!containsAny(instruction, ['主按钮', '次按钮', '辅助按钮', '危险按钮', '警告按钮', '链接按钮', '普通按钮'])) {
    return ''
  }

  const props = ensureProps(target)
  const style = ensureTargetStyle(target, selection)
  const action = selection?.actionId
    ? interactions.find((item) => String((item as unknown as LooseRecord).actionId || '') === selection.actionId)
    : null
  const actionRecord = action as unknown as LooseRecord | null

  if (containsAny(instruction, ['主按钮'])) {
    props.variant = 'primary'
    props.buttonVariant = 'primary'
    style.backgroundColor = '#2563eb'
    style.color = '#ffffff'
    style.borderColor = '#1d4ed8'
    if (actionRecord) actionRecord.variant = 'primary'
    return '按钮改为主按钮'
  }

  if (containsAny(instruction, ['危险按钮', '警告按钮'])) {
    props.variant = 'danger'
    props.buttonVariant = 'danger'
    style.backgroundColor = '#dc2626'
    style.color = '#ffffff'
    style.borderColor = '#b91c1c'
    if (actionRecord) actionRecord.variant = 'danger'
    return containsAny(instruction, ['警告按钮']) ? '按钮改为警告按钮' : '按钮改为危险按钮'
  }

  if (containsAny(instruction, ['链接按钮'])) {
    props.variant = 'link'
    props.buttonVariant = 'link'
    style.backgroundColor = 'transparent'
    style.color = '#2563eb'
    style.borderColor = 'transparent'
    if (actionRecord) actionRecord.variant = 'link'
    return '按钮改为链接按钮'
  }

  props.variant = 'secondary'
  props.buttonVariant = 'secondary'
  style.backgroundColor = '#ffffff'
  style.color = '#1d4ed8'
  style.borderColor = '#bfdbfe'
  if (actionRecord) actionRecord.variant = 'secondary'
  return '按钮改为次按钮'
}

function ensureLayoutHints(component: UiContractComponent): LooseRecord {
  const props = ensureProps(component)
  if (!props.layoutHints || typeof props.layoutHints !== 'object' || Array.isArray(props.layoutHints)) {
    props.layoutHints = {}
  }
  return props.layoutHints as unknown as LooseRecord
}

function ensureFormLayout(component: UiContractComponent): LooseRecord {
  const props = ensureProps(component)
  if (!props.layout || typeof props.layout !== 'object' || Array.isArray(props.layout)) {
    props.layout = {}
  }
  return props.layout as unknown as LooseRecord
}

function nextPx(
  current: unknown,
  fallback: number,
  delta: number,
  min: number,
  max: number,
): string {
  const parsed = Number.parseInt(String(current || '').replace('px', ''), 10)
  const base = Number.isFinite(parsed) ? parsed : fallback
  return `${Math.min(max, Math.max(min, base + delta))}px`
}

function uniqueSummaries(items: string[]): string[] {
  return Array.from(new Set(items.filter(Boolean)))
}

function applyVisibilityChange(
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
  instruction: string,
): string {
  if (!containsAny(instruction, ['隐藏', '先不显示', '不要显示', '显示出来', '恢复显示', '显示'])) {
    return ''
  }

  const shouldHide = containsAny(instruction, ['隐藏', '先不显示', '不要显示'])

  if (selection?.fieldKey) {
    const field = findField(target, selection.fieldKey)
    if (field) {
      field.hidden = shouldHide
      ensureTargetStyle(target, selection).display = shouldHide ? 'none' : ''
      return shouldHide ? '字段设为隐藏' : '字段恢复显示'
    }

    const item = findComponentItem(target, selection.fieldKey)
    if (item) {
      item.hidden = shouldHide
      ensureTargetStyle(target, selection).display = shouldHide ? 'none' : ''
      return shouldHide ? '条目设为隐藏' : '条目恢复显示'
    }
  }

  if (selection?.columnKey) {
    const column = findColumn(target, selection.columnKey)
    if (column) {
      column.hidden = shouldHide
      ensureTargetStyle(target, selection).display = shouldHide ? 'none' : ''
      return shouldHide ? '表格列设为隐藏' : '表格列恢复显示'
    }
  }

  if (selection?.tabKey) {
    const tab = findTab(target, selection.tabKey)
    if (tab) {
      tab.hidden = shouldHide
      ensureTargetStyle(target, selection).display = shouldHide ? 'none' : ''
      return shouldHide ? '标签页设为隐藏' : '标签页恢复显示'
    }
  }

  ensureProps(target).hidden = shouldHide
  return shouldHide ? '设为隐藏' : '恢复显示'
}

function ensureProps(component: UiContractComponent): LooseRecord {
  const record = component as unknown as LooseRecord
  if (!record.props || typeof record.props !== 'object' || Array.isArray(record.props)) {
    record.props = {}
  }
  return record.props as unknown as LooseRecord
}

function ensureStyle(props: LooseRecord): LooseRecord {
  if (!props.style || typeof props.style !== 'object' || Array.isArray(props.style)) {
    props.style = {}
  }
  return props.style as unknown as LooseRecord
}

function ensureTargetStyle(
  component: UiContractComponent,
  selection: PrototypeElementSelection | null,
): LooseRecord {
  const key = elementStyleKey(selection)

  if (!key) {
    return ensureStyle(ensureProps(component))
  }

  const props = ensureProps(component)

  if (!props.elementStyles || typeof props.elementStyles !== 'object' || Array.isArray(props.elementStyles)) {
    props.elementStyles = {}
  }

  const styles = props.elementStyles as unknown as LooseRecord

  if (!styles[key] || typeof styles[key] !== 'object' || Array.isArray(styles[key])) {
    styles[key] = {}
  }

  return styles[key] as unknown as LooseRecord
}

function elementStyleKey(selection: PrototypeElementSelection | null): string {
  if (!selection) return ''

  const kind = normalizeKind(selection.elementKind)

  if (selection.fieldKey) {
    if (kind.includes('INPUT') || kind.includes('CONTROL')) return `fieldControl:${selection.fieldKey}`
    if (kind.includes('LABEL')) return `fieldLabel:${selection.fieldKey}`
    return `field:${selection.fieldKey}`
  }

  if (selection.actionId) return `action:${selection.actionId}`
  if (selection.columnKey) return `column:${selection.columnKey}`
  if (selection.tabKey) return `tab:${selection.tabKey}`

  if (selection.textKey || selection.textRole) {
    return `text:${selection.textKey || selection.textRole}`
  }

  if (kind.includes('TITLE')) return 'text:title'
  if (kind.includes('DESCRIPTION')) return 'text:description'
  if (kind.includes('TEXT')) return 'text:text'

  return ''
}


function findComponentItem(
  component: UiContractComponent,
  itemKey?: string | null,
): LooseRecord | null {
  const expected = String(itemKey || '').trim()
  if (!expected) return null

  const record = component as unknown as LooseRecord
  const props = ensureProps(component)
  const arrayKeys = [
    'items',
    'metrics',
    'stats',
    'kpis',
    'cards',
    'data',
    'series',
    'values',
    'timeline',
    'records',
    'logs',
    'events',
    'files',
    'fileList',
    'nodes',
    'treeData',
    'options',
  ]

  for (const key of arrayKeys) {
    const found = findComponentItemInArray((props[key] || record[key]) as unknown, expected)
    if (found) return found
  }

  return null
}

function findComponentItemInArray(value: unknown, expected: string): LooseRecord | null {
  if (!Array.isArray(value)) return null

  for (let index = 0; index < value.length; index += 1) {
    const item = value[index]
    if (!item || typeof item !== 'object' || Array.isArray(item)) continue

    const record = item as unknown as LooseRecord
    const identity = String(
      record.key ||
        record.id ||
        record.fieldKey ||
        record.code ||
        record.name ||
        record.title ||
        record.label ||
        index,
    )

    if (identity === expected) return record

    const child = findComponentItemInArray(record.children || record.items || record.nodes, expected)
    if (child) return child
  }

  return null
}

function resolveComponentItemText(
  item: LooseRecord,
  selection: PrototypeElementSelection | null,
): string {
  const textKey = String(selection?.textKey || selection?.textRole || '').trim().toLowerCase()

  if (textKey.includes('value') || textKey.includes('count') || textKey.includes('amount')) {
    return String(item.value ?? item.count ?? item.amount ?? item.total ?? item.percent ?? '')
  }

  if (
    textKey.includes('description') ||
    textKey.includes('hint') ||
    textKey.includes('message') ||
    textKey.includes('subtitle')
  ) {
    return String(item.description || item.hint || item.subtitle || item.message || item.remark || '')
  }

  if (textKey.includes('file')) {
    return String(item.fileName || item.filename || item.name || item.title || item.label || '')
  }

  return String(item.title || item.label || item.name || item.fileName || item.filename || item.key || item.value || '')
}

function applyComponentItemTextChange(
  item: LooseRecord,
  nextText: string,
  selection: PrototypeElementSelection | null,
): string {
  const textKey = String(selection?.textKey || selection?.textRole || '').trim().toLowerCase()

  if (textKey.includes('value') || textKey.includes('count') || textKey.includes('amount')) {
    item.value = nextText
    return `条目数值改为“${nextText}”`
  }

  if (
    textKey.includes('description') ||
    textKey.includes('hint') ||
    textKey.includes('message') ||
    textKey.includes('subtitle')
  ) {
    if ('hint' in item || textKey.includes('hint')) item.hint = nextText
    else if ('message' in item || textKey.includes('message')) item.message = nextText
    else item.description = nextText
    return `条目说明改为“${nextText}”`
  }

  if (textKey.includes('file')) {
    item.fileName = nextText
    item.name = nextText
    return `文件名改为“${nextText}”`
  }

  if ('title' in item || textKey.includes('title') || textKey.includes('step') || textKey.includes('timeline')) {
    item.title = nextText
    return `条目标题改为“${nextText}”`
  }

  item.label = nextText
  return `条目名称改为“${nextText}”`
}

function findField(
  component: UiContractComponent,
  fieldKey?: string | null,
): LooseRecord | null {
  if (!fieldKey) return null

  const record = component as unknown as LooseRecord
  const fields = Array.isArray(record.fields) ? record.fields : []
  const found = fields.find((field) =>
    String((field as unknown as LooseRecord).fieldKey || '') === fieldKey,
  )

  return found && typeof found === 'object'
    ? found as unknown as LooseRecord
    : null
}

function findColumn(
  component: UiContractComponent,
  columnKey?: string | null,
): LooseRecord | null {
  if (!columnKey) return null

  const record = component as unknown as LooseRecord
  const columns = Array.isArray(record.columns) ? record.columns : []
  const found = columns.find((column) =>
    String((column as unknown as LooseRecord).columnKey || '') === columnKey,
  )

  return found && typeof found === 'object'
    ? found as unknown as LooseRecord
    : null
}

function findTab(
  component: UiContractComponent,
  tabKey?: string | null,
): LooseRecord | null {
  if (!tabKey) return null

  const record = component as unknown as LooseRecord
  const tabs = Array.isArray(record.tabs) ? record.tabs : []
  const found = tabs.find((tab) =>
    String((tab as unknown as LooseRecord).tabKey || '') === tabKey,
  )

  return found && typeof found === 'object'
    ? found as unknown as LooseRecord
    : null
}

function normalizeKind(value: unknown): string {
  return String(value || '').trim().toUpperCase()
}

function syncVisiblePageTextAfterLocalEdit(
  contract: UiContractContent,
  target: UiContractComponent,
  selection: PrototypeElementSelection | null,
) {
  const targetRecord = target as unknown as LooseRecord
  const targetType = String(targetRecord.type || targetRecord.componentType || '').toUpperCase()
  const kind = normalizeKind(selection?.elementKind)
  const textKey = String(selection?.textKey || selection?.textRole || '').trim().toLowerCase()
  const page = (contract as unknown as LooseRecord).page as unknown as LooseRecord | undefined

  if (!page || typeof page !== 'object') {
    return
  }

  const isPageHeader =
    targetType.includes('PAGE_HEADER') ||
    String(targetRecord.componentId || '').toLowerCase().includes('page_header') ||
    String(selection?.componentType || '').toUpperCase().includes('PAGE_HEADER')

  if (!isPageHeader) {
    return
  }

  if (textKey === 'title' || kind.includes('TITLE')) {
    const nextTitle = String(
      targetRecord.title ||
      ensureProps(target).title ||
      ensureProps(target).text ||
      '',
    ).trim()

    if (nextTitle) {
      page.title = nextTitle
      page.pageName = nextTitle
      page.name = nextTitle

      const identity = (contract as unknown as LooseRecord).identity
      if (identity && typeof identity === 'object' && !Array.isArray(identity)) {
        ;(identity as unknown as LooseRecord).pageName = nextTitle
        ;(identity as unknown as LooseRecord).title = nextTitle
      }

      const metadata = (contract as unknown as LooseRecord).metadata
      if (metadata && typeof metadata === 'object' && !Array.isArray(metadata)) {
        ;(metadata as unknown as LooseRecord).pageName = nextTitle
        ;(metadata as unknown as LooseRecord).title = nextTitle
      }
    }
  }

  if (textKey === 'description' || kind.includes('DESCRIPTION')) {
    const nextDescription = String(
      targetRecord.description ||
      ensureProps(target).description ||
      '',
    ).trim()

    if (nextDescription) {
      page.description = nextDescription
    }
  }
}

function bumpPrototypeLocalEditRevision(contract: UiContractContent) {
  const record = contract as unknown as LooseRecord
  const metadata =
    record.metadata && typeof record.metadata === 'object' && !Array.isArray(record.metadata)
      ? record.metadata as unknown as LooseRecord
      : {}

  const current = Number(metadata.localEditRevision || 0)
  metadata.localEditRevision = Number.isFinite(current) ? current + 1 : 1
  metadata.localEditUpdatedAt = new Date().toISOString()
  record.metadata = metadata
}

function appendLocalEditMetadata(
  contract: UiContractContent,
  instruction: string,
  targetComponentId: string,
  selection: PrototypeElementSelection | null,
  changes: string[],
) {
  const record = contract as unknown as LooseRecord
  const metadata =
    record.metadata && typeof record.metadata === 'object' && !Array.isArray(record.metadata)
      ? record.metadata as unknown as LooseRecord
      : {}

  const edits = Array.isArray(metadata.prototypeLocalEdits)
    ? metadata.prototypeLocalEdits as unknown[]
    : []

  const requirementSyncImpact = classifyPrototypeRequirementSync(
    instruction,
    selection,
    changes,
  )

  edits.push({
    source: 'A9_7_CANVAS_SELECTION_PROTOTYPE_EDIT',
    instruction,
    targetComponentId,
    targetSelection: selection || null,
    changes,
    requirementSyncClassification: requirementSyncImpact.classification,
    requirementSyncImpact,
    visualOnly: requirementSyncImpact.visualOnly,
    targetArtifactTypes: requirementSyncImpact.targetArtifactTypes,
    semanticChangeTypes: requirementSyncImpact.semanticChangeTypes,
    visualChangeTypes: requirementSyncImpact.visualChangeTypes,
    editedAt: new Date().toISOString(),
  })

  metadata.prototypeLocalEdits = edits
  metadata.prototypeRequirementSync = summarizePrototypeRequirementSync(edits)
  record.metadata = metadata
}


function classifyPrototypeRequirementSync(
  instruction: string,
  selection: PrototypeElementSelection | null,
  changes: string[],
): LooseRecord {
  const text = [
    instruction,
    ...(changes || []),
    selection?.elementKind || '',
    selection?.textKey || '',
    selection?.textRole || '',
  ].join(' ').toLowerCase()

  const semanticChangeTypes: string[] = []
  const visualChangeTypes: string[] = []

  const add = (target: string[], value: string) => {
    if (!target.includes(value)) target.push(value)
  }

  const kind = normalizeKind(selection?.elementKind)

  if (
    selection?.fieldKey ||
    containsAny(text, ['字段名', '输入提示', 'placeholder', '占位', '字段说明', '必填', '非必填', '可选'])
  ) {
    if (containsAny(text, ['字段名', '输入提示', 'placeholder', '占位', '字段说明', '必填', '非必填', '可选'])) {
      add(semanticChangeTypes, 'FIELD_OR_VALIDATION')
    }
  }

  if (
    selection?.columnKey ||
    containsAny(text, ['表格列名', '列名', '表格列改为', '隐藏这个表格列'])
  ) {
    if (containsAny(text, ['表格列名', '列名', '表格列改为', '隐藏这个表格列'])) {
      add(semanticChangeTypes, 'TABLE_COLUMN')
    }
  }

  if (
    selection?.actionId ||
    kind.includes('ACTION') ||
    kind.includes('BUTTON') ||
    containsAny(text, ['按钮文案', '跳转', '导航', '审批', '提交', '保存为', '删除动作', '新增按钮'])
  ) {
    if (containsAny(text, ['按钮文案', '跳转', '导航', '审批', '提交逻辑', '保存逻辑', '删除动作', '新增按钮'])) {
      add(semanticChangeTypes, 'ACTION_OR_NAVIGATION')
    }
  }

  if (
    containsAny(text, ['页面标题', '标题改为', '说明文案', '页面说明']) ||
    kind.includes('TITLE') ||
    kind.includes('DESCRIPTION')
  ) {
    if (containsAny(text, ['页面标题', '标题改为', '说明文案', '页面说明', '文案改为'])) {
      add(semanticChangeTypes, 'PAGE_COPY')
    }
  }

  if (containsAny(text, ['隐藏', '显示出来', '恢复显示', '禁用', '不可编辑', '不可用', '启用'])) {
    add(semanticChangeTypes, 'VISIBILITY_OR_AVAILABILITY')
  }

  if (containsAny(text, ['删除', '删掉', '删去', '去掉', '移除', '新增', '添加', '加上', '追加', '补上'])) {
    add(semanticChangeTypes, 'STRUCTURE_CHANGE')
  }

  if (containsAny(text, ['颜色', '蓝色', '绿色', '红色', '橙色', '紫色', '灰色', '白色', '黑色'])) {
    add(visualChangeTypes, 'COLOR')
  }

  if (containsAny(text, ['宽度', '宽一点', '窄一点', '长一点', '短一点', '全宽', '铺满', '拉满', '高度', '高一点', '矮一点', '大一点', '小一点', '放大', '缩小'])) {
    add(visualChangeTypes, 'SIZE')
  }

  if (containsAny(text, ['靠右', '靠左', '居中', '右对齐', '左对齐', '右上角', '上方', '下方', '置顶', '置底', '往左', '往右', '往上', '往下'])) {
    add(visualChangeTypes, 'POSITION')
  }

  if (containsAny(text, ['两列', '三列', '四列', '整行', '独占一行', '布局'])) {
    add(visualChangeTypes, 'LAYOUT')
  }

  if (containsAny(text, ['主按钮', '次按钮', '辅助按钮', '危险按钮', '警告按钮', '链接按钮', '普通按钮'])) {
    add(visualChangeTypes, 'BUTTON_VARIANT')
  }

  const hasSemantic = semanticChangeTypes.length > 0
  const hasVisual = visualChangeTypes.length > 0
  const visualOnly = hasVisual && !hasSemantic

  return {
    schemaVersion: 'A9.10.14.4.3.15.4.7.3',
    classification: visualOnly ? 'VISUAL_ONLY' : 'REQUIREMENT_SYNC_REVIEW_REQUIRED',
    visualOnly,
    requiresRequirementSync: !visualOnly,
    targetArtifactTypes: visualOnly ? [] : ['MODULE_PRD', 'PAGE_SPEC'],
    semanticChangeTypes,
    visualChangeTypes,
    reason: visualOnly
      ? 'Only layout/style/position/size/button-variant changed.'
      : 'Prototype edit may affect field/action/navigation/copy/visibility semantics.',
  }
}

function summarizePrototypeRequirementSync(edits: unknown[]): LooseRecord {
  const visualOnlyEdits: LooseRecord[] = []
  const semanticEdits: LooseRecord[] = []

  for (const item of edits) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) continue
    const record = item as LooseRecord
    const impact = record.requirementSyncImpact &&
      typeof record.requirementSyncImpact === 'object' &&
      !Array.isArray(record.requirementSyncImpact)
      ? record.requirementSyncImpact as LooseRecord
      : {}
    const visualOnly = Boolean(impact.visualOnly || record.visualOnly)
    if (visualOnly) {
      visualOnlyEdits.push(record)
    } else {
      semanticEdits.push(record)
    }
  }

  return {
    schemaVersion: 'A9.10.14.4.3.15.4.7.3',
    mode: semanticEdits.length ? 'REQUIREMENT_SYNC_REVIEW_REQUIRED' : 'UI_CONTRACT_ONLY',
    visualOnly: semanticEdits.length === 0,
    totalEditCount: edits.length,
    visualOnlyEditCount: visualOnlyEdits.length,
    semanticEditCount: semanticEdits.length,
    targetArtifactTypes: semanticEdits.length ? ['MODULE_PRD', 'PAGE_SPEC'] : [],
    policy: 'Visual-only edits stay in UI_CONTRACT. Field/action/navigation/copy/visibility edits require PRD/PAGE_SPEC review.',
  }
}


function containsAny(text: string, keywords: string[]): boolean {
  return keywords.some((keyword) => text.includes(keyword))
}
