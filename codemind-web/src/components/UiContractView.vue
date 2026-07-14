<template>
  <section class="contract-view">
    <article class="contract-card">
      <p class="eyebrow">Contract Summary</p>
      <h3>契约摘要</h3>

      <div class="summary-grid">
        <div>
          <strong>{{ contract.schemaVersion || '1.0' }}</strong>
          <span>Schema Version</span>
        </div>
        <div>
          <strong>{{ contract.pages?.length || 0 }}</strong>
          <span>页面数</span>
        </div>
        <div>
          <strong>{{ componentCount }}</strong>
          <span>组件数</span>
        </div>
        <div>
          <strong>{{ contract.apiBindings?.length || 0 }}</strong>
          <span>接口绑定</span>
        </div>
      </div>

      <p class="analysis">
        {{ contract.analysisSummary || '暂无分析摘要。' }}
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Routes</p>
      <h3>页面路由</h3>

      <div
        v-for="route in contract.routes || []"
        :key="route.pageId"
        class="route-row"
      >
        <strong>{{ route.title || route.pageId }}</strong>
        <code>{{ route.path || '-' }}</code>
        <span>{{ route.pageId }}</span>
      </div>

      <p
        v-if="!contract.routes?.length"
        class="empty-tip"
      >
        暂无路由定义。
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Pages & Components</p>
      <h3>页面组件树</h3>

      <div
        v-for="page in contract.pages || []"
        :key="page.pageId"
        class="page-block"
      >
        <header>
          <strong>{{ page.pageName || page.title || page.pageId }}</strong>
          <span>{{ page.layout || '-' }}</span>
        </header>

        <p>{{ page.description || '暂无页面说明。' }}</p>

        <ul>
          <li
            v-for="component in page.components || []"
            :key="component.componentId"
          >
            <span>{{ component.type }}</span>
            {{ component.title || component.label || component.componentId }}
            <em>{{ componentHint(component) }}</em>
          </li>
        </ul>
      </div>
    </article>

    <article class="contract-card">
      <p class="eyebrow">API Bindings</p>
      <h3>接口绑定</h3>

      <div
        v-for="binding in contract.apiBindings || []"
        :key="bindingKey(binding)"
        class="api-row"
      >
        <strong>{{ binding.method || '-' }} {{ binding.path || '-' }}</strong>
        <span>{{ binding.bindingKey || binding.mockBinding || binding.mockAction || '-' }}</span>
      </div>

      <p
        v-if="!contract.apiBindings?.length"
        class="empty-tip"
      >
        暂无接口绑定。
      </p>
    </article>

    <article class="contract-card">
      <p class="eyebrow">Development Rules</p>
      <h3>开发一致性规则</h3>

      <dl class="rule-grid">
        <template
          v-for="(value, key) in contract.developmentRules || {}"
          :key="String(key)"
        >
          <dt>{{ key }}</dt>
          <dd>{{ formatValue(value) }}</dd>
        </template>
      </dl>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  UiContractComponent,
  UiContractContent,
} from '../types/uiContract'

const props = defineProps<{
  contract: UiContractContent
}>()

const componentCount = computed(() => {
  return (props.contract.pages || []).reduce((total, page) => {
    return total + countComponents(page.components || [])
  }, 0)
})

function countComponents(components: UiContractComponent[]): number {
  return components.reduce((total, component) => {
    return total +
      1 +
      countComponents(component.components || []) +
      (component.tabs || []).reduce((tabTotal, tab) => {
        return tabTotal + countComponents(tab.components || [])
      }, 0)
  }, 0)
}

function componentHint(component: UiContractComponent): string {
  if (component.type === 'form') {
    return `字段 ${component.fields?.length || 0} 个`
  }

  if (component.type === 'table') {
    return `列 ${component.columns?.length || 0} 个`
  }

  if (component.type === 'modal') {
    return `动作 ${component.actions?.length || 0} 个`
  }

  return ''
}

function bindingKey(binding: Record<string, unknown>): string {
  return String(
    binding.bindingKey ||
      `${binding.method || ''}-${binding.path || ''}-${binding.mockBinding || ''}-${binding.mockAction || ''}`,
  )
}

function formatValue(value: unknown): string {
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-grid div {
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.summary-grid strong,
.summary-grid span {
  display: block;
}

.summary-grid strong {
  color: #0f172a;
  font-size: 22px;
}

.summary-grid span {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
}

.analysis {
  margin: 14px 0 0;
  color: #334155;
  line-height: 1.7;
}

.route-row,
.api-row,
.page-block {
  border-top: 1px solid #e2e8f0;
  padding: 12px 0;
}

.route-row strong,
.route-row code,
.route-row span,
.api-row strong,
.api-row span {
  display: block;
}

.route-row code {
  width: fit-content;
  margin: 5px 0;
  border-radius: 8px;
  padding: 3px 6px;
  background: #f1f5f9;
  color: #0f172a;
}

.route-row span,
.api-row span,
.empty-tip {
  color: #64748b;
  font-size: 12px;
}

.page-block header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.page-block header strong {
  color: #0f172a;
}

.page-block header span {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.page-block p {
  margin: 6px 0 10px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.page-block ul {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  line-height: 1.8;
}

.page-block li span {
  display: inline-flex;
  margin-right: 6px;
  border-radius: 999px;
  padding: 2px 7px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 800;
}

.page-block li em {
  margin-left: 6px;
  color: #94a3b8;
  font-style: normal;
  font-size: 12px;
}

.rule-grid {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.rule-grid dt {
  color: #64748b;
}

.rule-grid dd {
  margin: 0;
  color: #0f172a;
  font-weight: 800;
}

@media (max-width: 760px) {
  .summary-grid,
  .rule-grid {
    grid-template-columns: 1fr;
  }
}
</style>
