<template>
  <section class="ui-quality-panel">
    <header class="quality-header">
      <div>
        <p class="eyebrow">UI Contract Quality</p>
        <h3>交互原型质量检查</h3>
        <span>
          检查 UI_CONTRACT 是否适合预览、评审和交给后续开发 Agent 使用。
        </span>
      </div>

      <div
        class="score-badge"
        :class="summary.grade.toLowerCase()"
      >
        <strong>{{ summary.score }}</strong>
        <span>{{ summary.gradeLabel }}</span>
      </div>
    </header>

    <div class="quality-metrics">
      <article>
        <small>组件</small>
        <strong>{{ summary.componentCount }}</strong>
      </article>
      <article>
        <small>动作</small>
        <strong>{{ summary.actionCount }}</strong>
      </article>
      <article>
        <small>字段</small>
        <strong>{{ summary.fieldCount }}</strong>
      </article>
      <article>
        <small>接口绑定</small>
        <strong>{{ summary.bindingCount }}</strong>
      </article>
      <article>
        <small>Mock 数据</small>
        <strong>{{ summary.mockDataKeyCount }}</strong>
      </article>
    </div>

    <div class="quality-status-grid">
      <article :class="{ ok: summary.canPreview, bad: !summary.canPreview }">
        <strong>{{ summary.canPreview ? '可预览' : '预览受阻' }}</strong>
        <span>
          {{ summary.canPreview ? '运行时具备基础渲染条件。' : '存在阻断问题，预览可能为空或异常。' }}
        </span>
      </article>

      <article :class="{ ok: summary.canHandoff, warn: !summary.canHandoff }">
        <strong>{{ summary.canHandoff ? '可交付开发' : '交付前需检查' }}</strong>
        <span>
          {{ summary.canHandoff ? '契约质量足以进入开发 Agent。' : '建议先处理阻断项和关键警告。' }}
        </span>
      </article>
    </div>

    <UiContractCoverageMatrix
      :contract="contract"
    />

    <div class="issue-summary">
      <span class="blocker">阻断 {{ summary.blockerCount }}</span>
      <span class="warning">警告 {{ summary.warningCount }}</span>
      <span class="info">提示 {{ summary.infoCount }}</span>
      <span class="pass">通过 {{ summary.passCount }}</span>
    </div>

    <section
      v-for="group in groupedIssues"
      :key="group.category"
      class="issue-group"
    >
      <header>
        <h4>{{ categoryLabel(group.category) }}</h4>
        <span>{{ group.items.length }} 项</span>
      </header>

      <article
        v-for="issue in group.items"
        :key="issue.id"
        class="issue-card"
        :class="issue.level.toLowerCase()"
      >
        <div class="issue-main">
          <span class="level-pill">{{ levelLabel(issue.level) }}</span>
          <div>
            <strong>{{ issue.title }}</strong>
            <p>{{ issue.message }}</p>
            <small v-if="issue.path">位置：{{ issue.path }}</small>
            <small v-if="issue.suggestion">建议：{{ issue.suggestion }}</small>
          </div>
        </div>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
} from 'vue'
import UiContractCoverageMatrix from './UiContractCoverageMatrix.vue'
import {
  analyzeUiContractQuality,
  type UiContractQualityCategory,
  type UiContractQualityIssue,
  type UiContractQualityLevel,
} from '../domain/uiContractQuality'

const props = defineProps<{
  contract: Record<string, unknown> | null
}>()

const summary = computed(() => analyzeUiContractQuality(props.contract))

const groupedIssues = computed(() => {
  const groups = new Map<UiContractQualityCategory, UiContractQualityIssue[]>()

  for (const issue of summary.value.issues) {
    const current = groups.get(issue.category) || []
    current.push(issue)
    groups.set(issue.category, current)
  }

  return Array.from(groups.entries()).map(([category, items]) => ({
    category,
    items,
  }))
})

function categoryLabel(
  category: UiContractQualityCategory,
) {
  const labels: Record<UiContractQualityCategory, string> = {
    STRUCTURE: '契约结构',
    RENDERABILITY: '运行时渲染',
    DATA: '数据与 Mock',
    INTERACTION: '交互动作',
    STATE: '页面状态',
    HANDOFF: '开发交接',
  }

  return labels[category]
}

function levelLabel(
  level: UiContractQualityLevel,
) {
  const labels: Record<UiContractQualityLevel, string> = {
    BLOCKER: '阻断',
    WARNING: '警告',
    INFO: '提示',
    PASS: '通过',
  }

  return labels[level]
}
</script>

<style scoped>
.ui-quality-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 18px;
}

.ui-quality-panel,
.ui-quality-panel * {
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.quality-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  border: 1px solid #dbe4ef;
  border-radius: 18px;
  padding: 18px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.eyebrow {
  margin: 0 0 6px;
  color: #4f46e5;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h3,
h4 {
  margin: 0;
  color: #0f172a;
}

.quality-header span,
.issue-card p,
.issue-card small,
.quality-status-grid span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.score-badge {
  display: grid;
  min-width: 132px;
  gap: 3px;
  border-radius: 16px;
  padding: 12px;
  text-align: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.score-badge strong {
  color: #0f172a;
  font-size: 30px;
  line-height: 1;
}

.score-badge.excellent,
.score-badge.good {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.score-badge.usable {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.score-badge.needs_fix {
  background: #fffbeb;
  border-color: #fde68a;
}

.score-badge.blocked {
  background: #fef2f2;
  border-color: #fecaca;
}

.quality-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.quality-metrics article,
.quality-status-grid article {
  display: grid;
  gap: 6px;
  border: 1px solid #dbe4ef;
  border-radius: 16px;
  padding: 13px;
  background: #ffffff;
}

.quality-metrics small {
  color: #64748b;
  font-size: 12px;
}

.quality-metrics strong {
  color: #0f172a;
  font-size: 20px;
}

.quality-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quality-status-grid strong {
  color: #0f172a;
}

.quality-status-grid article.ok {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.quality-status-grid article.warn {
  border-color: #fde68a;
  background: #fffbeb;
}

.quality-status-grid article.bad {
  border-color: #fecaca;
  background: #fef2f2;
}

.issue-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.issue-summary span {
  border-radius: 999px;
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 900;
}

.issue-summary .blocker {
  background: #fee2e2;
  color: #991b1b;
}

.issue-summary .warning {
  background: #fef3c7;
  color: #92400e;
}

.issue-summary .info {
  background: #dbeafe;
  color: #1e40af;
}

.issue-summary .pass {
  background: #dcfce7;
  color: #166534;
}

.issue-group {
  display: grid;
  gap: 8px;
}

.issue-group > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.issue-group > header span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 800;
}

.issue-card {
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  padding: 12px;
  background: #ffffff;
}

.issue-card.blocker {
  border-color: #fecaca;
  background: #fff7f7;
}

.issue-card.warning {
  border-color: #fde68a;
  background: #fffdf2;
}

.issue-card.info {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.issue-card.pass {
  border-color: #bbf7d0;
  background: #f7fff9;
}

.issue-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
}

.level-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 24px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 900;
}

.issue-card.blocker .level-pill {
  background: #fee2e2;
  color: #991b1b;
}

.issue-card.warning .level-pill {
  background: #fef3c7;
  color: #92400e;
}

.issue-card.info .level-pill {
  background: #dbeafe;
  color: #1e40af;
}

.issue-card.pass .level-pill {
  background: #dcfce7;
  color: #166534;
}

.issue-card strong {
  color: #0f172a;
}

.issue-card p {
  margin: 4px 0 0;
}

.issue-card small {
  display: block;
  margin-top: 5px;
}

@media (max-width: 920px) {
  .quality-header,
  .quality-status-grid {
    display: grid;
  }

  .quality-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
