<!-- A9.10.14.4.3.15.3.5_6_REQUIREMENT_DIAGNOSTIC_BATCH -->
<template>
  <section class="a91536-panel">
    <header class="a91536-header">
      <div>
        <p>Requirement Diagnostic</p>
        <h4>需求资产体检</h4>
        <span>不调用 LLM，只检查 MODULE_PRD / PAGE_SPEC / UI_CONTRACT 是否和当前模块树一致。</span>
      </div>

      <div class="a91536-actions">
        <button
          type="button"
          class="a91536-secondary"
          :disabled="refreshing"
          @click="refreshCurrentDiagnostic"
        >
          {{ refreshing ? '刷新中...' : '刷新体检' }}
        </button>
        <button
          v-if="canRunBatch"
          type="button"
          class="a91536-primary"
          :disabled="batchRunning"
          @click="runBatchDiagnostic"
        >
          {{ batchRunning ? '检查中...' : `一键检查 ${batchTargetPages.length} 个 PAGE` }}
        </button>
      </div>
    </header>

    <div class="a91536-score-row">
      <article :class="['a91536-score-card', `state-${currentDiagnostic.state}`]">
        <strong>{{ currentDiagnostic.score }}</strong>
        <span>{{ currentDiagnostic.statusText }}</span>
      </article>
      <article>
        <strong>{{ currentDiagnostic.prdCoverageText }}</strong>
        <span>PRD 页面契约</span>
      </article>
      <article>
        <strong>{{ currentDiagnostic.pageSpecText }}</strong>
        <span>PAGE_SPEC</span>
      </article>
      <article>
        <strong>{{ currentDiagnostic.uiContractText }}</strong>
        <span>UI_CONTRACT</span>
      </article>
    </div>

    <div v-if="currentDiagnostic.blockers.length" class="a91536-message-list blocked">
      <strong>阻断项</strong>
      <ul>
        <li v-for="item in currentDiagnostic.blockers" :key="item">{{ item }}</li>
      </ul>
    </div>

    <div v-if="currentDiagnostic.warnings.length" class="a91536-message-list warning">
      <strong>提醒</strong>
      <ul>
        <li v-for="item in currentDiagnostic.warnings" :key="item">{{ item }}</li>
      </ul>
    </div>

    <div v-if="!currentDiagnostic.blockers.length && !currentDiagnostic.warnings.length" class="a91536-message-list ready">
      <strong>当前节点可继续推进</strong>
      <span>{{ currentDiagnostic.readyHint }}</span>
    </div>

    <div v-if="batchItems.length" class="a91536-batch">
      <div class="a91536-batch-head">
        <div>
          <strong>批量检查结果</strong>
          <span>{{ batchSummary }}</span>
        </div>
        <button type="button" class="a91536-secondary" @click="batchItems = []">
          收起结果
        </button>
      </div>

      <div class="a91536-batch-table">
        <article
          v-for="item in batchItems"
          :key="item.pageId"
          :class="['a91536-batch-row', `state-${item.state}`]"
        >
          <div>
            <strong>{{ item.pageName }}</strong>
            <span>{{ item.pageKey || '-' }} · {{ item.pageType || 'PAGE' }}</span>
          </div>
          <em>{{ item.score }} / 100</em>
          <span>{{ item.pageSpecText }}</span>
          <span>{{ item.uiContractText }}</span>
          <small>
            {{ item.blockers[0] || item.warnings[0] || '可继续推进' }}
          </small>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchCurrentModuleArtifact } from '../api/artifactApi'
import { getModuleDisplayName, getModuleStableKey } from '../api'
import type { ArtifactResponse, ModuleTreeNode } from '../types/codemind'
import { isPageNode, resolveRequirementNodeType } from '../domain/requirementNodePolicy'

defineOptions({
  name: 'RequirementDiagnosticPanel',
})

type ArtifactLike = ArtifactResponse | Record<string, any> | null | undefined

type DiagnosticState = 'ready' | 'warning' | 'blocked'

type DiagnosticResult = {
  pageId: string
  pageName: string
  pageKey: string
  pageType: string
  state: DiagnosticState
  score: number
  statusText: string
  prdCoverageText: string
  pageSpecText: string
  uiContractText: string
  readyHint: string
  blockers: string[]
  warnings: string[]
}

const props = withDefaults(
  defineProps<{
    workspaceId?: string
    projectCode?: string
    selectedModule: ModuleTreeNode | null
    moduleTree?: ModuleTreeNode[]
    modulePrdArtifact?: ArtifactResponse | null
    pageSpecArtifact?: ArtifactResponse | null
    uiContractArtifact?: ArtifactResponse | null
    uiContractReadiness?: Record<string, unknown> | null
    developmentHandoffReadiness?: Record<string, unknown> | null
    confirmedUiContractSnapshot?: Record<string, unknown> | null
  }>(),
  {
    workspaceId: '',
    projectCode: '',
    moduleTree: () => [],
    modulePrdArtifact: null,
    pageSpecArtifact: null,
    uiContractArtifact: null,
    uiContractReadiness: null,
    developmentHandoffReadiness: null,
    confirmedUiContractSnapshot: null,
  },
)

const refreshing = ref(false)
const batchRunning = ref(false)
const selectedPageModulePrdArtifact = ref<ArtifactResponse | null>(null)
const selectedPageSpecArtifact = ref<ArtifactResponse | null>(null)
const selectedPageUiContractArtifact = ref<ArtifactResponse | null>(null)
const batchItems = ref<DiagnosticResult[]>([])

const selectedNodeType = computed(() => resolveRequirementNodeType(props.selectedModule))
const selectedOwnerModule = computed(() => resolveOwnerModule(props.selectedModule))
const selectedModulePrdForDiagnostic = computed<ArtifactLike>(() =>
  selectedNodeType.value === 'PAGE'
    ? selectedPageModulePrdArtifact.value || props.modulePrdArtifact
    : props.modulePrdArtifact,
)

const batchTargetPages = computed(() => {
  if (!props.selectedModule) return []
  if (selectedNodeType.value === 'PAGE') return [props.selectedModule]
  return collectDescendantPages(props.selectedModule)
})

const canRunBatch = computed(() =>
  Boolean(props.selectedModule && selectedNodeType.value !== 'FEATURE' && batchTargetPages.value.length > 0),
)

const currentDiagnostic = computed<DiagnosticResult>(() => {
  const node = props.selectedModule
  if (!node) {
    return emptyDiagnostic('未选择节点', '请先选择左侧需求节点。')
  }

  if (selectedNodeType.value === 'PAGE') {
    return buildPageDiagnostic({
      page: node,
      modulePrdArtifact: selectedModulePrdForDiagnostic.value,
      pageSpecArtifact: props.pageSpecArtifact || selectedPageSpecArtifact.value,
      uiContractArtifact: props.uiContractArtifact || selectedPageUiContractArtifact.value,
    })
  }

  return buildModuleDiagnostic(node, props.modulePrdArtifact)
})

const batchSummary = computed(() => {
  const total = batchItems.value.length
  const blocked = batchItems.value.filter((item) => item.state === 'blocked').length
  const warning = batchItems.value.filter((item) => item.state === 'warning').length
  const ready = total - blocked - warning
  return `共 ${total} 个 PAGE；可推进 ${ready} 个，提醒 ${warning} 个，阻断 ${blocked} 个。`
})

watch(
  () => [
    props.selectedModule?.id || '',
    props.workspaceId,
    props.projectCode,
    artifactIdentity(props.modulePrdArtifact),
    artifactIdentity(props.pageSpecArtifact),
    artifactIdentity(props.uiContractArtifact),
  ],
  () => {
    selectedPageModulePrdArtifact.value = null
    selectedPageSpecArtifact.value = null
    selectedPageUiContractArtifact.value = null
    batchItems.value = []
    void refreshCurrentDiagnostic()
  },
  { immediate: true },
)

async function refreshCurrentDiagnostic(): Promise<void> {
  const node = props.selectedModule
  if (!node?.id || !props.workspaceId || !props.projectCode) {
    return
  }

  if (selectedNodeType.value !== 'PAGE') {
    return
  }

  refreshing.value = true
  try {
    const owner = selectedOwnerModule.value
    selectedPageModulePrdArtifact.value = owner?.id
      ? await fetchArtifactSafe(owner.id, 'MODULE_PRD')
      : props.modulePrdArtifact || null

    selectedPageSpecArtifact.value = props.pageSpecArtifact || await fetchArtifactSafe(node.id, 'PAGE_SPEC')
    selectedPageUiContractArtifact.value = props.uiContractArtifact || await fetchArtifactSafe(node.id, 'UI_CONTRACT')
  } finally {
    refreshing.value = false
  }
}

async function runBatchDiagnostic(): Promise<void> {
  const owner = selectedNodeType.value === 'PAGE'
    ? selectedOwnerModule.value
    : props.selectedModule

  if (!owner?.id || !props.workspaceId || !props.projectCode) {
    return
  }

  const pages = batchTargetPages.value
  batchRunning.value = true
  batchItems.value = []

  try {
    const modulePrd = props.modulePrdArtifact || await fetchArtifactSafe(owner.id, 'MODULE_PRD')
    const result: DiagnosticResult[] = []

    for (const page of pages) {
      if (!page.id) continue

      const [pageSpec, uiContract] = await Promise.all([
        fetchArtifactSafe(page.id, 'PAGE_SPEC'),
        fetchArtifactSafe(page.id, 'UI_CONTRACT'),
      ])

      result.push(buildPageDiagnostic({
        page,
        modulePrdArtifact: modulePrd,
        pageSpecArtifact: pageSpec,
        uiContractArtifact: uiContract,
      }))
    }

    batchItems.value = result
  } finally {
    batchRunning.value = false
  }
}

async function fetchArtifactSafe(moduleId: string, artifactType: string): Promise<ArtifactResponse | null> {
  try {
    const artifact = await fetchCurrentModuleArtifact(
      props.workspaceId,
      props.projectCode,
      moduleId,
      artifactType,
    )

    return artifact || null
  } catch {
    return null
  }
}

function buildModuleDiagnostic(node: ModuleTreeNode, modulePrdArtifact: ArtifactLike): DiagnosticResult {
  const pages = collectDescendantPages(node)
  const prdContent = artifactContent(modulePrdArtifact)
  const pageRequirements = pageRequirementList(prdContent)
  const blockers: string[] = []
  const warnings: string[] = []

  if (!artifactExists(modulePrdArtifact)) {
    blockers.push('当前 MODULE 还没有 MODULE_PRD，无法为下级 PAGE 提供页面契约。')
  }

  if (pages.length > 0 && pageRequirements.length === 0) {
    blockers.push('MODULE_PRD 缺少 page_requirements，页面原型生成没有可锁定的业务契约。')
  }

  const missingPages = pages.filter((page) => !matchPageRequirement(pageRequirements, page))
  if (missingPages.length > 0) {
    blockers.push(`有 ${missingPages.length} 个 PAGE 没有匹配到 PRD 页面契约：${missingPages.slice(0, 5).map((page) => getModuleDisplayName(page)).join('、')}${missingPages.length > 5 ? '等' : ''}。`)
  }

  if (pages.length === 0) {
    warnings.push('当前 MODULE 下没有 PAGE，后续无法生成交互原型。')
  }

  const score = scoreFrom(blockers, warnings)
  const state = stateFrom(blockers, warnings)

  return {
    pageId: String(node.id || ''),
    pageName: getModuleDisplayName(node),
    pageKey: getModuleStableKey(node),
    pageType: selectedNodeType.value || 'MODULE',
    state,
    score,
    statusText: statusText(state),
    prdCoverageText: pages.length ? `${Math.max(0, pages.length - missingPages.length)} / ${pages.length}` : '无 PAGE',
    pageSpecText: '批量检查',
    uiContractText: '批量检查',
    readyHint: pages.length ? '可以一键检查下级页面资产。' : '请先补齐 PAGE 页面节点。',
    blockers,
    warnings,
  }
}

function buildPageDiagnostic(input: {
  page: ModuleTreeNode
  modulePrdArtifact: ArtifactLike
  pageSpecArtifact: ArtifactLike
  uiContractArtifact: ArtifactLike
}): DiagnosticResult {
  const page = input.page
  const prdContent = artifactContent(input.modulePrdArtifact)
  const requirements = pageRequirementList(prdContent)
  const requirement = matchPageRequirement(requirements, page)
  const pageSpecExists = artifactExists(input.pageSpecArtifact)
  const uiContractExists = artifactExists(input.uiContractArtifact)
  const contract = normalizeUiContract(input.uiContractArtifact)

  const blockers: string[] = []
  const warnings: string[] = []

  if (!artifactExists(input.modulePrdArtifact)) {
    blockers.push('未找到所属 MODULE_PRD，无法校验页面契约。')
  }

  if (!requirement) {
    blockers.push('当前 PAGE 没有匹配到 MODULE_PRD.page_requirements。')
  }

  if (!pageSpecExists) {
    blockers.push('PAGE_SPEC 尚未生成。')
  }

  if (!uiContractExists) {
    blockers.push('UI_CONTRACT 尚未生成。')
  }

  const requirementPageType = normalizePageType(textOf(
    requirement?.page_type ||
      requirement?.pageType ||
      requirement?.layout_type ||
      requirement?.layoutType ||
      pageTypeFromNode(page),
  ))

  if (contract && requirement) {
    const footprint = collectUiContractFootprint(contract)

    if (requirementPageType === 'LIST' || requirementPageType === 'SEARCH') {
      if (!footprint.componentTypes.has('TABLE')) {
        blockers.push('LIST / SEARCH 页面缺少 TABLE 结果区。')
      }
      const tableColumns = normalizeStringList(requirement.table_columns || requirement.tableColumns)
      const missingColumns = missingLabels(tableColumns, footprint.columnLabels, footprint.columnKeys)
      if (missingColumns.length) {
        blockers.push(`表格列缺失：${missingColumns.slice(0, 6).join('、')}。`)
      }
      const rowActions = normalizeStringList(requirement.row_actions || requirement.rowActions)
      const missingRowActions = missingLabels(rowActions, footprint.actionLabels, footprint.actionIds)
      if (missingRowActions.length) {
        warnings.push(`行操作可能缺失：${missingRowActions.slice(0, 6).join('、')}。`)
      }
    }

    if (requirementPageType === 'FORM') {
      if (!footprint.componentTypes.has('FORM') && !footprint.componentTypes.has('FIELD_GROUP')) {
        blockers.push('FORM 页面缺少 FORM / FIELD_GROUP 表单结构。')
      }
      const formFields = normalizeStringList(requirement.form_fields || requirement.formFields)
      const missingFields = missingLabels(formFields, footprint.fieldLabels, footprint.fieldKeys)
      if (missingFields.length) {
        blockers.push(`表单字段缺失：${missingFields.slice(0, 8).join('、')}。`)
      }
      if (!hasAnyAction(footprint, ['保存', '提交', '确定', '完成'])) {
        warnings.push('FORM 页面没有识别到保存 / 提交类主操作。')
      }
    }

    if (requirementPageType === 'DETAIL') {
      const detailFields = normalizeStringList(
        requirement.table_columns ||
          requirement.tableColumns ||
          requirement.form_fields ||
          requirement.formFields,
      )
      const missingDetailFields = missingLabels(detailFields, footprint.visibleLabels, footprint.fieldKeys)
      if (missingDetailFields.length) {
        warnings.push(`详情字段可能缺失：${missingDetailFields.slice(0, 8).join('、')}。`)
      }
    }

    if (requirementPageType === 'APPROVAL') {
      if (!hasAnyAction(footprint, ['通过', '驳回', '审核', '审批'])) {
        blockers.push('APPROVAL 页面缺少通过 / 驳回 / 审核类动作。')
      }
      if (!footprint.componentTypes.has('FORM') && !footprint.visibleLabels.some((label) => /审核意见|审批意见|驳回原因/.test(label))) {
        warnings.push('APPROVAL 页面没有明显审核意见区。')
      }
    }

    const primaryActions = normalizeStringList(requirement.primary_actions || requirement.primaryActions || requirement.user_actions || requirement.userActions)
    const missingPrimaryActions = missingLabels(primaryActions, footprint.actionLabels, footprint.actionIds)
    if (missingPrimaryActions.length) {
      warnings.push(`主操作可能缺失：${missingPrimaryActions.slice(0, 6).join('、')}。`)
    }
  }

  const readiness = props.uiContractReadiness || {}
  const readinessBlockers = Array.isArray((readiness as any).blockers) ? (readiness as any).blockers : []
  if (isPageNode(page) && readinessBlockers.length > 0 && !uiContractExists) {
    warnings.push(`生成准备度提醒：${readinessBlockers.slice(0, 2).join('；')}。`)
  }

  const score = scoreFrom(blockers, warnings)
  const state = stateFrom(blockers, warnings)

  return {
    pageId: String(page.id || ''),
    pageName: getModuleDisplayName(page),
    pageKey: getModuleStableKey(page),
    pageType: requirementPageType || pageTypeFromNode(page) || 'PAGE',
    state,
    score,
    statusText: statusText(state),
    prdCoverageText: requirement ? '已匹配' : '未匹配',
    pageSpecText: pageSpecExists ? versionText(input.pageSpecArtifact, '已生成') : '缺失',
    uiContractText: uiContractExists ? versionText(input.uiContractArtifact, '已生成') : '缺失',
    readyHint: uiContractExists ? '当前页面资产基本就绪，可继续预览、编辑或发布原型。' : '请先补齐阻断项，再生成交互原型。',
    blockers,
    warnings,
  }
}

function emptyDiagnostic(title: string, message: string): DiagnosticResult {
  return {
    pageId: '',
    pageName: title,
    pageKey: '',
    pageType: '',
    state: 'warning',
    score: 0,
    statusText: '待选择',
    prdCoverageText: '-',
    pageSpecText: '-',
    uiContractText: '-',
    readyHint: message,
    blockers: [],
    warnings: [message],
  }
}

function resolveOwnerModule(node: ModuleTreeNode | null): ModuleTreeNode | null {
  if (!node) return null
  if (resolveRequirementNodeType(node) === 'MODULE') return node

  const path = findNodePath(props.moduleTree || [], String(node.id || ''))
  for (let index = path.length - 1; index >= 0; index -= 1) {
    const candidate = path[index]
    if (resolveRequirementNodeType(candidate) === 'MODULE') {
      return candidate
    }
  }

  return null
}

function findNodePath(nodes: ModuleTreeNode[], nodeId: string, path: ModuleTreeNode[] = []): ModuleTreeNode[] {
  for (const node of nodes || []) {
    const next = [...path, node]
    if (String(node.id || '') === nodeId) {
      return next
    }
    const child = findNodePath(node.children || [], nodeId, next)
    if (child.length) return child
  }
  return []
}

function collectDescendantPages(root: ModuleTreeNode): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []
  const visit = (node: ModuleTreeNode) => {
    if (resolveRequirementNodeType(node) === 'PAGE') {
      result.push(node)
    }
    for (const child of node.children || []) {
      visit(child)
    }
  }
  visit(root)
  return result
}

function artifactExists(artifact: ArtifactLike): boolean {
  const record = recordOf(artifact)
  return Boolean(
    record?.artifactId ||
      record?.id ||
      record?.exists ||
      record?.content ||
      record?.contentMarkdown ||
      record?.content_markdown,
  )
}

function artifactContent(artifact: ArtifactLike): Record<string, any> {
  const record = recordOf(artifact)
  if (!record) return {}
  const content = recordOf(record.content)
  if (content) return content
  return {}
}

function artifactIdentity(artifact: ArtifactLike): string {
  const record = recordOf(artifact)
  return [
    record?.artifactId,
    record?.id,
    record?.versionNo,
    record?.updatedAt,
    record?.createdAt,
  ].filter(Boolean).join(':')
}

function pageRequirementList(content: Record<string, any>): Record<string, any>[] {
  const raw = content.page_requirements || content.pageRequirements || content.page_semantic_contracts || content.pageSemanticContracts
  return Array.isArray(raw)
    ? raw.filter((item): item is Record<string, any> => Boolean(recordOf(item)) && !Boolean((item as any).archived))
    : []
}

function matchPageRequirement(requirements: Record<string, any>[], page: ModuleTreeNode): Record<string, any> | null {
  const pageKeys = pageIdentityKeys(page)
  let best: { item: Record<string, any>; score: number } | null = null

  for (const item of requirements) {
    const keys = requirementIdentityKeys(item)
    let score = 0
    for (const key of pageKeys) {
      if (keys.includes(key)) {
        score += key.startsWith('id:') ? 100 : key.startsWith('route:') ? 60 : 40
      }
    }

    if (!best || score > best.score) {
      best = { item, score }
    }
  }

  return best && best.score > 0 ? best.item : null
}

function pageIdentityKeys(page: ModuleTreeNode): string[] {
  const extra = recordOf(page.extra) || {}
  return [
    ['id', page.id],
    ['key', page.moduleKey || (page as any).key || extra.pageKey || extra.page_key || extra.prdPageRequirementKey],
    ['name', getModuleDisplayName(page)],
    ['route', (page as any).routePath || extra.routePath || extra.route_path || extra.route],
  ].map(([prefix, value]) => identityKey(prefix, value)).filter(Boolean)
}

function requirementIdentityKeys(requirement: Record<string, any>): string[] {
  return [
    ['id', requirement.node_id || requirement.nodeId || requirement.page_node_id || requirement.pageNodeId],
    ['key', requirement.page_key || requirement.pageKey || requirement.key],
    ['name', requirement.page_name || requirement.pageName || requirement.name || requirement.title],
    ['route', requirement.route_path || requirement.routePath || requirement.route],
  ].map(([prefix, value]) => identityKey(prefix, value)).filter(Boolean)
}

function identityKey(prefix: unknown, value: unknown): string {
  const normalized = normalizeIdentity(value)
  return normalized ? `${prefix}:${normalized}` : ''
}

function normalizeIdentity(value: unknown): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-:/\\]+/g, '')
}

function pageTypeFromNode(page: ModuleTreeNode): string {
  const extra = recordOf(page.extra) || {}
  return normalizePageType(
    textOf((page as any).pageKind || (page as any).layoutType || extra.pageType || extra.page_type || extra.pageKind || extra.page_kind || page.moduleName || page.moduleKey),
  )
}

function normalizePageType(value: string): string {
  const text = String(value || '').trim().toUpperCase()
  const haystack = text + ' ' + text.toLowerCase()
  if (/APPROVAL|AUDIT|审核|审批/.test(haystack)) return 'APPROVAL'
  if (/DETAIL|VIEW|详情|查看/.test(haystack)) return 'DETAIL'
  if (/LIST|SEARCH|INDEX|TABLE|列表|查询|搜索|管理/.test(haystack)) return text.includes('SEARCH') || /查询|搜索/.test(haystack) ? 'SEARCH' : 'LIST'
  if (/FORM|CREATE|NEW|ADD|EDIT|UPDATE|创建|新建|新增|录入|编辑|修改|表单/.test(haystack)) return 'FORM'
  if (/DASHBOARD|WORKBENCH|看板|工作台|首页/.test(haystack)) return 'DASHBOARD'
  if (/CONFIG|SETTING|设置|配置/.test(haystack)) return 'CONFIG'
  if (/REPORT|报表/.test(haystack)) return 'REPORT'
  if (/PORTAL|门户/.test(haystack)) return 'PORTAL'
  if (/WIZARD|向导/.test(haystack)) return 'WIZARD'
  return text || 'PAGE'
}

function normalizeUiContract(artifact: ArtifactLike): Record<string, any> | null {
  const record = recordOf(artifact)
  if (!record) return null
  const candidates = [
    record.content,
    record.uiContract,
    record.contract,
    record,
  ]

  for (const candidate of candidates) {
    const normalized = normalizeUiContractValue(candidate)
    if (normalized) return normalized
  }

  return null
}

function normalizeUiContractValue(value: unknown): Record<string, any> | null {
  if (typeof value === 'string') {
    try {
      return normalizeUiContractValue(JSON.parse(value))
    } catch {
      return null
    }
  }

  const record = recordOf(value)
  if (!record) return null
  if (record.page && Array.isArray(recordOf(record.page)?.componentTree)) {
    return record
  }
  return normalizeUiContractValue(record.content || record.payload || record.snapshot)
}

function collectUiContractFootprint(contract: Record<string, any>): {
  componentTypes: Set<string>
  fieldLabels: string[]
  fieldKeys: string[]
  columnLabels: string[]
  columnKeys: string[]
  actionLabels: string[]
  actionIds: string[]
  visibleLabels: string[]
} {
  const componentTypes = new Set<string>()
  const fieldLabels: string[] = []
  const fieldKeys: string[] = []
  const columnLabels: string[] = []
  const columnKeys: string[] = []
  const actionLabels: string[] = []
  const actionIds: string[] = []
  const visibleLabels: string[] = []

  const push = (bucket: string[], value: unknown) => {
    const text = textOf(value)
    if (text && !bucket.includes(text)) bucket.push(text)
  }

  const visit = (value: unknown) => {
    if (Array.isArray(value)) {
      value.forEach(visit)
      return
    }
    const record = recordOf(value)
    if (!record) return

    push(visibleLabels, record.title || record.label || record.name || record.description)
    const type = textOf(record.type || record.componentType).toUpperCase()
    if (type) componentTypes.add(type)

    for (const field of arrayOf(record.fields)) {
      const item = recordOf(field)
      if (!item) continue
      push(fieldLabels, item.label || item.fieldName || item.name || item.title)
      push(fieldKeys, item.fieldKey || item.field_key || item.key || item.name)
      push(visibleLabels, item.label || item.fieldName || item.name || item.title)
    }

    for (const column of arrayOf(record.columns)) {
      const item = recordOf(column)
      if (!item) continue
      push(columnLabels, item.label || item.columnName || item.name || item.title)
      push(columnKeys, item.columnKey || item.column_key || item.fieldKey || item.key || item.name)
      push(visibleLabels, item.label || item.columnName || item.name || item.title)
    }

    for (const actionId of arrayOf(record.actionIds)) {
      push(actionIds, actionId)
    }
    const props = recordOf(record.props)
    for (const actionId of arrayOf(props?.rowActionIds)) {
      push(actionIds, actionId)
    }

    for (const child of arrayOf(record.children)) visit(child)
    for (const child of arrayOf(record.components)) visit(child)
    for (const child of arrayOf(record.items)) visit(child)
  }

  visit(recordOf(contract.page)?.componentTree || [])

  for (const interaction of arrayOf(contract.interactions)) {
    const item = recordOf(interaction)
    if (!item) continue
    push(actionLabels, item.label || item.title || item.name || item.actionName || item.actionId)
    push(actionIds, item.actionId || item.id || item.key)
    push(visibleLabels, item.label || item.title || item.name || item.actionName)
  }

  return {
    componentTypes,
    fieldLabels,
    fieldKeys,
    columnLabels,
    columnKeys,
    actionLabels,
    actionIds,
    visibleLabels,
  }
}

function normalizeStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map(textOf).filter(Boolean)
  }
  if (typeof value === 'string') {
    return value
      .split(/[、,，;；/\n]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  return []
}

function missingLabels(required: string[], labels: string[], keys: string[]): string[] {
  if (!required.length) return []
  const haystack = [...labels, ...keys].map(normalizeCompare).join('|')
  return required.filter((item) => {
    const key = normalizeCompare(item)
    return key && !haystack.includes(key)
  })
}

function hasAnyAction(footprint: ReturnType<typeof collectUiContractFootprint>, tokens: string[]): boolean {
  const text = [...footprint.actionLabels, ...footprint.actionIds, ...footprint.visibleLabels].join(' ')
  return tokens.some((token) => text.includes(token))
}

function scoreFrom(blockers: string[], warnings: string[]): number {
  return Math.max(0, 100 - blockers.length * 28 - warnings.length * 8)
}

function stateFrom(blockers: string[], warnings: string[]): DiagnosticState {
  if (blockers.length) return 'blocked'
  if (warnings.length) return 'warning'
  return 'ready'
}

function statusText(state: DiagnosticState): string {
  if (state === 'ready') return '可推进'
  if (state === 'warning') return '需复核'
  return '有阻断'
}

function versionText(artifact: ArtifactLike, fallback: string): string {
  const record = recordOf(artifact)
  const version = record?.versionNo || record?.version_no || record?.artifactVersionNo
  return version ? `V${version}` : fallback
}

function textOf(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') {
    const record = recordOf(value)
    return textOf(record?.label || record?.name || record?.title || record?.fieldName || record?.columnName || record?.actionName || record?.key || '')
  }
  return String(value || '').trim()
}

function recordOf(value: unknown): Record<string, any> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : null
}

function arrayOf(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function normalizeCompare(value: unknown): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/^(按|以|根据|和|及|以及|或|并|包含|包括|支持|提供)+/, '')
    .replace(/(字段|按钮|操作|入口|查询|筛选|检索|进行)$/g, '')
    .replace(/[\s_\-:/\\，,。；;（）()]+/g, '')
}
</script>

<style scoped>
.a91536-panel {
  display: grid;
  gap: 14px;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.a91536-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.a91536-header p {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.a91536-header h4 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.a91536-header span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.a91536-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.a91536-primary,
.a91536-secondary {
  border: 0;
  border-radius: 11px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.a91536-primary {
  background: #2563eb;
  color: #fff;
}

.a91536-secondary {
  background: #eef2ff;
  color: #1e3a8a;
}

.a91536-primary:disabled,
.a91536-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.a91536-score-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.a91536-score-row article {
  display: grid;
  gap: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 11px 12px;
  background: #fff;
}

.a91536-score-row strong {
  color: #0f172a;
  font-size: 16px;
}

.a91536-score-row span {
  color: #64748b;
  font-size: 12px;
}

.a91536-score-card.state-ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.a91536-score-card.state-warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.a91536-score-card.state-blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.a91536-message-list {
  display: grid;
  gap: 8px;
  border-radius: 14px;
  padding: 12px 13px;
  font-size: 13px;
}

.a91536-message-list strong {
  color: #0f172a;
}

.a91536-message-list ul {
  margin: 0;
  padding-left: 18px;
}

.a91536-message-list li {
  margin: 4px 0;
  line-height: 1.55;
}

.a91536-message-list.blocked {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.a91536-message-list.warning {
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.a91536-message-list.ready {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.a91536-batch {
  display: grid;
  gap: 10px;
  border-top: 1px dashed #cbd5e1;
  padding-top: 12px;
}

.a91536-batch-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.a91536-batch-head div {
  display: grid;
  gap: 4px;
}

.a91536-batch-head strong {
  color: #0f172a;
}

.a91536-batch-head span {
  color: #64748b;
  font-size: 13px;
}

.a91536-batch-table {
  display: grid;
  gap: 8px;
}

.a91536-batch-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.4fr) 84px 92px 112px minmax(160px, 1fr);
  gap: 10px;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 13px;
  padding: 10px 12px;
  background: #fff;
}

.a91536-batch-row.state-ready {
  border-left: 4px solid #22c55e;
}

.a91536-batch-row.state-warning {
  border-left: 4px solid #f59e0b;
}

.a91536-batch-row.state-blocked {
  border-left: 4px solid #ef4444;
}

.a91536-batch-row div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.a91536-batch-row strong {
  color: #0f172a;
  font-size: 13px;
}

.a91536-batch-row span,
.a91536-batch-row small {
  color: #64748b;
  font-size: 12px;
}

.a91536-batch-row em {
  color: #0f172a;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

@media (max-width: 1180px) {
  .a91536-score-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .a91536-batch-row {
    grid-template-columns: 1fr;
  }
}
</style>
