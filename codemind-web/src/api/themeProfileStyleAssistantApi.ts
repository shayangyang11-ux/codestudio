import { authFetch } from './httpClient'
import type {
  ProjectStyleAssistantRequest,
  ProjectStyleAssistantResponse,
} from '../types/themeProfileStyleAssistant'

export async function analyzeProjectStyleAssistant(
  workspaceId: string,
  projectCode: string,
  payload: ProjectStyleAssistantRequest,
): Promise<ProjectStyleAssistantResponse> {
  const url = `/api/workspaces/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectCode)}/theme-profile/style-assistant/analyze`

  return authFetch<ProjectStyleAssistantResponse>(
    url,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}
