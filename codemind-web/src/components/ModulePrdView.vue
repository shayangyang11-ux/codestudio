<template>
  <section class="module-prd-view">
    <div class="a9-prd-user-actions">
      <button type="button" class="a9-prd-add-content-button" :disabled="manualEditSaving" @click="openAddContentDialog">
        新增内容
      </button>
    </div>

    <div v-if="hasReadablePrd" class="prd-reader-view">
      <article
        v-for="section in readerSections"
        :key="section.key"
        class="prd-reader-section"
      >
        <header class="prd-reader-section-header">
          <div>
            <h4>{{ section.title }}</h4>
            <p v-if="section.description">{{ section.description }}</p>
            <div class="a9-prd-section-type-row">
              <span
                :class="[
                  'a9-prd-section-type-pill',
                  section.customTitleEditable ? 'document-only' : 'business-structure',
                ]"
              >
                {{ section.customTitleEditable ? '补充说明' : '业务结构' }}
              </span>
              <span class="a9-prd-section-type-hint">
                {{ section.customTitleEditable ? '只影响文档内容' : '用于后续页面设计和原型提示' }}
              </span>
            </div>
          </div>
          <div class="a9-prd-section-header-actions">
            <button
              v-if="section.customTitleEditable"
              type="button"
              class="a9-prd-mini-action"
              :disabled="manualEditSaving || isFirstCustomSection(section)"
              @click="moveCustomSection(section, -1)"
            >
              上移
            </button>
            <button
              v-if="section.customTitleEditable"
              type="button"
              class="a9-prd-mini-action"
              :disabled="manualEditSaving || isLastCustomSection(section)"
              @click="moveCustomSection(section, 1)"
            >
              下移
            </button>
            <button
              type="button"
              class="a9-prd-edit-icon-button"
              :title="`编辑${section.title}`"
              :aria-label="`编辑${section.title}`"
              :disabled="manualEditSaving"
              @click="openSectionEditor(section)"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 17.75V20h2.25L17.8 8.45l-2.25-2.25L4 17.75Zm15.52-11.08a.75.75 0 0 0 0-1.06l-1.13-1.13a.75.75 0 0 0-1.06 0l-.88.88 2.25 2.25.82-.94Z" />
              </svg>
            </button>
            <button
              v-if="section.customTitleEditable"
              type="button"
              class="a9-prd-mini-action danger"
              :disabled="manualEditSaving"
              @click="deleteCustomSection(section)"
            >
              删除
            </button>
          </div>
        </header>

        <div
          v-if="section.renderMode === 'markdown'"
          class="prd-reader-markdown-body"
          v-html="section.html"
        />

        <div v-else-if="section.key === 'module_basic_info'" class="a9-basic-grid">
          <article v-for="item in basicInfoItems" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value || '-' }}</strong>
          </article>
        </div>

        <div v-else-if="section.key === 'page_requirements'" class="prd-page-contract-document">
          <article
            v-for="(item, index) in normalizedRecords(section.value)"
            :key="itemKey(item, index, section.key)"
            class="prd-page-doc-item"
          >
            <header class="prd-page-doc-header">
              <div class="prd-page-doc-index">{{ index + 1 }}</div>
              <div class="prd-page-doc-title-copy">
                <div class="prd-page-doc-title-row">
                  <h5>{{ itemTitle(item, index, section.key) }}</h5>
                  <em v-if="pageTypeDisplay(item)">{{ pageTypeDisplay(item) }}</em>
                </div>
                <p>{{ itemDescription(item) }}</p>
              </div>
            </header>

            <section
              v-if="listNotEmpty(item.form_fields || item.formFields)"
              class="prd-page-doc-subsection"
            >
              <h6>主要信息</h6>
              <p>{{ joinList(item.form_fields || item.formFields) }}</p>
            </section>

            <section
              v-if="listNotEmpty(item.filter_fields || item.filterFields)"
              class="prd-page-doc-subsection"
            >
              <h6>筛选字段</h6>
              <p>{{ joinList(item.filter_fields || item.filterFields) }}</p>
            </section>

            <section
              v-if="listNotEmpty(item.table_columns || item.tableColumns)"
              class="prd-page-doc-subsection"
            >
              <h6>列表字段</h6>
              <p>{{ joinList(item.table_columns || item.tableColumns) }}</p>
            </section>

            <section class="prd-page-doc-action-grid">
              <div v-if="listNotEmpty(item.primary_actions || item.primaryActions || item.user_actions)">
                <h6>主要操作</h6>
                <p>{{ joinList(item.primary_actions || item.primaryActions || item.user_actions) }}</p>
              </div>
              <div v-if="listNotEmpty(item.row_actions || item.rowActions)">
                <h6>行操作</h6>
                <p>{{ joinList(item.row_actions || item.rowActions) }}</p>
              </div>
              <div v-if="listNotEmpty(item.navigation_targets || item.navigationTargets)">
                <h6>关联页面</h6>
                <p>{{ joinList(item.navigation_targets || item.navigationTargets) }}</p>
              </div>
            </section>
          </article>
        </div>

        <div v-else-if="Array.isArray(section.value)" class="compact-card-list">
          <article
            v-for="(item, index) in normalizedRecords(section.value)"
            :key="itemKey(item, index, section.key)"
            class="compact-card"
          >
            <h5>{{ itemTitle(item, index, section.key) }}</h5>
            <p>{{ itemDescription(item) }}</p>
            <p
              v-for="detail in itemDetails(item, section.key)"
              :key="detail.label"
            >
              <strong>{{ detail.label }}：</strong>{{ detail.value }}
            </p>
          </article>
        </div>

        <div v-else-if="isRecord(section.value)" class="hint-grid">
          <article
            v-for="entry in objectEntries(section.value)"
            :key="entry.key"
            class="hint-card"
          >
            <h5>{{ readableKey(entry.key) }}</h5>
            <p>{{ valueText(entry.value) }}</p>
          </article>
        </div>

        <div v-else class="prd-reader-markdown-body">
          <p>{{ displayText(section.value) }}</p>
        </div>
      </article>
    </div>
    <div v-else class="prd-reader-empty">暂无模块需求内容。</div>


    <div
      v-if="addContentDialogVisible"
      class="a9-prd-section-editor-mask"
      role="dialog"
      aria-modal="true"
      aria-label="新增内容"
      @click.self="closeAddContentDialog"
    >
      <article class="a9-prd-add-content-dialog">
        <header class="a9-prd-section-editor-header">
          <div>
            <p>新增内容</p>
            <h3>你想补充哪类内容？</h3>
            <span>业务内容会参与后续页面设计和原型提示；补充说明只作为文档内容保存。</span>
          </div>
          <button type="button" aria-label="关闭" @click="closeAddContentDialog">×</button>
        </header>

        <section class="a9-prd-add-choice-grid">
          <button type="button" @click="openStructuredAddEditor('functional_requirements')">
            <strong>新增业务能力</strong>
            <span>例如创建、查询、审核、导出等业务功能。</span>
          </button>
          <button type="button" @click="openStructuredAddEditor('page_requirements')">
            <strong>新增页面设计</strong>
            <span>例如列表页、创建页、详情页、审批页。</span>
          </button>
          <button type="button" @click="openStructuredAddEditor('business_rules')">
            <strong>新增业务规则</strong>
            <span>例如必填校验、状态限制、数量规则。</span>
          </button>
          <button type="button" @click="openStructuredAddEditor('data_requirements')">
            <strong>新增数据对象</strong>
            <span>例如业务单据、明细、审核记录。</span>
          </button>
          <button type="button" @click="openStructuredAddEditor('state_rules')">
            <strong>新增状态流转</strong>
            <span>例如草稿、待审核、已完成、已驳回。</span>
          </button>
          <button type="button" @click="openStructuredAddEditor('acceptance_criteria')">
            <strong>新增验收标准</strong>
            <span>用于说明交付时如何确认该模块可用。</span>
          </button>
          <button type="button" class="document-only" @click="openCustomSectionCreate">
            <strong>新增补充说明</strong>
            <span>自由栏目，可修改标题和正文，不直接影响模块树或原型。</span>
          </button>
        </section>
      </article>
    </div>

    <div
      v-if="customSectionDialogVisible"
      class="a9-prd-section-editor-mask"
      role="dialog"
      aria-modal="true"
      aria-label="新增补充说明"
      @click.self="cancelCustomSectionCreate"
    >
      <article class="a9-prd-section-editor-dialog compact">
        <header class="a9-prd-section-editor-header">
          <div>
            <p>补充说明</p>
            <h3>新增自由栏目</h3>
            <span>自由栏目只进入 PRD 文档，不直接触发模块树、页面规范或原型变更。</span>
          </div>
          <button type="button" aria-label="关闭" @click="cancelCustomSectionCreate">×</button>
        </header>

        <section class="a9-prd-section-editor-body">
          <div class="a9-custom-section-editor">
            <label>
              栏目标题
              <input v-model="customSectionTitle" placeholder="例如：待确认问题、客户补充说明、上线备注" />
            </label>
            <label>
              正文内容
              <textarea v-model="customSectionBody" rows="8" placeholder="请输入补充说明内容。" />
            </label>
          </div>
          <p v-if="customSectionError" class="a9-prd-section-edit-error">{{ customSectionError }}</p>
        </section>

        <footer class="a9-prd-section-editor-footer">
          <button type="button" class="a9-prd-secondary-button" :disabled="manualEditSaving" @click="cancelCustomSectionCreate">取消</button>
          <button type="button" class="a9-prd-primary-button" :disabled="manualEditSaving" @click="saveCustomSectionCreate">
            {{ manualEditSaving ? '保存中...' : '保存为新版本' }}
          </button>
        </footer>
      </article>
    </div>

    <div
      v-if="editingSection"
      class="a9-prd-section-editor-mask"
      role="dialog"
      aria-modal="true"
      :aria-label="`编辑${editingSection.title}`"
      @click.self="cancelSectionEdit"
    >
      <article class="a9-prd-section-editor-dialog">
        <header class="a9-prd-section-editor-header">
          <div>
            <p>{{ editorKindLabel }}</p>
            <h3>{{ editingSection.title }}</h3>
            <span>{{ sectionEditorHint }}</span>
          </div>
          <button type="button" aria-label="关闭" @click="cancelSectionEdit">×</button>
        </header>

        <section class="a9-prd-section-editor-body">
          <div v-if="isEditingPageRequirements" class="a9-page-requirement-editor">
            <div class="a9-page-editor-intro">
              <div>
                <strong>页面与操作设计</strong>
                <p>像填写表单一样维护页面用途、主要信息、操作和关联页面；系统会在内部维护页面标识、路由和布局。</p>
              </div>
              <button type="button" class="a9-prd-secondary-button" @click="addPageRequirementDraft">
                新增页面设计
              </button>
            </div>

            <article
              v-for="(draft, index) in pageRequirementDrafts"
              :key="draft._draftKey || index"
              class="a9-page-requirement-editor-card"
            >
              <header>
                <div>
                  <span>页面 {{ index + 1 }}</span>
                  <strong>{{ draft.page_name || '未命名页面' }}</strong>
                </div>
                <button
                  type="button"
                  class="a9-page-remove-button"
                  :disabled="pageRequirementDrafts.length <= 1"
                  @click="removePageRequirementDraft(index)"
                >
                  移除
                </button>
              </header>

              <div class="a9-page-form-grid">
                <label>
                  页面名称
                  <input v-model="draft.page_name" placeholder="例如：入库单创建页" />
                </label>
                <label>
                  页面类型
                  <select v-model="draft.page_type">
                    <option v-for="option in pageTypeOptions" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                </label>
              </div>

              <label class="a9-page-form-full">
                页面说明
                <textarea v-model="draft.description" rows="2" placeholder="说明该页面承担的业务场景和页面目标。" />
              </label>

              <div class="a9-page-form-grid two">
                <label>
                  表单字段
                  <textarea v-model="draft.form_fields_text" rows="2" placeholder="用顿号、逗号或换行分隔，例如：入库单号、供应商、仓库" />
                </label>
                <label>
                  筛选字段
                  <textarea v-model="draft.filter_fields_text" rows="2" placeholder="例如：入库单号、供应商、入库状态" />
                </label>
                <label>
                  列表字段
                  <textarea v-model="draft.table_columns_text" rows="2" placeholder="例如：入库单号、供应商、仓库、创建时间" />
                </label>
                <label>
                  主要操作
                  <textarea v-model="draft.primary_actions_text" rows="2" placeholder="例如：保存、提交审核、取消" />
                </label>
                <label>
                  行操作
                  <textarea v-model="draft.row_actions_text" rows="2" placeholder="例如：查看详情、编辑、查看审核" />
                </label>
                <label>
                  跳转关系
                  <textarea v-model="draft.navigation_targets_text" rows="2" placeholder="例如：保存后返回列表页；查看详情进入详情页" />
                </label>
              </div>
            </article>
          </div>

          <div v-else-if="isGenericRecordEditor" class="a9-generic-section-editor">
            <div class="a9-generic-editor-intro">
              <div>
                <strong>{{ editingSection.title }}</strong>
                <p>这类内容会用于后续页面设计、原型定位或同步提示，请按业务信息逐项维护。</p>
              </div>
              <button type="button" class="a9-prd-secondary-button" @click="addGenericRecordDraft">
                {{ addRecordButtonLabel }}
              </button>
            </div>

            <article
              v-for="(draft, index) in genericRecordDrafts"
              :key="draft._draftKey || index"
              class="a9-generic-record-editor-card"
            >
              <header>
                <div>
                  <span>{{ recordItemLabel(editingSection?.key || '', index) }}</span>
                  <strong>{{ genericDraftTitle(draft, index) }}</strong>
                </div>
                <button
                  type="button"
                  class="a9-page-remove-button"
                  :disabled="genericRecordDrafts.length <= 1"
                  @click="removeGenericRecordDraft(index)"
                >
                  移除
                </button>
              </header>

              <div class="a9-generic-field-grid">
                <label
                  v-for="field in draft._fields"
                  :key="field.key"
                  :class="{ wide: field.multiline }"
                >
                  {{ field.label }}
                  <textarea
                    v-if="field.multiline"
                    v-model="field.value"
                    rows="2"
                    :placeholder="field.placeholder"
                  />
                  <input
                    v-else
                    v-model="field.value"
                    :placeholder="field.placeholder"
                  />
                </label>
              </div>
            </article>
          </div>

          <div v-else-if="isGenericObjectEditor" class="a9-generic-section-editor">
            <div class="a9-generic-editor-intro">
              <div>
                <strong>{{ editingSection.title }}</strong>
                <p>按字段维护本章节内容；保存时仍写回结构化 PRD 对象。</p>
              </div>
            </div>

            <div class="a9-generic-object-editor-card">
              <label
                v-for="field in genericObjectDrafts"
                :key="field.key"
                :class="{ wide: field.multiline }"
              >
                {{ field.label }}
                <textarea
                  v-if="field.multiline"
                  v-model="field.value"
                  rows="2"
                  :placeholder="field.placeholder"
                />
                <input
                  v-else
                  v-model="field.value"
                  :placeholder="field.placeholder"
                />
              </label>
            </div>
          </div>

          <div v-else-if="isEditingCustomSection" class="a9-custom-section-editor">
            <label>
              栏目标题
              <input v-model="sectionDraftTitle" placeholder="请输入栏目标题。" />
            </label>
            <label>
              正文内容
              <textarea v-model="sectionDraftText" rows="10" spellcheck="false" />
            </label>
          </div>

          <textarea
            v-else
            v-model="sectionDraftText"
            spellcheck="false"
          />
          <p v-if="sectionEditError" class="a9-prd-section-edit-error">
            {{ sectionEditError }}
          </p>
        </section>

        <footer class="a9-prd-section-editor-footer">
          <button type="button" class="a9-prd-secondary-button" :disabled="manualEditSaving" @click="cancelSectionEdit">
            取消
          </button>
          <button type="button" class="a9-prd-primary-button" :disabled="manualEditSaving" @click="submitSectionEdit">
            {{ manualEditSaving ? '保存中...' : '保存本章节为新版本' }}
          </button>
        </footer>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
// A9.10.14.4.3.15.1.3_PRD_READER_STYLE_SECTION_EDIT_HOTFIX
import { computed, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type {
  ArtifactResponse,
  ManualArtifactEditPayload,
} from '../types/codemind'

type PrdSection = {
  key: string
  title: string
  description: string
  value: unknown
  kind: 'object' | 'array' | 'text'
  countLabel: string
  renderMode?: 'structured' | 'markdown'
  markdownBody?: string
  html?: string
  headingLevel?: number
  customSectionId?: string
  customTitleEditable?: boolean
  customOrder?: number
}

const props = withDefaults(defineProps<{
  artifact: ArtifactResponse | null
  markdown: string
  manualEditSaving?: boolean
  allowCreateFromMarkdown?: boolean
}>(), {
  allowCreateFromMarkdown: false,
  manualEditSaving: false,
})

const emit = defineEmits<{
  'save-manual-edit': [payload: ManualArtifactEditPayload]
}>()

const MARKER = 'A9.10.14.4.3.15.2.6_PRD_UNIFIED_UI_TREE_COLLAPSE'

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const editingSection = ref<PrdSection | null>(null)
const sectionDraftText = ref('')
const sectionDraftTitle = ref('')
const sectionEditError = ref('')
const pageRequirementDrafts = ref<Array<Record<string, any>>>([])
const genericRecordDrafts = ref<GenericRecordDraft[]>([])
const genericObjectDrafts = ref<GenericFieldDraft[]>([])
const genericObjectSource = ref<Record<string, any>>({})
const addContentDialogVisible = ref(false)
const customSectionDialogVisible = ref(false)
const customSectionTitle = ref('')
const customSectionBody = ref('')
const customSectionError = ref('')

const pageTypeOptions = [
  { value: 'LIST', label: '列表页 / 查询页' },
  { value: 'FORM', label: '表单页 / 创建页' },
  { value: 'DETAIL', label: '详情页' },
  { value: 'EDIT', label: '编辑页' },
  { value: 'APPROVAL', label: '审核页 / 审批页' },
  { value: 'DASHBOARD', label: '看板页' },
  { value: 'PORTAL', label: '门户页 / 首页' },
  { value: 'CONFIG', label: '配置页' },
  { value: 'REPORT', label: '报表页' },
  { value: 'WIZARD', label: '向导页' },
]


const prd = computed<Record<string, any>>(() => {
  const content = props.artifact?.content
  return content && typeof content === 'object' && !Array.isArray(content)
    ? content as Record<string, any>
    : {}
})

const basicInfo = computed<Record<string, any>>(() => recordValue(prd.value.module_basic_info) || {})

const userRoles = computed<any[]>(() => asArray(prd.value.user_roles))
const functionalRequirements = computed<any[]>(() => asArray(prd.value.functional_requirements))
const businessRules = computed<any[]>(() => asArray(prd.value.business_rules))
const stateRules = computed<any[]>(() => asArray(prd.value.state_rules))
const pageRequirements = computed<any[]>(() => asArray(prd.value.page_requirements))
const dataRequirements = computed<any[]>(() => asArray(prd.value.data_requirements))
const exceptionScenarios = computed<any[]>(() => asArray(prd.value.exception_scenarios))
const acceptanceCriteria = computed<any[]>(() => asArray(prd.value.acceptance_criteria))
const nonFunctionalRequirements = computed<any[]>(() => asArray(prd.value.non_functional_requirements))
const customSections = computed<PrdSection[]>(() => normalizeCustomSections(prd.value.custom_sections || prd.value.customSections))

const hasStructuredPrd = computed(() => {
  const keys = [
    'module_basic_info',
    'business_background',
    'user_roles',
    'functional_requirements',
    'business_rules',
    'state_rules',
    'page_requirements',
    'data_requirements',
    'custom_sections',
  ]
  return keys.some((key) => hasMeaningfulValue(prd.value[key]))
})

const markdownSections = computed(() => parseMarkdownSections(props.markdown || ''))
const hasReadablePrd = computed(() => hasStructuredPrd.value || markdownSections.value.length > 0 || Boolean((props.markdown || '').trim()))

const documentTitle = computed(() => {
  const firstH1 = markdownSections.value.find((section) => section.headingLevel === 1)
  return (
    basicInfo.value.module_name ||
    firstH1?.title ||
    props.artifact?.title ||
    '模块需求'
  )
})

const basicInfoItems = computed(() => [
  { label: '模块位置', value: basicInfo.value.module_position },
  { label: '模块目标', value: basicInfo.value.module_goal },
  { label: '职责边界', value: basicInfo.value.responsibility_boundary },
].filter((item) => valueText(item.value) !== '-'))

const structuredSections = computed<PrdSection[]>(() => {
  const sections = [
    makeSection('module_basic_info', '模块概述', '模块目标与职责边界。', basicInfo.value, 'object'),
    makeSection('business_background', '业务背景', '业务场景、当前问题与建设背景。', prd.value.business_background, 'text'),
    makeSection('user_roles', '用户角色', '参与该模块的业务角色与职责。', userRoles.value, 'array'),
    makeSection('functional_requirements', '功能需求', '可开发、可测试、可验收的业务能力。', functionalRequirements.value, 'array'),
    makeSection('business_rules', '业务规则', '关键规则、触发条件与期望结果。', businessRules.value, 'array'),
    makeSection('state_rules', '状态与流转', '业务状态、流转边界和异常状态。', stateRules.value, 'array'),
    makeSection('page_requirements', '页面与操作设计', '页面用途、主要信息、操作和关联页面。', pageRequirements.value, 'array'),
    makeSection('data_requirements', '数据需求', '业务实体、关键字段和约束。', dataRequirements.value, 'array'),
    makeSection('exception_scenarios', '异常场景', '异常输入、状态冲突和处理策略。', exceptionScenarios.value, 'array'),
    makeSection('acceptance_criteria', '验收标准', '验收口径、验证方式和交付检查点。', acceptanceCriteria.value, 'array'),
    makeSection('non_functional_requirements', '非功能需求', '性能、安全、可用性等质量要求。', nonFunctionalRequirements.value, 'array'),
  ].filter((section) => hasMeaningfulValue(section.value))

  return [...sections, ...customSections.value]
})

const readerSections = computed<PrdSection[]>(() => {
  if (hasStructuredPrd.value) {
    return structuredSections.value
  }

  const sections = markdownSections.value
    .filter((section) => section.headingLevel !== 1 || section.body.trim())
    .filter((section) => section.title.trim() || section.body.trim())
    .map((section, index) => ({
      key: section.key || `markdown_${index + 1}`,
      title: section.title || `章节 ${index + 1}`,
      description: '',
      value: section.body,
      kind: 'text' as const,
      countLabel: '',
      renderMode: 'markdown' as const,
      markdownBody: section.body,
      html: renderMarkdown(section.body || '暂无内容。'),
      headingLevel: section.headingLevel,
    }))

  if (sections.length > 0) {
    return sections
  }

  return [{
    key: 'document',
    title: documentTitle.value,
    description: '',
    value: props.markdown || '',
    kind: 'text' as const,
    countLabel: '',
    renderMode: 'markdown' as const,
    markdownBody: props.markdown || '',
    html: renderMarkdown(props.markdown || '暂无内容。'),
    headingLevel: 1,
  }]
})

const isEditingPageRequirements = computed(() => editingSection.value?.key === 'page_requirements')
const isEditingCustomSection = computed(() => Boolean(editingSection.value?.customTitleEditable))

const sectionEditorMode = computed<'text' | 'record-list' | 'object-form'>(() => {
  const section = editingSection.value
  if (!section) return 'text'
  if (section.renderMode === 'markdown') return 'text'
  if (section.key === 'page_requirements') return 'record-list'
  if (section.kind === 'array') return 'record-list'
  if (section.kind === 'object') return 'object-form'
  return 'text'
})

const isGenericRecordEditor = computed(() => {
  return sectionEditorMode.value === 'record-list' && !isEditingPageRequirements.value
})

const isGenericObjectEditor = computed(() => {
  return sectionEditorMode.value === 'object-form'
})

const sectionEditorHint = computed(() => {
  if (isEditingPageRequirements.value) {
    return '这是会影响后续页面设计和原型提示的业务内容，请按页面用途、信息和操作维护。'
  }
  if (isEditingCustomSection.value) {
    return '自由栏目只作为文档补充保存，标题和正文都可以修改。'
  }
  if (isGenericRecordEditor.value || isGenericObjectEditor.value) {
    return '这是会影响后续需求结构和原型提示的业务内容，请用表单维护，系统会在内部保持同步。'
  }
  return '编辑当前补充正文，保存后生成新的需求版本。'
})

const editorKindLabel = computed(() => {
  if (isEditingCustomSection.value) return '编辑补充说明'
  if (isEditingPageRequirements.value) return '编辑页面设计'
  if (isGenericRecordEditor.value || isGenericObjectEditor.value) return '编辑业务结构'
  return '编辑栏目'
})

const addRecordButtonLabel = computed(() => structuredAddButtonLabel(editingSection.value?.key || ''))

watch(
  () => [props.artifact?.artifactId || '', props.artifact?.versionNo || '', props.markdown || ''].join('|'),
  () => {
    editingSection.value = null
    sectionDraftText.value = ''
    sectionDraftTitle.value = ''
    sectionEditError.value = ''
    pageRequirementDrafts.value = []
    genericRecordDrafts.value = []
    genericObjectDrafts.value = []
    genericObjectSource.value = {}
    addContentDialogVisible.value = false
    customSectionDialogVisible.value = false
    customSectionTitle.value = ''
    customSectionBody.value = ''
    customSectionError.value = ''
  },
)

function makeSection(
  key: string,
  title: string,
  description: string,
  value: unknown,
  kind: 'object' | 'array' | 'text',
): PrdSection {
  let countLabel = ''
  if (Array.isArray(value)) {
    countLabel = `${value.length} 项`
  }
  return {
    key,
    title,
    description,
    value,
    kind,
    countLabel,
    renderMode: 'structured',
  }
}



function normalizeCustomSections(value: unknown): PrdSection[] {
  return normalizeCustomSectionRecords(value)
    .sort((left, right) => (Number(left.order || 0) || 0) - (Number(right.order || 0) || 0))
    .map((item, index) => makeCustomSection(item, index))
}

function normalizeCustomSectionRecords(value: unknown): Array<Record<string, any>> {
  return asArray(value)
    .map((item, index) => {
      const record = recordValue(item) || {}
      const title = valueText(record.title || record.section_title || record.sectionTitle || record.name)
      const body = valueText(record.body || record.content || record.markdown || record.text || record.description)
      return {
        section_id: String(record.section_id || record.sectionId || record.id || `custom_section_${index + 1}`).trim(),
        title: title === '-' ? `补充说明 ${index + 1}` : title,
        body: body === '-' ? '' : body,
        order: Number(record.order ?? record.sortOrder ?? index + 1) || index + 1,
        visibility: record.visibility || 'USER_VISIBLE',
        source: record.source || 'USER',
        impact_policy: record.impact_policy || record.impactPolicy || 'DOCUMENT_ONLY',
      }
    })
    .filter((item) => String(item.title || '').trim() || String(item.body || '').trim())
}

function makeCustomSection(item: Record<string, any>, index: number): PrdSection {
  const title = String(item.title || `补充说明 ${index + 1}`).trim()
  const body = String(item.body || '').trim()
  return {
    key: `custom_section_${item.section_id || index + 1}`,
    title,
    description: '用户自定义补充说明，不直接影响模块树、页面规范或原型。',
    value: body,
    kind: 'text',
    countLabel: '',
    renderMode: 'markdown',
    markdownBody: body,
    html: renderMarkdown(body || '暂无内容。'),
    headingLevel: 2,
    customSectionId: String(item.section_id || `custom_section_${index + 1}`),
    customTitleEditable: true,
    customOrder: Number(item.order || index + 1) || index + 1,
  }
}

function customSectionRecordsFromContent(content: Record<string, any>): Array<Record<string, any>> {
  return normalizeCustomSectionRecords(content.custom_sections || content.customSections)
}
function openSectionEditor(section: PrdSection) {
  editingSection.value = section
  sectionEditError.value = ''
  pageRequirementDrafts.value = []
  genericRecordDrafts.value = []
  genericObjectDrafts.value = []

  if (section.customTitleEditable) {
    sectionDraftTitle.value = section.title || ''
    sectionDraftText.value = section.markdownBody || valueText(section.value) || ''
    return
  }

  if (section && section.key === 'page_requirements') {
    pageRequirementDrafts.value = normalizePageRequirementDrafts(section.value)
    return
  }

  if (section.renderMode === 'markdown') {
    sectionDraftText.value = section.markdownBody || valueText(section.value)
    return
  }

  if (section.kind === 'array') {
    genericRecordDrafts.value = buildGenericRecordDrafts(section)
    return
  }

  if (section.kind === 'object') {
    genericObjectDrafts.value = buildGenericObjectDrafts(section)
    return
  }

  sectionDraftText.value = valueText(section.value) === '-' ? '' : valueText(section.value)
}

function cancelSectionEdit() {
  editingSection.value = null
  sectionDraftText.value = ''
  sectionEditError.value = ''
  pageRequirementDrafts.value = []
  genericRecordDrafts.value = []
  genericObjectDrafts.value = []
  genericObjectSource.value = {}
}

function submitSectionEdit() {
  const section = editingSection.value
  if (!section) return

  sectionEditError.value = ''

  try {
    if (isEditingCustomSection.value) {
      const nextContent = deepCloneRecord(prd.value)
      const records = customSectionRecordsFromContent(nextContent)
      const targetId = section.customSectionId || section.key
      const title = sectionDraftTitle.value.trim()
      const body = sectionDraftText.value.trim()

      if (!title) {
        throw new Error('请填写栏目标题。')
      }

      const nextRecords = records.map((record, index) => {
        const id = String(record.section_id || record.sectionId || `custom_section_${index + 1}`)
        if (id !== targetId) return record
        return {
          ...record,
          section_id: id,
          title,
          body,
          order: Number(record.order || index + 1) || index + 1,
          visibility: 'USER_VISIBLE',
          source: record.source || 'USER',
          impact_policy: 'DOCUMENT_ONLY',
        }
      })

      nextContent.custom_sections = nextRecords
      nextContent._prd_section_edit = {
        sectionKey: section.key,
        sectionTitle: title,
        editedAt: new Date().toISOString(),
        source: MARKER,
        editorMode: 'CUSTOM_SECTION_TEXT',
        impactPolicy: 'DOCUMENT_ONLY',
      }

      emit('save-manual-edit', {
        artifactId: props.artifact?.artifactId || null,
        artifactType: 'MODULE_PRD',
        content: nextContent,
        contentMarkdown: buildModulePrdMarkdown(nextContent),
        reason: `自由栏目编辑：${title}`,
      } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

      cancelSectionEdit()
      return
    }

    if (section && section.key === 'page_requirements') {
      const nextContent = deepCloneRecord(prd.value)
      const nextValue = pageRequirementDrafts.value
        .map((draft, index) => pageRequirementFromDraft(draft, index))
        .filter((item) => item.page_name || item.page_key)

      if (!nextValue.length) {
        throw new Error('至少保留一个页面契约。')
      }

      nextContent[section.key] = nextValue
      nextContent._prd_section_edit = {
        sectionKey: section.key,
        sectionTitle: section.title,
        editedAt: new Date().toISOString(),
        source: MARKER,
        editorMode: 'PAGE_REQUIREMENT_FORM',
        prototypeImpact: 'PAGE_REQUIREMENTS_CHANGED',
        moduleTreeImpact: 'PENDING_USER_CONFIRMATION',
      }

      emit('save-manual-edit', {
        artifactId: props.artifact?.artifactId || null,
        artifactType: 'MODULE_PRD',
        content: nextContent,
        contentMarkdown: buildModulePrdMarkdown(nextContent),
        reason: `页面契约表单编辑：${section.title}`,
      } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

      cancelSectionEdit()
      return
    }

    if (isGenericRecordEditor.value) {
      const nextContent = deepCloneRecord(prd.value)
      const nextValue = genericRecordDrafts.value
        .map((draft) => genericRecordFromDraft(draft))
        .filter(hasGenericRecordContent)

      nextContent[section.key] = nextValue
      nextContent._prd_section_edit = {
        sectionKey: section.key,
        sectionTitle: section.title,
        editedAt: new Date().toISOString(),
        source: MARKER,
        editorMode: 'GENERIC_RECORD_FORM',
      }

      emit('save-manual-edit', {
        artifactId: props.artifact?.artifactId || null,
        artifactType: 'MODULE_PRD',
        content: nextContent,
        contentMarkdown: buildModulePrdMarkdown(nextContent),
        reason: `章节表单编辑：${section.title}`,
      } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

      cancelSectionEdit()
      return
    }

    if (isGenericObjectEditor.value) {
      const nextContent = deepCloneRecord(prd.value)
      nextContent[section.key] = genericObjectFromDrafts(genericObjectDrafts.value)
      nextContent._prd_section_edit = {
        sectionKey: section.key,
        sectionTitle: section.title,
        editedAt: new Date().toISOString(),
        source: MARKER,
        editorMode: 'GENERIC_OBJECT_FORM',
      }

      emit('save-manual-edit', {
        artifactId: props.artifact?.artifactId || null,
        artifactType: 'MODULE_PRD',
        content: nextContent,
        contentMarkdown: buildModulePrdMarkdown(nextContent),
        reason: `章节表单编辑：${section.title}`,
      } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

      cancelSectionEdit()
      return
    }

    if (section.renderMode === 'markdown') {
      const updatedMarkdown = updateMarkdownSection(section, sectionDraftText.value)
      emit('save-manual-edit', {
        artifactId: props.artifact?.artifactId || null,
        artifactType: 'MODULE_PRD',
        contentMarkdown: updatedMarkdown,
        content: {
          ...(isRecord(props.artifact?.content) ? props.artifact?.content as Record<string, unknown> : {}),
          _manual_document_edit: {
            sectionKey: section.key,
            sectionTitle: section.title,
            editedAt: new Date().toISOString(),
            source: MARKER,
          },
        },
        reason: `章节编辑：${section.title}`,
      } as ManualArtifactEditPayload & { content?: Record<string, unknown> })
      cancelSectionEdit()
      return
    }

    const nextContent = deepCloneRecord(prd.value)
    let nextValue: unknown

    if (section.kind === 'text') {
      nextValue = sectionDraftText.value.trim()
    } else {
      nextValue = JSON.parse(sectionDraftText.value || (section.kind === 'array' ? '[]' : '{}'))
    }

    nextContent[section.key] = nextValue
    nextContent._prd_section_edit = {
      sectionKey: section.key,
      sectionTitle: section.title,
      editedAt: new Date().toISOString(),
      source: MARKER,
    }

    emit('save-manual-edit', {
      artifactId: props.artifact?.artifactId || null,
      artifactType: 'MODULE_PRD',
      content: nextContent,
      contentMarkdown: buildModulePrdMarkdown(nextContent),
      reason: `章节编辑：${section.title}`,
    } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

    cancelSectionEdit()
  } catch (error) {
    sectionEditError.value = error instanceof Error && error.message
      ? error.message
      : '章节内容格式不正确。'
  }
}



function structuredAddButtonLabel(sectionKey: string): string {
  const map: Record<string, string> = {
    user_roles: '新增角色',
    functional_requirements: '新增业务能力',
    business_rules: '新增规则',
    state_rules: '新增状态',
    page_requirements: '新增页面',
    data_requirements: '新增数据对象',
    exception_scenarios: '新增异常场景',
    acceptance_criteria: '新增验收项',
    non_functional_requirements: '新增质量要求',
  }
  return map[sectionKey] || '新增内容'
}

function recordItemLabel(sectionKey: string, index: number): string {
  const map: Record<string, string> = {
    user_roles: '角色',
    functional_requirements: '业务能力',
    business_rules: '规则',
    state_rules: '状态',
    page_requirements: '页面',
    data_requirements: '数据对象',
    exception_scenarios: '异常场景',
    acceptance_criteria: '验收项',
    non_functional_requirements: '质量要求',
  }
  return `${map[sectionKey] || '内容'} ${index + 1}`
}

function defaultRecordTitle(sectionKey: string, index: number): string {
  const map: Record<string, string> = {
    user_roles: '未命名角色',
    functional_requirements: '未命名业务能力',
    business_rules: '未命名规则',
    state_rules: '未命名状态',
    page_requirements: '未命名页面',
    data_requirements: '未命名数据对象',
    exception_scenarios: '未命名异常场景',
    acceptance_criteria: '未命名验收项',
    non_functional_requirements: '未命名质量要求',
  }
  return `${map[sectionKey] || '未命名内容'} ${index + 1}`
}

function openAddContentDialog() {
  addContentDialogVisible.value = true
  customSectionError.value = ''
}

function closeAddContentDialog() {
  addContentDialogVisible.value = false
}

function sectionTemplateForAdd(sectionKey: string): PrdSection | null {
  const existing = structuredSections.value.find((section) => section.key === sectionKey)
  if (existing) return existing

  const definitions: Record<string, { title: string; description: string; kind: 'object' | 'array' | 'text' }> = {
    user_roles: { title: '用户角色', description: '参与该模块的业务角色与职责。', kind: 'array' },
    functional_requirements: { title: '功能需求', description: '可开发、可测试、可验收的业务能力。', kind: 'array' },
    business_rules: { title: '业务规则', description: '关键规则、触发条件与期望结果。', kind: 'array' },
    state_rules: { title: '状态与流转', description: '业务状态、流转边界和异常状态。', kind: 'array' },
    page_requirements: { title: '页面与操作设计', description: '页面用途、主要信息、操作和关联页面。', kind: 'array' },
    data_requirements: { title: '数据需求', description: '业务实体、关键字段和约束。', kind: 'array' },
    exception_scenarios: { title: '异常场景', description: '异常输入、状态冲突和处理策略。', kind: 'array' },
    acceptance_criteria: { title: '验收标准', description: '验收口径、验证方式和交付检查点。', kind: 'array' },
    non_functional_requirements: { title: '非功能需求', description: '性能、安全、可用性等质量要求。', kind: 'array' },
  }

  const definition = definitions[sectionKey]
  if (!definition) return null
  return makeSection(sectionKey, definition.title, definition.description, [], definition.kind)
}

function openStructuredAddEditor(sectionKey: string) {
  const section = sectionTemplateForAdd(sectionKey)
  if (!section) return
  closeAddContentDialog()
  openSectionEditor(section)
  if (section.key === 'page_requirements') {
    addPageRequirementDraft()
    return
  }
  if (section.kind === 'array') {
    addGenericRecordDraft()
  }
}

function openCustomSectionCreate() {
  closeAddContentDialog()
  customSectionDialogVisible.value = true
  customSectionTitle.value = ''
  customSectionBody.value = ''
  customSectionError.value = ''
}

function cancelCustomSectionCreate() {
  customSectionDialogVisible.value = false
  customSectionTitle.value = ''
  customSectionBody.value = ''
  customSectionError.value = ''
}

function saveCustomSectionCreate() {
  const title = customSectionTitle.value.trim()
  const body = customSectionBody.value.trim()

  if (!title) {
    customSectionError.value = '请填写栏目标题。'
    return
  }

  const nextContent = deepCloneRecord(prd.value)
  const records = customSectionRecordsFromContent(nextContent)
  const nextOrder = records.length + 1
  const sectionId = `custom_${Date.now()}_${Math.random().toString(16).slice(2)}`

  nextContent.custom_sections = [
    ...records,
    {
      section_id: sectionId,
      title,
      body,
      order: nextOrder,
      visibility: 'USER_VISIBLE',
      source: 'USER',
      impact_policy: 'DOCUMENT_ONLY',
    },
  ]
  nextContent._prd_section_edit = {
    sectionKey: 'custom_sections',
    sectionTitle: title,
    editedAt: new Date().toISOString(),
    source: MARKER,
    editorMode: 'CUSTOM_SECTION_CREATE',
    impactPolicy: 'DOCUMENT_ONLY',
  }

  emit('save-manual-edit', {
    artifactId: props.artifact?.artifactId || null,
    artifactType: 'MODULE_PRD',
    content: nextContent,
    contentMarkdown: buildModulePrdMarkdown(nextContent),
    reason: `新增自由栏目：${title}`,
  } as ManualArtifactEditPayload & { content?: Record<string, unknown> })

  cancelCustomSectionCreate()
}

function deleteCustomSection(section: PrdSection) {
  if (!section.customSectionId) return
  const confirmed = window.confirm(`删除“${section.title}”后不会影响模块树或原型，只会从当前 PRD 文档中移除。确认删除？`)
  if (!confirmed) return

  const nextContent = deepCloneRecord(prd.value)
  const records = customSectionRecordsFromContent(nextContent)
    .filter((record) => String(record.section_id || record.sectionId) !== section.customSectionId)
    .map((record, index) => ({ ...record, order: index + 1 }))

  nextContent.custom_sections = records
  nextContent._prd_section_edit = {
    sectionKey: section.key,
    sectionTitle: section.title,
    editedAt: new Date().toISOString(),
    source: MARKER,
    editorMode: 'CUSTOM_SECTION_DELETE',
    impactPolicy: 'DOCUMENT_ONLY',
  }

  emit('save-manual-edit', {
    artifactId: props.artifact?.artifactId || null,
    artifactType: 'MODULE_PRD',
    content: nextContent,
    contentMarkdown: buildModulePrdMarkdown(nextContent),
    reason: `删除自由栏目：${section.title}`,
  } as ManualArtifactEditPayload & { content?: Record<string, unknown> })
}

function moveCustomSection(section: PrdSection, direction: -1 | 1) {
  if (!section.customSectionId) return
  const nextContent = deepCloneRecord(prd.value)
  const records = customSectionRecordsFromContent(nextContent)
    .sort((left, right) => (Number(left.order || 0) || 0) - (Number(right.order || 0) || 0))
  const index = records.findIndex((record) => String(record.section_id || record.sectionId) === section.customSectionId)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= records.length) return

  const nextRecords = [...records]
  const [item] = nextRecords.splice(index, 1)
  nextRecords.splice(targetIndex, 0, item)
  nextContent.custom_sections = nextRecords.map((record, itemIndex) => ({ ...record, order: itemIndex + 1 }))
  nextContent._prd_section_edit = {
    sectionKey: 'custom_sections',
    sectionTitle: section.title,
    editedAt: new Date().toISOString(),
    source: MARKER,
    editorMode: 'CUSTOM_SECTION_REORDER',
    impactPolicy: 'DOCUMENT_ONLY',
  }

  emit('save-manual-edit', {
    artifactId: props.artifact?.artifactId || null,
    artifactType: 'MODULE_PRD',
    content: nextContent,
    contentMarkdown: buildModulePrdMarkdown(nextContent),
    reason: `调整自由栏目顺序：${section.title}`,
  } as ManualArtifactEditPayload & { content?: Record<string, unknown> })
}

function customSectionIndex(section: PrdSection): number {
  return customSections.value.findIndex((item) => item.customSectionId === section.customSectionId)
}

function isFirstCustomSection(section: PrdSection): boolean {
  return customSectionIndex(section) <= 0
}

function isLastCustomSection(section: PrdSection): boolean {
  const index = customSectionIndex(section)
  return index < 0 || index >= customSections.value.length - 1
}

function parseMarkdownSections(markdown: string): Array<{
  key: string
  title: string
  body: string
  headingLevel: number
  rawHeading: string
}> {
  const text = String(markdown || '').replace(/\r\n/g, '\n')
  const lines = text.split('\n')
  const result: Array<{ key: string; title: string; body: string; headingLevel: number; rawHeading: string }> = []
  let current: { title: string; headingLevel: number; rawHeading: string; body: string[] } | null = null

  const flush = () => {
    if (!current) return
    const body = current.body.join('\n').trim()
    result.push({
      key: `markdown_${result.length + 1}_${slugify(current.title)}`,
      title: current.title,
      body,
      headingLevel: current.headingLevel,
      rawHeading: current.rawHeading,
    })
  }

  for (const line of lines) {
    const match = line.match(/^(#{1,3})\s+(.+?)\s*$/)
    if (match) {
      flush()
      current = {
        title: match[2].trim(),
        headingLevel: match[1].length,
        rawHeading: line,
        body: [],
      }
      continue
    }

    if (!current) {
      current = {
        title: documentTitleFromMarkdown(text) || '模块需求',
        headingLevel: 1,
        rawHeading: '# 模块需求',
        body: [],
      }
    }

    current.body.push(line)
  }

  flush()

  return result.filter((section) => section.title || section.body.trim())
}

function updateMarkdownSection(section: PrdSection, body: string): string {
  const original = String(props.markdown || '').replace(/\r\n/g, '\n')
  const sections = parseMarkdownSections(original)
  const targetIndex = sections.findIndex((item) => item.key === section.key)
  if (targetIndex < 0) {
    const heading = `${'#'.repeat(section.headingLevel || 2)} ${section.title}`
    return [original.trim(), '', heading, '', body.trim()].filter(Boolean).join('\n')
  }

  sections[targetIndex] = {
    ...sections[targetIndex],
    body: body.trim(),
  }

  return sections
    .map((item) => [
      `${'#'.repeat(item.headingLevel || 2)} ${item.title}`,
      item.body.trim(),
    ].filter(Boolean).join('\n\n'))
    .join('\n\n')
}

function documentTitleFromMarkdown(markdown: string): string {
  const match = String(markdown || '').match(/^#\s+(.+?)\s*$/m)
  return match?.[1]?.trim() || ''
}

function renderMarkdown(markdown: string): string {
  return DOMPurify.sanitize(markdownRenderer.render(markdown || ''))
}

function buildModulePrdMarkdown(content: Record<string, any>): string {
  const info = recordValue(content.module_basic_info) || {}
  const title = info.module_name || props.artifact?.title || documentTitle.value || '模块需求'
  const lines: string[] = [`# ${title}`]

  pushStructuredSection(lines, '模块概述', [
    info.module_position ? `模块位置：${info.module_position}` : '',
    info.module_goal ? `模块目标：${info.module_goal}` : '',
    info.responsibility_boundary ? `职责边界：${info.responsibility_boundary}` : '',
  ].filter(Boolean).join('\n\n'))
  pushStructuredSection(lines, '业务背景', content.business_background)
  pushArraySection(lines, '用户角色', content.user_roles, (item) => `${item.role_name || item.name || item.title || '用户角色'}：${item.description || item.responsibilities || ''}`)
  pushArraySection(lines, '功能需求', content.functional_requirements, (item) => `${item.title || item.requirement_name || item.name || '功能需求'}：${item.description || ''}`)
  pushArraySection(lines, '业务规则', content.business_rules, (item) => `${item.title || item.rule_name || item.name || '业务规则'}：${item.description || ''}`)
  pushArraySection(lines, '状态与流转', content.state_rules, (item) => `${item.state_name || item.title || item.name || '状态'}：${item.description || ''}`)
  pushPageRequirementMarkdownSection(lines, content.page_requirements)
  pushArraySection(lines, '数据需求', content.data_requirements, (item) => `${item.entity_name || item.title || item.name || '数据对象'}：${item.description || ''}`)
  pushArraySection(lines, '异常场景', content.exception_scenarios, (item) => `${item.title || item.scenario_name || item.name || '异常场景'}：${item.description || item.handling_strategy || ''}`)
  pushArraySection(lines, '验收标准', content.acceptance_criteria, (item) => `${item.criterion_name || item.title || '验收标准'}：${item.description || item.verification_method || ''}`)
  pushArraySection(lines, '非功能需求', content.non_functional_requirements, (item) => `${item.nfr_name || item.category || item.title || '非功能需求'}：${item.description || item.target || ''}`)
  pushCustomSectionsMarkdown(lines, content.custom_sections || content.customSections)

  return lines.join('\n\n')
}

function pushStructuredSection(lines: string[], title: string, value: unknown) {
  const text = valueText(value)
  if (text === '-') return
  lines.push(`## ${title}`, text)
}

function pushArraySection(lines: string[], title: string, value: unknown, formatter: (item: Record<string, any>) => string) {
  const items = asArray(value).map((item) => recordValue(item)).filter(Boolean) as Record<string, any>[]
  if (!items.length) return
  lines.push(`## ${title}`)
  lines.push(...items.map((item) => `- ${formatter(item).replace(/\s+/g, ' ').trim()}`))
}

function pushCustomSectionsMarkdown(lines: string[], value: unknown) {
  const items = normalizeCustomSectionRecords(value)
  for (const item of items) {
    const title = String(item.title || '').trim()
    const body = String(item.body || '').trim()
    if (!title && !body) continue
    lines.push(`## ${title || '补充说明'}`)
    if (body) lines.push(body)
  }
}
function pushPageRequirementMarkdownSection(lines: string[], value: unknown) {
  const items = asArray(value).map((item) => recordValue(item)).filter(Boolean) as Record<string, any>[]
  if (!items.length) return

  lines.push('## 页面与操作设计')

  for (const [index, item] of items.entries()) {
    lines.push(`### ${item.page_name || item.pageName || `页面 ${index + 1}`}`)
    const details = [
      item.description ? `页面说明：${item.description}` : '',
      `页面类型：${pageTypeDisplay(item) || valueText(item.page_type || item.pageType)}`,
      listNotEmpty(item.form_fields || item.formFields) ? `主要信息：${joinList(item.form_fields || item.formFields)}` : '',
      listNotEmpty(item.filter_fields || item.filterFields) ? `筛选条件：${joinList(item.filter_fields || item.filterFields)}` : '',
      listNotEmpty(item.table_columns || item.tableColumns) ? `列表字段：${joinList(item.table_columns || item.tableColumns)}` : '',
      listNotEmpty(item.primary_actions || item.primaryActions || item.user_actions) ? `主要操作：${joinList(item.primary_actions || item.primaryActions || item.user_actions)}` : '',
      listNotEmpty(item.row_actions || item.rowActions) ? `行操作：${joinList(item.row_actions || item.rowActions)}` : '',
      listNotEmpty(item.navigation_targets || item.navigationTargets) ? `关联页面：${joinList(item.navigation_targets || item.navigationTargets)}` : '',
    ].filter(Boolean)
    lines.push(details.join('\n'))
  }
}

function normalizePageRequirementDrafts(value: unknown): Array<Record<string, any>> {
  const items = normalizedRecords(value)
  const drafts = items.map((item, index) => pageRequirementToDraft(item, index))

  return drafts.length
    ? drafts
    : [createEmptyPageRequirementDraft(0)]
}

function pageRequirementToDraft(item: Record<string, any>, index: number): Record<string, any> {
  return {
    _draftKey: `${item.page_key || item.pageKey || index}_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    page_key: valueText(item.page_key || item.pageKey) === '-' ? '' : valueText(item.page_key || item.pageKey),
    page_name: valueText(item.page_name || item.pageName) === '-' ? '' : valueText(item.page_name || item.pageName),
    page_type: normalizePageType(item.page_type || item.pageType),
    route_path: valueText(item.route_path || item.routePath || item.route) === '-' ? '' : valueText(item.route_path || item.routePath || item.route),
    layout_type: valueText(item.layout_type || item.layoutType) === '-' ? '' : valueText(item.layout_type || item.layoutType),
    description: valueText(item.description || item.summary) === '-' ? '' : valueText(item.description || item.summary),
    form_fields_text: arrayToDraftText(item.form_fields || item.formFields),
    filter_fields_text: arrayToDraftText(item.filter_fields || item.filterFields),
    table_columns_text: arrayToDraftText(item.table_columns || item.tableColumns),
    primary_actions_text: arrayToDraftText(item.primary_actions || item.primaryActions || item.user_actions),
    row_actions_text: arrayToDraftText(item.row_actions || item.rowActions),
    navigation_targets_text: arrayToDraftText(item.navigation_targets || item.navigationTargets),
    _source: item,
  }
}

function createEmptyPageRequirementDraft(index: number): Record<string, any> {
  return {
    _draftKey: `new_page_${Date.now()}_${index}`,
    page_key: '',
    page_name: '',
    page_type: 'LIST',
    route_path: '',
    layout_type: '',
    description: '',
    form_fields_text: '',
    filter_fields_text: '',
    table_columns_text: '',
    primary_actions_text: '',
    row_actions_text: '',
    navigation_targets_text: '',
  }
}

function addPageRequirementDraft() {
  pageRequirementDrafts.value = [
    ...pageRequirementDrafts.value,
    createEmptyPageRequirementDraft(pageRequirementDrafts.value.length),
  ]
}

function removePageRequirementDraft(index: number) {
  if (pageRequirementDrafts.value.length <= 1) return
  pageRequirementDrafts.value = pageRequirementDrafts.value.filter((_, itemIndex) => itemIndex !== index)
}

function pageRequirementFromDraft(draft: Record<string, any>, index: number): Record<string, any> {
  const pageName = String(draft.page_name || '').trim()
  const pageKey = String(draft.page_key || '').trim() || slugify(pageName || `page_${index + 1}`)
  const pageType = normalizePageType(draft.page_type)

  const source = recordValue(draft._source) || {}
  return {
    ...preserveInternalPrdFields(source),
    page_key: pageKey,
    page_name: pageName || pageKey || `页面 ${index + 1}`,
    page_type: pageType,
    route_path: String(draft.route_path || source.route_path || source.routePath || source.route || '').trim(),
    layout_type: String(draft.layout_type || source.layout_type || source.layoutType || '').trim() || defaultLayoutType(pageType),
    description: String(draft.description || '').trim(),
    form_fields: draftTextToList(draft.form_fields_text),
    filter_fields: draftTextToList(draft.filter_fields_text),
    table_columns: draftTextToList(draft.table_columns_text),
    primary_actions: draftTextToList(draft.primary_actions_text),
    row_actions: draftTextToList(draft.row_actions_text),
    navigation_targets: draftTextToList(draft.navigation_targets_text),
  }
}

function defaultLayoutType(pageType: string): string {
  const map: Record<string, string> = {
    LIST: 'TABLE_LIST',
    FORM: 'FORM_DETAIL',
    DETAIL: 'DETAIL_VIEW',
    EDIT: 'FORM_DETAIL',
    APPROVAL: 'APPROVAL_WORKBENCH',
    DASHBOARD: 'DASHBOARD',
    PORTAL: 'PORTAL',
    CONFIG: 'CONFIG_FORM',
    REPORT: 'REPORT_VIEW',
    WIZARD: 'STEP_WIZARD',
  }
  return map[pageType] || pageType || 'STANDARD_PAGE'
}

function normalizePageType(value: unknown): string {
  const raw = String(value || '').trim().toUpperCase()
  if (raw.includes('LIST') || raw.includes('TABLE') || raw.includes('QUERY')) return 'LIST'
  if (raw.includes('FORM') || raw.includes('CREATE')) return 'FORM'
  if (raw.includes('DETAIL')) return 'DETAIL'
  if (raw.includes('EDIT')) return 'EDIT'
  if (raw.includes('APPROVAL') || raw.includes('AUDIT') || raw.includes('REVIEW')) return 'APPROVAL'
  if (raw.includes('DASHBOARD')) return 'DASHBOARD'
  if (raw.includes('PORTAL') || raw.includes('HOME')) return 'PORTAL'
  if (raw.includes('CONFIG') || raw.includes('SETTING')) return 'CONFIG'
  if (raw.includes('REPORT')) return 'REPORT'
  if (raw.includes('WIZARD') || raw.includes('STEP')) return 'WIZARD'
  return raw || 'LIST'
}

function pageTypeDisplay(item: Record<string, any>): string {
  const pageType = normalizePageType(item.page_type || item.pageType || item.layout_type || item.layoutType)
  const option = pageTypeOptions.find((candidate) => candidate.value === pageType)
  return option?.label || pageType
}

function arrayToDraftText(value: unknown): string {
  if (!Array.isArray(value)) return ''
  return value.map(valueText).filter((item) => item !== '-').join('、')
}

function draftTextToList(value: unknown): string[] {
  return String(value || '')
    .split(/[、,，;；\n]/g)
    .map((item) => item.trim())
    .filter(Boolean)
}



type GenericFieldDraft = {
  key: string
  label: string
  value: string
  kind: 'text' | 'list'
  multiline: boolean
  placeholder: string
}

type GenericRecordDraft = {
  _draftKey: string
  _sectionKey: string
  _source?: Record<string, any>
  _fields: GenericFieldDraft[]
}

const genericSectionFieldOrders: Record<string, string[]> = {
  module_basic_info: [
    'module_position', 'module_goal', 'responsibility_boundary',
  ],
  user_roles: [
    'role_name', 'description', 'responsibilities',
  ],
  functional_requirements: [
    'requirement_name', 'description', 'input_description', 'output_description', 'preconditions', 'postconditions', 'priority',
  ],
  business_rules: [
    'rule_name', 'description', 'trigger_condition', 'expected_result', 'constraint',
  ],
  state_rules: [
    'state_name', 'description', 'allowed_transitions', 'entry_condition', 'exit_condition',
  ],
  data_requirements: [
    'entity_name', 'description', 'important_fields', 'constraints', 'relationships',
  ],
  api_requirements: [
    'api_name', 'api_key', 'method', 'path_suggestion', 'request_summary', 'response_summary', 'description',
  ],
  permission_requirements: [
    'permission_name', 'permission_key', 'description', 'roles', 'actions', 'scope',
  ],
  exception_scenarios: [
    'scenario_name', 'description', 'handling_strategy', 'trigger_condition',
  ],
  acceptance_criteria: [
    'criterion_name', 'description', 'verification_method', 'expected_result',
  ],
  non_functional_requirements: [
    'nfr_name', 'description', 'target', 'verification_method',
  ],
  downstream_artifact_hints: [
    'business_flow', 'page_spec', 'data_model', 'api_contract', 'test_spec', 'dev_handoff_package',
  ],
}

const genericFieldLabels: Record<string, string> = {
  module_name: '模块名称',
  module_key: '模块 Key',
  module_type: '模块类型',
  module_position: '模块位置',
  module_goal: '模块目标',
  responsibility_boundary: '职责边界',
  role_name: '角色名称',
  role_key: '角色 Key',
  responsibilities: '职责',
  permissions: '权限',
  requirement_name: '功能点名称',
  requirement_key: '功能点 Key',
  rule_name: '规则名称',
  rule_key: '规则 Key',
  state_name: '状态名称',
  state_key: '状态 Key',
  entity_name: '实体名称',
  entity_key: '实体 Key',
  api_name: '接口名称',
  api_key: '接口 Key',
  permission_name: '权限名称',
  permission_key: '权限 Key',
  scenario_name: '异常场景名称',
  scenario_key: '异常场景 Key',
  criterion_name: '验收项名称',
  criterion_key: '验收项 Key',
  nfr_name: '非功能项名称',
  nfr_key: '非功能项 Key',
  title: '标题',
  name: '名称',
  key: 'Key',
  label: '标签',
  description: '说明',
  summary: '摘要',
  input_description: '主要输入',
  output_description: '处理结果',
  preconditions: '前置条件',
  postconditions: '后置结果',
  acceptance_criteria: '验收要求',
  priority: '优先级',
  trigger_condition: '触发条件',
  expected_result: '期望结果',
  constraint: '约束',
  allowed_transitions: '可流转至',
  entry_condition: '进入条件',
  exit_condition: '退出条件',
  important_fields: '关键字段',
  constraints: '约束',
  relationships: '关联关系',
  method: '方法',
  path_suggestion: '路径建议',
  request_summary: '请求说明',
  response_summary: '响应说明',
  roles: '适用角色',
  actions: '允许操作',
  scope: '权限范围',
  handling_strategy: '处理策略',
  target: '目标',
  verification_method: '验证方式',
  business_flow: '业务流程提示',
  page_spec: '页面规范提示',
  data_model: '数据模型提示',
  api_contract: '接口契约提示',
  test_spec: '测试规格提示',
  dev_handoff_package: '开发交接提示',
}



function isInternalPrdFieldKey(key: string): boolean {
  return /(^_|_key$|Key$|^key$|module_key|module_type|page_key|pageKey|route_path|routePath|route$|layout_type|layoutType|api_key|permission_key|criterion_key|nfr_key|path_suggestion|sync|trace|artifact|token|source)/i.test(key)
}

function preserveInternalPrdFields(source: Record<string, any>): Record<string, any> {
  const result: Record<string, any> = {}
  for (const [key, value] of Object.entries(source || {})) {
    if (isInternalPrdFieldKey(key) && hasMeaningfulValue(value)) {
      result[key] = value
    }
  }
  return result
}
function buildGenericRecordDrafts(section: PrdSection): GenericRecordDraft[] {
  const records = normalizedRecords(section.value)
  const source = records.length ? records : [{}]

  return source.map((record, index) => createGenericRecordDraft(section.key, record, index))
}

function createGenericRecordDraft(
  sectionKey: string,
  record: Record<string, any>,
  index: number,
): GenericRecordDraft {
  const keys = collectGenericFieldKeys(sectionKey, record)
  return {
    _draftKey: `${sectionKey}_${Date.now()}_${index}_${Math.random().toString(16).slice(2)}`,
    _sectionKey: sectionKey,
    _source: record,
    _fields: keys.map((key) => createGenericFieldDraft(key, record[key], sectionKey)),
  }
}

function collectGenericFieldKeys(sectionKey: string, record: Record<string, any>): string[] {
  const preferred = genericSectionFieldOrders[sectionKey] || []
  const actual = Object.keys(record || {}).filter((key) => !key.startsWith('_') && !isInternalPrdFieldKey(key))
  const keys = [...preferred, ...actual]
  const unique = Array.from(new Set(keys))

  if (unique.length) {
    return unique
  }

  return ['name', 'key', 'description']
}

function createGenericFieldDraft(
  key: string,
  value: unknown,
  sectionKey: string,
): GenericFieldDraft {
  const kind = isGenericListField(key, value) ? 'list' : 'text'
  const text = genericValueToEditorText(value)
  const multiline = kind === 'list' || isGenericLongTextField(key) || text.length > 36

  return {
    key,
    label: genericFieldLabel(key),
    value: text,
    kind,
    multiline,
    placeholder: genericFieldPlaceholder(key, sectionKey),
  }
}

function isGenericListField(key: string, value: unknown): boolean {
  if (Array.isArray(value)) return true
  return /fields|columns|actions|roles|permissions|responsibilities|criteria|constraints|transitions|relationships|steps|items|scopes/i.test(key)
}

function isGenericLongTextField(key: string): boolean {
  return /description|summary|condition|result|strategy|target|goal|boundary|note|hint|criteria|constraint|flow|contract|spec/i.test(key)
}

function genericValueToEditorText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(valueText).filter((item) => item !== '-').join('、')
  if (typeof value === 'object') return valueText(value) === '-' ? '' : valueText(value)
  return String(value || '').trim()
}

function genericFieldLabel(key: string): string {
  return genericFieldLabels[key] || readableKey(key)
}

function genericFieldPlaceholder(key: string, sectionKey: string): string {
  if (isGenericListField(key, undefined)) return '可用顿号、逗号或换行分隔多个内容。'
  if (/key$/i.test(key) || key === 'key') return '系统标识，可使用英文小写和下划线。'
  if (/name$|title|label/i.test(key)) return '请输入名称。'
  if (/description|summary/i.test(key)) return '请输入面向业务用户可理解的说明。'
  return `请输入${genericFieldLabel(key) || readableKey(sectionKey)}。`
}

function addGenericRecordDraft() {
  const section = editingSection.value
  if (!section) return
  genericRecordDrafts.value = [
    ...genericRecordDrafts.value,
    createGenericRecordDraft(section.key, {}, genericRecordDrafts.value.length),
  ]
}

function removeGenericRecordDraft(index: number) {
  if (genericRecordDrafts.value.length <= 1) return
  genericRecordDrafts.value = genericRecordDrafts.value.filter((_, itemIndex) => itemIndex !== index)
}

function genericDraftTitle(draft: GenericRecordDraft, index: number): string {
  const nameField = draft._fields.find((field) => /(_name|name|title|label)$/i.test(field.key))
  const value = String(nameField?.value || '').trim()
  return value || defaultRecordTitle(draft._sectionKey, index)
}

function genericRecordFromDraft(draft: GenericRecordDraft): Record<string, any> {
  const result: Record<string, any> = preserveInternalPrdFields(draft._source || {})

  for (const field of draft._fields) {
    const text = String(field.value || '').trim()
    if (!text) continue
    result[field.key] = field.kind === 'list' ? draftTextToList(text) : text
  }

  return result
}

function hasGenericRecordContent(record: Record<string, any>): boolean {
  return Object.values(record).some((value) => hasMeaningfulValue(value))
}

function buildGenericObjectDrafts(section: PrdSection): GenericFieldDraft[] {
  const record = recordValue(section.value) || {}
  genericObjectSource.value = record
  return collectGenericFieldKeys(section.key, record).map((key) =>
    createGenericFieldDraft(key, record[key], section.key),
  )
}

function genericObjectFromDrafts(fields: GenericFieldDraft[]): Record<string, any> {
  const result: Record<string, any> = preserveInternalPrdFields(genericObjectSource.value || {})

  for (const field of fields) {
    const text = String(field.value || '').trim()
    if (!text) continue
    result[field.key] = field.kind === 'list' ? draftTextToList(text) : text
  }

  return result
}

function normalizedRecords(value: unknown): Record<string, any>[] {
  return asArray(value).map((item) => recordValue(item) || { value: item })
}

function itemTitle(item: Record<string, any>, index: number, sectionKey: string): string {
  return valueText(
    item.title || item.name || item.label ||
      item.page_name || item.pageName ||
      item.role_name || item.roleName ||
      item.requirement_name || item.requirementName ||
      item.state_name || item.stateName ||
      item.entity_name || item.entityName ||
      item.api_name || item.apiName ||
      item.permission_name || item.permissionName ||
      item.category ||
      readableKey(sectionKey) + ` ${index + 1}`,
  )
}

function itemKey(item: Record<string, any>, index: number, sectionKey: string): string {
  return valueText(
    item.key ||
      item.page_key || item.pageKey ||
      item.role_key || item.roleKey ||
      item.requirement_key || item.requirementKey ||
      item.rule_key || item.ruleKey ||
      item.state_key || item.stateKey ||
      item.entity_key || item.entityKey ||
      item.api_key || item.apiKey ||
      item.permission_key || item.permissionKey ||
      item.scenario_key || item.scenarioKey ||
      item.criterion_key || item.criterionKey ||
      item.nfr_key || item.nfrKey ||
      `${sectionKey}_${index + 1}`,
  )
}

function itemDescription(item: Record<string, any>): string {
  return valueText(item.description || item.summary || item.value || item.target || '')
}

function itemDetails(item: Record<string, any>, sectionKey: string): Array<{ label: string; value: string }> {
  const keysBySection: Record<string, Array<[string, string]>> = {
    functional_requirements: [
      ['priority', '优先级'],
      ['input_description', '输入'],
      ['output_description', '输出'],
      ['preconditions', '前置条件'],
      ['postconditions', '后置条件'],
    ],
    business_rules: [
      ['trigger_condition', '触发条件'],
      ['expected_result', '期望结果'],
    ],
    state_rules: [
      ['allowed_transitions', '可流转至'],
    ],
    data_requirements: [
      ['important_fields', '关键字段'],
      ['constraints', '约束'],
    ],
    api_requirements: [
      ['method', '方法'],
      ['path_suggestion', '路径'],
      ['request_summary', '请求'],
      ['response_summary', '响应'],
    ],
    permission_requirements: [
      ['roles', '角色'],
    ],
    exception_scenarios: [
      ['handling_strategy', '处理策略'],
    ],
    acceptance_criteria: [
      ['verification_method', '验证方式'],
    ],
    non_functional_requirements: [
      ['target', '目标'],
    ],
  }

  return (keysBySection[sectionKey] || [])
    .map(([key, label]) => ({ label, value: valueText(item[key]) }))
    .filter((item) => item.value !== '-')
}

function objectEntries(value: unknown): Array<{ key: string; value: unknown }> {
  const record = recordValue(value)
  if (!record) return []
  return Object.entries(record)
    .filter(([key, entryValue]) => !isInternalPrdFieldKey(key) && hasMeaningfulValue(entryValue))
    .map(([key, entryValue]) => ({ key, value: entryValue }))
}

function pageTypeLabel(item: Record<string, any>): string {
  return pageTypeDisplay(item)
}

function displayText(value: unknown): string {
  return valueText(value) === '-' ? '暂无内容。' : valueText(value)
}

function valueText(value: unknown): string {
  if (value === null || value === undefined) return '-'
  if (Array.isArray(value)) return value.length ? value.map(valueText).filter((item) => item !== '-').join('；') : '-'
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    if ('label' in record) return valueText(record.label)
    if ('name' in record) return valueText(record.name)
    if ('title' in record) return valueText(record.title)
    if ('field_name' in record) return valueText(record.field_name)
    if ('fieldName' in record) return valueText(record.fieldName)
    if ('column_name' in record) return valueText(record.column_name)
    if ('columnName' in record) return valueText(record.columnName)
    if ('action_name' in record) return valueText(record.action_name)
    if ('actionName' in record) return valueText(record.actionName)
    if ('page_name' in record) return valueText(record.page_name)
    if ('pageName' in record) return valueText(record.pageName)
    if ('description' in record) return valueText(record.description)
    return Object.keys(record).length ? JSON.stringify(record) : '-'
  }
  const text = String(value || '').trim()
  return text || '-'
}

function joinList(value: unknown): string {
  if (!Array.isArray(value) || value.length === 0) return '-'
  return value.map(valueText).filter((item) => item !== '-').join('；') || '-'
}

function listNotEmpty(value: unknown): boolean {
  return Array.isArray(value) && value.length > 0
}

function readableKey(value: string): string {
  const map: Record<string, string> = {
    module_basic_info: '模块概述',
    business_background: '业务背景',
    user_roles: '用户角色',
    functional_requirements: '功能需求',
    business_rules: '业务规则',
    state_rules: '状态规则',
    page_requirements: '页面与操作设计',
    data_requirements: '数据需求',
    api_requirements: '接口需求',
    permission_requirements: '权限需求',
    exception_scenarios: '异常场景',
    acceptance_criteria: '验收标准',
    non_functional_requirements: '非功能需求',
  }
  return map[value] || value.replace(/_/g, ' ')
}

function asArray(value: unknown): any[] {
  return Array.isArray(value) ? value : []
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function recordValue(value: unknown): Record<string, any> | null {
  return isRecord(value) ? value as Record<string, any> : null
}

function deepCloneRecord(value: Record<string, any>): Record<string, any> {
  try {
    return JSON.parse(JSON.stringify(value || {}))
  } catch {
    return { ...(value || {}) }
  }
}

function hasMeaningfulValue(value: unknown): boolean {
  if (value === null || value === undefined) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value as Record<string, unknown>).length > 0
  return String(value || '').trim().length > 0
}

function slugify(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/gi, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 48)
}
// A9.10.14.4.3.15.4.6.1_BUILD_RED_ERROR_CLEANUP_KEEPALIVE: display helpers are retained for page requirement reader variants.
void pageTypeLabel

</script>

<style scoped>
.module-prd-view {
  display: grid;
  gap: 14px;
}

.prd-view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.asset-title-copy h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
}

.asset-title-copy p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 12px;
}

.prd-view-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.version-badge {
  border-radius: 999px;
  padding: 5px 9px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
}

.view-mode-link {
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 6px 11px;
  background: #ffffff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.view-mode-link:hover {
  background: #eff6ff;
}

.prd-reader-view {
  display: grid;
  gap: 16px;
}

.prd-reader-cover {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #dbeafe;
  border-radius: 20px;
  padding: 18px 20px;
  background:
    radial-gradient(circle at 6% 12%, rgba(37, 99, 235, 0.10), transparent 30%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.prd-reader-cover p {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
}

.prd-reader-cover h2 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.25;
  letter-spacing: -0.035em;
}

.prd-reader-cover span {
  display: block;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.prd-reader-cover strong {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 6px 11px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
}

.prd-summary-grid,
.reader-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  border: 1px solid #dbe4ef;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
}

.summary-label,
.summary-card span {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.summary-card strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 950;
}

.prd-reader-section,
.prd-section-card {
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 18px 20px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.045);
}

.prd-reader-section-header,
.section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.prd-reader-section-header h4,
.section-title-row h4 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
  font-weight: 950;
}

.prd-reader-section-header p,
.section-title-row p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.a9-prd-edit-icon-button {
  flex: 0 0 auto;
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.16s ease;
}

.a9-prd-edit-icon-button svg {
  width: 15px;
  height: 15px;
  fill: currentColor;
}

.a9-prd-edit-icon-button:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.a9-prd-edit-icon-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.prd-reader-markdown-body {
  color: #334155;
  font-size: 14px;
  line-height: 1.85;
}

.prd-reader-markdown-body :deep(p) {
  margin: 0 0 10px;
}

.prd-reader-markdown-body :deep(ul),
.prd-reader-markdown-body :deep(ol) {
  margin: 0 0 12px;
  padding-left: 22px;
}

.prd-reader-markdown-body :deep(li + li) {
  margin-top: 4px;
}

.prd-reader-markdown-body :deep(h3) {
  margin: 18px 0 8px;
  color: #0f172a;
  font-size: 15px;
}

.a9-basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.a9-basic-grid article,
.compact-card,
.hint-card {
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  padding: 13px;
  background: #f8fafc;
}

.a9-basic-grid span,
.compact-card span,
.hint-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.a9-basic-grid strong,
.compact-card h5,
.hint-card h5 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
}

.compact-card-list,
.hint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.compact-card p,
.hint-card p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.page-contract-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.page-contract-card-head em {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 4px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
}

.a9-prd-section-editor-mask {
  position: fixed;
  inset: 0;
  z-index: 2600;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(6px);
}

.a9-prd-section-editor-dialog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(860px, 94vw);
  max-height: 88vh;
  overflow: hidden;
  border: 1px solid rgba(191, 219, 254, 0.86);
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 34px 90px rgba(15, 23, 42, 0.26);
}

.a9-prd-section-editor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #e2e8f0;
  padding: 18px 20px;
  background: linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.a9-prd-section-editor-header p {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.08em;
}

.a9-prd-section-editor-header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
}

.a9-prd-section-editor-header span {
  display: block;
  margin-top: 7px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.a9-prd-section-editor-header button {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 26px;
  line-height: 1;
  cursor: pointer;
}

.a9-prd-section-editor-body {
  display: grid;
  gap: 10px;
  min-height: 0;
  padding: 16px 20px;
}

.a9-prd-section-editor-body textarea {
  width: 100%;
  min-height: 360px;
  max-height: 58vh;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  padding: 14px;
  outline: none;
  resize: vertical;
  color: #0f172a;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.75;
}

.a9-prd-section-editor-body textarea.json {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 13px;
}

.a9-prd-section-edit-error {
  margin: 0;
  border: 1px solid #fecaca;
  border-radius: 12px;
  padding: 9px 11px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}

.a9-prd-section-editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #e2e8f0;
  padding: 14px 20px;
}

.a9-prd-secondary-button,
.a9-prd-primary-button {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 8px 14px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.a9-prd-primary-button {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

@media (max-width: 900px) {
  .prd-summary-grid,
  .reader-summary-grid,
  .a9-basic-grid {
    grid-template-columns: 1fr;
  }

  .prd-reader-cover,
  .prd-reader-section-header,
  .section-title-row {
    display: grid;
  }
}

/* A9.10.14.4.3.15.2.5_PRD_WORD_READER_AND_PAGE_FORM_EDITOR */
.prd-page-contract-document {
  display: grid;
  gap: 18px;
}

.prd-page-doc-item {
  border-bottom: 1px solid #e2e8f0;
  padding: 4px 0 18px;
}

.prd-page-doc-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.prd-page-doc-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.prd-page-doc-index {
  flex: 0 0 auto;
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 950;
}

.prd-page-doc-title-copy {
  min-width: 0;
  flex: 1;
}

.prd-page-doc-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.prd-page-doc-title-row h5 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 950;
}

.prd-page-doc-title-row em {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  line-height: 1.2;
  font-style: normal;
  font-weight: 850;
}

.prd-page-doc-title-copy p {
  margin: 6px 0 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.8;
}

.prd-page-doc-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 12px 40px;
}

.prd-page-doc-meta div {
  border-left: 3px solid #dbeafe;
  padding-left: 10px;
}

.prd-page-doc-meta dt,
.prd-page-doc-subsection h6,
.prd-page-doc-action-grid h6 {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.prd-page-doc-meta dd,
.prd-page-doc-subsection p,
.prd-page-doc-action-grid p {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.8;
}

.prd-page-doc-subsection,
.prd-page-doc-action-grid {
  margin-left: 40px;
}

.prd-page-doc-subsection + .prd-page-doc-subsection,
.prd-page-doc-subsection + .prd-page-doc-action-grid {
  margin-top: 10px;
}

.prd-page-doc-action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.prd-page-doc-action-grid > div {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fafc;
}

.a9-page-requirement-editor {
  display: grid;
  gap: 14px;
}

.a9-page-editor-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 12px;
  background: #eff6ff;
}

.a9-page-editor-intro strong {
  color: #0f172a;
  font-size: 14px;
}

.a9-page-editor-intro p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.a9-page-requirement-editor-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
}

.a9-page-requirement-editor-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.a9-page-requirement-editor-card header span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 850;
}

.a9-page-requirement-editor-card header strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 950;
}

.a9-page-remove-button {
  border: none;
  background: transparent;
  color: #ef4444;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.a9-page-remove-button:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
}

.a9-page-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.a9-page-form-grid.two {
  margin-top: 12px;
}

.a9-page-form-full {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.a9-page-form-grid label,
.a9-page-form-full {
  color: #334155;
  font-size: 12px;
  font-weight: 850;
}

.a9-page-form-grid input,
.a9-page-form-grid select,
.a9-page-form-grid textarea,
.a9-page-form-full textarea {
  width: 100%;
  margin-top: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 9px 10px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.5;
}

.a9-page-form-grid textarea,
.a9-page-form-full textarea {
  resize: vertical;
  min-height: 66px;
}

.a9-page-form-grid input:focus,
.a9-page-form-grid select:focus,
.a9-page-form-grid textarea:focus,
.a9-page-form-full textarea:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

@media (max-width: 900px) {
  .prd-page-doc-meta,
  .prd-page-doc-action-grid,
  .a9-page-form-grid {
    grid-template-columns: 1fr;
  }

  .prd-page-doc-meta,
  .prd-page-doc-subsection,
  .prd-page-doc-action-grid {
    margin-left: 0;
  }
}



/* A9.10.14.4.3.15.2.5.1_PRD_UNIFIED_READER_EDITOR_SCROLL_HOTFIX
   PRD reader visual unification + section editor scroll fix.
   This block intentionally lives at the end of <style scoped> so it overrides
   previous card/grid styles without changing business logic or persistence.
*/
.module-prd-view {
  --a9-prd-ink: #0f172a;
  --a9-prd-muted: #64748b;
  --a9-prd-soft: #f8fafc;
  --a9-prd-line: #e2e8f0;
  --a9-prd-blue: #2563eb;
  --a9-prd-blue-soft: #eff6ff;
  background: #ffffff;
}

.prd-reader-body,
.prd-card-view,
.prd-reader-document {
  max-width: 1040px;
  margin: 0 auto;
}

.prd-reader-cover,
.prd-section-card,
.prd-reader-section {
  border: none !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
}

.prd-reader-cover {
  padding: 8px 4px 20px !important;
  border-bottom: 1px solid var(--a9-prd-line) !important;
  margin-bottom: 18px !important;
}

.prd-reader-cover h3,
.prd-reader-cover h2,
.module-prd-view h3 {
  color: var(--a9-prd-ink);
  font-size: 24px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.prd-reader-cover p,
.module-prd-view p {
  color: #334155;
  line-height: 1.78;
}

.prd-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
  gap: 10px !important;
  margin: 14px 0 22px !important;
}

.summary-card {
  border: 1px solid var(--a9-prd-line) !important;
  border-radius: 10px !important;
  background: #ffffff !important;
  box-shadow: none !important;
  padding: 12px 14px !important;
}

.summary-label,
.summary-card span {
  color: var(--a9-prd-muted) !important;
  font-size: 12px !important;
}

.summary-card strong {
  color: var(--a9-prd-ink) !important;
  font-size: 14px !important;
}

.prd-section-card,
.prd-reader-section {
  position: relative;
  margin: 0 !important;
  padding: 20px 6px 22px 6px !important;
  border-bottom: 1px solid var(--a9-prd-line) !important;
}

.prd-section-card:last-child,
.prd-reader-section:last-child {
  border-bottom: none !important;
}

.section-title-row,
.prd-reader-section-header {
  display: flex !important;
  align-items: flex-start !important;
  justify-content: space-between !important;
  gap: 16px !important;
  margin-bottom: 10px !important;
}

.section-title-row h4,
.prd-reader-section-header h4,
.prd-reader-section h4,
.prd-section-card > h4 {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 18px !important;
  line-height: 1.45 !important;
  font-weight: 800 !important;
}

.section-title-row > span,
.prd-reader-section-header > span {
  border: none !important;
  border-radius: 999px !important;
  background: var(--a9-prd-soft) !important;
  color: var(--a9-prd-muted) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  padding: 4px 9px !important;
}

.a9-prd-edit-icon-button {
  flex: 0 0 auto !important;
  width: 28px !important;
  height: 28px !important;
  border: none !important;
  border-radius: 999px !important;
  background: transparent !important;
  color: #94a3b8 !important;
  box-shadow: none !important;
}

.a9-prd-edit-icon-button:hover {
  background: var(--a9-prd-blue-soft) !important;
  color: var(--a9-prd-blue) !important;
}

.prd-section-card p,
.prd-reader-section p,
.prd-reader-markdown-body,
.prd-reader-markdown-body p {
  color: #1f2937 !important;
  font-size: 14px !important;
  line-height: 1.82 !important;
}

.prd-section-card ul,
.prd-section-card ol,
.prd-reader-section ul,
.prd-reader-section ol {
  margin: 8px 0 0 20px !important;
  padding: 0 !important;
  color: #1f2937 !important;
  line-height: 1.85 !important;
}

.requirement-list,
.compact-card-list,
.state-card-list,
.page-contract-list,
.prd-page-contract-document {
  display: block !important;
}

.requirement-card,
.compact-card,
.state-card,
.api-card,
.hint-card,
.success-card,
.danger-card {
  position: relative;
  display: block !important;
  border: none !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
  padding: 12px 0 12px 18px !important;
  margin: 0 !important;
  border-left: 2px solid #dbeafe !important;
}

.requirement-card + .requirement-card,
.compact-card + .compact-card,
.state-card + .state-card,
.api-card + .api-card,
.hint-card + .hint-card {
  border-top: 1px dashed #e5e7eb !important;
}

.requirement-card-header {
  margin-bottom: 6px !important;
}

.requirement-card h5,
.compact-card h5,
.state-card h5,
.api-card h5,
.hint-card h5 {
  margin: 0 0 4px !important;
  color: var(--a9-prd-ink) !important;
  font-size: 15px !important;
  line-height: 1.5 !important;
  font-weight: 800 !important;
}

.requirement-card span,
.compact-card span,
.state-card span,
.api-card span {
  display: inline !important;
  border: none !important;
  background: transparent !important;
  color: #475569 !important;
  padding: 0 !important;
  font-size: 12px !important;
  font-weight: 700 !important;
}

.priority-badge,
.status-badge,
.type-badge {
  border: none !important;
  border-radius: 999px !important;
  background: var(--a9-prd-blue-soft) !important;
  color: var(--a9-prd-blue) !important;
  font-size: 11px !important;
  padding: 3px 8px !important;
}

.two-column-info,
.prd-page-contract-meta,
.prd-page-contract-detail-grid {
  display: grid !important;
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
  gap: 12px !important;
  margin-top: 10px !important;
}

.two-column-info > div,
.prd-page-contract-meta > div,
.prd-page-contract-detail-grid > div,
.prd-page-contract-box {
  border: none !important;
  border-left: 2px solid #dbeafe !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  padding: 2px 0 2px 12px !important;
}

.prd-page-contract-document {
  padding: 4px 0 !important;
}

.prd-page-contract-item {
  display: block !important;
  border: none !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
  padding: 18px 0 20px !important;
  margin: 0 !important;
  border-bottom: 1px solid #e5e7eb !important;
}

.prd-page-contract-item:last-child {
  border-bottom: none !important;
}

.prd-page-contract-title-row {
  display: flex !important;
  align-items: center !important;
  gap: 10px !important;
  margin-bottom: 8px !important;
}

.prd-page-contract-index {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 24px !important;
  height: 24px !important;
  border-radius: 999px !important;
  background: var(--a9-prd-blue-soft) !important;
  color: var(--a9-prd-blue) !important;
  font-weight: 800 !important;
  font-size: 12px !important;
}

.prd-page-contract-title-row h5 {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 16px !important;
  font-weight: 850 !important;
}

.prd-page-contract-title-row .type-badge {
  margin-left: 4px !important;
}

/* Editor dialog scroll fix */
.a9-prd-section-editor-mask {
  position: fixed !important;
  inset: 0 !important;
  z-index: 1200 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 22px !important;
  overflow: hidden !important;
  background: rgba(15, 23, 42, 0.48) !important;
  backdrop-filter: blur(7px) !important;
}

.a9-prd-section-editor-dialog {
  width: min(960px, calc(100vw - 44px)) !important;
  max-width: calc(100vw - 44px) !important;
  height: auto !important;
  max-height: calc(100vh - 44px) !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
  border-radius: 18px !important;
  background: #ffffff !important;
}

.a9-prd-section-editor-header,
.a9-prd-section-editor-footer {
  flex: 0 0 auto !important;
}

.a9-prd-section-editor-body {
  flex: 1 1 auto !important;
  min-height: 0 !important;
  overflow-x: hidden !important;
  overflow-y: auto !important;
  max-height: none !important;
  overscroll-behavior: contain !important;
  scrollbar-gutter: stable !important;
  padding: 16px 20px 22px !important;
}

.a9-prd-section-editor-body textarea {
  max-height: none !important;
}

.a9-page-requirement-editor {
  display: grid !important;
  gap: 14px !important;
}

.a9-page-requirement-editor-card {
  border: 1px solid var(--a9-prd-line) !important;
  border-radius: 14px !important;
  background: #ffffff !important;
}

.a9-prd-section-editor-footer {
  border-top: 1px solid var(--a9-prd-line) !important;
  padding: 12px 20px !important;
  background: rgba(255, 255, 255, 0.96) !important;
}

@media (max-width: 900px) {
  .prd-summary-grid,
  .two-column-info,
  .prd-page-contract-meta,
  .prd-page-contract-detail-grid {
    grid-template-columns: 1fr !important;
  }

  .a9-prd-section-editor-mask {
    padding: 12px !important;
  }

  .a9-prd-section-editor-dialog {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
    max-height: calc(100vh - 24px) !important;
  }
}



/* A9.10.14.4.3.15.2.5.2_PRD_ALL_SECTION_FORM_EDITOR_STYLE_FIX
   Make page_requirements visually match the Word-like reader, and make every
   structured section editable through Chinese form controls instead of raw JSON.
*/
.prd-page-contract-document {
  display: block !important;
}

.prd-page-doc-item {
  border: none !important;
  border-bottom: 1px dashed #e2e8f0 !important;
  border-radius: 0 !important;
  padding: 14px 0 18px !important;
  background: #ffffff !important;
}

.prd-page-doc-item:last-child {
  border-bottom: none !important;
}

.prd-page-doc-header {
  display: block !important;
  margin: 0 0 10px !important;
}

.prd-page-doc-index {
  display: none !important;
}

.prd-page-doc-title-copy {
  min-width: 0 !important;
}

.prd-page-doc-title-row {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  margin-bottom: 6px !important;
}

.prd-page-doc-title-row h5 {
  margin: 0 !important;
  color: #0f172a !important;
  font-size: 16px !important;
  font-weight: 900 !important;
  line-height: 1.55 !important;
}

.prd-page-doc-title-row em {
  border: 1px solid #e2e8f0 !important;
  border-radius: 999px !important;
  padding: 2px 8px !important;
  background: #f8fafc !important;
  color: #475569 !important;
  font-size: 11px !important;
  font-style: normal !important;
  font-weight: 700 !important;
}

.prd-page-doc-title-copy p {
  margin: 0 0 8px !important;
  color: #334155 !important;
  font-size: 14px !important;
  line-height: 1.85 !important;
}

.prd-page-doc-meta {
  display: block !important;
  margin: 8px 0 10px !important;
}

.prd-page-doc-meta div {
  display: flex !important;
  gap: 8px !important;
  align-items: baseline !important;
  border-left: none !important;
  padding-left: 0 !important;
  margin: 3px 0 !important;
}

.prd-page-doc-meta dt,
.prd-page-doc-subsection h6,
.prd-page-doc-action-grid h6 {
  flex: 0 0 auto !important;
  margin: 0 !important;
  color: #475569 !important;
  font-size: 13px !important;
  font-weight: 800 !important;
}

.prd-page-doc-meta dt::after,
.prd-page-doc-subsection h6::after,
.prd-page-doc-action-grid h6::after {
  content: '：';
}

.prd-page-doc-meta dd,
.prd-page-doc-subsection p,
.prd-page-doc-action-grid p {
  margin: 0 !important;
  color: #0f172a !important;
  font-size: 14px !important;
  line-height: 1.8 !important;
}

.prd-page-doc-subsection,
.prd-page-doc-action-grid {
  margin-left: 0 !important;
}

.prd-page-doc-subsection {
  display: flex !important;
  gap: 8px !important;
  align-items: baseline !important;
  margin-top: 4px !important;
}

.prd-page-doc-action-grid {
  display: block !important;
  margin-top: 4px !important;
}

.prd-page-doc-action-grid > div {
  display: flex !important;
  gap: 8px !important;
  align-items: baseline !important;
  border: none !important;
  border-radius: 0 !important;
  padding: 0 !important;
  margin: 4px 0 !important;
  background: transparent !important;
}

.a9-prd-section-editor-body {
  overflow-y: auto !important;
  max-height: calc(88vh - 164px) !important;
}

.a9-generic-section-editor {
  display: grid;
  gap: 14px;
}

.a9-generic-editor-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 12px;
  background: #eff6ff;
}

.a9-generic-editor-intro strong {
  color: #0f172a;
  font-size: 14px;
}

.a9-generic-editor-intro p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.a9-generic-record-editor-card,
.a9-generic-object-editor-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #ffffff;
}

.a9-generic-record-editor-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.a9-generic-record-editor-card header span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 850;
}

.a9-generic-record-editor-card header strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 950;
}

.a9-generic-field-grid,
.a9-generic-object-editor-card {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.a9-generic-field-grid label,
.a9-generic-object-editor-card label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 12px;
  font-weight: 850;
}

.a9-generic-field-grid label.wide,
.a9-generic-object-editor-card label.wide {
  grid-column: 1 / -1;
}

.a9-generic-field-grid input,
.a9-generic-field-grid textarea,
.a9-generic-object-editor-card input,
.a9-generic-object-editor-card textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 9px 10px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.55;
}

.a9-generic-field-grid textarea,
.a9-generic-object-editor-card textarea {
  resize: vertical;
  min-height: 64px;
}

.a9-generic-field-grid input:focus,
.a9-generic-field-grid textarea:focus,
.a9-generic-object-editor-card input:focus,
.a9-generic-object-editor-card textarea:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

@media (max-width: 900px) {
  .a9-generic-field-grid,
  .a9-generic-object-editor-card {
    grid-template-columns: 1fr;
  }
}



/* A9.10.14.4.3.15.2.6.1_PRD_TREE_UI_DENSITY_FIX
   Final user-facing PRD reader polish:
   - one Word-like section rhythm for every MODULE_PRD section;
   - module_basic_info no longer renders as a card grid;
   - page_requirements no longer looks like a separate config panel;
   - every section uses the same subtle left blue guide line;
   - internal downstream engineering hints are hidden from the reader.
*/
.module-prd-view {
  --a9-prd-ink: #0f172a;
  --a9-prd-muted: #64748b;
  --a9-prd-line: #e2e8f0;
  --a9-prd-guide: #bfdbfe;
  --a9-prd-blue: #2563eb;
  --a9-prd-soft: #f8fafc;
}

.prd-reader-view {
  max-width: none !important;
  margin: 0 auto !important;
  gap: 0 !important;
}

.prd-reader-cover {
  margin: 0 0 18px !important;
  padding: 4px 0 18px !important;
  border: none !important;
  border-bottom: 1px solid var(--a9-prd-line) !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
}

.prd-summary-grid,
.reader-summary-grid {
  display: none !important;
}

.prd-reader-section {
  display: grid !important;
  grid-template-columns: 4px minmax(0, 1fr) !important;
  column-gap: 16px !important;
  margin: 0 !important;
  padding: 24px 0 26px !important;
  border: none !important;
  border-bottom: 1px solid var(--a9-prd-line) !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
}

.prd-reader-section:last-child {
  border-bottom: none !important;
}

.prd-reader-section::before {
  content: '';
  grid-column: 1;
  grid-row: 1 / span 2;
  width: 2px;
  height: 100%;
  margin-left: 1px;
  border-radius: 999px;
  background: var(--a9-prd-guide);
}

.prd-reader-section-header {
  grid-column: 2 !important;
  display: flex !important;
  align-items: flex-start !important;
  justify-content: space-between !important;
  gap: 16px !important;
  margin: 0 0 12px !important;
}

.prd-reader-section > :not(.prd-reader-section-header) {
  grid-column: 2 !important;
}

.prd-reader-section-header h4 {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 19px !important;
  line-height: 1.45 !important;
  font-weight: 900 !important;
  letter-spacing: -0.01em !important;
}

.prd-reader-section-header p {
  margin: 5px 0 0 !important;
  color: var(--a9-prd-muted) !important;
  font-size: 13px !important;
  line-height: 1.7 !important;
}

/* Module basic info: from grid cards to normal document lines. */
.a9-basic-grid {
  display: block !important;
  margin: 0 !important;
  padding: 0 !important;
}

.a9-basic-grid article {
  display: flex !important;
  align-items: baseline !important;
  gap: 8px !important;
  border: none !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 3px 0 !important;
  margin: 0 !important;
}

.a9-basic-grid article span {
  flex: 0 0 auto !important;
  min-width: 76px !important;
  margin: 0 !important;
  color: #475569 !important;
  font-size: 14px !important;
  line-height: 1.8 !important;
  font-weight: 800 !important;
}

.a9-basic-grid article span::after {
  content: '：';
}

.a9-basic-grid article strong {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 14px !important;
  line-height: 1.8 !important;
  font-weight: 600 !important;
}

/* Generic structured arrays/objects should keep document rhythm, not card-grid rhythm. */
.compact-card-list,
.hint-grid {
  display: block !important;
  margin: 0 !important;
  padding: 0 !important;
}

.compact-card,
.hint-card {
  display: block !important;
  border: none !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 10px 0 12px !important;
  margin: 0 !important;
  border-bottom: 1px dashed #e5e7eb !important;
}

.compact-card:last-child,
.hint-card:last-child {
  border-bottom: none !important;
}

.compact-card h5,
.hint-card h5 {
  margin: 0 0 4px !important;
  color: var(--a9-prd-ink) !important;
  font-size: 15px !important;
  line-height: 1.65 !important;
  font-weight: 850 !important;
}

.compact-card span {
  display: block !important;
  margin: 0 0 5px !important;
  color: #475569 !important;
  font-size: 12px !important;
  line-height: 1.6 !important;
  font-weight: 700 !important;
}

.compact-card p,
.hint-card p {
  margin: 4px 0 0 !important;
  color: #1f2937 !important;
  font-size: 14px !important;
  line-height: 1.82 !important;
}

/* Page requirements: exact same Word-like rhythm as other sections. */
.prd-page-contract-document {
  display: block !important;
  margin: 0 !important;
  padding: 0 !important;
  background: transparent !important;
}

.prd-page-doc-item {
  display: block !important;
  border: none !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 10px 0 16px !important;
  margin: 0 !important;
  border-bottom: 1px dashed #e5e7eb !important;
}

.prd-page-doc-item:last-child {
  border-bottom: none !important;
  padding-bottom: 0 !important;
}

.prd-page-doc-header {
  display: block !important;
  margin: 0 0 8px !important;
}

.prd-page-doc-index {
  display: none !important;
}

.prd-page-doc-title-row {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  flex-wrap: wrap !important;
  margin: 0 0 4px !important;
}

.prd-page-doc-title-row h5 {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 16px !important;
  line-height: 1.6 !important;
  font-weight: 850 !important;
}

.prd-page-doc-title-row em {
  border: none !important;
  border-radius: 999px !important;
  padding: 2px 7px !important;
  background: #eff6ff !important;
  color: #2563eb !important;
  font-size: 11px !important;
  line-height: 1.3 !important;
  font-style: normal !important;
  font-weight: 750 !important;
}

.prd-page-doc-title-copy p {
  margin: 0 0 7px !important;
  color: #1f2937 !important;
  font-size: 14px !important;
  line-height: 1.82 !important;
}

.prd-page-doc-meta,
.prd-page-doc-action-grid {
  display: block !important;
  margin: 0 !important;
  padding: 0 !important;
}

.prd-page-doc-meta div,
.prd-page-doc-subsection,
.prd-page-doc-action-grid > div {
  display: flex !important;
  align-items: baseline !important;
  gap: 8px !important;
  border: none !important;
  border-radius: 0 !important;
  background: transparent !important;
  padding: 0 !important;
  margin: 4px 0 !important;
}

.prd-page-doc-meta dt,
.prd-page-doc-subsection h6,
.prd-page-doc-action-grid h6 {
  flex: 0 0 auto !important;
  min-width: 64px !important;
  margin: 0 !important;
  color: #475569 !important;
  font-size: 14px !important;
  line-height: 1.78 !important;
  font-weight: 800 !important;
}

.prd-page-doc-meta dt::after,
.prd-page-doc-subsection h6::after,
.prd-page-doc-action-grid h6::after {
  content: '：';
}

.prd-page-doc-meta dd,
.prd-page-doc-subsection p,
.prd-page-doc-action-grid p {
  margin: 0 !important;
  color: var(--a9-prd-ink) !important;
  font-size: 14px !important;
  line-height: 1.78 !important;
}

@media (max-width: 900px) {
  .prd-reader-section {
    grid-template-columns: 3px minmax(0, 1fr) !important;
    column-gap: 12px !important;
  }

  .a9-basic-grid article,
  .prd-page-doc-meta div,
  .prd-page-doc-subsection,
  .prd-page-doc-action-grid > div {
    display: block !important;
  }

  .a9-basic-grid article span,
  .prd-page-doc-meta dt,
  .prd-page-doc-subsection h6,
  .prd-page-doc-action-grid h6 {
    min-width: 0 !important;
  }
}

/* A9.10.14.4.3.15.4.10.2_MODULE_PRD_USER_VIEW_FLEX
   User-facing MODULE_PRD reader: one display path, no duplicate title/cover/version,
   no default internal-contract fields, and document-only custom sections.
*/
.prd-view-toolbar,
.prd-reader-cover,
.prd-summary-grid,
.reader-summary-grid,
.view-mode-link,
.version-badge {
  display: none !important;
}

.module-prd-view {
  gap: 0 !important;
}

.a9-prd-user-actions {
  display: flex;
  justify-content: flex-end;
  margin: 0 0 10px;
}

.a9-prd-add-content-button {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 7px 13px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.a9-prd-add-content-button:hover {
  background: #dbeafe;
}

.prd-reader-empty {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 18px;
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

.a9-prd-section-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.a9-prd-mini-action {
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 5px 8px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.a9-prd-mini-action:hover:not(:disabled) {
  border-color: #bfdbfe;
  color: #1d4ed8;
  background: #eff6ff;
}

.a9-prd-mini-action.danger:hover:not(:disabled) {
  border-color: #fecaca;
  color: #b91c1c;
  background: #fef2f2;
}

.a9-prd-mini-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.a9-prd-add-content-dialog {
  width: min(760px, calc(100vw - 32px));
  max-height: calc(100vh - 44px);
  overflow: hidden;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.28);
}

.a9-prd-add-choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 20px 20px;
}

.a9-prd-add-choice-grid button {
  display: grid;
  gap: 6px;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 13px;
  background: #ffffff;
  cursor: pointer;
}

.a9-prd-add-choice-grid button:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.a9-prd-add-choice-grid button.document-only {
  grid-column: 1 / -1;
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.a9-prd-add-choice-grid strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
}

.a9-prd-add-choice-grid span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.a9-custom-section-editor {
  display: grid;
  gap: 12px;
}

.a9-custom-section-editor label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 12px;
  font-weight: 850;
}

.a9-custom-section-editor input,
.a9-custom-section-editor textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 9px 10px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.6;
}

.a9-custom-section-editor textarea {
  resize: vertical;
  min-height: 130px;
}

.a9-prd-section-editor-dialog.compact {
  width: min(720px, calc(100vw - 32px));
}

@media (max-width: 760px) {
  .a9-prd-add-choice-grid {
    grid-template-columns: 1fr;
  }
}


/* A9.10.14.4.3.15.4.10.2.3_PRD_EDITOR_UX_LAYERING
   Final user-facing PRD editor experience polish.
   Core sections remain structured editors; custom sections remain document-only.
*/
.prd-reader-section {
  grid-template-columns: 2px minmax(0, 1fr) !important;
  column-gap: 10px !important;
  padding: 20px 0 22px !important;
}

.prd-reader-section::before {
  width: 2px !important;
  margin-left: 0 !important;
}

.prd-reader-section-header {
  margin-bottom: 10px !important;
}

.a9-prd-section-type-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 7px;
  flex-wrap: wrap;
}

.a9-prd-section-type-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 900;
  line-height: 1.2;
}

.a9-prd-section-type-pill.business-structure {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.a9-prd-section-type-pill.document-only {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.a9-prd-section-type-hint {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.a9-generic-editor-intro p,
.a9-page-editor-intro p,
.a9-prd-section-editor-header span {
  color: #64748b !important;
}

.a9-generic-record-editor-card > header span,
.a9-page-requirement-editor-card > header span {
  color: #2563eb !important;
  font-weight: 900 !important;
}

.a9-generic-record-editor-card > header strong,
.a9-page-requirement-editor-card > header strong {
  color: #0f172a !important;
  font-size: 16px !important;
}

</style>