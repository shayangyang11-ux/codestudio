import type {
  AgentModelRoutingGatewayResponse,
  ConfirmDevelopmentPlanSnapshotRequest,
  ConfirmedDevelopmentPlanSnapshot,
  DevelopmentCodeMaterializationResponse,
  DevelopmentCodeProjectResponse,
  DevelopmentCodebaseFileContentResponse,
  DevelopmentCodebaseWorkspaceResponse,
  DevelopmentCodebaseExportStatusResponse,
  DevelopmentCodebaseSnapshotStatusResponse,
  DevelopmentCodebaseAcceptanceResponse,
  DevelopmentCodebaseAcceptanceRunRequest,
  DevelopmentCodebaseRepairRunRequest,
  DevelopmentCodebaseRepairResponse,
  DevelopmentCodebaseDeploymentStatusResponse,
  DevelopmentCodebaseDeploymentApplyRequest,
  DevelopmentCodebaseDeploymentApplyResponse,
  DevelopmentCodebaseBindingStatusResponse,
  DevelopmentCodebaseRebindRequest,
  DevelopmentCodebaseRebindResponse,
  DevelopmentHandoffContextResponse,
  DevelopmentHandoffReadinessResponse,
  DevelopmentHandoffPackageResponse,
  DevelopmentPhase4AcceptanceResponse,
  DevelopmentPlanResponse,
  DevelopmentPlanSnapshotQueryResponse,
  DevelopmentTargetStackResponse,
  DevelopmentRuntimeActionRequest,
  DevelopmentRuntimeLogsResponse,
  DevelopmentRuntimeStatusResponse,
  GenerateDevelopmentCodeProjectRequest,
  MaterializeDevelopmentCodeProjectRequest,
  GenerateDevelopmentPlanRequest,
} from '../types/developmentHandoff'
import { authBlobFetch, authFetch } from './httpClient'


export async function fetchAgentModelRoutingStatus(): Promise<AgentModelRoutingGatewayResponse> {
  return authFetch<AgentModelRoutingGatewayResponse>('/api/agent-runtime/model-routing/status')
}

export async function fetchDevelopmentHandoffReadiness(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<DevelopmentHandoffReadinessResponse> {
  return authFetch<DevelopmentHandoffReadinessResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/readiness`,
  )
}

export async function fetchDevelopmentHandoffContext(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<DevelopmentHandoffContextResponse> {
  return authFetch<DevelopmentHandoffContextResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/context`,
  )
}


export async function fetchDevelopmentTargetStacks(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<DevelopmentTargetStackResponse[]> {
  return authFetch<DevelopmentTargetStackResponse[]>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/plans/target-stacks`,
  )
}

export async function generateDevelopmentPlan(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDevelopmentPlanRequest = {},
): Promise<DevelopmentPlanResponse> {
  return authFetch<DevelopmentPlanResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/plans/generate`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function fetchCurrentDevelopmentPlanConfirmation(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<ConfirmedDevelopmentPlanSnapshot | null> {
  const response = await authFetch<DevelopmentPlanSnapshotQueryResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/plans/current-confirmation`,
  )

  return response.exists ? response.snapshot || null : null
}

export async function confirmDevelopmentPlanSnapshot(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: ConfirmDevelopmentPlanSnapshotRequest,
): Promise<ConfirmedDevelopmentPlanSnapshot> {
  return authFetch<ConfirmedDevelopmentPlanSnapshot>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/plans/confirm`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function generateDevelopmentHandoffPackage(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<DevelopmentHandoffPackageResponse> {
  return authFetch<DevelopmentHandoffPackageResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/packages/generate`,
    {
      method: 'POST',
    },
  )
}


export async function generateDevelopmentCodeProject(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDevelopmentCodeProjectRequest = {},
): Promise<DevelopmentCodeProjectResponse> {
  return authFetch<DevelopmentCodeProjectResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/code-projects/generate`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}





export async function materializeDevelopmentCodeProject(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: MaterializeDevelopmentCodeProjectRequest = {},
): Promise<DevelopmentCodeMaterializationResponse> {
  return authFetch<DevelopmentCodeMaterializationResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/code-projects/materialize`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function fetchDevelopmentPhase4Acceptance(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<DevelopmentPhase4AcceptanceResponse> {
  return authFetch<DevelopmentPhase4AcceptanceResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/handoff/phase4/acceptance`,
  )
}

export async function fetchDevelopmentCodebaseWorkspace(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseWorkspaceResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseWorkspaceResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/workspace${query}`,
  )
}

export async function fetchDevelopmentCodebaseFileContent(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  filePath: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseFileContentResponse> {
  const params = new URLSearchParams({
    filePath,
    targetStack,
  })

  return authFetch<DevelopmentCodebaseFileContentResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/files/content?${params.toString()}`,
  )
}



export async function fetchDevelopmentRuntimeStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentRuntimeStatusResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentRuntimeStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/status${query}`,
  )
}

export async function installDevelopmentRuntimeDependencies(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentRuntimeActionRequest = {},
): Promise<DevelopmentRuntimeStatusResponse> {
  return authFetch<DevelopmentRuntimeStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/install`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function startDevelopmentRuntime(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentRuntimeActionRequest = {},
): Promise<DevelopmentRuntimeStatusResponse> {
  return authFetch<DevelopmentRuntimeStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/start`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function stopDevelopmentRuntime(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentRuntimeActionRequest = {},
): Promise<DevelopmentRuntimeStatusResponse> {
  return authFetch<DevelopmentRuntimeStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/stop`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function restartDevelopmentRuntime(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentRuntimeActionRequest = {},
): Promise<DevelopmentRuntimeStatusResponse> {
  return authFetch<DevelopmentRuntimeStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/restart`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function fetchDevelopmentRuntimeLogs(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
  lines = 220,
): Promise<DevelopmentRuntimeLogsResponse> {
  const params = new URLSearchParams({
    targetStack,
    lines: String(lines),
  })

  return authFetch<DevelopmentRuntimeLogsResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/runtime/logs?${params.toString()}`,
  )
}



export async function fetchDevelopmentCodebaseSnapshotStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseSnapshotStatusResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseSnapshotStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/snapshots/status${query}`,
  )
}


export async function fetchDevelopmentCodebaseAcceptanceStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseAcceptanceResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseAcceptanceResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/acceptance/status${query}`,
  )
}

export async function runDevelopmentCodebaseAcceptance(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentCodebaseAcceptanceRunRequest = {},
): Promise<DevelopmentCodebaseAcceptanceResponse> {
  return authFetch<DevelopmentCodebaseAcceptanceResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/acceptance/run`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}



export async function proposeDevelopmentCodebaseRepair(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentCodebaseRepairRunRequest = {},
): Promise<DevelopmentCodebaseRepairResponse> {
  return authFetch<DevelopmentCodebaseRepairResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/repair/propose`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function applyDevelopmentCodebaseRepair(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentCodebaseRepairRunRequest = {},
): Promise<DevelopmentCodebaseRepairResponse> {
  return authFetch<DevelopmentCodebaseRepairResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/repair/apply`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}


export async function fetchDevelopmentCodebaseBindingStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseBindingStatusResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseBindingStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/binding/status${query}`,
  )
}

export async function rebindDevelopmentCodebase(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentCodebaseRebindRequest = {},
): Promise<DevelopmentCodebaseRebindResponse> {
  return authFetch<DevelopmentCodebaseRebindResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/binding/rebind`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}


export async function fetchDevelopmentCodebaseDeploymentStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseDeploymentStatusResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseDeploymentStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/deployment/status${query}`,
  )
}

export async function applyDevelopmentCodebaseDeploymentConfig(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: DevelopmentCodebaseDeploymentApplyRequest = {},
): Promise<DevelopmentCodebaseDeploymentApplyResponse> {
  return authFetch<DevelopmentCodebaseDeploymentApplyResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/deployment/apply`,
    {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    },
  )
}

export async function fetchDevelopmentCodebaseExportStatus(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
): Promise<DevelopmentCodebaseExportStatusResponse> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''

  return authFetch<DevelopmentCodebaseExportStatusResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/export/status${query}`,
  )
}

export async function downloadDevelopmentCodebaseZip(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  targetStack = 'NODEJS_FULLSTACK',
  fallbackFileName = 'codemind-generated-codebase.zip',
): Promise<string> {
  const query = targetStack ? `?targetStack=${encodeURIComponent(targetStack)}` : ''
  const response = await authBlobFetch(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/development/codebase/export/download${query}`,
  )
  const blob = await response.blob()
  const fileName = resolveDownloadFileName(
    response.headers.get('Content-Disposition'),
    fallbackFileName,
  )
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)

  return fileName
}

function resolveDownloadFileName(
  contentDisposition: string | null,
  fallback: string,
): string {
  if (!contentDisposition) {
    return fallback
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)

  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].replace(/\"/g, ''))
    } catch {
      return utf8Match[1].replace(/\"/g, '')
    }
  }

  const plainMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i)

  return plainMatch?.[1] || fallback
}




