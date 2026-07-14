import type {
  ProjectAgentTaskMatrix,
  ProjectAgentTaskPage,
  ProjectAgentTaskPageQuery,
  ProjectAgentTaskStatusSummary,
} from '../types/codemind'
import { authFetch } from './httpClient'

export async function fetchProjectAgentTaskSummary(
  projectId: string,
): Promise<ProjectAgentTaskStatusSummary> {
  return authFetch<ProjectAgentTaskStatusSummary>(
    `/api/projects/${encodeURIComponent(projectId)}/agent-tasks/summary`,
  )
}

export async function fetchProjectAgentTaskPage(
  projectId: string,
  query: ProjectAgentTaskPageQuery = {},
): Promise<ProjectAgentTaskPage> {
  const params = new URLSearchParams()

  appendParam(params, 'status', query.status)
  appendParam(params, 'taskType', query.taskType)
  appendParam(params, 'moduleId', query.moduleId)
  appendParam(params, 'createdBy', query.createdBy)
  appendParam(params, 'page', query.page ?? 0)
  appendParam(params, 'size', query.size ?? 20)
  appendParam(params, 'sort', query.sort || 'createdAtDesc')

  return authFetch<ProjectAgentTaskPage>(
    `/api/projects/${encodeURIComponent(projectId)}/agent-tasks/page?${params.toString()}`,
  )
}

export async function fetchProjectAgentTaskMatrix(
  projectId: string,
): Promise<ProjectAgentTaskMatrix> {
  return authFetch<ProjectAgentTaskMatrix>(
    `/api/projects/${encodeURIComponent(projectId)}/agent-tasks/module-matrix`,
  )
}

function appendParam(
  params: URLSearchParams,
  key: string,
  value: string | number | null | undefined,
) {
  if (value === null || value === undefined || value === '') {
    return
  }

  params.set(key, String(value))
}