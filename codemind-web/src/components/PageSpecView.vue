
<template>
  <section class="module-prd-view">
    <div class="prd-view-toolbar compact-reader-toolbar">
      <div class="asset-title-copy">
        <h3>页面规范</h3>
        <p v-if="!artifact?.artifactId">
          当前页面尚未生成页面规范。
        </p>
      </div>

      <div class="prd-view-actions compact-reader-actions">
        <span v-if="artifact?.versionNo" class="version-badge">
          v{{ artifact.versionNo }}
        </span>

        <button
          type="button"
          class="view-mode-link"
          @click="activeView = activeView === 'markdown' ? 'cards' : 'markdown'"
        >
          {{ activeView === 'markdown' ? '切换到卡片视图' : '切换回文档视图' }}
        </button>
      </div>
    </div>

    <ManualDocumentCardView
        v-if="activeView === 'cards' && isManualDocumentEdited"
        title="页面规范"
        :artifact="artifact"
        :markdown="markdown"
    />

    <div v-else-if="activeView === 'cards'" class="prd-card-view">
      <section class="prd-summary-grid">
        <div class="summary-card">
          <span class="summary-label">页面名称</span>
          <strong>{{ basicInfo.page_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">页面 Key</span>
          <strong>{{ basicInfo.page_key || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">页面类型</span>
          <strong>{{ basicInfo.page_type || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">路由建议</span>
          <strong>{{ basicInfo.route_suggestion || '-' }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>页面目标</h4>
        <p>{{ basicInfo.page_goal || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>主要使用角色</h4>
        <p>{{ basicInfo.primary_user_role || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面布局区域</h4>
          <span>{{ layoutSections.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in layoutSections"
              :key="item.section_key"
              class="compact-card"
          >
            <h5>{{ item.section_name }}</h5>
            <span>{{ item.section_key }}</span>
            <p><strong>位置：</strong>{{ item.position }}</p>
            <p>{{ item.description }}</p>
            <p><strong>组件：</strong>{{ joinList(item.components) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面组件规范</h4>
          <span>{{ componentSpecs.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in componentSpecs"
              :key="item.component_key"
              class="compact-card"
          >
            <h5>{{ item.component_name }}</h5>
            <span>{{ item.component_key }} / {{ item.component_type }}</span>
            <p>{{ item.description }}</p>
            <p><strong>数据来源：</strong>{{ item.data_source }}</p>
            <p><strong>交互：</strong>{{ joinList(item.interactions) }}</p>
            <p><strong>展示规则：</strong>{{ joinList(item.display_rules) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面操作</h4>
          <span>{{ actionSpecs.length }} 项</span>
        </div>

        <div class="requirement-list">
          <article
              v-for="item in actionSpecs"
              :key="item.action_key"
              class="requirement-card"
          >
            <div class="requirement-card-header">
              <div>
                <h5>{{ item.action_name }}</h5>
                <span>{{ item.action_key }}</span>
              </div>
              <strong class="priority-badge">{{ item.actor }}</strong>
            </div>

            <p>{{ item.description }}</p>

            <div class="two-column-info">
              <div>
                <strong>触发位置</strong>
                <p>{{ item.trigger_position }}</p>
              </div>
              <div>
                <strong>成功反馈</strong>
                <p>{{ item.success_feedback }}</p>
              </div>
            </div>

            <p><strong>前置条件：</strong>{{ joinList(item.preconditions) }}</p>
            <p><strong>业务规则：</strong>{{ joinList(item.business_rules) }}</p>
            <p><strong>失败反馈：</strong>{{ item.failure_feedback }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>表单字段</h4>
          <span>{{ formFields.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in formFields"
              :key="item.field_key"
              class="compact-card"
          >
            <h5>{{ item.field_name }}</h5>
            <span>{{ item.field_key }} / {{ item.field_type }}</span>
            <p><strong>必填：</strong>{{ item.required ? '是' : '否' }}</p>
            <p><strong>默认值：</strong>{{ item.default_value || '无' }}</p>
            <p><strong>校验规则：</strong>{{ joinList(item.validation_rules) }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>表格列</h4>
          <span>{{ tableColumns.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in tableColumns"
              :key="item.column_key"
              class="compact-card"
          >
            <h5>{{ item.column_name }}</h5>
            <span>{{ item.column_key }}</span>
            <p><strong>数据字段：</strong>{{ item.data_field }}</p>
            <p><strong>展示类型：</strong>{{ item.display_type }}</p>
            <p><strong>排序/筛选：</strong>{{ item.sortable ? '可排序' : '不可排序' }} / {{ item.filterable ? '可筛选' : '不可筛选' }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>API 绑定</h4>
          <span>{{ apiBindings.length }} 项</span>
        </div>

        <div class="api-list">
          <article
              v-for="item in apiBindings"
              :key="item.api_key"
              class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ item.method }}</span>
              <strong>{{ item.path_suggestion }}</strong>
            </div>
            <h5>{{ item.api_name }}</h5>
            <span>{{ item.api_key }}</span>
            <p><strong>触发时机：</strong>{{ item.trigger_timing }}</p>
            <p><strong>请求摘要：</strong>{{ item.request_summary }}</p>
            <p><strong>响应用途：</strong>{{ item.response_usage }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面状态</h4>
          <span>{{ pageStates.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
              v-for="item in pageStates"
              :key="item.state_key"
              class="state-card"
          >
            <h5>{{ item.state_name }}</h5>
            <span>{{ item.state_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>UI 行为：</strong>{{ item.ui_behavior }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限控制</h4>
          <span>{{ permissionSpecs.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in permissionSpecs"
              :key="item.permission_key"
              class="compact-card"
          >
            <h5>{{ item.permission_name }}</h5>
            <span>{{ item.permission_key }}</span>
            <p><strong>角色：</strong>{{ joinList(item.roles) }}</p>
            <p><strong>受限元素：</strong>{{ joinList(item.restricted_elements) }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>异常与空状态</h4>
          <span>{{ exceptionSpecs.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in exceptionSpecs"
              :key="item.exception_key"
              class="compact-card danger-card"
          >
            <h5>{{ item.title }}</h5>
            <span>{{ item.exception_key }}</span>
            <p><strong>场景：</strong>{{ item.scenario }}</p>
            <p><strong>UI 反馈：</strong>{{ item.ui_feedback }}</p>
            <p><strong>恢复动作：</strong>{{ item.recovery_action }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>验收标准</h4>
          <span>{{ acceptanceCriteria.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
              v-for="item in acceptanceCriteria"
              :key="item.criterion_key"
              class="compact-card success-card"
          >
            <h5>{{ item.criterion_key }}</h5>
            <p>{{ item.description }}</p>
            <p><strong>验证方式：</strong>{{ item.verification_method }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>分析摘要</h4>
        <p>{{ spec.analysis_summary || '-' }}</p>
      </section>
    </div>

    <ArtifactDocumentEditor
        compact
        v-else
        title="页面规范"
        artifact-type="PAGE_SPEC"
        :artifact="artifact"
        :markdown="markdown"
        :editable="true"
        :saving="manualEditSaving"
        @save="$emit('save-manual-edit', $event)"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ArtifactDocumentEditor from './ArtifactDocumentEditor.vue'
import ManualDocumentCardView from './ManualDocumentCardView.vue'
import type {
  ArtifactResponse,
  ManualArtifactEditPayload,
} from '../types/codemind'

const props = defineProps<{
  artifact: ArtifactResponse | null
  markdown: string
  manualEditSaving?: boolean
}>()

defineEmits<{
  'save-manual-edit': [payload: ManualArtifactEditPayload]
}>()

const activeView = ref<'cards' | 'markdown'>('markdown')


const spec = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => spec.value.basic_info || {})

const layoutSections = computed<any[]>(() => asArray(spec.value.layout_sections))
const componentSpecs = computed<any[]>(() => asArray(spec.value.component_specs))
const actionSpecs = computed<any[]>(() => asArray(spec.value.action_specs))
const formFields = computed<any[]>(() => asArray(spec.value.form_fields))
const tableColumns = computed<any[]>(() => asArray(spec.value.table_columns))
const apiBindings = computed<any[]>(() => asArray(spec.value.api_bindings))
const pageStates = computed<any[]>(() => asArray(spec.value.page_states))
const permissionSpecs = computed<any[]>(() => asArray(spec.value.permission_specs))
const exceptionSpecs = computed<any[]>(() => asArray(spec.value.exception_specs))
const acceptanceCriteria = computed<any[]>(() => asArray(spec.value.acceptance_criteria))


const isManualDocumentEdited = computed(() => {
  const content = props.artifact?.content

  if (!content || typeof content !== 'object') {
    return false
  }

  return Boolean((content as Record<string, unknown>)['_manual_document_edit'])
})

function asArray(value: unknown): any[] {
  return Array.isArray(value) ? value : []
}

function joinList(value: unknown): string {
  if (!Array.isArray(value) || value.length === 0) {
    return '无'
  }

  return value.join('；')
}
</script>

