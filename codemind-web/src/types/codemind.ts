
export interface ModuleTreeNode {
  id?: string
  parentId?: string | null
  moduleKey?: string
  moduleName?: string
  moduleType?: string
  modulePath?: string
  levelNo?: number
  sortOrder?: number
  status?: string
  extra?: Record<string, unknown>
  children?: ModuleTreeNode[]

  pageKind?: string
  routePath?: string
  layoutType?: string
  primaryEntity?: string
  canGenerateUiContract?: boolean
  featureRole?: string

  key?: string
  name?: string
  title?: string
  type?: string
}

export type ModuleTreePatchOperationType =
  | 'ADD_NODE'
  | 'UPDATE_NODE'
  | 'MOVE_NODE'
  | 'DELETE_NODE'

export interface ModuleTreePatchOperationRequest {
  op: ModuleTreePatchOperationType
  nodeId?: string | null
  parentId?: string | null
  newParentId?: string | null
  moduleName?: string
  moduleKey?: string
  moduleType?: string
  sortOrder?: number
  extra?: Record<string, unknown>
}

export interface ApplyModuleTreePatchRequest {
  description?: string
  source?: string
  operations: ModuleTreePatchOperationRequest[]
}

export interface ModuleTreePatchApplyResponse {
  description?: string
  source?: string
  operationCount: number
  appliedOperations: string[]
  tree: ModuleTreeNode[]
}

export interface ProposeModuleTreePatchRequest {
  instruction: string
  selectedNodeId?: string | null
  /** Phase 7-E: selected attachment diff item ids that should drive the proposal. */
  selectedDiffItemIds?: string[]
  /** Phase 7-E3: selected diff item payloads used by backend agents instead of full diff context. */
  selectedDiffItems?: AttachmentArtifactDiffItem[]
}

export interface ModuleTreePatchProposalResponse {
  description?: string
  source?: string
  proposal: {
    description?: string
    source?: string
    operations?: ModuleTreePatchOperationRequest[]
    impact?: {
      summary?: string
      affectedNodeIds?: string[]
      affectedArtifacts?: string[]
      riskNotes?: string[]
    }
    analysis_summary?: string
    [key: string]: unknown
  }
  markdown?: string | null
  requiresUserConfirmation: boolean
}

export interface RequirementChangeTargetNode {
  nodeId?: string | null
  nodeName?: string | null
  nodeType?: string | null
  moduleKey?: string | null
  modulePath?: string | null
}

export interface RequirementChangeAttachmentSummary {
  count: number
  items: Array<Record<string, unknown>>
}

export interface RequirementChangeProposal {
  proposalId?: string | null
  source?: string | null
  status?: string | null
  phase?: string | null
  originalInstruction?: string | null
  summary?: string | null
  changeType?: string | null
  scopeType?: string | null
  /** Phase 7-E: selected attachment diff item ids used as the source of this proposal. */
  selectedDiffItemIds?: string[]
  /** Phase 7-E: selected attachment diff item payloads, kept for backend agent context and audit. */
  selectedDiffItems?: AttachmentArtifactDiffItem[]
  /** Phase 7-E4: target asset baseline captured when the diff card was generated. */
  baseArtifactId?: string | null
  baseVersionNo?: number | null
  baseChecksum?: string | null
  baselineValidationPolicy?: string | null
  targetNode?: RequirementChangeTargetNode | null
  affectedNodeIds?: string[]
  affectedArtifacts?: string[]
  riskNotes?: string[]
  recommendedSteps?: string[]
  requiresUserConfirmation?: boolean
  executionPolicy?: string | null
  executionMode?: string | null
  executionAction?: string | null
  executionActions?: string[]
  nextPhase?: string | null
  attachmentSummary?: RequirementChangeAttachmentSummary | null
  [key: string]: unknown
}



export type AttachmentDrivenChangeExecutionAction =
  | 'MODULE_TREE_PATCH'
  | 'ASSET_EDIT_PROPOSAL'
  | 'GENERATE_REQUIREMENT_ARTIFACT'
  | 'ATTACHMENT_ARTIFACT_DIFF'
  | 'ANALYSIS_ONLY'
  | string

export interface AttachmentDrivenChangeAction {
  type: AttachmentDrivenChangeExecutionAction
  label?: string | null
  description?: string | null
  targetArtifactType?: string | null
  expectedIntent?: AssistantIntent | null
  payload?: Record<string, unknown>
  [key: string]: unknown
}

export interface AttachmentDrivenChangeProposal {
  proposalId?: string | null
  source?: string | null
  status?: string | null
  phase?: string | null
  originalInstruction?: string | null
  summary?: string | null
  materialInstruction?: string | null
  recommendedDispatch?: AttachmentDrivenChangeExecutionAction | null
  executionAction?: AttachmentDrivenChangeExecutionAction | null
  targetNode?: RequirementChangeTargetNode | null
  targetArtifactType?: string | null
  affectedArtifacts?: string[]
  riskNotes?: string[]
  recommendedSteps?: string[]
  candidateActions?: AttachmentDrivenChangeAction[]
  attachmentSummary?: RequirementChangeAttachmentSummary | null
  requiresUserConfirmation?: boolean
  executionPolicy?: string | null
  [key: string]: unknown
}


export interface AttachmentArtifactDiffAction {
  type: 'ASSET_EDIT_PROPOSAL' | 'MODULE_TREE_PATCH' | 'ANALYSIS_ONLY' | string
  label?: string | null
  description?: string | null
  targetArtifactType?: string | null
  expectedIntent?: AssistantIntent | null
  payload?: Record<string, unknown>
  [key: string]: unknown
}

export interface AttachmentArtifactDiffItem {
  id?: string | null
  type?: 'MISSING' | 'POTENTIAL_CONFLICT' | 'ALREADY_COVERED' | 'NEEDS_REVIEW' | string
  title?: string | null
  severity?: 'INFO' | 'WARNING' | 'BLOCKER' | string | null
  attachmentExcerpt?: string | null
  currentEvidence?: string | null
  recommendation?: string | null
  targetArtifactType?: string | null
  canAutoPatch?: boolean
  /**
   * Frontend-only UI state used by Phase 7-E selection controls.
   * The canonical payload field is AttachmentArtifactDiffProposal.selectedDiffItemIds.
   */
  selected?: boolean
  [key: string]: unknown
}

export interface AttachmentArtifactDiffProposal {
  proposalId?: string | null
  source?: string | null
  status?: string | null
  phase?: string | null
  originalInstruction?: string | null
  summary?: string | null
  targetNode?: RequirementChangeTargetNode | null
  targetArtifact?: AssetEditTargetArtifact | null
  targetArtifactType?: string | null
  currentArtifactExcerpt?: string | null
  attachmentExcerpt?: string | null
  attachmentSummary?: RequirementChangeAttachmentSummary | null
  diffItems?: AttachmentArtifactDiffItem[]
  /**
   * Phase 7-E: user-selected diff item ids for follow-up actions.
   * This is optional so historical Phase 7-D messages remain compatible.
   */
  selectedDiffItemIds?: string[]
  /** Phase 7-E protocol fields copied from targetArtifact for later stale-baseline checks. */
  baseArtifactId?: string | null
  baseVersionNo?: number | null
  baseChecksum?: string | null
  missingRequirements?: string[]
  possibleConflicts?: string[]
  alreadyCoveredItems?: string[]
  affectedArtifacts?: string[]
  riskNotes?: string[]
  recommendedSteps?: string[]
  candidateActions?: AttachmentArtifactDiffAction[]
  requiresUserConfirmation?: boolean
  confidence?: number | null
  [key: string]: unknown
}

export interface AssetEditTargetArtifact {
  artifactId?: string | null
  artifactType: string
  artifactTitle?: string | null
  versionNo?: number | null
  status?: string | null
  checksum?: string | null
}

export interface AssetEditProposal {
  proposalId?: string | null
  source?: string | null
  status?: string | null
  originalInstruction?: string | null
  summary?: string | null
  editMode?: string | null
  editPolicy?: string | null
  targetNode?: RequirementChangeTargetNode | null
  targetArtifact?: AssetEditTargetArtifact | null
  targetArtifactType?: string | null
  targetArtifactId?: string | null
  currentVersionNo?: number | null
  currentChecksum?: string | null
  baseArtifactId?: string | null
  baseVersionNo?: number | null
  baseChecksum?: string | null
  baselineValidationPolicy?: string | null
  currentTitle?: string | null
  beforeExcerpt?: string | null
  proposedContentMarkdown?: string | null
  proposedPatchSummary?: string[]
  affectedArtifacts?: string[]
  downstreamArtifacts?: string[]
  riskNotes?: string[]
  recommendedSteps?: string[]
  requiresUserConfirmation?: boolean
  createdAt?: string | null
  sourceRequirementChangeProposal?: RequirementChangeProposal | null
  selectedDiffItemIds?: string[]
  selectedDiffItems?: AttachmentArtifactDiffItem[]
  [key: string]: unknown
}

export interface ConfirmAssistantAssetEditProposalResponse {
  artifactId: string
  projectId?: string | null
  moduleId?: string | null
  artifactType: string
  versionNo: number
  title?: string | null
  status?: string | null
  previousArtifactId?: string | null
  previousVersionNo?: number | null
  affectedArtifacts: string[]
  downstreamMarkedReviewCount: number
  message: string
}

export type AssistantIntent =
  | 'GENERATE_MODULE_TREE'
  | 'PROPOSE_MODULE_TREE_PATCH'
  | 'PROPOSE_REQUIREMENT_CHANGE'
  | 'PROPOSE_ASSET_EDIT'
  | 'CONFIRM_ASSET_EDIT'
  | 'EXECUTE_UI_COMMAND'
  | 'GENERATE_REQUIREMENT_ARTIFACT'
  | 'EXPLAIN_CURRENT_ARTIFACT'
  | 'ANALYZE_ATTACHMENT'
  | 'GENERAL_QA'
  | string

export type AssistantAttachmentParseStatus =
  | 'PENDING'
  | 'PARSING'
  | 'PARSED'
  | 'FAILED'
  | string

export interface AssistantAttachmentRef {
  attachmentId?: string | null
  fileName?: string
  mimeType?: string
  size?: number
  parseStatus?: AssistantAttachmentParseStatus | null
  parsedTextExcerpt?: string | null
  metadata?: Record<string, unknown>
}

export interface AssistantAttachmentUploadResponse {
  attachmentId: string
  fileName: string
  mimeType: string
  size: number
  parseStatus: AssistantAttachmentParseStatus
  parsedTextExcerpt?: string | null
  metadata?: Record<string, unknown>
}

export interface AssistantMessageRequest {
  message: string
  selectedNodeId?: string | null
  selectedArtifactType?: string | null
  expectedIntent?: AssistantIntent | null
  attachments?: AssistantAttachmentRef[]
  /** Phase 7-E: selected diff item ids from an attachment-artifact-diff card. */
  selectedDiffItemIds?: string[]
  /** Phase 7-E3: selected diff item payloads for selective backend proposal generation. */
  selectedDiffItems?: AttachmentArtifactDiffItem[]
  /** Phase 7-E4: asset baseline used to block stale follow-up actions. */
  baseArtifactId?: string | null
  baseVersionNo?: number | null
  baseChecksum?: string | null
}

export interface AssistantActionResponse {
  type: string
  label: string
  payload?: Record<string, unknown>
}

export interface AssistantMessageResponse {
  messageId: string
  intent: AssistantIntent
  reply: string
  requiresUserConfirmation: boolean
  proposal?: ModuleTreePatchProposalResponse | null
  requirementChangeProposal?: RequirementChangeProposal | null
  assetEditProposal?: AssetEditProposal | null
  attachmentDrivenChangeProposal?: AttachmentDrivenChangeProposal | null
  attachmentArtifactDiffProposal?: AttachmentArtifactDiffProposal | null
  actions: AssistantActionResponse[]
}

export interface AssistantConversationMessageResponse {
  messageId: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | string
  intent?: AssistantIntent | null
  selectedNodeId?: string | null
  selectedArtifactType?: string | null
  type?: 'TEXT' | 'TREE_PATCH_PROPOSAL' | 'REQUIREMENT_CHANGE_PROPOSAL' | 'ASSET_EDIT_PROPOSAL' | 'ATTACHMENT_CHANGE_PROPOSAL' | 'ATTACHMENT_ARTIFACT_DIFF_PROPOSAL' | 'TASK_NOTIFICATION' | string
  text?: string | null
  attachments?: AssistantAttachmentRef[]
  proposal?: ModuleTreePatchProposalResponse | null
  requirementChangeProposal?: RequirementChangeProposal | null
  assetEditProposal?: AssetEditProposal | null
  attachmentDrivenChangeProposal?: AttachmentDrivenChangeProposal | null
  attachmentArtifactDiffProposal?: AttachmentArtifactDiffProposal | null
  content?: Record<string, unknown>
  createdAt?: string | null
}

export interface AssistantConversationArchiveResponse {
  archiveMessageId: string
  archivedMessageCount: number
  archivedAt?: string | null
  message: string
}

export interface AssistantTaskNotificationRequest {
  taskId: string
  moduleId?: string | null
  resultArtifactId?: string | null
  taskType: string
  artifactType?: string | null
  artifactTitle?: string | null
  status: AgentTaskStatus
  errorCode?: string | null
  errorMessage?: string | null
}

export interface CreateProjectModuleNodeRequest {
  parentId?: string | null
  moduleName: string
  moduleKey?: string
  moduleType: string
  sortOrder?: number
  extra?: Record<string, unknown>
}

export interface UpdateProjectModuleNodeRequest {
  moduleName?: string
  moduleKey?: string
  moduleType?: string
  sortOrder?: number
  extra?: Record<string, unknown>
}

export interface MoveProjectModuleNodeRequest {
  parentId?: string | null
  sortOrder?: number
}

export interface ProjectModuleMutationResponse {
  node?: ModuleTreeNode | null
  tree: ModuleTreeNode[]
}

export interface GenerateModuleTreeRequest {
  requirementText: string
}

export interface GenerateModulePrdRequest {
  requirementText: string
}

export interface ArtifactResponse extends PromptTemplateMetadata{
  exists?: boolean
  taskId?: string
  artifactId?: string
  projectId?: string
  moduleId?: string
  moduleKey?: string
  moduleName?: string
  artifactType?: string
  versionNo?: number
  title?: string
  status?: string
  current?: boolean
  content?: Record<string, unknown>
  contentMarkdown?: string
  createdAt?: string
  updatedAt?: string
}

export interface ArtifactVersionSummaryResponse extends PromptTemplateMetadata{
  artifactId: string
  projectId?: string | null
  moduleId?: string | null
  taskId?: string | null
  artifactType: string
  versionNo: number
  title?: string | null
  status?: string | null
  current?: boolean | null
  createdBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ArtifactVersionDetailResponse extends PromptTemplateMetadata{
  exists?: boolean
  artifactId: string
  projectId?: string | null
  moduleId?: string | null
  taskId?: string | null
  artifactType: string
  versionNo: number
  title?: string | null
  status?: string | null
  current?: boolean | null
  content?: Record<string, unknown> | null
  contentMarkdown?: string | null
  createdBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}


export interface ManualArtifactEditPayload {
  artifactId?: string | null
  artifactType: string
  contentMarkdown: string
  reason?: string | null
}

export interface ManualArtifactEditRequest {
  artifactId?: string | null
  artifactType: string
  contentMarkdown: string
  reason?: string | null
}

export interface ManualArtifactEditResponse extends PromptTemplateMetadata {
  artifactId: string
  projectId?: string | null
  moduleId?: string | null
  artifactType: string
  versionNo: number
  title?: string | null
  status?: string | null
  current?: boolean | null
  content?: Record<string, unknown> | null
  contentMarkdown?: string | null
  createdBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  downstreamArtifactTypes?: string[]
  downstreamMarkedCount?: number
  message?: string | null
}

export interface ArtifactVersionListResponse {
  projectId: string
  moduleId?: string | null
  artifactType: string
  versions: ArtifactVersionSummaryResponse[]
}

export interface WorkspaceSummary {
  workspaceId: string
  workspaceCode: string
  workspaceName: string
  workspaceType: 'PERSONAL' | 'TEAM' | string
  memberRole: string
  status: string
}

export interface ProjectSummary {
  projectId: string
  projectCode: string
  projectName: string
  description?: string
  status: string
  workspaceId: string
  workspaceCode: string
  visibility: string
  moduleCount: number
  artifactCount: number
  latestArtifactAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface CreateProjectRequest {
  projectCode: string
  projectName: string
  description?: string
  techStack?: Record<string, unknown>
}

export interface ProjectWorkspaceState {
  exists: boolean
  workspaceId: string
  projectId: string
  userId: string
  selectedModuleId?: string | null
  selectedArtifactType?: string | null
  selectedArtifactId?: string | null
  activeAgentTab?: AgentTab | string
  uiState?: Record<string, unknown>
  updatedAt?: string | null
}

export interface SaveProjectWorkspaceStateRequest {
  selectedModuleId?: string | null
  selectedArtifactType?: string | null
  selectedArtifactId?: string | null
  activeAgentTab?: AgentTab | string
  uiState?: Record<string, unknown>
}

export interface AssistantStep {
  id: string
  title: string
  description: string
  status: 'pending' | 'running' | 'success' | 'failed'
}

export type AgentTab = 'requirement' | 'development' | 'testing'

export type AppPage = 'home' | 'workspace'

export interface LoginRequest {
  account: string
  password: string
}

export interface CurrentUser {
  userId: string
  username: string
  displayName: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: CurrentUser
}
export interface GenerateBusinessFlowRequest {
  requirementText: string
}

export interface GeneratePageSpecRequest {
  requirementText: string
}
export interface GenerateDataModelRequest {
  requirementText: string
}

export interface GenerateApiContractRequest {
  requirementText: string
}

export interface GenerateTestSpecRequest {
  requirementText: string
}

export interface GenerateDevHandoffPackageRequest {
  requirementText: string
}

export interface GenerateFullChainRequest {
  requirementText: string
  overwriteExisting?: boolean
}

export interface FullChainTaskItemResponse {
  taskId: string
  projectId: string
  moduleId?: string | null
  taskType: string
  artifactType: string
  title: string
  status: AgentTaskStatus
  createdAt?: string
  reused: boolean
}

export interface FullChainSkippedArtifactResponse {
  projectId: string
  moduleId?: string | null
  taskType: string
  artifactType: string
  title: string
  reason: string
}

export interface FullChainAsyncTaskResponse {
  projectId: string
  projectCode: string
  moduleId: string
  moduleKey?: string | null
  moduleName?: string | null
  overwriteExisting: boolean
  createdTasks: FullChainTaskItemResponse[]
  reusedTasks: FullChainTaskItemResponse[]
  skippedArtifacts: FullChainSkippedArtifactResponse[]
  totalTaskCount: number
  createdAt?: string
}

export interface GenerateBatchFullChainRequest {
  moduleIds: string[]
  requirementText: string
  overwriteExisting?: boolean
}

export interface BatchFullChainFailedModuleResponse {
  moduleId: string
  errorCode: string
  errorMessage?: string | null
}

export interface BatchFullChainAsyncTaskResponse {
  projectId: string
  projectCode: string
  requestedModuleCount: number
  successModuleCount: number
  failedModuleCount: number
  totalCreatedTaskCount: number
  totalReusedTaskCount: number
  totalSkippedArtifactCount: number
  overwriteExisting: boolean
  results: FullChainAsyncTaskResponse[]
  failedModules: BatchFullChainFailedModuleResponse[]
  createdAt?: string
}

export type AgentTaskStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'RETRYING'
  | 'CANCELLED'
  | 'CONFLICT'
  | 'BLOCKED'
  | 'PARTIAL_SUCCESS'
  | string

export interface UiContractFeatureReadinessItem {
  featureId: string
  featureKey?: string | null
  featureName: string
  complete: boolean
  definitionSource?: string | null
  missingFields: string[]
}

export interface UiContractGenerationReadinessResponse {
  ready: boolean
  pageId: string
  pageKey?: string | null
  pageName: string
  pageKind: string
  pageSpecAvailable: boolean
  featureCount: number
  completeFeatureCount: number
  features: UiContractFeatureReadinessItem[]
  blockers: string[]
  warnings: string[]
}

export interface AsyncAgentTaskResponse {
  taskId: string
  projectId: string
  projectCode: string
  moduleId?: string | null
  taskType: string
  status: AgentTaskStatus
  createdAt?: string
  reused?: boolean
}

export interface AgentTaskSummary extends PromptTemplateMetadata{
  id: string
  projectId?: string
  moduleId?: string | null
  taskType: string
  status: AgentTaskStatus
  resultArtifactId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  queueName?: string
  priority?: number
  attemptCount?: number
  maxAttempts?: number
  nextRunAt?: string | null
  lockedBy?: string | null
  lockedUntil?: string | null
  heartbeatAt?: string | null
  createdBy?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  hasRequestPayload?: boolean
  hasResponsePayload?: boolean
  requirementTextPreview?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface AgentTaskPayloadResponse extends PromptTemplateMetadata{
  id: string
  projectId?: string | null
  moduleId?: string | null
  taskType: string
  status: AgentTaskStatus
  requestPayload?: Record<string, unknown> | null
  responsePayload?: Record<string, unknown> | null
  resultArtifactId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  queueName?: string | null
  attemptCount?: number | null
  maxAttempts?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ModuleArtifactTaskItem {
  artifactType: string
  taskType: string
  title: string
  artifactExists: boolean
  artifactVersionNo?: number
  artifactStatus?: string | null
  task?: AgentTaskSummary | null
  unavailableReason?: string | null
}

export interface ProjectAgentTaskStatusSummary {
  projectId: string
  total: number
  pending: number
  queued: number
  running: number
  retrying: number
  blocked: number
  success: number
  partialSuccess: number
  failed: number
  timeout: number
  cancelled: number
  active: number
  abnormal: number
  terminal: number
  statusCounts: Record<string, number>
  generatedAt?: string
}

export interface ProjectAgentTaskPageQuery {
  status?: string | null
  taskType?: string | null
  moduleId?: string | null
  createdBy?: string | null
  page?: number
  size?: number
  sort?: string
}

export interface ProjectAgentTaskPageItem extends PromptTemplateMetadata{
  id: string
  projectId?: string | null
  moduleId?: string | null
  taskType: string
  taskTitle?: string | null
  artifactType?: string | null
  artifactTitle?: string | null
  status: AgentTaskStatus
  statusText?: string | null
  resultArtifactId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  displayMessage?: string | null
  queueName?: string | null
  priority?: number | null
  attemptCount?: number | null
  maxAttempts?: number | null
  nextRunAt?: string | null
  lockedBy?: string | null
  lockedUntil?: string | null
  heartbeatAt?: string | null
  createdBy?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  hasRequestPayload?: boolean
  hasResponsePayload?: boolean
  requirementTextPreview?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ProjectAgentTaskPage {
  content: ProjectAgentTaskPageItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  status?: string | null
  taskType?: string | null
  moduleId?: string | null
  createdBy?: string | null
  sort?: string | null
  generatedAt?: string
}

export interface ProjectModuleTaskMatrixItem {
  artifactType: string
  artifactTitle: string
  taskType: string
  taskTitle: string
  sortOrder: number
  artifactExists: boolean
  artifactId?: string | null
  artifactVersionNo?: number | null
  artifactStatus?: string | null
  artifactUpdatedAt?: string | null
  latestTaskId?: string | null
  latestTaskStatus?: AgentTaskStatus | null
  statusText?: string | null
  displayMessage?: string | null
  errorCode?: string | null
  lastErrorCode?: string | null
  attemptCount?: number | null
  maxAttempts?: number | null
  taskCreatedAt?: string | null
  taskUpdatedAt?: string | null
  taskStartedAt?: string | null
  taskFinishedAt?: string | null
}

export interface ProjectModuleTaskMatrixModule {
  moduleId: string
  parentId?: string | null
  moduleKey?: string | null
  moduleName: string
  moduleType?: string | null
  modulePath?: string | null
  levelNo?: number | null
  sortOrder?: number | null
  status?: string | null
  items: ProjectModuleTaskMatrixItem[]
}

export interface ProjectAgentTaskMatrix {
  projectId: string
  modules: ProjectModuleTaskMatrixModule[]
  generatedAt?: string
}

export interface PromptTemplateMetadata {
  promptTemplateId?: string | null
  promptTemplateVersionId?: string | null
  promptTemplateKey?: string | null
  promptTemplateVersion?: string | null
  promptTemplateChecksum?: string | null
}

export interface ArtifactQualityIssue {
  id: string
  level: 'blocker' | 'warning' | 'pass'
  title: string
  description: string
  artifactType: string
}

export interface ArtifactQualityAsset {
  artifactType: string
  title: string
  exists: boolean
  versionNo?: number | null
  status?: string | null
  current?: boolean | null
  updatedAt?: string | null
  level: 'pass' | 'warning' | 'blocker'
  statusText: string
}

export interface ArtifactQualityCheckResult {
  score: number
  generatedAt: string
  assets: ArtifactQualityAsset[]
  blockers: ArtifactQualityIssue[]
  warnings: ArtifactQualityIssue[]
  passes: ArtifactQualityIssue[]
}

export interface ArtifactQualityCheckRecordResponse {
  id: string
  projectId: string
  moduleId?: string | null
  checkScope: string
  score: number
  assetCount: number
  blockerCount: number
  warningCount: number
  passCount: number
  exportDecision: string
  forcedExport: boolean
  source: string
  resultJson: ArtifactQualityCheckResult
  checkedBy?: string | null
  checkedAt: string
}
