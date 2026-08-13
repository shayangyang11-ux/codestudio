<template>
  <div class="module-prd-view">
    <div class="prd-view-toolbar compact-reader-toolbar">
      <div class="asset-title-copy">
        <h3>开发交接包</h3>
        <p>语言无关 Dev Blueprint，给后续 Node.js / Java / Python 等 Dev Agent 分支消费。</p>
      </div>

      <div class="prd-view-actions compact-reader-actions">
        <span class="version-badge">DEV_BLUEPRINT V{{ blueprintVersion }}</span>

        <button
          type="button"
          class="view-mode-link"
          @click="activeView = activeView === 'markdown' ? 'cards' : 'markdown'"
        >
          {{ activeView === 'markdown' ? '切换到卡片视图' : '切换回文档视图' }}
        </button>
      </div>
    </div>

    <div v-if="activeView === 'cards'" class="prd-card-view">
      <section class="prd-summary-grid">
        <div class="summary-card">
          <span>目标节点</span>
          <strong>{{ targetNodeName }}</strong>
        </div>

        <div class="summary-card">
          <span>节点层级</span>
          <strong>{{ targetNodeType }}</strong>
        </div>

        <div class="summary-card">
          <span>技术栈策略</span>
          <strong>{{ languageNeutral ? '语言无关' : '需确认' }}</strong>
        </div>

        <div class="summary-card">
          <span>Dev Agent 就绪</span>
          <strong>{{ readinessText }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>交接目标</h4>
        </div>

        <div class="two-column-info">
          <div>
            <span>蓝图名称</span>
            <strong>{{ blueprintName }}</strong>
          </div>

          <div>
            <span>实现范围</span>
            <strong>{{ implementationScope }}</strong>
          </div>
        </div>

        <p class="section-description">{{ businessGoal }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>可转换目标栈</h4>
          <span>{{ supportedStackModes.length }} 项</span>
        </div>

        <div class="tag-list">
          <span v-for="item in supportedStackModes" :key="item" class="soft-tag">{{ item }}</span>
          <span v-if="!supportedStackModes.length" class="empty-hint">暂无目标栈提示。</span>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>来源资产引用矩阵</h4>
          <span>{{ sourceReferences.length }} 项</span>
        </div>

        <div v-if="sourceReferences.length" class="compact-table-wrap">
          <table class="compact-table">
            <thead>
              <tr>
                <th>资产</th>
                <th>版本</th>
                <th>状态</th>
                <th>用途</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in sourceReferences" :key="sourceKey(item)">
                <td>{{ sourceTitle(item) }}</td>
                <td>V{{ sourceVersion(item) }}</td>
                <td>{{ sourceStatus(item) }}</td>
                <td>{{ joinList(sourceUsedFor(item)) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else class="empty-hint">暂无来源资产引用。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面蓝图</h4>
          <span>{{ pages.length }} 项</span>
        </div>

        <div v-if="pages.length" class="compact-card-list">
          <article v-for="item in pages" :key="item.page_key || item.pageKey || item.page_name" class="compact-card">
            <h5>{{ item.page_name || item.pageName || item.page_key || item.pageKey }}</h5>
            <p>{{ item.page_goal || item.pageGoal || '暂无页面目标。' }}</p>
            <p><strong>路由语义：</strong>{{ item.route_semantics || item.routeSemantics || '无' }}</p>
            <p><strong>交互：</strong>{{ joinList(item.interactions) }}</p>
            <p><strong>状态规则：</strong>{{ joinList(item.state_rules || item.stateRules) }}</p>
            <p><strong>接口依赖：</strong>{{ joinList(item.api_dependencies || item.apiDependencies) }}</p>
            <p><strong>校验点：</strong>{{ joinList(item.validation_points || item.validationPoints) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">当前交接包没有独立页面蓝图。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>功能蓝图</h4>
          <span>{{ features.length }} 项</span>
        </div>

        <div v-if="features.length" class="compact-card-list">
          <article v-for="item in features" :key="item.feature_key || item.featureKey || item.feature_name" class="compact-card">
            <h5>{{ item.feature_name || item.featureName || item.feature_key || item.featureKey }}</h5>
            <p><strong>触发：</strong>{{ item.trigger || '-' }}</p>
            <p><strong>输入：</strong>{{ item.input_summary || item.inputSummary || '无' }}</p>
            <p><strong>输出：</strong>{{ item.output_summary || item.outputSummary || '无' }}</p>
            <p><strong>业务规则：</strong>{{ joinList(item.business_rules || item.businessRules) }}</p>
            <p><strong>接口依赖：</strong>{{ joinList(item.api_dependencies || item.apiDependencies) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无功能蓝图。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口蓝图</h4>
          <span>{{ apis.length }} 项</span>
        </div>

        <div v-if="apis.length" class="api-list">
          <article v-for="item in apis" :key="item.api_key || item.apiKey || item.path" class="api-card">
            <div class="api-card-header">
              <span class="method-badge">{{ item.method || 'GET' }}</span>
              <strong>{{ item.path || '-' }}</strong>
            </div>

            <h5>{{ item.api_key || item.apiKey || item.capability }}</h5>
            <p><strong>能力：</strong>{{ item.capability || '-' }}</p>
            <p><strong>请求：</strong>{{ item.request_summary || item.requestSummary || '-' }}</p>
            <p><strong>响应：</strong>{{ item.response_summary || item.responseSummary || '-' }}</p>
            <p><strong>关联实体：</strong>{{ joinList(item.data_entities || item.dataEntities) }}</p>
            <p><strong>权限：</strong>{{ joinList(item.permission_keys || item.permissionKeys) }}</p>
            <p><strong>错误场景：</strong>{{ joinList(item.error_scenarios || item.errorScenarios) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无接口蓝图。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据蓝图</h4>
          <span>{{ dataEntities.length }} 项</span>
        </div>

        <div v-if="dataEntities.length" class="compact-card-list">
          <article v-for="item in dataEntities" :key="item.entity_key || item.entityKey || item.entity_name" class="compact-card">
            <h5>{{ item.entity_name || item.entityName || item.entity_key || item.entityKey }}</h5>
            <p>{{ item.business_meaning || item.businessMeaning || '暂无业务含义。' }}</p>
            <p><strong>核心字段：</strong>{{ joinList(item.fields) }}</p>
            <p><strong>关系：</strong>{{ joinList(item.relationships) }}</p>
            <p><strong>约束：</strong>{{ joinList(item.constraints) }}</p>
            <p><strong>生命周期：</strong>{{ joinList(item.lifecycle_states || item.lifecycleStates) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无数据蓝图。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>UI 契约引用</h4>
          <span>{{ uiContracts.length }} 项</span>
        </div>

        <div v-if="uiContracts.length" class="compact-card-list">
          <article v-for="item in uiContracts" :key="item.ui_key || item.uiKey || item.page_key" class="compact-card">
            <h5>{{ item.ui_key || item.uiKey || item.page_key || item.pageKey }}</h5>
            <p><strong>布局：</strong>{{ item.layout_summary || item.layoutSummary || '-' }}</p>
            <p><strong>区域：</strong>{{ joinList(item.key_regions || item.keyRegions) }}</p>
            <p><strong>控件：</strong>{{ joinList(item.controls) }}</p>
            <p><strong>交互规则：</strong>{{ joinList(item.interaction_rules || item.interactionRules) }}</p>
            <p><strong>一致性约束：</strong>{{ joinList(item.visual_consistency_rules || item.visualConsistencyRules) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无 UI 契约引用。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>验收标准</h4>
          <span>{{ acceptanceCriteria.length }} 项</span>
        </div>

        <div v-if="acceptanceCriteria.length" class="compact-card-list">
          <article v-for="item in acceptanceCriteria" :key="item.acceptance_key || item.acceptanceKey || item.title" class="compact-card">
            <h5>{{ item.title }}</h5>
            <p><strong>场景：</strong>{{ item.scenario_type || item.scenarioType || '-' }}</p>
            <p><strong>前置：</strong>{{ joinList(item.preconditions) }}</p>
            <p><strong>步骤：</strong>{{ joinList(item.steps) }}</p>
            <p><strong>期望：</strong>{{ item.expected_result || item.expectedResult || '-' }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无验收标准。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>开发顺序</h4>
          <span>{{ developmentSequence.length }} 步</span>
        </div>

        <div v-if="developmentSequence.length" class="state-flow-list">
          <article v-for="item in developmentSequence" :key="item.step_key || item.stepKey || item.title" class="state-card">
            <h5>{{ item.step_order || item.stepOrder }}. {{ item.title }}</h5>
            <p>{{ item.description }}</p>
            <p><strong>交付物：</strong>{{ joinList(item.deliverables) }}</p>
            <p><strong>依赖项：</strong>{{ joinList(item.dependencies) }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无开发顺序。</div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>风险与待确认</h4>
          <span>{{ risksAndAttention.length }} 项</span>
        </div>

        <div v-if="risksAndAttention.length" class="compact-card-list">
          <article v-for="item in risksAndAttention" :key="item.risk_key || item.riskKey || item.title" class="compact-card danger-card">
            <h5>{{ item.title }}</h5>
            <p><strong>风险等级：</strong>{{ item.risk_level || item.riskLevel }}</p>
            <p><strong>风险说明：</strong>{{ item.description }}</p>
            <p><strong>规避措施：</strong>{{ item.mitigation }}</p>
          </article>
        </div>

        <div v-else class="empty-hint">暂无风险与待确认。</div>
      </section>
    </div>

    <ArtifactDocumentEditor
      v-else
      compact
      title="开发交接包"
      artifact-type="DEV_HANDOFF_PACKAGE"
      :artifact="artifact"
      :markdown="markdown"
      :editable="false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ArtifactDocumentEditor from './ArtifactDocumentEditor.vue'
import type { ArtifactResponse } from '../types/codemind'

const props = defineProps<{
  artifact: ArtifactResponse | null
  markdown: string
}>()

const activeView = ref<'cards' | 'markdown'>('markdown')
const packageData = computed<any>(() => props.artifact?.content || {})
const metadata = computed<any>(() => packageData.value.metadata || {})
const basicInfo = computed<any>(() => packageData.value.basic_info || packageData.value.basicInfo || {})
const devBlueprint = computed<any>(() => {
  return packageData.value.dev_blueprint || packageData.value.devBlueprint || {}
})
const targetNode = computed<any>(() => {
  return devBlueprint.value.targetNode || packageData.value.targetNode || {}
})
const readiness = computed<any>(() => packageData.value.readiness || devBlueprint.value.readiness || {})

const blueprintVersion = computed(() => {
  return basicInfo.value.blueprint_version || metadata.value.schemaVersion || devBlueprint.value.blueprintVersion || '2.0'
})

const targetNodeName = computed(() => {
  return (
    basicInfo.value.related_module_name ||
    basicInfo.value.relatedModuleName ||
    targetNode.value.nodeName ||
    targetNode.value.node_name ||
    devBlueprint.value.target_node_name ||
    '-'
  )
})

const targetNodeType = computed(() => {
  return (
    basicInfo.value.related_node_type ||
    basicInfo.value.relatedNodeType ||
    targetNode.value.nodeType ||
    targetNode.value.node_type ||
    devBlueprint.value.target_node_type ||
    devBlueprint.value.scopeType ||
    devBlueprint.value.scope_type ||
    '-'
  )
})

const languageNeutral = computed(() => {
  const stackPolicy = devBlueprint.value.stackPolicy || devBlueprint.value.stack_policy || {}
  return Boolean(basicInfo.value.language_neutral ?? basicInfo.value.languageNeutral ?? stackPolicy.languageNeutral ?? true)
})

const readinessText = computed(() => {
  const level = readiness.value.readinessLevel || readiness.value.readiness_level || 'WARNING'
  if (level === 'READY') return '已就绪'
  if (level === 'BLOCKED') return '有阻断'
  return '需复核'
})

const blueprintName = computed(() => {
  return devBlueprint.value.blueprint_name || devBlueprint.value.blueprintName || basicInfo.value.package_name || '-'
})

const implementationScope = computed(() => {
  return basicInfo.value.implementation_scope || basicInfo.value.implementationScope || '-'
})

const businessGoal = computed(() => {
  return devBlueprint.value.business_goal || devBlueprint.value.businessGoal || basicInfo.value.package_goal || '暂无业务目标。'
})

const supportedStackModes = computed<string[]>(() => {
  const stackPolicy = devBlueprint.value.stackPolicy || devBlueprint.value.stack_policy || {}
  return asStringArray(
    devBlueprint.value.supported_stack_modes ||
      devBlueprint.value.supportedStackModes ||
      stackPolicy.supportedStackModes ||
      stackPolicy.supported_stack_modes,
  )
})

const sourceReferences = computed<any[]>(() => {
  return asArray(
    packageData.value.source_reference_matrix ||
      packageData.value.sourceReferenceMatrix ||
      devBlueprint.value.sourceReferences ||
      devBlueprint.value.source_references ||
      packageData.value.sourceArtifacts,
  )
})

const pages = computed<any[]>(() => asArray(devBlueprint.value.pages))
const features = computed<any[]>(() => asArray(devBlueprint.value.features))
const apis = computed<any[]>(() => asArray(devBlueprint.value.apis))
const dataEntities = computed<any[]>(() => asArray(devBlueprint.value.data_entities || devBlueprint.value.dataEntities))
const uiContracts = computed<any[]>(() => asArray(devBlueprint.value.ui_contracts || devBlueprint.value.uiContracts))
const acceptanceCriteria = computed<any[]>(() => asArray(devBlueprint.value.acceptance_criteria || devBlueprint.value.acceptanceCriteria))
const risksAndAttention = computed<any[]>(() => asArray(packageData.value.risks_and_attention || packageData.value.risksAndAttention))
const developmentSequence = computed<any[]>(() => {
  return asArray(packageData.value.development_sequence || packageData.value.developmentSequence).slice().sort((a, b) => {
    const left = Number(a.step_order || a.stepOrder || 0)
    const right = Number(b.step_order || b.stepOrder || 0)
    return left - right
  })
})

function asArray(value: unknown): any[] {
  return Array.isArray(value) ? value : []
}

function asStringArray(value: unknown): string[] {
  return asArray(value).map((item) => String(item)).filter(Boolean)
}

function joinList(value: unknown): string {
  const values = asStringArray(value)
  return values.length ? values.join('；') : '无'
}

function sourceKey(item: any): string {
  return String(item.source_key || item.sourceKey || item.artifactType || item.artifact_type || item.title || Math.random())
}

function sourceTitle(item: any): string {
  const title = item.artifact_title || item.artifactTitle || item.title || item.artifactType || item.artifact_type
  const type = item.artifact_type || item.artifactType
  return type && title && !String(title).includes(String(type)) ? `${title}（${type}）` : String(title || '-')
}

function sourceVersion(item: any): string {
  return String(item.version_no || item.versionNo || '-')
}

function sourceStatus(item: any): string {
  return String(item.status || '-')
}

function sourceUsedFor(item: any): unknown {
  return item.used_for || item.usedFor || []
}
</script>

<style scoped>
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.soft-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.section-description {
  margin: 12px 0 0;
  color: #475569;
  line-height: 1.7;
}

.compact-table-wrap {
  overflow-x: auto;
}

.compact-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.compact-table th,
.compact-table td {
  border-bottom: 1px solid #e2e8f0;
  padding: 8px 10px;
  text-align: left;
  vertical-align: top;
}

.compact-table th {
  color: #64748b;
  font-weight: 700;
  background: #f8fafc;
}
</style>
