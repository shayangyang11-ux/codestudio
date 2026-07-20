import type { ProjectThemeProfile } from './themeProfile'

export type ProjectThemeProfileApplicationSource =
  | 'MANUAL'
  | 'STYLE_INTENT_AGENT'
  | 'STYLE_INTENT_RULE_FALLBACK'
  | 'REFERENCE_STYLE_AGENT'
  | 'SYSTEM'
  | 'THEME_PROFILE_ROLLBACK'

export interface ProjectThemeProfileApplicationLogRequest {
  source: ProjectThemeProfileApplicationSource
  styleText?: string | null
  targetPageType?: string | null
  agentMode?: string | null
  confidence?: number | null
  summary?: string | null
  beforeProfile?: Record<string, unknown> | null
  suggestion?: Record<string, unknown> | null
  updatePayload?: Record<string, unknown> | null
  resultProfile?: Record<string, unknown> | null
  reasons?: string[] | null
  warnings?: string[] | null
}

export interface ProjectThemeProfileApplicationLogResponse {
  id: string
  workspaceId: string
  projectId?: string | null
  projectCode: string
  source: ProjectThemeProfileApplicationSource
  styleText?: string | null
  targetPageType?: string | null
  agentMode?: string | null
  confidence?: number | null
  summary?: string | null
  beforeProfile: Record<string, unknown>
  suggestion: Record<string, unknown>
  updatePayload: Record<string, unknown>
  resultProfile: Record<string, unknown>
  changedFields: string[]
  reasons: string[]
  warnings: string[]
  createdAt: string
}


export interface ProjectThemeProfileRollbackResponse {
  sourceLog: ProjectThemeProfileApplicationLogResponse
  rollbackLog: ProjectThemeProfileApplicationLogResponse
  resultProfile: ProjectThemeProfile
}
