<template>
  <section class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>数据模型</h3>
        <p v-if="artifact?.artifactId">
          Artifact：{{ artifact.artifactId }}
        </p>
        <p v-else>
          当前模块尚未生成数据模型。
        </p>
      </div>

      <div class="prd-view-actions">
        <span v-if="artifact?.versionNo" class="version-badge">
          v{{ artifact.versionNo }}
        </span>

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
          <span class="summary-label">模型名称</span>
          <strong>{{ basicInfo.model_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">模型 Key</span>
          <strong>{{ basicInfo.model_key || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">关联模块</span>
          <strong>{{ basicInfo.related_module_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">数据库建议</span>
          <strong>{{ basicInfo.database_type_suggestion || '-' }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>设计目标</h4>
        <p>{{ basicInfo.design_goal || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>命名规范</h4>
        <p>{{ basicInfo.naming_convention || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>实体设计</h4>
          <span>{{ entities.length }} 项</span>
        </div>

        <div class="requirement-list">
          <article
            v-for="entity in entities"
            :key="entity.entity_key"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <div>
                <h5>{{ entity.entity_name }}</h5>
                <span>{{ entity.entity_key }} / {{ entity.table_name }}</span>
              </div>
              <strong class="priority-badge">{{ entity.aggregate_role }}</strong>
            </div>

            <p>{{ entity.description }}</p>

            <div class="two-column-info">
              <div>
                <strong>主键</strong>
                <p>{{ entity.primary_key || '-' }}</p>
              </div>
              <div>
                <strong>生命周期</strong>
                <p>{{ entity.lifecycle_summary || '-' }}</p>
              </div>
            </div>

            <div class="compact-card-list">
              <article
                v-for="field in asArray(entity.fields)"
                :key="field.field_key"
                class="compact-card"
              >
                <h5>{{ field.field_name }}</h5>
                <span>{{ field.column_name }} / {{ field.data_type }}{{ field.length ? `(${field.length})` : '' }}</span>
                <p><strong>可空：</strong>{{ field.nullable ? '是' : '否' }} / <strong>唯一：</strong>{{ field.unique ? '是' : '否' }}</p>
                <p><strong>默认值：</strong>{{ field.default_value || '无' }}</p>
                <p><strong>业务含义：</strong>{{ field.business_meaning }}</p>
                <p><strong>校验规则：</strong>{{ joinList(field.validation_rules) }}</p>
              </article>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>实体关系</h4>
          <span>{{ relationships.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
            v-for="item in relationships"
            :key="item.relationship_key"
            class="state-card"
          >
            <h5>{{ item.source_entity_key }} → {{ item.target_entity_key }}</h5>
            <span>{{ item.relationship_type }}</span>
            <p>{{ item.description }}</p>
            <p><strong>字段：</strong>{{ item.source_field }} → {{ item.target_field }}</p>
            <p><strong>级联规则：</strong>{{ joinList(item.cascade_rules) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>索引设计</h4>
          <span>{{ indexes.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in indexes"
            :key="item.index_key"
            class="compact-card"
          >
            <h5>{{ item.index_name }}</h5>
            <span>{{ item.index_type }} / {{ item.table_name }}</span>
            <p><strong>字段：</strong>{{ joinList(item.columns) }}</p>
            <p>{{ item.reason }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据约束</h4>
          <span>{{ constraints.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in constraints"
            :key="item.constraint_key"
            class="compact-card"
          >
            <h5>{{ item.constraint_key }}</h5>
            <span>{{ item.constraint_type }} / {{ item.entity_key }}</span>
            <p><strong>字段：</strong>{{ joinList(item.fields) }}</p>
            <p><strong>规则：</strong>{{ item.rule_description }}</p>
            <p><strong>违反处理：</strong>{{ item.violation_handling }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>状态模型</h4>
          <span>{{ statusModels.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
            v-for="item in statusModels"
            :key="item.status_key"
            class="state-card"
          >
            <h5>{{ item.status_key }}</h5>
            <span>{{ item.entity_key }} / {{ item.status_field }}</span>
            <p><strong>初始状态：</strong>{{ item.initial_value }}</p>
            <p><strong>允许状态：</strong>{{ joinList(item.allowed_values) }}</p>
            <p><strong>终态：</strong>{{ joinList(item.terminal_values) }}</p>
            <p><strong>流转规则：</strong>{{ joinList(item.transition_rules) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面数据绑定</h4>
          <span>{{ pageBindings.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in pageBindings"
            :key="item.binding_key"
            class="compact-card"
          >
            <h5>{{ item.page_name }}</h5>
            <span>{{ item.page_key }} / {{ item.entity_key }}</span>
            <p><strong>字段：</strong>{{ joinList(item.fields) }}</p>
            <p>{{ item.usage_description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>API 数据使用提示</h4>
          <span>{{ apiUsageHints.length }} 项</span>
        </div>

        <div class="api-list">
          <article
            v-for="item in apiUsageHints"
            :key="item.api_hint_key"
            class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ item.method }}</span>
              <strong>{{ item.path_suggestion }}</strong>
            </div>
            <h5>{{ item.api_name }}</h5>
            <span>{{ item.api_hint_key }}</span>
            <p><strong>请求实体：</strong>{{ joinList(item.request_entities) }}</p>
            <p><strong>响应实体：</strong>{{ joinList(item.response_entities) }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据安全</h4>
          <span>{{ dataSecurity.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in dataSecurity"
            :key="item.security_key"
            class="compact-card danger-card"
          >
            <h5>{{ item.security_key }}</h5>
            <span>{{ item.entity_key }} / {{ item.security_level }}</span>
            <p><strong>字段：</strong>{{ joinList(item.fields) }}</p>
            <p>{{ item.protection_strategy }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>分析摘要</h4>
        <p>{{ model.analysis_summary || '-' }}</p>
      </section>
    </div>

    <article
      v-else
      class="prd-document"
      v-html="renderedMarkdown"
    />
  </section>
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

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const model = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => model.value.basic_info || {})

const entities = computed<any[]>(() => asArray(model.value.entities))
const relationships = computed<any[]>(() => asArray(model.value.relationships))
const indexes = computed<any[]>(() => asArray(model.value.indexes))
const constraints = computed<any[]>(() => asArray(model.value.constraints))
const statusModels = computed<any[]>(() => asArray(model.value.status_models))
const pageBindings = computed<any[]>(() => asArray(model.value.page_bindings))
const apiUsageHints = computed<any[]>(() => asArray(model.value.api_usage_hints))
const dataSecurity = computed<any[]>(() => asArray(model.value.data_security))

const renderedMarkdown = computed(() => {
  const rawHtml = markdownRenderer.render(props.markdown || '')
  return DOMPurify.sanitize(rawHtml)
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