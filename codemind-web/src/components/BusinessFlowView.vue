<template>
  <section class="module-prd-view">
    <div class="prd-view-toolbar">
      <div>
        <h3>业务流程</h3>
        <p v-if="artifact?.artifactId">
          Artifact：{{ artifact.artifactId }}
        </p>
        <p v-else>
          当前模块尚未生成业务流程。
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
          <span class="summary-label">流程名称</span>
          <strong>{{ basicInfo.flow_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">流程 Key</span>
          <strong>{{ basicInfo.flow_key || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">关联模块</span>
          <strong>{{ basicInfo.related_module_name || '-' }}</strong>
        </div>

        <div class="summary-card">
          <span class="summary-label">主要参与者</span>
          <strong>{{ basicInfo.primary_actor || '-' }}</strong>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>流程目标</h4>
        <p>{{ basicInfo.flow_goal || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>触发事件</h4>
        <p>{{ basicInfo.trigger_event || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <h4>流程边界</h4>
        <p>{{ basicInfo.boundary || '-' }}</p>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>参与角色</h4>
          <span>{{ participants.length }} 项</span>
        </div>

        <div class="role-grid">
          <article
            v-for="item in participants"
            :key="item.role_key"
            class="role-card"
          >
            <h5>{{ item.role_name }}</h5>
            <span>{{ item.role_key }}</span>
            <p>{{ item.responsibility }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>主流程步骤</h4>
          <span>{{ mainSteps.length }} 项</span>
        </div>

        <div class="requirement-list">
          <article
            v-for="item in sortedMainSteps"
            :key="item.step_key"
            class="requirement-card"
          >
            <div class="requirement-card-header">
              <div>
                <h5>{{ item.step_order }}. {{ item.step_name }}</h5>
                <span>{{ item.step_key }}</span>
              </div>
              <strong class="priority-badge">{{ item.actor }}</strong>
            </div>

            <p>{{ item.description }}</p>

            <div class="two-column-info">
              <div>
                <strong>输入数据</strong>
                <p>{{ joinList(item.input_data) }}</p>
              </div>
              <div>
                <strong>输出数据</strong>
                <p>{{ joinList(item.output_data) }}</p>
              </div>
            </div>

            <div class="tag-block">
              <strong>业务规则</strong>
              <div>
                <span
                  v-for="rule in item.business_rules || []"
                  :key="rule"
                  class="soft-tag"
                >
                  {{ rule }}
                </span>
              </div>
            </div>

            <div class="tag-block">
              <strong>异常点</strong>
              <div>
                <span
                  v-for="point in item.exception_points || []"
                  :key="point"
                  class="flow-tag"
                >
                  {{ point }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>分支流程</h4>
          <span>{{ branches.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in branches"
            :key="item.branch_key"
            class="compact-card"
          >
            <h5>{{ item.branch_name }}</h5>
            <span>{{ item.branch_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>条件：</strong>{{ item.condition }}</p>
            <p><strong>目标步骤：</strong>{{ item.target_step_key || '无' }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>状态流转</h4>
          <span>{{ stateTransitions.length }} 项</span>
        </div>

        <div class="state-flow-list">
          <article
            v-for="item in stateTransitions"
            :key="`${item.from_state}-${item.event}-${item.to_state}`"
            class="state-card"
          >
            <h5>{{ item.from_state }} → {{ item.to_state }}</h5>
            <span>{{ item.event }}</span>
            <p>{{ item.description }}</p>
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
            <p><strong>恢复动作：</strong>{{ item.recovery_action }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>数据交互</h4>
          <span>{{ dataInteractions.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in dataInteractions"
            :key="item.entity_key"
            class="compact-card"
          >
            <h5>{{ item.entity_name }}</h5>
            <span>{{ item.entity_key }}</span>
            <p><strong>操作：</strong>{{ item.operation }}</p>
            <p>{{ item.description }}</p>
            <p><strong>关联步骤：</strong>{{ joinList(item.related_step_keys) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>接口交互</h4>
          <span>{{ apiInteractions.length }} 项</span>
        </div>

        <div class="api-list">
          <article
            v-for="item in apiInteractions"
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
            <p><strong>关联步骤：</strong>{{ joinList(item.related_step_keys) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <div class="section-title-row">
          <h4>权限控制点</h4>
          <span>{{ permissionPoints.length }} 项</span>
        </div>

        <div class="compact-card-list">
          <article
            v-for="item in permissionPoints"
            :key="item.permission_key"
            class="compact-card"
          >
            <h5>{{ item.permission_name }}</h5>
            <span>{{ item.permission_key }}</span>
            <p>{{ item.description }}</p>
            <p><strong>角色：</strong>{{ joinList(item.roles) }}</p>
            <p><strong>关联步骤：</strong>{{ joinList(item.related_step_keys) }}</p>
          </article>
        </div>
      </section>

      <section class="prd-section-card">
        <h4>下游工程资产生成提示</h4>

        <div class="hint-grid">
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
        <p>{{ flow.analysis_summary || '-' }}</p>
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

const flow = computed<any>(() => props.artifact?.content || {})
const basicInfo = computed<any>(() => flow.value.basic_info || {})
const downstreamHints = computed<any>(() => flow.value.downstream_hints || {})

const participants = computed<any[]>(() => asArray(flow.value.participants))
const mainSteps = computed<any[]>(() => asArray(flow.value.main_steps))
const branches = computed<any[]>(() => asArray(flow.value.branches))
const stateTransitions = computed<any[]>(() => asArray(flow.value.state_transitions))
const exceptionScenarios = computed<any[]>(() => asArray(flow.value.exception_scenarios))
const dataInteractions = computed<any[]>(() => asArray(flow.value.data_interactions))
const apiInteractions = computed<any[]>(() => asArray(flow.value.api_interactions))
const permissionPoints = computed<any[]>(() => asArray(flow.value.permission_points))

const sortedMainSteps = computed<any[]>(() => {
  return [...mainSteps.value].sort((a, b) => {
    const left = Number(a.step_order || 0)
    const right = Number(b.step_order || 0)
    return left - right
  })
})

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