export interface DevelopmentHandoffIssue {
  id: string
  level: 'blocker' | 'warning' | 'pass' | string
  title: string
  description: string
  artifactType?: string | null
}

export interface DevelopmentHandoffAsset {
  assetKey: string
  artifactType: string
  title: string
  required: boolean
  importance: 'CORE' | 'RECOMMENDED' | string
  sourceScope:
    | 'LOCAL_MODULE'
    | 'LOCAL_PAGE'
    | 'INHERITED_MODULE'
    | 'DESCENDANT_PAGE'
    | string
  sourceNodeId?: string | null
  sourceNodeKey?: string | null
  sourceNodeName?: string | null
  sourceNodeType?: string | null
  inherited: boolean
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
  directDevelopmentAllowed: boolean
  gateMode:
    | 'MODULE_AGGREGATE'
    | 'PAGE_WITH_MODULE_INHERITANCE'
    | 'CONTEXT_ONLY'
    | 'GROUP_ONLY'
    | 'INLINE_ONLY'
    | string
  effectiveModuleId?: string | null
  effectiveModuleKey?: string | null
  effectiveModuleName?: string | null
  scopeSummary: string
  ready: boolean
  readinessLevel:
    | 'BLOCKED'
    | 'WARNING'
    | 'READY'
    | 'NOT_APPLICABLE'
    | string
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
  assetKey: string
  artifactType: string
  title: string
  required: boolean
  sourceScope?: string | null
  sourceNodeId?: string | null
  sourceNodeKey?: string | null
  sourceNodeName?: string | null
  sourceNodeType?: string | null
  inherited?: boolean
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
  developmentBuildContext?: Record<string, unknown> | null
  themeProfileSnapshot?: Record<string, unknown> | null
}

export interface GenerateDevelopmentPlanRequest {
  planScope?: string | null
  targetStack?: string | null
  remarks?: string | null
}

export interface DevelopmentTargetStackResponse {
  stackCode: string
  displayName: string
  lifecycleStatus: 'AVAILABLE' | 'PLANNED' | string
  available: boolean
  description: string
  capabilities: string[]
  runtimeCommands: string[]
  notes: string[]
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
  targetStack?: string | null
  targetStackLabel?: string | null
  generatorMode?: string | null
  runtimeCommands?: string[]
  stackNotes?: string[]
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
  codegenTrace?: DevelopmentCodegenTraceResponse | null
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

export interface GenerateDevelopmentCodeProjectRequest {
  planScope?: string | null
  targetStack?: string | null
  remarks?: string | null
}

export interface DevelopmentCodeFileResponse {
  filePath: string
  fileType: 'FRONTEND' | 'BACKEND' | 'DATABASE' | 'TEST' | 'CONFIG' | 'DOC' | string
  action: 'CREATE' | 'UPDATE' | 'REVIEW' | string
  source: string
  description: string
  language: string
  content: string
  priority: number
}


export interface DevelopmentCodegenTraceResponse {
  traceId: string
  agentScope: string
  llmTask: string
  provider?: string | null
  model?: string | null
  generatorMode?: string | null
  status: string
  aiGenerated: boolean
  fallbackUsed: boolean
  generatedFileCount: number
  warnings: string[]
  usage?: Record<string, unknown> | null
  generatedAt?: string | null
}

export interface DevelopmentCodeProjectResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  targetStack: string
  targetStackLabel: string
  generatorMode: string
  proposalStatus: string
  proposalChecksum: string
  generatedAt?: string | null
  generationRoot: string
  summary: string
  runtimeCommands: string[]
  sourceArtifactTypes: string[]
  warnings: string[]
  files: DevelopmentCodeFileResponse[]
  developmentPlan: DevelopmentPlanResponse
  handoffContext: DevelopmentHandoffContextResponse
  codegenTrace?: DevelopmentCodegenTraceResponse | null
}

export interface MaterializeDevelopmentCodeProjectRequest extends GenerateDevelopmentCodeProjectRequest {
  allowOverwrite?: boolean | null
}

export interface DevelopmentCodeMaterializedFileResponse {
  filePath: string
  absolutePath: string
  fileType: string
  action: string
  status: 'WRITTEN' | 'OVERWRITTEN' | 'SKIPPED_EXISTS' | string
  message: string
  bytes: number
}

export interface DevelopmentCodeMaterializationResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  targetStack: string
  materializationStatus: string
  materializedAt?: string | null
  workspaceRoot: string
  generationRoot: string
  absoluteGenerationRoot: string
  allowOverwrite: boolean
  writtenCount: number
  skippedCount: number
  addedCount?: number | null
  modifiedCount?: number | null
  unchangedCount?: number | null
  conflictCount?: number | null
  warnings: string[]
  files: DevelopmentCodeMaterializedFileResponse[]
  codeProject: DevelopmentCodeProjectResponse
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  snapshot?: DevelopmentCodebaseSnapshotResponse | null
  safetyCheck?: DevelopmentCodebaseSafetyCheckResponse | null
}

export interface DevelopmentCodebaseFileTreeItem {
  name: string
  path: string
  itemType: 'DIRECTORY' | 'FILE' | string
  language?: string | null
  bytes: number
  updatedAt?: string | null
  children: DevelopmentCodebaseFileTreeItem[]
}

export interface DevelopmentCodebaseNodeFile {
  nodeId: string
  nodeType: string
  nodeName: string
  relationship: 'OWNED' | 'CONTRIBUTED' | 'SHARED' | string
  filePath: string
  fileType: string
  description: string
  language: string
  exists: boolean
  bytes: number
}


export interface DevelopmentCodebaseRegistryResponse {
  id?: string | null
  workspaceId?: string | null
  projectId?: string | null
  projectCode?: string | null
  targetStack: string
  storageType: string
  storageRootKey: string
  relativeRootPath: string
  platformRootPath: string
  manifestRelativePath: string
  manifestPlatformPath: string
  absoluteRootPath: string
  status: string
  registered: boolean
  physicalExists: boolean
  lastGeneratedAt?: string | null
  lastMaterializedAt?: string | null
  lastRuntimeStatus?: string | null
  lastFrontendPort?: number | null
  lastBackendPort?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}


export interface DevelopmentCodebaseBindingValidationResponse {
  relativeRootPath: string
  platformRootPath: string
  absoluteRootPath: string
  directoryExists: boolean
  packageJsonExists: boolean
  manifestExists: boolean
  manifestReadable: boolean
  manifestContainsNodes: boolean
  manifestNodeCount: number
  fileCount: number
  totalBytes: number
  blockers: string[]
  warnings: string[]
}

export interface DevelopmentCodebaseBindingStatusResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  bindingStatus: 'BOUND_READY' | 'BOUND_WARNING' | 'BOUND_BROKEN' | 'UNREGISTERED_LOCAL_EXISTS' | 'NOT_BOUND' | string
  registered: boolean
  bindable: boolean
  storageRoot: string
  storageRootKey: string
  suggestedRelativeRootPath: string
  suggestedPlatformRootPath: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  validation: DevelopmentCodebaseBindingValidationResponse
  blockers: string[]
  warnings: string[]
  summary: string
  generatedAt?: string | null
}

export interface DevelopmentCodebaseRebindRequest {
  targetStack?: string | null
  relativeRootPath?: string | null
  platformRootPath?: string | null
  remarks?: string | null
}

export interface DevelopmentCodebaseRebindResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  rebound: boolean
  bindingStatus: 'BOUND' | 'BOUND_MANIFEST_MISSING' | 'REJECTED' | string
  requestedRelativeRootPath: string
  requestedPlatformRootPath: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  validation: DevelopmentCodebaseBindingValidationResponse
  blockers: string[]
  warnings: string[]
  summary: string
  reboundAt?: string | null
}


export interface DevelopmentCodeTraceNode {
  nodeId: string
  nodeType: string
  nodeName: string
  nodeKey?: string | null
  previewRoute?: string | null
  ownedFileCount: number
  relatedFileCount: number
  sharedFileCount: number
}

export interface DevelopmentCodeTraceFile {
  filePath: string
  fileType: string
  language: string
  relationship: 'OWNED' | 'CONTRIBUTED' | 'SHARED' | string
  exists: boolean
  bytes: number
  affectedNodeIds: string[]
  affectedNodes: string[]
}

export interface DevelopmentCodeTraceEdge {
  nodeId: string
  filePath: string
  relationship: 'OWNED' | 'CONTRIBUTED' | 'SHARED' | string
}

export interface DevelopmentCodeTraceGraph {
  schemaVersion: string
  selectedNodeId: string
  selectedNodeType: string
  nodeCount: number
  fileCount: number
  edgeCount: number
  selectedNodeFileCount: number
  nodes: DevelopmentCodeTraceNode[]
  files: DevelopmentCodeTraceFile[]
  edges: DevelopmentCodeTraceEdge[]
  summary: string
}

export interface DevelopmentCodebaseWorkspaceResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  moduleType?: string | null
  nodeType: string
  targetStack: string
  codebaseStatus: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  selectedNodeHasCode: boolean
  selectedNodeCanGenerate: boolean
  generateActionLabel: string
  readinessLevel: string
  ready: boolean
  readinessScore: number
  missingRequiredArtifacts: string[]
  blockers: string[]
  warnings: string[]
  fileTree: DevelopmentCodebaseFileTreeItem[]
  nodeFiles: DevelopmentCodebaseNodeFile[]
  codeTraceGraph?: DevelopmentCodeTraceGraph | null
  defaultFilePath?: string | null
  previewRoute?: string | null
  summary: string
  generatedAt?: string | null
}

export interface DevelopmentCodebaseFileContentResponse {
  filePath: string
  absolutePath: string
  language: string
  content: string
  bytes: number
  updatedAt?: string | null
}





export interface DevelopmentCodebaseFileSaveRequest {
  targetStack?: string | null
  filePath: string
  content: string
  relationship?: 'OWNED' | 'CONTRIBUTED' | 'SHARED' | string | null
  intentScope?: string | null
  fileRole?: string | null
  remarks?: string | null
}

export interface DevelopmentCodebaseFileSaveResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  filePath: string
  absolutePath: string
  language: string
  bytes: number
  relationship: 'OWNED' | 'CONTRIBUTED' | 'SHARED' | string
  fileType: string
  status: string
  affectedNodeIds: string[]
  warnings: string[]
  summary: string
  savedAt?: string | null
}

export interface DevelopmentCodebaseSnapshotFileResponse {
  filePath: string
  sha256: string
  bytes: number
  updatedAt?: string | null
}

export interface DevelopmentCodebaseSnapshotResponse {
  snapshotId: string
  snapshotStatus: string
  reason: string
  snapshotRoot: string
  archivePlatformPath: string
  archiveAbsolutePath: string
  fileCount: number
  totalBytes: number
  checksum: string
  createdAt?: string | null
  warnings: string[]
  files: DevelopmentCodebaseSnapshotFileResponse[]
}

export interface DevelopmentCodebaseSnapshotStatusResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  snapshotRoot: string
  snapshotCount: number
  latestSnapshot?: DevelopmentCodebaseSnapshotResponse | null
  snapshots: DevelopmentCodebaseSnapshotResponse[]
  warnings: string[]
  summary: string
  generatedAt?: string | null
}

export interface DevelopmentCodebaseSafetyFileResponse {
  filePath: string
  fileType: string
  action: string
  changeType: 'ADDED' | 'MODIFIED' | 'UNCHANGED' | 'CONFLICT' | 'SKIPPED_KEEP' | 'OVERWRITE_REQUESTED' | string
  safeToWrite: boolean
  conflict: boolean
  userModified: boolean
  previousSha256?: string | null
  currentSha256?: string | null
  proposedSha256?: string | null
  message: string
}

export interface DevelopmentCodebaseSafetyCheckResponse {
  checkStatus: 'SAFE' | 'CONFLICT' | 'EMPTY' | string
  safeToApply: boolean
  allowOverwrite: boolean
  totalCount: number
  addedCount: number
  modifiedCount: number
  unchangedCount: number
  conflictCount: number
  skippedCount: number
  blockers: string[]
  warnings: string[]
  files: DevelopmentCodebaseSafetyFileResponse[]
  checkedAt?: string | null
}

export interface DevelopmentCodebaseExportStatusResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  exportStatus: 'READY' | 'BLOCKED' | string
  exportable: boolean
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  registered: boolean
  manifestExists: boolean
  fileCount: number
  totalBytes: number
  estimatedZipName: string
  excludedDirectories: string[]
  blockers: string[]
  warnings: string[]
  summary: string
  generatedAt?: string | null
}



export interface DevelopmentCodebaseQualityItemResponse {
  id: string
  category: string
  title: string
  status: 'PASS' | 'WARNING' | 'FAILED' | 'NOT_READY' | string
  required: boolean
  passed: boolean
  scoreImpact: number
  summary: string
  details: string[]
  filePaths: string[]
  recommendation: string
}

export interface DevelopmentCodebaseQualityGateResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  qualityStatus: 'PASS' | 'WARNING' | 'FAILED' | 'NOT_READY' | string
  score: number
  deliverableCandidate: boolean
  passedCount: number
  warningCount: number
  failedCount: number
  requiredFailedCount: number
  items: DevelopmentCodebaseQualityItemResponse[]
  blockers: string[]
  warnings: string[]
  nextActions: string[]
  summary: string
  generatedAt?: string | null
}


export interface DevelopmentCodebaseAcceptanceRunRequest {
  targetStack?: string | null
  installDependencies?: boolean | null
  buildProject?: boolean | null
  runTests?: boolean | null
  checkRuntimeHealth?: boolean | null
}

export interface DevelopmentCodebaseAcceptanceItemResponse {
  id: string
  phase: string
  title: string
  status: 'PASS' | 'READY' | 'WARNING' | 'FAILED' | 'TIMEOUT' | 'SKIPPED' | 'NOT_READY' | string
  required: boolean
  passed: boolean
  commandExecuted: boolean
  command?: string | null
  exitCode?: number | null
  durationMillis?: number | null
  summary: string
  details: string[]
  logTail: string[]
}

export interface DevelopmentCodebaseAcceptanceResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  readyToRun: boolean
  executed: boolean
  deliverable: boolean
  acceptanceStatus: 'READY' | 'PASS' | 'WARNING' | 'FAILED' | 'NOT_READY' | string
  passedCount: number
  warningCount: number
  failedCount: number
  skippedCount: number
  durationMillis: number
  startedAt?: string | null
  finishedAt?: string | null
  frontendUrl: string
  backendUrl: string
  healthUrl: string
  items: DevelopmentCodebaseAcceptanceItemResponse[]
  blockers: string[]
  warnings: string[]
  nextActions: string[]
  summary: string
}

export interface DevelopmentCodebaseRepairRunRequest {
  targetStack?: string | null
  allowOverwrite?: boolean | null
  acceptance?: DevelopmentCodebaseAcceptanceResponse | null
  extraLogLines?: string[] | null
  context?: Record<string, unknown> | null
  remarks?: string | null
}

export interface DevelopmentCodebaseRepairResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  repairStatus: 'BLOCKED' | 'PROPOSED' | 'APPLIED' | 'EMPTY' | 'PARTIAL_APPLIED_WITH_CONFLICTS' | string
  aiGenerated: boolean
  applied: boolean
  provider?: string | null
  model?: string | null
  generatorMode?: string | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  failedItemCount: number
  repairFileCount: number
  generatedAt?: string | null
  summary: string
  blockers: string[]
  warnings: string[]
  nextActions: string[]
  failedItems: DevelopmentCodebaseAcceptanceItemResponse[]
  repairCodeProject?: DevelopmentCodeProjectResponse | null
  materialization?: DevelopmentCodeMaterializationResponse | null
  postRepairAcceptance?: DevelopmentCodebaseAcceptanceResponse | null
}



export interface DevelopmentCodebaseDeploymentFileResponse {
  filePath: string
  fileType: string
  action: 'CREATE' | 'UPDATE' | 'UNCHANGED' | 'WRITTEN' | 'OVERWRITTEN' | 'SKIPPED_EXISTS' | string
  exists: boolean
  upToDate: boolean
  writable: boolean
  bytes: number
  description: string
  message: string
}

export interface DevelopmentCodebaseDeploymentStatusResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  codebaseExists: boolean
  packageJsonExists: boolean
  deployable: boolean
  deploymentStatus: 'READY' | 'NOT_GENERATED' | 'NEEDS_UPDATE' | 'BLOCKED' | string
  plannedFileCount: number
  existingFileCount: number
  missingFileCount: number
  outdatedFileCount: number
  blockers: string[]
  warnings: string[]
  commands: string[]
  files: DevelopmentCodebaseDeploymentFileResponse[]
  summary: string
  generatedAt?: string | null
}

export interface DevelopmentCodebaseDeploymentApplyRequest {
  targetStack?: string | null
  overwriteExisting?: boolean | null
  remarks?: string | null
}

export interface DevelopmentCodebaseDeploymentApplyResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  projectCode: string
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  codebaseRoot: string
  absoluteCodebaseRoot: string
  applied: boolean
  overwriteExisting: boolean
  deploymentStatus: 'READY' | 'WARNING' | 'BLOCKED' | string
  writtenCount: number
  overwrittenCount: number
  skippedCount: number
  blockers: string[]
  warnings: string[]
  files: DevelopmentCodebaseDeploymentFileResponse[]
  summary: string
  appliedAt?: string | null
}

export interface DevelopmentRuntimeActionRequest {
  targetStack?: string | null
  forceRestart?: boolean | null
}

export interface DevelopmentRuntimeStatusResponse {
  projectId: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  targetStack: string
  codebaseRegistry?: DevelopmentCodebaseRegistryResponse | null
  runtimeStatus: 'NOT_READY' | 'STOPPED' | 'RUNNING' | 'START_FAILED' | 'EXITED' | string
  installStatus: 'NOT_READY' | 'NOT_INSTALLED' | 'INSTALLING' | 'INSTALLED' | 'INSTALL_FAILED' | string
  codebaseExists: boolean
  packageJsonExists: boolean
  dependencyInstalled: boolean
  codebaseRoot: string
  absoluteCodebaseRoot: string
  frontendUrl: string
  backendUrl: string
  healthUrl: string
  previewUrl?: string | null
  healthStatus?: 'NOT_CHECKED' | 'STARTING' | 'HEALTHY' | 'DEGRADED' | 'UNREACHABLE' | string | null
  frontendReachable?: boolean | null
  backendReachable?: boolean | null
  frontendStatusCode?: number | null
  backendHealthStatusCode?: number | null
  frontendHealthMessage?: string | null
  backendHealthMessage?: string | null
  lastHealthCheckedAt?: string | null
  diagnosticLevel?: 'PASS' | 'INFO' | 'WARNING' | 'ERROR' | string | null
  diagnosticTitle?: string | null
  diagnosticSummary?: string | null
  detectedProblems?: string[] | null
  diagnosticSuggestions?: string[] | null
  nextActions?: string[] | null
  recentErrorLines?: string[] | null
  processId?: number | null
  installProcessId?: number | null
  lastExitCode?: number | null
  lastInstallExitCode?: number | null
  startedAt?: string | null
  stoppedAt?: string | null
  lastActionAt?: string | null
  allowedActions: string[]
  summary: string
}

export interface DevelopmentRuntimeLogsResponse {
  projectId: string
  moduleId: string
  targetStack: string
  codebaseRoot: string
  runtimeStatus: string
  installStatus: string
  lineCount: number
  truncated: boolean
  lines: string[]
  errorLineCount?: number | null
  warningLineCount?: number | null
  errorLines?: string[] | null
  warningLines?: string[] | null
  diagnosticSuggestions?: string[] | null
  generatedAt?: string | null
}

export interface AgentModelProfileResponse {
  agentScope: string
  llmTask: string
  provider: string
  model: string
  baseUrl: string
  protocol: string
  jsonMode: boolean
  freeQuotaStopEnabled: boolean
  source: string
  apiKeyConfigured: boolean
}

export interface AgentModelRoutingStatusResponse {
  schemaVersion: string
  routingMode: string
  legacy?: AgentModelProfileResponse | null
  requirement?: {
    default?: AgentModelProfileResponse | null
  } | null
  development?: {
    codegen?: AgentModelProfileResponse | null
    fast?: AgentModelProfileResponse | null
    diagnosis?: AgentModelProfileResponse | null
  } | null
  policy?: Record<string, unknown> | null
}

export interface AgentModelRoutingGatewayResponse {
  source: string
  endpoint: string
  fetchedAt?: string | null
  available: boolean
  durationMs?: number | null
  status?: AgentModelRoutingStatusResponse | null
  errorCode?: string | null
  errorMessage?: string | null
  exceptionType?: string | null
}