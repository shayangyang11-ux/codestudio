<!-- A9.10.14.4.3.10_CURRENT_FILE_CLEANUP -->
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

          <span
            v-if="showPrototypeRequirementUpdateBanner && !prototypeEditDraftDirty"
            class="a9-prototype-impact-pill"
            :title="prototypeRequirementUpdateMessage"
          >
            需求已更新，建议重新生成本页原型
          </span>

          <button
            v-if="isPage"
            type="button"
            class="a9-primary-button"
            :disabled="isGeneratingUiContract"
            @click="emit('generate-ui-contract')"
          >
            {{ isGeneratingUiContract ? '正在生成原型...' : prototypeButtonText }}
          </button>


          <span
            v-if="isModule && moduleRequirementReaderArtifact?.versionNo"
            class="a9-header-prd-version-pill"
          >
            v{{ moduleRequirementReaderArtifact.versionNo }}
          </span>

          <button
            v-if="isModule && moduleAppContract"
            type="button"
            class="a9-primary-button compact a9-header-module-preview-button"
            @click="openModuleAppOverlay"
          >
            查看完整模块预览
          </button>


        </div>
      </header>
      <section v-if="showModuleRequirementDocument" class="a9-section-card a9-module-document-card">
        <!-- A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION -->
        <div
          v-if="showPrdTreeCompletionAction"
          class="a9-prd-tree-completion-banner"
        >
          <div>
            <strong>模块需求已准备好</strong>
            <span>确认 PRD 内容后，可一键生成该模块下的业务能力和页面入口。生成后此按钮会自动消失。</span>
          </div>
          <button
            type="button"
            class="a9-primary-button compact"
            @click="emit('complete-module-tree-from-prd')"
          >
            根据模块需求生成业务能力和页面
          </button>
        </div>

        <ModulePrdView
          :artifact="moduleRequirementReaderArtifact"
          :markdown="moduleRequirementMarkdownForReader"
          :manual-edit-saving="manualEditSaving"
          :allow-create-from-markdown="true"
          @save-manual-edit="emit('save-manual-artifact-edit', $event)"
        />
      </section>

      <!-- A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR: non-terminal modules keep a simple overview instead of an empty workspace. -->
      <section v-if="isModule && !showModuleRequirementDocument" class="a9-section-card a9-module-overview-card">
        <div class="a9-module-overview-head">
          <div>
            <p>Module Overview</p>
            <h4>{{ moduleSectionTitle }}</h4>
          </div>
          <span>{{ childSummary }} 个下级节点</span>
        </div>

        <div class="a9-module-overview-copy">
          <p v-for="paragraph in moduleRequirementParagraphs" :key="paragraph">
            {{ paragraph }}
          </p>
        </div>

        <div class="a9-module-overview-grid">
          <article
            v-for="block in moduleRequirementBlocks"
            :key="block.title"
          >
            <strong>{{ block.title }}</strong>
            <ul v-if="block.items.length">
              <li v-for="item in block.items" :key="item">{{ item }}</li>
            </ul>
            <p v-else>{{ block.emptyText }}</p>
          </article>
        </div>
      </section>

      <!-- A9.10.13.0.1 MODULE APP PROTOTYPE BEGIN -->
      <!-- A9.10.13.0.7 MODULE APP INTEGRATED PROTOTYPE STUDIO BEGIN -->
      <ModuleAppPrototypeStudioOverlay
        v-if="moduleAppContract"
        :visible="moduleAppOverlayVisible"
        :module-title="moduleAppTitle"
        :module-app-contract="moduleAppEffectiveContract"
        :navigation="moduleAppNavigation"
        :page-navigator-items="moduleAppPageNavigatorPreviewItems"
        :active-page="activeModuleAppPage"
        :active-page-route="activeModuleAppPageRoute"
        :active-contract="activeModuleAppUiContract"
        :project-theme-profile="projectThemeProfile || null"
        :focused-component-id="moduleAppFocusedComponentId"
        :focused-component-label="moduleAppFocusedComponentLabel"
        :focused-element-selection="moduleAppFocusedElementSelection"
        :prototype-edit-draft-dirty="moduleAppAnyDraftDirty"
        :prototype-edit-draft-summary="moduleAppPrototypeDraftSummary"
        :saving="moduleAppPrototypeSaving"
        :loading="moduleAppUiContractLoading"
        :error="moduleAppUiContractError"
        :prototype-size-label="moduleAppPrototypeSizeLabel"
        :prototype-component-count="moduleAppPrototypeComponentCount"
        :navigate-request="handleModuleAppRuntimeNavigate"
        @close="closeModuleAppOverlay"
        @navigate-page="openModuleAppPageByNav"
        @select-component="handleModuleAppStudioSelectComponent"
        @clear-selection="handleModuleAppStudioClearSelection"
        @send-instruction="handleModuleAppStudioInstruction"
        @save-draft="handleModuleAppStudioSaveDraft"
        @confirm-snapshot="handleModuleAppPrototypeConfirmSnapshot"
        @discard-draft="handleModuleAppStudioDiscardDraft"
      />
      <!-- A9.10.13.0.7 MODULE APP INTEGRATED PROTOTYPE STUDIO END -->
      <!-- A9.10.13.0.1 MODULE APP PROTOTYPE END -->

      <section v-if="false && isModule" class="a9-two-column" data-a9-hidden="A9.10.14.4.3.15.2.6_PRD_UNIFIED_UI_TREE_COLLAPSE">
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
            <span>{{ prototypePublicationLabel }}</span>
            <span v-if="prototypeEditDraftDirty">本地预览未保存</span>
            <span>{{ prototypeSizeLabel }}</span>
            <span>{{ prototypeComponentCount }} 个组件</span>
          </div>
        </div>

        <div v-else class="a9-prototype-empty">
          <strong>还没有交互原型</strong>
          <span>点击“生成交互原型”，系统会根据页面需求生成可预览、可交互、可继续调整的原型。</span>
        </div>


        <section v-if="false && isPage && hasPrototype" class="a9-prototype-version-status-panel">
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
          :prototype-publication-state="prototypePublicationState"
          :prototype-publication-label="prototypePublicationLabel"
          :prototype-version-label="prototypeVersionLabel"
          :can-publish-prototype="canPublishPrototype"
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

      <details v-if="false" class="a9-backstage-details" data-a9-hidden="A9.10.14.4.3.15.2.6_PRD_UNIFIED_UI_TREE_COLLAPSE">
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
              本次会把当前 PAGE 的本地原型草稿保存为新的交互原型版本；开发代码只会使用模块汇总原型确认后的开发基线。
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
            <strong>{{ a9PrototypeSaveRequirementSync.badge }}</strong>
            <span>需求同步</span>
          </article>
          <article>
            <strong>未保存</strong>
            <span>当前状态</span>
          </article>
        </section>

        <section class="a9-prototype-save-confirm-body">
          <h4>保存前确认</h4>
          <p>
            保存后会生成新的 PAGE_UI_CONTRACT 版本；模块汇总原型会读取该版本，确认模块开发基线后 Development Agent 才会使用。
          </p>

          <section
            class="a9-prototype-save-sync-card"
            :class="a9PrototypeSaveRequirementSyncCardClass"
          >
            <div>
              <span>{{ a9PrototypeSaveRequirementSync.badge }}</span>
              <strong>{{ a9PrototypeSaveRequirementSync.title }}</strong>
              <p>{{ a9PrototypeSaveRequirementSync.description }}</p>
            </div>

            <ul v-if="a9PrototypeSaveRequirementSync.targetArtifactTypes.length">
              <li
                v-for="target in a9PrototypeSaveRequirementSync.targetArtifactTypes"
                :key="target"
              >
                {{ target }}
              </li>
            </ul>
            <ul v-else>
              <li
                v-for="item in a9PrototypeSaveRequirementSync.visualChangeLabels"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>

            <em>{{ a9PrototypeSaveRequirementSync.reviewNote }}</em>
          </section>

          <div v-if="a9PrototypeSaveEditRecords.length" class="a9-prototype-save-edit-list">
            <article
              v-for="record in a9PrototypeSaveEditRecords"
              :key="record.key"
            >
              <div class="a9-prototype-save-edit-title">
                <div>
                  <strong>{{ record.targetLabel }}</strong>
                  <span>{{ record.instruction }}</span>
                </div>
                <em :class="record.requirementSyncClass">
                  {{ record.requirementSyncLabel }}
                </em>
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
// A9.10.14.4.3.7.1_ROBUST_PRD_IMPACT_FRONTEND_FIX
// A9.10.14.4.3.4_PRD_IMPACT_REGENERATE_CHAIN
import { computed, ref, watch, nextTick } from 'vue'
import { getModuleDisplayName, getModuleStableKey } from '../api'
import { fetchCurrentModuleArtifact, saveManualArtifactEdit } from '../api/artifactApi'
import type { ArtifactResponse, ManualArtifactEditPayload, ModuleTreeNode } from '../types/codemind'
import type { ProjectThemeProfile } from '../types/themeProfile'
import type { UiContractComponent, UiContractContent } from '../types/uiContract'
import {
  isFeatureNode,
  isPageNode,
  resolveRequirementNodeType,
} from '../domain/requirementNodePolicy'
import PrototypeStudioOverlay from './PrototypeStudioOverlay.vue'
import ModuleAppPrototypeStudioOverlay from './ModuleAppPrototypeStudioOverlay.vue'
import UiContractPreviewRuntime from './UiContractPreviewRuntime.vue'
import ModulePrdView from './ModulePrdView.vue'
import { applyPrototypeLocalEdit, isPrototypeLocalEditInstruction } from '../domain/uiContractPrototypeLocalEdit'

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
    workspaceId?: string
    projectCode?: string
    moduleTree?: ModuleTreeNode[]
    isGeneratingFullChain?: boolean
    isGeneratingUiContract?: boolean
    modulePrdArtifact?: ArtifactResponse | null
    prdArtifact?: ArtifactResponse | null
    modulePrdMarkdown?: string
    pageSpecArtifact?: ArtifactResponse | null
    uiContractArtifact?: ArtifactResponse | null
    uiContractReadiness?: Record<string, unknown> | null
    uiContractReadinessLoading?: boolean
    uiContractReadinessError?: string
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
    requirementImpactPageIds?: string[]
  }>(),
  {
    moduleTree: () => [],
    workspaceId: '',
    projectCode: '',
    isGeneratingFullChain: false,
    isGeneratingUiContract: false,
    modulePrdArtifact: null,
    prdArtifact: null,
    modulePrdMarkdown: '',
    pageSpecArtifact: null,
    uiContractArtifact: null,
    uiContractReadiness: null,
    uiContractReadinessLoading: false,
    uiContractReadinessError: '',
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
    requirementImpactPageIds: () => [],
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
  'complete-module-tree-from-prd': []
}>()


const prototypeInspectMode = ref(false)
const prototypeStudioVisible = ref(false)
const prototypeStudioStartInspectMode = ref(false)
// A9.9.1 safe prop aliases for prototype draft status.
// Some computed/template branches refer to prototypeEditDraftDirty directly;
// keep an explicit local binding to avoid runtime ReferenceError.
const prototypeEditDraftDirty = computed(() => Boolean(props.prototypeEditDraftDirty))
const prototypeEditDraftSummary = computed(() => props.prototypeEditDraftSummary || '')


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
    closeModuleAppOverlay()
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

// A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION
const showPrdTreeCompletionAction = computed(() => {
  const node = props.selectedModule
  if (!node || resolveRequirementNodeType(node) !== 'MODULE') {
    return false
  }

  const children = Array.isArray(node.children) ? node.children : []
  const hasChildModule = children.some((child) => resolveRequirementNodeType(child) === 'MODULE')
  if (hasChildModule) {
    return false
  }

  const alreadyCompleted = children.some((child) => {
    const childType = resolveRequirementNodeType(child)
    return childType === 'FEATURE' || childType === 'PAGE'
  })

  if (alreadyCompleted) {
    return false
  }

  const artifactContent = props.modulePrdArtifact?.content || props.prdArtifact?.content
  const hasStructuredContent =
    typeof artifactContent === 'object' &&
    artifactContent !== null &&
    !Array.isArray(artifactContent)

  return Boolean(hasStructuredContent || props.modulePrdMarkdown.trim())
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

// A9.10.14.4.3.6_PRD_IMPACT_BANNER_UI_INTEGRITY_FIX
// Do not expose PAGE_SPEC / NEEDS_REVIEW to users. A PAGE only needs to know
// whether its visible prototype may be older than the module PRD.
const showPrototypeRequirementUpdateBanner = computed(() => {
  if (!isPage.value || !hasPrototype.value) {
    return false
  }

  // A9.10.14.4.3.15.4.3.1_REQUIREMENT_IMPACT_PILL_SOURCE
  // Prefer the explicit PAGE impact marker from ProjectWorkspacePage. This avoids
  // showing a heavy technical panel or a duplicate regenerate button on every page.
  const selectedPageId = String(props.selectedModule?.id || '')
  const markedByRecentRequirementChange = Boolean(
    selectedPageId && (props.requirementImpactPageIds || []).includes(selectedPageId),
  )

  if (markedByRecentRequirementChange) {
    return true
  }

  const uiArtifact = props.uiContractArtifact as Record<string, unknown> | null
  const prdArtifact = effectiveModulePrdArtifact.value as Record<string, unknown> | null

  if (artifactStatusNeedsRegeneration(uiArtifact)) {
    return true
  }

  const prdTime = artifactUpdatedMillis(prdArtifact)
  const uiTime = artifactUpdatedMillis(uiArtifact)

  return Boolean(prdTime && (!uiTime || prdTime > uiTime))
})

const prototypeRequirementUpdateMessage = computed(() => {
  const pageName = nodeName.value || '当前页面'
  return `模块 PRD 保存了新版本，${pageName} 的筛选条件、表格字段、操作按钮、页面跳转或状态规则可能需要同步。点击重新生成即可，系统会在后台先处理页面规范再更新交互原型。`
})

function artifactStatusNeedsRegeneration(artifact: Record<string, unknown> | null | undefined): boolean {
  const status = String(
    artifact?.status ||
      artifact?.reviewStatus ||
      artifact?.lifecycleStatus ||
      '',
  ).trim().toUpperCase()

  return [
    'NEEDS_REVIEW',
    'STALE',
    'OUTDATED',
    'IMPACTED',
    'DIRTY',
    'INVALIDATED',
  ].includes(status)
}

function artifactUpdatedMillis(artifact: Record<string, unknown> | null | undefined): number {
  if (!artifact) return 0

  for (const key of [
    'updatedAt',
    'updated_at',
    'modifiedAt',
    'modified_at',
    'createdAt',
    'created_at',
    'generatedAt',
    'generated_at',
  ]) {
    const raw = artifact[key]
    if (!raw) continue
    const time = new Date(String(raw)).getTime()
    if (Number.isFinite(time) && time > 0) {
      return time
    }
  }

  return 0
}
const prototypeArtifactStatus = computed(() =>
  String((props.uiContractArtifact as any)?.status || '').trim().toUpperCase(),
)
const showPrototypeStaleFromRequirementBanner = computed(() =>
  isPage.value &&
    hasPrototype.value &&
    [
      'NEEDS_REVIEW',
      'STALE',
      'OUTDATED',
      'IMPACTED',
      'DIRTY',
      'INVALIDATED',
    ].includes(prototypeArtifactStatus.value),
)

// A9.10.13.0.1 MODULE APP PROTOTYPE BEGIN
const activeModuleAppPageKey = ref('')
const moduleAppOverlayVisible = ref(false)
const currentModuleAppArtifact = ref<ArtifactResponse | null>(null)
const currentModuleAppArtifactLoading = ref(false)

function openModuleAppOverlay() {
  if (!moduleAppContract.value) {
    return
  }
  moduleAppOverlayVisible.value = true
  moduleAppUiContractError.value = ''
  void loadCurrentModuleAppContractArtifact()
  // A9.10.13.3: 模块汇总草稿必须实时读取 PAGE_UI_CONTRACT 当前版本；
  // 打开模块汇总时清空旧缓存，避免 PAGE 层保存后汇总仍显示旧页面。
  moduleAppUiContractArtifactCache.value = {}
  void loadActiveModuleAppUiContract()
  if (typeof document !== 'undefined') {
    document.body.classList.add('a9-module-app-overlay-open')
  }
}

function closeModuleAppOverlay() {
  moduleAppOverlayVisible.value = false
  if (typeof document !== 'undefined') {
    document.body.classList.remove('a9-module-app-overlay-open')
  }
}


const moduleAppContract = computed<Record<string, any> | null>(() => {
  if (!isModule.value || !props.selectedModule) return null

  const artifactSource = normalizeModuleAppContractRawSource(
    currentModuleAppArtifact.value?.content ||
      (currentModuleAppArtifact.value as any)?.contentMarkdown,
  )
  if (artifactSource) {
    return normalizeModuleAppContractSource(
      artifactSource,
      'MODULE_APP_CONTRACT_CURRENT_ARTIFACT',
    )
  }

  return normalizeModuleAppContractSource(
    resolveModuleAppContractCompatSource(),
    'MODULE_PROTOTYPE_MAP_COMPAT',
  )
})

function resolveModuleAppContractCompatSource(): Record<string, any> | null {
  const extra = safeExtra(props.selectedModule)
  const direct = recordValue(extra.moduleAppContract) || recordValue(extra.module_app_contract)
  const legacy = recordValue(extra.modulePrototypeMap) || recordValue(extra.module_prototype_map)
  const requirementContext = recordValue(extra.requirementContext) || recordValue(extra.requirement_context)
  const nestedLegacy = recordValue(requirementContext?.modulePrototypeMap) ||
    recordValue(requirementContext?.module_prototype_map)

  return direct || legacy || nestedLegacy || null
}

function normalizeModuleAppContractRawSource(value: unknown): Record<string, any> | null {
  const direct = recordValue(value)
  if (direct) {
    return direct
  }

  if (typeof value === 'string' && value.trim()) {
    return recordValue(tryParseJson(value))
  }

  return null
}

function normalizeModuleAppContractSource(
  source: Record<string, any> | null,
  sourceLabel: string,
): Record<string, any> | null {
  if (!source) return null

  const pages = normalizeModuleAppPages(arrayValue(source.pages), source)
  if (!pages.length) return null

  const navigationEdges = normalizeModuleAppEdges(arrayValue(source.navigationEdges || source.navigation_edges))
  const navigation = normalizeModuleAppNavigation(source, pages)
  const appShell = recordValue(source.appShell) || recordValue(source.app_shell) || {}
  const artifact = sourceLabel === 'MODULE_APP_CONTRACT_CURRENT_ARTIFACT'
    ? currentModuleAppArtifact.value
    : null

  return {
    schemaVersion: source.schemaVersion || source.schema_version || '1.0',
    artifactType: source.artifactType || source.artifact_type || 'MODULE_APP_CONTRACT',
    artifactId: artifact?.artifactId || source.artifactId || source.artifact_id || null,
    artifactVersionNo: artifact?.versionNo || source.artifactVersionNo || source.artifact_version_no || null,
    current: artifact ? artifact.current !== false : source.current !== false,
    moduleKey: source.moduleKey || source.module_key || nodeKey.value,
    moduleName: source.moduleName || source.module_name || nodeName.value,
    appShell: {
      ...appShell,
      layout: appShell.layout || 'sidebar',
      title: appShell.title || source.moduleName || source.module_name || nodeName.value,
      navigation,
    },
    pages,
    navigationEdges,
    source: source.source || sourceLabel,
  }
}

watch(
  () => [
    props.workspaceId || '',
    props.projectCode || '',
    props.selectedModule?.id || '',
    isModule.value ? 'MODULE' : 'NON_MODULE',
  ].join('|'),
  () => {
    void loadCurrentModuleAppContractArtifact()
  },
  { immediate: true },
)

async function loadCurrentModuleAppContractArtifact(): Promise<ArtifactResponse | null> {
  currentModuleAppArtifact.value = null

  if (!isModule.value || !props.selectedModule?.id || !props.workspaceId || !props.projectCode) {
    return null
  }

  currentModuleAppArtifactLoading.value = true

  try {
    const artifact = await fetchCurrentModuleArtifact(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      'MODULE_APP_CONTRACT',
    )

    const source = normalizeModuleAppContractRawSource(artifact?.content) ||
      normalizeModuleAppContractRawSource((artifact as any)?.contentMarkdown)

    currentModuleAppArtifact.value = source ? artifact : null
    return currentModuleAppArtifact.value
  } catch {
    currentModuleAppArtifact.value = null
    return null
  } finally {
    currentModuleAppArtifactLoading.value = false
  }
}

// A9.10.13.0.7 MODULE APP INTEGRATED STATE BEGIN
const moduleAppShellDraftContent = ref<Record<string, any> | null>(null)
const moduleAppShellDraftBaseContent = ref<Record<string, any> | null>(null)
const moduleAppShellDraftDirty = ref(false)
const moduleAppDraftContractCache = ref<Record<string, UiContractContent | null>>({})
const moduleAppFocusedComponentId = ref<string | null>(null)
const moduleAppFocusedElementSelection = ref<PrototypeElementSelection | null>(null)
const moduleAppPrototypeSaving = ref(false)
const moduleAppLastEditSummary = ref('')

const moduleAppEffectiveContract = computed<Record<string, any> | null>(() =>
  moduleAppShellDraftContent.value || moduleAppContract.value,
)

// A9.10.14.4.3.15.4.6.2.1:
// Drafts belong to one workspace/project/module context only.
// Never allow a PAGE draft from a previous selected module to leak into the next one.
watch(
  () => [
    props.workspaceId || '',
    props.projectCode || '',
    props.selectedModule?.id || '',
  ].join('::'),
  (nextContext, previousContext) => {
    if (!previousContext || nextContext === previousContext) {
      return
    }

    moduleAppDraftContractCache.value = {}
    moduleAppShellDraftContent.value = null
    moduleAppShellDraftBaseContent.value = null
    moduleAppShellDraftDirty.value = false
    moduleAppFocusedComponentId.value = null
    moduleAppFocusedElementSelection.value = null
    moduleAppLastEditSummary.value = ''
    moduleAppUiContractArtifactCache.value = {}
    activeModuleAppPageKey.value = ''
    moduleAppUiContractError.value = ''
    moduleAppOverlayVisible.value = false

    if (typeof document !== 'undefined') {
      document.body.classList.remove('a9-module-app-overlay-open')
    }
  },
)
// A9.10.13.0.7 MODULE APP INTEGRATED STATE END

const moduleAppPages = computed<Record<string, any>[]>(() => arrayValue(moduleAppEffectiveContract.value?.pages) as Record<string, any>[])
const moduleAppNavigation = computed<Record<string, any>[]>(() => arrayValue(recordValue(moduleAppEffectiveContract.value?.appShell)?.navigation) as Record<string, any>[])

// A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR
// These items feed the outer Prototype Studio page directory. It uses artifact cache status only for UI hints; it never mutates PRD / PAGE_UI_CONTRACT semantics.
const moduleAppPageNavigatorItems = computed<Record<string, any>[]>(() =>
  moduleAppPages.value.map((page, index) => {
    const rawPageKey = String(page.pageKey || page.page_key || '').trim()
    const cacheKey = stringValue(rawPageKey || page.pageKey)
    const hasCachedArtifact = Boolean(
      cacheKey && Object.prototype.hasOwnProperty.call(moduleAppUiContractArtifactCache.value, cacheKey),
    )
    const artifact = cacheKey ? moduleAppUiContractArtifactCache.value[cacheKey] || null : null
    const draft = cacheKey ? moduleAppDraftContractCache.value[cacheKey] || null : null
    const hasUiContract = Boolean(
      draft ||
        normalizeUiContractCandidate(artifact?.content) ||
        normalizeUiContractCandidate((artifact as any)?.contentMarkdown),
    )
    const checking = Boolean(moduleAppNavigatorProbeLoading.value && !hasCachedArtifact)

    return {
      ...page,
      pageKey: rawPageKey,
      pageName: String(page.pageName || page.page_name || page.title || rawPageKey || `页面 ${index + 1}`).trim(),
      routePath: String(page.routePath || page.route_path || page.route || '').trim(),
      route: String(page.routePath || page.route_path || page.route || '').trim(),
      order: Number(page.order || index + 1),
      hasUiContract,
      navigatorStatus: draft
        ? 'DRAFT'
        : hasUiContract
          ? 'READY'
          : checking
            ? 'LOADING'
            : hasCachedArtifact
              ? 'MISSING'
              : 'UNKNOWN',
    }
  }),
)

const moduleAppTitle = computed(() => stringValue(recordValue(moduleAppEffectiveContract.value?.appShell)?.title) || nodeName.value)
const moduleAppNavigationPageCount = computed(() =>
  moduleAppPages.value.filter((page) => page.navigationVisible !== false).length,
)
const moduleAppSecondaryPageCount = computed(() =>
  Math.max(0, moduleAppPages.value.length - moduleAppNavigationPageCount.value),
)
const moduleAppDefaultPageName = computed(() => {
  const page = moduleAppPages.value.find((item) => item.navigationVisible !== false) || moduleAppPages.value[0]
  return stringValue(page?.pageName) || '未设置'
})


const activeModuleAppPage = computed<Record<string, any> | null>(() => {
  const pages = moduleAppPages.value
  if (!pages.length) return null
  const selected = activeModuleAppPageKey.value
  return pages.find((page) => page.pageKey === selected) || pages.find((page) => page.navigationVisible !== false) || pages[0]
})

const activeModuleAppPageRoute = computed(() => {
  const page = activeModuleAppPage.value
  return stringValue(page?.routePath || page?.route) || '无独立 route，由父页面动作触发'
})

const moduleAppEdgesForActivePage = computed<Record<string, any>[]>(() => {
  const pageKey = activeModuleAppPage.value?.pageKey
  if (!pageKey) return []
  return (arrayValue(moduleAppContract.value?.navigationEdges) as Record<string, any>[])
    .filter((edge) => !edge.sourcePageKey || edge.sourcePageKey === pageKey)
})


const moduleAppUiContractArtifactCache = ref<Record<string, ArtifactResponse | null>>({})
const moduleAppNavigatorProbeLoading = ref(false)
const moduleAppUiContractLoading = ref(false)
const moduleAppUiContractError = ref('')

const activeModuleAppUiContractArtifact = computed<ArtifactResponse | null>(() => {
  const pageKey = stringValue(activeModuleAppPage.value?.pageKey)
  return pageKey ? moduleAppUiContractArtifactCache.value[pageKey] || null : null
})

const activeModuleAppDraftContract = computed<UiContractContent | null>(() => {
  const pageKey = stringValue(activeModuleAppPage.value?.pageKey)
  return pageKey ? moduleAppDraftContractCache.value[pageKey] || null : null
})

const activeModuleAppUiContract = computed<UiContractContent | null>(() => {
  if (activeModuleAppDraftContract.value) {
    return activeModuleAppDraftContract.value
  }

  const artifact = activeModuleAppUiContractArtifact.value
  return normalizeUiContractCandidate(artifact?.content) ||
    normalizeUiContractCandidate((artifact as any)?.contentMarkdown)
})


// A9.10.14.4.3.15.4.5.2_REAL_THUMBNAIL_AND_TYPE_HOTFIX
// The studio page list needs real PAGE_UI_CONTRACT content to render PPT-like thumbnails.
// This stays read-only and does not call LLM; it only uses the existing page artifact cache/draft cache.
const moduleAppPageNavigatorPreviewItems = computed<Record<string, any>[]>(() =>
  moduleAppPages.value.map((page, index) => {
    const pageKey = stringValue(page.pageKey)
    const draftContract = pageKey ? moduleAppDraftContractCache.value[pageKey] || null : null
    const artifact = pageKey ? moduleAppUiContractArtifactCache.value[pageKey] || null : null
    const artifactContract = normalizeUiContractCandidate(artifact?.content) ||
      normalizeUiContractCandidate((artifact as any)?.contentMarkdown)
    const uiContract = draftContract || artifactContract
    const hasCached = Boolean(
      pageKey && Object.prototype.hasOwnProperty.call(moduleAppUiContractArtifactCache.value, pageKey),
    )
    const isCurrentLoading = Boolean(
      moduleAppUiContractLoading.value &&
        pageKey &&
        pageKey === stringValue(activeModuleAppPage.value?.pageKey),
    )

    return {
      ...page,
      order: Number(page.order || page.navigatorOrder || index + 1),
      hasUiContract: Boolean(uiContract),
      uiContract,
      thumbnailContract: uiContract,
      navigatorStatus: draftContract
        ? 'DRAFT'
        : uiContract
          ? 'READY'
          : isCurrentLoading
            ? 'LOADING'
            : hasCached
              ? 'MISSING'
              : 'UNKNOWN',
    }
  }),
)

const moduleAppPrototypeDraftDirty = computed(() => Boolean(activeModuleAppDraftContract.value))
const moduleAppPendingPageDraftEntries = computed<Array<[string, UiContractContent]>>(() =>
  Object.entries(moduleAppDraftContractCache.value)
    .filter((entry): entry is [string, UiContractContent] => Boolean(entry[1])),
)
const moduleAppPendingPageDraftCount = computed(() => moduleAppPendingPageDraftEntries.value.length)
const moduleAppAnyDraftDirty = computed(() =>
  Boolean(moduleAppPendingPageDraftCount.value > 0 || moduleAppShellDraftDirty.value),
)
const moduleAppFocusedComponentLabel = computed(() =>
  buildModuleAppSelectionLabel(moduleAppFocusedElementSelection.value, moduleAppFocusedComponentId.value),
)
const moduleAppPrototypeDraftSummary = computed(() => {
  if (moduleAppLastEditSummary.value) {
    return moduleAppLastEditSummary.value
  }

  const pending: string[] = []
  if (moduleAppPendingPageDraftCount.value > 0) {
    pending.push(`${moduleAppPendingPageDraftCount.value} 个页面有未保存修改`)
  }
  if (moduleAppShellDraftDirty.value) {
    pending.push('模块应用导航壳有未保存修改')
  }

  return pending.length
    ? `${pending.join('；')}。点击“保存为新版本”会统一保存当前模块工作台中的全部草稿。`
    : props.prototypeEditDraftSummary || ''
})

const moduleAppPrototypeComponentCount = computed(() =>
  flattenComponents(activeModuleAppUiContract.value?.page?.componentTree || []).length,
)

const moduleAppPrototypeSizeLabel = computed(() => {
  const raw = activeModuleAppUiContract.value
    ? JSON.stringify(activeModuleAppUiContract.value)
    : ''
  if (!raw) return '0 KB'
  const kb = raw.length / 1024
  return `${kb.toFixed(kb >= 10 ? 0 : 1)} KB`
})

watch(
  () => [
    props.workspaceId || '',
    props.projectCode || '',
    props.selectedModule?.id || '',
    activeModuleAppPage.value?.pageKey || '',
    activeModuleAppPage.value?.pageName || '',
  ].join('|'),
  () => {
    void loadActiveModuleAppUiContract()
  },
  { immediate: true },
)

watch(
  () => [
    moduleAppOverlayVisible.value ? 'open' : 'closed',
    props.workspaceId || '',
    props.projectCode || '',
    moduleAppPages.value.map((page) => String(page.pageKey || '')).join('|'),
  ].join('::'),
  () => {
    if (moduleAppOverlayVisible.value) {
      void preloadModuleAppNavigatorUiContracts()
    }
  },
  { flush: 'post' },
)

async function preloadModuleAppNavigatorUiContracts() {
  if (!moduleAppOverlayVisible.value || moduleAppNavigatorProbeLoading.value) {
    return
  }

  if (!props.workspaceId || !props.projectCode) {
    return
  }

  const pages = moduleAppPages.value
  if (!pages.length) {
    return
  }

  moduleAppNavigatorProbeLoading.value = true

  try {
    for (const page of pages) {
      const pageKey = stringValue(page.pageKey)
      if (!pageKey) {
        continue
      }

      if (Object.prototype.hasOwnProperty.call(moduleAppUiContractArtifactCache.value, pageKey)) {
        continue
      }

      const targetNode = resolveModuleAppPageNode(page)
      const moduleId = String(targetNode?.id || '').trim()

      if (!moduleId) {
        moduleAppUiContractArtifactCache.value = {
          ...moduleAppUiContractArtifactCache.value,
          [pageKey]: null,
        }
        continue
      }

      try {
        const artifact = await fetchCurrentModuleArtifact(
          props.workspaceId,
          props.projectCode,
          moduleId,
          'UI_CONTRACT',
        )

        const normalized = normalizeUiContractCandidate(artifact?.content) ||
          normalizeUiContractCandidate((artifact as any)?.contentMarkdown)

        moduleAppUiContractArtifactCache.value = {
          ...moduleAppUiContractArtifactCache.value,
          [pageKey]: normalized ? artifact : null,
        }
      } catch {
        moduleAppUiContractArtifactCache.value = {
          ...moduleAppUiContractArtifactCache.value,
          [pageKey]: null,
        }
      }
    }
  } finally {
    moduleAppNavigatorProbeLoading.value = false
  }
}

async function loadActiveModuleAppUiContract() {
  moduleAppUiContractError.value = ''

  const page = activeModuleAppPage.value
  const pageKey = stringValue(page?.pageKey)

  if (!page || !pageKey) {
    return
  }

  const hasCachedModuleAppArtifact = Object.prototype.hasOwnProperty.call(
    moduleAppUiContractArtifactCache.value,
    pageKey,
  )
  const cachedModuleAppArtifact = moduleAppUiContractArtifactCache.value[pageKey]

  // A9.10.13.0.3: if an earlier hot-reload cached null because workspaceId/projectCode
  // were missing, retry once those props become available instead of staying blocked.
  if (hasCachedModuleAppArtifact && cachedModuleAppArtifact && !moduleAppOverlayVisible.value) {
    return
  }

  if (
    hasCachedModuleAppArtifact &&
    !cachedModuleAppArtifact &&
    (!props.workspaceId || !props.projectCode)
  ) {
    return
  }

  const targetNode = resolveModuleAppPageNode(page)
  const moduleId = stringValue(targetNode?.id)

  if (!moduleId) {
    moduleAppUiContractArtifactCache.value = {
      ...moduleAppUiContractArtifactCache.value,
      [pageKey]: null,
    }
    moduleAppUiContractError.value = '没有在模块树中找到该页面对应的 PAGE 节点。'
    return
  }

  if (!props.workspaceId || !props.projectCode) {
    moduleAppUiContractArtifactCache.value = {
      ...moduleAppUiContractArtifactCache.value,
      [pageKey]: null,
    }
    moduleAppUiContractError.value = '缺少 workspaceId / projectCode，无法加载页面交互原型；请检查 ProjectWorkspacePage 是否向 AcceptanceRequirementWorkspace 传入 workspace-id 和 project-code。'
    return
  }

  moduleAppUiContractLoading.value = true

  try {
    const artifact = await fetchCurrentModuleArtifact(
      props.workspaceId,
      props.projectCode,
      moduleId,
      'UI_CONTRACT',
    )

    const normalized = normalizeUiContractCandidate(artifact?.content) ||
      normalizeUiContractCandidate((artifact as any)?.contentMarkdown)

    moduleAppUiContractArtifactCache.value = {
      ...moduleAppUiContractArtifactCache.value,
      [pageKey]: normalized ? artifact : null,
    }

    if (!normalized) {
      moduleAppUiContractError.value = artifact?.exists === false
        ? '该 PAGE 节点尚未生成交互原型。'
        : '该 PAGE 节点的交互原型内容暂不可直接渲染。'
    }
  } catch (error) {
    moduleAppUiContractArtifactCache.value = {
      ...moduleAppUiContractArtifactCache.value,
      [pageKey]: null,
    }
    moduleAppUiContractError.value = error instanceof Error && error.message
      ? error.message
      : '加载页面交互原型失败。'
  } finally {
    moduleAppUiContractLoading.value = false
  }
}

function resolveModuleAppPageNode(page: Record<string, any> | null): ModuleTreeNode | null {
  if (!page) return null

  const candidates = collectModuleAppPageNodeCandidates()
  const pageKey = moduleAppComparableKey(page.pageKey)
  const pageName = moduleAppComparableKey(page.pageName)
  const route = moduleAppComparableKey(page.routePath || page.route)

  return candidates.find((node) => {
    const extra = safeExtra(node)
    const nodeKeys = [
      node.id,
      getModuleStableKey(node),
      (node as any).moduleKey,
      (node as any).moduleCode,
      (node as any).pageKey,
      (node as any).routePath,
      (node as any).route,
      extra.nodeKey,
      extra.node_key,
      extra.pageKey,
      extra.page_key,
      extra.routePath,
      extra.route_path,
      extra.route,
      getModuleDisplayName(node),
      (node as any).moduleName,
      (node as any).name,
      (node as any).title,
    ].map(moduleAppComparableKey).filter(Boolean)

    return Boolean(
      (pageKey && nodeKeys.includes(pageKey)) ||
        (route && nodeKeys.includes(route)) ||
        (pageName && nodeKeys.includes(pageName)),
    )
  }) || null
}

function collectModuleAppPageNodeCandidates(): ModuleTreeNode[] {
  const result: ModuleTreeNode[] = []
  const seen = new Set<string>()

  const visit = (node: ModuleTreeNode | null | undefined) => {
    if (!node) return

    if (isPageNode(node)) {
      const id = stringValue(node.id || getModuleStableKey(node) || getModuleDisplayName(node))
      if (!seen.has(id)) {
        seen.add(id)
        result.push(node)
      }
    }

    for (const child of node.children || []) {
      visit(child)
    }
  }

  visit(props.selectedModule)
  for (const root of props.moduleTree || []) {
    visit(root)
  }

  return result
}

function moduleAppComparableKey(value: unknown): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/^\/+|\/+$/g, '')
    .replace(/[\s_\-]+/g, '')
}

async function handleModuleAppRuntimeNavigate(payload: Record<string, unknown>) {
  const target = resolveModuleAppNavigationTarget(payload)

  if (!target) {
    return {
      ok: false,
      code: 'TARGET_NOT_FOUND' as const,
      message: '当前模块应用原型没有找到目标页面。',
    }
  }

  activeModuleAppPageKey.value = target.pageKey || ''

  return {
    ok: true,
    code: 'SUCCESS' as const,
    message: `已切换到：${target.pageName || target.pageKey}`,
    targetPageKey: target.pageKey || null,
    targetPageName: target.pageName || null,
    route: target.routePath || target.route || null,
  }
}

function resolveModuleAppNavigationTarget(payload: Record<string, unknown>): Record<string, any> | null {
  const pages = moduleAppPages.value
  const targetPageKey = moduleAppComparableKey(payload.targetPageKey)
  const targetPageName = moduleAppComparableKey(payload.targetPageName)
  const targetRoute = moduleAppComparableKey(payload.route)
  const actionId = moduleAppComparableKey(payload.actionId)

  const direct = pages.find((page) => {
    return Boolean(
      (targetPageKey && moduleAppComparableKey(page.pageKey) === targetPageKey) ||
        (targetPageName && moduleAppComparableKey(page.pageName) === targetPageName) ||
        (targetRoute && moduleAppComparableKey(page.routePath || page.route) === targetRoute),
    )
  })

  if (direct) return direct

  const matchedEdge = (arrayValue(moduleAppContract.value?.navigationEdges) as Record<string, any>[])
    .find((edge) => {
      return Boolean(
        (actionId && moduleAppComparableKey(edge.actionId || edge.label) === actionId) ||
          (targetPageKey && moduleAppComparableKey(edge.targetPageKey) === targetPageKey) ||
          (targetRoute && moduleAppComparableKey(edge.targetRoute) === targetRoute),
      )
    })

  if (!matchedEdge) return null

  return pages.find((page) => {
    return Boolean(
      moduleAppComparableKey(page.pageKey) === moduleAppComparableKey(matchedEdge.targetPageKey) ||
        moduleAppComparableKey(page.pageName) === moduleAppComparableKey(matchedEdge.targetPageName) ||
        moduleAppComparableKey(page.routePath || page.route) === moduleAppComparableKey(matchedEdge.targetRoute),
    )
  }) || null
}

watch(
  () => `${moduleAppContract.value?.moduleKey || ''}:${moduleAppPages.value.map((page) => page.pageKey).join('|')}`,
  () => {
    const pages = moduleAppPages.value
    if (!pages.length) {
      activeModuleAppPageKey.value = ''
      return
    }
    if (!pages.some((page) => page.pageKey === activeModuleAppPageKey.value)) {
      activeModuleAppPageKey.value = (pages.find((page) => page.navigationVisible !== false) || pages[0]).pageKey || ''
    }
  },
  { immediate: true },
)

function openModuleAppPageByNav(item: Record<string, any>) {
  const pageKey = stringValue(item.pageKey)
  if (pageKey) activeModuleAppPageKey.value = pageKey
  handleModuleAppStudioClearSelection()
}

function openModuleAppPageByEdge(edge: Record<string, any>) {
  const targetPageKey = stringValue(edge.targetPageKey)
  if (targetPageKey && moduleAppPages.value.some((page) => page.pageKey === targetPageKey)) {
    activeModuleAppPageKey.value = targetPageKey
  }
}

function normalizeModuleAppPages(rawPages: unknown[], source: Record<string, any>): Record<string, any>[] {
  return rawPages
    .map((raw, index) => {
      const page = recordValue(raw)
      if (!page) return null
      const pageKey = stringValue(page.pageKey || page.page_key || `page_${index + 1}`)
      const pageName = stringValue(page.pageName || page.page_name || page.title || pageKey)
      const routePath = stringValue(page.routePath || page.route_path || page.route)
      const pageKind = stringValue(page.pageKind || page.page_kind || '')
      const isListLike = /LIST|列表|查询/.test(`${pageKind} ${pageName}`)
      const isSecondaryByName = /CREATE|NEW|ADD|DETAIL|APPROVAL|AUDIT|EDIT|FORM|创建|新建|新增|详情|审核|审批|编辑|修改|数量|表单|提交/.test(`${pageKind} ${pageName}`)
      const explicitNavigationVisible = typeof page.navigationVisible === 'boolean'
        ? page.navigationVisible
        : typeof page.navigation_visible === 'boolean'
          ? page.navigation_visible
          : null
      const navigationVisible = explicitNavigationVisible === false
        ? false
        : isSecondaryByName && !isListLike
          ? false
          : explicitNavigationVisible === true
            ? true
            : isListLike || index === 0

      return {
        ...page,
        pageKey,
        pageName,
        routePath,
        route: routePath,
        pageKind,
        surfaceType: stringValue(page.surfaceType || page.surface_type) || (navigationVisible ? 'MAIN_NAV_PAGE' : 'SECONDARY_ROUTE'),
        entryMode: stringValue(page.entryMode || page.entry_mode) || (navigationVisible ? 'NAVIGATION' : 'ACTION_ROUTE'),
        navigationVisible,
        primaryEntity: stringValue(page.primaryEntity || page.primary_entity || source.primaryEntity || ''),
      }
    })
    .filter(Boolean) as Record<string, any>[]
}

function normalizeModuleAppNavigation(source: Record<string, any>, pages: Record<string, any>[]): Record<string, any>[] {
  const shell = recordValue(source.appShell) || recordValue(source.app_shell)
  const explicit = arrayValue(shell?.navigation)
    .map((raw) => recordValue(raw))
    .filter(Boolean) as Record<string, any>[]

  if (explicit.length) {
    const visiblePageKeys = new Set(
      pages
        .filter((page) => page.navigationVisible !== false)
        .map((page) => moduleAppComparableKey(page.pageKey)),
    )

    return explicit
      .map((item, index) => ({
        label: stringValue(item.label || item.pageName || item.page_key) || `页面 ${index + 1}`,
        route: stringValue(item.route || item.routePath || ''),
        pageKey: stringValue(item.pageKey || item.page_key || ''),
        visible: item.visible !== false,
        order: Number(item.order || index + 1),
      }))
      .filter((item) => item.visible !== false && (!item.pageKey || visiblePageKeys.has(moduleAppComparableKey(item.pageKey))))
  }

  return pages
    .filter((page) => page.navigationVisible !== false)
    .map((page, index) => ({
      label: page.pageName,
      route: page.routePath || page.route,
      pageKey: page.pageKey,
      visible: true,
      order: index + 1,
    }))
}

function normalizeModuleAppEdges(rawEdges: unknown[]): Record<string, any>[] {
  return rawEdges
    .map((raw) => recordValue(raw))
    .filter((edge): edge is Record<string, any> => Boolean(edge))
    .map((edge) => ({
      ...edge,
      sourcePageKey: stringValue(edge.sourcePageKey || edge.source_page_key || ''),
      sourcePageName: stringValue(edge.sourcePageName || edge.source_page_name || ''),
      targetPageKey: stringValue(edge.targetPageKey || edge.target_page_key || ''),
      targetPageName: stringValue(edge.targetPageName || edge.target_page_name || ''),
      targetRoute: stringValue(edge.targetRoute || edge.target_route || ''),
      actionId: stringValue(edge.actionId || edge.action_id || ''),
      label: stringValue(edge.label || edge.actionLabel || edge.action_label || ''),
    }))
}

// A9.10.13.0.7 MODULE APP INTEGRATED STUDIO HELPERS BEGIN
function handleModuleAppStudioSelectComponent(payload: PrototypeElementSelection) {
  const componentId = String(payload?.componentId || '').trim()
  if (!componentId) return

  moduleAppFocusedComponentId.value = componentId
  moduleAppFocusedElementSelection.value = {
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
    ...payload,
  }
}

function handleModuleAppStudioClearSelection() {
  moduleAppFocusedComponentId.value = null
  moduleAppFocusedElementSelection.value = null
}

function handleModuleAppStudioDiscardDraft() {
  const discardedPageCount = moduleAppPendingPageDraftCount.value
  const discardedShellDraft = moduleAppShellDraftDirty.value

  moduleAppDraftContractCache.value = {}

  if (discardedShellDraft) {
    moduleAppShellDraftContent.value = moduleAppShellDraftBaseContent.value
      ? deepCloneRecord(moduleAppShellDraftBaseContent.value)
      : null
  }
  moduleAppShellDraftBaseContent.value = null
  moduleAppShellDraftDirty.value = false

  const discardedParts: string[] = []
  if (discardedPageCount > 0) {
    discardedParts.push(`${discardedPageCount} 个页面草稿`)
  }
  if (discardedShellDraft) {
    discardedParts.push('模块导航壳草稿')
  }

  moduleAppLastEditSummary.value = discardedParts.length
    ? `已放弃${discardedParts.join('和')}，当前工作台已恢复到最近保存版本。`
    : ''
}

function handleModuleAppStudioInstruction(instruction: string) {
  const text = String(instruction || '').trim()
  if (!text) return

  const selection = moduleAppFocusedElementSelection.value

  if (isModuleAppShellSelection(selection)) {
    const baseContract = moduleAppEffectiveContract.value || moduleAppContract.value
    if (!baseContract) {
      moduleAppLastEditSummary.value = '当前模块还没有可编辑的 MODULE_APP_CONTRACT。'
      return
    }

    const alreadyDirty = moduleAppShellDraftDirty.value
    if (!alreadyDirty) {
      moduleAppShellDraftBaseContent.value = deepCloneRecord(baseContract)
    }

    const result = applyModuleAppShellLocalEdit(
      baseContract,
      selection,
      text,
    )
    moduleAppShellDraftContent.value = result.contract
    moduleAppShellDraftDirty.value = alreadyDirty || result.changed
    if (!moduleAppShellDraftDirty.value) {
      moduleAppShellDraftBaseContent.value = null
    }
    moduleAppLastEditSummary.value = result.summary
    return
  }

  const current = activeModuleAppUiContract.value
  const pageKey = stringValue(activeModuleAppPage.value?.pageKey)
  if (!current || !pageKey) {
    moduleAppLastEditSummary.value = '当前页面还没有可编辑的 PAGE_UI_CONTRACT。'
    return
  }

  if (!isPrototypeLocalEditInstruction(text)) {
    moduleAppLastEditSummary.value = '这条指令暂未识别为可执行的原型局部修改。'
    return
  }

  const result = applyPrototypeLocalEdit({
    contract: current,
    instruction: text,
    focusedComponentId: moduleAppFocusedComponentId.value,
    prototypeElementSelection: selection as any,
    focusedPrototypeElementSelection: selection as any,
    selectionRef: selection as any,
  })

  if (!result.changed) {
    moduleAppLastEditSummary.value = result.summary || '没有产生可保存的页面原型修改。'
    return
  }

  moduleAppDraftContractCache.value = {
    ...moduleAppDraftContractCache.value,
    [pageKey]: result.contract,
  }
  moduleAppLastEditSummary.value = result.summary
}

async function handleModuleAppStudioSaveDraft() {
  if (moduleAppPrototypeSaving.value) return

  const pageDraftEntries = [...moduleAppPendingPageDraftEntries.value]
  const shellDraft = moduleAppShellDraftDirty.value ? moduleAppShellDraftContent.value : null

  if (!pageDraftEntries.length && !shellDraft) return
  if (!props.workspaceId || !props.projectCode) {
    moduleAppLastEditSummary.value = '缺少 workspaceId / projectCode，无法保存模块应用原型修改。'
    return
  }

  const pageDraftTargets = pageDraftEntries.map(([pageKey, pageDraft]) => {
    const page = moduleAppPages.value.find(
      (item) => stringValue(item.pageKey) === stringValue(pageKey),
    ) || null
    const targetNode = resolveModuleAppPageNode(page)
    return {
      pageKey,
      pageDraft,
      page,
      targetNode,
      moduleId: stringValue(targetNode?.id),
    }
  })

  const unresolvedTarget = pageDraftTargets.find((item) => !item.page || !item.moduleId)
  if (unresolvedTarget) {
    const unresolvedName = String(
      unresolvedTarget.page?.pageName ||
        unresolvedTarget.page?.title ||
        unresolvedTarget.pageKey ||
        '未知页面',
    ).trim()

    moduleAppLastEditSummary.value =
      `页面“${unresolvedName}”没有找到对应的 PAGE 节点，` +
      '为避免只保存一部分草稿，本次没有执行保存。'
    return
  }

  let savedPageCount = 0
  let shellSaved = false

  moduleAppPrototypeSaving.value = true
  try {
    if (shellDraft && props.selectedModule?.id) {
      const result = await saveManualArtifactEdit(
        props.workspaceId,
        props.projectCode,
        props.selectedModule.id,
        {
          artifactId: currentModuleAppArtifact.value?.artifactId || null,
          artifactType: 'MODULE_APP_CONTRACT',
          contentMarkdown: JSON.stringify(shellDraft, null, 2),
          content: shellDraft,
          reason: '模块应用原型工作台调整导航壳。',
        },
      )

      const savedModuleAppArtifact = {
        ...(result as any),
        exists: true,
        artifactType: 'MODULE_APP_CONTRACT',
        content: (result as any).content || shellDraft,
        contentMarkdown:
          (result as any).contentMarkdown ||
          JSON.stringify((result as any).content || shellDraft, null, 2),
      } as ArtifactResponse

      currentModuleAppArtifact.value = savedModuleAppArtifact
      const readBackArtifact = await loadCurrentModuleAppContractArtifact()
      if (!readBackArtifact) {
        currentModuleAppArtifact.value = savedModuleAppArtifact
      }

      moduleAppShellDraftDirty.value = false
      moduleAppShellDraftBaseContent.value = null
      moduleAppShellDraftContent.value = null
      shellSaved = true
    }

    for (const target of pageDraftTargets) {
      const previousArtifact = moduleAppUiContractArtifactCache.value[target.pageKey] || null
      const result = await saveManualArtifactEdit(
        props.workspaceId,
        props.projectCode,
        target.moduleId,
        {
          artifactId: previousArtifact?.artifactId || null,
          artifactType: 'UI_CONTRACT',
          contentMarkdown: JSON.stringify(target.pageDraft, null, 2),
          content: target.pageDraft as unknown as Record<string, unknown>,
          reason: '模块应用原型工作台调整页面原型。',
        },
      )

      moduleAppUiContractArtifactCache.value = {
        ...moduleAppUiContractArtifactCache.value,
        [target.pageKey]: {
          ...(previousArtifact || {}),
          ...(result as any),
          exists: true,
          artifactType: 'UI_CONTRACT',
          content: (result as any).content || target.pageDraft,
          contentMarkdown:
            (result as any).contentMarkdown ||
            JSON.stringify(target.pageDraft, null, 2),
        } as ArtifactResponse,
      }

      moduleAppDraftContractCache.value = {
        ...moduleAppDraftContractCache.value,
        [target.pageKey]: null,
      }
      savedPageCount += 1
    }

    const savedParts: string[] = []
    if (shellSaved) {
      savedParts.push('模块导航壳')
    }
    if (savedPageCount > 0) {
      savedParts.push(`${savedPageCount} 个 PAGE_UI_CONTRACT 新版本`)
    }

    moduleAppLastEditSummary.value = savedParts.length
      ? `已保存${savedParts.join('和')}。重新确认模块开发基线后，Development Agent 才会使用这些新版本。`
      : '当前没有需要保存的模块原型修改。'
  } catch (error) {
    const message = error instanceof Error && error.message
      ? error.message
      : '保存模块应用原型修改失败。'
    const savedParts: string[] = []
    if (shellSaved) savedParts.push('模块导航壳')
    if (savedPageCount > 0) savedParts.push(`${savedPageCount} 个页面`)

    moduleAppLastEditSummary.value = savedParts.length
      ? `已成功保存${savedParts.join('和')}，但后续保存失败：${message}。未成功的草稿仍保留在工作台中。`
      : message
  } finally {
    moduleAppPrototypeSaving.value = false
  }
}

async function handleModuleAppPrototypeConfirmSnapshot() {
  if (moduleAppPrototypeSaving.value) return

  if (moduleAppAnyDraftDirty.value) {
    moduleAppLastEditSummary.value = '模块汇总原型还有未保存修改，请先保存为新版本后再确认为开发基线。'
    return
  }

  if (!props.selectedModule?.id || !isModule.value) {
    moduleAppLastEditSummary.value = '只有 MODULE 节点可以确认模块汇总原型开发基线。'
    return
  }

  if (!props.workspaceId || !props.projectCode) {
    moduleAppLastEditSummary.value = '缺少 workspaceId / projectCode，无法确认模块开发基线。'
    return
  }

  let contract = moduleAppEffectiveContract.value || moduleAppContract.value
  if (!contract) {
    moduleAppLastEditSummary.value = '当前模块还没有 MODULE_APP_CONTRACT，无法确认为开发基线。'
    return
  }

  const pages = moduleAppPages.value
  if (!pages.length) {
    moduleAppLastEditSummary.value = '当前模块汇总原型没有页面，无法确认为开发基线。'
    return
  }

  const confirmText = [
    '确认把当前模块汇总原型作为 Development Agent 的开发基线？',
    '',
    '系统会锁定当前 MODULE_APP_CONTRACT，以及模块汇总中可用的 PAGE_UI_CONTRACT 版本。',
    '后续 PAGE 原型继续修改时，需要重新确认模块汇总后，Development Agent 才会同步到代码。'
  ].join('\n')

  if (typeof window !== 'undefined' && !window.confirm(confirmText)) {
    return
  }

  moduleAppPrototypeSaving.value = true
  moduleAppLastEditSummary.value = '正在确认模块汇总原型开发基线...'

  try {
    let moduleAppArtifact: ArtifactResponse | null = null
    try {
      moduleAppArtifact = await fetchCurrentModuleArtifact(
        props.workspaceId,
        props.projectCode,
        props.selectedModule.id,
        'MODULE_APP_CONTRACT',
      )

      const artifactSource = normalizeModuleAppContractRawSource(moduleAppArtifact?.content) ||
        normalizeModuleAppContractRawSource((moduleAppArtifact as any)?.contentMarkdown)
      const artifactContract = normalizeModuleAppContractSource(
        artifactSource,
        'MODULE_APP_CONTRACT_CURRENT_ARTIFACT',
      )

      if (artifactContract) {
        currentModuleAppArtifact.value = moduleAppArtifact
        contract = artifactContract
      }
    } catch {
      moduleAppArtifact = null
    }

    const pageSnapshots: Record<string, unknown>[] = []

    for (const page of pages) {
      const pageKey = stringValue(page.pageKey)
      const targetNode = resolveModuleAppPageNode(page)
      const moduleId = stringValue(targetNode?.id)
      const base: Record<string, unknown> = {
        pageKey,
        pageName: stringValue(page.pageName),
        routePath: stringValue(page.routePath || page.route),
        pageKind: stringValue(page.pageKind),
        surfaceType: stringValue(page.surfaceType),
        entryMode: stringValue(page.entryMode),
        navigationVisible: page.navigationVisible !== false,
        nodeId: moduleId || null,
        nodeKey: targetNode ? getModuleStableKey(targetNode) : '',
      }

      if (!moduleId) {
        pageSnapshots.push({
          ...base,
          status: 'NO_PAGE_NODE',
          hasUiContract: false,
          missingReason: '没有在模块树中找到该页面对应的 PAGE 节点。',
        })
        continue
      }

      try {
        const artifact = await fetchCurrentModuleArtifact(
          props.workspaceId,
          props.projectCode,
          moduleId,
          'UI_CONTRACT',
        )

        const uiContract = normalizeUiContractCandidate(artifact?.content) ||
          normalizeUiContractCandidate((artifact as any)?.contentMarkdown)

        if (!uiContract) {
          pageSnapshots.push({
            ...base,
            status: artifact?.exists === false ? 'NO_PAGE_UI_CONTRACT' : 'INVALID_PAGE_UI_CONTRACT',
            hasUiContract: false,
            artifactId: artifact?.artifactId || null,
            artifactVersionNo: artifact?.versionNo || null,
            missingReason: artifact?.exists === false
              ? '该 PAGE 节点尚未生成交互原型。'
              : '该 PAGE 节点的交互原型内容暂不可直接渲染。',
          })
          continue
        }

        pageSnapshots.push({
          ...base,
          status: 'MODULE_PROTOTYPE_CONFIRMED',
          hasUiContract: true,
          artifactId: artifact?.artifactId || null,
          artifactVersionNo: artifact?.versionNo || null,
          artifactType: 'UI_CONTRACT',
          uiContract,
        })
      } catch (error) {
        pageSnapshots.push({
          ...base,
          status: 'LOAD_PAGE_UI_CONTRACT_FAILED',
          hasUiContract: false,
          missingReason: error instanceof Error && error.message
            ? error.message
            : '加载 PAGE_UI_CONTRACT 失败。',
        })
      }
    }

    const usablePageCount = pageSnapshots.filter((item) => item.hasUiContract === true).length
    if (usablePageCount <= 0) {
      moduleAppLastEditSummary.value = '当前模块汇总原型里没有任何可用 PAGE_UI_CONTRACT；请先生成并保存至少一个页面交互原型。'
      return
    }

    const snapshot = {
      schemaVersion: 'A9.10.13.3',
      artifactType: 'MODULE_PROTOTYPE_SNAPSHOT',
      source: 'MODULE_APP_PROTOTYPE_STUDIO',
      authority: 'DEVELOPMENT_AGENT_CODEGEN_BASELINE',
      confirmedAt: new Date().toISOString(),
      moduleId: props.selectedModule.id,
      moduleKey: nodeKey.value,
      moduleName: nodeName.value,
      moduleAppArtifact: moduleAppArtifact?.artifactId
        ? {
            artifactId: moduleAppArtifact.artifactId,
            artifactVersionNo: moduleAppArtifact.versionNo || null,
            artifactType: 'MODULE_APP_CONTRACT',
            current: moduleAppArtifact.current !== false,
          }
        : null,
      moduleAppContract: contract,
      projectThemeProfile: props.projectThemeProfile || null,
      pageCount: pageSnapshots.length,
      usablePageCount,
      partial: usablePageCount < pageSnapshots.length,
      pages: pageSnapshots,
      developmentPolicy: {
        codegenOwner: 'MODULE',
        pageAuthority: 'PAGE_UI_CONTRACT_CURRENT_AT_MODULE_CONFIRM_TIME',
        unconfirmedPageBehavior: 'PLACEHOLDER_ONLY_NO_FAKE_BUSINESS_PAGE',
        requiresManualCodeUpdate: true,
      },
    }

    await saveManualArtifactEdit(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      {
        artifactId: null,
        artifactType: 'MODULE_PROTOTYPE_SNAPSHOT',
        contentMarkdown: JSON.stringify(snapshot, null, 2),
        content: snapshot,
        reason: '确认模块汇总原型为 Development Agent 开发基线。',
      },
    )

    moduleAppLastEditSummary.value = usablePageCount === pageSnapshots.length
      ? `已确认模块开发基线：${usablePageCount}/${pageSnapshots.length} 个页面可进入 Development Agent。`
      : `已确认阶段性模块开发基线：${usablePageCount}/${pageSnapshots.length} 个页面可生成真实代码，未生成页面只会输出占位提示。`
  } catch (error) {
    moduleAppLastEditSummary.value = error instanceof Error && error.message
      ? error.message
      : '确认模块汇总原型开发基线失败。'
  } finally {
    moduleAppPrototypeSaving.value = false
  }
}


function isModuleAppShellSelection(selection: PrototypeElementSelection | null): boolean {
  if (!selection) return false
  const componentId = String(selection.componentId || '')
  const kind = String(selection.elementKind || selection.componentType || '').toUpperCase()
  return componentId.startsWith('module-app-shell:') || kind.startsWith('MODULE_')
}

function buildModuleAppSelectionLabel(
  selection: PrototypeElementSelection | null,
  componentId: string | null,
): string {
  if (!selection && !componentId) return ''
  const kind = String(selection?.elementKind || '').toUpperCase()
  const prefix = kind.startsWith('MODULE_') ? '模块壳' : '页面元素'
  const label = stringValue(
    selection?.elementLabel ||
      selection?.fieldKey ||
      selection?.actionId ||
      selection?.columnKey ||
      selection?.componentTitle ||
      componentId ||
      '',
  )
  return label ? `${prefix} · ${label}` : prefix
}

function applyModuleAppShellLocalEdit(
  source: Record<string, any> | null,
  selection: PrototypeElementSelection | null,
  instruction: string,
): { changed: boolean; contract: Record<string, any>; summary: string } {
  const contract = deepCloneRecord(source || {})
  const appShell = recordValue(contract.appShell) || {}
  contract.appShell = appShell
  const style = recordValue(appShell.style) || {}
  appShell.style = style

  const changes: string[] = []
  const text = String(instruction || '').trim()
  const selectionId = String(selection?.componentId || '')
  const isNavItem = selectionId.includes('navigation-item')
  const isActionEdge = selectionId.includes('action-edge')
  const targetPageKey = stringValue((selection as any)?.pageKey || (selection as any)?.targetPageKey)
  const targetActionId = stringValue((selection as any)?.actionId)

  if (/蓝|blue/i.test(text)) {
    style.navTheme = 'blue'
    changes.push('导航主题改为蓝色')
  } else if (/浅|白|light/i.test(text)) {
    style.navTheme = 'light'
    changes.push('导航主题改为浅色')
  } else if (/深|黑|dark/i.test(text)) {
    style.navTheme = 'dark'
    changes.push('导航主题改为深色')
  }

  if (/圆角|圆润|round/i.test(text)) {
    style.itemRadius = 'large'
    changes.push('导航菜单圆角加大')
  }

  // A9.10.13.0.8 MODULE SHELL LAYOUT EDIT BEGIN
  if (/顶部|顶栏|横向|top|header|horizontal|电商|商城/i.test(text)) {
    appShell.layout = 'TOP_NAV'
    appShell.navigationPlacement = 'top'
    appShell.shellKind = 'WEBSITE_TOP_NAV'
    changes.push('模块应用壳改为顶部导航')
  } else if (/左侧|侧边|sidebar|sider|后台|管理/i.test(text)) {
    appShell.layout = 'SIDEBAR'
    appShell.navigationPlacement = 'left'
    appShell.shellKind = 'ADMIN_SIDEBAR'
    changes.push('模块应用壳改为左侧导航')
  } else if (/无导航|不要导航|隐藏导航|按钮流|动作流|button flow|action hub/i.test(text)) {
    appShell.layout = 'ACTION_HUB'
    appShell.navigationPlacement = 'none'
    appShell.shellKind = 'ACTION_FLOW'
    changes.push('模块应用壳改为无导航按钮流')
  }
  // A9.10.13.0.8 MODULE SHELL LAYOUT EDIT END

  const newText = extractModuleAppQuotedText(text)
  if (newText) {
    if (selectionId.includes('title') || /标题|模块名|导航标题/.test(text)) {
      appShell.title = newText
      contract.moduleName = newText
      changes.push(`导航标题改为「${newText}」`)
    } else if (isNavItem && targetPageKey) {
      const navigation = arrayValue(appShell.navigation)
      for (const item of navigation) {
        const record = recordValue(item)
        if (record && moduleAppComparableKey(record.pageKey) === moduleAppComparableKey(targetPageKey)) {
          record.label = newText
          changes.push(`导航项文案改为「${newText}」`)
          break
        }
      }
    } else if (isActionEdge && (targetActionId || targetPageKey)) {
      const edges = arrayValue(contract.navigationEdges)
      for (const item of edges) {
        const record = recordValue(item)
        if (!record) continue
        const sameAction = targetActionId && moduleAppComparableKey(record.actionId || record.label) === moduleAppComparableKey(targetActionId)
        const sameTarget = targetPageKey && moduleAppComparableKey(record.targetPageKey) === moduleAppComparableKey(targetPageKey)
        if (sameAction || sameTarget) {
          record.label = newText
          record.actionLabel = newText
          changes.push(`页面动作按钮文案改为「${newText}」`)
          break
        }
      }
    }
  }

  if ((/隐藏|去掉|移除/.test(text)) && isNavItem && targetPageKey) {
    const navigation = arrayValue(appShell.navigation)
    for (const item of navigation) {
      const record = recordValue(item)
      if (record && moduleAppComparableKey(record.pageKey) === moduleAppComparableKey(targetPageKey)) {
        record.visible = false
        changes.push('隐藏当前导航项')
        break
      }
    }
  }

  if ((/隐藏|去掉|移除/.test(text)) && isActionEdge && (targetActionId || targetPageKey)) {
    const edges = arrayValue(contract.navigationEdges)
    for (const item of edges) {
      const record = recordValue(item)
      if (!record) continue
      const sameAction = targetActionId && moduleAppComparableKey(record.actionId || record.label) === moduleAppComparableKey(targetActionId)
      const sameTarget = targetPageKey && moduleAppComparableKey(record.targetPageKey) === moduleAppComparableKey(targetPageKey)
      if (sameAction || sameTarget) {
        record.visible = false
        record.hidden = true
        changes.push('隐藏当前页面动作按钮')
        break
      }
    }
  }

  const metadata = recordValue(contract.metadata) || {}
  contract.metadata = metadata
  const edits = Array.isArray(metadata.moduleAppLocalEdits)
    ? metadata.moduleAppLocalEdits
    : []
  metadata.moduleAppLocalEdits = edits
  edits.push({
    editedAt: new Date().toISOString(),
    instruction,
    targetComponentId: selection?.componentId || '',
    selectionRef: selection || null,
    changes: changes.length ? changes : ['记录模块应用壳调整指令'],
  })

  return {
    changed: true,
    contract,
    summary: changes.length
      ? `已在模块应用壳中调整：${changes.join('、')}。`
      : '已记录模块应用壳调整指令，保存后进入 MODULE_APP_CONTRACT。',
  }
}

function extractModuleAppQuotedText(text: string): string {
  const quoted = text.match(/[“"]([^”"]+)[”"]/)
  if (quoted?.[1]) return quoted[1].trim()
  const suffix = text.match(/(?:改成|改为|换成|叫做|命名为)(.+)$/)
  return suffix?.[1]
    ? suffix[1].replace(/[。！!,.，]/g, '').trim()
    : ''
}

function deepCloneRecord<T extends Record<string, any>>(value: T): T {
  try {
    return JSON.parse(JSON.stringify(value || {}))
  } catch {
    return { ...(value || {}) } as T
  }
}
// A9.10.13.0.7 MODULE APP INTEGRATED STUDIO HELPERS END

// A9.10.13.0.1 MODULE APP PROTOTYPE END

// A9.9.4 auto-open Prototype Studio after new UI_CONTRACT generation completes.
const pendingPrototypeStudioAutoOpen = ref(false)
const lastPrototypeStudioAutoOpenKey = ref('')

watch(
  () => props.isGeneratingUiContract,
  (generating) => {
    if (generating) {
      pendingPrototypeStudioAutoOpen.value = true
    }
  },
)

watch(
  () => [
    props.isGeneratingUiContract,
    props.uiContractArtifact?.artifactId || '',
    props.uiContractArtifact?.versionNo || '',
    hasPrototype.value,
  ],
  async ([generating, artifactId, versionNo, prototypeReady]) => {
    if (generating || !pendingPrototypeStudioAutoOpen.value) return
    if (!prototypeReady || !isPage.value || !uiContractObject.value) return

    const autoOpenKey = `${artifactId || 'artifact'}:${versionNo || 'current'}`
    if (autoOpenKey === lastPrototypeStudioAutoOpenKey.value) return

    pendingPrototypeStudioAutoOpen.value = false
    lastPrototypeStudioAutoOpenKey.value = autoOpenKey
    await nextTick()
    openPrototypeStudio()
  },
  { flush: 'post' },
)
const hasRequirementTree = computed(() => props.moduleTree.length > 0)

const uiContractObject = computed<UiContractContent | null>(() => {
  const content = props.uiContractDraftContent || props.uiContractArtifact?.content || tryParseJson(props.uiContractMarkdown)
  return normalizeUiContractCandidate(content)
})

const prototypeComponentCount = computed(() => {
  return flattenComponents(uiContractObject.value?.page?.componentTree || []).length
})


const prototypeVersionLabel = computed(() => {
  const versionNo = numberValue(props.uiContractArtifact?.versionNo)
  return versionNo ? `V${versionNo}` : '未生成版本'
})

const prototypePublicationState = computed<'DRAFT' | 'SAVED_UNPUBLISHED' | 'PUBLISHED' | 'EMPTY'>(() => {
  const artifactId = stringValue(props.uiContractArtifact?.artifactId)

  if (props.prototypeEditDraftDirty || props.uiContractDraftContent) {
    return 'DRAFT'
  }

  if (!artifactId) {
    return 'EMPTY'
  }

  // A9.10.13.3: PAGE 层只保存 PAGE_UI_CONTRACT 版本；
  // Development Agent 的授权入口收敛到 MODULE 汇总原型确认快照。
  return 'SAVED_UNPUBLISHED'
})

const prototypePublicationLabel = computed(() => {
  if (prototypePublicationState.value === 'DRAFT') {
    return '草稿未保存'
  }

  if (prototypePublicationState.value === 'PUBLISHED') {
    return '已由旧页面快照确认'
  }

  if (prototypePublicationState.value === 'SAVED_UNPUBLISHED') {
    return '已保存待模块确认'
  }

  return '尚未生成'
})

const canPublishPrototype = computed(() => false)

function stringValue(value: unknown): string {
  return String(value || '').trim().toLowerCase()
}


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
  if (typeof value === 'string') {
    return normalizeUiContractCandidate(tryParseJson(value))
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const candidate = value as Record<string, any>
  if (!candidate.page) {
    const nested = candidate.content || candidate.uiContract || candidate.ui_contract || candidate.contract || candidate.snapshot || candidate.payload
    if (nested && nested !== value) {
      return normalizeUiContractCandidate(nested)
    }
  }
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

type A9PrototypeSaveRequirementSyncMode =
  | 'UI_CONTRACT_ONLY'
  | 'REQUIREMENT_SYNC_REVIEW_REQUIRED'

interface A9PrototypeSaveEditRecord {
  key: string
  targetLabel: string
  instruction: string
  changes: string[]
  requirementSyncLabel: string
  requirementSyncClass: 'visual' | 'semantic'
  requirementSyncVisualOnly: boolean
  targetArtifactTypes: string[]
  semanticChangeTypes: string[]
  visualChangeTypes: string[]
}

interface A9PrototypeSaveRequirementSyncView {
  mode: A9PrototypeSaveRequirementSyncMode
  visualOnly: boolean
  badge: string
  title: string
  description: string
  reviewNote: string
  targetArtifactTypes: string[]
  semanticChangeLabels: string[]
  visualChangeLabels: string[]
}

const a9PrototypeDraftMetadata = computed<Record<string, unknown>>(() => {
  const draft = props.uiContractDraftContent as unknown as Record<string, unknown> | null

  if (!draft || typeof draft !== 'object' || Array.isArray(draft)) {
    return {}
  }

  const metadata = draft.metadata
  return metadata && typeof metadata === 'object' && !Array.isArray(metadata)
    ? metadata as Record<string, unknown>
    : {}
})

const a9PrototypeSaveEditRecords = computed<A9PrototypeSaveEditRecord[]>(() => {
  const rawRecords = Array.isArray(a9PrototypeDraftMetadata.value.prototypeLocalEdits)
    ? a9PrototypeDraftMetadata.value.prototypeLocalEdits
    : []

  return rawRecords
    .map((item, index) => a9NormalizePrototypeSaveEditRecord(item, index))
    .filter((item) => item.instruction || item.changes.length > 0)
})

const a9PrototypeSaveRequirementSync = computed<A9PrototypeSaveRequirementSyncView>(() =>
  a9ResolvePrototypeSaveRequirementSync(
    a9PrototypeDraftMetadata.value,
    a9PrototypeSaveEditRecords.value,
  ),
)

const a9PrototypeSaveRequirementSyncCardClass = computed(() =>
  a9PrototypeSaveRequirementSync.value.visualOnly
    ? 'state-visual'
    : 'state-semantic',
)

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
): A9PrototypeSaveEditRecord {
  const record = value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}

  const rawSelection = record.targetSelection || record.selectionRef
  const selection = rawSelection && typeof rawSelection === 'object' && !Array.isArray(rawSelection)
    ? rawSelection as Record<string, unknown>
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

  const syncImpact = a9NormalizePrototypeSaveRequirementSyncImpact(record)

  return {
    key: String(record.editId || record.editedAt || `${index}-${targetLabel}`),
    targetLabel,
    instruction: String(record.instruction || '快捷编辑操作'),
    changes,
    requirementSyncLabel: syncImpact.visualOnly ? '纯视觉' : '需同步需求',
    requirementSyncClass: syncImpact.visualOnly ? 'visual' : 'semantic',
    requirementSyncVisualOnly: syncImpact.visualOnly,
    targetArtifactTypes: syncImpact.targetArtifactTypes,
    semanticChangeTypes: syncImpact.semanticChangeTypes,
    visualChangeTypes: syncImpact.visualChangeTypes,
  }
}

function a9ResolvePrototypeSaveRequirementSync(
  metadata: Record<string, unknown>,
  records: A9PrototypeSaveEditRecord[],
): A9PrototypeSaveRequirementSyncView {
  const summary = a9RecordValue(metadata.prototypeRequirementSync)

  if (summary) {
    const mode = String(summary.mode || summary.classification || '').toUpperCase()
    const visualOnly = Boolean(summary.visualOnly) || mode === 'UI_CONTRACT_ONLY'
    const targetArtifactTypes = a9NormalizeStringArray(summary.targetArtifactTypes)
    const semanticChangeLabels = a9ChangeTypeLabels(a9NormalizeStringArray(summary.semanticChangeTypes), 'semantic')
    const visualChangeLabels = a9ChangeTypeLabels(a9NormalizeStringArray(summary.visualChangeTypes), 'visual')
    return a9BuildPrototypeSaveRequirementSyncView(
      visualOnly,
      targetArtifactTypes,
      semanticChangeLabels,
      visualChangeLabels,
    )
  }

  const visualOnly = Boolean(records.length) &&
    records.every((record) => record.requirementSyncVisualOnly)

  const targetArtifactTypes = visualOnly
    ? []
    : ['MODULE_PRD', 'PAGE_SPEC']

  const semanticChangeLabels = a9ChangeTypeLabels(
    Array.from(new Set(records.flatMap((record) => record.semanticChangeTypes))),
    'semantic',
  )
  const visualChangeLabels = a9ChangeTypeLabels(
    Array.from(new Set(records.flatMap((record) => record.visualChangeTypes))),
    'visual',
  )

  return a9BuildPrototypeSaveRequirementSyncView(
    visualOnly,
    targetArtifactTypes,
    semanticChangeLabels,
    visualChangeLabels,
  )
}

function a9BuildPrototypeSaveRequirementSyncView(
  visualOnly: boolean,
  targetArtifactTypes: string[],
  semanticChangeLabels: string[],
  visualChangeLabels: string[],
): A9PrototypeSaveRequirementSyncView {
  if (visualOnly) {
    return {
      mode: 'UI_CONTRACT_ONLY',
      visualOnly: true,
      badge: 'UI only',
      title: '纯视觉 / 布局调整',
      description: '本次只改变颜色、尺寸、对齐、位置或布局样式；保存后仅生成新的 PAGE_UI_CONTRACT 版本，不会要求同步 MODULE_PRD 或 PAGE_SPEC。',
      reviewNote: '适合按钮变宽、靠右、全宽、两列布局、颜色和视觉密度等修改。',
      targetArtifactTypes: [],
      semanticChangeLabels: [],
      visualChangeLabels: visualChangeLabels.length ? visualChangeLabels : ['视觉样式', '布局位置'],
    }
  }

  return {
    mode: 'REQUIREMENT_SYNC_REVIEW_REQUIRED',
    visualOnly: false,
    badge: 'PRD sync',
    title: '涉及需求语义变更',
    description: '本次可能修改字段、按钮业务含义、表格列、页面标题、显示隐藏或跳转状态；保存后会保留 UI_CONTRACT 新版本，并提示复核 MODULE_PRD / PAGE_SPEC。',
    reviewNote: 'Development Agent 会等模块汇总原型确认后再消费；语义变更建议先完成需求同步复核。',
    targetArtifactTypes: targetArtifactTypes.length ? targetArtifactTypes : ['MODULE_PRD', 'PAGE_SPEC'],
    semanticChangeLabels: semanticChangeLabels.length ? semanticChangeLabels : ['字段 / 按钮 / 跳转 / 页面文案'],
    visualChangeLabels,
  }
}

function a9NormalizePrototypeSaveRequirementSyncImpact(
  record: Record<string, unknown>,
): {
  visualOnly: boolean
  targetArtifactTypes: string[]
  semanticChangeTypes: string[]
  visualChangeTypes: string[]
} {
  const impact = a9RecordValue(record.requirementSyncImpact)
  const classification = String(
    impact?.classification ||
      record.requirementSyncClassification ||
      '',
  ).toUpperCase()
  const visualOnly = Boolean(
    impact?.visualOnly ||
      record.visualOnly ||
      classification === 'VISUAL_ONLY',
  )

  return {
    visualOnly,
    targetArtifactTypes: visualOnly
      ? []
      : a9NormalizeStringArray(impact?.targetArtifactTypes || record.targetArtifactTypes || ['MODULE_PRD', 'PAGE_SPEC']),
    semanticChangeTypes: a9NormalizeStringArray(impact?.semanticChangeTypes || record.semanticChangeTypes),
    visualChangeTypes: a9NormalizeStringArray(impact?.visualChangeTypes || record.visualChangeTypes),
  }
}

function a9ChangeTypeLabels(
  values: string[],
  kind: 'semantic' | 'visual',
): string[] {
  const semanticLabels: Record<string, string> = {
    FIELD_OR_VALIDATION: '字段 / 校验',
    TABLE_COLUMN: '表格列',
    ACTION_OR_NAVIGATION: '按钮 / 跳转',
    PAGE_COPY: '页面标题 / 说明',
    VISIBILITY_OR_AVAILABILITY: '显示隐藏 / 可用性',
    STRUCTURE_CHANGE: '结构增删',
  }
  const visualLabels: Record<string, string> = {
    COLOR: '颜色',
    SIZE: '尺寸',
    POSITION: '位置',
    LAYOUT: '布局',
    BUTTON_VARIANT: '按钮样式',
  }
  const labels = kind === 'semantic' ? semanticLabels : visualLabels

  return values
    .map((value) => labels[String(value || '').toUpperCase()] || String(value || '').trim())
    .filter(Boolean)
}

function a9RecordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function a9NormalizeStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}


// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: workspace helpers are kept for branch variants and template feature flags.
void UiContractPreviewRuntime
void togglePrototypeInspectMode
void showPrototypeRequirementUpdateBanner
void prototypeRequirementUpdateMessage
void showPrototypeStaleFromRequirementBanner
void effectiveModulePrdArtifact
void moduleAppPageNavigatorItems
void moduleAppSecondaryPageCount
void moduleAppDefaultPageName
void moduleAppEdgesForActivePage
void moduleAppPrototypeDraftDirty
void openModuleAppPageByEdge
void moduleSectionTitle
void moduleRequirementParagraphs
void moduleRequirementBlocks
void pageDescription

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


/* A9.10.14.4.3.15.4.3.1_REQUIREMENT_UI_CLUTTER_CLEANUP_STYLE */
.a9-prototype-impact-pill {
  display: inline-flex;
  align-items: center;
  max-width: 320px;
  min-height: 34px;
  border: 1px solid #facc15;
  border-radius: 999px;
  padding: 7px 12px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
  font-weight: 900;
  line-height: 1.35;
  box-shadow: 0 10px 24px rgba(245, 158, 11, 0.12);
}

@media (max-width: 860px) {
  .a9-prototype-impact-pill {
    max-width: none;
    width: 100%;
    justify-content: center;
  }
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



.a9-prd-impact-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 12px 0;
  border: 1px solid #fed7aa;
  border-radius: 18px;
  padding: 13px 14px;
  background:
    radial-gradient(circle at 6% 20%, rgba(249, 115, 22, 0.12), transparent 32%),
    linear-gradient(135deg, #fff7ed 0%, #ffffff 100%);
  color: #9a3412;
  box-shadow: 0 14px 34px rgba(249, 115, 22, 0.08);
}

.a9-prd-impact-banner div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prd-impact-banner strong {
  color: #9a3412;
  font-size: 13px;
  font-weight: 950;
}

.a9-prd-impact-banner span {
  color: #7c2d12;
  font-size: 12px;
  line-height: 1.65;
}

.a9-prd-impact-banner button {
  flex: 0 0 auto;
}

@media (max-width: 760px) {
  .a9-prd-impact-banner {
    display: grid;
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

.a9-prd-impact-inline-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 12px 0;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  padding: 13px 14px;
  background:
    radial-gradient(circle at 6% 20%, rgba(37, 99, 235, 0.12), transparent 32%),
    linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
  color: #1e3a8a;
}

.a9-prd-impact-inline-banner div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prd-impact-inline-banner strong {
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 950;
}

.a9-prd-impact-inline-banner span {
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.a9-prd-impact-inline-banner button {
  flex: 0 0 auto;
}

@media (max-width: 860px) {
  .a9-prd-impact-inline-banner {
    display: grid;
  }
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.a9-prototype-save-sync-card {
  display: grid;
  gap: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  padding: 14px;
  background: #eff6ff;
}

.a9-prototype-save-sync-card.state-semantic {
  border-color: #fed7aa;
  background: #fff7ed;
}

.a9-prototype-save-sync-card div {
  display: grid;
  gap: 4px;
}

.a9-prototype-save-sync-card span {
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-prototype-save-sync-card.state-semantic span {
  color: #c2410c;
}

.a9-prototype-save-sync-card strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 950;
}

.a9-prototype-save-sync-card p {
  margin: 0;
}

.a9-prototype-save-sync-card ul {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.a9-prototype-save-sync-card li {
  border-radius: 999px;
  padding: 5px 9px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
}

.a9-prototype-save-sync-card.state-semantic li {
  background: #ffedd5;
  color: #c2410c;
}

.a9-prototype-save-sync-card em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.6;
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

.a9-prototype-save-edit-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.a9-prototype-save-edit-title div {
  min-width: 0;
}

.a9-prototype-save-edit-title em {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  font-size: 11px;
  font-style: normal;
  font-weight: 950;
}

.a9-prototype-save-edit-title em.visual {
  background: #dcfce7;
  color: #166534;
}

.a9-prototype-save-edit-title em.semantic {
  background: #ffedd5;
  color: #c2410c;
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



/* A9.10.13.0.1 MODULE APP PROTOTYPE BEGIN */
.a9-module-app-prototype-card {
  display: grid;
  gap: 16px;
}

.a9-module-app-shell-preview {
  min-height: 430px;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #dbe5f5;
  border-radius: 18px;
  background: #f8fafc;
}

.a9-module-app-sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px 16px;
  background: #111827;
  color: #fff;
}

.a9-module-app-sidebar strong {
  font-size: 16px;
  line-height: 1.3;
}

.a9-module-app-sidebar span {
  margin-bottom: 12px;
  color: #bfdbfe;
  font-size: 12px;
}

.a9-module-app-sidebar button {
  width: 100%;
  border: 0;
  border-radius: 12px;
  padding: 10px 12px;
  background: transparent;
  color: #dbeafe;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.a9-module-app-sidebar button:hover,
.a9-module-app-sidebar button.active {
  background: #2563eb;
  color: #fff;
}

.a9-module-app-canvas {
  min-width: 0;
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 22px;
}

.a9-module-app-canvas header,
.a9-module-app-page-mock {
  border: 1px solid #dbe5f5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
}

.a9-module-app-canvas header {
  padding: 18px 20px;
}

.a9-module-app-canvas header p,
.a9-module-app-canvas header h5 {
  margin: 0;
}

.a9-module-app-canvas header p {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.a9-module-app-canvas header h5 {
  margin-top: 6px;
  color: #0f172a;
  font-size: 22px;
}

.a9-module-app-canvas header span {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.a9-module-app-page-mock {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.mock-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mock-toolbar strong {
  color: #0f172a;
}

.mock-toolbar em {
  border-radius: 999px;
  padding: 4px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.mock-content-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.mock-content-grid article {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.mock-content-grid span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.mock-content-grid strong {
  color: #0f172a;
  font-size: 14px;
}

.a9-module-app-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.a9-module-app-actions button {
  border: 0;
  border-radius: 10px;
  padding: 9px 13px;
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.a9-module-app-empty-action {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}


.a9-module-app-page-real {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.module-app-real-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbe5f5;
  border-radius: 14px;
  padding: 12px 14px;
  background: #ffffff;
}

.module-app-real-toolbar div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.module-app-real-toolbar strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.module-app-real-toolbar span {
  color: #64748b;
  font-size: 12px;
}

.a9-module-app-runtime-frame {
  overflow: hidden;
  width: 100%;
  height: min(760px, 72vh);
  min-height: 560px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: #f3f6fb;
}

.a9-module-app-runtime-frame :deep(.runtime-shell) {
  height: 100%;
  min-height: 0;
}

.a9-module-app-contract-state {
  display: grid;
  gap: 8px;
  border: 1px dashed #bfdbfe;
  border-radius: 16px;
  padding: 18px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 13px;
}

.a9-module-app-contract-state.warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.a9-module-app-contract-state strong {
  color: #92400e;
  font-size: 14px;
}

.a9-module-app-contract-state span,
.a9-module-app-contract-state em {
  color: #92400e;
  font-size: 12px;
  font-style: normal;
  line-height: 1.7;
}

/* A9.10.13.0.4.2 MODULE APP OVERLAY CSS FIX BEGIN */
.a9-module-app-prototype-card.compact {
  gap: 12px;
}

.a9-module-app-summary-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 16px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 18px;
  padding: 16px;
  background:
    radial-gradient(circle at 6% 14%, rgba(37, 99, 235, 0.11), transparent 30%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.06);
}

.a9-module-app-summary-main {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.a9-module-app-summary-main p {
  margin: 0;
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-module-app-summary-main strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a9-module-app-summary-main span {
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.a9-module-app-summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(74px, 1fr));
  gap: 8px;
}

.a9-module-app-summary-metrics article {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.88);
}

.a9-module-app-summary-metrics strong {
  display: block;
  color: #1d4ed8;
  font-size: 18px;
  line-height: 1.1;
}

.a9-module-app-summary-metrics span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
}

.a9-module-app-overlay-mask {
  position: fixed;
  inset: 0;
  z-index: 2600;
  display: grid;
  padding: 24px;
  background: rgba(15, 23, 42, 0.52);
  backdrop-filter: blur(8px);
}

.a9-module-app-overlay-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  width: min(1680px, calc(100vw - 48px));
  height: min(940px, calc(100vh - 48px));
  margin: auto;
  overflow: hidden;
  border: 1px solid rgba(191, 219, 254, 0.72);
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 38px 100px rgba(15, 23, 42, 0.34);
}

.a9-module-app-overlay-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #e2e8f0;
  padding: 18px 22px;
  background:
    radial-gradient(circle at 4% 0%, rgba(37, 99, 235, 0.12), transparent 30%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.a9-module-app-overlay-header p,
.a9-module-app-overlay-header h3 {
  margin: 0;
}

.a9-module-app-overlay-header p {
  color: #2563eb;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.a9-module-app-overlay-header h3 {
  margin-top: 5px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 950;
  letter-spacing: -0.03em;
}

.a9-module-app-overlay-header span {
  display: block;
  margin-top: 5px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.a9-module-app-overlay-header > button {
  flex: 0 0 auto;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #ffffff;
  color: #64748b;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.a9-module-app-overlay-header > button:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.a9-module-app-shell-preview.overlay-mode {
  min-height: 0;
  height: 100%;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.a9-module-app-shell-preview.overlay-mode .a9-module-app-sidebar {
  overflow: auto;
}

.a9-module-app-shell-preview.overlay-mode .a9-module-app-canvas {
  overflow: auto;
  padding: 20px;
  background: #f3f6fb;
}

.a9-module-app-shell-preview.overlay-mode .a9-module-app-runtime-frame {
  height: min(760px, calc(100vh - 250px));
  min-height: 560px;
}

@media (max-width: 1180px) {
  .a9-module-app-summary-card {
    grid-template-columns: 1fr;
  }

  .a9-module-app-summary-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .a9-module-app-overlay-mask {
    padding: 12px;
  }

  .a9-module-app-overlay-panel {
    width: calc(100vw - 24px);
    height: calc(100vh - 24px);
    border-radius: 20px;
  }

  .a9-module-app-shell-preview.overlay-mode {
    grid-template-columns: 190px minmax(0, 1fr);
  }
}

:global(body.a9-module-app-overlay-open) {
  overflow: hidden;
}
/* A9.10.13.0.4.2 MODULE APP OVERLAY CSS FIX END */

/* A9.10.13.0.1 MODULE APP PROTOTYPE END */


/* A9.10.13.0.5 MODULE HEADER PREVIEW BUTTON */
.a9-header-module-preview-button {
  white-space: nowrap;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.18);
}

.a9-module-document-card {
  display: grid;
  gap: 0;
}

.a9-module-document-card :deep(.module-prd-view),
.a9-module-document-card :deep(.prd-document-shell),
.a9-module-document-card :deep(.prd-document) {
  margin-top: 0;
}

.a9-module-app-overlay-header p {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-module-app-overlay-header span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.a9-module-app-overlay-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.a9-module-app-shell-preview.overlay-mode {
  min-height: 0;
  height: 100%;
}

.a9-module-app-shell-preview.overlay-mode .a9-module-app-canvas {
  overflow: auto;
}





/* A9.10.14.4.3.15.4.5_PROTOTYPE_STUDIO_PAGE_NAVIGATOR: non-terminal module overview */
.a9-module-overview-card {
  display: grid;
  gap: 16px;
}

.a9-module-overview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.a9-module-overview-head p {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.a9-module-overview-head h4 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
}

.a9-module-overview-head > span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
}

.a9-module-overview-copy {
  display: grid;
  gap: 8px;
}

.a9-module-overview-copy p {
  margin: 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.75;
}

.a9-module-overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.a9-module-overview-grid article {
  display: grid;
  align-content: start;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
}

.a9-module-overview-grid strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 950;
}

.a9-module-overview-grid ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.55;
}

.a9-module-overview-grid p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}


/* A9.10.14.4.3.15.4.10.2_MODULE_PRD_USER_VIEW_FLEX_WORKSPACE
   Keep MODULE_PRD as one clean user-facing document area: no extra card shell,
   and keep the only PRD version pill beside the module preview action.
*/
.a9-module-document-card {
  border: none !important;
  border-radius: 0 !important;
  padding: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.a9-header-prd-version-pill {
  border-radius: 999px;
  padding: 6px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
  line-height: 1;
}


/* A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION */
.a9-prd-tree-completion-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
  padding: 13px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: #eff6ff;
}

.a9-prd-tree-completion-banner div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.a9-prd-tree-completion-banner strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 850;
}

.a9-prd-tree-completion-banner span {
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.a9-prd-tree-completion-banner button {
  flex: 0 0 auto;
}

</style>
