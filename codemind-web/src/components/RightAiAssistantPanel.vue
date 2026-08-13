<template>
  <button v-if="collapsed" class="assistant-float-button" @click="$emit('update:collapsed', false)">
    AI
  </button>

  <aside v-else class="ai-assistant-panel" :style="{ width: `${panelWidth}px` }">
    <div class="resize-handle" @mousedown="startResize" />

    <header class="assistant-header">
      <div>
        <h3>AI Assistant</h3>
        <p>
          {{
            moduleTreeExists
              ? '可以修改模块树、生成提案、解释资产和辅助需求分析。'
              : '请先输入业务需求，生成结构化模块树。'
          }}
        </p>
      </div>

      <div class="assistant-header-actions">
        <button class="clear-conversation-button" title="清空当前对话视图" :disabled="isBusy"
          @click="$emit('clear-conversation')">
          清空对话
        </button>

        <button class="collapse-button" title="收起" @click="$emit('update:collapsed', true)">
          ×
        </button>
      </div>
    </header>

    <section ref="messageListRef" class="assistant-message-list">
      <article v-for="message in visibleMessages" :key="message.id" class="assistant-message" :class="message.role">
        <div class="message-avatar">
          {{ message.role === 'user' ? '我' : 'AI' }}
        </div>

        <div class="message-content">
          <div class="message-meta">
            <strong>
              {{ message.role === 'user' ? '你' : 'AI Assistant' }}
            </strong>
            <span>{{ message.createdAt }}</span>
          </div>

          <p v-if="message.text" class="message-text">
            {{ message.text }}
          </p>

          <div v-if="message.quickReplies?.length" class="quick-reply-actions">
            <button
              v-for="reply in message.quickReplies"
              :key="reply.text"
              type="button"
              :disabled="isBusy"
              @click="sendQuickReply(reply.text, reply.label)"
            >
              {{ reply.label }}
            </button>
          </div>

          <div v-if="message.type === 'TREE_PATCH_PROPOSAL' && message.proposal" class="proposal-card">
            <div class="proposal-title-row">
              <div>
                <strong>模块树变更提案</strong>
                <p>
                  {{
                    message.proposal?.description ||
                    message.proposal?.proposal?.description ||
                    'AI 已生成模块树变更提案。'
                  }}
                </p>
              </div>

              <span>
                {{
                  message.proposal?.source ||
                  message.proposal?.proposal?.source ||
                  'AI_TREE_PATCH'
                }}
              </span>
            </div>

            <div class="proposal-section">
              <h4>操作列表</h4>

              <ul v-if="message.proposal?.proposal?.operations?.length">
                <li v-for="(operation, index) in message.proposal.proposal.operations" :key="index">
                  <span class="operation-op">{{ operation.op }}</span>
                  <span class="operation-main">
                    {{ formatOperation(operation) }}
                  </span>
                </li>
              </ul>

              <p v-else class="empty-tip">
                AI 没有生成可执行操作，请调整描述后重试。
              </p>
            </div>

            <div v-if="message.proposal?.proposal?.impact" class="proposal-section">
              <h4>影响分析</h4>
              <p>
                {{ message.proposal.proposal.impact.summary || '暂无影响摘要。' }}
              </p>

              <ul v-if="message.proposal.proposal.impact.affectedArtifacts?.length">
                <li v-for="artifact in message.proposal.proposal.impact.affectedArtifacts" :key="artifact">
                  {{ artifact }}
                </li>
              </ul>
            </div>

            <div v-if="message.proposal?.proposal?.impact?.riskNotes?.length" class="proposal-section warning">
              <h4>风险提示</h4>
              <ul>
                <li v-for="note in message.proposal.proposal.impact.riskNotes" :key="note">
                  {{ note }}
                </li>
              </ul>
            </div>

            <footer class="proposal-actions">
              <button class="secondary-button" :disabled="isApplyingTreePatch" @click="clearProposal(message)">
                放弃提案
              </button>

              <button class="primary-button"
                :disabled="isApplyingTreePatch || !canApplyProposalFromTreePatchCard(message.proposal)"
                @click="handleApplyProposal(message.proposal)">
                {{ isApplyingTreePatch ? '应用中...' : formatTreePatchApplyButtonLabel(message.proposal) }}
              </button>
            </footer>
          </div>


          <div v-if="message.type === 'REQUIREMENT_CHANGE_PROPOSAL' && message.requirementChangeProposal"
            class="proposal-card requirement-change-card">
            <div class="proposal-title-row">
              <div>
                <strong>需求变更草案</strong>
                <p>
                  {{
                    message.requirementChangeProposal.summary ||
                    'AI 已根据你的描述形成一份需求变更草案。'
                  }}
                </p>
              </div>

              <span>
                {{ message.requirementChangeProposal.changeType || 'CHANGE' }}
              </span>
            </div>

            <div class="proposal-section">
              <h4>变更范围</h4>
              <p>
                {{ formatRequirementChangeTarget(message.requirementChangeProposal) }}
              </p>

              <ul>
                <li>
                  范围类型：
                  <span class="operation-main">
                    {{ message.requirementChangeProposal.scopeType || 'GENERAL_REQUIREMENT' }}
                  </span>
                </li>
                <li>
                  当前策略：
                  <span class="operation-main">
                    {{ message.requirementChangeProposal.executionPolicy || '仅生成草案，暂不执行。' }}
                  </span>
                </li>
              </ul>
            </div>

            <div v-if="message.requirementChangeProposal.affectedArtifacts?.length" class="proposal-section">
              <h4>预计影响资产</h4>
              <ul>
                <li v-for="artifact in message.requirementChangeProposal.affectedArtifacts" :key="artifact">
                  {{ artifact }}
                </li>
              </ul>
            </div>

            <div v-if="message.requirementChangeProposal.riskNotes?.length" class="proposal-section warning">
              <h4>风险提示</h4>
              <ul>
                <li v-for="note in message.requirementChangeProposal.riskNotes" :key="note">
                  {{ note }}
                </li>
              </ul>
            </div>

            <div v-if="message.requirementChangeProposal.recommendedSteps?.length" class="proposal-section">
              <h4>后续步骤</h4>
              <ol>
                <li v-for="step in message.requirementChangeProposal.recommendedSteps" :key="step">
                  {{ step }}
                </li>
              </ol>
            </div>

            <footer class="proposal-actions">
              <button class="secondary-button" :disabled="isBusy" title="放弃当前草案。对不满意的部分，可以直接在输入框继续说明，AI 会基于上下文重新生成方案。"
                @click="clearRequirementChangeProposal(message)">
                放弃并继续说明
              </button>

              <button class="primary-button" :disabled="isBusy" title="确认该需求变更草案，并交给当前阶段可用的执行链路处理。"
                @click="handleConfirmRequirementChange(message.requirementChangeProposal)">
                确认执行
              </button>
            </footer>
          </div>

          <div v-if="message.type === 'ASSET_EDIT_PROPOSAL' && message.assetEditProposal"
            class="proposal-card asset-edit-card">
            <div class="proposal-title-row">
              <div>
                <strong>资产编辑提案</strong>
                <p>
                  {{ message.assetEditProposal.summary || 'AI 已生成局部修订方案。请确认修改点，保存后会形成新的待确认版本。' }}
                </p>
              </div>

              <span>
                {{ formatArtifactTypeLabel(message.assetEditProposal.targetArtifactType ||
                  message.assetEditProposal.targetArtifact?.artifactType || 'ASSET') }}
              </span>
            </div>

            <div class="asset-edit-flow-hint">
              <strong>执行结果</strong>
              <span>保存时只应用局部 Patch，并形成新的待确认版本；旧版本保留，不会触发整份重新生成任务。</span>
            </div>

            <div class="proposal-section">
              <h4>目标资产</h4>
              <p>{{ formatAssetEditTarget(message.assetEditProposal) }}</p>
              <ul>
                <li>编辑模式：<span class="operation-main">{{ formatAssetEditMode(message.assetEditProposal.editMode)
                    }}</span></li>
                <li>编辑策略：<span class="operation-main">{{ formatAssetEditPolicy(message.assetEditProposal.editPolicy)
                    }}</span></li>
              </ul>
            </div>

            <div v-if="getAssetEditPatchOperations(message.assetEditProposal).length"
              class="proposal-section patch-ops-section">
              <h4>局部 Patch 操作</h4>
              <p class="section-tip">Agent 只输出需要修改的片段，后端负责应用到当前资产全文并保存完整版本快照。</p>
              <ul>
                <li v-for="(operation, index) in getAssetEditPatchOperations(message.assetEditProposal)" :key="index">
                  <strong>{{ formatPatchOperationTitle(operation, index) }}</strong>
                  <p v-if="formatPatchOperationText(operation)">{{ formatPatchOperationText(operation) }}</p>
                </li>
              </ul>
            </div>

            <div v-if="message.assetEditProposal.proposedPatchSummary?.length" class="proposal-section">
              <h4>拟局部修订内容</h4>
              <ul>
                <li v-for="item in message.assetEditProposal.proposedPatchSummary" :key="item">
                  {{ item }}
                </li>
              </ul>
            </div>

            <div v-if="message.assetEditProposal.downstreamArtifacts?.length"
              class="proposal-section downstream-section">
              <h4>确认后可能受影响的下游资产</h4>
              <p class="section-tip">这些资产不是必须立即处理；可以稍后在影响分析中逐个使用 AI 局部同步修订。</p>
              <ul>
                <li v-for="artifact in message.assetEditProposal.downstreamArtifacts" :key="artifact">
                  {{ formatArtifactTypeLabel(artifact) }}
                </li>
              </ul>
            </div>

            <div v-if="message.assetEditProposal.riskNotes?.length" class="proposal-section warning">
              <h4>风险提示</h4>
              <ul>
                <li v-for="note in message.assetEditProposal.riskNotes" :key="note">
                  {{ note }}
                </li>
              </ul>
            </div>

            <footer class="proposal-actions">
              <button class="secondary-button" :disabled="isBusy" @click="clearAssetEditProposal(message)">
                放弃并继续说明
              </button>

              <button class="primary-button" :disabled="isBusy || !message.assetEditProposal.proposedContentMarkdown"
                title="确认本次局部修订，并保存为新的待确认版本。旧版本保留。" @click="handleConfirmAssetEditProposal(message.assetEditProposal)">
                确认并保存局部修订版本
              </button>
            </footer>
          </div>


          <div v-if="message.type === 'ATTACHMENT_CHANGE_PROPOSAL' && message.attachmentDrivenChangeProposal"
            class="proposal-card attachment-change-card">
            <div class="proposal-title-row">
              <div>
                <strong>附件驱动变更分析</strong>
                <p>
                  {{
                    message.attachmentDrivenChangeProposal.summary ||
                    'AI 已根据附件内容形成一份可审查的需求变更入口。'
                  }}
                </p>
              </div>

              <span>
                {{ message.attachmentDrivenChangeProposal.recommendedDispatch || 'ATTACHMENT' }}
              </span>
            </div>

            <div v-if="message.attachmentDrivenChangeProposal.attachmentSummary?.items?.length"
              class="proposal-section">
              <h4>附件材料</h4>
              <ul>
                <li v-for="(item, index) in message.attachmentDrivenChangeProposal.attachmentSummary.items"
                  :key="String(item.attachmentId || item.fileName || index)">
                  {{ formatAttachmentSummaryItem(item) }}
                </li>
              </ul>
            </div>

            <div v-if="message.attachmentDrivenChangeProposal.materialInstruction" class="proposal-section">
              <h4>材料理解摘要</h4>
              <p>
                {{ trimProposalPreview(message.attachmentDrivenChangeProposal.materialInstruction) }}
              </p>
            </div>

            <div v-if="message.attachmentDrivenChangeProposal.affectedArtifacts?.length" class="proposal-section">
              <h4>可能影响资产</h4>
              <ul>
                <li v-for="artifact in message.attachmentDrivenChangeProposal.affectedArtifacts" :key="artifact">
                  {{ formatArtifactTypeLabel(artifact) }}
                </li>
              </ul>
            </div>

            <div v-if="message.attachmentDrivenChangeProposal.riskNotes?.length" class="proposal-section warning">
              <h4>风险提示</h4>
              <ul>
                <li v-for="note in message.attachmentDrivenChangeProposal.riskNotes" :key="note">
                  {{ note }}
                </li>
              </ul>
            </div>

            <div v-if="message.attachmentDrivenChangeProposal.recommendedSteps?.length" class="proposal-section">
              <h4>建议步骤</h4>
              <ol>
                <li v-for="step in message.attachmentDrivenChangeProposal.recommendedSteps" :key="step">
                  {{ step }}
                </li>
              </ol>
            </div>

            <footer class="proposal-actions attachment-actions">
              <button v-for="action in getAttachmentChangeCandidateActions(message.attachmentDrivenChangeProposal)"
                :key="String(action.type || action.label)" class="primary-button"
                :class="{ 'secondary-button': normalizeAttachmentActionType(action) === 'ANALYSIS_ONLY' }"
                :disabled="isBusy" :title="action.description || ''"
                @click="handleExecuteAttachmentChange(message.attachmentDrivenChangeProposal, action)">
                {{ formatAttachmentChangeActionLabel(action) }}
              </button>

              <button class="secondary-button" :disabled="isBusy" @click="clearAttachmentDrivenChangeProposal(message)">
                稍后处理
              </button>
            </footer>
          </div>

          <div
            v-if="message.type === 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL' && message.attachmentArtifactDiffProposal"
            class="proposal-card attachment-diff-card"
          >
            <div class="proposal-title-row">
              <div>
                <strong>附件与当前资产差异分析</strong>
                <p>
                  {{
                    message.attachmentArtifactDiffProposal.summary ||
                    '已完成附件材料与当前需求资产的差异分析。'
                  }}
                </p>
              </div>

              <span>
                {{
                  formatArtifactTypeLabel(
                    message.attachmentArtifactDiffProposal.targetArtifactType ||
                    message.attachmentArtifactDiffProposal.targetArtifact?.artifactType ||
                    'ASSET',
                  )
                }}
              </span>
            </div>

            <div class="asset-edit-flow-hint attachment-diff-flow-hint">
              <strong>安全边界</strong>
              <span>当前卡片只展示差异，不会直接覆盖资产或修改模块树。继续操作后仍会先生成可审查提案。</span>
            </div>

            <div class="proposal-section">
              <h4>对比目标</h4>
              <p>{{ formatAttachmentDiffTarget(message.attachmentArtifactDiffProposal) }}</p>
              <ul>
                <li v-if="message.attachmentArtifactDiffProposal.confidence !== null && message.attachmentArtifactDiffProposal.confidence !== undefined">
                  分析置信度：
                  <span class="operation-main">
                    {{ formatAttachmentDiffConfidence(message.attachmentArtifactDiffProposal.confidence) }}
                  </span>
                </li>
                <li>
                  处理策略：
                  <span class="operation-main">先审查差异，再生成局部 Patch 或模块树提案</span>
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.attachmentSummary?.items?.length"
              class="proposal-section"
            >
              <h4>参与对比的附件</h4>
              <ul>
                <li
                  v-for="(item, index) in message.attachmentArtifactDiffProposal.attachmentSummary.items"
                  :key="String(item.attachmentId || item.fileName || index)"
                >
                  {{ formatAttachmentSummaryItem(item) }}
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.diffItems?.length"
              class="proposal-section"
            >
              <div class="attachment-diff-section-header">
                <div>
                  <h4>差异清单</h4>
                  <p class="section-tip">请选择要进入后续修订流程的差异项；未选中的差异只会保留在本次分析中。</p>
                </div>

                <div class="attachment-diff-selection-summary">
                  已选择
                  <strong>{{ getSelectedAttachmentDiffCount(message) }}</strong>
                  /
                  {{ getAttachmentDiffItemCount(message) }}
                  项
                </div>
              </div>

              <div class="attachment-diff-selection-actions">
                <button
                  type="button"
                  class="link-button"
                  :disabled="isBusy || getAttachmentDiffItemCount(message) === 0"
                  @click="selectAllAttachmentDiffItems(message)"
                >
                  全选
                </button>
                <button
                  type="button"
                  class="link-button"
                  :disabled="isBusy || getSelectedAttachmentDiffCount(message) === 0"
                  @click="clearAttachmentDiffSelection(message)"
                >
                  取消全选
                </button>
              </div>

              <ol class="attachment-diff-list">
                <li
                  v-for="(item, index) in message.attachmentArtifactDiffProposal.diffItems"
                  :key="getAttachmentDiffItemId(item, index)"
                  class="attachment-diff-item"
                  :class="{ selected: isAttachmentDiffItemSelected(message, item, index) }"
                >
                  <label class="attachment-diff-select-row">
                    <input
                      type="checkbox"
                      :checked="isAttachmentDiffItemSelected(message, item, index)"
                      :disabled="isBusy"
                      @change="toggleAttachmentDiffItem(message, item, index)"
                    >
                    <strong>{{ formatAttachmentDiffItemTitle(item, index) }}</strong>
                  </label>

                  <p v-if="item.attachmentExcerpt">
                    <span class="diff-field-label">附件依据：</span>
                    {{ trimProposalPreview(item.attachmentExcerpt) }}
                  </p>

                  <p v-if="item.currentEvidence">
                    <span class="diff-field-label">当前资产证据：</span>
                    {{ trimProposalPreview(item.currentEvidence) }}
                  </p>

                  <p v-if="item.recommendation">
                    <span class="diff-field-label">建议：</span>
                    {{ item.recommendation }}
                  </p>
                </li>
              </ol>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.missingRequirements?.length"
              class="proposal-section"
            >
              <h4>当前资产可能缺失</h4>
              <ul>
                <li
                  v-for="item in message.attachmentArtifactDiffProposal.missingRequirements"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.possibleConflicts?.length"
              class="proposal-section warning"
            >
              <h4>可能冲突</h4>
              <ul>
                <li
                  v-for="item in message.attachmentArtifactDiffProposal.possibleConflicts"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.alreadyCoveredItems?.length"
              class="proposal-section"
            >
              <h4>当前资产已覆盖</h4>
              <ul>
                <li
                  v-for="item in message.attachmentArtifactDiffProposal.alreadyCoveredItems"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.riskNotes?.length"
              class="proposal-section warning"
            >
              <h4>风险提示</h4>
              <ul>
                <li
                  v-for="note in message.attachmentArtifactDiffProposal.riskNotes"
                  :key="note"
                >
                  {{ note }}
                </li>
              </ul>
            </div>

            <div
              v-if="message.attachmentArtifactDiffProposal.recommendedSteps?.length"
              class="proposal-section"
            >
              <h4>建议步骤</h4>
              <ol>
                <li
                  v-for="step in message.attachmentArtifactDiffProposal.recommendedSteps"
                  :key="step"
                >
                  {{ step }}
                </li>
              </ol>
            </div>

            <footer class="proposal-actions attachment-actions">
              <button
                v-for="action in getAttachmentDiffCandidateActions(message.attachmentArtifactDiffProposal)"
                :key="String(action.type || action.label)"
                class="primary-button"
                :class="{ 'secondary-button': normalizeAttachmentDiffActionType(action) === 'ANALYSIS_ONLY' }"
                :disabled="isBusy || getSelectedAttachmentDiffCount(message) === 0"
                :title="getAttachmentDiffActionButtonTitle(message, action)"
                @click="handleExecuteAttachmentDiffAction(
                  message.attachmentArtifactDiffProposal,
                  action,
                  getSelectedAttachmentDiffItemIds(message),
                )"
              >
                {{ formatAttachmentDiffActionLabel(action) }}
              </button>

              <button
                class="secondary-button"
                :disabled="isBusy"
                @click="clearAttachmentArtifactDiffProposal(message)"
              >
                稍后处理
              </button>
            </footer>
          </div>

          <div v-if="message.attachments?.length" class="message-attachments">
            <span v-for="attachment in message.attachments" :key="attachment.localId" class="attachment-chip"
              :class="attachmentStatusClass(attachment)" :title="formatAttachmentTitle(attachment)">
              {{ attachment.fileName }}
              <em>{{ attachmentParseLabel(attachment) }}</em>
            </span>
          </div>
        </div>
      </article>

      <article v-if="isGeneratingTree || isSendingMessage || isApplyingTreePatch"
        class="assistant-message assistant pending">
        <div class="message-avatar">AI</div>

        <div class="message-content">
          <div class="message-meta">
            <strong>AI Assistant</strong>
            <span>处理中</span>
          </div>

          <p class="message-text">
            {{ pendingText }}
          </p>
        </div>
      </article>
    </section>

    <footer class="assistant-input-area">
      <div v-if="pendingAttachments.length" class="pending-attachments">
        <span v-for="attachment in pendingAttachments" :key="attachment.localId" class="attachment-chip"
          :class="attachmentStatusClass(attachment)"
          :title="attachment.errorMessage || formatAttachmentTitle(attachment)">
          {{ attachment.fileName }}
          <em>{{ attachmentParseLabel(attachment) }}</em>
          <button title="移除" @click="removeAttachment(attachment.localId)">
            ×
          </button>
        </span>
      </div>

      <textarea v-model="inputText" class="assistant-input" :placeholder="inputPlaceholder"
        @keydown="handleInputKeydown" />

      <div class="assistant-input-footer">
        <button class="upload-button" :disabled="isBusy" title="上传 Word、PDF、Excel、图片等附件" @click="handleUploadClick">
          ＋ 附件
        </button>

        <input ref="fileInputRef" class="hidden-file-input" type="file" multiple
          accept=".txt,.md,.json,.pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.webp" @change="handleFileChange" />

        <span class="input-hint">
          Enter 发送，Ctrl + Enter 换行
        </span>

        <button class="send-button" :disabled="submitDisabled" @click="submit">
          {{ buttonText }}
        </button>
      </div>
    </footer>
  </aside>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue'
import type {
  AssistantAttachmentRef,
  AssistantConversationMessageResponse,
  AssistantMessageResponse,
  AttachmentDrivenChangeAction,
  AttachmentDrivenChangeProposal,
  AttachmentArtifactDiffAction,
  AttachmentArtifactDiffItem,
  AttachmentArtifactDiffProposal,
  ModuleTreePatchOperationRequest,
  ModuleTreePatchProposalResponse,
  RequirementChangeProposal,
  AssetEditProposal,
} from '../types/codemind'

type AssistantMessageRole = 'system' | 'user' | 'assistant'

type AssistantMessageType =
  | 'TEXT'
  | 'TREE_PATCH_PROPOSAL'
  | 'REQUIREMENT_CHANGE_PROPOSAL'
  | 'ASSET_EDIT_PROPOSAL'
  | 'ATTACHMENT_CHANGE_PROPOSAL'
  | 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL'

type AssistantAttachmentUploadStatus =
  | 'UPLOADING'
  | 'READY'
  | 'FAILED'

interface AssistantAttachmentDraft extends AssistantAttachmentRef {
  localId: string
  fileName: string
  mimeType: string
  size: number
  parseStatus?: string | null
  parsedTextExcerpt?: string | null
  metadata?: Record<string, unknown>
  uploadStatus: AssistantAttachmentUploadStatus
  errorMessage?: string
}

interface AssistantQuickReply {
  label: string
  text: string
}

interface AssistantMessage {
  id: string
  role: AssistantMessageRole
  type: AssistantMessageType
  text: string
  createdAt: string
  quickReplies?: AssistantQuickReply[]
  attachments?: AssistantAttachmentDraft[]
  proposal?: ModuleTreePatchProposalResponse | null
  requirementChangeProposal?: RequirementChangeProposal | null
  assetEditProposal?: AssetEditProposal | null
  attachmentDrivenChangeProposal?: AttachmentDrivenChangeProposal | null
  attachmentArtifactDiffProposal?: AttachmentArtifactDiffProposal | null
}

const props = defineProps<{
  collapsed: boolean
  moduleTreeExists: boolean
  isGeneratingTree: boolean
  isSendingMessage: boolean
  isApplyingTreePatch: boolean
  assistantResponse: AssistantMessageResponse | null
  historyMessages: AssistantConversationMessageResponse[]
  uploadAttachment: (file: File) => Promise<AssistantAttachmentRef>
}>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  send: [payload: {
    text: string
    attachments: AssistantAttachmentRef[]
  }]
  'apply-proposal': [proposal: ModuleTreePatchProposalResponse]
  'confirm-requirement-change': [proposal: RequirementChangeProposal]
  'confirm-asset-edit-proposal': [proposal: AssetEditProposal]
  'execute-attachment-change': [
    proposal: AttachmentDrivenChangeProposal,
    action: AttachmentDrivenChangeAction
  ]
  'execute-attachment-diff-action': [
    proposal: AttachmentArtifactDiffProposal,
    action: AttachmentArtifactDiffAction,
    selectedDiffItemIds: string[]
  ]
  'clear-proposal': []
  'clear-conversation': []
}>()

const inputText = ref('')
const panelWidth = ref(380)
const resizing = ref(false)
const startX = ref(0)
const startWidth = ref(380)

const fileInputRef = ref<HTMLInputElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const pendingAttachments = ref<AssistantAttachmentDraft[]>([])
const attachmentDiffSelections = ref<Record<string, string[]>>({})

const messages = ref<AssistantMessage[]>(createDefaultMessages())

watch(
  messages,
  (items) => {
    for (const message of items) {
      if (message.role !== 'assistant' || typeof message.text !== 'string') {
        continue
      }

      const nextText = sanitizeAssistantVisibleText(message.text)

      if (nextText !== message.text) {
        message.text = nextText
      }
    }
  },
  { deep: true },
)


const visibleMessages = computed(() => messages.value)

const isBusy = computed(() => {
  return props.isGeneratingTree ||
    props.isSendingMessage ||
    props.isApplyingTreePatch
})


function sanitizeAssistantVisibleText(value: string): string {
  const text = String(value || '').trim()

  if (!text) {
    return text
  }

  const upper = text.toUpperCase()

  if (upper === 'UNAUTHORIZED' || upper === 'HTTP 401' || upper === '401') {
    return '登录状态已过期，请重新登录后再继续。'
  }

  return text
    .replaceAll('MODULE → FUNCTION → PAGE', '模块 → 功能点 → 页面')
    .replaceAll('PAGE_SPEC / UI_CONTRACT', '交互原型')
    .replaceAll('PAGE_SPEC', '页面准备信息')
    .replaceAll('UI_CONTRACT', '交互原型')
}

const inputPlaceholder = computed(() => {
  if (!props.moduleTreeExists) {
    return '例如：我要做一个电商系统，包含用户、订单、支付、库存、后台管理、权限控制等模块。'
  }

  return '例如：给用户中心增加一个密码找回页，放在登录注册子模块下面。'
})

const buttonText = computed(() => {
  if (!props.moduleTreeExists) {
    return props.isGeneratingTree ? '生成中...' : '生成需求骨架'
  }

  if (props.isSendingMessage) {
    return '处理中...'
  }

  return '发送'
})

const hasUploadingAttachment = computed(() => {
  return pendingAttachments.value.some(
    (attachment) => attachment.uploadStatus === 'UPLOADING',
  )
})

const hasFailedAttachment = computed(() => {
  return pendingAttachments.value.some((attachment) => {
    if (attachment.uploadStatus === 'FAILED') {
      return true
    }

    return String(attachment.parseStatus || '').toUpperCase() === 'FAILED'
  })
})

const readyAttachments = computed(() => {
  return pendingAttachments.value.filter((attachment) => {
    if (attachment.uploadStatus !== 'READY' || !attachment.attachmentId) {
      return false
    }

    return String(attachment.parseStatus || '').toUpperCase() !== 'FAILED'
  })
})

const submitDisabled = computed(() => {
  const hasText = Boolean(inputText.value.trim())
  const hasReadyAttachment = readyAttachments.value.length > 0

  return (
    (!hasText && !hasReadyAttachment) ||
    hasUploadingAttachment.value ||
    hasFailedAttachment.value ||
    isBusy.value
  )
})

const pendingText = computed(() => {
  if (props.isGeneratingTree) {
    return '正在根据业务需求生成需求骨架，请稍候。'
  }

  if (props.isSendingMessage) {
    return '正在理解你的意图，并调用对应的需求 Agent 能力。'
  }

  if (props.isApplyingTreePatch) {
    return '正在应用模块树变更提案，并标记受影响资产。'
  }

  return '正在处理。'
})

watch(
  () => props.historyMessages,
  (historyMessages) => {
    const historyBasedMessages = buildMessagesFromHistory(historyMessages || [])
    messages.value = mergeHistoryMessagesWithLocalOperationMessages(
      historyBasedMessages,
      messages.value,
    )
    scrollToBottom()
  },
  {
    immediate: true,
    deep: true,
  },
)

watch(
  () => props.assistantResponse,
  (response) => {
    if (!response) {
      return
    }

    if (response.intent === 'CONFIRM_ASSET_EDIT') {
      markAssetEditProposalsAsSaved()
    }

    upsertAssistantMessage(
      buildMessageFromAssistantResponse(response),
    )

    scrollToBottom()
  },
)

watch(
  () => props.moduleTreeExists,
  () => {
    messages.value = messages.value.map((message) =>
      message.id === 'assistant_system_welcome'
        ? {
            ...message,
            text: buildAssistantWelcomeText(),
          }
        : message,
    )
  },
)


function extractQuickRepliesFromResponse(
  response: AssistantMessageResponse,
): AssistantQuickReply[] {
  const responseRecord = response as unknown as Record<string, unknown>
  const directQuickReplies = responseRecord['quickReplies']

  if (Array.isArray(directQuickReplies)) {
    return directQuickReplies
      .map((item) => {
        if (!isRecord(item)) return null
        const label = String(item.label || item.text || '').trim()
        const text = String(item.text || item.label || '').trim()

        return label && text
          ? { label, text }
          : null
      })
      .filter((item): item is AssistantQuickReply => Boolean(item))
  }

  return (response.actions || [])
    .filter((action) =>
      action.type === 'QUICK_REPLY' ||
      action.type === 'A9_MODULE_TREE_MODE_CHOICE',
    )
    .map((action) => {
      const payload = action.payload || {}
      const text = String(payload.text || payload.value || action.label || '').trim()
      const label = String(action.label || text).trim()

      return label && text
        ? { label, text }
        : null
    })
    .filter((item): item is AssistantQuickReply => Boolean(item))
}

function normalizeQuickReplyDisplayText(
  text: string,
  displayText?: string,
): string {
  const label = String(displayText || '').trim()
  const value = text.trim()

  if (label && label !== value) {
    return label.replace(/^\s*[12一二ABab][.、\s-]*/, '').trim() || label
  }

  if (/^(2|二|b)$/i.test(value)) {
    return '我选择：生成完整企业蓝图'
  }

  if (/^(1|一|a)$/i.test(value)) {
    return '我选择：生成模块骨架'
  }

  return value
}

function sendQuickReply(text: string, displayText?: string) {
  const trimmedText = text.trim()
  const visibleText = normalizeQuickReplyDisplayText(trimmedText, displayText)

  if (!trimmedText || isBusy.value) {
    return
  }

  messages.value.push({
    ...createMessage('user', 'TEXT', visibleText),
  })

  emit('send', {
    text: trimmedText,
    attachments: [],
  })

  scrollToBottom()
}

function buildMessageFromAssistantResponse(
  response: AssistantMessageResponse,
): AssistantMessage {
  const requirementChangeProposal =
    extractRequirementChangeProposalFromResponse(response)
  const assetEditProposal =
    extractAssetEditProposalFromResponse(response)
  const attachmentDrivenChangeProposal =
    extractAttachmentDrivenChangeProposalFromResponse(response)
  const attachmentArtifactDiffProposal =
    extractAttachmentArtifactDiffProposalFromResponse(response)

  return {
    ...createMessage(
      'assistant',
      response.proposal
        ? 'TREE_PATCH_PROPOSAL'
        : assetEditProposal
          ? 'ASSET_EDIT_PROPOSAL'
          : attachmentArtifactDiffProposal
            ? 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL'
            : attachmentDrivenChangeProposal
              ? 'ATTACHMENT_CHANGE_PROPOSAL'
              : requirementChangeProposal
                ? 'REQUIREMENT_CHANGE_PROPOSAL'
                : 'TEXT',
      response.reply || '已完成处理。',
      response.messageId || undefined,
    ),
    proposal: response.proposal || null,
    quickReplies: extractQuickRepliesFromResponse(response),
    requirementChangeProposal,
    assetEditProposal,
    attachmentDrivenChangeProposal,
    attachmentArtifactDiffProposal,
  }
}

function upsertAssistantMessage(
  nextMessage: AssistantMessage,
) {
  const sourceMessages = pruneSupersededTransientMessages(
    nextMessage,
    messages.value,
  )

  const index = sourceMessages.findIndex(
    (message) => message.id === nextMessage.id,
  )

  if (index >= 0) {
    messages.value = sourceMessages.map((message, messageIndex) =>
      messageIndex === index
        ? {
            ...message,
            ...nextMessage,
          }
        : message,
    )
    return
  }

  messages.value = [
    ...sourceMessages,
    nextMessage,
  ]
}

function pruneSupersededTransientMessages(
  nextMessage: AssistantMessage,
  sourceMessages: AssistantMessage[],
): AssistantMessage[] {
  const nextId = String(nextMessage.id || '')
  const nextType = String(nextMessage.type || '')

  const withoutDuplicateGenerating = nextId.startsWith('module_tree_generation_started_')
    ? sourceMessages.filter(
        (message) =>
          !String(message.id || '').startsWith('module_tree_generation_started_'),
      )
    : sourceMessages

  const shouldClearModuleTreeGenerating =
    nextId.startsWith('module_tree_generated_') ||
    nextType === 'TREE_PATCH_PROPOSAL' ||
    nextType === 'ATTACHMENT_CHANGE_PROPOSAL' ||
    nextType === 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL' ||
    nextType === 'REQUIREMENT_CHANGE_PROPOSAL' ||
    nextType === 'ASSET_EDIT_PROPOSAL'

  if (!shouldClearModuleTreeGenerating) {
    return withoutDuplicateGenerating
  }

  return withoutDuplicateGenerating.filter(
    (message) =>
      !String(message.id || '').startsWith('module_tree_generation_started_'),
  )
}


function mergeHistoryMessagesWithLocalOperationMessages(
  historyBasedMessages: AssistantMessage[],
  currentMessages: AssistantMessage[],
): AssistantMessage[] {
  const historyIds = new Set(historyBasedMessages.map((message) => message.id))
  const localOperationMessages = currentMessages.filter((message) =>
    !historyIds.has(message.id) &&
    isLocalOperationMessage(message)
  )

  return [
    ...historyBasedMessages,
    ...localOperationMessages,
  ]
}

function isLocalOperationMessage(
  message: AssistantMessage,
): boolean {
  if (message.role !== 'assistant') {
    return false
  }

  const id = String(message.id || '')

  return (
    id.startsWith('module_tree_generated_') ||
    id.startsWith('assistant_error_') ||
    id.startsWith('module_tree_patch_applied_') ||
    id.startsWith('dev_handoff_package_') ||
    id.startsWith('asset_edit_saved_') ||
    id.startsWith('attachment_change_ack_') ||
    id.startsWith('attachment_diff_ack_')
  )
}


function submit() {
  if (submitDisabled.value) {
    return
  }

  const text = inputText.value.trim() ||
    '请分析这些附件，并结合当前项目上下文给出建议。'

  const attachmentsForMessage = readyAttachments.value.map((attachment) => ({
    ...attachment,
  }))

  const attachmentsForRequest = attachmentsForMessage.map((attachment) => ({
    attachmentId: attachment.attachmentId || null,
    fileName: attachment.fileName,
    mimeType: attachment.mimeType,
    size: attachment.size,
    parseStatus: attachment.parseStatus || null,
    parsedTextExcerpt: attachment.parsedTextExcerpt || null,
    metadata: attachment.metadata || {},
  }))

  messages.value.push({
    ...createMessage('user', 'TEXT', text),
    attachments: attachmentsForMessage.length
      ? attachmentsForMessage
      : undefined,
  })

  emit('send', {
    text,
    attachments: attachmentsForRequest,
  })

  inputText.value = ''
  pendingAttachments.value = []

  scrollToBottom()
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') {
    return
  }

  if (event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }

  event.preventDefault()
  submit()
}

function clearProposal(message: AssistantMessage) {
  message.type = 'TEXT'
  message.proposal = null
  message.text = '该模块树变更提案已放弃。你可以继续描述新的修改要求。'

  emit('clear-proposal')
  scrollToBottom()
}


function clearRequirementChangeProposal(message: AssistantMessage) {
  message.type = 'TEXT'
  message.requirementChangeProposal = null
  message.text = '该需求变更草案已放弃。你可以直接继续说明不满意的部分，例如“不是加新页面，是在登录页表单里增加短信验证码”。'

  scrollToBottom()
}

function handleConfirmRequirementChange(
  proposal: RequirementChangeProposal | null | undefined,
) {
  if (!proposal) {
    return
  }

  emit('confirm-requirement-change', proposal)
}

function clearAssetEditProposal(message: AssistantMessage) {
  message.type = 'TEXT'
  message.assetEditProposal = null
  message.text = '该资产编辑提案已放弃。你可以直接继续说明不满意的部分，AI 会重新生成一版编辑方案。'

  scrollToBottom()
}

function handleConfirmAssetEditProposal(
  proposal: AssetEditProposal | null | undefined,
) {
  if (!proposal) {
    return
  }

  emit('confirm-asset-edit-proposal', proposal)
}


function clearAttachmentDrivenChangeProposal(message: AssistantMessage) {
  message.type = 'TEXT'
  message.attachmentDrivenChangeProposal = null
  message.text = '该附件驱动变更分析已暂不处理。你可以继续说明希望基于附件执行什么操作。'

  scrollToBottom()
}

function handleExecuteAttachmentChange(
  proposal: AttachmentDrivenChangeProposal | null | undefined,
  action: AttachmentDrivenChangeAction | null | undefined,
) {
  if (!proposal || !action) {
    return
  }

  emit('execute-attachment-change', proposal, action)
}


function clearAttachmentArtifactDiffProposal(message: AssistantMessage) {
  const selectionKey = getAttachmentDiffSelectionKey(message)

  message.type = 'TEXT'
  message.attachmentArtifactDiffProposal = null
  message.text = '该附件与当前资产差异分析已暂不处理。你可以继续说明希望基于哪一条差异生成修订提案。'

  const nextSelections = { ...attachmentDiffSelections.value }
  delete nextSelections[selectionKey]
  attachmentDiffSelections.value = nextSelections

  scrollToBottom()
}

function handleExecuteAttachmentDiffAction(
  proposal: AttachmentArtifactDiffProposal | null | undefined,
  action: AttachmentArtifactDiffAction | null | undefined,
  selectedDiffItemIds: string[] = [],
) {
  if (!proposal || !action) {
    return
  }

  const normalizedSelectedDiffItemIds = normalizeSelectedAttachmentDiffItemIds(
    proposal,
    selectedDiffItemIds,
  )

  if (!normalizedSelectedDiffItemIds.length) {
    return
  }

  emit(
    'execute-attachment-diff-action',
    {
      ...proposal,
      selectedDiffItemIds: normalizedSelectedDiffItemIds,
    },
    action,
    normalizedSelectedDiffItemIds,
  )
}

function markAssetEditProposalsAsSaved() {
  messages.value = messages.value.map((message) => {
    if (message.type !== 'ASSET_EDIT_PROPOSAL' || !message.assetEditProposal) {
      return message
    }

    return {
      ...message,
      type: 'TEXT',
      assetEditProposal: null,
      text: '该局部修订提案已确认保存，已进入版本历史。',
    }
  })
}



function extractAttachmentDrivenChangeProposalFromResponse(
  response: AssistantMessageResponse | null,
): AttachmentDrivenChangeProposal | null {
  if (!response) {
    return null
  }

  const responseRecord = response as unknown as Record<string, unknown>

  if (responseRecord['attachmentDrivenChangeProposal']) {
    return normalizeAttachmentDrivenChangeProposal(
      responseRecord['attachmentDrivenChangeProposal'],
    )
  }

  const action = response.actions?.find((item) =>
    item.type === 'ATTACHMENT_DRIVEN_CHANGE_PROPOSAL' ||
    item.type === 'OPEN_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL',
  )

  return normalizeAttachmentDrivenChangeProposal(
    action?.payload?.proposal || action?.payload,
  )
}

function extractAttachmentDrivenChangeProposalFromHistory(
  message: AssistantConversationMessageResponse,
): AttachmentDrivenChangeProposal | null {
  const messageRecord = message as unknown as Record<string, unknown>

  if (messageRecord['attachmentDrivenChangeProposal']) {
    return normalizeAttachmentDrivenChangeProposal(
      messageRecord['attachmentDrivenChangeProposal'],
    )
  }

  const content = message.content || {}

  return normalizeAttachmentDrivenChangeProposal(
    content.attachmentDrivenChangeProposal ||
    extractAttachmentDrivenChangeProposalFromContentActions(content.actions),
  )
}

function extractAttachmentDrivenChangeProposalFromContentActions(
  actions: unknown,
): unknown {
  if (!Array.isArray(actions)) {
    return null
  }

  const action = actions.find((item) => {
    if (!isRecord(item)) {
      return false
    }

    return item.type === 'ATTACHMENT_DRIVEN_CHANGE_PROPOSAL' ||
      item.type === 'OPEN_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL'
  })

  if (!isRecord(action)) {
    return null
  }

  const payload = action.payload

  if (isRecord(payload) && payload.proposal) {
    return payload.proposal
  }

  return payload
}

function normalizeAttachmentDrivenChangeProposal(
  value: unknown,
): AttachmentDrivenChangeProposal | null {
  if (!isRecord(value)) {
    return null
  }

  const proposal = value as AttachmentDrivenChangeProposal

  if (
    !proposal.summary &&
    !proposal.proposalId &&
    !proposal.attachmentSummary &&
    !proposal.materialInstruction
  ) {
    return null
  }

  return proposal
}


function extractAttachmentArtifactDiffProposalFromResponse(
  response: AssistantMessageResponse | null,
): AttachmentArtifactDiffProposal | null {
  if (!response) {
    return null
  }

  const responseRecord = response as unknown as Record<string, unknown>

  if (responseRecord['attachmentArtifactDiffProposal']) {
    return normalizeAttachmentArtifactDiffProposal(
      responseRecord['attachmentArtifactDiffProposal'],
    )
  }

  const action = response.actions?.find((item) =>
    item.type === 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL' ||
    item.type === 'OPEN_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL',
  )

  return normalizeAttachmentArtifactDiffProposal(
    action?.payload?.proposal || action?.payload,
  )
}

function extractAttachmentArtifactDiffProposalFromHistory(
  message: AssistantConversationMessageResponse,
): AttachmentArtifactDiffProposal | null {
  const messageRecord = message as unknown as Record<string, unknown>

  if (messageRecord['attachmentArtifactDiffProposal']) {
    return normalizeAttachmentArtifactDiffProposal(
      messageRecord['attachmentArtifactDiffProposal'],
    )
  }

  const content = message.content || {}

  return normalizeAttachmentArtifactDiffProposal(
    content.attachmentArtifactDiffProposal ||
    extractAttachmentArtifactDiffProposalFromContentActions(content.actions),
  )
}

function extractAttachmentArtifactDiffProposalFromContentActions(
  actions: unknown,
): unknown {
  if (!Array.isArray(actions)) {
    return null
  }

  const action = actions.find((item) => {
    if (!isRecord(item)) {
      return false
    }

    return item.type === 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL' ||
      item.type === 'OPEN_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL'
  })

  if (!isRecord(action)) {
    return null
  }

  const payload = action.payload

  if (isRecord(payload) && payload.proposal) {
    return payload.proposal
  }

  return payload
}

function normalizeAttachmentArtifactDiffProposal(
  value: unknown,
): AttachmentArtifactDiffProposal | null {
  if (!isRecord(value)) {
    return null
  }

  const proposal = value as AttachmentArtifactDiffProposal

  if (
    !proposal.summary &&
    !proposal.proposalId &&
    !proposal.diffItems &&
    !proposal.attachmentSummary
  ) {
    return null
  }

  return proposal
}

function extractAssetEditProposalFromResponse(
  response: AssistantMessageResponse | null,
): AssetEditProposal | null {
  if (!response) {
    return null
  }

  if (response.assetEditProposal) {
    return response.assetEditProposal
  }

  const action = response.actions?.find((item) =>
    item.type === 'ASSET_EDIT_PROPOSAL' ||
    item.type === 'OPEN_ASSET_EDIT_PROPOSAL',
  )

  return normalizeAssetEditProposal(
    action?.payload?.proposal || action?.payload,
  )
}

function extractAssetEditProposalFromHistory(
  message: AssistantConversationMessageResponse,
): AssetEditProposal | null {
  if (message.assetEditProposal) {
    return message.assetEditProposal
  }

  const content = message.content || {}

  return normalizeAssetEditProposal(
    content.assetEditProposal ||
    extractAssetEditProposalFromContentActions(content.actions),
  )
}

function extractAssetEditProposalFromContentActions(
  actions: unknown,
): unknown {
  if (!Array.isArray(actions)) {
    return null
  }

  const action = actions.find((item) => {
    if (!isRecord(item)) {
      return false
    }

    return item.type === 'ASSET_EDIT_PROPOSAL' ||
      item.type === 'OPEN_ASSET_EDIT_PROPOSAL'
  })

  if (!isRecord(action)) {
    return null
  }

  const payload = action.payload

  if (isRecord(payload) && payload.proposal) {
    return payload.proposal
  }

  return payload
}

function normalizeAssetEditProposal(
  value: unknown,
): AssetEditProposal | null {
  if (!isRecord(value)) {
    return null
  }

  const proposal = value as AssetEditProposal

  if (
    !proposal.summary &&
    !proposal.proposalId &&
    !proposal.targetArtifactType &&
    !proposal.targetArtifact
  ) {
    return null
  }

  return proposal
}

function extractRequirementChangeProposalFromResponse(
  response: AssistantMessageResponse | null,
): RequirementChangeProposal | null {
  if (!response) {
    return null
  }

  if (response.requirementChangeProposal) {
    return response.requirementChangeProposal
  }

  const action = response.actions?.find((item) =>
    item.type === 'REQUIREMENT_CHANGE_PROPOSAL' ||
    item.type === 'OPEN_REQUIREMENT_CHANGE_PROPOSAL',
  )

  return normalizeRequirementChangeProposal(
    action?.payload?.proposal || action?.payload,
  )
}

function extractRequirementChangeProposalFromHistory(
  message: AssistantConversationMessageResponse,
): RequirementChangeProposal | null {
  if (message.requirementChangeProposal) {
    return message.requirementChangeProposal
  }

  const content = message.content || {}

  return normalizeRequirementChangeProposal(
    content.requirementChangeProposal ||
    extractRequirementChangeProposalFromContentActions(content.actions),
  )
}

function extractRequirementChangeProposalFromContentActions(
  actions: unknown,
): unknown {
  if (!Array.isArray(actions)) {
    return null
  }

  const action = actions.find((item) => {
    if (!isRecord(item)) {
      return false
    }

    return item.type === 'REQUIREMENT_CHANGE_PROPOSAL' ||
      item.type === 'OPEN_REQUIREMENT_CHANGE_PROPOSAL'
  })

  if (!isRecord(action)) {
    return null
  }

  const payload = action.payload

  if (isRecord(payload) && payload.proposal) {
    return payload.proposal
  }

  return payload
}

function normalizeRequirementChangeProposal(
  value: unknown,
): RequirementChangeProposal | null {
  if (!isRecord(value)) {
    return null
  }

  const proposal = value as RequirementChangeProposal

  if (
    !proposal.summary &&
    !proposal.originalInstruction &&
    !proposal.proposalId
  ) {
    return null
  }

  return proposal
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}


function getAttachmentChangeCandidateActions(
  proposal: AttachmentDrivenChangeProposal,
): AttachmentDrivenChangeAction[] {
  if (proposal.candidateActions?.length) {
    return proposal.candidateActions
  }

  return [
    {
      type: proposal.recommendedDispatch || proposal.executionAction || 'ANALYSIS_ONLY',
      label: '按推荐动作继续',
    },
  ]
}

function normalizeAttachmentActionType(
  action: AttachmentDrivenChangeAction,
): string {
  return String(action.type || '').trim().toUpperCase()
}

function formatAttachmentChangeActionLabel(
  action: AttachmentDrivenChangeAction,
): string {
  if (action.label) {
    return action.label
  }

  const actionType = normalizeAttachmentActionType(action)

  const labels: Record<string, string> = {
    MODULE_TREE_PATCH: '生成模块树变更提案',
    ASSET_EDIT_PROPOSAL: '生成资产局部修订提案',
    GENERATE_REQUIREMENT_ARTIFACT: '生成需求资产',
    ATTACHMENT_ARTIFACT_DIFF: '对比当前资产差异',
    ANALYSIS_ONLY: '仅保留摘要',
  }

  return labels[actionType] || '按该动作继续'
}

function formatAttachmentSummaryItem(
  item: Record<string, unknown>,
): string {
  const fileName = String(item.fileName || '未命名附件')
  const mimeType = String(item.mimeType || '')
  const parseStatus = String(item.parseStatus || '')
  const excerpt = String(item.parsedTextExcerpt || '').trim()
  const parts = [
    fileName,
    mimeType,
    parseStatus ? `解析状态：${parseStatus}` : '',
    excerpt ? `摘录：${trimProposalPreview(excerpt).slice(0, 160)}` : '',
  ].filter(Boolean)

  return parts.join(' / ')
}


function getAttachmentDiffSelectionKey(message: AssistantMessage): string {
  return String(
    message.id ||
      message.attachmentArtifactDiffProposal?.proposalId ||
      'attachment-diff-proposal',
  )
}

function getAttachmentDiffItemId(
  item: AttachmentArtifactDiffItem | Record<string, unknown>,
  index: number,
): string {
  const id = item.id

  if (id !== null && id !== undefined && String(id).trim()) {
    return String(id)
  }

  return `diff-item-${index + 1}`
}

function getAttachmentDiffItemCount(message: AssistantMessage): number {
  return message.attachmentArtifactDiffProposal?.diffItems?.length || 0
}

function isDefaultSelectedAttachmentDiffItem(item: AttachmentArtifactDiffItem): boolean {
  const type = String(item.type || '').trim().toUpperCase()

  return type !== 'ALREADY_COVERED'
}

function getDefaultAttachmentDiffSelection(
  proposal: AttachmentArtifactDiffProposal,
): string[] {
  return (proposal.diffItems || [])
    .map((item, index) => ({
      id: getAttachmentDiffItemId(item, index),
      item,
    }))
    .filter(({ item }) => isDefaultSelectedAttachmentDiffItem(item))
    .map(({ id }) => id)
}

function normalizeSelectedAttachmentDiffItemIds(
  proposal: AttachmentArtifactDiffProposal,
  selectedDiffItemIds: string[],
): string[] {
  const validIds = new Set(
    (proposal.diffItems || []).map((item, index) => getAttachmentDiffItemId(item, index)),
  )

  return Array.from(new Set(selectedDiffItemIds.map(String)))
    .filter((id) => validIds.has(id))
}

function ensureAttachmentDiffSelection(message: AssistantMessage): string[] {
  const proposal = message.attachmentArtifactDiffProposal

  if (!proposal) {
    return []
  }

  const selectionKey = getAttachmentDiffSelectionKey(message)
  const currentSelection = attachmentDiffSelections.value[selectionKey]

  if (currentSelection) {
    return normalizeSelectedAttachmentDiffItemIds(proposal, currentSelection)
  }

  const initialSelection = proposal.selectedDiffItemIds?.length
    ? normalizeSelectedAttachmentDiffItemIds(proposal, proposal.selectedDiffItemIds)
    : getDefaultAttachmentDiffSelection(proposal)

  attachmentDiffSelections.value = {
    ...attachmentDiffSelections.value,
    [selectionKey]: initialSelection,
  }

  return initialSelection
}

function getSelectedAttachmentDiffItemIds(message: AssistantMessage): string[] {
  return [...ensureAttachmentDiffSelection(message)]
}

function getSelectedAttachmentDiffCount(message: AssistantMessage): number {
  return getSelectedAttachmentDiffItemIds(message).length
}

function isAttachmentDiffItemSelected(
  message: AssistantMessage,
  item: AttachmentArtifactDiffItem,
  index: number,
): boolean {
  const selectedIds = new Set(getSelectedAttachmentDiffItemIds(message))

  return selectedIds.has(getAttachmentDiffItemId(item, index))
}

function toggleAttachmentDiffItem(
  message: AssistantMessage,
  item: AttachmentArtifactDiffItem,
  index: number,
) {
  const proposal = message.attachmentArtifactDiffProposal

  if (!proposal) {
    return
  }

  const selectionKey = getAttachmentDiffSelectionKey(message)
  const selectedIds = new Set(getSelectedAttachmentDiffItemIds(message))
  const itemId = getAttachmentDiffItemId(item, index)

  if (selectedIds.has(itemId)) {
    selectedIds.delete(itemId)
  } else {
    selectedIds.add(itemId)
  }

  attachmentDiffSelections.value = {
    ...attachmentDiffSelections.value,
    [selectionKey]: normalizeSelectedAttachmentDiffItemIds(
      proposal,
      Array.from(selectedIds),
    ),
  }
}

function selectAllAttachmentDiffItems(message: AssistantMessage) {
  const proposal = message.attachmentArtifactDiffProposal

  if (!proposal) {
    return
  }

  attachmentDiffSelections.value = {
    ...attachmentDiffSelections.value,
    [getAttachmentDiffSelectionKey(message)]: (proposal.diffItems || [])
      .map((item, index) => getAttachmentDiffItemId(item, index)),
  }
}

function clearAttachmentDiffSelection(message: AssistantMessage) {
  const proposal = message.attachmentArtifactDiffProposal

  if (!proposal) {
    return
  }

  attachmentDiffSelections.value = {
    ...attachmentDiffSelections.value,
    [getAttachmentDiffSelectionKey(message)]: [],
  }
}

function getAttachmentDiffActionButtonTitle(
  message: AssistantMessage,
  action: AttachmentArtifactDiffAction,
): string {
  if (getSelectedAttachmentDiffCount(message) === 0) {
    return '请至少选择一条差异后再继续。'
  }

  return action.description || ''
}


function getAttachmentDiffCandidateActions(
  proposal: AttachmentArtifactDiffProposal,
): AttachmentArtifactDiffAction[] {
  const defaults: AttachmentArtifactDiffAction[] = [
    {
      type: 'ASSET_EDIT_PROPOSAL',
      label: '基于差异生成资产局部修订提案',
      description: '只基于已选择的差异生成可审查的资产局部修订提案，不会直接覆盖资产。',
      targetArtifactType: proposal.targetArtifactType || null,
      expectedIntent: 'ANALYZE_ATTACHMENT',
      payload: { artifactType: proposal.targetArtifactType || null },
    },
    {
      type: 'MODULE_TREE_PATCH',
      label: '生成模块树变更提案',
      description: '只基于已选择的结构性差异生成可审查的模块树 Patch，不会直接修改模块树。',
      targetArtifactType: null,
      expectedIntent: 'PROPOSE_MODULE_TREE_PATCH',
      payload: { artifactType: proposal.targetArtifactType || null },
    },
    {
      type: 'ANALYSIS_ONLY',
      label: '仅保留差异摘要',
      description: '只保留已选择的差异摘要，暂不修改模块树和需求资产。',
      targetArtifactType: null,
      expectedIntent: 'ANALYZE_ATTACHMENT',
      payload: { artifactType: proposal.targetArtifactType || null },
    },
  ]

  const actionByType = new Map<string, AttachmentArtifactDiffAction>()

  defaults.forEach((action) => {
    actionByType.set(normalizeAttachmentDiffActionType(action), action)
  })

  ;(proposal.candidateActions || []).forEach((action) => {
    const actionType = normalizeAttachmentDiffActionType(action)

    if (!actionType) {
      return
    }

    const defaultAction = actionByType.get(actionType)

    actionByType.set(actionType, {
      ...(defaultAction || {}),
      ...action,
      type: action.type || actionType,
      payload: {
        ...(defaultAction?.payload || {}),
        ...(action.payload || {}),
      },
    })
  })

  const orderedTypes = [
    'ASSET_EDIT_PROPOSAL',
    'MODULE_TREE_PATCH',
    'ANALYSIS_ONLY',
  ]

  const orderedActions = orderedTypes
    .map((type) => actionByType.get(type))
    .filter((action): action is AttachmentArtifactDiffAction => Boolean(action))

  const extraActions = Array.from(actionByType.entries())
    .filter(([type]) => !orderedTypes.includes(type))
    .map(([, action]) => action)

  return [...orderedActions, ...extraActions]
}

function normalizeAttachmentDiffActionType(
  action: AttachmentArtifactDiffAction,
): string {
  return String(action.type || '').trim().toUpperCase()
}

function formatAttachmentDiffActionLabel(
  action: AttachmentArtifactDiffAction,
): string {
  if (action.label) {
    return action.label
  }

  const actionType = normalizeAttachmentDiffActionType(action)

  const labels: Record<string, string> = {
    ASSET_EDIT_PROPOSAL: '基于差异生成资产局部修订提案',
    MODULE_TREE_PATCH: '生成模块树变更提案',
    ANALYSIS_ONLY: '仅保留差异摘要',
  }

  return labels[actionType] || '按该动作继续'
}

function formatAttachmentDiffTarget(
  proposal: AttachmentArtifactDiffProposal,
): string {
  const targetArtifact = proposal.targetArtifact
  const targetNode = proposal.targetNode

  const artifactType = String(
    proposal.targetArtifactType ||
      targetArtifact?.artifactType ||
      '当前资产',
  )

  const artifactTitle = String(
    targetArtifact?.artifactTitle ||
      artifactType,
  )

  const versionValue = targetArtifact?.versionNo
  const version =
    versionValue === null || versionValue === undefined
      ? ''
      : ` / V${versionValue}`

  const nodeName = String(
    targetNode?.nodeName ||
      '当前节点',
  )

  return `${nodeName} / ${artifactTitle} / ${artifactType}${version}`
}

function formatAttachmentDiffConfidence(
  value?: number | null,
): string {
  const confidence = Number(value)

  if (!Number.isFinite(confidence)) {
    return '未提供'
  }

  const normalized = confidence <= 1
    ? confidence * 100
    : confidence

  return `${Math.max(0, Math.min(100, Math.round(normalized)))}%`
}

function formatAttachmentDiffItemTitle(
  item: Record<string, unknown>,
  index: number,
): string {
  const type = String(item.type || 'NEEDS_REVIEW').toUpperCase()
  const title = String(item.title || '待核对差异')
  const severity = String(item.severity || '').toUpperCase()

  const typeLabels: Record<string, string> = {
    MISSING: '缺失',
    POTENTIAL_CONFLICT: '可能冲突',
    ALREADY_COVERED: '已覆盖',
    NEEDS_REVIEW: '待核对',
  }

  const severityText = severity ? ` / ${severity}` : ''

  return `${index + 1}. ${typeLabels[type] || type}${severityText}：${title}`
}

function formatRequirementChangeTarget(
  proposal: RequirementChangeProposal,
): string {
  const target = proposal.targetNode || {}

  const nodeName = (target as RequirementChangeProposal['targetNode'])?.nodeName || '当前项目'
  const nodeType = (target as RequirementChangeProposal['targetNode'])?.nodeType || 'PROJECT'
  const targetModuleKey = (target as RequirementChangeProposal['targetNode'])?.moduleKey
  const moduleKey = targetModuleKey ? ` / ${targetModuleKey}` : ''

  return `${nodeName}${moduleKey} / ${nodeType}`
}


function formatArtifactTypeLabel(
  value?: string | null,
): string {
  const artifactType = String(value || '').toUpperCase()

  const labels: Record<string, string> = {
    MODULE_PRD: '模块需求',
    BUSINESS_FLOW: '业务流程',
    PAGE_SPEC: '页面规范',
    DATA_MODEL: '数据模型',
    API_CONTRACT: '接口契约',
    TEST_SPEC: '测试规格',
    UI_CONTRACT: '交互原型',
    DEV_HANDOFF_PACKAGE: '开发交接包',
  }

  return labels[artifactType] || value || '资产'
}

function formatAssetEditMode(
  value?: string | null,
): string {
  const mode = String(value || '').toUpperCase()

  if (mode.includes('UPSTREAM') || mode.includes('SYNC')) {
    return 'AI 局部同步修订'
  }

  if (mode.includes('MANUAL')) {
    return '人工文档编辑'
  }

  if (mode.includes('CHAT')) {
    return '对话式资产修订'
  }

  return value || '对话式资产修订'
}

function formatAssetEditPolicy(
  value?: string | null,
): string {
  const policy = String(value || '')

  if (!policy) {
    return '确认后创建新版本，旧版本保留，新版本进入“新版本待确认”。'
  }

  return policy
    .replace(/PENDING_REVIEW/g, '新版本待确认')
    .replace(/NEEDS_REVIEW/g, '受上游影响')
    .replace(/artifact_version/g, '资产版本')
}

function formatAssetEditTarget(
  proposal: AssetEditProposal,
): string {
  const target = isRecord(proposal.targetArtifact)
    ? proposal.targetArtifact
    : {}
  const proposalRecord = proposal as unknown as Record<string, unknown>
  const targetRecord = target as Record<string, unknown>

  const artifactType = String(
    proposalRecord['targetArtifactType'] ||
    targetRecord['artifactType'] ||
    targetRecord['type'] ||
    '资产',
  )

  const title = String(
    targetRecord['artifactTitle'] ||
    targetRecord['title'] ||
    proposalRecord['currentTitle'] ||
    artifactType,
  )

  const versionValue =
    proposalRecord['currentVersionNo'] ??
    targetRecord['versionNo'] ??
    targetRecord['currentVersionNo'] ??
    null

  const version = versionValue === null || versionValue === undefined || versionValue === ''
    ? ''
    : String(versionValue)

  return `${title} / ${artifactType}${version ? ` / 当前 V${version}` : ''}`
}

function getAssetEditPatchOperations(
  proposal: AssetEditProposal,
): Record<string, unknown>[] {
  const proposalRecord = proposal as unknown as Record<string, unknown>
  const value = proposalRecord['proposedPatchOperations']

  if (!Array.isArray(value)) {
    return []
  }

  return value.filter(isRecord)
}

function formatPatchOperationTitle(
  operation: Record<string, unknown>,
  index: number,
): string {
  const op = String(operation['op'] || operation['operation'] || 'replace')
  const sectionTitle = String(operation['sectionTitle'] || operation['section'] || '').trim()
  const opLabels: Record<string, string> = {
    replace: '替换片段',
    update: '更新片段',
    insert_after: '在指定位置后插入',
    insert_before: '在指定位置前插入',
    append_section: '追加局部章节',
    add_section: '新增局部章节',
  }
  const label = opLabels[op] || opLabels[op.toLowerCase()] || op

  return `${index + 1}. ${label}${sectionTitle ? `：${sectionTitle}` : ''}`
}

function formatPatchOperationText(
  operation: Record<string, unknown>,
): string {
  const findText = String(operation['findText'] || operation['beforeText'] || operation['anchorText'] || '').trim()
  const replaceWith = String(operation['replaceWith'] || operation['afterText'] || operation['newText'] || '').trim()
  const rationale = String(operation['rationale'] || operation['reason'] || '').trim()

  const parts: string[] = []
  if (findText) {
    parts.push(`原片段：${trimProposalPreview(findText)}`)
  }
  if (replaceWith) {
    parts.push(`新片段：${trimProposalPreview(replaceWith)}`)
  }
  if (rationale) {
    parts.push(`原因：${rationale}`)
  }

  return parts.join('；')
}

function trimProposalPreview(
  value?: string | null,
): string {
  const text = String(value || '').trim()

  if (text.length <= 420) {
    return text
  }

  return `${text.slice(0, 420)}...`
}



// A9.10.14.4.3.15.2.4.1_LEGACY_PRD_MIGRATION_ACTION_HOTFIX
// A legacy PRD migration proposal intentionally has no module-tree operations.
// It still has an executable action: start MODULE_PRD LLM upgrade for the
// currently selected MODULE.
function canApplyProposalFromTreePatchCard(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
): boolean {
  if (!proposal) {
    return false
  }

  const operations = ((proposal as any)?.proposal?.operations || []) as unknown[]

  if (Array.isArray(operations) && operations.length > 0) {
    return true
  }

  return isLegacyPrdMigrationProposalFromTreePatchCard(proposal)
}

function formatTreePatchApplyButtonLabel(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
): string {
  return isLegacyPrdMigrationProposalFromTreePatchCard(proposal)
    ? '开始升级'
    : '应用提案'
}

function isLegacyPrdMigrationProposalFromTreePatchCard(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
): boolean {
  if (!proposal) {
    return false
  }

  const record = proposal as unknown as Record<string, any>
  const nested = isRecord(record.proposal)
    ? record.proposal as Record<string, any>
    : {}
  const impact = isRecord(nested.impact)
    ? nested.impact as Record<string, any>
    : isRecord(record.impact)
      ? record.impact as Record<string, any>
      : {}
  const operations = Array.isArray(nested.operations)
    ? nested.operations
    : []
  const affectedArtifacts = Array.isArray(impact.affectedArtifacts)
    ? impact.affectedArtifacts.map(String)
    : Array.isArray(record.affectedArtifacts)
      ? record.affectedArtifacts.map(String)
      : []
  const riskNotes = Array.isArray(impact.riskNotes)
    ? impact.riskNotes.map(String)
    : []
  const text = [
    record.source,
    nested.source,
    record.description,
    nested.description,
    record.summary,
    nested.summary,
    impact.summary,
    ...riskNotes,
  ]
    .filter(Boolean)
    .map(String)
    .join('\n')
    .toUpperCase()

  const affectsModulePrd = affectedArtifacts
    .map((item) => item.toUpperCase())
    .includes('MODULE_PRD')

  if (operations.length > 0 || !affectsModulePrd) {
    return false
  }

  return (
    text.includes('LEGACY_PRD') ||
    text.includes('LEGACY PRD') ||
    text.includes('旧 PRD') ||
    text.includes('旧PRD') ||
    text.includes('老 PRD') ||
    text.includes('老PRD') ||
    text.includes('重新生成 MODULE_PRD') ||
    (
      text.includes('MODULE_PRD') &&
      text.includes('PAGE_REQUIREMENTS') &&
      (text.includes('升级') || text.includes('迁移') || text.includes('补齐'))
    )
  )
}

function handleApplyProposal(
  proposal: ModuleTreePatchProposalResponse | null | undefined,
) {
  if (!proposal) {
    return
  }

  emit('apply-proposal', proposal)
}

function createDefaultMessages(): AssistantMessage[] {
  return [
    createMessage(
      'system',
      'TEXT',
      buildAssistantWelcomeText(),
      'assistant_system_welcome',
    ),
  ]
}

function buildAssistantWelcomeText(): string {
  if (!props.moduleTreeExists) {
    return [
      '你好，我是 CodeMind Studio 的 AI Assistant。',
      '当前项目还没有需求骨架。你可以直接描述要建设的系统，或上传需求材料。',
      '如果描述比较粗，我会先让你选择“先生成模块骨架”还是“生成完整项目蓝图”。',
      '示例：我要做一个智能仓储管理系统，包含入库、出库、库存预警、供应商和仓库管理。',
    ].join('\n')
  }

  return [
    '你好，我是 CodeMind Studio 的 AI Assistant。',
    '当前项目已经有需求骨架。你可以让我继续拆分模块、调整结构、补充功能点、生成页面原型，或解释当前节点下一步应该做什么。',
  ].join('\n')
}


function buildMessagesFromHistory(
  historyMessages: AssistantConversationMessageResponse[],
): AssistantMessage[] {
  const result = createDefaultMessages()

  for (const historyMessage of historyMessages) {
    const role = normalizeHistoryRole(historyMessage.role)
    const text = historyMessage.text || ''

    const requirementChangeProposal =
      extractRequirementChangeProposalFromHistory(historyMessage)
    const assetEditProposal =
      extractAssetEditProposalFromHistory(historyMessage)
    const attachmentDrivenChangeProposal =
      extractAttachmentDrivenChangeProposalFromHistory(historyMessage)
    const attachmentArtifactDiffProposal =
      extractAttachmentArtifactDiffProposalFromHistory(historyMessage)

    if (
      !text &&
      !historyMessage.proposal &&
      !requirementChangeProposal &&
      !assetEditProposal &&
      !attachmentDrivenChangeProposal &&
      !attachmentArtifactDiffProposal
    ) {
      continue
    }

    result.push({
      id: historyMessage.messageId ||
        `${Date.now()}_${Math.random().toString(16).slice(2)}`,
      role,
      type: historyMessage.proposal ||
        historyMessage.type === 'TREE_PATCH_PROPOSAL'
        ? 'TREE_PATCH_PROPOSAL'
        : assetEditProposal ||
          historyMessage.type === 'ASSET_EDIT_PROPOSAL'
          ? 'ASSET_EDIT_PROPOSAL'
          : attachmentArtifactDiffProposal ||
            historyMessage.type === 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL'
            ? 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL'
            : attachmentDrivenChangeProposal ||
              historyMessage.type === 'ATTACHMENT_CHANGE_PROPOSAL'
              ? 'ATTACHMENT_CHANGE_PROPOSAL'
              : requirementChangeProposal ||
                historyMessage.type === 'REQUIREMENT_CHANGE_PROPOSAL'
                ? 'REQUIREMENT_CHANGE_PROPOSAL'
                : 'TEXT',
      text,
      createdAt: formatHistoryTime(historyMessage.createdAt),
      attachments: normalizeHistoryAttachments(historyMessage.attachments),
      proposal: historyMessage.proposal || null,
      requirementChangeProposal,
      assetEditProposal,
      attachmentDrivenChangeProposal,
      attachmentArtifactDiffProposal,
    })
  }

  return result
}

function normalizeHistoryRole(
  role: string,
): AssistantMessageRole {
  if (role === 'USER') {
    return 'user'
  }

  if (role === 'SYSTEM') {
    return 'system'
  }

  return 'assistant'
}

function normalizeHistoryAttachments(
  attachments?: AssistantAttachmentRef[],
): AssistantAttachmentDraft[] | undefined {
  if (!attachments?.length) {
    return undefined
  }

  return attachments.map((attachment, index) => ({
    localId: attachment.attachmentId ||
      `${Date.now()}_${index}_${Math.random().toString(16).slice(2)}`,
    attachmentId: attachment.attachmentId || null,
    fileName: attachment.fileName || '附件',
    mimeType: attachment.mimeType || 'application/octet-stream',
    size: attachment.size || 0,
    parseStatus: attachment.parseStatus || null,
    parsedTextExcerpt: attachment.parsedTextExcerpt || null,
    metadata: attachment.metadata || {},
    uploadStatus: 'READY',
  }))
}

function formatHistoryTime(
  value?: string | null,
): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleTimeString()
}

function createMessage(
  role: AssistantMessageRole,
  type: AssistantMessageType,
  text: string,
  id?: string,
): AssistantMessage {
  return {
    id: id || `${Date.now()}_${Math.random().toString(16).slice(2)}`,
    role,
    type,
    text,
    createdAt: new Date().toLocaleTimeString(),
  }
}

function formatOperation(operation: ModuleTreePatchOperationRequest): string {
  if (operation.op === 'ADD_NODE') {
    return `${operation.moduleName || '新节点'} / ${operation.moduleType || '-'}`
  }

  if (operation.op === 'UPDATE_NODE') {
    return operation.moduleName
      ? `${operation.nodeId || '-'} → ${operation.moduleName}`
      : operation.nodeId || '-'
  }

  if (operation.op === 'MOVE_NODE') {
    return `${operation.nodeId || '-'} → ${operation.newParentId || '根节点'}`
  }

  if (operation.op === 'DELETE_NODE') {
    return operation.nodeId || '-'
  }

  return operation.moduleName || operation.nodeId || '-'
}

function formatAttachmentTitle(
  attachment: AssistantAttachmentRef,
): string {
  const metadata = attachment.metadata || {}
  const imageWidth = typeof metadata.imageWidth === 'number' || typeof metadata.imageWidth === 'string'
    ? String(metadata.imageWidth)
    : ''
  const imageHeight = typeof metadata.imageHeight === 'number' || typeof metadata.imageHeight === 'string'
    ? String(metadata.imageHeight)
    : ''
  const dimensions = imageWidth && imageHeight
    ? `${imageWidth}×${imageHeight}`
    : ''
  const parser = typeof metadata.parser === 'string'
    ? metadata.parser
    : ''
  const parseError = typeof metadata.parseError === 'string'
    ? metadata.parseError
    : ''

  const parts = [
    attachment.fileName || '附件',
    attachment.mimeType || '',
    typeof attachment.size === 'number'
      ? formatFileSize(attachment.size)
      : '',
    attachment.parseStatus
      ? `解析状态：${attachmentParseLabel(attachment)}`
      : '',
    dimensions,
    parser,
    parseError ? `错误：${parseError}` : '',
    attachment.parsedTextExcerpt
      ? `摘录：${attachment.parsedTextExcerpt.slice(0, 120).replace(/\n/g, ' ')}`
      : '',
  ].filter(Boolean)

  return parts.join(' / ')
}

function attachmentParseLabel(
  attachment: AssistantAttachmentRef & {
    uploadStatus?: AssistantAttachmentUploadStatus
    errorMessage?: string
  },
): string {
  if (attachment.uploadStatus === 'UPLOADING') {
    return '上传并解析中'
  }

  if (attachment.uploadStatus === 'FAILED') {
    return '上传失败'
  }

  const status = String(attachment.parseStatus || '').toUpperCase()
  const mimeType = String(attachment.mimeType || '').toLowerCase()

  if (status === 'PARSED') {
    return '已解析'
  }

  if (status === 'FAILED') {
    return '解析失败'
  }

  if (mimeType.startsWith('image/')) {
    return '图片已记录'
  }

  if (status === 'PARSING') {
    return '解析中'
  }

  if (status === 'PENDING') {
    return '待解析'
  }

  return '已上传'
}

function attachmentStatusClass(
  attachment: AssistantAttachmentRef & {
    uploadStatus?: AssistantAttachmentUploadStatus
  },
): string {
  if (attachment.uploadStatus === 'UPLOADING') {
    return 'uploading'
  }

  if (attachment.uploadStatus === 'FAILED') {
    return 'failed'
  }

  const status = String(attachment.parseStatus || '').toUpperCase()
  const mimeType = String(attachment.mimeType || '').toLowerCase()

  if (status === 'PARSED') {
    return 'parsed'
  }

  if (status === 'FAILED') {
    return 'failed'
  }

  if (mimeType.startsWith('image/')) {
    return 'image'
  }

  if (status === 'PENDING' || status === 'PARSING') {
    return 'pending'
  }

  return 'ready'
}

function formatFileSize(size: number): string {
  if (size < 1024) {
    return `${size} B`
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function handleUploadClick() {
  if (isBusy.value) {
    return
  }

  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])

  input.value = ''

  if (!files.length) {
    return
  }

  for (const file of files) {
    const localId = `${Date.now()}_${Math.random().toString(16).slice(2)}`

    pendingAttachments.value.push({
      localId,
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      size: file.size,
      uploadStatus: 'UPLOADING',
    })

    try {
      const uploaded = await props.uploadAttachment(file)

      pendingAttachments.value = pendingAttachments.value.map((attachment) => {
        if (attachment.localId !== localId) {
          return attachment
        }

        return {
          ...attachment,
          attachmentId: uploaded.attachmentId || null,
          fileName: uploaded.fileName || attachment.fileName,
          mimeType: uploaded.mimeType || attachment.mimeType,
          size: uploaded.size || attachment.size,
          parseStatus: uploaded.parseStatus || null,
          parsedTextExcerpt: uploaded.parsedTextExcerpt || null,
          metadata: uploaded.metadata || {},
          uploadStatus: 'READY',
        }
      })
    } catch (error) {
      pendingAttachments.value = pendingAttachments.value.map((attachment) => {
        if (attachment.localId !== localId) {
          return attachment
        }

        return {
          ...attachment,
          uploadStatus: 'FAILED',
          errorMessage: error instanceof Error
            ? error.message
            : '附件上传失败。',
        }
      })
    }
  }

  scrollToBottom()
}

function removeAttachment(localId: string) {
  pendingAttachments.value = pendingAttachments.value.filter(
    (attachment) => attachment.localId !== localId,
  )
}

function startResize(event: MouseEvent) {
  resizing.value = true
  startX.value = event.clientX
  startWidth.value = panelWidth.value

  window.addEventListener('mousemove', handleResize)
  window.addEventListener('mouseup', stopResize)
}

function handleResize(event: MouseEvent) {
  if (!resizing.value) {
    return
  }

  const delta = startX.value - event.clientX
  panelWidth.value = Math.min(
    620,
    Math.max(320, startWidth.value + delta),
  )
}

function stopResize() {
  resizing.value = false

  window.removeEventListener('mousemove', handleResize)
  window.removeEventListener('mouseup', stopResize)
}

async function scrollToBottom() {
  await nextTick()

  if (!messageListRef.value) {
    return
  }

  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleResize)
  window.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.assistant-float-button {
  position: fixed;
  right: 18px;
  bottom: 22px;
  z-index: 70;
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 999px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 800;
  box-shadow: 0 16px 32px rgb(37 99 235 / 35%);
  cursor: pointer;
}

.ai-assistant-panel {
  position: relative;
  min-width: 320px;
  max-width: 620px;
  border-left: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 128px);
  overflow: hidden;
}

.resize-handle {
  position: absolute;
  left: -4px;
  top: 0;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 2;
}

.assistant-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 14px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.assistant-header h3 {
  margin: 0;
  font-size: 17px;
  color: #0f172a;
}

.assistant-header p {
  margin: 5px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.assistant-header-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-shrink: 0;
}

.clear-conversation-button {
  height: 30px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  padding: 0 10px;
  background: #ffffff;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.clear-conversation-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.clear-conversation-button:hover:not(:disabled) {
  border-color: #ef4444;
  color: #dc2626;
}

.collapse-button {
  width: 30px;
  height: 30px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.assistant-message-list {
  flex: 1;
  padding: 14px;
  overflow-y: auto;
  background: #f8fafc;
}

.assistant-message {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.assistant-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #334155;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}

.assistant-message.user .message-avatar {
  background: #2563eb;
  color: #ffffff;
}

.assistant-message.assistant .message-avatar,
.assistant-message.system .message-avatar {
  background: #0f172a;
  color: #ffffff;
}

.message-content {
  max-width: calc(100% - 44px);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px 12px;
  background: #ffffff;
}

.assistant-message.user .message-content {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.assistant-message.pending .message-content {
  border-color: #fde68a;
  background: #fffbeb;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-meta strong {
  color: #0f172a;
  font-size: 13px;
}

.message-meta span {
  font-size: 11px;
  color: #94a3b8;
}

.message-text {
  margin: 6px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.proposal-card {
  margin-top: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
  overflow: hidden;
}

.requirement-change-card {
  border-color: #ddd6fe;
  background: linear-gradient(180deg, #f5f3ff 0%, #ffffff 100%);
}

.asset-edit-card {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 100%);
}

.attachment-change-card {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #fff7ed 0%, #ffffff 100%);
}

.attachment-diff-card {
  border-color: #c4b5fd;
  background: linear-gradient(180deg, #f5f3ff 0%, #ffffff 100%);
}

.attachment-actions {
  flex-wrap: wrap;
}

.attachment-diff-flow-hint {
  border-color: #ddd6fe;
  background: #f5f3ff;
  color: #5b21b6;
}

.attachment-diff-flow-hint strong {
  color: #4c1d95;
}

.attachment-diff-section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.attachment-diff-section-header h4 {
  margin-bottom: 4px;
}

.attachment-diff-selection-summary {
  flex: 0 0 auto;
  border: 1px solid #ddd6fe;
  border-radius: 999px;
  padding: 4px 10px;
  background: #ffffff;
  color: #4c1d95;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
}

.attachment-diff-selection-summary strong {
  color: #312e81;
}

.attachment-diff-selection-actions {
  display: flex;
  gap: 10px;
  margin: 8px 0 10px;
}


.attachment-diff-list {
  display: grid;
  gap: 10px;
  padding-left: 0 !important;
  list-style: none;
}

.attachment-diff-item {
  border: 1px solid #ddd6fe;
  border-radius: 10px;
  padding: 10px;
  background: #ffffff;
}

.attachment-diff-item.selected {
  border-color: #8b5cf6;
}

.attachment-diff-select-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
}

.attachment-diff-select-row input {
  margin-top: 2px;
}

.attachment-diff-select-row strong {
  display: block;
  color: #312e81;
  font-size: 12px;
  line-height: 1.55;
}

.attachment-diff-item p {
  margin-top: 6px;
  white-space: pre-wrap;
}

.diff-field-label {
  color: #475569;
  font-weight: 700;
}

.proposal-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid #dbeafe;
}

.proposal-title-row>div {
  min-width: 0;
}

.proposal-title-row strong {
  color: #0f172a;
  font-size: 14px;
}

.proposal-title-row p {
  margin: 5px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.proposal-title-row>span {
  flex: 0 0 auto;
  align-self: flex-start;
  border-radius: 999px;
  padding: 4px 8px;
  background: #2563eb;
  color: #ffffff;
  font-size: 11px;
  line-height: 1;
  font-weight: 700;
  white-space: nowrap;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.16);
}


.asset-edit-flow-hint {
  display: grid;
  gap: 5px;
  margin: 10px 12px 0;
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f0fdf4;
  color: #166534;
  font-size: 12px;
  line-height: 1.65;
}

.asset-edit-flow-hint strong {
  color: #14532d;
  font-size: 12px;
}

.section-tip {
  margin-bottom: 6px !important;
  color: #64748b !important;
}

.downstream-section {
  background: #f8fafc;
}

.proposal-section {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.proposal-section:last-child {
  border-bottom: none;
}

.proposal-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #0f172a;
}

.proposal-section p,
.proposal-excerpt,
.empty-tip {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.proposal-section ul,
.proposal-section ol {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.proposal-section.warning {
  margin: 10px 12px 0;
  border: 1px solid #fde68a;
  border-radius: 10px;
  background: #fffbeb;
}

.operation-op {
  display: inline-flex;
  margin-right: 6px;
  border-radius: 999px;
  padding: 1px 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 700;
}

.operation-main {
  color: #334155;
}

.proposal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px;
}

.message-attachments,
.pending-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 4px 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  max-width: 100%;
}

.attachment-chip.uploading {
  background: #eff6ff;
  color: #1d4ed8;
}

.attachment-chip.ready,
.attachment-chip.parsed {
  background: #ecfdf5;
  color: #047857;
}

.attachment-chip.pending {
  background: #fffbeb;
  color: #92400e;
}

.attachment-chip.image {
  background: #eef2ff;
  color: #4338ca;
}

.attachment-chip.failed {
  background: #fef2f2;
  color: #b91c1c;
}

.attachment-chip em {
  font-style: normal;
  font-size: 11px;
  opacity: 0.8;
}

.attachment-chip button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.assistant-input-area {
  border-top: 1px solid #e2e8f0;
  padding: 12px;
  background: #ffffff;
}

.assistant-input {
  width: 100%;
  min-height: 92px;
  resize: vertical;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  padding: 10px;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.6;
}

.assistant-input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

.assistant-input-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.upload-button,
.send-button,
.primary-button,
.secondary-button {
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
}

.upload-button,
.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.upload-button:hover:not(:disabled),
.secondary-button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.upload-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.link-button {
  border: none;
  background: transparent;
  color: #4c1d95;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}

.link-button:hover:not(:disabled) {
  text-decoration: underline;
}

.link-button:disabled {
  color: #a8a29e;
  cursor: not-allowed;
}

.input-hint {
  flex: 1;
  color: #94a3b8;
  font-size: 12px;
}

.send-button,
.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
  font-weight: 700;
}

.send-button:disabled,
.primary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.hidden-file-input {
  display: none;
}

/* A9_7_6_AI_PANEL_INDEPENDENT_SCROLL_FIX */
.ai-assistant-panel {
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
}

.assistant-header,
.assistant-input-area {
  flex: 0 0 auto;
}

.assistant-message-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.assistant-input {
  min-height: 78px;
  max-height: 150px;
  resize: vertical;
}


.quick-reply-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.quick-reply-actions button {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 7px 11px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.quick-reply-actions button:hover:not(:disabled) {
  border-color: #2563eb;
  background: #dbeafe;
}

.quick-reply-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

</style>
