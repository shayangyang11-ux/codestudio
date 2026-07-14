<template>
  <div class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>测试规格</h3>
        <p>面向测试 Agent、开发 Agent 和验收流程的结构化测试资产。</p>
      </div>

      <div class="prd-view-actions">
        <span class="version-badge">TEST_SPEC</span>

        <button
          class="view-toggle-button"
          :class="{ active: activeView === 'cards' }"
          @click="activeView = 'cards'"
        >
          卡片视图
        </button>

        <button
          class="view-toggle-button"
          :class="{ active: activeView === 'markdown' }"
          @click="activeView = 'markdown'"
        >
          文档视图
        </button>
      </div>
    </div>

    <div v-if="activeView === 'cards'" class="prd-card-view">
      <section class="prd-summary-grid">
        <div class="summary-card">
          <span>测试规格</span>
          <strong>{{ basicInfo.test_spec_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span>关联模块</span>
          <strong>{{ basicInfo.related_module_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span>测试层级</span>
          <strong>{{ basicInfo.test_level || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span>测试用例数</span>
          <strong>{{ totalCaseCount }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>测试目标与策略</h4>
        </div>

        <div class="two-column-info">
          <div>
            <span>测试目标</span>
            <strong>{{ basicInfo.test_goal || '-' }}</strong>
          </div>

          <div>
            <span>测试策略</span>
            <strong>{{ basicInfo.test_strategy || '-' }}</strong>
          </div>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>测试范围</h4>
          <span>{{ testScope.length }} 项</span>
        </div>

        <div v-if="testScope.length" class="compact-card-list">
          <article
            v-for="item in testScope"
            :key="item.scope_key || item.scope_name"
            class="compact-card"
          >
            <h5>{{ item.scope_name }}</h5>
            <p>{{ item.description }}</p>
            <div class="meta-line">
              <span>类型：{{ item.scope_type }}</span>
              <span>纳入：{{ item.included ? '是' : '否' }}</span>
            </div>
          </article>
        </div>

        <div v-else class="empty-hint">暂无测试范围。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>验收标准</h4>
          <span>{{ acceptanceCriteria.length }} 项</span>
        </div>

        <div v-if="acceptanceCriteria.length" class="compact-card-list">
          <article
            v-for="item in acceptanceCriteria"
            :key="item.criterion_key || item.title"
            class="compact-card"
          >
            <h5>{{ item.title }}</h5>
            <p>{{ item.description }}</p>
            <div class="meta-line">
              <span>验证方式：{{ item.verification_method }}</span>
            </div>
          </article>
        </div>

        <div v-else class="empty-hint">暂无验收标准。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>功能测试用例</h4>
          <span>{{ functionalCases.length }} 条</span>
        </div>

        <div v-if="functionalCases.length" class="requirement-list">
          <article
            v-for="item in functionalCases"
            :key="item.case_key || item.title"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <h5>{{ item.title }}</h5>
              <span class="priority-badge">{{ item.priority || 'P1' }}</span>
            </div>

            <p><strong>前置条件：</strong>{{ joinList(item.preconditions) }}</p>
            <p><strong>测试步骤：</strong>{{ joinList(item.steps) }}</p>
            <p><strong>预期结果：</strong>{{ joinList(item.expected_results) }}</p>
            <p><strong>测试数据：</strong>{{ item.test_data || '无' }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无功能测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口测试用例</h4>
          <span>{{ apiCases.length }} 条</span>
        </div>

        <div v-if="apiCases.length" class="api-list">
          <article
            v-for="item in apiCases"
            :key="item.case_key || item.api_key"
            class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ item.method || 'GET' }}</span>
              <strong>{{ item.path }}</strong>
            </div>

            <h5>{{ item.title }}</h5>
            <p><strong>API Key：</strong>{{ item.api_key }}</p>
            <p><strong>请求关注：</strong>{{ joinList(item.request_focus) }}</p>
            <p><strong>状态码：</strong>{{ joinList(item.expected_status_codes) }}</p>
            <p><strong>响应校验：</strong>{{ joinList(item.expected_response_checks) }}</p>
            <p><strong>反向用例：</strong>{{ joinList(item.negative_cases) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无接口测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>业务流程测试用例</h4>
          <span>{{ flowCases.length }} 条</span>
        </div>

        <div v-if="flowCases.length" class="state-flow-list">
          <article
            v-for="item in flowCases"
            :key="item.case_key || item.flow_key"
            class="state-card"
          >
            <h5>{{ item.title }}</h5>
            <p><strong>流程 Key：</strong>{{ item.flow_key }}</p>
            <p><strong>场景：</strong>{{ item.scenario }}</p>
            <p><strong>步骤：</strong>{{ joinList(item.steps) }}</p>
            <p><strong>状态变化：</strong>{{ joinList(item.expected_state_changes) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无业务流程测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限测试用例</h4>
          <span>{{ permissionCases.length }} 条</span>
        </div>

        <div v-if="permissionCases.length" class="compact-card-list">
          <article
            v-for="item in permissionCases"
            :key="item.case_key || item.permission_key"
            class="compact-card"
          >
            <h5>{{ item.title }}</h5>
            <p><strong>角色：</strong>{{ item.role }}</p>
            <p><strong>权限：</strong>{{ item.permission_key }}</p>
            <p><strong>允许动作：</strong>{{ joinList(item.allowed_actions) }}</p>
            <p><strong>拒绝动作：</strong>{{ joinList(item.denied_actions) }}</p>
            <p><strong>预期结果：</strong>{{ item.expected_result }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无权限测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据校验测试用例</h4>
          <span>{{ dataValidationCases.length }} 条</span>
        </div>

        <div v-if="dataValidationCases.length" class="compact-card-list">
          <article
            v-for="item in dataValidationCases"
            :key="item.case_key || item.field_name"
            class="compact-card"
          >
            <h5>{{ item.title }}</h5>
            <p><strong>实体：</strong>{{ item.entity_key }}</p>
            <p><strong>字段：</strong>{{ item.field_name }}</p>
            <p><strong>规则：</strong>{{ item.validation_rule }}</p>
            <p><strong>合法示例：</strong>{{ joinList(item.valid_examples) }}</p>
            <p><strong>非法示例：</strong>{{ joinList(item.invalid_examples) }}</p>
            <p><strong>预期错误：</strong>{{ item.expected_error }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无数据校验测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>异常场景测试用例</h4>
          <span>{{ exceptionCases.length }} 条</span>
        </div>

        <div v-if="exceptionCases.length" class="compact-card-list">
          <article
            v-for="item in exceptionCases"
            :key="item.case_key || item.title"
            class="compact-card danger-card"
          >
            <h5>{{ item.title }}</h5>
            <p><strong>异常类型：</strong>{{ item.exception_type }}</p>
            <p><strong>触发条件：</strong>{{ item.trigger_condition }}</p>
            <p><strong>预期处理：</strong>{{ item.expected_behavior }}</p>
            <p><strong>关联接口：</strong>{{ joinList(item.related_api_keys) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无异常场景测试用例。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>测试数据要求</h4>
          <span>{{ testDataRequirements.length }} 项</span>
        </div>

        <div v-if="testDataRequirements.length" class="compact-card-list">
          <article
            v-for="item in testDataRequirements"
            :key="item.data_key || item.data_name"
            class="compact-card"
          >
            <h5>{{ item.data_name }}</h5>
            <p>{{ item.description }}</p>
            <div class="meta-line">
              <span>实体：{{ item.related_entity_key || '无' }}</span>
              <span>准备方式：{{ item.setup_method }}</span>
            </div>
          </article>
        </div>

        <div v-else class="empty-hint">暂无测试数据要求。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>自动化测试建议</h4>
          <span>{{ automationHints.length }} 项</span>
        </div>

        <div v-if="automationHints.length" class="compact-card-list">
          <article
            v-for="item in automationHints"
            :key="item.hint_key || item.target"
            class="compact-card"
          >
            <h5>{{ item.target }}</h5>
            <p>{{ item.recommendation }}</p>
            <div class="meta-line">
              <span>优先级：{{ item.priority }}</span>
            </div>
          </article>
        </div>

        <div v-else class="empty-hint">暂无自动化测试建议。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>风险提示</h4>
        </div>

        <ul v-if="riskNotes.length" class="requirement-list">
          <li
            v-for="item in riskNotes"
            :key="item"
            class="compact-card"
          >
            {{ item }}
          </li>
        </ul>

        <div v-else class="empty-hint">暂无风险提示。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>分析摘要</h4>
        </div>

        <p>{{ spec.analysis_summary || '暂无分析摘要。' }}</p>
      </section>
    </div>

    <article
      v-else
      class="prd-document"
      v-html="renderedMarkdown"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type { ArtifactResponse } from '../types/codemind'

const props = defineProps<{
  artifact: ArtifactResponse | null
  markdown: string
}>()

const activeView = ref<'cards' | 'markdown'>('cards')

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const spec = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => spec.value.basic_info || {})

const testScope = computed<any[]>(() => asArray(spec.value.test_scope))
const acceptanceCriteria = computed<any[]>(() => asArray(spec.value.acceptance_criteria))
const functionalCases = computed<any[]>(() => asArray(spec.value.functional_test_cases))
const apiCases = computed<any[]>(() => asArray(spec.value.api_test_cases))
const flowCases = computed<any[]>(() => asArray(spec.value.flow_test_cases))
const permissionCases = computed<any[]>(() => asArray(spec.value.permission_test_cases))
const dataValidationCases = computed<any[]>(() => asArray(spec.value.data_validation_test_cases))
const exceptionCases = computed<any[]>(() => asArray(spec.value.exception_test_cases))
const testDataRequirements = computed<any[]>(() => asArray(spec.value.test_data_requirements))
const automationHints = computed<any[]>(() => asArray(spec.value.automation_hints))
const riskNotes = computed<string[]>(() => asArray(spec.value.risk_notes).map(String))

const totalCaseCount = computed(() => {
  return (
    functionalCases.value.length +
    apiCases.value.length +
    flowCases.value.length +
    permissionCases.value.length +
    dataValidationCases.value.length +
    exceptionCases.value.length
  )
})

const renderedMarkdown = computed(() => {
  const html = markdown.render(props.markdown || '')
  return DOMPurify.sanitize(html)
})

function asArray(value: unknown): any[] {
  return Array.isArray(value) ? value : []
}

function joinList(value: unknown): string {
  if (!Array.isArray(value) || value.length === 0) {
    return '无'
  }

  return value.map((item) => String(item)).join('；')
}
</script>