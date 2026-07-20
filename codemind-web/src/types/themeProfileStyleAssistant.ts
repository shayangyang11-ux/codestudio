import type {
  ProjectIndustry,
  ProjectLoginLayout,
  ProjectNavigationStyle,
  ProjectThemeDensity,
  ProjectThemeKey,
  ProjectThemeRadius,
  ProjectVisualTone,
  UpdateProjectThemeProfileRequest,
} from './themeProfile'

export type ProjectStyleAnalysisMode =
  | 'STYLE_INTENT_TEXT'
  | 'REFERENCE_TEXT'
  | 'REFERENCE_IMAGE'
  | 'MIXED_REFERENCE'
  | 'REFERENCE_IMAGE_PENDING'
  | 'MIXED_REFERENCE_PENDING'
  | 'JAVA_RULE_FALLBACK'
  | 'PYTHON_RULE_FALLBACK'

export interface ProjectStyleAssistantRequest {
  instruction: string
  referenceText?: string | null
  referenceNotes?: string | null
  referenceMode?: 'AUTO' | 'STYLE_INTENT' | 'REFERENCE_TEXT' | 'REFERENCE_IMAGE' | 'MIXED_REFERENCE'
  targetPageType?: string | null
  allowDarkMode?: boolean | null
  attachmentIds?: string[]
  attachmentNames?: string[]
}

export interface ProjectStyleAssistantResponse {
  industry: ProjectIndustry
  themeKey: ProjectThemeKey
  visualTone: ProjectVisualTone
  primaryColor: string
  secondaryColor?: string | null
  accentColor?: string | null
  density: ProjectThemeDensity
  radius: ProjectThemeRadius
  loginLayout: ProjectLoginLayout
  navigationStyle: ProjectNavigationStyle
  darkModeEnabled: boolean
  analysisMode: ProjectStyleAnalysisMode
  agentMode?: string | null
  attachmentIds: string[]
  attachmentNames: string[]
  extractedSignals: Record<string, unknown>
  confidence: number
  summary: string
  reasons: string[]
  warnings: string[]
  updatePayload: UpdateProjectThemeProfileRequest
}
