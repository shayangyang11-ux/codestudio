
export interface DevelopmentHandoffIssue {
  id: string
  level: 'blocker' | 'warning' | 'pass' | string
  title: string
  description: string
  artifactType?: string | null
}

export interface DevelopmentHandoffAsset {
  artifactType: string
  title: string
  required: boolean
  importance: 'CORE' | 'RECOMMENDED' | string
  exists: boolean
  contentReady: boolean
  readyForDevelopment: boolean
  artifactId?: string | null
  versionNo?: number | null
  status?: string | null
  current?: boolean | null
  updatedAt?: string | null
  statusText: string
}

export interface DevelopmentUiContractSnapshotSummary {
  exists: boolean
  matchesCurrentArtifact: boolean
  snapshotId?: string | null
  artifactId?: string | null
  artifactVersionNo?: number | null
  pageId?: string | null
  pageKey?: string | null
  pageName?: string | null
  status?: string | null
  confirmedBy?: string | null
  confirmedAt?: string | null
}

export interface DevelopmentHandoffReadinessResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  nodeTypeLabel: string
  ready: boolean
  readinessLevel: 'BLOCKED' | 'WARNING' | 'READY' | string
  readinessText: string
  readinessDescription: string
  score: number
  generatedAt?: string | null
  requiredArtifactTypes: string[]
  recommendedArtifactTypes: string[]
  assets: DevelopmentHandoffAsset[]
  blockers: DevelopmentHandoffIssue[]
  warnings: DevelopmentHandoffIssue[]
  passes: DevelopmentHandoffIssue[]
  uiContractSnapshot?: DevelopmentUiContractSnapshotSummary | null
}

export interface DevelopmentHandoffArtifactPayload {
  artifactType: string
  title: string
  required: boolean
  artifactId?: string | null
  versionNo?: number | null
  status?: string | null
  contentMarkdown?: string | null
  content?: Record<string, unknown> | null
}

export interface DevelopmentHandoffContextResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  generatedAt?: string | null
  readiness: DevelopmentHandoffReadinessResponse
  artifacts: DevelopmentHandoffArtifactPayload[]
  confirmedUiContract?: Record<string, unknown> | null
  themeProfileSnapshot?: Record<string, unknown> | null
}

export interface GenerateDevelopmentPlanRequest {
  planScope?: string | null
  targetStack?: string | null
  remarks?: string | null
}

export interface DevelopmentPlanFileItem {
  filePath: string
  fileType: 'FRONTEND' | 'BACKEND' | 'DATABASE' | 'TEST' | 'CONFIG' | string
  action: 'CREATE' | 'UPDATE' | 'REVIEW' | string
  source: string
  description: string
  priority: number
}

export interface DevelopmentPlanApiItem {
  method: string
  path: string
  purpose: string
  sourceArtifactTypes: string[]
  implementationHint: string
}

export interface DevelopmentPlanDatabaseItem {
  objectName: string
  changeType: string
  sourceArtifactTypes: string[]
  description: string
}

export interface DevelopmentPlanStepItem {
  stepNo: number
  title: string
  description: string
  requiredBeforeCodeGeneration: boolean
}

export interface DevelopmentPlanSourceMapping {
  sourceType: string
  sourceTitle: string
  usedFor: string
  confidence: 'HIGH' | 'MEDIUM' | 'LOW' | string
}

export interface DevelopmentPlanResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  ready: boolean
  planStatus: string
  planChecksum?: string | null
  generatedAt?: string | null
  summary: string
  principles: string[]
  sourceArtifactTypes: string[]
  frontendFiles: DevelopmentPlanFileItem[]
  backendFiles: DevelopmentPlanFileItem[]
  apiItems: DevelopmentPlanApiItem[]
  databaseItems: DevelopmentPlanDatabaseItem[]
  implementationSteps: DevelopmentPlanStepItem[]
  sourceMappings: DevelopmentPlanSourceMapping[]
  handoffContext: DevelopmentHandoffContextResponse
}

export interface ConfirmDevelopmentPlanSnapshotRequest {
  plan?: Record<string, unknown> | null
  planChecksum?: string | null
  confirmationNote?: string | null
}

export interface ConfirmedDevelopmentPlanSnapshot {
  id: string
  projectId: string
  moduleId: string
  planChecksum: string
  planStatus: string
  plan: Record<string, unknown>
  readinessSnapshot?: Record<string, unknown> | null
  handoffContextSnapshot?: Record<string, unknown> | null
  sourceArtifactVersions?: Record<string, unknown> | null
  confirmedUiContractSnapshotId?: string | null
  confirmationNote?: string | null
  status: string
  current: boolean
  confirmedBy?: string | null
  confirmedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface DevelopmentPlanSnapshotQueryResponse {
  exists: boolean
  snapshot?: ConfirmedDevelopmentPlanSnapshot | null
}


export interface DevelopmentHandoffPackageManifest {
  schemaVersion: string
  packageType: string
  packageId: string
  packageStatus: string
  projectId: string
  projectCode: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType?: string | null
  generatedAt?: string | null
  generatedBy?: string | null
  confirmedDevelopmentPlanSnapshotId: string
  developmentPlanChecksum: string
  confirmedUiContractSnapshotId?: string | null
  includedSections: string[]
  sourceArtifactVersions?: Record<string, unknown> | null
}

export interface DevelopmentHandoffPackageAssetItem {
  artifactType: string
  title?: string | null
  artifactId?: string | null
  versionNo?: number | null
  status?: string | null
  current?: boolean | null
  checksum?: string | null
  updatedAt?: string | null
}

export interface DevelopmentHandoffPackageResponse {
  packageId: string
  packageStatus: string
  packageChecksum: string
  generatedAt?: string | null
  manifest: DevelopmentHandoffPackageManifest
  confirmedDevelopmentPlan: Record<string, unknown>
  readinessSnapshot?: Record<string, unknown> | null
  handoffContextSnapshot?: Record<string, unknown> | null
  confirmedUiContractSnapshot?: Record<string, unknown> | null
  sourceArtifactVersions?: Record<string, unknown> | null
  assets: DevelopmentHandoffPackageAssetItem[]
  handoffReadmeMarkdown: string
}



export interface DevelopmentPhase4AcceptanceItem {
  id: string
  title: string
  status: 'PASS' | 'READY' | 'BLOCKED' | 'WARNING' | 'SKIPPED' | string
  statusText: string
  description: string
  required: boolean
  passed: boolean
}

export interface DevelopmentPhase4AcceptanceResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  phaseKey: string
  phaseName: string
  readyForNextPhase: boolean
  completionLevel: string
  completionPercent: number
  generatedAt?: string | null
  items: DevelopmentPhase4AcceptanceItem[]
  blockers: string[]
  nextPhases: string[]
  summaryText: string
}
