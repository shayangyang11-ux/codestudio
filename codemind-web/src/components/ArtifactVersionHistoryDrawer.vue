<template>
    <Teleport to="body">
        <div v-if="visible" class="version-mask" @click.self="$emit('close')">
            <aside class="version-drawer">
                <header class="version-header">
                    <div>
                        <p class="eyebrow">版本历史</p>
                        <h2>{{ artifactTitle }}</h2>
                        <span v-if="versions.length > 0">
                            共 {{ versions.length }} 个版本
                        </span>
                    </div>

                    <button class="close-button" @click="$emit('close')">
                        ×
                    </button>
                </header>

                <div v-if="loading" class="empty-box">
                    正在加载版本历史...
                </div>

                <div v-else-if="error" class="error-box">
                    {{ error }}
                </div>

                <div v-else class="version-body">
                    <section class="version-list">
                        <div class="version-list-header">
                            <h3>历史版本</h3>

                            <button class="secondary-button" @click="$emit('refresh')">
                                刷新
                            </button>
                        </div>

                        <div v-if="versions.length === 0" class="empty-inline">
                            当前资产暂无历史版本。
                        </div>

                        <button
                            v-for="version in versions"
                            :key="version.artifactId"
                            class="version-item"
                            :class="{
                                active: selectedVersion?.artifactId === version.artifactId,
                                current: version.current,
                            }"
                            @click="$emit('view-version', version.artifactId)"
                        >
                            <div>
                                <strong>v{{ version.versionNo }}</strong>

                                <span v-if="version.current" class="status-chip current-chip">
                                    当前版本
                                </span>

                                <span :class="['status-chip', statusClass(version.status)]">
                                    {{ statusText(version.status) }}
                                </span>
                            </div>

                            <span
                                v-if="version.promptTemplateKey || version.promptTemplateVersion"
                                class="template-meta"
                            >
                                模板：{{ version.promptTemplateKey || '-' }} /
                                {{ version.promptTemplateVersion || '-' }}
                            </span>

                            <p>{{ version.title || artifactTitle }}</p>

                            <small>
                                {{ version.createdBy || '未知用户' }}
                                ·
                                {{ formatTime(version.createdAt) }}
                            </small>
                        </button>
                    </section>

                    <section class="version-preview">
                        <div class="preview-header">
                            <div>
                                <h3>
                                    版本预览
                                    <span v-if="selectedVersion">
                                        v{{ selectedVersion.versionNo }}
                                    </span>
                                </h3>

                                <p v-if="selectedVersion">
                                    {{ selectedVersion.current ? '当前版本' : '历史版本' }}
                                    ·
                                    {{ formatTime(selectedVersion.createdAt) }}
                                </p>
                            </div>

                            <div v-if="selectedVersion" class="preview-actions">
                                <button
                                    v-if="canSubmitReview(selectedVersion)"
                                    class="secondary-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('submit-review', selectedVersion.artifactId)"
                                >
                                    提交确认
                                </button>

                                <button
                                    v-if="canApprove(selectedVersion)"
                                    class="primary-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('approve-version', selectedVersion.artifactId)"
                                >
                                    确认通过
                                </button>

                                <button
                                    v-if="canReject(selectedVersion)"
                                    class="danger-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('reject-version', selectedVersion.artifactId)"
                                >
                                    退回修改
                                </button>

                                <button
                                    v-if="selectedVersion && !selectedVersion.current"
                                    class="primary-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('rollback-version', selectedVersion.artifactId)"
                                >
                                    {{ rollbackLoading ? '处理中...' : '回滚为当前版本' }}
                                </button>

                                <button
                                    v-if="canArchive(selectedVersion)"
                                    class="secondary-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('archive-version', selectedVersion.artifactId)"
                                >
                                    归档
                                </button>

                                <button
                                    v-if="canSyncAffected(selectedVersion)"
                                    class="primary-button sync-button"
                                    :disabled="rollbackLoading"
                                    @click="$emit('sync-affected-version', selectedVersion.artifactId)"
                                >
                                    AI 局部同步修订
                                </button>

                                <button
                                    v-if="selectedVersion.current && !canSubmitReview(selectedVersion) && !canApprove(selectedVersion) && !canReject(selectedVersion) && !canSyncAffected(selectedVersion)"
                                    class="disabled-button"
                                    disabled
                                >
                                    已是当前版本
                                </button>
                            </div>
                        </div>

                        <div v-if="!selectedVersion" class="empty-inline">
                            请从左侧选择一个版本进行查看。
                        </div>

                        <div v-else class="preview-content">
                            <div class="meta-grid">
                                <label>
                                    版本号
                                    <span>v{{ selectedVersion.versionNo }}</span>
                                </label>

                                <label>
                                    状态
                                    <span>{{ statusText(selectedVersion.status) }}</span>
                                </label>

                                <label>
                                    创建人
                                    <span>{{ selectedVersion.createdBy || '-' }}</span>
                                </label>

                                <label>
                                    更新时间
                                    <span>{{ formatTime(selectedVersion.updatedAt) }}</span>
                                </label>
                            </div>

                            <div
                                v-if="isPendingReview(selectedVersion)"
                                class="review-hint pending-review"
                            >
                                <strong>当前版本待确认</strong>
                                <span>这是真正需要人工确认的新版本。确认无误后，点击右上角“确认通过”；不满意则点击“退回修改”。确认通过后，该资产才算进入稳定可交接状态。</span>
                            </div>

                            <div
                                v-else-if="isNeedsReview(selectedVersion)"
                                class="review-hint needs-review"
                            >
                                <strong>受上游影响</strong>
                                <span>这个状态不是“等待确认”的新版本，而是表示该资产可能落后于上游资产。优先点击右上角“AI 局部同步修订”，让 LangGraph Agent 根据上游变化生成局部修订提案；差异过大时再打开影响分析选择重生成。</span>
                            </div>

                            <section
                                v-if="selectedVersion.promptTemplateKey || selectedVersion.promptTemplateVersion"
                                class="detail-section"
                            >
                                <h3>Prompt 模板追溯</h3>

                                <dl class="meta-grid template-trace-grid">
                                    <label>
                                        模板 Key
                                        <span>{{ selectedVersion.promptTemplateKey || '-' }}</span>
                                    </label>

                                    <label>
                                        模板版本
                                        <span>{{ selectedVersion.promptTemplateVersion || '-' }}</span>
                                    </label>

                                    <label class="full-row">
                                        模板校验值
                                        <span class="checksum">
                                            {{ selectedVersion.promptTemplateChecksum || '-' }}
                                        </span>
                                    </label>
                                </dl>
                            </section>

                            <div
                                v-if="selectedVersion.contentMarkdown"
                                class="markdown-preview"
                                v-html="renderMarkdown(selectedVersion.contentMarkdown)"
                            ></div>

                            <pre v-else class="json-box">{{ prettyJson(selectedVersion.content) }}</pre>
                        </div>
                    </section>
                </div>
            </aside>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
import type {
    ArtifactVersionDetailResponse,
    ArtifactVersionSummaryResponse,
} from '../types/codemind'

defineProps<{
    visible: boolean
    loading?: boolean
    rollbackLoading?: boolean
    artifactTitle: string
    versions: ArtifactVersionSummaryResponse[]
    selectedVersion: ArtifactVersionDetailResponse | null
    error?: string
}>()

defineEmits<{
    close: []
    refresh: []
    'view-version': [artifactId: string]
    'rollback-version': [artifactId: string]
    'submit-review': [artifactId: string]
    'approve-version': [artifactId: string]
    'reject-version': [artifactId: string]
    'archive-version': [artifactId: string]
    'sync-affected-version': [artifactId: string]
}>()

function formatTime(value?: string | null): string {
    if (!value) {
        return '-'
    }

    return value.replace('T', ' ').slice(0, 19)
}

function prettyJson(value?: Record<string, unknown> | null): string {
    if (!value) {
        return '-'
    }

    try {
        return JSON.stringify(value, null, 2)
    } catch {
        return String(value)
    }
}

function statusClass(status?: string | null): string {
    if (status === 'ACTIVE') return 'status-active'
    if (status === 'DRAFT') return 'status-draft'
    if (status === 'PENDING_REVIEW') return 'status-review'
    if (status === 'NEEDS_REVIEW') return 'status-needs-review'
    if (status === 'APPROVED') return 'status-approved'
    if (status === 'REJECTED') return 'status-rejected'
    if (status === 'ARCHIVED') return 'status-archived'
    if (status === 'SUPERSEDED') return 'status-superseded'
    return 'status-default'
}

function renderMarkdown(markdown?: string | null): string {
    if (!markdown) {
        return '<p class="markdown-empty">暂无内容。</p>'
    }

    const lines = markdown.split(/\r?\n/)
    const html: string[] = []
    let inList = false

    for (const rawLine of lines) {
        const line = rawLine.trim()

        if (!line) {
            if (inList) {
                html.push('</ul>')
                inList = false
            }

            continue
        }

        if (line.startsWith('### ')) {
            if (inList) {
                html.push('</ul>')
                inList = false
            }

            html.push(`<h3>${formatInlineMarkdown(line.slice(4))}</h3>`)
            continue
        }

        if (line.startsWith('## ')) {
            if (inList) {
                html.push('</ul>')
                inList = false
            }

            html.push(`<h2>${formatInlineMarkdown(line.slice(3))}</h2>`)
            continue
        }

        if (line.startsWith('# ')) {
            if (inList) {
                html.push('</ul>')
                inList = false
            }

            html.push(`<h1>${formatInlineMarkdown(line.slice(2))}</h1>`)
            continue
        }

        if (line.startsWith('- ') || line.startsWith('* ')) {
            if (!inList) {
                html.push('<ul>')
                inList = true
            }

            html.push(`<li>${formatInlineMarkdown(line.slice(2))}</li>`)
            continue
        }

        if (/^\d+\.\s+/.test(line)) {
            if (inList) {
                html.push('</ul>')
                inList = false
            }

            html.push(`<p class="numbered-line">${formatInlineMarkdown(line)}</p>`)
            continue
        }

        if (inList) {
            html.push('</ul>')
            inList = false
        }

        html.push(`<p>${formatInlineMarkdown(line)}</p>`)
    }

    if (inList) {
        html.push('</ul>')
    }

    return html.join('')
}

function formatInlineMarkdown(value: string): string {
    return escapeHtml(value)
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/`([^`]+)`/g, '<code>$1</code>')
}

function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;')
}

function statusText(status?: string | null): string {
    if (status === 'ACTIVE') return '未确认'
    if (status === 'DRAFT') return '草稿未确认'
    if (status === 'PENDING_REVIEW') return '新版本待确认'
    if (status === 'NEEDS_REVIEW') return '受上游影响'
    if (status === 'APPROVED') return '已确认'
    if (status === 'REJECTED') return '已退回'
    if (status === 'ARCHIVED') return '已归档'
    if (status === 'SUPERSEDED') return '已被替代'
    return status || '-'
}

function isPendingReview(
    version: ArtifactVersionDetailResponse | null,
): boolean {
    return Boolean(version?.current && version.status === 'PENDING_REVIEW')
}

function isNeedsReview(
    version: ArtifactVersionDetailResponse | null,
): boolean {
    return Boolean(version?.current && version.status === 'NEEDS_REVIEW')
}

function canSyncAffected(
    version: ArtifactVersionDetailResponse | null,
): boolean {
    return Boolean(version?.current && version.status === 'NEEDS_REVIEW')
}

function canSubmitReview(
    version: ArtifactVersionDetailResponse,
): boolean {
    return Boolean(
        version.current &&
        ['ACTIVE', 'DRAFT', 'REJECTED'].includes(version.status || 'ACTIVE'),
    )
}

function canApprove(
    version: ArtifactVersionDetailResponse,
): boolean {
    return Boolean(
        version.current &&
        version.status === 'PENDING_REVIEW',
    )
}

function canReject(
    version: ArtifactVersionDetailResponse,
): boolean {
    return Boolean(
        version.current &&
        version.status === 'PENDING_REVIEW',
    )
}

function canArchive(
    version: ArtifactVersionDetailResponse,
): boolean {
    return Boolean(
        !version.current &&
        version.status !== 'ARCHIVED',
    )
}
</script>

<style scoped>
.version-mask {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: flex;
    justify-content: flex-end;
    background: rgba(15, 23, 42, 0.36);
}

.version-drawer {
    width: min(980px, 96vw);
    height: 100vh;
    overflow: hidden;
    background: #ffffff;
    box-shadow: -20px 0 60px rgba(15, 23, 42, 0.18);
    display: flex;
    flex-direction: column;
}

.version-header {
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

.version-header h2 {
    margin: 0 0 8px;
    color: #0f172a;
    font-size: 20px;
}

.version-header span {
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

.version-body {
    min-height: 0;
    flex: 1;
    display: grid;
    grid-template-columns: 280px minmax(0, 1fr);
    gap: 0;
}

.version-list {
    min-height: 0;
    overflow-y: auto;
    border-right: 1px solid #e2e8f0;
    padding: 16px;
    background: #f8fafc;
}

.version-list-header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    margin-bottom: 12px;
}

.version-list-header h3 {
    margin: 0;
    color: #0f172a;
    font-size: 16px;
}

.version-item {
    width: 100%;
    display: grid;
    gap: 6px;
    margin-bottom: 10px;
    border: 1px solid #e2e8f0;
    border-radius: 14px;
    padding: 12px;
    background: #ffffff;
    cursor: pointer;
    text-align: left;
}

.version-item:hover {
    border-color: #2563eb;
}

.version-item.active {
    border-color: #2563eb;
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.version-item.current {
    background: #eff6ff;
    border-color: #93c5fd;
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.version-item div {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    align-items: center;
}

.version-item strong {
    color: #0f172a;
}

.version-item p {
    margin: 0;
    color: #334155;
    font-size: 13px;
}

.version-item small {
    color: #64748b;
    font-size: 12px;
}

.version-preview {
    min-height: 0;
    overflow-y: auto;
    padding: 18px 24px 28px;
    background: #ffffff;
}

.preview-header {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    align-items: flex-start;
    margin-bottom: 16px;
}

.preview-header h3 {
    margin: 0 0 6px;
    color: #0f172a;
    font-size: 18px;
}

.preview-header h3 span {
    color: #2563eb;
}

.preview-header p {
    margin: 0;
    color: #64748b;
    font-size: 13px;
}

.meta-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 16px;
}

.meta-grid label {
    display: grid;
    gap: 6px;
    color: #64748b;
    font-size: 12px;
}

.meta-grid span {
    color: #0f172a;
    font-size: 13px;
    overflow-wrap: anywhere;
}

.json-box {
    min-height: 360px;
    max-height: 62vh;
    overflow: auto;
    margin: 0;
    border-radius: 14px;
    padding: 14px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font-size: 13px;
    line-height: 1.7;
    background: #0f172a;
    color: #e2e8f0;
}

.markdown-preview {
    min-height: 360px;
    max-height: 62vh;
    overflow: auto;
    border: 1px solid #e2e8f0;
    border-radius: 14px;
    padding: 22px 24px;
    background: #ffffff;
    color: #0f172a;
    line-height: 1.85;
    font-size: 14px;
}

.markdown-preview :deep(h1) {
    margin: 0 0 18px;
    padding-bottom: 12px;
    border-bottom: 1px solid #e2e8f0;
    color: #0f172a;
    font-size: 22px;
    line-height: 1.35;
}

.markdown-preview :deep(h2) {
    margin: 24px 0 12px;
    color: #0f172a;
    font-size: 18px;
    line-height: 1.4;
}

.markdown-preview :deep(h3) {
    margin: 18px 0 10px;
    color: #1e293b;
    font-size: 15px;
    line-height: 1.4;
}

.markdown-preview :deep(p) {
    margin: 8px 0;
    color: #334155;
}

.markdown-preview :deep(ul) {
    margin: 8px 0 14px;
    padding-left: 22px;
}

.markdown-preview :deep(li) {
    margin: 6px 0;
    color: #334155;
}

.markdown-preview :deep(strong) {
    color: #0f172a;
    font-weight: 700;
}

.markdown-preview :deep(code) {
    border-radius: 6px;
    padding: 2px 5px;
    background: #f1f5f9;
    color: #0f172a;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
}

.markdown-preview :deep(.numbered-line) {
    margin-top: 12px;
    font-weight: 600;
    color: #0f172a;
}

.markdown-empty {
    color: #64748b;
}

.json-box {
    background: #0f172a;
    color: #e2e8f0;
}

.review-hint {
    display: grid;
    gap: 6px;
    margin: 0 0 16px;
    border-radius: 14px;
    padding: 12px 14px;
    font-size: 13px;
    line-height: 1.65;
}

.review-hint strong {
    color: #0f172a;
}

.review-hint span {
    color: #475569;
}

.review-hint.pending-review {
    border: 1px solid #fde68a;
    background: #fffbeb;
}

.review-hint.needs-review {
    border: 1px solid #fed7aa;
    background: #fff7ed;
}

.primary-button,
.secondary-button,
.disabled-button {
    border-radius: 10px;
    padding: 8px 12px;
    white-space: nowrap;
}

.primary-button {
    border: 1px solid #2563eb;
    background: #2563eb;
    color: #ffffff;
    cursor: pointer;
}

.secondary-button {
    border: 1px solid #cbd5e1;
    background: #ffffff;
    color: #334155;
    cursor: pointer;
}

.disabled-button {
    border: 1px solid #cbd5e1;
    background: #f8fafc;
    color: #94a3b8;
    cursor: not-allowed;
}

.primary-button:hover:not(:disabled) {
    background: #1d4ed8;
}

.secondary-button:hover {
    border-color: #2563eb;
    color: #2563eb;
}

.primary-button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.sync-button {
    box-shadow: 0 8px 20px rgba(37, 99, 235, 0.18);
}

.empty-box,
.error-box {
    margin: 20px 24px;
    border-radius: 14px;
    padding: 24px;
    text-align: center;
}

.empty-box,
.empty-inline {
    color: #64748b;
    background: #f8fafc;
}

.empty-inline {
    border-radius: 12px;
    padding: 18px;
    text-align: center;
}

.error-box {
    color: #991b1b;
    background: #fef2f2;
    border: 1px solid #fecaca;
}

@media (max-width: 860px) {
    .version-body {
        grid-template-columns: 1fr;
    }

    .version-list {
        max-height: 260px;
        border-right: none;
        border-bottom: 1px solid #e2e8f0;
    }

    .meta-grid {
        grid-template-columns: 1fr;
    }

    .preview-header {
        flex-direction: column;
    }
}

.preview-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
}

.danger-button {
    border: 1px solid #dc2626;
    border-radius: 10px;
    padding: 8px 12px;
    background: #fef2f2;
    color: #b91c1c;
    cursor: pointer;
    white-space: nowrap;
}

.danger-button:hover:not(:disabled) {
    background: #fee2e2;
}

.danger-button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.status-chip {
    border-radius: 999px;
    padding: 2px 8px;
    font-size: 12px;
    white-space: nowrap;
}

.current-chip {
    background: #dbeafe;
    color: #1d4ed8;
}

.status-active,
.status-draft {
    background: #f1f5f9;
    color: #475569;
}

.status-review {
    background: #fef3c7;
    color: #92400e;
}

.status-needs-review {
    background: #fffbeb;
    color: #b45309;
}

.status-approved {
    background: #dcfce7;
    color: #166534;
}

.status-rejected {
    background: #fee2e2;
    color: #991b1b;
}

.status-archived,
.status-superseded {
    background: #e5e7eb;
    color: #4b5563;
}

.status-default {
    background: #f1f5f9;
    color: #475569;
}

.template-meta {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.5;
    color: #64748b;
}

.detail-section {
    margin-top: 16px;
    padding: 14px 16px;
    border: 1px solid #e2e8f0;
    border-radius: 14px;
    background: #f8fafc;
}

.detail-section h3 {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 700;
    color: #0f172a;
}

.template-trace-grid {
    margin: 0;
}

.template-trace-grid .full-row {
    grid-column: 1 / -1;
}

.checksum {
    font-family: Consolas, Monaco, monospace;
    word-break: break-all;
}
</style>




