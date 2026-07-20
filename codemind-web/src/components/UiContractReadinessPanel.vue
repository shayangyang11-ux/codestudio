<template>
  <section class="readiness-card">
    <header class="readiness-header">
      <div>
        <p class="eyebrow">UI_CONTRACT READINESS</p>
        <h3>交互原型生成准备度</h3>
        <span>
          系统会检查 PAGE_SPEC、页面功能点基础定义和可选增强资产。
        </span>
      </div>

      <button
        class="refresh-button"
        type="button"
        :disabled="loading"
        @click="$emit('refresh')"
      >
        {{ loading ? '检查中...' : '刷新检查' }}
      </button>
    </header>

    <div
      v-if="error"
      class="readiness-message error"
    >
      <strong>准备度检查失败</strong>
      <span>{{ error }}</span>
    </div>

    <div
      v-else-if="loading && !readiness"
      class="readiness-message loading"
    >
      <strong>正在检查生成条件</strong>
      <span>正在读取当前 PAGE、PAGE_SPEC 与下属 FEATURE。</span>
    </div>

    <template v-else-if="readiness">
      <div class="status-strip">
        <div
          class="status-pill"
          :class="readiness.ready ? 'ready' : 'blocked'"
        >
          {{ readiness.ready ? '允许生成' : '暂不可生成' }}
        </div>

        <span>
          {{ readiness.pageName }}
          · {{ readiness.pageKind }}
        </span>
      </div>

      <div class="metric-grid">
        <article>
          <span>PAGE_SPEC</span>
          <strong>
            {{ readiness.pageSpecAvailable ? '已完成' : '缺失' }}
          </strong>
        </article>

        <article>
          <span>功能点总数</span>
          <strong>{{ readiness.featureCount }}</strong>
        </article>

        <article>
          <span>定义完整</span>
          <strong>{{ readiness.completeFeatureCount }}</strong>
        </article>

        <article>
          <span>待补齐</span>
          <strong>{{ incompleteFeatureCount }}</strong>
        </article>
      </div>

      <section
        v-if="readiness.blockers.length"
        class="issue-section blockers"
      >
        <h4>阻断项</h4>
        <ul>
          <li
            v-for="blocker in readiness.blockers"
            :key="blocker"
          >
            {{ blocker }}
          </li>
        </ul>
      </section>

      <section
        v-if="readiness.warnings.length"
        class="issue-section warnings"
      >
        <h4>质量警告</h4>
        <ul>
          <li
            v-for="warning in readiness.warnings"
            :key="warning"
          >
            {{ warning }}
          </li>
        </ul>
      </section>

      <details
        v-if="readiness.features.length"
        class="feature-fold"
      >
        <summary>
          <span>查看 FEATURE 明细</span>
          <em>
            {{ readiness.completeFeatureCount }} /
            {{ readiness.featureCount }}
          </em>
        </summary>

        <div class="feature-list">
          <article
            v-for="feature in readiness.features"
            :key="feature.featureId"
            class="feature-row"
          >
            <div>
              <strong>{{ feature.featureName }}</strong>
              <span>{{ feature.featureKey || feature.featureId }}</span>
            </div>

            <div class="feature-status">
              <em :class="feature.complete ? 'complete' : 'incomplete'">
                {{ feature.complete ? '完整' : '待补齐' }}
              </em>

              <small v-if="feature.missingFields.length">
                缺少：{{ feature.missingFields.join('、') }}
              </small>

              <small v-else>
                来源：{{ feature.definitionSource || '已识别' }}
              </small>
            </div>
          </article>
        </div>
      </details>

      <footer class="readiness-actions">
        <div>
          <strong>{{ actionTitle }}</strong>
          <span>{{ actionDescription }}</span>
        </div>

        <button
          class="generate-button"
          type="button"
          :disabled="!readiness.ready || loading || generating"
          @click="$emit('generate')"
        >
          {{
            generating
              ? '交互原型生成中...'
              : readiness.ready
                ? '生成交互原型'
                : '暂不可生成'
          }}
        </button>
      </footer>
    </template>

    <div
      v-else
      class="readiness-message"
    >
      <strong>尚未完成准备度检查</strong>
      <span>点击“刷新检查”读取当前页面条件。</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  UiContractGenerationReadinessResponse,
} from '../types/codemind'

const props = defineProps<{
  readiness: UiContractGenerationReadinessResponse | null
  loading: boolean
  error: string
  generating: boolean
}>()

defineEmits<{
  refresh: []
  generate: []
}>()

const incompleteFeatureCount = computed(() => {
  if (!props.readiness) return 0

  return Math.max(
    0,
    props.readiness.featureCount -
      props.readiness.completeFeatureCount,
  )
})

const actionTitle = computed(() => {
  if (!props.readiness) {
    return '等待检查'
  }

  if (!props.readiness.ready) {
    return `存在 ${props.readiness.blockers.length} 项阻断`
  }

  if (props.readiness.warnings.length) {
    return `可生成，存在 ${props.readiness.warnings.length} 项质量警告`
  }

  return '已满足交互原型生成条件'
})

const actionDescription = computed(() => {
  if (!props.readiness) {
    return '完成准备度检查后才能创建 UI_CONTRACT 任务。'
  }

  if (!props.readiness.ready) {
    return '请先补齐上方阻断项，系统不会创建无效生成任务。'
  }

  if (props.readiness.warnings.length) {
    return '警告不会阻止生成，但会影响原型完整度和后续开发一致性。'
  }

  return '当前 PAGE 可直接进入 UI_CONTRACT 生成。'
})
</script>

<style scoped>
.readiness-card {
  margin-top: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  padding: 18px;
  background:
    linear-gradient(
      180deg,
      #eff6ff 0%,
      #ffffff 72%
    );
}

.readiness-header,
.readiness-actions,
.status-strip,
.feature-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.readiness-header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
}

.readiness-header span,
.readiness-actions span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.refresh-button,
.generate-button {
  border-radius: 999px;
  padding: 9px 14px;
  font-weight: 700;
  cursor: pointer;
}

.refresh-button {
  border: 1px solid #2563eb;
  background: #ffffff;
  color: #2563eb;
}

.generate-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.refresh-button:disabled,
.generate-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.status-strip {
  align-items: center;
  margin-top: 16px;
}

.status-strip > span {
  color: #475569;
  font-size: 12px;
}

.status-pill {
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 800;
}

.status-pill.ready {
  background: #dcfce7;
  color: #166534;
}

.status-pill.blocked {
  background: #fee2e2;
  color: #991b1b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.metric-grid article {
  border: 1px solid #dbeafe;
  border-radius: 13px;
  padding: 12px;
  background: #ffffff;
}

.metric-grid span,
.metric-grid strong {
  display: block;
}

.metric-grid span {
  color: #64748b;
  font-size: 11px;
}

.metric-grid strong {
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

.issue-section {
  margin-top: 14px;
  border-radius: 13px;
  padding: 12px 14px;
}

.issue-section h4 {
  margin: 0;
  font-size: 13px;
}

.issue-section ul {
  display: grid;
  gap: 6px;
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.6;
}

.issue-section.blockers {
  background: #fef2f2;
  color: #991b1b;
}

.issue-section.warnings {
  background: #fffbeb;
  color: #92400e;
}

.feature-fold {
  margin-top: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 13px;
  background: #ffffff;
  overflow: hidden;
}

.feature-fold summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 13px;
  cursor: pointer;
  list-style: none;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.feature-fold summary::-webkit-details-marker {
  display: none;
}

.feature-fold summary em {
  color: #64748b;
  font-style: normal;
}

.feature-list {
  border-top: 1px solid #e2e8f0;
}

.feature-row {
  padding: 11px 13px;
  border-bottom: 1px solid #f1f5f9;
}

.feature-row:last-child {
  border-bottom: none;
}

.feature-row strong,
.feature-row span,
.feature-row small {
  display: block;
}

.feature-row strong {
  color: #0f172a;
  font-size: 12px;
}

.feature-row span,
.feature-row small {
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
}

.feature-status {
  text-align: right;
}

.feature-status em {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 10px;
  font-style: normal;
}

.feature-status em.complete {
  background: #dcfce7;
  color: #166534;
}

.feature-status em.incomplete {
  background: #fee2e2;
  color: #991b1b;
}

.readiness-actions {
  align-items: center;
  margin-top: 16px;
  border-top: 1px solid #dbeafe;
  padding-top: 14px;
}

.readiness-actions strong {
  color: #0f172a;
  font-size: 13px;
}

.readiness-message {
  display: grid;
  gap: 5px;
  margin-top: 14px;
  border-radius: 13px;
  padding: 13px 14px;
  background: #f8fafc;
  color: #475569;
}

.readiness-message.error {
  background: #fef2f2;
  color: #991b1b;
}

.readiness-message.loading {
  background: #eff6ff;
  color: #1e40af;
}

.readiness-message strong {
  font-size: 13px;
}

.readiness-message span {
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .readiness-header,
  .readiness-actions,
  .feature-row {
    display: grid;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .generate-button,
  .refresh-button {
    width: 100%;
  }

  .feature-status {
    text-align: left;
  }
}
</style>
