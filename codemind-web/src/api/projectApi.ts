import type {
  ProjectWorkspaceState,
  SaveProjectWorkspaceStateRequest,
} from '../types/codemind'
import { authFetch } from './httpClient'

export async function fetchProjectWorkspaceState(
  workspaceId: string,
  projectCode: string,
): Promise<ProjectWorkspaceState> {
  return authFetch<ProjectWorkspaceState>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}/workspace-state`,
  )
}

export async function saveProjectWorkspaceState(
  workspaceId: string,
  projectCode: string,
  payload: SaveProjectWorkspaceStateRequest,
): Promise<ProjectWorkspaceState> {
  return authFetch<ProjectWorkspaceState>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}/workspace-state`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}