import type {
  AssistantAttachmentUploadResponse,
  AssistantConversationArchiveResponse,
  AssistantConversationMessageResponse,
  AssistantMessageRequest,
  AssistantMessageResponse,
  AssistantTaskNotificationRequest,
} from '../types/codemind'
import { authFetch } from './httpClient'

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