<template>
  <div class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>开发交接包</h3>
        <p>面向开发 Agent、测试 Agent 和人工开发人员的最终工程交接资产。</p>
      </div>

      <div class="prd-view-actions">
        <span class="version-badge">DEV_HANDOFF_PACKAGE</span>

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
          <span>交接包</span>
          <strong>{{ basicInfo.package_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span>关联模块</span>
          <strong>{{ basicInfo.related_module_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span>目标 Agent</span>
          <strong>{{ joinList(basicInfo.target_agents) }}</strong>
        </div>

        <div class="summary-card">
          <span>文件建议</span>
          <strong>{{ fileGenerationPlan.length }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>交接目标与实现范围</h4>
        </div>

        <div class="two-column-info">
          <div>
            <span>交接目标</span>
            <strong>{{ basicInfo.package_goal || '-' }}</strong>
          </div>

          <div>
            <span>实现范围</span>
            <strong>{{ basicInfo.implementation_scope || '-' }}</strong>
          </div>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>开发目标</h4>
          <span>{{ developmentObjectives.length }} 项</span>
        </div>

        <div v-if="developmentObjectives.length" class="compact-card-list">
          <article
            v-for="item in developmentObjectives"
            :key="item.objective_key || item.title"
            class="compact-card"
          >
            <h5>{{ item.title }}</h5>
            <p>{{ item.description }}</p>
            <div class="meta-line">
              <span>优先级：{{ item.priority }}</span>
              <span>验收引用：{{ joinList(item.acceptance_reference) }}</span>
            </div>
          </article>
        </div>

        <div v-else class="empty-hint">暂无开发目标。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>模块边界</h4>
          <span>{{ moduleBoundaries.length }} 项</span>
        </div>

        <div v-if="moduleBoundaries.length" class="compact-card-list">
          <article
            v-for="item in moduleBoundaries"
            :key="item.boundary_key || item.responsibility"
            class="compact-card"
          >
            <h5>{{ item.boundary_key }}</h5>
            <p><strong>职责：</strong>{{ item.responsibility }}</p>
            <p><strong>包含范围：</strong>{{ joinList(item.included_scope) }}</p>
            <p><strong>排除范围：</strong>{{ joinList(item.excluded_scope) }}</p>
            <p><strong>依赖模块：</strong>{{ joinList(item.dependency_modules) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无模块边界。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>前端实现清单</h4>
          <span>{{ frontendItems.length }} 项</span>
        </div>

        <div v-if="frontendItems.length" class="requirement-list">
          <article
            v-for="item in frontendItems"
            :key="item.item_key || item.page_key"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <h5>{{ item.page_name }}</h5>
              <span class="priority-badge">{{ item.page_key }}</span>
            </div>

            <p><strong>路由：</strong>{{ item.route_path || '无' }}</p>
            <p><strong>组件：</strong>{{ joinList(item.components) }}</p>
            <p><strong>交互：</strong>{{ joinList(item.interactions) }}</p>
            <p><strong>依赖接口：</strong>{{ joinList(item.api_dependencies) }}</p>
            <p><strong>校验点：</strong>{{ joinList(item.validation_points) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无前端实现清单。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>后端实现清单</h4>
          <span>{{ backendItems.length }} 项</span>
        </div>

        <div v-if="backendItems.length" class="requirement-list">
          <article
            v-for="item in backendItems"
            :key="item.item_key || item.service_name"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <h5>{{ item.service_name }}</h5>
              <span class="priority-badge">BACKEND</span>
            </div>

            <p><strong>职责：</strong>{{ item.responsibility }}</p>
            <p><strong>关联接口：</strong>{{ joinList(item.api_keys) }}</p>
            <p><strong>关联实体：</strong>{{ joinList(item.entity_keys) }}</p>
            <p><strong>业务规则：</strong>{{ joinList(item.business_rules) }}</p>
            <p><strong>事务说明：</strong>{{ joinList(item.transaction_notes) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无后端实现清单。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口实现清单</h4>
          <span>{{ apiItems.length }} 项</span>
        </div>

        <div v-if="apiItems.length" class="api-list">
          <article
            v-for="item in apiItems"
            :key="item.item_key || item.api_key"
            class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ item.method || 'GET' }}</span>
              <strong>{{ item.path }}</strong>
            </div>

            <h5>{{ item.api_key }}</h5>
            <p><strong>请求摘要：</strong>{{ item.request_summary }}</p>
            <p><strong>响应摘要：</strong>{{ item.response_summary }}</p>
            <p><strong>权限：</strong>{{ joinList(item.permission_keys) }}</p>
            <p><strong>错误处理：</strong>{{ joinList(item.error_handling) }}</p>
            <p><strong>测试引用：</strong>{{ joinList(item.test_reference) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无接口实现清单。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据实现清单</h4>
          <span>{{ dataItems.length }} 项</span>
        </div>

        <div v-if="dataItems.length" class="compact-card-list">
          <article
            v-for="item in dataItems"
            :key="item.item_key || item.entity_key"
            class="compact-card"
          >
            <h5>{{ item.entity_key }}</h5>
            <p><strong>建议表名：</strong>{{ item.table_name || '无' }}</p>
            <p><strong>重要字段：</strong>{{ joinList(item.important_fields) }}</p>
            <p><strong>约束：</strong>{{ joinList(item.constraints) }}</p>
            <p><strong>索引：</strong>{{ joinList(item.indexes) }}</p>
            <p><strong>迁移说明：</strong>{{ joinList(item.migration_notes) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无数据实现清单。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限与安全要求</h4>
          <span>{{ permissionAndSecurityItems.length }} 项</span>
        </div>

        <div v-if="permissionAndSecurityItems.length" class="compact-card-list">
          <article
            v-for="item in permissionAndSecurityItems"
            :key="item.item_key || item.security_type"
            class="compact-card"
          >
            <h5>{{ item.security_type }}</h5>
            <p>{{ item.description }}</p>
            <p><strong>实现建议：</strong>{{ item.implementation_hint }}</p>
            <p><strong>关联接口：</strong>{{ joinList(item.related_api_keys) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无权限与安全要求。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>文件生成建议</h4>
          <span>{{ fileGenerationPlan.length }} 项</span>
        </div>

        <div v-if="fileGenerationPlan.length" class="compact-card-list">
          <article
            v-for="item in fileGenerationPlan"
            :key="item.file_key || item.suggested_path"
            class="compact-card"
          >
            <h5>{{ item.suggested_path }}</h5>
            <p>{{ item.file_purpose }}</p>
            <div class="meta-line">
              <span>层级：{{ item.layer }}</span>
              <span>优先级：{{ item.generation_priority }}</span>
            </div>
            <p><strong>依赖：</strong>{{ joinList(item.depends_on) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无文件生成建议。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>开发顺序</h4>
          <span>{{ developmentSequence.length }} 步</span>
        </div>

        <div v-if="developmentSequence.length" class="state-flow-list">
          <article
            v-for="item in developmentSequence"
            :key="item.step_key || item.title"
            class="state-card"
          >
            <h5>{{ item.step_order }}. {{ item.title }}</h5>
            <p>{{ item.description }}</p>
            <p><strong>交付物：</strong>{{ joinList(item.deliverables) }}</p>
            <p><strong>依赖项：</strong>{{ joinList(item.dependencies) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无开发顺序。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>测试与验收交接</h4>
          <span>{{ testAndAcceptanceHandoff.length }} 项</span>
        </div>

        <div v-if="testAndAcceptanceHandoff.length" class="compact-card-list">
          <article
            v-for="item in testAndAcceptanceHandoff"
            :key="item.item_key || item.test_type"
            class="compact-card"
          >
            <h5>{{ item.test_type }}</h5>
            <p><strong>参考用例：</strong>{{ joinList(item.reference_cases) }}</p>
            <p><strong>验收点：</strong>{{ joinList(item.acceptance_points) }}</p>
            <p><strong>自动化提示：</strong>{{ item.automation_hint || '无' }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无测试与验收交接。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>风险与注意事项</h4>
          <span>{{ risksAndAttention.length }} 项</span>
        </div>

        <div v-if="risksAndAttention.length" class="compact-card-list">
          <article
            v-for="item in risksAndAttention"
            :key="item.risk_key || item.title"
            class="compact-card danger-card"
          >
            <h5>{{ item.title }}</h5>
            <p><strong>风险等级：</strong>{{ item.risk_level }}</p>
            <p><strong>风险说明：</strong>{{ item.description }}</p>
            <p><strong>规避措施：</strong>{{ item.mitigation }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无风险与注意事项。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>分析摘要</h4>
        </div>

        <p>{{ packageData.analysis_summary || '暂无分析摘要。' }}</p>
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

const packageData = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => packageData.value.basic_info || {})

const developmentObjectives = computed<any[]>(() => asArray(packageData.value.development_objectives))
const moduleBoundaries = computed<any[]>(() => asArray(packageData.value.module_boundaries))
const frontendItems = computed<any[]>(() => asArray(packageData.value.frontend_implementation_items))
const backendItems = computed<any[]>(() => asArray(packageData.value.backend_implementation_items))
const apiItems = computed<any[]>(() => asArray(packageData.value.api_implementation_items))
const dataItems = computed<any[]>(() => asArray(packageData.value.data_implementation_items))
const permissionAndSecurityItems = computed<any[]>(() => asArray(packageData.value.permission_and_security_items))
const fileGenerationPlan = computed<any[]>(() => asArray(packageData.value.file_generation_plan))
const developmentSequence = computed<any[]>(() => {
  return asArray(packageData.value.development_sequence).slice().sort((a, b) => {
    const left = Number(a.step_order || 0)
    const right = Number(b.step_order || 0)
    return left - right
  })
})
const testAndAcceptanceHandoff = computed<any[]>(() => asArray(packageData.value.test_and_acceptance_handoff))
const risksAndAttention = computed<any[]>(() => asArray(packageData.value.risks_and_attention))

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