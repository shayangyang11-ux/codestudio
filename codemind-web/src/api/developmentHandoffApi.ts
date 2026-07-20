
import type {
  ConfirmDevelopmentPlanSnapshotRequest,
  ConfirmedDevelopmentPlanSnapshot,
  DevelopmentHandoffContextResponse,
  DevelopmentHandoffReadinessResponse,
  DevelopmentHandoffPackageResponse,
  DevelopmentPhase4AcceptanceResponse,
  DevelopmentPlanResponse,
  DevelopmentPlanSnapshotQueryResponse,
  GenerateDevelopmentPlanRequest,
} from '../types/developmentHandoff'
import { authFetch } from './httpClient'

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
