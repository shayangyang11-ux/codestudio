// A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK
import type {
  UiContractComponent,
  UiContractContent,
} from '../../types/uiContract'
import type { ProjectThemeProfile } from '../../types/themeProfile'

import { inferSurface } from '../../domain/pageSemantics'

export type LooseRecord = Record<string, any>

export type UiRuntimeThemeKey =
  | 'enterprise_default'
  | 'tech_modern'
  | 'commerce_light'
  | 'industrial_pro'
  | 'finance_stable'
  | 'gov_clean'

export type UiRuntimePagePattern =
  | 'LOGIN_FORM'
  | 'REGISTER_FORM'
  | 'SEARCH_LIST'
  | 'CRUD_LIST'
  | 'DASHBOARD'
  | 'PROFILE_PAGE'
  | 'DETAIL_PAGE'
  | 'EDIT_FORM'
  | 'SETTINGS_FORM'
  | 'UPLOAD_FORM'
  | 'WORKFLOW_STEP'
  | 'GENERIC_PAGE'

export interface UiRuntimeThemeProfile {
  key: UiRuntimeThemeKey
  label: string
  tone: string
  primary: string
  secondary: string
  accent: string
  pageBg: string
  surface: string
  textMain: string
  textMuted: string
  border: string
  radius: string
  density: 'compact' | 'standard' | 'comfortable'
  buttonShape: 'soft' | 'rounded' | 'pill'
  visual: 'clean' | 'commerce' | 'tech' | 'industrial' | 'finance' | 'gov'
}


// A9.10.14.4.3.15.4.3_RUNTIME_INDUSTRY_STYLE_RENDER
export interface UiRuntimeIndustryStyle {
  code: string
  name: string
  cssClass: string
  source: string
  autoInferred: boolean
  userOverrideLocked: boolean
  confidence: number
  pageType: string
  visualTone: string
  density: UiRuntimeThemeProfile['density']
  radius: string
  navigationStyle: string
  matchedSignals: string[]
}

export interface UiRuntimeRenderContext {
  pattern: UiRuntimePagePattern
  theme: UiRuntimeThemeProfile
  pageTitle: string
  pageSubtitle: string
  visibleTitle: string
  brandTitle: string
  brandSubtitle: string
  brandBullets: string[]
  primaryActionText: string
  industryStyle: UiRuntimeIndustryStyle
}

const THEME_PROFILES: Record<UiRuntimeThemeKey, UiRuntimeThemeProfile> = {
  enterprise_default: {
    key: 'enterprise_default',
    label: '通用企业',
    tone: '清晰、稳定、面向企业业务系统',
    primary: '#2563eb',
    secondary: '#0ea5e9',
    accent: '#7c3aed',
    pageBg: '#f5f7fb',
    surface: '#ffffff',
    textMain: '#0f172a',
    textMuted: '#64748b',
    border: '#dbe4ef',
    radius: '18px',
    density: 'standard',
    buttonShape: 'soft',
    visual: 'clean',
  },
  tech_modern: {
    key: 'tech_modern',
    label: '科技平台',
    tone: '现代、智能、数据感',
    primary: '#4f46e5',
    secondary: '#06b6d4',
    accent: '#22c55e',
    pageBg: '#eef2ff',
    surface: '#ffffff',
    textMain: '#0f172a',
    textMuted: '#64748b',
    border: '#dbe4ef',
    radius: '22px',
    density: 'comfortable',
    buttonShape: 'rounded',
    visual: 'tech',
  },
  commerce_light: {
    key: 'commerce_light',
    label: '电商零售',
    tone: '清爽、亲和、转化导向',
    primary: '#2563eb',
    secondary: '#f97316',
    accent: '#fb7185',
    pageBg: '#fff7ed',
    surface: '#ffffff',
    textMain: '#111827',
    textMuted: '#6b7280',
    border: '#fed7aa',
    radius: '20px',
    density: 'comfortable',
    buttonShape: 'rounded',
    visual: 'commerce',
  },
  industrial_pro: {
    key: 'industrial_pro',
    label: '工业制造',
    tone: '稳健、理性、强调状态和数据',
    primary: '#0f766e',
    secondary: '#2563eb',
    accent: '#f59e0b',
    pageBg: '#edf3f5',
    surface: '#ffffff',
    textMain: '#0f172a',
    textMuted: '#64748b',
    border: '#cbd5e1',
    radius: '14px',
    density: 'compact',
    buttonShape: 'soft',
    visual: 'industrial',
  },
  finance_stable: {
    key: 'finance_stable',
    label: '金融风控',
    tone: '克制、可信、强调风险与数据',
    primary: '#1e3a8a',
    secondary: '#b45309',
    accent: '#059669',
    pageBg: '#f8fafc',
    surface: '#ffffff',
    textMain: '#0f172a',
    textMuted: '#64748b',
    border: '#dbe4ef',
    radius: '14px',
    density: 'standard',
    buttonShape: 'soft',
    visual: 'finance',
  },
  gov_clean: {
    key: 'gov_clean',
    label: '政务公共',
    tone: '严肃、规整、可读性优先',
    primary: '#1d4ed8',
    secondary: '#475569',
    accent: '#0369a1',
    pageBg: '#f8fafc',
    surface: '#ffffff',
    textMain: '#111827',
    textMuted: '#64748b',
    border: '#dbe4ef',
    radius: '12px',
    density: 'standard',
    buttonShape: 'soft',
    visual: 'gov',
  },
}

export function resolveUiRuntimeContext(
  contract: UiContractContent,
  projectThemeProfile?: ProjectThemeProfile | null,
): UiRuntimeRenderContext {
  const pattern = resolvePagePattern(contract)
  const theme = resolveThemeProfile(
    contract,
    projectThemeProfile,
  )
  const source = contract as UiContractContent & LooseRecord
  const page = (source.page || {}) as LooseRecord

  const pageTitle = safeText(
    page.title ||
      page.pageName ||
      page.pageKey ||
      '页面预览',
  )

  const pageSubtitle = cleanDisplayText(
    page.description ||
      source.analysisSummary ||
      '',
  )

  return {
    pattern,
    theme,
    pageTitle,
    pageSubtitle,
    visibleTitle: resolveVisibleTitle(contract, pattern, pageTitle),
    brandTitle: brandTitle(contract),
    brandSubtitle: brandSubtitle(contract, theme),
    brandBullets: brandBullets(pattern, theme),
    primaryActionText: primaryActionText(contract, pattern),
    industryStyle: resolveIndustryStyle(contract, projectThemeProfile),
  }
}

export function resolvePagePattern(
  contract: UiContractContent,
): UiRuntimePagePattern {
  const source = contract as UiContractContent & LooseRecord
  const page = (source.page || {}) as LooseRecord

  const identityText = joinSearchText([
    page.pageKind,
    getLayoutType(contract),
    page.title,
    page.pageName,
    page.pageKey,
    page.description,
  ]).toLowerCase()

  const componentTextValue = joinSearchText(
    ((page.componentTree || []) as UiContractComponent[])
      .map((component) => componentText(component)),
  ).toLowerCase()

  const featureText = joinSearchText(
    ((source.featureCoverage || []) as LooseRecord[])
      .map((item) => `${item.featureName || ''} ${item.featureKey || ''} ${item.status || ''}`),
  ).toLowerCase()

  const businessText = [
    identityText,
    componentTextValue,
    source.analysisSummary || '',
  ].join(' ').toLowerCase()

  const loginIdentity = hasAny(identityText, ['登录页', '登录页面', 'login_page', 'login page', 'signin_page', 'sign in page']) ||
    ['login', 'login_page', 'signin', 'sign_in', 'auth_login'].includes(String(page.pageKey || '').toLowerCase()) ||
    ['login', 'login_page', 'auth_login'].includes(String(page.pageKind || '').toLowerCase())

  const registerIdentity = hasAny(identityText, ['注册页', '注册页面', 'register_page', 'register page', 'signup_page', 'sign up page']) ||
    ['register', 'register_page', 'signup', 'sign_up'].includes(String(page.pageKey || '').toLowerCase()) ||
    ['register', 'register_page', 'auth_register'].includes(String(page.pageKind || '').toLowerCase())

  const identityNegative = hasAny(identityText, ['退出登录', '登录状态', '状态守卫', 'session', 'logout', '用户首页', '首页', 'home', '工作台'])

  // Important:
  // FEATURE names such as “登录状态守卫” must not turn a business PAGE into LOGIN_FORM.
  if (loginIdentity && !identityNegative) return 'LOGIN_FORM'
  if (registerIdentity && !identityNegative) return 'REGISTER_FORM'

  const pageKind = String(page.pageKind || '').toUpperCase()
  const layoutType = String(getLayoutType(contract) || '').toUpperCase()
  const surface = inferSurface(businessText)

  switch (surface) {
    case 'APPROVAL': return 'WORKFLOW_STEP'
    case 'DETAIL': return 'DETAIL_PAGE'
    case 'UPLOAD': return 'UPLOAD_FORM'
    case 'FORM': return 'EDIT_FORM'
    case 'DASHBOARD': return 'DASHBOARD'
    case 'CONFIG': return 'SETTINGS_FORM'
    case 'PORTAL': return 'DASHBOARD'
    case 'WIZARD': return 'EDIT_FORM'
    case 'SEARCH_LIST': return 'SEARCH_LIST'
    case 'LIST': return 'CRUD_LIST'
    default: return 'GENERIC_PAGE'
  }
}

export function resolveThemeProfile(
  contract: UiContractContent,
  projectThemeProfile?: ProjectThemeProfile | null,
): UiRuntimeThemeProfile {
  const embeddedStyleProfile = resolveEmbeddedIndustryStyleProfile(contract)

  // Manual project-level override wins.  This preserves the product rule:
  // auto-detect by default, but never overwrite a user-locked style.
  if (projectThemeProfile?.locked) {
    return resolveThemeProfileFromProjectProfile(projectThemeProfile)
  }

  // 15.4.2 writes the effective auto/user style into the UI_CONTRACT payload.
  // Runtime should honor that stable per-contract signal so regenerated pages do
  // not drift between industries.
  if (embeddedStyleProfile) {
    return resolveThemeProfileFromIndustryStyle(
      embeddedStyleProfile,
      projectThemeProfile,
    )
  }

  if (projectThemeProfile) {
    return resolveThemeProfileFromProjectProfile(projectThemeProfile)
  }

  const source = contract as UiContractContent & LooseRecord
  const page = (source.page || {}) as LooseRecord

  const text = joinSearchText([
    page.pageName,
    page.title,
    page.description,
    source.analysisSummary,
    Array.isArray(source.assumptions) ? source.assumptions.join(' ') : '',
    Array.isArray(source.featureCoverage)
      ? source.featureCoverage
          .map((item: LooseRecord) => `${item.featureName || ''} ${item.featureKey || ''}`)
          .join(' ')
      : '',
  ]).toLowerCase()

  if (hasAny(text, ['电商', '商城', '零售', '订单', '商品', '购物', '用户中心', '会员', 'ecommerce', 'shop', 'mall', 'order'])) return THEME_PROFILES.commerce_light
  if (hasAny(text, ['仓库', '仓储', '库存', '入库', '出库', '盘点', '库位', 'wms', 'warehouse', 'inventory', 'industrial', '工业', '制造', '设备', '产线', '工厂', 'mes', 'iot', '传感器', '监控'])) return THEME_PROFILES.industrial_pro
  if (hasAny(text, ['金融', '风控', '银行', '支付', '交易', '账务', 'finance', 'risk'])) return THEME_PROFILES.finance_stable
  if (hasAny(text, ['政务', '政府', '公共服务', '监管', 'gov'])) return THEME_PROFILES.gov_clean
  if (hasAny(text, ['工单', '派单', '客服', '维修', 'sla', 'ticket', 'service desk', '科技', '智能', 'ai', '算法', '数据平台', 'saas', '云平台', '大数据', '数字化'])) return THEME_PROFILES.tech_modern

  return THEME_PROFILES.enterprise_default
}


// A9.10.14.4.3.15.4.3_RUNTIME_INDUSTRY_STYLE_RENDER
export function resolveEmbeddedIndustryStyleProfile(
  contract: UiContractContent,
): LooseRecord | null {
  const source = contract as UiContractContent & LooseRecord
  const mockData = isLooseRecord(source.mockData) ? source.mockData : {}
  const candidates = [
    source.industryStyleProfile,
    source.a9IndustryStyleProfile,
    mockData.__a9IndustryStyleProfile,
    mockData.industryStyleProfile,
    findFirstComponentIndustryStyle((source.page as LooseRecord | undefined)?.componentTree),
  ]

  for (const candidate of candidates) {
    if (isLooseRecord(candidate)) {
      const code = safeText(candidate.industryCode || candidate.industry || candidate.visualTone)
      const marker = safeText(candidate.marker)
      if (code || marker.includes('15.4.2') || marker.includes('INDUSTRY_STYLE')) {
        return candidate
      }
    }
  }

  return null
}

function findFirstComponentIndustryStyle(
  value: unknown,
): LooseRecord | null {
  if (Array.isArray(value)) {
    for (const item of value) {
      const matched = findFirstComponentIndustryStyle(item)
      if (matched) return matched
    }
    return null
  }

  if (!isLooseRecord(value)) return null

  const props = isLooseRecord(value.props) ? value.props : {}
  if (isLooseRecord(props.a9IndustryStyle)) {
    return props.a9IndustryStyle
  }

  const children = findFirstComponentIndustryStyle(value.children)
  if (children) return children

  if (Array.isArray(value.tabs)) {
    for (const tab of value.tabs) {
      const tabRecord = isLooseRecord(tab) ? tab : {}
      const matched =
        findFirstComponentIndustryStyle(tabRecord.children) ||
        findFirstComponentIndustryStyle(tabRecord.components) ||
        findFirstComponentIndustryStyle(tabRecord.items)
      if (matched) return matched
    }
  }

  return null
}

function resolveThemeProfileFromIndustryStyle(
  styleProfile: LooseRecord,
  projectThemeProfile?: ProjectThemeProfile | null,
): UiRuntimeThemeProfile {
  const themeKey = safeText(styleProfile.themeKey) as UiRuntimeThemeKey
  const fallback =
    THEME_PROFILES[themeKey] ||
    (projectThemeProfile?.themeKey
      ? THEME_PROFILES[projectThemeProfile.themeKey as UiRuntimeThemeKey]
      : null) ||
    THEME_PROFILES.enterprise_default

  const palette = isLooseRecord(styleProfile.palette) ? styleProfile.palette : {}
  const visualTone = safeText(styleProfile.visualTone || styleProfile.visual || fallback.visual)
  const density = normalizeRuntimeDensity(styleProfile.density || fallback.density)
  const radius = normalizeRuntimeRadius(styleProfile.radius || fallback.radius)

  return {
    ...fallback,
    key: fallback.key,
    label: safeText(styleProfile.industryName || styleProfile.label) || fallback.label,
    tone:
      safeText(styleProfile.pageTypeStyleRules) ||
      safeText((Array.isArray(styleProfile.industryRules) ? styleProfile.industryRules[0] : '')) ||
      fallback.tone,
    primary: normalizeColor(palette.primary, fallback.primary),
    secondary: normalizeColor(palette.secondary, fallback.secondary),
    accent: normalizeColor(palette.accent, fallback.accent),
    pageBg: normalizeColor(palette.background, fallback.pageBg),
    surface: normalizeColor(palette.surface, fallback.surface),
    density,
    radius,
    buttonShape:
      radius === '22px'
        ? 'rounded'
        : density === 'compact'
          ? 'soft'
          : fallback.buttonShape,
    visual: normalizeRuntimeVisual(visualTone, fallback.visual),
  }
}

export function resolveIndustryStyle(
  contract: UiContractContent,
  projectThemeProfile?: ProjectThemeProfile | null,
): UiRuntimeIndustryStyle {
  if (projectThemeProfile?.locked) {
    return industryStyleFromProjectProfile(projectThemeProfile)
  }

  const embeddedStyleProfile = resolveEmbeddedIndustryStyleProfile(contract)
  if (embeddedStyleProfile) {
    return industryStyleFromEmbeddedProfile(embeddedStyleProfile)
  }

  if (projectThemeProfile) {
    return industryStyleFromProjectProfile(projectThemeProfile)
  }

  return {
    code: 'GENERAL',
    name: '通用企业系统',
    cssClass: 'general',
    source: 'RUNTIME_FALLBACK',
    autoInferred: false,
    userOverrideLocked: false,
    confidence: 0,
    pageType: '',
    visualTone: 'CLEAN',
    density: 'standard',
    radius: '18px',
    navigationStyle: 'SIDE_NAV',
    matchedSignals: [],
  }
}

function industryStyleFromEmbeddedProfile(
  profile: LooseRecord,
): UiRuntimeIndustryStyle {
  const rawCode = safeText(profile.industryCode || profile.industry || profile.inferredIndustryCode || 'GENERAL')
  const code = rawCode.toUpperCase() || 'GENERAL'
  const matchedSignals = Array.isArray(profile.inferenceMatchedSignals)
    ? profile.inferenceMatchedSignals.map((item) => safeText(item)).filter(Boolean)
    : []

  return {
    code,
    name: safeText(profile.industryName || profile.label || profile.inferredIndustryName) || industryNameFromCode(code),
    cssClass: normalizeIndustryCssClass(code),
    source: safeText(profile.styleSource || profile.source || 'UI_CONTRACT'),
    autoInferred: profile.autoInferred === true || safeText(profile.styleSource).includes('AUTO'),
    userOverrideLocked: profile.userOverrideLocked === true || profile.locked === true,
    confidence: Number(profile.inferenceConfidence || 0) || 0,
    pageType: safeText(profile.currentPageType || profile.pageType),
    visualTone: safeText(profile.visualTone || 'CLEAN'),
    density: normalizeRuntimeDensity(profile.density),
    radius: normalizeRuntimeRadius(profile.radius),
    navigationStyle: safeText(profile.navigationStyle || 'SIDE_NAV'),
    matchedSignals,
  }
}

function industryStyleFromProjectProfile(
  profile: ProjectThemeProfile,
): UiRuntimeIndustryStyle {
  const code = String(profile.industry || 'GENERAL').toUpperCase()
  return {
    code,
    name: industryNameFromCode(code),
    cssClass: normalizeIndustryCssClass(code),
    source: profile.locked ? 'USER_LOCKED_PROJECT_THEME' : safeText(profile.source || 'PROJECT_THEME'),
    autoInferred: !profile.locked && safeText(profile.source).toUpperCase().includes('AUTO'),
    userOverrideLocked: Boolean(profile.locked),
    confidence: 0,
    pageType: '',
    visualTone: String(profile.visualTone || 'CLEAN'),
    density: normalizeRuntimeDensity(profile.density),
    radius: normalizeRuntimeRadius(profile.radius),
    navigationStyle: String(profile.navigationStyle || 'SIDE_NAV'),
    matchedSignals: [],
  }
}

function normalizeRuntimeDensity(
  value: unknown,
): UiRuntimeThemeProfile['density'] {
  const text = safeText(value).toUpperCase()
  if (text === 'COMPACT') return 'compact'
  if (text === 'COMFORTABLE') return 'comfortable'
  return 'standard'
}

function normalizeRuntimeRadius(
  value: unknown,
): string {
  const raw = safeText(value)
  const text = raw.toUpperCase()
  if (/^\d+px$/.test(raw)) return raw
  if (text === 'SMALL') return '12px'
  if (text === 'LARGE') return '22px'
  if (text === 'MEDIUM') return '18px'
  return raw || '18px'
}

function normalizeRuntimeVisual(
  value: unknown,
  fallback: UiRuntimeThemeProfile['visual'],
): UiRuntimeThemeProfile['visual'] {
  const text = safeText(value).toUpperCase()
  if (['COMMERCE', 'RETAIL'].includes(text)) return 'commerce'
  if (['TECH', 'SAAS', 'WORK_ORDER'].includes(text)) return 'tech'
  if (['INDUSTRIAL', 'WAREHOUSE', 'MANUFACTURING'].includes(text)) return 'industrial'
  if (['FINANCE'].includes(text)) return 'finance'
  if (['GOV'].includes(text)) return 'gov'
  return fallback || 'clean'
}

function normalizeIndustryCssClass(
  value: unknown,
): string {
  return safeText(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'general'
}

function industryNameFromCode(
  value: unknown,
): string {
  const code = safeText(value).toUpperCase()
  const map: Record<string, string> = {
    GENERAL: '通用企业系统',
    COMMERCE: '电商 / 零售',
    FOREIGN_TRADE: '外贸 / 跨境业务',
    WORK_ORDER: '工单 / 服务流程',
    WAREHOUSE: '仓储 / 供应链',
    MANUFACTURING: '制造 / 生产管理',
    CRM: '客户关系 / CRM',
    FINANCE: '金融 / 风控',
    GOV: '政企 / 公共服务',
    SAAS: 'SaaS / 数据平台',
    MEDICAL: '医疗 / 健康',
    EDUCATION: '教育 / 培训',
    TECH: '科技平台',
    INDUSTRIAL: '工业制造',
    HEALTHCARE: '医疗健康',
  }
  return map[code] || code || '通用企业系统'
}

function isLooseRecord(
  value: unknown,
): value is LooseRecord {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}


function resolveThemeProfileFromProjectProfile(
  profile: ProjectThemeProfile,
): UiRuntimeThemeProfile {
  const fallback =
    THEME_PROFILES[profile.themeKey as UiRuntimeThemeKey] ||
    THEME_PROFILES.enterprise_default

  return {
    ...fallback,
    key: fallback.key,
    label: themeLabelFromProjectProfile(profile),
    primary: normalizeColor(profile.primaryColor, fallback.primary),
    secondary: normalizeColor(profile.secondaryColor, fallback.secondary),
    accent: normalizeColor(profile.accentColor, fallback.accent),
    density: densityFromProjectProfile(profile),
    radius: radiusFromProjectProfile(profile),
    buttonShape: profile.radius === 'LARGE' ? 'rounded' : fallback.buttonShape,
    visual: visualFromProjectProfile(profile),
  }
}

function themeLabelFromProjectProfile(
  profile: ProjectThemeProfile,
): string {
  const industryLabel = industryNameFromCode(profile.industry)

  if (String(profile.industry || '').toUpperCase() !== 'GENERAL' && industryLabel) {
    return industryLabel
  }

  if (profile.themeKey === 'commerce_light') return '电商零售'
  if (profile.themeKey === 'tech_modern') return '科技平台'
  if (profile.themeKey === 'industrial_pro') return '工业制造'
  if (profile.themeKey === 'finance_stable') return '金融风控'
  if (profile.themeKey === 'gov_clean') return '政务公共'

  return '通用企业'
}

function densityFromProjectProfile(
  profile: ProjectThemeProfile,
): UiRuntimeThemeProfile['density'] {
  if (profile.density === 'COMPACT') return 'compact'
  if (profile.density === 'COMFORTABLE') return 'comfortable'

  return 'standard'
}

function radiusFromProjectProfile(
  profile: ProjectThemeProfile,
): string {
  if (profile.radius === 'SMALL') return '12px'
  if (profile.radius === 'LARGE') return '22px'

  return '18px'
}

function visualFromProjectProfile(
  profile: ProjectThemeProfile,
): UiRuntimeThemeProfile['visual'] {
  if (profile.visualTone === 'COMMERCE') return 'commerce'
  if (profile.visualTone === 'TECH') return 'tech'
  if (profile.visualTone === 'INDUSTRIAL') return 'industrial'
  if (profile.visualTone === 'FINANCE') return 'finance'
  if (profile.visualTone === 'GOV') return 'gov'

  return 'clean'
}

function normalizeColor(
  value: string | null | undefined,
  fallback: string,
): string {
  const text = String(value || '').trim()

  return /^#[0-9a-fA-F]{6}$/.test(text)
    ? text
    : fallback
}

export function getThemeCssVars(
  theme: UiRuntimeThemeProfile,
): Record<string, string> {
  const densityVars = runtimeDensityCssVars(theme.density)
  const visualVars = runtimeVisualCssVars(theme.visual, theme)

  return {
    '--runtime-primary': theme.primary,
    '--runtime-secondary': theme.secondary,
    '--runtime-accent': theme.accent,
    '--runtime-page-bg': theme.pageBg,
    '--runtime-surface': theme.surface,
    '--runtime-text-main': theme.textMain,
    '--runtime-text-muted': theme.textMuted,
    '--runtime-border': theme.border,
    '--runtime-radius': theme.radius,
    '--runtime-radius-sm': densityVars.radiusSm,
    '--runtime-control-height': densityVars.controlHeight,
    '--runtime-card-padding': densityVars.cardPadding,
    '--runtime-grid-gap': densityVars.gridGap,
    '--runtime-table-cell-padding-y': densityVars.tableCellPaddingY,
    '--runtime-table-cell-padding-x': densityVars.tableCellPaddingX,
    '--runtime-action-column-width': densityVars.actionColumnWidth,
    '--runtime-button-radius': theme.buttonShape === 'pill' ? '999px' : theme.radius,
    '--runtime-card-shadow': visualVars.cardShadow,
    '--runtime-page-gradient': visualVars.pageGradient,
    '--runtime-tag-bg': visualVars.tagBg,
    '--runtime-tag-text': visualVars.tagText,
    '--runtime-status-success-bg': visualVars.successBg,
    '--runtime-status-success-text': visualVars.successText,
    '--runtime-status-warning-bg': visualVars.warningBg,
    '--runtime-status-warning-text': visualVars.warningText,
    '--runtime-status-error-bg': visualVars.errorBg,
    '--runtime-status-error-text': visualVars.errorText,
    '--runtime-focus-ring': `0 0 0 4px color-mix(in srgb, ${theme.primary} 14%, transparent)`,
    '--runtime-hero-radius': `calc(${theme.radius} + 8px)`,
    '--runtime-surface-muted': 'color-mix(in srgb, var(--runtime-surface) 86%, var(--runtime-page-bg))',
    '--runtime-border-strong': 'color-mix(in srgb, var(--runtime-border) 72%, var(--runtime-primary))',
    '--runtime-button-shadow': `0 12px 28px color-mix(in srgb, ${theme.primary} 18%, transparent)`,
    '--runtime-density-scale': theme.density === 'compact' ? '0.88' : theme.density === 'comfortable' ? '1.08' : '1',
    '--runtime-visual-system-version': 'A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM',
  }
}

function runtimeDensityCssVars(
  density: UiRuntimeThemeProfile['density'],
): Record<string, string> {
  if (density === 'compact') {
    return {
      radiusSm: '10px',
      controlHeight: '36px',
      cardPadding: '14px',
      gridGap: '10px',
      tableCellPaddingY: '8px',
      tableCellPaddingX: '10px',
      actionColumnWidth: '300px',
    }
  }

  if (density === 'comfortable') {
    return {
      radiusSm: '16px',
      controlHeight: '48px',
      cardPadding: '22px',
      gridGap: '18px',
      tableCellPaddingY: '14px',
      tableCellPaddingX: '16px',
      actionColumnWidth: '340px',
    }
  }

  return {
    radiusSm: '12px',
    controlHeight: '42px',
    cardPadding: '18px',
    gridGap: '14px',
    tableCellPaddingY: '11px',
    tableCellPaddingX: '12px',
    actionColumnWidth: '320px',
  }
}

function runtimeVisualCssVars(
  visual: UiRuntimeThemeProfile['visual'],
  theme: UiRuntimeThemeProfile,
): Record<string, string> {
  const base = {
    cardShadow: '0 18px 48px rgba(15, 23, 42, 0.08)',
    pageGradient:
      `radial-gradient(circle at 18% 12%, color-mix(in srgb, ${theme.primary} 10%, transparent), transparent 28%), linear-gradient(135deg, #f8fafc 0%, ${theme.pageBg} 100%)`,
    tagBg: 'color-mix(in srgb, var(--runtime-primary) 10%, #ffffff)',
    tagText: theme.primary,
    successBg: '#dcfce7',
    successText: '#166534',
    warningBg: '#fef3c7',
    warningText: '#92400e',
    errorBg: '#fee2e2',
    errorText: '#991b1b',
  }

  if (visual === 'commerce') {
    return {
      ...base,
      cardShadow: '0 22px 54px rgba(249, 115, 22, 0.13)',
      pageGradient:
        'radial-gradient(circle at 12% 8%, rgba(249, 115, 22, 0.13), transparent 30%), radial-gradient(circle at 88% 12%, rgba(14, 165, 233, 0.10), transparent 26%), linear-gradient(135deg, #fff7ed 0%, var(--runtime-page-bg) 62%, #ffffff 100%)',
      tagBg: '#ffedd5',
      tagText: '#c2410c',
    }
  }

  if (visual === 'industrial') {
    return {
      ...base,
      cardShadow: '0 14px 36px rgba(15, 23, 42, 0.10)',
      pageGradient:
        'linear-gradient(135deg, #f1f5f9 0%, var(--runtime-page-bg) 58%, #ffffff 100%)',
      tagBg: '#e0f2fe',
      tagText: '#075985',
      warningBg: '#fef3c7',
      warningText: '#92400e',
    }
  }

  if (visual === 'tech') {
    return {
      ...base,
      cardShadow: '0 24px 68px rgba(79, 70, 229, 0.12)',
      pageGradient:
        'radial-gradient(circle at 16% 10%, rgba(79, 70, 229, 0.16), transparent 30%), radial-gradient(circle at 90% 20%, rgba(6, 182, 212, 0.12), transparent 28%), linear-gradient(135deg, #eef2ff 0%, var(--runtime-page-bg) 62%, #ffffff 100%)',
      tagBg: '#eef2ff',
      tagText: '#4338ca',
    }
  }

  if (visual === 'finance' || visual === 'gov') {
    return {
      ...base,
      cardShadow: '0 12px 34px rgba(15, 23, 42, 0.07)',
      pageGradient:
        'linear-gradient(135deg, #f8fafc 0%, var(--runtime-page-bg) 100%)',
      tagBg: '#e0e7ff',
      tagText: '#3730a3',
    }
  }

  return base
}

export function cleanDisplayText(
  value: unknown,
): string {
  const text = safeText(value)
  if (!text) return ''
  return isDeveloperOnlyText(text) ? '' : text
}

export function isDeveloperOnlyText(
  value: unknown,
): boolean {
  const text = safeText(value).toLowerCase()
  if (!text) return true

  return hasAny(text, [
    '包含',
    '字段',
    '提交按钮',
    '组件',
    'feature',
    'mock',
    'runtime',
    'contract',
    '契约',
    '覆盖',
    '用于',
    '当前页面用于',
    '该组件',
    '该页面',
    '表单字段',
    '调试',
    '原型',
    '蓝图',
    '交互原型',
    'ui_contract',
  ])
}

export function getLayoutType(
  contract: UiContractContent,
): string {
  const source = contract as UiContractContent & LooseRecord
  const layout = (source.page || {}).layout

  if (!layout) return ''
  return typeof layout === 'string'
    ? layout
    : String((layout as LooseRecord).type || '')
}

function resolveVisibleTitle(
  contract: UiContractContent,
  pattern: UiRuntimePagePattern,
  fallback: string,
): string {
  const source = contract as UiContractContent & LooseRecord
  const page = (source.page || {}) as LooseRecord

  if (pattern === 'LOGIN_FORM') return '欢迎登录'
  if (pattern === 'REGISTER_FORM') return '创建账号'
  if (pattern === 'PROFILE_PAGE') return '个人资料'

  return cleanDisplayText(page.title) ||
    cleanDisplayText(page.pageName) ||
    fallback
}

function brandTitle(
  contract: UiContractContent,
): string {
  const source = contract as UiContractContent & LooseRecord
  const module = (source.module || {}) as LooseRecord
  const page = (source.page || {}) as LooseRecord

  const raw = safeText(
    module.module_name ||
      module.moduleName ||
      module.name ||
      source.projectName ||
      page.pageName ||
      page.title ||
      '业务系统',
  )

  return raw.replace(/登录页|登录|注册页|注册/g, '').trim() || '业务系统'
}

function brandSubtitle(
  contract: UiContractContent,
  theme: UiRuntimeThemeProfile,
): string {
  const source = contract as UiContractContent & LooseRecord
  const page = (source.page || {}) as LooseRecord

  const text = cleanDisplayText(page.description)
  if (text) return text

  if (theme.visual === 'commerce') return '安全、便捷地进入您的业务工作台。'
  if (theme.visual === 'tech') return '以智能化能力连接业务流程、数据资产与团队协同。'
  if (theme.visual === 'industrial') return '面向生产、设备与运营场景的稳定业务平台。'
  if (theme.visual === 'finance') return '可信、稳健地管理业务流程、交易数据与风险控制。'
  if (theme.visual === 'gov') return '规范、清晰地支撑公共服务与业务办理。'

  return '面向业务团队的高效协同与管理平台。'
}

function brandBullets(
  pattern: UiRuntimePagePattern,
  theme: UiRuntimeThemeProfile,
): string[] {
  if (pattern === 'LOGIN_FORM' || pattern === 'REGISTER_FORM') {
    if (theme.visual === 'commerce') {
      return ['统一会员与业务入口', '保护账号安全与访问权限', '支持注册和后续业务扩展']
    }
    if (theme.visual === 'tech') {
      return ['统一身份认证和权限接入', '安全连接数据和业务流程', '支持扩展第三方登录能力']
    }
    if (theme.visual === 'industrial') {
      return ['统一生产业务入口', '面向设备和产线权限控制', '保证关键操作安全可控']
    }

    return ['统一账号入口', '安全访问业务系统', '支持后续业务扩展']
  }

  return ['结构清晰', '交互可验证', '契约可追踪']
}

function primaryActionText(
  contract: UiContractContent,
  pattern: UiRuntimePagePattern,
): string {
  const source = contract as UiContractContent & LooseRecord
  const actions = Array.isArray(source.interactions)
    ? source.interactions
    : []

  const submitAction = actions.find((action: LooseRecord) =>
    ['SUBMIT_FORM', 'SUBMIT'].includes(String(action.type || '').toUpperCase()),
  )

  if (submitAction?.label && !isDeveloperOnlyText(submitAction.label)) {
    return String(submitAction.label)
  }

  if (pattern === 'LOGIN_FORM') return '登录'
  if (pattern === 'REGISTER_FORM') return '注册'
  if (['EDIT_FORM', 'SETTINGS_FORM', 'PROFILE_PAGE'].includes(pattern)) return '保存'

  return '确认'
}

function componentText(
  component: UiContractComponent,
): string {
  const source = component as UiContractComponent & LooseRecord

  return [
    source.componentId,
    source.type,
    source.title,
    source.description,
    source.label,
    Array.isArray(source.fields)
      ? source.fields
          .map((field: LooseRecord) =>
            `${field.fieldKey || ''} ${field.label || ''} ${field.placeholder || ''}`,
          )
          .join(' ')
      : '',
    Array.isArray(source.children)
      ? source.children
          .map((child: UiContractComponent) => componentText(child))
          .join(' ')
      : '',
  ].join(' ')
}

function safeText(
  value: unknown,
): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

function joinSearchText(
  values: unknown[],
): string {
  return values
    .flatMap((item) => Array.isArray(item) ? item : [item])
    .filter((item) => item !== null && item !== undefined)
    .map((item) => String(item))
    .join(' ')
}

function hasAny(
  text: string,
  keywords: string[],
): boolean {
  const source = text.toLowerCase()
  return keywords.some((keyword) => source.includes(keyword.toLowerCase()))
}