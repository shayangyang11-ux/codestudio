
<template>
  <section class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>模块 PRD</h3>
        <p v-if="artifact?.artifactId">
          Artifact：{{ artifact.artifactId }}
        </p>
        <p v-else>
          当前模块尚未生成 PRD。
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

    <ManualDocumentCardView
      v-if="activeView === 'cards' && isManualDocumentEdited"
      title="模块 PRD"
      :artifact="artifact"
      :markdown="markdown"
    />

    <div v-else-if="activeView === 'cards'" class="prd-card-view">
      <section class="prd-summary-grid">
        <div class="summary-card">
          <span class="summary-label">模块名称</span>
          <strong>{{ basicInfo.module_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">模块 Key</span>
          <strong>{{ basicInfo.module_key || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">模块类型</span>
          <strong>{{ basicInfo.module_type || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">模块位置</span>
          <strong>{{ basicInfo.module_position || '-' }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>模块目标</h4>
        <p>{{ basicInfo.module_goal || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>职责边界</h4>
        <p>{{ basicInfo.responsibility_boundary || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>业务背景</h4>
        <p>{{ prd.business_background || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>用户角色</h4>
          <span>{{ userRoles.length }} 项</span>
        </div>

        <div class="role-grid">
          <article
            v-for="role in userRoles"
            :key="role.role_key"
            class="role-card"
          >
            <h5>{{ role.role_name }}</h5>
            <span>{{ role.role_key }}</span>
            <p>{{ role.description }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>功能需求</h4>
          <span>{{ functionalRequirements.length }} 项</span>
        </div>

        <div class="requirement-list">
          <article
            v-for="item in functionalRequirements"
            :key="item.requirement_key"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <div>
                <h5>{{ item.title }}</h5>
                <span>{{ item.requirement_key }}</span>
              </div>
              <strong class="priority-badge">{{ item.priority }}</strong>
            </div>

            <p>{{ item.description }}</p>

            <div class="two-column-info">
              <div>
                <strong>输入</strong>
                <p>{{ item.input_description }}</p>
              </div>
              <div>
                <strong>输出</strong>
                <p>{{ item.output_description }}</p>
              </div>
            </div>

            <div class="tag-block">
              <strong>前置条件</strong>
              <div>
                <span
                  v-for="condition in item.preconditions || []"
                  :key="condition"
                  class="soft-tag"
                >
                  {{ condition }}
                </span>
              </div>
            </div>

            <div class="tag-block">
              <strong>后置条件</strong>
              <div>
                <span
                  v-for="condition in item.postconditions || []"
                  :key="condition"
                  class="soft-tag"
                >
                  {{ condition }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>业务规则</h4>
          <span>{{ businessRules.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in businessRules"
            :key="item.rule_key"
            class="compact-card"
          >
            <h5>{{ item.title }}</h5>
            <span>{{ item.rule_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>触发条件：</strong>{{ item.trigger_condition }}</p>
            <p><strong>期望结果：</strong>{{ item.expected_result }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>状态规则</h4>
          <span>{{ stateRules.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
            v-for="item in stateRules"
            :key="item.state_key"
            class="state-card"
          >
            <h5>{{ item.state_name }}</h5>
            <span>{{ item.state_key }}</span>
            <p>{{ item.description }}</p>
            <div class="tag-block">
              <strong>可流转至</strong>
              <div>
                <span
                  v-for="target in item.allowed_transitions || []"
                  :key="target"
                  class="flow-tag"
                >
                  {{ target }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>页面需求</h4>
          <span>{{ pageRequirements.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in pageRequirements"
            :key="item.page_key"
            class="compact-card"
          >
            <h5>{{ item.page_name }}</h5>
            <span>{{ item.page_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>主要组件：</strong>{{ joinList(item.main_components) }}</p>
            <p><strong>用户操作：</strong>{{ joinList(item.user_actions) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据需求</h4>
          <span>{{ dataRequirements.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in dataRequirements"
            :key="item.entity_key"
            class="compact-card"
          >
            <h5>{{ item.entity_name }}</h5>
            <span>{{ item.entity_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>关键字段：</strong>{{ joinList(item.important_fields) }}</p>
            <p><strong>约束：</strong>{{ joinList(item.constraints) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口需求</h4>
          <span>{{ apiRequirements.length }} 项</span>
        </div>

        <div class="api-list">
          <article
            v-for="item in apiRequirements"
            :key="item.api_key"
            class="api-card"
          >
            <div class="api-card-header">
              <span class="method-badge">{{ item.method }}</span>
              <strong>{{ item.path_suggestion }}</strong>
            </div>
            <h5>{{ item.api_name }}</h5>
            <span>{{ item.api_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>请求：</strong>{{ item.request_summary }}</p>
            <p><strong>响应：</strong>{{ item.response_summary }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限需求</h4>
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
            <p>{{ item.description }}</p>
            <p><strong>角色：</strong>{{ joinList(item.roles) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>异常场景</h4>
          <span>{{ exceptionScenarios.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in exceptionScenarios"
            :key="item.scenario_key"
            class="compact-card danger-card"
          >
            <h5>{{ item.title }}</h5>
            <span>{{ item.scenario_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>处理策略：</strong>{{ item.handling_strategy }}</p>
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
        <div class="section-title-row">
          <h4>非功能需求</h4>
          <span>{{ nonFunctionalRequirements.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in nonFunctionalRequirements"
            :key="item.nfr_key"
            class="compact-card"
          >
            <h5>{{ item.category }}</h5>
            <span>{{ item.nfr_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>目标：</strong>{{ item.target }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>后续工程资产生成提示</h4>

        <div class="hint-grid">
          <div class="hint-card">
            <h5>业务流程</h5>
            <ul>
              <li
                v-for="item in downstreamHints.business_flow_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>

          <div class="hint-card">
            <h5>页面规范</h5>
            <ul>
              <li
                v-for="item in downstreamHints.page_spec_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>

          <div class="hint-card">
            <h5>数据模型</h5>
            <ul>
              <li
                v-for="item in downstreamHints.data_model_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>

          <div class="hint-card">
            <h5>接口契约</h5>
            <ul>
              <li
                v-for="item in downstreamHints.api_contract_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>

          <div class="hint-card">
            <h5>测试规格</h5>
            <ul>
              <li
                v-for="item in downstreamHints.test_spec_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>

          <div class="hint-card">
            <h5>开发交接包</h5>
            <ul>
              <li
                v-for="item in downstreamHints.dev_handoff_hints || []"
                :key="item"
              >
                {{ item }}
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>分析摘要</h4>
        <p>{{ prd.analysis_summary || '-' }}</p>
      </section>
    </div>

    <ArtifactDocumentEditor
      v-else
      title="模块 PRD"
      artifact-type="MODULE_PRD"
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

const activeView = ref<'cards' | 'markdown'>('cards')


const prd = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => prd.value.module_basic_info || {})
const downstreamHints = computed<any>(() => prd.value.downstream_artifact_hints || {})

const userRoles = computed<any[]>(() => asArray(prd.value.user_roles))
const functionalRequirements = computed<any[]>(() => asArray(prd.value.functional_requirements))
const businessRules = computed<any[]>(() => asArray(prd.value.business_rules))
const stateRules = computed<any[]>(() => asArray(prd.value.state_rules))
const pageRequirements = computed<any[]>(() => asArray(prd.value.page_requirements))
const dataRequirements = computed<any[]>(() => asArray(prd.value.data_requirements))
const apiRequirements = computed<any[]>(() => asArray(prd.value.api_requirements))
const permissionRequirements = computed<any[]>(() => asArray(prd.value.permission_requirements))
const exceptionScenarios = computed<any[]>(() => asArray(prd.value.exception_scenarios))
const acceptanceCriteria = computed<any[]>(() => asArray(prd.value.acceptance_criteria))
const nonFunctionalRequirements = computed<any[]>(() => asArray(prd.value.non_functional_requirements))


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
