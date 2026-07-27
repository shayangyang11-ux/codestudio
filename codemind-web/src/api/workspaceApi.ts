import type {
  CreateProjectRequest,
  ProjectSummary,
  WorkspaceSummary,
} from '../types/codemind'
import { authFetch } from './httpClient'

export async function fetchCurrentUserWorkspaces(): Promise<WorkspaceSummary[]> {
  return authFetch<WorkspaceSummary[]>('/api/me/workspaces')
}

export async function fetchWorkspaceProjects(
  workspaceId: string,
): Promise<ProjectSummary[]> {
  return authFetch<ProjectSummary[]>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects`,
  )
}

export async function createWorkspaceProject(
  workspaceId: string,
  payload: CreateProjectRequest,
): Promise<ProjectSummary> {
  return authFetch<ProjectSummary>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function deleteWorkspaceProject(
  workspaceId: string,
  projectCode: string,
): Promise<void> {
  await authFetch<void>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}`,
    {
      method: 'DELETE',
    },
  )
}