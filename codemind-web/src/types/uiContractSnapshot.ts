export interface ConfirmedUiContractSnapshot {
  id: string
  projectId: string
  moduleId: string
  artifactId: string
  artifactType: string
  artifactVersionNo: number
  pageId?: string | null
  pageKey?: string | null
  pageName?: string | null
  uiContract: Record<string, unknown>
  themeProfileSnapshot: Record<string, unknown>
  confirmationNote?: string | null
  status: string
  current: boolean
  confirmedBy?: string | null
  confirmedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface UiContractSnapshotQueryResponse {
  exists: boolean
  snapshot?: ConfirmedUiContractSnapshot | null
}

export interface ConfirmUiContractSnapshotRequest {
  artifactId?: string | null
  confirmationNote?: string | null
}
