<template>
  <section class="contract-view">
    <article class="contract-card">
      <p class="eyebrow">Contract Summary</p>
      <h3>契约摘要</h3>

      <div class="summary-grid">
        <div>
          <strong>{{ contract.schemaVersion }}</strong>
          <span>Schema</span>
        </div>
        <div>
          <strong>{{ componentCount }}</strong>
          <span>组件数</span>
        </div>
        <div>
          <strong>{{ contract.interactions.length }}</strong>
          <span>动作数</span>
        </div>
        <div>
          <strong>{{ contract.qualitySummary.coverageRate }}%</strong>
          <span>FEATURE 覆盖</span>
        </div>
      </div>

      <p class="analysis">
        {{ contract.analysisSummary || '暂无分析摘要。' }}
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Page Identity</p>
      <h3>页面身份</h3>

      <dl class="identity-grid">
        <dt>Page ID</dt>
        <dd>{{ contract.identity.pageId }}</dd>
        <dt>Page Key</dt>
        <dd>{{ contract.identity.pageKey }}</dd>
        <dt>页面名称</dt>
        <dd>{{ contract.identity.pageName }}</dd>
        <dt>页面类型</dt>
        <dd>{{ contract.identity.pageKind }}</dd>
        <dt>路由</dt>
        <dd>{{ contract.page.route }}</dd>
        <dt>布局</dt>
        <dd>{{ contract.page.layout.type }}</dd>
      </dl>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Requirement Trace</p>
      <h3>需求来源追踪</h3>
      <div class="summary-grid">
        <div>
          <strong>{{ requirementSemanticLinkCount }}</strong>
          <span>语义关联</span>
        </div>
        <div>
          <strong>{{ requirementSemanticItemCount }}</strong>
          <span>页面语义项</span>
        </div>
        <div>
          <strong>{{ prototypeModuleLayoutMode }}</strong>
          <span>页面意图</span>
        </div>
        <div>
          <strong>{{ prototypeModuleSyncStatus }}</strong>
          <span>同步状态</span>
        </div>
      </div>
      <p class="analysis">
        {{ prototypeModuleTraceSummary }}
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Interactions</p>
      <h3>交互动作</h3>

      <div v-for="action in contract.interactions" :key="action.actionId" class="interaction-row">
        <div>
          <strong>{{ action.label || action.actionId }}</strong>
          <span>{{ action.actionId }}</span>
        </div>
        <code>{{ action.type }}</code>
        <em>
          {{
            action.targetPageId ||
            action.targetId ||
            action.dataBindingKey ||
            '-'
          }}
        </em>
      </div>

      <p v-if="!contract.interactions.length" class="empty-tip">
        当前页面没有交互动作。
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Feature Coverage</p>
      <h3>功能点覆盖</h3>

      <div v-for="coverage in contract.featureCoverage" :key="coverage.featureId" class="coverage-card">
        <header>
          <div>
            <strong>{{ coverage.featureName }}</strong>
            <span>{{ coverage.featureKey }}</span>
          </div>
          <em :class="coverage.status.toLowerCase()">
            {{ coverage.status }}
          </em>
        </header>

        <p>
          组件：{{ coverage.componentIds?.join('、') || '无' }}
        </p>
        <p>
          动作：{{ coverage.actionIds?.join('、') || '无' }}
        </p>

        <ul v-if="coverage.notes?.length">
          <li v-for="note in coverage.notes" :key="note">
            {{ note }}
          </li>
        </ul>
      </div>
    </article>

    <article class="contract-card">
      <p class="eyebrow">States & Scenarios</p>
      <h3>页面状态与 Mock 场景</h3>

      <div class="two-column">
        <section>
          <h4>页面状态</h4>
          <div v-for="state in contract.pageStates" :key="state.stateKey" class="mini-card">
            <strong>{{ state.title || state.stateKey }}</strong>
            <span>{{ state.type }}</span>
            <p>{{ state.message || '-' }}</p>
          </div>
        </section>

        <section>
          <h4>Mock 场景</h4>
          <div v-for="scenario in contract.mockScenarios" :key="scenario.scenarioKey" class="mini-card">
            <strong>{{ scenario.scenarioKey }}</strong>
            <span>{{ scenario.outcome }}</span>
            <p>{{ scenario.message || '-' }}</p>
          </div>
        </section>
      </div>
    </article>

    <article class="contract-card">
      <p class="eyebrow">API Bindings</p>
      <h3>接口绑定</h3>

      <div v-for="binding in contract.apiBindings" :key="binding.bindingKey" class="api-row">
        <strong>{{ binding.method }} {{ binding.path }}</strong>
        <span>{{ binding.bindingKey }}</span>
        <em>{{ binding.status || 'MOCK' }}</em>
      </div>

      <p v-if="!contract.apiBindings.length" class="empty-tip">
        暂无接口绑定。
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Quality</p>
      <h3>质量摘要</h3>

      <div class="quality-grid">
        <div>
          <strong>{{ contract.qualitySummary.score }}</strong>
          <span>质量分</span>
        </div>
        <div>
          <strong>{{ contract.qualitySummary.coverageRate }}%</strong>
          <span>覆盖率</span>
        </div>
        <div>
          <strong>{{ contract.qualitySummary.blockers?.length || 0 }}</strong>
          <span>阻断项</span>
        </div>
        <div>
          <strong>{{ contract.qualitySummary.warnings?.length || 0 }}</strong>
          <span>警告项</span>
        </div>
      </div>

      <ul v-if="contract.qualitySummary.warnings?.length" class="warning-list">
        <li v-for="warning in contract.qualitySummary.warnings" :key="warning">
          {{ warning }}
        </li>
      </ul>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Development Rules</p>
      <h3>开发一致性规则</h3>

      <dl class="rule-grid">
        <template v-for="(value, key) in contract.developmentRules" :key="String(key)">
          <dt>{{ key }}</dt>
          <dd>{{ formatValue(value) }}</dd>
        </template>
      </dl>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiContractComponentTreeItem from './UiContractComponentTreeItem.vue'
import type {
  UiContractComponent,
  UiContractContent,
} from '../types/uiContract'

const props = defineProps<{
  contract: UiContractContent
}>()

const componentCount = computed(() => {
  return countComponents(props.contract.page.componentTree)
})

const prototypeModuleSemantics = computed(() => {
  const metadata = (props.contract as any).metadata

  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    return {}
  }

  const semantics = metadata.prototypeModuleSemantics

  return semantics && typeof semantics === 'object' && !Array.isArray(semantics)
    ? semantics as Record<string, any>
    : {}
})

const requirementSemanticLinkCount = computed(() => {
  const metadata = (props.contract as any).metadata

  return Array.isArray(metadata?.requirementSemanticLinks)
    ? metadata.requirementSemanticLinks.length
    : 0
})

const requirementSemanticItemCount = computed(() => {
  return Array.isArray(prototypeModuleSemantics.value.semanticItems)
    ? prototypeModuleSemantics.value.semanticItems.length
    : 0
})

const prototypeModuleLayoutMode = computed(() => {
  const uiIntent = prototypeModuleSemantics.value.uiIntent

  return uiIntent && typeof uiIntent === 'object'
    ? String((uiIntent as Record<string, unknown>).layoutMode || '-')
    : '-'
})

const prototypeModuleSyncStatus = computed(() => {
  const prototypeBinding = prototypeModuleSemantics.value.prototypeBinding

  return prototypeBinding && typeof prototypeBinding === 'object'
    ? String((prototypeBinding as Record<string, unknown>).syncStatus || '-')
    : '-'
})

const prototypeModuleTraceSummary = computed(() => {
  const pageName =
    prototypeModuleSemantics.value.pageName ||
    props.contract.identity.pageName

  const count = requirementSemanticItemCount.value

  return count
    ? `${pageName} 已记录 ${count} 个 PRD 页面语义项，后续同步会优先按这些语义项增量更新原型。`
    : '当前原型还没有记录 PRD 页面语义项。'
})

function countComponents(
  components: UiContractComponent[],
): number {
  return components.reduce(
    (total, component) =>
      total + 1 + countComponents(component.children || []),
    0,
  )
}

function formatValue(
  value: unknown,
): string {
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }

  if (value === null || value === undefined) {
    return '-'
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}
</script>

<style scoped>
.contract-view {
  display: grid;
  gap: 16px;
  padding: 20px 24px 32px;
}

.contract-card {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 18px;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.contract-card h3 {
  margin: 0 0 14px;
  color: #0f172a;
  font-size: 16px;
}

.contract-card h4 {
  margin: 0 0 10px;
  color: #334155;
}

.summary-grid,
.quality-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-grid div,
.quality-grid div {
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.summary-grid strong,
.summary-grid span,
.quality-grid strong,
.quality-grid span {
  display: block;
}

.summary-grid strong,
.quality-grid strong {
  color: #0f172a;
  font-size: 22px;
}

.summary-grid span,
.quality-grid span {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
}

.analysis {
  margin: 14px 0 0;
  color: #475569;
  line-height: 1.7;
}

.identity-grid,
.rule-grid {
  display: grid;
  grid-template-columns: 180px 1fr;
  margin: 0;
}

.identity-grid dt,
.identity-grid dd,
.rule-grid dt,
.rule-grid dd {
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
}

.identity-grid dt,
.rule-grid dt {
  background: #f8fafc;
  color: #64748b;
}

.identity-grid dd,
.rule-grid dd {
  overflow-wrap: anywhere;
  color: #334155;
}

.component-tree,
.component-tree ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  list-style: none;
}

.component-line {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 10px;
  padding: 9px 10px;
  background: #f8fafc;
}

.component-line code {
  border-radius: 6px;
  padding: 3px 6px;
  background: #e0e7ff;
  color: #3730a3;
}

.component-line span {
  color: #64748b;
  font-size: 12px;
}

.component-line em {
  margin-left: auto;
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}

.interaction-row,
.api-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 0;
}

.interaction-row strong,
.interaction-row span {
  display: block;
}

.interaction-row span,
.api-row span,
.interaction-row em,
.api-row em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.coverage-card {
  margin-top: 10px;
  border-radius: 12px;
  padding: 13px;
  background: #f8fafc;
}

.coverage-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.coverage-card strong,
.coverage-card span {
  display: block;
}

.coverage-card span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.coverage-card em {
  align-self: start;
  border-radius: 999px;
  padding: 4px 8px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-style: normal;
}

.coverage-card em.full {
  background: #dcfce7;
  color: #166534;
}

.coverage-card em.partial {
  background: #fef3c7;
  color: #92400e;
}

.coverage-card em.missing {
  background: #fee2e2;
  color: #991b1b;
}

.coverage-card p,
.coverage-card ul {
  margin: 8px 0 0;
  color: #475569;
  font-size: 12px;
}

.two-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.mini-card {
  margin-top: 8px;
  border-radius: 10px;
  padding: 10px;
  background: #f8fafc;
}

.mini-card strong,
.mini-card span {
  display: block;
}

.mini-card span {
  margin-top: 3px;
  color: #2563eb;
  font-size: 11px;
}

.mini-card p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}

.warning-list {
  margin: 14px 0 0;
  color: #92400e;
  line-height: 1.7;
}

.empty-tip {
  margin: 0;
  color: #94a3b8;
}

@media (max-width: 760px) {

  .summary-grid,
  .quality-grid,
  .two-column {
    grid-template-columns: 1fr;
  }

  .identity-grid,
  .rule-grid {
    grid-template-columns: 1fr;
  }

  .interaction-row,
  .api-row {
    grid-template-columns: 1fr;
  }
}
</style>
