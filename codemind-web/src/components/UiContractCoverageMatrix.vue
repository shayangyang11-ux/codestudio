<template>
  <section class="coverage-matrix-panel">
    <header class="coverage-header">
      <div>
        <p class="eyebrow">Feature Coverage & Dev Handoff</p>
        <h4>功能覆盖与开发交接</h4>
        <span>
          检查 PAGE 下的功能点是否已经映射到组件、动作、接口、状态和 Mock 数据。
        </span>
      </div>

      <div
        class="coverage-score"
        :class="{ ready: summary.canHandoffToDevAgent, blocked: !summary.canHandoffToDevAgent }"
      >
        <strong>{{ summary.coverageRate }}%</strong>
        <span>{{ summary.coverageLabel }}</span>
      </div>
    </header>

    <div class="handoff-status">
      <article :class="{ ok: summary.canHandoffToDevAgent, warn: !summary.canHandoffToDevAgent }">
        <strong>{{ summary.canHandoffToDevAgent ? '可进入开发 Agent' : '交接前需要补齐' }}</strong>
        <span>
          {{ summary.canHandoffToDevAgent
            ? '功能覆盖矩阵已经具备开发交接条件。'
            : '请优先处理缺失覆盖和阻断项，避免开发 Agent 消费不完整资产。' }}
        </span>
      </article>

      <article>
        <strong>{{ summary.readyCount }} / {{ summary.featureCount }}</strong>
        <span>功能点可交付</span>
      </article>

      <article>
        <strong>{{ summary.partialCount }}</strong>
        <span>部分覆盖</span>
      </article>

      <article>
        <strong>{{ summary.missingCount }}</strong>
        <span>缺失覆盖</span>
      </article>
    </div>

    <section class="checklist-section">
      <header>
        <h5>开发 Agent 交接清单</h5>
        <span>{{ passedChecklistCount }} / {{ summary.checklist.length }} 通过</span>
      </header>

      <div class="checklist-grid">
        <article
          v-for="item in summary.checklist"
          :key="item.id"
          :class="{
            passed: item.passed,
            failed: !item.passed,
            blocker: !item.passed && item.level === 'BLOCKER',
          }"
        >
          <strong>
            <i>{{ item.passed ? '✓' : '!' }}</i>
            {{ item.label }}
          </strong>
          <span>{{ item.message }}</span>
          <small v-if="!item.passed && item.suggestion">
            建议：{{ item.suggestion }}
          </small>
        </article>
      </div>
    </section>

    <section class="matrix-section">
      <header>
        <h5>功能覆盖矩阵</h5>
        <span>{{ summary.featureCount }} 个功能点</span>
      </header>

      <div
        v-if="summary.rows.length"
        class="matrix-list"
      >
        <article
          v-for="row in summary.rows"
          :key="row.featureId"
          class="matrix-row"
          :class="row.status.toLowerCase()"
        >
          <div class="feature-cell">
            <span class="status-pill">{{ row.statusLabel }}</span>
            <strong>{{ row.featureName }}</strong>
            <small>{{ row.featureId }} · {{ row.featureKind }}</small>
          </div>

          <div class="ref-cell">
            <small>组件</small>
            <div v-if="row.componentRefs.length" class="ref-list">
              <span
                v-for="ref in row.componentRefs"
                :key="ref.id"
              >
                {{ ref.label }}
              </span>
            </div>
            <em v-else>未绑定</em>
          </div>

          <div class="ref-cell">
            <small>动作</small>
            <div v-if="row.actionRefs.length" class="ref-list">
              <span
                v-for="ref in row.actionRefs"
                :key="ref.id"
              >
                {{ ref.label }}
              </span>
            </div>
            <em v-else>未绑定</em>
          </div>

          <div class="ref-cell">
            <small>接口</small>
            <div v-if="row.apiRefs.length" class="ref-list">
              <span
                v-for="ref in row.apiRefs"
                :key="ref.id"
              >
                {{ ref.label }}
              </span>
            </div>
            <em v-else>未绑定</em>
          </div>

          <div
            v-if="row.issues.length"
            class="issue-cell"
          >
            <small>问题</small>
            <ul>
              <li
                v-for="issue in row.issues"
                :key="issue.message"
                :class="issue.level.toLowerCase()"
              >
                {{ issue.message }}
              </li>
            </ul>
          </div>
        </article>
      </div>

      <div
        v-else
        class="empty-coverage"
      >
        <strong>当前 UI_CONTRACT 未声明 featureCoverage</strong>
        <span>
          这不影响基础预览，但会影响开发 Agent 判断“每个 FEATURE 是否已经被组件、动作和接口覆盖”。
        </span>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
} from 'vue'
import { analyzeUiContractCoverage } from '../domain/uiContractCoverage'

const props = defineProps<{
  contract: Record<string, unknown> | null
}>()

const summary = computed(() => analyzeUiContractCoverage(props.contract))

const passedChecklistCount = computed(() => {
  return summary.value.checklist.filter((item) => item.passed).length
})
</script>

<style scoped>
.coverage-matrix-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 18px;
  background:
    radial-gradient(circle at 100% 0, rgba(79, 70, 229, 0.10), transparent 32%),
    #ffffff;
}

.coverage-matrix-panel,
.coverage-matrix-panel * {
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.coverage-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 5px;
  color: #4f46e5;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h4,
h5 {
  margin: 0;
  color: #0f172a;
}

.coverage-header span,
.handoff-status span,
.checklist-grid span,
.checklist-grid small,
.ref-cell small,
.ref-cell em,
.feature-cell small,
.empty-coverage span,
.matrix-section > header span,
.checklist-section > header span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.coverage-score {
  display: grid;
  min-width: 132px;
  align-content: center;
  gap: 4px;
  border-radius: 16px;
  padding: 12px;
  text-align: center;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.coverage-score.ready {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.coverage-score strong {
  color: #0f172a;
  font-size: 28px;
}

.handoff-status {
  display: grid;
  grid-template-columns: 1.6fr repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.handoff-status article,
.checklist-grid article {
  display: grid;
  gap: 6px;
  border: 1px solid #dbe4ef;
  border-radius: 14px;
  padding: 12px;
  background: #f8fafc;
}

.handoff-status article.ok,
.checklist-grid article.passed {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.handoff-status article.warn,
.checklist-grid article.failed {
  border-color: #fde68a;
  background: #fffbeb;
}

.checklist-grid article.blocker {
  border-color: #fecaca;
  background: #fff7f7;
}

.handoff-status strong,
.checklist-grid strong {
  color: #0f172a;
  font-size: 13px;
}

.checklist-grid strong {
  display: flex;
  gap: 7px;
  align-items: center;
}

.checklist-grid i {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-style: normal;
  font-size: 11px;
}

.checklist-grid article.passed i {
  background: #bbf7d0;
  color: #166534;
}

.checklist-grid article.failed i {
  background: #fde68a;
  color: #92400e;
}

.checklist-grid article.blocker i {
  background: #fecaca;
  color: #991b1b;
}

.checklist-section,
.matrix-section {
  display: grid;
  gap: 10px;
}

.checklist-section > header,
.matrix-section > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checklist-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.matrix-list {
  display: grid;
  gap: 10px;
}

.matrix-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.1fr) repeat(3, minmax(120px, 1fr));
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px;
  background: #ffffff;
}

.matrix-row.ready {
  border-color: #bbf7d0;
}

.matrix-row.partial {
  border-color: #fde68a;
}

.matrix-row.missing {
  border-color: #fecaca;
}

.feature-cell {
  display: grid;
  align-content: start;
  gap: 6px;
}

.feature-cell strong {
  color: #0f172a;
}

.status-pill {
  width: fit-content;
  border-radius: 999px;
  padding: 5px 9px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 900;
}

.matrix-row.ready .status-pill {
  background: #dcfce7;
  color: #166534;
}

.matrix-row.partial .status-pill {
  background: #fef3c7;
  color: #92400e;
}

.matrix-row.missing .status-pill {
  background: #fee2e2;
  color: #991b1b;
}

.ref-cell {
  display: grid;
  align-content: start;
  gap: 6px;
  min-width: 0;
}

.ref-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.ref-list span {
  border-radius: 999px;
  padding: 5px 8px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 11px;
  font-weight: 800;
}

.issue-cell {
  grid-column: 1 / -1;
  border-radius: 12px;
  padding: 10px;
  background: #f8fafc;
}

.issue-cell small {
  color: #64748b;
}

.issue-cell ul {
  margin: 6px 0 0;
  padding-left: 18px;
}

.issue-cell li {
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.issue-cell li.warning {
  color: #92400e;
}

.issue-cell li.blocker {
  color: #991b1b;
}

.empty-coverage {
  display: grid;
  gap: 6px;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  padding: 18px;
  background: #f8fafc;
}

.empty-coverage strong {
  color: #0f172a;
}

@media (max-width: 980px) {
  .coverage-header,
  .handoff-status,
  .checklist-grid,
  .matrix-row {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
