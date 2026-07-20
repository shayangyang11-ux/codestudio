import type {
  ConfirmedUiContractSnapshot,
  ConfirmUiContractSnapshotRequest,
  UiContractSnapshotQueryResponse,
} from '../types/uiContractSnapshot'
import { authFetch } from './httpClient'

export async function fetchCurrentUiContractSnapshot(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
): Promise<ConfirmedUiContractSnapshot | null> {
  const response = await authFetch<UiContractSnapshotQueryResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/ui-contract-snapshots/current`,
  )

  return response.exists ? response.snapshot || null : null
}

export async function confirmUiContractSnapshot(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: ConfirmUiContractSnapshotRequest,
): Promise<ConfirmedUiContractSnapshot> {
  return authFetch<ConfirmedUiContractSnapshot>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/ui-contract-snapshots/confirm`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload || {}),
    },
  )
}
