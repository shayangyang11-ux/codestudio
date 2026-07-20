import type {
  ProjectThemeProfile,
  UpdateProjectThemeProfileRequest,
} from '../types/themeProfile'
import { authFetch } from './httpClient'

export async function fetchProjectThemeProfile(
  workspaceId: string,
  projectCode: string,
): Promise<ProjectThemeProfile> {
  return authFetch<ProjectThemeProfile>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile`,
  )
}

export async function updateProjectThemeProfile(
  workspaceId: string,
  projectCode: string,
  payload: UpdateProjectThemeProfileRequest,
): Promise<ProjectThemeProfile> {
  return authFetch<ProjectThemeProfile>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}
