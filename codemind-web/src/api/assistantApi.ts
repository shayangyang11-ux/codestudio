import type {
  AssistantAttachmentUploadResponse,
  AssistantConversationArchiveResponse,
  AssistantConversationMessageResponse,
  AssistantMessageRequest,
  AssistantMessageResponse,
  AssistantTaskNotificationRequest,
  AssetEditProposal,
  ConfirmAssistantAssetEditProposalResponse,
  RequirementChangeProposal,
} from '../types/codemind'
import { authBlobFetch, authFetch } from './httpClient'

export async function fetchAssistantMessages(
  workspaceId: string,
  projectCode: string,
  limit = 100,
): Promise<AssistantConversationMessageResponse[]> {
  return authFetch<AssistantConversationMessageResponse[]>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/messages?limit=${encodeURIComponent(String(limit))}`,
  )
}

export async function archiveAssistantMessages(
  workspaceId: string,
  projectCode: string,
): Promise<AssistantConversationArchiveResponse> {
  return authFetch<AssistantConversationArchiveResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/messages/archive`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    },
  )
}

export async function sendAssistantMessage(
  workspaceId: string,
  projectCode: string,
  payload: AssistantMessageRequest,
): Promise<AssistantMessageResponse> {
  return authFetch<AssistantMessageResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/messages`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: payload.message,
        selectedNodeId: payload.selectedNodeId || null,
        selectedArtifactType: payload.selectedArtifactType || null,
        expectedIntent: payload.expectedIntent || null,
        attachments: payload.attachments || [],
        selectedDiffItemIds: payload.selectedDiffItemIds || [],
        selectedDiffItems: payload.selectedDiffItems || [],
        baseArtifactId: payload.baseArtifactId || null,
        baseVersionNo: payload.baseVersionNo ?? null,
        baseChecksum: payload.baseChecksum || null,
        baseModuleTreeHash: payload.baseModuleTreeHash || null,
        baseModuleTreeNodeCount: payload.baseModuleTreeNodeCount ?? null,
      }),
    },
  )
}


// A9.10.14.4.3.15.6.24J2_PROTOTYPE_EDIT_PLANNER_V1
export async function planPrototypeEdit(
  workspaceId: string,
  projectCode: string,
  payload: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return authFetch<Record<string, unknown>>(
    `/api/agent-runtime/prototype-edit/plan`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        workspaceId,
        projectCode,
        ...payload,
      }),
    },
  )
}

export async function createAssistantTaskNotification(
  workspaceId: string,
  projectCode: string,
  payload: AssistantTaskNotificationRequest,
): Promise<AssistantMessageResponse> {
  return authFetch<AssistantMessageResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/task-notifications`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )
}

export async function uploadAssistantAttachment(
  workspaceId: string,
  projectCode: string,
  file: File,
): Promise<AssistantAttachmentUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)

  return authFetch<AssistantAttachmentUploadResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/attachments`,
    {
      method: 'POST',
      body: formData,
    },
  )
}


export function buildAssistantAttachmentContentUrl(
  workspaceId: string,
  projectCode: string,
  attachmentId: string,
): string {
  return (
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
    `/projects/${encodeURIComponent(projectCode)}` +
    `/assistant/attachments/${encodeURIComponent(attachmentId)}/content`
  )
}

export async function fetchAssistantAttachmentObjectUrl(
  workspaceId: string,
  projectCode: string,
  attachmentId: string,
): Promise<string> {
  const response = await authBlobFetch(
    buildAssistantAttachmentContentUrl(
      workspaceId,
      projectCode,
      attachmentId,
    ),
  )
  const blob = await response.blob()
  return window.URL.createObjectURL(blob)
}


export async function proposeAssistantAssetEdit(
  workspaceId: string,
  projectCode: string,
  payload: {
    requirementChangeProposal?: RequirementChangeProposal | null
    instruction?: string | null
    selectedNodeId?: string | null
    selectedArtifactType?: string | null
    selectedDiffItemIds?: string[]
    selectedDiffItems?: RequirementChangeProposal['selectedDiffItems']
    baseArtifactId?: string | null
    baseVersionNo?: number | null
    baseChecksum?: string | null
    baseModuleTreeHash?: string | null
    baseModuleTreeNodeCount?: number | null
  },
): Promise<AssistantMessageResponse> {
  return authFetch<AssistantMessageResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/asset-edit-proposals/propose`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requirementChangeProposal: payload.requirementChangeProposal || null,
        instruction: payload.instruction || null,
        selectedNodeId: payload.selectedNodeId || null,
        selectedArtifactType: payload.selectedArtifactType || null,
        selectedDiffItemIds: payload.selectedDiffItemIds || [],
        selectedDiffItems: payload.selectedDiffItems || [],
        baseArtifactId: payload.baseArtifactId || null,
        baseVersionNo: payload.baseVersionNo ?? null,
        baseChecksum: payload.baseChecksum || null,
        baseModuleTreeHash: payload.baseModuleTreeHash || null,
        baseModuleTreeNodeCount: payload.baseModuleTreeNodeCount ?? null,
      }),
    },
  )
}

export async function confirmAssistantAssetEditProposal(
  workspaceId: string,
  projectCode: string,
  proposal: AssetEditProposal,
  confirmationNote = '',
): Promise<ConfirmAssistantAssetEditProposalResponse> {
  return authFetch<ConfirmAssistantAssetEditProposalResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/assistant/asset-edit-proposals/confirm`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        proposal,
        confirmationNote,
      }),
    },
  )
}
