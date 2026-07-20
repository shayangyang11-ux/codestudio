import { authFetch } from './httpClient'
import type {
  ProjectThemeProfileApplicationLogRequest,
  ProjectThemeProfileApplicationLogResponse,
  ProjectThemeProfileRollbackResponse,
} from '../types/themeProfileProvenance'

export async function createProjectThemeProfileApplicationLog(
  workspaceId: string,
  projectCode: string,
  payload: ProjectThemeProfileApplicationLogRequest,
): Promise<ProjectThemeProfileApplicationLogResponse> {
  return authFetch<ProjectThemeProfileApplicationLogResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile/application-logs`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

export async function listProjectThemeProfileApplicationLogs(
  workspaceId: string,
  projectCode: string,
  limit = 20,
): Promise<ProjectThemeProfileApplicationLogResponse[]> {
  return authFetch<ProjectThemeProfileApplicationLogResponse[]>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile/application-logs?limit=${encodeURIComponent(String(limit))}`,
  )
}


export async function rollbackProjectThemeProfileToApplicationLog(
  workspaceId: string,
  projectCode: string,
  logId: string,
): Promise<ProjectThemeProfileRollbackResponse> {
  return authFetch<ProjectThemeProfileRollbackResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile/application-logs/${encodeURIComponent(logId)}/rollback`,
    {
      method: 'POST',
    },
  )
}
