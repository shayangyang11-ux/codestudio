
<template>
  <section class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>接口契约</h3>
        <p v-if="artifact?.artifactId">
          Artifact：{{ artifact.artifactId }}
        </p>
        <p v-else>
          当前模块尚未生成接口契约。
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
          <span class="summary-label">契约名称</span>
          <strong>{{ basicInfo.contract_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">契约 Key</span>
          <strong>{{ basicInfo.contract_key || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">接口风格</span>
          <strong>{{ basicInfo.api_style || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">基础路径</span>
          <strong>{{ basicInfo.base_path || '-' }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>设计目标</h4>
        <p>{{ basicInfo.design_goal || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>版本策略</h4>
        <p>{{ basicInfo.version_strategy || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口清单</h4>
          <span>{{ endpoints.length }} 项</span>
        </div>

        <div class="api-list">
          <article
            v-for="endpoint in endpoints"
            :key="endpoint.api_key"
            class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ endpoint.method }}</span>
              <strong>{{ endpoint.path }}</strong>
            </div>

            <h5>{{ endpoint.api_name }}</h5>
            <span>{{ endpoint.api_key }}</span>
            <p>{{ endpoint.summary }}</p>
            <p><strong>业务目的：</strong>{{ endpoint.business_purpose }}</p>

            <div class="two-column-info">
              <div>
                <strong>成功状态码</strong>
                <p>{{ endpoint.success_status_code }}</p>
              </div>
              <div>
                <strong>事务</strong>
                <p>{{ endpoint.transaction_required ? '需要事务' : '不需要事务' }}</p>
              </div>
            </div>

            <p><strong>关联页面：</strong>{{ joinList(endpoint.related_page_keys) }}</p>
            <p><strong>关联实体：</strong>{{ joinList(endpoint.related_entity_keys) }}</p>
            <p><strong>权限要求：</strong>{{ joinList(endpoint.permission_keys) }}</p>
            <p><strong>前置条件：</strong>{{ joinList(endpoint.preconditions) }}</p>
            <p><strong>后置条件：</strong>{{ joinList(endpoint.postconditions) }}</p>
            <p><strong>事务边界：</strong>{{ endpoint.transaction_boundary }}</p>
            <p><strong>测试关注点：</strong>{{ joinList(endpoint.test_focus) }}</p>

            <div class="compact-card-list">
              <article
                v-for="param in asArray(endpoint.request_parameters)"
                :key="param.param_key"
                class="compact-card"
              >
                <h5>请求参数：{{ param.name }}</h5>
                <span>{{ param.in_location }} / {{ param.data_type }}</span>
                <p><strong>必填：</strong>{{ param.required ? '是' : '否' }}</p>
                <p>{{ param.description }}</p>
              </article>

              <article
                v-for="field in asArray(endpoint.request_body_fields)"
                :key="field.field_key"
                class="compact-card"
              >
                <h5>请求体：{{ field.name }}</h5>
                <span>{{ field.data_type }}</span>
                <p><strong>必填：</strong>{{ field.required ? '是' : '否' }}</p>
                <p><strong>关联实体字段：</strong>{{ field.related_entity_field || '无' }}</p>
                <p>{{ field.description }}</p>
              </article>

              <article
                v-for="field in asArray(endpoint.response_fields)"
                :key="field.field_key"
                class="compact-card success-card"
              >
                <h5>响应字段：{{ field.name }}</h5>
                <span>{{ field.data_type }}</span>
                <p><strong>可空：</strong>{{ field.nullable ? '是' : '否' }}</p>
                <p><strong>关联实体字段：</strong>{{ field.related_entity_field || '无' }}</p>
                <p>{{ field.description }}</p>
              </article>

              <article
                v-for="error in asArray(endpoint.error_codes)"
                :key="error.error_key"
                class="compact-card danger-card"
              >
                <h5>{{ error.error_code }}</h5>
                <span>HTTP {{ error.http_status }}</span>
                <p><strong>提示：</strong>{{ error.message }}</p>
                <p><strong>场景：</strong>{{ error.scenario }}</p>
                <p><strong>处理建议：</strong>{{ error.handling_suggestion }}</p>
              </article>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限要求</h4>
          <span>{{ permissionRequirements.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in permissionRequirements"
            :key="item.permission_key"
            class="compact-card"
          >
            <h5>{{ item.permission_name }}</h5>
            <span>{{ item.permission_key }}</span>
            <p><strong>角色：</strong>{{ joinList(item.roles) }}</p>
            <p><strong>接口：</strong>{{ joinList(item.api_keys) }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据映射</h4>
          <span>{{ dataMappings.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in dataMappings"
            :key="item.mapping_key"
            class="compact-card"
          >
            <h5>{{ item.mapping_key }}</h5>
            <span>{{ item.api_key }} / {{ item.entity_key }}</span>
            <p><strong>类型：</strong>{{ item.mapping_type }}</p>
            <p><strong>字段映射：</strong>{{ joinList(item.field_mappings) }}</p>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>流程绑定</h4>
          <span>{{ workflowBindings.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
            v-for="item in workflowBindings"
            :key="item.binding_key"
            class="state-card"
          >
            <h5>{{ item.api_key }} → {{ item.flow_step_key }}</h5>
            <span>{{ item.trigger_timing }}</span>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>安全要求</h4>
          <span>{{ securityRequirements.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in securityRequirements"
            :key="item.security_key"
            class="compact-card danger-card"
          >
            <h5>{{ item.security_type }}</h5>
            <span>{{ item.api_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>实现建议：</strong>{{ item.implementation_hint }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>分析摘要</h4>
        <p>{{ contract.analysis_summary || '-' }}</p>
      </section>
    </div>

    <ArtifactDocumentEditor
      v-else
      title="接口契约"
      artifact-type="API_CONTRACT"
      :artifact="artifact"
      :markdown="markdown"
      :editable="false"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ArtifactDocumentEditor from './ArtifactDocumentEditor.vue'
import type { ArtifactResponse } from '../types/codemind'

const props = defineProps<{
  artifact: ArtifactResponse | null
  markdown: string
}>()

const activeView = ref<'cards' | 'markdown'>('cards')


const contract = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => contract.value.basic_info || {})

const endpoints = computed<any[]>(() => asArray(contract.value.endpoints))
const permissionRequirements = computed<any[]>(() => asArray(contract.value.permission_requirements))
const dataMappings = computed<any[]>(() => asArray(contract.value.data_mappings))
const workflowBindings = computed<any[]>(() => asArray(contract.value.workflow_bindings))
const securityRequirements = computed<any[]>(() => asArray(contract.value.security_requirements))


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
