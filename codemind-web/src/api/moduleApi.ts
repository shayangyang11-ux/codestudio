import type {
  ApplyModuleTreePatchRequest,
  CreateProjectModuleNodeRequest,
  ModuleTreeNode,
  ModuleTreePatchApplyResponse,
  ModuleTreePatchProposalResponse,
  MoveProjectModuleNodeRequest,
  ProjectModuleMutationResponse,
  ProposeModuleTreePatchRequest,
  UpdateProjectModuleNodeRequest,
} from '../types/codemind'
import { authFetch } from './httpClient'
import { normalizeModuleNodes } from './moduleUtils'

export async function fetchProjectModules(
  workspaceId: string,
  projectCode: string,
): Promise<ModuleTreeNode[]> {
  const raw = await authFetch<unknown>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}/modules`,
  )

  return normalizeModuleNodes(raw)
}

export async function createProjectModuleNode(
  workspaceId: string,
  projectCode: string,
  payload: CreateProjectModuleNodeRequest,
): Promise<ProjectModuleMutationResponse> {
  const raw = await authFetch<{
    node?: ModuleTreeNode | null
    tree?: unknown
  }>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}/modules`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  return normalizeMutationResponse(raw)
}

export async function updateProjectModuleNode(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: UpdateProjectModuleNodeRequest,
): Promise<ProjectModuleMutationResponse> {
  const raw = await authFetch<{
    node?: ModuleTreeNode | null
    tree?: unknown
  }>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}/update`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  return normalizeMutationResponse(raw)
}

export async function moveProjectModuleNode(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: MoveProjectModuleNodeRequest,
): Promise<ProjectModuleMutationResponse> {
  const raw = await authFetch<{
    node?: ModuleTreeNode | null
    tree?: unknown
  }>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}/move`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  return normalizeMutationResponse(raw)
}

export async function deleteProjectModuleNode(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<ProjectModuleMutationResponse> {
  const raw = await authFetch<{
    node?: ModuleTreeNode | null
    tree?: unknown
  }>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}/delete`,
    {
      method: 'POST',
    },
  )

  return normalizeMutationResponse(raw)
}

export async function proposeModuleTreePatch(
  workspaceId: string,
  projectCode: string,
  payload: ProposeModuleTreePatchRequest,
): Promise<ModuleTreePatchProposalResponse> {
  return authFetch<ModuleTreePatchProposalResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/tree-patch/propose`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

export async function applyModuleTreePatch(
  workspaceId: string,
  projectCode: string,
  payload: ApplyModuleTreePatchRequest,
): Promise<ModuleTreePatchApplyResponse> {
  const raw = await authFetch<{
    description?: string
    source?: string
    operationCount?: number
    appliedOperations?: string[]
    tree?: unknown
  }>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/tree-patch/apply`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  return {
    description: raw.description,
    source: raw.source,
    operationCount: raw.operationCount || 0,
    appliedOperations: raw.appliedOperations || [],
    tree: normalizeModuleNodes(raw.tree || []),
  }
}

function normalizeMutationResponse(raw: {
  node?: ModuleTreeNode | null
  tree?: unknown
}): ProjectModuleMutationResponse {
  return {
    node: raw.node || null,
    tree: normalizeModuleNodes(raw.tree || []),
  }
}