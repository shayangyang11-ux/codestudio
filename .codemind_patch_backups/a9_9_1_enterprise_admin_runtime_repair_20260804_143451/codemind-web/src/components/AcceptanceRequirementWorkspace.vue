<template>
  <main class="a9-workspace">
    <section class="a9-hero-card">
      <div class="a9-hero-main">
        <p class="a9-eyebrow">Requirement Agent · 从需求骨架到交互原型</p>
        <h2>需求建模工作区</h2>
        <p>
          先从业务描述生成需求骨架；确认模块边界后，再逐步细化功能点、页面原型和开发交付材料。
        </p>
      </div>

      <div class="a9-hero-metrics">
        <article>
          <strong>{{ selectedKindLabel }}</strong>
          <span>当前节点</span>
        </article>
        <article>
          <strong>{{ childSummary }}</strong>
          <span>下级节点</span>
        </article>
        <article>
          <strong>后台准备</strong>
          <span>原型状态</span>
        </article>
      </div>
    </section>

    <section v-if="!selectedModule && !hasRequirementTree" class="a9-empty-card a9-empty-project-card">
      <div class="a9-empty-project-hero">
        <p class="a9-empty-eyebrow">Start a new project</p>
        <h3>先告诉我你要做什么系统</h3>
        <p>
          在右侧输入业务目标、用户角色、核心流程，或上传需求材料。系统会先判断需求粒度，
          再让你选择“先搭模块骨架”还是“生成完整项目蓝图”。
        </p>
      </div>

      <div class="a9-start-option-grid">
        <article>
          <span>推荐</span>
          <strong>先生成模块骨架</strong>
          <p>适合只有一句话或初步想法的项目。先得到业务模块层级，后续可继续拆分、合并或定稿某个模块。</p>
        </article>

        <article>
          <span>资料较完整</span>
          <strong>生成完整项目蓝图</strong>
          <p>适合已经明确角色、流程和页面范围的项目。系统会生成模块、功能点和页面入口，便于快速进入原型验证。</p>
        </article>

        <article>
          <span>手动补充</span>
          <strong>也可以从左侧新增模块</strong>
          <p>点击左侧标题旁的“+”，可以手动创建第一个模块；之后再让 AI 继续补全或调整结构。</p>
        </article>
      </div>

      <div class="a9-empty-example-card">
        <strong>可以这样开始</strong>
        <span>我要做一个智能仓储管理系统，包含入库、出库、库存预警、供应商和仓库管理。</span>
      </div>
    </section>

    <section v-else-if="!selectedModule" class="a9-empty-card a9-empty-selection-card">
      <h3>请选择左侧的需求节点</h3>
      <p>选择一个模块、功能点或页面后，可以继续完善需求、生成页面原型，或检查后续开发准备状态。</p>
    </section>

    <section v-else class="a9-node-card">
      <header class="a9-node-header">
        <div>
          <span class="a9-kind-pill">{{ selectedKindLabel }}</span>
          <h3>{{ nodeName }}</h3>
          <p>Key：{{ nodeKey || '-' }}</p>
        </div>

        <div class="a9-node-actions">
          <button
            v-if="showModuleRequirementAction"
            type="button"
            class="a9-primary-button"
            :disabled="isGeneratingFullChain"
            @click="emit('generate-full-chain')"
          >
            {{ isGeneratingFullChain ? '后台准备中...' : moduleRequirementButtonText }}
          </button>

          <button
            v-if="isPage"
            type="button"
            class="a9-primary-button"
            :disabled="isGeneratingUiContract"
            @click="emit('generate-ui-contract')"
          >
            {{ isGeneratingUiContract ? '正在生成原型...' : prototypeButtonText }}
          </button>


        </div>
      </header>

      <section v-if="showModuleRequirementDocument" class="a9-section-card a9-module-document-card">
        <div class="a9-module-document-heading">
          <div>
            <h4>模块需求</h4>
            <p>
              这份需求用于确认模块边界、核心功能和页面方向；字段、按钮和页面细节会在交互原型里继续确认。
            </p>
          </div>
          <span
            v-if="currentModuleRequirementArtifact?.versionNo"
            class="a9-module-doc-version"
          >
            V{{ currentModuleRequirementArtifact.versionNo }}
          </span>
        </div>

        <ModulePrdView
          :artifact="moduleRequirementReaderArtifact"
          :markdown="moduleRequirementMarkdownForReader"
          :manual-edit-saving="manualEditSaving"
          :allow-create-from-markdown="true"
          @save-manual-edit="emit('save-manual-artifact-edit', $event)"
        />
      </section>

      <section v-if="isModule" class="a9-two-column">
        <article class="a9-info-card">
          <h4>当前定位</h4>
          <p>{{ modulePositionText }}</p>
        </article>
        <article class="a9-info-card">
          <h4>下一步建议</h4>
          <p>{{ moduleNextStepText }}</p>
        </article>
      </section>

      <section v-if="isFeature" class="a9-section-card">
        <h4>功能点需求</h4>
        <p>{{ featureDescription }}</p>
        <div class="a9-child-strip">
          <strong>{{ pageChildren.length }}</strong>
          <span>个页面承载该功能点</span>
        </div>
      </section>

      <section v-if="isPage" class="a9-section-card a9-page-prototype-card">
        <div class="a9-section-title-row">
          <div>
            <h4>交互原型</h4>
            <p>生成后可进入原型工作台查看、点击跳转，并直接选择按钮、字段、表格列或卡片进行修改。</p>
          </div>
          <span :class="hasPrototype ? 'a9-status-ready' : 'a9-status-waiting'">
            {{ hasPrototype ? '已生成' : '未生成' }}
          </span>
        </div>

        <div v-if="prototypeInspectMode" class="a9-prototype-inspect-banner">
          <strong>元素选择模式已开启</strong>
          <span>把鼠标移到原型元素上会出现高亮框；默认优先选中最小的字段、按钮、输入框、表格列。按住 Alt 或 Shift 单击可选择外层卡片、表单或区域；选中后可直接在 AI Assistant 里说“把这个按钮改成蓝色 / 文案改成提交”。</span>
        </div>

                <div v-if="focusedComponentId" class="a9-prototype-locked-target-card">
          <div>
            <strong>已锁定页面元素</strong>
            <span>{{ focusedComponentDisplayLabel }}</span>
            <em>AI Assistant 会优先修改这个元素。需要换目标时，点击“重新选择元素”。</em>
          </div>
          <button type="button" @click="openPrototypeStudioInspect">
            重新选择元素
          </button>
        </div>



        <div
          v-if="focusedComponentId && prototypeQuickEditActions.length"
          class="a9-prototype-quick-edit-card"
        >
          <div class="a9-prototype-quick-edit-head">
            <strong>快捷编辑</strong>
            <span>选中元素后可直接点一个常用修改，系统会把它转成 AI 原型编辑指令。</span>
          </div>

          <div class="a9-prototype-quick-edit-actions">
            <button
              v-for="action in prototypeQuickEditActions"
              :key="action.key"
              type="button"
              @click="handlePrototypeQuickEditSuggestion(action)"
            >
              {{ action.label }}
            </button>
          </div>
        </div>

<div v-if="prototypeEditDraftDirty" class="a9-prototype-draft-banner">
          <div class="a9-prototype-draft-content">
            <strong>原型调整预览中</strong>
            <span>{{ prototypeEditDraftSummary || 'AI 已在本地更新当前原型，确认无误后可保存为新版本。' }}</span>
          </div>

          <div class="a9-prototype-draft-actions">
            <button type="button" class="a9-secondary-button compact" @click="emit('discard-prototype-draft')">
              放弃
            </button>
            <button type="button" class="a9-primary-button compact" @click="openA9PrototypeSaveConfirm">
              保存为新版本
            </button>
          </div>
        </div>

        

        <div
          v-if="prototypeEditDraftDirty && prototypeDraftEditEntries.length"
          class="a9-prototype-edit-explain-card"
        >
          <div class="a9-prototype-edit-explain-head">
            <div>
              <strong>待保存修改明细</strong>
              <span>
                共 {{ prototypeDraftEditTotalCount }} 次原型调整；这些变更当前只在预览中生效，点击“保存为新版本”后才会进入 交互原型 版本链路。
              </span>
            </div>
            <em>未保存</em>
          </div>

          <ol class="a9-prototype-edit-explain-list">
            <li
              v-for="entry in prototypeDraftEditEntries"
              :key="entry.key"
            >
              <div class="a9-prototype-edit-index">
                {{ entry.indexLabel }}
              </div>
              <div class="a9-prototype-edit-body">
                <div class="a9-prototype-edit-title-row">
                  <strong>{{ entry.targetLabel }}</strong>
                  <span>{{ entry.timeLabel }}</span>
                </div>
                <p>{{ entry.instruction }}</p>
                <div class="a9-prototype-edit-tags">
                  <span
                    v-for="change in entry.changes"
                    :key="change"
                  >
                    {{ change }}
                  </span>
                </div>
              </div>
            </li>
          </ol>
        </div>

<div v-if="hasPrototype" class="a9-prototype-ready">
          <strong>交互原型已生成</strong>
          <span v-if="focusedComponentId">
            已锁定页面元素：{{ focusedComponentDisplayLabel }}。AI Assistant 会优先修改这个元素。
          </span>
          <span v-else>
            点击“进入原型工作台”后，会以正常桌面页面比例展示原型；在工作台里可选择按钮、输入框、字段、表格列和卡片，并用 Prototype Copilot 修改。
          </span>
          <div class="a9-prototype-meta">
            <span>原型版本已就绪</span>
            <span v-if="prototypeEditDraftDirty">本地预览未保存</span>
            <span>{{ prototypeSizeLabel }}</span>
            <span>{{ prototypeComponentCount }} 个组件</span>
          </div>
        </div>

        <div v-else class="a9-prototype-empty">
          <strong>还没有交互原型</strong>
          <span>点击“生成交互原型”，系统会根据页面需求生成可预览、可交互、可继续调整的原型。</span>
        </div>


        <section v-if="isPage && hasPrototype" class="a9-prototype-version-status-panel">
          <article
            v-for="item in prototypeLifecycleStatusItems"
            :key="item.key"
            class="a9-prototype-version-status-item"
            :class="`state-${item.state}`"
          >
            <div>
              <span>{{ item.eyebrow }}</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <em>{{ item.badge }}</em>
          </article>
        </section>

        <div v-if="hasPrototype && uiContractObject" class="a9-prototype-studio-entry-card">
          <div class="a9-prototype-studio-entry-main">
            <p>Prototype Studio</p>
            <strong>以正常页面比例查看和编辑原型</strong>
            <span>
              原型不再挤在中间工作区里；进入后使用 1440px 真实桌面视口；工作台只负责预览缩放，后续生成代码仍按正常网页比例运行。
            </span>
          </div>

          <div class="a9-prototype-studio-entry-actions">
            <button type="button" class="a9-primary-button compact" @click="openPrototypeStudio">
              进入原型工作台
            </button>
          </div>
        </div>

        <PrototypeStudioOverlay
          v-if="uiContractObject"
          :visible="prototypeStudioVisible && hasPrototype"
          :contract="uiContractObject"
          :page-title="nodeName"
          :project-theme-profile="projectThemeProfile || null"
          :focused-component-id="focusedComponentId || null"
          :focused-component-label="focusedComponentDisplayLabel"
          :focused-element-selection="focusedPrototypeElementSelection || focusedElementSelection || null"
          :prototype-edit-draft-dirty="prototypeEditDraftDirty"
          :prototype-edit-draft-summary="prototypeEditDraftSummary"
          :prototype-size-label="prototypeSizeLabel"
          :prototype-component-count="prototypeComponentCount"
          :saving="manualEditSaving"
          :start-in-inspect-mode="prototypeStudioStartInspectMode"
          @close="closePrototypeStudio"
          @select-component="handlePrototypeComponentSelect"
          @clear-selection="handlePrototypeSelectionClear"
          @send-instruction="handlePrototypeStudioInstruction"
          @save-draft="handlePrototypeStudioSaveDraft"
          @discard-draft="emit('discard-prototype-draft')"
        />

        <div v-else-if="hasPrototype" class="a9-prototype-empty warning">
          <strong>原型数据暂不可直接渲染</strong>
          <span>当前 交互原型 已存在，但内容结构不满足运行时预览要求。可点击完整预览查看原始契约，或重新生成交互原型。</span>
        </div>
      </section>

      <details class="a9-backstage-details">
        <summary>后台工程资产状态</summary>
        <div class="a9-backstage-grid">
          <article>
            <strong>模块 PRD / 页面地图 / 流程</strong>
            <span>后台用于功能点、页面和原型生成。</span>
          </article>
          <article>
            <strong>数据模型 / 接口契约 / 测试规格</strong>
            <span>后台用于后续开发 Agent。</span>
          </article>
          <article>
            <strong>开发交接包</strong>
            <span>用户不需要手动维护，系统会在关键操作后准备。</span>
          </article>
        </div>
      </details>
    </section>


    <div
      v-if="a9PrototypeSaveConfirmVisible"
      class="a9-prototype-save-confirm-mask"
      role="dialog"
      aria-modal="true"
      aria-label="保存原型修改确认"
      @click.self="cancelA9PrototypeSaveConfirm"
    >
      <article class="a9-prototype-save-confirm-dialog">
        <header class="a9-prototype-save-confirm-header">
          <div>
            <p>Save 交互原型 Version</p>
            <h3>确认保存原型修改？</h3>
            <span>
              本次会把当前 PAGE 的本地原型草稿保存为新的交互原型版本，保存后可继续进入确认、交接和开发链路。
            </span>
          </div>
          <button type="button" aria-label="关闭" @click="cancelA9PrototypeSaveConfirm">×</button>
        </header>

        <section class="a9-prototype-save-confirm-summary">
          <article>
            <strong>{{ a9PrototypeSavePageTitle }}</strong>
            <span>目标页面</span>
          </article>
          <article>
            <strong>{{ a9PrototypeSaveChangeCount }}</strong>
            <span>待保存修改</span>
          </article>
          <article>
            <strong>未保存</strong>
            <span>当前状态</span>
          </article>
        </section>

        <section class="a9-prototype-save-confirm-body">
          <h4>保存前确认</h4>
          <p>
            保存后会生成新的交互原型版本，并可能使后续开发交接包、页面实现和相关确认快照需要重新同步。
          </p>

          <div v-if="a9PrototypeSaveEditRecords.length" class="a9-prototype-save-edit-list">
            <article
              v-for="record in a9PrototypeSaveEditRecords"
              :key="record.key"
            >
              <div>
                <strong>{{ record.targetLabel }}</strong>
                <span>{{ record.instruction }}</span>
              </div>
              <ul>
                <li
                  v-for="change in record.changes"
                  :key="change"
                >
                  {{ change }}
                </li>
              </ul>
            </article>
          </div>

          <p v-else class="a9-prototype-save-empty-note">
            当前草稿没有可解析的修改明细，但已检测到本地原型内容发生变化。确认后仍会保存为新版本。
          </p>
        </section>

        <footer class="a9-prototype-save-confirm-footer">
          <button type="button" class="a9-secondary-button compact" @click="cancelA9PrototypeSaveConfirm">
            再检查一下
          </button>
          <button type="button" class="a9-primary-button compact" @click="confirmA9PrototypeSaveDraft">
            确认保存为新版本
          </button>
        </footer>
      </article>
    </div>

  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getModuleDisplayName, getModuleStableKey } from '../api'
import type { ArtifactResponse, ManualArtifactEditPayload, ModuleTreeNode } from '../types/codemind'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type { UiContractComponent, UiContractContent } from '../types/uiContract'
import {
  isFeatureNode,
  isPageNode,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'
import PrototypeStudioOverlay from './PrototypeStudioOverlay.vue'
import ModulePrdView from './ModulePrdView.vue'

defineOptions({
  name: 'AcceptanceRequirementWorkspace',
})

type PrototypeElementSelection = {
  componentId?: string | null
  componentType?: string | null
  componentTitle?: string | null
  elementKind?: string | null
  elementLabel?: string | null
  fieldKey?: string | null
  actionId?: string | null
  columnKey?: string | null
  tabKey?: string | null
  textKey?: string | null
  textRole?: string | null
  elementPath?: string | string[] | null
  [key: string]: unknown
}

const props = withDefaults(
  defineProps<{
    selectedModule: ModuleTreeNode | null
    moduleTree?: ModuleTreeNode[]
    isGeneratingFullChain?: boolean
    isGeneratingUiContract?: boolean
    modulePrdArtifact?: ArtifactResponse | null
    prdArtifact?: ArtifactResponse | null
    modulePrdMarkdown?: string
    uiContractArtifact?: ArtifactResponse | null
    uiContractMarkdown?: string
    uiContractDraftContent?: UiContractContent | null
    prototypeEditDraftDirty?: boolean
    prototypeEditDraftSummary?: string
    focusedComponentId?: string | null
    focusedComponentLabel?: string
    focusedPrototypeElementSelection?: Record<string, unknown> | null
    focusedElementSelection?: PrototypeElementSelection | null
    projectThemeProfile?: ProjectThemeProfile | null
    confirmedUiContractSnapshot?: Record<string, unknown> | null
    developmentHandoffReadiness?: Record<string, unknown> | null
    manualEditSaving?: boolean
  }>(),
  {
    moduleTree: () => [],
    isGeneratingFullChain: false,
    isGeneratingUiContract: false,
    modulePrdArtifact: null,
    prdArtifact: null,
    modulePrdMarkdown: '',
    uiContractArtifact: null,
    uiContractMarkdown: '',
    uiContractDraftContent: null,
    prototypeEditDraftDirty: false,
    prototypeEditDraftSummary: '',
    focusedComponentId: null,
    focusedComponentLabel: '',
    focusedPrototypeElementSelection: null,
    focusedElementSelection: null,
    projectThemeProfile: null,
    confirmedUiContractSnapshot: null,
    developmentHandoffReadiness: null,
    manualEditSaving: false,
  },
)

const emit = defineEmits<{
  'generate-full-chain': []
  'generate-ui-contract': []
  'open-ui-contract-preview': []
  'save-prototype-draft': []
  'discard-prototype-draft': []
  'clear-prototype-selection': []
  'select-prototype-component': [payload: PrototypeElementSelection]
  'prototype-quick-edit': [instruction: string]
  'save-manual-artifact-edit': [payload: ManualArtifactEditPayload]
}>()


const prototypeInspectMode = ref(false)
const prototypeStudioVisible = ref(false)
const prototypeStudioStartInspectMode = ref(false)

function togglePrototypeInspectMode() {
  prototypeInspectMode.value = !prototypeInspectMode.value
}

function openPrototypeStudio() {
  if (!hasPrototype.value || !uiContractObject.value) {
    return
  }

  prototypeStudioStartInspectMode.value = false
  prototypeStudioVisible.value = true
}

function openPrototypeStudioInspect() {
  if (!hasPrototype.value || !uiContractObject.value) {
    return
  }

  prototypeStudioStartInspectMode.value = true
  prototypeStudioVisible.value = true
}

function closePrototypeStudio() {
  prototypeStudioVisible.value = false
  prototypeStudioStartInspectMode.value = false
}

function handlePrototypeStudioInstruction(instruction: string) {
  const text = String(instruction || '').trim()

  if (!text) {
    return
  }

  emit('prototype-quick-edit', text)
}

function handlePrototypeStudioSaveDraft() {
  openA9PrototypeSaveConfirm()
}


function handlePrototypeSelectionClear() {
  emit('clear-prototype-selection')
}

function handlePrototypeComponentSelect(
  payload: PrototypeElementSelection,
) {
  const componentId = String(payload?.componentId || '').trim()

  if (!componentId) {
    return
  }

  emit('select-prototype-component', {
    componentId,
    componentType: payload.componentType || '',
    componentTitle: payload.componentTitle || '',
    elementKind: payload.elementKind || 'COMPONENT',
    elementLabel: payload.elementLabel || payload.componentTitle || componentId,
    fieldKey: payload.fieldKey || '',
    actionId: payload.actionId || '',
    columnKey: payload.columnKey || '',
    tabKey: payload.tabKey || '',
    textKey: payload.textKey || '',
    textRole: payload.textRole || '',
    elementPath: payload.elementPath || '',
  })
  prototypeInspectMode.value = false
}

watch(
  () => props.selectedModule?.id || null,
  () => {
    prototypeInspectMode.value = false
    closePrototypeStudio()
  },
)



interface PrototypeDraftEditExplainEntry {
  key: string
  indexLabel: string
  targetLabel: string
  instruction: string
  changes: string[]
  timeLabel: string
}

const prototypeDraftEditRawEntries = computed<Record<string, unknown>[]>(() => {
  const metadata = (props.uiContractDraftContent as unknown as Record<string, unknown> | null)?.metadata

  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    return []
  }

  const edits = (metadata as Record<string, unknown>).prototypeLocalEdits
  return Array.isArray(edits)
    ? edits.filter((item): item is Record<string, unknown> =>
        Boolean(item) && typeof item === 'object' && !Array.isArray(item),
      )
    : []
})

const prototypeDraftEditTotalCount = computed(() => prototypeDraftEditRawEntries.value.length)

const prototypeDraftEditEntries = computed<PrototypeDraftEditExplainEntry[]>(() => {
  const entries = prototypeDraftEditRawEntries.value
  const total = entries.length

  return entries
    .map((entry, index) => normalizePrototypeDraftEditEntry(entry, index, total))
    .slice(-6)
    .reverse()
})

function normalizePrototypeDraftEditEntry(
  entry: Record<string, unknown>,
  index: number,
  total: number,
): PrototypeDraftEditExplainEntry {
  const selection = normalizeSelectionRecord(entry.selectionRef)
  const changes = normalizeStringList(entry.changes)
  const targetLabel = buildPrototypeDraftEditTargetLabel(entry, selection)
  const instruction = stringValue(entry.instruction) || '快捷编辑原型元素'

  return {
    key: `${stringValue(entry.editedAt) || index}-${stringValue(entry.targetComponentId) || 'target'}-${index}`,
    indexLabel: `#${total - index}`,
    targetLabel,
    instruction,
    changes: changes.length ? changes : ['已更新原型元素'],
    timeLabel: formatPrototypeEditTime(entry.editedAt),
  }
}

function buildPrototypeDraftEditTargetLabel(
  entry: Record<string, unknown>,
  selection: Record<string, unknown>,
): string {
  const kind = String(
    selection.elementKind ||
      selection.kind ||
      selection.componentType ||
      '',
  ).toUpperCase()

  const kindMap: Record<string, string> = {
    COMPONENT: '组件',
    FIELD: '字段',
    FIELD_LABEL: '字段名',
    FIELD_CONTROL: '输入框',
    ACTION: '按钮',
    BUTTON: '按钮',
    LINK: '链接',
    TEXT: '文案',
    TITLE: '标题',
    DESCRIPTION: '说明',
    TABLE_COLUMN: '表格列',
    TABLE_CELL: '表格单元格',
    TABLE_ACTION: '行操作',
    TAB: '标签页',
    METRIC: '指标卡',
  }

  const label = stringValue(
    selection.elementLabel ||
      selection.fieldKey ||
      selection.actionId ||
      selection.columnKey ||
      selection.tabKey ||
      selection.textKey ||
      selection.componentTitle ||
      entry.targetComponentId ||
      '当前元素',
  )

  const kindLabel = kindMap[kind] || kind || '元素'
  return `${kindLabel} · ${label}`
}

function normalizeSelectionRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function normalizeStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}

function formatPrototypeEditTime(value: unknown): string {
  const text = stringValue(value)
  if (!text) return '刚刚'

  const date = new Date(text)
  if (Number.isNaN(date.getTime())) {
    return text
  }

  const pad = (input: number) => String(input).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

interface PrototypeQuickEditAction {
  key: string
  label: string
  instruction: string
  prompt?: string
  placeholder?: string
}

const prototypeSelectionKind = computed(() => {
  const selection = props.focusedPrototypeElementSelection as Record<string, unknown> | null
  return String(
    selection?.elementKind ||
      selection?.kind ||
      selection?.type ||
      '',
  ).toUpperCase()
})

const prototypeQuickEditActions = computed<PrototypeQuickEditAction[]>(() => {
  if (!props.focusedComponentId) {
    return []
  }

  const kind = prototypeSelectionKind.value
  const label = props.focusedComponentLabel || '当前元素'

  if (kind.includes('FIELD') || kind.includes('INPUT') || kind.includes('CONTROL')) {
    return [
      {
        key: 'field-label',
        label: '改字段名',
        instruction: '把这个字段改成',
        prompt: '请输入新的字段名',
        placeholder: readableTextDefault(label),
      },
      {
        key: 'field-placeholder',
        label: '改 placeholder',
        instruction: '把这个输入框 placeholder 改成',
        prompt: '请输入新的 placeholder',
        placeholder: '请输入' + readableTextDefault(label),
      },
      {
        key: 'field-required',
        label: '设为必填',
        instruction: '把这个字段设为必填',
      },
      {
        key: 'field-optional',
        label: '设为非必填',
        instruction: '把这个字段设为非必填',
      },
      {
        key: 'field-hide',
        label: '隐藏字段',
        instruction: '隐藏这个字段',
      },
    ]
  }

  if (kind.includes('ACTION') || kind.includes('BUTTON') || kind.includes('LINK')) {
    return [
      {
        key: 'button-text',
        label: '改按钮文案',
        instruction: '把这个按钮文案改成',
        prompt: '请输入新的按钮文案',
        placeholder: readableTextDefault(label),
      },
      {
        key: 'button-primary',
        label: '改成蓝色主按钮',
        instruction: '把这个按钮改成蓝色主按钮',
      },
      {
        key: 'button-green',
        label: '改成绿色',
        instruction: '把这个按钮改成绿色',
      },
      {
        key: 'button-large',
        label: '放大一点',
        instruction: '把这个按钮放大一点',
      },
      {
        key: 'button-hide',
        label: '隐藏按钮',
        instruction: '隐藏这个按钮',
      },
    ]
  }

  if (kind.includes('COLUMN') || kind.includes('CELL') || kind.includes('TABLE')) {
    return [
      {
        key: 'column-label',
        label: '改列名',
        instruction: '把这个表格列改成',
        prompt: '请输入新的列名',
        placeholder: readableTextDefault(label),
      },
      {
        key: 'column-wide',
        label: '加宽一点',
        instruction: '把这个表格列加宽一点',
      },
      {
        key: 'column-narrow',
        label: '收窄一点',
        instruction: '把这个表格列收窄一点',
      },
      {
        key: 'column-hide',
        label: '隐藏该列',
        instruction: '隐藏这个表格列',
      },
    ]
  }

  if (kind.includes('TAB')) {
    return [
      {
        key: 'tab-title',
        label: '改标签名',
        instruction: '把这个标签页改成',
        prompt: '请输入新的标签名',
        placeholder: readableTextDefault(label),
      },
      {
        key: 'tab-blue',
        label: '改成蓝色',
        instruction: '把这个标签页改成蓝色',
      },
      {
        key: 'tab-hide',
        label: '隐藏标签页',
        instruction: '隐藏这个标签页',
      },
    ]
  }

  if (kind.includes('TEXT') || kind.includes('TITLE') || kind.includes('DESCRIPTION') || kind.includes('LABEL')) {
    return [
      {
        key: 'text-change',
        label: '改文案',
        instruction: '把这段文案改成',
        prompt: '请输入新的文案',
        placeholder: readableTextDefault(label),
      },
      {
        key: 'text-blue',
        label: '改成蓝色',
        instruction: '把这段文案改成蓝色',
      },
      {
        key: 'text-large',
        label: '放大一点',
        instruction: '把这段文案放大一点',
      },
      {
        key: 'text-hide',
        label: '隐藏文案',
        instruction: '隐藏这段文案',
      },
    ]
  }

  return [
    {
      key: 'generic-text',
      label: '改文案',
      instruction: '把这个元素文案改成',
      prompt: '请输入新的文案',
      placeholder: readableTextDefault(label),
    },
    {
      key: 'generic-blue',
      label: '改成蓝色',
      instruction: '把这个元素改成蓝色',
    },
    {
      key: 'generic-large',
      label: '放大一点',
      instruction: '把这个元素放大一点',
    },
    {
      key: 'generic-hide',
      label: '隐藏元素',
      instruction: '隐藏这个元素',
    },
  ]
})

function handlePrototypeQuickEditSuggestion(action: PrototypeQuickEditAction) {
  let instruction = action.instruction

  if (action.prompt) {
    const value = window.prompt(action.prompt, action.placeholder || '')
    if (!value || !value.trim()) {
      return
    }

    instruction = `${instruction}“${value.trim()}”`
  }

  emit('prototype-quick-edit', instruction)
}

function readableTextDefault(value: unknown): string {
  const text = String(value || '')
    .replace(/^[A-Z_]+\s*[·:-]\s*/i, '')
    .replace(/^已选中\s*[·:-]\s*/i, '')
    .trim()

  return text || '当前元素'
}

const focusedComponentDisplayLabel = computed(() => {
  const selection = props.focusedElementSelection

  if (selection?.elementLabel) {
    return selection.elementLabel
  }

  if (selection?.fieldKey) {
    return `字段 · ${selection.fieldKey}`
  }

  if (selection?.actionId) {
    return `按钮/动作 · ${selection.actionId}`
  }

  if (selection?.columnKey) {
    return `表格列 · ${selection.columnKey}`
  }

  return props.focusedComponentLabel || props.focusedComponentId || '已选元素'
})
const nodeType = computed(() => resolveRequirementNodeType(props.selectedModule))
const isModule = computed(() => nodeType.value === 'MODULE')
const isFeature = computed(() => isFeatureNode(props.selectedModule))
const isPage = computed(() => isPageNode(props.selectedModule))

const nodeName = computed(() => props.selectedModule ? getModuleDisplayName(props.selectedModule) : '未选择节点')
const nodeKey = computed(() => props.selectedModule ? getModuleStableKey(props.selectedModule) : '')

const moduleChildren = computed(() => childNodesByType(['MODULE']))
const featureChildren = computed(() => childNodesByType(['FEATURE']))
const pageChildren = computed(() => childNodesByType(['PAGE']))
const componentChildren = computed(() => childNodesByType(['COMPONENT']))
const hasChildModules = computed(() => moduleChildren.value.length > 0)
const inlineModuleRequirementText = computed(() => resolveInlineModuleRequirementText(safeExtra(props.selectedModule)))
const effectiveModulePrdArtifact = computed(() => props.modulePrdArtifact || props.prdArtifact || null)
const currentModuleRequirementArtifact = computed(() => props.modulePrdArtifact || props.prdArtifact || null)
const moduleRequirementMarkdownForReader = computed(() => resolveModuleRequirementMarkdownForReader())
const moduleRequirementDocumentAvailable = computed(() => Boolean(moduleRequirementMarkdownForReader.value.trim()))
const moduleRequirementReaderArtifact = computed<ArtifactResponse | null>(() => {
  const artifact = currentModuleRequirementArtifact.value
  const markdown = moduleRequirementMarkdownForReader.value

  if (artifact) {
    return {
      ...(artifact as any),
      artifactType: (artifact as any).artifactType || 'MODULE_PRD',
      title: (artifact as any).title || `${nodeName.value}模块需求`,
      contentMarkdown: (artifact as any).contentMarkdown || markdown,
    } as ArtifactResponse
  }

  if (!props.selectedModule || !markdown.trim()) {
    return null
  }

  return {
    exists: true,
    artifactType: 'MODULE_PRD',
    artifactId: null,
    moduleId: props.selectedModule.id,
    moduleKey: nodeKey.value,
    moduleName: nodeName.value,
    title: `${nodeName.value}模块需求`,
    content: {
      _inline_module_requirement_document: true,
    },
    contentMarkdown: markdown,
    versionNo: null,
    current: true,
  } as unknown as ArtifactResponse
})
const hasModuleDocument = computed(() => Boolean(
  (currentModuleRequirementArtifact.value as any)?.content ||
    (currentModuleRequirementArtifact.value as any)?.contentMarkdown ||
    props.modulePrdMarkdown ||
    moduleRequirementDocumentAvailable.value,
))
const hasModuleRequirement = computed(() => Boolean(hasModuleDocument.value || inlineModuleRequirementText.value))
const isTerminalRequirementModule = computed(() => isModule.value && !hasChildModules.value)
const showModuleRequirementDocument = computed(() => isTerminalRequirementModule.value)
const showModuleRequirementAction = computed(() => isTerminalRequirementModule.value && !moduleRequirementDocumentAvailable.value)
const hasPrototype = computed(() => Boolean(props.uiContractArtifact?.content || props.uiContractMarkdown))
const hasRequirementTree = computed(() => props.moduleTree.length > 0)

const uiContractObject = computed<UiContractContent | null>(() => {
  const content = props.uiContractDraftContent || props.uiContractArtifact?.content || tryParseJson(props.uiContractMarkdown)
  return normalizeUiContractCandidate(content)
})

const prototypeComponentCount = computed(() => {
  return flattenComponents(uiContractObject.value?.page?.componentTree || []).length
})


const prototypeLifecycleStatusItems = computed(() => {
  const currentVersionNo = numberValue(props.uiContractArtifact?.versionNo)
  const snapshot = recordValue(props.confirmedUiContractSnapshot)
  const snapshotVersionNo = resolveSnapshotVersionNo(snapshot)
  const readiness = recordValue(props.developmentHandoffReadiness)

  const versionText = currentVersionNo ? `V${currentVersionNo}` : '已生成'

  const items: Array<{
    key: string
    eyebrow: string
    title: string
    description: string
    badge: string
    state: 'ready' | 'warning' | 'blocked' | 'muted'
  }> = [
    {
      key: 'ui-contract-version',
      eyebrow: '交互原型',
      title: `当前原型版本：${versionText}`,
      description: prototypeEditDraftDirty.value
        ? '当前画布还有未保存的本地修改，保存后会生成新的交互原型版本。'
        : '当前页面原型已进入版本体系，后续编辑会保存为新的交互原型版本。',
      badge: prototypeEditDraftDirty.value ? '有未保存修改' : '版本就绪',
      state: prototypeEditDraftDirty.value ? 'warning' : 'ready',
    },
  ]

  if (!snapshot) {
    items.push({
      key: 'ui-contract-confirmation',
      eyebrow: '确认快照',
      title: '尚未确认当前交互原型',
      description: '生成或保存原型后，需要在完整预览中确认快照，Development Agent 才能拿到稳定的页面交付基线。',
      badge: '待确认',
      state: 'warning',
    })
  } else if (currentVersionNo && snapshotVersionNo && currentVersionNo === snapshotVersionNo) {
    items.push({
      key: 'ui-contract-confirmation',
      eyebrow: '确认快照',
      title: `已确认 V${snapshotVersionNo}`,
      description: '确认快照与当前 交互原型 版本一致，可作为后续开发交接与代码生成依据。',
      badge: '已同步',
      state: 'ready',
    })
  } else {
    items.push({
      key: 'ui-contract-confirmation',
      eyebrow: '确认快照',
      title: snapshotVersionNo
        ? `确认快照仍停留在 V${snapshotVersionNo}`
        : '确认快照可能已过期',
      description: '当前交互原型已发生版本变化，建议重新打开完整预览并确认原型快照，避免 Dev Handoff 使用旧页面基线。',
      badge: '需重新确认',
      state: 'blocked',
    })
  }

  const readinessLevel = stringValue(readiness?.readinessLevel || readiness?.level || readiness?.status).toUpperCase()
  const readinessReady = readiness?.ready === true
  const blockerCount = arrayValue(readiness?.blockers).length
  const warningCount = arrayValue(readiness?.warnings).length

  if (!readiness) {
    items.push({
      key: 'dev-handoff-sync',
      eyebrow: 'Dev Handoff',
      title: '交接包状态待刷新',
      description: '当前页面原型版本状态尚未和开发交接门禁联动展示。保存或确认原型后，系统会刷新交接状态。',
      badge: '待刷新',
      state: 'muted',
    })
  } else if (readinessReady && blockerCount === 0 && !readinessLevel.includes('BLOCK')) {
    items.push({
      key: 'dev-handoff-sync',
      eyebrow: 'Dev Handoff',
      title: '开发交接门禁可通过',
      description: warningCount > 0
        ? `当前没有阻断项，但仍有 ${warningCount} 条提醒需要关注。`
        : '当前交互原型与开发交付门禁状态良好。',
      badge: warningCount > 0 ? `${warningCount} 条提醒` : '可交付',
      state: warningCount > 0 ? 'warning' : 'ready',
    })
  } else {
    items.push({
      key: 'dev-handoff-sync',
      eyebrow: 'Dev Handoff',
      title: '开发交接需要同步',
      description: blockerCount > 0
        ? `当前还有 ${blockerCount} 个阻断项，通常需要重新确认原型或同步下游资产。`
        : '当前开发交接状态未完全就绪，建议刷新门禁并检查交互原型和开发交接包。',
      badge: blockerCount > 0 ? `${blockerCount} 个阻断` : '需同步',
      state: 'blocked',
    })
  }

  return items
})

const selectedKindLabel = computed(() => {
  if (isPage.value) return '页面'
  if (isFeature.value) return '功能点'
  return '模块'
})

const childSummary = computed(() => {
  if (!props.selectedModule) return '0'
  if (isPage.value) return String(componentChildren.value.length)
  if (isFeature.value) return String(pageChildren.value.length)
  return String((props.selectedModule.children || []).length)
})

const moduleSectionTitle = computed(() => {
  return isTerminalRequirementModule.value ? '模块需求' : '模块描述'
})

const moduleDescription = computed(() => {
  const extra = safeExtra(props.selectedModule)
  const requirementContext = recordValue(extra.requirementContext) || recordValue(extra.requirement_context)
  return stringValue(
    extra.requirement ||
      extra.description ||
      extra.summary ||
      extra.requirementSummary ||
      requirementContext?.requirementSummary ||
      requirementContext?.businessBoundary ||
      props.selectedModule?.modulePath ||
      props.selectedModule?.moduleName ||
      '当前模块还没有描述。可以先写一句模块描述，再继续拆分下级模块；当模块不再拆分时，再生成模块文档。',
  )
})

const featureDescription = computed(() => {
  const extra = safeExtra(props.selectedModule)
  return stringValue(
    extra.requirement ||
      extra.description ||
      extra.summary ||
      props.selectedModule?.moduleName ||
      '当前功能点还没有需求描述。请补充功能目标、业务规则、输入输出和异常处理，再生成页面。',
  )
})


type A982RequirementBlock = {
  title: string
  items: string[]
  emptyText: string
}

const moduleRequirementParagraphs = computed(() => {
  const context = a982RequirementContext()
  const lines: string[] = []

  a982CollectTextItems(context?.requirementText, lines)
  a982CollectTextItems(context?.moduleRequirement, lines)
  a982CollectTextItems(context?.requirement, lines)
  a982CollectTextItems(context?.businessGoal, lines)
  a982CollectTextItems(context?.requirementSummary, lines)
  a982CollectTextItems(context?.businessBoundary, lines)
  a982CollectTextItems(moduleDescription.value, lines)

  const unique = a982UniqueText(lines)
  if (unique.length > 0) {
    return unique.slice(0, 3)
  }

  if (featureChildren.value.length || a982CollectDescendantNodes(props.selectedModule, 'PAGE').length) {
    return [
      `${nodeName.value}围绕当前业务范围组织功能点和页面，后续交互原型会继续依赖这份模块需求。`,
    ]
  }

  return ['当前模块还没有明确的模块需求。可以先补充一句业务目标，再继续拆分功能点和页面。']
})

const moduleRequirementBlocks = computed<A982RequirementBlock[]>(() => {
  const context = a982RequirementContext()
  const blocks: A982RequirementBlock[] = []

  const boundaryItems = a982UniqueText([
    ...a982TextArray(context?.businessScope),
    ...a982TextArray(context?.scope),
    ...a982TextArray(context?.businessBoundary),
  ]).filter((item) => item !== moduleDescription.value)

  const featureItems = a982UniqueText([
    ...featureChildren.value.map((item) => a982NodeName(item)),
    ...a982TextArray(context?.functionNames),
    ...a982TextArray(context?.functions),
    ...a982TextArray(context?.features),
  ]).slice(0, 12)

  const pageItems = a982UniqueText([
    ...a982CollectDescendantNodes(props.selectedModule, 'PAGE').map((item) => a982NodeName(item)),
    ...a982TextArray(context?.pageNames),
    ...a982TextArray(context?.pages),
  ]).slice(0, 12)

  const objectItems = a982UniqueText([
    ...a982TextArray(context?.businessObjects),
    ...a982TextArray(context?.coreObjects),
    ...a982TextArray(context?.entities),
    ...a982TextArray(context?.keyFields),
  ]).slice(0, 10)

  if (boundaryItems.length > 0) {
    blocks.push({
      title: '业务范围',
      items: boundaryItems.slice(0, 6),
      emptyText: '',
    })
  }

  blocks.push({
    title: '核心功能',
    items: featureItems,
    emptyText: '当前模块暂未拆出功能点。',
  })

  blocks.push({
    title: '页面方向',
    items: pageItems,
    emptyText: '当前模块暂未拆出页面。',
  })

  if (objectItems.length > 0) {
    blocks.push({
      title: '关键对象',
      items: objectItems,
      emptyText: '',
    })
  }

  return blocks
})

const pageDescription = computed(() => {
  const extra = safeExtra(props.selectedModule)
  return stringValue(
    extra.description ||
      extra.summary ||
      props.selectedModule?.routePath ||
      props.selectedModule?.moduleName ||
      '当前页面用于承载所属功能点的交互流程。',
  )
})

const modulePositionText = computed(() => {
  if (hasChildModules.value) {
    return '这是一个分组模块，主要用于组织下级模块。它只保留模块描述，不直接生成模块需求。'
  }

  if (featureChildren.value.length > 0 || hasModuleRequirement.value) {
    return '这是一个最终需求模块，已经进入功能点与页面设计阶段。'
  }

  return '这是一个末级模块候选。确认不再继续拆分模块后，可以生成或手动完善模块需求。'
})

const moduleNextStepText = computed(() => {
  if (hasChildModules.value) {
    return '继续选择下级模块；不要在当前分组模块上生成详细需求。'
  }

  if (!hasModuleRequirement.value) {
    return '先完善模块需求并生成模块文档。后续功能点和页面会继续依赖这份模块需求。'
  }

  if (featureChildren.value.length === 0) {
    return '模块需求已准备好，下一步应根据需求生成或手动添加功能点。'
  }

  return '继续完善功能点，并在功能点下面生成页面和交互原型。'
})

const moduleRequirementButtonText = computed(() => {
  return hasModuleRequirement.value ? '完善模块需求' : '生成模块文档'
})

const prototypeButtonText = computed(() => {
  return hasPrototype.value ? '重新生成交互原型' : '生成交互原型'
})

const prototypeSizeLabel = computed(() => {
  const raw = props.uiContractMarkdown || JSON.stringify(props.uiContractArtifact?.content || {})
  if (!raw) return '0 KB'
  const kb = raw.length / 1024
  return `${kb.toFixed(kb >= 10 ? 0 : 1)} KB`
})



function resolveModuleRequirementMarkdownForReader(): string {
  const artifactMarkdown = stringValue(
    props.modulePrdMarkdown ||
      currentModuleRequirementArtifact.value?.contentMarkdown,
  )

  if (artifactMarkdown) {
    return artifactMarkdown
  }

  const extra = safeExtra(props.selectedModule)
  const context = a982RequirementContext()

  const direct = stringValue(
    extra.moduleRequirementMarkdown ||
      extra.module_requirement_markdown ||
      context?.moduleRequirementMarkdown ||
      context?.module_requirement_markdown ||
      context?.requirementMarkdown ||
      context?.requirement_markdown,
  )

  if (direct) {
    return direct
  }

  return buildGeneratedModuleRequirementMarkdown()
}

function buildGeneratedModuleRequirementMarkdown(): string {
  if (!props.selectedModule || !isTerminalRequirementModule.value) {
    return ''
  }

  const context = a982RequirementContext()
  const features = a982UniqueText([
    ...featureChildren.value.map((item) => a982NodeName(item)),
    ...a982TextArray(context?.functionNames),
    ...a982TextArray(context?.functions),
    ...a982TextArray(context?.features),
  ]).slice(0, 18)

  const pages = a982UniqueText([
    ...a982CollectDescendantNodes(props.selectedModule, 'PAGE').map((item) => a982NodeName(item)),
    ...a982TextArray(context?.pageNames),
    ...a982TextArray(context?.pages),
  ]).slice(0, 18)

  const objects = a982UniqueText([
    ...a982TextArray(context?.businessObjects),
    ...a982TextArray(context?.coreObjects),
    ...a982TextArray(context?.entities),
    ...a982TextArray(context?.keyFields),
    ...inferModuleBusinessObjectsFromText(`${nodeName.value} ${moduleDescription.value}`),
  ]).slice(0, 12)

  const summary = stringValue(
    context?.requirementText ||
      context?.requirementSummary ||
      context?.businessBoundary ||
      inlineModuleRequirementText.value ||
      moduleDescription.value,
  )

  const featureText = features.length ? features.join('、') : '核心业务操作、查询、维护和确认'
  const pageText = pages.length ? pages.join('、') : '列表页、表单页和详情页'
  const objectText = objects.length ? objects.join('、') : '业务单据、业务明细、状态、经办人和操作记录'

  const lines = [
    `# ${nodeName.value}模块需求`,
    '',
    '## 1. 模块定位',
    summary || `${nodeName.value}负责承载当前业务范围内的核心操作、查询、维护和状态流转。`,
    `${nodeName.value}是当前项目中的末级需求模块，负责把明确的业务边界落到可操作的功能点和页面入口上。模块内的功能点应围绕同一组业务对象展开，并支持业务人员从发起、维护、查看、处理到结果确认的完整路径。`,
    '',
    '## 2. 业务目标',
    `本模块的目标是支持${featureText}等关键业务能力，使用户能够在统一入口中完成${objectText}相关的信息录入、查询、跟踪和处理。模块交付后，应能够支撑日常业务操作、状态确认、异常处理和管理追踪。`,
    '',
    '## 3. 使用角色',
    ...markdownList(inferModuleUserRolesFromText(`${nodeName.value} ${moduleDescription.value}`), ['业务经办人：负责发起、维护和查询本模块业务数据。']),
    '',
    '## 4. 业务范围',
    `本模块覆盖${objectText}相关的业务信息管理，主要包含${featureText}，并通过${pageText}等页面入口承载具体操作。`,
    '本模块不承担跨业务域的系统配置、全局权限分配或报表中心职责；这些内容应由上层系统管理、基础资料或报表分析模块承接。',
    '',
    '## 5. 核心业务对象',
    ...markdownList(objects, ['业务单据', '业务明细', '业务状态', '经办人', '操作记录']),
    '',
    '## 6. 业务主流程',
    ...numberedMarkdownList(inferModuleProcessStepsFromFeatures(features)),
    '',
    '## 7. 功能点需求',
    ...buildFeatureRequirementMarkdownItems(features),
    '',
    '## 8. 页面规划',
    ...buildPageRequirementMarkdownItems(pages),
    '',
    '## 9. 状态与流转规则',
    ...markdownList(inferModuleStateRulesFromText(`${nodeName.value} ${moduleDescription.value}`), []),
    '',
    '## 10. 关键业务规则',
    ...markdownList(inferModuleBusinessRulesFromText(`${nodeName.value} ${moduleDescription.value}`), []),
    '',
    '## 11. 异常场景',
    ...markdownList([
      '用户提交缺少必填信息的数据时，系统应阻止提交并提示需要补充的内容。',
      '用户操作已完成或已锁定记录时，系统应提示当前状态不允许继续修改。',
      '查询结果为空时，页面应明确展示空状态，并允许用户调整条件重新查询。',
      '审核或确认过程中出现业务冲突时，系统应提示冲突原因，并保留用户已填写信息。',
    ], []),
    '',
    '## 12. 权限与操作边界',
    ...markdownList([
      '普通业务人员可以创建、查询和维护本人权限范围内的数据。',
      '主管或审核角色可以处理提交、审核、驳回、确认等关键状态流转操作。',
      '只读或查看类角色只能查看列表、详情和处理结果，不能执行保存、删除或审核操作。',
      '系统管理员负责账号、角色和权限配置维护。',
    ], []),
    '',
    '## 13. 验收要点',
    ...markdownList([
      '用户可以按照模块主流程完成关键业务操作，并看到明确的成功、失败或待处理反馈。',
      '列表、表单、详情、审核等页面入口与功能点对应关系清晰，不出现无法到达或职责不明的页面。',
      '关键状态流转符合业务规则，异常场景有清晰提示，不产生无效或冲突数据。',
      '不同角色只能看到和执行自己权限范围内的操作。',
      ...(features.length ? [`以下功能点均有明确入口和可验证结果：${features.join('、')}。`] : []),
      ...(pages.length ? [`以下页面均能承载对应业务场景：${pages.join('、')}。`] : []),
    ], []),
  ]

  return lines.join('\n\n')
}

function buildFeatureRequirementMarkdownItems(features: string[]): string[] {
  if (!features.length) {
    return ['- 当前模块尚未拆出功能点，后续应补充可开发、可测试、可验收的业务能力。']
  }

  return features.flatMap((feature, index) => [
    `### 7.${index + 1} ${feature}`,
    `${feature}用于完成${nodeName.value}中的一项核心业务能力，需要明确业务触发条件、输入信息、处理过程和结果反馈。`,
    '- 主要输入：业务编号、业务对象、状态条件、经办信息和必要备注。',
    '- 处理结果：完成数据保存、状态更新、结果展示或待办流转。',
    '- 验收要求：用户能够按预期完成操作，异常输入能够得到清晰提示，关键操作可追踪。',
    '',
  ])
}

function buildPageRequirementMarkdownItems(pages: string[]): string[] {
  if (!pages.length) {
    return ['- 当前模块尚未规划页面。后续应在功能点下补充列表、表单、详情、审核或看板等页面入口。']
  }

  return pages.flatMap((page, index) => [
    `### 8.${index + 1} ${page}`,
    `${page}用于承载对应功能点的主要业务操作，页面应保证主要入口清晰、业务状态可见、异常信息明确，并与同一模块内其他页面形成自然跳转。`,
    '',
  ])
}

function inferModuleBusinessObjectsFromText(text: string): string[] {
  if (/入库/.test(text)) {
    return ['入库单', '入库明细', '供应商', '仓库', '物料/商品', '入库数量', '入库状态', '审核记录']
  }
  if (/出库/.test(text)) {
    return ['出库单', '出库明细', '客户/领用方', '仓库', '物料/商品', '出库数量', '出库状态', '审核记录']
  }
  if (/库存/.test(text)) {
    return ['库存记录', '物料/商品', '仓库', '可用数量', '锁定数量', '预警阈值', '盘点记录', '更新时间']
  }
  if (/供应商/.test(text)) {
    return ['供应商档案', '联系人', '供货范围', '合作状态', '资质信息', '维护记录']
  }
  if (/订单/.test(text)) {
    return ['订单', '订单明细', '客户', '商品', '支付状态', '履约状态', '操作记录']
  }
  return ['业务单据', '业务明细', '业务对象', '状态', '经办人', '创建时间', '处理记录', '备注']
}

function inferModuleUserRolesFromText(text: string): string[] {
  if (/仓储|入库|出库|库存|仓库/.test(text)) {
    return [
      '仓库经办人：负责创建、维护和查询日常业务单据。',
      '仓库主管：负责审核关键操作、确认状态流转并处理异常。',
      '供应链/采购人员：关注供应商、到货、入库或库存相关业务结果。',
      '系统管理员：负责账号、权限和基础配置维护。',
    ]
  }

  return [
    '业务经办人：负责发起、维护和查询本模块业务数据。',
    '业务主管：负责审核、确认和跟踪关键业务结果。',
    '运营管理人员：负责查看处理进度、异常情况和数据质量。',
    '系统管理员：负责权限、账号和基础配置维护。',
  ]
}

function inferModuleProcessStepsFromFeatures(features: string[]): string[] {
  const featureText = features.join(' ')
  return [
    /创建|新建|新增/.test(featureText)
      ? '业务人员创建或维护业务单据，录入必要的业务对象、数量、状态和备注信息。'
      : '业务人员进入模块后，根据业务场景选择查询、查看或处理入口。',
    '系统根据用户输入进行基础校验，保证必填信息、状态条件和业务对象有效。',
    '用户提交后，系统保存业务结果并更新当前记录状态。',
    '需要审核或确认的场景进入待处理状态，由有权限的角色完成后续处理。',
    '用户可通过列表、详情或审核页面追踪处理结果、历史记录和异常信息。',
  ]
}

function inferModuleStateRulesFromText(text: string): string[] {
  if (/入库/.test(text)) {
    return [
      '草稿：入库单已创建但尚未提交，可继续编辑。',
      '待审核：入库单已提交，等待有权限人员审核。',
      '已审核：审核通过后进入后续入库确认或库存更新环节。',
      '已入库：入库结果已确认，相关库存数量完成更新。',
      '已驳回：审核不通过，需要经办人修改后重新提交或作废。',
    ]
  }

  return [
    '草稿：业务记录尚未提交，可继续编辑。',
    '处理中：业务已提交，等待系统处理或人员确认。',
    '已完成：业务结果已确认并对用户可见。',
    '已驳回/异常：业务处理未通过或出现异常，需要补充信息后重新处理。',
  ]
}

function inferModuleBusinessRulesFromText(text: string): string[] {
  const rules = [
    '关键业务对象必须完整填写，缺少必填信息时不允许提交。',
    '涉及数量、金额或状态的字段必须进行合法性校验，不允许出现负数、空值或与业务状态冲突的数据。',
    '已经完成、审核通过或被锁定的记录不应被普通用户随意删除或覆盖。',
    '关键操作应记录操作人、操作时间、操作结果和必要备注，便于后续追踪。',
  ]

  if (/审核|审批/.test(text)) {
    rules.push('提交审核后，普通经办人只能查看处理结果，审核通过或驳回必须由有权限的角色完成。')
  }
  if (/库存|入库|出库/.test(text)) {
    rules.push('涉及库存变化的操作必须保证单据状态与库存更新一致，避免重复入库、重复出库或库存数量不一致。')
  }

  return rules
}

function markdownList(items: string[], fallback: string[]): string[] {
  const source = items.length ? items : fallback
  return source.map((item) => `- ${item}`)
}

function numberedMarkdownList(items: string[]): string[] {
  return items.map((item, index) => `${index + 1}. ${item}`)
}


function recordValue(value: unknown): Record<string, any> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, any>
    : null
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function numberValue(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function resolveSnapshotVersionNo(snapshot: Record<string, any> | null): number | null {
  if (!snapshot) return null

  const direct = numberValue(
    snapshot.artifactVersionNo ??
      snapshot.uiContractVersionNo ??
      snapshot.versionNo ??
      snapshot.confirmedVersionNo ??
      snapshot.currentVersionNo,
  )

  if (direct) return direct

  const nested = recordValue(snapshot.snapshot) || recordValue(snapshot.uiContract) || recordValue(snapshot.artifact)
  return numberValue(
    nested?.artifactVersionNo ??
      nested?.uiContractVersionNo ??
      nested?.versionNo ??
      nested?.confirmedVersionNo,
  )
}

function childNodesByType(types: string[]): ModuleTreeNode[] {
  const children = props.selectedModule?.children || []
  return children.filter((child) => types.includes(resolveNodeTypeName(child)))
}

function resolveNodeTypeName(node: ModuleTreeNode | null | undefined): string {
  const extra = safeExtra(node)
  return String(node?.moduleType || node?.type || extra.nodeType || '').toUpperCase()
}

function safeExtra(node: ModuleTreeNode | null | undefined): Record<string, unknown> {
  const extra = node?.extra
  return extra && typeof extra === 'object' && !Array.isArray(extra) ? extra : {}
}

function stringValue(value: unknown): string {
  if (typeof value === 'string' && value.trim()) return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

function resolveInlineModuleRequirementText(extra: Record<string, unknown>): string {
  const direct = stringValue(
    extra.moduleRequirement ||
      extra.moduleRequirementText ||
      extra.requirementText ||
      extra.requirementSummary ||
      extra.summary ||
      extra.description,
  )

  if (direct) return direct

  const requirementContext = recordValue(
    extra.requirementContext ||
      extra.moduleContext ||
      extra.modulePrototypeContext,
  )

  return stringValue(
    requirementContext?.requirementText ||
      requirementContext?.summary ||
      requirementContext?.goal ||
      requirementContext?.description,
  )
}

function tryParseJson(value: unknown): unknown {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}


function a982RequirementContext(): Record<string, unknown> | null {
  const extra = safeExtra(props.selectedModule)
  return (
    recordValue(extra.requirementContext) ||
    recordValue(extra.requirement_context) ||
    recordValue(extra.moduleRequirement) ||
    recordValue(extra.module_requirement) ||
    null
  )
}

function a982NodeName(node: unknown): string {
  const value = recordValue(node)
  return stringValue(
    value?.moduleName ||
      value?.name ||
      value?.title ||
      value?.pageName ||
      value?.moduleKey ||
      value?.key ||
      '',
  )
}

function a982NodeType(node: unknown): string {
  const value = recordValue(node)
  return stringValue(value?.moduleType || value?.type || '').toUpperCase()
}

function a982NodeChildren(node: unknown): unknown[] {
  const value = recordValue(node)
  const children = value?.children
  return Array.isArray(children) ? children : []
}

function a982CollectDescendantNodes(node: unknown, targetType: string): unknown[] {
  const result: unknown[] = []
  const queue = [...a982NodeChildren(node)]

  while (queue.length) {
    const current = queue.shift()
    if (a982NodeType(current) === targetType) {
      result.push(current)
    }
    queue.push(...a982NodeChildren(current))
  }

  return result
}

function a982TextArray(value: unknown): string[] {
  const result: string[] = []
  a982CollectTextItems(value, result)
  return result
}

function a982CollectTextItems(value: unknown, target: string[]): void {
  if (value == null) return

  if (Array.isArray(value)) {
    value.forEach((item) => a982CollectTextItems(item, target))
    return
  }

  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    const label = stringValue(
      record.name ||
        record.title ||
        record.label ||
        record.summary ||
        record.description ||
        record.pageName ||
        record.featureName ||
        record.moduleName ||
        '',
    )
    if (label) target.push(label)
    return
  }

  const text = stringValue(value)
  if (!text) return

  text
    .split(/\n|；|;/)
    .map((item) => item.replace(/^[-*•\d.、\s]+/, '').trim())
    .filter(Boolean)
    .forEach((item) => target.push(item))
}

function a982UniqueText(items: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []

  for (const item of items) {
    const text = item.trim()
    if (!text) continue
    const key = text.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    result.push(text)
  }

  return result
}

function normalizeUiContractCandidate(value: unknown): UiContractContent | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const candidate = value as Record<string, any>
  const page = candidate.page
  if (!page || typeof page !== 'object') return null
  const componentTree = Array.isArray(page.componentTree) ? page.componentTree : []
  if (componentTree.length === 0) return null

  return {
    schemaVersion: candidate.schemaVersion || '1.0',
    artifactType: '交互原型',
    identity: candidate.identity || {},
    module: candidate.module || {},
    page: {
      ...page,
      pageName: page.pageName || page.title || nodeName.value,
      title: page.title || page.pageName || nodeName.value,
      description: page.description || page.summary || '',
      pageKind: page.pageKind || props.selectedModule?.pageKind || 'BUSINESS_PAGE',
      route: page.route || props.selectedModule?.routePath || '',
      layout: typeof page.layout === 'object' && page.layout ? page.layout : { type: 'CUSTOM_GRID' },
      componentTree,
    },
    routes: Array.isArray(candidate.routes) ? candidate.routes : [],
    pages: Array.isArray(candidate.pages) ? candidate.pages : [],
    interactions: Array.isArray(candidate.interactions) ? candidate.interactions : [],
    validationRules: Array.isArray(candidate.validationRules) ? candidate.validationRules : [],
    navigationTargets: Array.isArray(candidate.navigationTargets) ? candidate.navigationTargets : [],
    mockData: candidate.mockData || {},
    apiBindings: Array.isArray(candidate.apiBindings) ? candidate.apiBindings : [],
    developmentRules: candidate.developmentRules || {},
    analysisSummary: candidate.analysisSummary || '',
    mockScenarios: Array.isArray(candidate.mockScenarios) ? candidate.mockScenarios : [],
    pageStates: Array.isArray(candidate.pageStates) ? candidate.pageStates : [],
    featureCoverage: Array.isArray(candidate.featureCoverage) ? candidate.featureCoverage : [],
    qualitySummary: candidate.qualitySummary || undefined,
    assumptions: Array.isArray(candidate.assumptions) ? candidate.assumptions : [],
  } as unknown as UiContractContent
}

function flattenComponents(components: UiContractComponent[]): UiContractComponent[] {
  const result: UiContractComponent[] = []
  const visit = (items: UiContractComponent[]) => {
    for (const item of items || []) {
      if (!item?.componentId) continue
      result.push(item)
      visit(item.children || [])
    }
  }
  visit(components)
  return result
}


const a9PrototypeSaveConfirmVisible = ref(false)

const a9PrototypeSaveEditRecords = computed(() => {
  const draft = props.uiContractDraftContent as unknown as Record<string, unknown> | null
  const metadata = draft && typeof draft === 'object' && !Array.isArray(draft)
    ? draft.metadata as Record<string, unknown> | undefined
    : undefined
  const rawRecords = metadata && Array.isArray(metadata.prototypeLocalEdits)
    ? metadata.prototypeLocalEdits
    : []

  return rawRecords
    .map((item, index) => a9NormalizePrototypeSaveEditRecord(item, index))
    .filter((item) => item.instruction || item.changes.length > 0)
})

const a9PrototypeSaveChangeCount = computed(() => {
  const detailedCount = a9PrototypeSaveEditRecords.value.reduce(
    (total, record) => total + Math.max(1, record.changes.length),
    0,
  )
  return detailedCount || (props.prototypeEditDraftDirty ? 1 : 0)
})

const a9PrototypeSavePageTitle = computed(() => {
  const page = props.uiContractDraftContent?.page || uiContractObject.value?.page
  return String(page?.title || page?.pageName || nodeName.value || '当前页面')
})

function openA9PrototypeSaveConfirm() {
  if (!props.prototypeEditDraftDirty) {
    emit('save-prototype-draft')
    return
  }

  a9PrototypeSaveConfirmVisible.value = true
}

function cancelA9PrototypeSaveConfirm() {
  a9PrototypeSaveConfirmVisible.value = false
}

function confirmA9PrototypeSaveDraft() {
  a9PrototypeSaveConfirmVisible.value = false
  emit('save-prototype-draft')
}

function a9NormalizePrototypeSaveEditRecord(
  value: unknown,
  index: number,
): {
  key: string
  targetLabel: string
  instruction: string
  changes: string[]
} {
  const record = value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}

  const selection = record.selectionRef && typeof record.selectionRef === 'object' && !Array.isArray(record.selectionRef)
    ? record.selectionRef as Record<string, unknown>
    : {}

  const changes = Array.isArray(record.changes)
    ? record.changes.map((item) => String(item || '').trim()).filter(Boolean)
    : []

  const targetLabel = String(
    record.targetLabel ||
      selection.label ||
      selection.elementLabel ||
      selection.fieldKey ||
      selection.actionId ||
      selection.columnKey ||
      record.targetComponentId ||
      '原型元素',
  )

  return {
    key: String(record.editId || record.editedAt || `${index}-${targetLabel}`),
    targetLabel,
    instruction: String(record.instruction || '快捷编辑操作'),
    changes,
  }
}

</script>

<style scoped>
.a9-workspace {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: auto;
  padding: 24px;
  background: linear-gradient(180deg, #eef4ff 0%, #f8fafc 45%, #f8fafc 100%);
}

.a9-hero-card,
.a9-node-card,
.a9-empty-card {
  border: 1px solid #dbe4ef;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.a9-hero-card {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 24px 22px;
}

.a9-hero-main h2,
.a9-node-header h3 {
  margin: 6px 0;
  color: #0f172a;
  font-size: 28px;
  letter-spacing: -0.04em;
}

.a9-hero-main p,
.a9-node-header p,
.a9-section-card p,
.a9-info-card p,
.a9-prototype-ready span,
.a9-prototype-empty span,
.a9-backstage-grid span {
  margin: 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
}

.a9-eyebrow,
.a9-kind-pill {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(104px, 1fr));
  gap: 12px;
  min-width: 330px;
}

.a9-hero-metrics article {
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 16px;
  background: #f8fbff;
}

.a9-hero-metrics strong {
  display: block;
  color: #1d4ed8;
  font-size: 20px;
}

.a9-hero-metrics span,
.a9-child-strip span,
.a9-prototype-meta span {
  color: #64748b;
  font-size: 12px;
}

.a9-empty-card {
  padding: 24px;
}

.a9-node-card {
  display: grid;
  gap: 16px;
  padding: 22px;
}

.a9-node-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.a9-node-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.a9-primary-button,
.a9-secondary-button {
  border: 1px solid #2563eb;
  border-radius: 999px;
  padding: 12px 18px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 800;
  cursor: pointer;
}

.a9-secondary-button {
  background: #ffffff;
  color: #2563eb;
}

.a9-primary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.a9-section-card,
.a9-info-card,
.a9-backstage-details {
  border: 1px solid #dbe4ef;
  border-radius: 17px;
  padding: 16px;
  background: #ffffff;
}

.a9-section-card h4,
.a9-info-card h4 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 15px;
}

.a9-two-column {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.a9-section-title-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.a9-status-ready,
.a9-status-waiting {
  align-self: flex-start;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 900;
}

.a9-status-ready {
  background: #dcfce7;
  color: #166534;
}

.a9-status-waiting {
  background: #fef3c7;
  color: #92400e;
}

.a9-prototype-ready,
.a9-prototype-empty {
  display: grid;
  gap: 7px;
  border: 1px solid #bfdbfe;
  border-radius: 15px;
  padding: 14px;
  background: #eff6ff;
}

.a9-prototype-empty {
  border-color: #e2e8f0;
  background: #f8fafc;
}

.a9-prototype-empty.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.a9-prototype-ready strong,
.a9-prototype-empty strong,
.a9-backstage-grid strong {
  color: #0f172a;
  font-size: 14px;
}

.a9-prototype-meta,
.a9-child-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.a9-prototype-meta span,
.a9-child-strip {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(255, 255, 255, 0.86);
}

.a9-inline-prototype-frame {
  overflow: hidden;
  min-height: 640px;
  margin-top: 14px;
  border: 1px solid #dbeafe;
  border-radius: 20px;
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.a9-inline-prototype-frame :deep(.runtime-shell) {
  min-height: 640px;
}

.a9-backstage-details summary {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.a9-backstage-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.a9-backstage-grid article {
  display: grid;
  gap: 6px;
  border-radius: 14px;
  padding: 13px;
  background: #f8fafc;
}

@media (max-width: 980px) {
  .a9-hero-card,
  .a9-node-header {
    display: grid;
  }

  .a9-hero-metrics,
  .a9-two-column,
  .a9-backstage-grid {
    grid-template-columns: 1fr;
    min-width: 0;
  }
}


.a9-prototype-draft-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0;
  padding: 12px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
  color: #1e3a8a;
}

.a9-prototype-draft-banner strong {
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.a9-prototype-draft-banner span {
  color: #1d4ed8;
  font-size: 13px;
  line-height: 1.6;
}


.a9-prototype-draft-content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prototype-draft-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.a9-primary-button.compact,
.a9-secondary-button.compact {
  min-height: 32px;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}


.a9-secondary-button.active {
  border-color: #0ea5e9;
  background: #e0f2fe;
  color: #0369a1;
}

.a9-prototype-inspect-banner {
  display: grid;
  gap: 5px;
  margin: 12px 0;
  border: 1px solid #7dd3fc;
  border-radius: 16px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #e0f2fe 0%, #ffffff 100%);
  color: #075985;
}

.a9-prototype-inspect-banner strong {
  color: #0369a1;
  font-size: 13px;
  font-weight: 950;
}

.a9-prototype-inspect-banner span {
  color: #0c4a6e;
  font-size: 13px;
  line-height: 1.65;
}


.a9-prototype-locked-target-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 12px 0;
  border: 1px solid rgba(37, 99, 235, 0.22);
  border-radius: 18px;
  padding: 13px 14px;
  background:
    radial-gradient(circle at 8% 20%, rgba(37, 99, 235, 0.14), transparent 30%),
    linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.09);
}

.a9-prototype-locked-target-card div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prototype-locked-target-card strong {
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 950;
}

.a9-prototype-locked-target-card span {
  overflow: hidden;
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-prototype-locked-target-card em {
  color: #475569;
  font-size: 12px;
  font-style: normal;
  line-height: 1.55;
}

.a9-prototype-locked-target-card button {
  flex: 0 0 auto;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 8px 12px;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}


.a9-prototype-quick-edit-card {
  display: grid;
  gap: 10px;
  margin: 12px 0;
  border: 1px solid rgba(14, 165, 233, 0.24);
  border-radius: 18px;
  padding: 13px 14px;
  background:
    radial-gradient(circle at 10% 10%, rgba(14, 165, 233, 0.12), transparent 32%),
    linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
}

.a9-prototype-quick-edit-head {
  display: grid;
  gap: 4px;
}

.a9-prototype-quick-edit-head strong {
  color: #0369a1;
  font-size: 13px;
  font-weight: 950;
}

.a9-prototype-quick-edit-head span {
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.a9-prototype-quick-edit-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.a9-prototype-quick-edit-actions button {
  border: 1px solid #bae6fd;
  border-radius: 999px;
  padding: 7px 11px;
  background: #ffffff;
  color: #0369a1;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(14, 165, 233, 0.09);
}

.a9-prototype-quick-edit-actions button:hover {
  border-color: #38bdf8;
  background: #e0f2fe;
}



.a9-prototype-edit-explain-card {
  display: grid;
  gap: 12px;
  margin: 12px 0;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 18px;
  padding: 14px;
  background:
    radial-gradient(circle at 8% 10%, rgba(37, 99, 235, 0.10), transparent 32%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.a9-prototype-edit-explain-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.a9-prototype-edit-explain-head div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prototype-edit-explain-head strong {
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 950;
}

.a9-prototype-edit-explain-head span {
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.a9-prototype-edit-explain-head em {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-style: normal;
  font-weight: 950;
}

.a9-prototype-edit-explain-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.a9-prototype-edit-explain-list li {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.92);
}

.a9-prototype-edit-index {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
}

.a9-prototype-edit-body {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.a9-prototype-edit-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.a9-prototype-edit-title-row strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-prototype-edit-title-row span {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
}

.a9-prototype-edit-body p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.a9-prototype-edit-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.a9-prototype-edit-tags span {
  border-radius: 999px;
  padding: 4px 8px;
  background: #f1f5f9;
  color: #334155;
  font-size: 11px;
  font-weight: 850;
}




.a9-prototype-save-confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(6px);
}

.a9-prototype-save-confirm-dialog {
  width: min(760px, 94vw);
  max-height: 88vh;
  overflow: auto;
  border: 1px solid rgba(191, 219, 254, 0.82);
  border-radius: 26px;
  padding: 20px;
  background: #ffffff;
  box-shadow: 0 34px 90px rgba(15, 23, 42, 0.28);
}

.a9-prototype-save-confirm-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 16px;
}

.a9-prototype-save-confirm-header p {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-prototype-save-confirm-header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  letter-spacing: -0.03em;
}

.a9-prototype-save-confirm-header span,
.a9-prototype-save-confirm-body p,
.a9-prototype-save-edit-list span,
.a9-prototype-save-confirm-summary span {
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.a9-prototype-save-confirm-header button {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 26px;
  line-height: 1;
  cursor: pointer;
}

.a9-prototype-save-confirm-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.a9-prototype-save-confirm-summary article {
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 13px;
  background: #eff6ff;
}

.a9-prototype-save-confirm-summary strong {
  display: block;
  overflow: hidden;
  color: #1d4ed8;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-prototype-save-confirm-body {
  display: grid;
  gap: 12px;
}

.a9-prototype-save-confirm-body h4 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.a9-prototype-save-edit-list {
  display: grid;
  gap: 10px;
}

.a9-prototype-save-edit-list article {
  display: grid;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px 13px;
  background: #f8fafc;
}

.a9-prototype-save-edit-list strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.a9-prototype-save-edit-list ul {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.a9-prototype-save-edit-list li {
  border-radius: 999px;
  padding: 5px 9px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 850;
}

.a9-prototype-save-empty-note {
  border: 1px dashed #bfdbfe;
  border-radius: 16px;
  padding: 12px;
  background: #f8fbff;
}

.a9-prototype-save-confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  border-top: 1px solid #e2e8f0;
  padding-top: 16px;
}

@media (max-width: 760px) {
  .a9-prototype-save-confirm-summary {
    grid-template-columns: 1fr;
  }

  .a9-prototype-save-confirm-footer {
    display: grid;
  }
}


.a9-prototype-version-status-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 12px 0 14px;
}

.a9-prototype-version-status-item {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 14px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.a9-prototype-version-status-item div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.a9-prototype-version-status-item span {
  color: #64748b;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-prototype-version-status-item strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-prototype-version-status-item p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.a9-prototype-version-status-item em {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
  white-space: nowrap;
}

.a9-prototype-version-status-item.state-ready {
  border-color: #bbf7d0;
  background: linear-gradient(135deg, #f0fdf4 0%, #ffffff 100%);
}

.a9-prototype-version-status-item.state-ready em {
  background: #dcfce7;
  color: #166534;
}

.a9-prototype-version-status-item.state-warning {
  border-color: #fde68a;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 100%);
}

.a9-prototype-version-status-item.state-warning em {
  background: #fef3c7;
  color: #92400e;
}

.a9-prototype-version-status-item.state-blocked {
  border-color: #fecaca;
  background: linear-gradient(135deg, #fef2f2 0%, #ffffff 100%);
}

.a9-prototype-version-status-item.state-blocked em {
  background: #fee2e2;
  color: #991b1b;
}

.a9-prototype-version-status-item.state-muted em {
  background: #f1f5f9;
  color: #64748b;
}

@media (max-width: 1180px) {
  .a9-prototype-version-status-panel {
    grid-template-columns: 1fr;
  }
}



/* A9_7_6_COMPACT_HEADER_REDESIGN */
.a9-workspace {
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 18px 20px;
}

.a9-hero-card {
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  border-radius: 18px;
  padding: 13px 16px;
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.08), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 251, 255, 0.96) 100%);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.a9-hero-main {
  min-width: 0;
}

.a9-hero-main h2 {
  margin: 2px 0 4px;
  font-size: 24px;
  line-height: 1.16;
  letter-spacing: -0.035em;
}

.a9-hero-main p:not(.a9-eyebrow) {
  max-width: 740px;
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-hero-main .a9-eyebrow {
  margin: 0;
  color: #334155;
  font-size: 11px;
  letter-spacing: 0.12em;
}

.a9-hero-metrics {
  flex: 0 0 auto;
  grid-template-columns: repeat(3, minmax(86px, 1fr));
  gap: 8px;
  min-width: 0;
  width: min(356px, 42vw);
}

.a9-hero-metrics article {
  min-height: 56px;
  border-color: rgba(147, 197, 253, 0.58);
  border-radius: 14px;
  padding: 9px 11px;
  background:
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.05);
}

.a9-hero-metrics strong {
  overflow: hidden;
  font-size: 17px;
  line-height: 1.16;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-hero-metrics span {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.2;
}

.a9-node-card {
  gap: 14px;
  border-radius: 20px;
  padding: 18px;
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.07);
}

.a9-node-header h3 {
  margin: 4px 0 3px;
  font-size: 24px;
}

.a9-node-header p {
  font-size: 13px;
  line-height: 1.45;
}

.a9-section-card {
  padding: 14px;
}

@media (max-width: 1180px) {
  .a9-hero-card {
    display: grid;
  }

  .a9-hero-main p:not(.a9-eyebrow) {
    white-space: normal;
  }

  .a9-hero-metrics {
    width: 100%;
  }
}

/* A9_7_7_PROTOTYPE_STUDIO_ENTRY */
.a9-prototype-studio-entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 20px;
  padding: 16px;
  background:
    radial-gradient(circle at 6% 12%, rgba(37, 99, 235, 0.13), transparent 30%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.07);
}

.a9-prototype-studio-entry-main {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.a9-prototype-studio-entry-main p {
  margin: 0;
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-prototype-studio-entry-main strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 950;
}

.a9-prototype-studio-entry-main span {
  max-width: 760px;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.a9-prototype-studio-entry-actions {
  display: inline-flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 9px;
}

@media (max-width: 860px) {
  .a9-prototype-studio-entry-card {
    display: grid;
  }

  .a9-prototype-studio-entry-actions {
    justify-content: flex-start;
  }
}


/* A9_8_1_EMPTY_PROJECT_LANDING */
.a9-empty-project-card {
  display: grid;
  gap: 18px;
  padding: 26px;
}

.a9-empty-project-hero {
  max-width: 760px;
}

.a9-empty-eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.a9-empty-project-hero h3,
.a9-empty-selection-card h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 24px;
  letter-spacing: -0.03em;
}

.a9-empty-project-hero p,
.a9-empty-selection-card p {
  margin: 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.8;
}

.a9-start-option-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.a9-start-option-grid article {
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.05);
}

.a9-start-option-grid article span {
  display: inline-flex;
  width: fit-content;
  margin-bottom: 10px;
  border-radius: 999px;
  padding: 4px 9px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.a9-start-option-grid article strong {
  display: block;
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 16px;
}

.a9-start-option-grid article p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.a9-empty-example-card {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  padding: 13px 15px;
  background: #eff6ff;
  color: #1e3a8a;
}

.a9-empty-example-card strong {
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 900;
}

.a9-empty-example-card span {
  color: #1e40af;
  font-size: 13px;
  line-height: 1.6;
}

.a9-empty-selection-card {
  padding: 24px;
}

@media (max-width: 1180px) {
  .a9-start-option-grid {
    grid-template-columns: 1fr;
  }

  .a9-empty-example-card {
    align-items: flex-start;
    flex-direction: column;
  }
}


.a9-module-requirement-card {
  display: grid;
  gap: 14px;
}

.a9-module-requirement-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.a9-module-requirement-heading h4 {
  margin: 0;
}

.a9-module-requirement-heading p,
.a9-module-requirement-body p,
.a9-module-requirement-block p {
  margin: 4px 0 0;
  color: #52627a;
  line-height: 1.75;
}

.a9-module-requirement-body {
  display: grid;
  gap: 6px;
}

.a9-module-requirement-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.a9-module-requirement-block {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.96));
  padding: 14px 16px;
}

.a9-module-requirement-block h5 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #0f172a;
}

.a9-module-requirement-block ul {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  line-height: 1.8;
}

.a9-module-requirement-block li + li {
  margin-top: 2px;
}

@media (max-width: 980px) {
  .a9-module-requirement-grid {
    grid-template-columns: 1fr;
  }
}


.a9-module-document-card {
  display: grid;
  gap: 14px;
  padding: 18px 20px;
}

.a9-module-document-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 12px;
}

.a9-module-document-heading h4 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
}

.a9-module-document-heading p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.a9-module-doc-version {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
}

.a9-module-document-card :deep(.module-prd-view) {
  display: grid;
  gap: 12px;
}

.a9-module-document-card :deep(.prd-view-toolbar) {
  margin: 0;
}

.a9-module-document-card :deep(.document-preview) {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.85;
}

.a9-module-document-card :deep(.prd-document h1) {
  margin: 0 0 18px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.25;
  letter-spacing: -0.035em;
}

.a9-module-document-card :deep(.prd-document h2) {
  margin: 24px 0 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 18px;
  color: #0f172a;
  font-size: 18px;
}

.a9-module-document-card :deep(.prd-document p),
.a9-module-document-card :deep(.prd-document li) {
  color: #334155;
}

.a9-module-document-card :deep(.prd-document ul),
.a9-module-document-card :deep(.prd-document ol) {
  padding-left: 20px;
}


</style>
