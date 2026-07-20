import type {
  ArtifactResponse,
  ModuleTreeNode,
} from '../types/codemind'
import type { ConfirmedUiContractSnapshot } from '../types/uiContractSnapshot'
import {
  getRequirementArtifactTitle,
  getUpstreamArtifactTypes,
  type RequirementArtifactType,
} from './requirementArtifactDefinitions'
import {
  getAdvancedRequirementArtifactTypesForNode,
  getCoreRequirementArtifactTypesForNode,
  getRequirementArtifactTitleForNode,
  getRequirementNodeTypeLabel,
  resolveRequirementNodeType,
  type RequirementNodeType,
} from './requirementNodePolicy'

export type ArtifactQualityLevel = 'pass' | 'warning' | 'blocker'
export type ArtifactQualityImportance = 'CORE' | 'ADVANCED' | 'STRUCTURE'

export interface ArtifactQualityAssetItem {
  artifactType: RequirementArtifactType
  title: string
  exists: boolean
  versionNo?: number | null
  status?: string | null
  current?: boolean | null
  updatedAt?: string | null
  level: ArtifactQualityLevel
  statusText: string
  importance: ArtifactQualityImportance
  importanceText: string
}

export interface ArtifactQualityIssue {
  id: string
  level: ArtifactQualityLevel
  title: string
  description: string
  artifactType?: RequirementArtifactType
}

export interface ArtifactQualityCheckResult {
  score: number
  generatedAt: string
  nodeType: RequirementNodeType
  nodeTypeLabel: string
  checkScopeTitle: string
  checkPurpose: string
  checkStrategy: string
  readinessLevel: 'BLOCKED' | 'WARNING' | 'READY'
  readinessText: string
  readinessDescription: string
  coreArtifactTypes: RequirementArtifactType[]
  advancedArtifactTypes: RequirementArtifactType[]
  assets: ArtifactQualityAssetItem[]
  blockers: ArtifactQualityIssue[]
  warnings: ArtifactQualityIssue[]
  passes: ArtifactQualityIssue[]
}

export interface BuildArtifactQualityGateOptions {
  node: ModuleTreeNode | null
  getArtifactByType: (artifactType: RequirementArtifactType) => ArtifactResponse | null | undefined
  getArtifactMarkdownByType: (artifactType: RequirementArtifactType) => string
  confirmedUiContractSnapshot?: ConfirmedUiContractSnapshot | null
  generatedAt?: string
}

export function buildArtifactQualityGate(
  options: BuildArtifactQualityGateOptions,
): ArtifactQualityCheckResult {
  const nodeType = resolveRequirementNodeType(options.node)
  const nodeTypeLabel = getRequirementNodeTypeLabel(nodeType)
  const coreArtifactTypes = uniqueArtifactTypes(
    getCoreRequirementArtifactTypesForNode(options.node),
  )
  const advancedArtifactTypes = uniqueArtifactTypes(
    getAdvancedRequirementArtifactTypesForNode(options.node)
      .filter((artifactType) => !coreArtifactTypes.includes(artifactType)),
  )
  const checkedArtifactTypes = [
    ...coreArtifactTypes,
    ...advancedArtifactTypes,
  ]

  const assets: ArtifactQualityAssetItem[] = []
  const blockers: ArtifactQualityIssue[] = []
  const warnings: ArtifactQualityIssue[] = []
  const passes: ArtifactQualityIssue[] = []
  const artifactByType = new Map<RequirementArtifactType, ArtifactResponse | null>()

  for (const artifactType of checkedArtifactTypes) {
    const artifact = options.getArtifactByType(artifactType) || null
    artifactByType.set(artifactType, artifact)

    const importance: ArtifactQualityImportance = coreArtifactTypes.includes(artifactType)
      ? 'CORE'
      : 'ADVANCED'
    const title = getRequirementArtifactTitleForNode(
      artifactType,
      options.node,
    )
    const markdown = options.getArtifactMarkdownByType(artifactType)
    const exists = hasArtifact(artifact, markdown)
    const contentOk = hasMeaningfulArtifactContent(artifact, markdown)
    const status = artifact?.status || null

    let level: ArtifactQualityLevel = 'pass'
    let statusText = '已生成'

    if (!exists) {
      level = importance === 'CORE' ? 'blocker' : 'warning'
      statusText = '缺失'
    } else if (!contentOk) {
      level = importance === 'CORE' ? 'blocker' : 'warning'
      statusText = '内容为空'
    } else if (status === 'REJECTED') {
      level = importance === 'CORE' ? 'blocker' : 'warning'
      statusText = '已退回'
    } else if (
      status === 'DRAFT' ||
      status === 'ACTIVE' ||
      status === 'PENDING_REVIEW'
    ) {
      level = 'warning'
      statusText = status === 'PENDING_REVIEW' ? '待审核' : '未确认'
    } else if (status === 'APPROVED') {
      level = 'pass'
      statusText = '已确认'
    } else {
      level = exists ? 'warning' : level
      statusText = status || statusText
    }

    if (artifact?.current === false && level !== 'blocker') {
      level = 'warning'
      statusText = '非当前版本'
    }

    const asset: ArtifactQualityAssetItem = {
      artifactType,
      title,
      exists,
      versionNo: artifact?.versionNo || null,
      status,
      current: artifact?.current,
      updatedAt: artifact?.updatedAt || null,
      level,
      statusText,
      importance,
      importanceText: importance === 'CORE' ? '核心' : '高级',
    }

    assets.push(asset)

    const issue = buildIssueForAsset(
      asset,
      importance,
      options.node,
    )

    if (issue) {
      pushIssue(issue, blockers, warnings, passes)
    } else {
      passes.push({
        id: `pass-${artifactType}`,
        level: 'pass',
        title: `${title}已生成`,
        description: `${title}存在有效内容，可以进入当前${nodeTypeLabel}层级检查。`,
        artifactType,
      })
    }
  }

  appendNodeStructureChecks(
    options.node,
    nodeType,
    blockers,
    warnings,
    passes,
  )

  appendUiContractSnapshotChecks(
    options,
    artifactByType,
    blockers,
    warnings,
    passes,
  )

  appendStaleChecks(
    checkedArtifactTypes,
    artifactByType,
    options.node,
    warnings,
  )

  const score = Math.max(
    0,
    100 - blockers.length * 18 - warnings.length * 6,
  )

  const readinessLevel =
    blockers.length > 0
      ? 'BLOCKED'
      : warnings.length > 0
        ? 'WARNING'
        : 'READY'

  return {
    score,
    generatedAt: options.generatedAt || new Date().toLocaleString(),
    nodeType,
    nodeTypeLabel,
    checkScopeTitle: `${nodeTypeLabel}级质量检查`,
    checkPurpose: getCheckPurpose(nodeType),
    checkStrategy: getCheckStrategy(nodeType),
    readinessLevel,
    readinessText: getReadinessText(readinessLevel),
    readinessDescription: getReadinessDescription(readinessLevel),
    coreArtifactTypes,
    advancedArtifactTypes,
    assets,
    blockers,
    warnings,
    passes,
  }
}

function uniqueArtifactTypes(
  items: RequirementArtifactType[],
): RequirementArtifactType[] {
  return Array.from(new Set(items))
}

function hasArtifact(
  artifact: ArtifactResponse | null | undefined,
  markdown: string,
): boolean {
  return Boolean(
    artifact?.artifactId ||
      artifact?.exists ||
      artifact?.content ||
      artifact?.contentMarkdown ||
      markdown,
  )
}

function hasMeaningfulArtifactContent(
  artifact: ArtifactResponse | null | undefined,
  fallbackMarkdown: string,
): boolean {
  const markdown = artifact?.contentMarkdown || fallbackMarkdown

  if (markdown && markdown.trim().length > 20) {
    const normalizedMarkdown = markdown.trim()

    return !(
      normalizedMarkdown.includes('当前模块尚未生成') ||
      normalizedMarkdown.includes('暂无内容') ||
      normalizedMarkdown.includes('TODO') ||
      normalizedMarkdown.includes('占位')
    )
  }

  if (!artifact?.content) {
    return false
  }

  try {
    const contentText = JSON.stringify(artifact.content)

    return contentText.length > 20 &&
      contentText !== '{}' &&
      !contentText.includes('当前模块尚未生成') &&
      !contentText.includes('暂无内容') &&
      !contentText.includes('TODO') &&
      !contentText.includes('占位')
  } catch {
    return false
  }
}

function buildIssueForAsset(
  asset: ArtifactQualityAssetItem,
  importance: ArtifactQualityImportance,
  node?: ModuleTreeNode | null,
): ArtifactQualityIssue | null {
  const title = asset.title
  const artifactTitle = getRequirementArtifactTitleForNode(
    asset.artifactType,
    node,
  )
  const isCore = importance === 'CORE'

  if (!asset.exists) {
    return {
      id: `missing-${asset.artifactType}`,
      level: isCore ? 'blocker' : 'warning',
      title: `${title}缺失`,
      description: isCore
        ? `当前节点的核心资产尚未生成${artifactTitle}，会影响该层级正式交付。`
        : `${artifactTitle}属于当前层级高级资产，未生成不会阻断基础交付，但进入正式开发前建议补齐。`,
      artifactType: asset.artifactType,
    }
  }

  if (asset.status === 'REJECTED') {
    return {
      id: `rejected-${asset.artifactType}`,
      level: isCore ? 'blocker' : 'warning',
      title: `${title}已退回`,
      description: `${artifactTitle}当前处于已退回状态，建议修改并重新提交审核。`,
      artifactType: asset.artifactType,
    }
  }

  if (asset.level === 'blocker') {
    return {
      id: `empty-${asset.artifactType}`,
      level: 'blocker',
      title: `${title}内容不可用`,
      description: `${artifactTitle}存在空内容或占位内容，无法作为正式交付依据。`,
      artifactType: asset.artifactType,
    }
  }

  if (asset.level === 'warning') {
    return {
      id: `status-${asset.artifactType}`,
      level: 'warning',
      title: `${title}尚未确认`,
      description: `${artifactTitle}当前状态为“${asset.statusText}”，建议确认通过后再交付。`,
      artifactType: asset.artifactType,
    }
  }

  return null
}

function pushIssue(
  issue: ArtifactQualityIssue,
  blockers: ArtifactQualityIssue[],
  warnings: ArtifactQualityIssue[],
  passes: ArtifactQualityIssue[],
) {
  if (issue.level === 'blocker') {
    blockers.push(issue)
    return
  }

  if (issue.level === 'warning') {
    warnings.push(issue)
    return
  }

  passes.push(issue)
}

function appendNodeStructureChecks(
  node: ModuleTreeNode | null,
  nodeType: RequirementNodeType,
  blockers: ArtifactQualityIssue[],
  warnings: ArtifactQualityIssue[],
  passes: ArtifactQualityIssue[],
) {
  if (!node) {
    blockers.push({
      id: 'missing-selected-node',
      level: 'blocker',
      title: '未选择模块树节点',
      description: '请先选择一个业务域、模块、子模块、页面或功能点节点。',
    })
    return
  }

  const children = Array.isArray(node.children) ? node.children : []

  if (nodeType === 'DOMAIN') {
    if (!children.length) {
      blockers.push({
        id: 'domain-empty-children',
        level: 'blocker',
        title: '业务域下没有模块',
        description: '业务域节点至少应包含一个模块或子模块，否则无法形成可交付的业务范围。',
      })
      return
    }

    passes.push({
      id: 'domain-children-pass',
      level: 'pass',
      title: '业务域结构可检查',
      description: `当前业务域下包含 ${children.length} 个下级节点，可进行业务域级交付检查。`,
    })
    return
  }

  if (nodeType === 'MODULE') {
    if (!children.length) {
      warnings.push({
        id: 'module-empty-children',
        level: 'warning',
        title: '模块下暂无页面或子模块',
        description: '模块层可以先形成规划，但正式交付前建议补齐页面或子模块结构。',
      })
      return
    }

    passes.push({
      id: 'module-children-pass',
      level: 'pass',
      title: '模块结构可检查',
      description: `当前模块下包含 ${children.length} 个下级节点，可进行模块级交付检查。`,
    })
    return
  }

  if (nodeType === 'PAGE') {
    if (!children.length) {
      warnings.push({
        id: 'page-no-feature-children',
        level: 'warning',
        title: '页面下暂无功能点',
        description: '页面可以直接生成交互原型，但进入正式开发前建议补充按钮、表单提交、跳转等功能点节点。',
      })
      return
    }

    passes.push({
      id: 'page-feature-children-pass',
      level: 'pass',
      title: '页面功能点结构可检查',
      description: `当前页面下包含 ${children.length} 个功能点或下级节点，可辅助 UI_CONTRACT 和开发交接。`,
    })
    return
  }

  if (nodeType === 'FEATURE') {
    if (!node.parentId) {
      warnings.push({
        id: 'feature-parent-missing',
        level: 'warning',
        title: '功能点缺少父页面信息',
        description: '功能点应挂载在 PAGE 页面节点下，便于后续把动作映射到页面组件和接口。',
      })
      return
    }

    passes.push({
      id: 'feature-parent-pass',
      level: 'pass',
      title: '功能点结构可检查',
      description: '当前功能点存在父节点信息，可作为页面局部动作进行质量检查。',
    })
  }
}

function appendUiContractSnapshotChecks(
  options: BuildArtifactQualityGateOptions,
  artifactByType: Map<RequirementArtifactType, ArtifactResponse | null>,
  blockers: ArtifactQualityIssue[],
  warnings: ArtifactQualityIssue[],
  passes: ArtifactQualityIssue[],
) {
  const nodeType = resolveRequirementNodeType(options.node)

  if (nodeType !== 'PAGE') {
    return
  }

  const uiContract = artifactByType.get('UI_CONTRACT')

  if (!uiContract?.artifactId) {
    return
  }

  const snapshot = options.confirmedUiContractSnapshot

  if (!snapshot) {
    blockers.push({
      id: 'ui-contract-not-confirmed',
      level: 'blocker',
      title: '交互原型尚未确认',
      description: '当前页面已有 UI_CONTRACT，但尚未保存用户确认快照。后续 Development Agent 不应直接使用未确认页面蓝图。',
      artifactType: 'UI_CONTRACT',
    })
    return
  }

  if (snapshot.artifactId !== uiContract.artifactId) {
    blockers.push({
      id: 'ui-contract-confirmation-stale',
      level: 'blocker',
      title: '交互原型确认快照已滞后',
      description: '用户确认的 UI_CONTRACT 版本不是当前最新版本。请重新预览并确认页面效果。',
      artifactType: 'UI_CONTRACT',
    })
    return
  }

  passes.push({
    id: 'ui-contract-confirmed-pass',
    level: 'pass',
    title: '交互原型已确认',
    description: `当前页面 UI_CONTRACT 已确认，可作为后续 Development Agent 的页面蓝图。确认版本：v${snapshot.artifactVersionNo || '-'}`,
    artifactType: 'UI_CONTRACT',
  })
}

function appendStaleChecks(
  checkedArtifactTypes: RequirementArtifactType[],
  artifactByType: Map<RequirementArtifactType, ArtifactResponse | null>,
  node: ModuleTreeNode | null,
  warnings: ArtifactQualityIssue[],
) {
  for (const artifactType of checkedArtifactTypes) {
    const artifact = artifactByType.get(artifactType)

    if (!artifact?.updatedAt) {
      continue
    }

    for (const upstreamType of getUpstreamArtifactTypes(artifactType)) {
      if (!checkedArtifactTypes.includes(upstreamType)) {
        continue
      }

      const upstreamArtifact = artifactByType.get(upstreamType)

      if (!upstreamArtifact?.updatedAt) {
        continue
      }

      const currentUpdatedAt = Date.parse(artifact.updatedAt)
      const upstreamUpdatedAt = Date.parse(upstreamArtifact.updatedAt)

      if (
        Number.isFinite(currentUpdatedAt) &&
        Number.isFinite(upstreamUpdatedAt) &&
        upstreamUpdatedAt > currentUpdatedAt
      ) {
        warnings.push({
          id: `stale-${artifactType}-${upstreamType}`,
          level: 'warning',
          title: `${getRequirementArtifactTitleForNode(artifactType, node)}可能已滞后`,
          description:
            `${getRequirementArtifactTitle(upstreamType)}的更新时间晚于` +
            `${getRequirementArtifactTitleForNode(artifactType, node)}，建议重新生成以保持链路一致。`,
          artifactType,
        })
      }
    }
  }
}

function getCheckPurpose(
  nodeType: RequirementNodeType,
): string {
  switch (nodeType) {
    case 'DOMAIN':
      return '检查业务域边界、模块组成、跨模块流程和高层交付完整性。'
    case 'MODULE':
      return '检查模块级 PRD、流程、页面地图、接口规划和开发交接准备度。'
    case 'SUBMODULE':
      return '检查业务场景需求、场景流程、页面清单和上下游约束是否可交付。'
    case 'PAGE':
      return '检查页面规范、页面接口、交互原型和用户确认快照是否足以支撑开发 Agent。'
    case 'FEATURE':
      return '检查功能点需求、动作接口、测试点和父页面挂载关系是否清晰。'
    default:
      return '请选择模块树节点后进行层级化质量检查。'
  }
}

function getCheckStrategy(
  nodeType: RequirementNodeType,
): string {
  switch (nodeType) {
    case 'DOMAIN':
      return '业务域层只把业务域概览、业务域流程总览和模块/页面地图作为核心检查项。'
    case 'MODULE':
      return '模块层把模块 PRD、模块业务流程、页面地图和接口规划作为核心检查项。'
    case 'SUBMODULE':
      return '子模块层把场景需求、场景业务流程和场景页面清单作为核心检查项。'
    case 'PAGE':
      return '页面层把页面需求、页面规范、页面接口契约和交互原型作为核心检查项，并要求 UI_CONTRACT 已确认。'
    case 'FEATURE':
      return '功能点层只检查局部功能需求、接口动作和测试点，不要求完整页面规范或完整交互原型。'
    default:
      return '未知节点只进行基础检查。'
  }
}

function getReadinessText(
  level: ArtifactQualityCheckResult['readinessLevel'],
): string {
  if (level === 'BLOCKED') {
    return '暂不建议交付'
  }

  if (level === 'WARNING') {
    return '可继续推进，但建议先处理警告'
  }

  return '满足当前层级交付条件'
}

function getReadinessDescription(
  level: ArtifactQualityCheckResult['readinessLevel'],
): string {
  if (level === 'BLOCKED') {
    return '当前存在核心资产缺失、内容不可用、确认快照缺失或结构不完整等阻断项，建议修复后再导出交付包。'
  }

  if (level === 'WARNING') {
    return '当前没有核心阻断项，但存在未确认、高级资产缺失或资产链路可能滞后的情况。'
  }

  return '当前节点的核心资产、结构关系和交付链路状态良好，可以进入交付或后续开发准备。'
}
