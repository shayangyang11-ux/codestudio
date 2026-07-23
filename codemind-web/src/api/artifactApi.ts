
import type {
  ArtifactQualityCheckRecordResponse,
  ArtifactResponse,
  ArtifactVersionDetailResponse,
  ArtifactVersionListResponse,
  DevHandoffPackageResponse,
  GenerateDevHandoffPackageAssetRequest,
  ManualArtifactEditRequest,
  ManualArtifactEditResponse,
} from '../types/codemind'
import { authBlobFetch, authFetch } from './httpClient'

export async function fetchCurrentModuleArtifact(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactType: string,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/current?artifactType=${encodeURIComponent(artifactType)}`,
  )
}

export async function fetchLatestProjectArtifact(
  workspaceId: string,
  projectCode: string,
  artifactType: string,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/artifacts/latest?artifactType=${encodeURIComponent(artifactType)}`,
  )
}

export async function fetchModuleArtifactVersions(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactType: string,
): Promise<ArtifactVersionListResponse> {
  return authFetch<ArtifactVersionListResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/versions?artifactType=${encodeURIComponent(artifactType)}`,
  )
}

export async function fetchModuleArtifactVersion(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
): Promise<ArtifactVersionDetailResponse> {
  return authFetch<ArtifactVersionDetailResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/versions/${encodeURIComponent(artifactId)}` +
      `?artifactType=${encodeURIComponent(artifactType)}`,
  )
}

export async function rollbackModuleArtifactVersion(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
): Promise<ArtifactVersionDetailResponse> {
  return authFetch<ArtifactVersionDetailResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/versions/${encodeURIComponent(artifactId)}` +
      `/rollback?artifactType=${encodeURIComponent(artifactType)}`,
    {
      method: 'POST',
    },
  )
}

export async function submitModuleArtifactReview(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
  reason?: string,
): Promise<ArtifactVersionDetailResponse> {
  return postArtifactReviewAction(
    workspaceId,
    projectCode,
    moduleId,
    artifactId,
    artifactType,
    'submit-review',
    reason,
  )
}

export async function approveModuleArtifactVersion(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
  reason?: string,
): Promise<ArtifactVersionDetailResponse> {
  return postArtifactReviewAction(
    workspaceId,
    projectCode,
    moduleId,
    artifactId,
    artifactType,
    'approve',
    reason,
  )
}

export async function rejectModuleArtifactVersion(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
  reason?: string,
): Promise<ArtifactVersionDetailResponse> {
  return postArtifactReviewAction(
    workspaceId,
    projectCode,
    moduleId,
    artifactId,
    artifactType,
    'reject',
    reason,
  )
}

export async function archiveModuleArtifactVersion(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
  reason?: string,
): Promise<ArtifactVersionDetailResponse> {
  return postArtifactReviewAction(
    workspaceId,
    projectCode,
    moduleId,
    artifactId,
    artifactType,
    'archive',
    reason,
  )
}

async function postArtifactReviewAction(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  artifactId: string,
  artifactType: string,
  action: string,
  reason?: string,
): Promise<ArtifactVersionDetailResponse> {
  return authFetch<ArtifactVersionDetailResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/versions/${encodeURIComponent(artifactId)}` +
      `/${encodeURIComponent(action)}` +
      `?artifactType=${encodeURIComponent(artifactType)}`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reason: reason || '',
      }),
    },
  )
}

export async function runModuleArtifactQualityCheck(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<ArtifactQualityCheckRecordResponse> {
  return authFetch<ArtifactQualityCheckRecordResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/quality-checks`,
    {
      method: 'POST',
    },
  )
}

export async function fetchLatestModuleArtifactQualityCheck(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<ArtifactQualityCheckRecordResponse> {
  return authFetch<ArtifactQualityCheckRecordResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/quality-checks/latest`,
  )
}


export async function saveManualArtifactEdit(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: ManualArtifactEditRequest,
): Promise<ManualArtifactEditResponse> {
  return authFetch<ManualArtifactEditResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/manual-edits`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        artifactId: payload.artifactId || null,
        artifactType: payload.artifactType,
        contentMarkdown: payload.contentMarkdown,
        reason: payload.reason || '',
      }),
    },
  )
}



export async function generateDevHandoffPackageAsset(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDevHandoffPackageAssetRequest = {},
): Promise<DevHandoffPackageResponse> {
  return authFetch<DevHandoffPackageResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/artifacts/dev-handoff-package`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        forceGenerate: Boolean(payload.forceGenerate),
        includeUnapproved: payload.includeUnapproved ?? true,
        note: payload.note || '',
      }),
    },
  )
}

export function buildModuleArtifactExportZipUrl(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  force = false,
): string {
  const query = force ? '?force=true' : ''

  return (
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
    `/projects/${encodeURIComponent(projectCode)}` +
    `/modules/${encodeURIComponent(moduleId)}` +
    `/artifacts/export.zip${query}`
  )
}

export async function downloadModuleArtifactExportZip(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  force = false,
): Promise<{
  blob: Blob
  contentDisposition: string | null
}> {
  const response = await authBlobFetch(
    buildModuleArtifactExportZipUrl(
      workspaceId,
      projectCode,
      moduleId,
      force,
    ),
    {
      method: 'GET',
    },
  )

  return {
    blob: await response.blob(),
    contentDisposition:
      response.headers.get('Content-Disposition') ||
      response.headers.get('content-disposition'),
  }
}

export function buildModuleArtifactExportDocxUrl(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  force = false,
): string {
  const query = force ? '?force=true' : ''

  return (
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
    `/projects/${encodeURIComponent(projectCode)}` +
    `/modules/${encodeURIComponent(moduleId)}` +
    `/artifacts/export.docx${query}`
  )
}

export async function downloadModuleArtifactExportDocx(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  force = false,
): Promise<{
  blob: Blob
  contentDisposition: string | null
}> {
  const response = await authBlobFetch(
    buildModuleArtifactExportDocxUrl(
      workspaceId,
      projectCode,
      moduleId,
      force,
    ),
    {
      method: 'GET',
    },
  )

  return {
    blob: await response.blob(),
    contentDisposition:
      response.headers.get('Content-Disposition') ||
      response.headers.get('content-disposition'),
  }
}

export function buildProjectArtifactExportZipUrl(
  workspaceId: string,
  projectCode: string,
  force = false,
): string {
  const query = force ? '?force=true' : ''

  return (
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
    `/projects/${encodeURIComponent(projectCode)}` +
    `/artifacts/export-project.zip${query}`
  )
}

export async function downloadProjectArtifactExportZip(
  workspaceId: string,
  projectCode: string,
  force = false,
): Promise<{
  blob: Blob
  contentDisposition: string | null
}> {
  const response = await authBlobFetch(
    buildProjectArtifactExportZipUrl(
      workspaceId,
      projectCode,
      force,
    ),
    {
      method: 'GET',
    },
  )

  return {
    blob: await response.blob(),
    contentDisposition:
      response.headers.get('Content-Disposition') ||
      response.headers.get('content-disposition'),
  }
}
