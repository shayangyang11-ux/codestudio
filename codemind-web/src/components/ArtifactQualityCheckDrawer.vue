<template>
    <Teleport to="body">
        <div v-if="visible" class="quality-mask" @click.self="$emit('close')">
            <aside class="quality-drawer">
                <header class="quality-header">
                    <div>
                        <p class="eyebrow">交付质量检查</p>
                        <h2>{{ moduleName || '当前模块' }}</h2>
                        <span>检查时间：{{ result.generatedAt }}</span>
                    </div>

                    <button class="close-button" @click="$emit('close')">
                        ×
                    </button>
                </header>

                <section class="score-card">
                    <div class="score-main">
                        <strong>{{ result.score }}</strong>
                        <span>/ 100</span>
                    </div>

                    <div class="score-desc">
                        <h3>{{ readinessText }}</h3>
                        <p>{{ readinessDescription }}</p>
                    </div>
                </section>

                <section class="asset-grid">
                    <article v-for="asset in result.assets" :key="asset.artifactType" class="asset-card"
                        :class="asset.level">
                        <div>
                            <strong>{{ asset.title }}</strong>
                            <span>{{ asset.exists ? `v${asset.versionNo || '-'}` : '未生成' }}</span>
                        </div>

                        <p>{{ asset.statusText }}</p>

                        <small>
                            {{ asset.updatedAt ? `更新时间：${formatTime(asset.updatedAt)}` : '暂无更新时间' }}
                        </small>
                    </article>
                </section>

                <section class="issue-section">
                    <div class="section-title blocker">
                        <h3>阻断项</h3>
                        <span>{{ result.blockers.length }}</span>
                    </div>

                    <div v-if="result.blockers.length === 0" class="empty-box">
                        当前没有阻断项。
                    </div>

                    <article v-for="issue in result.blockers" :key="issue.id" class="issue-card blocker">
                        <strong>{{ issue.title }}</strong>
                        <p>{{ issue.description }}</p>
                    </article>
                </section>

                <section class="issue-section">
                    <div class="section-title warning">
                        <h3>警告项</h3>
                        <span>{{ result.warnings.length }}</span>
                    </div>

                    <div v-if="result.warnings.length === 0" class="empty-box">
                        当前没有警告项。
                    </div>

                    <article v-for="issue in result.warnings" :key="issue.id" class="issue-card warning">
                        <strong>{{ issue.title }}</strong>
                        <p>{{ issue.description }}</p>
                    </article>
                </section>

                <section class="issue-section">
                    <div class="section-title pass">
                        <h3>通过项</h3>
                        <span>{{ result.passes.length }}</span>
                    </div>

                    <article v-for="issue in result.passes" :key="issue.id" class="issue-card pass">
                        <strong>{{ issue.title }}</strong>
                        <p>{{ issue.description }}</p>
                    </article>
                </section>

                <footer class="quality-footer">
                    <button class="secondary-button" @click="$emit('close')">
                        关闭
                    </button>

                    <button class="primary-button" :class="{ danger: result.blockers.length > 0 }"
                        @click="$emit('export-zip')">
                        {{ result.blockers.length > 0 ? '强制导出交接包' : '导出交接包' }}
                    </button>
                </footer>
            </aside>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RequirementArtifactType } from '../domain/requirementArtifactDefinitions'

export type ArtifactQualityLevel = 'pass' | 'warning' | 'blocker'

export interface ArtifactQualityAssetItem {
    artifactType: RequirementArtifactType
    title: string
    exists: boolean
    versionNo?: number | null
    status?: string | null
    current?: boolean | null
    updatedAt?: string | null
    level: ArtifactQualityLevel
    statusText: string
}

export interface ArtifactQualityIssue {
    id: string
    level: ArtifactQualityLevel
    title: string
    description: string
    artifactType?: RequirementArtifactType
}

export interface ArtifactQualityCheckResult {
    score: number
    generatedAt: string
    assets: ArtifactQualityAssetItem[]
    blockers: ArtifactQualityIssue[]
    warnings: ArtifactQualityIssue[]
    passes: ArtifactQualityIssue[]
}

const props = defineProps<{
    visible: boolean
    moduleName?: string
    result: ArtifactQualityCheckResult
}>()

defineEmits<{
    close: []
    'export-zip': []
}>()

const readinessText = computed(() => {
    if (props.result.blockers.length > 0) {
        return '暂不建议交付'
    }

    if (props.result.warnings.length > 0) {
        return '可交付，但建议先处理警告'
    }

    return '满足交付条件'
})

const readinessDescription = computed(() => {
    if (props.result.blockers.length > 0) {
        return '当前存在缺失、退回、空内容或链路不完整等阻断项，建议修复后再导出交付包。'
    }

    if (props.result.warnings.length > 0) {
        return '当前没有阻断项，但存在未确认、可能滞后或状态不够稳定的资产。'
    }

    return '当前模块资产完整、状态良好，可以导出交接包。'
})

function formatTime(value?: string | null): string {
    if (!value) {
        return '-'
    }

    return value.replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.quality-mask {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: flex;
    justify-content: flex-end;
    background: rgba(15, 23, 42, 0.36);
}

.quality-drawer {
    width: min(920px, 96vw);
    height: 100vh;
    overflow-y: auto;
    background: #ffffff;
    box-shadow: -20px 0 60px rgba(15, 23, 42, 0.18);
    display: flex;
    flex-direction: column;
}

.quality-header {
    position: sticky;
    top: 0;
    z-index: 2;
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 22px 24px;
    border-bottom: 1px solid #e2e8f0;
    background: #ffffff;
}

.eyebrow {
    margin: 0 0 6px;
    color: #64748b;
    font-size: 12px;
}

.quality-header h2 {
    margin: 0 0 8px;
    color: #0f172a;
    font-size: 20px;
}

.quality-header span {
    color: #64748b;
    font-size: 13px;
}

.close-button {
    width: 34px;
    height: 34px;
    border: 1px solid #cbd5e1;
    border-radius: 999px;
    background: #ffffff;
    color: #334155;
    cursor: pointer;
    font-size: 22px;
    line-height: 1;
}

.close-button:hover {
    border-color: #2563eb;
    color: #2563eb;
}

.score-card {
    margin: 18px 24px 0;
    border: 1px solid #dbeafe;
    border-radius: 18px;
    padding: 18px;
    background: linear-gradient(135deg, #eff6ff, #ffffff);
    display: flex;
    gap: 18px;
    align-items: center;
}

.score-main {
    width: 116px;
    height: 116px;
    border-radius: 999px;
    background: #2563eb;
    color: #ffffff;
    display: flex;
    align-items: baseline;
    justify-content: center;
    padding-top: 30px;
    box-sizing: border-box;
}

.score-main strong {
    font-size: 42px;
    line-height: 1;
}

.score-main span {
    font-size: 15px;
}

.score-desc h3 {
    margin: 0 0 8px;
    color: #0f172a;
    font-size: 18px;
}

.score-desc p {
    margin: 0;
    color: #475569;
    line-height: 1.7;
    font-size: 13px;
}

.asset-grid {
    padding: 18px 24px 0;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
}

.asset-card {
    border: 1px solid #e2e8f0;
    border-radius: 14px;
    padding: 12px;
    background: #ffffff;
}

.asset-card div {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    align-items: center;
}

.asset-card strong {
    color: #0f172a;
    font-size: 14px;
}

.asset-card span {
    border-radius: 999px;
    padding: 2px 8px;
    font-size: 12px;
}

.asset-card p {
    margin: 8px 0 0;
    color: #334155;
    font-size: 13px;
}

.asset-card small {
    display: block;
    margin-top: 6px;
    color: #64748b;
    font-size: 12px;
}

.asset-card.pass {
    border-color: #bbf7d0;
    background: #f0fdf4;
}

.asset-card.pass span {
    background: #dcfce7;
    color: #166534;
}

.asset-card.warning {
    border-color: #fde68a;
    background: #fffbeb;
}

.asset-card.warning span {
    background: #fef3c7;
    color: #92400e;
}

.asset-card.blocker {
    border-color: #fecaca;
    background: #fef2f2;
}

.asset-card.blocker span {
    background: #fee2e2;
    color: #991b1b;
}

.issue-section {
    padding: 18px 24px 0;
}

.section-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.section-title h3 {
    margin: 0;
    font-size: 16px;
    color: #0f172a;
}

.section-title span {
    border-radius: 999px;
    padding: 2px 9px;
    font-size: 12px;
}

.section-title.blocker span {
    background: #fee2e2;
    color: #991b1b;
}

.section-title.warning span {
    background: #fef3c7;
    color: #92400e;
}

.section-title.pass span {
    background: #dcfce7;
    color: #166534;
}

.issue-card {
    border-radius: 14px;
    padding: 12px 14px;
    margin-bottom: 10px;
    border: 1px solid #e2e8f0;
}

.issue-card strong {
    display: block;
    margin-bottom: 6px;
    color: #0f172a;
    font-size: 14px;
}

.issue-card p {
    margin: 0;
    color: #475569;
    line-height: 1.7;
    font-size: 13px;
}

.issue-card.blocker {
    border-color: #fecaca;
    background: #fef2f2;
}

.issue-card.warning {
    border-color: #fde68a;
    background: #fffbeb;
}

.issue-card.pass {
    border-color: #bbf7d0;
    background: #f0fdf4;
}

.empty-box {
    border-radius: 14px;
    padding: 18px;
    color: #64748b;
    background: #f8fafc;
    text-align: center;
}

.quality-footer {
    margin-top: auto;
    position: sticky;
    bottom: 0;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding: 16px 24px;
    border-top: 1px solid #e2e8f0;
    background: #ffffff;
}

.primary-button,
.secondary-button {
    border-radius: 10px;
    padding: 8px 13px;
    white-space: nowrap;
    cursor: pointer;
}

.primary-button {
    border: 1px solid #2563eb;
    background: #2563eb;
    color: #ffffff;
}

.secondary-button {
    border: 1px solid #cbd5e1;
    background: #ffffff;
    color: #334155;
}

.primary-button:hover:not(:disabled) {
    background: #1d4ed8;
}

.secondary-button:hover:not(:disabled) {
    border-color: #2563eb;
    color: #2563eb;
}

.primary-button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

@media (max-width: 760px) {
    .score-card {
        align-items: flex-start;
        flex-direction: column;
    }

    .asset-grid {
        grid-template-columns: 1fr;
    }
}
.primary-button.danger {
  border-color: #dc2626;
  background: #dc2626;
}

.primary-button.danger:hover {
  background: #b91c1c;
}
</style>