import type {
  AgentTaskPayloadResponse,
  AgentTaskSummary,
  AsyncAgentTaskResponse,
  UiContractGenerationReadinessResponse,
} from '../types/codemind'
import type { ProjectThemeProfile } from '../types/themeProfile'
import { authFetch } from './httpClient'

export async function fetchProjectAgentTasks(
  projectId: string,
  moduleId?: string | null,
): Promise<AgentTaskSummary[]> {
  const query = moduleId
    ? `?moduleId=${encodeURIComponent(moduleId)}`
    : ''

  return authFetch<AgentTaskSummary[]>(
    `/api/projects/${encodeURIComponent(projectId)}/agent-tasks${query}`,
  )
}

export async function fetchAgentTask(
  taskId: string,
): Promise<AgentTaskSummary> {
  return authFetch<AgentTaskSummary>(
    `/api/agent-tasks/${encodeURIComponent(taskId)}`,
  )
}

export async function fetchAgentTaskPayload(
  taskId: string,
): Promise<AgentTaskPayloadResponse> {
  return authFetch<AgentTaskPayloadResponse>(
    `/api/agent-tasks/${encodeURIComponent(taskId)}/payload`,
  )
}

export async function cancelAgentTask(
  taskId: string,
  reason = 'User cancelled task from frontend.',
): Promise<AgentTaskSummary> {
  return authFetch<AgentTaskSummary>(
    `/api/agent-tasks/${encodeURIComponent(taskId)}/cancel`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reason,
      }),
    },
  )
}

export async function retryAgentTask(
  taskId: string,
  reason = 'User retried task from frontend.',
): Promise<AgentTaskSummary> {
  return authFetch<AgentTaskSummary>(
    `/api/agent-tasks/${encodeURIComponent(taskId)}/retry`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reason,
      }),
    },
  )
}

export async function archiveAgentTask(
  taskId: string,
  reason = 'User archived task from frontend.',
): Promise<AgentTaskSummary> {
  return authFetch<AgentTaskSummary>(
    `/api/agent-tasks/${encodeURIComponent(taskId)}/archive`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reason,
      }),
    },
  )
}

export async function createUiContractTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: {
    requirementText?: string
    themeProfile?: ProjectThemeProfile | null
  },
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/ui-contract/async`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

export async function fetchUiContractReadiness(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<UiContractGenerationReadinessResponse> {
  return authFetch<UiContractGenerationReadinessResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/ui-contract/readiness`,
  )
}